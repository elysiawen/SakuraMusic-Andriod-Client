package com.sakura.music.ui.screens.player.nowplaying.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.music.core.player.LrcParser
import com.sakura.music.core.player.LyricLine
import com.sakura.music.core.player.LyricWord
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 原生页歌词行高：固定值让居中滚动可以直接算，不用两阶段测量。 */
internal val NativeLyricLineHeight = 34.sp
internal val NativeLyricVPadding = 10.dp
internal val LyricTransition = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * 原生页的歌词卡片：自动把当前行滚到中间；用户自己拖动时暂停跟随并给一个「回到当前」，
 * 停手 3 秒后恢复。
 */
@Composable
fun MaterialLyricBox(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    synced: Boolean,
    loading: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    wordByWord: Boolean,
    scrubbing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current

    val livePosition = rememberLyricPositionMs(
        positionMs = positionMs,
        active = isPlaying && wordByWord && !scrubbing,
    )
    val effectiveIndex by remember(lyrics, synced, currentIndex, wordByWord) {
        derivedStateOf {
            if (synced && wordByWord) {
                LrcParser.activeIndex(lyrics, livePosition.value)
            } else {
                currentIndex
            }
        }
    }

    var userScrolling by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && !autoScrolling) userScrolling = true
            }
    }

    LaunchedEffect(effectiveIndex, viewportPx, userScrolling) {
        if (effectiveIndex >= 0 && viewportPx > 0 && !userScrolling) {
            autoScrolling = true
            val itemHeightPx = with(density) {
                (NativeLyricLineHeight.toPx() + NativeLyricVPadding.toPx() * 2).roundToInt()
            }
            listState.animateScrollToItem(effectiveIndex, itemHeightPx / 2)
            autoScrolling = false
        }
    }

    LaunchedEffect(userScrolling) {
        if (userScrolling) {
            delay(3000)
            userScrolling = false
        }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { viewportPx = it.height },
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在读取歌词…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyrics.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "未找到歌词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val halfViewport = with(density) { (viewportPx / 2).toDp() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = halfViewport),
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isActive = synced && index == effectiveIndex && line.words.isNotEmpty()
                        MaterialLyricLine(
                            text = line.text,
                            translation = line.trans,
                            timeMs = line.timeMs,
                            distance = if (synced) abs(index - effectiveIndex) else 0,
                            synced = synced,
                            words = line.words,
                            livePosition = if (isActive) livePosition else null,
                            onClick = { if (line.timeMs > 0L) onSeek(line.timeMs) },
                        )
                    }
                }

                LyricFade(MaterialTheme.colorScheme.surfaceContainerLow)

                if (userScrolling && effectiveIndex >= 0) {
                    FilledTonalButton(
                        onClick = { userScrolling = false },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("回到当前", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** 一行原生歌词：离当前行越远越小越淡。 */
@Composable
private fun MaterialLyricLine(
    text: String,
    translation: String?,
    timeMs: Long,
    distance: Int,
    synced: Boolean,
    words: List<LyricWord>,
    livePosition: State<Long>?,
    onClick: () -> Unit,
) {
    val isCurrent = distance == 0
    val wordProgress = if (isCurrent && words.isNotEmpty()) {
        if (livePosition != null) {
            LrcParser.wordProgress(words, livePosition.value)
        } else {
            FloatArray(words.size) { 0f }
        }
    } else {
        null
    }

    val targetSize = if (!synced) 16f else 16f + 6f / (1f + distance * 1.5f)
    val targetAlpha = if (!synced) 1f else max(0.2f, 1f / (1f + distance * 0.8f))

    val size by animateFloatAsState(targetSize, LyricTransition, label = "lyricSize")
    val alpha by animateFloatAsState(targetAlpha, LyricTransition, label = "lyricAlpha")
    val colour by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "lyricColour",
    )

    val textBrush: Brush? = if (isCurrent) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
            ),
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.045f else 1f,
        animationSpec = LyricTransition,
        label = "lyricScale",
    )

    val idleColour = colour.copy(alpha = 0.42f)
    val hasWordProgress = words.isNotEmpty() && wordProgress != null
    val lyricText = if (hasWordProgress) {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val progress = wordProgress!!.getOrElse(index) { 0f }
                val start = length
                append(word.text)
                addStyle(
                    SpanStyle(color = lerp(idleColour, colour, progress)),
                    start,
                    length,
                )
            }
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = synced && timeMs > 0L, onClick = onClick)
            .padding(vertical = NativeLyricVPadding, horizontal = 16.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isCurrent && hasWordProgress) {
            WordByWordText(
                text = text,
                words = words,
                wordProgress = wordProgress!!,
                fontSize = size.sp,
                lineHeight = NativeLyricLineHeight,
                fontWeight = FontWeight.Bold,
                activeColor = colour,
                idleColor = idleColour,
            )
        } else if (lyricText != null) {
            Text(
                text = lyricText,
                fontSize = size.sp,
                lineHeight = NativeLyricLineHeight,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = colour,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        } else {
            Text(
                text = text,
                fontSize = size.sp,
                lineHeight = NativeLyricLineHeight,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = colour,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    brush = textBrush,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
        translation?.takeIf { it.isNotBlank() }?.let { translated ->
            Text(
                text = translated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 网页端三套样式共用的歌词舞台。
 *
 * 经典与沉浸只差字号、对齐、距离衰减和是否发光，滚动/遮罩/拖动暂停/逐字擦除
 * 全部复用这一份——和网页端 `.lyrics-stage` 在两套 .vue 里重复出现不同，
 * 安卓端把它收成了一张配置表。
 */
data class DesktopLyricConfig(
    val activeSize: TextUnit,
    val idleSize: TextUnit,
    val activeWeight: FontWeight,
    val idleWeight: FontWeight,
    val textAlign: TextAlign,
    val horizontalPadding: Dp,
    /** 距离 0/1/2/3/4+ 的透明度；小于 5 项时取最后一项。 */
    val distanceAlpha: List<Float>,
    val activeScale: Float,
    val glow: Boolean,
    val translationSize: TextUnit,
    val translationAlpha: Float,
    /** 当前行落在视口高度的哪个位置（网页端 0.38）。 */
    val anchorFraction: Float = 0.38f,
    /** 上下遮罩：fraction 与 alpha 成对。 */
    val maskStops: List<Pair<Float, Float>> = listOf(
        0.0f to 0.0f,
        0.07f to 1.0f,
        0.17f to 1.0f,
        0.83f to 1.0f,
        0.93f to 0.0f,
        1.0f to 0.0f,
    ),
    /** 当前行文字色；null 时沿用 palette.accent（经典样式），沉浸用白字 + 主题色辉光。 */
    val activeColor: Color? = null,
) {
    fun alphaFor(distance: Int): Float =
        distanceAlpha.getOrElse(distance) { distanceAlpha.lastOrNull() ?: 1f }
}

@Composable
fun DesktopLyricStage(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    synced: Boolean,
    loading: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    wordByWord: Boolean,
    scrubbing: Boolean,
    palette: DesktopPalette,
    config: DesktopLyricConfig,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current
    val livePosition = rememberLyricPositionMs(
        positionMs = positionMs,
        active = isPlaying && !scrubbing,
    )
    val effectiveIndex by remember(lyrics, synced, currentIndex, wordByWord) {
        derivedStateOf {
            if (synced && wordByWord) {
                LrcParser.activeIndex(lyrics, livePosition.value)
            } else {
                currentIndex
            }
        }
    }

    var userScrolling by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && !autoScrolling) userScrolling = true
            }
    }
    LaunchedEffect(effectiveIndex, viewportPx, userScrolling) {
        if (effectiveIndex >= 0 && viewportPx > 0 && !userScrolling) {
            autoScrolling = true
            listState.animateScrollToItem(effectiveIndex)
            autoScrolling = false
        }
    }
    LaunchedEffect(userScrolling) {
        if (userScrolling) {
            delay(4000)
            userScrolling = false
        }
    }

    Box(
        modifier = modifier.onSizeChanged { viewportPx = it.height },
    ) {
        when {
            loading -> LyricPlaceholder("正在读取歌词…", palette, Modifier.fillMaxSize())

            lyrics.isEmpty() -> LyricPlaceholder("未找到歌词", palette, Modifier.fillMaxSize())

            else -> {
                val topPad = with(density) { (viewportPx * config.anchorFraction).toDp() }
                val bottomPad = with(density) { (viewportPx * (1f - config.anchorFraction)).toDp() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalMask(config.maskStops),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        DesktopLyricLine(
                            line = line,
                            nextTimeMs = lyrics.getOrNull(index + 1)?.timeMs,
                            distance = if (synced) abs(index - effectiveIndex) else 0,
                            synced = synced,
                            livePosition = if (synced && index == effectiveIndex) livePosition else null,
                            config = config,
                            palette = palette,
                            onClick = { if (line.timeMs > 0L) onSeek(line.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopLyricLine(
    line: LyricLine,
    nextTimeMs: Long?,
    distance: Int,
    synced: Boolean,
    livePosition: State<Long>?,
    config: DesktopLyricConfig,
    palette: DesktopPalette,
    onClick: () -> Unit,
) {
    val isCurrent = distance == 0
    val targetAlpha = if (synced) config.alphaFor(distance) else 1f
    val alpha by animateFloatAsState(targetAlpha, LyricTransition, label = "desktopLyricAlpha")
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) config.activeScale else 1f,
        animationSpec = LyricTransition,
        label = "desktopLyricScale",
    )
    val fontSize by animateFloatAsState(
        targetValue = if (isCurrent) config.activeSize.value else config.idleSize.value,
        animationSpec = LyricTransition,
        label = "desktopLyricSize",
    )

    val activeColour = config.activeColor ?: palette.accent
    val idleGlyphColour = palette.text.copy(alpha = 0.34f)
    val words = line.words
    val hasWords = isCurrent && words.isNotEmpty() && livePosition != null
    val wordProgress = if (hasWords) {
        LrcParser.wordProgress(words, livePosition!!.value)
    } else {
        null
    }

    // 整行扫光：网页端用 CSS 变量推进 background-clip:text 的分界点。
    val sweepProgress = if (isCurrent && !hasWords && livePosition != null && synced) {
        val span = ((nextTimeMs ?: 0L) - line.timeMs).coerceAtLeast(400L)
        ((livePosition.value - line.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }

    val glow = if (config.glow && isCurrent) {
        Shadow(color = palette.accent.copy(alpha = 0.42f), blurRadius = 22f)
    } else {
        null
    }

    val baseStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.42f).sp,
        fontWeight = if (isCurrent) config.activeWeight else config.idleWeight,
        textAlign = config.textAlign,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        shadow = glow,
    )

    // 左对齐时缩放原点放左侧，否则 1.045× 放大后首字会溢出左 padding 被 LazyColumn 裁掉。
    val transformOrigin = if (config.textAlign == TextAlign.Start) {
        TransformOrigin(0f, 0.5f)
    } else {
        TransformOrigin.Center
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = synced && line.timeMs > 0L, onClick = onClick)
            .padding(vertical = 7.dp, horizontal = config.horizontalPadding)
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
                this.transformOrigin = transformOrigin
            },
        horizontalAlignment = when (config.textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
    ) {
        when {
            hasWords -> WordByWordText(
                text = line.text,
                words = words,
                wordProgress = wordProgress!!,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.42f).sp,
                fontWeight = config.activeWeight,
                activeColor = activeColour,
                idleColor = idleGlyphColour,
                textAlign = config.textAlign,
                shadow = glow,
            )

            sweepProgress != null -> {
                // 整行先铺「未唱」色，再叠一层当前色的副本按进度裁掉右侧：
                // Compose 的 TextStyle 还不能直接在 copy 里挂渐变画笔。
                Box {
                    Text(
                        text = line.text,
                        style = baseStyle.copy(color = idleGlyphColour),
                    )
                    Text(
                        text = line.text,
                        style = baseStyle.copy(color = activeColour),
                        modifier = Modifier.drawWithContent {
                            clipRect(right = size.width * sweepProgress) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    )
                }
            }

            isCurrent -> Text(
                text = line.text,
                style = baseStyle.copy(color = activeColour),
            )

            else -> Text(
                text = line.text,
                style = baseStyle.copy(color = palette.text),
            )
        }

        line.trans?.takeIf { it.isNotBlank() }?.let { translated ->
            Text(
                text = translated,
                fontSize = config.translationSize,
                color = palette.muted.copy(alpha = config.translationAlpha),
                textAlign = config.textAlign,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun LyricPlaceholder(text: String, palette: DesktopPalette, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 14.sp, color = palette.muted)
    }
}

/**
 * 上下渐隐：把 LazyColumn 画完的内容按竖向 alpha 遮罩裁掉两端。
 *
 * 必须先用 graphicsLayer(Offscreen) 把内容画进独立离屏缓冲，
 * 否则 BlendMode.DstIn 会连父级背景一起裁透明，露出窗口黑底。
 */
private fun Modifier.verticalMask(stops: List<Pair<Float, Float>>): Modifier =
    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = stops
                        .map { (fraction, alpha) -> fraction to Color.Black.copy(alpha = alpha) }
                        .toTypedArray(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * 逐字擦除歌词：每个字的左半（已唱）是实色，右半（未唱）是半透明色，
 * 分界点随演唱进度从左往右推进，还原网页端 `background-clip: text` 的卡拉 OK 观感。
 */
@Composable
fun WordByWordText(
    text: String,
    words: List<LyricWord>,
    wordProgress: FloatArray,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    activeColor: Color,
    idleColor: Color,
    textAlign: TextAlign = TextAlign.Center,
    shadow: Shadow? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        shadow = shadow,
    )

    val layoutResult = remember(text, fontSize, fontWeight, lineHeight, textAlign) {
        textMeasurer.measure(AnnotatedString(text), style = textStyle)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { lineHeight.toDp() }),
    ) {
        val textLeft = when (textAlign) {
            TextAlign.Start -> 0f
            else -> (size.width - layoutResult.size.width) / 2f
        }
        val textTop = 0f

        drawText(
            textLayoutResult = layoutResult,
            color = idleColor,
            topLeft = Offset(textLeft, textTop),
        )

        var charIndex = 0
        words.forEachIndexed { wordIndex, word ->
            val wordLen = word.text.length
            if (wordLen == 0) return@forEachIndexed
            val firstBox = layoutResult.getBoundingBox(charIndex)
            val lastBox = layoutResult.getBoundingBox(charIndex + wordLen - 1)
            val wordLeft = textLeft + firstBox.left
            val wordRight = textLeft + lastBox.right
            val progress = wordProgress.getOrElse(wordIndex) { 0f }
            val clipRight = wordLeft + (wordRight - wordLeft) * progress

            clipRect(
                left = wordLeft,
                top = textTop + firstBox.top,
                right = clipRight,
                bottom = textTop + lastBox.bottom,
            ) {
                drawText(
                    textLayoutResult = layoutResult,
                    color = activeColor,
                    topLeft = Offset(textLeft, textTop),
                )
            }
            charIndex += wordLen
        }
    }
}

/** 把歌词卡片上下两端渐隐进它的底色（原生页专用）。 */
@Composable
fun BoxScope.LyricFade(colour: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.verticalGradient(listOf(colour, Color.Transparent))),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colour))),
    )
}
