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

/** 自建歌单详情：可以改信息（名称 + 描述）、删除、移除单曲。 */
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

    /**
     * 改名称与描述。
     *
     * 两个字段走的是**同一次** `PATCH`：拆成两次请求的话，用户同时改了名称和描述、其中一次
     * 失败，歌单就会停在一个只改了一半的状态。描述提交空串 = 清掉它（省略字段才是保持原值）。
     */
    fun updateInfo(name: String, description: String) {
        val current = _state.value.playlist ?: return
        val newName = name.trim()
        val newDescription = description.trim()
        viewModelScope.launch {
            runCatching { library.updatePlaylist(current.id, newName, newDescription) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            // 就地更新，不必为一个名称再拉一次详情。
                            playlist = current.copy(name = newName, description = newDescription),
                            message = "歌单信息已更新",
                        )
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
