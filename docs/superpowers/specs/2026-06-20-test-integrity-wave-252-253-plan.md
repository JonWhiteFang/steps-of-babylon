# Implementation Plan — Test-integrity wave: #252 · #253

**Spec:** `2026-06-20-test-integrity-wave-252-253.md` (reviewed; F1 critical + F2/F3/F4 major applied).
**Branch:** `test/concurrency-compose-252-253` (already cut). **Target:** `[Unreleased]`, test-only PR.

Ordering rationale: do **#253 build wiring first** (it's the riskiest unknown — Compose-on-Robolectric
viability, R1). If it works, the screens follow; if it can't be made non-flaky on the JVM lane, we learn
that before sinking effort, and STOP-and-flag per R1. Then #252 (self-contained). Then docs + commit.

## Task list

### Phase A — #253 harness viability spike (de-risk R1 first)
1. **Add Compose-UI-test deps (test scope).**
   - `gradle/libs.versions.toml`: add aliases `compose-ui-test-junit4 = { group = "androidx.compose.ui",
     name = "ui-test-junit4" }` and `compose-ui-test-manifest = { group = "androidx.compose.ui",
     name = "ui-test-manifest" }` (version-less — BOM-managed, like the existing `compose-ui` entries).
   - `app/build.gradle.kts` dependencies: add `testImplementation(platform(libs.compose.bom))` (the BOM is
     currently only on `implementation`/`debugImplementation` — must be re-applied to the test classpath),
     `testImplementation(libs.compose.ui.test.junit4)`, `testImplementation(libs.compose.ui.test.manifest)`.
