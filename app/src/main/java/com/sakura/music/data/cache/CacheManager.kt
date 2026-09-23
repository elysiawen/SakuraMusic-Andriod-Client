package com.sakura.music.data.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import com.sakura.music.data.downloads.DownloadStore
import com.sakura.music.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 缓存的种类。设置页按这个顺序展示，并分别统计与清理。
 *
 * [hint] 要短：它在设置页里和右侧的占用量同处一行，写得长了就会折行，
 * 把那一行的图标和数字挤得和别行对不齐。
 */
enum class CacheKind(val label: String, val hint: String) {
    AUDIO("音频", "边播边存"),
    COVER("封面图片", "专辑封面与头像"),
    LYRIC("歌词", "取过的歌词"),
    META("歌曲与专辑信息", "歌曲与专辑"),
}

/** 某一类缓存的占用。封面数不出来条目数时 [entries] 为 0。 */
data class CacheUsage(
    val kind: CacheKind,
    val bytes: Long,
    val entries: Int,
)

/**
 * 全应用缓存的总管。
 *
 * 四类缓存各有各的存法，但都要能被「统计占用」和「清理」：
 * - 音频交给 ExoPlayer 的 [SimpleCache]，边播边存、按最近最少使用淘汰；
 * - 封面交给 Coil 的磁盘缓存，这里只固定它的目录与上限，好把它算进来；
 * - 歌词与歌曲/专辑信息是网关响应，按 key 存成 JSON 文件。
 *
 * 全部落在 `cacheDir` 下：minSdk 26 起这里不需要任何存储权限，
 * 而且系统存储紧张时会优先清掉它，语义上正是「缓存」。
 */
