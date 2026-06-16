package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A28 (audit finding) — the collision sweep now receives pre-filtered, engine-owned scratch lists
 * instead of allocating three `filterIsInstance<…>().filter{}` lists per call. These three tests pin
 * the new typed-list [CollisionSystem.checkCollisions] signature: that it forwards the supplied lists
 * correctly to the simulation sweeps — a projectile-enemy hit fires, an empty enemy list
 * short-circuits, and an enemy-projectile ziggurat hit fires. They pass already-filtered lists
 * straight in, so they do NOT exercise the partition/exclusion step itself (that moved into
 * `GameEngine.update()`); the dead-/non-collidable-entity exclusion is covered end-to-end by
 * `GameEngineTest` ("A28 partition sweeps only live …"). Robolectric is needed because the concrete
 * entities construct android.graphics.Paint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CollisionSystemScratchTest {

    private fun enemyAt(x: Float, y: Float, hp: Double = 1.0): EnemyEntity = EnemyEntity(
        enemyType = EnemyType.BASIC,
        currentHp = hp, maxHp = hp,
        speed = 0f, damage = 0.0,
        targetX = x, targetY = y,
        onDeath = { },
    ).apply { this.x = x; this.y = y; initDistance() }

    @Test
    fun `checkCollisions fires projectile-enemy hit for an overlapping pair from the typed lists`() {
        val proj = ProjectileEntity(startX = 10f, startY = 10f, targetX = 10f, targetY = 10f, speed = 100f, damage = 1.0)
        val enemy = enemyAt(10f, 10f)
        val hits = mutableListOf<Pair<ProjectileEntity, EnemyEntity>>()
        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = listOf(proj),
            enemies = listOf(enemy),
            enemyProjectiles = emptyList(),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { p, e -> hits.add(p to e) },
            onEnemyProjectileHitZiggurat = { },
        )
        assertEquals(1, hits.size)
    }

    @Test
    fun `checkCollisions does not fire when the enemy list is empty`() {
        val proj = ProjectileEntity(startX = 10f, startY = 10f, targetX = 10f, targetY = 10f, speed = 100f, damage = 1.0)
        var fired = false
        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = listOf(proj),
            enemies = emptyList(),
            enemyProjectiles = emptyList(),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { _, _ -> fired = true },
            onEnemyProjectileHitZiggurat = { },
        )
        assertEquals(false, fired)
    }

    @Test
    fun `checkCollisions fires enemy-projectile ziggurat hit from the typed list`() {
        val eproj = EnemyProjectileEntity(startX = 0f, startY = 0f, targetX = 0f, targetY = 0f, damage = 1.0)
        var hit = 0
        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = emptyList(),
            enemies = emptyList(),
            enemyProjectiles = listOf(eproj),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { _, _ -> },
            onEnemyProjectileHitZiggurat = { hit++ },
        )
        assertEquals(1, hit)
    }
}
