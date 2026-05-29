package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Domain-model invariants for [ResearchType] (RO-11 #B.2). The [ResearchType.isComingSoon]
 * flag drives the Labs-screen Coming Soon badge, the Start-Research-button suppression in
 * [LabsScreen], and the defensive VM-level guard in `LabsViewModel.startResearch`. All
 * three readers MUST stay in sync with the canonical flag values asserted here.
 *
 * Sits alongside the existing model-level tests ([MilestoneTest], [UpgradeTypeTest],
 * [EnemyTypeTest], [DailyMissionTypeTest]) rather than [LabsViewModelTest] because the
 * existing Labs VM tests intentionally bypass [LabsViewModel] instantiation due to its
 * `while(true) { delay(1000); tick.value = ... }` ticker block (see comment in
 * `LabsViewModelTest`). The flag is content-as-code on the enum itself, so a model test
 * is the natural fit.
 */
class ResearchTypeTest {

    @Test
    fun `only AUTO_UPGRADE_AI is flagged isComingSoon`() {
        // Set-equality contract: catches both directions of regression —
        //   (a) the deferred enum silently flipping back to wired (would re-introduce
        //       a dead-enum gap because its gameplay code is still missing), and
        //   (b) one of the 11 wired enums (DAMAGE / HEALTH / CASH / CRITICAL / REGEN /
        //       STEP_EFFICIENCY / UW_COOLDOWN / WAVE_SKIP / MULTISHOT_RESEARCH /
        //       BOUNCE_RESEARCH / ENEMY_INTEL) silently getting marked Coming Soon (would
        //       suppress its UI — a player-visible regression).
        // ENEMY_INTEL was flipped to wired in V1X-15b (was deferred under RO-11 #B.2).
        // Both layers (LabsScreen Coming Soon badge + LabsViewModel.startResearch defensive
        // guard) read the same flag, so this test transitively guards both.
        val deferred = ResearchType.entries.filter { it.isComingSoon }.toSet()
        assertEquals(
            setOf(ResearchType.AUTO_UPGRADE_AI),
            deferred,
            "Only AUTO_UPGRADE_AI should be Coming Soon as of V1X-15b (ENEMY_INTEL now wired)",
        )
    }

    @Test
    fun `ENEMY_INTEL has full balance values populated (V1X-15b)`() {
        // V1X-15b flipped ENEMY_INTEL from the placeholder (3000/6.0/maxLevel 3/Coming Soon)
        // to the locked balance: 8000 Steps base × 1.5 scaling, 4h × 1.10, maxLevel 10,
        // +2 %/level damage. Guards against a partial revert that leaves stale placeholder
        // values while clearing the isComingSoon flag.
        val t = ResearchType.ENEMY_INTEL
        assertEquals(8_000L, t.baseCostSteps, "baseCostSteps")
        assertEquals(1.5, t.costScaling, 1e-9, "costScaling")
        assertEquals(4.0, t.baseTimeHours, 1e-9, "baseTimeHours")
        assertEquals(1.10, t.timeScaling, 1e-9, "timeScaling")
        assertEquals(10, t.maxLevel, "maxLevel")
        assertEquals(2.0, t.effectPerLevel, 1e-9, "effectPerLevel")
        assertEquals(false, t.isComingSoon, "isComingSoon")
    }
}
