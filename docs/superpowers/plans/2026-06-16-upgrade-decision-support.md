# Workshop Upgrade Decision Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add decision support to the Workshop screen — a Now→Next stat preview on every upgrade, a "value per step" combat-power indicator on combat upgrades, and a single "★ BEST BUY" badge — so a player can tell whether an upgrade is worth buying (#29, Gate F).

**Architecture:** Approach 1 from the spec — two new pure domain use cases (`CombatPower`, `EvaluateUpgradeValue`) + a tiny shared `WorkshopLevels` helper + a reuse-and-extend of `DescribeUpgradeEffect` (new workshop-dimension path). The Workshop ViewModel composes them into new UiState fields; the `UpgradeCard` Composable is a thin renderer. All load-bearing logic is pure and JVM-unit-tested; the new UI rendering is verified on-device (PR-4736 — no Compose-rule tests in this repo).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), MVVM + Clean Architecture, JUnit Jupiter + kotlinx-coroutines-test for JVM unit tests. Build via `./run-gradle.sh` (never `./gradlew` directly — non-TTY buffering).

**Spec:** `docs/superpowers/specs/2026-06-16-upgrade-decision-support-design.md` (passed the Adversarial Review Gate).

**Scope guardrails (from the spec):**
- Workshop screen only. No Cards, no ROI-sort/reorder, no quick-buy multiplier, no readability re-theme.
- **No domain/model change.** `ResolvedStats` is read, never modified. No balance-constant edits. No schema/engine/economy/concurrency change.
- The combat-power index is a **proxy** — UI says "combat power", never "DPS". It returns a bare `Double` and must never be passed to an engine stat sink.
- Combat-power value bar + Best-Buy badge apply **only** to upgrades whose Δpower > 0 (Damage, Attack Speed, Critical Chance, and Critical Factor *iff* crit chance > 0). Everything else (Range, Damage-per-meter, Rapid Fire, all Defense, all Utility) gets the Now→Next preview only.

---

## File structure

**Create (all pure domain, swept by `architecture/DomainPurityTest`):**
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPower.kt` — the index.
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevels.kt` — shared workshop-dimension level helpers (cap test + increment), used by both `DescribeUpgradeEffect`'s new path and `EvaluateUpgradeValue` so they can't diverge.
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValue.kt` — `UpgradeValue` result type + the ranking/best-buy use case.

**Create (presentation, Compose-free → JVM-testable):**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabel.kt` — pure `formatPowerPerKStepsLabel(Double): String`.

**Create (tests):**
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPowerTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevelsTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValueTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabelTest.kt`

**Modify:**
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffect.kt` — add `workshopPreview(...)`.
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffectTest.kt` — add workshop-dimension tests.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopUiState.kt` — new `UpgradeDisplayInfo` fields.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModel.kt` — wire the use cases.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModelTest.kt` — assert new fields.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt` — render Now→Next + value bar + badge.

**Docs (Task 9):** `docs/steering/source-files.md`, `CHANGELOG.md`, `CLAUDE.md` (headline count), `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.

**Dependency order:** Task 1 (`CombatPower`) and Task 2 (`WorkshopLevels`) have no deps. Task 3 (`DescribeUpgradeEffect.workshopPreview`) depends on Task 2. Task 4 (`EvaluateUpgradeValue`) depends on Tasks 1+2. Task 5 (label) standalone. Task 6 (UiState) standalone. Task 7 (VM) depends on Tasks 3+4+6. Task 8 (UpgradeCard) depends on Tasks 5+6. Task 9 (docs) last.

---

### Task 1: `CombatPower` use case (the index)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPower.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPowerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPowerTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Combat-power index (#29, spec §2): a steady-state single-target DPS *proxy*
 * = damage × attackSpeed × (1 + critChance × (critMultiplier − 1)).
 * Deliberately ignores multishot/bounce/orbs/range/sustain.
 */
class CombatPowerTest {

    private val sut = CombatPower()

    @Test
    fun `base stats with no crit equal damage times attackSpeed`() {
        // ResolvedStats defaults: damage 10, attackSpeed 1, critChance 0, critMultiplier 2.
        val power = sut(ResolvedStats())
        assertEquals(10.0, power, 1e-9)
    }

    @Test
    fun `damage scales power linearly`() {
        val power = sut(ResolvedStats(damage = 20.0, attackSpeed = 1.0, critChance = 0.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `attack speed scales power linearly`() {
        val power = sut(ResolvedStats(damage = 10.0, attackSpeed = 2.0, critChance = 0.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `crit chance and multiplier raise power via expected-crit factor`() {
        // 10 × 1 × (1 + 0.5 × (3 − 1)) = 10 × 2 = 20.
        val power = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.5, critMultiplier = 3.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `crit factor with zero crit chance does not change power`() {
        // The §3.3 synergy: (1 + 0 × (critMultiplier − 1)) = 1 for any critMultiplier.
        val low = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0, critMultiplier = 2.0))
        val high = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0, critMultiplier = 5.0))
        assertEquals(low, high, 1e-9)
    }

    @Test
    fun `multishot bounce orbs and range do NOT affect the index`() {
        val plain = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0))
        val decorated = sut(
            ResolvedStats(
                damage = 10.0, attackSpeed = 1.0, critChance = 0.0,
                multishotTargets = 5, bounceCount = 4, orbCount = 6, range = 900f,
            ),
        )
        assertEquals(plain, decorated, 1e-9)
    }

    @Test
    fun `more of any contributing stat strictly increases power`() {
        val baseline = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.1, critMultiplier = 2.0))
        assertTrue(sut(ResolvedStats(damage = 11.0, attackSpeed = 1.0, critChance = 0.1, critMultiplier = 2.0)) > baseline)
        assertTrue(sut(ResolvedStats(damage = 10.0, attackSpeed = 1.1, critChance = 0.1, critMultiplier = 2.0)) > baseline)
        assertTrue(sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.2, critMultiplier = 2.0)) > baseline)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails to compile (no `CombatPower`)**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.CombatPowerTest" > build.log 2>&1; tail -n 20 build.log`
Expected: FAIL — unresolved reference `CombatPower`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPower.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats

