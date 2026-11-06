package com.sakura.music.ui.screens.player.nowplaying.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sakura.music.core.player.PlaybackRoute
import com.sakura.music.data.model.ArtistRef
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.prefs.PlayMode
import com.sakura.music.data.room.RoomMemberView
import com.sakura.music.ui.components.AccountAvatar

/** 播放模式的小图标：循环按钮一轮换四种状态。 */
val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.Order, PlayMode.All -> Icons.Rounded.Repeat
        PlayMode.One -> Icons.Rounded.RepeatOne
        PlayMode.Shuffle -> Icons.Rounded.Shuffle
    }

/* ------------------------------ 封面 ------------------------------ */

@Composable
fun MaterialCoverArt(
    cover: String?,
    showBuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(70.dp),
            )
        }

        if (showBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(30.dp)
                    .background(Color(0x8C000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** 网页端样式的封面：直角边 + 大圆角 + 一层向下的投影。 */
@Composable
fun DesktopCover(
    cover: String?,
    modifier: Modifier = Modifier,
    corner: Int = 24,
    shadow: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(corner.dp),
        color = Color.Transparent,
        shadowElevation = if (shadow) 26.dp else 0.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(46.dp),
                )
            }
        }
    }
}

/* ------------------------------ 曲目信息 ------------------------------ */

/**
 * 副标题：`歌手 · 专辑`，两边的名字各自可点。
 *
 * 用带 link 的 [AnnotatedString] 而不是把它们拆成一行 Text——这一行要保持原来的居中、
 * 单行、超长省略号的行为，拆成多个 Text 就得自己重做换行与省略。
 */
fun subtitleWithLinks(
    track: UnifiedTrack,
    linkColor: Color,
    onOpenArtist: (Platform, String, String) -> Unit,
    onOpenAlbum: (Platform, String, String) -> Unit,
): AnnotatedString {
    val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor))

    return buildAnnotatedString {
        val artists = track.artists.ifEmpty { listOf(ArtistRef(name = "未知歌手")) }
        artists.forEachIndexed { index, artist ->
            if (index > 0) append(" / ")

            val platform = artist.platform
            val id = artist.id
            if (platform != null && platform.isKnown && id != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "artist:${platform.id}:$id",
                        styles = linkStyles,
                    ) { onOpenArtist(platform, id, artist.name) }
                ) { append(artist.name) }
            } else {
                append(artist.name)
            }
        }

        val album = track.album
        val albumPlatform = album.platform
        val albumId = album.id
        if (album.name.isNotBlank()) {
            append(" · ")
            if (albumPlatform != null && albumPlatform.isKnown && albumId != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "album:${albumPlatform.id}:$albumId",
                        styles = linkStyles,
                    ) { onOpenAlbum(albumPlatform, albumId, album.name) }
                ) { append(album.name) }
            } else {
                append(album.name)
            }
        }
    }
}

@Composable
fun MaterialTrackInfo(title: String, subtitle: AnnotatedString, trial: Boolean) {
    Text(
        text = title.ifBlank { "未知歌曲" },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    if (trial) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "仅试听片段 · 可在「更多」里切换音源试试",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/* ------------------------------ 路由标签 ------------------------------ */

/**
 * 这次播放的字节从哪儿来：本地缓存 / CDN 直连 / 网关中转。
 * 贴在封面右上角，回答「为什么这首歌要等半天」「为什么流量跑得多」。
 */
@Composable
fun RouteBadge(route: PlaybackRoute, modifier: Modifier = Modifier) {
    if (route == PlaybackRoute.Unknown) return

    val icon: ImageVector
    val container: Color
    val content: Color
    when (route) {
        PlaybackRoute.Unknown -> {
            icon = Icons.Rounded.MusicNote
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        PlaybackRoute.Cached -> {
            icon = Icons.Rounded.Cached
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        PlaybackRoute.Direct -> {
            icon = Icons.Rounded.Bolt
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }

        PlaybackRoute.Proxy -> {
            icon = Icons.Rounded.SwapHoriz
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        shape = CircleShape,
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = route.hint,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = route.label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

/** 网页端样式的小标签：半透明白底 / 浅色纸面，和封面上的那块玻璃同一族。 */
@Composable
fun DesktopTag(
    text: String,
    palette: DesktopPalette,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (palette.isDark) Color.White.copy(alpha = 0.08f) else palette.surfaceStrong,
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: palette.border),
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = accent ?: palette.muted,
            maxLines = 1,
        )
    }
}

/** 网页端样式的来源小标签：半透明白底，和封面上的那块玻璃同一族。 */
@Composable
fun DesktopRouteTag(route: PlaybackRoute, palette: DesktopPalette) {
    if (route == PlaybackRoute.Unknown) return
    Surface(
        shape = CircleShape,
        color = if (palette.isDark) Color.White.copy(alpha = 0.08f) else palette.surfaceStrong,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Text(
            text = route.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = palette.muted,
        )
    }
}

/* ------------------------------ 一起听（封面下沿） ------------------------------ */

/**
 * 封面下沿那一块：两个人的头像（中间一个回形针）＋ 一起听读数。
 * 都压在封面里面，不额外占一行的歌词高度。
 */
@Composable
fun TogetherCoverBand(
    text: String,
    me: RoomMemberView?,
    partner: RoomMemberView?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(TOGETHER_COVER_SCRIM)
            .clickable(onClick = onClick)
            .padding(top = 34.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TogetherAvatar(member = me, fallback = "我", modifier = Modifier.size(34.dp))
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .padding(horizontal = 9.dp)
                    .size(18.dp),
            )
            TogetherAvatar(member = partner, fallback = "友", modifier = Modifier.size(34.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = TOGETHER_HEART,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TogetherAvatar(
    member: RoomMemberView?,
    fallback: String,
    modifier: Modifier = Modifier,
) {
    if (member == null) {
        Box(
            modifier = modifier.border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "等待好友加入",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp),
            )
        }
        return
    }
    AccountAvatar(
        avatar = member.avatar,
        fallbackInitial = member.nickname.takeIf { it.isNotBlank() }?.take(1) ?: fallback,
        modifier = modifier,
    )
}

private val TOGETHER_HEART = Color(0xFFFF5A79)

private val TOGETHER_COVER_SCRIM = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.26f to Color.Black.copy(alpha = 0.14f),
    0.50f to Color.Black.copy(alpha = 0.40f),
    0.76f to Color.Black.copy(alpha = 0.64f),
    1.00f to Color.Black.copy(alpha = 0.80f),
)
