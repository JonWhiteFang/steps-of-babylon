package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.ZigguratState
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [ZigguratDamageResolver] — the ziggurat damage/defense/death-defy/second-wind/
 * shake-threshold resolution hoisted out of presentation `CombatResolver.applyDamageToZiggurat`
 * (#306, ADR-0012 Phase 5 Slice 1). Drives the resolver against a real [ZigguratState] via the
 * [com.whitefang.stepsofbabylon.domain.battle.entity.Damageable] port, with injected RNG + a spy
 * consumeSecondWind lambda. No Robolectric, no Android.
 */
class ZigguratDamageResolverTest {
    private fun fixedRandom(value: Double) =
        object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0

            override fun nextDouble(): Double = value
        }

    private fun state(maxHealth: Double = 100.0) = ZigguratState(ResolvedStats(maxHealth = maxHealth))

    @Test
    fun `normal hit subtracts mitigated damage`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 10.0,
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertEquals(90.0, s.currentHp, 1e-9)
        assertFalse(outcome.crossedShakeThreshold, "1.0 → 0.9 ratio does not cross 0.25")
    }

    @Test
    fun `defense percent reduces the HP lost`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 10.0,
            stats = ResolvedStats(maxHealth = 100.0, defensePercent = 0.5),
            secondWindHpPercent = 0.0,
            consumeSecondWind = { false },
        )
        assertEquals(95.0, s.currentHp, 1e-9, "50% defense halves the 10 damage to 5")
    }

    @Test
    fun `overkill floors currentHp at zero`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 500.0,
            stats = ResolvedStats(maxHealth = 100.0),
            secondWindHpPercent = 0.0,
            consumeSecondWind = { false },
        )
        assertEquals(0.0, s.currentHp, 1e-9)
    }

    @Test
    fun `death-defy success sets currentHp to 1 and does not consume second wind`() {
        val s = state()
        var consumeCalls = 0
        val resolver = ZigguratDamageResolver(random = fixedRandom(0.0)) // 0.0 < 0.5 → survives
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
                consumeSecondWind = {
                    consumeCalls++
                    true
                },
            )
        assertEquals(1.0, s.currentHp, 1e-9)
        assertEquals(0, consumeCalls, "death-defy priority: second wind must not be consulted")
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `failed death-defy roll falls through to second wind`() {
        val s = state()
        var consumeCalls = 0
        val resolver = ZigguratDamageResolver(random = fixedRandom(0.99)) // 0.99 < 0.5 false → fails
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
                consumeSecondWind = {
                    consumeCalls++
                    true
                },
            )
        assertEquals(50.0, s.currentHp, 1e-9, "fall-through restores maxHp × 0.5")
        assertEquals(1, consumeCalls, "the fall-through must consume second wind once")
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `second wind restores maxHp times percent when death-defy is off`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
                secondWindHpPercent = 0.5,
                consumeSecondWind = { true },
            )
        assertEquals(50.0, s.currentHp, 1e-9)
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `second wind unavailable takes the lethal damage`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 200.0,
            stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
            secondWindHpPercent = 0.5,
            consumeSecondWind = { false }, // already used
        )
        assertEquals(0.0, s.currentHp, 1e-9)
    }

    @Test
    fun `crossing below 25 percent flags the shake threshold`() {
        val s = state()
        s.currentHp = 30.0 // 30% of 100
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 10.0, // → 20.0 = 20%
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertEquals(20.0, s.currentHp, 1e-9)
        assertTrue(outcome.crossedShakeThreshold, "30% → 20% crosses the 0.25 boundary")
    }

    @Test
    fun `a hit already below 25 percent does not re-flag the shake threshold`() {
        val s = state()
        s.currentHp = 20.0 // already 20%
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 5.0, // → 15%
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertFalse(outcome.crossedShakeThreshold, "prevRatio was already ≤ 0.25 → not a crossing")
    }
}
