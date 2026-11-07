// folia beatEffects 的 shim：只保留雾笺/町景用到的效果 id 的常量形状。
export const BEAT_EFFECT_IDS = {
  nebbiaDrizzleRide: 'nebbiaDrizzleRide',
  nebbiaAquaBeat: 'nebbiaAquaBeat',
  machiNeonPulse: 'machiNeonPulse',
  machiDrumRipple: 'machiDrumRipple',
  machiScreenFlash: 'machiScreenFlash',
} as const;

export const beatEffectPartId = (id: string, part: string): string => `${id}:${part}`;

// sakura 没有智能动感模式：效果永远不播。
export const smartBeatActive = (_settings: unknown): boolean => false;
