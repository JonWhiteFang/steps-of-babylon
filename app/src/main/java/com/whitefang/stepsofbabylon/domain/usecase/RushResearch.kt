package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlin.math.ceil
import kotlin.math.max

class RushResearch(
    private val labRepository: LabRepository,
    private val playerRepository: PlayerRepository,
) {
    sealed class Result {
        data class Rushed(
            val gemCost: Long,
            val newLevel: Int,
        ) : Result()

        data object InsufficientGems : Result()
    }

    suspend operator fun invoke(
        type: ResearchType,
        activeResearch: ActiveResearch,
        wallet: PlayerWallet,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val cost = calculateRushCost(activeResearch.startedAt, activeResearch.completesAt, now)
        if (wallet.gems < cost) return Result.InsufficientGems

        val level = labRepository.getResearchLevel(type)
        // #122: only complete the research when the guarded deduct actually charged the Gems.
        if (!playerRepository.spendGems(cost)) return Result.InsufficientGems
        labRepository.completeResearch(type)
        return Result.Rushed(cost, newLevel = level + 1)
    }

    companion object {
        fun calculateRushCost(
            startedAt: Long,
            completesAt: Long,
            now: Long,
        ): Long {
            val totalDuration = completesAt - startedAt
            if (totalDuration <= 0) return 50L
            val remaining = max(0L, completesAt - now)
            val fraction = remaining.toDouble() / totalDuration.toDouble()
            return ceil(50.0 + fraction * 150.0).toLong()
        }
    }
}
