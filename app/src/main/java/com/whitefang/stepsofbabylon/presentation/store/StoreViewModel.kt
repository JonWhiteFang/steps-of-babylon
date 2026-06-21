package com.whitefang.stepsofbabylon.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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

    // #194: bump to re-subscribe the data flow after a load error (retry).
    private val _retry = MutableStateFlow(0)

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

    val uiState: StateFlow<StoreUiState> = _retry.flatMapLatest {
    combine(
        playerRepository.observeProfile(),
        cosmeticRepository.observeAll(),
        _purchasing,
        _userMessage,
        _priceDisplays,
    ) { profile, cosmetics, purchasing, message, priceDisplays ->
        val isActive = profile.seasonPassActive && profile.seasonPassExpiry > System.currentTimeMillis()
        val daysRemaining = if (isActive) {
            ((profile.seasonPassExpiry - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else null
        StoreUiState(
            gems = profile.gems,
            adRemoved = profile.adRemoved,
            seasonPassActive = isActive,
            seasonPassExpiry = profile.seasonPassExpiry,
            seasonPassDaysRemaining = daysRemaining,
            cosmetics = cosmetics.map {
                CosmeticDisplayInfo(it.cosmeticId, it.category, it.name, it.description, it.priceGems, it.isOwned, it.isEquipped)
            },
            isPurchasing = purchasing,
            isLoading = false,
            userMessage = message,
            priceDisplays = priceDisplays,
        )
    }
        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
        // flatMapLatest so retry() re-subscribes.
        .catch { emit(StoreUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreUiState())

    /** #194: re-subscribe the data flow after a load error. */
    fun retry() { _retry.value++ }

    fun purchaseGemPack(product: BillingProduct) = runPurchase(product)

    fun purchaseAdRemoval() = runPurchase(BillingProduct.AD_REMOVAL)

    fun purchaseSeasonPass() = runPurchase(BillingProduct.SEASON_PASS)

    /**
     * Shared purchase driver: guards against concurrent taps, launches the billing flow, and
     * surfaces a [PurchaseResult.Error] message (network / pending / cancelled) via [_userMessage]
     * so the Store Snackbar can show it (#249). [purchaseCosmetic] is intentionally NOT routed
     * through here — it is a Gem spend, not a Play-Billing purchase.
     */
    private fun runPurchase(product: BillingProduct) {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val result = billingManager.purchase(product)
                if (result is PurchaseResult.Error) _userMessage.value = result.message
            } finally { _purchasing.value = false }
        }
    }

    fun purchaseCosmetic(cosmeticId: String) {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val cosmetic = uiState.value.cosmetics.find { it.cosmeticId == cosmeticId } ?: return@launch
                // #122: gate the cosmetic grant on the guarded deduct's success rather than the
                // stale uiState snapshot, so a concurrent spend can't yield a free cosmetic.
                if (playerRepository.spendGems(cosmetic.priceGems)) {
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
