package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout constants for the battle [BattleControlRail] (#171). Single source of truth for the rail's
 * fixed footprint and the upgrade-menu start padding, so the "menu clears the rail by GAP" coupling
 * (spec §4.5) can't drift into two hardcoded numbers. WIDTH/GAP are cosmetic — tune on-device — but
 * the [menuStartPadding] DERIVATION must stay the single value the menu wrapper consumes.
 */
object BattleControlRailDefaults {
    /**
     * Fixed rail footprint. Sized to hold the widest control (4x / pause / upgrade) + pill padding.
     * Starts at 80.dp (spec §4.5 showed 64.dp illustratively); both are tune-on-device cosmetics and
     * the [menuStartPadding] coupling is value-independent.
     */
    val WIDTH: Dp = 80.dp

    /** Separation between the rail's right edge and the upgrade menu's left edge. */
    val GAP: Dp = 8.dp

    /** Upgrade-menu wrapper start padding. The menu wrapper MUST consume this (not re-type WIDTH + GAP). */
    fun menuStartPadding(): Dp = WIDTH + GAP
}
