// folia fontStacks 的 shim：雾笺用系统黑体写「手写字」，权重 600 与 sakura 其他播放页一致。
export const resolveThemeFontStack = (_theme: unknown): string =>
  '"PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif';

export const resolveThemeFontWeight = (_theme: unknown, weight: number): number => weight;
