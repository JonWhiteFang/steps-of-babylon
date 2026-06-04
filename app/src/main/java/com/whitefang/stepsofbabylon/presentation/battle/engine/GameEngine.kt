package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.RapidFireSchedule
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDamage
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
import com.whitefang.stepsofbabylon.presentation.audio.SoundEffect
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.biome.BackgroundRenderer
import com.whitefang.stepsofbabylon.presentation.battle.biome.BiomeTheme
import com.whitefang.stepsofbabylon.presentation.battle.effects.DeathEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText
import com.whitefang.stepsofbabylon.presentation.battle.effects.ProjectileTrailEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.UWVisualEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.WaveAnnouncement
import com.whitefang.stepsofbabylon.presentation.battle.effects.WaveCooldownText
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.OrbEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import com.whitefang.stepsofbabylon.presentation.battle.ui.HealthBarRenderer
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.random.Random

class GameEngine {

    private val entities = mutableListOf<Entity>()
    private val pendingAdd = mutableListOf<Entity>()
    private val healthBarRenderer = HealthBarRenderer()
    private val calculateDamage = CalculateDamage()
    private val calculateDefense = CalculateDefense()

    var screenWidth: Float = 0f; private set
    var screenHeight: Float = 0f; private set
    var ziggurat: ZigguratEntity? = null; private set
    var waveSpawner: WaveSpawner? = null; private set
    private var stats: ResolvedStats = ResolvedStats()
    private var tier: Int = 1
    private var conditions: BattleConditionEffects = BattleConditionEffects()
    /**
     * Effective level lookup for cash-utility computations (`CASH_BONUS`, `CASH_PER_WAVE`,
     * `INTEREST`, `FREE_UPGRADES`). Initially seeded from the player's Workshop levels at
     * [init], then updated by [updateEffectiveLevels] on every in-round upgrade purchase
     * so that mid-round cash-utility purchases actually take effect (RO-08). Pre-RO-08
     * the field was named `workshopLevels` and was set once at battle start, which made
     * in-round purchases of these four utilities silently no-op.
     *
     * Stat-bearing upgrades (damage, attack speed, etc.) propagate via [stats] /
     * [applyStats] — they don't read from this map.
     */
    private var effectiveLevels: Map<UpgradeType, Int> = emptyMap()
    private var backgroundRenderer: BackgroundRenderer? = null
    private var biomeTheme: BiomeTheme = BiomeTheme.forBiome(Biome.HANGING_GARDENS)

    // Effects
    var effectEngine: EffectEngine? = null; private set
    var soundManager: SoundManager? = null
    /** Engine-internal display strings (V1X-13, ADR-0014). Set by [GameSurfaceView]; null in pure-JVM tests, which fall back to literals. */
    var strings: Strings? = null
    private var reducedMotion: Boolean = false
    private var cooldownText: WaveCooldownText? = null
    private var lastWave: Int = 0

    /**
     * In-round cash economy, extracted to the pure-domain [Simulation] in V1X-09 Phase 3
     * (ADR-0012). The engine delegates its `cash` / `totalCashEarned` / `spendCash` public
     * surface here; callers (BattleViewModel polling, BattleScreen, tests) are unchanged.
     */
    private val simulation = Simulation()
    val cash: Long get() = simulation.cash
    val totalCashEarned: Long get() = simulation.totalCashEarned
    @Volatile var roundOver: Boolean = false
    val totalEnemiesKilled: Int get() = simulation.totalEnemiesKilled
    val totalStepsEarned: Long get() = simulation.totalStepsEarned
    val elapsedTimeSeconds: Float get() = simulation.elapsedSeconds
    @Volatile var secondWindHpPercent: Double = 0.0
    @Volatile var secondWindUsed: Boolean = false
    @Volatile var cashBonusPercent: Double = 0.0

    /**
     * CASH_RESEARCH lab multiplier (RO-11 #A.2). Applied to every kill-cash and wave-end-cash
     * computation as the outermost multiplier. Default `1.0` means "no CASH_RESEARCH".
     * Computed by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel] as
     * `1.0 + level × 0.05` (max L20 → 2.0×) and pushed onto the engine in
     * `init` and `playAgain`. Pre-RO-11 the CASH_RESEARCH enum was dead.
     */
    @Volatile var cashResearchMultiplier: Double = 1.0

    /**
     * UW_COOLDOWN lab multiplier (RO-11 #A.2). Applied at the [activateUW] cooldown-set
     * site so the active cooldown reflects the player's research level. Default `1f` means
     * "no UW_COOLDOWN research". Computed by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel]
     * as `(1f - level × 0.03f).coerceAtLeast(0.10f)` (max L15 → 0.55×; defensive floor at
     * 0.10× future-proofs any future level cap extension). Pre-RO-11 the UW_COOLDOWN enum
     * was dead.
     *
     * The UI's cooldown-ring fill in [com.whitefang.stepsofbabylon.presentation.battle.BattleUiState.UWSlotInfo]
     * is recomputed from `cooldownAtLevel × uwCooldownMultiplier` so the visual progress
     * stays in sync with the actual cooldown duration.
     */
    @Volatile var uwCooldownMultiplier: Float = 1f

    /**
     * ENEMY_INTEL lab research level (V1X-15b, ADR-0017). Drives three information overlays:
     * L1+ next-wave composition in the cooldown banner ([nextWaveCompositionLabel]), L5+ a
     * per-enemy HP-% label drawn above each enemy's HP bar in [render], and L10 a boss-arrival
     * countdown ([bossCountdownLabel]) drawn in [render]. Default `0` = no overlays (preserves
     * pre-V1X-15b rendering). Set by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel]
     * from the round-start lab snapshot; NOT reset in [init] (mirrors [cashResearchMultiplier]
     * — the VM owns the value and re-pushes it each round). The +2 %/lvl damage half of
     * ENEMY_INTEL is applied separately via `ResolveStats` (PR #84 combat foundation).
     */
    @Volatile var enemyIntelLevel: Int = 0

