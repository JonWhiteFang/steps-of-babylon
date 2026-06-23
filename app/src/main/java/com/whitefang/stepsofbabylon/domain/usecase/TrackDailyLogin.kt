package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.DailyLogin
import com.whitefang.stepsofbabylon.domain.repository.DailyLoginRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TrackDailyLogin(
    private val dailyLoginRepository: DailyLoginRepository,
    private val playerRepository: PlayerRepository,
) {
    companion object {
        private const val PS_STEP_THRESHOLD = 1_000L
        private const val MAX_STREAK = 7
        private const val MAX_GEM_REWARD = 5
        private const val SEASON_PASS_DAILY_GEMS = 10L
    }

    suspend fun checkAndAward(
        todayDate: String,
        todayCreditedSteps: Long,
        seasonPassActive: Boolean = false,
        seasonPassExpiry: Long = 0,
        isRollback: Boolean = false,
    ) {
        // #211: on a detected backward clock rollback, refuse ALL credit for the tampered date and
        // write NOTHING — no streak/gem grant, no DailyLogin row, no gemsClaimed flag. Writing
        // gemsClaimed=true here would silently deny credit when the date legitimately arrives later
        // (the !login.gemsClaimed gate). The power-stone-for-1k-steps grant is step-gated (the step
        // anti-cheat owns that axis), but on a rollback we still write nothing — the row isn't created,
        // so the PS for this date is deferred to the next trusted pass (acceptable; steps persist).
        if (isRollback) return

        val login = dailyLoginRepository.getByDate(todayDate) ?: DailyLogin(date = todayDate)
        var updated = login.copy(stepsWalked = todayCreditedSteps)

        // PS for walking 1k+ steps
        if (todayCreditedSteps >= PS_STEP_THRESHOLD && !login.powerStoneClaimed) {
            playerRepository.addPowerStones(1)
            updated = updated.copy(powerStoneClaimed = true)
        }

        // Gem streak on first check of the day
        if (!login.gemsClaimed) {
            val profile = playerRepository.observeProfile().first()
            val yesterday =
                LocalDate
                    .parse(todayDate, DateTimeFormatter.ISO_LOCAL_DATE)
                    .minusDays(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)

            val newStreak =
                when (profile.lastLoginDate) {
                    todayDate -> profile.currentStreak

                    // Already logged in today
                    yesterday -> (profile.currentStreak % MAX_STREAK) + 1

                    else -> 1 // Streak broken
                }

            if (profile.lastLoginDate != todayDate) {
                var gemReward = newStreak.coerceAtMost(MAX_GEM_REWARD).toLong()
                // Season Pass bonus: +10 Gems/day
                if (seasonPassActive && seasonPassExpiry > System.currentTimeMillis()) {
                    gemReward += SEASON_PASS_DAILY_GEMS
                }
                playerRepository.addGems(gemReward)
                playerRepository.updateStreak(newStreak, todayDate)
            }
            updated = updated.copy(gemsClaimed = true)
        }

        dailyLoginRepository.upsert(updated)
    }
}
