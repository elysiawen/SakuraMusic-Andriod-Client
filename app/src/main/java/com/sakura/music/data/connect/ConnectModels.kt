package com.sakura.music.data.connect

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
)

/** `devices`：任何变化都推全量列表。 */
@Serializable
data class DevicesEvent(
    val devices: List<DeviceView> = emptyList(),
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

/** 用 [UnifiedTrack] 里那份信息拼出上报用的摘要。 */
fun UnifiedTrack.toTrackSummary(): TrackSummary = TrackSummary(
    key = key,
    title = title,
    // 协议的 artists 是字符串字段，这里拼一次就好，别让控制端自己拼。
    artists = artists.mapNotNull { it.name.trim().ifEmpty { null } }.joinToString(" / "),
    album = album.name,
    cover = album.cover?.takeIf { it.isNotBlank() },
    durationMs = durationMs,
)


