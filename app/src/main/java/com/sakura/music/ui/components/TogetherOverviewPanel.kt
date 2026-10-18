package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.room.TogetherOverview
import com.sakura.music.data.room.TogetherPartner
import com.sakura.music.data.room.TogetherPartnerDetail
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.util.formatPlayedAt
import com.sakura.music.ui.util.formatSessionTime
import com.sakura.music.ui.util.formatTogetherDuration

/**
 * 一起听总览的加载态。
 *
 * 与网页端账户中心同一栏：三项读数 + 伙伴列表。房间入口不在这里——
 * 那是播放条上的事（见 [TogetherSheet]）。
 */
data class TogetherOverviewState(
    val loading: Boolean = true,
    val error: String? = null,
    val overview: TogetherOverview? = null,
    val reload: () -> Unit,
)

/**
 * 一起听总览：进页面拉一次，失败给「重试」。
 *
 * [attempt] 是个计数器：`reload` 不自去发请求，只把它加一，由 [LaunchedEffect] 统一加载，
 * 这样初次进入与手动重试走的是同一条路。
 */
@Composable
fun rememberTogetherOverviewState(): TogetherOverviewState {
    val container = appContainer()
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var overview by remember { mutableStateOf<TogetherOverview?>(null) }

    LaunchedEffect(attempt) {
        loading = true
        error = null
        val result = runCatching { container.api.roomOverview() }
        loading = false
        result
            .onSuccess { overview = it }
            .onFailure { error = it.message ?: "一起听数据加载失败" }
    }

    return remember(loading, error, overview) {
        TogetherOverviewState(
            loading = loading,
            error = error,
            overview = overview,
            reload = { attempt++ },
        )
    }
}

/**
 * 账户中心的「一起听」统计面板。
 *
 * 与网页端账户中心同一块：没听过给一句引导；有过伙伴则上面三项读数（见 [TogetherStats]），
 * 下面伙伴列表，点开某一位看和 TA 的每次一起听（见 [TogetherPartnerDialog]）。
 */
@Composable
fun TogetherOverviewPanel(
    onOpenPartner: (userId: String, nickname: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTogetherOverviewState()
    val overview = state.overview

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            overview == null && state.loading -> {
                Text(
                    text = "正在统计…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            overview == null && state.error != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = state.reload) { Text("重试") }
                }
            }

            overview == null -> Unit

            overview.partners.isEmpty() -> {
                Text(
                    text = "还没有和别人一起听过。点播放条上的「一起听」创建房间，" +
                        "把房间码或邀请链接发给好友即可开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                TogetherStats(overview = overview)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))

                overview.partners.forEach { partner ->
                    TogetherPartnerRow(
                        partner = partner,
                        onClick = { onOpenPartner(partner.userId, partner.nickname) },
                    )
                }
            }
        }
    }
}

/** 伙伴列表的一行：头像 + 昵称 + 最近一次，右侧是累计时长与「可点开」的箭头。 */
@Composable
private fun TogetherPartnerRow(
    partner: TogetherPartner,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            avatar = partner.avatar,
            fallbackInitial = partner.nickname.ifBlank { "友" }.take(1).uppercase(),
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = partner.nickname.ifBlank { "好友" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val recent = partner.lastAt.takeIf { it.isNotBlank() }?.let { formatPlayedAt(it) }
            if (recent != null) {
                Text(
                    text = "最近 $recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatTogetherDuration(partner.seconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 「和某位好友的一起听记录」弹层。
 *
 * 明细可能不可用（服务端那张表可以随时被删），这时要说清「只有累计可用」，
 * 而不是显示成"一次都没听过"。
 */
@Composable
fun TogetherPartnerSheet(
    partnerId: String,
    partnerName: String,
    onDismiss: () -> Unit,
) {
    val container = appContainer()
    var loading by remember(partnerId) { mutableStateOf(true) }
    var error by remember(partnerId) { mutableStateOf<String?>(null) }
    var detail by remember(partnerId) { mutableStateOf<TogetherPartnerDetail?>(null) }

    LaunchedEffect(partnerId) {
        loading = true
        error = null
        val result = runCatching { container.api.roomPartnerDetail(partnerId) }
        loading = false
        result
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "记录加载失败" }
    }

    SheetScaffold(title = "一起听记录", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            // 先落到局部变量：`detail` 是委托属性，`when` 里没法智能转换。
            val current = detail
            when {
                loading && current == null -> Text(
                    text = "正在读取…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )

                current == null -> Text(
                    text = error ?: "记录加载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )

                else -> PartnerDetailBody(detail = current, fallbackName = partnerName)
            }
        }
    }
}

/** 弹层主体：伙伴标题栏 + 明细列表（或「明细不可用」的说明）。 */
@Composable
private fun PartnerDetailBody(detail: TogetherPartnerDetail, fallbackName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(
                avatar = detail.partner.avatar,
                fallbackInitial = detail.partner.nickname
                    .ifBlank { fallbackName }
                    .ifBlank { "友" }
                    .take(1)
                    .uppercase(),
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.partner.nickname.ifBlank { fallbackName }.ifBlank { "好友" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sessions = detail.sessionCount
                Text(
                    text = buildString {
                        append("一起听过 ${formatTogetherDuration(detail.totalSeconds)}")
                        if (sessions != null) append(" · $sessions 次")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val items = detail.sessions
        when {
            // 明细表被删掉的情况：说明清楚，不假装"没听过"。
            items == null -> {
                Text(
                    text = "这里只有累计时长可用：每一次的明细已经不再记录了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items.isEmpty() -> {
                Text(
                    text = "还没有一起听过的记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatSessionTime(item.startedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatTogetherDuration(item.seconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // 服务端最多返回 200 条：被截断时补一句说明，别让人以为只听过这么几次。
                val total = detail.sessionCount
                if (total != null && items.size < total) {
                    Text(
                        text = "仅显示最近 ${items.size} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
