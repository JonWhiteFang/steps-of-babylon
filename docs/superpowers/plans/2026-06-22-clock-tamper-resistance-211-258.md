# Clock-tamper resistance (#211) + schema-doc gap-fill (#258) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add proportionate time-tamper resistance for an offline single-player game — close backward-rollback farming (login/streak/season-pass) reboot-durably and the in-session research forward-jump — via a pure-domain `TimeIntegrity` core + a baseline persisted in the existing `AntiCheatPreferences`; plus a docs-only pre-v7-upgrade-crash note (#258).

**Architecture:** A pure-Kotlin `TimeIntegrity` object (`domain/time/`, zero Android — `DomainPurityTest`) decides `Trusted`/`Rollback` from a persisted `(lastElapsedRealtime, lastWallClock, maxWallClockSeen)` baseline + a fresh `(elapsedRealtime, wallClock)` reading. `AntiCheatPreferences` gains 3 `Long` slots + the Android clock reads. **`DailyStepManager` is the SINGLE owner** that evaluates+persists the baseline (under its existing #120 mutex); `HomeViewModel`/`LabsViewModel` are read-only consumers. The login credit path refuses credit on `Rollback` (early no-credit return, no row write); research completion gates on a trusted-now derived from the pre-advance baseline.

**Tech Stack:** Kotlin, JUnit Jupiter (pure-JVM use-case + `TimeIntegrity` tests), Robolectric (`AntiCheatPreferences`/store tests), Hilt (DailyStepManager already injects `AntiCheatPreferences`). Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-22-clock-tamper-resistance-211-258.md` (adversarially reviewed: 22 findings → 20 surviving, applied).

---

## Guiding rules

- Build wrapper is `./run-gradle.sh` (NOT ./gradlew). Output is large — redirect to a temp file, tail/grep.
- TDD per unit: failing test → minimal code → green. The bulk of #211 coverage is `TimeIntegrityTest` (pure).
- **Domain purity is sacrosanct:** `TimeIntegrity` + its data classes have ZERO Android imports; `DomainPurityTest` must stay green. The `SystemClock.elapsedRealtime()`/`System.currentTimeMillis()` reads live ONLY in `AntiCheatPreferences` (data layer).
- **Single baseline owner:** only `DailyStepManager` (under #120 mutex) calls the evaluate-and-**persist** path. `HomeViewModel`/`LabsViewModel` read the baseline + derive a verdict/trusted-now but never persist.
- Behavior change is limited to the two intended effects (rollback → no credit; in-session forward jump → research not instant). Legitimate paths stay green.
- Commit after each task. Already on branch `fix/211-258-clock-tamper-schema-doc` (spec committed there).

---

## File structure

| File | Change | Responsibility |
|---|---|---|
| `docs/database-schema.md` | Modify | #258: add the pre-v7 upgrade-crash note (docs-only) |
| `domain/time/TimeIntegrity.kt` | Create | pure decision core: `TimeBaseline`/`TimeReading`/`TimeVerdict` + `evaluate`/`trustedElapsedSince` |
| `data/anticheat/AntiCheatPreferences.kt` | Modify | 3 new `Long` slots + `readTimeBaseline()`/`writeTimeBaseline()` + `currentTimeReading()` (the Android clock reads) |
| `domain/usecase/TrackDailyLogin.kt` | Modify | gains a `TimeVerdict` param; on `Rollback` → early no-credit return |
| `domain/usecase/CheckResearchCompletion.kt` | Modify | `now` becomes the trusted-now passed by callers (no signature-shape change) |
| `domain/usecase/CompleteResearch.kt` | Modify | same — gate on the trusted-now |
| `data/sensor/DailyStepManager.kt` | Modify | the SINGLE owner: evaluate+persist baseline under #120 mutex; pass verdict to TrackDailyLogin |
| `presentation/home/HomeViewModel.kt` | Modify | read-only consumer: derive verdict (no persist) for its TrackDailyLogin + trusted-now for research |
| `presentation/labs/LabsViewModel.kt` | Modify | read-only consumer: trusted-now for `checkCompletion()`/`CompleteResearch` |
| `app/.../domain/time/TimeIntegrityTest.kt` | Create | pure table-driven tests (bulk of coverage) |
| `app/.../data/anticheat/AntiCheatPreferencesTimeBaselineTest.kt` | Create | Robolectric round-trip of the 3 slots |
| `app/.../domain/usecase/TrackDailyLoginTest.kt` | Modify | rollback → no credit/no row; normal → credited; latched-future |
| `app/.../domain/usecase/CheckResearchCompletionTest.kt` + `CompleteResearchTest.kt` | Modify | in-session forward-jump → not complete |
| `docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md` | Create | the design decision + scope boundary |

**Task order:** Task 1 (#258 docs — fast, independent) → Task 2 (`TimeIntegrity` pure core + tests) → Task 3 (`AntiCheatPreferences` baseline store + Robolectric test) → Task 4 (`TrackDailyLogin` rollback guard + tests) → Task 5 (research trusted-now guard + tests) → Task 6 (`DailyStepManager` single-owner wiring) → Task 7 (`HomeViewModel`/`LabsViewModel` read-only consumers) → Task 8 (ADR + docs sync) → Task 9 (STATE/RUN_LOG) → Task 10 (final gate + push + PR).

---

### Task 1: #258 — schema-doc pre-v7 upgrade-crash note (docs-only)

**Files:**
- Modify: `docs/database-schema.md`

- [ ] **Step 1: Confirm the floor-safety fact from release history**

Run: `rg -n "1.0.0|v1.0.0|first release|internal track" CHANGELOG.md | head` and confirm the app first shipped at schema ≥ v7 (no pre-v7 public install). This is the rationale the note asserts. (If CHANGELOG contradicts it, STOP and report — the alternative is a developer decision, not this doc fix.)

- [ ] **Step 2: Add the note near the migration-floor line**

In `docs/database-schema.md`, find the migration-floor bullet (~line 250, the "Pre-v7 schemas… migration floor is v7" line). Immediately after it, add:
```markdown
- **Pre-v7 upgrade behavior:** there is no v1→v7 *upgrade* path — those migrations were never written
  (floor assumed at v7). A surviving pre-v7 install hitting an upgrade would therefore throw Room's
  missing-migration `IllegalStateException` (a launch crash), **not** silently reset — because only
  `fallbackToDestructiveMigrationOnDowngrade` is configured, never the bare
  `fallbackToDestructiveMigration()`. The floor is safe because the app first shipped to the Play
  internal track at schema ≥ v7, so no public/pre-v7 install exists to upgrade (#258).
```

- [ ] **Step 3: Commit**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git add docs/database-schema.md
git commit -m "docs(schema): document pre-v7 missing-migration upgrade-crash behavior (#258)"
```

---

### Task 2: `TimeIntegrity` pure-domain core + tests

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/time/TimeIntegrity.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/time/TimeIntegrityTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `TimeIntegrityTest.kt`:
```kotlin
package com.whitefang.stepsofbabylon.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeIntegrityTest {
    private fun reading(elapsed: Long, wall: Long) = TimeReading(elapsed, wall)

    @Test
    fun `first run with null baseline is Trusted and seeds maxWallClockSeen`() {
        val v = TimeIntegrity.evaluate(null, reading(elapsed = 1000, wall = 5000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(5000, v.newBaseline.maxWallClockSeen)
        assertEquals(5000, v.newBaseline.lastWallClock)
        assertEquals(1000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `normal forward advance is Trusted and advances maxWallClockSeen`() {
        val base = TimeBaseline(lastElapsedRealtime = 1000, lastWallClock = 5000, maxWallClockSeen = 5000)
        val v = TimeIntegrity.evaluate(base, reading(elapsed = 4000, wall = 8000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(8000, v.newBaseline.maxWallClockSeen)
        assertEquals(8000, v.newBaseline.lastWallClock)
        assertEquals(4000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `backward jump below the floor is Rollback even when elapsed advanced`() {
        val base = TimeBaseline(lastElapsedRealtime = 1000, lastWallClock = 9000, maxWallClockSeen = 9000)
        // wall jumped BACK to 4000 (< maxSeen 9000) while elapsed advanced normally
        val v = TimeIntegrity.evaluate(base, reading(elapsed = 2000, wall = 4000))
        assertTrue(v is TimeVerdict.Rollback)
        assertEquals(9000, v.newBaseline.maxWallClockSeen) // floor unchanged (max of prev, 4000)
        assertEquals(4000, v.newBaseline.lastWallClock)
        assertEquals(2000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `reboot (elapsed reset to small) with wall still above floor is Trusted not false-rollback`() {
        val base = TimeBaseline(lastElapsedRealtime = 900_000, lastWallClock = 9000, maxWallClockSeen = 9000)
        // after reboot elapsedRealtime resets near 0; wall still >= floor
        val v = TimeIntegrity.evaluate(base, reading(elapsed = 50, wall = 9500))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(9500, v.newBaseline.maxWallClockSeen)
    }

    @Test
    fun `trustedElapsedSince caps a forward wall jump at the elapsed delta`() {
        val base = TimeBaseline(lastElapsedRealtime = 1000, lastWallClock = 5000, maxWallClockSeen = 5000)
        // wall leapt +30 min (1_800_000) but only 5 min (300_000) of monotonic time passed
        val trusted = TimeIntegrity.trustedElapsedSince(base, reading(elapsed = 301_000, wall = 1_805_000))
        assertEquals(300_000, trusted) // min(1_800_000, 300_000)
    }

    @Test
    fun `trustedElapsedSince returns full wall delta on reboot (elapsed delta negative)`() {
        val base = TimeBaseline(lastElapsedRealtime = 900_000, lastWallClock = 5000, maxWallClockSeen = 5000)
        // reboot: elapsed reset → elapsedDelta negative → fall back to wall delta (the accepted gap)
        val trusted = TimeIntegrity.trustedElapsedSince(base, reading(elapsed = 100, wall = 8000))
        assertEquals(3000, trusted)
    }

    @Test
    fun `trustedElapsedSince clamps a backward wall move to zero`() {
        val base = TimeBaseline(lastElapsedRealtime = 1000, lastWallClock = 9000, maxWallClockSeen = 9000)
        val trusted = TimeIntegrity.trustedElapsedSince(base, reading(elapsed = 2000, wall = 4000))
        assertEquals(0, trusted) // min(-5000, 1000) = -5000 → clamp ≥ 0
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*TimeIntegrityTest*" > /tmp/t2.log 2>&1; tail -25 /tmp/t2.log`
Expected: FAIL — `TimeIntegrity`/`TimeBaseline`/`TimeReading`/`TimeVerdict` unresolved.

- [ ] **Step 3: Create `TimeIntegrity.kt`**

```kotlin
package com.whitefang.stepsofbabylon.domain.time

/**
 * Pure-domain time-tamper decision core (#211, TIME-1). No Android imports (DomainPurityTest). Backs the
 * monotonic anti-rollback guard + the reboot-durable max-wall-clock floor described in the #211 spec.
 * The Android clock reads (SystemClock.elapsedRealtime / System.currentTimeMillis) live in the data
 * layer (AntiCheatPreferences); this object only reasons over the values.
 */

/** Persisted tamper baseline (three Long slots in anti_cheat_prefs). */
data class TimeBaseline(
    val lastElapsedRealtime: Long,   // monotonic since-boot clock at last checkpoint
    val lastWallClock: Long,         // wall-clock at last checkpoint
    val maxWallClockSeen: Long,      // highest wall-clock ever observed — the reboot-durable rollback floor
)

/** A fresh pair of readings taken together at one instant. */
data class TimeReading(val elapsedRealtime: Long, val wallClock: Long)

/** Classification of the current reading vs the persisted baseline. Always carries the advanced baseline. */
sealed interface TimeVerdict {
    val newBaseline: TimeBaseline
    data class Trusted(override val newBaseline: TimeBaseline) : TimeVerdict
    data class Rollback(override val newBaseline: TimeBaseline) : TimeVerdict
}

object TimeIntegrity {

    /**
     * Update the baseline from a fresh reading and classify the time axis.
     * - baseline == null (first run): Trusted; seed maxWallClockSeen = reading.wallClock.
     * - reading.wallClock < baseline.maxWallClockSeen: Rollback (backward jump — reboot-durable).
     * - else: Trusted.
     *
     * In ALL branches the new baseline advances lastWallClock / lastElapsedRealtime to "now"; only
     * maxWallClockSeen is the monotonic-max floor (max of prev and reading.wallClock).
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict {
        if (baseline == null) {
            return TimeVerdict.Trusted(
                TimeBaseline(reading.elapsedRealtime, reading.wallClock, reading.wallClock),
            )
        }
        val newMax = maxOf(baseline.maxWallClockSeen, reading.wallClock)
        val advanced = TimeBaseline(reading.elapsedRealtime, reading.wallClock, newMax)
        return if (reading.wallClock < baseline.maxWallClockSeen) {
            TimeVerdict.Rollback(advanced)
        } else {
            TimeVerdict.Trusted(advanced)
        }
    }

    /**
     * Trusted elapsed wall-time since the baseline, for the in-session forward-jump guard:
     * min(wallDelta, elapsedDelta) clamped to >= 0. A forward wall jump can't claim more "real time"
     * than the monotonic clock advanced this session.
     *
     * Reboot fallback (accepted §2 gap): when elapsedDelta < 0 the device rebooted (elapsedRealtime is
     * since-boot) so there is no in-session monotonic delta to cap against → returns the FULL wall delta.
     * The Rollback floor does NOT guard this (it catches only backward jumps), so a reboot-spanning
     * forward jump passes through — the documented-accepted boundary.
     */
    fun trustedElapsedSince(baseline: TimeBaseline, reading: TimeReading): Long {
        val wallDelta = reading.wallClock - baseline.lastWallClock
        val elapsedDelta = reading.elapsedRealtime - baseline.lastElapsedRealtime
        if (elapsedDelta < 0) return wallDelta.coerceAtLeast(0) // reboot: no monotonic cap available
        return minOf(wallDelta, elapsedDelta).coerceAtLeast(0)
    }
}
```

- [ ] **Step 4: Run to verify pass + purity**

Run: `./run-gradle.sh testDebugUnitTest --tests "*TimeIntegrityTest*" --tests "*DomainPurityTest*" > /tmp/t2.log 2>&1; tail -25 /tmp/t2.log`
Expected: PASS for both (TimeIntegrity has zero Android imports).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/time/TimeIntegrity.kt app/src/test/java/com/whitefang/stepsofbabylon/domain/time/TimeIntegrityTest.kt
git commit -m "feat(time): pure-domain TimeIntegrity tamper-decision core (#211)"
```

---

### Task 3: `AntiCheatPreferences` time-baseline store + Robolectric test

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/anticheat/AntiCheatPreferences.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/anticheat/AntiCheatPreferencesTimeBaselineTest.kt`

- [ ] **Step 1: Write the failing Robolectric test**

Create `AntiCheatPreferencesTimeBaselineTest.kt` (Robolectric — the real prefs-test convention, per `OnboardingPreferencesTest`):
```kotlin
package com.whitefang.stepsofbabylon.data.anticheat

import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AntiCheatPreferencesTimeBaselineTest {

    @Test
    fun `readTimeBaseline is null before any write`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        assertNull(prefs.readTimeBaseline())
    }

    @Test
    fun `writeTimeBaseline then readTimeBaseline round-trips all three slots`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        prefs.writeTimeBaseline(TimeBaseline(lastElapsedRealtime = 1234, lastWallClock = 5678, maxWallClockSeen = 9999))
        val read = prefs.readTimeBaseline()
        assertEquals(TimeBaseline(1234, 5678, 9999), read)
    }

    @Test
    fun `currentTimeReading returns non-negative monotonic and wall values`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        val r = prefs.currentTimeReading()
        // Robolectric supplies deterministic clocks; both reads are present and non-negative.
        org.junit.Assert.assertTrue(r.elapsedRealtime >= 0)
        org.junit.Assert.assertTrue(r.wallClock >= 0)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*AntiCheatPreferencesTimeBaselineTest*" > /tmp/t3.log 2>&1; tail -25 /tmp/t3.log`
Expected: FAIL — `readTimeBaseline`/`writeTimeBaseline`/`currentTimeReading` unresolved.

- [ ] **Step 3: Add the baseline store to `AntiCheatPreferences`**

Add these imports at the top of `AntiCheatPreferences.kt`:
```kotlin
import android.os.SystemClock
import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import com.whitefang.stepsofbabylon.domain.time.TimeReading
```
Add to the `companion object` (alongside the existing keys):
```kotlin
        private const val KEY_TAMPER_LAST_ELAPSED = "tamper_last_elapsed"
        private const val KEY_TAMPER_LAST_WALL = "tamper_last_wall"
        private const val KEY_TAMPER_MAX_WALL = "tamper_max_wall"
        private const val KEY_TAMPER_SET = "tamper_baseline_set"
```
Add these methods to the class body:
```kotlin
    /** #211: a fresh clock reading (the one Android-coupled time seam). */
    fun currentTimeReading(): TimeReading =
        TimeReading(elapsedRealtime = SystemClock.elapsedRealtime(), wallClock = System.currentTimeMillis())

    /** #211: the persisted tamper baseline, or null until first written. */
    fun readTimeBaseline(): TimeBaseline? {
        if (!prefs.getBoolean(KEY_TAMPER_SET, false)) return null
        return TimeBaseline(
            lastElapsedRealtime = prefs.getLong(KEY_TAMPER_LAST_ELAPSED, 0),
            lastWallClock = prefs.getLong(KEY_TAMPER_LAST_WALL, 0),
            maxWallClockSeen = prefs.getLong(KEY_TAMPER_MAX_WALL, 0),
        )
    }

    /** #211: persists the (advanced) tamper baseline. */
    fun writeTimeBaseline(baseline: TimeBaseline) {
        prefs.edit()
            .putLong(KEY_TAMPER_LAST_ELAPSED, baseline.lastElapsedRealtime)
            .putLong(KEY_TAMPER_LAST_WALL, baseline.lastWallClock)
            .putLong(KEY_TAMPER_MAX_WALL, baseline.maxWallClockSeen)
            .putBoolean(KEY_TAMPER_SET, true)
            .apply()
    }
```
(The 3 keys live in the existing `anti_cheat_prefs` file, so `DataDeletionManager`'s existing clear of that file wipes them — no `PREFS_NAMES` change. Confirm `DataDeletionPrefsCoverageTest` stays green in the full gate.)

- [ ] **Step 4: Run to verify pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*AntiCheatPreferencesTimeBaselineTest*" > /tmp/t3.log 2>&1; tail -25 /tmp/t3.log`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/anticheat/AntiCheatPreferences.kt app/src/test/java/com/whitefang/stepsofbabylon/data/anticheat/AntiCheatPreferencesTimeBaselineTest.kt
git commit -m "feat(anticheat): TimeBaseline store in AntiCheatPreferences (#211)"
```

---

### Task 4: `TrackDailyLogin` rollback guard + tests

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/TrackDailyLogin.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/TrackDailyLoginTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `TrackDailyLoginTest.kt` (match the file's existing fakes + `runTest`; the existing tests pass `seasonPassExpiry = Long.MAX_VALUE` to dodge the inline clock):
```kotlin
@Test
fun `R211 rollback verdict writes nothing for the tampered date`() = runTest {
    // arrange: a fresh date, normal profile
    val vm = TrackDailyLogin(dailyLoginRepo, playerRepo)
    val gemsBefore = playerRepo.observeProfile().first().gems
    vm.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
    // no DailyLogin row persisted for the tampered date
    assertNull(dailyLoginRepo.getByDate("2026-03-09"))
    // no gems credited
    assertEquals(gemsBefore, playerRepo.observeProfile().first().gems)
}

@Test
fun `R211 trusted verdict credits normally (regression)`() = runTest {
    val vm = TrackDailyLogin(dailyLoginRepo, playerRepo)
    vm.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
    // a row is written + streak/gems advance exactly as before
    assertNotNull(dailyLoginRepo.getByDate("2026-03-09"))
}

@Test
fun `R211 latched future clock then legit date is still denied while rolled back`() = runTest {
    val vm = TrackDailyLogin(dailyLoginRepo, playerRepo)
    // a rolled-back pass for date D writes nothing...
    vm.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
    assertNull(dailyLoginRepo.getByDate("2026-03-09"))
    // ...so when D legitimately arrives the credit is NOT pre-suppressed by a stale gemsClaimed=true
    vm.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
    assertNotNull(dailyLoginRepo.getByDate("2026-03-09"))
}
```
(Match the actual fake field names in the file — `dailyLoginRepo`/`playerRepo`. Add `assertNull`/`assertNotNull` imports from `org.junit.jupiter.api.Assertions` if absent, and `kotlinx.coroutines.flow.first`.)

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*TrackDailyLoginTest*" > /tmp/t4.log 2>&1; tail -30 /tmp/t4.log`
Expected: FAIL — `checkAndAward` has no `isRollback` param.

- [ ] **Step 3: Add the rollback guard**

In `TrackDailyLogin.kt`, change the `checkAndAward` signature + add an early no-credit return at the very top of the body:
```kotlin
    suspend fun checkAndAward(
        todayDate: String,
        todayCreditedSteps: Long,
        seasonPassActive: Boolean = false,
        seasonPassExpiry: Long = 0,
        isRollback: Boolean = false,
    ) {
        // #211: on a detected backward clock rollback, refuse ALL credit for the tampered date and
        // write NOTHING — no streak/gem grant, no DailyLogin row, no gemsClaimed flag. Writing
        // gemsClaimed=true here would silently deny credit when the date legitimately arrives later
        // (the !login.gemsClaimed gate). The power-stone-for-1k-steps grant is step-gated (the step
        // anti-cheat owns that axis), but on a rollback we still write nothing — the row isn't created,
        // so the PS for this date is deferred to the next trusted pass (acceptable; steps persist).
        if (isRollback) return

        val login = dailyLoginRepository.getByDate(todayDate) ?: DailyLogin(date = todayDate)
        // ... rest unchanged ...
```
Keep the entire remaining body exactly as-is. (Pass the boolean rather than the whole `TimeVerdict` to keep the domain use case free of any data-layer type; the caller maps `verdict is Rollback` → `isRollback`.)

> Note: the inline `seasonPassExpiry > System.currentTimeMillis()` at the season-pass-bonus line stays as-is for this wave — on a rollback the whole credit block (including that compare) is skipped by the early return, and forward-jump-on-season-pass is an explicit §2 non-goal.

- [ ] **Step 4: Run to verify pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*TrackDailyLoginTest*" > /tmp/t4.log 2>&1; tail -25 /tmp/t4.log`
Expected: PASS (new + existing).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/TrackDailyLogin.kt app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/TrackDailyLoginTest.kt
git commit -m "fix(login): refuse daily-login/streak credit on a clock rollback (#211)"
```

---

### Task 5: Research trusted-now guard + tests

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletion.kt` (no signature change — the existing `now` param IS the seam)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CompleteResearch.kt` (same)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletionTest.kt`, `CompleteResearchTest.kt`

> The use cases ALREADY take `now: Long`. The guard is entirely in the CALLER: it passes a *trusted now* instead of the raw `System.currentTimeMillis()` default. So the use-case CODE needs no change — but the tests gain a "forward jump → trusted now < completesAt → not complete" case that documents/locks the contract that callers pass trusted-now. (If review prefers an explicit comment in the use cases pointing callers at trusted-now, add a one-line KDoc; no logic change.)

- [ ] **Step 1: Write the failing/locking tests**

Add to `CheckResearchCompletionTest.kt`:
```kotlin
@Test
fun `R211 in-session forward jump does not complete research when trusted-now is below completesAt`() = runTest {
    labRepo.active.value = listOf(
        ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, startedAt = 1000, completesAt = 100_000),
    )
    // Raw wall-clock jumped to 200_000 (> completesAt) but the TRUSTED now (capped by monotonic
    // elapsed) is only 50_000 — research must NOT complete.
    val completed = useCase(now = 50_000)
    assertTrue(completed.isEmpty())
    assertEquals(1, labRepo.active.value.size)
}
```
Add to `CompleteResearchTest.kt`:
```kotlin
@Test
fun `R211 forward jump with trusted-now below completesAt returns NotReady`() = runTest {
    labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 0, 100_000))
    val result = useCase(ResearchType.DAMAGE_RESEARCH, completesAt = 100_000, now = 50_000)
    assertEquals(CompleteResearch.Result.NotReady, result)
}
```
(Match the files' existing `labRepo`/`useCase`/`ActiveResearch` fixtures.)

- [ ] **Step 2: Run to verify pass (these LOCK the existing contract — they pass immediately because the use case already honours `now`)**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CheckResearchCompletionTest*" --tests "*CompleteResearchTest*" > /tmp/t5.log 2>&1; tail -25 /tmp/t5.log`
Expected: PASS. (The behavior — "gate on the passed `now`" — already exists; these tests assert that a *trusted* now correctly withholds completion. The real guard is the caller passing trusted-now, done in Task 7. This task documents the contract + adds a one-line KDoc.)

- [ ] **Step 3: Add a one-line KDoc to each use case pointing callers at trusted-now**

In `CheckResearchCompletion.kt`, above `invoke`:
```kotlin
    /**
     * @param now the TRUSTED current time. Callers under #211 pass a monotonically-capped trusted-now
     *   (TimeIntegrity.trustedElapsedSince applied to the baseline), NOT the raw wall-clock, so an
     *   in-session forward clock jump can't complete research early. The default is the raw clock for
     *   tests / non-guarded call sites.
     */
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): List<ResearchType> {
```
Add the equivalent one-line `@param now` KDoc to `CompleteResearch.invoke`. No logic change.

- [ ] **Step 4: Re-run + commit**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CheckResearchCompletionTest*" --tests "*CompleteResearchTest*" > /tmp/t5.log 2>&1; tail -20 /tmp/t5.log` → PASS.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletion.kt app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CompleteResearch.kt app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletionTest.kt app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CompleteResearchTest.kt
git commit -m "test(research): lock trusted-now completion contract + KDoc (#211)"
```

---

### Task 6: `DailyStepManager` — single baseline owner (evaluate + persist under #120 mutex)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt`

`DailyStepManager` already injects `antiCheatPrefs: AntiCheatPreferences` (line 42) and calls `trackDailyLogin.checkAndAward(...)` (line ~329) inside the #120 mutex (`runFollowOnPipeline`, under `mutex.withLock`). This is the single owner.

- [ ] **Step 1: Add the imports**

In `DailyStepManager.kt`:
```kotlin
import com.whitefang.stepsofbabylon.domain.time.TimeIntegrity
import com.whitefang.stepsofbabylon.domain.time.TimeVerdict
```

- [ ] **Step 2: Evaluate + persist the baseline, pass the rollback flag to TrackDailyLogin**

At the `trackDailyLogin.checkAndAward(...)` call site (inside the mutex-held follow-on pipeline, ~line 329), immediately before the call add:
```kotlin
            // #211: single baseline owner — evaluate the time axis and persist the advanced baseline
            // (this is the ONLY site that persists; HomeViewModel is read-only). Under the #120 mutex.
            val timeVerdict = TimeIntegrity.evaluate(antiCheatPrefs.readTimeBaseline(), antiCheatPrefs.currentTimeReading())
            antiCheatPrefs.writeTimeBaseline(timeVerdict.newBaseline)
```
and change the `checkAndAward(...)` call to pass `isRollback = timeVerdict is TimeVerdict.Rollback`:
```kotlin
            trackDailyLogin.checkAndAward(
                currentDate,
                /* existing args… */,
                isRollback = timeVerdict is TimeVerdict.Rollback,
            )
```
(Preserve the existing positional args exactly — only append `isRollback`.)

- [ ] **Step 3: Build + run the DailyStepManager tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "*DailyStepManager*" > /tmp/t6.log 2>&1; tail -25 /tmp/t6.log`
Expected: PASS (existing DailyStepManager tests stay green; the baseline reads/writes go through the real `AntiCheatPreferences` under Robolectric or the existing fake — confirm the test harness; if `AntiCheatPreferences` is mocked in those tests, stub `readTimeBaseline()`/`currentTimeReading()`/`writeTimeBaseline()` to no-op/Trusted).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt
git commit -m "feat(steps): DailyStepManager owns the time baseline; passes rollback to login (#211)"
```

---

### Task 7: `HomeViewModel` / `LabsViewModel` — read-only consumers (trusted-now for research)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt`

Both are `@HiltViewModel` and construct the research use cases inline. They get `AntiCheatPreferences` via Hilt (add to the constructor) and pass a **trusted-now** to `checkCompletion()` / `CompleteResearch`. They are READ-ONLY: they read the baseline + reading and compute trusted-now, but do **NOT** call `writeTimeBaseline` (that's DailyStepManager's job). `HomeViewModel`'s `TrackDailyLogin` call passes `isRollback` derived from a read-only `evaluate` (no persist).

- [ ] **Step 1: HomeViewModel — inject AntiCheatPreferences + pass trusted-now / rollback**

Add `private val antiCheatPrefs: AntiCheatPreferences` to the `@Inject constructor`. Add the import `import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences` + `TimeIntegrity`/`TimeVerdict`. In `init` where it computes research completion + daily login (~lines 77/83):
```kotlin
            val baseline = antiCheatPrefs.readTimeBaseline()
            val reading = antiCheatPrefs.currentTimeReading()
            // read-only verdict + trusted-now (does NOT persist — DailyStepManager owns the baseline)
            val verdict = TimeIntegrity.evaluate(baseline, reading)
            val trustedNow = if (baseline == null) reading.wallClock
                else baseline.lastWallClock + TimeIntegrity.trustedElapsedSince(baseline, reading)
            val completed = CheckResearchCompletion(labRepository)(now = trustedNow)
            // ...
            TrackDailyLogin(dailyLoginRepository, playerRepository)
                .checkAndAward(today, todaySteps, profile0.seasonPassActive, profile0.seasonPassExpiry,
                    isRollback = verdict is TimeVerdict.Rollback)
```
(Adapt to the actual surrounding code; the point: trusted-now into research, rollback flag into login, NO persist.)

- [ ] **Step 2: LabsViewModel — inject AntiCheatPreferences + pass trusted-now to checkCompletion**

Add `private val antiCheatPrefs: AntiCheatPreferences` to its `@Inject constructor` + imports. Where it calls `checkCompletion()` (~line 58) and `CompleteResearch`, pass a trusted-now:
```kotlin
            val baseline = antiCheatPrefs.readTimeBaseline()
            val reading = antiCheatPrefs.currentTimeReading()
            val trustedNow = if (baseline == null) reading.wallClock
                else baseline.lastWallClock + TimeIntegrity.trustedElapsedSince(baseline, reading)
            val completed = checkCompletion(now = trustedNow)
```
For the manual `CompleteResearch` collect path, pass `now = trustedNow` similarly. READ-ONLY (no `writeTimeBaseline`).

> Helper to avoid duplication: if the trusted-now derivation appears 3+ times, extract a tiny private `fun AntiCheatPreferences.trustedNow(): Long` extension in a shared presentation util OR a method on the store — implementer's call; keep it DRY but don't over-engineer. (A `TimeBaselineStore.trustedNow()` convenience on AntiCheatPreferences is reasonable since the derivation is pure given a baseline+reading.)

- [ ] **Step 3: Build + run the VM tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "*HomeViewModelTest*" --tests "*LabsViewModelTest*" > /tmp/t7.log 2>&1; tail -25 /tmp/t7.log`
Expected: PASS. The VM test helpers now must construct the VMs with an `AntiCheatPreferences` — if the tests build these VMs directly, add the new ctor arg (a real `AntiCheatPreferences(RuntimeEnvironment.getApplication())` if the test is Robolectric, or confirm how the VM tests instantiate; if they're plain-JVM and can't get a real prefs, this is the one spot that may need the store behind a tiny interface — flag as a likely fix-up at implementation).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModel.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt
git commit -m "feat(research): Home/Labs pass trusted-now to research completion (read-only guard) (#211)"
```

> **Implementer note (flagged risk):** Step 3 is the integration seam most likely to need adjustment — if `HomeViewModelTest`/`LabsViewModelTest` are plain-JVM (no Robolectric) they can't construct a real `AntiCheatPreferences`. If so, the cleanest fix is a tiny domain interface (e.g. `TimeBaselineSource { fun readTimeBaseline(): TimeBaseline?; fun currentTimeReading(): TimeReading }`) that `AntiCheatPreferences` implements and a fake implements in tests — report DONE_WITH_CONCERNS and surface this rather than forcing Robolectric into those VM tests.

---

### Task 8: ADR + current-state docs sync

**Files:**
- Create: `docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md` (next free number)
- Modify: `docs/steering/security-model.md`, `CLAUDE.md`, `CHANGELOG.md`, `docs/steering/source-files.md`

- [ ] **Step 1: Run the full gate, record the test count**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t8.log 2>&1; tail -15 /tmp/t8.log`
Expected: BUILD SUCCESSFUL. Count: `rg -c "<testcase" app/build/test-results/testDebugUnitTest/*.xml | awk -F: '{s+=$2} END {print s}'`.

- [ ] **Step 2: Write the ADR**

Create `docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md` (use the next number after the highest existing ADR-00xx; check `ls docs/agent/DECISIONS/`). Content: the decision (monotonic anti-rollback guard + reboot-durable max-wall-clock floor in a pure `TimeIntegrity` core + AntiCheatPreferences baseline; single owner = DailyStepManager; refuse-credit-on-rollback; research trusted-now); the **scope boundary** (rooted/file-edit out of scope per the project threat model; reboot-spanning forward jump on research accepted; BillingManager season-pass authority unchanged); consequences; alternatives rejected (accept-and-document; full subsystem).

- [ ] **Step 3: security-model.md — add the time axis**

Add a "Time axis (#211)" subsection: the guard, what's closed (backward rollback reboot-durably; in-session forward jump on research), what's explicitly accepted (rooted file-edit; reboot-spanning forward jump on research), and the single-owner persistence model.

- [ ] **Step 4: CLAUDE.md + CHANGELOG + source-files**

CLAUDE.md: bump the headline test count; add one Conventions/anti-cheat line if warranted (time-gated mechanics consult `TimeIntegrity` via the `AntiCheatPreferences` baseline; DailyStepManager is the single baseline owner). CHANGELOG `[Unreleased]`: an entry for #211 + #258. source-files.md: new `domain/time/TimeIntegrity.kt` entry + the `AntiCheatPreferences` baseline-store note + the use-case guard notes.

- [ ] **Step 5: Commit docs**

```bash
git add docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md docs/steering/security-model.md CLAUDE.md CHANGELOG.md docs/steering/source-files.md
git commit -m "docs: ADR + sync for time-axis anti-cheat (#211) + schema note (#258)"
```

---

### Task 9: STATE.md + RUN_LOG.md

**Files:**
- Modify: `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`

- [ ] **Step 1: STATE.md** — rotate Current objective: new #211/#258 objective on top (what shipped, the guard, the accepted boundary, test-count delta), demote the prior (#234) to Previous. Update the headline count. Keep ~one page.

- [ ] **Step 2: RUN_LOG.md** — new dated entry: goal, the TimeIntegrity core + single-owner model, the two guards + accepted boundary, spec+plan through the Adversarial Review Gate (spec 22→20 surviving), verification, doc-sync list, ADR number, what remains.

- [ ] **Step 3: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(state): checkpoint time-axis anti-cheat (#211 #258)"
```

---

### Task 10: Final gate + push + PR

- [ ] **Step 1: Full gate**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t10.log 2>&1; tail -20 /tmp/t10.log`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Confirm the guards**

`DomainPurityTest` green (TimeIntegrity Android-free); `DataDeletionPrefsCoverageTest` green (the 3 new keys ride the existing `anti_cheat_prefs` clear).

- [ ] **Step 3: Push + PR**

```bash
git push -u origin fix/211-258-clock-tamper-schema-doc
gh pr create --title "fix: time-axis anti-cheat (#211) + schema-doc pre-v7 note (#258)" --body "<summary: TimeIntegrity guard, single-owner baseline, two closed exploits + accepted boundary, #258 doc note, Closes #211 #258>"
```

---

## Self-review checklist (run before execution)

- **Spec coverage:** §4.A `TimeIntegrity`→Task 2; §4.B store→Task 3; §4.C login-rollback→Task 4 + owner Task 6; §4.C research trusted-now→Task 5 + callers Task 7; §3.5 single-owner→Task 6 (persist) + Task 7 (read-only); §5 #258→Task 1; §6 tests→Tasks 2-7; ADR/security-model→Task 8. ✓
- **Domain purity:** TimeIntegrity Android-free (Task 2), asserted green Task 2 Step 4 + Task 10. ✓
- **Type consistency:** `TimeBaseline`/`TimeReading`/`TimeVerdict(.Trusted/.Rollback, .newBaseline)`/`evaluate`/`trustedElapsedSince` defined Task 2, used Tasks 3/6/7; `readTimeBaseline`/`writeTimeBaseline`/`currentTimeReading` defined Task 3, used Tasks 6/7; `TrackDailyLogin.checkAndAward(..., isRollback)` defined Task 4, called Tasks 6/7. ✓
- **Flagged open items** (plan-stage honest): (a) Task 6 — whether DailyStepManager tests mock `AntiCheatPreferences` (stub the 3 new methods if so); (b) Task 7 — whether Home/Labs VM tests can construct a real `AntiCheatPreferences` (plain-JVM → may need a tiny `TimeBaselineSource` interface + fake; report DONE_WITH_CONCERNS). These are the two integration seams the implementer confirms against the real test harness.
