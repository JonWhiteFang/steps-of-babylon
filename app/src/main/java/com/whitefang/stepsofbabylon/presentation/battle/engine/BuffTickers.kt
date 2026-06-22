package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.RapidFireSchedule
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText

/**
 * Per-mechanic in-round buff timers (#231 decomposition): RECOVERY_PACKAGES periodic heal (RO-08),
 * RAPID_FIRE periodic attack-speed burst (R4-03), and the LIFESTEAL fractional-heal accumulator
 * (R3-02 / #4). Lifted verbatim from GameEngine; reaches engine state via [BuffHost]. Loop-thread
 * only — invoked from inside the engine's held `entitiesLock`; holds no monitor of its own.
 */
class BuffTickers(private val host: BuffHost) {

    /** RECOVERY heal timer (accumulates during SPAWNING phase only). */
    private var recoveryTimer: Float = 0f

    /** RAPID_FIRE inter-burst timer. */
    private var rapidFireTimer: Float = 0f

    /** RAPID_FIRE active-burst countdown. */
    private var rapidFireActiveRemaining: Float = 0f

    /** LIFESTEAL fractional-heal accumulator (conserves sub-1-HP heals for visible feedback). */
    private var lifestealAccumulator: Double = 0.0

    /** Round-start reset (called by GameEngine.init under entitiesLock). */
    fun reset() {
        recoveryTimer = 0f
        rapidFireTimer = 0f
        rapidFireActiveRemaining = 0f
        lifestealAccumulator = 0.0
    }

    /**
     * RECOVERY_PACKAGES periodic-heal pulse (RO-08). Runs every game-loop tick during the
     * SPAWNING phase only; resets the accumulated timer between waves so the first pulse of
     * a new wave waits a full [SimulationMath.RECOVERY_INTERVAL_SECONDS]. Pulses are no-ops at full HP,
     * but the timer still advances so HP that drops mid-wave doesn't immediately trigger a fresh
     * pulse from a stale timer.
     *
     * Heal amount: `min(level × 1 %, 50 %)` of `maxHp` per pulse, capped to prevent godmode
     * at high levels. Floors at 1 HP healed (so any non-zero level produces visible feedback).
     * Spawns a green floating-text indicator above the ziggurat for visual feedback.
     */
    fun tickRecovery(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        // Preserve the original `waveSpawner ?: return` bare-return: when no spawner exists yet,
        // leave timers untouched (host.wavePhase is null -> would otherwise fall into the reset
        // branch below). Behaviorally dead in production (init always creates spawner+ziggurat
        // together) but keeps the move verbatim-equivalent.
        if (host.wavePhase == null) return
        if (host.wavePhase != WavePhase.SPAWNING) {
            recoveryTimer = 0f
            return
        }
        val level = host.wsLevel(UpgradeType.RECOVERY_PACKAGES)
        if (level <= 0) return
        recoveryTimer += deltaTime
        if (recoveryTimer < SimulationMath.RECOVERY_INTERVAL_SECONDS) return
        recoveryTimer = 0f
        if (zig.currentHp >= zig.maxHp) return
        val healAmount = SimulationMath.recoveryPulseAmount(level, zig.maxHp)
        zig.currentHp = SimulationMath.clampHp(zig.currentHp + healAmount, zig.maxHp)
        host.effectEngine?.addEffect(
            FloatingText(
                x = zig.x,
                y = zig.originY - 30f,
                text = host.strings?.healHp(healAmount.toInt()) ?: "+${healAmount.toInt()} HP",
                color = FloatingText.STEP_COLOR,
            ),
        )
    }

    /**
     * Rapid Fire periodic-burst tick (R4-03). Mirrors [tickRecovery]: only fires during the
     * SPAWNING wave phase; resets state on cooldown phase so a partial timer doesn't leak across
     * the wave boundary; level 0 is a no-op. Order matters: the active-decrement step runs *before*
     * the timer-fire step so at L10 (interval == duration) the multiplier transitions seamlessly
     * from one burst to the next within a single tick, with no observable 1-frame gap of `1f`.
     */
    fun tickRapidFire(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        // Preserve the original `waveSpawner ?: return` bare-return (see tickRecovery).
        if (host.wavePhase == null) return
        if (host.wavePhase != WavePhase.SPAWNING) {
            // Wave-cooldown reset matches RECOVERY_PACKAGES semantics: a survived wave
            // shouldn't grant a head-start on the next wave's first burst.
            rapidFireTimer = 0f
            rapidFireActiveRemaining = 0f
            zig.rapidFireMultiplier = 1f
            return
        }
        val level = host.wsLevel(UpgradeType.RAPID_FIRE)
        if (level <= 0) return

        val interval = RapidFireSchedule.interval(level)
        val duration = RapidFireSchedule.duration(level)
        val multiplier = RapidFireSchedule.multiplier(level)

        // 1. Tick down the active burst countdown.
        if (rapidFireActiveRemaining > 0f) {
            rapidFireActiveRemaining -= deltaTime
            if (rapidFireActiveRemaining <= 0f) {
                rapidFireActiveRemaining = 0f
                zig.rapidFireMultiplier = 1f
            }
        }

        // 2. Tick up the inter-burst timer; fire when it crosses interval.
        rapidFireTimer += deltaTime
        if (rapidFireTimer >= interval) {
            rapidFireTimer = 0f
            rapidFireActiveRemaining = duration
            zig.rapidFireMultiplier = multiplier
            host.effectEngine?.addEffect(
                FloatingText(
                    x = zig.x,
                    y = zig.originY - 30f,
                    text = host.strings?.rapidFireBurst() ?: "RAPID FIRE!",
                    color = FloatingText.DEFAULT_COLOR,
                ),
            )
        }
    }

    /**
     * Applies a lifesteal heal of [healAmount] HP to the ziggurat (R3-02). The HP delta is
     * added directly to `zig.currentHp` (a `Double`, so sub-1-HP heals are conserved); the
     * same fractional amount is accumulated in [lifestealAccumulator]; each time the
     * accumulator crosses an integer HP threshold a `+X HP` `FloatingText` indicator is spawned.
     */
    fun applyLifesteal(healAmount: Double) {
        val zig = host.ziggurat ?: return
        zig.currentHp = SimulationMath.clampHp(zig.currentHp + healAmount, zig.maxHp)
        val tick = SimulationMath.tickLifestealAccumulator(lifestealAccumulator, healAmount)
        lifestealAccumulator = tick.newAccumulator
        if (tick.visibleHp > 0) {
            host.effectEngine?.addEffect(
                FloatingText(
                    x = zig.x,
                    y = zig.originY - 30f,
                    text = host.strings?.healHp(tick.visibleHp) ?: "+${tick.visibleHp} HP",
                    color = FloatingText.STEP_COLOR,
                ),
            )
        }
    }
}
