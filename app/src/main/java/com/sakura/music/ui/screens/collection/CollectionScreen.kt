package com.sakura.music.ui.screens.collection

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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.MediaHeader
import com.sakura.music.ui.components.PlatformBadge
import com.sakura.music.ui.components.PlayAllButton
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.navigation.CollectionKind
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import com.sakura.music.data.model.Platform

/**
 * 平台榜单 / 平台歌单详情。
 *
 * 曲目由网关一次性给全（榜单默认 100 首、上限 200），所以这里不做分页。
 */
@Composable
fun CollectionScreen(
    navigator: SakuraNavigator,
    platform: Platform,
    collectionId: String,
    kind: CollectionKind,
    initialTitle: String,
) {
    val container = appContainer()
    val viewModel = rememberAppViewModel(key = "${platform.id}-$collectionId") {
        CollectionViewModel(it.api, platform, collectionId, kind)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by container.playbackCenter.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val current = state
    val detail = current.detail
    val tracks = detail?.items.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = detail?.title?.takeIf { it.isNotBlank() } ?: initialTitle.ifBlank { kind.label },
                subtitle = kind.label,
                onBack = navigator::back,
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
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
                current.loading && detail == null -> LoadingState("正在加载…")

                current.error != null && detail == null -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "加载失败",
                    message = current.error,
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                detail == null -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "没有取到内容",
                    message = "上游可能暂时不可用，稍后再试。",
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    item(key = "header") {
                        MediaHeader(
                            title = detail.title,
                            subtitle = listOf(
                                detail.platform.label,
                                "${tracks.size} 首",
                            ).joinToString(" · "),
                            description = detail.description,
                            coverUrl = detail.cover,
                            badges = {
                                if (detail.platform.isKnown) {
                                    PlatformBadge(platform = detail.platform, selected = true)
                                }
                            },
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
                                icon = Icons.Rounded.CloudOff,
                                title = "这个歌单是空的",
                                message = "上游没有返回曲目，可能受版权限制。",
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
}
