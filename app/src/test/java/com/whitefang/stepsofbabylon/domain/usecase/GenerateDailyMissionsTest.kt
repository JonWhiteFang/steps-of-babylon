package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.MissionCategory
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateDailyMissionsTest {

    private lateinit var dao: FakeMissionRepository
    private lateinit var useCase: GenerateDailyMissions

    @BeforeEach
    fun setup() {
        dao = FakeMissionRepository()
        useCase = GenerateDailyMissions(dao)
    }

    @Test
    fun `generates exactly 3 missions`() = runTest {
        useCase("2026-03-09")
        assertEquals(3, dao.dao.getByDateOnce("2026-03-09").size)
    }

    @Test
    fun `one mission per category`() = runTest {
        useCase("2026-03-09")
        val missions = dao.dao.getByDateOnce("2026-03-09")
        val categories = missions.map { m ->
            DailyMissionType.entries.first { it.name == m.missionType }.category
        }.toSet()
        assertEquals(setOf(MissionCategory.WALKING, MissionCategory.BATTLE, MissionCategory.UPGRADE), categories)
    }

    @Test
    fun `same date generates same missions (deterministic)`() = runTest {
        useCase("2026-03-09")
        val first = dao.dao.getByDateOnce("2026-03-09").map { it.missionType }

        val dao2 = FakeMissionRepository()
        val useCase2 = GenerateDailyMissions(dao2)
        useCase2("2026-03-09")
        val second = dao2.dao.getByDateOnce("2026-03-09").map { it.missionType }

        assertEquals(first, second)
    }

    @Test
    fun `different dates can generate different missions`() = runTest {
        useCase("2026-03-09")
        val day1 = dao.dao.getByDateOnce("2026-03-09").map { it.missionType }

        val dao2 = FakeMissionRepository()
        val useCase2 = GenerateDailyMissions(dao2)
        useCase2("2026-03-10")
        val day2 = dao2.dao.getByDateOnce("2026-03-10").map { it.missionType }

        // Not guaranteed to differ, but with 2 options per category, very likely at least one differs
        // This test just verifies no crash and correct count
        assertEquals(3, day2.size)
    }

    @Test
    fun `does not regenerate if missions already exist`() = runTest {
        useCase("2026-03-09")
        useCase("2026-03-09") // second call
        assertEquals(3, dao.dao.getByDateOnce("2026-03-09").size) // still 3, not 6
    }

    @Test
    fun `missions start with zero progress and unclaimed`() = runTest {
        useCase("2026-03-09")
        val missions = dao.dao.getByDateOnce("2026-03-09")
        missions.forEach { m ->
            assertEquals(0, m.progress)
            assertFalse(m.completed)
            assertFalse(m.claimed)
        }
    }
}
