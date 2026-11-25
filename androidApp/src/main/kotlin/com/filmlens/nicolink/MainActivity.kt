package com.filmlens.nicolink

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.film.AndroidFilmBridge
import com.filmlens.nicolink.film.AutoDeveloper
import com.filmlens.nicolink.ui.App
import com.filmlens.nicolink.ui.KeepAliveController
import com.filmlens.nicolink.ui.UiPrefs
import com.filmlens.nicolink.ui.theme.ThemeMode
import com.filmlens.nicolink.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    // Android 13+ 通知权限（保活通知需要）
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户拒绝也不影响服务运行：前台服务通知系统仍会显示 */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // 回前台：更新前后台状态 + 已连接则主动 probe 验证、已断开则重连。
        // 解决「回前台时还连着→跳过重连→过一会被心跳判断→无人重连」的问题。
        CameraSession.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        // 退后台：标记状态，后台断开不再立即重连，等回前台统一处理
        CameraSession.onAppPaused()
    }

    /**
     * 高刷：让本窗口跟随设备最高刷新率（90/120/144Hz），避免系统「智能帧率/ARR」
     * 因 View 默认投 Normal(60Hz) 票而把 App 锁在 60。
     * - preferredRefreshRate：窗口级偏好，作用于窗口内所有 Surface（API 30+ 生效）
     * Compose 侧另有 Modifier.preferredFrameRate(FrameRateCategory.High) 投高刷票。
     */
    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val mode = window.windowManager.defaultDisplay.supportedModes
                .maxByOrNull { it.refreshRate }
            val fps = mode?.refreshRate ?: 0f
            if (fps > 0f) {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = fps
                }
            }
        } catch (_: Exception) {
            // 部分 ROM 可能拒绝，失败不影响 App 正常运行
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 高刷：声明本窗口偏好设备支持的最高刷新率，避免系统「智能帧率」默认把 App 锁在 60Hz。
        requestHighRefreshRate()
        // 创建 GPL 引擎桥接并注入 Compose（引擎在 DevelopScreen 首次渲染时才懒加载）
        val filmBridge = AndroidFilmBridge(applicationContext)
        // 连接层就绪（M2）：恢复已保存相机、准备主机 GUID
        CameraSession.init(applicationContext)
        UiPrefs.init(applicationContext)
        // 注入保活服务真实实现（commonMain 通过 KeepAliveController 调用）
        KeepAliveController.start = { KeepAliveService.start(this) }
        KeepAliveController.stop = { KeepAliveService.stop(this) }
        // 保活通知：默认开启，前台服务维持相机连接；同时请求 Android 13+ 通知权限
        if (UiPrefs.keepAliveEnabled) KeepAliveService.start(this)
        requestNotificationPermissionIfNeeded()
        // 自动显影：照片落盘后按设置应用预设
        CameraSession.autoDevelopHook = { path, name ->
            AutoDeveloper.onPhotoSaved(applicationContext, filmBridge, path, name)
        }
        val themeMode = ThemePreferences.getMode(this)
        setContent {
            App(
                filmBridge = filmBridge,
                initialThemeMode = themeMode,
                onThemeChanged = { ThemePreferences.setMode(this@MainActivity, it) },
                onStatusBarChanged = { isDark ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                },
                onExitApp = { finish() },
            )
        }
    }
}
