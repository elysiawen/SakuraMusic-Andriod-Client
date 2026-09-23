package com.sakura.music.data.prefs

/**
 * 取流走哪条路。
 *
 * **每个平台单独设**：直连省的是服务器带宽，中转稳的是「能不能放出来」，而两个平台被
 * 防盗链卡住的程度并不一样——一个开关套两个平台，必然有一边要将就。
 */
enum class RoutePreference(val id: String, val label: String, val hint: String) {

    /** 默认逻辑：直连优先，被 CDN 拒了就改用网关中转，并记住这个平台。 */
    Smart(
        id = "smart",
        label = "智能",
        hint = "直连优先，被 CDN 拒绝时自动改用中转并记住",
    ),

    /**
     * 锁定直连：不走网关。
     *
     * 被拒时**不自动回退**——用户既然把路锁死了，就别背着他改道；那时界面会提醒他改成
     * 「智能」或「中转」。省带宽这个目标值得让用户自己承担一次播放失败。
     */
    Direct(
        id = "direct",
        label = "直连",
        hint = "始终从平台 CDN 取流；不通就直接失败，不自动改走中转",
    ),

    /** 锁定中转：不试直连，用服务器带宽换稳定。 */
    Proxy(
        id = "proxy",
        label = "中转",
        hint = "始终经网关转发，用服务器带宽换稳定",
    ),
    ;

    companion object {
        /** 没设过就是智能——这也是这个功能出现之前的行为。 */
        val Default = Smart

        fun fromId(id: String?): RoutePreference =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Default
    }
}
