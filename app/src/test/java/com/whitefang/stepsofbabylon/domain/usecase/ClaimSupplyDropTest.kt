package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaimSupplyDropTest {

    private val playerRepo = FakePlayerRepository()
    private val encounterRepo = FakeWalkingEncounterRepository()
    private val cardRepo = FakeCardRepository()
    private val sut = ClaimSupplyDrop(encounterRepo, playerRepo, cardRepo)

    private fun makeDrop(reward: SupplyDropReward, amount: Int, claimed: Boolean = false) =
        SupplyDrop(id = 1, trigger = SupplyDropTrigger.RANDOM, reward = reward, rewardAmount = amount, claimed = claimed, createdAt = 1000)

    @Test
    fun `claiming steps drop adds steps to player`() = runTest {
        playerRepo.ensureProfileExists()
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 150)
        val drop = encounterRepo.observeUnclaimed().first().first()

        val result = sut(drop)

        assertInstanceOf(ClaimSupplyDrop.Result.Success::class.java, result)
        assertEquals(150L, playerRepo.observeWallet().first().stepBalance)
    }

    @Test
    fun `claiming gems drop adds gems to player`() = runTest {
        playerRepo.ensureProfileExists()
        encounterRepo.createDrop(SupplyDropTrigger.STEP_THRESHOLD, SupplyDropReward.GEMS, 3)
        val drop = encounterRepo.observeUnclaimed().first().first()

        sut(drop)

        assertEquals(3L, playerRepo.observeWallet().first().gems)
    }

    @Test
    fun `claiming power stones drop adds power stones`() = runTest {
        playerRepo.ensureProfileExists()
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.POWER_STONES, 2)
        val drop = encounterRepo.observeUnclaimed().first().first()

        sut(drop)

        assertEquals(2L, playerRepo.observeWallet().first().powerStones)
    }

    @Test
    fun `claiming card copy drop adds card to inventory`() = runTest {
        playerRepo.ensureProfileExists()
        // rewardAmount=0 → CardType.entries[0] = IRON_SKIN
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.CARD_COPY, 0)
        val drop = encounterRepo.observeUnclaimed().first().first()

        sut(drop)

        assertEquals(1, cardRepo.cards.value.size)
    }

    @Test
    fun `claiming marks drop as claimed`() = runTest {
        playerRepo.ensureProfileExists()
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val drop = encounterRepo.observeUnclaimed().first().first()

        sut(drop)

        assertEquals(0, encounterRepo.observeUnclaimed().first().size)
    }

    @Test
    fun `claiming already claimed drop returns AlreadyClaimed`() = runTest {
        val drop = makeDrop(SupplyDropReward.STEPS, 100, claimed = true)
        val result = sut(drop)
        assertInstanceOf(ClaimSupplyDrop.Result.AlreadyClaimed::class.java, result)
    }

    // #122 (audit #9): a rapid double-tap presents the SAME unclaimed snapshot twice. The atomic
    // guarded claim (markClaimed AND claimed = 0) lets only the first call credit; the second sees
    // 0 rows and returns AlreadyClaimed. Pre-fix the credit ran before an unconditional mark, so
    // both taps credited — a double credit of premium currency.
    @Test
    fun `R122 double-claim credits the reward exactly once`() = runTest {
        playerRepo.ensureProfileExists()
        encounterRepo.createDrop(SupplyDropTrigger.STEP_THRESHOLD, SupplyDropReward.GEMS, 25)
        val drop = encounterRepo.observeUnclaimed().first().first()

        val first = sut(drop)
        // Second tap reuses the same stale (claimed == false) snapshot.
        val second = sut(drop)

        assertInstanceOf(ClaimSupplyDrop.Result.Success::class.java, first)
        assertInstanceOf(ClaimSupplyDrop.Result.AlreadyClaimed::class.java, second)
        assertEquals(25L, playerRepo.observeWallet().first().gems, "gems credited exactly once")
    }
}
