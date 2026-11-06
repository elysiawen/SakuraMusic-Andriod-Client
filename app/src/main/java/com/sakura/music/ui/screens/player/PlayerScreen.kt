package com.sakura.music.ui.screens.player

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sakura.music.core.player.LrcParser
import com.sakura.music.core.player.LyricLine
import com.sakura.music.core.player.LyricWord
import com.sakura.music.core.player.PlaybackRoute
import com.sakura.music.data.model.ArtistRef
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.model.formatQualitySize
import com.sakura.music.data.model.qualityLabel
import com.sakura.music.data.model.qualityMenuLabel
import com.sakura.music.data.model.qualitySpec
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.room.RoomMemberView
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AccountAvatar
import com.sakura.music.ui.components.AddToPlaylistSheet
import com.sakura.music.ui.components.ChoiceSheet
import com.sakura.music.ui.components.DeviceSheet
import com.sakura.music.ui.components.PlaybackErrorCard
import com.sakura.music.ui.components.QueueSheet
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.TogetherSheet
import com.sakura.music.ui.components.TrackActionsSheet
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.util.formatDuration
import com.sakura.music.ui.util.formatTogetherDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** How long buffering must last before the loading indicator appears. */
private const val BUFFER_GRACE_MS = 350L

/** Duration for line size/alpha animations. */
private val lyricTransition = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * Fixed metrics for every lyric row. With both constant, an item's height is
 * exactly `lineHeight + 2 * vPadding` — the centring scroll computes it
 * directly instead of measuring laid-out items.
 */
private val LYRIC_LINE_HEIGHT = 34.sp
private val LYRIC_V_PADDING = 10.dp

/**
 * 全屏播放器。
 *
 * 队列、进度、歌词都来自 [com.sakura.music.core.player.PlaybackCenter]（播放器本体活在后台服务里），
 * 这一页只负责画出来并接上手势：封面、歌词、进度、控制，以及队列 / 音质 / 更多三个弹层。
 */
/**
 * @param onCollapse 收起播放器浮层（箭头、下拉手势、失败页的「返回」都走它）。
 * @param navigator 播放页是**盖在导航之上的浮层**，不在导航栈里，所以点歌手 / 专辑名要跳转
 *   时只能把入口从外面递进来。它只管跳，浮层什么时候藏、什么时候回来由宿主
 *   （`PlayerHost`）按「出发页」判断。
 */
