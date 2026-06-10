package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository

/**
 * R4-06 rewrite. Pre-R4-06 the gate was `owned.any { it.type == type }` — i.e. a row
 * existing in the DAO meant "unlocked". Post-R4-06 the row may exist with
 * `isUnlocked = false` (e.g. created by [UltimateWeaponRepository.upgradePathLevel]
 * before the unlock has been paid for, or by the v9→v10 migration's seeding logic).
 * The gate now reads the explicit [OwnedWeapon.isUnlocked] flag.
 */
class UnlockUltimateWeapon(
    private val uwRepository: UltimateWeaponRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend operator fun invoke(
        type: UltimateWeaponType,
        powerStones: Long,
        owned: List<OwnedWeapon>,
    ): Boolean {
        if (owned.any { it.type == type && it.isUnlocked }) return false
        if (powerStones < type.unlockCost) return false
        // #122: only unlock when the guarded deduct actually moved the balance. The UW screen
        // has no _processing guard, so two quick taps could both pass the stale-snapshot check;
        // gating on the deduct's success prevents a free unlock when the second deduct no-ops.
        if (!playerRepository.spendPowerStones(type.unlockCost.toLong())) return false
        uwRepository.unlockWeapon(type)
        return true
    }
}
