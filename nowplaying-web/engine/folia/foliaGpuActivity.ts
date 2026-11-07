// folia services/analysis/gpuActivity 的 shim：sakura 没有 GPU 音频分析，
// 町景的「分析刚刚跑过」时间戳永远不会被刷新。
export const isGpuAnalysisActive = (_now: number): boolean => false;
