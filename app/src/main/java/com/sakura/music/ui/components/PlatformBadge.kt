package com.sakura.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Platform

/** 平台品牌色：只用在那个小圆点上，整体配色依然是克制的。 */
val Platform.brandColor: Color
    get() = when (this) {
        Platform.NETEASE -> Color(0xFFE0453A)
        Platform.QQ -> Color(0xFF31C27C)
        Platform.UNKNOWN -> Color(0xFF9AA0A6)
    }

/**
 * 平台徽标。
 *
 * 合并后的歌手 / 专辑会有多个平台入口，点哪个徽标就进哪个平台的详情页——
 * 两个平台的 ID 体系不同，绝不能混着拼。
 */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // 选中态多一圈描边，避免只靠底色变化（深浅色下对比度差别很大）。
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(platform.brandColor)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = platform.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
