package com.sakura.music.ui.screens.player.nowplaying.styles

/**
 * 町景设置项，对齐桌面端 `MachiSettings.vue`。
 * 关掉运镜就不显示快慢，对焦不选手动就不显示对焦前后，景深强度归零就不显示光圈和清晰范围。
 */

private val motionOn: SettingVisible = { it.flag("motion") }
private val manualFocus: SettingVisible = { it.text("focus") == "manual" }
private val dofOn: SettingVisible = { it.amount("dof") > 0f }

internal fun machiSpecs(): List<EffectSetting> = listOf(
    pick(
        "街景", "scene", "场景", "auto",
        "auto" to "自动", "konbini" to "便利店街角", "station" to "加油站停车场", "kowloon" to "城寨酒吧",
    ),
    pick(
        "街景", "timeOfDay", "时间", "auto",
        "auto" to "自动", "day" to "白天", "dusk" to "黄昏", "night" to "夜晚",
    ),
    pick(
        "街景", "weather", "天气", "auto",
        "auto" to "自动", "clear" to "晴", "rain" to "雨", "snow" to "雪", "fog" to "雾",
    ),

    pick("镜头", "camera", "镜头", "default", "default" to "默认构图", "custom" to "自定义视角"),
    flag("镜头", "motion", "缓慢运镜", false),
    num("镜头", "motionSpeed", "运镜快慢", 1f, 0.25f, 3f, visibleWhen = motionOn),
    pick("镜头", "focus", "对焦在哪", "center", "center" to "画面中心", "lyrics" to "歌词屏", "manual" to "手动"),
    num("镜头", "focusOffset", "对焦前后", 0f, -10f, 10f, 0.1f, "m", visibleWhen = manualFocus),
    num("镜头", "aperture", "光圈", 8f, 1.4f, 16f, 0.1f, "f/", visibleWhen = dofOn),
    num("镜头", "dofRange", "清晰范围", 1f, 0.3f, 3f, visibleWhen = dofOn),
    num("镜头", "dof", "景深强度", 1f, 0f, 2f),
    num("镜头", "tiltShift", "移轴", 0.3f, 0f, 1f),

    preset(
        "画质", "qualityPreset", "画质预设",
        "高" to """{"quality":{"renderScale":"global","antialias":"global","shadows":"high","reflections":"high","glow":true}}""",
        "均衡" to """{"quality":{"renderScale":"global","antialias":"fxaa","shadows":"low","reflections":"low","glow":true}}""",
        "省电" to """{"quality":{"renderScale":0.75,"antialias":"off","shadows":"off","reflections":"off","glow":false}}""",
    ),
    numericPick(
        "画质", "quality.renderScale", "分辨率", "global",
        "global" to "跟随全局", "1" to "100%", "0.75" to "75%", "0.5" to "50%",
    ),
    pick(
        "画质", "quality.shadows", "阴影", "high",
        "off" to "关", "low" to "低", "high" to "高",
    ),
    pick(
        "画质", "quality.reflections", "湿路倒影", "high",
        "off" to "关", "low" to "低", "high" to "高",
    ),
    pick(
        "画质", "quality.antialias", "抗锯齿", "global",
        "global" to "跟随全局", "off" to "关", "fxaa" to "FXAA", "smaa" to "SMAA", "ssaa" to "超采样",
    ),
    flag("画质", "quality.glow", "辉光与镜头光条", true),
    flag("画质", "quality.adaptive", "帧率跟不上时自动降级", true),

    flag("节拍", "simulatedBeat", "模拟节拍", true),
)
