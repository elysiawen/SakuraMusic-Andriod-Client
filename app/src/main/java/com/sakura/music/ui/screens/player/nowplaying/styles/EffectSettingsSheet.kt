package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.music.ui.screens.player.nowplaying.common.DesktopPalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/* 与右下角玻璃胶囊同一套色系：中性深灰打底，强调色由播放页调色板提供。 */
private val SheetBackdrop = Color(0xFF0C0E11)
private val CardSurface = Color(0xFF191B1F)
private val CardStroke = Color.White.copy(alpha = 0.07f)
private val Hairline = Color.White.copy(alpha = 0.06f)
private val SheetText = Color(0xFFF4F4F6)
private val SheetDim = Color.White.copy(alpha = 0.58f)
private val SheetFaint = Color.White.copy(alpha = 0.34f)
private val GroupShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(11.dp)

/**
 * 强调色跟着播放页的 [DesktopPalette] 走，也就是 App 主题色和 secondary，
 * 与玻璃胶囊、进度条、顶栏同一来源；桌面端对应 CSS 变量 `--accent`。
 * 默认值只是兜底，实际使用时由面板入口覆盖。
 */
private data class SheetAccents(val primary: Color, val secondary: Color)

private val LocalSheetAccents = staticCompositionLocalOf {
    SheetAccents(Color(0xFFB3C0F0), Color(0xFF8F9FE0))
}

