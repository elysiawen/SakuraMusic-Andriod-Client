package com.sakura.music.data.session

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 会话 Cookie 的收发与持久化。
 *
 * 网关的登录态就是一个 HttpOnly Cookie（`sakura_session`），客户端只要像浏览器一样
 * 存下来、每次请求带上即可。放在 SharedPreferences 里，进程重启后依然登录着。
 */
class SakuraCookieJar(
    context: Context,
    private val json: Json,
) : CookieJar {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val cache: MutableList<Cookie> = load()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cookies.forEach { cookie ->
                cache.removeAll {
                    it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                }
                // 服务端删 Cookie 时会下发一个已过期的同名额，落库前先过滤掉。
                if (cookie.expiresAt > now || cookie.expiresAt == Long.MAX_VALUE) {
                    cache.add(cookie)
                }
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expired = cache.filter { it.expiresAt != Long.MAX_VALUE && it.expiresAt <= now }
            if (expired.isNotEmpty()) {
                cache.removeAll(expired)
                persist()
            }
            return cache.filter { it.matches(url) }
        }
    }

    /** 登出、改密码之后调用；会话已失效，留着只会让请求一直带一个死 Cookie。 */
    fun clear() {
        synchronized(lock) {
            cache.clear()
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    /** 本地是否留着会话 Cookie。注意它不能当鉴权探针：服务端可能已经把这行删了。 */
    fun hasSessionCookie(): Boolean = synchronized(lock) {
        cache.any { it.name == SESSION_COOKIE }
    }

    private fun load(): MutableList<Cookie> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return mutableListOf()
        val stored = runCatching {
            json.decodeFromString(ListSerializer, raw)
        }.getOrNull() ?: return mutableListOf()

        val now = System.currentTimeMillis()
        return stored
            .filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }
            .mapNotNull { it.toCookie() }
            .toMutableList()
    }

    private fun persist() {
        val snapshot = cache.map { StoredCookie.from(it) }
        prefs.edit()
            .putString(KEY_COOKIES, json.encodeToString(ListSerializer, snapshot))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "sakura_music_session"
        const val KEY_COOKIES = "cookies"
        const val SESSION_COOKIE = "sakura_session"

        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(StoredCookie.serializer())
    }
}

/** okhttp 的 [Cookie] 没有公开构造字段，落盘时得自己摊平。 */
@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    // SameSite 不落盘：它是给浏览器用的语义，okhttp 不做校验，丢了也不影响请求。
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (expiresAt != Long.MAX_VALUE) expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}
