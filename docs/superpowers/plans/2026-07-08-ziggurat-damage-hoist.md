# Ziggurat Damage Resolution Hoist (#306, ADR-0012 Phase 5 Slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoist the pure combat arithmetic + HP mutation of `CombatResolver.applyDamageToZiggurat`
into a new pure-domain `ZigguratDamageResolver` that operates on a new `Damageable` domain port,
leaving `CombatResolver` a thin presentation adapter — behaviour-preserving, with new pure-JVM
coverage for the defense / death-defy / second-wind / shake-threshold branches.

**Architecture:** Clean Architecture (`presentation → domain ← data`). The battle simulation core lives
in `domain/battle/`; the Canvas-coupled `GameEngine` + collaborators live in `presentation/battle/engine/`.
This slice moves the *decision* logic to domain and keeps side-effects (screen shake, thorn reflection)
in the adapter. Thread-safety is unchanged: the resolver holds no monitor and runs inside the engine's
already-held `entitiesLock`.

**Tech Stack:** Kotlin (JVM 17), JUnit Jupiter (pure-JVM unit tests, no Robolectric), Gradle via
`./run-gradle.sh`. Static analysis: detekt + ktlint (`./lint-kotlin.sh`), both CI-gated on NEW violations.

**Spec:** `docs/superpowers/specs/2026-07-08-ziggurat-damage-hoist-design.md` (approved; passed the
Adversarial Review Gate — 17 findings applied).

**Branch:** `arch/306-ziggurat-damage-hoist` (already created; the spec is committed there).

---

## Why the task order matters (read before starting)

The hoisted branches (death-defy, second-wind, the defy-fails→second-wind fall-through, the <25% shake
crossing) have **no existing end-to-end test oracle** — the `GameEngineTest` R3-02 thorn tests pin the
ziggurat *unkillable*, and R17 drives a different method (`onProjectileHitEnemy`). So "existing corpus
green" alone cannot prove the hoist is behaviour-preserving.

The fix is **characterization-first**: before touching the resolution logic, we (1) make the death-defy
random roll injectable in `CombatResolver` (a pure refactor), then (2) write tests against the *current*
`applyDamageToZiggurat` that pin its HP outcomes. Those tests pass on baseline, and must pass **unchanged**
after the hoist — so the new resolver is diffed against real baseline behaviour, not just against itself.
The injected `random` is threaded through to the resolver, so the same test injection controls the roll
before and after the hoist.

**Do the tasks in order.** Each ends green and committed.

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `presentation/battle/engine/CombatResolver.kt` | Modify | Task 1 (inject `random`), Task 6 (delegate to resolver, drop `calculateDefense`) |
| `app/src/test/.../presentation/battle/engine/CombatResolverTest.kt` | Modify | Task 2 (pre-hoist characterization tests) |
| `domain/battle/entity/Damageable.kt` | Create | Task 3 — the new pure port (`currentHp`/`maxHp`) |
| `domain/battle/entity/ZigguratState.kt` | Modify | Task 3 — implement `Damageable` (add `override`) |
| `presentation/battle/entities/ZigguratEntity.kt` | Modify | Task 4 — expose `zigguratState: Damageable` |
| `domain/battle/engine/ZigguratDamageResolver.kt` | Create | Task 5 — the pure resolver |
| `app/src/test/.../domain/battle/engine/ZigguratDamageResolverTest.kt` | Create | Task 5 — the resolver's unit tests |
| ADR-0012 / CLAUDE.md / source-files.md / CHANGELOG / STATE.md | Modify | Task 7 — doc sync |

All `main` paths are under `app/src/main/java/com/whitefang/stepsofbabylon/`; all test paths under
`app/src/test/java/com/whitefang/stepsofbabylon/`.

---

## Task 1: Make the death-defy roll injectable in CombatResolver (pure refactor)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt`

