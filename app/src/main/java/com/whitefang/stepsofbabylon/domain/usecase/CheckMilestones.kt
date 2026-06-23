package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.repository.MilestoneRepository

class CheckMilestones(
    private val milestoneRepository: MilestoneRepository,
) {
    suspend operator fun invoke(totalStepsEarned: Long): List<Milestone> {
        val claimed = milestoneRepository.getClaimedMilestoneIds().toSet()
        return Milestone.entries.filter { it.requiredSteps <= totalStepsEarned && it.name !in claimed }
    }
}
