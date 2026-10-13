package com.sakura.music.core.player

import android.os.SystemClock
import com.sakura.music.data.connect.ConnectClient
import com.sakura.music.data.connect.toUnifiedTrack
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.ApiException
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.room.RoomCodeStore
import com.sakura.music.data.room.RoomEnvelope
import com.sakura.music.data.room.RoomMemberView
import com.sakura.music.data.room.RoomPlayback
import com.sakura.music.data.room.RoomStreamEvent
import com.sakura.music.data.room.RoomView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * 「一起听」房间的客户端。
 *
 * **服务端是权威**：成员的任何操作都提交给网关，网关改写房间状态再广播；本机只做一件事——
 * 把本地播放器**镜像**到房间状态上（用的就是多设备跟随那一套同步算法）。
 * 所以这里没有「本地抢先执行」：点一下暂停要等回声，局域网下这一趟往返可以忽略。
 *
 * 与 [ConnectClient] 的分工：那边只管通道（连、收、发），这边管语义；
 * 与 [ConnectSync] 的关系是**互斥**的——两者都在驱动播放器，入房即停掉设备跟随。
 *
 * 一条硬规则贯穿全文件：**本机从不把进度报给服务端**。时间只由服务端盖章
 * （`position` + `positionAt` + `serverNow`），客户端拿校正过的时钟把它推到「现在」。
 * 两端各报各的进度，就会变成两台机器互相拽。
 */
