package com.shilapi.xcertplay.network

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HotspotBssidTest {
    @Test fun parsesMixedCaseWithoutApi28MacAddress() {
        assertArrayEquals(byteArrayOf(2, 10, 15, 0x80.toByte(), 0xfe.toByte(), 0xff.toByte()),
            parseHotspotBssid("02:0a:0F:80:fE:FF"))
    }

    @Test fun retainsPlatformSupportForSingleDigitOctets() {
        assertArrayEquals(byteArrayOf(2, 0, 10, 11, 12, 13), parseHotspotBssid("2:0:a:b:c:d"))
    }

    @Test fun rejectsMalformedAndOversizedOctets() {
        for (invalid in listOf("", "02:00:00:00:01", "02:00:00:00:00:01:02",
            "02:00:00:00:00:100", "02:00:00:00:00:zz", "02:00:00:00:00:",
            "02:00:00:00:00:-1")) {
            assertThrows(IOException::class.java) { parseHotspotBssid(invalid) }
        }
    }
}
