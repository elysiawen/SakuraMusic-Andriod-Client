/**
 * folia 共享类型的最小 shim：只保留雾笺运行时碰到的字段。
 * sakura 的歌词只有行级时间，words 恒为空数组（逐字时间由 graphemeTiming 按行时长均摊）。
 */

export interface Word {
  text: string;
  startTime: number;
  endTime: number;
  syllables?: { text: string; startTime: number; endTime: number }[];
}

export interface Line {
  fullText: string;
  startTime: number;
  endTime: number;
  words: Word[];
  wordSegments?: string[];
  autoWordSegments?: string[];
}

export type LyricRenderScale = number;

export interface LyricRenderOverride {
  scale: 'global' | number;
  antialias: 'global' | string;
}

/** 雾笺的调色板只读这几个字段（nebbiaPalette.ts）。 */
export interface Theme {
  accentColor?: string;
  backgroundColor?: string;
  primaryColor?: string;
  secondaryColor?: string;
  wordColors?: { color: string }[];
  lyricsIcons?: string[];
}
