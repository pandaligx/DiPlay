package com.shilapi.xcertplay.transport

/** Reads USB configuration/alternate-setting values that Android 4.4's USB API omits. */
internal object LegacyUsbDescriptors {
    data class Endpoint(val address: Int, val attributes: Int, val maxPacketSize: Int, val interval: Int)
    data class Interface(
        val id: Int,
        val alternateSetting: Int,
        val interfaceClass: Int,
        val subclass: Int,
        val protocol: Int,
        val endpoints: List<Endpoint>,
    )
    data class Configuration(val id: Int, val interfaces: List<Interface>)

    /** Rejects incomplete descriptors instead of guessing a configuration id or alternate setting. */
    fun parse(raw: ByteArray): List<Configuration> {
        val configurations = mutableListOf<Configuration>()
        var offset = 0
        while (offset < raw.size) {
            if (offset + 2 > raw.size) return emptyList()
            val length = raw.u8(offset)
            if (length < 2 || offset + length > raw.size) return emptyList()
            if (raw.u8(offset + 1) != CONFIGURATION) {
                offset += length
                continue
            }
            if (length < 9) return emptyList()
            val totalLength = raw.u16(offset + 2)
            if (totalLength < length || totalLength > raw.size - offset) return emptyList()
            val id = raw.u8(offset + 5)
            if (id == 0) return emptyList()
            val end = offset + totalLength
            var cursor = offset + length
            val interfaces = mutableListOf<Interface>()
            var current: Interface? = null
            var endpointCount = 0
            fun finishInterface(): Boolean {
                val value = current ?: return true
                if (value.endpoints.size != endpointCount) return false
                interfaces += value
                return true
            }
            while (cursor < end) {
                if (cursor + 2 > end) return emptyList()
                val size = raw.u8(cursor)
                if (size < 2 || size > end - cursor) return emptyList()
                when (raw.u8(cursor + 1)) {
                    CONFIGURATION -> return emptyList()
                    INTERFACE -> {
                        if (size < 9 || !finishInterface()) return emptyList()
                        endpointCount = raw.u8(cursor + 4)
                        current = Interface(raw.u8(cursor + 2), raw.u8(cursor + 3),
                            raw.u8(cursor + 5), raw.u8(cursor + 6), raw.u8(cursor + 7), emptyList())
                    }
                    ENDPOINT -> {
                        if (size < 7) return emptyList()
                        val currentInterface = current ?: return emptyList()
                        val endpoint = Endpoint(raw.u8(cursor + 2), raw.u8(cursor + 3),
                            raw.u16(cursor + 4), raw.u8(cursor + 6))
                        current = currentInterface.copy(endpoints = currentInterface.endpoints + endpoint)
                    }
                }
                cursor += size
            }
            if (!finishInterface()) return emptyList()
            configurations += Configuration(id, interfaces)
            offset = end
        }
        return configurations
    }

    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xff
    private fun ByteArray.u16(index: Int) = u8(index) or (u8(index + 1) shl 8)
    private const val CONFIGURATION = 2
    private const val INTERFACE = 4
    private const val ENDPOINT = 5
}
