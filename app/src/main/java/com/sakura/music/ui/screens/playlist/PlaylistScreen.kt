package com.sakura.music.ui.screens.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ActionsSheet
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.MediaHeader
import com.sakura.music.ui.components.PlayAllButton
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SheetAction
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel

/**
 * 自建歌单详情。
 *
 * 与平台歌单不同，这里可以改名、删除、把某一首挪出去——歌单的归属校验在网关侧做，
 * 别人的歌单会直接 404，客户端不用额外判断。
 */
@Composable
fun PlaylistScreen(
    navigator: SakuraNavigator,
    playlistId: String,
    initialName: String,
) {
    val container = appContainer()
    val viewModel = rememberAppViewModel(key = "playlist-$playlistId") {
        PlaylistViewModel(it.libraryRepository, playlistId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val current = state
    val playlist = current.playlist
    val tracks = current.tracks

    // 歌单被删掉之后这个页面就没什么可展示的了。
    LaunchedEffect(current.deleted) {
        if (current.deleted) navigator.back()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = playlist?.name?.takeIf { it.isNotBlank() } ?: initialName.ifBlank { "歌单" },
                subtitle = "自建歌单",
                onBack = navigator::back,
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "歌单操作")
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
            when {
                current.loading && playlist == null -> LoadingState("正在加载…")

                current.error != null && playlist == null -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "歌单打不开",
                    message = current.error,
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                playlist == null -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "歌单不存在",
                    message = "它可能已经被删除了。",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    item(key = "header") {
                        MediaHeader(
                            title = playlist.name,
                            subtitle = "${tracks.size} 首",
                            description = playlist.description.takeIf { it.isNotBlank() }
                                ?: "自建歌单，可以用任意列表里的「更多 → 加入歌单」往这里添歌。",
                            coverUrl = playlist.cover,
                            actions = {
                                PlayAllButton(
                                    enabled = tracks.isNotEmpty(),
                                    onClick = { container.playbackCenter.play(tracks, 0) },
                                )
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    if (tracks.isEmpty()) {
                        item(key = "empty") {
                            StateMessage(
                                icon = Icons.Rounded.PlaylistRemove,
                                title = "歌单还是空的",
                                message = "在搜索、发现或收藏里点歌曲的「更多」，就能把它加进来。",
                            )
                        }
                    }

                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "$index:${track.key}" },
                    ) { index, track ->
                        TrackRowContainer(
                            track = track,
                            index = index + 1,
                            onPlay = { container.playbackCenter.play(tracks, index) },
                            onMessage = viewModel::showMessage,
                            extraActions = listOf(
                                SheetAction(
                                    label = "从歌单移除",
                                    icon = Icons.Rounded.Delete,
                                    destructive = true,
                                    onClick = { viewModel.removeTrack(track) },
                                ),
                            ),
                        )
                        if (index != tracks.lastIndex) TrackDivider()
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

    if (showActions) {
        ActionsSheet(
            title = playlist?.name ?: "歌单",
            onDismiss = { showActions = false },
            actions = listOf(
                SheetAction(
                    label = "重命名歌单",
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    onClick = { renaming = true },
                ),
                SheetAction(
                    label = "删除歌单",
                    icon = Icons.Rounded.Delete,
                    destructive = true,
                    onClick = { deleting = true },
                ),
            ),
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = false
                        viewModel.rename(name)
                    },
                    enabled = name.trim().isNotEmpty(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "删除歌单",
            message = "「${playlist?.name.orEmpty()}」及其中的曲目会被一起删除，无法撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                deleting = false
                viewModel.delete()
            },
            onDismiss = { deleting = false },
        )
    }
}
