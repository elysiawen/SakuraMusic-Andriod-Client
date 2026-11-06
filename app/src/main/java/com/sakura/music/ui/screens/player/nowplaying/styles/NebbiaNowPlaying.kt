package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopCover
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopSeekBlock
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopTransportRow
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingTopBar
import com.sakura.music.ui.screens.player.nowplaying.common.nebbiaDesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.rememberCoverPalette
import com.sakura.music.ui.screens.player.nowplaying.nebbia.NebbiaWebStatus
import com.sakura.music.ui.screens.player.nowplaying.nebbia.NebbiaWebView
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 雾窗（网页端 `NebbiaMode`）。
 *
 * 整块玻璃上的夜景、雾、雨和「手写」歌词都由网页端运行时负责，这里只把歌、
 * 主题、播放进度推进去，再用 Compose 补上顶栏与右下角的玻璃浮块：
 * 浮块平时收成「封面 + 歌名 + 播放键」，点一下才展开进度与按键 —— 对应桌面端
 * 悬停展开的手机版。
 */
@Composable
fun NebbiaNowPlaying(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val palette = nebbiaDesktopPalette()
    val cover = rememberCoverPalette(state.track.album.cover)
    val status = remember { NebbiaWebStatus() }
    var bootVisible by remember { mutableStateOf(true) }

    // 首帧一到先等画布自己的淡入走完，再撤加载层（和网页端 boot 过渡同一节奏）。
    LaunchedEffect(status.firstFrame, status.failed) {
        if (status.firstFrame || status.failed) {
            delay(320)
            bootVisible = false
        }
    }

    val canvasAlpha by animateFloatAsState(
        targetValue = if (status.firstFrame) 1f else 0f,
        animationSpec = tween(900, delayMillis = 100),
        label = "nebbiaCanvasAlpha",
    )
    val canvasScale by animateFloatAsState(
        targetValue = if (status.firstFrame) 1f else 1.03f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing, delayMillis = 100),
        label = "nebbiaCanvasScale",
    )

    Box(
        modifier = modifier.background(Color(0xFF05070A)),
    ) {
        NebbiaWebView(
            state = state,
            palette = palette,
            cover = cover,
            status = status,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = canvasAlpha
                    scaleX = canvasScale
                    scaleY = canvasScale
                },
        )

        // WebGL2 不可用：退回「只显示当前这句」，行为对齐网页端 nebbia-fallback。
        if (status.failed) {
            FallbackLine(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp)
                    .padding(top = 160.dp, bottom = 220.dp),
            )
        }

        // 手势板：压在 WebView 上，空白处不再把触摸漏给画布。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .then(collapseGesture),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NowPlayingTopBar(
                state = state,
                actions = actions,
                skin = NowPlayingSkin.Desktop,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.weight(1f))
        }

        NebbiaChip(
            state = state,
            actions = actions,
            palette = palette,
            cover = state.track.album.cover,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
        )

        if (bootVisible && !status.firstFrame) {
            BootFog(Modifier.fillMaxSize())
        }
    }
}

/* ------------------------------ 冷启动 ------------------------------ */

/** 首帧前的黑画布：一滴呼吸的水 + 两圈扩散涟漪（网页端 nebbia-boot 的 Compose 版）。 */
@Composable
private fun BootFog(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nebbiaBoot")
    val breathe by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "bootBreathe",
    )
    val ripple by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)),
        label = "bootRipple",
    )

    Box(
        modifier = modifier
            .background(Color(0xFF05070A))
            .alpha(breathe),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 两圈涟漪：画在图标外，第二圈靠动画相位错开。
            androidx.compose.foundation.Canvas(Modifier.size(96.dp)) {
                val ring = Color.White.copy(alpha = (1f - (ripple - 0.55f)) * 0.32f)
                drawCircle(color = ring, radius = size.minDimension / 2f * ripple, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = size.minDimension / 2f * 0.55f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            }
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = "雾气凝结中",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.65f),
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 118.dp),
        )
    }
}

/* ------------------------------ 兜底 ------------------------------ */

