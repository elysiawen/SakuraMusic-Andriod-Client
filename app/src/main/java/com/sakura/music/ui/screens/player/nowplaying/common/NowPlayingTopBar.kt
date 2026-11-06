package com.sakura.music.ui.screens.player.nowplaying.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.prefs.NowPlayingStyle
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState

/** 每种播放页样式的小图标，顶栏菜单与设置页共用。 */
val NowPlayingStyle.icon: ImageVector
    get() = when (this) {
        NowPlayingStyle.Native -> Icons.Rounded.MusicNote
        NowPlayingStyle.Classic -> Icons.Rounded.Album
        NowPlayingStyle.Immersive -> Icons.Rounded.AutoAwesome
        NowPlayingStyle.Nebbia -> Icons.Rounded.WaterDrop
    }

/**
 * 全屏播放页顶栏。
 *
 * 对应网页端的 `NowPlayingTopActions`：左半边是「收起 + 正在播放」，
 * 右半边收着页面操作。安卓端多出来的跟随 / 电台提示也在这里，
 * 换样式时不必再各自实现一份。
 *
 * 播放队列统一由底部控制排承担，顶栏不再重复放入口。
 *
 * @param skin 原生页用 [NowPlayingSkin.Material]（跟主题色走）；
 *   网页端三套样式用 [NowPlayingSkin.Desktop]（深色玻璃、白字）。
 */
@Composable
fun NowPlayingTopBar(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    skin: NowPlayingSkin,
    modifier: Modifier = Modifier,
) {
    val desktop = skin == NowPlayingSkin.Desktop
    val tint = if (desktop) {
        Color.White.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.collapse) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起",
                tint = if (desktop) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.weight(1f))

        state.followingName?.let { name ->
            TopBarChip(
                text = "跟随 $name",
                icon = Icons.Rounded.Sync,
                desktop = desktop,
                onClick = actions.stopFollow,
            )
            Spacer(Modifier.width(6.dp))
        }

        state.radioLabel?.let { label ->
            TopBarChip(
                text = "$label · 退出",
                icon = Icons.Rounded.MusicNote,
                desktop = desktop,
                onClick = actions.stopRadio,
            )
            Spacer(Modifier.width(6.dp))
        }

        StylePicker(
            current = state.style,
            desktop = desktop,
            onPick = actions.setStyle,
        )

        IconButton(onClick = actions.openTogether) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = "一起听",
                    tint = if (state.inRoom) {
                        if (desktop) Color.White else MaterialTheme.colorScheme.primary
                    } else {
                        tint
                    },
                )
                if (state.inRoom) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp, end = 1.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (desktop) Color.White else MaterialTheme.colorScheme.primary
                            ),
                    )
                }
            }
        }

        IconButton(onClick = actions.openDevices) {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = "播放到其它设备",
                tint = tint,
            )
        }

        IconButton(onClick = actions.openActions) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "更多",
                tint = tint,
            )
        }
    }
}

/** 跟随 / 电台两枚小胶囊：原生页跟主题色，网页端样式压成半透明白。 */
@Composable
private fun TopBarChip(
    text: String,
    icon: ImageVector,
    desktop: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (desktop) {
            Color.White.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        border = if (desktop) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (desktop) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (desktop) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 样式菜单：与网页端顶栏的 `NowPlayingModePicker` 同一位置、同一职责。
 * 选中即写入偏好，页面切换由宿主统一处理。
 */
@Composable
private fun StylePicker(
    current: NowPlayingStyle,
    desktop: Boolean,
    onPick: (NowPlayingStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = current.icon,
                contentDescription = "播放页样式",
                tint = if (desktop) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NowPlayingStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = style.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(style.label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = style.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (style == current) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (style != current) onPick(style)
                    },
                )
            }
        }
    }
}