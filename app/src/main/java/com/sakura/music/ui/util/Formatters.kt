package com.sakura.music.ui.util

import com.sakura.music.data.model.Platform
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** `mm:ss`，超过一小时则是 `h:mm:ss`。传 0 表示未知时长。 */
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "--:--"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 播放进度用的时间：从 0 开始计时，未知时长显示 `00:00` 而不是 `--:--`。 */
fun formatProgress(millis: Long): String {
    if (millis <= 0) return "00:00"
    return formatDuration(millis)
}

/** 刚才 / 12 分钟前 / 昨天 / 2026-09-01。 */
fun formatPlayedAt(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val zone = ZoneId.systemDefault()
    val then = instant.atZone(zone)
    val now = Instant.now().atZone(zone)

    val minutes = Duration.between(then, now).toMinutes()
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        then.toLocalDate() == now.toLocalDate() -> "${minutes / 60} 小时前"
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "昨天"
        else -> String.format(
            Locale.US,
            "%04d-%02d-%02d",
            then.year,
            then.monthValue,
            then.dayOfMonth,
        )
    }
}

/** 存储占用：`0 B` / `812 KB` / `24.3 MB`。按 1024 进位，和系统的算法一致。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

/**
 * 一整张列表的总时长：`3 小时 40 分` / `24 分 12 秒`。
 *
 * 和 [formatDuration] 不同：那个是给进度条用的 `mm:ss`，这个是要读出来的一句话。
 * 时长为 0（一首都没拿到）时返回空串，调用方据此决定要不要写这一段。
 */
fun formatTotalDuration(millis: Long): String {
    if (millis <= 0) return ""
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "$hours 小时 $minutes 分"
        minutes > 0 -> "$minutes 分 $seconds 秒"
        else -> "$seconds 秒"
    }
}

/**
 * 一起听时长的人话写法：`38 秒` / `24 分 12 秒` / `3 小时 40 分`。
 *
 * 与 [formatDuration]（`mm:ss`，给进度条用）不同：这里的数字是要**读出来**的一句话。
 * 也不直接用 [formatTotalDuration]——那个对 0 返回空串，而一起听刚开房时
 * 「0 秒」是有意义的读数。
 */
fun formatTogetherDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    if (total < 60) return "$total 秒"
    return formatTotalDuration(total * 1000L).ifEmpty { "$total 秒" }
}

/**
 * 一起听明细里的时间点：日期 + 时刻。
 *
 * 与 [formatPlayedAt]（「3 分钟前」）不同：翻明细时要的是**具体哪天**，
 * 相对时间反而看不出先后。同一年不写年份，跨年的才带上——明细里绝大多数都是今年的。
 */
fun formatSessionTime(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val zone = ZoneId.systemDefault()
    val then = instant.atZone(zone)
    val sameYear = then.year == Instant.now().atZone(zone).year
    val date = String.format(Locale.US, "%02d-%02d", then.monthValue, then.dayOfMonth)
    val time = String.format(Locale.US, "%02d:%02d", then.hour, then.minute)
    val year = if (sameYear) "" else "${then.year}-"
    return "$year$date $time"
}

/**
 * 会员档位标识 → 界面文案。
 *
 * 网关发的 `vipLabel` 是**上游字段名**（ASCII、不本地化，接口里不放展示文案），
 * 中文只出现在这里——要改叫法、要出多语言版本，都只动这张表：
 *   QQ 音乐  `vip` → 绿钻、`svip` → 豪华绿钻、`huge_vip` → 超级会员
 *
 * **QQ 那三行看着别扭，但别「顺手修」**：QQ 的字段名与产品名是反的——`huge_vip`
 * （名字像"豪华"）实际是**超级会员**，`svip` 才是**豪华绿钻**。拿真实账号核对过
 * （`huge_vip_end` 与超级会员到期日、`eight_end` 与豪华绿钻到期日一一对上），
 * 原因与证据写在 `gateway/src/upstream/qq.ts` 那段注释里。
 *
 * **网易云不看标识**：上游给不出可靠的档位（`redVipLevel` 是「红VIP等级」不是档位、
 * 产品编码 `vipCode` 又没有官方枚举表），网关目前干脆不发 `vipLabel`——既然只知道
 * 「是不是会员」，就统一显示 `VIP`，不假装知道哪一档。依据见
 * `gateway/src/upstream/netease.ts` 与 `docs/API.md`。
 *
 * QQ 侧认不出的标识、以及旧数据里没有标识的，退到通用的「会员」——「是会员但不知道哪一档」，
 * 比空着或者干脆不显示都准。
 */
fun formatVipLabel(platform: Platform, vipLabel: String?): String = when (platform) {
    // 有档位可查：标识 → 档位名。
    Platform.QQ -> QQ_VIP_LABELS[vipLabel] ?: "会员"

    // 网易云只有「是不是会员」这一件事可说（网关眼下也不发标识），一律 VIP。
    Platform.NETEASE -> "VIP"

    else -> "会员"
}

private val QQ_VIP_LABELS = mapOf(
    "vip" to "绿钻",
    "svip" to "豪华绿钻",
    "huge_vip" to "超级会员",
)

/** 数量文案：`12 首` / `1.2 万首`。 */
fun formatCount(count: Int?): String {
    val value = count ?: return ""
    return when {
        value < 10_000 -> "$value 首"
        else -> String.format(Locale.US, "%.1f 万首", value / 10_000.0)
    }
}
