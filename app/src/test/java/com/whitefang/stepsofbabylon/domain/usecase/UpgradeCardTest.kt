package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpgradeCardTest {
    private lateinit var cardRepo: FakeCardRepository
    private lateinit var useCase: UpgradeCard

    @BeforeEach
    fun setup() {
        cardRepo = FakeCardRepository()
        useCase = UpgradeCard(cardRepo)
    }

    @Test
    fun `success upgrades when enough copies`() =
        runTest {
            // Common needs 3 copies
            val card = OwnedCard(1, CardType.IRON_SKIN, 1, false, copyCount = 5)
            cardRepo.cards.value = listOf(card)
            val result = useCase(card)
            assertTrue(result is UpgradeCard.Result.Upgraded)
            assertEquals(2, (result as UpgradeCard.Result.Upgraded).newLevel)
            // Verify copies decremented
            assertEquals(2, cardRepo.cards.value[0].copyCount)
            assertEquals(2, cardRepo.cards.value[0].level)
        }

    @Test
    fun `max level returns error`() =
        runTest {
            val card = OwnedCard(1, CardType.IRON_SKIN, 7, false, copyCount = 10)
            cardRepo.cards.value = listOf(card)
            val result = useCase(card)
            assertTrue(result is UpgradeCard.Result.MaxLevel)
        }

    @Test
    fun `insufficient copies returns error`() =
        runTest {
            // Rare needs 4 copies, only have 2
            val card = OwnedCard(1, CardType.VAMPIRIC_TOUCH, 1, false, copyCount = 2)
            cardRepo.cards.value = listOf(card)
            val result = useCase(card)
            assertTrue(result is UpgradeCard.Result.InsufficientCopies)
        }

    @Test
    fun `copies needed scales with rarity`() =
        runTest {
            val common = OwnedCard(1, CardType.IRON_SKIN, 1, false, copyCount = 3)
            val rare = OwnedCard(2, CardType.VAMPIRIC_TOUCH, 1, false, copyCount = 4)
            val epic = OwnedCard(3, CardType.GLASS_CANNON, 1, false, copyCount = 5)
            cardRepo.cards.value = listOf(common, rare, epic)

            assertTrue(useCase(common) is UpgradeCard.Result.Upgraded)
            assertTrue(useCase(rare) is UpgradeCard.Result.Upgraded)
            assertTrue(useCase(epic) is UpgradeCard.Result.Upgraded)

            // Common: 3-3=0, Rare: 4-4=0, Epic: 5-5=0
            assertEquals(0, cardRepo.cards.value[0].copyCount)
            assertEquals(0, cardRepo.cards.value[1].copyCount)
            assertEquals(0, cardRepo.cards.value[2].copyCount)
        }

    @Test
    fun `exact copies needed succeeds`() =
        runTest {
            // Epic needs exactly 5
            val card = OwnedCard(1, CardType.GLASS_CANNON, 1, false, copyCount = 5)
            cardRepo.cards.value = listOf(card)
            val result = useCase(card)
            assertTrue(result is UpgradeCard.Result.Upgraded)
        }
}
