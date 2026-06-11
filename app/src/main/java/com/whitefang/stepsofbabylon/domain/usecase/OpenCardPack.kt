package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlin.random.Random

enum class PackTier(val gemCost: Long, val commonWeight: Double, val rareWeight: Double, val epicWeight: Double) {
    COMMON(50, 0.80, 0.18, 0.02),
    RARE(150, 0.50, 0.40, 0.10),
    EPIC(500, 0.20, 0.40, 0.40),
}

data class CardResult(val type: CardType, val isNew: Boolean, val copiesAwarded: Int = 1)

class OpenCardPack(
    private val cardRepository: CardRepository,
    private val playerRepository: PlayerRepository,
    private val random: Random = Random,
) {
    sealed class Result {
        data class Opened(val cards: List<CardResult>) : Result()
        data object InsufficientGems : Result()
    }

    suspend operator fun invoke(packTier: PackTier, gems: Long, isFree: Boolean = false): Result {
        if (!isFree && gems < packTier.gemCost) return Result.InsufficientGems
        // #122: gate the card grant on the guarded deduct's success (free packs skip the spend
        // entirely). Keeps the deduct inside the !isFree branch so free packs are never blocked.
        if (!isFree && !playerRepository.spendGems(packTier.gemCost)) return Result.InsufficientGems

        val results = mutableListOf<CardResult>()

        repeat(3) { index ->
            // First card guaranteed at pack-tier rarity; other 2 use standard weights
            val rarity = if (index == 0) packTierToRarity(packTier) else rollRarity(packTier)
            val candidates = CardType.entries.filter { it.rarity == rarity }
            val type = pickCardType(candidates)

            if (cardRepository.hasCard(type)) {
                cardRepository.incrementCopyCount(type)
                results += CardResult(type, isNew = false)
            } else {
                cardRepository.addCard(type)
                results += CardResult(type, isNew = true)
            }
        }
        return Result.Opened(results)
    }

    /**
     * Picks one card from [candidates]. #35: guards the empty-bucket case — a naive
     * `candidates[random.nextInt(candidates.size)]` throws `IllegalArgumentException` because
     * `Random.nextInt(0)` requires a positive bound, which would crash a pack open AFTER gems
     * were spent. The live 9-card roster never empties a rarity bucket, but a future content
     * rebalance (or a new [CardRarity] with no members) could; falling back to the COMMON
     * bucket keeps a pack open from ever crashing. `internal` for direct unit coverage.
     */
    internal fun pickCardType(candidates: List<CardType>): CardType {
        // Two-stage fallback so this can never call random.nextInt(0): the requested bucket → the
        // COMMON bucket → the full roster. CardType is a non-empty enum, so the final fallback is
        // always non-empty (a pack open must never crash after the gem deduct already succeeded).
        val pool = candidates
            .ifEmpty { CardType.entries.filter { it.rarity == CardRarity.COMMON } }
            .ifEmpty { CardType.entries }
        return pool[random.nextInt(pool.size)]
    }

    private fun packTierToRarity(packTier: PackTier): CardRarity = when (packTier) {
        PackTier.COMMON -> CardRarity.COMMON
        PackTier.RARE -> CardRarity.RARE
        PackTier.EPIC -> CardRarity.EPIC
    }

    private fun rollRarity(packTier: PackTier): CardRarity {
        val roll = random.nextDouble()
        return when {
            roll < packTier.commonWeight -> CardRarity.COMMON
            roll < packTier.commonWeight + packTier.rareWeight -> CardRarity.RARE
            else -> CardRarity.EPIC
        }
    }
}
