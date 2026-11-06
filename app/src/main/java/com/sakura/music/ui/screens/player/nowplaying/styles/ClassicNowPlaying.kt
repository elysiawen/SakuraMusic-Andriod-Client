package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopCover
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopLyricConfig
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopLyricStage
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopRouteTag
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopSeekBlock
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopTransportRow
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingTopBar
import com.sakura.music.ui.screens.player.nowplaying.common.materialDesktopPalette

/**
 * 经典模式（网页端 `ClassicMode`）：模糊封面上铺一层主题色光晕，封面与歌词
 * 左右分栏；窄屏收成单列，封面缩到 200dp 上下，歌词居中。
 *
 * 与网页端一样跟着 App 的明暗主题走，深色时是夜空，浅色时是纸面。
 */
@Composable
fun ClassicNowPlaying(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val palette = materialDesktopPalette()
    val scheme = MaterialTheme.colorScheme
    val track = state.track

    BoxWithConstraints(
        modifier = modifier.background(scheme.background),
    ) {
        val wide = maxWidth >= 900.dp
        val coverSize = minOf(maxHeight * 0.24f, maxWidth * 0.50f, 200.dp)

        // 背景：封面放大 + 高斯模糊，再压两团主题色光晕与一层底色的渐隐。
        if (!track.album.cover.isNullOrBlank()) {
            BlurredBackdrop(
                cover = track.album.cover,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.04f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.accent2.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.96f),
                    radius = size.maxDimension * 0.58f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        scheme.background.copy(alpha = 0.56f),
                        scheme.background.copy(alpha = 0.80f),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (wide) 34.dp else 20.dp),
        ) {
            NowPlayingTopBar(
                state = state,
                actions = actions,
                skin = NowPlayingSkin.Material,
            )

            if (wide) {
                // 外层撑满宽度并居中，内层限宽——避免 fillMaxWidth 吃掉 align 导致不居中。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Aside(
                            state = state,
                            actions = actions,
                            palette = palette,
                            coverSize = coverSize,
                            collapseGesture = collapseGesture,
                            modifier = Modifier
                                .width(coverSize + 18.dp)
                                .fillMaxHeight()
                                .padding(top = 28.dp),
                        )
                        Spacer(Modifier.width(36.dp))
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
                            config = classicLyricConfig(activeSize = 27f, idleSize = 19f, align = TextAlign.Start),
                            onSeek = actions.seek,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .align(Alignment.CenterHorizontally)
                        .then(collapseGesture)
                        .clickable(onClick = actions.collapse),
                ) {
                    DesktopCover(
                        cover = track.album.cover,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ClassicTitle(state = state, palette = palette, centered = true, onToggleFavorite = actions.toggleFavorite)
                Spacer(Modifier.height(8.dp))
                ClassicTags(state = state, palette = palette, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
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
                    config = classicLyricConfig(activeSize = 24f, idleSize = 17.5f, align = TextAlign.Center),
                    onSeek = actions.seek,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // 横屏下进度条与传输控件收窄并居中；桌面端用 720px，换算到安卓 dp 约 432，取 360 更紧凑。
            val controlsModifier = if (wide) {
                Modifier
                    .widthIn(max = 360.dp)
                    .align(Alignment.CenterHorizontally)
            } else {
                Modifier.fillMaxWidth()
            }

            DesktopSeekBlock(
                positionMs = state.shownPositionMs,
                durationMs = state.durationMs,
                palette = palette,
                onScrub = actions.scrub,
                onSeek = actions.seek,
                modifier = controlsModifier,
            )

            Spacer(Modifier.height(6.dp))

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
                modifier = controlsModifier,
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 宽屏左侧栏：封面、标题、标签。 */
@Composable
private fun Aside(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    palette: DesktopPalette,
    coverSize: androidx.compose.ui.unit.Dp,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .then(collapseGesture)
                .clickable(onClick = actions.collapse),
        ) {
            DesktopCover(
                cover = state.track.album.cover,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(22.dp))
        ClassicTitle(state = state, palette = palette, centered = false, onToggleFavorite = actions.toggleFavorite)
        Spacer(Modifier.height(10.dp))
        ClassicTags(state = state, palette = palette)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ClassicTitle(
    state: NowPlayingUiState,
    palette: DesktopPalette,
    centered: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.track.title.ifBlank { "未知歌曲" },
                fontSize = if (centered) 25.sp else 29.sp,
                lineHeight = if (centered) 32.sp else 37.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (centered) {
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (state.isFavorite) "取消收藏" else "收藏",
                    tint = if (state.isFavorite) palette.accent else palette.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.subtitle,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = palette.muted,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 来源、路由、试听、多音源这些「这首歌从哪儿来」的标签。 */
@Composable
private fun ClassicTags(
    state: NowPlayingUiState,
    palette: DesktopPalette,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.sourcePlatform?.let { platform ->
            ClassicTag(text = platform.label, palette = palette)
        }
        DesktopRouteTag(route = state.route, palette = palette)
        if (state.trial) {
            ClassicTag(text = "试听片段", palette = palette, accent = scheme.error)
        }
        if (state.hasMultipleSources && state.trial) {
            ClassicTag(text = "可在「更多」切换音源", palette = palette)
        }
        if (state.inRoom) {
            ClassicTag(text = "一起听中", palette = palette, accent = scheme.primary)
        }
    }
}

@Composable
private fun ClassicTag(text: String, palette: DesktopPalette, accent: Color? = null) {
    Surface(
        shape = CircleShape,
        color = if (palette.isDark) Color.White.copy(alpha = 0.08f) else palette.surfaceStrong,
        border = BorderStroke(
            1.dp,
            accent?.copy(alpha = 0.45f) ?: palette.border,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = accent ?: palette.muted,
            maxLines = 1,
        )
    }
}

/** 经典模式的歌词参数：随宽度换字号，但衰减阶梯/缩放/滚动节奏与网页端一致。 */
private fun classicLyricConfig(
    activeSize: Float,
    idleSize: Float,
    align: TextAlign,
): DesktopLyricConfig = DesktopLyricConfig(
    activeSize = activeSize.sp,
    idleSize = idleSize.sp,
    activeWeight = FontWeight.ExtraBold,
    idleWeight = FontWeight.Medium,
    textAlign = align,
    horizontalPadding = if (align == TextAlign.Start) 8.dp else 20.dp,
    distanceAlpha = listOf(1f, 0.46f, 0.30f, 0.20f, 0.12f),
    activeScale = 1.045f,
    glow = false,
    translationSize = (idleSize * 0.62f).sp,
    translationAlpha = 0.72f,
)

/**
 * 模糊封面底图。
 *
 * Coil 2.x 已经拿掉了 BlurTransformation，这里直接用 Compose 的 blur：
 * Android 12+ 是真高斯模糊，老系统上会退化成清晰底图（文案与渐隐照样压得住）。
 */
@Composable
private fun BlurredBackdrop(
    cover: String,
    palette: DesktopPalette,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = if (palette.isDark) 0.42f else 0.34f,
        modifier = modifier
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .blur(26.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
    )
}
