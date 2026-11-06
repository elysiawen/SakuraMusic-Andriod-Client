// folia beatEffects 的 shim：只保留雾笺用到的两个效果 id 的常量形状。
export const BEAT_EFFECT_IDS = {
  nebbiaDrizzleRide: 'nebbiaDrizzleRide',
  nebbiaAquaBeat: 'nebbiaAquaBeat',
} as const;

export const beatEffectPartId = (id: string, part: string): string => `${id}:${part}`;
