package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.UpgradeType

class QuickInvest(
    private val calculateCost: CalculateUpgradeCost = CalculateUpgradeCost(),
) {
    operator fun invoke(
        upgrades: Map<UpgradeType, Int>,
        wallet: PlayerWallet,
    ): UpgradeType? =
        upgrades.entries
            .filter { (type, level) -> type.config.maxLevel?.let { level < it } != false }
            .map { (type, level) -> type to calculateCost(type, level) }
            .filter { (_, cost) -> wallet.stepBalance >= cost }
            .minByOrNull { (_, cost) -> cost }
            ?.first
}
