package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.core.player.AudioSessionBridge
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.AudioSpectrum
import com.sakura.music.ui.screens.player.nowplaying.common.CoverPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopControlCard
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopCover
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopLyricConfig
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopLyricStage
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopRouteTag
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopTag
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingTopBar
import com.sakura.music.ui.screens.player.nowplaying.common.immersiveDesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.rememberAudioSpectrum
import com.sakura.music.ui.screens.player.nowplaying.common.rememberCoverPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 单侧频谱柱数，左右镜像展开（与网页端 SPECTRUM_BARS 一致）。 */
private const val SPECTRUM_BARS = 56

/**
 * 沉浸模式（网页端 `ImmersiveMode`）：封面取色后的色块在暗底上漂移呼吸，
 * 底部是左右镜像的频谱，中间留一整块给大字歌词。
 *
 * 频谱优先用 Visualizer 的真实 FFT：播放服务把 audioSessionId 发布出来，界面按会话
 * 采集并做对数分频，得到 56 根跟着音乐走的柱。没录音权限、会话失效或设备不支持时
 * 自动回退模拟节拍。背景色块的呼吸始终走模拟节拍（按 Δt 推进，60/120Hz 观感一致），
 * 暂停后缓缓停住。
 */
@Composable
fun ImmersiveNowPlaying(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val palette = immersiveDesktopPalette().let { base ->
        // 浅色主题下 M3 primary 在深色画布上偏暗，补光后再给辉光/进度条/标签用。
        base.copy(
            accent = base.accent.brightOnDark(),
            accent2 = base.accent2.brightOnDark(),
        )
    }
    val coverPalette = rememberCoverPalette(state.track.album.cover)
    val audioSessionId by AudioSessionBridge.sessionId.collectAsStateWithLifecycle()
    val spectrum = rememberAudioSpectrum(sessionId = audioSessionId, barCount = SPECTRUM_BARS)
    val beat = rememberImmersiveBeat(state.isPlaying && !state.isBuffering)

    BoxWithConstraints(
        modifier = modifier.background(BG_BASE),
    ) {
        val wide = maxWidth >= 720.dp
        val spectrumHeight = if (wide) 190.dp else minOf(190.dp, maxHeight * 0.24f)

        FluidBackdrop(
            palette = palette,
            cover = coverPalette,
            beat = beat,
            modifier = Modifier.fillMaxSize(),
        )
        Scrim(Modifier.fillMaxSize())
        Spectrum(
            palette = palette,
            cover = coverPalette,
            beat = beat,
            // 暂停 / 缓冲时交回模拟值，让它平滑收住，柱子不会卡在暂停前那一帧。
            spectrum = spectrum?.takeIf { state.isPlaying && !state.isBuffering },
            modifier = Modifier
                .fillMaxWidth()
                .height(spectrumHeight)
                .align(Alignment.BottomCenter),
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
                modifier = Modifier.padding(horizontal = if (wide) 18.dp else 8.dp),
            )

            MetaRow(
                state = state,
                palette = palette,
                wide = wide,
                collapseGesture = collapseGesture,
                onCollapse = actions.collapse,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 2.dp),
            )

            DesktopLyricStage(
                lyrics = state.lyrics,
                currentIndex = state.lyricIndex,
                synced = state.lyricSynced,
                loading = state.lyricLoading,
                positionMs = state.shownPositionMs,
                isPlaying = state.isPlaying && !state.isBuffering,
                wordByWord = state.lyricWordByWord,
                scrubbing = state.scrubbing,
                palette = palette,
                config = immersiveLyricConfig(
                    activeSize = if (wide) 46f else 30f,
                    palette = palette,
                ),
                onSeek = actions.seek,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            // 控制排是 5 个固定宽度的键（46+44+62+44+46）加 4 个 14dp 间距 = 298dp。
            // 宽屏浮块原来给 320dp，扣掉右侧 22dp 只剩 298dp，卡片内边距再吃掉 32dp，
            // Row 居中溢出、最右侧播放队列键会被 Surface 圆角裁掉 —— 只有沉浸走这张卡。
            DesktopControlCard(
                state = state,
                actions = actions,
                palette = palette,
                modifier = if (wide) {
                    Modifier
                        .align(Alignment.End)
                        .width(360.dp)
                        .padding(end = 22.dp, bottom = 22.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                },
            )
        }
    }
}

/* ------------------------------ 节拍时钟 ------------------------------ */

/**
 * 模拟节拍：驱动背景色块呼吸，也是频谱拿不到真实数据时的回退柱值，
 * 和网页端的 bassSmooth / playLevel 同源。
 */
private class ImmersiveBeat {
    var clock by mutableFloatStateOf(0f)
    var bass by mutableFloatStateOf(0.06f)
    var level by mutableFloatStateOf(0f)
    /** 回退用的每根柱值，避免把 sin 抖动直接写进柱高（对齐网页端 bars[]）。 */
    val bars = FloatArray(SPECTRUM_BARS)
}

