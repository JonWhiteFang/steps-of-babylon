package com.whitefang.stepsofbabylon.presentation.labs

import com.whitefang.stepsofbabylon.domain.model.ResearchType

data class ResearchDisplayInfo(
    val type: ResearchType,
    val level: Int,
    val isMaxed: Boolean,
    val costToStart: Long,
    val canAffordStart: Boolean,
    val timeToCompleteHours: Double,
    val isActive: Boolean,
    val remainingMs: Long = 0,
    val rushCostGems: Long = 0,
    val canAffordRush: Boolean = false,
)

data class LabsUiState(
    val researchList: List<ResearchDisplayInfo> = emptyList(),
    val activeSlots: Int = 0,
    val totalSlots: Int = 1,
    val stepBalance: Long = 0,
    val gems: Long = 0,
    val slotUnlockCostGems: Long = 200,
    val canAffordSlotUnlock: Boolean = false,
    val seasonPassFreeRushAvailable: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
)
