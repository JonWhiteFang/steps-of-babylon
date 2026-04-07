package com.whitefang.stepsofbabylon.presentation.workshop

import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType

data class UpgradeDisplayInfo(
    val type: UpgradeType,
    val level: Int,
    val cost: Long,
    val isMaxed: Boolean,
    val canAfford: Boolean,
    val description: String,
    val statValue: String = "",
)

data class WorkshopUiState(
    val upgrades: List<UpgradeDisplayInfo> = emptyList(),
    val stepBalance: Long = 0,
    val selectedCategory: UpgradeCategory = UpgradeCategory.ATTACK,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
)
