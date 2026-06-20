package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMission
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.repository.MissionRepository

/**
 * In-memory fake for [MissionRepository] (#227). Delegates to a wrapped [FakeDailyMissionDao] so the
 * `(date, missionType)` dedup (IGNORE) + guarded `markClaimed` + autogen-id behaviour are reused.
 * Maps entity↔domain the same way the real impl does (null-skipping unknown `missionType`).
 */
class FakeMissionRepository(
    val dao: FakeDailyMissionDao = FakeDailyMissionDao(),
) : MissionRepository {

    override suspend fun getMissionsForDate(date: String): List<DailyMission> =
        dao.getByDateOnce(date).mapNotNull { it.toDomainOrNull() }

    override suspend fun generateForDate(date: String, missions: List<DailyMission>) {
        if (dao.getByDateOnce(date).isNotEmpty()) return
        missions.forEach { dao.insert(it.toEntity()) }
    }

    override suspend fun markClaimed(id: Int): Int = dao.markClaimed(id)

    override suspend fun updateProgress(id: Int, progress: Int, completed: Boolean) =
        dao.updateProgress(id, progress, completed)

    private fun DailyMissionEntity.toDomainOrNull(): DailyMission? {
        val type = DailyMissionType.entries.find { it.name == missionType } ?: return null
        return DailyMission(
            id = id, type = type, date = date, target = target, progress = progress,
            rewardGems = rewardGems, rewardPowerStones = rewardPowerStones,
            completed = completed, claimed = claimed,
        )
    }

    private fun DailyMission.toEntity() = DailyMissionEntity(
        date = date, missionType = type.name, target = target, progress = progress,
        rewardGems = rewardGems, rewardPowerStones = rewardPowerStones,
        completed = completed, claimed = claimed,
    )
}
