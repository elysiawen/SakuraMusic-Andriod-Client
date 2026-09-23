package com.sakura.music.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

/* ------------------------------ 枚举 ------------------------------ */

/**
 * 平台标识。
 *
 * 用字符串自定义序列化而不是 [SerialName] 枚举：上游将来多出一个平台时，
 * 反序列化不会整条响应炸掉，只会退化成 [UNKNOWN]。
 */
@Serializable(with = PlatformSerializer::class)
enum class Platform(val id: String, val label: String) {
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ 音乐"),
    UNKNOWN("unknown", "未知平台");

    val isKnown: Boolean get() = this != UNKNOWN

    companion object {
        fun fromId(id: String?): Platform =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

object PlatformSerializer : KSerializer<Platform> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Platform", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Platform = Platform.fromId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Platform) = encoder.encodeString(value.id)
}

/** 音质档位。请求用枚举，响应里的 `quality` 用字符串（网关会降级到未知档位）。 */
@Serializable(with = QualitySerializer::class)
enum class Quality(val id: String, val label: String) {
    STANDARD("standard", "标准"),
    HIGH("high", "高品"),
    LOSSLESS("lossless", "无损"),
    HIRES("hires", "Hi-Res");

    companion object {
        val Default = HIGH

        fun fromId(id: String?): Quality =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Default
    }
}

object QualitySerializer : KSerializer<Quality> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Quality", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Quality = Quality.fromId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Quality) = encoder.encodeString(value.id)
}

/** 搜索页的类型页签。 */
@Serializable
enum class SearchType(val id: String, val label: String) {
    @SerialName("song")
    SONG("song", "单曲"),

    @SerialName("artist")
    ARTIST("artist", "歌手"),

    @SerialName("album")
    ALBUM("album", "专辑"),

    @SerialName("playlist")
    PLAYLIST("playlist", "歌单");

    companion object {
        fun fromId(id: String?): SearchType =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: SONG
    }
}

/** 发现页分区里装载的是歌曲还是歌单/榜单。 */
@Serializable
enum class DiscoverKind {
    @SerialName("tracks")
    TRACKS,

    @SerialName("playlists")
    PLAYLISTS,

    @SerialName("toplists")
    TOPLISTS;

    companion object {
        fun fromId(id: String?): DiscoverKind =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: TRACKS
    }
}

/* ------------------------------ 歌曲 ------------------------------ */

@Serializable
data class ArtistRef(
    val name: String = "",
    /** 平台内歌手 ID，用于跳转歌手页。 */
    val id: String? = null,
    /** `id` 所属平台；两个平台的 ID 体系不同，必须按它拼接。 */
    val platform: Platform? = null,
)

@Serializable
data class TrackSource(
    val platform: Platform = Platform.UNKNOWN,
    /** 网易云为数字 ID，QQ 音乐为歌曲 mid。 */
    val id: String = "",
    val mid: String? = null,
    /** QQ 音乐数字 ID（部分写操作需要）。 */
    val numericId: String? = null,
)

@Serializable
data class AlbumRef(
    val name: String = "",
    val cover: String? = null,
    val id: String? = null,
    val platform: Platform? = null,
)

/**
 * 跨平台合并后的歌曲。
 *
 * [key] 是活动期间的唯一标识：列表去重、播放队列判等、收藏红心状态全靠它。
 */
@Serializable
data class UnifiedTrack(
    val key: String = "",
    val title: String = "",
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef = AlbumRef(),
    val durationMs: Long = 0L,
    /** 各平台来源，顺序即合并时的优先级，`sources[0]` 为主来源。 */
    val sources: List<TrackSource> = emptyList(),
    /** 是否付费 / 会员专享。 */
    val vip: Boolean? = null,
    /** 只有播放历史里会带上。 */
    val playedAt: String? = null,
) {
    val artistText: String
        get() = artists.mapNotNull { it.name.trim().ifEmpty { null } }.joinToString(" / ")

    val primarySource: TrackSource? get() = sources.firstOrNull()

    fun sourceOf(platform: Platform): TrackSource? =
        sources.firstOrNull { it.platform == platform }
}

