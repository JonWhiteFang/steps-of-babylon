package com.whitefang.stepsofbabylon.presentation.store

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory

data class StoreUiState(
    val gems: Long = 0,
    val adRemoved: Boolean = false,
    val seasonPassActive: Boolean = false,
    val seasonPassExpiry: Long = 0,
    val seasonPassDaysRemaining: Int? = null,
    val cosmetics: List<CosmeticDisplayInfo> = emptyList(),
    val isPurchasing: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val userMessage: String? = null,
    /**
     * Live formatted prices from Play Billing per SKU (e.g. `BillingProduct.GEM_PACK_SMALL
     * → "$0.99"`, locale-formatted by Play Billing). Populated by [StoreViewModel.refreshPriceDisplays].
     * Missing keys (failed queries, no network, SKU unreleased) signal the UI to fall back
     * to the static [com.whitefang.stepsofbabylon.domain.model.BillingProduct.priceDisplay]
     * constant. Plan 31 PR B.
     */
    val priceDisplays: Map<com.whitefang.stepsofbabylon.domain.model.BillingProduct, String> = emptyMap(),
)

data class CosmeticDisplayInfo(
    val cosmeticId: String,
    val category: CosmeticCategory,
    val name: String,
    val description: String,
    val priceGems: Long,
    val isOwned: Boolean,
    val isEquipped: Boolean,
)