    /**
     * GOLDEN_ZIGGURAT cash buff multiplier. Written by [activateUW] (set to 5.0×) and reset
     * to 1.0× by the GOLDEN expiry branch in [updateUWs]. Pre-R4-01 this multiplier was
     * shared between Step Overdrive (FORTUNE, 3.0×) and the Ultimate Weapon (GOLDEN, 5.0×)
     * with a coerceAtLeast lifecycle that preserved the higher of the two across
     * activation/expiry interactions; R4-01 deletes Step Overdrive entirely so GOLDEN is
     * the sole writer.
     */
    private var fortuneMultiplier: Double = 1.0

    /**
     * Recovery Packages periodic-heal timer (RO-08). Accumulates during the SPAWNING wave
     * phase only (not during cooldown — heals between waves with no enemies on screen would
     * feel disconnected from the upgrade's "during waves" framing). Fires when the timer
     * crosses [SimulationMath.RECOVERY_INTERVAL_SECONDS]; reset to zero on each fire and on round init.
     */
    private var recoveryTimer: Float = 0f

    /**
     * Rapid Fire timer (R4-03). Accumulates during the SPAWNING wave phase only — mirrors
     * the [recoveryTimer] / [tickRecoveryPackages] pattern, including the "reset to zero
     * during cooldown phase" behaviour so a player who survives a wave doesn't carry over
     * a partial cooldown into the next wave's first 0–60 s window. Fires when the timer
     * crosses [RapidFireSchedule.interval] for the player's RAPID_FIRE level. Reset to
     * zero on each fire and on round init.
     */
    private var rapidFireTimer: Float = 0f

    /**
     * Rapid Fire active-burst countdown (R4-03). When [rapidFireTimer] crosses interval,
     * this is set to [RapidFireSchedule.duration] for the level and the ziggurat's
     * [com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity.rapidFireMultiplier]
     * is set to the level's multiplier; both reset when the countdown reaches zero. At
     * L10 duration == interval (30 s) so the next burst fires before the previous one
     * expires — effectively a permanent +3.0× attack-speed buff.
     */
    private var rapidFireActiveRemaining: Float = 0f

    /**
     * LIFESTEAL fractional-heal accumulator (R3-02 / GitHub issue #4). Each projectile-hit
     * and orb-hit applies the mathematically-correct heal directly to `zig.currentHp` (a
     * `Double`, so sub-1-HP heals are conserved); this counter accumulates the same
     * fractional amount in parallel and emits a `+X HP` floating-text indicator each time
     * the accumulated amount crosses an integer HP threshold. Pre-R3-02 LIFESTEAL was
     * mathematically correct but visually invisible at low levels (Lv 1 = 0.2 % lifesteal
     * × base damage 10 → 0.02 HP per shot — a sub-pixel HP-bar nudge users could not
     * perceive). Reset to 0.0 in [init] so accumulator state never leaks across rounds.
     */
    private var lifestealAccumulator: Double = 0.0

    /**
     * One-shot side-effect events from the in-round simulation (V1X-09 Phase 3 final slice,
     * ADR-0012). Replaces the pre-Phase-3 `@Volatile onStepReward` / `onBossKilled` callback
     * fields: the game loop now [Simulation.emit]s [SimulationEvent.StepReward] /
     * [SimulationEvent.BossKilled] from [handleEnemyDeath], and `BattleViewModel` collects
     * this stream instead of installing nullable lambdas. `replay = 0` (see [Simulation]) so a
     * collector started for a replayed round never re-sees the previous round's events.
     */
    val events: SharedFlow<SimulationEvent> get() = simulation.events

    /**
     * Returns `true` if this round has made observable progress — at least one enemy killed
     * or any game-loop ticks elapsed. Used by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel.onCleared]
     * to decide whether a mid-nav teardown should persist the in-flight round (RO-03 B.3 PR 2).
     *
     * A fresh battle screen that the user backs out of immediately — before any wave ticks —
     * returns `false` and skips persistence. Once the game loop has ticked at least once
     * ([elapsedTimeSeconds] > 0) the round is considered "in progress" and deserves to be
     * committed even if navigation cancels the VM. Safe to call from any thread — reads
     * `@Volatile` fields only.
     */
    fun hasWaveProgress(): Boolean = simulation.hasWaveProgress()

    /**
     * Equipped-cosmetic override map, indexed by category. Read by [init] when constructing
     * entities whose visuals can be overridden by a cosmetic (currently only [ZigguratEntity]
     * layer colors, via [CosmeticCategory.ZIGGURAT_SKIN]). Default `emptyMap()` means "use
     * the biome theme" — no behaviour change when no cosmetic is equipped (RO-07 C.2 PR 1).
     *
     * Populated by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel] after
     * loading the player's equipped-cosmetic set from [com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository].
     * Assignment should happen BEFORE [init] runs; the ViewModel hydrates in both
     * `startPollingEngine` (early) and the init-launch completion path (later), so whichever
     * fires last wins — the subsequent [init] call reads the up-to-date map.
     */
    @Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()



    /**
     * R4-06: per-UW state with three independent path levels (see [UWPath]). Pre-R4-06
     * this held a single `level: Int`; the engine now reads each path explicitly via
     * [damageLevel] / [secondaryLevel] / [cooldownLevel] and computes the per-path
     * effect via [UltimateWeaponType.valueAtLevel]. The active CHRONO_FIELD slow factor
     * is captured in [chronoSlowFactor] at activation time so a re-equip / level-up
     * mid-effect can't change the slow strength on an already-firing UW.
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
    private var chronoActive = false
    /**
     * Active CHRONO_FIELD slow factor. Set to the firing UW's [UltimateWeaponType.damageAtLevel]
     * value at activation; reset to 1.0 when [chronoActive] flips back to false. Replaces
     * the pre-R4-06 [CHRONO_SLOW_FACTOR] companion constant (which was the single value
     * the slow path produced regardless of UW level). Smaller value = stronger slow.
     */
    private var chronoSlowFactor: Float = 1f
    private var goldenZigActive = false
    private var preGoldenStats: ResolvedStats? = null