/**
 * Combat-power index — a steady-state single-target DPS *proxy* used ONLY to rank Workshop upgrades
 * and drive the value indicator (#29, spec §2). It is deliberately simple:
 *
 *     combatPower = damage × attackSpeed × (1 + critChance × (critMultiplier − 1))
 *
 * The third factor is the standard expected-crit multiplier. It intentionally ignores
 * `multishotTargets`, `bounceCount`, `orbCount`, `range`, `damagePerMeterBonus`, knockback, and all
 * sustain — these are either runtime-variable, periodic, or not single-target throughput, and none of
 * their upgrades are ranked on the Workshop screen, so omitting them never misranks a candidate
 * (spec §2 BUG-1/BUG-2).
 *
 * **It is a comparison instrument, not a balance input.** It returns a bare `Double`; the engine's
 * stat sinks (`GameEngine.setStats`/`updateZigguratStats`/`applyStats`, `ZigguratState.updateStats`)
 * all take `ResolvedStats`, so this proxy is type-incompatible with every real-stat channel and can
 * never feed the simulation (spec INV-3). The "combat power" UI naming is the player-facing guard.
 *
 * Pure Kotlin — no Android imports — guarded by `architecture/DomainPurityTest`.
 */
class CombatPower {
    operator fun invoke(stats: ResolvedStats): Double =
        stats.damage *
            stats.attackSpeed *
            (1.0 + stats.critChance * (stats.critMultiplier - 1.0))
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.CombatPowerTest" > build.log 2>&1; tail -n 20 build.log`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPower.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CombatPowerTest.kt
git commit -m "feat(#29): CombatPower index — steady-state single-target DPS proxy"
```

---

### Task 2: `WorkshopLevels` shared helper

Tiny pure helper for the workshop-dimension cap test + level increment. Both `DescribeUpgradeEffect`'s new workshop path (Task 3) and `EvaluateUpgradeValue` (Task 4) use it, so the Now→Next string and the value-bar delta can't diverge on increment/cap semantics (spec §5.1, INV-6).

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevels.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevelsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevelsTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkshopLevelsTest {

    @Test
    fun `levelOf returns the map value or zero when absent`() {
        assertEquals(7, WorkshopLevels.levelOf(mapOf(UpgradeType.DAMAGE to 7), UpgradeType.DAMAGE))
        assertEquals(0, WorkshopLevels.levelOf(emptyMap(), UpgradeType.DAMAGE))
    }

    @Test
    fun `isAtMax is false for an uncapped upgrade regardless of level`() {
        // DAMAGE has maxLevel = null.
        assertFalse(WorkshopLevels.isAtMax(mapOf(UpgradeType.DAMAGE to 9999), UpgradeType.DAMAGE))
    }

    @Test
    fun `isAtMax is true only at or above the workshop cap`() {
        // CRITICAL_CHANCE maxLevel = 160.
        val type = UpgradeType.CRITICAL_CHANCE
        assertFalse(WorkshopLevels.isAtMax(mapOf(type to 159), type))
        assertTrue(WorkshopLevels.isAtMax(mapOf(type to 160), type))
        assertTrue(WorkshopLevels.isAtMax(mapOf(type to 161), type))
    }

    @Test
    fun `withIncremented bumps only the target type by one`() {
        val before = mapOf(UpgradeType.DAMAGE to 3, UpgradeType.ATTACK_SPEED to 5)
        val after = WorkshopLevels.withIncremented(before, UpgradeType.DAMAGE)
        assertEquals(4, after[UpgradeType.DAMAGE])
        assertEquals(5, after[UpgradeType.ATTACK_SPEED])
    }

    @Test
    fun `withIncremented treats an absent type as level zero`() {
        val after = WorkshopLevels.withIncremented(emptyMap(), UpgradeType.DAMAGE)
        assertEquals(1, after[UpgradeType.DAMAGE])
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.WorkshopLevelsTest" > build.log 2>&1; tail -n 20 build.log`
Expected: FAIL — unresolved reference `WorkshopLevels`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevels.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType

/**
 * Shared workshop-dimension level helpers (#29, spec §5.1 / INV-6).
 *
 * The Workshop screen is about **permanent** (workshop) levels. Both the Now→Next preview
 * ([DescribeUpgradeEffect.workshopPreview]) and the value-per-step delta ([EvaluateUpgradeValue])
 * must increment the *workshop* dimension — not the in-round dimension — because for multiplicative
 * stats `ResolveStats` gives different "Next" numbers depending on which dimension is bumped. Routing
 * both through these helpers guarantees they agree on (a) the next-level map and (b) the cap test.
 *
 * The cap test gates on the **workshop-only** level, matching `PurchaseUpgrade` / `WorkshopViewModel`.
 * Pure Kotlin — guarded by `architecture/DomainPurityTest`.
 */
object WorkshopLevels {

    /** The workshop level of [type] (0 when absent). */
    fun levelOf(levels: Map<UpgradeType, Int>, type: UpgradeType): Int = levels[type] ?: 0

    /** True when [type] is at (or beyond) its workshop-level cap; always false for uncapped upgrades. */
    fun isAtMax(levels: Map<UpgradeType, Int>, type: UpgradeType): Boolean {
        val maxLevel = type.config.maxLevel ?: return false
        return levelOf(levels, type) >= maxLevel
    }

