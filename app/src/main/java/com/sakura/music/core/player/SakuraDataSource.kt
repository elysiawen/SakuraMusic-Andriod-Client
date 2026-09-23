package com.sakura.music.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sakura.music.data.network.MeteredBlockedException
import com.sakura.music.data.network.NetworkPolicy
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * 播放队列用的数据源。
 *
 * 队列里的 URI 是 `sakura://track/<platform>/<id>?quality=…`——只有坐标，没有地址。
 * 真正的地址（直连还是走网关）在这一层打开时才解析：
 *
 * 1. 直连平台 CDN（服务器带宽为零），失败就
 * 2. 回退到网关代理（`/api/stream?t=…`，兼容一切情况）。
 *
 * 直连失败会记住这个平台，后续不再重试直连，免得每次播放都先失败一次。
 * 代理返回 401 表示播放令牌过期，会丢掉缓存重新解析一次再试。
 *
 * 注意：这里用的是**不带会话 Cookie** 的 client。音频字节没必要带上登录态，
 * 更重要的是直连请求会打到平台 CDN 上，绝不能把 Sakura 的 Cookie 送给第三方。
 */
class SakuraDataSource(
    private val resolver: PlaybackResolver,
    private val httpFactory: OkHttpDataSource.Factory,
    /** 这一路到底走通了哪条，上报到这里供界面显示。 */
    private val routeTracker: PlaybackRouteTracker,
    /**
     * 「仅 Wi-Fi」：移动网络下不该联网取流。
     *
     * 给 null 表示不受这条策略约束——下载走的就是那条路：用户主动点下载是明确意图，
     * 不该被「自动播放别偷跑流量」的开关拦下。
     */
    private val networkPolicy: NetworkPolicy?,
) : BaseDataSource(/* isNetwork = */ true) {

    private var upstream: DataSource? = null
    private var openedUri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var lastError: IOException? = null
    private var lastStatusCode: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        // 「仅 Wi-Fi」开着又在流量网络下：这一首没缓存（有缓存的话缓存层根本不会开上游），
        // 直接拒绝，而不是偷偷把流量跑了。
        if (networkPolicy != null && !networkPolicy.mayFetchAudio()) {
            throw MeteredBlockedException()
        }

        val request = PlayRequest.from(dataSpec.uri)
            ?: throw IOException("无法识别的播放地址：${dataSpec.uri}")

        var resolved = resolver.resolveBlocking(request)
        var triedDirect = false
        var retriedAfterTokenExpiry = false

        while (true) {
            // 1) 直连优先。
            val direct = if (triedDirect) null else resolver.directOf(resolved)
            if (direct != null && !resolver.isProxyOnly(request.platform)) {
                triedDirect = true
                val (url, headers) = direct
                openWith(url, headers, dataSpec)?.let { length ->
                    // 开的是平台 CDN：整首歌的字节都没经过网关。
                    routeTracker.reportNetwork(request.cacheKey, PlaybackRoute.Direct)
                    transferStarted(dataSpec)
                    return length
                }
                // 防盗链拒绝（403）或 CDN 连不上：记下来，之后这个平台走网关中转。
                resolver.markProxyOnly(request.platform)
            }

            // 2) 网关代理兜底。
            openWith(resolver.proxyUrl(resolved), emptyMap(), dataSpec)?.let { length ->
                routeTracker.reportNetwork(request.cacheKey, PlaybackRoute.Proxy)
                transferStarted(dataSpec)
                return length
            }

            // 3) 令牌过期（10 分钟）时重新解析一次，仅此一次。
            if (lastStatusCode == 401 && !retriedAfterTokenExpiry) {
                retriedAfterTokenExpiry = true
                triedDirect = false
                resolver.invalidate(request)
                resolved = resolver.resolveBlocking(request)
                continue
            }

            throw lastError ?: IOException("无法播放该音频")
        }
    }

    /** 尝试打开一条路；失败时记下原因并返回 null，让调用方决定要不要换一条。 */
    private fun openWith(
        url: String,
        headers: Map<String, String>,
        dataSpec: DataSpec,
    ): Long? {
        val uri = Uri.parse(url)
        val source = httpFactory
            .setDefaultRequestProperties(headers)
            .createDataSource()

        return try {
            val length = source.open(dataSpec.withUri(uri))
            upstream = source
            openedUri = uri
            bytesRemaining = length
            lastError = null
            lastStatusCode = 0
            length
        } catch (error: IOException) {
            lastError = error
            lastStatusCode = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
            runCatching { source.close() }
            null
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val source = upstream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val read = source.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            close()
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        val source = upstream
        upstream = null
        openedUri = null
        bytesRemaining = C.LENGTH_UNSET.toLong()
        if (source != null) {
            try {
                source.close()
            } finally {
                transferEnded()
            }
        }
    }

    class Factory(
        private val resolver: PlaybackResolver,
        private val client: OkHttpClient,
        private val routeTracker: PlaybackRouteTracker,
        private val networkPolicy: NetworkPolicy? = null,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SakuraDataSource(
                resolver = resolver,
                httpFactory = OkHttpDataSource.Factory(client),
                routeTracker = routeTracker,
                networkPolicy = networkPolicy,
            )
    }
}
