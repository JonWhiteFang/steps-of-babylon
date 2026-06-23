package com.whitefang.stepsofbabylon.domain.model

sealed class MilestoneReward {
    data class Gems(
        val amount: Long,
    ) : MilestoneReward()

    data class PowerStones(
        val amount: Long,
    ) : MilestoneReward()

    data class Cosmetic(
        val id: String,
        val name: String,
    ) : MilestoneReward()
}
