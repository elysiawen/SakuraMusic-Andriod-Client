package com.sakura.music.ui.screens.player.nowplaying.styles

/**
 * 雾窗设置项，对齐桌面端 `NebbiaSettings.vue` 的分组与显隐：
 * 窗外选了梦海流萤才有水母，玻璃换挡只留对应那组滑块，水族箱里没有车身颠簸；
 * 出现 / 消失 / 构图三组至少留一种。
 */

private val aquarium: SettingVisible = { it.text("behind") == "aquarium" }
private fun glassIs(value: String): SettingVisible = { it.text("glass") == value }

/**
 * 街景的雨只画在聚光灯的光柱里：雪窗没有雨，关掉聚光灯景物或把强度调到 0
 * 也看不出变化，这几种情况「窗外的雨」就隐藏。水族箱的细气泡不看聚光灯。
 */
private val rainVisible: SettingVisible = {
    it.text("behind") == "aquarium" ||
        (it.text("glass") != "snow" && "spot" in it.choices("scenery") && it.amount("spot") > 0f)
}

internal fun nebbiaSpecs(): List<EffectSetting> = listOf(
    pick("窗外", "behind", "窗外场景", "night", "night" to "雨夜街景", "aquarium" to "梦海流萤"),
    num("窗外", "jellies", "水母数量", 16f, 0f, 24f, 1f, "个", visibleWhen = aquarium),
    num("窗外", "jellySize", "水母大小", 1f, 0.5f, 1.8f, 0.05f, "×", visibleWhen = aquarium),
    num("窗外", "bubbles", "气泡多少", 0.6f, 0f, 1f, 0.05f, "%", visibleWhen = aquarium),
    multi(
        "窗外", "scenery", "窗外景物",
        listOf("spot", "buildings", "lights", "cars", "road"),
        "spot" to "聚光灯", "buildings" to "楼群", "lights" to "霓虹光斑",
        "cars" to "车灯", "road" to "湿路倒影",
    ),
    pick(
        "窗外", "rain", "窗外的雨", "slant",
        "slant" to "斜落", "straight" to "直落", "off" to "不下",
        visibleWhen = rainVisible,
    ),

    pick("玻璃", "glass", "玻璃上的水", "rain", "rain" to "雨窗", "dew" to "凝露", "snow" to "雪窗"),
    num("玻璃", "rainHit", "雨点打窗", 1f, 0f, 3f, visibleWhen = glassIs("rain")),
    num("玻璃", "rainBig", "大雨点", 1f, 0f, 2f, visibleWhen = glassIs("rain")),
    num("玻璃", "dew", "冷凝水珠", 1f, 0f, 1.5f, visibleWhen = glassIs("dew")),
    num("玻璃", "dewSize", "水珠大小", 1f, 0.5f, 2f, visibleWhen = glassIs("dew")),
    num("玻璃", "dewGlint", "水珠高光", 1f, 0f, 2f, visibleWhen = glassIs("dew")),
    num("玻璃", "snow", "落雪多少", 1f, 0f, 3f, visibleWhen = glassIs("snow")),
    num("玻璃", "snowSize", "雪花大小", 1f, 0.5f, 2f, visibleWhen = glassIs("snow")),
    num("玻璃", "snowMelt", "化雪速度", 1f, 0.25f, 3f, visibleWhen = glassIs("snow")),

    num("雾上写字", "strokeWidth", "笔画粗细", 0.09f, 0.04f, 0.16f, 0.005f, "%"),
    num("雾上写字", "writeSpeed", "书写速度", 1f, 0.4f, 2.5f),
    num("雾上写字", "jitter", "手写歪斜", 0.6f, 0f, 1.5f),
    num("雾上写字", "warp", "手写变形", 0.6f, 0f, 1.5f),
    num("雾上写字", "doodle", "句尾小画", 0.3f, 0f, 1f),
    num("雾上写字", "mist", "雾气浓度", 0.75f, 0.2f, 1f, 0.01f, "%"),
    num("雾上写字", "hold", "写完保持", 1.2f, 0f, 6f, 0.1f, "s"),
    num("雾上写字", "refog", "重新起雾", 4f, 0.5f, 12f, 0.5f, "s"),
    flag("雾上写字", "credits", "曲终写出曲名和歌手", true),

    multi(
        "出现方式", "appear", "出现方式", listOf("write", "fade"),
        "write" to "一笔一画写", "fade" to "从雾里透出", "breath" to "先哈气再写",
        minSelected = 1,
    ),
    multi(
        "消失方式", "fade", "消失方式", listOf("fog"),
        "fog" to "慢慢起雾", "fingers" to "手指搓掉",
        minSelected = 1,
    ),
    multi(
        "构图", "layouts", "构图",
        listOf("ribbon", "lines2", "lines3", "cross", "pillar", "header", "badge", "columns", "orbit"),
        "ribbon" to "横条", "lines2" to "两行错落", "lines3" to "三行错落",
        "cross" to "十字", "pillar" to "竖柱", "header" to "刊头",
        "badge" to "徽章", "columns" to "竖排几列", "orbit" to "环绕",
        minSelected = 1,
    ),

    num("窗外动效", "busSpeed", "车速", 1f, 0f, 3f),
    num("窗外动效", "neon", "霓虹亮度", 1f, 0f, 2f),
    num("窗外动效", "bump", "车身颠簸", 1f, 0f, 2.5f, visibleWhen = { it.text("behind") != "aquarium" }),
    num("窗外动效", "spot", "聚光灯", 0.5f, 0f, 2f),
    num("窗外动效", "spotX", "聚光灯位置", 0.8f, 0f, 1f, 0.01f, "%"),
)
