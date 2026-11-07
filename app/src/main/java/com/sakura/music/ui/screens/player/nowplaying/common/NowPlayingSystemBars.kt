package com.sakura.music.ui.screens.player.nowplaying.common

import android.app.Activity
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sakura.music.data.prefs.NowPlayingStyle

/**
 * 全屏播放页的系统栏图标配色。
 *
 * 全局 `SystemBarAppearance` 只认应用主题，但沉浸和雾窗铺的是**固定深色**画布，
 * 与主题无关：浅色主题下它会把状态栏 / 手势条图标设成深色，画在深色画布上几乎看不见。
 * 这里按当前样式再覆盖一次，播放页收起时把图标还给主题。
 *
 * 设置用 [LaunchedEffect]（样式切换只重设、不先恢复，图标不会闪一下），
 * 恢复放在只以 view 为 key 的 [DisposableEffect]，避免每次切样式都抖一次状态栏。
 */
@Composable
fun NowPlayingSystemBars(style: NowPlayingStyle) {
    val view = LocalView.current
    val themeIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val darkCanvas = style != NowPlayingStyle.Native && style != NowPlayingStyle.Classic
    val restoreDark by rememberUpdatedState(themeIsDark)

    LaunchedEffect(view, darkCanvas, themeIsDark) {
        applyLightSystemBarIcons(view, darkCanvas || themeIsDark)
    }
    DisposableEffect(view) {
        onDispose { applyLightSystemBarIcons(view, restoreDark) }
    }
}

/** `useLightIcons = true` 即深色画布：系统栏图标用浅色。 */
private fun applyLightSystemBarIcons(view: View, useLightIcons: Boolean) {
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !useLightIcons
        isAppearanceLightNavigationBars = !useLightIcons
    }
}
