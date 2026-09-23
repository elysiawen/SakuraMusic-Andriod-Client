package com.sakura.music.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.GatewayDialog
import com.sakura.music.ui.components.PrimaryButton
import com.sakura.music.ui.components.SakuraCard
import com.sakura.music.ui.rememberAppViewModel

/**
 * 登录 / 注册。
 *
 * 网关的音乐类接口全部要求登录，所以这里是进入应用的第一道门。
 * 未登录时 `/api/auth/me` 返回 200 + `user: null`，根组件据此切页面。
 */
@Composable
fun LoginScreen() {
    val container = appContainer()
    val viewModel = rememberAppViewModel { c ->
        LoginViewModel(auth = c.authRepository, settings = c.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gatewayUrl by container.settings.gatewayUrl.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    var showGateway by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(text = "Sakura Music", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "网易云 · QQ 音乐，聚合搜索与播放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))

            SakuraCard {
                ModeTabs(
                    registerMode = state.registerMode,
                    enabled = !state.busy,
                    onSelect = { viewModel.toggleMode() },
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text("用户名") },
                    supportingText = { Text("3–24 位字母、数字、下划线或连字符") },
                    singleLine = true,
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = { Text("密码") },
                    supportingText = { Text("至少 8 位") },
                    singleLine = true,
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.registerMode) ImeAction.Next else ImeAction.Done,
                    ),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.registerMode) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = viewModel::setNickname,
                        label = { Text("昵称（可选）") },
                        singleLine = true,
                        enabled = !state.busy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(20.dp))

                PrimaryButton(
                    text = if (state.registerMode) "注册并进入" else "登录",
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    loading = state.busy,
                )

                if (state.registerMode) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "第一个注册的用户会成为管理员",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "网关地址：$gatewayUrl",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            // 地址填错时这里是唯一的出口：连不上网关就登不进去，自然也进不到设置页。
            TextButton(onClick = { showGateway = true }) {
                Text("修改网关地址", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showGateway) {
        GatewayDialog(onDismiss = { showGateway = false })
    }
}

/** 登录 / 注册二选一，用两段等宽色块而不是 TabRow：这里只有两个选项。 */
@Composable
private fun ModeTabs(
    registerMode: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ModeTab(
            label = "登录",
            selected = !registerMode,
            enabled = enabled && registerMode,
            onClick = onSelect,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ModeTab(
            label = "注册",
            selected = registerMode,
            enabled = enabled && !registerMode,
            onClick = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
