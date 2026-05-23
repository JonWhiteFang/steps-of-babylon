package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import kotlin.math.min

/**
 * Resolves the player's permanent (Workshop) and temporary (in-round) upgrade levels into a
 * concrete [ResolvedStats] snapshot the engine consumes for combat math.
 *
 * **Multiplicative stats** (`damage`, `attackSpeed`, `maxHealth`, `healthRegen`, `range`,
 * `knockbackForce`) follow the formula documented in `docs/battle-formulas.md`:
 *
 *     baseStat × (1 + workshopLevel × perLevel) × (1 + inRoundLevel × perLevel)
 *
 * **Additive stats** (crit chance, defense %, lifesteal, thorn, death-defy, multishot,
 * bounce, orbs, damage/meter, defense absolute, crit factor) sum the two level sources
 * before applying the per-level effect and any cap. This matches GDD §5: "All Workshop
 * stats have corresponding in-round upgrades that stack multiplicatively."
 *
 * Pre-RO-08 only DAMAGE / ATTACK_SPEED / HEALTH had an `ir(...)` term — the other 14 stat
 * upgrades silently produced no in-round effect even though the in-round upgrade menu
 * would happily deduct cash for them. That gap is closed here.
 *
 * **Lab research stacks as a third multiplicative tier** (RO-11). DAMAGE_RESEARCH /
 * HEALTH_RESEARCH (+5 % each per level), CRITICAL_RESEARCH (+3 %/lvl), and REGEN_RESEARCH
 * (+4 %/lvl) attach to the matching base stats; the formula extends to:
 *
 *     baseStat × (1 + ws × perLevel) × (1 + ir × perLevel) × (1 + lab × labPerLevel)
 *
 * Pre-RO-11 all 10 [ResearchType] enums were dead — declared with effect descriptions and
 * costing Steps + real-time + Gems to complete, but never read by any combat-path consumer.
 * The optional [labLevels] parameter defaults to `emptyMap()` so the existing call sites
 * that don't yet pass it (workshop screen previews) keep their current behaviour. The
 * remaining lab research types (CASH_RESEARCH / UW_COOLDOWN — engine-side; STEP_EFFICIENCY —
 * walking-credit) are wired in their own dedicated commits because they don't pass through
 * `ResolvedStats`.
 */
class ResolveStats {

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int> = emptyMap(),
        labLevels: Map<ResearchType, Int> = emptyMap(),
    ): ResolvedStats {
        fun ws(type: UpgradeType) = workshopLevels[type] ?: 0
        fun ir(type: UpgradeType) = inRoundLevels[type] ?: 0
        // Sum the two level sources for additive stats. Caps still apply afterwards
        // to the combined level via the existing `min(...)` clamps.
        fun total(type: UpgradeType) = ws(type) + ir(type)
        fun lab(type: ResearchType) = labLevels[type] ?: 0

        return ResolvedStats(
            damage = ZigguratBaseStats.BASE_DAMAGE *
                (1 + ws(UpgradeType.DAMAGE) * 0.02) *
                (1 + ir(UpgradeType.DAMAGE) * 0.02) *
                (1 + lab(ResearchType.DAMAGE_RESEARCH) * 0.05),
            attackSpeed = ZigguratBaseStats.BASE_ATTACK_SPEED *
                (1 + ws(UpgradeType.ATTACK_SPEED) * 0.015) *
                (1 + ir(UpgradeType.ATTACK_SPEED) * 0.015),
            critChance = min(total(UpgradeType.CRITICAL_CHANCE) * 0.005, 0.80),
            critMultiplier = (2.0 + total(UpgradeType.CRITICAL_FACTOR) * 0.1) *
                (1 + lab(ResearchType.CRITICAL_RESEARCH) * 0.03),
            range = (
                ZigguratBaseStats.BASE_RANGE *
                    (1.0 + ws(UpgradeType.RANGE) * 0.02) *
                    (1.0 + ir(UpgradeType.RANGE) * 0.02)
                ).coerceAtMost(ZigguratBaseStats.BASE_RANGE * 3.0).toFloat(),
            maxHealth = ZigguratBaseStats.BASE_HEALTH *
                (1 + ws(UpgradeType.HEALTH) * 0.03) *
                (1 + ir(UpgradeType.HEALTH) * 0.03) *
                (1 + lab(ResearchType.HEALTH_RESEARCH) * 0.05),
            healthRegen = ZigguratBaseStats.BASE_REGEN *
                (1 + ws(UpgradeType.HEALTH_REGEN) * 0.02) *
                (1 + ir(UpgradeType.HEALTH_REGEN) * 0.02) *
                (1 + lab(ResearchType.REGEN_RESEARCH) * 0.04),
            defensePercent = min(total(UpgradeType.DEFENSE_PERCENT) * 0.003, 0.75),
            defenseAbsolute = total(UpgradeType.DEFENSE_ABSOLUTE) * 1.0,
            knockbackForce = (
                ZigguratBaseStats.BASE_KNOCKBACK *
                    (1 + ws(UpgradeType.KNOCKBACK) * 0.02) *
                    (1 + ir(UpgradeType.KNOCKBACK) * 0.02)
                ).toFloat(),
            thornPercent = total(UpgradeType.THORN_DAMAGE) * 0.01,
            lifestealPercent = min(total(UpgradeType.LIFESTEAL) * 0.002, 0.15),
            damagePerMeterBonus = total(UpgradeType.DAMAGE_PER_METER) * 0.01,
            deathDefyChance = min(total(UpgradeType.DEATH_DEFY) * 0.01, 0.50),
            multishotTargets = min(
                1 + total(UpgradeType.MULTISHOT) + (labLevels[ResearchType.MULTISHOT_RESEARCH] ?: 0),
                11,
            ),
            bounceCount = min(
                total(UpgradeType.BOUNCE_SHOT) + (labLevels[ResearchType.BOUNCE_RESEARCH] ?: 0),
                10,
            ),
            orbCount = min(total(UpgradeType.ORBS), 6),
        )
    }
}
