# 播放页 Web 效果

播放页里由 `WebView` 承载的视觉效果都放在这个工程里。渲染引擎是桌面端纯 TS 实现的镜像，
统一放在 `engine/`；每个效果在 `effects/<id>/` 只放一层 Android WebView 宿主外壳。
`build.mjs` 遍历它们，把打包结果写到 Android 的 assets：

```
app/src/main/assets/nowplaying/<id>/
  index.html          ← shared/index.template.html 渲染
  <id>.js             ← esbuild bundle
  ...effect.json 里 assets / assetDirs 声明的静态资源
```

> 产物**不入库**（`.gitignore` 忽略 `app/src/main/assets/nowplaying/`）。
> GitHub Actions 每次构建前会先执行 `npm run build`；本地 clone 后也要跑一次，
> 否则构建会在 `preBuild` 的 `checkWebAssets` 处直接失败，不会编出缺 Web 效果的 APK。

## 目录

```
nowplaying-web/
  build.mjs                    # 遍历 effects/*，统一打包 + 生成入口页
  shared/
    protocol.ts                # 安卓↔Web 线格式、缺字段兜底、applySettings 合并
    bridge.ts                  # JS→Kotlin 事件回传（ready/firstFrame/failed）
    index.template.html        # 通用入口页，__TITLE__ / __ID__ 由清单填充
  engine/                      # 桌面端纯 TS 引擎镜像，保持原有相对 import
    folia/ pixi/ shared/       # 跨效果共用的工具与类型
    nebbia/ lumiere/ machi/    # 各效果的 runtime、shader、tuning 与静态资源
  effects/
    nebbia/ lumiere/ machi/    # 每个效果一个目录，只放 Android 宿主外壳
      effect.json              # id / title / entry / 静态资源清单
      main.ts                  # 实现宿主接口，按 id 接上 engine/ 里的 runtime
```

三个效果共用一个桥名 `AndroidNowPlaying` 与全局对象 `window.nowPlaying`；
Kotlin 侧也只有一份 `NowPlayingWebView.kt` 和一份 `WebEffectNowPlaying.kt`，
`effectId` 决定加载哪个引擎。

## 构建

```bash
cd nowplaying-web
npm ci
npm run build
```

改完 Web 源码后重新 `npm run build` 即可。GitHub Actions 在每个构建前都会执行这一步，
产物不入库，因此不会出现「产物与源码脱节」的漂移。

## 新增一个效果

1. 在 `effects/` 下建目录，目录名就是 `id`（同时作为 assets 子目录名和 `<id>.js` 文件名）；
   渲染引擎按桌面端的层级镜像到 `engine/<id>/`，`effects/<id>/` 只放宿主外壳。
2. 放 `effect.json`：`id` 必须与目录名一致，`entry` 默认 `main.ts`；
   运行时要按相对路径读的文件写进 `assets`（按文件名复制到产物根目录），
   整目录复制的写进 `assetDirs`（按目录名复制）。资源可以指向 `../../engine/...`，
   输出仍落在 `assets/nowplaying/<id>/`。
3. `main.ts` 从 `../../shared/` 引入 `protocol.ts` 与 `bridge.ts`，实现宿主接口：

   | 宿主方法 | 入参 | 说明 |
   | --- | --- | --- |
   | `setSong(payload)` | `protocol.ts` 的线格式 | 歌词行，时间轴是毫秒 |
   | `setTheme(payload)` | 四个十六进制颜色 | 封面取色已在 Kotlin 侧算好 |
   | `setPlayback(payload)` | `{ playing, positionMs }` | 只在采样时校准，帧内自行外推 |
   | `setSettings(payload)` | 设置面板的 JSON patch | `applySettings()` 合并默认 tuning 后推给 runtime |
   | `requestFrame()` / `reload()` / `destroy()` | — | 生命周期 |
   | `post('ready' / 'firstFrame' / 'failed', ...)` | — | 回传给 Kotlin |

4. Kotlin 侧登记：在 `NowPlayingStyle` 加一项；需要设置面板时新增一份
   `XxxSettingsSpecs.kt` 并挂到 `specsFor()`（有设置项时顶栏自动出现齿轮）。
   WebView 宿主与样式外壳已经统一，`NowPlayingWebView.kt` 会按 `effectId` 加载
   `https://appassets.androidplatform.net/assets/nowplaying/<id>/index.html`，不用再复制一份宿主。
5. `npm run build` 生成 assets（产物不入库，CI 会现场生成），提交源码即可。

授权文本（如 `nebbiaRaindrop.LICENSE.txt`、`licenses/`）由 `effect.json` 声明，
会随 APK 分发，删除前请确认许可要求。

## 致谢

三种可视化的渲染引擎移植自 **[Folia](https://github.com/chthollyphile/folia-major)**
（`folia-major`）桌面音乐可视化项目，感谢原作者提供的 WebGL2 / PixiJS / three.js 引擎、
场景设计与大量细致的巧思。移植保留了渲染代码原貌，只把外壳换成 Android WebView 宿主桥、
Compose 控制条与设置面板。
