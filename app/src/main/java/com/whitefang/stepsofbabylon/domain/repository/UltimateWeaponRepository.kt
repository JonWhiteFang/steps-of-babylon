package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import kotlinx.coroutines.flow.Flow

/**
 * R4-06 rewrite. The `upgradeWeapon(type, newLevel)` method on the pre-R4-06 interface
 * advanced a single `level` axis; post-R4-06 each UW has three independent paths
 * (see [UWPath]) and [upgradePathLevel] writes exactly one of them.
 */
interface UltimateWeaponRepository {
    fun observeUnlockedWeapons(): Flow<List<OwnedWeapon>>

    fun observeEquippedWeapons(): Flow<List<OwnedWeapon>>

    suspend fun unlockWeapon(type: UltimateWeaponType)

    /**
     * #236: atomically deduct [powerStoneCost] Power Stones and unlock [type] in a single
     * transaction. Returns `true` when the deduct succeeded and the weapon is now unlocked;
     * `false` when it was already unlocked or the guarded deduct found insufficient Power Stones.
     */
    suspend fun unlockWeaponAtomic(
        type: UltimateWeaponType,
        powerStoneCost: Long,
    ): Boolean

    suspend fun upgradePathLevel(
        type: UltimateWeaponType,
        path: UWPath,
        newLevel: Int,
    )

    suspend fun equipWeapon(type: UltimateWeaponType)

    suspend fun unequipWeapon(type: UltimateWeaponType)
}
