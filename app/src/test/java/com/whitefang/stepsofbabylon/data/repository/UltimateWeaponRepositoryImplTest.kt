package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.UltimateWeaponDao
import com.whitefang.stepsofbabylon.data.local.UltimateWeaponStateEntity
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [UltimateWeaponRepositoryImpl] — R4-06 per-path upgrade dispatch,
 * unlock state transitions, equip/unequip slot logic, and the
 * `observeUnlockedWeapons` filter that excludes locked rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UltimateWeaponRepositoryImplTest {

    private fun mockDao(rows: List<UltimateWeaponStateEntity>): UltimateWeaponDao {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getAll()).thenReturn(MutableStateFlow(rows))
        whenever(dao.getEquipped()).thenReturn(MutableStateFlow(rows.filter { it.isEquipped }))
        return dao
    }

    @Test
    fun `observeUnlockedWeapons filters out locked rows`() = runTest {
        val rows = listOf(
            UltimateWeaponStateEntity(weaponType = "GOLDEN_ZIGGURAT", isUnlocked = true),
            UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = false),
        )
        val dao = mockDao(rows)
        val repo = UltimateWeaponRepositoryImpl(dao)

        val unlocked = repo.observeUnlockedWeapons().first()

        assertEquals(1, unlocked.size)
        assertEquals(UltimateWeaponType.GOLDEN_ZIGGURAT, unlocked.first().type)
    }

    @Test
    fun `observeEquippedWeapons returns empty when none equipped`() = runTest {
        val dao = mockDao(emptyList())
        val repo = UltimateWeaponRepositoryImpl(dao)

        assertTrue(repo.observeEquippedWeapons().first().isEmpty())
    }

    @Test
    fun `observeEquippedWeapons filters out locked-but-equipped rows`() = runTest {
        // Defensive: a row with isEquipped=true but isUnlocked=false should never persist
        // (R4-06 contract), but the filter ensures rendering doesn't surface it.
        val rows = listOf(
            UltimateWeaponStateEntity(weaponType = "GOLDEN_ZIGGURAT", isUnlocked = false, isEquipped = true),
            UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = true, isEquipped = true),
        )
        val dao = mockDao(rows)
        val repo = UltimateWeaponRepositoryImpl(dao)

        val equipped = repo.observeEquippedWeapons().first()

        assertEquals(1, equipped.size)
        assertEquals(UltimateWeaponType.DEATH_WAVE, equipped.first().type)
    }

    @Test
    fun `unlockWeapon inserts new row when none exists`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType(any())).thenReturn(null)
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.unlockWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT)

        val captor = argumentCaptor<UltimateWeaponStateEntity>()
        verify(dao).upsert(captor.capture())
        assertEquals("GOLDEN_ZIGGURAT", captor.firstValue.weaponType)
        assertTrue(captor.firstValue.isUnlocked)
        verify(dao, never()).markUnlocked(any())
    }

    @Test
    fun `unlockWeapon flips isUnlocked flag when row already exists`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType("DEATH_WAVE")).thenReturn(
            UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = false)
        )
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.unlockWeapon(UltimateWeaponType.DEATH_WAVE)

        verify(dao).markUnlocked("DEATH_WAVE")
        verify(dao, never()).upsert(any())
    }

    @Test
    fun `upgradePathLevel DAMAGE dispatches to updateDamageLevel`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType("GOLDEN_ZIGGURAT")).thenReturn(
            UltimateWeaponStateEntity(weaponType = "GOLDEN_ZIGGURAT", isUnlocked = true)
        )
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.upgradePathLevel(UltimateWeaponType.GOLDEN_ZIGGURAT, UWPath.DAMAGE, newLevel = 5)

        verify(dao).updateDamageLevel("GOLDEN_ZIGGURAT", 5)
        verify(dao, never()).updateSecondaryLevel(any(), any())
        verify(dao, never()).updateCooldownLevel(any(), any())
    }

    @Test
    fun `upgradePathLevel SECONDARY dispatches to updateSecondaryLevel`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType("DEATH_WAVE")).thenReturn(
            UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = true)
        )
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.upgradePathLevel(UltimateWeaponType.DEATH_WAVE, UWPath.SECONDARY, newLevel = 3)

        verify(dao).updateSecondaryLevel("DEATH_WAVE", 3)
        verify(dao, never()).updateDamageLevel(any(), any())
        verify(dao, never()).updateCooldownLevel(any(), any())
    }

    @Test
    fun `upgradePathLevel COOLDOWN dispatches to updateCooldownLevel`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType("CHRONO_FIELD")).thenReturn(
            UltimateWeaponStateEntity(weaponType = "CHRONO_FIELD", isUnlocked = true)
        )
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.upgradePathLevel(UltimateWeaponType.CHRONO_FIELD, UWPath.COOLDOWN, newLevel = 7)

        verify(dao).updateCooldownLevel("CHRONO_FIELD", 7)
    }

    @Test
    fun `upgradePathLevel inserts row defensively when missing`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType("GOLDEN_ZIGGURAT")).thenReturn(null)
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.upgradePathLevel(UltimateWeaponType.GOLDEN_ZIGGURAT, UWPath.DAMAGE, newLevel = 1)

        verify(dao).upsert(any())
        verify(dao).updateDamageLevel("GOLDEN_ZIGGURAT", 1)
    }

    @Test
    fun `equipWeapon flips isEquipped flag on existing entity`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        val existing = UltimateWeaponStateEntity(weaponType = "GOLDEN_ZIGGURAT", isUnlocked = true, isEquipped = false)
        whenever(dao.getByType("GOLDEN_ZIGGURAT")).thenReturn(existing)
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.equipWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT)

        val captor = argumentCaptor<UltimateWeaponStateEntity>()
        verify(dao).upsert(captor.capture())
        assertTrue(captor.firstValue.isEquipped)
    }

    @Test
    fun `equipWeapon is no-op when entity does not exist`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        whenever(dao.getByType(any())).thenReturn(null)
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.equipWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT)

        verify(dao, never()).upsert(any())
    }

    @Test
    fun `unequipWeapon flips isEquipped to false on existing entity`() = runTest {
        val dao = mock<UltimateWeaponDao>()
        val existing = UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = true, isEquipped = true)
        whenever(dao.getByType("DEATH_WAVE")).thenReturn(existing)
        val repo = UltimateWeaponRepositoryImpl(dao)

        repo.unequipWeapon(UltimateWeaponType.DEATH_WAVE)

        val captor = argumentCaptor<UltimateWeaponStateEntity>()
        verify(dao).upsert(captor.capture())
        assertFalse(captor.firstValue.isEquipped)
    }
}
