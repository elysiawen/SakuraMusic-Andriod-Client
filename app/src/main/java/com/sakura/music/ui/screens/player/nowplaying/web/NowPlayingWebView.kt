package com.sakura.music.ui.screens.player.nowplaying.web

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.CoverPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import org.json.JSONArray
import org.json.JSONObject

/** 三个网页端效果共用的 JS 接口名与全局对象名。 */
private const val BRIDGE_NAME = "AndroidNowPlaying"
private const val ASSET_ORIGIN = "https://appassets.androidplatform.net"

private const val EVENT_READY = "ready"
private const val EVENT_FIRST_FRAME = "firstFrame"
private const val EVENT_FAILED = "failed"

/** 页面就绪、出第一帧、运行时失败。 */
@Stable
class WebEffectStatus {
    var ready by mutableStateOf(false)
    var firstFrame by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/**
 * 网页端效果的通用 WebView 宿主。
 *
 * 雾窗原本就有这么一套宿主，但它把页面地址、接口名、全局对象都写死成 nebbia。
 * 绘光与町景沿用同样的歌 / 主题 / 播放进度协议，所以这里按 effectId 参数化，
 * 页面落在 `assets/nowplaying/<effectId>/index.html`；JS 侧统一挂 `window.nowPlaying`。
 *
 * WebView 本身不接触摸，上层仍有 Compose 手势板负责下拉收起。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NowPlayingWebView(
    effectId: String,
    state: NowPlayingUiState,
    palette: DesktopPalette,
    cover: CoverPalette?,
    status: WebEffectStatus,
    /** 该效果设置面板存的 tuning patch（JSON），空对象就是全默认。 */
    settingsJson: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 页面加载完成 / JS 就绪后 +1，让下面几条推送在页面真正能接的时候再补一遍。
    var pageTick by remember { mutableIntStateOf(0) }

    val themeJson = remember(palette.accent, palette.accent2, cover) {
        JSONObject()
            .put("accentColor", palette.accent.toHex())
            .put("secondaryColor", palette.accent2.toHex())
            .put("primaryColor", (cover?.primary ?: palette.accent).toHex())
            .put("backgroundColor", "#05070a")
            .toString()
    }
    val songJson = remember(state.track.key, state.lyrics) { buildSongJson(state) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", AssetsPathHandler(ctx))
                .build()

            status.ready = false
            status.firstFrame = false
            status.failed = false
            status.error = null

            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.rgb(5, 7, 10))
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isFocusable = false
                isFocusableInTouchMode = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = false
                }

                addJavascriptInterface(
                    EffectBridge { type, message ->
                        // JS 接口回调在线程池里，切回主线程再改 Compose 状态。
                        post {
                            when (type) {
                                EVENT_READY -> {
                                    status.ready = true
                                    pageTick++
                                }

                                EVENT_FIRST_FRAME -> {
                                    status.firstFrame = true
                                    status.failed = false
                                    status.error = null
                                }

                                EVENT_FAILED -> {
                                    status.failed = true
                                    status.error = message
                                }
                            }
                        }
                    },
                    BRIDGE_NAME,
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        return assetLoader.shouldInterceptRequest(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageTick++
                    }
                }

                loadUrl("$ASSET_ORIGIN/assets/nowplaying/$effectId/index.html")
                webView = this
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface(BRIDGE_NAME)
            view.stopLoading()
            view.destroy()
        },
    )

    /*
     * 全屏页还展开着就把应用切到后台时，跟着宿主生命周期停掉 WebView 的渲染。
     * 与雾窗那边同一套做法：只用实例级 onPause/onResume，不动全局 pauseTimers。
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(webView, lifecycleOwner) {
        val view = webView
        if (view == null) {
            onDispose { }
        } else {
            var paused = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        view.onPause()
                        paused = true
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        if (paused) {
                            view.onResume()
                            paused = false
                        }
                    }

                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // 设置面板的 tuning patch：页面就绪后补推一遍，改一项也会立刻过桥预览。
    LaunchedEffect(webView, pageTick, settingsJson) {
        webView?.callJson("setSettings", settingsJson)
    }
    LaunchedEffect(webView, pageTick, themeJson) {
        webView?.callJson("setTheme", themeJson)
    }
    LaunchedEffect(webView, pageTick, songJson) {
        webView?.callJson("setSong", songJson)
    }
    /*
     * 播放中每 400ms 推一次进度锚点，用来纠正 Web 侧时钟在缓冲 / seek 之后的漂移；
     * 暂停时播放状态快照不变，这一条不会重跑。
     */
    LaunchedEffect(webView, pageTick, state.isPlaying, state.shownPositionMs) {
        webView?.callJson(
            "setPlayback",
            JSONObject()
                .put("playing", state.isPlaying)
                .put("positionMs", state.shownPositionMs)
                .toString(),
        )
    }
}

/* ------------------------------ JS 桥 ------------------------------ */

private class EffectBridge(
    private val onEvent: (type: String, message: String?) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        val type = json.optString("type")
        val detail = json.optString("message").takeIf { it.isNotBlank() }
        onEvent(type, detail)
    }
}

private fun WebView.callJson(method: String, json: String) {
    evaluateJavascript(
        "window.nowPlaying&&window.nowPlaying.$method(JSON.parse(${JSONObject.quote(json)}))",
        null,
    )
}

private fun buildSongJson(state: NowPlayingUiState): String {
    val root = JSONObject()
        .put("seed", state.track.key)
        .put("title", state.track.title)
        .put("artist", state.track.artistText)

    val lines = JSONArray()
    state.lyrics.forEach { line ->
        val item = JSONObject()
            .put("text", line.text)
            .put("startMs", line.timeMs)
        line.endMs?.takeIf { it > 0 }?.let { item.put("endMs", it) }
        if (line.words.isNotEmpty()) {
            val words = JSONArray()
            line.words.forEach { word ->
                words.put(
                    JSONObject()
                        .put("text", word.text)
                        .put("startMs", word.startMs)
                        .put("endMs", word.endMs),
                )
            }
            item.put("words", words)
        }
        lines.put(item)
    }
    root.put("lines", lines)
    return root.toString()
}

private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
