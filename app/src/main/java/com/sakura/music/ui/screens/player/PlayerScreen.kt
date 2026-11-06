package com.sakura.music.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.core.player.LrcParser
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.formatQualitySize
import com.sakura.music.data.model.qualityLabel
import com.sakura.music.data.model.qualityMenuLabel
import com.sakura.music.data.model.qualitySpec
import com.sakura.music.data.prefs.NowPlayingStyle
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AddToPlaylistSheet
import com.sakura.music.ui.components.ChoiceSheet
import com.sakura.music.ui.components.DeviceSheet
import com.sakura.music.ui.components.PlaybackErrorCard
import com.sakura.music.ui.components.QueueSheet
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.TogetherSheet
import com.sakura.music.ui.components.TrackActionsSheet
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingSystemBars
import com.sakura.music.ui.screens.player.nowplaying.common.subtitleWithLinks
import com.sakura.music.ui.screens.player.nowplaying.styles.ClassicNowPlaying
import com.sakura.music.ui.screens.player.nowplaying.styles.ImmersiveNowPlaying
import com.sakura.music.ui.screens.player.nowplaying.styles.NativeNowPlaying
import com.sakura.music.ui.screens.player.nowplaying.styles.NebbiaNowPlaying
import com.sakura.music.ui.util.formatTogetherDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 缓冲超过这个时长才亮转圈：一次很快的 seek 不该让图标闪一下。 */
private const val BUFFER_GRACE_MS = 350L

/** 往下拖过这个距离就收起。 */
private val COLLAPSE_DRAG_THRESHOLD = 120.dp

/** 松手时的甩动速度超过这个也收起（轻快一甩，不用拖满）。 */
private val COLLAPSE_FLING_VELOCITY = 1250.dp

