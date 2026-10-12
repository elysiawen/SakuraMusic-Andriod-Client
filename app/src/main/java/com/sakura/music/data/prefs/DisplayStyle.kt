package com.sakura.music.data.prefs

/**
 * 详情页（专辑 / 歌单 / 榜单 / 歌手 / 收藏）的头部版式。
 *
 * 两种都要留着是有理由的：沉浸式好看但封面占掉半屏，翻歌单时想快点看到曲目的人会嫌它挡路；
 * 经典版式紧凑，一屏多看好几行。这是口味问题，不该由我们替他定。
 */
enum class DetailHeaderStyle(val id: String, val label: String, val hint: String) {

    /** 封面铺满顶部、画到状态栏下面，返回浮在封面上。 */
    Immersive(
        id = "immersive",
        label = "沉浸式",
        hint = "封面铺满顶部，返回浮在封面上",
    ),

    /** 封面缩进页内，页面自带一条顶栏。改动之前就是这个样子。 */
    Classic(
        id = "classic",
        label = "经典",
        hint = "封面缩进页内，页面自带顶栏",
    );

    companion object {
        val Default = Immersive

        fun fromId(id: String?): DetailHeaderStyle =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
