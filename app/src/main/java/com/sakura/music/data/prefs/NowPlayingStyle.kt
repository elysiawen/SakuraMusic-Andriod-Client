package com.sakura.music.data.prefs

/**
 * 全屏播放页（Now Playing）的样式。
 *
 * 与网页端 `nowPlayingModes` 是同一份思路：加一种样式 = 写一个 Composable + 在这张表里登记一行，
 * 顶栏的样式菜单与 `PlayerScreen` 的分发都读它，不用再去别处接线。
 *
 * 安卓端多出一项 [Native]：原来那套播放页原样保留，作为没换过样式的默认值；
 * 网页端的三套样式是后来接入的可选项，所以老用户升级后看到的仍然是熟悉的页面。
 */
enum class NowPlayingStyle(val id: String, val label: String, val hint: String) {

    /** 安卓原有的全屏播放页：封面居中 + 歌词卡片 + M3 控制条。 */
    Native(
        id = "native",
        label = "原生",
        hint = "封面居中 · 歌词卡片 · 默认版式",
    ),

    /** 网页端经典：封面与歌词左右分栏，窄屏时自动收成单列。 */
    Classic(
        id = "classic",
        label = "经典",
        hint = "封面与歌词左右分栏",
    ),

    /** 网页端沉浸：大字居中、流体背景、镜像频谱。 */
    Immersive(
        id = "immersive",
        label = "沉浸",
        hint = "大字居中 · 流体背景 · 实时频谱",
    ),

    /** 网页端雾窗：WebGL 雨夜车窗，按笔顺写歌词。 */
    Nebbia(
        id = "nebbia",
        label = "雾窗",
        hint = "雨夜车窗 · 指尖按笔顺写歌词 · 雨点随歌声打窗",
    );

    /** 是否来自网页端的三套样式（原生页的皮肤与它们不同）。 */
    val fromDesktop: Boolean
        get() = this != Native

    companion object {
        val Default = Native

        /** 认不出的 id 回落到 [Default]，旧存档 / 手改过的偏好都有得渲染。 */
        fun fromId(id: String?): NowPlayingStyle =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
