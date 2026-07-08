# ADR-0012: Simulation Extraction (Phase 1 — Pure Math Helpers)

**Status:** Accepted
**Date:** 2026-05-28
**Supersedes:** None
**Superseded by:** None

## Context

The `presentation/battle/engine/GameEngine.kt` file had grown to 1004 lines mixing pure simulation logic (HP healing math, damage modifiers, threshold-crossing accumulators) with Android Canvas/SurfaceView entity rendering. This violates the project's Clean Architecture layering rule (`domain/` has zero Android imports) and forces all simulation tests to use Robolectric reflection patterns (`GameEngineTest.kt` is 700+ lines of reflective helpers).

The V1X-09 sub-plan in `plan-V1X-roadmap.md` proposes a full extraction of simulation logic to `domain/battle/engine/Simulation.kt` (~400 LOC pure Kotlin) with renderer delegation, but this is a 3-4 day refactor with significant regression risk across the entire battle subsystem.

## Decision

Adopt the plan's **Option C: Partial extraction** — Phase 1 lifts only the safest pure-math helpers into a new `domain/battle/engine/SimulationMath.kt` `object` and has GameEngine delegate to them. Entity rendering, collision system, wave spawning, and UW lifecycle remain in the presentation layer pending future phases.

Functions extracted in Phase 1:

- `recoveryPulseAmount(level, maxHp)` — Recovery Packages heal-per-pulse formula
- `chronoMultiplier(active, slowFactor)` — CHRONO_FIELD UW enemy-slow factor
- `thornReflectionDamage(rawDamage, thornPercent, conditionMultiplier)` — THORN_DAMAGE reflection
- `lifestealHealAmount(damageDealt, lifestealPercent)` — LIFESTEAL heal-per-hit
- `tickLifestealAccumulator(accumulator, healAmount)` — sub-1-HP threshold-crossing tracker
- `clampHp(candidateHp, maxHp)` — defensive HP clamp helper

Constants moved alongside (`RECOVERY_INTERVAL_SECONDS`, `RECOVERY_PERCENT_PER_LEVEL`, `RECOVERY_PERCENT_PER_PULSE_CAP`, `CHRONO_SLOW_FACTOR_DEFAULT`, `LIFESTEAL_CAP`).

## Consequences

### Positive

- New `SimulationMathTest.kt` with 27 pure-JVM tests directly exercising the extracted math (no Robolectric needed).
- GameEngine's `applyThorn`, `applyLifesteal`, and `tickRecoveryPackages` reduced to ~3-line delegating wrappers.
- Domain layer now contains *some* simulation math, establishing the package structure for future Phase 2 extraction.
- Closes the dead-tree problem where pure-math functions were trapped in a Canvas-using class.

### Negative

- Two parallel locations now contain "simulation math": the new `SimulationMath` object and the still-presentation-layer entity update logic. Deferred items (RapidFire timer state, GOLDEN_ZIGGURAT × overdrive fortuneMultiplier stacking, projectile movement, UW activation) remain in GameEngine.
- Tests in GameEngineTest still exercise the old reflection-heavy pattern for the bits that didn't move.
- Phase 1 alone does not enable V1X-27 (simulation tooling for headless balance simulation) — Phase 2 is required for that.

### Neutral

- `floor` import removed from GameEngine; `min` retained (used elsewhere).
- `RECOVERY_*` companion-object constants in GameEngine deleted; no external references found.
- No behavior change. All 760 existing JVM tests continue to pass.

## Future Phases

**Phase 2 (V1X-09b, COMPLETE — 2026-05-29 → 2026-06-01):** Extract entity update logic. Each `presentation/battle/entities/*` file splits into:
- `domain/battle/entity/<Name>State.kt` — pure data class + update(deltaTime, world) logic
- the presentation entity keeps `render()` + any Android-coupled collision fields and delegates `update()` to its state

Estimated effort: 3-4 days. Requires V1X-08 (instrumented tests) as a safety net.

