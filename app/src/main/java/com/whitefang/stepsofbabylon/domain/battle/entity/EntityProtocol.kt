package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Pure-domain view of a battle entity — just enough surface for the simulation to tick it
 * and run collision detection without touching Android `Canvas`. Implemented by the
 * presentation `Entity` base so `domain/battle/engine/Simulation` can own the per-frame
 * update loop and the collision sweep (V1X-09 Phase 3, ADR-0012). No Android imports.
 */
interface EntityProtocol {
    val isAlive: Boolean

    /** Centre position + collision width (radius = [width] / 2) read by the collision sweep. */
    val x: Float
    val y: Float
    val width: Float

    /**
     * `true` for entities CHRONO_FIELD should slow (enemies). [Simulation.tickEntities]
     * scales these entities' `deltaTime` by the active slow factor; everything else
     * (projectiles, orbs, the ziggurat) ticks at full speed. Defaults to `false`.
     */
    val isChronoSlowable: Boolean get() = false

    fun update(deltaTime: Float)
}