class CacheManager(
    private val appContext: Context,
    private val json: Json,
    private val settings: SettingsStore,
    /** 已下载歌曲的索引：它给出的缓存键是「受保护」的，不算缓存。 */
    private val downloads: DownloadStore,
) {

    private val root = File(appContext.cacheDir, ROOT_DIR)
    private val audioDir = File(root, AUDIO_DIR)
    private val coverDir = File(root, COVER_DIR)
    private val lyricDir = File(root, LYRIC_DIR)
    private val metaDir = File(root, META_DIR)

    /** 歌词与「歌曲/专辑信息」分开存：设置页要能分别报出各占多少。 */
    val lyricCache: ResponseCache = JsonFileCache(lyricDir, json)
    val metaCache: ResponseCache = JsonFileCache(metaDir, json)

    /** 用户设定的上限与有效期。改设置时 [refresh] 会把它们换掉，读取方（淘汰器、请求层）只用当前值。 */
    @Volatile
    private var audioLimitBytes = SettingsStore.mbToBytes(SettingsStore.DEFAULT_AUDIO_CACHE_MB)

    @Volatile
    private var coverLimitBytes = SettingsStore.mbToBytes(SettingsStore.DEFAULT_COVER_CACHE_MB)

    @Volatile
    private var lyricTtl = SettingsStore.daysToMillis(SettingsStore.DEFAULT_LYRIC_CACHE_DAYS)

    @Volatile
    private var metaTtl = SettingsStore.daysToMillis(SettingsStore.DEFAULT_META_CACHE_DAYS)

    /** 歌词有效期（毫秒）；[Long.MAX_VALUE] 表示永不过期。 */
    val lyricTtlMillis: Long get() = lyricTtl

    /** 歌曲与专辑信息有效期（毫秒）。 */
    val metaTtlMillis: Long get() = metaTtl

    @Volatile
    private var audioCacheInstance: SimpleCache? = null

    /**
     * 音频缓存。
     *
     * 上限由用户在设置里定，超了按最近最少播放淘汰——缓存是给自己省流量的，
     * 不该把用户的存储空间吃光。
     *
     * 自己懒加载而不用 `by lazy`：设置页要能区分「一首都没缓存」和「已经建好了」，
     * 为了显示 0 B 就去建一个缓存数据库不划算。
     */
    val audioCache: SimpleCache
        get() = audioCacheInstance ?: synchronized(this) {
            audioCacheInstance ?: SimpleCache(
                audioDir.apply { mkdirs() },
                LruCacheEvictor(
                    maxBytes = { audioLimitBytes },
                    // 用户下载过的歌不参与淘汰：那是他主动留下的，不是缓存。
                    protectedKeys = { downloads.cacheKeys() },
                ),
                StandaloneDatabaseProvider(appContext),
            ).also { audioCacheInstance = it }
        }

    @Volatile
    private var imageLoaderInstance: ImageLoader? = null

    /**
     * 图片加载器。
     *
     * 默认的全局 ImageLoader 也带磁盘缓存，但目录不受我们控制；这里自己建一个，
     * 目录和上限都自己定，才能在设置页里和另外三类一起展示。
     */
    val imageLoader: ImageLoader
        get() = imageLoaderInstance ?: synchronized(this) {
            imageLoaderInstance ?: buildImageLoader(coverLimitBytes).also { imageLoaderInstance = it }
        }

    /**
     * 重新读一遍用户的缓存设置并立刻生效。
     *
     * 音频的上限交给淘汰器，改完下一次写入就按新上限走，同时把已经超出的部分腾掉；
     * 封面的上限写死在 Coil 的 DiskCache 里，只能换一个 ImageLoader——磁盘文件是同一份目录，
     * 所以内容不会丢，只是换了条 LRU 的尺子。
     */
    suspend fun refresh() {
        audioLimitBytes = limitBytes(settings.audioCacheMb.first())
        lyricTtl = SettingsStore.daysToMillis(settings.lyricCacheDays.first())
        metaTtl = SettingsStore.daysToMillis(settings.metaCacheDays.first())

        val coverBytes = limitBytes(settings.coverCacheMb.first())
        if (coverBytes != coverLimitBytes) {
            coverLimitBytes = coverBytes
            rebuildImageLoader()
        }
        trimAudioCache()
    }

    /**
     * 上限换算成字节。
     *
     * 0 是「不缓存」，[SettingsStore.UNLIMITED_MB] 是「无限」——后者给成一个大到
     * 不可能达到的数，淘汰器因此永远不触发，效果就是不限量。
     */
    private fun limitBytes(mb: Int): Long = when {
        mb < 0 -> Long.MAX_VALUE
        else -> SettingsStore.mbToBytes(mb)
    }

    /**
     * 播放用不用缓存，看用户把上限拨到了哪。
     *
     * 「不缓存」时返回 null，数据源就会直接走网络——比建一个上限为 0 的缓存干净：
     * 后者每写一点就被淘汰，白折腾。
     */
    fun audioCacheOrNull(): SimpleCache? = if (audioLimitBytes <= 0L) null else audioCache

    @OptIn(ExperimentalCoilApi::class)
    private fun buildImageLoader(maxBytes: Long): ImageLoader =
        ImageLoader.Builder(appContext)
            .apply {
                if (maxBytes <= 0L) {
                    // 上限拨到 0：直接不给磁盘缓存，图片每次重新下载。
                    diskCache(null)
                } else {
                    diskCache {
                        DiskCache.Builder()
                            .directory(coverDir)
                            .maxSizeBytes(maxBytes)
                            .build()
                    }
                }
            }
            .build()

    private fun rebuildImageLoader() {
        val old = imageLoaderInstance
        imageLoaderInstance = null
        // 先收掉旧的再开新的：同一个磁盘目录被两个 DiskCache 同时拿着会打架。
        old?.shutdown()
        val next = buildImageLoader(coverLimitBytes)
        imageLoaderInstance = next
        Coil.setImageLoader(next)
    }

    /** 上限调小之后，把已经超出的一并腾掉：不然要等下一次写入才会触发淘汰。 */
    private fun trimAudioCache() {
        val cache = audioCacheInstance ?: return
        val limit = audioLimitBytes
        if (cache.cacheSpace <= limit) return
        val protected = downloads.cacheKeys()

        cache.keys
            .filterNot { it in protected }
            .flatMap { cache.getCachedSpans(it) }
            .sortedBy { it.lastTouchTimestamp }
            .forEach { span ->
                if (cache.cacheSpace <= limit) return
                runCatching { cache.removeSpan(span) }
            }
    }

    /** 删掉某个键的全部音频分片；下载被删除时用它，播放缓存淘汰也走它。 */
    fun removeAudio(cacheKey: String) {
        val cache = audioCacheInstance ?: return
        cache.getCachedSpans(cacheKey).forEach { span ->
            runCatching { cache.removeSpan(span) }
        }
    }

    /** 按 [CacheKind.entries] 的顺序给出每一类的占用。会读磁盘，请在 IO 线程调用。 */
    @OptIn(ExperimentalCoilApi::class)
    fun usage(): List<CacheUsage> = CacheKind.entries.map { kind ->
        when (kind) {
            CacheKind.AUDIO -> {
                // 只读已经建好的缓存：为看一眼占用就把播放器依赖的缓存建起来不值得。
                val cache = audioCacheInstance
                CacheUsage(kind, cache?.cacheSpace ?: 0L, cache?.keys?.size ?: 0)
            }

            CacheKind.COVER -> CacheUsage(kind, imageLoader.diskCache?.size ?: 0L, 0)

            CacheKind.LYRIC -> (lyricCache as JsonFileCache).stats().let { (bytes, entries) ->
                CacheUsage(kind, bytes, entries)
            }

            CacheKind.META -> (metaCache as JsonFileCache).stats().let { (bytes, entries) ->
                CacheUsage(kind, bytes, entries)
            }
        }
    }

    /** 清掉一类；传 null 表示全清。会读磁盘，请在 IO 线程调用。 */
    @OptIn(ExperimentalCoilApi::class)
    fun clear(kind: CacheKind? = null) {
        when (kind) {
            null -> {
                clearAudio()
                clearCovers()
                lyricCache.clear()
                metaCache.clear()
            }

            CacheKind.AUDIO -> clearAudio()
            CacheKind.COVER -> clearCovers()
            CacheKind.LYRIC -> lyricCache.clear()
            CacheKind.META -> metaCache.clear()
        }
    }

    /**
     * 清音频用的方式是「删 span」而不是删目录。
     *
     * 目录删了，正在播放的那首歌读的缓存文件会当场消失，播放会断；
     * 删 span 时已经打开的文件描述符还在，当前这首能正常播完。
     */
    /**
     * 清缓存不动已下载的歌。
     *
     * 用户点「清理缓存」是想腾空间，不是想把下载的东西删了——后者有明确入口
     * （本地音乐页里的删除），两个动作不该互相连坐。
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun clearCovers() {
        imageLoader.diskCache?.clear()
    }

    /**
     * 读出某张封面的字节。
     *
     * 系统媒体条（通知栏/锁屏那一条）活在 SystemUI 进程里：它既不会用 Coil 的缓存，
     * 也读不了我们的私有目录（`file://` 会被权限挡住），拿到的只有元数据里的封面 URL，
     * 然后自己联网去取——离线时那一条的封面就是空的。
     *
     * 唯一能给的办法是把字节填进元数据（artworkData）。而这份字节其实就在 Coil 的磁盘缓存里
     * （界面显示封面走的就是它），所以直接复用，不必再存一份。
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun coverBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        val cache = imageLoader.diskCache ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                cache.openSnapshot(url)?.use { snapshot ->
                    FileSystem.SYSTEM.read(snapshot.data) { readByteArray() }
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * 把封面拉进磁盘缓存。
     *
     * 换歌和下载时顺手做一次：等真要离线听了再去取就来不及了。
     */
    suspend fun warmUpCover(url: String?) {
        if (url.isNullOrBlank()) return
        val request = ImageRequest.Builder(appContext)
            .data(url)
            // 只为了落盘，不必占着内存缓存。
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        runCatching { imageLoader.execute(request) }
    }

    private fun clearAudio() {
        val cache = audioCacheInstance ?: return
        val protected = downloads.cacheKeys()
        cache.keys
            .filterNot { it in protected }
            .forEach { key -> removeAudio(key) }
    }

    companion object {
        /**
         * 设置页滑块的档位（MB），从左到右：不缓存 → … → 无限。
         *
         * 音频最右一档是无限，它前一档给到 10 GB——想认真离线听的人要的就是这个量级；
         * 再往上加档没意义，直接选无限就行。
         */
        val AUDIO_LIMIT_STOPS = listOf(
            SettingsStore.NO_CACHE_MB, 256, 512, 1024, 2048, 4096, 10 * 1024, SettingsStore.UNLIMITED_MB,
        )

        /** 封面图片小得多，档位整体下移一档，到 2 GB 之后直接是无限。 */
        val COVER_LIMIT_STOPS = listOf(
            SettingsStore.NO_CACHE_MB, 64, 128, 256, 512, 1024, 2048, SettingsStore.UNLIMITED_MB,
        )

        /** 设置页可选的歌词有效期（天），[SettingsStore.FOREVER] 表示永不过期。 */
        val LYRIC_TTL_CHOICES = listOf(7, 30, 90, SettingsStore.FOREVER)

        /** 设置页可选的歌曲/专辑信息有效期（天）。 */
        val META_TTL_CHOICES = listOf(1, 7, 30, SettingsStore.FOREVER)

        private const val ROOT_DIR = "sakura_cache"
        private const val AUDIO_DIR = "audio"
        private const val COVER_DIR = "cover"
        private const val LYRIC_DIR = "lyric"
        private const val META_DIR = "meta"
    }
}
