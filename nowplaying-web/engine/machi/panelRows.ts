// panelRows 里町景相机条用到的两个格式化函数（React 行组件不移植）。
export const percent = (value: number) => `${Math.round(value * 100)}%`;
export const times = (value: number) => `${value.toFixed(2)}x`;
