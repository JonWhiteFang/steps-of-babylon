# Implementation Plan — Architecture-invariant wave: #227 · #228

**Spec:** `2026-06-20-architecture-invariant-wave-227-228.md` (reviewed; F1 confirmed-safe, F2/F3/F5/F7/F9
applied). **Branch:** `arch/domain-purity-227-228` (cut). **Target:** `[Unreleased]`, behavior-preserving
structural refactor. **Combined PR, full domain models.**

OQ resolutions (locked from the spec review): **OQ1 — keep use cases hand-built** (no `@Inject` in domain;
add the `javax.inject` guard assertion). **OQ2 — extend `StepRepository`** with the battle/boss/sum methods
(cohesive; it already wraps `DailyStepDao`). **OQ3 — `DailyStepManager` only gets its constructor rewired**
(deeper smell flagged, not fixed). **OQ4 — presentation→data calls stay** (ports cover only use-case surface).

Sequencing principle: **build stays green at every numbered step** — but green means BOTH compile AND
`testDebugUnitTest` (plan-review: the first draft undercounted broken tests). Add ports + impls + bindings
FIRST (additive, compiles), then flip use cases one cluster at a time. **Each cluster must rewire ALL tests it
breaks, not just the use-case tests** — production-code cluster boundaries are sound (a VM keeps its raw DAO
until its last consuming use case flips), but the test blast radius is wider than the use-case tests alone
(see the per-cluster test lists + the summary below). Add the #228 guard LAST (the tripwire that proves done).

**Test-rewiring blast radius (plan-review F1/F2/F3/F4 — the full set, beyond the 9 use-case tests):**
- Cluster 5: `AwardBattleStepsTest`, `AwardBossPowerStonesTest`, **`BattleViewModelTest`**, **`StepRepositoryImplTest`** (9 ctor sites), + `FakeStepRepository` extension (felt by `EscrowLifecycleTest`, `StatsViewModelTest`, the 3 DailyStepManager tests — interface-method-only, no-op-safe).
- Cluster 6: `CheckMilestonesTest`, `ClaimMilestoneTest`, + `HomeViewModelTest`/`MissionsViewModelTest` construction.
- Cluster 7: `ClaimMissionTest`, `GenerateDailyMissionsTest`, `UpdateCompleteResearchMissionProgressTest`, `LabsViewModelTest`, + `HomeViewModelTest`/`MissionsViewModelTest`/`WorkshopViewModelTest` construction.
- Cluster 8: `TrackDailyLoginTest`, `TrackWeeklyChallengeTest`, **`DailyStepManagerTest` + `DailyStepManagerConcurrencyTest` + `DailyStepManagerErrorReportingTest`**.
- New fakes: `FakeMissionRepository`, `FakeMilestoneRepository`, `FakeDailyLoginRepository`, `FakeWeeklyChallengeRepository` (+ `FakeStepRepository` extension).

## Task list

### Phase A — domain models + ports + impls + Hilt (additive; build stays green)
1. **New domain models** (`domain/model/`): `DailyMission`, `DailyLogin`, `WeeklyChallenge` (field sets per
   spec; `DailyMission.type: DailyMissionType`). Pure Kotlin.
