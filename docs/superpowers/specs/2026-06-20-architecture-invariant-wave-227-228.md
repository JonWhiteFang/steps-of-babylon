# Spec — Architecture-invariant wave: #227 domain→data dependency-rule fix · #228 guard

**Date:** 2026-06-20 · **Branch:** `arch/domain-purity-227-228` · **Target:** `[Unreleased]`
**Issues:** #227 (severity:major, architecture, effort:large) · #228 (severity:major, architecture,
effort:small) — both from the 2026-06-18 complete-app review, both adversarially confirmed (3 refuters,
3✓/0~/0✗). **Combined PR** (developer decision); **full domain models** for the entity-leak cases
(developer decision).

## Goal

Restore the Clean-Architecture dependency rule (`presentation → domain ← data`) at the **dependency-direction**
level, not just the Android-import level:

1. **#227** — 9 domain use cases illegally import the data layer (Room DAOs, Room `@Entity`, concrete
   `SystemTimeProvider`). Introduce the missing repository **ports** in `domain/repository/`, move all Room
   access into `data/repository/*Impl`, and have the use cases depend only on domain interfaces + domain
   models. The domain must compile with zero `com.whitefang.stepsofbabylon.data` imports.
2. **#228** — `DomainPurityTest` only forbids Android-framework prefixes, so the domain→data violation sailed
   through while the project documented the architecture as machine-enforced. Add
   `com.whitefang.stepsofbabylon.data` to `forbiddenPrefixes` so the guard actually enforces the dependency
   rule. (By design this fails until #227 is complete — that coupling is why they ship together.)

**Non-goals.** No behavior change (no schema/economy/engine-formula change; every credit/claim/generate path
keeps identical observable behavior). Not converting use cases to `@Inject` unless needed (they're hand-built
today; keep that unless a port change forces it — see Open Questions). Not touching the `DailyStepManager`
data-layer-constructs-use-cases second-order concern beyond what's needed to rewire constructors (flag it,
don't fix it here — that's ARCH-adjacent, separate). No new DI module for use cases.

## Constraints / invariants (must hold)

