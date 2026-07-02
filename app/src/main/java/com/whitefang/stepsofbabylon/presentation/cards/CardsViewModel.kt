package com.whitefang.stepsofbabylon.presentation.cards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.repository.CardRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import com.whitefang.stepsofbabylon.domain.usecase.ManageCardLoadout
import com.whitefang.stepsofbabylon.domain.usecase.OpenCardPack
import com.whitefang.stepsofbabylon.domain.usecase.PackTier
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeCard
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import com.whitefang.stepsofbabylon.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel
    @Inject
    constructor(
        private val cardRepository: CardRepository,
        private val playerRepository: PlayerRepository,
        private val rewardAdManager: RewardAdManager,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val openCardPack = OpenCardPack(cardRepository)
        private val upgradeCardUseCase = UpgradeCard(cardRepository)
        private val manageLoadout = ManageCardLoadout(cardRepository)

        private val packRevealHandleFlow: StateFlow<PackRevealState?> =
            savedStateHandle.getStateFlow(KEY_PACK_REVEAL, null)
        private val _processing = MutableStateFlow(false)
        private val _userMessage = MutableStateFlow<UiMessage?>(null)

        // #194: bump to re-subscribe the data flow after a load error (retry).
        private val _retry = MutableStateFlow(0)
        private var allCards: List<OwnedCard> = emptyList()

        val uiState: StateFlow<CardsUiState> =
            _retry
                .flatMapLatest {
                    combine(
                        cardRepository.observeAllCards(),
                        playerRepository.observeProfile(),
                        packRevealHandleFlow,
                        _processing,
                        _userMessage,
                    ) { cards, profile, packReveal, processing, message ->
                        allCards = cards
                        val equipped = cards.count { it.isEquipped }
                        CardsUiState(
                            ownedCards =
                                cards.map { card ->
                                    val isMax = card.level >= card.type.maxLevel
                                    val copiesNeeded = card.type.rarity.copiesPerLevel
                                    CardDisplayInfo(
                                        id = card.id,
                                        type = card.type,
                                        level = card.level,
                                        isEquipped = card.isEquipped,
                                        isMaxLevel = isMax,
                                        copyCount = card.copyCount,
                                        copiesNeeded = copiesNeeded,
                                        canAffordUpgrade = !isMax && card.copyCount >= copiesNeeded,
                                    )
                                },
                            equippedCount = equipped,
                            gems = profile.gems,
                            packOptions = PackTier.entries.map { PackOption(it, profile.gems >= it.gemCost) },
                            lastPackResult = packReveal?.toCardResultsOrNull(),
                            freePackAvailable =
                                !profile.adRemoved && profile.freeCardPackAdUsedToday != LocalDate.now().toString(),
                            adRemoved = profile.adRemoved,
                            isLoading = false,
                            isProcessing = processing,
                            userMessage = message,
                        )
                    }
                        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
                        // flatMapLatest so retry() re-subscribes.
                        .catch { emit(CardsUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardsUiState())

        /** #194: re-subscribe the data flow after a load error. */
        fun retry() {
            _retry.value++
        }

        fun openPack(packTier: PackTier) {
            if (_processing.value) return
            viewModelScope.launch {
                _processing.value = true
                try {
                    val result = openCardPack(packTier, uiState.value.gems)
                    if (result is OpenCardPack.Result.Opened) {
                        savedStateHandle[KEY_PACK_REVEAL] = result.cards.toPackRevealState()
                    } else {
                        _userMessage.value = UiMessage.NotEnoughGems
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

                        is UpgradeCard.Result.MaxLevel -> {
                            _userMessage.value = UiMessage.CardAtMaxLevel
                        }

                        is UpgradeCard.Result.InsufficientCopies -> {
                            _userMessage.value = UiMessage.NotEnoughCopies
                        }
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

        fun dismissPackResult() {
            savedStateHandle[KEY_PACK_REVEAL] = null
        }

        fun watchFreePackAd() {
            if (_processing.value) return
            viewModelScope.launch {
                _processing.value = true
                try {
                    val result = rewardAdManager.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)
                    when (result) {
                        is AdResult.Rewarded -> {
                            val packResult = openCardPack(PackTier.COMMON, uiState.value.gems, isFree = true)
                            if (packResult is OpenCardPack.Result.Opened) {
                                savedStateHandle[KEY_PACK_REVEAL] =
                                    packResult.cards.toPackRevealState()
                            }
                            playerRepository.updateFreeCardPackAdUsed(LocalDate.now().toString())
                        }

                        is AdResult.Cancelled -> {
                            _userMessage.value = UiMessage.AdCancelled
                        }

                        is AdResult.Error -> {
                            _userMessage.value =
                                if (result.message.isBlank()) UiMessage.AdFailed else UiMessage.Raw(result.message)
                        }
                    }
                } finally {
                    _processing.value = false
                }
            }
        }

        fun clearMessage() {
            _userMessage.value = null
        }

        private companion object {
            const val KEY_PACK_REVEAL = "packReveal"
        }
    }
