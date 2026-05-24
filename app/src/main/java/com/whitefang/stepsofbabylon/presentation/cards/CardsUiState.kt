package com.whitefang.stepsofbabylon.presentation.cards

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import com.whitefang.stepsofbabylon.domain.usecase.PackTier

data class CardDisplayInfo(
    val id: Int,
    val type: CardType,
    val level: Int,
    val isEquipped: Boolean,
    val isMaxLevel: Boolean,
    val copyCount: Int,
    val copiesNeeded: Int,
    val canAffordUpgrade: Boolean,
    val effectDescription: String,
)

data class CardsUiState(
    val ownedCards: List<CardDisplayInfo> = emptyList(),
    val equippedCount: Int = 0,
    val gems: Long = 0,
    val packOptions: List<PackOption> = PackTier.entries.map { PackOption(it, false) },
    val lastPackResult: List<CardResult>? = null,
    val freePackAvailable: Boolean = false,
    val adRemoved: Boolean = false,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
)

data class PackOption(val tier: PackTier, val canAfford: Boolean)
