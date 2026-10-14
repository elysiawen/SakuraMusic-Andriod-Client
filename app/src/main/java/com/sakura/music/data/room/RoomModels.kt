package com.sakura.music.data.room

import com.sakura.music.data.connect.TrackSummary
import com.sakura.music.data.model.UnifiedTrack
import kotlinx.serialization.Serializable

/**
 * 「一起听」房间协议的数据结构（服务端见 `gateway/src/services/rooms.ts`）。
 *
 * 房间事件与多设备那套协议**共用同一条 SSE 长连接**，队列里的一首也与设备上报的一首
 * 字段完全一致，所以曲目直接复用 [TrackSummary]（含 `toUnifiedTrack()`），不再定义一份
 * 长得一模一样的镜像类型——两份类型迟早会各改各的。
 *
 * 这里所有字段都带默认值，与 [com.sakura.music.data.model.UnifiedTrack] 的容错约定一致：
 * 上游多给字段不会炸，少给字段退回默认值。
 */

/* ------------------------------ 房间 ------------------------------ */

/** 房间成员。席位按 `deviceId` 存（与连接解耦），所以断线仍是成员，只是 `online` 为 false。 */
@Serializable
data class RoomMemberView(
    val userId: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val deviceId: String = "",
    val deviceName: String = "",
    /** 设备形态（web / android / windows），与房间类型无关。 */
    val kind: String = "web",
    val online: Boolean = true,
    /** 收件的**这台设备**就占着这个席位。服务端按接收方逐个填。 */
    val me: Boolean = false,
    /** 这条成员属于**收件人的账号**（席位可能挂在它的另一台设备上）。同样按接收方逐个填。 */
    val mine: Boolean = false,
    /** 双人房恒为 `member`；多人房里创建者是 `owner`。 */
    val role: String = "member",
)

/**
 * 房间全量快照（成员 + 队列），随 `room` 事件下发。
 *
 * `kind` 是字符串而不是枚举：协议里只有 `duo` / `group` 两个取值，但上游哪天加一个
 * 也不该让整个快照反序列化失败（真发生时就当双人房处理）。
 */
@Serializable
data class RoomView(
    val code: String = "",
    val kind: String = "duo",
    /** 人数上限。 */
    val capacity: Int = 2,
    val ownerUserId: String = "",
    val members: List<RoomMemberView> = emptyList(),
    val queue: List<TrackSummary> = emptyList(),
    val index: Int = 0,
    /** 队列版本号：变了就代表队列被改过，镜像端据此决定要不要重建队列。 */
    val version: Int = 0,
    /** 两人**历史累计**一起听时长（秒，跨房间跨会话），已含本次房间的部分。 */
    val togetherSeconds: Double = 0.0,
    /** 只算**这一次房间**的累计时长（秒），从两人凑齐那刻起算。 */
    val togetherSessionSeconds: Double = 0.0,
    /** 此刻是否正在走表（两人都在房间里）。为 true 时客户端自行把它推演到「现在」。 */
    val togetherCounting: Boolean = false,
) {

    /** 双人房：2 人、人人平权、没有房主。协议里非 `group` 的一律按它处理。 */
    val isDuo: Boolean get() = kind != KIND_GROUP

    /** 多人房：有房主与成员管理（服务端尚未开放）。 */
    val isGroup: Boolean get() = kind == KIND_GROUP

    /**
     * 本机成员——**设备级**（席位就在这台设备上时才有值）。
     *
     * 界面上别用它：同一账号的第二台设备收到的快照里，自己账号那条也是 `me = false`
     * （席位在另一台设备上），拿它分不出「我账号的席位」与「对面那位」。
     * 请用 `RoomSync.myAccountMember()` / `otherMember()`。
     */
    val me: RoomMemberView? get() = members.firstOrNull { it.me }

    /** 除本机之外的成员。双人房恒为 0 或 1 人。 */
    val others: List<RoomMemberView> get() = members.filter { !it.me }

    /** 另一位成员（双人房的专用说法）。同上：设备级，界面上用 `RoomSync.otherMember()`。 */
    val partner: RoomMemberView? get() = others.firstOrNull()

    /** 本机是不是房主。**双人房永远为 false**——那里没有房主这个概念（创建者也只是 `member`）。 */
    val isOwner: Boolean get() = isGroup && me?.role == ROLE_OWNER

    /** 本机能否解散房间：双人房人人可解散；多人房只有房主。 */
    val canDissolve: Boolean get() = isDuo || isOwner

    private companion object {
        const val KIND_GROUP = "group"
        const val ROLE_OWNER = "owner"
    }
}

/** 房间播放态，随 `room-playback` 事件下发。 */
@Serializable
data class RoomPlayback(
    val index: Int = 0,
    val playing: Boolean = false,
    /** 秒，盖章那一刻的位置。 */
    val position: Double = 0.0,
    /** 服务端盖这一刻的 epoch 毫秒：客户端拿它把位置推到「现在」。 */
    val positionAt: Long = 0L,
    /** 秒。 */
    val duration: Double = 0.0,
    val track: TrackSummary? = null,
    /** 这一帧是「有成员播不了、全员跳过」产生的。 */
    val skipReason: String? = null,
    /** 被跳过的那首的名字（提示用）。 */
    val skippedTitle: String? = null,
    /** 服务端发这一帧的时刻（epoch 毫秒），用来校正本机时钟差。 */
    val serverNow: Long? = null,
)

