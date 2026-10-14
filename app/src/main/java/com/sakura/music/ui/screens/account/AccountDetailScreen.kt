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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.PublicUser
import com.sakura.music.data.model.ServerCredentialItem
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.AccountAvatar
import com.sakura.music.ui.components.ConfirmDialog
import com.sakura.music.ui.components.ContentTopPadding
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.SakuraCard
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SectionHeader
import com.sakura.music.ui.components.SettingRow
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.TogetherOverviewPanel
import com.sakura.music.ui.components.TogetherPartnerDialog
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import com.sakura.music.ui.util.formatPlayedAt
import com.sakura.music.ui.util.formatVipLabel

/**
 * 账户中心：账号资料。
 *
 * 从账号页的头像那一栏进来，装的是「会改到账号本身」的东西：昵称与头像、密码、
 * 退出登录；中间**只读**展示第三方账号绑定了什么（绑定流程只在网页端 / 桌面端），
 * 最后是**一起听**的统计面板（读数 + 伙伴列表，与网页端账户中心同一栏、同样排在最后）。
 * 外观与播放偏好不在这里——那些在设置页。
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
    // 点开统计面板里某位伙伴时，记住是哪一位（userId → 昵称）；null = 关着。
    var partnerFor by remember { mutableStateOf<Pair<String, String>?>(null) }

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
                    }

                    /*
                     * 第三方账号：**只读展示**绑定了什么。绑定 / 解绑（扫码那一套）只在
                     * 网页端与桌面端提供，这里不重复一套入口，只把结果摆出来。
                     */
                    Spacer(Modifier.height(22.dp))
                    SectionHeader("第三方账号", modifier = Modifier.padding(start = 22.dp))
                    SakuraCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ThirdPartyAccounts()
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

                    /*
                     * 一起听排在最末：它是「你和另一个人的事」，与上面那些「这个账号本身」的
                     * 东西不同类，放最后既不打断账号资料的连贯。
                     * 这里只放统计面板（与网页端账户中心同一栏）：读数 + 伙伴列表，
                     * 房间入口在播放条上（见 TogetherSheet），账号相关页面不再给。
                     */
                    Spacer(Modifier.height(22.dp))
                    SectionHeader("一起听", modifier = Modifier.padding(start = 22.dp))
                    SakuraCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TogetherOverviewPanel(
                            onOpenPartner = { userId, nickname ->
                                partnerFor = userId to nickname
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

    partnerFor?.let { (userId, nickname) ->
        TogetherPartnerDialog(
            partnerId = userId,
            partnerName = nickname,
            onDismiss = { partnerFor = null },
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

/**
 * 第三方账号：**只读**展示这个账号绑定了什么。
 *
 * 绑定 / 解绑的扫码流程只在网页端与桌面端提供，这里不摆入口，只把结果列出来：
 * - 「存到服务器」的凭据：带头像、昵称与登录方式，各端通用；
 * - 「仅本机」的凭据：只认这台设备的请求头，看不到账号信息，如实说明；
 * - 没绑的平台：一行「未绑定」。
 */
@Composable
private fun ThirdPartyAccounts() {
    val container = appContainer()
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var serverItems by remember { mutableStateOf<List<ServerCredentialItem>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        loading = true
        failed = false
        val result = runCatching { container.api.serverCredentials() }
        loading = false
        result
            .onSuccess { serverItems = it.items }
            .onFailure { failed = true }
    }

    // 本机凭据只有 Cookie、没有账号信息；读一次即可——在这一页上它不会变。
    val localBound = remember {
        Platform.entries.filter { it.isKnown }.associateWith { container.localCredentials.cookieOf(it) != null }
    }

    when {
        loading && serverItems.isEmpty() -> {
            Text(
                text = "正在读取…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        failed && serverItems.isEmpty() -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "读取失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { attempt++ }) { Text("重试") }
            }
        }

        else -> {
            val known = Platform.entries.filter { it.isKnown }
            val anyBound = known.any { platform ->
                serverItems.any { it.platform == platform } || localBound[platform] == true
            }
            if (!anyBound && !loading) {
                Text(
                    text = "还没有绑定第三方音乐账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            known.forEachIndexed { index, platform ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ThirdPartyAccountRow(
                    platform = platform,
                    server = serverItems.firstOrNull { it.platform == platform },
                    localOnly = serverItems.none { it.platform == platform } &&
                        localBound[platform] == true,
                )
            }
        }
    }
}

/** 一个平台一行：服务器凭据显示账号，「仅本机」只说明来源，没绑就是「未绑定」。 */
@Composable
private fun ThirdPartyAccountRow(
    platform: Platform,
    server: ServerCredentialItem?,
    localOnly: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nickname = server?.profile?.nickname?.trim().orEmpty()
        if (nickname.isNotEmpty()) {
            AccountAvatar(
                avatar = server?.profile?.avatar,
                fallbackInitial = nickname.take(1).uppercase(),
                modifier = Modifier.size(40.dp),
            )
        } else {
            // 没拿到账号信息时用平台首字占位：这一行的主角是「哪个平台」，不是某个人。
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = platform.label.take(1),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nickname.ifEmpty { platform.label },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 带权重（不撑满）：昵称很长时让位给右边的会员标识，而不是把标识挤出这一行。
                    modifier = Modifier.weight(1f, fill = false),
                )
                /*
                 * 会员标识：接口只给 ASCII 档位标识（`vipLabel`，上游字段名），中文由客户端映射
                 * （见 formatVipLabel）——QQ 音乐是绿钻 / 豪华绿钻 / 超级会员；网易云的档位上游
                 * 给不出可靠的，统一显示 `VIP`。
                 * 「查不到」与「不是会员」由 `vip` 的可空性区分，缺省就整个不显示。
                 */
                if (server?.profile?.vip == true) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = formatVipLabel(platform, server.profile.vipLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = thirdPartySubtitle(platform = platform, server = server, localOnly = localOnly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行副标题：平台名 + 登录方式 + 凭据存在哪 / 还管不管用。 */
@Composable
private fun thirdPartySubtitle(
    platform: Platform,
    server: ServerCredentialItem?,
    localOnly: Boolean,
): String = buildString {
    append(server?.platformLabel?.takeIf { it.isNotBlank() } ?: platform.label)
    when {
        server != null -> {
            when (server.profile.login) {
                "qq" -> append(" · QQ 登录")
                "wx" -> append(" · 微信登录")
            }
            append(if (server.status == "invalid") " · 凭据已失效" else " · 已保存到服务器")
            formatPlayedAt(server.updatedAt)?.let { append(" · 更新于 $it") }
        }

        localOnly -> append(" · 仅本机凭据，看不到账号信息")

        else -> append(" · 未绑定")
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
