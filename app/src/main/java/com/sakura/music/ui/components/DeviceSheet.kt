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
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.connect.DeviceState
import com.sakura.music.data.connect.DeviceView
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatDuration
import kotlinx.coroutines.delay

/**
 * 设备列表：同一账号下的所有设备。
 *
 * 这一层只负责「看」——谁在线、哪台在放什么。要动它就点进去，控制都在
 * [DeviceControlSheet] 里，免得列表本身挂满按钮、每行都挤成一团。
 */
@Composable
fun DeviceSheet(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val container = appContainer()
    val connected by container.connectClient.connected.collectAsStateWithLifecycle()
    val devices by container.connectClient.devices.collectAsStateWithLifecycle()
    val selfId = container.connectClient.deviceId

    var controlTarget by remember { mutableStateOf<String?>(null) }

    // 各设备的进度每秒重算一次：对方在播时它会自己走。
    var now by remember { mutableLongStateOf(container.connectClient.serverTimeNow) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = container.connectClient.serverTimeNow
        }
    }

    SheetScaffold(title = "其它设备", onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            val hint = when {
                !connected -> "还没连上。登录后会自动连接，稍等片刻再打开这里。"
                devices.none { it.deviceId != selfId } ->
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
                    isSelf = device.deviceId == selfId,
                    followsSelf = device.state?.following == selfId,
                    serverNow = now,
                    onClick = { controlTarget = device.deviceId },
                )
            }
        }
    }

    controlTarget?.let { deviceId ->
        DeviceControlSheet(
            deviceId = deviceId,
            onDismiss = { controlTarget = null },
            onMessage = onMessage,
        )
    }
}

/** 一行设备：图标、名字、它在放什么。 */
@Composable
private fun DeviceRow(
    device: DeviceView,
    isSelf: Boolean,
    /** 这台设备正在跟随本机（靠它上报的 `following` 判断）。 */
    followsSelf: Boolean,
    serverNow: Long,
    onClick: () -> Unit,
) {
    val state = device.state
    val track = state?.track

    val subtitle = when {
        state == null || track == null -> "空闲"
        else -> listOf(
            track.artists.takeIf { it.isNotBlank() }.orEmpty(),
            formatDuration((livePositionSeconds(state, serverNow) * 1000).toLong()),
        ).filter { it.isNotBlank() }.joinToString(" · ")
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
                text = when {
                    isSelf -> "${device.name}（本机）"
                    // 「谁跟着谁」得让被跟随的一方能确认：光看都在放同一首歌，
                    // 分不清是跟着走还是各放各的。
                    followsSelf -> "${device.name}（正在跟随本机）"
                    else -> device.name
                },
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
internal fun iconFor(kind: String): ImageVector = when (kind) {
    "android" -> Icons.Rounded.Android
    "windows" -> Icons.Rounded.DesktopWindows
    // web 端是「一个标签页一台设备」，浏览器窗口比地球更贴切。
    else -> Icons.Rounded.Web
}

/**
 * 目标设备**此刻**的进度（秒）。
 *
 * 上报的是「那一刻」的值，播放中要往前推；并且**必须掐上界**——没有周期上报之后，
 * 上一次上报可能已经过去很久，播完那一刻位置会越过曲长，看着像在放一首已经结束的歌。
 *
 * 时间基准用服务端时间（`positionAt` 也是服务端盖的）：两台机器的时钟差多少，
 * 推算就整体偏多少，而且永远不会自愈。
 */
internal fun livePositionSeconds(state: DeviceState, serverNow: Long): Double {
    val advanced = if (state.playing) {
        val elapsed = (serverNow - (state.positionAt ?: serverNow)) / 1000.0
        state.position + elapsed
    } else {
        state.position
    }
    val clamped = if (state.duration > 0) advanced.coerceAtMost(state.duration) else advanced
    return clamped.coerceAtLeast(0.0)
}
