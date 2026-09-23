package com.sakura.music.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.SearchType
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
import com.sakura.music.ui.navigation.CollectionKind
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import kotlinx.coroutines.delay

/**
 * 聚合搜索。
 *
 * 四个页签对应网关的四种 `type`；同一首歌在两个平台都有时会合并成一条，
 * 点整行播放，长列的「更多」里可以切音源。
 */
@Composable
fun SearchScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { SearchViewModel(it.api, it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val current = state

    // 从发现 / 音乐库的搜索条点进来，就是要立刻打字——所以自动聚焦并弹出键盘。
    // 等一帧再要焦点：此时输入框节点还没挂上去，直接 requestFocus 会抛异常。
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    // 搜到东西后收起键盘，列表才看得全。
    LaunchedEffect(current.loading) {
        if (current.loading) keyboard?.hide()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = "搜索",
                subtitle = "两个平台的结果会自动合并去重",
                onBack = navigator::back,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
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
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = ContentTopPadding)
                    .focusRequester(focusRequester),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchType.entries.forEach { type ->
                    TypeChip(
                        label = type.label,
                        selected = type == current.type,
                        onClick = { viewModel.setType(type) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::search) { Text("搜索") }
            }

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
                        message = "两个平台会同时搜索，同一首歌只占一条；结果里点「更多」可以切换音源。",
                    )

                    current.isEmptyResult -> StateMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = "没有找到相关内容",
                        message = "换个关键词试试，或者确认上游服务是否正常。",
                    )

                    else -> LazyColumn(
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

/** 类型页签：选中的用强调色容器打底。 */
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
