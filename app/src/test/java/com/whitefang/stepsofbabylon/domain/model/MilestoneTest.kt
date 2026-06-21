package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MilestoneTest {

    @Test
    fun `all 6 milestones have correct step thresholds`() {
        val expected = mapOf(
            Milestone.FIRST_STEPS to 1_000L,
            Milestone.MORNING_JOGGER to 10_000L,
            Milestone.TRAIL_BLAZER to 100_000L,
            Milestone.MARATHON_WALKER to 500_000L,
            Milestone.IRON_SOLES to 1_000_000L,
            Milestone.GLOBE_TROTTER to 5_000_000L,
        )
        assertEquals(6, Milestone.entries.size)
        expected.forEach { (milestone, steps) -> assertEquals(steps, milestone.requiredSteps) }
    }

    @Test
    fun `milestones are sorted by requiredSteps ascending`() {
        val steps = Milestone.entries.map { it.requiredSteps }
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun `FIRST_STEPS rewards 60 Gems total (10 + 50 card pack equivalent)`() {
        assertEquals(60, Milestone.FIRST_STEPS.totalGems)
        assertEquals(0, Milestone.FIRST_STEPS.totalPowerStones)
    }

    @Test
    fun `IRON_SOLES rewards 200 Gems + 50 Power Stones + cosmetic`() {
        assertEquals(200, Milestone.IRON_SOLES.totalGems)
        assertEquals(50, Milestone.IRON_SOLES.totalPowerStones)
        assertTrue(Milestone.IRON_SOLES.rewards.any { it is MilestoneReward.Cosmetic })
    }

    @Test
    fun `MARATHON_WALKER includes cosmetic reward`() {
        val cosmetic = Milestone.MARATHON_WALKER.rewards.filterIsInstance<MilestoneReward.Cosmetic>()
        assertEquals(1, cosmetic.size)
        assertEquals("garden_ziggurat_skin", cosmetic[0].id)
    }
}
