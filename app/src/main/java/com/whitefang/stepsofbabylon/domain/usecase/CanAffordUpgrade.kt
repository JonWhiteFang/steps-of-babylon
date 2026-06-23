package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.UpgradeType

class CanAffordUpgrade(
    private val calculateCost: CalculateUpgradeCost = CalculateUpgradeCost(),
) {
    operator fun invoke(
        wallet: PlayerWallet,
        upgradeType: UpgradeType,
        currentLevel: Int,
    ): Boolean = wallet.stepBalance >= calculateCost(upgradeType, currentLevel)
}
