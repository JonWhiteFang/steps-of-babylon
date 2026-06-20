# Spec — Presentation→data cleanup: #219 ViewModel DAO injection / entity leak · #229 persistence-abstraction consistency

**Date:** 2026-06-20 · **Branch:** `arch/presentation-data-219-229` · **Target:** `[Unreleased]`
**Issues:** #219 (severity:major, architecture — presentation injects DAOs / `AppDatabase`, raw entity leaks
into UI) · #229 (severity:major, architecture — inconsistent persistence abstraction). Both from the
complete-app reviews; #219 confirmed, #229 "partial" (2 refuters 0✓/2~ — it's a consistency smell, not a bug).

Builds directly on #227 (ADR-0034), which introduced the Mission/Milestone/DailyLogin/WeeklyChallenge ports.

## Goal

Finish the dependency-rule work at the **presentation→data** boundary:

1. **#219** — 5 ViewModels inject Room DAOs (and `BattleViewModel` injects `AppDatabase`), and
   `CurrencyDashboardViewModel` constructs/iterates a raw `WeeklyChallengeEntity` internally. Route every
   presentation-layer DAO read through a repository **port**; map entities to domain models at the data
   boundary so no Room `@Entity` type appears in a ViewModel. **Exception (developer decision):**
   `BattleViewModel`'s `AppDatabase`/`runInTransaction` (`withTransaction`) seam stays — it is a legitimate
   cross-repository atomicity boundary; moving it behind a port would mean restructuring the end-of-round
   write fan-out (out of scope). Document it as an accepted exception.
2. **#229** — after #227, **BillingReceiptDao is the only DAO with no port**, and it is data-layer-only
   (called solely by `BillingManagerImpl` behind the `BillingManager` port; never from presentation). So the
   "inconsistent abstraction" smell is resolved by (a) routing all presentation reads through ports [this
   wave] + (b) a short ADR recording the deliberate rule: **every Room table has a repository port; DAO-direct
   access is confined to the data layer (repository impls / data-layer orchestrators like `DailyStepManager`
   / `BillingManagerImpl`), never presentation.** BillingReceiptDao stays DAO-direct *within `BillingManagerImpl`*
   — which honors the rule (data-layer) — so no new BillingReceiptRepository port is required.

