package com.whitefang.stepsofbabylon.data.sensor

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.data.local.DailyLoginDao
import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeDao
import com.whitefang.stepsofbabylon.domain.model.DropGeneratorState
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.MissionCategory
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.usecase.GenerateSupplyDrop
import com.whitefang.stepsofbabylon.domain.usecase.TrackDailyLogin
import com.whitefang.stepsofbabylon.domain.usecase.TrackWeeklyChallenge
import com.whitefang.stepsofbabylon.service.SupplyDropNotificationManager
import com.whitefang.stepsofbabylon.service.WidgetUpdateHelper
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates step crediting: rate limit → daily ceiling → persist to Room → supply drops → economy rewards.
 */
@Singleton
class DailyStepManager @Inject constructor(
    private val stepRepository: StepRepository,
    private val playerRepository: PlayerRepository,
    private val rateLimiter: StepRateLimiter,
    private val velocityAnalyzer: StepVelocityAnalyzer,
    private val antiCheatPrefs: AntiCheatPreferences,
    private val walkingEncounterRepository: WalkingEncounterRepository,
    private val supplyDropNotificationManager: SupplyDropNotificationManager,
    private val dailyLoginDao: DailyLoginDao,
    private val weeklyChallengeDao: WeeklyChallengeDao,
    private val dailyStepDao: DailyStepDao,
    private val dailyMissionDao: DailyMissionDao,
    private val widgetUpdateHelper: WidgetUpdateHelper,
    private val workshopRepository: WorkshopRepository,
    private val labRepository: LabRepository,
) {
    companion object {
        const val DAILY_CEILING = 50_000L

        /** Per-level effect of the STEP_MULTIPLIER Workshop upgrade: +1 % bonus credited steps. */
        const val STEP_MULTIPLIER_PER_LEVEL = 0.01

        /**
         * Per-level effect of the STEP_EFFICIENCY Lab research: +2 % bonus credited steps
         * (RO-11 #A.3). Stacks additively with [STEP_MULTIPLIER_PER_LEVEL] under the shared
         * [STEP_MULTIPLIER_CAP] of +100 %.
         */
        const val STEP_EFFICIENCY_PER_LEVEL = 0.02

        /** Cap on the combined walking-credit bonus: +100 % (i.e. up to 2× credited steps). */
        const val STEP_MULTIPLIER_CAP = 1.0

        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    }

    private var currentDate: String = todayDate()
    private var dailySensorTotal: Long = 0L
    private var dailySensorCredited: Long = 0L
    private var dailyCreditedTotal: Long = 0L
    private var dailyActivityMinuteTotal: Long = 0L
    private var initialized = false

    private val generateSupplyDrop = GenerateSupplyDrop()
    private var dropState = DropGeneratorState()

    private val stepsPerMinute = mutableMapOf<Long, Long>()

    private val trackDailyLogin by lazy { TrackDailyLogin(dailyLoginDao, playerRepository) }
    private val trackWeeklyChallenge by lazy { TrackWeeklyChallenge(weeklyChallengeDao, dailyStepDao, playerRepository) }

    fun getDailyCredited(): Long = dailyCreditedTotal

    fun getSensorStepsPerMinute(): Map<Long, Long> = stepsPerMinute.toMap()

    fun todayDate(): String = LocalDate.now().format(DATE_FMT)

    private suspend fun ensureInitialized() {
        val today = todayDate()
        if (today != currentDate) {
            currentDate = today
            dailySensorTotal = 0L
            dailySensorCredited = 0L
            dailyCreditedTotal = 0L
            dailyActivityMinuteTotal = 0L
            dropState = DropGeneratorState()
            stepsPerMinute.clear()
            initialized = false
            antiCheatPrefs.resetDailyCounters(today)
        }

        if (!initialized) {
            val existing = stepRepository.getDailyRecord(currentDate)
            if (existing != null) {
                dailySensorTotal = existing.sensorSteps
                dailySensorCredited = existing.creditedSteps
                dailyActivityMinuteTotal = existing.stepEquivalents
                dailyCreditedTotal = existing.creditedSteps + existing.stepEquivalents
                dropState = dropState.copy(lastCheckSteps = dailyCreditedTotal)
            }
            initialized = true
        }
    }

    suspend fun recordSteps(rawDelta: Long, timestampMs: Long) {
        if (rawDelta <= 0) return

        ensureInitialized()

        val rateLimited = rateLimiter.credit(rawDelta, timestampMs)
        val rateRejected = rawDelta - rateLimited
        if (rateRejected > 0) antiCheatPrefs.incrementRateRejected(rateRejected)
        if (rateLimited <= 0) return

        // Velocity analysis — penalize unnatural patterns
        val velocityMultiplier = velocityAnalyzer.analyze(rawDelta, timestampMs)
        val velocityAdjusted = (rateLimited * velocityMultiplier).toLong()
        val velocityPenalized = rateLimited - velocityAdjusted
        if (velocityPenalized > 0) antiCheatPrefs.incrementVelocityPenalized(velocityPenalized)
        if (velocityAdjusted <= 0) return

        // STEP_MULTIPLIER Workshop upgrade applies AFTER anti-cheat (so the bonus only ever
        // multiplies legitimately-walked steps that already passed rate-limit + velocity
        // analysis) and BEFORE the 50k daily ceiling (so the absolute cap remains absolute).
        // The cap on the multiplier itself is +100 %, matching the GDD §4.3 "Cap 100 %".
        val multiplied = applyStepMultiplier(velocityAdjusted)

        val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal).coerceAtLeast(0)
        val credited = multiplied.coerceAtMost(remainingCeiling)
        if (credited <= 0) return

        dailySensorTotal += rawDelta
        dailyCreditedTotal += credited

        stepRepository.updateDailySteps(currentDate, dailySensorTotal, dailySensorCredited + credited)
        dailySensorCredited += credited
        playerRepository.addSteps(credited)

        // Per-minute tracking for overlap deduction
        val epochMin = timestampMs / 60_000
        stepsPerMinute[epochMin] = (stepsPerMinute[epochMin] ?: 0) + credited
        if (stepsPerMinute.size > 1440) {
            val oldest = stepsPerMinute.keys.min()
            stepsPerMinute.remove(oldest)
        }

        runFollowOnPipeline(timestampMs)
    }

    suspend fun recordActivityMinutes(
        activityMinutes: Map<String, Int>,
        stepEquivalents: Long,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (stepEquivalents <= 0) return

        ensureInitialized()

        val delta = stepEquivalents - dailyActivityMinuteTotal
        if (delta <= 0) return

        // STEP_MULTIPLIER intentionally does NOT apply here. The GDD §4.3 wording is
        // "+1 % bonus steps earned from walking" — sensor steps are walking; activity
        // minutes are converted from cycling / swimming / treadmill exercise sessions
        // via Health Connect's exercise-session aggregation. Restricting the multiplier
        // to the sensor path also keeps the existing `dailyActivityMinuteTotal +=
        // credited` source-tracking semantics intact (a multiplier > 1 would inflate
        // the source-side counter and under-credit subsequent HC deltas).

        val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal).coerceAtLeast(0)
        val credited = delta.coerceAtMost(remainingCeiling)
        if (credited <= 0) return

        dailyActivityMinuteTotal += credited
        dailyCreditedTotal += credited

        stepRepository.updateActivityMinutes(currentDate, activityMinutes, dailyActivityMinuteTotal)
        playerRepository.addSteps(credited)

        runFollowOnPipeline(timestampMs)
    }

    private suspend fun runFollowOnPipeline(timestampMs: Long) {
        // Widget update
        try {
            val balance = playerRepository.getStepBalance()
            widgetUpdateHelper.update(dailyCreditedTotal, balance)
        } catch (_: Exception) {}

        // Supply drop generation
        try {
            val prevSteps = dropState.lastCheckSteps
            val unclaimedCount = walkingEncounterRepository.getUnclaimedCount()
            val drop = generateSupplyDrop(dailyCreditedTotal, prevSteps, timestampMs, unclaimedCount)
            if (drop != null) {
                walkingEncounterRepository.enforceInboxCap(GenerateSupplyDrop.MAX_INBOX)
                val id = walkingEncounterRepository.createDrop(drop.trigger, drop.reward, drop.rewardAmount)
                supplyDropNotificationManager.notify(drop.copy(id = id.toInt()))
            }
            dropState = dropState.copy(
                lastCheckSteps = dailyCreditedTotal,
                milestoneTriggered = dropState.milestoneTriggered || (drop?.trigger == SupplyDropTrigger.DAILY_MILESTONE),
            )
        } catch (_: Exception) {}

        // Economy rewards
        try {
            // Mirror HomeViewModel.init by passing Season Pass flags so the
            // walking-streak Gem reward includes the +10 Gems/day bonus when the
            // pipeline runs from a background ingestion path (widget, worker,
            // or service) rather than app foreground.
            val profile = playerRepository.observeProfile().first()
            trackDailyLogin.checkAndAward(
                currentDate,
                dailyCreditedTotal,
                profile.seasonPassActive,
                profile.seasonPassExpiry,
            )
            trackWeeklyChallenge.checkAndAward()
        } catch (_: Exception) {}

        // Walking mission progress
        try { updateWalkingMissions() } catch (_: Exception) {}
    }

    private suspend fun updateWalkingMissions() {
        val missions = dailyMissionDao.getByDateOnce(currentDate)
        for (m in missions) {
            if (m.claimed || m.completed) continue
            val type = DailyMissionType.entries.find { it.name == m.missionType } ?: continue
            if (type.category != MissionCategory.WALKING) continue
            val progress = dailyCreditedTotal.toInt().coerceAtMost(m.target)
            dailyMissionDao.updateProgress(m.id, progress, progress >= m.target)
        }
    }

    /**
     * Applies the combined Workshop STEP_MULTIPLIER + Lab STEP_EFFICIENCY bonus to a base
     * step credit (RO-08 + RO-11 #A.3).
     *
     * Reads both levels fresh on every credit so any level-up takes effect immediately.
     * The per-level effects are additive (`ws × 0.01 + lab × 0.02`) and the combined sum
     * is capped at [STEP_MULTIPLIER_CAP] = 1.0 (i.e. up to 2× credited steps total) per the
     * GDD §4.3 "Cap 100 %" wording. STEP_EFFICIENCY caps at L10 = +20 %; STEP_MULTIPLIER caps
     * at the workshop max level. Combined max bonus is therefore +100 %, not +120 %.
     *
     * Returns the input unchanged when both levels are 0 or when either lookup fails —
     * defensive null-handling so a transient DB issue never silently penalises the player
     * by zeroing out their credit. Pre-RO-11 only the Workshop side was wired; the lab
     * STEP_EFFICIENCY enum was dead despite costing 5 000 Steps + 8 hours research time.
     */
    private suspend fun applyStepMultiplier(baseCredit: Long): Long {
        if (baseCredit <= 0) return baseCredit
        val wsLevel = try {
            workshopRepository.observeAllUpgrades().first()[UpgradeType.STEP_MULTIPLIER] ?: 0
        } catch (_: Exception) {
            0
        }
        val labLevel = try {
            labRepository.observeAllResearch().first()[ResearchType.STEP_EFFICIENCY] ?: 0
        } catch (_: Exception) {
            0
        }
        if (wsLevel <= 0 && labLevel <= 0) return baseCredit
        val bonus = (wsLevel * STEP_MULTIPLIER_PER_LEVEL + labLevel * STEP_EFFICIENCY_PER_LEVEL)
            .coerceAtMost(STEP_MULTIPLIER_CAP)
        return (baseCredit * (1.0 + bonus)).toLong()
    }
}
