package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMission
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.repository.MissionRepository
import javax.inject.Inject

class MissionRepositoryImpl @Inject constructor(
    private val dao: DailyMissionDao,
) : MissionRepository {

    override suspend fun getMissionsForDate(date: String): List<DailyMission> =
        dao.getByDateOnce(date).mapNotNull { it.toDomainOrNull() }

    override suspend fun generateForDate(date: String, missions: List<DailyMission>) =
        // Preserve the @Transaction batch + (date,missionType) unique-index + IGNORE guard (#127).
        dao.generateForDate(date, missions.map { it.toEntity() })

    override suspend fun markClaimed(id: Int): Int = dao.markClaimed(id)

    override suspend fun updateProgress(id: Int, progress: Int, completed: Boolean) =
        dao.updateProgress(id, progress, completed)

    // #227 F5: a stored missionType that no longer resolves to an enum member must not crash a
    // mission read — null-skip the row (drop it) rather than DailyMissionType.valueOf throwing.
    private fun DailyMissionEntity.toDomainOrNull(): DailyMission? {
        val type = DailyMissionType.entries.find { it.name == missionType } ?: return null
        return DailyMission(
            id = id, type = type, date = date, target = target, progress = progress,
            rewardGems = rewardGems, rewardPowerStones = rewardPowerStones,
            completed = completed, claimed = claimed,
        )
    }

    private fun DailyMission.toEntity(): DailyMissionEntity = DailyMissionEntity(
        date = date, missionType = type.name, target = target, progress = progress,
        rewardGems = rewardGems, rewardPowerStones = rewardPowerStones,
        completed = completed, claimed = claimed,
    )
}
