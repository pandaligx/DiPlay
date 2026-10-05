package com.shilapi.xcertplay

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build

/** On-device facts only. Listing a decoder does not prove that CarPlay playback succeeds. */
internal object DeviceCapabilityReport {
    @Suppress("DEPRECATION")
    fun create(context: Context): String = buildString {
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; hardware=${Build.HARDWARE}")
        val abis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.toList()
            else listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { it.isNotBlank() }
        appendLine("Reported ABI: ${abis.joinToString()}")
        val metrics = context.resources.displayMetrics
        appendLine("Screen: ${metrics.widthPixels}x${metrics.heightPixels}; densityDpi=${metrics.densityDpi}")
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (manager != null) {
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            appendLine("RAM MiB: total=${memory.totalMem / 1048576} available=${memory.availMem / 1048576}; appHeapMiB=${manager.memoryClass}; lowRam=${manager.isLowRamDevice}")
        }
        appendLine("Decoders reported by Android (not a playback test):")
        runCatching {
            for (index in 0 until MediaCodecList.getCodecCount()) {
                val codec = MediaCodecList.getCodecInfoAt(index)
                if (codec.isEncoder) continue
                val types = codec.supportedTypes.filter { it.startsWith("video/") || it.startsWith("audio/") }
                if (types.isEmpty()) continue
                appendLine("${codec.name}: ${types.joinToString()}")
                for (type in types.filter { it.equals("video/avc", true) || it.equals("video/hevc", true) }) {
                    val capabilities = runCatching { codec.getCapabilitiesForType(type) }.getOrNull() ?: continue
                    appendLine("  $type profiles/levels=${capabilities.profileLevels.joinToString { "${it.profile}/${it.level}" }}; colorFormats=${capabilities.colorFormats.joinToString()}")
                }
            }
        }.onFailure { appendLine("Decoder query unavailable: ${it.javaClass.simpleName}") }
        if (Build.VERSION.SDK_INT < 21) {
            appendLine("API 19 does not expose decoder size/rate limits. Start with H.264 at 30 fps and confirm playback on this device.")
        }
    }
}