    companion object {
        const val BASE_CASH_PER_WAVE = 20L
        const val FLAT_BONUS_PER_WAVE_LEVEL = 5L
        // Recovery Packages constants moved to domain/battle/engine/SimulationMath
        // as part of V1X-09 Phase 1 simulation extraction (ADR-0012).
    }

    fun init(
        width: Float, height: Float,
        resolvedStats: ResolvedStats = ResolvedStats(),
        playerTier: Int = 1,
        wsLevels: Map<UpgradeType, Int> = emptyMap(),
        isReducedMotion: Boolean = false,
        /**
         * Wave number the round opens on (RO-11 #B.1, WAVE_SKIP lab research). Default `1`
         * preserves the pre-RO-11 "start at wave 1" behaviour for every call site that
         * doesn't yet thread the value through (workshop preview, fresh-engine tests).
         * [BattleViewModel] passes `1 + WAVE_SKIP_level` so L0 = wave 1 (current behaviour),
         * L10 = wave 11 (max research level). Floor of 1 is enforced via `coerceAtLeast`
         * before the value reaches [WaveSpawner] and [triggerWaveAnnouncement].
         */
        startWave: Int = 1,
    ) {
        screenWidth = width; screenHeight = height
        entities.clear(); pendingAdd.clear()
        simulation.reset(); roundOver = false
        fortuneMultiplier = 1.0
        secondWindUsed = false
        uwStates.clear(); chronoActive = false; chronoSlowFactor = 1f; goldenZigActive = false; preGoldenStats = null
        recoveryTimer = 0f
        rapidFireTimer = 0f
        rapidFireActiveRemaining = 0f
        lifestealAccumulator = 0.0
        stats = resolvedStats; tier = playerTier; effectiveLevels = wsLevels
        reducedMotion = isReducedMotion
        conditions = BattleConditionEffects.fromTier(tier)
        biomeTheme = BiomeTheme.forBiome(Biome.forTier(tier))
        backgroundRenderer = BackgroundRenderer(width, height, biomeTheme)

        // Initialize effect engine
        val fx = EffectEngine(reducedMotion)
        effectEngine = fx
        lastWave = 0

        val zigColors = cosmeticOverrides[CosmeticCategory.ZIGGURAT_SKIN]?.overrideColors
            ?: biomeTheme.zigguratColors
        val zig = ZigguratEntity(width, height, stats, ::findNearestEnemies, layerColors = zigColors) { sx, sy, tx, ty ->
            pendingAdd.add(ProjectileEntity(sx, sy, tx, ty, ZigguratBaseStats.PROJECTILE_SPEED, bouncesRemaining = stats.bounceCount))
            val intervalMs = ((1.0 / (ziggurat?.stats?.attackSpeed ?: 1.0)) * 1000).toLong()
            soundManager?.play(SoundEffect.SHOOT, intervalMs)
        }
        ziggurat = zig
        entities.add(zig)

        spawnOrbs()

        val safeStartWave = startWave.coerceAtLeast(1)

        waveSpawner = WaveSpawner(
            onSpawnEnemy = { pendingAdd.add(it) },
            zigguratX = zig.originX, zigguratY = zig.originY,
            onEnemyDeath = ::handleEnemyDeath,
            // R3-02: forward the attacker reference so applyThorn can reflect damage back
            // to the melee enemy. Pre-R3-02 this was `{ dmg -> ... null }` and THORN_DAMAGE
            // never fired on melee hits despite being plumbed through ResolvedStats.
            onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
            onEnemyFireProjectile = { shooter, sx, sy, tx, ty, dmg ->
                pendingAdd.add(EnemyProjectileEntity(sx, sy, tx, ty, damage = dmg, shooter = shooter))
            },
            onWaveComplete = ::handleWaveComplete,
            conditions = conditions,
            enemyTint = biomeTheme.enemyTint,
            startWave = safeStartWave,
            tierMultiplier = TierConfig.forTier(tier).cashMultiplier,
        )

        // Initial wave announcement
        triggerWaveAnnouncement(safeStartWave)
    }

    fun setStats(resolvedStats: ResolvedStats) { applyStats(resolvedStats) }

    fun updateZigguratStats(newStats: ResolvedStats) { applyStats(newStats) }

    /**
     * Single mutation point for [stats]. Replaces any direct `stats = …` assignment so
     * the same write is mirrored onto the [ZigguratEntity] (`zig.updateStats`) and the
     * derived per-frame state (orb spawn, max-HP rebalance) stays in lock-step (RO-08).
     *
     * Pre-RO-08 the engine kept its own `stats` field and the ziggurat held a separate
     * reference captured at construction; Overdrive ASSAULT's 2× attack speed and FORTRESS's
     * 2× health regen were silently dropped because the entity's reference never updated.
     * Centralising the mutation here is the cheapest possible fix and keeps every future
     * stat-modifying mechanic correctly propagated by default.
     *
     * Side effects:
     * - Updates engine `stats` to [newStats].
     * - Pushes `newStats` onto the ziggurat entity so per-tick reads see the new values.
     * - Rebalances `zig.currentHp` proportionally if `maxHealth` changed (preserves HP %).
     * - Re-spawns orbs if `orbCount` changed (entity-level reconciliation).
     */
    private fun applyStats(newStats: ResolvedStats) {
        val oldStats = stats
        stats = newStats
        val zig = ziggurat ?: return
        zig.updateStats(newStats)
        if (newStats.maxHealth != oldStats.maxHealth) {
            val hpRatio = if (zig.maxHp > 0) zig.currentHp / zig.maxHp else 1.0
            zig.maxHp = newStats.maxHealth
            zig.currentHp = newStats.maxHealth * hpRatio
        }
        if (newStats.orbCount != oldStats.orbCount) {
            entities.removeAll { it is OrbEntity }
            spawnOrbs()
        }
    }

    fun spendCash(amount: Long): Boolean = simulation.spend(amount)

