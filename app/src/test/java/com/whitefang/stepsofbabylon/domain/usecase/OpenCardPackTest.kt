package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardRarity
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

    // #122 (audit #5): stale snapshot says affordable but the wallet is empty — the guarded deduct
    // no-ops, so NO cards may be granted and the result must be InsufficientGems. Pre-fix the
    // discarded rows-affected meant the pack opened for free.
    @Test
    fun `R122 stale snapshot does not grant a free pack`() = runTest {
        // On-disk balance is 0, but the caller passes a stale snapshot of 1000 gems.
        playerRepo.profile.value = PlayerProfile(gems = 0)
        val result = useCase(PackTier.COMMON, gems = 1000)
        assertTrue(
            result is OpenCardPack.Result.InsufficientGems,
            "a pack must not open when the guarded deduct fails (got $result)",
        )
        assertTrue(cardRepo.cards.value.isEmpty(), "no cards may be granted for a failed deduct")
        assertEquals(0L, playerRepo.profile.value.gems, "balance must stay at 0")
    }

    // #35: OpenCardPack picks a rarity, filters CardType.entries to it, then indexes with
    // `candidates[random.nextInt(candidates.size)]`. kotlin.random.Random.nextInt(0) throws
    // IllegalArgumentException, so an empty rarity bucket (a future content rebalance, or a new
    // CardRarity with no members) would crash a pack open AFTER gems were spent. These guards
    // pin (a) the roster invariant and (b) the defensive fallback so the crash can never ship.

    @Test
    fun `R35 every CardRarity has at least one CardType`() {
        // Fails the instant a rarity bucket goes empty — before it can crash a live pack open.
        for (rarity in CardRarity.entries) {
            assertTrue(
                CardType.entries.any { it.rarity == rarity },
                "CardRarity.$rarity has no CardType members — OpenCardPack would crash rolling it",
            )
        }
    }

    @Test
    fun `R35 pickCardType falls back to a COMMON card when the rarity bucket is empty`() {
        // Directly exercises the empty-bucket path the live roster cannot reach today. A naive
        // `candidates[random.nextInt(candidates.size)]` throws on an empty list; the fallback
        // must return a real card instead of crashing.
        val picked = useCase.pickCardType(emptyList())
        assertTrue(
            picked.rarity == CardRarity.COMMON,
            "an empty candidate bucket must fall back to a COMMON card, got $picked",
        )
    }

    @Test
    fun `R35 pickCardType returns a card from a non-empty bucket`() {
        val bucket = CardType.entries.filter { it.rarity == CardRarity.EPIC }
        val picked = useCase.pickCardType(bucket)
        assertTrue(picked in bucket, "a non-empty bucket must yield one of its own cards, got $picked")
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
