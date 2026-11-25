package com.filmlens.nicolink.camera

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * 相机发现：
 * 1) mDNS 浏览 `_ptp._tcp`（CIPA DC-X005 服务类型）
 * 2) 当前 IPv4 子网 15740 端口扫描（热点下最可靠）
 *
 * [hotspot] 为疑似相机热点的 Wi-Fi 网络（无互联网能力）：仅当目标地址落在
 * 它自己的网段内才用绑定 socket，其他网段（如 STA 的手机热点）走普通 socket。
 *
 * 只做「发现」，不发 PTP 字节——避免一个探测把相机上已建立的会话顶掉。
 */
class CameraDiscovery(
    private val context: Context,
    private val log: (String) -> Unit,
    private val hotspot: NetworkBinder.CameraHotspotNetwork? = null,
    private val mode: String = "sta",
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolving = ConcurrentHashMap<String, Boolean>()

    /** 本机所有活跃 IPv4 地址（连接页展示，方便确认手机热点网段） */
    fun localIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .filter { it.isNotEmpty() }
            .distinct()
    }.getOrDefault(emptyList())

    /**
     * 并行跑 mDNS 与子网扫描，[timeoutMs] 内收集到的相机去重返回。
     */
    suspend fun discover(timeoutMs: Long = 6000): List<DiscoveredCamera> = coroutineScope {
        val found = ConcurrentHashMap<String, DiscoveredCamera>()
        // AP 模式：mDNS 跑在默认网络（蜂窝）上发现不了相机热点，跳过；只靠直探 192.168.1.1
        val mdns = if (mode == "ap") null else launch { browseMdns(found) }
        val scan = launch(Dispatchers.IO) { scanSubnets(found) }
        delay(timeoutMs)
        mdns?.cancel()
        scan.cancel()
        stopMdns()
        found.values.sortedWith(compareBy({ it.via != "mdns" }, { it.host }))
    }

    /** 已保存相机的存活探测（只连端口，不发 PTP） */
    suspend fun isReachable(host: String, port: Int = 15740, timeoutMs: Int = 800): Boolean =
        withContext(Dispatchers.IO) {
            try {
                newSocket(host).use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun browseMdns(found: ConcurrentHashMap<String, DiscoveredCamera>) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                log("mDNS：开始浏览 _ptp._tcp")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("mDNS 启动失败（错误码 $errorCode）")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("mDNS 停止失败（错误码 $errorCode）")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                log("mDNS：停止浏览")
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                log("mDNS 服务消失：${info.serviceName}")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (resolving.putIfAbsent(info.serviceName, true) != null) return
                log("mDNS 发现服务：${info.serviceName}")
                try {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            log("mDNS 解析失败：${serviceInfo.serviceName}（$errorCode）")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val address = serviceInfo.host?.hostAddress ?: return
                            val port = if (serviceInfo.port > 0) serviceInfo.port else 15740
                            found[address] = DiscoveredCamera(
                                host = address,
                                port = port,
                                name = serviceInfo.serviceName,
                                via = "mdns",
                            )
                            log("mDNS 解析到相机：$address:$port（${serviceInfo.serviceName}）")
                        }
                    })
                } catch (e: Exception) {
                    log("mDNS 解析异常：${e.message}")
                }
            }
        }
        discoveryListener = listener
        mainHandler.post {
            try {
                nsd.discoverServices("_ptp._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                log("mDNS 启动异常：${e.message}")
            }
        }
    }

    private fun stopMdns() {
        val nsd = nsdManager ?: return
        val listener = discoveryListener ?: return
        mainHandler.post {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        nsdManager = null
        discoveryListener = null
    }

    private suspend fun scanSubnets(found: ConcurrentHashMap<String, DiscoveredCamera>) = coroutineScope {
        // 只有 AP 模式（手机连相机热点）才直探相机的固定地址；STA 下手机自己是热点，
        // 相机 IP 由 DHCP 分配且不在 192.168.1.1，直探+提前返回会直接漏掉相机。
        if (mode == "ap") {
            // AP 模式相机热点地址固定 192.168.1.1，直接列为候选，**绝不发探测包**：
            // 尼康相机在 AP 下是 WiFi 服务端，任何到 15740 的 TCP 连接都被当作"主机要连接"，
            // 探测连上又不发 PTP 握手就断开 → 相机判定"无法连接"并关闭热点（用户实测）。
            // 只有用户真正点连接时才发起完整 PTP 握手。STA 模式不受影响（仍走下面的探测/扫描）。
            found.putIfAbsent(
                "192.168.1.1",
                DiscoveredCamera(host = "192.168.1.1", port = 15740, name = "", via = "scan"),
            )
            log("AP 模式：相机热点固定地址 192.168.1.1 已加入候选（不探测，避免打挂相机）")
            return@coroutineScope
        }
        val prefixes = localIpv4().mapNotNull { ip ->
            ip.substringBeforeLast('.', "").takeIf { it.isNotEmpty() }
        }.distinct()
        if (prefixes.isEmpty()) {
            log("子网扫描：没有可用的 IPv4 地址")
            return@coroutineScope
        }
        log("子网扫描：${prefixes.joinToString { "$it.0/24" }}（端口 15740）")
        // AP 模式并发必须低：PROTOCOL.md 实证 48 并发会把 Z30 嵌入式 Wi-Fi 服务打挂（需重建连接），
        // 降到 16。STA 模式维持原 64（已验证正常，不改，避免拖慢 STA 发现）。
        val gate = Semaphore(if (mode == "ap") 16 else 64)
        val hosts = prefixes.flatMap { prefix -> (1..254).map { "$prefix.$it" } }
        hosts.map { host ->
            launch {
                gate.withPermit {
                    if (probe(host)) {
                        found.putIfAbsent(
                            host,
                            DiscoveredCamera(host = host, port = 15740, name = "", via = "scan"),
                        )
                        log("子网扫描：命中 $host:15740")
                    }
                }
            }
        }.joinAll()
    }

    /** 目标在热点任一网段内才用绑定 socket，其他网段走普通路由（STA 场景手机自己是网关） */
    private fun newSocket(forHost: String): java.net.Socket {
        if (hotspot != null && hotspot.matches(forHost)) {
            return hotspot.network.socketFactory.createSocket()
        }
        return Socket()
    }

    /** AP 直探相机固定地址：强制绑定热点网络（192.168.1.1 是相机热点网关，必在该网络内） */
    private fun probeAp(host: String): Boolean = try {
        val socket = hotspot?.network?.socketFactory?.createSocket() ?: Socket()
        socket.use { it.connect(InetSocketAddress(host, 15740), 800) }
        true
    } catch (_: Exception) {
        false
    }

    private fun probe(host: String): Boolean = try {
        newSocket(host).use { it.connect(InetSocketAddress(host, 15740), 350) }
        true
    } catch (_: Exception) {
        false
    }
}
