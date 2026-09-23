package com.sakura.music.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.AlbumDetail
import com.sakura.music.data.model.ArtistDetail
import com.sakura.music.data.model.Platform
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 歌手详情。
 *
 * 详情接口本身就是「针对某一个平台」的——两个平台的歌手 ID 体系不同，
 * 所以这里要带上平台，而不是像搜索结果那样可以跨平台跳。
 */
class ArtistViewModel(
    private val api: SakuraApi,
    private val platform: Platform,
    private val artistId: String,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val detail: ArtistDetail? = null,
        val error: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.artist(platform, artistId) }
                .onSuccess { detail ->
                    _state.update { it.copy(loading = false, detail = detail) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** 专辑详情。 */
class AlbumViewModel(
    private val api: SakuraApi,
    private val platform: Platform,
    private val albumId: String,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val detail: AlbumDetail? = null,
        val error: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.album(platform, albumId) }
                .onSuccess { detail ->
                    _state.update { it.copy(loading = false, detail = detail) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
