package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.time.TimeProvider

/**
 * Credits battle-earned Steps to the player's wallet, respecting the per-day
 * [DAILY_BATTLE_STEP_CAP]. Intended to be invoked once per enemy kill with the
 * flat per-type reward from [com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler.stepReward].
 *
 * Battle Steps are tracked separately from the 50,000 daily walking ceiling \u2014
 * this use case counts against [DailyStepRecordEntity.battleStepsEarned], which
 * is distinct from the walking credit pipeline. Returns the amount that was
 * actually credited (0 when the daily cap is exhausted, a partial amount when
 * only part of the request fits under the cap).
 *
 * Post-RO-02 (B.2 PR 2): the cap-check + counter-increment + wallet-credit chain runs inside a
 * single Room `@Transaction` via [DailyStepDao.creditBattleStepsAtomic]. This closes the
 * partial-failure gap (a crash between the two writes could previously leave the wallet and
 * cap counter out of sync) and the concurrent-kill race (two kills arriving with 1 headroom
 * could previously both credit 1 and overflow the cap by 1).
 *
 * [timeProvider] is the B.1 (RO-01) seam for midnight-boundary testability.
 * Callers may still override [invoke]'s `today` parameter directly for tests
 * that seed a specific date; the default expression resolves through
 * [timeProvider] so a [FakeTimeProvider] in the constructor reaches both the
 * default path and any code that reads `today` internally.
 */
class AwardBattleSteps(
    private val stepRepository: StepRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(
        amount: Long,
        today: String = timeProvider.today().toString(),
    ): Long = stepRepository.creditBattleStepsAtomic(
        date = today,
        requested = amount,
        dailyCap = DAILY_BATTLE_STEP_CAP,
    )

    companion object {
        /** Maximum battle-earned Steps the player may collect per calendar day. */
        const val DAILY_BATTLE_STEP_CAP: Long = 2_000L
    }
}
