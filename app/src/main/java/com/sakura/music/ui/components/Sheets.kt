package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.downloads.DownloadStatus
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.ui.appContainer

/** 底部弹层统一的壳：标题 + 分隔线 + 内容。 */
@Composable
private fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/** 单选弹层：音质、音源、播放模式都用它。 */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    optionSubtitle: ((T) -> String?)? = null,
) {
    SheetScaffold(title = title, onDismiss = onDismiss) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = optionLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                    optionSubtitle?.invoke(option)?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 播放队列。序号即队列下标——同一首歌可能在队列里出现多次，只能靠下标定位。 */
@Composable
fun QueueSheet(
    tracks: List<UnifiedTrack>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 打开时定位到正在播的那一首，队列很长时才不用自己找。
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }

    SheetScaffold(
        title = "播放队列（${tracks.size}）",
        onDismiss = onDismiss,
        trailing = {
            if (tracks.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        },
    ) {
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "队列是空的",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SheetScaffold
        }

        QueueList(
            listState = listState,
            tracks = tracks,
            currentIndex = currentIndex,
            onPlayAt = onPlayAt,
            onRemoveAt = onRemoveAt,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QueueList(
    listState: LazyListState,
    tracks: List<UnifiedTrack>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = 460.dp),
    ) {
            itemsIndexed(tracks, key = { index, track -> "$index:${track.key}" }) { index, track ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAt(index) }
                        .padding(start = 24.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title.ifBlank { "未知歌曲" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artistText.ifBlank { "未知歌手" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { onRemoveAt(index) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "从队列移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
    }
}

/**
 * 菜单里的下载一行：未下载 / 下载中 / 已下载三种样子。
 *
 * 单独抽出来是因为它要自己订阅下载中心的状态——菜单本身只是个壳，
 * 不该为了这一行去收集一堆流。
 */
@Composable
private fun DownloadAction(
    track: UnifiedTrack,
    platform: Platform?,
) {
    val container = appContainer()
    val quality by container.settings.quality
        .collectAsStateWithLifecycle(initialValue = Quality.Default)
    val downloads by container.downloadCenter.downloads.collectAsStateWithLifecycle()
    val progress by container.downloadCenter.progress.collectAsStateWithLifecycle()
    // downloads / progress 进 remember 的键：这两个流变了，这一行就要重算。
    val state = remember(downloads, progress, track.key, quality, platform) {
        container.downloadCenter.downloadState(track, quality, platform)
    }
    val center = container.downloadCenter

    when (state.status) {
        DownloadStatus.None -> ActionRow(
            label = "下载到本地",
            icon = Icons.Rounded.Download,
            onClick = { center.start(track, quality, platform) },
        )

        DownloadStatus.Downloading -> ActionRow(
            label = if (state.percent != null) "取消下载（${state.percent}%）" else "取消下载",
            icon = Icons.Rounded.Downloading,
            tint = MaterialTheme.colorScheme.primary,
            onClick = { center.cancel(track, quality, platform) },
        )

        DownloadStatus.Done -> ActionRow(
            label = "已下载 · 删除本地文件",
            icon = Icons.Rounded.Delete,
            tint = MaterialTheme.colorScheme.error,
            onClick = { center.remove(track) },
        )
    }
}

/** 单曲操作菜单里的自定义项。 */
data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * 单曲操作菜单。
 *
 * 歌单详情里会通过 [extraActions] 追加「从歌单移除」，历史页追加「从历史删除」之类，
 * 主菜单本身保持全站一致。
 */
@Composable
fun TrackActionsSheet(
    track: UnifiedTrack,
    isFavorite: Boolean,
    activePlatform: Platform,
    onDismiss: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onSwitchPlatform: ((Platform) -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    /** 当前音质标签；给了它就会出现「音质」这一行。 */
    qualityLabel: String? = null,
    onPickQuality: (() -> Unit)? = null,
    extraActions: List<SheetAction> = emptyList(),
) {
    SheetScaffold(title = track.title.ifBlank { "未知歌曲" }, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            if (onPickQuality != null && qualityLabel != null) {
                ActionRow(
                    label = "音质（当前：$qualityLabel）",
                    icon = Icons.Rounded.HighQuality,
                    onClick = { onDismiss(); onPickQuality() },
                )
            }
            if (onPlayNext != null) {
                ActionRow(
                    label = "下一首播放",
                    icon = Icons.Rounded.PlaylistPlay,
                    onClick = { onDismiss(); onPlayNext() },
                )
            }
            if (onToggleFavorite != null) {
                ActionRow(
                    label = if (isFavorite) "取消收藏" else "收藏",
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else null,
                    onClick = { onDismiss(); onToggleFavorite() },
                )
            }
            if (onAddToPlaylist != null) {
                ActionRow(
                    label = "加入歌单",
                    icon = Icons.Rounded.PlaylistAdd,
                    onClick = { onDismiss(); onAddToPlaylist() },
                )
            }

            // 下载：按当前音质把整首歌存下来，之后离线也能播。
            DownloadAction(
                track = track,
                platform = activePlatform.takeIf { it.isKnown },
            )

            // 这首歌在多个平台都有实体时才给「切换音源」。
            val sources = remember(track) { track.sources.map { it.platform }.filter { it.isKnown }.distinct() }
            if (onSwitchPlatform != null && sources.size > 1) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "切换音源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sources.forEach { platform ->
                            PlatformBadge(
                                platform = platform,
                                selected = platform == activePlatform,
                                onClick = { onDismiss(); onSwitchPlatform(platform) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            extraActions.forEach { action ->
                ActionRow(
                    label = action.label,
                    icon = action.icon,
                    tint = if (action.destructive) MaterialTheme.colorScheme.error else null,
                    onClick = { onDismiss(); action.onClick() },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: Color.Unspecified,
        )
    }
}

/** 通用动作弹层：把一串 [SheetAction] 竖着排开。 */
@Composable
fun ActionsSheet(
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    SheetScaffold(title = title, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            actions.forEach { action ->
                ActionRow(
                    label = action.label,
                    icon = action.icon,
                    tint = if (action.destructive) MaterialTheme.colorScheme.error else null,
                    onClick = { onDismiss(); action.onClick() },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 确认弹层：删除歌单、清空历史这类不可撤销的操作都过一下它。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
