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

    /**
     * Live formatted prices from Play Billing's `ProductDetails.priceDisplay`. Populated
     * lazily on Store entry by [refreshPriceDisplays]; missing keys fall back to the static
     * [BillingProduct.priceDisplay] constant at the UI layer (see `StoreScreen`).
     *
     * Plan 31 PR B: removes the in-app/Play-Console price drift footgun where the static
     * constants could disagree with what Play Console actually charges.
     */
    private val _priceDisplays = MutableStateFlow<Map<BillingProduct, String>>(emptyMap())

    init {
        viewModelScope.launch { cosmeticRepository.ensureSeedData() }
        // Sweep any pending/unresolved Play Billing purchases on Store entry so that
        // purchases completed asynchronously (PENDING → PURCHASED) or while the app was
        // killed mid-flow are granted exactly once. FakeBillingManager (test) inherits the
        // no-op default from BillingManager. C.5 PR 2.
        viewModelScope.launch { billingManager.reconcilePendingPurchases() }
        // Fetch live formatted prices for all 5 SKUs so the Store screen displays whatever
        // Play Console is currently charging instead of a stale static constant. Sequential
        // (the impl serialises queries via a Mutex) but each Play Billing query is ~100–200
        // ms; ~1 s total worst case, with prices populating progressively. Plan 31 PR B.
        viewModelScope.launch { refreshPriceDisplays() }
    }

    val uiState: StateFlow<StoreUiState> = combine(
        playerRepository.observeProfile(),
        cosmeticRepository.observeAll(),
        _purchasing,
        _userMessage,
        _priceDisplays,
    ) { profile, cosmetics, purchasing, message, priceDisplays ->
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
            priceDisplays = priceDisplays,
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

    /**
     * Queries Play Billing for the live formatted price of every [BillingProduct] and
     * populates [_priceDisplays] progressively as each query completes. Failures (network,
     * Play Services unavailable, SKU unreleased) are silently skipped — the UI layer falls
     * back to the static [BillingProduct.priceDisplay] constant for any missing key.
     *
     * Visible for testing so a unit test can drive a deterministic refresh and assert the
     * resulting [StoreUiState.priceDisplays] map. Plan 31 PR B.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun refreshPriceDisplays() {
        val accumulator = _priceDisplays.value.toMutableMap()
        for (product in BillingProduct.entries) {
            val priceDisplay = billingManager.getPriceDisplay(product)
            if (priceDisplay != null) {
                accumulator[product] = priceDisplay
                _priceDisplays.value = accumulator.toMap()
            }
        }
    }
}
