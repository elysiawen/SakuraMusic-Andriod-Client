/**
 * 雾窗运行时的安卓外壳。
 *
 * 渲染引擎与桌面端共用 `engine/` 下的同一份快照（nebbia/ + folia/），
 * 和绘光、町景一样；这里只留外壳，负责：
 *   - 建运行时（全屏画布）
 *   - 接收 Compose 通过 JS 桥推过来的歌 / 主题 / 播放进度
 *   - 把 ready / firstFrame / failed 回传给 Kotlin
 *
 * 线格式归一化与事件回传放在 `shared/`，播放时钟、运行时生命周期与宿主接口
 * 留在效果目录里（每个效果各有一份）。
 *
 * 进度只在收到 Kotlin 的采样时校准锚点，帧内按 performance.now 外推，
 * 保证暂停、seek、后台切回都跟得上，又不用每帧过桥。
 */

import { createPost } from '../../shared/bridge';
import { applySettings, asObject, normalizeSong, normalizeTheme, THEME_FALLBACK } from '../../shared/protocol';
import { NebbiaRuntime } from '../../engine/nebbia/createNebbiaRuntime';
import type { Theme } from '../../engine/folia/foliaTypes';
import { DEFAULT_NEBBIA_TUNING, type NebbiaSong, type NebbiaTuning } from '../../engine/nebbia/types';

interface NowPlayingHost {
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
    nowPlaying?: NowPlayingHost;
  }
}

const STAGE_ID = 'stage';
const post = createPost('AndroidNowPlaying');

/* ── 播放时钟 ───────────────────────────────────────────────────── */

let playing = false;
/** 收到采样那一刻的播放位置与时钟。 */
let anchorMs = 0;
let anchorAt = performance.now();

/** 人声能量：拿不到真实频谱，用和沉浸模式同一条模拟节拍。 */
let vocalLevel = 0;
let vocalPhase = 0;
let vocalLast = performance.now();

function currentTime(): number {
  const now = performance.now();
  return (anchorMs + (playing ? now - anchorAt : 0)) / 1000;
}

function vocal(): number {
  const now = performance.now();
  const dt = Math.min(0.05, Math.max(0, (now - vocalLast) / 1000));
  vocalLast = now;
  vocalLevel += ((playing ? 1 : 0) - vocalLevel) * 0.08;
  vocalPhase += dt * 0.96;
  return (Math.max(0, Math.sin(vocalPhase * 2.4)) ** 2 * 0.5 + 0.05) * vocalLevel;
}

/* ── 歌与主题 ───────────────────────────────────────────────────── */

let song: NebbiaSong = { lines: [], seed: 'nebbia' };
let theme: Theme = THEME_FALLBACK;
/** 设置面板推来的 tuning：以默认值为底，只覆盖用户改过的字段。 */
let tuning: NebbiaTuning = applySettings(DEFAULT_NEBBIA_TUNING, null);

/* ── 运行时 ─────────────────────────────────────────────────────── */

let runtime: NebbiaRuntime | null = null;
let firstFrameSent = false;
let restored = false;

function destroyRuntime(): void {
  runtime?.destroy();
  runtime = null;
}

function createRuntime(): void {
  destroyRuntime();
  const host = document.getElementById(STAGE_ID);
  if (!host) {
    post('failed', { message: 'stage element missing' });
    return;
  }
  try {
    runtime = new NebbiaRuntime(
      {
        host,
        seeThrough: false,
        scene: tuning.behind === 'aquarium' ? 'aquarium' : 'night',
        glass: tuning.glass,
        currentTime: { get: currentTime },
        vocal: { get: vocal },
        onFirstFrame: () => {
          if (!firstFrameSent) {
            firstFrameSent = true;
            post('firstFrame');
          }
        },
        onFailed: () => post('failed', { message: 'webgl unavailable' }),
        onRestored: () => {
          // 上下文丢了又回来：重建一个，别把旧的半死画布留着。
          if (restored) return;
          restored = true;
          createRuntime();
        },
      },
      theme,
      applySettings(tuning, null),
      1,
    );
    runtime.setSong(song);
    runtime.setPlayback(!playing, false);
    post('ready');
  } catch (error) {
    post('failed', { message: String(error) });
  }
}

/* ── 对 Kotlin 暴露的接口 ─────────────────────────────────────────── */

/** 设置面板推来的 patch：窗外 / 玻璃写进着色器，必须重建；其余热更。 */
function applyTuning(payload: unknown): void {
  const previous = tuning;
  tuning = applySettings(DEFAULT_NEBBIA_TUNING, payload);
  if (previous.behind !== tuning.behind || previous.glass !== tuning.glass) {
    firstFrameSent = false;
    restored = false;
    createRuntime();
  } else {
    runtime?.setTuning(tuning);
  }
}

function installHost(): void {
  window.nowPlaying = {
    setSong(payload: unknown) {
      const next = normalizeSong(asObject(payload));
      song = { ...next, seed: next.seed ?? 'nebbia' };
      runtime?.setSong(song);
    },
    setTheme(payload: unknown) {
      theme = normalizeTheme(asObject(payload));
      runtime?.setTheme(theme);
    },
    setPlayback(payload: unknown) {
      const source = asObject(payload) as { playing?: boolean; positionMs?: number } | null;
      playing = Boolean(source?.playing);
      anchorMs = Math.max(0, Number(source?.positionMs) || 0);
      anchorAt = performance.now();
      runtime?.setPlayback(!playing, false);
    },
    setSettings(payload: unknown) {
      applyTuning(payload);
    },
    requestFrame() {
      runtime?.requestFrame();
    },
    reload() {
      firstFrameSent = false;
      restored = false;
      createRuntime();
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
  createRuntime();
}

// Kotlin 可能在页面还没跑起来时就调用（WebView 尚未加载完），保留最后一次状态，
// 等 createRuntime 时自然会用上 song / theme / playing。
