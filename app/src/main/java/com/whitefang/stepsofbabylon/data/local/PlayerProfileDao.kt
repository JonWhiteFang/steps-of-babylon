package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun get(): Flow<PlayerProfileEntity?>

    @Upsert
    suspend fun upsert(entity: PlayerProfileEntity)

    @Query("UPDATE player_profile SET currentStepBalance = :balance WHERE id = 1")
    suspend fun updateStepBalance(balance: Long)

    @Query("UPDATE player_profile SET gems = :gems WHERE id = 1")
    suspend fun updateGems(gems: Long)

    @Query("UPDATE player_profile SET powerStones = :powerStones WHERE id = 1")
    suspend fun updatePowerStones(powerStones: Long)

    @Query("UPDATE player_profile SET currentTier = :tier WHERE id = 1")
    suspend fun updateTier(tier: Int)

    @Query("UPDATE player_profile SET highestUnlockedTier = :tier WHERE id = 1")
    suspend fun updateHighestUnlockedTier(tier: Int)

    @Query("UPDATE player_profile SET bestWavePerTier = :bestWavePerTier WHERE id = 1")
    suspend fun updateBestWavePerTier(bestWavePerTier: Map<Int, Int>)

    @Query("UPDATE player_profile SET lastActiveAt = :lastActiveAt WHERE id = 1")
    suspend fun updateLastActiveAt(lastActiveAt: Long)

    @Query(
        "UPDATE player_profile SET currentStepBalance = MAX(0, currentStepBalance + :delta), totalStepsEarned = CASE WHEN :delta > 0 THEN totalStepsEarned + :delta ELSE totalStepsEarned END WHERE id = 1",
    )
    suspend fun adjustStepBalance(delta: Long)

    /**
     * Atomic guarded Step deduction. Used by multi-write transactions (e.g. [WorkshopDao.purchaseUpgradeAtomic])
     * to combine an affordability check and a deduction in a single SQL statement. The `WHERE` clause ensures
     * the row is only updated when the player can afford the cost, eliminating the classic read-modify-write
     * race where two concurrent purchases can both pass a Kotlin-side check but only one has the funds.
     *
     * @param cost Steps to deduct. Must be non-negative.
     * @return Rows affected — `1` if the player had sufficient Steps and the balance was deducted; `0` otherwise.
     */
    @Query(
        "UPDATE player_profile SET currentStepBalance = currentStepBalance - :cost WHERE id = 1 AND currentStepBalance >= :cost",
    )
    suspend fun adjustStepBalanceIfSufficient(cost: Long): Int

    @Query("UPDATE player_profile SET gems = MAX(0, gems + :delta) WHERE id = 1")
    suspend fun adjustGems(delta: Long)

    @Query("UPDATE player_profile SET powerStones = MAX(0, powerStones + :delta) WHERE id = 1")
    suspend fun adjustPowerStones(delta: Long)

    @Query("UPDATE player_profile SET labSlotCount = :count WHERE id = 1")
    suspend fun updateLabSlotCount(count: Int)

    @Query("UPDATE player_profile SET currentStreak = :streak, lastLoginDate = :date WHERE id = 1")
    suspend fun updateStreak(
        streak: Int,
        date: String,
    )

    @Query("UPDATE player_profile SET totalGemsEarned = totalGemsEarned + :amount WHERE id = 1")
    suspend fun incrementGemsEarned(amount: Long)

    @Query("UPDATE player_profile SET totalGemsSpent = totalGemsSpent + :amount WHERE id = 1")
    suspend fun incrementGemsSpent(amount: Long)

    @Query("UPDATE player_profile SET totalPowerStonesEarned = totalPowerStonesEarned + :amount WHERE id = 1")
    suspend fun incrementPowerStonesEarned(amount: Long)

    @Query("UPDATE player_profile SET totalPowerStonesSpent = totalPowerStonesSpent + :amount WHERE id = 1")
    suspend fun incrementPowerStonesSpent(amount: Long)

    @Query(
        "UPDATE player_profile SET totalRoundsPlayed = totalRoundsPlayed + :rounds, totalEnemiesKilled = totalEnemiesKilled + :kills, totalCashEarned = totalCashEarned + :cash WHERE id = 1",
    )
    suspend fun incrementBattleStats(
        rounds: Long,
        kills: Long,
        cash: Long,
    )

    @Query("UPDATE player_profile SET adRemoved = :removed WHERE id = 1")
    suspend fun updateAdRemoved(removed: Boolean)

    @Query("UPDATE player_profile SET seasonPassActive = :active, seasonPassExpiry = :expiry WHERE id = 1")
    suspend fun updateSeasonPass(
        active: Boolean,
        expiry: Long,
    )

    @Query("UPDATE player_profile SET freeLabRushUsedToday = :date WHERE id = 1")
    suspend fun updateFreeLabRushUsed(date: String)

    @Query("UPDATE player_profile SET freeCardPackAdUsedToday = :date WHERE id = 1")
    suspend fun updateFreeCardPackAdUsed(date: String)

    /**
     * Atomic guarded Gem deduction. Deducts [amount] from gem balance and increments
     * totalGemsSpent in a single SQL statement, only if the balance is sufficient.
     * Eliminates the TOCTOU race where two concurrent spends both pass a Kotlin-side check.
     *
     * @return Rows affected — `1` if sufficient and deducted; `0` otherwise.
     */
    @Query(
        "UPDATE player_profile SET gems = gems - :amount, totalGemsSpent = totalGemsSpent + :amount WHERE id = 1 AND gems >= :amount",
    )
    suspend fun spendGemsAtomic(amount: Long): Int

    /**
     * Atomic guarded Power Stone deduction. Same shape as [spendGemsAtomic].
     *
     * @return Rows affected — `1` if sufficient and deducted; `0` otherwise.
     */
    @Query(
        "UPDATE player_profile SET powerStones = powerStones - :amount, totalPowerStonesSpent = totalPowerStonesSpent + :amount WHERE id = 1 AND powerStones >= :amount",
    )
    suspend fun spendPowerStonesAtomic(amount: Long): Int
}
