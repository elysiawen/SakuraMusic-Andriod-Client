package com.sakura.music.ui

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sakura.music.AppContainer
import com.sakura.music.data.model.Platform
import com.sakura.music.data.prefs.ThemeMode
import com.sakura.music.data.prefs.isDark
import com.sakura.music.ui.components.LocalMusicController
import com.sakura.music.ui.components.MiniPlayerBar
import com.sakura.music.ui.components.rememberMusicController
import com.sakura.music.ui.navigation.CollectionKind
import com.sakura.music.ui.navigation.Routes
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.screens.account.AccountDetailScreen
import com.sakura.music.ui.screens.account.AccountScreen
import com.sakura.music.ui.screens.collection.CollectionScreen
import com.sakura.music.ui.screens.home.HomeScreen
import com.sakura.music.ui.screens.library.FavoritesScreen
import com.sakura.music.ui.screens.login.LoginScreen
import com.sakura.music.ui.screens.media.AlbumScreen
import com.sakura.music.ui.screens.media.ArtistScreen
import com.sakura.music.ui.screens.player.PlayerScreen
import com.sakura.music.ui.screens.playlist.PlaylistScreen
import com.sakura.music.ui.screens.search.SearchScreen
import com.sakura.music.ui.screens.settings.SettingsScreen
import com.sakura.music.ui.theme.AccentTheme
import com.sakura.music.ui.theme.SakuraTheme
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 播放器浮层。
 *
 * 它**不是**导航栈里的一页，而是盖在导航内容之上的浮层：
 * - 往下拖 / 收起时，底下的页面（发现、账号、详情页…）还组合着，拖下来能看到它们；
 * - 系统返回键先收起播放器，而不是把底下的页面弹掉；
 * - 队列、迷你播放条、播放器三者看到的是同一个播放会话。
 *
 * 从播放页跳去歌手 / 专辑页时它只是**先藏起来**，不是关掉：用户从详情页返回，
 * 回到出发那一页时它自己回来——返回键那条链才是「详情页 → 播放页 → 出发页」。
 *
 * 它一直是个浮层（而不是导航栈里的一页），是因为「往下拖能看到底下的页面」这个交互
 * 需要底下的页面还组合着。代价是：它盖在导航内容之上，所以详情页自己的推入 / 返回动画
 * 大半被它挡住——转场只能由它来演。这也正是它在跳转时不该用竖向滑动的原因（见下面的
 * [AnimatedVisibility]）：它扮的是转场里「被推走 / 重新露出来」的那一页，得跟着导航那一套走。
 */
