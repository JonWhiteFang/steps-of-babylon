package com.whitefang.stepsofbabylon.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.BattleCondition
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
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

/**
 * #34 phase 3 (G): domain-model description resolvers. The four enums carry hardcoded English
 * `description` strings (kept on the enum so `domain/` stays Android-free); these map each entry to
 * a @StringRes so the render sites resolve via stringResource. Exhaustive `when` (no `else`) so the
 * compiler flags any new enum member that forgets a description string.
 */
@StringRes
fun UpgradeType.descriptionRes(): Int =
    when (this) {
        UpgradeType.DAMAGE -> R.string.upgrade_desc_damage
        UpgradeType.ATTACK_SPEED -> R.string.upgrade_desc_attack_speed
        UpgradeType.CRITICAL_CHANCE -> R.string.upgrade_desc_critical_chance
        UpgradeType.CRITICAL_FACTOR -> R.string.upgrade_desc_critical_factor
        UpgradeType.RANGE -> R.string.upgrade_desc_range
        UpgradeType.MULTISHOT -> R.string.upgrade_desc_multishot
        UpgradeType.BOUNCE_SHOT -> R.string.upgrade_desc_bounce_shot
        UpgradeType.DAMAGE_PER_METER -> R.string.upgrade_desc_damage_per_meter
        UpgradeType.RAPID_FIRE -> R.string.upgrade_desc_rapid_fire
        UpgradeType.HEALTH -> R.string.upgrade_desc_health
        UpgradeType.HEALTH_REGEN -> R.string.upgrade_desc_health_regen
        UpgradeType.DEFENSE_PERCENT -> R.string.upgrade_desc_defense_percent
        UpgradeType.DEFENSE_ABSOLUTE -> R.string.upgrade_desc_defense_absolute
        UpgradeType.KNOCKBACK -> R.string.upgrade_desc_knockback
        UpgradeType.THORN_DAMAGE -> R.string.upgrade_desc_thorn_damage
        UpgradeType.ORBS -> R.string.upgrade_desc_orbs
        UpgradeType.LIFESTEAL -> R.string.upgrade_desc_lifesteal
        UpgradeType.DEATH_DEFY -> R.string.upgrade_desc_death_defy
        UpgradeType.CASH_BONUS -> R.string.upgrade_desc_cash_bonus
        UpgradeType.CASH_PER_WAVE -> R.string.upgrade_desc_cash_per_wave
        UpgradeType.INTEREST -> R.string.upgrade_desc_interest
        UpgradeType.FREE_UPGRADES -> R.string.upgrade_desc_free_upgrades
        UpgradeType.RECOVERY_PACKAGES -> R.string.upgrade_desc_recovery_packages
        UpgradeType.STEP_MULTIPLIER -> R.string.upgrade_desc_step_multiplier
    }

@StringRes
fun ResearchType.descriptionRes(): Int =
    when (this) {
        ResearchType.DAMAGE_RESEARCH -> R.string.research_desc_damage_research
        ResearchType.HEALTH_RESEARCH -> R.string.research_desc_health_research
        ResearchType.CASH_RESEARCH -> R.string.research_desc_cash_research
        ResearchType.STEP_EFFICIENCY -> R.string.research_desc_step_efficiency
        ResearchType.WAVE_SKIP -> R.string.research_desc_wave_skip
        ResearchType.AUTO_UPGRADE_AI -> R.string.research_desc_auto_upgrade_ai
        ResearchType.UW_COOLDOWN -> R.string.research_desc_uw_cooldown
        ResearchType.CRITICAL_RESEARCH -> R.string.research_desc_critical_research
        ResearchType.REGEN_RESEARCH -> R.string.research_desc_regen_research
        ResearchType.ENEMY_INTEL -> R.string.research_desc_enemy_intel
        ResearchType.MULTISHOT_RESEARCH -> R.string.research_desc_multishot_research
        ResearchType.BOUNCE_RESEARCH -> R.string.research_desc_bounce_research
    }

@StringRes
fun DailyMissionType.descriptionRes(): Int =
    when (this) {
        DailyMissionType.WALK_5000 -> R.string.mission_desc_walk_5000
        DailyMissionType.WALK_12000 -> R.string.mission_desc_walk_12000
        DailyMissionType.REACH_WAVE_30 -> R.string.mission_desc_reach_wave_30
        DailyMissionType.KILL_500_ENEMIES -> R.string.mission_desc_kill_500_enemies
        DailyMissionType.SPEND_5000_WORKSHOP -> R.string.mission_desc_spend_5000_workshop
        DailyMissionType.COMPLETE_RESEARCH -> R.string.mission_desc_complete_research
    }

