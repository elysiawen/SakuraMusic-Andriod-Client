package com.sakura.music.data.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/**
 * 一个「按 key 存取原始响应」的缓存桶。
 *
 * 只有读、写、读过期数据三个动作：解释内容是调用方（[com.sakura.music.data.remote.ApiClient]）
 * 的事，缓存层因此不必认识任何业务模型。
 */
interface ResponseCache {

    /** 未过期时返回缓存的原始响应；过期或没有就返回 null。 */
    fun read(key: String, maxAgeMillis: Long): String?

    /**
     * 不管过没过期，有就返回。
     *
     * 网络挂了的时候，一份旧数据也比「加载不出来」强——尤其是歌词和专辑信息，
     * 这类内容几年都不会变一次。
     */
    fun readStale(key: String): String?

    fun write(key: String, payload: String)

    fun remove(key: String)

    fun clear()
}

/**
 * 用 JSON 文件存响应的缓存桶。
 *
 * 落盘内容是「保存时间 + 原始响应字符串」。存字符串而不是解析后的对象，
 * 是为了让缓存和序列化格式解耦：模型怎么改都只影响读取的那一行。
 *
 * 文件名是 key 的 URL-safe Base64：key 里会带 `/`、`:`、中文，直接当文件名不安全。
 */
class JsonFileCache(
    private val directory: File,
    private val json: Json,
) : ResponseCache {

    init {
        directory.mkdirs()
    }

    override fun read(key: String, maxAgeMillis: Long): String? {
        val entry = readEntry(key) ?: return null
        val age = System.currentTimeMillis() - entry.savedAt
        // 系统时间被往回调过（age < 0）时当作过期，免得一份数据永远新鲜。
        if (age < 0 || age > maxAgeMillis) return null
        return entry.payload
    }

    override fun readStale(key: String): String? = readEntry(key)?.payload

    override fun write(key: String, payload: String) {
        val file = fileOf(key)
        val temp = File(directory, file.name + TEMP_SUFFIX)
        runCatching {
            val text = json.encodeToString(CacheEntry.serializer(), CacheEntry(System.currentTimeMillis(), payload))
            temp.writeText(text)
            // 先写临时文件再改名：进程在写一半时被杀也不会留下半截 JSON。
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }.onFailure {
            runCatching { temp.delete() }
        }
    }

    override fun remove(key: String) {
        runCatching { fileOf(key).delete() }
    }

    override fun clear() {
        directory.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** 这个桶占了多少字节、存了多少条。 */
    fun stats(): Pair<Long, Int> {
        val files = directory.listFiles()?.filter { it.isFile } ?: return 0L to 0
        return files.sumOf { it.length() } to files.size
    }

    private fun readEntry(key: String): CacheEntry? {
        val file = fileOf(key)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        // 解析不了的缓存直接丢掉：它只会一直挡在前面，让每次请求都白读一次。
        return runCatching { json.decodeFromString(CacheEntry.serializer(), text) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull()
    }

    private fun fileOf(key: String): File = File(directory, encodeKey(key) + EXTENSION)

    private fun encodeKey(key: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray(Charsets.UTF_8))

    @Serializable
    private class CacheEntry(val savedAt: Long, val payload: String)

    private companion object {
        const val EXTENSION = ".json"
        const val TEMP_SUFFIX = ".tmp"
    }
}
