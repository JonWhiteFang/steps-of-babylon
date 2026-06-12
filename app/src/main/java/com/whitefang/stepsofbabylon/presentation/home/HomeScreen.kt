package com.whitefang.stepsofbabylon.presentation.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.presentation.battle.biome.BiomeTheme
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.LapisLazuli
import com.whitefang.stepsofbabylon.presentation.ui.theme.LapisLight
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    onBattleClick: () -> Unit = {},
    onSuppliesClick: () -> Unit = {},
    onEconomyClick: () -> Unit = {},
    onMissionsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onStoreClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme = BiomeTheme.forBiome(state.currentBiome)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier.fillMaxSize().background(
            // Raised alpha (was 0.30/0.15) so Home actually wears its biome colours instead of
            // looking flat-brown. Still translucent over the bronze window background.
            Brush.verticalGradient(listOf(Color(theme.skyColorTop).copy(alpha = 0.55f), Color(theme.skyColorBottom).copy(alpha = 0.30f)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TierSelector(
                currentTier = state.currentTier,
                highestUnlockedTier = state.highestUnlockedTier,
                bestWavePerTier = state.bestWavePerTier,
                onSelectTier = viewModel::selectTier,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LapisLazuli.copy(alpha = 0.18f)),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // LapisLight (not deep LapisLazuli) for legible text on the dark card —
                    // deep lapis was ~1.45:1 on this background (WCAG fail).
                    Text("Today", style = MaterialTheme.typography.labelLarge, color = LapisLight)
                    Text(
                        "${formatCount(state.todaySteps)} steps",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                        modifier = Modifier.animateContentSize(),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().clickable { onEconomyClick() }, horizontalArrangement = Arrangement.SpaceEvenly) {
                CurrencyItem("Steps", state.stepBalance)
                CurrencyItem("Gems", state.gems)
                CurrencyItem("Power Stones", state.powerStones)
            }

            Text("Best Wave: ${state.bestWave}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)

            if (state.unclaimedDropCount > 0) {
                OutlinedButton(onClick = onSuppliesClick, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Unclaimed supplies, ${state.unclaimedDropCount} available" }) {
                    BadgedBox(badge = { Badge { Text("${state.unclaimedDropCount}") } }) {
                        Icon(Icons.Default.Inbox, contentDescription = null)
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("Unclaimed Supplies", fontWeight = FontWeight.Bold)
                }
            }

            MenuButton(
                icon = Icons.Default.Flag,
                label = "Missions",
                badgeCount = state.claimableMissionCount,
                onClick = onMissionsClick,
            )
            MenuButton(icon = Icons.Default.Settings, label = "Settings", onClick = onSettingsClick)
            MenuButton(icon = Icons.AutoMirrored.Filled.HelpOutline, label = "Help", onClick = onHelpClick)
            MenuButton(icon = Icons.Default.ShoppingCart, label = "Store", onClick = onStoreClick)

            if (state.seasonPassActive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                    Text("Season Pass Active", style = MaterialTheme.typography.labelMedium, color = StatusWarning)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(onClick = onBattleClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                Text("BATTLE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A full-width Home menu row: a themed Material icon + label, with an optional claimable-count
 * badge. Replaced the previous emoji-prefixed text buttons (📋/⚙️/❓/🏪), which rendered as
 * inconsistent multicolour glyphs against the rest of the monochrome icon set and read as
 * placeholder UI. The label carries the meaning for TalkBack; the icon is decorative.
 */
@Composable
private fun MenuButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                Icon(icon, contentDescription = null)
            }
        } else {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.size(12.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CurrencyItem(label: String, amount: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = formatCount(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Group digits with thousands separators ("12,345") for legibility on the larger currency values. */
private fun formatCount(value: Long): String = "%,d".format(value)
