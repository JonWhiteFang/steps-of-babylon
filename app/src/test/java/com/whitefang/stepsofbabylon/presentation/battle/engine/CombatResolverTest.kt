package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeStrings
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused pure-JVM unit tests for [CombatResolver] (#231 GameEngine decomposition) against a small
 * fake [CombatHost]. Exercises the kill-reward and wave-complete cash paths directly — no Robolectric,
 * no render(). The stubbed Android `Paint()` is a no-op
 * (`testOptions.unitTests.isReturnDefaultValues = true`); combat LOGIC never reads it.
 */
class CombatResolverTest {
    private class FakeCombatHost(
        override val ziggurat: ZigguratEntity?,
        override val simulation: Simulation = Simulation(),
        override val currentStats: ResolvedStats = ResolvedStats(),
        override val secondWindHpPercent: Double = 0.0,
        override val reducedMotion: Boolean = true,
        private val secondWindAvailable: Boolean = true,
    ) : CombatHost {
        override val conditions: BattleConditionEffects = BattleConditionEffects()
        override val tier: Int = 1
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = reducedMotion)
        override val soundManager = null
        override val strings: Strings? = FakeStrings()
        override val fortuneMultiplier: Double = 1.0
        override val cashResearchMultiplier: Double = 1.0
        override val cashBonusPercent: Double = 0.0

        var consumeSecondWindCalls = 0
            private set
        private var secondWindConsumed = false

        override fun consumeSecondWind(): Boolean {
            consumeSecondWindCalls++
            if (secondWindConsumed || !secondWindAvailable) return false
            secondWindConsumed = true
            return true
        }

        val pending = mutableListOf<Entity>()

        override fun addPending(entity: Entity) {
            pending.add(entity)
        }

        override fun aliveEnemies(): List<EnemyEntity> = emptyList()

        override fun nearestEnemies(n: Int): List<EnemyEntity> = emptyList()

        override fun wsLevel(type: UpgradeType): Int = 0

        override fun applyLifesteal(healAmount: Double) {}
    }

    private fun makeZiggurat(): ZigguratEntity =
        ZigguratEntity(
            screenWidth = 1080f,
            screenHeight = 1920f,
            initialStats = ResolvedStats(),
            findNearestEnemies = { emptyList<EnemyEntity>() },
            onFireProjectile = { _, _, _, _ -> },
        )

    /** Deterministic RNG: nextDouble() always returns [value]; roll < chance is fully controllable. */
    private fun fixedRandom(value: Double) =
        object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0

            override fun nextDouble(): Double = value
        }

    private fun makeZigguratWithHealth(maxHealth: Double): ZigguratEntity =
        ZigguratEntity(
            screenWidth = 1080f,
            screenHeight = 1920f,
            initialStats = ResolvedStats(maxHealth = maxHealth),
            findNearestEnemies = { emptyList<EnemyEntity>() },
            onFireProjectile = { _, _, _, _ -> },
        )

    private fun makeEnemy(
        zig: ZigguratEntity,
        type: EnemyType,
    ): EnemyEntity =
        EnemyEntity(
            enemyType = type,
            currentHp = 1.0,
            maxHp = 1.0,
            speed = 0f,
            damage = 0.0,
            targetX = zig.originX,
            targetY = zig.originY,
            onDeath = { },
        ).apply {
            x = zig.originX
            y = zig.originY + 200f
        }

    @Test
    fun `handleEnemyDeath credits positive cash and increments the kill counter`() {
        val zig = makeZiggurat()
        val host = FakeCombatHost(zig)
        val resolver = CombatResolver(host)
        val enemy = makeEnemy(zig, EnemyType.BASIC)

        val cashBefore = host.simulation.cash
        val killsBefore = host.simulation.totalEnemiesKilled

        resolver.handleEnemyDeath(enemy)

        assertTrue(
            host.simulation.cash > cashBefore,
            "a BASIC kill must credit positive cash to host.simulation " +
                "(before=$cashBefore, after=${host.simulation.cash})",
        )
        assertEquals(
            killsBefore + 1,
            host.simulation.totalEnemiesKilled,
            "handleEnemyDeath must increment the simulation kill counter",
        )
    }

    @Test
    fun `handleWaveComplete with no upgrades credits exactly BASE_CASH_PER_WAVE`() {
        val zig = makeZiggurat()
        val host = FakeCombatHost(zig) // wsLevel() == 0 for every type → no flat bonus / interest
        val resolver = CombatResolver(host)

        resolver.handleWaveComplete(1)

        assertEquals(
            SimulationMath.BASE_CASH_PER_WAVE,
            host.simulation.cash,
            "handleWaveComplete(1) with no upgrades must credit exactly BASE_CASH_PER_WAVE (20L)",
        )
    }

    // --- #306 pre-hoist characterization: applyDamageToZiggurat HP outcomes (baseline oracle) ---

    @Test
    fun `CHAR death-defy success on a lethal hit leaves the ziggurat at 1 HP`() {
        val zig = makeZigguratWithHealth(100.0)
        val host =
            FakeCombatHost(
                zig,
                currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                // A second wind is AVAILABLE (percent > 0) so consumeSecondWindCalls == 0 genuinely
                // proves death-defy takes priority — not just that second wind was disabled.
                secondWindHpPercent = 0.5,
            )
        val resolver = CombatResolver(host, random = fixedRandom(0.0)) // 0.0 < 0.5 → defy succeeds

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(1.0, zig.currentHp, 1e-9, "death-defy success must set currentHp to exactly 1.0")
        assertEquals(0, host.consumeSecondWindCalls, "death-defy success must not touch second wind")
    }

    @Test
    fun `CHAR second wind restores maxHp times percent on a lethal hit`() {
        val zig = makeZigguratWithHealth(100.0)
        val host =
            FakeCombatHost(
                zig,
                currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
                secondWindHpPercent = 0.5,
            )
        val resolver = CombatResolver(host, random = fixedRandom(0.99))

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(50.0, zig.currentHp, 1e-9, "second wind must restore maxHp × 0.5 = 50.0")
        assertEquals(1, host.consumeSecondWindCalls, "second wind must be consumed exactly once")
    }

    @Test
    fun `CHAR failed death-defy roll falls through to second wind`() {
        val zig = makeZigguratWithHealth(100.0)
        val host =
            FakeCombatHost(
                zig,
                currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
            )
        val resolver = CombatResolver(host, random = fixedRandom(0.99)) // 0.99 < 0.5 false → roll fails

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(50.0, zig.currentHp, 1e-9, "a failed defy roll must fall through to second wind (→ 50.0)")
        assertEquals(1, host.consumeSecondWindCalls, "the fall-through must consume second wind exactly once")
    }

    // --- #306 (ADR-0012 Phase 5): adapter screen-shake glue on a <25% HP crossing ---
    // The hoist moved the shake side-effect out of the branch into the adapter's
    // `if (crossedShakeThreshold && !reducedMotion) screenShake.trigger(...)`. ScreenShake exposes no
    // "was triggered" flag; dx/dy stay 0 until update() runs, so we trigger via a crossing hit, pump
    // one update(dt), and observe dy. (dx can land near-zero; dy = sin(0.1*47) * amp ≈ -2.5 is robust.)

    @Test
    fun `CHAR crossing 25 percent fires screen shake when motion is enabled`() {
        val zig = makeZigguratWithHealth(100.0)
        val host = FakeCombatHost(zig, currentStats = ResolvedStats(maxHealth = 100.0), reducedMotion = false)
        zig.currentHp = 30.0 // 30% → after 10 damage → 20% : crosses the 25% shake threshold
        val resolver = CombatResolver(host)

        resolver.applyDamageToZiggurat(rawDamage = 10.0, attacker = null)
        host.effectEngine!!.update(0.1f) // pump the shake: dx/dy stay 0 until an update runs

        assertTrue(
            host.effectEngine.screenShake.dy != 0f,
            "crossing 25% with motion enabled must fire the shake (dy should be non-zero after update)",
        )
    }

    @Test
    fun `CHAR crossing 25 percent does not fire screen shake under reduced motion`() {
        val zig = makeZigguratWithHealth(100.0)
        val host = FakeCombatHost(zig, currentStats = ResolvedStats(maxHealth = 100.0)) // reducedMotion defaults true
        zig.currentHp = 30.0 // same crossing hit as the positive case
        val resolver = CombatResolver(host)

        resolver.applyDamageToZiggurat(rawDamage = 10.0, attacker = null)
        host.effectEngine!!.update(0.1f)

        assertEquals(
            0f,
            host.effectEngine.screenShake.dy,
            "reduced motion must suppress the shake (adapter's !reducedMotion gate; dy stays 0)",
        )
    }

    /**
     * #306 Slice 2 characterization oracle for the SCATTER split. Asserts the OBSERVABLE outcome rather
     * than the arithmetic, so it holds identically across the hoist to the pure-domain `ScatterSplit`.
     * Verified green against both the pre-hoist inline path and the post-hoist helper.
     *
     * The parent is deliberately given **`currentHp` != `maxHp`** so the test can distinguish them: the
     * children derive from the parent's `maxHp` (40 → 20 each), so a regression that passed
     * `enemy.currentHp` (10) into the split would yield 5.0 and fail here. With a 40/40 parent that
     * mix-up would have been invisible. Child X offsets are asserted too, so the adapter cannot silently
     * ignore `descriptor.offsetX` while the pure helper's own tests still pass.
     */
    @Test
    fun `handleEnemyDeath on a SCATTER enemy spawns half-HP BASIC children`() {
        val zig = makeZiggurat()
        val host = FakeCombatHost(zig)
        // Seed the RNG so the 2..3 roll is deterministic run-to-run.
        val resolver = CombatResolver(host, random = kotlin.random.Random(1))
        val parentX = zig.originX
        val scatter =
            EnemyEntity(
                enemyType = EnemyType.SCATTER,
                currentHp = 10.0, // deliberately NOT maxHp — see the KDoc
                maxHp = 40.0,
                speed = 0f,
                damage = 12.0,
                targetX = zig.originX,
                targetY = zig.originY,
                onDeath = { },
            ).apply {
                x = parentX
                y = zig.originY + 200f
            }

        resolver.handleEnemyDeath(scatter)

        val children = host.pending.filterIsInstance<EnemyEntity>()
        assertTrue(children.size in 2..3, "SCATTER must spawn 2..3 children (was ${children.size})")
        children.forEach { child ->
            assertEquals(EnemyType.BASIC, child.enemyType, "SCATTER children are BASIC")
            assertEquals(20.0, child.maxHp, 1e-9, "half the parent MAX hp (40 → 20), not half currentHp (10 → 5)")
            assertEquals(20.0, child.currentHp, 1e-9, "children spawn at full health for their own maxHp")
            assertEquals(6.0, child.damage, 1e-9, "each child gets half the parent damage (12 → 6)")
        }
        // Pins that the adapter actually applies descriptor.offsetX: `(i - n / 2f) * 15f` off the parent X.
        val n = children.size
        val expectedX = (0 until n).map { parentX + (it - n / 2f) * 15f }
        assertEquals(expectedX, children.map { it.x }, "children fan out from the parent X by (i - n/2f)*15")
    }
}
