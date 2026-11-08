package com.sakura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.music.core.player.RadioState
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.ui.screens.home.HomeSection
import com.sakura.music.ui.screens.home.radioSectionKey
import java.util.Calendar

private val NETEASE_ACCENT = Color(0xFFE0453A)
private val QQ_ACCENT = Color(0xFF31C27C)

/**
 * 首页顶部的私人电台卡片：网易云「私人漫游」/ QQ「沉浸刷歌」，**一行两张**。
 *
 * 卡片背后是发现页已经取回的一整批（私人 FM 3 首 / 猜你喜欢 5 首），点开时整批入队，
 * 一次电台请求都不打；最后一首开始播时才由播放器续下一批。
 * 平台开关关掉的平台不在这里出现；只有**本机占着一起听席位**时卡片才置灰禁用，
 * 旁观设备（同账号另一台在放）照常可用电台。
 */
@Composable
fun RadioCards(
    platforms: List<Platform>,
    sections: List<HomeSection>,
    radio: RadioState?,
    radioLoading: Boolean,
    seatActive: Boolean,
    onStart: (Platform, List<UnifiedTrack>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (platforms.isEmpty()) return
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 两列固定等宽：只有一个平台启用时，weight(1f) 会让它自然占满整行。
        platforms.chunked(2).forEach { rowPlatforms ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowPlatforms.forEach { platform ->
                    val tracks = remember(sections, platform) {
                        sections.firstOrNull { it.key == radioSectionKey(platform) }?.tracks.orEmpty()
                    }
                    RadioCard(
                        platform = platform,
                        tracks = tracks,
                        active = radio?.platform == platform,
                        loading = radioLoading && radio?.platform == platform,
                        blocked = seatActive,
                        onStart = { onStart(platform, tracks) },
                        onStop = onStop,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 和网页端一样，按时段给一句有生活感的短句。 */
private fun radioGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..5 -> "夜深了，让电台陪你慢慢听~"
    in 6..10 -> "早上好，来点音乐醒醒神吧~"
    in 11..13 -> "午后小憩，随便放点什么吧~"
    in 14..18 -> "尝试来点儿音乐提提神吧~"
    else -> "晚上好，把播放交给电台吧~"
}

@Composable
private fun RadioCard(
    platform: Platform,
    tracks: List<UnifiedTrack>,
    active: Boolean,
    loading: Boolean,
    blocked: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val netease = platform == Platform.NETEASE
    val accent = if (netease) NETEASE_ACCENT else QQ_ACCENT
    val badge = if (netease) "网易云" else "QQ 音乐"
    val title = if (netease) "私人漫游" else "沉浸刷歌"
    val lead = tracks.firstOrNull()
    val caption = lead?.let { track ->
        val artists = track.artists.joinToString(" / ") { it.name }.takeIf { it.isNotBlank() }
        artists?.let { "${track.title} - $it" } ?: track.title
    } ?: "点一下，电台就开始滚动"
    val note = when {
        blocked -> "本机正在「一起听」中，退出后可用"
        active -> "电台播放中 · 可退出"
        else -> "点开即播 · 自动续歌"
    }
    val greeting = remember { radioGreeting() }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .graphicsLayer { alpha = if (blocked) 0.62f else 1f },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 两团柔光：左上取主题容器色、右下掺一点平台色，亮/暗主题都不会刺眼。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-46).dp, y = (-52).dp)
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 46.dp, y = 52.dp)
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.2f), Color.Transparent),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    maxLines = 1,
                                )
                            }
                            if (active && !blocked) {
                                // 退出放在右上角：窄卡片底部已经要放播放钮和封面，
                                // 再挤一个按钮会把封面顶出边界。
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = accent.copy(alpha = 0.35f),
                                            shape = CircleShape,
                                        )
                                        .clickable(onClick = onStop)
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(10.dp),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "退出",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (blocked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    } else {
                                        accent
                                    },
                                )
                                .clickable(enabled = !blocked && !loading, onClick = onStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "开始电台",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // 圆形封面 + 一圈浅色描边，呼应网页端那张「黑胶」的意象。
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CoverArt(
                                url = lead?.album?.cover,
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
