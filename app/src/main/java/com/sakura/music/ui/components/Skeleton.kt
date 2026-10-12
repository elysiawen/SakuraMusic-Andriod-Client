package com.sakura.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架屏共用的那一条扫光进度（0 → 1 循环）。
 *
 * 整页只建一次，再传给每个占位块：一块一个 `rememberInfiniteTransition` 的话，
 * 一屏十来块就是十来个常驻动画，而且各扫各的、相位对不齐。
 */
@Composable
fun rememberSkeletonProgress(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "sweep",
    )
    return progress
}

/**
 * 一道横着扫过整块的光带。
 *
 * 全页所有占位共用 [rememberSkeletonProgress] 给的那一个进度，相位才对得齐 ——
 * 看起来是一道光从头扫到尾，而不是每块各扫各的。
 */
private fun DrawScope.drawSweep(progress: Float, colors: List<Color>) {
    val span = size.width * 0.7f
    val start = -span + (size.width + span) * progress
    drawRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(start, 0f),
            end = Offset(start + span, 0f),
        )
    )
}

/**
 * 骨架屏上的一块占位。
 *
 * 刻意不做成一块死灰：有一道更亮的光带横向扫过，用来区分「这里在等内容送到」和
 * 「这里本来就是空的」——后者才是 `StateMessage` 该出现的地方。
 */
@Composable
fun SkeletonBlock(
    progress: Float,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.surface, 0.7f)

    Box(
        modifier = modifier
            .clip(shape)
            // 用 drawBehind 而不是 drawWithCache：它每次绘制都会重读 progress，
            // 不需要为了动画重建 modifiers。
            .drawBehind { drawSweep(progress, listOf(base, highlight, base)) }
    )
}

/**
 * 头部那块封面占位。
 *
 * 底用的是「没有封面」时那套渐变——这首歌要是真没有封面，数据到了这里不会忽然换色。
 * 但**另外扫一道白光**：一块静止的渐变泡在整页会扫光的占位里，看着不像在加载，
 * 更像图坏了。
 *
 * 下沿还压了一道和真头部同款的渐变：真头部的白字就压在封面下沿那片暗区上，
 * 占位条也得压在同款暗区上才不会糊。
 */
@Composable
private fun CoverSkeleton(
    progress: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
) {
    Box(modifier = modifier) {
        CoverArt(
            url = null,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            iconSize = iconSize,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)),
                            startY = size.height * 0.46f,
                            endY = size.height,
                        )
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawSweep(
                        progress = progress,
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    )
                }
        )
    }
}

/**
 * 压在封面上的那几行字（分类 / 标题 / 数量）的占位条。
 *
 * 用白色半透明而不是灰块：那儿到时候写的是白字，占位也得是白的，换上去才不突兀。
 */
@Composable
private fun CoverTextBar(progress: Float, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .drawBehind {
                drawSweep(
                    progress = progress,
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.44f),
                        Color.White.copy(alpha = 0.20f),
                    ),
                )
            }
    )
}

/**
 * 一行歌曲的占位。
 *
 * 尺寸照抄 [TrackRow]（序号列 26dp + 封面 46dp + 两行文字），等真数据到了原地换成真行，
 * 整页不会上下跳一下。
 */
@Composable
fun TrackRowSkeleton(progress: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            SkeletonBlock(progress = progress, modifier = Modifier.size(width = 12.dp, height = 10.dp))
        }

        SkeletonBlock(
            progress = progress,
            modifier = Modifier.size(46.dp),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.55f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.3f).height(11.dp))
        }
    }
}

/** 骨架里铺几行歌。 */
private const val SkeletonRows = 6

/**
 * 详情页（专辑 / 歌单 / 榜单 / 歌手 / 收藏）的加载骨架。
 *
 * 不再只放一个转圈：这些页面的内容区很高，一个圈配上大片空白看着像「这一页什么都没有」。
 * 骨架先把封面、标题、播放全部、列表行的**位置**占住，等数据到了原地换成真的。
 *
 * @param title 从导航参数就能拿到的标题。已知的东西没必要藏起来，先写上。
 * @param compact 头部选了经典版式时传 true——那样封面是缩进页内的紧凑占位，
 *   不然加载完版面会从「全出血大图」跳成「左边小图」。
 * @param twoPane 横屏的沉浸式版式传 true：左栏整块封面、右栏曲目。
 */
