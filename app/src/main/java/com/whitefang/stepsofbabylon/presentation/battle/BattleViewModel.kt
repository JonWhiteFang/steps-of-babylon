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
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
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
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.di.ApplicationScope
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
import kotlinx.coroutines.CoroutineScope
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
    private val cosmeticRepository: CosmeticRepository,
    private val dailyMissionDao: DailyMissionDao,
    private val dailyStepDao: DailyStepDao,
    private val playerProfileDao: PlayerProfileDao,
    private val appDatabase: AppDatabase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
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
    private var equippedCosmetics: Map<CosmeticCategory, CosmeticItem> = emptyMap()
    private var cardCashBonus: Double = 0.0
    private var cardSecondWind: Double = 0.0
    /**
     * Gem-reward multiplier from the equipped STEP_SURGE card (RO-08). Default `1.0` means
     * no STEP_SURGE equipped. Applied to the post-round watch-ad gem reward in [watchGemAd]
     * so the only in-game gem source benefits from the card. Computed by [ApplyCardEffects]
     * via `CardEffectResult.gemMultiplier`; refreshed in [init] and [playAgain]. Pre-RO-08
     * the field was computed but never read — STEP_SURGE was a no-op.
     */
    private var cardGemMultiplier: Double = 1.0
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
            cardGemMultiplier = cardResult.gemMultiplier

            // RO-07 C.2 PR 1: hydrate the equipped-cosmetic override map. Stored on the VM so
            // `startPollingEngine` (which may fire before or after this launch completes) can
            // propagate the map to the engine; we also push directly to `engine` here if it's
            // already attached — whichever write fires last wins and the subsequent
            // `engine.init()` (gated on isLoading=false below) reads the up-to-date map.
            equippedCosmetics = cosmeticRepository.observeEquipped().first().associateBy { it.category }
            engine?.cosmeticOverrides = equippedCosmetics

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
        // RO-07 C.2 PR 1: propagate the cosmetic override map. May be empty if the VM init
        // launch hasn't completed yet; the init path re-pushes on completion so the subsequent
        // `engine.init()` (fired by the surfaceView when isLoading becomes false) reads the
        // up-to-date set either way.
        engine.cosmeticOverrides = equippedCosmetics
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
        val eng = engine ?: return
        markEndedAndLaunchPersistence(viewModelScope, eng)
    }

    /**
     * Shared helper for the two end-of-round launch call sites: the normal [endRound] path
     * (polling loop + [quitRound]) which launches on [viewModelScope], and the mid-nav
     * [onCleared] path (RO-03 B.3 PR 2) which launches on [applicationScope] so the work
     * outlives the VM cancellation.
     *
     * Centralises the "claim the guard, mark the engine ended, compute the wave, launch the
     * persistence" sequence so both paths stay in sync. The `roundEnded` flag is set *before*
     * the launch so an overlapping call (e.g. polling loop firing one tick after quitRound)
     * short-circuits at [endRound]'s guard.
     */
    private fun markEndedAndLaunchPersistence(scope: CoroutineScope, eng: GameEngine) {
        roundEnded = true
        eng.roundOver = true
        val wave = eng.waveSpawner?.currentWave ?: 1
        scope.launch { runEndRoundPersistence(eng, wave) }
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
        cardGemMultiplier = cardResult.gemMultiplier
        _uiState.update { BattleUiState(maxHp = resolvedStats.maxHealth, currentHp = resolvedStats.maxHealth,
            speedMultiplier = it.speedMultiplier, isLoading = false, stepBalance = it.stepBalance, adRemoved = it.adRemoved) }
        surfaceView?.configure(resolvedStats, tier, workshopLevels)
        engine?.initUWs(equippedWeapons)
        engine?.secondWindHpPercent = cardSecondWind; engine?.cashBonusPercent = cardCashBonus
        val eng = engine ?: return; val sv = surfaceView ?: return
        startPollingEngine(eng, sv)
    }

    override fun onCleared() {
        // RO-03 B.3 PR 2: if the user navigates away mid-round (e.g. deep-link from a supply-drop
        // notification replaces the Battle route), `viewModelScope` is about to be cancelled and
        // any in-flight persistence would be discarded silently. Launch the end-of-round writes
        // on the application-scoped CoroutineScope instead so they survive VM teardown.
        //
        // Only fires when the round has actually made progress ([GameEngine.hasWaveProgress])
        // AND hasn't already ended — prevents a no-op persistence pass from a bounce-through
        // (user opens Battle then immediately backs out) and prevents double persistence from
        // a normal quitRound → onCleared sequence.
        val eng = engine
        if (eng != null && !roundEnded && eng.hasWaveProgress()) {
            markEndedAndLaunchPersistence(applicationScope, eng)
        }
        eng?.onStepReward = null
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
        // FREE_UPGRADES rolls against the combined Workshop + in-round level (RO-08): a
        // mid-round FREE_UPGRADES purchase contributes to its own subsequent free-roll chance.
        val freeLevel = (workshopLevels[UpgradeType.FREE_UPGRADES] ?: 0) +
            (inRoundLevels[UpgradeType.FREE_UPGRADES] ?: 0)
        val freeChance = min(freeLevel * 0.01, 0.25)
        val isFree = freeChance > 0 && Random.nextDouble() < freeChance
        if (!isFree && !eng.spendCash(cost)) return
        inRoundLevels[type] = currentLevel + 1
        resolvedStats = resolveStats(workshopLevels, inRoundLevels)
        eng.updateZigguratStats(resolvedStats)
        // Push combined effective levels for cash-utility upgrades (CASH_BONUS, CASH_PER_WAVE,
        // INTEREST, FREE_UPGRADES). The engine uses these for subsequent kill cash, wave-end
        // bonuses, and between-wave interest. Pre-RO-08 in-round purchases of these were dead
        // cash because the engine only ever read the workshop-level snapshot from init.
        eng.updateEffectiveLevels(combinedLevelsForCash())
        eng.soundManager?.play(com.whitefang.stepsofbabylon.presentation.audio.SoundEffect.UPGRADE_PURCHASE)
        _uiState.update { it.copy(inRoundLevels = inRoundLevels.toMap(), lastPurchaseFree = isFree) }
    }

    /**
     * Sums Workshop + in-round levels per upgrade type for the engine's cash-utility lookup.
     * Additive merge matches the documented stacking semantics ("All Workshop stats have
     * corresponding in-round upgrades"; for non-stat utilities this means level + level
     * additively). Stat-bearing upgrades go through [resolveStats] instead — this map is
     * consulted only by `GameEngine.wsLevel` for the four cash-related utilities.
     */
    private fun combinedLevelsForCash(): Map<UpgradeType, Int> {
        if (inRoundLevels.isEmpty()) return workshopLevels
        val combined = workshopLevels.toMutableMap()
        for ((type, level) in inRoundLevels) {
            combined[type] = (combined[type] ?: 0) + level
        }
        return combined
    }

    fun toggleUpgradeMenu() { _uiState.update { it.copy(showUpgradeMenu = !it.showUpgradeMenu, showOverdriveMenu = false) } }
    fun toggleOverdriveMenu() { _uiState.update { it.copy(showOverdriveMenu = !it.showOverdriveMenu, showUpgradeMenu = false) } }

    fun watchGemAd() {
        if (_uiState.value.roundEndState?.gemAdWatched == true) return
        viewModelScope.launch {
            val result = rewardAdManager.showRewardAd(AdPlacement.POST_ROUND_GEM)
            when (result) {
                is AdResult.Rewarded -> {
                    // RO-08: STEP_SURGE Epic card multiplies the post-round ad gem reward.
                    // Multiplier defaults to 1.0 when no STEP_SURGE is equipped, so the
                    // baseline reward stays at 1 gem. Reward floors at 1 even if a future
                    // multiplier somehow lands below it.
                    val gems = (1.0 * cardGemMultiplier).toLong().coerceAtLeast(1L)
                    playerRepository.addGems(gems)
                    _uiState.update { it.copy(roundEndState = it.roundEndState?.copy(gemAdWatched = true)) }
                }
                is AdResult.Cancelled ->
                    _uiState.update { it.copy(userMessage = "Ad cancelled. Try again.") }
                is AdResult.Error -> {
                    val msg = result.message.ifBlank { "Ad failed to load. Try again later." }
                    _uiState.update { it.copy(userMessage = msg) }
                }
            }
        }
    }

    fun watchPsAd() {
        if (_uiState.value.roundEndState?.psAdWatched == true) return
        viewModelScope.launch {
            val state = _uiState.value.roundEndState ?: return@launch
            val result = rewardAdManager.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)
            when (result) {
                is AdResult.Rewarded -> {
                    if (state.powerStonesAwarded > 0) {
                        playerRepository.addPowerStones(state.powerStonesAwarded.toLong())
                        _uiState.update { it.copy(roundEndState = it.roundEndState?.copy(psAdWatched = true)) }
                    }
                }
                is AdResult.Cancelled ->
                    _uiState.update { it.copy(userMessage = "Ad cancelled. Try again.") }
                is AdResult.Error -> {
                    val msg = result.message.ifBlank { "Ad failed to load. Try again later." }
                    _uiState.update { it.copy(userMessage = msg) }
                }
            }
        }
    }

    /** Clear the snackbar message after it has been shown. */
    fun clearMessage() { _uiState.update { it.copy(userMessage = null) } }
    fun setSpeed(multiplier: Float) { _uiState.update { it.copy(speedMultiplier = multiplier) } }
    fun togglePause() { _uiState.update { it.copy(isPaused = !it.isPaused) } }
    fun pause() { _uiState.update { it.copy(isPaused = true) } }

    private companion object {
        private const val TAG = "BattleViewModel"
    }
}