/* ------------------------------ 歌单 / 榜单 ------------------------------ */

@Serializable
data class PlaylistSummary(
    val platform: Platform = Platform.UNKNOWN,
    val id: String = "",
    val title: String = "",
    val cover: String? = null,
    val description: String? = null,
    val trackCount: Int? = null,
)

@Serializable
data class CollectionDetail(
    val platform: Platform = Platform.UNKNOWN,
    val id: String = "",
    val title: String = "",
    val cover: String? = null,
    val description: String? = null,
    val trackCount: Int? = null,
    val items: List<UnifiedTrack> = emptyList(),
)

/** 用户自建歌单。 */
@Serializable
data class Playlist(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    /** 取歌单里最近加入那首歌的封面，可能为空。 */
    val cover: String? = null,
    val trackCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/* ------------------------------ 歌手 / 专辑 ------------------------------ */

/** 某个歌手 / 专辑在单个平台上的坐标，用于渲染可跳转的平台徽标。 */
@Serializable
data class MediaSource(
    val platform: Platform = Platform.UNKNOWN,
    val id: String = "",
)

/** 单平台歌手条目：歌手详情页返回的模型。 */
@Serializable
data class PlatformArtist(
    val platform: Platform = Platform.UNKNOWN,
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val subtitle: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
)

/** 单平台专辑条目：专辑详情页返回的模型。 */
@Serializable
data class PlatformAlbum(
    val platform: Platform = Platform.UNKNOWN,
    val id: String = "",
    val name: String = "",
    val cover: String? = null,
    val artists: List<ArtistRef> = emptyList(),
    val releaseDate: String? = null,
    val trackCount: Int? = null,
)

/** 合并后的歌手：同名歌手只占一张卡片，`sources` 里是各平台入口。 */
@Serializable
data class UnifiedArtist(
    val key: String = "",
    val name: String = "",
    val avatar: String? = null,
    val subtitle: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
    val sources: List<MediaSource> = emptyList(),
)

/** 合并后的专辑：专辑名与首位歌手都相同才算同一张。 */
@Serializable
data class UnifiedAlbum(
    val key: String = "",
    val name: String = "",
    val cover: String? = null,
    val artists: List<ArtistRef> = emptyList(),
    val releaseDate: String? = null,
    val trackCount: Int? = null,
    val sources: List<MediaSource> = emptyList(),
) {
    val artistText: String
        get() = artists.mapNotNull { it.name.trim().ifEmpty { null } }.joinToString(" / ")
}

/* ------------------------------ 搜索 ------------------------------ */

@Serializable
data class PlatformStatus(
    val platform: Platform = Platform.UNKNOWN,
    val ok: Boolean = false,
    /** 合并去重前该平台返回的条数，可能大于最终数组长度。 */
    val count: Int = 0,
    val error: String? = null,
)

@Serializable
data class SearchResult(
    val keyword: String = "",
    val type: SearchType = SearchType.SONG,
    val page: Int = 1,
    val limit: Int = 20,
    /** 仅 type=song 时有效。 */
    val items: List<UnifiedTrack> = emptyList(),
    /** 仅 type=artist 时有效。 */
    val artists: List<UnifiedArtist> = emptyList(),
    /** 仅 type=album 时有效。 */
    val albums: List<UnifiedAlbum> = emptyList(),
    /** 仅 type=playlist 时有效。 */
    val playlists: List<PlaylistSummary> = emptyList(),
    val platforms: List<PlatformStatus> = emptyList(),
)

@Serializable
data class ArtistDetail(
    val artist: PlatformArtist = PlatformArtist(),
    val items: List<UnifiedTrack> = emptyList(),
    val albums: List<UnifiedAlbum> = emptyList(),
)

@Serializable
data class AlbumDetail(
    val album: PlatformAlbum = PlatformAlbum(),
    val items: List<UnifiedTrack> = emptyList(),
)

@Serializable
data class TrackDetail(val track: UnifiedTrack = UnifiedTrack())

/* ------------------------------ 发现页 ------------------------------ */

@Serializable
data class DiscoverSection(
    val key: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val platform: Platform = Platform.UNKNOWN,
    val kind: DiscoverKind = DiscoverKind.TRACKS,
    /**
     * 元素类型由 [kind] 决定（tracks → 歌曲，playlists/toplists → 歌单摘要）。
     * 这里先按原始 JSON 收下，交给调用方按 kind 二次解析。
     */
    val items: List<JsonElement> = emptyList(),
)

@Serializable
data class DiscoverError(
    val platform: Platform = Platform.UNKNOWN,
    val section: String = "",
    val message: String = "",
)

@Serializable
data class DiscoverFeed(
    val sections: List<DiscoverSection> = emptyList(),
    val errors: List<DiscoverError> = emptyList(),
)

/* ------------------------------ 播放 ------------------------------ */

/** 直连播放所需的地址与请求头（由平台 CDN 的防盗链要求决定）。 */
@Serializable
data class DirectStream(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class PlayResolveResult(
    /** 网关代理地址（相对路径），字节经服务器转发。 */
    val url: String = "",
    /** 网关实际给出的档位，可能是降级后的值，因此按字符串收。 */
    val quality: String = "",
    /** 只能听到试听片段（受版权或会员限制）。 */
    val trial: Boolean = false,
    /** 直连地址：客户端自己向 CDN 取流，服务器不占音频带宽。 */
    val direct: DirectStream? = null,
)

@Serializable
data class LyricResult(
    val lrc: String = "",
    val trans: String = "",
    val roma: String = "",
)

/* ------------------------------ 账户 ------------------------------ */

@Serializable
data class PublicUser(
    val id: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val role: String = "user",
    val createdAt: String = "",
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)

    /** 没设置头像时用昵称首字当兜底。 */
    val initial: String
        get() = nickname.trim().ifEmpty { username }.take(1).uppercase()
}

@Serializable
data class UserStats(
    val playlists: Int = 0,
    val favorites: Int = 0,
    val history: Int = 0,
)

@Serializable
data class MeResponse(
    val user: PublicUser? = null,
    val stats: UserStats? = null,
)

@Serializable
data class AccountProfile(
    val nickname: String = "",
    val avatar: String? = null,
    val userId: String? = null,
    val vip: Boolean? = null,
)

/* ------------------------------ 简单响应 ------------------------------ */

@Serializable
data class UserResponse(val user: PublicUser = PublicUser())

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class PlaylistResponse(val playlist: Playlist = Playlist())

@Serializable
data class PlaylistListResponse(val items: List<Playlist> = emptyList())

@Serializable
data class PlaylistDetailResponse(
    val playlist: Playlist = Playlist(),
    val items: List<UnifiedTrack> = emptyList(),
)

@Serializable
data class TrackListResponse(val items: List<UnifiedTrack> = emptyList())

@Serializable
data class FavoriteResponse(val favorite: Boolean = false)

@Serializable
data class FavoritesResponse(
    val items: List<UnifiedTrack> = emptyList(),
    /** 收藏歌曲的 key 集合，客户端拿它标记红心最省事。 */
    val keys: List<String> = emptyList(),
)

@Serializable
data class AddTrackResponse(val added: Boolean = false, val trackCount: Int = 0)

@Serializable
data class RemoveTrackResponse(val ok: Boolean = false, val trackCount: Int = 0)

@Serializable
data class PlaylistSummaryListResponse(val items: List<PlaylistSummary> = emptyList())

@Serializable
data class PlatformInfo(
    val platform: Platform = Platform.UNKNOWN,
    val label: String = "",
    val acceptsCredentials: Boolean = false,
)

@Serializable
data class PlatformListResponse(val items: List<PlatformInfo> = emptyList())

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val time: String = "",
    val upstreams: Map<String, String> = emptyMap(),
)
