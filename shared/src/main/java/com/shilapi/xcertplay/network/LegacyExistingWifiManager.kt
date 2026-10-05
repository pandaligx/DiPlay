package com.shilapi.xcertplay.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Looper
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** API 19 has no Network objects or callbacks; observe only the already connected station. */
@Suppress("DEPRECATION")
internal class LegacyExistingWifiManager(
    context: Context,
    private val ssid: String,
    private val passphrase: String,
    private val onDiagnostic: (String) -> Unit,
    private val onNetworkChanged: () -> Unit,
) : WirelessHotspotManager {
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: throw IllegalStateException("ConnectivityManager is unavailable")
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: throw IllegalStateException("WifiManager is unavailable")
    private val lock = Any()
    private val invalidated = AtomicBoolean()
    @Volatile private var closed = false
    @Volatile private var selected: StationSnapshot? = null
    private var monitor: Thread? = null

    init {
        require(ManualHotspotValidation.error(ssid, passphrase) == null) {
            "Invalid existing Wi-Fi credentials"
        }
    }

    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "ExistingWifiManager.start must not run on the main thread"
        }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        check(selected == null) { "ExistingWifiManager has already started" }
        val started = System.nanoTime()
        while (!closed) {
            val current = sample()
            if (current != null) {
                if (current.ssid != null && current.ssid != ssid) {
                    throw IOException("Configured Wi-Fi does not match the connected network; check Wi-Fi settings and saved details")
                }
                val address = wirelessHostAddress(current.addresses, current.index)
                    ?: throw IOException("Existing Wi-Fi has no usable address")
                // ScanResult.frequency predates API 19. Reading cached results does not start a scan.
                val frequency = try {
                    current.bssid?.let { bssid ->
                        wifi.scanResults.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
                            ?.frequency?.takeIf { it > 0 }
                    }
                } catch (_: SecurityException) {
                    // Frequency is optional; saved station credentials and addresses remain usable.
                    null
                } catch (_: RuntimeException) {
                    null
                }
                synchronized(lock) {
                    check(!closed) { "ExistingWifiManager is closed" }
                    if (!current.sameLink(sample())) {
                        throw IOException("Existing Wi-Fi changed during startup; connect again")
                    }
                    selected = current
                    monitor = Thread(::monitorLink, "existing-wifi-legacy-monitor").apply {
                        isDaemon = true
                        start()
                    }
                }
                onDiagnostic("Existing Wi-Fi attached legacy=true iface=${current.name} " +
                    "host=${address.hostAddress} networkNameReadable=${current.ssid != null} " +
                    "channel=${frequency?.let(::wifiFrequencyMhzToChannel) ?: 0} " +
                    "securityObserved=false linkMonitoring=poll")
                return WirelessHotspotInfo(
                    ssid, passphrase,
                    if (passphrase.isEmpty()) Iap2WirelessSecurity.NONE else Iap2WirelessSecurity.WPA_WPA2,
                    frequency?.let(::wifiFrequencyMhzToChannel) ?: 0, frequency, null,
                    current.name, address,
                    when {
                        frequency == null -> "Auto"
                        frequency < 2500 -> "2.4 GHz"
                        frequency < 5955 -> "5 GHz"
                        else -> "6 GHz"
                    },
                    WirelessHotspotBackend.EXISTING_WIFI,
                    hostAddresses = current.addresses,
                    accessPointBssid = current.bssid?.let(::legacyAccessPointAddress),
                )
            }
            if ((System.nanoTime() - started) / 1_000_000 >= timeoutMillis) {
                throw IOException("Existing Wi-Fi is not connected or its station IPv4 interface is unavailable. Connect both devices to the same Wi-Fi in system settings")
            }
            try {
                Thread.sleep(200)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Existing Wi-Fi attachment was interrupted", interrupted)
            }
        }
        throw IOException("Existing Wi-Fi attachment was cancelled")
    }

    private fun sample(): StationSnapshot? {
        if (connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected != true) return null
        val info = wifi.connectionInfo ?: return null
        val station = selectLegacyStationInterface(info.ipAddress, legacyNetworkInterfaces()) ?: return null
        return StationSnapshot(station.name, station.index,
            existingWifiHostAddresses(station.addresses, station.index),
            info.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isEmpty() },
            info.bssid?.takeIf { legacyAccessPointAddress(it) != null })
    }

    private fun monitorLink() {
        try {
            while (!closed && !invalidated.get()) {
                Thread.sleep(1_000)
                if (!closed && selected?.sameLink(runCatching { sample() }.getOrNull()) != true) {
                    invalidate()
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun invalidate() {
        if (!closed && invalidated.compareAndSet(false, true)) {
            onDiagnostic("Existing Wi-Fi interface, address or station changed; restarting wireless session")
            onNetworkChanged()
        }
    }

    override fun validateReady() {
        if (closed || invalidated.get() || selected?.sameLink(sample()) != true) {
            throw IOException("Existing Wi-Fi changed before publication; connect again")
        }
    }

    override fun connectionDiagnosticSnapshot(): String =
        "existingWifiLink=${if (closed || invalidated.get() || selected == null) "lost" else "attached"} monitoring=poll"

    override fun close() {
        synchronized(lock) {
            closed = true
            monitor?.interrupt()
            monitor = null
        }
    }

    private data class StationSnapshot(
        val name: String,
        val index: Int,
        val addresses: List<InetAddress>,
        val ssid: String?,
        val bssid: String?,
    ) {
        fun sameLink(other: StationSnapshot?): Boolean = other != null && name == other.name &&
            index == other.index && addresses.toSet() == other.addresses.toSet() &&
            ssid == other.ssid && bssid == other.bssid
    }
}

/** Match WifiInfo's little-endian IPv4 address, never a guessed wlan name or the default route. */
internal fun selectLegacyStationInterface(
    stationIpv4: Int,
    interfaces: List<HotspotInterfaceSnapshot>,
): HotspotInterfaceSnapshot? {
    if (stationIpv4 == 0) return null
    val bytes = ByteArray(4) { (stationIpv4 ushr (it * 8)).toByte() }
    return interfaces.filter { iface ->
        iface.up && iface.index > 0 && iface.addresses.any {
            it is Inet4Address && !it.isAnyLocalAddress && !it.isLoopbackAddress &&
                !it.isMulticastAddress && it.address.contentEquals(bytes)
        }
    }.singleOrNull()
}

internal fun legacyNetworkInterfaces(): List<HotspotInterfaceSnapshot> =
    Collections.list(NetworkInterface.getNetworkInterfaces()).mapNotNull { iface ->
        runCatching {
            if (iface.isLoopback) null else HotspotInterfaceSnapshot(
                iface.name, iface.index, iface.isUp, Collections.list(iface.inetAddresses),
                wirelessInterfaceName(iface.name),
            )
        }.getOrNull()
    }

private fun legacyAccessPointAddress(text: String): ByteArray? {
    if (!text.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))) return null
    val bytes = text.split(':').map { it.toInt(16).toByte() }.toByteArray()
    if (bytes.all { it == 0.toByte() } || bytes[0].toInt() and 1 != 0 ||
        text.equals("02:00:00:00:00:00", ignoreCase = true)) return null
    return bytes
}
