package com.whitefang.stepsofbabylon.presentation.navigation

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

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Workshop : Screen("workshop", "Workshop", Icons.Default.Build)
    data object Battle : Screen("battle", "Battle", Icons.Default.PlayArrow)
    data object Labs : Screen("labs", "Labs", Icons.Default.Search)
    data object Stats : Screen("stats", "Stats", Icons.Filled.BarChart)
    data object Weapons : Screen("weapons", "Weapons", Icons.Filled.AutoAwesome)
    data object Cards : Screen("cards", "Cards", Icons.Filled.Style)
    data object Supplies : Screen("supplies", "Supplies", Icons.Filled.Inbox)
    data object Economy : Screen("economy", "Economy", Icons.Filled.AttachMoney)
    data object Missions : Screen("missions", "Missions", Icons.Filled.Flag)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Store : Screen("store", "Store", Icons.Filled.ShoppingCart)
    data object Help : Screen("help", "Help", Icons.AutoMirrored.Filled.HelpOutline)

    companion object {
        val items by lazy { listOf(Home, Workshop, Battle, Labs, Stats) }

        // All 12 screens, needed for O(1) deep-link lookup. Uses `by lazy` for
        // the same reason as `items` above — sealed-class init order can NPE if
        // this list is evaluated before all data objects are constructed
        // (see commit 1872af9).
        private val allScreens by lazy {
            listOf(Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store, Help)
        }

        /**
         * Deep-link routes that can be navigated to directly from an Intent
         * extra (`navigate_to=<route>`). Currently every screen in the app is
         * argument-free — if a future screen accepts route args, exclude it
         * here and route it through a dedicated deep-link handler instead of
         * the generic whitelist.
         */
        val argumentFreeRoutes: Set<String> by lazy { allScreens.map { it.route }.toSet() }

        /**
         * Resolves a route name (e.g. `"workshop"`) to its [Screen], or null if
         * no screen matches. Callers should typically gate navigation on
         * [argumentFreeRoutes] to avoid launching a screen that expects args
         * it has no way to provide from a deep-link.
         */
        fun fromRoute(name: String?): Screen? =
            name?.let { n -> allScreens.firstOrNull { it.route == n } }
    }
}
