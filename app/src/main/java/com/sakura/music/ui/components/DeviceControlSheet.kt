package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    // 每秒重算一次对方进度。
    var now by remember { mutableLongStateOf(container.connectClient.serverTimeNow) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = container.connectClient.serverTimeNow
        }
    }

    // 拖动期间显示手上的值：否则对方的进度每隔一秒就会把它盖回去。
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var volumeDraft by remember { mutableStateOf<Float?>(null) }

    /** 发一条不等确认的指令；只有发不出去时才吭声（成功看状态变化就够了）。 */
    fun fire(action: String, payload: JsonObject? = null) {
        scope.launch {
            val outcome = container.connectSync.send(device.deviceId, action, payload)
            if (outcome != ConnectOutcome.Delivered) onMessage(sendFailure(device.name, outcome))
        }
    }

    SheetScaffold(title = device.name, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
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

            if (state == null || track == null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "这台设备没有在放东西。可以把本机的播放交给它，或让它跟随本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                /* ------------------------------ 进度 ------------------------------ */
                // 进度只能绑在「它手里有东西」上：没内容就没有进度可言。
                val durationSec = state.duration.toFloat().coerceAtLeast(0f)
                val shown = scrubbing ?: livePositionSeconds(state, now).toFloat()
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = shown.coerceIn(0f, durationSec.coerceAtLeast(0.1f)),
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        // 松手才发指令：一路拖一路发就是请求洪流。
                        scrubbing?.let { fire("seek", seekPayload(it.toDouble())) }
                        scrubbing = null
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
            }

            /*
             * 音量是**设备**的属性，不是内容的：同一份播放状态在各台设备上可以不一样，
             * 所以它跟着设备走，也是跟随时唯一不该同步的东西。
             *
             * 但也正因为它属于设备而不是内容，**空着的设备一样有音量可调** ——
             * 它只是没在放歌，不代表不需要合适的响度。所以这一行不跟着「有内容」藏起来。
             */
            val volume = volumeDraft ?: (state?.volume?.toFloat() ?: 1f)
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
                    onValueChange = { volumeDraft = it },
                    onValueChangeFinished = {
                        volumeDraft?.let { fire("volume", volumePayload(it.toDouble())) }
                        volumeDraft = null
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

            /* ------------------------------ 传输控制 ------------------------------ */
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
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

            /* ------------------------------ 交接 ------------------------------ */
            Spacer(Modifier.height(14.dp))

            if (playback.queue.isNotEmpty()) {
                ControlRow(
                    icon = Icons.Rounded.Cast,
                    title = "投放到这台设备",
                    subtitle = "把本机正在播的队列连进度交给它，本机停下",
                    onClick = {
                        scope.launch {
                            val result = container.connectSync.transferTo(device.deviceId)
                            onMessage(transferMessage(device.name, result))
                        }
                    },
                )
            }

            // 接管需要它手里确实有东西可搬。
            if (track != null) {
                ControlRow(
                    icon = Icons.Rounded.Devices,
                    title = "接管它的播放",
                    subtitle = "把它手里的队列搬到本机来放",
                    onClick = {
                        scope.launch {
                            val result = container.connectSync.requestTakeOver(device.deviceId)
                            onMessage(
                                when (result) {
                                    AckResult.Delivered -> "已请求接管，稍后本机开始播放"
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    AckResult.NoResponse -> "已发送，「$name」可能没有响应"
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
