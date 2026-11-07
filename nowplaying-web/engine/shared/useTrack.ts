import { computed } from 'vue';
import { usePlayerStore } from '@/stores/player';
import { useRoomStore } from '@/stores/room';
import { cachedImage } from '@/utils/cacheImage';
import { platformName } from '@/utils/format';
import { parseLyricResult, type LyricLine } from '@/utils/lyric';

/**
 * 全屏播放页四个样式共用的曲目派生状态。
 *
 * 一起听时「是否在播」「循环模式」看房间座位，其余（封面、标题、歌手、专辑、
 * 音源平台、歌词）都来自本地播放器。歌词按行解析，逐字时间轴留在 words 里。
 *
 * `toggleMaximize` 也放这里：页内标题栏的「双击最大化」四处写法完全相同。
 */
export function useTrack() {
  const player = usePlayerStore();
  const room = useRoomStore();

  const roomSeat = computed(() => room.seatActive);
  const shownPlaying = computed(() => (roomSeat.value ? room.playing : player.playing));
  const currentMode = computed(() => (roomSeat.value ? room.playback?.mode ?? 'order' : player.mode));

  const modeIcon = computed(() => {
    if (currentMode.value === 'one') return 'repeat-one';
    if (currentMode.value === 'all') return 'repeat';
    if (currentMode.value === 'shuffle') return 'shuffle';
    return 'list';
  });

  const track = computed(() => player.current);
  const cover = computed(() => cachedImage(track.value?.album.cover));
  const title = computed(() => track.value?.title ?? '未在播放');
  const artists = computed(() => track.value?.artists ?? []);
  const albumName = computed(() => track.value?.album.name ?? '');
  const sourceText = computed(() => {
    const platform = player.activeSource?.platform;
    return platform ? platformName(platform) : '';
  });
  const lines = computed<LyricLine[]>(() => parseLyricResult(player.lyric).lines);
  const hasLyric = computed(() => lines.value.length > 0);

  /** 双击标题栏最大化/还原（macOS 的红绿灯在左边，这条带不背这个习惯）。 */
  function toggleMaximize(): void {
    if (window.sakura.platform === 'darwin') return;
    void window.sakura.windowControls.toggleMaximize();
  }

  return {
    roomSeat,
    shownPlaying,
    currentMode,
    modeIcon,
    track,
    cover,
    title,
    artists,
    albumName,
    sourceText,
    lines,
    hasLyric,
    toggleMaximize,
  };
}
