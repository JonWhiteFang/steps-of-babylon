package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Pure-domain HP surface a combat resolver needs to apply damage / heal a battle entity, without
 * touching the Canvas-coupled presentation types (ADR-0012 Phase 5, #306). Deliberately NOT a subtype
 * of [EntityProtocol] — HP is orthogonal to the positional/tickable surface, and a non-positional state
 * ([ZigguratState]) implements only this. The deferred enemy slice will declare
 * `EnemyState : EntityProtocol, Damageable`. No Android imports.
 */
interface Damageable {
    var currentHp: Double
    val maxHp: Double
}
