import type { LyricLine } from '@/utils/lyric';
import type { CoverPalette } from '@/utils/palette';
import type { Line, Theme } from '../folia/foliaTypes';

/**
 * 雾笺/绘光共用的两个转换：
 *
 * `toFoliaLines` —— sakura 的歌词行 → folia 的 Line。上游给了逐字歌词（YRC/QRC）
 * 就照真实节奏；只有按行 LRC 的歌 words 为空，由 folia 的 graphemeTiming 按行时长
 * 均摊。行结束优先用逐字数据自带的行尾，否则取下一行开始（封顶 8 秒，免得长间奏
 * 里字写得太慢），最后一行给 4 秒。
 *
 * `buildFoliaTheme` —— 主题色 + 封面色 → folia Theme。两个样式都固定深夜底色
 * （#05070a），取不到主题色时用粉色兜底。
 */
export function toFoliaLines(source: LyricLine[]): Line[] {
  return source.map((line, index) => {
    const next = source[index + 1]?.time;
    const fallbackEnd =
      next === undefined ? line.time + 4 : Math.max(line.time, Math.min(next, line.time + 8));
    return {
      fullText: line.text,
      startTime: line.time,
      endTime: line.end ?? fallbackEnd,
      words: (line.words ?? []).map((word) => ({
        text: word.text,
        startTime: word.start,
        endTime: word.end,
      })),
    };
  });
}

export function buildFoliaTheme(
  accentCss: string,
  accent2Css: string,
  palette: CoverPalette | null,
): Theme {
  const accent = accentCss || '#f2a7c3';
  return {
    accentColor: accent,
    secondaryColor: accent2Css || accent,
    primaryColor: palette?.primary || '#e8ecf4',
    backgroundColor: '#05070a',
  };
}
