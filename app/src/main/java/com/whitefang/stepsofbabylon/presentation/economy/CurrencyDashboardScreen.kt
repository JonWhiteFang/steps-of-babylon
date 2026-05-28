package com.whitefang.stepsofbabylon.presentation.economy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold

@Composable
fun CurrencyDashboardScreen(
    viewModel: CurrencyDashboardViewModel = hiltViewModel(),
    onStoreClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Premium Currencies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            androidx.compose.material3.TextButton(onClick = onStoreClick) { Text("🏪 Store") }
        }

        // Balances
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BalanceCard("Gems", state.gems, Color(0xFF4CAF50))
            BalanceCard("Power Stones", state.powerStones, Color(0xFF9C27B0))
        }

        // Weekly Challenge
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Weekly Step Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.weeklyTimeRemaining.isNotBlank()) {
                        Text("⏱ ${state.weeklyTimeRemaining}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${state.weeklySteps} / 100,000 steps", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (state.weeklySteps / 100_000f).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Gold,
                )
                Spacer(Modifier.height(8.dp))
                ThresholdRow("50,000", 10, state.weeklyClaimedTier >= 1, state.weeklySteps >= 50_000)
                ThresholdRow("75,000", 20, state.weeklyClaimedTier >= 2, state.weeklySteps >= 75_000)
                ThresholdRow("100,000", 35, state.weeklyClaimedTier >= 3, state.weeklySteps >= 100_000)
                if (state.weeklyHistory.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Past Weeks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    state.weeklyHistory.forEach { week ->
                        HistoryRow(week)
                    }
                }
            }
        }

        // Daily Login Streak
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Login Streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (day in 1..7) {
                        val filled = day <= state.currentStreak
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = if (filled) Gold else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$day", style = MaterialTheme.typography.labelSmall, color = if (filled) Color.Black else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.todayGemsClaimed) "✓ Today's Gems claimed" else "Open the app daily for Gems!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Daily PS
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Daily Power Stone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.todayPsClaimed) "✓ Earned today (walked 1,000+ steps)"
                    else "Walk 1,000 steps today for 1 Power Stone",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(label: String, amount: Long, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$amount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ThresholdRow(steps: String, ps: Int, claimed: Boolean, reached: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$steps steps → $ps PS", style = MaterialTheme.typography.bodySmall)
        Text(
            when {
                claimed -> "✓ Claimed"
                reached -> "Ready!"
                else -> "—"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (reached && !claimed) FontWeight.Bold else FontWeight.Normal,
            color = if (claimed) Color(0xFF4CAF50) else if (reached) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(week: WeeklyResult) {
    val met = week.claimedTier > 0
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(week.weekStartDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(
                if (met) "✓" else "✗",
                style = MaterialTheme.typography.bodySmall,
                color = if (met) Color(0xFF4CAF50) else Color(0xFFE53935),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (met) "${week.powerStonesEarned} PS" else "—",
                style = MaterialTheme.typography.bodySmall,
                color = if (met) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
