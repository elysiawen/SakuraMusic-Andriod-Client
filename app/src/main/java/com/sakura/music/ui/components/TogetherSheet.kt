package com.sakura.music.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.room.RoomMemberView
import com.sakura.music.data.room.TogetherOverview
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatTogetherDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「一起听」面板。
 *
 * **双人房与多人房是两套界面**，由 `room.kind` 分岔（不要在这里散落别的判据）：
 * - 不在房间：创建（双人）+ 加入；
 * - `duo`：两人对置、人人平权、没有成员管理；
 * - `group`：成员列表 + 管理区（房主才可解散）——服务端尚未开放，是个空壳。
 *
 * 这里只负责「看和点」：真正的状态机在 [com.sakura.music.core.player.RoomSync] 里，
 * 所有操作都是提交给服务端、等它广播回来再体现，本地不做抢先执行。
 */
@Composable
fun TogetherSheet(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val container = appContainer()
    val roomSync = container.roomSync
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val code by roomSync.code.collectAsStateWithLifecycle()
    val room by roomSync.room.collectAsStateWithLifecycle()
    val spectating by roomSync.spectating.collectAsStateWithLifecycle()

    var busy by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var confirmingEnd by remember { mutableStateOf(false) }

    /*
     * 时长读数的每秒节拍。
     *
     * 服务端只在房间快照里给一个整数秒，正在走表时并不会每秒推送——靠这个节拍让读数自己走。
     * 只在走表（两人都在）时才起，没人和你一起听的时候没有必要空转。
     */
    var tick by remember { mutableIntStateOf(0) }
    val counting = room?.togetherCounting == true
    LaunchedEffect(counting) {
        if (!counting) return@LaunchedEffect
        while (true) {
            delay(1_000)
            tick++
        }
    }

    fun copyText(text: String, label: String) {
        clipboard.setText(AnnotatedString(text))
        onMessage("$label 已复制")
    }

    /** 邀请用系统分享面板发出去：对面在网页端打开这个链接就自动入房。 */
    fun shareInvite() {
        val link = roomSync.inviteLink()
        if (link.isBlank()) return
        val text = "来和我一起听歌，房间码 $code：\n$link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "邀请好友一起听")) }
            .onFailure { onMessage("没有可用的分享方式") }
    }

    SheetScaffold(title = "一起听", onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            val roomCode = code

            if (roomCode == null) {
                NotInRoom(
                    busy = busy,
                    joinCode = joinCode,
                    onJoinCodeChange = { input ->
                        // 房间码是 6 位大写字母数字（服务端剔掉了易混的 0/O/1/I/L）。
                        joinCode = input.filter { it.isLetterOrDigit() }.uppercase().take(6)
                    },
                    onCreate = {
                        if (busy) return@NotInRoom
                        busy = true
                        scope.launch {
                            roomSync.create()
                            busy = false
                        }
                    },
                    onJoin = {
                        if (busy || joinCode.length < 4) return@NotInRoom
                        busy = true
                        scope.launch {
                            if (roomSync.join(joinCode)) joinCode = ""
                            busy = false
                        }
                    },
                )
                return@Column
            }

            val isDuo = room?.isGroup != true
            if (isDuo) {
                DuoBody(
                    code = roomCode,
                    // 用「我这个账号的成员 / 对面那位」而不是 `room.me`：
                    // `me` 是设备级的，旁观设备收到的快照里自己那条也是 false，
                    // 照它画就会把账号席位和对面那位画反。
                    me = roomSync.myAccountMember(),
                    partner = roomSync.otherMember(),
                    sessionText = togetherText(
                        remember(tick, room?.togetherSessionSeconds) {
                            roomSync.togetherSessionLiveSeconds()
                        },
                    ),
                    totalText = togetherText(
                        remember(tick, room?.togetherSeconds) { roomSync.togetherLiveSeconds() },
                    ),
                    spectator = spectating,
                    seatDeviceName = roomSync.seatDeviceName(),
                    busy = busy,
                    onCopyCode = { copyText(roomCode, "房间码") },
                    onShareInvite = ::shareInvite,
                    onTransfer = {
                        if (busy) return@DuoBody
                        busy = true
                        scope.launch {
                            roomSync.join(roomCode)
                            busy = false
                        }
                    },
                )
            } else {
                GroupBody(members = room?.members.orEmpty(), capacity = room?.capacity ?: 0)
            }

            Spacer(Modifier.height(16.dp))

            when {
                // 旁观时唯一的动作就是「转移过来」，收尾那一下不该出现在这台设备上。
                spectating -> Text(
                    text = "转移到本机之后，就可以在这里切歌、拖进度了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                /*
                 * 双人房只有一个动作：一个人结束，房间就散了。
                 * 「退出但让房间留着」在两个人之间没有意义——剩下那一个人也听不成一起听。
                 */
                isDuo -> PrimaryButton(
                    text = "结束一起听",
                    icon = Icons.Rounded.Groups,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    onClick = { confirmingEnd = true },
                )
                else -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = "退出房间",
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { roomSync.leave() } },
                    )
                    SecondaryButton(
                        text = "解散房间",
                        modifier = Modifier.weight(1f),
                        enabled = room?.canDissolve == true,
                        onClick = { confirmingEnd = true },
                    )
                }
            }
        }
    }

    if (confirmingEnd) {
        ConfirmDialog(
            title = "结束一起听",
            message = "一个人结束，双方就都退出一起听，各自恢复自己的播放。",
            confirmLabel = "结束",
            destructive = true,
            onConfirm = {
                confirmingEnd = false
                onDismiss()
                scope.launch { roomSync.dissolve() }
            },
            onDismiss = { confirmingEnd = false },
        )
    }
}