This adds a defaulted `random` constructor param and routes the existing `Random.nextDouble()` through it.
No behaviour change (default is the same global RNG). It's the permanent injection path — Task 6 forwards
it to the resolver. Mirrors the `CalculateDamage(random: Random = …)` precedent.

- [ ] **Step 1: Add the `random` constructor parameter**

`CombatResolver` currently declares (lines 27-29):

```kotlin
class CombatResolver(
    private val host: CombatHost,
) {
```

Change it to:

```kotlin
class CombatResolver(
    private val host: CombatHost,
    private val random: Random = Random.Default,
) {
```

- [ ] **Step 2: Route the death-defy roll through the injected `random`**

In `applyDamageToZiggurat` (line 119) the current roll is:

```kotlin
            if (Random.nextDouble() < stats.deathDefyChance) {
```

Change it to:

```kotlin
            if (random.nextDouble() < stats.deathDefyChance) {
```

(The `import kotlin.random.Random` at line 17 already covers both the type and `Random.Default`, so no
import change is needed. The `Random.nextDouble()` companion call at the SCATTER `(2..3).random()` site —
line 220 — is a different API and stays unchanged.)

- [ ] **Step 3: Verify the build and existing tests pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.*"`
Expected: BUILD SUCCESSFUL; all existing `GameEngineTest` / `CombatResolverTest` / `UWControllerTest`
green (no behaviour change).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt
git commit -m "refactor(#306): inject Random into CombatResolver death-defy roll (pre-hoist seam)"
```

---

## Task 2: Pre-hoist characterization tests (the baseline oracle)

**Files:**
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt`

Pin the HP outcomes of the *current* `applyDamageToZiggurat` for the three risky branches (defy-success,
second-wind, defy-fails→second-wind fall-through). These pass on the Task-1 code and MUST pass unchanged
after Task 6. We do NOT characterize the screen-shake trigger here — `ScreenShake` exposes no clean
"was-triggered" signal, and the spec accepts the 2-line trigger glue as untested presentation wiring; the
shake *decision* gets full coverage as a pure boolean in Task 5.

- [ ] **Step 1: Extend `FakeCombatHost` to be configurable + count second-wind consumes**

The current `FakeCombatHost` (lines 25-62) hardcodes `currentStats` / `secondWindHpPercent` /
`reducedMotion` and a private `secondWindUsed`. Replace the class header + those members so they're
constructor-injectable (defaults preserve the two existing tests) and expose a consume counter. Replace
lines 25-47:

```kotlin
    private class FakeCombatHost(
        override val ziggurat: ZigguratEntity?,
        override val simulation: Simulation = Simulation(),
        override val currentStats: ResolvedStats = ResolvedStats(),
        override val secondWindHpPercent: Double = 0.0,
        override val reducedMotion: Boolean = true,
        private val secondWindAvailable: Boolean = true,
    ) : CombatHost {
        override val conditions: BattleConditionEffects = BattleConditionEffects()
        override val tier: Int = 1
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
        override val soundManager = null
        override val strings: Strings? = FakeStrings()
        override val fortuneMultiplier: Double = 1.0
        override val cashResearchMultiplier: Double = 1.0
        override val cashBonusPercent: Double = 0.0

        var consumeSecondWindCalls = 0
            private set
        private var secondWindConsumed = false

        override fun consumeSecondWind(): Boolean {
            consumeSecondWindCalls++
            if (secondWindConsumed || !secondWindAvailable) return false
            secondWindConsumed = true
            return true
        }
```

(Leave the rest of the class — `pending`, `addPending`, `aliveEnemies`, `nearestEnemies`, `wsLevel`,
`applyLifesteal` — unchanged. Note `currentStats` / `secondWindHpPercent` / `reducedMotion` are now
constructor params, so their old `override val … = <literal>` body lines are removed by the replacement
above.)

- [ ] **Step 2: Add a fixed-RNG helper + an HP-parameterised ziggurat builder**