**Non-goals.** No behavior change (no schema/economy-formula/engine change; every read returns identical
data, every reactive Flow stays reactive) — **with one accepted edge-case shift (spec-review F2):** a
daily-mission row whose stored `missionType` no longer resolves to a `DailyMissionType` is now *dropped*
from the list (the `mapNotNull` in `MissionRepositoryImpl`, consistent with #227) instead of rendering its
raw id string. No test asserts the old raw-string fallback; this is the safe direction (a corrupt/renamed
type can't surface as garbage UI). NOT moving the `BattleViewModel` `withTransaction` seam behind a
port (documented exception). NOT adding a BillingReceiptRepository (data-internal; the ADR documents why).
NOT touching the GameEngine god-class (#230/#231) or process-death (#234).

## Constraints / invariants (must hold)

- **Behavior-preserving.** Reactive reads (`getByDate` Flow, `countClaimable` Flow, `milestoneDao.getAll()`
  Flow) MUST stay `Flow`-returning so the screens keep recomposing on Room emissions — do NOT collapse them
  to suspend snapshots. Snapshot reads (`getByDateOnce`, `sumCreditedSteps`, `getByWeek`, `getLastNWeeks`)
  stay suspend.
- **Atomic-deduct invariant intact** (ADR-0020/0027): this wave only moves *reads* + the dashboard's entity
  mapping behind ports; it does not touch any `@Transaction`/guarded write. `BattleViewModel`'s end-of-round
  transaction seam is unchanged.
- `DomainPurityTest` is unaffected (it scans `domain/`, not presentation). There is no machine guard for
  presentation→data today; the ADR documents the rule (a presentation-purity test is a possible follow-up,
  noted not built — see Open Questions).
- Fragile zones intact: `GameEngine`/`Simulation`/`EffectEngine` untouched; `BattleViewModel` change is a
  pure `dailyMissionDao` → `MissionRepository` swap on the read/update calls only.

## Grounding (verified file:line facts)

### Presentation→DAO call sites (the full surface)
| ViewModel | DAO calls | Already on a port? |
|---|---|---|
| `economy/CurrencyDashboardViewModel` | `dailyStepDao.sumCreditedSteps` (:86) ✓; `weeklyChallengeDao.getByWeek` (:87, **builds raw `WeeklyChallengeEntity` fallback** — the #219 leak, import :8); `dailyLoginDao.getByDate` (:88) ✓; `weeklyChallengeDao.getLastNWeeks(5)` (:92, **returns `List<WeeklyChallengeEntity>`**) | sumCreditedSteps/getByWeek/getByDate yes; **getLastNWeeks MUST ADD** |
| `home/HomeViewModel` | `dailyMissionDao.countClaimable(date)` Flow (:108); `milestoneDao.getAll()` Flow (:109, reads `.claimed`/`.milestoneId`) | **both MUST ADD (Flow)** |
| `missions/MissionsViewModel` | `dailyMissionDao.getByDate` Flow (:97); `milestoneDao.getAll()` Flow (:98); `getByDateOnce` (:193) ✓; `dailyStepDao.sumCreditedSteps` (:194) ✓; `updateProgress` (:200) ✓ | getByDate/getAll **MUST ADD (Flow)**; rest yes |
| `workshop/WorkshopViewModel` | `dailyMissionDao.getByDateOnce` (:128) ✓; `updateProgress` (:132) ✓ | yes (both on MissionRepository) |
| `battle/BattleViewModel` | `dailyMissionDao.getByDateOnce` (:352) ✓; `updateProgress` (:358,:362) ✓; **`appDatabase.withTransaction` seam (:94-97, called :317)** | DAO calls yes; **AppDatabase seam = documented exception** |

### Port methods to ADD (everything else already exists from #227)
- `MissionRepository`: `fun observeMissionsForDate(date): Flow<List<DailyMission>>`,
  `fun observeClaimableCount(date): Flow<Int>`.
- `MilestoneRepository`: `fun observeClaimedMilestoneIds(): Flow<Set<String>>` (covers both Home + Missions
  consumers, which only read `.claimed`/`.milestoneId` — no entity, no new domain model needed).
- `WeeklyChallengeRepository`: `suspend getLastNWeeks(limit): List<WeeklyChallenge>` (impl maps via the
  existing `toDomain()`).
- `DailyLoginRepository`, `StepRepository`: **no additions** (call sites already covered).

### Domain models
- `WeeklyChallenge` (#227) already carries `weekStartDate`/`totalSteps`/`claimedTier` — exactly what the
  dashboard reads. The `?: WeeklyChallengeEntity(weekStartDate=…)` fallback becomes
  `?: WeeklyChallenge(weekStartDate=…)` (default `claimedTier=0` matches). `EconomyUiState`/`WeeklyResult`
  are already clean presentation models — the leak is purely VM-internal (entity import + iteration).
- No new domain model is needed for the milestone Flow: `observeClaimedMilestoneIds(): Flow<Set<String>>`
  is the minimal behavior-preserving shape (both consumers compute a claimed-id set today).

### #229 / DAO-port cross-check
All 13 DAOs now have a port EXCEPT `BillingReceiptDao`, whose only production caller is `BillingManagerImpl`
(`data/billing/`) behind the `BillingManager` port — never presentation. So it already honors the
"DAO-direct only in the data layer" rule; the ADR records this rather than adding a redundant port.

### Tests (rewiring cost — all fakes already exist from #227)
- `CurrencyDashboardViewModelTest` — **highest**: 3 Mockito DAO mocks → `FakeWeeklyChallengeRepository` /
  `FakeDailyLoginRepository` / `FakeStepRepository`; the `.thenReturn(WeeklyChallengeEntity(...))` stubs
  become fake-state setup with domain types.
- `HomeViewModelTest` / `MissionsViewModelTest` — already wrap fake DAOs in fake repos; drop the raw DAO
  ctor args, add the new port methods to the fakes (delegating to the backing fake DAO).
- `WorkshopViewModelTest` / `BattleViewModelTest` — swap the one `mock<DailyMissionDao>()` → fake repo;
  BattleViewModelTest keeps its `mock<AppDatabase>()` + `runInTransaction` pass-through overrides (seam stays).

## What this wave delivers
1. **Add the 4 port methods** (Mission ×2, Milestone ×1, WeeklyChallenge ×1) + impls (map entities→domain at
   the boundary; `observe*` delegate to the DAO Flows + `.map`).
2. **Implement those methods on the 3 fake repositories** (spec-review F4): `FakeMissionRepository` /
   `FakeMilestoneRepository` wrap a backing fake DAO, so `observe*` delegate via `.map{}` over the DAO's
   `MutableStateFlow` (reactive, matches prod). `FakeWeeklyChallengeRepository` is a plain in-memory map with
   **no** backing DAO, so its `getLastNWeeks(limit)` is implemented directly
   (`values.sortedByDescending { weekStartDate }.take(limit)`) + a seeding helper.
3. **Reroute the 5 ViewModels:**
   - `CurrencyDashboardViewModel` — inject `WeeklyChallengeRepository` + `DailyLoginRepository` +
     `StepRepository` (drop the 3 DAOs + the `WeeklyChallengeEntity` import); map `WeeklyChallenge`→`WeeklyResult`.
   - `HomeViewModel` — drop `dailyMissionDao`/`milestoneDao`; use `missionRepository.observeClaimableCount` +
     `milestoneRepository.observeClaimedMilestoneIds`.
   - `MissionsViewModel` — drop `dailyMissionDao`/`milestoneDao`/`dailyStepDao` **and the stale unused
     `PlayerProfileDao` import (`:9`)** (spec-review F3 — it would otherwise trip the acceptance grep); use
     the new observe* + the existing `getMissionsForDate`/`sumCreditedSteps`/`updateProgress`.
     **NOT a mechanical swap (spec-review F1, major):** the VM currently reads the raw `m.missionType` String
     (`:109` display mapping `DailyMissionType.entries.find { it.name == … }?.description ?: type`; `:197`
     progress lookup) — `DailyMission` exposes `type: DailyMissionType`, not the string. The mapping MUST be
     rewritten to `m.type.description` (drop the `find{}`) and `m.type` / `m.type.category` for the progress
     lookup. This rewrite IS the #219 fix for this VM.
   - `WorkshopViewModel` — drop `dailyMissionDao`; use `missionRepository`.
   - `BattleViewModel` — drop `dailyMissionDao`; use `missionRepository` for the end-of-round mission writes.
     **Keep** `appDatabase` + `runInTransaction` (documented exception).
4. **Update the 3 ports' KDoc** (they currently say "getByDate Flow / countClaimable / getAll stay on the raw
   DAO, tracked as #219") to reflect that those reads now go through the port.
5. **ADR** recording the persistence-abstraction rule (#229) + the two documented exceptions (BattleViewModel
   `withTransaction` seam; BillingReceiptDao data-internal).
6. **Rewire the 5 ViewModel tests** to fakes.

## Acceptance criteria
- `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL.
- **No `*Dao` or `AppDatabase` import remains in `presentation/`** EXCEPT `BattleViewModel`'s `AppDatabase`
  (the documented seam): `grep -rE "import .*data\.local\.\w+Dao|import .*data\.local\.AppDatabase" presentation/`
  → only the `BattleViewModel` AppDatabase line.
- **No Room `@Entity` import in `presentation/`:**
  `grep -rE "import .*data\.local\.\w+Entity" presentation/` → empty.
- All 5 ViewModel tests pass with **unchanged expected values** (only construction changed: DAO/mock → fake repo).
- Reactive behavior preserved: Home/Missions screens still update on mission/milestone Room emissions
  (the new port methods return `Flow`). Verify via the existing VM tests that assert re-emission.
- No schema change; `#252 AtomicDaoConcurrencyTest` + `DomainPurityTest` still green.

## Risks
- **R1 — reactivity regression (highest).** If a new `observe*` port method is implemented as a suspend
  snapshot (or the impl breaks the Flow chain), Home/Missions stop updating live. Mitigation: impls are
  `dao.flowMethod().map { … }`; keep the VM `combine()` arity/shape identical; the VM tests that drive
  `advanceUntilIdle()` and assert post-emission state are the guard.
- **R2 — entity-leak whack-a-mole.** The grep acceptance criteria (no `*Dao`/`*Entity`/`AppDatabase` imports
  in presentation, minus the one seam) is the objective check that the leak is actually gone, not just moved.
- **R3 — CurrencyDashboard mock→fake fidelity.** Its test stubs entities via Mockito; the fake repos must be
  seeded with the same domain values so the dashboard math (powerStonesForTier, weekly totals) is unchanged.
- **R4 — scope creep into the transaction seam.** Explicitly out of scope (documented exception). Do not
  restructure the BattleViewModel end-of-round fan-out.

## Open questions (resolve in plan/review, do not pre-decide)
- **OQ1 — milestone Flow shape:** `observeClaimedMilestoneIds(): Flow<Set<String>>` (minimal, both consumers
  compute a claimed set) vs a richer `observeAll(): Flow<List<Milestone-state>>` (needs a new domain model).
  Default: the `Set<String>` shape (no new model). Confirm both call sites only need claimed ids.
- **OQ2 — presentation-purity guard:** worth adding a `PresentationPurityTest` (no `*Dao`/`AppDatabase`/`*Entity`
  imports in `presentation/`, allowlisting the BattleViewModel seam) to machine-enforce #219 like #228 did for
  the domain? Default: propose it; decide in review (could be a tiny add or deferred).

## Review note (Adversarial Review Gate)
Ultracode is OFF → this spec is unreviewed. Flag to the developer and ask (a) ultracode-on full Gate,
(b) lighter single-agent review, or (c) proceed — before advancing spec→plan. Do not skip silently.
