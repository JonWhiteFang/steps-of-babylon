# #306 Slice 2 — Enemy damage/death resolution hoist (ADR-0012 Phase 5)

**Date:** 2026-07-09
**Issue:** #306 (Extend EntityProtocol to enable domain hoist of combat effect-resolution + damage application)
**Predecessor:** Slice 1 — ziggurat damage resolution (`ZigguratDamageResolver` + `Damageable` port), PR #413, `ac9dbb4`
**Status:** Draft — pending adversarial review

## Summary

Hoist the **enemy** damage/death arithmetic out of the presentation layer into the pure domain, mirroring
the pattern Slice 1 established for the ziggurat. Today `EnemyEntity.takeDamage` (presentation) owns the
armor-absorption / corpse-guard / HP-subtraction / death-detection logic, and the enemy's damage-relevant
state (`currentHp`, `maxHp`, `armorHits`) lives on the presentation entity. After this slice:

- Enemy HP/armor state lives in the pure-domain `EnemyState` (which already owns movement/attack state),
  exposed via a new `DamageableEnemy` port (extends the existing `Damageable`).
- A new pure `EnemyDamageResolver` performs the armor/corpse/HP/death arithmetic, returning an outcome the
  presentation adapter uses to fire the death cascade.
- The SCATTER child-descriptor math (count roll + per-child HP/damage/offset) moves to a pure `ScatterSplit`
  helper; the actual `EnemyEntity` construction (needs presentation lambdas + condition multipliers) stays
  in `CombatResolver`.

**Behaviour-preserving.** No gameplay, balance, or visual change. The existing test corpus plus new
pre-hoist characterization tests are the oracle.

## Scope

**In scope (this slice):**
- Enemy `takeDamage` arithmetic → pure `EnemyDamageResolver`.
- Enemy `currentHp`/`maxHp`/`armorHits` state → `EnemyState` behind a `DamageableEnemy` port.
- Enemy `onDeath` firing → stays presentation (the adapter), because the death cascade already delegates
  its *data* to `Simulation` and its *feedback* is Canvas/sound-coupled.
- SCATTER child-descriptor math → pure `ScatterSplit` helper.