**Per-entity sub-PR progress:**
- ✅ **ProjectileEntity (2026-05-29):** motion extracted to `domain/battle/entity/ProjectileState` (homing-toward-target step + alive flag). The presentation entity delegates `update()` and keeps `render()` + the collision/bounce fields (`damage`, `bouncesRemaining`, `hitEnemies`); constructor signature unchanged so `CollisionSystem` / `GameEngine` are untouched. +4 pure-JVM `ProjectileStateTest` cases. Establishes the `domain/battle/entity/<Name>State` package + delegation pattern.
- ✅ **EnemyProjectileEntity (2026-05-29):** reuses the same `ProjectileState` (identical homing motion); keeps `render()` + `damage`/`shooter`. No new domain class — motion already covered by `ProjectileStateTest`.
- ✅ **OrbEntity (2026-05-29):** orbit position + radial-oscillation math extracted to `domain/battle/entity/OrbState`. The presentation entity delegates position and keeps the enemy-proximity / per-enemy hit-cooldown logic (needs `EnemyEntity` refs + `onHitEnemy`) + `render()`; re-exposes `ORBIT_RADIUS_MIN/MAX`, `ORBIT_PERIOD_SEC`, and `currentOrbitRadius` from `OrbState` so the Robolectric `OrbEntityTest` (6 #54 regression tests) is untouched. +4 pure-JVM `OrbStateTest` cases.
- ✅ **EnemyEntity (2026-06-01):** movement + attack-cooldown state extracted to `domain/battle/entity/EnemyState` (homing step + RANGED stop-distance + attack-cadence; `update(dt): Boolean` returns `true` on the frame an attack should fire). The presentation entity delegates `update()`/`initDistance()`/`applyKnockback()` to it, syncs `x`/`y` back onto the `Entity` base each tick (so `CollisionSystem` / `GameEngine` / `OrbEntity` reads stay correct), and keeps HP/armor/death, the melee/ranged attack callbacks (which need the entity reference), and the Canvas `render()`. Constructor + `WaveSpawner` `.apply { x=…; y=…; initDistance() }` wiring unchanged. +5 pure-JVM `EnemyStateTest` cases.
- ✅ **ZigguratEntity (2026-06-01):** HP / regen + attack-cooldown + RAPID_FIRE multiplier + the derived `attackInterval` / `attackRange` reads extracted to `domain/battle/entity/ZigguratState`. The presentation entity delegates its full public surface (`currentHp` / `maxHp` get+set, `stats` / `attackRange` read-only get, `rapidFireMultiplier` get+set, `updateStats`) to the state so `GameEngine` (≈30 `zig.*` touch points: HP damage/heal/death-defy/second-wind writes, `applyStats`, `tickRapidFire`) and `BattleViewModel` are **untouched**; keeps the layer geometry, `originX`/`originY`/`centerY`, the nearest-enemy targeting + fire callback (need presentation `EnemyEntity` refs), and the Canvas `render()`. `update()` = `state.regenHp(dt)` then `if (state.tickAttackReady(dt)) { targets = findNearestEnemies(stats.multishotTargets); if non-empty state.onFired() + fire each else state.holdReady() }`. Constructor unchanged. +6 pure-JVM `ZigguratStateTest` cases. **Completes Phase 2 — all 5 battle entities now delegate simulation state to `domain/battle/entity`.**

**Phase 3 (V1X-09c, IN PROGRESS — started 2026-06-01):** Extract the `GameEngine.update()` loop into `domain/battle/engine/Simulation.kt`. Migrate `BattleViewModel` from polling individual `engine.*` fields to collecting `simulation.events: Flow<SimulationEvent>`. Done as per-slice sub-PRs, mirroring the Phase 2 cadence.

**Per-slice progress:**
- ✅ **Cash economy (2026-06-01):** new `domain/battle/engine/Simulation` class owns the in-round cash state (`cash` / `totalCashEarned`, both `@Volatile private set`) + the mutation primitives `reset()` / `creditCash(amount)` (credits both balances) / `applyInterest(level)` (compounds `min(level × 0.5 %, 10 %)` onto `cash` only — excluded from lifetime) / `spend(amount)` (insufficient-balance guard). `GameEngine` holds a `Simulation` and delegates its `cash` / `totalCashEarned` / `spendCash` public surface; the per-kill and wave-complete reward *formulas* stay in `GameEngine` (they depend on tier config, enemy type, and engine multiplier fields) and call `creditCash` / `applyInterest` with the computed amounts. +9 pure-JVM `SimulationTest`. Establishes the `Simulation` class + delegation pattern. No behaviour change; 831 → 840 tests.
- ✅ **Round-progress counters (2026-06-01):** `Simulation` gains `totalEnemiesKilled` / `totalStepsEarned` / `elapsedSeconds` (all `@Volatile private set`) + `tickElapsed(dt)` / `recordEnemyKilled()` / `creditSteps(amount)` / `hasWaveProgress()`. `GameEngine` delegates the three counters + `hasWaveProgress()` and routes its increment sites (update-loop clock, `handleEnemyDeath` kill + step credit) through `Simulation`; `roundOver` stays in the engine — it's a control flag tied to the update loop + the external `quitRound` signal, so it belongs with the tick (which moves in a later slice). +7 `SimulationTest`. No behaviour change; 840 → 847 tests.
- ✅ **EntityProtocol seam + entity tick (2026-06-01):** new pure-domain `domain/battle/entity/EntityProtocol` interface (`isAlive`, `isChronoSlowable` defaulting `false`, `update(deltaTime)`) is the seam that lets the domain `Simulation` iterate battle entities without referencing the Canvas-coupled presentation types. `presentation/battle/engine/Entity` implements it (keeps its abstract `render(canvas)`); `EnemyEntity` overrides `isChronoSlowable = true` (the only entity CHRONO_FIELD slows). New `Simulation.tickEntities(entities, deltaTime, chronoSlowFactor)` runs the per-frame tick — scaling `deltaTime` for chrono-slowable entities, full `deltaTime` otherwise — and `GameEngine.update()` swaps its inline `entities.forEach { … if (chronoActive && e is EnemyEntity) … }` loop for `simulation.tickEntities(entities, deltaTime, if (chronoActive) chronoSlowFactor else 1f)`, removing the last `is EnemyEntity` type check from the tick. +4 `SimulationTest` (backed by a private `FakeEntity`). No behaviour change; 847 → 851 tests.
- ✅ **Collision sweep (2026-06-02):** `EntityProtocol` gains `x` / `y` / `width` reads (the positional surface collision needs; `Entity`'s existing `var x/y/width` become `override var`). The two collision loops move into `Simulation` as generic, behaviour-preserving sweeps: `detectProjectileEnemyHits<P, E : EntityProtocol>(projectiles, enemies, onHit)` (first-overlap-per-projectile + `break`) and `detectZigguratHits<P : EntityProtocol>(enemyProjectiles, zigX, zigY, zigWidth, onHit)`. Both keep the EXACT original overlap expressions and fire `onHit` **synchronously inside the loop** — so a hit that knocks back or kills an enemy (`GameEngine.onProjectileHitEnemy` applies `applyKnockback` + sets `proj.isAlive = false`) is still observed by later projectiles' overlap tests the same frame; this is why the loop could not become detect-all-then-dispatch. Generics let the callers pass concretely-typed lists + callbacks while the sweep stays Canvas-free. `CollisionSystem` shrinks to a presentation adapter — it keeps only `filterIsInstance` type-partitioning + the alive snapshot (both inherently concrete-type concerns) and delegates both sweeps to the engine's `Simulation`; `GameEngine.update()` passes `simulation` into `CollisionSystem.checkCollisions`. +6 `SimulationTest` (incl. an interleaved-firing guard proving a mid-sweep moved enemy is missed by a later projectile). No behaviour change; 851 → 857 tests.
- ✅ **UW lifecycle timers (2026-06-02):** the pure UW cooldown + effect-duration timer state machine moves into `Simulation`. New `advanceUWTimers(cooldownRemaining, effectTimeRemaining, deltaTime): UWTimerAdvance` decrements both timers (cooldown floored at 0; effect counts down only while active, clamped to 0 on the crossing frame) and returns transition flags `effectWasActive` (run per-frame ongoing effects — BLACK_HOLE / POISON_SWAMP — true even on the expiry frame, matching the pre-extraction `effectTimeRemaining > 0f` guard) + `justExpired` (run one-shot expiry side-effects — CHRONO_FIELD / GOLDEN_ZIGGURAT flag + stat/fortune restore). New `isUWReadyToFire(cooldownRemaining, effectTimeRemaining)` is the auto-trigger readiness predicate. `GameEngine.updateUWs` applies the returned timer values onto each `UWState` and keeps the presentation-coupled side-effects (enemy damage, stat mutation, visual flags) + `activateUW`; the engine's public API + the `UWState` data class + every caller (`BattleViewModel`, `GameEngineTest`) are unchanged. +7 `SimulationTest`. No behaviour change; 857 → 864 tests.
- ✅ **SimulationEvent flow (2026-06-03):** the FINAL Phase 3 slice. The game-loop → ViewModel hand-off moves from two `@Volatile` callback fields to a pure-domain stream. New `domain/battle/engine/SimulationEvent` sealed interface (`StepReward(amount, x, y)` / `BossKilled(tier, x, y)`); `Simulation` gains `events: SharedFlow<SimulationEvent>` (`replay = 0`, `extraBufferCapacity = 64`, `DROP_OLDEST`) fed by `emit(event)` (non-suspending `tryEmit`, safe to call from the game-loop thread). `GameEngine` drops `onStepReward` / `onBossKilled`, exposes `val events get() = simulation.events`, and `handleEnemyDeath` emits the two events instead of invoking lambdas. `BattleViewModel` replaces `wireStepRewardCallback` / `wireBossKilledCallback` with a single `@VisibleForTesting handleSimulationEvent(engine, event)` (preserving the per-event scope — Step credit on `applicationScope`, boss Power Stones on `viewModelScope`), collected by a child coroutine of the polling loop that is cancelled when the loop breaks at round end and re-created fresh by `playAgain` (`replay = 0` guarantees a re-subscribing collector never re-sees the previous round's events → no double-credit). `onCleared` no longer null-unwires callbacks. +2 `SimulationTest` (delivery + ordering; the `replay = 0` no-replay contract); the 6 `BattleViewModelTest` step-reward tests + 3 `GameEngineTest` boss tests were migrated in place (event-driven instead of callback-driven). No behaviour change; 864 → 866 tests. **Completes Phase 3 — `GameEngine` is now a thin presentation/render shell over the pure-domain `Simulation`, and the engine↔ViewModel boundary is a single Flow.**

Phase 3 is complete: the in-round cash economy, round-progress counters, chrono-aware entity tick, collision sweep, UW lifecycle timers, and the side-effect event hand-off all live in the pure-domain `Simulation`.

**Phase 4 (#230/#231, COMPLETE — 2026-06-22):** Presentation-layer decomposition of the still-1233-line `GameEngine`. Phase 3 extracted the pure-domain core, but the bulk of the *presentation* mechanics (Canvas render, UW lifecycle, per-mechanic buff timers, combat/death resolution) remained co-habiting one Canvas-coupled god class. #230/#231 split it into focused collaborators that `GameEngine` composes and reaches via three narrow capability interfaces (`UWHost`/`BuffHost`/`CombatHost`, in `presentation/battle/engine/BattleHosts.kt`), which `GameEngine` implements:

- **`BattleRenderer`** (89 LOC) — owns all Canvas `Paint` + the per-frame draw sequence (lifted verbatim from `GameEngine.render()`). `GameEngine` zero longer holds any `android.graphics.Paint` field.
- **`UWController`** (303 LOC) — UW cooldown/effect state machine, auto-trigger, ongoing effects (BLACK_HOLE/POISON_SWAMP), CHRONO_FIELD/GOLDEN_ZIGGURAT activation+expiry, and the #119 GOLDEN re-layer (`relayerBaseStats`). Owns `uwStates`, `chronoActive`/`chronoSlowFactor`/`fortuneMultiplier` (plain `var private set`). Delegates pure timer arithmetic to the engine-owned `Simulation` (passed via ctor).
- **`BuffTickers`** (150 LOC) — RECOVERY_PACKAGES / RAPID_FIRE / LIFESTEAL timers + accumulator.
- **`CombatResolver`** (209 LOC) — projectile/orb hits, ziggurat damage+defense+thorn+second-wind+death-defy, enemy death (reward credit/feedback + SCATTER split), wave-complete cash. Owns its own `CalculateDamage`/`CalculateDefense`.

**Pure-formula hoist (#230's domain half):** the two inline cash-reward formulas moved to `SimulationMath` as `killCashReward(...)` / `waveCompleteCash(...)` (+ named constants `BASE_CASH_PER_WAVE`/`FLAT_BONUS_PER_WAVE_LEVEL`/`CASH_BONUS_PER_LEVEL`), arithmetically bit-identical, now pure-JVM tested.

**Thread-safety preserved exactly:** `GameEngine` stays the SOLE owner of `entitiesLock` (held across the whole `update()` tick); collaborators contain no `synchronized` block and run inside the held lock (or the main-thread `init`/`initUWs`/`uwSnapshot` paths the engine wraps, #191). The acyclic `entitiesLock → effectsLock` order is unchanged. `GameEngineConcurrencyTest` + `EffectEngineConcurrencyTest` pass **unchanged** (empty diff vs `main`) — the proof the concurrency model survived.

**Result:** `GameEngine.kt` 1233 → 618 LOC (orchestrator + lock owner + façade; public API unchanged); every new collaborator < 400 LOC. Behavior-preserving (the existing corpus is the oracle; `GameEngineTest` reflection re-pointed to collaborators via `@VisibleForTesting` getters, no assertion weakened). +9 JVM tests (2 cash-formula + 7 collaborator) → 1196 → 1205.

**Explicitly NOT done (tracked remaining slice, per #230's "track remaining slices explicitly"):** UW *effect resolution* (the `when(type)` damage/pull/DoT bodies) and `applyDamageToZiggurat`/`applyThorn` HP mutation are NOT hoisted to the pure-domain `Simulation` — they call `EnemyEntity.takeDamage`/`applyKnockback`/`ZigguratEntity.currentHp`, none of which `EntityProtocol` exposes. A true domain hoist needs entity-model surgery (extend `EntityProtocol` / new domain ports) — a separate large refactor. #231 closes on this PR; #230 closes on the partial-domain-hoist + explicit-tracking basis (confirm with the issue owner if #230 demands the full domain migration).

**Phase 5 (#306, Slice 1 — ziggurat damage resolution, 2026-07-08):** The first slice of the tracked
effect-resolution hoist. New pure-domain `domain/battle/entity/Damageable` port (`currentHp`/`maxHp`) —
deliberately NOT a subtype of `EntityProtocol` (HP is orthogonal to the positional/tickable surface;
`ZigguratState` is non-positional). `ZigguratState` implements it (declaration + `override`). New pure
`domain/battle/engine/ZigguratDamageResolver` lifts the defense/death-defy/second-wind/HP-floor/
shake-threshold arithmetic + HP mutation out of `CombatResolver.applyDamageToZiggurat`, operating on the
`Damageable` port and returning a `DamageOutcome(crossedShakeThreshold)`. `CombatResolver` becomes a thin
adapter: it fires the `reducedMotion`-gated screen shake + thorn reflection (thorn calls a presentation
`EnemyEntity.takeDamage`, so it stays presentation). `ZigguratEntity` exposes `zigguratState: Damageable`
(port-typed, so `ZigguratState`'s loop-thread-only mutators stay encapsulated). Behaviour-preserving,
validated by pre-hoist characterization tests (baseline oracle) + the new resolver tests.
Thread-safety unchanged: the resolver holds no monitor and runs inside the engine's held `entitiesLock`.
**Caveat:** the pure-domain "no monitor" property is convention-only — `BattleEngineLockScanTest` scans
only `presentation/battle/engine`; extending it to `domain/battle/**` is deferred forward-hardening ahead
of the larger #306 slices (enemy HP + UW effect bodies).

**Explicitly still NOT done (remaining #306 slices):** enemy `takeDamage`/`onDeath`/SCATTER child spawn;
all `UWController.when(type)` effect bodies; `onProjectileHitEnemy`/`onOrbHit` knockback+lifesteal.

## References

- `domain/battle/engine/SimulationMath.kt` — extracted pure-math helpers
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMathTest.kt` — 27 pure-JVM tests
- `presentation/battle/engine/GameEngine.kt` — refactored to delegate (`applyThorn`, `applyLifesteal`, `tickRecoveryPackages`)
- `docs/plans/plan-V1X-roadmap.md` § V1X-09 — full sub-plan spec including Phase 2/3 details
