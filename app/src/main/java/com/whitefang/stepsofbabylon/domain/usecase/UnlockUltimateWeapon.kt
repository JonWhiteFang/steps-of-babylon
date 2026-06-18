package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
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
) {
    suspend operator fun invoke(
        type: UltimateWeaponType,
        powerStones: Long,
        owned: List<OwnedWeapon>,
    ): Boolean {
        if (owned.any { it.type == type && it.isUnlocked }) return false
        if (powerStones < type.unlockCost) return false
        // #236: deduct Power Stones + unlock atomically (one transaction) so a crash between the
        // two can't permanently debit Power Stones with no weapon unlocked. The atomic method keeps
        // the #122 guard (only unlock when the guarded deduct moved the balance — two quick taps
        // both passing the stale `owned`/`powerStones` snapshot can't free-unlock) and additionally
        // re-checks already-unlocked inside the transaction so the second tap pays nothing.
        return uwRepository.unlockWeaponAtomic(type, type.unlockCost.toLong())
    }
}
