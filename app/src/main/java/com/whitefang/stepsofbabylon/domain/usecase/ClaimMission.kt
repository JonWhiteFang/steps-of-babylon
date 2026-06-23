package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.repository.MissionRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository

/**
 * Result of a [ClaimMission] invocation (#122).
 */
sealed class ClaimMissionResult {
    /** Mission claimed; Gems / Power Stones credited. */
    data object Success : ClaimMissionResult()

    /** Mission not found for [id], not completed, or already claimed — nothing credited. */
    data object NotClaimable : ClaimMissionResult()
}

/**
 * Claims a completed daily mission and credits its Gems / Power Stones reward (#122, audit #10).
 *
 * Extracted from `MissionsViewModel.claimMission`, which previously credited the reward and THEN
 * called an unconditional `markClaimed` UPDATE (no `AND claimed = 0`). Two rapid taps both read
 * `claimed = false` (the Room Flow had not re-emitted yet) and both credited — a double-credit of
 * premium currency.
 *
 * The fix mirrors [ClaimMilestone] / the established atomic-claim pattern: **mark first** via the
 * guarded [DailyMissionDao.markClaimed] (`... AND claimed = 0` returning rows-affected), and credit
 * the reward only when that returns `1`. The losing tap sees `0` rows and returns [NotClaimable].
 *
 * (Room does not let an `@Query` UPDATE and the cross-table wallet writes share one statement, so
 * the credit is a follow-up call rather than a single `@Transaction`. The mark-first ordering still
 * guarantees credit-exactly-once: only one caller can flip `claimed`, and only that caller credits.
 * The residual partial-failure window — a crash between the mark and the credit — drops a reward
 * rather than duplicating it, which is the safe direction for the player-economy invariant.)
 */
class ClaimMission(
    private val missionRepository: MissionRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend operator fun invoke(
        id: Int,
        date: String,
    ): ClaimMissionResult {
        val mission =
            missionRepository
                .getMissionsForDate(date)
                .find { it.id == id && it.completed && !it.claimed }
                ?: return ClaimMissionResult.NotClaimable

        // Mark-first: only the call that actually flips claimed (rows == 1) credits the reward.
        if (missionRepository.markClaimed(id) != 1) return ClaimMissionResult.NotClaimable

        if (mission.rewardGems > 0) playerRepository.addGems(mission.rewardGems.toLong())
        if (mission.rewardPowerStones > 0) playerRepository.addPowerStones(mission.rewardPowerStones.toLong())
        return ClaimMissionResult.Success
    }
}
