package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

open class FakePlayerRepository(
    initialProfile: PlayerProfile = PlayerProfile(),
) : PlayerRepository {
    val profile = MutableStateFlow(initialProfile)

    /**
     * Number of times [spendSteps] was called directly on this fake. Post-RO-02 purchase flow
     * goes through [FakeWorkshopRepository.purchaseUpgradeAtomic] and never hits this method, so
     * a test can assert `spendStepsCallCount == 0` after a successful workshop purchase to prove
     * the atomic path is live. Other call sites (e.g. rewarded-ad refunds) still call it directly.
     */
    var spendStepsCallCount: Int = 0
        private set

    override fun observeProfile(): Flow<PlayerProfile> = profile

    override fun observeWallet(): Flow<PlayerWallet> = profile.map { it.toWallet() }

    override fun observeTier(): Flow<Int> = profile.map { it.currentTier }

    override suspend fun addSteps(amount: Long) {
        profile.update { it.copy(stepBalance = it.stepBalance + amount) }
    }

    override suspend fun spendSteps(amount: Long) {
        spendStepsCallCount++
        profile.update { it.copy(stepBalance = maxOf(0, it.stepBalance - amount)) }
    }

    // #122: mirrors PlayerProfileDao.adjustStepBalanceIfSufficient — guarded, no mutation on
    // insufficient balance, returns whether the deduction happened.
    override suspend fun spendStepsIfSufficient(amount: Long): Boolean {
        if (profile.value.stepBalance < amount) return false
        profile.update { it.copy(stepBalance = it.stepBalance - amount) }
        return true
    }

    override suspend fun addGems(amount: Long) {
        profile.update {
            it.copy(
                gems = it.gems + amount,
                totalGemsEarned =
                    it.totalGemsEarned + amount,
            )
        }
    }

    // #122: mirrors the guarded spendGemsAtomic — no mutation + false when insufficient (the
    // pre-#122 fake clamped to 0 and returned Unit, masking the free-grant bug).
    override suspend fun spendGems(amount: Long): Boolean {
        if (profile.value.gems < amount) return false
        profile.update { it.copy(gems = it.gems - amount, totalGemsSpent = it.totalGemsSpent + amount) }
        return true
    }

    open override suspend fun addPowerStones(amount: Long) {
        profile.update {
            it.copy(
                powerStones =
                    it.powerStones + amount,
                totalPowerStonesEarned = it.totalPowerStonesEarned + amount,
            )
        }
    }

    override suspend fun spendPowerStones(amount: Long): Boolean {
        if (profile.value.powerStones < amount) return false
        profile.update {
            it.copy(
                powerStones = it.powerStones - amount,
                totalPowerStonesSpent =
                    it.totalPowerStonesSpent + amount,
            )
        }
        return true
    }

    override suspend fun updateTier(tier: Int) {
        profile.update { it.copy(currentTier = tier) }
    }

    open override suspend fun updateHighestUnlockedTier(tier: Int) {
        profile.update { it.copy(highestUnlockedTier = tier) }
    }

    override suspend fun updateLabSlotCount(count: Int) {
        profile.update { it.copy(labSlotCount = count) }
    }

    override suspend fun updateStreak(
        streak: Int,
        date: String,
    ) {
        profile.update { it.copy(currentStreak = streak, lastLoginDate = date) }
    }

    open override suspend fun incrementBattleStats(
        rounds: Long,
        kills: Long,
        cash: Long,
    ) {
        profile.update {
            it.copy(
                totalRoundsPlayed = it.totalRoundsPlayed + rounds,
                totalEnemiesKilled =
                    it.totalEnemiesKilled + kills,
                totalCashEarned = it.totalCashEarned + cash,
            )
        }
    }

    open override suspend fun updateBestWave(
        tier: Int,
        wave: Int,
    ) {
        profile.update { it.copy(bestWavePerTier = it.bestWavePerTier + (tier to wave)) }
    }

    override suspend fun updateAdRemoved(removed: Boolean) {
        profile.update { it.copy(adRemoved = removed) }
    }

    override suspend fun updateSeasonPass(
        active: Boolean,
        expiry: Long,
    ) {
        profile.update { it.copy(seasonPassActive = active, seasonPassExpiry = expiry) }
    }

    override suspend fun updateFreeLabRushUsed(date: String) {
        profile.update { it.copy(freeLabRushUsedToday = date) }
    }

    override suspend fun updateFreeCardPackAdUsed(date: String) {
        profile.update { it.copy(freeCardPackAdUsedToday = date) }
    }

    override suspend fun updateLastActiveAt(timestamp: Long) {
        profile.update { it.copy(lastActiveAt = timestamp) }
    }

    override suspend fun getStepBalance(): Long = profile.value.stepBalance

    override suspend fun ensureProfileExists() {}
}