**Explicitly OUT of scope (remaining #306 slices, unchanged by this spec):**
- All `UWController.when(type)` effect bodies (DEATH_WAVE / CHAIN_LIGHTNING / BLACK_HOLE / POISON_SWAMP
  damage + pull).
- `CombatResolver.onProjectileHitEnemy` / `onOrbHit` knockback + lifesteal side-effects (they *call* the new
  `takeDamage`, but their own knockback/lifesteal hoist is a later slice).
- Enemy knockback impulse (`applyKnockback`) — already domain (`EnemyState.applyKnockback`); untouched.

## Current state (grounding)

- `presentation/battle/entities/EnemyEntity.kt` — holds `currentHp` (var), `maxHp` (val), `armorHits`
  (var, private set), and `takeDamage(amount): Double`:
  ```
  if (!isAlive) return 0.0                 // #146 corpse guard
  if (armorHits > 0) { armorHits--; return 0.0 }   // #17 armor absorb
  currentHp -= amount                       // NOTE: no HP floor (can go negative)
  if (currentHp <= 0.0) { isAlive = false; onDeath(this) }
  return amount
  ```
  `isAlive` is an `override var` on the shared `Entity` base (read by collision sweep, render,
  `aliveEnemies()`). Delegates movement/attack to `domain/battle/entity/EnemyState`.
- `EnemyState` — pure movement + attack cooldown; `update(dt): Boolean` (true on an attack frame).
- `CombatResolver.handleEnemyDeath(enemy)` — the death cascade: `simulation.recordEnemyKilled()` +
  `creditCash` (kill reward via `SimulationMath.killCashReward`) + sound + death particles + floating cash
  text + boss screen-shake + `SimulationEvent.BossKilled` + `creditSteps` + `SimulationEvent.StepReward` +
  SCATTER split (constructs `childCount` `EnemyEntity` children with halved HP/damage). **The reward data
  already lives in `Simulation`; only feedback + construction are presentation.**
- External readers that constrain the API: `BattleAnnouncer` reads `.currentHp`/`.maxHp`; `BattleRenderer`
  reads both; `CombatResolver` SCATTER reads `enemy.maxHp`/`enemy.damage`; `WaveSpawner` sets `armorHits`
  via the ctor. So `EnemyEntity` must keep public `currentHp`/`maxHp`/`armorHits` as delegating properties.

## Design

### 1. Domain port: `DamageableEnemy`

New in `domain/battle/entity/`:

```kotlin
/**
 * Enemy HP + armor surface a combat resolver mutates when applying damage. Extends [Damageable]
 * (shared HP surface) with the armor-charge count that absorbs a hit before HP is lost (#17).
 * Implemented by [EnemyState]. No Android imports.
 */
interface DamageableEnemy : Damageable {
    var armorHits: Int
}
```

`Damageable` (from Slice 1) stays unchanged: `var currentHp: Double; val maxHp: Double`. Armor is
enemy-specific, so it does NOT pollute the shared `Damageable` (the ziggurat has no armor).

**ADR deviation, documented:** ADR-0012 Phase 5 wrote that the enemy slice "will declare
`EnemyState : EntityProtocol, Damageable`". That is wrong in detail — `EntityProtocol.update(dt): Unit`
clashes with `EnemyState.update(dt): Boolean`, and the `EntityProtocol` role already lives on the
presentation `Entity` (which `EnemyEntity` extends). `EnemyState` therefore implements
`DamageableEnemy` only. This slice's ADR amendment will correct the wording.

### 2. `EnemyState` owns HP + armor

`EnemyState` gains three fields and implements `DamageableEnemy`:

```kotlin
class EnemyState(
    // existing movement/attack params …
    initialHp: Double,
    override val maxHp: Double,
    initialArmorHits: Int,
) : DamageableEnemy {
    override var currentHp: Double = initialHp
    override var armorHits: Int = initialArmorHits
    // existing x/y/attack state …
}
```

`currentHp`/`armorHits` are plain `var` (loop-thread mutated under the engine's `entitiesLock`, exactly as
today — no new happens-before requirement; matches how `ZigguratState`'s HP is a plain `var`).

### 3. `EnemyEntity` delegates + becomes a thin damage adapter

- `EnemyEntity` constructs `EnemyState` with the HP/armor params it currently holds, and delegates
  `currentHp` (get/set), `maxHp` (get), `armorHits` (get) to the state — preserving every external reader.
- `takeDamage` becomes the presentation adapter:
  ```kotlin
  fun takeDamage(amount: Double): Double {
      val outcome = ENEMY_DAMAGE_RESOLVER.resolve(state, amount, isAlive)
      if (outcome.died) {
          isAlive = false     // preserve pre-hoist order: flag BEFORE onDeath
          onDeath(this)
      }
      return outcome.dealt
  }
  ```
  `isAlive` stays on `Entity` (shared seam); the adapter passes it into the resolver and flips it on death,
  then fires the presentation `onDeath` cascade — the same split Slice 1 used (thorn/shake stayed in the
  adapter).

### 4. Pure `EnemyDamageResolver`

New in `domain/battle/engine/`:

```kotlin
/**
 * Pure-domain resolution of a hit against an enemy (#306, ADR-0012 Phase 5 Slice 2). Lifted verbatim from
 * EnemyEntity.takeDamage: corpse guard (#146) → armor absorb (#17) → HP subtraction (NO floor — enemy HP
 * may go negative, matching pre-hoist) → death detection. Mutates the [DamageableEnemy] target's HP/armor;
 * returns the [Outcome] the presentation adapter needs (damage actually dealt for lifesteal/knockback
 * gating; whether the hit was lethal so the adapter flips isAlive + fires onDeath). No Android imports;
 * holds no monitor — the caller invokes it inside the engine's held `entitiesLock`.
 */
class EnemyDamageResolver {
    data class Outcome(val dealt: Double, val died: Boolean)

    fun resolve(target: DamageableEnemy, amount: Double, isAlive: Boolean): Outcome {
        if (!isAlive) return Outcome(dealt = 0.0, died = false)
        if (target.armorHits > 0) {
            target.armorHits--
            return Outcome(dealt = 0.0, died = false)
        }
        target.currentHp -= amount
        return Outcome(dealt = amount, died = target.currentHp <= 0.0)
    }
}
```

A single shared stateless instance lives on `EnemyEntity`'s companion (`ENEMY_DAMAGE_RESOLVER`) to avoid
per-enemy allocation (many enemies per round). It takes no constructor deps (no RNG — unlike the ziggurat's
death-defy roll), so a shared instance is safe and pure.

### 5. Pure `ScatterSplit` helper

The SCATTER child-descriptor math (count roll + per-child HP/damage/offset) moves to a pure helper; the
`EnemyEntity` construction stays in `CombatResolver` (needs `onDeath`/`onMeleeHit` lambdas, `zig.originX/Y`,
and `EnemyScaler.scaleSpeed × conditions.enemySpeedMultiplier`).

```kotlin
/**
 * Pure descriptors for the SCATTER-on-death child split (#306 Slice 2). The count is rolled from [random]
 * (2..3, verbatim from the pre-hoist `(2..3).random()`); each child gets half the parent's HP/damage and a
 * fanned-out X offset. CombatResolver maps these onto presentation EnemyEntity children (speed + lambdas +
 * ziggurat target stay presentation). No Android imports.
 */
object ScatterSplit {
    data class Child(val hp: Double, val maxHp: Double, val damage: Double, val offsetX: Float)

    fun children(parentMaxHp: Double, parentDamage: Double, random: Random): List<Child> {
        val count = random.nextInt(2, 4)          // 2..3 inclusive, == (2..3).random()
        return (0 until count).map { i ->
            Child(
                hp = parentMaxHp * 0.5,
                maxHp = parentMaxHp * 0.5,
                damage = parentDamage * 0.5,
                offsetX = (i - count / 2f) * 15f,
            )
        }
    }
}
```

`CombatResolver.handleEnemyDeath`'s SCATTER branch becomes:
```kotlin
if (enemy.enemyType == EnemyType.SCATTER) {
    val zig = host.ziggurat ?: return
    ScatterSplit.children(enemy.maxHp, enemy.damage, random).forEach { c ->
        host.addPending(
            EnemyEntity(
                enemyType = EnemyType.BASIC,
                currentHp = c.hp,
                maxHp = c.maxHp,
                speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * host.conditions.enemySpeedMultiplier,
                damage = c.damage,
                targetX = zig.originX,
                targetY = zig.originY,
                onDeath = ::handleEnemyDeath,
                onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
            ).apply { x = enemy.x + c.offsetX; y = enemy.y; initDistance() },
        )
    }
}
```
`CombatResolver` already holds a `random` (used by `ZigguratDamageResolver`); reuse it so the split stays
seedable in tests. **Verify parity:** `(2..3).random()` on the default RNG == `Random.nextInt(2, 4)`; the
per-child `(i - childCount / 2f) * 15f` offset formula is copied verbatim (integer `childCount / 2f`
matches the original — `count` is `Int`, so `count / 2f` is the same float division).

## Thread-safety

Unchanged from Slice 1. `EnemyDamageResolver` and `ScatterSplit` hold no monitor and are invoked only from
inside the engine's held `entitiesLock` (collision callbacks, UW effect bodies, and the death handler all
run under the tick lock; SCATTER `addPending` queues into the engine's `pendingAdd`). `EnemyState`'s new
`currentHp`/`armorHits` are plain `var`s mutated only under that lock — the same guarantee `ZigguratState`
already relies on. No lock-order change (`entitiesLock` outer → `effectsLock` inner).

**Guard caveat (same as Slice 1):** `BattleEngineLockScanTest` scans only `presentation/battle/engine`, so
the new domain resolver isn't covered by the #372 no-nested-lock tripwire. Extending the scan to
`domain/battle/**` remains deferred forward-hardening (already noted in ADR-0012 Slice 1).

## Testing strategy

Behaviour-preservation; the existing corpus is the oracle. New tests:

- **`EnemyDamageResolverTest`** (pure JVM) — the four branches: corpse guard (`isAlive=false` → dealt 0.0,
  not died, HP untouched); armor absorb (armor>0 → dealt 0.0, armor decremented, not died, HP untouched);
  non-lethal hit (dealt=amount, HP reduced, not died); lethal hit (dealt=amount, `died=true`); and the
  **no-floor** property (HP goes negative on overkill, `died=true`) — the enemy-specific divergence from the
  ziggurat resolver, worth an explicit pin.
- **`EnemyStateTest`** (existing, extend) — add HP/armor field cases: ctor seeds `currentHp`/`maxHp`/
  `armorHits`; the `DamageableEnemy` mutators behave. (Movement/attack cases unchanged.)
- **`ScatterSplitTest`** (pure JVM) — count ∈ 2..3 (seeded RNG at both ends of the range); each child hp/
  maxHp/damage halved; offsets fan out symmetrically; the exact offset values for count=2 and count=3
  match the pre-hoist formula.
- **`CombatResolverTest`** (existing, extend) — the SCATTER split still produces 2..3 children with halved
  HP via `handleEnemyDeath` (the integration oracle over the new helper); a **pre-hoist characterization**
  test for the armor-absorb + corpse-guard `takeDamage` return contract if not already covered, so the
  adapter re-wire is pinned by an assertion that predates the change.
- **`GameEngineTest` / `CollisionSystemScratchTest`** — unchanged (the #146/#125 corpse-double-credit guards
  still pass through the same `takeDamage` → `onDeath` path; the re-wire must not weaken them).

Verification: `./run-gradle.sh :app:testDebugUnitTest` green (net +tests, no assertion weakened);
`:app:detekt` + `./lint-kotlin.sh` clean; `:app:koverVerifyDebug` (the concurrency/economy ratchet covers
`domain.battle.*` — new pure code should raise, not lower, coverage).

## Fragile-zone touch list (for the review gate + `concurrency-reviewer`)

This diff touches `presentation/battle/engine/` (CombatResolver) and `domain/battle/**` (new resolver +
EnemyState) — the `concurrency-reviewer` lane is **mandatory** (CLAUDE.md #372/ADR-0038). Fragile
invariants in play: `EnemyEntity.takeDamage` corpse guard (#146), armor-absorb damage-dealt gating (#17),
the death-cascade double-credit guards (#125/#146), and the SCATTER-bypasses-the-counter history (#146).

## Documentation updates (per the PR Task-List Convention)

- `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md` — add the Slice 2 entry under Phase 5; correct
  the `EnemyState : EntityProtocol, Damageable` wording to `DamageableEnemy`-only (with rationale).
- `CLAUDE.md` — Battle Renderer section: note enemy damage/HP now domain-hoisted (Slice 2); update the
  headline test count.
- `docs/steering/source-files.md` — new files: `EnemyDamageResolver`, `DamageableEnemy`, `ScatterSplit`;
  updated responsibility shape for `EnemyState` (now owns HP/armor) + `EnemyEntity` (thin damage adapter).
- `CHANGELOG.md` — PR section.
- `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md` — per the End-of-Run writes.

## Risks & mitigations

- **Re-wiring `takeDamage` could weaken the #146 corpse / #125 no-cache guards.** Mitigation: the resolver
  reproduces the guard verbatim (`if (!isAlive) return`); the adapter keeps the `isAlive = false` → `onDeath`
  order; the existing `GameEngineTest`/`CollisionSystemScratchTest` double-credit tests stay green unchanged.
- **Moving HP into `EnemyState` could break an external reader.** Mitigation: `EnemyEntity` keeps public
  delegating `currentHp`/`maxHp`/`armorHits`; the grep-confirmed readers (`BattleAnnouncer`,
  `BattleRenderer`, SCATTER, `WaveSpawner`) compile against the unchanged surface.
- **SCATTER RNG parity.** Mitigation: `Random.nextInt(2, 4)` == `(2..3).random()`; pin both range ends in
  `ScatterSplitTest` with a seeded RNG, and keep the integration oracle in `CombatResolverTest`.
- **`EnemyState` gains a non-movement responsibility.** Accepted: it mirrors `ZigguratState` (which owns HP +
  regen + attack + rapid-fire), so "entity simulation state" is the established shape for the state classes.
```
