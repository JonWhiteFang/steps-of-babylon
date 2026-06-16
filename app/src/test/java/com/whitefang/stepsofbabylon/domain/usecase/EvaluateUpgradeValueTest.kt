package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ranks combat upgrades by Δcombat-power ÷ step-cost and flags the single Best Buy (#29, spec §3-§4).
 *
 * Baseline arithmetic (all-zero workshop, ResolvedStats defaults: damage 10, attackSpeed 1, crit 0):
 *   currentPower = 10.
 *   DAMAGE L0->L1:    damage 10.2 -> power 10.2, Δ 0.2,  cost 50  -> value 0.004
 *   ATTACK_SPEED 0->1: spd 1.015  -> power 10.15, Δ 0.15, cost 75  -> value 0.002
 *   CRITICAL_CHANCE:  crit 0.005  -> power 10.05, Δ 0.05, cost 100 -> value 0.0005
 *   CRITICAL_FACTOR:  crit 0, factor 2.1 -> power 10.0, Δ 0 -> EXCLUDED
 *   RANGE / DAMAGE_PER_METER / RAPID_FIRE: not in index -> Δ 0 -> EXCLUDED
 * So DAMAGE is the Best Buy.
 */
class EvaluateUpgradeValueTest {

    private val sut = EvaluateUpgradeValue()

    private val attackCandidates = listOf(
        UpgradeType.DAMAGE, UpgradeType.ATTACK_SPEED, UpgradeType.CRITICAL_CHANCE,
        UpgradeType.CRITICAL_FACTOR, UpgradeType.RANGE, UpgradeType.DAMAGE_PER_METER, UpgradeType.RAPID_FIRE,
    )

    @Test
    fun `only Δpower-positive combat upgrades are returned`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val types = result.map { it.type }.toSet()
        assertEquals(setOf(UpgradeType.DAMAGE, UpgradeType.ATTACK_SPEED, UpgradeType.CRITICAL_CHANCE), types)
    }

    @Test
    fun `critical factor is excluded when crit chance is zero`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_FACTOR))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `critical factor is included once crit chance is non-zero`() {
        // With some CRITICAL_CHANCE levels, a CRITICAL_FACTOR purchase now raises power -> included.
        val levels = mapOf(UpgradeType.CRITICAL_CHANCE to 20) // crit 0.10
        val result = sut(levels, stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_FACTOR))
        assertEquals(1, result.size)
        assertEquals(UpgradeType.CRITICAL_FACTOR, result.first().type)
        assertTrue(result.first().valuePerStep > 0.0)
    }

    @Test
    fun `damage is the best buy from an all-zero attack tab`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertEquals(UpgradeType.DAMAGE, best.type)
        assertTrue(best.bestBuyAffordable)
    }

    @Test
    fun `exactly one upgrade is flagged best buy`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        assertEquals(1, result.count { it.isBestBuy })
    }

    @Test
    fun `percent-per-k-steps is computed correctly and positive`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = listOf(UpgradeType.DAMAGE))
        // value 0.004 ÷ currentPower 10 × 1000 × 100 = 40.0
        // (sanity: a 50-step DAMAGE level = +2% power, so ~20 levels ≈ 1,000 steps ≈ +40% power)
        assertEquals(40.0, result.single().percentPerKSteps, 1e-6)
    }

    @Test
    fun `best buy bar fraction is full and lower-value bars are proportional`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val damage = result.single { it.type == UpgradeType.DAMAGE }
        val attackSpeed = result.single { it.type == UpgradeType.ATTACK_SPEED }
        assertEquals(1.0f, damage.barFraction, 1e-6f)            // highest pct -> full bar
        assertTrue(attackSpeed.barFraction in 0.0f..1.0f)
        assertTrue(attackSpeed.barFraction < damage.barFraction) // lower value -> shorter bar
    }

    @Test
    fun `when nothing is affordable the best buy falls back to highest value, greyed`() {
        // Balance below DAMAGE's base cost (50) -> no candidate affordable.
        val result = sut(emptyMap(), stepBalance = 0, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertEquals(UpgradeType.DAMAGE, best.type)       // still the highest-value
        assertFalse(best.bestBuyAffordable)               // but flagged unaffordable (greyed)
    }

    @Test
    fun `best buy prefers the highest-value AFFORDABLE upgrade`() {
        // Make DAMAGE unaffordable by pricing it up via level, leaving ATTACK_SPEED/CRIT affordable.
        // DAMAGE L40 cost = ceil(50 * 1.12^40) ≈ 4653 (large); ATTACK_SPEED L0 = 75, CRIT L0 = 100.
        val levels = mapOf(UpgradeType.DAMAGE to 40)
        val costDamage = CalculateUpgradeCost()(UpgradeType.DAMAGE, 40)
        val balance = costDamage - 1 // can't afford DAMAGE, can afford ATTACK_SPEED(75) and CRIT(100)
        assertTrue(balance >= 100, "sanity: balance must still afford the cheaper candidates")
        val result = sut(levels, stepBalance = balance, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertTrue(best.bestBuyAffordable)
        assertTrue(best.type != UpgradeType.DAMAGE, "DAMAGE is unaffordable here, so it cannot be the affordable best buy")
    }

    @Test
    fun `maxed candidates are excluded`() {
        // CRITICAL_CHANCE at cap (160) -> excluded by the isAtMax guard (before any Δpower compute).
        val result = sut(mapOf(UpgradeType.CRITICAL_CHANCE to 160), stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_CHANCE))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty candidate set yields empty result`() {
        assertTrue(sut(emptyMap(), stepBalance = 100_000, candidates = emptyList()).isEmpty())
    }

    @Test
    fun `best buy selection is deterministic across identical inputs`() {
        // The `.thenBy { it.type.ordinal }` value-tie break is verified by inspection: with the real
        // CombatPower / CalculateUpgradeCost (both final classes, not injectable), no two candidates
        // produce an exactly-equal valuePerStep, so a genuine tie isn't constructible here without
        // exposing private internals. This asserts the achievable property — stable selection.
        val a = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates).single { it.isBestBuy }.type
        val b = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates).single { it.isBestBuy }.type
        assertEquals(a, b)
    }
}
