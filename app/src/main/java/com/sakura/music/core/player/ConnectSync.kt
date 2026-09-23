package com.sakura.music.core.player

import android.os.SystemClock
import com.sakura.music.data.connect.CommandEvent
import com.sakura.music.data.connect.ConnectClient
import com.sakura.music.data.connect.ConnectOutcome
import com.sakura.music.data.connect.DeviceState
import com.sakura.music.data.connect.SeekPayload
import com.sakura.music.data.connect.TransferPayload
import com.sakura.music.data.connect.toTrackSummary
import com.sakura.music.data.model.Quality
import com.sakura.music.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** 上报串行化：重试与事件上报撞在一起时，别让基准位置互相盖掉。 */
    private val reportLock = Mutex()

    /** 上次**成功**上报时的位置（秒）与本地单调时刻，偏差检测的基准。 */
    private var reportedPositionSec = 0.0
    private var reportedAtMono = 0L
    private var lastDriftReportAt = 0L

    init {
        scope.launch {
            settings.quality.collect { quality.value = it.id }
        }

        // 状态真的变了才报：一次切歌会连着改好几个字段，合并一下别发三遍。
        scope.launch {
            playback.state
                .map { Snapshot(playing = it.isPlaying, index = it.index, key = it.current?.key, queue = it.queue.size) }
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
            client.connected.collect { connected -> if (connected) report() }
        }

        // 偏差检测：纯本地，不产生网络请求。
        scope.launch {
            while (true) {
                delay(DRIFT_CHECK_INTERVAL_MS)
                detectDrift()
            }
        }

        scope.launch {
            client.commands.collect { handle(it) }
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
        )
    }

    /** 判断「状态是否真的变了」用的几个字段。 */
    private data class Snapshot(
        val playing: Boolean,
        val index: Int,
        val key: String?,
        val queue: Int,
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

            "transfer" -> event.transferPayload()?.let(::loadAndPlay)

            "release" -> handOver(event.from)

            else -> return
        }
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
