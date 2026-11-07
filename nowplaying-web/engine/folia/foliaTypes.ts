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
  /** 歌曲结构标记（folia 歌词管线的产物；sakura 没有，段落切分退回时间空隙启发式）。 */
  blockIndex?: number;
  songPart?: string;
  isChorus?: boolean;
  translation?: string;
}

export type LyricRenderScale = number;

/** 歌词画布的全局画质挡位（folia「动画与性能」）。 */
export type LyricAntialias = 'auto' | 'on' | 'off';

export interface LyricRenderQuality {
  scale: LyricRenderScale;
  antialias: LyricAntialias;
}

export interface LyricRenderOverride {
  scale: 'global' | number;
  antialias: 'global' | string;
}

/** folia 的主题色板：四个颜色必有（与上游一致），其余可选。 */
export interface Theme {
  accentColor: string;
  backgroundColor: string;
  primaryColor: string;
  secondaryColor: string;
  /** 主题名 / 描述 / 提供方（町景的店面招牌会读）。 */
  name?: string;
  description?: string;
  provider?: string;
  fontStyle?: 'sans' | 'serif' | 'mono';
  fontFamily?: string;
  fontFamilyStack?: string[];
  fontWeight?: number;
  /** 动效强度（folia「动画与性能」）：绘光/町景按它缩放镜头运动；sakura 没有对应设置，恒不填 = normal。 */
  animationIntensity?: 'calm' | 'normal' | 'chaotic';
  wordColors?: { word?: string; color: string }[];
  lyricsIcons?: string[];
}

/** framer-motion MotionValue 的最小形状：读当前值、订阅变化（写值仅调试钩子用到）。 */
export interface MotionValue<T = number> {
  get(): T;
  set?(value: T): void;
  on(event: 'change', cb: (v: T) => void): () => void;
}

/** 音频分频（folia AudioBands）：绘光的光强随音乐起伏。 */
export interface AudioBands {
  bass: MotionValue<number>;
  lowMid: MotionValue<number>;
  mid: MotionValue<number>;
  vocal: MotionValue<number>;
  treble: MotionValue<number>;
  spectrum?: MotionValue<Uint8Array>;
}

export type LumiereRenderQuality = 'full' | 'balanced' | 'low';

export interface LumiereTuning {
  /** 分辨率与抗锯齿：global 跟随全局挡位。 */
  render: LyricRenderOverride;
  lightIntensity: number;
  audioResponse: number;
  fogDensity: number;
  darkField: number;
  moteAmount: number;
  bloom: number;
  textBloom: number;
  unlitOpacity: number;
  windowNeighbors: 1 | 2;
  decay: number;
  echo: number;
  fogOctaves: number;
  lineArt: boolean;
  frontBokeh: boolean;
  trails: boolean;
  hideTrails: boolean;
  seamlessTransitions: boolean;
  overlayFrame: boolean;
  textOnly: boolean;
  keywordColors: boolean;
  themeIcons: boolean;
  themeColorMix: number;
  renderQuality: LumiereRenderQuality;
}

export const DEFAULT_LUMIERE_TUNING: LumiereTuning = {
  render: { scale: 'global', antialias: 'global' },
  lightIntensity: 1,
  audioResponse: 1,
  fogDensity: 1,
  darkField: 0.75,
  moteAmount: 1,
  bloom: 1,
  textBloom: 1,
  unlitOpacity: 0.22,
  windowNeighbors: 2,
  decay: 1,
  echo: 1,
  fogOctaves: 5,
  lineArt: true,
  frontBokeh: true,
  trails: true,
  hideTrails: false,
  seamlessTransitions: true,
  overlayFrame: true,
  textOnly: false,
  keywordColors: true,
  themeIcons: true,
  themeColorMix: 0.3,
  renderQuality: 'full',
};
