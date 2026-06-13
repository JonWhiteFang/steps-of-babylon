package com.whitefang.stepsofbabylon.presentation.navigation

import androidx.navigation.testing.TestNavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the bottom-nav restore-wrong-screen bug (#161, Bundle B PR-B2).
 *
 * Drives the REAL shared tab-navigation NavOptions ([bottomNavOptions], used verbatim by
 * [BottomNavBar]) against a [TestNavHostController] wired to the REAL [Screen] routes, with Workshop
 * pushing Cards via the SAME optionless `navigate()` MainActivity uses. This faithfully reproduces
 * the on-device repro:
 *
 *   Home → Workshop(tab) → Cards(push) → Stats(tab) → Workshop(tab)
 *
 * Before the fix (`popUpTo(Home){saveState} + restoreState`), `restoreState` resurrected the saved
 * `[Workshop, Cards]` branch on the Workshop re-entry, so the user landed back on **Cards** instead of
 * the Workshop root. The fix drops saveState/restoreState so a tab tap always pops to that tab's root.
 *
 * JVM/Robolectric via [TestNavHostController] (no Compose UI rule / activity): the defect lives
 * entirely in `navigation-common`'s back-stack save/restore, which runs on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BottomNavRestoreTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.graph = navController.createGraph(startDestination = Screen.Home.route) {
            // Mirror the real flat NavHost: the 5 tabs + Cards as a push-child of Workshop.
            // Content is irrelevant to the back-stack behaviour under test, so it's empty.
            composable(Screen.Home.route) {}
            composable(Screen.Workshop.route) {}
            composable(Screen.Battle.route) {}
            composable(Screen.Labs.route) {}
            composable(Screen.Stats.route) {}
            composable(Screen.Cards.route) {}
        }
    }

    /** A bottom-nav tab tap — the real shared [bottomNavOptions] NavOptions. */
    private fun tapTab(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) { bottomNavOptions() }
        }
    }

    private fun currentRoute(): String? = navController.currentDestination?.route

    private fun backStackRoutes(): List<String> =
        navController.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun `returning to a tab after drilling into its push-child shows the tab root, not the child`() {
        tapTab(Screen.Workshop.route)                       // tab → Workshop root
        navController.navigate(Screen.Cards.route)          // push Cards (optionless, as Workshop does)
        tapTab(Screen.Stats.route)                          // switch away to another tab
        tapTab(Screen.Workshop.route)                       // return to the Workshop tab

        assertEquals(
            "Tapping the Workshop tab must land on the Workshop root, not the resurrected Cards child",
            Screen.Workshop.route,
            currentRoute(),
        )
        assertFalse(
            "The pushed Cards child must not be restored onto the back stack on tab re-entry",
            backStackRoutes().contains(Screen.Cards.route),
        )
    }

    @Test
    fun `tapping a bottom-nav tab navigates to that tab root`() {
        // Sanity guard: the fix must not break ordinary tab switching.
        tapTab(Screen.Stats.route)
        assertEquals(Screen.Stats.route, currentRoute())

        tapTab(Screen.Labs.route)
        assertEquals(Screen.Labs.route, currentRoute())

        tapTab(Screen.Home.route)
        assertEquals(Screen.Home.route, currentRoute())
    }
}
