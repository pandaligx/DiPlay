package com.shilapi.xcertplay

/** Initial decode budget for old head units; never replaces an explicitly saved resolution. */
internal object LegacyDisplayDefaults {
    fun scalePercent(sdkInt: Int, width: Int, height: Int, minimumPercent: Int): Int {
        if (sdkInt >= 21 || width <= 0 || height <= 0) return 100
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        return minOf(100L, 1024L * 100 / longSide, 600L * 100 / shortSide)
            .toInt().coerceAtLeast(minimumPercent)
    }
}
