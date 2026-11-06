package com.sakura.music.ui.screens.player.nowplaying

import androidx.compose.ui.text.AnnotatedString
import com.sakura.music.core.player.LyricLine
import com.sakura.music.core.player.PlaybackRoute
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.NowPlayingStyle
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.room.RoomMemberView

/**
 * 播放页样式共享的只读状态。
 *
 * 状态由 `PlayerScreen`（宿主）收集，样式层只负责画。房间分流、跟随、电台这些
 * 「播放逻辑」留在宿主里，样式不需要知道指令最后交给了房间还是本地播放器。
 */
data class NowPlayingUiState(
    val track: UnifiedTrack,
    /** 歌手 · 专辑，两边的名字各自可点。 */
    val subtitle: AnnotatedString,
    /** 播放器报上来的真实进度。 */
    val positionMs: Long,
    /** 拖动进度条时的预览值；没在拖时等于 [positionMs]。 */
    val shownPositionMs: Long,
    /** 正在拖进度条（歌词跟手，但先不打扰播放器）。 */
    val scrubbing: Boolean,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    /** 缓冲超过宽限期后才亮的小转圈（见宿主的 BUFFER_GRACE_MS）。 */
    val showBuffering: Boolean,
    val trial: Boolean,
    val lyrics: List<LyricLine>,
    val lyricLoading: Boolean,
    val lyricWordByWord: Boolean,
    val lyricSynced: Boolean,
    val lyricIndex: Int,
    val playMode: PlayMode,
    val route: PlaybackRoute,
    val canSkip: Boolean,
    val volume: Float,
    /** 这一路实际来自哪个平台，网页端样式的来源标签用。 */
    val sourcePlatform: Platform?,
    /** 还有别的音源可切——试听提示里会写「可在更多里切换音源」。 */
    val hasMultipleSources: Boolean,
    /** 账号在房间里（可能放音在另一台设备上）。 */
    val inRoom: Boolean,
    /** 封面下沿那句「我们一起听了 …」/「一起听在「某台设备」上播放」。 */
    val roomTogetherText: String,
    val me: RoomMemberView?,
    val partner: RoomMemberView?,
    /** 正在跟随哪台设备；null = 没在跟随。 */
    val followingName: String?,
    /** 电台播放中的提示（含平台名）；null = 没在听电台。 */
    val radioLabel: String?,
    val isFavorite: Boolean,
    val style: NowPlayingStyle,
)

/**
 * 播放页样式共享的操作。
 *
 * 全部由宿主实现：样式层点一下，宿主决定这是本地播放器还是房间同步。
 */
data class NowPlayingActions(
    val collapse: () -> Unit,
    /** 松手后的最终位置——只有这一下才真的去 seek。 */
    val seek: (Long) -> Unit,
    /** 拖动过程中的位置；传 null 表示松手。 */
    val scrub: (Long?) -> Unit,
    val toggle: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val cyclePlayMode: () -> Unit,
    val setVolume: (Float) -> Unit,
    val openQueue: () -> Unit,
    val openActions: () -> Unit,
    val openTogether: () -> Unit,
    val openDevices: () -> Unit,
    val openArtist: (Platform, String, String) -> Unit,
    val openAlbum: (Platform, String, String) -> Unit,
    val stopFollow: () -> Unit,
    val stopRadio: () -> Unit,
    val setStyle: (NowPlayingStyle) -> Unit,
    val toggleFavorite: () -> Unit,
)

/** 皮肤的粗分：原生页走 Material，网页端三套样式走深色玻璃。 */
enum class NowPlayingSkin { Material, Desktop }
