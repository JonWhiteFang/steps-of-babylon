# Spec — Test-integrity wave: #252 concurrent-contention DAO test · #253 Compose-UI-test beachhead

**Date:** 2026-06-20 · **Branch:** `test/concurrency-compose-252-253` · **Target:** `[Unreleased]`
**Issues:** #252 (severity:major, testing) · #253 (severity:major, testing) — both from the 2026-06-18
complete-app review, both adversarially confirmed (2 refuters, 2✓/0~/0✗).

## Goal

Close the two highest-confidence, lowest-risk test-integrity gaps as one **test-only** PR:

1. **#252** — the guarded-deduct / one-shot-claim atomic DAOs are only tested *serially*. Add a real
   concurrent-contention test at the DAO layer that opens the race window a serial test never can.
2. **#253 (beachhead)** — the presentation layer has *zero* Compose UI tests. Stand up the Compose-UI-test
   harness and cover the **two** highest-value screens the issue names (Onboarding carousel + a
   claim/currency flow). #253 stays **open** for follow-up screen slices; this PR establishes the pattern.

**Non-goals.** No production-code change (no new app behavior, no schema/economy/engine change). No
emulator/instrumented-lane additions. Closing #253 outright (deferred — beachhead only). No coverage
tooling (#218, separate).

## Constraints / invariants (must hold)

- **Test-only.** Nothing under `app/src/main/` changes. Build files change only to add **test-scope**
  dependencies. (If a screen proves genuinely untestable without a production seam, STOP and flag — do
  not refactor main code under a test PR without an explicit decision.)
- New tests run on the **JVM `testDebugUnitTest` lane** (the PR gate), not the `connected` emulator lane.
- Fragile zones intact: no touch to `GameEngine`/`Simulation`/`EffectEngine` or their concurrency tests.
- Atomic-spend invariant (ADR-0020/#122): guarded `UPDATE … WHERE balance >= cost` returning
  rows-affected; the test asserts the invariant, never weakens it.

## Grounding (verified file:line facts)

### #252 — atomic DAOs + existing test fabric
- **Serial-only test today:** `app/src/test/java/com/whitefang/stepsofbabylon/data/local/GuardedClaimDaoTest.kt`
  (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk=[34], application=android.app.Application)`,
  JUnit4 via the vintage engine). Builds `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`;
  exercises `markClaimed` ×2, `adjustStepBalanceIfSufficient`/`spendGemsAtomic`/`spendPowerStonesAtomic` —
  all sequential on one coroutine (lines 44-83).
- **Single-statement guarded UPDATEs** (atomic at the SQLite level — the strongest case to prove under load):
  - `PlayerProfileDao.adjustStepBalanceIfSufficient(cost)` — `PlayerProfileDao.kt:53` —
    `… SET currentStepBalance = currentStepBalance - :cost WHERE id = 1 AND currentStepBalance >= :cost`
  - `PlayerProfileDao.spendGemsAtomic(amount)` — `PlayerProfileDao.kt:105`
  - `PlayerProfileDao.spendPowerStonesAtomic(amount)` — `PlayerProfileDao.kt:113`
  - `WalkingEncounterDao.markClaimed(id, claimedAt)` — `WalkingEncounterDao.kt:28` — `… WHERE id AND claimed = 0`
  - `DailyMissionDao.markClaimed(id)` — `DailyMissionDao.kt:63`
- **`@Transaction` default-method composites** (Kotlin-side read-then-write; safety relies on SQLite
  serializing the txn — the part never exercised under contention):
  `MilestoneDao.claimMilestoneAtomic` (`MilestoneDao.kt:56`), `CardDao.openCardPackAtomic`
  (`CardDao.kt:66`), `UltimateWeaponDao.unlockWeaponAtomic` (`UltimateWeaponDao.kt:63`),
  `WorkshopDao.purchaseUpgradeAtomic` (`WorkshopDao.kt:48`).
- **Concurrency-test template:** `DailyStepManagerConcurrencyTest.kt` (#120) — plain JUnit Jupiter
  (no `@RunWith`), two real `java.lang.Thread`s wrapping `runBlocking`, orchestrated with two
  `CountDownLatch` + `Thread.sleep`/`join(5_000)`. `GameEngineConcurrencyTest`/`EffectEngineConcurrencyTest`
  use the probabilistic `Thread` + `AtomicReference<Throwable>` stress pattern.
- **Availability:** Robolectric 4.16.1 (`libs.versions.toml:39`), `room-testing` (`app/build.gradle.kts:312`),
  `coroutines-test` already on the test classpath. JVM tests run `useJUnitPlatform()`
  (`app/build.gradle.kts:197`) with the **vintage engine present** (`app/build.gradle.kts:308`) — so JUnit4
  `@RunWith(RobolectricTestRunner)` and JUnit4 `@Rule` (incl. `ComposeTestRule`) both run on the JVM lane.

### #253 — Compose-UI-test harness + target screens
- **No Compose test deps exist.** Compose BOM (`composeBom = 2026.06.00`) is applied via
  `platform(libs.compose.bom)` (`app/build.gradle.kts:229`) to `implementation` only — **not** to the test
  classpath. No `ui-test-junit4` / `ui-test-manifest` entries in `libs.versions.toml` or the build file.
- **Robolectric path (chosen):** Robolectric 4.16.1 is already a dep and `unitTests.isIncludeAndroidResources
  = true` (`app/build.gradle.kts:199`) is set → `createComposeRule()` runs on the **JVM lane**. The 19 fakes
  in `app/src/test/java/com/whitefang/stepsofbabylon/fakes/` are already on the unit-test classpath, so
  fake-backed ViewModels can be constructed and injected directly. (Instrumented Compose tests would require
  duplicating fakes into `androidTest` + a Hilt graph — strictly more work, worse-gated. Rejected.)
- **Screens take their VM via `hiltViewModel()` default-arg** and read state via
  `collectAsStateWithLifecycle()`. A test bypasses Hilt by constructing the real VM from fakes (the
  `HomeViewModelTest.createVm()` pattern) and passing `viewModel = vm`.
  - **Onboarding** (easiest; issue's #1 priority): `presentation/onboarding/OnboardingScreen.kt:69` —
    params are passed-in booleans + lambda callbacks + `viewModel: OnboardingViewModel = hiltViewModel()`.
    VM ctor (`OnboardingViewModel.kt:11`) takes `OnboardingPreferences`, `StepSensorDataSource`,
    `BatteryOptimizationStatus` (light). State is plain fields (`slides`, `stepSensorAvailable`,
    `shouldOfferBatteryExemption`). Skip button at `OnboardingScreen.kt:133`; `onFinished` callback.
  - **Cards** (claim/currency flow; issue says "a currency/claim screen first"):
    `presentation/cards/CardsScreen.kt:61` — `CardsScreen(viewModel: CardsViewModel = hiltViewModel())`.
    Asserts: gem balance + owned count (`:76`), equipped 3/3 cap message (`:80`), free-pack ad button gated
    on `freePackAvailable` (`:93`), pack buttons enabled on `pack.canAfford` → `viewModel.openPack(tier)`
    (`:111`), snackbar via `state.userMessage` (`:67`). `CardsViewModelTest` is the fake-wiring template.
- **VM-with-fakes template:** `HomeViewModelTest.kt:61` `createVm()`; fakes seeded in `@BeforeEach`;
  `Dispatchers.setMain(StandardTestDispatcher())`. `FakePlayerRepository(PlayerProfile(...))` seeds any
  wallet/tier state (`FakePlayerRepository.kt:11`).

## What this wave delivers

### #252 — `AtomicDaoConcurrencyTest` (new, `data/local/`)
A Robolectric + in-memory Room test (mirroring `GuardedClaimDaoTest`'s DB build) that fires **N concurrent
workers** (real threads, `CountDownLatch` start-gate so they collide) at a balance/claim that affords
**exactly one** success, and asserts the atomic invariant under contention:

- **Guarded deduct (single-statement):** seed `currentStepBalance` to afford exactly one of N spends;
  fire N concurrent `adjustStepBalanceIfSufficient(cost)`; assert **exactly one** returns `1`, the rest `0`,
  and the final balance never goes negative and equals `initial - cost`. Repeat for `spendGemsAtomic`
  (also assert `totalGemsSpent` increments exactly once) and `spendPowerStonesAtomic`.
- **One-shot claim:** insert one claimable row; fire N concurrent `markClaimed(...)`; assert **exactly one**
  returns `1`, the rest `0`, and the row ends `claimed = 1` once (no double-credit). Cover both
  `WalkingEncounterDao.markClaimed` and `DailyMissionDao.markClaimed`.
- **`@Transaction` composite (at least one):** N concurrent `MilestoneDao.claimMilestoneAtomic(...)` for a
  single milestone → exactly one `true`, wallet credited exactly once (this is the read-then-write path
  whose txn-serialization is the real risk).

Must also cover `WorkshopDao.purchaseUpgradeAtomic` (`WorkshopDao.kt:48`) — the canonical Steps-spend path
the issue names by name, and the natural mutation-check target (see below).

Concurrency mechanics: prefer the deterministic `CountDownLatch` start-gate + `join(timeout)` shape (from
`DailyStepManagerConcurrencyTest`) over `Thread.sleep` racing. Each worker runs its DAO call off the main
thread; collect results into a thread-safe structure (`AtomicInteger` success counter / `ConcurrentLinkedQueue`).
Run a modest N (e.g. 8–16) and, where cheap, loop the scenario a few times to widen the window.

> **RESOLVED (spec review F1, critical).** `Room.inMemoryDatabaseBuilder(...)` opens a **single private
> connection** — a `:memory:` SQLite DB has no shared cache, no WAL, no connection pool, so N threads
> **cannot physically race at the SQLite layer**; Room serializes them on the one connection. Therefore:
> - **DB build:** use a **file-based temp DB** (`Room.databaseBuilder(ctx, AppDatabase::class.java,
>   tempFile.path)`, temp dir per-test, deleted in `tearDown`) so Room provisions its **real connection
>   pool + WAL** and concurrent writers genuinely contend — this is the only build path that exercises the
>   behavior #252 is about. Do **not** use `:memory:` for the contention test. (Keep the serial
>   `GuardedClaimDaoTest` as-is.) The journal-mode-on-`:memory:` option is struck (a dead end on a
>   private-cache DB). If the file-based pool proves flaky under Robolectric, fall back to asserting the
>   **invariant** under whatever execution Room provides — see next bullet — and say so explicitly.
> - **Assertion is invariant-based, never timing-based:** `successes == 1 && everyOtherCall == 0 &&
>   finalBalance == initial - cost && finalBalance >= 0` (and `totalGemsSpent` incremented exactly once;
>   claim row `claimed == 1` exactly once). The invariant — not a forced physical race — is what proves
>   correctness, and it false-fails under no scheduling. Must be **deterministic / non-flaky** in CI.
> - **Mutation-check target (spec review F2, major):** a single-statement guarded `UPDATE` is atomic at
>   SQLite regardless of threading, so removing its `WHERE … >= :cost` guard may NOT fail a serialized
>   test → false-green. The mutation check MUST target a **`@Transaction` composite** whose Kotlin body has
>   a read-then-write seam — `WorkshopDao.purchaseUpgradeAtomic` or `MilestoneDao.claimMilestoneAtomic` —
>   rewritten into a non-atomic read-check-then-write (or the txn boundary dropped); the file-based-pool
>   contention test must then catch the resulting double-spend. The plan settles whether a deterministic
>   parking seam (à la `DailyStepManagerConcurrencyTest`'s `onBeforeCreditCommit`) is needed to make that
>   catch reliable, or whether the real WAL pool surfaces it on its own.

### #253 — Compose-UI-test harness + 2 screens
- **Build wiring (test-scope only):** add `compose-ui-test-junit4` (BOM-managed, version-less) +
  `compose-ui-test-manifest` aliases to `libs.versions.toml`; add `testImplementation(platform(libs.compose.bom))`
  + `testImplementation(libs.compose.ui.test.junit4)` + `testImplementation(libs.compose.ui.test.manifest)` to
  `app/build.gradle.kts` (test classpath — Robolectric/JVM lane). Confirm the BOM is on the test classpath
  (it is currently only on `implementation`).
- **`OnboardingScreenTest`** (new, `presentation/onboarding/`): Robolectric + `createComposeRule()`.
  **`OnboardingViewModel` is MOCK-backed, not fake-backed** (spec review F3) — its ctor takes the concrete
  final `@Singleton` `StepSensorDataSource` + `BatteryOptimizationStatus`, for which **no fakes exist**;
  mock them with mockito-kotlin exactly as `OnboardingViewModelTest.kt:19-21` already does (only
  repository-backed VMs like Cards use the `fakes/`). Assert: carousel renders the first slide; Skip invokes `onFinished`;
  paging/Next advances; the final-slide CTA branch (step-counting vs Health-Connect-fallback, driven by
  `stepSensorAvailable`) renders the right control + content description. At least one **content-description /
  semantics** assertion (the #253 a11y concern, complements the #214 TalkBack work).
- **`CardsScreenTest`** (new, `presentation/cards/`): Robolectric + `createComposeRule()`, **genuinely
  fake-backed** `CardsViewModel` seeded via `FakePlayerRepository`/`FakeCardRepository` (all its
  dependencies have fakes). Assert: gem balance + owned count render; equipped 3/3 cap message appears at
  cap; a pack button is **disabled** when `!canAfford` and **enabled**/invokes `openPack` when affordable;
  `userMessage` surfaces as a snackbar then clears.

> **Compose-test-clock interaction (spec review F4, major).** `createComposeRule()` is a **JUnit4 `@Rule`**,
> so it runs via the **vintage engine** (the JVM lane's platform is Jupiter; vintage is present at
> `app/build.gradle.kts:308`) — keep these test classes JUnit4-annotated. `CardsViewModel` exposes state via
> `stateIn(..., SharingStarted.WhileSubscribed(5000), ...)` (`CardsViewModel.kt:86`) consumed by
> `collectAsStateWithLifecycle()` (`CardsScreen.kt:62`); under a Compose test clock the `WhileSubscribed`
> timeout + lifecycle-aware collection interact with `composeTestRule.mainClock` non-obviously. The plan MUST
> settle clock/idling handling — likely `Dispatchers.setMain(StandardTestDispatcher/UnconfinedTestDispatcher)`
> + `mainClock.autoAdvance` (or explicit `waitForIdle`) — so the screen actually observes seeded state.

## Acceptance criteria
- `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL.
- New suites run on the JVM lane (not emulator); **0 failures, 0 flakes** (re-run the new suites 2–3× to
  confirm determinism).
- **Mutation check (mandatory, per repo MO):**
  - #252 — temporarily rewrite a **`@Transaction` composite** (`WorkshopDao.purchaseUpgradeAtomic` or
    `MilestoneDao.claimMilestoneAtomic`) into a non-atomic read-check-then-write (the Kotlin-side seam),
    NOT a single-statement guarded `UPDATE` (atomic at SQLite regardless of threading → would false-green)
    → the file-based-pool concurrency test must FAIL (it catches the double-spend the serial test misses).
    Restore. (Per spec-review F1/F2.)
  - #253 — break a rendered string / remove a content description on each tested screen → the matching UI
    assertion must FAIL. Restore.
- Test count rises by the number of new `@Test` methods; CLAUDE.md headline updated.
- No `app/src/main/` diff (verify with `git diff --stat`).

## Risks
- **R1 — Compose-on-Robolectric viability.** `createComposeRule()` under Robolectric can need a Robolectric
  config bump or `@GraphicsMode`. If it cannot be made to run reliably on the JVM lane, the fallback is
  instrumented Compose tests on the `connected` lane (fakes duplicated into `androidTest`) — a scope change
  the plan/review must flag, **not** silently adopt.
- **R2 — #252 file-based-pool flakiness** (resolved-approach above). A real WAL connection pool can surface
  `SQLiteDatabaseLockedException`/busy under heavy contention. The assertion must be invariant-based and
  deterministic (exactly-one-winner + non-negative balance), tolerate Room serializing writers, and never
  depend on `Thread.sleep` timing. If the file-based pool can't be made non-flaky under Robolectric, fall
  back to the invariant assertion under `:memory:` + a deterministic parking seam — and say so in the plan.
- **R3 — Screen needs a production seam to be testable.** If true, STOP and flag (test PR must not refactor
  main code without a decision).

## Review note (Adversarial Review Gate)
Ultracode is OFF. Per CLAUDE.md the Gate's multi-agent form is disabled by the opt-in rules — this spec is
**unreviewed**. Flag to the developer and ask whether to (a) turn ultracode on for the review, (b) run a
lighter single-agent review inline, or (c) proceed without one — before advancing spec→plan. Do not skip
silently.
