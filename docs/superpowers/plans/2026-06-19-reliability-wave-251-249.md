# Reliability wave — #251 (gap-fill rate-clamp) + #249 (offline IAP swallowed) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop offline-recovered Health-Connect steps from being clamped to ~200 by the live-walking rate limiter (#251), and surface offline/failed Play-Billing purchase errors to the user instead of silently swallowing them (#249).

**Architecture:** Two independent, small fixes. #251 adds a `DailyStepManager.recordTrustedSteps()` batch-credit path that skips rate-limit + velocity (HC is an independently-validated source of truth) while keeping the 50k daily ceiling and the STEP_MULTIPLIER; `StepGapFiller` switches to it. #249 captures the discarded `PurchaseResult` in the three `StoreViewModel` purchase functions and routes `Error` messages through the already-wired `_userMessage` Snackbar, mirroring `CardsViewModel.watchFreePackAd`.

**Tech Stack:** Kotlin, coroutines/Flow, kotlinx-coroutines-test, JUnit Jupiter, Mockito-kotlin, Hilt. No schema change, no new domain types.

**Spec:** `docs/superpowers/specs/2026-06-19-reliability-wave-251-249.md` (adversarially reviewed; amendments applied).

---

## File structure

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt` | Modify | Add `recordTrustedSteps()` — trusted batch credit (skip anti-cheat, keep ceiling + multiplier). |
| `app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepGapFiller.kt` | Modify | Call `recordTrustedSteps` instead of `recordSteps`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt` | Modify | Hoist `antiCheatPrefs` to a field; add `recordTrustedSteps` tests. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModel.kt` | Modify | Capture `PurchaseResult` in the 3 purchase fns; set `_userMessage` on `Error`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModelTest.kt` | Modify | Assert error surfaces / pending surfaces / success quiet. |

Test command throughout (JVM, non-TTY safe): `./run-gradle.sh testDebugUnitTest`. To scope a single class: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.sensor.DailyStepManagerTest"`.

---

## Task 1: #251 — hoist the `antiCheatPrefs` mock so the new tests can verify it

**Files:**
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt:28-67`

This is a pure test-refactor with no behaviour change — do it first so Task 2's tests can `verify(antiCheatPrefs, …)`.

- [ ] **Step 1: Add the field declaration.**

In the `private lateinit var` block (currently `DailyStepManagerTest.kt:30-36`, ending with `private lateinit var manager: DailyStepManager`), add a line:

```kotlin
    private lateinit var antiCheatPrefs: com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
```

(There is no existing top-of-file import for `AntiCheatPreferences`; using the FQN here avoids touching the import block. If you prefer, add `import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences` with the other imports and use the short name.)

- [ ] **Step 2: Assign it in `setup()` and reference it in the constructor.**

In `setup()` (`DailyStepManagerTest.kt:44-66`), add the assignment alongside the other fakes (e.g. after `labRepo = FakeLabRepository()`):

```kotlin
        antiCheatPrefs = mock<AntiCheatPreferences>()
```

Then change the constructor argument from the inline anonymous mock:

```kotlin
            antiCheatPrefs = mock<AntiCheatPreferences>(),
```

to the held field:

```kotlin
            antiCheatPrefs = antiCheatPrefs,
```

(If you did not add the import in Step 1, write `antiCheatPrefs = mock<com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences>()` in Step 1's field assignment — the constructor line is just `antiCheatPrefs = antiCheatPrefs` either way.)

- [ ] **Step 3: Run the existing suite to confirm the refactor is behaviour-neutral.**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.sensor.DailyStepManagerTest"`
Expected: PASS (same count as before — this is a no-op refactor).

