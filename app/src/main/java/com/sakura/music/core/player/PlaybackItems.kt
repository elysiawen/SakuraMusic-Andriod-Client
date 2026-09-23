package com.sakura.music.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import kotlinx.serialization.json.Json

/**
 * 播放项与界面模型之间的桥。
 *
 * 队列活在 ExoPlayer 里，而界面需要完整的 [UnifiedTrack]（歌手可跳转、专辑可跳转、
 * 音源可切换）。所以整首歌以 JSON 挂在 `MediaMetadata.extras` 上，同时把标题、
 * 歌手、封面这些「系统要用的」字段填进 metadata，媒体通知和锁屏才有东西显示。
 */
object PlaybackExtras {

    const val TRACK_JSON = "sakura.track"
    const val SOURCE_PLATFORM = "sakura.source.platform"
    const val SOURCE_ID = "sakura.source.id"
    const val QUALITY = "sakura.quality"

    /** 播放项的 URI 方案：音频真正从哪儿来由 [SakuraDataSource] 在打开时决定。 */
    const val SCHEME = "sakura"
    const val HOST = "track"
}

/** 播放项的解析结果：ExoPlayer 拿到的 URI 里只带这三样。 */
data class PlayRequest(
    val platform: Platform,
    val id: String,
    val quality: Quality,
) {
    fun uri(): Uri = Uri.Builder()
        .scheme(PlaybackExtras.SCHEME)
        .authority(PlaybackExtras.HOST)
        .appendPath(platform.id)
        .appendPath(id)
        .appendQueryParameter("quality", quality.id)
        .build()

    /** 解析缓存用的键，必须与 [SakuraDataSource] 里的完全一致。 */
    val cacheKey: String get() = "$platform:$id:$quality"

    companion object {
        fun from(uri: Uri): PlayRequest? {
            if (!PlaybackExtras.SCHEME.equals(uri.scheme, ignoreCase = true)) return null
            val segments = uri.pathSegments
            if (segments.size < 2) return null
            val platform = Platform.fromId(segments[0])
            val id = segments[1]
            if (id.isBlank()) return null
            val quality = Quality.fromId(uri.getQueryParameter("quality"))
            return PlayRequest(platform, id, quality)
        }

        fun of(track: UnifiedTrack, quality: Quality): PlayRequest? {
            val platform = track.primarySource?.platform ?: return null
            val id = track.primarySource?.id?.takeIf { it.isNotBlank() } ?: return null
            return PlayRequest(platform, id, quality)
        }
    }
}

/**
 * 把一首歌变成可播放的媒体项。选择的音源与音质决定 URI，切换音源/音质就是重建它。
 *
 * @param artworkData 封面字节。系统媒体条（SystemUI）只拿到 URL 的话会自己联网取，
 *   离线时那一条就没有封面；把字节直接带上，它就不用去网上找了。
 */
fun UnifiedTrack.toMediaItem(
    json: Json,
    quality: Quality,
    platform: Platform? = null,
    artworkData: ByteArray? = null,
): MediaItem {
    val source = platform?.let { sourceOf(it) } ?: primarySource
    val request = source?.let { PlayRequest(it.platform, it.id, quality) }

    val extras = Bundle().apply {
        putString(PlaybackExtras.TRACK_JSON, json.encodeToString(UnifiedTrack.serializer(), this@toMediaItem))
        putString(PlaybackExtras.SOURCE_PLATFORM, source?.platform?.id)
        putString(PlaybackExtras.SOURCE_ID, source?.id)
        putString(PlaybackExtras.QUALITY, quality.id)
    }

    val metadata = MediaMetadata.Builder()
        .setTitle(title.ifBlank { "未知歌曲" })
        .setArtist(artistText.ifBlank { "未知歌手" })
        .setAlbumTitle(album.name.takeIf { it.isNotBlank() })
        .setArtworkUri(album.cover?.takeIf { it.isNotBlank() }?.let(Uri::parse))
        // 有字节就用字节：系统优先用它，离线也有封面。
        .apply {
            if (artworkData != null && artworkData.isNotEmpty()) {
                setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(request?.uri())
        .setMediaMetadata(metadata)
        .build()
}

/**
 * 给已有的媒体项补上封面字节。
 *
 * 队列是在起播时一次性塞进播放器的，那时只带了封面 URL；切到某一首之前再把字节补上，
 * 系统媒体条（SystemUI）就不必联网去取那张图了。
 */
fun MediaItem.withArtwork(bytes: ByteArray): MediaItem =
    buildUpon()
        .setMediaMetadata(
            mediaMetadata.buildUpon()
                .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
        )
        .build()

/** 从媒体项里取回完整的歌曲；拿不到（例如用户从别的控制器塞进来的项）就返回 null。 */
fun MediaItem.trackOrNull(json: Json): UnifiedTrack? {
    val raw = mediaMetadata.extras?.getString(PlaybackExtras.TRACK_JSON) ?: return null
    return runCatching { json.decodeFromString(UnifiedTrack.serializer(), raw) }.getOrNull()
}

/** 当前播放项选中的音源平台，界面用它高亮「正在用哪个音源」。 */
fun MediaItem.activePlatform(): Platform =
    Platform.fromId(mediaMetadata.extras?.getString(PlaybackExtras.SOURCE_PLATFORM))
