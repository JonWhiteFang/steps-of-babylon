package com.whitefang.stepsofbabylon.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.data.ads.internal.ConsentManager
import com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider
import com.whitefang.stepsofbabylon.data.healthconnect.HealthConnectClientWrapper
import com.whitefang.stepsofbabylon.presentation.battle.BattleScreen
import com.whitefang.stepsofbabylon.presentation.cards.CardsScreen
import com.whitefang.stepsofbabylon.presentation.economy.CurrencyDashboardScreen
import com.whitefang.stepsofbabylon.presentation.home.HomeScreen
import com.whitefang.stepsofbabylon.presentation.labs.LabsScreen
import com.whitefang.stepsofbabylon.presentation.missions.MissionsScreen
import com.whitefang.stepsofbabylon.presentation.navigation.BottomNavBar
import com.whitefang.stepsofbabylon.presentation.navigation.Screen
import com.whitefang.stepsofbabylon.presentation.settings.NotificationSettingsScreen
import com.whitefang.stepsofbabylon.presentation.stats.StatsScreen
import com.whitefang.stepsofbabylon.presentation.store.StoreScreen
import com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesScreen
import com.whitefang.stepsofbabylon.presentation.ui.theme.StepsOfBabylonTheme
import com.whitefang.stepsofbabylon.presentation.weapons.UltimateWeaponScreen
import com.whitefang.stepsofbabylon.presentation.workshop.WorkshopScreen
import com.whitefang.stepsofbabylon.service.StepCounterService
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthConnectWrapper: HealthConnectClientWrapper
    @Inject lateinit var playerRepository: PlayerRepository
    @Inject internal lateinit var activityProvider: ActivityProvider
    @Inject internal lateinit var consentManager: ConsentManager

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingNavigation = MutableStateFlow<String?>(null)

    /**
     * Ensures the UMP consent prefetch fires at most once per Activity lifecycle, even
     * across multiple [onResume] calls during a session. UMP's own [ConsentManager.ensureInitialized]
     * is already idempotent, but guarding here avoids launching a coroutine that would immediately
     * no-op on every resume. Reset in [onDestroy] along with [activityScope] cancellation.
     */
    private val consentPrefetchAttempted = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StepsOfBabylonTheme {
                val context = LocalContext.current
                val navController = rememberNavController()

                val hcPermissionLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                ) { }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val activityGranted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true
                    if (activityGranted) {
                        context.startForegroundService(
                            Intent(context, StepCounterService::class.java)
                        )
                        if (healthConnectWrapper.isAvailable()) {
                            hcPermissionLauncher.launch(healthConnectWrapper.getRequiredPermissions())
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val activityGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
                    val notifGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (activityGranted) {
                        context.startForegroundService(
                            Intent(context, StepCounterService::class.java)
                        )
                        if (healthConnectWrapper.isAvailable() && !healthConnectWrapper.hasPermissions()) {
                            hcPermissionLauncher.launch(healthConnectWrapper.getRequiredPermissions())
                        }
                    }

                    if (!activityGranted || !notifGranted) {
                        val needed = buildList {
                            if (!activityGranted) add(Manifest.permission.ACTIVITY_RECOGNITION)
                            if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(needed.toTypedArray())
                    }

                    // Push initial intent deep-link
                    intent?.getStringExtra("navigate_to")?.let { pendingNavigation.value = it }
                }

                LaunchedEffect(Unit) {
                    pendingNavigation.collect { route ->
                        if (route != null) {
                            // Generic deep-link handler for argument-free routes
                            // (currently all 12). Unknown route strings fall
                            // through to the start destination silently rather
                            // than crashing; this matches the prior 4-route
                            // `when` behaviour for unmapped values.
                            Screen.fromRoute(route)
                                ?.takeIf { it.route in Screen.argumentFreeRoutes }
                                ?.let { navController.navigate(it.route) }
                            pendingNavigation.value = null
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        if (backStackEntry?.destination?.route != Screen.Battle.route) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    val reducedMotion = remember {
                        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    }
                    val enterAnim = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 }
                    val exitAnim = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = { enterAnim },
                        exitTransition = { exitAnim },
                        popEnterTransition = { if (reducedMotion) fadeIn(snap()) else fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 4 } },
                        popExitTransition = { if (reducedMotion) fadeOut(snap()) else fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 } },
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onBattleClick = { navController.navigate(Screen.Battle.route) },
                                onSuppliesClick = { navController.navigate(Screen.Supplies.route) },
                                onEconomyClick = { navController.navigate(Screen.Economy.route) },
                                onMissionsClick = { navController.navigate(Screen.Missions.route) },
                                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                                onStoreClick = { navController.navigate(Screen.Store.route) },
                                onHelpClick = { navController.navigate(Screen.Help.route) },
                            )
                        }
                        composable(Screen.Workshop.route) {
                            WorkshopScreen(
                                onNavigateToWeapons = { navController.navigate(Screen.Weapons.route) },
                                onNavigateToCards = { navController.navigate(Screen.Cards.route) },
                            )
                        }
                        composable(Screen.Weapons.route) {
                            UltimateWeaponScreen()
                        }
                        composable(Screen.Cards.route) {
                            CardsScreen()
                        }
                        composable(Screen.Battle.route) {
                            BattleScreen(onExitBattle = { navController.popBackStack() })
                        }
                        composable(Screen.Labs.route) {
                            LabsScreen()
                        }
                        composable(Screen.Stats.route) {
                            StatsScreen()
                        }
                        composable(Screen.Supplies.route) {
                            UnclaimedSuppliesScreen()
                        }
                        composable(Screen.Economy.route) {
                            CurrencyDashboardScreen(
                                onStoreClick = { navController.navigate(Screen.Store.route) },
                            )
                        }
                        composable(Screen.Missions.route) {
                            MissionsScreen()
                        }
                        composable(Screen.Settings.route) {
                            NotificationSettingsScreen()
                        }
                        composable(Screen.Store.route) {
                            StoreScreen()
                        }
                        composable(Screen.Help.route) {
                            com.whitefang.stepsofbabylon.presentation.help.HelpScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register this Activity with ActivityProvider so BillingManagerImpl can launch
        // Play Billing purchase flows (BillingClient.launchBillingFlow requires an
        // Activity, not a Context). C.5 PR 2 / ADR-0005.
        activityProvider.set(this)
        activityScope.launch(Dispatchers.IO) {
            playerRepository.updateLastActiveAt(System.currentTimeMillis())
        }
        // Prefetch UMP consent so the first reward-ad tap doesn't pay the
        // ~200-500ms UMP init latency. Flag-gated on BuildConfig.USE_REAL_ADS so debug
        // emulators without Play Services don't hit UMP on every resume — the real
        // RewardAdManagerImpl is still bound in debug (C.6 PR 3 deleted the stub), but
        // a bare emulator would just log UMP errors on every start.
        // One-shot per Activity lifecycle via [consentPrefetchAttempted]; UMP itself is
        // idempotent across calls, so a stray second invocation is harmless but wasteful.
        // RealConsentManager catches errors internally and logs them, so no try/catch here.
        // C.6 PR 2 / PR 3 / ADR-0006.
        if (BuildConfig.USE_REAL_ADS && consentPrefetchAttempted.compareAndSet(false, true)) {
            activityScope.launch {
                consentManager.ensureInitialized(this@MainActivity)
            }
        }
    }

    override fun onPause() {
        // Clear before super so nothing observes a stale Activity reference mid-teardown.
        // The WeakReference in ActivityProvider would also let the ref die, but an explicit
        // clear avoids purchase attempts racing with a paused-but-not-yet-GC'd Activity.
        // C.5 PR 2.
        activityProvider.clear()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("navigate_to")?.let { pendingNavigation.value = it }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