@Composable
private fun PlayerHost(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    controller: MediaController?,
) {
    /*
     * 导航控制器提到这一层，而不是留在 SakuraNavigation 里。
     *
     * 因为播放器浮层是**盖在导航之上的兄弟节点**，够不到 SakuraNavigation 内部的东西；
     * 而播放页里点歌手 / 专辑名要跳转，两边就得共用同一个 navigator。
     * 提上来之后两者的生命周期不变：都跟着登录态一起被创建和销毁。
     */
    val navController = rememberNavController()
    val navigator = remember(navController) { SakuraNavigator(navController) }

    /*
     * 播放页是从哪一页打开的。
     *
     * 用它决定浮层此刻该不该露出来：跳去详情页之后（当前 entry 换了）先藏起来，
     * 不然会盖住详情页；回到出发那一页时（当前 entry 又是它）自动回来。
     *
     * 比的是 entry **本体**而不是它所在的路由：详情页之间互相跳（歌手 → 歌手、
     * 歌手 → 专辑）时路由可能一模一样，拿路由比会把浮层留在上面盖住新页面。
     *
     * 它刻意不是 saveable —— entry 对象活不过进程重建，重建后按「没有出处」处理，
     * 照常显示播放页（和以前的行为一致）。
     */
    var openedFrom by remember { mutableStateOf<NavBackStackEntry?>(null) }
    val currentEntry by navController.currentBackStackEntryAsState()
    /** 被详情页压住了：出发页还在栈里，只是当前页已经不是它。 */
    val coveredByDetail = openedFrom != null && openedFrom != currentEntry
    val playerVisible = open && !coveredByDetail

    /*
     * 这一次返回会不会正好把播放页露回来。
     *
     * 弹掉详情页之后落在哪一页，就是 `previousBackStackEntry`。只有它正是**打开播放页的那一页**
     * 时，返回的结果才是「播放页重新盖上来」；详情页摞详情页（歌手 → 专辑）时落点还是详情页，
     * 播放页仍旧被压着，那种情况交给 NavHost 按普通返回去演。
     */
    val revealsPlayer =
        open && coveredByDetail && navController.previousBackStackEntry == openedFrom

    /*
     * 这一次显露是不是「从详情页回来的」。
     *
     * 两种入场必须分开：
     * - 用户自己打开播放页（迷你条）：从下往上**升起**，它本来就是从迷你条那儿冒出来的；
     * - 从详情页回来：它不是被打开，是**被详情页压住之后又露出来**——该用导航返回时
     *   「上一页重新出现」的那套动画。
     *
     * 后者要是也来一遍「升起」，用户看到的就是播放页"又开了一次"，可它其实一直在那儿；
     * 何况底下的详情页正在退场，两套方向相反的动画叠在一起，正是看着别扭的根源。
     *
     * 只在被压住的时候置位；迷你条那条入口负责清掉。
     */
    var revealFromDetail by remember { mutableStateOf(false) }
    LaunchedEffect(open, coveredByDetail) {
        if (open && coveredByDetail) revealFromDetail = true
    }

    /*
     * 预测式返回时播放页的位移（像素）。
     *
     * 用 Animatable 而不是普通 state：拖动过程要**跟手**（snapTo），手势取消要**弹回去**
     * （animateTo），两种节奏都在它身上。
     */
    val dragOffset = remember { Animatable(0f) }
    // 回弹发生在手势协程已经被取消之后，得另起一个作用域才跑得动。
    val scope = rememberCoroutineScope()

    /*
     * 「详情页退回播放页」这一下的进度：1 = 播放页完整盖着，0 = 完全让给详情页。
     *
     * 播放页不在导航栈里，NavHost 的预测式返回只能预览它自己栈里的上一页，拖动时露出来的是
     * 播放页**底下**那一页——和松手后的结果对不上。所以这一种由我们自己演：同一组转场参数
     * （见 PAGE_SLIDE_FRACTION / PAGE_SLIDE_MS），导航层跟着进度右移淡出，播放页从左推上来。
     */
    val popPreview = remember { Animatable(1f) }
    var popGestureActive by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 进度 1 时播放页该刚好滑出屏幕，所以整段位移按这一层的高度算。
        val overlayHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        SakuraNavigation(
            navController = navController,
            navigator = navigator,
            controller = controller,
            // 浮层露着时，底下的导航内容要**一并从无障碍树里排除**（见 SakuraNavigation）。
            playerVisible = playerVisible,
            // 打开播放页时记下出发页，之后才知道该回到哪。
            onOpenPlayer = {
                openedFrom = navController.currentBackStackEntry
                // 用户自己打开的，用「升起」那套。
                revealFromDetail = false
                onOpen()
            },
            /*
             * 手势预览时把整块导航内容当成转场里的「上一页」挪走：
             * 右移 1/5 屏 + 淡出，和 popExitTransition 是同一组参数。
             * 用 graphicsLayer 而不是重组：进度每帧都在动，这条路径不经过 composition。
             */
            modifier = Modifier.graphicsLayer {
                // 不在手势里时 f = 0，等于这一层什么都没做（graphicsLayer 的属性会沿用上一次的
                // 值，所以两种状态都得显式写一遍）。
                val f = if (popGestureActive) popPreview.value else 0f
                alpha = 1f - f
                translationX = f * size.width / PAGE_SLIDE_FRACTION
            },
        )

        /*
         * 预测式返回：播放页开着的时候，整段手势归这一层。
         *
         * 不能只挂个 BackHandler 等松手再处理：`enableOnBackInvokedCallback` 打开之后，系统会
         * 把返回手势交给**能跟进度的那个回调**，只听「按下」的拿不到拖动过程——用户按住
         * 往后拖，画面一动不动，松手才「啪」地跳一下，预测式返回等于白开了。
         *
         * 两种情形分开演：
         * - 播放页正露着：按进度把浮层往下推，底下的页面随之露出来；松手关掉播放页，
         *   剩下的路交给 AnimatedVisibility 的退出动画接着滑完，中途滑回去则弹回原位。
         * - 详情页压着播放页：演的是「详情页退场、播放页重新盖上来」（见 popPreview），
         *   松手后真的弹栈——播放页此刻已经盖满屏，底下那一下转场看不见。
         *
         * 谁拿得到手势由**注册顺序**决定：dispatcher 只把整段手势交给最后注册的那个回调。
         * 这里必须晚于 NavHost 注册，否则手势仍归它，预览的还是它栈里的上一页。
         * 平时靠「打开播放页时才组合这一层」就够了；但 NavHost 藏在 Scaffold 的 subcompose 里，
         * 是**测量阶段**才注册的——首帧就带着播放页（旋转、进程重建）时这里反而更早，
         * 所以首帧之后再换一次 key，让它摘掉重加，稳落在最后。
         *
         * 详情页摞详情页（落点还是详情页）时这里不组合，手势照旧留给 NavHost。
         */
        var backRegistration by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { backRegistration++ }

        if (playerVisible || revealsPlayer) {
            key(backRegistration) {
                PredictiveBackHandler(enabled = true) { backEvent ->
                    if (revealsPlayer) {
                        var moved = false
                        popPreview.snapTo(0f)
                        try {
                            backEvent.collect {
                                // 进度真的到了才把浮层摆上来：系统若只给「按下」不给进度
                                // （旧版本的返回键就是如此），就当普通返回处理，别凭空演预览。
                                if (!moved) {
                                    moved = true
                                    popGestureActive = true
                                }
                                popPreview.snapTo(it.progress)
                            }
                            if (moved) {
                                // 松手的位置未必到 1，补完最后一段再弹栈。
                                popPreview.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                            }
                            navController.popBackStack()
                            popGestureActive = false
                        } catch (_: CancellationException) {
                            if (popGestureActive) {
                                scope.launch {
                                    popPreview.animateTo(
                                        0f,
                                        tween(180, easing = FastOutSlowInEasing),
                                    )
                                    popGestureActive = false
                                }
                            }
                        }
                    } else {
                        dragOffset.snapTo(0f)
                        try {
                            backEvent.collect { dragOffset.snapTo(it.progress * overlayHeightPx) }
                            // 提交：停在手指离开时的位置，让退出动画从这儿接着往下走。
                            onClose()
                        } catch (_: CancellationException) {
                            scope.launch {
                                dragOffset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }
            }
        }

        // 手势结束时位移留在原地让退场动画接住，等它下次露出来再归零，
        // 否则重新打开时浮层会先错开一截。
        LaunchedEffect(playerVisible) {
            if (playerVisible) {
                dragOffset.snapTo(0f)
                popPreview.snapTo(1f)
            }
        }

        // 跳去歌手 / 专辑页、再从那儿回来时，播放页在转场里扮的是**被推走 / 重新露出来的
        // 那一页**，所以直接用导航那一套参数（同一组常量，两边不会再各改各的）：
        // 被推走 = 淡出（导航里任何页面被下一个盖住都是这么退场的），重新露出来 = 从左侧滑入
        // + 淡入（和导航返回时「上一页」的入场逐帧一致）。只有用户自己开 / 关才用竖向滑动。
        AnimatedVisibility(
            // 触发返回手势时播放页虽然还「压着」，但要跟着手势一起露出来。
            visible = playerVisible || popGestureActive,
            // 手势拖到哪儿，浮层就停在哪儿；这个位移叠在入场 / 退场动画之上。
            modifier = Modifier.offset { IntOffset(0, dragOffset.value.roundToInt()) },
            // 手势预览期间浮层完全按进度画，再叠一层入场动画的话两段位移会加在一起。
            enter = when {
                popGestureActive -> EnterTransition.None
                revealFromDetail -> slideInHorizontally(
                    initialOffsetX = { -it / PAGE_SLIDE_FRACTION },
                    animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(PAGE_FADE_IN_MS))

                else -> slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(220))
            },
            exit = if (coveredByDetail) {
                fadeOut(tween(PAGE_FADE_OUT_MS))
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(220))
            },
        ) {
            /*
             * 进度即透明度与横向位置：0 时完全让位给详情页，1 时就是浮层本来的样子。
             * 正常显示时进度恒为 1，这一层等于没加。
             */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = popPreview.value
                        translationX =
                            -(1f - popPreview.value) * size.width / PAGE_SLIDE_FRACTION
                    },
            ) {
                PlayerScreen(onCollapse = onClose, navigator = navigator)
            }
        }
    }
}

