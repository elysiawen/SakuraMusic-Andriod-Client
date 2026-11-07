/**
 * 绘光（Lumiere）运行时的安卓外壳。
 *
 * 桌面端由 Vue 外壳负责喂歌、主题、调参与真实音频频谱；安卓这边只有 Kotlin
 * 推来的三份 payload（protocol.ts），所以这里做三件事：
 *   - 把行级歌词编成绘光的整首演出程序（纯函数，不碰 Pixi）
 *   - 用播放锚点 + performance.now 造一个逐帧的 currentTime
 *   - 拿不到宿主音频，用和雾窗一样的模拟节拍给 AudioBands，光效照常呼吸
 *
 * 宿主接口与雾窗保持一致，Kotlin 侧可以只换一个 assets 目录就换效果。
 */

import { createPost } from '../../shared/bridge';
import {
  applySettings,
  asObject,
  normalizeSong,
  normalizeTheme,
  THEME_FALLBACK,
  type SongPayload,
  type ThemePayload,
} from '../../shared/protocol';
import {
  LumierePixiRuntime,
  type LumiereSongContext,
  type LumiereSongMetadata,
} from '../../engine/lumiere/createLumierePixiRuntime';
import { compileLumiereProgram } from '../../engine/lumiere/lumiereProgram';
import {
  DEFAULT_LUMIERE_TUNING,
  type AudioBands,
  type Line,
  type LumiereTuning,
  type MotionValue,
  type Theme,
} from '../../engine/folia/foliaTypes';

interface EffectHost {
  setSong(payload: unknown): void;
  setTheme(payload: unknown): void;
  setPlayback(payload: unknown): void;
  setSettings(payload: unknown): void;
  requestFrame(): void;
  reload(): void;
  destroy(): void;
}

declare global {
  interface Window {
    nowPlaying?: EffectHost;
  }
}

const STAGE_ID = 'stage';
const post = createPost('AndroidNowPlaying');

/* ------------------------------ 播放时钟 ------------------------------ */

let playing = false;
let anchorMs = 0;
let anchorAt = performance.now();

function currentTime(): number {
  return (anchorMs + (playing ? performance.now() - anchorAt : 0)) / 1000;
}

/* ------------------------------ 模拟音频 ------------------------------ */

const makeValue = (get: () => number): MotionValue<number> => ({ get, on: () => () => {} });
const wave = (period: number, phase: number): number =>
  (Math.sin(performance.now() / period + phase) + 1) * 0.5;
const live = (value: number): number => (playing ? value : 0);

const currentTimeValue = makeValue(currentTime);
const audioPower = makeValue(() => live(0.15 + 0.25 * wave(720, 0)));
const audioBands: AudioBands = {
  bass: makeValue(() => live(20 + 55 * wave(380, 0))),
  lowMid: makeValue(() => live(15 + 40 * wave(510, 1))),
  mid: makeValue(() => live(12 + 35 * wave(640, 2))),
  vocal: makeValue(() => live(10 + 40 * wave(830, 3))),
  treble: makeValue(() => live(8 + 30 * wave(290, 4))),
};

/* ------------------------------ 状态与转换 ------------------------------ */

let song: SongPayload = { lines: [], seed: 'lumiere', title: null, artist: null };
let theme: ThemePayload = THEME_FALLBACK;
/** 设置面板推来的 tuning：以默认值为底，只覆盖用户改过的字段。 */
let tuning: LumiereTuning = applySettings(DEFAULT_LUMIERE_TUNING, null);

function toTheme(payload: ThemePayload): Theme {
  return {
    accentColor: payload.accentColor,
    secondaryColor: payload.secondaryColor,
    primaryColor: payload.primaryColor,
    backgroundColor: payload.backgroundColor,
  };
}

