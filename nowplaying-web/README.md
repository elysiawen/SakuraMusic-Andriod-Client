# 播放页 Web 效果

播放页里由 `WebView` 承载的视觉效果都放在这个工程里。每个效果是一个独立的
`effects/<id>/` 目录，`build.mjs` 会遍历它们，把打包结果写到 Android 的 assets：

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
    protocol.ts                # 安卓↔Web 线格式、毫秒→秒、缺字段兜底
    bridge.ts                  # JS→Kotlin 事件回传（ready/firstFrame/failed）
    index.template.html        # 通用入口页，__TITLE__ / __ID__ 由清单填充
  effects/
    nebbia/                    # 一个效果 = 一个目录
      effect.json              # id / title / entry / 静态资源清单
      main.ts                  # 效果入口，实现宿主接口
      nebbia/                  # 该效果自己的渲染 runtime 与数据
```

## 构建

```bash
cd nowplaying-web
npm ci
npm run build
```

改完 Web 源码后重新 `npm run build` 即可。GitHub Actions 在每个构建前都会执行这一步，
产物不入库，因此不会出现「产物与源码脱节」的漂移。

## 新增一个效果

1. 在 `effects/` 下建目录，目录名就是 `id`（同时作为 assets 子目录名和 `<id>.js` 文件名）。
2. 放 `effect.json`：`id` 必须与目录名一致，`entry` 默认 `main.ts`；
   运行时要按 `import.meta.url` 相对路径读的文件写进 `assets`（按文件名复制到产物根目录），
   整目录复制的写进 `assetDirs`（按目录名复制）。
3. `main.ts` 从 `../../shared/` 引入 `protocol.ts` 与 `bridge.ts`，实现宿主接口：

   | 宿主方法 | 入参 | 说明 |
   | --- | --- | --- |
   | `setSong(payload)` | `protocol.ts` 的线格式 | 歌词行，时间轴是毫秒 |
   | `setTheme(payload)` | 四个十六进制颜色 | 封面取色已在 Kotlin 侧算好 |
   | `setPlayback(payload)` | `{ playing, positionMs }` | 只在采样时校准，帧内自行外推 |
   | `requestFrame()` / `reload()` / `destroy()` | — | 生命周期 |
   | `post('ready' / 'firstFrame' / 'failed', ...)` | — | 回传给 Kotlin |

4. Kotlin 侧登记：在 `NowPlayingStyle` 加一项，并在样式分发处按 `id` 加载
   `https://appassets.androidplatform.net/assets/nowplaying/<id>/index.html`
   （当前 `NebbiaWebView.kt` 仍是写死路径，宿主泛化后可只改一处）。
5. `npm run build` 生成 assets，随代码一起提交。

授权文本（如 `nebbiaRaindrop.LICENSE.txt`、`licenses/`）由 `effect.json` 声明，
会随 APK 分发，删除前请确认许可要求。
