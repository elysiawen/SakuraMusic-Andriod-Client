/**
 * 町景（Machi）运行时的安卓外壳。
 *
 * 与绘光共用同一套 Kotlin → JS 协议：歌、主题、播放锚点三份 payload，
 * 事件回传走 AndroidNowPlaying。桌面端的 Pinia 调参、取景控制条与真实频谱
 * 在 WebView 里拿不到，这里用默认 tuning、默认视角和模拟节拍。
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
import { MachiRuntime, type MachiSongContext } from '../../engine/machi/createMachiRuntime';
import { DEFAULT_MACHI_TUNING, type MachiTuning } from '../../engine/machi/types';
import { normalizeMachiTuning } from '../../engine/machi/machiTuning';
import type { AudioBands, Line, MotionValue, Theme } from '../../engine/folia/foliaTypes';
import { DEFAULT_LYRIC_RENDER_QUALITY } from '../../engine/folia/foliaRenderQuality';

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
const audioPower = makeValue(() => live(0.2 + 0.3 * wave(700, 0)));
const audioBands: AudioBands = {
  bass: makeValue(() => live(0.2 + 0.5 * wave(380, 0))),
  lowMid: makeValue(() => live(0.15 + 0.4 * wave(510, 1))),
  mid: makeValue(() => live(0.12 + 0.35 * wave(640, 2))),
  vocal: makeValue(() => live(0.1 + 0.4 * wave(830, 3))),
  treble: makeValue(() => live(0.08 + 0.3 * wave(290, 4))),
};

/* ------------------------------ 状态与转换 ------------------------------ */

let song: SongPayload = { lines: [], seed: 'machi', title: null, artist: null };
let theme: ThemePayload = THEME_FALLBACK;

/** 设置面板推来的 tuning：归一化后补齐字段、夹取范围。 */
let tuning: MachiTuning = normalizeMachiTuning(null);

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

function buildSong(): MachiSongContext {
  return {
    seed: String(song.seed ?? 'machi'),
    lines: toLines(song),
    isInstrumental: song.lines.length === 0,
    title: song.title ?? '',
    artist: song.artist ?? '',
    album: '',
  };
}

/* ------------------------------ 运行时生命周期 ------------------------------ */

let runtime: MachiRuntime | null = null;
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
  const abort = new AbortController();
  bootAbort = abort;
  try {
    const created = await MachiRuntime.create({
      host,
      song: buildSong(),
      theme: toTheme(theme),
      tuning: applySettings(tuning, null),
      renderQuality: DEFAULT_LYRIC_RENDER_QUALITY,
      currentTime: currentTimeValue,
      audioPower,
      audioBands,
      paused: !playing,
      staticMode: false,
      showText: true,
      previewMode: false,
      transparent: false,
      fallbackIcons: ['music'],
      onContextLost: () => {
        destroyRuntime();
        void createRuntime();
      },
      signal: abort.signal,
    });
    if (token !== bootToken) {
      created.destroy();
      return;
    }
    runtime = created;
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

/** 设置面板推来的 patch：归一化后交给运行时，换场景 / 画质由它自己过渡。 */
function applyTuning(payload: unknown): void {
  tuning = normalizeMachiTuning(applySettings(DEFAULT_MACHI_TUNING, payload));
  runtime?.setTuning(tuning);
}

function installHost(): void {
  window.nowPlaying = {
    setSong(payload: unknown) {
      song = normalizeSong(asObject(payload));
      if (runtime) runtime.swapSong(buildSong());
      else if (!booting) void createRuntime();
    },
    setTheme(payload: unknown) {
      theme = normalizeTheme(asObject(payload));
      runtime?.setTheme(toTheme(theme));
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
