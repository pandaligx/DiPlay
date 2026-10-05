package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class LegacyDisplayPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() { prefs.edit().clear().commit() }

    @Test fun explicitCustomResolutionWinsOverCompatibilityDefault() {
        prefs.edit().putInt("display_scale_percent", 85).putInt("display_scale_tenths", 6).commit()
        assertEquals(85, AirPlayPersistence.loadDisplayScalePercent(context))
    }

    @Test fun previousTenthsSelectionIsPreserved() {
        prefs.edit().putInt("display_scale_tenths", 7).commit()
        assertEquals(70, AirPlayPersistence.loadDisplayScalePercent(context))
    }

    @Test fun freshInstallRetainsThirtyFpsDefault() {
        assertEquals(30, AirPlayPersistence.loadFps(context))
    }
}
