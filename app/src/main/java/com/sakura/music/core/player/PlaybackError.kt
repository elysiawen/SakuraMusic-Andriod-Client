package com.sakura.music.core.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.sakura.music.data.network.MeteredBlockedException
import com.sakura.music.data.network.NetworkPolicy
import com.sakura.music.data.remote.ApiException
import com.sakura.music.data.remote.userFacingMessage

/**
 * 把播放异常翻成看得懂的中文。
 *
 * 链路上可能藏着网关给的原始文案（解析失败时它被塞进了 IOException 的 cause），
 * 有就直接用——那句通常比「播放失败」有用得多。
 */
fun describePlaybackError(error: PlaybackException?): String {
    if (error == null) return "播放失败"

    causeChain(error).forEach { throwable ->
        when (throwable) {
            // 自己拦下来的情况要原样说清楚：这不是网络故障，是用户开着的开关，
            // 落到下面那些按 errorCode 匹配的分支上会被翻译成「网络连接失败」。
            is MeteredBlockedException -> return throwable.message ?: NetworkPolicy.METERED_BLOCKED_MESSAGE

            is ApiException -> return throwable.userFacingMessage()
            is HttpDataSource.InvalidResponseCodeException -> {
                return when (throwable.responseCode) {
                    401, 403 -> "播放地址已过期或被拒绝，请重新点击播放"
                    404 -> "音频源返回 404，可能已下架"
                    else -> "音频源返回异常（HTTP ${throwable.responseCode}）"
                }
            }

            else -> Unit
        }
    }

    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "网络连接失败，请检查网关是否可达"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        -> "音频源返回异常，可尝试切换音源"

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "找不到音频资源"

        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "没有权限播放该曲目，可能受版权或会员限制"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "无法解析该音频格式"

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> "解码器初始化失败"

        /*
         * 播放器内部冒出来的运行时异常。ExoPlayer 给这种错误的原文就是
         * "Unexpected runtime error"——那句英文既是用户看不懂，又容易被当成「这首歌坏了」，
         * 而它其实与音频、网络都无关（有时甚至不影响播放）。原始细节留在日志里
         * （见 `PlaybackCenter.onPlayerError`），这里只给一句「可以重试」。
         */
        PlaybackException.ERROR_CODE_UNSPECIFIED,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        -> "播放器内部错误，可重试"

        else -> error.message?.takeIf { it.isNotBlank() } ?: "播放失败"
    }
}

/**
 * 这一首是不是「**确定**播不了」。
 *
 * 「一起听」拿它决定要不要上报让全员跳过，判错的代价很不对称：
 * 把网络抖动误判成播不了，一首其实能放的歌会被两边一起跳掉；反过来漏判，
 * 只是这台设备卡在错误页上、对方还在正常听。所以只认那些**重试也不会变**的失败。
 */
fun isUnplayableFailure(error: PlaybackException?): Boolean {
    if (error == null) return false

    causeChain(error).forEach { throwable ->
        when (throwable) {
            // 「仅 Wi-Fi」拦下的：这是本机的开关，换个网络就能放，不是这首的问题。
            is MeteredBlockedException -> return false

            // 取流被上游拒了（无版权 / 会员限制 / 根本没音源）：换台设备也一样放不出来。
            // 网络类的 ApiException（连不上网关）不在此列。
            is ApiException -> return !throwable.isNetworkError

            is HttpDataSource.InvalidResponseCodeException -> return when (throwable.responseCode) {
                // 被拒绝（401/403）与源已下架（404）都不会自己好起来。
                401, 403, 404 -> true
                else -> false
            }

            else -> Unit
        }
    }

    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> true

        else -> false
    }
}

private fun causeChain(error: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = error
    var guard = 0
    while (current != null && guard++ < MAX_DEPTH) {
        chain += current
        current = current.cause?.takeIf { it !== current }
    }
    return chain
}

private const val MAX_DEPTH = 8
