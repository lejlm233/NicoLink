package com.filmlens.nicolink.data.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * NicoLink 热力图数据管理器（GitHub 贡献图式按天计数，非地图/GPS）
 *
 * 按天记录「拍照 + 导入」次数，持久化到 NicoLink/heatmap.json，
 * 输出 39 周 × 7 天网格（列=周，行=星期，0=周一，最右=当前周）。
 * 未登录时纯本地显示；登录后同步到 nicolink_heatmap 做跨设备聚合。
 */
class HeatmapManager(private val context: android.content.Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val file: File
        get() = File(context.filesDir, "NicoLink/heatmap.json")

    /** { "yyyy-MM-dd" -> 当日计数 } */
    private val counts = mutableMapOf<String, Int>()

    /** 未同步到云端的增量（同步成功后以云端全量覆盖并清空） */
    private val pendingCounts = mutableMapOf<String, Int>()

    /** Compose 可观察的 39×7 网格，驱动 UI 重组 */
    var grid: List<List<Int>> by mutableStateOf(emptyList())
        private set

    // 磁盘读写全部离开主线程；counts/pendingCounts 访问收敛到 lock
    private val lock = Any()
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val initialLoad = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        // 同步读盘（文件很小）：确保构造完成时 grid 已带真实数据，
        // 避免后台线程写 mutableStateOf 未被 Compose 观察导致「重启后热力图为空」。
        loadFromDisk()
        rebuildGrid()
        initialLoad.complete(Unit)
    }

    /** 为今天增加计数（一次拍照或一张导入） */
    fun incrementToday(count: Int = 1) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        synchronized(lock) {
            counts[today] = (counts[today] ?: 0) + count
            pendingCounts[today] = (pendingCounts[today] ?: 0) + count
            rebuildGrid()
        }
        saveToDiskAsync()
    }

    /**
     * 云端同步（登录后）：推送未同步增量 → 以云端全量覆盖本地（含其他设备）。
     * 未登录或失败静默跳过，增量保留待下次。
     */
    suspend fun syncCloud(repo: AccountRepository) {
        try {
            initialLoad.await()
            if (!repo.isLoggedIn) return
            val pending = synchronized(lock) { pendingCounts.toMap() }
            val res = repo.syncHeatmap(pending) ?: return
            synchronized(lock) {
                counts.clear()
                counts.putAll(res.days)
                pendingCounts.clear()
                rebuildGrid()
            }
            saveToDiskAsync()
        } catch (_: Exception) { /* 离线/未配置时静默 */ }
    }

    /** 重新从磁盘加载 */
    fun refresh() {
        ioExecutor.execute {
            loadFromDisk()
            synchronized(lock) { rebuildGrid() }
        }
    }

    // ── 网格构建 ──

    private fun buildGrid(counts: Map<String, Int>): List<List<Int>> {
        val totalWeeks = 39
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.add(Calendar.WEEK_OF_YEAR, -(totalWeeks - 1))
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val result = MutableList(totalWeeks) { MutableList(7) { 0 } }
        for (w in 0 until totalWeeks) {
            for (d in 0 until 7) {
                val dateStr = sdf.format(cal.time)
                result[w][d] = countToLevel(counts[dateStr] ?: 0)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return result
    }

    /** 将当日计数映射为 0-4 等级 */
    private fun countToLevel(count: Int): Int = when {
        count <= 0 -> 0
        count == 1 -> 1
        count <= 3 -> 2   // 2~3 张
        count <= 7 -> 3   // 4~7 张
        else -> 4         // 8+ 张
    }

    // ── 持久化 ──

    private fun loadFromDisk() {
        synchronized(lock) {
            counts.clear()
            pendingCounts.clear()
            try {
                if (file.exists()) {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        val data = json.decodeFromString<HeatmapData>(text)
                        counts.putAll(data.dailyCounts)
                        pendingCounts.putAll(data.pendingCounts)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveToDiskAsync() {
        val data = synchronized(lock) {
            HeatmapData(dailyCounts = counts.toMap(), pendingCounts = pendingCounts.toMap())
        }
        ioExecutor.execute {
            try {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(data))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun rebuildGrid() {
        grid = buildGrid(counts)
    }
}

@Serializable
private data class HeatmapData(
    val dailyCounts: Map<String, Int> = emptyMap(),
    val pendingCounts: Map<String, Int> = emptyMap(),
)
