// folia utils/lyrics/parserCore 的 shim：町景只用到「间奏行」判定。
export const INTERLUDE_FULL_TEXT = '......';

export const isInterludeLine = (line: { fullText: string }): boolean => line.fullText === INTERLUDE_FULL_TEXT;
