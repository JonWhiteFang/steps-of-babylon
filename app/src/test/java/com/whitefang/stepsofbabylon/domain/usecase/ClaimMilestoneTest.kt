package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneDao
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ClaimMilestoneTest {

    private lateinit var dao: FakeMilestoneDao
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var playerProfileDao: PlayerProfileDao
    private lateinit var useCase: ClaimMilestone

    @BeforeEach
    fun setup() {
        playerRepo = FakePlayerRepository(PlayerProfile(totalStepsEarned = 10_000_000))
        dao = FakeMilestoneDao(linkedPlayer = playerRepo)
        playerProfileDao = mock()
        useCase = ClaimMilestone(dao, playerRepo, playerProfileDao)
    }

    @Test
    fun `claiming milestone without reaching step threshold returns false`() = runTest {
        val lowStepsRepo = FakePlayerRepository(PlayerProfile(totalStepsEarned = 500))
        val lowDao = FakeMilestoneDao(linkedPlayer = lowStepsRepo)
        val uc = ClaimMilestone(lowDao, lowStepsRepo, mock())
        val result = uc(Milestone.MORNING_JOGGER) // requires 10,000
        assertFalse(result)
        assertEquals(0, lowStepsRepo.observeWallet().first().gems)
        // Step-threshold guard short-circuits before the atomic DAO call.
        assertEquals(0, lowDao.claimMilestoneAtomicCallCount)
    }

    @Test
    fun `credits Gems correctly`() = runTest {
        val result = useCase(Milestone.MORNING_JOGGER)
        assertTrue(result)
        assertEquals(25, playerRepo.observeWallet().first().gems)
    }

    @Test
    fun `credits Gems and Power Stones for IRON_SOLES`() = runTest {
        useCase(Milestone.IRON_SOLES)
        val wallet = playerRepo.observeWallet().first()
        assertEquals(200, wallet.gems)
        assertEquals(50, wallet.powerStones)
    }

    @Test
    fun `marks milestone as claimed`() = runTest {
        useCase(Milestone.FIRST_STEPS)
        val entity = dao.getByIdOnce(Milestone.FIRST_STEPS.name)
        assertNotNull(entity)
        assertTrue(entity!!.claimed)
        assertNotNull(entity.claimedAt)
    }

    @Test
    fun `claiming twice is no-op`() = runTest {
        useCase(Milestone.MORNING_JOGGER)
        val secondResult = useCase(Milestone.MORNING_JOGGER)
        assertFalse(secondResult)
        assertEquals(25, playerRepo.observeWallet().first().gems) // Not doubled
    }

    // ----------------------------------------------------------------------
    // RO-02 (B.2 PR 4) atomicity tests
    // ----------------------------------------------------------------------

    @Test
    fun `successful claim goes through atomic DAO method exactly once`() = runTest {
        val result = useCase(Milestone.FIRST_STEPS)
        assertTrue(result)
        // Proves the use case delegates to the atomic path (not the legacy
        // addGems + upsert split sequence that this PR replaced).
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
        // And still credits through the linked player (which the fake routes
        // inside the atomic block, matching the real Room transaction body).
        val wallet = playerRepo.observeWallet().first()
        assertEquals(60, wallet.gems)
    }

    @Test
    fun `two concurrent claims on the same milestone - only one credits`() = runTest {
        // IRON_SOLES: 200 Gems + 50 Power Stones + (Cosmetic no-op). Starting from zero
        // on the wallet side so we can assert "credited exactly one reward, not two".
        val repo = FakePlayerRepository(
            PlayerProfile(totalStepsEarned = 10_000_000, gems = 0, powerStones = 0),
        )
        val race = FakeMilestoneDao(linkedPlayer = repo)
        val uc = ClaimMilestone(race, repo, mock())

        val results = listOf(
            async { uc(Milestone.IRON_SOLES) },
            async { uc(Milestone.IRON_SOLES) },
        ).awaitAll()

        // Exactly one winner, exactly one loser.
        assertEquals(1, results.count { it })
        assertEquals(1, results.count { !it })
        // Wallet credited exactly once (200 Gems + 50 PS), not twice.
        val wallet = repo.observeWallet().first()
        assertEquals(200, wallet.gems)
        assertEquals(50, wallet.powerStones)
        // Both callers reached the atomic path; mutex + claimed flag decided the winner.
        assertEquals(2, race.claimMilestoneAtomicCallCount)
        // Milestone marked claimed exactly once (single row).
        val entity = race.getByIdOnce(Milestone.IRON_SOLES.name)
        assertNotNull(entity)
        assertTrue(entity!!.claimed)
    }

    @Test
    fun `already-claimed entity pre-existing in DAO causes invoke to short-circuit`() = runTest {
        // Seed the DAO with a claimed entity directly (emulates "claim committed in a
        // previous process lifecycle"). The atomic method should observe claimed=true
        // and return false without re-crediting.
        dao.upsert(MilestoneEntity(Milestone.MORNING_JOGGER.name, claimed = true, claimedAt = 1L))
        val result = useCase(Milestone.MORNING_JOGGER)
        assertFalse(result)
        assertEquals(0, playerRepo.observeWallet().first().gems)
        // The atomic method was still called (guard lives inside it, not outside).
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }
}
