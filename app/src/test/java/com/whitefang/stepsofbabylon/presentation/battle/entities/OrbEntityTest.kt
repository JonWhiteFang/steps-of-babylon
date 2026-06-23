package com.whitefang.stepsofbabylon.presentation.battle.entities

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI

/**
 * Regression coverage for [OrbEntity] (#54).
 *
 * Pre-fix orbs orbited at a fixed radius `stats.range * 0.4f` ≈ 120 px while enemies
 * stopped at meleeRange = 40 px from the same origin, so orbs literally couldn't reach
 * stopped enemies. The post-fix orb oscillates radially between [OrbEntity.ORBIT_RADIUS_MIN]
 * (25 px) and [OrbEntity.ORBIT_RADIUS_MAX] (70 px) so the inner sweep overlaps the kill
 * zone (HIT_RANGE = 25 px reaches melee enemies at the same angle) while the outer sweep
 * places the orb cleanly outside HIT_RANGE.
 *
 * These tests construct an [OrbEntity] directly with specific `initialRadialPhase` values
 * to control the orbit position deterministically, plus a tiny `dt = 0.001f` first
 * `update()` call to advance the phase only marginally before the hit-check fires.
 *
 * Robolectric is required because [OrbEntity] initialises an [android.graphics.Paint]
 * field at construction (the orb's render-time fill paint). Mirrors the
 * `DailyStepDaoTest` / `GameSurfaceViewTest` JUnit 4 + Robolectric pattern from the rest
 * of the codebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OrbEntityTest {
    /**
     * Builds a stationary alive enemy at the requested coordinates. Speed = 0 and
     * onMeleeHit = null so the enemy doesn't move or fire callbacks during the test.
     */
    private fun stationaryEnemyAt(
        x: Float,
        y: Float,
        alive: Boolean = true,
    ): EnemyEntity =
        EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 100.0,
            maxHp = 100.0,
            speed = 0f,
            damage = 0.0,
            targetX = 0f,
            targetY = 0f,
            onDeath = {},
        ).apply {
            this.x = x
            this.y = y
            this.isAlive = alive
        }

    @Test
    fun `R54 orb at inner sweep hits enemy at melee range from origin`() {
        // Arrange: orb at angle 0 with radial phase = -PI/2 → sin = -1 → R = MIN = 25 px.
        // Enemy at (40, 0), i.e. at meleeRange from origin, at angle 0.
        // Distance(orb, enemy) = |25 - 40| = 15 px < HIT_RANGE = 25 → hit expected.
        val hits = mutableListOf<Pair<EnemyEntity, Double>>()
        val enemy = stationaryEnemyAt(40f, 0f)
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f, // freeze angular motion for deterministic geometry
                damage = 5.0,
                getEnemies = { listOf(enemy) },
                onHitEnemy = { e, d -> hits += e to d },
                initialRadialPhase = (-PI / 2).toFloat(),
            )

        orb.update(0.001f) // tiny dt so radialPhase barely advances

        assertEquals("Inner-sweep orb should hit melee-range enemy at same angle", 1, hits.size)
        assertEquals("Damage value should pass through verbatim", 5.0, hits[0].second, 0.001)
        assertEquals("Hit should target the same enemy instance", enemy, hits[0].first)
    }

    @Test
    fun `R54 orb at outer sweep does NOT hit enemy at melee range`() {
        // Arrange: orb at angle 0 with radial phase = +PI/2 → sin = +1 → R = MAX = 70 px.
        // Enemy at (40, 0). Distance = |70 - 40| = 30 px > HIT_RANGE = 25 → no hit expected.
        val hits = mutableListOf<Pair<EnemyEntity, Double>>()
        val enemy = stationaryEnemyAt(40f, 0f)
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f,
                damage = 5.0,
                getEnemies = { listOf(enemy) },
                onHitEnemy = { e, d -> hits += e to d },
                initialRadialPhase = (PI / 2).toFloat(),
            )

        orb.update(0.001f)

        assertEquals(
            "Outer-sweep orb should NOT hit enemy: orb at R=70, enemy at R=40, distance 30 > HIT_RANGE 25",
            0,
            hits.size,
        )
    }

    @Test
    fun `R54 per-enemy HIT_COOLDOWN prevents double-hits within one cooldown window`() {
        // First update at inner sweep registers the hit; second update 0.1s later
        // (well below HIT_COOLDOWN = 0.5s) must NOT register a second hit on the same
        // enemy because hitCooldowns gates the proximity check.
        val hits = mutableListOf<Pair<EnemyEntity, Double>>()
        val enemy = stationaryEnemyAt(40f, 0f)
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f,
                damage = 5.0,
                getEnemies = { listOf(enemy) },
                onHitEnemy = { e, d -> hits += e to d },
                initialRadialPhase = (-PI / 2).toFloat(),
            )

        orb.update(0.001f)
        assertEquals("Sanity: first update should land 1 hit", 1, hits.size)

        orb.update(0.1f)
        assertEquals(
            "Same orb must not double-hit within HIT_COOLDOWN (0.5s) — cooldown still active",
            1,
            hits.size,
        )
    }

    @Test
    fun `R54 orb does not hit dead enemy at melee range`() {
        // Defensive: the proximity check explicitly skips !enemy.isAlive. If a future
        // refactor regresses that check, this test fails and the orb would otherwise be
        // visibly damaging corpses.
        val hits = mutableListOf<Pair<EnemyEntity, Double>>()
        val deadEnemy = stationaryEnemyAt(40f, 0f, alive = false)
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f,
                damage = 5.0,
                getEnemies = { listOf(deadEnemy) },
                onHitEnemy = { e, d -> hits += e to d },
                initialRadialPhase = (-PI / 2).toFloat(),
            )

        orb.update(0.001f)

        assertEquals("Orb must not hit !isAlive enemies", 0, hits.size)
    }

    @Test
    fun `R54 damage value is forwarded verbatim from constructor to onHitEnemy callback`() {
        // The damage value passed by GameEngine.spawnOrbs (stats.damage × 0.5 ×
        // conditions.orbDamageMultiplier) must reach the engine's onOrbHitEnemy callback
        // unchanged. Tests an arbitrary non-default damage to defend against any future
        // hard-coding regression.
        val hits = mutableListOf<Double>()
        val enemy = stationaryEnemyAt(40f, 0f)
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f,
                damage = 42.5,
                getEnemies = { listOf(enemy) },
                onHitEnemy = { _, d -> hits += d },
                initialRadialPhase = (-PI / 2).toFloat(),
            )

        orb.update(0.001f)

        assertEquals(42.5, hits.single(), 0.001)
    }

    @Test
    fun `R54 radial oscillation traverses full MIN to MAX cycle over ORBIT_PERIOD_SEC`() {
        // Sample the orbit radius at 4 phase angles by advancing the phase by 1/4 of a
        // full cycle each time. Catches any future regression where the oscillation gets
        // collapsed to a constant radius (e.g. someone dropping the sin() call) and locks
        // in the MIN/MAX/PERIOD contract in case those constants ever drift.
        val orb =
            OrbEntity(
                zigX = 0f,
                zigY = 0f,
                angle = 0f,
                angularSpeed = 0f,
                damage = 0.0,
                getEnemies = { emptyList() },
                onHitEnemy = { _, _ -> },
                initialRadialPhase = 0f,
            )

        // 1/4 cycle in seconds: ORBIT_PERIOD_SEC / 4 = 2.5 / 4 = 0.625 s.
        // Each call to update(0.625) advances phase by π/2 rad.
        val quarterCycleDt = OrbEntity.ORBIT_PERIOD_SEC / 4f

        // Phase 0: radialPhase = 0 → sin = 0 → R = MID = (25 + 70)/2 = 47.5
        val rAtPhase0 = orb.currentOrbitRadius

        // Phase π/2: sin = 1 → R = MAX = 70
        orb.update(quarterCycleDt)
        val rAtMax = orb.currentOrbitRadius

        // Phase π: sin = 0 → R = MID = 47.5
        orb.update(quarterCycleDt)
        val rAtMidAgain = orb.currentOrbitRadius

        // Phase 3π/2: sin = -1 → R = MIN = 25
        orb.update(quarterCycleDt)
        val rAtMin = orb.currentOrbitRadius

        // Tolerance 0.5 px absorbs floating-point accumulation across 3 update() calls.
        assertEquals("phase 0 → mid", 47.5f, rAtPhase0, 0.5f)
        assertEquals("phase π/2 → MAX", OrbEntity.ORBIT_RADIUS_MAX, rAtMax, 0.5f)
        assertEquals("phase π → mid", 47.5f, rAtMidAgain, 0.5f)
        assertEquals("phase 3π/2 → MIN", OrbEntity.ORBIT_RADIUS_MIN, rAtMin, 0.5f)

        // Spot-check that the values are actually distinct (defends against a bug where
        // all four samples accidentally read the same constant radius).
        assertTrue("MAX and MIN must differ", rAtMax > rAtMin + 1f)
        assertTrue("MAX > mid > MIN ordering must hold", rAtMax > rAtPhase0 && rAtPhase0 > rAtMin)
    }
}