class RoomSync(
    private val client: ConnectClient,
    private val api: SakuraApi,
    private val playback: PlaybackCenter,
    /** 入房即与「设备跟随」互斥：两者都在驱动同一个播放器，必须只留一个。 */
    private val following: ConnectSync,
    private val store: RoomCodeStore,
    /** 本机登录的账号 id：用来认出「这个席位是我们账号的，但挂在另一台设备上」。 */
    private val currentUserId: () -> String?,
    private val json: Json,
    private val scope: CoroutineScope,
) : RoomPlaybackBridge {

    /* ------------------------------ 播放意图转交 ------------------------------ */

    /**
     * 界面在房间里点了某一首歌。
     *
     * 走的是「点歌」而不是「换歌单」：正在听的那一队不该因为随手点了一首就被整队换掉。
     * 不在房间里（或只是旁观）时返回 false，调用方照常走本地播放器。
     */
    override fun onPlayTrack(track: UnifiedTrack): Boolean {
        if (!seatActive) return false
        playNow(track)
        return true
    }

    /** 界面在房间里点了「播放全部」：整队交给房间（服务端会连下标一起换）。 */
    override fun onReplaceQueue(tracks: List<UnifiedTrack>, startIndex: Int): Boolean {
        if (!seatActive) return false
        setQueue(tracks, startIndex)
        return true
    }

    /* ------------------------------ 对界面暴露的状态 ------------------------------ */

    private val _room = MutableStateFlow<RoomView?>(null)

    /** 房间快照：成员、队列、一起听时长。null = 还没收到快照。 */
    val room: StateFlow<RoomView?> = _room.asStateFlow()

    private val _playbackState = MutableStateFlow<RoomPlayback?>(null)

    /** 房间播放态。 */
    val playbackState: StateFlow<RoomPlayback?> = _playbackState.asStateFlow()

    private val _code = MutableStateFlow<String?>(null)

    /** 房间码；非 null 即「本机在这个房间里」。 */
    val code: StateFlow<String?> = _code.asStateFlow()

    private val _spectating = MutableStateFlow(false)

    /**
     * 本机只是**旁观**：账号在房间里，但播放不在这台设备上（同一账号的另一台在放）。
     *
     * 这种情况下列表、成员、时长都照常显示，但**一个字节的播放都不接管**——
     * 本机还是本机自己的播放器，界面上写清楚「不在本机播放」，并给一个「转移到本机」。
     *
     * 席位被同账号的另一台设备接走时会**落到这个状态**（见 [degradeToSpectating]），
     * 而不是退出房间：房间还开着，只是这台不再放了。
     */
    val spectating: StateFlow<Boolean> = _spectating.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** 一次性提示（房间被对方结束、操作失败…）；由根组件统一弹，和播放提示同一套。 */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    /** 我的账号在这个房间里（播放可能在另一台设备上）。界面判断请用 [code]。 */
    val active: Boolean get() = _code.value != null

    /**
     * 本机**占着席位**：房间的播放就在这台上，所以它要镜像播放、并接管传输控制。
     *
     * 与 [active] 的差别就是「在不在一起听」和「是不是我在这台上放」——
     * 后者才是驱动本机播放器的判据。
     */
    val seatActive: Boolean get() = _code.value != null && !_spectating.value

    /* ------------------------------ 内部状态 ------------------------------ */

    /**
     * 入房请求在途。
     *
     * 服务端会在 HTTP 响应**之前**就把房间状态推过来（`create` 时本地甚至还不知道房间码），
     * 所以这段时间要放行房间事件；平时只认「本来就在这个房间里」，
     * 免得退出之后又被对方的广播拉回去。
     */
    @Volatile
    private var joining = false

    /** 镜像重建中：换歌那一轮别再被下一次节拍打断。 */
    private var syncing = false

    /** 房间队列变过（快照的版本号 / 下标变了）→ 下一轮节拍要比一比本机这份队列。 */
    @Volatile
    private var queueDirty = true

    /** 本机已确认播不了的曲目 key：等服务端跳过它之前，别再反复重试解析。 */
    private var failedKey: String? = null

    /** 上一次重建用到的曲目与时刻（单调时钟），见 [allowRebuild]。 */
    private var lastRebuildKey: String? = null
    private var lastRebuildAt = 0L

    /**
     * 冷启动时从本地读出来的房间码，等连上之后再对它一次账（见 [reconcileRoom]）。
     *
     * 不能拿来直接 `join`：同一账号在房间里只占一个席位，`join` 是**把席位接过来**，
     * 上次在手机上听、这次在平板上打开，直接 join 会把播放从手机抢走。
     */
    private var pendingCode: String? = null

    /** 入房前的个人现场，退房后原样恢复。 */
    private var personal: PersonalSnapshot? = null

    /* 房间快照里那份时长的盖章时刻与数值：用来把「正在走的那一段」推到此刻。 */
    private var togetherSeconds = 0.0
    private var togetherSessionSeconds = 0.0
    private var togetherCounting = false
    private var togetherSyncedAt = 0L

    /** 入房前暂存的个人播放现场。 */
    private data class PersonalSnapshot(
        val queue: List<UnifiedTrack>,
        val index: Int,
        val positionMs: Long,
        val playing: Boolean,
    )

    init {
        scope.launch { client.roomEvents.collect { handle(it) } }

        /*
         * 连接（重新）建立后把手里的房间码对一次账，见 [reconcileRoom]。
         *
         * 覆盖三种情形：首次建连晚于入房；网关重启把内存里的房间清空；以及
         * 「同账号的另一台设备正在这个房间里」——那时本机只能旁观，不能 join。
         */
        scope.launch {
            client.connected.collect { connected -> if (connected) reconcileRoom() }
        }

        // 镜像节拍：纯本地数值比对，不产生任何请求。
        scope.launch {
            while (true) {
                delay(SYNC_TICK_MS)
                if (_code.value != null) runCatching { syncToRoom() }
            }
        }

        // 一首播完 → 交给房间推进队列（本机不自行切歌，否则两边各切一次就错位了）。
        scope.launch { playback.ended.collect { reportEnded(it) } }

        // 本机确定播不了的歌 → 上报让全员跳过。
        scope.launch {
            playback.state
                .map { it.unplayableKey }
                .distinctUntilChanged()
                .collect { key -> if (key != null) reportUnplayable(key) }
        }

        /*
         * 冷启动：本地记着的房间码先捏在手里，等连上之后由 [reconcileRoom] 决定身份。
         *
         * 刻意**不在这里入房、也不捕获个人现场**：
         * - 上次是在另一台设备上听的（`seat = false`）→ 本机连旁观都算不上「已确认」，
         *   更不能 join（那会把播放从那边抢过来）；
         * - 上次是本机在听（`seat = true`）→ 也要先问一次预览：那之后用户完全可能
         *   已经在另一台设备上把一起听接走了，此刻 join 同样会把播放抢回来。
         * 真接回席位时（[reclaim]）才捕获现场，时机正好在房间队列接管播放器之前。
         */
        store.load()?.let { saved -> pendingCode = saved.code }
    }

    /* ------------------------------ 创建 / 加入 / 离开 ------------------------------ */

    /**
     * 创建房间。
     *
     * 带上**本机当前队列**当种子：一起听一般就从「你现在正在听的那一队」开始，
     * 对面进来即可接着同一首、同一进度。
     */
    suspend fun create(): String? {
        val local = playback.state.value
        // 用户明确要开一个新的：之前记着的那个房间码不再管。
        pendingCode = null
        capturePersonal()
        joining = true
        return try {
            val result = api.createRoom(
                deviceId = client.deviceId,
                name = client.deviceName,
                deviceKind = client.deviceKind,
                queue = local.queue,
                index = local.index.coerceAtLeast(0),
            )
            enterRoom(result.code)
            _notice.value = "一起听已创建，房间码 ${result.code}"
            result.code
        } catch (error: Throwable) {
            restorePersonal()
            _notice.value = error.friendlyMessage()
            null
        } finally {
            joining = false
        }
    }

    /**
     * 凭房间码加入。房间不存在（404）与满员（409）的文案都由网关给。
     *
     * @return 是否真的进去了。界面据此决定要不要清空输入框——失败时留着，
     *   免得用户手打的那串码被一句话吞掉。
     */
    suspend fun join(input: String): Boolean {
        val roomCode = input.trim().uppercase()
        if (roomCode.isEmpty()) return false
        /*
         * 「转移到本机」走的也是这里：`join` 在服务端的语义本来就是**把席位接过来**
         * （同一账号只占一个席位），所以同一账号的其它设备点一下就等于把播放搬过来。
         */
        pendingCode = null
        capturePersonal()
        joining = true
        return try {
            api.joinRoom(roomCode, client.deviceId, client.deviceName, client.deviceKind)
            enterRoom(roomCode)
            _notice.value = "已加入一起听"
            true
        } catch (error: Throwable) {
            restorePersonal()
            _notice.value = error.friendlyMessage()
            false
        } finally {
            joining = false
        }
    }

    /** 自己退出（房间还有人就继续）。 */
    suspend fun leave() {
        val roomCode = _code.value
        leaveLocally()
        if (roomCode == null) return
        // 退出失败也不影响本地收尾：服务端那份会随断线宽限到期清掉。
        runCatching { api.leaveRoom(client.deviceId) }
    }

    /**
     * 结束 / 解散房间。双人房人人可结束（服务端把判据收在 `roomPolicy` 里）。
     *
     * 先本地收尾再通知服务端：关闭广播回来时 `code` 已经空了，不会重复提示。
     *
     * 旁观设备不能解散：服务端的解散是按「席位设备」鉴权的，旁观设备传什么都理不直气不壮。
     */
    suspend fun dissolve() {
        if (!seatActive) return
        val roomCode = _code.value
        leaveLocally()
        if (roomCode == null) return
        runCatching { api.dissolveRoom(client.deviceId) }
        _notice.value = "已结束一起听"
    }

    /** 退出登录：断开房间语义（不通知服务端、也不恢复现场）。 */
    fun stop() {
        val wasSeat = seatActive
        pendingCode = null
        personal = null
        clearRoom()
        playback.setPersistencePaused(false)
        if (wasSeat) {
            // 房间把播放器接管成了共享队列：别把它留在下一段会话里。
            // 只是旁观时绝不能清：本机队列是用户自己的，跟这个房间没关系。
            playback.clearQueue()
        }
    }

    /* ------------------------------ 播放控制（供界面走） ------------------------------ */

    fun toggle() {
        act(if (_playbackState.value?.playing == true) ACTION_PAUSE else ACTION_PLAY)
    }

    fun play() {
        act(ACTION_PLAY)
    }

    fun pause() {
        act(ACTION_PAUSE)
    }

    fun next() {
        act(ACTION_NEXT)
    }

    fun prev() {
        act(ACTION_PREV)
    }

    /** 跳到队列里的某一首（房间下标）。 */
    fun playAt(index: Int) {
        act(ACTION_PLAY_AT, buildJsonObject { put(FIELD_INDEX, index) })
    }

    /** 拖进度（秒）。 */
    fun seek(seconds: Double) {
        act(ACTION_SEEK, buildJsonObject { put(FIELD_POSITION, seconds.coerceAtLeast(0.0)) })
    }

    /** 把一首歌追加到共享队列末尾（不打断当前播放）。 */
    fun addTrack(track: UnifiedTrack) {
        act(ACTION_ADD, buildJsonObject { put(FIELD_TRACK, trackElement(track)) })
    }

    /** 点歌并播放：不在队列里就追加，然后跳过去从头播。 */
    fun playNow(track: UnifiedTrack) {
        act(ACTION_PLAY_NOW, buildJsonObject { put(FIELD_TRACK, trackElement(track)) })
    }

    /** 移除共享队列里的一项。 */
    fun removeAt(index: Int) {
        act(ACTION_REMOVE, buildJsonObject { put(FIELD_INDEX, index) })
    }

    /** 整体换歌单（把本机这一队交给房间）。 */
    fun setQueue(tracks: List<UnifiedTrack>, startIndex: Int = 0) {
        act(
            ACTION_SET_QUEUE,
            buildJsonObject {
                put(FIELD_QUEUE, kotlinx.serialization.json.JsonArray(tracks.map(::trackElement)))
                put(FIELD_INDEX, startIndex)
            },
        )
    }

    /* ------------------------------ 时长 / 进度读数 ------------------------------ */

    /**
     * 两人**历史累计**一起听时长（秒），已把「正在走的那一段」算进来。
     *
     * 服务端只给整数秒 + 是否在走表，所以走表时要由客户端补上这一段——
     * 否则读数要等下一次事件才跳一次。基准用的是快照里带的 `serverNow`。
     */
    fun togetherLiveSeconds(): Double = togetherSeconds + runningSeconds()

    /** **本次房间**的一起听时长（秒），同样推演到此刻。 */
    fun togetherSessionLiveSeconds(): Double = togetherSessionSeconds + runningSeconds()

    /** 此刻正在走表吗（两人都在房间里）。 */
    fun togetherCounting(): Boolean = togetherCounting

    /** 房间播放态**此刻**的位置（毫秒）。界面画进度条用它，不要自己再推一遍。 */
    fun livePositionMs(state: RoomPlayback? = _playbackState.value): Long {
        val pb = state ?: return 0L
        val serverNow = client.serverTimeNow
        val advanced = if (pb.playing) {
            pb.position + (serverNow - pb.positionAt) / 1000.0
        } else {
            pb.position
        }
        val clamped = if (pb.duration > 0) advanced.coerceAtMost(pb.duration) else advanced
        return (clamped.coerceAtLeast(0.0) * 1000).toLong()
    }

    /** 邀请链接：桌面端 / 网页端打开即自动入房。 */
    fun inviteLink(): String {
        val roomCode = _code.value ?: return ""
        return "${api.gatewayBaseUrl()}/together?code=$roomCode"
    }

    /* ------------------------------ 成员（旁观时也要认得出谁是谁） ------------------------------ */

    /**
     * 我这个账号在房间里的那条成员。
     *
     * 刻意不用 `RoomView.me`：那个是**设备级**的——旁观设备收到的快照里，自己账号这条
     * 也是 `me = false`（席位在另一台设备上），拿它根本分不出「我账号的席位」和「对面那位」。
     *
     * 读它之前请先订阅 [room]，否则不会随快照刷新。
     */
    fun myAccountMember(): RoomMemberView? {
        val members = _room.value?.members.orEmpty()
        // 服务端逐台算好的账号级标记优先；老服务端没有它时退回按 userId 认。
        members.firstOrNull { it.mine }?.let { return it }
        val userId = currentUserId() ?: return null
        return members.firstOrNull { it.userId == userId }
    }

    /** 对面那位（不是我账号的那条成员）。 */
    fun otherMember(): RoomMemberView? {
        val members = _room.value?.members.orEmpty()
        val mine = myAccountMember() ?: return members.firstOrNull()
        return members.firstOrNull { it.deviceId != mine.deviceId }
    }

    /** 旁观时：播放落在哪台设备上（拿来把「不在本机播放」这句话说具体）。 */
    fun seatDeviceName(): String? =
        myAccountMember()?.deviceName?.takeIf { it.isNotBlank() }

    /* ------------------------------ 事件 ------------------------------ */

    private fun handle(event: RoomStreamEvent) {
        when (event) {
            // 重连：把手里的房间码对一次账（可能已经被另一台设备接走了）。
            is RoomStreamEvent.Reconnected -> scope.launch { reconcileRoom() }
            is RoomStreamEvent.Snapshot -> applyRoom(event.envelope)
            is RoomStreamEvent.Playback -> applyPlayback(event.state)
        }
    }

    private fun applyRoom(envelope: RoomEnvelope) {
        val room = envelope.room
        val roomCode = _code.value

        /*
         * 房间没了。能走到这里说明**不是本机发起的**——本机发起时 finalizeLeave 已经把
         * `code` 清空，这一帧就不会进这个分支。于是「谁结束的」是确定的：
         * 双人房就是对方，多人房只有房主能解散；唯一的例外是席位被同账号的另一台设备接走，
         * 那一条由 `reason` 单独指出来。
         */
        if (room == null) {
            if (roomCode == null) return
            if (envelope.reason == REASON_SEAT_TAKEN) {
                /*
                 * 席位被同一账号的另一台设备接走了。
                 *
                 * 注意**房间并没有散**：两个人还在里面，只是播放换到那台设备上去了。
                 * 所以这里不能当「房间没了」处理（界面会退回「创建/加入」的样子，看着像
                 * 一起听被谁结束了），只降级为旁观——房间继续显示，随时能「转移到本机」接回来。
                 */
                _notice.value = envelope.deviceName?.takeIf { it.isNotBlank() }?.let {
                    "一起听已转移到「$it」，本机改为旁观"
                } ?: "一起听已转移到你的另一台设备，本机改为旁观"
                degradeToSpectating()
                return
            }
            _notice.value = when {
                _spectating.value -> "一起听已结束"
                _room.value?.isGroup == true -> "房主结束了一起听"
                else -> "对方结束了一起听"
            }
            leaveLocally()
            return
        }

        // 只认「正在入房」或「本来就在这个房间」的快照——退出之后不该被别人的广播拉回去。
        // 手里没有房间码时也接：同账号的另一台设备刚进房间，本机是被推来的第一帧（旁观开始）。
        if (!joining && roomCode != null && roomCode != room.code) return

        /*
         * 身份优先用服务端逐台算好的两个标记（它当然知道是谁在收）：
         * `me` = 席位就在本机，`mine` = 我账号那条（席位可能在它的另一台设备上）。
         * 本地比对只作为老服务端（没有 `mine`）时的退路——客户端那点登录态不保证随时都在，
         * 拿它当唯一判据会把「我账号在别台在放」误判成「这房间与我无关」。
         */
        val mySeat = room.members.firstOrNull { it.me }
            ?: room.members.firstOrNull { it.deviceId == client.deviceId }
        val accountSeat = room.members.firstOrNull { it.mine } ?: run {
            val myUserId = currentUserId()
            room.members.firstOrNull { myUserId != null && it.userId == myUserId }
        }
        if (mySeat == null && accountSeat == null) {
            // 这个账号已经不在房间里了（被移出 / 席位过了宽限期）：本地收尾。
            if (roomCode == room.code) leaveLocally()
            return
        }

        val previous = _room.value
        if (previous?.version != room.version || previous.index != room.index) queueDirty = true

        // 席位是不是在本机：是则镜像播放，否则只显示（见 spectating 的说明）。
        val spectating = mySeat == null
        _code.value = room.code
        _spectating.value = spectating
        store.save(room.code, seat = !spectating)
        _room.value = room
        togetherSeconds = room.togetherSeconds.coerceAtLeast(0.0)
        togetherSessionSeconds = room.togetherSessionSeconds.coerceAtLeast(0.0)
        togetherCounting = room.togetherCounting
        // 时长与事件同源：`serverNow` 就是服务端算这份快照的时刻，别再取一次本机时钟。
        togetherSyncedAt = envelope.serverNow ?: client.serverTimeNow

        // 旁观：状态记下了，但播放器一个字节都不碰。
        if (spectating) return
    }

    private fun applyPlayback(state: RoomPlayback) {
        // 不在房间（且不在入房途中）时，任何播放帧都与我无关。
        if (_code.value == null && !joining) return
        _playbackState.value = state

        // 旁观：看得到房间在放什么，但那与本机播放器无关，连「播不了」的提示都不该弹。
        if (_spectating.value) return

        if (state.skipReason == SKIP_UNPLAYABLE && !state.skippedTitle.isNullOrBlank()) {
            // 两端都会收到这一帧：各自提示一次「这首播不了，已跳过」。
            _notice.value = "「${state.skippedTitle}」无法播放，已跳过"
        }

        // 换了歌就允许重新尝试解析。
        if (failedKey != null && failedKey != state.track?.key) failedKey = null

        scope.launch { syncToRoom() }
    }

    /* ------------------------------ 镜像 ------------------------------ */

    /**
     * 把本地播放器对齐到房间状态。
     *
     * 三段式与多设备跟随完全一致（同一条经验：seek 会重新缓冲、很贵，能调速就别 seek）：
     * 大偏差直接 seek，小偏差用 ±5% 的速率慢慢追，够准就复位。
     */
    private suspend fun syncToRoom() {
        // 旁观：房间在别处响着，本机播放器不归它管。
        if (!seatActive || syncing) return
        val state = _playbackState.value ?: return
        val track = state.track

        if (track == null) {
            // 房间没在放歌：本机也停下来，别自顾自地响。
            if (playback.state.value.isPlaying) playback.pause()
            return
        }
        // 这首已经确认播不了、正等服务端跳过：别再反复重试解析。
        if (failedKey == track.key) return

        val local = playback.state.value
        val roomTracks = _room.value?.queue?.mapNotNull { it.toUnifiedTrack() }.orEmpty()

        /*
         * 队列变过才比对队列本体（比对只是几个字符串，但没必要每 200 毫秒都做一遍）。
         *
         * 只在**真的不一样**时才重建：本机可能已经自己走到同一首上了（多首队列里播完
         * 一首，播放器会自动切下一首），那种情况重建只会白白重新缓冲一次。
         */
        if (queueDirty) {
            queueDirty = false
            if (roomTracks.isNotEmpty() && !sameQueue(local.queue, roomTracks)) {
                rebuild(roomTracks, track.key, state)
                return
            }
        }

        // 换了歌（或本机还没上这首）：按房间当前状态重建并播放。
        if (local.current?.key != track.key) {
            /*
             * 带冷却：曲目取流失败时本地状态不会变，下一拍又会来一次，而每次都要打一次
             * 上游。网络不好时那会变成 200 毫秒一次的请求风暴，所以同一首 5 秒内只试一次。
             */
            if (!allowRebuild(track.key)) return
            // 快照还没到（只有播放帧）时至少把这一首放出来，剩下的等房间快照来补。
            val queue = roomTracks.ifEmpty { listOfNotNull(track.toUnifiedTrack()) }
            rebuild(queue, track.key, state)
            return
        }

        // 同一首：先对齐播放态，再收拾进度偏差。
        if (state.playing && !local.isPlaying && !atTrackEnd(local)) {
            playback.play()
        } else if (!state.playing && local.isPlaying) {
            playback.pause()
        }

        val expected = livePositionMs(state)
        val drift = local.positionMs - expected
        when {
            // 差太多：跳过去，并复位速率——跳完就是准的。
            abs(drift) > SYNC_SEEK_MS -> {
                playback.resetPlaybackSpeed()
                playback.seekTo(expected)
            }

            // 差得不多：用一点点速率差慢慢追（每秒追回约 50 毫秒），听不出来也不卡。
            abs(drift) > SYNC_TRIM_MS -> {
                playback.setPlaybackSpeed(if (drift < 0) 1f + TRIM_RATE else 1f - TRIM_RATE)
            }

            else -> playback.resetPlaybackSpeed()
        }
    }

    /**
     * 本机播放器是不是**已经停在这一首的结尾**。
     *
     * 用来避免「播完之后又被镜像叫起来重播一遍」：那一瞬间服务端还没把下一首推过来，
     * 房间帧里仍是这一首、`playing` 也还是 true，照着它 `play()` 就会把刚放完的歌
     * 从头再放一次。等房间帧换了曲目（或位置回到开头）自然会继续。
     */
    private fun atTrackEnd(local: PlaybackState): Boolean =
        local.durationMs > 0 && local.positionMs >= local.durationMs - END_GUARD_MS

    /**
     * 这一首现在能不能重建（同曲 5 秒冷却）。
     *
     * 挡的是「取流失败 → 下一拍再试」的循环：每次重建都会真的去打一次上游，
     * 没有冷却的话一次网络抖动就会变成每 200 毫秒一次的请求风暴。
     */
    private fun allowRebuild(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastRebuildKey == key && now - lastRebuildAt < REBUILD_COOLDOWN_MS) return false
        lastRebuildKey = key
        lastRebuildAt = now
        return true
    }

    /** 把（房间的）队列装进播放器，并跳到房间当前那一首的位置。 */
    private suspend fun rebuild(queue: List<UnifiedTrack>, trackKey: String, state: RoomPlayback) {
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.key == trackKey }.takeIf { it >= 0 }
            ?: state.index.coerceIn(0, queue.lastIndex)
        syncing = true
        try {
            // 直接驱动播放器：这里是「把队列装进去」本身，再过一次转交口就是自己调自己。
            playback.playDirect(queue, index, livePositionMs(state))
            if (!state.playing) playback.pause()
        } finally {
            syncing = false
        }
    }

    private fun sameQueue(local: List<UnifiedTrack>, room: List<UnifiedTrack>): Boolean {
        if (local.size != room.size) return false
        for (index in local.indices) {
            if (local[index].key != room[index].key) return false
        }
        return true
    }

    /* ------------------------------ 上报（只会是这两种） ------------------------------ */

    /**
     * 本机放完了一首：交给服务端决定下一首。
     *
     * 只报「正符合房间当前曲目」的那一首：本机自动切歌与房间广播会有一小段重叠，
     * 迟到的 `ended` 若把已经切过去的曲目再报一次，就会多跳一首。
     */
    private suspend fun reportEnded(key: String) {
        // 旁观时本机没在放房间的歌，也就谈不上「放完了」。
        if (!seatActive) return
        val roomTrack = _playbackState.value?.track ?: return
        if (roomTrack.key != key) return
        runCatching {
            api.roomAction(client.deviceId, ACTION_ENDED, buildJsonObject { put(FIELD_TRACK_KEY, key) })
        }
    }

    /** 本机播不了这一首：上报让全员跳过（判据见 [isUnplayableFailure]，网络抖动不报）。 */
    private suspend fun reportUnplayable(key: String) {
        if (!seatActive) return
        val roomTrack = _playbackState.value?.track ?: return
        if (roomTrack.key != key) return
        failedKey = key
        runCatching {
            api.roomAction(client.deviceId, ACTION_UNPLAYABLE, buildJsonObject { put(FIELD_TRACK_KEY, key) })
        }
    }

    /**
     * 提交一个房间操作。刻意不做本地抢先执行：服务端权威，等它的回声再统一应用。
     *
     * 旁观设备发不出这些动作——服务端的鉴权是「坐席设备」级的，它连房间成员都不是。
     * 想控制就先「转移到本机」。
     */
    private fun act(action: String, payload: JsonObject? = null) {
        if (!seatActive) return
        scope.launch {
            runCatching { api.roomAction(client.deviceId, action, payload) }
                .onFailure { _notice.value = it.friendlyMessage() }
        }
    }

    /* ------------------------------ 进出房间的本地准备 ------------------------------ */

    /** 真正拿到席位之后（建了房 / 加入了 / 转移过来了）的本地准备。 */
    private fun enterRoom(roomCode: String) {
        _code.value = roomCode
        _spectating.value = false
        store.save(roomCode, seat = true)
        // 入房即与「设备跟随」互斥：两者都在驱动播放器，必须只留一个。
        following.stopFollow()
        playback.setPersistencePaused(true)
        playback.setRoomPlayMode(true)
        queueDirty = true
    }

    /**
     * 本机进入旁观：账号在房间里，但**播放不在本机**（同账号的另一台设备在放）。
     *
     * 刻意什么都不接管：不捕获个人现场、不暂停落盘、不动播放器、不停设备跟随——
     * 本机就是本机，只是界面上多显示一个「你的账号正在另一台设备上一听」。
     */
    private fun enterSpectating(roomCode: String) {
        _code.value = roomCode
        _spectating.value = true
        store.save(roomCode, seat = false)
    }

    /**
     * 席位被同账号的另一台设备接走：**降级为旁观**，而不是退出房间。
     *
     * 房间还在、两个人还在里面，只是播放换到那台设备上去了。本机要做的只有一件事：
     * 别再驱动播放器——先把入房时对播放器做过的改动收回（播放顺序、速率），再把个人现场
     * 摆回来；房间状态（成员 / 队列 / 时长）继续留着显示，面板上的「转移到本机」
     * 随时能接回来。顺序与 [clearRoom] 一致：先收回房间对播放器的影响，再摆现场。
     *
     * 服务端在这一帧之后紧接着推新的全量快照（那份里本机已是非席位设备），所以这里
     * 不自己猜成员、也不自己重算「谁在放」，等那一帧把身份对齐。
     */
    private fun degradeToSpectating() {
        playback.setRoomPlayMode(false)
        playback.resetPlaybackSpeed()
        restorePersonal()
        failedKey = null
        queueDirty = true
        _spectating.value = true
        _code.value?.let { store.save(it, seat = false) }
    }

    /** 本地离开房间（按当前身份收尾：占席位就恢复个人现场，旁观则只是不再显示）。 */
    private fun leaveLocally() {
        if (_spectating.value) clearSpectating() else finalizeLeave()
    }

    private fun clearSpectating() {
        store.clear()
        _code.value = null
        _spectating.value = false
        _room.value = null
        _playbackState.value = null
    }

    /**
     * 连上之后，把手里的房间码对一次账，决定本机该用哪种身份。
     *
     * 三种身份走的路完全不同：
     * - 本机**占着席位**（重连、掉线宽限期内回来）→ `join` 接回来接着放；
     * - 席位挂在**同账号的另一台设备**上 → 只当旁观，**绝不能 join**
     *   （`join` 在服务端就是「把席位接过来」，那会把对面正在放的歌抢走）；
     * - 这个账号已经不在房间里（席位过了宽限期、网关重启）→ 当没发生过。
     */
    private suspend fun reconcileRoom() {
        if (joining) return

        // 已经在房间里的（重连）：身份没变，直接按席位接回来。
        if (seatActive) {
            reclaim(_code.value)
            return
        }

        val code = pendingCode ?: _code.value ?: return
        val preview = try {
            api.roomPreview(code)
        } catch (error: Throwable) {
            if (error is ApiException && error.status == HTTP_NOT_FOUND) {
                // 房间确实没了：本地收尾（席位在服务端也已经不存在了）。
                if (_code.value == code) leaveLocally() else store.clear()
                pendingCode = null
            } else {
                // 网络问题：留着房间码，下次连上再问。
                pendingCode = code
            }
            return
        }

        pendingCode = null
        if (preview.mine != null && preview.mine != client.deviceId) {
            // 席位在我另一台设备上：一起听跟着那台走了，本机只当旁观。
            enterSpectating(code)
            return
        }
        // 席位空着、或本来就是本机：接回来（join 是幂等的）。
        reclaim(code)
    }

    /**
     * 把席位接到本机。
     *
     * 同一账号的第二台设备执行它，就等于**把播放从另一台设备搬过来**——这正是
     * 界面上「转移到本机」那一下。对面会收到服务端的「席位已被接走」通知，**降级为旁观**：
     * 那边房间继续显示着（随时能再转移回去），只是播放器还给用户自己的队列。
     */
    private suspend fun reclaim(codeArg: String?) {
        val roomCode = codeArg ?: return
        if (joining) return
        joining = true
        try {
            // 进房之前先把本机现场收好：房间队列一接管，用户自己那一队就找不回来了。
            // （已经在房间里时这一步是空操作，现场早收过了。）
            capturePersonal()
            api.joinRoom(roomCode, client.deviceId, client.deviceName, client.deviceKind)
            enterRoom(roomCode)
        } catch (error: Throwable) {
            /*
             * 404 说明房间确实没了（网关重启、已解散）：本地收尾，回到个人播放。
             * 其它错误（断网等）保留房间码与现场，等下一次连接建立再试——此时**不能**
             * 把落盘放开：房间码还在（自认在房间里），一旦把房间队列写进个人存档就找不回来了。
             */
            if (error is ApiException && error.status == HTTP_NOT_FOUND) {
                _notice.value = "一起听已结束"
                leaveLocally()
            }
        } finally {
            joining = false
        }
    }

    /**
     * 本地收尾：清空房间状态、恢复个人现场。服务端侧的退出 / 解散由调用方负责。
     */
    private fun finalizeLeave() {
        clearRoom()
        restorePersonal()
    }

    private fun clearRoom() {
        store.clear()
        _code.value = null
        _spectating.value = false
        _room.value = null
        _playbackState.value = null
        togetherSeconds = 0.0
        togetherSessionSeconds = 0.0
        togetherCounting = false
        togetherSyncedAt = 0L
        failedKey = null
        queueDirty = true
        // 收尾：留着调速会得到一个「莫名快慢几个百分点」的播放器。
        playback.resetPlaybackSpeed()
        playback.setRoomPlayMode(false)
    }

    /**
     * 入房前把本机现场收好，并**立刻**暂停落盘。
     *
     * 暂停必须在这里，而不是等 `enterRoom`：入房是一次请求往返，服务端在响应到达之前
     * 就把房间状态推过来了，那一刻播放器已被房间队列接管——落盘还开着的话，
     * 用户的个人现场就被房间队列覆盖了。
     */
    private fun capturePersonal() {
        if (personal != null) return
        val state = playback.state.value
        personal = if (state.queue.isNotEmpty()) {
            PersonalSnapshot(state.queue, state.index.coerceAtLeast(0), state.positionMs, state.isPlaying)
        } else {
            // 冷启动时播放现场可能还没从落盘恢复到内存（播放器是后接上的），那就直接读
            // 落盘的那一份：房间队列一接管，内存里就再也找不到用户自己的队列了。
            playback.loadPersistedSession()?.let {
                PersonalSnapshot(
                    queue = it.queue,
                    index = it.index.coerceIn(0, it.queue.lastIndex),
                    positionMs = it.positionMs,
                    // 恢复出来的现场一律按暂停摆：不知道当时在不在播，贸然出声更糟。
                    playing = false,
                )
            }
        }
        playback.setPersistencePaused(true)
    }

    /** 退房：把入房前那一份现场原样摆回去。 */
    private fun restorePersonal() {
        val saved = personal
        personal = null
        // 先恢复落盘再摆现场：顺序反过来的话，摆回去的队列不会被写进个人存档。
        playback.setPersistencePaused(false)

        // 没有现场可恢复（刷新后重进房间）：保持播放器现状，别乱动。
        if (saved == null) return
        // 入房前本来就是空的：把被房间接管的队列清掉，而不是留着房间队列。
        if (saved.queue.isEmpty()) {
            playback.clearQueue()
            return
        }

        val index = saved.index.coerceIn(0, saved.queue.lastIndex)
        // 退房恢复现场也是「直接装队列」：此刻房间码还非空，走转交口会被当成房间里的点歌。
        playback.playDirect(saved.queue, index, saved.positionMs)
        if (!saved.playing) playback.pause()
        playback.persistSession()
    }

    private fun trackElement(track: UnifiedTrack) =
        json.encodeToJsonElement(UnifiedTrack.serializer(), track)

    private fun runningSeconds(): Double {
        if (!togetherCounting) return 0.0
        val syncedAt = togetherSyncedAt.takeIf { it > 0L } ?: return 0.0
        return ((client.serverTimeNow - syncedAt) / 1000.0).coerceAtLeast(0.0)
    }

    private companion object {
        /** 镜像比对节拍。纯本地比较，不产生请求。 */
        const val SYNC_TICK_MS = 200L

        /** 偏差超过它就直接 seek（靠调速追三秒要一分钟）。 */
        const val SYNC_SEEK_MS = 3_000L

        /** 偏差小到可以忽略的界限，低于它就说明已经对齐了。 */
        const val SYNC_TRIM_MS = 150L

        /** 追赶用的速率幅度：±5%，每秒追回约 50 毫秒。 */
        const val TRIM_RATE = 0.05f

        /** 距离结尾多近就算「这一首已经放完了」，别再被镜像叫起来重播。 */
        const val END_GUARD_MS = 500L

        /** 同一首曲目的重建冷却，见 [allowRebuild]。 */
        const val REBUILD_COOLDOWN_MS = 5_000L

        const val HTTP_NOT_FOUND = 404

        /** `room: null` 的原因之一：席位被同账号的另一台设备接走了。 */
        const val REASON_SEAT_TAKEN = "seat_taken"

        const val ACTION_PLAY = "play"
        const val ACTION_PAUSE = "pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_PLAY_AT = "playAt"
        const val ACTION_SEEK = "seek"
        const val ACTION_ADD = "add"
        const val ACTION_PLAY_NOW = "playNow"
        const val ACTION_REMOVE = "remove"
        const val ACTION_SET_QUEUE = "setQueue"
        const val ACTION_ENDED = "ended"
        const val ACTION_UNPLAYABLE = "unplayable"

        const val FIELD_INDEX = "index"
        const val FIELD_POSITION = "position"
        const val FIELD_TRACK = "track"
        const val FIELD_TRACK_KEY = "trackKey"
        const val FIELD_QUEUE = "queue"

        const val SKIP_UNPLAYABLE = "unplayable"
    }
}
