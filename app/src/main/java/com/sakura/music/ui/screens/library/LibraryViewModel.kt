package com.sakura.music.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.downloads.DownloadCenter
import com.sakura.music.data.downloads.DownloadedTrack
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 音乐库的三个页签。
 *
 * 收藏不再单独占一个页签：它现在是「歌单」里的第一条（我喜欢的音乐），
 * 翻起来和别的歌单是同一个位置。
 */
enum class LibraryTab(val label: String) {
    Playlists("歌单"),
    History("历史"),
    /** 下载到本地的歌；数据全在本机，不向网关要。 */
    Local("本地"),
}

/**
 * 个人音乐库：收藏 / 自建歌单 / 播放历史。
 *
 * 三份数据都在网关自己的库里，与两个第三方平台独立，所以它同时也是「换机器也不丢」的那部分。
 * 展示形态是三个页签切换（现在整块排在账号页下面），所以这里也按页签来加载：
 * 每个页签各自记一次「拉过了」，来回切不会重复请求。
 */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val downloads: DownloadCenter,
) : ViewModel() {

    data class UiState(
        val tab: LibraryTab = LibraryTab.Playlists,
        val loading: Boolean = false,
        val playlists: List<Playlist> = emptyList(),
        val history: List<UnifiedTrack> = emptyList(),
        /** 本地已下载的歌，最新下载的在前。 */
        val local: List<DownloadedTrack> = emptyList(),
        /** 收藏了多少首：歌单页签里那条「我喜欢的音乐」要显示它。 */
        val favoriteCount: Int = 0,
        val error: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 每个页签各自记住是否已经拉过，切回来就不重复请求。 */
    private val loadedTabs = mutableSetOf<LibraryTab>()

    init {
        load(LibraryTab.Playlists)
        // 本地那页随下载进度自己更新：下载完成、删除都要立刻反映到列表上。
        viewModelScope.launch {
            downloads.downloads.collect { list ->
                _state.update { it.copy(local = list) }
            }
        }
        // 收藏数在别处（歌曲行、播放器）也会变，跟着 key 集合走。
        viewModelScope.launch {
            library.favoriteKeys.collect { keys ->
                _state.update { it.copy(favoriteCount = keys.size) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        val changed = _state.value.tab != tab
        if (changed) _state.update { it.copy(tab = tab, error = null) }
        // 本地这页读的是本机数据，来回切就顺手刷新一次，成本可以忽略。
        if (tab == LibraryTab.Local) load(tab, force = true) else if (tab !in loadedTabs) load(tab)
    }

    /** 顶栏刷新：重新拉当前页签。 */
    fun refresh() = load(_state.value.tab, force = true)

    fun load(tab: LibraryTab = _state.value.tab, force: Boolean = false) {
        if (!force && tab in loadedTabs) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (tab) {
                LibraryTab.Playlists -> runCatching {
                    // 顺手刷一次收藏的 key 集合：红心状态是全局共享的。
                    library.refreshFavorites()
                    library.playlists()
                }
                    .onSuccess { list ->
                        loadedTabs += tab
                        _state.update { it.copy(loading = false, playlists = list) }
                    }
                    .onFailure(::fail)

                LibraryTab.History -> runCatching { library.history() }
                    .onSuccess { list ->
                        loadedTabs += tab
                        _state.update { it.copy(loading = false, history = list) }
                    }
                    .onFailure(::fail)

                LibraryTab.Local -> {
                    loadedTabs += tab
                    _state.update { it.copy(loading = false, local = downloads.downloads.value) }
                }
            }
        }
    }

    private fun fail(error: Throwable) {
        _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            runCatching { library.createPlaylist(name.trim(), null) }
                .onSuccess { created ->
                    loadedTabs += LibraryTab.Playlists
                    _state.update {
                        it.copy(
                            playlists = listOf(created) + it.playlists,
                            message = "已创建「${created.name}」",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        viewModelScope.launch {
            runCatching { library.updatePlaylist(playlist.id, name.trim(), null) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            playlists = current.playlists.map {
                                if (it.id == playlist.id) it.copy(name = name.trim()) else it
                            },
                            message = "已重命名",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            runCatching { library.deletePlaylist(playlist.id) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            playlists = current.playlists.filterNot { it.id == playlist.id },
                            message = "已删除「${playlist.name}」",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { library.clearHistory() }
                .onSuccess {
                    _state.update { it.copy(history = emptyList(), message = "播放历史已清空") }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
