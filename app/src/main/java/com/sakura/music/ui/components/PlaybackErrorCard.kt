package com.sakura.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 卡片进出场：从上方滑下来、同时淡入，收起时原路退回。 */
private val cardEnter = slideInVertically(
    animationSpec = tween(260, easing = FastOutSlowInEasing),
    initialOffsetY = { -it },
) + fadeIn(tween(180))

private val cardExit = slideOutVertically(
    animationSpec = tween(220, easing = FastOutSlowInEasing),
    targetOffsetY = { -it },
) + fadeOut(tween(140))

/**
 * 播放失败时的提示卡片。
 *
 * 早先这里是一层整屏遮罩：封面、标题、歌词全被压在底下，整个播放器页变成一张错误页。
 * 但用户真正需要的是「知道哪儿出了问题」加上「还能看见自己在听哪首」——封面和标题
 * 恰恰是判断这件事的依据。所以改成一张卡片，从顶部滑入，其余部分照常可见可点。
 *
 * 定位交给调用方（放页面的 Box 里 `align`），这里只管内容和动画。
 */
@Composable
fun PlaybackErrorCard(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    title: String = "无法播放",
) {
    AnimatedVisibility(
        visible = visible,
        enter = cardEnter,
        exit = cardExit,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRetry != null) {
                        Button(
                            onClick = onRetry,
                            // 反色配：容器色当文字色、卡片底色当按钮底色，在这张卡上最醒目。
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                contentColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text("重试")
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
