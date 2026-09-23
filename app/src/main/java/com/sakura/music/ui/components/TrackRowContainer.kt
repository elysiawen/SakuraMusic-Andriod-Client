package com.sakura.music.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.appContainer
import kotlinx.coroutines.launch

/**
 * 列表里的「一行歌」——把 [TrackRow] 与它背后的一整套动作接起来。
 *
 * 收藏、下一首播放、加入歌单、切换音源全都一样，所有列表页共用这一个入口，
 * 页面只需要说「点了这一首要干什么」。
 *
 * @param onPlay 点整行的行为（一般是把所在列表整段交给播放器）。
 * @param onMessage 需要给用户一句反馈时回调（页面负责弹 snackbar）。
 */
@Composable
fun TrackRowContainer(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    subtitle: String? = null,
    trailingText: String? = null,
    showFavorite: Boolean = true,
    /**
     * 操作菜单里要不要「收藏」这一项。
     *
     * 行内那个爱心已经是一键收藏了，有些列表（比如搜索结果）里菜单再放一条就重复，
     * 所以和 [showFavorite] 分开控制。
     */
    showFavoriteAction: Boolean = true,
    showPlatform: Boolean = true,
    /** 追加到操作菜单里的动作，例如「从歌单移除」。 */
    extraActions: List<SheetAction> = emptyList(),
    /** 收藏状态变化后的回调：收藏列表靠它把取消收藏的那一行拿走。 */
    onFavoriteToggled: ((Boolean) -> Unit)? = null,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val favorites by container.libraryRepository.favoriteKeys.collectAsStateWithLifecycle()
    val playback by container.playbackCenter.state.collectAsStateWithLifecycle()

    var showActions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val isFavorite = favorites.contains(track.key)
    val isCurrent = playback.current?.key == track.key
    // 正在播的那一首要标的是「实际在用的音源」，其余行标的是主来源。
    val badge = if (isCurrent) playback.activePlatform else track.primarySource?.platform

    val toggleFavorite: () -> Unit = {
        scope.launch {
            runCatching { container.libraryRepository.toggleFavorite(track) }
                .onSuccess { favorite ->
                    onMessage(if (favorite) "已收藏" else "已取消收藏")
                    onFavoriteToggled?.invoke(favorite)
                }
                .onFailure { onMessage(it.friendlyMessage()) }
        }
    }

    TrackRow(
        track = track,
        onClick = onPlay,
        modifier = modifier,
        index = index,
        isCurrent = isCurrent,
        isPlaying = isCurrent && playback.isPlaying,
        isFavorite = isFavorite,
        showFavorite = showFavorite,
        activePlatform = badge?.takeIf { showPlatform },
        subtitle = subtitle,
        trailingText = trailingText,
        onFavoriteClick = if (showFavorite) toggleFavorite else null,
        onMoreClick = { showActions = true },
    )

    if (showActions) {
        TrackActionsSheet(
            track = track,
            isFavorite = isFavorite,
            activePlatform = playback.activePlatform ?: Platform.UNKNOWN,
            onDismiss = { showActions = false },
            onToggleFavorite = if (showFavorite && showFavoriteAction) toggleFavorite else null,
            onAddToPlaylist = { showAddToPlaylist = true },
            // 切换音源只对正在播的那一首有意义。
            onSwitchPlatform = if (isCurrent) {
                { platform ->
                    container.playbackCenter.switchPlatform(platform)
                    onMessage("已切换到 ${platform.label}")
                }
            } else {
                null
            },
            onPlayNext = {
                container.playbackCenter.playNext(track)
                onMessage("已插入下一首播放")
            },
            extraActions = extraActions,
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            track = track,
            onMessage = onMessage,
            onDismiss = { showAddToPlaylist = false },
        )
    }
}
