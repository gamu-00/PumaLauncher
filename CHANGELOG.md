# Changelog

All notable changes to **PumaLauncher** are documented in this file.

## [v0.1.2] - 2026-11-21

### Automotive Vector Map & 3D Driving Perspective
- **High-Contrast Vector Map Style (`AutomotiveMapStyle`)**: Added dedicated Day (*Cool Slate*) and Night (*Obsidian*) MapLibre vector styles with aggressive decluttering (hiding pedestrian paths, railways, parking lots, and minor POIs), compact highway/road shields, and tone-on-tone 3D building extrusions at zoom >= 15.
- **Dynamic 3D Driving Tilt & Speed-Adaptive Zoom**: Smooth 45° camera pitch and speed-based zoom adjustments while in `FOLLOW` mode (`CameraMode.TRACKING_GPS`), returning to top-down 0° during free-look panning or route preview.

### 3-Route Preview & Valhalla Routing
- **Multi-Route Preview (`NavigationEngine`, `NativeNavigatorPanel`)**: Added interactive route preview comparing up to 3 Valhalla alternatives — **Fastest**, **Alternative**, and **Scenic** (avoiding highways/tolls and favoring hilly/scenic roads) — with toll badges and tap-on-map route selection.
- **Split-Screen Adaptive Preview Card**: Tuned `RoutePreviewCard` sizing, spacing, and typography to fit cleanly inside the 8" landscape split-screen map panel without truncated labels or overlapping buttons.

### Localization & Compact Settings UI
- **Polish, Russian & Ukrainian Support**: Added complete localized string resources (`values-pl`, `values-ru`, `values-uk`) covering navigation, media, weather, settings, and onboarding, bringing total supported languages to **9**.
- **YouTube, Media & TTS Language Priority**: Extended `YouTubeStreamExtractor`, `MediaStationRepository`, `MediaPlayerView`, and `NavigationEngine` to prioritize dubbed audio tracks, stream tags, Valhalla turn instructions, and TTS voice locale for Polish (`pl`), Russian (`ru`), and Ukrainian (`uk`).
- **Compact Language Dropdown (`SettingsScreen`)**: Replaced the multi-row language grid with a single compact, touch-friendly dropdown menu featuring flag icons, language code badges, and native names styled for both Day and Night themes.

### Build & Release
- **R8 / ProGuard & Release Signing**: Added `-keep` rules for `AutomotiveMapStyle`, `RouteOptionType`, `RoutePreviewOption`, `AppLanguageManager`, and `AppLanguageOption` in `proguard-rules.pro`, bumped to `versionCode = 3` / `versionName = "0.1.2"`.

## [v0.1.1-beta] - 2026-11-20

### Navigation & GPS Engine
- **Pure GNSS Location Engine (`FilteredGpsLocationEngine`)**: Replaced Fused/Wi-Fi location providers with a dedicated `GPS_PROVIDER` implementation, eliminating sudden ~500 m position jumps in urban and cabin environments.
- **Signal Filtering & Stabilization**: Added physical outlier rejection, a 2D constant-velocity Kalman filter, standstill jitter suppression, and soft snap-to-route projection along active polylines.
- **Native Directional Arrow**: Switched to a custom native vector directional arrow (`ic_nav_arrow`) rendered via MapLibre's `RenderMode.GPS`.
- **Free Look Camera, Compass & Smooth Recenter**: Added Free Look map panning (`CameraMode.NONE`) with a dynamic Compass control to reset bearing to north and a smooth **Recenter** button (`recenterCamera`) to resume GPS tracking (`CameraMode.TRACKING_GPS`).
- **Traveled Route Trail**: Split the active route polyline into a faded traveled trail (`traveledRoutePoints`) and an active remaining route (`remainingRoutePoints`) for clearer progress visibility.
- **Persistent Active Navigation**: Preserved active turn-by-turn navigation state seamlessly when switching between the Home dashboard, App Library, and Settings.

### Responsive UI & Scaling (6" to 16" Displays)
- **6 Discrete UI Scaling Presets**: Replaced continuous UI zoom with 6 calibrated presets (`65%`, `80%`, `100%`, `120%`, `140%`, `160%`) paired with harmonized text scaling.
- **Zoom-Independent Physical Breakpoints**: Introduced `PhysicalScreenMetrics` (`LocalPhysicalScreenMetrics`) so layout breakpoints adapt to actual display dimensions rather than the active UI zoom level.
- **Larger Dock & Drawer Icons**: Increased icon sizing and spacing readability in the `Sidebar` dock and `AppLibraryScreen` app drawer.
- **Overflow-Protected Navigation Banners**: Hardened active navigation banners against text and layout overflow across compact and scaled displays.

### Contextual Live Weather & Media Timeout
- **Live Weather Widget (`CockpitWeatherCard`)**: Added a new weather card powered by Open-Meteo (`WeatherRepository` with smart 30-minute / 10 km caching), featuring Material You atmospheric gradients and live Canvas particle animations:
  - Accurate day/night sky rendering (sun vs. moon & stars) driven by Open-Meteo's `is_day` parameter.
  - Animated weather states for clear sky, partly cloudy, cloudy, fog, rain, thunderstorm, and snow.
- **Contextual Media Trigger**: Automatically surfaces the weather card after 15 seconds of active music or video playback, complete with a quick-return button back to the Media Station.
- **Configurable Media Pause Timeout**: Added a new Settings slider allowing users to configure the media pause timeout between **1 and 30 minutes**.

### Daylight Anti-Glare Theme
- **Cool Slate / Stone Grey Palette**: Redesigned `LightCockpitPalette` with anti-glare Cool Slate and Stone Grey surfaces (`#DCE3EC` canvas, `#EDF1F7` cards/dock, `#E3E9F2` elevated surfaces) to minimize cabin glare in bright daylight while maintaining high contrast on maps, cards, and accent elements.
