# #306 Slice 2 — Enemy Damage/Death Resolution Hoist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoist enemy damage/death arithmetic out of the presentation `EnemyEntity` into the pure domain (a `DamageableEnemy` port on `EnemyState` + a pure `EnemyDamageResolver` + a pure `ScatterSplit` helper), mirroring the Slice-1 ziggurat pattern, with zero behaviour change.

**Architecture:** `EnemyState` (already the pure movement/attack state) gains enemy HP + armor behind a new `DamageableEnemy : Damageable` port. A stateless `EnemyDamageResolver` performs the corpse-guard/armor-absorb/HP-subtract/death-detect arithmetic on that port. `EnemyEntity.takeDamage` becomes a thin adapter that delegates to the resolver, flips `isAlive`, and fires the (already domain-backed) `onDeath` cash/steps/boss/SCATTER cascade. SCATTER child-descriptor math moves to a pure `ScatterSplit`; `EnemyEntity` construction stays in `CombatResolver`. All new domain code is monitor-free and runs inside the engine's held `entitiesLock`.

**Tech Stack:** Kotlin (JVM target 17), JUnit Jupiter + kotlinx-coroutines-test (pure JVM unit lane), detekt + ktlint (CI-gated). Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-07-09-306-slice2-enemy-damage-hoist-design.md` (adversarial-review passed — concurrency lane SAFE).

---

## File Structure

**Create (domain — pure, no Android):**
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/DamageableEnemy.kt` — enemy HP+armor port (`Damageable` + `var armorHits`).
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolver.kt` — pure damage/death arithmetic.
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplit.kt` — pure SCATTER child descriptors.

**Modify:**
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyState.kt` — implement `DamageableEnemy`; add `currentHp`/`maxHp`/`armorHits` (defaulted ctor params).
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/EnemyEntity.kt` — delegate HP/armor to state; `takeDamage` → adapter; companion `ENEMY_DAMAGE_RESOLVER`.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt` — SCATTER branch uses `ScatterSplit`.

**Create (tests — pure JVM):**
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolverTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplitTest.kt`

**Modify (tests):**
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyStateTest.kt` — add HP/armor field cases.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt` — SCATTER integration + pre-hoist `takeDamage` characterization.

**Ordering rationale:** Task 1 (port) → Task 2 (state owns HP/armor) → Task 3 (resolver) → Task 4 (EnemyEntity adapter, the behaviour-preserving swap) → Task 5 (ScatterSplit) → Task 6 (CombatResolver SCATTER re-wire) → Task 7 (full verification + docs). Each task builds green before the next.

---

## Task 1: `DamageableEnemy` port

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/DamageableEnemy.kt`

- [ ] **Step 1: Create the port interface**

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.entity

/**
 * Enemy HP + armor surface a combat resolver mutates when applying damage (#306, ADR-0012 Phase 5
 * Slice 2). Extends [Damageable] (the shared currentHp/maxHp surface) with the armor-charge count that
 * absorbs a hit before HP is lost (#17). Armor is enemy-specific — the ziggurat has none — so it lives
 * here, not on [Damageable]. Implemented by [EnemyState]. No Android imports.
 */
interface DamageableEnemy : Damageable {
    var armorHits: Int
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin > /tmp/t1.log 2>&1; tail -n 5 /tmp/t1.log`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/DamageableEnemy.kt
git commit -m "feat(#306): DamageableEnemy port (enemy HP+armor domain surface)"
```

---

## Task 2: `EnemyState` owns HP + armor

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyState.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyStateTest.kt`

- [ ] **Step 1: Write the failing test (HP/armor fields + DamageableEnemy mutators)**

Append these tests to `EnemyStateTest.kt` (inside the class, after the `knockback shifts position` test):

```kotlin
    @Test
    fun `seeds currentHp maxHp and armorHits from the constructor`() {
        val s =
            EnemyState(
                targetX = 100f, targetY = 0f, speed = 50f, isRanged = false, attackInterval = 1f,
                initialHp = 30.0, maxHp = 40.0, initialArmorHits = 2,
            )
        assertEquals(30.0, s.currentHp, 1e-9)
        assertEquals(40.0, s.maxHp, 1e-9)
        assertEquals(2, s.armorHits)
    }

    @Test
    fun `exposes currentHp and armorHits as mutable via the DamageableEnemy port`() {
        val s: DamageableEnemy =
            EnemyState(
                targetX = 100f, targetY = 0f, speed = 50f, isRanged = false, attackInterval = 1f,
                initialHp = 10.0, maxHp = 10.0, initialArmorHits = 1,
            )
        s.currentHp -= 4.0
        s.armorHits--
        assertEquals(6.0, s.currentHp, 1e-9)
        assertEquals(0, s.armorHits)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*EnemyStateTest*" > /tmp/t2.log 2>&1; tail -n 15 /tmp/t2.log`
