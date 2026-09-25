package com.sakura.music.core.player

import android.os.SystemClock
import com.sakura.music.data.connect.CommandEvent
import com.sakura.music.data.connect.ConnectClient
import com.sakura.music.data.connect.ConnectOutcome
import com.sakura.music.data.connect.DeviceState
import com.sakura.music.data.connect.DeviceView
import com.sakura.music.data.connect.SeekPayload
import com.sakura.music.data.connect.TransferPayload
import com.sakura.music.data.connect.VolumePayload
import com.sakura.music.data.connect.toTrackSummary
import com.sakura.music.data.connect.toUnifiedTrack
import com.sakura.music.data.model.Quality
import com.sakura.music.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.math.abs

/**
 * 让本机成为多设备网络里的一员：上报自己在放什么，执行别的设备发来的指令。
 *
 * 与 [ConnectClient] 的分工：那边只管通道（连、收、发），这边管**语义**——
 * 什么算状态变化、收到 `release` 要回传什么、`transfer` 过来的队列怎么载入。
 * 放在播放层是因为它必须调用和本地点击**完全相同**的那套播放器方法：
 * 单开一条「被遥控」的路径，两条路会慢慢长歪。
 *
 * ## 上报是纯事件驱动的，没有周期上报
 *
 * 协议明确要求：一台正常播放的设备，上行请求数应该是 **0**。进度由控制端拿
 * `positionAt` 自己推算，不需要我们每几秒报一次。代价是失去了「周期对表」的自愈能力，
 * 所以配了两样保险：
 *
 * - **失败重试**：丢一次就没人纠正了，必须退避重试；
 * - **偏差检测**：每秒在本地比一比「实际进度」和「按上报推算的进度」，
 *   兜住两类事件驱动看不见的情况——播放中拖进度条、以及音频卡顿。
 */
