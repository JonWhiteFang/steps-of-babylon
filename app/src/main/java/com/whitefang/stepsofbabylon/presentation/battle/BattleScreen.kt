package com.whitefang.stepsofbabylon.presentation.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val roundActive = state.roundEndState == null
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

        // Top-left: wave info + cash + battle-step counter
        Column(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 80.dp)) {
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

        if (roundActive) {
            IconButton(onClick = { viewModel.quitRound() }, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.battle_cd_quit_round), tint = Color.White)
            }
        }

        // UW bar (passive cooldown indicator post-R4-06; auto-trigger handled in engine)
        if (roundActive && state.uwSlots.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                UltimateWeaponBar(slots = state.uwSlots)
            }
        }

        // Bottom controls
        if (roundActive) {
            // R3-04 / GitHub #3: bottom control row (3× speed + Pause + Upgrade) historically
            // overflowed the right edge of narrow phones (e.g. Pixel 6, 411dp wide). The
            // Overdrive button was removed in R4-01 (5 buttons now, was 6) but the scroll
            // safety net stays in place because future R4 work (Rapid Fire indicator etc.)
            // may grow the row again. Layout invariant:
            //   - `windowInsetsPadding(navigationBars)` lifts the row above the system
            //     gesture handle so it isn't competing with the swipe-up area.
            //   - `horizontalScroll(rememberScrollState())` on the inner content lets the
            //     row scroll horizontally on screens too narrow to show every button.
            //   - The background+rounded corners stay on the inner Row so the pill follows
            //     the buttons (and only the buttons), not the full viewport.
            // Pure layout change — no behaviour change to any individual button. Verified
            // on-device on the next AAB; no JVM regression test (Compose UI surface).
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
                    .horizontalScroll(rememberScrollState())
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(1f, 2f, 4f).forEach { speed ->
                    val desc = stringResource(R.string.battle_cd_speed, speed.toInt())
                    val label = stringResource(R.string.battle_speed_label, speed.toInt())
                    if (state.speedMultiplier == speed) {
                        Button(onClick = {}, modifier = Modifier.semantics { contentDescription = desc }) { Text(label) }
                    } else {
                        FilledTonalButton(onClick = { viewModel.setSpeed(speed) },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.semantics { contentDescription = desc },
                        ) { Text(label, color = Color.White) }
                    }
                }
                val pauseDesc = stringResource(if (state.isPaused) R.string.action_resume else R.string.battle_cd_pause)
                FilledTonalButton(onClick = { viewModel.togglePause() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.isPaused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = pauseDesc },
                ) { Text(if (state.isPaused) "▶" else "⏸", color = Color.White) }

                val upgradesDesc = stringResource(R.string.battle_cd_upgrades)
                FilledTonalButton(onClick = { viewModel.toggleUpgradeMenu() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.showUpgradeMenu) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = upgradesDesc },
                ) { Icon(Icons.Filled.Upgrade, contentDescription = null, tint = Color.White) }
            }
        }

        // Upgrade menu
        if (state.showUpgradeMenu && roundActive) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                InRoundUpgradeMenu(cash = state.cash, inRoundLevels = state.inRoundLevels,
                    onPurchase = viewModel::purchaseInRoundUpgrade, onDismiss = viewModel::toggleUpgradeMenu,
                    lastPurchaseFree = state.lastPurchaseFree,
                    describeEffect = viewModel::describeEffect)
            }
        }

        if (state.isPaused && roundActive) {
            PauseOverlay(onResume = { viewModel.togglePause() }, onQuitRound = { viewModel.quitRound() })
        }

        state.roundEndState?.let { PostRoundOverlay(state = it, onPlayAgain = { viewModel.playAgain() }, onExitBattle = onExitBattle, onWatchGemAd = { viewModel.watchGemAd() }, onWatchPsAd = { viewModel.watchPsAd() }) }
        state.biomeTransition?.let { BiomeTransitionOverlay(info = it, onContinue = { viewModel.dismissBiomeTransition() }) }

        // Snackbar last — stacks on top of every overlay, including PostRoundOverlay
        // where the ad-failure messages originate.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