- **Atomic guarded-deduct invariant (ADR-0020/0027, #122) — PRESERVE VERBATIM.** The `@Transaction` methods
  `creditBattleStepsAtomic`, `creditBossPowerStonesAtomic`, `claimMilestoneAtomic` each take a
  `playerDao: PlayerProfileDao` that is *passed into the transaction*. Room's transaction tracker is
  **DB-scoped, not DAO-scoped**, so the repository impl wrapping these MUST inject the real `PlayerProfileDao`
  and hand it into the atomic call — the credit must still happen inside the one transaction. Same for
  `DailyMissionDao.generateForDate` (`@Transaction` batch + `(date,missionType)` unique index +
  `onConflict=IGNORE`) and `markClaimed` (guarded `UPDATE … WHERE claimed=0`, mark-first). Do not collapse,
  re-order, or move these writes out of their transaction/guard.
- **No `app/src/main/` behavior change.** Pure structural move: DAO calls relocate behind interfaces; logic is
  identical. Entity ↔ domain-model mapping happens only in `data/repository/*Impl`.
- **Domain stays DI-framework-agnostic.** #228 optionally also asserts no `dagger.`/`javax.inject` in domain;
  domain is currently clean of those — preserve it (do NOT add `@Inject` to domain use-case constructors if it
  would introduce `javax.inject` into `domain/`). Repository **impls** (in `data/`) keep `@Inject`.
- Fragile zones intact: no touch to `GameEngine`/`Simulation`/`EffectEngine`.

## Grounding (verified file:line facts)

### The violation — 9 use cases (`domain/usecase/`)
| Use case | Data imports | DAO calls (atomic?) | Entity in body? | Built at |
|---|---|---|---|---|
| `AwardBattleSteps` | DailyStepDao, PlayerProfileDao, SystemTimeProvider | `creditBattleStepsAtomic` (**atomic**, passes playerDao) | no | `BattleViewModel.kt:114` |
| `AwardBossPowerStones` | DailyStepDao, PlayerProfileDao, SystemTimeProvider | `creditBossPowerStonesAtomic` (**atomic**) | no | `BattleViewModel.kt:115` |
| `CheckMilestones` | MilestoneDao | `getAllOnce()` (read) | no (returns `List<Milestone>`) | `HomeViewModel.kt:88` |
| `ClaimMilestone` | MilestoneDao, PlayerProfileDao (+ already uses PlayerRepository, CosmeticRepository) | `claimMilestoneAtomic` (**atomic**) | no | `MissionsViewModel.kt:56-61` |
| `ClaimMission` | DailyMissionDao (+ PlayerRepository) | `getByDateOnce` (read), `markClaimed` (**guarded**) | no | `MissionsViewModel.kt:55` |
| `GenerateDailyMissions` | DailyMissionDao, **DailyMissionEntity** | `generateForDate` (**@Transaction batch**) | **builds `List<DailyMissionEntity>`** | `HomeViewModel.kt:85,139`, `MissionsViewModel.kt:54` |
| `TrackDailyLogin` | DailyLoginDao, **DailyLoginEntity** (+ PlayerRepository) | `getByDate` (read), `upsert` (write) | **read-copy-write `DailyLoginEntity`** | `HomeViewModel.kt:84`, `DailyStepManager.kt:131` |
| `TrackWeeklyChallenge` | WeeklyChallengeDao, DailyStepDao, **WeeklyChallengeEntity** (+ PlayerRepository) | `sumCreditedSteps` (read), `getByWeek` (read), `upsert` (write) | **read-copy-write `WeeklyChallengeEntity`** | `DailyStepManager.kt:132` |
| `UpdateCompleteResearchMissionProgress` | DailyMissionDao | `getByDateOnce` (read), `updateProgress` (write) | no | `LabsViewModel.kt:47`, `HomeViewModel.kt:69` |

### Existing patterns to mirror
- **Ports** in `domain/repository/`: Card, Cosmetic, Lab, Player, Step, UltimateWeapon, WalkingEncounter,
  Workshop. **Impls** in `data/repository/*Impl` — e.g. `StepRepositoryImpl` (`@Inject constructor(dao)`,
  maps `Entity.toDomain()` privately, delegates each method to the DAO). `PlayerRepositoryImpl` already
  surfaces the guarded-deduct contract as `Boolean`-returning methods.
- **Hilt:** `di/RepositoryModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class` with
  `@Binds @Singleton abstract fun bindXRepository(impl: XRepositoryImpl): XRepository` per repo. New ports add
  a `@Binds @Singleton` line each.
- **TimeProvider:** `domain/time/TimeProvider` interface already exists; `data/time/SystemTimeProvider`
  (`@Singleton @Inject`) is already Hilt-bound in `di/TimeModule.kt`. The only `SystemTimeProvider` leak is the
  `= SystemTimeProvider()` **default arg** in `AwardBattleSteps`/`AwardBossPowerStones`.
- **DAO/port gap:** PlayerProfileDao→PlayerRepository ✓, DailyStepDao→StepRepository ✓ (but `StepRepository`
  does NOT yet expose `creditBattleStepsAtomic`/`creditBossPowerStonesAtomic`/`sumCreditedSteps`).
  **MilestoneDao, DailyMissionDao, DailyLoginDao, WeeklyChallengeDao have NO port — 4 new ports needed.**
- **Entities (no domain model today):** `DailyMissionEntity(id, date, missionType, target, progress,
  rewardGems, rewardPowerStones, completed, claimed)`; `DailyLoginEntity(date, stepsWalked, powerStoneClaimed,
  gemsClaimed)`; `WeeklyChallengeEntity(weekStartDate, totalSteps, claimedTier)`. `Milestone`/`MilestoneReward`
  domain models already exist (only `MilestoneEntity` is the persistence row — `CheckMilestones` already
  returns `List<Milestone>`).
- **Tests:** all 9 use-case tests hand-build with **fake DAOs** (`FakeDailyMissionDao`, `FakeMilestoneDao`,
  `FakeDailyLoginDao`, `FakeWeeklyChallengeDao`, `FakeDailyStepDao`) — no Room, no Hilt. `FakeDailyStepDao` /
  `FakeMilestoneDao` take an optional `linkedPlayer: FakePlayerRepository?` and override the atomic methods to
  route credits to it (ignoring the passed playerDao). **4 new fake repositories needed**, carrying that
  atomic-credit-routing logic.

## What this wave delivers

### New domain models (`domain/model/`)
- `DailyMission` — `id, type: DailyMissionType, date, target, progress, rewardGems, rewardPowerStones,
  completed, claimed` (covers every field the use cases read: `ClaimMission` reads id/completed/claimed/
  rewardGems/rewardPowerStones; `UpdateCompleteResearchMissionProgress` reads progress/target). Mapped from/to
  `DailyMissionEntity` in the impl. **`missionType` String↔enum mapping failure mode (spec review F5):** the
  entity stores `missionType: String` (enum `.name`); `DailyMissionType.valueOf` THROWS on an unknown/legacy
  string. The impl's entity→domain map MUST handle this safely — **drop the unmappable row** (filter it out)
  rather than throw, so a stale/renamed mission type can't crash a mission read. (Use `entries.find { it.name
  == … }` → null-skip, not `valueOf`.)
- `DailyLogin` — `date, stepsWalked, powerStoneClaimed, gemsClaimed`.
- `WeeklyChallenge` — `weekStartDate, totalSteps, claimedTier`.
- (Reuse existing `Milestone`/`MilestoneReward`/`DailyMissionType`/`MissionCategory`.)

### New repository ports (`domain/repository/`) + impls (`data/repository/`)
1. **`MissionRepository`** wraps `DailyMissionDao` — methods covering: read today's missions
   (`List<DailyMission>` by date), `generateForDate(date, missions: List<DailyMission>)` (impl maps → entities,
   calls `generateForDate` preserving the batch+IGNORE), `markClaimed(id): Int`/`Boolean`,
   `updateProgress(id, progress, completed)`. Consumers: `ClaimMission`, `GenerateDailyMissions`,
   `UpdateCompleteResearchMissionProgress` (+ the ViewModel direct calls `getByDate`/`countClaimable` — see
   Open Questions).
2. **`MilestoneRepository`** wraps `MilestoneDao` — `getAll(): List<...>` (claimed-id read for `CheckMilestones`),
   `claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt): Boolean` (impl injects `PlayerProfileDao`
   and hands it into the `@Transaction`). Consumers: `CheckMilestones`, `ClaimMilestone`.
3. **`DailyLoginRepository`** wraps `DailyLoginDao` — `getByDate(date): DailyLogin?`, `upsert(DailyLogin)`.
   Consumer: `TrackDailyLogin`.
4. **`WeeklyChallengeRepository`** wraps `WeeklyChallengeDao` — `getByWeek(weekStart): WeeklyChallenge?`,
   `upsert(WeeklyChallenge)`. Consumer: `TrackWeeklyChallenge`.
5. **Extend `StepRepository`** (or a focused new port) with `creditBattleStepsAtomic(date, requested, cap):
   Long`, `creditBossPowerStonesAtomic(date, requested, cap): Long`, `sumCreditedSteps(start, end): Long`.
   The impl (`StepRepositoryImpl`) injects `PlayerProfileDao` in addition to `DailyStepDao` and hands it into
   the atomic calls. Consumers: `AwardBattleSteps`, `AwardBossPowerStones`, `TrackWeeklyChallenge`.

> **Atomic passthrough (the load-bearing detail) — CONFIRMED CORRECT (spec review F1).** The impls for
> Milestone + Step crediting must `@Inject` both their own DAO **and** `PlayerProfileDao`, then call the DAO's
> `@Transaction` method with `playerDao = playerProfileDao`. `di/DatabaseModule.kt:32-44` provides every DAO as
> `db.xxxDao()` off the single `@Singleton AppDatabase`, so the injected `PlayerProfileDao` is the same
> DB-backed instance and the wallet credit stays inside the one DB-scoped Room transaction. Do NOT reimplement
> the credit in the repo outside the transaction. (DatabaseModule is the sole DAO provider — verify no second
> `@Provides` DB exists, then this holds.)

> **SCOPE BOUND — presentation→data stays (spec review F2/F3; resolves OQ4).** The new ports cover **only the
> surface the 9 USE CASES call**, NOT what ViewModels call directly. Many ViewModels inject these DAOs directly
> for their own reads/writes — `DailyMissionDao` in Battle/Labs/Home/Workshop/Missions VMs;
> `MilestoneDao.getAll()` Flow in Home/Missions VMs; `WeeklyChallengeDao.getLastNWeeks` + `DailyLoginDao` in
> CurrencyDashboard VM; etc. That is **presentation→data, which the documented arrows permit** and which
> `DomainPurityTest` (scans `domain/` only) does not flag; it is tracked separately as ARCH-2 (#219). This wave
> leaves every such direct ViewModel→DAO call **as-is** — a VM that both builds a use case and calls the DAO
> directly will, after this wave, inject **both** the new port (to construct the use case) and keep its raw DAO
> (for its own calls). This keeps the port surface bounded and #227 decoupled from #219. So:
> `WeeklyChallengeRepository` does NOT need `getLastNWeeks`; `MissionRepository` does NOT need `countClaimable`
> /`getByDate(Flow)` unless a use case calls them. Do **not** route presentation calls through ports in this PR.

### Use-case rewrites (`domain/usecase/`)
Each of the 9 swaps its data import(s) for the matching port + domain model; entity construction/mapping moves
into the impl. The 3 entity-builders (`GenerateDailyMissions`, `TrackDailyLogin`, `TrackWeeklyChallenge`) now
build/read **domain models**; the impl maps to/from entities. Drop the `= SystemTimeProvider()` default args
→ require a `TimeProvider` (already Hilt-bound); fold the residual direct clock reads (`System.currentTimeMillis()`
in `TrackDailyLogin`/`ClaimMilestone`, `LocalDate.now()` in `TrackWeeklyChallenge`/
`UpdateCompleteResearchMissionProgress`) through the injected `TimeProvider` where a `TimeProvider` is now in
scope (don't expand scope gratuitously — minimum to remove the data import + keep behavior).

### Wiring (4 ViewModels + DailyStepManager)
Update the hand-build sites (`BattleViewModel:114-115`, `MissionsViewModel:54-61`, `HomeViewModel:69,84,85,88,139`,
`LabsViewModel:47`, `DailyStepManager:131-132`) to pass the new ports instead of raw DAOs. The ViewModels/
DailyStepManager must obtain the new repositories — they already receive other repositories via Hilt, so add
the new ports to their constructors and bind in `RepositoryModule`. (If a consumer currently gets a raw DAO
only to feed a use case, replace that DAO param with the port.)

### #228 — strengthen the guard
`DomainPurityTest.kt:23` — add `"com.whitefang.stepsofbabylon.data"` to `forbiddenPrefixes`. Rename the test +
message to "domain has no Android AND no data-layer imports". The test must go GREEN only after all 9 use cases
are clean.
- **No false positives (spec review F7):** the existing `com.whitefang.stepsofbabylon.data.*` strings in domain
  (`PlayerRepository.kt:15`, `BillingManager.kt:22,40`, `BillingProduct.kt:13,18,24`) are all KDoc `[…]`
  doc-links, NOT `import` lines, so the prefix scan (matches only `startsWith("import ")`) won't flag them —
  expect grep to still show `data.` strings in domain KDoc after the fix; that's fine.
- **Known guard limitation (spec review F8):** the scan only catches `import` lines, so an inline
  fully-qualified `com.whitefang.stepsofbabylon.data.local.X` reference (no import) would evade it. No domain
  file does this today; accept + note as a known limitation (a full AST/Konsist check is out of scope).
- **`@Inject`/`javax.inject` assertion is COUPLED to OQ1 (spec review F9):** adding a `dagger.`/`javax.inject.`
  forbidden assertion would also block ever putting `@Inject` on a domain use-case constructor. Since OQ1's
  default keeps use cases hand-built (no `@Inject` in domain — confirmed: `grep javax.inject domain/` = none),
  this assertion is consistent and worth adding to lock in DI-agnosticism. If OQ1 flips to `@Inject`, drop it.

## Acceptance criteria
- `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL.
- `grep -rl "import com.whitefang.stepsofbabylon.data" app/src/main/java/com/whitefang/stepsofbabylon/domain/`
  returns **nothing**.
