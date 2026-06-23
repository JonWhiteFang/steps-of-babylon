package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository

class ClaimSupplyDrop(
    private val encounterRepository: WalkingEncounterRepository,
    private val playerRepository: PlayerRepository,
    private val cardRepository: CardRepository,
) {
    sealed class Result {
        data object Success : Result()

        data object AlreadyClaimed : Result()
    }

    suspend operator fun invoke(drop: SupplyDrop): Result {
        if (drop.claimed) return Result.AlreadyClaimed

        // #122: mark-first via the atomic guarded claim. Only the call that actually transitions
        // the drop unclaimed → claimed (rows-affected == 1) proceeds to credit the reward; a
        // concurrent double-tap loses the race and returns AlreadyClaimed, so the reward is
        // credited exactly once. Pre-fix the credit ran BEFORE an unconditional mark, so two
        // taps both read claimed == false and both credited.
        if (!encounterRepository.claimDrop(drop.id)) return Result.AlreadyClaimed

        when (drop.reward) {
            SupplyDropReward.STEPS -> {
                playerRepository.addSteps(drop.rewardAmount.toLong())
            }

            SupplyDropReward.GEMS -> {
                playerRepository.addGems(drop.rewardAmount.toLong())
            }

            SupplyDropReward.POWER_STONES -> {
                playerRepository.addPowerStones(drop.rewardAmount.toLong())
            }

            SupplyDropReward.CARD_COPY -> {
                // Award 1 copy of a random card type (seeded by rewardAmount as index)
                val cardType = CardType.entries[drop.rewardAmount % CardType.entries.size]
                cardRepository.addCardOrIncrementCopy(cardType)
            }
        }
        return Result.Success
    }
}
