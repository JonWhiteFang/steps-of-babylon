package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.CardDao
import com.whitefang.stepsofbabylon.data.local.CardInventoryEntity
import com.whitefang.stepsofbabylon.domain.model.CardType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [CardRepositoryImpl] — R4-08 copy-based progression including
 * addCardOrIncrementCopy duplicate handling, decrementCopiesAndLevelUp atomic flow,
 * and equip/unequip/delete state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardRepositoryImplTest {

    @Test
    fun `observeAllCards maps entities to domain models`() = runTest {
        val dao = mock<CardDao>()
        val rows = listOf(
            CardInventoryEntity(id = 1, cardType = "IRON_SKIN", level = 3, copyCount = 2),
            CardInventoryEntity(id = 2, cardType = "SHARP_SHOOTER", level = 1, isEquipped = true),
        )
        whenever(dao.getAll()).thenReturn(MutableStateFlow(rows))
        val repo = CardRepositoryImpl(dao)

        val cards = repo.observeAllCards().first()

        assertEquals(2, cards.size)
        assertEquals(CardType.IRON_SKIN, cards[0].type)
        assertEquals(3, cards[0].level)
        assertEquals(2, cards[0].copyCount)
        assertEquals(CardType.SHARP_SHOOTER, cards[1].type)
        assertTrue(cards[1].isEquipped)
    }

    @Test
    fun `observeEquippedCards returns only equipped entities`() = runTest {
        val dao = mock<CardDao>()
        val equipped = listOf(
            CardInventoryEntity(id = 1, cardType = "IRON_SKIN", isEquipped = true),
        )
        whenever(dao.getEquipped()).thenReturn(MutableStateFlow(equipped))
        val repo = CardRepositoryImpl(dao)

        val cards = repo.observeEquippedCards().first()

        assertEquals(1, cards.size)
        assertEquals(CardType.IRON_SKIN, cards.first().type)
    }

    @Test
    fun `hasCard returns true when card exists`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getByType("IRON_SKIN")).thenReturn(
            CardInventoryEntity(cardType = "IRON_SKIN")
        )
        val repo = CardRepositoryImpl(dao)

        assertTrue(repo.hasCard(CardType.IRON_SKIN))
    }

    @Test
    fun `hasCard returns false when card does not exist`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getByType(any())).thenReturn(null)
        val repo = CardRepositoryImpl(dao)

        assertFalse(repo.hasCard(CardType.IRON_SKIN))
    }

    @Test
    fun `addCard inserts new entity`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.insert(any())).thenReturn(42L)
        val repo = CardRepositoryImpl(dao)

        val id = repo.addCard(CardType.SHARP_SHOOTER)

        assertEquals(42L, id)
        val captor = argumentCaptor<CardInventoryEntity>()
        verify(dao).insert(captor.capture())
        assertEquals("SHARP_SHOOTER", captor.firstValue.cardType)
    }

    @Test
    fun `R4-08 addCardOrIncrementCopy increments when card exists`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getByType("IRON_SKIN")).thenReturn(
            CardInventoryEntity(cardType = "IRON_SKIN", copyCount = 1)
        )
        val repo = CardRepositoryImpl(dao)

        repo.addCardOrIncrementCopy(CardType.IRON_SKIN)

        verify(dao).incrementCopyCount(eq("IRON_SKIN"), any())
        verify(dao, never()).insert(any())
    }

    @Test
    fun `R4-08 addCardOrIncrementCopy inserts when card does not exist`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getByType("SHARP_SHOOTER")).thenReturn(null)
        whenever(dao.insert(any<CardInventoryEntity>())).thenReturn(1L)
        val repo = CardRepositoryImpl(dao)

        repo.addCardOrIncrementCopy(CardType.SHARP_SHOOTER)

        verify(dao).insert(any<CardInventoryEntity>())
        verify(dao, never()).incrementCopyCount(any(), any())
    }

    @Test
    fun `R4-08 decrementCopiesAndLevelUp returns true when DAO returns positive rows`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.decrementCopiesAndLevelUp(1, 3)).thenReturn(1)
        val repo = CardRepositoryImpl(dao)

        assertTrue(repo.decrementCopiesAndLevelUp(id = 1, amount = 3))
    }

    @Test
    fun `R4-08 decrementCopiesAndLevelUp returns false when insufficient copies`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.decrementCopiesAndLevelUp(any(), any())).thenReturn(0)
        val repo = CardRepositoryImpl(dao)

        assertFalse(repo.decrementCopiesAndLevelUp(id = 1, amount = 5))
    }

    @Test
    fun `upgradeCard updates level on existing entity`() = runTest {
        val dao = mock<CardDao>()
        val existing = CardInventoryEntity(id = 1, cardType = "IRON_SKIN", level = 2)
        whenever(dao.getById(1)).thenReturn(existing)
        val repo = CardRepositoryImpl(dao)

        repo.upgradeCard(id = 1, newLevel = 3)

        val captor = argumentCaptor<CardInventoryEntity>()
        verify(dao).update(captor.capture())
        assertEquals(3, captor.firstValue.level)
    }

    @Test
    fun `upgradeCard is no-op when entity does not exist`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getById(any())).thenReturn(null)
        val repo = CardRepositoryImpl(dao)

        repo.upgradeCard(id = 99, newLevel = 5)

        verify(dao, never()).update(any())
    }

    @Test
    fun `equipCard flips isEquipped to true`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getById(1)).thenReturn(
            CardInventoryEntity(id = 1, cardType = "IRON_SKIN", isEquipped = false)
        )
        val repo = CardRepositoryImpl(dao)

        repo.equipCard(1)

        val captor = argumentCaptor<CardInventoryEntity>()
        verify(dao).update(captor.capture())
        assertTrue(captor.firstValue.isEquipped)
    }

    @Test
    fun `unequipCard flips isEquipped to false`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getById(1)).thenReturn(
            CardInventoryEntity(id = 1, cardType = "IRON_SKIN", isEquipped = true)
        )
        val repo = CardRepositoryImpl(dao)

        repo.unequipCard(1)

        val captor = argumentCaptor<CardInventoryEntity>()
        verify(dao).update(captor.capture())
        assertFalse(captor.firstValue.isEquipped)
    }

    @Test
    fun `deleteCard removes existing entity`() = runTest {
        val dao = mock<CardDao>()
        val existing = CardInventoryEntity(id = 1, cardType = "IRON_SKIN")
        whenever(dao.getById(1)).thenReturn(existing)
        val repo = CardRepositoryImpl(dao)

        repo.deleteCard(1)

        verify(dao).delete(existing)
    }

    @Test
    fun `deleteCard is no-op when entity does not exist`() = runTest {
        val dao = mock<CardDao>()
        whenever(dao.getById(any())).thenReturn(null)
        val repo = CardRepositoryImpl(dao)

        repo.deleteCard(99)

        verify(dao, never()).delete(any())
    }
}
