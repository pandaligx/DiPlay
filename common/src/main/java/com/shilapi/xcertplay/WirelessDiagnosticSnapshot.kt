package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import java.util.UUID

/** Retain a bounded, redacted startup snapshot even when retries rotate the session log. */
internal object WirelessDiagnosticSnapshot {
    private const val PREFS = "diplay_wireless_diagnostics"
    private val fields = listOf("attemptStarted", "stage", "hotspotCandidate", "hotspotConfirmed",
        "hotspotRejected", "hotspotOwnership", "hotspotBackend", "controllerBluetooth", "startup", "network", "discovery", "lastFailure")

    @Synchronized fun begin(context: Context, mode: String): String {
        val attempt = UUID.randomUUID().toString()
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        // A fast automatic retry must not erase the actual failure before the owner can export it.
        fields.filter { it != "lastFailure" }.forEach(editor::remove)
        editor.putString("attempt", attempt)
            .putString("attemptStarted", "atEpochMs=${System.currentTimeMillis()} wirelessMode=$mode")
            .apply()
        return attempt
    }

    fun currentAttempt(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("attempt", null)

    @Synchronized fun status(context: Context, attempt: String?, status: CarPlayStatus) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (attempt == null || prefs.getString("attempt", null) != attempt) return
        val stamp = "atEpochMs=${System.currentTimeMillis()}"
        val editor = prefs.edit()
        if (status is CarPlayStatus.Failed) {
            val layer = when (status.startupFailure) {
                WirelessStartupFailure.HOTSPOT_NOT_READY -> "hotspot_readiness"
                WirelessStartupFailure.HOTSPOT_CONFIGURATION -> "hotspot_configuration"
                WirelessStartupFailure.FIRST_TCP_TIMEOUT -> "discovery_or_first_tcp"
                null -> "not_classified"
            }
            val stage = prefs.getString("stage", "unobserved")
            val detail = DiagnosticRedactor.redact(status.message) ?: "[omitted by diagnostic redactor]"
            editor.putString("lastFailure", "$stamp layer=$layer code=${status.startupFailure ?: "unspecified"} " +
                "resetRequired=${status.wifiResetRequired} precedingStage=($stage) detail=$detail")
        } else {
            val stage = status.javaClass.simpleName
            val extra = if (status is CarPlayStatus.HotspotReady)
                " addressAvailable=${status.address.isNotBlank()} family=${if (':' in status.address) "IPv6" else "IPv4"}"
                else ""
            // Do not serialize HotspotReady: its data-class text includes the network's identity.
            editor.putString("stage", "$stamp stage=$stage$extra")
        }
        editor.apply()
    }

    @Synchronized fun record(context: Context, attempt: String?, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (attempt == null || prefs.getString("attempt", null) != attempt) return
        val (field, safe) = WirelessDiagnosticLines.select(message) ?: return
        prefs.edit().putString(field, "atEpochMs=${System.currentTimeMillis()} $safe").apply()
    }

    @Synchronized fun report(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return fields.mapNotNull { field -> prefs.getString(field, null)?.let(DiagnosticRedactor::redact)
            ?.let { "$field: $it" } }.joinToString("\n")
            .ifEmpty { "No wireless startup observation recorded by this build." }
    }
}
