package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbTransferPolicyTest {
    @Test fun largeLegacyWritesKeepEveryByteInOrder() {
        val source = ByteArray(32_769) { it.toByte() }
        val received = mutableListOf<Byte>()
        val chunks = mutableListOf<Int>()
        val result = UsbTransferPolicy.write(source.size, 1_000, UsbTransferPolicy.LEGACY_MAX_BYTES,
            nanoTime = { 0L }) { offset, length, _ ->
            chunks += length
            received.addAll(source.copyOfRange(offset, offset + length).toList())
            length
        }
        assertEquals(source.size, result)
        assertEquals(listOf(16_384, 16_384, 1), chunks)
        assertEquals(source.toList(), received)
    }

    @Test fun chunksShareTheOriginalDeadline() {
        var elapsed = 0L
        val timeouts = mutableListOf<Int>()
        val result = UsbTransferPolicy.write(40_000, 100, UsbTransferPolicy.LEGACY_MAX_BYTES,
            nanoTime = { elapsed }) { _, length, timeout ->
            timeouts += timeout
            elapsed += 60_000_000
            length
        }
        assertEquals(listOf(100, 40), timeouts)
        assertEquals(32_768, result)
    }

    @Test fun failureAfterFirstChunkNeverRestartsOrRetriesFrame() {
        val offsets = mutableListOf<Int>()
        val result = UsbTransferPolicy.write(40_000, 100, UsbTransferPolicy.LEGACY_MAX_BYTES,
            nanoTime = { 0L }) { offset, length, _ ->
            offsets += offset
            if (offset == 0) length else -1
        }
        assertEquals(listOf(0, 16_384), offsets)
        assertEquals(16_384, result)
    }

    @Test fun shortTransferIsReturnedToCallerAsFailure() {
        var calls = 0
        val result = UsbTransferPolicy.write(40_000, 100, UsbTransferPolicy.LEGACY_MAX_BYTES,
            nanoTime = { 0L }) { _, _, _ -> calls++; 12 }
        assertEquals(1, calls)
        assertEquals(12, result)
    }

    @Test fun positiveLongTimeoutNeverWrapsToInfiniteOrNegative() {
        assertEquals(Int.MAX_VALUE, UsbTransferPolicy.timeout(Long.MAX_VALUE))
        assertTrue(UsbTransferPolicy.timeout(1) > 0)
    }
}