@StringRes
fun UltimateWeaponType.descriptionRes(): Int =
    when (this) {
        UltimateWeaponType.DEATH_WAVE -> R.string.uw_desc_death_wave
        UltimateWeaponType.CHAIN_LIGHTNING -> R.string.uw_desc_chain_lightning
        UltimateWeaponType.BLACK_HOLE -> R.string.uw_desc_black_hole
        UltimateWeaponType.CHRONO_FIELD -> R.string.uw_desc_chrono_field
        UltimateWeaponType.POISON_SWAMP -> R.string.uw_desc_poison_swamp
        UltimateWeaponType.GOLDEN_ZIGGURAT -> R.string.uw_desc_golden_ziggurat
    }

/**
 * #34 phase 3 (G): Milestone display-name resolver. `Milestone.displayName` stays on the enum
 * (keeps `domain/` Android-free); this maps each entry to a @StringRes for the render site.
 * Exhaustive `when` (no `else`) so a new milestone that forgets its name string fails to compile.
 */
@StringRes
fun Milestone.displayNameRes(): Int =
    when (this) {
        Milestone.FIRST_STEPS -> R.string.milestone_name_first_steps
        Milestone.MORNING_JOGGER -> R.string.milestone_name_morning_jogger
        Milestone.TRAIL_BLAZER -> R.string.milestone_name_trail_blazer
        Milestone.MARATHON_WALKER -> R.string.milestone_name_marathon_walker
        Milestone.IRON_SOLES -> R.string.milestone_name_iron_soles
        Milestone.GLOBE_TROTTER -> R.string.milestone_name_globe_trotter
    }

/**
 * #34 phase 3 (G): domain-model display-name resolvers. These replace the `.toDisplayName()`
 * title-cased CONSTANT_CASE render at each UI site, so a non-English locale can localize the enum
 * names. Values are BYTE-IDENTICAL to the old `String.toDisplayName()` output — including the
 * pre-existing quirks `UW_COOLDOWN` → "U W Cooldown" and `UNDERWORLD_OF_KUR` → "Underworld Of Kur"
 * (this is extraction, not a copy fix). Exhaustive `when` (no `else`) so a new enum member that
 * forgets its name string fails to compile.
 */
@StringRes
fun UpgradeType.nameRes(): Int =
    when (this) {
        UpgradeType.DAMAGE -> R.string.upgrade_name_damage
        UpgradeType.ATTACK_SPEED -> R.string.upgrade_name_attack_speed
        UpgradeType.CRITICAL_CHANCE -> R.string.upgrade_name_critical_chance
        UpgradeType.CRITICAL_FACTOR -> R.string.upgrade_name_critical_factor
        UpgradeType.RANGE -> R.string.upgrade_name_range
        UpgradeType.MULTISHOT -> R.string.upgrade_name_multishot
        UpgradeType.BOUNCE_SHOT -> R.string.upgrade_name_bounce_shot
        UpgradeType.DAMAGE_PER_METER -> R.string.upgrade_name_damage_per_meter
        UpgradeType.RAPID_FIRE -> R.string.upgrade_name_rapid_fire
        UpgradeType.HEALTH -> R.string.upgrade_name_health
        UpgradeType.HEALTH_REGEN -> R.string.upgrade_name_health_regen
        UpgradeType.DEFENSE_PERCENT -> R.string.upgrade_name_defense_percent
        UpgradeType.DEFENSE_ABSOLUTE -> R.string.upgrade_name_defense_absolute
        UpgradeType.KNOCKBACK -> R.string.upgrade_name_knockback
        UpgradeType.THORN_DAMAGE -> R.string.upgrade_name_thorn_damage
        UpgradeType.ORBS -> R.string.upgrade_name_orbs
        UpgradeType.LIFESTEAL -> R.string.upgrade_name_lifesteal
        UpgradeType.DEATH_DEFY -> R.string.upgrade_name_death_defy
        UpgradeType.CASH_BONUS -> R.string.upgrade_name_cash_bonus
        UpgradeType.CASH_PER_WAVE -> R.string.upgrade_name_cash_per_wave
        UpgradeType.INTEREST -> R.string.upgrade_name_interest
        UpgradeType.FREE_UPGRADES -> R.string.upgrade_name_free_upgrades
        UpgradeType.RECOVERY_PACKAGES -> R.string.upgrade_name_recovery_packages
        UpgradeType.STEP_MULTIPLIER -> R.string.upgrade_name_step_multiplier
    }

