package com.whitefang.stepsofbabylon.presentation.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyCost
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
import com.whitefang.stepsofbabylon.presentation.ui.EmptyState
import com.whitefang.stepsofbabylon.presentation.ui.EquippedChip
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.presentation.ui.RarityBadge
import com.whitefang.stepsofbabylon.presentation.ui.cardRarityLabelRes
import com.whitefang.stepsofbabylon.presentation.ui.cardRarityTier
import com.whitefang.stepsofbabylon.presentation.ui.color
import com.whitefang.stepsofbabylon.presentation.ui.effectDescription
import com.whitefang.stepsofbabylon.presentation.ui.labelRes
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rarityBorder
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.resolve
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusWarning
import com.whitefang.stepsofbabylon.presentation.ui.toDisplayName

@Composable
fun CardsScreen(viewModel: CardsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.error != null) {
        ErrorState(stringResource(state.error!!), onRetry = viewModel::retry)
        return
    }
    if (state.isLoading) {
        LoadingBox()
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Left: how many cards the player owns. Right: the gem balance they spend on packs.
                // (Previously both slots printed the gem balance — "💎 1 / 💎 1 Gems" — a copy bug.)
                Text(
                    stringResource(R.string.cards_owned_count, state.ownedCards.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                CurrencyValue(CurrencyType.GEMS, state.gems)
            }
            Spacer(Modifier.height(8.dp))
            if (state.equippedCount >= 3) {
                Text(
                    stringResource(R.string.cards_equipped_full),
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusWarning,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    stringResource(R.string.cards_equipped_count, state.equippedCount),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))

            // Free pack ad button
            if (state.freePackAvailable) {
                OutlinedButton(onClick = { viewModel.watchFreePackAd() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cards_free_pack_ad))
                }
                Spacer(Modifier.height(4.dp))
            } else if (!state.adRemoved) {
                Text(
                    stringResource(R.string.cards_free_pack_used),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Pack buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.packOptions.forEach { pack ->
                    key(pack.tier) {
                        val packPulse = rememberPulse()
                        val packHaptics = rememberHaptics()
                        Button(
                            onClick = {
                                packPulse.trigger()
                                packHaptics.tap()
                                viewModel.openPack(pack.tier)
                            },
                            enabled = pack.canAfford,
                            modifier = Modifier.weight(1f).pulseScale(packPulse),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(pack.tier.labelRes()))
                                CurrencyCost(CurrencyType.GEMS, pack.tier.gemCost)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Card collection
            if (state.ownedCards.isEmpty() && !state.isLoading) {
                EmptyState(
                    title = stringResource(R.string.cards_empty_title),
                    message = stringResource(R.string.cards_empty_message),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.ownedCards) { card ->
                        CardItem(
                            card,
                            state.equippedCount,
                            onEquip = { viewModel.equipCard(card.id) },
                            onUnequip = { viewModel.unequipCard(card.id) },
                            onUpgrade = { viewModel.upgradeCard(card.id) },
                        )
                    }
                }
            }
        }

        // Pack result dialog
        state.lastPackResult?.let { results ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPackResult() },
                title = { Text(stringResource(R.string.cards_pack_opened_title)) },
                text = {
                    Column {
                        results.forEach { r ->
                            val rowColor = cardRarityTier(r.type.rarity).color()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (r.isNew) {
                                    Icon(
                                        Icons.Filled.FiberNew,
                                        contentDescription = stringResource(R.string.cards_pack_new),
                                        tint = rowColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Autorenew,
                                        contentDescription = stringResource(R.string.cards_pack_duplicate),
                                        tint = rowColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (r.isNew) {
                                        formatName(r.type.name)
                                    } else {
                                        stringResource(
                                            R.string.card_pull_result,
                                            formatName(r.type.name),
                                            pluralStringResource(
                                                R.plurals.card_copies,
                                                r.copiesAwarded,
                                                r.copiesAwarded,
                                            ),
                                        )
                                    },
                                    color = rowColor,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissPackResult() }) { Text(stringResource(R.string.action_ok)) }
                },
            )
        }
    } // Scaffold
}

@Composable
private fun CardItem(
    card: CardDisplayInfo,
    equippedCount: Int,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val haptics = rememberHaptics()
    val upgradePulse = rememberPulse()
    val tier = cardRarityTier(card.type.rarity)
    Card(
        Modifier.fillMaxWidth().rarityBorder(tier),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RarityBadge(tier, stringResource(cardRarityLabelRes(card.type.rarity)))
                    Text(formatName(card.type.name), style = MaterialTheme.typography.titleSmall)
                }
                if (card.isEquipped) {
                    EquippedChip()
                } else if (card.isMaxLevel) {
                    Text(
                        stringResource(R.string.upgrade_max),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.cards_level_progress, card.level, card.type.maxLevel),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                card.type.effectDescription(card.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.isEquipped) {
                    OutlinedButton(onClick = {
                        haptics.tap()
                        onUnequip()
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cards_unequip)) }
                } else {
                    Button(
                        onClick = {
                            haptics.tap()
                            onEquip()
                        },
                        enabled = equippedCount < 3,
                        modifier =
                            Modifier.weight(
                                1f,
                            ),
                    ) { Text(stringResource(R.string.cards_equip)) }
                }
                if (!card.isMaxLevel) {
                    Button(
                        onClick = {
                            upgradePulse.trigger()
                            haptics.tap()
                            onUpgrade()
                        },
                        enabled = card.canAffordUpgrade,
                        modifier = Modifier.weight(1f).pulseScale(upgradePulse),
                    ) {
                        Text(stringResource(R.string.cards_upgrade_progress, card.copyCount, card.copiesNeeded))
                    }
                }
            }
        }
    }
}

private fun formatName(name: String): String = name.toDisplayName()
