package com.whitefang.stepsofbabylon.presentation.missions

import androidx.annotation.VisibleForTesting
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
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMission
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMissionResult
import com.whitefang.stepsofbabylon.domain.usecase.GenerateDailyMissions
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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
    private val claimMissionUseCase = ClaimMission(dailyMissionDao, playerRepository)
    private val claimMilestoneUseCase = ClaimMilestone(
        milestoneDao,
        playerRepository,
        playerProfileDao,
        cosmeticRepository,
    )
    // #195: the current day as a Flow so the missions query RE-SUBSCRIBES on day-rollover. A plain
    // `var` captured once inside combine() never re-subscribed getByDate(), so the screen showed
    // yesterday's missions across midnight. Mirrors HomeViewModel/StatsViewModel's _currentDate.
    private val _today = MutableStateFlow(timeProvider.today().toString())
    // #194: bump to re-subscribe the data flow after a load error (retry).
    private val _retry = MutableStateFlow(0)
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val userMessage = MutableStateFlow<String?>(null)

    // Bundle C (#162): one-shot claim-celebration event. CONFLATED for consistency with
    // UnclaimedSuppliesViewModel (Task 7) — the screen renders a brief reward chip + success haptic.
    private val _celebration = Channel<ClaimCelebrationEvent>(Channel.CONFLATED)
    val celebration = _celebration.receiveAsFlow()

    init {
        viewModelScope.launch { generateMissions(_today.value) }
        viewModelScope.launch { updateWalkingMissionProgress() }
        // The 1 s ticker drives the per-second midnight countdown (Missions shows "Resets in Xh Ym",
        // unlike Home/Stats). On rollover it delegates to refreshDate() — which flips _today so the
        // query re-subscribes — instead of mutating a captured var (#195). cancelForTest() stops it.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick.value = System.currentTimeMillis()
                refreshDate()
            }
        }
    }

    val uiState: StateFlow<MissionsUiState> = combine(_today, _retry) { today, _ -> today }
        .flatMapLatest { today ->
        combine(
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
        }
        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
        // flatMapLatest so retry() re-subscribes.
        .catch { emit(MissionsUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MissionsUiState())

    /** #194: re-subscribe the data flow after a load error. */
    fun retry() { _retry.value++ }

    /**
     * #195: re-reads the current day; on a rollover, flips [_today] (so the missions query
     * re-subscribes to the new day's rows) and regenerates the day's missions + walking progress.
     * Idempotent — [GenerateDailyMissions] is guarded by the `(date, missionType)` unique index
     * (#127), so the ticker and the screen's ON_RESUME observer can both call this safely. Mirrors
     * [com.whitefang.stepsofbabylon.presentation.home.HomeViewModel.refreshDate].
     */
    fun refreshDate() {
        val now = timeProvider.today().toString()
        if (now != _today.value) {
            _today.value = now
            viewModelScope.launch {
                generateMissions(now)
                updateWalkingMissionProgress()
            }
        }
    }

    fun claimMission(id: Int) {
        // #122: delegate to the atomic ClaimMission use case (mark-first guarded claim, credit
        // only on rows == 1) so a rapid double-tap can't double-credit the reward.
        viewModelScope.launch {
            if (claimMissionUseCase(id, _today.value) == ClaimMissionResult.Success) {
                // Bundle C (#162): celebrate only on a real credit (Success-gated).
                val m = uiState.value.missions.find { it.id == id }
                _celebration.trySend(ClaimCelebrationEvent(label = missionRewardLabel(m)))
            }
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
                ClaimMilestoneResult.Success ->
                    _celebration.trySend(ClaimCelebrationEvent(label = "${milestone.rewardsSummary()} claimed!"))
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

    /**
     * Cancels [viewModelScope] (including the init `while(true)` ticker) so JVM unit tests that
     * construct the VM can terminate — a bare `runTest{}`'s end-of-test cleanup otherwise spins
     * forever on the rescheduling ticker (backgroundScope's exemption covers collectors, not the
     * viewModelScope ticker). Tests call this as their last statement (#162, Bundle C).
     */
    @VisibleForTesting
    fun cancelForTest() { viewModelScope.coroutineContext[Job]?.cancel() }

    private suspend fun updateWalkingMissionProgress() {
        val today = _today.value
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

/** Pure celebration-label builder for a claimed mission (testable without the VM). */
internal fun missionRewardLabel(m: MissionDisplayInfo?): String {
    if (m == null) return "Reward claimed!"
    val parts = buildList {
        if (m.rewardGems > 0) add("+${m.rewardGems} Gems")
        if (m.rewardPowerStones > 0) add("+${m.rewardPowerStones} Power Stones")
    }
    return if (parts.isEmpty()) "Reward claimed!" else parts.joinToString(" ") + " claimed!"
}
