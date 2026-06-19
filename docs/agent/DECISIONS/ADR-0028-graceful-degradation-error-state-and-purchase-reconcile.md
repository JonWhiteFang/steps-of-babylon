# ADR-0028: Graceful degradation — shared screen error state (#194) and offline-purchase reconcile triggers (#250)

**Status:** Accepted — 2026-06-19 (shipped on branch `fix/graceful-degradation-194-250`, `[Unreleased]`).

## Context

Two confirmed audit defects in the "graceful degradation" theme:

- **#194 (UX-1)** — no error state anywhere. All 11 data-backed screens used
  `if (state.isLoading) { LoadingBox(); return }`; no `UiState` carried an error field and no VM had a
  `.catch`. A throwing source flow (Room/SQLCipher decrypt/migration edge) made `combine` complete
  exceptionally; `stateIn` then retained the initial `isLoading = true` → an infinite spinner with no
  message and no recovery short of force-quit. (This issue had been closed 2026-06-17 with no
  implementing commit; reconciling against HEAD on 2026-06-19 confirmed it was never fixed, and it was
  re-opened.)
- **#250** — pending/offline-completed Play Billing purchases only reconciled on Store open. Play
  auto-refunds purchases not acknowledged within 3 days; the sole `reconcilePendingPurchases()` trigger
  was `StoreViewModel.init`, so an entitlement bought on a flaky connection whose `acknowledge()` RPC
  failed would be refunded unless the user happened to re-open the Store within 3 days.

## Decision

### #194 — additive `error` field + retry-re-subscribe + shared `ErrorState`, NOT a sealed state

- New shared `presentation/ui/ErrorState.kt` (icon + message + Retry button) sibling to
  `LoadingBox`/`EmptyState`, plus a single generic `SCREEN_LOAD_ERROR` message constant.
- `error: String? = null` added to the 10 affected UiStates. **Battle is excluded** — it polls the
  engine via `LaunchedEffect`, uses `collectAsState()`, and already owns `battleError` + a
  `BattleErrorOverlay` (#190).
- Each VM gains `private val _retry = MutableStateFlow(0)` and wraps its data flow in
  `_retry.flatMapLatest { <combine/map>.catch { emit(<UiState>(isLoading=false, error=SCREEN_LOAD_ERROR)) } }`,
  plus `fun retry() { _retry.value++ }`. The three date-based VMs (Home/Missions/Stats) fold `_retry`
  into their existing `flatMapLatest` via `combine(_date, _retry) { d, _ -> d }`.
- Screens early-return `ErrorState(state.error!!, onRetry = viewModel::retry)` immediately before the
  existing loading early-return.

**Chosen over a sealed `ScreenState<T>`** because the established convention is a flat data-class
UiState with `isLoading: Boolean` + many fields (often `userMessage`/`isProcessing`); a sealed type
would force every screen body, every `uiState.value.field` access, and every test to unwrap a Success
branch — a large invasive rewrite for no extra safety.

**Critical correctness point (from the adversarial review): the `.catch` MUST live INSIDE
`flatMapLatest`, not downstream of it.** A downstream `.catch` completes the merged stream on the first
throw, so a later `_retry.value++` has nothing collecting it → `retry()` becomes a no-op and the screen
is stuck on the error forever (the inverse of the original bug). Catch-inside gives each re-subscribe a
fresh terminal boundary. Pinned by `StatsViewModelTest` (throw → error; `retry()` → recover).

`state.error` is read via a `by`-delegated property, so it can't smart-cast non-null — screens use
`state.error!!` inside the `if (state.error != null)` guard.

### #250 — two triggers via a shared, time-bounded best-effort helper

`reconcilePendingPurchases()` is already idempotent, `sessionMutex`-serialised, `ensureConnected()`-guarded,
and Activity-independent (the `BillingClient` is built from `@ApplicationContext`; only
`launchBillingFlow` needs an Activity). Added two triggers, both routed through one helper:

- **`MainActivity.onResume`** — foreground reconcile on every resume (cheap; a no-network resume is
  nearly free behind the connect-guard).
- **`StepSyncWorker.doWork`** — background safety net on the existing 15-min periodic worker, for users
  who never re-foreground the app within the 3-day window. `BillingManager` injected into the
  `@HiltWorker` (`@Singleton` non-assisted ctor dep).

**Both call `reconcileBillingSafely(billingManager)`**, a top-level `suspend fun` that wraps the call in
`withTimeoutOrNull(20s)` + a catch-all. The timeout is load-bearing (from the review):
`BillingManagerImpl.connect()` is a `suspendCancellableCoroutine` whose disconnect callback never
resumes, so on a stalled Play Services / offline device it has **no internal timeout** and would
otherwise hang the worker to WorkManager's execution cap (and leak a coroutine on the Activity's
`activityScope`). Extracting the helper as a top-level fun (rather than a worker method) keeps it
cheaply JVM-unit-testable with a fake `BillingManager` — the full worker has 11 injected deps + needs a
Robolectric Context.

## Alternatives rejected

- **Sealed `ScreenState<T>` for #194** — invasive across 11 screens + all tests; fights the convention.
- **A bare `.catch` without a retry trigger** — makes the error terminal (no recovery).
- **`.catch` downstream of `flatMapLatest`** — breaks retry (see above).
- **A `ProcessLifecycleOwner` app-wide resume observer for #250** — `lifecycle-process` is not on the
  classpath; `MainActivity.onResume` is the existing process-level resume surface.
- **Reconcile from a one-shot `NetworkType.CONNECTED` WorkManager job** — the periodic `StepSyncWorker`
  already runs background work; piggybacking avoids a second worker. (A connectivity-constrained one-shot
  remains a possible future enhancement.)

## Consequences

- A throwing screen data-flow now shows a recoverable error instead of an infinite spinner; retry
  re-subscribes the sources.
- Pending/offline purchases reconcile on resume and every 15 min, closing the 3-day auto-refund gap
  without requiring a Store visit.
- New fragile-zone surface (see STATE.md): the per-VM `_retry`/`flatMapLatest`/`.catch` shape (catch
  MUST stay inside flatMapLatest) and the shared `reconcileBillingSafely` timeout/best-effort contract.
- No schema change. 1093 → 1098 JVM tests. VM-/helper-level tests only; the `ErrorState` rendering and
  the `MainActivity.onResume` wiring are verified by build + on-device (no Compose-UI test harness in
  the repo — a documented coverage boundary).
