package com.sakura.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.DiscoverError
import com.sakura.music.data.model.DiscoverKind
import com.sakura.music.data.model.DiscoverSection
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 发现页的一个分区：要么是一排歌，要么是一排歌单/榜单。 */
data class HomeSection(
    val key: String,
    val title: String,
    val subtitle: String?,
    val platform: Platform,
    val kind: DiscoverKind,
    val tracks: List<UnifiedTrack> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

class HomeViewModel(private val api: SakuraApi) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        /** 已经成功拿过一次，切页回来就不再重拉（推荐内容是上游随机给的，重拉会换一批）。 */
        val loaded: Boolean = false,
        val sections: List<HomeSection> = emptyList(),
        val errors: List<DiscoverError> = emptyList(),
        val error: String? = null,
        val message: String? = null,
    ) {
        val trackSections: List<HomeSection>
            get() = sections.filter { it.kind == DiscoverKind.TRACKS }

        val collectionSections: List<HomeSection>
            get() = sections.filter { it.kind != DiscoverKind.TRACKS }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    /** `force = true` 是「换一批」。 */
    fun load(force: Boolean = false) {
        val current = _state.value
        if (!force && current.loaded) return
        if (current.refreshing) return

        viewModelScope.launch {
            _state.update {
                it.copy(loading = !it.loaded, refreshing = it.loaded, error = null)
            }
            runCatching { api.discoverFeed() }
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loaded = true,
                            sections = feed.sections.map(::toSection),
                            errors = feed.errors,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = error.friendlyMessage())
                    }
                }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun toSection(section: DiscoverSection) = HomeSection(
        key = section.key,
        title = section.title,
        subtitle = section.subtitle,
        platform = section.platform,
        kind = section.kind,
        tracks = api.tracksOf(section),
        playlists = api.playlistsOf(section),
    )
}
