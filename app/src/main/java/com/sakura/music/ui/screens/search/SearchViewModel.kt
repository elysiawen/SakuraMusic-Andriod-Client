package com.sakura.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.PlatformStatus
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.SearchResult
import com.sakura.music.data.model.SearchType
import com.sakura.music.data.model.UnifiedAlbum
import com.sakura.music.data.model.UnifiedArtist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聚合搜索。
 *
 * 网关会把两个平台的结果混排、按名称去重合并，所以这里的四个列表总是存在——
 * 与当前 `type` 无关的三个是空的（不是缺字段）。
 */
class SearchViewModel(
    private val api: SakuraApi,
    private val settings: SettingsStore,
) : ViewModel() {

    data class UiState(
        val keyword: String = "",
        val type: SearchType = SearchType.SONG,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val page: Int = 1,
        val tracks: List<UnifiedTrack> = emptyList(),
        val artists: List<UnifiedArtist> = emptyList(),
        val albums: List<UnifiedAlbum> = emptyList(),
        val playlists: List<PlaylistSummary> = emptyList(),
        val platforms: List<PlatformStatus> = emptyList(),
        /** 已经搜过至少一次，用来区分「还没搜」和「搜了没结果」。 */
        val searched: Boolean = false,
        val canLoadMore: Boolean = false,
        val error: String? = null,
        val message: String? = null,
    ) {
        val isEmptyResult: Boolean
            get() = when (type) {
                SearchType.SONG -> tracks.isEmpty()
                SearchType.ARTIST -> artists.isEmpty()
                SearchType.ALBUM -> albums.isEmpty()
                SearchType.PLAYLIST -> playlists.isEmpty()
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 搜索记录，最近搜过的在最前。 */
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            settings.searchHistory.collect { _history.value = it }
        }
    }

    fun setKeyword(value: String) = _state.update { it.copy(keyword = value) }

    /** 点一条历史记录：填进输入框并立刻搜。 */
    fun searchKeyword(keyword: String) {
        if (keyword.isBlank()) return
        _state.update { it.copy(keyword = keyword) }
        search()
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { settings.removeSearch(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            settings.clearSearchHistory()
            _state.update { it.copy(message = "搜索记录已清空") }
        }
    }

    fun setType(type: SearchType) {
        if (_state.value.type == type) return
        _state.update {
            it.copy(
                type = type,
                tracks = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                platforms = emptyList(),
                canLoadMore = false,
                error = null,
                page = 1,
            )
        }
        // 已经搜过就换页签立刻重搜，不用再敲一次回车。
        if (_state.value.searched && _state.value.keyword.isNotBlank()) search()
    }

    fun search() {
        val keyword = _state.value.keyword.trim()
        if (keyword.isEmpty()) {
            _state.update { it.copy(message = "请输入搜索关键词") }
            return
        }
        val type = _state.value.type
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searched = true) }
            runCatching { api.search(keyword, type, page = 1, limit = PAGE_SIZE) }
                .onSuccess { result ->
                    // 搜出结果才记进历史：手滑打错、或是网络没通的那次不值得留。
                    settings.rememberSearch(keyword)
                    _state.update {
                        it.copy(
                            loading = false,
                            page = 1,
                            tracks = result.items,
                            artists = result.artists,
                            albums = result.albums,
                            playlists = result.playlists,
                            platforms = result.platforms,
                            canLoadMore = countOf(result) >= PAGE_SIZE,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.friendlyMessage(),
                            tracks = emptyList(),
                            artists = emptyList(),
                            albums = emptyList(),
                            playlists = emptyList(),
                            platforms = emptyList(),
                            canLoadMore = false,
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore || current.loadingMore || current.loading) return
        val keyword = current.keyword.trim()
        if (keyword.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val nextPage = current.page + 1
            runCatching { api.search(keyword, current.type, page = nextPage, limit = PAGE_SIZE) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            page = nextPage,
                            tracks = it.tracks + result.items,
                            artists = it.artists + result.artists,
                            albums = it.albums + result.albums,
                            playlists = it.playlists + result.playlists,
                            canLoadMore = countOf(result) >= PAGE_SIZE,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingMore = false, message = error.friendlyMessage()) }
                }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun countOf(result: SearchResult): Int = when (result.type) {
        SearchType.SONG -> result.items.size
        SearchType.ARTIST -> result.artists.size
        SearchType.ALBUM -> result.albums.size
        SearchType.PLAYLIST -> result.playlists.size
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
