package com.whitefang.stepsofbabylon.domain.model

/**
 * A single generated daily mission, the domain view of a `daily_mission` row. Introduced by the
 * #227 dependency-rule fix so the mission use cases depend on a domain model instead of the Room
 * entity. [type] is the resolved [DailyMissionType]; the repository maps to/from the persisted
 * `missionType` String and drops rows whose stored type no longer resolves to an enum member.
 */
data class DailyMission(
    val id: Int = 0, // autogen PK; 0 when constructing a not-yet-persisted mission to generate
    val type: DailyMissionType,
    val date: String,
    val target: Int,
    val progress: Int = 0,
    val rewardGems: Int = 0,
    val rewardPowerStones: Int = 0,
    val completed: Boolean = false,
    val claimed: Boolean = false,
)
