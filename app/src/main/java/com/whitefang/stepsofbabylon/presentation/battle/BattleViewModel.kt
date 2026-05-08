package com.whitefang.stepsofbabylon.presentation.battle

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.data.BiomePreferences
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.OverdriveType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.domain.usecase.AwardBattleSteps
import com.whitefang.stepsofbabylon.domain.usecase.AwardWaveMilestone
import com.whitefang.stepsofbabylon.domain.usecase.ActivateOverdrive
import com.whitefang.stepsofbabylon.domain.usecase.ApplyCardEffects
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.domain.usecase.CheckTierUnlock
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import com.whitefang.stepsofbabylon.domain.usecase.UpdateBestWave
import com.whitefang.stepsofbabylon.service.MilestoneNotificationManager
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine
import com.whitefang.stepsofbabylon.presentation.battle.ui.BiomeTransitionInfo
import com.whitefang.stepsofbabylon.presentation.battle.UWSlotInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

@HiltViewModel
class BattleViewModel @Inject constructor(
    private val workshopRepository: WorkshopRepository,
    private val playerRepository: PlayerRepository,
    private val biomePreferences: BiomePreferences,
    private val uwRepository: UltimateWeaponRepository,
    private val cardRepository: CardRepository,
    private val dailyMissionDao: DailyMissionDao,
    private val dailyStepDao: DailyStepDao,
    private val playerProfileDao: PlayerProfileDao,
    private val appDatabase: AppDatabase,
    private val milestoneNotificationManager: MilestoneNotificationManager,
    private val rewardAdManager: RewardAdManager,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BattleUiState())
    val uiState: StateFlow<BattleUiState> = _uiState.asStateFlow()

    /**
     * Runs [block] inside a single Room transaction so the end-of-round write fan-out commits
     * atomically. Exposed as `@VisibleForTesting internal var` because the tests construct this
     * class with `mock<AppDatabase>()` — Mockito mocks of [AppDatabase] do not support Room's
     * `withTransaction` extension. Tests override this with a pass-through lambda so the
     * behaviour under test is the persistence logic, not Room's transaction machinery (which
     * is validated by instrumented tests, not JVM). Matches the idiom established by
     * [com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator] (B.2 PR 3).
     */
    @VisibleForTesting
    internal var runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block ->
        appDatabase.withTransaction { block() }
    }

    private val resolveStats = ResolveStats()
    private val calculateCost = CalculateUpgradeCost()
    private val updateBestWave = UpdateBestWave(playerRepository)
    private val checkTierUnlock = CheckTierUnlock()
    private val activateOverdriveUseCase = ActivateOverdrive()
    private val awardWaveMilestone = AwardWaveMilestone(playerRepository)
    private val applyCardEffects = ApplyCardEffects()
    private val awardBattleSteps = AwardBattleSteps(dailyStepDao, playerProfileDao, timeProvider)

    var resolvedStats: ResolvedStats = ResolvedStats(); private set
    var workshopLevels: Map<UpgradeType, Int> = emptyMap(); private set
    private val inRoundLevels = mutableMapOf<UpgradeType, Int>()
    private var engine: GameEngine? = null
    private var surfaceView: GameSurfaceView? = null
    var tier: Int = 1; private set
    private var equippedWeapons: List<OwnedWeapon> = emptyList()
    private var equippedCards: List<OwnedCard> = emptyList()
    private var cardCashBonus: Double = 0.0
    private var cardSecondWind: Double = 0.0
    private var roundEnded = false

    init {
        viewModelScope.launch {
            workshopLevels = workshopRepository.observeAllUpgrades().first()
            val profile = playerRepository.observeProfile().first()
            tier = profile.currentTier
            resolvedStats = resolveStats(workshopLevels)
            equippedWeapons = uwRepository.observeEquippedWeapons().first()

            equippedCards = cardRepository.observeEquippedCards().first()
            val cardResult = applyCardEffects(resolvedStats, equippedCards)
            resolvedStats = cardResult.stats
            cardCashBonus = cardResult.cashBonusPercent
            cardSecondWind = cardResult.secondWindHpPercent

            val biome = Biome.forTier(tier)
            val transition = if (!biomePreferences.hasSeenBiome(biome)) BiomeTransitionInfo(biome, profile.totalStepsEarned) else null

            _uiState.update {
                it.copy(maxHp = resolvedStats.maxHealth, currentHp = resolvedStats.maxHealth, isLoading = false,
                    biomeTransition = transition, stepBalance = profile.stepBalance, adRemoved = profile.adRemoved)
            }
        }
    }

    fun dismissBiomeTransition() {
        val biome = _uiState.value.biomeTransition?.biome ?: return
        biomePreferences.markBiomeSeen(biome)
        _uiState.update { it.copy(biomeTransition = null) }
    }

    fun startPollingEngine(engine: GameEngine, surfaceView: GameSurfaceView) {
        this.engine = engine; this.surfaceView = surfaceView
        engine.setStats(resolvedStats); engine.initUWs(equippedWeapons)
        engine.secondWindHpPercent = cardSecondWind; engine.cashBonusPercent = cardCashBonus
        wireStepRewardCallback(engine)
        roundEnded = false
        viewModelScope.launch {
            while (true) {
                delay(200)
                val eng = this@BattleViewModel.engine ?: break
                val zig = eng.ziggurat ?: continue
                val spawner = eng.waveSpawner
                _uiState.update {
                    it.copy(
                        currentWave = spawner?.currentWave ?: 1,
                        currentHp = zig.currentHp, maxHp = zig.maxHp,
                        cash = eng.cash, enemyCount = spawner?.enemiesAlive ?: 0,
                        wavePhase = spawner?.phase?.name ?: "",
                        uwSlots = eng.uwStates.map { uw ->
                            UWSlotInfo(uw.type.name, uw.cooldownRemaining, uw.type.cooldownAtLevel(uw.level), uw.cooldownRemaining <= 0f)
                        },
                        activeOverdriveType = eng.activeOverdrive,
                        overdriveTimeRemaining = eng.overdriveTimeRemaining,
                    )
                }
                if (eng.roundOver && !roundEnded) { endRound(); break }
            }
        }
    }

    private fun endRound() {
        if (roundEnded) return
        roundEnded = true
        val eng = engine ?: return
        val wave = eng.waveSpawner?.currentWave ?: 1
        viewModelScope.launch { runEndRoundPersistence(eng, wave) }
    }

    /**
     * Runs the end-of-round persistence fan-out and pushes the post-round UI state.
     *
     * **Resilience (RO-03, B.3 PR 1):** Each write / notification is isolated in its own
     * `runCatching { }.onFailure { Log.w }` block so a single Room or notification-manager
     * exception cannot leave the player on a frozen battle screen with no post-round overlay.
     * Writes whose results feed the [RoundEndState] ([updateBestWave], [awardWaveMilestone],
     * [playerRepository.updateHighestUnlockedTier]) fall back to safe defaults on failure so
     * the UI push always runs.
     *
     * **Atomicity (RO-02, B.2 PR 5):** All 5 SQLite writes now commit inside a single Room
     * transaction via [runInTransaction]. This gives external readers (e.g. Flow-based reactive
     * reads in other ViewModels) "all-or-nothing" visibility of the end-of-round state instead
     * of being able to observe a partially-applied fan-out. The outer [runCatching] around
     * the transaction preserves the RO-03 guarantee even if Room's infrastructure itself
     * throws (disk full, SQLCipher decrypt failure, etc.) — the UI push still runs.
     *
     * Non-SQLite side effects stay *outside* the transaction: the milestone notification
     * ([MilestoneNotificationManager.notifyNewBestWave]) posts through the Android
     * notification system, not Room, and the `_uiState.update` push is in-memory state.
     * Holding the DB lock across these would serve no purpose.
     */
    private suspend fun runEndRoundPersistence(eng: GameEngine, wave: Int) {
        // Locals captured by the transaction block so the post-transaction notification and
        // UI push can read the computed values. Written under per-write runCatching inside
        // the tx; safe-default on any failure so the UI push always produces a RoundEndState.
        var isNewRecord = false
        var previousBest = 0
        var psAwarded = 0
        var newTier: Int? = null

        // All 5 SQLite writes commit atomically in a single Room transaction. The outer
        // runCatching guards against Room infrastructure failures (e.g. withTransaction itself
        // throwing) so the UI push below still runs — RO-03 preservation trumps RO-02 when they
        // conflict. Per-write runCatching inside keeps individual failures from short-circuiting
        // later writes (original B.3 PR 1 behaviour, preserved here).
        runCatching {
            runInTransaction {
                // Write 1: best wave per tier. Result feeds the UI push below.
                val bestWaveResult = runCatching { updateBestWave(tier, wave) }
                    .onFailure { Log.w(TAG, "endRound: updateBestWave failed", it) }
                    .getOrNull()
                isNewRecord = bestWaveResult?.isNewRecord == true
                previousBest = bestWaveResult?.previousBest ?: 0

                // Write 2: award wave-milestone power stones (only on a new record).
                psAwarded = if (isNewRecord) {
                    runCatching { awardWaveMilestone(wave) }
                        .onFailure { Log.w(TAG, "endRound: awardWaveMilestone failed", it) }
                        .getOrDefault(0)
                } else 0

                // Write 3: highest-unlocked-tier advance. Wrapped so a profile-read failure or a
                // DB write failure on updateHighestUnlockedTier cannot skip the UI push below.
                newTier = runCatching {
                    val profile = playerRepository.observeProfile().first()
                    val computed = checkTierUnlock(profile.bestWavePerTier, profile.highestUnlockedTier)
                    if (computed != null) playerRepository.updateHighestUnlockedTier(computed)
                    computed
                }
                    .onFailure { Log.w(TAG, "endRound: updateHighestUnlockedTier failed", it) }
                    .getOrNull()

                // Write 4: all-time battle stats. Previously wrapped in an ad-hoc try/catch
                // swallow (pre-B.3 PR 1); normalised to runCatching + Log.w for consistency.
                runCatching {
                    playerRepository.incrementBattleStats(1, eng.totalEnemiesKilled.toLong(), eng.totalCashEarned)
                }.onFailure { Log.w(TAG, "endRound: incrementBattleStats failed", it) }

                // Write 5: daily-mission progress (battle missions). Same normalisation as #4.
                runCatching {
                    val today = timeProvider.today().toString()
                    val missions = dailyMissionDao.getByDateOnce(today)
                    for (m in missions) {
                        if (m.claimed || m.completed) continue
                        when (m.missionType) {
                            DailyMissionType.REACH_WAVE_30.name -> {
                                val newProgress = maxOf(m.progress, wave)
                                dailyMissionDao.updateProgress(m.id, newProgress, newProgress >= m.target)
                            }
                            DailyMissionType.KILL_500_ENEMIES.name -> {
                                val newProgress = m.progress + eng.totalEnemiesKilled
                                dailyMissionDao.updateProgress(m.id, newProgress, newProgress >= m.target)
                            }
                        }
                    }
                }.onFailure { Log.w(TAG, "endRound: updateDailyMissionProgress failed", it) }
            }
        }.onFailure { Log.w(TAG, "endRound: transaction failed", it) }

        // ---- Post-transaction side effects. SQLite locks released above. ----

        // New-record notification — best-effort; Android notification system, not SQLite.
        if (isNewRecord) {
            runCatching {
                milestoneNotificationManager.notifyNewBestWave(
                    wave,
                    Biome.forTier(tier).name.replace("_", " "),
                )
            }.onFailure { Log.w(TAG, "endRound: notifyNewBestWave failed", it) }
        }

        // UI state push — MUST run regardless of the above writes so the post-round overlay
        // appears. This is the critical user-facing payoff of RO-03.
        _uiState.update {
            it.copy(
                isPaused = false,
                showUpgradeMenu = false,
                showOverdriveMenu = false,
                roundEndState = RoundEndState(
                    wave,
                    eng.totalEnemiesKilled,
                    eng.totalCashEarned,
                    eng.elapsedTimeSeconds,
                    isNewRecord,
                    previousBest,
                    newTier,
                    psAwarded,
                    stepsEarned = it.stepsEarnedThisRound,
                    adRemoved = it.adRemoved,
                ),
            )
        }
    }

    fun quitRound() { val eng = engine ?: return; eng.roundOver = true; endRound() }

    /**
     * Wires the engine's per-kill Step reward callback to [awardBattleSteps]
     * and forwards credited amounts into [BattleUiState]. Exposed at package
     * visibility so tests can exercise the callback without starting the
     * polling loop.
     */
    @androidx.annotation.VisibleForTesting
    internal fun wireStepRewardCallback(engine: GameEngine) {
        engine.onStepReward = { amount, x, y ->
            viewModelScope.launch {
                val credited = awardBattleSteps(amount)
                if (credited > 0L) {
                    _uiState.update { s ->
                        s.copy(
                            stepsEarnedThisRound = s.stepsEarnedThisRound + credited,
                            stepBalance = s.stepBalance + credited,
                        )
                    }
                    // Spawn the floating "+N Step" indicator only when credit
                    // actually went through. A capped kill (credited == 0) must
                    // not show a misleading indicator; the frozen HUD counter
                    // at DAILY_BATTLE_STEP_CAP already communicates the gate.
                    engine.effectEngine?.addEffect(
                        com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText(
                            x = x,
                            y = y,
                            text = "+$credited Step",
                            color = com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText.STEP_COLOR,
                        ),
                    )
                }
            }
        }
    }

    fun playAgain() {
        roundEnded = false; inRoundLevels.clear()
        resolvedStats = resolveStats(workshopLevels)
        val cardResult = applyCardEffects(resolvedStats, equippedCards)
        resolvedStats = cardResult.stats
        cardCashBonus = cardResult.cashBonusPercent
        cardSecondWind = cardResult.secondWindHpPercent
        _uiState.update { BattleUiState(maxHp = resolvedStats.maxHealth, currentHp = resolvedStats.maxHealth,
            speedMultiplier = it.speedMultiplier, isLoading = false, stepBalance = it.stepBalance, adRemoved = it.adRemoved) }
        surfaceView?.configure(resolvedStats, tier, workshopLevels)
        engine?.initUWs(equippedWeapons)
        engine?.secondWindHpPercent = cardSecondWind; engine?.cashBonusPercent = cardCashBonus
        val eng = engine ?: return; val sv = surfaceView ?: return
        startPollingEngine(eng, sv)
    }

    override fun onCleared() {
        engine?.onStepReward = null
        super.onCleared()
    }

    fun activateOverdrive(type: OverdriveType) {
        val state = _uiState.value
        val result = activateOverdriveUseCase(type, state.stepBalance, state.overdriveUsed)
        if (result !is ActivateOverdrive.Result.Success) return
        viewModelScope.launch {
            playerRepository.spendSteps(type.stepCost)
            engine?.activateOverdrive(type, resolvedStats)
            _uiState.update { it.copy(overdriveUsed = true, showOverdriveMenu = false, stepBalance = it.stepBalance - type.stepCost) }
        }
    }

    fun activateUW(index: Int) { engine?.activateUW(index) }

    fun purchaseInRoundUpgrade(type: UpgradeType) {
        val eng = engine ?: return
        val currentLevel = inRoundLevels[type] ?: 0
        val maxLevel = type.config.maxLevel
        if (maxLevel != null && currentLevel >= maxLevel) return
        val cost = calculateCost(type, currentLevel)
        val freeLevel = workshopLevels[UpgradeType.FREE_UPGRADES] ?: 0
        val freeChance = min(freeLevel * 0.01, 0.25)
        val isFree = freeChance > 0 && Random.nextDouble() < freeChance
        if (!isFree && !eng.spendCash(cost)) return
        inRoundLevels[type] = currentLevel + 1
        resolvedStats = resolveStats(workshopLevels, inRoundLevels)
        eng.updateZigguratStats(resolvedStats)
        eng.soundManager?.play(com.whitefang.stepsofbabylon.presentation.audio.SoundEffect.UPGRADE_PURCHASE)
        _uiState.update { it.copy(inRoundLevels = inRoundLevels.toMap(), lastPurchaseFree = isFree) }
    }

    fun toggleUpgradeMenu() { _uiState.update { it.copy(showUpgradeMenu = !it.showUpgradeMenu, showOverdriveMenu = false) } }
    fun toggleOverdriveMenu() { _uiState.update { it.copy(showOverdriveMenu = !it.showOverdriveMenu, showUpgradeMenu = false) } }

    fun watchGemAd() {
        if (_uiState.value.roundEndState?.gemAdWatched == true) return
        viewModelScope.launch {
            val result = rewardAdManager.showRewardAd(AdPlacement.POST_ROUND_GEM)
            if (result is AdResult.Rewarded) {
                playerRepository.addGems(1)
                _uiState.update { it.copy(roundEndState = it.roundEndState?.copy(gemAdWatched = true)) }
            }
        }
    }

    fun watchPsAd() {
        if (_uiState.value.roundEndState?.psAdWatched == true) return
        viewModelScope.launch {
            val state = _uiState.value.roundEndState ?: return@launch
            val result = rewardAdManager.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)
            if (result is AdResult.Rewarded && state.powerStonesAwarded > 0) {
                playerRepository.addPowerStones(state.powerStonesAwarded.toLong())
                _uiState.update { it.copy(roundEndState = it.roundEndState?.copy(psAdWatched = true)) }
            }
        }
    }
    fun setSpeed(multiplier: Float) { _uiState.update { it.copy(speedMultiplier = multiplier) } }
    fun togglePause() { _uiState.update { it.copy(isPaused = !it.isPaused) } }
    fun pause() { _uiState.update { it.copy(isPaused = true) } }

    private companion object {
        private const val TAG = "BattleViewModel"
    }
}
