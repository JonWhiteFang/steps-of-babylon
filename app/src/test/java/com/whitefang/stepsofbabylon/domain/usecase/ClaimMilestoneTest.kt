package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
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
        // `cosmeticRepo.items` themselves. Tests that expect UnknownCosmetic leave it empty,
        // which matches prod: the 3 mismatched milestone cosmetic ids (garden_ziggurat_skin,
        // lapis_lazuli_skin, sandals_of_gilgamesh) have no corresponding SEED_COSMETICS row.
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
    // ----------------------------------------------------------------------

    @Test
    fun `UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER`() = runTest {
        // MARATHON_WALKER rewards 600 Gems + Cosmetic("garden_ziggurat_skin"). The id is
        // not in prod SEED_COSMETICS so idExists returns false \u2014 claim is rejected.
        val result = useCase(Milestone.MARATHON_WALKER)
        assertTrue(result is ClaimMilestoneResult.UnknownCosmetic)
        assertEquals("garden_ziggurat_skin", (result as ClaimMilestoneResult.UnknownCosmetic).cosmeticId)
    }

    @Test
    fun `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES`() = runTest {
        // IRON_SOLES rewards 200 Gems + 50 PS + Cosmetic("lapis_lazuli_skin").
        val result = useCase(Milestone.IRON_SOLES)
        assertTrue(result is ClaimMilestoneResult.UnknownCosmetic)
        assertEquals("lapis_lazuli_skin", (result as ClaimMilestoneResult.UnknownCosmetic).cosmeticId)
    }

    @Test
    fun `UnknownCosmetic surfaces offending cosmetic id for GLOBE_TROTTER`() = runTest {
        // GLOBE_TROTTER rewards 500 Gems + Cosmetic("sandals_of_gilgamesh").
        val result = useCase(Milestone.GLOBE_TROTTER)
        assertTrue(result is ClaimMilestoneResult.UnknownCosmetic)
        assertEquals("sandals_of_gilgamesh", (result as ClaimMilestoneResult.UnknownCosmetic).cosmeticId)
    }

    @Test
    fun `UnknownCosmetic rejects claim before the atomic DAO call with no credit`() = runTest {
        // Regression-guard: the pre-flight check MUST short-circuit before the atomic
        // transaction so the player doesn't get partial credit (gems + PS without the
        // cosmetic). Proves no wallet movement and no atomic call attempt.
        val result = useCase(Milestone.IRON_SOLES)
        assertTrue(result is ClaimMilestoneResult.UnknownCosmetic)
        val wallet = playerRepo.observeWallet().first()
        assertEquals(0, wallet.gems)
        assertEquals(0, wallet.powerStones)
        assertEquals(0, dao.claimMilestoneAtomicCallCount)
        assertNull(dao.getByIdOnce(Milestone.IRON_SOLES.name))
    }

    @Test
    fun `milestone with matching cosmetic id credits rewards via atomic path`() = runTest {
        // Positive path proving the UnknownCosmetic check is selective, not blanket:
        // when the cosmetic id IS in the catalogue, the atomic credit runs as normal.
        // Emulates the post-C.2-PR-3 state where lapis_lazuli_skin has a matching seed
        // row; once that lands, IRON_SOLES claims will succeed cleanly. The actual
        // cosmetic is not equipped here \u2014 that is the renderer contract (C.2 PR 1 / 2),
        // not the claim contract.
        cosmeticRepo.items.value = listOf(
            CosmeticItem(
                cosmeticId = "lapis_lazuli_skin",
                category = CosmeticCategory.ZIGGURAT_SKIN,
                name = "Lapis Lazuli Ziggurat Skin",
                description = "Test-only fixture emulating C.2 PR 3+ content.",
                priceGems = 0L,
                isOwned = false,
                isEquipped = false,
            ),
        )

        val result = useCase(Milestone.IRON_SOLES)
        assertEquals(ClaimMilestoneResult.Success, result)
        val wallet = playerRepo.observeWallet().first()
        assertEquals(200, wallet.gems)
        assertEquals(50, wallet.powerStones)
        assertEquals(1, dao.claimMilestoneAtomicCallCount)
    }
}