    /**
     * [levels] with [type]'s workshop level incremented by one. No clamp here — `ResolveStats`
     * applies the per-stat caps, so a level past the cap simply yields the same resolved value
     * (Δpower = 0), which the caller then excludes.
     */
    fun withIncremented(levels: Map<UpgradeType, Int>, type: UpgradeType): Map<UpgradeType, Int> =
        levels + (type to levelOf(levels, type) + 1)
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.WorkshopLevelsTest" > build.log 2>&1; tail -n 20 build.log`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevels.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/WorkshopLevelsTest.kt
git commit -m "feat(#29): WorkshopLevels — shared workshop-dimension cap + increment helpers"
```

---

### Task 3: `DescribeUpgradeEffect.workshopPreview` (workshop-dimension Now→Next)

Add a **distinct** preview path that increments the workshop dimension (the existing `invoke` stays untouched — it powers the in-round menu and all its existing tests must keep passing).

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffect.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffectTest.kt` (append)

- [ ] **Step 1: Write the failing tests**

Append these tests to `DescribeUpgradeEffectTest.kt` (before the final `}` of the class):

```kotlin
    // ---- #29: workshop-dimension preview (spec §5.1) ----

    @Test
    fun `workshopPreview increments the WORKSHOP dimension, not in-round`() {
        // At ws=50 the two dimensions diverge visibly (the §5.1 trap):
        //   in-round path (invoke): next bumps ir 0->1 -> 10*(1+50*.02)*(1+1*.02) = 20.4 -> "20.4 dmg"
        //   workshop path:          next bumps ws 50->51 -> 10*(1+51*.02)         = 20.2 -> "20.2 dmg"
        val ws = mapOf(UpgradeType.DAMAGE to 50)
        val inRoundPath = describe(ws, emptyMap(), emptyMap(), UpgradeType.DAMAGE)
        val workshopPath = describe.workshopPreview(ws, type = UpgradeType.DAMAGE)
        assertEquals("20.0 dmg", workshopPath.current)
        assertEquals("20.2 dmg", workshopPath.next)
        assertEquals("20.4 dmg", inRoundPath.next)
        assertNotEquals(
            inRoundPath.next, workshopPath.next,
            "Workshop preview must bump the permanent (workshop) level, not the in-round level (spec §5.1)",
        )
    }

    @Test
    fun `workshopPreview returns null next at the workshop cap`() {
        // CRITICAL_CHANCE maxLevel = 160; at the cap there is no next purchase.
        val atMax = describe.workshopPreview(mapOf(UpgradeType.CRITICAL_CHANCE to 160), type = UpgradeType.CRITICAL_CHANCE)
        assertEquals("80.0%", atMax.current) // 160 * 0.005 = 0.80 (capped)
        assertNull(atMax.next)
    }

    @Test
    fun `workshopPreview near the cap still previews the next workshop level`() {
        // ws=159 -> 79.5%; next ws=160 -> 80.0% (capped).
        val nearMax = describe.workshopPreview(mapOf(UpgradeType.CRITICAL_CHANCE to 159), type = UpgradeType.CRITICAL_CHANCE)
        assertEquals("79.5%", nearMax.current)
        assertEquals("80.0%", nearMax.next)
    }

    @Test
    fun `workshopPreview applies equipped card effects like the in-round path`() {
        // Mirrors the in-round path's RO-12 behaviour: WALKING_FORTRESS +50% maxHealth.
        // ws=5 HEALTH -> 1000*1.15 = 1150; with WF +50% -> 1725 -> "1725 HP".
        val cards = listOf(OwnedCard(1, CardType.WALKING_FORTRESS, 1, true))
        val r = describe.workshopPreview(
            workshopLevels = mapOf(UpgradeType.HEALTH to 5),
            type = UpgradeType.HEALTH,
            equippedCards = cards,
        )
        assertEquals("1725 HP", r.current)
    }
```

Add the imports `CardType` and `OwnedCard` are already imported at the top of the file (verify they are — they are used by the existing RO-12 tests).

- [ ] **Step 2: Run the tests, verify they fail**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.DescribeUpgradeEffectTest" > build.log 2>&1; tail -n 25 build.log`
Expected: FAIL — unresolved reference `workshopPreview`.

- [ ] **Step 3: Add the `workshopPreview` method**

In `DescribeUpgradeEffect.kt`, add this method inside the class, immediately after the existing `invoke(...)` operator. `WorkshopLevels` is in the same package (`domain.usecase`), so no import is needed; `UpgradeEffectReadout`, `ResearchType`, `OwnedCard`, and `UpgradeType` are all already imported/declared in this file:

```kotlin
    /**
     * Now → Next readout for the **Workshop** screen (#29, spec §5.1). Unlike [invoke] — which
     * increments the *in-round* dimension for the in-round upgrade menu — this previews incrementing
     * the *permanent* (workshop) level, because that is the purchase the Workshop card makes. For
     * multiplicative stats the two dimensions produce different "Next" numbers when the other is
     * non-zero, so the Workshop screen MUST use this path.
     *
     * Reuses the private [format] formatter (so number formatting / card-effect post-application /
     * `Locale.ROOT` discipline are identical to [invoke]) and the shared [WorkshopLevels] helper for
     * the cap test + level increment (so this readout and [EvaluateUpgradeValue]'s value delta can't
     * diverge — spec INV-6). In-round levels are intentionally empty here: the Workshop screen has no
     * in-round dimension in play.
     */
    fun workshopPreview(
        workshopLevels: Map<UpgradeType, Int>,
        labLevels: Map<ResearchType, Int> = emptyMap(),
        type: UpgradeType,
        equippedCards: List<OwnedCard> = emptyList(),
    ): UpgradeEffectReadout {
        val currentReadout = format(workshopLevels, emptyMap(), labLevels, equippedCards, type)
        val nextReadout = if (WorkshopLevels.isAtMax(workshopLevels, type)) {
            null
        } else {
            format(WorkshopLevels.withIncremented(workshopLevels, type), emptyMap(), labLevels, equippedCards, type)
        }
        return UpgradeEffectReadout(currentReadout, nextReadout)
    }
```

- [ ] **Step 4: Run the full `DescribeUpgradeEffectTest`, verify all pass (new + all existing)**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.DescribeUpgradeEffectTest" > build.log 2>&1; tail -n 25 build.log`
Expected: PASS — **BUILD SUCCESSFUL** with the test count = (whatever the file had before) + 4. The existing in-round `invoke` tests are unaffected (we only added a new method). Don't assert a hardcoded prior count; trust gradle's BUILD SUCCESSFUL + the +4 delta.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffect.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/DescribeUpgradeEffectTest.kt
git commit -m "feat(#29): DescribeUpgradeEffect.workshopPreview — workshop-dimension Now→Next (spec §5.1)"
```

---

### Task 4: `EvaluateUpgradeValue` use case (ranking + Best Buy)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValue.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValueTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValueTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ranks combat upgrades by Δcombat-power ÷ step-cost and flags the single Best Buy (#29, spec §3-§4).
 *
 * Baseline arithmetic (all-zero workshop, ResolvedStats defaults: damage 10, attackSpeed 1, crit 0):
 *   currentPower = 10.
 *   DAMAGE L0->L1:    damage 10.2 -> power 10.2, Δ 0.2,  cost 50  -> value 0.004
 *   ATTACK_SPEED 0->1: spd 1.015  -> power 10.15, Δ 0.15, cost 75  -> value 0.002
 *   CRITICAL_CHANCE:  crit 0.005  -> power 10.05, Δ 0.05, cost 100 -> value 0.0005
 *   CRITICAL_FACTOR:  crit 0, factor 2.1 -> power 10.0, Δ 0 -> EXCLUDED
 *   RANGE / DAMAGE_PER_METER / RAPID_FIRE: not in index -> Δ 0 -> EXCLUDED
 * So DAMAGE is the Best Buy.
 */
class EvaluateUpgradeValueTest {

    private val sut = EvaluateUpgradeValue()

    private val attackCandidates = listOf(
        UpgradeType.DAMAGE, UpgradeType.ATTACK_SPEED, UpgradeType.CRITICAL_CHANCE,
        UpgradeType.CRITICAL_FACTOR, UpgradeType.RANGE, UpgradeType.DAMAGE_PER_METER, UpgradeType.RAPID_FIRE,
    )

    @Test
    fun `only Δpower-positive combat upgrades are returned`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val types = result.map { it.type }.toSet()
        assertEquals(setOf(UpgradeType.DAMAGE, UpgradeType.ATTACK_SPEED, UpgradeType.CRITICAL_CHANCE), types)
    }

    @Test
    fun `critical factor is excluded when crit chance is zero`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_FACTOR))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `critical factor is included once crit chance is non-zero`() {
        // With some CRITICAL_CHANCE levels, a CRITICAL_FACTOR purchase now raises power -> included.
        val levels = mapOf(UpgradeType.CRITICAL_CHANCE to 20) // crit 0.10
        val result = sut(levels, stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_FACTOR))
        assertEquals(1, result.size)
        assertEquals(UpgradeType.CRITICAL_FACTOR, result.first().type)
        assertTrue(result.first().valuePerStep > 0.0)
    }

    @Test
    fun `damage is the best buy from an all-zero attack tab`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertEquals(UpgradeType.DAMAGE, best.type)
        assertTrue(best.bestBuyAffordable)
    }

    @Test
    fun `exactly one upgrade is flagged best buy`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        assertEquals(1, result.count { it.isBestBuy })
    }

    @Test
    fun `percent-per-k-steps is computed correctly and positive`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = listOf(UpgradeType.DAMAGE))
        // value 0.004 ÷ currentPower 10 × 1000 × 100 = 40.0
        // (sanity: a 50-step DAMAGE level = +2% power, so ~20 levels ≈ 1,000 steps ≈ +40% power)
        assertEquals(40.0, result.single().percentPerKSteps, 1e-6)
    }

    @Test
    fun `best buy bar fraction is full and lower-value bars are proportional`() {
        val result = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates)
        val damage = result.single { it.type == UpgradeType.DAMAGE }
        val attackSpeed = result.single { it.type == UpgradeType.ATTACK_SPEED }
        assertEquals(1.0f, damage.barFraction, 1e-6f)            // highest pct -> full bar
        assertTrue(attackSpeed.barFraction in 0.0f..1.0f)
        assertTrue(attackSpeed.barFraction < damage.barFraction) // lower value -> shorter bar
    }

    @Test
    fun `when nothing is affordable the best buy falls back to highest value, greyed`() {
        // Balance below DAMAGE's base cost (50) -> no candidate affordable.
        val result = sut(emptyMap(), stepBalance = 0, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertEquals(UpgradeType.DAMAGE, best.type)       // still the highest-value
        assertFalse(best.bestBuyAffordable)               // but flagged unaffordable (greyed)
    }

    @Test
    fun `best buy prefers the highest-value AFFORDABLE upgrade`() {
        // Make DAMAGE unaffordable by pricing it up via level, leaving ATTACK_SPEED/CRIT affordable.
        // DAMAGE L40 cost = ceil(50 * 1.12^40) ≈ 4653 (large); ATTACK_SPEED L0 = 75, CRIT L0 = 100.
        // COVERAGE LIMITATION (review test-correctness F1): with real configs the cheapest upgrade
        // (DAMAGE, base 50) is ALSO the highest-value, so we cannot construct a case where the single
        // UNAFFORDABLE candidate is simultaneously the highest-value — at L40 DAMAGE is both
        // unaffordable AND lowest-value (its Δpower/cost collapses). So this test proves the best buy
        // is affordable + not the priced-out DAMAGE, but does NOT independently prove affordable-pref
        // would override a higher-value-but-unaffordable option. The affordable-vs-greyed *fallback*
        // branch is what the next test pins; the "exactly one" + "damage is best from zero" tests pin
        // the ranking. (A synthetic-config test could prove the override but would not reflect real play.)
        val levels = mapOf(UpgradeType.DAMAGE to 40)
        val costDamage = CalculateUpgradeCost()(UpgradeType.DAMAGE, 40)
        val balance = costDamage - 1 // can't afford DAMAGE, can afford ATTACK_SPEED(75) and CRIT(100)
        assertTrue(balance >= 100, "sanity: balance must still afford the cheaper candidates")
        val result = sut(levels, stepBalance = balance, candidates = attackCandidates)
        val best = result.single { it.isBestBuy }
        assertTrue(best.bestBuyAffordable)
        assertTrue(best.type != UpgradeType.DAMAGE, "DAMAGE is unaffordable here, so it cannot be the affordable best buy")
    }

    @Test
    fun `maxed candidates are excluded`() {
        // CRITICAL_CHANCE at cap (160) -> Δpower 0 -> excluded.
        val result = sut(mapOf(UpgradeType.CRITICAL_CHANCE to 160), stepBalance = 100_000, candidates = listOf(UpgradeType.CRITICAL_CHANCE))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty candidate set yields empty result`() {
        assertTrue(sut(emptyMap(), stepBalance = 100_000, candidates = emptyList()).isEmpty())
    }

