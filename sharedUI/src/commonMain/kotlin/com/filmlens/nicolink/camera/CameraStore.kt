package com.filmlens.nicolink.camera

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * 相机侧持久数据：主机 GUID + 已保存的相机列表。
 *
 * GUID 必须稳定：尼康用它在 STA 下识别「同一个主机」，
 * 每次随机会让相机把 App 当成新设备。
 */
object CameraStore {
    private const val PREFS = "nicolink_camera"
    private const val KEY_GUID = "host_guid"
    private const val KEY_SAVED = "saved_cameras"
    private const val KEY_REGISTERED = "registered_serials"
    private const val KEY_DOWNLOADED_PREFIX = "downloaded_" // + 序列号 → StringSet(句柄)

    private val json = Json { ignoreUnknownKeys = true }
    private val savedSerializer = ListSerializer(SavedCamera.serializer())

    fun hostGuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_GUID, null)?.let { if (it.length == 32) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
        prefs.edit().putString(KEY_GUID, hex).apply()
        return hex
    }

    /** 该相机是否已完成首次注册（注册把主机 GUID 绑进相机的网络配置文件） */
    fun isRegistered(context: Context, serial: String): Boolean {
        if (serial.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_REGISTERED, emptySet())?.contains(serial) == true
    }

    fun markRegistered(context: Context, serial: String) {
        if (serial.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_REGISTERED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(serial)
        prefs.edit().putStringSet(KEY_REGISTERED, set).apply()
    }

    fun savedCameras(context: Context): List<SavedCamera> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SAVED, null) ?: return emptyList()
        val list = runCatching { json.decodeFromString(savedSerializer, raw) }.getOrDefault(emptyList())
        // 兜底去重：同一台相机（序列号相同，或历史无序列号但同 IP）只留最新一条
        val deduped = LinkedHashMap<String, SavedCamera>()
        for (cam in list) {
            val key = cam.serial.ifBlank { "host:${cam.host}" }
            val old = deduped[key]
            val replace = when {
                old == null -> true
                cam.serial.isNotBlank() && old.serial.isBlank() -> true
                cam.lastConnectedAt > old.lastConnectedAt -> true
                else -> false
            }
            if (replace) deduped[key] = cam
        }
        return deduped.values.sortedByDescending { it.lastConnectedAt }
    }

    fun save(context: Context, camera: SavedCamera) {
        val list = savedCameras(context).toMutableList()
        if (camera.serial.isNotBlank()) {
            // 带序列号的新记录接管同 IP 的无序列号历史条目（STA 下 IP 会变，序列号才是身份）
            list.removeAll { it.serial.isBlank() && it.host == camera.host }
        }
        val index = if (camera.serial.isNotBlank()) {
            list.indexOfFirst { it.serial == camera.serial }
        } else {
            list.indexOfFirst { it.id == camera.id && it.serial.isBlank() }
        }
        if (index >= 0) list[index] = camera else list.add(0, camera)
        persist(context, list)
    }

    fun remove(context: Context, id: String) {
        persist(context, savedCameras(context).filterNot { it.id == id })
    }

    /**
     * 累加某台相机的连接时长（优先按序列号匹配，STA 下 IP/端口会变）。
     * 返回更新后的记录，供上层同步到云端收藏。
     */
    fun addConnectedTime(context: Context, id: String, serial: String, deltaMs: Long): SavedCamera? {
        if (deltaMs <= 0) return null
        val list = savedCameras(context).toMutableList()
        val index = list.indexOfFirst {
            if (serial.isNotBlank()) it.serial == serial else it.id == id
        }
        if (index < 0) return null
        val updated = list[index].copy(totalConnectedMs = list[index].totalConnectedMs + deltaMs)
        list[index] = updated
        persist(context, list)
        return updated
    }

    /**
     * 记录某台相机的快门次数（从照片 EXIF MakerNote 解析得到，取最大值）。
     * 优先按序列号匹配（STA 下 IP 会变）；返回更新后的记录供上层同步云端。
     */
    fun setShutterCount(context: Context, id: String, serial: String, count: Long): SavedCamera? {
        if (count <= 0) return null
        val list = savedCameras(context).toMutableList()
        val index = list.indexOfFirst {
            if (serial.isNotBlank()) it.serial == serial else it.id == id
        }
        if (index < 0) return null
        val old = list[index]
        if (count <= old.shutterCount) return old
        val updated = old.copy(shutterCount = count)
        list[index] = updated
        persist(context, list)
        return updated
    }

    /** 已下载到本机的对象句柄集合（按相机序列号持久，UI 显示「已下载」角标用） */
    fun downloadedHandles(context: Context, serial: String): Set<Int> {
        if (serial.isBlank()) return emptySet()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DOWNLOADED_PREFIX + serial, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    fun markDownloaded(context: Context, serial: String, handle: Int) {
        if (serial.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DOWNLOADED_PREFIX + serial, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        set.add(handle.toString())
        prefs.edit().putStringSet(KEY_DOWNLOADED_PREFIX + serial, set).apply()
    }

    private fun persist(context: Context, list: List<SavedCamera>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED, json.encodeToString(savedSerializer, list))
            .apply()
    }
}
