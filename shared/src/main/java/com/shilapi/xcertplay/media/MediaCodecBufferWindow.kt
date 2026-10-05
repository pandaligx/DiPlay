package com.shilapi.xcertplay.media

import java.nio.ByteBuffer

/** KitKat reuses output buffers with the position/limit left by the previous access unit. */
internal fun codecOutputWindow(buffer: ByteBuffer, offset: Int, size: Int): ByteBuffer {
    require(offset >= 0 && size >= 0 && offset <= buffer.capacity() - size) {
        "Codec output exceeds buffer capacity"
    }
    return buffer.duplicate().apply {
        clear()
        limit(offset + size)
        position(offset)
    }
}
