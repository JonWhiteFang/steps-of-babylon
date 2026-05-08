package com.whitefang.stepsofbabylon.presentation.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.MissionCategory
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestone
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestoneResult
import com.whitefang.stepsofbabylon.domain.usecase.GenerateDailyMissions
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class MissionsViewModel @Inject constructor(
    private val dailyMissionDao: DailyMissionDao,
    private val milestoneDao: MilestoneDao,
    private val dailyStepDao: DailyStepDao,
    private val playerRepository: PlayerRepository,
    private val playerProfileDao: PlayerProfileDao,
    private val cosmeticRepository: CosmeticRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) : ViewModel() {

    private val generateMissions = GenerateDailyMissions(dailyMissionDao)
    private val claimMilestoneUseCase = ClaimMilestone(
        milestoneDao,
        playerRepository,
        playerProfileDao,
        cosmeticRepository,
    )
    private var today = timeProvider.today().toString()
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val userMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { generateMissions(today) }
        viewModelScope.launch { updateWalkingMissionProgress() }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick.value = System.currentTimeMillis()
                val now = timeProvider.today().toString()
                if (now != today) {
                    today = now
                    generateMissions(today)
                    updateWalkingMissionProgress()
                }
            }
        }
    }

    val uiState: StateFlow<MissionsUiState> = combine(
        dailyMissionDao.getByDate(today),
        milestoneDao.getAll(),
        playerRepository.observeProfile(),
        tick,
        userMessage,
    ) { missions, claimedMilestones, profile, _, message ->
        val claimedIds = claimedMilestones.filter { it.claimed }.map { it.milestoneId }.toSet()
        val midnight = Duration.between(LocalTime.now(), LocalTime.MIDNIGHT.minusNanos(1)).toMillis()
            .let { if (it < 0) it + 86_400_000 else it }

        MissionsUiState(
            missions = missions.map { m ->
                MissionDisplayInfo(m.id, m.missionType.let { type ->
                    DailyMissionType.entries.find { it.name == type }?.description ?: type
                }, m.target, m.progress, m.rewardGems, m.rewardPowerStones, m.completed, m.claimed)
            },
            milestones = Milestone.entries.map { ms ->
                MilestoneDisplayInfo(ms, profile.totalStepsEarned >= ms.requiredSteps, ms.name in claimedIds, profile.totalStepsEarned)
            },
            timeUntilMidnightMs = midnight,
            isLoading = false,
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MissionsUiState())

    fun claimMission(id: Int) {
        viewModelScope.launch {
            val missions = dailyMissionDao.getByDateOnce(today)
            val m = missions.find { it.id == id && it.completed && !it.claimed } ?: return@launch
            if (m.rewardGems > 0) playerRepository.addGems(m.rewardGems.toLong())
            if (m.rewardPowerStones > 0) playerRepository.addPowerStones(m.rewardPowerStones.toLong())
            dailyMissionDao.markClaimed(id)
        }
    }

    fun claimMilestone(milestone: Milestone) {
        viewModelScope.launch {
            // C.4: surface non-Success outcomes as snackbar messages. The UnknownCosmetic
            // variant specifically exists to make the 3 currently-mismatched milestone
            // cosmetic ids (garden_ziggurat_skin / lapis_lazuli_skin / sandals_of_gilgamesh)
            // visible to the player instead of silently dropping. Resolution \u2014 matching those
            // ids to seed rows \u2014 is C.2 PR 3+ content work; until then those 3 milestones
            // cannot be claimed and the snackbar explains why.
            when (val result = claimMilestoneUseCase.invoke(milestone)) {
                ClaimMilestoneResult.Success -> Unit // claim state updates via flow
                ClaimMilestoneResult.InsufficientSteps ->
                    userMessage.value = "You haven't walked enough steps yet."
                ClaimMilestoneResult.AlreadyClaimed ->
                    userMessage.value = "Milestone already claimed."
                is ClaimMilestoneResult.UnknownCosmetic ->
                    userMessage.value = "Reward temporarily unavailable (cosmetic \u201c${result.cosmeticId}\u201d is being finalised). Try again after the next update."
            }
        }
    }

    fun clearMessage() { userMessage.value = null }

    private suspend fun updateWalkingMissionProgress() {
        val missions = dailyMissionDao.getByDateOnce(today)
        val todaySteps = dailyStepDao.sumCreditedSteps(today, today)
        for (m in missions) {
            if (m.claimed || m.completed) continue
            val type = DailyMissionType.entries.find { it.name == m.missionType } ?: continue
            if (type.category != MissionCategory.WALKING) continue
            val progress = todaySteps.toInt().coerceAtMost(m.target)
            dailyMissionDao.updateProgress(m.id, progress, progress >= m.target)
        }
    }
}
