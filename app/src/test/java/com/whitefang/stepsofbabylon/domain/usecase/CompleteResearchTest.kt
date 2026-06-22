package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CompleteResearchTest {

    private lateinit var labRepo: FakeLabRepository
    private lateinit var useCase: CompleteResearch

    @BeforeEach
    fun setup() {
        labRepo = FakeLabRepository()
        useCase = CompleteResearch(labRepo)
    }

    @Test
    fun `completes when timer elapsed`() = runTest {
        labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 1000, 5000))
        val result = useCase(ResearchType.DAMAGE_RESEARCH, completesAt = 5000, now = 6000)
        assertTrue(result is CompleteResearch.Result.Completed)
        assertEquals(1, (result as CompleteResearch.Result.Completed).newLevel)
        assertTrue(labRepo.active.value.isEmpty())
    }

    @Test
    fun `not ready when timer not elapsed`() = runTest {
        labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 1000, 5000))
        val result = useCase(ResearchType.DAMAGE_RESEARCH, completesAt = 5000, now = 3000)
        assertTrue(result is CompleteResearch.Result.NotReady)
    }

    @Test
    fun `not active returns error`() = runTest {
        val result = useCase(ResearchType.DAMAGE_RESEARCH, completesAt = 5000, now = 6000)
        assertTrue(result is CompleteResearch.Result.NotActive)
    }

    @Test
    fun `R211 forward jump with trusted-now below completesAt returns NotReady`() = runTest {
        labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 0, 100_000))
        val result = useCase(ResearchType.DAMAGE_RESEARCH, completesAt = 100_000, now = 50_000)
        assertEquals(CompleteResearch.Result.NotReady, result)
    }
}
