package com.whitefang.stepsofbabylon.presentation.stats

import androidx.lifecycle.SavedStateHandle
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var workshopRepo: FakeWorkshopRepository

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository()
        stepRepo = FakeStepRepository()
        workshopRepo = FakeWorkshopRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle()) =
        StatsViewModel(stepRepo, playerRepo, workshopRepo, handle)

    @Test
    fun `initial state populates`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `maps profile battle stats`() =
        runTest(dispatcher) {
            playerRepo.profile.value =
                PlayerProfile(
                    totalRoundsPlayed = 10,
                    totalEnemiesKilled = 500,
                    totalCashEarned = 9999,
                    totalGemsEarned = 50,
                    totalGemsSpent = 20,
                    gems = 30,
                    totalPowerStonesEarned = 15,
                    totalPowerStonesSpent = 5,
                    powerStones = 10,
                    bestWavePerTier = mapOf(1 to 25),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(10, state.totalRoundsPlayed)
            assertEquals(500, state.totalEnemiesKilled)
            assertEquals(30, state.currentGems)
            assertEquals(mapOf(1 to 25), state.bestWavePerTier)
        }

    @Test
    fun `builds 7 bars for week period`() =
        runTest(dispatcher) {
            val today = LocalDate.now()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            stepRepo.records.value =
                mapOf(
                    today.format(fmt) to DailyStepSummary(today.format(fmt), creditedSteps = 5000),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(7, vm.uiState.value.bars.size)
        }

    @Test
    fun `period switching changes bar count`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            vm.selectPeriod(StatsPeriod.MONTH)
            advanceUntilIdle()
            assertEquals(30, vm.uiState.value.bars.size)
        }

    @Test
    fun `workshop levels summed`() =
        runTest(dispatcher) {
            workshopRepo.upgrades.value = mapOf(UpgradeType.DAMAGE to 5, UpgradeType.ATTACK_SPEED to 3)
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(8, vm.uiState.value.totalWorkshopLevels)
        }

    // #30: the QUARTER branch re-parsed every history row's date inside each of 12 weekly
    // buckets (up to 12×90 LocalDate.parse calls per emission). The fix parses each row once.
    // It is behaviour-preserving — these tests pin the 12-bucket shape and the per-week sums so
    // the single-pass refactor cannot change what the user sees.

    @Test
    fun `builds 12 bars for quarter period`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            vm.selectPeriod(StatsPeriod.QUARTER)
            advanceUntilIdle()
            assertEquals(12, vm.uiState.value.bars.size)
        }

    @Test
    fun `R30 quarter buckets sum the week containing each record`() =
        runTest(dispatcher) {
            val today = LocalDate.now()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            // Put two records in THIS week (the last bucket) and one ~5 weeks back (an earlier bucket).
            val thisWeekA = today
            val thisWeekB =
                today
                    .minusDays(
                        1,
                    ).let { if (it.isBefore(today.with(java.time.DayOfWeek.MONDAY))) today else it }
            stepRepo.records.value =
                mapOf(
                    thisWeekA.format(fmt) to
                        DailyStepSummary(thisWeekA.format(fmt), creditedSteps = 5000, stepEquivalents = 1000),
                    today.minusDays(35).format(fmt) to
                        DailyStepSummary(today.minusDays(35).format(fmt), creditedSteps = 2000, stepEquivalents = 0),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            vm.selectPeriod(StatsPeriod.QUARTER)
            advanceUntilIdle()
            val bars = vm.uiState.value.bars
            assertEquals(12, bars.size)
            // Total credited across all buckets must equal the sum of all in-window records'
            // (sensorSteps + stepEquivalents) = creditedSteps. 5000 (this week) + 2000 (5 weeks ago).
            val totalCredited = bars.sumOf { it.sensorSteps + it.stepEquivalents }
            assertEquals(7000L, totalCredited, "every in-window record must land in exactly one weekly bucket")
            // The last bucket is the current week and must contain the 5000-credited record.
            assertEquals(
                5000L,
                bars.last().let {
                    it.sensorSteps + it.stepEquivalents
                },
                "this week's record lands in the final bucket",
            )
        }

    @Test
    fun `days active counted from history`() =
        runTest(dispatcher) {
            val today = LocalDate.now()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            stepRepo.records.value =
                mapOf(
                    today.format(fmt) to DailyStepSummary(today.format(fmt), creditedSteps = 5000),
                    today.minusDays(1).format(fmt) to
                        DailyStepSummary(today.minusDays(1).format(fmt), creditedSteps = 0),
                    today.minusDays(2).format(fmt) to
                        DailyStepSummary(today.minusDays(2).format(fmt), creditedSteps = 3000),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.daysActive)
        }

    // ---------------------------------------------------------------------------------------
    // #194 (UX-1) — a throwing source flow must surface an error state (not a silent infinite
    // spinner), and retry() must re-subscribe and recover. Pre-fix, combine completed
    // exceptionally and stateIn kept the initial isLoading=true forever. The retry hinges on
    // .catch living INSIDE flatMapLatest (a downstream .catch would make the error terminal and
    // retry() a no-op) — this pair pins that operator placement.
    // ---------------------------------------------------------------------------------------

    /** A player fake whose profile flow throws until [healed], to drive the #194 error path. */
    private class ThrowingPlayerRepository(
        initial: PlayerProfile,
    ) : FakePlayerRepository(initial) {
        @Volatile var healed = false

        override fun observeProfile(): Flow<PlayerProfile> =
            if (healed) super.observeProfile() else flow { throw RuntimeException("boom") }
    }

    @Test
    fun `R194 a throwing source flow surfaces an error state, not an infinite spinner`() =
        runTest(dispatcher) {
            playerRepo = ThrowingPlayerRepository(PlayerProfile())
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading, "must not be stuck loading after a source throws")
            assertNotNull(state.error, "a thrown source flow must surface an error message")
        }

    @Test
    fun `R194 retry re-subscribes and recovers after the source heals`() =
        runTest(dispatcher) {
            val throwing = ThrowingPlayerRepository(PlayerProfile(totalStepsEarned = 4242))
            playerRepo = throwing
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.error, "precondition: starts in the error state")

            // Heal the source and retry — the flow must re-subscribe and clear the error.
            throwing.healed = true
            vm.retry()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.error, "retry() must clear the error after the source heals")
            assertFalse(state.isLoading)
            assertEquals(4242, state.allTimeSteps, "recovered state must reflect the healed source data")
        }

    @Test
    fun `R234 selected period restores from a seeded SavedStateHandle`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle(mapOf("selectedPeriod" to StatsPeriod.MONTH))
            val vm = createVm(handle)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(
                StatsPeriod.MONTH,
                vm.uiState.value.selectedPeriod,
                "selected period must restore from SavedStateHandle (process-death survival)",
            )
        }

    @Test
    fun `R234 selectPeriod writes through to the SavedStateHandle`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = createVm(handle)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            vm.selectPeriod(StatsPeriod.MONTH)
            advanceUntilIdle()
            assertEquals(
                StatsPeriod.MONTH,
                handle["selectedPeriod"],
                "selectPeriod must persist to SavedStateHandle",
            )
        }
}
