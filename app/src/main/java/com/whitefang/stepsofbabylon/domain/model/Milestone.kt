package com.whitefang.stepsofbabylon.domain.model

enum class Milestone(
    val displayName: String,
    val requiredSteps: Long,
    val rewards: List<MilestoneReward>,
) {
    FIRST_STEPS(
        "First Steps",
        1_000,
        listOf(
            MilestoneReward.Gems(60), // 10 Gems + 50 Gems (Tutorial Card Pack equivalent)
        ),
    ),
    MORNING_JOGGER(
        "Morning Jogger",
        10_000,
        listOf(
            MilestoneReward.Gems(25),
        ),
    ),
    TRAIL_BLAZER(
        "Trail Blazer",
        100_000,
        listOf(
            MilestoneReward.Gems(200), // 50 Gems + 150 Gems (Rare Card Pack equivalent)
        ),
    ),
    MARATHON_WALKER(
        "Marathon Walker",
        500_000,
        listOf(
            MilestoneReward.Gems(600), // 100 Gems + 500 Gems (Epic Card Pack equivalent)
            MilestoneReward.Cosmetic("garden_ziggurat_skin", "Garden Ziggurat Skin"),
        ),
    ),
    IRON_SOLES(
        "Iron Soles",
        1_000_000,
        listOf(
            MilestoneReward.Gems(200),
            MilestoneReward.PowerStones(50),
            MilestoneReward.Cosmetic("lapis_lazuli_skin", "Lapis Lazuli Ziggurat Skin"),
        ),
    ),
    GLOBE_TROTTER(
        "Globe Trotter",
        5_000_000,
        listOf(
            MilestoneReward.Gems(500),
            MilestoneReward.Cosmetic("sandals_of_gilgamesh", "Sandals of Gilgamesh"),
        ),
    ),
    ;

    val totalGems: Long get() = rewards.filterIsInstance<MilestoneReward.Gems>().sumOf { it.amount }
    val totalPowerStones: Long get() = rewards.filterIsInstance<MilestoneReward.PowerStones>().sumOf { it.amount }
}
