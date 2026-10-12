package com.sakura.music.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.Platform
import com.sakura.music.data.prefs.DetailHeaderStyle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AlbumCard
import com.sakura.music.ui.components.DetailAction
import com.sakura.music.ui.components.DetailHeaderData
import com.sakura.music.ui.components.DetailPage
import com.sakura.music.ui.components.InfoPill
import com.sakura.music.ui.components.SectionTitleRow
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
    val headerStyle by container.settings.detailHeaderStyle
        .collectAsStateWithLifecycle(initialValue = DetailHeaderStyle.Default)

    val current = state
    val detail = current.detail
    val tracks = detail?.items.orEmpty()

    // 头部版式与加载占位都是用户可选的（设置 → 外观）。
    val immersive = headerStyle == DetailHeaderStyle.Immersive

    // 页面骨架（顶栏 / 悬浮工具栏 / 骨架屏 / 两栏接线）在 DetailPage 里，五个详情页共用。
    DetailPage(
        immersive = immersive,
        loading = current.loading,
        ready = detail != null,
        error = current.error,
        errorTitle = "歌手页加载失败",
        onRetry = viewModel::load,
        onBack = navigator::back,
        initialTitle = initialName,
        subtitle = "歌手 · ${platform.label}",
        snackbar = snackbar,
        message = current.message,
        onMessageConsumed = viewModel::consumeMessage,
        actions = listOf(
            DetailAction(icon = Icons.Rounded.Refresh, label = "刷新", onClick = viewModel::load),
        ),
        header = DetailHeaderData(
            title = detail?.artist?.name?.takeIf { it.isNotBlank() } ?: initialName.ifBlank { "歌手" },
            coverUrl = detail?.artist?.avatar,
            label = "歌手 · ${platform.label}",
            subtitle = detail?.artist?.subtitle,
            stats = "热门歌曲 ${tracks.size} 首",
            badges = {
                detail?.artist?.songCount?.let { InfoPill("$it 首歌曲") }
                detail?.artist?.albumCount?.let { InfoPill("$it 张专辑") }
            },
            onPlayAll = { container.playbackCenter.play(tracks, 0) },
            playAllEnabled = tracks.isNotEmpty(),
            // 经典版式里头像用圆形。
            circularCover = true,
        ),
        // 一首都没取到：整页换成它（头部也不再显示）。
        absent = {
            StateMessage(
                icon = Icons.Rounded.CloudOff,
                title = "没有取到歌手信息",
                message = "上游可能暂时不可用。",
                actionLabel = "重试",
                onAction = viewModel::load,
            )
        },
        // 列表内容：头部不在里面——单栏时它是第一项，两栏时它是左栏。
        list = {
            val albums = detail?.albums.orEmpty()
            if (albums.isNotEmpty()) {
                item(key = "albums-title") {
                    SectionTitleRow(title = "专辑", subtitle = "${albums.size} 张")
                }
                item(key = "albums") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = albums,
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
        },
    )
}
