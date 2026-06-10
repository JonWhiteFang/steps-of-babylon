package com.whitefang.stepsofbabylon.data.sensor

import com.whitefang.stepsofbabylon.service.StepSyncWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests the step ingestion coordination logic between service and worker.
 * Uses in-memory fakes to verify no double-crediting under all scenarios.
 */
class StepIngestionTest {

    private lateinit var prefs: FakeStepIngestionPreferences
    private var roomSensorSteps: Long = 0L
    private var totalCredited: Long = 0L

    // Use realistic epoch millis so heartbeat logic works correctly
    private val baseTime = 1_710_000_000_000L // ~March 2024
    private val threeMinMs = 3 * 60 * 1000L

    @BeforeEach
    fun setup() {
        prefs = FakeStepIngestionPreferences()
        roomSensorSteps = 0L
        totalCredited = 0L
    }

    /**
     * Drives the worker's sensorCatchUp logic through the REAL production decision
     * [StepSyncWorker.computeCatchUp] (#123 — no mirror-drift copy), applying the same Room/prefs
     * side-effects the worker applies. Returns the gap credited (0 if skipped / baseline-only).
     */
    private fun workerCatchUp(
        today: String,
        currentCounter: Long,
        nowMs: Long,
    ): Long {
        if (prefs.isServiceAlive(nowMs)) return 0

        val dayStart = prefs.getCounterAtDayStart(today)
        val sensorStepsAtDayStart = prefs.getSensorStepsAtDayStart(today)
        return when (
            val decision = StepSyncWorker.computeCatchUp(dayStart, currentCounter, roomSensorSteps, sensorStepsAtDayStart)
        ) {
            is StepSyncWorker.CatchUpDecision.Establish -> {
                prefs.setCounterAtDayStart(today, decision.counter, roomSensorSteps)
                0
            }
            is StepSyncWorker.CatchUpDecision.Rebaseline -> {
                prefs.setCounterAtDayStart(today, decision.counter, roomSensorSteps)
                0
            }
            is StepSyncWorker.CatchUpDecision.Credit -> {
                roomSensorSteps += decision.gap
                totalCredited += decision.gap
                decision.gap
            }
            StepSyncWorker.CatchUpDecision.Skip -> 0
        }
    }

    /**
     * Simulates the service crediting steps (updates Room sensorSteps + heartbeat).
     */
    private fun serviceCredit(steps: Long, nowMs: Long) {
        roomSensorSteps += steps
        totalCredited += steps
        prefs.updateServiceHeartbeat(nowMs)
    }

    @Test
    fun `worker skips when service heartbeat is fresh`() {
        prefs.setCounterAtDayStart("2026-03-11", 10000)
        prefs.updateServiceHeartbeat(baseTime)

        val credited = workerCatchUp("2026-03-11", 10500, baseTime + 500)
        assertEquals(0, credited)
    }

    @Test
    fun `worker establishes baseline on first run and credits nothing`() {
        val credited = workerCatchUp("2026-03-11", 50000, baseTime)
        assertEquals(0, credited)
        assertEquals(50000L, prefs.getCounterAtDayStart("2026-03-11"))
    }

