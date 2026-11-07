package com.sakura.music.ui.screens.player.nowplaying.styles

import org.json.JSONArray
import org.json.JSONObject

/**
 * 效果设置项的公共模型：控件类型、显隐条件与构造工具。
 *
 * 每个效果的字段表放在各自的 XxxSettingsSpecs.kt，新增效果只要加一个文件和
 * [specsFor] 里的一行，不用往一个越来越长的文件里继续堆。
 * Kotlin 只存用户改过的 JSON patch，完整 tuning 由 Web 侧以 DEFAULT_*_TUNING
 * 为底合并，所以这里的 default 只用于面板回显和显隐判断。
 *
 * key 支持 `quality.shadows` 这样的点路径，用来写嵌套对象。
 */

/** 显隐条件：拿到「默认 tuning + 用户 patch」合成后的当前值。 */
internal typealias SettingVisible = (SettingValues) -> Boolean

internal sealed interface EffectSetting {
    val group: String
    val key: String
    val label: String

    /** 条件不满足时整行不渲染；默认常显。 */
    val visibleWhen: SettingVisible get() = { true }

    data class Number(
        override val group: String,
        override val key: String,
        override val label: String,
        val default: Float,
        val min: Float,
        val max: Float,
        val step: Float,
        val unit: String,
        override val visibleWhen: SettingVisible = { true },
    ) : EffectSetting

    data class Flag(
        override val group: String,
        override val key: String,
        override val label: String,
        val default: Boolean,
        override val visibleWhen: SettingVisible = { true },
    ) : EffectSetting

    /** 单选：value 直接写进 tuning；数字档位（如町景分辨率）要写成 number 而不是字符串。 */
    data class Pick(
        override val group: String,
        override val key: String,
        override val label: String,
        val default: String,
        val options: List<Pair<String, String>>,
        val numericValues: Boolean = false,
        override val visibleWhen: SettingVisible = { true },
    ) : EffectSetting

    /** 多选：值是一个字符串数组；[minSelected] 保证至少留下几项。 */
    data class Multi(
        override val group: String,
        override val key: String,
        override val label: String,
        val default: List<String>,
        val options: List<Pair<String, String>>,
        val minSelected: Int = 0,
        override val visibleWhen: SettingVisible = { true },
    ) : EffectSetting

    /** 一键套用一组字段；option 的第二项是要合并的 patch JSON。 */
    data class Preset(
        override val group: String,
        override val key: String,
        override val label: String,
        val options: List<Pair<String, String>>,
        override val visibleWhen: SettingVisible = { true },
    ) : EffectSetting
}

/**
 * 显隐判断用的「有效值」：先铺各字段默认值，再被用户 patch 覆盖。
 * 这样即使某项从未被改过，依赖它的条件也能拿到桌面端 tuning 里的默认值。
 */
internal class SettingValues private constructor(private val values: Map<String, Any?>) {
    fun raw(key: String): Any? = values[key]
    fun text(key: String): String = values[key]?.let(::settingText).orEmpty()
    fun flag(key: String): Boolean = values[key] as? Boolean ?: false
    fun amount(key: String): Float = (values[key] as? Number)?.toFloat() ?: 0f

    /** 多选字段的有效值：默认值是 List，被 patch 覆盖后是 JSONArray。 */
    fun choices(key: String): Set<String> = when (val value = values[key]) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it, null) }.toSet()
        is Iterable<*> -> value.mapNotNull { it?.toString() }.toSet()
        else -> emptySet()
    }

    companion object {
        fun of(specs: List<EffectSetting>, patch: JSONObject): SettingValues {
            val values = HashMap<String, Any?>()
            specs.forEach { spec ->
                when (spec) {
                    is EffectSetting.Number -> values[spec.key] = spec.default
                    is EffectSetting.Flag -> values[spec.key] = spec.default
                    is EffectSetting.Pick -> values[spec.key] = spec.default
                    is EffectSetting.Multi -> values[spec.key] = spec.default
                    is EffectSetting.Preset -> Unit
                }
            }
            specs.forEach { spec ->
                val overridden = read(patch, spec.key)
                if (overridden != null) values[spec.key] = overridden
            }
            return SettingValues(values)
        }

        /** 与面板写入逻辑一致的点路径读取。 */
        private fun read(root: JSONObject, key: String): Any? {
            val parts = key.split(".")
            var node: JSONObject? = root
            for (index in 0 until parts.size - 1) {
                node = node?.optJSONObject(parts[index]) ?: return null
            }
            return node?.opt(parts.last())
        }
    }
}

/** 数字档位从 JSONObject 读出来是 Double，统一成和选项值一致的形式。 */
internal fun settingText(raw: Any): String = when (raw) {
    is Number -> {
        val value = raw.toDouble()
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    else -> raw.toString()
}

/* ------------------------------ 字段表构造工具 ------------------------------ */

internal fun num(
    g: String, k: String, l: String, d: Float, a: Float, b: Float,
    s: Float = 0.05f, u: String = "", visibleWhen: SettingVisible = { true },
) = EffectSetting.Number(g, k, l, d, a, b, s, u, visibleWhen)

internal fun flag(
    g: String, k: String, l: String, d: Boolean,
    visibleWhen: SettingVisible = { true },
) = EffectSetting.Flag(g, k, l, d, visibleWhen)

internal fun pick(
    g: String, k: String, l: String, d: String,
    vararg o: Pair<String, String>,
    visibleWhen: SettingVisible = { true },
) = EffectSetting.Pick(g, k, l, d, o.toList(), false, visibleWhen)

internal fun numericPick(
    g: String, k: String, l: String, d: String,
    vararg o: Pair<String, String>,
    visibleWhen: SettingVisible = { true },
) = EffectSetting.Pick(g, k, l, d, o.toList(), true, visibleWhen)

internal fun multi(
    g: String, k: String, l: String, d: List<String>,
    vararg o: Pair<String, String>,
    minSelected: Int = 0,
    visibleWhen: SettingVisible = { true },
) = EffectSetting.Multi(g, k, l, d, o.toList(), minSelected, visibleWhen)

internal fun preset(
    g: String, k: String, l: String,
    vararg o: Pair<String, String>,
    visibleWhen: SettingVisible = { true },
) = EffectSetting.Preset(g, k, l, o.toList(), visibleWhen)
