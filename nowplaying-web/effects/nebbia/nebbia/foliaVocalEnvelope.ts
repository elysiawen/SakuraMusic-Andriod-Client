// folia vocalEnvelope 的 shim：sakura 没有人声分离管线，人声轨包络恒为 null，
// 雾笺的雨点退回实时频段的能量（nebbiaHits.ts 的 VocalEnergy 路径）。
export const VOCAL_FPS = 50;

export interface VocalEnvelope {
  fps: number;
  db: Float32Array;
  top: number;
  onsets: Uint32Array;
  from?: number;
}

export const getVocalEnvelope = (_key: string | null): VocalEnvelope | null => null;