Add these two helpers inside `CombatResolverTest` (next to `makeZiggurat`, around line 71):

```kotlin
    /** Deterministic RNG: nextDouble() always returns [value]; roll < chance is fully controllable. */
    private fun fixedRandom(value: Double) =
        object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = value
        }

    private fun makeZigguratWithHealth(maxHealth: Double): ZigguratEntity =
        ZigguratEntity(
            screenWidth = 1080f,
            screenHeight = 1920f,
            initialStats = ResolvedStats(maxHealth = maxHealth),
            findNearestEnemies = { emptyList<EnemyEntity>() },
            onFireProjectile = { _, _, _, _ -> },
        )
```

- [ ] **Step 3: Write the three characterization tests**

Append these tests to `CombatResolverTest` (before the closing brace). They call
`applyDamageToZiggurat(rawDamage, attacker = null)` — thorn needs a non-null living attacker to fire, so
`null` isolates the ziggurat-HP behaviour under test.

```kotlin
    // --- #306 pre-hoist characterization: applyDamageToZiggurat HP outcomes (baseline oracle) ---

    @Test
    fun `CHAR death-defy success on a lethal hit leaves the ziggurat at 1 HP`() {
        val zig = makeZigguratWithHealth(100.0)
        val host = FakeCombatHost(zig, currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5))
        val resolver = CombatResolver(host, random = fixedRandom(0.0)) // 0.0 < 0.5 → defy succeeds

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(1.0, zig.currentHp, 1e-9, "death-defy success must set currentHp to exactly 1.0")
        assertEquals(0, host.consumeSecondWindCalls, "death-defy success must not touch second wind")
    }

    @Test
    fun `CHAR second wind restores maxHp times percent on a lethal hit`() {
        val zig = makeZigguratWithHealth(100.0)
        val host =
            FakeCombatHost(
                zig,
                currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
                secondWindHpPercent = 0.5,
            )
        val resolver = CombatResolver(host, random = fixedRandom(0.99))

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(50.0, zig.currentHp, 1e-9, "second wind must restore maxHp × 0.5 = 50.0")
        assertEquals(1, host.consumeSecondWindCalls, "second wind must be consumed exactly once")
    }

    @Test
    fun `CHAR failed death-defy roll falls through to second wind`() {
        val zig = makeZigguratWithHealth(100.0)
        val host =
            FakeCombatHost(
                zig,
                currentStats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
            )
        val resolver = CombatResolver(host, random = fixedRandom(0.99)) // 0.99 < 0.5 false → roll fails

        resolver.applyDamageToZiggurat(rawDamage = 200.0, attacker = null)

        assertEquals(50.0, zig.currentHp, 1e-9, "a failed defy roll must fall through to second wind (→ 50.0)")
        assertEquals(1, host.consumeSecondWindCalls, "the fall-through must consume second wind exactly once")
    }
```

- [ ] **Step 4: Run the new tests against the current code — they must PASS**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.CombatResolverTest"`
Expected: PASS (5 tests total — the 2 pre-existing + 3 new). These pin baseline behaviour BEFORE the hoist.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt
git commit -m "test(#306): pre-hoist characterization of applyDamageToZiggurat HP branches (baseline oracle)"
```

---

## Task 3: Add the `Damageable` port + implement it on `ZigguratState`

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/Damageable.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/ZigguratState.kt`
- Modify (test): `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/entity/ZigguratStateTest.kt`

- [ ] **Step 1: Create the `Damageable` port**

Create `Damageable.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Pure-domain HP surface a combat resolver needs to apply damage / heal a battle entity, without
 * touching the Canvas-coupled presentation types (ADR-0012 Phase 5, #306). Deliberately NOT a subtype
 * of [EntityProtocol] — HP is orthogonal to the positional/tickable surface, and a non-positional state
 * ([ZigguratState]) implements only this. The deferred enemy slice will declare
 * `EnemyState : EntityProtocol, Damageable`. No Android imports.
 */
interface Damageable {
    var currentHp: Double
    val maxHp: Double
}
```

