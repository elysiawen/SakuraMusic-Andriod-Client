package com.sakura.music.ui.screens.player.nowplaying.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * 播放进度按帧插值的连续时钟。
 *
 * 播放器状态是 400ms 轮询一次，拿它直接算逐字进度就会一格一格跳。这里以每次状态更新
 * 为锚点，用 [withFrameNanos] 在两拍之间按帧插值——和网页端 `requestAnimationFrame`
 * + `preciseTime()` 是同一套思路。
 *
 * 返回 [State] 而不是值：只有真正订阅它的那一行歌词会逐帧重组，整页不会跟着每帧重画。
 * 暂停、缓冲、拖动进度、非逐字歌词时都不起帧循环。
 */
@Composable
fun rememberLyricPositionMs(positionMs: Long, active: Boolean): State<Long> {
    val live = remember { mutableLongStateOf(positionMs) }
    val anchorPosition = remember { mutableLongStateOf(positionMs) }
    val anchorNanos = remember { mutableLongStateOf(System.nanoTime()) }

    // 状态每 400ms 来一拍：重新对齐锚点，seek / 拖动造成的跳变也在这里被吸收。
    LaunchedEffect(positionMs) {
        anchorPosition.longValue = positionMs
        anchorNanos.longValue = System.nanoTime()
        live.longValue = positionMs
    }

    LaunchedEffect(active) {
        if (!active) {
            live.longValue = positionMs
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { frameTimeNanos ->
                // 最长只外推 600ms：状态流若被后台卡住，歌词宁可停一下也不要跑飞。
                val elapsedMs = ((frameTimeNanos - anchorNanos.longValue) / 1_000_000L)
                    .coerceIn(0L, 600L)
                live.longValue = anchorPosition.longValue + elapsedMs
            }
        }
    }
    return live
}
