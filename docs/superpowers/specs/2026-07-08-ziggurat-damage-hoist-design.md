# Design — ADR-0012 Phase 5, Slice 1: hoist ziggurat damage resolution to pure domain (#306)

**Date:** 2026-07-08
**Issue:** #306 (Extend EntityProtocol to enable domain hoist of combat effect-resolution + damage
application — ADR-0012 future slice). Advances #306; does **not** close it. Related: #233 (in-flight
round destroyed on config change) is already neutralized by the ADR-0029 portrait lock — this slice is
the *clean* fix #233 pointed to, but #233 does not gate or reopen on it.
**Status:** approved (brainstorming) → pending Adversarial Review Gate before plan.

## Problem & context

ADR-0012 extracted the pure-domain battle core (`domain/battle/`) across Phases 1–4. Phase 4 (#230/#231,
PR #304) decomposed the `GameEngine` god class into an orchestrator + four presentation collaborators,
and explicitly tracked a **remaining slice**: the combat *effect-resolution* logic — damage/defense
application and the UW `when(type)` effect bodies — was **not** hoisted, because it calls
`EnemyEntity.takeDamage`/`applyKnockback`/`currentHp` and `ZigguratEntity.currentHp`, none of which the
domain seam (`EntityProtocol`) exposes. #306 is that tracked slice.

The full slice is flagged **large** and touches the repo's most fragile file (`GameEngine` /
`entitiesLock`, #118/#191). Per the brainstorming decision we take a **bounded first sub-slice**: the one
self-contained piece that mutates **only the ziggurat's HP** (which already lives in pure domain) and
**spawns no entities** — `CombatResolver.applyDamageToZiggurat`. The harder half (enemy `takeDamage` +
`onDeath` + SCATTER child spawning; all UW effect bodies; projectile/orb knockback+lifesteal) is deferred
to later #306 slices.

**Two facts from the code that make this slice low-risk:**
1. **`ZigguratState` (`domain/battle/entity/ZigguratState.kt`) already owns `var currentHp` / `var maxHp`
   / `regenHp`, is already pure-domain, and is already pure-tested (`ZigguratStateTest`).** The ziggurat's
   HP *state* is already hoisted — only the damage *resolution* logic (`applyDamageToZiggurat`) still sits
   in the presentation `CombatResolver`.
2. `CalculateDefense` (`domain/usecase/CalculateDefense.kt`) — the mitigation math the resolution uses — is
   already pure-domain and injectable. No change needed there.

## Goal

Hoist the pure combat arithmetic + HP mutation of `applyDamageToZiggurat` into a new pure-domain
resolver operating on a new `Damageable` domain port, leaving `CombatResolver` a thin presentation
adapter that fires only the genuinely-presentation side-effects. Behaviour-preserving; adds real
pure-JVM coverage for defense/death-defy/second-wind/shake-threshold branches that today have thin-to-no
direct coverage.

## Decisions (locked in brainstorming)

| Decision | Choice | Rationale |
|---|---|---|
| **Scope** | First bounded slice: ziggurat damage only | Lowest-risk piece (HP already pure; spawns no entities). Matches the project's sequential-slice discipline. |
| **Shape** | Approach B — pure resolver **mutates `ZigguratState`** via a `Damageable` port | Faithful to #306 acceptance ("operate on `*State`"); lands the reusable port the deferred slices reuse. Risk stays low because `ZigguratState` is already pure. |
| **Port type** | **Standalone `Damageable`**, NOT `Damageable : EntityProtocol` | `ZigguratState` is non-positional (no `x`/`y`/`width`/`update`); subtyping `EntityProtocol` would force meaningless members on it and would ripple into `SimulationTest`'s `FakeEntity`. #306 permits "EntityProtocol **or new domain ports**". Deferred enemy slice will declare `EnemyState : EntityProtocol, Damageable`. |
| **Home for the resolver** | New dedicated class `ZigguratDamageResolver`, NOT a method on `Simulation` | Matches `CalculateDamage`'s injectable-`Random` precedent; avoids touching `Simulation`'s ctor / the `@Volatile` state owner. |
| **`applyThorn`** | Left untouched in the adapter | Already routes through pure `SimulationMath.thornReflectionDamage`; the `attacker.takeDamage(reflection)` call **must** stay presentation (it re-fires enemy `onDeath` → the deferred slice). Orthogonal to ziggurat HP. |

## Scope

### In scope
1. **New `domain/battle/entity/Damageable.kt`** — pure port, no Android imports:
   ```kotlin
   interface Damageable {
       var currentHp: Double
       val maxHp: Double
   }
   ```
2. **`ZigguratState` declares `: Damageable`.** It already has `var currentHp: Double` and
   `var maxHp: Double` (public) — this is a declaration-only change; `currentHp` already matches the
   `var` (get+set) requirement and `maxHp` satisfies the `val` (get) requirement. No member bodies change.
3. **New `domain/battle/engine/ZigguratDamageResolver.kt`** — pure resolver:
   ```kotlin
   class ZigguratDamageResolver(
       private val calculateDefense: CalculateDefense = CalculateDefense(),
       private val random: Random = Random.Default,
   ) {
       data class DamageOutcome(val crossedShakeThreshold: Boolean)

       fun resolve(
           target: Damageable,
           rawDamage: Double,
           stats: ResolvedStats,
           secondWindHpPercent: Double,
           consumeSecondWind: () -> Boolean,
       ): DamageOutcome
   }
   ```
   Body **lifted verbatim** from `CombatResolver.applyDamageToZiggurat` (current lines 117–137):
   defense mitigation (`calculateDefense`) → death-defy branch (rolls `random.nextDouble()` **lazily,
   only inside that branch**, faithful to the original `Random.nextDouble()` call site) → second-wind
   branch (invokes `consumeSecondWind()` **exactly where the original does** — preserving the stateful
   one-shot test-and-set order and short-circuit) → normal damage with the `coerceAtLeast(0.0)` HP floor →
   shake-threshold crossing computed as a pure boolean (`prevRatio > 0.25 && newRatio <= 0.25`). Mutates
   `target.currentHp` directly. Returns `DamageOutcome(crossedShakeThreshold)`.

   **Ordering invariant (verbatim from original):** death-defy is checked **before** second-wind; when a
   lethal hit is survived by either branch, the ziggurat is set to its restore HP and the method returns
   **without** the normal-damage subtraction and **without** signalling a shake crossing. Thorn is applied
   by the caller in all branches (see adapter), so the resolver does not touch thorn.
4. **`CombatResolver.applyDamageToZiggurat` shrinks to a thin adapter** that:
   - short-circuits on `host.ziggurat ?: return` (unchanged),
   - calls `zigguratDamageResolver.resolve(target = zig.zigguratState, …)`,
   - fires screen shake **only** on `outcome.crossedShakeThreshold && !host.reducedMotion`
     (the `reducedMotion` gate + `effectEngine?.screenShake?.trigger(5f, 0.2f)` stay presentation),
   - calls `applyThorn(rawDamage, attacker)` (unchanged).
   `CombatResolver` gains a `private val zigguratDamageResolver = ZigguratDamageResolver()` field and
   **removes** its now-unused `private val calculateDefense = CalculateDefense()` field (dead after the
   hoist — verify no other method in the file uses it; `onProjectileHitEnemy`/`onOrbHit` use
   `calculateDamage`, not `calculateDefense`).
5. **`ZigguratEntity` exposes its state:** `val zigguratState: ZigguratState get() = state`
   (the private `state` field already exists). Read-only accessor — no new mutable surface.
6. **New pure-JVM `ZigguratDamageResolverTest`** (domain lane) — see Testing.
7. Doc sync: ADR-0012 Phase 5 entry (this slice); CLAUDE.md headline test count; `source-files.md`
   (new files); STATE.md fragile-zone note if the economy/battle fragile list warrants it; CHANGELOG.

### Out of scope (YAGNI — deferred to later #306 slices)
- Enemy `takeDamage` / `armorHits` / `onDeath` / SCATTER child spawning → a pure `EnemyState`-owned HP
  surface. (Spawns presentation `EnemyEntity` objects; the hard half.)
- All `UWController.when(type)` effect bodies (DEATH_WAVE / CHAIN_LIGHTNING / BLACK_HOLE / POISON_SWAMP /
  CHRONO_FIELD / GOLDEN_ZIGGURAT damage + knockback + DoT).
- `CombatResolver.onProjectileHitEnemy` / `onOrbHit` (knockback + lifesteal gating on damage dealt).
- Moving `applyThorn`'s `attacker.takeDamage` into domain.
- Any `EntityProtocol` extension (this slice adds a **separate** `Damageable` port; `EntityProtocol` is
  untouched). The deferred enemy slice will combine them.

## Concurrency invariants — preserved exactly

- Every caller of `applyDamageToZiggurat` (collision `onEnemyProjectileHitZiggurat`, the melee-hit
  callbacks wired in `GameEngine.init` and the SCATTER path) runs **inside the engine's held
  `entitiesLock`** (the whole `GameEngine.update()` tick, plus the collision sweep). The hoist does not
  move the call site — `ZigguratState.currentHp` is still mutated under the same monitor, at the same
  point in the tick.
- `ZigguratDamageResolver` holds **no monitor of its own** (like `SimulationMath` / `CalculateDefense`).
  It is a stateless pure function object; the only mutable state it touches is the caller-supplied
  `target` (the ziggurat's already-lock-protected HP).
- `secondWindUsed` is a `@Volatile` engine field; the resolver never reads/writes it directly — it calls
  the `consumeSecondWind` callback (`host::consumeSecondWind`), which keeps the existing
  test-and-set-under-lock semantics exactly. `secondWindHpPercent` is passed by value (read once by the
  caller before the call — same as the original inline read).
- **Guards that must stay green, unchanged:** `GameEngineConcurrencyTest`, `EffectEngineConcurrencyTest`
  (empty diff), `BattleEngineLockScanTest` (new class is under `domain/battle/`, out of its
  `presentation/battle/engine` scan scope; `CombatResolver` stays in the allowlist and gains no
  `synchronized`/monitor), `DomainPurityTest` (new files import only `CalculateDefense` /
  `ResolvedStats` / `kotlin.random.Random` — all domain/stdlib, zero Android/data).

## Data flow (after)

```
collision sweep / melee callback (inside entitiesLock)
  → CombatResolver.applyDamageToZiggurat(rawDamage, attacker)
      → ZigguratDamageResolver.resolve(zig.zigguratState, rawDamage, stats, secondWindHpPercent, host::consumeSecondWind)
          [pure] mitigate → (death-defy? | second-wind? | normal-damage) → mutate target.currentHp → return DamageOutcome
      → if crossedShakeThreshold && !reducedMotion: effectEngine.screenShake.trigger(5f, 0.2f)   [presentation]
      → applyThorn(rawDamage, attacker)   [presentation: SimulationMath math + attacker.takeDamage]
```

## Testing

**New `ZigguratDamageResolverTest`** (pure JVM, `domain/battle/engine/`, JUnit Jupiter) — the payoff.
Drives `resolve()` against a **real `ZigguratState`** (already pure-constructible from `ResolvedStats`)
and stub `consumeSecondWind` lambdas. Cover the branches that have thin-to-no direct coverage today:
- **Defense mitigation:** percent + flat defense reduce the HP lost (delegates to `CalculateDefense`; a
  smoke assertion, since `CalculateDefenseTest` owns the math).
- **Normal damage:** `currentHp` drops by the mitigated amount; floored at `0.0` on an overkill hit.
- **Death-defy:** injected `random` returning `< deathDefyChance` on a lethal hit → HP restored to `1.0`,
  `crossedShakeThreshold == false`, second-wind **not** consumed. Injected `random` returning
  `>= deathDefyChance` → normal lethal damage applied (HP floored at 0).
- **Second-wind:** `deathDefyChance == 0`, `secondWindHpPercent > 0`, `consumeSecondWind` returns true on
  a lethal hit → HP restored to `maxHp × pct`, `crossedShakeThreshold == false`. `consumeSecondWind`
  returns false → normal lethal damage. **Priority:** when both death-defy (roll succeeds) and second-wind
  are eligible, death-defy wins and `consumeSecondWind` is **not** called (verify via a spy lambda).
- **Shake threshold:** a hit that crosses `prevRatio > 0.25 && newRatio <= 0.25` → `crossedShakeThreshold
  == true`; a hit entirely above 0.25, or one already below 0.25, → `false`; the defy/second-wind
  early-returns → `false`.

**Behaviour-preservation oracle (must pass unchanged):**
- `GameEngineTest` R3-02 thorn tests + R17 armor/lifesteal tests drive `applyDamageToZiggurat`'s normal
  branch and the thorn tail — these are the end-to-end oracle. They pass **unchanged**.
- `ZigguratStateTest` unchanged (optionally +1 line asserting `ZigguratState` is a `Damageable`).
- `CombatResolverTest` unchanged (its two tests exercise `handleEnemyDeath`/`handleWaveComplete`, not the
  ziggurat-damage path).

**Headline test count** rises by ~+6–8 (the new resolver tests). Update the count line in CLAUDE.md +
CHANGELOG when it lands. The new class lands in the Kover-ratcheted `domain.battle.*` coverage zone.

## Acceptance
- `applyDamageToZiggurat`'s pure arithmetic + HP mutation lives in `ZigguratDamageResolver` (domain);
  `CombatResolver.applyDamageToZiggurat` is a thin adapter firing only presentation side-effects.
- `ZigguratState` implements `Damageable`; the resolver mutates via the port.
- Behaviour-preserving: full existing corpus green with **no assertion weakened**; new resolver tests
  green.
- `DomainPurityTest` / `BattleEngineLockScanTest` / both concurrency tests green.
- ADR-0012 gains a Phase 5 (Slice 1) entry; #306 stays open for the enemy + UW slices.

## Risks & mitigations
- **Risk: a subtle re-ordering changes the death-defy/second-wind priority or the lazy random roll.**
  Mitigation: lift the body verbatim; the R3-02/R17 `GameEngineTest` oracle + the new priority test pin it.
- **Risk: `crossedShakeThreshold` semantics drift** (original computes ratios around the HP write).
  Mitigation: compute `prevRatio` before the write and `newRatio` after, identical to the inline code;
  the shake-crossing test pins the three cases (crossing / above / already-below).
- **Risk: exposing `zigguratState` widens the entity's mutable surface.** Mitigation: expose as a
  read-only `val … get() = state`; callers get the already-mutable `currentHp` they had via
  `zig.currentHp` anyway — no new capability.
