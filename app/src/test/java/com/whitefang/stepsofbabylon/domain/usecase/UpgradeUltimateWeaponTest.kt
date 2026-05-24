package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeUltimateWeaponRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpgradeUltimateWeaponTest {

    private val playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 10000))
    private val uwRepo = FakeUltimateWeaponRepository()
    private val sut = UpgradeUltimateWeapon(uwRepo, playerRepo)

    @Test
    fun `upgrade succeeds with sufficient stones`() = runTest {
        // DEATH_WAVE costForPath(1) = 50 * 2 * 1 = 100
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, UWPath.DAMAGE, 1, 10000))
    }

    @Test
    fun `upgrade fails at max path level`() = runTest {
        assertFalse(sut(UltimateWeaponType.DEATH_WAVE, UWPath.DAMAGE, 10, 99999))
    }

    @Test
    fun `upgrade fails with insufficient stones`() = runTest {
        // DEATH_WAVE costForPath(5) = 50 * 2 * 5 = 500
        assertFalse(sut(UltimateWeaponType.DEATH_WAVE, UWPath.DAMAGE, 5, 499))
    }

    @Test
    fun `upgrade cost scales with path level`() = runTest {
        // BLACK_HOLE costForPath(3) = 100 * 2 * 3 = 600
        assertTrue(sut(UltimateWeaponType.BLACK_HOLE, UWPath.SECONDARY, 3, 600))
        assertFalse(sut(UltimateWeaponType.BLACK_HOLE, UWPath.SECONDARY, 3, 599))
    }

    @Test
    fun `L0 to L1 is free`() = runTest {
        // costForPath(0) = unlockCost * 2 * 0 = 0
        assertTrue(sut(UltimateWeaponType.CHAIN_LIGHTNING, UWPath.COOLDOWN, 0, 0))
    }

    @Test
    fun `each path upgrades independently`() = runTest {
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, UWPath.DAMAGE, 1, 10000))
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, UWPath.SECONDARY, 1, 10000))
        assertTrue(sut(UltimateWeaponType.DEATH_WAVE, UWPath.COOLDOWN, 1, 10000))
    }
}
