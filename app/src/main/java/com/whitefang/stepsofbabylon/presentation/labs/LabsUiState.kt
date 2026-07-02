package com.whitefang.stepsofbabylon.presentation.labs

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.presentation.ui.UiMessage

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
    @StringRes val error: Int? = null,
    val isProcessing: Boolean = false,
    val userMessage: UiMessage? = null,
)
