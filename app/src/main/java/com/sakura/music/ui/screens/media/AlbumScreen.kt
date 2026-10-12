package com.sakura.music.ui.screens.media

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.Platform
import com.sakura.music.data.prefs.DetailHeaderStyle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.DetailAction
import com.sakura.music.ui.components.DetailHeaderData
import com.sakura.music.ui.components.DetailPage
import com.sakura.music.ui.components.SectionTitleRow
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.components.trackListStats
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel

/** 专辑详情：同样是单平台页面。 */
@Composable
fun AlbumScreen(
    navigator: SakuraNavigator,
    platform: Platform,
    albumId: String,
    initialName: String,
) {
    val container = appContainer()
    val viewModel = rememberAppViewModel(key = "album-${platform.id}-$albumId") {
        AlbumViewModel(it.api, platform, albumId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val headerStyle by container.settings.detailHeaderStyle
        .collectAsStateWithLifecycle(initialValue = DetailHeaderStyle.Default)

    val current = state
    val detail = current.detail
    val tracks = detail?.items.orEmpty()
    val artists = detail?.album?.artists.orEmpty()

    // 头部版式与加载占位都是用户可选的（设置 → 外观）。
    val immersive = headerStyle == DetailHeaderStyle.Immersive

    // 页面骨架（顶栏 / 悬浮工具栏 / 骨架屏 / 两栏接线）在 DetailPage 里，五个详情页共用。
    DetailPage(
        immersive = immersive,
        loading = current.loading,
        ready = detail != null,
        error = current.error,
        errorTitle = "专辑页加载失败",
        onRetry = viewModel::load,
        onBack = navigator::back,
        initialTitle = initialName,
        subtitle = "专辑 · ${platform.label}",
        snackbar = snackbar,
        message = current.message,
        onMessageConsumed = viewModel::consumeMessage,
        actions = listOf(
            DetailAction(icon = Icons.Rounded.Refresh, label = "刷新", onClick = viewModel::load),
        ),
        header = DetailHeaderData(
            title = detail?.album?.name?.takeIf { it.isNotBlank() } ?: initialName.ifBlank { "专辑" },
            coverUrl = detail?.album?.cover,
            label = "专辑 · ${platform.label}",
            subtitle = listOf(
                artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                detail?.album?.releaseDate.orEmpty(),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            stats = trackListStats(tracks),
            onPlayAll = { container.playbackCenter.play(tracks, 0) },
            playAllEnabled = tracks.isNotEmpty(),
        ),
        // 一首都没取到：整页换成它（头部也不再显示）。
        absent = {
            StateMessage(
                icon = Icons.Rounded.CloudOff,
                title = "没有取到专辑信息",
                message = "上游可能暂时不可用。",
                actionLabel = "重试",
                onAction = viewModel::load,
            )
        },
        // 列表内容：头部不在里面——单栏时它是第一项，两栏时它是左栏。
        list = {
            item(key = "tracks-title") {
                SectionTitleRow(title = "曲目")
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = "没有取到曲目",
                        message = "这张专辑可能受版权限制。",
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
                    showPlatform = false,
                    onPlay = { container.playbackCenter.play(tracks, index) },
                    onMessage = viewModel::showMessage,
                )
                if (index != tracks.lastIndex) TrackDivider()
            }
        },
    )
}