2. **New ports** (`domain/repository/`), methods = exactly the use-case call surface (NOT presentation):
   - `MissionRepository`: `getMissionsForDate(date): List<DailyMission>` (read once),
     `generateForDate(date, missions: List<DailyMission>)`, `markClaimed(id): Int`,
     `updateProgress(id, progress, completed)`.
   - `MilestoneRepository`: `getAllClaimedIds(): List<String>` (or `getAll(): List<MilestoneStatus>` — minimal
     for `CheckMilestones`' `.filter{claimed}.map{milestoneId}`), `claimMilestoneAtomic(milestoneId, gems,
     powerStones, claimedAt): Boolean`.
   - `DailyLoginRepository`: `getByDate(date): DailyLogin?`, `upsert(DailyLogin)`.
   - `WeeklyChallengeRepository`: `getByWeek(weekStart): WeeklyChallenge?`, `upsert(WeeklyChallenge)`.
   - **Extend `StepRepository`**: `creditBattleStepsAtomic(date, requested, cap): Long`,
     `creditBossPowerStonesAtomic(date, requested, cap): Long`, `sumCreditedSteps(start, end): Long`.
3. **New impls** (`data/repository/`), `@Inject constructor`, entity↔domain mapping private extensions:
   - `MissionRepositoryImpl(dailyMissionDao)` — `getMissionsForDate` maps entities→`DailyMission`
     **null-skipping unknown `missionType`** (F5: `DailyMissionType.entries.find{it.name==…}`, drop if null);
     `generateForDate` maps `List<DailyMission>`→`List<DailyMissionEntity>` and calls the DAO's `@Transaction`
     `generateForDate` (preserve batch + IGNORE); `markClaimed`/`updateProgress` delegate.
   - `MilestoneRepositoryImpl(milestoneDao, playerProfileDao)` — **injects both** (F1); `claimMilestoneAtomic`
     hands `playerDao = playerProfileDao` into the DAO `@Transaction`.
   - `DailyLoginRepositoryImpl(dailyLoginDao)`, `WeeklyChallengeRepositoryImpl(weeklyChallengeDao)` — map+delegate.
   - `StepRepositoryImpl` — **add `playerProfileDao` to its ctor** (currently injects only `DailyStepDao`); the
     3 new methods delegate to the DAO atomics with `playerDao = playerProfileDao` / to `sumCreditedSteps`.
4. **Hilt bindings** (`di/RepositoryModule.kt`): add `@Binds @Singleton` for the 4 new ports (StepRepository
   already bound). Build: `./run-gradle.sh assembleDebug` — must compile (ports unused yet, but present).

### Phase B — flip use cases cluster-by-cluster (build green after each cluster)
For each use case: swap data import(s) → port + domain model; drop `= SystemTimeProvider()` default args →
require injected `TimeProvider`; fold residual direct clock reads through `TimeProvider` where one is now in
scope; rewire call sites (pass ports, not DAOs); rewire the test to fake repos; build.

5. **Cluster: battle/boss crediting** — `AwardBattleSteps`, `AwardBossPowerStones` → depend on
   `StepRepository` (+ `TimeProvider`, no default). Call sites `BattleViewModel:114-115` (inject
   `StepRepository`, drop the raw `dailyStepDao`/`playerProfileDao` it passed to these use cases — keep
   `dailyMissionDao` which BattleVM uses directly at `:354-364`).
   **Tests that MUST be rewired this cluster (plan-review F1/F3/F6 — DO NOT defer):**
   - `AwardBattleStepsTest` / `AwardBossPowerStonesTest` → `FakeStepRepository`.
   - **`BattleViewModelTest`** (plan-review F1) — builds `BattleViewModel(... dailyStepDao, playerProfileDao ...)`
     and asserts `dailyStepDao.getBattleStepsEarned`/`incrementBattleSteps` (`:60,72-74,388-523`). Rewire to the
     `FakeStepRepository`; its battle-step/PS-count assertions must read off the fake repo's re-exposed state.
   - **`StepRepositoryImplTest`** (plan-review F3) — 9 single-arg `StepRepositoryImpl(dao)` call sites
     (`:40,55,67,77,90,101,112,123,133`) break when the ctor gains `playerProfileDao`; pass a mock/fake.
   - **`FakeStepRepository` extension (plan-review F6 — behavior-preservation crux):** today the credit/cap
     state + atomic call-counts live in `FakeDailyStepDao:94-140` (`linkedPlayer` routing,
     `creditBattleStepsAtomicCallCount`, per-day `getBattleStepsEarned`/`getBossPsEarnedToday`, partial-credit
     under cap). `StepRepository` does NOT expose those DAO methods, so `FakeStepRepository` must **re-surface
     equivalent test accessors + state** (credited-battle-steps map, cap logic, call-counts) or the
     battle/boss assertions (`AwardBattleStepsTest:43,55,68,106,132-175`) will either not compile or vacuously
     pass. The 5 other `FakeStepRepository` consumers (`EscrowLifecycleTest`, `StatsViewModelTest`, the 3
     DailyStepManager tests) just need the 3 new interface methods to exist (no-op-safe defaults). Build.
6. **Cluster: milestones** — `CheckMilestones` (→ `MilestoneRepository`), `ClaimMilestone` (→
   `MilestoneRepository`, drop raw `milestoneDao`+`playerProfileDao`; keep existing `PlayerRepository`/
   `CosmeticRepository`; fold `System.currentTimeMillis()` at `:98` through an injected `TimeProvider`). Call
   sites `HomeViewModel:88`, `MissionsViewModel:56-61`. Tests → new `FakeMilestoneRepository` (carry
   `claimMilestoneAtomicCallCount` from `FakeMilestoneDao`); rewire `CheckMilestonesTest`, `ClaimMilestoneTest`.
   **HomeVM/MissionsVM keep their raw `milestoneDao`** for direct `getAll()` Flow reads (`HomeViewModel:103`,
   `MissionsViewModel:95`) — so `HomeViewModelTest`/`MissionsViewModelTest` keep `FakeMilestoneDao` AND gain
   the construction change for the flipped use case (plan-review F4). Build.
7. **Cluster: missions** — `ClaimMission`, `GenerateDailyMissions` (build `List<DailyMission>` not entities),
   `UpdateCompleteResearchMissionProgress` (fold `LocalDate.now()` default through `TimeProvider`) → all depend
   on `MissionRepository`. Call sites `HomeViewModel:69,85,139`, `MissionsViewModel:54,55`, `LabsViewModel:47`.
   Tests → new `FakeMissionRepository`; rewire `ClaimMissionTest`, `GenerateDailyMissionsTest`,
   `UpdateCompleteResearchMissionProgressTest`, `LabsViewModelTest`. **HomeVM/MissionsVM/WorkshopVM keep their
   raw `dailyMissionDao`** for direct calls (`HomeVM:102` countClaimable, `MissionsVM:94,190-197`, `WorkshopVM:128-132`)
   → those VM tests keep `FakeDailyMissionDao` AND gain the flipped-use-case construction change (plan-review F4). Build.
8. **Cluster: login + weekly challenge** — `TrackDailyLogin` (→ `DailyLoginRepository`; build/read
   `DailyLogin` not entity; **add a `TimeProvider` ctor param** — it has none today — to absorb
   `System.currentTimeMillis()` at `:46`), `TrackWeeklyChallenge` (→ `WeeklyChallengeRepository` +
   `StepRepository.sumCreditedSteps`; build/read `WeeklyChallenge`; **add a `TimeProvider` ctor param** to
   absorb `LocalDate.now()`). Call sites `HomeViewModel:84`, `DailyStepManager:131-132` (rewire the `by lazy`
   builders + the manager ctor to ports — OQ3).
   **TimeProvider threading (plan-review F8):** `DailyStepManager` has no `TimeProvider` injected today — adding
   it to these two use cases means threading a `TimeProvider` into `DailyStepManager`'s ctor (or its
   `by lazy` builders) and `HomeViewModel:84`. Confirm the thread-through at each call site; if it balloons,
   fall back to keeping the inline clock read in these two (still removes the *data* import — the #227/#228
   goal) and note the residual clock-read as out-of-scope for this wave.
   **Tests that MUST be rewired this cluster (plan-review F2):** `TrackDailyLoginTest`,
   `TrackWeeklyChallengeTest` (needs BOTH `FakeWeeklyChallengeRepository` AND `FakeStepRepository` with
   `sumCreditedSteps` seeded as `DailyStepSummary`, not `DailyStepRecordEntity` — plan-review F5), **and all 3
   `DailyStepManager*Test`** (`DailyStepManagerTest`, `DailyStepManagerConcurrencyTest`,
   `DailyStepManagerErrorReportingTest`) which build the manager with `FakeDailyLoginDao`/`FakeWeeklyChallengeDao`
   (`:55-64,201-210`) → rewire to the new fake repos. New fakes: `FakeDailyLoginRepository`,
   `FakeWeeklyChallengeRepository`. Build.

### Phase C — the #228 guard + final verification
9. **Strengthen `DomainPurityTest`**: add `"com.whitefang.stepsofbabylon.data"` to `forbiddenPrefixes`; rename
   test/message to "domain has no Android AND no data-layer imports"; add a second assertion that domain has no
   `dagger.`/`javax.inject.` imports (F9 — locks DI-agnosticism, consistent with hand-built use cases). Run it.
10. **Confirm the violation is gone:**
    `grep -rl "import com.whitefang.stepsofbabylon.data" app/src/main/java/.../domain/` → empty.
11. **Mutation check (#228):** temporarily re-add one `import com.whitefang.stepsofbabylon.data.local.…` to a
    domain file → `DomainPurityTest` FAILS naming it; restore. (Note the known limitation: inline FQ refs
    without an import evade the scan — F8, accepted.)
12. **Full verification:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. All 9
    use-case tests green with **unchanged expected values** (behavior-preservation — only construction changed).
    `AtomicDaoConcurrencyTest` (#252) still green (independent atomic backstop). Confirm no schema change.

### Phase D — doc-sync, commit, PR
13. **Sync current-state docs** (PR Task-List Convention, BEFORE STATE/RUN_LOG): `CLAUDE.md` (architecture map
    — note domain purity now machine-enforced at the dependency-direction level; test count if it changed),
    `CHANGELOG.md` (new `[Unreleased]` section), `docs/steering/source-files.md` (3 models + 4 ports + 4 impls
    + 4 fakes + the strengthened guard), `docs/steering/structure.md` (new repository ports landed),
    `docs/architecture.md` if it describes the layer boundary. **ADR** — yes: this is a non-trivial
    architecture decision (restoring the dependency rule + the atomic-passthrough pattern for repo-wrapped
    `@Transaction` methods); add `ADR-0034-domain-data-dependency-rule.md`.
14. **Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.**
15. **Commit + open PR** (closes #227 + #228), monitor CI, merge on green.

## Behavior-preservation discipline (the core risk control)
- Every use-case test keeps its **expected values** identical; only the fake it's built with changes
  (fake DAO → fake repo). If an expected value would have to change, STOP — that's a behavior change, not a
  structural move; surface it.
- The atomic `@Transaction` methods are called from the impls with the real `PlayerProfileDao` (F1). Keep the
  `*AtomicCallCount` assertions alive through the fake-repo migration (R2).
- No SQL, no formula, no schema touched. `git diff` should show only: moved imports, new ports/impls/models/
  fakes, ctor-param swaps (DAO→port), and the guard.

## Open items deferred to implementation discovery
- Exact minimal method shape for `MilestoneRepository.getAll*` (read `CheckMilestones` body — it only needs
  claimed ids vs total; pick the smallest domain-typed surface).
- Whether `BattleViewModel`/`MissionsViewModel` still need their raw `dailyStepDao`/`playerProfileDao`/
  `milestoneDao`/`dailyMissionDao` for their OWN calls after the use cases stop taking them (keep the DAO param
  iff the VM still calls it directly; otherwise drop it). Decide per VM at its cluster.
- `FakeStepRepository` currently may not model the atomic credit — extend it (template: `FakeDailyStepDao`'s
  `linkedPlayer` routing) so the battle/boss tests assert real credit behavior.

## Review note
Per the Adversarial Review Gate, this PLAN must pass adversarial review before implementation. Ultracode is
OFF → flag as unreviewed and ask the developer (a/b/c) before writing any code. Given the size + fragile-zone
exposure, recommend at least the lighter single-agent review (as for the spec).
