package com.whitefang.stepsofbabylon.presentation.battle.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.presentation.battle.RoundEndState

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
                Text("Round Over", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFD4A843), fontWeight = FontWeight.Bold)

                if (state.isNewBestWave) {
                    Spacer(Modifier.height(8.dp))
                    Text("\uD83C\uDFC6 New Record!", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFD700))
                    if (state.previousBest > 0) {
                        Text("Previous best: Wave ${state.previousBest}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.tierUnlocked?.let { tier ->
                    Spacer(Modifier.height(8.dp))
                    Text("\uD83D\uDD13 Tier $tier Unlocked!", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
                    Text("${TierConfig.forTier(tier).cashMultiplier}x cash multiplier", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }

                if (state.powerStonesAwarded > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("💎 +${state.powerStonesAwarded} Power Stones", style = MaterialTheme.typography.titleMedium, color = Color(0xFF9C27B0))
                }

                if (state.stepsEarned > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "👟 +${state.stepsEarned} Steps",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50),
                    )
                }

                Spacer(Modifier.height(16.dp))

                StatRow("Wave Reached", "${state.waveReached}")
                StatRow("Enemies Killed", "${state.enemiesKilled}")
                StatRow("Cash Earned", "$${state.totalCashEarned}")
                if (state.stepsEarned > 0) {
                    StatRow("Steps Earned", "+${state.stepsEarned}")
                }
                StatRow("Time Survived", "%d:%02d".format(minutes, seconds))

                Spacer(Modifier.height(24.dp))

                // Reward ad buttons (hidden if ad removal purchased)
                if (!state.adRemoved) {
                    if (state.gemAdWatched) {
                        Text("✅ +1 Gem Earned!", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = onWatchGemAd, modifier = Modifier.fillMaxWidth()) {
                            Text("🎬 Watch Ad for +1 Gem")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (state.powerStonesAwarded > 0) {
                        if (state.psAdWatched) {
                            Text("✅ Power Stones Doubled!", color = Color(0xFF9C27B0), style = MaterialTheme.typography.bodySmall)
                        } else {
                            OutlinedButton(onClick = onWatchPsAd, modifier = Modifier.fillMaxWidth()) {
                                Text("🎬 Watch Ad to Double PS")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                    Text("Play Again")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onExitBattle, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave Battle")
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
