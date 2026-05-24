package com.whitefang.stepsofbabylon.presentation

import android.content.Intent
import com.whitefang.stepsofbabylon.presentation.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeepLinkRoutingTest {

    // --- Intent extra extraction (preserves existing contract) ---

    @Test
    fun `intent with navigate_to supplies extracts correctly`() {
        val intent = Intent().putExtra("navigate_to", "supplies")
        assertEquals("supplies", intent.getStringExtra("navigate_to"))
    }

    @Test
    fun `intent with navigate_to workshop extracts correctly`() {
        val intent = Intent().putExtra("navigate_to", "workshop")
        assertEquals("workshop", intent.getStringExtra("navigate_to"))
    }

    @Test
    fun `intent without navigate_to returns null`() {
        val intent = Intent()
        assertNull(intent.getStringExtra("navigate_to"))
    }

    // --- Screen.fromRoute coverage (A.5) ---
    // Every one of the 13 routes wired in NavHost must resolve back to its
    // Screen object. A silent regression here would disable notification
    // deep-links without test failure.

    @Test
    fun `fromRoute resolves home`() {
        assertSame(Screen.Home, Screen.fromRoute("home"))
    }

    @Test
    fun `fromRoute resolves store`() {
        assertSame(Screen.Store, Screen.fromRoute("store"))
    }

    @Test
    fun `fromRoute resolves help`() {
        assertSame(Screen.Help, Screen.fromRoute("help"))
    }

    @Test
    fun `fromRoute resolves stats`() {
        assertSame(Screen.Stats, Screen.fromRoute("stats"))
    }

    @Test
    fun `fromRoute resolves weapons`() {
        assertSame(Screen.Weapons, Screen.fromRoute("weapons"))
    }

    @Test
    fun `fromRoute resolves cards`() {
        assertSame(Screen.Cards, Screen.fromRoute("cards"))
    }

    @Test
    fun `fromRoute resolves economy`() {
        assertSame(Screen.Economy, Screen.fromRoute("economy"))
    }

    @Test
    fun `fromRoute resolves settings`() {
        assertSame(Screen.Settings, Screen.fromRoute("settings"))
    }

    @Test
    fun `fromRoute resolves labs`() {
        assertSame(Screen.Labs, Screen.fromRoute("labs"))
    }

    @Test
    fun `fromRoute resolves battle`() {
        assertSame(Screen.Battle, Screen.fromRoute("battle"))
    }

    @Test
    fun `fromRoute resolves missions`() {
        assertSame(Screen.Missions, Screen.fromRoute("missions"))
    }

    @Test
    fun `fromRoute resolves supplies and workshop (regression for prior whitelist)`() {
        assertSame(Screen.Supplies, Screen.fromRoute("supplies"))
        assertSame(Screen.Workshop, Screen.fromRoute("workshop"))
    }

    // --- fromRoute null / unknown handling ---

    @Test
    fun `fromRoute returns null for unknown route`() {
        assertNull(Screen.fromRoute("not_a_real_route"))
    }

    @Test
    fun `fromRoute returns null for null input`() {
        assertNull(Screen.fromRoute(null))
    }

    @Test
    fun `fromRoute is case-sensitive - uppercase does not match`() {
        // Route strings are stored lowercase; callers must normalise before
        // lookup. An uppercase deep-link should fail-closed, not cross-match.
        assertNull(Screen.fromRoute("WORKSHOP"))
    }

    // --- argumentFreeRoutes whitelist ---

    @Test
    fun `argumentFreeRoutes contains all 13 current screens`() {
        val expected = setOf(
            "home", "workshop", "battle", "labs", "stats",
            "weapons", "cards", "supplies", "economy", "missions",
            "settings", "store", "help",
        )
        assertEquals(expected, Screen.argumentFreeRoutes)
    }

    @Test
    fun `argumentFreeRoutes excludes unknown routes`() {
        assertFalse("sentinel guard - unknown routes must not silently pass",
            "fake_route" in Screen.argumentFreeRoutes)
    }

    @Test
    fun `argumentFreeRoutes whitelist gate accepts all fromRoute results`() {
        // Round-trip: every Screen reachable via fromRoute must also satisfy
        // the whitelist check, otherwise the MainActivity deep-link handler
        // would drop navigations it should accept.
        val allRouteStrings = listOf(
            "home", "workshop", "battle", "labs", "stats",
            "weapons", "cards", "supplies", "economy", "missions",
            "settings", "store",
        )
        for (route in allRouteStrings) {
            val screen = Screen.fromRoute(route)
            assertTrue("fromRoute($route) should resolve", screen != null)
            assertTrue(
                "route $route must be in argumentFreeRoutes",
                screen!!.route in Screen.argumentFreeRoutes,
            )
        }
    }
}
