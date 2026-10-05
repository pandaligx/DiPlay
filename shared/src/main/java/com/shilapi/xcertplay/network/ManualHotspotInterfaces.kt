package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TetheringInterface
import android.net.TetheringManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

internal class ManualHotspotInterfaces(
    private val context: Context,
    private val onDiagnostic: (String) -> Unit = {},
) : Closeable {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val publicTethering = if (Build.VERSION.SDK_INT >= 36) PublicTethering(context) else null
    private var lastLegacyDiagnostic: String? = null

    fun sample(): HotspotNetworkSnapshot {
        val ap = (if (Build.VERSION.SDK_INT >= 36) publicTethering?.interfaces else null)
            ?: legacyApInterfaces()
        val before = runCatching { connectivityState() }.getOrNull()
        val interfaces = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).mapNotNull { iface ->
                runCatching {
                    if (iface.isLoopback) null else HotspotInterfaceSnapshot(
                        iface.name, iface.index, iface.isUp, Collections.list(iface.inetAddresses),
                        wirelessInterfaceName(iface.name) || File("/sys/class/net/${iface.name}/wireless").isDirectory,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
        val after = runCatching { connectivityState() }.getOrNull()
        return HotspotNetworkSnapshot(
            interfaces, ap, before?.upstreams, before?.defaultName,
            consistent = before != null && before == after,
            apEnabled = CarHotspotStatus.isEnabled(context),
        )
    }

    @Suppress("DEPRECATION")
    private fun connectivityState(): ConnectivityState {
        val manager = checkNotNull(connectivity)
        if (Build.VERSION.SDK_INT >= 23) return Api23Connectivity.read(manager)
        val network = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            ?: return ConnectivityState("unavailable", null, null)
        if (!network.isConnected) return ConnectivityState("disconnected", emptySet(), null)
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val station = wifi?.connectionInfo
        val iface = station?.let { selectLegacyStationInterface(it.ipAddress, legacyNetworkInterfaces()) }
        // Unknown ownership remains unknown; it must not make another interface an AP candidate.
        return ConnectivityState("${station?.networkId}:${station?.ipAddress}:${station?.bssid}",
            iface?.let { setOf(it.name) }, null)
    }

    private data class ConnectivityState(val token: String?, val upstreams: Set<String>?, val defaultName: String?)

    @RequiresApi(23)
    private object Api23Connectivity {
        fun read(manager: ConnectivityManager): ConnectivityState {
            val active = manager.activeNetwork
            val upstreams = manager.allNetworks.mapNotNull { network ->
                val caps = checkNotNull(manager.getNetworkCapabilities(network))
                val links = checkNotNull(manager.getLinkProperties(network))
                links.interfaceName?.takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            }.toSet()
            return ConnectivityState(active?.toString(), upstreams,
                active?.let { manager.getLinkProperties(it)?.interfaceName })
        }
    }

    // 旧平台只使用允许读取的结果；接口归属读不到时保持 unknown，不放宽普通网卡资格。
    @SuppressLint("PrivateApi")
    private fun legacyApInterfaces(): Set<String>? = runCatching {
        val manager = checkNotNull(connectivity)
        val tethered = ConnectivityManager::class.java.getMethod("getTetheredIfaces")
            .invoke(manager) as Array<*>
        val regexes = ConnectivityManager::class.java.getMethod("getTetherableWifiRegexs")
            .invoke(manager) as Array<*>
        val names = tethered.filterIsInstance<String>()
        val patterns = regexes.filterIsInstance<String>()
        val ap = legacyHotspotInterfaces(names, patterns)
        val diagnostic = "legacy hotspot ownership=${if (ap == null) "unobservable" else "observed"} " +
            "tethered=$names wifiRegexes=$patterns matched=$ap"
        if (diagnostic != lastLegacyDiagnostic) {
            lastLegacyDiagnostic = diagnostic
            onDiagnostic(diagnostic)
        }
        ap
    }.getOrNull()

    override fun close() {
        if (Build.VERSION.SDK_INT >= 36) publicTethering?.close()
    }

    @RequiresApi(36)
    private class PublicTethering(context: Context) : Closeable {
        @Volatile var interfaces: Set<String>? = null
            private set
        private val manager = context.getSystemService(TetheringManager::class.java)
        private val callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                this@PublicTethering.interfaces = interfaces.filter { it.type == TetheringManager.TETHERING_WIFI }
                    .map { it.getInterface() }.toSet()
            }
        }
        private val registered = runCatching {
            checkNotNull(manager).registerTetheringEventCallback(context.mainExecutor, callback)
        }.isSuccess

        override fun close() {
            if (registered) runCatching { manager?.unregisterTetheringEventCallback(callback) }
        }
    }
}

internal fun legacyHotspotInterfaces(tethered: List<String>, wifiRegexes: List<String>): Set<String>? {
    if (wifiRegexes.isEmpty()) return null
    val patterns = wifiRegexes.map(::Regex)
    val matched = tethered.filter { name -> patterns.any { it.matches(name) } }.toSet()
    // 厂商可能动态使用 wlan0，而静态配置仍写 softap0；匹配失败不能证明没有热点。
    return matched.takeUnless { tethered.isNotEmpty() && it.isEmpty() }
}

internal fun wirelessInterfaceName(name: String): Boolean =
    name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("ap") ||
        name.contains("softap", ignoreCase = true) || name.startsWith("wifi") || name.startsWith("p2p")
