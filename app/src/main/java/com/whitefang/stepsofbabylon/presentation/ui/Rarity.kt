package com.whitefang.stepsofbabylon.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.LapisLight
import com.whitefang.stepsofbabylon.presentation.ui.theme.RaritySand
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess

/** Shared pill font size for [RarityBadge] / [EquippedChip]. */
private val PILL_FONT_SIZE = 10.5.sp

/**
 * Bundle D (#163): presentation-only collectibles rarity identity, shared by Cards + Ultimate Weapons.
 *
 * [RarityTier] is the shared 3-colour palette; the per-screen LABEL shifts (Cards: COMMON/RARE/EPIC;
 * UWs: RARE/EPIC/LEGENDARY — no UW is "common"). NOT a domain concept: UWs have no rarity field, so
 * their tier is derived from [com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType.unlockCost]
 * here in the UI ([uwRarityTier]).
 */
enum class RarityTier { TIER_0, TIER_1, TIER_2 }

/**
 * Tier → theme colour token (sand → sky-lapis → gold ramp). PLAIN fun (not @Composable): reads only
 * top-level `val Color` tokens, so it is JVM-unit-testable and callable from [rarityBorder].
 * `Color(0xFF…)` is a value class (ULong bit-math) — no Android runtime needed.
 */
fun RarityTier.color(): Color =
    when (this) {
        RarityTier.TIER_0 -> RaritySand

        // #C2B280
        RarityTier.TIER_1 -> LapisLight

        // #A7C7E7
        RarityTier.TIER_2 -> Gold // #D4A843
    }

/** Card rarity → tier. Exhaustive over [CardRarity] (compiler-enforced). */
fun cardRarityTier(rarity: CardRarity): RarityTier =
    when (rarity) {
        CardRarity.COMMON -> RarityTier.TIER_0
        CardRarity.RARE -> RarityTier.TIER_1
        CardRarity.EPIC -> RarityTier.TIER_2
    }

/**
 * UW unlock cost → tier. Range-based (not exact-value) so a re-priced or future UW landing off
 * today's costs (50/60/75/80/100) still tiers sanely (spec D8). [RarityTest] pins today's six.
 */
fun uwRarityTier(unlockCost: Int): RarityTier =
    when {
        unlockCost <= 60 -> RarityTier.TIER_0
        unlockCost <= 89 -> RarityTier.TIER_1
        else -> RarityTier.TIER_2
    }

/** Card label = the rarity name (COMMON / RARE / EPIC). Delegates to [CardRarity.labelRes] so the
 *  rarity→string mapping lives in exactly one place (EnumLabels.kt); this keeps the badge call-site name. */
@StringRes fun cardRarityLabelRes(rarity: CardRarity): Int = rarity.labelRes()

/** UW label shifts up so no UW reads as "common" (RARE / EPIC / LEGENDARY). */
@StringRes fun uwRarityLabelRes(tier: RarityTier): Int =
    when (tier) {
        RarityTier.TIER_0 -> R.string.uw_rarity_rare
        RarityTier.TIER_1 -> R.string.uw_rarity_epic
        RarityTier.TIER_2 -> R.string.uw_rarity_legendary
    }

/**
 * Filled pill badge in the tier colour, dark text for contrast on the light tier colours.
 * [label] is supplied by the caller (resolving [cardRarityLabelRes] / [uwRarityLabelRes] via
 * stringResource) so the same badge serves both screens' naming. [alpha] dims the badge for a
 * locked UW (spec D6); default 1f = full opacity.
 */
@Composable
fun RarityBadge(
    tier: RarityTier,
    label: String,
    alpha: Float = 1f,
) {
    Text(
        text = label,
        color = Color(0xFF1A1A2E).copy(alpha = alpha),
        fontWeight = FontWeight.Bold,
        fontSize = PILL_FONT_SIZE,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(tier.color().copy(alpha = alpha))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Filled "✓ EQUIPPED" chip — the prominent equipped signal (replaces the tiny ✓ / background tint). */
@Composable
fun EquippedChip() {
    Text(
        text = "✓ EQUIPPED",
        color = Color(0xFF10300A),
        fontWeight = FontWeight.Bold,
        fontSize = PILL_FONT_SIZE,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(StatusSuccess)
                .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * 3dp rarity border + a left accent bar in the tier colour. Plain Modifier extension — resolves the
 * colour itself via [color], so call-sites stay the bare `Modifier.rarityBorder(tier)`. [alpha] dims
 * the whole treatment for a locked UW (spec D6); default 1f = full opacity (Cards always use default).
 *
 * The accent bar is drawn ON TOP of content via [drawWithContent] (the spec pins this: a `drawBehind`
 * bar would be occluded by the Material3 card container fill). 5dp wide, full height, left edge.
 * The shape is clipped first so the accent bar's corners follow the card's rounded corners (and don't
 * bleed past them).
 */
fun Modifier.rarityBorder(
    tier: RarityTier,
    alpha: Float = 1f,
): Modifier {
    val c = tier.color().copy(alpha = alpha)
    return this
        .clip(RoundedCornerShape(12.dp))
        .border(width = 3.dp, color = c, shape = RoundedCornerShape(12.dp))
        .drawWithContent {
            drawContent()
            drawRect(color = c, size = Size(width = 5.dp.toPx(), height = size.height))
        }
}
