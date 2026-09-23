package com.sakura.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 让任意 composable 读到当前选中的强调色。 */
val LocalAccentTheme: ProvidableCompositionLocal<AccentTheme> =
    staticCompositionLocalOf { AccentTheme.Sakura }

private fun buildLight(accent: AccentTheme) = lightColorScheme(
    primary = lightAccent(accent).primary,
    onPrimary = lightAccent(accent).onPrimary,
    primaryContainer = lightAccent(accent).primaryContainer,
    onPrimaryContainer = lightAccent(accent).onPrimaryContainer,
    secondary = lightAccent(accent).secondary,
    onSecondary = lightAccent(accent).onSecondary,
    secondaryContainer = lightAccent(accent).secondaryContainer,
    onSecondaryContainer = lightAccent(accent).onSecondaryContainer,
    tertiary = lightAccent(accent).tertiary,
    onTertiary = lightAccent(accent).onTertiary,
    background = Neutral.BackgroundLight,
    onBackground = Neutral.OnBackgroundLight,
    surface = Neutral.SurfaceLight,
    onSurface = Neutral.OnBackgroundLight,
    surfaceVariant = Neutral.SurfaceVariantLight,
    onSurfaceVariant = Neutral.OnSurfaceVariantLight,
    // 五个 container 层级都要给全：少给的那个会回落到 Material 默认色（带紫调），
    // 在只有中性色的界面上会很明显。歌词卡片用的就是 surfaceContainerLow。
    surfaceContainerLowest = Neutral.SurfaceContainerLowestLight,
    surfaceContainerLow = Neutral.SurfaceContainerLowLight,
    surfaceContainer = Neutral.SurfaceContainerLight,
    surfaceContainerHigh = Neutral.SurfaceVariantLight,
    surfaceContainerHighest = Neutral.SurfaceContainerHighestLight,
    outline = Neutral.OutlineLight,
    outlineVariant = Neutral.OutlineVariantLight,
    error = Neutral.ErrorLight,
    onError = Neutral.OnErrorLight,
    errorContainer = Neutral.ErrorContainerLight,
    scrim = Color(0x99000000),
)

private fun buildDark(accent: AccentTheme) = darkColorScheme(
    primary = darkAccent(accent).primary,
    onPrimary = darkAccent(accent).onPrimary,
    primaryContainer = darkAccent(accent).primaryContainer,
    onPrimaryContainer = darkAccent(accent).onPrimaryContainer,
    secondary = darkAccent(accent).secondary,
    onSecondary = darkAccent(accent).onSecondary,
    secondaryContainer = darkAccent(accent).secondaryContainer,
    onSecondaryContainer = darkAccent(accent).onSecondaryContainer,
    tertiary = darkAccent(accent).tertiary,
    onTertiary = darkAccent(accent).onTertiary,
    background = Neutral.BackgroundDark,
    onBackground = Neutral.OnBackgroundDark,
    surface = Neutral.SurfaceDark,
    onSurface = Neutral.OnBackgroundDark,
    surfaceVariant = Neutral.SurfaceVariantDark,
    onSurfaceVariant = Neutral.OnSurfaceVariantDark,
    surfaceContainerLowest = Neutral.SurfaceContainerLowestDark,
    surfaceContainerLow = Neutral.SurfaceContainerLowDark,
    surfaceContainer = Neutral.SurfaceContainerDark,
    surfaceContainerHigh = Neutral.SurfaceVariantDark,
    surfaceContainerHighest = Neutral.SurfaceContainerHighestDark,
    outline = Neutral.OutlineDark,
    outlineVariant = Neutral.OutlineVariantDark,
    error = Neutral.ErrorDark,
    onError = Neutral.OnErrorDark,
    errorContainer = Neutral.ErrorContainerDark,
    scrim = Color(0xCC000000),
)

/**
 * 应用主题。
 *
 * @param accent 强调色板，设置页里可以随时切换。
 * @param darkTheme 默认跟随系统。
 */
@Composable
fun SakuraTheme(
    accent: AccentTheme = AccentTheme.Sakura,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) buildDark(accent) else buildLight(accent)

    CompositionLocalProvider(LocalAccentTheme provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SakuraTypography,
            shapes = SakuraShapes,
        ) {
            SystemBarAppearance(darkTheme = darkTheme)
            content()
        }
    }
}

/**
 * 让状态栏与导航栏的图标跟着应用主题走。
 *
 * `enableEdgeToEdge()` 默认按**系统**的夜间模式决定图标深浅，而深浅色在应用里是
 * 用户自己选的（设置 → 外观）。两者不一致时——比如系统是浅色、应用强制深色——
 * 状态栏图标会继续用深色，画在深色背景上几乎看不见，所以这里按实际主题再设一次。
 */
@Composable
private fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    DisposableEffect(darkTheme, view) {
        val window = (view.context as? Activity)?.window
        // 预览里没有真正的 Window，跳过。
        if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view).apply {
                // 这两个标志问的是「背景亮不亮」：深色主题下背景是暗的，图标要用浅色。
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose { }
    }
}
