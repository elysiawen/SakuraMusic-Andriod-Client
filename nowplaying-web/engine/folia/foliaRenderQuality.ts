import type { LyricRenderQuality } from './foliaTypes';

// folia lyricRenderQuality 的 shim：雾笺在 sakura 里固定按设备像素比（最高 2x）渲染。
export const resolveLyricPixelRatio = (dpr: number, scale: number): number =>
  Math.max(0.5, Math.min(2, dpr * (typeof scale === 'number' ? scale : 1)));

/** 默认挡位：分辨率不加不减，抗锯齿按样式自己的默认。 */
export const DEFAULT_LYRIC_RENDER_QUALITY: LyricRenderQuality = { scale: 1, antialias: 'auto' };

/** 超采样后的渲染倍率上限（町景 engine/quality 用它）。 */
export const MAX_LYRIC_PIXEL_RATIO = 3;