- [ ] **Step 2: Implement `Damageable` on `ZigguratState`**

In `ZigguratState.kt`, change the class header (line 19-21):

```kotlin
class ZigguratState(
    initialStats: ResolvedStats,
) {
```

to:

```kotlin
class ZigguratState(
    initialStats: ResolvedStats,
) : Damageable {
```

Then add the `override` modifier to the two HP properties (lines 26-27):

```kotlin
    override var currentHp: Double = initialStats.maxHealth
    override var maxHp: Double = initialStats.maxHealth
```

(Kotlin requires `override` on members implementing an interface member; a class `var` legally satisfies
the interface's `var currentHp` / `val maxHp`. No initializers change. `Damageable` is in the same
package, so no import is needed.)

- [ ] **Step 3: Add a one-line port assertion to `ZigguratStateTest`**

Append to `ZigguratStateTest` (before the closing brace, ~line 68):

```kotlin
    @Test
    fun `ZigguratState is a Damageable exposing currentHp and maxHp`() {
        val s: Damageable = ZigguratState(ResolvedStats(maxHealth = 100.0))
        assertEquals(100.0, s.maxHp, 1e-9)
        s.currentHp = 40.0
        assertEquals(40.0, s.currentHp, 1e-9)
    }
```

- [ ] **Step 4: Run the domain-entity tests + the purity guard**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.battle.entity.*" --tests "com.whitefang.stepsofbabylon.architecture.DomainPurityTest"`
Expected: PASS. `DomainPurityTest` confirms `Damageable.kt` has zero Android/data imports.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/Damageable.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/ZigguratState.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/entity/ZigguratStateTest.kt
git commit -m "feat(#306): add Damageable domain port; ZigguratState implements it"
```

---

## Task 4: Expose `zigguratState` from `ZigguratEntity` through the port

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/ZigguratEntity.kt`

- [ ] **Step 1: Add the port-typed accessor + import**

`ZigguratEntity.kt` already imports `ZigguratState` (line 5). Add the `Damageable` import next to it:

```kotlin
import com.whitefang.stepsofbabylon.domain.battle.entity.Damageable
import com.whitefang.stepsofbabylon.domain.battle.entity.ZigguratState
```

Then add the accessor immediately after the `private val state = ZigguratState(initialStats)` line
(line 25):

```kotlin
    /**
     * The ziggurat's damage/HP surface, exposed as the [Damageable] port (NOT the concrete
     * [ZigguratState]) so a combat resolver can apply damage while [ZigguratState]'s loop-thread-only
     * mutators (regenHp/tickAttackReady/onFired/holdReady) stay encapsulated (#306, ADR-0012 Phase 5).
     */
    val zigguratState: Damageable get() = state
```

- [ ] **Step 2: Verify the build compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/ZigguratEntity.kt
git commit -m "feat(#306): expose ZigguratEntity.zigguratState as the Damageable port"
```

---

## Task 5: Create `ZigguratDamageResolver` (pure domain) + its tests (TDD)

**Files:**
- Create (test): `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/ZigguratDamageResolverTest.kt`
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/ZigguratDamageResolver.kt`

- [ ] **Step 1: Write the failing test file**

