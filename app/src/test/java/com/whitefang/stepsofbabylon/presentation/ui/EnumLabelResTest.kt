package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.usecase.PackTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #260: every in-scope enum constant (and the WavePhase string keys) maps to a non-blank, non-raw
 * localized label. Catches a missing mapping when a constant is added. Robolectric resolves text.
 * Also re-homes the two RarityTest label-text assertions (TRW-4) that can no longer be pure-JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EnumLabelResTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = ctx.getString(id)

    @Test fun `every UpgradeCategory has a non-blank label`() =
        UpgradeCategory.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every PackTier has a non-blank label`() =
        PackTier.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every CardRarity has a non-blank label`() =
        CardRarity.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every CosmeticCategory has a non-blank label not equal to the raw name`() =
        CosmeticCategory.entries.forEach {
            val s = str(it.labelRes()); assertTrue(s.isNotBlank()); assertFalse(s == it.name)
        }

    @Test fun `wave phase keys resolve and blank returns null`() {
        assertTrue(str(wavePhaseLabelRes("SPAWNING")!!).isNotBlank())
        assertTrue(str(wavePhaseLabelRes("COOLDOWN")!!).isNotBlank())
        assertTrue(wavePhaseLabelRes("") == null)
        assertTrue(wavePhaseLabelRes("GARBAGE") == null)
    }

    // Re-homed from RarityTest (TRW-4) — exact rendered label text, now via Robolectric.
    @Test fun `uw rarity labels never say COMMON`() {
        assertEquals("RARE", str(uwRarityLabelRes(RarityTier.TIER_0)))
        assertEquals("EPIC", str(uwRarityLabelRes(RarityTier.TIER_1)))
        assertEquals("LEGENDARY", str(uwRarityLabelRes(RarityTier.TIER_2)))
    }
    @Test fun `card rarity labels are the rarity name`() {
        assertEquals("COMMON", str(cardRarityLabelRes(CardRarity.COMMON)))
        assertEquals("RARE", str(cardRarityLabelRes(CardRarity.RARE)))
        assertEquals("EPIC", str(cardRarityLabelRes(CardRarity.EPIC)))
    }
}