@Composable
fun MediaDetailSkeleton(
    modifier: Modifier = Modifier,
    title: String? = null,
    compact: Boolean = false,
    twoPane: Boolean = false,
) {
    val progress = rememberSkeletonProgress()

    if (twoPane) {
        TwoPaneDetailSkeleton(progress = progress, title = title)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 让它可以滚动：屏幕不够高时最后一行不至于被硬切掉一半。
            .verticalScroll(rememberScrollState()),
    ) {
        if (compact) {
            CompactHeaderSkeleton(progress = progress, title = title)
        } else {
            PortraitHeaderSkeleton(progress = progress, title = title)
        }

        Spacer(Modifier.height(18.dp))

        repeat(SkeletonRows) { TrackRowSkeleton(progress = progress) }
    }
}

/**
 * 沉浸版式（竖屏）的头部占位。
 *
 * 位置照 [MediaHero] 来：封面铺满整行（1:1），分类 / 标题 / 统计压在封面下沿，
 * 「播放全部」在封面下方铺满整行。数据到了原地换成真的，一个字都不跳位置。
 */
@Composable
private fun PortraitHeaderSkeleton(progress: Float, title: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            CoverSkeleton(progress = progress, modifier = Modifier.fillMaxSize())

            // 这三条就是真头部压在封面上的那三行：分类 / 标题 / 统计。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                CoverTextBar(progress = progress, modifier = Modifier.size(width = 64.dp, height = 11.dp))
                Spacer(Modifier.height(9.dp))
                TitleOrBar(progress = progress, title = title, onCover = true)
                Spacer(Modifier.height(9.dp))
                CoverTextBar(progress = progress, modifier = Modifier.size(width = 128.dp, height = 11.dp))
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 「播放全部」那颗按钮的位置：宽度和高度都照 MediaHero 里那个来。
            SkeletonBlock(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}

/**
 * 横屏两栏版式的加载占位：左栏一整块封面（信息压在底部），右栏铺几行曲目。
 *
 * 形状照着 [MediaHeroPane] 那个左栏来（封面铺满、信息压在底部），曲目行则和右栏一致，
 * 数据到了原地换成真的，版面不会跳。
 */
@Composable
private fun TwoPaneDetailSkeleton(progress: Float, title: String?) {
    val paneWidth = detailPaneWidth()

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(paneWidth)
                .fillMaxHeight(),
        ) {
            CoverSkeleton(progress = progress, modifier = Modifier.fillMaxSize(), iconSize = 56.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                CoverTextBar(progress = progress, modifier = Modifier.size(width = 58.dp, height = 11.dp))
                Spacer(Modifier.height(9.dp))
                TitleOrBar(progress = progress, title = title, onCover = true)
                Spacer(Modifier.height(9.dp))
                CoverTextBar(progress = progress, modifier = Modifier.size(width = 112.dp, height = 11.dp))

                Spacer(Modifier.height(14.dp))

                // 「播放全部」在左栏里不铺满整栏，占位也跟着收窄。
                SkeletonBlock(
                    progress = progress,
                    modifier = Modifier.size(width = 168.dp, height = 46.dp),
                    shape = MaterialTheme.shapes.large,
                )
            }
        }

        // 右栏顶上让开状态栏，和真实版面一致（见 DetailPage）。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding(),
        ) {
            repeat(SkeletonRows) { TrackRowSkeleton(progress = progress) }
        }
    }
}

/**
 * 标题：拿到了就直接写上去（已知的东西没必要藏起来），没拿到就一条占位杠。
 *
 * @param onCover 压在封面上的那一版——字号更大、字是白的，和 [MediaHero] 里的标题一致。
 */
@Composable
private fun TitleOrBar(
    progress: Float,
    title: String?,
    onCover: Boolean = false,
) {
    if (!title.isNullOrBlank()) {
        Text(
            text = title,
            style = if (onCover) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (onCover) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (onCover) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else if (onCover) {
        CoverTextBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(26.dp),
        )
    } else {
        SkeletonBlock(
            progress = progress,
            modifier = Modifier.size(width = 168.dp, height = 20.dp),
        )
    }
}

/** 经典版式的头部占位：左边 112dp 封面、右边标题与一行副标题，下面一颗文字按钮。 */
@Composable
private fun CompactHeaderSkeleton(progress: Float, title: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 尺寸照 ClassicMediaHeader 里那张封面。
            SkeletonBlock(
                progress = progress,
                modifier = Modifier.size(112.dp),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                TitleOrBar(progress = progress, title = title)
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(0.5f).height(12.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 「播放全部」在经典版式里是个文字按钮，占位也跟着做小一点。
        SkeletonBlock(
            progress = progress,
            modifier = Modifier.size(width = 104.dp, height = 30.dp),
        )
    }
}
