package com.sakura.music.data.remote

import com.sakura.music.data.cache.ResponseCache
import com.sakura.music.data.model.AddTrackResponse
import com.sakura.music.data.model.AlbumDetail
import com.sakura.music.data.model.ArtistDetail
import com.sakura.music.data.model.CollectionDetail
import com.sakura.music.data.model.DiscoverFeed
import com.sakura.music.data.model.DiscoverKind
import com.sakura.music.data.model.DiscoverSection
import com.sakura.music.data.model.FavoriteResponse
import com.sakura.music.data.model.FavoritesResponse
import com.sakura.music.data.model.HealthResponse
import com.sakura.music.data.model.LyricResult
import com.sakura.music.data.model.MeResponse
import com.sakura.music.data.model.OkResponse
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlatformListResponse
import com.sakura.music.data.model.PlayResolveResult
import com.sakura.music.data.model.PlaylistDetailResponse
import com.sakura.music.data.model.PlaylistListResponse
import com.sakura.music.data.model.PlaylistResponse
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.PlaylistSummaryListResponse
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.QualitySerializer
import com.sakura.music.data.model.RemoveTrackResponse
import com.sakura.music.data.model.SearchResult
import com.sakura.music.data.model.SearchType
import com.sakura.music.data.model.TrackDetail
import com.sakura.music.data.model.TrackListResponse
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.model.UserResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * 网关的 40 个端点里，Android 端用得到的那些。
 *
 * 扫码绑定（`/api/bind` 与 `/api/credentials` 两组接口）刻意没有实现：那套流程请走网页端或
 * 桌面端；[com.sakura.music.data.session.LocalCredentialStore] 仍然会在请求上带上
 * 本机凭据，所以「在别处绑成本机模式」的用户在这里照样能用。
 */
