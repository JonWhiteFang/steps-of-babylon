package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardLoadout
import com.whitefang.stepsofbabylon.domain.repository.CardRepository

class ManageCardLoadout(
    private val cardRepository: CardRepository,
) {
    sealed class Result {
        data object Success : Result()

        data object LoadoutFull : Result()
    }

    suspend fun equip(
        cardId: Int,
        equippedCount: Int,
    ): Result {
        if (equippedCount >= CardLoadout.MAX_SIZE) return Result.LoadoutFull
        cardRepository.equipCard(cardId)
        return Result.Success
    }

    suspend fun unequip(cardId: Int): Result {
        cardRepository.unequipCard(cardId)
        return Result.Success
    }
}
