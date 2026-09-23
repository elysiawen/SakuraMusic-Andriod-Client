package com.sakura.music.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 自建歌单详情：可以改名、删除、移除单曲。 */
class PlaylistViewModel(
    private val library: LibraryRepository,
    private val playlistId: String,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val playlist: Playlist? = null,
        val tracks: List<UnifiedTrack> = emptyList(),
        val error: String? = null,
        val message: String? = null,
        /** 歌单被删掉之后，页面应该自己退出。 */
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { library.playlistDetail(playlistId) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(loading = false, playlist = detail.playlist, tracks = detail.items)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

    fun rename(name: String) {
        val current = _state.value.playlist ?: return
        viewModelScope.launch {
            runCatching { library.updatePlaylist(current.id, name.trim(), null) }
                .onSuccess {
                    _state.update {
                        it.copy(playlist = current.copy(name = name.trim()), message = "已重命名")
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun delete() {
        val current = _state.value.playlist ?: return
        viewModelScope.launch {
            runCatching { library.deletePlaylist(current.id) }
                .onSuccess { _state.update { it.copy(deleted = true, message = "歌单已删除") } }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun removeTrack(track: UnifiedTrack) {
        viewModelScope.launch {
            runCatching { library.removeTrack(playlistId, track) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            tracks = current.tracks.filterNot { it.key == track.key },
                            playlist = current.playlist?.let { playlist ->
                                playlist.copy(trackCount = (playlist.trackCount - 1).coerceAtLeast(0))
                            },
                            message = "已从歌单移除",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.friendlyMessage()) } }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
