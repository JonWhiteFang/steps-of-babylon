package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity

/**
 * Presentation-layer adapter for the per-frame collision sweep. Maps the typed, already-filtered
 * entity lists + callbacks onto the pure-domain [Simulation.detectProjectileEnemyHits] /
 * [Simulation.detectZigguratHits], where the iteration + overlap geometry live (V1X-09 Phase 3,
 * ADR-0012).
 *
 * A28 (audit finding): the `filterIsInstance<…>().filter { it.isAlive }` type-partitioning used to
 * happen here, allocating three intermediate lists per frame. It now happens in [GameEngine] in a
 * single pass over `entities` (held under `entitiesLock`, #118) that fills three engine-owned,
 * per-tick scratch buffers; this method just forwards those typed lists to the simulation. Keeping
 * the partition in the engine lets the buffers be reused across frames instead of re-allocated, and
 * keeps it under the lock that already guards every structural iteration of `entities`.
 */
object CollisionSystem {

    fun checkCollisions(
        simulation: Simulation,
        projectiles: List<ProjectileEntity>,
        enemies: List<EnemyEntity>,
        enemyProjectiles: List<EnemyProjectileEntity>,
        zigX: Float, zigY: Float, zigWidth: Float,
        onProjectileHitEnemy: (ProjectileEntity, EnemyEntity) -> Unit,
        onEnemyProjectileHitZiggurat: (EnemyProjectileEntity) -> Unit,
    ) {
        simulation.detectProjectileEnemyHits(projectiles, enemies, onProjectileHitEnemy)
        simulation.detectZigguratHits(enemyProjectiles, zigX, zigY, zigWidth, onEnemyProjectileHitZiggurat)
    }
}
