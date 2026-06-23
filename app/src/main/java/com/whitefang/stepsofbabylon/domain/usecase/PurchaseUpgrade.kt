package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository

/**
 * Purchases a single workshop upgrade level.
 *
 * Delegates to [WorkshopRepository.purchaseUpgradeAtomic] so that the Step deduction and workshop
 * level increment commit as a single Room transaction. The atomic path closes two previously-open
 * correctness windows:
 *  - **Partial-failure gap** — a crash between the two writes could previously charge the player
 *    without crediting the upgrade (or vice versa).
 *  - **Double-tap race** — two concurrent purchase clicks could both pass an in-memory affordability
 *    check and double-spend. The SQL-guarded deduct (`WHERE balance >= :cost`) means at most one
 *    concurrent purchase succeeds.
 *
 * The [wallet] param is kept as a UI-side fast-fail hint (avoids a DB round-trip when the player
 * is obviously short), but the repository's atomic method is the authoritative guard.
 */
class PurchaseUpgrade(
    private val workshopRepository: WorkshopRepository,
    private val calculateCost: CalculateUpgradeCost = CalculateUpgradeCost(),
) {
    suspend operator fun invoke(
        type: UpgradeType,
        currentLevel: Int,
        wallet: PlayerWallet,
    ): Boolean {
        val maxLevel = type.config.maxLevel
        if (maxLevel != null && currentLevel >= maxLevel) return false

        val cost = calculateCost(type, currentLevel)
        // Fast-fail path: if the wallet snapshot is obviously short, skip the DB round-trip.
        // The atomic repo method below is still the authoritative guard (handles stale snapshots
        // and concurrent purchases).
        if (wallet.stepBalance < cost) return false

        return workshopRepository.purchaseUpgradeAtomic(type, currentLevel + 1, cost)
    }
}
