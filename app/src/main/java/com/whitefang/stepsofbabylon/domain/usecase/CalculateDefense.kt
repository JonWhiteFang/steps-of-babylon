package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import kotlin.math.max

class CalculateDefense {
    operator fun invoke(
        incomingDamage: Double,
        stats: ResolvedStats,
    ): Double = max(0.0, incomingDamage * (1 - stats.defensePercent) - stats.defenseAbsolute)
}
