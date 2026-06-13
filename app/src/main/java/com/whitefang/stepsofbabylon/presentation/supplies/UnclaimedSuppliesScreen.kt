package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.toDisplayName

@Composable
fun UnclaimedSuppliesScreen(
    viewModel: UnclaimedSuppliesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) { LoadingBox(); return }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Unclaimed Supplies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (state.drops.isNotEmpty()) {
                Button(onClick = viewModel::claimAll, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                    Text("Claim All")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.drops.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No supply drops yet — keep walking!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.drops, key = { it.id }) { drop ->
                    SupplyDropCard(drop = drop, onClaim = { viewModel.claimDrop(drop) })
                }
            }
        }
    }
}

@Composable
private fun SupplyDropCard(drop: SupplyDrop, onClaim: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(drop.trigger.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatSupplyReward(drop),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                )
                Text(
                    text = formatTimeAgo(drop.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClaim, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                Text("Claim")
            }
        }
    }
}

/**
 * Renders a supply drop's reward line. #20: for CARD_COPY, `rewardAmount` is NOT a quantity — it
 * is a card-TYPE index (0..8), and ClaimSupplyDrop always awards exactly ONE copy of
 * `CardType.entries[rewardAmount % size]`. Rendering it as "+N Card Copy" produced misleading
 * labels ("+0 Card Copy" reads as an empty reward). CARD_COPY now renders the resolved card name
 * + a fixed "x1"; the genuine-quantity rewards keep the "+N <label>" shape. Pure + top-level for
 * unit coverage (SupplyRewardFormatTest).
 */
internal fun formatSupplyReward(drop: SupplyDrop): String = when (drop.reward) {
    SupplyDropReward.STEPS -> "+${drop.rewardAmount} Steps"
    SupplyDropReward.GEMS -> "+${drop.rewardAmount} Gems"
    SupplyDropReward.POWER_STONES -> "+${drop.rewardAmount} Power Stones"
    SupplyDropReward.CARD_COPY -> {
        // Resolve the SAME card type ClaimSupplyDrop awards so the display matches the grant.
        val cardType = CardType.entries[drop.rewardAmount % CardType.entries.size]
        "${cardType.name.toDisplayName()} x1"
    }
}

private fun formatTimeAgo(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
