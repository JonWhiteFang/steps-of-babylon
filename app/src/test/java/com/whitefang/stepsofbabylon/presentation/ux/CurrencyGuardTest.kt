package com.whitefang.stepsofbabylon.presentation.ux

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the currency-spend semantics.
 *
 * #122: `spendGems` / `spendPowerStones` are now **guarded** (return `Boolean`, no mutation on
 * insufficient funds) — mirroring the SQL `spendGemsAtomic` / `spendPowerStonesAtomic` DAO guards
 * so a use case can refuse to grant when the deduct fails. The pre-#122 behaviour (clamp to 0 and
 * return Unit) silently let a stale-snapshot caller grant an item for free; these tests pin the new
 * contract. `spendSteps` deliberately KEEPS the clamp (used by the anti-cheat escrow clawback in
 * `StepCrossValidator`, which intentionally deducts a disputed excess that may exceed the balance);
 * the affordability-gated alternative is `spendStepsIfSufficient`.
 */
class CurrencyGuardTest {
    @Test
    fun `spending more gems than balance is rejected and leaves balance unchanged`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(gems = 10))
            assertFalse(repo.spendGems(50), "guarded gem spend must report failure when insufficient")
            assertEquals(10L, repo.profile.value.gems, "a rejected gem spend must not mutate the balance")
        }

    @Test
    fun `sufficient gem spend succeeds and deducts`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(gems = 100))
            assertTrue(repo.spendGems(40))
            assertEquals(60L, repo.profile.value.gems)
        }

    @Test
    fun `spending more power stones than balance is rejected and leaves balance unchanged`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(powerStones = 5))
            assertFalse(repo.spendPowerStones(20))
            assertEquals(5L, repo.profile.value.powerStones)
        }

    @Test
    fun `spendStepsIfSufficient is rejected and leaves balance unchanged when short`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 100))
            assertFalse(repo.spendStepsIfSufficient(500))
            assertEquals(100L, repo.profile.value.stepBalance)
        }

    @Test
    fun `spending more card dust than balance clamps to zero`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(cardDust = 3))
            repo.spendCardDust(100)
            assertEquals(0L, repo.profile.value.cardDust)
        }

    @Test
    fun `spendSteps keeps the clamp for the anti-cheat escrow clawback`() =
        runTest {
            // spendSteps (NOT spendStepsIfSufficient) is the escrow path: it must clamp at 0 so a
            // disputed-excess deduction larger than the balance still zeroes the wallet.
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 100))
            repo.spendSteps(500)
            assertEquals(0L, repo.profile.value.stepBalance)
        }
}
