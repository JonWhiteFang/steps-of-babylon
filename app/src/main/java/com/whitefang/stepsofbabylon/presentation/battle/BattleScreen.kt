package com.whitefang.stepsofbabylon.presentation.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.whitefang.stepsofbabylon.presentation.battle.ui.BiomeTransitionOverlay
import com.whitefang.stepsofbabylon.presentation.battle.ui.InRoundUpgradeMenu
import com.whitefang.stepsofbabylon.presentation.battle.ui.OverdriveMenu
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

    LaunchedEffect(surfaceView) { viewModel.startPollingEngine(surfaceView.engine, surfaceView) }
    LaunchedEffect(state.speedMultiplier) { surfaceView.setSpeedMultiplier(state.speedMultiplier) }
    LaunchedEffect(state.isPaused) { surfaceView.setPaused(state.isPaused) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) surfaceView.configure(viewModel.resolvedStats, viewModel.tier, viewModel.workshopLevels)
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

        // Top-left: wave info + cash + overdrive status
        Column(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 80.dp)) {
            Text("Wave ${state.currentWave} · ${state.enemyCount} enemies", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(state.wavePhase.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("$${state.cash}", color = Color(0xFFD4A843), style = MaterialTheme.typography.titleSmall)
            state.activeOverdriveType?.let { type ->
                Text("⚡ ${type.name} ${state.overdriveTimeRemaining.toInt()}s", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelMedium)
            }
        }

        if (roundActive) {
            IconButton(onClick = { viewModel.quitRound() }, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quit round", tint = Color.White)
            }
        }

        // UW bar
        if (roundActive && state.uwSlots.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                UltimateWeaponBar(slots = state.uwSlots, onActivate = viewModel::activateUW)
            }
        }

        // Bottom controls
        if (roundActive) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(1f, 2f, 4f).forEach { speed ->
                    val desc = "Speed ${speed.toInt()}x"
                    if (state.speedMultiplier == speed) {
                        Button(onClick = {}, modifier = Modifier.semantics { contentDescription = desc }) { Text("${speed.toInt()}x") }
                    } else {
                        FilledTonalButton(onClick = { viewModel.setSpeed(speed) },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.semantics { contentDescription = desc },
                        ) { Text("${speed.toInt()}x", color = Color.White) }
                    }
                }
                FilledTonalButton(onClick = { viewModel.togglePause() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.isPaused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = if (state.isPaused) "Resume" else "Pause" },
                ) { Text(if (state.isPaused) "▶" else "⏸", color = Color.White) }

                FilledTonalButton(onClick = { viewModel.toggleUpgradeMenu() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.showUpgradeMenu) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = "Upgrades" },
                ) { Text("⬆", color = Color.White) }

                // Overdrive button
                FilledTonalButton(
                    onClick = { viewModel.toggleOverdriveMenu() },
                    enabled = !state.overdriveUsed,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.showOverdriveMenu) Color(0xFFFF9800) else Color.White.copy(alpha = 0.2f),
                        disabledContainerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.semantics { contentDescription = "Step Overdrive" },
                ) { Text("⚡", color = if (state.overdriveUsed) Color.Gray else Color.White) }
            }
        }

        // Upgrade menu
        if (state.showUpgradeMenu && roundActive) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                InRoundUpgradeMenu(cash = state.cash, inRoundLevels = state.inRoundLevels,
                    onPurchase = viewModel::purchaseInRoundUpgrade, onDismiss = viewModel::toggleUpgradeMenu)
            }
        }

        // Overdrive menu
        if (state.showOverdriveMenu && roundActive) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                OverdriveMenu(stepBalance = state.stepBalance, onSelect = viewModel::activateOverdrive, onDismiss = viewModel::toggleOverdriveMenu)
            }
        }

        if (state.isPaused && roundActive) {
            PauseOverlay(onResume = { viewModel.togglePause() }, onQuitRound = { viewModel.quitRound() })
        }

        state.roundEndState?.let { PostRoundOverlay(state = it, onPlayAgain = { viewModel.playAgain() }, onExitBattle = onExitBattle, onWatchGemAd = { viewModel.watchGemAd() }, onWatchPsAd = { viewModel.watchPsAd() }) }
        state.biomeTransition?.let { BiomeTransitionOverlay(info = it, onContinue = { viewModel.dismissBiomeTransition() }) }
    }
}
