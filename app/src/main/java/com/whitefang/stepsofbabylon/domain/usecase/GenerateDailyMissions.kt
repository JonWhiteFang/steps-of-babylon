package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.DailyMission
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.MissionCategory
import com.whitefang.stepsofbabylon.domain.repository.MissionRepository
import kotlin.random.Random

class GenerateDailyMissions(
    private val missionRepository: MissionRepository,
) {
    suspend operator fun invoke(todayDate: String) {
        // #127: build the deterministic daily set, then hand it to the repository's generateForDate.
        // The (date, missionType) unique index + onConflict=IGNORE is what makes this safe against a
        // concurrent re-generation (Home + Missions VM inits race): a raced duplicate batch is
        // silently dropped at the DB. generateForDate's transaction just batches the check+inserts.
        val rng = Random(todayDate.hashCode())
        val missions =
            MissionCategory.entries.map { category ->
                val candidates = DailyMissionType.byCategory(category)
                val picked = candidates[rng.nextInt(candidates.size)]
                DailyMission(
                    type = picked,
                    date = todayDate,
                    target = picked.target,
                    rewardGems = picked.rewardGems,
                    rewardPowerStones = picked.rewardPowerStones,
                )
            }
        missionRepository.generateForDate(todayDate, missions)
    }
}
