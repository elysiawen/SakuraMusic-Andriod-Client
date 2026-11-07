// folia drumHits 的 shim：没有鼓点分离，恒为 null，车身颠簸退回随机（nebbiaDrops 自带）。
export const DRUM_KICK = 0;
export const DRUM_SNARE = 1;
export const DRUM_HAT = 2;

export interface DrumHits {
  times: ArrayLike<number>;
  strengths: ArrayLike<number>;
  kinds: ArrayLike<number>;
  until?: number;
}

export const getDrumHits = (_key: string | null): DrumHits | null => null;
