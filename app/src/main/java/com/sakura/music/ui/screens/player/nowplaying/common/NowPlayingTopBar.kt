package com.sakura.music.ui.screens.player.nowplaying.common

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        NowPlayingStyle.Lumiere -> Icons.Rounded.Flare
        NowPlayingStyle.Machi -> Icons.Rounded.LocationCity
    }

/** 所有播放页样式共用的顶栏左右留白，切换样式时收起箭头不左右跳。 */
val TopBarHorizontalPadding = 16.dp

private val ActionWidth = 48.dp
private val PortraitChipMax = 140.dp

/**
 * 顶栏无视各样式容器自己的左右留白，始终贴到屏幕上统一的 [TopBarHorizontalPadding]。
 *
 * 外层 Column 常带一整圈 content padding（原生 24dp、经典 20/34dp 等），顶栏作为它的
 * 第一个子项会被一起推进去。这里把测量宽度补回差值、再平移回去：父容器看到的仍是
 * 原本的可用宽度（不会撑宽布局），而顶栏内容按「屏宽 - 2 * 统一留白」排布。
 * 各样式的正文布局因此完全不用改。
 */
private fun Modifier.alignToTopBarEdge(
    outerHorizontalPadding: Dp,
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val shift = (outerHorizontalPadding - TopBarHorizontalPadding).roundToPx()
        val contentWidth = (constraints.maxWidth + shift * 2).coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(minWidth = contentWidth, maxWidth = contentWidth),
        )
        layout(constraints.maxWidth, placeable.height) { placeable.place(-shift, 0) }
    }
}

/* 跟右下角玻璃胶囊同一套色：中性深灰打底，强调色只走 App 主题色。 */
private val GlassSurface = Color(0xFF191B1F)
private val GlassStroke = Color.White.copy(alpha = 0.12f)
private val ChipShape = RoundedCornerShape(16.dp)

/**
 * 全屏播放页顶栏。
 *
 * 对应网页端的 `NowPlayingTopActions`：左半边是「收起 + 正在播放」，
 * 右半边收着页面操作。安卓端多出来的跟随 / 电台提示也在这里，
 * 换样式时不必再各自实现一份。
 *
 * 竖屏把跟随 / 电台合并成一枚紧凑胶囊放在左半区（收起按钮右侧），横屏仍按两枚
 * 完整胶囊留在右半区。胶囊宽度计入预算，剩下的空间不够就把「一起听」「投屏」
 * 依次收进「更多」的溢出菜单，任何屏宽都不会把按钮挤出屏幕。
 *
 * 顶栏位置由本组件统一负责：调用方只需用 [outerHorizontalPadding] 声明外层容器给
 * 正文加的左右留白，不要再用 modifier 给顶栏单独加水平 padding。
 *
 * @param skin 原生页用 [NowPlayingSkin.Material]（跟主题色走）；
 *   网页端三套样式用 [NowPlayingSkin.Desktop]（深色玻璃、白字）。
 */
