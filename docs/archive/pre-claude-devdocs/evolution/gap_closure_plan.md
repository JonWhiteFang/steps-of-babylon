# Gap Closure Plan

*Standard Analysis Phase 11 — converts `devdocs/evolution/gap_analysis.md`
into an executable, phased plan. Nothing new is introduced here; every
item is cited back to its source in Phase 10 (gap_analysis.md),
Phase 4 (5_things_or_not.md), or the roadmap. Code is treated as the
source of truth (global rule #1); doc/code disagreements are flagged
against Phase 10's citations rather than re-derived.*

**Scope.** Covers all gaps enumerated in `gap_analysis.md` §1–§5 plus
Plan 31 from the master plan. Items explicitly deferred in Phase 10
(§1.6 Accessibility, §1.7 onboarding, §1.8 i18n, §6.3 non-unknowns)
are listed once in §5 *Non-goals for this plan* and not re-scheduled.

**Global rules enforcement.** Every entry below keeps the rules of the
repo-root prompt:
- Rule #1 (code > docs): each phase cites a code file path and the
  Phase 10 section that cross-checked doc claims against code.
- Rule #2 (no silent deletion): any deletion (e.g. `PlaceholderScreen`,
  `SupplyDropTrigger.STEP_BURST`) documents what the code did first.
- Rule #3 (reuse before create): the new files proposed (`TimeProvider`,
  `FollowOnPipeline`, `UpdateMissionProgress`, cosmetic-override
  plumbing) were all evaluated against existing equivalents in
  Phase 4 / Phase 10; none are duplicates.
- Rule #4 (no nondeterminism in core logic): the TimeProvider phase
  replaces 53 direct wall-clock calls with an injectable abstraction;
  the `SystemTimeProvider` is the approved boundary.
- Rule #5 (refactor protocol): each phase entry contains exact file
  paths, risk, rollback, verification.
- Rule #6 (incremental): every phase lists commit / PR boundaries.

**Legend.**
- *Blocks release* — must ship before Plan 31 can close.
- *Improves quality* — post-release OK per Phase 10 §7.
- *Deferred* — Phase 10 §1.6–§1.12 / §6.3 say not now.

---

## 0. Read-first checklist

Before executing any phase:

1. Re-read `devdocs/evolution/gap_analysis.md` §1, §2, §3, §5.
2. Re-read `devdocs/archaeology/5_things_or_not.md` §1–§5 for the
   five items that already have full risk / rollback / verification
   write-ups; this plan cross-references them rather than duplicates.
3. Run Agent Preflight (`.kiro/steering/11-agent-protocol.md`) —
   `git status` clean, `docs/agent/STATE.md` read.
4. Confirm `./run-gradle.sh testDebugUnitTest` is **green at HEAD**
   (412 tests per `STATE.md`). Any red test means stop and diagnose
   before starting a phase.

---

## 1. Quick wins — immediate, low-risk

Each item below is a **single PR** that can land in any order
independent of the others. Total wall-clock: ~1–2 engineering days
if done serially.

### Q1. Documentation drift: schema v7 → v8

*Source:* gap_analysis §3.12; `intro2codebase.md` §9. *Quality:*
improves; does not block release.

- **Files:** `docs/database-schema.md` (line claiming v7),
  `AGENTS.md` (source-index header note if present),
  `README.md` if it cites a test count that drifted.
- **Change:** one-line edits to reflect code state (v8 per
  `AppDatabase.kt` `@Database(version = 8)`; 412 tests per
  `STATE.md`).
- **Dependencies / prerequisites:** none.
- **Risk assessment:** effectively zero — doc-only edit. Mitigation:
  none required.
- **Testing / verification:**
  - `grep -rn "version 7\|schema v7" docs/` returns only historical
    migration notes, not current-state claims.
  - `./run-gradle.sh testDebugUnitTest` — unchanged result (doc edit
    cannot affect tests).
- **Rollback:** `git revert` the single commit.
- **Developer workflow:** one sitting, single reviewer.
- **PR boundary:** one PR titled `docs: sync schema version to v8`.
- **Non-goals:** not a sweep of all docs; not a new schema file; not
  migrating `master-plan.md`.

### Q2. Wipe SQLCipher DB file on decrypt failure

*Source:* gap_analysis §3.7, §4.2.1; Phase 3 trace 12 §9;
`missing_concepts_list.md` §2. *Quality:* blocks release for any
device-restore user (crash-in-loop); not currently in a plan file
— **promote to release-blocker if a single Play Console pre-launch
report hits it.** Treat as release-adjacent.

- **Files:** `data/local/DatabaseKeyManager.kt`.
- **Change:** after the existing passphrase-blob wipe path, add
  `context.getDatabasePath("steps_of_babylon.db").delete()` so
  `AppDatabase` rebuilds from scratch next open instead of crashing
  on the undecryptable file.
- **Dependencies / prerequisites:** none.
- **Risk assessment:**
  - *Main risk:* wipes user progress. Mitigation: progress is
    already unrecoverable at this point (passphrase is gone); the
    alternative is a crash-on-launch loop that requires the user to
    Clear App Data manually. Net positive.
  - *Secondary risk:* someone triggers decrypt failure recovery in a
    path we did not anticipate. Mitigation: Phase 10 §3.7 and
    trace 12 §9 agree there is exactly one trigger path
    (`SupportOpenHelperFactory` raising on wrong passphrase).
- **Testing / verification:**
  - New Robolectric test next to `RoomSchemaTest`: write a DB with
    key K1, simulate key rotation by replacing K1 with K2,
    `openDatabase()` must succeed (rebuilt empty DB), not throw.
  - `./run-gradle.sh testDebugUnitTest` — all 412 existing tests
    remain green.
- **Rollback:** revert the three-line change. `DatabaseKeyManager`
  returns to its current behaviour.
- **Developer workflow:** half-day, one reviewer.
- **PR boundary:** `fix(db): delete undecryptable DB file on key rotation recovery`.
- **Non-goals:** no cloud save (Phase 10 §6.3 forbids for v1.0); no
  prompt to the user; no backup/export UI.

### Q3. Configurable failure modes in Fake billing / ad managers

*Source:* gap_analysis §3.1, §4.2.3. *Quality:* blocks Plan 31
verification (currently there is zero regression net for failure
paths when the real SDK swap lands).

- **Files:**
  - `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeBillingManager.kt`
  - `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeRewardAdManager.kt`
  - `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreViewModelTest.kt`
  - `app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsViewModelTest.kt`
