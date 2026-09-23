package com.sakura.music.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.CollectionDetail
import com.sakura.music.data.model.Platform
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.navigation.CollectionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 平台榜单 / 平台歌单详情。
 *
 * 两者在网关侧是两组接口（`/api/toplist/...` 与 `/api/collection/...`），
 * 返回结构一致，页面因此可以共用。
 */
class CollectionViewModel(
    private val api: SakuraApi,
    private val platform: Platform,
    private val collectionId: String,
    private val kind: CollectionKind,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val detail: CollectionDetail? = null,
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
            runCatching {
                when (kind) {
                    CollectionKind.TOPLIST -> api.toplist(platform, collectionId)
                    CollectionKind.PLAYLIST -> api.collection(platform, collectionId)
                }
            }
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
