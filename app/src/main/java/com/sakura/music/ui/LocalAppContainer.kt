package com.sakura.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakura.music.AppContainer

/** 把应用级依赖容器提供给整棵组合树。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided. Wrap the UI in CompositionLocalProvider.")
}

/** 任意 composable 里取容器的快捷方式。 */
@Composable
fun appContainer(): AppContainer = LocalAppContainer.current

/**
 * 不用 DI 框架，直接把 [ViewModel] 和容器接起来。
 *
 * ```kotlin
 * val vm = rememberAppViewModel { HomeViewModel(it.api, it.authRepository) }
 * ```
 */
@Composable
inline fun <reified VM : ViewModel> rememberAppViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container) }
        },
    )
}
