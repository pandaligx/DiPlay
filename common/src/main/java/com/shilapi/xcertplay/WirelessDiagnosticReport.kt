package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.isManualHotspotChannelCompatible

internal object WirelessDiagnosticReport {
    fun create(context: Context): String = buildString {
        appendLine("Current configuration (may differ from the last connection attempt):")
        appendLine("wirelessEnabled=${AirPlayPersistence.loadWirelessEnabled(context)} " +
            "mode=${AirPlayPersistence.loadWirelessHotspotMode(context)}")
        val name = AirPlayPersistence.loadManualHotspotSsid(context)
        val credential = AirPlayPersistence.loadManualHotspotPassphrase(context)
        val band = AirPlayPersistence.loadManualHotspotBand(context)
        val channel = AirPlayPersistence.loadManualHotspotChannel(context)
        val validation = ManualHotspotValidation.error(name, credential)
        // Only result codes and presence flags leave this method, never network names or credentials.
        val validationCode = when (validation) {
            ManualHotspotValidation.Error.EMPTY_NAME -> "network_label_missing"
            ManualHotspotValidation.Error.LONG_NAME -> "network_label_too_long"
            ManualHotspotValidation.Error.INVALID_CHARACTER -> "invalid_character"
            ManualHotspotValidation.Error.PASSWORD_LENGTH -> "credential_length_invalid"
            null -> "valid"
        }
        appendLine("manualHotspot configuration=$validationCode networkLabelPresent=${name.isNotBlank()} " +
            "credentialPresent=${credential.isNotEmpty()} band=$band channel=$channel " +
            "channelCompatible=${channel == 0 || isManualHotspotChannelCompatible(band, channel)}")
        appendLine("systemHotspotEnabled=${CarHotspotStatus.isEnabled(context)?.toString() ?: "unobservable"}")
        appendLine(DiPlayBluetooth.diagnosticReport(context))
        appendLine("Last observed wireless startup (timestamps distinguish automatic retries):")
        appendLine(WirelessDiagnosticSnapshot.report(context))
        appendLine("A stage or waitingFor value is an observation, not proof of a rejected authentication. " +
            "An enabled hotspot does not prove that the iPhone joined it or accepted CarPlay.")
        appendLine("Wireless CarPlay normally delivers the hotspot configuration over Bluetooth iAP2; " +
            "manually joining the hotspot is not a required prerequisite.")
    }.lineSequence().mapNotNull(DiagnosticRedactor::redact).joinToString("\n")
}
