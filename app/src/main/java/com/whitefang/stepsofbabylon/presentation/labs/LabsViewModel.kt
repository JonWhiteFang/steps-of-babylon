package com.whitefang.stepsofbabylon.presentation.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.usecase.CalculateResearchCost
import com.whitefang.stepsofbabylon.domain.usecase.CalculateResearchTime
import com.whitefang.stepsofbabylon.domain.usecase.CheckResearchCompletion
import com.whitefang.stepsofbabylon.domain.usecase.RushResearch
import com.whitefang.stepsofbabylon.domain.usecase.StartResearch
import com.whitefang.stepsofbabylon.domain.usecase.UnlockLabSlot
import com.whitefang.stepsofbabylon.domain.usecase.UpdateCompleteResearchMissionProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class LabsViewModel @Inject constructor(
    private val labRepository: LabRepository,
    private val playerRepository: PlayerRepository,
    private val dailyMissionDao: DailyMissionDao,
) : ViewModel() {

    private val calculateCost = CalculateResearchCost()
    private val calculateTime = CalculateResearchTime()
    private val startResearch = StartResearch(labRepository, playerRepository, calculateCost, calculateTime)
    private val rushResearch = RushResearch(labRepository, playerRepository)
    private val unlockLabSlot = UnlockLabSlot(playerRepository)
    private val checkCompletion = CheckResearchCompletion(labRepository)
    private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)

    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val _processing = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            labRepository.ensureResearchExists()
            val completed = checkCompletion()
            // R3-03: only tick the COMPLETE_RESEARCH daily mission when something
            // actually completed. The use case applies the count gating internally so
            // every call site (init / rushResearch / freeRush) gets the same semantics
            // for free — closes the false-trigger that previously fired every time the
            // Labs screen was opened with nothing in flight.
            updateMissionProgress(completedCount = completed.size)
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick.value = System.currentTimeMillis()
            }
        }
    }

    val uiState: StateFlow<LabsUiState> = combine(
        labRepository.observeAllResearch(),
        labRepository.observeActiveResearch(),
        playerRepository.observeProfile(),
        tick,
        combine(_processing, _userMessage) { p, m -> p to m },
    ) { levels, activeList, profile, now, (processing, message) ->
        val activeMap = activeList.associateBy { it.type }
        LabsUiState(
            researchList = ResearchType.entries.map { type ->
                val level = levels[type] ?: 0
                val isMaxed = level >= type.maxLevel
                val active = activeMap[type]
                val cost = if (isMaxed) 0L else calculateCost(type, level)
                val timeHours = if (isMaxed) 0.0 else calculateTime(type, level)
                val remainingMs = active?.let { max(0L, it.completesAt - now) } ?: 0L
                val rushCost = active?.let {
                    RushResearch.calculateRushCost(it.startedAt, it.completesAt, now)
                } ?: 0L
                ResearchDisplayInfo(
                    type = type, level = level, isMaxed = isMaxed,
                    costToStart = cost,
                    canAffordStart = !isMaxed && active == null && profile.stepBalance >= cost,
                    timeToCompleteHours = timeHours,
                    isActive = active != null,
                    remainingMs = remainingMs,
                    rushCostGems = rushCost,
                    canAffordRush = active != null && profile.gems >= rushCost,
                )
            },
            activeSlots = activeList.size,
            totalSlots = profile.labSlotCount,
            stepBalance = profile.stepBalance,
            gems = profile.gems,
            canAffordSlotUnlock = profile.labSlotCount < UnlockLabSlot.MAX_SLOTS && profile.gems >= UnlockLabSlot.SLOT_COST_GEMS,
            seasonPassFreeRushAvailable = profile.seasonPassActive && profile.seasonPassExpiry > now && profile.freeLabRushUsedToday != LocalDate.now().toString(),
            isLoading = false,
            isProcessing = processing,
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabsUiState())

    fun startResearch(type: ResearchType) {
        if (_processing.value) return
        // RO-11 #B.2: defensive belt-and-braces guard. The Labs UI already suppresses the
        // Start Research button when [ResearchType.isComingSoon] is true, but this block
        // protects against any future entry point (e.g. quick-research flow, deep-link)
        // accidentally bypassing the UI gate while AUTO_UPGRADE_AI + ENEMY_INTEL are still
        // deferred. Both layers read the same content-as-code flag so they cannot drift.
        if (type.isComingSoon) {
            _userMessage.value = "Coming soon \u2014 reserved for v1.x"
            return
        }
        viewModelScope.launch {
            _processing.value = true
            try {
                val profile = playerRepository.observeProfile().first()
                val result = startResearch(type, profile.toWallet(), profile.labSlotCount)
                if (result !is StartResearch.Result.Success) {
                    _userMessage.value = when (result) {
                        is StartResearch.Result.InsufficientSteps -> "Not enough Steps"
                        is StartResearch.Result.NoSlotAvailable -> "No research slot available"
                        is StartResearch.Result.MaxLevelReached -> "Already at max level"
                        is StartResearch.Result.AlreadyResearching -> "Already researching"
                        is StartResearch.Result.Success -> null
                    }
                }
            } finally {
                _processing.value = false
            }
        }
    }

    fun rushResearch(type: ResearchType) {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val profile = playerRepository.observeProfile().first()
                val activeList = labRepository.observeActiveResearch().first()
                val active = activeList.find { it.type == type } ?: return@launch
                val result = rushResearch(type, active, profile.toWallet())
                if (result is RushResearch.Result.Rushed) updateMissionProgress(completedCount = 1)
                else _userMessage.value = "Not enough Gems"
            } finally {
                _processing.value = false
            }
        }
    }

    fun freeRush(type: ResearchType) {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val profile = playerRepository.observeProfile().first()
                if (!profile.seasonPassActive || profile.seasonPassExpiry <= System.currentTimeMillis()) {
                    _userMessage.value = "Season Pass required"
                    return@launch
                }
                if (profile.freeLabRushUsedToday == LocalDate.now().toString()) {
                    _userMessage.value = "Free rush already used today"
                    return@launch
                }
                val activeList = labRepository.observeActiveResearch().first()
                if (activeList.find { it.type == type } == null) {
                    _userMessage.value = "No active research to rush"
                    return@launch
                }
                labRepository.completeResearch(type)
                playerRepository.updateFreeLabRushUsed(LocalDate.now().toString())
                updateMissionProgress(completedCount = 1)
            } finally {
                _processing.value = false
            }
        }
    }

    fun unlockSlot() {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val result = unlockLabSlot(uiState.value.totalSlots, uiState.value.gems)
                if (result !is UnlockLabSlot.Result.Unlocked) _userMessage.value = "Not enough Gems or max slots reached"
            } finally {
                _processing.value = false
            }
        }
    }

    fun clearMessage() { _userMessage.value = null }
}
