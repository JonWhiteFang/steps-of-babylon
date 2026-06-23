package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake for [CardRepository].
 *
 * @param linkedPlayer when supplied, [openCardPackAtomic] forwards the Gem deduct to this player
 *                     fake (mirroring the real cross-DAO `@Transaction`: the guarded
 *                     [FakePlayerRepository.spendGems] gates the card grant). When null, the deduct
 *                     is a no-op (only the card writes happen) — for tests that don't assert wallet.
 */
class FakeCardRepository(
    private val linkedPlayer: FakePlayerRepository? = null,
) : CardRepository {
    private var nextId = 1
    val cards = MutableStateFlow<List<OwnedCard>>(emptyList())

    /** Number of [openCardPackAtomic] calls — lets a test assert the atomic path is live. */
    var openCardPackAtomicCallCount: Int = 0
        private set

    override fun observeAllCards(): Flow<List<OwnedCard>> = cards

    override fun observeEquippedCards(): Flow<List<OwnedCard>> = cards.map { it.filter { c -> c.isEquipped } }

    override suspend fun hasCard(type: CardType): Boolean = cards.value.any { it.type == type }

    override suspend fun addCard(type: CardType): Long {
        val id = nextId++
        cards.update { it + OwnedCard(id, type, 1, false, copyCount = 1) }
        return id.toLong()
    }

    override suspend fun openCardPackAtomic(
        gemCost: Long,
        cardTypeNames: List<String>,
    ): List<Boolean>? {
        openCardPackAtomicCallCount++
        // Guarded deduct first — emulates spendGemsAtomic gating the grant inside the transaction.
        if (gemCost > 0L && linkedPlayer != null && !linkedPlayer.spendGems(gemCost)) return null
        return cardTypeNames.map { name ->
            val type = CardType.valueOf(name)
            if (cards.value.any { it.type == type }) {
                cards.update { list -> list.map { if (it.type == type) it.copy(copyCount = it.copyCount + 1) else it } }
                false
            } else {
                addCard(type)
                true
            }
        }
    }

    override suspend fun addCardOrIncrementCopy(type: CardType) {
        val existing = cards.value.find { it.type == type }
        if (existing != null) {
            cards.update { list -> list.map { if (it.type == type) it.copy(copyCount = it.copyCount + 1) else it } }
        } else {
            addCard(type)
        }
    }

    override suspend fun incrementCopyCount(type: CardType) {
        val existing = cards.value.find { it.type == type }
        if (existing != null) {
            cards.update { list -> list.map { if (it.type == type) it.copy(copyCount = it.copyCount + 1) else it } }
        } else {
            addCard(type)
        }
    }

    override suspend fun decrementCopiesAndLevelUp(
        id: Int,
        amount: Int,
    ): Boolean {
        val card = cards.value.find { it.id == id } ?: return false
        if (card.copyCount < amount) return false
        cards.update { list ->
            list.map { if (it.id == id) it.copy(copyCount = it.copyCount - amount, level = it.level + 1) else it }
        }
        return true
    }

    override suspend fun upgradeCard(
        id: Int,
        newLevel: Int,
    ) {
        cards.update { list -> list.map { if (it.id == id) it.copy(level = newLevel) else it } }
    }

    override suspend fun equipCard(id: Int) {
        cards.update { list -> list.map { if (it.id == id) it.copy(isEquipped = true) else it } }
    }

    override suspend fun unequipCard(id: Int) {
        cards.update { list -> list.map { if (it.id == id) it.copy(isEquipped = false) else it } }
    }

    override suspend fun deleteCard(id: Int) {
        cards.update { list -> list.filter { it.id != id } }
    }
}
