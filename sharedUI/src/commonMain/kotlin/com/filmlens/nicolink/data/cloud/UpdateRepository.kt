package com.filmlens.nicolink.data.cloud

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

// ============================================================
// UpdateRepository —— 检查更新：查云端最新版本（biz/checkUpdate，免登录）
// 主页静默检查按 last_check 做 7 天节流；设置页点版本号手动检查不受节流限制。
// 备用通道：主域连续失败 2 次（约隔 7 天即第 14 天那次）后，改用 ApiClient.FALLBACK_BASE_URL
// 再试一次，主域恢复即清零回正常——保证任一域名失效时用户仍能收到更新提示。
// 存储用独立 SharedPreferences（nicolink_update），与 lazyfilm 完全隔离。
// ============================================================
class UpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("nicolink_update", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    /** 本地 versionCode（与云端 latest.versionCode 比对） */
    @Suppress("DEPRECATION")
    fun currentVersionCode(): Int {
        val info = packageInfo() ?: return 0
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }

    /** 本地 versionName（设置页展示用） */
    fun currentVersionName(): String = packageInfo()?.versionName.orEmpty()

    /** 距上次静默检查是否已满 7 天 */
    fun dueForAutoCheck(): Boolean {
        val last = prefs.getLong("last_check", 0L)
        return System.currentTimeMillis() - last >= 7L * 24 * 60 * 60 * 1000
    }

    fun markChecked() {
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
    }

    /** 查询最新版本；网络/解析失败返回 null（调用方决定提示与否） */
    suspend fun fetchLatest(): AppLatestVersion? {
        // 主域名
        val primary = requestLatest(ApiClient.BASE_URL)
        if (primary != null) {
            if (prefs.getInt("check_fail", 0) != 0) {
                prefs.edit().putInt("check_fail", 0).apply()
            }
            return primary
        }
        // 主域失败：连续满 2 次后走备用域再试一次
        val fails = prefs.getInt("check_fail", 0) + 1
        prefs.edit().putInt("check_fail", fails).apply()
        if (fails >= 2) return requestLatest(ApiClient.FALLBACK_BASE_URL)
        return null
    }

    private suspend fun requestLatest(baseUrl: String): AppLatestVersion? = runCatching {
        ApiClient.post(context, "biz/checkUpdate", "{}", baseUrl = baseUrl)
            .getOrThrow()
            .let { ApiClient.json.decodeFromString<CheckUpdateResponse>(it) }
    }.getOrNull()?.takeIf { it.errCode == 0 }?.latest
}