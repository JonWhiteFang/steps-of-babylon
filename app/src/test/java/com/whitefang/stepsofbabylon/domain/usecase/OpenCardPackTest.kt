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
        // Seeded random: always rolls 0.0 → COMMON rarity for COMMON pack
        useCase = OpenCardPack(cardRepo, playerRepo, Random(42))
    }

    @Test
    fun `insufficient gems returns error`() = runTest {
        playerRepo.profile.value = PlayerProfile(gems = 10)
        val result = useCase(PackTier.COMMON, 10, emptyList())
        assertTrue(result is OpenCardPack.Result.InsufficientGems)
    }

    @Test
    fun `deducts gems on success`() = runTest {
        val result = useCase(PackTier.COMMON, 1000, emptyList())
        assertTrue(result is OpenCardPack.Result.Opened)
        assertEquals(950L, playerRepo.profile.value.gems)
    }

    @Test
    fun `new cards are added to repository`() = runTest {
        val result = useCase(PackTier.COMMON, 1000, emptyList())
        assertTrue(result is OpenCardPack.Result.Opened)
        val opened = (result as OpenCardPack.Result.Opened).cards
        assertTrue(opened.any { it.isNew })
    }

    @Test
    fun `duplicate cards increment copy count`() = runTest {
        // Own all cards already
        val owned = CardType.entries.mapIndexed { i, t -> OwnedCard(i + 1, t, 1, false) }
        val result = useCase(PackTier.COMMON, 1000, owned)
        assertTrue(result is OpenCardPack.Result.Opened)
        val opened = (result as OpenCardPack.Result.Opened).cards
        assertTrue(opened.all { !it.isNew })
    }
}
