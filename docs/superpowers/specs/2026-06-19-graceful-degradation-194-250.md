# Spec + Plan — Graceful degradation: #194 shared error state · #250 offline-purchase reconcile

**Date:** 2026-06-19 · **Branch:** `fix/graceful-degradation-194-250` · **One combined PR.**
**Review depth:** lighter inline single-agent adversarial review (ultracode off, per the developer's standing choice for this batch).
**Discipline:** TDD — RED→GREEN per fix; no schema change; reuse existing patterns.

Two confirmed audit defects, both "graceful degradation" themed. #194 was closed prematurely
(2026-06-17, no implementing commit) and **re-opened 2026-06-19** after reconciling against HEAD
(no `.catch`, no error field, bare `LoadingBox` still present).

---

## Fix 1 — #194: no error state anywhere (UX-1)

### Problem (code-grounded)
All 11 data-backed screens use `if (state.isLoading) { LoadingBox(); return }`. No `UiState` carries
an error field; `isLoading` flips false only inside the success branch of `combine`. If a source
Room/SQLCipher flow throws, `combine` completes exceptionally, `stateIn` stops collecting and retains
the initial `isLoading=true` → **infinite spinner, no message, no retry**. `LoadingBox.kt` is a bare
`CircularProgressIndicator`.

### The 11 screens (Battle excluded — see below)
Home, Workshop, Store, Labs, Cards, Missions, Stats, UltimateWeapon (Weapons), UnclaimedSupplies,
CurrencyDashboard (Economy). All build `combine(...)`/`.map`/`flatMapLatest` → `.stateIn(scope,
WhileSubscribed(5000), <UiState>())` with `isLoading: Boolean = true` default. **Battle is excluded:**
it polls the engine via `LaunchedEffect(isLoading)`, uses `collectAsState()`, and already owns
`BattleUiState.battleError` + `BattleErrorOverlay` (#190). Settings/Help/Onboarding are not data-load-gated.

### Design — additive `error` field + `.catch` + retry re-subscribe + shared `ErrorState`
Chosen over a sealed `ScreenState<T>` because the established convention is a flat data-class UiState
with `isLoading: Boolean` + many fields (often `userMessage`/`isProcessing`); a sealed type would force
every screen body + every test + every `uiState.value.field` access to unwrap a `Success` branch — a
large invasive rewrite for no extra safety.

**Per UiState (10 — Battle's `BattleUiState` is untouched):** add `val error: String? = null`.

**Per ViewModel:** add a retry trigger + catch. A bare `.catch` makes the error *terminal* (a StateFlow
that has caught can't re-subscribe its sources), so retry needs a `flatMapLatest` re-subscribe.
**CRITICAL (review finding 1+2): the `.catch` MUST live INSIDE the `flatMapLatest` block, not after it.**
If `.catch` is downstream of `flatMapLatest`, the first throw completes the merged stream and a later
`_retry.value++` has nothing collecting it → `retry()` is a no-op and the screen is stuck on the error
forever (inverse of today's bug). Catch inside ⇒ each re-subscribe gets a fresh terminal boundary:
```kotlin
private val _retry = MutableStateFlow(0)
// plain-combine VMs (Workshop/Store/Cards/Labs/UltimateWeapon/Economy/Supplies):
val uiState = _retry.flatMapLatest {
    combine(...) { ... isLoading = false ... }
        .catch { emit(<UiState>(isLoading = false, error = LOAD_ERROR)) }   // INSIDE flatMapLatest
}.stateIn(viewModelScope, WhileSubscribed(5000), <UiState>())
// date-based VMs already using flatMapLatest (Home uses _currentDate; Missions/Stats use _today):
// fold _retry into the existing single-source flatMapLatest via combine, catch still INSIDE:
val uiState = combine(_currentDate /*or _today*/, _retry) { day, _ -> day }
    .flatMapLatest { day ->
        combine(...) { ... }
            .catch { emit(<UiState>(isLoading = false, error = LOAD_ERROR)) }   // INSIDE flatMapLatest
    }.stateIn(...)
fun retry() { _retry.value++ }
```
`@OptIn(ExperimentalCoroutinesApi::class)` where flatMapLatest is newly added (Home/Missions/Stats
already have it; the rest need the import + annotation). Error message is a single generic constant
(`"Couldn't load this screen. Check your connection and try again."`) — no per-VM message logic.
`.catch` is orthogonal to the transient `userMessage`/`celebration` channels (those stay).

**Shared composable** `presentation/ui/ErrorState.kt` (sibling to `LoadingBox`/`EmptyState`):
```kotlin
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) { /* centered
    column: icon + message + a "Retry" Button(onRetry) */ }
```

**Per screen:** add an error early-return immediately beside the existing loading one — the minimal,
uniform diff (no restructuring the body into a host lambda). **Use the `if`/`return` form, not
`?.let { return }`** (review finding 4: `return` in an inline-`let` lambda is a non-local return that
*does* work, but it's a footgun and inconsistent with the adjacent loading line; `if` also smart-casts
`state.error` non-null):
```kotlin
if (state.error != null) { ErrorState(state.error, onRetry = viewModel::retry); return }
if (state.isLoading) { LoadingBox(); return }
```
(Cards `:125` / Supplies `:64` keep their separate empty-state checks — empty ≠ error.)

### Tests (TDD, RED first)
- For 2-3 representative VMs (Home [flatMapLatest], Workshop [plain combine], one more), add a test:
  a fake repository whose observed flow `flow { throw RuntimeException() }` → assert
  `state.error != null && !state.isLoading` (not stuck loading). Then `vm.retry()` with the fake
  switched to a good flow → assert recovery (`error == null`, data present). Fakes are `open` and
  StateFlow-backed, so a throwing override is trivial (no new infra).
- No Compose-UI test harness exists in this repo (confirmed — no `createComposeRule`, no compose-ui-test
  dep); the regression is VM-level (assert the StateFlow value). `ErrorState` rendering is visual-only,
  verified by build + on-device — documented coverage boundary, consistent with the repo's stance.

### Scope note
This is a wide but mechanical change (10 UiStates + 10 VMs + 10 screens + 1 composable). Each edit is
2-4 lines and uniform. Battle is the one deliberate exclusion.

---

## Fix 2 — #250: offline/pending purchases only reconcile on Store open (monetization)

### Problem (code-grounded)
`BillingManagerImpl.reconcilePendingPurchases()` (idempotent, `sessionMutex`-serialised,
`ensureConnected()`-guarded, Activity-independent) is called **only** from `StoreViewModel.init`. Play
auto-refunds purchases not acknowledged within 3 days (KDoc `BillingManagerImpl.kt:265-266`). A player
who buys Ad Removal / Season Pass on a flaky connection, gets the local grant, but whose `acknowledge()`
RPC fails, loses the entitlement to a refund unless they re-open the **Store** within 3 days. No
app-resume, no worker, no connectivity hook drives reconciliation. The interface already documents the
intent: `BillingManager.reconcilePendingPurchases()` "Intended to be called on Store entry **and app
resume**" (default no-op body).

### Design — two triggers (foreground + background)
The method is already safe to call repeatedly and from any context (the `BillingClient` is built from
`@ApplicationContext`; only `launchBillingFlow` needs an Activity). Add:

1. **`MainActivity.onResume` (foreground, immediate).** `MainActivity` is already `@AndroidEntryPoint`
   with `@Inject` fields and an `onResume()` (sets `activityProvider`, updates lastActive, prefetches
   consent) + an `activityScope`. Add `@Inject internal lateinit var billingManager: BillingManager`
   and, in `onResume` after `activityProvider.set(this)`:
   `activityScope.launch { withTimeoutOrNull(20_000) { billingManager.reconcilePendingPurchases() } }`.
   No one-shot guard needed (idempotent + connect-guarded → a no-network resume is nearly free; a
   per-resume sweep is correct). `activityScope` is alive in `onResume` (cancelled only in `onDestroy`).
2. **`StepSyncWorker.doWork` (background safety net, every 15 min).** Covers the user who never
   re-foregrounds the app within 3 days. Inject `BillingManager` into the `@HiltWorker` constructor
   (a `@Singleton` non-assisted ctor dep is fine; the `BillingClient` is built from `@ApplicationContext`
   so it's headless-capable — no Activity needed for connect/query/acknowledge/consume).
   **CRITICAL (review finding 7): wrap the call in a timeout.** `reconcilePendingPurchases()` →
   `ensureConnected()` → `adapter.connect()` is a `suspendCancellableCoroutine` that resumes only in
   `onBillingSetupFinished`; `onBillingServiceDisconnected` deliberately does NOT resume → offline /
   Play-Services-stalled hangs with **no timeout**, tying up `doWork` to WorkManager's 10-min cap (and
   leaking a coroutine on `activityScope` on the resume path). Best-effort block before the final `return`:
   `try { withTimeoutOrNull(20_000) { billingManager.reconcilePendingPurchases() } } catch (e: Exception) { Log.w(TAG, …) }`
   (mirrors the existing HC-sync / smart-reminder try/catch blocks). Apply the same `withTimeoutOrNull`
   wrap on the `MainActivity.onResume` path. `CoroutineWorker.doWork` is `suspend`; `sessionMutex`
   serialises it against the Store/resume paths.

### Tests (TDD, RED first)
- **Worker carries the regression test** (most unit-testable): a `StepSyncWorker` test (new) using
  `work-testing` (`TestListenableWorkerBuilder`) + a `FakeBillingManager` (already exists, has
  `reconcileCallCount`) → assert the worker calls `reconcilePendingPurchases` exactly once on a
  successful `doWork()`. **Review finding 8:** `TestListenableWorkerBuilder` needs a `Context`, so this
  test must be **Robolectric + JUnit4** (`@RunWith(RobolectricTestRunner::class)`, NOT the Jupiter
  default the VM tests use), and there is **no existing worker-test precedent** + 8 non-assisted deps to
  supply. If a clean build proves too heavy, fall back to a thin extraction (a `suspend
  reconcileBillingSafely(billingManager)` helper the worker calls, unit-tested directly with
  `FakeBillingManager`) OR document the worker coverage boundary and keep the 2-line best-effort block.
  Decide during impl — prefer the extracted-helper route if the full worker build is heavy.
- **MainActivity.onResume** is not cleanly unit-testable (Compose Activity, no `lifecycle-process` dep);
  its trigger is verified by build + on-device, documented as a coverage boundary (the worker path
  carries the asserted regression).

---

## Task order (single PR)
1. ☐ #194: `ErrorState.kt` + `error` field on 10 UiStates + `_retry`/`.catch`/`retry()` on 10 VMs +
   error early-return on 10 screens. RED→GREEN VM tests (throw → error state; retry → recover).
2. ☐ #250: `MainActivity.onResume` reconcile + `StepSyncWorker.doWork` reconcile + worker test (RED→GREEN).
3. ☐ Full build: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`.
4. ☐ Sync current-state docs (CLAUDE.md test count; CHANGELOG; source-files; STATE/RUN_LOG) + ADR if warranted.
5. ☐ Branch, commit, PR, watch CI, merge, checkpoint.

## Open verification items (resolve during impl)
- [ ] Confirm `work-testing` (`libs.versions.toml`) + a tractable `StepSyncWorker` test construction;
      if too heavy, document the worker coverage boundary and keep the call.
- [ ] Confirm each of the 10 VMs' exact combine/flatMapLatest shape when adding `_retry` (the date-based
      3 fold `_retry` into the existing outer flow; the rest get a fresh `_retry.flatMapLatest`).
- [ ] Confirm no UiState already has an `error` field (grep said none) before adding.
