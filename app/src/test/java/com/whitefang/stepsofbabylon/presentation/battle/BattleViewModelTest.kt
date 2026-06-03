package com.whitefang.stepsofbabylon.presentation.battle

import com.whitefang.stepsofbabylon.data.BiomePreferences
import com.whitefang.stepsofbabylon.domain.model.*
import com.whitefang.stepsofbabylon.domain.usecase.AwardBattleSteps
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.fakes.*
import com.whitefang.stepsofbabylon.service.MilestoneNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var labRepo: FakeLabRepository
    private lateinit var adManager: FakeRewardAdManager
    private lateinit var dailyStepDao: FakeDailyStepDao
    private lateinit var applicationScope: CoroutineScope
    private lateinit var cosmeticRepo: FakeCosmeticRepository
    private val biomePreferences = mock<BiomePreferences>()
    private val dailyMissionDao = mock<com.whitefang.stepsofbabylon.data.local.DailyMissionDao>()
    private val playerProfileDao = mock<com.whitefang.stepsofbabylon.data.local.PlayerProfileDao>()
    private val appDatabase = mock<com.whitefang.stepsofbabylon.data.local.AppDatabase>()
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
        cosmeticRepo = FakeCosmeticRepository()
        labRepo = FakeLabRepository()
        // Link the DAO to playerRepo so the VM's internal AwardBattleSteps (post-B.2 PR 2 goes
        // through DailyStepDao.creditBattleStepsAtomic) still surfaces wallet changes via the
        // existing FakePlayerRepository.profile flow.
        dailyStepDao = FakeDailyStepDao(linkedPlayer = playerRepo)
        whenever(biomePreferences.hasSeenBiome(any())).thenReturn(true)
        whenever(dailyMissionDao.getByDateOnce(any())).thenReturn(emptyList())
        // Application-scoped CoroutineScope for B.3 PR 2 onCleared tests. Bound to the test
        // dispatcher so `advanceUntilIdle()` drains launches made on it. SupervisorJob so a
        // child failure doesn't kill the scope for later tests (mirrors prod semantics).
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm(timeProvider: com.whitefang.stepsofbabylon.domain.time.TimeProvider = com.whitefang.stepsofbabylon.data.time.SystemTimeProvider()) = BattleViewModel(
        workshopRepo, playerRepo, biomePreferences, uwRepo, cardRepo, cosmeticRepo, labRepo,
        dailyMissionDao, dailyStepDao, playerProfileDao, appDatabase, applicationScope, milestoneNotificationManager, adManager,
        timeProvider,
    ).apply {
        // B.2 PR 5: override the transaction seam with a direct pass-through so tests exercise
        // the persistence logic, not Room's withTransaction machinery (which can't run against
        // a mock<AppDatabase>()).
        runInTransaction = { block -> block() }
    }

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

    // ---- RO-11 #B.1: WAVE_SKIP lab research opens rounds at a higher wave ----

    @Test
    fun `RO11 init reads WAVE_SKIP and exposes startWave as 1 plus level`() = runTest(dispatcher) {
        // L0 baseline is implicitly covered by every other test in this file (all of which
        // leave WAVE_SKIP at 0 and never read `startWave`, but the field defaults to 1).
        // Here we set L5 → expect startWave = 6, which is what BattleViewModel.playAgain
        // pushes into surfaceView.configure(…, startWave) and thence into GameEngine.init.
        labRepo.levels.value = ResearchType.entries.associateWith { 0 } +
            (ResearchType.WAVE_SKIP to 5)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(
            6,
            vm.startWave,
            "WAVE_SKIP L5 must surface as startWave 6 (1 + level) for BattleScreen to push into GameEngine.init",
        )
    }

    // ---- V1X-15b: ENEMY_INTEL level plumbs through to the engine overlays ----

    @Test
    fun `V1X15b applyResearchParams pushes ENEMY_INTEL level onto the engine`() = runTest(dispatcher) {
        // ENEMY_INTEL L7 must reach GameEngine.enemyIntelLevel so the render-time L1/L5/L10
        // overlay gates fire. Asserted via the extracted applyResearchParams helper to avoid
        // driving the infinite polling loop.
        labRepo.levels.value = ResearchType.entries.associateWith { 0 } +
            (ResearchType.ENEMY_INTEL to 7)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        vm.applyResearchParams(engine)

        assertEquals(
            7,
            engine.enemyIntelLevel,
            "ENEMY_INTEL level must be pushed onto the engine to gate the L1/L5/L10 overlays",
        )
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

    // -------- RO-08 #4: STEP_SURGE card multiplies the post-round watch-ad gem reward --------

    @Test
    fun `RO08 STEP_SURGE level 1 doubles the watchGemAd reward`() = runTest(dispatcher) {
        // STEP_SURGE level 1 → effectAtLevel(1) = 2.0 → gemMultiplier 2.0×.
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.STEP_SURGE, 1, true))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val initialGems = playerRepo.profile.value.gems

        vm.watchGemAd()
        advanceUntilIdle()

        assertEquals(
            initialGems + 2L,
            playerRepo.profile.value.gems,
            "STEP_SURGE Lv1 (2× gemMultiplier) must double the 1-gem ad reward to 2",
        )
    }

    @Test
    fun `RO08 STEP_SURGE level 5 quadruples the watchGemAd reward`() = runTest(dispatcher) {
        // STEP_SURGE level 5 → effectAtLevel(5) = 4.0 → gemMultiplier 4.0×.
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.STEP_SURGE, 5, true))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val initialGems = playerRepo.profile.value.gems

        vm.watchGemAd()
        advanceUntilIdle()

        assertEquals(
            initialGems + 4L,
            playerRepo.profile.value.gems,
            "STEP_SURGE Lv5 (4× gemMultiplier) must quadruple the 1-gem ad reward to 4",
        )
    }

    // -------- PR A: ad-error UX (snackbar wiring on Cancelled / Error) --------

    @Test
    fun `watchGemAd Cancelled surfaces snackbar message and does not credit gem`() = runTest(dispatcher) {
        adManager.nextResult = AdResult.Cancelled
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val initialGems = playerRepo.profile.value.gems

        vm.watchGemAd()
        advanceUntilIdle()

        assertEquals(initialGems, playerRepo.profile.value.gems, "no credit on Cancelled")
        assertEquals(
            "Ad cancelled. Try again.",
            vm.uiState.value.userMessage,
            "user-visible snackbar message on Cancelled (PR A: ad-error UX)",
        )
    }

    @Test
    fun `watchGemAd Error surfaces the adapter message verbatim and does not credit gem`() = runTest(dispatcher) {
        adManager.nextResult = AdResult.Error("No ad available")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val initialGems = playerRepo.profile.value.gems

        vm.watchGemAd()
        advanceUntilIdle()

        assertEquals(initialGems, playerRepo.profile.value.gems, "no credit on Error")
        assertEquals(
            "No ad available",
            vm.uiState.value.userMessage,
            "AdResult.Error.message surfaces verbatim when non-blank (PR A: ad-error UX)",
        )
    }

    @Test
    fun `watchPsAd Cancelled surfaces snackbar message and does not credit power stones`() = runTest(dispatcher) {
        // Need a roundEndState for watchPsAd to enter its body. Use the existing endRound
        // plumbing to land one with powerStonesAwarded == 0 (no actual reward to skip on the
        // Cancelled branch — the snackbar should still appear).
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        installEngineForEndRound(vm)
        vm.quitRound()
        advanceUntilIdle()

        adManager.nextResult = AdResult.Cancelled
        val initialPs = playerRepo.profile.value.powerStones

        vm.watchPsAd()
        advanceUntilIdle()

        assertEquals(initialPs, playerRepo.profile.value.powerStones, "no credit on Cancelled")
        assertEquals(
            "Ad cancelled. Try again.",
            vm.uiState.value.userMessage,
            "user-visible snackbar message on Cancelled (PR A: ad-error UX)",
        )
    }

    @Test
    fun `watchPsAd Error with blank message falls back to a generic snackbar`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        installEngineForEndRound(vm)
        vm.quitRound()
        advanceUntilIdle()

        adManager.nextResult = AdResult.Error("")
        vm.watchPsAd()
        advanceUntilIdle()

        assertEquals(
            "Ad failed to load. Try again later.",
            vm.uiState.value.userMessage,
            "blank Error.message must not surface as an empty snackbar (PR A: ad-error UX)",
        )
    }

    @Test
    fun `clearMessage nulls userMessage`() = runTest(dispatcher) {
        adManager.nextResult = AdResult.Cancelled
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.watchGemAd()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.userMessage)

        vm.clearMessage()
        advanceUntilIdle()

        assertNull(
            vm.uiState.value.userMessage,
            "clearMessage must null the field so the snackbar shows once per event (PR A)",
        )
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
        val initialBalance = playerRepo.profile.value.stepBalance

        repeat(5) { vm.handleSimulationEvent(engine, SimulationEvent.StepReward(1L, 0f, 0f)) }
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
        val initialBalance = playerRepo.profile.value.stepBalance

        vm.handleSimulationEvent(engine, SimulationEvent.StepReward(10L, 0f, 0f))
        advanceUntilIdle()

        assertEquals(0L, vm.uiState.value.stepsEarnedThisRound)
        assertEquals(initialBalance, playerRepo.profile.value.stepBalance)
    }

    @Test
    fun `A_7 - no floating text spawned when step reward is fully capped`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        dailyStepDao.incrementBattleSteps(
            java.time.LocalDate.now().toString(),
            AwardBattleSteps.DAILY_BATTLE_STEP_CAP,
        )
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        // Wire up an EffectEngine we can inspect afterwards. The production
        // path sets this inside GameEngine.initSurfaceView; here we reach in
        // directly via reflection-free test setup.
        val fx = com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine(reducedMotion = true)
        val fxField = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine::class.java
            .getDeclaredField("effectEngine")
            .apply { isAccessible = true }
        fxField.set(engine, fx)

        vm.handleSimulationEvent(engine, SimulationEvent.StepReward(10L, 100f, 200f))
        advanceUntilIdle()

        // credited == 0 path must not spawn a step-reward FloatingText.
        // addEffect() writes to pendingEffects until the next update() tick,
        // so we inspect both fields to be robust.
        fun pendingAndActive(fxEngine: com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine): Int {
            val cls = com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine::class.java
            val pending = cls.getDeclaredField("pendingEffects").apply { isAccessible = true }.get(fxEngine) as List<*>
            val active = cls.getDeclaredField("effects").apply { isAccessible = true }.get(fxEngine) as List<*>
            return pending.size + active.size
        }
        assertEquals(0, pendingAndActive(fx),
            "capped kill must not spawn FloatingText")
    }

    @Test
    fun `A_7 - floating text spawned when step reward is partially credited`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        dailyStepDao.incrementBattleSteps(
            java.time.LocalDate.now().toString(),
            AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 3L,
        )
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        val fx = com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine(reducedMotion = true)
        val fxField = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine::class.java
            .getDeclaredField("effectEngine")
            .apply { isAccessible = true }
        fxField.set(engine, fx)

        vm.handleSimulationEvent(engine, SimulationEvent.StepReward(10L, 100f, 200f))
        advanceUntilIdle()

        fun pendingAndActive(fxEngine: com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine): Int {
            val cls = com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine::class.java
            val pending = cls.getDeclaredField("pendingEffects").apply { isAccessible = true }.get(fxEngine) as List<*>
            val active = cls.getDeclaredField("effects").apply { isAccessible = true }.get(fxEngine) as List<*>
            return pending.size + active.size
        }
        assertEquals(1, pendingAndActive(fx),
            "partial-credit kill should still spawn exactly one FloatingText")
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
        val initialBalance = playerRepo.profile.value.stepBalance

        vm.handleSimulationEvent(engine, SimulationEvent.StepReward(10L, 0f, 0f))

        advanceUntilIdle()

        assertEquals(3L, vm.uiState.value.stepsEarnedThisRound)
        assertEquals(initialBalance + 3L, playerRepo.profile.value.stepBalance)
    }

    @Test
    fun `timeProvider drives the battle-step date bucket`() = runTest(dispatcher) {
        // Proves B.1 PR 2 + PR 3 wiring: the VM's TimeProvider is propagated
        // into AwardBattleSteps, so the date bucket that receives the kill
        // reward is the fake clock's date, not real today.
        //
        // Setup: exhaust real-today's cap in the DAO. If the VM were reading
        // the real clock, the kill reward would see no cap space and credit
        // 0. With a FakeTimeProvider one day ahead, the kill writes to that
        // day's fresh bucket and credit goes through.
        val fakeDate = java.time.LocalDate.now().plusDays(1)
        val fakeClock = com.whitefang.stepsofbabylon.fakes.FakeTimeProvider(fixedDate = fakeDate)

        dailyStepDao.incrementBattleSteps(
            java.time.LocalDate.now().toString(),
            com.whitefang.stepsofbabylon.domain.usecase.AwardBattleSteps.DAILY_BATTLE_STEP_CAP,
        )

        val vm = createVm(timeProvider = fakeClock)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val initialBalance = playerRepo.profile.value.stepBalance
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()

        vm.handleSimulationEvent(engine, SimulationEvent.StepReward(10L, 0f, 0f))
        advanceUntilIdle()

        // Real-today's bucket is still at cap; fake-tomorrow's bucket got 10.
        assertEquals(
            com.whitefang.stepsofbabylon.domain.usecase.AwardBattleSteps.DAILY_BATTLE_STEP_CAP,
            dailyStepDao.getBattleStepsEarned(java.time.LocalDate.now().toString()),
            "real-today bucket must stay exhausted — VM must not have written here",
        )
        assertEquals(
            10L,
            dailyStepDao.getBattleStepsEarned(fakeDate.toString()),
            "fake-tomorrow bucket must receive the credit",
        )
        assertEquals(initialBalance + 10L, playerRepo.profile.value.stepBalance)
    }

    // -------- RO-03 endRound resilience tests --------

    /**
     * Drives `endRound` from a test context by reflection-setting the private `engine`
     * field, then calling the public `quitRound()`. Mirrors the pattern the A.7 tests use
     * for accessing `effectEngine` — avoids needing the full polling loop.
     */
    private fun installEngineForEndRound(vm: BattleViewModel): com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine {
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        val field = BattleViewModel::class.java.getDeclaredField("engine").apply { isAccessible = true }
        field.set(vm, engine)
        return engine
    }

    @Test
    fun `RO-03 - updateBestWave failure does not block later writes or UI push`() = runTest(dispatcher) {
        // A throwing PlayerRepository: updateBestWave (write 1) fails; other writes behave
        // normally. Without the runCatching wrapper this exception would propagate out of
        // the coroutine launched by endRound, skipping the _uiState.update push and the
        // two remaining writes. With the wrapper in place, the rest of the pipeline runs.
        val throwingPlayer = object : FakePlayerRepository(PlayerProfile(stepBalance = 10_000, currentTier = 1)) {
            override suspend fun updateBestWave(tier: Int, wave: Int) {
                throw RuntimeException("simulated DB failure on updateBestWave")
            }
        }
        val vm = BattleViewModel(
            workshopRepo, throwingPlayer, biomePreferences, uwRepo, cardRepo, cosmeticRepo, labRepo,
            dailyMissionDao, dailyStepDao, playerProfileDao, appDatabase, applicationScope, milestoneNotificationManager, adManager,
        ).apply { runInTransaction = { block -> block() } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm)
        vm.quitRound()
        advanceUntilIdle()

        // The UI push must have happened — this is the primary user-facing payoff.
        assertNotNull(
            vm.uiState.value.roundEndState,
            "RoundEndState must be set even when updateBestWave throws",
        )
        // A later write (incrementBattleStats) must still have run. Before RO-03 the
        // propagated exception would have short-circuited the launch before this point.
        assertEquals(
            1L,
            throwingPlayer.profile.value.totalRoundsPlayed,
            "incrementBattleStats must still run after an earlier write fails",
        )
    }

    @Test
    fun `RO-03 - all persistence failures still produce RoundEndState`() = runTest(dispatcher) {
        // Worst-case robustness: every wrapped write throws. The player still sees a
        // post-round overlay. Before RO-03 the first thrown exception would have
        // cancelled the launch, leaving the player on a frozen battle screen.
        val brokenPlayer = object : FakePlayerRepository(PlayerProfile(stepBalance = 10_000, currentTier = 1)) {
            override suspend fun updateBestWave(tier: Int, wave: Int) { throw RuntimeException("w1") }
            override suspend fun addPowerStones(amount: Long) { throw RuntimeException("w2") }
            override suspend fun updateHighestUnlockedTier(tier: Int) { throw RuntimeException("w3") }
            override suspend fun incrementBattleStats(rounds: Long, kills: Long, cash: Long) {
                throw RuntimeException("w4")
            }
        }
        val vm = BattleViewModel(
            workshopRepo, brokenPlayer, biomePreferences, uwRepo, cardRepo, cosmeticRepo, labRepo,
            dailyMissionDao, dailyStepDao, playerProfileDao, appDatabase, applicationScope, milestoneNotificationManager, adManager,
        ).apply { runInTransaction = { block -> block() } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm)
        vm.quitRound()
        advanceUntilIdle()

        val state = vm.uiState.value.roundEndState
        assertNotNull(state, "RoundEndState must be set even when every write fails")
        // Because updateBestWave failed, isNewBestWave falls back to false; previousBest
        // falls back to 0; psAwarded stays 0 (no wave-milestone awarded because not a
        // new record). tierUnlocked falls back to null. This is the safe-default contract.
        assertFalse(state!!.isNewBestWave, "isNewBestWave must default to false when updateBestWave throws")
        assertEquals(0, state.previousBest)
        assertEquals(0, state.powerStonesAwarded)
        assertNull(state.tierUnlocked)
    }

    @Test
    fun `RO-03 - roundEnded guard prevents double persistence on repeated quitRound`() = runTest(dispatcher) {
        // The polling loop and quitRound both call endRound; the in-flight
        // `roundEnded` boolean is what guarantees only one persistence pass. A
        // regression here would show up as double-incrementing totalRoundsPlayed.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm)

        vm.quitRound()
        advanceUntilIdle()
        val roundsAfterFirst = playerRepo.profile.value.totalRoundsPlayed

        vm.quitRound()
        advanceUntilIdle()
        val roundsAfterSecond = playerRepo.profile.value.totalRoundsPlayed

        assertEquals(1L, roundsAfterFirst, "first quitRound must persist exactly one round")
        assertEquals(
            roundsAfterFirst,
            roundsAfterSecond,
            "second quitRound must be a no-op — roundEnded guard must hold",
        )
    }

    // -------- B.2 PR 5 atomicity tests --------

    @Test
    fun `RO-02 B2PR5 - runEndRoundPersistence opens the transaction seam exactly once per round`() = runTest(dispatcher) {
        // One quitRound = one transaction opening. A second quitRound must not re-enter the
        // transaction (roundEnded guard lives outside the tx, fires first). Before B.2 PR 5
        // there was no transaction boundary at all; this test guards against accidental
        // removal of the withTransaction wrap.
        var transactionCalls = 0
        val vm = createVm().apply {
            runInTransaction = { block ->
                transactionCalls++
                block()
            }
        }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm)

        vm.quitRound()
        advanceUntilIdle()
        assertEquals(1, transactionCalls, "first quitRound must open exactly one transaction")

        vm.quitRound()
        advanceUntilIdle()
        assertEquals(
            1, transactionCalls,
            "second quitRound must be short-circuited by roundEnded guard — no second transaction",
        )
    }

    @Test
    fun `RO-02 B2PR5 - UI push runs AFTER the transaction commits`() = runTest(dispatcher) {
        // The post-round overlay push moved from the middle of runEndRoundPersistence (pre-PR 5)
        // to strictly after the transaction block completes (post-PR 5). This protects the RO-03
        // guarantee while ensuring the SQLite lock is released before any UI work happens.
        //
        // Assertion: capture uiState at the moment the transaction block returns. At that point
        // the RoundEndState must still be null (UI push has not run yet). After the whole call
        // completes, the RoundEndState must be populated.
        var uiStateAtTxClose: RoundEndState? = null
        lateinit var vm: BattleViewModel
        vm = createVm().apply {
            runInTransaction = { block ->
                block()
                uiStateAtTxClose = vm.uiState.value.roundEndState
            }
        }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm)
        vm.quitRound()
        advanceUntilIdle()

        assertNull(
            uiStateAtTxClose,
            "UI push must not run inside the transaction — SQLite lock must be released first",
        )
        assertNotNull(
            vm.uiState.value.roundEndState,
            "UI push must run after the transaction commits so the overlay appears",
        )
    }

    // -------- B.3 PR 2 onCleared guard tests --------

    /**
     * Drives `onCleared()` from a test context via reflection. [ViewModel.onCleared] is
     * `protected`, so direct invocation requires setAccessible. Mirrors the pattern already
     * used by [installEngineForEndRound] for the private `engine` field.
     */
    private fun invokeOnCleared(vm: BattleViewModel) {
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
        method.invoke(vm)
    }

    /**
     * Simulates a mid-round engine — in-progress wave with kills on the clock. Used by the
     * B.3 PR 2 tests that need [GameEngine.hasWaveProgress] to return `true` without running
     * the full polling loop.
     */
    private fun installEngineWithProgress(
        vm: BattleViewModel,
        elapsedSeconds: Float = 15f,
        kills: Int = 3,
    ): com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine {
        val engine = installEngineForEndRound(vm)
        // V1X-09 Phase 3: round counters now live on the engine's private `simulation`
        // (domain Simulation). Seed them via its public tickElapsed / recordEnemyKilled.
        val sim = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine::class.java
            .getDeclaredField("simulation").apply { isAccessible = true }.get(engine)
        val simCls = sim.javaClass
        simCls.getMethod("tickElapsed", Float::class.java).invoke(sim, elapsedSeconds)
        repeat(kills) { simCls.getMethod("recordEnemyKilled").invoke(sim) }
        return engine
    }

    @Test
    fun `B3PR2 - onCleared mid-round launches persistence on the application scope`() = runTest(dispatcher) {
        // User navigates away via a deep-link mid-battle. onCleared runs; viewModelScope is
        // about to be cancelled. Without B.3 PR 2 the in-flight round would be silently lost.
        // With the applicationScope launch, persistence still runs and the player keeps the
        // round's progress (best wave, kills, battle stats).
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineWithProgress(vm, elapsedSeconds = 30f, kills = 7)
        val roundsBefore = playerRepo.profile.value.totalRoundsPlayed

        invokeOnCleared(vm)
        advanceUntilIdle()

        assertEquals(
            roundsBefore + 1L,
            playerRepo.profile.value.totalRoundsPlayed,
            "incrementBattleStats must run — persistence survived VM cancellation",
        )
        assertEquals(
            7L,
            playerRepo.profile.value.totalEnemiesKilled,
            "kills must be credited to the all-time counter",
        )
    }

    @Test
    fun `B3PR2 - onCleared with no wave progress is a no-op`() = runTest(dispatcher) {
        // Bounce-through: user opens Battle screen, engine never ticks (elapsedTimeSeconds = 0,
        // totalEnemiesKilled = 0), user backs out immediately. Persisting here would record a
        // zero-progress round as totalRoundsPlayed += 1, which is misleading. The hasWaveProgress
        // guard in onCleared must short-circuit.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForEndRound(vm) // engine present, but elapsed=0 and kills=0
        val roundsBefore = playerRepo.profile.value.totalRoundsPlayed

        invokeOnCleared(vm)
        advanceUntilIdle()

        assertEquals(
            roundsBefore,
            playerRepo.profile.value.totalRoundsPlayed,
            "no-progress bounce-through must not persist a phantom round",
        )
    }

    @Test
    fun `B3PR2 - onCleared after quitRound is a no-op (roundEnded guard holds)`() = runTest(dispatcher) {
        // Normal quitRound path: endRound already ran; roundEnded = true. onCleared fires next
        // (typical navigation teardown). The roundEnded guard in onCleared must short-circuit
        // to prevent double persistence (quitRound: totalRoundsPlayed = 1; onCleared bug would
        // make it 2).
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineWithProgress(vm, elapsedSeconds = 20f, kills = 4)
        vm.quitRound()
        advanceUntilIdle()

        val roundsAfterQuit = playerRepo.profile.value.totalRoundsPlayed
        assertEquals(1L, roundsAfterQuit, "quitRound must persist exactly one round")

        invokeOnCleared(vm)
        advanceUntilIdle()

        assertEquals(
            roundsAfterQuit,
            playerRepo.profile.value.totalRoundsPlayed,
            "onCleared after quitRound must be a no-op — roundEnded guard must hold",
        )
    }

    // -------- C.2 PR 1 cosmetic renderer override pipeline tests --------

    @Test
    fun `C2PR1 - no equipped cosmetics keeps engine cosmeticOverrides empty`() = runTest(dispatcher) {
        // Regression guard: the pipeline is additive. Players with nothing equipped must see
        // exactly the same engine state as before this PR — empty map propagated by the VM
        // init-launch's `engine?.cosmeticOverrides = equippedCosmetics` write when engine is
        // attached before the launch completes (same timing relationship as startPollingEngine
        // in prod). engine.init() later uses the null-coalescing fallback to biome colors.
        cosmeticRepo.items.value = emptyList()
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        // Install engine BEFORE letting the init launch complete, so the VM's push in the
        // init block lands on this engine. Mirrors the prod ordering where startPollingEngine
        // (which attaches the engine) fires first, then the init launch's cosmetic load pushes.
        val engine = installEngineForEndRound(vm)
        advanceUntilIdle()

        assertTrue(
            engine.cosmeticOverrides.isEmpty(),
            "no cosmetics equipped → engine.cosmeticOverrides must stay empty (no-regression default)",
        )
    }

    @Test
    fun `C2PR1 - equipped ziggurat cosmetic propagates to engine cosmeticOverrides`() = runTest(dispatcher) {
        // Seed an equipped ZIGGURAT_SKIN cosmetic with an override palette. After VM init
        // completes the engine must see it in cosmeticOverrides keyed by category, so the
        // subsequent engine.init() can apply it when constructing ZigguratEntity.
        val jadeColors = listOf(0xFF104E3C.toInt(), 0xFF1A6B52.toInt(), 0xFF2A8F6E.toInt(), 0xFF3CAB82.toInt(), 0xFF54C79A.toInt())
        val jade = CosmeticItem(
            cosmeticId = "ZIG_JADE",
            category = CosmeticCategory.ZIGGURAT_SKIN,
            name = "Jade Ziggurat",
            description = "Test-only jade palette",
            priceGems = 100L,
            isOwned = true,
            isEquipped = true,
            overrideColors = jadeColors,
        )
        cosmeticRepo.items.value = listOf(jade)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        val engine = installEngineForEndRound(vm)
        advanceUntilIdle()

        val applied = engine.cosmeticOverrides[CosmeticCategory.ZIGGURAT_SKIN]
        assertNotNull(applied, "equipped ziggurat cosmetic must propagate to engine.cosmeticOverrides")
        assertEquals("ZIG_JADE", applied!!.cosmeticId)
        assertEquals(jadeColors, applied.overrideColors)
    }

    // -------- RO-12: in-round purchase preserves lab research + card stats --------

    /**
     * Variant of [installEngineForEndRound] that also seeds the engine's cash so an in-round
     * purchase actually executes (otherwise [BattleViewModel.purchaseInRoundUpgrade] returns
     * early on the `eng.spendCash(cost)` check). Reflectively writes the private `engine`
     * field on the VM, then seeds cash through the engine's private `simulation` (domain
     * [com.whitefang.stepsofbabylon.domain.battle.engine.Simulation], V1X-09 Phase 3) via its
     * public `creditCash` entry point.
     */
    private fun installEngineForPurchase(
        vm: BattleViewModel,
        seedCash: Long = 1_000_000L,
    ): com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine {
        val engine = com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine()
        val engineField = BattleViewModel::class.java.getDeclaredField("engine").apply { isAccessible = true }
        engineField.set(vm, engine)
        val sim = engine.javaClass.getDeclaredField("simulation").apply { isAccessible = true }.get(engine)
        sim.javaClass.getMethod("creditCash", Long::class.java).invoke(sim, seedCash)
        return engine
    }

    @Test
    fun `RO12 in-round HEALTH purchase preserves HEALTH_RESEARCH lab bonus`() = runTest(dispatcher) {
        // Direct regression for Bug 1 (RO-11-introduced). Pre-RO-12 the purchase site called
        // `resolveStats(workshopLevels, inRoundLevels)` without `labLevels`, so HEALTH_RESEARCH
        // L4's +20 % multiplier was silently dropped on the first in-round HEALTH purchase.
        // Post-fix the multiplier survives across the purchase: 1000 * 1.20 * 1.03 = 1236.
        workshopRepo.upgrades.value = mapOf(UpgradeType.HEALTH to 0)
        labRepo.levels.value = ResearchType.entries.associateWith { 0 } +
            (ResearchType.HEALTH_RESEARCH to 4)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Sanity at round-start: BASE * 1.20 (lab L4 +20 %), no in-round purchases yet.
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 1.20,
            vm.resolvedStats.maxHealth,
            0.01,
            "round-start lab bonus must already be applied (RO-11 #A.1)",
        )

        installEngineForPurchase(vm)
        vm.purchaseInRoundUpgrade(UpgradeType.HEALTH)
        advanceUntilIdle()

        // Post-purchase: BASE * 1.20 (lab) * 1.03 (in-round HEALTH L1 +3 %).
        // Pre-fix this would have been BASE * 1.03 (lab dropped) = ~1030.
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 1.20 * 1.03,
            vm.resolvedStats.maxHealth,
            0.01,
            "RO-12 Bug 1: in-round purchase must preserve HEALTH_RESEARCH lab bonus. " +
                "Pre-fix the labLevels arg was missing, dropping the lab multiplier mid-round.",
        )
    }

    @Test
    fun `RO12 in-round HEALTH purchase preserves WALKING_FORTRESS card bonus`() = runTest(dispatcher) {
        // Direct regression for Bug 2 (pre-existing, unmasked by RO-11). Pre-RO-12 the purchase
        // site called `resolveStats(...)` without re-applying `applyCardEffects`, so
        // WALKING_FORTRESS Lv 1's +50 % maxHealth multiplier was silently dropped on the first
        // in-round HEALTH purchase. Post-fix the multiplier survives: 1000 * 1.50 * 1.03 = 1545.
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.WALKING_FORTRESS, 1, true))
        workshopRepo.upgrades.value = mapOf(UpgradeType.HEALTH to 0)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Sanity at round-start: BASE * 1.50 (WALKING_FORTRESS Lv 1 +50 %).
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 1.50,
            vm.resolvedStats.maxHealth,
            0.01,
            "round-start card effect must already be applied",
        )

        installEngineForPurchase(vm)
        vm.purchaseInRoundUpgrade(UpgradeType.HEALTH)
        advanceUntilIdle()

        // Post-purchase: BASE * 1.50 (card) * 1.03 (in-round HEALTH L1 +3 %).
        // Pre-fix this would have been BASE * 1.03 (card dropped) = ~1030.
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 1.50 * 1.03,
            vm.resolvedStats.maxHealth,
            0.01,
            "RO-12 Bug 2: in-round purchase must preserve WALKING_FORTRESS card bonus. " +
                "Pre-fix applyCardEffects was not re-applied after resolveStats, dropping the " +
                "card multiplier mid-round.",
        )
    }

    @Test
    fun `RO12 in-round HEALTH purchase preserves both lab AND card bonuses stacked`() = runTest(dispatcher) {
        // Combined regression: HEALTH_RESEARCH L4 (+20 %) AND WALKING_FORTRESS Lv 1 (+50 %)
        // both survive the in-round purchase. This is the screenshot scenario where the
        // ziggurat HP drift was a multiplicative compound of the two missing multipliers.
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.WALKING_FORTRESS, 1, true))
        workshopRepo.upgrades.value = mapOf(UpgradeType.HEALTH to 0)
        labRepo.levels.value = ResearchType.entries.associateWith { 0 } +
            (ResearchType.HEALTH_RESEARCH to 4)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        installEngineForPurchase(vm)
        vm.purchaseInRoundUpgrade(UpgradeType.HEALTH)
        advanceUntilIdle()

        // Expected: BASE * 1.20 (lab) * 1.03 (in-round) * 1.50 (card) = 1854.
        // Pre-fix: BASE * 1.03 (only in-round) = 1030 -- a 824 HP drift in a single screen.
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 1.20 * 1.03 * 1.50,
            vm.resolvedStats.maxHealth,
            0.01,
            "RO-12: in-round purchase must preserve both lab + card bonuses simultaneously. " +
                "This is the closed-test-blocker drift the v5 screenshot captured.",
        )
    }
}
