package com.sakura.music.data.network

import android.content.Context
import android.net.ConnectivityManager
import com.sakura.music.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 「能不能联网取音频」这件事的答案。
 *
 * 设置是 DataStore 里的（异步读），而数据源在播放线程上打开、没有挂起的机会，
 * 所以这里把开关同步缓存在内存里，跟着设置流更新；网络状态则每次现问系统。
 *
 * 只关心音频取流：歌词、专辑信息这些几十 KB 的请求不拦——那不是用户想省的流量。
 */
class NetworkPolicy(
    context: Context,
    settings: SettingsStore,
    scope: CoroutineScope,
) {

    private val connectivity =
        context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var wifiOnly: Boolean = false

    init {
        scope.launch {
            settings.wifiOnly.collect { wifiOnly = it }
        }
    }

    /**
     * 当前网络是否按流量计费。
     *
     * 用系统的「计费网络」判断而不是「是不是 Wi-Fi」：手机热点、部分随身 Wi-Fi 也是计费的，
     * 那些场景下用户同样不想让它偷偷跑流量。没有活动网络时返回 false——反正也连不上，
     * 让数据源按正常流程失败，报出来的错误更准确。
     */
    fun isMetered(): Boolean = runCatching {
        connectivity?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)

    /** 现在允许联网取音频吗。 */
    fun mayFetchAudio(): Boolean = !wifiOnly || !isMetered()

    companion object {
        /**
         * 被拦下时给用户看的话。
         *
         * 要说清「为什么」和「怎么办」：这不是网络故障，是用户自己开的开关——
         * 如果只报一句「播放失败」，他只会以为是软件坏了。
         */
        const val METERED_BLOCKED_MESSAGE =
            "「仅 Wi-Fi 播放」已开启，移动网络下不播放未缓存的歌曲。" +
                "可以连上 Wi-Fi，或在「设置 → 播放」里关掉这个开关。"
    }
}

/**
 * 被「仅 Wi-Fi」拦下的取流请求。
 *
 * 单独一个类型是为了让错误文案层一眼认出它：ExoPlayer 会把数据源的 IOException
 * 包进 cause 链，不区分类型的话只能当成普通的网络故障，报出来的原因就完全不对了。
 */
class MeteredBlockedException : IOException(NetworkPolicy.METERED_BLOCKED_MESSAGE)
