# GameEngine god-class decomposition (#230 + #231) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the 1233-line `presentation/battle/engine/GameEngine.kt` god class into four focused, sub-400-line presentation collaborators (`BattleRenderer`, `UWController`, `BuffTickers`, `CombatResolver`) plus a slimmed orchestrator engine, and hoist the two pure cash-reward formulas into the pure-domain `SimulationMath` — strictly behavior-preserving.

**Architecture:** `GameEngine` stays the **sole owner of `entitiesLock`** (held across the whole `update()` tick) and the **public façade** (consumer API unchanged). Each collaborator is a pure logic holder reaching engine-owned state through a narrow host interface (`UWHost`/`BuffHost`/`CombatHost`) the engine implements; collaborators define **no monitor of their own** and are invoked from inside the engine's already-held lock. `UWController` receives the engine-owned `Simulation` via constructor. The genuinely-pure reward arithmetic moves to `SimulationMath` (the #230 domain payoff); the entity-coupled remainder is tracked in ADR-0012.

**Tech Stack:** Kotlin (JVM target 17), JUnit Jupiter (plain JVM lane, `unitTests.isReturnDefaultValues=true` — no Robolectric for logic paths), Robolectric only for `ChronoOverlayPaintTest`. Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-22-gameengine-decomposition-230-231.md` (adversarially reviewed: 37 findings → 24 surviving, applied).

---

## Guiding rules for every task

- **Move, do not rewrite.** Moved method bodies are copied **verbatim**, including all KDoc and inline `#118`/`#191`/`#119`/`#125`/`#146`/`R4-01`/`RO-11` comments. The ONLY edits to a moved body are the mechanical reference rewrites listed per task (e.g. `stats` → `host.currentStats`, `effectEngine` → `host.effectEngine`, `getAliveEnemies()` → `host.aliveEnemies()`, `simulation` → the injected/`host.simulation`). Do not "improve" anything.
- **The code blocks in this plan are ILLUSTRATIVE for structure/signatures; their KDoc and inline comments are abbreviated for brevity.** When implementing, paste the **full original KDoc and every inline comment verbatim** from the `GameEngine.kt` source of each moved member — do NOT reproduce the plan's trimmed doc text. Specifically restore: the `#146` no-tally block + the RO-11 `#A.2` cash rationale + the Boss-PS "tier-scaled, capped at 100/day" + Step-reward rationale in `CombatResolver.handleEnemyDeath` (GameEngine.kt:1170-1204); the "Heal amount: min(level × 1%, 50%) …" paragraph in `BuffTickers.tickRecovery` (942-944); the `R4-06`/`#119` comments in `UWController.activateUW`'s GOLDEN branch (715-726) and the full `#119` + `R4-01/R4-06 sole-writer` comments in `UWController.update`'s GOLDEN expiry (752-761).
- **No new monitors.** No collaborator contains a `synchronized` block. The engine keeps every `synchronized(entitiesLock)` exactly where it is today.
- **Red-before-green on the test re-points.** After each collaborator extraction, the relevant existing `GameEngineTest` cases must compile and pass through the new path before moving on. Assertions are never weakened.
- **Build command:** `./run-gradle.sh testDebugUnitTest` (full JVM suite) and the final gate `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`. Save output to a temp file and tail/grep it (it is large).
- **Commit after each task.** Branch first (Task 0).

---

## File structure (what lands where)

| File | Responsibility | New / Modified |
|---|---|---|
| `presentation/battle/engine/BattleHosts.kt` | The three host interfaces (`UWHost`/`BuffHost`/`CombatHost`) | Create |
| `presentation/battle/engine/BattleRenderer.kt` | Canvas draw + all `Paint` | Create |
| `presentation/battle/engine/UWController.kt` | UW lifecycle + GOLDEN/chrono/fortune state | Create |
| `presentation/battle/engine/BuffTickers.kt` | recovery / rapid-fire / lifesteal timers | Create |
| `presentation/battle/engine/CombatResolver.kt` | damage / targeting / death / reward side-effects | Create |
| `domain/battle/engine/SimulationMath.kt` | + `killCashReward` / `waveCompleteCash` + cash constants | Modify |
| `presentation/battle/engine/GameEngine.kt` | slimmed orchestrator + lock owner + façade + host impls | Modify |
| `app/.../domain/battle/engine/SimulationMathTest.kt` | + cash-formula tests | Modify |
| `app/.../presentation/battle/engine/GameEngineTest.kt` | reflection re-points | Modify |
| `app/.../presentation/battle/engine/UWControllerTest.kt` | new focused tests | Create |
| `app/.../presentation/battle/engine/BuffTickersTest.kt` | new focused tests | Create |
| `app/.../presentation/battle/engine/CombatResolverTest.kt` | new focused tests | Create |

**Recommended task order** (each leaves the build green): Task 0 (branch) → **Task 1** (`SimulationMath` cash formulas — pure, no dependency) → **Task 2** (`BattleHosts.kt` interfaces) → **Task 3** (`BattleRenderer`) → **Task 4** (`BuffTickers`) → **Task 5** (`CombatResolver`) → **Task 6** (`UWController`) → **Task 7** (slim `GameEngine` wiring + host impls) → **Task 8** (new collaborator unit tests) → **Task 9** (line-count + Paint-free verification) → **Task 10** (docs sync) → **Task 11** (STATE/RUN_LOG) → **Task 12** (final gate + commit).

> Note: Tasks 3–6 each create a collaborator file but the methods are not *removed* from `GameEngine` until Task 7 (the wiring task), so the build stays green throughout — Tasks 3–6 add the new files and their unit tests; Task 7 deletes the now-duplicated engine members and wires the collaborators in. This keeps every intermediate commit compilable. (Alternative per-collaborator cutover is possible but riskier to keep green; this plan cuts over once in Task 7.)

---

### Task 0: Branch

- [ ] **Step 1: Create the feature branch**

Run:
```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git checkout -b refactor/230-231-gameengine-decomposition
```
Expected: `Switched to a new branch 'refactor/230-231-gameengine-decomposition'`

---

### Task 1: Hoist pure cash formulas into `SimulationMath` (#230 payoff)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMath.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMathTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `SimulationMathTest.kt` (inside the existing test class):

```kotlin
// ---- Cash reward formulas (#230 hoist) ----

@Test
fun `killCashReward applies all multipliers like the pre-hoist engine formula`() {
    // baseCash 5 (BASIC), tierMult 1.0, cashBonusLevel 0, fortune 1.0, cashBonusPercent 0, cashResearch 1.0
    assertEquals(5L, SimulationMath.killCashReward(5L, 1.0, 0, 1.0, 0.0, 1.0))
    // cashBonusLevel 10 → 1 + 10*0.03 = 1.30 → 5 * 1.30 = 6.5 → toLong() = 6
    assertEquals(6L, SimulationMath.killCashReward(5L, 1.0, 10, 1.0, 0.0, 1.0))
    // fortune 5.0 (GOLDEN) → 5 * 5.0 = 25
    assertEquals(25L, SimulationMath.killCashReward(5L, 1.0, 0, 5.0, 0.0, 1.0))
    // cashBonusPercent 50 → 1 + 50/100 = 1.5 → 100 * 1.5 = 150
    assertEquals(150L, SimulationMath.killCashReward(100L, 1.0, 0, 1.0, 50.0, 1.0))
    // cashResearch 2.0 → 100 * 2.0 = 200
    assertEquals(200L, SimulationMath.killCashReward(100L, 1.0, 0, 1.0, 0.0, 2.0))
    // tierMult 1.5 → 100 * 1.5 = 150
    assertEquals(150L, SimulationMath.killCashReward(100L, 1.5, 0, 1.0, 0.0, 1.0))
}

@Test
fun `waveCompleteCash applies base plus per-level flat bonus times multipliers`() {
    // level 0 → BASE_CASH_PER_WAVE 20, fortune 1.0, cashResearch 1.0
    assertEquals(20L, SimulationMath.waveCompleteCash(0, 1.0, 1.0))
    // level 3 → 20 + 3*5 = 35
    assertEquals(35L, SimulationMath.waveCompleteCash(3, 1.0, 1.0))
    // level 2 → 30, fortune 5.0 → 150
    assertEquals(150L, SimulationMath.waveCompleteCash(2, 5.0, 1.0))
    // level 0 → 20, cashResearch 2.0 → 40
    assertEquals(40L, SimulationMath.waveCompleteCash(0, 1.0, 2.0))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./run-gradle.sh testDebugUnitTest --tests "*SimulationMathTest*" > /tmp/t1.log 2>&1; tail -30 /tmp/t1.log`
Expected: FAIL — `Unresolved reference: killCashReward` / `waveCompleteCash`.

