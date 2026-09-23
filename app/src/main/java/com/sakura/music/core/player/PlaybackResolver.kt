package com.sakura.music.core.player

import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlayResolveResult
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 播放地址的解析与缓存。
 *
 * `play/resolve` 同时给两条路：网关代理（`url`）与平台 CDN 直连（`direct`）。
 * **直连优先**，服务器带宽为零；直连被防盗链拒绝时回退到代理，并把该平台记下来，
 * 之后直接跳过直连——可用性优先，带宽次之。
 *
 * 解析结果缓存几分钟：拖进度条会让 ExoPlayer 反复 open，没有缓存就会反复打上游；
 * 但缓存不能超过令牌有效期（10 分钟），否则会拿到一个已经过期的地址。
 */
class PlaybackResolver(
    private val api: SakuraApi,
    private val settings: SettingsStore,
    private val baseUrlProvider: () -> String,
    private val scope: CoroutineScope,
) {

    private class Entry(val result: PlayResolveResult, val createdAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val proxyOnly = AtomicReference<Set<Platform>>(emptySet())

    init {
        scope.launch {
            settings.proxyOnlyPlatforms.collect { proxyOnly.set(it) }
        }
    }

    fun isProxyOnly(platform: Platform): Boolean = proxyOnly.get().contains(platform)

    /** 直连被 CDN 拒了：记进偏好，这个平台以后不再折腾直连。 */
    fun markProxyOnly(platform: Platform) {
        if (!platform.isKnown || proxyOnly.get().contains(platform)) return
        proxyOnly.set(proxyOnly.get() + platform)
        // 写偏好是挂起操作，而这里是从加载线程的同步路径上被调用的。
        scope.launch { settings.rememberProxyOnly(platform) }
    }

    /** 拼出网关代理的绝对地址——响应里的 `url` 是相对路径。 */
    fun proxyUrl(result: PlayResolveResult): String {
        val base = baseUrlProvider().trim().trimEnd('/')
        val path = result.url.trim()
        return if (path.startsWith("http://") || path.startsWith("https://")) path else base + path
    }

    /** 直连时客户端要自己附加的请求头（防盗链）。 */
    fun directOf(result: PlayResolveResult): Pair<String, Map<String, String>>? {
        val direct = result.direct ?: return null
        if (direct.url.isBlank()) return null
        return direct.url to direct.headers
    }

    fun cached(request: PlayRequest): PlayResolveResult? {
        val entry = cache[request.cacheKey] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > CACHE_TTL_MS) {
            cache.remove(request.cacheKey)
            return null
        }
        return entry.result
    }

    fun invalidate(request: PlayRequest) {
        cache.remove(request.cacheKey)
    }

    suspend fun resolve(request: PlayRequest): PlayResolveResult {
        val result = api.resolvePlay(request.platform, request.id, request.quality)
        cache[request.cacheKey] = Entry(result, System.currentTimeMillis())
        return result
    }

    /**
     * 供 [SakuraDataSource] 在 ExoPlayer 的加载线程上调用。
     *
     * 那里是阻塞式接口，没法挂起；加载线程本身就不是主线程，`runBlocking` 是安全的。
     */
    fun resolveBlocking(request: PlayRequest): PlayResolveResult {
        cached(request)?.let { return it }
        return runBlocking {
            try {
                resolve(request)
            } catch (error: Throwable) {
                // ExoPlayer 只认 IOException；业务错误翻成它能理解的形状，
                // 文案留给界面从 PlaybackException 的 cause 里取。
                throw if (error is IOException) error else IOException(error.message, error)
            }
        }
    }

    private companion object {
        /** 令牌有效期 10 分钟，缓存留足余量。 */
        const val CACHE_TTL_MS = 3 * 60 * 1000L
    }
}
