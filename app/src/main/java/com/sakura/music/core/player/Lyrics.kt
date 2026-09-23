package com.sakura.music.core.player

/** 一行歌词：原词 + 可选翻译（或罗马音）。 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val trans: String? = null,
)

/**
 * LRC 解析。
 *
 * 网关只把原始 LRC 文本给过来（`lrc` / `trans` / `roma`），逐行时间戳的解析由客户端做。
 */
object LrcParser {

    /** `[mm:ss.xx]` 或 `[mm:ss:xx]`，一行里可以出现多个。 */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String, trans: String = "", roma: String = ""): List<LyricLine> {
        val main = parseLines(lrc)
        val translation = parseLines(trans)
        val roman = parseLines(roma)

        val times = (main.keys + translation.keys).toSortedSet()
        return times
            .map { time ->
                LyricLine(
                    timeMs = time,
                    text = main[time].orEmpty(),
                    trans = (translation[time] ?: roman[time])?.takeIf { it.isNotBlank() },
                )
            }
            .filter { it.text.isNotBlank() || !it.trans.isNullOrBlank() }
    }

    /** 有没有可用时间轴：全是无时间标签的纯文本歌词就没法跟着唱。 */
    fun isSynced(lines: List<LyricLine>): Boolean = lines.any { it.timeMs > 0L }

    /** 当前时间对应的歌词行下标，未命中返回 -1。 */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            val line = lines[mid]
            if (line.timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** 同一时间标签在一行里出现多次时，会展开成多条。 */
    private fun parseLines(source: String): Map<Long, String> {
        if (source.isBlank()) return emptyMap()
        val map = LinkedHashMap<Long, String>()

        source.split('\n').forEach { rawLine ->
            val line = rawLine.trim('\r', ' ')
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.replace(TIME_TAG, "").trim()
            tags.forEach { tag ->
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                val fractionText = tag.groupValues[3]
                val fraction = if (fractionText.isEmpty()) {
                    0L
                } else {
                    fractionText.padEnd(3, '0').take(3).toLong()
                }
                map[minutes * 60_000 + seconds * 1_000 + fraction] = text
            }
        }
        return map
    }
}
