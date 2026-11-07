package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingTopBar
import com.sakura.music.ui.screens.player.nowplaying.common.nebbiaDesktopPalette
import com.sakura.music.ui.screens.player.nowplaying.common.rememberCoverPalette
import com.sakura.music.ui.screens.player.nowplaying.web.NowPlayingWebView
import com.sakura.music.ui.screens.player.nowplaying.web.WebEffectStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 三个 WebView 效果（雾窗 / 绘光 / 町景）共用的唯一外壳。
 *
 * 它们共享同一套 JS 桥协议、同一个 WebView 宿主、同一副顶栏与玻璃胶囊播放条，
 * 差别只有画布里的引擎，所以这里只按 effectId 换 assets 目录：
 * `assets/nowplaying/<effectId>/index.html`。
 *
 * 画布之外的那一层 Compose 负责：首帧淡入、下拉收起、WebGL 不可用时退回当前歌词，
 * 右下角可展开的播放条，以及顶栏齿轮打开的设置面板。加第四个 WebView 样式时只需
 * 在 `engine/` 放引擎、在 `effects/` 放外壳并登记 [com.sakura.music.data.prefs.NowPlayingStyle]。
 */
@Composable
fun WebEffectNowPlaying(
    effectId: String,
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val palette = nebbiaDesktopPalette()
    val cover = rememberCoverPalette(state.track.album.cover)
    val status = remember { WebEffectStatus() }
    var bootVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsJson by remember(effectId) { mutableStateOf("{}") }
    val hasSettings = remember(effectId) { specsFor(effectId).isNotEmpty() }

    // 一个样式一份 tuning patch：拖动时先本地预览，松手 / 切换才写盘。
    LaunchedEffect(effectId) {
        container.settings.effectSettings(effectId).collect { settingsJson = it }
    }

    // 首帧一到先等画布自己的淡入走完，再撤加载层。
    LaunchedEffect(status.firstFrame, status.failed) {
        if (status.firstFrame || status.failed) {
            delay(320)
            bootVisible = false
        }
    }

    val canvasAlpha by animateFloatAsState(
        targetValue = if (status.firstFrame) 1f else 0f,
        animationSpec = tween(900, delayMillis = 100),
        label = "webEffectCanvasAlpha",
    )
    val canvasScale by animateFloatAsState(
        targetValue = if (status.firstFrame) 1f else 1.03f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing, delayMillis = 100),
        label = "webEffectCanvasScale",
    )

    Box(modifier = modifier.background(Color(0xFF05070A))) {
        NowPlayingWebView(
            effectId = effectId,
            state = state,
            palette = palette,
            cover = cover,
            status = status,
            settingsJson = settingsJson,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = canvasAlpha
                    scaleX = canvasScale
                    scaleY = canvasScale
                },
        )

        // WebGL 不可用：退回「只显示当前这句」，跟网页端 fallback 一个意思。
        if (status.failed) {
            FallbackLine(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp)
                    .padding(top = 160.dp, bottom = 220.dp),
            )
        }

        // 手势板：压在 WebView 上，空白处不再把触摸漏给画布。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .then(collapseGesture),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NowPlayingTopBar(
                state = state,
                actions = actions,
                skin = NowPlayingSkin.Desktop,
                onOpenEffectSettings = if (hasSettings) {
                    { showSettings = true }
                } else {
                    null
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.weight(1f))
        }

        NebbiaChip(
            state = state,
            actions = actions,
            palette = palette,
            cover = state.track.album.cover,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
        )

        if (bootVisible && !status.firstFrame) {
            BootFog(Modifier.fillMaxSize())
        }
    }

    if (showSettings) {
        EffectSettingsSheet(
            effectId = effectId,
            palette = palette,
            settingsJson = settingsJson,
            onChange = { json, commit ->
                settingsJson = json
                if (commit) {
                    scope.launch { container.settings.setEffectSettings(effectId, json) }
                }
            },
            onDismiss = { showSettings = false },
        )
    }
}
