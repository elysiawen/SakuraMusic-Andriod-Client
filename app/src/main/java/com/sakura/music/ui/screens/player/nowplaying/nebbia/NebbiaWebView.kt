package com.sakura.music.ui.screens.player.nowplaying.nebbia

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
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
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.CoverPalette
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import org.json.JSONArray
import org.json.JSONObject

/** 页面挂在资产加载器的固定源上，WebGL 与模块脚本都当它是同源 https 页面。 */
private const val NEBBIA_HOME =
    "https://appassets.androidplatform.net/assets/nowplaying/nebbia/index.html"

/** JS 桥的事件名。 */
private const val EVENT_READY = "ready"
private const val EVENT_FIRST_FRAME = "firstFrame"
private const val EVENT_FAILED = "failed"

/** 运行时的三条状态：页面就绪、出第一帧、WebGL 失败。 */
@Stable
class NebbiaWebStatus {
    var ready by mutableStateOf(false)
    var firstFrame by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/**
 * 雾窗的 WebView 宿主。
 *
 * 页面只负责画布；歌、主题、播放进度都从 Compose 侧推过去，避免在 Web 里重复实现
 * 播放器状态。WebView 本身不接触摸 —— 上层还有一层 Compose 手势板负责下拉收起。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NebbiaWebView(
    state: NowPlayingUiState,
    palette: DesktopPalette,
    cover: CoverPalette?,
    status: NebbiaWebStatus,
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
                    // 运行时不写 localStorage；关掉存储面能少一个被页面文件污染的口子。
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = false
                }

                addJavascriptInterface(
                    NebbiaBridge { type, message ->
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
                    "AndroidNebbia",
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
                        // 模块脚本可能还没跑完，先记一次；JS 的 ready 会再来一次。
                        pageTick++
                    }
                }

                loadUrl(NEBBIA_HOME)
                webView = this
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("AndroidNebbia")
            view.stopLoading()
            view.destroy()
        },
    )

    LaunchedEffect(webView, pageTick, themeJson) {
        webView?.callJson("setTheme", themeJson)
    }
    LaunchedEffect(webView, pageTick, songJson) {
        webView?.callJson("setSong", songJson)
    }
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

private class NebbiaBridge(
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

/** 传对象参数时走 JSON.parse，引号和控制字符都由 JSONObject.quote 处理。 */
private fun WebView.callJson(method: String, json: String) {
    evaluateJavascript(
        "window.nebbia&&window.nebbia.$method(JSON.parse(${JSONObject.quote(json)}))",
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
