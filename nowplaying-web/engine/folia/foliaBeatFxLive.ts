import type { BeatFxEvent } from './foliaBeatClock';

// folia beatFxLive 的 shim：智能动感模式在 sakura 不存在，全部回答「没开」，
// 雾笺走自己的默认路径（雨点跟频段能量、车随机颠）。
export const isBeatFxReady = (): boolean => false;

export const isBeatEffectPlaying = (_id: string): boolean => false;

export const getBeatEffectStrength = (_id: string): number => 0;

export const getBeatFxHeard = (): { key: string | null; time: number | null } => ({
  key: null,
  time: null,
});

export const getBeatFxEvents = (): BeatFxEvent[] => [];

export const getBeatFxTrackScale = (): number => 1;
