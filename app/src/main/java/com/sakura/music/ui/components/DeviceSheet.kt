package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.sakura.music.data.connect.DeviceView
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 同一账号下的设备列表与操作（协议见 `connect-protocol.md`）。
 *
 * 这里只发指令，不碰音频：一台手机遥控另一台播放时，本机的网络开销只有每 5 秒几百字节。
 * 点某台设备才会展开动作，「投放到它」和「接管它」是两件不同的事，见下方注释。
 */
@Composable
fun DeviceSheet(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val container = appContainer()
    val connected by container.connectClient.connected.collectAsStateWithLifecycle()
    val devices by container.connectClient.devices.collectAsStateWithLifecycle()
    val playback by container.playbackCenter.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf<DeviceView?>(null) }

    // 进度靠「上报时刻 + 本地流逝」推算：各设备时钟不一致，服务端只给 positionAt。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    SheetScaffold(title = "其它设备", onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            val hint = when {
                !connected -> "还没连上。登录后会自动连接，稍等片刻再打开这里。"
                devices.none { it.deviceId != container.connectClient.deviceId } ->
                    "只有这一台在线。在另一台设备上登录同一账号，它就会出现在这里。"
                else -> null
            }

            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            devices.forEach { device ->
                DeviceRow(
                    device = device,
                    isSelf = device.deviceId == container.connectClient.deviceId,
                    now = now,
                    onClick = { target = device },
                )
            }
        }
    }

    target?.let { device ->
        val isSelf = device.deviceId == container.connectClient.deviceId
        val remotePlaying = device.state?.playing == true
        val remoteHasTrack = device.state?.track != null
        val actions = buildList {
            if (!isSelf && playback.queue.isNotEmpty()) {
                // 场景 B：我这边有队列，交给它播——单程，我这边的队列跟着走，然后自己停下。
                add(
                    SheetAction(
                        label = "投放到这台设备",
                        icon = Icons.Rounded.Cast,
                        onClick = {
                            scope.launch {
                                val result = container.connectSync.transferTo(device.deviceId)
                                onMessage(transferMessage(device.name, result))
                            }
                        },
                    )
                )
            }
            if (!isSelf && remoteHasTrack) {
                // 场景 A：它那边有播放，我要接手——往返，它回传队列之后本机才开始播。
                // 这里不做送达确认：确认要看回程的 transfer 到了没有。
                add(
                    SheetAction(
                        label = "接管它的播放",
                        icon = Icons.Rounded.Devices,
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
                )
            }
            if (!isSelf) {
                add(
                    SheetAction(
                        label = if (remotePlaying) "让它暂停" else "让它继续",
                        icon = if (remotePlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        onClick = {
                            scope.launch {
                                val result = container.connectSync.remote(device.deviceId, "toggle")
                                onMessage(
                                    remoteMessage(
                                        label = if (remotePlaying) "暂停" else "继续",
                                        name = device.name,
                                        result = result,
                                    )
                                )
                            }
                        },
                    )
                )
                add(
                    SheetAction(
                        label = "下一首",
                        icon = Icons.Rounded.SkipNext,
                        onClick = {
                            scope.launch {
                                val result = container.connectSync.remote(device.deviceId, "next")
                                onMessage(remoteMessage("播下一首", device.name, result))
                            }
                        },
                    )
                )
                add(
                    SheetAction(
                        label = "上一首",
                        icon = Icons.Rounded.SkipPrevious,
                        onClick = {
                            scope.launch {
                                val result = container.connectSync.remote(device.deviceId, "prev")
                                onMessage(remoteMessage("播上一首", device.name, result))
                            }
                        },
                    )
                )
            }
        }

        if (actions.isNotEmpty()) {
            ActionsSheet(
                title = device.name,
                actions = actions,
                onDismiss = { target = null },
            )
        }
    }
}

/** 一行设备：图标、名字、它在放什么。 */
@Composable
private fun DeviceRow(
    device: DeviceView,
    isSelf: Boolean,
    now: Long,
    onClick: () -> Unit,
) {
    val state = device.state
    val track = state?.track

    val subtitle = when {
        track == null -> "空闲"
        else -> {
            // 播放中就从上报时刻往前推，暂停时直接用 position。
            // 推算**必须掐上界**：现在没有周期上报了，控制端可能独自推很久，
            // 播完那一刻位置会越过曲长，看起来像「在放一首已经结束的歌」。
            val seconds = if (state.playing) {
                val elapsed = state.position + (now - (state.positionAt ?: now)) / 1000.0
                if (state.duration > 0) elapsed.coerceAtMost(state.duration) else elapsed
            } else {
                state.position
            }
            listOf(
                track.artists.takeIf { it.isNotBlank() }.orEmpty(),
                formatDuration((seconds.coerceAtLeast(0.0) * 1000).toLong()),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标垫一层圆底：三种设备一眼可辨，列表也有了纵向的节奏；
        // 本机的图标染成主色，省得在几台设备里找自己。
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconFor(device.kind),
                    contentDescription = null,
                    tint = if (isSelf) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "${device.name}（本机）" else device.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = if (track == null) subtitle else "${track.title} · $subtitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state?.playing == true) {
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(6.dp).size(14.dp),
                )
            }
        }
    }
}

/**
 * 协议里 kind 只区分 android / windows，其余一律当 web，图标也照这个来。
 *
 * 不用「手机 / 显示器 / 地球」那套通用轮廓：三种设备在列表里是并排出现的，
 * 通用轮廓看久了分不出谁是谁；这里挑的是各自最有辨识度的形象——
 * 安卓机器人、Windows 那扇窗、浏览器窗口。
 */
private fun iconFor(kind: String): ImageVector = when (kind) {
    "android" -> Icons.Rounded.Android
    "windows" -> Icons.Rounded.DesktopWindows
    // web 端是「一个标签页一台设备」，浏览器窗口比地球更贴切。
    else -> Icons.Rounded.Web
}

/** 投放的结果。语气留余地：对方执行了但状态上报恰好失败时，也会走到「没响应」。 */
private fun transferMessage(name: String, result: AckResult): String = when (result) {
    AckResult.Delivered -> "已投放到「$name」"
    AckResult.NoResponse -> "已发送，「$name」可能没有响应"
    AckResult.Offline -> "「$name」可能已离线"
    AckResult.Failed -> "没发出去，检查一下网络"
}

/** 遥控动作的结果。 */
private fun remoteMessage(label: String, name: String, result: AckResult): String = when (result) {
    AckResult.Delivered -> "已让「$name」$label"
    AckResult.NoResponse -> "已发送，「$name」可能没有响应"
    AckResult.Offline -> "「$name」可能已离线"
    AckResult.Failed -> "没发出去，检查一下网络"
}
