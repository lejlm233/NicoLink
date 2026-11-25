package com.filmlens.nicolink.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.filmlens.nicolink.camera.ptp.DataSink
import com.filmlens.nicolink.camera.ptp.NikonCamera
import com.filmlens.nicolink.camera.ptp.PtpCodec
import com.filmlens.nicolink.camera.ptp.PtpException
import com.filmlens.nicolink.camera.ptp.PtpInitRejectedException
import com.filmlens.nicolink.camera.ptp.PtpIpTransport
import com.filmlens.nicolink.camera.ptp.PtpProp
import com.filmlens.nicolink.data.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * 全局相机会话：连接、发现、照片列表、缩略图、下载、拍照、实时取景。
 *
 * UI 只跟这个对象打交道；PTP 细节在 camera.ptp 包里。
 * 所有网络/IO 操作都在 [scope]（IO 线程）上，Compose 只读状态。
 */
object CameraSession {
    private const val TAG = "NicoLinkCamera"
    private const val MAX_LOG_LINES = 400

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    /**
     * App 是否在前台（由 MainActivity.onResume/onPause 更新）。
     * 用于判断「异常断开后是否自动重连」——后台断开不立即重连（省电、避免徒劳扫描），
     * 回前台后再恢复。缺省 true 保证首次进入也能重连。
     */
    var isAppInForeground by mutableStateOf(true)
        private set

    /** 自动显影钩子：(落盘绝对路径, 文件名) → Unit，由 App 层注册；commonMain 不直接依赖引擎 */
    var autoDevelopHook: ((String, String) -> Unit)? = null

    /** 编辑缩略图保存完成通知（自动显影保存后调用，App 层据此 bumpPhotos 刷新三页） */
    var onEditThumbSaved: (() -> Unit)? = null

    /** 相机照片落盘通知（拍照/自动传输成功保存一张后调用，App 层据此累计热力图） */
    var onPhotoDownloaded: (() -> Unit)? = null

    /** 相机连接成功通知（App 层据此把"用过的设备"上报到云端收藏） */
    var onCameraConnected: ((com.filmlens.nicolink.camera.SavedCamera) -> Unit)? = null

    /** 从照片 EXIF 解析出快门次数后的通知（App 层据此上报云端收藏） */
    var onShutterCountUpdated: ((com.filmlens.nicolink.camera.SavedCamera) -> Unit)? = null

    /**
     * 本次连接会话是否已解析过快门次数。每次连接都重置，连接后第一张成功下载的照片
     * 解析一次并置位——这样每次连接都会刷新快门次数（反映真实增长），且同一会话只解析一张，
     * 避免资源浪费。@Volatile 保证下载回调与连接重置跨线程可见。
     */
    @Volatile
    private var shutterParsedThisSession = false

    /** 相机断开通知（App 层据此上报本次累计的连接时长） */
    var onCameraDisconnected: ((com.filmlens.nicolink.camera.SavedCamera) -> Unit)? = null

    /** 当前连接的计时起点与相机身份，断开时结算到累计时长 */
    private var connectedTimerId: String = ""
    private var connectedTimerSerial: String = ""
    private var connectedSince: Long = 0L

    /** 自动显影队列状态（供相机页进度条显示，放在传输队列下方） */
    data class AutoDevelopProgress(val active: Boolean = false, val currentName: String = "", val remaining: Int = 0)
    var autoDevelopProgress by mutableStateOf(AutoDevelopProgress())
        private set

    /** 更新自动显影队列（AutoDeveloper 调用） */
    fun updateAutoDevelopProgress(p: AutoDevelopProgress) { autoDevelopProgress = p }

    private var transport: PtpIpTransport? = null
    private var camera: NikonCamera? = null

    // ───────── UI 状态 ─────────

    var state by mutableStateOf(CameraState())
        private set

    var savedCameras by mutableStateOf<List<SavedCamera>>(emptyList())
        private set

    var discovered by mutableStateOf<List<DiscoveredCamera>>(emptyList())
        private set

    var discovering by mutableStateOf(false)
        private set

    var objects by mutableStateOf<List<CameraObject>>(emptyList())
        private set

    var objectsLoading by mutableStateOf(false)
        private set

    /** 最近一次照片列表读取失败原因（主页空态展示；null 正常） */
    var objectsError by mutableStateOf<String?>(null)
        private set

    /** 对象访问被拒（相机处于拍摄模式）：提示用户切回放模式 */
    var objectsDenied by mutableStateOf(false)
        private set

    var transfer by mutableStateOf<TransferProgress?>(null)
        private set

    var liveActive by mutableStateOf(false)
        private set

    /** 取景启动/运行失败原因（监看页直接展示；null 正常） */
    var liveError by mutableStateOf<String?>(null)
        private set

    var liveFrame by mutableStateOf<ImageBitmap?>(null)
        private set

    /** 实时取景亮度直方图（256 桶），供监看页叠加显示 */
    var liveHistogram by mutableStateOf(HistogramData.EMPTY)
        private set

    /** 相机当前拍摄参数（光圈/快门/ISO/WB/模式），取景期间定时刷新 */
    var exposureInfo by mutableStateOf(ExposureInfo())
        private set

    /** 水平仪原始 u32（D067/D07D/D07E），取景期间定时刷新，供标定与电子水平仪显示 */
    var levelRaw by mutableStateOf(LevelRaw())
        private set

    // ───────── P2 拍照增强状态 ─────────

    /** 拍摄模式：photo=照片（0x9207）/ video=录像（0x920A/0x920B） */
    var captureMode by mutableStateOf("photo")
        private set

    /** 定时拍摄秒数：0=关，2/5/10 秒后释放快门 */
    var timerSeconds by mutableIntStateOf(0)
        private set

    /** 间隔拍摄秒数：0=关，2/5/10 秒循环拍照（再次点按停止） */
    var intervalSeconds by mutableIntStateOf(0)
        private set

    /** 本轮间隔拍摄已拍张数 */
    var intervalTaken by mutableIntStateOf(0)
        private set

    /** 正在倒计时的剩余秒数（0=不在倒计时，监看页画大数字） */
    var captureCountdown by mutableIntStateOf(0)
        private set

    /** 录像中（监看页快门变停止按钮） */
    var videoRecording by mutableStateOf(false)
        private set

    /** 画面模式：false=联动画面（相机屏同显），true=遥控画面（相机屏熄） */
    var displayRemote by mutableStateOf(false)
        private set

    /** 拍摄增强的一次性提示（监看页显示） */
    var captureMessage by mutableStateOf<String?>(null)
        private set

    // ───────── P3 对焦 / B 门状态 ─────────

    /** 最近一次触屏对焦点（归一化 0..1，监看页画对焦框；null 不显示） */
    var focusPoint by mutableStateOf<Pair<Float, Float>?>(null)
        private set

    /** B 门曝光时长（秒），机身拨盘需切到 B 档 */
    var bulbSeconds by mutableIntStateOf(10)
        private set

    /** B 门开曝中（快门按钮变停止/倒计时） */
    var bulbActive by mutableStateOf(false)
        private set

    /** 连接时解析的 DeviceInfo（含 opcode/属性/事件支持表），供能力探测报告 */
    var deviceInfo by mutableStateOf<CameraDeviceInfo?>(null)
        private set

    /** P1 能力探测报告（按 opcode 列出 Z30 是否声明支持；结果写日志 + 此状态供 UI 展示） */
    var capabilitiesReport by mutableStateOf("")
        private set

