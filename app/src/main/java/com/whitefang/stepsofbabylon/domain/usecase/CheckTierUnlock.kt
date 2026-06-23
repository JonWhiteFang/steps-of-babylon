package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.TierConfig

class CheckTierUnlock {
    operator fun invoke(
        bestWavePerTier: Map<Int, Int>,
        highestUnlockedTier: Int,
    ): Int? {
        var highest: Int? = null
        for (candidateNum in (highestUnlockedTier + 1)..10) {
            val tier = TierConfig.forTier(candidateNum)
            val bestOnRequired = bestWavePerTier[tier.unlockTierRequirement] ?: 0
            if (bestOnRequired >= tier.unlockWaveRequirement) {
                highest = candidateNum
            } else {
                break
            }
        }
        return highest
    }
}
