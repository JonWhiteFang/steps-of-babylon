package com.whitefang.stepsofbabylon.presentation.ui

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.usecase.PackTier

/**
 * #260: single source of truth for localized enum display names that previously surfaced raw
 * CONSTANT_CASE. Each returns a @StringRes resolved via stringResource at the Compose call site,
 * keeping the domain enums Android-free. (toDisplayName() stays for already-title-cased enums —
 * out of scope per the spec.)
 */
@StringRes fun UpgradeCategory.labelRes(): Int =
    when (this) {
        UpgradeCategory.ATTACK -> R.string.upgrade_cat_attack
        UpgradeCategory.DEFENSE -> R.string.upgrade_cat_defense
        UpgradeCategory.UTILITY -> R.string.upgrade_cat_utility
    }

@StringRes fun PackTier.labelRes(): Int =
    when (this) {
        PackTier.COMMON -> R.string.pack_tier_common
        PackTier.RARE -> R.string.pack_tier_rare
        PackTier.EPIC -> R.string.pack_tier_epic
    }

@StringRes fun CardRarity.labelRes(): Int =
    when (this) {
        CardRarity.COMMON -> R.string.rarity_common
        CardRarity.RARE -> R.string.rarity_rare
        CardRarity.EPIC -> R.string.rarity_epic
    }

@StringRes fun CosmeticCategory.labelRes(): Int =
    when (this) {
        CosmeticCategory.ZIGGURAT_SKIN -> R.string.cosmetic_cat_ziggurat_skin
        CosmeticCategory.PROJECTILE_EFFECT -> R.string.cosmetic_cat_projectile_effect
        CosmeticCategory.ENEMY_SKIN -> R.string.cosmetic_cat_enemy_skin
    }

/**
 * #260: BattleUiState.wavePhase is a String ("SPAWNING"/"COOLDOWN"/""), not the WavePhase enum,
 * so this resolves from the raw string. Returns null for the blank/unknown default (render nothing).
 */
@StringRes fun wavePhaseLabelRes(rawPhase: String): Int? =
    when (rawPhase) {
        "SPAWNING" -> R.string.wave_phase_spawning
        "COOLDOWN" -> R.string.wave_phase_cooldown
        else -> null
    }
