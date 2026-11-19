# PumaLauncher

<img width="1376" height="768" alt="Puma_Launcher_app" src="https://github.com/user-attachments/assets/fac2cba2-39ba-49c1-94f2-cab7480796a9" />




**An automotive-first Android launcher designed for landscape head units and AI Boxes.**

Forked from [OpenLauncher](https://github.com/OpenLauncherTeam/openlauncher), **PumaLauncher** reimagines the Android home screen for widescreen in-car infotainment systems (tested on 8" landscape head units and CarlinKit S2p AI Boxes). It combines split-screen navigation, an ad-free hybrid YouTube player, and a unified media station inside a distraction-reduced cockpit UI.

> **Current Release:** `v0.1-beta` — Closed beta build. Core features are functional and signed for release, while navigation HUD enhancements and UI refinements are actively in progress.

---

<img width="809" height="545" alt="Screenshot 2026-11-19 233607" src="https://github.com/user-attachments/assets/75c41293-62ac-485e-aaed-25fb8329ec01" />


## Key Features

- **Automotive Landscape Cockpit UI**
  - Purpose-built for wide horizontal car displays with adaptive Day/Night theming (`CockpitColors`) and custom vector branding.
  - Full localization across **6 languages**: English, Italian, German, French, Spanish, and Portuguese.
- **Integrated Split-Screen Navigation (MapLibre)**
  - Persistent interactive map rendered side-by-side with the media card—no app switching required.
  - Address and POI search powered by **Photon** geocoding, paired with turn-by-turn routing via **Valhalla**.
- **Hybrid Ad-Free YouTube Player (Up to 720p)**
  - Browse subscriptions, search, and keep your Google account signed in through an isolated `WebView` with persistent cookies and OAuth/2FA popup support.
  - Selecting any video or Short intercepts SPA navigation and hands the stream over to **NewPipeExtractor** + **Media3 ExoPlayer**, eliminating web-injected ads and background visibility pauses.
- **Unified Media Station**
  - **Movies & Series Hub:** Cinemeta metadata catalog with Stremio addon stream resolution, language-aware result sorting, and source/quality badges.
  - **Now Playing Widget:** System-wide media controller (`MediaNotificationListenerService`) to control external music apps (Spotify, YouTube Music, etc.) directly from the dashboard.
- **Strict Audio Focus Discipline**
  - `ExoPlayer` is instantiated on demand and immediately releases Audio Focus (`abandonAudioFocusRequest` + `player.release()`) when paused, closed, or backgrounded, preventing audio conflicts with navigation voice prompts or external radio/music apps.

<img width="809" height="542" alt="Screenshot 2026-11-19 233943" src="https://github.com/user-attachments/assets/c33e584f-72ae-4fe0-b9cc-53e0e63b73be" />


---

## Architecture & Technical Solutions

| Challenge on Android Head Units | Solution Implemented | Why It Works |
| :--- | :--- | :--- |
| **YouTube WebView playback breaks** (auto-pauses when split-screen loses focus, heavy JS overhead, intrusive ads) | **Hybrid WebView + Native ExoPlayer Pipeline** (`YouTubeView` → `YouTubeStreamExtractor` → `MediaPlayerView`) | The `WebView` acts purely as an authenticated catalog browser (with a sanitized User-Agent). Video URLs are extracted server-side via InnerTube/NewPipe and played natively in `ExoPlayer`, bypassing web ad injection and Page Visibility throttling. |
| **YouTube no longer serves 720p muxed streams** (`videoStreams` often caps at 360p) | **On-the-fly DASH Stream Merging** (`MergingMediaSource`) | `YouTubeStreamExtractor` pairs the highest available `<= 720p` video-only stream with the best `m4a` audio stream and merges them seamlessly in Media3. |
| **Cropped or stretched videos on wide head-unit cards** | **Dynamic Native Aspect Ratio Letterboxing** (`RESIZE_MODE_FIT` + `onVideoSizeChanged`) | The player fills the full card width against a pure `#000000` canvas while preserving native proportions—rendering both 16:9 videos and 9:16 vertical Shorts intact without edge cropping. |
| **R8 minification breaking reflection-heavy extractors** | **Dedicated Release ProGuard Rules** (`proguard-rules.pro`) | Preserves DTO serialization (`@Keep` / `@SerializedName`), `NewPipeExtractor`, `NanoJson`, Mozilla `Rhino` JS engine, `MapLibre`, and `Media3` (including the FFmpeg audio/video decoder) in signed release builds. |

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Android Views (hybrid)
- **Maps & Routing:** MapLibre Native, Photon Geocoding, Valhalla Routing
- **Media & Stream Extraction:** AndroidX Media3 (`ExoPlayer`, `MergingMediaSource`, FFmpeg extension), `NewPipeExtractor`
- **Networking & Serialization:** OkHttp, Gson

<img width="810" height="541" alt="Screenshot 2026-11-19 234019" src="https://github.com/user-attachments/assets/2ed9c107-4e9f-4ec5-8282-9e45c869a9dd" />

---

## Installation & Building from Source

### Installing the Beta APK (Testers)
1. Download `PumaLauncher-v0.1-beta-release.apk` from the [Releases](https://github.com/gamu-00/PumaLauncher/releases) page.
2. Install it on your Android head unit or AI Box (Android 13 / API 33 recommended) and grant **Notification Access** when prompted to enable the *Now Playing* media widget.

### Compiling from Source
1. Clone the repository:
   ```bash
   git clone [https://github.com/gamu-00/PumaLauncher.git](https://github.com/gamu-00/PumaLauncher.git)
   cd PumaLauncher
   ```
2. For signed **Release** builds (`assembleRelease`), create a `keystore.properties` file in the project root (ignored by Git):
   ```properties
   storeFile=path/to/your-release-keystore.jks
   storePassword=your_store_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
3. Build the APK via Gradle:
   ```bash
   ./gradlew assembleRelease
   ```
   *(Alternatively, run `./gradlew assembleDebug` to build a debug APK without a custom signing keystore.)*

---

## Known Limitations & Roadmap (`v0.1-beta`)

- **Upstream YouTube Client Changes:** Because stream extraction depends on `NewPipeExtractor`, periodic server-side changes by YouTube will require updating the `NewPipeExtractor` version in `app/build.gradle.kts`.
- **Navigation HUD Evolution:** The current MapLibre module handles search and route rendering; a dedicated automotive turn-by-turn HUD and voice guidance overhaul is planned for `v0.2`.
- **Hardware Target:** Layout proportions are tuned primarily for landscape head units (8"+); portrait phone screens are not the primary target.

---

## Credits & Acknowledgments

- Forked from [OpenLauncher](https://github.com/OpenLauncherTeam/openlauncher).
- YouTube stream extraction powered by [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
