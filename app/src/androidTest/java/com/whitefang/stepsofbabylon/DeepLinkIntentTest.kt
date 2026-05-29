package com.whitefang.stepsofbabylon

import android.content.Intent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whitefang.stepsofbabylon.presentation.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1X-08 second real instrumented suite. Verifies the `navigate_to` deep-link contract
 * (the one `MainActivity` reads via `intent.getStringExtra("navigate_to")` and routes
 * through `Screen.fromRoute` + `argumentFreeRoutes`) against the **real** Android framework.
 *
 * Value over the Robolectric `DeepLinkRoutingTest`: that suite extracts the extra from an
 * `Intent` under Robolectric's shadow implementation. Notification deep-links travel inside a
 * `PendingIntent`, which marshals the `Intent` through Binder — a genuine [Parcel] round-trip.
 * The headline test here writes the `navigate_to` extra into a real [Parcel] and reads it back
 * with the real framework, proving the deep-link string survives the marshalling that the
 * notification path actually relies on (Robolectric stubs Parcel and never exercises it).
 *
 * The remaining tests re-run the `MainActivity` routing gate (`Screen.fromRoute(route)
 * .takeIf { it.route in argumentFreeRoutes }`) on the real framework for the full route set
 * plus the fail-closed unknown-route path.
 *
 * No Hilt rule — this suite touches only `Intent` / `Parcel` / the pure-Kotlin `Screen` object.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkIntentTest {

    @Test
    fun navigateToExtraSurvivesRealParcelRoundTrip() {
        val original = Intent().putExtra("navigate_to", "supplies")

        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Intent.CREATOR.createFromParcel(parcel)
            assertEquals(
                "navigate_to extra must survive the Binder/Parcel marshalling a notification " +
                    "PendingIntent performs",
                "supplies",
                restored.getStringExtra("navigate_to"),
            )
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun missingNavigateToExtraIsNullAfterParcelRoundTrip() {
        val parcel = Parcel.obtain()
        try {
            Intent().writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Intent.CREATOR.createFromParcel(parcel)
            assertNull(restored.getStringExtra("navigate_to"))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun everyArgumentFreeRouteResolvesAndPassesTheRoutingGate() {
        // Mirrors MainActivity's deep-link handler:
        //   Screen.fromRoute(route)?.takeIf { it.route in argumentFreeRoutes }?.navigate(...)
        for (route in Screen.argumentFreeRoutes) {
            val intent = Intent().putExtra("navigate_to", route)
            val extracted = intent.getStringExtra("navigate_to")
            val screen = Screen.fromRoute(extracted)
            assertEquals("fromRoute($route) must resolve to the matching Screen", route, screen?.route)
            assertEquals(
                "route $route must pass the argumentFreeRoutes gate so MainActivity navigates",
                true,
                screen?.route in Screen.argumentFreeRoutes,
            )
        }
    }

    @Test
    fun unknownRouteFailsClosedThroughTheRoutingGate() {
        val intent = Intent().putExtra("navigate_to", "not_a_real_route")
        val screen = Screen.fromRoute(intent.getStringExtra("navigate_to"))
            ?.takeIf { it.route in Screen.argumentFreeRoutes }
        assertNull("Unknown deep-link routes must fall through to the start destination", screen)
    }
}
