package com.sakura.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 逐字歌词解析与按行降级，口径与网页端 `web/src/utils/lyric.ts` 一致。 */
class LyricsParserTest {

    @Test
    fun `yrc words keep their own timeline and inherit the nearest translation`() {
        val yrc = "[1000,2000](1000,300,0)你(1300,300,0)好\n" +
            "[3000,2000](3000,300,0)世(3300,300,0)界"
        val lrc = "[00:01.00]你好\n[00:03.00]世界"
        val trans = "[00:01.00]Hello\n[00:03.00]World"

        val lines = LrcParser.parseResult(lrc, trans, yrc = yrc)

        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals(3000L, lines[0].endMs)
        assertEquals("你好", lines[0].text)
        assertEquals("Hello", lines[0].trans)
        assertEquals(2, lines[0].words.size)
        assertEquals(1000L, lines[0].words[0].startMs)
        assertEquals(1300L, lines[0].words[1].startMs)

        val progress = LrcParser.wordProgress(lines[0].words, 1150L)
        assertEquals(0.5f, progress[0], 0.001f)
        assertEquals(0f, progress[1], 0.001f)
    }

    @Test
    fun `qrc xml attribute is decoded before parsing`() {
        val qrc = "<Lyric_1 LyricContent=\"[1000,2000]你(1000,300)好(1300,300)\"/>"

        val lines = LrcParser.parseResult(lrc = "", qrc = qrc)

        assertEquals(1, lines.size)
        assertEquals("你好", lines[0].text)
        assertEquals(2, lines[0].words.size)
        assertEquals(1300L, lines[0].words[1].startMs)
    }

    @Test
    fun `falls back to line lrc when no word timeline is available`() {
        val lrc = "[00:01.00]Hello\n[00:02.00]World"
        val trans = "[00:01.00]你好"

        val lines = LrcParser.parseResult(lrc, trans, yrc = "not a word lyric", qrc = null)

        assertEquals(2, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals("你好", lines[0].trans)
        assertTrue(lines.all { it.words.isEmpty() })
        assertTrue(LrcParser.isSynced(lines))
        assertEquals(0, LrcParser.activeIndex(lines, 1500L))
        assertEquals(1, LrcParser.activeIndex(lines, 2500L))
        assertFalse(LrcParser.isSynced(LrcParser.parse("纯文本歌词")))
    }
}
