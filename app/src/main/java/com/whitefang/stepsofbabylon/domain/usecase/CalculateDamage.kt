package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import kotlin.random.Random

data class DamageResult(
    val amount: Double,
    val isCrit: Boolean,
)

class CalculateDamage(
    private val random: Random = Random,
) {
    operator fun invoke(
        stats: ResolvedStats,
        distanceToEnemy: Float,
    ): DamageResult {
        var raw = stats.damage
        if (stats.damagePerMeterBonus > 0 && stats.range > 0) {
            raw *= 1 + (distanceToEnemy / stats.range) * stats.damagePerMeterBonus
        }
        val isCrit = random.nextDouble() < stats.critChance
        val final_ = if (isCrit) raw * stats.critMultiplier else raw
        return DamageResult(final_, isCrit)
    }
}
