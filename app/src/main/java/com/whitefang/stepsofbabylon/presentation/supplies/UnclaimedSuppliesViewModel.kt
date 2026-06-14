package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import com.whitefang.stepsofbabylon.domain.usecase.ClaimSupplyDrop
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnclaimedSuppliesViewModel @Inject constructor(
    private val encounterRepository: WalkingEncounterRepository,
    private val playerRepository: PlayerRepository,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val claimSupplyDrop = ClaimSupplyDrop(encounterRepository, playerRepository, cardRepository)

    val uiState: StateFlow<SuppliesUiState> = encounterRepository.observeUnclaimed()
        .map { SuppliesUiState(drops = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SuppliesUiState())

    private val _celebration = Channel<ClaimCelebrationEvent>(Channel.CONFLATED)
    val celebration = _celebration.receiveAsFlow()

    fun claimDrop(drop: SupplyDrop) {
        viewModelScope.launch {
            if (claimSupplyDrop(drop) is ClaimSupplyDrop.Result.Success) {
                _celebration.trySend(ClaimCelebrationEvent(label = supplyLabel(drop)))
            }
        }
    }

    fun claimAll() {
        viewModelScope.launch {
            val anySuccess = uiState.value.drops.fold(false) { acc, d ->
                (claimSupplyDrop(d) is ClaimSupplyDrop.Result.Success) || acc
            }
            if (anySuccess) _celebration.trySend(ClaimCelebrationEvent(label = "All supplies claimed!"))
        }
    }
}

/** Pure celebration-label builder for a single supply drop (testable without the VM). */
internal fun supplyLabel(drop: SupplyDrop): String = when (drop.reward) {
    SupplyDropReward.STEPS -> "+${drop.rewardAmount} Steps claimed!"
    SupplyDropReward.GEMS -> "+${drop.rewardAmount} Gems claimed!"
    SupplyDropReward.POWER_STONES -> "+${drop.rewardAmount} Power Stones claimed!"
    SupplyDropReward.CARD_COPY -> "Card claimed!"
}
