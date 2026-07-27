package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Enemy HP + armor surface a combat resolver mutates when applying damage (#306, ADR-0012 Phase 5
 * Slice 2). Extends [Damageable] (the shared currentHp/maxHp surface) with the armor-charge count that
 * absorbs a hit before HP is lost (#17). Armor is enemy-specific — the ziggurat has none — so it lives
 * here, not on [Damageable]. Implemented by [EnemyState]. No Android imports.
 */
interface DamageableEnemy : Damageable {
    var armorHits: Int
}
