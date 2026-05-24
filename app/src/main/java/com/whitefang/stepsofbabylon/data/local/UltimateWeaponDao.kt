package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
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
}
