package com.sakura.music.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sakura.music.MainActivity
import com.sakura.music.MusicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 承载播放会话的前台服务。
 *
 * 播放器活在这里而不是界面里：退到后台、锁屏后音乐要继续，媒体通知也要有个归属。
 * 界面通过 `MediaController` 连上来，只是它的遥控器。
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /** 跟设置里「与其他应用一起播放」用的；服务结束时取消。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val container = (application as MusicApp).container

        val player = ExoPlayer.Builder(this)
            // 外面那层是缓存：播过的歌第二遍不再走网络。
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.playbackDataSourceFactory))
            // 音频焦点交给 ExoPlayer 管：来电、其它应用抢焦点时该降该停都由它处理。
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            // 亮屏时也允许下载，避免锁屏后音频断流。
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 「与其他应用一起播放」打开时不再抢音频焦点，两边各放各的。
        // 设置是异步读的，所以第一下可能还是按默认（抢焦点）走，读出来立刻改过来——
        // 那时候通常还没开始播。
        serviceScope.launch {
            container.settings.mixWithOthers.collect { mix ->
                player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ !mix)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 用户把应用从最近任务里划掉：没在播就直接收摊，别留一个空通知。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
