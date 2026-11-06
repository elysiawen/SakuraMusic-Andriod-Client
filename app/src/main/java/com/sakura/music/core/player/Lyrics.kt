package com.sakura.music.core.player

/** 逐字歌词里的一个字（或一小段）：绝对毫秒时间轴。 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** 一行歌词：原词 + 可选翻译（或罗马音）；[words] 非空时带字级时间轴。 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val trans: String? = null,
    /** 逐字歌词才有；按行 LRC 模式下为空，此时整行一起高亮。 */
    val words: List<LyricWord> = emptyList(),
    /** 本行结束时间，逐字歌词才有。 */
    val endMs: Long? = null,
)

/**
 * 歌词解析。
 *
 * 网关把 `lrc` / `trans` / `roma` 与逐字原文 `yrc`（网易云）/ `qrc`（QQ）原样返回，
 * 两种歌词同时保留、互不覆盖：能解析出字级时间轴就按字渲染，否则退回按行。
 * 解析口径与网页端 `web/src/utils/lyric.ts` 一致。
 */
object LrcParser {

    /** `[mm:ss.xx]` 或 `[mm:ss:xx]`，一行里可以出现多个。 */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** 逐字歌词的行首 `[开始ms,时长ms]`，YRC / QRC 共用。 */
    private val WORD_LINE = Regex("""^\[(\d+),(\d+)\](.*)$""")

    /** YRC 字标签 `(开始ms,时长ms,0)`，文字跟在标签后面。 */
    private val YRC_WORD = Regex("""\((\d+),(\d+),\d+\)""")

    /** QRC 字标签 `(开始ms,时长ms)`，少数实现多一段 `(开始ms,时长ms,0)`。 */
    private val QRC_WORD = Regex("""\((\d+),(\d+)(?:,\d+)?\)""")

    /** QQ 上游把逐字歌词整份塞进 `<Lyric_1 LyricContent="...">` 的**属性**里。 */
    private val QRC_CONTENT = Regex("""LyricContent\s*=\s*["']([\s\S]*?)["']""")

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

