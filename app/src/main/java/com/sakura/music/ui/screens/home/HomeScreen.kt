package com.sakura.music.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.music.data.model.DiscoverKind
import com.sakura.music.data.model.Platform
import com.sakura.music.ui.appContainer
import com.sakura.music.ui.components.ContentTopPadding
import com.sakura.music.ui.components.InfoNotice
import com.sakura.music.ui.components.ListBottomPadding
import com.sakura.music.ui.components.LoadingState
import com.sakura.music.ui.components.NoticeTone
import com.sakura.music.ui.components.PlaylistCard
import com.sakura.music.ui.components.SakuraTopBar
import com.sakura.music.ui.components.SearchEntryBar
import com.sakura.music.ui.components.SectionTitleRow
import com.sakura.music.ui.components.SnackbarMessages
import com.sakura.music.ui.components.StateMessage
import com.sakura.music.ui.components.TrackCard
import com.sakura.music.ui.navigation.CollectionKind
import com.sakura.music.ui.navigation.SakuraNavigator
import com.sakura.music.ui.rememberAppViewModel
import java.time.LocalTime

/**
 * 发现页。
 *
 * 内容是网关把两个平台的推荐聚合出来的：`tracks` 分区是一排歌，`playlists` / `toplists`
 * 分区是一排歌单卡片。单个分区失败只会出现在顶部的提示里，其余照常展示。
 */
@Composable
fun HomeScreen(navigator: SakuraNavigator) {
    val container = appContainer()
    val viewModel = rememberAppViewModel { HomeViewModel(it.api) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val playback by container.playbackCenter.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val current = state

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SakuraTopBar(
                title = "发现",
                subtitle = greeting(auth.user?.nickname),
                actions = {
                    IconButton(
                        onClick = { viewModel.load(force = true) },
                        enabled = !current.refreshing,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "换一批")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            SearchEntryBar(
                onClick = navigator::openSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = ContentTopPadding),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    current.loading -> LoadingState("正在获取推荐…")

                    current.error != null -> StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = "推荐加载失败",
                        message = current.error,
                        actionLabel = "重试",
                        onAction = { viewModel.load(force = true) },
                    )

                    current.sections.isEmpty() -> StateMessage(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "暂时没有可展示的推荐",
                        message = "可能是两个上游服务未启动，或账号未绑定第三方平台导致个性化内容不可用。",
                        actionLabel = "重新加载",
                        onAction = { viewModel.load(force = true) },
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = ContentTopPadding,
                            bottom = ListBottomPadding,
                        ),
                    ) {
                        if (current.errors.isNotEmpty()) {
                            item(key = "errors") {
                                InfoNotice(
                                    icon = Icons.Rounded.CloudOff,
                                    title = "部分板块加载失败",
                                    message = current.errors.joinToString("、") {
                                        "${it.platform.label}·${it.section}"
                                    } + " —— 通常是上游风控或未绑定账号，可稍后重试。",
                                    tone = NoticeTone.Warning,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }

                        item(key = "binding-hint") {
                            InfoNotice(
                                icon = Icons.Rounded.QrCode,
                                title = "第三方账号绑定请在网页端完成",
                                message = "绑定后「每日推荐」「私人 FM」等个性化板块才会出现；搜索与播放始终可用。",
                                tone = NoticeTone.Neutral,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                        items(current.trackSections, key = { "t-${it.key}" }) { section ->
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                SectionTitleRow(
                                    title = section.title,
                                    subtitle = section.subtitle ?: "来自${section.platform.label}",
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(
                                        items = section.tracks,
                                        key = { index, track -> "$index:${track.key}" },
                                    ) { index, track ->
                                        TrackCard(
                                            track = track,
                                            isCurrent = playback.current?.key == track.key,
                                            isPlaying = playback.isPlaying,
                                            onClick = {
                                                container.playbackCenter.play(section.tracks, index)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        items(current.collectionSections, key = { "c-${it.key}" }) { section ->
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                SectionTitleRow(
                                    title = section.title,
                                    subtitle = section.subtitle ?: "来自${section.platform.label}",
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(section.playlists, key = { "${it.platform.id}-${it.id}" }) { summary ->
                                        PlaylistCard(
                                            summary = summary,
                                            onClick = {
                                                navigator.openCollection(
                                                    platform = summary.platform.takeIf { it.isKnown }
                                                        ?: section.platform.takeIf { it.isKnown }
                                                        ?: Platform.NETEASE,
                                                    id = summary.id,
                                                    kind = if (section.kind == DiscoverKind.TOPLISTS) {
                                                        CollectionKind.TOPLIST
                                                    } else {
                                                        CollectionKind.PLAYLIST
                                                    },
                                                    title = summary.title,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "footer") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                )
                                Text(
                                    text = "推荐内容来自网易云音乐与 QQ 音乐；同一首歌会自动合并，播放时可随时切换音源。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                SnackbarMessages(
                    hostState = snackbar,
                    message = current.message,
                    onConsumed = viewModel::consumeMessage,
                )
            }
        }
    }
}

/** 按时间段问声好，顺便把昵称带上。 */
private fun greeting(nickname: String?): String {
    val hour = LocalTime.now().hour
    val part = when {
        hour < 6 -> "夜深了"
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 19 -> "下午好"
        else -> "晚上好"
    }
    return "$part，${nickname?.takeIf { it.isNotBlank() } ?: "朋友"}"
}