    fun update(deltaTime: Float) {
        if (roundOver) return
        val zig = ziggurat ?: return
        simulation.tickElapsed(deltaTime)
        backgroundRenderer?.update(deltaTime)
        effectEngine?.update(deltaTime)

        updateUWs(deltaTime)
        tickRecoveryPackages(deltaTime)
        tickRapidFire(deltaTime)

        // Check for new wave to trigger announcement
        val currentWave = waveSpawner?.currentWave ?: 1
        if (currentWave != lastWave) {
            lastWave = currentWave
            triggerWaveAnnouncement(currentWave)
        }

        waveSpawner?.update(deltaTime, screenWidth, screenHeight)
        entities.addAll(pendingAdd); pendingAdd.clear()
        // RO-09 #1 / R4-06 / V1X-09 Phase 3: tick every entity, slowing chrono-slowable
        // entities (enemies) to [chronoSlowFactor] while CHRONO_FIELD is active. The loop
        // now lives in the pure-domain [Simulation]; the engine just supplies the active
        // slow factor (1f when inactive). Projectiles, orbs, and the ziggurat tick at full
        // `deltaTime` because they report `isChronoSlowable = false`.
        simulation.tickEntities(entities, deltaTime, if (chronoActive) chronoSlowFactor else 1f)

        // Spawn projectile trails
        if (!reducedMotion) {
            val fx = effectEngine
            if (fx != null) {
                for (e in entities) {
                    if (e is ProjectileEntity && e.isAlive) {
                        ProjectileTrailEffect.spawn(fx.pool, e.x, e.y, biomeTheme.particleColor)
                    }
                }
            }
        }

        CollisionSystem.checkCollisions(
            simulation,
            entities, zig.x, zig.y, zig.width,
            onProjectileHitEnemy = ::onProjectileHitEnemy,
            onEnemyProjectileHitZiggurat = { proj ->
                applyDamageToZiggurat(proj.damage, proj.shooter)
                proj.isAlive = false
            },
        )
        entities.removeAll { !it.isAlive }
        if (zig.currentHp <= 0.0) {
            roundOver = true
            soundManager?.play(SoundEffect.ROUND_END)
        }
    }

    fun render(canvas: Canvas) {
        backgroundRenderer?.render(canvas) ?: canvas.drawColor(0xFF6B3A2A.toInt())

        val fx = effectEngine
        // Screen shake
        if (fx != null && !reducedMotion) fx.screenShake.apply(canvas)

        entities.forEach { it.render(canvas) }

        // Render effects (particles, floating text, UW visuals, auras, announcements)
        fx?.render(canvas)

        // Chrono field overlay
        if (chronoActive) {
            val p = android.graphics.Paint().apply { color = 0x222196F3; style = android.graphics.Paint.Style.FILL }
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, p)
        }

        if (fx != null && !reducedMotion) fx.screenShake.restore(canvas)

        // V1X-15b: ENEMY_INTEL L5+ per-enemy HP-% label above each enemy's HP bar. Drawn here
        // (not in EnemyEntity.render) so the level gate stays out of the entity constructor.
        if (enemyIntelLevel >= 5) {
            for (e in entities) {
                if (e is EnemyEntity && e.isAlive) {
                    val pct = ((e.currentHp / e.maxHp).coerceIn(0.0, 1.0) * 100).toInt()
                    canvas.drawText("$pct%", e.x, e.y - e.height / 2f - 14f, hpPercentPaint)
                }
            }
        }

        ziggurat?.let { healthBarRenderer.render(canvas, it.currentHp, it.maxHp, screenWidth) }

