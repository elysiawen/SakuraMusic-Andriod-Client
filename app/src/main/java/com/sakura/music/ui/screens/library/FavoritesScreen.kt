package com.sakura.music.ui.screens.library

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
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.MediaHeader
import com.sakura.music.ui.components.PlayAllButton
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackDivider
import com.sakura.music.ui.components.TrackRowContainer
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「我喜欢的音乐」——收藏。
 *
 * 收不进「自建歌单」那套：收藏在网关侧是 `/api/favorites` 一组独立的接口，
 * 没有歌单 id，也就不能改名、删除、排序。所以它是歌单页签里一个固定入口，
 * 点进来是这一页，而不是复用 [com.sakura.music.ui.screens.playlist.PlaylistScreen]。
 */
@Composable
fun FavoritesScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { FavoritesViewModel(it.libraryRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = "我喜欢的音乐",
                subtitle = "收藏",
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
                state.loading && state.tracks.isEmpty() -> LoadingState("正在加载…")

                state.error != null && state.tracks.isEmpty() -> StateMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "收藏打不开",
                    message = state.error,
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                state.tracks.isEmpty() -> StateMessage(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = "还没有收藏",
                    message = "在任意列表里点歌曲右侧的爱心即可收藏，收藏只存在你的账号下。",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    // 头部与自建歌单详情页同一套：大封面 + 数量 + 播放全部。
                    item(key = "header") {
                        MediaHeader(
                            title = "我喜欢的音乐",
                            subtitle = "${state.tracks.size} 首",
                            description = "在任意列表里点歌曲右侧的爱心，就会收藏到这里。",
                            coverUrl = state.tracks.firstOrNull()?.album?.cover,
                            actions = {
                                PlayAllButton(
                                    onClick = { container.playbackCenter.play(state.tracks, 0) },
                                )
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }

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
                }
            }
        }
    }
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
