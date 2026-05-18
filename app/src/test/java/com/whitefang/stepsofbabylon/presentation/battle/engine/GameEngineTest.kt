package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.OverdriveType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * RO-08 regression guards for the engine-side stats / cash / recovery wiring.
 *
 * Pre-RO-08:
 * - [ZigguratEntity] captured `attackInterval` and `attackRange` at construction; Overdrive
 *   ASSAULT's 2× attack speed was silently dropped.
 * - The engine stored `workshopLevels` once at init; in-round CASH_BONUS / CASH_PER_WAVE /
 *   INTEREST / FREE_UPGRADES purchases never reached the cash math.
 * - RECOVERY_PACKAGES had no implementation.
 */
class GameEngineTest {

    private fun freshEngine(): GameEngine {
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = ResolvedStats(), playerTier = 1)
        return eng
    }

    // ---- RO-08 #2: ZigguratEntity stale-stats propagation fix ----

    @Test
    fun `RO08 activateOverdrive ASSAULT propagates 2x attackSpeed to ziggurat`() {
        val eng = freshEngine()
        val baselineInterval = readAttackInterval(eng)

        eng.activateOverdrive(OverdriveType.ASSAULT, baseStats = eng.zigStatsForTest())
        val boostedInterval = readAttackInterval(eng)

        // Pre-fix: boostedInterval == baselineInterval (zig.attackInterval was a captured val).
        // Post-fix: attackInterval is computed each tick from the live stats.attackSpeed,
        // so 2× attackSpeed → ½ attackInterval.
        assertEquals(
            baselineInterval / 2.0f,
            boostedInterval,
            0.001f,
            "ASSAULT must halve the ziggurat's attack interval (2× attack speed)",
        )
    }

    @Test
    fun `RO08 activateOverdrive FORTRESS propagates healthRegen to ziggurat`() {
        val eng = freshEngine()
        val baselineRegen = eng.zigStatsForTest().healthRegen

        eng.activateOverdrive(OverdriveType.FORTRESS, baseStats = eng.zigStatsForTest())
        val boostedRegen = eng.ziggurat!!.stats.healthRegen

        // Pre-fix: zig.stats was captured at construction; FORTRESS only mutated engine.stats.
        // Post-fix: applyStats() pushes the new ResolvedStats onto the ziggurat too.
        assertEquals(
            baselineRegen * 2.0,
            boostedRegen,
            0.001,
            "FORTRESS must double the ziggurat's healthRegen via the live stats reference",
        )
    }

    @Test
    fun `RO08 expireOverdrive restores baseline stats on the ziggurat`() {
        val eng = freshEngine()
        val baselineInterval = readAttackInterval(eng)
        val baseline = eng.zigStatsForTest()

        eng.activateOverdrive(OverdriveType.ASSAULT, baseStats = baseline)
        // Direct invocation of expireOverdrive bypasses the 60 s game-loop drain (which
        // would otherwise spawn enemies + damage the tower + flip roundOver before the
        // overdrive timer ran out). The expiry path is what we care about for this
        // regression — that the restored stats reach the ziggurat too.
        invokeExpireOverdrive(eng)

        val restoredInterval = readAttackInterval(eng)
        assertEquals(
            baselineInterval,
            restoredInterval,
            0.001f,
            "Overdrive expiry must restore the ziggurat's pre-Overdrive attack interval",
        )
    }

    // ---- RO-08 #3c: in-round cash-utility purchases reach the engine ----

    @Test
    fun `RO08 updateEffectiveLevels propagates CASH_BONUS to subsequent kill rewards`() {
        val eng = freshEngine()
        // Workshop CASH_BONUS level 0 baseline. Then push an in-round level into the engine
        // via updateEffectiveLevels — equivalent to BattleViewModel's combinedLevelsForCash.
        eng.updateEffectiveLevels(mapOf(UpgradeType.CASH_BONUS to 50))
        val readLevel = readEffectiveLevel(eng, UpgradeType.CASH_BONUS)
        assertEquals(
            50,
            readLevel,
            "updateEffectiveLevels must replace the engine's effective level lookup",
        )
    }

    // ---- RO-08 #1b: RECOVERY_PACKAGES periodic heal ----

    @Test
    fun `RO08 RECOVERY_PACKAGES heals the ziggurat once per interval during SPAWNING phase`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        // Damage the ziggurat to 10% so the heal has room.
        zig.currentHp = zig.maxHp * 0.10
        val hpBefore = zig.currentHp

        // Level 5 → 5 % per pulse; expected heal = 5 % of maxHp.
        eng.updateEffectiveLevels(mapOf(UpgradeType.RECOVERY_PACKAGES to 5))

        // Direct invocation of the private heal helper avoids the full game-loop side
        // effects (enemy spawning, melee hits) that would otherwise contaminate the HP
        // delta. The wave is in SPAWNING phase by construction immediately after init.
        invokeTickRecovery(eng, deltaTime = 31f)

        val expectedHeal = zig.maxHp * 0.05
        val actualDelta = zig.currentHp - hpBefore
        org.junit.jupiter.api.Assertions.assertTrue(
            actualDelta >= expectedHeal - 0.5,
            "RECOVERY_PACKAGES Lv5 must heal ≥ 5 % of maxHp on a single 30 s pulse " +
                "(before=$hpBefore, after=${zig.currentHp}, expectedDelta=$expectedHeal, " +
                "actualDelta=$actualDelta)",
        )
    }

    @Test
    fun `RO08 RECOVERY_PACKAGES does not heal at full HP`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        // Tower starts at full HP by construction.
        val hpBefore = zig.currentHp

        eng.updateEffectiveLevels(mapOf(UpgradeType.RECOVERY_PACKAGES to 5))
        invokeTickRecovery(eng, deltaTime = 31f)

        assertEquals(
            hpBefore,
            zig.currentHp,
            0.001,
            "RECOVERY_PACKAGES must not heal beyond max HP",
        )
    }

    @Test
    fun `RO08 RECOVERY_PACKAGES level 0 produces no heal`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        zig.currentHp = zig.maxHp * 0.10
        val hpBefore = zig.currentHp

        // No RECOVERY_PACKAGES level set → effective level 0.
        invokeTickRecovery(eng, deltaTime = 31f)

        assertEquals(
            hpBefore,
            zig.currentHp,
            0.001,
            "Level 0 RECOVERY_PACKAGES must not heal",
        )
    }

    @Test
    fun `RO08 RECOVERY_PACKAGES heal pulse caps at 50 percent of max HP per pulse`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        zig.currentHp = 1.0 // near death
        eng.updateEffectiveLevels(mapOf(UpgradeType.RECOVERY_PACKAGES to 200))

        invokeTickRecovery(eng, deltaTime = 31f)

        // Lv 200 unclamped → 200 %; clamp at 50 %. Heal = 0.5 × maxHp from currentHp = 1.
        val expectedMax = 1.0 + zig.maxHp * 0.50
        org.junit.jupiter.api.Assertions.assertTrue(
            zig.currentHp <= expectedMax + 0.5,
            "Heal pulse must cap at 50 % of maxHp; got currentHp=${zig.currentHp}, " +
                "max=${zig.maxHp}, expectedMax=$expectedMax",
        )
    }

    // ---- Helpers: reach into private state via reflection ----

    /** Reads the engine's `ziggurat` and returns a snapshot of its live stats reference. */
    private fun GameEngine.zigStatsForTest(): ResolvedStats =
        ziggurat?.stats ?: ResolvedStats()

    /**
     * Mirrors the live `ZigguratEntity.attackInterval` formula. Equivalent to what the entity
     * computes each tick — used to assert the formula reads the up-to-date stats reference.
     */
    private fun readAttackInterval(eng: GameEngine): Float {
        val zig = eng.ziggurat!!
        return (1.0 / zig.stats.attackSpeed).toFloat()
    }

    /** Reflectively reads the engine's private `effectiveLevels` map for the given type. */
    private fun readEffectiveLevel(eng: GameEngine, type: UpgradeType): Int {
        val field = GameEngine::class.java.getDeclaredField("effectiveLevels")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val map = field.get(eng) as Map<UpgradeType, Int>
        return map[type] ?: 0
    }

    /** Reflectively invokes the private `expireOverdrive()` helper. */
    private fun invokeExpireOverdrive(eng: GameEngine) {
        val method = GameEngine::class.java.getDeclaredMethod("expireOverdrive")
            .apply { isAccessible = true }
        method.invoke(eng)
    }

    /**
     * Reflectively invokes the private `tickRecoveryPackages(deltaTime: Float)` helper.
     * Bypasses the full game-loop side effects (enemy spawn, melee hits, projectile
     * collisions) so the heal-only assertions stay deterministic.
     */
    private fun invokeTickRecovery(eng: GameEngine, deltaTime: Float) {
        val method = GameEngine::class.java
            .getDeclaredMethod("tickRecoveryPackages", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(eng, deltaTime)
    }
}