@Composable
private fun FallbackLine(state: NowPlayingUiState, modifier: Modifier = Modifier) {
    val line = state.lyrics.getOrNull(state.lyricIndex)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = line?.text.orEmpty(),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
        )
        val trans = line?.trans
        if (!trans.isNullOrBlank()) {
            Text(
                text = trans,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.58f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Text(
            text = "雾窗渲染不可用",
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.34f),
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/* ------------------------------ 右下角玻璃浮块 ------------------------------ */

private val ChipCollapsedWidth = 252.dp
private val ChipExpandedWidth = 336.dp
private val ChipShape = RoundedCornerShape(24.dp)

/**
 * 雨夜车窗上的一枚玻璃胶囊：收起时是「封面 + 歌名 + 播放键」，点一下展开进度与控制排。
 *
 * 展开和收起只由一条 progress 曲线驱动 —— 宽度、展开区高度、箭头旋转、内容淡入与
 * 位移都在 layout / draw 阶段读同一个值，动画期间不会反复重组，也不会再出现
 * `animateContentSize` 与 `AnimatedVisibility` 各量一次尺寸时的抖动。
 */
@Composable
private fun NebbiaChip(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    palette: DesktopPalette,
    cover: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 380 else 290,
            easing = FastOutSlowInEasing,
        ),
        label = "nebbiaChipProgress",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "nebbiaChipPress",
    )

    // 展开区的自然高度只测一次，之后高度 = 自然高度 × progress。
    var bodyHeightPx by remember { mutableIntStateOf(0) }

    val playedFraction = if (state.durationMs > 0L) {
        (state.shownPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 展开走完就把快捷播放键移出组合树：既让位给下方传输排，也避免一个透明按钮
    // 继续压在箭头的点击区域上。derivedStateOf 只在布尔值翻转时重组，不跟每帧动画。
    val quickPlayVisible by remember {
        derivedStateOf { progress < 0.999f }
    }

    // 深色雾面玻璃：WebView 是独立图层，Compose 拿不到它下面的像素做 backdrop-blur，
    // 所以用带冷光的深色渐变叠出「凝结了水雾的玻璃」，白字在上面才立得住。
    Surface(
        shape = ChipShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        shadowElevation = 18.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .layout { measurable, constraints ->
                val widthPx = (ChipCollapsedWidth + (ChipExpandedWidth - ChipCollapsedWidth) * progress)
                    .toPx().roundToInt().coerceAtMost(constraints.maxWidth)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = widthPx, maxWidth = widthPx),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ChipShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xE3253142),
                                Color(0xE616202C),
                                Color(0xF00E151E),
                            ),
                        ),
                    ),
            )
            // 一层很淡的冷光，玻璃不至于是一块死板的纯色。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ChipShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 420f,
                        ),
                    ),
            )
            // 玻璃上沿凝结的亮边，两端留白。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ChipShape)
                    .drawBehind {
                        drawLine(
                            color = Color.White.copy(alpha = 0.26f),
                            start = Offset(28.dp.toPx(), 0.5.dp.toPx()),
                            end = Offset(size.width - 28.dp.toPx(), 0.5.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
            )

            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(11.dp),
                            ),
                    ) {
                        DesktopCover(
                            cover = cover,
                            corner = 11,
                            shadow = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.track.title.ifBlank { "未知歌曲" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = state.track.artistText.ifBlank { "未知歌手" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text.copy(alpha = 0.58f),
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 收起态的快捷播放键：展开时淡出并收掉占位，让位给下方传输排。
                    if (quickPlayVisible) {
                        Box(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val widthPx = (40.dp.toPx() * (1f - progress)).roundToInt()
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = 0,
                                            maxWidth = Constraints.Infinity,
                                        ),
                                    )
                                    layout(widthPx, placeable.height) { placeable.place(0, 0) }
                                }
                                .graphicsLayer {
                                    clip = true
                                    alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                                },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = actions.toggle,
                                    enabled = !expanded,
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(palette.accent, palette.accent2),
                                                ),
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (state.showBuffering) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 1.6.dp,
                                                color = palette.onAccent,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (state.isPlaying) {
                                                    Icons.Rounded.Pause
                                                } else {
                                                    Icons.Rounded.PlayArrow
                                                },
                                                contentDescription = if (state.isPlaying) "暂停" else "播放",
                                                tint = palette.onAccent,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = palette.text.copy(alpha = 0.72f),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = 180f * progress },
                    )
                }

                // 展开区：跟着 progress 长出来，进度条先到、控制排稍后落位。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val heightPx = (bodyHeightPx * progress).roundToInt()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = 0,
                                    maxHeight = Constraints.Infinity,
                                ),
                            )
                            layout(placeable.width, heightPx) { placeable.place(0, 0) }
                        }
                        // 只在过渡时裁剪展开内容，展开完成后尺寸正好，不必再裁。
                        .graphicsLayer { clip = progress < 0.999f },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .onSizeChanged { bodyHeightPx = it.height }
                            .graphicsLayer {
                                alpha = ((progress - 0.06f) / 0.60f).coerceIn(0f, 1f)
                                translationY = (1f - progress) * 10.dp.toPx()
                            },
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.09f)),
                        )
                        Spacer(Modifier.height(10.dp))
                        DesktopSeekBlock(
                            positionMs = state.shownPositionMs,
                            durationMs = state.durationMs,
                            palette = palette,
                            onScrub = actions.scrub,
                            onSeek = actions.seek,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = ((progress - 0.28f) / 0.55f).coerceIn(0f, 1f)
                            },
                        ) {
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
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            // 收起态沿底边留一线播放进度，展开后让位给完整进度条。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .graphicsLayer { alpha = 1f - progress }
                    .background(Color.White.copy(alpha = 0.07f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playedFraction)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.accent, palette.accent2),
                            ),
                        ),
                )
            }
        }
    }
}