Create `ZigguratDamageResolverTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.ZigguratState
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [ZigguratDamageResolver] — the ziggurat damage/defense/death-defy/second-wind/
 * shake-threshold resolution hoisted out of presentation `CombatResolver.applyDamageToZiggurat`
 * (#306, ADR-0012 Phase 5 Slice 1). Drives the resolver against a real [ZigguratState] via the
 * [com.whitefang.stepsofbabylon.domain.battle.entity.Damageable] port, with injected RNG + a spy
 * consumeSecondWind lambda. No Robolectric, no Android.
 */
class ZigguratDamageResolverTest {
    private fun fixedRandom(value: Double) =
        object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = value
        }

    private fun state(maxHealth: Double = 100.0) = ZigguratState(ResolvedStats(maxHealth = maxHealth))

    @Test
    fun `normal hit subtracts mitigated damage`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 10.0,
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertEquals(90.0, s.currentHp, 1e-9)
        assertFalse(outcome.crossedShakeThreshold, "1.0 → 0.9 ratio does not cross 0.25")
    }

    @Test
    fun `defense percent reduces the HP lost`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 10.0,
            stats = ResolvedStats(maxHealth = 100.0, defensePercent = 0.5),
            secondWindHpPercent = 0.0,
            consumeSecondWind = { false },
        )
        assertEquals(95.0, s.currentHp, 1e-9, "50% defense halves the 10 damage to 5")
    }

    @Test
    fun `overkill floors currentHp at zero`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 500.0,
            stats = ResolvedStats(maxHealth = 100.0),
            secondWindHpPercent = 0.0,
            consumeSecondWind = { false },
        )
        assertEquals(0.0, s.currentHp, 1e-9)
    }

    @Test
    fun `death-defy success sets currentHp to 1 and does not consume second wind`() {
        val s = state()
        var consumeCalls = 0
        val resolver = ZigguratDamageResolver(random = fixedRandom(0.0)) // 0.0 < 0.5 → survives
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
                consumeSecondWind = { consumeCalls++; true },
            )
        assertEquals(1.0, s.currentHp, 1e-9)
        assertEquals(0, consumeCalls, "death-defy priority: second wind must not be consulted")
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `failed death-defy roll falls through to second wind`() {
        val s = state()
        var consumeCalls = 0
        val resolver = ZigguratDamageResolver(random = fixedRandom(0.99)) // 0.99 < 0.5 false → fails
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.5),
                secondWindHpPercent = 0.5,
                consumeSecondWind = { consumeCalls++; true },
            )
        assertEquals(50.0, s.currentHp, 1e-9, "fall-through restores maxHp × 0.5")
        assertEquals(1, consumeCalls, "the fall-through must consume second wind once")
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `second wind restores maxHp times percent when death-defy is off`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 200.0,
                stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
                secondWindHpPercent = 0.5,
                consumeSecondWind = { true },
            )
        assertEquals(50.0, s.currentHp, 1e-9)
        assertFalse(outcome.crossedShakeThreshold)
    }

    @Test
    fun `second wind unavailable takes the lethal damage`() {
        val s = state()
        val resolver = ZigguratDamageResolver()
        resolver.resolve(
            target = s,
            rawDamage = 200.0,
            stats = ResolvedStats(maxHealth = 100.0, deathDefyChance = 0.0),
            secondWindHpPercent = 0.5,
            consumeSecondWind = { false }, // already used
        )
        assertEquals(0.0, s.currentHp, 1e-9)
    }

    @Test
    fun `crossing below 25 percent flags the shake threshold`() {
        val s = state()
        s.currentHp = 30.0 // 30% of 100
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 10.0, // → 20.0 = 20%
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertEquals(20.0, s.currentHp, 1e-9)
        assertTrue(outcome.crossedShakeThreshold, "30% → 20% crosses the 0.25 boundary")
    }

    @Test
    fun `a hit already below 25 percent does not re-flag the shake threshold`() {
        val s = state()
        s.currentHp = 20.0 // already 20%
        val resolver = ZigguratDamageResolver()
        val outcome =
            resolver.resolve(
                target = s,
                rawDamage = 5.0, // → 15%
                stats = ResolvedStats(maxHealth = 100.0),
                secondWindHpPercent = 0.0,
                consumeSecondWind = { false },
            )
        assertFalse(outcome.crossedShakeThreshold, "prevRatio was already ≤ 0.25 → not a crossing")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.battle.engine.ZigguratDamageResolverTest"`
