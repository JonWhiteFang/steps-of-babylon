package com.whitefang.stepsofbabylon.service

import android.app.Service
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #217: [StepCounterService] had zero test references. The crash-safety seam of its `onCreate`
 * (`startForegroundSafely`) and the notification-balance fold (`resolveDisplayBalance`) are already
 * covered as pure top-level functions. The remaining uncovered glue is the two injection-independent
 * lifecycle overrides — the [Service] contract the OS relies on.
 *
 * [StepCounterService] is `@AndroidEntryPoint` and its `onCreate` does Hilt-injected work plus a
 * typed `startForeground`, so a full `Robolectric.buildService(...).create()` would require the Hilt
 * graph (the wrong tool on the JVM lane). [onStartCommand] and [onBind] touch NO injected field, so
 * we cover them on a directly-constructed instance — confirming the START_STICKY restart contract
 * and the not-bindable contract without standing up Hilt. The injected `onCreate` path stays on its
 * pure seams + the instrumented lifecycle suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StepCounterServiceTest {
    private val service = StepCounterService()

    @Test
    fun `onStartCommand returns START_STICKY so the OS re-creates the service after a kill`() {
        assertEquals(
            "the step service must be sticky so step counting resumes after a process kill",
            Service.START_STICKY,
            service.onStartCommand(null, 0, 1),
        )
    }

    @Test
    fun `onBind returns null because the step service is start-only, not bound`() {
        assertNull(
            "StepCounterService is a started foreground service and must not be bindable",
            service.onBind(Intent()),
        )
    }
}
