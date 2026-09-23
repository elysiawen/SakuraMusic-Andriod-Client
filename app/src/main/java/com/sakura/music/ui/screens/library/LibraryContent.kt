package com.sakura.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.components.UserPlaylistRow
import com.sakura.music.ui.util.formatBytes
import com.sakura.music.ui.util.formatPlayedAt

/**
 * 音乐库三段内容需要的回调。
 *
 * 打包成一个对象：这是给 `LazyListScope` 用的，十几行参数列表会让调用点完全没法读。
 */
class LibraryActions(
    /** 播放当前页签的列表，从第几首开始——整段交给播放器当队列。 */
    val onPlay: (List<UnifiedTrack>, Int) -> Unit,
    val onMessage: (String) -> Unit,
    /** 打开「我喜欢的音乐」——收藏排在歌单页签的第一条。 */
    val onOpenFavorites: () -> Unit,
    val onOpenPlaylist: (Playlist) -> Unit,
    val onPlaylistMore: (Playlist) -> Unit,
    /** 加载失败后的重试。 */
    val onRetry: () -> Unit,
)

/**
 * 页签行：三个等宽色块，与原来的音乐库页完全一致。
 *
 * 因为是要钉在列表顶部的（sticky），底色由调用方负责，免得滚动时内容从下面透上来。
 */
@Composable
fun LibraryTabRow(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        LibraryTab.entries.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            LibraryTabChip(
                label = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 当前页签的内容，铺进外层列表。 */
fun LazyListScope.libraryTabContent(
    state: LibraryViewModel.UiState,
    actions: LibraryActions,
) {
    val tab = state.tab

    when {
        state.loading -> item(key = "library-loading") {
            LoadingState("正在加载…")
        }

        // 拉收藏/歌单/历史失败不该连累「本地」那一页：断网时那正是唯一还能用的。
        state.error != null && tab != LibraryTab.Local -> item(key = "library-error") {
            StateMessage(
                icon = Icons.Rounded.CloudOff,
                title = "加载失败",
                message = state.error + "\n\n下载好的歌不受影响，可以切到「本地」页签离线听。",
                actionLabel = "重试",
                onAction = actions.onRetry,
            )
        }

        else -> when (tab) {
            LibraryTab.Playlists -> {
                // 「我喜欢的音乐」和「新建歌单」始终在最上面，不受自建歌单是否为空影响：
                // 一条歌单都没有的时候，恰恰最需要看见这两个入口。
                item(key = "playlists-favorites") {
                    FavoritePlaylistRow(
                        count = state.favoriteCount,
                        onClick = actions.onOpenFavorites,
                    )
                    TrackDivider(16)
                }

                if (state.playlists.isEmpty()) {
                    item(key = "playlists-empty") {
                        StateMessage(
                            icon = Icons.Rounded.PlaylistAdd,
                            title = "还没有自建歌单",
                            message = "点右上角的「+」新建一个，把喜欢的歌收进来。",
                        )
                    }
                }

                itemsIndexed(
                    items = state.playlists,
                    key = { _, playlist -> "playlist-${playlist.id}" },
                ) { index, playlist ->
                    UserPlaylistRow(
                        playlist = playlist,
                        onClick = { actions.onOpenPlaylist(playlist) },
                        onMoreClick = { actions.onPlaylistMore(playlist) },
                    )
                    if (index != state.playlists.lastIndex) TrackDivider(16)
                }
            }

            LibraryTab.History -> if (state.history.isEmpty()) {
                item(key = "history-empty") {
                    StateMessage(
                        icon = Icons.Rounded.History,
                        title = "还没有播放记录",
                        message = "播放过的歌会自动记在这里，5 分钟内重复播放只刷新时间。",
                    )
                }
            } else {
                itemsIndexed(
                    items = state.history,
                    key = { index, track -> "history-$index:${track.key}" },
                ) { index, track ->
                    TrackRowContainer(
                        track = track,
                        index = index + 1,
                        subtitle = listOf(
                            track.artistText.ifBlank { "未知歌手" },
                            formatPlayedAt(track.playedAt).orEmpty(),
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        trailingText = formatPlayedAt(track.playedAt) ?: "",
                        onPlay = { actions.onPlay(state.history, index) },
                        onMessage = actions.onMessage,
                    )
                    if (index != state.history.lastIndex) TrackDivider()
                }
            }

            LibraryTab.Local -> if (state.local.isEmpty()) {
                item(key = "local-empty") {
                    StateMessage(
                        icon = Icons.Rounded.Download,
                        title = "还没有本地音乐",
                        message = "在歌曲的「更多」里点「下载到本地」，之后不用联网也能播。",
                    )
                }
            } else itemsIndexed(
                items = state.local,
                key = { _, item -> "local-${item.key}" },
            ) { index, item ->
                TrackRowContainer(
                    track = item.track,
                    index = index + 1,
                    // 副标题点出大小和音质：本地列表里「占多少」比播放时间有用。
                    subtitle = listOf(
                        item.track.artistText.ifBlank { "未知歌手" },
                        formatBytes(item.sizeBytes),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    onPlay = { actions.onPlay(state.local.map { it.track }, index) },
                    onMessage = actions.onMessage,
                )
                if (index != state.local.lastIndex) TrackDivider()
            }
        }
    }
}

/**
 * 歌单页签里的「我喜欢的音乐」。
 *
 * 和自建歌单排在一起，但它是固定的第一条：收藏在网关侧不是歌单（没有 id、不能改名），
 * 只能这样单独做成一个入口。
 */
@Composable
private fun FavoritePlaylistRow(
    count: Int,
    onClick: () -> Unit,
) {
    // 尺寸与右边距都对齐自建歌单那一行：两行挨着排，不能一个胖一个瘦。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 没有真实封面，就用强调色底上一个心形，比一行纯文字更像一个「歌单」。
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "我喜欢的音乐",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$count 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 音乐库页签：三个等宽色块，比 TabRow 更贴合这个应用的圆角语汇。 */
@Composable
private fun LibraryTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
