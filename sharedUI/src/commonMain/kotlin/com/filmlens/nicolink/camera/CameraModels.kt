package com.filmlens.nicolink.camera

/** 快门分数（分子/分母；B门为 -1/-1） */
data class ShutterValue(val numerator: Int, val denominator: Int)

/** 拍摄参数（P0 监看页参数条只读显示；字段为已格式化好的字符串，null 表示相机未返回） */
data class ExposureInfo(
    val aperture: String? = null,      // f/2.8
    val shutter: String? = null,       // 1/250 或 BULB
    val iso: String? = null,           // 100 / AUTO
    val whiteBalance: String? = null,  // 日光 / AUTO
    val program: String? = null,       // M/P/A/S
    val exposureBias: String? = null,  // +0.3
)

/** 实时取景直方图（256 桶亮度，供 UI 绘制；峰值用于归一化） */
data class HistogramData(val bins: IntArray, val peak: Int) {
    companion object {
        val EMPTY = HistogramData(IntArray(256), 1)
    }
}

/** 水平仪原始 u32（相机传感器，编码待标定；-1=无效） */
data class LevelRaw(
    val angleLevel: Int? = null,  // 0xD067
    val pitching: Int? = null,    // 0xD07D
    val yawing: Int? = null,      // 0xD07E
)

/** 发现到的一台相机（mDNS 或子网扫描） */
data class DiscoveredCamera(
    val host: String,
    val port: Int = 15740,
    /** mDNS 名称 / 相机名，扫描时可能为空 */
    val name: String = "",
    /** 来源：mdns / scan / manual */
    val via: String = "scan",
)

/** GetDeviceInfo 解析结果（只保留 UI 用得上的字段） */
data class CameraDeviceInfo(
    val manufacturer: String,
    val model: String,
    val firmware: String,
    val serial: String,
    val operations: Set<Int>,
    val properties: Set<Int>,
    val events: Set<Int>,
) {
    /**
     * 受限（等待首次注册）状态：DeviceInfo 只开放配对相关指令，
     * 连标志性的 GetStorageIDs 都没有。此时除注册外的一切操作都会被拒。
     */
    val restricted: Boolean
        get() = GET_STORAGE_IDS !in operations

    fun supports(opcode: Int): Boolean = opcode in operations

    private companion object {
        const val GET_STORAGE_IDS = 0x1004
    }
}

/** 相机存储卡上的一张照片 */
data class CameraObject(
    val handle: Int,
    val storageId: Int,
    val filename: String,
    /** 字节数；个别机型 ObjectInfo 给 0，下载时再查 GetObjectSize */
    val size: Long,
    val width: Int,
    val height: Int,
    val formatCode: Int,
    val captureDate: String,
) {
    val isRaw: Boolean
        get() {
            val n = filename.lowercase()
            return RAW_SUFFIXES.any { n.endsWith(it) }
        }

    val isJpeg: Boolean
        get() {
            val n = filename.lowercase()
            return n.endsWith(".jpg") || n.endsWith(".jpeg")
        }

    /** 视频（P2 录像产物，落 App 目录但不进显影队列） */
    val isVideo: Boolean
        get() {
            val n = filename.lowercase()
            return n.endsWith(".mov") || n.endsWith(".mp4")
        }

    /** 下载落盘的 mime */
    val mimeType: String
        get() {
            val n = filename.lowercase()
            return when {
                n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
                n.endsWith(".heif") || n.endsWith(".hif") -> "image/heif"
                n.endsWith(".nef") -> "image/x-nikon-nef"
                n.endsWith(".nrw") -> "image/x-nikon-nrw"
                n.endsWith(".dng") -> "image/x-adobe-dng"
                n.endsWith(".tif") || n.endsWith(".tiff") -> "image/tiff"
                n.endsWith(".png") -> "image/png"
                n.endsWith(".mov") -> "video/quicktime"
                n.endsWith(".mp4") -> "video/mp4"
                else -> "application/octet-stream"
            }
        }

    private companion object {
        val RAW_SUFFIXES = listOf(
            ".nef", ".nrw", ".dng", ".arw", ".cr2", ".cr3", ".raf",
            ".orf", ".rw2", ".pef", ".srw", ".tif", ".tiff",
        )
    }
}

/** 已保存的相机（STA 下 IP 可能变化，用序列号识别「是不是上次那台」） */
@kotlinx.serialization.Serializable
data class SavedCamera(
    val id: String,
    val name: String,
    val host: String,
    val serial: String = "",
    val lastConnectedAt: Long = 0L,
    /** 累计连接时长（毫秒），每次断开时累加 */
    val totalConnectedMs: Long = 0L,
    /** 快门次数（相机返回时记录，暂无实机时为 0） */
    val shutterCount: Long = 0L,
)

/**
 * 从相机名拆出「品牌 / 型号」。
 * - "Nikon Z6II" / "尼康 Z30" → ("Nikon", "Z6II" / "Z30")
 * - 相机直接返回机型（如 "Z 30" / "Z6II"）时品牌固定 Nikon，型号原样保留
 */
fun splitCameraName(name: String): Pair<String, String> {
    val trimmed = name.trim()
    val nikonPrefix = Regex("^(nikon|尼康)[\\s\\-]+", RegexOption.IGNORE_CASE)
    val match = nikonPrefix.find(trimmed)
    if (match != null) {
        val model = trimmed.removeRange(match.range).trim()
        if (model.isNotBlank()) return "Nikon" to model
    }
    return "Nikon" to trimmed
}

/** 累计毫秒数格式化为「X小时」/「X分」，陈列架空间有限只显示到最大单位 */
fun formatConnectedDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val minutes = (ms / 60_000).coerceAtLeast(1) // 不足 1 分钟按 1 分显示
    val hours = minutes / 60
    return if (hours > 0) "${hours}小时" else "${minutes}分"
}

/** 下载/连接进度 */
data class TransferProgress(
    val filename: String,
    val done: Long,
    val total: Long,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** 相机会话状态（Compose 观察） */
data class CameraState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val model: String = "",
    val serial: String = "",
    val firmware: String = "",
    val host: String = "",
    val battery: Int? = null,
    val freeBytes: Long? = null,
    /** 本机是否已注册进相机的网络配置文件（首次注册后为 true） */
    val registered: Boolean = false,

    /** 相机处于受限（等待首次注册）状态，仅开放配对指令 */
    val restricted: Boolean = false,

    val status: String = "未连接",
) {
    /** 顶栏/卡片用的一行摘要 */
    val summary: String
        get() = when {
            connecting -> "连接中…"
            connected -> listOfNotNull(
                model.ifBlank { "尼康相机" },
                battery?.let { "电量 $it%" },
                host.ifBlank { null },
            ).joinToString(" · ")
            else -> "未连接"
        }
}