    @Test
    fun `ties on value break deterministically by enum declaration order`() {
        // Construct a tie: two synthetic candidates with identical value can't easily occur with real
        // configs, so assert the comparator behaviour indirectly — DAMAGE (ordinal 0) must win over any
        // equal-or-lower candidate. Re-running gives a stable winner (no flicker).
        val a = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates).single { it.isBestBuy }.type
        val b = sut(emptyMap(), stepBalance = 100_000, candidates = attackCandidates).single { it.isBestBuy }.type
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.EvaluateUpgradeValueTest" > build.log 2>&1; tail -n 25 build.log`
Expected: FAIL — unresolved reference `EvaluateUpgradeValue`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValue.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType

/**
 * One ranked combat upgrade's decision-support data for the Workshop screen (#29, spec §3-§4).
 *
 * @property valuePerStep ranking key — Δcombat-power ÷ step-cost for one workshop level.
 * @property percentPerKSteps display value — Δpower as a percentage of the player's *current* combat
 *   power, scaled per 1,000 steps. Always > 0 (only Δpower>0 upgrades are returned). Dividing by the
 *   shared current power doesn't change the ranking (spec §3.2), so the bar and the badge agree.
 * @property barFraction 0f..1f — [percentPerKSteps] normalised against the max in the returned set.
 * @property isBestBuy true on exactly one upgrade (the single Best Buy).
 * @property bestBuyAffordable meaningful only on the Best Buy row: true when the Best Buy is currently
 *   affordable; false when no candidate was affordable and it fell back to the highest-value overall
 *   (the UI greys it as "save up for this").
 */
data class UpgradeValue(
    val type: UpgradeType,
    val valuePerStep: Double,
    val percentPerKSteps: Double,
    val barFraction: Float,
    val isBestBuy: Boolean,
    val bestBuyAffordable: Boolean,
)

/**
 * Computes the value-per-step of each combat upgrade and flags the single Best Buy (#29, spec §3-§4).
 *
 * Coverage (spec §3.3): an upgrade is a candidate only when buying one workshop level raises the
 * [CombatPower] index (`Δpower > 0`). That one rule excludes Range / Damage-per-meter / Rapid Fire /
 * all Defense / all Utility (not in the index) and Critical Factor while crit chance is 0 (the synergy
 * collapses the crit factor to 1). Maxed and cost-0 upgrades are excluded defensively.
 *
 * Best Buy (spec §3.4): the highest-`valuePerStep` upgrade the player can afford; if none is
 * affordable, the highest-value overall, flagged [UpgradeValue.bestBuyAffordable] = false. Exactly one
 * upgrade is flagged; ties break by [UpgradeType] declaration order so the badge never flickers.
 *
 * Increments the **workshop** dimension via [WorkshopLevels] (spec §5.1). Pure Kotlin — guarded by
 * `architecture/DomainPurityTest`.
 */
class EvaluateUpgradeValue(
    private val resolveStats: ResolveStats = ResolveStats(),
    private val combatPower: CombatPower = CombatPower(),
    private val calculateCost: CalculateUpgradeCost = CalculateUpgradeCost(),
) {

    private data class Scored(val type: UpgradeType, val value: Double, val pct: Double)

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        stepBalance: Long,
        candidates: Collection<UpgradeType>,
    ): List<UpgradeValue> {
        val currentPower = combatPower(resolveStats(workshopLevels))

        val scored = candidates.mapNotNull { type ->
            if (WorkshopLevels.isAtMax(workshopLevels, type)) return@mapNotNull null
            val cost = calculateCost(type, WorkshopLevels.levelOf(workshopLevels, type))
            if (cost <= 0L) return@mapNotNull null
            val nextPower = combatPower(resolveStats(WorkshopLevels.withIncremented(workshopLevels, type)))
            val delta = nextPower - currentPower
            if (delta <= 0.0) return@mapNotNull null
            val value = delta / cost
            val pct = if (currentPower > 0.0) value / currentPower * 1000.0 * 100.0 else 0.0
            Scored(type, value, pct)
        }
        if (scored.isEmpty()) return emptyList()

        // Best Buy: highest value affordable; else highest value overall (greyed). Ties -> lowest ordinal.
        val byValueThenOrdinal = compareByDescending<Scored> { it.value }.thenBy { it.type.ordinal }
        val costOf = { s: Scored -> calculateCost(s.type, WorkshopLevels.levelOf(workshopLevels, s.type)) }
        val affordable = scored.filter { costOf(it) <= stepBalance }
        val bestBuyIsAffordable = affordable.isNotEmpty()
        val bestBuyType = (if (bestBuyIsAffordable) affordable else scored)
            .minWithOrNull(byValueThenOrdinal)!!.type

        val maxPct = scored.maxOf { it.pct }
        return scored.map { s ->
            UpgradeValue(
                type = s.type,
                valuePerStep = s.value,
                percentPerKSteps = s.pct,
                barFraction = if (maxPct > 0.0) (s.pct / maxPct).toFloat() else 0f,
                isBestBuy = s.type == bestBuyType,
                // True only on the flagged Best Buy row (the field is meaningful only there — review F2).
                bestBuyAffordable = bestBuyIsAffordable && s.type == bestBuyType,
            )
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.usecase.EvaluateUpgradeValueTest" > build.log 2>&1; tail -n 25 build.log`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValue.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/EvaluateUpgradeValueTest.kt
git commit -m "feat(#29): EvaluateUpgradeValue — value-per-step ranking + Best Buy selection"
```

---

### Task 5: `formatPowerPerKStepsLabel` pure formatter

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabelTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.workshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpgradeValueLabelTest {

    @Test
    fun `formats one decimal place with leading plus and per-1000-steps suffix`() {
        assertEquals("+4.0% power / 1,000 steps", formatPowerPerKStepsLabel(4.0))
    }

    @Test
    fun `rounds to one decimal`() {
        assertEquals("+1.6% power / 1,000 steps", formatPowerPerKStepsLabel(1.5567))
    }

    @Test
    fun `a positive value that would round to zero is floored to plus 0_1 percent`() {
        // Critical Factor at very low crit chance can be tiny-but-positive; never show "+0.0%".
        assertEquals("+0.1% power / 1,000 steps", formatPowerPerKStepsLabel(0.0001))
    }

    @Test
    fun `uses Locale ROOT decimal point regardless of default locale`() {
        // Would be "+12,5%" under a comma-decimal locale; ROOT pins the dot.
        assertEquals("+12.5% power / 1,000 steps", formatPowerPerKStepsLabel(12.5))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.UpgradeValueLabelTest" > build.log 2>&1; tail -n 20 build.log`
Expected: FAIL — unresolved reference `formatPowerPerKStepsLabel`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabel.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.workshop

import java.util.Locale

/**
 * Formats a combat-power value-per-step as the Workshop value-bar label, e.g.
 * "+1.6% power / 1,000 steps" (#29, spec §3.2 / §5.3).
 *
 * Callers render a bar only when the upgrade's Δpower > 0, so [percentPerKSteps] is always positive
 * and the leading "+" is always valid. A small-but-positive value that would round to "+0.0%" (e.g.
 * Critical Factor at very low crit chance) is floored to "+0.1%" so a card that legitimately carries a
 * bar never shows a contradictory "+0.0%". `Locale.ROOT` keeps the decimal separator device-independent
 * (matching `DescribeUpgradeEffect.fmt`) — do NOT copy `WorkshopViewModel.statValueFor`'s default-locale
 * `.format()` (spec INV-5).
 */
fun formatPowerPerKStepsLabel(percentPerKSteps: Double): String {
    val shown = if (percentPerKSteps > 0.0 && percentPerKSteps < 0.05) 0.1 else percentPerKSteps
    return String.format(Locale.ROOT, "+%.1f%% power / 1,000 steps", shown)
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.UpgradeValueLabelTest" > build.log 2>&1; tail -n 20 build.log`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeValueLabelTest.kt
git commit -m "feat(#29): formatPowerPerKStepsLabel — Locale.ROOT value-bar label with +0.1% floor"
```

---

### Task 6: New `UpgradeDisplayInfo` fields

Add the decision-support fields to the UiState (no behaviour yet — the VM populates them in Task 7, the card reads them in Task 8). Keeping this its own commit means the type change compiles independently.

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopUiState.kt`

- [ ] **Step 1: Add the fields**

Replace the `UpgradeDisplayInfo` data class in `WorkshopUiState.kt` with:

```kotlin
data class UpgradeDisplayInfo(
    val type: UpgradeType,
    val level: Int,
    val cost: Long,
    val isMaxed: Boolean,
    val canAfford: Boolean,
    val description: String,
    val statValue: String = "",
    // #29 decision support. `nowNext` = workshop-dimension Now→Next preview (null only if it could not
    // be computed). `value` = combat-power value/Best-Buy data; null for non-combat upgrades (Δpower ≤ 0)
    // → the card renders no bar/badge for them.
    val nowNext: UpgradeEffectReadout? = null,
    val value: UpgradeValue? = null,
)
```

Add these imports to the top of `WorkshopUiState.kt` (below the existing two imports):

```kotlin
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeEffectReadout
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeValue
```

- [ ] **Step 2: Verify it compiles**

Run: `./run-gradle.sh compileDebugKotlin > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL (defaults keep all existing construction sites valid).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopUiState.kt
git commit -m "feat(#29): add nowNext + value fields to UpgradeDisplayInfo"
```

---

### Task 7: Wire the use cases into `WorkshopViewModel`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModelTest.kt` (append)

- [ ] **Step 1: Write the failing tests**

Append to `WorkshopViewModelTest.kt` (before the class's final `}`):

```kotlin
    @Test
    fun `R29 combat upgrades carry value and exactly one is best buy`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val attack = vm.uiState.value.upgrades
        // DAMAGE/ATTACK_SPEED/CRITICAL_CHANCE get a value; the best buy from an all-zero tab is DAMAGE.
        val damage = attack.single { it.type == UpgradeType.DAMAGE }
        assertNotNull(damage.value, "DAMAGE must carry combat-power value data")
        assertNotNull(damage.nowNext, "DAMAGE must carry a Now→Next preview")
        assertEquals(1, attack.count { it.value?.isBestBuy == true }, "exactly one Best Buy")
        assertTrue(attack.single { it.value?.isBestBuy == true }.type == UpgradeType.DAMAGE)
    }

    @Test
    fun `R29 non-combat upgrades have null value but still a Now-Next preview`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.selectCategory(UpgradeCategory.DEFENSE)
        advanceUntilIdle()
        val health = vm.uiState.value.upgrades.single { it.type == UpgradeType.HEALTH }
        assertNull(health.value, "Defense upgrades get no value bar/badge (spec §3.3)")
        assertNotNull(health.nowNext, "Defense upgrades still get the Now→Next preview")
        assertEquals(0, vm.uiState.value.upgrades.count { it.value?.isBestBuy == true }, "no Best Buy on the Defense tab")
    }

    @Test
    fun `R29 best buy is greyed when nothing on the tab is affordable`() = runTest(dispatcher) {
        playerRepo.profile.value = PlayerProfile(stepBalance = 0)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val best = vm.uiState.value.upgrades.single { it.value?.isBestBuy == true }
        assertFalse(best.value!!.bestBuyAffordable, "with 0 Steps the Best Buy falls back to greyed")
    }
```

Add this import to the test file's import block (the others — `assertNull`, `assertFalse`, `assertTrue`, `assertEquals` — come from the existing `org.junit.jupiter.api.Assertions.*`):

```kotlin
import org.junit.jupiter.api.Assertions.assertNotNull
```

(`Assertions.*` is already wildcard-imported, so `assertNotNull` is already available — only add the line if the build reports it unresolved.)

- [ ] **Step 2: Run the tests, verify they fail**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.WorkshopViewModelTest" > build.log 2>&1; tail -n 25 build.log`
Expected: FAIL — `value`/`nowNext` are always null (VM doesn't populate them yet).

- [ ] **Step 3: Wire the use cases into the VM**

In `WorkshopViewModel.kt`, add two imports next to the existing use-case imports:

```kotlin
import com.whitefang.stepsofbabylon.domain.usecase.DescribeUpgradeEffect
import com.whitefang.stepsofbabylon.domain.usecase.EvaluateUpgradeValue
```

Add two instances next to the existing `private val resolveStats = ResolveStats()` (after line 37):

```kotlin
    private val describeUpgradeEffect = DescribeUpgradeEffect()
    private val evaluateUpgradeValue = EvaluateUpgradeValue()
```

Then, inside the `combine { ... }` block, replace the `WorkshopUiState(upgrades = filtered.map { ... })` construction with the version below. The change: compute `values` once for the tab's candidate set, then add `nowNext` + `value` to each `UpgradeDisplayInfo`.

Replace this existing block:

```kotlin
        WorkshopUiState(
            upgrades = filtered.map { (type, level) ->
                val maxLevel = type.config.maxLevel
                val isMaxed = maxLevel != null && level >= maxLevel
                val cost = if (isMaxed) 0L else calculateCost(type, level)
                UpgradeDisplayInfo(
                    type = type, level = level, cost = cost, isMaxed = isMaxed,
                    canAfford = !isMaxed && wallet.stepBalance >= cost,
                    description = type.config.description,
                    statValue = statValueFor(type, stats),
                )
            },
            stepBalance = wallet.stepBalance,
            selectedCategory = category,
            isLoading = false,
            isProcessing = processing,
            userMessage = message,
        )
```

with:

```kotlin
        // #29: value/Best-Buy data for the CURRENT tab's candidates (per-tab scoping, spec §5.2).
        // Pass the FULL upgrade map so ResolveStats sees every stat; candidates = this tab's visible
        // types. EvaluateUpgradeValue returns only the Δpower>0 ones, keyed back by type below.
        val values = evaluateUpgradeValue(upgrades, wallet.stepBalance, filtered.keys)
            .associateBy { it.type }
        WorkshopUiState(
            upgrades = filtered.map { (type, level) ->
                val maxLevel = type.config.maxLevel
                val isMaxed = maxLevel != null && level >= maxLevel
                val cost = if (isMaxed) 0L else calculateCost(type, level)
                UpgradeDisplayInfo(
                    type = type, level = level, cost = cost, isMaxed = isMaxed,
                    canAfford = !isMaxed && wallet.stepBalance >= cost,
                    description = type.config.description,
                    statValue = statValueFor(type, stats),
                    nowNext = describeUpgradeEffect.workshopPreview(upgrades, type = type),
                    value = values[type],
                )
            },
            stepBalance = wallet.stepBalance,
            selectedCategory = category,
            isLoading = false,
            isProcessing = processing,
            userMessage = message,
        )
```

Note: `filtered.keys` is a `Set<UpgradeType>` — a `Collection<UpgradeType>`, which is what `EvaluateUpgradeValue` accepts.

- [ ] **Step 4: Run the tests, verify they pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.WorkshopViewModelTest" > build.log 2>&1; tail -n 25 build.log`
Expected: PASS (all tests — the 3 new ones plus the existing 9).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModelTest.kt
git commit -m "feat(#29): WorkshopViewModel populates nowNext + value (per-tab Best Buy)"
```

---

### Task 8: Render Now→Next + value bar + Best-Buy badge in `UpgradeCard`

No unit test (Compose rendering is verified on-device per PR-4736). The new elements read the Task-6 fields; the math is already proven in Tasks 1–5/7.

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt`

- [ ] **Step 1: Replace `UpgradeCard.kt` with the version that renders the new elements**

Replace the entire file with:

```kotlin
package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.theme.BronzeSurface
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.Ivory
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess

@Composable
fun UpgradeCard(info: UpgradeDisplayInfo, onClick: () -> Unit) {
    // Whole-card dim is reserved for the MAXED state (paired with the Gold tint below). An
    // *unaffordable* card stays fully opaque so its title/description remain readable — only the
    // cost/stat readout dims (see `valueAlpha`).
    val cardAlpha = if (info.isMaxed) 0.85f else 1f
    val valueAlpha = when {
        info.isMaxed -> 1f
        info.canAfford -> 1f
        else -> 0.55f
    }

    val pulse = rememberPulse()
    val haptics = rememberHaptics()

    Card(
        onClick = {
            // Guarded redundantly with `enabled` below (#154): at cap/unaffordable the Card is disabled.
            if (info.canAfford && !info.isMaxed) {
                pulse.trigger()
                haptics.tap()
                onClick()
            }
        },
        enabled = info.canAfford && !info.isMaxed,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha).pulseScale(pulse),
        colors = if (info.isMaxed) {
            CardDefaults.cardColors(
                containerColor = Gold.copy(alpha = 0.15f),
                disabledContainerColor = Gold.copy(alpha = 0.15f),
            )
        } else {
            CardDefaults.cardColors(
                disabledContainerColor = CardDefaults.cardColors().containerColor,
            )
        },
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            // #29: "★ BEST BUY" chip on the single highest-value upgrade. Greyed (alpha) when the
            // Best Buy isn't currently affordable ("save up for this"). Static — NOT a PurchasePulse.
            info.value?.let { value ->
                if (value.isBestBuy) {
                    BestBuyChip(affordable = value.bestBuyAffordable)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.type.name.replace('_', ' '),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // #29: Now → Next preview (workshop dimension). Shown when there's a next level.
                    info.nowNext?.let { readout ->
                        readout.next?.let { next ->
                            Text(
                                text = "${readout.current} → $next",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.alpha(valueAlpha)) {
                    Text(
                        text = if (info.isMaxed) stringResource(R.string.upgrade_max) else stringResource(R.string.upgrade_level, info.level),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (info.isMaxed) Gold else MaterialTheme.colorScheme.onSurface,
                    )
                    if (info.statValue.isNotEmpty()) {
                        Text(text = info.statValue, style = MaterialTheme.typography.labelSmall, color = Gold)
                    }
                    if (!info.isMaxed) {
                        Text(
                            text = stringResource(R.string.upgrade_cost_steps, info.cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (info.canAfford) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // #29: combat-power value bar + label. Rendered only for combat upgrades (value != null).
            info.value?.let { value ->
                ValueBar(fraction = value.barFraction)
                Text(
                    text = formatPowerPerKStepsLabel(value.percentPerKSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusSuccess,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BestBuyChip(affordable: Boolean) {
    // Affordable: solid Gold background with dark (DeepBronze = colorScheme.surface) text — ~4.2:1,
    // matches the theme's onPrimary=DeepBronze rationale. Greyed "save up" state: a desaturated SOLID
    // fill (NOT Gold@0.4f over the dark card, which gives illegible ~1.9:1 dark-on-dark — review F1)
    // with light Ivory text for a legible >4:1 contrast. The exact tokens are confirmed on-device
    // (Task 8 Step 4); keep the fill opaque so the greyed chip never composites to dark-on-dark.
    val bg = if (affordable) Gold else BronzeSurface
    val fg = if (affordable) MaterialTheme.colorScheme.surface else Ivory
    Box(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (affordable) "★ BEST BUY" else "★ BEST BUY · SAVE UP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun ValueBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StatusSuccess),
        )
    }
}
```

- [ ] **Step 2: Verify the whole module builds + lints**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > build.log 2>&1; tail -n 30 build.log`
Expected: BUILD SUCCESSFUL; all JVM unit tests pass; lint clean.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt
git commit -m "feat(#29): render Now→Next + value bar + Best-Buy badge on UpgradeCard"
```

- [ ] **Step 4: On-device verification (manual — record the result in the PR/RUN_LOG)**

Launch the app on the emulator and open the Workshop screen. Confirm:
- ATTACK tab: Damage / Attack Speed / Critical Chance show a green value bar + "+X.X% power / 1,000 steps"; the highest-value one (Damage from a fresh profile) shows the "★ BEST BUY" chip.
- Range / Damage-per-meter / Rapid Fire show a Now→Next line but **no** bar/badge.
- DEFENSE and UTILITY tabs: Now→Next lines, **no** bars/badges, no Best-Buy chip.
- With a low Step balance, the Best-Buy chip shows the greyed "★ BEST BUY · SAVE UP" variant.
- Critical Factor shows **no** bar until some Critical Chance is owned.

If anything looks wrong, fix in `UpgradeCard.kt` and amend the Task-8 commit; the math is pinned by Tasks 1–7 so layout-only tweaks don't need new unit tests.

---

### Task 9: Sync current-state docs + STATE/RUN_LOG (PR Task-List Convention)

Per CLAUDE.md, doc-sync runs **before** the STATE/RUN_LOG update.

**Files:**
- Modify: `docs/steering/source-files.md`, `CHANGELOG.md`, `CLAUDE.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.

- [ ] **Step 1: Determine the new headline test count**

Run: `./run-gradle.sh testDebugUnitTest > build.log 2>&1; grep -iE "tests completed|BUILD (SUCCESSFUL|FAILED)" build.log | tail -n 3`
Record the new total (was **1010 JVM**; this PR adds 7 + 5 + 4 + 12 + 4 + 3 = **35** new JVM tests → expect **1045**; confirm against the actual run and use the real number below).

- [ ] **Step 2: Add `source-files.md` entries**

In `docs/steering/source-files.md`, add entries (matching the file's existing format) for:
- `domain/usecase/CombatPower.kt` — combat-power index (DPS proxy) for upgrade ranking (#29).
- `domain/usecase/WorkshopLevels.kt` — shared workshop-dimension cap/increment helpers (#29).
- `domain/usecase/EvaluateUpgradeValue.kt` — value-per-step ranking + Best-Buy selection (#29).
- `presentation/workshop/UpgradeValueLabel.kt` — pure "+X.X% power / 1,000 steps" formatter (#29).
- Update the `DescribeUpgradeEffect.kt` entry to note the new `workshopPreview` path.
- Fix the **stale `DescribeUpgradeEffectTest.kt` test-count** in `source-files.md` (line ~434 currently
  says "28 tests total" / "RO-12 adds 3" — the file actually has 35 before this PR's +4; update to the
  real post-PR count from Step 1) (review F2).
- Update the `WorkshopViewModel.kt` / `UpgradeCard.kt` / `WorkshopUiState.kt` entries to note the decision-support fields/rendering.

- [ ] **Step 3: Add a `CHANGELOG.md` section**

Under `[Unreleased]`, add a bullet:
```markdown
- **#29 Workshop decision support (Gate F):** Now→Next stat preview on every upgrade; combat-power
  "value per step" indicator + bar on combat upgrades (Damage / Attack Speed / Critical Chance, and
  Critical Factor once crit chance > 0); a single "★ BEST BUY" badge (affordable-first, greyed
  "save up" fallback). Presentation + pure domain math only — new `CombatPower` / `EvaluateUpgradeValue`
  / `WorkshopLevels` use cases; no schema/engine/economy change. +35 JVM tests.
```
Update the current-state test-count block in CHANGELOG if it tracks one.

- [ ] **Step 4: Update the CLAUDE.md headline count**

In `CLAUDE.md`, update the Testing line from `1010 JVM tests + 9 instrumented tests` to the new total (e.g. `1045 JVM tests + 9 instrumented tests`) — use the real number from Step 1.

- [ ] **Step 5: Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`**

- In `STATE.md`: update the headline + "Recently shipped" with the #29 decision-support entry; add a fragile-zone note: *"Combat-power index (#29) is a display/ranking proxy only — returns Double, never feed it to the engine (type-incompatible with ResolvedStats sinks). Best-Buy/value bar apply only when Δpower>0; Workshop previews increment the WORKSHOP dimension via `WorkshopLevels` (not in-round — spec §5.1)."* Move #29 from "next objective" toward done (Gate F).
- In `RUN_LOG.md`: append a session entry describing the work, the spec/plan adversarial reviews, the files added, and the new test count.

- [ ] **Step 6: Commit**

```bash
git add docs/steering/source-files.md CHANGELOG.md CLAUDE.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#29): sync current-state docs + STATE/RUN_LOG for Workshop decision support"
```

---

## Final verification

- [ ] **Full build green:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > build.log 2>&1; tail -n 30 build.log` → BUILD SUCCESSFUL, all JVM tests pass, lint clean.
- [ ] **DomainPurityTest green** (the two new domain use cases import nothing Android): it runs as part of `testDebugUnitTest`; confirm no `DomainPurityTest` failure in the log.
- [ ] **On-device check done** (Task 8 Step 4) and recorded.
- [ ] Then proceed to the whole-branch review / PR via the project's normal flow.
