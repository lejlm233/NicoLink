package com.filmlens.nicolink.camera.ptp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import com.filmlens.nicolink.camera.CameraDeviceInfo
import com.filmlens.nicolink.camera.CameraObject
import com.filmlens.nicolink.camera.ExposureInfo
import com.filmlens.nicolink.camera.ShutterValue

/**
 * 尼康 PTP 会话：会话打开、机型信息、相机照片列表、缩略图、分段下载、拍照、实时取景、首次注册。
 *
 * 相机每个网络配置文件按主机 GUID 绑定一个 App；未注册时连接能建链但相机停在
 * 「等待连接」向导，已绑定其他主机的配置文件则直接拒绝 Init（原因 1）。
 * 首次使用走 [registerHost] 完成注册，之后同一 GUID 免配置直连。
 */
class NikonCamera(
    private val transport: PtpIpTransport,
    private val log: (String) -> Unit,
) {
    var info: CameraDeviceInfo? = null
        private set
    var storageIds: List<Int> = emptyList()
        private set
    var applicationMode: Int = -1
        private set

    private var liveViewOp: Int? = null
    private var lastFrameLogMs = 0L
    private var accessProbed = false

    /** 直读态（STA 固件锁标准命令）：0x100A 缩略图必被拒，直接走 0x9530 */
    private var staDirectThumbs = false

    /** 最近一次非 App 态列表看到的媒体句柄（拍照轮询的「拍前基线」） */
    private var lastKnownMediaHandles: Set<Int> = emptySet()

    /** 最近一次 listObjects 是否遇到对象访问被拒（拍摄模式下尼康锁对象浏览） */
    var lastListAccessDenied = false
        private set

    /** 打开会话并读取设备信息；[fullSetup] 为 false 时只做会话+设备信息（主机注册流程用） */
    suspend fun open(fullSetup: Boolean = true): CameraDeviceInfo {
        // 随机会话号：固定 session=1 时，相机端残留的未释放会话会让新会话状态机错乱，
        // 典型表现就是对象访问 AccessDenied
        val sessionId = (1..0x7FFFFFFF).random()
        var session = transport.transaction(
            PtpOp.OPEN_SESSION,
            params = listOf(sessionId),
            transactionId = 0,
        )
        log("OpenSession(session=$sessionId) → ${PtpResp.name(session.code)}")
        if (!session.ok && session.code == PtpResp.SESSION_ALREADY_OPEN) {
            // 相机记着上一次的会话：显式清理后换新号重开
            log("检测到残留会话，CloseSession 后重开")
            transport.transaction(PtpOp.CLOSE_SESSION)
            val retryId = (1..0x7FFFFFFF).random()
            session = transport.transaction(
                PtpOp.OPEN_SESSION,
                params = listOf(retryId),
                transactionId = 0,
            )
            log("OpenSession(session=$retryId) 重试 → ${PtpResp.name(session.code)}")
        }
        if (!session.ok && session.code != PtpResp.SESSION_ALREADY_OPEN) {
            throw PtpException("OpenSession 失败：${PtpResp.name(session.code)}")
        }

        val dev = transport.transaction(PtpOp.GET_DEVICE_INFO)
        if (!dev.ok || dev.data == null) throw PtpException("GetDeviceInfo 失败：${PtpResp.name(dev.code)}")
        val parsed = parseDeviceInfo(dev.data)
        info = parsed
        log("机型：${parsed.manufacturer} ${parsed.model} / 固件 ${parsed.firmware} / 序列号 ${parsed.serial}")
        log("支持操作 ${parsed.operations.size} 个，属性 ${parsed.properties.size} 个，事件 ${parsed.events.size} 个")

        if (parsed.restricted) {
            log("相机处于受限（等待首次注册）状态：仅开放配对指令，跳过常规初始化")
        }

        if (fullSetup && !parsed.restricted) {
            // 注意：这里不进 App 控制（0x9435=1）——App 控制态下相机锁对象浏览
            // （GetObjectInfo → AccessDenied），只有取景/遥控拍照时才按需进入。

            // 厂商属性清单（Gen1/Gen2 用 0x90CA，Gen3 用 0x9439）
            val vendor = if (parsed.supports(PtpOp.NIKON_GET_VENDOR_PROP_CODES)) {
                transport.transaction(PtpOp.NIKON_GET_VENDOR_PROP_CODES)
            } else {
                transport.transaction(PtpOp.NIKON_GET_VENDOR_CODES)
            }
            if (vendor.ok && vendor.data != null) {
                val count = runCatching {
                    val r = PtpReader(vendor.data)
                    r.u16Array().size
                }.getOrDefault(-1)
                log("厂商属性清单 → ${PtpResp.name(vendor.code)}（$count 项）")
            } else {
                log("厂商属性清单 → ${PtpResp.name(vendor.code)}")
            }
        }

        return parsed
    }

    suspend fun closeSession() {
        runCatching {
            val r = transport.transaction(PtpOp.CLOSE_SESSION)
            log("CloseSession → ${PtpResp.name(r.code)}")
        }
        transport.close()
    }

    /**
     * STA 首次注册（SnapBridge 式配对，把本机 GUID 写进相机网络配置文件）：
     *
     * 1. `0x9439(9)` 进入智能设备配对模式（部分机身不需要，失败不阻塞）
     * 2. `0x952B` 读一次性配对码（相机屏幕同步显示，主机侧比对）
     * 3. **等待约 8.5s** —— 基准实现实证：必须给用户时间在相机「主机确认」界面按 OK，
     *    否则 0x935A 即使返回 OK 注册也未真正写入（症状：存储卡表现为不可用/空壳对象）
     * 4. `0x935A(0x2001)` 确认配对（失败重试 3 次，间隔 1.5s）
     * 5. `0x9439(13)` 退出配对模式
     *
     * 注册完成后调用方应直接断开 socket（不要 CloseSession），等相机重启 Wi-Fi
     * 后用同一 GUID 重连。
     *
     * 返回 null 表示成功，否则返回给 UI 显示的失败原因。
     */
    suspend fun registerHost(
        onProgress: (String) -> Unit,
        onPairingCode: (String) -> Unit,
    ): String? {
        onProgress("正在进入配对模式…")
        val enter = transport.transaction(
            PtpOp.NIKON_PAIRING_MODE,
            params = listOf(PtpOp.PAIRING_MODE_ENTER),
        )
        log("进入配对模式 0x9439(9) → ${PtpResp.name(enter.code)}")

        onProgress("正在读取配对码…")
        val codeResp = transport.transaction(
            PtpOp.NIKON_GET_PAIRING_INFO,
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        val code = parsePairingCode(codeResp.data)
        log("读取配对码 0x952B → ${PtpResp.name(codeResp.code)}" + if (code.isNotBlank()) "，码=$code" else "")
        if (code.isNotBlank()) onPairingCode(code)
        onProgress(
            if (code.isNotBlank()) {
                "请核对相机屏幕上的配对码是否为 $code，并在相机上按 OK 确认（等待约 9 秒）…"
            } else {
                "请在相机屏幕上确认连接请求（按 OK，等待约 9 秒）…"
            },
        )

        // 基准实现实证的等待窗口：用户在相机端按 OK 后注册才真正写入
        delay(HOST_CONFIRM_WAIT_MS)

        var lastCode = -1
        repeat(3) { attempt ->
            val confirm = transport.transaction(
                PtpOp.NIKON_CONFIRM_PAIRING,
                params = listOf(PtpOp.HOST_REGISTRATION_CONFIRM_OK),
            )
            lastCode = confirm.code
            log("确认配对 0x935A(0x2001) 第 ${attempt + 1}/3 次 → ${PtpResp.name(confirm.code)}")
            if (confirm.ok) {
                runCatching {
                    val leave = transport.transaction(
                        PtpOp.NIKON_PAIRING_MODE,
                        params = listOf(PtpOp.PAIRING_MODE_LEAVE),
                    )
                    log("退出配对模式 0x9439(13) → ${PtpResp.name(leave.code)}")
                }
                return null
            }
            if (attempt < 2) delay(1500)
        }
        return "相机未完成注册确认（${PtpResp.name(lastCode)}）。请盯住相机屏幕：出现确认界面时按 OK，然后重试"
    }

    /**
     * 0x952B 配对码解析：u32 位数 + 每位一字节 0-9（非 ASCII）。
     * 兼容带 16 字节头的另一种返回（u16 位数、偏移 20 起数字）。
     */
    private fun parsePairingCode(data: ByteArray?): String {
        if (data == null) return ""
        if (data.size >= 4) {
            val count = PtpCodec.readU32(data, 0)
            if (count in 1..8 && data.size >= 4 + count) {
                val digits = IntArray(count) { data[4 + it].toInt() and 0xFF }
                if (digits.all { it <= 9 }) return digits.joinToString("")
            }
        }
        if (data.size >= 18) {
            val count = PtpCodec.readU16(data, 16)
            if (count in 1..8 && data.size >= 20 + count) {
                val digits = IntArray(count) { data[20 + it].toInt() and 0xFF }
                if (digits.all { it <= 9 }) return digits.joinToString("")
            }
        }
        return ""
    }

    // ───────────────────────── 存储与照片列表 ─────────────────────────

    /** 优先用尼康厂商存储 ID（标准 GetStorageIDs 会返回空占位槽） */
    suspend fun loadStorageIds(): List<Int> {
        val out = LinkedHashSet<Int>()
        val vendor = transport.transaction(
            PtpOp.NIKON_GET_VENDOR_STORAGE_IDS,
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        val vendorIds = if (vendor.ok) parseU32Array(vendor.data) else emptyList()
        out.addAll(vendorIds)
        val standard = transport.transaction(PtpOp.GET_STORAGE_IDS, dataPhase = PtpIp.PHASE_DATA_IN)
        val stdIds = if (standard.ok) parseU32Array(standard.data) else emptyList()
        out.addAll(stdIds)
        storageIds = out.toList()
        log(
            "存储卡：厂商[${vendorIds.joinToString { "0x${it.toString(16)}" }}] " +
                "标准[${stdIds.joinToString { "0x${it.toString(16)}" }}]"
        )
        return storageIds
    }

    suspend fun objectHandles(storageId: Int): List<Int> {
        val r = transport.transaction(
            PtpOp.GET_OBJECT_HANDLES,
            params = listOf(storageId, 0, -1), // 0=全部格式，0xFFFFFFFF=全部关联
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        if (!r.ok) throw PtpException("GetObjectHandles 失败：${PtpResp.name(r.code)}")
        return parseU32Array(r.data)
    }

    /**
     * 列出相机照片（每张卡取最新 [limitPerStorage] 张）。
     *
     * 先用一个媒体句柄（高 8 位判型）试标准路 ObjectInfo：被拒说明是 STA 固件锁
     * （0x1008/0x100A/0x101B → 0x200F），此时尼康私有读命令（0x9421/0x9431/0x9530）
     * 不受锁，改走 [staDirectList] 直读通道。
     */
    suspend fun listObjects(limitPerStorage: Int = 400): List<CameraObject> {
        val storages = storageIds.ifEmpty { loadStorageIds() }
        val result = mutableListOf<CameraObject>()
        lastListAccessDenied = false
        for (storage in storages) {
            val handles = loadHandles(storage)
            if (handles.isEmpty()) {
                log("卡 0x${storage.toString(16)}：无对象句柄")
                continue
            }
            log("卡 0x${storage.toString(16)}：${handles.size} 个对象")
            val mediaHandles = handles.filter { staFormat(it) != null }
            val sample = mediaHandles.firstOrNull()
            if (sample == null) {
                log("卡 0x${storage.toString(16)}：句柄里没有媒体对象（仅目录），跳过")
                continue
            }
            val standardOk = runCatching { objectInfo(storage, sample) }.getOrNull() != null
            if (standardOk) {
                val recent = handles.takeLast(limitPerStorage)
                log("标准路 ObjectInfo 可读，读取最新 ${recent.size} 个")
                for (handle in recent) {
                    val obj = runCatching { objectInfo(storage, handle) }.getOrNull() ?: continue
                    // 图片 + 视频都保留（P2 录像产物要能在相机 Tab 浏览/下载）
                    if (obj.isVideo || obj.isRaw || obj.isJpeg || obj.mimeType.startsWith("image/")) {
                        result.add(obj)
                    }
                }
            } else {
                probeObjectAccess(storage, sample, handles)
                result += staDirectList(storage, handles, limitPerStorage)
            }
        }
        if (result.isEmpty() && lastListAccessDenied) {
            log("对象访问被拒：相机处于拍摄模式。请按 ▶ 切到回放模式后刷新")
        }
        if (result.isNotEmpty()) lastKnownMediaHandles = result.map { it.handle }.toSet()
        return result.sortedByDescending { it.captureDate }
    }

    /**
     * 对象访问诊断探针 v3：标准路被拒后，在真实媒体句柄上实测 STA 直读命令的可用边界。
     * 覆盖：0x9434 批量元数据 / 0x9421 大小 / 0x9431 区间读 / 0x9530 机身预览 / 0x9805·0x9803 文件名。
     * 每次连接只跑一轮。
     */
    private suspend fun probeObjectAccess(storage: Int, sample: Int, handles: List<Int>) {
        if (accessProbed) return
        accessProbed = true
        val hist = handles.groupingBy { (it ushr 24) and 0xFF }.eachCount().toSortedMap()
            .entries.joinToString(",") { "%02x:%d".format(it.key, it.value) }
        log("──── 对象访问探针开始（媒体样本=0x${sample.toString(16)}，句柄前缀 $hist）────")
        suspend fun tryOp(label: String, block: suspend () -> PtpResponse): PtpResponse? {
            val r = runCatching { block() }.getOrElse {
                log("探针 $label 异常：${it.message}")
                return null
            }
            log("探针 $label → ${PtpResp.name(r.code)} data=${r.data?.size ?: -1}B")
            return r
        }
        // ① 0x9434 批量元数据（handle/大小/拍摄时间）
        tryOp("MetaData(0x9434)") {
            transport.transaction(0x9434, params = listOf(storage, 0, 0), dataPhase = PtpIp.PHASE_DATA_IN)
        }?.let { r ->
            parseStaMeta(r.data)?.let { meta ->
                if (meta.isNotEmpty()) {
                    val first = meta.entries.first()
                    log("探针 0x9434 命中 ${meta.size} 条，首条 0x${first.key.toString(16)} ${first.value.first}B")
                }
            }
        }
        // ② 0x9421 64 位大小
        tryOp("ObjectSize(0x9421)") {
            transport.transaction(PtpOp.NIKON_GET_OBJECT_SIZE, params = listOf(sample), dataPhase = PtpIp.PHASE_DATA_IN)
        }?.let { r ->
            val d = r.data
            if (d != null && d.size >= 8) log("探针 ObjectSize 值：${PtpCodec.readU64(d, 0)} 字节")
        }
        // ③ 0x9431 任意区间读（64KB 文件头）
        tryOp("PartialEx64B(0x9431)") {
            transport.transaction(
                PtpOp.NIKON_GET_PARTIAL_OBJECT_EX,
                params = listOf(sample, 0, 0, 65536, 0),
                dataPhase = PtpIp.PHASE_DATA_IN,
            )
        }?.let { r ->
            val d = r.data
            if (d != null && d.size >= 4) {
                log("探针 0x9431 头 4 字节：${d.take(4).joinToString("") { "%02x".format(it) }}")
            }
        }
        // ④ 0x9530 机身现生成预览（首块）
        tryOp("PartialImage(0x9530)") {
            transport.transaction(0x9530, params = listOf(sample, 0, 0, STA_PREVIEW_CHUNK), dataPhase = PtpIp.PHASE_DATA_IN)
        }?.let { r ->
            log("探针 0x9530 预览总长参数：${r.params.getOrNull(1)}")
        }
        // ⑤ 文件名可读性（0x9805 批量 / 0x9803 单个，锁态下通常 0x200F）
        tryOp("PropList(0x9805)") {
            transport.transaction(0x9805, params = listOf(-1, 0, 0xDC07, 0, 0), dataPhase = PtpIp.PHASE_DATA_IN)
        }
        tryOp("PropValue(0x9803)") {
            transport.transaction(0x9803, params = listOf(sample, 0xDC07), dataPhase = PtpIp.PHASE_DATA_IN)
        }
        log("──── 对象访问探针结束 ────")
    }

    // ───────────────────────── STA 直读（固件锁标准对象命令时的私有命令通道） ─────────────────────────

    /**
     * 句柄高 8 位 = 对象类别（Z30 实测：0x09/0x0A=RAW、0x29/0x2A=JPEG、0x11/0x12=目录、
     * 0x59/0x5A/0x61/0x62/0x65/0x75=视频）。这里只区分参与浏览的图片类，其余返回 null 丢弃。
     */
    fun staFormat(handle: Int): String? = when ((handle ushr 24) and 0xFF) {
        0x09, 0x0A -> "nef"
        0x29, 0x2A -> "jpg"
        // 视频（P2 录像产物）：高 8 位前缀见 Z30 实测，扩展名 .mov
        0x59, 0x5A, 0x61, 0x62, 0x65, 0x75 -> "mov"
        else -> null
    }

    /** 0x9434 全卡元数据（App 态下也实时，自动传输对账用） */
    suspend fun staMeta(storage: Int): Map<Int, Pair<Long, String>> = loadStaMeta(storage)

    /** 最近一次非 App 态列表看到的媒体句柄（供外部对账去重） */
    val knownMediaHandles: Set<Int>
        get() = lastKnownMediaHandles

    /**
     * STA 直读列表：0x9434 批量元数据（缺的逐个 0x9421 补大小），
     * 文件名用句柄低 16 位当卡内文件号拼占位名（Z30 实测低 16 位与卡内文件号一致）。
     * 样本（0x9421+0x9431）任一不可用则判定直读不可用，返回空表。
     */
    private suspend fun staDirectList(storage: Int, handles: List<Int>, limitPerStorage: Int): List<CameraObject> {
        val media = handles.filter { staFormat(it) != null }
        if (media.isEmpty()) {
            log("直读：句柄里没有媒体对象（仅目录）")
            return emptyList()
        }
        val recent = media.takeLast(limitPerStorage)
        val sample = recent.last()
        val sampleSize = objectSize(sample)
        if (sampleSize == null || sampleSize <= 0L) {
            log("直读不可用：样本 0x${sample.toString(16)} 的 0x9421 大小无效（$sampleSize）")
            return emptyList()
        }
        val head = getPartialExBytes(sample, 0, 65536)
        if (head == null || head.isEmpty()) {
            log("直读不可用：样本 0x${sample.toString(16)} 的 0x9431 头读取失败")
            return emptyList()
        }
        log("STA 直读可用：样本 0x${sample.toString(16)} size=$sampleSize head=${head.size}B")
        staDirectThumbs = true
        val meta = loadStaMeta(storage)
        if (meta.isNotEmpty()) log("0x9434 元数据命中 ${meta.size} 条")
        val out = recent.map { h ->
            val ext = staFormat(h) ?: "bin"
            val m = meta[h]
            CameraObject(
                handle = h,
                storageId = storage,
                filename = "DSC_%04d.$ext".format(h and 0xFFFF),
                size = m?.first?.takeIf { it > 0 } ?: (objectSize(h) ?: 0L),
                width = 0,
                height = 0,
                formatCode = when (ext) {
                    "nef" -> 0xB103
                    "mov" -> 0xB003
                    else -> 0x3801
                },
                captureDate = m?.second ?: "",
            )
        }
        lastListAccessDenied = false
        log("直读列表：${out.size} 个对象")
        lastKnownMediaHandles = out.map { it.handle }.toSet()
        return out
    }

    /**
     * 0x9434 GetObjectsMetaData(storage,0,0) 载荷：
     * u32 版本, u32 数量, 数量 × { u32 handle, u32 size, u8 保留, u8 秒, u8 分, u8 时, u8 日, u8 月, u16 年 }
     */
    private suspend fun loadStaMeta(storage: Int): Map<Int, Pair<Long, String>> {
        val r = runCatching {
            transport.transaction(0x9434, params = listOf(storage, 0, 0), dataPhase = PtpIp.PHASE_DATA_IN)
        }.getOrNull()
        return parseStaMeta(r?.data) ?: emptyMap()
    }

    private fun parseStaMeta(data: ByteArray?): Map<Int, Pair<Long, String>>? {
        if (data == null || data.size < 8) return null
        val count = PtpCodec.readU32(data, 4)
        if (count < 0 || count > 100000 || data.size < 8 + count * 16) return null
        val out = HashMap<Int, Pair<Long, String>>(count)
        for (i in 0 until count) {
            val p = 8 + i * 16
            val handle = PtpCodec.readU32(data, p)
            val size = PtpCodec.readU32(data, p + 4).toLong() and 0xFFFFFFFFL
            val sec = data[p + 9].toInt() and 0xFF
            val min = data[p + 10].toInt() and 0xFF
            val hour = data[p + 11].toInt() and 0xFF
            val day = data[p + 12].toInt() and 0xFF
            val month = data[p + 13].toInt() and 0xFF
            val year = PtpCodec.readU16(data, p + 14)
            val time = if (year in 1970..2200 && month in 1..12 && day in 1..31 && hour < 24 && min < 60 && sec < 60) {
                "%04d%02d%02dT%02d%02d%02d".format(year, month, day, hour, min, sec)
            } else ""
            out[handle] = size to time
        }
        return out
    }

    /** 0x9431 GetPartialObjectEx：64 位偏移区间读，返回原始字节（被拒/异常返回 null） */
    private suspend fun getPartialExBytes(handle: Int, offset: Long, len: Int): ByteArray? {
        val r = transport.transaction(
            PtpOp.NIKON_GET_PARTIAL_OBJECT_EX,
            params = listOf(
                handle,
                (offset and 0xFFFFFFFFL).toInt(),
                (offset ushr 32).toInt(),
                len,
                0,
            ),
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        if (!r.ok) return null
        return r.data
    }

    /**
     * 0x9530 GetPartialImage(h, type, offset, maxBytes)：机身现生成的预览 JPEG，按块循环取，
     * 响应参数[1] 是这张预览的总长度。先试 type=0 再试 1，用于 STA 锁态下的缩略图兜底。
     */
    private suspend fun staPartialImage(handle: Int): ByteArray? {
        for (type in listOf(0, 1)) {
            val out = java.io.ByteArrayOutputStream()
            var total = -1L
            var failed = false
            while (total < 0 || out.size() < total) {
                val r = runCatching {
                    transport.transaction(
                        0x9530,
                        params = listOf(handle, type, out.size(), STA_PREVIEW_CHUNK),
                        dataPhase = PtpIp.PHASE_DATA_IN,
                    )
                }.getOrNull() ?: return null
                val chunk = r.data
                if (!r.ok || chunk == null || chunk.isEmpty()) {
                    failed = true
                    break
                }
                if (total < 0) {
                    total = r.params.getOrNull(1)?.toLong()?.and(0xFFFFFFFFL) ?: -1L
                    if (total <= 0L || total > STA_PREVIEW_MAX) {
                        failed = true
                        break
                    }
                }
                out.write(chunk)
            }
            val bytes = out.toByteArray()
            if (!failed && bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return bytes
        }
        return null
    }

    /**
     * 对象句柄查询。Z30 固件对 parent=0xFFFFFFFF 只回异常对象，
     * libgphoto2 对尼康的成熟用法是 storage=0xFFFFFFFF + parent=0（根），
     * 依次尝试并取第一个非空结果。
     */
    private suspend fun loadHandles(storage: Int): List<Int> {
        val combos = listOf(
            listOf(storage, 0, 0),      // 本卡 + 根目录
            listOf(-1, 0, 0),           // 全存储 + 根目录（libgphoto2 用法）
            listOf(storage, 0, -1),     // ISO 规范语义（部分固件支持）
        )
        for (params in combos) {
            val r = transport.transaction(
                PtpOp.GET_OBJECT_HANDLES,
                params = params,
                dataPhase = PtpIp.PHASE_DATA_IN,
            )
            if (!r.ok) {
                log("GetObjectHandles(storage=0x${params[0].toString(16)}, parent=0x${params[2].toString(16)}) → ${PtpResp.name(r.code)}")
                continue
            }
            val handles = parseU32Array(r.data)
            log("GetObjectHandles(storage=0x${params[0].toString(16)}, parent=0x${params[2].toString(16)}) → ${handles.size} 个")
            if (handles.isNotEmpty()) return handles
        }
        return emptyList()
    }

    /**
     * 删除相机内对象（PTP DeleteObject 0x100B）。
     * 注意：尼康 STA 直读态可能锁定标准命令，失败时返回错误码由 UI 提示用户。
     * @return Pair(是否成功, 结果描述)
     */
    suspend fun deleteObject(handle: Int): Pair<Boolean, String> {
        return try {
            val r = transport.transaction(PtpOp.DELETE_OBJECT, params = listOf(handle))
            val ok = r.code == PtpResp.OK
            log("DeleteObject(handle=$handle) → ${PtpResp.name(r.code)}")
            ok to PtpResp.name(r.code)
        } catch (e: Exception) {
            log("DeleteObject(handle=$handle) 异常：${e.message}")
            false to (e.message ?: "未知错误")
        }
    }

    suspend fun objectInfo(storageId: Int, handle: Int): CameraObject? {
        val r = transport.transaction(PtpOp.GET_OBJECT_INFO, params = listOf(handle))
        if (!r.ok) {
            log("GetObjectInfo(handle=$handle) → ${PtpResp.name(r.code)}")
            if (r.code == PtpResp.ACCESS_DENIED) lastListAccessDenied = true
            return null
        }
        val data = r.data
        if (data == null || data.size < 4) {
            log("GetObjectInfo(handle=$handle) 数据异常（${data?.size ?: -1} 字节）")
            return null
        }
        return try {
            val reader = PtpReader(data)
            val storage = reader.u32()
            val format = reader.u16()
            reader.u16() // protection
            val compressedSize = reader.u32().toLong() and 0xFFFFFFFFL
            reader.u16() // thumbFormat
            reader.u32() // thumbCompressedSize
            reader.u32() // thumbPixWidth
            reader.u32() // thumbPixHeight
            val width = reader.u32()
            val height = reader.u32()
            reader.u32() // imageBitDepth
            reader.u32() // parentObject
            reader.u16() // associationType
            reader.u32() // associationDesc
            reader.u32() // sequenceNumber
            val filename = reader.string()
            val captureDate = reader.string()
            CameraObject(
                handle = handle,
                storageId = if (storage != 0) storage else storageId,
                filename = filename.ifBlank { "IMG_$handle" },
                size = if (compressedSize == 0xFFFFFFFFL) 0L else compressedSize,
                width = width,
                height = height,
                formatCode = format,
                captureDate = captureDate,
            )
        } catch (e: Exception) {
            log("ObjectInfo 解析失败 handle=$handle：${e.message}")
            null
        }
    }

    /**
     * @param preferEmbedded 是否优先读文件头内嵌缩略图。仅对 RAW(NEF) 传 true——
     *   NEF 头部的第一个 SOI 就是内嵌预览，解析可靠且快；JPG 头部的第一个 SOI 是主 JPEG 起点，
     *   其后的第一个 EOI 落在 EXIF 缩略图段内部，截出的字节不是有效 JPEG（会显示失败），
     *   所以 JPG 必须传 false 走 0x9530。
     */
    suspend fun thumbnail(handle: Int, preferEmbedded: Boolean = false): ByteArray? {
        if (!staDirectThumbs) {
            val r = transport.transaction(PtpOp.GET_THUMB, params = listOf(handle), dataPhase = PtpIp.PHASE_DATA_IN)
            if (r.ok) return r.data?.takeIf { it.size > 4 }
        }
        // STA 直读态优化：RAW 先读文件头（0x9431 单次请求）解析内嵌 JPEG，
        // 避免 0x9530 全尺寸预览 + 10KB 分块循环（往返多、慢）。JPG 不走此路（见上）。
        if (preferEmbedded) {
            staEmbeddedThumb(handle)?.let { return it }
        }
        // 兜底 / JPG：0x9530 机身现生成预览
        return staPartialImage(handle)
    }

    /**
     * 读文件头前 [STA_HEAD_BYTES] 字节，抽取第一张完整的内嵌 JPEG（SOI→首个 EOI）。
     * NEF/JPG 文件头都有 EXIF 缩略图，单次 0x9431 请求即可拿到，比 0x9530 快得多。
     * @return 完整 JPEG（以 FFD8 开头、FFD9 结尾），失败返回 null 让调用方回退 0x9530。
     */
    private suspend fun staEmbeddedThumb(handle: Int): ByteArray? {
        val head = getPartialExBytes(handle, 0L, STA_HEAD_BYTES) ?: return null
        if (head.size < 4) return null
        // 找首个 SOI（FF D8 FF — 确保是 JPEG 标记而非数据巧合）
        var start = -1
        for (i in 0..head.size - 3) {
            if ((head[i].toInt() and 0xFF) == 0xFF &&
                (head[i + 1].toInt() and 0xFF) == 0xD8 &&
                (head[i + 2].toInt() and 0xFF) == 0xFF
            ) { start = i; break }
        }
        if (start < 0) return null
        // 找 SOI 之后的第一个 EOI（FF D9），确保截出的是一张完整 JPEG
        var end = -1
        for (i in start + 2..head.size - 2) {
            if ((head[i].toInt() and 0xFF) == 0xFF && (head[i + 1].toInt() and 0xFF) == 0xD9) {
                end = i + 2
                break
            }
        }
        if (end < 0) return null // 头部内嵌图被截断（极小概率），回退 0x9530
        val jpeg = head.copyOfRange(start, end)
        if (jpeg.size < MIN_EMBEDDED_THUMB) return null // 太小（<1KB）不像有效缩略图
        log("STA 内嵌缩略图命中：${jpeg.size} 字节（文件头直读，省去 0x9530 分块）")
        return jpeg
    }

    /** 64 位对象大小（ObjectInfo 里为 0 时兜底） */
    suspend fun objectSize(handle: Int): Long? {
        val r = transport.transaction(
            PtpOp.NIKON_GET_OBJECT_SIZE,
            params = listOf(handle),
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        if (!r.ok) return null
        val data = r.data
        if (data != null && data.size >= 8) return PtpCodec.readU64(data, 0)
        if (r.params.size >= 2) {
            return (r.params[0].toLong() and 0xFFFFFFFFL) or
                ((r.params[1].toLong() and 0xFFFFFFFFL) shl 32)
        }
        return null
    }

    /**
     * 分段下载（默认 4 MiB/次）。先 32 位 GetPartialObject，机型不支持再退到 Ex 64 位版本。
     */
    suspend fun download(
        obj: CameraObject,
        sink: DataSink,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        var total = obj.size
        if (total <= 0) total = objectSize(obj.handle) ?: 0L
        if (total <= 0) throw PtpException("无法确定文件大小：${obj.filename}")

        var offset = 0L
        var useEx = false
        while (offset < total) {
            val want = minOf(CHUNK_SIZE.toLong(), total - offset).toInt()
            var written = 0L
            val params = if (useEx) {
                listOf(
                    obj.handle,
                    (offset and 0xFFFFFFFFL).toInt(),
                    (offset ushr 32).toInt(),
                    want,
                    0,
                )
            } else {
                listOf(obj.handle, offset.toInt(), want)
            }
            val opcode = if (useEx) PtpOp.NIKON_GET_PARTIAL_OBJECT_EX else PtpOp.GET_PARTIAL_OBJECT
            val response = transport.transaction(
                opcode,
                params = params,
                dataPhase = PtpIp.PHASE_DATA_IN,
                sink = { buffer, start, length ->
                    sink.write(buffer, start, length)
                    written += length
                },
            )
            if (!response.ok) {
                // 0x101B 被拒：机型不支持，或 STA 固件锁（AccessDenied）——都退到 0x9431 64 位分段读
                if (!useEx && (response.code == PtpResp.OPERATION_NOT_SUPPORTED || response.code == PtpResp.ACCESS_DENIED)) {
                    log("0x101B 被拒（${PtpResp.name(response.code)}），改用 0x9431（64 位分段读）")
                    useEx = true
                    continue
                }
                throw PtpException("下载 ${obj.filename} 失败：${PtpResp.name(response.code)}")
            }
            if (written == 0L) break
            offset += written
            onProgress(offset, total)
        }
    }

    // ───────────────────────── App 控制模式 ─────────────────────────

    /** 进入 App 控制（0x9435=1）。取景/遥控拍照需要；浏览对象时必须不在此态 */
    private suspend fun enterAppMode() {
        if (applicationMode == 1) return
        val r = transport.transaction(PtpOp.NIKON_CHANGE_APPLICATION_MODE, params = listOf(1))
        applicationMode = if (r.ok) 1 else -1
        log("ChangeApplicationMode(1) → ${PtpResp.name(r.code)}")
    }

    /** 退出 App 控制（0x9435=0），还相机对象浏览能力 */
    suspend fun exitAppMode() {
        if (applicationMode != 1) return
        val r = transport.transaction(PtpOp.NIKON_CHANGE_APPLICATION_MODE, params = listOf(0))
        applicationMode = if (r.ok) 0 else applicationMode
        log("ChangeApplicationMode(0) → ${PtpResp.name(r.code)}")
    }

    // ───────────────────────── 拍照 ─────────────────────────

    /**
     * 释放快门并等新照片出现（三层轮询见 [waitForNewObject]）。
     *
     * @param keepAppMode 取景中拍照时传 true：保持 App 控制态，不退出取景；
     *                    普通拍照传 false：拍完退出 App 态，让对象浏览恢复可读。
     */
    suspend fun captureAndWait(
        storageId: Int,
        timeoutMs: Long = 25000,
        keepAppMode: Boolean = false,
    ): CameraObject? {
        enterAppMode()
        try {
            val before = captureBaseline(storageId)
            if (!tryReleaseShutter()) throw PtpException("相机拒绝了拍照指令")
            return waitForNewObject(storageId, before, timeoutMs)
        } finally {
            if (!keepAppMode) exitAppMode()
        }
    }

    /** 拍前基线：优先最近一次非 App 态列表，避免把上一张误判成新照片 */
    private suspend fun captureBaseline(storageId: Int): Set<Int> =
        if (lastKnownMediaHandles.isNotEmpty()) lastKnownMediaHandles
        else runCatching { objectHandles(storageId).toHashSet() }.getOrDefault(emptySet())

    /**
     * 三层轮询等新对象：① GetObjectHandles → ② 0x9434 全卡元数据 → ③ 预测句柄探测。
     * 录像产物（video=true）用视频句柄前缀识别，复用同一套对账逻辑。
     */
    private suspend fun waitForNewObject(
        storageId: Int,
        before: Set<Int>,
        timeoutMs: Long,
        video: Boolean = false,
    ): CameraObject? {
        fun isWanted(h: Int) = if (video) staVideo(h) != null || staFormat(h) != null else staFormat(h) != null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(600)
            // ① 常规句柄轮询；只认媒体句柄（0x11 目录段随时间漂移，会污染「新增」集合）
            val now = runCatching { objectHandles(storageId) }.getOrNull()
            if (now != null) {
                val fresh = now.firstOrNull { it !in before && isWanted(it) }
                if (fresh != null) {
                    log("相机新增对象 handle=$fresh")
                    // 相机可能先建对象再写完数据，稍等再读
                    delay(300)
                    if (!video) {
                        runCatching { objectInfo(storageId, fresh) }.getOrNull()?.let {
                            lastKnownMediaHandles = lastKnownMediaHandles + it.handle
                            return it
                        }
                    }
                    val size = runCatching { objectSize(fresh) }.getOrNull() ?: 0L
                    if (size > 0) return newDirectObject(fresh, storageId, size, video)
                }
            }
            // ② STA App 态：GetObjectHandles 不吐新对象，用 0x9434 全卡元数据找
            val meta = runCatching { loadStaMeta(storageId) }.getOrNull().orEmpty()
            val freshMeta = meta.keys.firstOrNull { it !in before && isWanted(it) }
            if (freshMeta != null) {
                val size = meta[freshMeta]?.first?.takeIf { it > 0 }
                    ?: (runCatching { objectSize(freshMeta) }.getOrNull() ?: 0L)
                if (size > 0) {
                    log("相机新增对象 handle=$freshMeta（0x9434）")
                    return newDirectObject(freshMeta, storageId, size, video)
                }
            }
            // ③ 预测句柄探测：每个媒体前缀的「最大编号 + 1」，0x9421 有非零大小即命中
            val candidates = before.filter { isWanted(it) }
                .groupBy { (it ushr 24) and 0xFF }
                .map { (prefix, hs) ->
                    (prefix shl 24) or (((hs.max() and 0xFFFF) + 1) and 0xFFFF)
                }
            for (cand in candidates) {
                val size = runCatching { objectSize(cand) }.getOrNull() ?: 0L
                if (size > 0) {
                    log("相机新增对象 handle=$cand（预测命中）")
                    return newDirectObject(cand, storageId, size, video)
                }
            }
        }
        log("${timeoutMs / 1000}s 内没看到新${if (video) "视频" else "照片"}")
        return null
    }

    /** 视频句柄前缀（Z30 实测 0x59/0x5A/0x61/0x62/0x65/0x75）；仅录像等待用，不改照片浏览列表 */
    private fun staVideo(handle: Int): String? = when ((handle ushr 24) and 0xFF) {
        0x59, 0x5A, 0x61, 0x62, 0x65, 0x75 -> "mov"
        else -> null
    }

    /** 直读态下用 0x9421 大小 + 占位名构造新对象，照片并入拍前基线 */
    private fun newDirectObject(handle: Int, storageId: Int, size: Long, video: Boolean = false): CameraObject {
        val ext = if (video) (staVideo(handle) ?: staFormat(handle) ?: "mov") else (staFormat(handle) ?: "jpg")
        if (!video) lastKnownMediaHandles = lastKnownMediaHandles + handle
        return CameraObject(
            handle = handle,
            storageId = storageId,
            filename = "DSC_%04d.$ext".format(handle and 0xFFFF),
            size = size,
            width = 0,
            height = 0,
            formatCode = when (ext) {
                "nef" -> 0xB103
                "mov" -> 0xB003
                else -> 0x3801
            },
            captureDate = "",
        )
    }

    private suspend fun tryReleaseShutter(): Boolean {
        val media = transport.transaction(
            PtpOp.NIKON_INITIATE_CAPTURE_REC_IN_MEDIA,
            params = listOf(0, 0),
        )
        log("InitiateCaptureRecInMedia(0,0) → ${PtpResp.name(media.code)}")
        if (media.ok) return true
        // STA 下部分固件把第一参数当存储 ID 解释：0 被拒时试 0xFFFFFFFF（全部存储）
        val mediaAll = transport.transaction(
            PtpOp.NIKON_INITIATE_CAPTURE_REC_IN_MEDIA,
            params = listOf(-1, 0),
        )
        log("InitiateCaptureRecInMedia(-1,0) → ${PtpResp.name(mediaAll.code)}")
        if (mediaAll.ok) return true

        val standard = transport.transaction(PtpOp.INITIATE_CAPTURE, params = listOf(0, 0))
        log("InitiateCapture(0,0) → ${PtpResp.name(standard.code)}")
        if (standard.ok) return true

        val sdram = transport.transaction(0x90C0)
        log("InitiateCaptureRecInSdram → ${PtpResp.name(sdram.code)}")
        return sdram.ok
    }

    // ───────────────────────── 录像 / 画面模式（P2） ─────────────────────────

    /** 录像拍前基线（startMovieRec 记录，endMovieRec 对账找新视频） */
    private var movieBaseline: Set<Int> = emptySet()

    /**
     * 0x920A StartMovieRecInCard：0x9435(1) 应用模式 → 发命令；0xA00B 表示机身要求
     * 先开实时取景，则启动取景后重试一次；DeviceBusy 短暂重试。失败时退出应用模式。
     */
    suspend fun startMovieRec(storageId: Int): Boolean {
        enterAppMode()
        movieBaseline = captureBaseline(storageId)
        var triedLiveView = false
        var busyRetries = 0
        while (true) {
            val r = transport.transaction(PtpOp.NIKON_START_MOVIE_REC)
            log("StartMovieRecInCard → ${PtpResp.name(r.code)}")
            if (r.ok) return true
            if (r.code == PtpResp.NIKON_NOT_LIVE_VIEW && !triedLiveView) {
                triedLiveView = true
                log("录像要求实时取景，启动取景后重试")
                runCatching { startLiveView() }.onFailure { log("录像前启动取景失败：${it.message}") }
                continue
            }
            if (r.code == PtpResp.DEVICE_BUSY && busyRetries < 2) {
                busyRetries++
                delay(500)
                continue
            }
            exitAppMode()
            if (r.code == PtpResp.NIKON_INVALID_STATUS) {
                throw PtpException("相机当前档位不允许录像，请把机身拨盘转到视频档")
            }
            throw PtpException("开始录像失败：${PtpResp.name(r.code)}")
        }
    }

    /** 0x920B EndMovieRec：收录后等新视频写卡（最多 [timeoutMs]），再退应用模式 */
    suspend fun endMovieRec(storageId: Int, timeoutMs: Long = 30000): CameraObject? {
        val r = transport.transaction(PtpOp.NIKON_END_MOVIE_REC)
        log("EndMovieRec → ${PtpResp.name(r.code)}")
        val obj = waitForNewObject(storageId, movieBaseline, timeoutMs, video = true)
        movieBaseline = emptySet()
        exitAppMode()
        if (!r.ok && obj == null) throw PtpException("停止录像失败：${PtpResp.name(r.code)}")
        return obj
    }

    /** 0x90C2：参数 1 = 遥控画面（相机屏熄，显示「已连接到智能设备」）；0 = 联动画面（相机屏同显） */
    suspend fun setCameraDisplayMode(remote: Boolean): Boolean {
        val param = if (remote) 1 else 0
        val r = transport.transaction(PtpOp.NIKON_CHANGE_CAMERA_MODE, params = listOf(param))
        log("ChangeCameraMode($param) → ${PtpResp.name(r.code)}")
        return r.ok
    }

    // ───────────────────────── P3：触屏对焦 / B 门长曝光 ─────────────────────────

    /**
     * 触屏点对焦：0x9205 ChangeAfArea(x,y) → 150ms → 0x90C1 AfDrive。
     * AF 坐标域按取景帧同宽高比线性映射：x∈[0,4000]，y∈[0,4000×h/w]；
     * 帧尺寸未知时用 4000×3000 标称域兜底（两家报文分析独立收敛）。
     */
    suspend fun touchFocus(nx: Float, ny: Float, frameWidth: Int, frameHeight: Int): Boolean {
        val boxW = 4000
        val boxH = if (frameWidth > 0 && frameHeight > 0) {
            (boxW.toFloat() * frameHeight / frameWidth).toInt().coerceAtLeast(1)
        } else 3000
        val x = (nx.coerceIn(0f, 1f) * boxW).toInt().coerceIn(0, boxW)
        val y = (ny.coerceIn(0f, 1f) * boxH).toInt().coerceIn(0, boxH)
        val area = transport.transaction(PtpOp.NIKON_CHANGE_AF_AREA, params = listOf(x, y))
        log("ChangeAfArea($x,$y) → ${PtpResp.name(area.code)}")
        if (!area.ok) return false
        delay(150)
        val drive = transport.transaction(PtpOp.NIKON_AF_DRIVE)
        log("AfDrive → ${PtpResp.name(drive.code)}")
        return drive.ok
    }

    /**
     * B 门长曝光：机身拨盘切 B 档后，App 用 0x9207 开曝、计时、0x920C 收门。
     * 属性 0x500D 实测只读，写 B 值行不通，只能机身拨盘切档。
     * 收门必须 0x920C(0,0)——Z30 实测无参数回 ParameterNotSupported；0x1010(0) 兜底。
     * 0xA200=BulbBusy / DeviceBusy 时短暂重试；收门后复用三层轮询找新照片。
     */
    suspend fun bulbExposure(
        storageId: Int,
        seconds: Int,
        onTick: (Int) -> Unit,
        keepAppMode: Boolean = true,
    ): CameraObject? {
        enterAppMode()
        try {
            val before = captureBaseline(storageId)
            if (!tryReleaseShutter()) throw PtpException("B 门开曝失败：相机拒绝了快门指令")
            for (s in seconds downTo 1) {
                onTick(s)
                delay(1000)
            }
            onTick(0)
            // 收门逐级降级（任一 OK 即收门）：
            // ① 0x920C(0,0) TerminateCapture —— 0x9207 长曝光的权威配套结束命令，必须带两个参数；
            // ② 0x1010(0) TerminateOpenCapture —— 0x100F 路径兜底（Z 系无线常见 0x920C 被拒）。
            var closed = false
            val closePlan = listOf(
                PtpOp.NIKON_TERMINATE_CAPTURE to listOf(0, 0),
                PtpOp.TERMINATE_OPEN_CAPTURE to listOf(0),
            )
            for ((opcode, params) in closePlan) {
                var attempt = 0
                while (true) {
                    val r = transport.transaction(opcode, params = params)
                    log("收门 0x${opcode.toString(16)}$params → ${PtpResp.name(r.code)}")
                    if (r.ok) {
                        closed = true
                        break
                    }
                    if ((r.code == PtpResp.DEVICE_BUSY || r.code == PtpResp.NIKON_BULB_RELEASE_BUSY) && attempt < 5) {
                        attempt++
                        delay(600)
                        continue
                    }
                    break
                }
                if (closed) break
            }
            if (!closed) throw PtpException("收门失败：相机可能仍在曝光，请手动按一次快门结束")
            return waitForNewObject(storageId, before, 30000)
        } finally {
            if (!keepAppMode) exitAppMode()
        }
    }

    // ───────────────────────── 实时取景 ─────────────────────────

    suspend fun startLiveView() {
        // 取景需要 App 控制态（0x9435=1）+ 控制权交给主机（实测打通顺序）
        enterAppMode()
        val mode = transport.transaction(PtpOp.NIKON_CHANGE_CAMERA_MODE, params = listOf(1))
        log("ChangeCameraMode(1) → ${PtpResp.name(mode.code)}")
        runCatching { setProp(PtpProp.NIKON_LIVE_VIEW_SELECTOR, byteArrayOf(0)) }

        // 取景尺寸：0xD1AC = 2 约 VGA（失败忽略，相机用当前设置）
        runCatching { setProp(PtpProp.NIKON_LIVE_VIEW_IMAGE_SIZE, byteArrayOf(2)) }

        val r = transport.transaction(PtpOp.NIKON_START_LIVE_VIEW)
        if (!r.ok) {
            val prohibit = runCatching { readPropU8(PtpProp.NIKON_LIVE_VIEW_PROHIBIT) }.getOrNull()
            throw PtpException(
                "StartLiveView 失败：${PtpResp.name(r.code)}" +
                    (prohibit?.let { "，禁止条件代码 $it" } ?: ""),
            )
        }
        log("StartLiveView → OK，等待 DeviceReady")
        val deadline = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < deadline) {
            val ready = transport.transaction(PtpOp.NIKON_DEVICE_READY)
            if (ready.ok) {
                log("DeviceReady → OK")
                return
            }
            delay(80)
        }
        log("DeviceReady 超时，继续尝试取帧")
    }

    suspend fun liveViewJpeg(): ByteArray? {
        val candidates = liveViewOp?.let { listOf(it) } ?: listOf(
            PtpOp.NIKON_GET_LIVE_VIEW_IMAGE_EX,
            PtpOp.NIKON_GET_LIVE_VIEW_IMAGE,
        )
        var lastCode = -1
        for (opcode in candidates) {
            val r = transport.transaction(opcode, dataPhase = PtpIp.PHASE_DATA_IN)
            if (r.ok) {
                liveViewOp = opcode
                return extractJpeg(r.data)
            }
            lastCode = r.code
            val now = System.currentTimeMillis()
            if (now - lastFrameLogMs > 2000) {
                log("取帧 0x${opcode.toString(16)} → ${PtpResp.name(r.code)}")
                lastFrameLogMs = now
            }
        }
        // 缓存的取帧指令开始失败时清掉，下次重新在两档间探测
        liveViewOp = null
        if (lastCode >= 0) {
            val now = System.currentTimeMillis()
            if (now - lastFrameLogMs > 2000) {
                log("取帧失败：${PtpResp.name(lastCode)}")
                lastFrameLogMs = now
            }
        }
        return null
    }

    suspend fun endLiveView() {
        runCatching {
            val r = transport.transaction(PtpOp.NIKON_END_LIVE_VIEW)
            log("EndLiveView → ${PtpResp.name(r.code)}")
        }
        runCatching {
            val r = transport.transaction(PtpOp.NIKON_CHANGE_CAMERA_MODE, params = listOf(0))
            log("ChangeCameraMode(0) → ${PtpResp.name(r.code)}")
        }
        // 退出 App 控制态，恢复对象浏览能力（否则之后照片列表 AccessDenied）
        runCatching { exitAppMode() }
        liveViewOp = null
    }

    // ───────────────────────── 属性 ─────────────────────────

    suspend fun readPropU8(prop: Int): Int? {
        val r = transport.transaction(PtpOp.GET_DEVICE_PROP_VALUE, params = listOf(prop), dataPhase = PtpIp.PHASE_DATA_IN)
        val data = r.data ?: return null
        return if (r.ok && data.isNotEmpty()) data[0].toInt() and 0xFF else null
    }

    suspend fun setProp(prop: Int, value: ByteArray) {
        val r = transport.transaction(
            PtpOp.SET_DEVICE_PROP_VALUE,
            params = listOf(prop),
            dataPhase = PtpIp.PHASE_DATA_OUT,
            dataOut = value,
        )
        if (!r.ok) log("SetProp 0x${prop.toString(16)} → ${PtpResp.name(r.code)}")
    }

    suspend fun batteryLevel(): Int? = runCatching { readPropU8(PtpProp.BATTERY_LEVEL) }.getOrNull()

    /** 读取设备属性的原始字节（GET_DEVICE_PROP_VALUE 的数据阶段） */
    private suspend fun readPropRaw(prop: Int): ByteArray? {
        val r = transport.transaction(PtpOp.GET_DEVICE_PROP_VALUE, params = listOf(prop), dataPhase = PtpIp.PHASE_DATA_IN)
        return if (r.ok) r.data else null
    }

    /** P1 探测用：读属性原始字节的十六进制 + 响应码（含被拒原因） */
    suspend fun probePropRaw(prop: Int): String = try {
        val r = transport.transaction(PtpOp.GET_DEVICE_PROP_VALUE, params = listOf(prop), dataPhase = PtpIp.PHASE_DATA_IN)
        val hex = r.data?.take(16)?.joinToString(" ") { "%02X".format(it) } ?: "-"
        "resp=${PtpResp.name(r.code)} data=[$hex] len=${r.data?.size ?: 0}"
    } catch (t: Throwable) {
        "EXCEPTION ${t.message}"
    }

    /**
     * 读水平仪原始 u32（用于标定）。返回属性数据的小端 u32；-1(0xFFFFFFFF) 表示无效。
     * 编码待标定：初判 16.16 定点角度（度 = u32/65536），此处先返回原值让 UI/日志观察。
     */
    suspend fun readLevelRaw(prop: Int): Int? = try {
        val r = transport.transaction(PtpOp.GET_DEVICE_PROP_VALUE, params = listOf(prop), dataPhase = PtpIp.PHASE_DATA_IN)
        if (r.ok && r.data != null && r.data.size >= 4) PtpCodec.readU32(r.data, 0) else null
    } catch (t: Throwable) {
        null
    }

    /**
     * 一次性读取拍摄参数（供监看页参数条显示，P0 只读）。
     * Z30 Gen2 的这些属性用 0x1015 读即可；读不到返回 null，UI 显示「--」。
     * 注意：快门优先读尼康 0xD100（分子/分母打包），失败再回退标准 0x500D（1/10000 秒）。
     */
    suspend fun readExposure(): ExposureInfo = withContext(Dispatchers.IO) {
        fun u16(d: ByteArray?) = if (d != null && d.size >= 2) PtpCodec.readU16(d, 0) else null
        fun u32(d: ByteArray?) = if (d != null && d.size >= 4) PtpCodec.readU32(d, 0) else null

        val apertureRaw = u16(runCatching { readPropRaw(PtpProp.APERTURE) }.getOrNull())
        val isoRaw = u16(runCatching { readPropRaw(PtpProp.ISO) }.getOrNull())
        val programRaw = u16(runCatching { readPropRaw(PtpProp.EXPOSURE_PROGRAM) }.getOrNull())
        val wbRaw = u16(runCatching { readPropRaw(PtpProp.WHITE_BALANCE) }.getOrNull())
        val biasRaw = runCatching { readPropRaw(PtpProp.EXPOSURE_BIAS) }.getOrNull()?.let { d ->
            if (d.size >= 2) PtpCodec.readU16(d, 0).let { if (it >= 0x8000) it - 0x10000 else it } else null
        }
        // 快门：优先尼康 0xD100（高16位分子/低16位分母）
        val nikonShutter = u32(runCatching { readPropRaw(PtpProp.NIKON_SHUTTER) }.getOrNull())
        val shutter = if (nikonShutter != null && nikonShutter != 0 && nikonShutter != -1) {
            val num = nikonShutter ushr 16
            val den = nikonShutter and 0xFFFF
            if (num > 0 && den > 0) ShutterValue(num, den) else null
        } else {
            // 回退 0x500D：单位 1/10000 秒
            u32(runCatching { readPropRaw(PtpProp.EXPOSURE_TIME) }.getOrNull())?.let { raw ->
                if (raw == -1) ShutterValue(-1, -1) // B门 (0xFFFFFFFF)
                else if (raw > 0) ShutterValue(1, 10000 / raw) else null // 单位 1/10000 秒
            }
        }
        ExposureInfo(
            aperture = apertureRaw?.let { if (it > 0) "f/${"%.1f".format(it / 100f)}" else null },
            shutter = shutter?.let {
                when {
                    it.numerator < 0 -> "BULB"
                    it.numerator >= 1 -> if (it.numerator >= it.denominator) "${it.numerator / it.denominator}\"" else "1/${it.denominator / it.numerator}"
                    else -> null
                }
            },
            iso = isoRaw?.let { if (it == 0xFFFF) "AUTO" else it.toString() },
            whiteBalance = wbRaw?.let { wbLabel(it) },
            program = programRaw?.let { programLabel(it) },
            exposureBias = biasRaw?.let { "${if (it >= 0) "+" else ""}${"%.1f".format(it / 6f)}" },
        )
    }

    private fun wbLabel(v: Int): String = when (v) {
        0, 1, 2 -> "AUTO"
        0x8010 -> "日光"
        0x8011 -> "白炽灯"
        0x8012 -> "荧光灯"
        0x8013 -> "闪光灯"
        0x8014 -> "阴天"
        0x8015 -> "阴影"
        0x8016 -> "手动"
        else -> "WB $v"
    }

    private fun programLabel(v: Int): String = when (v) {
        1 -> "M"; 2 -> "P"; 3 -> "A"; 4 -> "S"
        0x8012 -> "U1"; 0x8013 -> "U2"; else -> ""
    }

    /**
     * 读属性描述符的 Get/Set 标志（P1 探测，无副作用）。
     * DevicePropDesc 数据集：u16 code, u16 type, u8 GetSet(bit0=Get,bit1=Set), ...
     * @return Pair(是否可读, 是否可写)；读取失败返回 null
     */
    suspend fun propAccess(prop: Int): Pair<Boolean, Boolean>? = try {
        val r = transport.transaction(PtpOp.GET_DEVICE_PROP_DESC, params = listOf(prop), dataPhase = PtpIp.PHASE_DATA_IN)
        val d = r.data
        if (r.ok && d != null && d.size >= 5) {
            val flags = d[4].toInt() and 0xFF
            Pair(flags and 0x01 != 0, flags and 0x02 != 0)
        } else null
    } catch (t: Throwable) {
        null
    }

    /**
     * 连接保活探测：命令通道发一个轻事务。
     * 只有传输层异常（socket 断开/超时）才算连接丢失；响应码非 OK 只是当前模式不支持，不算断线。
     * 顺带返回电量（读得到时），供心跳刷新 UI。
     */
    suspend fun probe(): Pair<Boolean, Int?> = try {
        val r = transport.transaction(
            PtpOp.GET_DEVICE_PROP_VALUE,
            params = listOf(PtpProp.BATTERY_LEVEL),
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        val battery = if (r.ok && r.data != null && r.data.isNotEmpty()) r.data[0].toInt() and 0xFF else null
        true to battery
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false to null
    }

    /** 主存储卡的剩余字节（用于监控页/相机页展示） */
    suspend fun freeSpace(): Long? {
        val storage = storageIds.firstOrNull() ?: return null
        val r = transport.transaction(
            PtpOp.GET_STORAGE_INFO,
            params = listOf(storage),
            dataPhase = PtpIp.PHASE_DATA_IN,
        )
        val data = r.data ?: return null
        if (!r.ok) return null
        return runCatching {
            val reader = PtpReader(data)
            reader.u16() // storageType
            reader.u16() // filesystemType
            reader.u16() // accessCapability
            reader.u32() // maxCapacity low
            reader.u32() // maxCapacity high
            val freeLow = reader.u32()
            val freeHigh = reader.u32()
            (freeLow.toLong() and 0xFFFFFFFFL) or ((freeHigh.toLong() and 0xFFFFFFFFL) shl 32)
        }.getOrNull()
    }

    // ───────────────────────── 解析工具 ─────────────────────────

    private fun parseDeviceInfo(data: ByteArray): CameraDeviceInfo {
        val reader = PtpReader(data)
        reader.u16() // standardVersion
        reader.u32() // vendorExtensionID
        reader.u16() // vendorExtensionVersion
        reader.string() // vendorExtensionDesc
        reader.u16() // functionalMode
        val operations = reader.u16Array().toSet()
        val events = reader.u16Array().toSet()
        val properties = reader.u16Array().toSet()
        reader.u16Array() // captureFormats
        reader.u16Array() // imageFormats
        val manufacturer = reader.string()
        val model = reader.string()
        val firmware = reader.string()
        val serial = reader.string()
        return CameraDeviceInfo(
            manufacturer = manufacturer,
            model = model,
            firmware = firmware,
            serial = serial,
            operations = operations,
            properties = properties,
            events = events,
        )
    }

    private fun parseU32Array(data: ByteArray?): List<Int> {
        if (data == null || data.size < 4) return emptyList()
        val reader = PtpReader(data)
        val count = reader.u32()
        if (count < 0 || count > 65535) return emptyList()
        return List(count) { reader.u32() }
    }

    /** 取景帧 = 显示器头 + JPEG；按 SOI/EOI 截取，兼容不同代机身的头长度 */
    private fun extractJpeg(data: ByteArray?): ByteArray? {
        if (data == null || data.size < 4) return null
        var start = -1
        for (i in 0..data.size - 3) {
            if ((data[i].toInt() and 0xFF) == 0xFF &&
                (data[i + 1].toInt() and 0xFF) == 0xD8 &&
                (data[i + 2].toInt() and 0xFF) == 0xFF
            ) {
                start = i
                break
            }
        }
        if (start < 0) return null
        var end = -1
        var i = data.size - 2
        while (i >= start) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD9) {
                end = i + 2
                break
            }
            i--
        }
        val stop = if (end > start) end else data.size
        return data.copyOfRange(start, stop)
    }

    private companion object {
        const val CHUNK_SIZE = 4 * 1024 * 1024

        /** 0x9530 GetPartialImage 单次块大小（真机实测可用值） */
        const val STA_PREVIEW_CHUNK = 10240
        const val STA_PREVIEW_MAX = 32 * 1024 * 1024

        /** STA 缩略图提速：0x9431 读文件头字节数（内嵌 EXIF 缩略图通常在前 128KB） */
        const val STA_HEAD_BYTES = 128 * 1024
        /** 内嵌缩略图最小有效字节（<1KB 视为无效，回退 0x9530） */
        const val MIN_EMBEDDED_THUMB = 1024

        /** 0x952B 后等待用户在相机端确认的窗口（基准实现实证 8500ms） */
        const val HOST_CONFIRM_WAIT_MS = 8500L
    }
}
