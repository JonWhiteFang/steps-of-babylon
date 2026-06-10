package com.whitefang.stepsofbabylon.data.sensor

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeDao
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import com.whitefang.stepsofbabylon.service.SupplyDropNotificationManager
import com.whitefang.stepsofbabylon.service.WidgetUpdateHelper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * #120 (audit #6 + #12): [DailyStepManager] is a @Singleton mutated by two genuinely-concurrent
 * producers (foreground service sensor collector + periodic worker). Pre-fix the ceiling check and
 * the `dailyCreditedTotal += credited` increment were a non-atomic read-check-write on unsynchronized
 * fields, so two credits near the 50k ceiling could both pass the check and both add — overshooting
 * the daily cap and double-crediting the permanent Steps wallet.
 *
 * This is a DETERMINISTIC test (not a probabilistic race). The production code exposes a test-only
 * `onBeforeCreditCommit` suspend seam that runs between the ceiling read and the credit commit. The
 * test parks worker thread A at that seam (with the fix, A is INSIDE the lock there) and then runs
 * worker thread B. Two real `Thread`s are used (not a coroutine pool) so the orchestration never
 * depends on the dispatcher's core-derived thread count.
 *
 * - With the Mutex fix: B blocks at `withLock` while A holds the lock, so B's read-check-write only
 *   runs AFTER A commits → the ceiling holds → wallet stays at 50,000.
 * - Without the fix: B's full read-check-write interleaves while A is parked → both pass the stale
 *   ceiling check → wallet overshoots to 50,100.
 *
 * `FakePlayerRepository.addSteps` uses a CAS-safe `MutableStateFlow.update{}`, so both credits truly
 * land — the lock is the only thing that can hold the total at the cap.
 */
class DailyStepManagerConcurrencyTest {

    private fun newManager(
        stepRepo: FakeStepRepository,
        playerRepo: FakePlayerRepository,
    ) = DailyStepManager(
        stepRepository = stepRepo,
        playerRepository = playerRepo,
        rateLimiter = StepRateLimiter(),
        velocityAnalyzer = StepVelocityAnalyzer(),
        antiCheatPrefs = mock<AntiCheatPreferences>(),
        walkingEncounterRepository = FakeWalkingEncounterRepository(),
        supplyDropNotificationManager = mock<SupplyDropNotificationManager>(),
        dailyLoginDao = FakeDailyLoginDao(),
        weeklyChallengeDao = FakeWeeklyChallengeDao(),
        dailyStepDao = FakeDailyStepDao(),
        dailyMissionDao = FakeDailyMissionDao(),
        widgetUpdateHelper = mock<WidgetUpdateHelper>(),
        workshopRepository = FakeWorkshopRepository(),
        labRepository = FakeLabRepository(),
    )

    @Test
    fun `concurrent credits near the ceiling never overshoot 50k`() {
        val stepRepo = FakeStepRepository()
        val playerRepo = FakePlayerRepository()
        val manager = newManager(stepRepo, playerRepo)

        // Seed today's record at 49,800 credited so the manager initialises dailyCreditedTotal to
        // 49,800 on its first call — leaving exactly 200 of ceiling headroom. Seeding via Room
        // (rather than a warmup walk) keeps the StepRateLimiter / StepVelocityAnalyzer pristine, so
        // each subsequent 150-step credit passes anti-cheat cleanly and the ONLY variable under
        // test is the ceiling read-check-write.
        val today = manager.todayDate()
        runBlocking { stepRepo.updateDailySteps(today, sensorSteps = 49_800, creditedSteps = 49_800) }

        // 61_000ms apart so each credit lands in its own rate-limiter window (the limiter keys off
        // the supplied timestamp, not wall-clock).
        val tA = 1_710_000_000_000L
        val tB = tA + 61_000L

        // A reads the ceiling, then parks at the seam until released; B credits concurrently.
        val aAtSeam = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val firstCommit = AtomicBoolean(true)
        manager.onBeforeCreditCommit = {
            if (firstCommit.getAndSet(false)) {
                aAtSeam.countDown()
                releaseA.await()
            }
        }

        val threadA = Thread { runBlocking { manager.recordSteps(150, tA) } }
        val threadB = Thread { runBlocking { manager.recordSteps(150, tB) } }

        threadA.start()
        assertTrue(aAtSeam.await(5, TimeUnit.SECONDS), "coroutine A never reached the credit seam")
        // Start B while A is parked. With the Mutex, B blocks at withLock and only credits the
        // 50 of remaining headroom AFTER A commits → 50,000. Without it, B reads the SAME stale
        // 49,800 ceiling and credits a full 150 → A then commits 150 → 50,100 overshoot.
        threadB.start()
        Thread.sleep(300)
        releaseA.countDown()

        threadA.join(5_000)
        threadB.join(5_000)

        assertTrue(
            manager.getDailyCredited() <= DailyStepManager.DAILY_CEILING,
            "concurrent credits must not overshoot the 50k ceiling; got ${manager.getDailyCredited()}",
        )
    }
}
