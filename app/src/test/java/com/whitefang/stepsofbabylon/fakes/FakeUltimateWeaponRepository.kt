package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeUltimateWeaponRepository : UltimateWeaponRepository {
    val weapons = MutableStateFlow<Map<UltimateWeaponType, OwnedWeapon>>(emptyMap())

    override fun observeUnlockedWeapons(): Flow<List<OwnedWeapon>> =
        weapons.map { m -> m.values.filter { it.isUnlocked }.toList() }

    override fun observeEquippedWeapons(): Flow<List<OwnedWeapon>> =
        weapons.map { m -> m.values.filter { it.isEquipped && it.isUnlocked }.toList() }

    override suspend fun unlockWeapon(type: UltimateWeaponType) {
        weapons.update { m ->
            val existing = m[type]
            if (existing != null) m + (type to existing.copy(isUnlocked = true))
            else m + (type to OwnedWeapon(type, isUnlocked = true))
        }
    }

    override suspend fun upgradePathLevel(type: UltimateWeaponType, path: UWPath, newLevel: Int) {
        weapons.update { m ->
            val existing = m[type] ?: OwnedWeapon(type, isUnlocked = true)
            val updated = when (path) {
                UWPath.DAMAGE -> existing.copy(damageLevel = newLevel)
                UWPath.SECONDARY -> existing.copy(secondaryLevel = newLevel)
                UWPath.COOLDOWN -> existing.copy(cooldownLevel = newLevel)
            }
            m + (type to updated)
        }
    }

    override suspend fun equipWeapon(type: UltimateWeaponType) {
        weapons.update { m -> m[type]?.let { m + (type to it.copy(isEquipped = true)) } ?: m }
    }

    override suspend fun unequipWeapon(type: UltimateWeaponType) {
        weapons.update { m -> m[type]?.let { m + (type to it.copy(isEquipped = false)) } ?: m }
    }
}
