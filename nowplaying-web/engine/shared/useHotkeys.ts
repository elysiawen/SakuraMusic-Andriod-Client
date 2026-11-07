import { onBeforeUnmount, onMounted } from 'vue';
import { usePlayerStore } from '@/stores/player';
import { useUiStore } from '@/stores/ui';

/** 键位要调用的动作（来自 useTransport，保证与界面上的按钮共用同一份状态）。 */
export interface HotkeyActions {
  toggle: () => void;
  seekTo: (seconds: number) => void;
}

/**
 * 全屏播放页的键盘：Esc / L 收起、Q 弹出队列、空格播放暂停、←→ 快退快进 5 秒、
 * ↑↓ 音量。处理函数只在整个播放页打开时生效，且在输入框里不抢键。
 *
 * 挂载与卸载随组件生命周期自动完成（各样式文件不用再单独 addEventListener）。
 */
export function useHotkeys(actions: HotkeyActions): void {
  const player = usePlayerStore();
  const ui = useUiStore();

  function onKeydown(event: KeyboardEvent): void {
    if (!ui.lyricsOpen) return;
    const target = event.target as HTMLElement | null;
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
      return;
    }

    switch (event.key) {
      case 'Escape':
      case 'KeyL':
        ui.closeLyrics();
        break;
      case 'KeyQ':
        ui.toggleQueue();
        break;
      case ' ':
        event.preventDefault();
        actions.toggle();
        break;
      case 'ArrowRight':
        event.preventDefault();
        actions.seekTo(player.currentTime + 5);
        break;
      case 'ArrowLeft':
        event.preventDefault();
        actions.seekTo(player.currentTime - 5);
        break;
      case 'ArrowUp':
        event.preventDefault();
        player.setVolume(Math.min(1, player.volume + 0.05));
        break;
      case 'ArrowDown':
        event.preventDefault();
        player.setVolume(Math.max(0, player.volume - 0.05));
        break;
      default:
        break;
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown));
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
}
