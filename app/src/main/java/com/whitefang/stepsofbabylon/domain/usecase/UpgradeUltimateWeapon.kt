package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository

/**
 * R4-06 rewrite. Pre-R4-06 this took `(type, currentLevel, powerStones)` and incremented
 * a single `level` axis from L1 to L10. Post-R4-06 it takes a [path] parameter and
 * advances exactly one of the three independent path axes (see [UWPath]).
 *
 * Cost: [UltimateWeaponType.costForPath] = `unlockCost × 2 × currentPathLevel`. The
 * L0→L1 step is free per the cost formula, treating it as a freebie included in the
 * unlock fee. The L9→L10 step costs `18 × unlockCost`. Total across one path
 * L0→L10 = `90 × unlockCost`; across all three paths = `270 × unlockCost`.
 */
class UpgradeUltimateWeapon(
    private val uwRepository: UltimateWeaponRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend operator fun invoke(
        type: UltimateWeaponType,
        path: UWPath,
        currentPathLevel: Int,
        powerStones: Long,
    ): Boolean {
        if (currentPathLevel >= UltimateWeaponType.MAX_PATH_LEVEL) return false
        val cost = type.costForPath(currentPathLevel)
        if (powerStones < cost) return false
        if (cost > 0) playerRepository.spendPowerStones(cost.toLong())
        uwRepository.upgradePathLevel(type, path, currentPathLevel + 1)
        return true
    }
}
