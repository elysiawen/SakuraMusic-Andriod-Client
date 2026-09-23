package com.sakura.music.data.connect

import com.sakura.music.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.sakura.music.data.remote.ApiException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 多设备播放入口（协议见仓库根目录 `connect-protocol.md`）。
 *
 * 三件事：维持一条 SSE 长连接拿到设备列表与指令、上报本机状态、给别的设备下发指令。
 * 音频一个字节都不经过这里——别的设备在放什么，我们只看见一行文字描述。
 */
class ConnectClient(
    private val api: ApiClient,
    private val baseUrlProvider: () -> String,
    httpClient: OkHttpClient,
    private val identity: DeviceIdentity,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    val deviceId: String get() = identity.id

    private val _devices = MutableStateFlow<List<DeviceView>>(emptyList())
    val devices: StateFlow<List<DeviceView>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 收到的控制指令。播放器那边订阅它来执行。 */
    private val _commands = MutableSharedFlow<CommandEvent>(extraBufferCapacity = 16)
    val commands: SharedFlow<CommandEvent> = _commands.asSharedFlow()

    /**
     * SSE 用的 client。
     *
     * 读超时给 45 秒，比服务端 25 秒的心跳宽一截：正常时每 25 秒被心跳刷新一次，
     * 而连接「半死」（网络断了但 TCP 还没断）时 45 秒收不到任何字节就会超时，
     * 我们据此重连——这正是协议要求的那个看门狗，不必自己数时间。
     *
     * 不能给成无限：那样半死的连接会一直挂着，设备列表不更新、指令也收不到。
     * 其余设置（Cookie、连接超时等）沿用传入的 client。
     */
    private val streamClient: OkHttpClient =
        httpClient.newBuilder().readTimeout(SSE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    private var eventSource: EventSource? = null
    private var retryJob: Job? = null
    private var attempts = 0
    private val running = AtomicBoolean(false)

    /** 登录之后调用；重复调用没有副作用。 */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        attempts = 0
        connect()
    }

    /** 登出、或者会话过期时调用。 */
    fun stop() {
        running.set(false)
        retryJob?.cancel()
        retryJob = null
        eventSource?.cancel()
        eventSource = null
        _connected.value = false
        _devices.value = emptyList()
    }

    /**
     * 上报本机状态。
     *
     * 结果分四类，是为了让调用方决定「要不要重试」：
     * 网络异常 / 5xx 值得退避重试；4xx 重试没用；`ok:false` 更不是失败
     * （只说明这台设备不在线，等 SSE 接上后补报即可）。
     */
    suspend fun report(state: DeviceState): ConnectOutcome {
        val body = ReportBody(deviceId = identity.id, state = state)
        return post("/api/connect/state", json.encodeToJsonElement(ReportBody.serializer(), body))
    }

    /**
     * 给某台设备下发指令。
     *
     * `deviceId` 填的是**自己**：服务端拿它填 `command` 事件的 `from`，接管回传时要靠它
     * 找到收件人。漏填会让接管功能失效。
     *
     * 指令**不重试**：`next` 之类的动作重发一次就真的跳两首。
     */
    suspend fun sendCommand(
        target: String,
        action: String,
        payload: JsonObject? = null,
    ): ConnectOutcome {
        val body = CommandBody(
            deviceId = identity.id,
            target = target,
            action = action,
            payload = payload,
        )
        return post("/api/connect/command", json.encodeToJsonElement(CommandBody.serializer(), body))
    }

    private suspend fun post(path: String, body: JsonElement): ConnectOutcome = try {
        val ok = api.request(
            method = "POST",
            path = path,
            deserializer = OkResponse.serializer(),
            body = body,
        ).ok
        if (ok) ConnectOutcome.Delivered else ConnectOutcome.Offline
    } catch (error: ApiException) {
        // 4xx 是「这个请求本身有问题」，重发多少次都一样；其余（网络、5xx）值得再试。
        if (error.isNetworkError || error.status == 0 || error.status >= 500) {
            ConnectOutcome.Retryable
        } else {
            ConnectOutcome.Fatal
        }
    } catch (error: Throwable) {
        ConnectOutcome.Retryable
    }

    /* ------------------------------ SSE ------------------------------ */

    private fun connect() {
        val request = Request.Builder()
            .url(eventsUrl())
            .header("Accept", "text/event-stream")
            .build()
        eventSource = EventSources.createFactory(streamClient).newEventSource(request, listener)
    }

    private fun eventsUrl(): String {
        val base = baseUrlProvider().trim().trimEnd('/')
        return "$base/api/connect/events?deviceId=${identity.id}" +
            "&name=${encode(identity.name)}&kind=${identity.kind}"
    }

    private fun encode(text: String): String =
        java.net.URLEncoder.encode(text, "UTF-8")

    private val listener = object : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            attempts = 0
            _connected.value = true
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            when (type) {
                // hello 与 devices 都是全量列表，处理方式一样，区别只是 hello 还带自己的 id。
                "hello" -> runCatching { json.decodeFromString(HelloEvent.serializer(), data) }
                    .onSuccess { _devices.value = it.devices }

                "devices" -> runCatching { json.decodeFromString(DevicesEvent.serializer(), data) }
                    .onSuccess { _devices.value = it.devices }

                "command" -> runCatching { json.decodeFromString(CommandEvent.serializer(), data) }
                    .onSuccess {
                        // 在播放线程之外执行：这里只是把指令转交给订阅者。
                        _commands.tryEmit(it)
                    }
            }
        }

        override fun onClosed(eventSource: EventSource) {
            _connected.value = false
            scheduleRetry()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            _connected.value = false
            // 401 不重连：会话过期了，再试也只会一直失败；等登录成功后重新 start。
            if (response?.code == 401) {
                running.set(false)
                return
            }
            scheduleRetry()
        }
    }

    /** 指数退避（1s 起、30s 封顶）。 */
    private fun scheduleRetry() {
        if (!running.get()) return
        retryJob?.cancel()
        retryJob = scope.launch {
            val wait = (1 shl attempts.coerceAtMost(5)).coerceAtMost(MAX_RETRY_SECONDS)
            attempts++
            delay(wait * 1000L)
            if (running.get()) connect()
        }
    }

    /** 上报/下发用的请求体。 */
    @Serializable
    private data class ReportBody(val deviceId: String, val state: DeviceState)

    @Serializable
    private data class CommandBody(
        val deviceId: String,
        val target: String,
        val action: String,
        val payload: JsonObject? = null,
    )

    @Serializable
    private data class OkResponse(val ok: Boolean = false)

    private companion object {
        const val MAX_RETRY_SECONDS = 30

        /** 服务端心跳是 25 秒一次，这里留出余量。 */
        const val SSE_READ_TIMEOUT_SECONDS = 45L
    }
}

/**
 * 一次上报 / 下发的结局。
 *
 * 分开这几种是为了回答唯一一个问题：**要不要重试**。
 * 协议里明确区分了「网络异常、5xx」（值得重试）、「4xx」（重试没用）与
 * 「`ok: false`」（不是失败，只是设备当下不在线）。
 */
enum class ConnectOutcome {
    /** 已送达。 */
    Delivered,

    /** 这台设备此刻不在线（响应 `ok: false`）。等重连后补报即可，不要重试。 */
    Offline,

    /** 网络异常或 5xx：值得退避重试。 */
    Retryable,

    /** 4xx：会话过期、参数不合法之类，重试没用。 */
    Fatal,
}
