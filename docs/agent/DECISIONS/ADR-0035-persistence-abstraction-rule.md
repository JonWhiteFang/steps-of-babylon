# ADR-0035: Persistence-abstraction rule + presentation→data guard (#219/#229)

## Context
- After #227/ADR-0034 every Room table had a repository port EXCEPT `BillingReceiptDao`, but 5 ViewModels
  still injected DAOs directly (`BattleViewModel`, `MissionsViewModel`, `HomeViewModel`,
  `CurrencyDashboardViewModel`, `WorkshopViewModel`) and `CurrencyDashboardViewModel` constructed/iterated a
  raw `WeeklyChallengeEntity` — a persistence type leaking into the UI (#219). The repository pattern was
  applied inconsistently (~half the tables) with no documented rule for which features "deserve" an
  abstraction (#229), making the architecture unpredictable and blocking fakes for those flows.
- There was no machine guard for the presentation→data boundary (the analogue of `DomainPurityTest`/#228).

## Decision
1. **Rule:** every Room table has a repository **port** in `domain/repository/`. **DAO-direct access is
   confined to the data layer** — repository impls, and data-layer orchestrators (`DailyStepManager`,
   `BillingManagerImpl`) — **never the presentation layer.** Presentation depends only on ports.
2. Route all 5 ViewModels' DAO reads through ports (extending the #227 ports with the reactive/history
   reads they needed: `MissionRepository.observeMissionsForDate`/`observeClaimableCount`,
   `MilestoneRepository.observeClaimedMilestoneIds`, `WeeklyChallengeRepository.getLastNWeeks`). Map entities
   to domain models at the data boundary so no Room `@Entity` reaches a ViewModel.
3. **Machine-enforce it** with a new `PresentationPurityTest` (mirrors `DomainPurityTest`): fails on any
   `import …data.local.\w+Dao` / `data.local.AppDatabase` / `data.local.\w+Entity` in `presentation/`.
4. **Documented exception A — `BattleViewModel` `AppDatabase`/`withTransaction` seam.** The end-of-round
   write fan-out (use-cases + `playerRepository` + mission writes) is wrapped in one Room transaction via
   `runInTransaction { appDatabase.withTransaction { … } }`. This is a cross-repository atomicity boundary,
   not a 1:1 DAO read; moving it behind a port would mean restructuring the whole fan-out. It stays, and is
   the single allowlisted `AppDatabase` import in `PresentationPurityTest`.
5. **Documented exception B — `BillingReceiptDao` has no port.** Its only caller is `BillingManagerImpl`
   (data layer) behind the `BillingManager` port; it is never touched by presentation. That already honors
   the rule (DAO-direct confined to data), so a redundant `BillingReceiptRepository` port is not added.

## Alternatives considered
- **#229 via a "DAO-direct allowed" ADR with no routing** (option b in the issue): rejected — it still
  required moving presentation reads off DAOs to honor "never presentation", so it didn't shrink the work;
  routing + the rule is the consistent Clean-Architecture outcome.
- **Move the `withTransaction` seam behind a port** (a method taking the whole end-of-round computation):
  rejected for this wave — large, logic-adjacent change to the economy fragile zone; documented exception
  instead. Revisit if the fan-out is refactored.
- **Add a `BillingReceiptRepository`**: rejected — pure data-internal use; the port would have zero
  presentation/domain consumers.
- **`observeAll(): Flow<List<Milestone-state>>` domain model** for the milestone Flow: rejected — both
  consumers only need the claimed-id set; `observeClaimedMilestoneIds(): Flow<Set<String>>` is the minimal
  behavior-preserving shape (no new domain model).

## Consequences
- Positive: presentation→data boundary holds and is machine-enforced; a future DAO/entity import into a
  ViewModel fails the build. The persistence abstraction is now consistent + documented. Fakes back every
  presentation flow (the 5 VM tests run on fake repos, no Mockito DAO stubs).
- Negative / tradeoffs: 4 new port methods + fake impls; one allowlisted exception that a future reader must
  understand (documented here + in the guard's KDoc + a ctor comment).
- Behavior-preserving, with one accepted edge-case shift: a daily-mission row whose stored `missionType` no
  longer resolves to a `DailyMissionType` is now dropped from the list (the impl's `mapNotNull`, per #227)
  instead of rendering its raw id string. No test covered the old fallback; safe direction.

## Links
- Commit(s): (this PR — closes #219, #229)
- Related ADRs: ADR-0034 (domain→data dependency rule + the ports), ADR-0020/0027 (atomic guarded-deduct,
  the invariant the BattleViewModel transaction seam protects).
