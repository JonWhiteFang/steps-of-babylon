package com.whitefang.stepsofbabylon.presentation.battle.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.presentation.battle.RoundEndState
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import kotlinx.coroutines.delay

@Composable
fun PostRoundOverlay(
    state: RoundEndState,
    onPlayAgain: () -> Unit,
    onExitBattle: () -> Unit,
    onWatchGemAd: () -> Unit = {},
    onWatchPsAd: () -> Unit = {},
) {
    val minutes = (state.timeSurvivedSeconds / 60).toInt()
    val seconds = (state.timeSurvivedSeconds % 60).toInt()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }

    // Build the ordered list of present highlight lines (record, tier, power-stones, steps).
    val highlights: List<@Composable () -> Unit> = buildList {
        if (state.isNewBestWave) add {
            Text(stringResource(R.string.postround_new_record), style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFD700))
            if (state.previousBest > 0) {
                Text(stringResource(R.string.postround_previous_best, state.previousBest), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.tierUnlocked?.let { tier ->
            add {
                Text(stringResource(R.string.postround_tier_unlocked, tier), style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
                Text(stringResource(R.string.postround_cash_multiplier, TierConfig.forTier(tier).cashMultiplier.toString()), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.powerStonesAwarded > 0) add {
            Text(stringResource(R.string.postround_power_stones, state.powerStonesAwarded), style = MaterialTheme.typography.titleMedium, color = Color(0xFF9C27B0))
        }
        if (state.stepsEarned > 0) add {
            Text(
                stringResource(R.string.steps_earned_banner, state.stepsEarned),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4CAF50),
            )
        }
    }

    var visibleCount by remember { mutableIntStateOf(if (reducedMotion) highlights.size else 0) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            for (i in highlights.indices) { delay(180); visibleCount = i + 1; haptics.success() }
        } else {
            haptics.success() // one confirm, no stagger
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F14)),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.postround_title), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFD4A843), fontWeight = FontWeight.Bold)

                // Staggered highlight sting — reveals the present subset one-by-one.
                highlights.take(visibleCount).forEach { highlight ->
                    Spacer(Modifier.height(8.dp))
                    highlight()
                }

                Spacer(Modifier.height(16.dp))

                StatRow(stringResource(R.string.postround_stat_wave), "${state.waveReached}")
                StatRow(stringResource(R.string.postround_stat_enemies), "${state.enemiesKilled}")
                StatRow(stringResource(R.string.postround_stat_cash), stringResource(R.string.cash_amount, state.totalCashEarned))
                if (state.stepsEarned > 0) {
                    StatRow(stringResource(R.string.postround_stat_steps), stringResource(R.string.postround_stat_steps_value, state.stepsEarned))
                }
                StatRow(stringResource(R.string.postround_stat_time), stringResource(R.string.postround_time_value, minutes, seconds))

                Spacer(Modifier.height(24.dp))

                // Reward ad buttons (hidden if ad removal purchased)
                if (!state.adRemoved) {
                    if (state.gemAdWatched) {
                        Text(stringResource(R.string.postround_gem_earned), color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = onWatchGemAd, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.postround_watch_gem_ad))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (state.powerStonesAwarded > 0) {
                        if (state.psAdWatched) {
                            Text(stringResource(R.string.postround_ps_doubled), color = Color(0xFF9C27B0), style = MaterialTheme.typography.bodySmall)
                        } else {
                            OutlinedButton(onClick = onWatchPsAd, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.postround_watch_ps_ad))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(onClick = { haptics.tap(); onPlayAgain() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.postround_play_again))
                }
                Spacer(Modifier.height(8.dp))
                if (state.isNewBestWave) {
                    val shareText = stringResource(R.string.postround_share_text, state.waveReached, BuildConfig.PLAY_STORE_URL)
                    val shareChooserTitle = stringResource(R.string.postround_share_chooser)
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.postround_share_record))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = onExitBattle, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.postround_leave_battle))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.8f))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}
