package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import kotlin.math.floor
import kotlin.math.min

class ResolveStats {

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int> = emptyMap(),
    ): ResolvedStats {
        fun ws(type: UpgradeType) = workshopLevels[type] ?: 0
        fun ir(type: UpgradeType) = inRoundLevels[type] ?: 0

        return ResolvedStats(
            damage = ZigguratBaseStats.BASE_DAMAGE *
                (1 + ws(UpgradeType.DAMAGE) * 0.02) *
                (1 + ir(UpgradeType.DAMAGE) * 0.02),
            attackSpeed = ZigguratBaseStats.BASE_ATTACK_SPEED *
                (1 + ws(UpgradeType.ATTACK_SPEED) * 0.015) *
                (1 + ir(UpgradeType.ATTACK_SPEED) * 0.015),
            critChance = min(ws(UpgradeType.CRITICAL_CHANCE) * 0.005, 0.80),
            critMultiplier = 2.0 + ws(UpgradeType.CRITICAL_FACTOR) * 0.1,
            range = (ZigguratBaseStats.BASE_RANGE *
                min(1.0 + ws(UpgradeType.RANGE) * 0.02, 3.0)).toFloat(),
            maxHealth = ZigguratBaseStats.BASE_HEALTH *
                (1 + ws(UpgradeType.HEALTH) * 0.03) *
                (1 + ir(UpgradeType.HEALTH) * 0.03),
            healthRegen = ZigguratBaseStats.BASE_REGEN *
                (1 + ws(UpgradeType.HEALTH_REGEN) * 0.02),
            defensePercent = min(ws(UpgradeType.DEFENSE_PERCENT) * 0.003, 0.75),
            defenseAbsolute = ws(UpgradeType.DEFENSE_ABSOLUTE) * 1.0,
            knockbackForce = (ZigguratBaseStats.BASE_KNOCKBACK *
                (1 + ws(UpgradeType.KNOCKBACK) * 0.02)).toFloat(),
            thornPercent = ws(UpgradeType.THORN_DAMAGE) * 0.01,
            lifestealPercent = min(ws(UpgradeType.LIFESTEAL) * 0.002, 0.15),
            damagePerMeterBonus = ws(UpgradeType.DAMAGE_PER_METER) * 0.01,
            deathDefyChance = min(ws(UpgradeType.DEATH_DEFY) * 0.01, 0.50),
            multishotTargets = min(1 + floor(ws(UpgradeType.MULTISHOT) / 20.0).toInt(), 5),
            bounceCount = min(floor(ws(UpgradeType.BOUNCE_SHOT) / 15.0).toInt(), 4),
            orbCount = min(ws(UpgradeType.ORBS), 6),
        )
    }
}
