package com.whitefang.stepsofbabylon.presentation.store

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.whitefang.stepsofbabylon.domain.model.BillingProduct

@Composable
fun StoreScreen(viewModel: StoreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
    LazyColumn(
        Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Store", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("💎 ${state.gems} Gems", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2196F3))
            Spacer(Modifier.height(8.dp))
        }

        // Gem Packs
        item { Text("Gem Packs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        val gemPacks = listOf(BillingProduct.GEM_PACK_SMALL, BillingProduct.GEM_PACK_MEDIUM, BillingProduct.GEM_PACK_LARGE)
        items(gemPacks) { product ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${product.gemAmount} 💎 Gems", fontWeight = FontWeight.Bold)
                        // Live Play Billing price; fall back to the static constant if the
                        // ProductDetails query hasn't completed yet (or failed). Plan 31 PR B.
                        Text(state.priceDisplays[product] ?: product.priceDisplay, color = Color.Gray)
                    }
                    Button(onClick = { viewModel.purchaseGemPack(product) }) { Text("Buy") }
                }
            }
        }

        // Ad Removal
        item {
            Spacer(Modifier.height(8.dp))
            Text("Premium", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (state.adRemoved) Color(0xFF1B5E20) else CardDefaults.cardColors().containerColor)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Ad Removal", fontWeight = FontWeight.Bold)
                        Text(
                            if (state.adRemoved) "✅ Purchased"
                            else state.priceDisplays[BillingProduct.AD_REMOVAL] ?: BillingProduct.AD_REMOVAL.priceDisplay,
                            color = Color.Gray,
                        )
                    }
                    if (!state.adRemoved) {
                        Button(onClick = { viewModel.purchaseAdRemoval() }) { Text("Buy") }
                    }
                }
            }
        }

        // Season Pass
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (state.seasonPassActive) Color(0xFF1A237E) else CardDefaults.cardColors().containerColor)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("⭐ Season Pass", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    state.seasonPassActive -> "✅ Active — ${state.seasonPassDaysRemaining ?: 0} days remaining"
                                    else -> state.priceDisplays[BillingProduct.SEASON_PASS] ?: BillingProduct.SEASON_PASS.priceDisplay
                                },
                                color = Color.Gray,
                            )
                        }
                        if (!state.seasonPassActive) {
                            Button(onClick = { viewModel.purchaseSeasonPass() }) { Text("Subscribe") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Free vs Season Pass:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("• Daily Gems: 0 → 10/day", style = MaterialTheme.typography.bodySmall)
                    Text("• Lab Rush: 50–200 Gems → 1 free/day", style = MaterialTheme.typography.bodySmall)
                    Text("• Exclusive cosmetics unlocked", style = MaterialTheme.typography.bodySmall)
                    if (state.seasonPassActive) {
                        Spacer(Modifier.height(8.dp))
                        val context = LocalContext.current
                        OutlinedButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://play.google.com/store/account/subscriptions?package=com.whitefang.stepsofbabylon"))
                            context.startActivity(intent)
                        }) { Text("Manage subscription") }
                    }
                }
            }
        }

        // Cosmetics
        item {
            Spacer(Modifier.height(8.dp))
            Text("Cosmetics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Most cosmetic visuals are still being finalized. Jade Ziggurat is available now.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        items(state.cosmetics) { cosmetic ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(cosmetic.name, fontWeight = FontWeight.Bold)
                        Text(cosmetic.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(cosmetic.category.replace("_", " "), style = MaterialTheme.typography.labelSmall)
                    }
                    when {
                        cosmetic.isEquipped -> OutlinedButton(onClick = { viewModel.unequipCosmetic(cosmetic.cosmeticId) }) { Text("Unequip") }
                        cosmetic.isOwned -> Button(onClick = { viewModel.equipCosmetic(cosmetic.cosmeticId) }) { Text("Equip") }
                        // C.2 PR 2: only zig_jade has a shipped renderer palette. Remaining
                        // cosmetics stay behind the R2-11 "Coming Soon" guard until their
                        // palette ships in C.2 PR 3+.
                        cosmetic.cosmeticId == ENABLED_COSMETIC_ID -> Button(
                            onClick = { viewModel.purchaseCosmetic(cosmetic.cosmeticId) },
                            enabled = !state.isPurchasing,
                        ) { Text("💎 ${cosmetic.priceGems}") }
                        else -> Button(
                            onClick = { },
                            enabled = false,
                        ) { Text("Coming Soon") }
                    }
                }
            }
        }
    }
    }
}

/**
 * Allow-list of cosmetic IDs whose renderer palette has shipped and whose purchase button is
 * enabled in the store. Each entry here must have both a [data/repository/CosmeticRepositoryImpl]
 * SEED_COSMETICS row and a ZIGGURAT_COLOR_LOOKUP / category-appropriate palette entry.
 * See `docs/evolution/implementation_roadmap.md` §C.2 PR 2+.
 */
private const val ENABLED_COSMETIC_ID = "zig_jade"
