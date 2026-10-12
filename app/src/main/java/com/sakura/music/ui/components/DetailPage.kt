package com.sakura.music.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 详情页头部那一份数据。
 *
 * 专辑 / 歌手 / 榜单 / 收藏 / 自建歌单喂给它的字段完全一样，只是取值不同——所以它值得是
 * 一个数据类，而不是五个页面各自拼一遍参数。
 */
internal data class DetailHeaderData(
    val title: String,
    val coverUrl: String?,
    val label: String? = null,
    val subtitle: String? = null,
    val stats: String? = null,
    val description: String? = null,
    val badges: (@Composable RowScope.() -> Unit)? = null,
    val onPlayAll: (() -> Unit)? = null,
    val playAllEnabled: Boolean = true,
    /** 经典版式里歌手头像用圆形。 */
    val circularCover: Boolean = false,
)

/**
 * 详情页顶部的一个动作（刷新、歌单操作…）。
 *
 * 两种版式下它的样子不一样：经典版式是顶栏上的普通图标按钮，沉浸版是浮在封面上的白胶囊。
 * 这里只描述「什么图标、点了做什么」，两处各画各的——否则每个页面都得把同一组动作写两遍。
 */
internal data class DetailAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 详情页骨架（专辑 / 歌手 / 榜单 / 收藏 / 自建歌单共用）。
 *
 * 这五个页面除了「数据从哪来」和「列表里放什么」，其余是同一套：顶栏或悬浮工具栏、加载骨架、
 * 出错态、头部三选一（沉浸竖屏 / 沉浸横屏两栏 / 经典）、以及底下或右边那份列表。搬进来之后，
 * 跨页面的改动——换一种头部样式、横屏改两栏、加一条工具栏规则——就只改这一处。
 *
 * 留在调用方自己的部分：
 * - 头部数据 [header] 与列表内容 [list]（列表**不含**头部：单栏时头部是它的第一项，两栏时头部是左栏）；
 * - [absent]：「一份都没有」时的样子。收藏页用它（没有收藏了），其余页面用它放「不存在 / 没取到」；
 * - [errorTitle] 那句出错文案，以及歌单页那几个弹窗（在页面里，不在骨架里）。
 *
 * @param ready 内容到手了没。它是这一屏「有没有东西可看」的唯一判据：为 false 时按加载中 /
 *   出错 / [absent] 三选一，为 true 时才画头部与列表。
 * @param initialTitle 骨架里先写上的名字——导航参数里就有，不必等数据。留空则画一条灰杠。
 */