2. **Spike a trivial Compose-on-Robolectric test** — a throwaway `@RunWith(RobolectricTestRunner::class)`
   (`@Config(sdk=[34], application=android.app.Application)`, JUnit4) with `@get:Rule val composeRule =
   createComposeRule()`, set a one-line `Text("hello")`, assert `onNodeWithText("hello").assertExists()`.
   Run `./run-gradle.sh testDebugUnitTest --tests '*ComposeSpike*'`.
   - If it needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)` or a Robolectric `@Config` tweak to avoid a
     `Typeface`/native-graphics crash, find the minimal incantation here and record it (it becomes the
     template header for the two real tests).
   - **GATE:** if no reasonable config makes it pass reliably on the JVM lane → **STOP, delete the deps +
     spike, and flag to the developer** (fallback = instrumented `connected`-lane Compose tests, a scope
     change needing a decision). Do not silently switch lanes. Delete the spike file once the real tests exist.

### Phase B — #253 screen tests (TDD: assertions are the spec)
3. **`CardsScreenTest`** (`app/src/test/java/.../presentation/cards/CardsScreenTest.kt`) — do Cards first
   (genuinely fake-backed, simpler than Onboarding's pager). Template from the Phase-A header +
   `CardsViewModelTest` fake wiring.
   - Build a real `CardsViewModel` from `FakePlayerRepository(PlayerProfile(gems = …))` + `FakeCardRepository`
     + `FakeRewardAdManager` (the full ctor — all three deps have fakes, per plan-review F-G; `CardsViewModelTest.kt:41`
     is the template). `Dispatchers.setMain`. The "Equipped: 3/3" cap text only renders at `equippedCount >= 3`
     (`CardsScreen.kt:80`) — seed 3 equipped cards in `FakeCardRepository` for that assertion (F-H).
   - Clock/idling (F4): use `composeTestRule.setContent { CardsScreen(viewModel = vm) }`; rely on
     `mainClock.autoAdvance` (default true) + `waitForIdle()`; if `WhileSubscribed(5000)` leaves state cold,
     drive the dispatcher (`advanceUntilIdle()`/`UnconfinedTestDispatcher`) so the screen observes seeded state.
   - Assert (4–5 `@Test`): gem balance + owned-card count render; equipped 3/3 cap message shows at cap; a
     pack button `assertIsNotEnabled()` when `!canAfford` and `assertIsEnabled()` + `performClick()` invokes
     `openPack` (observe fake state change / spy) when affordable; `userMessage` surfaces then clears.
4. **`OnboardingScreenTest`** (`app/src/test/java/.../presentation/onboarding/OnboardingScreenTest.kt`).
   - Build `OnboardingViewModel` with **mocked** `StepSensorDataSource` + `BatteryOptimizationStatus`
     (per `OnboardingViewModelTest.kt:16-21`, which mocks all three deps) + mocked `OnboardingPreferences`.
     Pass screen the booleans + lambda callbacks (captured-flag lambdas).
   - **Corrected assertion targets (plan-review F-D/F-E/F-F — verified against `OnboardingScreen.kt`):**
     - **Skip does NOT call `onFinished` (F-E).** Skip (`:132-133`) calls `goTo(lastIndex)` — it jumps to
       the final slide; `onFinished` fires only from the final-slide CTA's `finish()` path (`:104-115`).
       Assert: Skip → pager on last slide → CTA → `onFinished` captured-flag set.
     - **a11y assertion targets the page-dots row, not a button (F-D).** The CTAs are plain
       `Button { Text(...) }` with **no contentDescription**; the only real `contentDescription` in the
       screen is the page-dots Row `"Page ${page+1} of ${slides.size}"` (`:194`). Assert
       `onNodeWithContentDescription("Page 1 of N")`; assert CTA branches by **visible text**
       (`onNodeWithText`).
     - **Suppress the battery primer to reach "Start playing" (F-F).** "Start playing" (`:296`) only renders
       when `showBatteryPrimer && !batteryPrimerHandled` is false, and `showBatteryPrimer =
       !battery.isIgnoring()` (`OnboardingViewModel.kt:35`). A default mock returns `false` → the battery
       primer ("Allow background activity") shows instead. So `whenever(battery.isIgnoring()).thenReturn(true)`
       when asserting the granted-state "Start playing" CTA (or deliberately assert the primer branch).
     - **CTA branch on `stepSensorAvailable`:** with sensor available vs unavailable (drive via the mocked
       `StepSensorDataSource`), assert "Enable step counting" (`:302`) vs the Health-Connect-fallback CTA.
   - Assert (3–4 `@Test`): first slide renders; page-dots contentDescription present; Next advances the
     pager; Skip → last slide → CTA → `onFinished`; the granted-state "Start playing" CTA shows when battery
     is ignoring + sensor available.
5. **#253 mutation check:** break one rendered string per screen (e.g. the Cards gem-balance / cap text,
   an Onboarding CTA label) AND remove the page-dots `contentDescription` (`OnboardingScreen.kt:194` — the
   one real content description, per F-D) → the matching assertions FAIL; restore. Record in commit/RUN_LOG.

### Phase C — #252 concurrent-contention DAO test
6. **`AtomicDaoConcurrencyTest`** (`app/src/test/java/.../data/local/AtomicDaoConcurrencyTest.kt`),
   `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk=[34], application=android.app.Application)`, JUnit4.
   - **DB build (F1):** file-based temp DB — `val dbFile = File.createTempFile("sob-conc", ".db")` (or a
     `@get:Rule TemporaryFolder`); `Room.databaseBuilder(ApplicationProvider.getApplicationContext(),
     AppDatabase::class.java, dbFile.absolutePath).build()` (NO `inMemory`, NO `allowMainThreadQueries` —
     workers run off-main). `tearDown`: `db.close()` + delete the file (+ `-wal`/`-shm`).
     - **No cipher needed (plan-review F-A, refuted-as-blocker):** SQLCipher is wired only in DI
       (`DatabaseModule` `SupportOpenHelperFactory`); `AppDatabase` is plain Room, so this builder opens
       unencrypted framework SQLite with no passphrase / no native-lib wall — exactly as the passing
       `GuardedClaimDaoTest` does. Encryption is irrelevant to the atomic-contention behavior under test.
     - **Validate determinism early (plan-review F-C):** write scenario (a) FIRST and run it 3× before
       building the other 6. Robolectric 4.16.1 defaults to SQLite NATIVE mode (real multi-connection pool
       for a *file* DB — in-memory cannot, confirming spec F1), but that's unverified for this harness; if
       (a) can't be made deterministic, fall back per R2 and document it.
   - **Contention harness helper:** `fun runContended(workerCount: Int, block: suspend () -> Int): List<Int>`
     — a `CountDownLatch(1)` start-gate + `workerCount` threads each `await()`-ing the gate then
     `runBlocking { block() }`, results into a `ConcurrentLinkedQueue<Int>`; `countDown()`; `join(5_000)` all.
   - **Scenarios / `@Test` methods (assert the invariant, F1):**
     a. `adjustStepBalanceIfSufficient` — seed balance affording exactly one of N; assert successes==1,
        rest==0, final balance == initial-cost and ≥ 0.
     b. `spendGemsAtomic` — as above + `totalGemsSpent` incremented exactly once.
     c. `spendPowerStonesAtomic` — as above.
     d. `WalkingEncounterDao.markClaimed` — one row, N concurrent; successes==1, row `claimed==1` once.
     e. `DailyMissionDao.markClaimed` — same.
     f. `MilestoneDao.claimMilestoneAtomic` — single milestone, N concurrent; exactly one `true`, wallet
        credited exactly once (the `@Transaction` read-then-write path).
     g. `WorkshopDao.purchaseUpgradeAtomic` — single affordable upgrade, N concurrent; exactly one `true`,
        balance debited once (the issue-named canonical path + mutation-check target).
   - **If file-based pool is flaky under Robolectric** (R2): per spec, fall back to invariant assertion under
     `:memory:` + a deterministic parking seam; document the fallback in the RUN_LOG. Re-run each new method
     2–3× to confirm determinism.
7. **#252 mutation check (F2 + plan-review F-B, CRITICAL — resolved here, not deferred):** the
   `@Transaction` DAO targets have **no injectable parking seam** (the only in-repo deterministic-race
   precedent, `DailyStepManager.onBeforeCreditCommit`, is a *production* seam — and the spec forbids adding
   main-code seams under a test PR). Under SQLite SERIALIZABLE, a body merely *rewritten* to read-then-write
   while still inside `@Transaction` will usually still serialize → **false-green**. So the mutation is:
   **temporarily remove the `@Transaction` annotation** from `MilestoneDao.claimMilestoneAtomic` (or
   `WorkshopDao.purchaseUpgradeAtomic`) → Room runs its two writes in **autocommit**, the file-pool workers
   interleave the read-check-then-write, and scenario (f)/(g) must FAIL (double-credit / over-spend).
   Restore the annotation. **GATE:** if removing `@Transaction` does NOT make the test fail (Robolectric's
   SQLite still serializes — see F-C), then the test cannot prove the contention property without a main-code
   seam → **STOP and flag per spec R3**; do not add a production seam silently, and do not claim a green
   mutation check that didn't actually catch the regression. Record the outcome either way in the RUN_LOG.

### Phase D — verify, doc-sync, commit
8. **Full verification:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL;
   `git diff --stat` shows **no `app/src/main/` change** (only build files + `app/src/test/`).
9. **Sync current-state docs** (PR Task-List Convention — BEFORE the STATE/RUN_LOG update):
   - `CLAUDE.md` headline test count → new total (1152 + new `@Test` count).
   - `CHANGELOG.md` — new `[Unreleased]` section.
   - `docs/steering/source-files.md` — entries for `AtomicDaoConcurrencyTest`, `CardsScreenTest`,
     `OnboardingScreenTest`; note Compose-UI-test deps added.
   - `docs/steering/tech.md` / `lib-*.md` — only if the Compose-UI-test dep addition warrants a note (likely
     a one-line "Compose UI tests run on the Robolectric/JVM lane" under testing conventions).
   - `docs/agent/STATE.md` headline test count + fragile-zone note if the concurrency-test surface grows.
   - No schema/structure/README/database-schema change (test-only).
10. **Update `docs/agent/STATE.md` (rotate Current objective) + append `docs/agent/RUN_LOG.md`.**
11. **ADR:** none expected (test additions on established patterns). Add one only if the #252 fallback path
    (file-based vs `:memory:`+seam) becomes a non-trivial reusable decision — judge at the end.
12. **Commit + open PR** (closes #252; #253 stays open — PR notes the beachhead and what remains). Monitor CI,
    merge on green.

## Open items the plan defers to implementation discovery
- Ctor arg lists are now **resolved** (plan-review F-G): `CardsViewModel(CardRepository, PlayerRepository,
  RewardAdManager)` — all faked; `OnboardingViewModel(OnboardingPreferences, StepSensorDataSource,
  BatteryOptimizationStatus)` — all mocked per `OnboardingViewModelTest`.
- The precise Robolectric config incantation for Compose (Phase A spike output) — genuine unknown (R1/F-I).
- Whether removing `@Transaction` reliably fails the #252 mutation check under Robolectric's SQLite, or
  whether proving contention needs a forbidden main-code seam (step 7 GATE; STOP-and-flag per R3 if so).

## Review note
Per the Adversarial Review Gate, this PLAN must pass adversarial review before implementation. Ultracode is
OFF → flag as unreviewed and ask the developer (a/b/c) before writing any test code.
