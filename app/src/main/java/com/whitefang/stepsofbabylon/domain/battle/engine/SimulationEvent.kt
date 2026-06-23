package com.whitefang.stepsofbabylon.domain.battle.engine

/**
 * One-shot side-effect events the in-round [Simulation] emits to the presentation layer
 * (collected by `BattleViewModel`). V1X-09 Phase 3 final slice (ADR-0012): replaces the two
 * `@Volatile` callback fields (`onStepReward`, `onBossKilled`) that `GameEngine` previously
 * exposed, so the game-loop → ViewModel hand-off is a single pure-domain stream instead of
 * mutable nullable lambdas. Pure domain — no Android imports.
 *
 * The collector ([Simulation.events]) intentionally does NOT replay past events to late
 * subscribers, so emitting these only ever produces an effect when a round is actively being
 * observed — exactly the lifecycle the callbacks had.
 */
sealed interface SimulationEvent {
    /**
     * A kill granted [amount] Battle Steps — the flat per-enemy reward, BEFORE the daily-cap
     * enforcement that lives in the collector (`AwardBattleSteps`). [x]/[y] are the enemy's
     * screen position so the collector can place a floating "+N Step" indicator (suppressed
     * when the cap leaves the kill uncredited).
     */
    data class StepReward(
        val amount: Long,
        val x: Float,
        val y: Float,
    ) : SimulationEvent

    /**
     * A BOSS enemy died at screen position [x]/[y]. [tier] scales the Power Stone reward the
     * collector awards via `AwardBossPowerStones`.
     */
    data class BossKilled(
        val tier: Int,
        val x: Float,
        val y: Float,
    ) : SimulationEvent
}
