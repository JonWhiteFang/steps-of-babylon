package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Domain-model invariants for [ResearchType] (RO-11 #B.2; #44). The [ResearchType.isComingSoon]
 * flag drives the `surfacedInLabs()` list filter (which removes deferred types from the Labs UI
 * before any card renders) and the defensive VM-level guard in `LabsViewModel.startResearch`. Both
 * readers MUST stay in sync with the canonical flag values asserted here. (The old in-`LabsScreen`
 * Coming Soon badge + Start-button suppression were removed in #44 once the list filter made them
 * dead code.)
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
        //       hide it from the Labs UI — a player-visible regression).
        // ENEMY_INTEL was flipped to wired in V1X-15b (was deferred under RO-11 #B.2).
        // Both readers (the `surfacedInLabs()` list filter + LabsViewModel.startResearch
        // defensive guard) read the same flag, so this test transitively guards both.
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

    @Test
    fun `surfacedInLabs excludes coming-soon research`() {
        // The Labs UI must never surface a deferred (isComingSoon) research type — that is
        // exactly the half-built stub #44 / Gate B.1 is about. surfacedInLabs() is the single
        // source of truth LabsViewModel consumes; this pins its body so the exclusion can't be
        // silently dropped (see also `surfacedInLabs is exactly the wired types`).
        val surfaced = ResearchType.surfacedInLabs()
        assertTrue(
            surfaced.none { it.isComingSoon },
            "surfacedInLabs() must exclude every isComingSoon entry",
        )
        assertFalse(
            ResearchType.AUTO_UPGRADE_AI in surfaced,
            "AUTO_UPGRADE_AI (the deferred type) must not be surfaced in Labs",
        )
    }

    @Test
    fun `surfacedInLabs is exactly the wired types`() {
        // Set-equality both directions: surfaced == all entries minus the single deferred one.
        // Fails red if surfacedInLabs() stops filtering (would re-include AUTO_UPGRADE_AI) OR
        // over-filters (drops a wired type). AUTO_UPGRADE_AI is the sole isComingSoon entry
        // (guarded by `only AUTO_UPGRADE_AI is flagged isComingSoon` above).
        assertEquals(
            ResearchType.entries.toSet() - ResearchType.AUTO_UPGRADE_AI,
            ResearchType.surfacedInLabs().toSet(),
            "surfacedInLabs() must be exactly the 11 wired types (all entries minus AUTO_UPGRADE_AI)",
        )
    }
}
