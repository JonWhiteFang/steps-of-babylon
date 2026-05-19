package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeEffectReadout
import kotlin.math.ceil
import kotlin.math.pow

@Composable
fun InRoundUpgradeMenu(
    cash: Long,
    inRoundLevels: Map<UpgradeType, Int>,
    onPurchase: (UpgradeType) -> Unit,
    onDismiss: () -> Unit,
    /**
     * RO-11 #C / RO-10: per-row "Now → Next" readout. Default is a no-readout fallback
     * (current = empty, next = null) so the existing `BattleScreen` invocation that hasn't
     * yet wired the BVM accessor still compiles — the UI suppresses the readout line
     * entirely when the lambda returns an empty current. Production callers pass
     * `viewModel::describeEffect` and the readout renders below the description text.
     */
    describeEffect: (UpgradeType) -> UpgradeEffectReadout = { UpgradeEffectReadout("", null) },
) {
    val tabs = UpgradeCategory.entries
    var selectedTab by remember { mutableIntStateOf(0) }
    val category = tabs[selectedTab]
    // Mirror the WorkshopViewModel hidden-set: STEP_MULTIPLIER affects walking outside
    // battles and RECOVERY_PACKAGES is a passive periodic-heal effect — neither makes
    // sense as a cash-fed in-round purchase. Hiding them here closes the dead-cash gap
    // identified in RO-08.
    val hiddenInRound = setOf(UpgradeType.STEP_MULTIPLIER, UpgradeType.RECOVERY_PACKAGES)
    val upgrades = UpgradeType.entries.filter { it.category == category && it !in hiddenInRound }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(8.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$${cash}", color = Color(0xFFD4A843), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDismiss) { Text("✕", color = Color.White, fontSize = 18.sp) }
        }

        // Tabs
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { i, cat ->
                Tab(selected = i == selectedTab, onClick = { selectedTab = i },
                    text = { Text(cat.name, fontSize = 12.sp) })
            }
        }

        // Upgrade list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(upgrades) { type ->
                val level = inRoundLevels[type] ?: 0
                val maxed = type.config.maxLevel != null && level >= type.config.maxLevel!!
                val cost = if (maxed) 0L else ceil(type.config.baseCost * type.config.scaling.pow(level)).toLong()
                val affordable = cash >= cost && !maxed

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(type.name.replace("_", " "), color = Color.White, fontSize = 13.sp)
                        Text("Lv $level · ${type.config.description}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        // RO-11 #C / RO-10: live "Now → Next" readout.
                        // Skip when describeEffect returns an empty current string — keeps
                        // unit-test / preview call sites (which pass the default no-op
                        // lambda) visually identical to the pre-RO-11 layout.
                        val readout = describeEffect(type)
                        if (readout.current.isNotEmpty()) {
                            val line = if (readout.next != null) {
                                "Now: ${readout.current} → ${readout.next}"
                            } else {
                                "Now: ${readout.current} (MAX)"
                            }
                            Text(
                                line,
                                color = Color(0xFFD4A843),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (maxed) {
                        Text("MAX", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Button(
                            onClick = { onPurchase(type) },
                            enabled = affordable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (affordable) Color(0xFFD4A843) else Color.DarkGray,
                            ),
                        ) { Text("$${cost}", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}