/**
 * 网页端效果的设置面板。
 *
 * 对应桌面端 `ModePicker` 里那个居中弹窗：分组收进玻璃卡片，控件按元数据
 * [specsFor] 生成，「恢复默认」清空整块 patch。滑块拖动时只推桥做实时预览
 * （不落盘），松手才写偏好；条件不满足的整组带动画收起。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EffectSettingsSheet(
    effectId: String,
    palette: DesktopPalette,
    settingsJson: String,
    onChange: (json: String, commit: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val specs = remember(effectId) { specsFor(effectId) }
    var current by remember {
        mutableStateOf(runCatching { JSONObject(settingsJson) }.getOrDefault(JSONObject()))
    }

    /** 读一个字段，支持 `quality.shadows` 这样的点路径。 */
    fun read(key: String): Any? {
        val parts = key.split(".")
        var node: JSONObject? = current
        for (index in 0 until parts.size - 1) {
            node = node?.optJSONObject(parts[index]) ?: return null
        }
        return node?.opt(parts.last())
    }

    /** 写一个字段；commit=false 只预览，true 顺带落盘。 */
    fun put(key: String, value: Any, commit: Boolean) {
        val next = runCatching { JSONObject(current.toString()) }.getOrDefault(JSONObject())
        val parts = key.split(".")
        var node = next
        for (index in 0 until parts.size - 1) {
            val child = node.optJSONObject(parts[index])
            node = if (child != null) {
                child
            } else {
                val created = JSONObject()
                node.put(parts[index], created)
                created
            }
        }
        node.put(parts.last(), value)
        current = next
        onChange(next.toString(), commit)
    }

    val putField: (String, Any, Boolean) -> Unit = { key, value, commit -> put(key, value, commit) }
    val commit: () -> Unit = { onChange(current.toString(), true) }
    // 条件显隐基于「默认 tuning + 当前 patch」，改一项就立刻重算可见项。
    val values = remember(current) { SettingValues.of(specs, current) }

    CompositionLocalProvider(
        LocalSheetAccents provides SheetAccents(palette.accent, palette.accent2),
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SheetBackdrop,
            contentColor = SheetText,
            dragHandle = { SheetDragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                SheetHeader(
                    effectId = effectId,
                    onReset = {
                        current = JSONObject()
                        onChange("{}", true)
                    },
                )
                Spacer(Modifier.height(10.dp))

                specs.groupBy { it.group }.forEach { (group, allItems) ->
                    val visibleItems = allItems.filter { it.visibleWhen(values) }
                    AnimatedVisibility(
                        visible = visibleItems.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        GroupCard(title = group, count = visibleItems.size) {
                            visibleItems.forEach { spec ->
                                SettingRow(
                                    spec = spec,
                                    raw = read(spec.key),
                                    values = values,
                                    put = putField,
                                    commit = commit,
                                    onPreset = { patch ->
                                        val merged = mergePreset(current, patch)
                                        current = merged
                                        onChange(merged.toString(), true)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------ 头部与卡片 ------------------------------ */

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .size(width = 38.dp, height = 4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

private fun effectSubtitle(effectId: String): String = when (effectId) {
    "nebbia" -> "雾窗 Nebbia · 窗外、玻璃与雾上写字"
    "lumiere" -> "绘光 Lumiere · 光线、氛围与文字"
    "machi" -> "町景 Machi · 街景、镜头与画质"
    else -> "调节当前画布"
}

@Composable
private fun SheetHeader(effectId: String, onReset: () -> Unit) {
    val accent = LocalSheetAccents.current.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "效果设置",
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = SheetText,
            )
            Spacer(Modifier.height(3.dp))
            // 对应桌面端弹窗头部那行 accent 色 eyebrow。
            Text(
                text = effectSubtitle(effectId),
                fontSize = 11.5.sp,
                color = accent.copy(alpha = 0.78f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, CardStroke, RoundedCornerShape(12.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "恢复默认",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = SheetDim,
            )
        }
    }
}

@Composable
private fun GroupCard(
    title: String,
    count: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accents = LocalSheetAccents.current
    Surface(
        shape = GroupShape,
        color = CardSurface,
        border = BorderStroke(1.dp, CardStroke),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(accents.primary, accents.secondary))),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetText,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "$count 项", fontSize = 10.sp, color = SheetFaint)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Hairline),
            )
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    spec: EffectSetting,
    raw: Any?,
    values: SettingValues,
    put: (String, Any, Boolean) -> Unit,
    commit: () -> Unit,
    onPreset: (String) -> Unit,
) {
    when (spec) {
        is EffectSetting.Number -> NumberRow(spec, raw, put, commit)
        is EffectSetting.Flag -> FlagRow(spec, raw, put)
        is EffectSetting.Pick -> PickRow(spec, raw, put)
        is EffectSetting.Multi -> MultiRow(spec, raw, put)
        is EffectSetting.Preset -> PresetRow(spec, values, onPreset)
    }
}

/* ------------------------------ 控件 ------------------------------ */

@Composable
private fun ValueBadge(text: String) {
    val accent = LocalSheetAccents.current.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = accent,
        )
    }
}

@Composable
private fun NumberRow(
    spec: EffectSetting.Number,
    raw: Any?,
    put: (String, Any, Boolean) -> Unit,
    commit: () -> Unit,
) {
    val value = (raw as? Number)?.toFloat() ?: spec.default
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = spec.label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = SheetText,
                modifier = Modifier.weight(1f),
            )
            ValueBadge(formatValue(value, spec))
        }
        // 不给 Slider 设 steps：那会沿轨道画一排刻度点。改成在回调里按 step 吸附，
        // 拖动仍是连续手感，写进 tuning 的值也始终落在合法档位上。
        Slider(
            value = value.coerceIn(spec.min, spec.max),
            onValueChange = { dragged ->
                val snapped = spec.min +
                    ((dragged - spec.min) / spec.step).roundToInt() * spec.step
                put(spec.key, snapped.coerceIn(spec.min, spec.max), false)
            },
            onValueChangeFinished = commit,
            valueRange = spec.min..spec.max,
            colors = SliderDefaults.colors(
                thumbColor = SheetText,
                activeTrackColor = LocalSheetAccents.current.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.10f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FlagRow(
    spec: EffectSetting.Flag,
    raw: Any?,
    put: (String, Any, Boolean) -> Unit,
) {
    val checked = raw as? Boolean ?: spec.default
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { put(spec.key, !checked, true) }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = SheetText,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { put(spec.key, it, true) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SheetText,
                checkedTrackColor = LocalSheetAccents.current.primary.copy(alpha = 0.70f),
                uncheckedThumbColor = SheetDim,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun settingChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.05f),
    labelColor = SheetDim,
    selectedContainerColor = LocalSheetAccents.current.primary.copy(alpha = 0.16f),
    selectedLabelColor = SheetText,
)

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = SheetDim,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickRow(
    spec: EffectSetting.Pick,
    raw: Any?,
    put: (String, Any, Boolean) -> Unit,
) {
    val selected = if (raw == null) spec.default else settingText(raw)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowLabel(spec.label)
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            spec.options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = {
                        val written: Any = if (spec.numericValues) {
                            value.toDoubleOrNull() ?: value
                        } else {
                            value
                        }
                        put(spec.key, written, true)
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    shape = ChipShape,
                    colors = settingChipColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiRow(
    spec: EffectSetting.Multi,
    raw: Any?,
    put: (String, Any, Boolean) -> Unit,
) {
    val selected: Set<String> = when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it, null) }.toSet()
        else -> spec.default.toSet()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowLabel(spec.label)
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            spec.options.forEach { (value, label) ->
                FilterChip(
                    selected = value in selected,
                    onClick = {
                        // 桌面端同款：出现 / 消失 / 构图这类组至少留一种，最后一项关不掉。
                        val canRemove = value !in selected || selected.size > spec.minSelected
                        if (canRemove) {
                            val next = if (value in selected) selected - value else selected + value
                            put(spec.key, JSONArray(next.toList()), true)
                        }
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    shape = ChipShape,
                    colors = settingChipColors(),
                )
            }
        }
    }
}

/** 画质预设：当前 quality 细项和某一档完全一致时，那一档高亮。 */
private fun presetMatches(values: SettingValues, patchJson: String): Boolean {
    val patch = runCatching { JSONObject(patchJson).optJSONObject("quality") }.getOrNull()
        ?: return false
    return patch.keys().asSequence().all { key ->
        val expected = patch.opt(key) ?: return@all false
        val actual = values.raw("quality.$key") ?: return@all false
        settingText(expected) == settingText(actual)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    spec: EffectSetting.Preset,
    values: SettingValues,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowLabel(spec.label)
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            spec.options.forEach { (label, patch) ->
                FilterChip(
                    selected = presetMatches(values, patch),
                    onClick = { onPick(patch) },
                    label = { Text(label, fontSize = 12.sp) },
                    shape = ChipShape,
                    colors = settingChipColors(),
                )
            }
        }
    }
}

/* ------------------------------ 工具 ------------------------------ */

private fun formatValue(value: Float, spec: EffectSetting.Number): String {
    val digits = when {
        spec.step >= 1f -> 0
        spec.step >= 0.1f -> 1
        else -> 2
    }
    return String.format(Locale.US, "%." + digits + "f%s", value, spec.unit)
}

/** 画质预设只覆盖 quality 下的细项，保留用户自己设的 adaptive，不整块替换。 */
private fun mergePreset(current: JSONObject, patchJson: String): JSONObject {
    val patch = runCatching { JSONObject(patchJson).optJSONObject("quality") }.getOrNull()
        ?: return current
    val next = runCatching { JSONObject(current.toString()) }.getOrDefault(JSONObject())
    val quality = next.optJSONObject("quality") ?: JSONObject().also { next.put("quality", it) }
    patch.keys().forEach { key -> quality.put(key, patch.get(key)) }
    return next
}
