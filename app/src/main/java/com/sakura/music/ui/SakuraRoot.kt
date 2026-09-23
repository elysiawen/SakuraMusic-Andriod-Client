package com.sakura.music.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavBackStackEntry
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

/**
 * 播放器浮层。
 *
 * 它**不是**导航栈里的一页，而是盖在导航内容之上的浮层：
 * - 往下拖 / 收起时，底下的页面（发现、账号、详情页…）还组合着，拖下来能看到它们；
 * - 系统返回键先收起播放器，而不是把底下的页面弹掉；
 * - 队列、迷你播放条、播放器三者看到的是同一个播放会话。
 */
@Composable
private fun PlayerHost(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    controller: MediaController?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SakuraNavigation(controller = controller, onOpenPlayer = onOpen)

        // 必须用「开着才组合」而不是常驻 + enabled 开关：
        // OnBackPressedDispatcher 是按注册顺序取最后一个生效的，而 NavHost 每次页面
        // 切换都会重新注册自己的「弹栈」回调——常驻的那个是启动时就注册的，
        // 永远排在它后面，于是系统返回会去弹底下的页面（首页除外，因为首页无可弹）。
        // 开着的时候才组合，注册时机就在 NavHost 之后，优先级才对。
        if (open) {
            androidx.activity.compose.BackHandler { onClose() }
        }


        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(220)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(220)),
        ) {
            PlayerScreen(onCollapse = onClose)
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
    controller: MediaController?,
    onOpenPlayer: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // 首帧 back stack 还没建好，按起始页（发现）算，免得底部栏闪一下才出现。
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val onMainDestination = currentRoute in Routes.mainDestinations
    val navigator = remember(navController) { SakuraNavigator(navController) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 一级页面下面紧跟着底部栏，系统手势区由导航栏自己让开；
                    // 详情页没有导航栏，而且迷你播放条也可能因为没在播放而不出现，
                    // 所以让这一层来让——否则没播歌时列表底部会压在系统栏下面。
                    .then(if (onMainDestination) Modifier else Modifier.navigationBarsPadding()),
            ) {
                MiniPlayerBar(controller = controller, onOpen = onOpenPlayer)

                if (onMainDestination) {
                    SakuraBottomBar(
                        currentRoute = currentRoute,
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
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                if (tabDirection() != 0) {
                    slideInHorizontally(
                        initialOffsetX = { tabDirection() * it / TAB_SLIDE_FRACTION },
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(240))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it / 5 },
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(200))
                }
            },
            exitTransition = {
                if (tabDirection() != 0) {
                    slideOutHorizontally(
                        targetOffsetX = { -tabDirection() * it / TAB_SLIDE_FRACTION },
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(180))
                } else {
                    fadeOut(tween(160))
                }
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 5 },
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 5 },
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
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
    onSelect: (String) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        bottomDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = {
                    Text(text = destination.label, style = MaterialTheme.typography.labelSmall)
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
