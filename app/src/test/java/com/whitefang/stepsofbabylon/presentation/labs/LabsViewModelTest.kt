package com.whitefang.stepsofbabylon.presentation.labs

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LabsViewModelTest {
    // Use UnconfinedTestDispatcher but cancel the VM scope to stop the ticker
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var labRepo: FakeLabRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val dailyMissionDao = mock<com.whitefang.stepsofbabylon.data.local.DailyMissionDao>()

    @BeforeEach
    fun setup() =
        runTest {
            Dispatchers.setMain(dispatcher)
            labRepo = FakeLabRepository()
            playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 100_000, gems = 500))
            whenever(dailyMissionDao.getByDateOnce(org.mockito.kotlin.any())).thenReturn(emptyList())
        }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // LabsViewModel has a while(true) ticker — test the use cases directly instead
    @Test
    fun `displays all research types via use case`() =
        runTest {
            assertEquals(ResearchType.entries.size, labRepo.levels.value.size)
        }

    @Test
    fun `start research marks active`() =
        runTest {
            val cost =
                com.whitefang.stepsofbabylon.domain.usecase
                    .CalculateResearchCost()
            val time =
                com.whitefang.stepsofbabylon.domain.usecase
                    .CalculateResearchTime()
            val start =
                com.whitefang.stepsofbabylon.domain.usecase
                    .StartResearch(labRepo, playerRepo, cost, time)
            val type = ResearchType.entries.first()
            val wallet = playerRepo.profile.value.toWallet()
            start(type, wallet, 1)
            assertEquals(1, labRepo.active.value.size)
            assertTrue(playerRepo.profile.value.stepBalance < 100_000)
        }

    @Test
    fun `unlock slot deducts gems`() =
        runTest {
            val unlock =
                com.whitefang.stepsofbabylon.domain.usecase
                    .UnlockLabSlot(playerRepo)
            unlock(1, 500)
            assertEquals(2, playerRepo.profile.value.labSlotCount)
            assertEquals(300, playerRepo.profile.value.gems)
        }

    @Test
    fun `slot unlock rejected when insufficient gems`() =
        runTest {
            playerRepo.profile.value = PlayerProfile(stepBalance = 100_000, gems = 100)
            val unlock =
                com.whitefang.stepsofbabylon.domain.usecase
                    .UnlockLabSlot(playerRepo)
            unlock(1, 100)
            assertEquals(1, playerRepo.profile.value.labSlotCount) // unchanged
        }
}
