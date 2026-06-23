package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DailyMissionTypeTest {
    @Test
    fun `all 6 mission types exist`() {
        assertEquals(6, DailyMissionType.entries.size)
    }

    @Test
    fun `2 walking missions`() {
        assertEquals(2, DailyMissionType.byCategory(MissionCategory.WALKING).size)
    }

    @Test
    fun `2 battle missions`() {
        assertEquals(2, DailyMissionType.byCategory(MissionCategory.BATTLE).size)
    }

    @Test
    fun `2 upgrade missions`() {
        assertEquals(2, DailyMissionType.byCategory(MissionCategory.UPGRADE).size)
    }

    @Test
    fun `WALK_5000 rewards 5 Gems 0 PS`() {
        val m = DailyMissionType.WALK_5000
        assertEquals(5_000, m.target)
        assertEquals(5, m.rewardGems)
        assertEquals(0, m.rewardPowerStones)
    }

    @Test
    fun `WALK_12000 rewards 10 Gems 2 PS`() {
        val m = DailyMissionType.WALK_12000
        assertEquals(12_000, m.target)
        assertEquals(10, m.rewardGems)
        assertEquals(2, m.rewardPowerStones)
    }

    @Test
    fun `COMPLETE_RESEARCH target is 1`() {
        assertEquals(1, DailyMissionType.COMPLETE_RESEARCH.target)
        assertEquals(5, DailyMissionType.COMPLETE_RESEARCH.rewardGems)
    }
}
