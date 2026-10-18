package com.sakura.music.data.prefs

import com.sakura.music.data.model.Platform

/**
 * 平台能力维度，与网关 `/api/platforms` 里的 `capabilities` 一一对应。
 *
 * 播放不在其中：关掉一个平台只是不再拿它做发现与检索，
 * 收藏 / 歌单 / 历史里该平台的歌照常解析播放。
 */
enum class PlatformCapability(val id: String, val label: String, val hint: String) {
    SEARCH("search", "参与搜索", "搜索单曲、歌手、专辑、歌单时包含这个平台"),
    DISCOVER("discover", "首页推荐", "首页的每日推荐、FM、榜单、热门歌单包含这个平台"),
    LIBRARY("library", "账号歌单", "「音乐库」里展示这个平台账号的收藏与歌单"),
    ;

    companion object {
        fun fromId(id: String?): PlatformCapability? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/**
 * 平台开关偏好：总开关 + 能力开关。
 *
 * 总开关关掉时，这个平台的任何功能都不参与（搜索 / 推荐 / 账号歌单一起失效），
 * 但各能力开关的值会保留，重新打开时按原样恢复。
 *
 * 这份数据存在网关账号下（`/api/preferences` 的 `platforms` 键），
 * 本地只留一份用于冷启动首屏；改了之后由 `PlatformPrefsRepository` 推到云端。
 */
data class PlatformPrefs(
    val enabled: Map<Platform, Boolean> = Platform.known.associateWith { true },
    val capabilities: Map<Platform, Map<PlatformCapability, Boolean>> = Platform.known.associateWith {
        PlatformCapability.entries.associateWith { true }
    },
) {

    fun isPlatformEnabled(platform: Platform): Boolean =
        platform.isKnown && (enabled[platform] ?: true)

    fun isEnabled(platform: Platform, capability: PlatformCapability): Boolean =
        isPlatformEnabled(platform) && (capabilities[platform]?.get(capability) ?: true)

    /** 某个能力下启用的平台，顺序固定，可以直接拼请求参数。 */
    fun enabledPlatforms(capability: PlatformCapability): List<Platform> =
        Platform.known.filter { isEnabled(it, capability) }

    /**
     * 请求参数用的平台串。
     *
     * 全部启用时返回 null：让请求与「没有这个功能」时完全一致，调用方也不必判断。
     */
    fun queryParam(capability: PlatformCapability): String? {
        val list = enabledPlatforms(capability)
        return if (list.size == Platform.known.size) null else list.joinToString(",") { it.id }
    }
}
