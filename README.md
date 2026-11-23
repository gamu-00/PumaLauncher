# PumaLauncher

<img width="1376" height="768" alt="Puma_Launcher_app" src="https://github.com/user-attachments/assets/fac2cba2-39ba-49c1-94f2-cab7480796a9" />




**An automotive-first Android launcher designed for landscape head units and AI Boxes.**

Forked from [OpenLauncher](https://github.com/dw2lam/openlauncher), **PumaLauncher** reimagines the Android home screen for widescreen in-car infotainment systems (tested on 8" landscape head units and CarlinKit S2p AI Boxes, scaling across 6" to 16" displays). It combines split-screen pure-GNSS navigation, an ad-free hybrid YouTube player, contextual live weather, and a unified media station inside a distraction-reduced cockpit UI.

> **Current Release:** `v0.1.3` — Signed release build introducing a 23-module Google Maps-style navigator architecture (Day/Night slate-blue nocturnal map & cyan route styling), expanded turn-by-turn banners with road shields, persistent Home/Work/Recent search overlay, real-time GPS speedometer with Valhalla speed limit signs, swipe-up trip drawer with 3D view toggle and 60 fps 2.5D driving avatars, and full 9-language localization across UI and routing APIs.

---

<img width="806" height="539" alt="Screenshot 2026-11-21 004223" src="https://github.com/user-attachments/assets/7b8083a2-ba82-457a-b1bb-fcec5e72eb56" />




## Key Features

- **Automotive Landscape Cockpit UI & Responsive Scaling (6" to 16")**
  - Purpose-built for wide horizontal car displays with adaptive Day/Night theming (`CockpitColors`): a high-contrast Dark Cockpit theme and a **Daylight Anti-Glare** theme palette (`Cool Slate / Stone Grey`) to minimize cabin reflections.
  - **Responsive UI Scaling:** 6 discrete scaling presets (`65%`, `80%`, `100%`, `120%`, `140%`, `160%`) paired with zoom-independent physical screen breakpoints, larger dock/drawer icons, and overflow-protected turn-by-turn banners.
  - Full localization across **9 languages** via a compact automotive dropdown selector: English, Italian, German, French, Spanish, Portuguese, Polish (`Polski`), Russian (`Русский`), and Ukrainian (`Українська`), with automatic audio/stream language prioritization across Media Station and YouTube.
- **Integrated Split-Screen Navigation (MapLibre + Pure GNSS)**
  - Persistent interactive map rendered side-by-side with the media card—no app switching required—with active turn-by-turn routing persisting seamlessly across Home, App Library, and Settings.
  - **Modular Google Maps-Style HUD:** 23-module split-screen navigator with Day/Night themes (slate-blue nocturnal map with bright cyan route polylines), enlarged turn-by-turn banners with road shields (`SS`, `SP`, `A`, `B`), and **Fastest / Alternative / Scenic** 3-route preview.
  - **Search, Speed Limits & 2.5D Avatars:** Persistent **Home** / **Work** shortcuts and **Recent** (last 10) search overlay, unified live GPS speedometer + Valhalla speed limit sign (`edge.speed_limit` with `/trace_attributes` fallback), swipe-up trip drawer with on-the-fly **Add stop**, **3D view** toggle, **Gemini / Voice Assistant** shortcut, and 60 fps 2.5D driving avatars (*Classic Arrow*, *Blue Puma ST*, *White Sport*).
  - **Pure GNSS Engine (`FilteredGpsLocationEngine`) & 9-Language APIs:** Dedicated `GPS_PROVIDER` pipeline with physical outlier rejection, 2D constant-velocity Kalman smoothing, standstill anti-jitter, soft snap-to-route projection, and `AppLanguageManager`-synced Valhalla, Photon, and Nominatim localization.
- **Hybrid Ad-Free YouTube Player (Up to 720p)**
  - Browse subscriptions, search, and keep your Google account signed in through an isolated `WebView` with persistent cookies and OAuth/2FA popup support.
  - Selecting any video or Short intercepts SPA navigation and hands the stream over to **NewPipeExtractor** + **Media3 ExoPlayer**, eliminating web-injected ads and background visibility pauses.
- **Unified Media Station & Contextual Live Weather**
  - **Contextual Live Weather (`CockpitWeatherCard`):** Powered by Open-Meteo (`is_day` + Material You Canvas particle effects for sun, moon/stars, clouds, fog, rain, thunderstorms, and snow), automatically triggered after 15s of active video or music playback with a quick-return control and a configurable media pause timeout (`1–30 min`).
  - **Movies & Series Hub:** Cinemeta metadata catalog with Stremio addon stream resolution, language-aware result sorting, and source/quality badges.
  - **Now Playing Widget:** System-wide media controller (`MediaNotificationListenerService`) to control external music apps (Spotify, YouTube Music, etc.) directly from the dashboard.
- **Strict Audio Focus Discipline**
  - `ExoPlayer` is instantiated on demand and immediately releases Audio Focus (`abandonAudioFocusRequest` + `player.release()`) when paused, closed, or backgrounded, preventing audio conflicts with navigation voice prompts or external radio/music apps.

<img width="808" height="540" alt="Screenshot 2026-11-21 004503" src="https://github.com/user-attachments/assets/0728e95d-4716-4e3a-a558-47c63b67d53d" />




---

## Architecture & Technical Solutions

| Challenge on Android Head Units | Solution Implemented | Why It Works |
| :--- | :--- | :--- |
| **Noisy Fused/Wi-Fi location jumps in vehicles** | **Pure GNSS Kalman Engine** (`FilteredGpsLocationEngine`) | Bypasses Wi-Fi/Cell triangulation in favor of `GPS_PROVIDER` with physical outlier rejection, 2D constant-velocity Kalman filtering, standstill suppression, and route-snapping. |
| **YouTube WebView playback breaks** (auto-pauses when split-screen loses focus, heavy JS overhead, intrusive ads) | **Hybrid WebView + Native ExoPlayer Pipeline** (`YouTubeView` → `YouTubeStreamExtractor` → `MediaPlayerView`) | The `WebView` acts purely as an authenticated catalog browser (with a sanitized User-Agent). Video URLs are extracted server-side via InnerTube/NewPipe and played natively in `ExoPlayer`, bypassing web ad injection and Page Visibility throttling. |
| **YouTube no longer serves 720p muxed streams** (`videoStreams` often caps at 360p) | **On-the-fly DASH Stream Merging** (`MergingMediaSource`) | `YouTubeStreamExtractor` pairs the highest available `<= 720p` video-only stream with the best `m4a` audio stream and merges them seamlessly in Media3. |
| **Cropped or stretched videos on wide head-unit cards** | **Dynamic Native Aspect Ratio Letterboxing** (`RESIZE_MODE_FIT` + `onVideoSizeChanged`) | The player fills the full card width against a pure `#000000` canvas while preserving native proportions—rendering both 16:9 videos and 9:16 vertical Shorts intact without edge cropping. |
| **R8 minification breaking reflection-heavy extractors** | **Dedicated Release ProGuard Rules** (`proguard-rules.pro`) | Preserves DTO serialization (`@Keep` / `@SerializedName`), `NewPipeExtractor`, `NanoJson`, Mozilla `Rhino` JS engine, `MapLibre`, and `Media3` (including the FFmpeg audio/video decoder) in signed release builds. |

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Android Views (hybrid)
- **Maps & Routing:** MapLibre Native, Photon Geocoding, Valhalla Routing
- **Weather:** Open-Meteo Forecast API (no API key required)
- **Media & Stream Extraction:** AndroidX Media3 (`ExoPlayer`, `MergingMediaSource`, FFmpeg extension), `NewPipeExtractor`
- **Networking & Serialization:** OkHttp, Gson

<img width="807" height="541" alt="Screenshot 2026-11-21 010422" src="https://github.com/user-attachments/assets/4d2ee610-0cc0-41f3-b667-1e430b0a263b" />


---

## Project Structure

```text
app/src/main/java/com/openlauncher/app/
├── MainActivity.kt                      # Host activity, physical screen metrics & contextual weather trigger
├── data/
│   ├── AppSettings.kt                   # UI scaling presets, theme/media preferences & defaults
│   ├── SettingsRepository.kt            # DataStore persistence
│   └── WeatherRepository.kt             # Open-Meteo client with 30-min / 10 km cache
├── mediastation/
│   ├── MediaStationCard.kt              # Split Movies | YouTube | Contextual Weather container
│   ├── YouTubeView.kt                   # Authenticated WebView catalog + stream handoff
│   └── ui/
│       ├── MediaPlayerView.kt           # Native ExoPlayer surface with audio focus discipline
│       ├── MediaStationViewModel.kt     # Playback, extraction & idle-reset state
│       └── MoviesHubView.kt             # Cinemeta & Stremio addon hub
├── ui/
│   ├── components/
│   │   ├── CockpitWeatherCard.kt        # Material You live Canvas particle weather card
│   │   └── Sidebar.kt                   # Responsive cockpit dock & clock/status rail
│   ├── screen/
│   │   ├── HomeScreen.kt                # Split-screen dashboard layout & physical breakpoints
│   │   ├── AutomotiveMapStyle.kt        # Day/Night automotive vector map style, road shields & 3D buildings
│   │   ├── NativeNavigatorPanel.kt      # MapLibre HUD, 3-route preview card, compass, recenter & route trail
│   │   ├── NavigationEngine.kt          # Valhalla multi-route preview, TTS guidance & active route state
│   │   ├── AppLibraryScreen.kt          # Paginated app drawer
│   │   └── SettingsScreen.kt            # Compact language dropdown, scaling presets & cockpit preferences
│   ├── theme/
│   │   ├── CockpitColors.kt             # Dark Cockpit & Daylight Anti-Glare palettes
│   │   └── Theme.kt                     # PhysicalScreenMetrics & Material 3 theme setup
│   └── widget/
│       └── NowPlayingWidget.kt          # System media controller widget
├── util/
│   ├── FilteredGpsLocationEngine.kt     # Pure GNSS LocationEngine with 2D Kalman & anti-jitter
│   └── LocationCompassManager.kt        # Compass & low-frequency GNSS day/night helper
└── viewmodel/
    └── LauncherViewModel.kt             # Launcher state, shortcuts & weather coordination
```

---

## Installation & Building from Source

### Installing the Release APK
1. Download `PumaLauncher-v0.1.3-release.apk` from the [Releases](https://github.com/gamu-00/PumaLauncher/releases) page.
2. Install it on your Android head unit or AI Box (Android 13 / API 33 recommended) and grant **Location** and **Notification Access** when prompted to enable pure-GNSS navigation and the *Now Playing* media widget.

### Compiling from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/gamu-00/PumaLauncher.git
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

## Known Limitations & Roadmap (`v0.1.3`)

- **Upstream YouTube Client Changes:** Because stream extraction depends on `NewPipeExtractor`, periodic server-side changes by YouTube will require updating the `NewPipeExtractor` version in `app/build.gradle.kts`.
- **Navigation HUD Evolution:** `v0.1.3` delivers the 23-module Google Maps-style navigator, persistent Home/Work/Recent search, waypoint stops, real-time speed limits, swipe-up 3D/avatar controls, and full 9-language UI + API localization; offline map packages are planned for `v0.2`.
- **Hardware Target:** Layout proportions and scaling presets (`65%`–`160%`) are tuned for landscape head units and automotive displays (6" to 16"); portrait phone screens are not the primary target.

---

## Credits & Acknowledgments

- Forked from [OpenLauncher](https://github.com/OpenLauncherTeam/openlauncher).
- YouTube stream extraction powered by [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
- Weather data powered by [Open-Meteo](https://open-meteo.com/).
