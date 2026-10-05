package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyDisplayDefaultsTest {
    @Test fun smallHeadUnitKeepsNativeResolution() {
        assertEquals(100, LegacyDisplayDefaults.scalePercent(19, 800, 480, 30))
        assertEquals(100, LegacyDisplayDefaults.scalePercent(19, 1024, 600, 30))
    }

    @Test fun fullHdStartsWithinLegacyDecodeBudgetInEitherOrientation() {
        assertEquals(53, LegacyDisplayDefaults.scalePercent(19, 1920, 1080, 30))
        assertEquals(53, LegacyDisplayDefaults.scalePercent(19, 1080, 1920, 30))
    }

    @Test fun modernDevicesAndMissingMetricsRetainUpstreamDefault() {
        assertEquals(100, LegacyDisplayDefaults.scalePercent(21, 1920, 1080, 30))
        assertEquals(100, LegacyDisplayDefaults.scalePercent(19, 0, 0, 30))
    }
}
