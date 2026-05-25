package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OpenCardPackTest {

    private lateinit var cardRepo: FakeCardRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: OpenCardPack

    @BeforeEach
    fun setup() {
        cardRepo = FakeCardRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 1000))
        useCase = OpenCardPack(cardRepo, playerRepo, Random(42))
    }

    @Test
    fun `insufficient gems returns error`() = runTest {
        playerRepo.profile.value = PlayerProfile(gems = 10)
        val result = useCase(PackTier.COMMON, 10)
        assertTrue(result is OpenCardPack.Result.InsufficientGems)
    }

    @Test
    fun `deducts gems on success`() = runTest {
        val result = useCase(PackTier.COMMON, 1000)
        assertTrue(result is OpenCardPack.Result.Opened)
        assertEquals(950L, playerRepo.profile.value.gems)
    }

    @Test
    fun `new cards are added to repository`() = runTest {
        val result = useCase(PackTier.COMMON, 1000)
        assertTrue(result is OpenCardPack.Result.Opened)
        val opened = (result as OpenCardPack.Result.Opened).cards
        assertTrue(opened.any { it.isNew })
        assertTrue(cardRepo.cards.value.isNotEmpty())
    }

    @Test
    fun `duplicate cards increment copy count`() = runTest {
        // Pre-populate repo with all card types
        CardType.entries.forEach { cardRepo.addCard(it) }
        val result = useCase(PackTier.COMMON, 1000)
        assertTrue(result is OpenCardPack.Result.Opened)
        val opened = (result as OpenCardPack.Result.Opened).cards
        assertTrue(opened.all { !it.isNew })
    }

    @Test
    fun `free pack skips gem deduction`() = runTest {
        val result = useCase(PackTier.COMMON, 0, isFree = true)
        assertTrue(result is OpenCardPack.Result.Opened)
        assertEquals(1000L, playerRepo.profile.value.gems)
    }

    @Test
    fun `issue 18 regression - owned cards are persisted even when queried fresh from DB`() = runTest {
        // Pre-populate repo with some cards (simulates player who already owns cards)
        cardRepo.addCard(CardType.entries[0])
        cardRepo.addCard(CardType.entries[1])
        val initialCount = cardRepo.cards.value.size

        // Open a free pack — the use case queries the DB directly, not a stale snapshot
        val result = useCase(PackTier.COMMON, 0, isFree = true)
        assertTrue(result is OpenCardPack.Result.Opened)

        // Every card in the result must have been persisted (either new card or copy increment)
        val opened = (result as OpenCardPack.Result.Opened).cards
        for (card in opened) {
            val repoCard = cardRepo.cards.value.find { it.type == card.type }
            assertTrue(repoCard != null, "Card ${card.type} was not persisted to repository")
            if (!card.isNew) {
                assertTrue(repoCard!!.copyCount > 1, "Duplicate ${card.type} should have incremented copy count")
            }
        }
    }
}
