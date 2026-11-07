package com.sakura.music.ui.screens.player.nowplaying.styles

/**
 * 效果设置面板的入口：每种效果一份字段表，各自放在 XxxSettingsSpecs.kt。
 * 没有设置面板的样式返回空表，顶栏也就不会出现齿轮。
 */
internal fun specsFor(effectId: String): List<EffectSetting> = when (effectId) {
    "nebbia" -> nebbiaSpecs()
    "lumiere" -> lumiereSpecs()
    "machi" -> machiSpecs()
    else -> emptyList()
}