- **Change:** add `var nextResult: PurchaseResult = Success(...)` and
  `var nextAdResult: AdResult = Rewarded` mutable properties; tests
  override per case. Add cases for `PurchaseResult.Cancelled`,
  `Failed`, `Pending` and `AdResult.Failed`, `Skipped`.
- **Dependencies / prerequisites:** Phase 10 §3.1 notes the domain
  sealed classes `PurchaseResult` and `AdResult` already enumerate
  these variants — no domain model changes needed.
- **Risk assessment:** zero (test-only code).
- **Testing / verification:** new tests **are** the verification.
  Each sealed variant gets one VM-level assertion. `./run-gradle.sh
  testDebugUnitTest` must grow by ≥6 tests and stay green.
- **Rollback:** revert. Fakes return to always-succeed behaviour.
- **Developer workflow:** one day, one reviewer.
- **PR boundary:** `test: exercise billing/ad failure paths in fakes and VMs`.
- **Non-goals:** do not start the real SDK swap (that is Phase 3
  §M2). Do not add new domain types.

### Q4. Suppress Battle-Step-Reward FloatingText when daily cap hit

*Source:* gap_analysis §4.2.7; Phase 9 §3 edge case. *Quality:*
minor UX polish.

- **Files:** `presentation/battle/BattleViewModel.kt`
  (`onStepRewardHook` callback).
- **Change:** `if (credited > 0) effectEngine.spawnFloatingText(...)`
  guard; `AwardBattleSteps` already returns 0 when capped.
- **Dependencies / prerequisites:** none.
- **Risk assessment:** low. Mitigation: add one VM test that asserts
  `spawnFloatingText` is not invoked when the fake
  `AwardBattleSteps` returns 0.
- **Testing / verification:**
  - One new `BattleViewModelTest` case.
  - Manual smoke: play a round past the 2000/day cap; confirm
    "+Step" text stops while the counter freezes.
- **Rollback:** revert.
- **Developer workflow:** hour-scale, bundled with another PR.
- **PR boundary:** can piggy-back on Q3 or stand alone as
  `fix(battle): do not render +Step FloatingText when daily cap is exhausted`.
- **Non-goals:** do not suppress the HUD counter (it remains at
  2000/2000 — the freeze communicates the cap).

### Q5. Deep-link collector covers all 12 routes

*Source:* gap_analysis §2.3, §4.2.2; Phase 8 §3; trace 10 §8.
*Quality:* reliability improvement; not a release blocker but any
future notification-to-route is silently broken today.

- **Files:**
  - `presentation/navigation/Screen.kt` (add `fun fromRoute(name: String): Screen?`).
  - `presentation/MainActivity.kt` (`pendingNavigation` collector).
  - `app/src/test/java/com/whitefang/stepsofbabylon/presentation/DeepLinkRoutingTest.kt`.
- **Change:** replace the 5-route `when` with
  `Screen.fromRoute(name)?.let { navController.navigate(it.route) }`.
  Fall-through to Home preserved for unknown strings (current
  behaviour).
- **Dependencies / prerequisites:** none.
- **Risk assessment:**
  - *Main risk:* a `navigate_to=battle` deep-link without tier
    context could crash the BattleScreen init. Mitigation: add a
    per-route "requires context" allow-list in the collector; routes
    needing arguments (Battle) stay on the current 5-route whitelist,
    only argument-free routes (Store, Stats, Weapons, Cards, Economy,
    Settings) are added.
- **Testing / verification:**
  - Extend `DeepLinkRoutingTest` with 7 cases covering the
    previously-unhandled routes.
  - Manual smoke: send an `am start -a ... --es navigate_to store`
    intent; confirm StoreScreen opens.
- **Rollback:** revert; the 5-route `when` is restored.
- **Developer workflow:** half-day, one reviewer.
- **PR boundary:** `feat(nav): deep-link collector handles all argument-free routes`.
- **Non-goals:** do **not** adopt typed routes (Phase 10 §2.3
  explicitly defers until routes grow past 12). Do not refactor
  navigation graph structure.

### Q6. `SupplyDropTrigger.STEP_BURST` — decide delete or wire

*Source:* gap_analysis §1.4; Phase 8 §3; `missing_concepts_list.md`
§2. *Quality:* improves (removes dead enum branch or wires an
advertised feature).

- **Files:**
  - `domain/model/SupplyDropTrigger.kt` (one of: delete the entry, or
    wire its trigger emission site).
  - `data/sensor/DailyStepManager.kt` → `FollowOnPipeline` once the
    extraction lands (M1), **or** directly here if wiring comes
    before M1.
  - `domain/usecase/GenerateSupplyDrop.kt` (currently only inspects
    MILESTONE, THRESHOLD, RANDOM).
- **Decision required first (unknown — gap_analysis §6.2 item 1):**
  the docs do not demand either outcome. Propose: **delete** in
  this quick-win PR; if a designer later wants burst drops, the
  enum entry can be re-added with the wiring in one PR.
