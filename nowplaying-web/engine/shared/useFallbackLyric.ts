import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';
import { usePlayerStore } from '@/stores/player';
import { useUiStore } from '@/stores/ui';
import { activeLineIndex, type LyricLine } from '@/utils/lyric';

interface FallbackSources {
  lines: Readonly<Ref<LyricLine[]>>;
  hasLyric: Readonly<Ref<boolean>>;
  /** 有没有正在播放的曲目：没有歌词时用它决定显示「♪」还是空。 */
  hasTrack: Readonly<Ref<boolean>>;
  /** 运行时启动失败（退回纯文字）时才需要跟行。 */
  failed: Readonly<Ref<boolean>>;
}

/**
 * 画布渲染失败时退回「只显示当前这句」的兜底。
 *
 * 当前行只能靠逐帧时钟（`player.preciseTime()`）跟，`currentTime` 约 4Hz 太粗；
 * 正常渲染时这个循环空转，不抢运行时的时钟。
 */
export function useFallbackLyric(sources: FallbackSources) {
  const player = usePlayerStore();
  const ui = useUiStore();

  const activeIdx = ref(-1);
  const fallbackText = computed(() => {
    if (!sources.hasLyric.value) return sources.hasTrack.value ? '♪' : '';
    const index = activeIdx.value;
    return index >= 0 ? (sources.lines.value[index]?.text ?? '') : '';
  });

  let rafId = 0;

  function tick(): void {
    rafId = requestAnimationFrame(tick);
    if (sources.failed.value && ui.lyricsOpen) {
      activeIdx.value = activeLineIndex(sources.lines.value, player.preciseTime());
    }
  }

  onMounted(() => {
    rafId = requestAnimationFrame(tick);
  });
  onBeforeUnmount(() => cancelAnimationFrame(rafId));

  return { fallbackText };
}
