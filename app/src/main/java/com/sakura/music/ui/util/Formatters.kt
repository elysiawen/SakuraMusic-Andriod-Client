package com.sakura.music.ui.util

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

/** 数量文案：`12 首` / `1.2 万首`。 */
fun formatCount(count: Int?): String {
    val value = count ?: return ""
    return when {
        value < 10_000 -> "$value 首"
        else -> String.format(Locale.US, "%.1f 万首", value / 10_000.0)
    }
}
