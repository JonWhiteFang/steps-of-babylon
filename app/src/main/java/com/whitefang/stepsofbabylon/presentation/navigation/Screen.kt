package com.whitefang.stepsofbabylon.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector
import com.whitefang.stepsofbabylon.R

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)

    data object Workshop : Screen("workshop", R.string.nav_workshop, Icons.Default.Build)

    data object Battle : Screen("battle", R.string.nav_battle, Icons.Default.PlayArrow)

    data object Labs : Screen("labs", R.string.nav_labs, Icons.Default.Search)

    data object Stats : Screen("stats", R.string.nav_stats, Icons.Filled.BarChart)

    data object Weapons : Screen("weapons", R.string.nav_weapons, Icons.Filled.AutoAwesome)

    data object Cards : Screen("cards", R.string.nav_cards, Icons.Filled.Style)

    data object Supplies : Screen("supplies", R.string.nav_supplies, Icons.Filled.Inbox)

    data object Economy : Screen("economy", R.string.nav_economy, Icons.Filled.AttachMoney)

    data object Missions : Screen("missions", R.string.nav_missions, Icons.Filled.Flag)

    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)

    data object Store : Screen("store", R.string.nav_store, Icons.Filled.ShoppingCart)

    data object Help : Screen("help", R.string.nav_help, Icons.AutoMirrored.Filled.HelpOutline)

    // First-run / replay-only route. NOT in `items`, `allScreens`, or `argumentFreeRoutes`
    // (so it can't be reached as a public navigate_to deep-link target). The icon is
    // unused — Onboarding never appears in the bottom nav. Reached only via literal
    // Screen.Onboarding.route navigation (start destination on first launch; Settings replay).
    data object Onboarding : Screen("onboarding", R.string.nav_onboarding, Icons.AutoMirrored.Filled.HelpOutline)

    companion object {
        val items by lazy { listOf(Home, Workshop, Battle, Labs, Stats) }

        // All 13 non-onboarding screens, needed for O(1) deep-link lookup. Uses `by lazy`
        // for the same reason as `items` above — sealed-class init order can NPE if this
        // list is evaluated before all data objects are constructed (see commit 1872af9).
        // Onboarding is deliberately excluded: it must never be a public navigate_to target.
        private val allScreens by lazy {
            listOf(
                Home,
                Workshop,
                Battle,
                Labs,
                Stats,
                Weapons,
                Cards,
                Supplies,
                Economy,
                Missions,
                Settings,
                Store,
                Help,
            )
        }

        /**
         * Deep-link routes that can be navigated to directly from an Intent extra
         * (`navigate_to=<route>`). Onboarding is intentionally absent — it is a
         * first-run/replay-only route, never a public deep-link target.
         */
        val argumentFreeRoutes: Set<String> by lazy { allScreens.map { it.route }.toSet() }

        /**
         * Resolves a route name to its [Screen], or null if no screen matches.
         * Includes [Onboarding] (so internal navigation/tests can resolve it) even though
         * Onboarding is excluded from [argumentFreeRoutes]; deep-link callers gate on
         * [argumentFreeRoutes], so resolving Onboarding here does not make it a deep-link target.
         */
        fun fromRoute(name: String?): Screen? =
            name?.let { n -> (allScreens + Onboarding).firstOrNull { it.route == n } }

        /**
         * The NavHost start destination, chosen from the synchronous onboarding-completion
         * flag. Pure (route strings only) so it is unit-testable. NavHost captures
         * startDestination only on first composition — callers MUST pass a synchronous read,
         * not an async StateFlow default.
         */
        fun startDestination(hasCompletedOnboarding: Boolean): String =
            if (hasCompletedOnboarding) Home.route else Onboarding.route

        /**
         * The top-app-bar title for the 8 secondary (push-navigated) screens that get a back
         * affordance (Bundle B / #161, ADR-0022 follow-up). Returns null for bottom-nav tabs,
         * Battle (a tab with its own exit affordance), Onboarding (a self-contained carousel),
         * and unknown routes — those render NO SobTopAppBar.
         *
         * Pure (route strings only) so it is unit-testable and so adding the bar never touches the
         * `by lazy` route lists or the pinned deep-link set. Titles are deliberately explicit, NOT
         * derived from `label` (e.g. Supplies → "Unclaimed Supplies", Economy → "Premium Currencies"
         * read better as headers than their narrow tab labels).
         */
        @StringRes
        fun secondaryTitle(route: String?): Int? =
            when (route) {
                Weapons.route -> R.string.screen_title_weapons
                Cards.route -> R.string.screen_title_cards
                Supplies.route -> R.string.screen_title_supplies
                Economy.route -> R.string.screen_title_economy
                Missions.route -> R.string.screen_title_missions
                Settings.route -> R.string.screen_title_settings
                Store.route -> R.string.screen_title_store
                Help.route -> R.string.screen_title_help
                else -> null
            }
    }
}
