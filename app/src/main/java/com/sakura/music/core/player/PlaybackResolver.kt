package com.sakura.music.core.player

import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlayResolveResult
import com.sakura.music.data.prefs.RoutePreference
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** 用户给每个平台锁的取流方式；没设过就是「智能」。 */
    private val preferences = AtomicReference<Map<Platform, RoutePreference>>(emptyMap())

    /** 锁定「直连」时连续失败了几首（平台 → 首数）。 */
    private val directFailures = ConcurrentHashMap<Platform, Int>()

    /** 上一次失败的曲目键。ExoPlayer 会为同一首歌重试多次，那些不该各算一次。 */
    private val lastFailedKey = ConcurrentHashMap<Platform, String>()

    private val _directIssues = MutableSharedFlow<Platform>(extraBufferCapacity = 1)

    /** 锁了直连却怎么都连不上：界面据此提醒用户去设置里换一种连接方式。 */
    val directIssues: SharedFlow<Platform> = _directIssues.asSharedFlow()

    init {
        scope.launch {
            settings.proxyOnlyPlatforms.collect { proxyOnly.set(it) }
        }
        // 每个平台一条流：改完设置，下一条播放就用新方式。
        Platform.entries.filter { it.isKnown }.forEach { platform ->
            scope.launch {
                settings.routePreference(platform).collect { value ->
                    setPreference(platform, value)
                    // 换了连接方式就重新计数：之前那几次失败属于旧设定，不该算在新设定头上。
                    directFailures.remove(platform)
                    lastFailedKey.remove(platform)
                }
            }
        }
    }

    @Synchronized
    private fun setPreference(platform: Platform, value: RoutePreference) {
        preferences.set(preferences.get() + (platform to value))
    }

    fun preferenceOf(platform: Platform): RoutePreference =
        preferences.get()[platform] ?: RoutePreference.Default

    fun isProxyOnly(platform: Platform): Boolean = proxyOnly.get().contains(platform)

    /**
     * 这一首要不要先试直连。
     *
     * 「中转」直接跳过；「智能」看这个平台以前有没有被拒过；「直连」永远试。
     */
    fun mayUseDirect(platform: Platform): Boolean = when (preferenceOf(platform)) {
        RoutePreference.Direct -> true
        RoutePreference.Proxy -> false
        RoutePreference.Smart -> !isProxyOnly(platform)
    }

    /**
     * 直连没打开。
     *
     * 「智能」下要分两种失败：
     * - **4xx**（防盗链明确拒绝）是确定性的，重试多少次都一样 → 记下来，这个平台以后走中转；
     * - **连不上 / 超时 / 5xx** 多半只是这一次不走运 → **不记账**，这一首照样换中转先放出来，
     *   但下次播放仍会试直连。这样一次网络抖动不会把平台永久钉在中转上。
     *
     * 「直连」下路是用户自己锁死的，系统不替他改道，只能攒够几首提醒他去设置里换——
     * 提示而不是自动切换，为的是不让「锁定」这个承诺落空。
     *
     * [key] 是曲目键：ExoPlayer 自己会为同一首歌重试几次，每次都重新 open 一遍数据源，
     * 照单全收的话**一首歌**就能把提醒撞出来。所以同一首只算一次。
     */
    fun noteDirectFailure(platform: Platform, key: String, statusCode: Int) {
        when (preferenceOf(platform)) {
            RoutePreference.Smart -> if (isRefusal(statusCode)) markProxyOnly(platform)

            RoutePreference.Proxy -> Unit

            RoutePreference.Direct -> {
                if (lastFailedKey.put(platform, key) == key) return
                val count = (directFailures[platform] ?: 0) + 1
                directFailures[platform] = count
                // 只提醒一次：还失败说明用户没去改，反复弹就成了骚扰。
                if (count == DIRECT_FAILURE_HINT) _directIssues.tryEmit(platform)
            }
        }
    }

    /** 4xx 是「这个请求被明确拒绝了」，换个时间再试也是同样的结果。 */
    private fun isRefusal(statusCode: Int): Boolean = statusCode in 400..499

    /**
     * 直连成功了。
     *
     * 除了清掉连续失败计数，还要**忘掉**这个平台「直连不通」的旧记录——
     * 不这么做的话，用户把连接方式锁成「直连」验证通过、再切回「智能」时，
     * 智能仍然照着老印象跳过直连，而设置里已经没有手动清除的入口了。
     */
    fun noteDirectSuccess(platform: Platform) {
        directFailures.remove(platform)
        lastFailedKey.remove(platform)
        forgetProxyOnly(platform)
    }

    /** 直连被 CDN 拒了：记进偏好，这个平台以后不再折腾直连。 */
    fun markProxyOnly(platform: Platform) {
        if (!platform.isKnown || proxyOnly.get().contains(platform)) return
        proxyOnly.set(proxyOnly.get() + platform)
        // 写偏好是挂起操作，而这里是从加载线程的同步路径上被调用的。
        scope.launch { settings.rememberProxyOnly(platform) }
    }

    private fun forgetProxyOnly(platform: Platform) {
        if (!platform.isKnown || !proxyOnly.get().contains(platform)) return
        proxyOnly.set(proxyOnly.get() - platform)
        // 和记住的时候一样：失败/成功都发生在加载线程的同步路径上，落盘只能丢给协程。
        scope.launch { settings.forgetProxyOnly(platform) }
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

        /** 锁定直连时连着失败这么多次，就提醒用户去设置里换连接方式。 */
        const val DIRECT_FAILURE_HINT = 3
    }
}
