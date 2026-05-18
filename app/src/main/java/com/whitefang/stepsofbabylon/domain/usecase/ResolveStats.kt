package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import kotlin.math.floor
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
 */
class ResolveStats {

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int> = emptyMap(),
    ): ResolvedStats {
        fun ws(type: UpgradeType) = workshopLevels[type] ?: 0
        fun ir(type: UpgradeType) = inRoundLevels[type] ?: 0
        // Sum the two level sources for additive stats. Caps still apply afterwards
        // to the combined level via the existing `min(...)` clamps.
        fun total(type: UpgradeType) = ws(type) + ir(type)

        return ResolvedStats(
            damage = ZigguratBaseStats.BASE_DAMAGE *
                (1 + ws(UpgradeType.DAMAGE) * 0.02) *
                (1 + ir(UpgradeType.DAMAGE) * 0.02),
            attackSpeed = ZigguratBaseStats.BASE_ATTACK_SPEED *
                (1 + ws(UpgradeType.ATTACK_SPEED) * 0.015) *
                (1 + ir(UpgradeType.ATTACK_SPEED) * 0.015),
            critChance = min(total(UpgradeType.CRITICAL_CHANCE) * 0.005, 0.80),
            critMultiplier = 2.0 + total(UpgradeType.CRITICAL_FACTOR) * 0.1,
            range = (
                ZigguratBaseStats.BASE_RANGE *
                    (1.0 + ws(UpgradeType.RANGE) * 0.02) *
                    (1.0 + ir(UpgradeType.RANGE) * 0.02)
                ).coerceAtMost(ZigguratBaseStats.BASE_RANGE * 3.0).toFloat(),
            maxHealth = ZigguratBaseStats.BASE_HEALTH *
                (1 + ws(UpgradeType.HEALTH) * 0.03) *
                (1 + ir(UpgradeType.HEALTH) * 0.03),
            healthRegen = ZigguratBaseStats.BASE_REGEN *
                (1 + ws(UpgradeType.HEALTH_REGEN) * 0.02) *
                (1 + ir(UpgradeType.HEALTH_REGEN) * 0.02),
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
            multishotTargets = min(1 + floor(total(UpgradeType.MULTISHOT) / 20.0).toInt(), 5),
            bounceCount = min(floor(total(UpgradeType.BOUNCE_SHOT) / 15.0).toInt(), 4),
            orbCount = min(total(UpgradeType.ORBS), 6),
        )
    }
}
