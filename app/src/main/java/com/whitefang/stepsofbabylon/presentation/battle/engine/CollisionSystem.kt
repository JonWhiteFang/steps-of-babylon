package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity

/**
 * Presentation-layer adapter for the per-frame collision sweep. Owns only the concerns that
 * are inherently tied to the concrete entity classes: `filterIsInstance` type partitioning
 * and the alive snapshot. The iteration + overlap geometry live in the pure-domain
 * [Simulation] (V1X-09 Phase 3, ADR-0012); this class maps the typed entity lists + callbacks
 * onto [Simulation.detectProjectileEnemyHits] / [Simulation.detectZigguratHits].
 */
object CollisionSystem {

    fun checkCollisions(
        simulation: Simulation,
        entities: List<Entity>,
        zigX: Float, zigY: Float, zigWidth: Float,
        onProjectileHitEnemy: (ProjectileEntity, EnemyEntity) -> Unit,
        onEnemyProjectileHitZiggurat: (EnemyProjectileEntity) -> Unit,
    ) {
        val projectiles = entities.filterIsInstance<ProjectileEntity>().filter { it.isAlive }
        val enemies = entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }
        val enemyProjectiles = entities.filterIsInstance<EnemyProjectileEntity>().filter { it.isAlive }

        simulation.detectProjectileEnemyHits(projectiles, enemies, onProjectileHitEnemy)
        simulation.detectZigguratHits(enemyProjectiles, zigX, zigY, zigWidth, onEnemyProjectileHitZiggurat)
    }
}
