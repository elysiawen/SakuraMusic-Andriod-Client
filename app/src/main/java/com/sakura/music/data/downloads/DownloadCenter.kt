package com.sakura.music.data.downloads

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import kotlinx.coroutines.isActive
import com.sakura.music.core.player.PlayRequest
import com.sakura.music.core.player.sakuraCacheKeyFactory
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一首歌的下载状态：没下载 / 正在下 / 已下载。 */
enum class DownloadStatus { None, Downloading, Done }

/** 状态 + 进度百分比（拿不到总长度时 [percent] 为 null）。 */
data class DownloadState(
    val status: DownloadStatus,
    val percent: Int?,
)

/**
 * 下载。
 *
 * 和「边播边存」用的是同一份音频缓存：下载其实就是把整首歌一次性写进缓存，
 * 播的时候自然命中，不用再为下载单独准备一套播放器。区别只在于记了一笔索引——
 * 索引里的键会被缓存管理器当成「受保护」，LRU 淘汰和设置页的清理都不会碰它们。
 *
 * 用 [CacheWriter] 而不是 media3 的 DownloadManager：后者要配 DownloadService、
 * 通知和它自己的索引数据库，而这里要的只是「把字节拉下来并记住」。
 */
class DownloadCenter(
    private val cacheManager: CacheManager,
    private val upstream: androidx.media3.datasource.DataSource.Factory,
    private val store: DownloadStore,
    private val scope: CoroutineScope,
) {

    private val _downloads = MutableStateFlow(store.all())
    val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    /** 正在下的进度：`cacheKey -> 0..100`；拿不到总长度时是 -1。 */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val jobs = HashMap<String, Job>()

    /**
     * 这首歌此刻的下载状态。
     *
     * 读的是内存里的进度与索引，所以调用方要把 [downloads] / [progress] 两个流收集起来
     * 才能跟着变——打包成一个对象，界面那边就好写。
     */
    fun downloadState(
        track: UnifiedTrack,
        quality: Quality,
        platform: Platform? = null,
    ): DownloadState {
        val cacheKey = keyOf(track, quality, platform)
        val percent = cacheKey?.let { _progress.value[it] }
        val status = when {
            cacheKey != null && _progress.value.containsKey(cacheKey) -> DownloadStatus.Downloading
            store.contains(track.key) -> DownloadStatus.Done
            else -> DownloadStatus.None
        }
        return DownloadState(status, percent?.takeIf { it >= 0 })
    }

    /** 开始下载。同一个音质重复点不会起第二个任务。 */
    fun start(track: UnifiedTrack, quality: Quality, platform: Platform? = null) {
        val request = requestOf(track, quality, platform) ?: return
        val cacheKey = request.cacheKey
        if (cacheKey in jobs) return
        // 先占个位：界面要能立刻看到「在下载」，不等第一次进度回调。
        setProgress(cacheKey, 0)

        jobs[cacheKey] = scope.launch(Dispatchers.IO) {
            val dataSource = CacheDataSource.Factory()
                .setCache(cacheManager.audioCache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // 和播放用同一个键，否则下完的歌在缓存里找不到。
                .setCacheKeyFactory(sakuraCacheKeyFactory)
                .createDataSource()

            try {
                // 直接读完整条流，CacheDataSource 顺手就把字节写进缓存了。
                // 没用 CacheWriter：它的 run() 和 Kotlin 的 run {} 撞名，
                // 而且自己读可以靠 isActive 干净地取消。
                val length = dataSource.open(DataSpec(request.uri()))
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (isActive) {
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    if (read <= 0) continue
                    total += read
                    // 拿不到总长度（分块传输）时给 -1：界面显示「下载中」而不是乱跳的百分比。
                    setProgress(cacheKey, if (length > 0) ((total * 100) / length).toInt() else -1)
                }
                dataSource.close()
                if (!isActive) return@launch

                // 缓存里量出来的是准的；万一写入还没落盘（或者上游没报长度），
                // 退回用刚读到的总字节数，不显示 0。
                val cached = cacheManager.audioCache
                    .getCachedSpans(cacheKey)
                    .sumOf { it.length }
                val bytes = if (cached > 0) cached else length.coerceAtLeast(0)
                store.put(
                    DownloadedTrack(
                        key = track.key,
                        cacheKey = cacheKey,
                        platform = request.platform.id,
                        sourceId = request.id,
                        quality = quality.id,
                        sizeBytes = bytes,
                        savedAt = System.currentTimeMillis(),
                        track = track,
                    )
                )
                // 顺手把封面也拉进缓存：不然离线听下载的歌，系统媒体条上没有图。
                cacheManager.warmUpCover(track.album.cover)
            } finally {
                runCatching { dataSource.close() }
                jobs.remove(cacheKey)
                setProgress(cacheKey, null)
                _downloads.value = store.all()
            }
        }
    }

    /** 取消正在进行的下载；已经写进缓存的那部分留着，下次继续。 */
    fun cancel(track: UnifiedTrack, quality: Quality, platform: Platform? = null) {
        val cacheKey = keyOf(track, quality, platform) ?: return
        jobs[cacheKey]?.cancel()
        jobs.remove(cacheKey)
        setProgress(cacheKey, null)
    }

    /** 删掉一首已下载的歌：索引和音频都要清。 */
    fun remove(track: UnifiedTrack) {
        val item = store.remove(track.key) ?: return
        jobs[item.cacheKey]?.cancel()
        jobs.remove(item.cacheKey)
        scope.launch(Dispatchers.IO) { cacheManager.removeAudio(item.cacheKey) }
        _downloads.value = store.all()
    }

    private fun keyOf(track: UnifiedTrack, quality: Quality, platform: Platform?): String? =
        requestOf(track, quality, platform)?.cacheKey

    private fun requestOf(
        track: UnifiedTrack,
        quality: Quality,
        platform: Platform?,
    ): PlayRequest? {
        val source = platform?.let { track.sourceOf(it) } ?: track.primarySource ?: return null
        return PlayRequest(source.platform, source.id, quality)
    }

    private fun setProgress(cacheKey: String, percent: Int?) {
        val next = _progress.value.toMutableMap()
        if (percent == null) next.remove(cacheKey) else next[cacheKey] = percent
        _progress.value = next
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
