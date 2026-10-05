package com.shilapi.xcertplay.transport

/** Pre-P Android silently truncates bulk transfers over 16 KiB. Preserve stream bytes explicitly. */
internal object UsbTransferPolicy {
    const val LEGACY_MAX_BYTES = 16 * 1024

    fun timeout(timeoutMillis: Long): Int = timeoutMillis.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

    /** Returns bytes sent, or the first error when nothing was sent. Never restarts a partial frame. */
    fun write(
        size: Int,
        timeoutMillis: Int,
        maxChunkBytes: Int,
        nanoTime: () -> Long = System::nanoTime,
        transfer: (offset: Int, length: Int, timeout: Int) -> Int,
    ): Int {
        require(size >= 0 && timeoutMillis > 0 && maxChunkBytes > 0)
        val started = nanoTime()
        val budget = timeoutMillis.toLong() * NANOS_PER_MILLISECOND
        var offset = 0
        while (offset < size) {
            val remaining = budget - (nanoTime() - started)
            if (remaining <= 0) return if (offset == 0) -1 else offset
            val length = minOf(size - offset, maxChunkBytes)
            val count = transfer(offset, length, timeout((remaining + NANOS_PER_MILLISECOND - 1) / NANOS_PER_MILLISECOND))
            if (count <= 0) return if (offset == 0) count else offset
            // A short transfer is an error to the caller, as in the upstream single-write path.
            if (count != length) return offset + count
            offset += count
        }
        return offset
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
