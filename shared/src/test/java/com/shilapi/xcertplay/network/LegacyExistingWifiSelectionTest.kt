package com.shilapi.xcertplay.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyExistingWifiSelectionTest {
    private fun iface(name: String, address: String, index: Int = 4, up: Boolean = true) =
        HotspotInterfaceSnapshot(name, index, up, listOf(InetAddress.getByName(address)),
            wirelessInterfaceName(name))

    // WifiInfo stores 192.168.1.42 in little-endian order, including a signed high byte.
    private val stationIp = 0x2a01a8c0

    @Test fun identifiesTheStationByAddressInsteadOfInterfaceNameOrEnumerationOrder() {
        val selected = selectLegacyStationInterface(stationIp, listOf(
            iface("wlan0", "192.168.43.1"),
            iface("eth0", "192.168.1.10"),
            iface("vendor_wifi", "192.168.1.42"),
        ))
        assertEquals("vendor_wifi", selected?.name)
    }

    @Test fun handlesSignedLittleEndianStationAddresses() {
        val signedIp = 0xc801a8c0.toInt()
        assertEquals("wlan0", selectLegacyStationInterface(signedIp,
            listOf(iface("wlan0", "192.168.1.200")))?.name)
    }

    @Test fun absentOrStaleDhcpAddressDoesNotPickAnUnrelatedInterface() {
        val interfaces = listOf(iface("wlan0", "192.168.43.1"), iface("eth0", "10.0.0.2"))
        assertNull(selectLegacyStationInterface(0, interfaces))
        assertNull(selectLegacyStationInterface(stationIp, interfaces))
    }

    @Test fun refusesAmbiguousDownAndUnindexedInterfaces() {
        assertNull(selectLegacyStationInterface(stationIp, listOf(
            iface("wlan0", "192.168.1.42"), iface("tun0", "192.168.1.42", index = 7),
        )))
        assertNull(selectLegacyStationInterface(stationIp,
            listOf(iface("wlan0", "192.168.1.42", up = false))))
        assertNull(selectLegacyStationInterface(stationIp,
            listOf(iface("wlan0", "192.168.1.42", index = 0))))
    }

    @Test fun stationEvidencePreventsManualHotspotFromSelectingTheUpstream() {
        val station = iface("wlan0", "192.168.1.42")
        val hotspot = iface("wlan1", "192.168.43.1", index = 5)
        val upstream = selectLegacyStationInterface(stationIp, listOf(station, hotspot))!!
        val selected = selectHotspotInterface(HotspotNetworkSnapshot(
            listOf(station, hotspot), null, setOf(upstream.name), upstream.name,
        )) {}
        assertEquals("wlan1", selected?.name)
    }

    @Test fun unknownStationOwnershipDoesNotInventAnAp() {
        val selected = selectHotspotInterface(HotspotNetworkSnapshot(
            listOf(iface("wlan0", "192.168.1.42")), null, null, null,
        )) {}
        assertNull(selected)
    }
}