- `DomainPurityTest` GREEN with `data` in `forbiddenPrefixes`.
- All 9 use-case tests pass (rewired to fake repositories); the atomic-credit assertions
  (`FakeMilestoneDao.claimMilestoneAtomicCallCount`-style) still verify the atomic path.
- **Mutation check (#228):** temporarily re-add one `import com.whitefang.stepsofbabylon.data.local.…` to a
  domain file → `DomainPurityTest` FAILS naming that file+import. Restore.
- **Behavior-preservation check:** the existing use-case test assertions (credit amounts, cap behavior,
  streak math, mission generation, claim idempotency) pass unchanged — same inputs, same outputs. No test's
  *expected values* change; only its *construction* (fake repo vs fake DAO).
- No schema change (`docs/database-schema.md` untouched); no economy-formula change.

## Risks
- **R1 — atomic passthrough regression (highest).** If a repo reimplements credit outside the `@Transaction`,
  the guarded-deduct invariant breaks silently (tests may still pass if fakes don't model it). Mitigation: the
  impl calls the DAO's atomic method with the real `PlayerProfileDao`; keep/port the
  `claimMilestoneAtomicCallCount`-style assertions; the #252 `AtomicDaoConcurrencyTest` (file-backed Room) is
  an independent backstop and must stay green.
- **R2 — fake-repository fidelity (spec review F10).** New fake repos must reproduce the fake-DAO
  credit-routing (`linkedPlayer`) so the rewired tests assert the same behavior, AND preserve the
  `creditBattleStepsAtomicCallCount` / `claimMilestoneAtomicCallCount`-style exposure so the atomic-path
  assertions survive the rewire. `FakeDailyStepDao.kt` / `FakeMilestoneDao.kt` are the migration template (move
  their `linkedPlayer` routing + call-count counters into the corresponding fake repositories). A too-simple
  fake repo could make a test vacuously pass — guard against that with the call-count assertions.
- **R3 — ViewModel direct DAO use is OUT of scope (resolved, OQ4).** ViewModels keep their direct DAO calls
  (presentation→data, permitted by the arrows; tracked as #219). `DomainPurityTest` scans `domain/` only, so it
  stays green regardless. A VM that builds a use case AND calls the DAO directly injects both the port and the
  raw DAO after this wave. No presentation call is re-routed here.
- **R4 — scope creep.** 9 use cases + 4 ports + 3 models + 4 fakes + 5 wiring sites is large. Keep strictly to
  structural moves; resist "while I'm here" cleanups.

## Open questions (resolve in the plan/review, do not pre-decide)
- **OQ1 — `@Inject` for use cases?** They're hand-built today (fields / `by lazy` / inline). Switching to
  `@Inject` would pull `javax.inject` into `domain/` (which #228 may forbid) and need a use-case provision
  strategy. Default: **keep hand-built**, just swap DAO params for ports — minimal, keeps domain DI-agnostic.
  Confirm in plan.
- **OQ2 — extend `StepRepository` vs new crediting port?** Adding battle/boss atomic credit + `sumCreditedSteps`
  to `StepRepository` grows a cohesive port; a separate port is cleaner-segregated. Plan picks one.
- **OQ3 — `DailyStepManager` constructs domain use cases (data→domain "down" is fine, but it's a data class
  orchestrating domain).** In scope only for constructor rewire (pass ports). Flag the deeper smell; don't fix.
- **OQ4 — ViewModel direct DAO calls (R3).** Route through ports now, or leave for #219? Default leave + note.

## Review note (Adversarial Review Gate)
Ultracode is OFF. Per CLAUDE.md the Gate's multi-agent form is disabled by the opt-in rules — this spec is
**unreviewed**. This is a LARGE, behavior-sensitive refactor touching the guarded-deduct fragile zone, so the
review matters more than usual. Flag to the developer and ask whether to (a) turn ultracode on for the full
multi-agent Gate, (b) run a lighter single-agent review inline, or (c) proceed without one — before advancing
spec→plan. Do not skip silently.
