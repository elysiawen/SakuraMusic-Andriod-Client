package com.sakura.music.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 音质面板里的体积格式化，口径与网页端 `toFixed(1)M` 一致。 */
class QualitySizeTest {

    @Test
    fun `formats bytes as one-decimal MB`() {
        assertEquals("8.3M", formatQualitySize(8_702_566L))
        assertEquals("1.0M", formatQualitySize(1_048_576L))
        // 特别小的文件也不该显示成「0.0M」。
        assertEquals("0.1M", formatQualitySize(1_000L))
    }

    @Test
    fun `unknown size stays unknown`() {
        assertNull(formatQualitySize(0L))
        assertNull(formatQualitySize(-1L))
    }

    @Test
    fun `gateway qualitySizes deserializes into the track model`() {
        val json = Json { ignoreUnknownKeys = true }
        val track = json.decodeFromString(
            UnifiedTrack.serializer(),
            """{"key":"qq:demo","title":"demo","qualities":["standard","high","lossless"],""" +
                """"qualitySizes":{"standard":4718592,"high":11848908,"lossless":37539021},""" +
                """"qualitiesComplete":true}""",
        )
        assertEquals(4718592L, track.qualitySizes?.get("standard"))
        assertEquals("4.5M", formatQualitySize(track.qualitySizes?.get("standard") ?: 0L))
        assertEquals(
            listOf(Quality.STANDARD, Quality.HIGH, Quality.LOSSLESS),
            track.availableQualities,
        )
        assertEquals(true, track.qualitiesAuthoritative)
    }

    @Test
    fun `spec covers every quality`() {
        Quality.entries.forEach { assertEquals(true, qualitySpec(it).isNotBlank()) }
        assertEquals("无损 FLAC", qualitySpec(Quality.LOSSLESS))
        assertEquals("母带级 192kHz/24bit", qualitySpec(Quality.MASTER))
    }
}
