package com.sakura.music.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.sakura.music.data.model.Platform
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AlbumCard
import com.sakura.music.ui.components.InfoPill
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.MediaHeader
import com.sakura.music.ui.components.PlatformBadge
import com.sakura.music.ui.components.PlayAllButton
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SectionTitleRow
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel

/**
 * 歌手详情。
 *
 * 这是**单平台**页面：歌手 ID 只在对应平台有效，所以页面自己也带着平台，
 * 「专辑」里的卡片也只会跳回同一个平台。
 */
@Composable
fun ArtistScreen(
    navigator: SakuraNavigator,
    platform: Platform,
    artistId: String,
    initialName: String,
) {
    val container = appContainer()
    val viewModel = rememberAppViewModel(key = "artist-${platform.id}-$artistId") {
        ArtistViewModel(it.api, platform, artistId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val current = state
    val detail = current.detail
    val tracks = detail?.items.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = detail?.artist?.name?.takeIf { it.isNotBlank() } ?: initialName.ifBlank { "歌手" },
                subtitle = "歌手 · ${platform.label}",
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
                    title = "歌手页加载失败",
                    message = current.error,
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                detail == null -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "没有取到歌手信息",
                    message = "上游可能暂时不可用。",
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    item(key = "header") {
                        MediaHeader(
                            title = detail.artist.name,
                            subtitle = detail.artist.subtitle,
                            description = "热门歌曲 ${tracks.size} 首",
                            coverUrl = detail.artist.avatar,
                            circularCover = true,
                            badges = {
                                if (detail.artist.platform.isKnown) {
                                    PlatformBadge(platform = detail.artist.platform, selected = true)
                                }
                                detail.artist.songCount?.let { InfoPill("$it 首歌曲") }
                                detail.artist.albumCount?.let { InfoPill("$it 张专辑") }
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

                    if (detail.albums.isNotEmpty()) {
                        item(key = "albums-title") {
                            SectionTitleRow(title = "专辑", subtitle = "${detail.albums.size} 张")
                        }
                        item(key = "albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = detail.albums,
                                    key = { "${it.key}-${it.name}" },
                                ) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = {
                                            val source = album.sources.firstOrNull { it.platform.isKnown }
                                            if (source != null) {
                                                navigator.openAlbum(source.platform, source.id, album.name)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "tracks-title") {
                        SectionTitleRow(title = "热门歌曲")
                    }

                    if (tracks.isEmpty()) {
                        item(key = "empty") {
                            StateMessage(
                                icon = Icons.Rounded.CloudOff,
                                title = "没有取到歌曲",
                                message = "上游可能限制了这首歌手的热门歌曲。",
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
                            subtitle = track.album.name.takeIf { it.isNotBlank() },
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
