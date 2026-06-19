# Reliability wave — #251 (offline gap-fill rate-clamp) + #249 (offline IAP swallowed)

**Date:** 2026-06-19
**Issues:** #251 (severity:major, confirmed), #249 (severity:major, confirmed)
**Source:** `docs/reviews/2026-06-18-complete-app-review.md`
**Scope:** before-public reliability. No schema change; no migration; no engine/economy-formula
change beyond the gap-fill *crediting path*. TDD (RED→GREEN per fix).

---

## #251 — Offline-recovery gap-fill is silently clamped to ~200 steps/min

### Problem (grounded)

`StepGapFiller.fillGaps` (`data/healthconnect/StepGapFiller.kt:18-27`) recovers steps the device
counted while the foreground service was dead:

```kotlin
val gap = hcTotal - sensorTotal
if (gap > 0) {
    dailyStepManager.recordSteps(gap, System.currentTimeMillis())   // ← single-instant credit
}
```

`DailyStepManager.recordSteps` (`data/sensor/DailyStepManager.kt:171-222`) funnels every credit
through `StepRateLimiter.credit` (`data/sensor/StepRateLimiter.kt`), capped at **200/min** (250 in a
<5-min burst window). So a large, legitimate, HC-verified recovered total (e.g. 4,000 steps walked
over two hours while the service was killed) is credited at ~200 in one shot; the remaining ~3,800
are counted as anti-cheat **rate-rejected** (`antiCheatPrefs.incrementRateRejected`) and permanently
lost.

The anti-cheat intent (block a sensor being fed fast — shaker/spoof) is valid, but it is the wrong
gate for a **batch recovery of an already-elapsed period** whose total is independently bounded by
Health Connect's own daily aggregate. `StepCrossValidator` already treats HC as the trusted
reconciliation source of truth, so a gap derived from `hcTotal - sensorTotal` is, by construction,
already validated.

### Fix — a trusted batch-credit path

Add a new entry point to `DailyStepManager`:

```kotlin
/**
 * Credits an HC-verified recovered gap (offline-recovery, #251). UNLIKE [recordSteps] this path
 * SKIPS rate-limit + velocity analysis — the total is independently bounded by Health Connect's
 * own daily aggregate (the gap is `hcTotal - sensorTotal`), the same source StepCrossValidator
 * already trusts — so funnelling it through the live-walking 200/min limiter would clamp a
 * legitimate multi-hour recovery to a tiny fraction. The 50k DAILY_CEILING and STEP_MULTIPLIER
 * still apply (the cap stays absolute; gap steps are legitimately-walked sensor steps).
 */
suspend fun recordTrustedSteps(rawDelta: Long, timestampMs: Long)
```

Behaviour (all under the existing `#120` `mutex`, sharing the ceiling RMW with
`recordSteps`/`recordActivityMinutes`):

1. `if (rawDelta <= 0) return`.
2. `mutex.withLock { ensureInitializedLocked() ... }`.
3. **Skip** `rateLimiter.credit` and `velocityAnalyzer.analyze` entirely — no anti-cheat counters
   touched (this is not a rejection; it is a trusted source).
4. **Apply** `applyStepMultiplier(rawDelta)` — gap steps are legitimately-walked sensor steps;
   crediting them identically to what live crediting would have granted (had the service been alive)
   is correct, and keeps the STEP_MULTIPLIER / STEP_EFFICIENCY economy intact.
5. **Keep** the 50k ceiling: `credited = multiplied.coerceAtMost(remainingCeiling)`;
   `if (credited <= 0) return`.
6. Persist as **sensor** steps: `dailySensorTotal += rawDelta`;
   `stepRepository.updateDailySteps(currentDate, dailySensorTotal, dailySensorCredited + credited)`;
   `dailySensorCredited += credited`; `dailyCreditedTotal += credited`;
   `playerRepository.addSteps(credited)`.
   - **Idempotency invariant:** because the raw gap increments `dailySensorTotal`, the *next*
     `fillGaps` run computes `gap = hcTotal - sensorTotal ≈ 0` and credits nothing. This mirrors the
     existing `recordSteps` accounting (`dailySensorTotal += rawDelta`) so the gap-fill stays
     idempotent across worker runs.
