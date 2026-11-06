package com.sakura.music.ui.screens.player.nowplaying.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.util.formatDuration

/**
 * 网页端三套样式共用的一套颜色。
 *
 * 网页端把这些写在 CSS 变量里（`--accent` / `--text-primary` / `--border-strong`…），
 * Compose 里用一个数据类在样式入口处现算，子组件只认字段名。
 */
data class DesktopPalette(
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val isDark: Boolean,
)

/** 经典模式：跟着 App 的明暗主题走，和网页端一样不锁死深色。 */
@Composable
fun materialDesktopPalette(): DesktopPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return DesktopPalette(
        accent = scheme.primary,
        accent2 = scheme.secondary,
        onAccent = scheme.onPrimary,
        text = scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        border = scheme.outlineVariant,
        surface = scheme.surfaceContainerLow.copy(alpha = if (dark) 0.72f else 0.82f),
        surfaceStrong = scheme.surface.copy(alpha = 0.92f),
        isDark = dark,
    )
}

/** 沉浸模式：固定深色，流体色块用加法混合，浅底会糊成一片白。 */
@Composable
fun immersiveDesktopPalette(): DesktopPalette {
    val scheme = MaterialTheme.colorScheme
    return DesktopPalette(
        accent = scheme.primary,
        accent2 = scheme.secondary,
        onAccent = scheme.onPrimary,
        text = Color(0xFFEDEFF7),
        muted = Color.White.copy(alpha = 0.58f),
        border = Color.White.copy(alpha = 0.12f),
        surface = Color.White.copy(alpha = 0.06f),
        surfaceStrong = Color(0xFF16161F).copy(alpha = 0.82f),
        isDark = true,
    )
}

/** 雾窗：雨夜车窗，同样是固定深色，青色偏冷一点。 */
@Composable
fun nebbiaDesktopPalette(): DesktopPalette {
    val base = immersiveDesktopPalette()
    return base.copy(
        text = Color(0xFFE9EEF4),
        muted = Color(0xFFB7C0CB).copy(alpha = 0.62f),
    )
}

/* ------------------------------ 进度 ------------------------------ */

/** 原生页的进度条：Material 滑块 + 两端时间。 */
@Composable
fun MaterialSeekBlock(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val sliderMax = durationMs.toFloat().coerceAtLeast(1f)
    val shown = if (dragging) dragValue.toLong() else positionMs

    Slider(
        value = shown.toFloat().coerceIn(0f, sliderMax),
        onValueChange = {
            dragging = true
            dragValue = it
            onScrub(it.toLong())
        },
        onValueChangeFinished = {
            onSeek(dragValue.toLong())
            dragging = false
        },
        valueRange = 0f..sliderMax,
        enabled = durationMs > 0,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(shown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 网页端样式的进度条：两段渐变填充、按压缩出一根小圆点。
 *
 * 网页端在 hover 时才把 4px 拉到 6px 并露出 thumb；手机没有 hover，
 * 所以只把「按下 / 拖动」当成同一个态，静息时保持干净的 4px。
 */
@Composable
fun DesktopSeekBlock(
    positionMs: Long,
    durationMs: Long,
    palette: DesktopPalette,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
    activeBarHeight: Dp = 6.dp,
    labelSize: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toLong() else positionMs
    val max = durationMs.coerceAtLeast(1L)

    val height by animateDpAsState(
        targetValue = if (dragging) activeBarHeight else barHeight,
        animationSpec = tween(180),
        label = "seekBarHeight",
    )

    @Composable
    fun Track() {
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { 6.dp.toPx() }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(max) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        fun update(x: Float) {
                            val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragValue = fraction * max
                            onScrub(dragValue.toLong())
                        }
                        update(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position.x)
                            change.consume()
                        }
                        onSeek(dragValue.toLong())
                        onScrub(null)
                        dragging = false
                    }
                },
        ) {
            val barH = height.toPx()
            val top = (size.height - barH) / 2f
            val radius = CornerRadius(barH / 2f)
            drawRoundRect(
                color = palette.border,
                topLeft = Offset(0f, top),
                size = Size(size.width, barH),
                cornerRadius = radius,
            )
            val fraction = (shown.toFloat() / max).coerceIn(0f, 1f)
            val filled = size.width * fraction
            if (filled > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(palette.accent, palette.accent2),
                        startX = 0f,
                        endX = size.width.coerceAtLeast(1f),
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(filled, barH),
                    cornerRadius = radius,
                )
            }
            if (dragging) {
                drawCircle(
                    color = palette.text,
                    radius = thumbRadiusPx,
                    center = Offset(filled.coerceIn(thumbRadiusPx, size.width - thumbRadiusPx), size.height / 2f),
                )
            }
        }
    }

    if (labelSize) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatDuration(shown),
                fontSize = 11.5.sp,
                color = palette.muted,
                fontWeight = FontWeight.Medium,
            )
            Box(Modifier.weight(1f)) { Track() }
            Text(
                text = formatDuration(durationMs),
                fontSize = 11.5.sp,
                color = palette.muted,
                fontWeight = FontWeight.Medium,
            )
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) { Track() }
    }
}