- [ ] **Step 4: Commit.**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt
git commit -m "test(#251): hoist antiCheatPrefs mock to a field for trusted-credit verification"
```

---

## Task 2: #251 — `recordTrustedSteps` (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt` (add method after `recordSteps`, before `recordActivityMinutes`, ~line 222)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt`

- [ ] **Step 1: Write the failing tests.**

Add these four tests to `DailyStepManagerTest` (near the other RO-08 multiplier tests, ~line 410). They use the existing fakes (`stepRepo`, `playerRepo`, `workshopRepo`) plus the `antiCheatPrefs` field from Task 1, and `org.mockito.kotlin.never` (add the import `import org.mockito.kotlin.never` and `import org.mockito.kotlin.any` if not present — `verify` and `mock` are already imported at lines 24-25).

```kotlin
    // ---- #251: recordTrustedSteps — HC-verified batch credit bypasses the rate limiter ----

    @Test
    fun `R251 trusted credit of a large gap is NOT clamped to the per-minute cap`() = runTest {
        // A 4,000-step offline recovery must credit far more than the 200/250 rate-limiter cap.
        manager.recordTrustedSteps(4_000, baseTime)

        assertEquals(
            4_000L,
            playerRepo.getStepBalance(),
            "trusted gap-fill must bypass the 200/min rate limiter (issue acceptance: > 250)",
        )
        assertTrue(playerRepo.getStepBalance() > 250L)
    }

    @Test
    fun `R251 trusted credit still respects the 50k daily ceiling`() = runTest {
        manager.recordTrustedSteps(60_000, baseTime)

        assertEquals(
            DailyStepManager.DAILY_CEILING,
            playerRepo.getStepBalance(),
            "trusted credit must clamp at the absolute 50k ceiling",
        )
    }

    @Test
    fun `R251 trusted credit applies the STEP_MULTIPLIER bonus`() = runTest {
        // L50 → asymptotic curve 1 - 0.95^50 ≈ 0.923. 100 trusted steps → 192 credited
        // (identical to recordSteps; see `V1X18 STEP_MULTIPLIER level 50` above).
        workshopRepo.upgrades.value =
            mapOf(com.whitefang.stepsofbabylon.domain.model.UpgradeType.STEP_MULTIPLIER to 50)

        manager.recordTrustedSteps(100, baseTime)

        assertEquals(
            192L,
            playerRepo.getStepBalance(),
            "trusted credit must apply the same STEP_MULTIPLIER as recordSteps",
        )
    }

    @Test
    fun `R251 trusted credit does NOT record an anti-cheat rate rejection`() = runTest {
        // recordSteps(4000) would reject ~3800 as rate-limited; recordTrustedSteps must reject none.
        manager.recordTrustedSteps(4_000, baseTime)

        verify(antiCheatPrefs, never()).incrementRateRejected(any())
    }
```

- [ ] **Step 2: Run the tests to verify they fail.**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.sensor.DailyStepManagerTest"`
Expected: FAIL — compilation error / "unresolved reference: recordTrustedSteps" (the method does not exist yet).

- [ ] **Step 3: Implement `recordTrustedSteps`.**

In `DailyStepManager.kt`, add this method immediately after `recordSteps` (after line 222, before `recordActivityMinutes`). It mirrors `recordSteps` but drops the rate-limit + velocity gates and the per-minute tracking:

