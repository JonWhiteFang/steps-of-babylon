package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import com.whitefang.stepsofbabylon.presentation.audio.SoundEffect
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.biome.BackgroundRenderer
import com.whitefang.stepsofbabylon.presentation.battle.biome.BiomeTheme
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.effects.ProjectileTrailEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.WaveAnnouncement
import com.whitefang.stepsofbabylon.presentation.battle.effects.WaveCooldownText
import com.whitefang.stepsofbabylon.presentation.battle.effects.advanceTrail
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.OrbEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.PI
import kotlin.math.hypot

class GameEngine :
    UWHost,
    BuffHost,
    CombatHost {
    private val entities = mutableListOf<Entity>()
    private val pendingAdd = mutableListOf<Entity>()

    /**
     * Issue #118 (audit #1 + #15): `entities` is structurally mutated AND iterated from two
     * threads — the dedicated GameLoopThread runs [update] / [render] every tick, while the
     * UI/main thread can re-init the engine ([init], playAgain) or reconcile orbs after an
     * in-round ORBS purchase ([applyStats] via [updateZigguratStats]). A plain `ArrayList`
     * structurally modified by one thread while another iterates it throws
     * [java.util.ConcurrentModificationException] / corrupts iteration. Every region that
     * structurally mutates or iterates `entities` is guarded by this monitor so the two
     * threads can never collide. Java monitors are reentrant, so the loop-thread GOLDEN path
     * ([activateUW] → [applyStats]) re-entering the lock is safe. [render] snapshots under the
     * lock then draws outside it, to avoid holding the monitor across the Canvas draw window.
     */
    private val entitiesLock = Any()

    // A28 (audit): reusable per-tick scratch buffers for the collision partition, owned by THIS
    // engine instance (NOT the CollisionSystem object — it's a singleton; instance buffers avoid
    // cross-engine shared mutable state). Filled in one pass under [entitiesLock] each tick; never
    // retained across update() calls (so this is NOT the #125 cross-frame caching hazard).
    private val projScratch = ArrayList<ProjectileEntity>()
    private val enemyScratch = ArrayList<EnemyEntity>()
    private val enemyProjScratch = ArrayList<EnemyProjectileEntity>()

    override var screenWidth: Float = 0f
        private set
    override var screenHeight: Float = 0f
        private set
    override var ziggurat: ZigguratEntity? = null
        private set
    var waveSpawner: WaveSpawner? = null
        private set

    // @Volatile: written by [applyStats] from the main thread (in-round purchase) and read by
    // the loop thread every tick (#118).
    @Volatile private var stats: ResolvedStats = ResolvedStats()
    private var currentTier: Int = 1
    private var battleConditions: BattleConditionEffects = BattleConditionEffects()

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
    override var effectEngine: EffectEngine? = null
        private set
    override var soundManager: SoundManager? = null

    /** Engine-internal display strings (V1X-13, ADR-0014). Set by [GameSurfaceView]; null in pure-JVM tests, which fall back to literals. */
    override var strings: Strings? = null
    override var reducedMotion: Boolean = false
        private set
    private var lastWave: Int = 0

    /**
     * In-round cash economy, extracted to the pure-domain [Simulation] in V1X-09 Phase 3
     * (ADR-0012). The engine delegates its `cash` / `totalCashEarned` / `spendCash` public
     * surface here; callers (BattleViewModel polling, BattleScreen, tests) are unchanged.
     */
    override val simulation: Simulation = Simulation()

    @Suppress("LeakingThis")
    private val uwController = UWController(this, simulation)

    @Suppress("LeakingThis")
    private val buffTickers = BuffTickers(this)

    @Suppress("LeakingThis")
    private val combatResolver = CombatResolver(this)
    private val battleRenderer = BattleRenderer()
    val cash: Long get() = simulation.cash
    val totalCashEarned: Long get() = simulation.totalCashEarned

    @Volatile var roundOver: Boolean = false
    val totalEnemiesKilled: Int get() = simulation.totalEnemiesKilled
    val totalStepsEarned: Long get() = simulation.totalStepsEarned
    val elapsedTimeSeconds: Float get() = simulation.elapsedSeconds

    @Volatile override var secondWindHpPercent: Double = 0.0

    @Volatile var secondWindUsed: Boolean = false

    @Volatile override var cashBonusPercent: Double = 0.0

    /**
     * CASH_RESEARCH lab multiplier (RO-11 #A.2). Applied to every kill-cash and wave-end-cash
     * computation as the outermost multiplier. Default `1.0` means "no CASH_RESEARCH".
     * Computed by [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel] as
     * `1.0 + level × 0.05` (max L20 → 2.0×) and pushed onto the engine in
     * `init` and `playAgain`. Pre-RO-11 the CASH_RESEARCH enum was dead.
     */
    @Volatile override var cashResearchMultiplier: Double = 1.0

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
    @Volatile override var uwCooldownMultiplier: Float = 1f

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

    // --- Host interface members (UWHost / BuffHost / CombatHost, #230/#231) ---

    override val currentStats: ResolvedStats get() = stats
    override val conditions: BattleConditionEffects get() = battleConditions
    override val tier: Int get() = currentTier
    override val wavePhase: WavePhase? get() = waveSpawner?.phase
    override val fortuneMultiplier: Double get() = uwController.fortuneMultiplier

    override fun consumeSecondWind(): Boolean {
        if (secondWindUsed) return false
        secondWindUsed = true
        return true
    }

    override fun addPending(entity: Entity) {
        pendingAdd.add(entity)
    }

    override fun aliveEnemies(): List<EnemyEntity> = getAliveEnemies()

    override fun nearestEnemies(n: Int): List<EnemyEntity> = findNearestEnemies(n)

    override fun applyLifesteal(healAmount: Double) = buffTickers.applyLifesteal(healAmount)

    override fun wsLevel(type: UpgradeType): Int = effectiveLevels[type] ?: 0

    fun init(
        width: Float,
        height: Float,
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
        // #118: init() runs on the main thread (playAgain) and structurally rebuilds the
        // shared `entities` / `pendingAdd` / `uwStates` collections while the GameLoopThread
        // may still be running. Guard the whole rebuild with [entitiesLock] so a concurrent
        // [update] tick either completes before the rebuild starts or blocks until it finishes
        // — it can never iterate a half-cleared list. [update]'s `if (roundOver) return` guard
        // reads happen before its own lock acquisition, so a ticker simply waits here.
        synchronized(entitiesLock) {
            screenWidth = width
            screenHeight = height
            entities.clear()
            pendingAdd.clear()
            simulation.reset()
            roundOver = false
            secondWindUsed = false
            uwController.resetRoundState()
            buffTickers.reset()
            stats = resolvedStats
            currentTier = playerTier
            effectiveLevels = wsLevels
            reducedMotion = isReducedMotion
            battleConditions = BattleConditionEffects.fromTier(currentTier)
            biomeTheme = BiomeTheme.forBiome(Biome.forTier(currentTier))
            backgroundRenderer = BackgroundRenderer(width, height, biomeTheme)

            // Initialize effect engine
            val fx = EffectEngine(reducedMotion)
            effectEngine = fx

            val zigColors =
                cosmeticOverrides[CosmeticCategory.ZIGGURAT_SKIN]?.overrideColors
                    ?: biomeTheme.zigguratColors
            val zig =
                ZigguratEntity(width, height, stats, ::findNearestEnemies, layerColors = zigColors) { sx, sy, tx, ty ->
                    pendingAdd.add(
                        ProjectileEntity(
                            sx,
                            sy,
                            tx,
                            ty,
                            ZigguratBaseStats.PROJECTILE_SPEED,
                            bouncesRemaining = stats.bounceCount,
                        ),
                    )
                    val intervalMs = ((1.0 / (ziggurat?.stats?.attackSpeed ?: 1.0)) * 1000).toLong()
                    soundManager?.play(SoundEffect.SHOOT, intervalMs)
                }
            ziggurat = zig
            entities.add(zig)

            spawnOrbs()

            val safeStartWave = startWave.coerceAtLeast(1)

            waveSpawner =
                WaveSpawner(
                    onSpawnEnemy = { pendingAdd.add(it) },
                    zigguratX = zig.originX,
                    zigguratY = zig.originY,
                    onEnemyDeath = combatResolver::handleEnemyDeath,
                    // R3-02: forward the attacker reference so applyThorn can reflect damage back
                    // to the melee enemy. Pre-R3-02 this was `{ dmg -> ... null }` and THORN_DAMAGE
                    // never fired on melee hits despite being plumbed through ResolvedStats.
                    onMeleeHit = { atk, dmg -> combatResolver.applyDamageToZiggurat(dmg, atk) },
                    onEnemyFireProjectile = { shooter, sx, sy, tx, ty, dmg ->
                        pendingAdd.add(EnemyProjectileEntity(sx, sy, tx, ty, damage = dmg, shooter = shooter))
                    },
                    onWaveComplete = combatResolver::handleWaveComplete,
                    conditions = battleConditions,
                    enemyTint = biomeTheme.enemyTint,
                    startWave = safeStartWave,
                    tierMultiplier = TierConfig.forTier(currentTier).cashMultiplier,
                )

            // Initial wave announcement. #16: seed lastWave to the opening wave so the first
            // update() tick does not re-detect a wave change (currentWave == lastWave) and
            // announce the same wave twice — pre-fix lastWave stayed 0 here, so every round
            // start fired a doubled wave-start sound + a stacked WaveAnnouncement overlay.
            lastWave = safeStartWave
            triggerWaveAnnouncement(safeStartWave)
        }
    }

    fun setStats(resolvedStats: ResolvedStats) {
        applyStats(resolvedStats)
    }

    /**
     * In-round-purchase stats channel (called by `BattleViewModel.purchaseInRoundUpgrade`).
     *
     * #119: when GOLDEN_ZIGGURAT is active, the [UWController] relayer treats [newStats] as the
     * new BASE — re-capturing it and re-applying the active GOLDEN damage multiplier on top so the
     * player keeps the GOLDEN damage buff over their just-purchased upgrade until GOLDEN expires.
     * When GOLDEN is inactive the relayer returns [newStats] unchanged. [setStats] (init-time push)
     * deliberately does NOT re-layer — it runs before GOLDEN can be active, so the multiplier is 1.0× there.
     */
    fun updateZigguratStats(newStats: ResolvedStats) {
        applyStats(uwController.relayerBaseStats(newStats))
    }

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
    override fun applyStats(newStats: ResolvedStats) {
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
            // #118: this runs on the main thread (in-round ORBS purchase via
            // updateZigguratStats) and structurally mutates `entities`. Guard it with the same
            // monitor the loop thread holds across [update] so the two can't collide. Reentrant
            // for the loop-thread GOLDEN path (activateUW→applyStats, which preserves orbCount
            // so this branch is skipped there, but the lock is harmless if re-entered).
            synchronized(entitiesLock) {
                entities.removeAll { it is OrbEntity }
                spawnOrbs()
            }
        }
    }

    fun spendCash(amount: Long): Boolean = simulation.spend(amount)

    fun update(deltaTime: Float) {
        if (roundOver) return
        val zig = ziggurat ?: return
        // #118: the entire tick reads and structurally mutates `entities` (addAll/removeAll
        // here, plus iteration in uwController.update→getAliveEnemies, the projectile-trail loop,
        // CollisionSystem, and the simulation entity tick). Hold [entitiesLock] for the whole
        // tick so a main-thread [applyStats] orb-reconcile or [init] rebuild can never mutate
        // the list mid-iteration. The lock is reentrant, so the loop-thread GOLDEN path
        // (uwController.update→host.applyStats) re-acquiring it is safe.
        synchronized(entitiesLock) {
            simulation.tickElapsed(deltaTime)
            backgroundRenderer?.update(deltaTime)
            effectEngine?.update(deltaTime)

            uwController.update(deltaTime)
            buffTickers.tickRecovery(deltaTime)
            buffTickers.tickRapidFire(deltaTime)

            // Check for new wave to trigger announcement
            val currentWave = waveSpawner?.currentWave ?: 1
            if (currentWave != lastWave) {
                lastWave = currentWave
                triggerWaveAnnouncement(currentWave)
            }

            waveSpawner?.update(deltaTime, screenWidth, screenHeight)
            entities.addAll(pendingAdd)
            pendingAdd.clear()
            // RO-09 #1 / R4-06 / V1X-09 Phase 3: tick every entity, slowing chrono-slowable
            // entities (enemies) to [chronoSlowFactor] while CHRONO_FIELD is active. The loop
            // now lives in the pure-domain [Simulation]; the engine just supplies the active
            // slow factor (1f when inactive). Projectiles, orbs, and the ziggurat tick at full
            // `deltaTime` because they report `isChronoSlowable = false`.
            simulation.tickEntities(
                entities,
                deltaTime,
                if (uwController.chronoActive) uwController.chronoSlowFactor else 1f,
            )

            // Spawn projectile trails
            if (!reducedMotion) {
                val fx = effectEngine
                if (fx != null) {
                    for (e in entities) {
                        if (e is ProjectileEntity && e.isAlive) {
                            // #243: time-throttle so the per-frame spawn count is bounded by elapsed
                            // sim-time, not by projectile count × catch-up tick count (which thrashed
                            // the 200-slot pool at 4×). Loop-thread-only mutation, under entitiesLock.
                            val (emit, newTimer) = advanceTrail(e.trailTimer, deltaTime)
                            e.trailTimer = newTimer
                            if (emit) {
                                ProjectileTrailEffect.spawn(fx.pool, e.x, e.y, biomeTheme.particleColor)
                            }
                        }
                    }
                }
            }

            // A28: one partition pass over `entities` (under [entitiesLock]) fills the three scratch
            // buffers, replacing three per-frame filterIsInstance().filter{} allocations. The SAME
            // `enemyScratch` instance serves the whole sweep — matching the old single `enemies`
            // snapshot — so mid-sweep deaths behave identically (a corpse stays in the list; the
            // #146 `takeDamage` guard prevents a double onDeath).
            projScratch.clear()
            enemyScratch.clear()
            enemyProjScratch.clear()
            for (e in entities) {
                when {
                    e is ProjectileEntity && e.isAlive -> projScratch.add(e)
                    e is EnemyEntity && e.isAlive -> enemyScratch.add(e)
                    e is EnemyProjectileEntity && e.isAlive -> enemyProjScratch.add(e)
                }
            }
            CollisionSystem.checkCollisions(
                simulation,
                projScratch,
                enemyScratch,
                enemyProjScratch,
                zig.x,
                zig.y,
                zig.width,
                onProjectileHitEnemy = combatResolver::onProjectileHitEnemy,
                onEnemyProjectileHitZiggurat = { proj ->
                    combatResolver.applyDamageToZiggurat(proj.damage, proj.shooter)
                    proj.isAlive = false
                },
            )
            entities.removeAll { !it.isAlive }
        }
        if (zig.currentHp <= 0.0) {
            roundOver = true
            soundManager?.play(SoundEffect.ROUND_END)
        }
    }

    fun render(canvas: Canvas) {
        // #118: snapshot `entities` under [entitiesLock] then draw outside the lock.
        val renderSnapshot = synchronized(entitiesLock) { entities.toList() }
        battleRenderer.render(
            canvas = canvas,
            backgroundRenderer = backgroundRenderer,
            effectEngine = effectEngine,
            reducedMotion = reducedMotion,
            renderSnapshot = renderSnapshot,
            chronoActive = uwController.chronoActive,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            enemyIntelLevel = enemyIntelLevel,
            ziggurat = ziggurat,
            bossCountdownLabel = bossCountdownLabel(),
        )
    }

    /** A31 test seam: toggle the CHRONO_FIELD overlay so the render-path Paint reuse is JVM-testable. */
    @androidx.annotation.VisibleForTesting
    internal fun setChronoActiveForTest(active: Boolean) {
        uwController.setChronoActiveForTest(active)
    }

    fun addEntity(entity: Entity) {
        pendingAdd.add(entity)
    }

    /**
     * Authoritative live count of on-screen enemies for the HUD wave header (#146). Derived
     * from the live entity list — NOT a hand-kept tally — so it is immune to spawn paths that
     * bypass [WaveSpawner.spawnEnemy] (e.g. SCATTER children added straight to `pendingAdd`)
     * and to onDeath re-fires; it can never drift negative. Counts entities currently in the
     * live list (post-`pendingAdd` flush), matching what the player sees on screen. Held under
     * [entitiesLock] (#118) because the loop thread structurally mutates `entities` concurrently.
     */
    fun aliveEnemyCount(): Int =
        synchronized(entitiesLock) {
            entities.count { it is EnemyEntity && it.isAlive }
        }

    /**
     * R4-06: populates the UW lifecycle states from the player's equipped UWs (delegated to
     * [UWController]).
     *
     * #191 CONC-2: initUWs runs on the main thread (playAgain) while the loop thread iterates
     * uwStates in [UWController.update] under entitiesLock. Take the same monitor for mutual
     * exclusion so the main-thread clear/add can never race the loop-thread iteration.
     */
    fun initUWs(equipped: List<OwnedWeapon>) {
        synchronized(entitiesLock) { uwController.initUWs(equipped) }
    }

    /**
     * #191 CONC-2: a thread-safe copy of the UW states for the 200ms polling read in BattleViewModel.
     * Snapshots the LIST STRUCTURE under [entitiesLock] (the only thing the replay race corrupts).
     */
    fun uwSnapshot(): List<UWController.UWState> = synchronized(entitiesLock) { uwController.uwSnapshot() }

    /** R4-06: fires the UW at [index] (delegated to [UWController]). Public for direct invocation by tests. */
    fun activateUW(index: Int) = uwController.activateUW(index)

    // --- Wave announcements ---

    private fun triggerWaveAnnouncement(wave: Int) {
        val fx = effectEngine ?: return
        val isBoss = wave % conditions.bossWaveInterval == 0 && wave > 0
        val bossLabel = strings?.bossIncoming() ?: "⚠ BOSS INCOMING"
        val waveLabel = strings?.waveHeader(wave) ?: "Wave $wave"
        fx.addEffect(
            WaveAnnouncement(wave, isBoss, screenWidth, screenHeight, reducedMotion, bossLabel, waveLabel),
        )
        soundManager?.play(SoundEffect.WAVE_START)

        // Add cooldown text for next wave. The previous WaveCooldownText auto-finishes via the
        // EffectEngine, so there is no per-engine handle to clear.
        val spawner = waveSpawner
        if (spawner != null) {
            // V1X-15b: ENEMY_INTEL L1+ reveals the next wave's composition during cooldown.
            val labeler: (Int) -> String = { secs -> strings?.nextWaveIn(secs) ?: "Next Wave: ${secs}s" }
            val ct =
                WaveCooldownText(screenWidth, nextWaveCompositionLabel(), labeler) {
                    if (spawner.phase == WavePhase.COOLDOWN) {
                        WaveSpawner.COOLDOWN_DURATION - (spawner.phaseTimer)
                    } else {
                        0f
                    }
                }
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
        return strings?.waveComposition(comp)
            ?: ("Next: " + comp.entries.joinToString(", ") { "${it.value} ${it.key.name}" })
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
        return strings?.bossCountdown(waves)
            ?: if (waves == 1) "Boss next wave" else "Boss in $waves waves"
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
            entities.add(
                OrbEntity(
                    zigX = zig.originX,
                    zigY = zig.originY,
                    angle = angle,
                    damage = damage,
                    getEnemies = ::getAliveEnemies,
                    onHitEnemy = combatResolver::onOrbHit,
                ),
            )
        }
    }

    /**
     * Live alive-enemy snapshot for this frame. Called multiple times per tick — by the
     * UW auto-trigger gate, each active BLACK_HOLE / POISON_SWAMP ongoing effect, and once
     * per [OrbEntity] (its `getEnemies` callback) — so allocation here is hot-path GC churn
     * at 60fps × up to 4× catch-up (#125).
     *
     * #125: build the result in a single pass (one `ArrayList`) instead of
     * `filterIsInstance<EnemyEntity>().filter { it.isAlive }`, which allocated two
     * intermediate lists per call. The list is re-derived live on every call (NOT cached
     * across the frame) on purpose: [EnemyEntity.takeDamage] re-fires `onDeath` if called on
     * an already-dead enemy, and BLACK_HOLE/POISON_SWAMP both kill enemies mid-frame — a
     * shared/stale snapshot would let one ongoing effect re-hit another's corpses and
     * double-credit the kill. Guarded by the `R125` GameEngineTest entries.
     */
    private fun getAliveEnemies(): List<EnemyEntity> {
        val result = ArrayList<EnemyEntity>()
        for (e in entities) {
            if (e is EnemyEntity && e.isAlive) result.add(e)
        }
        return result
    }

    // --- Cash economy ---

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

    // --- Targeting ---

    private fun findNearestEnemies(n: Int): List<EnemyEntity> {
        val zig = ziggurat ?: return emptyList()
        return entities
            .asSequence()
            .filterIsInstance<EnemyEntity>()
            .filter { it.isAlive && hypot(it.x - zig.originX, it.y - zig.originY) <= zig.attackRange }
            .sortedBy { hypot(it.x - zig.originX, it.y - zig.originY) }
            .take(n)
            .toList()
    }

    // --- @VisibleForTesting collaborator accessors (#230/#231) ---

    @get:androidx.annotation.VisibleForTesting internal val uwControllerForTest get() = uwController

    @get:androidx.annotation.VisibleForTesting internal val combatResolverForTest get() = combatResolver

    @get:androidx.annotation.VisibleForTesting internal val buffTickersForTest get() = buffTickers
}