@Composable
private fun rememberImmersiveBeat(isPlaying: Boolean): ImmersiveBeat {
    val beat = remember { ImmersiveBeat() }

    // 用 Δt 的指数平滑代替「每帧 ×0.08 / ×0.25」：60Hz 与 120Hz 上呼吸节奏一致。
    // clock 改成自己累加，暂停后不再推进；收尾到静止就退出循环，两张 Canvas 随之停帧。
    LaunchedEffect(isPlaying) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrame == 0L) {
                    0f
                } else {
                    ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                lastFrame = now

                val target = if (isPlaying) 1f else 0f
                beat.level += (target - beat.level) * (1f - exp(-dt * 5f))
                if (isPlaying || beat.level > 0.002f) {
                    beat.clock += dt
                }

                val pulse = sin(beat.clock * 2.4f).coerceAtLeast(0f)
                val raw = (pulse * pulse * 0.72f + 0.06f) * beat.level + 0.06f
                beat.bass += (raw - beat.bass) * (1f - exp(-dt * 18f))

                val barK = 1f - exp(-dt * 22f)
                for (i in 0 until SPECTRUM_BARS) {
                    val wobble = 0.6f + 0.4f * sin(beat.clock * 1.7f + i * 0.55f)
                    val value = max(
                        0f,
                        (0.62f - i.toFloat() / SPECTRUM_BARS * 0.5f) *
                            (0.25f + beat.bass) * wobble * beat.level,
                    )
                    beat.bars[i] += (value - beat.bars[i]) * barK
                }
            }
            if (!isPlaying &&
                beat.level < 0.002f &&
                abs(beat.bass - 0.06f) < 0.002f &&
                beat.bars.all { it < 0.002f }
            ) {
                break
            }
        }
    }
    return beat
}

/* ------------------------------ 背景层 ------------------------------ */

private val BG_BASE = Color(0xFF0A0A12)

/**
 * 深色画布上的强调色补光：浅色主题下 M3 的 primary 偏暗，压在 #0A0A12 上
 * 歌词辉光、进度条、标签会一起糊掉；越暗提得越多，本来就亮的原样保留。
 */
private fun Color.brightOnDark(): Color {
    val lift = (0.52f - luminance()).coerceAtLeast(0f) * 0.95f
    return Color(
        red = red + (1f - red) * lift,
        green = green + (1f - green) * lift,
        blue = blue + (1f - blue) * lift,
        alpha = alpha,
    )
}

/** 色块锚点与漂移参数，与网页端 BLOB_PRESETS 一一对应。 */
private class BlobPreset(
    val ax: Float,
    val ay: Float,
    val dx: Float,
    val dy: Float,
    val sx: Float,
    val sy: Float,
    val phase: Float,
    val size: Float,
    val colorIndex: Int,
)

private val BLOB_PRESETS = listOf(
    BlobPreset(0.2f, 0.28f, 0.10f, 0.15f, 0.11f, 0.09f, 0f, 0.46f, 0),
    BlobPreset(0.8f, 0.22f, 0.12f, 0.10f, 0.08f, 0.12f, 2.1f, 0.38f, 1),
    BlobPreset(0.52f, 0.88f, 0.15f, 0.08f, 0.10f, 0.07f, 4.2f, 0.52f, 2),
    BlobPreset(0.9f, 0.74f, 0.09f, 0.13f, 0.13f, 0.10f, 1.2f, 0.34f, 3),
    BlobPreset(0.1f, 0.82f, 0.08f, 0.12f, 0.07f, 0.11f, 5.3f, 0.36f, 1),
)

private fun blobColors(cover: CoverPalette?, palette: DesktopPalette): List<Color> =
    if (cover == null) {
        listOf(palette.accent, palette.accent2, palette.accent, palette.accent2)
    } else {
        listOf(cover.primary, cover.secondary, palette.accent, palette.accent2)
    }

@Composable
private fun FluidBackdrop(
    palette: DesktopPalette,
    cover: CoverPalette?,
    beat: ImmersiveBeat,
    modifier: Modifier = Modifier,
) {
    val colors = blobColors(cover, palette)

    Canvas(modifier) {
        drawRect(BG_BASE)
        BLOB_PRESETS.forEach { blob ->
            val color = colors[blob.colorIndex % colors.size]
            val clock = beat.clock
            val x = (blob.ax + sin(clock * blob.sx + blob.phase) * blob.dx) * size.width
            val y = (blob.ay + cos(clock * blob.sy + blob.phase * 1.7f) * blob.dy) * size.height
            // 按较长边缩放：竖屏手机上色块才铺得开，不会剩几团孤立亮斑。
            val radius = (blob.size * (1f + beat.bass * 0.55f) * size.maxDimension * 0.38f)
                .coerceAtLeast(1f)
            // 峰值压低、渐变分四段：Plus 叠加时不再糊成一片死白，边缘更像雾团。
            val alpha = 0.30f + beat.bass * 0.20f
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to color.copy(alpha = alpha * 0.55f),
                    0.22f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.42f),
                    1.0f to Color.Transparent,
                    center = Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** 压暗与 vignette：保证大字歌词在任何封面配色下都可读。 */
@Composable
private fun Scrim(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
                center = Offset(size.width / 2f, size.height * 0.42f),
                radius = size.maxDimension * 0.75f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.40f),
                0.16f to Color.Transparent,
                0.70f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.58f),
            ),
        )
    }
}