@Composable
internal fun DetailPage(
    immersive: Boolean,
    loading: Boolean,
    ready: Boolean,
    error: String?,
    errorTitle: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    header: DetailHeaderData,
    subtitle: String,
    snackbar: SnackbarHostState,
    initialTitle: String = header.title,
    actions: List<DetailAction> = emptyList(),
    message: String? = null,
    onMessageConsumed: () -> Unit = {},
    absent: @Composable () -> Unit,
    list: LazyListScope.() -> Unit,
) {
    val pane = rememberDetailPane(immersive)
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // 只有经典版式才有顶栏；沉浸版把返回 / 刷新浮在封面上（见下面的 MediaHeroToolbar）。
            if (!immersive) {
                SakuraTopBar(
                    title = header.title,
                    subtitle = subtitle,
                    onBack = onBack,
                    actions = {
                        actions.forEach { action ->
                            IconButton(onClick = action.onClick) {
                                Icon(action.icon, contentDescription = action.label)
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 沉浸版的封面要画到状态栏下面去，所以不能吃这份 padding。
                .padding(top = if (immersive) 0.dp else padding.calculateTopPadding()),
        ) {
            when {
                loading && !ready -> MediaDetailSkeleton(
                    title = initialTitle,
                    compact = !immersive,
                    twoPane = pane.twoPane,
                )

                error != null && !ready -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = errorTitle,
                    message = error,
                    actionLabel = "重试",
                    onAction = onRetry,
                )

                !ready -> absent()

                pane.twoPane -> Row(modifier = Modifier.fillMaxSize()) {
                    // 左栏整块不滚动——它管的是「这是什么」，用户滚右栏时它不该跟着动。
                    MediaHeroPane(
                        title = header.title,
                        coverUrl = header.coverUrl,
                        label = header.label,
                        subtitle = header.subtitle,
                        stats = header.stats,
                        description = header.description,
                        badges = header.badges,
                        onPlayAll = header.onPlayAll,
                        playAllEnabled = header.playAllEnabled,
                        modifier = Modifier
                            .width(pane.width)
                            .fillMaxHeight(),
                    )

                    LazyColumn(
                        state = pane.listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 左栏的封面可以画到状态栏下面去（沉浸感），右栏不行：
                            // 曲目第一行顶到系统栏底下会被切掉一截。
                            .statusBarsPadding(),
                        contentPadding = PaddingValues(bottom = ListBottomPadding),
                        content = list,
                    )
                }

                else -> LazyColumn(
                    // 必须用外面那个 listState：悬浮工具栏的显隐就是按它的位置算的。
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    item(key = "hero") {
                        // 两种版式共用同一份数据，只换组件。
                        if (immersive) {
                            MediaHero(
                                title = header.title,
                                coverUrl = header.coverUrl,
                                label = header.label,
                                subtitle = header.subtitle,
                                stats = header.stats,
                                description = header.description,
                                badges = header.badges,
                                onPlayAll = header.onPlayAll,
                                playAllEnabled = header.playAllEnabled,
                            )
                        } else {
                            MediaHeader(
                                title = header.title,
                                coverUrl = header.coverUrl,
                                label = header.label,
                                subtitle = header.subtitle,
                                stats = header.stats,
                                description = header.description,
                                badges = header.badges,
                                onPlayAll = header.onPlayAll,
                                playAllEnabled = header.playAllEnabled,
                                circularCover = header.circularCover,
                            )
                        }
                        Spacer(Modifier.height(if (immersive) 10.dp else 14.dp))
                    }

                    list()
                }
            }

            // 悬浮工具栏只属于沉浸版式：压在封面上时图标是白的，封面卷上去之后自己换成
            // 正常前景色。加载中 / 出错时没有封面（heroPresent = false），直接按列表态渲染。
            if (immersive) {
                MediaHeroToolbar(
                    title = header.title,
                    onBack = onBack,
                    // 两栏时那条列表在右栏里，进度按它算没有意义（第 0 项是首歌不是封面），
                    // 所以用 alwaysOverCover 按住「浮在封面上」的样子；工具栏也只铺在左栏上方
                    // （铺满整条会有一半落在右栏那块浅色曲目上，白图标直接看不见）。
                    listState = if (pane.twoPane) pane.listState else listState,
                    heroPresent = ready,
                    alwaysOverCover = pane.twoPane,
                    modifier = if (pane.twoPane) Modifier.width(pane.width) else Modifier,
                    actions = {
                        actions.forEach { action ->
                            HeroIconButton(
                                icon = action.icon,
                                contentDescription = action.label,
                                onClick = action.onClick,
                            )
                        }
                    },
                )
            }

            SnackbarMessages(
                hostState = snackbar,
                message = message,
                onConsumed = onMessageConsumed,
            )
        }
    }
}

/** 详情页要不要用横屏那套版式（沉浸式 = 左右两栏）。 */
@Composable
internal fun detailLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * 横屏两栏版式里左栏（头部）的宽度。
 *
 * 占屏宽四成，再夹一个上下限：窄一些的横屏别把右栏的曲目挤到放不下，
 * 平板横屏也别让头部那栏宽得离谱。
 */
@Composable
internal fun detailPaneWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return (width * 0.4f).coerceIn(260.dp, 420.dp)
}

/**
 * 详情页横屏两栏要用到的三个量。
 *
 * @param twoPane 该不该用两栏：横屏 **且** 沉浸式版式。竖屏、以及经典版式都用单栏——
 *   经典版的头部本来就是紧凑的横排，横屏下继续单栏就好；而单栏那套在横屏是浪费：
 *   宽出来一倍，曲目却被挤在封面底下，得先滚过整张封面才看得到。
 * @param width 左栏宽度，见 [detailPaneWidth]。
 * @param listState 右栏那条列表自己的滚动状态。单栏时用的仍是 [DetailPage] 里那个
 *   `listState`；两栏时头部不在列表里，工具栏按曲目算滚动进度没有意义
 *   （见 [MediaHeroToolbar] 的 `alwaysOverCover`）。
 */
internal class DetailPane(
    val twoPane: Boolean,
    val width: Dp,
    val listState: LazyListState,
)

@Composable
internal fun rememberDetailPane(immersive: Boolean): DetailPane {
    val twoPane = immersive && detailLandscape()
    val width = detailPaneWidth()
    val listState = rememberLazyListState()
    return remember(twoPane, width, listState) { DetailPane(twoPane, width, listState) }
}
