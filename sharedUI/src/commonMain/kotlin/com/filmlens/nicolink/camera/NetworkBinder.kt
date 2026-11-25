package com.filmlens.nicolink.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * AP 模式网络绑定：手机连相机热点（无互联网能力）时，Android 默认路由仍走
 * 移动数据，普通 socket 到不了相机（192.168.1.1）。必须把 socket 绑定到
 * 相机热点所在的 Wi-Fi 网络（network.socketFactory），流量才走热点。
 *
 * 识别方式（2026-11-24 重写）：不再依赖 INTERNET/VALIDATED 能力位——官方文档明确
 * INTERNET 只表示"配置上能上网"，各 ROM 对无互联网热点的位设置五花八门，极易漏判。
 * 改为纯网段匹配：遍历所有 Wi-Fi 网络，目标 IP 落在哪个网络的网段内就绑定哪个网络。
 * STA 下相机 IP 在"手机热点"网段、不在手机所连 Wi-Fi 网段，因此不会被误绑定，
 * 走普通 socket（与原来一致），不影响 STA。
 */
object NetworkBinder {

    data class CameraHotspotNetwork(
        val network: Network,
        /** 该网络所有 IPv4 网段前缀（如 ["192.168.1", "192.168.43"]） */
        val prefixes: List<String>,
    ) {
        val prefix: String? get() = prefixes.firstOrNull()
        fun matches(host: String): Boolean = prefixes.any { host.startsWith("$it.") }
    }

    /** 列出手机当前所有 Wi-Fi 网络及其 IPv4 网段（不按能力位过滤） */
    private fun wifiNetworks(context: Context): List<CameraHotspotNetwork> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        val out = mutableListOf<CameraHotspotNetwork>()
        return runCatching {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val inet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val prefixes = cm.getLinkProperties(network)?.linkAddresses
                    ?.mapNotNull { it.address as? java.net.Inet4Address }
                    ?.filter { !it.isLoopbackAddress }
                    ?.mapNotNull { it.hostAddress?.substringBeforeLast('.') }
                    ?.distinct()
                    .orEmpty()
                android.util.Log.i("NicoLinkNet", "network=$network wifi=$wifi validated=$validated inet=$inet prefixes=$prefixes")
                if (!wifi || prefixes.isEmpty()) continue
                out.add(CameraHotspotNetwork(network, prefixes))
            }
            out
        }.getOrDefault(out)
    }

    /**
     * 供 CameraDiscovery 使用：优先返回"无 VALIDATED"（连上但上不了网，最像相机热点）的
     * Wi-Fi；若没有则退回第一个 Wi-Fi（手机通常只连一个 Wi-Fi，AP 下那就是相机热点）。
     */
    fun cameraHotspotNetwork(context: Context): CameraHotspotNetwork? {
        val all = wifiNetworks(context)
        if (all.isEmpty()) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val unvalidated = all.firstOrNull { net ->
            val caps = cm?.getNetworkCapabilities(net.network)
            caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return unvalidated ?: all.first()
    }

    /**
     * 目标 IP 落在某个 Wi-Fi 网段内就返回该网络（绑定 socket），否则 null 走普通 socket。
     * 纯网段匹配，不看能力位：STA 下相机 IP 不在手机所连 Wi-Fi 网段 → 不绑定 → 走原路径。
     */
    fun networkForHost(context: Context, host: String): Network? {
        val nets = wifiNetworks(context)
        val hit = nets.firstOrNull { it.matches(host) }
        android.util.Log.i("NicoLinkNet", "networkForHost($host) → ${hit?.network ?: "null"}（wifi网络数=${nets.size}）")
        return hit?.network
    }
}
