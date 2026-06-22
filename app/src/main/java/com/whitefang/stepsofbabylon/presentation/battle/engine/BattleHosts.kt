package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity

/**
 * Narrow capability interfaces the GameEngine collaborators (UWController / BuffTickers /
 * CombatResolver) depend on, all implemented by [GameEngine] (#230/#231 decomposition). Keeps
 * collaborators free of a concrete-engine back-pointer (isolated + fakeable in tests) and avoids a
 * circular type dependency.
 *
 * **Threading contract (#118/#191):** every method here is UNSYNCHRONIZED and is invoked only from
 * inside the engine's already-held `entitiesLock` (the whole `GameEngine.update()` tick) or the
 * main-thread paths the engine wraps (`init`/`applyStats`/`initUWs`). Implementations must NOT add
 * their own `synchronized` block — see [aliveEnemies].
 */

interface UWHost {
    val ziggurat: ZigguratEntity?
    val screenWidth: Float
    val screenHeight: Float
    val reducedMotion: Boolean
    val uwCooldownMultiplier: Float
    val effectEngine: EffectEngine?
    val soundManager: SoundManager?

    /** Current resolved stats (the engine's `stats` field). */
    val currentStats: ResolvedStats

    /** The engine's single stats-mutation channel (mirrors stats onto the ziggurat + orb reconcile). */
    fun applyStats(stats: ResolvedStats)

    /**
     * Live alive-enemy list. #125 no-cache hot-path contract: the impl re-derives the list on EVERY
     * call and NEVER caches/snapshots it (a stale list lets an ongoing UW effect re-hit a corpse and
     * double-credit the kill). Caller holds `entitiesLock` (or is the single-threaded test /
     * main-thread `activateUW` path). The impl must stay UNSYNCHRONIZED — do not add
     * `synchronized(entitiesLock)`; it is a 60fps×4 hot path and an extra reentrant acquisition per
     * call would deviate from behavior-preservation.
     */
    fun aliveEnemies(): List<EnemyEntity>
}

interface BuffHost {
    val ziggurat: ZigguratEntity?

    /** Current wave phase (from the engine's WaveSpawner), or null before a spawner exists. */
    val wavePhase: WavePhase?
    val effectEngine: EffectEngine?
    val strings: Strings?

    /** Effective workshop level for a cash/utility upgrade type (engine's `effectiveLevels[type] ?: 0`). */
    fun wsLevel(type: UpgradeType): Int
}

interface CombatHost {
    val ziggurat: ZigguratEntity?
    val currentStats: ResolvedStats
    val conditions: BattleConditionEffects
    val tier: Int
    val effectEngine: EffectEngine?
    val soundManager: SoundManager?
    val strings: Strings?
    val reducedMotion: Boolean

    /** The engine-owned in-round simulation (cash credit, kill/step counters, event emit). */
    val simulation: Simulation

    /** Active GOLDEN_ZIGGURAT cash buff multiplier (read from UWController). */
    val fortuneMultiplier: Double
    val cashResearchMultiplier: Double
    val cashBonusPercent: Double

    /** SECOND_WIND HP-fraction restore target (engine @Volatile field). */
    val secondWindHpPercent: Double

    /**
     * One-shot test-and-set on the engine's `secondWindUsed` @Volatile flag: returns `true` exactly
     * once (the first call when second wind has not yet been consumed), then `false`. Preserves the
     * pre-refactor `!secondWindUsed → secondWindUsed = true` semantics; a read-only getter is
     * insufficient because the original is a write-back.
     */
    fun consumeSecondWind(): Boolean

    /** Queue an entity into the engine's `pendingAdd` (flushed into `entities` next tick). */
    fun addPending(entity: Entity)

    /** See [UWHost.aliveEnemies] — same #125 no-cache contract, same single engine impl. */
    fun aliveEnemies(): List<EnemyEntity>

    /** Live enemies within `zig.attackRange`, sorted by distance from the ziggurat, capped at [n]. */
    fun nearestEnemies(n: Int): List<EnemyEntity>

    fun wsLevel(type: UpgradeType): Int

    /** Apply a lifesteal heal (delegates to BuffTickers' accumulator/feedback path). */
    fun applyLifesteal(healAmount: Double)
}
