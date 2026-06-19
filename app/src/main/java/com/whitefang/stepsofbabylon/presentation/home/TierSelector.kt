package com.whitefang.stepsofbabylon.presentation.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.presentation.ui.toDisplayName

@Composable
fun TierSelector(
    currentTier: Int,
    highestUnlockedTier: Int,
    bestWavePerTier: Map<Int, Int>,
    onSelectTier: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Tier $currentTier — ${Biome.forTier(currentTier).name.toDisplayName()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4A843),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (t in 1..10) {
                val unlocked = t <= highestUnlockedTier
                val selected = t == currentTier
                val tier = TierConfig.forTier(t)
                FilterChip(
                    selected = selected,
                    onClick = { if (unlocked) onSelectTier(t) },
                    enabled = unlocked,
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("$t", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${tier.cashMultiplier}x", fontSize = 9.sp)
                        }
                    },
                    modifier = Modifier.size(width = 48.dp, height = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD4A843),
                        selectedLabelColor = Color.Black,
                    ),
                )
            }
        }
        // Condition summary
        val conditions = TierConfig.forTier(currentTier).battleConditions
        if (conditions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = conditions.entries.joinToString(" · ") { "${it.key.name.toDisplayName()} ${it.value}" },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF8A65),
                textAlign = TextAlign.Center,
            )
        }
    }
}
