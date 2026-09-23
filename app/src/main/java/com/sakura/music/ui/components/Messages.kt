package com.sakura.music.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 把 ViewModel 里的一条消息弹成 snackbar。
 *
 * 每个页面都有一份自己的 [SnackbarHostState]，所以这里只负责「显示后回报已消费」，
 * 消息本身由页面从状态里取。
 */
@Composable
fun SnackbarMessages(
    hostState: SnackbarHostState,
    message: String?,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            hostState.showSnackbar(message)
            onConsumed()
        }
    }
}
