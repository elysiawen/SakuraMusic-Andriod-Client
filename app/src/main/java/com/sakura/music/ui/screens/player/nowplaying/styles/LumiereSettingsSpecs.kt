package com.sakura.music.ui.screens.player.nowplaying.styles

/**
 * 绘光设置项，对齐桌面端 `LumiereSettings.vue`。
 * 桌面端这一组没有互相依赖的开关，所有项常显。
 */
internal fun lumiereSpecs(): List<EffectSetting> = listOf(
    num("光", "lightIntensity", "光强", 1f, 0.3f, 2f),
    num("光", "audioResponse", "随音乐起伏", 1f, 0f, 2f),
    num("光", "bloom", "辉光", 1f, 0f, 2f),
    num("光", "textBloom", "文字辉光", 1f, 0f, 2f),

    num("氛围", "darkField", "暗场", 0.75f, 0f, 1f),
    num("氛围", "fogDensity", "烟雾浓度", 1f, 0f, 2f),
    num("氛围", "fogOctaves", "烟雾细节", 5f, 2f, 6f, 1f),
    num("氛围", "moteAmount", "浮尘", 1f, 0f, 2f),
    flag("氛围", "frontBokeh", "前景光斑", true),
    flag("氛围", "lineArt", "线稿", true),
    flag("氛围", "overlayFrame", "画框", true),

    flag("文字", "textOnly", "只看文字", false),
    flag("文字", "trails", "轨迹", true),
    flag("文字", "hideTrails", "藏起轨迹", false),
    flag("文字", "seamlessTransitions", "轨迹过渡", true),
    num("文字", "unlitOpacity", "未唱字透明度", 0.22f, 0.05f, 0.6f, 0.01f, "%"),
    num("文字", "decay", "崩解", 1f, 0f, 2f),
    num("文字", "echo", "余音", 1f, 0f, 2f),
    num("文字", "windowNeighbors", "邻行", 2f, 1f, 2f, 1f),

    num("主题", "themeColorMix", "主题色混入", 0.3f, 0f, 1f),
    flag("主题", "keywordColors", "关键词上色", true),
    flag("主题", "themeIcons", "句尾小图标", true),

    pick(
        "性能", "renderQuality", "画质", "full",
        "full" to "满", "balanced" to "均衡", "low" to "省电",
    ),
)
