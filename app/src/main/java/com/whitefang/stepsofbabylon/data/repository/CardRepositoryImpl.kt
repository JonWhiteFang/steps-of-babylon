package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.CardDao
import com.whitefang.stepsofbabylon.data.local.CardInventoryEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CardRepositoryImpl @Inject constructor(
    private val dao: CardDao,
    private val playerDao: PlayerProfileDao,
) : CardRepository {

    override fun observeAllCards(): Flow<List<OwnedCard>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun observeEquippedCards(): Flow<List<OwnedCard>> =
        dao.getEquipped().map { list -> list.map { it.toDomain() } }

    override suspend fun hasCard(type: CardType): Boolean =
        dao.getByType(type.name) != null

    override suspend fun addCard(type: CardType): Long =
        dao.insert(CardInventoryEntity(cardType = type.name))

    override suspend fun openCardPackAtomic(gemCost: Long, cardTypeNames: List<String>): List<Boolean>? =
        dao.openCardPackAtomic(gemCost, cardTypeNames, playerDao)

    override suspend fun addCardOrIncrementCopy(type: CardType) {
        val existing = dao.getByType(type.name)
        if (existing != null) {
            dao.incrementCopyCount(type.name)
        } else {
            dao.insert(CardInventoryEntity(cardType = type.name))
        }
    }

    override suspend fun incrementCopyCount(type: CardType) {
        val existing = dao.getByType(type.name)
        if (existing != null) {
            dao.incrementCopyCount(type.name)
        } else {
            dao.insert(CardInventoryEntity(cardType = type.name))
        }
    }

    override suspend fun decrementCopiesAndLevelUp(id: Int, amount: Int): Boolean =
        dao.decrementCopiesAndLevelUp(id, amount) > 0

    override suspend fun upgradeCard(id: Int, newLevel: Int) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(level = newLevel))
    }

    override suspend fun equipCard(id: Int) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(isEquipped = true))
    }

    override suspend fun unequipCard(id: Int) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(isEquipped = false))
    }

    override suspend fun deleteCard(id: Int) {
        val entity = dao.getById(id) ?: return
        dao.delete(entity)
    }

    private fun CardInventoryEntity.toDomain() = OwnedCard(
        id = id,
        type = CardType.valueOf(cardType),
        level = level,
        isEquipped = isEquipped,
        copyCount = copyCount,
    )
}
