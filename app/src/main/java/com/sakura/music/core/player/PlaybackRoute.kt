package com.sakura.music.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedHashMap

/** 这一首歌的字节实际上是从哪儿来的。 */
enum class PlaybackRoute(val label: String, val hint: String) {
    /** 还没读到任何一个字节——刚换歌、或者还在缓冲。界面此时不显示标签。 */
    Unknown("", "还没有数据"),

    /** 读到了本地缓存里的字节，这一遍没联网。 */
    Cached("缓存", "这次播放读的是本地缓存，没有联网"),

    /** 直连平台 CDN，服务器带宽为零。 */
    Direct("CDN", "音频直接从平台 CDN 取流，不经网关"),

    /** CDN 拒了（或压根连不上），字节经网关转发。 */
    Proxy("中转", "直连不可用，音频经网关转发"),
}

/**
 * 记下当前这首实际走的是哪条路。
 *
 * 两处上报，都是真凭实据、不靠猜：
 * - [SakuraDataSource] 真的去连上游时，报直连还是走网关；
 * - 缓存层真的读到了本地字节时（[CacheDataSource] 的回调），报缓存。
 *
 * **上报按曲目分开记**，这不是过度设计：ExoPlayer 会提前把下一首的数据源打开做预加载，
 * 那一次上报发生在「换歌」事件之前。换歌时一律清空的话，自动播到下一首时标签就
 * 永远是空的——预加载探明的路被丢掉了，而真正播放时数据源不会再开一次。
 *
 * 挂在容器上而不是播放中心里：上报发生在播放线程（数据源被打开、缓存被读到的那一刻），
 * 而播放服务与界面各自都能拿到同一个实例。
 */
class PlaybackRouteTracker(
    /** 已下载歌曲的缓存键：这些歌的数据整份在本地，不用等回调就能断定走缓存。 */
    private val downloadedKeys: () -> Set<String> = { emptySet() },
    /**
     * 查询缓存：给定键与区间，返回缓存里已经有的字节数。
     *
     * 做成回调而不是直接持有缓存：缓存是懒加载的，为了判一个角标把它提前建出来不值得。
     */
    private val cachedBytes: (key: String, position: Long, length: Long) -> Long = { _, _, _ -> 0L },
) {

    private val _route = MutableStateFlow(PlaybackRoute.Unknown)
    val route: StateFlow<PlaybackRoute> = _route.asStateFlow()

    /**
     * 每首歌（键是 `平台:ID:音质`）实际走的路线。
     *
     * 网络与缓存的结论都记在同一张表里，**谁后发生谁算数**：第一遍走 CDN 记下 Direct，
     * 整首存下来之后第二遍读缓存就记成 Cached——后一次才是这一遍的真实情况。
     * 反过来记（只认第一次的网络结论）就会出现「断网也能播，角标却写 CDN」。
     *
     * 只留最近 [MAX_TRACKED] 首：它只在换歌那一瞬间有用，听过的老歌没必要一直记着。
     */
    private val routes = object : LinkedHashMap<String, PlaybackRoute>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlaybackRoute>): Boolean =
            size > MAX_TRACKED
    }

    @Volatile
    private var currentKey: String? = null

    /**
     * 换歌。
     *
     * 这一首的路可能已经探明过（被预加载过，或者上一遍播过），那就先用着；
     * 没有才回到 [PlaybackRoute.Unknown]——宁可空一下也别显示错的。
     */
    @Synchronized
    fun resetForTrack(key: String?) {
        currentKey = key
        // 「下载过了」是最硬的证据，优先于任何历史结论：这首歌的数据整份在本地，
        // 走缓存是必然的，不该因为上次联网播过就先显示 CDN 再等回调纠正。
        val initial = when {
            key == null -> PlaybackRoute.Unknown
            downloadedKeys().contains(key) -> PlaybackRoute.Cached
            else -> routes[key] ?: PlaybackRoute.Unknown
        }
        emit(initial)
    }

    /**
     * 数据源被打开的那一刻——也就是这一首刚开始出声的时候——先判一次。
     *
     * 判定要在这个时点做，是因为「走缓存」是个否定事实：整首都命中时根本没人去连上游，
     * 等不到任何网络上报。所以这里主动问缓存：这次要从 [position] 读的字节本地有没有？
     * 有就立刻定成缓存；没有的话，[reportNetwork] 会在上游连上后马上给出直连还是中转。
     *
     * 这样一来，每一首歌在最开始就有结论，不用先顶着上一遍的旧结论再等纠正。
     */
    @Synchronized
    fun onSourceOpened(key: String, position: Long) {
        val hit = runCatching { cachedBytes(key, position, 1) }.getOrDefault(0L) > 0L
        if (!hit) return
        routes[key] = PlaybackRoute.Cached
        // 预加载下一首时这里也会被调用，那种情况下只记账、不动界面。
        if (key == currentKey) emit(PlaybackRoute.Cached)
    }

    /** 数据源真的连了上游：记在这首歌名下，只有它正是当前这首时才改界面。 */
    @Synchronized
    fun reportNetwork(key: String, route: PlaybackRoute) {
        routes[key] = route
        if (key == currentKey) emit(route)
    }

    /**
     * 读到了这首歌在缓存里的字节。
     *
     * [key] 由数据源包装 [KeyAwareDataSource] 提供：回调本身只给字节数，不带上曲目
     * 就分不清「当前这首在读缓存」和「下一首预加载命中了缓存」。
     */
    @Synchronized
    fun reportCachedRead(key: String) {
        routes[key] = PlaybackRoute.Cached
        if (key == currentKey) emit(PlaybackRoute.Cached)
    }

    /**
     * 预解析给出的「预期」路线。
     *
     * 只是兜底：还没播（比如刚恢复的播放现场）时确实没人开过数据源，但用户打开播放页
     * 就该看到这首会走哪条路——解析结果已经能回答这个问题了。
     * 真连上游或读到缓存时的上报都会盖掉它。
     */
    @Synchronized
    fun reportExpected(key: String, route: PlaybackRoute) {
        if (routes.containsKey(key)) return
        if (key != currentKey) return
        emit(route)
    }

    /**
     * 缓存读取的回调每读一块就来一次，值没变就别发——
     * 否则同一首歌每次读盘都要把整棵播放页重组一遍。
     */
    private fun emit(route: PlaybackRoute) {
        if (_route.value != route) _route.tryEmit(route)
    }

    private companion object {
        const val MAX_TRACKED = 64
    }
}
