package com.sakura.music.data.repo

import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.SakuraApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 个人音乐库（收藏 / 自建歌单 / 播放历史）。
 *
 * 这些数据存在网关自己的库里，与两个第三方平台完全独立。
 * 收藏的 key 集合被多处同时使用（搜索列表、播放器、音乐库页），所以放在这里共享一份，
 * 而不是每个页面各查一次。
 */
class LibraryRepository(
    private val api: SakuraApi,
    private val auth: AuthRepository,
) {

    private val _favoriteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    fun isFavorite(key: String): Boolean = _favoriteKeys.value.contains(key)

    /** 登录后刷新一次；登出时由 [clear] 清干净。 */
    suspend fun refreshFavorites() {
        if (!auth.state.value.isLoggedIn) {
            _favoriteKeys.value = emptySet()
            return
        }
        _favoritesLoading.value = true
        runCatching { api.favorites() }
            .onSuccess { _favoriteKeys.value = it.keys.toSet() }
            .onFailure { /* 拿不到就保持现状，红心状态宁可不刷新也不要错乱 */ }
        _favoritesLoading.value = false
    }

    suspend fun favorites(): List<UnifiedTrack> =
        api.favorites().items

    /** 返回切换后的状态；服务端是幂等的，重复收藏不会报错。 */
    suspend fun toggleFavorite(track: UnifiedTrack): Boolean {
        val next = !isFavorite(track.key)
        api.setFavorite(track, next)
        _favoriteKeys.update { keys ->
            if (next) keys + track.key else keys - track.key
        }
        return next
    }

    suspend fun playlists(): List<Playlist> = api.playlists().items

    suspend fun createPlaylist(name: String, description: String?): Playlist =
        api.createPlaylist(name, description).playlist

    suspend fun playlistDetail(id: String) = api.playlist(id)

    suspend fun updatePlaylist(id: String, name: String?, description: String?) {
        api.updatePlaylist(id, name, description)
    }

    suspend fun deletePlaylist(id: String) {
        api.deletePlaylist(id)
    }

    suspend fun addTrack(playlistId: String, track: UnifiedTrack): Boolean =
        api.addTrackToPlaylist(playlistId, track).added

    suspend fun removeTrack(playlistId: String, track: UnifiedTrack) {
        val source = track.primarySource ?: return
        api.removeTrackFromPlaylist(playlistId, source.platform, source.id)
    }

    suspend fun history(): List<UnifiedTrack> = api.history().items

    suspend fun clearHistory() {
        api.clearHistory()
    }

    /** 记一次播放；网关会做 5 分钟去重，这里不重复判重。 */
    suspend fun recordPlay(track: UnifiedTrack) {
        if (!auth.state.value.isLoggedIn) return
        api.recordPlay(track)
    }

    suspend fun clear() {
        _favoriteKeys.value = emptySet()
    }
}