- **Dependencies / prerequisites:** if deleting, document the
  original intent in the commit body (rule #2) — copy the
  notification string "Your pace is impressive!" verbatim into the
  commit message and into a line in `concept_mappings.md`'s
  historical-context appendix so the intent is preserved.
- **Risk assessment:** deletion is safe because Phase 8 §3 and
  Phase 10 §1.4 both confirm zero production call sites. Mitigation:
  grep `STEP_BURST` before and after the change; must be zero
  matches after.
- **Testing / verification:**
  - `grep -r STEP_BURST app/src` returns no hits post-change.
  - `./run-gradle.sh testDebugUnitTest` — all 412 tests remain green
    (no test references the enum entry per Phase 9).
- **Rollback:** revert; the enum entry returns exactly as written
  today.
- **Developer workflow:** hour-scale.
- **PR boundary:** `chore(model): remove unused SupplyDropTrigger.STEP_BURST (document intent in commit body)`.
- **Non-goals:** do not wire burst-detection logic. That is a
  content/design decision outside this plan.

### Q7. `PlaceholderScreen` dead code

*Source:* gap_analysis §3.11; trace 05. *Quality:* trivial
cleanup.

- **Files:** `presentation/MainActivity.kt` (unused `@Composable fun PlaceholderScreen`).
- **Change:** delete the function. Per rule #2, the commit message
  documents what it did: "rendered a centred 'Coming soon' Text;
  never referenced from any `composable()` entry since Plan 06
  landed".
- **Dependencies / prerequisites:** none.
- **Risk assessment:** zero — zero call sites (verified in Phase 1 /
  trace 05).
- **Testing / verification:** `grep PlaceholderScreen app/src`
  returns empty; lint + tests green.
- **Rollback:** revert.
- **Non-goals:** do **not** touch `Screen.items by lazy` — Phase 10
  §3.11 flags it as a documented NPE workaround that must stay.
- **PR boundary:** piggy-back on Q1 or standalone.

### Q8. Season Pass bonus leak fix (in-pipeline quick patch)

*Source:* gap_analysis §3.8. *Quality:* correctness bug visible only
to Season-Pass owners.

- **Files:** `data/sensor/DailyStepManager.kt`
  (`runFollowOnPipeline`'s call to `TrackDailyLogin(...)`).
- **Change:** read `playerRepository.observeProfile().first()` in the
  pipeline and pass `hasSeasonPass` and `hasAdRemoval` flags to
  `TrackDailyLogin(...)` — matching what `HomeViewModel.init` already
  does.
- **Dependencies / prerequisites:** none. Note: this partially
  duplicates logic from `HomeViewModel.init` — the **clean** fix is
  the `FollowOnPipeline` extraction in Phase 3 §M1, which
  centralises the single call site. Treat Q8 as a tactical patch
  until M1 lands, then remove the duplication.
- **Risk assessment:** low. Mitigation: add a
  `DailyStepManagerTest` case with `hasSeasonPass = true` asserting
  the bonus Gems are credited during background crediting.
- **Testing / verification:**
  - New `DailyStepManagerTest` case.
  - Verify `TrackDailyLoginTest` already covers the flags path (per
    STATE.md it does).
- **Rollback:** revert; current leak re-appears.
- **PR boundary:** `fix(steps): pass Season Pass flags to TrackDailyLogin from background pipeline`.
- **Non-goals:** do **not** attempt the `FollowOnPipeline`
  extraction here (that is M1).

### Quick-win rollout order

No hard dependencies among Q1–Q8. Suggested order by payback-per-day:

1. Q2 (device-restore crash recovery) — user-visible severity.
2. Q8 (Season Pass leak) — correctness bug.
3. Q5 (deep-link coverage) — unlocks future notification features.
4. Q3 (fake failure modes) — prerequisite for safely verifying M2/M3.
5. Q1, Q7, Q6, Q4 — any order.

### Exit criteria for §1

- All 8 PRs merged (or explicitly deferred with rationale in
  `STATE.md`).
- Test count ≥ 412 + N (where N = number of new cases added by
  Q2/Q3/Q4/Q5/Q8).
- `grep STEP_BURST app/src` returns empty (assuming Q6 deletion
  route).

---

## 2. Incremental improvements — subsystem-by-subsystem

Each of these is sized at 1–3 PRs and addresses one subsystem at a
time. Order matters within a subsystem but not across subsystems.

### I1. Anti-cheat visibility on Stats screen

*Source:* gap_analysis §1.10, §4.2; Phase 4 §5. *Quality:* trust /
operability. Phase 4 §5 has the full write-up.

- **PR 1 — getters:** extend `data/anticheat/AntiCheatPreferences.kt`
  with read-only accessors (`getStepsDiscardedToday`,
  `getCvOffenseCount`, `getLastEscrowNote`). The underlying fields
  already exist (Phase 4 §5). Pure addition.
- **PR 2 — UI card:** add `AntiCheatSummary` to
  `presentation/stats/StatsUiState.kt`; render a conditional
  `Card { }` in `presentation/stats/StatsScreen.kt` ("Step validation"
  section, shown only when counters > 0).
- **Dependencies / prerequisites:** none. Does not depend on M1/M2.
- **Risk assessment:** Phase 4 §5 risk table — sophisticated users
  could reverse-engineer the ladder. Mitigation: show coarse
  facts (count + level indicator) only; no thresholds, no decay
  rules.
- **Testing / verification:**
  - Extend `StatsViewModelTest` with
    `stepsDiscardedToday=500, cvOffenseCount=2` → summary exposed.
  - Negative case: both 0 → summary card hidden.
- **Rollback:** revert either PR independently.
- **Developer workflow:** 1–2 days total.
- **PR boundary:** PR 1 = `feat(anticheat): expose validation counters as read-only accessors`; PR 2 = `feat(stats): surface step validation summary`.
- **Non-goals:** do not add a new notification. Do not audit past
  awards (gap_analysis §3 acknowledges no audit log; adding one is a
  separate conversation).

### I2. Atomic writes — first site (`PurchaseUpgrade`)

*Source:* gap_analysis §2.1 site #2, §3.2; Phase 4 §2. *Quality:*
correctness. Phase 4 §2 has the exact code shape.

- **PR 1 — one site:** introduce the SQL-guarded
  `adjustStepBalanceIfSufficient` on `PlayerProfileDao`; add
  `purchaseUpgradeAtomic` on `WorkshopDao` per Phase 4 §2. Wire
  `PurchaseUpgrade.invoke` through the atomic path. Keep the old
  read-then-write path as a dead-code `@Deprecated` fallback for
  one PR cycle, delete in PR 2.
- **Dependencies / prerequisites:** none (the atomic path lives
  inside `data/local/`; no layer-crossing). Phase 4 §2 verified the
  design.
- **Risk assessment:** Phase 4 §2 risk table — the SQL `WHERE >= :cost`
  is subtly different from read-then-deduct (closes a double-tap
  race). Mitigation: add two concurrency tests (success,
  insufficient) before removing the old path.
- **Testing / verification:**
  - Extend `PurchaseUpgradeTest` with the two new cases.
  - `./run-gradle.sh testDebugUnitTest` — green.
  - Manual: debug APK, buy an upgrade, confirm single-increment.
- **Rollback:** revert PR 1. The old read-then-write path is
  preserved in the `@Deprecated` fallback for one cycle — so the
  rollback is literally flipping a flag if PR 2 has not landed yet.
- **Developer workflow:** 1–2 days.
- **PR boundary:** PR 1 = `feat(db): atomic PurchaseUpgrade via WorkshopDao.purchaseUpgradeAtomic`; PR 2 = `chore(db): remove deprecated non-atomic PurchaseUpgrade path`.
- **Non-goals:** do not migrate the other four sites in this
  subsystem PR. They get follow-up PRs in I3–I5.

### I3. Atomic writes — remaining sites (compose with M1 where applicable)

*Source:* gap_analysis §2.1 sites #1, #3, #4, #5. *Quality:*
correctness. Dependency: I2 must have landed so the pattern is
proven.

- **PR 1 — `AwardBattleSteps`:** add
  `adjustStepBalanceAndIncrementBattleSteps` composite DAO method;
  `AwardBattleSteps.invoke` calls the single atomic method.
- **PR 2 — `StepCrossValidator.validate`:** the validator has three
  parallel branches (Phase 10 §2.1); each calls `spendSteps(excess)`
  then `updateEscrow(date, excess, newSync)`. Wrap each branch in
  `AppDatabase.withTransaction { ... }` — this is the one place where
  the cross-layer `RoomDatabase` import is acceptable because the
  validator is in `data/healthconnect/`, not `domain/`.
- **PR 3 — `ClaimMilestone`:** composite DAO method on `MilestoneDao`
  that does profile-credit + `markClaimed` in one transaction.
- **PR 4 — `BattleViewModel.endRound` transaction wrapper:** can only
  land **after** I4 (the resilience refactor) because I4 extracts the
  persistence function that this PR wraps.
- **Dependencies / prerequisites:** I2 merged.
- **Risk assessment:** each PR is narrower than I2 (the pattern is
  now established). Phase 10 §2.1 enumerates exactly these five
  sites; nothing new is discovered.
- **Testing / verification:** each PR adds one success + one
  partial-failure test (simulated exception mid-transaction).
- **Rollback:** per-PR revert; each site is independent.
- **Developer workflow:** ~1 week (4 PRs × 1 day).
- **PR boundaries:** one PR per write site, matching the four
  outstanding sites from gap_analysis §2.1.
- **Non-goals:** do not introduce a global `TransactionRunner`
  abstraction. The existing `@Transaction` DAO annotations are
  sufficient and recommended by Room docs.

### I4. Resilient `BattleViewModel.endRound`

*Source:* gap_analysis §3.4; Phase 4 §3. *Quality:* reliability.
Composes cleanly with I3 PR 4.

- **PR 1 — extract persistence:** split `endRound()` into a
  UI-facing wrapper and a pure-persistence
  `runEndRoundPersistence(engine)` function. Make `quitRound()` and
  the polling loop call the same extracted function. Wrap each of
  the three critical writes in try/catch + `Log.w`.
- **PR 2 — onCleared guard:** per Phase 4 §3, if a round is in
  progress at `onCleared`, call `runEndRoundPersistence` on
  `ProcessLifecycleOwner.get().lifecycleScope` so the writes survive
  VM cancellation.
- **Dependencies / prerequisites:** Phase 4 §3 walkthrough verified;
  I3 PR 4 can follow immediately.
- **Risk assessment:** Phase 4 §3 risk table — `ProcessLifecycleOwner`
  outlives the VM but not the process; background kill between
  `launch` and first write still loses the round. Mitigation:
  I3 PR 4 wraps the writes in a transaction, turning 3 atomicity
  gaps into 1.
- **Testing / verification:**
  - VM-level test: simulate `onCleared()` mid-round; assert
    `updateBestWave` called exactly once.
  - Failure injection: throw from `updateBestWave`; assert
    `awardWaveMilestone` is not called and VM does not propagate.
  - Manual smoke: start battle, reach wave 5, press system back;
    confirm Stats reflects the run.
- **Rollback:** revert either PR. `onCleared` returns to callback-null
  only; `endRound` returns to one coroutine body.
- **Developer workflow:** 2–3 days total.
- **PR boundary:** PR 1 = `refactor(battle): extract runEndRoundPersistence and guard with try/catch`; PR 2 = `fix(battle): persist round on mid-battle navigation via ProcessLifecycleOwner`.
- **Non-goals:** do not rewrite the round state machine. Do not add
  a "resume round" feature. The change preserves existing
  behaviour: quit or nav-away both **end** the round.

### I5. `TimeProvider` abstraction — narrow migration

*Source:* gap_analysis §2.2; Phase 4 §1. *Quality:* testability,
reproducibility. Satisfies rule #4 for future features that need
time injection. **Only migrates three call sites**; the other 50
stay on `LocalDate.now()` until opportunistically migrated.

- **PR 1 — abstraction and bindings:** create
  `domain/time/TimeProvider.kt`, `data/time/SystemTimeProvider.kt`,
  and `di/TimeModule.kt`. Pure Kotlin interface in `domain/`; impl
  in `data/`; `@Binds` in `di/`. No existing code changes.
- **PR 2 — three call sites:** migrate `AwardBattleSteps`,
  `BattleViewModel.endRound` (line 168 lookup),
  `MissionsViewModel` ticker. Each keeps a default argument / nullable
  fallback so current tests do not break.
- **PR 3 — `FakeTimeProvider` + one new test per migrated site:**
  adds `test/fakes/FakeTimeProvider.kt`; adds one test per migrated
  site that exercises a synthetic midnight boundary (currently
  untestable).
- **Dependencies / prerequisites:** none. Phase 4 §1 has the full
  code shape.
- **Risk assessment:** Phase 4 §1 risk — scope creep ("finish the
  migration"). Mitigation: PR description explicitly lists only the
  three target files and rejects others. Additive; no signatures
  change.
- **Testing / verification:**
  - All 412 existing tests stay green (defaults preserved).
  - 3 new tests (one per migration) exercise midnight boundaries.
  - `grep -r 'import android' app/src/main/java/com/whitefang/stepsofbabylon/domain/`
    stays empty — `TimeProvider` must be pure Kotlin.
- **Rollback:** per PR. PR 1 alone can land without PR 2/3 and
  nothing uses it; revert PR 2 → default args re-activate; PR 3 is
  tests only.
- **Developer workflow:** 2–3 days.
- **PR boundary:** per the three PRs above.
- **Non-goals:** **do not migrate the remaining 50 sites** (Phase 4
  §1 explicit boundary). Do not include `nanoTime()` in the interface
  (Phase 10 §2.2 open question — `GameLoopThread` stays on direct
  `nanoTime()`). Do not change ADR-0003 (default-parameter pattern
  remains the documented convention for use cases).

### I6. `ClaimMilestone.Cosmetic` branch fix

*Source:* gap_analysis §4.2.4, §1.3; Phase 8 §3 finding; Phase 9 §17
risk 2. *Quality:* correctness bug. Resolution depends on MR1
(cosmetic pipeline) for the end-to-end answer, but the **detection
fix** can land immediately.

- **Files:** `domain/usecase/ClaimMilestone.kt:25`;
  `data/local/CosmeticDao.kt` (or `CosmeticRepository`).
- **Change:** when `reward is MilestoneReward.Cosmetic`, look up the
  cosmetic by ID; on miss, return a new `Result.UnknownCosmetic`
  sealed variant (or `Result.Failure("Unknown cosmetic id=$id")`) so
  the caller sees the issue. **Do not silently drop.**
- **Dependencies / prerequisites:** none for detection; the
  resolution (making the three milestone IDs match `SEED_COSMETICS`
  or defining new cosmetic records) depends on MR1 direction.
- **Risk assessment:** low. Mitigation: add a `ClaimMilestoneTest`
  case covering the three known-mismatched IDs.
- **Testing / verification:**
  - New test asserting `UnknownCosmetic` result for each of the
    three current mismatches.
  - Negative case: a matching ID credits the cosmetic.
- **Rollback:** revert; silent drop resumes.
- **Developer workflow:** 1 day.
- **PR boundary:** `fix(milestones): surface unknown cosmetic IDs in ClaimMilestone result`.
- **Non-goals:** do not rename the three mismatched IDs in this PR
  (that is content work coupled to MR1).

### I7. Settings-screen rename + in-app privacy link

*Source:* gap_analysis §4.2.5, §4.2.6, §1.11. *Quality:* UX polish.

- **PR 1 — rename + split:** `NotificationSettingsScreen` →
  `SettingsScreen`; split body into "Notifications" and "Audio"
  sections; update `Screen.kt` route name and
  `presentation/navigation/BottomNavBar.kt` label. Keep the existing
  route string as an alias so deep-links don't break.
- **PR 2 — privacy policy row:** add a "Privacy Policy" row that
  launches the external URL via `CustomTabsIntent`. **Blocked on
  the privacy-policy URL actually existing** (Plan 31 Task 1 external
  step). Hide the row behind a `BuildConfig.PRIVACY_POLICY_URL` not
  null check.
- **Dependencies / prerequisites:** PR 1 has none. PR 2 depends on
  the external hosting step of Plan 31.
- **Risk assessment:** PR 1 low — the composable move is mechanical.
  Mitigation: the route alias protects existing deep-links.
- **Testing / verification:**
  - `NotificationSettingsViewModelTest` must still pass after
    rename; rename the test class too.
  - Manual smoke: screen loads under new route name.
- **Rollback:** revert each PR independently.
- **Developer workflow:** 1 day for PR 1; hour-scale for PR 2 when
  unblocked.
- **PR boundaries:** two PRs.
- **Non-goals:** do not add new settings in this PR. Do not build an
  in-app privacy-policy reader (an external link is sufficient per
  Play Store policy).

### Incremental rollout order

The six subsystems are mostly independent. Suggested order:

1. **I1** (anti-cheat visibility) — isolated, no cross-coupling.
2. **I5** (TimeProvider) — enables I3 PR 4 and future work; widest
   long-term payoff.
3. **I2** (atomic writes, first site) — proves the pattern.
4. **I4** (endRound resilience) — composes with I3 PR 4.
5. **I3** (atomic writes, remaining sites) — last because PR 4
   depends on I4.
6. **I6** (ClaimMilestone fix) — any time after I1.
7. **I7** (Settings rename) — any time; PR 2 gated on Plan 31 URL.

### Exit criteria for §2

- All six subsystems merged to `main`.
- `grep -c "withTransaction\|@Transaction" app/src/main` ≥ 5 (one
  per site from gap_analysis §2.1).
- `grep -rn 'LocalDate.now()' app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleSteps.kt`
  returns no direct calls (I5 migration verified).
- Test count ≥ 412 + N (N = new tests from I1, I2, I3, I4, I5, I6).
- No new Android imports in `domain/time/`.

---

## 3. Major refactoring — requires planning

These are the items that need a planning doc before coding
starts. Each is **one new ADR + one plan file**, then staged PRs.
Two belong here:

- **M1** — `FollowOnPipeline` + `UpdateMissionProgress` extractions
  (structural refactor inside the sensor / mission path).
- **M2** — Real Billing SDK swap (Plan 31 code-swap #1).
- **M3** — Real Ad SDK swap (Plan 31 code-swap #2).
- **M4** — Plan 31 external tasks (Play Console, listing assets,
  privacy-policy hosting, AAB upload, Firebase Test Lab).

### M1. `FollowOnPipeline` + `MissionProgressTracker` extractions

*Source:* gap_analysis §2.4, §2.5; Phase 4 §4; Phase 8 §3 fat-module
critique. *Quality:* maintainability + removes 4 of 12
forbidden-direction imports.

Two extractions composed into one refactor plan because they both
touch the step-ingestion → mission-progress boundary and Phase 10
§2.5 notes the Season Pass leak (Q8's tactical fix) is cleanly
resolved once both land.

- **Prerequisites:**
  - I5 merged (so new code can take `TimeProvider` by convention).
  - Q8 merged (so the Season Pass regression test exists — M1 must
    not regress it).
  - New ADR: `docs/agent/DECISIONS/ADR-0004-follow-on-pipeline.md`
    explaining the split + why `dropState` stays `@Singleton`.
  - New plan: `docs/plans/plan-32-follow-on-pipeline.md`.
- **PR plan (4 PRs):**
  1. **PR 1 — `FollowOnPipeline` extraction:** mechanical move per
     Phase 4 §4. `DailyStepManager` loses 6 constructor params; the
     pipeline is `@Singleton` with its own Hilt injection. `dropState`
     moves with it.
  2. **PR 2 — `UpdateMissionProgress` use case:** new file
     `domain/usecase/UpdateMissionProgress.kt` taking
     `(category: MissionCategory, delta: Int)`, owning
     read + clamp + write inside a `@Transaction`. Replaces one
     call site (start with `DailyStepManager`'s walking-mission path).
  3. **PR 3 — migrate remaining mission-progress sites:** the other
     four sites (`BattleViewModel`, `LabsViewModel`,
     `WorkshopViewModel`, `MissionsViewModel`) switch to
     `UpdateMissionProgress`. Each migration removes one
     forbidden-direction `data.local.DailyMissionDao` import.
  4. **PR 4 — remove Q8's tactical patch:** delete the
     `playerRepository.observeProfile().first()` duplication in
     `DailyStepManager`; `FollowOnPipeline` now owns the single call
     site to `TrackDailyLogin` with flags.
- **Risk assessment:**
  - *Behaviour parity:* zero-change extraction is the goal. Risk:
    subtle ordering dependency between the five follow-on stages.
    Mitigation: `DailyStepManagerTest` must pass verbatim under the
    new composition; any failing assertion is investigated before
    merge.
  - *Surface area:* 4 PRs, ~300 lines moved, ~50 new. Reviewable one
    PR at a time.
  - *Test split:* `DailyStepManagerTest` splits into two files
    (`DailyStepManagerTest` + `FollowOnPipelineTest`). Each side
    preserves the assertions from the original.
- **Testing / verification strategy:**
  - After each PR, the full 412+ test suite must stay green.
  - After PR 1, `DailyStepManagerTest` + new `FollowOnPipelineTest`
    together match pre-extraction assertion count (accept a small
    split delta if justified in PR description).
  - After PR 3, `grep -rn 'import.*data.local.*DailyMissionDao' app/src/main/java/com/whitefang/stepsofbabylon/presentation/`
    returns 0 hits.
  - After PR 4, `grep -rn 'observeProfile' app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/`
    returns 0 hits (pipeline is the single caller).
- **Rollback plan:** per-PR revert. Because extraction is
  mechanical, reverting any single PR restores its before-state
  without touching the others.
- **Developer workflow:** ~1 week calendar (4 PRs, 1-day each with
  review cycles).
- **Commit / PR boundaries:** the 4 PRs above; no one PR should
  mix extraction with behaviour change.
- **Non-goals:**
  - Do **not** extract beyond the five-stage pipeline. The
    rate-limit → velocity → ceiling → persist chain stays inside
    `DailyStepManager`.
  - Do not introduce the `Reward` sealed-hierarchy unification
    (gap_analysis §3.5 — explicitly deferred).
  - Do not change `dropState`'s lifetime (`@Singleton`) — Phase 4
    §4 flagged lifetime preservation as a review item.

### M2. Real Google Play Billing SDK swap

*Source:* gap_analysis §1.1 Task 4, §3.1; Plan 31. *Blocks
release.* Phase 8 §5 / Phase 9 §19 called out the stub-swap pattern
as an explicit design choice; swap point is `di/BillingModule.kt`.

- **Prerequisites:**
  - **Q3 merged** — fakes exercise failure paths; regression net
    exists.
  - Plan 31 external steps advanced far enough to have a Play
    Console app entry, a licensing test account, and product SKUs
    configured. Without those, the real SDK cannot be exercised.
  - New ADR: `docs/agent/DECISIONS/ADR-0005-billing-sdk.md` —
    chosen SDK version, pending-purchase handling strategy, receipt
    verification decision (v1.0 is client-only; no backend per
    `CONSTRAINTS.md`).
- **PR plan (3 PRs):**
  1. **PR 1 — dependency + `BillingManagerImpl`:** add Play Billing
     dependency to `libs.versions.toml` (pin exact version per rule
     #5 and steering). New `data/billing/BillingManagerImpl.kt`
     implementing `BillingManager`. Not yet bound.
  2. **PR 2 — Hilt binding swap:** change `di/BillingModule.kt`
     `@Binds BillingManager` from `StubBillingManager` to
     `BillingManagerImpl` **behind a `BuildConfig.USE_REAL_BILLING`
     flag** so debug/release can diverge during beta.
  3. **PR 3 — remove stub (post-beta verification):** once internal
     + closed tracks confirm behaviour, delete
     `StubBillingManager` (document its behaviour in the commit
     body per rule #2).
- **Risk assessment:**
  - *High:* the real SDK's failure paths have never run against
    production code. Mitigation: Q3's fake-failure tests run against
    the real manager via a thin adapter (`BillingManagerImpl` is
    unit-testable with mock Play Billing clients); Firebase Test
    Lab pre-launch report catches launch regressions.
  - *High:* mishandled pending purchases can cause duplicate grants
    or lost purchases. Mitigation: persist a receipt-id → granted
    flag in Room; re-query pending purchases on `onResume`; document
    in ADR-0005.
  - *Medium:* SKU drift between code and Play Console. Mitigation:
    code is the source of truth for SKU IDs (`BillingProduct.kt`);
    any Play Console entry must match a code constant. Validated by
    a startup sanity check.
  - *Low:* build-time dependency changes. Mitigation: standard
    `libs.versions.toml` workflow; R8 rules added to
    `proguard-rules.pro` per SDK release notes.
- **Testing / verification strategy:**
  - New unit tests against a mocked Play Billing client cover every
    `PurchaseResult` variant (uses Q3's patterns).
  - Manual QA on internal test track: happy purchase, cancelled
    purchase, pending purchase, reconnection after kill.
  - Firebase Test Lab pre-launch report must be clean.
- **Rollback plan:**
  - PR 2 is gated by a flag: flip `USE_REAL_BILLING = false` and
    ship a hotfix if a purchase-path regression is discovered
    post-release.
  - PR 3 is reversible via git revert up to ~1 week; after that the
    stub is considered gone.
- **Developer workflow:** ~1–2 weeks including QA cycles.
- **Commit / PR boundaries:** per the 3 PRs.
- **Non-goals:**
  - No server-side receipt verification (forbidden by
    `CONSTRAINTS.md` — no backend v1.0).
  - No subscription products (not in the GDD; all listed products
    are one-time per `BillingProduct.kt`).
  - Do not refactor `StoreViewModel`'s contract — the interface
    `BillingManager` stays stable; the swap is pure impl.

### M3. Real Ad SDK swap

*Source:* gap_analysis §1.1 Task 5, §3.1; Plan 31. Mirrors M2. Swap
point is `di/AdModule.kt`.

- **Prerequisites:**
  - **Q3 merged** — ad-failure fakes exist.
  - Plan 31 external step: AdMob app ID + ad unit IDs provisioned.
  - New ADR: `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` covering
    choice between AdMob vs mediation SDK, test-ad strategy for
    debug builds.
- **PR plan (3 PRs):** identical shape to M2.
  1. Dependency + `RewardAdManagerImpl`.
  2. Hilt binding behind `BuildConfig.USE_REAL_ADS`.
  3. Remove stub.
- **Risk assessment:** same risk categories as M2; additionally,
  ad-unit IDs are secrets-adjacent (they affect revenue but are not
  security-sensitive). Mitigation: store in
  `local.properties` / `BuildConfig` per steering; never hard-code.
- **Testing / verification strategy:**
  - Test ads in debug builds (AdMob provides documented test IDs).
  - Verify reward callback fires exactly once per successful view.
  - Verify load-failed path surfaces `AdResult.Failed` (Q3's
    patterns).
- **Rollback plan:** flag flip (PR 2) + revert (PR 3) — same as M2.
- **Developer workflow:** ~1 week in parallel with M2 if the same
  engineer owns both; separate engineers can run both in parallel.
- **Commit / PR boundaries:** 3 PRs.
- **Non-goals:**
  - No banner ads or interstitial ads in v1.0 (only reward ads per
    `AdPlacement.kt`).
  - No custom mediation tier selection (use AdMob default mediation
    if enabled).

### M4. Plan 31 external tasks (non-code)

*Source:* gap_analysis §1.1, §1.2; `docs/plans/plan-31-play-console.md`.

Strictly speaking these are not "refactoring" but are in this
section because they block release and require planning-level
coordination with M2 / M3.

- **Tasks (each is its own GitHub issue, not a code PR):**
  - T1: Play Console account + app listing + content rating + data
    safety.
  - T2: Store listing upload (screenshots, feature graphic, short
    description, full description — assets to be produced).
  - T3: Host the privacy policy on an https URL (subject of I7 PR 2
    unblock).
  - T4: AAB upload → internal → closed → open → production tracks.
  - T5: Firebase Test Lab pre-launch report.
  - T6: App icon (res/mipmap-* resources + `android:icon`).
- **Prerequisites:** M2 and M3 merged (production AAB has real
  SDKs).
- **Risk assessment:**
  - Content rating misclassification → app rejection. Mitigation:
    follow IARC questionnaire conservatively.
  - Data safety disclosure mismatch vs actual code behaviour → app
    rejection or policy strike. Mitigation: code is the source of
    truth — the disclosure reflects Health Connect usage, SQLCipher
    encryption, no analytics (per Phase 10 §6.3).
- **Testing / verification strategy:** Firebase Test Lab + manual
  device matrix per `docs/release/`.
- **Rollback plan:** halt rollout at the track boundary (internal
  → closed → open → production is staged specifically so rollback
  is "don't promote to the next track").
- **Developer workflow:** multi-week external coordination.
- **Commit / PR boundaries:** each task is a separate checklist
  item; T6 is the only code-touching item (app icon resources).
- **Non-goals:**
  - No marketing site.
  - No A/B testing of store listing in v1.0.
  - No pre-registration campaign.

### Major-refactor rollout order

1. **M1** can start at any time after I5 + Q8.
2. **M2 and M3** run in parallel after Q3 and a Play Console draft
   exists.
3. **M4** gates release: T6 (icon) can start immediately; T1–T3 are
   external and should start in parallel with M2; T4–T5 wait for
   M2 + M3 merged.

### Exit criteria for §3

- `@Binds` in `BillingModule.kt` points at `BillingManagerImpl`.
- `@Binds` in `AdModule.kt` points at `RewardAdManagerImpl`.
- `FollowOnPipeline` owns all five follow-on stages (exit criterion:
  `DailyStepManager.runFollowOnPipeline` no longer exists).
- `UpdateMissionProgress` is the single entry for mission-progress
  writes.
- A production AAB is live on the Play Store (M4 done).

---

## 4. Complete rewrites — only if necessary

**None.** Phase 10 §5 evaluated two candidates and rejected both.
Per rule #2 this section documents what was evaluated and why it
stayed.

### R1. Multi-module Gradle split — REJECTED

*Source:* gap_analysis §2.6, §5.1; Phase 8 §7, Phase 9 §22.

- **What was evaluated:** splitting the single `:app` module into
  `:domain`, `:data`, `:presentation` to compile-time-enforce layer
  boundaries and eliminate the 12 forbidden-direction imports.
- **Why rejected:**
  - Phase 8 §7 confirmed zero package-level cycles today.
  - The 12 forbidden imports are tactical; M1 removes 2 directly,
    I2/I3 remove 2 more indirectly, and the remaining 8 can be
    replaced one-at-a-time via repository methods.
  - Build times are already fast for a solo developer.
  - Gradle-module split imposes ongoing maintenance cost that only
    pays off with a second engineer or cross-module reuse.
- **Revisit trigger:** a second engineer joins the project, **or**
  an external consumer of `domain/` appears (e.g. an `:app-tv` or
  wearable target).
- **Non-goal:** do not schedule this in the current critical path.

### R2. `Reward` sealed-hierarchy unification — REJECTED

*Source:* gap_analysis §3.5; Phase 8 §4; Phase 9 Appendix A item 8.

- **What was evaluated:** collapsing `Currency` enum,
  `SupplyDropReward` sealed family, and `MilestoneReward` sealed
  family into one `Reward` sealed hierarchy.
- **Why rejected:**
  - No in-flight feature asks for a cross-cutting reward type.
  - The ripple is wide: every VM that type-matches any of the three
    existing families would need to change.
  - Phase 10 §3.5 acknowledges this as debt and explicitly defers.
- **Revisit trigger:** a concrete fourth reward type (e.g. "Season
  Pass XP" or "Event Token") is specified **and** is not
  representable as an existing currency.
- **Non-goal:** do not refactor "for cleanliness".

### What about the cosmetic pipeline?

The cosmetic rendering contract (gap_analysis §5.2, §1.3) is listed
in §3 *Major refactoring* below as **MR1**, not in this §4. Phase 10
§5.2 was explicit: "Not a rewrite — a new narrow contract." Moving
it out of §4:

### MR1. Cosmetic rendering pipeline (structural, not rewrite)

*Source:* gap_analysis §5.2, §1.3, §4.2.4. Listed here because it is
larger than §2 incremental but strictly smaller than a rewrite.

- **Prerequisites:**
  - Product decision on "which one cosmetic ships first" (unknown
    from gap_analysis §6.2 item 2). Proposed default: the jade
    ziggurat recolour per Phase 10 §5.2.
  - I6 merged (so milestone cosmetic IDs surface `UnknownCosmetic`
    rather than silently dropping).
- **PR plan (3 PRs):**
  1. **PR 1 — renderer override contract:** add
     `GameEngine.cosmeticOverrides: Map<CosmeticCategory, CosmeticItem>`
     populated from `PlayerRepository.observeProfile().equippedCosmeticIds`.
     Pass the `ZIGGURAT` override into `ZigguratEntity.layerColors`
     (already a constructor parameter).
  2. **PR 2 — seed + enable:** add
     `CosmeticItem(id="ZIG_JADE", category=ZIGGURAT, overrideColors=[...])`
     to `SEED_COSMETICS`. Enable the purchase button for this one
     cosmetic in `StoreScreen` (remove the R2-11 guard **only** for
     this ID).
  3. **PR 3 — remaining 6 seeded + 3 milestone cosmetics:** content
     work. Each cosmetic is one new seed row + optional colour
     constants; the renderer pathway is already done by PR 1.
- **Risk assessment:**
  - *Medium:* renderer changes can regress the battle screen.
    Mitigation: PR 1 is pure-additive (default override = empty map
    preserves current rendering); a VM-level test asserts
    "no-cosmetic equipped → identical colours to today".
  - *Low:* seed rows + UI enablement are mechanical.
- **Testing / verification strategy:**
  - Unit test: `GameEngine.cosmeticOverrides` maps correctly from
    `equippedCosmeticIds`.
  - Robolectric VM test: equip jade → `onBattleStart` → ziggurat
    colours match override.
  - Manual smoke: equip in Store, start battle, see the jade tint.
- **Rollback plan:** PR 1 is additive — revert restores empty-map
  default. PR 2 re-hides the purchase button. PR 3 is content —
  delete seed rows.
- **Developer workflow:** ~1 week for PR 1 + PR 2; PR 3 is content
  ongoing.
- **Commit / PR boundaries:** per the 3 PRs.
- **Non-goals:**
  - Do **not** add animated cosmetics (gap_analysis §6.2 item 7 —
    deferred).
  - Do not extend beyond `ZIGGURAT` category for PR 1; additional
    categories come as content in PR 3.
  - Do not re-enable all cosmetic purchases in PR 1 — only the
    single validated one in PR 2.

MR1 belongs to §3 *Major refactoring* rollout order; insert it
after M1 and before M4 track promotion.

---

## 5. Explicit non-goals for this plan

These items came up in the source material and are **deliberately
out of scope** for the gap closure plan. Each is cited so a future
reader understands the decision.

1. **Accessibility (Plan 24).** Phase 10 §1.6; `STATE.md` priority
   5. Deferred to post-v1.0. Activity Minute Parity (Plan 05)
   already serves non-ambulatory users.
2. **Onboarding / tutorial.** Phase 10 §1.7; no plan file demands
   it. Deferred; revisit post-launch with Play Console funnel data.
3. **Localisation / i18n.** Phase 10 §1.8. No additional locales
   required; defer until market signal.
4. **Closed-app Labs completion notification.** Phase 10 §1.9. No
   action until retention data supports it.
5. **Boss / high-threat targeting priority.** Phase 10 §1.5. Not a
   documented requirement.
6. **Celestial Gate distinct visual.** Phase 10 §1.12. GDD §6.3
   defines 5 biomes; Tier 11+ is explicitly a bucket.
7. **Cloud save / cross-device sync.** Phase 10 §6.3. Forbidden for
   v1.0 by `CONSTRAINTS.md`.
8. **Analytics / crash reporting.** Phase 10 §6.3. Privacy >
   observability per foundations.
9. **Server-side anti-cheat.** Phase 10 §6.3. No backend for v1.0.
10. **CI/CD.** Phase 10 §6.3. Manual release gate for v1.0.
11. **Multi-module Gradle split.** §4 R1 above — rejected, revisit
    trigger documented.
12. **`Reward` sealed unification.** §4 R2 above — rejected,
    revisit trigger documented.
13. **`TimeProvider.nanoTime()`.** Phase 10 §6.2 item 8. Game loop
    stays on direct `System.nanoTime()`.
14. **Typed-route Navigation Compose migration.** Phase 10 §2.3.
    Defer until routes grow past 12.
15. **In-code reward audit log.** I1 *Non-goals* — a trust card
    reads existing counters; no new table.
16. **Removing the `Screen.items by lazy` workaround.** Phase 10
    §3.11. Documented NPE fix; must stay.

---

## 6. Aggregate critical path

Lifting Phase 10 §7 and overlaying this plan:

```
         ┌─ Q1–Q8 (quick wins, independent) ──┐
HEAD ────┤                                     ├──────────┐
         └─ I1 anti-cheat visibility ──────────┘          │
                                                          │
HEAD ──── I5 TimeProvider (narrow) ───┬─ I4 endRound ─────┤
                                      │                   │
                                      └─ I2 atomic #1 ────┤
                                                          │
                                             I3 atomic #2–5 ──┐
                                                              │
                    I6 ClaimMilestone fix ── I7 Settings ─────┤
                                                              │
                                           M1 FollowOnPipeline ─┐
                                                                │
                    Q3 ──┬── M2 real Billing ──┐                │
                         │                      │                │
                         └── M3 real Ads ──────┤                │
                                                │                │
                                       MR1 cosmetic pipeline ───┤
                                                                │
                                             M4 Plan 31 external ── v1.0
```

**Release-critical sequence (minimum):**

1. Q3 (fake failure modes).
2. M2 + M3 (real SDKs).
3. MR1 PRs 1–2 (ship one cosmetic end-to-end).
4. M4 T1–T6 (Play Console + assets + icon + AAB tracks).

**Post-release (no blocker):**

- I1, I2, I3, I4, I5, I6, I7, M1, MR1 PR 3, Q1, Q2, Q4, Q5, Q6, Q7,
  Q8 — any order that respects the per-phase dependencies above.

---

## 7. Memory-update checklist (per §11-agent-protocol)

When any phase from this plan is executed:

- Append `docs/agent/RUN_LOG.md` with: phase ID, PR list, test-count
  delta, any deviation from this plan.
- Update `docs/agent/STATE.md` "Current objective" and "Top
  priorities" to reflect which phase is in-flight / done.
- If a phase changes architecture (M1, M2, M3, MR1), add a new ADR
  under `docs/agent/DECISIONS/` before the first code PR.
- If a `Non-goals` entry from §5 is later promoted (e.g. Plan 24
  accessibility moves in), update `master-plan.md` first, not this
  file — this plan is the **Phase 11 artefact**; Plan 24 is
  roadmap-level.

---

*End of Phase 11 gap closure plan. Source of truth re-verified
against `devdocs/evolution/gap_analysis.md` (Phase 10) and
`devdocs/archaeology/5_things_or_not.md` (Phase 4); no new findings
introduced beyond what those inputs already contain, per global
rule #3. Written 2026-05-05 against HEAD `a9d0386`.*
