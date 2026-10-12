package com.sakura.music.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.CollectionDetail
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
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
            runCatching { loadAllPages() }
                .onSuccess { detail ->
                    _state.update { it.copy(loading = false, detail = detail) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

    /**
     * 把曲目一次取全。
     *
     * 网关**单页最多 200 首**（文档 §3.4：`limit` 默认 100、最大 200），而 QQ 热歌榜有 300 首——
     * 只取第一页的话，列表卡片上写着 300 首、滑到底却只有 100 首。所以按页取到拿满为止：
     * 某一页返回的条数不足一整页，就说明到底了。
     *
     * 拿多少显示多少：曲目数与头部那行统计因此总是对得上（统计就是按曲目算的）。
     */
    private suspend fun loadAllPages(): CollectionDetail {
        val items = mutableListOf<UnifiedTrack>()
        var detail: CollectionDetail? = null

        for (page in 1..MaxPages) {
            val chunk = fetchPage(page)
            if (detail == null) detail = chunk
            items += chunk.items
            if (chunk.items.size < PageSize) break
        }

        // 循环至少跑过一次，第一页一定拿到了。
        return checkNotNull(detail).copy(items = items.toList(), trackCount = items.size)
    }

    private suspend fun fetchPage(page: Int): CollectionDetail = when (kind) {
        CollectionKind.TOPLIST -> api.toplist(platform, collectionId, page = page, limit = PageSize)
        CollectionKind.PLAYLIST -> api.collection(platform, collectionId, page = page, limit = PageSize)
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** 网关的单页上限（`gateway/src/routes/music.ts` 里那个 `Math.min(200, …)`）。 */
        const val PageSize = 200

        /** 兜底：最多 5 页（1000 首）。真遇上异常数据也不至于一直翻下去。 */
        const val MaxPages = 5
    }
}
