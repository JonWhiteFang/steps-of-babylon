package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PurchaseUpgradeTest {
    private val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
    private val workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
    private val sut = PurchaseUpgrade(workshopRepo)

    @Test
    fun `successful purchase deducts steps and increments level`() =
        runTest {
            val wallet = playerRepo.profile.value.toWallet()
            val result = sut(UpgradeType.DAMAGE, 0, wallet)
            assertTrue(result)
            assertEquals(1000 - 50, playerRepo.profile.value.stepBalance) // DAMAGE baseCost=50
            assertEquals(1, workshopRepo.upgrades.value[UpgradeType.DAMAGE])
        }

    @Test
    fun `insufficient steps returns false without mutating either repo`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 10))
            val workshop = FakeWorkshopRepository(linkedPlayer = repo)
            val sut = PurchaseUpgrade(workshop)
            val wallet = repo.profile.value.toWallet()
            val result = sut(UpgradeType.DAMAGE, 0, wallet)
            assertFalse(result)
            // Strengthened: verify BOTH sides unchanged (previously only step side asserted).
            assertEquals(10, repo.profile.value.stepBalance)
            assertNull(workshop.upgrades.value[UpgradeType.DAMAGE])
        }

    @Test
    fun `at max level returns false`() =
        runTest {
            // ORBS maxLevel=6
            val wallet = playerRepo.profile.value.toWallet()
            val result = sut(UpgradeType.ORBS, 6, wallet)
            assertFalse(result)
            assertEquals(1000, playerRepo.profile.value.stepBalance)
        }

    @Test
    fun `level 0 purchase costs exactly baseCost`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 50))
            val workshop = FakeWorkshopRepository(linkedPlayer = repo)
            val sut = PurchaseUpgrade(workshop)
            val wallet = repo.profile.value.toWallet()
            assertTrue(sut(UpgradeType.DAMAGE, 0, wallet))
            assertEquals(0, repo.profile.value.stepBalance)
        }

    // -------- RO-02 atomicity tests --------

    @Test
    fun `successful purchase uses atomic repo method and does not call spendSteps directly`() =
        runTest {
            val wallet = playerRepo.profile.value.toWallet()
            assertTrue(sut(UpgradeType.DAMAGE, 0, wallet))
            // Proves PurchaseUpgrade goes through WorkshopRepository.purchaseUpgradeAtomic and NOT
            // through the legacy PlayerRepository.spendSteps + WorkshopRepository.setUpgradeLevel
            // pair. If someone reintroduces the split flow, this assertion fails.
            assertEquals(0, playerRepo.spendStepsCallCount)
            assertEquals(1, workshopRepo.purchaseUpgradeAtomicCallCount)
        }

    @Test
    fun `purchase skips atomic call when wallet fast-fail trips`() =
        runTest {
            // Wallet says zero balance, so the use case returns false before reaching the repo.
            // This verifies the fast-fail path avoids an unnecessary DB round-trip.
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 0))
            val workshop = FakeWorkshopRepository(linkedPlayer = repo)
            val sut = PurchaseUpgrade(workshop)
            val wallet = repo.profile.value.toWallet()
            assertFalse(sut(UpgradeType.DAMAGE, 0, wallet))
            assertEquals(0, workshop.purchaseUpgradeAtomicCallCount)
        }

    @Test
    fun `two concurrent purchases on exactly sufficient balance - only one succeeds`() =
        runTest {
            // Player has exactly enough Steps for ONE purchase. Two racing coroutines both pass the
            // wallet fast-fail check with a stale snapshot and reach the repo. The Mutex-guarded
            // atomic method (mirroring the SQL WHERE balance >= :cost guard) must ensure only one
            // commits and the balance is deducted exactly once.
            val repo = FakePlayerRepository(PlayerProfile(stepBalance = 50))
            val workshop = FakeWorkshopRepository(linkedPlayer = repo)
            val sut = PurchaseUpgrade(workshop)
            val wallet = repo.profile.value.toWallet()

            val results =
                listOf(
                    async { sut(UpgradeType.DAMAGE, 0, wallet) },
                    async { sut(UpgradeType.DAMAGE, 0, wallet) },
                ).awaitAll()

            val successCount = results.count { it }
            assertEquals(1, successCount, "exactly one of two concurrent purchases must succeed")
            assertEquals(0, repo.profile.value.stepBalance, "cost deducted exactly once")
            assertEquals(1, workshop.upgrades.value[UpgradeType.DAMAGE])
            assertEquals(2, workshop.purchaseUpgradeAtomicCallCount)
        }
}
