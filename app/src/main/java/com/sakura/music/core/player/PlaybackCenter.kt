package com.sakura.music.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.network.NetworkPolicy
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.prefs.PlaybackSession
import com.sakura.music.data.prefs.PlaybackSessionStore
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

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
) {
    val current: UnifiedTrack? get() = queue.getOrNull(index)

    val hasLyrics: Boolean get() = lyric.isNotEmpty()
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
) {

    // MediaController 只能在它被创建的那个线程（主线程）上访问。
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** 当前播放模式：顶栏那个循环按钮要看着它画图标、给不给强调色。 */
    private val _playMode = MutableStateFlow(PlayMode.All)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

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

    fun consumeNotice() {
        _notice.value = null
    }

    private var controller: MediaController? = null
    private var quality: Quality = Quality.Default
    private var preferredPlatform: Platform? = null
    private var pollJob: Job? = null

    /**
     * 已经提示过的降级组合（`请求档位>实际档位`）。
     *
     * 同一种只提示一次：会员等级不够的账号**每一首**都会降到同一档，每首都弹一句就成了骚扰。
     * 提示过之后，界面上的「正在播放」标记仍然会一直在音质弹层里显示，信息没有丢。
     */
    private val notifiedDowngrades = mutableSetOf<String>()

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
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncQueue()
            syncFlags()
            onTrackChanged()
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
            syncFlags()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describePlaybackError(error), isBuffering = false) }
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

                    // 隔一阵子把进度也记下来。只靠「换歌 / 暂停」两个时机的话，
                    // 一直放着的时候进度会停在很早以前。
                    if (++ticks >= PERSIST_EVERY_TICKS) {
                        ticks = 0
                        if (active.isPlaying) persistSession()
                    }
                }
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
                trial = false,
                // 上一首的档位结论不能留给下一首：解析出来之前谁也不知道新的一首会降到哪。
                actualQuality = null,
                // 曲目自带清单的话当场就能筛，不能等异步那一步——否则刚换歌时打开音质弹层，
                // 会短暂把「本曲其实没有的档位」也列出来。清单不完整时这里拿到的是已知的那几档
                // （都是真的存在），完整的清单随后由 syncQualityOptions 补上。
                qualityOptions = track.availableQualities,
                lyric = emptyList(),
                lyricLoading = false,
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
     * 播放一个列表。
     *
     * [startIndex] 是列表里该从哪一首开始，[startPositionMs] 是从那一首的第几毫秒开始。
     *
     * 起播位置**必须**跟着 [MediaController.setMediaItems] 一起给出，不能等装好队列再补一个
     * `seekTo`：两者都是跨进程的异步命令，`seekTo` 会落在**尚未被替换的旧队列**上，
     * 随后新队列一装载就把位置清成 0——接管过来的进度就是这么丢的。
     */
    fun play(
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
        }
    }

    /** 单曲播放：队列里没有就把它换成队列。 */
    fun playSingle(track: UnifiedTrack) {
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
                ),
                synchronous = synchronous,
            )
        }
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
        mainScope.launch { controller?.setPlaybackSpeed(target) }
    }

    /**
     * 速率回到 1。
     *
     * 结束跟随、连接断开时都得调它——否则会留下一个「莫名其妙快/慢几个百分点」的播放器，
     * 而用户根本不知道问题出在哪。
     */
    fun resetPlaybackSpeed() {
        mainScope.launch { controller?.setPlaybackSpeed(1f) }
    }

    fun next() {
        mainScope.launch { controller?.seekToNextMediaItem() }
    }

    fun previous() {
        mainScope.launch {
            val active = controller ?: return@launch
            // 播过 4 秒以上时，「上一首」想表达的是「回到开头」。
            if (active.currentPosition > 4_000) active.seekTo(0) else active.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        mainScope.launch {
            controller?.seekTo(positionMs.coerceAtLeast(0L))
            _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        }
        // 多设备那条线要知道「用户拖了进度」：进度跳变不会产生任何播放状态变化，
        // 光靠事件驱动的话别的设备永远看不到这次拖动。Media3 也不会为 seek 回调，
        // 所以只能在这里主动说一声。
        _seeks.tryEmit(Unit)
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

    /* ------------------------------ 歌词 ------------------------------ */

    private suspend fun loadLyrics(track: UnifiedTrack) {
        val source = track.sourceOf(preferredPlatform ?: Platform.UNKNOWN) ?: track.primarySource ?: return
        val key = "${source.platform.id}:${source.id}"

        lyricCache[key]?.let { cached ->
            _state.update { it.copy(lyric = cached, lyricLoading = false) }
            return
        }

        _state.update { it.copy(lyricLoading = true) }
        val lines = runCatching {
            val result = api.lyric(source.platform, source.id)
            LrcParser.parse(result.lrc, result.trans, result.roma)
        }.getOrDefault(emptyList())

        if (lines.isNotEmpty()) lyricCache[key] = lines
        _state.update { it.copy(lyric = lines, lyricLoading = false) }
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
                // 锁了「直连」也别因为历史记录就预告中转。
                val willBeDirect = result.direct != null && resolver.mayUseDirect(source.platform)
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
        _notice.value = "「${requested.label}」暂不可用，已按「${actual.label}」播放"
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
