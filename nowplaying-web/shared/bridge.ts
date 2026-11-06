/**
 * JS → Kotlin 的事件通道。
 *
 * 桥对象由 WebView 的 addJavascriptInterface 注入，名字由宿主决定；
 * 目前雾笺沿用 `AndroidNebbia`，宿主泛化后统一成 `AndroidNowPlaying`。
 */

export type BridgeEvent = 'ready' | 'firstFrame' | 'failed';

interface AndroidBridge {
  postMessage(message: string): void;
}

export function createPost(
  channel: string,
): (type: BridgeEvent, payload?: Record<string, unknown>) => void {
  return (type, payload) => {
    try {
      const bridge = (window as unknown as Record<string, AndroidBridge | undefined>)[channel];
      bridge?.postMessage(JSON.stringify({ type, ...payload }));
    } catch {
      // 桥没注入（浏览器里调试）时忽略。
    }
  };
}