    /**
     * P1 无副作用能力探测：基于 GetDeviceInfo 的 operations/properties/events 集合，
     * 判断 P2/P3 需要的 opcode 是否被 Z30 声明支持。不写属性、不触发录像/B门/AF（无副作用）。
     * 注：声明支持 ≠ 实际可用（部分属性只读），真机调用仍需回读确认；这里只做第一层筛选。
     */
    fun probeCapabilities() {
        val info = deviceInfo
        if (info == null) { log("能力探测：未连接或无 DeviceInfo"); return }
        val ops = info.operations
        val props = info.properties
        fun op(code: Int, name: String) = "  ${if (code in ops) "✓" else "✗"} 0x${code.toString(16).uppercase()} $name"
        fun pr(code: Int, name: String) = "  ${if (code in props) "✓" else "✗"} 0x${code.toString(16).uppercase()} $name"
        val sb = StringBuilder()
        sb.appendLine("=== Z30 能力探测（DeviceInfo 声明）===")
        sb.appendLine("机型: ${info.model}  固件: ${info.firmware}")
        sb.appendLine("operations 共 ${ops.size} 项, properties 共 ${props.size} 项, events 共 ${info.events.size} 项")
        sb.appendLine("-- 拍照控制 --")
        sb.appendLine(op(0x9207, "InitiateCaptureRecInMedia(快门)"))
        sb.appendLine(op(0x920C, "TerminateCapture(B门收门)"))
        sb.appendLine(op(0x100E, "InitiateCapture(标准拍照)"))
        sb.appendLine("-- 对焦 --")
        sb.appendLine(op(0x9205, "ChangeAfArea(设置对焦点)"))
        sb.appendLine(op(0x90C1, "AfDrive(触发AF)"))
        sb.appendLine(op(0x9206, "AfDriveCancel"))
        sb.appendLine(op(0x9204, "MfDrive"))
        sb.appendLine("-- 录像 --")
        sb.appendLine(op(0x920A, "StartMovieRecInCard"))
        sb.appendLine(op(0x920B, "EndMovieRec"))
        sb.appendLine("-- 取景/画面模式 --")
        sb.appendLine(op(0x9201, "StartLiveView"))
        sb.appendLine(op(0x90C2, "ChangeCameraMode(联动/遥控)"))
        sb.appendLine(op(0x1014, "GetDevicePropDesc(属性描述)"))
        sb.appendLine(op(0x1015, "GetDevicePropValue(读属性)"))
        sb.appendLine(op(0x1016, "SetDevicePropValue(写属性)"))
        sb.appendLine("-- 拍摄属性 --")
        sb.appendLine(pr(0x5005, "WhiteBalance"))
        sb.appendLine(pr(0x5007, "FNumber(光圈)"))
        sb.appendLine(pr(0x500D, "ExposureTime(快门)"))
        sb.appendLine(pr(0x500E, "ExposureProgram(模式)"))
        sb.appendLine(pr(0x500F, "ISO"))
        sb.appendLine(pr(0x5010, "ExposureBias"))
        sb.appendLine(pr(0xD100, "NikonShutter"))
        val report = sb.toString()
        capabilitiesReport = report
        log(report)
        // 第二层（需真机调用，但无副作用）：读属性描述符的 Get/Set 标志，判断哪些属性真正可写。
        // DeviceInfo 声明支持 ≠ 可写（部分属性部分机身只读），描述符是权威判据。
        scope.launch {
            val cam = camera ?: return@launch
            val extra = StringBuilder("\n-- 属性 Get/Set 标志（0x1014 实测）--\n")
            val checks = listOf(
                0x5005 to "WhiteBalance", 0x5007 to "FNumber(光圈)",
                0x500D to "ExposureTime(快门)", 0x500E to "ExposureProgram(模式)",
                0x500F to "ISO", 0x5010 to "ExposureBias",
            )
            for ((code, name) in checks) {
                val acc = cam.propAccess(code)
                extra.appendLine("  ${if (acc == null) "?" else if (acc.second) "可写" else "只读"} 0x${code.toString(16).uppercase()} $name  ${acc?.let { "get=${it.first} set=${it.second}" } ?: "(读取失败)"}")
            }
            // 第三层：水平仪候选属性（相机自身倾斜传感器，比手机传感器更贴机位）
            extra.appendLine("\n-- 水平仪候选属性（0x1015 原始字节）--\n")
            val levelProps = listOf(
                0xD067 to "AngleLevel", 0xD07D to "Pitching", 0xD07E to "Yawing",
                0xD10E to "CameraOrientation",
            )
            for ((code, name) in levelProps) {
                extra.appendLine("  0x${code.toString(16).uppercase()} $name: ${cam.probePropRaw(code)}")
            }
            val merged = capabilitiesReport + extra.toString()
            capabilitiesReport = merged
            log(extra.toString())
        }
    }

    /** 每次下载完成 +1，主页据此刷新 MediaStore 照片流 */
    var downloadedTick by mutableIntStateOf(0)
        private set

    /** 已下载到本机的相机对象句柄（按序列号持久化；照片列表「已下载」角标用） */
    var downloadSet by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** 从持久层同步已下载句柄（连接成功 / 列表刷新时调用） */
    fun syncDownloadedSet() {
        downloadSet = CameraStore.downloadedHandles(appContext ?: return, state.serial)
    }

    /** 当前连接模式（ap=手机连相机热点；sta=相机连手机热点/同一网络） */
    var connectMode by mutableStateOf("sta")

    /** 相机照片大图浮层（由主页最外层渲染，避免被顶栏/底部玻璃按钮盖住） */
    var cameraPreview by mutableStateOf<CameraObject?>(null)
        private set

    fun openCameraPreview(obj: CameraObject) {
        // 清掉上一张照片残留的终态 transfer（finished/error），否则预览任何一张
        // 都可能被旧的「已下载」状态污染
        transfer?.takeIf { it.finished || it.error != null }?.let { transfer = null }
        cameraPreview = obj
    }

    fun closeCameraPreview() {
        cameraPreview = null
    }

    /** 缩略图缓存（handle → 位图） */
    val thumbs = mutableStateMapOf<Int, ImageBitmap>()

    /** 连接/协议调试日志（连接页与设置页展示） */
    val logs = mutableStateListOf<String>()

    // ───────── 首次注册状态 ─────────

    var registering by mutableStateOf(false)
        private set

    /** 注册流程提示（连接页展示；null 隐藏） */
    var registerNote by mutableStateOf<String?>(null)
        private set

    /** 注册期间读到的一次性配对码（与相机屏幕显示值比对；null 表示相机没发） */
    var pairingCode by mutableStateOf<String?>(null)
        private set

    private val thumbPending = Collections.synchronizedSet(mutableSetOf<Int>())
    private val thumbFailed = Collections.synchronizedSet(mutableSetOf<Int>())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile
    private var liveWanted = false
    private var liveJob: Job? = null

    /** 水平仪高频轮询协程（独立于取景帧循环，~8Hz；与取帧共用 transport Mutex，故不宜更快） */
    private var levelJob: Job? = null

    /** P2 拍摄增强：定时倒计时 / 间隔循环 / 录像任务；拍照串行闸门防重叠 */
    private var timerJob: Job? = null
    private var intervalJob: Job? = null
    private var videoJob: Job? = null
    private var videoStorageId: Int = -1
    private var bulbJob: Job? = null
    private var focusJob: Job? = null
    private val captureGate = Mutex()

    /** 连接保活：周期轻事务探测，连续失败判定相机断开 */
    private var heartbeatJob: Job? = null

