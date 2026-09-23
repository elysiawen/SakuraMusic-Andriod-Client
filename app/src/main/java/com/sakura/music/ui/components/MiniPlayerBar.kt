package com.sakura.music.ui.components

import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sakura.music.core.player.MusicPlaybackService
import kotlinx.coroutines.delay

/**
 * 全应用共用的一条播放会话。
 *
 * 挂在导航图之上：播放器页与迷你播放条驱动的是同一个会话，
 * 而不是各自去连一个。
 */
val LocalMusicController = compositionLocalOf<MediaController?> { null }

/** 只要调用方还在组合树里，就一直连着 [MusicPlaybackService]。 */
@Composable
fun rememberMusicController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            // get() 不会阻塞：监听器只在完成后才被调用。
            { controller = runCatching { future.get() }.getOrNull() },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            controller = null
            // 断开连接不会让服务停止播放——播放器本来就活在那边。
            MediaController.releaseFuture(future)
        }
    }

    return controller
}

/**
 * 迷你播放条。
 *
 * 会话里没有音乐时什么都不画，所以既不会留一条空条，也不会挡住内容。
 * 放在哪儿、要不要让开系统手势区由调用方决定——它是「底部栏的一部分」还是
 * 「详情页底部单独的一条」，两种情况的 insets 不一样。
 */
@Composable
fun MiniPlayerBar(
    controller: MediaController?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = controller

    var item by remember { mutableStateOf<MediaItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        if (player == null) {
            item = null
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    item = mediaItem
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                }
            }
            player.addListener(listener)

            // 监听器只听得见「变化」，中途挂上去的时候一切都没变化，所以先取一次现状。
            item = player.currentMediaItem
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            onDispose { player.removeListener(listener) }
        }
    }

    val visible = item != null && item?.mediaMetadata?.title != null

    LaunchedEffect(player, visible) {
        val active = player ?: return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        while (true) {
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration.takeIf { it > 0 } ?: 0L
            isPlaying = active.isPlaying
            isBuffering = active.playbackState == Player.STATE_BUFFERING
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val current = item ?: return@AnimatedVisibility
        val active = player ?: return@AnimatedVisibility
        val metadata = current.mediaMetadata

        val fraction = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen),
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CoverArt(
                            url = metadata.artworkUri?.toString(),
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            iconSize = 20.dp,
                        )
                        if (isBuffering) {
                            // 缓冲时在封面上压一个半透明圆点，比换图标更不打断视线。
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(11.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.title?.toString().orEmpty().ifEmpty { "未知曲目" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metadata.artist?.toString().orEmpty().ifEmpty { "未知歌手" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(onClick = { if (active.isPlaying) active.pause() else active.play() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                        )
                    }

                    IconButton(onClick = { active.seekToNextMediaItem() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                    }
                }

                // 细发丝进度条：自己画，而不是塞一个 Slider 进来把条撑高。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