Expected: FAIL — compilation error, "unresolved reference: ZigguratDamageResolver".

- [ ] **Step 3: Create the resolver**

Create `ZigguratDamageResolver.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.Damageable
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
import kotlin.random.Random

/**
 * Pure-domain resolution of a hit against the ziggurat (#306, ADR-0012 Phase 5 Slice 1). Lifted from
 * `presentation/battle/engine/CombatResolver.applyDamageToZiggurat` (defense mitigation → death-defy →
 * second-wind → normal damage with a 0.0 HP floor → <25% shake-threshold crossing). Mutates the
 * [Damageable] target's HP directly; returns only the [DamageOutcome] the presentation adapter needs to
 * decide side-effects (screen shake). Thorn reflection stays in the adapter (it calls a presentation
 * entity's takeDamage). No Android imports; holds no monitor — the caller invokes it inside the engine's
 * held `entitiesLock`.
 */
class ZigguratDamageResolver(
    private val calculateDefense: CalculateDefense = CalculateDefense(),
    private val random: Random = Random.Default,
) {
    /** @property crossedShakeThreshold the HP% dropped through 25% this hit (adapter fires screen shake). */
    data class DamageOutcome(val crossedShakeThreshold: Boolean)

    /**
     * Applies [rawDamage] to [target], mutating [Damageable.currentHp]. [consumeSecondWind] is the
     * caller's one-shot test-and-set (invoked at most once, only on a lethal hit when death-defy did not
     * already save the ziggurat) — matching the pre-hoist inline order exactly.
     */
    fun resolve(
        target: Damageable,
        rawDamage: Double,
        stats: ResolvedStats,
        secondWindHpPercent: Double,
        consumeSecondWind: () -> Boolean,
    ): DamageOutcome {
        val mitigated = calculateDefense(rawDamage, stats)
        if (target.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (random.nextDouble() < stats.deathDefyChance) {
                target.currentHp = 1.0
                return DamageOutcome(crossedShakeThreshold = false)
            }
        }
        if (target.currentHp - mitigated <= 0.0 && secondWindHpPercent > 0.0 && consumeSecondWind()) {
            target.currentHp = target.maxHp * secondWindHpPercent
            return DamageOutcome(crossedShakeThreshold = false)
        }
        val prevHpRatio = target.currentHp / target.maxHp
        target.currentHp = (target.currentHp - mitigated).coerceAtLeast(0.0)
        val newHpRatio = target.currentHp / target.maxHp
        return DamageOutcome(crossedShakeThreshold = prevHpRatio > 0.25 && newHpRatio <= 0.25)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.battle.engine.ZigguratDamageResolverTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/ZigguratDamageResolver.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/ZigguratDamageResolverTest.kt
git commit -m "feat(#306): add pure-domain ZigguratDamageResolver + tests"
```

---

## Task 6: Rewrite `CombatResolver.applyDamageToZiggurat` to delegate

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt`

The behaviour-preservation gate for this task is the Task-2 characterization tests: they must pass
**unchanged**.

- [ ] **Step 1: Add the resolver field, threading the injected `random` through**

Just below the existing field declarations (current lines 30-31 declare `calculateDamage` and
`calculateDefense`), add the resolver field. After this task, `random` is used only to construct the
resolver.

```kotlin
    private val calculateDamage = CalculateDamage()
    private val zigguratDamageResolver = ZigguratDamageResolver(random = random)
```

(This REPLACES the two-line block that currently reads:
```kotlin
    private val calculateDamage = CalculateDamage()
    private val calculateDefense = CalculateDefense()
