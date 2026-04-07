package com.whitefang.stepsofbabylon.presentation.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
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
import java.time.LocalDate

@HiltViewModel
class BattleViewModel @Inject constructor(
    private val workshopRepository: WorkshopRepository,
    private val playerRepository: PlayerRepository,
    private val biomePreferences: BiomePreferences,
    private val uwRepository: UltimateWeaponRepository,
    private val cardRepository: CardRepository,
    private val dailyMissionDao: DailyMissionDao,
    private val milestoneNotificationManager: MilestoneNotificationManager,
    private val rewardAdManager: RewardAdManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BattleUiState())
    val uiState: StateFlow<BattleUiState> = _uiState.asStateFlow()

    private val resolveStats = ResolveStats()
    private val calculateCost = CalculateUpgradeCost()
    private val updateBestWave = UpdateBestWave(playerRepository)
    private val checkTierUnlock = CheckTierUnlock()
    private val activateOverdriveUseCase = ActivateOverdrive()
    private val awardWaveMilestone = AwardWaveMilestone(playerRepository)
    private val applyCardEffects = ApplyCardEffects()

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
        if (roundEnded) return; roundEnded = true
        val eng = engine ?: return; val wave = eng.waveSpawner?.currentWave ?: 1
        viewModelScope.launch {
            val result = updateBestWave(tier, wave)
            val psAwarded = if (result.isNewRecord) awardWaveMilestone(wave) else 0
            if (result.isNewRecord) {
                milestoneNotificationManager.notifyNewBestWave(wave, Biome.forTier(tier).name.replace("_", " "))
            }
            val profile = playerRepository.observeProfile().first()
            val newTier = checkTierUnlock(profile.bestWavePerTier, profile.highestUnlockedTier)
            if (newTier != null) playerRepository.updateHighestUnlockedTier(newTier)
            _uiState.update {
                it.copy(isPaused = false, showUpgradeMenu = false, showOverdriveMenu = false,
                    roundEndState = RoundEndState(wave, eng.totalEnemiesKilled, eng.totalCashEarned,
                        eng.elapsedTimeSeconds, result.isNewRecord, result.previousBest, newTier, psAwarded, adRemoved = it.adRemoved))
            }
            // Update daily mission progress for battle missions
            try {
                playerRepository.incrementBattleStats(1, eng.totalEnemiesKilled.toLong(), eng.totalCashEarned)
            } catch (_: Exception) { /* best-effort */ }
            try {
                val today = LocalDate.now().toString()
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
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    fun quitRound() { val eng = engine ?: return; eng.roundOver = true; endRound() }

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
}
