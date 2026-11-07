/**
 * 安卓 ↔ Web 效果的线格式（共享契约层）。
 *
 * Kotlin 通过 JS 桥推 JSON；这里只做毫秒→秒换算、缺字段兜底与形状校验，
 * 不含任何具体效果的渲染逻辑。所有 Web 效果按这套 payload 实现
 * setSong / setTheme / setPlayback，宿主 Kotlin 就不必为每个效果写一份适配。
 */

export interface LyricWord {
  text: string;
  startTime: number;
  endTime: number;
}

export interface LyricLine {
  fullText: string;
  startTime: number;
  endTime: number;
  words: LyricWord[];
}

export interface SongPayload {
  lines: LyricLine[];
  seed: string | number | null;
  title: string | null;
  artist: string | null;
}

export interface ThemePayload {
  accentColor: string;
  secondaryColor: string;
  primaryColor: string;
  backgroundColor: string;
}

export interface PlaybackPayload {
  playing?: boolean;
  positionMs?: number;
}

/** 桥没推主题时的兜底色，与安卓默认靛青（深色）调色板的初值一致。 */
export const THEME_FALLBACK: ThemePayload = {
  accentColor: '#b3c0f0',
  secondaryColor: '#c4c6dc',
  primaryColor: '#b3c0f0',
  backgroundColor: '#05070a',
};

/** Kotlin 传进来可能是 JSON 字符串，也可能是已解析的对象。 */
export function asObject(payload: unknown): unknown {
  if (typeof payload !== 'string') return payload;
  try {
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

export function toSeconds(value: unknown): number {
  const ms = typeof value === 'number' && Number.isFinite(value) ? value : 0;
  return Math.max(0, ms) / 1000;
}

/**
 * Kotlin 侧是毫秒时间轴；缺行尾时按下一行的时间补，再没有就给 4 秒，
 * 和桌面端 `toFoliaLines` 的兜底一致。
 */
export function normalizeSong(payload: unknown): SongPayload {
  const source = payload as {
    lines?: Array<{
      text?: string;
      startMs?: number;
      endMs?: number | null;
      words?: Array<{ text?: string; startMs?: number; endMs?: number }>;
    }> | null;
    seed?: string | number;
    title?: string | null;
    artist?: string | null;
  } | null;
  const raw = Array.isArray(source?.lines) ? source.lines : [];
  const lines: LyricLine[] = raw.map((item, index) => {
    const start = toSeconds(item.startMs);
    const nextStart = index + 1 < raw.length ? toSeconds(raw[index + 1]?.startMs) : undefined;
    const fallbackEnd =
      nextStart === undefined ? start + 4 : Math.max(start, Math.min(nextStart, start + 8));
    const endFromPayload =
      typeof item.endMs === 'number' && item.endMs > 0 ? toSeconds(item.endMs) : null;
    const words: LyricWord[] = Array.isArray(item.words)
      ? item.words.map((word) => ({
          text: word?.text ?? '',
          startTime: toSeconds(word?.startMs),
          endTime: toSeconds(word?.endMs),
        }))
      : [];
    return {
      fullText: item?.text ?? '',
      startTime: start,
      endTime: endFromPayload ?? fallbackEnd,
      words,
    };
  });
  return {
    lines,
    seed: source?.seed ?? null,
    title: source?.title ?? null,
    artist: source?.artist ?? null,
  };
}

/** 封面取色 / 明暗主题在 Kotlin 侧已经算好，这里只做形状校验。 */
export function normalizeTheme(payload: unknown): ThemePayload {
  const source = payload as Partial<ThemePayload> | null;
  const read = (value: unknown, fallback: string): string =>
    typeof value === 'string' && /^#[0-9a-f]{6,8}$/i.test(value) ? value : fallback;
  return {
    accentColor: read(source?.accentColor, THEME_FALLBACK.accentColor),
    secondaryColor: read(source?.secondaryColor, THEME_FALLBACK.secondaryColor),
    primaryColor: read(source?.primaryColor, THEME_FALLBACK.primaryColor),
    backgroundColor: read(source?.backgroundColor, THEME_FALLBACK.backgroundColor),
  };
}

/**
 * 效果设置：Kotlin 只存「用户改过的那几项」JSON，这里以该效果的 DEFAULT_*_TUNING
 * 为底做深合并，得到一份完整 tuning。这样加字段、改默认值都只要动 TS 那边，
 * 老存档里缺的字段自动用新默认值补上，坏值由各效果自己的 normalize 兜底。
 *
 * key 支持 `quality.shadows` 这样的点路径，用来写嵌套对象；未知字段原样带进去，
 * 引擎只读认识的键，等价于忽略。
 */
export function applySettings<T extends object>(defaults: T, patch: unknown): T {
  const target: Record<string, unknown> = JSON.parse(JSON.stringify(defaults));
  const source = asObject(patch);
  if (!source || typeof source !== 'object' || Array.isArray(source)) {
    return target as unknown as T;
  }
  for (const [key, value] of Object.entries(source as Record<string, unknown>)) {
    if (value === undefined || value === null) continue;
    const parts = key.split('.');
    let node = target;
    for (let i = 0; i < parts.length - 1; i += 1) {
      const child = node[parts[i]];
      if (!child || typeof child !== 'object' || Array.isArray(child)) {
        node[parts[i]] = {};
      }
      node = node[parts[i]] as Record<string, unknown>;
    }
    node[parts[parts.length - 1]] = value;
  }
  return target as unknown as T;
}
