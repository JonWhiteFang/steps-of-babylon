package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.first

class StartResearch(
    private val labRepository: LabRepository,
    private val playerRepository: PlayerRepository,
    private val calculateCost: CalculateResearchCost = CalculateResearchCost(),
    private val calculateTime: CalculateResearchTime = CalculateResearchTime(),
) {
    sealed class Result {
        data class Success(
            val completesAt: Long,
        ) : Result()

        data object AlreadyResearching : Result()

        data object NoSlotAvailable : Result()

        data object MaxLevelReached : Result()

        data object InsufficientSteps : Result()
    }

    suspend operator fun invoke(
        type: ResearchType,
        wallet: PlayerWallet,
        labSlotCount: Int,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val level = labRepository.getResearchLevel(type)
        if (level >= type.maxLevel) return Result.MaxLevelReached

        val activeList = labRepository.observeActiveResearch().first()
        if (activeList.any { it.type == type }) return Result.AlreadyResearching
        if (activeList.size >= labSlotCount) return Result.NoSlotAvailable

        val cost = calculateCost(type, level)
        // #122: gate on the atomic guarded deduct, not the stale wallet snapshot. The snapshot
        // pre-check stays as a cheap fast-fail, but the authoritative decision is the guarded
        // SQL deduct — if a concurrent spend drained the balance after the snapshot, this
        // returns InsufficientSteps and no research starts (previously the unguarded
        // spendSteps clamped to 0 and started the research for free).
        if (wallet.stepBalance < cost) return Result.InsufficientSteps
        if (!playerRepository.spendStepsIfSufficient(cost)) return Result.InsufficientSteps

        val timeHours = calculateTime(type, level)
        val completesAt = now + (timeHours * 3_600_000).toLong()
        labRepository.startResearch(type, completesAt, startedAt = now)
        return Result.Success(completesAt)
    }
}
