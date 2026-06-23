package com.whitefang.stepsofbabylon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepsOfBabylonCrashHandlerTest {
    @Test
    fun `handler records a breadcrumb and still delegates to the previous handler`() {
        var recordedThread: String? = null
        var recordedThrowable: Throwable? = null
        val recordFn: (String, Throwable, Long) -> Unit = { t, ex, _ ->
            recordedThread = t
            recordedThrowable = ex
        }

        var delegatedThread: Thread? = null
        var delegatedThrowable: Throwable? = null
        val previous =
            Thread.UncaughtExceptionHandler { t, ex ->
                delegatedThread = t
                delegatedThrowable = ex
            }

        val handler = buildCrashHandler(previous, recordFn)

        val boom = RuntimeException("kaboom")
        val thread = Thread.currentThread()
        handler.uncaughtException(thread, boom)

        assertEquals(thread.name, recordedThread)
        assertSame(boom, recordedThrowable)
        assertSame(thread, delegatedThread)
        assertSame(boom, delegatedThrowable)
    }

    @Test
    fun `handler tolerates a null previous handler and a throwing record fn`() {
        val throwingRecord: (
            String,
            Throwable,
            Long,
        ) -> Unit = { _, _, _ -> throw IllegalStateException("record blew up") }
        val handler = buildCrashHandler(previous = null, record = throwingRecord)
        handler.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertTrue(true)
    }
}