/* ------------------------------ 不在房间 ------------------------------ */

@Composable
private fun NotInRoom(
    busy: Boolean,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Text(
        text = "和朋友在同一首歌上同步播放，任一方都能切歌、拖动进度。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 顶上多留一点：这段话紧贴在弹层标题的分隔线下面，贴边写会显得那道线压在字上。
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
    )
    Spacer(Modifier.height(16.dp))

    PrimaryButton(
        text = "创建双人一起听",
        icon = Icons.Rounded.Groups,
        loading = busy,
        modifier = Modifier.padding(horizontal = 24.dp),
        onClick = onCreate,
    )

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = joinCode,
            onValueChange = onJoinCodeChange,
            singleLine = true,
            placeholder = { Text("房间码") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onJoin, enabled = !busy && joinCode.length >= 4) {
            Text("加入")
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "让好友在「一起听」里输入房间码即可加入。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/* ------------------------------ 双人房 ------------------------------ */

@Composable
private fun DuoBody(
    code: String,
    me: RoomMemberView?,
    partner: RoomMemberView?,
    sessionText: String,
    totalText: String,
    /** 播放不在本机（同账号的另一台设备在放）。 */
    spectator: Boolean,
    seatDeviceName: String?,
    busy: Boolean,
    onCopyCode: () -> Unit,
    onShareInvite: () -> Unit,
    onTransfer: () -> Unit,
) {
    val partnerOnline = partner?.online == true

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberColumn(
            modifier = Modifier.weight(1f),
            nickname = me?.nickname?.takeIf { it.isNotBlank() } ?: "我",
            deviceName = me?.deviceName.orEmpty(),
            avatar = me?.avatar,
            highlight = true,
            online = true,
        )

        // 中间那条「关系」：对面在线就是在同步，没来就是在等人。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Text(
                text = when {
                    partnerOnline -> "同步中"
                    partner != null -> "对方离线"
                    else -> "等待中"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (partnerOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (partner != null) {
            MemberColumn(
                modifier = Modifier.weight(1f),
                nickname = partner.nickname.takeIf { it.isNotBlank() } ?: "好友",
                // 离线时把设备名换掉：那台设备此刻是什么不重要，重要的是它没连上。
                deviceName = if (partnerOnline) partner.deviceName else "暂时离线",
                avatar = partner.avatar,
                highlight = false,
                online = partnerOnline,
            )
        } else {
            MemberColumn(
                modifier = Modifier.weight(1f),
                nickname = "等待好友",
                deviceName = "邀请 TA 加入",
                avatar = null,
                highlight = false,
                online = false,
                empty = true,
            )
        }
    }

    if (spectator) {
        /*
         * 播放不在这台设备上：这是**必须写清楚**的一句话。
         *
         * 不写的话，用户看到的是「一起听」「两人头像」「时长」都在，唯独这台设备没声——
         * 只会以为一起听坏了。说清楚「在哪台上放」，再给一个「转移到本机」，
         * 这台设备才有意义。
         */
        SpectatorNotice(deviceName = seatDeviceName, busy = busy, onTransfer = onTransfer)
        if (partner == null) {
            Spacer(Modifier.height(12.dp))
            InviteCard(code = code, onCopyCode = onCopyCode, onShareInvite = onShareInvite)
        }
        return
    }

    if (partner == null) {
        // 人还没到齐：把邀请放在最显眼的地方。
        InviteCard(code = code, onCopyCode = onCopyCode, onShareInvite = onShareInvite)
    } else {
        /*
         * 人齐了才显示时长：两个读数必须各自标清楚——
         * 「本次」是这一次房间听了多久，「累计」是你们俩历史上一起听的总时长（跨房间、跨会话）。
         * 对方还没进来时不知道"跟谁"一起听，所以这块也等人齐才有意义。
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DurationRow(label = "本次", value = sessionText)
            DurationRow(label = "累计", value = totalText)
        }
    }
}

/**
 * 「播放不在这台设备上」那一条 + 「转移到本机」。
 *
 * 转移走的就是普通的 `join`：服务端那边「同一账号只占一个席位」，join 的语义正好是
 * 把席位接过来。对面会收到「席位已被接走」，**降级为旁观**——房间照旧显示在那台上，
 * 只是不再负责播放，随时能再转移回来。
 */
@Composable
private fun SpectatorNotice(
    deviceName: String?,
    busy: Boolean,
    onTransfer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = deviceName?.let { "正在「$it」上播放，本机没在放" }
                    ?: "正在你的另一台设备上播放，本机没在放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "转移到本机",
            icon = Icons.Rounded.SwapHoriz,
            loading = busy,
            onClick = onTransfer,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "转移之后，那台设备会恢复单独播放。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 邀请卡片：房间里还只有我一个人时，把房间码放到最显眼的地方。 */
@Composable
private fun InviteCard(
    code: String,
    onCopyCode: () -> Unit,
    onShareInvite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "房间码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "复制房间码",
                icon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f),
                onClick = onCopyCode,
            )
            PrimaryButton(
                text = "分享邀请",
                icon = Icons.Rounded.Share,
                modifier = Modifier.weight(1f),
                onClick = onShareInvite,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "把房间码或邀请链接发给好友，TA 打开后自动加入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
/** 两人对置里的一侧：头像、昵称、设备名。 */
@Composable
private fun MemberColumn(
    nickname: String,
    deviceName: String,
    avatar: String?,
    highlight: Boolean,
    online: Boolean,
    modifier: Modifier = Modifier,
    empty: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (empty) {
                // 虚位以待：别做成一个看起来已经有人、只是灰掉的头像。
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                AccountAvatar(
                    avatar = avatar,
                    fallbackInitial = nickname.take(1),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .then(
                            // 本机那一侧描一圈强调色：两个人并排时一眼能认出自己。
                            if (highlight) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            if (online && !empty) {
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ONLINE_GREEN),
                )
            }
        }
        Text(
            text = nickname,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = deviceName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「本次 / 累计」的一行。 */
@Composable
private fun DurationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/* ------------------------------ 多人房（空壳） ------------------------------ */

@Composable
private fun GroupBody(members: List<RoomMemberView>, capacity: Int) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(
                    avatar = member.avatar,
                    fallbackInitial = member.nickname.take(1),
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.nickname.ifBlank { "好友" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = member.deviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when {
                        member.me -> "我"
                        !member.online -> "离线"
                        member.role == "owner" -> "房主"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (members.size < capacity) {
            Text(
                text = "还可加入 ${capacity - members.size} 人",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "多人一起听还在开发中，当前只支持双人房。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 在线小圆点的绿：与系统「在线」的惯例色一致，两种主题下都认得出来。 */
private val ONLINE_GREEN = Color(0xFF34C759)

/** 时长读数的人话写法（`38 秒` / `24 分 12 秒`），与账户页那一栏共用同一个格式。 */
private fun togetherText(seconds: Double): String = formatTogetherDuration(seconds)

/* ------------------------------ 账户页那一栏 ------------------------------ */

/**
 * 一起听总览：累计时长、一起听过的伙伴、次数（[TogetherStats] 用）。
 *
 * 进房 / 退房会改变它，所以房间码一变就重新拉一次；账户页有两处要它
 * （账号页的入口卡片、账户资料页的「一起听」一节），逻辑只有一份，别各写一套。
 *
 * 刻意吞掉失败（拿不到就是 null）：它是账户页顺带展示的读数，为它弹一次错误没有意义，
 * 界面按「还没读到」显示 `--`。
 */
@Composable
fun rememberTogetherOverview(): TogetherOverview? {
    val container = appContainer()
    val roomCode by container.roomSync.code.collectAsStateWithLifecycle()
    var overview by remember { mutableStateOf<TogetherOverview?>(null) }
    LaunchedEffect(roomCode) {
        overview = runCatching { container.api.roomOverview() }.getOrNull()
    }
    return overview
}

/**
 * 一起听那一栏的说明行。
 *
 * 在房间里要说清「和谁、房间码多少」「放音在哪台设备上」，不在房间里就说这一栏是干什么的。
 * 账户页两处（账号页、账户中心）共用，省得两处各写一套 `when`、改一处漏一处。
 */
@Composable
fun rememberTogetherSubtitle(): String {
    val container = appContainer()
    val roomCode by container.roomSync.code.collectAsStateWithLifecycle()
    // 读一次快照：`otherMember()` / `seatDeviceName()` 读的是普通流，不订阅就不会重组。
    val room by container.roomSync.room.collectAsStateWithLifecycle()
    val spectating by container.roomSync.spectating.collectAsStateWithLifecycle()

    val partner = room?.let { container.roomSync.otherMember()?.nickname }
    return when {
        roomCode == null -> "和朋友同步听同一首歌，任一方都能切歌、拖进度"

        // 旁观：播放不在本机，把「在哪台」写出来，免得用户以为一起听坏了。
        spectating ->
            "正在「${container.roomSync.seatDeviceName() ?: "你的另一台设备"}」上播放 · 房间码 $roomCode"

        !partner.isNullOrBlank() -> "正在和 $partner 一起听 · 房间码 $roomCode"

        else -> "房间码 $roomCode，等好友加入"
    }
}

/**
 * 一起听的三项读数：累计时长 / 一起听过的伙伴 / 一起听次数。
 *
 * 与网页端账户中心是同一套数字。前两项来自可靠的累计表；**次数来自明细表**——
 * 那张表可能不存在，服务端那时给 null，这一格就整个不出现（显示成 0 会是在撒谎）。
 * 还没读到时显示 `--`，与账号页那三格统计（收藏 / 歌单 / 历史）的写法一致。
 */
@Composable
fun TogetherStats(overview: TogetherOverview?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        ReadingCell(
            label = "累计时长",
            value = overview?.let { formatTogetherDuration(it.totalSeconds) } ?: "--",
            modifier = Modifier.weight(1f),
        )
        ReadingCell(
            label = "一起听过的伙伴",
            value = overview?.let { "${it.partnerCount} 位" } ?: "--",
            modifier = Modifier.weight(1f),
        )
        if (overview == null || overview.sessionCount != null) {
            ReadingCell(
                label = "一起听次数",
                value = overview?.sessionCount?.let { "$it 次" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 读数里的一格：数字在上、标签在下。 */
@Composable
private fun ReadingCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
