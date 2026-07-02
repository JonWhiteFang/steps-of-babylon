package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeEffectReadout
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeValue
import com.whitefang.stepsofbabylon.presentation.ui.UiMessage

data class UpgradeDisplayInfo(
    val type: UpgradeType,
    val level: Int,
    val cost: Long,
    val isMaxed: Boolean,
    val canAfford: Boolean,
    // i18n #34 phase 3 (D-workshop): carry the pure-domain ResolvedStats so the per-type stat-value
    // unit label is resolved via @StringRes at the UpgradeCard render boundary (statValueLabel),
    // mirroring the UW pathValueAtNext extraction. Was `statValue: String` (hardcoded English units).
    val stats: ResolvedStats,
    // #29 decision support. `nowNext` = workshop-dimension Now→Next preview (null only if it could not
    // be computed). `value` = combat-power value/Best-Buy data; null for non-combat upgrades (Δpower ≤ 0)
    // → the card renders no bar/badge for them.
    val nowNext: UpgradeEffectReadout? = null,
    val value: UpgradeValue? = null,
)

data class WorkshopUiState(
    val upgrades: List<UpgradeDisplayInfo> = emptyList(),
    val stepBalance: Long = 0,
    val selectedCategory: UpgradeCategory = UpgradeCategory.ATTACK,
    val isLoading: Boolean = true,
    @StringRes val error: Int? = null,
    val isProcessing: Boolean = false,
    val userMessage: UiMessage? = null,
)
