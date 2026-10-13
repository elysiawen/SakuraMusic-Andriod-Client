package com.sakura.music.ui.screens.playlist

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.prefs.DetailHeaderStyle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ActionsSheet
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.DetailAction
import com.sakura.music.ui.components.DetailHeaderData
import com.sakura.music.ui.components.DetailPage
import com.sakura.music.ui.components.PlaylistDialog
import com.sakura.music.ui.components.SheetAction
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.components.trackListStats
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel

/** 描述留空时头部顶上那句引导。 */
private const val PlaylistHint = "自建歌单，可以用任意列表里的「更多 → 加入歌单」往这里添歌。"

/**
 * 自建歌单详情。
 *
 * 与平台歌单不同，这里可以改信息（名称 + 描述）、删除、把某一首挪出去——歌单的归属校验在网关侧做，
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
    val headerStyle by container.settings.detailHeaderStyle
        .collectAsStateWithLifecycle(initialValue = DetailHeaderStyle.Default)

    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val current = state
    val playlist = current.playlist
    val tracks = current.tracks

    // 歌单被删掉之后这个页面就没什么可展示的了。
    LaunchedEffect(current.deleted) {
        if (current.deleted) navigator.back()
    }

    // 头部版式与加载占位都是用户可选的（设置 → 外观）。
    val immersive = headerStyle == DetailHeaderStyle.Immersive

    // 页面骨架（顶栏 / 悬浮工具栏 / 骨架屏 / 两栏接线）在 DetailPage 里，五个详情页共用；
    // 「编辑信息」「删除」那两个弹窗是这一页独有的，留在下面。
    DetailPage(
        immersive = immersive,
        loading = current.loading,
        ready = playlist != null,
        error = current.error,
        errorTitle = "歌单打不开",
        onRetry = viewModel::load,
        onBack = navigator::back,
        initialTitle = initialName,
        subtitle = "自建歌单",
        snackbar = snackbar,
        message = current.message,
        onMessageConsumed = viewModel::consumeMessage,
        actions = listOf(
            DetailAction(icon = Icons.Rounded.Refresh, label = "刷新", onClick = viewModel::load),
            DetailAction(
                icon = Icons.Rounded.MoreVert,
                label = "歌单操作",
                onClick = { showActions = true },
            ),
        ),
        header = DetailHeaderData(
            title = playlist?.name?.takeIf { it.isNotBlank() } ?: initialName.ifBlank { "歌单" },
            coverUrl = playlist?.cover,
            label = "自建歌单",
            stats = trackListStats(tracks),
            description = playlist?.description?.takeIf { it.isNotBlank() } ?: PlaylistHint,
            onPlayAll = { container.playbackCenter.playAll(tracks) },
            playAllEnabled = tracks.isNotEmpty(),
        ),
        absent = {
            StateMessage(
                icon = Icons.Rounded.CloudOff,
                title = "歌单不存在",
                message = "它可能已经被删除了。",
            )
        },
        // 列表内容：头部不在里面——单栏时它是第一项，两栏时它是左栏。
        list = {
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
        },
    )

    if (showActions) {
        ActionsSheet(
            title = playlist?.name ?: "歌单",
            onDismiss = { showActions = false },
            actions = listOf(
                SheetAction(
                    label = "编辑信息",
                    icon = Icons.Rounded.Edit,
                    onClick = { editing = true },
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

    if (editing) {
        // 名称与描述在同一个弹窗里改、走同一次 PATCH——与新建歌单共用那份表单。
        PlaylistDialog(
            title = "编辑信息",
            confirmLabel = "保存",
            initialName = playlist?.name.orEmpty(),
            initialDescription = playlist?.description.orEmpty(),
            onDismiss = { editing = false },
            onSubmit = { name, description ->
                editing = false
                viewModel.updateInfo(name, description)
            },
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
