# Design — ADR-0012 Phase 5, Slice 1: hoist ziggurat damage resolution to pure domain (#306)

**Date:** 2026-07-08
**Issue:** #306 (Extend EntityProtocol to enable domain hoist of combat effect-resolution + damage
application — ADR-0012 future slice). Advances #306; does **not** close it. Related: #233 (in-flight
round destroyed on config change) is already **CLOSED** by the ADR-0029 portrait lock. #233's own
"clean fix" is a *different, deferred* ADR-0012 axis — the durable-`Simulation`-owner-in-the-VM refactor
— not this effect-resolution slice. This slice is one step in the broader ADR-0012 program; #233 neither
gates nor reopens on it.
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
2. **`ZigguratState` declares `: Damageable` and adds the `override` modifier to both properties.**
   It already has `var currentHp: Double` and `var maxHp: Double` (public, `ZigguratState.kt:26-27`).
   A class `var` legally satisfies an interface `val` (the class just adds a setter), so no type change is
   needed — BUT Kotlin **requires** an explicit `override` modifier on any member implementing an abstract
   interface member. So the two property lines must become `override var currentHp: Double = …` and
   `override var maxHp: Double = …`. Without `override`, adding `: Damageable` does **not** compile
   ("'currentHp' hides member of supertype 'Damageable' and needs an 'override' modifier"). No member
   *bodies* / initializers change — only the two `override` keywords are added.
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
   The *logic sequence* is lifted from `CombatResolver.applyDamageToZiggurat` lines 117–134, **minus the
   three presentation tails that stay in the adapter**: defense mitigation (`calculateDefense`) →
   death-defy branch (rolls `random.nextDouble()` **lazily, only inside that branch**, faithful to the
   original `Random.nextDouble()` call site) → second-wind branch (invokes `consumeSecondWind()`
   **exactly where the original does** — preserving the stateful one-shot test-and-set order and
   short-circuit) → normal damage with the `coerceAtLeast(0.0)` HP floor → shake-threshold crossing
   computed as a pure boolean (`prevRatio > 0.25 && newRatio <= 0.25`). Mutates `target.currentHp`
   directly. Returns `DamageOutcome(crossedShakeThreshold)`.

   **What is NOT in the resolver** (a literal verbatim lift of :117–137 would break `DomainPurityTest`):
   the three `applyThorn(rawDamage, attacker)` calls (:121 defy branch, :127 second-wind branch, :137
   normal path) stay in the adapter (thorn calls `attacker.takeDamage` on a presentation `EnemyEntity`);
   the `!host.reducedMotion` gate + `effectEngine?.screenShake?.trigger(5f, 0.2f)` (:134–135) stay in the
   adapter; and `val zig = host.ziggurat` / `val stats = host.currentStats` (:115–116) stay in the adapter
   (`stats` is passed in). The resolver returns only the `crossedShakeThreshold` boolean derived from the
   :134 ratio comparison; the adapter decides whether to fire the shake.

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
   **removes** its now-unused `private val calculateDefense = CalculateDefense()` field **and the
   `import …domain.usecase.CalculateDefense` line (`CombatResolver.kt:10`)** — both dead after the hoist.
   Verified via ast-grep/rg that `calculateDefense` is referenced **only** at `CombatResolver.kt:31`
   (field) and `:117` (inside `applyDamageToZiggurat`) — nowhere else in the file or main tree — so the
   removal is safe (`onProjectileHitEnemy` uses `calculateDamage`; `onOrbHit` takes `damage` as a param
   and calls neither). **Dropping the import too is required:** the CI-enforced ktlint `no-unused-imports`
   / detekt `UnusedImports` lane fails on a freshly-orphaned import.
