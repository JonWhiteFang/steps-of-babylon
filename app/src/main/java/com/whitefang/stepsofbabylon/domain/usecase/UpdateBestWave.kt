package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.first

class UpdateBestWave(
    private val playerRepository: PlayerRepository,
) {
    data class Result(
        val isNewRecord: Boolean,
        val previousBest: Int,
    )

    suspend operator fun invoke(
        tier: Int,
        waveReached: Int,
    ): Result {
        val profile = playerRepository.observeProfile().first()
        val previousBest = profile.bestWavePerTier[tier] ?: 0
        val isNew = waveReached > previousBest
        if (isNew) playerRepository.updateBestWave(tier, waveReached)
        return Result(isNewRecord = isNew, previousBest = previousBest)
    }
}