- [ ] **Step 3: Add the constants + functions to `SimulationMath`**

Add a new section to `SimulationMath.kt` (after the existing `// ---- Recovery Packages` section, before `// ---- Chrono Field`):

```kotlin
    // ---- Cash reward formulas (#230 — hoisted from GameEngine) ----

    /** Base cash granted at the end of each wave, before per-level + multiplier scaling. */
    const val BASE_CASH_PER_WAVE = 20L

    /** Extra wave-end cash per CASH_PER_WAVE workshop level. */
    const val FLAT_BONUS_PER_WAVE_LEVEL = 5L

    /** Per-level CASH_BONUS workshop multiplier increment (3 % per level). */
    const val CASH_BONUS_PER_LEVEL = 0.03

    /**
     * Per-kill cash reward. Identical to the pre-hoist `GameEngine.handleEnemyDeath` formula:
     * `baseCash × tierMult × (1 + cashBonusLevel × 0.03) × fortune × (1 + cashBonusPercent/100) × cashResearch`,
     * truncated to Long. Stacks the workshop CASH_BONUS, tier cash multiplier, GOLDEN_ZIGGURAT
     * fortune buff, the CASH_BONUS_GAIN card (cashBonusPercent), and the CASH_RESEARCH lab.
     */
    fun killCashReward(
        baseCash: Long,
        tierMultiplier: Double,
        cashBonusLevel: Int,
        fortuneMultiplier: Double,
        cashBonusPercent: Double,
        cashResearchMultiplier: Double,
    ): Long {
        val cashBonus = 1.0 + cashBonusLevel * CASH_BONUS_PER_LEVEL
        return (baseCash * tierMultiplier * cashBonus * fortuneMultiplier *
            (1.0 + cashBonusPercent / 100.0) * cashResearchMultiplier).toLong()
    }

    /**
     * Wave-complete cash payout. Identical to the pre-hoist `GameEngine.handleWaveComplete` formula:
     * `(BASE_CASH_PER_WAVE + cashPerWaveLevel × FLAT_BONUS_PER_WAVE_LEVEL) × fortune × cashResearch`,
     * truncated to Long.
     */
    fun waveCompleteCash(
        cashPerWaveLevel: Int,
        fortuneMultiplier: Double,
        cashResearchMultiplier: Double,
    ): Long =
        ((BASE_CASH_PER_WAVE + cashPerWaveLevel * FLAT_BONUS_PER_WAVE_LEVEL) *
            fortuneMultiplier * cashResearchMultiplier).toLong()
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*SimulationMathTest*" > /tmp/t1.log 2>&1; tail -30 /tmp/t1.log`
Expected: PASS (all `SimulationMathTest` green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMath.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMathTest.kt
git commit -m "feat(battle): hoist pure cash-reward formulas to SimulationMath (#230)"
```

---

### Task 2: Create the host interfaces (`BattleHosts.kt`)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BattleHosts.kt`

No test of its own (interfaces); compiled by Task 3+ usage. This task only adds the file so later tasks can reference it.

- [ ] **Step 1: Create `BattleHosts.kt` with the three interfaces**

```kotlin
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
```

- [ ] **Step 2: Compile-check**

Run: `./run-gradle.sh compileDebugKotlin > /tmp/t2.log 2>&1; tail -20 /tmp/t2.log`
Expected: BUILD SUCCESSFUL (interfaces compile; nothing implements them yet).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BattleHosts.kt
git commit -m "feat(battle): add UWHost/BuffHost/CombatHost capability interfaces (#230 #231)"
```

---

### Task 3: Create `BattleRenderer` (Canvas + all Paint)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BattleRenderer.kt`

`BattleRenderer` owns the three `Paint` fields + the `render()` draw body. It receives per-frame state as parameters (the engine takes the under-lock snapshot and passes it in). Move the bodies of `render` (GameEngine.kt:527–566) and the three Paint properties (568–585) verbatim.

- [ ] **Step 1: Create `BattleRenderer.kt`**

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import com.whitefang.stepsofbabylon.presentation.battle.biome.BackgroundRenderer
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import com.whitefang.stepsofbabylon.presentation.battle.ui.HealthBarRenderer

/**
 * Presentation-layer renderer for the battle SurfaceView (#231 decomposition). Owns ALL Canvas
 * `Paint` allocation and the per-frame draw sequence lifted verbatim from the old
 * `GameEngine.render()`. Pure draw logic — it mutates no shared engine state; the engine takes the
 * under-`entitiesLock` entity snapshot and passes it (plus the per-frame scalar reads) in.
 *
 * The three Paint fields are cached (allocated once) — A31 audit fix preserved exactly.
 */
class BattleRenderer(private val healthBarRenderer: HealthBarRenderer = HealthBarRenderer()) {

    // A31 (audit): cached CHRONO_FIELD overlay paint — was allocated per frame in render(). Colour
    // 0x222196F3 preserved exactly (semi-transparent blue). The literal intentionally keeps the
    // existing value; the alpha nuance the audit flagged is left unchanged (no observable colour
    // change in this PR).
    private val chronoOverlayPaint = android.graphics.Paint().apply {
        color = 0x222196F3; style = android.graphics.Paint.Style.FILL
    }

    private val hpPercentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8E7.toInt(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER
    }
    private val bossCountdownPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF44336.toInt(); textSize = 26f; textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true
    }

    /**
     * Renders one frame. [renderSnapshot] is the engine's under-lock copy of the live entity list;
     * everything else is a per-frame scalar/nullable read the engine supplies. Behaviour is identical
     * to the pre-decomposition `GameEngine.render()` — the draw sequence + the ENEMY_INTEL overlays +
     * the chrono overlay + screen-shake apply/restore are unchanged.
     */
    fun render(
        canvas: Canvas,
        backgroundRenderer: BackgroundRenderer?,
        effectEngine: EffectEngine?,
        reducedMotion: Boolean,
        renderSnapshot: List<Entity>,
        chronoActive: Boolean,
        screenWidth: Float,
        screenHeight: Float,
        enemyIntelLevel: Int,
        ziggurat: ZigguratEntity?,
        bossCountdownLabel: String?,
    ) {
        backgroundRenderer?.render(canvas) ?: canvas.drawColor(0xFF6B3A2A.toInt())

        val fx = effectEngine
        // Screen shake
        if (fx != null && !reducedMotion) fx.screenShake.apply(canvas)

        renderSnapshot.forEach { it.render(canvas) }

        // Render effects (particles, floating text, UW visuals, auras, announcements)
        fx?.render(canvas)

        // Chrono field overlay
        if (chronoActive) {
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, chronoOverlayPaint)
        }

        if (fx != null && !reducedMotion) fx.screenShake.restore(canvas)

        // V1X-15b: ENEMY_INTEL L5+ per-enemy HP-% label above each enemy's HP bar. Drawn here
        // (not in EnemyEntity.render) so the level gate stays out of the entity constructor.
        if (enemyIntelLevel >= 5) {
            for (e in renderSnapshot) {
                if (e is EnemyEntity && e.isAlive) {
                    val pct = ((e.currentHp / e.maxHp).coerceIn(0.0, 1.0) * 100).toInt()
                    canvas.drawText("$pct%", e.x, e.y - e.height / 2f - 14f, hpPercentPaint)
                }
            }
        }

        ziggurat?.let { healthBarRenderer.render(canvas, it.currentHp, it.maxHp, screenWidth) }

        // V1X-15b: ENEMY_INTEL L10 boss-arrival countdown, right-aligned to clear the
        // centre-aligned cooldown banner.
        bossCountdownLabel?.let { canvas.drawText(it, screenWidth - 16f, 90f, bossCountdownPaint) }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./run-gradle.sh compileDebugKotlin > /tmp/t3.log 2>&1; tail -20 /tmp/t3.log`
Expected: BUILD SUCCESSFUL (file compiles standalone; not yet wired).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BattleRenderer.kt
git commit -m "feat(battle): extract BattleRenderer (Canvas + Paint) from GameEngine (#231)"
```

---

### Task 4: Create `BuffTickers` (recovery / rapid-fire / lifesteal)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BuffTickers.kt`

Move `tickRecoveryPackages` (→ rename `tickRecovery`, body GameEngine.kt:946–969), `tickRapidFire` (993–1035), `applyLifesteal` (1136–1151), and the four timer fields (`recoveryTimer`, `rapidFireTimer`, `rapidFireActiveRemaining`, `lifestealAccumulator`). Reference rewrites: `waveSpawner?.phase` → `host.wavePhase`; `wsLevel(...)` → `host.wsLevel(...)`; `ziggurat` → `host.ziggurat`; `effectEngine` → `host.effectEngine`; `strings` → `host.strings`. Add a `reset()` for round-start.

- [ ] **Step 1: Create `BuffTickers.kt`**

```kotlin
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
     */
    fun tickRecovery(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        // Preserve the original `waveSpawner ?: return` bare-return: when no spawner exists yet,
        // leave timers untouched (host.wavePhase is null → would otherwise fall into the reset
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
     * the timer-fire step so at L10 (interval == duration) the multiplier transitions seamlessly.
     */
    fun tickRapidFire(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        // Preserve the original `waveSpawner ?: return` bare-return (see tickRecovery).
        if (host.wavePhase == null) return
        if (host.wavePhase != WavePhase.SPAWNING) {
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
     * Applies a lifesteal heal of [healAmount] HP to the ziggurat (R3-02). Added directly to
     * `zig.currentHp` (a Double, conserving sub-1 heals); the same fractional amount accumulates and
     * emits a `+X HP` FloatingText each time it crosses an integer HP threshold.
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
```

- [ ] **Step 2: Compile-check**

Run: `./run-gradle.sh compileDebugKotlin > /tmp/t4.log 2>&1; tail -20 /tmp/t4.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BuffTickers.kt
git commit -m "feat(battle): extract BuffTickers (recovery/rapid-fire/lifesteal) from GameEngine (#231)"
```

---

### Task 5: Create `CombatResolver` (damage / targeting / death)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt`

Move (verbatim, with reference rewrites): `onProjectileHitEnemy` (1047–1087), `onOrbHitEnemy` (901–914), `applyDamageToZiggurat` (1089–1110), `applyThorn` (1112–1120), `handleEnemyDeath` (1165–1232), `handleWaveComplete` (1037–1043). Own `calculateDamage`/`calculateDefense` as fields. Reference rewrites: `stats` → `host.currentStats`; `conditions` → `host.conditions`; `tier` → `host.tier`; `ziggurat` → `host.ziggurat`; `effectEngine` → `host.effectEngine`; `soundManager` → `host.soundManager`; `strings` → `host.strings`; `reducedMotion` → `host.reducedMotion`; `simulation` → `host.simulation`; `fortuneMultiplier` → `host.fortuneMultiplier`; `cashResearchMultiplier` → `host.cashResearchMultiplier`; `cashBonusPercent` → `host.cashBonusPercent`; `wsLevel(...)` → `host.wsLevel(...)`; `pendingAdd.add(...)` → `host.addPending(...)`; `applyLifesteal(...)` → `host.applyLifesteal(...)`; the bounce-target `entities.asSequence()...` → `host.aliveEnemies().asSequence()...`; the cash formulas → `SimulationMath.killCashReward(...)` / `SimulationMath.waveCompleteCash(...)`; the second-wind block → `host.secondWindHpPercent` + `host.consumeSecondWind()`.

- [ ] **Step 1: Create `CombatResolver.kt`**

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDamage
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
import com.whitefang.stepsofbabylon.presentation.audio.SoundEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.DeathEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Combat resolution for the battle simulation (#231 decomposition): projectile/orb hits, ziggurat
 * damage + defense + thorn + second-wind + death-defy, enemy death (reward credit/feedback + SCATTER
 * split), and wave-complete cash. Lifted verbatim from GameEngine; reward *arithmetic* delegates to
 * the pure-domain [SimulationMath] (#230). Reaches engine state via [CombatHost]. Loop-thread only —
 * invoked from inside the engine's held `entitiesLock` (collision callbacks, death handler); holds no
 * monitor of its own.
 */
class CombatResolver(private val host: CombatHost) {

    private val calculateDamage = CalculateDamage()
    private val calculateDefense = CalculateDefense()

    fun onProjectileHitEnemy(proj: ProjectileEntity, enemy: EnemyEntity) {
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        val dist = hypot(zig.originX - enemy.x, zig.originY - enemy.y)
        val result = calculateDamage(stats, dist)

        // #17: gate knockback + lifesteal on damage actually dealt — takeDamage returns 0.0
        // when the hit is fully absorbed by an armor charge.
        val dealt = enemy.takeDamage(result.amount)
        proj.hitEnemies.add(enemy)
        proj.isAlive = false

        host.soundManager?.play(SoundEffect.HIT)

        if (dealt > 0.0 && stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX; val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * host.conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (dealt > 0.0 && stats.lifestealPercent > 0) {
            host.applyLifesteal(dealt * stats.lifestealPercent)
        }

        // Bounce shot — candidates from the live alive-enemy list (#125 no-cache), excluding
        // already-hit enemies, nearest to the JUST-HIT enemy (NOT the ziggurat; no attackRange gate).
        if (proj.bouncesRemaining > 0) {
            val nextTarget = host.aliveEnemies().asSequence()
                .filter { it.isAlive && it !in proj.hitEnemies }
                .minByOrNull { hypot(it.x - enemy.x, it.y - enemy.y) }
            if (nextTarget != null) {
                host.addPending(ProjectileEntity(
                    startX = enemy.x, startY = enemy.y,
                    targetX = nextTarget.x, targetY = nextTarget.y,
                    speed = ZigguratBaseStats.PROJECTILE_SPEED,
                    bouncesRemaining = proj.bouncesRemaining - 1,
                    hitEnemies = proj.hitEnemies,
                ))
            }
        }
    }

    fun onOrbHitEnemy(enemy: EnemyEntity, damage: Double) {
        // #17: gate knockback + lifesteal on damage actually dealt (0.0 when armor-absorbed).
        val dealt = enemy.takeDamage(damage)
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        if (dealt > 0.0 && stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX; val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * 0.5f * host.conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (dealt > 0.0 && stats.lifestealPercent > 0) {
            host.applyLifesteal(dealt * stats.lifestealPercent)
        }
    }

    fun applyDamageToZiggurat(rawDamage: Double, attacker: EnemyEntity?) {
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        val mitigated = calculateDefense(rawDamage, stats)
        if (zig.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (Random.nextDouble() < stats.deathDefyChance) {
                zig.currentHp = 1.0; applyThorn(rawDamage, attacker); return
            }
        }
        if (zig.currentHp - mitigated <= 0.0 && host.secondWindHpPercent > 0.0 && host.consumeSecondWind()) {
            zig.currentHp = zig.maxHp * host.secondWindHpPercent
            applyThorn(rawDamage, attacker); return
        }
        val prevHpRatio = zig.currentHp / zig.maxHp
        zig.currentHp = (zig.currentHp - mitigated).coerceAtLeast(0.0)
        val newHpRatio = zig.currentHp / zig.maxHp
        // Screen shake when HP drops below 25%
        if (prevHpRatio > 0.25 && newHpRatio <= 0.25 && !host.reducedMotion) {
            host.effectEngine?.screenShake?.trigger(5f, 0.2f)
        }
        applyThorn(rawDamage, attacker)
    }

    private fun applyThorn(rawDamage: Double, attacker: EnemyEntity?) {
        if (attacker == null || !attacker.isAlive) return
        val reflection = SimulationMath.thornReflectionDamage(
            rawDamage = rawDamage,
            thornPercent = host.currentStats.thornPercent,
            conditionMultiplier = host.conditions.thornMultiplier.toDouble(),
        )
        if (reflection > 0) attacker.takeDamage(reflection)
    }

    fun handleWaveComplete(wave: Int) {
        // RO-11 #A.2: CASH_RESEARCH multiplies the wave-end cash payout.
        val waveCash = SimulationMath.waveCompleteCash(
            cashPerWaveLevel = host.wsLevel(UpgradeType.CASH_PER_WAVE),
            fortuneMultiplier = host.fortuneMultiplier,
            cashResearchMultiplier = host.cashResearchMultiplier,
        )
        host.simulation.creditCash(waveCash)
        host.simulation.applyInterest(host.wsLevel(UpgradeType.INTEREST))
    }

    fun handleEnemyDeath(enemy: EnemyEntity) {
        host.simulation.recordEnemyKilled()
        val baseCash = EnemyScaler.cashReward(enemy.enemyType)
        val tierMult = TierConfig.forTier(host.tier).cashMultiplier
        // RO-11 #A.2: CASH_RESEARCH multiplies the per-kill cash. Stacks multiplicatively with
        // workshop CASH_BONUS, tier cash multiplier, GOLDEN_ZIGGURAT UW, and the CASH_BONUS_GAIN card.
        val killCash = SimulationMath.killCashReward(
            baseCash = baseCash,
            tierMultiplier = tierMult,
            cashBonusLevel = host.wsLevel(UpgradeType.CASH_BONUS),
            fortuneMultiplier = host.fortuneMultiplier,
            cashBonusPercent = host.cashBonusPercent,
            cashResearchMultiplier = host.cashResearchMultiplier,
        )
        host.simulation.creditCash(killCash)

        host.soundManager?.play(SoundEffect.ENEMY_DEATH)

        // Death effect particles + floating cash text
        val fx = host.effectEngine
        if (fx != null) {
            if (!host.reducedMotion) DeathEffect.spawn(fx.pool, enemy.x, enemy.y, enemy.enemyType)
            fx.addEffect(FloatingText(enemy.x, enemy.y, host.strings?.cashReward(killCash) ?: "+$killCash"))
            // Boss death screen shake
            if (enemy.enemyType == EnemyType.BOSS && !host.reducedMotion) {
                fx.screenShake.trigger(8f, 0.3f)
            }
        }

        // Boss-kill Power Stone reward — emitted as a SimulationEvent; BattleViewModel's collector
        // awards the PS via AwardBossPowerStones.
        if (enemy.enemyType == EnemyType.BOSS) {
            host.simulation.emit(SimulationEvent.BossKilled(host.tier, enemy.x, enemy.y + 24f))
        }

        // Battle Step reward — flat per enemy type, independent of multipliers. Wallet credit, cap
        // enforcement, and floating-text spawn all live in the collector (BattleViewModel).
        val stepReward = EnemyScaler.stepReward(enemy.enemyType)
        if (stepReward > 0L) {
            host.simulation.creditSteps(stepReward)
            host.simulation.emit(SimulationEvent.StepReward(stepReward, enemy.x, enemy.y + 24f))
        }

        if (enemy.enemyType == EnemyType.SCATTER) {
            val zig = host.ziggurat ?: return
            val childCount = (2..3).random()
            repeat(childCount) { i ->
                val child = EnemyEntity(
                    enemyType = EnemyType.BASIC,
                    currentHp = enemy.maxHp * 0.5, maxHp = enemy.maxHp * 0.5,
                    speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * host.conditions.enemySpeedMultiplier,
                    damage = enemy.damage * 0.5,
                    targetX = zig.originX, targetY = zig.originY,
                    onDeath = ::handleEnemyDeath,
                    // R3-02: SCATTER child enemies also forward their attacker reference so
                    // THORN_DAMAGE reflects against them.
                    onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
                ).apply {
                    x = enemy.x + (i - childCount / 2f) * 15f; y = enemy.y; initDistance()
                }
                host.addPending(child)
            }
        }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./run-gradle.sh compileDebugKotlin > /tmp/t5.log 2>&1; tail -20 /tmp/t5.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt
git commit -m "feat(battle): extract CombatResolver (damage/targeting/death) from GameEngine (#231 #230)"
```

---

### Task 6: Create `UWController` (UW lifecycle + GOLDEN/chrono/fortune state)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/UWController.kt`

Move (verbatim, with reference rewrites): the `UWState` data class (258–265), `uwStates` (266), the UW state fields (`chronoActive`, `chronoSlowFactor`, `goldenZigActive`, `preGoldenStats`, `goldenDamageMult`, `fortuneMultiplier`), `initUWs` (606–624), `uwSnapshot` (632), `activateUW` (654–730), `updateUWs` (732–804), `resetUWCooldowns` (806). Add `resetRoundState()` (the init-path field reset) and `onBaseStatsChanged(newStats)` (the #119 re-layer the engine façade calls). Constructor takes `host: UWHost` and the engine-owned `simulation: Simulation`. Reference rewrites: `ziggurat` → `host.ziggurat`; `screenWidth`/`screenHeight` → `host.screenWidth`/`host.screenHeight`; `reducedMotion` → `host.reducedMotion`; `uwCooldownMultiplier` → `host.uwCooldownMultiplier`; `effectEngine` → `host.effectEngine`; `soundManager` → `host.soundManager`; `stats` → `host.currentStats`; `applyStats(...)` → `host.applyStats(...)`; `getAliveEnemies()` → `host.aliveEnemies()`; `simulation.advanceUWTimers/isUWReadyToFire` → the injected `simulation`.

- [ ] **Step 1: Create `UWController.kt`**

```kotlin
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
class UWController(private val host: UWHost, private val simulation: Simulation) {

    /**
     * R4-06: per-UW state with three independent path levels (see UWPath). The active CHRONO_FIELD
     * slow factor is captured in [chronoSlowFactor] at activation time so a re-equip / level-up
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

    var chronoActive = false
        private set
    var chronoSlowFactor: Float = 1f
        private set
    private var goldenZigActive = false
    private var preGoldenStats: ResolvedStats? = null
    private var goldenDamageMult: Double = 1.0

    /**
     * Active GOLDEN_ZIGGURAT cash buff multiplier. Written by [activateUW] (set to the DAMAGE-path
     * value) and reset to 1.0 by the GOLDEN expiry branch in [updateUWs]. GOLDEN is the sole writer
     * post-R4-01. Read by CombatResolver's cash formulas via CombatHost.fortuneMultiplier.
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
    internal fun setChronoActiveForTest(active: Boolean) { chronoActive = active }

    /**
     * #119 GOLDEN re-layer entry point, called by GameEngine.updateZigguratStats on the MAIN thread.
     * When GOLDEN is active, treat [newStats] as the new BASE: re-capture preGoldenStats and re-apply
     * the active goldenDamageMult on top, returning the stats the engine should actually apply. When
     * inactive, returns [newStats] unchanged. PRE-EXISTING lock-free main-thread path — this is a
     * single adjacent read-modify-write of the GOLDEN trio with NO host round-trip that re-reads the
     * trio; do NOT add a lock (preserve the exact pre-decomposition shape, #119 / spec §5.B).
     */
    fun relayerBaseStats(newStats: ResolvedStats): ResolvedStats {
        return if (goldenZigActive) {
            preGoldenStats = newStats
            newStats.copy(damage = newStats.damage * goldenDamageMult)
        } else {
            newStats
        }
    }

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

    fun resetUWCooldowns() { uwStates.forEach { it.cooldownRemaining = 0f } }

    fun activateUW(index: Int) {
        val uw = uwStates.getOrNull(index) ?: return
        if (uw.cooldownRemaining > 0f) return
        if (uw.effectTimeRemaining > 0f) return
        uw.cooldownRemaining = uw.type.cooldownAtLevel(uw.cooldownLevel) * host.uwCooldownMultiplier
        val zig = host.ziggurat ?: return

        val duration = if (uw.type == UltimateWeaponType.CHRONO_FIELD) {
            uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
        } else {
            uw.type.effectDurationSeconds
        }
        if (duration > 0f) uw.effectTimeRemaining = duration

        host.soundManager?.play(SoundEffect.UW_ACTIVATE)

        val fx = host.effectEngine
        if (fx != null) {
            val effectDuration = when {
                uw.type == UltimateWeaponType.DEATH_WAVE -> 1.2f
                duration > 0f -> duration
                else -> 0.5f
            }
            val cx: Float; val cy: Float
            when (uw.type) {
                UltimateWeaponType.BLACK_HOLE -> { cx = host.screenWidth / 2f; cy = host.screenHeight * 0.35f }
                UltimateWeaponType.POISON_SWAMP -> { cx = host.screenWidth / 2f; cy = host.screenHeight * 0.6f }
                else -> { cx = zig.x; cy = zig.originY }
            }
            fx.addEffect(UWVisualEffect(uw.type, fx.pool, cx, cy, host.screenWidth, host.screenHeight, effectDuration, host.reducedMotion))
        }

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
                val chainLen = uw.type.secondaryAtLevel(uw.secondaryLevel).toInt().coerceAtLeast(1)
                val targets = host.aliveEnemies().sortedBy { hypot(it.x - zig.x, it.y - zig.y) }.take(chainLen)
                targets.forEach { it.takeDamage(dmg) }
            }
            UltimateWeaponType.BLACK_HOLE -> {} // Ongoing effect in updateUWs
            UltimateWeaponType.CHRONO_FIELD -> {
                chronoActive = true
                chronoSlowFactor = uw.type.damageAtLevel(uw.damageLevel).toFloat()
            }
            UltimateWeaponType.POISON_SWAMP -> {} // Ongoing effect in updateUWs
            UltimateWeaponType.GOLDEN_ZIGGURAT -> {
                goldenZigActive = true; preGoldenStats = host.currentStats
                val cashMult = uw.type.damageAtLevel(uw.damageLevel)
                fortuneMultiplier = fortuneMultiplier.coerceAtLeast(cashMult)
                val dmgMult = uw.type.secondaryAtLevel(uw.secondaryLevel)
                goldenDamageMult = dmgMult
                host.applyStats(host.currentStats.copy(damage = host.currentStats.damage * dmgMult))
            }
        }
    }

    fun update(deltaTime: Float) {
        val zig = host.ziggurat ?: return
        for (uw in uwStates) {
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
                            // #119: preGoldenStats reflects any in-round upgrade bought during GOLDEN.
                            goldenZigActive = false; preGoldenStats?.let { host.applyStats(it) }; preGoldenStats = null
                            goldenDamageMult = 1.0
                            fortuneMultiplier = 1.0
                        }
                        else -> {}
                    }
                }
                when (uw.type) {
                    UltimateWeaponType.BLACK_HOLE -> {
                        val cx = host.screenWidth / 2f; val cy = host.screenHeight * 0.35f
                        val dps = uw.type.damageAtLevel(uw.damageLevel)
                        val pull = uw.type.secondaryAtLevel(uw.secondaryLevel).toFloat()
                        host.aliveEnemies().forEach { e ->
                            val dx = cx - e.x; val dy = cy - e.y; val d = hypot(dx, dy).coerceAtLeast(1f)
                            e.x += dx / d * pull * deltaTime; e.y += dy / d * pull * deltaTime
                            e.takeDamage(dps * deltaTime)
                        }
                    }
                    UltimateWeaponType.POISON_SWAMP -> {
                        val dotFrac = uw.type.damageAtLevel(uw.damageLevel)
                        host.aliveEnemies().forEach { e -> e.takeDamage(e.maxHp * dotFrac * deltaTime) }
                    }
                    else -> {}
                }
            }
        }

        // R4-06: auto-trigger any UW off cooldown + not mid-effect, only when an enemy is alive.
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
```

- [ ] **Step 2: Compile-check**

Run: `./run-gradle.sh compileDebugKotlin > /tmp/t6.log 2>&1; tail -20 /tmp/t6.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/UWController.kt
git commit -m "feat(battle): extract UWController (UW lifecycle + GOLDEN/chrono state) from GameEngine (#231)"
```

> **Note on `UWState` type-name change:** the public `GameEngine.UWState` becomes `UWController.UWState`. `BattleViewModel` reads `engine.uwSnapshot()` and maps over `uw.type`/`uw.damageLevel`/etc. (no explicit `GameEngine.UWState` type reference at the call site — verified). Task 7 keeps `engine.uwSnapshot(): List<UWController.UWState>` so the VM mapping still compiles. If any call site DID name `GameEngine.UWState`, add a `typealias UWState = UWController.UWState` in GameEngine in Task 7. Confirm during Task 7.

---

### Task 7: Slim `GameEngine` — wire collaborators, implement hosts, delete moved members

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt`
- Modify (re-point reflection): `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt`

This is the cutover. The engine: (a) constructs the four collaborators as fields; (b) implements `UWHost`/`BuffHost`/`CombatHost`; (c) deletes the moved methods/fields; (d) delegates `render`, `update`'s collaborator calls, `init`'s resets, the UW façade methods, `updateZigguratStats`, and `setChronoActiveForTest`.

- [ ] **Step 1: Edit the GameEngine class declaration + fields**

Change `class GameEngine {` to:
```kotlin
class GameEngine : UWHost, BuffHost, CombatHost {
```

Delete these now-moved fields from GameEngine: `healthBarRenderer` (73), `calculateDamage`/`calculateDefense` (74–75), `fortuneMultiplier` (169), `recoveryTimer` (177), `rapidFireTimer` (187), `rapidFireActiveRemaining` (197), `lifestealAccumulator` (209), the `UWState` data class (258–265), `uwStates` (266), `chronoActive` (267), `chronoSlowFactor` (275), `goldenZigActive` (276), `preGoldenStats` (277), `goldenDamageMult` (287), and the three Paint properties `chronoOverlayPaint`/`hpPercentPaint`/`bossCountdownPaint` (568–585).

**Before deleting the cash constants, sweep for external references:**
```bash
rg -n "BASE_CASH_PER_WAVE|FLAT_BONUS_PER_WAVE_LEVEL" app/src
```
Expected hits: `GameEngine.kt` (companion + the two formula call sites — all being deleted/moved) AND **`app/src/test/java/com/whitefang/stepsofbabylon/balance/CashEconomyTest.kt:25`** (`val waveCash = GameEngine.BASE_CASH_PER_WAVE`). Then delete the `BASE_CASH_PER_WAVE`/`FLAT_BONUS_PER_WAVE_LEVEL` companion constants from GameEngine (now in SimulationMath) **and** in the SAME commit re-point `CashEconomyTest.kt:25` to `SimulationMath.BASE_CASH_PER_WAVE` (add `import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath`), so no commit leaves the JVM suite uncompilable. (Add `CashEconomyTest.kt` to this task's modified-files set.)

Add the collaborator fields **after** the `override val simulation: Simulation = Simulation()` declaration (which was line 115's `private val simulation` — see clash note item 4; it MUST stay first so the `UWController` ctor can read it):
```kotlin
    private val uwController = UWController(this, simulation)
    private val buffTickers = BuffTickers(this)
    private val combatResolver = CombatResolver(this)
    private val battleRenderer = BattleRenderer()
```

> The collaborators capture `this` as their host. `this`-escape in a field initializer is safe here: collaborators store the reference but invoke no host method until `init()`/`update()` run (well after construction). If detekt flags it, add `@Suppress("LeakingThis")` on the fields.

- [ ] **Step 2: Implement the host members on GameEngine**

The engine already has most of these as fields/methods. Make them satisfy the interfaces (mark `override` where the field/getter already exists; add the small new ones). Add near the existing field declarations / wsLevel:

```kotlin
    // --- Host interface implementations (#230/#231 decomposition) ---

    override val currentStats: ResolvedStats get() = stats
    override val conditions: BattleConditionEffects get() = battleConditions   // private field renamed conditions→battleConditions
    override val tier: Int get() = currentTier                                 // private field renamed tier→currentTier (see clash note)
    override val wavePhase: WavePhase? get() = waveSpawner?.phase
    override val fortuneMultiplier: Double get() = uwController.fortuneMultiplier

    override fun consumeSecondWind(): Boolean {
        if (secondWindUsed) return false
        secondWindUsed = true
        return true
    }

    override fun addPending(entity: Entity) { pendingAdd.add(entity) }

    override fun aliveEnemies(): List<EnemyEntity> = getAliveEnemies()

    override fun nearestEnemies(n: Int): List<EnemyEntity> = findNearestEnemies(n)

    override fun applyLifesteal(healAmount: Double) = buffTickers.applyLifesteal(healAmount)

    override fun wsLevel(type: UpgradeType): Int = effectiveLevels[type] ?: 0
```

**Naming-clash resolution (CRITICAL — review-corrected).** Three categories:

1. **`tier` (private field, line 84) → rename to `currentTier`.** ⚠️ Do NOT rename it to `playerTier`: `init()` ALREADY has a parameter named `playerTier` (line 299), and `init()` assigns the field from it (`tier = playerTier`, line 329). Renaming the field to `playerTier` would make line 329 read `playerTier = playerTier` — a `Val cannot be reassigned` compile error (the param shadows the field). Rename the field to **`currentTier`** instead. Update its ~5 internal references: line 329 (`currentTier = playerTier`), 331 (`BattleConditionEffects.fromTier(currentTier)`), 332 (`Biome.forTier(currentTier)`), 368 (`TierConfig.forTier(currentTier)`), 1168/1198 (`TierConfig.forTier(currentTier)` inside the now-moved `handleEnemyDeath`/`handleWaveComplete` — but those move to CombatResolver and read `host.tier`, so the engine's host-impl `override val tier get() = currentTier` covers them). The `init()` parameter `playerTier` stays as-is.
2. **`conditions` (private field, line 85) → rename to `battleConditions`.** Its backing field is private and never externally written, so the `override val conditions get() = battleConditions` getter form is correct. Update its internal references (the `WaveSpawner(conditions = …)` wiring at line 365, the `onProjectileHitEnemy`/`applyDamageToZiggurat`/etc. reads — but those move to CombatResolver and read `host.conditions`).
3. **The VM-written `@Volatile var` fields stay PUBLIC SETTABLE `var` and just gain `override`** (a `var` legally overrides an interface `val`). ⚠️ Do NOT convert them to a read-only `get()`-backed form or rename their backing field — `BattleViewModel` writes them externally (`engine.secondWindHpPercent = …` at BattleViewModel.kt:204/490; `engine.cashResearchMultiplier = …` at 625; `engine.cashBonusPercent = …` at 204/490). Mark these `override`: `@Volatile override var secondWindHpPercent: Double = 0.0`, `@Volatile override var cashResearchMultiplier: Double = 1.0`, `@Volatile override var cashBonusPercent: Double = 0.0`.
4. **Other engine fields the interfaces declare become `override`** (no rename, no getter indirection — just add `override`): `screenWidth`, `screenHeight`, `ziggurat`, `reducedMotion`, `effectEngine`, `soundManager`, `strings`, `uwCooldownMultiplier`. For `simulation` (currently `private val simulation = Simulation()`, line 115): simply change it to **`override val simulation: Simulation = Simulation()`** — keep it FIRST (before the collaborator fields) so `UWController(this, simulation)` can read it. Keep `applyStats` as `override fun`.

- [ ] **Step 3: Rewrite `init()` resets to delegate**

In `init()` (inside `synchronized(entitiesLock)`), replace the inline UW/buff field resets:
```kotlin
            fortuneMultiplier = 1.0
            secondWindUsed = false
            uwStates.clear(); chronoActive = false; chronoSlowFactor = 1f; goldenZigActive = false; preGoldenStats = null; goldenDamageMult = 1.0
            recoveryTimer = 0f
            rapidFireTimer = 0f
            rapidFireActiveRemaining = 0f
            lifestealAccumulator = 0.0
```
with:
```kotlin
            secondWindUsed = false
            uwController.resetRoundState()
            buffTickers.reset()
```
(The `simulation.reset()` / `roundOver = false` lines stay.) Update the `tier`/`conditions` field references to `currentTier`/`battleConditions` per the clash note — but NOT the `init()` parameter `playerTier`, which is unchanged: line 329 becomes `currentTier = playerTier` (field = parameter).

- [ ] **Step 4: Rewrite `updateZigguratStats` to use the relayer**

Replace the body (392–399):
```kotlin
    fun updateZigguratStats(newStats: ResolvedStats) {
        applyStats(uwController.relayerBaseStats(newStats))
    }
```
(`relayerBaseStats` does the #119 GOLDEN re-capture+re-apply and returns the stats to apply; lock-free main-thread shape preserved.)

- [ ] **Step 5: Rewrite `update()` collaborator calls**

In the `synchronized(entitiesLock)` tick body, replace:
- `updateUWs(deltaTime)` → `uwController.update(deltaTime)`
- `tickRecoveryPackages(deltaTime)` → `buffTickers.tickRecovery(deltaTime)`
- `tickRapidFire(deltaTime)` → `buffTickers.tickRapidFire(deltaTime)`
- the `simulation.tickEntities(entities, deltaTime, if (chronoActive) chronoSlowFactor else 1f)` line → `simulation.tickEntities(entities, deltaTime, if (uwController.chronoActive) uwController.chronoSlowFactor else 1f)`
- the `CollisionSystem.checkCollisions(...)` callbacks: `onProjectileHitEnemy = ::onProjectileHitEnemy` → `onProjectileHitEnemy = combatResolver::onProjectileHitEnemy`; the enemy-projectile lambda `applyDamageToZiggurat(proj.damage, proj.shooter)` → `combatResolver.applyDamageToZiggurat(proj.damage, proj.shooter)`.

- [ ] **Step 6: Rewrite `render()` to delegate**

Replace the `render(canvas)` body (527–566) with:
```kotlin
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
```

- [ ] **Step 7: Delegate the UW façade methods + init wiring**

Replace `initUWs`/`uwSnapshot`/`activateUW`/`resetUWCooldowns` with delegations that preserve the engine's public signatures AND the #191 main-thread lock:
```kotlin
    fun initUWs(equipped: List<OwnedWeapon>) {
        // #191 CONC-2: main-thread mutation of uwStates — take entitiesLock (loop thread iterates it).
        synchronized(entitiesLock) { uwController.initUWs(equipped) }
    }

    fun uwSnapshot(): List<UWController.UWState> = synchronized(entitiesLock) { uwController.uwSnapshot() }

    fun activateUW(index: Int) = uwController.activateUW(index)

    fun resetUWCooldowns() = uwController.resetUWCooldowns()
```
Replace `setChronoActiveForTest`:
```kotlin
    @androidx.annotation.VisibleForTesting
    internal fun setChronoActiveForTest(active: Boolean) { uwController.setChronoActiveForTest(active) }
```
Delete the now-moved private methods: `updateUWs`, `tickRecoveryPackages`, `tickRapidFire`, `applyLifesteal`, `onProjectileHitEnemy`, `onOrbHitEnemy`, `applyDamageToZiggurat`, `applyThorn`, `handleEnemyDeath`, `handleWaveComplete`. Keep `getAliveEnemies`, `findNearestEnemies`, `spawnOrbs`, `addEntity`, `aliveEnemyCount`, the wave-announcement methods, `wsLevel` (now also the host impl), `updateEffectiveLevels`, `applyStats`, `setStats`.

> The `init` block wires `WaveSpawner` callbacks: `onEnemyDeath = ::handleEnemyDeath` → `combatResolver::handleEnemyDeath`; `onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) }` → `{ atk, dmg -> combatResolver.applyDamageToZiggurat(dmg, atk) }`; `onWaveComplete = ::handleWaveComplete` → `combatResolver::handleWaveComplete`. The orb `onHitEnemy = ::onOrbHitEnemy` in `spawnOrbs` → `combatResolver::onOrbHitEnemy`. **`onEnemyFireProjectile` is intentionally LEFT UNCHANGED** — its lambda only calls `pendingAdd.add(EnemyProjectileEntity(...))`, and `pendingAdd` stays engine-owned, so it compiles as-is. Likewise the `ZigguratEntity` construction's `::findNearestEnemies` binding (GameEngine.kt:341) and `spawnOrbs`'s `getEnemies = ::getAliveEnemies` (873) stay — both methods are KEPT on the engine.

- [ ] **Step 8: Re-point GameEngineTest reflection (per-site migration table)**

Apply these re-points in `GameEngineTest.kt` (the reflection helpers around lines 1043–1208). Reach collaborators via these `@VisibleForTesting` getters — **add them to GameEngine** in this task:
```kotlin
    @get:androidx.annotation.VisibleForTesting internal val uwControllerForTest get() = uwController
    @get:androidx.annotation.VisibleForTesting internal val combatResolverForTest get() = combatResolver
    @get:androidx.annotation.VisibleForTesting internal val buffTickersForTest get() = buffTickers
```

| Test helper (line) | Old target | New target |
|---|---|---|
| `engineDeathHandler` / `simulateBasicKillCash` / R407 (1043,1205,1390,1413,1439) | `GameEngine.getDeclaredMethod("handleEnemyDeath", EnemyEntity)` | `eng.combatResolverForTest`, method `"handleEnemyDeath"` on `CombatResolver::class.java` |
| `invokeGetAliveEnemies` (1053) | `GameEngine.getDeclaredMethod("getAliveEnemies")` | **NO CHANGE** — `getAliveEnemies` is KEPT on the engine (Step 7 keep-list); reflection stays `GameEngine.getDeclaredMethod("getAliveEnemies")`. (The engine ALSO gains a public `aliveEnemies()` host wrapper delegating to it, but the test keeps targeting the original private name.) |
| **`RO11 uwCooldownMultiplier` test direct access (GameEngineTest.kt:168, 174)** | `eng1.uwStates[0]` / `eng2.uwStates[0]` (NON-reflective public-field read) | **`eng1.uwControllerForTest.uwStates[0]`** / `eng2.uwControllerForTest.uwStates[0]` — `GameEngine.uwStates` is deleted; `UWController.uwStates` is public and reached via the `uwControllerForTest` getter. **This is a direct Kotlin access, not reflection — it fails to COMPILE if missed (CRITICAL).** |
| `readEffectiveLevel` (1059) | `GameEngine` field `effectiveLevels` | **no change** (stays on engine) |
| `invokeTickRecovery` (1073) | `GameEngine.getDeclaredMethod("tickRecoveryPackages", Float)` | `eng.buffTickersForTest`, method `"tickRecovery"` on `BuffTickers::class.java` |
| `invokeTickRapidFire` (1086) | `GameEngine.getDeclaredMethod("tickRapidFire", Float)` | `eng.buffTickersForTest`, method `"tickRapidFire"` on `BuffTickers::class.java` |
| `setChronoActive` (1108/1112) | `GameEngine` fields `chronoActive`/`chronoSlowFactor` | `eng.uwControllerForTest`, fields on `UWController::class.java` |
| `readFortuneMultiplier` (1154) | `GameEngine` field `fortuneMultiplier` | `eng.uwControllerForTest`, field on `UWController::class.java` |
| `invokeUpdateUWs` (1178) | `GameEngine.getDeclaredMethod("updateUWs", Float)` | `eng.uwControllerForTest`, method `"update"` on `UWController::class.java` |
| `invokeOnProjectileHitEnemy` (1352) | `GameEngine.getDeclaredMethod("onProjectileHitEnemy", …)` | `eng.combatResolverForTest`, method `"onProjectileHitEnemy"` on `CombatResolver::class.java` |
| `flushPendingAdd` (1028/1029) | `GameEngine` fields `entities`/`pendingAdd` | **no change** (stay on engine) |
| `countWaveAnnouncements`/`readPendingFloatingTextSnippets` (1005/1365) | `EffectEngine` fields | **no change** |
| `setWavePhase` (1098) / `invokeOnMeleeHit` (1326) | `WaveSpawner` fields | **no change** (WaveSpawner unchanged) |

Example rewrite for `invokeUpdateUWs`:
```kotlin
    private fun invokeUpdateUWs(eng: GameEngine, deltaTime: Float) {
        val controller = eng.uwControllerForTest
        val method = UWController::class.java
            .getDeclaredMethod("update", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(controller, deltaTime)
    }
```
Example rewrite for `setChronoActive`:
```kotlin
    private fun setChronoActive(eng: GameEngine, active: Boolean) {
        val controller = eng.uwControllerForTest
        val field = UWController::class.java.getDeclaredField("chronoActive").apply { isAccessible = true }
        field.setBoolean(controller, active)
        if (active) {
            val sfField = UWController::class.java.getDeclaredField("chronoSlowFactor").apply { isAccessible = true }
            sfField.setFloat(controller, 0.10f)
        }
    }
```
Apply the analogous change to every row above. **`activateUW`/`initUWs`** in the test call the public `eng.activateUW(0)`/`eng.initUWs(...)` — **no change** (the engine still exposes them). **`ChronoOverlayPaintTest`** calls `engine.setChronoActiveForTest(true)` — **no change**: the engine keeps that `@VisibleForTesting` pass-through (Step 7 Step 7 re-points its body to `uwController.setChronoActiveForTest`, public signature preserved), so the Robolectric test compiles untouched.

- [ ] **Step 9: Build + full JVM suite (red-before-green oracle)**

Run: `./run-gradle.sh testDebugUnitTest > /tmp/t7.log 2>&1; tail -40 /tmp/t7.log`
Expected: BUILD SUCCESSFUL, all tests pass (incl. `GameEngineTest`, `ChronoOverlayPaintTest`, `GameEngineConcurrencyTest`, `EffectEngineConcurrencyTest`). If `GameEngineConcurrencyTest`/`EffectEngineConcurrencyTest` needed ANY edit to pass → STOP, the concurrency model changed (spec §10 red flag).

- [ ] **Step 10: Add the positive cash-delta false-green guard**

In the existing `RO11 cashResearchMultiplier scales kill cash` test (GameEngineTest.kt:138), assert against the variable **already bound** from the existing `simulateBasicKillCash` call (named `cashBaseline` at ~line 144) — do NOT add a new `val delta = simulateBasicKillCash(eng)` call (that would add a redundant third reflection invocation). Add, after line 144:
```kotlin
        assertTrue(cashBaseline > 0L, "kill must credit positive cash via host.simulation (false-green guard)")
```
This catches a misrouted credit (CombatResolver crediting a detached `Simulation` instead of `host.simulation`, which `eng.cash` reads) → it would yield a 0 delta and trip the guard. Do not remove existing asserts.

- [ ] **Step 11: Re-run suite + commit**

Run: `./run-gradle.sh testDebugUnitTest > /tmp/t7b.log 2>&1; tail -20 /tmp/t7b.log`
Expected: PASS.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt
git commit -m "refactor(battle): slim GameEngine to orchestrator+facade; wire collaborators (#230 #231)"
```

---

### Task 8: New focused unit tests for the collaborators (plain JVM lane)

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/UWControllerTest.kt`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BuffTickersTest.kt`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt`

These run on the plain JVM lane (no Robolectric; `isReturnDefaultValues=true` no-ops the stubbed Paint). Each uses a small fake host. They must NOT call any `render()`/`draw()` path. Below are starter tests proving the collaborator works against a fake host; add more as coverage allows but these are the minimum.

- [ ] **Step 1: Write `BuffTickersTest`**

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeStrings
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuffTickersTest {

    private class FakeBuffHost(
        override val ziggurat: ZigguratEntity?,
        override var wavePhase: WavePhase? = WavePhase.SPAWNING,
        private val levels: Map<UpgradeType, Int> = emptyMap(),
    ) : BuffHost {
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
        override val strings: Strings? = FakeStrings()
        override fun wsLevel(type: UpgradeType): Int = levels[type] ?: 0
    }

    private fun zig() = ZigguratEntity(1080f, 1920f, ResolvedStats(maxHealth = 1000.0), { emptyList() }) { _, _, _, _ -> }

    @Test
    fun `recovery heals during SPAWNING after one interval`() {
        val z = zig().apply { currentHp = 500.0 }
        val host = FakeBuffHost(z, WavePhase.SPAWNING, mapOf(UpgradeType.RECOVERY_PACKAGES to 10))
        val tickers = BuffTickers(host)
        // advance one full interval (30s) in one tick
        tickers.tickRecovery(30f)
        assertTrue(z.currentHp > 500.0, "recovery must heal after one interval during SPAWNING")
    }

    @Test
    fun `recovery does nothing during COOLDOWN`() {
        val z = zig().apply { currentHp = 500.0 }
        val host = FakeBuffHost(z, WavePhase.COOLDOWN, mapOf(UpgradeType.RECOVERY_PACKAGES to 10))
        val tickers = BuffTickers(host)
        tickers.tickRecovery(30f)
        assertEquals(500.0, z.currentHp, 0.001, "no heal outside SPAWNING")
    }

    @Test
    fun `rapid fire sets multiplier on burst and resets after duration`() {
        val z = zig()
        val host = FakeBuffHost(z, WavePhase.SPAWNING, mapOf(UpgradeType.RAPID_FIRE to 1))
        val tickers = BuffTickers(host)
        // fire a burst by crossing the interval
        tickers.tickRapidFire(1000f)
        assertTrue(z.rapidFireMultiplier > 1f, "burst sets the attack-speed multiplier")
    }
}
```

- [ ] **Step 2: Write `CombatResolverTest`**

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeStrings
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombatResolverTest {

    private class FakeCombatHost(
        override val ziggurat: ZigguratEntity?,
        override val simulation: Simulation = Simulation(),
    ) : CombatHost {
        override val currentStats = ResolvedStats()
        override val conditions = BattleConditionEffects()
        override val tier = 1
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
        override val soundManager: SoundManager? = null
        override val strings: Strings? = FakeStrings()
        override val reducedMotion = true
        override val fortuneMultiplier = 1.0
        override val cashResearchMultiplier = 1.0
        override val cashBonusPercent = 0.0
        override val secondWindHpPercent = 0.0
        private var swUsed = false
        override fun consumeSecondWind(): Boolean { if (swUsed) return false; swUsed = true; return true }
        val pending = mutableListOf<Entity>()
        override fun addPending(entity: Entity) { pending.add(entity) }
        override fun aliveEnemies(): List<EnemyEntity> = emptyList()
        override fun nearestEnemies(n: Int): List<EnemyEntity> = emptyList()
        override fun wsLevel(type: UpgradeType): Int = 0
        override fun applyLifesteal(healAmount: Double) {}
    }

    private fun zig() = ZigguratEntity(1080f, 1920f, ResolvedStats(maxHealth = 1000.0), { emptyList() }) { _, _, _, _ -> }

    @Test
    fun `handleEnemyDeath credits positive cash to the host simulation`() {
        val host = FakeCombatHost(zig())
        val resolver = CombatResolver(host)
        val before = host.simulation.cash
        val enemy = EnemyEntity(EnemyType.BASIC, 1.0, 1.0, 0f, 0.0, 0f, 0f) {}
        resolver.handleEnemyDeath(enemy)
        assertTrue(host.simulation.cash > before, "kill credits cash to the engine-owned simulation")
        assertEquals(1, host.simulation.totalEnemiesKilled)
    }

    @Test
    fun `handleWaveComplete credits base wave cash`() {
        val host = FakeCombatHost(zig())
        val resolver = CombatResolver(host)
        resolver.handleWaveComplete(1)
        assertEquals(20L, host.simulation.cash, "base wave cash with no upgrades = 20")
    }
}
```

> Verify the `ZigguratEntity` / `EnemyEntity` constructor signatures against the real classes when implementing — the constructor arg lists above are illustrative; match the actual ones (the existing `GameEngineTest` helpers `makeStationaryEnemy` / `freshEngine` show the real shapes). Adjust to compile.

- [ ] **Step 3: Write `UWControllerTest`**

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UWControllerTest {

    private class FakeUWHost(override val ziggurat: ZigguratEntity?) : UWHost {
        override val screenWidth = 1080f
        override val screenHeight = 1920f
        override val reducedMotion = true
        override val uwCooldownMultiplier = 1f
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
        override val soundManager: SoundManager? = null
        var appliedStats: ResolvedStats = ResolvedStats(damage = 10.0)
        override val currentStats: ResolvedStats get() = appliedStats
        override fun applyStats(stats: ResolvedStats) { appliedStats = stats }
        override fun aliveEnemies(): List<EnemyEntity> = emptyList()
    }

    private fun zig() = ZigguratEntity(1080f, 1920f, ResolvedStats(maxHealth = 1000.0), { emptyList() }) { _, _, _, _ -> }

    @Test
    fun `GOLDEN activation sets fortune multiplier and expiry resets it`() {
        val host = FakeUWHost(zig())
        val controller = UWController(host, Simulation())
        controller.initUWs(listOf(OwnedWeapon(UltimateWeaponType.GOLDEN_ZIGGURAT, damageLevel = 1, secondaryLevel = 1, cooldownLevel = 1, isUnlocked = true, isEquipped = true)))
        controller.activateUW(0)
        assertTrue(controller.fortuneMultiplier > 1.0, "GOLDEN activation raises fortune multiplier")
        // expire via a long update tick (effect duration is finite)
        controller.update(1000f)
        assertEquals(1.0, controller.fortuneMultiplier, 0.001, "GOLDEN expiry resets fortune to 1.0")
    }

    @Test
    fun `relayerBaseStats passes through unchanged when GOLDEN inactive`() {
        val host = FakeUWHost(zig())
        val controller = UWController(host, Simulation())
        val base = ResolvedStats(damage = 42.0)
        assertEquals(42.0, controller.relayerBaseStats(base).damage, 0.001)
    }
}
```

- [ ] **Step 4: Run the new tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "*UWControllerTest*" --tests "*BuffTickersTest*" --tests "*CombatResolverTest*" > /tmp/t8.log 2>&1; tail -40 /tmp/t8.log`
Expected: PASS. (Fix constructor-arg mismatches against the real entity classes if any compile error appears.)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/UWControllerTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/BuffTickersTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt
git commit -m "test(battle): focused unit tests for UWController/BuffTickers/CombatResolver (#231)"
```

---

### Task 9: Verify line counts + Paint-free engine

**Files:** none modified (verification only).

- [ ] **Step 1: Check every new collaborator is < 400 lines and the engine shrank**

Run:
```bash
wc -l app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/{GameEngine,BattleRenderer,UWController,BuffTickers,CombatResolver,BattleHosts}.kt
```
Expected: `BattleRenderer`, `UWController`, `BuffTickers`, `CombatResolver`, `BattleHosts` each **< 400**; `GameEngine` substantially reduced (target ~500–560 per spec §2 — record the actual number).

- [ ] **Step 2: Confirm GameEngine has zero Paint fields**

Run: `rg -n "android.graphics.Paint" app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt`
Expected: **no matches** (all Paint is in `BattleRenderer`).

- [ ] **Step 3: Record the numbers** (used in Task 10 docs). No commit (verification only). If a collaborator is ≥ 400 lines, split it further before proceeding (e.g. move `activateUW`'s visual-effect block to a private helper).

---

### Task 10: Sync current-state docs (PR Task-List Convention — BEFORE STATE/RUN_LOG)

**Files:**
- Modify: `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md` (add Phase 4)
- Modify: `CLAUDE.md` (Battle Renderer section + headline test count)
- Modify: `docs/steering/source-files.md` (new files)
- Modify: `docs/steering/structure.md` (new modules under presentation/battle/engine)
- Modify: `CHANGELOG.md` (`[Unreleased]` entry + test count)

- [ ] **Step 1: Add Phase 4 to ADR-0012**

Append a `**Phase 4 (#230/#231, COMPLETE — 2026-06-22):**` section documenting: the presentation collaborator split (BattleRenderer/UWController/BuffTickers/CombatResolver + the three host interfaces), the pure cash-formula hoist to SimulationMath, GameEngine as sole lock owner / façade, and the **explicit remaining slice**: UW *effect resolution* (the `when(type)` damage/pull/DoT bodies) + `applyDamageToZiggurat`/`applyThorn` HP mutation are NOT hoisted to the domain because `EntityProtocol` exposes no `takeDamage`/`currentHp`/`applyKnockback` — a future domain slice gated on entity-model surgery.

- [ ] **Step 2: Update CLAUDE.md**

In the "Battle Renderer" section, add the collaborator structure (GameEngine = orchestrator + lock owner + façade; BattleRenderer = Canvas/Paint; UWController = UW lifecycle; BuffTickers = buff timers; CombatResolver = combat/death). Update the headline test count line (current `1196 JVM tests` → new count from Task 8: 1196 + the cash-formula tests (Task 1) + the new collaborator tests (Task 8); record the exact figure from the green run). Update the `presentation/battle/engine/` directory tree comment to list the new files.

- [ ] **Step 3: Update source-files.md + structure.md**

Add entries for `BattleHosts.kt`, `BattleRenderer.kt`, `UWController.kt`, `BuffTickers.kt`, `CombatResolver.kt`; update the `GameEngine.kt` entry's responsibility shape (now orchestrator/façade). Update structure.md's battle-engine module description.

- [ ] **Step 4: Update CHANGELOG.md**

Add a `[Unreleased]` entry: "refactor(battle): GameEngine god-class decomposition (#230 #231) — extracted BattleRenderer/UWController/BuffTickers/CombatResolver + host interfaces; hoisted pure cash formulas to SimulationMath; behavior-preserving, +N JVM tests." Update the current-state test-count block.

- [ ] **Step 5: Run the full gate before committing docs**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t10.log 2>&1; tail -30 /tmp/t10.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit docs**

```bash
git add CLAUDE.md CHANGELOG.md docs/agent/DECISIONS/ADR-0012-simulation-extraction.md \
        docs/steering/source-files.md docs/steering/structure.md
git commit -m "docs: sync for GameEngine decomposition (ADR-0012 Phase 4) (#230 #231)"
```

---

### Task 11: Update STATE.md + RUN_LOG.md

**Files:**
- Modify: `docs/agent/STATE.md`
- Modify: `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Update STATE.md**

Set the current objective to DONE for #230/#231; note the new file structure; update the headline test count; move #230/#231 out of the remaining-majors list. Keep it to one page.

- [ ] **Step 2: Append RUN_LOG.md**

Add a dated entry summarizing the decomposition: spec+plan through the Adversarial Review Gate (spec 37→24 surviving), the four collaborators + host seam, the #230 cash-formula hoist, behavior-preservation via the existing corpus + sacrosanct concurrency tests, and the explicit deferred domain slice (UW effect resolution).

- [ ] **Step 3: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(state): checkpoint GameEngine decomposition (#230 #231)"
```

---

### Task 12: Final gate + push

- [ ] **Step 1: Run the full build gate one final time**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t12.log 2>&1; tail -30 /tmp/t12.log`
Expected: BUILD SUCCESSFUL; 0 failures.

- [ ] **Step 2: Confirm concurrency guards passed unchanged**

Run: `git diff main -- app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineConcurrencyTest.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngineConcurrencyTest.kt`
Expected: **empty diff** (both concurrency tests untouched). If non-empty → the concurrency model changed; STOP and reassess (spec §10).

- [ ] **Step 3: Push + open PR**

```bash
git push -u origin refactor/230-231-gameengine-decomposition
gh pr create --title "refactor(battle): GameEngine god-class decomposition (#230 #231)" --body "<summary + findings + closeability note for #230 per spec §11>"
```

---

## Self-review checklist (run before execution)

- **Spec coverage:** §5.A→Task 3; §5.B→Task 6; §5.C→Task 4; §5.D→Task 5; §5.E→Task 7; §6 hosts→Task 2; §7 hoist→Task 1; §9 tests→Tasks 7-8; §11 line-count→Task 9; docs→Tasks 10-11. ✓
- **Behavior-preservation:** every method moved verbatim with mechanical reference rewrites only; concurrency tests sacrosanct (Task 12 Step 2). ✓
- **Type consistency:** `UWController.UWState` used in `uwSnapshot()` return type (Task 6 note + Task 7 Step 7); `tickRecovery` (not `tickRecoveryPackages`) used consistently (Tasks 4, 7, 8); `relayerBaseStats` defined Task 6, called Task 7 Step 4; `consumeSecondWind`/`secondWindHpPercent` defined Task 2, used Task 5 + implemented Task 7 Step 2. ✓
- **The one open implementation detail** flagged inline: the host-member field-name clashes (`tier`/`conditions`) — resolved in Task 7 Step 2 with a chosen rename approach. The collaborator unit-test constructor arg lists (Task 8) are flagged as "match the real signatures" since `ZigguratEntity`/`EnemyEntity` ctors must be verified at implementation time.
