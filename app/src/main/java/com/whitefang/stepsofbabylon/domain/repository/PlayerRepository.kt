package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observeProfile(): Flow<PlayerProfile>

    fun observeWallet(): Flow<PlayerWallet>

    fun observeTier(): Flow<Int>

    suspend fun addSteps(amount: Long)

    /**
     * Unguarded Step deduction that clamps the balance at 0 (`MAX(0, balance - amount)`).
     * Used by [com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator]'s escrow
     * clawback, which intentionally deducts a disputed `excess` that may exceed the current
     * balance (the clamp is the desired behaviour there). For affordability-gated *spends*,
     * use [spendStepsIfSufficient] so the caller can refuse to grant when the funds are short.
     */
    suspend fun spendSteps(amount: Long)

    /**
     * Atomic guarded Step deduction (#122). Deducts [amount] only if the balance is sufficient;
     * returns `true` iff the deduction happened. Mirrors the [spendGems] / [spendPowerStones]
     * contract so a use case can gate a grant on the deduct actually succeeding instead of
     * trusting a stale wallet snapshot. Backed by `PlayerProfileDao.adjustStepBalanceIfSufficient`.
     */
    suspend fun spendStepsIfSufficient(amount: Long): Boolean

    suspend fun addGems(amount: Long)

    /**
     * Atomic guarded Gem deduction (#122). Returns `true` iff [amount] was actually deducted
     * (sufficient balance), `false` otherwise — the caller MUST only grant the purchased item
     * when this returns `true`. Backed by `PlayerProfileDao.spendGemsAtomic`.
     */
    suspend fun spendGems(amount: Long): Boolean

    suspend fun addPowerStones(amount: Long)

    /** Atomic guarded Power Stone deduction (#122). Same contract as [spendGems]. */
    suspend fun spendPowerStones(amount: Long): Boolean

    suspend fun updateTier(tier: Int)

    suspend fun updateHighestUnlockedTier(tier: Int)

    suspend fun updateLabSlotCount(count: Int)

    suspend fun updateBestWave(
        tier: Int,
        wave: Int,
    )

    suspend fun updateStreak(
        streak: Int,
        date: String,
    )

    suspend fun incrementBattleStats(
        rounds: Long,
        kills: Long,
        cash: Long,
    )

    suspend fun updateAdRemoved(removed: Boolean)

    suspend fun updateSeasonPass(
        active: Boolean,
        expiry: Long,
    )

    suspend fun updateFreeLabRushUsed(date: String)

    suspend fun updateFreeCardPackAdUsed(date: String)

    suspend fun getStepBalance(): Long

    suspend fun updateLastActiveAt(timestamp: Long)

    suspend fun ensureProfileExists()
}
