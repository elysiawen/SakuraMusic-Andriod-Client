package com.sakura.music.data.prefs

import java.net.URI

/**
 * 规范化用户输入的网关地址，非法时返回 null。
 *
 * 用户在手机上敲地址时最常漏两件事：**协议头**和**结尾斜杠**，所以这两处直接替他们补上；
 * 剩下的部分交给 [URI] 判断——它说不清 host 在哪，这个地址就用不了。
 *
 * 例：`10.0.2.2:8787/` → `http://10.0.2.2:8787`
 */
fun normalizeGatewayUrl(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null

    if (!text.startsWith("http://", ignoreCase = true) &&
        !text.startsWith("https://", ignoreCase = true)
    ) {
        text = "http://$text"
    }

    // 结尾斜杠必须去掉：拼 `/api/...` 时会变成 `//api/...`。
    text = text.trimEnd('/')
    if (text.isEmpty()) return null

    val host = runCatching { URI(text).host }.getOrNull()
    if (host.isNullOrBlank()) return null

    // 只填了端口没填主机（`http://:8787`）之类的输入也会走到这里。
    if (host.contains(' ') || host.contains('/')) return null

    return text
}

/**
 * 把地址里的主机与端口拆出来，用于界面上的简短展示。
 * 解析不出来时原样返回，免得显示成空白。
 */
fun gatewayHostLabel(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host ?: return url
    return if (uri.port > 0) "$host:${uri.port}" else host
}