7. **Skip** per-minute tracking (`stepsPerMinute[...]`). The recovered total spans a multi-minute
   elapsed window; attributing it to a single `timestampMs / 60_000` epoch minute would distort the
   activity-minute overlap-dedup that `ActivityMinuteConverter` performs against
   `getSensorStepsPerMinute()`. A trusted batch has no single true minute — omitting it is the safe,
   conservative choice (no overlap credit is claimed against it).
8. Run `runFollowOnPipeline(timestampMs)` (widget / supply-drop / economy / mission) — same as the
   other two ingestion paths, so milestones/missions/widgets reflect the recovered steps.

`StepGapFiller.fillGaps` changes its single call site:

```kotlin
if (gap > 0) {
    dailyStepManager.recordTrustedSteps(gap, System.currentTimeMillis())
}
```

### Deliberately OUT of scope (conscious non-change)

`StepSyncWorker.sensorCatchUp` (`service/StepSyncWorker.kt:100`) also batch-credits via
`recordSteps(decision.gap, now)`. That gap is computed from the **raw hardware step counter**
(`computeCatchUp`), NOT from an HC-verified total — it is exactly the kind of unvalidated sensor
delta the rate limiter + velocity analyzer exist to guard. It **keeps** the rate-limited
`recordSteps` path. Only the HC-verified `StepGapFiller` path becomes trusted. This boundary is the
whole point of the fix: "HC-verified batch" vs "raw sensor delta" get different trust.

### Tests (#251)

New `app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/` coverage (extend
`DailyStepManagerTest`, or a focused new test) asserting, against the real production arithmetic:

