package com.sakura.music.data.session

import android.content.Context
import android.util.Base64
import com.sakura.music.data.model.Platform
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 「仅本机」第三方凭据的本地存放。
 *
 * 绑定流程（`/api/bind` 那一组接口）目前在 Android 端**没有实现**——请先在网页端或桌面端扫码，
 * 或者把凭据存到服务器上。这里保留完整的读取与透传链路，一旦绑定界面补上，
 * 只要往里写值，之后每个音乐类请求就会自动带上 `X-Sakura-Credential`。
 *
 * 值形态：`base64url(JSON.stringify({ "netease": "MUSIC_U=…", "qq": "musicid=…; musickey=…" }))`，
 * 不带 `=` 补齐，与网关的 `Buffer.from(x, 'base64url')` 一致。
 */
class LocalCredentialStore(
    context: Context,
    private val json: Json,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cache: Map<String, String> = load()

    /** 请求头值；没有任何本机凭据时返回 null（表示「未提供」）。 */
    fun headerValue(): String? {
        val current = cache
        if (current.isEmpty()) return null
        val text = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), current)
        return Base64.encodeToString(
            text.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    fun cookieOf(platform: Platform): String? = cache[platform.id]

    fun save(platform: Platform, cookie: String) {
        if (cookie.isBlank()) return
        val next = cache.toMutableMap().apply { put(platform.id, cookie) }
        write(next)
    }

    fun remove(platform: Platform) {
        if (!cache.containsKey(platform.id)) return
        write(cache.toMutableMap().apply { remove(platform.id) })
    }

    private fun write(value: Map<String, String>) {
        cache = value
        prefs.edit()
            .putString(KEY_MAP, json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value))
            .apply()
    }

    private fun load(): Map<String, String> {
        val raw = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFS_NAME = "sakura_music_credentials"
        const val KEY_MAP = "local_credentials"
    }
}
