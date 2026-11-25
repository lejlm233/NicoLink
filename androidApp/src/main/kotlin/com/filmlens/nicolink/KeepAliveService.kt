package com.filmlens.nicolink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * 保活前台服务：常驻通知让 App 保持前台优先级，避免切后台后系统回收导致与相机的 PTP/IP 连接断开。
 * 由设置页「保活通知」开关控制启停（UiPrefs.keepAliveEnabled）。
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "nicolink_keepalive"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // API 34+ 声明了 foregroundServiceType 时必须用带 type 的重载，否则抛
        // MissingForegroundServiceTypeException。dataSync = 持续数据传输类型，部分 ROM 对
        // 此类型的后台豁免更宽松（对齐 LunaShare 的保活策略）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        acquireLocks()
    }

    /**
     * 申请 Wi-Fi 高性能锁 + CPU 部分唤醒锁。
     * 手机连的是相机热点（无 INTERNET 的 Wi-Fi），屏幕关闭后系统会让 Wi-Fi 进入省电、
     * 到相机的 TCP 链路随之被断开——这正是「挂了通知+白名单仍断连」的根因。
     * 前台服务只保进程优先级，不阻止 Wi-Fi 省电，必须额外持 WifiLock。
     */
    private fun acquireLocks() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = runCatching {
            wifiManager?.createWifiLock(lockMode, "NicoLink:CameraLink")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = runCatching {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NicoLink:CameraKeepAlive")?.apply {
                setReferenceCounted(false)
                acquire() // 服务存活期间持续持有；进程死亡时系统自动释放，不会泄漏
            }
        }.getOrNull()
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        wakeLock = null
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统杀掉后自动重启（START_STICKY），配合电池白名单维持连接
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "相机连接保活",
                NotificationManager.IMPORTANCE_LOW, // 低调常驻：不发声、不弹横幅
            ).apply {
                description = "保持 NicoLink 在后台与相机的连接"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_keepalive)
            .setContentTitle("NicoLink 正在保持相机连接")
            .setContentText("点击返回应用")
            .setOngoing(true)          // 不可手动滑动关闭
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
