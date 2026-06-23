package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckResearchCompletionTest {
    private lateinit var labRepo: FakeLabRepository
    private lateinit var useCase: CheckResearchCompletion

    @BeforeEach
    fun setup() {
        labRepo = FakeLabRepository()
        useCase = CheckResearchCompletion(labRepo)
    }

    @Test
    fun `completes expired research`() =
        runTest {
            labRepo.active.value =
                listOf(
                    ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 1000, 5000),
                    ActiveResearch(ResearchType.HEALTH_RESEARCH, 0, 1000, 3000),
                )
            val completed = useCase(now = 6000)
            assertEquals(2, completed.size)
            assertTrue(labRepo.active.value.isEmpty())
        }

    @Test
    fun `skips not-ready research`() =
        runTest {
            labRepo.active.value =
                listOf(
                    ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 1000, 5000),
                    ActiveResearch(ResearchType.HEALTH_RESEARCH, 0, 1000, 10000),
                )
            val completed = useCase(now = 6000)
            assertEquals(1, completed.size)
            assertEquals(ResearchType.DAMAGE_RESEARCH, completed[0])
            assertEquals(1, labRepo.active.value.size)
        }

    @Test
    fun `handles empty list`() =
        runTest {
            val completed = useCase(now = 6000)
            assertTrue(completed.isEmpty())
        }

    @Test
    fun `R211 in-session forward jump does not complete research when trusted-now is below completesAt`() =
        runTest {
            labRepo.active.value =
                listOf(
                    ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 1000, 100_000),
                )
            // Raw wall-clock jumped to 200_000 (> completesAt) but the TRUSTED now (capped by monotonic
            // elapsed) is only 50_000 — research must NOT complete.
            val completed = useCase(now = 50_000)
            assertTrue(completed.isEmpty())
            assertEquals(1, labRepo.active.value.size)
        }
}