@Composable
fun PlayerScreen(onCollapse: () -> Unit, navigator: SakuraNavigator) {
    val container = appContainer()
    val state by container.playbackCenter.state.collectAsStateWithLifecycle()
    // 角标直接订阅路由流：上报发生在播放线程，走播放状态那条路容易漏掉更新。
    val route by container.playbackRoute.route.collectAsStateWithLifecycle()
    val playMode by container.playbackCenter.playMode.collectAsStateWithLifecycle()
    val following by container.connectSync.following.collectAsStateWithLifecycle()
    val favorites by container.libraryRepository.favoriteKeys.collectAsStateWithLifecycle()
    val quality by container.settings.quality.collectAsStateWithLifecycle(initialValue = Quality.Default)

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showTogether by remember { mutableStateOf(false) }

    // 打开音质面板时按需补一次单曲详情：旧存档 / 播放历史里的曲目常常没带 qualitySizes，
    // 面板第一次打开就该把各档体积补齐（已有体积时是空操作）。
    LaunchedEffect(showQuality) {
        if (showQuality) container.playbackCenter.refreshQualityMeta()
    }

    // 拖动进度时歌词跟着走（但不打扰播放器）；松手后回到真实进度。
    var scrubPosition by remember { mutableStateOf<Long?>(null) }

    /*
     * 房间接管期间，传输控制与进度**一律走房间**。
     *
     * 直接动本地播放器是没用的：镜像循环每 200 毫秒就会把它拽回房间状态，
     * 表现成「点了暂停，一秒后又自己播起来」。只有把意图提交给房间、等它广播回来，
     * 两台设备才会一起动。
     */
    val roomCode by container.roomSync.code.collectAsStateWithLifecycle()
    val room by container.roomSync.room.collectAsStateWithLifecycle()
    val spectating by container.roomSync.spectating.collectAsStateWithLifecycle()

    /** 账号在房间里（可能放音在另一台设备上）。 */
    val inRoom = roomCode != null

    /**
     * 房间的播放**就在本机**。
     *
     * 传输控制与队列都按它分流：旁观时（同账号的另一台在放）本机还是本机的播放器，
     * 照席位去发指令只会一条条撞上「你不在这个房间里」。
     */
    val seatHere = inRoom && !spectating

    /*
     * 一起听读数（「我们一起听了 …」）的每秒节拍。
     *
     * 播放中播放状态每 400 毫秒就会变一次（进度在动），但如果**暂停着**，那一份状态是等值的，
     * 状态流不发射、也就不会重组，读数会僵在那儿。所以在房间里单独起一个每秒的节拍。
     */
    var togetherTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(inRoom) {
        if (!inRoom) return@LaunchedEffect
        while (true) {
            delay(1_000)
            togetherTick++
        }
    }
    /**
     * 封面下沿那句话。
     *
     * 席位在本机时是「我们一起听了多久」；旁观时（同账号的另一台在放）必须换成
     * 「播放不在这台设备上、在哪台上」——照旧写时长的话，用户只会以为这台设备哑了。
     *
     * 设备名也要进 remember 的键：它在**第一次**收到快照时才从 null 变成名字，
     * 而那一刻累计时长很可能还是 0，光看时长是看不出要重算的。
     */
    val togetherSeatName = container.roomSync.seatDeviceName()
    val togetherBadgeText = remember(
        togetherTick,
        room?.togetherSeconds,
        room?.togetherCounting,
        spectating,
        togetherSeatName,
    ) {
        if (!spectating) {
            formatTogetherDuration(container.roomSync.togetherLiveSeconds())
        } else {
            togetherSeatName?.let { "一起听在「$it」上播放" } ?: "一起听在另一台设备上播放"
        }
    }

    fun seekTo(positionMs: Long) {
        if (seatHere) container.roomSync.seek(positionMs / 1000.0) else container.playbackCenter.seekTo(positionMs)
    }

    fun toggleTransport() {
        if (seatHere) container.roomSync.toggle() else container.playbackCenter.toggle()
    }

    fun previousTrack() {
        if (seatHere) container.roomSync.prev() else container.playbackCenter.previous()
    }

    fun nextTrack() {
        if (seatHere) container.roomSync.next() else container.playbackCenter.next()
    }

    // 错误卡片退场时 content 里已经读不到错误了，留一份最后的内容给它放完动画。
    var lastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error) {
        state.error?.let { lastError = it }
    }

    // 缓冲指示器有个宽限期：一次很快的 seek 不该让图标闪一下。
    var showBuffering by remember { mutableStateOf(false) }
    LaunchedEffect(state.isBuffering) {
        if (state.isBuffering) {
            delay(BUFFER_GRACE_MS)
            showBuffering = true
        } else {
            showBuffering = false
        }
    }

    // 看歌词的页面上屏幕不该自己暗下去。
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val track = state.current
    if (track == null) {
        EmptyPlayer(onBack = onCollapse)
        return
    }

    /* ── 下拉收起 ─────────────────────────────────────────────────
     * 手势放在封面上（那块区域没有会抢竖向滚动的兄弟），拖动时整页内容
     * 跟着往下走；松手时要么超过阈值直接收起，要么弹回原位。
     * ────────────────────────────────────────────────────────────*/
    val collapseOffset = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { COLLAPSE_DRAG_THRESHOLD.toPx() }
    val collapseFlingPx = with(density) { COLLAPSE_FLING_VELOCITY.toPx() }

    fun onCollapseDrag(amount: Float) {
        // 只认往下的：往上拖回 0 为止。
        scope.launch {
            collapseOffset.snapTo((collapseOffset.value + amount).coerceAtLeast(0f))
        }
    }

    fun onCollapseEnd() {
        val velocity = velocityTracker.calculateVelocity().y
        if (collapseOffset.value > collapseThresholdPx || velocity > collapseFlingPx) {
            // 收起之后浮层的退场动画会接着往下走，和手指的方向连起来。
            onCollapse()
        } else {
            scope.launch {
                collapseOffset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
        }
    }

    val collapseGesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                onCollapseDrag(dragAmount)
            },
            onDragEnd = ::onCollapseEnd,
            onDragCancel = ::onCollapseEnd,
        )
    }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shownPosition = scrubPosition ?: state.positionMs
    val syncedLyrics = LrcParser.isSynced(state.lyric)
    val currentLyricIndex = if (state.lyric.isEmpty()) {
        -1
    } else {
        state.lyric.indexOfLast { it.timeMs in 1..shownPosition }
    }
    val isFavorite = favorites.contains(track.key)
    // 歌手与专辑名各自可点。这里**不**收起浮层：跳转之后宿主会按「出发页」把它藏起来，
    // 用户从详情页返回时再自动放回来——返回键那条链才是「详情页 → 播放页 → 出发页」。
    val subtitle = subtitleWithLinks(
        track = track,
        linkColor = MaterialTheme.colorScheme.primary,
        onOpenArtist = { platform, id, name -> navigator.openArtist(platform, id, name) },
        onOpenAlbum = { platform, id, name -> navigator.openAlbum(platform, id, name) },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            /*
             * 位移加在**最外层**（连同底色一起），不是只挪里面的 Column。
             *
             * 底色是这页自己的：只挪内容的话，上面空出来的那一条露出的还是这层不透明的
             * 底色（浅色主题下就是一片白），底下那页根本透不出来——「往下拖能看到底下的
             * 页面」就成了空话。整块挪走，空出来的地方才真的是底下的页面。
             */
            .offset { IntOffset(0, collapseOffset.value.roundToInt()) }
            .background(MaterialTheme.colorScheme.background),
    ) {
        /*
         * 这页是**浮层**（见 `SakuraRoot` 的 `PlayerHost`）：底下的导航内容——发现 / 账号页、
         * 底部那条导航栏、列表里的行——都还组合着，为的是「往下拖能看到它们」。
         *
         * 但「画在上层」**不等于**「挡住点击」：Compose 的命中测试会把落在**空白处**的点击
         * 一路透到下面那层去。表现就是「在播放页上点空白区域，结果切到了账号页」，
         * 或者更隐蔽的「点到了底下列表里的某一首歌」。
         *
         * 所以在内容**底下**垫一层只吃点击的挡板：上面那层（按钮、封面拖动）先命中，
         * 落在空白处的那些由它吃掉。用 `pointerInput` 而不是 `clickable` —— 它只是个挡板，
         * 不该在无障碍树里多出一个什么都没有的控件。
         */
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 这页是「盖上来」的，收起用一个向下的箭头比返回键更符合直觉。
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起")
                }
                Spacer(Modifier.weight(1f))

                // 跟随中：在投送按钮旁边放个能直接停的开关。
                // 跟随期间本机的操作会被下一轮同步覆盖回来，所以「退出跟随」必须触手可及，
                // 藏进设备列表里的话，想停下来还得先进列表找到那台设备。
                // 只画个图标不够——光看一个「同步」符号，猜不出是这台设备在跟谁走。
                following?.let { target ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { container.connectSync.stopFollow() },
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(start = 14.dp, end = 16.dp)
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "跟随 ${target.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                // 电台播放中：离开首页后也要有「退出电台」的出口，否则只能回发现页找卡片。
                // 用 primary/onPrimary：tertiaryContainer 没在主题里定义，会回落到 Material
                // 默认色板，换强调色时它纹丝不动，看起来就像「不跟主题色走」。
                state.radio?.let { active ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { container.playbackCenter.stopRadio() },
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${active.platform.label}电台 · 退出",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                // 一起听：和朋友在两个客户端上同步听同一首歌。
                // 在房间里时图标染成强调色并带一个小圆点——不然退出去看别的页面回来，
                // 光从一个灰图标上分不出自己还在不在房间里。
                IconButton(onClick = {
                    // 电台与「一起听」互斥：说清楚为什么点不开，比灰掉一个图标友好。
                    if (state.radio != null) {
                        message = "正在听私人电台，请先退出电台再使用「一起听」"
                    } else {
                        showTogether = true
                    }
                }) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "一起听",
                            tint = if (inRoom) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (inRoom) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp, end = 1.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }

                // 投送到其它设备：同一账号下的设备互相可见，可以把播放交接过去。
                IconButton(onClick = { showDevices = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = "播放到其它设备",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { showActions = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isLandscape) {
                // 左边封面与控件，右边歌词。
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    /*
                     * 横屏的高度比竖屏紧张得多（有些设备扣掉状态栏只剩 300dp 出头）。
                     * 以前封面按宽度定边长、传输键固定 70dp，再套一层 verticalScroll：
                     * 内容一超高，可滚动 Column 就把中间那颗大按钮的下沿裁掉一小截。
                     *
                     * 现在反过来：封面吃掉「除控件之外的剩余高度」，在剩余空间里取宽高较小值
                     * 保持正方形；传输键按可用高度缩放。固定控件的高度不用猜，交给 weight 算。
                     */
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                    ) {
                        val playButtonSize = (maxHeight * 0.16f).coerceIn(44.dp, 70.dp)
                        val gap = (maxHeight * 0.025f).coerceIn(4.dp, 12.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                // 留一点下边距：按钮不该贴着系统手势区。
                                .padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(0.75f),
                            ) {
                                val side = minOf(maxWidth, maxHeight)
                                Box(
                                    modifier = Modifier
                                        .size(side)
                                        .align(Alignment.Center)
                                        .then(collapseGesture),
                                ) {
                                    CoverArt(
                                        cover = track.album.cover,
                                        showBuffering = showBuffering,
                                        modifier = Modifier.matchParentSize(),
                                    )
                                    RouteBadge(
                                        route = route,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp),
                                    )
                                    if (inRoom) {
                                        TogetherCoverBand(
                                            text = togetherBadgeText,
                                            me = container.roomSync.myAccountMember(),
                                            partner = container.roomSync.otherMember(),
                                            onClick = { showTogether = true },
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(gap))
                            TrackInfo(title = track.title, subtitle = subtitle, trial = state.trial)
                            Spacer(Modifier.height(gap))
                            SeekArea(
                                positionMs = state.positionMs,
                                durationMs = state.durationMs,
                                onScrub = { scrubPosition = it },
                                onSeek = {
                                    seekTo(it)
                                    scrubPosition = null
                                },
                            )
                            Spacer(Modifier.height(gap))
                            TransportRow(
                                isPlaying = state.isPlaying,
                                canSkip = state.queue.size > 1,
                                playMode = playMode,
                                onPlayMode = {
                                    // 房间里模式是两端共享的：按同一个循环顺序推进一次，交给房间去改
                                    // （服务端随下一帧广播回来，两端的图标一起变）。
                                    if (seatHere) {
                                        container.roomSync.setMode(playMode.next())
                                    } else {
                                        container.playbackCenter.cyclePlayMode()
                                    }
                                },
                                onQueue = { showQueue = true },
                                onPrevious = { previousTrack() },
                                onToggle = { toggleTransport() },
                                onNext = { nextTrack() },
                                playButtonSize = playButtonSize,
                            )
                        }
                    }

                    Spacer(Modifier.width(18.dp))

                    LyricsBox(
                        lyrics = state.lyric,
                        currentIndex = currentLyricIndex,
                        synced = syncedLyrics,
                        loading = state.lyricLoading,
                        positionMs = shownPosition,
                        isPlaying = state.isPlaying && !state.isBuffering,
                        wordByWord = state.lyricWordByWord,
                        scrubbing = scrubPosition != null,
                        onSeek = { seekTo(it) },
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            // 卡片下沿别贴着屏幕边。
                            .padding(bottom = 28.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(COVER_WIDTH_FRACTION)
                        .aspectRatio(1f)
                        .then(collapseGesture),
                ) {
                    CoverArt(
                        cover = track.album.cover,
                        showBuffering = showBuffering,
                        modifier = Modifier.matchParentSize(),
                    )
                    RouteBadge(
                        route = route,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                    // 在房间里：封面下沿压上两人的头像与一起听读数（见 TogetherCoverBand）。
                    if (inRoom) {
                        TogetherCoverBand(
                            text = togetherBadgeText,
                            // 「我」按账号维度认（`me` 是设备级的，旁观时自己那条也是 false）。
                            me = container.roomSync.myAccountMember(),
                            partner = container.roomSync.otherMember(),
                            onClick = { showTogether = true },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                TrackInfo(title = track.title, subtitle = subtitle, trial = state.trial)

                Spacer(Modifier.height(10.dp))

                LyricsBox(
                    lyrics = state.lyric,
                    currentIndex = currentLyricIndex,
                    synced = syncedLyrics,
                    loading = state.lyricLoading,
                    positionMs = shownPosition,
                    isPlaying = state.isPlaying && !state.isBuffering,
                    wordByWord = state.lyricWordByWord,
                    scrubbing = scrubPosition != null,
                    onSeek = { seekTo(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))

                SeekArea(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onScrub = { scrubPosition = it },
                    onSeek = {
                        seekTo(it)
                        scrubPosition = null
                    },
                )

                Spacer(Modifier.height(12.dp))

                TransportRow(
                    isPlaying = state.isPlaying,
                    canSkip = state.queue.size > 1,
                    playMode = playMode,
                    onPlayMode = {
                        // 房间里模式是两端共享的：按同一个循环顺序推进一次，交给房间去改
                        // （服务端随下一帧广播回来，两端的图标一起变）。
                        if (seatHere) container.roomSync.setMode(playMode.next()) else container.playbackCenter.cyclePlayMode()
                    },
                    onQueue = { showQueue = true },
                    onPrevious = { previousTrack() },
                    onToggle = { toggleTransport() },
                    onNext = { nextTrack() },
                )

                Spacer(Modifier.height(30.dp))
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
        )

        PlaybackErrorCard(
            visible = state.error != null,
            // 错误被清掉时还要留在卡片上把退场动画放完，所以另存一份最后的内容。
            message = lastError.orEmpty(),
            onDismiss = container.playbackCenter::clearError,
            onRetry = {
                container.playbackCenter.clearError()
                container.playbackCenter.playAt(state.index)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                // 让开顶栏（收起箭头、更多）：卡片浮在它下面，不挡这两处操作。
                .padding(start = 16.dp, end = 16.dp, top = 56.dp),
        )
    }

    SnackbarMessages(
        hostState = snackbar,
        message = message,
        onConsumed = { message = null },
    )

    if (showQueue) {
        QueueSheet(
            // 房间接管时队列就是房间队列（镜像过来的），所以显示不用分岔；
            // 只有「改队列」这件事必须交给房间——本地删一首，200 毫秒后就会被镜像装回来。
            tracks = state.queue,
            currentIndex = state.index,
            onPlayAt = { index ->
                if (seatHere) container.roomSync.playAt(index) else container.playbackCenter.playAt(index)
            },
            onRemoveAt = { index ->
                if (seatHere) container.roomSync.removeAt(index) else container.playbackCenter.removeAt(index)
            },
            // 共享队列没有「清空」：那等于把对方正在听的歌也一起抹掉。
            onClear = if (seatHere) null else container.playbackCenter::clearQueue,
            onDismiss = { showQueue = false },
        )
    }

    if (showQuality) {
        ChoiceSheet(
            title = "音质",
            // 只列本曲**实际有**的档位，没有的不列出来打灰。`qualityOptions` 为空列表之外的
            // null 代表「清单完全不知道」（网关没给 qualities），那种情况才按全量列。
            options = state.qualityOptions ?: Quality.entries,
            // 勾在哪儿代表「之后要用哪一档」——改的是偏好。
            selected = quality,
            // 面板里的完整档位名按当前曲目所属平台取（QQ 客户端叫「HQ 高品质」这类）。
            optionLabel = { qualityMenuLabel(it, state.activePlatform) },
            optionSubtitle = { option ->
                // 体积来自曲目自身的 qualitySizes（键就是档位 id），缺省时只写规格，
                // 不把「不知道」当成 0 字节 —— 口径与网页端音质面板的「体积 · 规格」一致。
                val size = state.qualitySizes?.get(option.id)?.let(::formatQualitySize)
                val specLine = listOfNotNull(size, qualitySpec(option)).joinToString(" · ")
                // 偏好与实际可能不是一回事：网易云拿不到某一档时不报错，而是"按能给的给"，
                // 所以「正在播放」只能看 resolve 回来的实际档位，不能照偏好写。
                val statusLine = listOfNotNull(
                    "当前设置".takeIf { option == quality },
                    "正在播放".takeIf { option == state.actualQuality },
                ).joinToString(" · ").ifEmpty { null }
                listOfNotNull(specLine, statusLine).joinToString("\n").ifEmpty { null }
            },
            // 改的是偏好：PlaybackCenter 监听到之后会按新档位重建队列并接回进度。
            onSelect = { scope.launch { container.settings.setQuality(it) } },
            onDismiss = { showQuality = false },
        )
    }

    if (showActions) {
        TrackActionsSheet(
            track = track,
            isFavorite = isFavorite,
            activePlatform = state.activePlatform ?: Platform.UNKNOWN,
            onDismiss = { showActions = false },
            // 这一行写的是「当前」，所以用实际拿到的档位；还没解析出来时退回偏好（最接近的已知值）。
            qualityLabel = qualityLabel(state.actualQuality ?: quality, state.activePlatform),
            onPickQuality = { showQuality = true },
            onToggleFavorite = {
                scope.launch {
                    runCatching { container.libraryRepository.toggleFavorite(track) }
                        .onSuccess { favorite -> message = if (favorite) "已收藏" else "已取消收藏" }
                        .onFailure { error -> message = error.friendlyMessage() }
                }
            },
            onAddToPlaylist = { showAddToPlaylist = true },
            onSwitchPlatform = { platform ->
                container.playbackCenter.switchPlatform(platform)
                message = "已切换到 ${platform.label}"
            },
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            track = track,
            onMessage = { message = it },
            onDismiss = { showAddToPlaylist = false },
        )
    }

    if (showDevices) {
        DeviceSheet(
            onDismiss = { showDevices = false },
            onMessage = { message = it },
        )
    }

    if (showTogether) {
        TogetherSheet(
            onDismiss = { showTogether = false },
            onMessage = { message = it },
        )
    }
}

/* ------------------------------ 一起听（封面下沿那一条） ------------------------------ */

/**
 * 封面下沿那一块：两个人的头像（中间一个回形针）＋ 一起听读数。
 *
 * 都压在封面**里面**：封面是这一页最贵的面积，头像与读数摆进去，就不必在封面下面
 * 再多占一行——那一行会从歌词那儿抢走同样的高度。
 *
 * 上面铺一层从透明到半黑的渐变再压白字与头像：封面可能是任意一张图，浅色的、花哨的
 * 都有，直接压上去有一半的封面读不清。渐变只铺下沿，封面主体还是完整的。
 *
 * 整块可点：打开一起听面板（房间码、时长、转移、结束都在那里）。
 */
@Composable
private fun TogetherCoverBand(
    text: String,
    me: RoomMemberView?,
    partner: RoomMemberView?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 和封面同一套圆角：不裁的话下面两个角会从封面的圆角里支出去。
            .clip(MaterialTheme.shapes.small)
            .background(TOGETHER_COVER_SCRIM)
            .clickable(onClick = onClick)
            .padding(top = 34.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TogetherAvatar(member = me, fallback = "我", modifier = Modifier.size(34.dp))
            /*
             * 中间那个回形针＝「两个人被别在一起」。
             *
             * 不画「+」（看着像"再加一个人"）、也不画一条竖线（那是分隔符，不是关系）：
             * 回形针是「别在一起」最直白的那个形象。
             */
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .padding(horizontal = 9.dp)
                    .size(18.dp),
            )
            TogetherAvatar(member = partner, fallback = "友", modifier = Modifier.size(34.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                // 固定这个粉红，不跟强调色走：它压在深色渐变上，强调色可能是深色系，
                // 换一个用户调过的主题就看不见了。
                tint = TOGETHER_HEART,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // fill = false：文字短的时候让 Row 把它居中，长的时候就地省略号，不挤掉右边的箭头。
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 封面上的一个头像；[member] 为 null（对方还没来）时画一个空位圈。
 *
 * 空位刻意做成**空心圈 + 加号**，而不是一个「已经有人、只是灰掉」的头像：
 * 后者会让人以为房间里已经有别人的歌在放。
 */
@Composable
private fun TogetherAvatar(
    member: RoomMemberView?,
    fallback: String,
    modifier: Modifier = Modifier,
) {
    if (member == null) {
        Box(
            modifier = modifier.border(
                // 压在深色渐变上，描边统一用白色系，不跟主题的描边色走（那可能是浅色的）。
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "等待好友加入",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp),
            )
        }
        return
    }
    AccountAvatar(
        avatar = member.avatar,
        fallbackInitial = member.nickname.takeIf { it.isNotBlank() }?.take(1) ?: fallback,
        modifier = modifier,
    )
}

/** 一起听读数里那颗心：暖粉，压在任何封面上都认得出来。 */
private val TOGETHER_HEART = Color(0xFFFF5A79)

/**
 * 封面下沿那块的渐影（头像与读数底下那层从透明到黑的压暗）。
 *
 * 写成**显式停点**而不是三个等距颜色：三个等距的那条，在这么矮的一块上（约 95dp）
 * 前 1/3 几乎是全透明的，而头像正好落在"还没怎么压暗"的那一段上——看着就像没铺渐变。
 * 这里让头像所在的区间（约 35%–65%）就压到 0.2→0.45，到最底下 0.78。
 *
 * 只铺下沿：封面主体仍然完整（浅色、花哨的封面才需要它，但没必要为了它整张压暗）。
 */
private val TOGETHER_COVER_SCRIM = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.26f to Color.Black.copy(alpha = 0.14f),
    0.50f to Color.Black.copy(alpha = 0.40f),
    0.76f to Color.Black.copy(alpha = 0.64f),
    1.00f to Color.Black.copy(alpha = 0.80f),
)

/** 竖屏封面占屏宽的比例。 */
private const val COVER_WIDTH_FRACTION = 0.52f

/** 往下拖过这个距离就收起。 */
private val COLLAPSE_DRAG_THRESHOLD = 120.dp

/** 松手时的甩动速度超过这个也收起（轻快一甩，不用拖满）。 */
private val COLLAPSE_FLING_VELOCITY = 1250.dp

/** 顶栏那个按钮的图标由当前模式决定。 */
private val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.Order, PlayMode.All -> Icons.Rounded.Repeat
        PlayMode.One -> Icons.Rounded.RepeatOne
        PlayMode.Shuffle -> Icons.Rounded.Shuffle
    }

/* ------------------------------ 封面 ------------------------------ */

@Composable
private fun CoverArt(
    cover: String?,
    showBuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 封面永远不变成转圈：它是「这是哪首歌」的唯一标识，加载状态在它旁边报。
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(70.dp),
            )
        }

        if (showBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(30.dp)
                    .background(Color(0x8C000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/* ------------------------------ 曲目信息 ------------------------------ */

/**
 * 副标题：`歌手 · 专辑`，两边的名字各自可点。
 *
 * 用带 link 的 [AnnotatedString] 而不是把它们拆成一行 Text——这一行要保持原来的居中、
 * 单行、超长省略号的行为，拆成多个 Text 就得自己重做换行与省略。link 的样式会**盖掉**
 * AnnotatedString 上已有的样式（`TextLinkStyles` 的约定），所以链接色直接写在这里就行。
 *
 * 缺 ID 或平台未知的那一段（合并前的老数据、上游没给）就按普通文字画，点了没反应，
 * 总比拼出一个错的详情页地址强。
 */
private fun subtitleWithLinks(
    track: UnifiedTrack,
    linkColor: Color,
    onOpenArtist: (Platform, String, String) -> Unit,
    onOpenAlbum: (Platform, String, String) -> Unit,
): AnnotatedString {
    val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor))

    return buildAnnotatedString {
        val artists = track.artists.ifEmpty { listOf(ArtistRef(name = "未知歌手")) }
        artists.forEachIndexed { index, artist ->
            if (index > 0) append(" / ")

            val platform = artist.platform
            val id = artist.id
            if (platform != null && platform.isKnown && id != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "artist:${platform.id}:$id",
                        styles = linkStyles,
                    ) { onOpenArtist(platform, id, artist.name) }
                ) { append(artist.name) }
            } else {
                append(artist.name)
            }
        }

        val album = track.album
        val albumPlatform = album.platform
        val albumId = album.id
        if (album.name.isNotBlank()) {
            append(" · ")
            if (albumPlatform != null && albumPlatform.isKnown && albumId != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "album:${albumPlatform.id}:$albumId",
                        styles = linkStyles,
                    ) { onOpenAlbum(albumPlatform, albumId, album.name) }
                ) { append(album.name) }
            } else {
                append(album.name)
            }
        }
    }
}

@Composable
private fun TrackInfo(title: String, subtitle: AnnotatedString, trial: Boolean) {
    Text(
        text = title.ifBlank { "未知歌曲" },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    // 受版权或会员限制时只能听到片段，这事必须说清楚。
    if (trial) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "仅试听片段 · 可在「更多」里切换音源试试",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 跟随状态的提示已从封面挪到顶栏（投送按钮左边）：封面上的角标只适合放「这一路的
 * 字节从哪儿来」这类元信息，「我正在听谁的」是个要随时能退出的操作状态，
 * 放在操作区更顺手。
 */


/**
 * 这次播放的字节从哪儿来：本地缓存 / CDN 直连 / 网关中转。
 *
 * 贴在封面右上角：那儿通常是空的，而曲目信息那块已经挤了标题、歌手和试听提示。
 *
 * 不是装饰——「为什么这首歌要等半天」「为什么流量跑得比预期多」都能从这枚标签上
 * 找到答案，所以做成常驻的，而不是只在出错时才出现。
 */
@Composable
private fun RouteBadge(route: PlaybackRoute, modifier: Modifier = Modifier) {
    // 还没读到字节时先不画：这时候说什么都是猜的，宁可空一下也别闪一个错的标签。
    if (route == PlaybackRoute.Unknown) return

    val icon: ImageVector
    val container: Color
    val content: Color
    when (route) {
        // Unknown 由调用方挡掉了，这里只给它一个不出错的落点。
        PlaybackRoute.Unknown -> {
            icon = Icons.Rounded.MusicNote
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        PlaybackRoute.Cached -> {
            icon = Icons.Rounded.Cached
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        PlaybackRoute.Direct -> {
            icon = Icons.Rounded.Bolt
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }

        PlaybackRoute.Proxy -> {
            icon = Icons.Rounded.SwapHoriz
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        shape = CircleShape,
        color = container,
        // 封面本身就带圆角，标签再往里让一点，别压在封面的角上。
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = route.hint,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = route.label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

/* ------------------------------ 进度 ------------------------------ */

/**
 * 进度条。
 *
 * 拖动状态由它自己拿着：松手时读到的才是真正最后拖到的那个值，
 * 而不是某一帧传进来的位置。
 *
 * @param onScrub 拖动过程中的位置，页面拿它让歌词一起动。
 * @param onSeek 松手后的最终位置——只有这一下才真的去 seek，拖动中一直 seek 会把上游请求打爆。
 */
@Composable
private fun SeekArea(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val sliderMax = durationMs.toFloat().coerceAtLeast(1f)
    val shown = if (dragging) dragValue.toLong() else positionMs

    Slider(
        value = shown.toFloat().coerceIn(0f, sliderMax),
        onValueChange = {
            dragging = true
            dragValue = it
            onScrub(it.toLong())
        },
        onValueChangeFinished = {
            onSeek(dragValue.toLong())
            dragging = false
        },
        valueRange = 0f..sliderMax,
        enabled = durationMs > 0,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(shown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------ 控制 ------------------------------ */

/**
 * 控制排：两边各一个功能键，中间三个传输键。
 *
 * 左右两个 `IconButton` 宽度一样，所以中间那组是真的居中，而不是「看起来差不多」。
 */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    canSkip: Boolean,
    playMode: PlayMode,
    onPlayMode: () -> Unit,
    onQueue: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    /** 横屏短屏时按比例缩小整排按键，避免大按钮顶到屏幕下沿被裁。 */
    playButtonSize: Dp = 70.dp,
) {
    val skipSize = playButtonSize * 0.686f
    val iconSize = playButtonSize * 0.486f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 一个按钮轮四种顺序：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序。
        IconButton(onClick = onPlayMode) {
            Icon(
                imageVector = playMode.icon,
                contentDescription = playMode.label,
                tint = if (playMode == PlayMode.Order) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = onPrevious,
                enabled = canSkip,
                modifier = Modifier.size(skipSize),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(onClick = onToggle, modifier = Modifier.size(playButtonSize)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(iconSize),
                )
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(
                onClick = onNext,
                enabled = canSkip,
                modifier = Modifier.size(skipSize),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
            }
        }

        IconButton(onClick = onQueue) {
            Icon(
                imageVector = Icons.Rounded.PlaylistPlay,
                contentDescription = "播放队列",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ------------------------------ 歌词 ------------------------------ */

/**
 * 逐字歌词用的连续进度。
 *
 * 播放器状态是 400ms 轮询一次（见 `PlaybackCenter.startPolling`），拿它直接算逐字进度
 * 就会一格一格跳。这里以每次状态更新为锚点，用 [withFrameNanos] 在两拍之间按帧插值——
 * 和网页端 `requestAnimationFrame` + `preciseTime()` 是同一套思路。
 *
 * 返回 [State] 而不是值：只有真正订阅它的那一行歌词会逐帧重组，整页不会跟着每帧重画。
 * 暂停、缓冲、拖动进度、非逐字歌词时都不起帧循环。
 */
@Composable
private fun rememberLyricPositionMs(positionMs: Long, active: Boolean): State<Long> {
    val live = remember { mutableLongStateOf(positionMs) }
    val anchorPosition = remember { mutableLongStateOf(positionMs) }
    val anchorNanos = remember { mutableLongStateOf(System.nanoTime()) }

    // 状态每 400ms 来一拍：重新对齐锚点，seek / 拖动造成的跳变也在这里被吸收。
    LaunchedEffect(positionMs) {
        anchorPosition.longValue = positionMs
        anchorNanos.longValue = System.nanoTime()
        live.longValue = positionMs
    }

    LaunchedEffect(active) {
        if (!active) {
            live.longValue = positionMs
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { frameTimeNanos ->
                // 最长只外推 600ms：状态流若被后台卡住，歌词宁可停一下也不要跑飞。
                val elapsedMs = ((frameTimeNanos - anchorNanos.longValue) / 1_000_000L)
                    .coerceIn(0L, 600L)
                live.longValue = anchorPosition.longValue + elapsedMs
            }
        }
    }
    return live
}

/**
 * 歌词面板：自动把当前行滚到中间；用户自己拖动时暂停跟随并给一个「回到当前」，
 * 停手 3 秒后恢复。
 */
@Composable
private fun LyricsBox(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    synced: Boolean,
    loading: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    wordByWord: Boolean,
    scrubbing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val livePosition = rememberLyricPositionMs(
        positionMs = positionMs,
        active = isPlaying && wordByWord && !scrubbing,
    )
    /*
     * 逐字模式连「换行」也用精确时间：否则 400ms 那一拍还没到时，上一行已经唱完、
     * 下一行还没亮，中间会出现一段明显的空档。derivedStateOf 只在行号真的变了才通知重组，
     * 不会因为每帧都在算 activeIndex 而把整块歌词重画一遍。
     */
    val effectiveIndex by remember(lyrics, synced, currentIndex, wordByWord) {
        derivedStateOf {
            if (synced && wordByWord) {
                LrcParser.activeIndex(lyrics, livePosition.value)
            } else {
                currentIndex
            }
        }
    }

    // ── 用户拖动检测 ──
    var userScrolling by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !autoScrolling) userScrolling = true
            }
    }

    // ── 自动滚动：让当前行居中 ──
    // 每行高度是固定的（见 LyricLineRow），所以行高可以直接算，不用两阶段测量。
    // scrollToItem 的偏移是有符号的：正值把这一行往上推，所以 +半行就正好居中。
    LaunchedEffect(effectiveIndex, viewportPx, userScrolling) {
        if (effectiveIndex >= 0 && viewportPx > 0 && !userScrolling) {
            autoScrolling = true
            val itemHeightPx = with(density) {
                (LYRIC_LINE_HEIGHT.toPx() + LYRIC_V_PADDING.toPx() * 2).roundToInt()
            }
            listState.animateScrollToItem(effectiveIndex, itemHeightPx / 2)
            autoScrolling = false
        }
    }

    // ── 停手 3 秒后恢复跟随 ──
    LaunchedEffect(userScrolling) {
        if (userScrolling) {
            delay(3000)
            userScrolling = false
        }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { viewportPx = it.height },
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在读取歌词…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyrics.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "未找到歌词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val halfViewport = with(density) { (viewportPx / 2).toDp() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = halfViewport),
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        // 只有当前这一行订阅每帧进度：其它行拿 400ms 那份就够，
                        // 否则整屏可见歌词都会跟着 60fps 重组。
                        val isActive = synced && index == effectiveIndex && line.words.isNotEmpty()
                        LyricLineRow(
                            text = line.text,
                            translation = line.trans,
                            timeMs = line.timeMs,
                            distance = if (synced) abs(index - effectiveIndex) else 0,
                            synced = synced,
                            words = line.words,
                            livePosition = if (isActive) livePosition else null,
                            onClick = { if (line.timeMs > 0L) onSeek(line.timeMs) },
                        )
                    }
                }

                // 上下两端渐隐，行滚进滚出卡片边缘时不会生硬地被切掉。
                LyricFade(MaterialTheme.colorScheme.surfaceContainerLow)

                if (userScrolling && effectiveIndex >= 0) {
                    FilledTonalButton(
                        onClick = { userScrolling = false },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("回到当前", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * 一行歌词。视觉重量由它离当前行的距离决定：越近越大越实，越远越退后。
 * 公式是连续的，整块看起来才像「在中间聚焦」的一整面，而不是一堆离散状态。
 */
@Composable
private fun LyricLineRow(
    text: String,
    translation: String?,
    timeMs: Long,
    distance: Int,
    synced: Boolean,
    words: List<LyricWord>,
    livePosition: State<Long>?,
    onClick: () -> Unit,
) {
    val isCurrent = distance == 0
    /*
     * 当前行 + 有逐字歌词时始终走 WordByWordText。livePosition 偶发为 null（synced 抖动、
     * isActive 切换间隙）时用全 0 进度兜底，不要回退到整行单色 Text——否则会闪一帧全亮。
     */
    val wordProgress = if (isCurrent && words.isNotEmpty()) {
        if (livePosition != null) {
            LrcParser.wordProgress(words, livePosition.value)
        } else {
            FloatArray(words.size) { 0f }
        }
    } else {
        null
    }

    // 连续衰减：字号 22sp(d=0) → 16sp(远)，透明度 1.0 → 0.2。
    val targetSize = if (!synced) 16f else 16f + 6f / (1f + distance * 1.5f)
    val targetAlpha = if (!synced) 1f else max(0.2f, 1f / (1f + distance * 0.8f))

    val size by animateFloatAsState(targetSize, lyricTransition, label = "lyricSize")
    val alpha by animateFloatAsState(targetAlpha, lyricTransition, label = "lyricAlpha")
    val colour by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "lyricColour",
    )

    /*
     * 当前行用主题色渐变（primary → secondary）+ 发光阴影 + 轻微放大，
     * 和桌面端 Classic 模式的观感对齐；非当前行保持单色。
     */
    val textBrush: Brush? = if (isCurrent) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
            ),
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.045f else 1f,
        animationSpec = lyricTransition,
        label = "lyricScale",
    )

    /*
     * 逐字歌词：每个字按演唱进度在「未唱（半透明）→ 已唱（实色）」之间连续过渡，
     * 而不是到点瞬间整字变色。配合上面的逐帧进度就是卡拉 OK 的顺滑擦除感。
     *
     * 网页端靠 CSS background-clip 做字内擦除，Compose 的 SpanStyle 只能给整个字一个
     * 颜色，所以这里用颜色插值代替；不再 remember 结果——进度每帧都在变，缓存命中不了，
     * 留着只会白做一次 snapshot 记录。没有字级时间轴时仍是整行一个颜色。
     */
    val idleColour = colour.copy(alpha = 0.42f)
    val hasWordProgress = words.isNotEmpty() && wordProgress != null
    val lyricText = if (hasWordProgress) {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val progress = wordProgress!!.getOrElse(index) { 0f }
                val start = length
                append(word.text)
                addStyle(
                    SpanStyle(color = lerp(idleColour, colour, progress)),
                    start,
                    length,
                )
            }
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = synced && timeMs > 0L, onClick = onClick)
            .padding(vertical = LYRIC_V_PADDING, horizontal = 16.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isCurrent && hasWordProgress) {
            // 当前行 + 逐字歌词：用 Canvas 画字内擦除（左半主题色、右半半透），
            // 模拟桌面端 CSS background-clip:text 的卡拉 OK 效果。
            WordByWordText(
                text = text,
                words = words,
                wordProgress = wordProgress!!,
                fontSize = size.sp,
                lineHeight = LYRIC_LINE_HEIGHT,
                fontWeight = FontWeight.Bold,
                activeColor = colour,
                idleColor = idleColour,
            )
        } else if (lyricText != null) {
            // 非当前行但有逐字歌词：用 AnnotatedString 逐字插值上色。
            Text(
                text = lyricText,
                fontSize = size.sp,
                lineHeight = LYRIC_LINE_HEIGHT,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = colour,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        } else {
            // 无逐字歌词：当前行用渐变文字色（brush 放 TextStyle 里）。
            Text(
                text = text,
                fontSize = size.sp,
                lineHeight = LYRIC_LINE_HEIGHT,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = colour,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    brush = textBrush,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
        translation?.takeIf { it.isNotBlank() }?.let { translated ->
            Text(
                text = translated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 逐字擦除歌词：每个字的左半（已唱）是主题色，右半（未唱）是半透明色，
 * 分界点随演唱进度从左往右推进，还原桌面端 CSS `background-clip: text` 的卡拉 OK 观感。
 *
 * 实现思路：先用 [TextMeasurer] 量出每个字在布局里的边界框，然后在 Canvas 上
 *  1) 整行画一遍「未唱」色；
 *  2) 逐字用 [clipRect] 裁掉右半，再画一遍「已唱」色。
 * 两层叠加就是字内从左到右的擦除渐变。
 *
 * 文字布局只随 [text] / 字号 / 字重变化，不随每帧进度重测；只有 Canvas 绘制随
 * [wordProgress] 每帧重画，因此当前行 60fps 也不会触发文字重排。
 */
@Composable
private fun WordByWordText(
    text: String,
    words: List<LyricWord>,
    wordProgress: FloatArray,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    activeColor: Color,
    idleColor: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    val layoutResult = remember(text, fontSize, fontWeight, lineHeight) {
        textMeasurer.measure(AnnotatedString(text), style = textStyle)
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(with(density) { lineHeight.toDp() })) {
        // 居中：Canvas 宽度减去文字宽度，余量平分到左右。
        val textLeft = (size.width - layoutResult.size.width) / 2f
        val textTop = 0f

        // 1) 整行先画未唱色（半透），作为底色。
        drawText(
            textLayoutResult = layoutResult,
            color = idleColor,
            topLeft = Offset(textLeft, textTop),
        )

        // 2) 每个字裁剪已唱区域，叠画主题色。
        var charIndex = 0
        words.forEachIndexed { wordIndex, word ->
            val wordLen = word.text.length
            if (wordLen == 0) return@forEachIndexed
            val firstBox = layoutResult.getBoundingBox(charIndex)
            val lastBox = layoutResult.getBoundingBox(charIndex + wordLen - 1)
            val wordLeft = textLeft + firstBox.left
            val wordRight = textLeft + lastBox.right
            val progress = wordProgress.getOrElse(wordIndex) { 0f }
            val clipRight = wordLeft + (wordRight - wordLeft) * progress

            clipRect(
                left = wordLeft,
                top = textTop + firstBox.top,
                right = clipRight,
                bottom = textTop + lastBox.bottom,
            ) {
                drawText(
                    textLayoutResult = layoutResult,
                    color = activeColor,
                    topLeft = Offset(textLeft, textTop),
                )
            }
            charIndex += wordLen
        }
    }
}

/** 把歌词卡片上下两端渐隐进它的底色。 */
@Composable
private fun BoxScope.LyricFade(colour: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.verticalGradient(listOf(colour, Color.Transparent))),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colour))),
    )
}

/* ------------------------------ 空队列 ------------------------------ */

@Composable
private fun EmptyPlayer(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(text = "还没有正在播放的歌曲", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "去发现或搜索里点一首歌吧。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack) { Text("返回") }
    }
}
