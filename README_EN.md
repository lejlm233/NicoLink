<div align="center">

# NicoLink

**Nikon camera connection · Film development · Local gallery — an all-in-one Android app**

简体中文 · [English](README_EN.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.11.1-4285F4.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com)

</div>

NicoLink is a third-party Android app for connecting Nikon cameras and developing photos with spektrafilm: connect camera → remote shooting → JPG/RAW transfer to phone → local gallery → Google Photos-class preview → spektrafilm film development → export. This is a brand-new piece of software, not a patchwork of existing projects: the connection layer is written from scratch, the development layer reuses a GPLv3 engine (physically isolated), and the UI aesthetics follow the author's own lazyfilm design language.

## Tech Stack

- Kotlin Multiplatform + Compose Multiplatform 1.11.1, Kotlin 2.4.0, AGP 9.2.1, Gradle 9.4.1 (JDK 21)
- minSdk 24 / targetSdk 36 / compileSdk 37, `arm64-v8a` only
- Modules: `:sharedUI` (shared UI/theme/navigation/development contract), `:androidApp` (Android entry/engine bridge/native libs)
- Key dependencies: Coil 3 (images), Zoomable 2.16.0 (gesture zoom), backdrop 2.0.0 + shapes 1.2.0 (liquid glass), Media3 / ExoPlayer 1.7.1 (in-app video playback), kotlinx-serialization
- Maven mirrors: Aliyun first + official fallback (see `settings.gradle.kts`)

## Project Structure

```
androidApp/src/main/
  assets/spektralab/resources/     # Engine resources (profiles/vulkan/spectral tables, ~15MB)
  assets/camera_shelf/             # Camera shelf images (11 transparent Nikon PNGs, 480px compressed)
  jniLibs/arm64-v8a/               # 7 .so files (engine + LibRaw + jpeg/z/lcms/c++, ~15MB)
  java/app/spektralab/engine/      # GPL engine JNI facade (package name immutable, JNI symbol binding)
  kotlin/com/filmlens/nicolink/
    MainActivity.kt                # Entry, registers AndroidFilmBridge
    film/FilmEngine.kt             # Engine wrapper: load/render/export/paramsJson
    film/AndroidFilmBridge.kt      # Android implementation of FilmBridge
sharedUI/src/commonMain/kotlin/com/filmlens/nicolink/
  ui/App.kt                        # Navigation stack + route dispatch
  ui/screen/Screens.kt             # Home (local/camera tabs + camera preview overlay mount)
  ui/screen/CameraScreens.kt       # Camera tab/connection/monitor/camera large preview (tiled zoom + download progress)
  ui/screen/Screens2.kt            # Local preview/development/settings
  ui/screen/AccountScreens.kt      # User center: account panel/heatmap/camera shelf
  ui/shared/GlassCommon.kt         # GlassPage and other glass overlay wrappers
  ui/shared/liquid/                # Liquid glass widgets (ported from lazyfilm)
  ui/theme/                        # lazyfilm brand tokens/icons/theme
  film/FilmBridge.kt               # Development bridge interface + FilmParams (DI, isolates GPL)
  film/ParamCatalog.kt             # Development parameter catalog + tabs, with CURVE/COLOR_WHEEL/CROP/FORMAT_CARD widgets
  camera/CameraSession.kt          # Global camera session: connect/discover/list/thumbnail/download/shoot/live view/auto transfer
  camera/ptp/NikonCamera.kt        # PTP session: direct-read channel (STA)/register/3-layer shooting poll/live view
  camera/ptp/PtpIpTransport.kt     # PTP/IP dual-channel transport (command interlock + event stream + heartbeat)
  camera/NetworkBinder.kt          # Camera hotspot network binding (Wi-Fi subnet matching without INTERNET)
  camera/CameraStore.kt            # Host GUID/saved cameras/registration serial/downloaded handles persistence
  data/PhotoRepository.kt          # App-private directory gallery reading + date grouping + adjusted thumbnail cache
docs/PROTOCOL.md                   # Protocol research and fact records
```

## Build & Install

1. Configure `sdk.dir=/path/to/Android/Sdk` in `local.properties`
2. Build the debug APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :androidApp:assembleDebug --no-daemon
```

3. Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

> Note: the Zoomable dependency is only on Maven Central; make sure the official repository is reachable.

## Development Engine (Important)

- Engine binaries come from upstream [SAINTTaiYi/SpektraLab-Android](https://github.com/SAINTTaiYi/SpektraLab-Android) v1.6.2m APK, unmodified, licensed **GPL-3.0-or-later**; the JNI facade `SpektraLabEngine.java` is public. Underlying engine source: [andreavolpato/spektrafilm](https://github.com/andreavolpato/spektrafilm).
- **JNI rule**: the engine package `app.spektralab.engine` must not be renamed, or JNI symbol binding fails.
- **Recursion rule**: liquid glass widgets may only live in `overlay` / `topBar` sibling layers, never in the `content` layer, or recursive measurement occurs.
- Parameter passing copies the wire fields of `applyParamsTo`; preview uses `tier="live"`.

## License & Compliance

This project is licensed under **GPL-3.0-or-later** (see [`LICENSE`](LICENSE)). Third-party components:

- Development engine: GPL-3.0-or-later (from SpektraLab-Android, unmodified; underlying source at andreavolpato/spektrafilm)
- Film profiles: CC BY-SA 4.0
  - Spektrafilm for Android by Akshay Sharma
  - Film modeling powered by spektrafilm by Andrea Volpato
- Zoomable / Coil / Media3: Apache-2.0
- Camera connection layer: written from scratch

## References

- [andreavolpato/spektrafilm](https://github.com/andreavolpato/spektrafilm)
- [SAINTTaiYi/SpektraLab-Android](https://github.com/SAINTTaiYi/SpektraLab-Android)
- [JamesQiu2005/SpektraLab](https://github.com/JamesQiu2005/SpektraLab)
