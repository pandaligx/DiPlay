package com.shilapi.xcertplay

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One iPhone URL reply, using concurrency primitives available before API 19. */
internal class PendingVideoUrlResult {
    private val ready = CountDownLatch(1)
    @Volatile private var response: Map<*, *>? = null

    @Synchronized fun complete(value: Map<*, *>?): Boolean {
        if (ready.count == 0L) return false
        response = value
        ready.countDown()
        return true
    }

    fun get(timeout: Long, unit: TimeUnit): Map<*, *>? {
        if (!ready.await(timeout, unit)) throw TimeoutException("iPhone URL reply timed out")
        return response
    }
}
