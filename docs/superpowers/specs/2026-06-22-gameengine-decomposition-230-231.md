# Design Spec — GameEngine god-class decomposition (#230 + #231)

**Date:** 2026-06-22
**Issues:** #230 (ADR-0012 extraction only partial — GameEngine still a 1233-line complexity hotspot),
#231 (1233-line god class mixing simulation orchestration, rendering, UW lifecycle, cash economy,
targeting, Paint allocation)
**Status:** Draft — pending Adversarial Review Gate (spec stage)
**Scope decision (developer):** ONE comprehensive PR; **Hybrid** decomposition; GameEngine stays sole
lock owner.

---

## 1. Problem

`presentation/battle/engine/GameEngine.kt` is **1233 lines** — the single highest-churn,
hardest-to-reason-about file in the app (next largest is 720). ADR-0012's V1X-09 extraction was real
and correct: it moved the *pure* in-round simulation (cash state, round counters, chrono-aware entity
tick, collision sweep, UW timer arithmetic, the `SimulationEvent` flow) into the pure-domain
`domain/battle/engine/Simulation` + `SimulationMath`, and per-entity sim state into
`domain/battle/entity/*State`. But it pulled out only *part* of the engine. What remains tangled in one
Canvas-coupled class:

- **Rendering + Paint allocation** — `render(canvas)` body + three `Paint` fields.
- **UW lifecycle** — `uwStates`, `UWState`, `activateUW`, `updateUWs`, `initUWs`, `uwSnapshot`,
  `resetUWCooldowns`, and the per-UW flags/multipliers (`chronoActive`, `chronoSlowFactor`,
  `goldenZigActive`, `preGoldenStats`, `goldenDamageMult`, `fortuneMultiplier`).
- **Per-mechanic buff timers** — `recoveryTimer`, `rapidFireTimer`, `rapidFireActiveRemaining`,
  `lifestealAccumulator` + `tickRecoveryPackages`, `tickRapidFire`, `applyLifesteal`.
- **Combat resolution / targeting / death** — `onProjectileHitEnemy`, `onOrbHitEnemy`,
  `applyDamageToZiggurat`, `applyThorn`, `findNearestEnemies`, `getAliveEnemies`, `handleEnemyDeath`,
  `handleWaveComplete`.
- **Inline pure reward formulas** — per-kill cash, wave-complete cash (the #230 hoist target).

Every mechanic threads its own mutable timer/multiplier field through the same class, so a change to one
forces re-reading the whole `entitiesLock` concurrency contract. This is the cost #230/#231 describe.

## 2. Goal & non-goals

**Goal:** decompose `GameEngine` into focused collaborators, each with one clear responsibility,
**strictly behavior-preserving**, satisfying both #230 (domain hoist of pure formulas + an explicit
tracker for the entity-coupled remainder) and #231 (presentation collaborators + `BattleRenderer` for
Canvas/Paint).

**Line-count targets (corrected after spec review).** Each *new* collaborator file
(`BattleRenderer` / `UWController` / `BuffTickers` / `CombatResolver`) must be **< 400 lines** — that is
the achievable, enforced goal and the substance of #231 ("sub-400-line files per responsibility"). The
**slimmed `GameEngine` is NOT held to < 400**: a dry-run accounting of the §5.E kept members
(≈281 code + ≈211 load-bearing KDoc/concurrency comments + blanks, *before* the render shell, the four
collaborator fields + construction, and the three host-interface impls) lands the engine at **~500–560
lines**. The #118/#191 concurrency comments and kept-field KDoc are load-bearing (CLAUDE.md treats them
so) and the §4 "move, don't rewrite" rule forbids trimming them — so sub-400 on the engine is
unattainable without a forbidden rewrite. **The engine target is therefore "as small as the kept
responsibilities allow (~500–560 lines), every other concern lifted into a < 400-line collaborator"** —
the win is single-responsibility files, not an arbitrary engine line count. The plan pins the measured
post-split engine count via a trial extraction.