@Composable
fun SakuraRoot(container: AppContainer) {
    val accent by container.settings.accent
        .collectAsStateWithLifecycle(initialValue = AccentTheme.Sakura)
    val themeMode by container.settings.themeMode
        .collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val auth by container.authRepository.state.collectAsStateWithLifecycle()

    /**
     * 播放器的一次性提示（例如「仅 Wi-Fi」拦下了这一首）。
     *
     * 在这里统一弹，是因为触发它的地方可能在任意列表页——用户在那儿点了一下播放，
     * 提示就得在那儿出现，而不是等他打开播放器页才看到。
     */
    val playbackNotice by container.playbackCenter.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(playbackNotice) {
        val text = playbackNotice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        container.playbackCenter.consumeNotice()
    }

    /**
     * 一起听的一次性提示（房间被对方结束、操作失败…）。
     *
     * 也在这里统一弹：触发点可能在播放页的面板里，但「对方结束了一起听」这类消息
     * 是**对面**的动作引起的，用户此刻没准已经收起播放器在看别的页面。
     */
    val roomNotice by container.roomSync.notice.collectAsStateWithLifecycle()
    LaunchedEffect(roomNotice) {
        val text = roomNotice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        container.roomSync.consumeNotice()
    }

    /** 播放器浮层开没开。开着的时候底下的页面还活着，收起就能看到它们。 */
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    // 整个应用共用一条播放会话：播放器页和迷你播放条驱动的是同一个播放器。
    val musicController = rememberMusicController()

    LaunchedEffect(musicController) { container.playbackCenter.attach(musicController) }

    // 界面走到后台就把播放现场落盘一次：用户「退出应用」基本都从这里走，
    // 而进程随时可能被系统收掉。同步写，因为接下来就没有下一次机会了。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, container) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                container.playbackCenter.persistSession(synchronous = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 启动时确认登录态；未登录时 /api/auth/me 返回 200 + null，不报错。
    LaunchedEffect(Unit) { container.authRepository.refresh() }

    LaunchedEffect(auth.isLoggedIn) {
        if (auth.isLoggedIn) {
            container.libraryRepository.refreshFavorites()
        } else {
            container.libraryRepository.clear()
        }
    }

    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalMusicController provides musicController,
    ) {
        SakuraTheme(accent = accent, darkTheme = themeMode.isDark()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                when {
                    // 登录态还没确认前先亮个品牌页，免得闪一下登录页又跳走。
                    !auth.resolved -> BrandSplash()

                    // 用 isLoggedIn 而不是「user 是否为空」：断网时问不出用户信息，
                    // 但本机还留着会话，此时该放人进主界面（至少能听下载好的歌），
                    // 而不是把他推到登录页——那里同样要联网，等于锁死。
                    !auth.isLoggedIn -> LoginScreen()

                    else -> PlayerHost(
                        open = playerOpen,
                        onOpen = { playerOpen = true },
                        onClose = { playerOpen = false },
                        controller = musicController,
                    )
                }
            }
        }
    }
}

