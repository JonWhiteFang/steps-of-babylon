package com.whitefang.stepsofbabylon.data.sensor

import android.util.Log
import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.domain.repository.DailyLoginRepository
import com.whitefang.stepsofbabylon.domain.repository.WeeklyChallengeRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
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
    private val dailyLoginRepository: DailyLoginRepository,
    private val weeklyChallengeRepository: WeeklyChallengeRepository,
    private val dailyMissionDao: DailyMissionDao,
    private val widgetUpdateHelper: WidgetUpdateHelper,
    private val workshopRepository: WorkshopRepository,
    private val labRepository: LabRepository,
) {
    companion object {
        private const val TAG = "DailyStepManager"

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

    /**
     * #120 (audit #6 + #12): this @Singleton is mutated by two genuinely-concurrent producers —
     * the foreground StepCounterService sensor collector (Dispatchers.Default) and the periodic
     * StepSyncWorker (CoroutineWorker, also a multi-threaded pool). Every mutating entry point
     * ([recordSteps], [recordActivityMinutes], [ensureInitializedLocked]) runs its full
     * read-check-write body under this Mutex, so the non-atomic ceiling RMW
     * (`DAILY_CEILING - dailyCreditedTotal` … `dailyCreditedTotal += credited`) can no longer
     * interleave to overshoot the 50k cap or double-credit. The Mutex is NOT reentrant, so the
     * init step is factored into [ensureInitializedLocked] which is only ever called while the
     * lock is already held. Mirrors the repo's existing `RealConsentManager.initMutex` /
     * `FakeWorkshopRepository.atomicMutex` idiom.
     */
    private val mutex = Mutex()

    /**
     * Test-only suspend seam (#120). Invoked between the ceiling read and the credit RMW in
     * [recordSteps] so a concurrency test can deterministically park one coroutine mid-critical-
     * section and prove the lock serialises the read-check-write. Default no-op in production.
     */
    internal var onBeforeCreditCommit: suspend () -> Unit = {}

    /**
     * #232: reports a swallowed follow-on-pipeline failure (widget / supply-drop / economy /
     * mission stage) instead of dropping it into a silent `catch{}`. The step credit itself must
     * never fail because of a follow-on error, so the catch stays — but the failure is now
     * surfaced. Defaults to a `Log.w` so a recurring failure is visible in logcat / a bug report;
     * a test can override it to assert a stage threw. Deliberately NOT routed to
     * [com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore]: that store is a
     * single-slot, newest-wins crash breadcrumb (#190) — writing a per-credit pipeline warning to
     * it would clobber the genuine crash context it exists to preserve.
     *
     * Invariant: this runs INSIDE the #120 credit [mutex] (the catch blocks live in
     * [runFollowOnPipeline], called under `mutex.withLock`). Any override MUST be non-blocking and
     * non-throwing — it is `(String, Throwable) -> Unit` (non-suspend) so it cannot re-enter the
     * non-reentrant Mutex, but a blocking call would stall the credit lock and a throw would escape
     * the catch and abort [recordSteps] after the credit already committed.
     */
    internal var onPipelineError: (stage: String, e: Throwable) -> Unit = { stage, e ->
        Log.w(TAG, "follow-on-pipeline stage '$stage' failed", e)
    }

    private var currentDate: String = todayDate()
    @Volatile private var dailySensorTotal: Long = 0L
    @Volatile private var dailySensorCredited: Long = 0L
    @Volatile private var dailyCreditedTotal: Long = 0L
    @Volatile private var dailyActivityMinuteTotal: Long = 0L
    @Volatile private var initialized = false

    private val generateSupplyDrop = GenerateSupplyDrop()
    private var dropState = DropGeneratorState()

    // #120: ConcurrentHashMap so getSensorStepsPerMinute().toMap() (read on the worker thread) can
    // never throw ConcurrentModificationException against a concurrent recordSteps write. The
    // size-trim RMW below still runs under [mutex] for correctness.
    private val stepsPerMinute = ConcurrentHashMap<Long, Long>()

    private val trackDailyLogin by lazy { TrackDailyLogin(dailyLoginRepository, playerRepository) }
    private val trackWeeklyChallenge by lazy { TrackWeeklyChallenge(weeklyChallengeRepository, stepRepository, playerRepository) }

    fun getDailyCredited(): Long = dailyCreditedTotal

    fun getSensorStepsPerMinute(): Map<Long, Long> = stepsPerMinute.toMap()

    fun todayDate(): String = LocalDate.now().format(DATE_FMT)

    /**
     * #120: caller MUST already hold [mutex] (the Mutex is non-reentrant). Called only from
     * within the locked bodies of [recordSteps] / [recordActivityMinutes].
     */
    private suspend fun ensureInitializedLocked() {
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

        // #120: hold the lock across the WHOLE read-check-write so the ceiling check and the
        // credit increment are atomic w.r.t. a concurrent recordSteps / recordActivityMinutes.
        mutex.withLock {
            ensureInitializedLocked()

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
            // #120 test seam: parks a coroutine here so a test can prove the lock serialises the
            // read→commit gap (no-op in production). It runs INSIDE the lock, so a second
            // coroutine blocks at withLock above rather than interleaving the RMW.
            onBeforeCreditCommit()
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
    }

    /**
     * Credits an HC-verified recovered gap (offline-recovery, #251). UNLIKE [recordSteps] this path
     * SKIPS rate-limit + velocity analysis: the total is independently bounded by Health Connect's
     * own daily aggregate (the caller derives the gap as `hcTotal - sensorTotal`, the same source
     * [com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator] already trusts), so
     * funnelling it through the live-walking 200/min limiter would clamp a legitimate multi-hour
     * recovery to a tiny fraction and discard the rest as a false anti-cheat rejection. The 50k
     * [DAILY_CEILING] and the STEP_MULTIPLIER bonus still apply (the cap stays absolute; gap steps
     * are legitimately-walked sensor steps credited identically to live crediting).
     *
     * Runs under the same non-reentrant [mutex] as [recordSteps] (calls [ensureInitializedLocked],
     * never [recordSteps] — that would self-deadlock). Persists the raw gap into [dailySensorTotal]
     * so the next [com.whitefang.stepsofbabylon.data.healthconnect.StepGapFiller.fillGaps] computes
     * `gap ≈ 0` (idempotent). Per-minute tracking ([stepsPerMinute]) is intentionally NOT updated —
     * a multi-minute elapsed window has no single true epoch minute and must not skew the
     * activity-minute overlap dedup.
     */
    suspend fun recordTrustedSteps(rawDelta: Long, timestampMs: Long) {
        if (rawDelta <= 0) return

        mutex.withLock {
            ensureInitializedLocked()

            val multiplied = applyStepMultiplier(rawDelta)

            val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal).coerceAtLeast(0)
            val credited = multiplied.coerceAtMost(remainingCeiling)
            if (credited <= 0) return

            dailySensorTotal += rawDelta
            dailyCreditedTotal += credited

            // Persist-with-sum FIRST, then increment the field (mirror recordSteps:208-209).
            stepRepository.updateDailySteps(currentDate, dailySensorTotal, dailySensorCredited + credited)
            dailySensorCredited += credited
            playerRepository.addSteps(credited)

            runFollowOnPipeline(timestampMs)
        }
    }

    suspend fun recordActivityMinutes(
        activityMinutes: Map<String, Int>,
        stepEquivalents: Long,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (stepEquivalents <= 0) return

        // #120: same lock as recordSteps — the two share dailyCreditedTotal and the ceiling RMW.
        mutex.withLock {
            ensureInitializedLocked()

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
    }

    private suspend fun runFollowOnPipeline(timestampMs: Long) {
        // Widget update
        try {
            val balance = playerRepository.getStepBalance()
            widgetUpdateHelper.update(dailyCreditedTotal, balance)
        } catch (e: Exception) { onPipelineError("widget", e) }

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
        } catch (e: Exception) { onPipelineError("supply-drop", e) }

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
        } catch (e: Exception) { onPipelineError("economy", e) }

        // Walking mission progress
        try { updateWalkingMissions() } catch (e: Exception) { onPipelineError("mission", e) }
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
        // V1X-18 / ADR-0015: STEP_MULTIPLIER now uses the asymptotic curve
        // `bonus = 1 - (1 - 0.05)^level` (centralised in SimulationMath.stepMultiplierBonus).
        // STEP_EFFICIENCY (Lab) keeps its linear +2 %/level — capped at 0.20 since maxLevel = 10.
        // The two stack ADDITIVELY but the COMBINED total is clamped at +100 % to preserve the
        // GDD §4.3 "cap 100 %" wording and the existing balance-test contract.
        val workshopBonus = com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath.stepMultiplierBonus(wsLevel)
        val labBonus = labLevel * STEP_EFFICIENCY_PER_LEVEL
        val bonus = (workshopBonus + labBonus).coerceAtMost(STEP_MULTIPLIER_CAP)
        return (baseCredit * (1.0 + bonus)).toLong()
    }
}
