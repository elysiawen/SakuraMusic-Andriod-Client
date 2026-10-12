package com.sakura.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 经典版式里封面那一栏的尺寸。 */
private val CompactCoverSize = 112.dp

/** 圆形头像稍微小一点，圆形本来就比方形显得大。 */
private val CompactAvatarSize = 104.dp

/**
 * 详情页头部的**紧凑版式**（设置 → 外观 → 详情页样式 选「经典」时用它）。
 *
 * 左边一张封面（歌手传圆形），右边分类行、标题、副标题、统计和徽标，下面一排动作。
 * 与 [MediaHero] 是同一个头的两种版式：那个把封面铺满顶部，这个把封面缩进页内。
 * 两者的参数一一对应，所以调用方只换组件名，不用重新组织数据。
 */
@Composable
fun MediaHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** 标题上方的分类行，例如「专辑 · QQ 音乐」。 */
    label: String? = null,
    subtitle: String? = null,
    /** 数量与总时长一类的统计行，用 [trackListStats] 生成。 */
    stats: String? = null,
    description: String? = null,
    coverUrl: String? = null,
    circularCover: Boolean = false,
    badges: (@Composable RowScope.() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    playAllEnabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (circularCover) {
                ArtistAvatar(avatar = coverUrl, size = CompactAvatarSize)
            } else {
                SquareCover(url = coverUrl, modifier = Modifier.size(CompactCoverSize))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = title.ifBlank { "未知" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!stats.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (badges != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        content = badges,
                    )
                }
            }
        }

        // 调用方常把同一句话同时塞给 subtitle / stats / description，重复的就不再画一遍。
        val note = description?.takeIf { it.isNotBlank() }
        if (note != null && note != subtitle && note != stats) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onPlayAll != null) {
            Spacer(Modifier.height(12.dp))
            // 这套版式用文字按钮：封面本来就小，一颗实心大按钮压在旁边会把主次颠倒过来。
            TextButton(onClick = onPlayAll, enabled = playAllEnabled) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("播放全部")
            }
        }
    }
}
