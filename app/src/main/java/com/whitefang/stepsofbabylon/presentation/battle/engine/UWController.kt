package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.presentation.audio.SoundEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.UWVisualEffect
import kotlin.math.hypot

/**
 * Ultimate Weapon lifecycle controller (#231 decomposition): per-UW cooldown/effect state machine,
 * auto-trigger, ongoing effects (BLACK_HOLE / POISON_SWAMP), and the CHRONO_FIELD / GOLDEN_ZIGGURAT
 * activation + expiry side-effects, plus the #119 GOLDEN re-layer. Lifted verbatim from GameEngine.
 * Pure timer arithmetic delegates to the engine-owned [Simulation] (advanceUWTimers / isUWReadyToFire
 * are pure stateless helpers). Reaches engine state via [UWHost]. Loop-thread paths run inside the
 * engine's held `entitiesLock`; [initUWs] / [uwSnapshot] are the main-thread paths the engine wraps
 * (#191 CONC-2). Holds no monitor of its own.
 */
class UWController(
    private val host: UWHost,
    private val simulation: Simulation,
) {
    /**
     * R4-06: per-UW state with three independent path levels (see UWPath). Pre-R4-06 this held a
     * single `level: Int`; the engine now reads each path explicitly via [damageLevel] /
     * [secondaryLevel] / [cooldownLevel] and computes the per-path effect via
     * [UltimateWeaponType.valueAtLevel]. The active CHRONO_FIELD slow factor is captured in
     * [chronoSlowFactor] at activation time so a re-equip / level-up mid-effect can't change the
     * slow strength on an already-firing UW.
     */
    data class UWState(
        val type: UltimateWeaponType,
        val damageLevel: Int,
        val secondaryLevel: Int,
        val cooldownLevel: Int,
        var cooldownRemaining: Float = 0f,
        var effectTimeRemaining: Float = 0f,
    )

    val uwStates = mutableListOf<UWState>()

    var chronoActive = false
        private set

    /**
     * Active CHRONO_FIELD slow factor. Set to the firing UW's [UltimateWeaponType.damageAtLevel]
     * value at activation; reset to 1.0 when [chronoActive] flips back to false. Smaller value =
     * stronger slow.
     */
    var chronoSlowFactor: Float = 1f
        private set

    private var goldenZigActive = false
    private var preGoldenStats: ResolvedStats? = null

    /**
     * Active GOLDEN_ZIGGURAT damage multiplier (#119). Captured at [activateUW] from the SECONDARY
     * path and reset to `1.0` on expiry / [resetRoundState]. GOLDEN is modelled as a damage *layer*
     * re-derived over the current base stats rather than a one-shot snapshot: when an in-round
     * upgrade lands while GOLDEN is active, [relayerBaseStats] re-captures [preGoldenStats] to the
     * new base and re-applies this multiplier, so the purchase survives GOLDEN expiry instead of
     * being rolled back to the stale activation snapshot.
     */
    private var goldenDamageMult: Double = 1.0

    /**
     * Active GOLDEN_ZIGGURAT cash buff multiplier. Written by [activateUW] (set to the DAMAGE-path
     * value) and reset to 1.0 by the GOLDEN expiry branch in [update]. Pre-R4-01 this multiplier was
     * shared between Step Overdrive (FORTUNE) and the Ultimate Weapon (GOLDEN); R4-01 deletes Step
     * Overdrive so GOLDEN is the sole writer. Read by CombatResolver's cash formulas via
     * CombatHost.fortuneMultiplier.
     */
    var fortuneMultiplier: Double = 1.0
        private set

    /**
     * Round-start reset of all UW lifecycle fields. Called by GameEngine.init() from INSIDE the
     * engine's held entitiesLock (mirrors the pre-decomposition inline reset at GameEngine.init,
     * preserving the lock-provided happens-before for these plain non-@Volatile vars). NOT folded
     * into initUWs (which separately populates uwStates from equipped weapons).
     */
    fun resetRoundState() {
        uwStates.clear()
        chronoActive = false
        chronoSlowFactor = 1f
        goldenZigActive = false
        preGoldenStats = null
        goldenDamageMult = 1.0
        fortuneMultiplier = 1.0
    }

    /** Test seam for the chrono overlay (delegated from GameEngine.setChronoActiveForTest). */
    @androidx.annotation.VisibleForTesting
    internal fun setChronoActiveForTest(active: Boolean) {
        chronoActive = active
    }

    /**
     * #119 GOLDEN re-layer entry point, called by GameEngine.updateZigguratStats on the MAIN thread.
     * When GOLDEN is active, treat [newStats] as the new BASE: re-capture preGoldenStats and re-apply
     * the active goldenDamageMult on top, returning the stats the engine should actually apply. When
     * inactive, returns [newStats] unchanged. PRE-EXISTING lock-free main-thread path — this is a
     * single adjacent read-modify-write of the GOLDEN trio with NO host round-trip that re-reads the
     * trio; do NOT add a lock (preserve the exact pre-decomposition shape, #119 / spec §5.B).
     */
    fun relayerBaseStats(newStats: ResolvedStats): ResolvedStats =
        if (goldenZigActive) {
            preGoldenStats = newStats
            newStats.copy(damage = newStats.damage * goldenDamageMult)
        } else {
            newStats
        }

    /**
     * R4-06: populates [uwStates] from the player's equipped UWs, mapping each [OwnedWeapon]'s 3
     * path levels onto a [UWState] and zeroing the active timers. Called by BattleViewModel after
     * loading the equipped-UW set from the repository.
     */
    fun initUWs(equipped: List<OwnedWeapon>) {
        uwStates.clear()
        equipped.forEach {
            uwStates.add(
                UWState(
                    type = it.type,
                    damageLevel = it.damageLevel,
                    secondaryLevel = it.secondaryLevel,
                    cooldownLevel = it.cooldownLevel,
                ),
            )
        }
    }

    fun uwSnapshot(): List<UWState> = uwStates.toList()

    fun resetUWCooldowns() {
        uwStates.forEach { it.cooldownRemaining = 0f }
    }

    /**
     * R4-06: fires the UW at [index] if it's off cooldown and not already mid-effect.
     * Pre-R4-06 this was the entry point for the player-tap activation path; post-R4-06
     * the UW is auto-fired from [update] when its cooldown reaches 0 AND enemies are
     * present (`UltimateWeaponBar` is now a passive cooldown indicator). The method
     * stays public for direct invocation by tests.
     *
     * Per-path reads (replaces the pre-R4-06 single-`level` reads):
     * - DEATH_WAVE: damage from DAMAGE path; SECONDARY (radius fraction) feeds visual but
     *   damage applies to all aliveEnemies for v1 of R4-06 (radius UI deferred).
     * - CHAIN_LIGHTNING: damage from DAMAGE path; chain length from SECONDARY path.
     * - BLACK_HOLE: ongoing-effect handled in [update]; activation just primes the
     *   timer + visual.
     * - CHRONO_FIELD: slow factor from DAMAGE path written to [chronoSlowFactor];
     *   duration from SECONDARY path overrides [UltimateWeaponType.effectDurationSeconds].
     * - POISON_SWAMP: ongoing-effect handled in [update].
     * - GOLDEN_ZIGGURAT: cash multiplier from DAMAGE path → [fortuneMultiplier]
     *   (`coerceAtLeast` preserves any pre-existing higher buff, defensive for future
     *   multi-source stacking); damage multiplier from SECONDARY path → stats copy.
     */
    fun activateUW(index: Int) {
        val uw = uwStates.getOrNull(index) ?: return
        if (uw.cooldownRemaining > 0f) return
        if (uw.effectTimeRemaining > 0f) return
        // R4-06: cooldown comes from per-UW-state COOLDOWN path. RO-11 #A.2 outer multiplier
        // (UW_COOLDOWN lab research) still stacks via uwCooldownMultiplier.
        uw.cooldownRemaining = uw.type.cooldownAtLevel(uw.cooldownLevel) * host.uwCooldownMultiplier
        val zig = host.ziggurat ?: return

        // CHRONO_FIELD overrides the flat effectDurationSeconds with the SECONDARY-path value; all
        // other UWs use the flat constant.
        val duration =
            if (uw.type == UltimateWeaponType.CHRONO_FIELD) {
                uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
            } else {
                uw.type.effectDurationSeconds
            }
        if (duration > 0f) uw.effectTimeRemaining = duration

        host.soundManager?.play(SoundEffect.UW_ACTIVATE)

        // Spawn particle-based UW visual effect
        val fx = host.effectEngine
        if (fx != null) {
            val effectDuration =
                when {
                    uw.type == UltimateWeaponType.DEATH_WAVE -> 1.2f
                    duration > 0f -> duration
                    else -> 0.5f
                }
            val cx: Float
            val cy: Float
            when (uw.type) {
                UltimateWeaponType.BLACK_HOLE -> {
                    cx = host.screenWidth / 2f
                    cy = host.screenHeight * 0.35f
                }

                UltimateWeaponType.POISON_SWAMP -> {
                    cx = host.screenWidth / 2f
                    cy = host.screenHeight * 0.6f
                }

                else -> {
                    cx = zig.x
                    cy = zig.originY
                }
            }
            fx.addEffect(
                UWVisualEffect(
                    uw.type,
                    fx.pool,
                    cx,
                    cy,
                    host.screenWidth,
                    host.screenHeight,
                    effectDuration,
                    host.reducedMotion,
                ),
            )
        }

        // Screen shake for Death Wave
        if (uw.type == UltimateWeaponType.DEATH_WAVE && !host.reducedMotion) {
            fx?.screenShake?.trigger(12f, 0.4f)
        }

        when (uw.type) {
            UltimateWeaponType.DEATH_WAVE -> {
                val dmg = uw.type.damageAtLevel(uw.damageLevel)
                host.aliveEnemies().forEach { it.takeDamage(dmg) }
            }

            UltimateWeaponType.CHAIN_LIGHTNING -> {
                val dmg = uw.type.damageAtLevel(uw.damageLevel)
                val chainLen =
                    uw.type
                        .secondaryAtLevel(uw.secondaryLevel)
                        .toInt()
                        .coerceAtLeast(1)
                val targets = host.aliveEnemies().sortedBy { hypot(it.x - zig.x, it.y - zig.y) }.take(chainLen)
                targets.forEach { it.takeDamage(dmg) }
            }

            UltimateWeaponType.BLACK_HOLE -> {}

            // Ongoing effect in update
            UltimateWeaponType.CHRONO_FIELD -> {
                chronoActive = true
                chronoSlowFactor = uw.type.damageAtLevel(uw.damageLevel).toFloat()
            }

            UltimateWeaponType.POISON_SWAMP -> {}

            // Ongoing effect in update
            UltimateWeaponType.GOLDEN_ZIGGURAT -> {
                goldenZigActive = true
                preGoldenStats = host.currentStats
                // R4-06: cash multiplier comes from DAMAGE path. coerceAtLeast preserves any
                // pre-existing higher fortuneMultiplier value (defensive for future multi-source
                // stacking; R4-01 already removed Step Overdrive as the only other writer, so in
                // practice this only matters across overlapping GOLDEN activations — prevented by
                // cooldown gating but harmless).
                val cashMult = uw.type.damageAtLevel(uw.damageLevel)
                fortuneMultiplier = fortuneMultiplier.coerceAtLeast(cashMult)
                // Damage multiplier comes from SECONDARY path (replaces hard-coded 1.5×).
                // #119: persist the multiplier so an in-round purchase mid-GOLDEN can re-layer it
                // over the new base (see [relayerBaseStats]).
                val dmgMult = uw.type.secondaryAtLevel(uw.secondaryLevel)
                goldenDamageMult = dmgMult
                host.applyStats(host.currentStats.copy(damage = host.currentStats.damage * dmgMult))
            }
        }
    }

    fun update(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        for (uw in uwStates) {
            // V1X-09 Phase 3 (ADR-0012): the pure cooldown + effect-duration timer arithmetic lives
            // in the domain Simulation. The controller applies the result and runs the
            // presentation-coupled side-effects (expiry stat/flag writes, ongoing enemy damage).
            // Behaviour is identical to the pre-extraction inline block — the ongoing `when` still
            // runs on the tick the effect expires because it is gated on
            // Simulation.UWTimerAdvance.effectWasActive (the old `effectTimeRemaining > 0f` guard).
            val timers = simulation.advanceUWTimers(uw.cooldownRemaining, uw.effectTimeRemaining, deltaTime)
            uw.cooldownRemaining = timers.cooldownRemaining
            uw.effectTimeRemaining = timers.effectTimeRemaining
            if (timers.effectWasActive) {
                if (timers.justExpired) {
                    when (uw.type) {
                        UltimateWeaponType.CHRONO_FIELD -> {
                            chronoActive = false
                            chronoSlowFactor = 1f
                        }

                        UltimateWeaponType.GOLDEN_ZIGGURAT -> {
                            // #119: preGoldenStats now reflects any in-round upgrade bought during
                            // the GOLDEN window (re-captured by relayerBaseStats), so restoring it
                            // preserves the purchase instead of discarding it.
                            goldenZigActive = false
                            preGoldenStats?.let { host.applyStats(it) }
                            preGoldenStats = null
                            goldenDamageMult = 1.0
                            // R4-01 / R4-06: GOLDEN is the sole writer of fortuneMultiplier
                            // post-R4-01 (Step Overdrive removed). Per-path damage value
                            // determines activation strength but expiry always resets to 1.0×
                            // because no other writer exists.
                            fortuneMultiplier = 1.0
                        }

                        else -> {}
                    }
                }
                // Ongoing effects (per-path reads)
                when (uw.type) {
                    UltimateWeaponType.BLACK_HOLE -> {
                        val cx = host.screenWidth / 2f
                        val cy = host.screenHeight * 0.35f
                        // R4-06: damage DPS from DAMAGE path; pull strength from SECONDARY path.
                        val dps = uw.type.damageAtLevel(uw.damageLevel)
                        val pull = uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
                        host.aliveEnemies().forEach { e ->
                            val dx = cx - e.x
                            val dy = cy - e.y
                            val d = hypot(dx, dy).coerceAtLeast(1f)
                            e.x += dx / d * pull * deltaTime
                            e.y += dy / d * pull * deltaTime
                            e.takeDamage(dps * deltaTime)
                        }
                    }

                    UltimateWeaponType.POISON_SWAMP -> {
                        // R4-06: DoT % MaxHP/sec from DAMAGE path. Area path (SECONDARY) is captured
                        // for visual / future filtering but every alive enemy is hit in v1 of R4-06.
                        val dotFrac = uw.type.damageAtLevel(uw.damageLevel)
                        host.aliveEnemies().forEach { e -> e.takeDamage(e.maxHp * dotFrac * deltaTime) }
                    }

                    else -> {}
                }
            }
        }

        // R4-06: auto-trigger. Fire any UW whose cooldown has reached 0 AND that's not currently
        // mid-effect, but only when at least one enemy is alive on screen — empty-screen fires would
        // burn cooldowns during wave-cooldown breaks for no benefit.
        if (uwStates.isNotEmpty() && host.aliveEnemies().isNotEmpty()) {
            for (i in uwStates.indices) {
                val uw = uwStates[i]
                if (simulation.isUWReadyToFire(uw.cooldownRemaining, uw.effectTimeRemaining)) {
                    activateUW(i)
                }
            }
        }
    }
}
