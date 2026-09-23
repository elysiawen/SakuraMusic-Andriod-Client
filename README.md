# SakuraMusic-Andriod-Client

Sakura Music 的 Android 客户端。Kotlin + Jetpack Compose + Media3（ExoPlayer），
播放器活在前台服务里，界面只是它的遥控器。

- 播放：Media3 / ExoPlayer，支持 CDN 直连与网关中转，缓存后离线可播
- 多设备：同一账号下的设备互相可见、可互相遥控，也能把正在播的队列连进度一起交接过去
- 音乐库：自建歌单、收藏（「我喜欢的音乐」）、播放历史、本地下载
- 本地缓存：音频、封面、歌词与歌曲信息，容量与有效期都可在设置里调
- 音源：网易云音乐 / QQ 音乐（经自建网关）

多设备那套协议的细节（设备身份、SSE 事件、`transfer` / `release` 时序）见
[`connect-protocol.md`](connect-protocol.md)。

## 构建

```bash
./gradlew assembleRelease
```

Release 签名材料（`keystore.properties` 与 `keystore/`）**不入库**，见 `.gitignore`；
没有它们时打出来的包不签名，而不是构建失败。

网关地址默认取 `gradle.properties` 里的 `sakura.gateway.url`，
也可以在应用内「设置 → 网络」里改，或用 `-Psakura.gateway.url=...` 覆盖。