**Non-goals (explicitly out of scope, tracked as future ADR-0012 slices):**
- Hoisting UW *effect resolution* (the `when(type)` damage/pull/DoT bodies) into the domain. They call
  `EnemyEntity.takeDamage`/`applyKnockback`/`currentHp` and `ZigguratEntity.currentHp`, none of which
  `EntityProtocol` exposes — a domain hoist needs entity-model surgery (extend `EntityProtocol` / new
  domain ports), a separate large refactor with its own regression surface.
- Hoisting `applyDamageToZiggurat`/`applyThorn` HP mutation (same reason — touches
  `ZigguratEntity.currentHp` + `EffectEngine` screen-shake).
- Any change to game balance, economy formulas, the GDD, or the database.

## 3. Hard invariants (must survive untouched)

These are the behavior-preservation contract. Any plan step that would change one of these is a stop.

1. **Thread-safety (#118 / #191).** `GameEngine` remains the **sole owner** of `entitiesLock` and
   continues to hold it across the whole `update()` tick. `entities` / `pendingAdd` / `uwStates` stay
   engine-owned and never escape the engine. The acyclic lock order **`entitiesLock` (outer) →
   `effectsLock` (inner)** is unchanged; `EffectEngine` keeps its own `effectsLock`. Collaborators
   contain **no `synchronized` block of their own** — they are pure logic holders invoked from inside
   the already-held lock (from `update()`), or from the main-thread paths the engine already wraps
   (`init`, `applyStats` orb-reconcile, `initUWs`, `uwSnapshot`, `aliveEnemyCount`). Every `@Volatile`
   field keeps its volatility (and its current owner — see §6 for where volatiles land).
2. **Public API.** Every member that `BattleViewModel`, `GameSurfaceView`, `GameLoopThread`, and
   `UltimateWeaponBar` touch keeps its exact signature **on `GameEngine`** (the engine stays the
   façade). Code-grounded list of consumer touch-points:
   - `BattleViewModel`: `engine.init`, `setStats`, `initUWs`, `events`, `cash`, `aliveEnemyCount()`,
     `uwSnapshot()`, `activateUW`, `updateZigguratStats`, `updateEffectiveLevels`,
     `uwCooldownMultiplier`, `cashResearchMultiplier`, `enemyIntelLevel`, `cashBonusPercent`,
     `secondWindHpPercent`, `cosmeticOverrides`, `effectEngine`, `strings`.
   - `GameSurfaceView`: `engine.init`, `soundManager`, `strings`, `hasWaveProgress()`, plus the loop
     thread's `update(dt)` / `render(canvas)`.
   - `UltimateWeaponBar`: no direct engine reference (driven by `uiState.uwSlots`) — unaffected.
3. **Test contract.** `GameEngineTest` (1450 lines) reflectively pokes private fields/methods that are
   moving (`entities`, `pendingAdd`, `handleEnemyDeath`, `getAliveEnemies`, `effectiveLevels`,
   `tickRecoveryPackages`, `tickRapidFire`, `chronoActive`, `chronoSlowFactor`, `fortuneMultiplier`,
   `updateUWs`, `onProjectileHitEnemy`). These reflection paths **will** break; the plan re-points them
   (via `@VisibleForTesting` collaborator getters on the engine) or converts to direct collaborator
   tests — **assertions are never weakened, only the plumbing that reaches the code is changed**,
   red-before-green per move.
4. **Concurrency guards sacrosanct.** `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` must
   pass **unchanged**. If either needs editing, that is a red flag that the concurrency model changed →
   stop and reassess.

## 4. Guiding principle

**Composition without behavior change.** Move code, do not rewrite it. Each collaborator is constructed
by the engine, reaches engine-owned shared state through a **narrow host interface** the engine
implements (no back-pointer to the concrete `GameEngine`, no circular type dependency), and is called at
the exact point the inline code ran, in the exact order. The engine becomes a thin orchestrator + lock
owner + façade.

## 5. The collaborators

All new files in `presentation/battle/engine/` unless noted.

### A. `BattleRenderer` — Canvas + Paint (#231 "Paint allocation")
Owns the three `Paint` fields (`chronoOverlayPaint`, `hpPercentPaint`, `bossCountdownPaint`) and the
body of `render(canvas)`: background draw, the entity snapshot draw, effects render, chrono overlay,
screen-shake apply/restore, ENEMY_INTEL L5+ per-enemy HP-% labels, health bar, ENEMY_INTEL L10 boss
countdown. **All `Paint` allocation leaves `GameEngine`.**

`GameEngine.render(canvas)` becomes: take the under-lock entity snapshot (`synchronized(entitiesLock) {
entities.toList() }` — lock stays in the engine), then call
`battleRenderer.render(canvas, snapshot, …)` passing the per-frame reads it needs (enemyIntelLevel,
chronoActive, the boss/composition labels, the ziggurat HP). Pure draw logic; mutates nothing shared.
The A31 cached-Paint property moves into `BattleRenderer`. The `setChronoActiveForTest` seam (a
`@VisibleForTesting` writer of `chronoActive`) stays a pass-through **on `GameEngine`** that delegates
the write to `UWController` (the new owner of `chronoActive` per §5.B) — it does **not** re-point to the
renderer, which only *reads* `chronoActive` as a per-frame parameter and owns no such field. (Review
corrected the evidence: this seam's actual consumer is `ChronoOverlayPaintTest:28`, not `GameEngineTest`
— `GameEngineTest` reaches `chronoActive`/`chronoSlowFactor` via its own separate field-reflection
helper; both paths are enumerated in the plan's reflection-migration step.)

### B. `UWController` — Ultimate Weapon lifecycle (#231 "UW lifecycle state machine")
Owns `uwStates`, the `UWState` data class, and the UW flags/multipliers: `chronoActive`,
`chronoSlowFactor`, `goldenZigActive`, `preGoldenStats`, `goldenDamageMult`, `fortuneMultiplier`.
Methods: `initUWs`, `uwSnapshot`, `activateUW`, `updateUWs`, `resetUWCooldowns`, plus a
**`resetRoundState()`** entry point (see below). Delegates timer arithmetic to the **unchanged**
`Simulation.advanceUWTimers` / `isUWReadyToFire`.

**`init()`-reset ownership (review fix).** `GameEngine.init()` today resets these UW fields inline
(`fortuneMultiplier = 1.0`; `chronoActive = false`; `chronoSlowFactor = 1f`; `goldenZigActive = false`;
`preGoldenStats = null`; `goldenDamageMult = 1.0`; `uwStates.clear()` — GameEngine.kt:322–324) on the
**main thread, inside `synchronized(entitiesLock)`**. After the move, those fields live in `UWController`,
so the engine's `init()` must call `uwController.resetRoundState()` (which performs the same field
resets) **from inside the engine's held `entitiesLock`**. This reset is NOT folded into `initUWs`
(`initUWs` is a separate VM call that populates `uwStates` from equipped weapons; the field reset is a
distinct round-start concern). `UWController` itself takes no monitor — the caller (engine `init`/`update`)
holds the lock (§3.1).

**`Simulation` access.** `updateUWs` calls `Simulation.advanceUWTimers` / `isUWReadyToFire`. Those are
**pure, stateless** helpers (read no `Simulation` instance state — Simulation.kt:233–253). `UWController`
reaches them by being handed the engine-owned `Simulation` **via its constructor** (chosen route; the
single instance the engine already owns at GameEngine.kt:115). `UWHost` therefore does NOT need a
`simulation` member.

Reads the engine can't give up are exposed back to `UWController` via a `UWHost` interface (§6).
`chronoActive` / `chronoSlowFactor` are read by the engine's tick (`simulation.tickEntities` slow
factor) and the renderer → exposed as reads on `UWController`. `fortuneMultiplier` is read by the cash
formulas (CombatResolver) → exposed as a read.

