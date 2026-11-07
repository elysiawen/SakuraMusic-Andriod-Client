// folia useVisualizerSettingsStore 的 shim：只保留町景运行时读的两项。
// sakura 没有「动画与性能」面板，帧率固定 60，智能动感模式恒为关。
const state = {
  visualizerFrameRate: 60,
  smartBeat: { mode: 'off' as const },
};

export const useVisualizerSettingsStore = {
  getState: () => state,
  subscribe: (_listener: () => void) => () => {},
};