        // V1X-15b: ENEMY_INTEL L10 boss-arrival countdown, right-aligned to clear the
        // centre-aligned cooldown banner.
        bossCountdownLabel()?.let { canvas.drawText(it, screenWidth - 16f, 90f, bossCountdownPaint) }
    }

    private val hpPercentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8E7.toInt(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER
    }
    private val bossCountdownPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF44336.toInt(); textSize = 26f; textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true
    }

    fun addEntity(entity: Entity) { pendingAdd.add(entity) }

    /**
     * R4-06: populates [uwStates] from the player's equipped UWs, mapping each
     * [OwnedWeapon]'s 3 path levels onto a [UWState] and zeroing the active timers.
     * Called by [BattleViewModel] after loading the equipped-UW set from the repository.
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

    /**
     * R4-06: fires the UW at [index] if it's off cooldown and not already mid-effect.
     * Pre-R4-06 this was the entry point for the player-tap activation path; post-R4-06
     * the UW is auto-fired from [updateUWs] when its cooldown reaches 0 AND enemies are
     * present (`UltimateWeaponBar` is now a passive cooldown indicator). The method
     * stays public for direct invocation by tests.
     *
     * Per-path reads (replaces the pre-R4-06 single-`level` reads):
     * - DEATH_WAVE: damage from DAMAGE path; SECONDARY (radius fraction) feeds visual but
     *   damage applies to all aliveEnemies for v1 of R4-06 (radius UI deferred).
     * - CHAIN_LIGHTNING: damage from DAMAGE path; chain length from SECONDARY path.
     * - BLACK_HOLE: ongoing-effect handled in [updateUWs]; activation just primes the
     *   timer + visual.
     * - CHRONO_FIELD: slow factor from DAMAGE path written to [chronoSlowFactor];
     *   duration from SECONDARY path overrides [UltimateWeaponType.effectDurationSeconds].
     * - POISON_SWAMP: ongoing-effect handled in [updateUWs].
     * - GOLDEN_ZIGGURAT: cash multiplier from DAMAGE path → [fortuneMultiplier]
     *   (`coerceAtLeast` preserves any pre-existing higher buff, defensive for future
     *   multi-source stacking); damage multiplier from SECONDARY path → stats copy.
     */
    fun activateUW(index: Int) {
        val uw = uwStates.getOrNull(index) ?: return
        if (uw.cooldownRemaining > 0f) return
        if (uw.effectTimeRemaining > 0f) return
        // R4-06: cooldown comes from per-UW-state COOLDOWN path. RO-11 #A.2 outer multiplier
        // (UW_COOLDOWN lab research) still stacks via [uwCooldownMultiplier].
        uw.cooldownRemaining = uw.type.cooldownAtLevel(uw.cooldownLevel) * uwCooldownMultiplier
        val zig = ziggurat ?: return

        // CHRONO_FIELD overrides the flat [effectDurationSeconds] with the SECONDARY-path
        // value; all other UWs use the flat constant.
        val duration = if (uw.type == UltimateWeaponType.CHRONO_FIELD) {
            uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
        } else {
            uw.type.effectDurationSeconds
        }
        if (duration > 0f) uw.effectTimeRemaining = duration

        soundManager?.play(SoundEffect.UW_ACTIVATE)

        // Spawn particle-based UW visual effect
        val fx = effectEngine
        if (fx != null) {
            val effectDuration = when {
                uw.type == UltimateWeaponType.DEATH_WAVE -> 1.2f
                duration > 0f -> duration
                else -> 0.5f
            }
            val cx: Float; val cy: Float
            when (uw.type) {
                UltimateWeaponType.BLACK_HOLE -> { cx = screenWidth / 2f; cy = screenHeight * 0.35f }
                UltimateWeaponType.POISON_SWAMP -> { cx = screenWidth / 2f; cy = screenHeight * 0.6f }
                else -> { cx = zig.x; cy = zig.originY }
            }
            fx.addEffect(UWVisualEffect(uw.type, fx.pool, cx, cy, screenWidth, screenHeight, effectDuration, reducedMotion))
        }

        // Screen shake for Death Wave
        if (uw.type == UltimateWeaponType.DEATH_WAVE && !reducedMotion) {
            fx?.screenShake?.trigger(12f, 0.4f)
        }

        when (uw.type) {
            UltimateWeaponType.DEATH_WAVE -> {
                val dmg = uw.type.damageAtLevel(uw.damageLevel)
                getAliveEnemies().forEach { it.takeDamage(dmg) }
            }
            UltimateWeaponType.CHAIN_LIGHTNING -> {
                val dmg = uw.type.damageAtLevel(uw.damageLevel)
                val chainLen = uw.type.secondaryAtLevel(uw.secondaryLevel).toInt().coerceAtLeast(1)
                val targets = getAliveEnemies().sortedBy { hypot(it.x - zig.x, it.y - zig.y) }.take(chainLen)
                targets.forEach { it.takeDamage(dmg) }
            }
            UltimateWeaponType.BLACK_HOLE -> {} // Ongoing effect in updateUWs
            UltimateWeaponType.CHRONO_FIELD -> {
                chronoActive = true
                chronoSlowFactor = uw.type.damageAtLevel(uw.damageLevel).toFloat()
            }
            UltimateWeaponType.POISON_SWAMP -> {} // Ongoing effect in updateUWs
            UltimateWeaponType.GOLDEN_ZIGGURAT -> {
                goldenZigActive = true; preGoldenStats = stats
                // R4-06: cash multiplier comes from DAMAGE path. coerceAtLeast preserves any
                // pre-existing higher fortuneMultiplier value (defensive for future
                // multi-source stacking; R4-01 already removed Step Overdrive as the only
                // other writer, so in practice this only matters across overlapping GOLDEN
                // activations — prevented by cooldown gating but harmless).
                val cashMult = uw.type.damageAtLevel(uw.damageLevel)
                fortuneMultiplier = fortuneMultiplier.coerceAtLeast(cashMult)
                // Damage multiplier comes from SECONDARY path (replaces hard-coded 1.5×).
                val dmgMult = uw.type.secondaryAtLevel(uw.secondaryLevel)
                applyStats(stats.copy(damage = stats.damage * dmgMult))
            }
        }
    }

    private fun updateUWs(deltaTime: Float) {
        val zig = ziggurat ?: return
        for (uw in uwStates) {
            // V1X-09 Phase 3 (ADR-0012): the pure cooldown + effect-duration timer arithmetic
            // lives in the domain [Simulation]. The engine applies the result and runs the
            // presentation-coupled side-effects (expiry stat/flag writes, ongoing enemy
            // damage). Behaviour is identical to the pre-extraction inline block — the ongoing
            // `when` still runs on the tick the effect expires because it is gated on
            // [Simulation.UWTimerAdvance.effectWasActive] (the old `effectTimeRemaining > 0f` guard).
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
                            goldenZigActive = false; preGoldenStats?.let { applyStats(it) }; preGoldenStats = null
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
                        val cx = screenWidth / 2f; val cy = screenHeight * 0.35f
                        // R4-06: damage DPS from DAMAGE path; pull strength from SECONDARY path.
                        val dps = uw.type.damageAtLevel(uw.damageLevel)
                        val pull = uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
                        getAliveEnemies().forEach { e ->
                            val dx = cx - e.x; val dy = cy - e.y; val d = hypot(dx, dy).coerceAtLeast(1f)
                            e.x += dx / d * pull * deltaTime; e.y += dy / d * pull * deltaTime
                            e.takeDamage(dps * deltaTime)
                        }
                    }
                    UltimateWeaponType.POISON_SWAMP -> {
                        // R4-06: DoT % MaxHP/sec from DAMAGE path. Area path (SECONDARY) is
                        // captured for visual / future filtering but every alive enemy is
                        // hit in v1 of R4-06.
                        val dotFrac = uw.type.damageAtLevel(uw.damageLevel)
                        getAliveEnemies().forEach { e -> e.takeDamage(e.maxHp * dotFrac * deltaTime) }
                    }
                    else -> {}
                }
            }
        }

        // R4-06: auto-trigger. Fire any UW whose cooldown has reached 0 AND that's not
        // currently mid-effect, but only when at least one enemy is alive on screen —
        // empty-screen fires would burn cooldowns during wave-cooldown breaks for no
        // benefit. Pre-R4-06 activation came from a player tap on `UltimateWeaponBar`;
        // post-R4-06 the bar is a passive indicator and this loop is the sole activator.
        if (uwStates.isNotEmpty() && getAliveEnemies().isNotEmpty()) {
            for (i in uwStates.indices) {
                val uw = uwStates[i]
                if (simulation.isUWReadyToFire(uw.cooldownRemaining, uw.effectTimeRemaining)) {
                    activateUW(i)
                }
            }
        }
    }

    fun resetUWCooldowns() { uwStates.forEach { it.cooldownRemaining = 0f } }

    // --- Wave announcements ---

    private fun triggerWaveAnnouncement(wave: Int) {
        val fx = effectEngine ?: return
        val isBoss = wave % conditions.bossWaveInterval == 0 && wave > 0
        fx.addEffect(WaveAnnouncement(wave, isBoss, screenWidth, screenHeight, reducedMotion))
        soundManager?.play(SoundEffect.WAVE_START)

        // Add cooldown text for next wave
        cooldownText = null // Old one auto-finishes
        val spawner = waveSpawner
        if (spawner != null) {
            // V1X-15b: ENEMY_INTEL L1+ reveals the next wave's composition during cooldown.
            val ct = WaveCooldownText(screenWidth, nextWaveCompositionLabel()) {
                if (spawner.phase == WavePhase.COOLDOWN) WaveSpawner.COOLDOWN_DURATION - (spawner.phaseTimer)
                else 0f
            }
            cooldownText = ct
            fx.addEffect(ct)
        }
    }

    /**
     * Next-wave composition string for the ENEMY_INTEL L1+ overlay (V1X-15b). Returns `null`
     * when ENEMY_INTEL is below L1 (overlay suppressed) or no spawner exists. Reads the pure
     * [WaveSpawner.getWaveComposition] helper for `currentWave + 1`, e.g. "Next: 12 BASIC, 4 RANGED, 1 BOSS".
     */
    fun nextWaveCompositionLabel(): String? {
        if (enemyIntelLevel < 1) return null
        val spawner = waveSpawner ?: return null
        val comp = spawner.getWaveComposition(spawner.currentWave + 1)
        if (comp.isEmpty()) return null
        return "Next: " + comp.entries.joinToString(", ") { "${it.value} ${it.key.name}" }
    }

    /**
     * Boss-arrival countdown string for the ENEMY_INTEL L10 overlay (V1X-15b). Returns `null`
     * below L10 or with no spawner. Combines [WaveSpawner.wavesUntilNextBoss] with the current
     * phase timer to estimate seconds, e.g. "Boss in 2 waves".
     */
    fun bossCountdownLabel(): String? {
        if (enemyIntelLevel < 10) return null
        val spawner = waveSpawner ?: return null
        val waves = spawner.wavesUntilNextBoss()
        return if (waves == 1) "Boss next wave" else "Boss in $waves waves"
    }

    // --- Orb management ---

    private fun spawnOrbs() {
        val zig = ziggurat ?: return
        val count = stats.orbCount
        if (count <= 0) return
        // #54: orbit radius is now controlled internally by [OrbEntity] via radial
        // oscillation between [OrbEntity.ORBIT_RADIUS_MIN] and [OrbEntity.ORBIT_RADIUS_MAX]
        // so orbs actually sweep through the enemy melee zone. Pre-fix the radius was
        // `stats.range * 0.4f` ≈ 120 px which placed orbs entirely outside the kill zone.
        val damage = stats.damage * 0.5 * conditions.orbDamageMultiplier
        for (i in 0 until count) {
            val angle = (2.0 * PI / count * i).toFloat()
            entities.add(OrbEntity(
                zigX = zig.originX, zigY = zig.originY,
                angle = angle, damage = damage,
                getEnemies = ::getAliveEnemies,
                onHitEnemy = ::onOrbHitEnemy,
            ))
        }
    }

    private fun getAliveEnemies(): List<EnemyEntity> =
        entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }

    private fun onOrbHitEnemy(enemy: EnemyEntity, damage: Double) {
        enemy.takeDamage(damage)
        val zig = ziggurat ?: return
        if (stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX; val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * 0.5f * conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (stats.lifestealPercent > 0) {
            applyLifesteal(damage * stats.lifestealPercent)
        }
    }

    // --- Cash economy ---

    private fun wsLevel(type: UpgradeType): Int = effectiveLevels[type] ?: 0

    /**
     * Replaces the engine's effective cash-utility level map. Called by
     * [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel] after every
     * in-round upgrade purchase with `workshopLevels + inRoundLevels` summed per type, so
     * `CASH_BONUS` / `CASH_PER_WAVE` / `INTEREST` / `FREE_UPGRADES` purchases made mid-round
     * affect subsequent kill rewards, wave-end bonuses, interest payouts, and free-upgrade
     * rolls (RO-08).
     *
     * No-op for stat-bearing upgrades — those propagate through [applyStats] /
     * [updateZigguratStats] which are the canonical stats-mutation channels.
     */
    fun updateEffectiveLevels(combined: Map<UpgradeType, Int>) {
        effectiveLevels = combined
    }

    /**
     * RECOVERY_PACKAGES periodic-heal pulse (RO-08). Runs every game-loop tick during the
     * SPAWNING phase only; resets the accumulated timer between waves so the first pulse of
     * a new wave waits a full [RECOVERY_INTERVAL_SECONDS]. Pulses are no-ops at full HP, but
     * the timer still advances so HP that drops mid-wave doesn't immediately trigger a fresh
     * pulse from a stale timer.
     *
     * Heal amount: `min(level × 1 %, 50 %)` of `maxHp` per pulse, capped to prevent godmode
     * at high levels. Floors at 1 HP healed (so any non-zero level produces visible feedback).
     * Spawns a green floating-text indicator above the ziggurat for visual feedback.
     */
    private fun tickRecoveryPackages(deltaTime: Float) {
        val zig = ziggurat ?: return
        val spawner = waveSpawner ?: return
        if (spawner.phase != WavePhase.SPAWNING) {
            recoveryTimer = 0f
            return
        }
        val level = wsLevel(UpgradeType.RECOVERY_PACKAGES)
        if (level <= 0) return
        recoveryTimer += deltaTime
        if (recoveryTimer < SimulationMath.RECOVERY_INTERVAL_SECONDS) return
        recoveryTimer = 0f
        if (zig.currentHp >= zig.maxHp) return
        val healAmount = SimulationMath.recoveryPulseAmount(level, zig.maxHp)
        zig.currentHp = SimulationMath.clampHp(zig.currentHp + healAmount, zig.maxHp)
        effectEngine?.addEffect(
            FloatingText(
                x = zig.x,
                y = zig.originY - 30f,
                text = strings?.healHp(healAmount.toInt()) ?: "+${healAmount.toInt()} HP",
                color = FloatingText.STEP_COLOR,
            ),
        )
    }

    /**
     * Rapid Fire periodic-burst tick (R4-03). Mirrors [tickRecoveryPackages]: only fires
     * during the SPAWNING wave phase; resets state on cooldown phase so a partial timer
     * doesn't leak across the wave boundary; level 0 (no purchase) is a no-op. Reads the
     * player's RAPID_FIRE level via [wsLevel] (which the engine seeds in [init] and
     * keeps in sync via [updateEffectiveLevels] for in-round purchases of cash utilities;
     * RAPID_FIRE is purchased only via the Workshop / DAO, so the level here is whatever
     * was loaded at battle start).
     *
     * State machine each tick:
     *  1. If a burst is active, decrement [rapidFireActiveRemaining] by `deltaTime`. When
     *     it reaches zero, reset the ziggurat's [com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity.rapidFireMultiplier]
     *     to `1f` so attack-speed reads return to baseline.
     *  2. Increment [rapidFireTimer] by `deltaTime`. When it crosses the level's interval,
     *     reset the timer, kick off a new burst (set the ziggurat multiplier and arm the
     *     active countdown), and emit a yellow-gold `"RAPID FIRE!"` floating text above
     *     the ziggurat for visual feedback.
     *
     * Order matters: the active-decrement step runs *before* the timer-fire step so that
     * at L10 (interval == duration) the multiplier transitions from one burst to the next
     * within a single tick, with no observable 1-frame gap of `1f`.
     */
    private fun tickRapidFire(deltaTime: Float) {
        val zig = ziggurat ?: return
        val spawner = waveSpawner ?: return
        if (spawner.phase != WavePhase.SPAWNING) {
            // Wave-cooldown reset matches RECOVERY_PACKAGES semantics: a survived wave
            // shouldn't grant a head-start on the next wave's first burst.
            rapidFireTimer = 0f
            rapidFireActiveRemaining = 0f
            zig.rapidFireMultiplier = 1f
            return
        }
        val level = wsLevel(UpgradeType.RAPID_FIRE)
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
            effectEngine?.addEffect(
                FloatingText(
                    x = zig.x,
                    y = zig.originY - 30f,
                    text = strings?.rapidFireBurst() ?: "RAPID FIRE!",
                    color = FloatingText.DEFAULT_COLOR,
                ),
            )
        }
    }

    private fun handleWaveComplete(wave: Int) {
        // RO-11 #A.2: CASH_RESEARCH multiplies the wave-end cash payout.
        val waveCash = ((BASE_CASH_PER_WAVE + wsLevel(UpgradeType.CASH_PER_WAVE) * FLAT_BONUS_PER_WAVE_LEVEL) *
            fortuneMultiplier * cashResearchMultiplier).toLong()
        simulation.creditCash(waveCash)
        simulation.applyInterest(wsLevel(UpgradeType.INTEREST))
    }

    // --- Combat mechanics ---

    private fun onProjectileHitEnemy(proj: ProjectileEntity, enemy: EnemyEntity) {
        val zig = ziggurat ?: return
        val dist = hypot(zig.originX - enemy.x, zig.originY - enemy.y)
        val result = calculateDamage(stats, dist)

        enemy.takeDamage(result.amount)
        proj.hitEnemies.add(enemy)
        proj.isAlive = false

        soundManager?.play(SoundEffect.HIT)

        if (stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX; val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (stats.lifestealPercent > 0) {
            applyLifesteal(result.amount * stats.lifestealPercent)
        }

        // Bounce shot
        if (proj.bouncesRemaining > 0) {
            val nextTarget = entities.asSequence()
                .filterIsInstance<EnemyEntity>()
                .filter { it.isAlive && it !in proj.hitEnemies }
                .minByOrNull { hypot(it.x - enemy.x, it.y - enemy.y) }
            if (nextTarget != null) {
                pendingAdd.add(ProjectileEntity(
                    startX = enemy.x, startY = enemy.y,
                    targetX = nextTarget.x, targetY = nextTarget.y,
                    speed = ZigguratBaseStats.PROJECTILE_SPEED,
                    bouncesRemaining = proj.bouncesRemaining - 1,
                    hitEnemies = proj.hitEnemies,
                ))
            }
        }
    }

    private fun applyDamageToZiggurat(rawDamage: Double, attacker: EnemyEntity?) {
        val zig = ziggurat ?: return
        val mitigated = calculateDefense(rawDamage, stats)
        if (zig.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (Random.nextDouble() < stats.deathDefyChance) {
                zig.currentHp = 1.0; applyThorn(rawDamage, attacker); return
            }
        }
        if (zig.currentHp - mitigated <= 0.0 && !secondWindUsed && secondWindHpPercent > 0.0) {
            secondWindUsed = true
            zig.currentHp = zig.maxHp * secondWindHpPercent
            applyThorn(rawDamage, attacker); return
        }
        val prevHpRatio = zig.currentHp / zig.maxHp
        zig.currentHp = (zig.currentHp - mitigated).coerceAtLeast(0.0)
        val newHpRatio = zig.currentHp / zig.maxHp
        // Screen shake when HP drops below 25%
        if (prevHpRatio > 0.25 && newHpRatio <= 0.25 && !reducedMotion) {
            effectEngine?.screenShake?.trigger(5f, 0.2f)
        }
        applyThorn(rawDamage, attacker)
    }

    private fun applyThorn(rawDamage: Double, attacker: EnemyEntity?) {
        if (attacker == null || !attacker.isAlive) return
        val reflection = SimulationMath.thornReflectionDamage(
            rawDamage = rawDamage,
            thornPercent = stats.thornPercent,
            conditionMultiplier = conditions.thornMultiplier.toDouble(),
        )
        if (reflection > 0) attacker.takeDamage(reflection)
    }

    /**
     * Applies a lifesteal heal of [healAmount] HP to the ziggurat (R3-02). The HP delta is
     * added directly to `zig.currentHp` (a `Double`, so sub-1-HP heals are conserved). The
     * same fractional amount is accumulated in [lifestealAccumulator]; each time the
     * accumulator crosses an integer HP threshold a `+X HP` `FloatingText` indicator is
     * spawned above the ziggurat so the player gets visible feedback on heals that would
     * otherwise be sub-pixel HP-bar movement.
     *
     * The math is identical to the pre-R3-02 in-place heal at `onProjectileHitEnemy` and
     * `onOrbHitEnemy` — only the visible feedback is new. At cap (15 % lifesteal × base
     * damage 10 = 1.5 HP / hit) every shot emits a `+1 HP` indicator; at Lv 1 (0.2 % × 10
     * = 0.02 HP / hit) it takes ~50 hits to accumulate one visible burst, which matches
     * the expectation that low-level lifesteal is weak but still observable.
     */
    private fun applyLifesteal(healAmount: Double) {
        val zig = ziggurat ?: return
        zig.currentHp = SimulationMath.clampHp(zig.currentHp + healAmount, zig.maxHp)
        val tick = SimulationMath.tickLifestealAccumulator(lifestealAccumulator, healAmount)
        lifestealAccumulator = tick.newAccumulator
        if (tick.visibleHp > 0) {
            effectEngine?.addEffect(
                FloatingText(
                    x = zig.x,
                    y = zig.originY - 30f,
                    text = strings?.healHp(tick.visibleHp) ?: "+${tick.visibleHp} HP",
                    color = FloatingText.STEP_COLOR,
                ),
            )
        }
    }

    // --- Targeting & death ---

    private fun findNearestEnemies(n: Int): List<EnemyEntity> {
        val zig = ziggurat ?: return emptyList()
        return entities.asSequence()
            .filterIsInstance<EnemyEntity>()
            .filter { it.isAlive && hypot(it.x - zig.originX, it.y - zig.originY) <= zig.attackRange }
            .sortedBy { hypot(it.x - zig.originX, it.y - zig.originY) }
            .take(n)
            .toList()
    }

    private fun handleEnemyDeath(enemy: EnemyEntity) {
        simulation.recordEnemyKilled()
        val baseCash = EnemyScaler.cashReward(enemy.enemyType)
        val tierMult = TierConfig.forTier(tier).cashMultiplier
        val cashBonus = 1.0 + wsLevel(UpgradeType.CASH_BONUS) * 0.03
        // RO-11 #A.2: CASH_RESEARCH multiplies the per-kill cash. Stacks multiplicatively
        // with workshop CASH_BONUS, tier cash multiplier, GOLDEN_ZIGGURAT UW, and the
        // CASH_BONUS_GAIN card. Default 1.0× means "no CASH_RESEARCH research".
        val killCash = (baseCash * tierMult * cashBonus * fortuneMultiplier *
            (1.0 + cashBonusPercent / 100.0) * cashResearchMultiplier).toLong()
        simulation.creditCash(killCash)
        waveSpawner?.onEnemyKilled()

        soundManager?.play(SoundEffect.ENEMY_DEATH)

        // Death effect particles + floating cash text
        val fx = effectEngine
        if (fx != null) {
            if (!reducedMotion) DeathEffect.spawn(fx.pool, enemy.x, enemy.y, enemy.enemyType)
            fx.addEffect(FloatingText(enemy.x, enemy.y, strings?.cashReward(killCash) ?: "+$killCash"))
            // Boss death screen shake
            if (enemy.enemyType == EnemyType.BOSS && !reducedMotion) {
                fx.screenShake.trigger(8f, 0.3f)
            }
        }

        // Boss-kill Power Stone reward — tier-scaled, capped at 100/day. Emitted as a
        // SimulationEvent; BattleViewModel's collector awards the PS via AwardBossPowerStones.
        if (enemy.enemyType == EnemyType.BOSS) {
            simulation.emit(SimulationEvent.BossKilled(tier, enemy.x, enemy.y + 24f))
        }

        // Battle Step reward — flat per enemy type, independent of multipliers.
        // Actual wallet credit, cap enforcement, and floating-text spawn all
        // live in the collector (BattleViewModel) so a capped reward can be
        // silently dropped without a misleading "+N Step" indicator.
        val stepReward = EnemyScaler.stepReward(enemy.enemyType)
        if (stepReward > 0L) {
            simulation.creditSteps(stepReward)
            simulation.emit(SimulationEvent.StepReward(stepReward, enemy.x, enemy.y + 24f))
        }

        if (enemy.enemyType == EnemyType.SCATTER) {
            val zig = ziggurat ?: return
            val childCount = (2..3).random()
            repeat(childCount) { i ->
                val child = EnemyEntity(
                    enemyType = EnemyType.BASIC,
                    currentHp = enemy.maxHp * 0.5, maxHp = enemy.maxHp * 0.5,
                    speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * conditions.enemySpeedMultiplier,
                    damage = enemy.damage * 0.5,
                    targetX = zig.originX, targetY = zig.originY,
                    onDeath = ::handleEnemyDeath,
                    // R3-02: SCATTER child enemies also forward their attacker reference
                    // so THORN_DAMAGE reflects against them (same fix as the wave-spawner
                    // path — these melee-hit lambdas previously dropped the attacker).
                    onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
                ).apply {
                    x = enemy.x + (i - childCount / 2f) * 15f; y = enemy.y; initDistance()
                }
                pendingAdd.add(child)
            }
        }
    }
}
