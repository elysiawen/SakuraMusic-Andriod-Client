import type { LyricLine } from '@/utils/lyric';

/**
 * 逐字擦亮：把当前行的每个字按自身时间轴写成 `--wp` 进度（经典/沉浸共用）。
 * 行内进度必须逐帧写，走响应式重渲染太贵；没有逐字时间轴的行不做处理。
 */
export function applyWordProgress(
  line: LyricLine | undefined,
  seconds: number,
  wordEls: (HTMLElement | null)[],
): void {
  if (!line?.words?.length) return;
  wordEls.length = line.words.length;
  for (let wi = 0; wi < line.words.length; wi += 1) {
    const element = wordEls[wi];
    const word = line.words[wi];
    if (!element || !word) continue;
    const progress =
      seconds >= word.end
        ? 1
        : seconds <= word.start
          ? 0
          : (seconds - word.start) / Math.max(0.01, word.end - word.start);
    element.style.setProperty('--wp', `${(progress * 100).toFixed(2)}%`);
  }
}
