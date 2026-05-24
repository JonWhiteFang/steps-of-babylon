package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.repository.CardRepository

class UpgradeCard(
    private val cardRepository: CardRepository,
) {
    sealed class Result {
        data class Upgraded(val newLevel: Int) : Result()
        data object MaxLevel : Result()
        data object InsufficientCopies : Result()
    }

    suspend operator fun invoke(card: OwnedCard): Result {
        if (card.level >= card.type.maxLevel) return Result.MaxLevel
        val copiesNeeded = card.type.rarity.copiesPerLevel
        if (card.copyCount < copiesNeeded) return Result.InsufficientCopies
        val success = cardRepository.decrementCopiesAndLevelUp(card.id, copiesNeeded)
        return if (success) Result.Upgraded(card.level + 1) else Result.InsufficientCopies
    }
}
