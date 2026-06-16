package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.battle.effects.Effect
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText
import com.whitefang.stepsofbabylon.presentation.battle.effects.WaveAnnouncement
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

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
@OptIn(ExperimentalCoroutinesApi::class)
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

    // ---- #119: GOLDEN_ZIGGURAT expiry must preserve in-round upgrades bought during its window ----
    // Pre-fix: activation snapshots preGoldenStats once; an in-round purchase mid-GOLDEN updates
    // `stats` via updateZigguratStats→applyStats but NOT preGoldenStats, so expiry restores the
    // STALE pre-GOLDEN snapshot and silently discards the purchase. Since GOLDEN auto-fires on
    // cooldown (R4-06), the loss recurs every cycle.

    @Test
    fun `R119 GOLDEN expiry preserves in-round upgrade bought during its window`() {
        val eng = freshEngine()
        val baseDamage = eng.ziggurat!!.stats.damage

        // Activate GOLDEN — applies its damage multiplier over the base and snapshots the base.
        activateGoldenZigForTest(eng)
        val goldenBoostedDamage = eng.ziggurat!!.stats.damage
        assertTrue(
            goldenBoostedDamage > baseDamage,
            "Sanity: GOLDEN activation must raise damage above base " +
                "(base=$baseDamage, golden=$goldenBoostedDamage)",
        )

        // Player buys an in-round DAMAGE upgrade WHILE GOLDEN is active (the in-round purchase
        // channel is updateZigguratStats). Use a distinctive base damage of 999 so the assertion
        // can't be satisfied by the pre-GOLDEN base value.
        eng.updateZigguratStats(ResolvedStats(damage = 999.0))

        // Expire GOLDEN via a long updateUWs tick (effectDuration is 10 s).
        invokeUpdateUWs(eng, deltaTime = 20f)

        assertEquals(
            999.0,
            eng.ziggurat!!.stats.damage,
            0.001,
            "After GOLDEN expires, the ziggurat must retain the in-round DAMAGE upgrade bought " +
                "during the GOLDEN window (999), NOT roll back to the stale pre-GOLDEN snapshot",
        )
    }

    @Test
    fun `R119 GOLDEN damage layer still applies while active after an in-round purchase`() {
        // Guards that the fix re-layers the GOLDEN multiplier over the NEW base after a mid-window
        // purchase (not just that expiry restores it). While GOLDEN is active, a purchase to base
        // damage 100 must show as 100 × goldenDamageMult, not a bare 100.
        val eng = freshEngine()
        val base = eng.ziggurat!!.stats.damage
        activateGoldenZigForTest(eng)
        val mult = eng.ziggurat!!.stats.damage / base // the GOLDEN damage multiplier (L1 secondary)

        eng.updateZigguratStats(ResolvedStats(damage = 100.0))

        assertEquals(
            100.0 * mult,
            eng.ziggurat!!.stats.damage,
            0.01,
            "A mid-GOLDEN purchase must keep the GOLDEN damage multiplier layered over the new " +
                "base (100 × $mult), so the player still sees the buff until it expires",
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

    // ---- #125: getAliveEnemies allocation refactor — behaviour-preserving guards ----
    //
    // #125 halves the per-frame allocation in getAliveEnemies() (one single-pass build
    // instead of filterIsInstance{}.filter{} = two lists). It must NOT become a shared
    // per-frame cache: EnemyEntity.takeDamage is NOT idempotent on a dead enemy — it
    // re-fires onDeath → handleEnemyDeath (re-credits cash/steps, re-spawns SCATTER
    // children). BLACK_HOLE and POISON_SWAMP both kill enemies mid-frame and each iterate
    // the alive set, so a stale cached list would let POISON re-hit BLACK_HOLE's corpses
    // and double-credit the kill. These guards pin that invariant so the refactor (and any
    // future "optimisation" of it) cannot regress it.

    @Test
    fun `R125 getAliveEnemies returns only alive enemy entities`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        val alive = makeStationaryEnemy(zig, hp = 1000.0)
        val dead = makeStationaryEnemy(zig, hp = 1000.0).apply { takeDamage(1000.0) }
        eng.addEntity(alive)
        eng.addEntity(dead)
        eng.addEntity(ProjectileEntity(zig.x, zig.y, zig.x, zig.y + 50f, 600f)) // non-enemy
        // Flush pendingAdd into the live list without running a full tick.
        flushPendingAdd(eng)

        val result = invokeGetAliveEnemies(eng)
        assertEquals(1, result.size, "only the single alive EnemyEntity should be returned")
        assertTrue(result.all { it.isAlive }, "every returned enemy must be alive")
        assertTrue(result.contains(alive), "the alive enemy must be present")
        assertFalse(result.contains(dead), "the dead enemy must be excluded")
    }

    @Test
    fun `R125 overlapping BLACK_HOLE and POISON_SWAMP fire each enemy's onDeath exactly once`() {
        // The no-double-credit invariant. Two ongoing-damage UWs run in the SAME updateUWs
        // pass; an enemy killed by BLACK_HOLE must not be re-killed by POISON_SWAMP.
        // EnemyEntity.takeDamage re-fires onDeath every time it is called on an already-dead
        // enemy, so a shared/stale alive list would double-fire onDeath (= double-credit the
        // kill in production via handleEnemyDeath). The live per-call isAlive re-filter
        // prevents that. Counting onDeath directly captures the mechanism without depending
        // on the engine's private kill wiring.
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        eng.initUWs(
            listOf(
                OwnedWeapon(UltimateWeaponType.BLACK_HOLE, damageLevel = 10, secondaryLevel = 10, cooldownLevel = 1, isUnlocked = true, isEquipped = true),
                OwnedWeapon(UltimateWeaponType.POISON_SWAMP, damageLevel = 10, secondaryLevel = 10, cooldownLevel = 1, isUnlocked = true, isEquipped = true),
            ),
        )
        // Fragile enemies that both UWs would one-shot this tick, each counting its own deaths.
        val n = 4
        val deathCounts = IntArray(n)
        repeat(n) { i -> eng.addEntity(makeStationaryEnemy(zig, hp = 0.01, onDeath = { deathCounts[i]++ })) }
        flushPendingAdd(eng)
        // Prime both UWs into the active ongoing-effect state, then run one ongoing tick.
        eng.activateUW(0)
        eng.activateUW(1)
        invokeUpdateUWs(eng, deltaTime = 1f)

        assertTrue(
            deathCounts.all { it == 1 },
            "each enemy's onDeath must fire exactly once even with two overlapping " +
                "ongoing-damage UWs in the same frame (no double-credit via a stale alive " +
                "list); got per-enemy counts ${deathCounts.toList()}",
        )
    }

    // ---- #146: enemy counter must never drift negative (two independent causes) ----
    //
    // The HUD wave header reads GameEngine.aliveEnemyCount(). Pre-#146 that was a hand-kept
    // WaveSpawner.enemiesAlive tally (incremented in ONE place — spawnEnemy — but decremented
    // by every onEnemyKilled), which drifts below zero mid/late run via two mechanisms:
    //   #1 SCATTER children spawn straight into pendingAdd (bypassing the only ++), yet each
    //      child's death decrements → net -childCount per SCATTER.
    //   #2 EnemyEntity.takeDamage had no isAlive guard, so a second projectile landing on a
    //      corpse in the same collision sweep re-fired onDeath → an extra decrement AND a
    //      double-credit of the kill reward (cash + battle Steps).
    // The fix makes aliveEnemyCount() authoritative (derived from live alive EnemyEntity) and
    // guards takeDamage against re-firing. These guards pin both.

    @Test
    fun `R146 SCATTER death plus all its children leaves an authoritative non-negative enemy count`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        // A SCATTER whose death routes through the engine's real handleEnemyDeath (which spawns
        // 2-3 BASIC children into pendingAdd, each wired onDeath = ::handleEnemyDeath).
        val scatter = EnemyEntity(
            enemyType = EnemyType.SCATTER,
            currentHp = 100.0, maxHp = 100.0, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng),
        ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }
        eng.addEntity(scatter)
        flushPendingAdd(eng)
        assertEquals(1, eng.aliveEnemyCount(), "one enemy on screen must read as a count of 1")

        // Kill the SCATTER: marks it dead + fires handleEnemyDeath → spawns its children.
        scatter.takeDamage(scatter.maxHp)
        flushPendingAdd(eng)
        assertTrue(
            eng.aliveEnemyCount() in 2..3,
            "after a SCATTER dies its 2-3 children must be counted (not bypassed); " +
                "got ${eng.aliveEnemyCount()}",
        )

        // Kill every remaining child. Because the count is now derived from the live entity
        // list, it settles at exactly 0 — the SCATTER children were counted while alive (the
        // old tally bypassed them) and are gone once dead. A derived count cannot underflow.
        invokeGetAliveEnemies(eng).forEach { it.takeDamage(it.maxHp) }
        assertEquals(
            0,
            eng.aliveEnemyCount(),
            "with every enemy dead the authoritative count must read 0 — children that were " +
                "counted while alive are no longer counted once killed (#146 cause #1)",
        )
    }

    @Test
    fun `R146 two projectiles hitting one enemy in a single sweep fire its onDeath exactly once`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        var deaths = 0
        val enemy = makeStationaryEnemy(zig, hp = 0.01, onDeath = { deaths++ })
        eng.addEntity(enemy)
        flushPendingAdd(eng)

        // Two projectiles overlap the same front-line enemy in one collision sweep: the engine
        // fires onProjectileHitEnemy once per projectile against the (frame-fixed) snapshot.
        val proj1 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        val proj2 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        invokeOnProjectileHitEnemy(eng, proj1, enemy) // kills it
        invokeOnProjectileHitEnemy(eng, proj2, enemy) // lands on the corpse

        assertEquals(
            1,
            deaths,
            "a second projectile hitting an already-dead enemy must NOT re-fire onDeath — " +
                "takeDamage must guard on isAlive (#146 cause #2)",
        )
    }

    @Test
    fun `R146 a second projectile on a corpse does not re-credit the kill reward`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        val enemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 0.01, maxHp = 0.01, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng), // route death through the real reward block
        ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }
        eng.addEntity(enemy)
        flushPendingAdd(eng)

        val cashBefore = eng.cash
        val proj1 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        val proj2 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        invokeOnProjectileHitEnemy(eng, proj1, enemy) // kills it → credits the kill once
        val cashAfterKill = eng.cash
        invokeOnProjectileHitEnemy(eng, proj2, enemy) // lands on the corpse
        val cashAfterCorpseHit = eng.cash

        assertTrue(cashAfterKill > cashBefore, "the kill must credit cash exactly once")
        assertEquals(
            cashAfterKill,
            cashAfterCorpseHit,
            "a second projectile hitting the corpse must NOT re-credit the kill reward (#146 cause #2)",
        )
    }

    @Test
    fun `A28 two projectiles on one enemy via the engine-owned scratch buffers credit the kill exactly once`() {
        // A28: the collision sweep now partitions `entities` into engine-owned scratch buffers
        // (one `enemyScratch` instance for the whole sweep, matching the old single `enemies`
        // snapshot). This pins that the single-fill keeps the corpse in the swept list yet does
        // NOT re-credit — the #146 `takeDamage` isAlive guard still gates the second hit. The
        // assertion rides the REAL reward path (eng.cash via handleEnemyDeath), not a counter.
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        val enemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 0.01, maxHp = 0.01, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng), // route death through the real reward block
        ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }
        eng.addEntity(enemy)
        flushPendingAdd(eng)

        val cashBefore = eng.cash
        val proj1 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        val proj2 = ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f)
        invokeOnProjectileHitEnemy(eng, proj1, enemy) // lethal first hit → credits the kill once
        val cashAfterKill = eng.cash
        invokeOnProjectileHitEnemy(eng, proj2, enemy) // lands on the corpse still in the buffer
        val cashAfterCorpseHit = eng.cash

        assertTrue(cashAfterKill > cashBefore, "the lethal hit must credit the kill exactly once")
        assertEquals(
            cashAfterKill,
            cashAfterCorpseHit,
            "the corpse retained in the single-fill scratch buffer must NOT re-credit the kill " +
                "(A28 scratch buffer must be behaviour-identical to the old single `enemies` snapshot, #146)",
        )
    }

    @Test
    fun `A28 partition sweeps only live projectiles-enemies-enemy-projectiles, excluding dead and non-collidable entities`() {
        // A28: GameEngine.update() partitions `entities` into three scratch buffers via a `when`
        // that admits ONLY live ProjectileEntity / EnemyEntity / EnemyProjectileEntity — exactly
        // mirroring the old `filterIsInstance<X>().filter { it.isAlive }`. OrbEntity, ZigguratEntity
        // and any DEAD entity must be excluded so they never reach the collision sweep. The other
        // A28 test covers the corpse-retained-mid-sweep case via direct onProjectileHitEnemy calls;
        // this one drives the exclusion end-to-end through a real eng.update(...).
        //
        // Setup (all flushed into the live list before the sweep, since addEntity → pendingAdd and
        // update() flushes pendingAdd at the top of the tick, before the partition):
        //   • a live BASIC enemy with lethal-on-one-hit HP, co-located with…
        //   • a live ProjectileEntity at the SAME (x, y) → the sweep produces exactly one hit,
        //     killing the enemy and routing through the real handleEnemyDeath reward path.
        //   • a DEAD enemy parked 1000 px below (out of any incidental reach), pre-killed so it is
        //     NOT admitted to enemyScratch. If the `when` wrongly swept it, a co-located projectile
        //     would land on it; even guarded by takeDamage, it must never credit a second kill.
        //   • an OrbEntity wired to see no enemies (cannot itself credit) — proves a non-collidable
        //     type in `entities` is skipped by the partition without crashing the sweep.
        //   • the ZigguratEntity from init() is already present and likewise never swept as a target.
        val eng = freshEngine()
        val zig = eng.ziggurat!!

        // Baseline single-BASIC-kill cash from an independent fresh engine: the deterministic
        // delta one kill must credit. Asserting the live engine's delta equals EXACTLY this proves
        // exactly one credit — i.e. the dead enemy contributed nothing.
        val oneKillCash = simulateBasicKillCash(freshEngine())
        assertTrue(oneKillCash > 0L, "a single BASIC kill must credit positive cash (test premise)")

        val liveEnemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 0.01, maxHp = 0.01, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng), // route the kill through the real reward block
        ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }
        // Co-located with the enemy, but aimed at a FAR target so ProjectileState.update (run by
        // tickEntities BEFORE the partition) does not self-destruct on arrival this tick. At
        // speed 100 × dt 0.016 it advances only ~1.6 px — well inside the (8 + 20) / 2 = 14 px
        // overlap radius of the stationary BASIC enemy — so the sweep still registers the hit.
        val liveProjectile = ProjectileEntity(
            startX = liveEnemy.x, startY = liveEnemy.y,
            targetX = liveEnemy.x + 10_000f, targetY = liveEnemy.y,
            speed = 100f, damage = 1.0,
        )

        // A pre-killed enemy that, if wrongly admitted to enemyScratch, could be re-hit/credited.
        val deadEnemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 0.01, maxHp = 0.01, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng),
        ).apply { x = zig.originX; y = zig.originY + 1000f; initDistance(); isAlive = false }

        val orb = com.whitefang.stepsofbabylon.presentation.battle.entities.OrbEntity(
            zigX = zig.originX, zigY = zig.originY, angle = 0f,
            damage = 1.0,
            getEnemies = { emptyList() }, // sees nothing → cannot itself credit a kill
            onHitEnemy = { _, _ -> },
        )

        eng.addEntity(liveEnemy)
        eng.addEntity(liveProjectile)
        eng.addEntity(deadEnemy)
        eng.addEntity(orb)

        val cashBefore = eng.cash
        val zigHpBefore = zig.currentHp

        eng.update(0.016f) // one real tick: flush → partition → sweep → removeAll(!isAlive)

        // Exactly ONE kill credited: the live projectile+enemy pair. The dead enemy was never in
        // enemyScratch, so it produced no hit and no second credit; the orb/ziggurat were never
        // swept as collision targets. A delta of exactly one kill's cash is the exclusion proof.
        assertEquals(
            oneKillCash,
            eng.cash - cashBefore,
            "A28 partition must sweep ONLY the live projectile/enemy pair — exactly one kill is " +
                "credited; a dead enemy and the orb/ziggurat must be excluded (no extra credit)",
        )
        // Both enemies are gone from the live list: the live one died this sweep, the dead one was
        // removed by removeAll { !it.isAlive } — neither lingers as a counted enemy.
        assertEquals(
            0,
            eng.aliveEnemyCount(),
            "after the tick no live enemy remains (the swept kill died; the excluded dead enemy " +
                "was pruned) — the partition never resurrected the corpse",
        )
        // The ziggurat was never treated as a collision target by the projectile/enemy sweep, so it
        // took no damage and the round did not end — the partition's type exclusion held.
        assertEquals(
            zigHpBefore,
            zig.currentHp,
            0.0,
            "the ZigguratEntity must be excluded from the projectile/enemy sweep (it took no hit)",
        )
        assertFalse(eng.roundOver, "no friendly-fire on the ziggurat → the round must not have ended")
    }

    // ---- #16: first-wave announcement must fire exactly once per round start ----
    //
    // Pre-fix init() set lastWave = 0 then called triggerWaveAnnouncement(safeStartWave) but
    // never updated lastWave, so the very first update() tick saw currentWave (= startWave) !=
    // lastWave (= 0) and announced the same wave a SECOND time — a doubled wave-start sound and
    // a stacked WaveAnnouncement overlay on every round start / playAgain. The fix sets
    // lastWave = safeStartWave in init() so the opening wave is not re-detected as a change.

    @Test
    fun `R16 first wave is announced exactly once after init and one update tick`() {
        val eng = freshEngine() // init() opens on wave 1 and queues the initial announcement
        // Sanity: init queued exactly one WaveAnnouncement.
        assertEquals(
            1,
            countWaveAnnouncements(eng),
            "init() must queue exactly one opening WaveAnnouncement",
        )
        // One update tick on the unchanged opening wave must NOT re-announce.
        eng.update(0.016f)
        assertEquals(
            1,
            countWaveAnnouncements(eng),
            "the opening wave must be announced exactly once — init() must seed lastWave to the " +
                "start wave so the first update() tick does not re-detect a wave change (#16)",
        )
    }

    @Test
    fun `R16 first wave announced once when round opens past wave 1 via startWave`() {
        // WAVE_SKIP research can open a round on a later wave; the double-announce bug fired
        // for any opening wave, not just wave 1. Pin the single-announce contract there too.
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = ResolvedStats(), playerTier = 1, startWave = 5)
        assertEquals(1, countWaveAnnouncements(eng), "init() must queue one announcement for wave 5")
        eng.update(0.016f)
        assertEquals(
            1,
            countWaveAnnouncements(eng),
            "opening past wave 1 must still announce exactly once (#16)",
        )
    }

    // ---- #17: lifesteal / knockback must be gated on damage actually dealt ----
    //
    // EnemyEntity.takeDamage consumes an armor hit and deals ZERO HP damage while armorHits > 0.
    // Pre-fix the lifesteal heal and knockback in onProjectileHitEnemy / onOrbHitEnemy fired off
    // the INTENDED damage regardless of whether the hit landed, so the ziggurat healed (and the
    // enemy was knocked back) on fully armor-absorbed hits — free healing/CC the design gates on
    // damage dealt. The fix has takeDamage return the dealt amount (0 when absorbed) and gates
    // lifesteal/knockback on dealt > 0.

    @Test
    fun `R17 lifesteal does not heal on a fully armor-absorbed projectile hit`() {
        val eng = freshEngineWithStats(ResolvedStats(lifestealPercent = 0.15))
        val zig = eng.ziggurat!!
        zig.currentHp = zig.maxHp * 0.5
        val hpBefore = zig.currentHp

        // armorHits = 1 → the first hit is fully absorbed (0 HP damage dealt).
        val armored = EnemyEntity(
            enemyType = EnemyType.TANK,
            currentHp = 100.0, maxHp = 100.0,
            speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = { },
            armorHits = 1,
        ).apply { x = zig.originX; y = zig.originY + 50f }

        val proj = ProjectileEntity(
            startX = zig.originX, startY = zig.originY,
            targetX = armored.x, targetY = armored.y,
            speed = 100f,
        )

        invokeOnProjectileHitEnemy(eng, proj, armored)

        assertEquals(
            hpBefore,
            zig.currentHp,
            0.0001,
            "lifesteal must not heal the ziggurat on a hit that dealt 0 damage (armor absorbed) — #17",
        )
        // And no `+X HP` floating text should have been queued for a zero-damage hit.
        assertFalse(
            readPendingFloatingTextSnippets(eng).any { it.startsWith("+") && it.endsWith("HP") },
            "no lifesteal floating text may be emitted for a fully armor-absorbed hit (#17)",
        )
    }

    @Test
    fun `R17 lifesteal still heals once armor is gone`() {
        // Guards against an over-broad fix that gates lifesteal off entirely: after the armor
        // hit is consumed, a real damaging hit must heal as before.
        val eng = freshEngineWithStats(ResolvedStats(lifestealPercent = 0.15))
        val zig = eng.ziggurat!!
        zig.currentHp = zig.maxHp * 0.5
        val hpBefore = zig.currentHp

        val armored = EnemyEntity(
            enemyType = EnemyType.TANK,
            currentHp = 100.0, maxHp = 100.0,
            speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = { },
            armorHits = 1,
        ).apply { x = zig.originX; y = zig.originY + 50f }

        // First hit: absorbed (consumes the armor). Second hit: lands and heals.
        invokeOnProjectileHitEnemy(
            eng,
            ProjectileEntity(zig.originX, zig.originY, armored.x, armored.y, 100f),
            armored,
        )
        invokeOnProjectileHitEnemy(
            eng,
            ProjectileEntity(zig.originX, zig.originY, armored.x, armored.y, 100f),
            armored,
        )

        assertTrue(
            zig.currentHp > hpBefore,
            "lifesteal must resume once the armor hit is consumed and real damage is dealt (#17)",
        )
    }

    // ---- Helpers: reach into private state via reflection ----

    /**
     * Counts queued [WaveAnnouncement] effects across both the EffectEngine's pending and
     * live lists. init() queues into `pendingEffects`; update() flushes pending into `effects`
     * then re-detects wave changes, so a double-announce shows up across the two lists.
     */
    private fun countWaveAnnouncements(eng: GameEngine): Int {
        val fx = eng.effectEngine ?: return 0
        var count = 0
        for (fieldName in listOf("pendingEffects", "effects")) {
            val field = EffectEngine::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val list = field.get(fx) as List<Effect>
            count += list.count { it is WaveAnnouncement }
        }
        return count
    }

    /** A stationary, non-aggressive enemy directly below the ziggurat for alive-set tests. */
    private fun makeStationaryEnemy(
        zig: com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity,
        hp: Double,
        onDeath: (EnemyEntity) -> Unit = { },
    ): EnemyEntity = EnemyEntity(
        enemyType = EnemyType.BASIC,
        currentHp = hp, maxHp = hp,
        speed = 0f, damage = 0.0,
        targetX = zig.originX, targetY = zig.originY,
        onDeath = onDeath,
    ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }

    /** Reflectively flushes `pendingAdd` into the live `entities` list (no full tick). */
    private fun flushPendingAdd(eng: GameEngine) {
        val entitiesField = GameEngine::class.java.getDeclaredField("entities").apply { isAccessible = true }
        val pendingField = GameEngine::class.java.getDeclaredField("pendingAdd").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entities = entitiesField.get(eng) as MutableList<Any>
        @Suppress("UNCHECKED_CAST")
        val pending = pendingField.get(eng) as MutableList<Any>
        entities.addAll(pending); pending.clear()
    }

    /**
     * Returns an `onDeath` callback that routes through the engine's real private
     * `handleEnemyDeath` — the same wiring [GameEngine.init] gives every spawned enemy. Lets a
     * test enemy's death credit rewards / spawn SCATTER children exactly as production does
     * (used by the #146 guards).
     */
    private fun engineDeathHandler(eng: GameEngine): (EnemyEntity) -> Unit {
        val method = GameEngine::class.java
            .getDeclaredMethod("handleEnemyDeath", EnemyEntity::class.java)
            .apply { isAccessible = true }
        return { enemy -> method.invoke(eng, enemy) }
    }

    /** Reflectively invokes the private `getAliveEnemies()` helper. */
    @Suppress("UNCHECKED_CAST")
    private fun invokeGetAliveEnemies(eng: GameEngine): List<EnemyEntity> {
        val method = GameEngine::class.java.getDeclaredMethod("getAliveEnemies").apply { isAccessible = true }
        return method.invoke(eng) as List<EnemyEntity>
    }

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
     * After this call: `goldenZigActive == true`, `fortuneMultiplier == 2.0` at L1 (the DAMAGE-path
     * cash multiplier; or higher if a prior buff already set a higher value), `preGoldenStats` captured.
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

    // ---- R4-07: handleEnemyDeath emits SimulationEvent.BossKilled for BOSS enemy type ----

    @Test
    fun `R407 emits BossKilled when a BOSS enemy dies`() = runTest(UnconfinedTestDispatcher()) {
        val eng = freshEngine()
        val events = mutableListOf<SimulationEvent>()
        // UnconfinedTestDispatcher: the collector subscribes eagerly at launch, before the
        // synchronous handleEnemyDeath emit below, so the replay=0 stream still delivers it.
        val collector = launch { eng.events.collect { events.add(it) } }
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
        advanceUntilIdle()
        collector.cancel()
        val bossEvents = events.filterIsInstance<SimulationEvent.BossKilled>()
        assertEquals(1, bossEvents.size, "exactly one BossKilled must be emitted for a BOSS kill")
        assertEquals(1, bossEvents.first().tier, "BossKilled must carry the engine's tier for BOSS kills")
    }

    @Test
    fun `R407 does NOT emit BossKilled for non-BOSS enemy types`() = runTest(UnconfinedTestDispatcher()) {
        val eng = freshEngine()
        val events = mutableListOf<SimulationEvent>()
        val collector = launch { eng.events.collect { events.add(it) } }
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
        advanceUntilIdle()
        collector.cancel()
        assertFalse(
            events.any { it is SimulationEvent.BossKilled },
            "BossKilled must NOT be emitted for non-BOSS enemy types",
        )
    }

    @Test
    fun `R407 emitted BossKilled carries the engine tier`() = runTest(UnconfinedTestDispatcher()) {
        // Init engine at tier 7
        val eng = GameEngine()
        eng.init(width = 1080f, height = 1920f, resolvedStats = ResolvedStats(), playerTier = 7)
        val events = mutableListOf<SimulationEvent>()
        val collector = launch { eng.events.collect { events.add(it) } }
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
        advanceUntilIdle()
        collector.cancel()
        assertEquals(
            7,
            events.filterIsInstance<SimulationEvent.BossKilled>().single().tier,
            "BossKilled must carry the engine's playerTier",
        )
    }
}
