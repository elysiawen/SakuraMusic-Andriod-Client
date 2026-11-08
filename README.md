**English** | [简体中文](README.zh-CN.md)

# SakuraMusic-Andriod-Client

[![Android CI](https://github.com/elysiawen/SakuraMusic-Andriod-Client/actions/workflows/ci.yml/badge.svg)](https://github.com/elysiawen/SakuraMusic-Andriod-Client/actions/workflows/ci.yml)

The Android client for Sakura Music. Kotlin + Jetpack Compose + Media3 (ExoPlayer);
playback lives in a foreground service and the UI is just its remote control.

- Playback: Media3 / ExoPlayer, direct CDN streaming or gateway relay, cached tracks play offline
- Lyrics: syllable-level highlighting from NetEase Cloud Music YRC / QQ Music QRC timelines,
  with automatic fallback to line-based LRC (including translations / romaji)
- Personal radio: "Private Roaming" (NetEase) / "Immersion Mix" (QQ Music) enqueue a whole batch
  from the home page; the next batch is fetched via `GET /api/radio/:platform` when the last track
  starts (session dedup, cooldown retries on empty batches, resumes after a refresh).
  Follows the per-platform toggle and is mutually exclusive with Listen Together only on the device
  holding the room seat; a spectator device can still use its own radio
- Audio quality: a unified 7-tier model; tier names follow the current track's platform
  (QQ client terms: Standard / HQ / SQ / Premium / Master 4.0 / Spatial Audio).
  "Current quality" shows what the gateway actually returned, which may be a downgraded tier
- Multiple devices: devices on the same account see and control each other, and can hand over
  the queue together with the playback position. **During Listen Together** the device panel only
  offers "view status / set volume / move audio here"; content control belongs to the room
- Listen Together: share one playback session with a friend (6-digit room code / invite link).
  Either side can change tracks, pause, seek, or switch play mode (sequential / list loop /
  single loop / shuffle, **synced on both ends**; the server decides what plays next from the room mode).
  Both ends mirror the same track at the same position; a second device on the same account
  automatically becomes a spectator and can take over playback in one tap, or push audio to itself
  from the device panel (the target must already be a spectator in the room).
  VIP sharing is per platform (NetEase / QQ) and borrows the other account's membership for streaming;
  borrowing requires **explicit confirmation from that person**, and the server stores only the
  grant relationship, never credentials
- Platform toggles: each platform has a master switch (off disables every feature of that platform)
  plus per-capability switches (search / home recommendations) when enabled.
  Config is stored under the Sakura account and shared with the web client, so it syncs across devices.
  Playback is unaffected
- Library: custom playlists, favourites ("My Favourites"), play history, local downloads
- Local cache: audio, cover art, lyrics and track metadata, with adjustable size and retention
- Sources: NetEase Cloud Music / QQ Music (via a self-hosted gateway)
- Now-playing styles: three Compose pages (Native / Classic / Immersive) plus three WebView
  visualizers — 雾窗 (Nebbia), 绘光 (Lumiere) and 町景 (Machi). The three WebView effects share
  one JS bridge, one WebView host and one glass pill control bar, each with its own settings panel
  (gear in the top bar). Preferences are stored per effect, options show or hide based on the
  current scene, and the accent follows the app theme (indigo by default). When the full-screen
  page is collapsed the whole subtree leaves the composition and the WebView is destroyed;
  backgrounding the app while it is open pauses rendering

The multi-device protocol (device identity, SSE events, `transfer` / `release` ordering) is documented
in [`docs/connect-protocol.md`](docs/connect-protocol.md); the Listen Together protocol and the
seat / spectator / "transfer to this device" semantics live in
[`sakura-music/docs/together-protocol.md`](../sakura-music/docs/together-protocol.md)
(client implementation: `app/src/main/java/com/sakura/music/core/player/RoomSync.kt`).

## Credits and Licenses

The **雾窗 (Nebbia), 绘光 (Lumiere) and 町景 (Machi)** now-playing visualizers are ported from
**[Folia](https://github.com/chthollyphile/folia-major)** (`folia-major`), a desktop music visualizer.
Many thanks to its author for the WebGL2 / PixiJS / three.js engines, the scene design, and the many
thoughtful details throughout. The pure-TS rendering engines are mirrored under
`nowplaying-web/engine/` with their original layout; only the shell was replaced with an Android
WebView host bridge, Compose controls and a settings panel.

Nebbia's stroke-order writing uses outline data from [AnimCJK](https://github.com/animCJK), whose
glyphs derive from the Arphic PL KaitiM font and are distributed under the **Arphic Public License**.
The relevant license texts are declared by `nowplaying-web/effects/*/effect.json`, shipped with the APK
(see `nowplaying-web/engine/nebbia/licenses/`); check the license terms before removing them.

## Building

```bash
./gradlew assembleRelease
```

The WebView effect sources live in `nowplaying-web/`: rendering engines are mirrored in `engine/`,
and each effect's WebView host shell lives in `effects/<effect id>/`. Bundles are written to
`app/src/main/assets/nowplaying/<effect id>/` and are **not committed** (see `.gitignore`).
Gradle does not run Node, so generate the assets once after cloning:

```bash
cd nowplaying-web
npm ci
npm run build
cd ..
./gradlew assembleDebug
```

GitHub Actions runs the first two steps on every build. If you skip them locally, `assembleDebug` /
`assembleRelease` fails early at the `checkWebAssets` task with a hint, so an APK without Web effects
is never produced. See [`nowplaying-web/README.md`](nowplaying-web/README.md) for the directory
layout, shared protocol and how to add an effect.

Release signing material (`keystore.properties` and `keystore/`) is **not committed** (see `.gitignore`);
without them a local build is unsigned rather than failing (CI has Secrets configured and produces
signed builds).

The gateway URL defaults to `sakura.gateway.url` in `gradle.properties`, and can be changed under
"Settings → Network" in the app or overridden with `-Psakura.gateway.url=...`.

## Automated Builds (GitHub Actions)

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | PRs, manual | `npm ci && npm run build` to generate Web assets → build Debug APK |
| `release.yml` | **push to `main` / `master`**, manual | Same Web asset step → build Release APK → upload artifact and create a GitHub Release |

Every push to the main branch produces a Release titled `Release YYYYMMDD` (UTC+8 date), with the
version, build time and commit in the body. Multiple pushes on the same day use
`release-<date>-<short sha>` as a unique tag while the title stays the same; to release only on tags,
change `release.yml`'s `on.push` back to `tags: ['v*']`.

Download the APK from **Artifacts** on the corresponding Actions run, or from the Release assets.
The repository contains **source only**: `app/src/main/assets/nowplaying/` is ignored, and both
workflows generate assets on the runner and verify `index.html` exists before packaging, so an APK
without Web effects is never produced.

**When committing**: just add `nowplaying-web/` (sources) and `.github/workflows/` to Git. Generated
assets never need `git add`, and there is no drift between changed sources and stale bundles.