/* ------------------------------ 传输条 ------------------------------ */

/** 原生页控制排：两边各一个功能键，中间三个传输键。 */
@Composable
fun MaterialTransportRow(
    isPlaying: Boolean,
    canSkip: Boolean,
    playMode: PlayMode,
    showBuffering: Boolean,
    onPlayMode: () -> Unit,
    onQueue: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    playButtonSize: Dp = 70.dp,
) {
    val skipSize = playButtonSize * 0.686f
    val iconSize = playButtonSize * 0.486f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPlayMode) {
            Icon(
                imageVector = playMode.icon,
                contentDescription = playMode.label,
                tint = if (playMode == PlayMode.Order) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = onPrevious,
                enabled = canSkip,
                modifier = Modifier.size(skipSize),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(onClick = onToggle, modifier = Modifier.size(playButtonSize)) {
                if (showBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(iconSize),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(
                onClick = onNext,
                enabled = canSkip,
                modifier = Modifier.size(skipSize),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
            }
        }

        IconButton(onClick = onQueue) {
            Icon(
                imageVector = Icons.Rounded.PlaylistPlay,
                contentDescription = "播放队列",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 网页端样式的控制排：`[循环] [上一首] [播放胶囊] [下一首] [播放队列]`。
 *
 * 队列键直接交给宿主的 `openQueue`（底部弹层 QueueSheet），控制排自己不碰队列数据。
 */
@Composable
fun DesktopTransportRow(
    isPlaying: Boolean,
    canSkip: Boolean,
    playMode: PlayMode,
    showBuffering: Boolean,
    palette: DesktopPalette,
    onPlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint = palette.text.copy(alpha = 0.86f)
    val playSize by animateFloatAsState(
        targetValue = if (showBuffering) 0.92f else 1f,
        animationSpec = tween(240),
        label = "playPulse",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            IconButton(onClick = onPlayMode, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = playMode.icon,
                    contentDescription = playMode.label,
                    tint = if (playMode == PlayMode.Order) {
                        iconTint.copy(alpha = 0.55f)
                    } else {
                        palette.accent
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = onPrevious,
                enabled = canSkip,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = iconTint.copy(alpha = if (canSkip) 0.86f else 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }

            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                shadowElevation = 10.dp,
                modifier = Modifier.size(width = 62.dp, height = 42.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(palette.accent, palette.accent2),
                            ),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                    if (showBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp * playSize),
                            strokeWidth = 2.dp,
                            color = palette.onAccent,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = palette.onAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onNext,
                enabled = canSkip,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = iconTint.copy(alpha = if (canSkip) 0.86f else 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }

            // 播放队列：点开宿主的 QueueSheet 底部弹层。
            // 和上一首/下一首对齐到 26dp：PlaylistPlay 本身留白多，21dp 会明显显小。
            IconButton(onClick = onQueue, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistPlay,
                    contentDescription = "播放队列",
                    tint = iconTint,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * 沉浸模式专用的底部玻璃卡：进度、传输控制、可选提示。
 *
 * 经典 / 雾窗自己直接摆 [DesktopTransportRow]，只有沉浸走这张定宽浮块，
 * 所以卡片的水平内缩必须避开控制排，否则宽屏下两端按键会被圆角裁掉。
 * 宿主只把状态和动作传进来，卡片不自己碰播放器。
 */
@Composable
fun DesktopControlCard(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    palette: DesktopPalette,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 18.dp,
        modifier = modifier,
    ) {
        Box {
            // 深色玻璃：比纯色 surfaceStrong 更沉，进度与控制排浮在上层；
            // 窄屏卡片压在频谱上时也不会被柱条透成一片噪声。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xEE1A2030),
                                Color(0xF20B0E16),
                            ),
                        ),
                    ),
            )
            // 玻璃上沿亮边。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind {
                        drawLine(
                            color = Color.White.copy(alpha = 0.20f),
                            start = Offset(24.dp.toPx(), 0.5.dp.toPx()),
                            end = Offset(size.width - 24.dp.toPx(), 0.5.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
            )
            // 水平内缩只加在进度条和提示上：控制排是 5 个固定宽度的按键（共 298dp），
            // 再吃 32dp 内边距就会在窄卡片里居中溢出，两端按键被 Surface 圆角裁掉。
            Column(modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DesktopSeekBlock(
                        positionMs = state.shownPositionMs,
                        durationMs = state.durationMs,
                        palette = palette,
                        onScrub = actions.scrub,
                        onSeek = actions.seek,
                    )
                }

                Spacer(Modifier.height(8.dp))

                DesktopTransportRow(
                    isPlaying = state.isPlaying,
                    canSkip = state.canSkip,
                    playMode = state.playMode,
                    showBuffering = state.showBuffering,
                    palette = palette,
                    onPlayMode = actions.cyclePlayMode,
                    onPrevious = actions.previous,
                    onToggle = actions.toggle,
                    onNext = actions.next,
                    onQueue = actions.openQueue,
                )

                if (hint != null) {
                    Text(
                        text = hint,
                        fontSize = 10.5.sp,
                        color = palette.muted.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}
