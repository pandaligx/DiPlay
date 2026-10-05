package com.shilapi.xcertplay.media

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class MediaCodecBufferWindowTest {
    @Test fun reusedLegacyOutputBufferCanExposeALargerPacketAtANewOffset() {
        val buffer = ByteBuffer.allocate(16)
        buffer.put(ByteArray(16) { it.toByte() })
        buffer.position(0)
        buffer.limit(2) // A previous, smaller output access unit.
        val window = codecOutputWindow(buffer, 4, 8)
        val bytes = ByteArray(window.remaining())
        window.get(bytes)
        assertArrayEquals(ByteArray(8) { (it + 4).toByte() }, bytes)
        assertEquals(0, buffer.position())
        assertEquals(2, buffer.limit())
    }

    @Test fun exactCapacityOutputRemainsReadable() {
        val window = codecOutputWindow(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)), 0, 4)
        assertEquals(4, window.remaining())
        assertEquals(1, window.get().toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowingVendorMetadataIsRejectedBeforeReading() {
        codecOutputWindow(ByteBuffer.allocate(16), Int.MAX_VALUE, 8)
    }
}
