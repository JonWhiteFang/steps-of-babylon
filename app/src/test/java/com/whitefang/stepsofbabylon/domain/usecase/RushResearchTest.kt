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

class RushResearchTest {
    private lateinit var labRepo: FakeLabRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: RushResearch

    @BeforeEach
    fun setup() {
        labRepo = FakeLabRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 500))
        useCase = RushResearch(labRepo, playerRepo)
    }

    @Test
    fun `full rush costs 200 gems`() =
        runTest {
            val active = ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, startedAt = 0, completesAt = 10000)
            labRepo.active.value = listOf(active)
            val result = useCase(ResearchType.DAMAGE_RESEARCH, active, playerRepo.profile.value.toWallet(), now = 0)
            assertTrue(result is RushResearch.Result.Rushed)
            assertEquals(200L, (result as RushResearch.Result.Rushed).gemCost)
        }

    @Test
    fun `half done rush costs approximately 125 gems`() =
        runTest {
            val active = ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, startedAt = 0, completesAt = 10000)
            labRepo.active.value = listOf(active)
            val result = useCase(ResearchType.DAMAGE_RESEARCH, active, playerRepo.profile.value.toWallet(), now = 5000)
            assertTrue(result is RushResearch.Result.Rushed)
            assertEquals(125L, (result as RushResearch.Result.Rushed).gemCost)
        }

    @Test
    fun `nearly done rush costs 50 gems`() =
        runTest {
            val active = ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, startedAt = 0, completesAt = 10000)
            labRepo.active.value = listOf(active)
            val result = useCase(ResearchType.DAMAGE_RESEARCH, active, playerRepo.profile.value.toWallet(), now = 10000)
            assertTrue(result is RushResearch.Result.Rushed)
            assertEquals(50L, (result as RushResearch.Result.Rushed).gemCost)
        }

    @Test
    fun `insufficient gems returns error`() =
        runTest {
            playerRepo.profile.value = PlayerProfile(gems = 10)
            val active = ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, startedAt = 0, completesAt = 10000)
            labRepo.active.value = listOf(active)
            val result = useCase(ResearchType.DAMAGE_RESEARCH, active, playerRepo.profile.value.toWallet(), now = 0)
            assertTrue(result is RushResearch.Result.InsufficientGems)
        }
}
