package com.sakura.music

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.sakura.music.core.player.LyricLine
import com.sakura.music.core.player.KeyAwareDataSource
import com.sakura.music.core.player.PlaybackCenter
import com.sakura.music.core.player.PlaybackResolver
import com.sakura.music.core.player.PlaybackRouteTracker
import com.sakura.music.core.player.SakuraDataSource
import com.sakura.music.core.player.sakuraCacheKeyFactory
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.downloads.DownloadCenter
import com.sakura.music.data.downloads.DownloadStore
import com.sakura.music.data.model.HealthResponse
import com.sakura.music.data.network.NetworkPolicy
import com.sakura.music.data.prefs.PlaybackSessionStore
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.ApiClient
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.repo.AuthRepository
import com.sakura.music.data.repo.LibraryRepository
import com.sakura.music.data.session.LocalCredentialStore
import com.sakura.music.data.session.SakuraCookieJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器。
 *
 * 依赖图很浅，上注解式 DI 框架只会增加仪式感。全部懒加载，冷启动因此很便宜。
 */
class AppContainer(private val appContext: Context) {

    val applicationContext: Context get() = appContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 网关地址。
     *
     * 默认值来自构建配置（`gradle.properties` 里的 `sakura.gateway.url`），
     * 但可以在「设置 → 网络」里改；改过之后以本地保存的为准。
     *
     * 做成 getter 而不是字段：请求链路每次都要拿最新值，缓存成字段会在改地址后继续打旧地址。
     */
    val gatewayBaseUrl: String get() = settings.gatewayUrlNow()

    /** 构建时注入的默认地址。「恢复默认」用的就是它。 */
    val defaultGatewayBaseUrl: String get() = SettingsStore.DEFAULT_GATEWAY_URL

    /**
     * 网关地址变了。
     *
     * 会话 Cookie 是按 host 保存的，换一个 host 之后自然带不上去，所以这里重新问一次
     * 登录态：能继续就继续，不能就回到登录页，而不是等用户点某个操作才发现 401。
     */
    fun onGatewayChanged() {
        scope.launch { authRepository.refresh() }
    }