/* ------------------------------ 频谱 ------------------------------ */

@Composable
private fun Spectrum(
    palette: DesktopPalette,
    cover: CoverPalette?,
    beat: ImmersiveBeat,
    spectrum: AudioSpectrum?,
    modifier: Modifier = Modifier,
) {
    val colors = blobColors(cover, palette)

    Canvas(
        modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = 0.85f
        },
    ) {
        // 必须读在 draw 里：beat.clock 每帧推进会触发重绘，真实数据才跟着刷新。
        val live = spectrum?.snapshot()
        // 渐变随节拍缓慢横向漂移：既让色带不死板，也建立逐帧重绘的依赖。
        val drift = sin(beat.clock * 0.5f) * size.width * 0.12f
        val brush = Brush.horizontalGradient(
            colors = listOf(
                colors[0].copy(alpha = 0.85f),
                colors[1].copy(alpha = 0.95f),
                colors[0].copy(alpha = 0.85f),
            ),
            startX = -size.width * 0.1f + drift,
            endX = size.width * 1.1f + drift,
        )
        val total = SPECTRUM_BARS * 2
        val barWidth = size.width / total
        val centerX = size.width / 2f
        val maxH = size.height * 0.92f
        val cap = min(barWidth * 0.72f, 3.dp.toPx())
        for (i in 0 until SPECTRUM_BARS) {
            // 有真实 FFT 就用它；没权限 / 会话失效时回退到已按 Δt 平滑过的模拟值。
            val value = if (live != null && i < live.size) live[i] else beat.bars[i]
            if (value < 0.01f) continue
            val barH = max(cap * 2f, value * maxH)
            val y = size.height - barH
            val corner = min(cap, barH / 2f)
            for (x in floatArrayOf(centerX + i * barWidth, centerX - (i + 1) * barWidth)) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x + barWidth * 0.14f, y),
                    size = Size(barWidth * 0.72f, barH),
                    cornerRadius = CornerRadius(corner),
                )
            }
        }
        // 顶部渐隐：频谱从底部往上淡出，不抢歌词的视线。
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0f),
                0.55f to Color.Black.copy(alpha = 1f),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/* ------------------------------ 歌曲信息行 ------------------------------ */

@Composable
private fun MetaRow(
    state: NowPlayingUiState,
    palette: DesktopPalette,
    wide: Boolean,
    collapseGesture: Modifier,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color.Black.copy(alpha = 0.55f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                )
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .then(collapseGesture)
                .clickable(onClick = onCollapse),
        ) {
            DesktopCover(
                cover = state.track.album.cover,
                corner = 14,
                shadow = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 10.dp),
        ) {
            Text(
                text = state.track.title.ifBlank { "未知歌曲" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text.copy(alpha = 0.62f),
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!wide) {
                MetaTags(
                    state = state,
                    palette = palette,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        if (wide) {
            MetaTags(state = state, palette = palette)
        }
    }
}

@Composable
private fun MetaTags(
    state: NowPlayingUiState,
    palette: DesktopPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.sourcePlatform?.let { platform ->
            DesktopTag(text = platform.label, palette = palette)
        }
        DesktopRouteTag(route = state.route, palette = palette)
        if (state.trial) {
            DesktopTag(text = "试听", palette = palette, accent = Color(0xFFFF6B81))
        }
        if (state.inRoom) {
            DesktopTag(text = "一起听", palette = palette, accent = palette.accent)
        }
    }
}

/* ------------------------------ 控制卡 ------------------------------ */

/**
 * 沉浸模式的歌词参数：大字、远近递减。
 *
 * 当前行固定用白字，只让辉光带主题色 —— 和网页端 `.lyric-line.is-active` 一致，
 * 也避免浅色主题下深色 primary 直接压在 #0A0A12 上读不出来。
 */
private fun immersiveLyricConfig(
    activeSize: Float,
    palette: DesktopPalette,
): DesktopLyricConfig = DesktopLyricConfig(
    activeSize = activeSize.sp,
    idleSize = (activeSize * 0.62f).sp,
    activeWeight = FontWeight.ExtraBold,
    idleWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    horizontalPadding = 24.dp,
    distanceAlpha = listOf(1f, 0.46f, 0.28f, 0.16f),
    activeScale = 1f,
    glow = true,
    translationSize = (activeSize * 0.5f).sp,
    translationAlpha = 0.86f,
    anchorFraction = 0.38f,
    maskStops = listOf(
        0.0f to 0.0f,
        0.04f to 0.35f,
        0.14f to 1.0f,
        0.80f to 1.0f,
        0.92f to 0.35f,
        1.0f to 0.0f,
    ),
    activeColor = palette.text,
)