/** `room` 事件的信封：`room` 为 null 表示房间没了（对方解散 / 席位被同账号的另一台设备接走）。 */
@Serializable
data class RoomEnvelope(
    val room: RoomView? = null,
    val serverNow: Long? = null,
    /**
     * 房间为什么没了。目前只有 `seat_taken` 一种：
     * 席位（也就是播放）被同账号的另一台设备接走了，本机该回到单机听。
     * null 就是常规的「对方结束了」，文案不同而已。
     */
    val reason: String? = null,
    /** `seat_taken` 时：接走席位那台设备的名字，用来把话说清楚。 */
    val deviceName: String? = null,
)

/** 「一起听」的房间预览：入房前看一眼这个房间存不存在、都有谁、在放什么。 */
@Serializable
data class RoomPreview(
    val code: String = "",
    val kind: String = "duo",
    val capacity: Int = 2,
    val members: List<PreviewMember> = emptyList(),
    val playing: Boolean = false,
    val track: TrackSummary? = null,
    /**
     * 本账号在这个房间的席位挂在哪台设备上；null = 本账号不在房间里。
     *
     * 冷启动时全靠它回答一个问题：上次是在哪台设备上听的？席位是本机的（或空着）就接回来，
     * 席位在**我的另一台设备**上就只能旁观——直接 `join` 会把播放从那边抢过来。
     */
    val mine: String? = null,
) {

    @Serializable
    data class PreviewMember(val nickname: String = "", val avatar: String? = null)
}

/**
 * SSE 上传下来的房间事件。
 *
 * 通道由 [com.sakura.music.data.connect.ConnectClient] 维持（与设备列表同一条连接），
 * 那边只负责把帧解出来，语义全在 [com.sakura.music.core.player.RoomSync] 里。
 */
sealed interface RoomStreamEvent {

    /**
     * `room`：房间快照（成员 / 队列 / 时长）；`room` 为 null 即房间没了。
     * 整只信封一起递过去：为什么没了（`reason`）也在里面。
     */
    data class Snapshot(val envelope: RoomEnvelope) : RoomStreamEvent

    /** `room-playback`：房间播放态。 */
    data class Playback(val state: RoomPlayback, val serverNow: Long?) : RoomStreamEvent

    /**
     * 连接（重新）建立。
     *
     * 重连后服务端会把房间补推过来，但前提是它还认得这台设备在房间里；
     * 冷启动（App 重启、网关也重启过）时得凭本地存着的房间码重新 `join` 一次。
     */
    data object Reconnected : RoomStreamEvent
}

/* ------------------------------ 请求 / 响应 ------------------------------ */

/** `POST /api/rooms/create` 的请求体。队列可选：不带就用本机当前队列当种子。 */
@Serializable
data class CreateRoomBody(
    val deviceId: String,
    val name: String? = null,
    val deviceKind: String? = null,
    val kind: String = "duo",
    val queue: List<UnifiedTrack> = emptyList(),
    val index: Int = 0,
)

/** `POST /api/rooms/join` 的请求体。 */
@Serializable
data class JoinRoomBody(
    val code: String,
    val deviceId: String,
    val name: String? = null,
    val deviceKind: String? = null,
)

/** `leave` / `dissolve` 的请求体。 */
@Serializable
data class DeviceIdBody(val deviceId: String)

/** `POST /api/rooms/create` 的响应。 */
@Serializable
data class RoomCodeResponse(val code: String = "")

/* ------------------------------ 一起听总览（账户中心） ------------------------------ */

/** 一起听总览里的一位伙伴。 */
@Serializable
data class TogetherPartner(
    val userId: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    /** 和这位伙伴的累计一起听时长（秒）。 */
    val seconds: Double = 0.0,
    /** 最近一次一起听的时间（ISO 字符串）。 */
    val lastAt: String = "",
)

/** 我的一起听总览。 */
@Serializable
data class TogetherOverview(
    /** 与所有人累计的一起听时长（秒）。 */
    val totalSeconds: Double = 0.0,
    val partnerCount: Int = 0,
    /** 按累计时长倒序。 */
    val partners: List<TogetherPartner> = emptyList(),
    /** 一起听过的次数；服务端明细表不在时为 null（界面少显示一个数字即可）。 */
    val sessionCount: Int? = null,
)

/** 一起听伙伴的基本信息（明细弹窗的标题栏用）。 */
@Serializable
data class TogetherPartnerBasic(
    val userId: String = "",
    val nickname: String = "",
    val avatar: String? = null,
)

/** 一次「一起听」的明细。 */
@Serializable
data class TogetherSessionItem(
    /** 这一次一起听的开始时间（ISO 字符串）。 */
    val startedAt: String = "",
    val seconds: Double = 0.0,
)

/** 某位伙伴的详细一起听记录。 */
@Serializable
data class TogetherPartnerDetail(
    val partner: TogetherPartnerBasic = TogetherPartnerBasic(),
    /** 与这位伙伴的累计一起听时长（秒）。 */
    val totalSeconds: Double = 0.0,
    /** 一起听过的次数；服务端明细表不在时为 null。 */
    val sessionCount: Int? = null,
    /**
     * 每次明细，最近的在前；服务端明细表不在时为 null
     * （那时只有累计可用，界面要说清楚，而不是显示成"没听过"）。
     */
    val sessions: List<TogetherSessionItem>? = null,
)
