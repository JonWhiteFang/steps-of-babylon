package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeUltimateWeaponRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockUltimateWeaponTest {
    private val playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 200))

    // #236: the Power-Stone deduct now routes through the atomic UltimateWeaponRepository.unlockWeaponAtomic,
    // so the UW fake is linked to the player fake to model the guarded deduct gating the unlock.
    private val uwRepo = FakeUltimateWeaponRepository(linkedPlayer = playerRepo)
    private val sut = UnlockUltimateWeapon(uwRepo)

    @Test
    fun `sufficient stones and not owned returns true`() =
        runTest {
            assertTrue(sut(UltimateWeaponType.DEATH_WAVE, 200, emptyList()))
        }

    @Test
    fun `insufficient stones returns false`() =
        runTest {
            assertFalse(sut(UltimateWeaponType.BLACK_HOLE, 50, emptyList()))
        }

    @Test
    fun `already unlocked returns false`() =
        runTest {
            val owned = listOf(OwnedWeapon(UltimateWeaponType.DEATH_WAVE, isUnlocked = true))
            assertFalse(sut(UltimateWeaponType.DEATH_WAVE, 200, owned))
        }

    @Test
    fun `row exists but not unlocked allows unlock`() =
        runTest {
            val owned = listOf(OwnedWeapon(UltimateWeaponType.DEATH_WAVE, isUnlocked = false))
            assertTrue(sut(UltimateWeaponType.DEATH_WAVE, 200, owned))
        }

    // #122 (audit #5): the UW screen has no _processing guard, so two quick taps both read the
    // stale powerStones snapshot. The guarded deduct charges the first and no-ops the second; the
    // second unlock must therefore return false and NOT unlock the weapon for free.
    @Test
    fun `R122 stale snapshot does not unlock a UW for free`() =
        runTest {
            // On-disk Power Stones are 0; both taps pass the stale snapshot (200).
            playerRepo.profile.value = PlayerProfile(powerStones = 0)
            val unlocked = sut(UltimateWeaponType.DEATH_WAVE, powerStones = 200, owned = emptyList())
            assertFalse(unlocked, "a UW must not unlock when the guarded Power Stone deduct fails")
        }

    // #236: deduct + unlock must go through the single atomic path so a crash can't debit Power
    // Stones with no weapon unlocked. Pins that the use case routes through unlockWeaponAtomic and
    // that the deduct actually moved the on-disk balance.
    @Test
    fun `R236 success routes through the atomic unlock path and deducts stones`() =
        runTest {
            val unlocked = sut(UltimateWeaponType.DEATH_WAVE, powerStones = 200, owned = emptyList())
            assertTrue(unlocked)
            assertEquals(1, uwRepo.unlockWeaponAtomicCallCount)
            assertEquals(
                200L - UltimateWeaponType.DEATH_WAVE.unlockCost.toLong(),
                playerRepo.profile.value.powerStones,
            )
        }
}
