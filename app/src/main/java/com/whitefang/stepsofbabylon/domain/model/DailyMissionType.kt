package com.whitefang.stepsofbabylon.domain.model

enum class MissionCategory { WALKING, BATTLE, UPGRADE }

enum class DailyMissionType(
    val category: MissionCategory,
    val description: String,
    val target: Int,
    val rewardGems: Int,
    val rewardPowerStones: Int,
) {
    WALK_5000(MissionCategory.WALKING, "Walk 5,000 steps", 5_000, 5, 0),
    WALK_12000(MissionCategory.WALKING, "Walk 12,000 steps", 12_000, 10, 2),
    REACH_WAVE_30(MissionCategory.BATTLE, "Reach Wave 30", 30, 3, 0),
    KILL_500_ENEMIES(MissionCategory.BATTLE, "Kill 500 enemies", 500, 5, 0),
    SPEND_5000_WORKSHOP(MissionCategory.UPGRADE, "Spend 5,000 Steps on Workshop", 5_000, 2, 0),
    COMPLETE_RESEARCH(MissionCategory.UPGRADE, "Complete a Lab research", 1, 5, 0),
    ;

    companion object {
        fun byCategory(category: MissionCategory): List<DailyMissionType> = entries.filter { it.category == category }
    }
}
