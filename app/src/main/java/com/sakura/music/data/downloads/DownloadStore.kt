package com.sakura.music.data.downloads

import com.sakura.music.data.model.UnifiedTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 一首已下载的歌。
 *
 * 整首 [UnifiedTrack] 一起存下来：离线时没有网关可以再去问详情，
 * 而播放、显示封面、跳专辑都要用到它。
 */
@Serializable
data class DownloadedTrack(
    /** 曲目键（平台无关），列表里去重与高亮当前播放都用它。 */
    val key: String,
    /** 音频在缓存里的键（`平台:ID:音质`），删除时要按它去删缓存分片。 */
    val cacheKey: String,
    val platform: String,
    val sourceId: String,
    val quality: String,
    val sizeBytes: Long,
    val savedAt: Long,
    val track: UnifiedTrack,
)

/**
 * 已下载歌曲的索引。
 *
 * 存在 `filesDir` 而不是 `cacheDir`：缓存目录在系统存储紧张时会被清掉，
 * 而「我下载过这首歌」这件事不该被系统随手抹掉。
 *
 * 音频字节本身仍放在缓存目录里（和边播边存的那些共用一份），靠 [cacheKeys]
 * 告诉缓存管理器别淘汰它们——所以索引和缓存必须同时对得上，缺一个都算没下载。
 */
class DownloadStore(
    directory: File,
    private val json: Json,
) {

    private val file = File(directory, INDEX_FILE)

    init {
        // 父目录得先有：写临时文件时它不存在会直接抛异常，索引就悄悄丢了。
        directory.mkdirs()
    }

    @Volatile
    private var items: List<DownloadedTrack> = readLocked()

    /** 最近下载的排在最前。 */
    fun all(): List<DownloadedTrack> = items.sortedByDescending { it.savedAt }

    fun get(key: String): DownloadedTrack? = items.firstOrNull { it.key == key }

    fun contains(key: String): Boolean = items.any { it.key == key }

    /** 受保护的缓存键：这些分片不算「缓存」，不能被淘汰或被清理掉。 */
    fun cacheKeys(): Set<String> = items.mapTo(HashSet()) { it.cacheKey }

    @Synchronized
    fun put(item: DownloadedTrack) {
        items = items.filterNot { it.key == item.key } + item
        writeLocked()
    }

    /** 删一条索引，返回被删的那条（调用方拿它的 cacheKey 去删音频）。 */
    @Synchronized
    fun remove(key: String): DownloadedTrack? {
        val target = items.firstOrNull { it.key == key } ?: return null
        items = items.filterNot { it.key == target.key }
        writeLocked()
        return target
    }

    private fun readLocked(): List<DownloadedTrack> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString(Index.serializer(), text) }
            .getOrNull()
            ?.items
            ?: emptyList()
    }

    private fun writeLocked() {
        runCatching {
            val text = json.encodeToString(Index.serializer(), Index(items))
            val temp = File(file.parentFile, file.name + TEMP_SUFFIX)
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }
    }

    @Serializable
    private class Index(val items: List<DownloadedTrack>)

    private companion object {
        const val INDEX_FILE = "downloads.json"
        const val TEMP_SUFFIX = ".tmp"
    }
}