5. **`ZigguratEntity` exposes its state through the port:** `val zigguratState: Damageable get() = state`
   (the private `state: ZigguratState` field already exists; `ZigguratState : Damageable` from item 2).
   **Return the `Damageable` port type, NOT the concrete `ZigguratState`** — the resolver only consumes
   `Damageable`, so this compiles unchanged, and it keeps `ZigguratState`'s loop-thread-only mutators
   (`regenHp`/`tickAttackReady`/`onFired`/`holdReady` — today unreachable through `ZigguratEntity`'s
   public API) encapsulated. A concrete-type accessor would leak those under-lock writers to any
   presentation caller (latent, not a reachable regression this slice, but it weakens the encapsulation
   that keeps them inside `entitiesLock`). The port-typed accessor makes the "no new capability" claim
   literally true — callers see only `currentHp`/`maxHp`. (The new `ZigguratDamageResolverTest`
   constructs `ZigguratState` directly, so it is unaffected by the accessor's narrower type.)
6. **New pure-JVM `ZigguratDamageResolverTest`** (domain lane) — see Testing.
7. Doc sync (per the PR Task-List Convention):
   - **ADR-0012** — add a Phase 5 (Slice 1) entry describing this hoist. Note that the pure-domain
     "no-monitor" property of `ZigguratDamageResolver` is **convention-only** (not build-enforced —
     `BattleEngineLockScanTest` scans only `presentation/battle/engine`; see fz-3 forward-hardening note).
   - **CLAUDE.md** — headline test count (rises; see Testing).
   - **`docs/steering/source-files.md`** — ADD entries for `Damageable.kt` + `ZigguratDamageResolver.kt`,
     AND UPDATE the existing `CombatResolver` (responsibility shrank — ziggurat-damage math hoisted),
     `ZigguratState` (now implements `Damageable`), and `ZigguratEntity` (exposes `zigguratState`) entries
     so they don't drift factually wrong post-merge.
   - **CHANGELOG** — add the PR section.
   - **STATE.md** — a fragile-zone note only if the battle/economy fragile list warrants it.
   - **NOT** `docs/plans/master-plan.md` (it tracks plan-level v1.0 completion, not ADR-0012 phase
     slices — confirmed no `#306`/`ADR-0012`/`Phase` tracker rows exist there).

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
- `secondWindUsed` is a `@Volatile` engine field (`GameEngine.kt:130`); the resolver never reads/writes it
  directly — it calls the `consumeSecondWind` callback (`host::consumeSecondWind`, a genuine test-and-set
  at `GameEngine.kt:214-218`), which keeps the existing test-and-set-under-lock semantics exactly.
- `secondWindHpPercent` (`@Volatile`, `GameEngine.kt:128`): the **original reads it twice** (the guard at
  `CombatResolver.kt:125` and the restore at `:126`). The adapter collapses this into **one** by-value
  read passed as a param, so the resolver uses a single consistent snapshot for the guard + restore. This
  is behaviour-preserving (strictly safer, actually) because both original reads happen under the held
  `entitiesLock` with no interleaving writer.
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

**The behaviour-preservation net is the load-bearing part of this section, because the existing corpus
does NOT pin the branches being hoisted.** Verified during review:
- `GameEngineTest` **R3-02** thorn tests (`:1403`, `:1428`) set `eng.ziggurat!!.currentHp = 1_000_000.0`
  so the ziggurat is **unkillable** — they never reach a lethal hit, so death-defy, second-wind, and the
  shake threshold are never exercised; they assert only the thorn reflection onto the attacker.
- `GameEngineTest` **R17** armor/lifesteal tests (`:1008`, `:1056`) drive `onProjectileHitEnemy` (an
  enemy-hit path this slice does **not** touch) — **zero** coverage of `applyDamageToZiggurat`.
- So death-defy / second-wind / <25%-shake-crossing have **no existing end-to-end oracle**, and
  "existing corpus green" alone cannot prove the hoist is behaviour-preserving. A `ZigguratDamageResolverTest`
  written only against the *new* resolver is a characterization test (it codifies whatever the new code
  does) — not an independent oracle for the *old* behaviour.

**Step 1 — pre-hoist characterization tests (write FIRST, against the CURRENT
`CombatResolver.applyDamageToZiggurat`, before touching the code).** Extend `CombatResolverTest` (its
`FakeCombatHost` already wires `secondWindHpPercent` + `consumeSecondWind`, `CombatResolverTest.kt:39-45`,
and builds a real `ZigguratEntity`). Assert against **old code** (they must pass on `main` before the
hoist, then unchanged after — this is the true diff-against-baseline oracle):
- Death-defy success (inject a `stats.deathDefyChance` high enough / a deterministic roll) → `zig.currentHp
  == 1.0` after a lethal hit.
- Second-wind success (`deathDefyChance == 0`, `secondWindHpPercent > 0`) → `zig.currentHp == maxHp × pct`.
- **Defy-fails → second-wind-rescues fall-through** (`deathDefyChance > 0` but roll fails, `secondWindHpPercent
  > 0`, `consumeSecondWind` returns true) → HP restored to `maxHp × pct`. This reachable interaction (the
  original falls through the nested defy `if` at `:118-124` into the second-wind check at `:125`) is the
  exact case a mis-lift to `else if` would silently break.
- Shake-threshold crossing → `effectEngine.screenShake` fires when a hit crosses `prevRatio > 0.25 &&
  newRatio <= 0.25` with `reducedMotion == false`, and does NOT fire when `reducedMotion == true`
  (also covers the ts-3 thin-adapter shake glue on both sides of the hoist).
> Note: forcing the death-defy roll deterministically at the `CombatResolver` layer requires the roll to be
> injectable. `CombatResolver` currently constructs its own `Random` implicitly via the `Random.nextDouble()`
> call inside `applyDamageToZiggurat` — the characterization test either (a) sets `deathDefyChance = 1.0`
> (roll always succeeds) / `0.0` (branch never taken) to avoid needing injection, and drives the fall-through
> case with `deathDefyChance` between the two after the hoist makes `random` injectable, or (b) is written
> after the `random` seam exists. The plan must sequence this: if the characterization tests need the
> injectable seam, add the seam first as a pure-refactor step, run the tests green on baseline, THEN hoist.

**Step 2 — new `ZigguratDamageResolverTest`** (pure JVM, `domain/battle/engine/`, JUnit Jupiter). Drives
`resolve()` against a **real `ZigguratState`** (pure-constructible from `ResolvedStats`, per
`ZigguratStateTest.kt:20`) with injected `random` + stub/spy `consumeSecondWind` lambdas:
- **Defense mitigation:** percent + flat defense reduce HP lost (smoke assertion; `CalculateDefenseTest`
  owns the math).
- **Normal damage:** `currentHp` drops by the mitigated amount; floored at `0.0` on overkill.
- **Death-defy:** injected `random < deathDefyChance` on a lethal hit → HP `== 1.0`, `crossedShakeThreshold
  == false`, `consumeSecondWind` **not** called (spy). `random >= deathDefyChance` → normal lethal damage
  (HP floored at 0).
- **Second-wind:** `deathDefyChance == 0`, `secondWindHpPercent > 0`, `consumeSecondWind → true` on a
  lethal hit → HP `== maxHp × pct`, `crossedShakeThreshold == false`. `consumeSecondWind → false` → normal
  lethal damage.
- **Defy-fails → second-wind-rescues** (the reachable combination): `deathDefyChance > 0` with injected
  `random >= chance` (roll fails), `secondWindHpPercent > 0`, `consumeSecondWind → true` → HP `== maxHp ×
  pct` and `consumeSecondWind` **IS** called (spy). Mirrors the Step-1 fall-through case.
- **Priority:** both eligible + defy roll succeeds → death-defy wins, `consumeSecondWind` **not** called.
- **Shake threshold:** crossing hit → `crossedShakeThreshold == true`; a hit entirely above 0.25 or already
  below 0.25 → `false`; the defy/second-wind early-returns → `false`.

**Unchanged (spot-checked):**
- `ZigguratStateTest` — unchanged (optionally +1 line asserting `ZigguratState is Damageable`).
- `CombatResolverTest`'s two existing tests (`handleEnemyDeath`/`handleWaveComplete`) — unchanged (the new
  characterization tests are additions).
