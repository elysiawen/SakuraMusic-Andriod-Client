// folia lyricRenderQuality 的 shim：雾笺在 sakura 里固定按设备像素比（最高 2x）渲染。
export const resolveLyricPixelRatio = (dpr: number, scale: number): number =>
  Math.max(0.5, Math.min(2, dpr * (typeof scale === 'number' ? scale : 1)));
