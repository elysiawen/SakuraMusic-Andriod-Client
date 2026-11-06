package com.sakura.music.core.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.model.qualityLabel
import com.sakura.music.data.network.NetworkPolicy
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.prefs.PlaybackSession
import com.sakura.music.data.prefs.PlaybackSessionStore
import com.sakura.music.data.prefs.RadioLive
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * 正在听的电台（私人 FM / 猜你喜欢）。它不是一份歌单，而是会不断往下补的流。
 *
 * 与网页端一致：电台流只由 [PlaybackCenter.stopRadio] 结束，空批次只进入冷却重试，
 * 不因为上游一次抖动就永久停播。
 */
data class RadioState(
    val platform: Platform,
    /** 网关的电台会话号：服务端据此记住这个电台已经推过哪些歌。 */
    val session: String = "",
)

/** 界面看到的播放状态快照。 */
data class PlaybackState(
    val queue: List<UnifiedTrack> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /**
     * 当前进度。
     *
     * 由主线程的轮询循环维护（见 `PlaybackCenter.startPolling`）。**别绕开这两个字段
     * 去读 `MediaController`**：它只能在创建它的线程上用，后台协程直接读会当场
     * 抛 `IllegalStateException: MediaController method is called from a wrong thread`。
     */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** 音量（0~1），同样由主线程轮询维护。 */
    val volume: Float = 1f,
    val error: String? = null,
    /**
     * 「这一首**确定播不了**」时的曲目 key（无音源 / 版权或会员限制 / 格式坏了 / 源 404）。
     *
     * 与 [error] 分开是因为它们的用途不同：[error] 是给用户看的整屏错误卡，
     * 这个是给「一起听」的判据——只有确定性播不了才该把整首歌跳掉，网络抖动不能。
     */
    val unplayableKey: String? = null,
    /** 当前曲目只能听到试听片段（受版权或会员限制）。 */
    val trial: Boolean = false,
    /** true = 直连平台 CDN，false = 字节经网关转发。这是**预先**判断的结果。 */
    val usingDirect: Boolean = false,
    /**
     * 当前这一首**实际拿到**的档位。网关会降级（会员等级不够时"按能给的给"），
     * 所以它可能低于设置里的偏好——界面上说「当前音质」用的是这个，不是偏好。
     * `null` = 还没解析出来，此时别显示「当前音质」。
     */
    val actualQuality: Quality? = null,
    /**
     * 当前这一首**已知存在**的档位；`null` = 清单完全不知道（网关没给 `qualities` 字段），
     * 此时界面按全量档位列。
     *
     * 注意它未必**完整**：网易云的搜索与歌单只带基础档，补一次单曲详情才会齐。
     * 但"不完整"与"不知道"是两回事——不完整时列表里的每一条都是真的存在，
     * 所以照样只列这些，宁可暂时少列，也不显示本曲其实没有的档位。
     */
    val qualityOptions: List<Quality>? = null,
    /** 当前这一首实际用的音源平台（偏好音源命中时可能不是 `sources[0]`）。 */
    val activePlatform: Platform? = null,
    val lyric: List<LyricLine> = emptyList(),
    val lyricLoading: Boolean = false,
    /** 当前这份歌词带字级时间轴（YRC / QRC），按字高亮；false 时退回按行。 */
    val lyricWordByWord: Boolean = false,
    /** 非空 = 这份队列是电台流，快见底时自动续歌，且不回卷到旧歌。 */
    val radio: RadioState? = null,
    /** 正在向网关要下一批，挡住 400ms 一拍的轮询打出并发请求。 */
    val radioLoading: Boolean = false,
) {
    val current: UnifiedTrack? get() = queue.getOrNull(index)

    val hasLyrics: Boolean get() = lyric.isNotEmpty()
}

/**
 * 房间接管时的「播放意图」转交口。
 *
 * 「点一首歌」的入口散在七八个页面里（搜索、歌单、专辑、发现、收藏…），全都在调
 * [PlaybackCenter.play]；一个个改过去既容易漏、两条路也会慢慢长歪。在这里转一次，
 * 语义就和网页端的 `useTransport` 对齐了：**房间里点歌 = 点歌**（`playNow`，
 * 不在共享队列里就加进去再播），**播放整张列表 = 换歌单**（`setQueue`）。
 *
 * 由容器在装配时接上（实现见 `RoomSync`）。没接上、或本机不在房间里时返回 false，
 * 一切照旧走本地播放器。
 */
interface RoomPlaybackBridge {

    /** 点一首歌。返回 true 表示已交给房间，调用方不要再动本地播放器。 */
    fun onPlayTrack(track: UnifiedTrack): Boolean

    /** 播放一整个列表（「播放全部」）。 */
    fun onReplaceQueue(tracks: List<UnifiedTrack>, startIndex: Int): Boolean
}

/**
 * 全应用唯一的播放入口。
 *
 * 播放器本体活在 [MusicPlaybackService] 里，这里握着它的 `MediaController`，
 * 把「队列 + 当前曲目 + 进度 + 歌词 + 音源/音质偏好」整理成一份界面可用的状态。
 *
 * 之所以做成容器级的单例而不是某个页面的 ViewModel：队列是全局的，
 * 列表页点一下就得能播，播放器页和迷你播放条看到的必须是同一份状态。
 */