/**
 * 全屏播放器宿主。
 *
 * 状态收集、房间分流、弹层与收起手势都留在这里；页面视觉按 [NowPlayingStyle]
 * 分发给具体样式（原生 / 经典 / 沉浸 / 雾窗）。样式只读 [NowPlayingUiState]、
 * 只调 [NowPlayingActions]，不再各自去碰播放器或房间。
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
    val style by container.settings.nowPlayingStyle
        .collectAsStateWithLifecycle(initialValue = NowPlayingStyle.Default)

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

    /** 房间的播放**就在本机**；旁观时本机照席位发指令只会撞「你不在这个房间里」。 */
    val seatHere = inRoom && !spectating

    /*
     * 一起听读数的每秒节拍：暂停时播放状态等值、状态流不发射，读数会僵住。
     * 所以在房间里单独起一个每秒的节拍。
     */
    var togetherTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(inRoom) {
        if (!inRoom) return@LaunchedEffect
        while (true) {
            delay(1_000)
            togetherTick++
        }
    }
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
        if (seatHere) {
            container.roomSync.seek(positionMs / 1000.0)
        } else {
            container.playbackCenter.seekTo(positionMs)
        }
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

    val showBuffering = rememberShowBuffering(state.isBuffering)

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
     * 拖动时整页内容跟着往下走；松手时要么超过阈值直接收起，要么弹回原位。
     * 手势本身交给具体样式贴在它认为安全的那块区域（封面 / 顶栏空白处），
     * 因为新样式里歌词和流体画布都在抢竖向滚动。
     * ────────────────────────────────────────────────────────────*/
    val collapseOffset = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { COLLAPSE_DRAG_THRESHOLD.toPx() }
    val collapseFlingPx = with(density) { COLLAPSE_FLING_VELOCITY.toPx() }

    fun onCollapseDrag(amount: Float) {
        scope.launch {
            collapseOffset.snapTo((collapseOffset.value + amount).coerceAtLeast(0f))
        }
    }

    fun onCollapseEnd() {
        val velocity = velocityTracker.calculateVelocity().y
        if (collapseOffset.value > collapseThresholdPx || velocity > collapseFlingPx) {
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

    val shownPosition = scrubPosition ?: state.positionMs
    val syncedLyrics = LrcParser.isSynced(state.lyric)
    val currentLyricIndex = if (state.lyric.isEmpty()) {
        -1
    } else {
        state.lyric.indexOfLast { it.timeMs in 1..shownPosition }
    }
    val isFavorite = favorites.contains(track.key)
    val subtitle = subtitleWithLinks(
        track = track,
        linkColor = MaterialTheme.colorScheme.primary,
        onOpenArtist = { platform, id, name -> navigator.openArtist(platform, id, name) },
        onOpenAlbum = { platform, id, name -> navigator.openAlbum(platform, id, name) },
    )

    val uiState = NowPlayingUiState(
        track = track,
        subtitle = subtitle,
        positionMs = state.positionMs,
        shownPositionMs = shownPosition,
        scrubbing = scrubPosition != null,
        durationMs = state.durationMs,
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering,
        showBuffering = showBuffering,
        trial = state.trial,
        lyrics = state.lyric,
        lyricLoading = state.lyricLoading,
        lyricWordByWord = state.lyricWordByWord,
        lyricSynced = syncedLyrics,
        lyricIndex = currentLyricIndex,
        playMode = playMode,
        route = route,
        canSkip = state.queue.size > 1,
        volume = state.volume,
        sourcePlatform = state.activePlatform,
        hasMultipleSources = track.sources.size > 1,
        inRoom = inRoom,
        roomTogetherText = togetherBadgeText,
        me = container.roomSync.myAccountMember(),
        partner = container.roomSync.otherMember(),
        followingName = following?.name,
        radioLabel = state.radio?.let { "${it.platform.label}电台" },
        isFavorite = isFavorite,
        style = style,
    )

    val actions = NowPlayingActions(
        collapse = onCollapse,
        seek = {
            seekTo(it)
            scrubPosition = null
        },
        scrub = { scrubPosition = it },
        toggle = ::toggleTransport,
        previous = ::previousTrack,
        next = ::nextTrack,
        cyclePlayMode = {
            if (seatHere) {
                container.roomSync.setMode(playMode.next())
            } else {
                container.playbackCenter.cyclePlayMode()
            }
        },
        setVolume = { container.playbackCenter.setVolume(it) },
        openQueue = { showQueue = true },
        openActions = { showActions = true },
        openTogether = {
            if (state.radio != null) {
                message = "正在听私人电台，请先退出电台再使用「一起听」"
            } else {
                showTogether = true
            }
        },
        openDevices = { showDevices = true },
        openArtist = { platform, id, name -> navigator.openArtist(platform, id, name) },
        openAlbum = { platform, id, name -> navigator.openAlbum(platform, id, name) },
        stopFollow = { container.connectSync.stopFollow() },
        stopRadio = { container.playbackCenter.stopRadio() },
        setStyle = { picked -> scope.launch { container.settings.setNowPlayingStyle(picked) } },
        toggleFavorite = {
            scope.launch {
                runCatching { container.libraryRepository.toggleFavorite(track) }
                    .onSuccess { favorite -> message = if (favorite) "已收藏" else "已取消收藏" }
                    .onFailure { error -> message = error.friendlyMessage() }
            }
        },
    )

    // 沉浸 / 雾窗是固定深色画布，系统栏图标不能跟着应用的浅色主题走。
    NowPlayingSystemBars(style)

    Box(
        modifier = Modifier
            .fillMaxSize()
            /*
             * 位移加在**最外层**（连同底色一起）：只挪内容的话，上面空出来的那一条露出的
             * 还是这层不透明的底色，底下那页透不出来。
             */
            .offset { IntOffset(0, collapseOffset.value.roundToInt()) }
            .background(MaterialTheme.colorScheme.background),
    ) {
        /*
         * 命中测试会把落在**空白处**的点击一路透到下面那层（就会点到账号页或列表里的歌）。
         * 在内容底下垫一层只吃点击的挡板：按钮、封面拖动先命中，剩下的空白由它吃掉。
         */
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )

        AnimatedContent(
            targetState = style,
            transitionSpec = {
                fadeIn(tween(280, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(280, easing = FastOutSlowInEasing))
            },
            label = "nowPlayingStyle",
        ) { activeStyle ->
            when (activeStyle) {
                NowPlayingStyle.Native -> NativeNowPlaying(
                    state = uiState,
                    actions = actions,
                    collapseGesture = collapseGesture,
                    modifier = Modifier.fillMaxSize(),
                )

                NowPlayingStyle.Classic -> ClassicNowPlaying(
                    state = uiState,
                    actions = actions,
                    collapseGesture = collapseGesture,
                    modifier = Modifier.fillMaxSize(),
                )

                NowPlayingStyle.Immersive -> ImmersiveNowPlaying(
                    state = uiState,
                    actions = actions,
                    collapseGesture = collapseGesture,
                    modifier = Modifier.fillMaxSize(),
                )

                NowPlayingStyle.Nebbia -> NebbiaNowPlaying(
                    state = uiState,
                    actions = actions,
                    collapseGesture = collapseGesture,
                    modifier = Modifier.fillMaxSize(),
                )
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
            tracks = state.queue,
            currentIndex = state.index,
            onPlayAt = { index ->
                if (seatHere) {
                    container.roomSync.playAt(index)
                } else {
                    container.playbackCenter.playAt(index)
                }
            },
            onRemoveAt = { index ->
                if (seatHere) {
                    container.roomSync.removeAt(index)
                } else {
                    container.playbackCenter.removeAt(index)
                }
            },
            // 共享队列没有「清空」：那等于把对方正在听的歌也一起抹掉。
            onClear = if (seatHere) null else container.playbackCenter::clearQueue,
            onDismiss = { showQueue = false },
        )
    }

    if (showQuality) {
        ChoiceSheet(
            title = "音质",
            options = state.qualityOptions ?: Quality.entries,
            selected = quality,
            optionLabel = { qualityMenuLabel(it, state.activePlatform) },
            optionSubtitle = { option ->
                val size = state.qualitySizes?.get(option.id)?.let(::formatQualitySize)
                val specLine = listOfNotNull(size, qualitySpec(option)).joinToString(" · ")
                val statusLine = listOfNotNull(
                    "当前设置".takeIf { option == quality },
                    "正在播放".takeIf { option == state.actualQuality },
                ).joinToString(" · ").ifEmpty { null }
                listOfNotNull(specLine, statusLine).joinToString("\n").ifEmpty { null }
            },
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

/** 缓冲宽限期：进缓冲后等一会儿再亮转圈。 */
@Composable
private fun rememberShowBuffering(isBuffering: Boolean): Boolean {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(BUFFER_GRACE_MS)
            show = true
        } else {
            show = false
        }
    }
    return show
}

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