class ConnectSync(
    private val client: ConnectClient,
    private val playback: PlaybackCenter,
    settings: SettingsStore,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    /** 音质只用于上报显示，跟着设置走。 */
    private val quality = MutableStateFlow(Quality.Default.id)

    /**
     * 正在跟随哪台设备。跟随 = 本机变成对方的镜像输出：曲目、播放态、进度都跟着它走。
     *
     * **必须声明在 [init] 块之前**：init 启动的协程跑在别的线程上，会立刻访问它——
     * 声明在后面的话，构造器还没给字段赋值，拿到的就是 null（`StateFlowImpl` 上的
     * NPE，启动瞬间随机崩）。
     */
    private val _following = MutableStateFlow<FollowTarget?>(null)

    /** 非 null 表示正在跟随。界面据此显示状态与「停止」入口。 */
    val following: StateFlow<FollowTarget?> = _following.asStateFlow()

    /** 正在跟随哪台设备。跟随 = 本机变成对方的镜像输出：曲目、播放态、进度都跟着它走。 */
    data class FollowTarget(val deviceId: String, val name: String)

    /** 上报串行化：重试与事件上报撞在一起时，别让基准位置互相盖掉。 */
    private val reportLock = Mutex()

    /** 上次**成功**上报时的位置（秒）与本地单调时刻，偏差检测的基准。 */
    private var reportedPositionSec = 0.0
    private var reportedAtMono = 0L
    private var lastDriftReportAt = 0L

    /**
     * 跟随目标开始「在列表里缺席」的时刻（单调时钟毫秒）。0 表示它在列表里。
     *
     * 单看一帧列表分不清「下线」和「瞬断重连」：设备重连时旧连接被摘除与新连接
     * 入册之间会短暂不在列表里。以前一帧看不见就放弃跟随，一次毫秒级的中断就能
     * 把同步播放永久打断，而且恢复不了（following 已经清掉了）。
     */
    private var followMissingSinceMono = 0L

    init {
        scope.launch {
            settings.quality.collect { quality.value = it.id }
        }

        // 状态真的变了才报：一次切歌会连着改好几个字段，合并一下别发三遍。
        scope.launch {
            playback.state
                .map {
                    Snapshot(
                        playing = it.isPlaying,
                        index = it.index,
                        key = it.current?.key,
                        queue = it.queue.size,
                        volume = it.volume,
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    delay(CHANGE_DEBOUNCE_MS)
                    report()
                }
        }

        // 拖进度条：进度跳变不产生任何状态变化，不主动说一声别的设备就看不到。
        // 播放中和暂停中都要报——暂停时拖完，控制端显示的位置也该跟着动。
        scope.launch {
            playback.seeks.collect { report() }
        }

        // 刚连上（含重连）就补报一次：中间断过，本地状态可能与服务端记得的不一致。
        scope.launch {
            client.connected.collect { connected ->
                if (connected) {
                    report()
                } else if (_following.value != null) {
                    // 连接断了：对方的状态不再更新，先别追了。速率也要复位，
                    // 否则重连之前这段时间会一直挂着个「快/慢几个百分点」的播放器。
                    playback.resetPlaybackSpeed()
                }
            }
        }

        // following 一变就要立刻上报——对端正是靠它确认「对方到底跟上了没有」。
        scope.launch {
            _following.collect { report() }
        }

        // 偏差检测：纯本地，不产生网络请求。
        scope.launch {
            while (true) {
                delay(DRIFT_CHECK_INTERVAL_MS)
                detectDrift()
                // 目标彻底下线后不会再有广播，光靠 devices 事件是等不到第二次判定的。
                expireFollowIfTargetGone()
            }
        }

        scope.launch {
            client.commands.collect { handle(it) }
        }

        // 跟随：目标那边一动，`devices` 事件就会推过来，照它对一遍账即可。
        scope.launch {
            client.devices.collect { devices -> syncFollow(devices) }
        }
    }

    /* ------------------------------ 上报 ------------------------------ */

    private suspend fun report() {
        // 没连上就别发：服务端会回 ok:false，白白多一次请求。
        if (!client.connected.value) return
        reportLock.withLock {
            reportLocked()
        }
    }

    private suspend fun reportLocked() {
        // 0.8s、2.4s 两次退避，加上首次共 3 次。
        RETRY_DELAYS_MS.forEach { wait ->
            if (wait > 0) delay(wait)
            val state = snapshot()
            when (client.report(state)) {
                ConnectOutcome.Delivered -> {
                    // 只有真的报进去了才更新基准：ok:false 与失败都没在服务端留下痕迹。
                    reportedPositionSec = state.position
                    reportedAtMono = SystemClock.elapsedRealtime()
                    return
                }

                // 不在线不是失败；4xx 重试也没用。两种情况都就此打住。
                ConnectOutcome.Offline, ConnectOutcome.Fatal -> return

                ConnectOutcome.Retryable -> Unit
            }
        }
    }

    /**
     * 实际进度与「按上次上报推算的进度」对不上就补报一次。
     *
     * 时间基准用单调时钟（[SystemClock.elapsedRealtime]）而不是挂钟：
     * 系统校时、睡眠唤醒会让挂钟跳变，那会被误判成「用户拖了进度」。
     */
    private suspend fun detectDrift() {
        if (!playback.state.value.isPlaying) return
        if (reportedAtMono == 0L) return

        val now = SystemClock.elapsedRealtime()
        val actual = playback.state.value.positionMs / 1000.0
        val expected = reportedPositionSec + (now - reportedAtMono) / 1000.0
        if (abs(actual - expected) <= DRIFT_THRESHOLD_SECONDS) return

        // 卡顿时偏差会一直存在，必须冷却，否则补报比原来的周期上报还频繁。
        if (now - lastDriftReportAt < DRIFT_COOLDOWN_MS) return
        lastDriftReportAt = now
        report()
    }

    private fun snapshot(): DeviceState {
        val state = playback.state.value
        return DeviceState(
            track = state.current?.toTrackSummary(),
            playing = state.isPlaying,
            position = state.positionMs / 1000.0,
            duration = state.durationMs / 1000.0,
            volume = state.volume.toDouble(),
            quality = quality.value,
            queueLength = state.queue.size,
            // 「谁跟着谁」要让对端看得见：光看两台都在放同一首歌，分不清是同步播放
            // 还是各放各的，被跟随的一方就没法确认对方到底跟上了没有。
            following = _following.value?.deviceId,
        )
    }

    /** 判断「状态是否真的变了」用的几个字段。 */
    private data class Snapshot(
        val playing: Boolean,
        val index: Int,
        val key: String?,
        val queue: Int,
        /**
         * 音量不参与跟随同步（各设备的输出音量各自管），但它要显示在别的设备的
         * 面板上，所以本机一调就得报一次——漏了的话对方看到的永远是旧值。
         */
        val volume: Float,
    )

    /* ------------------------------ 收指令 ------------------------------ */

    private suspend fun handle(event: CommandEvent) {
        when (event.action) {
            // play 可以带队列（等于 transfer），不带就是 resume。
            "play" -> event.transferPayload()?.let(::loadAndPlay) ?: playback.play()

            "pause" -> playback.pause()
            "toggle" -> playback.toggle()
            "next" -> playback.next()
            "prev" -> playback.previous()

            "seek" -> event.decode(SeekPayload.serializer())?.let {
                playback.seekTo((it.position * 1000).toLong())
            }

            // 调的是这台设备的输出音量，与播放内容无关（协议 7.2）。
            "volume" -> event.decode(VolumePayload.serializer())?.let {
                playback.setVolume(it.volume.toFloat())
            }

            // 对方要求本机跟随它。发起方（`from`）就是要跟随的对象——
            // 「让谁跟随谁」这个意图没法靠广播推断，只能有人明确要求一次。
            // 注意请求方不因此获得任何持久权限：随时可以自己停掉（unfollow 或本机操作）。
            // 若请求会构成互指（它正跟着我），拒绝即可——它看 following 没变，
            // 自然知道这台设备没接受。
            "follow" -> if (canFollow(event.from)) startFollow(event.from, nameOf(event.from))

            "unfollow" -> stopFollow()

            "transfer" -> event.transferPayload()?.let(::loadAndPlay)

            "release" -> handOver(event.from)

            else -> return
        }
        /*
         * 先让主线程把刚发出去的动作做完，再补报。
         *
         * 播放器只能从主线程碰，所以上面那些方法都是往主线程投递一下就返回。
         * 不等它们落地就上报的话，报的还是改动之前的状态（进度、音量），
         * 控制端据此判断就会以为指令没生效——而这正是它唯一的送达判据。
         */
        withContext(Dispatchers.Main.immediate) { Unit }
        // 执行完立刻补报：这不只是「让进度刷新得快一点」，控制端就是靠它确认指令送达的。
        report()
    }

    /**
     * 收到 `release`：暂停自己，并把整条队列回传给发起方。
     *
     * 这是整套协议里唯一的往返：发起方只表达「我要接手」，队列得由持有方回传——
     * 它本来就不掌握别人的队列。收件人是 `command.from`，所以发起方漏填自己的
     * `deviceId` 时这一步会静默失败。
     */
    private suspend fun handOver(target: String) {
        playback.pause()
        if (target.isBlank()) return
        client.sendCommand(target, ACTION_TRANSFER, transferPayload())
    }

    private fun loadAndPlay(payload: TransferPayload) {
        // `sources` 缺了就播不出来——接收方要靠 platform + id 去解析播放地址。
        val tracks = payload.queue.filter { it.sources.isNotEmpty() }
        if (tracks.isEmpty()) return
        val index = payload.index.coerceIn(0, tracks.lastIndex)
        // 位置跟着建队列一起给：先 play 再 seek 的话，那个 seek 会落到旧队列上被吞掉。
        // 协议建议：偏移不超过半秒就不必跳，「刚播就跳」的观感更差。
        val startMs = if (payload.position > MIN_SEEK_SECONDS) {
            (payload.position * 1000).toLong()
        } else {
            0L
        }
        playback.play(tracks, index, startMs)
    }

    /* ------------------------------ 界面用的动作 ------------------------------ */

    /**
     * 把本机正在放的整条队列交给 [target]，并暂停自己（协议里的场景 B）。
     *
     * 本地立刻停下，送达确认在后台等——不让用户为了一个提示多等几秒。
     */
    suspend fun transferTo(target: String): AckResult {
        val state = playback.state.value
        if (state.queue.isEmpty()) return AckResult.Failed

        val before = positionAtOf(target)
        return when (client.sendCommand(target, ACTION_TRANSFER, transferPayload())) {
            ConnectOutcome.Delivered -> {
                playback.pause()
                awaitAck(target, before)
            }

            ConnectOutcome.Offline -> AckResult.Offline
            ConnectOutcome.Fatal, ConnectOutcome.Retryable -> AckResult.Failed
        }
    }

    /**
     * 请 [target] 把它的播放交给我（协议里的场景 A）。
     *
     * 我们只表达意图：对方收到 `release` 后会暂停自己，再以 `transfer` 把队列回传过来，
     * 那时本机才真正开始播。
     *
     * **不做送达确认**：这是个往返，确认应该看回程的 `transfer` 有没有到，
     * 在 `release` 之后就下结论是错的。
     */
    suspend fun requestTakeOver(target: String): AckResult =
        when (client.sendCommand(target, ACTION_RELEASE)) {
            ConnectOutcome.Delivered -> AckResult.Delivered
            ConnectOutcome.Offline -> AckResult.Offline
            ConnectOutcome.Fatal, ConnectOutcome.Retryable -> AckResult.Failed
        }

    /**
     * 发一条指令，**不等送达确认**。
     *
     * 拖进度、拖音量都是连续操作：每次都等 3~5 秒的确认会让手感彻底没法用。
     * 这类指令本来就是「尽力而为」——下一轮 `devices` 事件会把结果告诉我们。
     */
    suspend fun send(target: String, action: String, payload: JsonObject? = null): ConnectOutcome =
        client.sendCommand(target, action, payload)

    /** 简单遥控：暂停、继续、下一首、上一首这类不需要 payload 的动作，发完做送达确认。 */
    suspend fun remote(target: String, action: String): AckResult {
        val before = positionAtOf(target)
        return when (client.sendCommand(target, action)) {
            ConnectOutcome.Delivered -> awaitAck(target, before)
            ConnectOutcome.Offline -> AckResult.Offline
            ConnectOutcome.Fatal, ConnectOutcome.Retryable -> AckResult.Failed
        }
    }

    /**
     * 送达确认（协议 7.4）。
     *
     * 网关返回 `ok: true` 只代表「已经写进那条连接」，不代表对方还在——网络中断的设备
     * 仍然挂在列表里（TCP 要过一会儿才发现）。判据是现成的：对方执行完指令会**立即补报状态**，
     * 所以看它的 `positionAt` 有没有往前走。
     */
    private suspend fun awaitAck(target: String, before: Long?): AckResult {
        delay(ACK_WAIT_MS)
        val device = client.devices.value.firstOrNull { it.deviceId == target }
            ?: return AckResult.Offline
        val after = device.state?.positionAt
        return if (before != null && after != null && after > before) {
            AckResult.Delivered
        } else {
            AckResult.NoResponse
        }
    }

    private fun positionAtOf(deviceId: String): Long? =
        client.devices.value.firstOrNull { it.deviceId == deviceId }?.state?.positionAt

    /* ------------------------------ 跟随播放 ------------------------------ */

    /**
     * 开始跟随。协议 7.5：不需要任何新协议，目标的状态本来就通过 `devices` 事件广播过来。
     */
    fun startFollow(deviceId: String, name: String) {
        if (!canFollow(deviceId)) return
        followMissingSinceMono = 0L
        _following.value = FollowTarget(deviceId, name)
        // 立刻对一次账，不用干等下一个 devices 事件。
        syncFollow(client.devices.value)
    }

    /**
     * 这条跟随关系能不能建立。
     *
     * 禁的是**直接互指**：它跟着我、我又跟着它，就是两台镜像输出互相镜像——没有源头，
     * 只会互相拖拽（双方都在用速率追赶对方，永远稳不下来）。链式（C 跟随 A、A 跟随 B）
     * 不在此列：那是多人看一台，语义没问题。
     */
    private fun canFollow(deviceId: String): Boolean {
        if (deviceId == client.deviceId) return false
        val target = client.devices.value.firstOrNull { it.deviceId == deviceId } ?: return true
        return target.state?.following != client.deviceId
    }

    fun stopFollow() {
        if (_following.value == null) return
        followMissingSinceMono = 0L
        _following.value = null
        // 收尾：留着调速会得到一个「莫名快慢几个百分点」的播放器。
        playback.resetPlaybackSpeed()
    }

    /** 设备名只用于展示；列表里查不到（比如刚下线）就给个通用说法。 */
    private fun nameOf(deviceId: String): String =
        client.devices.value
            .firstOrNull { it.deviceId == deviceId }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: "另一台设备"

    /**
     * 把本机对齐到跟随目标。
     *
     * 做成**单向镜像**：本机的传输控制不转发给对方（协议也是这么建议的）。这样不必去拦
     * 播放条上的每个按钮，用户想自己控制时先停止跟随即可，语义清楚。
     */
    private fun syncFollow(devices: List<DeviceView>) {
        val target = _following.value ?: return
        val device = devices.firstOrNull { it.deviceId == target.deviceId }
        if (device == null) {
            /*
             * 目标不在列表里：先别急着收手。
             *
             * 设备重连时会在列表里缺席一小会儿（旧连接被摘除、新连接还没入册），
             * 一帧看不见就清掉 following 的话，一次毫秒级的中断就能把同步永久打断，
             * 而且对方回来也不会自动恢复。先记下缺席起点，交给宽限期去判
             * （见 [expireFollowIfTargetGone]）——确实下线了才收手。
             */
            if (followMissingSinceMono == 0L) {
                followMissingSinceMono = SystemClock.elapsedRealtime()
            }
            return
        }
        followMissingSinceMono = 0L

        val state = device.state ?: return
        val summary = state.track ?: return
        val local = playback.state.value

        if (local.current?.key != summary.key) {
            // 曲目不同：用 `sources` 重建它正放着的那首，并从同一个进度起播。
            // 重建不出来（对方没带 sources）只能不动——这正是协议要求 sources 的原因。
            val rebuilt = summary.toUnifiedTrack() ?: return
            playback.play(listOf(rebuilt), 0, livePositionMs(state))
            if (!state.playing) playback.pause()
            return
        }

        // 同一首：先对齐播放态。
        if (state.playing != local.isPlaying) {
            if (state.playing) playback.play() else playback.pause()
        }

        /*
         * 再看进度，按代价从低到高分三段（协议 7.5「把偏差压到最小」）：
         *
         * - 大偏差：seek 过去，并复位速率——跳完就是准的；
         * - 小偏差：用 ±5% 的速率去追（每秒追回约 50 毫秒），听感上几乎察觉不到，
         *   比 seek 便宜得多（seek 要重新缓冲，会卡一下）；
         * - 够准：速率复位。
         *
         * 别指望零延迟：各端独立缓冲、独立解码，又没有共享时钟，实际能稳在 ±100~300 毫秒。
         */
        val targetPositionMs = livePositionMs(state)
        val drift = local.positionMs - targetPositionMs
        when {
            abs(drift) > FOLLOW_SEEK_THRESHOLD_MS -> {
                playback.resetPlaybackSpeed()
                playback.seekTo(targetPositionMs)
            }

            abs(drift) > FOLLOW_TRIM_THRESHOLD_MS -> {
                // 落后就放快一点，超前就放慢一点。
                val rate = if (drift < 0) 1f + FOLLOW_TRIM_RATE else 1f - FOLLOW_TRIM_RATE
                playback.setPlaybackSpeed(rate)
            }

            else -> playback.resetPlaybackSpeed()
        }
    }

    /**
     * 目标缺席太久就真的收手。
     *
     * 由每秒的本地定时器兜底（见 [init]）：目标彻底下线之后不再有任何广播，
     * 只靠 `devices` 事件驱动是等不到第二次判定的。
     */
    private fun expireFollowIfTargetGone() {
        if (_following.value == null || followMissingSinceMono == 0L) return
        if (SystemClock.elapsedRealtime() - followMissingSinceMono < FOLLOW_GRACE_MS) return

        followMissingSinceMono = 0L
        // 确实下线了：镜像一个不在的东西没有意义。
        _following.value = null
        // 收尾：留着调速会得到一个「莫名快慢几个百分点」的播放器。
        playback.resetPlaybackSpeed()
    }

    /**
     * 目标设备**此刻**的进度（毫秒）。
     *
     * 收到的是「上报那一刻」的值，播放中要往前推；并且必须**掐上界**——没有周期上报之后，
     * 上一次上报可能已经过去很久，播完那一刻会越过曲长。
     *
     * 时间基准用服务端时间（`positionAt` 也是服务端盖的），不是本机挂钟：两台机器的时钟
     * 差多少，推算就整体偏多少，而且永远不自愈。偏移量来自每次广播带的 `serverNow`。
     */
    private fun livePositionMs(state: DeviceState): Long {
        val serverNow = client.serverTimeNow
        val advanced = if (state.playing) {
            val elapsed = (serverNow - (state.positionAt ?: serverNow)) / 1000.0
            state.position + elapsed
        } else {
            state.position
        }
        val clamped = if (state.duration > 0) advanced.coerceAtMost(state.duration) else advanced
        return (clamped.coerceAtLeast(0.0) * 1000).toLong()
    }

    private fun transferPayload(): JsonObject {
        val state = playback.state.value
        val payload = TransferPayload(
            queue = state.queue,
            index = state.index.coerceAtLeast(0),
            position = state.positionMs / 1000.0,
        )
        return json.encodeToJsonElement(TransferPayload.serializer(), payload) as JsonObject
    }

    private fun <T> CommandEvent.decode(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T? = runCatching {
        payload?.let { json.decodeFromJsonElement(deserializer, it as JsonElement) }
    }.getOrNull()

    private fun CommandEvent.transferPayload(): TransferPayload? = decode(TransferPayload.serializer())

    private companion object {
        /** 首次 + 两次退避，与协议建议的「0.8s、2.4s，共 2~3 次」一致。 */
        val RETRY_DELAYS_MS = listOf(0L, 800L, 2400L)

        const val CHANGE_DEBOUNCE_MS = 300L
        const val DRIFT_CHECK_INTERVAL_MS = 1_000L
        const val DRIFT_THRESHOLD_SECONDS = 1.5
        const val DRIFT_COOLDOWN_MS = 5_000L
        const val ACK_WAIT_MS = 3_500L
        const val MIN_SEEK_SECONDS = 0.5

        /** 跟随的进度偏差超过它就直接 seek（协议 7.5 的三段式）。 */
        const val FOLLOW_SEEK_THRESHOLD_MS = 3_000L

        /** 偏差小到可以忽略的界限，低于它就说明已经对齐了。 */
        const val FOLLOW_TRIM_THRESHOLD_MS = 150L

        /** 追赶用的速率幅度：±5%，每秒追回约 50 毫秒。 */
        const val FOLLOW_TRIM_RATE = 0.05f

        /**
         * 目标在列表里缺席多久才认定它真的下线。
         *
         * 设备重连时会在列表里缺席一小会儿，那段时间不该被当成「目标下线」：
         * 它一回来就该自动接着同步，而不是让用户重新点一次「跟随」。
         */
        const val FOLLOW_GRACE_MS = 15_000L
        const val ACTION_TRANSFER = "transfer"
        const val ACTION_RELEASE = "release"
    }
}

/** 一条遥控指令的送达结果（协议 7.4）。 */
enum class AckResult {
    /** 对方执行了，而且状态确实更新了。 */
    Delivered,

    /** 对方可能已经离线：不在设备列表里，或者发送时就不在线。 */
    Offline,

    /** 发出去了，但对方迟迟没更新状态——可能没响应。 */
    NoResponse,

    /** 我们这边就没发成功（网络问题），可以重试。 */
    Failed,
}