@Composable
fun NowPlayingTopBar(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    skin: NowPlayingSkin,
    /** 只有带设置面板的网页端效果会传，其它样式不多一个用不上的按钮。 */
    onOpenEffectSettings: (() -> Unit)? = null,
    /** 外层容器（Column 等）已施加的左右留白，用于把顶栏对齐到统一位置。 */
    outerHorizontalPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val desktop = skin == NowPlayingSkin.Desktop
    val tint = if (desktop) {
        Color.White.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val followingName = state.followingName
    val radioLabel = state.radioLabel
    val hasStatus = followingName != null || radioLabel != null

    BoxWithConstraints(
        modifier = modifier
            .alignToTopBarEdge(outerHorizontalPadding)
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        // 收起、样式选择、更多是三个固定按钮，效果设置只有网页端效果才有。
        val fixedWidth = ActionWidth * (3 + if (onOpenEffectSettings != null) 1 else 0)
        // 竖屏只占一枚紧凑胶囊，横屏按实际枚数预留两枚完整胶囊（含 6dp 间距）。
        val chipCount = (if (followingName != null) 1 else 0) + (if (radioLabel != null) 1 else 0)
        val chipReserve = if (portrait) {
            if (hasStatus) PortraitChipMax + 6.dp else 0.dp
        } else {
            (180.dp + 6.dp) * chipCount
        }
        val room = maxWidth - fixedWidth - chipReserve
        val showTogether = room >= ActionWidth
        val showDevices = room >= ActionWidth * 2
        val hasOverflow = !showTogether || !showDevices

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.collapse) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "收起",
                    tint = if (desktop) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface,
                )
            }

            // 竖屏：状态合并成一枚放在左半区，利用原本空着的那片地方；出现/消失不硬闪。
            if (portrait) {
                AnimatedVisibility(
                    visible = hasStatus,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PortraitStatusChip(
                            followingName = followingName,
                            radioLabel = radioLabel,
                            desktop = desktop,
                            onDismiss = if (followingName != null) actions.stopFollow else actions.stopRadio,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 横屏宽度够，两枚完整胶囊按老位置留在右半区，点击整枚即退出。
            if (!portrait) {
                followingName?.let { name ->
                    TopBarChip(
                        text = "跟随 $name",
                        icon = Icons.Rounded.Sync,
                        desktop = desktop,
                        maxWidth = 180.dp,
                        onClick = actions.stopFollow,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                radioLabel?.let { label ->
                    TopBarChip(
                        text = "$label · 退出",
                        icon = Icons.Rounded.MusicNote,
                        desktop = desktop,
                        maxWidth = 180.dp,
                        onClick = actions.stopRadio,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }

            // 效果设置跟样式选择是一对，摆在它左边，不混进右边那排页面操作里。
            onOpenEffectSettings?.let { open ->
                IconButton(onClick = open) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "效果设置",
                        tint = tint,
                    )
                }
            }

            StylePicker(
                current = state.style,
                desktop = desktop,
                onPick = actions.setStyle,
            )

            if (showTogether) {
                TogetherButton(state = state, actions = actions, desktop = desktop, tint = tint)
            }
            if (showDevices) {
                DevicesButton(actions = actions, tint = tint)
            }

            OverflowMoreButton(
                tint = tint,
                hasOverflow = hasOverflow,
                showTogether = showTogether,
                showDevices = showDevices,
                actions = actions,
            )
        }
    }
}

/**
 * 竖屏状态胶囊：跟随和电台合并成一枚，右侧 × 每次退出一个（先跟随再电台），
 * 胶囊会随之刷新，再点一次就退出剩下的那个。
 */
@Composable
private fun PortraitStatusChip(
    followingName: String?,
    radioLabel: String?,
    desktop: Boolean,
    onDismiss: () -> Unit,
) {
    val text = when {
        followingName != null && radioLabel != null -> "跟随 $followingName · 电台"
        followingName != null -> "跟随 $followingName"
        else -> radioLabel.orEmpty()
    }
    val icon = if (followingName != null) Icons.Rounded.Sync else Icons.Rounded.MusicNote
    val foreground = if (desktop) {
        Color.White.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = ChipShape,
        color = if (desktop) {
            GlassSurface.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        border = BorderStroke(
            1.dp,
            if (desktop) GlassStroke else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        modifier = Modifier.widthIn(max = PortraitChipMax),
    ) {
        Row(
            modifier = Modifier.height(30.dp).padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "退出当前状态",
                    tint = foreground.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun TogetherButton(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    desktop: Boolean,
    tint: Color,
) {
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
}

@Composable
private fun DevicesButton(actions: NowPlayingActions, tint: Color) {
    IconButton(onClick = actions.openDevices) {
        Icon(
            imageVector = Icons.Rounded.Cast,
            contentDescription = "播放到其它设备",
            tint = tint,
        )
    }
}

/**
 * 宽度够时「更多」照旧一键打开歌曲操作面板；放不下时先弹溢出菜单，
 * 把被折叠的页面操作列出来，歌曲操作作为最后一项，不额外多占一个按钮。
 */
@Composable
private fun OverflowMoreButton(
    tint: Color,
    hasOverflow: Boolean,
    showTogether: Boolean,
    showDevices: Boolean,
    actions: NowPlayingActions,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (hasOverflow) expanded = true else actions.openActions() }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "更多",
                tint = tint,
            )
        }
        if (hasOverflow) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (!showTogether) {
                    DropdownMenuItem(
                        text = { Text("一起听") },
                        leadingIcon = { Icon(Icons.Rounded.Groups, contentDescription = null) },
                        onClick = {
                            expanded = false
                            actions.openTogether()
                        },
                    )
                }
                if (!showDevices) {
                    DropdownMenuItem(
                        text = { Text("播放到其它设备") },
                        leadingIcon = { Icon(Icons.Rounded.Cast, contentDescription = null) },
                        onClick = {
                            expanded = false
                            actions.openDevices()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("歌曲操作…") },
                    leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.openActions()
                    },
                )
            }
        }
    }
}

/** 横屏的跟随 / 电台胶囊：与竖屏状态胶囊同色系，点击整枚即退出。 */
@Composable
private fun TopBarChip(
    text: String,
    icon: ImageVector,
    desktop: Boolean,
    maxWidth: Dp,
    onClick: () -> Unit,
) {
    val foreground = if (desktop) {
        Color.White.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = ChipShape,
        color = if (desktop) {
            GlassSurface.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        border = BorderStroke(
            1.dp,
            if (desktop) GlassStroke else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        // 限宽之后 TextOverflow.Ellipsis 才真的有约束可用，长用户名不会把顶栏撑爆。
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clickable(onClick = onClick),
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
                tint = foreground,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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
