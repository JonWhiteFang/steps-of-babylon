# Implementation Plan — Presentation→data cleanup #219 · #229

**Spec:** `2026-06-20-presentation-data-cleanup-219-229.md` (reviewed; F1 major + F2/F3/F4 applied).
**Branch:** `arch/presentation-data-219-229` (cut). **Target:** `[Unreleased]`, behavior-preserving.

OQ resolutions: **OQ1** — `observeClaimedMilestoneIds(): Flow<Set<String>>` (both consumers compute a claimed
set; no new domain model). **OQ2** — add a `PresentationPurityTest` (machine-enforce #219 like #228 did for
domain), allowlisting the one `BattleViewModel` `AppDatabase` seam — small, worth it.

Sequencing: ports + impls + fakes first (additive, compiles), then reroute each VM + its test, then the
purity guard, then docs. Build green at each step.

## Task list

### Phase A — add the 4 port methods + impls + fake impls (additive)
1. **`MissionRepository`** (`domain/repository/`): add
   `fun observeMissionsForDate(date: String): Flow<List<DailyMission>>` and
   `fun observeClaimableCount(date: String): Flow<Int>`. Update the port KDoc (it currently says these "stay
   on the raw DAO… #219"). **`MissionRepositoryImpl`:** `observeMissionsForDate = dao.getByDate(date).map {
   list -> list.mapNotNull { it.toDomainOrNull() } }` (reuse the existing private mapper); `observeClaimableCount
   = dao.countClaimable(date)` (already `Flow<Int>`, pass through). Import `kotlinx.coroutines.flow.map`.
2. **`MilestoneRepository`**: add `fun observeClaimedMilestoneIds(): Flow<Set<String>>`. Update KDoc.
   **`MilestoneRepositoryImpl`:** `dao.getAll().map { it.filter { e -> e.claimed }.map { e -> e.milestoneId }.toSet() }`.
3. **`WeeklyChallengeRepository`**: add `suspend fun getLastNWeeks(limit: Int): List<WeeklyChallenge>`.
   **`WeeklyChallengeRepositoryImpl`:** `dao.getLastNWeeks(limit).map { it.toDomain() }` (the `toDomain()`
   mapper exists — make it usable here; it's `private` today, fine since the call is in-class).
4. **Fakes** (package `…fakes` — `app/src/test/java/com/whitefang/stepsofbabylon/fakes/`, NOT `test/fakes/`
   [plan-review F-1]): `FakeMissionRepository.observeMissionsForDate = dao.getByDate(date).map {… mapNotNull }`
   + `observeClaimableCount = dao.countClaimable(date)` (delegates to backing `FakeDailyMissionDao`, which is
   `MutableStateFlow`-backed → reactive). `FakeMilestoneRepository.observeClaimedMilestoneIds =
   dao.getAll().map {…}`. `FakeWeeklyChallengeRepository.getLastNWeeks` — **no backing DAO** (F4/F-5): its
   in-memory field is named `data`, so `data.values.sortedByDescending { it.weekStartDate }.take(limit)`;
   seeding stays via the existing `upsert`. Build: `./run-gradle.sh :app:compileDebugUnitTestKotlin`.

### Phase B — reroute the 5 ViewModels (one at a time, build after each)
5. **`CurrencyDashboardViewModel`** (the entity leak — do first): inject `WeeklyChallengeRepository`,
   `DailyLoginRepository`, `StepRepository`; drop `weeklyChallengeDao`/`dailyLoginDao`/`dailyStepDao` + the
   `WeeklyChallengeEntity` import (`:8`). `:86` `dailyStepDao.sumCreditedSteps` → `stepRepository.sumCreditedSteps`;
   `:87` `weeklyChallengeDao.getByWeek(weekStart) ?: WeeklyChallengeEntity(weekStartDate=weekStart)` →
   `weeklyChallengeRepository.getByWeek(weekStart) ?: WeeklyChallenge(weekStartDate=weekStart)`; `:88`
   `dailyLoginDao.getByDate` → `dailyLoginRepository.getByDate`; `:92` `weeklyChallengeDao.getLastNWeeks(5)` →
   `weeklyChallengeRepository.getLastNWeeks(5)` (now `List<WeeklyChallenge>`; the VM's existing `entity →
   WeeklyResult` map at `:95-101` reads `weekStartDate`/`totalSteps`/`claimedTier`, all on the domain model).
   Confirm the `dailyLoginDao`/`sumCreditedSteps` results are read only via fields on `DailyLogin`/`Long`.
6. **`MissionsViewModel`** (the F1 rewrite): drop `dailyMissionDao`/`milestoneDao`/`dailyStepDao` + the stale
   `PlayerProfileDao` import (`:9`). `combine` arg `:97` `dailyMissionDao.getByDate(today)` →
   `missionRepository.observeMissionsForDate(today)`; `:98` `milestoneDao.getAll()` →
   `milestoneRepository.observeClaimedMilestoneIds()` (the lambda's `claimedMilestones`→`claimedIds` derivation
   at `:100` collapses — it's now already the `Set<String>`). **Rewrite the display mapping** `:108-111`:
   `MissionDisplayInfo(m.id, m.type.description, m.target, m.progress, m.rewardGems, m.rewardPowerStones,
   m.completed, m.claimed)` (drop the `missionType.let{find…}`). `updateWalkingMissionProgress` `:193-200`:
   `dailyMissionDao.getByDateOnce` → `missionRepository.getMissionsForDate`; `dailyStepDao.sumCreditedSteps` →
   `stepRepository.sumCreditedSteps` (inject `StepRepository`); `:197` `find { it.name == m.missionType }` →
   `m.type`; `:200` `dailyMissionDao.updateProgress` → `missionRepository.updateProgress`. (MissionsVM already
   injects `missionRepository`/`milestoneRepository` from #227; add `stepRepository`.)
7. **`HomeViewModel`**: drop `dailyMissionDao`/`milestoneDao`. `:108` `dailyMissionDao.countClaimable(date)` →
   `missionRepository.observeClaimableCount(date)`; `:109` `milestoneDao.getAll()` →
   `milestoneRepository.observeClaimedMilestoneIds()` (the `:111` `milestoneEntities.filter{}.map{}.toSet()`
   collapses to the provided `Set<String>` — keep the `achievableMilestones` count math identical). Both
   ports already injected (#227).
8. **`WorkshopViewModel`**: drop `dailyMissionDao`, inject `MissionRepository`. `:128`
   `dailyMissionDao.getByDateOnce` → `missionRepository.getMissionsForDate`; `:129` `find { it.missionType ==
   …name }` → `m.type == DailyMissionType.SPEND_5000_WORKSHOP`; `:132` `updateProgress` → port.
9. **`BattleViewModel`**: drop `dailyMissionDao`, inject `MissionRepository`. `:352`
   `dailyMissionDao.getByDateOnce` → `missionRepository.getMissionsForDate`; `:355-364` `find { it.missionType
   == … }` → `m.type ==` (REACH_WAVE_30 / KILL_500_ENEMIES); `:358,:362` `updateProgress` → port. **KEEP**
   `appDatabase` + `runInTransaction`/`withTransaction` (documented exception) + its KDoc; add a one-line note
   pointing to the ADR.

### Phase C — purity guard + the 5 tests
10. **`PresentationPurityTest`** (`test/.../architecture/`, mirrors `DomainPurityTest`): walk
    `src/main/java/.../presentation`, fail on any `import …data.local.\w+Dao`, `…data.local.AppDatabase`, or
    `…data.local.\w+Entity` — **allowlist exactly** `BattleViewModel.kt`'s `AppDatabase` import (the seam).
    Self-validating (assert presentation root exists). This machine-enforces #219.
11. **Rewire the 5 ViewModel tests** to fakes (all fakes exist from #227, in package `…fakes` —
    `app/src/test/java/com/whitefang/stepsofbabylon/fakes/`, NOT `test/fakes/` [plan-review F-1]). Each VM
    ctor change alters arity, so the test construction site MUST be edited in lockstep or it won't compile:
    - `CurrencyDashboardViewModelTest` (highest): swap the 3 Mockito DAO mocks (`mock<WeeklyChallengeDao>` /
      `mock<DailyLoginDao>` / `mock<DailyStepDao>`) → `FakeWeeklyChallengeRepository` / `FakeDailyLoginRepository`
      / `FakeStepRepository`. The current `.thenReturn(WeeklyChallengeEntity(...))` / `getLastNWeeks` /
      `getByDate` / `sumCreditedSteps` stubs become **fake-state seeding with domain values**: the 5 weekly
      history rows seed via `FakeWeeklyChallengeRepository.upsert(WeeklyChallenge(...))` (the only seeding path
      the fake exposes — F-5), `DailyLogin` via the login fake, credited steps via `FakeStepRepository.records`.
      `WeeklyChallengeEntity(...)` literals → `WeeklyChallenge(...)`. `PlayerRepository` stays a fake.
    - `HomeViewModelTest` (plan-review F-3): `createVm` (`:65-69`) passes `dailyMissionDao, milestoneDao`
      positionally (args 9 & 10) — **remove both from the call**. Keep the backing `FakeMilestoneDao`/
      `FakeDailyMissionDao` fields (`:37-38`) that the wrapping fake repos use for seeding.
    - `MissionsViewModelTest` (plan-review F-2): `createVm` (`:118-128`) — **drop the `dailyMissionDao` /
      `milestoneDao` / `dailyStepDao` args** and **add `stepRepository = FakeStepRepository()`** (the current
      `mock<DailyStepDao>{ sumCreditedSteps … doReturn 0L }` → a default empty `FakeStepRepository`
      reproduces 0L; seed `records` only if a WALKING-progress test needs it). Keep the backing fake DAOs for
      seeding via the fake repos.
    - `WorkshopViewModelTest`: `mock<DailyMissionDao>()` (`:29`, stubbed `getByDateOnce → emptyList()` `:37`)
      → `FakeMissionRepository`; update the `WorkshopViewModel(...)` ctor call (`:43`).
    - `BattleViewModelTest` (plan-review F-4): `mock<DailyMissionDao>()` (`:41`) → `FakeMissionRepository`;
      **update ALL 3 ctor sites** (`createVm` `:71-74`, `:553-556`, `:591-594`, `dailyMissionDao` is arg 9)
      and **remove the `whenever(dailyMissionDao.getByDateOnce(any())).thenReturn(emptyList())` stub** (`:61`).
      **Keep** `mock<AppDatabase>()` + the `runInTransaction = { block -> block() }` pass-through overrides.
      Battle tests don't assert mission progress (stub was empty) → no expected-value drift.
12. **Mutation check (#219 guard):** temporarily add a literal `import com.whitefang.stepsofbabylon.data.local.MilestoneDao`
    line (NOT an inline FQN — the scan matches `startsWith("import ")`; inline FQNs evade it, same known
    limitation as #228) to a non-allowlisted file (e.g. `HomeViewModel.kt`) → `PresentationPurityTest` FAILS
    naming the file+import; restore.

### Phase D — verify, doc-sync, commit, PR
13. **Full verify:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` SUCCESSFUL; the two acceptance
    greps (no `*Dao`/`AppDatabase` import in presentation except the BattleVM seam; no `*Entity` import in
    presentation) return only-the-seam / empty. All 5 VM tests pass with unchanged expected values.
    `DomainPurityTest` + `#252 AtomicDaoConcurrencyTest` still green.