```kotlin
    /**
     * Credits an HC-verified recovered gap (offline-recovery, #251). UNLIKE [recordSteps] this path
     * SKIPS rate-limit + velocity analysis: the total is independently bounded by Health Connect's
     * own daily aggregate (the caller derives the gap as `hcTotal - sensorTotal`, the same source
     * [com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator] already trusts), so
     * funnelling it through the live-walking 200/min limiter would clamp a legitimate multi-hour
     * recovery to a tiny fraction and discard the rest as a false anti-cheat rejection. The 50k
     * [DAILY_CEILING] and the STEP_MULTIPLIER bonus still apply (the cap stays absolute; gap steps
     * are legitimately-walked sensor steps credited identically to live crediting).
     *
     * Runs under the same non-reentrant [mutex] as [recordSteps] (calls [ensureInitializedLocked],
     * never [recordSteps] — that would self-deadlock). Persists the raw gap into [dailySensorTotal]
     * so the next [com.whitefang.stepsofbabylon.data.healthconnect.StepGapFiller.fillGaps] computes
     * `gap ≈ 0` (idempotent). Per-minute tracking ([stepsPerMinute]) is intentionally NOT updated —
     * a multi-minute elapsed window has no single true epoch minute and must not skew the
     * activity-minute overlap dedup.
     */
    suspend fun recordTrustedSteps(rawDelta: Long, timestampMs: Long) {
        if (rawDelta <= 0) return

        mutex.withLock {
            ensureInitializedLocked()

            val multiplied = applyStepMultiplier(rawDelta)

            val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal).coerceAtLeast(0)
            val credited = multiplied.coerceAtMost(remainingCeiling)
            if (credited <= 0) return

            dailySensorTotal += rawDelta
            dailyCreditedTotal += credited

            // Persist-with-sum FIRST, then increment the field (mirror recordSteps:208-209).
            stepRepository.updateDailySteps(currentDate, dailySensorTotal, dailySensorCredited + credited)
            dailySensorCredited += credited
            playerRepository.addSteps(credited)

            runFollowOnPipeline(timestampMs)
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass.**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.sensor.DailyStepManagerTest"`
Expected: PASS (all existing tests + the 4 new R251 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManagerTest.kt
git commit -m "fix(#251): add DailyStepManager.recordTrustedSteps — HC gap-fill bypasses rate limiter, keeps ceiling+multiplier"
```

---

## Task 3: #251 — point `StepGapFiller` at the trusted path

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepGapFiller.kt:24-26`

- [ ] **Step 1: Switch the call site.**

Change the body of `fillGaps`:

```kotlin
        val gap = hcTotal - sensorTotal
        if (gap > 0) {
            dailyStepManager.recordSteps(gap, System.currentTimeMillis())
        }
```

to:

```kotlin
        val gap = hcTotal - sensorTotal
        if (gap > 0) {
            // #251: recovered HC gaps are an already-validated batch over an elapsed window, not a
            // live sensor delta — credit them through the trusted path so the live-walking rate
            // limiter doesn't clamp a legitimate multi-hour recovery to ~200 steps.
            dailyStepManager.recordTrustedSteps(gap, System.currentTimeMillis())
        }
```

- [ ] **Step 2: Build to confirm it compiles (no dedicated StepGapFiller test exists).**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.sensor.DailyStepManagerTest"`
Expected: PASS (compilation includes `StepGapFiller`; behaviour covered by Task 2's seam tests). A full `assembleDebug` runs in Task 6.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepGapFiller.kt
git commit -m "fix(#251): StepGapFiller credits recovered gaps via the trusted batch path"
```

---

## Task 4: #249 — surface purchase errors (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModel.kt:95-117`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModelTest.kt`

- [ ] **Step 1: Write the failing tests.**

Add to `StoreViewModelTest` (after the existing tests, ~line 130). **Two imports are missing and MUST be added** (the file currently uses FQNs for these, the new tests use short names):

```kotlin
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
```

(Alternatively, write the new test bodies with fully-qualified names to match the file's existing convention — but adding the two imports is cleaner.) `assertNull` needs no import (covered by the wildcard `import org.junit.jupiter.api.Assertions.*` at line 18). The fake's `nextResult` knob already exists (`FakeBillingManager.kt:21`). Use the existing `createVm()` helper and the `backgroundScope.launch { vm.uiState.collect {} }` + `advanceUntilIdle()` idiom from the file.

```kotlin
    @Test
    fun `purchase error surfaces a user message`() = runTest(dispatcher) {
        billingManager.nextResult = PurchaseResult.Error("Network error")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseGemPack(BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()

        assertEquals("Network error", vm.uiState.value.userMessage)
    }

    @Test
    fun `pending purchase surfaces its message`() = runTest(dispatcher) {
        billingManager.nextResult =
            PurchaseResult.Error("Purchase pending — complete payment to receive your items")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseSeasonPass()
        advanceUntilIdle()

        assertEquals(
            "Purchase pending — complete payment to receive your items",
            vm.uiState.value.userMessage,
        )
    }

    @Test
    fun `successful purchase shows no message`() = runTest(dispatcher) {
        billingManager.nextResult = PurchaseResult.Success
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseAdRemoval()
        advanceUntilIdle()

        assertNull(vm.uiState.value.userMessage)
    }
```

Confirm `BillingProduct.GEM_PACK_SMALL` is a real entry — if the enum uses a different small-pack name, use any valid `BillingProduct` consumable entry (grep `enum class BillingProduct` / `BillingProduct.` usages). The `purchaseGemPack` signature takes a `BillingProduct` (`StoreViewModel.kt:95`).

- [ ] **Step 2: Run the tests to verify they fail.**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.store.StoreViewModelTest"`
Expected: FAIL — the two error tests fail with `userMessage` null (result is currently discarded). The success test passes incidentally (still informative).

- [ ] **Step 3: Implement — capture the result in all three purchase functions.**

In `StoreViewModel.kt`, replace the three purchase functions (lines 95-117). Add `import com.whitefang.stepsofbabylon.domain.model.PurchaseResult` to the imports if not present.

```kotlin
    fun purchaseGemPack(product: BillingProduct) {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val result = billingManager.purchase(product)
                if (result is PurchaseResult.Error) _userMessage.value = result.message
            } finally { _purchasing.value = false }
        }
    }

    fun purchaseAdRemoval() {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val result = billingManager.purchase(BillingProduct.AD_REMOVAL)
                if (result is PurchaseResult.Error) _userMessage.value = result.message
            } finally { _purchasing.value = false }
        }
    }

    fun purchaseSeasonPass() {
        if (_purchasing.value) return
        viewModelScope.launch {
            _purchasing.value = true
            try {
                val result = billingManager.purchase(BillingProduct.SEASON_PASS)
                if (result is PurchaseResult.Error) _userMessage.value = result.message
            } finally { _purchasing.value = false }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass.**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.store.StoreViewModelTest"`
Expected: PASS (all existing + 3 new).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModelTest.kt
git commit -m "fix(#249): surface offline/failed Play-Billing purchase errors via userMessage Snackbar"
```

---

## Task 5: Sync current-state docs

Per the PR Task-List Convention, sync docs the change affects (touch only what the change invalidates).

**Files:**
- Modify: `CHANGELOG.md` (add `[Unreleased]` entries for #251 + #249; bump the headline JVM test count if you track it there).
- Modify: `CLAUDE.md` — update the **headline test count** line in the Testing section (currently `1110 JVM tests + 9 instrumented`) to the new count (1110 + 7 new = **1117**, assuming 4 + 3 new tests; recount from the actual test run output, do not trust this arithmetic blindly).
- Consider: `docs/steering/source-files.md` — `DailyStepManager` gained a public method and `StoreViewModel` changed behaviour; update their entries only if the responsibility *shape* changed (it didn't materially — a one-line note is enough, or skip).

- [ ] **Step 1: Confirm the new test count from the run output.**

Run: `./run-gradle.sh testDebugUnitTest 2>&1 | tail -n 20`
Note the reported test total; compute new headline = old (1110) + new tests added (Task 2: 4, Task 4: 3 = 7) → expect 1117. If the number differs, use the actual number.

- [ ] **Step 2: Update CLAUDE.md headline test count.**

In `CLAUDE.md` Testing section, change `**Headline count: 1110 JVM tests + 9 instrumented tests.**` to the new count.

- [ ] **Step 3: Add CHANGELOG `[Unreleased]` entries.**

Under `## [Unreleased]`, add bullets:

```markdown
### Fixed
- **#251** Offline-recovery gap-fill no longer clamped by the live-walking rate limiter — HC-verified
  recovered steps credit through a new `DailyStepManager.recordTrustedSteps` batch path (skips
  rate-limit + velocity; keeps the 50k daily ceiling + STEP_MULTIPLIER). Raw-sensor catch-up in
  `StepSyncWorker` stays rate-limited (unvalidated source).
- **#249** Offline / failed Play-Billing purchases now surface a user-facing message (network error,
  cancelled, or "purchase pending") via the Store Snackbar — `StoreViewModel` previously discarded
  the `PurchaseResult`.
```

- [ ] **Step 4: Commit.**

```bash
git add CHANGELOG.md CLAUDE.md docs/steering/source-files.md
git commit -m "docs: changelog + test-count sync for reliability wave #251/#249"
```

(Drop `docs/steering/source-files.md` from the `git add` if you decided not to touch it.)

---

## Task 6: Full build verification + STATE/RUN_LOG update

- [ ] **Step 1: Run the full JVM suite + lint + assemble.**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/build-251-249.log 2>&1; tail -n 25 /tmp/build-251-249.log`
Expected: `BUILD SUCCESSFUL`, 0 test failures. If lint flags anything in the touched files, address it.

- [ ] **Step 2: Update `docs/agent/STATE.md`.**

Add a new "Current objective (DONE …)" block at the top of the Current objective list and a "Recently shipped" entry: reliability wave #251 + #249, `[Unreleased]`, no schema change, JVM count old→new, the two fixes summarised, and note the spec/plan went through a single-agent adversarial review (ultracode off). Add a fragile-zone note for `recordTrustedSteps` (trusted path skips anti-cheat but keeps ceiling+multiplier; sensor-catch-up stays rate-limited; per-minute tracking intentionally skipped).

- [ ] **Step 3: Append `docs/agent/RUN_LOG.md`.**

Append a dated entry describing the session: spec → single-agent review (b) → plan → subagent/inline execution, the two fixes, test deltas, build result.

- [ ] **Step 4: Commit.**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(checkpoint): reliability wave #251/#249 implemented ([Unreleased])"
```

(The `/checkpoint` skill can perform Task 5 + Task 6 doc writes if preferred — in that case run it instead of doing these by hand.)

---

## Self-review notes

- **Spec coverage:** #251 trusted path (Task 2) + caller switch (Task 3) + the 4 specified tests (large-gap-not-clamped, ceiling, multiplier, no-rate-rejection) ✓; idempotency is asserted indirectly via the `dailySensorTotal += rawDelta` persist (the spec's optional `StepGapFillerTest` is left out — the seam tests cover the behaviour, and no fake `HealthConnectStepReader` exists yet; flagged as optional in the spec). #249 all three purchase fns (Task 4) + error/pending/success tests ✓; `purchaseCosmetic` correctly untouched (already sets `_userMessage`). 
- **Out-of-scope guard:** `StepSyncWorker.sensorCatchUp` deliberately keeps `recordSteps` — no task changes it; called out in CHANGELOG.
- **Fragile zones:** #120 mutex (uses `ensureInitializedLocked`, never re-enters `recordSteps`), #232 pipeline seam (`runFollowOnPipeline` reused), #194/#122 (StoreViewModel flow + cosmetic gate untouched) all preserved.
- **Type consistency:** `recordTrustedSteps(rawDelta: Long, timestampMs: Long)` used identically in Task 2 (def), Task 3 (call). `PurchaseResult.Error(val message)` matches `BillingProduct.kt:33`.
