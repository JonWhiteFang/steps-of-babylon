package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * #262 L88: `skuId()` is the wire `productId` (Play Billing query/purchase + the
 * `BillingReceiptEntity.productId` column + reconciliation reverse-lookup). It must be
 * locale-INDEPENDENT. The previous `name.lowercase()` (no Locale arg) used the JVM default
 * locale, so under a Turkish/Azeri locale `GEM_PACK_MEDIUM` (note the `I`) lowercased to
 * `gem_pack_medıum` (dotless ı) — a wire id that does NOT match the Play Console product
 * `gem_pack_medium`, breaking that purchase and its reconciliation. The fix pins `Locale.ROOT`.
 */
class BillingProductTest {
    @Test
    fun `skuId is stable under a Turkish default locale (I to dotless-i guard)`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr"))
            // GEM_PACK_MEDIUM contains 'I'; default-locale lowercase() under tr would yield
            // 'gem_pack_medıum'. Locale.ROOT keeps it ASCII.
            assertEquals("gem_pack_medium", BillingProduct.GEM_PACK_MEDIUM.skuId())
            // Spot-check the rest stay canonical too.
            assertEquals("gem_pack_small", BillingProduct.GEM_PACK_SMALL.skuId())
            assertEquals("gem_pack_large", BillingProduct.GEM_PACK_LARGE.skuId())
            assertEquals("ad_removal", BillingProduct.AD_REMOVAL.skuId())
            assertEquals("season_pass", BillingProduct.SEASON_PASS.skuId())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `skuId matches lowercased enum name on a Latin locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            for (p in BillingProduct.entries) {
                assertEquals(p.name.lowercase(Locale.ROOT), p.skuId())
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
