package com.sakura.music.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.DetailHeaderStyle
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.DetailAction
import com.sakura.music.ui.components.DetailHeaderData
import com.sakura.music.ui.components.DetailPage
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.components.trackListStats
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 收藏。
 *
 * 「我喜欢的音乐」在网关侧不是歌单（没有 id、不能改名、不能排序），所以它单独占一页，
 * 点进来是这一页，而不是复用 [com.sakura.music.ui.screens.playlist.PlaylistScreen]。
 */
@Composable
fun FavoritesScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { FavoritesViewModel(it.libraryRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val headerStyle by container.settings.detailHeaderStyle
        .collectAsStateWithLifecycle(initialValue = DetailHeaderStyle.Default)

    // 头部版式与加载占位都是用户可选的（设置 → 外观）。
    val immersive = headerStyle == DetailHeaderStyle.Immersive

    // 页面骨架（顶栏 / 悬浮工具栏 / 骨架屏 / 两栏接线）在 DetailPage 里，五个详情页共用。
    DetailPage(
        immersive = immersive,
        loading = state.loading,
        // 收藏这一页的「有没有东西可看」就是「有没有收藏」，所以空列表等于还没就绪——
        // 于是它会走下面的 absent（整页空态），而不是画一个空头部。
        ready = state.tracks.isNotEmpty(),
        error = state.error,
        errorTitle = "收藏打不开",
        onRetry = viewModel::load,
        onBack = navigator::back,
        initialTitle = "我喜欢的音乐",
        subtitle = "收藏",
        snackbar = snackbar,
        actions = listOf(
            DetailAction(icon = Icons.Rounded.Refresh, label = "刷新", onClick = viewModel::load),
        ),
        header = DetailHeaderData(
            title = "我喜欢的音乐",
            // 封面取自最近收藏的那首（倒序第一条），收藏本身没有独立封面。
            coverUrl = state.tracks.firstOrNull()?.album?.cover,
            label = "收藏",
            stats = trackListStats(state.tracks),
            description = "在任意列表里点歌曲右侧的爱心，就会收藏到这里。",
            onPlayAll = { container.playbackCenter.playAll(state.tracks) },
        ),
        absent = {
            StateMessage(
                icon = Icons.Rounded.FavoriteBorder,
                title = "还没有收藏",
                message = "在任意列表里点歌曲右侧的爱心即可收藏，收藏只存在你的账号下。",
            )
        },
        // 列表内容：头部不在里面——单栏时它是第一项，两栏时它是左栏。
        list = {
            itemsIndexed(
                items = state.tracks,
                key = { index, track -> "favorite-$index:${track.key}" },
            ) { index, track ->
                TrackRowContainer(
                    track = track,
                    index = index + 1,
                    onPlay = { container.playbackCenter.play(state.tracks, index) },
                    onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                    // 行内那颗爱心留着（点一下就能取消收藏）；只把菜单里重复的
                    // 「取消收藏」拿掉。
                    showFavoriteAction = false,
                    onFavoriteToggled = { favorite ->
                        if (!favorite) viewModel.remove(track.key)
                    },
                )
                if (index != state.tracks.lastIndex) TrackDivider()
            }

            item(key = "bottom-gap") { Spacer(Modifier.height(ListBottomPadding)) }
        },
    )
}

/** 收藏列表：一份 tracks 加一个「取消了就撤掉这一行」的动作就够。 */
class FavoritesViewModel(
    private val library: LibraryRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val tracks: List<UnifiedTrack> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { library.favorites() }
                .onSuccess { list -> _state.update { it.copy(loading = false, tracks = list) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

    /** 在这一页取消了收藏；那一行当场拿掉，不用等重新加载。 */
    fun remove(key: String) {
        _state.update { it.copy(tracks = it.tracks.filterNot { track -> track.key == key }) }
    }
}
