package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import com.whitefang.stepsofbabylon.domain.usecase.ClaimSupplyDrop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    fun claimDrop(drop: SupplyDrop) {
        viewModelScope.launch { claimSupplyDrop(drop) }
    }

    fun claimAll() {
        viewModelScope.launch {
            uiState.value.drops.forEach { claimSupplyDrop(it) }
        }
    }
}