**Highest-scrutiny zone:** the GOLDEN_ZIGGURAT #119 re-layering. `activateUW` captures
`preGoldenStats = stats` and applies `stats.copy(damage = stats.damage * dmgMult)`;
`updateZigguratStats` (engine, main thread) re-captures `preGoldenStats` to the new base and re-applies
`goldenDamageMult` when GOLDEN is active; `updateUWs` expiry restores `preGoldenStats`. After the split,
`goldenZigActive`/`preGoldenStats`/`goldenDamageMult` live in `UWController` but `updateZigguratStats`
(engine façade) must read/write them — encapsulated in a single `UWController.onBaseStatsChanged(newStats)`
method the engine calls. The `effectWasActive` / `justExpired` semantics from `advanceUWTimers` (ongoing
effect runs one final time on the expiry frame) must be preserved exactly.

**Pre-existing race — must NOT be widened (review fix).** `updateZigguratStats` runs on the **main
thread WITHOUT `entitiesLock`** today (GameEngine.kt:392–399; `applyStats` only locks in the
orbCount-changed branch, which a damage-only purchase skips), while the GOLDEN expiry in `updateUWs`
runs on the **loop thread under `entitiesLock`**. The GOLDEN trio is read/written from both — so it is
**not** thread-confined; this is a known, pre-existing main-vs-loop race that the refactor must
**preserve exactly, not enlarge**. Therefore `onBaseStatsChanged` must remain a single adjacent
read-modify-write of the trio (check `goldenZigActive` → set `preGoldenStats` → `host.applyStats(copy)`)
with **no host round-trips that re-read the trio mid-sequence** and must keep the same (lock-free,
main-thread) access shape `updateZigguratStats` has today — do not silently add a lock, an
`entitiesLock` acquisition, or a single-thread assertion. `GameEngineTest` R119 is single-threaded and
will not catch a cross-thread desync; the guard for this path is the no-widening invariant, not a test.

