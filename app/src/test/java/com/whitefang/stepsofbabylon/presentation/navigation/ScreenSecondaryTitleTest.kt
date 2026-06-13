package com.whitefang.stepsofbabylon.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenSecondaryTitleTest {

    @Test
    fun `secondaryTitle returns the explicit title for each of the 8 push-children`() {
        assertEquals("Ultimate Weapons", Screen.secondaryTitle(Screen.Weapons.route))
        assertEquals("Cards", Screen.secondaryTitle(Screen.Cards.route))
        assertEquals("Unclaimed Supplies", Screen.secondaryTitle(Screen.Supplies.route))
        assertEquals("Premium Currencies", Screen.secondaryTitle(Screen.Economy.route))
        assertEquals("Missions", Screen.secondaryTitle(Screen.Missions.route))
        assertEquals("Settings", Screen.secondaryTitle(Screen.Settings.route))
        assertEquals("Store", Screen.secondaryTitle(Screen.Store.route))
        assertEquals("Help", Screen.secondaryTitle(Screen.Help.route))
    }

    @Test
    fun `secondaryTitle is null for every bottom-nav tab`() {
        // Tabs reach themselves via the bottom nav; they get no back-arrow bar.
        Screen.items.forEach { tab ->
            assertNull("tab ${tab.route} must not get a bar", Screen.secondaryTitle(tab.route))
        }
    }

    @Test
    fun `secondaryTitle is null for Onboarding`() {
        // Onboarding is a self-contained carousel — not a tab (so not covered by the items
        // loop above) and not a secondary screen. Battle IS a tab, already covered above.
        assertNull(Screen.secondaryTitle(Screen.Onboarding.route))
    }

    @Test
    fun `secondaryTitle is null for an unknown route and for null`() {
        assertNull(Screen.secondaryTitle("not-a-real-route"))
        assertNull(Screen.secondaryTitle(null))
    }
}
