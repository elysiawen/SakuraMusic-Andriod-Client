# SakuraMusic-Andriod-Client

Sakura Music 的 Android 客户端。Kotlin + Jetpack Compose + Media3（ExoPlayer），
播放器活在前台服务里，界面只是它的遥控器。

- 播放：Media3 / ExoPlayer，支持 CDN 直连与网关中转，缓存后离线可播
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

Release 签名材料（`keystore.properties` 与 `keystore/`）**不入库**，见 `.gitignore`；
没有它们时打出来的包不签名，而不是构建失败。

网关地址默认取 `gradle.properties` 里的 `sakura.gateway.url`，
也可以在应用内「设置 → 网络」里改，或用 `-Psakura.gateway.url=...` 覆盖。
