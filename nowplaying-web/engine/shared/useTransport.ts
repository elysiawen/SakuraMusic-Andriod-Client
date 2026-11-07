import { computed, ref } from 'vue';
import { usePlayerStore } from '@/stores/player';
import { useRoomStore } from '@/stores/room';

/**
 * 全屏播放页四个样式共用的传输控制。
 *
 * 一起听时坐在座位上，播放、切歌、模式、定位都改走 room store；否则用本地播放器。
 * 拖动进度先记在 seekDraft 里（拖动中显示的位置不跟播放器跳），松手才真正 seek。
 */
export function useTransport() {
  const player = usePlayerStore();
  const room = useRoomStore();

  const roomSeat = computed(() => room.seatActive);
  const seekDraft = ref<number | null>(null);
  const shownTime = computed(() => seekDraft.value ?? player.currentTime);
  const duration = computed(() => player.duration || 0);
  const progressPercent = computed(() =>
    duration.value > 0 ? Math.min(100, (shownTime.value / duration.value) * 100) : 0,
  );
  const volumePercent = computed(() => (player.muted ? 0 : Math.round(player.volume * 100)));

  function toggle(): void {
    if (roomSeat.value) room.toggle();
    else player.toggle();
  }

  function prev(): void {
    if (roomSeat.value) room.prev();
    else void player.prev();
  }

  function next(): void {
    if (roomSeat.value) room.next();
    else void player.next();
  }

  function cycleMode(): void {
    if (roomSeat.value) room.cycleMode();
    else player.cycleMode();
  }

  function seekTo(seconds: number): void {
    const value = Math.max(0, seconds);
    seekDraft.value = null;
    if (roomSeat.value) room.seek(value);
    else player.seek(value);
  }

  function onSeekInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(value)) seekDraft.value = value;
  }

  function onSeekDone(): void {
    if (seekDraft.value !== null) seekTo(seekDraft.value);
  }

  function onVolumeInput(event: Event): void {
    player.setVolume(Number((event.target as HTMLInputElement).value) / 100);
  }

  return {
    seekDraft,
    shownTime,
    duration,
    progressPercent,
    volumePercent,
    toggle,
    prev,
    next,
    cycleMode,
    seekTo,
    onSeekInput,
    onSeekDone,
    onVolumeInput,
  };
}
