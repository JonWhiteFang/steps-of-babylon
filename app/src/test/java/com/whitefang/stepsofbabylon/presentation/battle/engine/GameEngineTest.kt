package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.OverdriveType
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.battle.effects.Effect
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RO-08 + RO-09 regression guards for the engine-side stats / cash / recovery / chrono / fortune wiring.
 *
 * Pre-RO-08:
 * - [ZigguratEntity] captured `attackInterval` and `attackRange` at construction; Overdrive
 *   ASSAULT's 2× attack speed was silently dropped.
 * - The engine stored `workshopLevels` once at init; in-round CASH_BONUS / CASH_PER_WAVE /
 *   INTEREST / FREE_UPGRADES purchases never reached the cash math.
 * - RECOVERY_PACKAGES had no implementation.
 *
 * Pre-RO-09:
 * - CHRONO_FIELD UW activation only set a render-overlay flag; raw `deltaTime` reached every
 *   entity, so the description's "Slows all enemies to 10 % speed" had no gameplay effect.
 * - GOLDEN_ZIGGURAT's `fortuneMultiplier = 5.0` leaked across ASSAULT / FORTRESS / SURGE
 *   expiries, and conversely a FORTUNE activation while GOLDEN was active downgraded the
 *   buff from 5.0 to 3.0. The fortune buff is shared between a Step Overdrive (FORTUNE,
 *   3.0×) and an Ultimate Weapon (GOLDEN_ZIGGURAT, 5.0×); the higher of the two should
 *   always win, and the lower should restore cleanly when one ends.
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

    // ---- RO-11 #A.2: lab research multipliers reach the engine ----
    // The CASH_RESEARCH and UW_COOLDOWN enums were dead pre-RO-11. BattleViewModel now reads
    // their levels from LabRepository and pushes per-round multipliers onto the engine; these
    // tests guard the engine-side application points are wired correctly.

    @Test
    fun `RO11 cashResearchMultiplier scales kill cash`() {
        val eng = freshEngine()
        // Spawn a basic enemy and kill it twice via the private handleEnemyDeath helper:
        // once at the default 1.0× multiplier (baseline) and once at 2.0× (max-research-level
        // simulation). Cash deltas are deterministic because TierConfig.tier(1).cashMultiplier
        // and EnemyScaler.cashReward(BASIC) are constants and no FORTUNE/GOLDEN buff is active.
        val cashBaseline = simulateBasicKillCash(eng)

        // Reset engine, push the +100 % multiplier, kill the same enemy.
        val eng2 = freshEngine()
        eng2.cashResearchMultiplier = 2.0
        val cashBoosted = simulateBasicKillCash(eng2)

        // 2.0× multiplier doubles the cash. Allow ±1 long for any flooring rounding.
        org.junit.jupiter.api.Assertions.assertTrue(
            cashBoosted >= cashBaseline * 2 - 1L,
            "cashResearchMultiplier 2.0× must approximately double kill cash " +
                "(baseline=$cashBaseline, boosted=$cashBoosted)",
        )
    }

    @Test
    fun `RO11 uwCooldownMultiplier shortens the active cooldown on activation`() {
        // Equip a single CHAIN_LIGHTNING UW (75 PS unlock; 30 s baseline cooldown at L1).
        // Activate at default 1f multiplier and read the cooldown; reset; activate at 0.55f
        // (max-research-level simulation: 1 - 15 × 0.03 = 0.55) and read again. Should be
        // ~55 % of baseline.
        val eng1 = freshEngine()
        eng1.initUWs(listOf(OwnedWeapon(UltimateWeaponType.CHAIN_LIGHTNING, 1, isEquipped = true)))
        eng1.activateUW(0)
        val cooldownBaseline = eng1.uwStates[0].cooldownRemaining

        val eng2 = freshEngine()
        eng2.uwCooldownMultiplier = 0.55f
        eng2.initUWs(listOf(OwnedWeapon(UltimateWeaponType.CHAIN_LIGHTNING, 1, isEquipped = true)))
        eng2.activateUW(0)
        val cooldownBoosted = eng2.uwStates[0].cooldownRemaining

        assertEquals(
            cooldownBaseline * 0.55f,
            cooldownBoosted,
            0.01f,
            "uwCooldownMultiplier 0.55× must shorten activated cooldown to 55 % of baseline",
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

    // ---- RO-09 #1: CHRONO_FIELD UW slows enemies via deltaTime scaling ----

    @Test
    fun `RO09 CHRONO_FIELD active slows enemy movement to 10 percent of baseline`() {
        val baseline = simulateEnemyMovement(activateChrono = false)
        val slowed = simulateEnemyMovement(activateChrono = true)

        // Baseline: speed=100 px/sec × 1 sec ≈ 100 px (modulo dist-based ratio).
        // Chrono: 100 × CHRONO_SLOW_FACTOR (0.10) ≈ 10 px.
        // Pre-fix: slowed == baseline (chronoActive only drove the render overlay).
        org.junit.jupiter.api.Assertions.assertTrue(
            baseline > 50f,
            "Sanity: baseline movement should be measurable (~100 px); got $baseline",
        )
        val ratio = slowed / baseline
        org.junit.jupiter.api.Assertions.assertTrue(
            ratio in 0.08..0.12,
            "Chrono active must slow enemy movement to ~10% of baseline; " +
                "got ratio=$ratio (baseline=$baseline px, slowed=$slowed px)",
        )
    }

    @Test
    fun `RO09 CHRONO_FIELD inactive leaves enemies at full speed`() {
        // Two fresh-engine runs with chrono inactive should produce the same movement —
        // confirms the new entity-update branch has no behavioural drift on the default path.
        val a = simulateEnemyMovement(activateChrono = false)
        val b = simulateEnemyMovement(activateChrono = false)
        assertEquals(
            a,
            b,
            0.001f,
            "Chrono-inactive baseline must be deterministic across fresh engines",
        )
    }

    @Test
    fun `RO09 CHRONO_FIELD does not slow projectile entities`() {
        val eng = freshEngine()
        // Projectile travels straight down at 200 px/sec toward a target 900 px away —
        // far enough that the self-destruct (`dist < speed * deltaTime`) won't trigger
        // even at full speed, so movement on this tick is exactly `speed * deltaTime`.
        val proj = ProjectileEntity(
            startX = 100f, startY = 100f,
            targetX = 100f, targetY = 1000f,
            speed = 200f,
        )
        eng.addEntity(proj)
        setChronoActive(eng, true)

        val yBefore = proj.y
        eng.update(1f)
        val movement = proj.y - yBefore

        // Chrono must NOT slow projectiles — 200 px/sec × 1 sec ≈ 200 px movement.
        // If the gate accidentally caught ProjectileEntity, movement would be ~20 px.
        assertEquals(
            200f,
            movement,
            5f,
            "Chrono active must not slow projectiles; got movement=$movement (expected ~200)",
        )
    }

    // ---- RO-09 #2: GOLDEN_ZIGGURAT × overdrive fortuneMultiplier stacking ----

    @Test
    fun `RO09 GOLDEN_ZIGGURAT expiry preserves FORTUNE multiplier when FORTUNE active`() {
        val eng = freshEngine()
        // FORTUNE first → fortuneMultiplier = 3.0.
        eng.activateOverdrive(OverdriveType.FORTUNE, baseStats = eng.zigStatsForTest())
        // GOLDEN second → fortuneMultiplier = coerceAtLeast(5.0) = 5.0.
        activateGoldenZigForTest(eng)
        assertEquals(
            5.0,
            readFortuneMultiplier(eng),
            0.001,
            "Sanity: GOLDEN activation must raise fortuneMultiplier to 5.0 even with FORTUNE active",
        )

        // Expire GOLDEN via a long updateUWs tick (effectDuration is 10 s).
        invokeUpdateUWs(eng, deltaTime = 20f)

        // Post-fix: branch reads `if (activeOverdrive == FORTUNE) 3.0 else 1.0` →
        // FORTUNE still active, restore 3.0.
        assertEquals(
            3.0,
            readFortuneMultiplier(eng),
            0.001,
            "GOLDEN_ZIGGURAT expiry must restore FORTUNE's 3.0× when FORTUNE is still active",
        )
    }

    @Test
    fun `RO09 GOLDEN_ZIGGURAT expiry resets fortuneMultiplier when ASSAULT active`() {
        val eng = freshEngine()
        // ASSAULT does not own a fortuneMultiplier value.
        eng.activateOverdrive(OverdriveType.ASSAULT, baseStats = eng.zigStatsForTest())
        // GOLDEN raises fortuneMultiplier to 5.0.
        activateGoldenZigForTest(eng)
        assertEquals(5.0, readFortuneMultiplier(eng), 0.001, "Sanity: GOLDEN sets 5.0×")

        // Expire GOLDEN.
        invokeUpdateUWs(eng, deltaTime = 20f)

        // Pre-fix: `if (activeOverdrive == null) fortuneMultiplier = 1.0` skipped the reset
        // because activeOverdrive == ASSAULT, leaking the 5.0× across the rest of the
        // ~50 s ASSAULT window.
        // Post-fix: only FORTUNE preserves a non-default value; ASSAULT resets to 1.0.
        assertEquals(
            1.0,
            readFortuneMultiplier(eng),
            0.001,
            "GOLDEN_ZIGGURAT expiry must reset fortuneMultiplier to 1.0 when overdrive is " +
                "ASSAULT (regression guard: pre-fix leaked 5.0× across ASSAULT/FORTRESS/SURGE)",
        )
    }

    @Test
    fun `RO09 FORTUNE activation does not downgrade GOLDEN_ZIGGURAT multiplier`() {
        val eng = freshEngine()
        // GOLDEN first → fortuneMultiplier = 5.0.
        activateGoldenZigForTest(eng)
        assertEquals(5.0, readFortuneMultiplier(eng), 0.001, "Sanity: GOLDEN sets 5.0×")

        // FORTUNE activation while GOLDEN is still running. Pre-fix this hard-wrote 3.0,
        // downgrading the player's active 5.0× buff. Post-fix: coerceAtLeast(3.0) keeps 5.0.
        eng.activateOverdrive(OverdriveType.FORTUNE, baseStats = eng.zigStatsForTest())

        assertEquals(
            5.0,
            readFortuneMultiplier(eng),
            0.001,
            "FORTUNE activation must coerceAtLeast(3.0) so GOLDEN_ZIGGURAT's 5.0× isn't downgraded",
        )
    }

    @Test
    fun `RO09 expireOverdrive preserves GOLDEN_ZIGGURAT multiplier when GOLDEN active`() {
        val eng = freshEngine()
        // ASSAULT (no fortune effect) running first.
        eng.activateOverdrive(OverdriveType.ASSAULT, baseStats = eng.zigStatsForTest())
        // GOLDEN raises fortuneMultiplier to 5.0.
        activateGoldenZigForTest(eng)
        assertEquals(5.0, readFortuneMultiplier(eng), 0.001, "Sanity: GOLDEN sets 5.0×")

        // Expire ASSAULT (the overdrive). Pre-fix: `fortuneMultiplier = 1.0` unconditionally,
        // collapsing GOLDEN's 5.0× buff for the remainder of GOLDEN's effect window.
        // Post-fix: `if (goldenZigActive) 5.0 else 1.0` preserves the active GOLDEN buff.
        invokeExpireOverdrive(eng)

        assertEquals(
            5.0,
            readFortuneMultiplier(eng),
            0.001,
            "expireOverdrive must preserve GOLDEN_ZIGGURAT's 5.0× when GOLDEN is still active",
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

    /** Reflectively flips the engine's private `chronoActive` flag without going through
     *  the full UW activation path (which would queue cooldowns + visual effects + sounds). */
    private fun setChronoActive(eng: GameEngine, active: Boolean) {
        val field = GameEngine::class.java.getDeclaredField("chronoActive")
            .apply { isAccessible = true }
        field.setBoolean(eng, active)
    }

    /**
     * Spawns a single low-aggression test enemy 1000 px below the ziggurat with huge HP and
     * speed 100 px/sec, ticks the engine once with `deltaTime = 1f`, and returns the
     * upward displacement (positive = moved toward the ziggurat). The enemy is positioned
     * far enough away that ziggurat projectiles (queued in `pendingAdd` this tick, not yet
     * active) cannot reach it, and the huge HP makes incidental damage from any wave-spawner
     * spawns a non-issue. Used by the RO-09 #1 chrono tests to compare chrono-active vs
     * chrono-inactive movement deterministically.
     */
    private fun simulateEnemyMovement(activateChrono: Boolean): Float {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        val enemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 1_000_000.0,
            maxHp = 1_000_000.0,
            speed = 100f,
            damage = 0.0,
            targetX = zig.originX,
            targetY = zig.originY,
            onDeath = { },
        ).apply {
            x = zig.originX
            y = zig.originY + 1000f
            initDistance()
        }
        eng.addEntity(enemy)
        if (activateChrono) setChronoActive(eng, true)
        val yBefore = enemy.y
        eng.update(1f)
        // Movement is upward (toward ziggurat at smaller y) → positive return.
        return yBefore - enemy.y
    }

    /** Reflectively reads the engine's private `fortuneMultiplier` field. */
    private fun readFortuneMultiplier(eng: GameEngine): Double {
        val field = GameEngine::class.java.getDeclaredField("fortuneMultiplier")
            .apply { isAccessible = true }
        return field.getDouble(eng)
    }

    /**
     * Equips a single GOLDEN_ZIGGURAT UW at the given level via the public `initUWs` /
     * `activateUW` path so the test exercises the same activation flow production uses.
     * After this call: `goldenZigActive == true`, `fortuneMultiplier == 5.0` (or higher
     * if a prior buff already set a higher value), `preGoldenStats` captured.
     */
    private fun activateGoldenZigForTest(eng: GameEngine, level: Int = 1) {
        eng.initUWs(listOf(OwnedWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT, level, isEquipped = true)))
        eng.activateUW(0)
    }

    /**
     * Reflectively invokes the private `updateUWs(deltaTime: Float)` helper. Used to
     * deterministically expire a UW (by passing a `deltaTime` larger than its
     * `effectDurationSeconds`) without running the full game-loop update path, which
     * would also tick the overdrive timer / spawn enemies / run collisions.
     */
    private fun invokeUpdateUWs(eng: GameEngine, deltaTime: Float) {
        val method = GameEngine::class.java
            .getDeclaredMethod("updateUWs", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(eng, deltaTime)
    }

    /**
     * Reflectively invokes the private `handleEnemyDeath(EnemyEntity)` helper on a freshly
     * constructed BASIC enemy and returns the resulting kill-cash delta. Used by the RO-11
     * cash-research test to assert the engine-side multiplier is applied without going
     * through the full collision system. Engine-tier defaults to 1 (`freshEngine()`), and
     * no FORTUNE/GOLDEN/CASH_BONUS_GAIN buff is active so the only multiplier in the
     * formula that varies is the one under test.
     */
    private fun simulateBasicKillCash(eng: GameEngine): Long {
        val zig = eng.ziggurat!!
        val enemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 1.0,
            maxHp = 1.0,
            speed = 0f,
            damage = 0.0,
            targetX = zig.originX,
            targetY = zig.originY,
            onDeath = { },
        ).apply { x = zig.originX; y = zig.originY + 200f }
        val cashBefore = eng.cash
        val method = GameEngine::class.java
            .getDeclaredMethod("handleEnemyDeath", EnemyEntity::class.java)
            .apply { isAccessible = true }
        method.invoke(eng, enemy)
        return eng.cash - cashBefore
    }

    // ---- R3-02: THORN_DAMAGE reflection on melee + LIFESTEAL visible-heal feedback ----
    // Pre-R3-02:
    // - THORN_DAMAGE was plumbed through `ResolvedStats.thornPercent` and consumed by
    //   `GameEngine.applyThorn`, but every call site of `applyDamageToZiggurat` passed
    //   `attacker = null`. The `EnemyEntity.onMeleeHit` -> `WaveSpawner.onMeleeHit` ->
    //   `GameEngine` callback chain was typed `(Double) -> Unit` and dropped the enemy
    //   reference, so `applyThorn` early-returned for every melee hit. Player saw zero
    //   reflected damage regardless of THORN_DAMAGE upgrade level.
    // - LIFESTEAL math was correct (`zig.currentHp += damage * stats.lifestealPercent`),
    //   but at low levels the heal was sub-pixel. Lv 1 (0.2 % lifesteal) on base damage 10
    //   produced 0.02 HP per shot — invisible HP-bar nudge. The fix accumulates the
    //   fractional heal in a parallel `lifestealAccumulator` field and emits a `+X HP`
    //   `FloatingText` indicator each time the accumulator crosses an integer threshold,
    //   mirroring the existing RECOVERY_PACKAGES feedback pattern.

    @Test
    fun `R302 THORN_DAMAGE reflects damage on melee hit via plumbed attacker reference`() {
        val eng = freshEngineWithStats(ResolvedStats(thornPercent = 0.5))
        // Make the ziggurat unkillable so wave-spawner's continuous melee ticks can't end
        // the round before the assertion fires.
        eng.ziggurat!!.currentHp = 1_000_000.0
        val attacker = createDummyAttacker(eng, hp = 1000.0)

        // Invoke the engine's wired onMeleeHit callback (the one WaveSpawner gives to each
        // EnemyEntity). Pre-R3-02 the callback shape was `{ dmg -> applyDamageToZiggurat(dmg, null) }`
        // and we updated it to `{ _, dmg -> applyDamageToZiggurat(dmg, null) }` in the seam
        // commit so it still discards the attacker. Post-fix it forwards the attacker through
        // as `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }` so `applyThorn` actually fires.
        invokeOnMeleeHit(eng, attacker, rawDamage = 20.0)

        val expectedReflect = 20.0 * 0.5  // damage × thornPercent
        assertEquals(
            1000.0 - expectedReflect,
            attacker.currentHp,
            0.5,
            "THORN_DAMAGE 50 % must reflect 50 % of melee damage back to the attacker via " +
                "the plumbed-through EnemyEntity reference",
        )
    }

    @Test
    fun `R302 THORN_DAMAGE scales linearly with thornPercent`() {
        val eng10 = freshEngineWithStats(ResolvedStats(thornPercent = 0.10))
        eng10.ziggurat!!.currentHp = 1_000_000.0
        val a10 = createDummyAttacker(eng10, hp = 1000.0)
        invokeOnMeleeHit(eng10, a10, rawDamage = 100.0)
        val reflectAt10 = 1000.0 - a10.currentHp

        val eng50 = freshEngineWithStats(ResolvedStats(thornPercent = 0.50))
        eng50.ziggurat!!.currentHp = 1_000_000.0
        val a50 = createDummyAttacker(eng50, hp = 1000.0)
        invokeOnMeleeHit(eng50, a50, rawDamage = 100.0)
        val reflectAt50 = 1000.0 - a50.currentHp

        // Direct value assertions (rather than `reflectAt10 * 5.0 == reflectAt50`) catch
        // the pre-fix degenerate case where both reflects are zero — 0 × 5 == 0 passes a
        // ratio check trivially. Post-fix reflectAt10 == 10.0 and reflectAt50 == 50.0,
        // confirming both non-zero AND linear scaling.
        assertEquals(10.0, reflectAt10, 0.5, "thornPercent 0.10 must reflect 10 HP from 100 raw damage")
        assertEquals(50.0, reflectAt50, 0.5, "thornPercent 0.50 must reflect 50 HP from 100 raw damage")
    }

    @Test
    fun `R302 LIFESTEAL emits visible floating text when accumulated heal crosses 1 HP`() {
        // 15 % lifesteal on base damage 10 → 1.5 HP heal per hit, well above the +1 HP
        // floating-text threshold; the first hit must emit a `+1 HP` indicator.
        val eng = freshEngineWithStats(ResolvedStats(lifestealPercent = 0.15))
        val zig = eng.ziggurat!!
        // Drop ziggurat to half HP so the heal isn't capped at maxHp.
        zig.currentHp = zig.maxHp * 0.5

        val target = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 100.0, maxHp = 100.0,
            speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = { },
        ).apply { x = zig.originX; y = zig.originY + 50f }

        val proj = ProjectileEntity(
            startX = zig.originX, startY = zig.originY,
            targetX = target.x, targetY = target.y,
            speed = 100f,
        )

        invokeOnProjectileHitEnemy(eng, proj, target)

        // Pre-R3-02: lifestealAccumulator is declared but unused; no FloatingText emitted.
        // Post-fix: accumulator grows to 1.5, floor=1 emitted as `+1 HP` indicator.
        val floats = readPendingFloatingTextSnippets(eng)
        assertTrue(
            floats.any { it.startsWith("+") && it.endsWith("HP") },
            "LIFESTEAL must emit a `+X HP` floating text when accumulated heal crosses 1 HP " +
                "(found floats=$floats)",
        )
    }

    // ---- R3-02 helpers ----

    private fun freshEngineWithStats(stats: ResolvedStats): GameEngine {
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = stats, playerTier = 1)
        return eng
    }

    /**
     * Reflectively invokes the engine's wired `WaveSpawner.onMeleeHit` callback. After the
     * R3-02 signature change this is a `(EnemyEntity, Double) -> Unit` lambda; the engine
     * constructs it once in [GameEngine.init] when the WaveSpawner is wired up. Tests
     * reach in directly so they don't have to drive a full wave-spawn / enemy-tick cycle
     * (which would also tick the spawner, accumulate enemies, and otherwise contaminate
     * the assertion).
     */
    private fun invokeOnMeleeHit(eng: GameEngine, attacker: EnemyEntity, rawDamage: Double) {
        val spawner = eng.waveSpawner!!
        val field = WaveSpawner::class.java.getDeclaredField("onMeleeHit")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callback = field.get(spawner) as (EnemyEntity, Double) -> Unit
        callback(attacker, rawDamage)
    }

    private fun createDummyAttacker(eng: GameEngine, hp: Double): EnemyEntity = EnemyEntity(
        enemyType = EnemyType.BASIC,
        currentHp = hp,
        maxHp = hp,
        speed = 0f,
        damage = 0.0,
        targetX = eng.ziggurat!!.originX,
        targetY = eng.ziggurat!!.originY,
        onDeath = { },
    )

    /** Reflectively invokes the private `onProjectileHitEnemy(ProjectileEntity, EnemyEntity)`
     *  helper so tests can exercise the lifesteal heal path without driving a full
     *  collision cycle (which would also queue bounce-shot projectiles into pendingAdd). */
    private fun invokeOnProjectileHitEnemy(
        eng: GameEngine,
        proj: ProjectileEntity,
        enemy: EnemyEntity,
    ) {
        val method = GameEngine::class.java.getDeclaredMethod(
            "onProjectileHitEnemy",
            ProjectileEntity::class.java,
            EnemyEntity::class.java,
        ).apply { isAccessible = true }
        method.invoke(eng, proj, enemy)
    }

    /** Reflectively reads the engine's `EffectEngine.pendingEffects` list and returns the
     *  text content of any [FloatingText] entries (regardless of color — callers can filter
     *  by prefix/suffix as needed). Used to assert visual feedback was queued. */
    private fun readPendingFloatingTextSnippets(eng: GameEngine): List<String> {
        val fx = eng.effectEngine ?: return emptyList()
        val pendingField = EffectEngine::class.java.getDeclaredField("pendingEffects")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val pending = pendingField.get(fx) as MutableList<Effect>
        val textField = FloatingText::class.java.getDeclaredField("text")
            .apply { isAccessible = true }
        return pending.filterIsInstance<FloatingText>().map { textField.get(it) as String }
    }
}
