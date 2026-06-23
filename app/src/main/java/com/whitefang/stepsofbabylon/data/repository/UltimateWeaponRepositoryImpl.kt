package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.UltimateWeaponDao
import com.whitefang.stepsofbabylon.data.local.UltimateWeaponStateEntity
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * R4-06 rewrite. Pre-R4-06 [observeUnlockedWeapons] returned every row in the table on
 * the assumption that "row exists" == "unlocked"; post-R4-06 the entity carries an
 * explicit [UltimateWeaponStateEntity.isUnlocked] flag and [observeUnlockedWeapons]
 * filters on it so partial-state rows (created by [upgradePathLevel] before the unlock
 * has been paid for, or by the v9 \u2192 v10 migration's seeding logic) don't surface as
 * unlocked.
 */
class UltimateWeaponRepositoryImpl
    @Inject
    constructor(
        private val dao: UltimateWeaponDao,
        private val playerDao: PlayerProfileDao,
    ) : UltimateWeaponRepository {
        override fun observeUnlockedWeapons(): Flow<List<OwnedWeapon>> =
            dao.getAll().map { list -> list.filter { it.isUnlocked }.map { it.toDomain() } }

        override fun observeEquippedWeapons(): Flow<List<OwnedWeapon>> =
            dao.getEquipped().map { list -> list.filter { it.isUnlocked }.map { it.toDomain() } }

        override suspend fun unlockWeapon(type: UltimateWeaponType) {
            val existing = dao.getByType(type.name)
            if (existing == null) {
                dao.upsert(UltimateWeaponStateEntity(weaponType = type.name, isUnlocked = true))
            } else {
                dao.markUnlocked(type.name)
            }
        }

        override suspend fun unlockWeaponAtomic(
            type: UltimateWeaponType,
            powerStoneCost: Long,
        ): Boolean = dao.unlockWeaponAtomic(type.name, powerStoneCost, playerDao)

        override suspend fun upgradePathLevel(
            type: UltimateWeaponType,
            path: UWPath,
            newLevel: Int,
        ) {
            // Ensure a row exists. The use case only calls this for unlocked UWs, so the
            // row is guaranteed to exist in the happy path; this defensive insert covers
            // any future data corruption / test path where the row might not exist.
            if (dao.getByType(type.name) == null) {
                dao.upsert(UltimateWeaponStateEntity(weaponType = type.name, isUnlocked = true))
            }
            when (path) {
                UWPath.DAMAGE -> dao.updateDamageLevel(type.name, newLevel)
                UWPath.SECONDARY -> dao.updateSecondaryLevel(type.name, newLevel)
                UWPath.COOLDOWN -> dao.updateCooldownLevel(type.name, newLevel)
            }
        }

        override suspend fun equipWeapon(type: UltimateWeaponType) {
            val entity = dao.getByType(type.name) ?: return
            dao.upsert(entity.copy(isEquipped = true))
        }

        override suspend fun unequipWeapon(type: UltimateWeaponType) {
            val entity = dao.getByType(type.name) ?: return
            dao.upsert(entity.copy(isEquipped = false))
        }

        private fun UltimateWeaponStateEntity.toDomain() =
            OwnedWeapon(
                type = UltimateWeaponType.valueOf(weaponType),
                damageLevel = damageLevel,
                secondaryLevel = secondaryLevel,
                cooldownLevel = cooldownLevel,
                isUnlocked = isUnlocked,
                isEquipped = isEquipped,
            )
    }
