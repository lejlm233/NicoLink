package com.filmlens.nicolink.data

import android.content.Context
import java.io.File

/**
 * 仅测试版（Canary，包名以 .canary 结尾）使用的 mock 数据注入。
 * 正式版完全不执行——所有逻辑都包在 isCanary 判断里。
 *
 * 注入内容：
 * - 账号：假登录态（token/nickname/avatar/email），头像用内置 a01~a20
 * - 相机陈列架：全部 11 款尼康机型，随机连接时长/快门次数
 * - 热力图：近 9 个月随机按天计数
 */
object MockData {

    private const val ACCOUNT_PREFS = "nicolink_account"
    private const val UI_PREFS = "nicolink_ui"
    private const val FAVOURITES_PREFS = "nicolink_camera_favourites"

    private val NIKON_MODELS = listOf(
        "Z30", "Z50II", "Z5II", "Z5IIC", "Z6II", "Z6III", "Z7II", "Z8", "Z9", "Zf", "Zfc"
    )

    fun isCanary(context: Context): Boolean =
        context.packageName == "com.filmlens.nicolink.canary" || context.packageName.endsWith(".canary")

    /** 在 App 启动时调用（仅 canary）。幂等：已有 mock 不重复写。 */
    fun injectIfCanary(context: Context) {
        if (!isCanary(context)) return
        try {
            injectAccount(context)
            injectCameras(context)
            injectHeatmap(context)
        } catch (_: Exception) {
            // mock 失败不影响 App 正常运行
        }
    }

    /** 是否使用 mock 登录态（供 renewTokenIfNeeded 短路，避免云端 checkToken 清空假账号） */
    fun useMockAccount(context: Context): Boolean = isCanary(context)

    private fun injectAccount(context: Context) {
        val prefs = context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("token", null).isNullOrBlank()) {
            prefs.edit()
                .putString("token", "mock-canary-token")
                .putLong("token_expired", System.currentTimeMillis() + 365L * 24 * 3600 * 1000)
                .putString("uid", "mock-uid-0001")
                .putString("nickname", "样片摄影师")
                .putString("avatar", "a07")
                .putString("email", "mock@nicolink.local")
                .putBoolean("has_password", true)
                .apply()
        }
    }

    private fun injectCameras(context: Context) {
        // 保持云端同步关闭：这样陈列架走本地缓存分支，不被云端覆盖
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("camera_cloud_sync_enabled", false).apply()

        val fav = context.getSharedPreferences(FAVOURITES_PREFS, Context.MODE_PRIVATE)
        if (!fav.getString("items", null).isNullOrBlank()) return // 已注入过

        val now = System.currentTimeMillis()
        val random = java.util.Random(20261125L)
        val items = NIKON_MODELS.mapIndexed { index, model ->
            val connectMs = (random.nextInt(180) + 15) * 60_000L      // 15~194 分钟
            val shutter = random.nextInt(48_000) + 1_200L             // 1,200~49,199
            val lastAt = now - random.nextInt(240) * 3600_000L        // 最近 0~239 小时
            buildString {
                append("{")
                append("\"id\":\"mock-$model\",\"name\":\"Nikon $model\",")
                append("\"host\":\"192.168.1.${10 + index}\",")
                append("\"serial\":\"MOCK-${model.replace(" ", "")}-00$index\",")
                append("\"last_connected_at\":$lastAt,")
                append("\"brand\":\"Nikon\",\"model\":\"$model\",")
                append("\"total_connected_ms\":$connectMs,")
                append("\"shutter_count\":$shutter")
                append("}")
            }
        }.joinToString(prefix = "[", postfix = "]")

        fav.edit().putString("items", items).apply()
    }

    private fun injectHeatmap(context: Context) {
        val file = File(context.filesDir, "NicoLink/heatmap.json")
        if (file.exists()) return // 已注入过
        file.parentFile?.mkdirs()

        val random = java.util.Random(20261125L)
        val days = mutableListOf<String>()
        val cal = java.util.Calendar.getInstance()
        for (i in 0 until 270) { // 近 9 个月
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
            // 约 70% 的日子有 1~9 次，其余为 0（模拟真实使用节奏）
            val count = if (random.nextInt(100) < 70) random.nextInt(9) + 1 else 0
            days.add("\"$date\":$count")
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val json = "{\"dailyCounts\":{${days.joinToString(",")}},\"pendingCounts\":{}}"
        file.writeText(json)
    }
}
