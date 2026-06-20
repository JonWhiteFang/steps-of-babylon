# ADR-0034: Restore the domain→data dependency rule + machine-enforce it (#227/#228)

## Context
- The Clean-Architecture invariant (`presentation → domain ← data`; "domain has zero Android
  dependencies") was broken at the **dependency-direction** level: 9 domain use cases imported the
  data layer (Room DAOs, `@Entity` types, concrete `SystemTimeProvider`). The domain compiled against
  persistence, so it could not be reused/tested without the data layer and a Room schema change rippled
  into "pure" domain code (#227).
- `DomainPurityTest` cited everywhere as the machine-enforced guarantee only scanned Android-framework
  prefixes, so the domain→data violation sailed through — **documented assurance the architecture was
  intact when it was not** (#228). The violation had accreted across 9 files unnoticed.

## Decision
- Introduce 3 domain models (`DailyMission`, `DailyLogin`, `WeeklyChallenge`) and 4 repository ports
  (`MissionRepository`, `MilestoneRepository`, `DailyLoginRepository`, `WeeklyChallengeRepository`),
  and extend `StepRepository` with the battle/boss atomic-credit + `sumCreditedSteps` methods. All Room
  access for the 9 use cases now lives in `data/repository/*Impl`; the use cases depend only on domain
  interfaces + domain models.
- **Atomic-passthrough pattern for repo-wrapped `@Transaction` methods:** the Milestone + Step impls
  `@Inject` both their own DAO **and** `PlayerProfileDao`, and hand the real `PlayerProfileDao` into the
  DAO's `@Transaction` method. Because `DatabaseModule` provides every DAO off the single `@Singleton
  AppDatabase`, the wallet credit stays inside the one DB-scoped Room transaction (Room's transaction
  tracker is DB-scoped, not DAO-scoped). The guarded-deduct invariant (#122/ADR-0020/ADR-0027) is
  preserved verbatim — no credit moved outside its transaction/guard.
- **Use cases stay hand-built (no `@Inject` in domain)** so domain remains DI-framework-agnostic;
  `DomainPurityTest` gains a second assertion forbidding `dagger.`/`javax.inject.` imports to lock that.
- **Strengthen `DomainPurityTest`** (#228): add `com.whitefang.stepsofbabylon.data` to the forbidden
  import prefixes (the dependency-direction guard) + the DI-agnostic assertion.
- **Scope bound:** ViewModels that read DAOs directly (e.g. `DailyMissionDao.countClaimable`,
  `MilestoneDao.getAll()` Flow, `WeeklyChallengeDao.getLastNWeeks`) keep those raw-DAO calls — that is
  presentation→data, permitted by the arrows and tracked separately as ARCH-2 (#219). The new ports
  cover only the **use-case** surface, keeping #227 decoupled from #219.

## Alternatives considered
- **`@Inject` the use cases** (a use-case DI module): rejected — pulls `javax.inject` into `domain/`
  (breaks DI-agnosticism) and adds a provision strategy for no benefit here; the consumers already
  hand-build them.
- **Reimplement the atomic credit in the repo** (repo calls `playerRepository.addGems` etc. outside the
  DAO transaction): rejected — loses the single-transaction guarantee, reopening the partial-failure +
  double-credit windows #122 closed.
- **Route presentation DAO calls through the ports too** (fold #219 into this PR): rejected — balloons
  the port surface and couples two issues; left for #219.
- **A full AST/Konsist purity check** instead of the import-line scan: rejected — adds a dependency; the
  import-line scan covers every real case (documented limitation: inline FQ refs without an import would
  evade it; none exist today).

## Consequences
- Positive: the dependency rule holds and is now machine-enforced at the direction level — a future
  domain→data import fails the build naming the file. Domain is reusable/testable without the data layer.
  Entity↔domain mapping is centralized in the impls (e.g. unknown `missionType` strings are null-skipped,
  not `valueOf`-thrown).
- Negative / tradeoffs: more types (3 models + 4 ports + 4 impls + 4 fake repos). Residual inline clock
  reads (`LocalDate.now()`/`System.currentTimeMillis()`) remain in a few use cases where threading a
  `TimeProvider` would have widened scope (they are `java.time`, not data imports — not a #227/#228
  violation); `ClaimMilestone` did adopt the injected `TimeProvider`.
- Behavior-preserving: every use-case test passed with unchanged expected values (only construction
  changed: fake DAO → fake repo). `#252 AtomicDaoConcurrencyTest` (independent atomic backstop) stays green.

## Links
- Commit(s): (this PR — closes #227, #228)
- Related ADRs: ADR-0012 (Simulation extraction), ADR-0020 / ADR-0027 (atomic guarded-deduct), ADR-0030.
