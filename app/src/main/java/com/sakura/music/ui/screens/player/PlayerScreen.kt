package com.sakura.music.ui.screens.player

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sakura.music.core.player.LrcParser
import com.sakura.music.core.player.LyricLine
import com.sakura.music.core.player.PlaybackRoute
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AddToPlaylistSheet
import com.sakura.music.ui.components.ChoiceSheet
import com.sakura.music.ui.components.PlaybackErrorCard
import com.sakura.music.ui.components.QueueSheet
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.TrackActionsSheet
import com.sakura.music.ui.util.formatDuration
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
 */
@Composable
fun PlayerScreen(onCollapse: () -> Unit) {
    val container = appContainer()
    val state by container.playbackCenter.state.collectAsStateWithLifecycle()
    // 角标直接订阅路由流：上报发生在播放线程，走播放状态那条路容易漏掉更新。
    val route by container.playbackRoute.route.collectAsStateWithLifecycle()
    val playMode by container.playbackCenter.playMode.collectAsStateWithLifecycle()
    val favorites by container.libraryRepository.favoriteKeys.collectAsStateWithLifecycle()
    val quality by container.settings.quality.collectAsStateWithLifecycle(initialValue = Quality.Default)

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // 拖动进度时歌词跟着走（但不打扰播放器）；松手后回到真实进度。
    var scrubPosition by remember { mutableStateOf<Long?>(null) }

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
    val subtitle = listOf(track.artistText.ifBlank { "未知歌手" }, track.album.name)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(0, collapseOffset.value.roundToInt()) }
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
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
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
                        }
                        Spacer(Modifier.height(12.dp))
                        TrackInfo(title = track.title, subtitle = subtitle, trial = state.trial)
                        Spacer(Modifier.height(10.dp))
                        SeekArea(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            onScrub = { scrubPosition = it },
                            onSeek = {
                                container.playbackCenter.seekTo(it)
                                scrubPosition = null
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        TransportRow(
                            isPlaying = state.isPlaying,
                            canSkip = state.queue.size > 1,
                            playMode = playMode,
                            onPlayMode = container.playbackCenter::cyclePlayMode,
                            onQueue = { showQueue = true },
                            onPrevious = container.playbackCenter::previous,
                            onToggle = container.playbackCenter::toggle,
                            onNext = container.playbackCenter::next,
                        )
                    }

                    Spacer(Modifier.width(18.dp))

                    LyricsBox(
                        lyrics = state.lyric,
                        currentIndex = currentLyricIndex,
                        synced = syncedLyrics,
                        loading = state.lyricLoading,
                        onSeek = container.playbackCenter::seekTo,
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
                }

                Spacer(Modifier.height(18.dp))

                TrackInfo(title = track.title, subtitle = subtitle, trial = state.trial)

                Spacer(Modifier.height(10.dp))

                LyricsBox(
                    lyrics = state.lyric,
                    currentIndex = currentLyricIndex,
                    synced = syncedLyrics,
                    loading = state.lyricLoading,
                    onSeek = container.playbackCenter::seekTo,
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
                        container.playbackCenter.seekTo(it)
                        scrubPosition = null
                    },
                )

                Spacer(Modifier.height(12.dp))

                TransportRow(
                    isPlaying = state.isPlaying,
                    canSkip = state.queue.size > 1,
                    playMode = playMode,
                    onPlayMode = container.playbackCenter::cyclePlayMode,
                    onQueue = { showQueue = true },
                    onPrevious = container.playbackCenter::previous,
                    onToggle = container.playbackCenter::toggle,
                    onNext = container.playbackCenter::next,
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
            tracks = state.queue,
            currentIndex = state.index,
            onPlayAt = container.playbackCenter::playAt,
            onRemoveAt = container.playbackCenter::removeAt,
            onClear = container.playbackCenter::clearQueue,
            onDismiss = { showQueue = false },
        )
    }

    if (showQuality) {
        ChoiceSheet(
            title = "音质",
            options = Quality.entries,
            selected = quality,
            optionLabel = { it.label },
            optionSubtitle = { option -> if (option == quality) "当前档位" else null },
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
            qualityLabel = quality.label,
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
}

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

@Composable
private fun TrackInfo(title: String, subtitle: String, trial: Boolean) {
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
) {
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
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(onClick = onToggle, modifier = Modifier.size(70.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            FilledIconButton(
                onClick = onNext,
                enabled = canSkip,
                modifier = Modifier.size(48.dp),
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
 * 歌词面板：自动把当前行滚到中间；用户自己拖动时暂停跟随并给一个「回到当前」，
 * 停手 3 秒后恢复。
 */
@Composable
private fun LyricsBox(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    synced: Boolean,
    loading: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

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
    LaunchedEffect(currentIndex, viewportPx, userScrolling) {
        if (currentIndex >= 0 && viewportPx > 0 && !userScrolling) {
            autoScrolling = true
            val itemHeightPx = with(density) {
                (LYRIC_LINE_HEIGHT.toPx() + LYRIC_V_PADDING.toPx() * 2).roundToInt()
            }
            listState.animateScrollToItem(currentIndex, itemHeightPx / 2)
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
                        LyricLineRow(
                            text = line.text,
                            translation = line.trans,
                            timeMs = line.timeMs,
                            distance = if (synced) abs(index - currentIndex) else 0,
                            synced = synced,
                            onClick = { if (line.timeMs > 0L) onSeek(line.timeMs) },
                        )
                    }
                }

                // 上下两端渐隐，行滚进滚出卡片边缘时不会生硬地被切掉。
                LyricFade(MaterialTheme.colorScheme.surfaceContainerLow)

                if (userScrolling && currentIndex >= 0) {
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
    onClick: () -> Unit,
) {
    val isCurrent = distance == 0

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = synced && timeMs > 0L, onClick = onClick)
            .padding(vertical = LYRIC_V_PADDING, horizontal = 16.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            fontSize = size.sp,
            // 每一行的行高都固定，当前行也不例外：行高变来变去会让列表在焦点移动时抖动，
            // 而且固定之后每项的高度正好可算——居中滚动就是靠这个。
            lineHeight = LYRIC_LINE_HEIGHT,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = colour,
            textAlign = TextAlign.Center,
            style = TextStyle(
                // Trim.Both 裁掉字体上下自带的空隙，渲染高度才与 lineHeight 一致。
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
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
