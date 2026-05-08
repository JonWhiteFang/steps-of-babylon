package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.first

/**
 * Credits a walking milestone's rewards to the player and marks it claimed.
 *
 * Post-RO-02 (B.2 PR 4): the mark-claimed + reward credit chain runs inside a single
 * Room `@Transaction` via [MilestoneDao.claimMilestoneAtomic]. This closes the
 * partial-failure gap (a crash between the credit and the mark-claimed write could
 * previously leave the player credited but not marked, enabling double-credit on retry)
 * and the double-claim race (two concurrent claims could both read `claimed = false`
 * and both credit).
 *
 * The step-threshold guard (`totalStepsEarned >= requiredSteps`) remains outside the
 * transaction: `totalStepsEarned` is monotonic, so a stale read can only fail-closed
 * (false-negative → the user retries). There is no correctness window here to close.
 *
 * [MilestoneReward.Cosmetic] remains a no-op pending the cosmetic-rendering pipeline
 * (tracked by Phase C.2 in `devdocs/evolution/implementation_roadmap.md`).
 */
class ClaimMilestone(
    private val milestoneDao: MilestoneDao,
    private val playerRepository: PlayerRepository,
    private val playerProfileDao: PlayerProfileDao,
) {
    suspend operator fun invoke(milestone: Milestone): Boolean {
        val profile = playerRepository.observeProfile().first()
        if (profile.totalStepsEarned < milestone.requiredSteps) return false

        return milestoneDao.claimMilestoneAtomic(
            milestoneId = milestone.name,
            gems = milestone.totalGems,
            powerStones = milestone.totalPowerStones,
            claimedAt = System.currentTimeMillis(),
            playerDao = playerProfileDao,
        )
    }
}
