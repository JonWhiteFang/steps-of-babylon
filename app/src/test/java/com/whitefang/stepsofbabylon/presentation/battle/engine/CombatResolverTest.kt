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
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
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
        val host = FakeCombatHost(zig, currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5))
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
}