- `SimulationTest`'s `FakeEntity` — unaffected: `Damageable` is a **separate** port, not an `EntityProtocol`
  member, so `FakeEntity` needs no new members.

**Headline test count** rises by ~+10–12 (Step-1 characterization + Step-2 resolver tests). Update the count
line in CLAUDE.md + CHANGELOG when it lands. The new resolver lands in the Kover-ratcheted `domain.battle.*`
coverage zone.

## Acceptance
- `applyDamageToZiggurat`'s pure arithmetic + HP mutation lives in `ZigguratDamageResolver` (domain);
  `CombatResolver.applyDamageToZiggurat` is a thin adapter firing only presentation side-effects.
- `ZigguratState` implements `Damageable`; the resolver mutates via the port.
- Behaviour-preserving: full existing corpus green with **no assertion weakened**; the pre-hoist
  characterization tests (Step 1) pass on baseline AND after the hoist unchanged; new resolver tests green.
- `DomainPurityTest` / `BattleEngineLockScanTest` / both concurrency tests green.
- ADR-0012 gains a Phase 5 (Slice 1) entry; #306 stays open for the enemy + UW slices.

## Risks & mitigations
- **Risk: no existing test pins the hoisted branches, so a subtle transcription error in the "verbatim
  lift" (death-defy/second-wind priority, the defy-fails→second-wind fall-through, the lazy random roll)
  would be caught by nothing in the current corpus.** This is the load-bearing risk. Mitigation: the
  **Step-1 pre-hoist characterization tests** (written against the current `CombatResolver` and passing on
  baseline) are the independent oracle — the new resolver is diffed against baseline behaviour, not just
  against itself. Preservation rests on verbatim-lift discipline + those characterization tests + review,
  NOT on "existing corpus green" (which is satisfiable even if the lift changes behaviour).
- **Risk: `crossedShakeThreshold` semantics drift** (original computes ratios around the HP write).
  Mitigation: compute `prevRatio` before the write and `newRatio` after, identical to the inline code;
  the shake-crossing test pins the three cases (crossing / above / already-below), and the Step-1 test
  pins the adapter's `&& !reducedMotion` gate on both sides of the hoist.
- **Risk: exposing `zigguratState` widens the entity's mutable surface.** Mitigation: type the accessor
  as the `Damageable` **port** (`val zigguratState: Damageable get() = state`), so callers see only
  `currentHp`/`maxHp` — the same capability they already had via `zig.currentHp`, and `ZigguratState`'s
  loop-thread-only mutators stay encapsulated.
- **Risk: the domain-side "no monitor" invariant is convention-only** (not build-enforced; the lock-scan
  is presentation-scoped). Mitigation for this slice: the resolver is stateless/pure. Forward-hardening
  (deferred, noted in the ADR): extend `BattleEngineLockScanTest` (or a sibling) to also scan
  `domain/battle/**` for `synchronized`/`ReentrantLock`/`= Any()` ahead of the larger #306 slices that add
  more domain resolvers.
