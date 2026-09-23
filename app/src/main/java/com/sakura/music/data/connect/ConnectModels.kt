package com.sakura.music.data.connect

import com.sakura.music.data.model.AlbumRef
import com.sakura.music.data.model.ArtistRef
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.TrackSource
import com.sakura.music.data.model.UnifiedTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 多设备播放协议的数据结构，见仓库根目录的 `connect-protocol.md`。
 *
 * 这套协议只传「谁在放什么」的描述，一个音频字节都不传——音频永远由真正出声的那台
 * 设备自己去取。所以这里的字段都很小（一次上报 200–400 字节）。
 */

/** 曲目摘要：上报给网关的那一小份，不是完整的 [UnifiedTrack]。 */
@Serializable
data class TrackSummary(
    val key: String = "",
    val title: String = "",
    /** **已拼好的字符串**（`周杰伦 / 费玉清`），协议明确要求不是数组。 */
    val artists: String = "",
    val album: String = "",
    val cover: String? = null,
    val durationMs: Long = 0L,
    /**
     * 取流坐标。
     *
     * 控制端要靠它重建曲目：「跟随播放」（协议 7.5）得拿 `platform` + `id` 去解析播放地址，
     * 少了这一段就只能干看着。
     */
    val sources: List<TrackSourceRef> = emptyList(),
)

/** [TrackSummary.sources] 的元素；协议里 `platform` 是字符串，所以不直接复用 [TrackSource]。 */
@Serializable
data class TrackSourceRef(
    val platform: String = "",
    val id: String = "",
    val mid: String? = null,
    val numericId: String? = null,
)

/** 本机的播放状态快照。 */
@Serializable
data class DeviceState(
    val track: TrackSummary? = null,
    val playing: Boolean = false,
    /** 秒。 */
    val position: Double = 0.0,
    /**
     * 服务端盖章的上报时刻（毫秒），控制端拿它推算实时进度。
     *
     * 只读：各设备时钟不一致，客户端传了也会被覆盖，所以上报时不带（序列化配置里
     * `explicitNulls = false`，null 字段不会出现在请求体里）。
     */
    val positionAt: Long? = null,
    /** 秒。 */
    val duration: Double = 0.0,
    val volume: Double? = null,
    val quality: String? = null,
    val queueLength: Int = 0,
    /**
     * 本机正在跟随谁（对端的 `deviceId`）。
     *
     * 单独上报是为了让「谁跟着谁」成为**互相可见的事实**：光看两台都在放同一首歌，
     * 分不清是同步播放还是各放各的，被跟随的一方也就没法确认对方到底跟上了没有。
     */
    val following: String? = null,
)

/** 一台设备（含它当前的状态）。 */
@Serializable
data class DeviceView(
    val deviceId: String = "",
    val name: String = "",
    val kind: String = "web",
    /** 在线设备才会出现在列表里，服务端不保留离线的墓碑。 */
    val online: Boolean = true,
    val state: DeviceState? = null,
)

/** `hello`：建连后唯一一次，带一份完整快照。 */
@Serializable
data class HelloEvent(
    val deviceId: String = "",
    val devices: List<DeviceView> = emptyList(),
    /** 服务端当前时间（毫秒），用来校正本机时钟差，见 7.5。 */
    val serverNow: Long? = null,
)

/** `devices`：任何变化都推全量列表。 */
@Serializable
data class DevicesEvent(
    val devices: List<DeviceView> = emptyList(),
    /** 服务端当前时间（毫秒），每次广播都带。 */
    val serverNow: Long? = null,
)

/** `command`：发给本机的控制指令。 */
@Serializable
data class CommandEvent(
    /** 发起方，接管时要拿它当收件人。 */
    val from: String = "",
    val action: String = "",
    val payload: JsonObject? = null,
)

/** `transfer` / `release` 的 payload：整条队列连进度一起交出去。 */
@Serializable
data class TransferPayload(
    /** 网关不解析它，只卡总大小；元素结构见 `connect-protocol.md` 第 9 节。 */
    val queue: List<UnifiedTrack> = emptyList(),
    val index: Int = 0,
    /** 秒。 */
    val position: Double = 0.0,
)

/** `seek` 的 payload。 */
@Serializable
data class SeekPayload(val position: Double = 0.0)

/** `volume` 的 payload：0 ~ 1。 */
@Serializable
data class VolumePayload(val volume: Double = 0.0)

/** 协议规定 `track.sources` 最多 4 条。 */
private const val MAX_TRACK_SOURCES = 4

/** 用 [UnifiedTrack] 里那份信息拼出上报用的摘要。 */
fun UnifiedTrack.toTrackSummary(): TrackSummary = TrackSummary(
    key = key,
    title = title,
    // 协议的 artists 是字符串字段，这里拼一次就好，别让控制端自己拼。
    artists = artists.mapNotNull { it.name.trim().ifEmpty { null } }.joinToString(" / "),
    album = album.name,
    cover = album.cover?.takeIf { it.isNotBlank() },
    durationMs = durationMs,
    // 协议：最多 4 条，`platform` 与 `id` 为空的项丢掉。
    sources = sources
        .filter { it.platform.isKnown && it.id.isNotBlank() }
        .take(MAX_TRACK_SOURCES)
        .map {
            TrackSourceRef(
                platform = it.platform.id,
                id = it.id,
                mid = it.mid,
                numericId = it.numericId,
            )
        },
)

/**
 * 拿上报来的摘要重建一份**能播**的曲目。
 *
 * 缺 [TrackSummary.sources] 就返回 null——没有 `platform` + `id` 就解析不出播放地址，
 * 这正是协议第 9 节反复强调「`sources` 必须带上」的原因。跟随播放靠它对齐另一台设备。
 */
fun TrackSummary.toUnifiedTrack(): UnifiedTrack? {
    val restored = sources.mapNotNull { ref ->
        val platform = Platform.fromId(ref.platform)
        if (!platform.isKnown || ref.id.isBlank()) return@mapNotNull null
        TrackSource(platform = platform, id = ref.id, mid = ref.mid, numericId = ref.numericId)
    }
    if (restored.isEmpty()) return null

    return UnifiedTrack(
        key = key.ifBlank { "${restored.first().platform.id}:${restored.first().id}" },
        title = title.ifBlank { "未知歌曲" },
        // 摘要把歌手拼成了一个字符串，这里按分隔符拆回去——拆不出来就当作没有歌手。
        artists = artists
            .split(" / ")
            .mapNotNull { it.trim().ifEmpty { null } }
            .map { ArtistRef(name = it) },
        album = AlbumRef(name = album, cover = cover?.takeIf { it.isNotBlank() }),
        durationMs = durationMs,
        sources = restored,
    )
}