    /** 日志落盘用的单线程执行器 */
    private val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        savedCameras = CameraStore.savedCameras(context.applicationContext)
        // 从 UiPrefs 的持久化位置恢复连接模式（camera 包不依赖 ui 包，直接读 SharedPreferences）。
        // 之前 connectMode 恒为 "sta"，导致自动重连在 AP 模式下也按 STA 走、跳过 192.168.1.1 直探。
        connectMode = context.applicationContext
            .getSharedPreferences("nicolink_ui", Context.MODE_PRIVATE)
            .getString("connect_mode", null) ?: "sta"
        log("连接层就绪（模式 $connectMode，主机 GUID ${CameraStore.hostGuid(context.applicationContext).take(8)}…）")
    }

    fun log(message: String) {
        Log.i(TAG, message)
        val line = "${timeFormat.format(Date())}  $message"
        synchronized(logs) {
            logs.add(line)
            while (logs.size > MAX_LOG_LINES) logs.removeAt(0)
        }
        persistLog(line)
    }

    /** 日志镜像落盘（cacheDir/nicolink_debug.log），供 adb run-as 拉取排查 */
    private fun persistLog(line: String) {
        val context = appContext ?: return
        logExecutor.execute {
            runCatching {
                val file = java.io.File(context.cacheDir, "nicolink_debug.log")
                if (file.length() > 400_000) {
                    val text = file.readText()
                    file.writeText(text.substring(text.length / 4))
                }
                file.appendText(line + "\n")
            }
        }
    }

    fun clearLogs() {
        synchronized(logs) { logs.clear() }
    }

    fun localAddresses(): List<String> {
        val context = appContext ?: return emptyList()
        return CameraDiscovery(context, { }).localIpv4()
    }

    // ───────────────────────── 发现 ─────────────────────────

    fun discover(timeoutMs: Long = 7000, mode: String = "sta") {
        val context = appContext ?: return
        if (discovering) return
        scope.launch {
            discovering = true
            val discovery = CameraDiscovery(context, ::log, NetworkBinder.cameraHotspotNetwork(context), mode)
            try {
                val addresses = discovery.localIpv4()
                log("开始发现相机… 本机地址：${addresses.joinToString().ifEmpty { "（无 IPv4）" }}")
                val list = discovery.discover(timeoutMs)
                discovered = list
                if (list.isEmpty()) {
                    log("未发现相机：确认相机已连到同一热点、相机 Wi-Fi 已开启")
                } else {
                    log("发现 ${list.size} 个目标：${list.joinToString { "${it.host}:${it.port}" }}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("发现失败：${e.message}")
            } finally {
                discovering = false
            }
        }
    }

    // ───────────────────────── 连接 ─────────────────────────

    fun connect(host: String, port: Int = 15740, mode: String = connectMode) {
        userDisconnected = false // 用户再次手动连接：恢复自动重连能力
        if (state.connecting) return
        if (autoReconnecting) {
            log("正在自动寻找相机，请稍候")
            return
        }
        scope.launch {
            if (state.connected && state.host == host) {
                log("已连接 $host，跳过重复连接")
                return@launch
            }
            if (state.connected) disconnectInternal()
            state = state.copy(connecting = true, status = "连接中…", host = host)
            try {
                openCamera(host, port)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PtpInitRejectedException) {
                transport = null
                camera = null
                log("连接失败：${e.message}")
                state = CameraState(
                    host = host,
                    status = when (e.reason) {
                        1 -> "连接失败：相机的这个 Wi-Fi 配置文件已绑定其他主机（如 SnapBridge）" +
                            "或本机尚未注册。请在相机端删除该配置文件重新创建后连接，并执行「首次注册」"
                        else -> "连接失败：${e.message}"
                    },
                )
            } catch (e: Exception) {
                log("连接失败：${e.message}")
                transport = null
                camera = null
                state = CameraState(status = "连接失败：${e.message ?: "未知错误"}", host = host)
            }
        }
    }

    /**
     * 建链 + OpenSession + 读取机型信息，成功后接管为当前会话。
     * 受限（等待注册）会话里除注册外一切操作都被拒：少发指令，尽快引导注册。
     * 失败时清理半开连接后原样抛出。
     */
    private suspend fun openCamera(host: String, port: Int) {
        val context = appContext ?: throw PtpException("应用尚未初始化")
        // AP 模式（手机连相机热点）：流量必须走热点网络，否则走移动数据连不上相机。
        // 先按网段匹配；AP 下若因链路信息尚未就绪没匹配到，退回已识别的 Wi-Fi 网络绑定。
        // STA 模式保持原样：只按网段匹配，相机 IP 不在手机所连 WiFi 网段 → 不绑定 → 普通 socket。
        var network = NetworkBinder.networkForHost(context, host)
        if (network == null && connectMode == "ap") {
            network = NetworkBinder.cameraHotspotNetwork(context)?.network
            if (network != null) log("AP 兜底：按已识别 Wi-Fi 网络绑定 socket")
        }
        val newTransport = PtpIpTransport(::log)
        try {
            newTransport.connect(
                host = host,
                port = port,
                friendlyName = "NicoLink",
                guid = PtpCodec.guidFromHex(CameraStore.hostGuid(context)),
                network = network,
            )
            val cam = NikonCamera(newTransport, ::log)
            val info = cam.open()
            val restricted = info.restricted
            if (!restricted) cam.loadStorageIds()
            val battery = if (restricted) null else cam.batteryLevel()
            val free = if (restricted) null else cam.freeSpace()
            val registered = !restricted && CameraStore.isRegistered(context, info.serial)
            transport = newTransport
            camera = cam
            // P1 探测：保存 DeviceInfo 的 opcode 支持表，供能力探测报告使用（无副作用）
            deviceInfo = info
            state = CameraState(
                connected = true,
                model = info.model.ifBlank { newTransport.cameraName },
                serial = info.serial,
                firmware = info.firmware,
                host = host,
                battery = battery,
                freeBytes = free,
                registered = registered,
                restricted = restricted,
                status = when {
                    restricted -> "已连接 · 相机在等待首次注册，请尽快点下方「注册到相机」（等待超时相机会关闭 Wi-Fi）"
                    registered -> "已连接"
                    else -> "已连接 · 本机尚未注册，建议执行「首次注册」"
                },
            )
            val connectedCam = SavedCamera(
                id = "$host:$port",
                name = info.model.ifBlank { newTransport.cameraName }.ifBlank { "尼康相机" },
                host = host,
                serial = info.serial,
                lastConnectedAt = System.currentTimeMillis(),
            )
            CameraStore.save(context, connectedCam)
            beginConnectedTimer(connectedCam)
            // 新连接会话：允许本次会话第一张下载的照片重新解析快门次数（每次连接都刷新）
            shutterParsedThisSession = false
            onCameraConnected?.invoke(connectedCam)
            savedCameras = CameraStore.savedCameras(context)
            log(
                "连接成功：${state.model}（$host）" + when {
                    restricted -> "，受限会话等待注册"
                    !registered -> "，尚未注册"
                    else -> ""
                }
            )
            if (!restricted) refreshObjectsInternal(cam)
            startHeartbeat()
            startEventWatch()
        } catch (e: Throwable) {
            runCatching { newTransport.close() }
            throw e
        }
    }

    /** 连接成功后启动本次会话计时 */
    private fun beginConnectedTimer(cam: SavedCamera) {
        connectedTimerId = cam.id
        connectedTimerSerial = cam.serial
        connectedSince = System.currentTimeMillis()
    }

    /**
     * 结算连接时长增量，累加进本机记录并通知 App 层上报云端。
     * @param finalSettle true=断开时的最终结算（结算后清空会话身份）；false=心跳期的增量结算
     *   （结算后把起点重置为当前时间，身份保留，继续累计）。
     *
     * 为什么要增量结算：只在断开时结算的话，杀进程/崩溃/相机突然关热点等场景时长会全部丢失，
     * 陈列架永远显示「—」。心跳期每 30s 增量结算一次，即使异常退出最多丢 30s。
     */
    private fun settleConnectedTimer(finalSettle: Boolean) {
        val since = connectedSince
        if (since <= 0) return
        val elapsed = System.currentTimeMillis() - since
        if (finalSettle) {
            connectedSince = 0L
            connectedTimerId = ""
            connectedTimerSerial = ""
        } else {
            connectedSince = System.currentTimeMillis() // 重置起点继续累计
        }
        if (elapsed <= 0) return
        val context = appContext ?: return
        val updated = CameraStore.addConnectedTime(context, connectedTimerId, connectedTimerSerial, elapsed)
        if (updated != null) {
            savedCameras = CameraStore.savedCameras(context)
            if (finalSettle) onCameraDisconnected?.invoke(updated)
            else onShutterCountUpdated?.invoke(updated) // 心跳增量：复用上报回调把累计时长同步云端
        }
    }

    // ───────────────────────── 自动重连 ─────────────────────────

    var autoReconnecting by mutableStateOf(false)
        private set

    /** 用户主动断开后置位：抑制进入相机 Tab / 回前台的自动重连，直到再次手动连接或冷启动 */
    private var userDisconnected = false

    /**
     * 打开 App 后自动寻找上次的相机：扫描局域网 → 逐个候选建会话读序列号 →
     * 与已保存相机的序列号比对，匹配则保持连接，不匹配则断开换下一个。
     * IP 变了也没关系——识别靠序列号，不靠地址。
     */
    fun autoReconnect() {
        val context = appContext ?: return
        if (userDisconnected) return // 用户主动断开后不再自动重连
        if (state.connected || state.connecting || autoReconnecting) return
        val known = CameraStore.savedCameras(context)
            .map { it.serial }
            .filter { it.isNotBlank() }
            .toSet()
        if (known.isEmpty()) return
        // 同步置位：调用方 autoReconnectWithRetry 紧接着轮询 autoReconnecting 等待本次结束，
        // 若放在 launch 内置位会有竞态（launch 尚未执行→等待循环直接跳过→重试叠加）。
        autoReconnecting = true
        scope.launch {
            try {
                state = state.copy(status = "正在寻找上次的相机…")
                val discovery = CameraDiscovery(context, ::log, NetworkBinder.cameraHotspotNetwork(context), connectMode)
                val list = discovery.discover(6000)
                log("自动重连：发现 ${list.size} 个候选")
                for (item in list) {
                    try {
                        openCamera(item.host, item.port)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("自动重连：${item.host} 连接失败（${e.message}）")
                        continue
                    }
                    val serial = state.serial
                    if (serial.isNotBlank() && serial in known) {
                        log("自动重连成功：${state.model}（${item.host}）")
                        return@launch
                    }
                    // 不是上次那台：断开换下一个候选
                    log("自动重连：${item.host} 不是已保存的相机，断开")
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    runCatching { transport?.close() }
                    transport = null
                    camera = null
                    state = CameraState(status = "发现新相机 ${item.host}，可在连接页手动连接")
                }
                if (!state.connected) {
                    state = CameraState()
                    log("自动重连：没有匹配到已保存的相机")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("自动重连失败：${e.message}")
                if (!state.connected) state = CameraState()
            } finally {
                autoReconnecting = false
            }
        }
    }

    /**
     * 非正常断开后返回 App 的自动重连（最多 [times] 次，间隔递增）。
     * 每次调用 autoReconnect()（它内部有 autoReconnecting 去重），这里只负责多次尝试。
     */
    fun autoReconnectWithRetry(times: Int = 5) {
        if (state.connected) return
        scope.launch {
            for (attempt in 1..times.coerceAtLeast(1)) {
                if (state.connected) break
                log("返回App自动重连（$attempt/$times）")
                autoReconnect()
                // 等待本次尝试结束（autoReconnect 内部 launch，自动重连是异步的）
                var waited = 0
                while (autoReconnecting && waited < 120) {
                    delay(500)
                    waited += 1
                }
                if (state.connected) break
                // 退避：1s→2s→3s→4s，给网络恢复时间（对齐 LunaShare 看门狗的递增退避）
                delay(1000L * attempt)
            }
            if (!state.connected) log("返回App自动重连 $times 次均未成功")
        }
    }

    /**
     * App 回到前台（MainActivity.onResume 调用）。
     *
     * 只做两件事：更新前后台标记；若确实已断开则重连。
     * 不再主动 probe！实测发现：相机是手机开热点、相机连入（AP 模式），后台休眠时不响应
     * GET_DEVICE_PROP_VALUE 查询命令，但链路本身没断（心跳一直成功）。主动 probe 会把
     * 「命令超时」误判为「链路断开」，造成回前台反复断开重连（相机连接图标闪烁的根因）。
     * 断开判定统一交给后台心跳（连续 N 次失败才判定，已验证可靠），判定断开后 markDisconnected
     * 会在前台自动重连。
     */
    fun onAppResumed() {
        isAppInForeground = true
        if (!state.connected && !userDisconnected) {
            log("回前台：当前未连接，自动重连")
            autoReconnectWithRetry()
        }
    }

    /** App 退到后台（MainActivity.onPause 调用） */
    fun onAppPaused() {
        isAppInForeground = false
    }

    // ───────────────────────── 连接保活 ─────────────────────────

    /**
     * 每 5s 一次轻事务探测（顺带刷新电量）。传输层异常连续 3 次判定相机断开，
     * 避免相机关 Wi-Fi 后 App 还停在「已连接」状态。
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var failures = 0
            var ticks = 0
            while (currentCoroutineContext().isActive) {
                delay(5000) // 5s 心跳：保持更密集的网络活动，降低被 ROM 判定为空闲而冻结的概率
                val cam = camera ?: return@launch
                val (alive, battery) = cam.probe()
                if (alive) {
                    failures = 0
                    battery?.let { state = state.copy(battery = it) }
                    // 每 6 个心跳（30s）增量结算一次连接时长：即使杀进程/崩溃最多丢 30s，
                    // 陈列架能持续显示累计值，而非只在断开时才更新（断开才更新会因异常退出永远显示「—」）。
                    ticks++
                    if (ticks % 6 == 0) settleConnectedTimer(finalSettle = false)
                } else {
                    failures++
                    log("相机心跳失败（$failures/3）")
                    // 阈值 3：后台偶发探测失败不立即断开，给网络恢复/重连留机会（3×5s=15s 判定）
                    if (failures >= 3) {
                        markDisconnected("相机连接已断开（心跳无响应），返回App将自动重连")
                        return@launch
                    }
                }
            }
        }
    }

    /** 相机侧异常断开：清空会话与状态，UI 回到未连接（监看页自动退出） */
    private fun markDisconnected(reason: String) {
        settleConnectedTimer(finalSettle = true)
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopEventWatch()
        liveJob?.cancel()
        liveJob = null
        runCatching { transport?.close() }
        transport = null
        camera = null
        liveFrame = null
        liveActive = false
        liveError = null
        objects = emptyList()
        objectsError = null
        objectsDenied = false
        thumbs.clear()
        objectIdsCleared()
        deviceInfo = null
        capabilitiesReport = ""
        state = CameraState(status = reason)
        log(reason)
        // 异常断开后：若 App 在前台，立即自动重连。
        // 这是「回前台时还连着→onResume 重连被跳过→过一会心跳才断→之前无人重连」的根治点。
        // 后台断开不重连（省电、避免徒劳扫描），回前台时 onAppResumed 会再触发。
        if (isAppInForeground) {
            autoReconnectWithRetry()
        }
    }

    fun disconnect() {
        userDisconnected = true // 主动断开：抑制回相机 Tab / 回前台的自动重连
        scope.launch {
            stopLiveViewInternal()
            disconnectInternal()
            state = CameraState()
            objects = emptyList()
            thumbs.clear()
            objectIdsCleared()
            log("已断开相机")
        }
    }

    private suspend fun disconnectInternal() {
        settleConnectedTimer(finalSettle = true)
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopCaptureControls()
        videoJob?.cancel()
        videoJob = null
        stopEventWatch()
        val cam = camera
        runCatching { cam?.closeSession() }
        transport?.close()
        transport = null
        camera = null
        liveFrame = null
        liveActive = false
        liveError = null
        objects = emptyList()
        objectsError = null
        objectsDenied = false
        thumbs.clear()
        objectIdsCleared()
        // 相机只接受一个会话，重连前留出释放时间
        delay(1200)
    }

    private fun objectIdsCleared() {
        thumbFailed.clear()
        thumbPending.clear()
    }

    fun forgetSaved(id: String) {
        val context = appContext ?: return
        CameraStore.remove(context, id)
        savedCameras = CameraStore.savedCameras(context)
    }

    fun clearRegisterNote() {
        if (!registering) registerNote = null
    }

    // ───────────────────────── 首次注册 ─────────────────────────

    /**
     * 首次注册：把本机 GUID 绑进相机当前的 Wi-Fi 配置文件（SnapBridge 式配对）。
     * 需要先连上相机，且用户在相机旁配合屏幕确认。
     * 成功后相机会重启 Wi-Fi，这里自动按同一 GUID 重连。
     */
    fun registerCamera() {
        val context = appContext ?: return
        if (registering || state.connecting) return
        scope.launch {
            registering = true
            pairingCode = null
            try {
                val cam = camera
                if (cam == null || !state.connected) {
                    registerNote = "请先连接相机，再执行首次注册"
                    return@launch
                }
                val host = state.host
                val serial = state.serial
                val error = cam.registerHost(
                    onProgress = { registerNote = it },
                    onPairingCode = { pairingCode = it },
                )
                if (error != null) {
                    registerNote = error
                    return@launch
                }
                if (serial.isNotBlank()) CameraStore.markRegistered(context, serial)
                log("首次注册完成，等待相机重启 Wi-Fi 后重连…")
                registerNote = "注册成功！相机正在重启 Wi-Fi，自动重连中…"
                // 注册后相机会重置连接：直接断 socket（不发 CloseSession）
                runCatching { transport?.close() }
                transport = null
                camera = null
                liveFrame = null
                liveActive = false
                liveWanted = false
                objects = emptyList()
                thumbs.clear()
                objectIdsCleared()
                state = state.copy(connected = false, status = "注册完成，重连中…")
                val reconnected = reconnectAfterRegistration(host)
                if (reconnected) {
                    // 注册前受限 DeviceInfo 可能拿不到序列号，用重连后的信息补记一次
                    state.serial.takeIf { it.isNotBlank() }?.let { CameraStore.markRegistered(context, it) }
                    state = state.copy(registered = true, status = "已连接（已注册）")
                    log("注册后重连成功")
                    registerNote = null
                } else {
                    registerNote = "注册已完成，但自动重连没成功：等相机 Wi-Fi 稳定后回到连接页手动连接即可"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registerNote = "注册失败：${e.message ?: "未知错误"}"
                log("注册失败：${e.message}")
            } finally {
                registering = false
            }
        }
    }

    /** 注册后相机重启 Wi-Fi，按同一 GUID 轮询重连（每 4s 一次，最长约 32s） */
    private suspend fun reconnectAfterRegistration(host: String): Boolean {
        repeat(8) {
            delay(4000)
            try {
                openCamera(host, 15740)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("注册后重连（第 ${it + 1} 次）失败：${e.message}")
            }
        }
        return false
    }

    // ───────────────────────── 相机照片 ─────────────────────────

    fun refreshObjects() {
        val cam = camera ?: return
        if (objectsLoading) return
        scope.launch { refreshObjectsInternal(cam) }
    }

    private suspend fun refreshObjectsInternal(cam: NikonCamera) {
        objectsLoading = true
        objectsError = null
        objectsDenied = false
        syncDownloadedSet()
        try {
            val list = cam.listObjects()
            objects = list
            objectsDenied = list.isEmpty() && cam.lastListAccessDenied
            log("读到相机照片 ${list.size} 张")
            val free = cam.freeSpace()
            if (free != null) state = state.copy(freeBytes = free)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            objectsError = e.message ?: "未知错误"
            log("读取相机照片失败：${e.message}")
        } finally {
            objectsLoading = false
        }
    }

    /** 相机缩略图按最大边 320px 采样解码：网格只需小图，避免大量全尺寸位图拖慢滚动/切页 */
    private fun decodeThumbnail(bytes: ByteArray, maxPx: Int = 320): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxPx)
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return bitmap.asImageBitmap()
    }

    fun requestThumbnail(handle: Int) {
        if (thumbs.containsKey(handle)) return
        if (thumbFailed.contains(handle)) return
        if (!thumbPending.add(handle)) return
        scope.launch {
            try {
                val cam = camera ?: return@launch
                val obj = objects.firstOrNull { it.handle == handle }
                // 视频的 0x9530 机身预览通常无效：已下载的直接用本地文件首帧
                if (obj?.isVideo == true) {
                    val local = appContext?.let { PhotoRepository.videoThumbnail(it, obj.filename) }
                    if (local != null) {
                        if (thumbs.size > 300) thumbs.clear()
                        thumbs[handle] = local
                        return@launch
                    }
                }
                // RAW(NEF) 走文件头内嵌缩略图提速；JPG 必须走 0x9530（文件头 SOI 解析会得到无效 JPEG）
                val isRaw = obj?.isRaw == true
                val bytes = cam.thumbnail(handle, preferEmbedded = isRaw) ?: run {
                    thumbFailed.add(handle)
                    return@launch
                }
                val bitmap = decodeThumbnail(bytes)
                if (bitmap == null) {
                    thumbFailed.add(handle)
                } else {
                    if (thumbs.size > 300) thumbs.clear()
                    thumbs[handle] = bitmap
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thumbFailed.add(handle)
                log("缩略图 $handle 读取失败：${e.message}")
            } finally {
                thumbPending.remove(handle)
            }
        }
    }

    fun download(obj: CameraObject) {
        val cam = camera ?: return
        scope.launch { downloadInternal(cam, obj) }
    }

    /** 删除相机内照片：结果通过回调返回（成功/失败描述），成功后刷新列表 */
    fun deletePhoto(obj: CameraObject, onResult: (Boolean, String) -> Unit) {
        val cam = camera ?: run { onResult(false, "相机未连接"); return }
        scope.launch {
            val (ok, msg) = cam.deleteObject(obj.handle)
            withContext(Dispatchers.Main) {
                if (ok) {
                    objects = objects.filter { it.handle != obj.handle }
                    downloadSet = downloadSet - obj.handle
                    log("已删除相机照片 ${obj.filename}")
                }
                onResult(ok, msg)
            }
        }
    }

    /** 批量删除相机内照片：串行执行，回调(成功数, 总数, 提示)，成功后刷新列表 */
    fun deletePhotos(
        objs: List<CameraObject>,
        onProgress: (Int) -> Unit = {},
        onResult: (Int, Int, String) -> Unit,
    ) {
        val cam = camera ?: run { onResult(0, objs.size, "相机未连接"); return }
        scope.launch {
            var ok = 0
            val removed = mutableSetOf<Int>()
            for (obj in objs) {
                val (success, msg) = cam.deleteObject(obj.handle)
                withContext(Dispatchers.Main) { onProgress(ok + if (success) 1 else 0) }
                if (success) {
                    ok++
                    removed += obj.handle
                } else {
                    log("批量删除 ${obj.filename} 失败：$msg")
                }
            }
            withContext(Dispatchers.Main) {
                if (removed.isNotEmpty()) {
                    objects = objects.filterNot { it.handle in removed }
                    downloadSet = downloadSet - removed
                }
                onResult(ok, objs.size, if (ok == objs.size) "已删除 $ok 张" else "成功 $ok/${objs.size} 张")
            }
        }
    }

    /** 批量下载到本机：串行复用下载互斥通道，逐张更新进度 */
    fun downloadPhotos(objs: List<CameraObject>) {
        val cam = camera ?: return
        scope.launch {
            for (obj in objs) downloadInternal(cam, obj)
        }
    }

    /** 全局下载互斥：手动下载 / 拍照自动下载 / 相机触发自动拉取共用一条串行通道 */
    private val downloadMutex = Mutex()

    /** 正在下载的句柄（自动拉取去重：手动/拍照路径已在传的对象不再入队） */
    private val downloadingHandles = mutableSetOf<Int>()

    private suspend fun downloadInternal(cam: NikonCamera, obj: CameraObject) {
        // 只把下载本身放进串行锁；落盘后返回路径，在锁外触发自动显影，
        // 让显影触发逻辑（预设解析等）不阻塞传输队列，下载与显影能流水线并行。
        val savedPath = downloadMutex.withLock {
            val context = appContext ?: return@withLock null
            downloadingHandles.add(obj.handle)
            try {
                downloadInternalLocked(cam, obj, context)
            } finally {
                downloadingHandles.remove(obj.handle)
            }
        }
        if (savedPath != null && !obj.isVideo) {
            autoDevelopHook?.invoke(savedPath, obj.filename)
            // 快门次数：每次连接会话只在「第一张成功下载的照片」解析一次（连接成功时已重置标志）。
            // 尼康机身不通过 PTP 提供快门次数，只能从照片 EXIF MakerNote 解析（只读前 1MB、IO 线程）。
            // 防串相机：拍摄瞬间快照 serial/host，异步严格按 serial 匹配，不引用可变的 state。
            val context = appContext
            val shotSerial = state.serial
            val shotHost = state.host
            if (context != null && shotSerial.isNotBlank() && !shutterParsedThisSession) {
                shutterParsedThisSession = true // 立即置位：同一会话后续照片不再解析，避免重复读盘
                val camId = "$shotHost:${com.filmlens.nicolink.camera.ptp.PtpIp.PORT}"
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val count = NikonShutterCountParser.parseFile(java.io.File(savedPath))?.shutterCount ?: return@launch
                    val updated = CameraStore.setShutterCount(context, camId, shotSerial, count.toLong()) ?: return@launch
                    log("快门次数：${updated.name} = $count（EXIF MakerNote）")
                    onShutterCountUpdated?.invoke(updated)
                }
            }
        }
    }

    /** 返回落盘后的绝对路径（成功），失败/跳过返回 null */
    private suspend fun downloadInternalLocked(cam: NikonCamera, obj: CameraObject, context: Context): String? {
        transfer = TransferProgress(obj.filename, 0, obj.size)
        log("开始下载 ${obj.filename}（${obj.size / 1024} KB）")
        val pending = try {
            CameraMedia.create(context, obj.filename, obj.mimeType)
        } catch (e: Exception) {
            log("创建相册条目失败：${e.message}")
            transfer = TransferProgress(obj.filename, 0, obj.size, finished = true, error = e.message)
            return null
        }
        try {
            pending.openOutput().use { out ->
                cam.download(
                    obj,
                    sink = DataSink { buffer, offset, length -> out.write(buffer, offset, length) },
                    onProgress = { done, total ->
                        transfer = TransferProgress(obj.filename, done, total)
                    },
                )
            }
            pending.commit()
            transfer = TransferProgress(obj.filename, obj.size, obj.size, finished = true)
            downloadedTick++
            onPhotoDownloaded?.invoke()
            if (state.serial.isNotBlank()) CameraStore.markDownloaded(context, state.serial, obj.handle)
            downloadSet = downloadSet + obj.handle
            log("已保存到相册：${pending.displayPath}（落盘完成，交显影队列）")
            return pending.displayPath
        } catch (e: CancellationException) {
            pending.abort()
            throw e
        } catch (e: Exception) {
            pending.abort()
            log("下载失败：${e.message}")
            transfer = TransferProgress(obj.filename, 0, obj.size, finished = true, error = e.message)
            return null
        }
    }

    // ───────────────────────── 自动传输（相机触发上传 → App 自动拉取） ─────────────────────────

    /**
     * 相机把照片「推」给主机在 PTP/IP 里是事件通知 + 主机拉取：
     * 尼康私有 0xC101 ObjectAddedInSDRAM（params[0]=句柄）与标准 0x4002 ObjectAdded、
     * 完成信号 0x400D/0xC102 到达后，走 STA 直读通道把新对象拉落盘——
     * 覆盖相机「自动发送到智能设备/计算机」与图库「标记上传」两种触发。
     */
    data class AutoTransferState(
        val active: Boolean = false,
        val currentName: String = "",
        val remaining: Int = 0,
    )

    var autoTransfer by mutableStateOf(AutoTransferState())
        private set

    private const val EV_NIKON_OBJECT_ADDED = 0xC101
    private const val EV_OBJECT_ADDED = 0x4002
    private const val EV_CAPTURE_COMPLETE = 0x400D
    private const val EV_NIKON_CAPTURE_COMPLETE = 0xC102

    private val autoPullQueue = ArrayDeque<Int>()
    private val autoPullInFlight = mutableSetOf<Int>()
    private var autoPullJob: Job? = null
    private var eventJob: Job? = null
    private var idleWatchJob: Job? = null
    private var reconcilePending = false

    /** 连接成功后启动事件监听 + 低频对账轮询（覆盖不发事件的「标记上传」） */
    private fun startEventWatch() {
        eventJob?.cancel()
        idleWatchJob?.cancel()
        val t = transport ?: return
        eventJob = scope.launch {
            runCatching {
                t.events.collect { ev ->
                    // 记录所有事件码（排查「上传至计算机」触发的事件类型：相机手动上传可能发非标准码）
                    log("PTP 事件 0x${ev.code.toString(16).uppercase()} params=${ev.params.take(4)}")
                    when (ev.code) {
                        EV_NIKON_OBJECT_ADDED -> {
                            val h = ev.params.firstOrNull() ?: 0
                            if (h > 0) enqueueAutoPull(listOf(h)) else scheduleReconcile()
                        }
                        EV_OBJECT_ADDED, EV_CAPTURE_COMPLETE, EV_NIKON_CAPTURE_COMPLETE -> {
                            // 标准 ObjectAdded 事件带对象句柄时直接拉取，避免依赖对账延迟
                            val h = ev.params.firstOrNull() ?: 0
                            if (h > 0) enqueueAutoPull(listOf(h)) else scheduleReconcile()
                        }
                        // 相机「上传至计算机」可能发厂商私有事件：任何带对象句柄的事件都尝试对账，
                        // 确保主机尽快拉取，避免相机端卡在「正在上传」等待状态。
                        else -> scheduleReconcile()
                    }
                }
            }.onFailure { if (state.connected) log("事件通道监听结束：${it.message}") }
        }
        idleWatchJob = scope.launch {
            while (isActive && state.connected) {
                delay(4_000) // 缩短到 4s：相机手动「上传至计算机」无事件时也能尽快对账拉取，避免相机端卡在「正在上传」
                scheduleReconcile()
            }
        }
        log("自动传输监听已启动（事件 + 4s 对账）")
    }

    private fun stopEventWatch() {
        eventJob?.cancel()
        eventJob = null
        idleWatchJob?.cancel()
        idleWatchJob = null
        autoPullJob?.cancel()
        autoPullJob = null
        autoPullQueue.clear()
        autoPullInFlight.clear()
        reconcilePending = false
        autoTransfer = AutoTransferState()
    }

    private fun knownMediaHandles(): Set<Int> =
        objects.map { it.handle }.toSet() + downloadSet + autoPullQueue.toSet() +
            autoPullInFlight + downloadingHandles + (camera?.knownMediaHandles ?: emptySet())

    /** 事件/轮询触发的对账：0x9434 全卡元数据对比已知集合，新媒体句柄入拉取队列 */
    private fun scheduleReconcile() {
        if (!state.connected || state.restricted || reconcilePending) return
        reconcilePending = true
        scope.launch {
            try {
                delay(1500)
                val cam = camera ?: return@launch
                if (!state.connected || state.restricted) return@launch
                val storage = cam.storageIds.firstOrNull() ?: return@launch
                val meta = runCatching { cam.staMeta(storage) }.getOrNull().orEmpty()
                val known = knownMediaHandles()
                val fresh = meta.keys.filter { it !in known && cam.staFormat(it) != null }
                if (fresh.isNotEmpty()) {
                    log("自动传输：对账发现 ${fresh.size} 个新对象")
                    enqueueAutoPull(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("自动传输对账失败：${e.message}")
            } finally {
                reconcilePending = false
            }
        }
    }

    private fun enqueueAutoPull(handles: List<Int>) {
        if (!state.connected || state.restricted) return
        var added = 0
        for (h in handles) {
            if (h <= 0) continue
            if (h in autoPullQueue || h in autoPullInFlight || h in downloadingHandles) continue
            if (h in downloadSet) continue
            autoPullQueue.add(h)
            added++
        }
        if (added == 0) return
        autoTransfer = autoTransfer.copy(active = true, remaining = autoPullQueue.size)
        log("自动传输：入队 $added 个对象（队列 ${autoPullQueue.size}）")
        if (autoPullJob?.isActive != true) {
            autoPullJob = scope.launch { processAutoPullQueue() }
        }
    }

    private suspend fun processAutoPullQueue() {
        while (true) {
            val handle = autoPullQueue.removeFirstOrNull() ?: break
            autoPullInFlight.add(handle)
            try {
                val cam = camera ?: break
                if (!state.connected) break
                val storage = cam.storageIds.firstOrNull() ?: break
                if (handle in downloadSet) continue
                // 等相机写完卡：0x9421 连续两次读数一致且 >0（RAW 大文件要写几秒）
                var lastSize = -1L
                var stable = 0
                var size = 0L
                val deadline = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < deadline) {
                    size = runCatching { cam.objectSize(handle) }.getOrNull() ?: 0L
                    if (size > 0 && size == lastSize) {
                        stable++
                        if (stable >= 2) break
                    } else {
                        stable = 0
                    }
                    lastSize = size
                    delay(700)
                }
                if (size <= 0) {
                    log("自动传输：handle=$handle 等待写卡超时，跳过")
                    continue
                }
                val ext = cam.staFormat(handle) ?: run {
                    log("自动传输：handle=$handle 非媒体句柄，跳过")
                    continue
                }
                val meta = runCatching { cam.staMeta(storage) }.getOrNull().orEmpty()
                val m = meta[handle]
                val obj = CameraObject(
                    handle = handle,
                    storageId = storage,
                    filename = "DSC_%04d.$ext".format(handle and 0xFFFF),
                    size = m?.first?.takeIf { it > 0 } ?: size,
                    width = 0,
                    height = 0,
                    formatCode = if (ext == "nef") 0xB103 else 0x3801,
                    captureDate = m?.second ?: "",
                )
                if (objects.none { it.handle == handle }) {
                    objects = (objects + obj).sortedByDescending { it.captureDate }
                }
                autoTransfer = autoTransfer.copy(
                    active = true,
                    currentName = obj.filename,
                    remaining = autoPullQueue.size + 1,
                )
                downloadInternal(cam, obj)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("自动传输：handle=$handle 拉取失败（${e.message}）")
            } finally {
                autoPullInFlight.remove(handle)
                autoTransfer = autoTransfer.copy(remaining = autoPullQueue.size)
            }
        }
        autoTransfer = AutoTransferState()
    }

    fun dismissTransfer() {
        transfer = null
    }

    // ───────────────────────── 拍照 ─────────────────────────

    /**
     * 快门统一入口：视频模式开/停录；照片模式按定时设置倒计时后拍一张。
     * 间隔循环直接调 [captureOnce]，不走定时，避免每次都叠加倒计时。
     */
    fun capture() {
        if (!state.connected || state.restricted) return
        if (captureMode == "video") {
            toggleVideoRecording()
            return
        }
        if (captureMode == "bulb") {
            startBulb()
            return
        }
        timerJob?.cancel()
        if (timerSeconds > 0) {
            timerJob = scope.launch {
                for (s in timerSeconds downTo 1) {
                    captureCountdown = s
                    delay(1000)
                }
                captureCountdown = 0
                captureOnce()
            }
        } else {
            scope.launch { captureOnce() }
        }
    }

    /** 单次拍照（含拍前基线等新照片 + 下载 + 刷新列表），串行闸门防与间隔/手动重叠 */
    private suspend fun captureOnce() {
        val cam = camera ?: return
        if (!captureGate.tryLock()) {
            log("拍照进行中，忽略重复触发")
            return
        }
        try {
            val storage = objects.firstOrNull()?.storageId ?: cam.storageIds.firstOrNull()
            if (storage == null) {
                log("没有可用存储卡，无法拍照")
                return
            }
            log("发送拍照指令…")
            val captured = try {
                cam.captureAndWait(storage, keepAppMode = liveActive)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("拍照失败：${e.message}")
                null
            }
            if (captured == null) return
            log("拍照完成：${captured.filename}")
            downloadInternal(cam, captured)
            refreshObjectsInternal(cam)
        } finally {
            captureGate.unlock()
        }
    }

    /** 设置定时秒数：0=关（同时取消正在倒计时）；>0 会停止间隔循环，避免两种自动拍摄叠加 */
    fun setTimer(seconds: Int) {
        timerSeconds = seconds.coerceIn(0, 30)
        timerJob?.cancel()
        timerJob = null
        captureCountdown = 0
        if (timerSeconds > 0 && intervalJob?.isActive == true) stopInterval()
        captureMessage = if (timerSeconds == 0) "定时拍摄：关" else "定时拍摄：${timerSeconds} 秒"
        log(captureMessage!!)
    }

    /** 设置间隔秒数：0=关（停止循环）；>0 立即启动循环 */
    fun setInterval(seconds: Int) {
        val s = seconds.coerceIn(0, 10)
        if (s == 0) stopInterval() else startInterval(s)
    }

    private fun startInterval(seconds: Int) {
        timerJob?.cancel()
        captureCountdown = 0
        intervalTaken = 0
        intervalSeconds = seconds
        intervalJob?.cancel()
        intervalJob = scope.launch {
            captureMessage = "间隔拍摄开始：每 ${seconds}s"
            while (currentCoroutineContext().isActive && state.connected) {
                captureOnce()
                intervalTaken++
                captureMessage = "间隔 ${seconds}s · 已拍 $intervalTaken 张（点当前间隔停止）"
                delay(seconds * 1000L)
            }
        }
        log("间隔拍摄启动：每 ${seconds}s")
    }

    fun stopInterval() {
        val taken = intervalTaken
        intervalJob?.cancel()
        intervalJob = null
        intervalTaken = 0
        // 关键：清掉间隔秒数，否则按钮仍高亮/文案仍显示「间隔 2s」，与实际已停止不符
        intervalSeconds = 0
        captureMessage = if (taken > 0) "间隔拍摄已停止（共 $taken 张）" else "间隔拍摄：关"
        log("间隔拍摄停止（共 $taken 张）")
    }

    /** 拍摄模式切换：photo / video / bulb；切走时停掉上一模式正在跑的任务 */
    fun switchCaptureMode(mode: String) {
        val next = if (mode in listOf("photo", "video", "bulb")) mode else "photo"
        if (next != "photo") {
            if (intervalJob?.isActive == true) stopInterval()
            timerJob?.cancel()
            timerJob = null
            captureCountdown = 0
        }
        if (captureMode == "bulb" && next != "bulb") {
            bulbJob?.cancel()
            bulbJob = null
            bulbActive = false
            captureCountdown = 0
        }
        if (captureMode == "video" && next != "video" && videoRecording) {
            val cam = camera
            val storage = videoStorageId
            videoRecording = false
            scope.launch {
                runCatching { if (cam != null && storage > 0) cam.endMovieRec(storage, timeoutMs = 8000) else null }
                    .onSuccess { obj -> if (obj != null && cam != null) downloadInternal(cam, obj) }
                    .onFailure { log("退出视频模式结束录像失败：${it.message}") }
            }
        }
        captureMode = next
        captureMessage = when (next) {
            "video" -> "视频模式：点快门开录，再点停录"
            "bulb" -> "B 门模式：机身拨盘切到 B 档，点快门开曝"
            else -> "照片模式"
        }
        log(captureMessage!!)
    }

    /** 0x920A/0x920B 录像开停；结束后找新视频落盘（视频不进显影队列） */
    fun toggleVideoRecording() {
        val cam = camera ?: return
        if (videoJob?.isActive == true) return
        videoJob = scope.launch {
            try {
                if (videoRecording) {
                    val obj = cam.endMovieRec(videoStorageId)
                    videoRecording = false
                    captureMessage = if (obj != null) "录像已保存：${obj.filename}" else "录像结束，未找到新视频文件"
                    if (obj != null) {
                        downloadInternal(cam, obj)
                        refreshObjectsInternal(cam)
                    }
                } else {
                    val storage = objects.firstOrNull()?.storageId ?: cam.storageIds.firstOrNull()
                    if (storage == null) {
                        captureMessage = "没有可用存储卡，无法录像"
                        return@launch
                    }
                    val ok = cam.startMovieRec(storage)
                    if (ok) {
                        videoStorageId = storage
                        videoRecording = true
                        captureMessage = "录像中…再次点按结束"
                    } else {
                        captureMessage = "录像启动失败"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("录像操作失败：${e.message}")
                videoRecording = false
                captureMessage = "录像失败：${e.message}"
            } finally {
                videoJob = null
            }
        }
    }

    /** 画面模式切换：0x90C2(1)=遥控画面 / 0x90C2(0)=联动画面 */
    fun toggleDisplayMode() {
        val cam = camera ?: return
        scope.launch {
            val target = !displayRemote
            val ok = try {
                cam.setCameraDisplayMode(target)
            } catch (e: Exception) {
                log("画面模式切换失败：${e.message}")
                false
            }
            if (ok) {
                displayRemote = target
                captureMessage = if (target) "遥控画面：相机屏熄" else "联动画面：相机屏同显"
            } else {
                captureMessage = "画面模式切换失败，请重试"
            }
        }
    }

    fun clearCaptureMessage() {
        if (captureMessage != null) captureMessage = null
    }

    // ───────────────────────── P3：触屏对焦 / B 门 ─────────────────────────

    /** 触屏对焦：用当前取景帧宽高把归一化点击映射到 AF 坐标域，发 0x9205 + 0x90C1 */
    fun touchFocus(nx: Float, ny: Float) {
        val cam = camera ?: return
        focusJob?.cancel()
        focusJob = scope.launch {
            val frame = liveFrame
            val ok = try {
                cam.touchFocus(nx, ny, frame?.width ?: 0, frame?.height ?: 0)
            } catch (e: Exception) {
                log("触屏对焦失败：${e.message}")
                false
            }
            val point = nx to ny
            focusPoint = point
            captureMessage = if (ok) "已对焦" else "对焦失败（请确认机身在 AF 模式）"
            delay(1800)
            if (focusPoint == point) focusPoint = null
        }
    }

    /** B 门曝光时长（5/10/20/30/60 秒） */
    fun chooseBulbSeconds(seconds: Int) {
        bulbSeconds = seconds.coerceIn(1, 300)
        captureMessage = "B 门曝光 ${bulbSeconds}s（机身拨盘切到 B 档）"
        log(captureMessage!!)
    }

    /** B 门：0x9207 开曝 → App 计时 → 0x920C 收门 → 找新照片下载（机身须先切 B 档） */
    private fun startBulb() {
        val cam = camera ?: return
        if (bulbJob?.isActive == true) return
        bulbJob = scope.launch {
            val storage = objects.firstOrNull()?.storageId ?: cam.storageIds.firstOrNull()
            if (storage == null) {
                captureMessage = "没有可用存储卡，无法 B 门拍摄"
                return@launch
            }
            bulbActive = true
            captureMessage = "B 门开曝 ${bulbSeconds}s（机身需切到 B 档）"
            try {
                val obj = cam.bulbExposure(storage, bulbSeconds, onTick = { s ->
                    captureCountdown = s
                    if (s == 0) captureMessage = "B 门收门…"
                }, keepAppMode = liveActive)
                captureCountdown = 0
                if (obj != null) {
                    log("B 门拍摄完成：${obj.filename}")
                    downloadInternal(cam, obj)
                    refreshObjectsInternal(cam)
                    captureMessage = "B 门完成：${obj.filename}"
                } else {
                    captureMessage = "B 门未找到新照片（请确认机身拨盘在 B 档）"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("B 门失败：${e.message}")
                captureMessage = "B 门失败：${e.message}"
            } finally {
                bulbActive = false
                bulbJob = null
                captureCountdown = 0
            }
        }
    }

    /** 离开监看页 / 断开连接时收尾所有拍摄增强任务，并把 UI 状态复位到与相机一致 */
    private fun stopCaptureControls() {
        timerJob?.cancel()
        timerJob = null
        captureCountdown = 0
        intervalJob?.cancel()
        intervalJob = null
        intervalTaken = 0
        intervalSeconds = 0
        timerSeconds = 0
        videoRecording = false
        bulbJob?.cancel()
        bulbJob = null
        bulbActive = false
        focusPoint = null
        // endLiveView() 会发 0x90C2(0) 恢复联动画面，UI 同步复位
        displayRemote = false
        captureMode = "photo"
        captureMessage = null
    }

    // ───────────────────────── 实时取景 ─────────────────────────

    fun startLiveView() {
        val cam = camera ?: return
        if (liveJob?.isActive == true) return
        liveWanted = true
        liveJob = scope.launch { runLiveView(cam) }
        // 水平仪独立高频轮询（~8Hz）
        levelJob?.cancel()
        levelJob = scope.launch {
            while (currentCoroutineContext().isActive && liveWanted) {
                val l = cam.readLevelRaw(PtpProp.NIKON_ANGLE_LEVEL)
                val p = cam.readLevelRaw(PtpProp.NIKON_ANGLE_PITCHING)
                val y = cam.readLevelRaw(PtpProp.NIKON_ANGLE_YAWING)
                if (l != null || p != null) levelRaw = LevelRaw(l, p, y)
                delay(120)
            }
        }
    }

    fun stopLiveView() {
        scope.launch { stopLiveViewInternal() }
    }

    private suspend fun stopLiveViewInternal() {
        liveWanted = false
        // 离开监看页时若正在录像，先体面收门并把视频拉下来（短超时，不卡 UI）
        if (videoRecording) {
            val cam = camera
            val storage = videoStorageId
            scope.launch {
                runCatching { if (cam != null && storage > 0) cam.endMovieRec(storage, timeoutMs = 8000) else null }
                    .onSuccess { obj ->
                        if (obj != null && cam != null) {
                            log("离开监看页，录像已保存：${obj.filename}")
                            downloadInternal(cam, obj)
                        }
                    }
                    .onFailure { log("离开监看页结束录像失败：${it.message}") }
            }
        }
        stopCaptureControls()
        levelJob?.cancel()
        levelJob = null
        val job = liveJob
        liveJob = null
        if (job != null) withTimeoutOrNull(4000) { job.join() }
        runCatching { camera?.endLiveView() }
        liveActive = false
        liveFrame = null
        liveError = null
        liveHistogram = HistogramData.EMPTY
        exposureInfo = ExposureInfo()
        levelRaw = LevelRaw()
    }

    private suspend fun runLiveView(cam: NikonCamera) {
        try {
            log("启动实时取景…")
            // 启动失败重试 3 次，之后停下并把原因摆到监看页
            var attempts = 0
            while (liveWanted && currentCoroutineContext().isActive) {
                try {
                    cam.startLiveView()
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempts++
                    val reason = e.message ?: "未知错误"
                    log("取景启动失败（第 $attempts 次）：$reason")
                    if (attempts >= 3) {
                        liveError = reason
                        return
                    }
                    delay(1000)
                }
            }
            liveActive = true
            liveError = null
            // startLiveView() 内部发了 0x90C2(1)：相机屏熄（遥控画面），按钮应为选中态
            displayRemote = true
            log("实时取景已启动")
            var exposureTick = 0L
            while (liveWanted && currentCoroutineContext().isActive) {
                val jpeg = cam.liveViewJpeg()
                if (jpeg == null) {
                    delay(120)
                    continue
                }
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                if (bmp != null) {
                    // 直方图：从每帧降采样计算（每隔几像素取样以省 CPU），首末桶统计死黑/溢出
                    liveHistogram = computeHistogram(bmp)
                    liveFrame = bmp.asImageBitmap()
                }
                // 拍摄参数：每 ~1s 刷新一次（避免高频 0x1015 抢占取帧的 Mutex）
                val now = System.currentTimeMillis()
                if (now - exposureTick > 1000L) {
                    exposureTick = now
                    exposureInfo = cam.readExposure()
                }
                delay(16)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            liveError = e.message ?: "未知错误"
            log("实时取景中断：${e.message}")
        } finally {
            liveActive = false
        }
    }

    /**
     * 从取景帧计算 256 桶亮度直方图。降采样（步长随分辨率）避免每帧全像素遍历拖慢取景。
     * Rec.601 亮度权重与 JPEG Y 分量一致；返回 bins + 峰值供 UI 归一化。
     */
    private fun computeHistogram(bmp: android.graphics.Bitmap): HistogramData {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return HistogramData.EMPTY
        // 目标最多约 12000 个采样点
        val total = w * h
        val step = maxOf(1, (total / 12000).toInt())
        val pixels = IntArray(total)
        return try {
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val bins = IntArray(256)
            var i = 0
            var peak = 1
            while (i < total) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = (r * 299 + g * 587 + b * 114) / 1000
                val idx = luma.coerceIn(0, 255)
                val v = ++bins[idx]
                if (v > peak) peak = v
                i += step
            }
            HistogramData(bins, peak)
        } catch (e: Throwable) {
            HistogramData.EMPTY
        }
    }
}
