package com.whitefang.stepsofbabylon.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import com.whitefang.stepsofbabylon.domain.usecase.ManageCardLoadout
import com.whitefang.stepsofbabylon.domain.usecase.OpenCardPack
import com.whitefang.stepsofbabylon.domain.usecase.PackTier
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeCard
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val playerRepository: PlayerRepository,
    private val rewardAdManager: RewardAdManager,
) : ViewModel() {

    private val openCardPack = OpenCardPack(cardRepository)
    private val upgradeCardUseCase = UpgradeCard(cardRepository)
    private val manageLoadout = ManageCardLoadout(cardRepository)

    private val _lastPackResult = MutableStateFlow<List<CardResult>?>(null)
    private val _processing = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private var allCards: List<OwnedCard> = emptyList()

    val uiState: StateFlow<CardsUiState> = combine(
        cardRepository.observeAllCards(),
        playerRepository.observeProfile(),
        _lastPackResult,
        _processing,
        _userMessage,
    ) { cards, profile, packResult, processing, message ->
        allCards = cards
        val equipped = cards.count { it.isEquipped }
        CardsUiState(
            ownedCards = cards.map { card ->
                val isMax = card.level >= card.type.maxLevel
                val copiesNeeded = card.type.rarity.copiesPerLevel
                CardDisplayInfo(
                    id = card.id, type = card.type, level = card.level,
                    isEquipped = card.isEquipped, isMaxLevel = isMax,
                    copyCount = card.copyCount,
                    copiesNeeded = copiesNeeded,
                    canAffordUpgrade = !isMax && card.copyCount >= copiesNeeded,
                    effectDescription = card.type.effectDescriptionAtLevel(card.level),
                )
            },
            equippedCount = equipped,
            gems = profile.gems,
            packOptions = PackTier.entries.map { PackOption(it, profile.gems >= it.gemCost) },
            lastPackResult = packResult,
            freePackAvailable = !profile.adRemoved && profile.freeCardPackAdUsedToday != LocalDate.now().toString(),
            adRemoved = profile.adRemoved,
            isLoading = false,
            isProcessing = processing,
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardsUiState())

    fun openPack(packTier: PackTier) {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val result = openCardPack(packTier, uiState.value.gems)
                if (result is OpenCardPack.Result.Opened) {
                    _lastPackResult.value = result.cards
                } else {
                    _userMessage.value = "Not enough Gems"
                }
            } finally {
                _processing.value = false
            }
        }
    }

    fun upgradeCard(cardId: Int) {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val card = allCards.find { it.id == cardId } ?: return@launch
                when (upgradeCardUseCase(card)) {
                    is UpgradeCard.Result.Upgraded -> { /* UI updates reactively via Flow */ }
                    is UpgradeCard.Result.MaxLevel -> _userMessage.value = "Card already at max level"
                    is UpgradeCard.Result.InsufficientCopies -> _userMessage.value = "Not enough copies"
                }
            } finally {
                _processing.value = false
            }
        }
    }

    fun equipCard(cardId: Int) {
        viewModelScope.launch {
            val equipped = allCards.count { it.isEquipped }
            manageLoadout.equip(cardId, equipped)
        }
    }

    fun unequipCard(cardId: Int) {
        viewModelScope.launch { manageLoadout.unequip(cardId) }
    }

    fun dismissPackResult() { _lastPackResult.value = null }

    fun watchFreePackAd() {
        if (_processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val result = rewardAdManager.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)
                when (result) {
                    is AdResult.Rewarded -> {
                        val packResult = openCardPack(PackTier.COMMON, uiState.value.gems, isFree = true)
                        if (packResult is OpenCardPack.Result.Opened) _lastPackResult.value = packResult.cards
                        playerRepository.updateFreeCardPackAdUsed(LocalDate.now().toString())
                    }
                    is AdResult.Cancelled -> _userMessage.value = "Ad cancelled. Try again."
                    is AdResult.Error ->
                        _userMessage.value = result.message.ifBlank { "Ad failed to load. Try again later." }
                }
            } finally {
                _processing.value = false
            }
        }
    }

    fun clearMessage() { _userMessage.value = null }
}
