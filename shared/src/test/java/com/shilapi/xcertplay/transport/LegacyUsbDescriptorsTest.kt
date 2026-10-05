package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyUsbDescriptorsTest {
    @Test fun configurationAndAlternateValuesComeFromDescriptors() {
        val descriptors = device() + configuration(6,
            usbInterface(2, 0, 2, 0x0d, 0),
            usbInterface(3, 0, 0x0a, 0, 0),
            usbInterface(3, 2, 0x0a, 0, 0, endpoint(0x83), endpoint(0x04)))
        val parsed = LegacyUsbDescriptors.parse(descriptors).single()
        assertEquals(6, parsed.id)
        assertEquals(listOf(0, 0, 2), parsed.interfaces.map { it.alternateSetting })
        assertEquals(listOf(0x83, 0x04), parsed.interfaces.last().endpoints.map { it.address })
        assertEquals(512, parsed.interfaces.last().endpoints.first().maxPacketSize)
    }

    @Test fun configurationsKeepSeparateInterfaceLists() {
        val parsed = LegacyUsbDescriptors.parse(device() +
            configuration(1, usbInterface(0, 0, 6, 1, 1)) +
            configuration(6, usbInterface(0, 0, 0xff, 0xfe, 2)))
        assertEquals(listOf(1, 6), parsed.map { it.id })
        assertEquals(listOf(6, 0xff), parsed.map { it.interfaces.single().interfaceClass })
    }

    @Test fun classSpecificDescriptorsDoNotChangeEndpointOwnership() {
        val cdc = bytes(5, 0x24, 0x0f, 4, 0)
        val parsed = LegacyUsbDescriptors.parse(configuration(6,
            usbInterface(2, 0, 2, 0x0d, 0) + cdc,
            usbInterface(3, 1, 0x0a, 0, 0, endpoint(0x83))))
        assertTrue(parsed.single().interfaces.first().endpoints.isEmpty())
        assertEquals(0x83, parsed.single().interfaces.last().endpoints.single().address)
    }

    @Test fun truncatedOrInconsistentDescriptorsAreRejected() {
        val valid = configuration(6, usbInterface(3, 1, 0x0a, 0, 0, endpoint(0x83)))
        assertTrue(LegacyUsbDescriptors.parse(valid.copyOf(valid.size - 1)).isEmpty())
        assertTrue(LegacyUsbDescriptors.parse(valid.copyOf().also { it[9] = 0 }).isEmpty())
        assertTrue(LegacyUsbDescriptors.parse(valid.copyOf().also { it[13] = 2 }).isEmpty())
        assertTrue(LegacyUsbDescriptors.parse(valid.copyOf().also { it[5] = 0 }).isEmpty())
    }

    private fun device() = bytes(18, 1, 0, 2, 0, 0, 0, 64, 0xac, 5, 1, 0, 0, 0, 0, 0, 0, 2)
    private fun configuration(id: Int, vararg interfaces: ByteArray): ByteArray {
        val contents = interfaces.fold(ByteArray(0)) { value, next -> value + next }
        val length = 9 + contents.size
        return bytes(9, 2, length and 0xff, length shr 8, interfaces.size, id, 0, 0x80, 50) + contents
    }
    private fun usbInterface(id: Int, alternate: Int, type: Int, subclass: Int, protocol: Int,
        vararg endpoints: ByteArray): ByteArray =
        bytes(9, 4, id, alternate, endpoints.size, type, subclass, protocol, 0) +
            endpoints.fold(ByteArray(0)) { value, next -> value + next }
    private fun endpoint(address: Int) = bytes(7, 5, address, 2, 0, 2, 0)
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