function toLines(payload: SongPayload): Line[] {
  return payload.lines.map((line) => ({
    fullText: line.fullText,
    startTime: line.startTime,
    endTime: line.endTime,
    words: line.words.map((word) => ({
      text: word.text,
      startTime: word.startTime,
      endTime: word.endTime,
    })),
  }));
}

/** 协议里没有总时长，用最后一行的结束时刻近似；纯音乐交给编译器的 480s 缺省。 */
function durationOf(payload: SongPayload): number | undefined {
  const last = payload.lines[payload.lines.length - 1];
  return last ? Math.max(1, last.endTime) : undefined;
}

function buildSongContext(): LumiereSongContext {
  return {
    seed: String(song.seed ?? 'lumiere'),
    program: compileLumiereProgram(toLines(song), song.seed ?? 'lumiere', {}, {
      duration: durationOf(song),
      seamless: tuning.seamlessTransitions,
    }),
    theme: toTheme(theme),
  };
}

function buildMetadata(): LumiereSongMetadata {
  return { title: song.title, artist: song.artist, album: null };
}

/* ------------------------------ 运行时生命周期 ------------------------------ */

let runtime: LumierePixiRuntime | null = null;
let booting = false;
let bootToken = 0;
let bootAbort: AbortController | null = null;

function destroyRuntime(): void {
  bootToken += 1;
  bootAbort?.abort();
  bootAbort = null;
  runtime?.destroy();
  runtime = null;
  booting = false;
}

async function createRuntime(): Promise<void> {
  const host = document.getElementById(STAGE_ID) as HTMLDivElement | null;
  if (!host) {
    post('failed', { message: 'stage element missing' });
    return;
  }
  if (runtime || booting) return;
  booting = true;
  const token = ++bootToken;
  const controller = new AbortController();
  bootAbort = controller;
  try {
    const instance = await LumierePixiRuntime.create({
      host,
      song: buildSongContext(),
      tuning: applySettings(tuning, null),
      currentTime: currentTimeValue,
      audioPower,
      audioBands,
      staticMode: false,
      showText: true,
      paused: !playing,
      metadata: buildMetadata(),
      renderScale: 1,
      antialias: false,
      signal: controller.signal,
    });
    if (token !== bootToken) {
      instance.destroy();
      return;
    }
    runtime = instance;
    booting = false;
    post('ready');
    post('firstFrame');
  } catch (error) {
    if (token !== bootToken) return;
    booting = false;
    post('failed', { message: String(error) });
  }
}

/* ------------------------------ 宿主接口 ------------------------------ */

/** 设置面板推来的 patch：轨迹过渡要重编演出程序，其余热更。 */
function applyTuning(payload: unknown): void {
  const previous = tuning;
  tuning = applySettings(DEFAULT_LUMIERE_TUNING, payload);
  runtime?.setTuning(tuning);
  if (runtime && previous.seamlessTransitions !== tuning.seamlessTransitions) {
    void runtime.swapSong(buildSongContext());
  }
}

function installHost(): void {
  window.nowPlaying = {
    setSong(payload: unknown) {
      song = normalizeSong(asObject(payload));
      if (runtime) void runtime.swapSong(buildSongContext());
      else if (!booting) void createRuntime();
    },
    setTheme(payload: unknown) {
      theme = normalizeTheme(asObject(payload));
      if (runtime) void runtime.swapSong(buildSongContext());
    },
    setPlayback(payload: unknown) {
      const source = asObject(payload) as { playing?: boolean; positionMs?: number } | null;
      playing = Boolean(source?.playing);
      anchorMs = Math.max(0, Number(source?.positionMs) || 0);
      anchorAt = performance.now();
      runtime?.setPaused(!playing);
    },
    setSettings(payload: unknown) {
      applyTuning(payload);
    },
    requestFrame() {
      runtime?.renderOnce();
    },
    reload() {
      destroyRuntime();
      void createRuntime();
    },
    destroy() {
      destroyRuntime();
    },
  };
}

installHost();
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', createRuntime, { once: true });
} else {
  void createRuntime();
}
