package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import kotlinx.coroutines.flow.Flow

interface WorkshopRepository {
    fun observeAllUpgrades(): Flow<Map<UpgradeType, Int>>

    fun observeUpgradeLevel(type: UpgradeType): Flow<Int>

    fun observeUpgradesByCategory(category: UpgradeCategory): Flow<Map<UpgradeType, Int>>

    suspend fun setUpgradeLevel(
        type: UpgradeType,
        level: Int,
    )

    suspend fun ensureUpgradesExist()

    /**
     * Atomically spend [cost] Steps and set the upgrade to [newLevel] in a single DB transaction.
     *
     * Returns `true` when the player could afford the cost and both writes committed; `false` when
     * the player was short (no mutation occurred). Closes the partial-failure gap between the old
     * two-step `PlayerRepository.spendSteps` + `WorkshopRepository.setUpgradeLevel` pair and the
     * double-tap race where two concurrent purchases could both see the same balance.
     */
    suspend fun purchaseUpgradeAtomic(
        type: UpgradeType,
        newLevel: Int,
        cost: Long,
    ): Boolean
}
