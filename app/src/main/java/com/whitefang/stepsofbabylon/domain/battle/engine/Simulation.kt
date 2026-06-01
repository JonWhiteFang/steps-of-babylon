package com.whitefang.stepsofbabylon.domain.battle.engine

import kotlin.math.min

/**
 * Pure-domain owner of in-round simulation state. Introduced by V1X-09 Phase 3
 * (ADR-0012) as the destination for simulation logic that currently lives in the
 * Canvas-coupled `presentation/battle/engine/GameEngine`. No Android imports — fully
 * unit-testable without Robolectric.
 *
 * **First slice — the in-round cash economy.** [GameEngine] holds a [Simulation] and
 * delegates its `cash` / `totalCashEarned` / `spendCash` public surface here, so the
 * engine's API (and every caller — `BattleViewModel` polling, `BattleScreen`,
 * `GameEngineTest`) is unchanged. The reward *formulas* (per-kill cash, wave-complete
 * cash) stay in [GameEngine] for now — they depend on tier config, enemy type, and the
 * engine's multiplier fields — and call [creditCash] / [applyInterest] with the
 * already-computed amounts. Subsequent Phase 3 slices migrate further state here.
 *
 * [cash] and [totalCashEarned] are `@Volatile`: they are written on the game-loop
 * thread (kills, wave completion) and read from the `BattleViewModel` polling coroutine
 * on a different thread, mirroring the volatility the engine fields had pre-extraction.
 */
class Simulation {

    @Volatile
    var cash: Long = 0L
        private set

    @Volatile
    var totalCashEarned: Long = 0L
        private set

    /** Zeroes the cash economy at round start. */
    fun reset() {
        cash = 0L
        totalCashEarned = 0L
    }

    /**
     * Credits [amount] cash from a kill or wave-complete payout, tracking it toward the
     * lifetime [totalCashEarned] counter. No-op for non-positive amounts (the pre-extraction
     * reward computations never produced negatives; a zero credit was already a no-op).
     */
    fun creditCash(amount: Long) {
        if (amount <= 0L) return
        cash += amount
        totalCashEarned += amount
    }

    /**
     * Applies between-wave compound interest: `+min(level × 0.5 %, 10 %)` of the current
     * [cash] balance. Interest is intentionally NOT counted toward [totalCashEarned]
     * (matches pre-extraction behaviour — only earned cash, not interest yield, is a
     * lifetime stat). No-op at level 0.
     */
    fun applyInterest(interestLevel: Int) {
        if (interestLevel <= 0) return
        cash += (cash * min(interestLevel * 0.005, 0.10)).toLong()
    }

    /**
     * Deducts [amount] for an in-round upgrade purchase. Returns `false` and leaves [cash]
     * untouched when the balance is insufficient.
     */
    fun spend(amount: Long): Boolean {
        if (cash < amount) return false
        cash -= amount
        return true
    }
}
