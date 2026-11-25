# 调试日志与能力探测指南（给 AI / 开发者）

用户中心陈列架改造后，连接页不再直接展示调试日志与能力探测入口。调试能力集中到一个隐藏页面，方便 AI 通过 adb 取日志、定位问题。

## 入口

设置 → 关于 → **长按「软件版本号」行 5 秒**（中途松手视为普通点击，无动作），进入「调试日志」页。

## 调试页功能

| 操作 | 作用 |
| --- | --- |
| 输出日志 | 把当前全部调试日志复制到剪贴板，同时保存一份文件到手机外部存储 |
| 清空日志 | 清空内存中的日志列表（`CameraSession.logs`） |
| 能力探测 | 触发 `CameraSession.probeCapabilities()`，需先连接相机；未连接时写入一条提示日志 |

页面还展示：能力探测报告（`CameraSession.capabilitiesReport`，未探测时提示）、调试日志列表（等宽字体，实时条数）。

## 日志获取路径（AI 通过 adb 拉取）

1. **输出日志按钮生成的文件**（推荐，带时间戳、每次点击一份）：
   ```
   /sdcard/Android/data/com.filmlens.nicolink/files/debug/nicolink_debug_<yyyyMMdd_HHmmss>.log
   ```
   拉取：
   ```bash
   adb pull /sdcard/Android/data/com.filmlens.nicolink/files/debug/ ./debug_logs/
   ```
2. **实时镜像文件**（App 持续写入，超 400KB 自动截断）：
   ```
   cacheDir/nicolink_debug.log
   ```
   需 `run-as` 读取（debug 包）：
   ```bash
   adb shell run-as com.filmlens.nicolink cat cacheDir/nicolink_debug.log
   ```
3. **剪贴板**：点「输出日志」后可直接 `mobile_clipboard` 读取，适合快速查看最近日志。

## 数据来源

- 日志：`CameraSession.logs`（`mutableStateListOf`，超过 `MAX_LOG_LINES` 截断），同时经 `persistLog` 落盘到 `cacheDir/nicolink_debug.log`。
- 能力报告：`CameraSession.capabilitiesReport`，由 `probeCapabilities()` 在连接相机后生成（PTP DeviceInfo 等）。
- 调试页实现：`sharedUI/src/commonMain/kotlin/com/filmlens/nicolink/ui/screen/DebugScreen.kt`。
- 路由：`App.kt` 的 `Route.DEBUG`，由设置页关于区块版本号行长按 5 秒进入（`Screens2.kt`）。

## 相关清理

连接页（CameraScreens.kt）已移除：主页相机 Tab 的「正在寻找上次连接的相机」副标题、STA 会话说明段、连接页内嵌的「显示调试日志 / 能力探测」入口，以及「添加相机」卡片的 STA 副标题。调试统一走上面的隐藏入口。
