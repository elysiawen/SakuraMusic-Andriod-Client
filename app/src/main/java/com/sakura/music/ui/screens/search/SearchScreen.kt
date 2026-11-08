package com.sakura.music.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.SearchType
import com.sakura.music.data.prefs.PlatformCapability
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AlbumRow
import com.sakura.music.ui.components.ArtistRow
import com.sakura.music.ui.components.ContentTopPadding
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.PlatformStatusRow
import com.sakura.music.ui.components.PlaylistSummaryRow
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.components.brandColor
import com.sakura.music.ui.navigation.CollectionKind
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import kotlinx.coroutines.delay

/**
 * 聚合搜索。
 *
 * 四个页签对应网关的四种 `type`；同一首歌在两个平台都有时会合并成一条，
 * 点整行播放，长列的「更多」里可以切音源。左侧另有平台维度，与类型相互独立。
 */
@Composable
fun SearchScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { SearchViewModel(it.api, it.settings, it.platformPrefs) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val prefs by container.platformPrefs.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    /*
     * 平台维度跟着设置里的开关走：两个都开着给页签，只剩一个时给静态标识。
     * remember(prefs) 免得每次重组都产生新 List（拿它当 Effect key 会反复重启）。
     */
    val enabledPlatforms = remember(prefs) {
        Platform.known.filter { prefs.isEnabled(it, PlatformCapability.SEARCH) }
    }
    val soloPlatform = if (enabledPlatforms.size == 1) enabledPlatforms.first() else null

    val current = state

    /*
     * 顶栏只在结果列表回到最顶上时露出来，往下翻就收掉，把高度让给结果。
     * 与「发现」页同一套做法：顶栏放进内容里而不是 Scaffold 的 topBar，
     * 收起省下的高度由 Column 自然还给列表；状态栏间距是 Column 的固定内边距，
     * 不会跟着顶栏一起收掉，所以输入框不会钻到状态栏底下。
     */
    val listState = rememberLazyListState()
    // 加载中 / 空态 / 历史面板都没有这个列表；滚动源不在场时顶栏一律露出来。
    val resultListVisible = !current.loading &&
        !(current.keyword.isBlank() && history.isNotEmpty()) &&
        current.error == null &&
        current.searched &&
        !current.isEmptyResult
    val atTop by remember(resultListVisible) {
        derivedStateOf {
            !resultListVisible ||
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 0)
        }
    }
    // 顶栏收起时输入框左边让出返回键的位置；展开时还是原来的 16dp 边距。
    val fieldStartPadding by animateDpAsState(
        targetValue = if (atTop) 8.dp else 0.dp,
        animationSpec = tween(200),
        label = "searchFieldStart",
    )

    // 从发现 / 音乐库的搜索条点进来，就是要立刻打字——所以自动聚焦并弹出键盘。
    // 等一帧再要焦点：此时输入框节点还没挂上去，直接 requestFocus 会抛异常。
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    // 搜到东西后收起键盘，列表才看得全；新一轮结果从第一首开始看，顶栏也跟着回来。
    LaunchedEffect(current.loading) {
        if (current.loading) {
            keyboard?.hide()
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            AnimatedVisibility(
                visible = atTop,
                // 收起比展开快一点：往下翻是「让开」，不该让人觉得有东西在挡路。
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(240)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
            ) {
                SakuraTopBar(
                    title = "搜索",
                    subtitle = if (soloPlatform != null) {
                        "当前只启用了${soloPlatform.label}的搜索"
                    } else {
                        "已启用平台的结果会自动合并去重"
                    },
                    onBack = navigator::back,
                    // 状态栏间距由 Column 统一让开，顶栏别再让第二遍。
                    statusBarPadding = false,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 16.dp,
                        top = ContentTopPadding,
                        bottom = ContentTopPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 顶栏收起后返回键不能一起消失：挪到输入框左边，输入框让出位置。
                AnimatedVisibility(
                    visible = !atTop,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(tween(240)),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(tween(140)),
                ) {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
                OutlinedTextField(
                    value = current.keyword,
                    onValueChange = viewModel::setKeyword,
                    placeholder = { Text("歌曲、歌手、专辑或歌单") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (current.keyword.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setKeyword("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = fieldStartPadding)
                        .focusRequester(focusRequester),
                )
            }

            SearchFilterBar(
                enabledPlatforms = enabledPlatforms,
                soloPlatform = soloPlatform,
                selectedPlatform = current.selectedPlatform,
                type = current.type,
                onPlatform = viewModel::setPlatform,
                onType = viewModel::setType,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    current.loading -> LoadingState("正在搜索…")

                    // 输入框空着的时候，「最近搜过什么」比任何提示都有用。
                    // 排在错误态前面：清空输入框就该回到记录，而不是停在上一次的报错上。
                    current.keyword.isBlank() && history.isNotEmpty() -> SearchHistoryPanel(
                        history = history,
                        onPick = viewModel::searchKeyword,
                        onRemove = viewModel::removeHistory,
                        onClear = viewModel::clearHistory,
                    )

                    current.error != null -> StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = "搜索失败",
                        message = current.error,
                        actionLabel = "重试",
                        onAction = viewModel::search,
                    )

                    !current.searched -> StateMessage(
                        icon = Icons.Rounded.Search,
                        title = "输入关键词开始搜索",
                        message = "已启用的平台会同时搜索，同一首歌只占一条；结果里点「更多」可以切换音源。",
                    )

                    current.isEmptyResult -> StateMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = "没有找到相关内容",
                        message = "换个关键词试试，或者确认上游服务是否正常。",
                    )

                    else -> LazyColumn(
                        // 顶栏的显隐按这份滚动状态算，不能用默认那份。
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = ListBottomPadding),
                    ) {
                        item(key = "platforms") {
                            Spacer(Modifier.height(6.dp))
                            PlatformStatusRow(current.platforms)
                            Spacer(Modifier.height(4.dp))
                        }

                        when (current.type) {
                            SearchType.SONG -> itemsIndexed(
                                items = current.tracks,
                                key = { index, track -> "s-$index:${track.key}" },
                            ) { index, track ->
                                val list = current.tracks
                                TrackRowContainer(
                                    track = track,
                                    index = index + 1,
                                    onPlay = { container.playbackCenter.play(list, index) },
                                    onMessage = viewModel::showMessage,
                                    // 行内的爱心够用了，菜单里不再重复一条「收藏」。
                                    showFavoriteAction = false,
                                )
                                if (index != list.lastIndex) TrackDivider()
                            }

                            SearchType.ARTIST -> itemsIndexed(
                                items = current.artists,
                                key = { index, artist -> "a-$index:${artist.key}" },
                            ) { index, artist ->
                                ArtistRow(
                                    artist = artist,
                                    onClick = { platform, id ->
                                        navigator.openArtist(platform, id, artist.name)
                                    },
                                )
                                if (index != current.artists.lastIndex) TrackDivider(16)
                            }

                            SearchType.ALBUM -> itemsIndexed(
                                items = current.albums,
                                key = { index, album -> "b-$index:${album.key}" },
                            ) { index, album ->
                                AlbumRow(
                                    album = album,
                                    onClick = { platform, id ->
                                        navigator.openAlbum(platform, id, album.name)
                                    },
                                )
                                if (index != current.albums.lastIndex) TrackDivider(16)
                            }

                            SearchType.PLAYLIST -> itemsIndexed(
                                items = current.playlists,
                                key = { index, item -> "p-$index:${item.platform.id}-${item.id}" },
                            ) { index, summary ->
                                PlaylistSummaryRow(
                                    summary = summary,
                                    onClick = {
                                        navigator.openCollection(
                                            platform = summary.platform,
                                            id = summary.id,
                                            kind = CollectionKind.PLAYLIST,
                                            title = summary.title,
                                        )
                                    },
                                )
                                if (index != current.playlists.lastIndex) TrackDivider(16)
                            }
                        }

                        if (current.canLoadMore) {
                            item(key = "more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (current.loadingMore) {
                                        CircularProgressIndicator(
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    } else {
                                        TextButton(onClick = viewModel::loadMore) { Text("加载更多") }
                                    }
                                }
                            }
                        }
                    }
                }

                SnackbarMessages(
                    hostState = snackbar,
                    message = current.message,
                    onConsumed = viewModel::consumeMessage,
                )
            }
        }
    }
}

