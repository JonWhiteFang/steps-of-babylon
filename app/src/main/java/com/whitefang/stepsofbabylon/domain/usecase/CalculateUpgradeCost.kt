package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import kotlin.math.ceil
import kotlin.math.pow

class CalculateUpgradeCost {
    operator fun invoke(
        upgradeType: UpgradeType,
        currentLevel: Int,
    ): Long {
        val config = upgradeType.config
        return ceil(config.baseCost * config.scaling.pow(currentLevel)).toLong()
    }
}
