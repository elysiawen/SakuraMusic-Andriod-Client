package com.sakura.music.ui.screens.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.PublicUser
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AccountAvatar
import com.sakura.music.ui.components.ActionsSheet
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.ContentTopPadding
import com.sakura.music.ui.components.CreatePlaylistDialog
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.SakuraCard
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SheetAction
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import com.sakura.music.ui.screens.library.LibraryActions
import com.sakura.music.ui.screens.library.LibraryTab
import com.sakura.music.ui.screens.library.LibraryTabRow
import com.sakura.music.ui.screens.library.LibraryViewModel
import com.sakura.music.ui.screens.library.libraryTabContent
import kotlinx.coroutines.launch

/**
 * 账号页。
 *
 * 从上往下：身份概览（点头像那一栏进账户资料）→ 三项统计 → 音乐库三个页签（歌单 / 历史 /
 * 本地）。音乐库不再单独占一个页签——它本来就是「这个账号的东西」，排在这里顺着看下来即可。
 *
 * 收藏是歌单页签里的第一条（「我喜欢的音乐」），点进去是单独一页：
 * 它在网关侧不是歌单，没有 id，不能改名或排序，所以没法和自建歌单共用详情页。
 */
@Composable
fun AccountScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { LibraryViewModel(it.libraryRepository, it.downloadCenter) }
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val library by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var clearingHistory by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Playlist?>(null) }

    val user = auth.user
    val current = library

    // 统计数字来自服务端：进这一页时刷新一次，收藏/歌单/历史是在别处改的。
    LaunchedEffect(Unit) { container.authRepository.refreshStats() }

    val actions = LibraryActions(
        onPlay = { tracks, index -> container.playbackCenter.play(tracks, index) },
        onMessage = viewModel::showMessage,
        onOpenFavorites = navigator::openFavorites,
        onOpenPlaylist = { navigator.openPlaylist(it.id, it.name) },
        onPlaylistMore = { actionsFor = it },
        onRetry = viewModel::refresh,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = "账号",
                subtitle = user?.let { "@${it.username}" } ?: if (auth.offline) "离线" else "未登录",
                actions = {
                    // 新建歌单：只在歌单页签出现。列表里因此只剩真实内容，
                    // 不再夹一条横在那儿的动作行。
                    if (current.tab == LibraryTab.Playlists) {
                        IconButton(onClick = { creating = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新建歌单")
                        }
                    }
                    // 清空历史只在历史页签、且确实有记录时出现（和原来音乐库页的顶栏一个逻辑）。
                    if (current.tab == LibraryTab.History && current.history.isNotEmpty()) {
                        IconButton(onClick = { clearingHistory = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "清空播放历史")
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch { container.authRepository.refreshStats() }
                            viewModel.refresh()
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    // 设置不占底部栏的位置，从这里进——图标比默认小一号，不抢标题的视线。
                    IconButton(onClick = navigator::openSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ListBottomPadding),
            ) {
                item(key = "top-gap") { Spacer(Modifier.height(ContentTopPadding)) }

                if (user == null) {
                    // 会话刚失效的一瞬间：根组件马上会切到登录页，这里只要别闪出空白。
                    item(key = "missing-user") {
                        Text(
                            text = "登录已失效，请重新登录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    item(key = "profile") {
                        ProfileCard(
                            user = user,
                            onClick = navigator::openAccountDetail,
                        )
                    }

                    item(key = "stats") {
                        Spacer(Modifier.height(18.dp))
                        SakuraCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                StatCell("收藏", auth.stats?.favorites, Modifier.weight(1f))
                                StatCell("歌单", auth.stats?.playlists, Modifier.weight(1f))
                                StatCell("历史", auth.stats?.history, Modifier.weight(1f))
                            }
                        }
                    }
                }

                item(key = "library-gap") { Spacer(Modifier.height(16.dp)) }

                // 页签钉在顶部：翻收藏翻到很下面时，也还能直接切到歌单或历史。
                stickyHeader(key = "library-tabs") {
                    LibraryTabRow(
                        selected = current.tab,
                        onSelect = viewModel::selectTab,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                libraryTabContent(state = current, actions = actions)

                // 底部留一点空，别让最后一个列表项贴着迷你播放条。
                item(key = "bottom-gap") { Spacer(Modifier.height(24.dp)) }
            }

            SnackbarMessages(
                hostState = snackbar,
                message = current.message,
                onConsumed = viewModel::consumeMessage,
            )
        }
    }

    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                creating = false
                viewModel.createPlaylist(name)
            },
        )
    }

    renaming?.let { playlist ->
        CreatePlaylistDialog(
            title = "重命名歌单",
            confirmLabel = "保存",
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onCreate = { name ->
                renaming = null
                viewModel.renamePlaylist(playlist, name)
            },
        )
    }

    deleting?.let { playlist ->
        ConfirmDialog(
            title = "删除歌单",
            message = "「${playlist.name}」及其中的曲目会被一起删除，无法撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                deleting = null
                viewModel.deletePlaylist(playlist)
            },
            onDismiss = { deleting = null },
        )
    }

    if (clearingHistory) {
        ConfirmDialog(
            title = "清空播放历史",
            message = "全部播放记录会被删除，无法撤销。",
            confirmLabel = "清空",
            destructive = true,
            onConfirm = {
                clearingHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { clearingHistory = false },
        )
    }

    actionsFor?.let { playlist ->
        ActionsSheet(
            title = playlist.name,
            onDismiss = { actionsFor = null },
            actions = listOf(
                SheetAction(
                    label = "重命名",
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    onClick = { renaming = playlist },
                ),
                SheetAction(
                    label = "删除歌单",
                    icon = Icons.Rounded.Delete,
                    destructive = true,
                    onClick = { deleting = playlist },
                ),
            ),
        )
    }
}

/** 身份那一栏：整块可点，点进账户资料。 */
@Composable
private fun ProfileCard(user: PublicUser, onClick: () -> Unit) {
    SakuraCard(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(
                avatar = user.avatar,
                fallbackInitial = user.initial,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname.ifBlank { user.username },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (user.isAdmin) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "管理员",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.toString() ?: "--",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
