package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import kotlin.random.Random

enum class PackTier(val gemCost: Long, val commonWeight: Double, val rareWeight: Double, val epicWeight: Double) {
    COMMON(50, 0.80, 0.18, 0.02),
    RARE(150, 0.50, 0.40, 0.10),
    EPIC(500, 0.20, 0.40, 0.40),
}

data class CardResult(val type: CardType, val isNew: Boolean, val copiesAwarded: Int = 1)

class OpenCardPack(
    private val cardRepository: CardRepository,
    private val random: Random = Random,
) {
    sealed class Result {
        data class Opened(val cards: List<CardResult>) : Result()
        data object InsufficientGems : Result()
    }

    suspend operator fun invoke(packTier: PackTier, gems: Long, isFree: Boolean = false): Result {
        // Cheap fast-path: reject obviously-unaffordable packs without a DB round-trip. The
        // authoritative gate is the guarded deduct inside openCardPackAtomic (a stale snapshot can
        // still pass this check — #122 / R122).
        if (!isFree && gems < packTier.gemCost) return Result.InsufficientGems

        // Roll the 3 card types first (pure + seeded — must stay here for unit coverage). First card
        // is guaranteed at pack-tier rarity; the other 2 use standard weights.
        val rolledTypes = (0 until 3).map { index ->
            val rarity = if (index == 0) packTierToRarity(packTier) else rollRarity(packTier)
            pickCardType(CardType.entries.filter { it.rarity == rarity })
        }

        // #236: deduct Gems + write all 3 cards atomically (one transaction). A crash mid-pack can
        // no longer debit Gems with no cards delivered. Free packs pass gemCost = 0 (no deduct).
        val gemCost = if (isFree) 0L else packTier.gemCost
        val isNewFlags = cardRepository.openCardPackAtomic(gemCost, rolledTypes.map { it.name })
            ?: return Result.InsufficientGems

        return Result.Opened(
            rolledTypes.zip(isNewFlags).map { (type, isNew) -> CardResult(type, isNew = isNew) },
        )
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
