package com.whitefang.stepsofbabylon.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * The NavOptions every bottom-nav tab tap uses. A tab tap means "go to that tab's ROOT": pop back to
 * the Home root (clearing any push-children such as Workshop→Cards/Weapons) and dedup the top entry.
 *
 * Deliberately **no** `saveState`/`restoreState`. The canonical multi-back-stack idiom saves and
 * restores each tab's entire nested sub-stack — correct only when nested screens live in the tab's
 * own navigation graph. This app is a **flat** NavHost where Cards/Weapons are push-children of
 * Workshop, so they were folded into "Workshop's saved branch" and `restoreState` resurrected the
 * detail screen when the user returned to the Workshop tab (#161, the restore-wrong-screen bug). The
 * fix is to not save/restore at all: a tab tap always lands on the tab root. (System Back and
 * Home-tile pushes don't go through here, so push-nav back behaviour is unchanged.)
 *
 * Extracted to a shared builder so the regression guard (`BottomNavRestoreTest`) drives the EXACT
 * NavOptions the bar uses, not a hand-copied approximation.
 */
fun NavOptionsBuilder.bottomNavOptions() {
    popUpTo(Screen.Home.route)
    launchSingleTop = true
}

@Composable
fun BottomNavBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        Screen.items.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) { bottomNavOptions() }
                    }
                },
                icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                label = { Text(stringResource(screen.labelRes)) },
            )
        }
    }
}
