# Strategic Refactoring Opportunities

*Standard Analysis Phase 14 — Part 1 of 2. Names the highest-ROI
refactors that enable safer, faster, and more reliable future
development. Each entry is a self-contained work package with
current pattern, proposed abstraction, benefits, effort, risk +
mitigation, ROI, first safe step, verification, and rollback.*

**Scope.** Code refactoring opportunities — structural improvements
to existing code or cross-cutting abstractions — not feature
implementation, not content work, not doc hygiene, not Plan 31
external tasks. Release-blockers live in the companion roadmap
(`implementation_roadmap.md`). Items on this list are candidates
for **post-v1.0 quality investment** except where noted as
"enables release-blocker verification".

**Source-of-truth rule.** Code at HEAD `a9d0386` is authoritative
per global rule #1. Every entry cites a specific file:line or a
grep-verifiable fact. Where prior archaeology phases already have
full write-ups, this document cross-references rather than
duplicates (global rule #3).

**Input phases.**
- Phase 4 `devdocs/archaeology/5_things_or_not.md` — 5 PR-sized
  improvement bets with file citations, risk, rollback, verification.
- Phase 8 `devdocs/archaeology/architecture_analysis.md` +
  `module_discovery.md` — structural findings, forbidden-direction
  imports, fat/thin modules.
- Phase 10 `devdocs/evolution/gap_analysis.md` — current-vs-desired
  state comparison, release gates.
- Phase 11 `devdocs/evolution/gap_closure_plan.md` — phased
  execution plan with per-item PR boundaries.
- Phase 12 `smoke_tests/check_what_is_working/report.md` — baseline
  test run, classpath gap finding.
- Phase 13 `devdocs/archaeology/cleanup_inventory.md` — removal /
  consolidation / quarantine inventory.

**What this document is not.**
- Not a schedule (that is `implementation_roadmap.md`).
- Not an ADR (no architectural decision is made here).
- Not exhaustive of every possible refactor — focused on the ~10
  with the highest payback per effort.
- Not a deletion list — see `cleanup_inventory.md` for that.

**ROI scoring.** Each entry estimates ROI as *high / medium / low*
based on: (a) breadth of downstream payback (how many future
features benefit), (b) inverse of effort (smaller PR = higher ROI),
and (c) risk profile (additive refactors rank higher than
cross-layer rewrites).

**Effort scale.**
- **XS** — 1–3 hour single-file change.
- **S** — 0.5–1 day, 1–2 files.
- **M** — 2–4 days, 3–6 files, may span 2 PRs.
- **L** — 1–2 weeks, multi-PR sequence.
- **XL** — structural multi-week refactor (none in this list;
  rewrites rejected in gap_analysis §5).

---

## TL;DR — ranked by ROI

| # | Refactor | Quality attr. | Effort | ROI | Release-gating? |
|---|---|---|---|---|---|
| RO-01 | `TimeProvider` abstraction (narrow migration) | Testability, reproducibility | S | High | No |
| RO-02 | `@Transaction` for currency-mutating multi-writes | Correctness | M | High | No |
| RO-03 | Resilient `BattleViewModel.endRound` | Reliability | S | High | No |
| RO-04 | Extract `FollowOnPipeline` from `DailyStepManager` | Maintainability | M | High | No |
| RO-05 | `UpdateMissionProgress` use case (single entry) | Maintainability, layer hygiene | M | High | No |
| RO-06 | `Screen.fromRoute` — deep-link coverage for 12 routes | Reliability | XS | High | No |
| RO-07 | Cosmetic rendering pipeline contract | Feature enablement | M | High | Blocks R2-11 re-enablement |
| RO-08 | Configurable fake failure modes (Billing / Ads) | Release-gate safety | S | High | **Enables M2 / M3** |
| RO-09 | `junit-vintage-engine` on test classpath | Test coverage recovery | XS | High | No |
| RO-10 | `PreferencesStore` consolidation (6 wrappers) | Maintainability | M | Medium | No |

**Bundle suggestions** (several are naturally paired):
- RO-01 composes with RO-02 (both land in first refactor sprint).
- RO-03 composes with RO-02 site #4 (`endRound` cascade).
- RO-04 unblocks RO-05 (mission-progress sites include the
  `DailyStepManager` walking path that moves into `FollowOnPipeline`).
- RO-07 depends on the `ClaimMilestone.Cosmetic` detection fix
  (covered in roadmap Phase C).

---

## RO-01. `TimeProvider` abstraction (narrow migration, 3 call sites)

### Current problematic pattern

The codebase has **53 direct `System.currentTimeMillis()` /
`LocalDate.now()` / `Instant.now()` calls across 33 files** (Phase 4
§1 grep count; re-verified in Phase 8). The existing pattern is
"default parameter on each use case":

```kotlin
// domain/usecase/AwardBattleSteps.kt:27
suspend operator fun invoke(
    enemyType: EnemyType,
    today: String = LocalDate.now().toString(),   // ← pinned at dispatch time
): Int
```

Default parameters cover use cases the author remembers to
parameterise but **fail for VM tickers**. Two VMs run
`while (true) { delay(1000) }` loops
(`presentation/labs/LabsViewModel.kt`,
`presentation/missions/MissionsViewModel.kt`) that read wall time
directly; they cannot be driven by `StandardTestDispatcher` today
because `LocalDate.now()` ignores the virtual clock.

Midnight-boundary hazards this hides (Phase 4 §1 detail; gap_analysis §3.3):

- `DailyStepManager.todayDate()` reads at dispatch time, not event
  time — late-day sensor deltas credit to the next day.
- `StepCrossValidator` keys escrow by `"yyyy-MM-dd"`; DST shifts the
  boundary.
- `AwardBattleSteps` reading `LocalDate.now()` mid-round can reset
  the 2,000/day cap if DST changes during a run.
- `BattleViewModel.endRound:168` reads `LocalDate.now().toString()`
  for mission lookup; crossing midnight misses the played-day's
  mission.

No user has reported any of these; all four are **latent**.

**Paths:** 33 files across `domain/usecase/`, `data/sensor/`,
`data/healthconnect/`, and `presentation/*/` (see Phase 4 §1 for
full list).

### Proposed abstraction

Introduce a pure-Kotlin interface in `domain/time/` and a system
impl in `data/time/`:

```kotlin
// domain/time/TimeProvider.kt
interface TimeProvider {
    fun nowEpochMs(): Long
    fun today(): String  // ISO LocalDate string
}

// data/time/SystemTimeProvider.kt
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMs() = System.currentTimeMillis()
    override fun today() = LocalDate.now().toString()
}
```

Bind via a new `di/TimeModule.kt`. Keep the existing
default-parameter pattern on use cases (ADR-0003 convention) —
`TimeProvider` is an *additive* boundary, not a replacement.

**Scope constraint:** migrate **only three call sites** in the first
PR — `AwardBattleSteps`, `BattleViewModel.endRound` line 168,
`MissionsViewModel` ticker. The other 50 stay on
`LocalDate.now()`.

### Benefits

- **Testability** — `FakeTimeProvider` (one new file in `test/fakes/`)
  can drive synthetic midnight boundaries, which are currently
  untestable. Lowest-coverage concept area (55% per Phase 9 §23).
- **Reproducibility** — satisfies global rule #4 for any future
  feature that needs time injection.
- **Determinism** — removes one of the two wall-clock unknowns in
  the 200 ms polling loop (the other is `nanoTime()`, intentionally
  left out — see Non-goals).
- **Layer hygiene** — `domain/time/` stays pure Kotlin (verified by
  `grep -r 'import android' domain/time/` staying empty).

### Effort estimate

**S** (0.5–1 day, 3 PRs, ~80 new lines total).

- PR 1: interface + impl + Hilt binding (no existing code changes).
- PR 2: migrate three call sites (each keeps default-arg fallback
  so current tests don't break).
- PR 3: `FakeTimeProvider` + one new test per migrated site.

### Risk assessment + mitigation

- **Risk:** scope creep — reviewer or author "finishes" the
  migration in the same PR and touches 33 files.
  **Mitigation:** PR description explicitly lists the three target
  files and rejects others; 50 other call sites are out of scope
  per Phase 4 §1 boundary.
- **Risk:** adding an Android dependency to `domain/`.
  **Mitigation:** interface is pure Kotlin; impl lives in `data/`.
  `grep -r 'import android' app/src/main/java/com/whitefang/stepsofbabylon/domain/`
  must stay empty after the PR.
- **Risk:** behaviour change from default-arg to injected.
  **Mitigation:** each migrated site keeps the default arg /
  nullable fallback pattern; existing tests unchanged.

### ROI justification

**High.** Single shared abstraction unlocks testing of four latent
correctness bugs (listed above) plus the two ticker-based VMs,
at the cost of ~80 lines and three mechanical migrations. Every
future date-sensitive feature inherits the seam for free.

### First safe step

PR 1 of Phase 4 §1 — `domain/time/TimeProvider.kt` + `data/time/SystemTimeProvider.kt`
+ `di/TimeModule.kt`. No existing code changes. Binding is unused
until PR 2.

### Verification strategy

1. `./run-gradle.sh testDebugUnitTest` stays green (412 → 412 in
   PR 1; 412 → 415 in PR 3 with three new midnight-boundary tests).
2. `grep -r 'import android' app/src/main/java/com/whitefang/stepsofbabylon/domain/`
   returns empty.
3. Post-PR 2: each of the three migrated call sites has a new test
   that exercises a synthetic midnight boundary — currently
   untestable.

### Rollback plan

Per-PR revert. PR 1 alone is dead code if reverted. PR 2 revert
restores default-arg behaviour. PR 3 is tests only. No user-facing
behaviour changes at any stage.

### Non-goals

- **Do not migrate the remaining 50 sites** (Phase 4 §1 explicit
  boundary; opportunistic migration over time is fine).
- **Do not include `nanoTime()` in the interface** (Phase 10 §6.2
  item 8 — `GameLoopThread` stays on direct `nanoTime()` per
  existing accepted trade-off).
- **Do not change ADR-0003** (default-parameter pattern remains
  documented convention for use cases).

---

## RO-02. `@Transaction` for currency-mutating multi-writes

### Current problematic pattern

**Zero `@Transaction` or `withTransaction` uses in
`app/src/main`** (grep-verified in Phase 4 §2 and re-verified in
Phase 8). The "balances never go negative" invariant holds only
because of atomic `UPDATE ... SET col = MAX(0, col + :delta)`
single-row SQL clamps. The architectural invariant "game-state
writes must be atomic at the DAO level"
(`devdocs/archaeology/foundations/known_requirements.md` §2) is
**violated at five sites**:

1. **`domain/usecase/AwardBattleSteps.kt:31-37`** — `addSteps(credited)`
   on one DAO, then `incrementBattleSteps(today, credited)` on another.
2. **`domain/usecase/PurchaseUpgrade.kt:20-22`** — `spendSteps(cost)`
   then `setUpgradeLevel(type, newLevel)`.
3. **`data/healthconnect/StepCrossValidator.kt:60-65`** and parallel
   branches at 69-73, 77-90 — `spendSteps(excess)` then
   `updateEscrow(date, excess, newSync)`.
4. **`presentation/battle/BattleViewModel.kt:144-184`** —
   `updateBestWave` → `awardWaveMilestone` → `updateHighestUnlockedTier`
   (three separate writes, no wrapper).
5. **`domain/usecase/ClaimMilestone.kt`** — profile-credit +
   `milestoneDao.markClaimed`.

**User-visible symptom:** a crash between writes produces silent
state divergence. The clamp hides the symptom until the player
notices "why did I get charged but not levelled up?" or "my Steps
went down but the escrow counter didn't move — I got cheated."

### Proposed abstraction

Per-site atomic DAO methods. Keep transactions inside `data/local/`
(no cross-layer `RoomDatabase` import into domain):

```kotlin
// data/local/WorkshopDao.kt — pattern from Phase 4 §2
@Transaction
suspend fun purchaseUpgradeAtomic(
    type: String,
    newLevel: Int,
    cost: Long,
    playerDao: PlayerProfileDao,
): Boolean {
    val rowsUpdated = playerDao.adjustStepBalanceIfSufficient(cost)
    if (rowsUpdated == 0) return false
    setUpgradeLevel(type, newLevel)
    return true
}

// data/local/PlayerProfileDao.kt
@Query("UPDATE player_profile SET currentStepBalance = currentStepBalance - :cost " +
       "WHERE id = 1 AND currentStepBalance >= :cost")
suspend fun adjustStepBalanceIfSufficient(cost: Long): Int
```

For site #3 (`StepCrossValidator`) the validator is in
`data/healthconnect/` so it can legally `AppDatabase.withTransaction { ... }`
around each parallel branch — this is the one place where the
cross-layer `RoomDatabase` import is acceptable.

For site #4 (`endRound`) the transaction wraps the persistence
function extracted by RO-03.

### Benefits

- **Correctness** — closes 5 documented partial-failure windows.
- **Race-closure** — the `WHERE ... >= :cost` SQL guard (site #2)
  also closes a latent double-tap race (Phase 4 §2 Risk section):
  two concurrent purchase clicks can no longer cause double-spend.
- **Convention** — establishes the `@Transaction` pattern for every
  future multi-write, so new features inherit the correctness for
  free.

### Effort estimate

**M** (~1 week calendar, 5 PRs — one per site).

- PR 1 (`PurchaseUpgrade`) — proves the pattern.
- PR 2 (`AwardBattleSteps`) — composite DAO method on DailyStepDao.
- PR 3 (`StepCrossValidator`) — `withTransaction` wrapping three
  parallel branches.
- PR 4 (`ClaimMilestone`) — composite DAO method on MilestoneDao.
- PR 5 (`endRound`) — transaction wrapper around RO-03's extracted
  persistence function. Gated on RO-03.

### Risk assessment + mitigation

- **Risk:** the SQL guard in site #2 changes semantics from
  read-then-deduct to "close the race". This is a behaviour change.
  **Mitigation:** Phase 4 §2 notes this is net-positive; verify
  with two new concurrency tests (success + insufficient) before
  removing the old path.
- **Risk:** partial-failure simulation is tricky in JVM tests.
  **Mitigation:** each PR adds one success test + one
  partial-failure test by throwing from inside the transaction.
- **Risk:** site #4 depends on RO-03 landing first.
  **Mitigation:** Phase 11 I2 → I3 → I4 ordering enforces this.

### ROI justification

**High.** 5 correctness fixes in one refactor family. The pattern
is one-time setup that pays down every future multi-write. Each
PR is independently shippable and reviewable.

### First safe step

Phase 4 §2 PR 1 — add `adjustStepBalanceIfSufficient` + `purchaseUpgradeAtomic`;
wire `PurchaseUpgrade.invoke` through the atomic path. Keep the
old read-then-write path behind a `@Deprecated` fallback for one
PR cycle; delete in the follow-up.

### Verification strategy

Per-PR:

1. `./run-gradle.sh testDebugUnitTest` stays green; each PR adds
   ≥2 new tests (success + partial-failure).
2. Manual smoke: debug APK; perform the currency-mutating action;
   confirm single-increment / single-spend via Room inspector.
3. Post-series: `grep -c "withTransaction\|@Transaction" app/src/main`
   returns ≥5.

### Rollback plan

Per-PR revert. PR 1's `@Deprecated` fallback means rolling back PR 1
alone is functionally a feature flag flip. Each later PR is
independent of the earlier ones at the revert-safety level (the
common abstraction — the `@Transaction` annotation — is already
established by PR 1).

### Non-goals

- Do **not** introduce a global `TransactionRunner` abstraction.
  Room's per-DAO `@Transaction` is the recommended pattern.
- Do **not** migrate currency single-row deltas (`addSteps`, etc.)
  — they are already atomic via SQL clamp and need no transaction.

---

## RO-03. Resilient `BattleViewModel.endRound`

### Current problematic pattern

**Mid-battle navigation silently discards the round.**

`presentation/battle/BattleViewModel.kt` has three fragility issues
(Phase 4 §3; trace 08 §8; trace 10 §9):

1. `onCleared()` nulls the `onStepReward` callback but does **not**
   run `endRound()`. Deep-links fired mid-round (e.g.
   `navigate_to=supplies` from a supply-drop notification) replace
   the Battle route; the VM is cleared; round state is lost.
   `updateBestWave`, `awardWaveMilestone`, and
   `updateHighestUnlockedTier` are skipped. Daily-mission battle
   progress is lost. User-invisible.
2. `endRound()` wraps three critical writes **without** `try/catch`.
   A single exception (e.g. a transient Room IO error) skips
   `_uiState` update, leaving the player on a frozen battle screen
   with no post-round overlay.
3. `quitRound()` and the polling loop both call `endRound()` and
   rely on a function-local `roundEnded` guard to dedupe. Any new
   call path would have to replicate the guard.

### Proposed abstraction

Extract a pure-persistence function and call it from a scope that
survives VM cancellation (Phase 4 §3):

```kotlin
// presentation/battle/BattleViewModel.kt
private suspend fun runEndRoundPersistence(engine: GameEngine) {
    runCatching { updateBestWave(engine.wavesCleared) }
        .onFailure { Log.w(TAG, "updateBestWave failed", it) }
    runCatching { awardWaveMilestone(engine.wavesCleared) }
        .onFailure { Log.w(TAG, "awardWaveMilestone failed", it) }
    runCatching { playerRepository.updateHighestUnlockedTier(...) }
        .onFailure { Log.w(TAG, "updateHighestUnlockedTier failed", it) }
}

override fun onCleared() {
    super.onCleared()
    val eng = engine
    if (eng != null && !roundEnded && eng.hasWaveProgress()) {
        eng.roundOver = true
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            runEndRoundPersistence(eng)
        }
    }
    eng?.onStepReward = null
}
```

`quitRound()` and the polling loop also call `runEndRoundPersistence`,
deduplicating behaviour.

### Benefits

- **Reliability** — mid-battle navigation now correctly saves the
  round per `quitRound()` semantics (player earned it, it counts).
- **Debuggability** — three previously-silent persistence errors
  now log at `Log.w`. Matches the R2-07 pattern for
  `StepSyncWorker` catches.
- **Composability with RO-02** — extracted function is exactly the
  unit of work that RO-02 site #4 wraps in a transaction.

### Effort estimate

**S** (2–3 days, 2 PRs).

- PR 1 — extract + `try/catch` (~60 new lines).
- PR 2 — `onCleared` guard using `ProcessLifecycleOwner.lifecycleScope`.

### Risk assessment + mitigation

- **Risk:** `ProcessLifecycleOwner` outlives the VM but not the
  process. A background kill between `launch` and first write still
  loses the round.
  **Mitigation:** RO-02 PR 5 wraps the writes in a transaction,
  turning 3 atomicity gaps into 1.
- **Risk:** behaviour change — a user who intentionally aborts via
  deep-link now has their wave counted.
  **Mitigation:** Phase 4 §3 judges this net-positive; matches
  existing `quitRound()` semantics.

### ROI justification

**High.** Closes one user-invisible data-loss bug, gains logging
of three previously-silent errors, enables RO-02 site #4, all with
~60 lines of extraction. No new dependency (`ProcessLifecycleOwner`
already transitively available via `androidx.lifecycle`).

### First safe step

PR 1 — extract `runEndRoundPersistence` and wrap each of the 3
writes in `runCatching { } .onFailure { Log.w }`. Both `endRound()`
and `quitRound()` now call the extracted function. No `onCleared`
change yet.

### Verification strategy

1. New `BattleViewModelTest` cases:
   - Simulate `onCleared()` mid-round; assert `updateBestWave`
     called exactly once.
   - Throw from `updateBestWave`; assert `awardWaveMilestone` is
     not called and VM does not propagate.
2. Manual smoke: start battle, reach wave 5, press system back;
   confirm Stats reflects the run.
3. `./run-gradle.sh testDebugUnitTest` stays green.

### Rollback plan

Per-PR revert. Either PR reverts cleanly to the current behaviour.

### Non-goals

- Do **not** rewrite the round state machine.
- Do **not** add a "resume round" feature. Quit or nav-away both
  **end** the round.

---

## RO-04. Extract `FollowOnPipeline` from `DailyStepManager`

### Current problematic pattern

`data/sensor/DailyStepManager.kt` is the step-ingestion god class.
It has **12 constructor parameters** (Phase 8 §3, Phase 4 §4)
spanning two repositories, four DAOs, three anti-cheat components,
one walking-encounter repo + notifier, and a widget update helper.
It mixes two responsibilities:

- **Primary:** anti-cheat-gated crediting pipeline
  (rate limit → velocity → ceiling → persist + activity minutes).
- **Secondary:** 5-stage follow-on fan-out after successful credit:
  widget refresh, supply drop generation, daily-login tracking,
  weekly-challenge tracking, walking-mission progress.

`runFollowOnPipeline` contains **4 pokemon-catch blocks**
(`try { ... } catch (_: Exception) { }`) that swallow errors
silently (Phase 4 §4; cleanup §A10). R2-07 fixed the analogous
`StepSyncWorker` catches but not these.

It also causes the **Season Pass bonus leak** (Phase 8 §3,
gap_analysis §3.8): the pipeline calls `TrackDailyLogin(...)`
without `hasSeasonPass` / `hasAdRemoval` flags, so Season-Pass
owners who cross the 1,000-step threshold while the app is closed
miss the +10 Gems bonus. `HomeViewModel.init` passes the flags
correctly, so behaviour depends on whether the app is open when the
threshold is crossed — a user-invisible inconsistency.

### Proposed abstraction

Mechanical extraction into a new `@Singleton`, no behaviour change:

```kotlin
// data/sensor/FollowOnPipeline.kt
@Singleton
class FollowOnPipeline @Inject constructor(
    private val walkingEncounterRepository: WalkingEncounterRepository,
    private val supplyDropNotificationManager: SupplyDropNotificationManager,
    private val dailyLoginDao: DailyLoginDao,
    private val weeklyChallengeDao: WeeklyChallengeDao,
    private val dailyMissionDao: DailyMissionDao,
    private val widgetUpdateHelper: WidgetUpdateHelper,
    private val playerRepository: PlayerRepository,
) {
    private var dropState: DropGeneratorState = DropGeneratorState()

    suspend fun run(
        context: Context,
        date: String,
        dailyCreditedTotal: Long,
        activityMinuteTotal: Long,
    ) { /* body moved from DailyStepManager.runFollowOnPipeline */ }
}
```

`DailyStepManager` loses 6 of its 12 constructor params (they move
to the pipeline). The `lazy { TrackDailyLogin(...) }` and
`lazy { TrackWeeklyChallenge(...) }` built inline move with the
pipeline — restoring the use-case-as-constructor-param convention.

### Benefits

- **Maintainability** — any new step-triggered reward now touches
  one class, not two. `DailyStepManager`'s surface shrinks.
- **Testability** — split `DailyStepManagerTest` into two files
  (`DailyStepManagerTest` + `FollowOnPipelineTest`); each side
  preserves original assertions.
- **Layer hygiene** — removes 4 of the 12 forbidden-direction
  imports (Phase 8 §8) because the pipeline owns the DAO references
  that previously leaked up into presentation VMs.
- **Prerequisite for RO-05** — `FollowOnPipeline` becomes the
  single call site to `TrackDailyLogin`, which lets RO-05
  centralise mission progress too.
- **Fixes Season Pass leak cleanly** — pipeline reads profile
  flags once, passes to `TrackDailyLogin` uniformly.

### Effort estimate

**M** (~1 week, 4 PRs).

- PR 1 — mechanical extraction. `DailyStepManager` loses 6 deps.
- PR 2 — `UpdateMissionProgress` use case (RO-05 PR 1).
- PR 3 — migrate remaining 4 mission-progress sites to RO-05.
- PR 4 — remove the Season Pass flags tactical patch (if shipped
  ahead as a quick win) — `FollowOnPipeline` owns the single call
  site.

### Risk assessment + mitigation

- **Risk:** behaviour parity. The five follow-on stages may have
  subtle ordering dependencies.
  **Mitigation:** zero-change extraction. `DailyStepManagerTest`
  must pass verbatim under the new composition; any failing
  assertion blocks merge.
- **Risk:** `dropState` ownership changes from `DailyStepManager`
  to `FollowOnPipeline`.
  **Mitigation:** both are `@Singleton`; app-lifetime is identical.
  Flag in PR 1 for reviewer.
- **Risk:** ~500-line test split.
  **Mitigation:** mechanical — each assertion moves verbatim; new
  `FollowOnPipelineTest` file.

### ROI justification

**High.** Same effort as RO-02 per-PR with broader downstream
payback: enables RO-05, fixes Season Pass leak, shrinks a 12-dep
god class, restores convention, adds observability via per-stage
`Log.w` (cleanup §A10).

### First safe step

Phase 4 §4 PR 1 — extract `FollowOnPipeline.kt` with identical
method bodies. `DailyStepManager.runFollowOnPipeline` replaced by
`followOnPipeline.run(...)`. Existing `DailyStepManagerTest`
assertions split verbatim into two files.

### Verification strategy

1. `./run-gradle.sh testDebugUnitTest` stays green.
2. Post-PR 1: `DailyStepManagerTest` + new `FollowOnPipelineTest`
   together match pre-extraction assertion count.
3. Post-PR 3: `grep -rn 'import.*data.local.*DailyMissionDao' app/src/main/java/com/whitefang/stepsofbabylon/presentation/`
   returns 0 hits.
4. Post-PR 4: `grep -rn 'observeProfile' app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/`
   returns 0 hits (pipeline is single caller).

### Rollback plan

Per-PR revert. Mechanical extraction reverts cleanly because each
PR is behaviour-preserving.

### Non-goals

- Do **not** extract beyond the five-stage fan-out. The rate-limit
  → velocity → ceiling → persist chain stays inside
  `DailyStepManager` (Phase 4 §4 boundary).
- Do **not** unify `Reward` sealed hierarchy (Phase 10 §3.5 —
  explicitly deferred).
- Do **not** change `dropState`'s lifetime (`@Singleton`).

---

## RO-05. `UpdateMissionProgress` use case (single entry)

### Current problematic pattern

Mission progress is updated from **5 sites** (Phase 8 §3):

- `presentation/battle/BattleViewModel.kt` (battle missions)
- `presentation/labs/LabsViewModel.kt` (research missions)
- `presentation/workshop/WorkshopViewModel.kt` (upgrade missions)
- `presentation/missions/MissionsViewModel.kt` (midnight regen +
  walking step re-check)
- `data/sensor/DailyStepManager.kt` (walking missions)

Each site calls `DailyMissionDao` or a scattered progress-update
flow. Two of them (`Workshop`, `Labs`) are **forbidden-direction
imports** (Phase 8 §8 — presentation reaching into
`data.local.*Dao`).

### Proposed abstraction

A single use case that owns read + clamp + write:

```kotlin
// domain/usecase/UpdateMissionProgress.kt
class UpdateMissionProgress @Inject constructor(
    private val dailyMissionRepository: DailyMissionRepository,
) {
    suspend operator fun invoke(category: MissionCategory, delta: Int) {
        dailyMissionRepository.incrementProgressAtomic(category, delta)
    }
}
```

The repository method wraps in `@Transaction` (composes with RO-02).
Each of the 5 sites switches to calling the use case.

### Benefits

- **Maintainability** — one call site for mission progress.
- **Layer hygiene** — removes 2 of the 12 forbidden-direction
  imports (one from `WorkshopViewModel`, one from `LabsViewModel`).
- **Correctness** — atomic read + clamp + write closes the same
  partial-failure gap as RO-02.
- **Composes with RO-04** — `DailyStepManager` site gets migrated
  as part of the `FollowOnPipeline` extraction.

### Effort estimate

**M** (2–4 days, 3 PRs).

- PR 1 — new use case + new DAO method (`incrementProgressAtomic`).
  Wire `DailyStepManager` walking-mission path.
- PR 2 — migrate `BattleViewModel` + `LabsViewModel` +
  `WorkshopViewModel`.
- PR 3 — migrate `MissionsViewModel` midnight-regen path.

### Risk assessment + mitigation

- **Risk:** missed progress update if old and new paths coexist.
  **Mitigation:** one PR per site; each site fully migrates in its
  PR.
- **Risk:** mission regen at midnight has subtle ordering with
  follow-on pipeline.
  **Mitigation:** Phase 10 §2.4 confirms the ordering is local to
  `MissionsViewModel` — no cross-VM coupling.

### ROI justification

**High.** Removes 2 forbidden imports, closes mission-progress
partial-failure gap, centralises future mission category additions.

### First safe step

PR 1 — add `domain/usecase/UpdateMissionProgress.kt` + matching DAO
method. Wire `DailyStepManager` only (one call site). Old paths in
other 4 VMs remain.

### Verification strategy

1. New tests: per category, success + partial-failure cases.
2. `./run-gradle.sh testDebugUnitTest` stays green.
3. Post-series: `grep -rn 'DailyMissionDao' app/src/main/java/com/whitefang/stepsofbabylon/presentation/`
   returns 0 hits.

### Rollback plan

Per-PR revert. Each PR is a single call-site migration.

### Non-goals

- Do **not** introduce a `MissionCategory` sealed hierarchy change
  (existing enum is fine).
- Do **not** migrate mission generation (only progress updates).

---

## RO-06. `Screen.fromRoute` — deep-link coverage for 12 routes

### Current problematic pattern

`presentation/MainActivity.kt` has a `pendingNavigation:
MutableStateFlow<String?>` collector that handles only **5 of 12
routes** (Home, Workshop, Battle, Missions, Supplies). The other
7 (Store, Stats, Weapons, Cards, Economy, Settings, Labs) silently
fall through to Home.

Four notification managers produce `navigate_to` intent extras
(`StepNotificationManager`, `SupplyDropNotificationManager`,
`MilestoneNotificationManager`, `SmartReminderManager`). Any future
`navigate_to=store` (e.g. from a "Season Pass expiring" reminder)
lands on Home with no error. No test asserts coverage.

### Proposed abstraction

Add a `Screen.fromRoute` factory + allow-list of routes that
don't require arguments:

```kotlin
// presentation/navigation/Screen.kt
companion object {
    fun fromRoute(name: String?): Screen? = when (name) {
        Home.route -> Home
        Workshop.route -> Workshop
        Labs.route -> Labs
        // ... all 12 routes
        else -> null
    }

    val argumentFreeRoutes: Set<String> = setOf(
        Home.route, Workshop.route, Labs.route,
        Stats.route, Weapons.route, Cards.route,
        Supplies.route, Economy.route, Missions.route,
        Settings.route, Store.route,
        // Battle.route excluded: requires tier context
    )
}

// presentation/MainActivity.kt collector
Screen.fromRoute(name)
    ?.takeIf { it.route in Screen.argumentFreeRoutes }
    ?.let { navController.navigate(it.route) }
```

### Benefits

- **Reliability** — closes silent-drop of 7 route strings.
- **Compile-time-ish safety** — `fromRoute` is exhaustive; adding a
  new screen forces a `when` update.
- **Minimal change** — one function + one collector branch.

### Effort estimate

**XS** (half-day, 1 PR, ~30 lines).

### Risk assessment + mitigation

- **Risk:** a `navigate_to=battle` deep-link without tier context
  could crash `BattleScreen` init.
  **Mitigation:** the `argumentFreeRoutes` allow-list excludes
  Battle; Battle stays on the current 5-route whitelist.
- **Risk:** future typed-route migration (Phase 10 §2.3) makes this
  obsolete.
  **Mitigation:** `fromRoute` is a clear stepping stone; adopting
  typed routes later deletes it in one PR.

### ROI justification

**High per unit effort.** ~30 lines unlocks 7 notification surfaces.

### First safe step

One PR: add `Screen.fromRoute` + `argumentFreeRoutes` + update
collector. Extend `DeepLinkRoutingTest` with 7 new cases for
previously-unhandled routes.

**Dependency:** this test file currently uses JUnit 4 +
`@RunWith(RobolectricTestRunner)` and is silently skipped by the
current classpath (see RO-09). Land RO-09 first so the new tests
actually run.

### Verification strategy

1. 7 new test cases in `DeepLinkRoutingTest`.
2. Manual smoke: `am start -a ... --es navigate_to store` intent;
   confirm StoreScreen opens.
3. `./run-gradle.sh testDebugUnitTest` stays green (after RO-09 the
   baseline includes these tests).

### Rollback plan

Revert. Collector returns to the 5-route `when`.

### Non-goals

- Do **not** adopt typed routes (Phase 10 §2.3 defers until routes
  grow past 12).
- Do **not** refactor navigation graph structure.

---

## RO-07. Cosmetic rendering pipeline contract

### Current problematic pattern

The cosmetic system has three disconnected parts:

- **Data** — `CosmeticEntity` + `CosmeticDao` +
  `CosmeticRepositoryImpl` + `SEED_COSMETICS` (7 placeholders) +
  owned/equipped state on `PlayerProfileEntity`.
- **UI** — `StoreScreen` + `StoreViewModel` allow buy/equip/unequip,
  but R2-11 greyed out the purchase button with "Coming Soon"
  because the renderer is missing.
- **Renderer** — `GameEngine` / `ZigguratEntity` /
  `ProjectileEntity` / `EnemyEntity` have **no awareness of
  equipped cosmetics at all**. No `BiomeTheme.kt` override pathway.

Three `MilestoneReward.Cosmetic` declarations reference IDs that
**do not match** any `SEED_COSMETICS` entry (Phase 8 §3; cleanup
§B3): `Milestone.kt` names `garden_ziggurat_skin`,
`lapis_lazuli_skin`, `sandals_of_gilgamesh`; `SEED_COSMETICS`
seeds `zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`,
`proj_lightning`, `enemy_shadow`, `enemy_neon`.
`ClaimMilestone.kt:25` silently drops the cosmetic reward type.

**This is a missing contract, not a broken one** (gap_analysis
§5.2). The monetization feature is **shipped and disabled**.

### Proposed abstraction

Pass-through override map from profile → engine → entities:

```kotlin
// presentation/battle/engine/GameEngine.kt
@Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()
    private set

fun applyCosmetics(equippedIds: List<String>, repo: CosmeticRepository) {
    cosmeticOverrides = repo.resolveEquipped(equippedIds)
        .associateBy { it.category }
}

// ZigguratEntity already takes layerColors: IntArray in its constructor;
// BattleViewModel selects override.colors if ZIGGURAT category is present
// before passing to the entity.
```

The constructor-parameter pattern is already data-driven
(`ZigguratEntity.layerColors`); only `BattleViewModel` +
`GameEngine` need plumbing.

### Benefits

- **Feature enablement** — unblocks re-enabling the R2-11 guard in
  `StoreScreen` for one cosmetic end-to-end.
- **Additive** — default `emptyMap()` preserves current rendering
  exactly.
- **Content-scalable** — the remaining 6 seeded + 3 milestone
  cosmetics become content PRs (new seed rows + colour constants)
  once the pipeline exists.
- **Fixes Milestone.Cosmetic silent drop** — paired with a
  detection fix in `ClaimMilestone`
  (`Result.UnknownCosmetic` sealed variant; roadmap Phase C).

### Effort estimate

**M** (1 week for pipeline; content PRs ongoing).

- PR 1 — `GameEngine.cosmeticOverrides` + plumbing from profile to
  `ZigguratEntity.layerColors`. Pure additive; default preserves
  behaviour.
- PR 2 — seed one cosmetic end-to-end (e.g. jade ziggurat); remove
  R2-11 guard for that single ID.
- PR 3+ — content work: remaining 6 seeded + 3 milestone cosmetics.

### Risk assessment + mitigation

- **Risk:** renderer changes regress the battle screen.
  **Mitigation:** PR 1 is pure-additive with `emptyMap()` default.
  A VM-level test asserts "no cosmetic equipped → identical colours
  to today". Manual smoke on debug APK.
- **Risk:** product decision on *which* cosmetic ships first
  (Phase 10 §6.2 unknown #2).
  **Mitigation:** propose jade ziggurat default per gap_analysis
  §5.2; any one-colour-swap cosmetic will do.
- **Risk:** `ClaimMilestone.Cosmetic` still silently drops in PR 1.
  **Mitigation:** roadmap Phase C pairs PR 1 with the detection fix.

### ROI justification

**High.** Single structural gap blocks a shipped-but-disabled
feature. Additive. The 3 milestone cosmetic IDs + 7 seeded
placeholders become meaningful the moment the pipeline lands.

### First safe step

PR 1 — add `GameEngine.cosmeticOverrides` + hydrate from profile in
`BattleViewModel.onBattleStart`. No UI changes, no seed changes.
Tests assert "empty overrides → identical colours to today".

### Verification strategy

1. Unit test: `GameEngine.cosmeticOverrides` maps correctly from
   `equippedCosmeticIds`.
2. Robolectric VM test: equip jade → `onBattleStart` → ziggurat
   colours match override.
3. Manual smoke: equip in Store (after PR 2), start battle, see
   the jade tint.

### Rollback plan

PR 1 additive — revert restores `emptyMap()` default. PR 2 re-hides
the purchase button. PR 3+ delete seed rows.

### Non-goals

- Do **not** animate cosmetics (gap_analysis §6.2 item 7 —
  deferred).
- Do **not** extend beyond `ZIGGURAT` category in PR 1; additional
  categories are content in PR 3+.
- Do **not** re-enable all cosmetic purchases in PR 1 — only the
  one validated ID in PR 2.

---

## RO-08. Configurable fake failure modes (Billing / Ads)

### Current problematic pattern

`app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeBillingManager.kt`
and `FakeRewardAdManager.kt` **always return success**. No test
exercises:

- `PurchaseResult.Cancelled`, `Failed`, `Pending`
- Billing service disconnected
- `AdResult.Failed`, `Skipped`, reward callback not fired
- Receipt verification rejected

When Plan 31 Task 4 lands the real Google Play Billing SDK +
AdMob, there is **zero regression net for failure paths** (Phase 9
§19 risk 5, §24 risk 5). The stub-then-swap pattern (Phase 8 §5)
was explicitly chosen to keep the swap cheap — but the swap is
only cheap if the test harness covers both halves.

### Proposed abstraction

Add configurable result properties and cover each sealed variant
with tests:

```kotlin
class FakeBillingManager : BillingManager {
    var nextResult: PurchaseResult = PurchaseResult.Success(...)
    override suspend fun purchase(product: BillingProduct) = nextResult
}

class FakeRewardAdManager : RewardAdManager {
    var nextAdResult: AdResult = AdResult.Rewarded
    override suspend fun showAd(placement: AdPlacement) = nextAdResult
}
```

New tests in `StoreViewModelTest` and `CardsViewModelTest` cover
each variant.

### Benefits

- **Release-gate safety** — real SDK swap (M2/M3 in roadmap) has
  a regression net for every documented failure path.
- **Parity with production** — tests exercise every sealed variant
  declared in `BillingProduct.kt` / `AdPlacement.kt`.
- **Zero risk** — fakes are test-only code.

### Effort estimate

**S** (1 day, 1 PR, test-only change).

### Risk assessment + mitigation

- **Risk:** none (test-only).

### ROI justification

**High. Enables M2 and M3 (Plan 31 real SDK swap) to ship with
confidence.** Without this, the real SDK has no partial-failure net
and any regression after release is discovered by users, not CI.

### First safe step

One PR. Add `nextResult` / `nextAdResult` mutable properties; add
3 tests in `StoreViewModelTest` (Cancelled, Failed, Pending) and 2
in `CardsViewModelTest` (Failed, Skipped).

### Verification strategy

1. New tests **are** the verification — 5+ new cases across
   `StoreViewModelTest` and `CardsViewModelTest`.
2. `./run-gradle.sh testDebugUnitTest` grows by ≥5 tests and stays
   green.

### Rollback plan

Revert. Fakes return to always-succeed.

### Non-goals

- Do **not** start the real SDK swap — that is Plan 31.
- Do **not** add new domain types — `PurchaseResult` and `AdResult`
  sealed classes already enumerate the variants.

---

## RO-09. `junit-vintage-engine` on test classpath

### Current problematic pattern

`app/build.gradle.kts` sets `unitTests.all { it.useJUnitPlatform() }`.
The JUnit Platform discovers tests via engines on the classpath.
The project has `junit-jupiter:5.11.4` (Jupiter engine) and
`junit-platform-launcher:1.11.4`, and **transitively**
`junit:junit:4.13.2` via `org.robolectric:junit:4.14.1`, but **no
`junit-vintage-engine`**. Result: JUnit 4-style tests annotated
`@RunWith(RobolectricTestRunner::class)` + `org.junit.Test` are
**silently not discovered**.

Affected files (cleanup §C1, smoke report §Area 4):

- `app/src/test/java/com/whitefang/stepsofbabylon/data/local/RoomSchemaTest.kt` — 3 `@Test`
- `app/src/test/java/com/whitefang/stepsofbabylon/service/StepWidgetProviderTest.kt` — 3 `@Test`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/DeepLinkRoutingTest.kt` — 3 `@Test`

Total: **up to 9 tests silently skipped**. The smoke report
originally claimed 6 (2 files × 3) but the cleanup inventory
flagged `DeepLinkRoutingTest` uses the same pattern, bringing the
total to 9. The STATE.md-claimed "412 tests" is always based on
what runs, not what exists.

### Proposed abstraction

One-line additive dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}
```

Alternative (longer-term, preferred for uniformity): port the three
files to JUnit 5 + `@ExtendWith(RobolectricExtension::class)`.

### Benefits

- **Coverage recovery** — up to 9 tests start running again.
- **Unblocks RO-06** — new `DeepLinkRoutingTest` cases that RO-06
  adds will actually run.
- **Makes schema round-trip (`RoomSchemaTest`) observable** —
  currently schema is only validated at build-time via
  `copyRoomSchemas` and at runtime via Room's `IllegalStateException`.
- **Zero downside** — vintage engine is stable, additive only.

### Effort estimate

**XS** (hour-scale, 1 PR).

### Risk assessment + mitigation

- **Risk:** the 9 tests might fail on first run (latent
  failures never observed).
  **Mitigation:** smoke report §"What is broken but acceptable"
  documents that these tests were passing on CI in a prior
  configuration; most likely they still pass.
  If any fail, fix in the same PR or mark `@Disabled` with a
  citation.

### ROI justification

**High per unit effort.** One line, up to 9 tests recovered,
prerequisite for RO-06 verification.

### First safe step

Add the one-line dependency. Re-run `testDebugUnitTest`; expect
412 → ~418–421 tests, all green.

### Verification strategy

1. Classpath audit: `./run-gradle.sh :app:dependencies --configuration debugUnitTestRuntimeClasspath | grep vintage`
   now returns a match.
2. `./run-gradle.sh testDebugUnitTest --rerun-tasks` shows new
   test count.
3. JUnit XML reports for `RoomSchemaTest`, `StepWidgetProviderTest`,
   `DeepLinkRoutingTest` exist with tests > 0.

### Rollback plan

Revert the one-line dependency. Tests return to being silently
skipped.

### Non-goals

- Do **not** port the 3 files to JUnit 5 in the same PR — that is
  the alternative long-term approach; do it as a separate cleanup
  PR if desired.

---

## RO-10. `PreferencesStore` consolidation (6 SharedPreferences wrappers)

### Current problematic pattern

Six separate SharedPreferences wrappers (cleanup §A8; Phase 8 §4
virtual `M8 prefs` module):

1. `data/BiomePreferences.kt`
2. `data/NotificationPreferences.kt`
3. `data/SoundPreferences.kt`
4. `data/MilestoneNotificationPreferences.kt`
5. `data/anticheat/AntiCheatPreferences.kt`
6. `data/sensor/StepIngestionPreferences.kt`

All open `context.getSharedPreferences("<name>", MODE_PRIVATE)` and
expose typed getters/setters. No shared base class. Different naming
conventions. `presentation/battle/GameSurfaceView.kt:26` **bypasses
`SoundPreferences` and reads `sound_prefs` inline** (Phase 8 §3).

### Proposed abstraction

Either:

- **Option A (consolidate)** — single `data/prefs/PreferencesStore.kt`
  with a key registry (typed constants) and a unified
  `get<T>(key)` / `set<T>(key, value)` API. Each existing wrapper
  becomes a thin domain-specific view over the store.
- **Option B (document the convention)** — keep the 6 files as-is;
  add `data/prefs/README.md` explaining the "one wrapper per
  concern" convention; fix the `GameSurfaceView.kt:26` leak; add a
  shared base class with `context.getSharedPreferences(name, mode)`
  indirection to enforce consistency.

Phase 10 §2 and Phase 11 I7 deliberately left the decision open.

### Benefits

- **Maintainability** — 6 files → 1 + 6 thin views; OR 6 files +
  enforced convention.
- **Closes the `GameSurfaceView` leak** — either option requires
  fixing the bypass.
- **Future-proofing** — any 7th preference category drops into a
  known pattern.

### Effort estimate

**M** (2–4 days depending on option).

- Option A: ~200 lines moved, 6 consumer refactors.
- Option B: ~50 lines added (base class + README), 1 consumer fix.

### Risk assessment + mitigation

- **Risk:** preference keys **must** be preserved byte-for-byte
  across the refactor or users lose stored state.
  **Mitigation:** enumerate every `getSharedPreferences(...)` call
  + each wrapper's key constants into a migration table; verify
  with a Robolectric test that reads a pre-refactor file name +
  key and gets the expected value.
- **Risk:** decision overhead — which option?
  **Mitigation:** Phase 11 I7 explicitly left this as a
  product/architecture decision. Propose Option B (documented
  convention) as the cheaper default; promote to Option A if a
  second leak like `GameSurfaceView.kt:26` appears.

### ROI justification

**Medium.** Not blocking anything; delivers readability + leak
fix. Lower than RO-01 through RO-09 in payback per unit effort.
Listed here for completeness because Phase 8 called this out by
name.

### First safe step

Option B PR 1 — add `data/prefs/PreferencesBase.kt` abstract class
providing `context.getSharedPreferences(fileName, MODE_PRIVATE)` +
a short `data/prefs/README.md`. Migrate one wrapper to extend it
(e.g. `SoundPreferences`). Fix the `GameSurfaceView.kt:26` bypass
in the same PR.

### Verification strategy

1. New Robolectric test: write a preference via old path, read via
   new path, assert equal (per-wrapper).
2. `./run-gradle.sh testDebugUnitTest` stays green.
3. Manual smoke: debug APK preserves toggles after uninstall +
   reinstall (Auto Backup is disabled, so this only verifies same-
   install persistence).

### Rollback plan

Per-PR revert. Each wrapper migration is independent.

### Non-goals

- Do **not** migrate SharedPreferences to DataStore in the same
  refactor. DataStore is a separate migration that would require
  its own ADR.
- Do **not** consolidate in an "all-at-once" PR — one wrapper per
  PR per Phase 11 PR-size convention.

---

## Deferred / lower-ROI refactors (appendix)

Flagged for completeness; not in the top-10 on ROI grounds. Each
has a Phase 13 cleanup-inventory entry or a Phase 10 gap-analysis
entry; cross-reference for details.

| Refactor | Source | Why deferred |
|---|---|---|
| `GameEngine` stat-snapshot stack (replace `preOverdriveStats` + `preGoldenStats` pair) | cleanup §A5 | Works today; only a trap for future additions. Pair with a new BattleViewModelTest when next touched. |
| `StepCrossValidator` Level 0/1 branch dedup | cleanup §A4 | ~20-line cosmetic cleanup; bundle with RO-02 site #3. |
| `StepRepositoryImpl.releaseEscrow`/`discardEscrow` merge | cleanup §A3 | Behaviourally identical today; requires product decision on whether they **should** diverge. |
| `PlayerWallet.cardDust` field addition | cleanup §B7 | Minor API hygiene; unblocks uniform wallet observation fan-out. |
| `UltimateWeaponLoadout` + `CardLoadout` collapse to per-entity `equipped: Boolean` only | cleanup §A2, §B5 | Typed loadouts are unused at runtime; deletion is safe but requires product decision on whether typed loadouts are intended future contract. |
| `Reward` sealed hierarchy unification | gap_analysis §3.5 | Explicitly rejected until a fourth reward type ships (revisit trigger documented). |
| Multi-module Gradle split (`:domain`, `:data`, `:presentation`) | gap_analysis §5.1 | Explicitly rejected for solo developer; revisit when a second engineer joins. |
| Typed-route Navigation Compose migration | gap_analysis §2.3 | Deferred until routes grow past 12. RO-06 is the minimal replacement. |
| `di/HealthConnectModule.kt` delete or document | cleanup §A6 | Empty placeholder; leave-alone until a real HC provider lands. |
| SharedPreferences → DataStore migration | N/A (not flagged) | Separate ADR scope; Auto Backup is already disabled so persistence semantics are fine. |

---

## Meta — relationship to prior phases

- **Phase 4 "5 Things" ⊂ this list.** Items RO-01 through RO-05
  are the Phase 4 proposals promoted to roadmap ROI ranking.
  Phase 4 has the detailed risk / rollback / verification per item.
- **Phase 8 structural findings → RO-05, RO-07, RO-10.** The
  forbidden-direction imports and fat/thin-module critique map
  directly to mission-progress extraction, cosmetic pipeline, and
  preferences consolidation.
- **Phase 10 gap_analysis §2 (architecture changes) ⊂ this list.**
  §2.1 → RO-02, §2.2 → RO-01, §2.3 → RO-06, §2.4 → RO-05, §2.5 →
  RO-04.
- **Phase 11 gap_closure_plan** is the *scheduled* version of these
  opportunities plus quick-wins; the companion
  `implementation_roadmap.md` promotes Phase 11 entries into
  release-critical phases A/B/C/D.
- **Phase 12 smoke_tests report §"What is broken but acceptable"**
  → RO-09.
- **Phase 13 cleanup_inventory** §A–§F enumerates candidates by
  risk; RO-10 and the appendix deferred items come from there.

---

*End of Phase 14 refactoring opportunities (Part 1 of 2). Companion
document: `implementation_roadmap.md`. Written 2026-05-06 against
HEAD `a9d0386`; no code modified this session.*