```
— i.e. `calculateDefense` is deleted, `zigguratDamageResolver` added.)

- [ ] **Step 2: Remove the now-unused `CalculateDefense` import; add the resolver import**

Delete this import line (current line 10):

```kotlin
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
```

Add (in the `domain.battle.engine` import group, next to the existing `SimulationMath` import at line 4):

```kotlin
import com.whitefang.stepsofbabylon.domain.battle.engine.ZigguratDamageResolver
```

(Leaving the orphaned `CalculateDefense` import would fail the CI ktlint `no-unused-imports` / detekt
`UnusedImports` lane. `CalculateDamage` stays — `onProjectileHitEnemy` still uses it.)

- [ ] **Step 3: Replace the `applyDamageToZiggurat` body with a delegating adapter**

Replace the entire current method (lines 111-138):

```kotlin
    fun applyDamageToZiggurat(
        rawDamage: Double,
        attacker: EnemyEntity?,
    ) {
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        val mitigated = calculateDefense(rawDamage, stats)
        if (zig.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (random.nextDouble() < stats.deathDefyChance) {
                zig.currentHp = 1.0
                applyThorn(rawDamage, attacker)
                return
            }
        }
        if (zig.currentHp - mitigated <= 0.0 && host.secondWindHpPercent > 0.0 && host.consumeSecondWind()) {
            zig.currentHp = zig.maxHp * host.secondWindHpPercent
            applyThorn(rawDamage, attacker)
            return
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
```

with the thin adapter:

```kotlin
    fun applyDamageToZiggurat(
        rawDamage: Double,
        attacker: EnemyEntity?,
    ) {
        val zig = host.ziggurat ?: return
        // #306 (ADR-0012 Phase 5): the pure defense/death-defy/second-wind/HP-floor/shake-threshold
        // resolution is hoisted to the domain ZigguratDamageResolver, operating on the Damageable port.
        // This adapter keeps only the presentation side-effects: the reducedMotion-gated screen shake and
        // thorn reflection (which calls a presentation EnemyEntity's takeDamage → out of domain scope).
        // consumeSecondWind stays the engine's one-shot test-and-set; both run inside the held entitiesLock.
        val outcome =
            zigguratDamageResolver.resolve(
                target = zig.zigguratState,
                rawDamage = rawDamage,
                stats = host.currentStats,
                secondWindHpPercent = host.secondWindHpPercent,
                consumeSecondWind = host::consumeSecondWind,
            )
        if (outcome.crossedShakeThreshold && !host.reducedMotion) {
            host.effectEngine?.screenShake?.trigger(5f, 0.2f)
        }
        applyThorn(rawDamage, attacker)
    }
```

- [ ] **Step 4: Run the characterization tests — they must PASS UNCHANGED**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.CombatResolverTest"`
Expected: PASS (the same 5 tests from Task 2 — proving the hoist preserved baseline HP behaviour).

- [ ] **Step 5: Run the full battle-engine test suite (R3-02 thorn + everything else)**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.*"`
Expected: PASS — `GameEngineTest` R3-02 thorn tests (which drive `applyDamageToZiggurat` end-to-end) green,
proving the thorn tail + normal path still work through the new adapter.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt
git commit -m "refactor(#306): CombatResolver.applyDamageToZiggurat delegates to ZigguratDamageResolver"
```

---

## Task 7: Full gate + documentation sync

**Files:**
- Modify: `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md`
- Modify: `CLAUDE.md`
- Modify: `docs/steering/source-files.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/agent/STATE.md` (only the objective/recently-shipped lines; NOT prior RUN_LOG entries)

- [ ] **Step 1: Run the complete PR gate to get the true test count + static-analysis result**

Run: `./run-gradle.sh :app:testDebugUnitTest 2>&1 | tail -n 20`
Then: `./run-gradle.sh :app:detekt` and `./lint-kotlin.sh`
Expected: all green. Record the total JVM test count printed (was 1317; expect ~1329 = 1317 + 3 char + 9
resolver + 1 port assertion). Use the ACTUAL printed number in the doc edits below — do not guess.

- [ ] **Step 2: Add the ADR-0012 Phase 5 entry**

In `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md`, under "Future Phases" after the Phase 4
block, add:

```markdown
**Phase 5 (#306, Slice 1 — ziggurat damage resolution, <date>):** The first slice of the tracked
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
```

(Replace `<date>` with today's date, 2026-07-08.)

- [ ] **Step 3: Update the CLAUDE.md headline test count**

In `CLAUDE.md`, find the "Headline count" line (currently `**Headline count: 1317 JVM tests + 9
instrumented tests.**`) and update `1317` to the actual number from Step 1. If the "Notable guards" list
warrants it, no change is needed (the new resolver is covered by its own test, not an architecture guard).

- [ ] **Step 4: Update source-files.md (new files + existing-entry drift)**

In `docs/steering/source-files.md`:
- ADD entries for `domain/battle/entity/Damageable.kt` (pure HP port) and
  `domain/battle/engine/ZigguratDamageResolver.kt` (pure ziggurat damage resolution).
- UPDATE the `CombatResolver` entry — note ziggurat-damage resolution was hoisted to
  `ZigguratDamageResolver`; the collaborator now delegates + keeps thorn/shake glue.
- UPDATE the `ZigguratState` entry — now implements `Damageable`.
- UPDATE the `ZigguratEntity` entry — exposes `zigguratState: Damageable`.

(Match the file's existing entry format. If any of these files aren't individually listed, add the two new
ones and update whatever aggregate battle-engine entry exists.)

- [ ] **Step 5: Add the CHANGELOG entry**

In `CHANGELOG.md`, add a section under `[Unreleased]` (create it if absent) summarising: "#306 (ADR-0012
Phase 5 Slice 1): hoisted ziggurat damage resolution to the pure-domain `ZigguratDamageResolver` via a new
`Damageable` port; `CombatResolver.applyDamageToZiggurat` is now a thin adapter. Behaviour-preserving;
+N JVM tests (pre-hoist characterization + resolver)." Use the real count.

- [ ] **Step 6: Update STATE.md**

In `docs/agent/STATE.md`, update the `## Current objective` block to reflect that #306 Slice 1 landed and
#306 stays open for the enemy + UW slices. Add a one-line fragile-zone note under the battle/economy list
if warranted (the resolver is pure + guarded by tests; a note is optional). Do NOT edit prior RUN_LOG
entries — that's a separate `/checkpoint` step.

- [ ] **Step 7: Final full gate**

Run: `./run-gradle.sh :app:testDebugUnitTest && ./run-gradle.sh :app:detekt && ./lint-kotlin.sh && ./run-gradle.sh :app:assembleDebug 2>&1 | tail -n 15`
Expected: all BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add docs/ CLAUDE.md CHANGELOG.md
git commit -m "docs(#306): ADR-0012 Phase 5 Slice 1 doc sync (ziggurat damage hoist)"
```

---

## Notes for the implementer

- **`./run-gradle.sh` not `./gradlew`** — Gradle buffers output in non-TTY environments; the wrapper
  script avoids hangs.
- **Behaviour-preservation is the whole point.** If a Task-2 characterization test changes result between
  Task 2 (baseline) and Task 6 (post-hoist), STOP — the lift is not faithful. Diff the resolver body against
  the original `applyDamageToZiggurat` lines 117-134 line-by-line before debugging anything else.
- **Do not add a `synchronized` block, `ReentrantLock`, or `= Any()` monitor to `ZigguratDamageResolver`** —
  it must stay a stateless pure object (the caller holds `entitiesLock`). This isn't machine-enforced on
  domain files yet (see the ADR caveat), so it's on you + review.
- **`DomainPurityTest`** will fail the build if `Damageable.kt` or `ZigguratDamageResolver.kt` imports any
  `android.`/`androidx.` package or the `data` layer. Keep their imports to `CalculateDefense` /
  `ResolvedStats` / `Damageable` / `kotlin.random.Random`.
```

