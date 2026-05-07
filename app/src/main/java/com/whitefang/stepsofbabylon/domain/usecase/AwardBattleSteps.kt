package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import kotlin.math.min

/**
 * Credits battle-earned Steps to the player's wallet, respecting the per-day
 * [DAILY_BATTLE_STEP_CAP]. Intended to be invoked once per enemy kill with the
 * flat per-type reward from [com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler.stepReward].
 *
 * Battle Steps are tracked separately from the 50,000 daily walking ceiling —
 * this use case counts against [DailyStepRecordEntity.battleStepsEarned], which
 * is distinct from the walking credit pipeline. Returns the amount that was
 * actually credited (0 when the daily cap is exhausted, a partial amount when
 * only part of the request fits under the cap).
 *
 * [timeProvider] is the B.1 (RO-01) seam for midnight-boundary testability.
 * Callers may still override [invoke]'s `today` parameter directly for tests
 * that seed a specific date; the default expression resolves through
 * [timeProvider] so a [FakeTimeProvider] in the constructor reaches both the
 * default path and any code that reads `today` internally.
 */
class AwardBattleSteps(
    private val playerRepository: PlayerRepository,
    private val dailyStepDao: DailyStepDao,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {

    suspend operator fun invoke(
        amount: Long,
        today: String = timeProvider.today().toString(),
    ): Long {
        if (amount <= 0L) return 0L
        val alreadyEarned = dailyStepDao.getBattleStepsEarned(today)
        val remaining = (DAILY_BATTLE_STEP_CAP - alreadyEarned).coerceAtLeast(0L)
        if (remaining <= 0L) return 0L
        val credited = min(amount, remaining)
        playerRepository.addSteps(credited)
        dailyStepDao.incrementBattleSteps(today, credited)
        return credited
    }

    companion object {
        /** Maximum battle-earned Steps the player may collect per calendar day. */
        const val DAILY_BATTLE_STEP_CAP: Long = 2_000L
    }
}
