package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.domain.usecase.PurchaseUpgrade
import com.whitefang.stepsofbabylon.domain.usecase.QuickInvest
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WorkshopViewModel @Inject constructor(
    private val workshopRepository: WorkshopRepository,
    private val playerRepository: PlayerRepository,
    private val dailyMissionDao: DailyMissionDao,
) : ViewModel() {

    private val calculateCost = CalculateUpgradeCost()
    private val purchaseUpgrade = PurchaseUpgrade(workshopRepository, calculateCost)
    private val quickInvest = QuickInvest(calculateCost)
    private val resolveStats = ResolveStats()

    private val _selectedCategory = MutableStateFlow(UpgradeCategory.ATTACK)
    private val _processing = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val hiddenUpgrades = setOf(UpgradeType.STEP_MULTIPLIER, UpgradeType.RECOVERY_PACKAGES)
    private var allUpgrades: Map<UpgradeType, Int> = emptyMap()

    val uiState: StateFlow<WorkshopUiState> = combine(
        workshopRepository.observeAllUpgrades(),
        playerRepository.observeWallet(),
        _selectedCategory,
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
        val filtered = upgrades.filter { (type, _) ->
            type.category == category && type !in hiddenUpgrades && type.isWorkshopVisible
        }
        WorkshopUiState(
            upgrades = filtered.map { (type, level) ->
                val maxLevel = type.config.maxLevel
                val isMaxed = maxLevel != null && level >= maxLevel
                val cost = if (isMaxed) 0L else calculateCost(type, level)
                UpgradeDisplayInfo(
                    type = type, level = level, cost = cost, isMaxed = isMaxed,
                    canAfford = !isMaxed && wallet.stepBalance >= cost,
                    description = type.config.description,
                    statValue = statValueFor(type, stats),
                )
            },
            stepBalance = wallet.stepBalance,
            selectedCategory = category,
            isLoading = false,
            isProcessing = processing,
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopUiState())

    fun selectCategory(category: UpgradeCategory) { _selectedCategory.value = category }

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
                        val missions = dailyMissionDao.getByDateOnce(today)
                        val m = missions.find { it.missionType == DailyMissionType.SPEND_5000_WORKSHOP.name && !it.claimed && !it.completed }
                        if (m != null) {
                            val newProgress = m.progress + cost.toInt()
                            dailyMissionDao.updateProgress(m.id, newProgress, newProgress >= m.target)
                        }
                    } catch (_: Exception) { }
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

    fun clearMessage() { _userMessage.value = null }

    private fun statValueFor(type: UpgradeType, s: ResolvedStats): String = when (type) {
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
