package com.shilapi.xcertplay

/** Keep existing controller evidence, never classify a translated UI error as a cause. */
internal object WirelessDiagnosticLines {
    fun select(message: String): Pair<String, String>? {
        val field = when {
            message.startsWith("wireless startup elapsedMs=") -> "startup"
            message.startsWith("wireless snapshot") && message.contains("receiveCounters ") -> return null
            message.startsWith("wireless snapshot") -> "network"
            message.startsWith("hotspot candidate ") -> "hotspotCandidate"
            message.startsWith("hotspot interface confirmed ") -> "hotspotConfirmed"
            message.startsWith("hotspot sample rejected:") -> "hotspotRejected"
            message.startsWith("legacy hotspot ownership=") -> "hotspotOwnership"
            message.startsWith("wireless hotspot backend=") -> "hotspotBackend"
            message.startsWith("wireless bonjour:") -> "discovery"
            message.startsWith("CONNECTION_DIAGNOSTIC ") && message.contains("Bluetooth ") -> "controllerBluetooth"
            else -> return null
        }
        return DiagnosticRedactor.redact(message)?.let { field to it }
    }
}
