package com.sakura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.Playlist
import com.sakura.music.data.model.UnifiedTrack
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.appContainer
import kotlinx.coroutines.launch

/**
 * 「加入歌单」弹层。
 *
 * 自己把歌单列表拉下来，避免每个调用页面都先备一份；新建歌单也顺手在这里做完。
 */
@Composable
fun AddToPlaylistSheet(
    track: UnifiedTrack,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(track.key) {
        playlists = runCatching { container.libraryRepository.playlists() }
            .onFailure { onMessage(it.friendlyMessage()) }
            .getOrDefault(emptyList())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = "加入歌单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { creating = true }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "新建歌单",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val list = playlists
            if (list == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                }
            } else if (list.isEmpty()) {
                Text(
                    text = "还没有自建歌单，先新建一个吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            container.libraryRepository.addTrack(playlist.id, track)
                                        }.onSuccess { added ->
                                            onMessage(
                                                if (added) {
                                                    "已加入「${playlist.name}」"
                                                } else {
                                                    "「${playlist.name}」里已经有这首歌了"
                                                }
                                            )
                                            onDismiss()
                                        }.onFailure { onMessage(it.friendlyMessage()) }
                                        busy = false
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${playlist.trackCount} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (creating) {
        PlaylistDialog(
            onDismiss = { creating = false },
            // 描述留空就整个不带：新建时没写描述，没必要提交一个空串。
            onSubmit = { name, description ->
                busy = true
                scope.launch {
                    runCatching {
                        container.libraryRepository.createPlaylist(
                            name,
                            description.takeIf { it.isNotBlank() },
                        )
                    }
                        .onSuccess { created ->
                            creating = false
                            onMessage("已创建「${created.name}」")
                            playlists = listOf(created) + (playlists ?: emptyList())
                        }
                        .onFailure { onMessage(it.friendlyMessage()) }
                    busy = false
                }
            },
        )
    }
}

/** 名称上限，与网关 `createPlaylist` 的校验（1–60 位）一致。 */
private const val PLAYLIST_NAME_MAX = 60

/**
 * 描述上限，与网关的 `PLAYLIST_DESCRIPTION_MAX` 一致。
 *
 * 两边必须一致：服务端是对超长内容做 `slice`（**悄悄截掉**），不是报错，所以界面上能填进去
 * 而库里存不下的那一段会凭空消失——用户以为自己写上了。
 */
private const val PLAYLIST_DESCRIPTION_MAX = 800

/**
 * 歌单弹窗：**新建与编辑共用**——两个模式要填的字段完全一样（名称 + 描述），
 * 分成两个弹窗只会把同一份表单抄两遍。
 *
 * - 只给 [initialName] / [initialDescription] 的默认值：新建。
 * - 传当前歌单的值：编辑（歌单页的「编辑信息」走这里），[onSubmit] 一次把两个字段提交上去。
 *
 * 名称发请求前会 trim；描述按原样 trim 后提交（提交空串 = 清掉描述，省略字段 = 保持原值，
 * 网关侧是 `coalesce`）。
 */
@Composable
fun PlaylistDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String) -> Unit,
    title: String = "新建歌单",
    confirmLabel: String = "创建",
    initialName: String = "",
    initialDescription: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= PLAYLIST_NAME_MAX) name = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        if (it.length <= PLAYLIST_DESCRIPTION_MAX) description = it
                    },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("写点什么，之后会在歌单页看到") },
                    // 多行：描述常要写一两句话，单行框里只能左右挪着看。
                    // 这里不拦回车（那是换行），提交走按钮。
                    minLines = 3,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.medium,
                    // 计数靠右，和输入框右下角对齐。
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text("${description.length} / $PLAYLIST_DESCRIPTION_MAX")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(trimmed, description.trim()) },
                enabled = trimmed.isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
