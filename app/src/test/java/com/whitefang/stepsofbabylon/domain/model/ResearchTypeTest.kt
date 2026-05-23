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
    fun `only AUTO_UPGRADE_AI and ENEMY_INTEL are flagged isComingSoon`() {
        // Set-equality contract: catches both directions of regression —
        //   (a) one of the deferred enums silently flipping back to wired (would re-introduce
        //       a dead-enum gap because the gameplay code is still missing), and
        //   (b) one of the 10 wired enums (DAMAGE / HEALTH / CASH / CRITICAL / REGEN /
        //       STEP_EFFICIENCY / UW_COOLDOWN / WAVE_SKIP / MULTISHOT_RESEARCH /
        //       BOUNCE_RESEARCH) silently getting marked Coming Soon (would suppress its UI —
        //       a player-visible regression to pre-RO-11/-R4-02b behaviour).
        // Both layers (LabsScreen Coming Soon badge + LabsViewModel.startResearch defensive
        // guard) read the same flag, so this test transitively guards both.
        val deferred = ResearchType.entries.filter { it.isComingSoon }.toSet()
        assertEquals(
            setOf(ResearchType.AUTO_UPGRADE_AI, ResearchType.ENEMY_INTEL),
            deferred,
            "Only AUTO_UPGRADE_AI and ENEMY_INTEL should be Coming Soon as of RO-11 #B.2",
        )
    }
}
