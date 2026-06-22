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
| `domain/time/TimeIntegrity.kt` | Create | pure core: `TimeBaseline`(4 slots)/`TimeReading`/`TimeVerdict` + `evaluate` (capped-accrual `trustedWallClock`) + `TimeBaselineSource` interface |
| `data/anticheat/AntiCheatPreferences.kt` | Modify | implements `TimeBaselineSource`; 4 new `Long` slots + `readTimeBaseline()`/`writeTimeBaseline()`/`currentTimeReading()` (the Android clock reads) |
| `di/TimeModule.kt` | Modify | `@Binds TimeBaselineSource` → `AntiCheatPreferences` |
| `app/.../fakes/FakeTimeBaselineSource.kt` | Create | test double for the VM tests |
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

Verify the floor-safety fact against the **dated internal-track rollout entries** in CHANGELOG (NOT a keyword grep — that's too narrow to confirm schema-at-first-ship). Run `rg -n "internal track|schema v|versionCode|Plan 31|C.5 PR 1" CHANGELOG.md | head -40` and confirm the documented sequence: schema reached **v9** (Phase C.5 PR 1, ~2026-05-11) BEFORE the first internal-track distribution (Plan 31 walk-through ~2026-05-13/14; v2/v3 rollout ~2026-05-15; "AAB v9 verified on internal track" ~2026-05-23). So the first distributed build was schema v9 (≥ v7). **STOP only if** a CHANGELOG entry shows a build at schema < v7 was distributed to a tester (the genuine refutation) — then it's a separate developer decision, not this doc fix.

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
    // 4-slot baseline helper (lastElapsed, lastWall, maxSeen, trustedWall)
    private fun base(elapsed: Long, wall: Long, max: Long, trusted: Long) =
        TimeBaseline(elapsed, wall, max, trusted)

    @Test
    fun `first run with null baseline is Trusted and seeds maxSeen and trustedWallClock`() {
        val v = TimeIntegrity.evaluate(null, reading(elapsed = 1000, wall = 5000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(5000, v.newBaseline.maxWallClockSeen)
        assertEquals(5000, v.newBaseline.trustedWallClock)   // seeded to wall
        assertEquals(5000, v.newBaseline.lastWallClock)
        assertEquals(1000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `normal forward advance is Trusted; trustedWallClock advances by the capped delta`() {
        val b = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 4000, wall = 8000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(8000, v.newBaseline.maxWallClockSeen)
        // wallDelta=3000, elapsedDelta=3000 → capped=3000 → trusted 5000+3000=8000
        assertEquals(8000, v.newBaseline.trustedWallClock)
        assertEquals(8000, v.newBaseline.lastWallClock)
        assertEquals(4000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `in-session forward JUMP advances trustedWallClock only by the elapsed delta (excess discarded)`() {
        val b = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        // wall leapt +30 min (1_800_000) but only 5 min (300_000) of monotonic time passed
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 301_000, wall = 1_805_000))
        assertTrue(v is TimeVerdict.Trusted)            // forward, not below floor
        // capped = min(1_800_000, 300_000) = 300_000 → trusted 5000+300_000 = 305_000 (NOT 1_805_000)
        assertEquals(305_000, v.newBaseline.trustedWallClock)
        assertEquals(1_805_000, v.newBaseline.maxWallClockSeen) // floor advances to the raw wall
    }

    @Test
    fun `backward jump below the floor is Rollback; trustedWallClock does not move backward`() {
        val b = base(elapsed = 1000, wall = 9000, max = 9000, trusted = 9000)
        // wall jumped BACK to 4000 (< maxSeen 9000) while elapsed advanced
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 2000, wall = 4000))
        assertTrue(v is TimeVerdict.Rollback)
        assertEquals(9000, v.newBaseline.maxWallClockSeen) // floor unchanged (max of prev, 4000)
        // capped = max(0, min(wallDelta=-5000, elapsedDelta=1000)) = 0 → trusted stays 9000
        assertEquals(9000, v.newBaseline.trustedWallClock)
        assertEquals(4000, v.newBaseline.lastWallClock)
        assertEquals(2000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `reboot (elapsed reset) with wall above floor is Trusted not false-rollback`() {
        val b = base(elapsed = 900_000, wall = 9000, max = 9000, trusted = 9000)
        // after reboot elapsedRealtime resets near 0; wall still >= floor
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 50, wall = 9500))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(9500, v.newBaseline.maxWallClockSeen)
        // reboot fallback (elapsedDelta<0) → capped = full wallDelta 500 → trusted 9000+500=9500 (accepted §2 gap)
        assertEquals(9500, v.newBaseline.trustedWallClock)
    }

    @Test
    fun `order-independence: advancing the baseline then re-deriving from it keeps the jump capped`() {
        // simulates: single owner advances on a forward-jump pass, THEN a read-only consumer
        // re-evaluates against the persisted (advanced) baseline with the same jumped reading.
        val b0 = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        val owner = TimeIntegrity.evaluate(b0, reading(elapsed = 301_000, wall = 1_805_000))
        assertEquals(305_000, owner.newBaseline.trustedWallClock)
        // consumer reads the advanced baseline + the same (still-jumped) reading → no further excess folds in
        val consumer = TimeIntegrity.evaluate(owner.newBaseline, reading(elapsed = 301_000, wall = 1_805_000))
        assertEquals(305_000, consumer.newBaseline.trustedWallClock) // unchanged — the jump never re-accepted
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

/** Persisted tamper baseline (FOUR Long slots in anti_cheat_prefs). */
data class TimeBaseline(
    val lastElapsedRealtime: Long,   // monotonic since-boot clock at last checkpoint
    val lastWallClock: Long,         // raw wall-clock at last checkpoint (to compute the next wallDelta)
    val maxWallClockSeen: Long,      // highest wall-clock ever observed — the reboot-durable rollback floor
    val trustedWallClock: Long,      // capped-accrual anchor — the trusted "now"; only ever advances by
                                     // min(wallDelta, elapsedDelta), so a forward jump's excess is never folded in
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
     * Update the baseline from a fresh reading and classify the time axis. `newBaseline.trustedWallClock`
     * IS the trusted "now" callers use to gate research.
     * - baseline == null (first run): Trusted; seed maxWallClockSeen = trustedWallClock = reading.wallClock.
     * - reading.wallClock < baseline.maxWallClockSeen: Rollback (backward jump — reboot-durable).
     * - else: Trusted.
     *
     * In ALL branches: lastWallClock/lastElapsedRealtime advance to "now"; maxWallClockSeen =
     * max(prev, reading.wallClock); trustedWallClock advances by the CAPPED delta (so an in-session
     * forward jump's excess is discarded). The capped-accrual anchor is order-independent — a read-only
     * consumer re-evaluating against an owner-advanced baseline still gets the capped value (it never
     * re-accepts the jump). Reboot (elapsedDelta < 0) → cappedDelta falls back to the full wallDelta
     * (accepted §2 forward-jump-across-reboot gap); the Rollback floor guards only the backward direction.
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict {
        if (baseline == null) {
            return TimeVerdict.Trusted(
                TimeBaseline(
                    lastElapsedRealtime = reading.elapsedRealtime,
                    lastWallClock = reading.wallClock,
                    maxWallClockSeen = reading.wallClock,
                    trustedWallClock = reading.wallClock,
                ),
            )
        }
        val wallDelta = reading.wallClock - baseline.lastWallClock
        val elapsedDelta = reading.elapsedRealtime - baseline.lastElapsedRealtime
        val cappedDelta =
            if (elapsedDelta < 0) wallDelta.coerceAtLeast(0)          // reboot: no monotonic cap available
            else minOf(wallDelta, elapsedDelta).coerceAtLeast(0)
        val advanced = TimeBaseline(
            lastElapsedRealtime = reading.elapsedRealtime,
            lastWallClock = reading.wallClock,
            maxWallClockSeen = maxOf(baseline.maxWallClockSeen, reading.wallClock),
            trustedWallClock = baseline.trustedWallClock + cappedDelta,
        )
        return if (reading.wallClock < baseline.maxWallClockSeen) {
            TimeVerdict.Rollback(advanced)
        } else {
            TimeVerdict.Trusted(advanced)
        }
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
    fun `writeTimeBaseline then readTimeBaseline round-trips all four slots`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        prefs.writeTimeBaseline(
            TimeBaseline(lastElapsedRealtime = 1234, lastWallClock = 5678, maxWallClockSeen = 9999, trustedWallClock = 7777),
        )
        val read = prefs.readTimeBaseline()
        assertEquals(TimeBaseline(1234, 5678, 9999, 7777), read)
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

First, add the **`TimeBaselineSource` interface** (pure domain — the test seam the VMs inject) to
`domain/time/TimeIntegrity.kt` (same file as the rest; Android-free):
```kotlin
/** Read-side seam over the persisted baseline + a fresh reading. AntiCheatPreferences implements it; a
 *  fake backs the plain-JVM VM tests (they can't construct a Context-backed AntiCheatPreferences). #211. */
interface TimeBaselineSource {
    fun readTimeBaseline(): TimeBaseline?
    fun currentTimeReading(): TimeReading
}
```
Then add the store + the Android clock reads to `AntiCheatPreferences.kt`. Add imports:
```kotlin
import android.os.SystemClock
import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import com.whitefang.stepsofbabylon.domain.time.TimeBaselineSource
import com.whitefang.stepsofbabylon.domain.time.TimeReading
```
Make the class implement the interface: `class AntiCheatPreferences @Inject constructor(...) : TimeBaselineSource {`.
Add to the `companion object` (alongside the existing keys) — note the **4th** slot:
```kotlin
        private const val KEY_TAMPER_LAST_ELAPSED = "tamper_last_elapsed"
        private const val KEY_TAMPER_LAST_WALL = "tamper_last_wall"
        private const val KEY_TAMPER_MAX_WALL = "tamper_max_wall"
        private const val KEY_TAMPER_TRUSTED_WALL = "tamper_trusted_wall"
        private const val KEY_TAMPER_SET = "tamper_baseline_set"
```
Add these methods to the class body (`override` since they satisfy `TimeBaselineSource`):
```kotlin
    /** #211: a fresh clock reading (the one Android-coupled time seam). */
    override fun currentTimeReading(): TimeReading =
        TimeReading(elapsedRealtime = SystemClock.elapsedRealtime(), wallClock = System.currentTimeMillis())

    /** #211: the persisted tamper baseline, or null until first written. */
    override fun readTimeBaseline(): TimeBaseline? {
        if (!prefs.getBoolean(KEY_TAMPER_SET, false)) return null
        return TimeBaseline(
            lastElapsedRealtime = prefs.getLong(KEY_TAMPER_LAST_ELAPSED, 0),
            lastWallClock = prefs.getLong(KEY_TAMPER_LAST_WALL, 0),
            maxWallClockSeen = prefs.getLong(KEY_TAMPER_MAX_WALL, 0),
            trustedWallClock = prefs.getLong(KEY_TAMPER_TRUSTED_WALL, 0),
        )
    }

    /** #211: persists the (advanced) tamper baseline. */
    fun writeTimeBaseline(baseline: TimeBaseline) {
        prefs.edit()
            .putLong(KEY_TAMPER_LAST_ELAPSED, baseline.lastElapsedRealtime)
            .putLong(KEY_TAMPER_LAST_WALL, baseline.lastWallClock)
            .putLong(KEY_TAMPER_MAX_WALL, baseline.maxWallClockSeen)
            .putLong(KEY_TAMPER_TRUSTED_WALL, baseline.trustedWallClock)
            .putBoolean(KEY_TAMPER_SET, true)
            .apply()
    }
```
(The 4 keys live in the existing `anti_cheat_prefs` file, so `DataDeletionManager`'s existing clear of that file wipes them — no `PREFS_NAMES` change. Confirm `DataDeletionPrefsCoverageTest` stays green in the full gate. `writeTimeBaseline` is NOT on the `TimeBaselineSource` interface — only the owner `DailyStepManager` and the concrete prefs write; read-only consumers use the interface.)

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

Add to `TrackDailyLoginTest.kt`. **Use the file's REAL fixtures — `loginRepo`, `playerRepo`, and the
already-built `useCase` field** (the `@BeforeEach` constructs `useCase = TrackDailyLogin(loginRepo, playerRepo)`;
do NOT re-`val vm = TrackDailyLogin(...)`). The existing tests pass `seasonPassExpiry = Long.MAX_VALUE` to
dodge the inline clock:
```kotlin
@Test
fun `R211 rollback verdict writes nothing for the tampered date`() = runTest {
    val gemsBefore = playerRepo.observeProfile().first().gems
    useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
    assertNull(loginRepo.getByDate("2026-03-09"))                       // no DailyLogin row
    assertEquals(gemsBefore, playerRepo.observeProfile().first().gems)  // no gems credited
}

@Test
fun `R211 trusted verdict credits normally (regression)`() = runTest {
    useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
    assertNotNull(loginRepo.getByDate("2026-03-09"))                    // row written + streak/gems advance
}

@Test
fun `R211 latched future clock then legit date is still credited`() = runTest {
    // a rolled-back pass for date D writes nothing (no stale gemsClaimed=true latched)...
    useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
    assertNull(loginRepo.getByDate("2026-03-09"))
    // ...so when D legitimately arrives the credit is NOT pre-suppressed
    useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
    assertNotNull(loginRepo.getByDate("2026-03-09"))
}
```
(Confirm the real fixture names at the top of the test file — they are `loginRepo`/`playerRepo`/`useCase`.
Add `assertNull`/`assertNotNull` from `org.junit.jupiter.api.Assertions` + `kotlinx.coroutines.flow.first` if absent.)

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
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletion.kt` (no signature change — the existing `now` param IS the seam; this is the LIVE guarded path)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/CompleteResearch.kt` (**CONTRACT-ONLY — no production caller**, see note)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/CheckResearchCompletionTest.kt`, `CompleteResearchTest.kt`

> The use cases ALREADY take `now: Long`. The guard is entirely in the CALLER: it passes the *trusted now*
> (the `trustedWallClock` anchor) instead of the raw `System.currentTimeMillis()` default. So the use-case
> CODE needs no logic change — the tests + a KDoc lock the contract that callers pass trusted-now.
>
> **`CompleteResearch` is dead production code (plan-review finding):** `rg "CompleteResearch\(" app/src/main`
> returns only its class definition — NO production caller (the real manual research-completion path is
> `RushResearch` + the season-pass `freeRush` calling `labRepository.completeResearch` directly, both
> §2-accepted out of scope; the rush-cost forward-jump is documented-accepted in the ADR). So
> `CompleteResearch`'s test + KDoc here are CONTRACT-ONLY (lock the seam for a currently-unused use case);
> do NOT hunt for a `CompleteResearch` caller to wire in Task 7. `CheckResearchCompletion` (auto-complete
> in Home/Labs init) is the genuinely live guarded path.

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
     * @param now the TRUSTED current time. Callers under #211 pass the monotonically-capped trusted-now
     *   (the `TimeIntegrity` `trustedWallClock` anchor), NOT the raw wall-clock, so an in-session forward
     *   clock jump can't complete research early. The default is the raw clock for tests / non-guarded
     *   call sites.
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
- Test (MANDATORY mock stub — not optional): `app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt`, `DailyStepManagerConcurrencyTest.kt`, `DailyStepManagerErrorReportingTest.kt`

`DailyStepManager` already injects `antiCheatPrefs: AntiCheatPreferences` (line 42) and calls `trackDailyLogin.checkAndAward(...)` (line ~329) inside the #120 mutex (`runFollowOnPipeline`, under `mutex.withLock`). This is the single owner.

> **MANDATORY mock-stub (plan-review CRITICAL):** these tests use `antiCheatPrefs = mock<AntiCheatPreferences>()` (DailyStepManagerTest.kt:55 + the process-restart manager at :207; ConcurrencyTest:56; ErrorReportingTest:61). An unstubbed mock returns `null` for the non-null `currentTimeReading(): TimeReading`, so `TimeIntegrity.evaluate(null, null)` NPEs. Even though that NPE lands in the `economy` try/catch and is swallowed (so some tests still pass), it silently routes a startup NPE through the error seam every credit — and the gem-delta tests (DailyStepManagerTest +11/+1 cases) WOULD regress. So stub it in every `@BeforeEach`/mock site (Step 3).

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

- [ ] **Step 3: Stub the AntiCheatPreferences mock in the 3 DailyStepManager test files (MANDATORY)**

In each test's `@BeforeEach`/mock-construction site (DailyStepManagerTest.kt:55 + :207; DailyStepManagerConcurrencyTest.kt:56; DailyStepManagerErrorReportingTest.kt:61), after the `mock<AntiCheatPreferences>()`, add (import `org.mockito.kotlin.whenever` + `com.whitefang.stepsofbabylon.domain.time.TimeReading`):
```kotlin
        whenever(antiCheatPrefs.currentTimeReading()).thenReturn(TimeReading(elapsedRealtime = 0, wallClock = baseTime))
        // readTimeBaseline() left unstubbed → returns null → evaluate hits the Trusted baseline==null
        // branch (no rollback, no behavior change). writeTimeBaseline(...) is a void no-op on a mock.
```
(Use the test's existing `baseTime`/fixed-clock value if one exists, else any positive Long. Mockito-inline already mocks this final class today, so the stub works.)

- [ ] **Step 4: Build + run the DailyStepManager tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "*DailyStepManager*" > /tmp/t6.log 2>&1; tail -25 /tmp/t6.log`
Expected: PASS — all three suites green (the stub makes `evaluate` hit the Trusted branch → `isRollback=false` → existing economy behavior unchanged; the +11/+1 gem-delta assertions hold).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerConcurrencyTest.kt app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerErrorReportingTest.kt
git commit -m "feat(steps): DailyStepManager owns the time baseline; passes rollback to login (#211)"
```

---

### Task 7: `HomeViewModel` / `LabsViewModel` — read-only consumers (trusted-now for research)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModelTest.kt` (ctor + `createVm` helper gains the source) + a new `FakeTimeBaselineSource`
- (`LabsViewModelTest` never constructs `LabsViewModel` — it tests use cases directly, per its line-42 comment — so the LabsViewModel ctor change is compile-safe for that test with NO edit needed.)

Both VMs are `@HiltViewModel`. They inject the **`TimeBaselineSource` interface** (Hilt binds it to `AntiCheatPreferences`; the VM tests are plain-JVM and can't build a `Context`-backed prefs, so the interface + a fake is the seam — committed, not optional). They are READ-ONLY: they derive the trusted-now / verdict from the current baseline but do **NOT** persist. The trusted-now is the **`trustedWallClock` anchor** straight off `evaluate(...).newBaseline` — order-independent, no `trustedElapsedSince`.

- [ ] **Step 0: Add the Hilt binding for TimeBaselineSource**

In `di/TimeModule.kt` (or the module that binds `AntiCheatPreferences`-adjacent types), add a `@Binds`:
```kotlin
    @Binds
    abstract fun bindTimeBaselineSource(impl: AntiCheatPreferences): TimeBaselineSource
```
(AntiCheatPreferences is `@Singleton @Inject` and now `: TimeBaselineSource` from Task 3 — bindable directly.)

- [ ] **Step 1: HomeViewModel — inject TimeBaselineSource + pass trusted-now / rollback (read-only)**

Add `private val timeBaselineSource: TimeBaselineSource` to the `@Inject constructor`. Imports: `com.whitefang.stepsofbabylon.domain.time.TimeBaselineSource` + `TimeIntegrity` + `TimeVerdict`. In `init` where it computes research completion + daily login (~lines 77/83):
```kotlin
            // #211 read-only: derive verdict + trusted-now from the CURRENT baseline; do NOT persist
            // (DailyStepManager owns the baseline). trustedWallClock is the capped-accrual anchor — a
            // forward jump's excess is never folded in, regardless of owner/consumer ordering.
            val verdict = TimeIntegrity.evaluate(
                timeBaselineSource.readTimeBaseline(), timeBaselineSource.currentTimeReading(),
            )
            val trustedNow = verdict.newBaseline.trustedWallClock
            val completed = CheckResearchCompletion(labRepository)(now = trustedNow)
            // ...
            TrackDailyLogin(dailyLoginRepository, playerRepository)
                .checkAndAward(today, todaySteps, profile0.seasonPassActive, profile0.seasonPassExpiry,
                    isRollback = verdict is TimeVerdict.Rollback)
```
(Adapt to the actual surrounding code; the point: `trustedWallClock` into research-`now`, rollback flag into login, NO `writeTimeBaseline`.)

- [ ] **Step 2: LabsViewModel — inject TimeBaselineSource + pass trusted-now to checkCompletion (read-only)**

Add `private val timeBaselineSource: TimeBaselineSource` to its `@Inject constructor` + imports. Where it calls `checkCompletion()` (~line 58):
```kotlin
            val verdict = TimeIntegrity.evaluate(
                timeBaselineSource.readTimeBaseline(), timeBaselineSource.currentTimeReading(),
            )
            val completed = checkCompletion(now = verdict.newBaseline.trustedWallClock)
```
READ-ONLY (no `writeTimeBaseline`). **Do NOT wire a `CompleteResearch` path — LabsViewModel has none** (its manual paths are `rushResearch`→`RushResearch` and `freeRush`→`labRepository.completeResearch` directly, both §2-accepted out of scope; the rush-cost forward-jump is documented-accepted in the ADR, Task 8).

> DRY: if the `evaluate→trustedWallClock` derivation appears in both VMs, it's a 3-line block reading the
> injected source — leave it inline (small, clear) or extract a `TimeBaselineSource.trustedNowAndVerdict()`
> default method on the interface. Implementer's call; don't over-engineer.

- [ ] **Step 3: Add `FakeTimeBaselineSource` + fix HomeViewModelTest's createVm (MANDATORY)**

`HomeViewModelTest` is plain-JVM JUnit Jupiter (no Robolectric) and its `createVm()` constructs `HomeViewModel` positionally with fakes — the new ctor param breaks it. Create `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeTimeBaselineSource.kt`:
```kotlin
package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import com.whitefang.stepsofbabylon.domain.time.TimeBaselineSource
import com.whitefang.stepsofbabylon.domain.time.TimeReading

/** Test double for [TimeBaselineSource]. Default: null baseline + a fixed reading → evaluate() returns
 *  Trusted with trustedWallClock == the reading's wall (no rollback, no jump). Tests can set [baseline]
 *  / [reading] to drive rollback / forward-jump cases. */
class FakeTimeBaselineSource(
    var baseline: TimeBaseline? = null,
    var reading: TimeReading = TimeReading(elapsedRealtime = 0, wallClock = 0),
) : TimeBaselineSource {
    override fun readTimeBaseline(): TimeBaseline? = baseline
    override fun currentTimeReading(): TimeReading = reading
}
```
Update `HomeViewModelTest`'s `createVm()` helper to pass `FakeTimeBaselineSource()` for the new param. (The default null-baseline → Trusted → `trustedNow = reading.wallClock`; the existing #55 background-research-completion test still completes research because the fake's reading.wallClock can be set ≥ completesAt — set `reading = TimeReading(0, <large wall>)` in that test's fake if its completesAt fixture requires it. Confirm the #55 test's expected completion still holds with the trusted-now value.) **LabsViewModelTest needs NO change** (it never constructs LabsViewModel).

- [ ] **Step 4: Build + run the VM tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "*HomeViewModelTest*" --tests "*LabsViewModelTest*" > /tmp/t7.log 2>&1; tail -25 /tmp/t7.log`
Expected: PASS (HomeViewModelTest with the fake-injected source; LabsViewModelTest unchanged/green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModel.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt app/src/main/java/com/whitefang/stepsofbabylon/di/TimeModule.kt app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeTimeBaselineSource.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModelTest.kt
git commit -m "feat(research): Home/Labs pass trusted-now to research completion via TimeBaselineSource (read-only) (#211)"
```

> **Resolved (was a flagged risk; the plan-review confirmed it):** `HomeViewModelTest` IS plain-JVM and
> would break on a concrete-`AntiCheatPreferences` ctor param — so the `TimeBaselineSource` interface +
> `FakeTimeBaselineSource` (Step 3) is a COMMITTED part of this task, not a maybe. `LabsViewModelTest`
> never constructs `LabsViewModel` (tests use cases directly), so its ctor change needs no test edit.

---

### Task 8: ADR + current-state docs sync

**Files:**
- Create: `docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md` (next free number)
- Modify: `docs/steering/security-model.md`, `CLAUDE.md`, `CHANGELOG.md`, `docs/steering/source-files.md`

- [ ] **Step 1: Run the full gate, record the test count**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t8.log 2>&1; tail -15 /tmp/t8.log`
Expected: BUILD SUCCESSFUL. Count: `rg -c "<testcase" app/build/test-results/testDebugUnitTest/*.xml | awk -F: '{s+=$2} END {print s}'`.

- [ ] **Step 2: Write the ADR**

Create `docs/agent/DECISIONS/ADR-00NN-time-axis-anticheat.md` (use the next number after the highest existing ADR-00xx; check `ls docs/agent/DECISIONS/`). Content: the decision (monotonic anti-rollback guard + reboot-durable max-wall-clock floor + the order-independent **`trustedWallClock` capped-accrual anchor** in a pure `TimeIntegrity` core + AntiCheatPreferences baseline via the `TimeBaselineSource` seam; single owner = DailyStepManager persists, VMs read-only; refuse-credit-on-rollback; research trusted-now); the **scope boundary** (rooted/file-edit out of scope per the project threat model; reboot-spanning forward jump on research accepted; **`RushResearch` rush-cost forward-jump accepted** — the auto-complete path is guarded but the gem rush-cost reads raw `now`; `freeRush` direct-completion accepted; BillingManager season-pass authority unchanged); consequences; alternatives rejected (accept-and-document; full subsystem).

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
- **Type consistency:** `TimeBaseline`(4 slots incl. `trustedWallClock`)/`TimeReading`/`TimeVerdict(.Trusted/.Rollback, .newBaseline)`/`evaluate` (capped-accrual, NO `trustedElapsedSince`) + `TimeBaselineSource` defined Task 2/3, used Tasks 6/7; `readTimeBaseline`/`currentTimeReading` (interface) + `writeTimeBaseline` (owner-only) defined Task 3, used Tasks 6/7; consumers read `verdict.newBaseline.trustedWallClock` as trusted-now; `TrackDailyLogin.checkAndAward(..., isRollback)` defined Task 4, called Tasks 6/7. ✓
- **Plan-review CRITICALs resolved (not deferred):** (a) the anchoring race → `trustedWallClock` capped-accrual anchor, order-independent (Task 2); (b) DailyStepManager mock NPE → mandatory stub (Task 6 Step 3); (c) HomeViewModelTest ctor break → `TimeBaselineSource` + `FakeTimeBaselineSource` committed (Task 7 Step 3); (d) dead `CompleteResearch` wiring removed + RushResearch rush-cost documented-accepted (Task 5/ADR); (e) #258 verification re-pointed at dated rollout entries (Task 1). ✓
