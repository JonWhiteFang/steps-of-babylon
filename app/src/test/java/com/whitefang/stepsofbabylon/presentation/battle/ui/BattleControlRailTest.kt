package com.whitefang.stepsofbabylon.presentation.battle.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #171: pins the single drift-prone invariant of the left control rail — the upgrade-menu start
 * padding is DERIVED from the same WIDTH + GAP the rail uses, so the "menu clears the rail by GAP"
 * coupling can't decay into two independently-edited numbers. Pure Dp arithmetic: no Compose render,
 * no Robolectric (battle-screen layout has no Compose-rule coverage — see spec §6 / PR-4736).
 */
class BattleControlRailTest {

    @Test
    fun `menu start padding equals rail width plus gap`() {
        assertEquals(
            BattleControlRailDefaults.WIDTH + BattleControlRailDefaults.GAP,
            BattleControlRailDefaults.menuStartPadding(),
        )
    }

    @Test
    fun `menu start padding clears the rail by a positive gap`() {
        assertTrue(
            BattleControlRailDefaults.menuStartPadding() > BattleControlRailDefaults.WIDTH,
            "menu must begin strictly past the rail's right edge",
        )
    }
}
