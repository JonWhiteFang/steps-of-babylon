package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Pure-domain view of a battle entity — just enough surface for the simulation to tick it
 * without touching Android `Canvas`. Implemented by the presentation `Entity` base so
 * `domain/battle/engine/Simulation` can own the per-frame update loop (V1X-09 Phase 3,
 * ADR-0012). No Android imports.
 */
interface EntityProtocol {
    val isAlive: Boolean

    /**
     * `true` for entities CHRONO_FIELD should slow (enemies). [Simulation.tickEntities]
     * scales these entities' `deltaTime` by the active slow factor; everything else
     * (projectiles, orbs, the ziggurat) ticks at full speed. Defaults to `false`.
     */
    val isChronoSlowable: Boolean get() = false

    fun update(deltaTime: Float)
}
