package com.whitefang.stepsofbabylon.data.sensor

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeDao
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import com.whitefang.stepsofbabylon.service.SupplyDropNotificationManager
import com.whitefang.stepsofbabylon.service.WidgetUpdateHelper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DailyStepManagerTest {

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var dailyMissionDao: FakeDailyMissionDao
    private lateinit var widgetHelper: WidgetUpdateHelper
    private lateinit var workshopRepo: FakeWorkshopRepository
    private lateinit var manager: DailyStepManager

    private val baseTime = 1_710_000_000_000L
    // 61s apart ensures each call is in a fresh rate-limiter window
    private val minuteGap = 61_000L

    @BeforeEach
    fun setup() {
        playerRepo = FakePlayerRepository()
        stepRepo = FakeStepRepository()
        dailyMissionDao = FakeDailyMissionDao()
        widgetHelper = mock<WidgetUpdateHelper>()
        workshopRepo = FakeWorkshopRepository()

        manager = DailyStepManager(
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
            dailyMissionDao = dailyMissionDao,
            widgetUpdateHelper = widgetHelper,
            workshopRepository = workshopRepo,
        )
    }

    // --- R06: Widget balance ---

    @Test
    fun `widget receives real step balance after crediting`() = runTest {
        manager.recordSteps(100, baseTime)

        val balanceCaptor = argumentCaptor<Long>()
        verify(widgetHelper, atLeastOnce()).update(org.mockito.kotlin.any(), balanceCaptor.capture())
        assertEquals(100L, balanceCaptor.lastValue)
    }

    @Test
    fun `widget balance accumulates across multiple credits`() = runTest {
        manager.recordSteps(100, baseTime)
        manager.recordSteps(100, baseTime + minuteGap)

        val balanceCaptor = argumentCaptor<Long>()
        verify(widgetHelper, atLeastOnce()).update(org.mockito.kotlin.any(), balanceCaptor.capture())
        assertEquals(200L, balanceCaptor.lastValue)
    }

    // --- R07: Walking mission progress ---

    @Test
    fun `walking mission progress updates on step credit`() = runTest {
        val today = manager.todayDate()
        dailyMissionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.WALK_5000.name,
            target = 5000, rewardGems = 5,
        ))

        manager.recordSteps(100, baseTime)

        val missions = dailyMissionDao.getByDateOnce(today)
        assertEquals(100, missions[0].progress)
        assertFalse(missions[0].completed)
    }

    @Test
    fun `walking mission completes when target reached`() = runTest {
        val today = manager.todayDate()
        dailyMissionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.WALK_5000.name,
            target = 5000, rewardGems = 5,
        ))

        // Alternate 150/250 steps to avoid constant-rate velocity penalty (CV > 5%)
        // Each call in a fresh rate-limiter window (61s apart)
        var total = 0L
        var i = 0
        while (total < 5000) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }

        val missions = dailyMissionDao.getByDateOnce(today)
        assertTrue(missions[0].progress >= 5000)
        assertTrue(missions[0].completed)
    }

    @Test
    fun `battle mission is not updated by step credits`() = runTest {
        val today = manager.todayDate()
        dailyMissionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.REACH_WAVE_30.name,
            target = 30, rewardGems = 3,
        ))

        manager.recordSteps(100, baseTime)

        val missions = dailyMissionDao.getByDateOnce(today)
        assertEquals(0, missions[0].progress)
        assertFalse(missions[0].completed)
    }

    // --- R2-01: Activity-minute idempotency ---

    @Test
    fun `activity minutes credit correct step-equivalents`() = runTest {
        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)
        assertEquals(500L, playerRepo.getStepBalance())
    }

    @Test
    fun `duplicate activity-minute call produces zero additional credits`() = runTest {
        val minutes = mapOf("cycling" to 10)
        manager.recordActivityMinutes(minutes, 500)
        manager.recordActivityMinutes(minutes, 500)
        assertEquals(500L, playerRepo.getStepBalance())
    }

    @Test
    fun `incremental activity-minute call credits only delta`() = runTest {
        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)
        manager.recordActivityMinutes(mapOf("cycling" to 20), 900)
        assertEquals(900L, playerRepo.getStepBalance())
    }

    @Test
    fun `combined sensor and activity-minute credits respect 50k ceiling`() = runTest {
        // Credit 49,900 sensor steps (alternate 150/250 to avoid velocity penalty)
        var total = 0L
        var i = 0
        while (total < 49_900) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }
        val sensorBalance = playerRepo.getStepBalance()

        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)

        val finalBalance = playerRepo.getStepBalance()
        val activityCredited = finalBalance - sensorBalance
        assertEquals(DailyStepManager.DAILY_CEILING, finalBalance)
        assertTrue(activityCredited <= 100, "Activity credits should be capped by ceiling, got $activityCredited")
    }

    @Test
    fun `process restart does not re-credit activity minutes`() = runTest {
        val minutes = mapOf("cycling" to 10)
        manager.recordActivityMinutes(minutes, 500)
        assertEquals(500L, playerRepo.getStepBalance())

        // Simulate process restart: new manager, same repos
        val manager2 = DailyStepManager(
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
            workshopRepository = workshopRepo,
        )
        manager2.recordActivityMinutes(minutes, 500)
        assertEquals(500L, playerRepo.getStepBalance())
    }

    // --- R2-12: Activity-minute follow-on pipeline ---

    @Test
    fun `activity-minute credits trigger walking mission progress`() = runTest {
        val today = manager.todayDate()
        dailyMissionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.WALK_5000.name,
            target = 5000, rewardGems = 5,
        ))

        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)

        val missions = dailyMissionDao.getByDateOnce(today)
        assertEquals(500, missions[0].progress)
    }

    @Test
    fun `activity-minute credits trigger widget updates`() = runTest {
        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)

        verify(widgetHelper, atLeastOnce()).update(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `already completed mission is not re-updated`() = runTest {
        val today = manager.todayDate()
        dailyMissionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.WALK_5000.name,
            target = 5000, rewardGems = 5, progress = 5000, completed = true,
        ))

        manager.recordSteps(100, baseTime)

        val missions = dailyMissionDao.getByDateOnce(today)
        assertEquals(5000, missions[0].progress)
        assertTrue(missions[0].completed)
    }

    // --- A.6: Season Pass flags in background TrackDailyLogin call ---

    @Test
    fun `background pipeline grants Season Pass bonus Gems when walking 1000+ steps`() = runTest {
        // Arrange: Season Pass active with expiry in the far future
        playerRepo.updateSeasonPass(active = true, expiry = Long.MAX_VALUE)
        val gemsBefore = playerRepo.profile.value.gems

        // Act: walk enough to cross both the 1000-step PS threshold and trigger
        // the first-day streak reward. Alternate 150/250 to avoid velocity
        // penalty; space calls 61s apart for fresh rate-limiter windows.
        var total = 0L
        var i = 0
        while (total < 1_000) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }

        // Assert: day-1 streak reward (1 Gem) + Season Pass bonus (+10) = 11 Gems
        val gemsAfter = playerRepo.profile.value.gems
        assertEquals(11L, gemsAfter - gemsBefore,
            "Season Pass owner should receive streak (1) + daily bonus (10) = 11 Gems")
    }

    @Test
    fun `background pipeline grants baseline streak Gems without Season Pass`() = runTest {
        // Control: no Season Pass — same walking threshold, no +10 bonus
        val gemsBefore = playerRepo.profile.value.gems

        var total = 0L
        var i = 0
        while (total < 1_000) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }

        val gemsAfter = playerRepo.profile.value.gems
        assertEquals(1L, gemsAfter - gemsBefore,
            "Non-Season-Pass walker should receive only the day-1 streak Gem")
    }

    @Test
    fun `expired Season Pass does not grant daily bonus`() = runTest {
        // Season Pass was purchased but has since expired — must not leak bonus
        playerRepo.updateSeasonPass(active = true, expiry = 1L)
        val gemsBefore = playerRepo.profile.value.gems

        var total = 0L
        var i = 0
        while (total < 1_000) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }

        val gemsAfter = playerRepo.profile.value.gems
        assertEquals(1L, gemsAfter - gemsBefore,
            "Expired Season Pass should fall back to baseline streak Gem only")
    }

    // ---- RO-08 #1a: STEP_MULTIPLIER Workshop upgrade applies to walking step credit ----

    @Test
    fun `RO08 STEP_MULTIPLIER level 0 leaves credited steps unchanged`() = runTest {
        // Default upgrades map is empty in FakeWorkshopRepository, level 0 → multiplier 1.0×.
        manager.recordSteps(100, baseTime)
        // 150 step rate-limiter and velocity rules pass exactly 100 through; balance == 100.
        assertEquals(100L, playerRepo.getStepBalance())
    }

    @Test
    fun `RO08 STEP_MULTIPLIER level 50 grants a 50 percent walking bonus`() = runTest {
        // 50 levels × 0.01 per level = 0.50 bonus. 100 sensor steps → 150 credited.
        com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository::class.java // unused import suppress
        workshopRepo.upgrades.value =
            mapOf(com.whitefang.stepsofbabylon.domain.model.UpgradeType.STEP_MULTIPLIER to 50)

        manager.recordSteps(100, baseTime)

        assertEquals(
            150L,
            playerRepo.getStepBalance(),
            "STEP_MULTIPLIER level 50 must grant +50 % bonus (100 sensor → 150 credited)",
        )
    }

    @Test
    fun `RO08 STEP_MULTIPLIER caps at +100 percent regardless of level`() = runTest {
        // 200 levels × 0.01 = 2.0 → clamped to 1.0 cap → 2.0× total. 100 sensor → 200 credited.
        workshopRepo.upgrades.value =
            mapOf(com.whitefang.stepsofbabylon.domain.model.UpgradeType.STEP_MULTIPLIER to 200)

        manager.recordSteps(100, baseTime)

        assertEquals(
            200L,
            playerRepo.getStepBalance(),
            "STEP_MULTIPLIER must cap the bonus at +100 % (max 2.0× credited)",
        )
    }

    @Test
    fun `RO08 STEP_MULTIPLIER bonus is capped by the 50k daily ceiling`() = runTest {
        // Walk to within 100 of the cap. Then with a 1.5× multiplier, the bonus would push us
        // to 49,900 + 150 = 50,050 — but the ceiling clamps to exactly 50,000.
        var total = 0L
        var i = 0
        while (total < 49_900) {
            val delta = if (i % 2 == 0) 150L else 250L
            manager.recordSteps(delta, baseTime + i * minuteGap)
            total += delta
            i++
        }
        val balanceBeforeMultiplier = playerRepo.getStepBalance()

        workshopRepo.upgrades.value =
            mapOf(com.whitefang.stepsofbabylon.domain.model.UpgradeType.STEP_MULTIPLIER to 50)
        manager.recordSteps(100, baseTime + i * minuteGap)

        assertEquals(
            DailyStepManager.DAILY_CEILING,
            playerRepo.getStepBalance(),
            "STEP_MULTIPLIER bonus must respect the 50 k absolute daily ceiling",
        )
        assertTrue(
            playerRepo.getStepBalance() - balanceBeforeMultiplier <= 100,
            "ceiling cap must clamp the credit including the multiplier bonus",
        )
    }

    @Test
    fun `RO08 STEP_MULTIPLIER does NOT apply to activity minutes`() = runTest {
        // GDD §4.3 wording: "+1 % bonus steps earned from walking". Activity minutes are
        // converted from cycling / swimming / treadmill exercise sessions, not walking.
        // The multiplier path is intentionally restricted to recordSteps.
        workshopRepo.upgrades.value =
            mapOf(com.whitefang.stepsofbabylon.domain.model.UpgradeType.STEP_MULTIPLIER to 50)

        manager.recordActivityMinutes(mapOf("cycling" to 10), 500)

        assertEquals(
            500L,
            playerRepo.getStepBalance(),
            "Activity minutes must not receive the STEP_MULTIPLIER walking bonus",
        )
    }
}
