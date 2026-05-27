package com.whitefang.stepsofbabylon.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.MilestoneNotificationPreferences
import com.whitefang.stepsofbabylon.data.local.DailyLoginDao
import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.usecase.CheckResearchCompletion
import com.whitefang.stepsofbabylon.domain.usecase.GenerateDailyMissions
import com.whitefang.stepsofbabylon.domain.usecase.CheckMilestones
import com.whitefang.stepsofbabylon.domain.usecase.TrackDailyLogin
import com.whitefang.stepsofbabylon.domain.usecase.UpdateCompleteResearchMissionProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val stepRepository: StepRepository,
    private val workshopRepository: WorkshopRepository,
    private val labRepository: LabRepository,
    private val walkingEncounterRepository: WalkingEncounterRepository,
    private val dailyLoginDao: DailyLoginDao,
    private val dailyMissionDao: DailyMissionDao,
    private val milestoneDao: MilestoneDao,
    private val milestoneNotificationManager: com.whitefang.stepsofbabylon.service.MilestoneNotificationManager,
    private val milestoneNotificationPrefs: MilestoneNotificationPreferences,
) : ViewModel() {

    private val _currentDate = MutableStateFlow(LocalDate.now().toString())

    /**
     * Closes #55. Pre-fix `init` discarded the [List] returned by [CheckResearchCompletion],
     * so research that completed in the background (timer elapsed while the app was closed)
     * had its level incremented correctly on the next app launch but never advanced the
     * COMPLETE_RESEARCH daily mission. By the time the player navigated to Labs, the
     * research was already marked complete in the DB, so [LabsViewModel]'s mission tick
     * found nothing to credit (`checkCompletion()` returned an empty list →
     * `updateMissionProgress(0)` early-returned at R3-03's count gate).
     *
     * Mirrors the [LabsViewModel] pattern landed in R3-03: capture `completed.size` from
     * [CheckResearchCompletion] and pass it to [UpdateCompleteResearchMissionProgress],
     * which gates the DAO write on `completedCount >= 1`. `dailyMissionDao` is already
     * injected for the existing `countClaimable` flow consumer, so no Hilt graph change.
     */
    private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)

    init {
        viewModelScope.launch {
            playerRepository.ensureProfileExists()
            workshopRepository.ensureUpgradesExist()
            labRepository.ensureResearchExists()
            // #55: capture the completed list so we can credit the COMPLETE_RESEARCH daily
            // mission on background completions. Pre-fix the return value was discarded.
            val completed = CheckResearchCompletion(labRepository)()
            updateMissionProgress(completedCount = completed.size)

            val today = _currentDate.value
            val todaySteps = stepRepository.getDailyRecord(today)?.creditedSteps ?: 0
            val profile0 = playerRepository.observeProfile().first()
            TrackDailyLogin(dailyLoginDao, playerRepository).checkAndAward(today, todaySteps, profile0.seasonPassActive, profile0.seasonPassExpiry)
            GenerateDailyMissions(dailyMissionDao)(today)

            val profile = playerRepository.observeProfile().first()
            val achievable = CheckMilestones(milestoneDao)(profile.totalStepsEarned)
            achievable.firstOrNull { !milestoneNotificationPrefs.hasNotified(it) }?.let {
                milestoneNotificationManager.notifyMilestoneAchieved(it.displayName)
                milestoneNotificationPrefs.markNotified(it)
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = _currentDate.flatMapLatest { date ->
        combine(
            playerRepository.observeProfile(),
            stepRepository.observeTodayRecord(date),
            walkingEncounterRepository.countUnclaimed(),
            dailyMissionDao.countClaimable(date),
            milestoneDao.getAll(),
        ) { profile, stepSummary, unclaimedCount, claimableMissions, milestoneEntities ->
            val claimedIds = milestoneEntities.filter { it.claimed }.map { it.milestoneId }.toSet()
            val achievableMilestones = Milestone.entries.count { it.requiredSteps <= profile.totalStepsEarned && it.name !in claimedIds }
            HomeUiState(
                todaySteps = stepSummary?.creditedSteps ?: 0,
                stepBalance = profile.stepBalance,
                gems = profile.gems,
                powerStones = profile.powerStones,
                currentTier = profile.currentTier,
                highestUnlockedTier = profile.highestUnlockedTier,
                currentBiome = Biome.forTier(profile.currentTier),
                bestWave = profile.bestWavePerTier[profile.currentTier] ?: 0,
                bestWavePerTier = profile.bestWavePerTier,
                unclaimedDropCount = unclaimedCount,
                claimableMissionCount = claimableMissions + achievableMilestones,
                seasonPassActive = profile.seasonPassActive && profile.seasonPassExpiry > System.currentTimeMillis(),
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectTier(tier: Int) {
        viewModelScope.launch { playerRepository.updateTier(tier) }
    }

    fun refreshDate() {
        val now = LocalDate.now().toString()
        if (now != _currentDate.value) {
            _currentDate.value = now
            viewModelScope.launch { GenerateDailyMissions(dailyMissionDao)(now) }
        }
    }
}
