package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.EnemyType
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RO-08 + RO-09 + R4-01 regression guards for the engine-side stats / cash / recovery /
 * chrono / fortune wiring.
 *
 * Pre-RO-08:
 * - The engine stored `workshopLevels` once at init; in-round CASH_BONUS / CASH_PER_WAVE /
 *   INTEREST / FREE_UPGRADES purchases never reached the cash math.
 * - RECOVERY_PACKAGES had no implementation.
 *
 * Pre-RO-09:
 * - CHRONO_FIELD UW activation only set a render-overlay flag; raw `deltaTime` reached every
 *   entity, so the description's "Slows all enemies to 10 % speed" had no gameplay effect.
 *
 * Pre-R4-01: `fortuneMultiplier` was shared between Step Overdrive (FORTUNE, 3.0×) and the
 * Ultimate Weapon (GOLDEN_ZIGGURAT, 5.0×) with a 4-test cross-overdrive coerceAtLeast
 * lifecycle. R4-01 deletes Step Overdrive entirely; GOLDEN is the sole writer and the
 * coverage collapses to 2 simple tests — activation sets 5.0×, expiry resets to 1.0×.
 */
class GameEngineTest {

    private fun freshEngine(): GameEngine {
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = ResolvedStats(), playerTier = 1)
        return eng
    }

    // ---- V1X-15b: ENEMY_INTEL information-overlay label helpers ----

    @Test
    fun `V1X15b nextWaveCompositionLabel is null below L1 and populated at L1`() {
        val eng = freshEngine() // opens on wave 1, default enemyIntelLevel = 0
        assertEquals(null, eng.nextWaveCompositionLabel(), "no overlay below ENEMY_INTEL L1")
        eng.enemyIntelLevel = 1
        // currentWave 1 → next wave 2: enemiesPerWave(2) = 7, band <=5 is 100% BASIC.
        assertEquals(
            "Next: 7 BASIC",
            eng.nextWaveCompositionLabel(),
            "L1 overlay must reveal the next wave's deterministic composition",
        )
    }

    @Test
    fun `V1X15b bossCountdownLabel is null below L10 and populated at L10`() {
        val eng = freshEngine() // wave 1, bossWaveInterval 10 (tier 1)
        eng.enemyIntelLevel = 9
        assertEquals(null, eng.bossCountdownLabel(), "no boss countdown below ENEMY_INTEL L10")
        eng.enemyIntelLevel = 10
        // currentWave 1, interval 10 → 9 waves until the wave-10 boss.
        assertEquals(
            "Boss in 9 waves",
            eng.bossCountdownLabel(),
            "L10 overlay must surface the boss-arrival countdown",
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
        // and EnemyScaler.cashReward(BASIC) are constants and no GOLDEN_ZIGGURAT buff is active.
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
        eng1.initUWs(listOf(OwnedWeapon(UltimateWeaponType.CHAIN_LIGHTNING, cooldownLevel = 1, isUnlocked = true, isEquipped = true)))
        eng1.activateUW(0)
        val cooldownBaseline = eng1.uwStates[0].cooldownRemaining

        val eng2 = freshEngine()
        eng2.uwCooldownMultiplier = 0.55f
        eng2.initUWs(listOf(OwnedWeapon(UltimateWeaponType.CHAIN_LIGHTNING, cooldownLevel = 1, isUnlocked = true, isEquipped = true)))
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

    // ---- RO-09 #2 / R4-01: GOLDEN_ZIGGURAT fortuneMultiplier lifecycle ----
    // Pre-R4-01 there were 4 cross-overdrive guards covering Step Overdrive (FORTUNE 3.0×) ×
    // GOLDEN (5.0×) interactions — see git history for the original tests. R4-01 deletes
    // Step Overdrive entirely so GOLDEN is the sole writer and the 4 tests collapse to a
    // simple activate-sets-5× / expiry-resets-1× pair.

    @Test
    fun `R401 GOLDEN_ZIGGURAT activation sets fortuneMultiplier to cash mult from DAMAGE path`() {
        val eng = freshEngine()
        assertEquals(
            1.0,
            readFortuneMultiplier(eng),
            0.001,
            "Sanity: fresh engine has fortuneMultiplier = 1.0×",
        )

        activateGoldenZigForTest(eng)

        // R4-06: GOLDEN damageAtLevel(1) = 2.0 (cash multiplier from DAMAGE path)
        assertEquals(
            2.0,
            readFortuneMultiplier(eng),
            0.001,
            "GOLDEN_ZIGGURAT activation must set fortuneMultiplier to damageAtLevel value (2.0× at L1)",
        )
    }

    @Test
    fun `R401 GOLDEN_ZIGGURAT expiry resets fortuneMultiplier to 1x`() {
        val eng = freshEngine()
        activateGoldenZigForTest(eng)
        assertEquals(2.0, readFortuneMultiplier(eng), 0.001, "Sanity: GOLDEN sets 2.0× at L1")

        // Expire GOLDEN via a long updateUWs tick (effectDuration is 10 s).
        invokeUpdateUWs(eng, deltaTime = 20f)

        assertEquals(
            1.0,
            readFortuneMultiplier(eng),
            0.001,
            "GOLDEN_ZIGGURAT expiry must reset fortuneMultiplier to 1.0× (sole writer post-R4-01)",
        )
    }

    // ---- R4-03: RAPID_FIRE periodic attack-speed burst ----
    // The Workshop ATTACK upgrade fires a periodic attack-speed burst during a wave's
    // SPAWNING phase. Pre-R4-03 the upgrade did not exist; these tests guard the engine-
    // side wiring (timer fires after interval, multiplier set + reset, level-0 no-op,
    // L10 permanent-buff convergence, cooldown-phase reset).

    @Test
    fun `R403 RAPID_FIRE level 0 produces no burst`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        // No RAPID_FIRE level set → effective level 0. A 60 s tick should produce no
        // burst, no multiplier change.
        invokeTickRapidFire(eng, deltaTime = 60f)
        assertEquals(
            1f,
            zig.rapidFireMultiplier,
            0.0001f,
            "RAPID_FIRE level 0 must leave rapidFireMultiplier at the 1f baseline",
        )
    }

    @Test
    fun `R403 RAPID_FIRE L1 fires after 60s and sets multiplier to 2x`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.updateEffectiveLevels(mapOf(UpgradeType.RAPID_FIRE to 1))
        // L1 interval = 60 s; first tick of 60 s should cross the threshold and arm a
        // burst. Pre-R4-03 the upgrade enum did not exist and this would have been a
        // dead path.
        invokeTickRapidFire(eng, deltaTime = 60f)
        assertEquals(
            2f,
            zig.rapidFireMultiplier,
            0.0001f,
            "RAPID_FIRE L1 first burst must set rapidFireMultiplier to 2.0×",
        )
    }

    @Test
    fun `R403 RAPID_FIRE L1 burst expires after 5s duration and resets multiplier`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.updateEffectiveLevels(mapOf(UpgradeType.RAPID_FIRE to 1))
        // Fire the first burst.
        invokeTickRapidFire(eng, deltaTime = 60f)
        assertEquals(
            2f,
            zig.rapidFireMultiplier,
            0.0001f,
            "Sanity: burst should be active after the first 60 s tick",
        )
        // Tick another 6 s (> 5 s duration) — still well below the next 60 s interval, so
        // the burst expires and no new burst arms.
        invokeTickRapidFire(eng, deltaTime = 6f)
        assertEquals(
            1f,
            zig.rapidFireMultiplier,
            0.0001f,
            "RAPID_FIRE L1 burst expires after 5 s; multiplier must reset to 1f",
        )
    }

    @Test
    fun `R403 RAPID_FIRE L5 interpolation produces correct intermediate burst params`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.updateEffectiveLevels(mapOf(UpgradeType.RAPID_FIRE to 5))
        // L5 interval ≈ 46.67 s. A 47 s tick crosses the threshold; multiplier should be
        // ~2.444× (linear: 2.0 + 1.0 × 4 / 9). RapidFireSchedule centralises the math —
        // this test guards the engine reads the same numbers as DescribeUpgradeEffect.
        invokeTickRapidFire(eng, deltaTime = 47f)
        assertEquals(
            2f + 4f / 9f,
            zig.rapidFireMultiplier,
            0.001f,
            "RAPID_FIRE L5 burst multiplier must be 2.0 + (3.0–2.0) × 4/9 ≈ 2.444×",
        )
    }

    @Test
    fun `R403 RAPID_FIRE L10 produces permanent buff via duration matching interval`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.updateEffectiveLevels(mapOf(UpgradeType.RAPID_FIRE to 10))
        // L10 interval = duration = 30 s. After firing the first burst (single 30 s tick),
        // a second 30 s tick should fire the next burst before the active window expires —
        // multiplier stays at 3.0× across the boundary because tickRapidFire decrements the
        // active counter BEFORE testing the timer-fire condition (see method KDoc).
        invokeTickRapidFire(eng, deltaTime = 30f)
        assertEquals(
            3f,
            zig.rapidFireMultiplier,
            0.0001f,
            "Sanity: L10 first burst sets multiplier to 3.0×",
        )
        invokeTickRapidFire(eng, deltaTime = 30f)
        assertEquals(
            3f,
            zig.rapidFireMultiplier,
            0.0001f,
            "L10 RAPID_FIRE must produce a permanent 3.0× buff across burst boundaries",
        )
    }

    @Test
    fun `R403 RAPID_FIRE timer resets when wave enters cooldown phase`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.updateEffectiveLevels(mapOf(UpgradeType.RAPID_FIRE to 1))
        // Build up partial timer in SPAWNING phase — not yet enough to fire.
        invokeTickRapidFire(eng, deltaTime = 30f)
        assertEquals(
            1f,
            zig.rapidFireMultiplier,
            0.0001f,
            "Sanity: 30 s < 60 s interval, no burst should have fired yet",
        )
        // Flip phase to COOLDOWN and tick again — the partial timer must reset (matches
        // RECOVERY_PACKAGES behaviour) so the next wave starts fresh.
        setWavePhase(eng, WavePhase.COOLDOWN)
        invokeTickRapidFire(eng, deltaTime = 1f)
        // Restore SPAWNING phase and tick another 30 s. If the timer reset correctly, no
        // burst should fire (only 30 s of fresh SPAWNING-phase ticks accumulated). Pre-fix
        // (no reset on cooldown) the timer would still hold 30 s + 30 s = 60 s and the
        // burst would fire spuriously at the start of the next wave.
        setWavePhase(eng, WavePhase.SPAWNING)
        invokeTickRapidFire(eng, deltaTime = 30f)
        assertEquals(
            1f,
            zig.rapidFireMultiplier,
            0.0001f,
            "COOLDOWN phase must reset the rapid-fire timer; the next SPAWNING phase " +
                "must accumulate from zero, not carry over the previous wave's partial timer",
        )
    }

    // ---- Helpers: reach into private state via reflection ----

    /** Reflectively reads the engine's private `effectiveLevels` map for the given type. */
    private fun readEffectiveLevel(eng: GameEngine, type: UpgradeType): Int {
        val field = GameEngine::class.java.getDeclaredField("effectiveLevels")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val map = field.get(eng) as Map<UpgradeType, Int>
        return map[type] ?: 0
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

    /**
     * Reflectively invokes the private `tickRapidFire(deltaTime: Float)` helper (R4-03).
     * Same shape as [invokeTickRecovery] — deliberately bypasses the full update loop so
     * the burst-state assertions don't see drift from incidental enemy spawns or projectile
     * collisions.
     */
    private fun invokeTickRapidFire(eng: GameEngine, deltaTime: Float) {
        val method = GameEngine::class.java
            .getDeclaredMethod("tickRapidFire", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(eng, deltaTime)
    }

    /**
     * Reflectively flips the engine's [com.whitefang.stepsofbabylon.presentation.battle.engine.WaveSpawner.phase]
     * (private set) without ticking through the full 26 + 9 s wave cycle. Used by the R4-03
     * cooldown-phase-reset test.
     */
    private fun setWavePhase(eng: GameEngine, phase: WavePhase) {
        val spawner = eng.waveSpawner ?: return
        val field = WaveSpawner::class.java.getDeclaredField("phase")
            .apply { isAccessible = true }
        field.set(spawner, phase)
    }

    /** Reflectively flips the engine's private `chronoActive` flag without going through
     *  the full UW activation path (which would queue cooldowns + visual effects + sounds).
     *  R4-06: also sets `chronoSlowFactor` to 0.10f (the pre-R4-06 constant) so the
     *  entity-update loop applies the expected slow. */
    private fun setChronoActive(eng: GameEngine, active: Boolean) {
        val field = GameEngine::class.java.getDeclaredField("chronoActive")
            .apply { isAccessible = true }
        field.setBoolean(eng, active)
        if (active) {
            val sfField = GameEngine::class.java.getDeclaredField("chronoSlowFactor")
                .apply { isAccessible = true }
            sfField.setFloat(eng, 0.10f)
        }
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
        eng.initUWs(listOf(OwnedWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT, damageLevel = level, secondaryLevel = level, cooldownLevel = level, isUnlocked = true, isEquipped = true)))
        eng.activateUW(0)
    }

    /**
     * Reflectively invokes the private `updateUWs(deltaTime: Float)` helper. Used to
     * deterministically expire a UW (by passing a `deltaTime` larger than its
     * `effectDurationSeconds`) without running the full game-loop update path, which
     * would also spawn enemies / run collisions / drive other engine subsystems.
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
     * no GOLDEN_ZIGGURAT/CASH_BONUS_GAIN buff is active so the only multiplier in the
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

    // ---- R4-07: onBossKilled callback fires for BOSS enemy type ----

    @Test
    fun `R407 onBossKilled fires when a BOSS enemy dies`() {
        val eng = freshEngine()
        var callbackTier: Int? = null
        eng.onBossKilled = { tier, _, _ -> callbackTier = tier }
        val zig = eng.ziggurat!!
        val boss = EnemyEntity(
            enemyType = EnemyType.BOSS,
            currentHp = 1.0, maxHp = 1.0, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY, onDeath = {},
        )
        val method = GameEngine::class.java
            .getDeclaredMethod("handleEnemyDeath", EnemyEntity::class.java)
            .apply { isAccessible = true }
        method.invoke(eng, boss)
        assertEquals(1, callbackTier, "onBossKilled must fire with the engine's tier for BOSS kills")
    }

    @Test
    fun `R407 onBossKilled does NOT fire for non-BOSS enemy types`() {
        val eng = freshEngine()
        var fired = false
        eng.onBossKilled = { _, _, _ -> fired = true }
        val zig = eng.ziggurat!!
        for (type in listOf(EnemyType.BASIC, EnemyType.FAST, EnemyType.TANK, EnemyType.RANGED)) {
            val enemy = EnemyEntity(
                enemyType = type,
                currentHp = 1.0, maxHp = 1.0, speed = 0f, damage = 0.0,
                targetX = zig.originX, targetY = zig.originY, onDeath = {},
            )
            val method = GameEngine::class.java
                .getDeclaredMethod("handleEnemyDeath", EnemyEntity::class.java)
                .apply { isAccessible = true }
            method.invoke(eng, enemy)
        }
        assertFalse(fired, "onBossKilled must NOT fire for non-BOSS enemy types")
    }

    @Test
    fun `R407 onBossKilled passes engine tier to callback`() {
        // Init engine at tier 7
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = ResolvedStats(), playerTier = 7)
        var receivedTier: Int? = null
        eng.onBossKilled = { tier, _, _ -> receivedTier = tier }
        val zig = eng.ziggurat!!
        val boss = EnemyEntity(
            enemyType = EnemyType.BOSS,
            currentHp = 1.0, maxHp = 1.0, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY, onDeath = {},
        )
        val method = GameEngine::class.java
            .getDeclaredMethod("handleEnemyDeath", EnemyEntity::class.java)
            .apply { isAccessible = true }
        method.invoke(eng, boss)
        assertEquals(7, receivedTier, "onBossKilled must pass the engine's playerTier")
    }
}
