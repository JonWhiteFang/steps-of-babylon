package com.whitefang.stepsofbabylon.presentation.battle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.battle.ui.BattleControlRail
import com.whitefang.stepsofbabylon.presentation.battle.ui.BattleErrorOverlay
import com.whitefang.stepsofbabylon.presentation.battle.ui.BiomeTransitionOverlay
import com.whitefang.stepsofbabylon.presentation.battle.ui.InRoundUpgradeMenu
import com.whitefang.stepsofbabylon.presentation.battle.ui.UltimateWeaponBar
import com.whitefang.stepsofbabylon.presentation.battle.ui.PauseOverlay
import com.whitefang.stepsofbabylon.presentation.battle.ui.PostRoundOverlay

@Composable
fun BattleScreen(
    onExitBattle: () -> Unit,
    viewModel: BattleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val surfaceView = remember { GameSurfaceView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // #233: lock the battle screen to portrait so a mid-round rotation can't recreate the
    // Activity/Composable → discard the GameSurfaceView/engine while the surviving BattleViewModel
    // still believes a round is in progress (the engine/VM desync that loses the round + can
    // mis-credit end-of-round persistence). Battle is portrait-designed (#171) and the app has no
    // landscape resources, so a one-time recreate at ENTER (if the device was in landscape) is
    // harmless — the round only starts after configure/startPollingEngine. Restored on exit so the
    // rest of the app keeps following the sensor. Touches no VM/engine/surface-survival logic.
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val roundActive = state.roundEndState == null
    // #190 REL-2: when the loop has crashed, suppress ALL interactive round chrome — the scrim
    // overlay doesn't block touches, so leaving the rail/quit/UW bar composed would let a tester
    // drive the stopped engine through it.
    val showGameChrome = roundActive && !state.battleError
    // #171: single source of truth for the left-edge inset, shared by the control rail (CenterStart)
    // and the upgrade-menu wrapper so the menu clears the rail by exactly GAP on any device — incl. a
    // side display cutout in landscape. systemBars ∪ displayCutout, Start side only (RTL-aware).
    val railStartInset = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start)
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface watchGemAd / watchPsAd ad-failure messages as a snackbar so testers see
    // why nothing happened when they tap "Watch ad" and AdMob returns NO_FILL or the
    // user dismisses the ad. Mirrors CardsScreen + LabsScreen + WorkshopScreen.
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    LaunchedEffect(state.speedMultiplier) { surfaceView.setSpeedMultiplier(state.speedMultiplier) }
    LaunchedEffect(state.isPaused) { surfaceView.setPaused(state.isPaused) }
    // Issue #19: ordering invariant — `surfaceView.configure(...)` MUST run before
    // `viewModel.startPollingEngine(...)` and BOTH MUST run only after the VM init
    // coroutine has loaded equipped weapons / cards / cosmetics from disk
    // (`state.isLoading == false`).
    //
    // Pre-fix: a separate `LaunchedEffect(surfaceView) { viewModel.startPollingEngine(...) }`
    // fired synchronously on first composition, calling `engine.initUWs(equippedWeapons)`
    // while `equippedWeapons` was still `emptyList()`. Subsequent `surfaceView.configure(...)`
    // called `engine.init(...)` which clears `uwStates` again, and `startPollingEngine` was
    // never re-invoked — leaving `uwStates` empty for the entire first round and silently
    // disabling the R4-06 auto-trigger gate (`if (uwStates.isNotEmpty() && ...)`).
    //
    // Post-fix: both calls live inside the same `LaunchedEffect(state.isLoading)` block,
    // ordered configure → startPollingEngine. `configure` triggers `engine.init` which
    // wipes `uwStates`; `startPollingEngine` then re-populates them via
    // `engine.initUWs(equippedWeapons)` with the now-loaded list. The block fires once
    // when `isLoading` flips from true → false (a one-way transition; never toggles back),
    // so the polling coroutine inside `startPollingEngine` is launched exactly once.
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            surfaceView.configure(viewModel.resolvedStats, viewModel.tier, viewModel.workshopLevels, viewModel.startWave)
            viewModel.startPollingEngine(surfaceView.engine, surfaceView)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())

        // Top-left: wave info + cash + battle-step counter.
        // top = 40.dp clears the engine-rendered ziggurat health bar (HealthBarRenderer draws it
        // at 40px..72px ≈ 36dp@2x density) with a small margin. This is intentionally NOT the old
        // 80.dp: that value bundled a status-bar (~24dp) + platform-ActionBar (~56dp) offset that
        // no longer applies — MainActivity is edge-to-edge and the Scaffold already supplies the
        // status-bar inset via innerPadding, and the ActionBar was removed app-wide in #159. The
        // stale 80.dp left the HUD text floating ~53dp below the health bar (verified on-device).
        Column(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 40.dp)) {
            Text(stringResource(R.string.battle_wave_header, state.currentWave, state.enemyCount), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(state.wavePhase.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { state.waveProgress },
                modifier = Modifier.width(120.dp).height(4.dp).padding(top = 2.dp),
                color = if (state.wavePhase == "SPAWNING") Color(0xFF4CAF50) else Color(0xFFFFA726),
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Text(stringResource(R.string.cash_amount, state.cash), color = Color(0xFFD4A843), style = MaterialTheme.typography.titleSmall)
            if (state.stepsEarnedThisRound > 0) {
                val stepsDesc = stringResource(R.string.battle_steps_earned_desc, state.stepsEarnedThisRound)
                Text(
                    stringResource(R.string.steps_earned_banner, state.stepsEarnedThisRound),
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { contentDescription = stepsDesc },
                )
            }
        }

        if (showGameChrome) {
            IconButton(onClick = { viewModel.quitRound() }, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.battle_cd_quit_round), tint = Color.White)
            }
        }

        // UW bar (passive cooldown indicator post-R4-06; auto-trigger handled in engine).
        // #171: now owns the bottom-center strip alone (speed/pause/upgrade moved to the left rail).
        // Nav-bar inset + 24dp lifts it above the system gesture handle — was a bare 72.dp chosen to
        // dodge the old bottom control row.
        if (showGameChrome && state.uwSlots.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                UltimateWeaponBar(slots = state.uwSlots)
            }
        }

        // #171: speed / pause / upgrade live on a left vertical rail (was a bottom-center Row that
        // overlapped the UW bar + upgrade menu). CenterStart clears the top-left HUD and the bottom UW
        // bar in portrait. The full-width upgrade menu below clears this rail vertically (its height sits
        // its top below the rail's bottom). See
        // docs/superpowers/specs/2026-06-15-battle-bottom-chrome-overlap-design.md.
        if (showGameChrome) {
            BattleControlRail(
                speedMultiplier = state.speedMultiplier,
                isPaused = state.isPaused,
                showUpgradeMenu = state.showUpgradeMenu,
                onSetSpeed = viewModel::setSpeed,
                onTogglePause = viewModel::togglePause,
                onToggleUpgradeMenu = viewModel::toggleUpgradeMenu,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(railStartInset),
            )
        }

        // Upgrade menu (#171): spans the FULL screen width along the bottom. It clears the left control
        // rail VERTICALLY — `InRoundUpgradeMenu`'s fixed `IN_ROUND_MENU_HEIGHT` is short enough that the
        // bottom-anchored sheet's top edge sits below the rail's bottom, so a full-width sheet never covers
        // the rail's lower buttons (rail stays tappable while shopping). Bottom nav-bar inset keeps the
        // sheet's controls clear of the gesture handle (flush otherwise — replaces the old flat 72.dp lift).
        // (Earlier this menu left-padded to dodge the rail horizontally; full-width + a shorter, scrolling
        // sheet reads better — the rail/menu separation is now vertical, not horizontal.)
        if (state.showUpgradeMenu && showGameChrome) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                InRoundUpgradeMenu(cash = state.cash, inRoundLevels = state.inRoundLevels,
                    onPurchase = viewModel::purchaseInRoundUpgrade, onDismiss = viewModel::toggleUpgradeMenu,
                    lastPurchaseFree = state.lastPurchaseFree,
                    describeEffect = viewModel::describeEffect)
            }
        }

        if (state.isPaused && showGameChrome) {
            PauseOverlay(onResume = { viewModel.togglePause() }, onQuitRound = { viewModel.quitRound() })
        }

        val showRoundEnd = remember { MutableTransitionState(false) }
        showRoundEnd.targetState = state.roundEndState != null
        state.roundEndState?.let { roundEnd ->
            AnimatedVisibility(visibleState = showRoundEnd, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                PostRoundOverlay(state = roundEnd, onPlayAgain = { viewModel.playAgain() }, onExitBattle = onExitBattle, onWatchGemAd = { viewModel.watchGemAd() }, onWatchPsAd = { viewModel.watchPsAd() })
            }
        }
        state.biomeTransition?.let { BiomeTransitionOverlay(info = it, onContinue = { viewModel.dismissBiomeTransition() }) }

        if (state.battleError) {
            BattleErrorOverlay(onReturnToMenu = onExitBattle)
        }

        // Snackbar last — stacks on top of every overlay, including PostRoundOverlay
        // where the ad-failure messages originate.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