@StringRes
fun ResearchType.nameRes(): Int =
    when (this) {
        ResearchType.DAMAGE_RESEARCH -> R.string.research_name_damage_research
        ResearchType.HEALTH_RESEARCH -> R.string.research_name_health_research
        ResearchType.CASH_RESEARCH -> R.string.research_name_cash_research
        ResearchType.STEP_EFFICIENCY -> R.string.research_name_step_efficiency
        ResearchType.WAVE_SKIP -> R.string.research_name_wave_skip
        ResearchType.AUTO_UPGRADE_AI -> R.string.research_name_auto_upgrade_ai
        ResearchType.UW_COOLDOWN -> R.string.research_name_uw_cooldown
        ResearchType.CRITICAL_RESEARCH -> R.string.research_name_critical_research
        ResearchType.REGEN_RESEARCH -> R.string.research_name_regen_research
        ResearchType.ENEMY_INTEL -> R.string.research_name_enemy_intel
        ResearchType.MULTISHOT_RESEARCH -> R.string.research_name_multishot_research
        ResearchType.BOUNCE_RESEARCH -> R.string.research_name_bounce_research
    }

@StringRes
fun UltimateWeaponType.nameRes(): Int =
    when (this) {
        UltimateWeaponType.DEATH_WAVE -> R.string.uw_name_death_wave
        UltimateWeaponType.CHAIN_LIGHTNING -> R.string.uw_name_chain_lightning
        UltimateWeaponType.BLACK_HOLE -> R.string.uw_name_black_hole
        UltimateWeaponType.CHRONO_FIELD -> R.string.uw_name_chrono_field
        UltimateWeaponType.POISON_SWAMP -> R.string.uw_name_poison_swamp
        UltimateWeaponType.GOLDEN_ZIGGURAT -> R.string.uw_name_golden_ziggurat
    }

@StringRes
fun CardType.nameRes(): Int =
    when (this) {
        CardType.IRON_SKIN -> R.string.card_name_iron_skin
        CardType.SHARP_SHOOTER -> R.string.card_name_sharp_shooter
        CardType.CASH_GRAB -> R.string.card_name_cash_grab
        CardType.VAMPIRIC_TOUCH -> R.string.card_name_vampiric_touch
        CardType.CHAIN_REACTION -> R.string.card_name_chain_reaction
        CardType.SECOND_WIND -> R.string.card_name_second_wind
        CardType.WALKING_FORTRESS -> R.string.card_name_walking_fortress
        CardType.GLASS_CANNON -> R.string.card_name_glass_cannon
        CardType.STEP_SURGE -> R.string.card_name_step_surge
    }

@StringRes
fun Biome.nameRes(): Int =
    when (this) {
        Biome.HANGING_GARDENS -> R.string.biome_name_hanging_gardens
        Biome.BURNING_SANDS -> R.string.biome_name_burning_sands
        Biome.FROZEN_ZIGGURATS -> R.string.biome_name_frozen_ziggurats
        Biome.UNDERWORLD_OF_KUR -> R.string.biome_name_underworld_of_kur
        Biome.CELESTIAL_GATE -> R.string.biome_name_celestial_gate
    }

@StringRes
fun BattleCondition.nameRes(): Int =
    when (this) {
        BattleCondition.ORB_RESISTANCE -> R.string.battle_condition_orb_resistance
        BattleCondition.KNOCKBACK_RESISTANCE -> R.string.battle_condition_knockback_resistance
        BattleCondition.ARMORED_ENEMIES -> R.string.battle_condition_armored_enemies
        BattleCondition.THORN_RESISTANCE -> R.string.battle_condition_thorn_resistance
        BattleCondition.MORE_BOSSES -> R.string.battle_condition_more_bosses
        BattleCondition.ENEMY_SPEED -> R.string.battle_condition_enemy_speed
        BattleCondition.ENEMY_ATTACK_SPEED -> R.string.battle_condition_enemy_attack_speed
    }

