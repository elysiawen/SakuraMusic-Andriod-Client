package com.sakura.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * 账户头像：有图就用图，没有就用昵称首字加强调色底。
 *
 * 账号概览页与账户资料页都要用，所以单独放出来，避免两处各画一遍。
 * 尺寸由调用方通过 [modifier] 给（概览页 64dp、资料页 72dp）。
 */
@Composable
fun AccountAvatar(
    avatar: String?,
    fallbackInitial: String,
    modifier: Modifier = Modifier,
) {
    val url = avatar?.takeIf { it.isNotBlank() }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackInitial.ifBlank { "?" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