/** 登录态未知时展示的品牌页。 */
@Composable
private fun BrandSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(text = "Sakura Music", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "正在连接网关…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 底部栏的一级页面，顺序即栏内顺序。 */
private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "发现", Icons.Rounded.Explore),
    BottomDestination(Routes.ACCOUNT, "账号", Icons.Rounded.Person),
)

/**
 * 切换一级页面时内容平移的幅度（屏幕宽度的几分之一）。
 * 刻意很小：够表达方向即可，不做整页滑动。
 */
private const val TAB_SLIDE_FRACTION = 8

/**
 * 底栏展开 / 收起后的内容高度（不含系统手势区）。
 *
 * 展开那份写成常量而不是取 `NavigationBarDefaults`：M3 里这个值改过几次名字（`Height` → `ContainerHeight`），
 * 写死 80dp（Material 规格里底栏的高度）不受版本影响；收起时矮一截，图标仍然居中。
 */
private val BAR_EXPANDED_HEIGHT = 80.dp
private val BAR_COLLAPSED_HEIGHT = 56.dp

/** 手指滑过这么远才收 / 放：太小的话列表的细微回弹会把底栏来回拨。 */
private val BAR_COLLAPSE_THRESHOLD = 24.dp

/**
 * 非页签切换（推入 / 返回详情页）时的页面转场参数。
 *
 * 播放器浮层也用这一组：它跳去歌手 / 专辑页、再从那儿回来时，扮的就是转场里「被推走 /
 * 重新露出来」的那一页。两边共用同一组常量，才不会各改各的、越走越不一个拍子。
 */
