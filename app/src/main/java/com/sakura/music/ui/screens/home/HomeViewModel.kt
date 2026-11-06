package com.sakura.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.model.DiscoverError
import com.sakura.music.data.model.DiscoverKind
import com.sakura.music.data.model.DiscoverSection
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.PlatformCapability
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
 * 首页两张电台卡片背后的分区 key（与网关 `/api/discover/feed` 一致）。
 *
 * 它们不能混进普通曲目轨道：卡片只把第一首画成封面，整批要交给播放器按顺序放完，
 * 最后一首开始播时再由电台接口续下一批。
 */
private const val NETEASE_FM_KEY = "netease-fm"
private const val QQ_GUESS_KEY = "qq-guess"
private val RADIO_SECTION_KEYS = setOf(NETEASE_FM_KEY, QQ_GUESS_KEY)

fun radioSectionKey(platform: Platform): String = when (platform) {
    Platform.NETEASE -> NETEASE_FM_KEY
    Platform.QQ -> QQ_GUESS_KEY
    else -> ""
}

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

class HomeViewModel(
    private val api: SakuraApi,
    private val platformPrefs: PlatformPrefsRepository,
) : ViewModel() {

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
        /** 电台分区单独画成顶部两张大卡片，不再走横向轨道。 */
        val trackSections: List<HomeSection>
            get() = sections.filter {
                it.kind == DiscoverKind.TRACKS && it.key !in RADIO_SECTION_KEYS
            }

        val radioSections: List<HomeSection>
            get() = sections.filter {
                it.kind == DiscoverKind.TRACKS && it.key in RADIO_SECTION_KEYS
            }

        val collectionSections: List<HomeSection>
            get() = sections.filter { it.kind != DiscoverKind.TRACKS }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        // 平台开关一变就换一批：关掉的平台不该继续留在当前这批区块里。
        viewModelScope.launch {
            platformPrefs.state.drop(1).collect {
                if (_state.value.loaded) load(force = true)
            }
        }
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
            runCatching { api.discoverFeed(platformPrefs.queryParam(PlatformCapability.DISCOVER)) }
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
