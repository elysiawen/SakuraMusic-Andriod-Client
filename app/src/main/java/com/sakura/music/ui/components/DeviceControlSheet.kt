package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.core.player.AckResult
import com.sakura.music.data.connect.ConnectOutcome
import com.sakura.music.data.connect.TrackSummary
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一台设备的控制面板：看它在放什么、拖动它的进度与音量、把播放交接过去。
 *
 * 进度与音量都做成**拖动**而不是加减按钮：遥控的目标是「让它和我想的一样」，
 * 而这两个量本来就是连续值。
 *
 * 这两条指令走的是**不等送达确认**的通道（[com.sakura.music.core.player.ConnectSync.send]）：
 * 拖动是连续操作，每次等 3~5 秒的确认会让手感彻底没法用；结果让下一轮 `devices` 事件
 * 告诉我们即可。
 */
@Composable
fun DeviceControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val devices by container.connectClient.devices.collectAsStateWithLifecycle()
    val playback by container.playbackCenter.state.collectAsStateWithLifecycle()
    val following by container.connectSync.following.collectAsStateWithLifecycle()
    // 房间快照：只为了认出「我账号的席位在哪台设备上」，见下面的只读判断。
    val room by container.roomSync.room.collectAsStateWithLifecycle()

    // 取实时状态而不是打开那一刻的快照：对方一直在放，界面得跟着走。
    val device = devices.firstOrNull { it.deviceId == deviceId }
    if (device == null) {
        // 对方下线了：列表里已经没有它，这个面板也就没意义了。
        LaunchedEffect(Unit) {
            onMessage("那台设备已离线")
            onDismiss()
        }
        return
    }

    val selfId = container.connectClient.deviceId
    val isSelf = device.deviceId == selfId
    val state = device.state
    val track = state?.track

    /*
     * 一起听占着席位的设备是**只读**的：它放的是房间的歌，对面发来的遥控会被它直接拒掉
     * （见 `ConnectSync.roomSeat`）。所以这里先把控件收掉，别让用户按下去却毫无反应。
     *
     * 反过来照旧可以遥控不在席位上的设备：那是它自己的播放，与房间无关。
     */
    val roomSeatId = room?.let { container.roomSync.myAccountMember()?.deviceId }
    val readOnly = roomSeatId != null && device.deviceId == roomSeatId

    /*
     * 房间进行中，这块面板只剩一件事：**决定声音从哪台设备出**（见末尾那一行）。
     * 内容控制归房间；投放 / 接管 / 跟随改的都是"谁在放"，房间里用它们只会得到
     * 一份副本播放，然后被房间的镜像拉回去。
     */
    val inRoom = room != null

    // 每秒重算一次对方进度。
    var now by remember { mutableLongStateOf(container.connectClient.serverTimeNow) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = container.connectClient.serverTimeNow
        }
    }

    /*
     * 滑块的草稿值。
     *
     * 它有两段生命期，少一段都会闪：
     * - 手指按着：对方的进度每秒都会重算一次，value 若一直跟着它，滑块会被盖回去；
     * - 松手之后：指令还在往返，对方上报的仍是旧值。此时立刻清掉草稿的话，滑块会先弹回
     *   旧位置、等广播到了再跳回手指的位置 —— 就是那一下「闪回去又闪回来」。
     *   所以草稿要留到真实值追上它（或超时）再撤。
     */
    var seekDraft by remember(device.deviceId) { mutableStateOf<SliderDraft?>(null) }
    var volumeDraft by remember(device.deviceId) { mutableStateOf<SliderDraft?>(null) }
    // 「让声音切到这台」在途：指令已发出、等对方接上。期间不让人连点。
    var handingOver by remember(device.deviceId) { mutableStateOf(false) }
    // 投放 / 接管在途：两者都要等一次往返（3.5 秒 / 5 秒），期间同样不让人连点。
    var movingPlayback by remember(device.deviceId) { mutableStateOf(false) }

    // 等回执：只是本地数值比较，不发任何请求。
    LaunchedEffect(device.deviceId) {
        while (true) {
            delay(SLIDER_SETTLE_MS)
            val liveState = container.connectClient.devices.value
                .firstOrNull { it.deviceId == device.deviceId }
                ?.state
            val serverNow = container.connectClient.serverTimeNow
            val wallNow = System.currentTimeMillis()

            seekDraft = seekDraft?.takeUnless { draft ->
                draftSettled(
                    draft = draft,
                    real = liveState?.let { livePositionSeconds(it, serverNow).toFloat() },
                    trackKey = liveState?.track?.key,
                    wallNow = wallNow,
                    isVolume = false,
                )
            }
            volumeDraft = volumeDraft?.takeUnless { draft ->
                draftSettled(
                    draft = draft,
                    real = liveState?.volume?.toFloat(),
                    trackKey = liveState?.track?.key,
                    wallNow = wallNow,
                    isVolume = true,
                )
            }
        }
    }

    /** 发一条不等确认的指令；只有发不出去时才吭声（成功看状态变化就够了）。 */
    fun fire(action: String, payload: JsonObject? = null) {
        scope.launch {
            val outcome = container.connectSync.send(device.deviceId, action, payload)
            if (outcome != ConnectOutcome.Delivered) onMessage(sendFailure(device.name, outcome))
        }
    }

    /** 类型化的遥控动作（播放模式 / 播放列表）；同样只在发不出去时吭声。 */
    fun fireRemote(block: suspend () -> ConnectOutcome) {
        scope.launch {
            val outcome = block()
            if (outcome != ConnectOutcome.Delivered) onMessage(sendFailure(device.name, outcome))
        }
    }

    /** 改完对方的队列后稍等再要一次：太快会拿到改之前那份。 */
    fun refreshQueueSoon() {
        scope.launch {
            delay(400)
            container.connectSync.requestQueue(device.deviceId)
        }
    }

    SheetScaffold(title = device.name, onDismiss = onDismiss) {
        // 内容比屏幕高时可以滚：小屏上「跟随」那一组也不至于被硬切掉。
        // 顶部留白是给分隔线的：歌名直接贴上去会像被裁掉一截。
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            Text(
                text = track?.title?.ifBlank { "未知歌曲" } ?: "空闲",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                track?.artists?.takeIf { it.isNotBlank() },
                track?.album?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isSelf) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "这是本机。它自己的控制都在播放页上——这里只用来遥控别的设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                return@Column
            }

            if (readOnly) {
                // 内容控制全收掉，只留音量——那是设备的属性，不是播放内容的。
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "这台正在放一起听的歌，播放归房间统一管——这里只剩音量。" +
                        "要切歌、拖进度，去「一起听」面板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // 条件写成「正向」的：progress 里要拿 state/track 当非空用，只能这样才智能转换。
            if (!readOnly && state != null && track != null) {
                /* ------------------------------ 进度 ------------------------------ */
                // 进度只能绑在「它手里有东西」上：没内容就没有进度可言。
                val durationSec = state.duration.toFloat().coerceAtLeast(0f)
                // 草稿优先：按着时是手指的位置，松手后则是「等对方回执」的目标值。
                val shown = seekDraft
                    ?.takeIf { it.trackKey == track.key }
                    ?.value
                    ?: livePositionSeconds(state, now).toFloat()
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = shown.coerceIn(0f, durationSec.coerceAtLeast(0.1f)),
                    onValueChange = { seekDraft = SliderDraft(value = it, trackKey = track.key) },
                    onValueChangeFinished = {
                        // 松手才发指令：一路拖一路发就是请求洪流。
                        // 草稿不在这里清掉 —— 要留到对方回执追上来，否则会先弹回旧位置。
                        seekDraft?.let { draft ->
                            seekDraft = draft.copy(committedAt = System.currentTimeMillis())
                            fire("seek", seekPayload(draft.value.toDouble()))
                        }
                    },
                    valueRange = 0f..durationSec.coerceAtLeast(0.1f),
                    enabled = durationSec > 0f,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration((shown * 1000).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration((durationSec * 1000).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!readOnly) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "这台设备没有在放东西。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            /*
             * 音量是**设备**的属性，不是内容的：同一份播放状态在各台设备上可以不一样，
             * 所以它跟着设备走，也是跟随时唯一不该同步的东西。
             *
             * 但也正因为它属于设备而不是内容，**空着的设备一样有音量可调** ——
             * 它只是没在放歌，不代表不需要合适的响度。所以这一行不跟着「有内容」藏起来。
             */
            val volume = volumeDraft?.value ?: (state?.volume?.toFloat() ?: 1f)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (volume <= 0.01f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = "音量",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = volume.coerceIn(0f, 1f),
                    onValueChange = { volumeDraft = SliderDraft(value = it) },
                    onValueChangeFinished = {
                        // 同进度：草稿留到对方回执把音量报回来（它那边有 300ms 防抖）。
                        volumeDraft?.let { draft ->
                            volumeDraft = draft.copy(committedAt = System.currentTimeMillis())
                            fire("volume", volumePayload(draft.value.toDouble()))
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                Text(
                    text = "${(volume.coerceIn(0f, 1f) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 房间里内容控制归房间：进度、切歌、模式全收掉，只留上面那行音量。
            if (readOnly) return@Column

            /* ------------------------------ 传输控制 ------------------------------ */
            // 模式是设备自己的状态：下发后等下一轮 devices 广播回填。
            val remoteMode = state?.playMode?.let(PlayMode::fromIdOrNull)
            var showModes by remember(device.deviceId) { mutableStateOf(false) }
            var showQueue by remember(device.deviceId) { mutableStateOf(false) }

            // 模式与队列收成切歌行两侧的图标，点开才出选择。
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { fire("prev") }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
                    }
                    FilledIconButton(onClick = { fire("toggle") }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = if (state?.playing == true) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state?.playing == true) "暂停" else "播放",
                        )
                    }
                    IconButton(onClick = { fire("next") }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                    }
                }

                // 左：播放模式。图标画的就是它**现在**的模式，点开才给选项。
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    IconButton(onClick = { showModes = true }) {
                        Icon(
                            imageVector = modeIcon(remoteMode),
                            contentDescription = "播放模式：${remoteMode?.label ?: "未知"}",
                            tint = if (remoteMode == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    DropdownMenu(expanded = showModes, onDismissRequest = { showModes = false }) {
                        PlayMode.entries.forEach { mode ->
                            val selected = remoteMode == mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = modeIcon(mode),
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                onClick = {
                                    showModes = false
                                    fireRemote { container.connectSync.remoteMode(device.deviceId, mode) }
                                },
                            )
                        }
                    }
                }

                // 右：播放列表。角标就是对方队列的长度，省掉一行「N 首」的说明。
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showQueue = true }) {
                        val queued = state?.queueLength ?: 0
                        BadgedBox(
                            badge = {
                                if (queued > 0) Badge { Text(if (queued > 99) "99+" else "$queued") }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "播放列表",
                            )
                        }
                    }
                }
            }

            /* ------------------------------ 播放列表弹层 ------------------------------ */
            // 队列体积大，点开才拉；每次打开、改完都重新要一份。
            val remoteQueues by container.connectSync.remoteQueues.collectAsStateWithLifecycle()
            val remoteQueue = remoteQueues[device.deviceId]

            LaunchedEffect(showQueue, device.deviceId) {
                if (showQueue) container.connectSync.requestQueue(device.deviceId)
            }

            if (showQueue) {
                // 再开一层底部弹层；居中对话框的出场方向和这里对不上。
                SheetScaffold(title = "播放列表 · ${device.name}", onDismiss = { showQueue = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        val snapshot = remoteQueue
                        when {
                            snapshot == null -> Text(
                                text = "正在读取对方的播放列表…（对方版本较旧时可能读不到）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            )

                            snapshot.queue.isEmpty() -> Text(
                                text = "对方的播放列表是空的",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            )

                            else -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "共 ${snapshot.queue.size} 首 · 点一首让它播那首",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            fireRemote {
                                                container.connectSync.requestQueue(device.deviceId)
                                            }
                                        },
                                    ) {
                                        Text("刷新")
                                    }
                                }
                                // 列表自己滚（封顶 420dp）：和本机队列那张弹层同一套比例。
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp),
                                ) {
                                    itemsIndexed(snapshot.queue) { index, item ->
                                        RemoteQueueRow(
                                            index = index,
                                            track = item,
                                            current = index == snapshot.index,
                                            onClick = {
                                                fireRemote {
                                                    container.connectSync.remotePlayAt(device.deviceId, index)
                                                }
                                                refreshQueueSoon()
                                            },
                                            onRemove = {
                                                fireRemote {
                                                    container.connectSync.remoteRemoveAt(device.deviceId, index)
                                                }
                                                refreshQueueSoon()
                                            },
                                        )
                                    }
                                }
                                // 本机有正在播的歌时，才给「把它塞进去」这个动作。
                                val local = playback.current
                                if (local != null) {
                                    TextButton(
                                        onClick = {
                                            fireRemote {
                                                container.connectSync.remoteAddTrack(device.deviceId, local)
                                            }
                                            refreshQueueSoon()
                                        },
                                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                                    ) {
                                        Text("＋ 加入本机当前这首")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ------------------------------ 交接 ------------------------------ */
            Spacer(Modifier.height(14.dp))

            if (inRoom) {
                /*
                 * 房间里这块只剩一件事：**把声音切到那台**。
                 *
                 * 投放 / 接管 / 跟随改的都是「谁在放」，而房间期间这件事只有一条路——
                 * 让目标设备接走席位：它自己 join 接过来，服务端随之把上一台降级为旁观。
                 * 走设备互控那条老路只会得到一份副本播放，然后被房间的镜像拉回去。
                 */
                ControlRow(
                    icon = Icons.Rounded.Cast,
                    title = "让声音切到这台",
                    subtitle = if (handingOver) {
                        "正在切换…"
                    } else {
                        "把一起听的播放转到它，本机改为只看"
                    },
                    enabled = !handingOver,
                    onClick = {
                        handingOver = true
                        scope.launch {
                            /*
                             * 等它真的接上再报成功：这条指令只保证「送到」，
                             * 接席位是那台设备自己去 join。没接过去就如实说没反应，
                             * 否则用户会盯着一个还在放自己歌的设备以为切过去了。
                             */
                            val handed = container.roomSync.handSeatTo(device.deviceId)
                            handingOver = false
                            if (handed) {
                                onMessage("声音已切到「${device.name}」")
                                onDismiss()
                            } else {
                                onMessage("「${device.name}」没有接过去，可能已离线或没响应")
                            }
                        }
                    },
                )
            } else if (playback.queue.isNotEmpty()) {
                ControlRow(
                    icon = Icons.Rounded.Cast,
                    title = "投放到这台设备",
                    subtitle = if (movingPlayback) {
                        "正在投放…"
                    } else {
                        "把本机正在播的队列连进度交给它，本机停下"
                    },
                    enabled = !movingPlayback,
                    onClick = {
                        movingPlayback = true
                        scope.launch {
                            val result = container.connectSync.transferTo(device.deviceId)
                            movingPlayback = false
                            onMessage(transferMessage(device.name, result))
                        }
                    },
                )
            }

            // 接管需要它手里确实有东西可搬；房间里同理不开放，见 [inRoom]。
            if (track != null && !inRoom) {
                ControlRow(
                    icon = Icons.Rounded.Devices,
                    title = "接管它的播放",
                    subtitle = if (movingPlayback) "正在接管…" else "把它手里的队列搬到本机来放",
                    enabled = !movingPlayback,
                    onClick = {
                        movingPlayback = true
                        scope.launch {
                            val result = container.connectSync.requestTakeOver(device.deviceId)
                            movingPlayback = false
                            onMessage(
                                // 这是往返：队列真的回来了才算接管成功。
                                when (result) {
                                    AckResult.Delivered -> "已接管「${device.name}」的播放"
                                    AckResult.NoResponse ->
                                        "「${device.name}」没有把播放交过来，可能已离线"

                                    AckResult.Offline -> "「${device.name}」可能已离线"
                                    else -> "请求没发出去，检查一下网络"
                                }
                            )
                        }
                    },
                )
            }

            val isFollowing = following?.deviceId == device.deviceId
            // 它正跟着本机吗？这个状态同时决定「跟随这台设备」要不要显示。
            val followsMe = state?.following == selfId
            /*
             * 跟随的两个方向**互斥显示**：任意时刻最多出现一个跟随动作。
             *
             * 不是省地方——是两个选项同时摆出来时，用户可以点出一个**注定无效**的组合：
             * 本机跟着它、再让它跟着本机，就是互指。协议层会拒绝这种 follow
             * （对方的 canFollow 拦下），但拒绝是静默的，界面上只会表现成「没反应」。
             * 所以谁的方向被占着，就只显示谁的动作；想换方向，先停掉当前那个。
             *
             * 这里也刻意不要求它「有内容」：本机正跟着它、或它正跟着本机时，解除的入口
             * 必须一直在 —— 它一停下来（track 变空）就把入口藏掉的话，用户没有别的办法脱身。
             */
            // 「同步」：和上面一次性的投放/接管分开。
            Spacer(Modifier.height(10.dp))
            Text(
                text = "同步",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(2.dp))
            when {
                isFollowing -> ControlRow(
                    icon = Icons.Rounded.Close,
                    title = "停止跟随",
                    subtitle = "本机不再跟着「${device.name}」走",
                    onClick = {
                        container.connectSync.stopFollow()
                        onMessage("已停止跟随「${device.name}」")
                    },
                )

                followsMe -> ControlRow(
                    icon = Icons.Rounded.Close,
                    title = "让它别跟着我",
                    subtitle = "它正在镜像本机的播放",
                    onClick = {
                        scope.launch {
                            val result = container.connectSync.remote(device.deviceId, "unfollow")
                            onMessage(followMessage(device.name, true, result))
                        }
                    },
                )

                // 房间里不新开跟随：跟随与房间互斥（两者都在驱动同一个播放器）。
                // 上两档的「停止」照旧保留——已经跟上的必须留一条脱身的路。
                inRoom -> Text(
                    text = "一起听期间不和其他设备同步——播放由房间统一决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )

                else -> {
                    // 「跟随这台设备」要它手上有内容：镜像一个空的东西没有意义。
                    if (track != null) {
                        ControlRow(
                            icon = Icons.Rounded.Sync,
                            title = "跟随这台设备",
                            subtitle = "本机只当它的镜像：它换歌、暂停，本机跟着走",
                            onClick = {
                                container.connectSync.startFollow(device.deviceId, device.name)
                                onMessage("开始跟随「${device.name}」")
                                // 收起面板：跟随后要看的是播放页。
                                onDismiss()
                            },
                        )
                    }
                    // 「让它跟随我」不受此限：它空着正好，本机一放它就跟上。
                    ControlRow(
                        icon = Icons.Rounded.Sync,
                        title = "让它跟随我",
                        subtitle = "跟不跟由它决定，它随时可以自己停掉",
                        onClick = {
                            scope.launch {
                                val result = container.connectSync.remote(device.deviceId, "follow")
                                onMessage(followMessage(device.name, false, result))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 面板里的一行操作：图标 + 标题 + 一句解释。 */
@Composable
private fun ControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 对方队列里的一行：序号 + 歌名 / 歌手 + 移除。
 *
 * 点整行 = 让那台设备播这一首；移除单独一个按钮，免得误点。
 */
@Composable
private fun RemoteQueueRow(
    index: Int,
    track: TrackSummary,
    current: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { "未知歌曲" },
                style = MaterialTheme.typography.bodyMedium,
                // 正在播的那首用主色标出来，扫一眼就知道它放到哪了。
                color = if (current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artists.isNotBlank()) {
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "从对方队列移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 模式图标；[mode] 为 null（空设备）时退回「顺序」。 */
private fun modeIcon(mode: PlayMode?): ImageVector = when (mode) {
    PlayMode.All -> Icons.Rounded.Repeat
    PlayMode.One -> Icons.Rounded.RepeatOne
    PlayMode.Shuffle -> Icons.Rounded.Shuffle
    else -> Icons.Rounded.PlaylistPlay
}

/**
 * 滑块的草稿值。
 *
 * [committedAt] 为 0 表示手指还按着；松手时填上提交时刻，之后一直留到真实值追上
 * 或者超时（见 [draftSettled]）。
 */
private data class SliderDraft(
    /** 要显示的值（进度为秒，音量为 0..1）。 */
    val value: Float,
    val committedAt: Long = 0L,
    /** 提交时对方在放的那首：换了歌，这条进度草稿就没有意义了。 */
    val trackKey: String = "",
)

/** 松手后最多等这么久：对方执行完会立刻补报状态，正常几十毫秒，这里只是失败时的兜底。 */
private const val SLIDER_ACK_MS = 1_200L
/** 草稿落地检查的间隔。 */
private const val SLIDER_SETTLE_MS = 80L
/** 判定「真实值已追上草稿」的容差：进度 1.5 秒（回执往返的误差），音量半个步进。 */
private const val PROGRESS_TOLERANCE_S = 1.5f
private const val VOLUME_TOLERANCE = 0.006f

/**
 * 松手之后的草稿什么时候可以撤。
 *
 * 只在两种情况下撤：对方的真实值追上了它（说明那条指令已经落地），或者等超时
 * （对方离线、丢包时的兜底）。按着的草稿永远不撤 —— 那一段归 onValueChange 管。
 */
private fun draftSettled(
    draft: SliderDraft,
    real: Float?,
    trackKey: String?,
    wallNow: Long,
    isVolume: Boolean,
): Boolean {
    // 还按着：不撤。此刻设备可能压根没上报过状态（空闲设备一样能调音量），
    // 但手指还在滑块上，显示的就该是手上的值。
    if (draft.committedAt == 0L) return false
    // 对方迟迟没响应：兜底放手，别把一个旧位置永远钉在屏幕上。
    if (wallNow - draft.committedAt > SLIDER_ACK_MS) return true
    // 状态还没回来（或设备下线、手里没东西了）：继续等，最多等到上面那个超时。
    if (real == null) return false
    // 换歌了：旧进度对不上新的时长，撤掉。
    if (!isVolume && draft.trackKey != trackKey.orEmpty()) return true

    val tolerance = if (isVolume) VOLUME_TOLERANCE else PROGRESS_TOLERANCE_S
    return abs(real - draft.value) <= tolerance
}

/** 拖动类指令的 payload；字段名与协议 7.1 一致。 */
private fun seekPayload(positionSeconds: Double): JsonObject =
    buildJsonObject { put("position", positionSeconds) }

private fun volumePayload(volume: Double): JsonObject =
    buildJsonObject { put("volume", volume) }

/** 不等确认的指令发不出去时，只在这两种情况下吭声。 */
private fun sendFailure(name: String, outcome: ConnectOutcome): String = when (outcome) {
    ConnectOutcome.Offline -> "「$name」可能已离线"
    else -> "指令没发出去，检查一下网络"
}

/** 投放的结果。语气留余地：对方执行了但状态上报恰好失败时，也会走到「没响应」。 */
private fun transferMessage(name: String, result: AckResult): String = when (result) {
    AckResult.Delivered -> "已投放到「$name」"
    // 没送出去时 [ConnectSync.transferTo] 已经把本机恢复成原样，这里得说出来。
    AckResult.NoResponse -> "「$name」没有接过去，已恢复本机播放"
    AckResult.Offline -> "「$name」可能已离线"
    AckResult.Failed -> "没发出去，检查一下网络"
}

/** 「让它跟随我」的结果。 */
private fun followMessage(name: String, wasFollowing: Boolean, result: AckResult): String = when (result) {
    AckResult.Delivered ->
        if (wasFollowing) "「$name」已停止跟随本机" else "「$name」开始跟随本机"

    AckResult.NoResponse -> "已发送，「$name」可能没有响应"
    AckResult.Offline -> "「$name」可能已离线"
    AckResult.Failed -> "没发出去，检查一下网络"
}
