package com.sakura.music.ui.screens.player.nowplaying.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 从封面里取一对主/辅色，给沉浸式流体背景铺底。
 *
 * 算法和网页端 `utils/palette.ts` 是同一套：
 * 1. 缩到 48×48 采样；
 * 2. 灰白黑对氛围色没有贡献，权重趋零；饱和且中等亮度的像素说了算；
 * 3. 主色把饱和度抬到 0.38–0.85、亮度压到 0.32–0.6，避免背景发白或死黑；
 * 4. 辅色色相 +46°，亮度和饱和度各抬一点，制造层次。
 *
 * 取不到颜色（封面加载失败）就返回 null，调用方退回主题色。
 */
data class CoverPalette(val primary: Color, val secondary: Color)

@Composable
fun rememberCoverPalette(url: String?): CoverPalette? {
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    var palette by remember(url) { mutableStateOf<CoverPalette?>(null) }

    LaunchedEffect(url) {
        palette = null
        if (url.isNullOrBlank()) return@LaunchedEffect
        palette = withContext(Dispatchers.IO) {
            CoverPaletteCache.get(url) ?: samplePalette(context, imageLoader, url).also {
                CoverPaletteCache.put(url, it)
            }
        }
    }
    return palette
}

private suspend fun samplePalette(
    context: android.content.Context,
    imageLoader: ImageLoader,
    url: String,
): CoverPalette? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(48, 48)
        .allowHardware(false)
        .build()
    val bitmap = (imageLoader.execute(request) as? SuccessResult)
        ?.drawable
        ?.toBitmap(width = 48, height = 48)
        ?: return null
    return paletteFromBitmap(bitmap)
}

private fun paletteFromBitmap(bitmap: Bitmap): CoverPalette? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var totalWeight = 0.0
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var plainR = 0.0
    var plainG = 0.0
    var plainB = 0.0
    var plainCount = 0.0

    for (pixel in pixels) {
        val a = ((pixel ushr 24) and 0xFF) / 255.0
        if (a < 0.5) continue
        val r = ((pixel shr 16) and 0xFF) / 255.0
        val g = ((pixel shr 8) and 0xFF) / 255.0
        val b = (pixel and 0xFF) / 255.0

        plainR += r
        plainG += g
        plainB += b
        plainCount += 1.0

        val (_, s, l) = rgbToHsl(r, g, b)
        val weight = a * s * (1.0 - abs(l - 0.5) * 1.4)
        if (weight <= 0.0) continue
        totalWeight += weight
        sumR += r * weight
        sumG += g * weight
        sumB += b * weight
    }

    val r: Double
    val g: Double
    val b: Double
    when {
        totalWeight > 0.01 -> {
            r = sumR / totalWeight
            g = sumG / totalWeight
            b = sumB / totalWeight
        }

        plainCount > 0.0 -> {
            r = plainR / plainCount
            g = plainG / plainCount
            b = plainB / plainCount
        }

        else -> return null
    }

    val (h, s, l) = rgbToHsl(r, g, b)
    val sat = min(0.85, max(0.38, s * 1.35))
    val light = min(0.6, max(0.32, l))
    val h2 = (h + 46.0) % 360.0

    return CoverPalette(
        primary = Color.hsl(h.toFloat(), sat.toFloat(), light.toFloat()),
        secondary = Color.hsl(
            h2.toFloat(),
            min(0.9, sat + 0.1).toFloat(),
            min(0.66, light + 0.08).toFloat(),
        ),
    )
}

/** 返回 `[h, s, l]`，h 是角度 0–360，s/l 是 0–1。 */
private fun rgbToHsl(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val l = (max + min) / 2.0
    if (max == min) return Triple(0.0, 0.0, l)
    val d = max - min
    val s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6.0 else 0.0) * 60.0
        g -> ((b - r) / d + 2.0) * 60.0
        else -> ((r - g) / d + 4.0) * 60.0
    }
    return Triple(h, s, l)
}

/** 同一封面只取一次色；失败也缓存，避免反复加载。 */
private object CoverPaletteCache {
    private val map = LinkedHashMap<String, CoverPalette?>(16, 0.75f, true)
    private const val MAX = 16

    fun get(url: String): CoverPalette? = map[url]

    fun put(url: String, palette: CoverPalette?) {
        map[url] = palette
        while (map.size > MAX) {
            val eldest = map.keys.firstOrNull() ?: break
            map.remove(eldest)
        }
    }
}
