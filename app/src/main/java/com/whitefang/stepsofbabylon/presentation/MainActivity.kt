package com.whitefang.stepsofbabylon.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
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
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingScreen
import com.whitefang.stepsofbabylon.presentation.audio.MusicManager
import com.whitefang.stepsofbabylon.presentation.audio.MusicPreferences
import com.whitefang.stepsofbabylon.presentation.battle.BattleScreen
import com.whitefang.stepsofbabylon.presentation.cards.CardsScreen
import com.whitefang.stepsofbabylon.presentation.economy.CurrencyDashboardScreen
import com.whitefang.stepsofbabylon.presentation.home.HomeScreen
import com.whitefang.stepsofbabylon.presentation.labs.LabsScreen
import com.whitefang.stepsofbabylon.presentation.missions.MissionsScreen
import com.whitefang.stepsofbabylon.presentation.navigation.BottomNavBar
import com.whitefang.stepsofbabylon.presentation.navigation.Screen
import com.whitefang.stepsofbabylon.presentation.settings.SettingsScreen
import com.whitefang.stepsofbabylon.presentation.stats.StatsScreen
import com.whitefang.stepsofbabylon.presentation.store.StoreScreen
import com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesScreen
import com.whitefang.stepsofbabylon.presentation.ui.SobTopAppBar
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
    @Inject lateinit var musicPreferences: MusicPreferences
    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    private lateinit var musicManager: MusicManager

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
        musicManager = MusicManager(this).apply {
            setVolume(musicPreferences.getVolume())
            setMuted(musicPreferences.isMuted())
        }
        enableEdgeToEdge()
        setContent {
            StepsOfBabylonTheme {
                val context = LocalContext.current
                val navController = rememberNavController()

                var onboardingComplete by remember {
                    mutableStateOf(onboardingPreferences.hasCompletedOnboarding())
                }
                var permissionAsked by remember { mutableStateOf(false) }
                var stepCountingGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACTIVITY_RECOGNITION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                // Permanently-denied recovery (spec §4): when set, the Scaffold snackbar offers a
                // deep-link to app settings instead of the current silent no-op. Reset after shown.
                val snackbarHostState = remember { SnackbarHostState() }
                var showStepPermissionSettingsHint by remember { mutableStateOf(false) }

                val hcPermissionLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                ) { }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val activityGranted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true
                    permissionAsked = true
                    stepCountingGranted = activityGranted
                    if (activityGranted) {
                        context.startForegroundService(
                            Intent(context, StepCounterService::class.java)
                        )
                        if (healthConnectWrapper.isAvailable()) {
                            hcPermissionLauncher.launch(healthConnectWrapper.getRequiredPermissions())
                        }
                    } else if (onboardingComplete &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION
                        )
                    ) {
                        // Permanently denied ("Don't ask again") AND past onboarding: a bare
                        // launch() is now a silent no-op, so surface the Settings-recovery hint
                        // instead of stranding the player (spec §4). During onboarding itself the
                        // carousel's own "Open Settings" affordance handles this, so we don't
                        // double up while !onboardingComplete.
                        showStepPermissionSettingsHint = true
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

                    // Gate ONLY the cold request: on a fresh install (onboarding not yet
                    // complete) the onboarding final slide owns the first ask, so we must
                    // not fire a context-free system dialog over the carousel. On later
                    // launches (onboarding complete) this resumes its normal re-prompt role —
                    // and because permanent-denial recovery lives in the launcher callback
                    // (Step 3), the previously-silent no-op now surfaces the Settings hint.
                    if (onboardingComplete && (!activityGranted || !notifGranted)) {
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
                            // Don't navigate over the onboarding carousel (first launch OR replay).
                            // A brand-new install has no scheduled notifications to deep-link from,
                            // and during a replay the user is mid-tutorial — drop rather than
                            // buffer; the route is reissued by the notification tap if it recurs.
                            val onOnboarding = navController.currentBackStackEntry
                                ?.destination?.route == Screen.Onboarding.route
                            if (!onOnboarding) {
                                Screen.fromRoute(route)
                                    ?.takeIf { it.route in Screen.argumentFreeRoutes }
                                    ?.let { navController.navigate(it.route) }
                            }
                            pendingNavigation.value = null
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        val topBarEntry by navController.currentBackStackEntryAsState()
                        val topBarRoute = topBarEntry?.destination?.route
                        Screen.secondaryTitle(topBarRoute)?.let { title ->
                            SobTopAppBar(
                                title = title,
                                onNavigateBack = { navController.navigateUp() },
                            )
                        }
                    },
                    bottomBar = {
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route
                        // Switch music track based on current screen
                        LaunchedEffect(currentRoute) {
                            if (currentRoute == Screen.Battle.route) {
                                musicManager.playBattle()
                            } else if (currentRoute != null) {
                                musicManager.playWalking()
                            }
                        }
                        // Hide the bottom nav on the immersive battle screen AND on the
                        // first-launch/replay onboarding carousel — the tutorial must read as a
                        // self-contained first impression, not a tab the player can escape from.
                        if (currentRoute != Screen.Battle.route &&
                            currentRoute != Screen.Onboarding.route
                        ) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    LaunchedEffect(showStepPermissionSettingsHint) {
                        if (showStepPermissionSettingsHint) {
                            val result = snackbarHostState.showSnackbar(
                                message = "Step counting is off — enable it in Settings",
                                actionLabel = "Settings",
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            }
                            // Reset AFTER await (not before): resetting first would change this
                            // effect's key true->false and cancel the in-flight showSnackbar. The
                            // only cost is a possible re-show if the device rotates while the
                            // snackbar is visible — cosmetic and rare; correctness wins here.
                            showStepPermissionSettingsHint = false
                        }
                    }
                    val reducedMotion = remember {
                        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    }
                    val enterAnim = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 }
                    val exitAnim = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.startDestination(onboardingComplete),
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
                        composable(Screen.Onboarding.route) {
                            val reducedMotion = remember {
                                com.whitefang.stepsofbabylon.presentation.battle.effects
                                    .ReducedMotionCheck.isReducedMotionEnabled(context)
                            }
                            OnboardingScreen(
                                stepCountingGranted = stepCountingGranted,
                                permissionAsked = permissionAsked,
                                reducedMotion = reducedMotion,
                                onEnableStepCounting = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACTIVITY_RECOGNITION,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    )
                                },
                                onOpenAppSettings = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        )
                                    )
                                },
                                onFinished = {
                                    onboardingComplete = true
                                    // previousBackStackEntry is null ONLY when Onboarding was the
                                    // start destination, i.e. first launch.
                                    if (navController.previousBackStackEntry == null) {
                                        // First launch: go to Home and clear Onboarding off the
                                        // back stack so system Back doesn't re-enter the carousel.
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        // Replay from Settings: return to Settings.
                                        navController.popBackStack()
                                    }
                                },
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
                            SettingsScreen(
                                onReplayTutorial = { navController.navigate(Screen.Onboarding.route) },
                            )
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
        musicManager.resume()
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
        musicManager.pause()
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
        musicManager.release()
        activityScope.cancel()
        super.onDestroy()
    }
}

