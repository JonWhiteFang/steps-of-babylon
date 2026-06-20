package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.repository.WeeklyChallengeRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class TrackWeeklyChallenge(
    private val weeklyChallengeRepository: WeeklyChallengeRepository,
    private val stepRepository: StepRepository,
    private val playerRepository: PlayerRepository,
) {
    companion object {
        private val THRESHOLDS = listOf(50_000L to 10L, 75_000L to 20L, 100_000L to 35L)
    }

    suspend fun checkAndAward() {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val weekStart = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val weekEnd = sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val weeklySteps = stepRepository.sumCreditedSteps(weekStart, weekEnd)
        val existing = weeklyChallengeRepository.getByWeek(weekStart) ?: WeeklyChallenge(weekStartDate = weekStart)

        var newTier = existing.claimedTier
        var psToAward = 0L

        for ((index, pair) in THRESHOLDS.withIndex()) {
            val tier = index + 1
            if (tier > existing.claimedTier && weeklySteps >= pair.first) {
                val prevTotal = if (existing.claimedTier > 0) THRESHOLDS[existing.claimedTier - 1].second else 0L
                psToAward = pair.second - prevTotal
                newTier = tier
            }
        }

        if (newTier > existing.claimedTier) {
            playerRepository.addPowerStones(psToAward)
            weeklyChallengeRepository.upsert(existing.copy(totalSteps = weeklySteps, claimedTier = newTier))
        } else if (weeklySteps != existing.totalSteps) {
            weeklyChallengeRepository.upsert(existing.copy(totalSteps = weeklySteps))
        }
    }
}