### C. `BuffTickers` — per-mechanic timers (#231 "per-mechanic timers")
Owns `recoveryTimer`, `rapidFireTimer`, `rapidFireActiveRemaining`, `lifestealAccumulator`. Methods:
`tickRecovery(dt)` (renamed from `tickRecoveryPackages`), `tickRapidFire(dt)`,
`applyLifesteal(healAmount)`. Reads ziggurat, wave phase, effect engine, strings, and the level map via
a `BuffHost` interface. The "reset-to-zero-during-COOLDOWN-phase" behavior and the L10 rapid-fire
seamless-transition ordering are preserved verbatim.

### D. `CombatResolver` — damage / targeting / death
Owns the methods `onProjectileHitEnemy`, `onOrbHitEnemy`, `applyDamageToZiggurat`, `applyThorn`,
`handleEnemyDeath`, `handleWaveComplete`. **Owns the fields** `calculateDamage = CalculateDamage()` and
`calculateDefense = CalculateDefense()` (GameEngine.kt:74–75 — their only callers are
`onProjectileHitEnemy:1050` / `applyDamageToZiggurat:1091`, both moving here; constructed inside
CombatResolver with the production default `Random`, so the §3.3 test contract is unaffected — no host
member needed). Computes reward amounts via the new `SimulationMath` formulas (§7) then runs the
entity/effect side effects exactly as before (`host.simulation.creditCash`, sound, `DeathEffect`,
`FloatingText`, `SimulationEvent.emit`, SCATTER child spawn into `pendingAdd` via `host.addPending`).
Reaches engine state via a `CombatHost` interface.

