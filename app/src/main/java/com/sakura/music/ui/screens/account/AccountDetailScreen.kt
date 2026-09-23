package com.sakura.music.ui.screens.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.PublicUser
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AccountAvatar
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.ContentTopPadding
import com.sakura.music.ui.components.InfoNotice
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.NoticeTone
import com.sakura.music.ui.components.SakuraCard
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SectionHeader
import com.sakura.music.ui.components.SettingRow
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel

/**
 * 账户资料。
 *
 * 从账号页的头像那一栏进来，装的是「会改到账号本身」的东西：昵称与头像、密码、
 * 第三方账号绑定情况、退出登录。外观与播放偏好不在这里——那些在设置页。
 */
@Composable
fun AccountDetailScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { AccountViewModel(it.authRepository) }
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editingProfile by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }

    val user = auth.user
    val current = state

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = "账户资料",
                subtitle = user?.let { "@${it.username}" } ?: if (auth.offline) "离线" else "未登录",
                onBack = navigator::back,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ListBottomPadding),
            ) {
                Spacer(Modifier.height(ContentTopPadding))

                if (user == null) {
                    Text(
                        text = "登录已失效，请重新登录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    AccountHeader(user = user)

                    Spacer(Modifier.height(22.dp))
                    SectionHeader("资料", modifier = Modifier.padding(start = 22.dp))
                    SakuraCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingRow(
                            title = "昵称与头像",
                            subtitle = user.nickname.ifBlank { "未设置昵称" },
                            onClick = { editingProfile = true },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingRow(
                            title = "修改密码",
                            subtitle = "修改后所有设备都会退出登录",
                            onClick = { changingPassword = true },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingRow(
                            title = "刷新统计",
                            subtitle = "重新拉取收藏、歌单与历史的数量",
                            onClick = viewModel::refreshStats,
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    SectionHeader("第三方账号", modifier = Modifier.padding(start = 22.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        InfoNotice(
                            icon = Icons.Rounded.QrCode,
                            title = "扫码绑定请在网页端或桌面端完成",
                            message = "Android 端暂不提供二维码绑定。绑定时选择「存到服务器」的话，" +
                                "这台设备什么都不用配；选择「仅本机」的凭据只留在绑定它的那台设备上。",
                            tone = NoticeTone.Accent,
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    SectionHeader("会话", modifier = Modifier.padding(start = 22.dp))
                    SakuraCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingRow(
                            title = "退出登录",
                            subtitle = "只清除本机的会话 Cookie",
                            onClick = { loggingOut = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Rounded.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            SnackbarMessages(
                hostState = snackbar,
                message = current.message,
                onConsumed = viewModel::consumeMessage,
            )
        }
    }

    if (editingProfile && user != null) {
        EditProfileDialog(
            user = user,
            busy = current.busy,
            onDismiss = { editingProfile = false },
            onSave = { nickname, avatar ->
                editingProfile = false
                viewModel.saveProfile(nickname, avatar)
            },
        )
    }

    if (changingPassword) {
        ChangePasswordDialog(
            busy = current.busy,
            onDismiss = { changingPassword = false },
            onConfirm = { oldPassword, newPassword ->
                changingPassword = false
                viewModel.changePassword(oldPassword, newPassword)
            },
        )
    }

    if (loggingOut) {
        ConfirmDialog(
            title = "退出登录",
            message = "退出后需要重新输入用户名与密码；播放队列会被清空。",
            confirmLabel = "退出",
            destructive = true,
            onConfirm = {
                loggingOut = false
                container.playbackCenter.clearQueue()
                viewModel.logout()
            },
            onDismiss = { loggingOut = false },
        )
    }
}

/** 顶部的身份行：头像 + 昵称 + 用户名（+ 管理员标签）。 */
@Composable
private fun AccountHeader(user: PublicUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            avatar = user.avatar,
            fallbackInitial = user.initial,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.nickname.ifBlank { user.username },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.isAdmin) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "管理员",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 昵称 + 头像链接。头像留空表示恢复默认，所以要显式告诉服务端「清掉」。 */
@Composable
private fun EditProfileDialog(
    user: PublicUser,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var nickname by remember { mutableStateOf(user.nickname) }
    var avatar by remember { mutableStateOf(user.avatar.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑资料") },
        text = {
            Column {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 32) nickname = it },
                    label = { Text("昵称") },
                    supportingText = { Text("1–32 个字符") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = avatar,
                    onValueChange = { avatar = it },
                    label = { Text("头像链接（留空则恢复默认）") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nickname, avatar) },
                enabled = nickname.trim().isNotEmpty() && !busy,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChangePasswordDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }

    val mismatch = repeated.isNotEmpty() && repeated != newPassword
    val canSubmit = oldPassword.isNotEmpty() &&
        newPassword.length >= 8 &&
        newPassword == repeated &&
        !busy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("当前密码") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    supportingText = { Text("至少 8 位") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = { Text("再输一次新密码") },
                    isError = mismatch,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "修改成功后，包括这台在内的所有设备都会被登出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(oldPassword, newPassword) }, enabled = canSubmit) {
                Text("确认修改")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
