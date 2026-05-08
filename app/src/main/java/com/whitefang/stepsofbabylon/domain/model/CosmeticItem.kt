package com.whitefang.stepsofbabylon.domain.model

/**
 * A cosmetic store item.
 *
 * [overrideColors] is an optional rendering override populated by the repository impl from a
 * code-side lookup table keyed on [cosmeticId]. When non-null and the cosmetic is equipped,
 * the battle renderer swaps the appropriate palette (e.g. ziggurat layer colors for
 * [CosmeticCategory.ZIGGURAT_SKIN]) in place of the biome default. Stays `null` when the
 * cosmetic has no visual override registered \u2014 the lookup table is content, shipped
 * per-cosmetic in follow-up PRs (Phase C.2 PR 2+).
 */
data class CosmeticItem(
    val cosmeticId: String,
    val category: CosmeticCategory,
    val name: String,
    val description: String,
    val priceGems: Long,
    val isOwned: Boolean = false,
    val isEquipped: Boolean = false,
    val overrideColors: List<Int>? = null,
)
