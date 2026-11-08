package com.sakura.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlatformStatus
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.SearchResult
import com.sakura.music.data.model.SearchType
import com.sakura.music.data.model.UnifiedAlbum
import com.sakura.music.data.model.UnifiedArtist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.PlatformCapability
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.PlatformPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
    private val platformPrefs: PlatformPrefsRepository,
) : ViewModel() {

    data class UiState(
        val keyword: String = "",
        val type: SearchType = SearchType.SONG,
        /** 左侧平台维度：null = 「全部」，非空 = 只看这一平台。 */
        val selectedPlatform: Platform? = null,
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
        /*
         * 平台开关变了就重搜：否则页面还留着关闭前那批结果，看起来像没生效。
         * 选中态的归一化也放在这里，避免和自动重搜叠加成两次请求。
         */
        viewModelScope.launch {
            platformPrefs.state.drop(1).collect { prefs ->
                val enabled = Platform.known.filter { prefs.isEnabled(it, PlatformCapability.SEARCH) }
                val picked = _state.value.selectedPlatform
                // 选中的平台被关掉，或只剩一个平台不必再切时，回落到「全部」。
                if (picked != null && (enabled.size <= 1 || picked !in enabled)) {
                    _state.update { it.copy(selectedPlatform = null) }
                }
                if (_state.value.searched && _state.value.keyword.isNotBlank()) search()
            }
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
        // 作废在途的分页请求，并复位它的加载态：过期回调不会再动 state。
        searchGeneration++
        _state.update {
            it.copy(
                type = type,
                tracks = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                platforms = emptyList(),
                loadingMore = false,
                canLoadMore = false,
                error = null,
                page = 1,
            )
        }
        // 已经搜过就换页签立刻重搜，不用再敲一次回车。
        if (_state.value.searched && _state.value.keyword.isNotBlank()) search()
    }

    /** 左侧平台维度：照 setType 的模板清结果、回第一页，已搜过就重搜。 */
    fun setPlatform(platform: Platform?) {
        // 二次校验：被关掉的平台不能被程序化触发绕过设置开关。
        if (platform != null && !platformPrefs.isEnabled(platform, PlatformCapability.SEARCH)) return
        if (_state.value.selectedPlatform == platform) return
        searchGeneration++
        _state.update {
            it.copy(
                selectedPlatform = platform,
                tracks = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                platforms = emptyList(),
                loadingMore = false,
                canLoadMore = false,
                error = null,
                page = 1,
            )
        }
        if (_state.value.searched && _state.value.keyword.isNotBlank()) search()
    }

    /**
     * 平台页签只决定这一轮检索取哪些平台，不越过设置里的开关：
     * 「全部」用 queryParam（= 当前启用集合）；选中单平台则只传它，但必须仍启用。
     * 失效时回落「全部」——网关遇到非法参数会放大成全平台，不能指望它替我们守。
     */
    private fun effectivePlatforms(): String? {
        val fallback = platformPrefs.queryParam(PlatformCapability.SEARCH)
        val picked = _state.value.selectedPlatform ?: return fallback
        return if (platformPrefs.isEnabled(picked, PlatformCapability.SEARCH)) picked.id else fallback
    }

    /** 连点页签 / 开关变化时，只认最后一次请求的结果（桌面端 requestToken 同款）。 */
    private var searchGeneration = 0

    fun search() {
        val keyword = _state.value.keyword.trim()
        if (keyword.isEmpty()) {
            _state.update { it.copy(message = "请输入搜索关键词") }
            return
        }
        val type = _state.value.type
        // 参数在发请求前一次性定下来：切页签的瞬间不该让在途请求读到新的选中值。
        val platforms = effectivePlatforms()
        val generation = ++searchGeneration
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, loadingMore = false, error = null, searched = true)
            }
            runCatching {
                api.search(
                    keyword = keyword,
                    type = type,
                    page = 1,
                    limit = PAGE_SIZE,
                    platforms = platforms,
                )
            }
                .onSuccess { result ->
                    if (generation != searchGeneration) return@onSuccess
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
                    if (generation != searchGeneration) return@onFailure
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

        // 期间切了平台 / 关键词 / 类型，旧分页晚到就不能再追加到新列表里。
        val generation = searchGeneration
        val platforms = effectivePlatforms()
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val nextPage = current.page + 1
            runCatching {
                api.search(
                    keyword = keyword,
                    type = current.type,
                    page = nextPage,
                    limit = PAGE_SIZE,
                    platforms = platforms,
                )
            }
                .onSuccess { result ->
                    // 新一轮 search 已把 loadingMore 复位，过期分页不再碰状态。
                    if (generation != searchGeneration) return@onSuccess
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
                    if (generation != searchGeneration) return@onFailure
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
