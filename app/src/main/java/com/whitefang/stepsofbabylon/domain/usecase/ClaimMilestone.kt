package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.MilestoneReward
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.first

/**
 * Result of a [ClaimMilestone] invocation.
 *
 * Introduced in C.4 to replace a `Boolean` return so the `MilestoneReward.Cosmetic`
 * branch can surface an unknown-id detection path instead of silently dropping. The
 * three currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin`,
 * `lapis_lazuli_skin`, `sandals_of_gilgamesh`) will all produce
 * [UnknownCosmetic] until their matching seed rows ship in C.2 PR 3+.
 *
 * Consumers (e.g. `MissionsViewModel`) should map each non-[Success] variant to a
 * user-visible message rather than treating the claim as silently no-op.
 */
sealed class ClaimMilestoneResult {

    /** Milestone claimed; rewards credited atomically via [MilestoneDao.claimMilestoneAtomic]. */
    data object Success : ClaimMilestoneResult()

    /** Player's lifetime step total is below the milestone's `requiredSteps` threshold. */
    data object InsufficientSteps : ClaimMilestoneResult()

    /**
     * Milestone was already claimed (on-disk row has `claimed = true`). Either a prior
     * successful claim in this process, or a persisted claim from a previous lifecycle.
     */
    data object AlreadyClaimed : ClaimMilestoneResult()

    /**
     * At least one [MilestoneReward.Cosmetic] in the milestone references an id that
     * does not exist in the cosmetic catalogue (i.e. no matching row in
     * `SEED_COSMETICS`). The claim is rejected — no gems / power stones are credited —
     * so the mismatch surfaces loudly instead of silently dropping the cosmetic while
     * crediting the other rewards. Resolution is content work (C.2 PR 3+).
     */
    data class UnknownCosmetic(val cosmeticId: String) : ClaimMilestoneResult()
}

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
 * Post-C.4: the return type is [ClaimMilestoneResult] (was `Boolean`). Before running
 * the atomic transaction the use case pre-flights every [MilestoneReward.Cosmetic] id
 * through [CosmeticRepository.idExists]; on miss it returns
 * [ClaimMilestoneResult.UnknownCosmetic] and credits nothing. Previously the cosmetic
 * rewards were silently dropped in `MilestoneReward.Cosmetic` branch of the credit loop
 * (see the pre-B.2 PR 4 history) \u2014 the 3 currently-mismatched milestone ids
 * (`garden_ziggurat_skin`, `lapis_lazuli_skin`, `sandals_of_gilgamesh`) stayed
 * un-issued without any observable error. The detection here makes the mismatch
 * visible. Resolution (renaming the ids to match seed rows, or adding seed rows that
 * match) is content work coupled to C.2 PR 3+.
 *
 * The step-threshold guard (`totalStepsEarned >= requiredSteps`) remains outside the
 * transaction: `totalStepsEarned` is monotonic, so a stale read can only fail-closed
 * (false-negative \u2192 the user retries). There is no correctness window here to close.
 */
class ClaimMilestone(
    private val milestoneDao: MilestoneDao,
    private val playerRepository: PlayerRepository,
    private val playerProfileDao: PlayerProfileDao,
    private val cosmeticRepository: CosmeticRepository,
) {
    suspend operator fun invoke(milestone: Milestone): ClaimMilestoneResult {
        val profile = playerRepository.observeProfile().first()
        if (profile.totalStepsEarned < milestone.requiredSteps) {
            return ClaimMilestoneResult.InsufficientSteps
        }

        // Pre-flight cosmetic-id check (C.4): verify every Cosmetic reward's id exists
        // in the catalogue before running the atomic credit. First unknown id wins \u2014 we
        // don't try to be clever and report all of them, since resolving one at a time
        // is the natural flow for C.2 PR 3+ content PRs.
        for (reward in milestone.rewards) {
            if (reward is MilestoneReward.Cosmetic && !cosmeticRepository.idExists(reward.id)) {
                return ClaimMilestoneResult.UnknownCosmetic(reward.id)
            }
        }

        val claimed = milestoneDao.claimMilestoneAtomic(
            milestoneId = milestone.name,
            gems = milestone.totalGems,
            powerStones = milestone.totalPowerStones,
            claimedAt = System.currentTimeMillis(),
            playerDao = playerProfileDao,
        )
        return if (claimed) ClaimMilestoneResult.Success else ClaimMilestoneResult.AlreadyClaimed
    }
}
