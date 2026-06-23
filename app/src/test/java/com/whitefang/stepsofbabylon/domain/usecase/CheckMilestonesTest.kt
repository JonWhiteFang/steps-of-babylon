package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckMilestonesTest {
    private lateinit var repo: FakeMilestoneRepository
    private lateinit var useCase: CheckMilestones

    @BeforeEach
    fun setup() {
        repo = FakeMilestoneRepository()
        useCase = CheckMilestones(repo)
    }

    @Test
    fun `returns empty when no milestones reached`() =
        runTest {
            val result = useCase(500)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `returns FIRST_STEPS when 1000 steps reached`() =
        runTest {
            val result = useCase(1_000)
            assertEquals(listOf(Milestone.FIRST_STEPS), result)
        }

    @Test
    fun `returns multiple milestones when threshold crossed`() =
        runTest {
            val result = useCase(100_000)
            assertEquals(3, result.size)
            assertTrue(result.contains(Milestone.FIRST_STEPS))
            assertTrue(result.contains(Milestone.MORNING_JOGGER))
            assertTrue(result.contains(Milestone.TRAIL_BLAZER))
        }

    @Test
    fun `excludes already claimed milestones`() =
        runTest {
            repo.upsert(MilestoneEntity(Milestone.FIRST_STEPS.name, claimed = true, claimedAt = 1L))
            val result = useCase(10_000)
            assertEquals(1, result.size)
            assertEquals(Milestone.MORNING_JOGGER, result[0])
        }
}
