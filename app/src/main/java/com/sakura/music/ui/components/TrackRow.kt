package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.ui.util.formatDuration

/**
 * 歌曲列表行。
 *
 * 全站的歌曲列表都长这样：序号（可选）+ 46dp 封面 + 标题/歌手 + 时长 + 红心 + 更多。
 * 点整行播放，右边的图标各管各的。
 */
@Composable
fun TrackRow(
    track: UnifiedTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    showFavorite: Boolean = true,
    /**
     * 这一首**眼下会用的**音源平台：它的点画实心，其余来源画空心（见 [PlatformBadge]）。
     * 传 null 表示「还不知道」——点照样画，只是没有实心的那个。
     */
    activePlatform: Platform? = null,
    /** 是否在副标题前画平台点。 */
    showSources: Boolean = true,
    /** 覆盖副标题（歌手页显示专辑名、历史页显示播放时间等）。 */
    subtitle: String? = null,
    /** 时长位置的替代文案。 */
    trailingText: String? = null,
    onFavoriteClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (index != null) 10.dp else 16.dp,
                end = 4.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(26.dp),
            )
        }

        CoverArt(
            url = track.album.cover,
            modifier = Modifier.size(46.dp),
            shape = MaterialTheme.shapes.medium,
            iconSize = 20.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { "未知歌曲" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                /*
                 * 每个来源一个点，而不是只画主来源：一首歌两个平台都有时，用户就该看见两个
                 * （与网页端的曲目行一致）。实心的是眼下会用的那个，空心的是「这个平台也有」。
                 *
                 * 不写平台名，是因为这一行已经有歌手、专辑、时长；红绿映射是固定的，看几次就记住。
                 */
                if (showSources) {
                    track.sources.forEach { source ->
                        if (!source.platform.isKnown) return@forEach
                        PlatformBadge(
                            platform = source.platform,
                            selected = source.platform == activePlatform,
                            showLabel = false,
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                }
                Text(
                    text = subtitle
                        ?: listOf(track.artistText.ifBlank { "未知歌手" }, track.album.name)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        if (isPlaying) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "正在播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = trailingText ?: formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showFavorite && onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 歌曲列表的分隔线：从封面右侧起画，与文字对齐。 */
@Composable
fun TrackDivider(startPadding: Int = 76) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = startPadding.dp),
    )
}