    /**
     * 试连某个网关地址并回报结果。
     *
     * 两处刻意为之：
     * - 跑在容器自己的作用域里——改完地址根组件可能立刻切到登录页，界面自己的协程会被取消，
     *   而这条提示必须在屏幕变化之后仍然弹得出来；
     * - 回调切回主线程——调用方要弹 Toast，那东西只能在主线程上用。
     */
    fun probeGateway(baseUrl: String, onResult: (Result<HealthResponse>) -> Unit) {
        scope.launch {
            val result = runCatching { api.health(baseUrl) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    val cookieJar: SakuraCookieJar by lazy { SakuraCookieJar(appContext, json) }

    /** 本机绑定的第三方凭据；Android 端没有绑定界面，但透传链路是通的。 */
    val localCredentials: LocalCredentialStore by lazy { LocalCredentialStore(appContext, json) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    /**
     * 音频流用的 client：**不带**会话 Cookie。
     *
     * 直连请求会打到平台 CDN 上，把 Sakura 的登录态带过去是不合适的；
     * 代理地址靠令牌鉴权，本来也不需要 Cookie。
     */
    val playbackHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    // ---- 数据 ----------------------------------------------------------------

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    /**
     * 本地缓存的总管：音频、封面、歌词、歌曲与专辑信息。
     *
     * 放在这里而不是某个页面里，是因为播放服务（写缓存）和设置页（统计、清理）
     * 必须操作同一份缓存，否则设置页报出来的数字会和实际占的不一样。
     */
    /**
     * 已下载歌曲的索引。
     *
     * 比缓存与下载中心都先建：缓存管理器要靠它区分「缓存」和「下载」，
     * 两边都要拿到同一个实例，所以在这里单独提出来。
     */
    val downloadStore: DownloadStore by lazy {
        DownloadStore(File(appContext.filesDir, "downloads"), json)
    }

    val cacheManager: CacheManager by lazy {
        CacheManager(appContext, json, settings, downloadStore).also {
            // 用户改过的上限要在冷启动时恢复：不读一次的话，缓存会一直按默认值跑。
            scope.launch { it.refresh() }
        }
    }

    /** 下载：把整首歌写进音频缓存，并记一笔索引让它不被淘汰。 */
    val downloadCenter: DownloadCenter by lazy {
        DownloadCenter(
            cacheManager = cacheManager,
            upstream = downloadDataSourceFactory,
            store = downloadStore,
            scope = scope,
        )
    }

    /** 缓存设置改完之后调用：新的上限与有效期立刻生效，不用重启。 */
    suspend fun applyCacheSettings() = cacheManager.refresh()

    val apiClient: ApiClient by lazy {
        ApiClient(
            baseUrlProvider = { gatewayBaseUrl },
            client = okHttpClient,
            json = json,
            cookieJar = cookieJar,
            localCredentialProvider = { localCredentials.headerValue() },
            // 会话过期只在一处清理，免得每个调用点各写一遍。
            onSessionExpired = { authRepository.onSessionExpired() },
        )
    }

    val api: SakuraApi by lazy {
        SakuraApi(
            client = apiClient,
            json = json,
            lyricCache = cacheManager.lyricCache,
            metaCache = cacheManager.metaCache,
            // 有效期是用户设定的，每次请求现取，改完设置下一条请求就用新值。
            lyricTtlMillis = { cacheManager.lyricTtlMillis },
            metaTtlMillis = { cacheManager.metaTtlMillis },
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(api = api, cookies = cookieJar, settings = settings)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(api = api, auth = authRepository)
    }

    // ---- 播放 ----------------------------------------------------------------

    /** 上次的播放现场：退出应用后再进来要能接着听。 */
    val playbackSessionStore: PlaybackSessionStore by lazy {
        PlaybackSessionStore(appContext, json)
    }

    val playbackResolver: PlaybackResolver by lazy {
        PlaybackResolver(
            api = api,
            settings = settings,
            baseUrlProvider = { gatewayBaseUrl },
            scope = scope,
        )
    }

    /**
     * 这一首歌的字节究竟走的哪条路（直连 / 网关 / 本地缓存）。
     *
     * 播放服务（上报）与播放器页（显示）都要拿同一个实例，所以挂在容器上。
     */
    val playbackRoute: PlaybackRouteTracker by lazy {
        PlaybackRouteTracker(
            downloadedKeys = { downloadStore.cacheKeys() },
            // 只在缓存已经建出来时读它：为了判角标去建一个缓存不值得。
            cachedBytes = { key, position, length ->
                cacheManager.audioCacheOrNull()?.getCachedBytes(key, position, length) ?: 0L
            },
        )
    }

    /**
     * 「仅 Wi-Fi」的执行者：数据源在打开时问它能不能联网。
     *
     * 设置是异步读的，而数据源在播放线程上没有挂起的机会，所以它把开关同步缓存在内存里。
     */
    val networkPolicy: NetworkPolicy by lazy {
        NetworkPolicy(appContext, settings, scope)
    }

    /** 播放取流用：受「仅 Wi-Fi」约束。 */
    val sakuraDataSourceFactory: SakuraDataSource.Factory by lazy {
        SakuraDataSource.Factory(
            resolver = playbackResolver,
            client = playbackHttpClient,
            routeTracker = playbackRoute,
            networkPolicy = networkPolicy,
        )
    }

    /**
     * 下载用：不受「仅 Wi-Fi」约束。
     *
     * 用户主动点下载是明确意图；那条开关防的是「自动播放偷偷跑流量」，
     * 不是「不许我下歌」。
     */
    private val downloadDataSourceFactory: SakuraDataSource.Factory by lazy {
        SakuraDataSource.Factory(
            resolver = playbackResolver,
            client = playbackHttpClient,
            routeTracker = playbackRoute,
        )
    }

    /**
     * 播放器真正用的数据源：在 [sakuraDataSourceFactory] 外面再包一层 [CacheDataSource]。
     *
     * 缓存键是播放项的 URI（`平台 / ID / 音质`）——解析出来的真实地址带令牌、十分钟就过期，
     * 拿它当键的话缓存永远命中不了。同一首歌第二遍就完全读本地，不再碰上游。
     *
     * `FLAG_IGNORE_CACHE_ON_ERROR`：缓存文件本身坏了（写了一半、被别的东西清过）时
     * 直接回源，而不是让播放失败。
     */
    val playbackDataSourceFactory: DataSource.Factory = DataSource.Factory {
        // 每次开流都现问一次：用户把音频缓存拨到「不缓存」之后，下一条播放就不用缓存了，
        // 不必等播放服务重建。
        val cache = cacheManager.audioCacheOrNull()
        if (cache == null) {
            sakuraDataSourceFactory.createDataSource()
        } else {
            // 每个数据源实例各自拿着「自己正在读哪首」：缓存回调不报曲目，
            // 不带上它，下一首预加载命中缓存时会把当前这首误标成缓存。
            val activeKey = AtomicReference<String?>(null)
            val cached = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(sakuraDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // 缓存键统一成 `平台:ID:音质`（默认是整条 sakura:// URI 拼上参数）。
                // 下载、统计大小、删除都按这个键来，不一致就会出现「下了但查不到」。
                .setCacheKeyFactory(sakuraCacheKeyFactory)
                // 读到本地字节才算「走的缓存」：没这个回调就只能靠「没联网」去猜。
                .setEventListener(object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        activeKey.get()?.let { playbackRoute.reportCachedRead(it) }
                    }

                    /** 为什么这一段没走缓存（比如在写、或者被要求忽略）；界面不关心。 */
                    override fun onCacheIgnored(reason: Int) = Unit
                })
                .createDataSource()
            KeyAwareDataSource(
                inner = cached,
                keyHolder = activeKey,
                onOpened = { key, position -> playbackRoute.onSourceOpened(key, position) },
            )
        }
    }

    /**
     * 歌词缓存。
     *
     * 播放器页每次进入都会重新组合，歌词不缓存就会反复请求；这些数据在一次会话里
     * 基本不变，按 平台:ID 存着即可。
     */
    val lyricCache = ConcurrentHashMap<String, List<LyricLine>>()

    /**
     * 全应用唯一的播放入口。
     *
     * 队列是全局的：列表页点一下就得能播，播放器页与迷你播放条看到的必须是同一份状态，
     * 所以它挂在容器上而不是某个页面的 ViewModel 里。
     */
    val playbackCenter: PlaybackCenter by lazy {
        PlaybackCenter(
            api = api,
            library = libraryRepository,
            resolver = playbackResolver,
            settings = settings,
            json = json,
            lyricCache = lyricCache,
            sessionStore = playbackSessionStore,
            routeTracker = playbackRoute,
            networkPolicy = networkPolicy,
            cacheManager = cacheManager,
            scope = scope,
        )
    }
}
