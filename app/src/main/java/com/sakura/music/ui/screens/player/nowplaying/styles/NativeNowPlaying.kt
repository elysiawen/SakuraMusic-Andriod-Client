package com.sakura.music.ui.screens.player.nowplaying.styles

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingActions
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingSkin
import com.sakura.music.ui.screens.player.nowplaying.NowPlayingUiState
import com.sakura.music.ui.screens.player.nowplaying.common.MaterialCoverArt
import com.sakura.music.ui.screens.player.nowplaying.common.MaterialLyricBox
import com.sakura.music.ui.screens.player.nowplaying.common.MaterialSeekBlock
import com.sakura.music.ui.screens.player.nowplaying.common.MaterialTrackInfo
import com.sakura.music.ui.screens.player.nowplaying.common.MaterialTransportRow
import com.sakura.music.ui.screens.player.nowplaying.common.NowPlayingTopBar
import com.sakura.music.ui.screens.player.nowplaying.common.RouteBadge
import com.sakura.music.ui.screens.player.nowplaying.common.TogetherCoverBand

/** 竖屏封面占屏宽的比例：再大就会把歌词挤成一条缝。 */
private const val COVER_WIDTH_FRACTION = 0.52f

/**
 * 安卓原有的全屏播放页，布局/动效保持原样，只是把顶栏、封面、歌词、进度与控制
 * 换成公用组件，以便和其它样式共用一份行为。
 */
@Composable
fun NativeNowPlaying(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    collapseGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val track = state.track

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NowPlayingTopBar(
            state = state,
            actions = actions,
            skin = NowPlayingSkin.Material,
            outerHorizontalPadding = 24.dp,
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                ) {
                    val playButtonSize = (maxHeight * 0.16f).coerceIn(44.dp, 70.dp)
                    val gap = (maxHeight * 0.025f).coerceIn(4.dp, 12.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.75f),
                        ) {
                            val side = minOf(maxWidth, maxHeight)
                            Box(
                                modifier = Modifier
                                    .size(side)
                                    .align(Alignment.Center)
                                    .then(collapseGesture),
                            ) {
                                MaterialCoverArt(
                                    cover = track.album.cover,
                                    showBuffering = state.showBuffering,
                                    modifier = Modifier.matchParentSize(),
                                )
                                RouteBadge(
                                    route = state.route,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp),
                                )
                                if (state.inRoom) {
                                    TogetherCoverBand(
                                        text = state.roomTogetherText,
                                        me = state.me,
                                        partner = state.partner,
                                        onClick = actions.openTogether,
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(gap))
                        MaterialTrackInfo(
                            title = track.title,
                            subtitle = state.subtitle,
                            trial = state.trial,
                        )
                        Spacer(Modifier.height(gap))
                        MaterialSeekBlock(
                            positionMs = state.shownPositionMs,
                            durationMs = state.durationMs,
                            onScrub = actions.scrub,
                            onSeek = actions.seek,
                        )
                        Spacer(Modifier.height(gap))
                        MaterialTransportRow(
                            isPlaying = state.isPlaying,
                            canSkip = state.canSkip,
                            playMode = state.playMode,
                            showBuffering = state.showBuffering,
                            onPlayMode = actions.cyclePlayMode,
                            onQueue = actions.openQueue,
                            onPrevious = actions.previous,
                            onToggle = actions.toggle,
                            onNext = actions.next,
                            playButtonSize = playButtonSize,
                        )
                    }
                }

                Spacer(Modifier.width(18.dp))

                MaterialLyricBox(
                    lyrics = state.lyrics,
                    currentIndex = state.lyricIndex,
                    synced = state.lyricSynced,
                    loading = state.lyricLoading,
                    positionMs = state.shownPositionMs,
                    isPlaying = state.isPlaying && !state.isBuffering,
                    wordByWord = state.lyricWordByWord,
                    scrubbing = state.scrubbing,
                    onSeek = actions.seek,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .padding(bottom = 28.dp),
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(COVER_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .then(collapseGesture),
            ) {
                MaterialCoverArt(
                    cover = track.album.cover,
                    showBuffering = state.showBuffering,
                    modifier = Modifier.matchParentSize(),
                )
                RouteBadge(
                    route = state.route,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
                if (state.inRoom) {
                    TogetherCoverBand(
                        text = state.roomTogetherText,
                        me = state.me,
                        partner = state.partner,
                        onClick = actions.openTogether,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            MaterialTrackInfo(
                title = track.title,
                subtitle = state.subtitle,
                trial = state.trial,
            )

            Spacer(Modifier.height(10.dp))

            MaterialLyricBox(
                lyrics = state.lyrics,
                currentIndex = state.lyricIndex,
                synced = state.lyricSynced,
                loading = state.lyricLoading,
                positionMs = state.shownPositionMs,
                isPlaying = state.isPlaying && !state.isBuffering,
                wordByWord = state.lyricWordByWord,
                scrubbing = state.scrubbing,
                onSeek = actions.seek,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            MaterialSeekBlock(
                positionMs = state.shownPositionMs,
                durationMs = state.durationMs,
                onScrub = actions.scrub,
                onSeek = actions.seek,
            )

            Spacer(Modifier.height(12.dp))

            MaterialTransportRow(
                isPlaying = state.isPlaying,
                canSkip = state.canSkip,
                playMode = state.playMode,
                showBuffering = state.showBuffering,
                onPlayMode = actions.cyclePlayMode,
                onQueue = actions.openQueue,
                onPrevious = actions.previous,
                onToggle = actions.toggle,
                onNext = actions.next,
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}
