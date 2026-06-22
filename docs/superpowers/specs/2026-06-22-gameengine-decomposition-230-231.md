# Design Spec — GameEngine god-class decomposition (#230 + #231)

**Date:** 2026-06-22
**Issues:** #230 (ADR-0012 extraction only partial — GameEngine still a 1223-line complexity hotspot),
#231 (1223-line god class mixing simulation orchestration, rendering, UW lifecycle, cash economy,
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

**Goal:** decompose `GameEngine` into focused collaborators (sub-400-line files), each with one clear
responsibility, **strictly behavior-preserving**, satisfying both #230 (domain hoist of pure formulas +
an explicit tracker for the entity-coupled remainder) and #231 (presentation collaborators +
`BattleRenderer` for Canvas/Paint).

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
The A31 cached-Paint property and the `setChronoActiveForTest` seam move with it (the seam becomes a
pass-through on the engine if `GameEngineTest` still needs it, or re-points to the renderer).

### B. `UWController` — Ultimate Weapon lifecycle (#231 "UW lifecycle state machine")
Owns `uwStates`, the `UWState` data class, and the UW flags/multipliers: `chronoActive`,
`chronoSlowFactor`, `goldenZigActive`, `preGoldenStats`, `goldenDamageMult`, `fortuneMultiplier`.
Methods: `initUWs`, `uwSnapshot`, `activateUW`, `updateUWs`, `resetUWCooldowns`. Delegates timer
arithmetic to the **unchanged** `Simulation.advanceUWTimers` / `isUWReadyToFire`.

Reads the engine can't give up are exposed back to `UWController` via a `UWHost` interface (§6).
`chronoActive` / `chronoSlowFactor` are read by the engine's tick (`simulation.tickEntities` slow
factor) and the renderer → exposed as reads on `UWController`. `fortuneMultiplier` is read by the cash
formulas (CombatResolver) → exposed as a read.

**Highest-scrutiny zone:** the GOLDEN_ZIGGURAT #119 re-layering. `activateUW` captures
`preGoldenStats = stats` and applies `stats.copy(damage = stats.damage * dmgMult)`;
`updateZigguratStats` (engine, main thread) re-captures `preGoldenStats` to the new base and re-applies
`goldenDamageMult` when GOLDEN is active; `updateUWs` expiry restores `preGoldenStats`. After the split,
`goldenZigActive`/`preGoldenStats`/`goldenDamageMult` live in `UWController` but `updateZigguratStats`
(engine façade) must read/write them — exposed via the host or via `UWController` methods
(`onBaseStatsChanged(newStats)` encapsulating the re-layer). The `effectWasActive` / `justExpired`
semantics from `advanceUWTimers` (ongoing effect runs one final time on the expiry frame) must be
preserved exactly.

### C. `BuffTickers` — per-mechanic timers (#231 "per-mechanic timers")
Owns `recoveryTimer`, `rapidFireTimer`, `rapidFireActiveRemaining`, `lifestealAccumulator`. Methods:
`tickRecoveryPackages(dt)`, `tickRapidFire(dt)`, `applyLifesteal(healAmount)`. Reads ziggurat, wave
phase, effect engine, strings, and the level map via a `BuffHost` interface. The
"reset-to-zero-during-COOLDOWN-phase" behavior and the L10 rapid-fire seamless-transition ordering are
preserved verbatim.

### D. `CombatResolver` — damage / targeting / death
Owns `onProjectileHitEnemy`, `onOrbHitEnemy`, `applyDamageToZiggurat`, `applyThorn`,
`findNearestEnemies`, `getAliveEnemies`, `handleEnemyDeath`, `handleWaveComplete`. Computes reward
amounts via the new `SimulationMath` formulas (§7) then runs the entity/effect side effects exactly as
before (`simulation.creditCash`, sound, `DeathEffect`, `FloatingText`, `SimulationEvent.emit`, SCATTER
child spawn into `pendingAdd` via host). Reaches engine state via a `CombatHost` interface.

