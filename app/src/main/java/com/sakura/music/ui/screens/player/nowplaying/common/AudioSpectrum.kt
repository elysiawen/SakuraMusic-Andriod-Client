package com.sakura.music.ui.screens.player.nowplaying.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.hypot
import kotlin.math.pow

/** FFT 最低有效频率；再往下是 DC 和亚低频噪声，画出来只会是一根常亮柱。 */
private const val MIN_HZ = 40f
private const val MAX_HZ = 16_000f
/** Visualizer 的 FFT 是 8-bit 有符号，单点幅值上限约 128。 */
private const val FFT_FULL_SCALE = 128f
/** 30 次/秒，够跟上柱条平滑，又不至于让回调把 CPU 吃满。 */
private const val CAPTURE_RATE_MILLIHZ = 30_000
private val CAPTURE_SIZES = intArrayOf(1024, 512, 256)

/**
 * 从真实音频会话取 FFT，压成 [barCount] 根归一化柱值。
 *
 * [start] 失败（无权限、session 无效、设备不支持）时 [snapshot] 一直是 null，
 * 调用方继续用模拟频谱，界面不会白掉。回调跑在 Visualizer 自己的线程上，
 * 所以每帧都换一个新数组再发布；主线程只读引用，不会和回调抢着改同一块数组。
 */
class AudioSpectrum(private val barCount: Int) {

    @Volatile
    private var frame: FloatArray? = null
    private var visualizer: Visualizer? = null
    private val smoothed = FloatArray(barCount)

    /** 最近一帧柱值，范围 0..1；null 表示还没有真实数据。 */
    fun snapshot(): FloatArray? = frame

    fun start(sessionId: Int): Boolean {
        if (sessionId <= 0 || visualizer != null) return false
        return try {
            visualizer = Visualizer(sessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                captureSize = CAPTURE_SIZES.firstOrNull { it in range[0]..range[1] } ?: range[1]
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int,
                        ) = onFft(fft, samplingRate)
                    },
                    minOf(CAPTURE_RATE_MILLIHZ, Visualizer.getMaxCaptureRate()),
                    false,
                    true,
                )
                enabled = true
            }
            true
        } catch (t: Throwable) {
            stop()
            false
        }
    }

    fun stop() {
        visualizer?.let { viz ->
            runCatching { viz.enabled = false }
            runCatching { viz.release() }
        }
        visualizer = null
        frame = null
        smoothed.fill(0f)
    }

    /**
     * 每两个 byte 是一个频点的实部/虚部，第 k 个 bin 对应 `k * 采样率 / captureSize`。
     * 按对数频率把 bin 归到柱里（低频分得细、高频合并），桶内取峰值；高频天然能量低，
     * 补一点斜率，最后起跳快、落下慢地平滑，柱条才不会抖成一团噪点。
     */
    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        if (fft.size < 4 || samplingRateMilliHz <= 0) return
        val binCount = fft.size / 2
        val binHz = (samplingRateMilliHz / 1000f) / fft.size
        val maxHz = (samplingRateMilliHz / 2000f).coerceAtMost(MAX_HZ)
        if (maxHz <= MIN_HZ) return
        val ratio = maxHz / MIN_HZ

        for (i in 0 until barCount) {
            val from = (MIN_HZ * ratio.pow(i.toFloat() / barCount) / binHz)
                .toInt().coerceIn(1, binCount - 1)
            val to = (MIN_HZ * ratio.pow((i + 1).toFloat() / barCount) / binHz)
                .toInt().coerceIn(from + 1, binCount)
            var peak = 0f
            for (bin in from until to) {
                val re = fft[bin * 2].toDouble()
                val im = fft[bin * 2 + 1].toDouble()
                val magnitude = hypot(re, im).toFloat()
                if (magnitude > peak) peak = magnitude
            }
            val target = (peak / FFT_FULL_SCALE * (1f + 1.7f * i / barCount))
                .coerceIn(0f, 1f)
            val k = if (target > smoothed[i]) 0.5f else 0.16f
            smoothed[i] += (target - smoothed[i]) * k
        }
        frame = smoothed.copyOf()
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** 本次进程只问一次；用户拒绝后不再反复弹框，直接回退模拟频谱。 */
private var micPermissionAsked = false

/**
 * 沉浸样式的真实频谱源。
 *
 * 返回 null 表示「这次不用真实数据」（没权限 / session 无效 / 设备不支持），
 * 由调用方继续画模拟节拍。Visualizer 的生命周期跟着 [sessionId] 和组合一起走。
 */
@Composable
fun rememberAudioSpectrum(sessionId: Int, barCount: Int): AudioSpectrum? {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted && !micPermissionAsked) {
            micPermissionAsked = true
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val spectrum = remember { AudioSpectrum(barCount) }
    val active = granted && sessionId > 0
    DisposableEffect(active, sessionId, spectrum) {
        if (active) spectrum.start(sessionId) else spectrum.stop()
        onDispose { spectrum.stop() }
    }
    return spectrum.takeIf { active }
}