/**
 * #34 phase 3 (G): cosmetic name/description resolvers keyed by the stable `cosmeticId`. The
 * localized value is resolved at the Store render site rather than baked into the seed DB rows
 * (which would freeze at seed time and need a migration to change). Returns 0 for an unknown id
 * so the render site can fall back to the stored `name`/`description` (resilient to future ids).
 */
@StringRes
fun cosmeticNameRes(id: String): Int =
    when (id) {
        "zig_jade" -> R.string.cosmetic_name_zig_jade
        "lapis_lazuli_skin" -> R.string.cosmetic_name_lapis_lazuli_skin
        "garden_ziggurat_skin" -> R.string.cosmetic_name_garden_ziggurat_skin
        "sandals_of_gilgamesh" -> R.string.cosmetic_name_sandals_of_gilgamesh
        "zig_obsidian" -> R.string.cosmetic_name_zig_obsidian
        "zig_crystal" -> R.string.cosmetic_name_zig_crystal
        "zig_golden" -> R.string.cosmetic_name_zig_golden
        else -> 0
    }

@StringRes
fun cosmeticDescRes(id: String): Int =
    when (id) {
        "zig_jade" -> R.string.cosmetic_desc_zig_jade
        "lapis_lazuli_skin" -> R.string.cosmetic_desc_lapis_lazuli_skin
        "garden_ziggurat_skin" -> R.string.cosmetic_desc_garden_ziggurat_skin
        "sandals_of_gilgamesh" -> R.string.cosmetic_desc_sandals_of_gilgamesh
        "zig_obsidian" -> R.string.cosmetic_desc_zig_obsidian
        "zig_crystal" -> R.string.cosmetic_desc_zig_crystal
        "zig_golden" -> R.string.cosmetic_desc_zig_golden
        else -> 0
    }

/**
 * #34 phase 3 (G): level-aware Card effect description resolver. Moved out of the domain
 * ([CardType]) so `domain/` stays Android-free — the numeric lerp ([CardType.effectAtLevel] /
 * [CardType.secondaryAtLevel]) stays on the enum (gameplay uses it), while the user-facing string
 * is built here via @StringRes templates. Byte-identical to the old `effectDescriptionAtLevel`:
 * same `.toInt()` truncation on the primary/secondary values + the same STEP_SURGE
 * [formatMultiplier] one-decimal formatting (trailing `.0` stripped, [java.util.Locale.ROOT]).
 * Exhaustive `when` (no `else`) so a new card that forgets its template fails to compile.
 */
@Composable
fun CardType.effectDescription(level: Int): String {
    val v = effectAtLevel(level).toInt()
    val sv = secondaryAtLevel(level).toInt()
    return when (this) {
        CardType.IRON_SKIN -> {
            stringResource(R.string.card_effect_iron_skin, v)
        }

        CardType.SHARP_SHOOTER -> {
            stringResource(R.string.card_effect_sharp_shooter, v)
        }

        CardType.CASH_GRAB -> {
            stringResource(R.string.card_effect_cash_grab, v)
        }

        CardType.VAMPIRIC_TOUCH -> {
            stringResource(R.string.card_effect_vampiric_touch, v)
        }

        CardType.CHAIN_REACTION -> {
            stringResource(R.string.card_effect_chain_reaction, v)
        }

        CardType.SECOND_WIND -> {
            stringResource(R.string.card_effect_second_wind, v)
        }

        CardType.WALKING_FORTRESS -> {
            stringResource(R.string.card_effect_walking_fortress, v, sv)
        }

        CardType.GLASS_CANNON -> {
            stringResource(R.string.card_effect_glass_cannon, v, sv)
        }

        CardType.STEP_SURGE -> {
            stringResource(R.string.card_effect_step_surge, formatMultiplier(effectAtLevel(level)))
        }
    }
}

/**
 * Formats a multiplier with a single decimal place, stripping a trailing `.0` so integer values
 * render as "2" instead of "2.0". Used by STEP_SURGE's [CardType.effectDescription] so its text
 * matches [CardType.effectLv1] / [CardType.effectLv7] verbatim at the endpoints while still showing
 * fractional progress mid-curve (e.g. Lv4 → 3.5). Pinned to [java.util.Locale.ROOT] so the decimal
 * separator stays `.` regardless of device locale. Moved from `CardType`'s private companion (#34).
 */
private fun formatMultiplier(value: Double): String {
    val s = String.format(java.util.Locale.ROOT, "%.1f", value)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
