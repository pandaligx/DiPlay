package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WirelessDiagnosticLinesTest {
    @Test fun keepsUpstreamProgressWithoutAssigningAnErrorCause() {
        val line = "wireless startup elapsedMs=10000 authenticated=false tcpAccepted=0 waitingFor=Bluetooth_iAP2_authentication"
        assertEquals("startup" to line, WirelessDiagnosticLines.select(line))
        assertNull(WirelessDiagnosticLines.select("Connection interrupted, retrying"))
    }

    @Test fun keepsInterfaceEvidenceWithoutRawAddresses() {
        val selected = WirelessDiagnosticLines.select("wireless hotspot backend=manual iface=wlan0 host=192.168.43.1")!!
        assertEquals("hotspotBackend", selected.first)
        assertFalse(selected.second.contains("192.168.43.1"))
    }

    @Test fun rejectsCredentialPayloadEvenWithRecognizedPrefix() {
        assertNull(WirelessDiagnosticLines.select("wireless snapshot password=secret"))
        assertNull(WirelessDiagnosticLines.select("wireless snapshot ssid=private-network"))
        assertNull(WirelessDiagnosticLines.select("wireless snapshot payload=bytes"))
    }

    @Test fun receiveCountersDoNotOverwriteAssociationAndBonjourSnapshot() {
        assertNull(WirelessDiagnosticLines.select("wireless snapshot receiveCounters windowMs=10000 udpScope=device"))
        assertEquals("network", WirelessDiagnosticLines.select("wireless snapshot interfaceState=up association=unknown bonjour=started")?.first)
    }
}