- **Large recovered gap is not clamped:** `recordTrustedSteps(4_000, t)` with an empty day credits
  **> 250** (the issue's explicit acceptance criterion) — in fact 4,000 (no multiplier configured).
- **Ceiling still absolute:** `recordTrustedSteps(60_000, t)` credits exactly the remaining headroom
  up to `DAILY_CEILING` (50,000 on an empty day), not more.
- **STEP_MULTIPLIER applies:** with a STEP_MULTIPLIER level set on the fake workshop repo, a trusted
  credit is inflated by the same bonus `recordSteps` would apply (asserts the shared
  `applyStepMultiplier`).
- **Rate-limiter untouched:** a trusted credit does NOT increment the rate-rejected anti-cheat
  counter (contrast with `recordSteps`, which does for the same over-cap delta).
- **Idempotent gap-fill (if a `StepGapFiller` test is added):** with a fake `HealthConnectStepReader`
  returning a fixed `hcTotal`, two successive `fillGaps` calls credit the gap once (second run sees
  `gap ≈ 0`).

(`StepGapFiller` itself has no existing test; a thin `StepGapFillerTest` with fakes for
`HealthConnectStepReader` / `StepRepository` and a real-or-fake `DailyStepManager` is optional but
welcome. The behaviour-critical assertions live at the `DailyStepManager.recordTrustedSteps` seam.)

---

## #249 — Offline IAP failures are silently swallowed

### Problem (grounded)

`StoreViewModel` (`presentation/store/StoreViewModel.kt:95-117`) has three purchase entry points that
**discard** the `PurchaseResult`:

```kotlin
fun purchaseGemPack(product: BillingProduct) {
    if (_purchasing.value) return
    viewModelScope.launch {
        _purchasing.value = true
        try { billingManager.purchase(product) } finally { _purchasing.value = false }  // result dropped
    }
}
// purchaseAdRemoval / purchaseSeasonPass — identical shape
```

`BillingManagerImpl.purchase` already returns rich, user-facing `PurchaseResult.Error` messages,
including a **distinct PENDING string** ("Purchase pending — complete payment to receive your items",
`data/billing/BillingManagerImpl.kt:193`), connection errors (`connect.toUserMessage()`), and a
cancel message. `StoreScreen` already renders a Snackbar bound to `state.userMessage`
(`presentation/store/StoreScreen.kt:56`, with `viewModel.clearMessage()`). The wire is simply cut at
the ViewModel — so on a flaky/absent network the spinner clears and **nothing** is shown.

This is an inconsistency, not a platform limitation: the ad path
(`CardsViewModel.watchFreePackAd`, `presentation/cards/CardsViewModel.kt:138-157`) already surfaces
`AdResult.Error` via `_userMessage`.

### Fix

Capture the result in each of the three purchase functions and surface a hard error; mirror
`CardsViewModel.watchFreePackAd`:

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
// same change in purchaseAdRemoval() and purchaseSeasonPass()
```

Notes:
- `PurchaseResult.Success` sets no message — the grant lands via the receipt-table reconcile + the
  profile `StateFlow`, so the balance simply updates. (No "Success!" toast — out of scope; not asked
  for.)
- The PENDING-vs-hard-network distinction is **already carried in `result.message`** (the impl emits
  the dedicated pending copy), so no new `PurchaseResult` variant or branching is needed at the VM.
  The user sees the pending message directly.
- `result.message` may be the "Purchase cancelled" string on a user-cancel. Surfacing it is
  acceptable and matches `CardsViewModel`'s "Ad cancelled. Try again." behaviour (a brief Snackbar on
  cancel is fine, not a regression). If we want to suppress the cancel case we could special-case it,
  but the issue + the Cards precedent both favour surfacing it — keep it simple, surface all errors.

### Tests (#249)

Extend `StoreViewModelTest`
(`app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModelTest.kt`), which
already has a `FakeBillingManager` with a `nextResult` knob:

- **Error surfaces:** `billingManager.nextResult = PurchaseResult.Error("Network error")`;
  `vm.purchaseGemPack(...)`; `advanceUntilIdle()` → `uiState.value.userMessage == "Network error"`.
- **Pending surfaces:** same with the pending message → `userMessage` equals it.
- **Success is quiet:** `nextResult = PurchaseResult.Success`; `userMessage` stays null.
- (Optionally repeat for `purchaseAdRemoval` / `purchaseSeasonPass` — at least one of the three to
  prove the shape, the others are identical.)

---

## Combined scope / non-goals

- **No schema change, no migration.** No Room entity, DAO, or DI change.
- **No new domain types.** `recordTrustedSteps` is a new `DailyStepManager` method; `#249` reuses the
  existing `PurchaseResult` + `_userMessage` plumbing.
- **No engine/balance change.** The only economy-touching change is the gap-fill *crediting path*
  (#251), which now credits *more* of an already-legitimate recovered total (closer to the player's
  real activity), still under the absolute 50k ceiling.
- **Adversarial Review Gate:** ultracode is OFF this session. Per CLAUDE.md, the spec and plan are
  flagged **unreviewed** — the developer chooses (a) turn ultracode on for the review, (b) a lighter
  single-agent inline review, or (c) proceed without one — before implementation begins.
- **Fragile-zone interactions:** touches `DailyStepManager` (the `#120` mutex, `#232` pipeline-error
  seam) — `recordTrustedSteps` MUST run its full body under the same non-reentrant `mutex` and call
  `ensureInitializedLocked()` (never `recordSteps`, which would self-deadlock on the non-reentrant
  lock). Touches `StoreViewModel` (`#194` error-state pattern + `#122` cosmetic-spend gate) — the new
  code only reads `purchase()`'s result and sets `_userMessage`; it does not alter the `#194`
  `flatMapLatest`/`.catch` flow or the `#122` `spendGems` gate.
