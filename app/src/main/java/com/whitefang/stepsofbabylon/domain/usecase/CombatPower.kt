package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats

/**
 * Combat-power index — a steady-state single-target DPS *proxy* used ONLY to rank Workshop upgrades
 * and drive the value indicator (#29, spec §2). It is deliberately simple:
 *
 *     combatPower = damage × attackSpeed × (1 + critChance × (critMultiplier − 1))
 *
 * The third factor is the standard expected-crit multiplier. It intentionally ignores
 * `multishotTargets`, `bounceCount`, `orbCount`, `range`, `damagePerMeterBonus`, knockback, and all
 * sustain — these are either runtime-variable, periodic, or not single-target throughput, and none of
 * their upgrades are ranked on the Workshop screen, so omitting them never misranks a candidate
 * (spec §2 BUG-1/BUG-2).
 *
 * **It is a comparison instrument, not a balance input.** It returns a bare `Double`; the engine's
 * stat sinks (`GameEngine.setStats`/`updateZigguratStats`/`applyStats`, `ZigguratState.updateStats`)
 * all take `ResolvedStats`, so this proxy is type-incompatible with every real-stat channel and can
 * never feed the simulation (spec INV-3). The "combat power" UI naming is the player-facing guard.
 *
 * Pure Kotlin — no Android imports — guarded by `architecture/DomainPurityTest`.
 */
class CombatPower {
    operator fun invoke(stats: ResolvedStats): Double =
        stats.damage *
            stats.attackSpeed *
            (1.0 + stats.critChance * (stats.critMultiplier - 1.0))
}
