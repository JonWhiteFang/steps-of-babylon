package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.domain.time.TimeProvider

/**
 * Credits Power Stones to the player's wallet on boss kills, respecting the per-day
 * [DAILY_BOSS_PS_CAP]. Each boss kill awards [tier] PS (T1=1, T2=2, … T10=10).
 *
 * The cap-check + counter-increment + wallet-credit chain runs inside a single Room
 * `@Transaction` via [DailyStepDao.creditBossPowerStonesAtomic], mirroring the
 * [AwardBattleSteps] pattern (RO-02 B.2 PR 2).
 *
 * Returns the amount actually credited (0 when the daily cap is exhausted).
 */
class AwardBossPowerStones(
    private val dailyStepDao: DailyStepDao,
    private val playerProfileDao: PlayerProfileDao,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {

    suspend operator fun invoke(
        tier: Int,
        today: String = timeProvider.today().toString(),
    ): Long = dailyStepDao.creditBossPowerStonesAtomic(
        date = today,
        requested = tier.toLong().coerceAtLeast(1L),
        dailyCap = DAILY_BOSS_PS_CAP,
        playerDao = playerProfileDao,
    )

    companion object {
        /** Maximum boss-earned Power Stones the player may collect per calendar day. */
        const val DAILY_BOSS_PS_CAP: Long = 100L
    }
}
