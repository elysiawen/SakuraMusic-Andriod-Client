package com.sakura.music.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.ui.components.SectionTitleRow

/**
 * 搜索记录。
 *
 * 只在输入框为空时出现——那时「最近搜过什么」比任何提示都有用。用的是标签而不是列表：
 * 关键词短，标签一屏能看到十几条，点一下就搜。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistoryPanel(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitleRow(
            title = "搜索记录",
            subtitle = "${history.size} 条",
            actionLabel = "清空",
            onAction = onClear,
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { keyword ->
                HistoryChip(
                    keyword = keyword,
                    onClick = { onPick(keyword) },
                    onRemove = { onRemove(keyword) },
                )
            }
        }
    }
}

/** 一条记录：点词搜它，点 ✕ 删它。 */
@Composable
private fun HistoryChip(
    keyword: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(MaterialTheme.shapes.extraSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            )

            // 单独一个热区：删除和搜索是两件事，别让手指赌在 14dp 的图标上。
            Box(
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "删除这条记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
