package com.sakura.music.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「这首歌要会员」的小角标。
 *
 * 与账户中心那个会员标识**不是一回事**：那个是**账号级**的（文案来自
 * [com.sakura.music.ui.util.formatVipLabel]，QQ 音乐能分绿钻 / 豪华绿钻 / 超级会员），
 * 这个只回答「这首歌有没有付费门槛」，跟你是不是会员无关，所以文案固定是 `VIP`
 * （网页端列表与播放条上的角标也是这两个词，别改成一个说档位、一个说限制）。
 *
 * 只在 `UnifiedTrack.vip == true` 时调用。「缺省」与 `false` 一律不显示——
 * 「不知道」和「不需要」在角标这件事上表现一致（与网页端同一条约定）。
 */
@Composable
fun VipBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "VIP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
