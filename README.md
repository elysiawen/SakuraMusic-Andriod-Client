# SakuraMusic-Andriod-Client

[![Android CI](https://github.com/elysiawen/SakuraMusic-Andriod-Client/actions/workflows/ci.yml/badge.svg)](https://github.com/elysiawen/SakuraMusic-Andriod-Client/actions/workflows/ci.yml)

Sakura Music 的 Android 客户端。Kotlin + Jetpack Compose + Media3（ExoPlayer），
播放器活在前台服务里，界面只是它的遥控器。

- 播放：Media3 / ExoPlayer，支持 CDN 直连与网关中转，缓存后离线可播
- 歌词：优先用网易云 YRC / QQ 音乐 QRC 的逐字时间轴按字点亮，解析不出来时自动退回按行 LRC（含翻译 / 罗马音）
- 私人电台：首页「私人漫游」（网易云）/「沉浸刷歌」（QQ 音乐）整批入队，最后一首开始播时经
  `GET /api/radio/:platform` 自动补下一批（会话去重、空批次冷却重试、刷新后接着续）；
  跟着平台开关走，与「一起听」互斥
- 音质：7 档统一模型，档位名按当前曲目平台显示（QQ 客户端口径：标准品质 / HQ / SQ / 臻品音质 /
  臻品母带 4.0 / 臻品全景声），「当前音质」显示网关实际返回、可能降级后的档位
- 多设备：同一账号下的设备互相可见、可互相遥控，也能把正在播的队列连进度一起交接过去；
  **一起听期间**设备面板只剩「看状态 / 调音量 / 让声音切到这台」，内容控制归房间
- 一起听：和朋友共享同一个播放现场（6 位房间码 / 邀请链接），任一方都能切歌、暂停、拖进度、
  切换播放模式（顺序 / 列表循环 / 单曲循环 / 随机，**两端同步**，播完往哪走由服务端按房间模式接），
  两端镜像同一首歌的同一进度；同账号的第二台设备自动成为「旁观」，可一键「把播放转移到本机」，
  也能在设备面板里「让声音切到这台」把声音推过去（目标必须已在房间里的旁观设备）；
  会员共享按平台（网易云 / QQ）借用对方账号的会员取流，借用要**对方本人确认**，
  服务端只存授权关系、不存凭据
- 平台开关：每个平台有总开关（关掉即该平台任何功能都不参与），开启后可再按能力（搜索 / 首页推荐）逐项启用；
  配置存在 Sakura 账号下，与网页端共用同一份，换设备自动同步。播放不受影响
- 音乐库：自建歌单、收藏（「我喜欢的音乐」）、播放历史、本地下载
- 本地缓存：音频、封面、歌词与歌曲信息，容量与有效期都可在设置里调
- 音源：网易云音乐 / QQ 音乐（经自建网关）

多设备那套协议的细节（设备身份、SSE 事件、`transfer` / `release` 时序）见
[`docs/connect-protocol.md`](docs/connect-protocol.md)；
「一起听」（房间）的协议与席位 / 旁观 / 「转移到本机」语义见工作区里的
[`sakura-music/docs/together-protocol.md`](../sakura-music/docs/together-protocol.md)
（客户端实现见 `app/src/main/java/com/sakura/music/core/player/RoomSync.kt`）。

## 构建

```bash
./gradlew assembleRelease
```

播放页的 Web 效果源码在 `nowplaying-web/`，产物写到 `app/src/main/assets/nowplaying/<效果 id>/`，
**不入库**（见 `.gitignore`）。Gradle 不跑 Node，所以 clone 后要先生成一次 assets 再编译：

```bash
cd nowplaying-web
npm ci
npm run build
cd ..
./gradlew assembleDebug
```

GitHub Actions 每次构建都会自动做前两步。本地漏了这一步，`assembleDebug` / `assembleRelease`
会在 `preBuild` 的 `checkWebAssets` 处直接失败并给出提示，不会编出没有 Web 效果的包。
目录结构、共享协议和新增效果的步骤见 [`nowplaying-web/README.md`](nowplaying-web/README.md)。

Release 签名材料（`keystore.properties` 与 `keystore/`）**不入库**，见 `.gitignore`；
本地没有它们时打出来的包不签名，而不是构建失败（CI 上已配置 Secrets，产出的是签名包）。

网关地址默认取 `gradle.properties` 里的 `sakura.gateway.url`，
也可以在应用内「设置 → 网络」里改，或用 `-Psakura.gateway.url=...` 覆盖。

## 自动构建（GitHub Actions）

| Workflow | 触发 | 做什么 |
| --- | --- | --- |
| `ci.yml` | PR、手动 | `npm ci && npm run build` 生成 Web 产物 → 编译 Debug APK |
| `release.yml` | **push 到 `main` / `master`**、手动 | 同样先生成 Web 产物 → 编译 Release APK → 上传 artifact 并创建 GitHub Release |

每次 push 到主分支都会自动出一个 Release：标题 `Release YYYYMMDD`（UTC+8 日期），正文列出
版本号、构建时间、Commit。同一天多次 push 用 `release-<日期>-<短 sha>` 作为唯一 tag，
标题不变；如果只想按 tag 发版，把 `release.yml` 的 `on.push` 改回 `tags: ['v*']` 即可。

APK 在对应 Actions 运行页的 **Artifacts** 里下载，也会挂在 Release 附件上。仓库里**只有源码**，
`app/src/main/assets/nowplaying/` 已被忽略，两个 workflow 都会先现场生成再打包，并在打包前
确认 `index.html` 存在，避免编出没有 Web 效果的包。

**提交时**：把 `nowplaying-web/`（源码）和 `.github/workflows/` 加进 Git 即可，生成的 assets
不需要 `git add`，也不存在「源码改了、产物忘了重新 build」的漂移。

### Release 签名

本仓库已配置签名 Secrets，每次 push 到主分支产出的都是可直接覆盖安装的签名包，Release 正文不再
标注签名状态。本地没有 `keystore/` 与 `keystore.properties` 时打出来的包不签名，仅能自测。

要让自己 fork 出的仓库也出签名包，在 `Settings → Secrets and variables → Actions` 里添加：

| Secret | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 keystore/sakura-music-release.jks` 的输出（Git Bash 里执行） |
| `KEYSTORE_PASSWORD` | 本地 `keystore.properties` 的 `storePassword` |
| `KEY_ALIAS` | `keyAlias` |
| `KEY_PASSWORD` | `keyPassword` |

`app/build.gradle.kts` 只从 `keystore.properties` 读签名，不认 `-P` 或环境变量，所以 CI 用 Secrets
在 runner 上临时还原 jks 和 properties；两者都在 `.gitignore` 里，不会回写仓库。

手动触发 `release.yml` 时可填 `gateway_url`，用 `-Psakura.gateway.url=...` 覆盖默认网关地址
（`gradle.properties` 默认是模拟器用的 `10.0.2.2`，真机包请填真实地址）。
