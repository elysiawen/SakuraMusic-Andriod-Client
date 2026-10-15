package com.sakura.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Platform

/** 平台品牌色：只用在那个小圆点上，整体配色依然是克制的。 */
val Platform.brandColor: Color
    get() = when (this) {
        Platform.NETEASE -> Color(0xFFE0453A)
        Platform.QQ -> Color(0xFF31C27C)
        Platform.UNKNOWN -> Color(0xFF9AA0A6)
    }

/** 只留小点时的直径。名单行里塞不下更粗的色块，再小又分不清红绿。 */
private val DOT_SIZE = 9.dp

/** 空心点（「这个平台也有，但不是眼下用的那个」）的圈口粗细。 */
private val DOT_RING = 1.5.dp

/** 可点击时套在点外面的内边距，把命中区从 [DOT_SIZE] 撑到 25dp。 */
private val DOT_TOUCH_PADDING = 8.dp

/**
 * 平台徽标。
 *
 * 合并后的歌手 / 专辑会有多个平台入口，点哪个徽标就进哪个平台的详情页——
 * 两个平台的 ID 体系不同，绝不能混着拼。
 *
 * [showLabel] 决定写不写平台名：
 * - **列表行**（歌曲 / 歌手 / 专辑 / 歌单）传 `false`，只留颜色小点——一行里的文字本来就密，
 *   而「红点 = 网易云、绿点 = QQ 音乐」是固定映射，看几次就记住了；
 * - **作为选项或读数出现的地方**（音源切换弹层、导入状态）保持默认的 `true`：
 *   那里平台名本身就是信息，去掉就只剩两个点要靠记颜色去选。
 *
 * [selected] 在两种形态下都表示「这是**眼下会用的那个**平台」：
 * - 写平台名时，它给药丸加上底色与一圈描边；
 * - 只留小点时，选中的画**实心**、其余画**空心圈**。一首歌两个平台都有时，这样既看得见
 *   「都有」，也能一眼看出眼下用的是哪个（与网页端曲目行的实心 / 描边同源）。
 *   只有单一来源的地方（专辑行、歌单行）传 `selected = true`，让那个唯一的点保持实心。
 */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 是否写出平台名；`false` = 只留颜色小点（见文件头）。 */
    showLabel: Boolean = true,
) {
    if (!showLabel) {
        /*
         * 只留小点：刻意**不套**那层药丸（底色 + 描边）。药丸是为装下平台名才存在的，
         * 一排两三个点还各自裹着底色和边框，在密集的列表里比原来更吵。
         *
         * 实心 / 空心 的语义见文件头；这里刻意不用「变淡」来表示非选中——
         * 变淡会把红绿一起冲淡，而看颜色正是这几个点唯一的用途。
         */
        Box(
            modifier = modifier
                // clickable 写在 padding **之前**，命中区才会把内边距算进去：
                // 9dp 的点手指点不准，可点击时（歌手行的多平台入口）靠这圈撑到 25dp。
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(if (onClick != null) DOT_TOUCH_PADDING else 0.dp)
                // 文字没了，这个徽标对读屏就成了隐形的：补一句平台名兜住无障碍。
                .semantics { contentDescription = platform.label },
            contentAlignment = Alignment.Center,
        ) {
            val dot = Modifier
                .size(DOT_SIZE)
                .clip(CircleShape)
            if (selected) {
                Box(dot.background(platform.brandColor))
            } else {
                Box(dot.border(DOT_RING, platform.brandColor, CircleShape))
            }
        }
        return
    }

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