**Second-wind / death-defy (review fix).** `applyDamageToZiggurat` (GameEngine.kt:1092–1099) reads
`secondWindHpPercent` and performs a one-shot test-and-set on `secondWindUsed`
(`if (… && !secondWindUsed …) { secondWindUsed = true; … }`). Both are `@Volatile` fields the VM writes,
kept on `GameEngine` per §6. `deathDefyChance` is read off `stats` (covered by `currentStats`). So
`CombatHost` must expose: `val secondWindHpPercent: Double` (read) and **`fun consumeSecondWind(): Boolean`**
— an atomic test-and-set on the engine's `secondWindUsed` (returns `true` exactly once, then `false`),
preserving the `!secondWindUsed → secondWindUsed = true` semantics. A read-only getter is insufficient
(it's a write-back).

**Targeting & three distinct `entities` queries.** Three different queries iterate engine-owned
`entities`; to honor "`entities` never leaves the engine", the engine keeps the iteration and exposes
each through the host (the #125 no-cache hot-path contract — the engine re-derives live each call):
1. `aliveEnemies()` — all live enemies (was `getAliveEnemies`, GameEngine.kt:893).
2. `nearestEnemies(n)` — live enemies within `zig.attackRange`, sorted by distance **from the ziggurat**
   (was `findNearestEnemies`, GameEngine.kt:1155–1163; range-gating preserved verbatim). This stays
   bound into `ZigguratEntity` via `::findNearestEnemies` at the engine's `init` (GameEngine.kt:341) —
   it does **not** move to CombatResolver, so no circular construction is introduced.
3. **Bounce-target** (GameEngine.kt:1073–1076) — a THIRD, distinct query: live enemies `!in
   proj.hitEnemies`, `minByOrNull { distance from the just-hit enemy }`, **no `attackRange` gate**. This
   is NOT `nearestEnemies(n)` (different ordering origin + no range filter). CombatResolver obtains
   candidates via `host.aliveEnemies()` and applies the `!in hitEnemies` + min-by-distance-from-hit-enemy
   filter itself.

### E. `GameEngine` (slimmed) — orchestrator + lock owner + façade (target ~500–560 lines — see §2)
Keeps: `entities` / `pendingAdd` / `entitiesLock`, the A28 scratch buffers, `init`, `update` (the locked
tick sequencing the collaborators), `applyStats` / `updateZigguratStats` (stats-mutation point + orb
reconcile — both touch `entities` under lock), `spawnOrbs`, **`addEntity(entity)`** (public test seam
that writes engine-owned `pendingAdd`, GameEngine.kt:587 → :49; stays public for GameEngineTest's 14
direct call sites), the wave-announcement triggers (`triggerWaveAnnouncement`, `nextWaveCompositionLabel`,
`bossCountdownLabel`), `aliveEnemyCount()`, the engine-owned host-impl query methods (`aliveEnemies()`,
`nearestEnemies(n)`, `wsLevel(type)`, `effectiveLevels` field), and the delegating façade getters/fields
(`cash`, `totalCashEarned`, `events`, `hasWaveProgress`, `spendCash`, `setStats`,
`updateEffectiveLevels`, plus the `@Volatile` VM-written fields). Implements `UWHost` / `BuffHost` /
`CombatHost`. Constructs the four collaborators as fields (handing `UWController` the engine-owned
`Simulation`; handing each collaborator its host = `this`).

## 6. Host-interface seam & where `@Volatile` fields land

Each collaborator declares a narrow host interface; `GameEngine` implements all of them. This keeps
collaborators free of a concrete-engine back-pointer (isolated, fakeable in tests) and avoids a circular
type dependency.

```
interface UWHost {            // implemented by GameEngine
  val ziggurat: ZigguratEntity?
  val screenWidth: Float; val screenHeight: Float
  val reducedMotion: Boolean
  val uwCooldownMultiplier: Float
  val effectEngine: EffectEngine?
  val soundManager: SoundManager?
  val currentStats: ResolvedStats
  fun applyStats(stats: ResolvedStats)
  fun aliveEnemies(): List<EnemyEntity>   // #125 no-cache contract — see note below
  // NOTE: no `simulation` member — UWController gets the engine-owned Simulation via its
  // constructor (advanceUWTimers / isUWReadyToFire are pure stateless helpers). See §5.B.
}
interface BuffHost {
  val ziggurat: ZigguratEntity?
  val wavePhase: WavePhase?       // from waveSpawner
  val effectEngine: EffectEngine?
  val strings: Strings?
  fun wsLevel(type: UpgradeType): Int
}
interface CombatHost {
  val ziggurat: ZigguratEntity?
  val currentStats: ResolvedStats
  val conditions: BattleConditionEffects
  val tier: Int
  val effectEngine: EffectEngine?; val soundManager: SoundManager?; val strings: Strings?
  val reducedMotion: Boolean
  val simulation: Simulation
  val fortuneMultiplier: Double   // read from UWController
  val cashResearchMultiplier: Double; val cashBonusPercent: Double
  val secondWindHpPercent: Double           // read (applyDamageToZiggurat, GameEngine.kt:1097)
  fun consumeSecondWind(): Boolean          // one-shot test-and-set on engine's secondWindUsed
  fun addPending(entity: Entity)
  fun aliveEnemies(): List<EnemyEntity>     // #125 no-cache contract — see note below
  fun nearestEnemies(n: Int): List<EnemyEntity>
  fun wsLevel(type: UpgradeType): Int
  fun applyLifesteal(healAmount: Double)    // delegates to BuffTickers
}
// CONTRACT on aliveEnemies() (declared in two hosts, single engine impl): the host impl is
// UNSYNCHRONIZED and re-derives the list LIVE on every call — NEVER cache/snapshot it (#125: a
// stale list lets an ongoing UW effect re-hit a corpse and double-credit the kill). Caller holds
// entitiesLock (or is the single-threaded test / main-thread activateUW path). Do NOT add a
// `synchronized(entitiesLock)` inside the host impl — getAliveEnemies is a #125 60fps×4 hot path
// and an extra reentrant acquisition per call would deviate from move-don't-rewrite.
```

**Field placement & memory semantics (corrected after review).** The eight VM-written fields
(`cashResearchMultiplier`, `uwCooldownMultiplier`, `enemyIntelLevel`, `cashBonusPercent`,
`secondWindHpPercent`, `secondWindUsed`, `cosmeticOverrides`, `roundOver`) are `@Volatile` and **stay on
`GameEngine`** (the façade the VM writes). The UW fields (`fortuneMultiplier`, `chronoActive`,
`chronoSlowFactor`, `goldenZigActive`, `preGoldenStats`, `goldenDamageMult`) are today **plain
non-`@Volatile` vars** (verified: GameEngine.kt:169/267/275/276/277/287) and move to `UWController`
**staying plain `var`**.

The reason they need no `@Volatile` is **lock-provided happens-before, NOT thread-confinement** — the
earlier "loop-thread-confined" framing was *factually wrong*: these fields are also written on the
**main thread** in `init()` (GameEngine.kt:322/324, under `entitiesLock`), and `chronoActive`/
`chronoSlowFactor` are read by `render()` on the loop thread. Every write (loop-thread tick + main-thread
`init`) and every read happens under the **same `entitiesLock`**, which supplies the visibility edge.
**Consequences the plan must hold:**
- The `init()`-path reset of the UW fields must stay **under `entitiesLock`** post-move (via
  `uwController.resetRoundState()` called from the engine's locked `init`; §5.B).
- The plan must **not** "optimize" any of these into a single-thread assumption (no
  same-thread assertion, no dropping the lock) on the strength of the (false) confinement story.
- **Exception — the GOLDEN trio's `updateZigguratStats` path is lock-FREE today** (main thread, no
  `entitiesLock` — GameEngine.kt:392–399) and races the loop-thread expiry. That is a pre-existing race;
  `onBaseStatsChanged` must **preserve that exact lock-free shape, not widen it** (§5.B highest-scrutiny
  zone). Do not add a lock there either — adding one would change behavior/timing, not just "fix" a race
  the corpus doesn't exercise.

## 7. The #230 domain hoist (pure formulas → `SimulationMath`)

Hoist **only the arithmetic**, not the side effects.

New pure functions in `domain/battle/engine/SimulationMath`:
- `killCashReward(baseCash: Long, tierMultiplier: Double, cashBonusLevel: Int, fortuneMultiplier: Double,
  cashBonusPercent: Double, cashResearchMultiplier: Double): Long` — the `handleEnemyDeath` formula
  (current lines ~1167–1174): `(baseCash * tierMult * (1 + cashBonusLevel*0.03) * fortune *
  (1 + cashBonusPercent/100) * cashResearch).toLong()`.
- `waveCompleteCash(cashPerWaveLevel: Int, fortuneMultiplier: Double, cashResearchMultiplier: Double):
  Long` — the `handleWaveComplete` formula (current lines ~1039–1040): `((BASE_CASH_PER_WAVE +
  cashPerWaveLevel * FLAT_BONUS_PER_WAVE_LEVEL) * fortune * cashResearch).toLong()`.

The `BASE_CASH_PER_WAVE = 20`, `FLAT_BONUS_PER_WAVE_LEVEL = 5`, `CASH_BONUS` per-level `0.03`, and the
`cashBonusPercent / 100.0` divisor become **named constants in `SimulationMath`** (moved from the
`GameEngine` companion). CombatResolver calls these for the amount, then runs the unchanged side effects.
This is the #230 payoff: the formulas become pure-JVM testable without Robolectric.

Everything entity/effect-coupled (the `simulation.creditCash` call, sounds, particles, floating text,
`SimulationEvent` emits, SCATTER spawn) stays in CombatResolver and is **tracked in the updated
ADR-0012** as the remaining (entity-coupled) extraction slice — satisfying #230's "track remaining
slices explicitly so the file shrinks toward the documented intent."

## 8. Data flow per tick (unchanged order, just delegated)

`GameEngine.update(dt)`:
1. `if (roundOver) return`; `zig ?: return`.
2. `synchronized(entitiesLock) {`
   - `simulation.tickElapsed(dt)`; `backgroundRenderer?.update(dt)`; `effectEngine?.update(dt)`
   - `uwController.update(dt)`  *(was `updateUWs`)*
   - `buffTickers.tickRecovery(dt)`; `buffTickers.tickRapidFire(dt)`  *(`tickRecovery` was
     `tickRecoveryPackages` — the canonical new name is `tickRecovery`; the reflection table re-points it)*
   - wave-change announce (`triggerWaveAnnouncement`)
   - `waveSpawner?.update(...)`; `entities.addAll(pendingAdd); pendingAdd.clear()`
   - `simulation.tickEntities(entities, dt, if (uwController.chronoActive) uwController.chronoSlowFactor else 1f)`
   - projectile trails (`advanceTrail`) — unchanged, loop-thread-only under lock
   - A28 scratch partition + `CollisionSystem.checkCollisions(...)` with `combatResolver`'s callbacks
   - `entities.removeAll { !it.isAlive }`
   - `}`
3. `if (zig.currentHp <= 0.0) { roundOver = true; soundManager?.play(ROUND_END) }`

`GameEngine.render(canvas)`: under-lock snapshot → `battleRenderer.render(canvas, snapshot, frameState)`.

## 9. Testing strategy

1. **Existing corpus is the behavior oracle.** No behavior added → every existing test stays green or
   is deliberately re-pointed (never weakened).
2. **Reflection migration** of `GameEngineTest` — the main test work. The plan MUST carry a **per-site
   migration table** enumerating, for each reflection site, (a) destination class, (b) how reached, and
   (c) whether the member is **renamed**. Three groups (review-corrected — do NOT blanket "all break"):
   - **Moves + RENAMED on a collaborator/engine:** `updateUWs`→`UWController.update`,
     `tickRecoveryPackages`→`BuffTickers.tickRecovery` (resolve the §C "tickRecoveryPackages" vs §8
     "tickRecovery" naming — pick one, pin it), `getAliveEnemies`→engine `aliveEnemies()` (re-point to
     the **engine**'s renamed method, NOT CombatResolver, which delegates back via `host.aliveEnemies()`).
   - **Moves, name-identical on a collaborator** (reach via `@VisibleForTesting engine.<collaborator>ForTest`):
     `chronoActive`/`chronoSlowFactor`/`fortuneMultiplier`/`tickRapidFire` → `UWController`/`BuffTickers`;
     `handleEnemyDeath`/`onProjectileHitEnemy` → `CombatResolver`. Plus `setChronoActiveForTest`'s two
     consumers: `ChronoOverlayPaintTest:28` (engine pass-through) + `GameEngineTest`'s own
     `setChronoActive` field-reflection helper (re-point to `UWControllerForTest`).
   - **Stays on the engine — NO change:** `entities`, `pendingAdd`, `effectiveLevels` (and `addEntity`,
     a direct non-reflective public seam). Do not re-point these.
   Red-before-green per move; no assertion weakened, only the plumbing that reaches the code.
3. **New focused unit tests** (the win — previously trapped behind Robolectric reflection):
   - `SimulationMathTest` += `killCashReward` / `waveCompleteCash` cases (pure JVM).
   - `UWControllerTest`, `BuffTickersTest`, `CombatResolverTest` against fake hosts. These run on the
     **plain JVM lane** (`unitTests.isReturnDefaultValues = true`, app/build.gradle.kts:198) exactly as
     `GameEngineTest` does today — plain JUnit Jupiter, **no Robolectric**: the stubbed `Paint()` is a
     no-op and the combat/UW/buff logic paths never read `Paint`. **Caveat:** a fake-host test must NOT
     invoke a `render()`/`draw()` path (those read `Paint` → NPE under the default stub); collaborators
     A–D expose no such path to the logic under test.
4. **Cash-credit false-green guard.** After re-pointing the cash-delta tests, add/keep a standalone
   **positive-delta** assertion on `simulateBasicKillCash` (delta > 0) so a misrouted credit
   (CombatResolver crediting its own `Simulation` instead of `host.simulation`, the one `GameEngine.cash`
   reads) fails loudly instead of yielding a false-green 0-delta. (R146/A28 already carry `> before`
   guards; RO11 compares two deltas — but `simulateBasicKillCash` itself lacks a standalone one.)
5. **Concurrency guards sacrosanct** — `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` pass
   unchanged (verified: both use only public API, zero reflection on moving members). The §10 red-flag
   rule (stop if they need editing) is the guard that the lock model survived.
6. **Instrumented** `BattleSurfaceLifecycleTest` stays valid via public-API preservation.
7. **Build gate:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; full
   JVM suite green; headline test count updated.

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Concurrency model silently changes | Engine stays sole lock owner; collaborators have no monitor; concurrency tests must pass unchanged (stop if they don't). |
| GOLDEN_ZIGGURAT #119 re-layer breaks | Highest-scrutiny zone (§5.B); `onBaseStatsChanged` keeps the re-capture+re-apply as one adjacent read-modify-write; existing `GameEngineTest` GOLDEN cases re-pointed, not weakened. |
| GOLDEN trio main-vs-loop race widened | Pre-existing lock-free race (`updateZigguratStats`, GameEngine.kt:392–399); `onBaseStatsChanged` preserves the exact lock-free shape — **no** added lock / round-trip / re-read (§5.B, §6). R119 is single-threaded; the guard is the no-widening invariant, not a test. |
| UW expiry `effectWasActive`/`justExpired` semantics drift | Timer arithmetic stays in unchanged `Simulation.advanceUWTimers`; UWController only applies results — same as today. |
| `@Volatile` semantics lost in the move | §6 field-placement pinned against actual modifiers (UW fields are plain `var`, lock-provides happens-before; init-reset stays under `entitiesLock`); no single-thread "optimization" permitted. |
| CombatResolver can't reach engine state (second-wind / calculators / Simulation) | §6 CombatHost gains `secondWindHpPercent` + `consumeSecondWind()`; calculators become CombatResolver fields; UWController gets `Simulation` via constructor. |
| Reflection-heavy test breakage underestimated | Plan enumerates every reflection site in `GameEngineTest` and its destination before any extraction. |
| One big PR is hard to review | Collaborator-by-collaborator extraction (small internal steps); both spec & plan through the Adversarial Review Gate (ultracode ON, full multi-agent). |

## 11. Acceptance criteria

- **Every new collaborator file** (`BattleRenderer` / `UWController` / `BuffTickers` / `CombatResolver`)
  is **< 400 lines**. The slimmed `GameEngine` is **~500–560 lines** (not < 400 — see §2; the plan pins
  the measured value via trial extraction). No single responsibility outside the engine exceeds its
  < 400-line collaborator.
- `BattleRenderer` owns all `Paint`; `GameEngine` has zero `android.graphics.Paint` fields.
- Pure cash formulas live in `SimulationMath` with new pure-JVM tests.
- Public API on `GameEngine` unchanged (consumer list in §3.2 compiles untouched; `addEntity` stays
  public per §5.E).
- `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` pass **unchanged**.
- Full `testDebugUnitTest lintDebug assembleDebug` green; headline count updated.
- ADR-0012 updated: Phase 4 (presentation collaborator split + pure-formula hoist) recorded; the
  entity-coupled UW-effect / HP-mutation remainder tracked as an explicit future slice.
- **#231 closeable on this PR.** **#230 closeable iff** the issue owner accepts the partial-domain-hoist
  basis (pure cash formulas → `SimulationMath` + entity-coupled remainder tracked in ADR-0012); if #230
  requires the full UW-effect/damage *domain* migration, it stays open (or splits into a follow-up
  ADR-0012 slice) while #231 closes. Confirm at PR/issue time.
