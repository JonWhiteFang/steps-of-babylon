package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository

class UnlockLabSlot(
    private val playerRepository: PlayerRepository,
) {
    sealed class Result {
        data class Unlocked(val newSlotCount: Int) : Result()
        data object MaxSlotsReached : Result()
        data object InsufficientGems : Result()
    }

    suspend operator fun invoke(currentSlotCount: Int, gems: Long): Result {
        if (currentSlotCount >= MAX_SLOTS) return Result.MaxSlotsReached
        if (gems < SLOT_COST_GEMS) return Result.InsufficientGems

        // #122: only increment the slot count when the guarded deduct actually charged the Gems.
        if (!playerRepository.spendGems(SLOT_COST_GEMS)) return Result.InsufficientGems
        val newCount = currentSlotCount + 1
        playerRepository.updateLabSlotCount(newCount)
        return Result.Unlocked(newCount)
    }

    companion object {
        const val MAX_SLOTS = 4
        const val SLOT_COST_GEMS = 200L
    }
}
