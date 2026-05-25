package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun observeAllCards(): Flow<List<OwnedCard>>
    fun observeEquippedCards(): Flow<List<OwnedCard>>
    suspend fun hasCard(type: CardType): Boolean
    suspend fun addCard(type: CardType): Long
    suspend fun addCardOrIncrementCopy(type: CardType)
    suspend fun incrementCopyCount(type: CardType)
    suspend fun decrementCopiesAndLevelUp(id: Int, amount: Int): Boolean
    suspend fun upgradeCard(id: Int, newLevel: Int)
    suspend fun equipCard(id: Int)
    suspend fun unequipCard(id: Int)
    suspend fun deleteCard(id: Int)
}
