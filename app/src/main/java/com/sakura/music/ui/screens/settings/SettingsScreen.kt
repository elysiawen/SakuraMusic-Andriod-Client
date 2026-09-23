package com.sakura.music.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.AppContainer
import com.sakura.music.BuildConfig
import com.sakura.music.data.cache.CacheKind
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.cache.CacheUsage
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.prefs.ThemeMode
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ChoiceSheet
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.GatewayDialog
import com.sakura.music.ui.components.LabelValueRow
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.SakuraCard
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SectionHeader
import com.sakura.music.ui.components.SettingRow
import com.sakura.music.ui.components.SwitchRow
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import com.sakura.music.ui.theme.AccentTheme
import com.sakura.music.ui.theme.lightAccent
import com.sakura.music.ui.util.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class SheetKind {
    Quality, PlayMode, Platform, LyricTtl, MetaTtl,
}

/**
 * 设置：外观（强调色 / 深浅色）、播放（音质 / 播放模式 / 优先音源）、网络与关于。
 *
 * 网关地址默认取构建配置（`gradle.properties` 的 `sakura.gateway.url`），
 * 但可以在这里直接改，改完立刻生效、不用重新打包。
 */
@Composable
fun SettingsScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val scope = rememberCoroutineScope()

    val accent by container.settings.accent.collectAsStateWithLifecycle(initialValue = AccentTheme.Sakura)
    val themeMode by container.settings.themeMode
        .collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val quality by container.settings.quality.collectAsStateWithLifecycle(initialValue = Quality.Default)
    val playMode by container.settings.playMode.collectAsStateWithLifecycle(initialValue = PlayMode.All)
    val mixWithOthers by container.settings.mixWithOthers.collectAsStateWithLifecycle(initialValue = false)
    val wifiOnly by container.settings.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val preferredPlatform by container.settings.preferredPlatform.collectAsStateWithLifecycle(initialValue = null)
    val proxyOnly by container.settings.proxyOnlyPlatforms.collectAsStateWithLifecycle(initialValue = emptySet())
    val gatewayUrl by container.settings.gatewayUrl.collectAsStateWithLifecycle()
    val audioCacheMb by container.settings.audioCacheMb
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_AUDIO_CACHE_MB)
    val coverCacheMb by container.settings.coverCacheMb
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_COVER_CACHE_MB)
    val lyricCacheDays by container.settings.lyricCacheDays
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_LYRIC_CACHE_DAYS)
    val metaCacheDays by container.settings.metaCacheDays
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_META_CACHE_DAYS)

    val cacheVm = rememberAppViewModel { CacheSettingsViewModel(it.cacheManager) }
    val cacheUsage by cacheVm.usage.collectAsStateWithLifecycle()
    val cacheWorking by cacheVm.working.collectAsStateWithLifecycle()

    var openSheet by remember { mutableStateOf<SheetKind?>(null) }
    var showGateway by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<CacheKind?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SakuraTopBar(
                title = "设置",
                subtitle = "外观、播放、网络与存储",
                onBack = navigator::back,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = ListBottomPadding),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionHeader("外观")
            SakuraCard {
                AccentPicker(
                    selected = accent,
                    onSelect = { scope.launch { container.settings.setAccent(it) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                ThemeModePicker(
                    selected = themeMode,
                    onSelect = { scope.launch { container.settings.setThemeMode(it) } },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionHeader("播放")
            SakuraCard {
                SettingRow(
                    title = "音质",
                    subtitle = "拿不到指定档位时网关会自动降级",
                    onClick = { openSheet = SheetKind.Quality },
                    trailing = {
                        Text(
                            text = quality.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "播放模式",
                    subtitle = "顺序 / 列表循环 / 单曲循环 / 随机",
                    onClick = { openSheet = SheetKind.PlayMode },
                    trailing = {
                        Text(
                            text = playMode.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "优先音源",
                    subtitle = "同一首歌在两个平台都有时，默认用哪个",
                    onClick = { openSheet = SheetKind.Platform },
                    trailing = {
                        Text(
                            text = preferredPlatform?.label ?: "自动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "与其他应用一起播放",
                    subtitle = "打开后不再抢占音频焦点，其它应用出声时本应用不暂停",
                    checked = mixWithOthers,
                    onCheckedChange = { scope.launch { container.settings.setMixWithOthers(it) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "仅 Wi-Fi 播放",
                    subtitle = "移动网络下只播放已缓存或已下载的歌，不再联网取流",
                    checked = wifiOnly,
                    onCheckedChange = { scope.launch { container.settings.setWifiOnly(it) } },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionHeader("网络")
            SakuraCard {
                SettingRow(
                    title = "直连失败记录",
                    subtitle = if (proxyOnly.isEmpty()) {
                        "当前所有平台都会先尝试直连 CDN（省服务器带宽）"
                    } else {
                        "这些平台已改走网关中转：${proxyOnly.joinToString("、") { it.label }}"
                    },
                    onClick = { scope.launch { container.settings.resetProxyOnly() } },
                    trailing = {
                        Text(
                            text = "重置",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "网关地址",
                    subtitle = gatewayUrl,
                    onClick = { showGateway = true },
                    trailing = {
                        Text(
                            text = "修改",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            Spacer(Modifier.height(22.dp))

            val totalBytes = cacheUsage.sumOf { it.bytes }
            SectionHeader("存储")
            SakuraCard {
                LabelValueRow(
                    label = "缓存总占用",
                    value = if (cacheUsage.isEmpty()) "计算中…" else formatBytes(totalBytes),
                    // 卡片本身已经留了 16dp，这里再补一层：SettingRow 就是这么多，
                    // 不补的话这一行的左边缘会比下面几行往里缩。
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                cacheUsage.forEach { item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CacheUsageRow(
                        usage = item,
                        enabled = !cacheWorking,
                        onClear = { pendingClear = item.kind },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "清理全部缓存",
                    subtitle = if (cacheWorking) "正在清理…" else "音频、封面、歌词与歌曲信息一起清掉",
                    onClick = if (cacheWorking || totalBytes == 0L) null else ({ confirmClearAll = true }),
                    trailing = {
                        Text(
                            text = "清理",
                            style = MaterialTheme.typography.labelLarge,
                            // 跟着强调色走，和这一页其它可点的动作保持一致。
                            color = if (cacheWorking || totalBytes == 0L) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(14.dp))

            SakuraCard {
                CacheLimitSlider(
                    title = "音频缓存上限",
                    subtitle = "最左不缓存，最右不限量；中间超出后从最久没听的歌开始淘汰",
                    stops = CacheManager.AUDIO_LIMIT_STOPS,
                    valueMb = audioCacheMb,
                    onCommit = { applyCacheSetting(scope, container) { container.settings.setAudioCacheMb(it) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CacheLimitSlider(
                    title = "封面缓存上限",
                    subtitle = "专辑封面与头像；关掉后每次进列表都要重新加载",
                    stops = CacheManager.COVER_LIMIT_STOPS,
                    valueMb = coverCacheMb,
                    onCommit = { applyCacheSetting(scope, container) { container.settings.setCoverCacheMb(it) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "歌词保留时间",
                    subtitle = "到期后重新从网关获取",
                    onClick = { openSheet = SheetKind.LyricTtl },
                    trailing = { ValueText(daysLabel(lyricCacheDays)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "歌曲信息保留时间",
                    subtitle = "歌曲详情、专辑与歌手信息同样适用",
                    onClick = { openSheet = SheetKind.MetaTtl },
                    trailing = { ValueText(daysLabel(metaCacheDays)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "播过的歌会边播边存，第二遍直接读本地；歌词与歌曲信息取过一次就留下来，" +
                    "断网时也能看。超出上限的部分按最近最少使用淘汰，过期的会重新拉取。" +
                    "下载的歌不占这里的额度，也不会被清理——要删请到「音乐库 → 本地」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(22.dp))

            SectionHeader("关于")
            SakuraCard {
                LabelValueRow("应用", "Sakura Music")
                Spacer(Modifier.height(8.dp))
                LabelValueRow("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                Spacer(Modifier.height(8.dp))
                LabelValueRow("上游", "网易云音乐 · QQ 音乐")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "音频经网关转发或直接从平台 CDN 取流，播过的歌会缓存到本地以便重复播放，" +
                        "可在「存储」里查看占用并随时清理。" +
                        "第三方账号的扫码绑定请使用网页端或桌面端。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    when (openSheet) {
        SheetKind.Quality -> ChoiceSheet(
            title = "音质",
            options = Quality.entries,
            selected = quality,
            optionLabel = { it.label },
            optionSubtitle = { option ->
                when (option) {
                    Quality.STANDARD -> "大多数账号都可用"
                    Quality.HIGH -> "默认档位，无明显音质损失"
                    Quality.LOSSLESS -> "需要平台会员"
                    Quality.HIRES -> "需要平台会员，且上游支持"
                }
            },
            onSelect = { scope.launch { container.settings.setQuality(it) } },
            onDismiss = { openSheet = null },
        )

        SheetKind.PlayMode -> ChoiceSheet(
            title = "播放模式",
            options = PlayMode.entries,
            selected = playMode,
            optionLabel = { it.label },
            onSelect = { scope.launch { container.settings.setPlayMode(it) } },
            onDismiss = { openSheet = null },
        )

        SheetKind.Platform -> ChoiceSheet(
            title = "优先音源",
            options = listOf<Platform?>(null, Platform.NETEASE, Platform.QQ),
            selected = preferredPlatform,
            optionLabel = { it?.label ?: "自动" },
            optionSubtitle = { option ->
                if (option == null) "按歌曲自带的来源优先级" else "这首歌在${option.label}有资源时优先用它"
            },
            onSelect = { scope.launch { container.settings.setPreferredPlatform(it) } },
            onDismiss = { openSheet = null },
        )

        SheetKind.LyricTtl -> ChoiceSheet(
            title = "歌词保留时间",
            options = CacheManager.LYRIC_TTL_CHOICES,
            selected = lyricCacheDays,
            optionLabel = ::daysLabel,
            optionSubtitle = { days -> if (days < 0) "除非手动清理，否则一直用本地的" else null },
            onSelect = { applyCacheSetting(scope, container) { container.settings.setLyricCacheDays(it) } },
            onDismiss = { openSheet = null },
        )

        SheetKind.MetaTtl -> ChoiceSheet(
            title = "歌曲信息保留时间",
            options = CacheManager.META_TTL_CHOICES,
            selected = metaCacheDays,
            optionLabel = ::daysLabel,
            optionSubtitle = { days -> if (days < 0) "上游改了元数据也不会刷新" else null },
            onSelect = { applyCacheSetting(scope, container) { container.settings.setMetaCacheDays(it) } },
            onDismiss = { openSheet = null },
        )

        null -> Unit
    }

    if (showGateway) {
        GatewayDialog(onDismiss = { showGateway = false })
    }

    pendingClear?.let { kind ->
        ConfirmDialog(
            title = "清理${kind.label}缓存",
            message = "将删除已缓存的${kind.label}（${kind.hint}）。清理后再次使用需要重新联网获取。",
            confirmLabel = "清理",
            destructive = true,
            onConfirm = { cacheVm.clear(kind) },
            onDismiss = { pendingClear = null },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "清理全部缓存",
            message = "音频、封面、歌词与歌曲信息都会被删除。已经缓存的歌下次播放要重新下载。",
            confirmLabel = "全部清理",
            destructive = true,
            onConfirm = { cacheVm.clearAll() },
            onDismiss = { confirmClearAll = false },
        )
    }
}

/**
 * 存下用户的缓存设置并让它立刻生效。
 *
 * 顺序很重要：先写进偏好，再让缓存管理器重读——反过来会拿旧值刷一遍，
 * 用户看到的就是「改了但没生效」。
 */
private fun applyCacheSetting(
    scope: CoroutineScope,
    container: AppContainer,
    save: suspend () -> Unit,
) {
    scope.launch {
        save()
        container.applyCacheSettings()
    }
}

/** 缓存上限的文案：`不缓存` / `512 MB` / `2 GB` / `无限`。 */
private fun sizeLabel(mb: Int): String = when {
    mb == SettingsStore.NO_CACHE_MB -> "不缓存"
    mb == SettingsStore.UNLIMITED_MB -> "无限"
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

/**
 * 缓存上限滑块：只能在 [stops] 这几档上停，最左「不缓存」、最右「无限」。
 *
 * 拖动过程中只动本地下标，松手（[androidx.compose.material3.Slider] 的
 * `onValueChangeFinished`）才写偏好并让缓存生效——一路拖一路重建 ImageLoader 太吵。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheLimitSlider(
    title: String,
    subtitle: String,
    stops: List<Int>,
    valueMb: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember(valueMb) { mutableIntStateOf(stops.indexOf(valueMb).coerceAtLeast(0)) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = sizeLabel(stops[index]),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { index = it.roundToInt() },
            onValueChangeFinished = { onCommit(stops[index]) },
            valueRange = 0f..stops.lastIndex.toFloat(),
            // Slider 的 steps 数的是「首尾之间再切几段」，所以是档位数减 2。
            steps = stops.size - 2,
        )
    }
}

/** 有效期的文案：`7 天` / `永久`。 */
private fun daysLabel(days: Int): String = if (days < 0) "永久" else "$days 天"

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 一类缓存占了多少，右边一个小按钮单独清它。 */
@Composable
private fun CacheUsageRow(
    usage: CacheUsage,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    SettingRow(
        title = usage.kind.label,
        subtitle = usage.kind.hint + if (usage.entries > 0) " · ${usage.entries} 项" else "",
        trailing = {
            Text(
                text = formatBytes(usage.bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            // 40dp 而不是默认的 48dp：右侧留白和上面那张卡片的文字边距看起来才一致。
            IconButton(onClick = onClear, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "清理${usage.kind.label}缓存",
                    // 用强调色而不是 error 色：清理是常规操作，不是危险动作，
                    // 而且这一页的其它可点元素都是强调色。
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** 八套强调色，4 个一行排开。 */
@Composable
private fun AccentPicker(
    selected: AccentTheme,
    onSelect: (AccentTheme) -> Unit,
) {
    Column {
        AccentTheme.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { theme ->
                    AccentOption(
                        theme = theme,
                        selected = theme == selected,
                        onClick = { onSelect(theme) },
                    )
                }
                // 最后一排不满 4 个时补空位，保持列对齐。
                repeat(4 - row.size) {
                    Spacer(Modifier.width(46.dp))
                }
            }
        }
    }
}

@Composable
private fun AccentOption(
    theme: AccentTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = lightAccent(theme).primary,
            border = if (selected) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
            } else {
                null
            },
        ) {
            if (selected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = lightAccent(theme).onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 深浅色三选一，等宽色块。 */
@Composable
private fun ThemeModePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            ModeBlock(
                mode = mode,
                selected = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeBlock(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (mode) {
        ThemeMode.System -> Icons.Rounded.SettingsBrightness
        ThemeMode.Light -> Icons.Rounded.LightMode
        ThemeMode.Dark -> Icons.Rounded.DarkMode
    }

    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
