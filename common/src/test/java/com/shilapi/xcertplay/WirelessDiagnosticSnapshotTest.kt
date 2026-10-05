package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class WirelessDiagnosticSnapshotTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        context.getSharedPreferences("diplay_wireless_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun retainsActualFailureAcrossRetryWithoutGuessingAuthentication() {
        val previous = WirelessDiagnosticSnapshot.begin(context, "MANUAL")
        WirelessDiagnosticSnapshot.status(context, previous, CarPlayStatus.ConnectingBluetooth)
        WirelessDiagnosticSnapshot.status(context, previous, CarPlayStatus.Failed("Connection interrupted"))
        WirelessDiagnosticSnapshot.begin(context, "MANUAL")
        val report = WirelessDiagnosticSnapshot.report(context)
        assertTrue(report.contains("layer=not_classified"))
        assertTrue(report.contains("precedingStage=("))
        assertTrue(report.contains("ConnectingBluetooth"))
        assertTrue(report.contains("detail=Connection interrupted"))
    }

    @Test fun staleAttemptCannotReplaceCurrentEvidence() {
        val old = WirelessDiagnosticSnapshot.begin(context, "MANUAL")
        val current = WirelessDiagnosticSnapshot.begin(context, "MANUAL")
        WirelessDiagnosticSnapshot.status(context, current, CarPlayStatus.StartingHotspot)
        WirelessDiagnosticSnapshot.status(context, old, CarPlayStatus.ConnectingBluetooth)
        assertFalse(WirelessDiagnosticSnapshot.report(context).contains("ConnectingBluetooth"))
    }

    @Test fun hotspotStatusDoesNotExportNetworkIdentity() {
        val attempt = WirelessDiagnosticSnapshot.begin(context, "MANUAL")
        WirelessDiagnosticSnapshot.status(context, attempt,
            CarPlayStatus.HotspotReady("private-network", "2.4 GHz", 6, "11:22:33:44:55:66", "192.168.43.1", "manual"))
        val report = WirelessDiagnosticSnapshot.report(context)
        assertTrue(report.contains("stage=HotspotReady"))
        assertFalse(report.contains("private-network"))
        assertFalse(report.contains("11:22:33:44:55:66"))
        assertFalse(report.contains("192.168.43.1"))
    }
}