    @Test
    fun `worker credits gap when service is dead`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000)
        // No heartbeat — service never ran

        val credited = workerCatchUp("2026-03-11", 50200, baseTime)
        assertEquals(200, credited)
    }

    @Test
    fun `worker credits only uncredited gap after service death`() {
        val today = "2026-03-11"
        prefs.setCounterAtDayStart(today, 50000)

        // Service credits 500 steps
        serviceCredit(500, baseTime)

        // Service dies. Worker runs 3 min after last heartbeat.
        // Counter is now 50700 (500 from service + 200 new)
        val credited = workerCatchUp(today, 50700, baseTime + threeMinMs)
        assertEquals(200, credited)
    }

    @Test
    fun `no double credit when service and worker both active`() {
        val today = "2026-03-11"
        prefs.setCounterAtDayStart(today, 50000)

        // Service credits 300 steps
        serviceCredit(300, baseTime)

        // Worker fires while service is alive (heartbeat fresh)
        val credited = workerCatchUp(today, 50300, baseTime + 500)
        assertEquals(0, credited)
        assertEquals(300, totalCredited)
    }

    @Test
    fun `day rollover resets baseline`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000)
        roomSensorSteps = 5000

        // New day — worker runs, day-start is null for new date
        val credited = workerCatchUp("2026-03-12", 55000, baseTime)
        assertEquals(0, credited) // just establishes baseline
        assertEquals(55000L, prefs.getCounterAtDayStart("2026-03-12"))
    }

    @Test
    fun `worker credits correctly after day rollover baseline established`() {
        roomSensorSteps = 0
        prefs.setCounterAtDayStart("2026-03-12", 55000)

        val credited = workerCatchUp("2026-03-12", 55300, baseTime)
        assertEquals(300, credited)
    }

    @Test
    fun `multiple worker runs credit incrementally without duplication`() {
        val today = "2026-03-11"
        prefs.setCounterAtDayStart(today, 50000)

        val c1 = workerCatchUp(today, 50200, baseTime)
        assertEquals(200, c1)

        val c2 = workerCatchUp(today, 50350, baseTime + 15 * 60 * 1000)
        assertEquals(150, c2)

        val c3 = workerCatchUp(today, 50350, baseTime + 30 * 60 * 1000)
        assertEquals(0, c3)

        assertEquals(350, totalCredited)
    }

    @Test
    fun `service credits then dies then worker recovers exactly the gap`() {
        val today = "2026-03-11"
        prefs.setCounterAtDayStart(today, 50000)

        // Service credits 1000 steps over time
        serviceCredit(400, baseTime)
        serviceCredit(300, baseTime + 1000)
        serviceCredit(300, baseTime + 2000)
        assertEquals(1000, totalCredited)

        // Service dies. 500 more steps happen.
        val credited = workerCatchUp(today, 51500, baseTime + 2000 + threeMinMs)
        assertEquals(500, credited)
        assertEquals(1500, totalCredited)
    }

    @Test
    fun `counter reboot mid-day produces no negative credit`() {
        val today = "2026-03-11"
        prefs.setCounterAtDayStart(today, 50000)
        roomSensorSteps = 3000

        // After device reboot, counter resets to a low value
        val credited = workerCatchUp(today, 100, baseTime)
        assertEquals(0, credited)
    }

    // #123 (audit #7): a mid-day reboot must RE-BASELINE (not just swallow the negative) so steps
    // walked after the reboot still credit. Pre-fix the stale baseline left rawToday negative for
    // the rest of the day and every post-reboot step was lost.
    @Test
    fun `worker re-baselines and credits correctly after a mid-day reboot`() {
        val today = "2026-03-11"
        // Pre-reboot: baseline 8000, and 8000 raw steps already credited live by the service.
        prefs.setCounterAtDayStart(today, 8000)
        roomSensorSteps = 8000
        totalCredited = 8000 // those 8000 were credited live before the reboot

        // Step 1: post-reboot worker run — counter restarted near 0. Must re-baseline to 120 and
        // credit nothing for the discontinuity (pre-reboot steps were already credited live).
        val c1 = workerCatchUp(today, 120, baseTime)
        assertEquals(0, c1, "the reboot discontinuity itself must credit nothing")
        assertEquals(120L, prefs.getCounterAtDayStart(today), "baseline must re-anchor to the post-reboot counter")

        // Step 2: 180 steps walked after the reboot (counter 120 -> 300). Must credit exactly 180,
        // measured from the NEW baseline — NOT swallowed by the stale pre-reboot cumulative.
        val c2 = workerCatchUp(today, 300, baseTime + 15 * 60 * 1000)
        assertEquals(180, c2, "post-reboot steps must credit from the re-anchored baseline")

        // No loss, no double-credit: 8000 pre-reboot + 180 post-reboot.
        assertEquals(8180, totalCredited)
    }
}
