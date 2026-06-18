package com.whitefang.stepsofbabylon.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #244: [StepCounterService.onCreate] called `startForeground(...)` with NO try/catch. On Android 14
 * (minSdk 34) a typed (health) foreground-service start can throw
 * `ForegroundServiceStartNotAllowedException` (background-start restriction — most exposed via
 * [BootReceiver], which starts the service from a background BOOT_COMPLETED broadcast),
 * `InvalidForegroundServiceTypeException`, or `SecurityException`. Any throw propagated out of
 * onCreate and crashed the process — the very background path the step counter depends on — and
 * START_STICKY would retry straight back into the same crash.
 *
 * The fix routes the start through [startForegroundSafely], which catches a [RuntimeException]
 * (covers all three: FGS-not-allowed/invalid-type extend IllegalStateException/IllegalArgumentException;
 * plus SecurityException), reports it, and returns `false` so the caller can stopSelf() gracefully
 * (WorkManager's StepSyncWorker handles catch-up) instead of letting the process die.
 *
 * Pure top-level seam (mirrors [resolveDisplayBalance]) so the crash-safety contract is JVM-tested
 * without a Hilt service / Robolectric: the wiring in onCreate is a thin application of it.
 */
class StartForegroundSafelyTest {

    @Test
    fun `a successful start returns true and does not invoke the failure handler`() {
        var failed: Throwable? = null
        var startCalls = 0

        val ok = startForegroundSafely(
            start = { startCalls++ },
            onFailure = { failed = it },
        )

        assertTrue(ok, "a clean start must return true")
        assertEquals(1, startCalls)
        assertEquals(null, failed, "onFailure must NOT fire on a successful start")
    }

    @Test
    fun `a ForegroundServiceStartNotAllowed-style failure is caught and reported, not propagated`() {
        // ForegroundServiceStartNotAllowedException extends IllegalStateException; we model it with
        // the supertype so the test is API-level-independent.
        val boom = IllegalStateException("ForegroundServiceStartNotAllowed")
        var reported: Throwable? = null

        val ok = startForegroundSafely(
            start = { throw boom },
            onFailure = { reported = it },
        )

        assertFalse(ok, "a failed FGS start must return false so the caller can stopSelf()")
        assertSame(boom, reported, "the originating exception must be passed to onFailure")
    }

    @Test
    fun `a SecurityException from the FGS start is caught and reported`() {
        val boom = SecurityException("missing FGS-type prerequisite")
        var reported: Throwable? = null

        val ok = startForegroundSafely(start = { throw boom }, onFailure = { reported = it })

        assertFalse(ok)
        assertSame(boom, reported)
    }
}
