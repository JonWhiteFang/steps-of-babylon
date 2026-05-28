package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileEntity
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlayerRepositoryImpl @Inject constructor(
    private val dao: PlayerProfileDao,
) : PlayerRepository {

    override fun observeProfile(): Flow<PlayerProfile> =
        dao.get().filterNotNull().map { it.toDomain() }

    override fun observeWallet(): Flow<PlayerWallet> =
        dao.get().filterNotNull().map { it.toDomain().toWallet() }

    override fun observeTier(): Flow<Int> =
        dao.get().filterNotNull().map { it.currentTier }

    override suspend fun addSteps(amount: Long) = dao.adjustStepBalance(amount)
    override suspend fun spendSteps(amount: Long) = dao.adjustStepBalance(-amount)
    override suspend fun addGems(amount: Long) {
        dao.adjustGems(amount)
        dao.incrementGemsEarned(amount)
    }
    override suspend fun spendGems(amount: Long) {
        dao.spendGemsAtomic(amount)
    }
    override suspend fun addPowerStones(amount: Long) {
        dao.adjustPowerStones(amount)
        dao.incrementPowerStonesEarned(amount)
    }
    override suspend fun spendPowerStones(amount: Long) {
        dao.spendPowerStonesAtomic(amount)
    }
    override suspend fun addCardDust(amount: Long) = dao.adjustCardDust(amount)
    override suspend fun spendCardDust(amount: Long) = dao.adjustCardDust(-amount)
    override suspend fun updateTier(tier: Int) = dao.updateTier(tier)
    override suspend fun updateHighestUnlockedTier(tier: Int) = dao.updateHighestUnlockedTier(tier)
    override suspend fun updateLabSlotCount(count: Int) = dao.updateLabSlotCount(count)
    override suspend fun updateStreak(streak: Int, date: String) = dao.updateStreak(streak, date)
    override suspend fun incrementBattleStats(rounds: Long, kills: Long, cash: Long) = dao.incrementBattleStats(rounds, kills, cash)
    override suspend fun updateAdRemoved(removed: Boolean) = dao.updateAdRemoved(removed)
    override suspend fun updateSeasonPass(active: Boolean, expiry: Long) = dao.updateSeasonPass(active, expiry)
    override suspend fun updateFreeLabRushUsed(date: String) = dao.updateFreeLabRushUsed(date)
    override suspend fun updateFreeCardPackAdUsed(date: String) = dao.updateFreeCardPackAdUsed(date)

    override suspend fun updateBestWave(tier: Int, wave: Int) {
        val entity = dao.get().first() ?: return
        val updated = entity.bestWavePerTier + (tier to wave)
        dao.updateBestWavePerTier(updated)
    }

    override suspend fun updateLastActiveAt(timestamp: Long) = dao.updateLastActiveAt(timestamp)

    override suspend fun getStepBalance(): Long =
        dao.get().first()?.currentStepBalance ?: 0

    override suspend fun ensureProfileExists() {
        if (dao.get().first() == null) {
            dao.upsert(PlayerProfileEntity())
        }
    }

    private fun PlayerProfileEntity.toDomain() = PlayerProfile(
        id = id,
        totalStepsEarned = totalStepsEarned,
        stepBalance = currentStepBalance,
        gems = gems,
        powerStones = powerStones,
        cardDust = cardDust,
        currentTier = currentTier,
        highestUnlockedTier = highestUnlockedTier,
        labSlotCount = labSlotCount,
        bestWavePerTier = bestWavePerTier,
        currentStreak = currentStreak,
        lastLoginDate = lastLoginDate,
        totalGemsEarned = totalGemsEarned,
        totalGemsSpent = totalGemsSpent,
        totalPowerStonesEarned = totalPowerStonesEarned,
        totalPowerStonesSpent = totalPowerStonesSpent,
        totalRoundsPlayed = totalRoundsPlayed,
        totalEnemiesKilled = totalEnemiesKilled,
        totalCashEarned = totalCashEarned,
        adRemoved = adRemoved,
        seasonPassActive = seasonPassActive,
        seasonPassExpiry = seasonPassExpiry,
        freeLabRushUsedToday = freeLabRushUsedToday,
        freeCardPackAdUsedToday = freeCardPackAdUsedToday,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt,
    )
}
