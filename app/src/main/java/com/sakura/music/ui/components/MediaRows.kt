package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PlatformStatus
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.PlaylistSummary
import com.sakura.music.data.model.UnifiedAlbum
import com.sakura.music.data.model.UnifiedArtist

/** 歌手行：圆形头像 + 名字 + 各平台入口徽标。 */
@Composable
fun ArtistRow(
    artist: UnifiedArtist,
    onClick: (Platform, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(avatar = artist.avatar, size = 48.dp)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.subtitle.isNullOrBlank()) {
                Text(
                    text = artist.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 同名歌手只占一条，点哪个平台的徽标就进哪个平台的详情页：两个平台的 ID 不通用。
            if (artist.sources.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    artist.sources.take(3).forEach { source ->
                        PlatformBadge(
                            platform = source.platform,
                            onClick = { onClick(source.platform, source.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 专辑行。点整行进首位来源平台的专辑页。 */
@Composable
fun AlbumRow(
    album: UnifiedAlbum,
    onClick: (Platform, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = album.sources.firstOrNull { it.platform.isKnown }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = source != null) {
                if (source != null) onClick(source.platform, source.id)
            }
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            url = album.cover,
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.medium,
            iconSize = 22.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name.ifBlank { "未知专辑" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                source?.platform?.let {
                    PlatformBadge(platform = it)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = listOf(
                        album.artistText.ifBlank { "未知歌手" },
                        album.releaseDate?.take(4).orEmpty(),
                        album.trackCount?.let { "$it 首" }.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 平台歌单 / 榜单行。 */
@Composable
fun PlaylistSummaryRow(
    summary: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            url = summary.cover,
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.medium,
            iconSize = 22.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title.ifBlank { "未命名歌单" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.platform.isKnown) {
                    PlatformBadge(platform = summary.platform)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = listOf(
                        summary.trackCount?.let { "$it 首" }.orEmpty(),
                        summary.description.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 自建歌单行，右侧多一个「更多」。 */
@Composable
fun UserPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            url = playlist.cover,
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.medium,
            iconSize = 22.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name.ifBlank { "未命名歌单" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOf(
                    "${playlist.trackCount} 首",
                    playlist.description.takeIf { it.isNotBlank() }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

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

/**
 * 每个平台这次搜索的结果概况。
 *
 * `count` 是合并去重前的条数，所以它可能大于列表里看到的数量——这不是 bug。
 */
@Composable
fun PlatformStatusRow(
    statuses: List<PlatformStatus>,
    modifier: Modifier = Modifier,
) {
    if (statuses.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        statuses.forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(platform = status.platform)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (status.ok) "${status.count} 条" else "失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
