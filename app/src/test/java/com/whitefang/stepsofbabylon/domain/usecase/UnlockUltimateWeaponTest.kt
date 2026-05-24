package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeUltimateWeaponRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockUltimateWeaponTest {

    private val playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 200))
    private val uwRepo = FakeUltimateWeaponRepository()
    private val sut = UnlockUltimateWeapon(uwRepo, playerRepo)

    @Test
    fun `sufficient stones and not owned returns true`() = runTest {
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, 200, emptyList()))
    }

    @Test
    fun `insufficient stones returns false`() = runTest {
        assertFalse(sut(UltimateWeaponType.BLACK_HOLE, 50, emptyList()))
    }

    @Test
    fun `already unlocked returns false`() = runTest {
        val owned = listOf(OwnedWeapon(UltimateWeaponType.DEATH_WAVE, isUnlocked = true))
        assertFalse(sut(UltimateWeaponType.DEATH_WAVE, 200, owned))
    }

    @Test
    fun `row exists but not unlocked allows unlock`() = runTest {
        val owned = listOf(OwnedWeapon(UltimateWeaponType.DEATH_WAVE, isUnlocked = false))
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, 200, owned))
    }
}
