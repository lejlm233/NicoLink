<div align="center">

# NicoLink

**尼康相机连接 · 胶片显影 · 本地相册 —— 一体化 Android 应用**

[English](README_EN.md) · 简体中文

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.11.1-4285F4.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com)

</div>

NicoLink 是一个第三方尼康相机连接与 spektrafilm 胶片显影 Android 应用：连接相机 → 遥控拍照 → JPG/RAW 传手机 → 本机相册 → Google Photos 级预览 → spektrafilm 胶片显影 → 导出。这是一个全新的软件，不是现有项目的拼接：连接层自研，显影层复用 GPLv3 引擎（物理隔离），UI 美学沿用作者自有的 lazyfilm 设计语言。

## 技术栈

- Kotlin Multiplatform + Compose Multiplatform 1.11.1，Kotlin 2.4.0，AGP 9.2.1，Gradle 9.4.1（JDK 21）
- minSdk 24 / targetSdk 36 / compileSdk 37，仅 `arm64-v8a`
- 模块：`:sharedUI`（共用 UI/主题/导航/显影契约）、`:androidApp`（Android 入口/显影引擎桥接/原生库）
- 关键依赖：Coil 3（图片）、Zoomable 2.16.0（手势缩放）、backdrop 2.0.0 + shapes 1.2.0（液态玻璃）、Media3 / ExoPlayer 1.7.1（App 内视频播放）、kotlinx-serialization
- Maven 走阿里云镜像优先 + 官方源备用（见 `settings.gradle.kts`）

## 目录结构

```
androidApp/src/main/
  assets/spektralab/resources/     # 显影引擎资源（profiles/vulkan/光谱表，约 15MB）
  assets/camera_shelf/             # 相机陈列架大图（11 张尼康机型透明 PNG，宽 480 压缩）
  jniLibs/arm64-v8a/               # 7 个 .so（引擎 + LibRaw + jpeg/z/lcms/c++，约 15MB）
  java/app/spektralab/engine/      # GPL 引擎 JNI 门面（包名不可改，JNI 符号绑定）
  kotlin/com/filmlens/nicolink/
    MainActivity.kt                # 入口，注册 AndroidFilmBridge
    film/FilmEngine.kt             # 引擎封装：加载/渲染/导出/paramsJson
    film/AndroidFilmBridge.kt      # FilmBridge 的 Android 实现
sharedUI/src/commonMain/kotlin/com/filmlens/nicolink/
  ui/App.kt                        # 导航栈 + 路由分发
  ui/screen/Screens.kt             # 主页（本机/相机 tab + 相机预览浮层挂载）
  ui/screen/CameraScreens.kt       # 相机 Tab/连接/监看/相机大图预览（分块缩放+下载进度）
  ui/screen/Screens2.kt            # 本机预览/显影/设置
  ui/screen/AccountScreens.kt      # 用户中心：账户面板/热力图/相机陈列架
  ui/shared/GlassCommon.kt         # GlassPage 等玻璃浮层封装
  ui/shared/liquid/                # 液态玻璃控件（从 lazyfilm 复刻）
  ui/theme/                        # lazyfilm 品牌令牌/图标/主题
  film/FilmBridge.kt               # 显影桥接接口 + FilmParams（依赖注入，隔离 GPL）
  film/ParamCatalog.kt             # 显影参数目录 + tab，含 CURVE/COLOR_WHEEL/CROP/FORMAT_CARD 交互控件
  camera/CameraSession.kt          # 全局相机会话：连接/发现/列表/缩略图/下载/拍照/取景/自动传输
  camera/ptp/NikonCamera.kt        # PTP 会话：直读通道（STA）/注册/拍照三层轮询/取景
  camera/ptp/PtpIpTransport.kt     # PTP/IP 双通道传输（命令互锁 + 事件流 + 心跳探测）
  camera/NetworkBinder.kt          # 相机热点网络绑定（无 INTERNET 时 Wi-Fi 按网段匹配）
  camera/CameraStore.kt            # 主机 GUID/已保存相机/注册序列号/已下载句柄持久化
  data/PhotoRepository.kt          # App 专属目录相册读取 + 日期分组 + 调节缩略图缓存
docs/PROTOCOL.md                   # 协议调研与事实记录
```

## 构建与安装

1. 在 `local.properties` 配置 `sdk.dir=/path/to/Android/Sdk`
2. 构建 Debug APK：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :androidApp:assembleDebug --no-daemon
```

3. 输出：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

> 注意：Zoomable 依赖只在 Maven Central，需保证官方源可达。

## 显影引擎（重要）

- 引擎二进制来自上游 [SAINTTaiYi/SpektraLab-Android](https://github.com/SAINTTaiYi/SpektraLab-Android) v1.6.2m APK，未修改，许可证 **GPL-3.0-or-later**；JNI 门面 `SpektraLabEngine.java` 已公开。引擎底层源码见 [andreavolpato/spektrafilm](https://github.com/andreavolpato/spektrafilm)。
- **JNI 铁律**：引擎包名 `app.spektralab.engine` 不可改，否则 JNI 符号绑定失败。
- **递归环铁律**：液态玻璃控件只能放在 `overlay` / `topBar` 兄弟层，不能进入 `content` 层，否则触发递归测量。
- 参数传递照抄 `applyParamsTo` 的 wire 字段；预览使用 `tier="live"`。

## 合规与许可

本项目采用 **GPL-3.0-or-later** 许可证（见 [`LICENSE`](LICENSE)）。第三方组件许可：

- 显影引擎：GPL-3.0-or-later（来自 SpektraLab-Android，未修改；底层源码见 andreavolpato/spektrafilm）
- 胶片 profile：CC BY-SA 4.0
  - Spektrafilm for Android by Akshay Sharma
  - Film modeling powered by spektrafilm by Andrea Volpato
- Zoomable / Coil / Media3：Apache-2.0
- 相机连接层：自研

## 参考

- [andreavolpato/spektrafilm](https://github.com/andreavolpato/spektrafilm)
- [SAINTTaiYi/SpektraLab-Android](https://github.com/SAINTTaiYi/SpektraLab-Android)
- [JamesQiu2005/SpektraLab](https://github.com/JamesQiu2005/SpektraLab)
