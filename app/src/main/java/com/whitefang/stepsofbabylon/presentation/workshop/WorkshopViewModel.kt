package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.MissionRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.domain.usecase.DescribeUpgradeEffect
import com.whitefang.stepsofbabylon.domain.usecase.EvaluateUpgradeValue
import com.whitefang.stepsofbabylon.domain.usecase.PurchaseUpgrade
import com.whitefang.stepsofbabylon.domain.usecase.QuickInvest
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkshopViewModel
    @Inject
    constructor(
        private val workshopRepository: WorkshopRepository,
        private val playerRepository: PlayerRepository,
        private val missionRepository: MissionRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val calculateCost = CalculateUpgradeCost()
        private val purchaseUpgrade = PurchaseUpgrade(workshopRepository, calculateCost)
        private val quickInvest = QuickInvest(calculateCost)
        private val resolveStats = ResolveStats()
        private val describeUpgradeEffect = DescribeUpgradeEffect()
        private val evaluateUpgradeValue = EvaluateUpgradeValue()

        private val selectedCategory: StateFlow<UpgradeCategory> =
            savedStateHandle.getStateFlow(KEY_SELECTED_CATEGORY, UpgradeCategory.ATTACK)
        private val _processing = MutableStateFlow(false)
        private val _userMessage = MutableStateFlow<String?>(null)

        // #194: bump to re-subscribe the data flow after a load error (retry).
        private val _retry = MutableStateFlow(0)
        private val hiddenUpgrades = setOf(UpgradeType.STEP_MULTIPLIER, UpgradeType.RECOVERY_PACKAGES)
        private var allUpgrades: Map<UpgradeType, Int> = emptyMap()

        val uiState: StateFlow<WorkshopUiState> =
            _retry
                .flatMapLatest {
                    combine(
                        workshopRepository.observeAllUpgrades(),
                        playerRepository.observeWallet(),
                        selectedCategory,
                        _processing,
                        _userMessage,
                    ) { upgrades, wallet, category, processing, message ->
                        allUpgrades = upgrades
                        val stats = resolveStats(upgrades)
                        // R4-02b: filter by `isWorkshopVisible` flag in addition to the legacy `hiddenUpgrades`
                        // set so MULTISHOT/BOUNCE_SHOT (now Labs-research / in-round-Cash only) disappear from
                        // the Workshop screen. The DAO still seeds Workshop rows for these upgrade types so the
                        // ResolveStats `total(MULTISHOT/BOUNCE_SHOT)` lookup keeps reading 0 from Workshop +
                        // any in-round levels.
                        val filtered =
                            upgrades.filter { (type, _) ->
                                type.category == category && type !in hiddenUpgrades && type.isWorkshopVisible
                            }
                        // #29: value/Best-Buy data for the CURRENT tab's candidates (per-tab scoping, spec §5.2).
                        // Pass the FULL upgrade map so ResolveStats sees every stat; candidates = this tab's visible
                        // types. EvaluateUpgradeValue returns only the Δpower>0 ones, keyed back by type below.
                        val values =
                            evaluateUpgradeValue(upgrades, wallet.stepBalance, filtered.keys)
                                .associateBy { it.type }
                        WorkshopUiState(
                            upgrades =
                                filtered.map { (type, level) ->
                                    val maxLevel = type.config.maxLevel
                                    val isMaxed = maxLevel != null && level >= maxLevel
                                    val cost = if (isMaxed) 0L else calculateCost(type, level)
                                    UpgradeDisplayInfo(
                                        type = type,
                                        level = level,
                                        cost = cost,
                                        isMaxed = isMaxed,
                                        canAfford = !isMaxed && wallet.stepBalance >= cost,
                                        description = type.config.description,
                                        statValue = statValueFor(type, stats),
                                        // Per-row workshop-dimension Now→Next preview (intentional fan-out; pure + small N).
                                        nowNext = describeUpgradeEffect.workshopPreview(upgrades, type),
                                        value = values[type],
                                    )
                                },
                            stepBalance = wallet.stepBalance,
                            selectedCategory = category,
                            isLoading = false,
                            isProcessing = processing,
                            userMessage = message,
                        )
                    }
                        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
                        // flatMapLatest so retry() re-subscribes.
                        .catch { emit(WorkshopUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopUiState())

        /** #194: re-subscribe the data flow after a load error. */
        fun retry() {
            _retry.value++
        }

        fun selectCategory(category: UpgradeCategory) {
            savedStateHandle[KEY_SELECTED_CATEGORY] = category
        }

        fun purchase(type: UpgradeType) {
            if (_processing.value) return
            viewModelScope.launch {
                _processing.value = true
                try {
                    val level = allUpgrades[type] ?: 0
                    val maxLevel = type.config.maxLevel
                    if (maxLevel != null && level >= maxLevel) {
                        _userMessage.value = "Already at max level"
                        return@launch
                    }
                    val wallet = playerRepository.observeWallet().first()
                    val cost = calculateCost(type, level)
                    val success = purchaseUpgrade(type, level, wallet)
                    if (success) {
                        try {
                            val today = LocalDate.now().toString()
                            val missions = missionRepository.getMissionsForDate(today)
                            val m =
                                missions.find {
                                    it.type == DailyMissionType.SPEND_5000_WORKSHOP && !it.claimed &&
                                        !it.completed
                                }
                            if (m != null) {
                                val newProgress = m.progress + cost.toInt()
                                missionRepository.updateProgress(m.id, newProgress, newProgress >= m.target)
                            }
                        } catch (_: Exception) {
                        }
                    } else {
                        _userMessage.value = "Not enough Steps"
                    }
                } finally {
                    _processing.value = false
                }
            }
        }

        fun quickInvest() {
            if (_processing.value) return
            viewModelScope.launch {
                _processing.value = true
                try {
                    val wallet = playerRepository.observeWallet().first()
                    val target = quickInvest(allUpgrades, wallet)
                    if (target == null) {
                        _userMessage.value = "No affordable upgrades"
                        return@launch
                    }
                    val level = allUpgrades[target] ?: 0
                    purchaseUpgrade(target, level, wallet)
                } finally {
                    _processing.value = false
                }
            }
        }

        fun clearMessage() {
            _userMessage.value = null
        }

        private companion object {
            const val KEY_SELECTED_CATEGORY = "selectedCategory"
        }

        private fun statValueFor(
            type: UpgradeType,
            s: ResolvedStats,
        ): String =
            when (type) {
                UpgradeType.DAMAGE -> "%.1f dmg".format(s.damage)
                UpgradeType.ATTACK_SPEED -> "%.2f/s".format(s.attackSpeed)
                UpgradeType.CRITICAL_CHANCE -> "%.1f%%".format(s.critChance * 100)
                UpgradeType.CRITICAL_FACTOR -> "%.1fx".format(s.critMultiplier)
                UpgradeType.RANGE -> "%.0f range".format(s.range)
                UpgradeType.HEALTH -> "%.0f HP".format(s.maxHealth)
                UpgradeType.HEALTH_REGEN -> "%.1f/s".format(s.healthRegen)
                UpgradeType.DEFENSE_PERCENT -> "%.1f%%".format(s.defensePercent * 100)
                UpgradeType.DEFENSE_ABSOLUTE -> "%.0f block".format(s.defenseAbsolute)
                UpgradeType.KNOCKBACK -> "%.1f force".format(s.knockbackForce)
                UpgradeType.THORN_DAMAGE -> "%.0f%%".format(s.thornPercent * 100)
                UpgradeType.LIFESTEAL -> "%.1f%%".format(s.lifestealPercent * 100)
                UpgradeType.DAMAGE_PER_METER -> "%.0f%%/m".format(s.damagePerMeterBonus * 100)
                UpgradeType.DEATH_DEFY -> "%.0f%%".format(s.deathDefyChance * 100)
                UpgradeType.MULTISHOT -> "${s.multishotTargets} targets"
                UpgradeType.BOUNCE_SHOT -> "${s.bounceCount} bounces"
                UpgradeType.ORBS -> "${s.orbCount} orbs"
                else -> ""
            }
    }
