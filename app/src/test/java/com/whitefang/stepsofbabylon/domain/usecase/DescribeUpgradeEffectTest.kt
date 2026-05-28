package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Per-row Now → Next readout for the in-round upgrade menu (RO-11 #C, originally RO-10).
 *
 * One test per visible upgrade type so that a regression in [DescribeUpgradeEffect.format]
 * (or in [ResolveStats] for the stat-bearing rows) surfaces with a helpful message naming
 * the specific upgrade that broke. Each test asserts both the current readout and the
 * next-purchase readout because the player-facing value is the *delta* between them.
 *
 * Format strings are pinned to [java.util.Locale.ROOT] inside the use case so the tests
 * run identically on locales that use `,` as the decimal separator (e.g. de-DE).
 */
class DescribeUpgradeEffectTest {

    private val describe = DescribeUpgradeEffect()

    // ---- Multiplicative stats ----

    @Test
    fun `DAMAGE multiplicative outer formula`() {
        // ws=10, ir=0 -> base 10 * (1 + 10*0.02) = 12.0 dmg.
        // next ir=1 -> 10 * 1.20 * 1.02 = 12.24 -> 12.2 dmg.
        val r = describe(mapOf(UpgradeType.DAMAGE to 10), emptyMap(), emptyMap(), UpgradeType.DAMAGE)
        assertEquals("12.0 dmg", r.current)
        assertEquals("12.2 dmg", r.next)
    }

    @Test
    fun `ATTACK_SPEED uses per-second suffix and 2-decimal format`() {
        // ws=10 -> 1.0 * (1 + 10*0.015) = 1.15 -> "1.15/s".
        val r = describe(mapOf(UpgradeType.ATTACK_SPEED to 10), emptyMap(), emptyMap(), UpgradeType.ATTACK_SPEED)
        assertEquals("1.15/s", r.current)
        assertNotEquals(r.current, r.next)
    }

    @Test
    fun `RANGE uses pixel suffix and 0-decimal format`() {
        val r = describe(mapOf(UpgradeType.RANGE to 5), emptyMap(), emptyMap(), UpgradeType.RANGE)
        // BASE_RANGE 300 * (1 + 5*0.02) = 330 -> "330 px".
        assertEquals("330 px", r.current)
    }

    @Test
    fun `HEALTH uses HP suffix and 0-decimal format`() {
        // ws=5 -> 1000 * (1 + 5*0.03) = 1150 -> "1150 HP".
        val r = describe(mapOf(UpgradeType.HEALTH to 5), emptyMap(), emptyMap(), UpgradeType.HEALTH)
        assertEquals("1150 HP", r.current)
    }

    @Test
    fun `HEALTH_REGEN uses per-second suffix and 2-decimal format`() {
        val r = describe(mapOf(UpgradeType.HEALTH_REGEN to 10), emptyMap(), emptyMap(), UpgradeType.HEALTH_REGEN)
        // BASE_REGEN 1.0 * (1 + 10*0.02) = 1.2 -> "1.20/s".
        // RO-12: 2-decimal precision needed because +2 %/level on a base ~1.3/s rounds away
        // under %.1f. Pre-RO-12 the readout showed "Now: 1.3/s -> 1.3/s" for a real Lv 0 -> Lv 1
        // upgrade, making the upgrade look like a no-op.
        assertEquals("1.20/s", r.current)
    }

    @Test
    fun `RO12 HEALTH_REGEN Lv 0 to Lv 1 produces a visibly different readout`() {
        // Direct regression for the screenshot-driven RO-12 finding: HEALTH_REGEN at L0 -> L1
        // must change the displayed string. BASE 1.0 * (1 + 0*0.02) = 1.00 -> "1.00/s";
        // BASE 1.0 * (1 + 1*0.02) = 1.02 -> "1.02/s".
        val r = describe(emptyMap(), emptyMap(), emptyMap(), UpgradeType.HEALTH_REGEN)
        assertEquals("1.00/s", r.current)
        assertEquals("1.02/s", r.next)
        assertNotEquals(
            r.current,
            r.next,
            "RO-12 Bug 4: HEALTH_REGEN L0 -> L1 must produce a visibly different readout. " +
                "Pre-fix the %.1f format collapsed both to \"1.0/s\".",
        )
    }

    @Test
    fun `KNOCKBACK uses pixel suffix and 1-decimal format`() {
        val r = describe(mapOf(UpgradeType.KNOCKBACK to 5), emptyMap(), emptyMap(), UpgradeType.KNOCKBACK)
        // BASE_KNOCKBACK 5f * (1 + 5*0.02) = 5.5 -> "5.5 px".
        assertEquals("5.5 px", r.current)
    }

    // ---- Additive percentage stats ----

    @Test
    fun `CRITICAL_CHANCE displays as percentage with 1-decimal`() {
        val r = describe(mapOf(UpgradeType.CRITICAL_CHANCE to 20), emptyMap(), emptyMap(), UpgradeType.CRITICAL_CHANCE)
        // 20 * 0.005 = 0.10 -> "10.0%".
        assertEquals("10.0%", r.current)
        // Next purchase: 21 * 0.005 = 0.105 -> "10.5%".
        assertEquals("10.5%", r.next)
    }

    @Test
    fun `CRITICAL_FACTOR uses multiplier prefix`() {
        val r = describe(mapOf(UpgradeType.CRITICAL_FACTOR to 5), emptyMap(), emptyMap(), UpgradeType.CRITICAL_FACTOR)
        // base 2.0 + 5*0.1 = 2.5 -> "×2.50".
        assertEquals("\u00d72.50", r.current)
    }

    @Test
    fun `DEFENSE_PERCENT displays as percentage`() {
        val r = describe(mapOf(UpgradeType.DEFENSE_PERCENT to 50), emptyMap(), emptyMap(), UpgradeType.DEFENSE_PERCENT)
        // 50 * 0.003 = 0.15 -> "15.0%".
        assertEquals("15.0%", r.current)
    }

    @Test
    fun `DEFENSE_ABSOLUTE displays as plus-N-blocked`() {
        val r = describe(mapOf(UpgradeType.DEFENSE_ABSOLUTE to 8), emptyMap(), emptyMap(), UpgradeType.DEFENSE_ABSOLUTE)
        assertEquals("+8 blocked", r.current)
    }

    @Test
    fun `THORN_DAMAGE shows reflect suffix`() {
        val r = describe(mapOf(UpgradeType.THORN_DAMAGE to 5), emptyMap(), emptyMap(), UpgradeType.THORN_DAMAGE)
        assertEquals("5.0% reflect", r.current)
    }

    @Test
    fun `LIFESTEAL clamps to 15 percent cap`() {
        // 100 * 0.002 = 0.20 -> would be 20 % uncapped; ResolveStats clamps to 0.15.
        val r = describe(mapOf(UpgradeType.LIFESTEAL to 100), emptyMap(), emptyMap(), UpgradeType.LIFESTEAL)
        assertEquals("15.0%", r.current)
    }

    @Test
    fun `DAMAGE_PER_METER uses per-meter suffix`() {
        val r = describe(mapOf(UpgradeType.DAMAGE_PER_METER to 5), emptyMap(), emptyMap(), UpgradeType.DAMAGE_PER_METER)
        assertEquals("+5.0%/m", r.current)
    }

    @Test
    fun `DEATH_DEFY clamps to 50 percent cap`() {
        // 100 * 0.01 = 1.0 -> would be 100 % uncapped; ResolveStats clamps to 0.50.
        val r = describe(mapOf(UpgradeType.DEATH_DEFY to 100), emptyMap(), emptyMap(), UpgradeType.DEATH_DEFY)
        assertEquals("50%", r.current)
    }

    // ---- Discrete-step upgrades ----

    @Test
    fun `MULTISHOT shows targets with threshold pluralisation`() {
        // Post-R4-02: per-level +1 target, baseline 1.
        val baseline = describe(emptyMap(), emptyMap(), emptyMap(), UpgradeType.MULTISHOT)
        assertEquals("1 target", baseline.current)
        assertEquals("2 targets", baseline.next)
        // ws=1, ir=0 -> 1 + 1 = 2 targets; next ir=1 -> 1 + 2 = 3 targets.
        val one = describe(mapOf(UpgradeType.MULTISHOT to 1), emptyMap(), emptyMap(), UpgradeType.MULTISHOT)
        assertEquals("2 targets", one.current)
        assertEquals("3 targets", one.next)
    }

    @Test
    fun `BOUNCE_SHOT shows bounces with threshold pluralisation`() {
        // Post-R4-02: per-level +1 bounce, baseline 0.
        // ws=1, ir=0 -> 1 bounce.
        val r = describe(mapOf(UpgradeType.BOUNCE_SHOT to 1), emptyMap(), emptyMap(), UpgradeType.BOUNCE_SHOT)
        assertEquals("1 bounce", r.current)
        // Next ir=1 -> 2 bounces.
        assertEquals("2 bounces", r.next)
    }

    @Test
    fun `ORBS counts with pluralisation`() {
        val r = describe(mapOf(UpgradeType.ORBS to 2), emptyMap(), emptyMap(), UpgradeType.ORBS)
        assertEquals("2 orbs", r.current)
        // Next ir=1 -> 3 orbs.
        assertEquals("3 orbs", r.next)
    }

    // ---- Cash-utility upgrades ----

    @Test
    fun `CASH_BONUS shows percent cash bonus`() {
        // L3 -> 3 * 3 % = +9 % cash.
        val r = describe(mapOf(UpgradeType.CASH_BONUS to 3), emptyMap(), emptyMap(), UpgradeType.CASH_BONUS)
        assertEquals("+9% cash", r.current)
        // Next ir=1 -> +12 % cash.
        assertEquals("+12% cash", r.next)
    }

    @Test
    fun `CASH_PER_WAVE shows flat cash per wave`() {
        // effectPerLevel=1, L15 -> +15 cash/wave.
        val r = describe(mapOf(UpgradeType.CASH_PER_WAVE to 15), emptyMap(), emptyMap(), UpgradeType.CASH_PER_WAVE)
        assertEquals("+15 cash/wave", r.current)
    }

    @Test
    fun `INTEREST clamps to 10 percent cap`() {
        // L20 (max) -> 20 * 0.5 = 10.0 % capped.
        val r = describe(mapOf(UpgradeType.INTEREST to 20), emptyMap(), emptyMap(), UpgradeType.INTEREST)
        assertEquals("10.0% interest", r.current)
        // L20 is the max level so next is null.
        assertNull(r.next)
    }

    @Test
    fun `FREE_UPGRADES clamps to 25 percent cap`() {
        // L25 (max) -> 25 * 1 = 25 % capped.
        val r = describe(mapOf(UpgradeType.FREE_UPGRADES to 25), emptyMap(), emptyMap(), UpgradeType.FREE_UPGRADES)
        assertEquals("25% free", r.current)
        assertNull(r.next)
    }

    // ---- Hidden-from-in-round upgrades (kept testable for Workshop reuse) ----

    @Test
    fun `V1X18 STEP_MULTIPLIER reads asymptotic curve formula`() {
        // V1X-18 / ADR-0015: asymptotic curve `1 - (1-0.05)^level` with 2-decimal-place format.
        // L50 → 1 - 0.95^50 ≈ 0.92306 → "+92.31% steps" (rounds up)
        val r = describe(mapOf(UpgradeType.STEP_MULTIPLIER to 50), emptyMap(), emptyMap(), UpgradeType.STEP_MULTIPLIER)
        assertEquals("+92.31% steps", r.current)
    }

    @Test
    fun `V1X18 STEP_MULTIPLIER L0 reads zero bonus`() {
        val r = describe(mapOf(UpgradeType.STEP_MULTIPLIER to 0), emptyMap(), emptyMap(), UpgradeType.STEP_MULTIPLIER)
        assertEquals("+0.00% steps", r.current)
    }

    @Test
    fun `V1X18 STEP_MULTIPLIER L100 reads near-100 percent (asymptote)`() {
        // L100 → 1 - 0.95^100 ≈ 0.99408 → "+99.41% steps"
        val r = describe(mapOf(UpgradeType.STEP_MULTIPLIER to 100), emptyMap(), emptyMap(), UpgradeType.STEP_MULTIPLIER)
        assertEquals("+99.41% steps", r.current)
    }

    @Test
    fun `V1X18 STEP_MULTIPLIER adjacent levels visibly differ at high range`() {
        // V1X-18 motivation: at high levels, adjacent levels should be visibly distinct in
        // the readout. Pre-V1X-18 L99 = L100 (both clamped to "+100% steps"); post-V1X-18
        // they differ by 0.03 percentage points (visible at 2-decimal format).
        val r99 = describe(mapOf(UpgradeType.STEP_MULTIPLIER to 99), emptyMap(), emptyMap(), UpgradeType.STEP_MULTIPLIER)
        val r100 = describe(mapOf(UpgradeType.STEP_MULTIPLIER to 100), emptyMap(), emptyMap(), UpgradeType.STEP_MULTIPLIER)
        assertNotEquals(r99.current, r100.current,
            "V1X-18 fixes the dead-content L99=L100 problem; readouts must differ")
    }

    @Test
    fun `RECOVERY_PACKAGES clamps to 50 percent cap`() {
        // L75 -> 75 * 1 = +75 % uncapped, clamped to +50 %.
        val r = describe(mapOf(UpgradeType.RECOVERY_PACKAGES to 75), emptyMap(), emptyMap(), UpgradeType.RECOVERY_PACKAGES)
        assertEquals("+50% heal", r.current)
    }

    // ---- Lab research stacks via the third multiplicative tier ----

    @Test
    fun `DAMAGE_RESEARCH outer multiplier stacks with DAMAGE`() {
        // ws=10 -> 12.0 baseline. With DAMAGE_RESEARCH L5 -> 12.0 * (1 + 5*0.05) = 15.0.
        val r = describe(
            workshopLevels = mapOf(UpgradeType.DAMAGE to 10),
            inRoundLevels = emptyMap(),
            labLevels = mapOf(ResearchType.DAMAGE_RESEARCH to 5),
            type = UpgradeType.DAMAGE,
        )
        assertEquals("15.0 dmg", r.current)
        // Next ir=1 -> 10 * 1.20 * 1.02 * 1.25 = 15.30 -> "15.3 dmg".
        assertEquals("15.3 dmg", r.next)
    }

    // ---- Card effects post-applied to mirror the live engine pipeline (RO-12) ----

    @Test
    fun `RO12 HEALTH readout reflects equipped WALKING_FORTRESS card multiplier`() {
        // WALKING_FORTRESS Lv 1: +50 % maxHealth. Without cards, ws=5 HEALTH -> 1000 * 1.15 = 1150.
        // With WALKING_FORTRESS Lv 1: 1150 * 1.50 = 1725 -> "1725 HP".
        // Pre-RO-12 the readout returned the pre-card value (1150 HP) while the live engine
        // ziggurat showed the post-card value (1725 HP) -- a 575 HP drift visible to the player.
        val cards = listOf(OwnedCard(1, CardType.WALKING_FORTRESS, 1, true))
        val r = describe(
            workshopLevels = mapOf(UpgradeType.HEALTH to 5),
            inRoundLevels = emptyMap(),
            labLevels = emptyMap(),
            type = UpgradeType.HEALTH,
            equippedCards = cards,
        )
        assertEquals(
            "1725 HP",
            r.current,
            "RO-12 Bug 3: HEALTH \"Now\" readout must include WALKING_FORTRESS +50 % multiplier " +
                "so the preview matches the engine ziggurat's post-card max HP.",
        )
        // Next purchase ir=1: 1000 * 1.15 * 1.03 = 1184.5. With WF +50 %: 1776.75 -> "1777 HP".
        assertEquals("1777 HP", r.next)
    }

    @Test
    fun `RO12 HEALTH readout with no cards equipped is unchanged from pre-RO12 baseline`() {
        // Default equippedCards = emptyList() must preserve pre-RO-12 behaviour for the 25
        // existing tests below + every Workshop-screen call site that doesn't pass cards.
        val r = describe(
            workshopLevels = mapOf(UpgradeType.HEALTH to 5),
            inRoundLevels = emptyMap(),
            labLevels = emptyMap(),
            type = UpgradeType.HEALTH,
        )
        assertEquals("1150 HP", r.current)
    }

    // ---- R4-03: RAPID_FIRE periodic-burst readout ----
    // The Workshop ATTACK upgrade is hidden from the in-round menu but the use case still
    // formats its readout (so the same code can power a future Workshop-screen preview).
    // Readout shape: "inactive" at L0; "{i}s/{d}s/{m}\u00d7" between L1 and L9; "permanent/{m}\u00d7"
    // at L10 where duration matches interval.

    @Test
    fun `R403 RAPID_FIRE shows inactive at L0`() {
        val r = describe(emptyMap(), emptyMap(), emptyMap(), UpgradeType.RAPID_FIRE)
        assertEquals("inactive", r.current)
        // L1 preview = "60s/5s/2.0\u00d7" so the player can see what the first purchase unlocks.
        assertEquals("60s/5s/2.0\u00d7", r.next)
    }

    @Test
    fun `R403 RAPID_FIRE L1 shows full triple readout`() {
        val r = describe(
            workshopLevels = mapOf(UpgradeType.RAPID_FIRE to 1),
            inRoundLevels = emptyMap(),
            labLevels = emptyMap(),
            type = UpgradeType.RAPID_FIRE,
        )
        assertEquals("60s/5s/2.0\u00d7", r.current)
        // L2 interval = 60 - 30/9 \u2248 56.67 \u2192 "57s" rounded; duration = 5 + 25/9 \u2248 7.78 \u2192 "8s";
        // multiplier = 2 + 1/9 \u2248 2.11 \u2192 "2.1\u00d7".
        assertEquals("57s/8s/2.1\u00d7", r.next)
    }

    @Test
    fun `R403 RAPID_FIRE L9 still shows finite triple readout`() {
        val r = describe(
            workshopLevels = mapOf(UpgradeType.RAPID_FIRE to 9),
            inRoundLevels = emptyMap(),
            labLevels = emptyMap(),
            type = UpgradeType.RAPID_FIRE,
        )
        // L9 interval = 60 - 30 * 8/9 \u2248 33.33 \u2192 "33s"; duration = 5 + 25 * 8/9 \u2248 27.22 \u2192 "27s";
        // multiplier = 2 + 8/9 \u2248 2.89 \u2192 "2.9\u00d7". Below the convergence point, so non-permanent.
        assertEquals("33s/27s/2.9\u00d7", r.current)
        // L10 \u2192 permanent.
        assertEquals("permanent/3.0\u00d7", r.next)
    }

    @Test
    fun `R403 RAPID_FIRE L10 collapses to permanent readout with next null`() {
        val r = describe(
            workshopLevels = mapOf(UpgradeType.RAPID_FIRE to 10),
            inRoundLevels = emptyMap(),
            labLevels = emptyMap(),
            type = UpgradeType.RAPID_FIRE,
        )
        assertEquals("permanent/3.0\u00d7", r.current)
        assertNull(r.next, "At maxLevel = 10, next-purchase readout must be null (renders as MAX)")
    }

    // ---- Smoke test: every visible upgrade produces a non-empty current readout ----

    @Test
    fun `every visible upgrade type produces a non-empty current readout at L0`() {
        // Guards against a future enum addition silently falling through to a missing case.
        // Phase A wired all 14 stat-bearing entries plus the 4 cash utilities; Phase B
        // hidden-but-tested entries are STEP_MULTIPLIER + RECOVERY_PACKAGES.
        UpgradeType.entries.forEach { type ->
            val r = describe(emptyMap(), emptyMap(), emptyMap(), type)
            assertTrue(r.current.isNotEmpty(), "$type produced an empty current readout")
        }
    }
}
