package com.whitefang.stepsofbabylon.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val billingManager: BillingManager,
    private val cosmeticRepository: CosmeticRepository,
) : ViewModel() {

    private val _purchasing = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { cosmeticRepository.ensureSeedData() }
        // Sweep any pending/unresolved Play Billing purchases on Store entry so that
        // purchases completed asynchronously (PENDING → PURCHASED) or while the app was
        // killed mid-flow are granted exactly once. StubBillingManager + FakeBillingManager
        // inherit the no-op default from BillingManager, so this is a no-op outside
        // release builds with USE_REAL_BILLING. C.5 PR 2.
        viewModelScope.launch { billingManager.reconcilePendingPurchases() }
    }

    val uiState: StateFlow<StoreUiState> = combine(
        playerRepository.observeProfile(),
        cosmeticRepository.observeAll(),
        _purchasing,
        _userMessage,
    ) { profile, cosmetics, purchasing, message ->
        StoreUiState(
            gems = profile.gems,
            adRemoved = profile.adRemoved,
            seasonPassActive = profile.seasonPassActive && profile.seasonPassExpiry > System.currentTimeMillis(),
            seasonPassExpiry = profile.seasonPassExpiry,
            cosmetics = cosmetics.map {
                CosmeticDisplayInfo(it.cosmeticId, it.category.name, it.name, it.description, it.priceGems, it.isOwned, it.isEquipped)
            },
            isPurchasing = purchasing,
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreUiState())

    fun purchaseGemPack(product: BillingProduct) {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try { billingManager.purchase(product) } finally { _purchasing.value = false }
        }
    }

    fun purchaseAdRemoval() {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try { billingManager.purchase(BillingProduct.AD_REMOVAL) } finally { _purchasing.value = false }
        }
    }

    fun purchaseSeasonPass() {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try { billingManager.purchase(BillingProduct.SEASON_PASS) } finally { _purchasing.value = false }
        }
    }

    fun purchaseCosmetic(cosmeticId: String) {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val cosmetic = uiState.value.cosmetics.find { it.cosmeticId == cosmeticId } ?: return@launch
                if (uiState.value.gems >= cosmetic.priceGems) {
                    playerRepository.spendGems(cosmetic.priceGems)
                    cosmeticRepository.purchase(cosmeticId)
                } else {
                    _userMessage.value = "Not enough Gems"
                }
            } finally {
                _purchasing.value = false
            }
        }
    }

    fun equipCosmetic(cosmeticId: String) {
        viewModelScope.launch { cosmeticRepository.equip(cosmeticId) }
    }

    fun unequipCosmetic(cosmeticId: String) {
        viewModelScope.launch { cosmeticRepository.unequip(cosmeticId) }
    }

    fun clearMessage() { _userMessage.value = null }
}
