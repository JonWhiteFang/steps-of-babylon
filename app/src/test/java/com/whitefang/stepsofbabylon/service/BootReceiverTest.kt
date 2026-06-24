package com.whitefang.stepsofbabylon.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * #217: [BootReceiver] had zero test references. The pure seams of the step-counting glue are already
 * covered (`startForegroundSafely`/`resolveDisplayBalance`/catch-up), but the receiver's Android
 * wiring — the action gate, the ACTIVITY_RECOGNITION permission gate, and the actual
 * `startForegroundService(StepCounterService)` dispatch — was untested. This is the thin glue that
 * decides whether the step service is (re)started after a device boot.
 *
 * [BootReceiver] is a plain [android.content.BroadcastReceiver] (NOT `@AndroidEntryPoint`), so it is
 * fully Robolectric-testable on the JVM lane with no Hilt graph. We drive `onReceive` directly and
 * assert via `ShadowApplication` whether a service-start Intent was issued and what it targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BootReceiverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val shadowApplication = Shadows.shadowOf(application)
    private val receiver = BootReceiver()

    @Test
    fun `a non-BOOT_COMPLETED action starts no service`() {
        shadowApplication.grantPermissions(android.Manifest.permission.ACTIVITY_RECOGNITION)

        receiver.onReceive(application, Intent(Intent.ACTION_POWER_CONNECTED))

        assertNull(
            "a non-BOOT_COMPLETED action must short-circuit before any service start",
            shadowApplication.peekNextStartedService(),
        )
    }

    @Test
    fun `BOOT_COMPLETED with ACTIVITY_RECOGNITION denied starts no service`() {
        shadowApplication.denyPermissions(android.Manifest.permission.ACTIVITY_RECOGNITION)

        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(
            "without ACTIVITY_RECOGNITION the receiver must not start the step service",
            shadowApplication.peekNextStartedService(),
        )
    }

    @Test
    fun `BOOT_COMPLETED with ACTIVITY_RECOGNITION granted starts the StepCounterService`() {
        shadowApplication.grantPermissions(android.Manifest.permission.ACTIVITY_RECOGNITION)

        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        val started = shadowApplication.nextStartedService
        assertEquals(
            "the granted boot path must target StepCounterService",
            StepCounterService::class.java.name,
            started.component?.className,
        )
        assertEquals(
            "the service-start Intent must target this application's package",
            application.packageName,
            started.component?.packageName,
        )
    }
}
