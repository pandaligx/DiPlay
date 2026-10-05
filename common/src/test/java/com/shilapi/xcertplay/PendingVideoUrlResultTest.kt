package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PendingVideoUrlResultTest {
    @Test fun firstReplyWinsEvenWhenARepeatedReplyArrives() {
        val pending = PendingVideoUrlResult()
        val first = mapOf("status" to 200)
        assertTrue(pending.complete(first))
        assertFalse(pending.complete(mapOf("status" to 500)))
        assertSame(first, pending.get(0, TimeUnit.MILLISECONDS))
    }

    @Test(expected = TimeoutException::class)
    fun missingReplyPreservesTimeoutBehavior() {
        PendingVideoUrlResult().get(0, TimeUnit.MILLISECONDS)
    }

    @Test fun replyUnblocksAnotherThread() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val pending = PendingVideoUrlResult()
            val answer = worker.submit<Map<*, *>?> { pending.get(2, TimeUnit.SECONDS) }
            val data = mapOf("status" to 200)
            pending.complete(data)
            assertSame(data, answer.get(2, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
        }
    }
}