private const val PAGE_SLIDE_FRACTION = 5
private const val PAGE_SLIDE_MS = 260
private const val PAGE_FADE_IN_MS = 200
private const val PAGE_FADE_OUT_MS = 160

/** -1 = 往左切页，1 = 往右，0 = 不是一级页面之间的切换。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = Routes.mainDestinations.indexOf(initialState.destination.route)
    val to = Routes.mainDestinations.indexOf(targetState.destination.route)
    if (from < 0 || to < 0) return 0
    return (to - from).coerceIn(-1, 1)
}

/**
 * 导航骨架。
 *
 * 一级页面用底部栏切换（每个页签各自记住自己的滚动位置），详情页是压在它们之上的。
 * 播放器不在这里——它是盖在整棵导航树之上的浮层（见 [PlayerHost]），
 * 这样收起时底下的页面还组合着，拖下来就能看到。
 */
@Composable
private fun SakuraNavigation(
    navController: NavHostController,
    navigator: SakuraNavigator,
    controller: MediaController?,
    /**
     * 播放页浮层此刻是不是露着。
     *
     * 露着时下面这一整套（导航栏 + 迷你条 + 页面内容）**对无障碍要当成不存在**：它们被
     * 播放页盖住了，用户看不见也点不到（点击由播放页那层挡板吃掉）。不排除的话，TalkBack
     * 仍会聚焦到「发现 / 账号」和底下的列表行上，读出来的东西和屏幕上的完全对不上。
     */
    playerVisible: Boolean,
    onOpenPlayer: () -> Unit,
    /**
     * 手势预览时整块导航内容的位移 / 透明度（见 `PlayerHost` 里的 popPreview）。
     *
     * 挂在这里而不是外面再包一层，是为了让位移作用在**整块**（页面 + 底栏 + 迷你条）上，
     * 和系统返回时「上一页整块滑走」的样子一致。
     */
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // 首帧 back stack 还没建好，按起始页（发现）算，免得底部栏闪一下才出现。
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val onMainDestination = currentRoute in Routes.mainDestinations

    /*
     * 底栏「收起」：内容往上翻的时候压掉一截、只留图标，往回滑或换页签再放下来。
     *
     * 听的是嵌套滚动，而不是让一级页面各自上报滚动位置——发现 / 账号各有各的列表，
     * 从根上听一次就都覆盖了，页面那边不用知道底栏的存在。只认**手指拖动**：
     * 甩出去之后的惯性要是继续收放，底栏就成了自己在那儿动，看着像失控。
     */
    var barCollapsed by remember { mutableStateOf(false) }
    // 换页签就放下来：新的一页从顶部开始看，没有理由还是收着的。
    LaunchedEffect(currentRoute) { barCollapsed = false }

    val collapseThresholdPx = with(LocalDensity.current) { BAR_COLLAPSE_THRESHOLD.toPx() }
    val barNestedScroll = remember(collapseThresholdPx) {
        // 余量记在这里而不是 Composition 里：它只服务于「够不够一次收 / 放」，界面不看它。
        var remainder = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                remainder += available.y
                when {
                    remainder <= -collapseThresholdPx -> {
                        barCollapsed = true
                        remainder = 0f
                    }

                    remainder >= collapseThresholdPx -> {
                        barCollapsed = false
                        remainder = 0f
                    }
                }
                // 一个像素都不吃：底栏只是「顺便看一眼方向」，滚动的仍是列表。
                return Offset.Zero
            }
        }
    }

    /*
     * 底部那一组（迷你播放条 + 导航栏）脚下的空档。
     *
     * 它必须是动画出来的，不能在一帧里从「导航栏 + 手势区」换成「只剩手势区」——那正是
     * 点进详情页时迷你条突然往下一沉（脚下一下子少了导航栏那 80dp）、退出来时又猛抬
     * 回去的原因。现在导航栏连它自带的手势区一起收缩归零，这一层把同样的高度补上，
     * 两边同一条时长与缓动，迷你条的净位移于是是连续的：整组像一块板子从屏幕下沿滑进滑出。
     */
    val bottomGap by animateDpAsState(
        targetValue = if (onMainDestination) {
            0.dp
        } else {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        },
        animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "bottomGap",
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 详情页没有导航栏，而迷你播放条也可能因为没在播放而不出现，
                    // 所以手势区得由这一层让开——否则没播歌时列表底部会压在系统栏下面。
                    .padding(bottom = bottomGap)
                    // 横屏时系统导航栏可能在侧边，那一条和上面的底边空档无关：一级页面由
                    // 导航栏自己让（它铺满整行），其余时候由这一层让。只让左右，免得和底部栏
                    // 自己那份底部 insets（它还要靠它把手势区刷成导航栏底色）叠成两份。
                    .then(
                        if (onMainDestination) {
                            Modifier
                        } else {
                            Modifier.windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                            )
                        },
                    )
                    /*
                     * 播放页浮层露着时，这一组（迷你条 + 导航栏）还在组合——为的是「往下拖能看到
                     * 底下的页面」——但用户看不见、也点不到（点击已被播放页那层挡板吃掉），
                     * 所以对无障碍要当成不存在，否则 TalkBack 仍会聚焦到「发现 / 账号」上。
                     *
                     * `clearAndSetSemantics {}` 把这棵子树的语义整棵清掉（等于对无障碍「不存在」）。
                     * 不用 `enabled = false`：那会把导航栏染成禁用色，往下拖的时候看得出来；
                     * 这里要的只是「无障碍看不见」，视觉与手感都不动。
                     */
                    .then(
                        if (playerVisible) Modifier.clearAndSetSemantics { } else Modifier,
                    ),
            ) {
                MiniPlayerBar(controller = controller, onOpen = onOpenPlayer)

                AnimatedVisibility(
                    visible = onMainDestination,
                    // 顶部对齐地长 / 缩：内容的下沿跟着容器一起动，看着才是导航栏整条从下沿
                    // 滑进滑出，而不是在原地被压扁。容器顶端就是迷你条的下沿，两者同进同退。
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                    ),
                ) {
                    SakuraBottomBar(
                        currentRoute = currentRoute,
                        collapsed = barCollapsed,
                        onSelect = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                // 内容让开底部栏 + 迷你播放条的位置。
                .padding(bottom = padding.calculateBottomPadding())
                .background(MaterialTheme.colorScheme.background)
                // 底栏的收起 / 放下靠它听方向（见上面的 barNestedScroll）。
                .nestedScroll(barNestedScroll)
                // 同上：浮层露着时这一整页都被它盖住，对无障碍一并清掉（详情页把它盖住时不算）。
                .then(
                    if (playerVisible) Modifier.clearAndSetSemantics { } else Modifier,
                ),
            enterTransition = {
                if (tabDirection() != 0) {
                    slideInHorizontally(
                        initialOffsetX = { tabDirection() * it / TAB_SLIDE_FRACTION },
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(240))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it / PAGE_SLIDE_FRACTION },
                        animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(PAGE_FADE_IN_MS))
                }
            },
            exitTransition = {
                if (tabDirection() != 0) {
                    slideOutHorizontally(
                        targetOffsetX = { -tabDirection() * it / TAB_SLIDE_FRACTION },
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(180))
                } else {
                    fadeOut(tween(PAGE_FADE_OUT_MS))
                }
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / PAGE_SLIDE_FRACTION },
                    animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(PAGE_FADE_IN_MS))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / PAGE_SLIDE_FRACTION },
                    animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(180))
            },
        ) {
            composable(Routes.HOME) { HomeScreen(navigator = navigator) }
            composable(Routes.ACCOUNT) { AccountScreen(navigator = navigator) }
            composable(Routes.SEARCH) { SearchScreen(navigator = navigator) }
            composable(Routes.SETTINGS) { SettingsScreen(navigator = navigator) }

            composable(Routes.ACCOUNT_DETAIL) { AccountDetailScreen(navigator = navigator) }

            composable(
                route = Routes.COLLECTION,
                arguments = listOf(
                    navArgument(Routes.COLLECTION_ARG_PLATFORM) { type = NavType.StringType },
                    navArgument(Routes.COLLECTION_ARG_ID) { type = NavType.StringType },
                    navArgument(Routes.COLLECTION_ARG_KIND) { type = NavType.StringType },
                    navArgument(Routes.COLLECTION_ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                CollectionScreen(
                    navigator = navigator,
                    platform = Platform.fromId(entry.arguments?.getString(Routes.COLLECTION_ARG_PLATFORM)),
                    collectionId = entry.arguments?.getString(Routes.COLLECTION_ARG_ID).orEmpty(),
                    kind = CollectionKind.fromId(entry.arguments?.getString(Routes.COLLECTION_ARG_KIND)),
                    initialTitle = entry.arguments?.getString(Routes.COLLECTION_ARG_TITLE).orEmpty(),
                )
            }

            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(
                    navArgument(Routes.PLAYLIST_ARG_ID) { type = NavType.StringType },
                    navArgument(Routes.PLAYLIST_ARG_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                PlaylistScreen(
                    navigator = navigator,
                    playlistId = entry.arguments?.getString(Routes.PLAYLIST_ARG_ID).orEmpty(),
                    initialName = entry.arguments?.getString(Routes.PLAYLIST_ARG_NAME).orEmpty(),
                )
            }

            composable(Routes.FAVORITES) { FavoritesScreen(navigator = navigator) }

            composable(
                route = Routes.ARTIST,
                arguments = listOf(
                    navArgument(Routes.MEDIA_ARG_PLATFORM) { type = NavType.StringType },
                    navArgument(Routes.MEDIA_ARG_ID) { type = NavType.StringType },
                    navArgument(Routes.MEDIA_ARG_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                ArtistScreen(
                    navigator = navigator,
                    platform = Platform.fromId(entry.arguments?.getString(Routes.MEDIA_ARG_PLATFORM)),
                    artistId = entry.arguments?.getString(Routes.MEDIA_ARG_ID).orEmpty(),
                    initialName = entry.arguments?.getString(Routes.MEDIA_ARG_NAME).orEmpty(),
                )
            }

            composable(
                route = Routes.ALBUM,
                arguments = listOf(
                    navArgument(Routes.MEDIA_ARG_PLATFORM) { type = NavType.StringType },
                    navArgument(Routes.MEDIA_ARG_ID) { type = NavType.StringType },
                    navArgument(Routes.MEDIA_ARG_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                AlbumScreen(
                    navigator = navigator,
                    platform = Platform.fromId(entry.arguments?.getString(Routes.MEDIA_ARG_PLATFORM)),
                    albumId = entry.arguments?.getString(Routes.MEDIA_ARG_ID).orEmpty(),
                    initialName = entry.arguments?.getString(Routes.MEDIA_ARG_NAME).orEmpty(),
                )
            }

        }
    }
}

/** 底部栏：一级页面之间切换，选中项用强调色容器打底，与全站的选中样式一致。 */
@Composable
private fun SakuraBottomBar(
    currentRoute: String,
    /** 收起态：只留图标，整条矮一截（内容往上翻的时候用）。 */
    collapsed: Boolean,
    onSelect: (String) -> Unit,
) {
    /*
     * 压高度时要**把手势区那一段算进去**：`NavigationBar` 自己用 `windowInsetsPadding` 把系统
     * 手势区垫在内部（底色也要刷到那儿），只按内容高度去压会把垫底那段一起切掉，底部漏出一条内容色。
     */
    val insetsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentHeight by animateDpAsState(
        targetValue = if (collapsed) BAR_COLLAPSED_HEIGHT else BAR_EXPANDED_HEIGHT,
        animationSpec = tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "bottomBarHeight",
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.height(contentHeight + insetsBottom),
    ) {
        bottomDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        // 收起时没有文字兜底，图标得自己把名字报给读屏。
                        contentDescription = if (collapsed) destination.label else null,
                    )
                },
                // 收起时整个标签槽都不给：留个空槽的话图标还是会被往上挤，白占地方。
                label = if (collapsed) {
                    null
                } else {
                    {
                        Text(text = destination.label, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