/** 页签（平台维度和类型维度共用）：选中的用强调色容器打底。 */
@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 只剩一个平台时的静态标识：品牌色圆点 + 平台名，说明结果来自哪儿。 */
@Composable
private fun PlatformSoloLabel(platform: Platform) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(platform.brandColor),
            )
            Text(
                text = platform.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "当前平台",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 平台维度靠左、内容类型靠右（与桌面 / 网页端一致）。
 *
 * 不用「屏幕够宽才分组」这种猜阈值的做法：先量出两组的实际宽度，
 * 同一行放得下就分列两端，放不下就平台在上、类型在下，类型仍然靠右。
 * 换字号、多一个平台、单平台变成静态标识都不会让类型页签跑回左边。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterBar(
    enabledPlatforms: List<Platform>,
    soloPlatform: Platform?,
    selectedPlatform: Platform?,
    type: SearchType,
    onPlatform: (Platform?) -> Unit,
    onType: (SearchType) -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        content = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlatformChips(enabledPlatforms, soloPlatform, selectedPlatform, onPlatform)
            }
            FlowRow(
                // 既要贴右又要留间距：光用 Arrangement.End 会把 chip 挤在一起。
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TypeChips(type, onType)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val platform = measurables[0].measure(loose)
        val types = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val sameRow = platform.width + types.width + gapPx <= width
        val height = if (sameRow) {
            maxOf(platform.height, types.height)
        } else {
            platform.height + types.height + gapPx
        }
        layout(width, height) {
            platform.placeRelative(0, 0)
            types.placeRelative(
                x = width - types.width,
                y = if (sameRow) 0 else platform.height + gapPx,
            )
        }
    }
}

/** 左侧平台页签：设置里关掉的不占位，只剩一个时改放静态标识。 */
@Composable
private fun PlatformChips(
    enabledPlatforms: List<Platform>,
    soloPlatform: Platform?,
    selectedPlatform: Platform?,
    onPlatform: (Platform?) -> Unit,
) {
    if (soloPlatform != null) {
        PlatformSoloLabel(soloPlatform)
        return
    }
    TypeChip(
        label = "全部",
        selected = selectedPlatform == null,
        onClick = { onPlatform(null) },
    )
    enabledPlatforms.forEach { platform ->
        TypeChip(
            label = platform.label,
            selected = selectedPlatform == platform,
            onClick = { onPlatform(platform) },
        )
    }
}

/** 右侧内容类型页签：输入关键词后按键盘的搜索键即可发起检索。 */
@Composable
private fun TypeChips(
    type: SearchType,
    onType: (SearchType) -> Unit,
) {
    SearchType.entries.forEach { item ->
        TypeChip(
            label = item.label,
            selected = item == type,
            onClick = { onType(item) },
        )
    }
}
