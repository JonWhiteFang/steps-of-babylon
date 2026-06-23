package com.whitefang.stepsofbabylon.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingRoutingTest {
    @Test
    fun `startDestination is Onboarding when not completed`() {
        assertEquals(Screen.Onboarding.route, Screen.startDestination(hasCompletedOnboarding = false))
    }

    @Test
    fun `startDestination is Home when completed`() {
        assertEquals(Screen.Home.route, Screen.startDestination(hasCompletedOnboarding = true))
    }

    @Test
    fun `fromRoute resolves onboarding`() {
        assertSame(Screen.Onboarding, Screen.fromRoute("onboarding"))
    }

    @Test
    fun `onboarding is NOT a public deep-link target`() {
        assertFalse("onboarding" in Screen.argumentFreeRoutes)
    }

    @Test
    fun `onboarding is not in bottom-nav items`() {
        assertFalse(Screen.items.any { it.route == "onboarding" })
    }
}