class PlaybackCenter(
    private val api: SakuraApi,
    private val library: LibraryRepository,
    private val resolver: PlaybackResolver,
    private val settings: SettingsStore,
    private val json: Json,
    private val lyricCache: ConcurrentHashMap<String, List<LyricLine>>,
    private val sessionStore: PlaybackSessionStore,
    /**
     * 换歌时重置路由。
     *
     * 界面直接订阅 [PlaybackRouteTracker.route]，不经过 [PlaybackState]：上报发生在
     * 播放线程、和播放状态的其他字段不同源，多绕一道反而容易漏更新。
     */
    private val routeTracker: PlaybackRouteTracker,
    /** 判断「仅 Wi-Fi」下这一首还能不能播，以及它有没有缓存。 */
    private val networkPolicy: NetworkPolicy,
    private val cacheManager: CacheManager,
    private val scope: CoroutineScope,
    /**
     * 「这个平台还允许听电台吗」由容器注入（读平台开关偏好）。
     * 不直接依赖偏好仓库：它与播放器在装配时互相引用，注入一份判定即可避免构造环。
     */
    private val radioPlatformAllowed: (Platform) -> Boolean = { true },
    /** 是否正在「一起听」：电台与房间互斥，覆盖邀请链接、自动回房这些绕过 UI 的路径。 */
    private val roomActive: () -> Boolean = { false },
) {

    // MediaController 只能在它被创建的那个线程（主线程）上访问。
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** 当前播放模式：顶栏那个循环按钮要看着它画图标、给不给强调色。 */
    private val _playMode = MutableStateFlow(PlayMode.All)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /**
     * 用户**自己**的播放模式（设置里那份）。
     *
     * 房间接管期间 [playMode] 显示的是**房间下发的**模式（见 [showRoomPlayMode]），两者在房间里
     * 就会不一样：退房时要把用户自己这份还回去，而不是把房间的模式留成他的默认（见 [setRoomPlayMode]）。
     */
    private var personalPlayMode: PlayMode = PlayMode.All

    /**
     * 一次性提示（不阻断界面）。
     *
     * 有些情况不该塞进 [PlaybackState.error]：那个是整屏的「无法播放」浮层，
     * 而「仅 Wi-Fi 拦下了这一首」只是「这次没播成」，用户在列表页点一下就该收到这句话，
     * 而不是被弹到播放器页看一个错误页。
     */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * 用户拖动进度条（或任何主动 seek）。
     *
     * 单独开一条流而不是并进 [state]：多设备那边要的是「刚刚 seek 了」这个**事件**，
     * 而不是「进度是多少」这个状态——后者每条都有，前者才有上报的价值。
     */
    private val _seeks = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val seeks: SharedFlow<Unit> = _seeks.asSharedFlow()

    /**
     * 一首歌**自然播完**（不是用户切歌、也不是被换掉）的事件，带的是刚播完那首的 key。
     *
     * 「一起听」靠它上报 `ended`，由服务端统一决定下一首——本机不自行切歌，
     * 否则两端会各切一次，队列立刻错位。
     */
    private val _ended = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val ended: SharedFlow<String> = _ended.asSharedFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    private var controller: MediaController? = null
    private var quality: Quality = Quality.Default
    private var preferredPlatform: Platform? = null
    private var pollJob: Job? = null

    /** 报错之后的复检（见 [verifyPlayerHealthy]）：每次报错只留最新那一个。 */
    private var errorCheckJob: Job? = null

    /**
     * 暂停落盘（一起听接管期间）。
     *
     * 房间把播放器的队列换成了共享队列，这时候要是还往「个人现场」里写，
     * 退房之后恢复出来的就是房间的歌——用户自己的那份现场被永久盖掉了。
     */
    private var persistencePaused = false

    /** 房间接管期间：播放顺序（循环 / 随机）由房间决定，用户设置暂时让位。 */
    private var roomPlayMode = false

    /**
     * 已经下发给播放器的速率。
     *
     * 镜像（设备跟随 / 一起听）每 200 毫秒算一次偏差，但结论多数时候是「保持」；
     * 靠这个记着上次真正发出去的值，就不必每秒五次白发一条跨进程命令（见 [setPlaybackSpeed]）。
     */
    private var appliedSpeed = 1f

    /**
     * 房间接管时的播放意图转交口，见 [RoomPlaybackBridge]。
     *
     * 用可写字段而不是构造参数：转交口的实现（`RoomSync`）本身要依赖播放器，
     * 构造期注入会变成环；反正只有容器会在一处接上它。
     */
    var roomBridge: RoomPlaybackBridge? = null

    /* ------------------------------ 电台续播 ------------------------------ */

    /** 当前电台；所有读写都发生在主线程（[mainScope]），不用额外加锁。 */
    private var radio: RadioState? = null
    /** 上次发起续播的时刻；成功留一个短节流，失败留作 20 秒冷却。 */
    private var radioRetryAt = 0L
    /** 提示的节流：别把轮询每一拍都变成一条提示。 */
    private var radioErrorAt = 0L
    /** 正在进行的续播；点「下一首」时复用它，不重复打上游。 */
    private var radioInFlight: Deferred<Int>? = null
    /** 已经播到队尾、正在等续播：补歌回来后自动接着播。 */
    private var radioTailStopped = false
    /**
     * 电台代数：开/关一次加一。续播请求回来时靠它判断「还是不是刚才那个电台」，
     * 免得退出后立刻重开同平台时，上一轮在途的批次被追加进新队列。
     */
    private var radioGeneration = 0

    /**
     * 上一次同步到的当前曲目 key。
     *
     * 只在为了回答一个问题：自动切歌时「刚播完的是哪一首」。Media3 的
     * `onMediaItemTransition` 给的是**新**曲目，而一起听要上报的正是结束的那一首。
     */
    private var lastKey: String? = null

    /**
     * 已经提示过的降级组合（`请求档位>实际档位`）。
     *
     * 同一种只提示一次：会员等级不够的账号**每一首**都会降到同一档，每首都弹一句就成了骚扰。
     * 提示过之后，界面上的「正在播放」标记仍然会一直在音质弹层里显示，信息没有丢。
     */
    private val notifiedDowngrades = mutableSetOf<String>()

    /**
     * 已经因为「缓存里是试听片段」重装过的曲目。
     *
     * 每首只修一次：判据是「播放器报的时长明显短于歌曲长度」，正常歌曲偶尔也会因为
     * 元数据不准落进这条线，重装一次还不对就说明不是缓存的问题，没必要反复折腾。
     */
    private val healedCacheKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            settings.quality.collect { next ->
                if (next == quality) return@collect
                val previous = quality
                quality = next
                // 切换音质要用新档位重新取地址，并保持当前进度。
                if (previous != next) mainScope.launch { rebuildKeepingPosition() }
            }
        }
        scope.launch {
            settings.preferredPlatform.collect { next ->
                if (next == preferredPlatform) return@collect
                preferredPlatform = next
                mainScope.launch { rebuildKeepingPosition() }
            }
        }
        scope.launch {
            settings.playMode.collect { mode ->
                // 设置里那份永远记下来（退房要还回去），但它管不到房间期间显示的那个值。
                personalPlayMode = mode
                // 房间接管期间模式由房间说了算：设置值别把界面上的模式盖掉，也别去动播放器。
                if (roomPlayMode) return@collect
                _playMode.value = mode
                mainScope.launch { applyPlayMode(mode) }
            }
        }
        // 锁了直连却总连不上：告诉用户去哪儿改。这里只提示、不代他改——
        // 「锁定」是他自己设的，系统不该背着他换线路。
        scope.launch {
            resolver.directIssues.collect { platform ->
                _notice.value = "「${platform.label}」直连连续失败，可在「设置 → 网络」里把连接方式改为「智能」或「中转」"
            }
        }
    }

    /* ------------------------------ 连接 ------------------------------ */

    fun attach(controller: MediaController?) {
        mainScope.launch {
            this@PlaybackCenter.controller?.removeListener(listener)
            this@PlaybackCenter.controller = controller
            if (controller == null) {
                _state.value = PlaybackState()
                return@launch
            }
            controller.addListener(listener)
            // 记住播放器**当前实际**的速率：镜像那边是「设过了就不再设」，值不能凭默认猜——
            // 换一个控制器接上来时，播放器可能还留着上一次留下的 ±5%。
            appliedSpeed = controller.playbackParameters.speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            /*
             * 播放模式是**播放器上**的状态，不是我们的：服务（播放器）可能是刚造出来的，
             * 默认是顺序播放。而设置里那份通常在这之前就发出来了（`init` 里 collect，
             * 那时 controller 还是 null，applyPlayMode 直接返回）——不在这里补一刀，
             * 冷启动后「列表循环」就只是界面上那个图标：播到最后一首照样停。
             */
            applyPlayMode(personalPlayMode)
            syncQueue()
            syncFlags()
            _state.update { it.copy(positionMs = controller.currentPosition.coerceAtLeast(0L)) }
            restoreSession()
            startPolling()
        }
    }

    /**
     * 恢复上次的播放现场。
     *
     * 只把队列装回去、**不 prepare**：启动时不该为了「上次那首歌」立刻去打上游取流。
     * 队列一旦非空，迷你播放条就会出现（正是用户要的那个「小条子」），
     * 进度取上次存下的位置，按播放时 ExoPlayer 会从这个位置接着放。
     */
    private fun restoreSession() {
        val active = controller ?: return
        if (active.mediaItemCount > 0) return

        val saved = sessionStore.load() ?: return
        val index = saved.index.coerceIn(0, saved.queue.lastIndex)
        val position = saved.positionMs.coerceAtLeast(0L)

        active.setMediaItems(itemsOf(saved.queue), index, position)
        syncQueue()
        _state.update {
            it.copy(
                index = index,
                positionMs = position,
                // 还没 prepare，控制器报不出时长，先用歌曲自带的时长顶着，
                // 免得播放器页的进度条是条死的。
                durationMs = saved.queue.getOrNull(index)?.durationMs ?: 0L,
            )
        }

        // 电台现场跟着队列一起回来：队列里的歌还在，快放完时就能接着自动续。
        // 退出电台时落盘里的 radioLive 会被立刻删掉，所以这里不会有「已退出的电台复活」。
        val live = saved.radioLive
        if (live != null && live.platform.isKnown && radioPlatformAllowed(live.platform) && !roomActive()) {
            val restored = RadioState(live.platform, live.session)
            radio = restored
            _state.update { it.copy(radio = restored) }
            applyRadioMode(active)
        } else if (live != null) {
            clearPersistedRadio()
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val finished = lastKey
            syncQueue()
            syncFlags()
            onTrackChanged()
            // 自动切歌 = 上一首放完了：把**刚结束的那一首**报出去（新曲目 Media3 已经切好了）。
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val now = _state.value.current?.key
                if (finished != null && finished != now) _ended.tryEmit(finished)
            }
            // 换歌就落盘：哪怕下一秒进程被杀，下次进来的也是这一首。
            persistSession()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            // 暂停时把进度记下来——用户大多是从「暂停」离开应用的。
            if (!isPlaying) persistSession()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            // 队尾播完（循环关闭时才会出现）：同样算「这首结束了」。
            if (playbackState == Player.STATE_ENDED) {
                _state.value.current?.key?.let { _ended.tryEmit(it) }
                // 电台是「一路往前」的流：提前续播还没回来时别停在最后一首，补到歌就接着放。
                if (radio != null && !radioTailStopped) {
                    radioTailStopped = true
                    refillRadio(force = true)
                }
            }
            syncFlags()
        }

        override fun onPlayerError(error: PlaybackException) {
            /*
             * 原始信息留在日志里。
             *
             * 翻成中文之后，「Unexpected runtime error」这种话什么也说明不了：它是 ExoPlayer
             * 对「内部冒出来的运行时异常」的统一说法，错在哪儿只有 errorCode 与 cause 链知道。
             * 真出问题时这份日志就是唯一线索（`adb logcat -s SakuraPlayback`）。
             */
            Log.w(
                LOG_TAG,
                "player error: code=${error.errorCode}" +
                    "(${PlaybackException.getErrorCodeName(error.errorCode)})" +
                    " state=${controller?.playbackState}" +
                    " track=${_state.value.current?.key}" +
                    " message=${error.message}",
                error,
            )
            _state.update {
                it.copy(
                    error = describePlaybackError(error),
                    isBuffering = false,
                    // 只有「确定性播不了」才标记：网络抖动不该让整首歌被跳过。
                    unplayableKey = it.current?.key?.takeIf { _ -> isUnplayableFailure(error) },
                )
            }
            verifyPlayerHealthy()
        }
    }

    /**
     * 报错之后隔一小会儿再看一眼播放器：它要是好好的，就把这条错误收回去。
     *
     * 「无法播放」那张卡片说的是一件很确定的事——**这首歌放不出来**。可 ExoPlayer 也会报
     * 与正在播的这一首无关的错误：命令层面、内部状态层面的失败都被它归成
     * `Unexpected runtime error`，而声音照常在走。此时卡片在那儿说放不了，而且它只会被
     * 下一次换歌清掉——于是就成了「明明在播，却一直说无法播放」。
     *
     * 判据交给播放器自己：真出问题（取流失败、解析失败）时它会落到 IDLE，这条错误就留着，
     * 让用户去重试；反之（READY，还能继续播）就撤销它。撤销也记一条日志——问题可以自愈，
     * 但不该消失得无影无踪。
     */
    private fun verifyPlayerHealthy() {
        errorCheckJob?.cancel()
        errorCheckJob = mainScope.launch {
            delay(ERROR_VERIFY_MS)
            val active = controller ?: return@launch
            if (active.playbackState != Player.STATE_READY) return@launch
            if (_state.value.error == null) return@launch
            Log.i(LOG_TAG, "player is READY ${ERROR_VERIFY_MS}ms after the error; withdrawing it")
            _state.update { it.copy(error = null) }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = mainScope.launch {
            var ticks = 0
            while (true) {
                val active = controller
                if (active == null) break
                // 还没 prepare（例如刚从本地恢复出来的现场）时控制器只会报 0，
                // 用它覆盖会把上次存下的进度抹掉。
                if (active.playbackState != Player.STATE_IDLE) {
                    _state.update {
                        it.copy(
                            positionMs = active.currentPosition.coerceAtLeast(0L),
                            durationMs = durationOf(active),
                            isPlaying = active.isPlaying,
                            // 音量也一起带出来：外面（多设备上报）读不到 MediaController。
                            volume = active.volume.coerceIn(0f, 1f),
                        )
                    }

                    // 缓存里如果是以前那份试听片段，这里会把它扔掉重来一次。
                    healTruncatedCache(active)

                    // 隔一阵子把进度也记下来。只靠「换歌 / 暂停」两个时机的话，
                    // 一直放着的时候进度会停在很早以前。
                    if (++ticks >= PERSIST_EVERY_TICKS) {
                        ticks = 0
                        if (active.isPlaying) persistSession()
                    }
                }
                // 电台续播在换歌时触发，失败后的冷却重试则靠这一拍补上；冷却与在途请求都在
                // maybeRefill 里挡着，400ms 一拍不会打出并发请求。
                maybeRefill()
                delay(400)
            }
        }
    }

    /** 控制器报不出时长时（还没 prepare，或流本身没有总长）回落到歌曲元数据里的时长。 */
    private fun durationOf(active: MediaController): Long {
        val reported = active.duration
        if (reported > 0) return reported
        return _state.value.current?.durationMs ?: 0L
    }

    /**
     * 本地缓存里是「以前那份试听片段」时，把它扔掉重来一次。
     *
     * 音频缓存的键只有「平台 / ID / 音质」，管不了这份内容是完整版还是 30 秒片段：
     * 没会员时听过的歌会把片段当成整首写进缓存，等借到会员再放，开头那段读的仍是本地
     * 那份文件，放完就停——听起来就是「明明借到会员了，安卓还是在放试听」。
     *
     * 判据是硬的：解析结果是完整版（[PlaybackState.trial] 为 false），但播放器报的时长
     * 明显短于歌曲本身。命中就把这个键的缓存删掉、从当前位置重装一次，这次读到的是网络
     * 上的完整版。每首只修一次，见 [healedCacheKeys]。
     */
    private fun healTruncatedCache(active: MediaController) {
        val state = _state.value
        val track = state.current ?: return
        val expected = track.durationMs
        val actual = active.duration
        if (expected <= 0L || actual <= 0L) return
        if (actual >= expected * FRAGMENT_DURATION_RATIO) return
        // 真的只有试听权限：那片段就是全部，删缓存也没用。
        if (state.trial) return
        // 没开音频缓存时不存在「缓存里的片段」，也就没什么可删的。
        if (cacheManager.audioCacheOrNull() == null) return
        if (!healedCacheKeys.add(track.key)) return

        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource ?: return
        val request = PlayRequest(source.platform, source.id, quality)
        val position = active.currentPosition.coerceAtLeast(0L)
        Log.i(LOG_TAG, "缓存里是试听片段，删除后重装：${track.title}（${actual}ms / ${expected}ms）")
        mainScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { cacheManager.removeAudio(request.cacheKey) }
            }
            // 进度保留：这一首可能已经放到几十秒，从头再来会让人以为播放被打断了。
            playDirect(state.queue, state.index, position)
        }
    }

    private fun syncFlags() {
        val active = controller ?: return
        _state.update {
            it.copy(
                isPlaying = active.isPlaying,
                isBuffering = active.playbackState == Player.STATE_BUFFERING,
                durationMs = durationOf(active),
                volume = active.volume.coerceIn(0f, 1f),
            )
        }
    }

    private fun syncQueue() {
        val active = controller ?: return
        val tracks = (0 until active.mediaItemCount).mapNotNull { position ->
            active.getMediaItemAt(position).trackOrNull(json)
        }
        val index = active.currentMediaItemIndex.takeIf { it in tracks.indices } ?: -1
        _state.update { it.copy(queue = tracks, index = index) }
        // 记下这一帧的曲目，下一次自动切歌时就知道"刚播完的是哪一首"。
        lastKey = _state.value.current?.key
    }

    /** 换歌：清掉上一首的错误与试听标记，重新取歌词、记一次播放、解析一次地址。 */
    private fun onTrackChanged() {
        val track = _state.value.current ?: return
        // 音源要和 toMediaItem 挑的完全一致，否则路由的键对不上，
        // 数据源上报时就认不出这是当前这首。
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource
        val platform = source?.platform
        routeTracker.resetForTrack(source?.let { PlayRequest(it.platform, it.id, quality).cacheKey })
        _state.update {
            it.copy(
                error = null,
                // 换歌了：上一首"播不了"的结论不能留给下一首，否则它会被无端跳过。
                unplayableKey = null,
                trial = false,
                // 上一首的档位结论不能留给下一首：解析出来之前谁也不知道新的一首会降到哪。
                actualQuality = null,
                // 曲目自带清单的话当场就能筛，不能等异步那一步——否则刚换歌时打开音质弹层，
                // 会短暂把「本曲其实没有的档位」也列出来。清单不完整时这里拿到的是已知的那几档
                // （都是真的存在），完整的清单随后由 syncQualityOptions 补上。
                qualityOptions = track.availableQualities,
                lyric = emptyList(),
                lyricLoading = false,
                lyricWordByWord = false,
                activePlatform = platform,
            )
        }

        mainScope.launch {
            loadLyrics(track)
            primeResolve(track)
        }
        // 补档位清单要额外打一次单曲详情，和上面两件事互不依赖，单独一个协程——
        // 排在 primeResolve 后面的话，它会白白多等一次网络往返。
        mainScope.launch { syncQualityOptions(track) }
        // 顺手把封面存到本地：等真要离线听了再去取就来不及了。
        scope.launch { cacheManager.warmUpCover(track.album.cover) }
        scope.launch { runCatching { library.recordPlay(track) } }
        // 最后一首开始播了才补下一批：首页那批按顺序放完，期间不打扰上游。
        maybeRefill()
    }

    /* ------------------------------ 队列操作 ------------------------------ */

    /**
     * 构建播放项。
     *
     * 封面字节只给 [artworkIndex] 那一首：字节会随媒体项一起跨进程传给 SystemUI，
     * 给整个队列都塞上既慢又占内存，而系统媒体条只关心当前这一首。
     */
    private fun itemsOf(
        tracks: List<UnifiedTrack>,
        artworkIndex: Int? = null,
        artwork: ByteArray? = null,
    ): List<MediaItem> = tracks.mapIndexed { index, track ->
        track.toMediaItem(
            json = json,
            quality = quality,
            platform = preferredPlatform,
            artworkData = artwork.takeIf { index == artworkIndex },
        )
    }

    /** 取某一首的封面字节；本地还没存过就是 null（那系统只能靠 URL 自己联网取）。 */
    private suspend fun artworkOf(track: UnifiedTrack?): ByteArray? =
        cacheManager.coverBytes(track?.album?.cover)

    /**
     * 「仅 Wi-Fi」开着、当前又是计费网络，而这一首在本地没有半个字节。
     *
     * 这种情况下不进播放器：进去也只是数据源抛异常，用户看到的是整屏「无法播放」，
     * 还得回头猜是网络问题。直接拦下并说清楚原因，比让他自己去试要友好。
     */
    private fun blockedByWifiOnly(track: UnifiedTrack): Boolean {
        if (networkPolicy.mayFetchAudio()) return false
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource
            ?: return false
        val cacheKey = PlayRequest(source.platform, source.id, quality).cacheKey
        val cached = runCatching {
            cacheManager.audioCacheOrNull()?.getCachedBytes(cacheKey, 0, 1) ?: 0L
        }.getOrDefault(0L)
        if (cached > 0L) return false

        _notice.value = NetworkPolicy.METERED_BLOCKED_MESSAGE
        return true
    }

    /**
     * 播放一个列表（界面上的「点这首」）。
     *
     * 房间里这份意图是**点给房间的**：本地先响起来，200 毫秒后就会被镜像装回房间那一份，
     * 表现成「点了没反应」。所以先问一次转交口。
     */
    fun play(
        tracks: List<UnifiedTrack>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    ) {
        if (tracks.isEmpty()) return
        // 单独点一首 / 换一张列表都是明确的新意图，电台流到此结束。
        stopRadio()
        val track = tracks.getOrNull(startIndex.coerceIn(0, tracks.lastIndex))
        if (track != null && roomBridge?.onPlayTrack(track) == true) return
        playDirect(tracks, startIndex, startPositionMs)
    }

    /**
     * 播放整张列表（界面上的「播放全部」）。
     *
     * 与 [play] 的差别只体现在房间里：那边是「点这首」（`playNow`），这边是「换歌单」
     * （`setQueue`）——把所有列表都按「点这首」处理会让人以为点了播放全部，结果只放了一首。
     */
    fun playAll(tracks: List<UnifiedTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        // 用户明确换了队列：否则电台歌会一直往别人的歌单后面追加。
        stopRadio()
        if (roomBridge?.onReplaceQueue(tracks, startIndex) == true) return
        playDirect(tracks, startIndex, 0L)
    }

    /**
     * 直接驱动本地播放器，**不经过房间转交口**。
     *
     * 房间镜像、设备接管 / 跟随、退房恢复现场都必须走这里：它们本来就是「把队列装进播放器」
     * 这件事本身，再过一次转交口就会自己调自己。
     *
     * [startIndex] 是列表里该从哪一首开始，[startPositionMs] 是从那一首的第几毫秒开始。
     *
     * 起播位置**必须**跟着 [MediaController.setMediaItems] 一起给出，不能等装好队列再补一个
     * `seekTo`：两者都是跨进程的异步命令，`seekTo` 会落在**尚未被替换的旧队列**上，
     * 随后新队列一装载就把位置清成 0——接管过来的进度就是这么丢的。
     */
    fun playDirect(
        tracks: List<UnifiedTrack>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    ) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.size - 1)
        if (blockedByWifiOnly(tracks[index])) return
        val startAt = startPositionMs.coerceAtLeast(0L)
        mainScope.launch {
            val active = controller ?: return@launch
            _state.update {
                it.copy(error = null, positionMs = startAt)
            }
            active.setMediaItems(itemsOf(tracks, index, artworkOf(tracks[index])), index, startAt)
            active.prepare()
            active.play()
            syncQueue()
            syncFlags()
            // 换队列当场落一次盘：电台刚开播时把「队列 + 电台现场」一起存下来，
            // 不能等下一次换歌（那一首要几分钟之后）。
            persistSession()
        }
    }

    /** 单曲播放：队列里没有就把它换成队列。 */
    fun playSingle(track: UnifiedTrack) {
        if (roomBridge?.onPlayTrack(track) == true) return
        val current = _state.value.queue.map { it.key }
        val index = current.indexOf(track.key)
        if (index >= 0) {
            playAt(index)
        } else {
            play(listOf(track), 0)
        }
    }

    fun playAt(index: Int) {
        // 切到某一首也要过一遍同样的判断：队列里存着的是坐标，缓存有没有是另一回事。
        val track = _state.value.queue.getOrNull(index)
        if (track != null && blockedByWifiOnly(track)) return
        mainScope.launch {
            val active = controller ?: return@launch
            if (index !in 0 until active.mediaItemCount) return@launch
            // 队列里的项只有封面 URL；切过去之前把字节补上，离线时系统媒体条才有图。
            artworkOf(track)?.let { artwork ->
                runCatching {
                    active.replaceMediaItem(index, active.getMediaItemAt(index).withArtwork(artwork))
                }
            }
            active.seekTo(index, 0L)
            active.play()
        }
    }

    /** 下一首播放：紧跟当前曲目之后插入。 */
    fun playNext(track: UnifiedTrack) {
        mainScope.launch {
            val active = controller ?: return@launch
            val item = track.toMediaItem(json, quality, preferredPlatform, artworkOf(track))
            val position = if (active.mediaItemCount == 0) 0 else active.currentMediaItemIndex + 1
            active.addMediaItem(position, item)
            syncQueue()
        }
    }

    fun addToQueue(track: UnifiedTrack) {
        mainScope.launch {
            val active = controller ?: return@launch
            val item = track.toMediaItem(json, quality, preferredPlatform, artworkOf(track))
            if (active.mediaItemCount == 0) {
                active.setMediaItems(listOf(item))
                active.prepare()
            } else {
                active.addMediaItem(item)
            }
            syncQueue()
        }
    }

    fun removeAt(index: Int) {
        mainScope.launch {
            val active = controller ?: return@launch
            if (index !in 0 until active.mediaItemCount) return@launch
            active.removeMediaItem(index)
            syncQueue()
        }
    }

    fun clearQueue() {
        stopRadio()
        mainScope.launch {
            val active = controller ?: return@launch
            active.stop()
            active.clearMediaItems()
            _state.value = PlaybackState()
            // 队列被清空（例如退出登录）时，本地那份现场也要一起丢掉。
            sessionStore.clear()
        }
    }

    /**
     * 把当前的队列与进度写到本地，下次启动接着听。
     *
     * @param synchronous 进程可能马上就没的时候（界面 ON_STOP）传 true：
     *   `apply` 是异步落盘，那种情况下有丢的可能。
     */
    fun persistSession(synchronous: Boolean = false) {
        // 一起听接管期间不落盘：这时候队列是共享队列，写进去会盖掉用户自己的现场。
        if (persistencePaused) return
        mainScope.launch {
            val current = _state.value
            if (current.queue.isEmpty()) {
                sessionStore.clear(synchronous)
                return@launch
            }
            // IDLE（还没 prepare，比如刚恢复出来的现场）时控制器报的是 0，
            // 拿它去存会把上次的进度抹平，所以那种情况下用状态里的位置。
            val position = controller
                ?.takeIf { it.playbackState != Player.STATE_IDLE }
                ?.currentPosition
                ?.coerceAtLeast(0L)
                ?: current.positionMs
            sessionStore.save(
                PlaybackSession(
                    queue = current.queue,
                    index = current.index.coerceAtLeast(0),
                    positionMs = position,
                    radioLive = radio?.let { RadioLive(it.platform, it.session) },
                ),
                synchronous = synchronous,
            )
        }
    }

    /**
     * 暂停 / 恢复「个人现场」的落盘。一起听入房时暂停、退房时恢复。
     *
     * 房间与电台互斥：被服务端推进一起听时电台就地结束并给出提示，
     * 免得后台还在往一个自己再也看不见的队列里续歌。
     */
    fun setPersistencePaused(paused: Boolean) {
        persistencePaused = paused
        if (paused) {
            mainScope.launch {
                if (radio != null) {
                    stopRadio()
                    _notice.value = "已进入「一起听」，私人电台已停止"
                }
            }
        }
    }

    /** 落盘的那一份个人现场（冷启动时内存里还没有队列，靠它把用户自己的队列找回来）。 */
    fun loadPersistedSession(): PlaybackSession? = sessionStore.load()

    /**
     * 房间接管期间的播放模式：顺序、不随机、不循环。
     *
     * 房间是权威，切歌由服务端统一决定。本机要是还开着列表循环或单曲循环，播完一首它
     * 自己就跳走了，而服务端的 `ended` 还没回来——两边各切一次，队列立刻错位。
     *
     * **房间下发的模式只影响界面显示，不落到播放器**（见 [showRoomPlayMode]）：随机那种
     * 模式要是真开给 ExoPlayer，它会自己随机跳一首，跟服务端的随机结果很可能不是同一首。
     * 退房时把用户自己的播放模式还回去（[personalPlayMode]，界面与播放器一起）。
     */
    fun setRoomPlayMode(enabled: Boolean) {
        roomPlayMode = enabled
        mainScope.launch {
            val active = controller ?: return@launch
            if (enabled) {
                active.repeatMode = Player.REPEAT_MODE_OFF
                active.shuffleModeEnabled = false
            } else {
                // 退房：界面上的模式也换回用户自己那份——房间里显示的是房间下发的那个。
                _playMode.value = personalPlayMode
                applyPlayMode(personalPlayMode)
            }
        }
    }

    /**
     * 房间里下发的播放模式：**只改界面显示，不动 ExoPlayer**。
     *
     * 房间期间播放器必须是「中性」的（见 [setRoomPlayMode]）：本机要是自己循环或随机，
     * 播完一首就抢在服务端的 `ended` 之前切歌，两端立刻错位。所以这个值只是拿来画顶栏那个
     * 按钮的图标，以及让用户知道「房间现在是什么模式」——播完往哪走仍由服务端决定。
     */
    fun showRoomPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    /* ------------------------------ 传输控制 ------------------------------ */

    fun toggle() {
        mainScope.launch {
            val active = controller ?: return@launch
            if (active.mediaItemCount == 0) return@launch
            if (active.isPlaying) active.pause() else active.play()
        }
    }

    fun play() {
        mainScope.launch { controller?.play() }
    }

    fun pause() {
        mainScope.launch { controller?.pause() }
    }

    /**
     * 改本机音量（远程 `volume` 指令也走这里）。
     *
     * 音量是**设备**的属性而不是内容的属性：同一份播放状态在不同设备上可以不一样，
     * 所以它跟着设备走、不跟着队列走，也是跟随播放时唯一不该同步的东西。
     */
    fun setVolume(value: Float) {
        val target = value.coerceIn(0f, 1f)
        mainScope.launch {
            controller?.volume = target
            // 顺手写进状态：上报读的就是它，而音量变化不在协议「必须上报」的清单里，
            // 只有执行指令后的那次补报——可补报发生时轮询（400ms）还没来得及同步，
            // 报出去的会是旧值，控制端据此判断就会以为指令没生效。
            _state.update { it.copy(volume = target) }
        }
    }

    /**
     * 微调播放速率（跟随同步用）。
     *
     * 小偏差靠调速去追比 seek 便宜得多：seek 要重新缓冲、会卡一下，而 1±0.05 的速率
     * 听感上几乎察觉不到。用完必须复位。
     */
    fun setPlaybackSpeed(speed: Float) {
        val target = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        /*
         * 设过了就不再设。
         *
         * 镜像循环每 200 毫秒算一次偏差，多数时候结论都是「维持现在的速率」——照直下发
         * 就是每秒五次跨进程命令，一路发下去。命令是白发的（值没变），而每一条都有失败的可能，
         * 失败会被播放器报成一条错误（`Unexpected runtime error`），用户看到的就是
         * 「明明在播，却弹无法播放」。所以这里做成幂等：真的变了才发。
         */
        if (target == appliedSpeed) return
        appliedSpeed = target
        mainScope.launch { controller?.setPlaybackSpeed(target) }
    }

    /**
     * 速率回到 1。
     *
     * 结束跟随、连接断开时都得调它——否则会留下一个「莫名其妙快/慢几个百分点」的播放器，
     * 而用户根本不知道问题出在哪。
     */
    fun resetPlaybackSpeed() {
        setPlaybackSpeed(1f)
    }

    fun next() {
        // 电台撞到队尾：通常是提前续播还没回来（网络慢、或用户连点下一首）。
        // 补歌回来由 doRefill 自动接着播，这里不把电台流结束掉。
        if (radio != null && _state.value.index >= _state.value.queue.lastIndex) {
            radioTailStopped = true
            mainScope.launch { runCatching { refillRadio(force = true).await() } }
            return
        }
        mainScope.launch { controller?.seekToNextMediaItem() }
    }

    fun previous() {
        mainScope.launch {
            val active = controller ?: return@launch
            // 播过 4 秒以上时，「上一首」想表达的是「回到开头」。
            // 走 seekLocal：还没 prepare 的现场上普通 seekTo 会被丢掉，回不到开头。
            if (active.currentPosition > 4_000) seekLocal(active, 0L) else active.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        mainScope.launch {
            controller?.let { seekLocal(it, target) }
            _state.update { it.copy(positionMs = target) }
        }
        // 多设备那条线要知道「用户拖了进度」：进度跳变不会产生任何播放状态变化，
        // 光靠事件驱动的话别的设备永远看不到这次拖动。Media3 也不会为 seek 回调，
        // 所以只能在这里主动说一声。
        _seeks.tryEmit(Unit)
    }

    /**
     * 对本地播放器 seek：**没 prepare 时改的是「起播位置」**，不是普通 seek。
     *
     * 冷启动恢复出来的现场是 IDLE、没 prepare 的，这时 `seekTo` 会被 Media3 悄悄丢掉：
     * `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` 只在「当前曲目可 seek」时才下发，而没 prepare 的
     * 播放器时间线还是空的（`BasePlayer.isCurrentMediaItemSeekable` 因此为 false），命令根本到不了
     * 播放器。于是进度条看着拖到了 0，一按播放又回到恢复时的那个位置——界面改的是自己的 state，
     * 播放器的起点还是 `setMediaItems` 时给的那个。
     *
     * 改法是把队列按新位置重新装一遍：`setMediaItems` 的 `startPositionMs` 正是给 prepare 用的
     * 起始位置（[restoreSession] 也是靠它恢复进度的），装完再播就从这里开始。
     */
    private fun seekLocal(active: MediaController, positionMs: Long) {
        val tracks = _state.value.queue
        if (active.playbackState == Player.STATE_IDLE) {
            val index = active.currentMediaItemIndex
            // 队列对不上就先不动：宁可这次不生效，也不能把播放列表换残。
            if (tracks.isNotEmpty() && tracks.size == active.mediaItemCount && index in tracks.indices) {
                active.setMediaItems(itemsOf(tracks), index, positionMs)
            }
            return
        }
        active.seekTo(positionMs)
    }

    /** 播放模式那个按钮：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序。 */
    fun cyclePlayMode() {
        scope.launch { settings.setPlayMode(_playMode.value.next()) }
    }

    /* ------------------------------ 音源 / 音质 ------------------------------ */

    /** 手动切换音源：切到另一个平台，并保持播放进度。 */
    fun switchPlatform(platform: Platform) {
        val track = _state.value.current ?: return
        if (track.sourceOf(platform) == null) return
        scope.launch { settings.setPreferredPlatform(platform) }
    }

    /** 只改偏好、不动当前播放（用于设置页）。 */
    fun preferPlatform(platform: Platform?) {
        scope.launch { settings.setPreferredPlatform(platform) }
    }

    /**
     * 按当前的音源/音质偏好重建整个队列。
     *
     * 播放地址是被编进播放项 URI 里的，所以切换音源或音质必须换掉播放项；
     * 代价是进度要自己接回来。
     */
    private fun rebuildKeepingPosition() {
        mainScope.launch {
            val active = controller ?: return@launch
            val tracks = _state.value.queue
            if (tracks.isEmpty()) return@launch

            val position = active.currentPosition.coerceAtLeast(0L)
            val index = active.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
            val wasPlaying = active.isPlaying

            active.setMediaItems(itemsOf(tracks, index, artworkOf(tracks.getOrNull(index))), index, position)
            active.prepare()
            if (wasPlaying) active.play()
            syncQueue()
        }
    }

    private fun applyPlayMode(mode: PlayMode) {
        val active = controller ?: return
        // 电台是「一路往前」的流：循环回旧歌、随机跳回听过的都不合语义。
        // 续播负责往队尾补歌，所以这里必须关掉循环与随机。
        if (radio != null) {
            applyRadioMode(active)
            return
        }
        // 房间接管期间由房间决定顺序：设置页 / 播放页改了播放模式也不许动播放器，
        // 否则列表循环或单曲循环会在播完一首时抢在服务端之前切歌。
        if (roomPlayMode) return
        when (mode) {
            PlayMode.Order -> {
                active.repeatMode = Player.REPEAT_MODE_OFF
                active.shuffleModeEnabled = false
            }

            PlayMode.All -> {
                active.repeatMode = Player.REPEAT_MODE_ALL
                active.shuffleModeEnabled = false
            }

            PlayMode.One -> {
                active.repeatMode = Player.REPEAT_MODE_ONE
                active.shuffleModeEnabled = false
            }

            PlayMode.Shuffle -> {
                active.repeatMode = Player.REPEAT_MODE_ALL
                active.shuffleModeEnabled = true
            }
        }
    }

    /* ------------------------------ 电台续播 ------------------------------ */

    /**
     * 开始听一个平台的电台。
     *
     * [initialTracks] 是首页卡片背后那一整批（网易云私人 FM 3 首 / QQ 猜你喜欢 5 首）：
     * 发现页已经取回来了，卡片只把第一首画成封面。整批直接入队按顺序放完，
     * **点开时一次电台请求都不打**；最后一首开始播时才由 [maybeRefill] 去要下一批。
     * 没拿到板块时（例如还没绑定账号）才退化为「现取一批」。
     */
    fun startRadio(platform: Platform, initialTracks: List<UnifiedTrack> = emptyList()) {
        if (roomActive() || persistencePaused) {
            _notice.value = "正在「一起听」中，请先退出再使用私人电台"
            return
        }
        if (!radioPlatformAllowed(platform)) {
            _notice.value = "${platform.label}已在设置里关闭，启用后才能使用私人电台"
            return
        }
        mainScope.launch {
            stopRadio(announce = false)
            radioGeneration++
            val started = RadioState(platform)
            radio = started
            _state.update { it.copy(radio = started, radioLoading = false) }

            val list = if (initialTracks.isNotEmpty()) {
                initialTracks
            } else {
                fetchRadio(reset = true)
            }
            // 请求期间用户可能已经点了别的歌：别把电台歌塞进新队列。
            if (radio?.platform != platform || list.isEmpty()) {
                // 接入失败时 fetchRadio 已经说了原因，别再用「已退出电台」盖掉它。
                if (radio?.platform == platform) stopRadio(announce = false)
                return@launch
            }
            // 刻意不经过 play()：那会清掉刚建立的电台状态（只有用户主动换歌单才该清）。
            playDirect(list, 0, 0L)
            controller?.let { applyRadioMode(it) }
            _notice.value = "私人电台已开始，快听完时会自动续歌"
        }
    }

    /** 退出电台：只结束「流」，不动当前队列与进度。 */
    fun stopRadio(announce: Boolean = true) {
        val wasActive = radio != null
        radioGeneration++
        radio = null
        radioTailStopped = false
        radioRetryAt = 0L
        radioErrorAt = 0L
        // 不 cancel 在途请求：它可能正运行在 stopRadio 的调用栈里（例如平台开关把电台关掉），
        // 取消自己会让那一轮续播以异常收场。用代数挡住它写回状态即可。
        radioInFlight = null
        _state.update { it.copy(radio = null, radioLoading = false) }
        if (wasActive) {
            // 只清内存不够：存档里的电台现场还在，重启就会被读回来。
            // 没在听电台时不做这次读盘：点歌是高频操作，不该为它解析整份存档。
            clearPersistedRadio()
            controller?.let { if (!roomPlayMode) applyPlayMode(personalPlayMode) }
            if (announce) _notice.value = "已退出私人电台，现在可以使用「一起听」"
        }
    }

    /** 平台开关在别处变了（含云端配置同步回来）：切回主线程再判定，避免跨线程读电台状态。 */
    fun onPlatformPrefsChanged() {
        mainScope.launch { stopRadioIfBlocked() }
    }

    /** 平台在设置里被停用（含刷新后从存档恢复的电台）：就地结束电台流。 */
    fun stopRadioIfBlocked(): Boolean {
        val state = radio ?: return false
        if (radioPlatformAllowed(state.platform)) return false
        stopRadio()
        _notice.value = "${state.platform.label}已在设置里关闭，私人电台已停止"
        return true
    }

    /** 向网关要一批：`reset` 表示新开一个电台，否则带上会话号与整个队列的 key 去重。 */
    private suspend fun fetchRadio(reset: Boolean): List<UnifiedTrack> {
        val state = radio ?: return emptyList()
        val generation = radioGeneration
        if (_state.value.radioLoading) return emptyList()
        if (stopRadioIfBlocked()) return emptyList()
        _state.update { it.copy(radio = state, radioLoading = true) }
        return try {
            val batch = api.radio(
                platform = state.platform,
                session = if (reset) null else state.session.takeIf { it.isNotBlank() },
                limit = 6,
                exclude = if (reset) emptyList() else _state.value.queue.map { it.key },
            )
            // 请求期间用户可能换了平台或退出了电台：只在这个电台还活着时写回状态。
            val live = radio
            if (generation == radioGeneration && live != null && live.platform == state.platform) {
                val next = live.copy(session = batch.session)
                radio = next
                _state.update { it.copy(radio = next, radioLoading = false) }
                // 新开电台时队列还没装进播放器，别在这时写一份「旧队列 + 新电台」的存档；
                // startRadio 装好队列后会自己落一次盘。
                if (!reset) persistSession()
            } else {
                _state.update { it.copy(radioLoading = false) }
            }
            batch.items
        } catch (error: CancellationException) {
            _state.update { it.copy(radioLoading = false) }
            throw error
        } catch (error: Exception) {
            radioRetryAt = System.currentTimeMillis()
            _state.update { it.copy(radioLoading = false) }
            if (reset) {
                _notice.value = "电台接入失败：${error.friendlyMessage()}"
            } else if (System.currentTimeMillis() - radioErrorAt > 60_000L) {
                radioErrorAt = System.currentTimeMillis()
                _notice.value = "电台续播失败：${error.friendlyMessage()}；稍后会自动重试，或点「下一首」立即再试"
            }
            emptyList()
        }
    }

    /** 追加一批新歌，返回实际加进去的条数。在途时复用同一个请求，不重复打上游。 */
    private fun refillRadio(force: Boolean): Deferred<Int> {
        radioInFlight?.let { return it }
        val pending = mainScope.async { doRefill(force) }
        radioInFlight = pending
        mainScope.launch {
            runCatching { pending.await() }
            if (radioInFlight == pending) radioInFlight = null
        }
        return pending
    }

    private suspend fun doRefill(force: Boolean): Int {
        val state = radio ?: return 0
        val generation = radioGeneration
        if (roomActive() || persistencePaused) return 0
        if (stopRadioIfBlocked()) return 0
        if (!force && System.currentTimeMillis() - radioRetryAt < RADIO_RETRY_MS) return 0

        val items = fetchRadio(reset = false)
        // 请求期间用户可能换了歌单 / 退出了电台（甚至又开了同平台的新电台）：
        // 代数或平台对不上就整批丢弃，绝不能把旧批歌塞进新队列。
        if (generation != radioGeneration || radio?.platform != state.platform) return 0

        val existing = _state.value.queue.map { it.key }.toSet()
        val fresh = items.filter { it.key !in existing }
        val added = appendRadioTracks(fresh)
        // 补成功也留一个短节流：上游偶尔只给得出 1~2 首时，下一拍轮询不该立刻又要一批。
        radioRetryAt = System.currentTimeMillis()

        if (added > 0) {
            // 之前已经播到队尾停下了：补歌到了就自动接上，不等用户再点一下。
            if (radioTailStopped) {
                radioTailStopped = false
                controller?.let { active ->
                    val nextIndex = (active.currentMediaItemIndex + 1)
                        .coerceAtMost((active.mediaItemCount - 1).coerceAtLeast(0))
                    active.seekTo(nextIndex, 0L)
                    active.play()
                }
            }
        } else {
            // 空批次 / 请求失败只进入冷却，不等于电台枯竭：一次抖动不该永久停播。
            if (System.currentTimeMillis() - radioErrorAt > 60_000L) {
                radioErrorAt = System.currentTimeMillis()
                _notice.value = "电台这批没有新歌，稍后会自动重试；也可以点「下一首」立即再试"
            }
            // 停在队尾时换歌事件不会再触发，显式排一次延时重试。
            if (_state.value.index >= _state.value.queue.lastIndex) {
                mainScope.launch {
                    delay(RADIO_RETRY_MS)
                    maybeRefill()
                }
            }
        }
        return added
    }

    private fun appendRadioTracks(items: List<UnifiedTrack>): Int {
        if (items.isEmpty()) return 0
        val active = controller ?: return 0
        active.addMediaItems(itemsOf(items))
        syncQueue()
        persistSession()
        return items.size
    }

    /** 最后一首开始播了才补下一批：首页那批按顺序放完，期间不打扰上游。 */
    private fun maybeRefill() {
        if (stopRadioIfBlocked()) return
        val state = radio ?: return
        if (_state.value.radioLoading || radioInFlight?.isActive == true) return
        if (roomActive() || persistencePaused) return
        if (System.currentTimeMillis() - radioRetryAt < RADIO_RETRY_MS) return
        val queue = _state.value.queue
        val index = _state.value.index
        if (index < 0 || queue.isEmpty()) return
        if (queue.lastIndex - index > RADIO_REFILL_AHEAD) return
        radioRetryAt = System.currentTimeMillis() // 立刻占位，下一拍不会重复发
        Log.i(LOG_TAG, "电台续播触发：${state.platform.id}，队列剩 ${queue.lastIndex - index} 首")
        refillRadio(force = true)
    }

    /** 电台是一路往前的流：不循环、不随机跳回听过的歌。 */
    private fun applyRadioMode(active: MediaController) {
        active.repeatMode = Player.REPEAT_MODE_OFF
        active.shuffleModeEnabled = false
    }

    /** 只从落盘里抹掉电台现场，不动队列与进度（房间接管期间落盘是暂停的，得单独写一次）。 */
    private fun clearPersistedRadio() {
        val saved = sessionStore.load() ?: return
        if (saved.radioLive == null) return
        sessionStore.save(saved.copy(radioLive = null), synchronous = true)
    }

    /* ------------------------------ 歌词 ------------------------------ */

    private suspend fun loadLyrics(track: UnifiedTrack) {
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource ?: return
        val key = "${source.platform.id}:${source.id}"

        lyricCache[key]?.let { cached ->
            _state.update {
                it.copy(
                    lyric = cached,
                    lyricWordByWord = cached.any { line -> line.words.isNotEmpty() },
                    lyricLoading = false,
                )
            }
            return
        }

        _state.update { it.copy(lyricLoading = true) }
        val lines = runCatching {
            val result = api.lyric(source.platform, source.id)
            // 逐字歌词与按行歌词同时保留：解析不出字级时间轴时 parseResult 自动按行降级。
            LrcParser.parseResult(result.lrc, result.trans, result.roma, result.yrc, result.qrc)
        }.getOrDefault(emptyList())

        if (lines.isNotEmpty()) lyricCache[key] = lines
        _state.update {
            it.copy(
                lyric = lines,
                lyricWordByWord = lines.any { line -> line.words.isNotEmpty() },
                lyricLoading = false,
            )
        }
    }

    /* ------------------------------ 试听 / 直连提示 ------------------------------ */

    /**
     * 提前解析一次播放地址。
     *
     * 两个用处：一是把「只有试听片段」和「走的哪条路」提前告诉界面，
     * 二是把结果放进解析器缓存，ExoPlayer 真正打开时就不用再打一次上游。
     */
    private suspend fun primeResolve(track: UnifiedTrack) {
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource ?: return
        val request = PlayRequest(source.platform, source.id, quality)
        runCatching { resolver.resolve(request) }
            .onSuccess { result ->
                // 和 SakuraDataSource 用同一个判断：锁了「中转」就别预告直连，
                // 锁了「直连」也别因为历史记录就预告中转；共享地址只能直连，也算在里面。
                val willBeDirect = resolver.willUseDirect(result, source.platform)
                // 还没开流的时候（比如刚恢复的现场）先把预解析的结论显示出来，
                // 真连上游或命中缓存时会有更准的结果盖掉它。
                routeTracker.reportExpected(
                    request.cacheKey,
                    if (willBeDirect) PlaybackRoute.Direct else PlaybackRoute.Proxy,
                )
                _state.update {
                    it.copy(
                        trial = result.trial,
                        usingDirect = willBeDirect,
                        // 显示「当前音质」用它：网关会把请求档位降级，回显的 quality 只是请求值。
                        actualQuality = result.effectiveQuality,
                    )
                }
                notifyQualityDowngrade(
                    requested = request.quality,
                    actual = result.effectiveQuality,
                    trial = result.trial,
                )
            }
            .onFailure { error ->
                // 解析不出来时先给一句提示；真正的播放失败还会再由 ExoPlayer 报一次。
                // 但用户还没打算播（例如刚从本地恢复出来的现场）时别打扰他——
                // 那种情况下解析失败只是「这次启动后还没成功取过流」。
                if (controller?.playWhenReady == true) {
                    _state.update { it.copy(error = error.friendlyMessage()) }
                }
            }
    }

    /**
     * 请求的档位没拿到、网关降级给了另一档时提示一次。
     *
     * 两个平台降级时都**不报错**（网易云「按能给的给」，QQ 按 `file_type` 链逐个重试），
     * 不提示的话用户会一直以为自己在听无损/母带——这正是文档点名要避免的那件事。
     *
     * 不提示的三种情况：
     * - `actual` 为空：网关没回报实际档位，无从判断，不能凭空说「降级了」；
     * - `trial` 为真：只能听试听片段时播放页已有专门的角标，再叠一句降级提示只是噪音；
     * - 没在播：冷启动恢复出来的现场也会预解析一次，那时候弹「已按 X 播放」是提前报信。
     *   没在播时这条信息由音质弹层里的「正在播放」标记承担，一样看得到。
     */
    private fun notifyQualityDowngrade(requested: Quality, actual: Quality?, trial: Boolean) {
        if (actual == null || actual == requested || trial) return
        if (controller?.playWhenReady != true) return
        if (!notifiedDowngrades.add("${requested.id}>${actual.id}")) return
        // 档位名按平台取，与音质面板里的叫法保持同一套（QQ 那边 high 叫「HQ」）。
        val platform = _state.value.activePlatform
        _notice.value = "「${qualityLabel(requested, platform)}」暂不可用，" +
            "已按「${qualityLabel(actual, platform)}」播放"
    }

    /**
     * 补全当前曲目的档位清单。
     *
     * 搜索与歌单里的网易云曲目只带基础档（`qualitiesComplete` 为 false），照着筛选项会把用户
     * 其实拥有的高阶档藏起来，所以补一次单曲详情——它的 `qualities` 是完整的。
     * 详情走元数据缓存，同一首歌重播不会再打上游。
     *
     * 清单本来就完整时 [onTrackChanged] 当场就填好了，这里不必多此一举。
     */
    private suspend fun syncQualityOptions(track: UnifiedTrack) {
        if (track.qualitiesAuthoritative) return
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource ?: return
        val detail = runCatching { api.track(source.platform, source.id) }.getOrNull()?.track ?: return
        // 详情也可能不完整（上游没给全）：那就别覆盖手上那份已知的清单——不能拿一份
        // "同样不全、而且还是另一份"的清单换掉它。
        if (!detail.qualitiesAuthoritative) return
        val complete = detail.availableQualities ?: return
        // 补详情期间可能已经换歌：别把上一首的档位盖到当前曲目上。
        if (_state.value.current?.key != track.key) return
        _state.update { it.copy(qualityOptions = complete) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

/** 轮询是 400ms 一次，这个数对应「大约每 15 秒把进度写一次本地」。 */
private const val PERSIST_EVERY_TICKS = 37

/** 跟随调速的允许范围。实际只在 1±0.05 上用，这里兜个底，防止意外的值灌进来。 */
private const val MIN_PLAYBACK_SPEED = 0.5f
private const val MAX_PLAYBACK_SPEED = 2f

/**
 * 报错之后隔多久复检一次「播放器是不是其实好好的」。
 *
 * 取 1.5 秒：够 ExoPlayer 把内部状态落定（真失败会落到 IDLE），又不至于让一条假错误
 * 在屏幕上挂太久。来源不明的错误大多是「报完就没事了」，这个窗口能把它认出来。
 */
private const val ERROR_VERIFY_MS = 1_500L

/** 播放相关的日志标签：真出问题时 `adb logcat -s SakuraPlayback` 一条条看。 */
private const val LOG_TAG = "SakuraPlayback"

/**
 * 播放器报的时长低于歌曲长度的这个比例时，就认为本地缓存里存的是**试听片段**。
 *
 * 取 0.8：完整版与元数据之间通常只差不到一秒，而试听片段只有 15 / 30 秒，
 * 长一点的歌差得非常明显，中间这些情况不该被误判。
 */
private const val FRAGMENT_DURATION_RATIO = 0.8

/** 只剩最后一首时才补下一批：首页那 3~5 首要按顺序放完，留一整首歌的时间给网络。 */
private const val RADIO_REFILL_AHEAD = 0

/** 续播失败后的冷却：别让 400ms 一拍的轮询把上游打进风控。 */
private const val RADIO_RETRY_MS = 20_000L