14. **Doc sync:** CLAUDE.md (test count + note presentation→data now guarded by `PresentationPurityTest`;
    the "Notable guards" list); CHANGELOG `[Unreleased]`; source-files.md (the 4 new port/impl methods, the 3
    fake additions, `PresentationPurityTest`); structure.md (architecture/ now has 2 purity guards);
    `docs/architecture.md` if it states the boundary. **ADR-0035** — the persistence-abstraction rule (#229):
    every Room table has a port; DAO-direct confined to data layer; documented exceptions (BattleViewModel
    `withTransaction` seam, BillingReceiptDao data-internal) + the new `PresentationPurityTest`.
15. **Update `STATE.md` + append `RUN_LOG.md`.**
16. **Commit + open PR** (closes #219, #229), monitor CI, merge on green.

## Behavior-preservation discipline
- `observe*` impls are pure `dao.flow().map{}` — keep the VM `combine()` arity + lambda shape identical
  (R1). The VM tests that `advanceUntilIdle()` then assert post-emission state are the reactivity guard.
- Every VM-test expected value stays identical; only construction changes (DAO/mock → fake repo). If an
  expected value would change, STOP — that's behavior drift, not a structural move (the F2 unknown-type drop
  is the one pre-accepted exception; no test covers it).
- No `@Transaction`/guarded write touched; the BattleViewModel end-of-round transaction seam is unchanged.

## Open items deferred to discovery
- Whether `CurrencyDashboardViewModel` reads any `DailyLogin`/`DailyStepSummary` field the domain models
  don't expose (review said no — confirm at Phase B step 5).
- Exact `WorkshopViewModel`/`BattleViewModel` mission-type comparison enum members (read at each step).

## Review note
Per the Gate, this PLAN needs adversarial review before implementation. Ultracode OFF → flag + ask (a/b/c).
Recommend at least the lighter single-agent review (consistent with the spec).
