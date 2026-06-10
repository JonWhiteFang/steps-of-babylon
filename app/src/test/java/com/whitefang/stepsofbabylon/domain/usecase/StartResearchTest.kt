package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartResearchTest {

    private lateinit var labRepo: FakeLabRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: StartResearch

    @BeforeEach
    fun setup() {
        labRepo = FakeLabRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 100_000))
        useCase = StartResearch(labRepo, playerRepo)
    }

    @Test
    fun `success deducts steps and starts research`() = runTest {
        val result = useCase(ResearchType.DAMAGE_RESEARCH, playerRepo.profile.value.toWallet(), 1, now = 1000L)
        assertTrue(result is StartResearch.Result.Success)
        assertTrue(playerRepo.profile.value.stepBalance < 100_000)
        assertTrue(labRepo.active.value.any { it.type == ResearchType.DAMAGE_RESEARCH })
    }

    @Test
    fun `insufficient steps returns error`() = runTest {
        playerRepo.profile.value = PlayerProfile(stepBalance = 0)
        val result = useCase(ResearchType.DAMAGE_RESEARCH, playerRepo.profile.value.toWallet(), 1)
        assertTrue(result is StartResearch.Result.InsufficientSteps)
    }

    @Test
    fun `max level returns error`() = runTest {
        labRepo.levels.value = labRepo.levels.value + (ResearchType.DAMAGE_RESEARCH to 20)
        val result = useCase(ResearchType.DAMAGE_RESEARCH, playerRepo.profile.value.toWallet(), 1)
        assertTrue(result is StartResearch.Result.MaxLevelReached)
    }

    @Test
    fun `already researching returns error`() = runTest {
        labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 0, 999999))
        val result = useCase(ResearchType.DAMAGE_RESEARCH, playerRepo.profile.value.toWallet(), 2)
        assertTrue(result is StartResearch.Result.AlreadyResearching)
    }

    @Test
    fun `no slot available returns error`() = runTest {
        labRepo.active.value = listOf(ActiveResearch(ResearchType.HEALTH_RESEARCH, 0, 0, 999999))
        val result = useCase(ResearchType.DAMAGE_RESEARCH, playerRepo.profile.value.toWallet(), 1)
        assertTrue(result is StartResearch.Result.NoSlotAvailable)
    }

    // #122 (audit #4): TOCTOU — a stale wallet snapshot must not let a second research start for
    // free. Two DIFFERENT research types (so the AlreadyResearching guard doesn't short-circuit),
    // funds for exactly ONE, and >= 2 lab slots. The second start sees the same stale snapshot but
    // the guarded deduct (spendStepsIfSufficient) no-ops, so it must return InsufficientSteps and
    // NOT create a second active-research row. Pre-fix the unguarded spendSteps clamped to 0 and
    // started the second research anyway — a free research.
    @Test
    fun `R122 stale snapshot does not start a second research for free`() = runTest {
        val costDamage = CalculateResearchCost()(ResearchType.DAMAGE_RESEARCH, 0)
        // Fund the wallet for exactly one DAMAGE_RESEARCH start.
        playerRepo.profile.value = PlayerProfile(stepBalance = costDamage)
        // Capture the stale snapshot both taps would have read on screen entry.
        val staleWallet = playerRepo.profile.value.toWallet()

        val first = useCase(ResearchType.DAMAGE_RESEARCH, staleWallet, labSlotCount = 2, now = 1000L)
        assertTrue(first is StartResearch.Result.Success, "first start should succeed")
        assertEquals(0L, playerRepo.profile.value.stepBalance, "balance fully spent by the first start")

        // Second start for a DIFFERENT type, using the SAME stale snapshot (balance looked
        // affordable) — but the wallet is now empty.
        val second = useCase(ResearchType.HEALTH_RESEARCH, staleWallet, labSlotCount = 2, now = 2000L)
        assertTrue(
            second is StartResearch.Result.InsufficientSteps,
            "second start must be refused once the guarded deduct no-ops (got $second)",
        )
        assertEquals(
            1,
            labRepo.active.value.size,
            "exactly one research may be active — the second must not start for free",
        )
        assertEquals(0L, playerRepo.profile.value.stepBalance, "balance must not go negative or be re-spent")
    }
}