    /**
     * 统一入口：按行歌词始终保留；上游给了逐字歌词且能解析出字级时间轴时才用它覆盖主时间轴，
     * 翻译仍从按行数据里按最接近的时间点挂过来。
     */
    fun parseResult(
        lrc: String,
        trans: String = "",
        roma: String = "",
        yrc: String? = null,
        qrc: String? = null,
    ): List<LyricLine> {
        val lineLyrics = parse(lrc, trans, roma)
        var wordLyrics = yrc?.let { parseWordLines(it, isYrc = true) }.orEmpty()
        if (wordLyrics.isEmpty() && !qrc.isNullOrBlank()) {
            wordLyrics = parseWordLines(qrc, isYrc = false)
        }
        if (wordLyrics.isEmpty()) return lineLyrics
        return wordLyrics.map { line ->
            line.copy(trans = nearestTranslation(lineLyrics, line.timeMs))
        }
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

    /** 每个字的演唱进度：0 未唱、1 已唱完，中间值是当前字的擦除比例。 */
    fun wordProgress(words: List<LyricWord>, positionMs: Long): FloatArray =
        FloatArray(words.size) { index ->
            val word = words[index]
            val progress = when {
                positionMs >= word.endMs -> 1f
                positionMs <= word.startMs -> 0f
                else -> (positionMs - word.startMs).toFloat() /
                    (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
            }
            progress.coerceIn(0f, 1f)
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

    /**
     * QQ 上游的 QRC 不是标签文本而是 XML 属性，直接剥标签会把歌词一起丢掉；
     * 少数来源给纯 QRC 文本，此时退回「去掉 XML 标签」的兜底。
     */
    private fun extractQrc(source: String): String {
        val raw = QRC_CONTENT.find(source)?.groupValues?.get(1)
            ?: source.replace(Regex("""<[^>]+>"""), "\n")
        return raw
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#10;|&#x0A;""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
            // 最后解 &amp;，避免把 &amp;quot; 二次解码成引号。
            .replace("&amp;", "&")
    }

    /** YRC：`(开始,时长,0)字`，文字在标签之后、到下一个标签为止。 */
    private fun yrcWords(rest: String): List<LyricWord> {
        val tags = YRC_WORD.findAll(rest).toList()
        val words = mutableListOf<LyricWord>()
        for (index in tags.indices) {
            val tag = tags[index]
            val from = tag.range.first + tag.value.length
            val to = tags.getOrNull(index + 1)?.range?.first ?: rest.length
            val text = rest.substring(from, to)
            if (text.isEmpty()) continue
            val startMs = tag.groupValues[1].toLong()
            words += LyricWord(text, startMs, startMs + tag.groupValues[2].toLong())
        }
        return words
    }

    /**
     * QRC：字标签是 `(开始,时长)`，文字可能在标签前（`字(0,300)字(300,300)`）
     * 也可能在标签后（`(0,300)字(300,300)字`），按首尾哪一侧有正文判断。
     */
    private fun qrcWords(rest: String): List<LyricWord> {
        val tags = QRC_WORD.findAll(rest).toList()
        if (tags.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var cursor = 0
        for (tag in tags) {
            chunks += rest.substring(cursor, tag.range.first)
            cursor = tag.range.last + 1
        }
        chunks += rest.substring(cursor)

        val first = chunks.first()
        val last = chunks.last()
        val prefersBefore = first.trim().isNotEmpty() && last.trim().isEmpty()
        val prefersAfter = first.trim().isEmpty() && last.trim().isNotEmpty()

        val words = mutableListOf<LyricWord>()
        for (index in tags.indices) {
            val tag = tags[index]
            val before = chunks[index]
            val after = chunks[index + 1]
            val text = when {
                prefersBefore -> before
                prefersAfter -> after
                after.trim().isNotEmpty() && before.trim().isEmpty() -> after
                before.trim().isNotEmpty() && after.trim().isEmpty() -> before
                else -> if (after.length >= before.length) after else before
            }
            if (text.trim().isEmpty()) continue
            val startMs = tag.groupValues[1].toLong()
            words += LyricWord(text, startMs, startMs + tag.groupValues[2].toLong())
        }
        return words
    }

    /** 把一行逐字歌词组装出来；字时间戳偶尔是「相对本行开头」的偏移，需要归一化。 */
    private fun makeWordLine(startMs: Long, durationMs: Long, words: List<LyricWord>): LyricLine {
        val firstStart = words.firstOrNull()?.startMs ?: 0L
        // 第一个字明显早于行首时，按相对值处理（加行首偏移）。
        val offset = if (firstStart >= 0L && firstStart < startMs - 500L) startMs else 0L
        val normalized = if (offset > 0L) {
            words.map { it.copy(startMs = it.startMs + offset, endMs = it.endMs + offset) }
        } else {
            words
        }
        return LyricLine(
            timeMs = startMs,
            text = normalized.joinToString("") { it.text },
            words = normalized,
            endMs = startMs + durationMs,
        )
    }

    /** 解析逐字歌词原文；解析不出任何字级时间轴时返回空数组，调用方按行降级。 */
    private fun parseWordLines(source: String, isYrc: Boolean): List<LyricLine> {
        if (source.isBlank()) return emptyList()
        val text = (if (isYrc) source else extractQrc(source)).removePrefix("\uFEFF")
        val lines = mutableListOf<LyricLine>()
        for (rawLine in text.split('\n')) {
            val match = WORD_LINE.matchEntire(rawLine.trim('\r', ' ', '\uFEFF')) ?: continue
            val rest = match.groupValues[3]
            val words = if (isYrc) yrcWords(rest) else qrcWords(rest)
            if (words.isEmpty()) continue
            lines += makeWordLine(match.groupValues[1].toLong(), match.groupValues[2].toLong(), words)
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 逐字行是毫秒级、LRC 翻译是秒级，按最接近的时间点（容差 0.6s）挂上翻译。 */
    private fun nearestTranslation(lines: List<LyricLine>, timeMs: Long): String? {
        if (lines.isEmpty()) return null
        var low = 0
        var high = lines.size - 1
        while (low < high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs < timeMs) low = mid + 1 else high = mid
        }
        var best: LyricLine? = null
        // 翻译 LRC 的时间戳常与原文差几十毫秒，会被拆成相邻的空文本行，
        // 所以在落点附近几行里挑「有翻译且时间最近」的那条。
        for (index in (low - 3).coerceAtLeast(0)..(low + 3).coerceAtMost(lines.lastIndex)) {
            val candidate = lines[index]
            if (candidate.trans == null || kotlin.math.abs(candidate.timeMs - timeMs) > 600L) continue
            val currentBest = best
            if (currentBest == null ||
                kotlin.math.abs(candidate.timeMs - timeMs) <
                kotlin.math.abs(currentBest.timeMs - timeMs)
            ) {
                best = candidate
            }
        }
        return best?.trans
    }
}
