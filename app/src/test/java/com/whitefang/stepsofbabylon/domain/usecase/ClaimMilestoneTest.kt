package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImpl
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticDao
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
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
    private lateinit var cosmeticRepo: FakeCosmeticRepository
    private lateinit var useCase: ClaimMilestone

    @BeforeEach
    fun setup() {
        playerRepo = FakePlayerRepository(PlayerProfile(totalStepsEarned = 10_000_000))
        dao = FakeMilestoneDao(linkedPlayer = playerRepo)
        playerProfileDao = mock()
        cosmeticRepo = FakeCosmeticRepository()
        // Default: empty cosmetic catalogue. Tests that need a matching cosmetic id seed
        // `cosmeticRepo.items` themselves. Post-C.2 PR 3b/3c all 3 milestone cosmetic ids
        // are now seeded in prod (see CosmeticRepositoryImpl.SEED_COSMETICS), so there are
        // no longer any prod-state mismatches \u2014 the empty fake used here is now a purely
        // synthetic mechanism-under-test scenario, preserved because the rejection path
        // still needs a regression guard for future content work that could accidentally
        // regress the pre-flight cosmetic-id check.
        useCase = ClaimMilestone(dao, playerRepo, playerProfileDao, cosmeticRepo)
    }

    @Test
    fun `claiming milestone without reaching step threshold returns InsufficientSteps`() = runTest {
        val lowStepsRepo = FakePlayerRepository(PlayerProfile(totalStepsEarned = 500))
        val lowDao = FakeMilestoneDao(linkedPlayer = lowStepsRepo)
        val uc = ClaimMilestone(lowDao, lowStepsRepo, mock(), FakeCosmeticRepository())
        val result = uc(Milestone.MORNING_JOGGER) // requires 10,000
        assertEquals(ClaimMilestoneResult.InsufficientSteps, result)
        assertEquals(0, lowStepsRepo.observeWallet().first().gems)
        // Step-threshold guard short-circuits before the atomic DAO call.
        assertEquals(0, lowDao.claimMilestoneAtomicCallCount)
    }

    @Test
    fun `credits Gems correctly on Success`() = runTest {
        val result = useCase(Milestone.MORNING_JOGGER)
        assertEquals(ClaimMilestoneResult.Success, result)
        assertEquals(25, playerRepo.observeWallet().first().gems)
    }

    @Test
    fun `marks milestone as claimed on Success`() = runTest {
        assertEquals(ClaimMilestoneResult.Success, useCase(Milestone.FIRST_STEPS))
        val entity = dao.getByIdOnce(Milestone.FIRST_STEPS.name)
        assertNotNull(entity)
        assertTrue(entity!!.claimed)
        assertNotNull(entity.claimedAt)
    }

    @Test
    fun `claiming twice returns AlreadyClaimed on second call`() = runTest {
        assertEquals(ClaimMilestoneResult.Success, useCase(Milestone.MORNING_JOGGER))
        assertEquals(ClaimMilestoneResult.AlreadyClaimed, useCase(Milestone.MORNING_JOGGER))
        // Reward not double-credited.
        assertEquals(25, playerRepo.observeWallet().first().gems)
    }

    // ----------------------------------------------------------------------
    // RO-02 (B.2 PR 4) atomicity tests \u2014 updated for C.4 Result type
    // ----------------------------------------------------------------------

    @Test
    fun `successful claim goes through atomic DAO method exactly once`() = runTest {
        val result = useCase(Milestone.FIRST_STEPS)
        assertEquals(ClaimMilestoneResult.Success, result)
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
        // C.4 switched the target from IRON_SOLES (has unknown Cosmetic reward) to
        // MORNING_JOGGER (Gems-only, 25 gems) so the concurrency test stays valid after
        // the cosmetic-id pre-flight check lands. The atomicity invariant being tested
        // (Mutex + claimed-flag serialisation) is independent of reward shape.
        val repo = FakePlayerRepository(
            PlayerProfile(totalStepsEarned = 10_000_000, gems = 0, powerStones = 0),
        )
        val race = FakeMilestoneDao(linkedPlayer = repo)
        val uc = ClaimMilestone(race, repo, mock(), FakeCosmeticRepository())

        val results = listOf(
            async { uc(Milestone.MORNING_JOGGER) },
            async { uc(Milestone.MORNING_JOGGER) },
        ).awaitAll()

        // Exactly one winner, exactly one loser.
        assertEquals(1, results.count { it == ClaimMilestoneResult.Success })
        assertEquals(1, results.count { it == ClaimMilestoneResult.AlreadyClaimed })
        // Wallet credited exactly once (25 Gems), not twice.
        val wallet = repo.observeWallet().first()
        assertEquals(25, wallet.gems)
        assertEquals(0, wallet.powerStones)
        // Both callers reached the atomic path; mutex + claimed flag decided the winner.
        assertEquals(2, race.claimMilestoneAtomicCallCount)
        // Milestone marked claimed exactly once (single row).
        val entity = race.getByIdOnce(Milestone.MORNING_JOGGER.name)
        assertNotNull(entity)
        assertTrue(entity!!.claimed)
    }

    @Test
    fun `already-claimed entity pre-existing in DAO causes invoke to return AlreadyClaimed`() = runTest {
        // Seed the DAO with a claimed entity directly (emulates "claim committed in a
        // previous process lifecycle"). The atomic method should observe claimed=true
        // and return AlreadyClaimed without re-crediting.
        dao.upsert(MilestoneEntity(Milestone.MORNING_JOGGER.name, claimed = true, claimedAt = 1L))
        val result = useCase(Milestone.MORNING_JOGGER)
        assertEquals(ClaimMilestoneResult.AlreadyClaimed, result)
        assertEquals(0, playerRepo.observeWallet().first().gems)
        // The atomic method was still called (guard lives inside it, not outside).
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }

    // ----------------------------------------------------------------------
    // C.4 \u2014 UnknownCosmetic detection tests
    //
    // Post-C.2 PR 3b/3c: all 3 milestone cosmetic ids (lapis_lazuli_skin,
    // garden_ziggurat_skin, sandals_of_gilgamesh) are seeded in prod. The
    // per-milestone `UnknownCosmetic` tests that previously tracked the
    // mismatched ids are gone; the rejection-before-atomic test below is
    // kept as a synthetic mechanism-level regression guard against future
    // content work that could accidentally reintroduce the silent-drop bug.
    // ----------------------------------------------------------------------

    @Test
    fun `UnknownCosmetic rejects claim before the atomic DAO call with no credit`() = runTest {
        // Synthetic regression guard: proves the pre-flight cosmetic-id check MUST
        // short-circuit before the atomic transaction so the player doesn't get partial
        // credit (gems + PS without the cosmetic). In prod today (post-C.2 PR 3b/3c) all
        // milestone cosmetic ids are seeded, so this rejection path is not currently
        // reached by any Milestone enum value \u2014 but the test keeps the mechanism under
        // coverage against regressions (e.g. a future content PR that introduces a new
        // Milestone with an unseeded Cosmetic reward).
        //
        // Uses MARATHON_WALKER against the empty FakeCosmeticRepository: garden_ziggurat_skin
        // would not resolve in the fake (no items.value set), so idExists returns false and
        // the claim is rejected. Against the real CosmeticRepositoryImpl this would Succeed
        // because the id is in SEED_COSMETICS \u2014 see the MARATHON_WALKER end-to-end test below.
        val result = useCase(Milestone.MARATHON_WALKER)
        assertTrue(result is ClaimMilestoneResult.UnknownCosmetic)
        val wallet = playerRepo.observeWallet().first()
        assertEquals(0, wallet.gems)
        assertEquals(0, wallet.powerStones)
        assertEquals(0, dao.claimMilestoneAtomicCallCount)
        assertNull(dao.getByIdOnce(Milestone.MARATHON_WALKER.name))
    }

    // ----------------------------------------------------------------------
    // C.2 PR 3 / 3b / 3c \u2014 end-to-end milestone claim via real CosmeticRepositoryImpl
    //
    // One test per milestone with a Cosmetic reward. Each uses the REAL
    // CosmeticRepositoryImpl (not the FakeCosmeticRepository fixture) so the
    // full chain is exercised: SEED_COSMETICS \u2192 ensureSeedData \u2192 idExists \u2192
    // ClaimMilestone atomic credit \u2192 wallet. A regression that drops any seed
    // row or breaks the lookup surfaces here as a failure.
    // ----------------------------------------------------------------------

    @Test
    fun `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl`() = runTest {
        // IRON_SOLES reward: 200 Gems + 50 Power Stones + Cosmetic("lapis_lazuli_skin").
        // lapis_lazuli_skin was seeded in C.2 PR 3.
        val realRepo = CosmeticRepositoryImpl(FakeCosmeticDao())
        val uc = ClaimMilestone(dao, playerRepo, playerProfileDao, realRepo)

        val result = uc(Milestone.IRON_SOLES)
        assertEquals(ClaimMilestoneResult.Success, result)

        val wallet = playerRepo.observeWallet().first()
        assertEquals(200, wallet.gems)
        assertEquals(50, wallet.powerStones)
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }

    @Test
    fun `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl`() = runTest {
        // MARATHON_WALKER reward: 600 Gems + Cosmetic("garden_ziggurat_skin").
        // garden_ziggurat_skin was seeded in C.2 PR 3b.
        val realRepo = CosmeticRepositoryImpl(FakeCosmeticDao())
        val uc = ClaimMilestone(dao, playerRepo, playerProfileDao, realRepo)

        val result = uc(Milestone.MARATHON_WALKER)
        assertEquals(ClaimMilestoneResult.Success, result)

        val wallet = playerRepo.observeWallet().first()
        assertEquals(600, wallet.gems)
        assertEquals(0, wallet.powerStones)
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }

    @Test
    fun `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl`() = runTest {
        // GLOBE_TROTTER reward: 500 Gems + Cosmetic("sandals_of_gilgamesh").
        // sandals_of_gilgamesh was seeded in C.2 PR 3c (reframed as a bronze Gilgamesh-
        // themed ziggurat variant so it fits the existing ZIGGURAT_SKIN category without
        // requiring a new CosmeticCategory enum value).
        val realRepo = CosmeticRepositoryImpl(FakeCosmeticDao())
        val uc = ClaimMilestone(dao, playerRepo, playerProfileDao, realRepo)

        val result = uc(Milestone.GLOBE_TROTTER)
        assertEquals(ClaimMilestoneResult.Success, result)

        val wallet = playerRepo.observeWallet().first()
        assertEquals(500, wallet.gems)
        assertEquals(0, wallet.powerStones)
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }
}
