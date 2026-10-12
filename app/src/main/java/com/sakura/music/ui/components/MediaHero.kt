package com.sakura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.ui.util.formatTotalDuration

/**
 * 全出血封面的头部（**单栏版式**：竖屏，以及横屏下的经典版式）。
 *
 * 封面铺满整行（1:1），分类 / 标题 / 歌手 / 数量压在封面下沿的渐变压暗层上，封面下方才是
 * 这个页面的主 action。这样一进详情页先看到的是「这张专辑 / 这张歌单」本身，而不是先看到
 * 一条顶栏。
 *
 * 尺寸用 1:1 的方形——封面本来就是方的，不做裁切就等于把封面完整地当成背景用。
 *
 * 横屏的沉浸式版式不用这里，用 [MediaHeroPane]：那一整套是「左栏封面、右栏曲目」的两栏
 * 布局，封面站在最左边而不是铺满整屏宽。
 */
@Composable
fun MediaHero(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    /** 压在封面上的分类行，例如「专辑 · QQ 音乐」。 */
    label: String? = null,
    subtitle: String? = null,
    /** 数量与总时长一类的统计行，用 [trackListStats] 生成。 */
    stats: String? = null,
    description: String? = null,
    badges: (@Composable RowScope.() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    playAllEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            CoverArt(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                iconSize = 64.dp,
            )

            // 上下各一道渐变。只有下面一道的话，浅色封面上那排白色图标会看不清；
            // 只有上面一道的话，压在封面上的文字又会被浅色封面吃掉。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.30f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.68f),
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                HeroHeadings(title = title, label = label, subtitle = subtitle, stats = stats)
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroActions(
                onPlayAll = onPlayAll,
                playAllEnabled = playAllEnabled,
                badges = badges,
                wide = false,
            )

            // 调用方常把同一句话同时塞给 subtitle / stats / description，重复的就不再画一遍。
            val note = description?.takeIf { it.isNotBlank() }
            if (note != null && note != subtitle && note != stats) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 头部那几行字：分类 / 标题 / 歌手 / 数量。
 *
 * 两种版式共用——竖屏压在封面的压暗层上，横屏压在横幅的深色底上，都是白字（不分深浅色主题），
 * 差别只在外面留多少边距，那个由调用方给。
 */
@Composable
private fun HeroHeadings(
    title: String,
    label: String?,
    subtitle: String?,
    stats: String?,
) {
    if (!label.isNullOrBlank()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
    }

    Text(
        text = title.ifBlank { "未知" },
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (!stats.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = stats,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 头部的主 action（播放全部）与徽标。
 *
 * 竖屏排在封面下方、居中且铺满整行——这一页最想让用户点的就是它，铺满才够显眼；
 * 横屏两栏的左栏里靠左收着（那栏三百来 dp，铺满会变成一条谁也认不出是按钮的长条），
 * 徽标另起一行——它们和按钮并排会挤出去。
 */
@Composable
private fun HeroActions(
    onPlayAll: (() -> Unit)?,
    playAllEnabled: Boolean,
    badges: (@Composable RowScope.() -> Unit)?,
    wide: Boolean,
) {
    if (wide) {
        Column {
            if (onPlayAll != null) {
                PlayAllButton(onClick = onPlayAll, enabled = playAllEnabled)
            }
            if (badges != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = badges,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onPlayAll != null) {
            PlayAllButton(
                onClick = onPlayAll,
                enabled = playAllEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (badges != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = badges,
            )
        }
    }
}

/**
 * 横屏两栏版式的左栏：封面铺满整栏，分类 / 标题 / 数量 / 描述压在底部。
 *
 * 和竖屏那套是同一个思路（封面当底、白字压上去），差别在于这里没有「封面下方」那块地方
 * ——左栏就是整块版面，所以主 action 也压在封面上。这一栏管「这是哪张歌单」，
 * 右栏同时把曲目摆出来，横屏那一屏才不算浪费。
 */
@Composable
fun MediaHeroPane(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    label: String? = null,
    subtitle: String? = null,
    stats: String? = null,
    description: String? = null,
    badges: (@Composable RowScope.() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    playAllEnabled: Boolean = true,
) {
    Box(modifier = modifier) {
        // 封面把整栏铺满。手机横屏下这一栏的宽高比接近 1:1（320×300 上下），裁掉的不多；
        // 换成「上面一张正方封面、下面再排文字」在手机横屏就放不下了——封面一个人比整栏还高，
        // 标题会被挤到屏外。
        CoverArt(
            url = coverUrl,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            iconSize = 56.dp,
        )

        // 上下两道渐变：上面那道给白返回键垫底，下面那道给压在封面上的文字留出可读的底。
        // 底部这一段比竖屏那道重得多——文字加按钮占了这栏近七成高，压暗层必须从文字顶上
        // 就开始起势，否则大标题会直接压在没被压暗的封面上。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.34f to Color.Black.copy(alpha = 0.40f),
                        0.62f to Color.Black.copy(alpha = 0.74f),
                        1f to Color.Black.copy(alpha = 0.86f),
                    )
                )
        )

        // 文字块底对齐；栏不够高时它可以自己滚（窗口被分屏压得很扁、或者描述很长），
        // 于是永远不会顶到工具栏和状态栏那一条上去。
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                HeroHeadings(title = title, label = label, subtitle = subtitle, stats = stats)

                val note = description?.takeIf { it.isNotBlank() }
                if (note != null && note != subtitle && note != stats) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        // 栏不高，比竖屏少一行。
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (onPlayAll != null || badges != null) {
                    Spacer(Modifier.height(14.dp))
                    HeroActions(
                        onPlayAll = onPlayAll,
                        playAllEnabled = playAllEnabled,
                        badges = badges,
                        wide = true,
                    )
                }
            }
        }
    }
}

/** 悬浮工具栏上的图标色：压在封面上时是白的，卷上去之后跟着主题走。 */
private val LocalHeroForeground = staticCompositionLocalOf { Color.White }

/** 图标底下那枚圆形底衬的颜色。 */
private val LocalHeroChip = staticCompositionLocalOf { Color.Black.copy(alpha = 0.30f) }

/** 封面下沿被卷走这么多之后，图标开始从「压在封面上」的样子切换成「压在列表上」的样子。 */
private val HeroBarFadeWindow = 88.dp

/**
 * 详情页顶部那条**悬浮**工具栏（返回 + 标题 + 动作）。
 *
 * 它固定在屏幕最上面、不参与滚动，随封面被卷走的程度在两种样子之间过渡：
 *
 * - **封面还在**：没有底色、图标是白的、不显示标题——标题已经大字号压在封面上了，
 *   这时候顶上再写一遍是重复，也会把封面切成两半；
 * - **封面卷上去之后**：淡入成一条正常的顶栏（主题表面色 + onSurface 图标），
 *   并把标题补上。名字从封面「升」到顶栏，用户始终知道自己在哪一页。
 *
 * 图标底下那枚圆形底衬同理：压在封面上时是半透明黑（浅色封面上也看得清），
 * 卷上去之后变成表面色，与顶栏底色融为一体。
 *
 * @param title 卷上去之后显示在顶栏里的页面标题。
 * @param heroPresent 这一屏有没有全出血封面。加载中 / 出错 / 空列表时没有，
 *   直接按「封面已卷走」的样子渲染（实底 + 标题）。
 * @param alwaysOverCover true = 一直按「浮在封面上」的样子画（白图标、不显示标题）。
 *   横屏两栏里悬浮工具栏只铺在左栏上方，底下永远是那块封面、不会「卷走」，
 *   所以不需要（也不该）过渡成实底顶栏。而它算滚动进度按的是**曲目列表**——
 *   那边第 0 项是首歌不是封面，算出来必然是 1，不按住的话一进页面就成了实底。
 */
@Composable
fun MediaHeroToolbar(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    heroPresent: Boolean = true,
    alwaysOverCover: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val fadeWindowPx = with(LocalDensity.current) { HeroBarFadeWindow.roundToPx() }

    // 0 = 还浮在封面上，1 = 已经完全脱离封面。
    // 用 layoutInfo 里第 0 项的位置，而不是 firstVisibleItemScrollOffset：后者只在「第一个
    // 可见项」内部计数，封面整个卷出去之后就再也读不到它被推走了多少。
    val progress by remember(listState, heroPresent, fadeWindowPx) {
        derivedStateOf {
            if (!heroPresent) return@derivedStateOf 1f
            val hero = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f
            val trigger = hero.size - fadeWindowPx
            if (trigger <= 0) return@derivedStateOf 1f
            ((-hero.offset - trigger).toFloat() / fadeWindowPx).coerceIn(0f, 1f)
        }
    }

    /** 图标与底色实际按哪个进度画，见 [alwaysOverCover]。 */
    val shade = if (alwaysOverCover) 0f else progress

    Row(
        modifier = modifier
            .fillMaxWidth()
            // background 写在 statusBarsPadding 之前：底色要盖住状态栏那一条，
            // 否则卷上去之后状态栏下面会露出一条没被遮住的内容。
            .background(lerp(Color.Transparent, MaterialTheme.colorScheme.surface, shade))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalHeroForeground provides
                lerp(Color.White, MaterialTheme.colorScheme.onSurface, shade),
            LocalHeroChip provides
                lerp(Color.Black.copy(alpha = 0.30f), MaterialTheme.colorScheme.surface, shade),
        ) {
            HeroIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )

            // 透明时它仍然占着位置，右侧那几个图标才不会在过渡中左右横跳。
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = LocalHeroForeground.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .alpha(shade),
            )

            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/**
 * 悬浮工具栏上的图标按钮：图标底下垫一枚圆形底衬。
 *
 * 单独封一层是为了让颜色跟着 [MediaHeroToolbar] 的滚动进度走：`IconButton` 会自己
 * 覆盖 `LocalContentColor`，所以调用方没法靠外部提供前景色，只能从这儿取。
 */
@Composable
fun HeroIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(shape = CircleShape, color = LocalHeroChip.current) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = LocalHeroForeground.current,
            )
        }
    }
}

/**
 * 头部那行统计：「52 首 · 总时长 3 小时 40 分」。
 *
 * 一首的时长都没拿到时只留数量——宁可不写，也别显示「总时长 0 秒」。
 */
fun trackListStats(tracks: List<UnifiedTrack>): String = buildList {
    add("${tracks.size} 首")
    formatTotalDuration(tracks.sumOf { it.durationMs }).takeIf { it.isNotBlank() }?.let {
        add("总时长 $it")
    }
}.joinToString(" · ")

/**
 * 头部的主 action：播放全部。
 *
 * 做成填充按钮并铺满整行——这一页最想让用户点的就是它。之前是个文字按钮，压在一堆
 * 灰色说明文字里，几乎看不出来是这个页面的主要动作。
 */
@Composable
fun PlayAllButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "播放全部",
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
