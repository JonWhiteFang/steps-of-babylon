package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.EntityProtocol
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.hypot
import kotlin.math.min

/**
 * Pure-domain owner of in-round simulation state. Introduced by V1X-09 Phase 3
 * (ADR-0012) as the destination for simulation logic that currently lives in the
 * Canvas-coupled `presentation/battle/engine/GameEngine`. No Android imports — fully
 * unit-testable without Robolectric.
 *
 * **In-round state owner (growing per Phase 3 slice).** [GameEngine] holds a [Simulation]
 * and delegates to it: the cash economy (`cash` / `totalCashEarned` / `spendCash`) plus the
 * round-progress counters (`totalEnemiesKilled` / `totalStepsEarned` / `elapsedSeconds` +
 * [hasWaveProgress]). The engine's public API (and every caller — `BattleViewModel` polling,
 * `BattleScreen`, `GameEngineTest`) is unchanged. The reward *formulas* (per-kill cash,
 * wave-complete cash) stay in [GameEngine] — they depend on tier config, enemy type, and the
 * engine's multiplier fields — and call [creditCash] / [applyInterest] / [creditSteps] /
 * [recordEnemyKilled] with the already-computed values. Subsequent Phase 3 slices migrate
 * further state here.
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

    /** Enemies killed this round. */
    @Volatile
    var totalEnemiesKilled: Int = 0
        private set

    /** Battle Steps earned this round (pre-cap; cap enforcement lives in the credit listener). */
    @Volatile
    var totalStepsEarned: Long = 0L
        private set

    /** Wall-clock seconds the round has been running. */
    @Volatile
    var elapsedSeconds: Float = 0f
        private set

    /**
     * Hot stream of one-shot side-effect events the game loop produces and the presentation
     * layer (`BattleViewModel`) consumes (V1X-09 Phase 3 final slice, ADR-0012). Replaces the
     * engine's two `@Volatile` callbacks. `replay = 0` so a late subscriber — e.g. the fresh
     * polling-loop collector `BattleViewModel.playAgain` starts for the next round — never
     * re-receives a prior round's events (that would double-credit). `extraBufferCapacity` +
     * [BufferOverflow.DROP_OLDEST] let [emit] hand off without ever suspending the game-loop
     * thread.
     */
    private val _events =
        MutableSharedFlow<SimulationEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<SimulationEvent> = _events.asSharedFlow()

    /** Emits [event] to [events] without suspending — safe to call from the game-loop thread. */
    fun emit(event: SimulationEvent) {
        _events.tryEmit(event)
    }

    /** Zeroes all in-round state at round start. */
    fun reset() {
        cash = 0L
        totalCashEarned = 0L
        totalEnemiesKilled = 0
        totalStepsEarned = 0L
        elapsedSeconds = 0f
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

    /** Advances the round clock. */
    fun tickElapsed(deltaTime: Float) {
        elapsedSeconds += deltaTime
    }

    /** Records one enemy kill. */
    fun recordEnemyKilled() {
        totalEnemiesKilled++
    }

    /** Accumulates Battle Steps earned this round. No-op for non-positive amounts. */
    fun creditSteps(amount: Long) {
        if (amount <= 0L) return
        totalStepsEarned += amount
    }

    /**
     * True once the round has made observable progress — at least one tick elapsed or one
     * enemy killed. Backs
     * [com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine.hasWaveProgress]
     * (the surface-recreation guard + mid-nav persistence decision).
     */
    fun hasWaveProgress(): Boolean = elapsedSeconds > 0f || totalEnemiesKilled > 0

    /**
     * Advances every entity one frame. Entities flagged [EntityProtocol.isChronoSlowable]
     * (enemies) are ticked with `deltaTime × [chronoSlowFactor]` so the CHRONO_FIELD UW
     * slows them; everything else (projectiles, orbs, the ziggurat) ticks at the full
     * [deltaTime]. Pass `1f` for [chronoSlowFactor] when CHRONO_FIELD is inactive (then
     * slowable entities also tick at full speed). Lifted verbatim from the
     * `GameEngine.update()` loop in V1X-09 Phase 3 (ADR-0012) — the engine previously did
     * the `e is EnemyEntity` type check inline, which kept the loop trapped in the
     * Canvas-coupled presentation layer.
     */
    fun tickEntities(
        entities: List<EntityProtocol>,
        deltaTime: Float,
        chronoSlowFactor: Float,
    ) {
        entities.forEach { e ->
            val dt = if (e.isChronoSlowable) deltaTime * chronoSlowFactor else deltaTime
            e.update(dt)
        }
    }

    /**
     * Player-projectile → enemy collision sweep. For each [projectiles] entry, fires [onHit]
     * with the FIRST overlapping [enemies] entry and stops (the original `break`). Overlap =
     * centre distance < sum of radii (radius = width / 2).
     *
     * [onHit] fires synchronously inside the loop, exactly as the pre-extraction
     * presentation `CollisionSystem` did, so side effects that move or kill an enemy
     * (knockback, death) are observed by subsequent projectiles' overlap tests this same
     * frame — behaviour is identical to the inline nested loop. Generic over [EntityProtocol]
     * so callers pass concretely-typed lists + callbacks while the sweep stays Canvas-free.
     * The caller ([CollisionSystem]) supplies already-`filterIsInstance`'d, already-alive
     * snapshots.
     */
    fun <P : EntityProtocol, E : EntityProtocol> detectProjectileEnemyHits(
        projectiles: List<P>,
        enemies: List<E>,
        onHit: (P, E) -> Unit,
    ) {
        for (proj in projectiles) {
            for (enemy in enemies) {
                if (hypot(proj.x - enemy.x, proj.y - enemy.y) < (proj.width + enemy.width) / 2f) {
                    onHit(proj, enemy)
                    break
                }
            }
        }
    }

    /**
     * Enemy-projectile → ziggurat collision sweep. Fires [onHit] for each [enemyProjectiles]
     * entry whose centre is within `zigWidth / 2 + projWidth / 2` of the ziggurat centre.
     * Like [detectProjectileEnemyHits], [onHit] fires synchronously inside the loop so the
     * behaviour matches the pre-extraction presentation `CollisionSystem`.
     */
    fun <P : EntityProtocol> detectZigguratHits(
        enemyProjectiles: List<P>,
        zigX: Float,
        zigY: Float,
        zigWidth: Float,
        onHit: (P) -> Unit,
    ) {
        for (proj in enemyProjectiles) {
            if (hypot(proj.x - zigX, proj.y - zigY) < zigWidth / 2f + proj.width / 2f) {
                onHit(proj)
            }
        }
    }

    // --- UW lifecycle timing (V1X-09 Phase 3, ADR-0012) ---

    /**
     * Result of advancing one Ultimate Weapon's two lifecycle timers by a frame. The engine
     * applies [cooldownRemaining] / [effectTimeRemaining] back onto its `UWState` and uses the
     * two flags to decide which presentation-coupled side-effects to run this frame:
     * - [effectWasActive] — the effect was running at frame start, so its per-frame ongoing
     *   effect (BLACK_HOLE pull/DoT, POISON_SWAMP DoT) should fire. True even on the frame the
     *   effect expires — matching the pre-extraction inline block, where the ongoing `when`
     *   sat inside the `effectTimeRemaining > 0f` guard and so ran one final time on expiry.
     * - [justExpired] — the effect crossed to ≤0 this frame, so its one-shot expiry side-effects
     *   (CHRONO_FIELD slow reset, GOLDEN_ZIGGURAT stat/fortune restore) should run.
     */
    data class UWTimerAdvance(
        val cooldownRemaining: Float,
        val effectTimeRemaining: Float,
        val effectWasActive: Boolean,
        val justExpired: Boolean,
    )

    /**
     * Advances a UW's cooldown + effect-duration timers by [deltaTime]. Pure: takes the two
     * current timer values, returns the new values + transition flags (see [UWTimerAdvance]).
     * The cooldown counts down to a floor of 0; the effect duration counts down only while
     * active and clamps to exactly 0 on the frame it crosses. Lifted verbatim from the
     * `GameEngine.updateUWs` inline timer block in V1X-09 Phase 3 (ADR-0012) — the engine keeps
     * the side-effects (which touch enemies, stats, and visual flags) and just applies this
     * result.
     */
    fun advanceUWTimers(
        cooldownRemaining: Float,
        effectTimeRemaining: Float,
        deltaTime: Float,
    ): UWTimerAdvance {
        val newCooldown =
            if (cooldownRemaining >
                0f
            ) {
                (cooldownRemaining - deltaTime).coerceAtLeast(0f)
            } else {
                cooldownRemaining
            }
        val effectWasActive = effectTimeRemaining > 0f
        var newEffect = effectTimeRemaining
        var justExpired = false
        if (effectWasActive) {
            newEffect -= deltaTime
            if (newEffect <= 0f) {
                newEffect = 0f
                justExpired = true
            }
        }
        return UWTimerAdvance(newCooldown, newEffect, effectWasActive, justExpired)
    }

    /**
     * Auto-trigger readiness predicate: a UW fires when it is off cooldown AND not mid-effect.
     * Matches the gate the engine's auto-trigger loop used inline pre-extraction.
     */
    fun isUWReadyToFire(
        cooldownRemaining: Float,
        effectTimeRemaining: Float,
    ): Boolean = cooldownRemaining <= 0f && effectTimeRemaining <= 0f
}
