package com.sakura.music.data.remote

/**
 * 网关返回的业务错误。
 *
 * [message] 是网关给的中文文案，可以直接展示给用户。
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message) {

    /** 未登录 / 会话过期。 */
    val isUnauthorized: Boolean get() = status == 401 || code == "unauthorized"

    /** 播放令牌过期，需要重新调用 `play/resolve`。 */
    val isStreamTokenExpired: Boolean get() = code == "stream_token_invalid"

    /** 上游不可用（网易云 / QQ 音乐挂了或没权限）。 */
    val isUpstreamError: Boolean get() = status == 502 || code == "upstream_error"

    /**
     * 当前这一档确实没有可用播放地址（网关的 `404` + `upstream_error`）。
     *
     * 和别的上游失败的区别在于它是**换一档就能解决**的，所以提示文案要给个出口。
     */
    val isNoPlayableQuality: Boolean get() = status == 404 && code == "upstream_error"

    /** 连不上网关：地址填错了、服务没起、或者不在同一个网络里。 */
    val isNetworkError: Boolean get() = code == "network_error"

    /** 网关地址本身写错了。 */
    val isBadBaseUrl: Boolean get() = code == "bad_base_url"

    companion object {
        fun network(detail: String): ApiException = ApiException(
            status = 0,
            code = "network_error",
            message = detail,
        )
    }
}

/**
 * 把上游错误翻成更可操作的中文提示。
 *
 * 未绑定第三方账号时，上游通常会回一句风控 / 登录相关的文案；
 * Android 端不做扫码绑定，所以这里补一句「去哪儿绑」。
 */
fun ApiException.userFacingMessage(): String = when {
    isBadBaseUrl -> message
    isNetworkError -> message
    isUnauthorized -> "登录已过期，请重新登录"
    isStreamTokenExpired -> "播放地址已过期，正在重新获取"
    code == "credential_required" -> "该平台账号尚未绑定，请先在网页端或桌面端完成扫码绑定"

    // 网关只说「当前音质下没有可用播放地址」，不给下一步：用户容易以为这首歌根本放不了
    // 而直接跳过——其实降到无损往往就能听，所以这里必须把出口写出来。
    isNoPlayableQuality -> "$message\n可在「音质」里换一档再试"

    isUpstreamError && looksLikeCredentialIssue(message) ->
        "$message\n（若尚未绑定第三方账号，请先在网页端或桌面端扫码绑定）"

    else -> message
}

private fun looksLikeCredentialIssue(message: String): Boolean {
    val lowered = message.lowercase()
    return listOf("未登录", "登录", "凭据", "cookie", "login", "auth", "风控", "无权限", "需要登录")
        .any { lowered.contains(it) }
}

/** 界面统一用它把异常变成一句能展示的话。 */
fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> userFacingMessage()
    else -> message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后再试"
}
