package com.sakura.music.ui.screens.player.nowplaying.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val SheetBackground = Color(0xFF121B26)
private val SheetAccent = Color(0xFFF9A8D4)
private val SheetText = Color.White
private val SheetDim = Color.White.copy(alpha = 0.62f)

/**
 * 网页端效果的设置面板。
 *
 * 对应桌面端 `ModePicker` 里那个居中弹窗：每个带设置面板的样式一个齿轮入口，
 * 面板按元数据 [specsFor] 生成控件，「恢复默认」清空整块 patch。
 * 滑块拖动时只推桥做实时预览（不落盘），松手才写偏好。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EffectSettingsSheet(
    effectId: String,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        contentColor = SheetText,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "效果设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = SheetText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        current = JSONObject()
                        onChange("{}", true)
                    },
                ) {
                    Text("恢复默认", color = SheetAccent)
                }
            }
            Spacer(Modifier.height(6.dp))

            // 隐藏项连分组标题一起不渲染，整组都隐藏时不会出现空标题。
            specs.filter { it.visibleWhen(values) }.groupBy { it.group }.forEach { (group, items) ->
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetAccent,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
                items.forEach { spec ->
                    when (spec) {
                        is EffectSetting.Number -> NumberRow(spec, read(spec.key), putField, commit)
                        is EffectSetting.Flag -> FlagRow(spec, read(spec.key), putField)
                        is EffectSetting.Pick -> PickRow(spec, read(spec.key), putField)
                        is EffectSetting.Multi -> MultiRow(spec, read(spec.key), putField)
                        is EffectSetting.Preset -> PresetRow(spec) { patch ->
                            val merged = mergePreset(current, patch)
                            current = merged
                            onChange(merged.toString(), true)
                        }
                    }
                }
            }
        }
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.label,
                style = MaterialTheme.typography.bodyMedium,
                color = SheetText,
                modifier = Modifier.weight(1f),
            )
            Text(text = formatValue(value, spec), style = MaterialTheme.typography.labelMedium, color = SheetDim)
        }
        val steps = ((spec.max - spec.min) / spec.step + 0.5f).toInt() - 1
        Slider(
            value = value.coerceIn(spec.min, spec.max),
            onValueChange = { put(spec.key, it, false) },
            onValueChangeFinished = commit,
            valueRange = spec.min..spec.max,
            steps = steps.coerceAtLeast(0),
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.bodyMedium,
            color = SheetText,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = raw as? Boolean ?: spec.default,
            onCheckedChange = { put(spec.key, it, true) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickRow(
    spec: EffectSetting.Pick,
    raw: Any?,
    put: (String, Any, Boolean) -> Unit,
) {
    val selected = if (raw == null) spec.default else settingText(raw)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = spec.label, style = MaterialTheme.typography.bodyMedium, color = SheetText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text(label) },
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = spec.label, style = MaterialTheme.typography.bodyMedium, color = SheetText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text(label) },
                )
            }
        }
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    spec: EffectSetting.Preset,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = spec.label, style = MaterialTheme.typography.bodyMedium, color = SheetText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.options.forEach { (label, patch) ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(patch) },
                    label = { Text(label) },
                )
            }
        }
    }
}