Expected: compile failure — `EnemyState` has no `initialHp`/`maxHp`/`initialArmorHits` params and no `currentHp`/`armorHits`/`maxHp` members.

- [ ] **Step 3: Add HP/armor state + implement the port**

In `EnemyState.kt`, change the class declaration to implement `DamageableEnemy` and add the three defaulted ctor params + the backing members. The **defaults are load-bearing** — the movement-only `melee()` test helper constructs `EnemyState` without HP args and must keep compiling (spec §2 Ctor compatibility note).

Replace the class header:

```kotlin
class EnemyState(
    private val targetX: Float,
    private val targetY: Float,
    private val speed: Float,
    private val isRanged: Boolean,
    private val attackInterval: Float,
    initialHp: Double = 0.0,
    override val maxHp: Double = 0.0,
    initialArmorHits: Int = 0,
) : DamageableEnemy {
    override var currentHp: Double = initialHp
    override var armorHits: Int = initialArmorHits

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
```

(The rest of the class body — `attackCooldown`, `initialDist`, `stopDistance`, `spawn`, `update`,
`applyKnockback`, `companion object` — is unchanged.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*EnemyStateTest*" > /tmp/t2.log 2>&1; tail -n 15 /tmp/t2.log`
Expected: `BUILD SUCCESSFUL`, all `EnemyStateTest` cases pass (the original movement cases + the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyState.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/entity/EnemyStateTest.kt
git commit -m "feat(#306): EnemyState owns enemy HP+armor via DamageableEnemy"
```

---

## Task 3: `EnemyDamageResolver` (pure damage/death arithmetic)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolver.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolverTest.kt`

- [ ] **Step 1: Write the failing test**

Create `EnemyDamageResolverTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.DamageableEnemy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [EnemyDamageResolver] (#306 Slice 2). Pins the four branches lifted verbatim from
 * the pre-hoist `EnemyEntity.takeDamage`: corpse guard (#146), armor absorb (#17), non-lethal hit, lethal
 * hit — plus the enemy-specific NO-HP-FLOOR property (HP goes negative on overkill), which diverges from
 * the ziggurat resolver's `coerceAtLeast(0.0)`.
 */
class EnemyDamageResolverTest {
    private class FakeEnemy(
        override var currentHp: Double,
        override val maxHp: Double,
        override var armorHits: Int = 0,
    ) : DamageableEnemy

    private val resolver = EnemyDamageResolver()

    @Test
    fun `corpse guard returns zero and does not mutate when not alive`() {
        val e = FakeEnemy(currentHp = 5.0, maxHp = 10.0, armorHits = 1)
        val out = resolver.resolve(e, amount = 3.0, isAlive = false)
        assertEquals(0.0, out.dealt, 1e-9)
        assertFalse(out.died)
        assertEquals(5.0, e.currentHp, 1e-9, "corpse guard must not touch HP")
        assertEquals(1, e.armorHits, "corpse guard must not touch armor")
    }

    @Test
    fun `armor charge absorbs the hit and is consumed with zero dealt`() {
        val e = FakeEnemy(currentHp = 10.0, maxHp = 10.0, armorHits = 2)
        val out = resolver.resolve(e, amount = 4.0, isAlive = true)
        assertEquals(0.0, out.dealt, 1e-9, "armor-absorbed hit deals no HP damage (#17)")
        assertFalse(out.died)
        assertEquals(10.0, e.currentHp, 1e-9, "HP untouched while armor absorbs")
        assertEquals(1, e.armorHits, "one armor charge consumed")
    }

    @Test
    fun `non-lethal hit reduces HP and reports damage dealt`() {
        val e = FakeEnemy(currentHp = 10.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 4.0, isAlive = true)
        assertEquals(4.0, out.dealt, 1e-9)
        assertFalse(out.died)
        assertEquals(6.0, e.currentHp, 1e-9)
    }

    @Test
    fun `lethal hit reports died`() {
        val e = FakeEnemy(currentHp = 3.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 3.0, isAlive = true)
        assertEquals(3.0, out.dealt, 1e-9)
        assertTrue(out.died, "HP reaching exactly 0.0 is lethal (<= 0.0)")
        assertEquals(0.0, e.currentHp, 1e-9)
    }

    @Test
    fun `overkill drives HP negative with no floor and reports died`() {
        val e = FakeEnemy(currentHp = 2.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 5.0, isAlive = true)
        assertEquals(5.0, out.dealt, 1e-9)
        assertTrue(out.died)
        assertEquals(-3.0, e.currentHp, 1e-9, "enemy HP has NO floor (diverges from the ziggurat resolver)")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*EnemyDamageResolverTest*" > /tmp/t3.log 2>&1; tail -n 15 /tmp/t3.log`
Expected: compile failure — `EnemyDamageResolver` does not exist.

- [ ] **Step 3: Write the resolver**

Create `EnemyDamageResolver.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.DamageableEnemy

/**
 * Pure-domain resolution of a hit against an enemy (#306, ADR-0012 Phase 5 Slice 2). Lifted verbatim from
 * `presentation/battle/entities/EnemyEntity.takeDamage`: corpse guard (#146) → armor absorb (#17) → HP
 * subtraction (NO floor — enemy HP may go negative, unlike the ziggurat's `coerceAtLeast(0.0)`) → death
 * detection (`currentHp <= 0.0`). Mutates the [DamageableEnemy] target's HP/armor; returns the [Outcome]
 * the presentation adapter needs: [Outcome.dealt] (damage actually dealt — 0.0 when absorbed/guarded — so
 * callers gate lifesteal/knockback on a positive value) and [Outcome.died] (the adapter flips `isAlive`
 * and fires `onDeath`). Stateless + pure — a single shared instance is safe. No Android imports; holds no
 * monitor — the caller invokes it inside the engine's held `entitiesLock`.
 */
class EnemyDamageResolver {
    /**
     * @property dealt damage actually applied to HP (0.0 when the enemy was already dead or the hit was
     *   fully armor-absorbed).
     * @property died the hit was lethal (HP crossed to <= 0.0 this call).
     */
    data class Outcome(
        val dealt: Double,
        val died: Boolean,
    )

    fun resolve(
        target: DamageableEnemy,
        amount: Double,
        isAlive: Boolean,
    ): Outcome {
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*EnemyDamageResolverTest*" > /tmp/t3.log 2>&1; tail -n 15 /tmp/t3.log`
Expected: `BUILD SUCCESSFUL`, all 5 cases pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolver.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/EnemyDamageResolverTest.kt
git commit -m "feat(#306): EnemyDamageResolver — pure enemy damage/death arithmetic"
```

---

## Task 4: `EnemyEntity` delegates HP/armor + `takeDamage` becomes an adapter

This is the behaviour-preserving swap. `EnemyEntity` keeps public `currentHp`/`maxHp`/`armorHits` (external
readers `BattleAnnouncer`/`BattleRenderer`/`CombatResolver` SCATTER/`WaveSpawner` are unchanged), but they
now delegate to the `EnemyState`.

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/EnemyEntity.kt`

- [ ] **Step 1: Confirm the existing oracle covers the re-wire, then run it (baseline green)**

The corpse-guard / armor-absorb / death double-credit behaviour is already exercised by
`CombatResolverTest`, `GameEngineTest`, and `CollisionSystemScratchTest`. Run them to capture a green
baseline BEFORE the swap:

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*CombatResolverTest*" --tests "*GameEngineTest*" --tests "*CollisionSystemScratchTest*" > /tmp/t4base.log 2>&1; tail -n 15 /tmp/t4base.log`
Expected: `BUILD SUCCESSFUL` (baseline oracle green).

- [ ] **Step 2: Move HP/armor into the state + rewrite `takeDamage` as the adapter**

In `EnemyEntity.kt`:

(a) Remove the `var currentHp`, `val maxHp` from the primary constructor's damage fields and the standalone
`var armorHits` property, replacing them with state construction + delegating properties. The constructor
still ACCEPTS `currentHp`/`maxHp`/`armorHits` (callers unchanged) but forwards them into `EnemyState`.

Change the constructor params `currentHp`/`maxHp` and the `armorHits` line. The current header is:

```kotlin
class EnemyEntity(
    val enemyType: EnemyType,
    var currentHp: Double,
    val maxHp: Double,
    val speed: Float,
    val damage: Double,
    private val targetX: Float,
    private val targetY: Float,
    private val onDeath: (EnemyEntity) -> Unit,
    // … onMeleeHit / onFireProjectile / attackInterval …
    armorHits: Int = 0,
    enemyTint: Int = 0,
) : Entity() {
    var armorHits: Int = armorHits
        private set
    private val state = EnemyState(targetX, targetY, speed, enemyType == EnemyType.RANGED, attackInterval)
```

Rewrite it to (rename the ctor params to `initialHp`/`initialArmorHits`, keep `maxHp` as a param name that
feeds the state, and add delegating properties):

```kotlin
class EnemyEntity(
    val enemyType: EnemyType,
    initialHp: Double,
    maxHp: Double,
    val speed: Float,
    val damage: Double,
    private val targetX: Float,
    private val targetY: Float,
    private val onDeath: (EnemyEntity) -> Unit,
    // … onMeleeHit / onFireProjectile / attackInterval (UNCHANGED) …
    armorHits: Int = 0,
    enemyTint: Int = 0,
) : Entity() {
    private val state =
        EnemyState(
            targetX = targetX,
            targetY = targetY,
            speed = speed,
            isRanged = enemyType == EnemyType.RANGED,
            attackInterval = attackInterval,
            initialHp = initialHp,
            maxHp = maxHp,
            initialArmorHits = armorHits,
        )

    /** HP/armor now live in the pure-domain [EnemyState] (#306 Slice 2); these delegate so external
     *  readers (BattleAnnouncer, BattleRenderer, SCATTER split, render()) see the unchanged surface. */
    var currentHp: Double
        get() = state.currentHp
        set(value) { state.currentHp = value }
    val maxHp: Double get() = state.maxHp
    val armorHits: Int get() = state.armorHits
```

> **IMPORTANT — parameter rename fan-out:** the primary-ctor param `currentHp` is renamed to `initialHp`.
> Every `EnemyEntity(...)` call site that uses the NAMED arg `currentHp =` must switch to `initialHp =`.
> There are **6 such sites** (all verified to use the named arg — confirm with
> `sg -l kotlin -p 'EnemyEntity($$$)' app/src`):
> - `presentation/battle/engine/WaveSpawner.kt:107` (main)
> - `presentation/battle/engine/CombatResolver.kt` (main — SCATTER block; will be rewritten in Task 6, but
>   the module must compile after this task, so rename it here too)
> - `presentation/battle/engine/CombatResolverTest.kt`
> - `presentation/battle/engine/GameEngineTest.kt`
> - `presentation/battle/engine/CollisionSystemScratchTest.kt`
> - `presentation/battle/entities/OrbEntityTest.kt`
>
> Positional callers are unaffected. `maxHp` keeps its name. Fix all 6 in this step so the module compiles.

(b) Rewrite `takeDamage` to delegate to the resolver (replace the whole current method body):

```kotlin
    /**
     * Applies [amount] HP damage and returns the damage actually dealt — `0.0` when the enemy is already
     * dead (#146 corpse guard) or the hit is fully absorbed by an armor charge (#17). Callers gate
     * damage-proportional side-effects (lifesteal, knockback) on a positive return. The corpse-guard /
     * armor-absorb / HP-subtract / death-detect arithmetic is hoisted to the pure-domain
     * [EnemyDamageResolver] (#306 Slice 2); this adapter flips `isAlive` and fires the presentation
     * `onDeath` cascade (cash/steps/boss/SCATTER — reward data already lives in Simulation).
     */
    fun takeDamage(amount: Double): Double {
        val outcome = ENEMY_DAMAGE_RESOLVER.resolve(state, amount, isAlive)
        if (outcome.died) {
            isAlive = false
            onDeath(this)
        }
        return outcome.dealt
    }
```

(c) Add the shared resolver instance to the existing `companion object` (alongside `ARMOR_PAINT` etc.):

```kotlin
        private val ENEMY_DAMAGE_RESOLVER = EnemyDamageResolver()
```

(d) Add the import near the other domain imports at the top of the file:

```kotlin
import com.whitefang.stepsofbabylon.domain.battle.engine.EnemyDamageResolver
```

- [ ] **Step 3: Run the oracle suite to verify behaviour is preserved**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*CombatResolverTest*" --tests "*GameEngineTest*" --tests "*CollisionSystemScratchTest*" --tests "*EnemyStateTest*" > /tmp/t4.log 2>&1; tail -n 20 /tmp/t4.log`
Expected: `BUILD SUCCESSFUL`, all pass (same green set as Step 1 — the corpse/armor/double-credit guards still hold through the adapter).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/EnemyEntity.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/WaveSpawner.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystemScratchTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/entities/OrbEntityTest.kt
git commit -m "feat(#306): EnemyEntity delegates HP/armor + takeDamage adapter over EnemyDamageResolver"
```

> `CombatResolver.kt` is staged here ONLY for the `currentHp =`→`initialHp =` rename in its SCATTER block
> (needed so the module compiles). Its SCATTER *logic* rewrite to `ScatterSplit` is Task 6 — do not do that
> yet. If you prefer to keep Task 4 free of `CombatResolver.kt`, instead do the whole SCATTER rewrite
> (Task 6) before Task 4's build step; the plan orders Task 4 first because it's the behaviour-critical swap.

---

## Task 5: `ScatterSplit` (pure SCATTER child descriptors)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplit.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplitTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ScatterSplitTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Pure-JVM tests for [ScatterSplit] (#306 Slice 2). Pins the SCATTER-on-death child math lifted verbatim
 * from `CombatResolver.handleEnemyDeath`: count 2..3 (`(2..3).random()` == `nextInt(2, 4)`), each child
 * half the parent's HP/damage, and the fanned `(i - count / 2f) * 15f` X offset (integer `count / 2f`).
 */
class ScatterSplitTest {
    /** RNG whose nextInt(from, until) always returns [fixed] (clamped into range by the caller's bounds). */
    private fun fixedIntRandom(fixed: Int) =
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(from: Int, until: Int): Int = fixed
        }

    @Test
    fun `rolls two children at the low end of the range`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        assertEquals(2, children.size)
    }

    @Test
    fun `rolls three children at the high end of the range`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(3))
        assertEquals(3, children.size)
    }

    @Test
    fun `each child gets half the parent HP and damage`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        children.forEach { c ->
            assertEquals(10.0, c.hp, 1e-9)
            assertEquals(10.0, c.maxHp, 1e-9)
            assertEquals(4.0, c.damage, 1e-9)
        }
    }

    @Test
    fun `offsets fan out symmetrically for two children`() {
        // count=2: i=0 → (0 - 1) * 15 = -15 ; i=1 → (1 - 1) * 15 = 0  (integer 2/2f = 1f)
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        assertEquals(-15f, children[0].offsetX, 1e-4f)
        assertEquals(0f, children[1].offsetX, 1e-4f)
    }

    @Test
    fun `offsets fan out for three children`() {
        // count=3: 3/2f = 1f ; i=0 → -15 ; i=1 → 0 ; i=2 → +15
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(3))
        assertEquals(-15f, children[0].offsetX, 1e-4f)
        assertEquals(0f, children[1].offsetX, 1e-4f)
        assertEquals(15f, children[2].offsetX, 1e-4f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*ScatterSplitTest*" > /tmp/t5.log 2>&1; tail -n 15 /tmp/t5.log`
Expected: compile failure — `ScatterSplit` does not exist.

- [ ] **Step 3: Write the helper**

Create `ScatterSplit.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.battle.engine

import kotlin.random.Random

/**
 * Pure descriptors for the SCATTER-on-death child split (#306 Slice 2). Lifted verbatim from
 * `CombatResolver.handleEnemyDeath`: the count is rolled 2..3 (`random.nextInt(2, 4)` == the pre-hoist
 * `(2..3).random()`), each child gets half the parent's HP/damage, and a fanned-out X offset
 * `(i - count / 2f) * 15f` (integer `count / 2f`, matching the original). The presentation
 * `CombatResolver` maps these onto `EnemyEntity` children — child speed, ziggurat target, and the
 * onDeath/onMeleeHit lambdas stay presentation. No Android imports; holds no monitor.
 */
object ScatterSplit {
    private const val CHILD_HP_FRACTION = 0.5
    private const val CHILD_DAMAGE_FRACTION = 0.5
    private const val OFFSET_SPACING = 15f
    private const val COUNT_MIN = 2
    private const val COUNT_MAX_EXCLUSIVE = 4 // 2..3 inclusive

    /**
     * @property offsetX added to the parent's X for this child's spawn position.
     */
    data class Child(
        val hp: Double,
        val maxHp: Double,
        val damage: Double,
        val offsetX: Float,
    )

    fun children(
        parentMaxHp: Double,
        parentDamage: Double,
        random: Random,
    ): List<Child> {
        val count = random.nextInt(COUNT_MIN, COUNT_MAX_EXCLUSIVE)
        return (0 until count).map { i ->
            Child(
                hp = parentMaxHp * CHILD_HP_FRACTION,
                maxHp = parentMaxHp * CHILD_HP_FRACTION,
                damage = parentDamage * CHILD_DAMAGE_FRACTION,
                offsetX = (i - count / 2f) * OFFSET_SPACING,
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*ScatterSplitTest*" > /tmp/t5.log 2>&1; tail -n 15 /tmp/t5.log`
Expected: `BUILD SUCCESSFUL`, all 5 cases pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplit.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/ScatterSplitTest.kt
git commit -m "feat(#306): ScatterSplit — pure SCATTER child descriptors"
```

---

## Task 6: `CombatResolver` SCATTER branch uses `ScatterSplit`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt`

- [ ] **Step 1: Write the failing test (SCATTER integration oracle)**

The existing `FakeCombatHost` in `CombatResolverTest.kt` already captures `addPending` into `host.pending`.
Add a SCATTER test. Note `CombatResolver` uses its injected `random` (default `Random.Default`); construct
the resolver with a seeded RNG so the count is deterministic.

Add to `CombatResolverTest.kt` (inside the class):

```kotlin
    @Test
    fun `handleEnemyDeath on a SCATTER enemy spawns half-HP BASIC children`() {
        val zig = makeZiggurat()
        val host = FakeCombatHost(zig)
        // Seed the resolver's RNG so nextInt(2,4) is deterministic (count within 2..3 either way).
        val resolver = CombatResolver(host, random = kotlin.random.Random(1))
        val scatter =
            EnemyEntity(
                enemyType = EnemyType.SCATTER,
                initialHp = 40.0,
                maxHp = 40.0,
                speed = 0f,
                damage = 12.0,
                targetX = zig.originX,
                targetY = zig.originY,
                onDeath = { },
            ).apply { x = zig.originX; y = zig.originY + 200f }

        resolver.handleEnemyDeath(scatter)

        val children = host.pending.filterIsInstance<EnemyEntity>()
        assertTrue(children.size in 2..3, "SCATTER must spawn 2..3 children (was ${children.size})")
        children.forEach { child ->
            assertEquals(EnemyType.BASIC, child.enemyType, "SCATTER children are BASIC")
            assertEquals(20.0, child.maxHp, 1e-9, "each child gets half the parent maxHp (40 → 20)")
            assertEquals(20.0, child.currentHp, 1e-9)
        }
    }
```

Add the import if not already present:

```kotlin
import org.junit.jupiter.api.Assertions.assertTrue
```

(`assertTrue`, `assertEquals`, `EnemyType`, `EnemyEntity` are already imported in this file.)

- [ ] **Step 2: Run test to verify it fails**

The named-arg rename from Task 4 (`currentHp =` → `initialHp =`) means the existing SCATTER construction in
`CombatResolver.kt` still uses the old code path (verbatim `(2..3).random()` + inline children). This new
test should PASS against the pre-refactor SCATTER code IF Task 4 already renamed the ctor args in
`CombatResolver.kt`'s SCATTER block. To make Step 2 a genuine red→green, first confirm the test compiles and
runs against current behaviour:

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*CombatResolverTest*" > /tmp/t6.log 2>&1; tail -n 15 /tmp/t6.log`
Expected: PASS (this test characterizes existing behaviour — it is the oracle that Step 3's refactor must
preserve). If it fails to COMPILE due to the `initialHp` rename not yet applied in the SCATTER block, apply
the rename first (it belongs to Task 4's fan-out).

- [ ] **Step 3: Rewrite the SCATTER branch to use `ScatterSplit`**

In `CombatResolver.kt` `handleEnemyDeath`, replace the current SCATTER block:

```kotlin
        if (enemy.enemyType == EnemyType.SCATTER) {
            val zig = host.ziggurat ?: return
            val childCount = (2..3).random()
            repeat(childCount) { i ->
                val child =
                    EnemyEntity(
                        enemyType = EnemyType.BASIC,
                        currentHp = enemy.maxHp * 0.5,
                        maxHp = enemy.maxHp * 0.5,
                        speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * host.conditions.enemySpeedMultiplier,
                        damage = enemy.damage * 0.5,
                        targetX = zig.originX,
                        targetY = zig.originY,
                        onDeath = ::handleEnemyDeath,
                        onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
                    ).apply {
                        x = enemy.x + (i - childCount / 2f) * 15f
                        y = enemy.y
                        initDistance()
                    }
                host.addPending(child)
            }
        }
```

with:

```kotlin
        if (enemy.enemyType == EnemyType.SCATTER) {
            val zig = host.ziggurat ?: return
            // #306 Slice 2: the count/HP/damage/offset descriptor math is hoisted to the pure-domain
            // ScatterSplit (seeded via this resolver's `random`, so it stays deterministic in tests). The
            // EnemyEntity construction stays here — it needs the onDeath/onMeleeHit lambdas, the ziggurat
            // target, and the condition-scaled child speed, none of which are pure-domain.
            ScatterSplit.children(enemy.maxHp, enemy.damage, random).forEach { c ->
                val child =
                    EnemyEntity(
                        enemyType = EnemyType.BASIC,
                        initialHp = c.hp,
                        maxHp = c.maxHp,
                        speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * host.conditions.enemySpeedMultiplier,
                        damage = c.damage,
                        targetX = zig.originX,
                        targetY = zig.originY,
                        onDeath = ::handleEnemyDeath,
                        onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
                    ).apply {
                        x = enemy.x + c.offsetX
                        y = enemy.y
                        initDistance()
                    }
                host.addPending(child)
            }
        }
```

Add the import near the other domain-engine imports:

```kotlin
import com.whitefang.stepsofbabylon.domain.battle.engine.ScatterSplit
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*CombatResolverTest*" > /tmp/t6.log 2>&1; tail -n 15 /tmp/t6.log`
Expected: `BUILD SUCCESSFUL`, the SCATTER integration test + all pre-existing `CombatResolverTest` cases pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolverTest.kt
git commit -m "feat(#306): CombatResolver SCATTER split via pure ScatterSplit helper"
```

---

## Task 7: Full verification + docs sync

**Files:**
- Modify: `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md`
- Modify: `CLAUDE.md`
- Modify: `docs/steering/source-files.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md` (via `/checkpoint` at session end)

- [ ] **Step 1: Full unit suite + static analysis green**

Run:
```bash
./run-gradle.sh :app:testDebugUnitTest > /tmp/t7-tests.log 2>&1; tail -n 20 /tmp/t7-tests.log
./run-gradle.sh :app:detekt > /tmp/t7-detekt.log 2>&1; tail -n 10 /tmp/t7-detekt.log
./lint-kotlin.sh > /tmp/t7-ktlint.log 2>&1; tail -n 10 /tmp/t7-ktlint.log
./run-gradle.sh :app:koverVerifyDebug > /tmp/t7-kover.log 2>&1; tail -n 10 /tmp/t7-kover.log
```
Expected: all `BUILD SUCCESSFUL`. Record the new headline JVM test count (was 1339; expect +~12: 5 resolver + 5 scatter + 2 state + 1 SCATTER-integration, minus none). koverVerifyDebug must not regress (new pure domain code raises `domain.battle.*` coverage).

- [ ] **Step 2: Confirm no stray behaviour change — grep the resolver is the only takeDamage arithmetic**

Run: `sg -l kotlin -p '$X.takeDamage($$$)' app/src/main`
Expected: the same 7 call sites as before the change (CombatResolver ×3, UWController ×4) — the adapter did
NOT change any caller, only `EnemyEntity`'s internals.

- [ ] **Step 3: Update ADR-0012 (Slice 2 entry + wording correction)**

In `docs/agent/DECISIONS/ADR-0012-simulation-extraction.md`, under the Phase 5 section, add a Slice 2 entry
after the Slice 1 paragraph. Include:
- New `DamageableEnemy` port (extends `Damageable` with `armorHits`); `EnemyState` implements it and now
  owns `currentHp`/`maxHp`/`armorHits`.
- New pure `EnemyDamageResolver` (corpse guard / armor absorb / no-floor HP subtract / death detect);
  `EnemyEntity.takeDamage` is a thin adapter (flips `isAlive`, fires `onDeath`).
- New pure `ScatterSplit`; `CombatResolver` maps descriptors → `EnemyEntity` children.
- **Correction:** the Slice-1 note said the enemy slice "will declare `EnemyState : EntityProtocol,
  Damageable`". Corrected: `EnemyState` implements `DamageableEnemy` only — `EntityProtocol.update(dt):
  Unit` clashes with `EnemyState.update(dt): Boolean`, and the `EntityProtocol` role stays on the
  presentation `Entity`.
- Update the "Explicitly still NOT done" list: enemy `takeDamage`/`onDeath`/SCATTER now DONE; remaining =
  `UWController.when(type)` effect bodies + `onProjectileHitEnemy`/`onOrbHit` knockback+lifesteal.

- [ ] **Step 4: Update CLAUDE.md (Battle Renderer note + test count)**

In `CLAUDE.md`:
- Battle Renderer section, the paragraph about domain hoisting: note enemy damage/HP resolution is now
  domain-hoisted (Slice 2) alongside the ziggurat (Slice 1); remaining entity-coupled work is the UW effect
  bodies + projectile/orb knockback+lifesteal.
- Testing section headline count: update `1339 JVM tests` to the new count from Step 1.

- [ ] **Step 5: Update source-files.md + CHANGELOG.md**

- `docs/steering/source-files.md`: add entries for `domain/battle/entity/DamageableEnemy.kt`,
  `domain/battle/engine/EnemyDamageResolver.kt`, `domain/battle/engine/ScatterSplit.kt`; update the
  responsibility shape for `EnemyState` (now owns HP/armor) and `EnemyEntity` (thin damage adapter).
- `CHANGELOG.md`: add a section under `[Unreleased]` for #306 Slice 2 (files added, behaviour-preserving,
  test-count delta).

- [ ] **Step 6: Commit docs**

```bash
git add docs/agent/DECISIONS/ADR-0012-simulation-extraction.md CLAUDE.md \
        docs/steering/source-files.md CHANGELOG.md
git commit -m "docs(#306): Slice 2 — ADR-0012 Phase 5 entry + source-files/CLAUDE/CHANGELOG sync"
```

- [ ] **Step 7: STATE.md + RUN_LOG.md via /checkpoint**

Run the `/checkpoint` skill at session end (it performs the doc-drift sweep, updates `docs/agent/STATE.md`,
appends `docs/agent/RUN_LOG.md`, and regenerates `BACKLOG.md`). #306 stays OPEN for the remaining slices.

---

## Self-Review (completed at authoring)

- **Spec coverage:** §1 port → Task 1; §2 EnemyState HP/armor + ctor-defaults note → Task 2; §4 resolver →
  Task 3; §3 EnemyEntity adapter + delegating readers → Task 4; §5 ScatterSplit → Task 5; SCATTER re-wire
  → Task 6; testing strategy → Tasks 2–6 tests + Task 7 full-suite; thread-safety (no new monitor, runs
  under `entitiesLock`) → preserved by design (no lock code touched); docs updates → Task 7. All covered.
- **Placeholder scan:** no TBD/TODO; every code step has complete code.
- **Type consistency:** `DamageableEnemy` (Task 1) used consistently in Tasks 2–3; `EnemyDamageResolver`
  ctor is arg-less (Tasks 3/4); `Outcome(dealt, died)` fields consistent (Tasks 3/4); `ScatterSplit.children`
  signature + `Child(hp, maxHp, damage, offsetX)` consistent (Tasks 5/6); ctor param rename
  `currentHp`→`initialHp` flagged in Task 4 and applied consistently in Tasks 4/6 tests.
- **Known trap called out:** the `EnemyEntity` primary-ctor `currentHp`→`initialHp` rename fans out to every
  named-arg call site (Task 4 note) — this is the one cross-cutting edit; positional callers unaffected,
  `maxHp` unchanged.
```