Note `findNearestEnemies` / `getAliveEnemies` iterate `entities` (engine-owned). To honor "`entities`
never leaves the engine", the engine keeps the iteration and exposes `aliveEnemies()` (and the
nearest-N targeting) through the host; CombatResolver and UWController call `host.aliveEnemies()`. (This
preserves the #125 no-cache hot-path contract — the engine method re-derives live each call.)

### E. `GameEngine` (slimmed) — orchestrator + lock owner + façade (target ~350–400 lines)
Keeps: `entities` / `pendingAdd` / `entitiesLock`, the A28 scratch buffers, `init`, `update` (the locked
tick sequencing the collaborators), `applyStats` / `updateZigguratStats` (stats-mutation point + orb
reconcile — both touch `entities` under lock), `spawnOrbs`, the wave-announcement triggers
(`triggerWaveAnnouncement`, `nextWaveCompositionLabel`, `bossCountdownLabel`), `aliveEnemyCount()`, and
the delegating façade getters/fields (`cash`, `totalCashEarned`, `events`, `hasWaveProgress`,
`spendCash`, `setStats`, `updateEffectiveLevels`, plus the `@Volatile` VM-written fields). Implements
`UWHost` / `BuffHost` / `CombatHost`. Constructs the four collaborators in `init` (or as fields).

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
  fun aliveEnemies(): List<EnemyEntity>
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
  fun addPending(entity: Entity)
  fun aliveEnemies(): List<EnemyEntity>
  fun nearestEnemies(n: Int): List<EnemyEntity>
  fun wsLevel(type: UpgradeType): Int
  fun applyLifesteal(healAmount: Double)   // delegates to BuffTickers
}
```

**`@Volatile` placement.** The VM-written fields (`cashResearchMultiplier`, `uwCooldownMultiplier`,
`enemyIntelLevel`, `cashBonusPercent`, `secondWindHpPercent`, `secondWindUsed`, `cosmeticOverrides`,
`roundOver`) stay on `GameEngine` (the façade the VM writes). `fortuneMultiplier` moves to
`UWController` and stays accessible — it is written only on the loop thread (`activateUW` / GOLDEN
expiry, both inside the tick) and read by CombatResolver on the same thread, so it does **not** need
`@Volatile` once it is loop-thread-confined; **but** to be conservative and preserve the exact current
memory semantics, it keeps no weaker guarantee than today (today it is a plain `var` read/written only
under the tick — confirm during implementation; if any cross-thread read exists it stays `@Volatile`).
`chronoActive`/`chronoSlowFactor` are today `@Volatile`-free plain vars written under the tick and read
by `render` (different thread) — they move to `UWController` **keeping the same `@Volatile`-or-not
status they have today** (verify exact current modifiers during implementation; the render read is the
reason any volatility exists).

> **Review focus:** the volatility audit above must be verified against the actual current field
> modifiers during the plan stage — the spec's claim "preserve exactly" is the contract; the plan
> pins the per-field truth.

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
   - `buffTickers.tickRecovery(dt)`; `buffTickers.tickRapidFire(dt)`
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
2. **Reflection migration** of `GameEngineTest` — the main test work. Each moved member's reflection is
   re-pointed to the collaborator (reached via `@VisibleForTesting engine.<collaborator>ForTest`) or
   converted to a direct collaborator test. Red-before-green per move.
3. **New focused unit tests** (the win — previously trapped behind Robolectric reflection):
   - `SimulationMathTest` += `killCashReward` / `waveCompleteCash` cases (pure JVM).
   - `UWControllerTest`, `BuffTickersTest`, `CombatResolverTest` against fake hosts (no engine, minimal
     Robolectric — only where `EnemyEntity`/`ZigguratEntity`/`EffectEngine` force it).
4. **Concurrency guards sacrosanct** — `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` pass
   unchanged.
5. **Instrumented** `BattleSurfaceLifecycleTest` stays valid via public-API preservation.
6. **Build gate:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; full
   JVM suite green; headline test count updated.

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Concurrency model silently changes | Engine stays sole lock owner; collaborators have no monitor; concurrency tests must pass unchanged (stop if they don't). |
| GOLDEN_ZIGGURAT #119 re-layer breaks | Highest-scrutiny zone; `onBaseStatsChanged` encapsulation keeps the re-capture+re-apply in one place; existing `GameEngineTest` GOLDEN cases re-pointed, not weakened. |
| UW expiry `effectWasActive`/`justExpired` semantics drift | Timer arithmetic stays in unchanged `Simulation.advanceUWTimers`; UWController only applies results — same as today. |
| `@Volatile` semantics lost in the move | §6 volatility audit pinned in the plan against actual current modifiers; "preserve exactly" is the contract. |
| Reflection-heavy test breakage underestimated | Plan enumerates every reflection site in `GameEngineTest` and its destination before any extraction. |
| One big PR is hard to review | Collaborator-by-collaborator extraction (small internal steps); both spec & plan through the Adversarial Review Gate (ultracode ON, full multi-agent). |

## 11. Acceptance criteria

- `GameEngine.kt` and every new collaborator file are **< 400 lines**.
- `BattleRenderer` owns all `Paint`; `GameEngine` has zero `android.graphics.Paint` fields.
- Pure cash formulas live in `SimulationMath` with new pure-JVM tests.
- Public API on `GameEngine` unchanged (consumer list in §3.2 compiles untouched).
- `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` pass **unchanged**.
- Full `testDebugUnitTest lintDebug assembleDebug` green; headline count updated.
- ADR-0012 updated: Phase 4 (presentation collaborator split + pure-formula hoist) recorded; the
  entity-coupled UW-effect / HP-mutation remainder tracked as an explicit future slice.
- #230 + #231 closeable.
