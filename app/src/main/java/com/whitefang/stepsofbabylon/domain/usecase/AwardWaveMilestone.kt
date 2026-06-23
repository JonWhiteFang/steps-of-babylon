package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository

class AwardWaveMilestone(
    private val playerRepository: PlayerRepository,
) {
    suspend operator fun invoke(newBestWave: Int): Int {
        val ps =
            when {
                newBestWave % 25 == 0 -> 5
                newBestWave % 10 == 0 -> 2
                else -> 1
            }
        playerRepository.addPowerStones(ps.toLong())
        return ps
    }
}
