package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UltimateWeaponDao {

    @Query("SELECT * FROM ultimate_weapon_state")
    fun getAll(): Flow<List<UltimateWeaponStateEntity>>

    @Query("SELECT * FROM ultimate_weapon_state WHERE isEquipped = 1")
    fun getEquipped(): Flow<List<UltimateWeaponStateEntity>>

    @Upsert
    suspend fun upsert(entity: UltimateWeaponStateEntity)

    @Query("SELECT COUNT(*) FROM ultimate_weapon_state WHERE isEquipped = 1")
    fun countEquipped(): Flow<Int>

    @Query("SELECT * FROM ultimate_weapon_state WHERE weaponType = :weaponType")
    suspend fun getByType(weaponType: String): UltimateWeaponStateEntity?

    /**
     * Marks an existing row's [UltimateWeaponStateEntity.isUnlocked] flag true without
     * touching path levels or equip state. Used by the unlock flow when the row already
     * exists from a prior partial state (e.g. migration seeded `isUnlocked = false`).
     */
    @Query("UPDATE ultimate_weapon_state SET isUnlocked = 1 WHERE weaponType = :weaponType")
    suspend fun markUnlocked(weaponType: String)

    @Query("UPDATE ultimate_weapon_state SET damageLevel = :level WHERE weaponType = :weaponType")
    suspend fun updateDamageLevel(weaponType: String, level: Int)

    @Query("UPDATE ultimate_weapon_state SET secondaryLevel = :level WHERE weaponType = :weaponType")
    suspend fun updateSecondaryLevel(weaponType: String, level: Int)

    @Query("UPDATE ultimate_weapon_state SET cooldownLevel = :level WHERE weaponType = :weaponType")
    suspend fun updateCooldownLevel(weaponType: String, level: Int)

    /**
     * #236: unlocks an Ultimate Weapon — the guarded Power-Stone deduct and the unlock write
     * commit or roll back as one SQLite transaction, closing the partial-failure gap where a crash
     * between [PlayerProfileDao.spendPowerStonesAtomic] and the unlock permanently debited Power
     * Stones with no weapon unlocked. Mirrors [MilestoneDao.claimMilestoneAtomic]: the [playerDao]
     * call joins this transaction because Room scopes its transaction to the [androidx.room.RoomDatabase].
     *
     * The unlock branch matches [com.whitefang.stepsofbabylon.data.repository.UltimateWeaponRepositoryImpl.unlockWeapon]:
     * insert an `isUnlocked = true` row when none exists, else flip the existing row's flag (covers
     * a migration-seeded `isUnlocked = false` row without duplicating).
     *
     * The already-unlocked re-check runs INSIDE the transaction and short-circuits BEFORE the
     * deduct — like [MilestoneDao.claimMilestoneAtomic]'s claimed-check — so two concurrent unlock
     * taps that both pass the use case's stale `owned` snapshot can't each deduct Power Stones for
     * one weapon (the second transaction observes `isUnlocked = true` and pays nothing).
     *
     * @return `true` if the deduct succeeded and the weapon is unlocked; `false` when the weapon was
     *         already unlocked (no deduct) or the guarded deduct found insufficient Power Stones.
     */
    @Transaction
    suspend fun unlockWeaponAtomic(
        weaponType: String,
        powerStoneCost: Long,
        playerDao: PlayerProfileDao,
    ): Boolean {
        val existing = getByType(weaponType)
        if (existing?.isUnlocked == true) return false
        if (playerDao.spendPowerStonesAtomic(powerStoneCost) == 0) return false
        if (existing == null) {
            upsert(UltimateWeaponStateEntity(weaponType = weaponType, isUnlocked = true))
        } else {
            markUnlocked(weaponType)
        }
        return true
    }
}
