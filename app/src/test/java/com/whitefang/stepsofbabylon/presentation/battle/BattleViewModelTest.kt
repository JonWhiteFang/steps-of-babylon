package com.whitefang.stepsofbabylon.presentation.battle

import com.whitefang.stepsofbabylon.data.BiomePreferences
import com.whitefang.stepsofbabylon.domain.model.*
import com.whitefang.stepsofbabylon.domain.usecase.AwardBattleSteps
import com.whitefang.stepsofbabylon.fakes.*
import com.whitefang.stepsofbabylon.service.MilestoneNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BattleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var workshopRepo: FakeWorkshopRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var uwRepo: FakeUltimateWeaponRepository
    private lateinit var cardRepo: FakeCardRepository
    private lateinit var adManager: FakeRewardAdManager
    private lateinit var dailyStepDao: FakeDailyStepDao
    private lateinit var awardBattleSteps: AwardBattleSteps
    private val biomePreferences = mock<BiomePreferences>()
    private val dailyMissionDao = mock<com.whitefang.stepsofbabylon.data.local.DailyMissionDao>()
    private val milestoneNotificationManager = mock<MilestoneNotificationManager>()

    @BeforeEach
    fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        workshopRepo = FakeWorkshopRepository()
        workshopRepo.upgrades.value = mapOf(UpgradeType.DAMAGE to 5, UpgradeType.HEALTH to 3)
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 10_000, currentTier = 1))
        uwRepo = FakeUltimateWeaponRepository()
        cardRepo = FakeCardRepository()
        adManager = FakeRewardAdManager()
        dailyStepDao = FakeDailyStepDao()
        awardBattleSteps = AwardBattleSteps(playerRepo, dailyStepDao)
        whenever(biomePreferences.hasSeenBiome(any())).thenReturn(true)
        whenever(dailyMissionDao.getByDateOnce(any())).thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = BattleViewModel(
        workshopRepo, playerRepo, biomePreferences, uwRepo, cardRepo,
        dailyMissionDao, dailyStepDao, milestoneNotificationManager, adManager,
    )

    @Test
    fun `init resolves stats from workshop levels`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.resolvedStats.damage > ZigguratBaseStats.BASE_DAMAGE)
        assertTrue(vm.resolvedStats.maxHealth > ZigguratBaseStats.BASE_HEALTH)
    }

    @Test
    fun `init applies card effects`() = runTest(dispatcher) {
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.GLASS_CANNON, 3, true))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.resolvedStats.damage > ZigguratBaseStats.BASE_DAMAGE)
    }

    @Test
    fun `biome transition shown for unseen biome`() = runTest(dispatcher) {
        whenever(biomePreferences.hasSeenBiome(any())).thenReturn(false)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.biomeTransition)
    }

    @Test
    fun `biome transition hidden for seen biome`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.biomeTransition)
    }

    @Test
    fun `setSpeed updates state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.setSpeed(2f)
        assertEquals(2f, vm.uiState.value.speedMultiplier)
    }

    @Test
    fun `togglePause toggles state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isPaused)
        vm.togglePause()
        assertTrue(vm.uiState.value.isPaused)
        vm.togglePause()
        assertFalse(vm.uiState.value.isPaused)
    }

    @Test
    fun `toggleUpgradeMenu toggles state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showUpgradeMenu)
        vm.toggleUpgradeMenu()
        assertTrue(vm.uiState.value.showUpgradeMenu)
    }

    @Test
    fun `watchGemAd credits gem on reward`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val initialGems = playerRepo.profile.value.gems
        vm.watchGemAd()
        advanceUntilIdle()
        assertEquals(initialGems + 1, playerRepo.profile.value.gems)
    }

    @Test
    fun `step balance shown in state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(10_000, vm.uiState.value.stepBalance)
    }

    @Test
    fun `adRemoved reflected in state`() = runTest(dispatcher) {
        playerRepo.profile.value = PlayerProfile(stepBalance = 10_000, adRemoved = true)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.adRemoved)
    }

    @Test
    fun `onStepReward credits wallet and updates stepsEarnedThisRound`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        vm.wireStepRewardCallback(engine)
        val initialBalance = playerRepo.profile.value.stepBalance

        repeat(5) { engine.onStepReward?.invoke(1L) }
        advanceUntilIdle()

        assertEquals(5L, vm.uiState.value.stepsEarnedThisRound)
        assertEquals(initialBalance + 5L, playerRepo.profile.value.stepBalance)
        assertEquals(initialBalance + 5L, vm.uiState.value.stepBalance)
    }

    @Test
    fun `onStepReward no-ops when daily cap already exhausted`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        // Exhaust today's cap up front.
        dailyStepDao.incrementBattleSteps(
            java.time.LocalDate.now().toString(),
            AwardBattleSteps.DAILY_BATTLE_STEP_CAP,
        )
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        vm.wireStepRewardCallback(engine)
        val initialBalance = playerRepo.profile.value.stepBalance

        engine.onStepReward?.invoke(10L)
        advanceUntilIdle()

        assertEquals(0L, vm.uiState.value.stepsEarnedThisRound)
        assertEquals(initialBalance, playerRepo.profile.value.stepBalance)
    }

    @Test
    fun `onStepReward partial credit when remaining is less than reward`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        // 3 remaining in the cap.
        dailyStepDao.incrementBattleSteps(
            java.time.LocalDate.now().toString(),
            AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 3L,
        )
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        vm.wireStepRewardCallback(engine)
        val initialBalance = playerRepo.profile.value.stepBalance

        engine.onStepReward?.invoke(10L)
        advanceUntilIdle()

        assertEquals(3L, vm.uiState.value.stepsEarnedThisRound)
        assertEquals(initialBalance + 3L, playerRepo.profile.value.stepBalance)
    }
}