class SakuraApi(
    private val client: ApiClient,
    private val json: Json,
    /** 歌词单独一桶，和「歌曲/专辑信息」分开，设置页才能分别报出各占多少。 */
    private val lyricCache: ResponseCache? = null,
    private val metaCache: ResponseCache? = null,
    /**
     * 有效期由用户在设置里定，所以做成取值函数而不是常量：
     * 每次请求现取一次，改完设置立刻生效。
     */
    private val lyricTtlMillis: () -> Long = { DEFAULT_LYRIC_TTL_MILLIS },
    private val metaTtlMillis: () -> Long = { DEFAULT_META_TTL_MILLIS },
) {

    companion object {
        /**
         * 这两类内容取过一次就不该再取：歌词发出来之后几乎不会变，
         * 歌曲/专辑/歌手信息也只在上游修正元数据时才会动。
         * 缓存不是永久的——留个期限，上游改了内容也终会刷新。
         */
        const val DEFAULT_LYRIC_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000  // 30 天
        const val DEFAULT_META_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000    // 7 天
    }

    /* ------------------------------ 探活 ------------------------------ */

    /**
     * 探活。
     *
     * [baseUrl] 用于「测试连接」：地址还没保存，先拿用户刚敲的地址试一次。
     */
    suspend fun health(baseUrl: String? = null): HealthResponse =
        client.request(
            method = "GET",
            path = "/api/health",
            deserializer = HealthResponse.serializer(),
            baseUrlOverride = baseUrl,
        )

    suspend fun platforms(): PlatformListResponse =
        client.request("GET", "/api/platforms", PlatformListResponse.serializer())

    /* ------------------------------ 账户 ------------------------------ */

    /** 未登录时返回 200 + `user: null`，不要拿它当鉴权探针。 */
    suspend fun me(): MeResponse =
        client.request("GET", "/api/auth/me", MeResponse.serializer())

    suspend fun login(username: String, password: String): UserResponse =
        client.request(
            method = "POST",
            path = "/api/auth/login",
            deserializer = UserResponse.serializer(),
            body = buildJsonObject {
                put("username", username)
                put("password", password)
            },
        )

    suspend fun register(username: String, password: String, nickname: String?): UserResponse =
        client.request(
            method = "POST",
            path = "/api/auth/register",
            deserializer = UserResponse.serializer(),
            body = buildJsonObject {
                put("username", username)
                put("password", password)
                nickname?.takeIf { it.isNotBlank() }?.let { put("nickname", it) }
            },
        )

    suspend fun logout(): OkResponse =
        client.request("POST", "/api/auth/logout", OkResponse.serializer())

    /**
     * 改资料。
     *
     * `avatar` 需要区分「不改」与「清空」：省略字段表示不改，显式 `null` 表示回到渐变头像，
     * 所以这里手搓 JSON——关闭 explicitNulls 的 Json 帮不上忙。
     */
    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        clearAvatar: Boolean = false,
    ): UserResponse {
        val body = buildJsonObject {
            nickname?.takeIf { it.isNotBlank() }?.let { put("nickname", it) }
            when {
                clearAvatar -> put("avatar", JsonNull)
                avatar != null -> put("avatar", avatar)
            }
        }
        return client.request("PATCH", "/api/auth/profile", UserResponse.serializer(), body = body)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): OkResponse =
        client.request(
            method = "POST",
            path = "/api/auth/password",
            deserializer = OkResponse.serializer(),
            body = buildJsonObject {
                put("oldPassword", oldPassword)
                put("newPassword", newPassword)
            },
        )

    /* ------------------------------ 音乐 ------------------------------ */

    suspend fun search(
        keyword: String,
        type: SearchType = SearchType.SONG,
        page: Int = 1,
        limit: Int = 20,
    ): SearchResult = client.request(
        method = "GET",
        path = "/api/search",
        deserializer = SearchResult.serializer(),
        query = mapOf(
            "keyword" to keyword,
            "type" to type.id,
            "page" to page,
            "limit" to limit,
        ),
    )

    suspend fun track(platform: Platform, id: String): TrackDetail = client.request(
        method = "GET",
        path = "/api/track/${platform.id}/${encode(id)}",
        deserializer = TrackDetail.serializer(),
        cache = metaCache,
        cacheTtlMillis = metaTtlMillis(),
    )

    suspend fun lyric(platform: Platform, id: String): LyricResult = client.request(
        method = "GET",
        path = "/api/track/${platform.id}/${encode(id)}/lyric",
        deserializer = LyricResult.serializer(),
        cache = lyricCache,
        cacheTtlMillis = lyricTtlMillis(),
    )

    suspend fun resolvePlay(platform: Platform, id: String, quality: Quality): PlayResolveResult =
        client.request(
            method = "POST",
            path = "/api/play/resolve",
            deserializer = PlayResolveResult.serializer(),
            body = buildJsonObject {
                put("platform", platform.id)
                put("id", id)
                put("quality", json.encodeToJsonElement(QualitySerializer, quality))
            },
        )

    suspend fun discoverFeed(): DiscoverFeed =
        client.request("GET", "/api/discover/feed", DiscoverFeed.serializer())

    suspend fun toplists(platform: Platform): PlaylistSummaryListResponse = client.request(
        method = "GET",
        path = "/api/toplists",
        deserializer = PlaylistSummaryListResponse.serializer(),
        query = mapOf("platform" to platform.id),
    )

    suspend fun toplist(
        platform: Platform,
        id: String,
        page: Int = 1,
        limit: Int = 100,
    ): CollectionDetail = client.request(
        method = "GET",
        path = "/api/toplist/${platform.id}/${encode(id)}",
        deserializer = CollectionDetail.serializer(),
        query = mapOf("page" to page, "limit" to limit),
    )

    suspend fun collection(
        platform: Platform,
        id: String,
        page: Int = 1,
        limit: Int = 100,
    ): CollectionDetail = client.request(
        method = "GET",
        path = "/api/collection/${platform.id}/${encode(id)}",
        deserializer = CollectionDetail.serializer(),
        query = mapOf("page" to page, "limit" to limit),
    )

    suspend fun artist(platform: Platform, id: String): ArtistDetail = client.request(
        method = "GET",
        path = "/api/artist/${platform.id}/${encode(id)}",
        deserializer = ArtistDetail.serializer(),
        cache = metaCache,
        cacheTtlMillis = metaTtlMillis(),
    )

    suspend fun album(platform: Platform, id: String): AlbumDetail = client.request(
        method = "GET",
        path = "/api/album/${platform.id}/${encode(id)}",
        deserializer = AlbumDetail.serializer(),
        cache = metaCache,
        cacheTtlMillis = metaTtlMillis(),
    )

    /**
     * 发现页分区的 `items` 是异构的，按 `kind` 二次解析。
     * 单条解析失败就跳过，不让一条脏数据毁掉整个分区。
     */
    fun tracksOf(section: DiscoverSection): List<UnifiedTrack> {
        if (section.kind != DiscoverKind.TRACKS) return emptyList()
        return section.items.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(UnifiedTrack.serializer(), element) }.getOrNull()
        }
    }

    fun playlistsOf(section: DiscoverSection): List<PlaylistSummary> {
        if (section.kind == DiscoverKind.TRACKS) return emptyList()
        return section.items.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(PlaylistSummary.serializer(), element) }.getOrNull()
        }
    }

    /* ------------------------------ 个人音乐库 ------------------------------ */

    suspend fun playlists(): PlaylistListResponse =
        client.request("GET", "/api/playlists", PlaylistListResponse.serializer())

    suspend fun createPlaylist(name: String, description: String?): PlaylistResponse =
        client.request(
            method = "POST",
            path = "/api/playlists",
            deserializer = PlaylistResponse.serializer(),
            body = buildJsonObject {
                put("name", name)
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
            },
        )

    suspend fun playlist(id: String): PlaylistDetailResponse = client.request(
        method = "GET",
        path = "/api/playlists/${encode(id)}",
        deserializer = PlaylistDetailResponse.serializer(),
    )

    suspend fun updatePlaylist(id: String, name: String?, description: String?): OkResponse =
        client.request(
            method = "PATCH",
            path = "/api/playlists/${encode(id)}",
            deserializer = OkResponse.serializer(),
            body = buildJsonObject {
                name?.let { put("name", it) }
                description?.let { put("description", it) }
            },
        )

    suspend fun deletePlaylist(id: String): OkResponse = client.request(
        method = "DELETE",
        path = "/api/playlists/${encode(id)}",
        deserializer = OkResponse.serializer(),
    )

    suspend fun addTrackToPlaylist(id: String, track: UnifiedTrack): AddTrackResponse =
        client.request(
            method = "POST",
            path = "/api/playlists/${encode(id)}/tracks",
            deserializer = AddTrackResponse.serializer(),
            body = buildJsonObject { put("track", trackJson(track)) },
        )

    suspend fun removeTrackFromPlaylist(
        id: String,
        platform: Platform,
        trackId: String,
    ): RemoveTrackResponse = client.request(
        method = "DELETE",
        path = "/api/playlists/${encode(id)}/tracks/${platform.id}/${encode(trackId)}",
        deserializer = RemoveTrackResponse.serializer(),
    )

    suspend fun favorites(): FavoritesResponse =
        client.request("GET", "/api/favorites", FavoritesResponse.serializer())

    suspend fun setFavorite(track: UnifiedTrack, favorite: Boolean): FavoriteResponse =
        client.request(
            method = "POST",
            path = "/api/favorites",
            deserializer = FavoriteResponse.serializer(),
            body = buildJsonObject {
                put("track", trackJson(track))
                put("favorite", favorite)
            },
        )

    suspend fun removeFavorite(platform: Platform, trackId: String): OkResponse = client.request(
        method = "DELETE",
        path = "/api/favorites/${platform.id}/${encode(trackId)}",
        deserializer = OkResponse.serializer(),
    )

    suspend fun history(limit: Int = 100): TrackListResponse = client.request(
        method = "GET",
        path = "/api/history",
        deserializer = TrackListResponse.serializer(),
        query = mapOf("limit" to limit),
    )

    suspend fun recordPlay(track: UnifiedTrack): OkResponse = client.request(
        method = "POST",
        path = "/api/history",
        deserializer = OkResponse.serializer(),
        body = buildJsonObject { put("track", trackJson(track)) },
    )

    suspend fun clearHistory(): OkResponse = client.request(
        method = "DELETE",
        path = "/api/history",
        deserializer = OkResponse.serializer(),
    )

    /* ------------------------------ 工具 ------------------------------ */

    /** 写库时把整首歌原样回传即可；这里只补一个库端会算的 key。 */
    private fun trackJson(track: UnifiedTrack): JsonElement =
        json.encodeToJsonElement(UnifiedTrack.serializer(), track)

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
