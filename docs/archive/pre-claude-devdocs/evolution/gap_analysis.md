# Gap Analysis

*Standard Analysis Phase 10 — compares current state (from archaeology
Phases 1–9) to desired state implied by the documented foundations, the
master plan roadmap, open ADRs, tests, `STATE.md` known issues, and the
explicit direction ("next is Plan 31"). Written from code as truth;
where docs and code disagree the code is treated as authoritative and
the disagreement is named.*

**Input sources (each cited inline below; no new findings introduced):**

- Phase 1–2: `devdocs/archaeology/small_summary.md`, `intro2codebase.md`, `intro2deployment.md`
- Phase 3: `devdocs/archaeology/traces/` (13 per-boundary traces)
- Phase 4: `devdocs/archaeology/5_things_or_not.md` (5 PR-sized proposals with risk/rollback)
- Phase 5: `devdocs/archaeology/concepts/{technical,design,business,missing}_concepts_list.md`
- Phase 6/7: `devdocs/archaeology/foundations/*.md` (code-inferred) + `devdocs/foundations/*.md` (doc-inferred)
- Phase 8: `devdocs/archaeology/architecture_analysis.md` + `module_discovery.md`
- Phase 9: `devdocs/archaeology/concept_mappings.md` (25-concept coverage map + Appendix A/B)
- Roadmap: `docs/plans/master-plan.md`, `docs/plans/plan-31-play-console.md`
- ADRs: `docs/agent/DECISIONS/ADR-0002-health-connect.md`, `ADR-0003-battle-step-rewards.md`
- Memory: `docs/agent/STATE.md`, `CONSTRAINTS.md`, `docs/agent/RUN_LOG.md`
- Code signal: `grep -E 'TODO|FIXME|XXX|HACK' app/src/main` → **0 matches**
  (nothing is tracked via in-code markers; all gaps live in docs + archaeology)

**Scoring legend used throughout:**

- *Known gap* — explicitly tracked in the roadmap, a plan file,
  `STATE.md`, an external-review remediation tier, or an ADR's
  follow-up.
- *Inferred gap* — surfaced only by archaeology (Phase 3–9); has no
  ticket and no plan entry today.
- *Blocks release* — required to complete Plan 31 (the only remaining
  master-plan entry) or violates a CONSTRAINTS.md invariant.
- *Improves quality but does not block* — payback on testability,
  correctness, maintainability, trust; defer until post-v1.0 is
  acceptable.
- *Deliberately out of scope for v1.0* — the foundations say "no".

---

## TL;DR

- The core gameplay loop (Plans 01–30 + R + R2) is **shipped and
  balanced**; 412 JVM tests green, release APK builds.
- **Plan 31 is the only remaining master-plan entry**
  (`docs/plans/master-plan.md` Current Status, `docs/agent/STATE.md`
  top priority). Everything that blocks release is inside Plan 31's
  task list (real billing SDK, real ad SDK, Play Console, app icon,
  privacy-policy URL, store assets).
- The highest-leverage *inferred* gaps come from Phase 4 and are
  already costed as PR-sized work: `TimeProvider`, `@Transaction`
  for multi-writes, resilient `BattleViewModel.endRound`,
  `FollowOnPipeline` extraction, anti-cheat visibility. All five
  improve quality without blocking release.
- **No rewrite is warranted.** The architecture (Clean Architecture
  inside a single Gradle module, MVVM + StateFlow, Room as single
  source of truth, SurfaceView-in-Compose battle renderer) matches
  the foundations and has absorbed 30+ plans plus two external
  remediation rounds without structural drift.
- One area requires a **structural refactor short of rewrite** before
  the cosmetic monetization pipeline can finish (see §5.1):
  `CosmeticEntity` + `GameEngine` need a visual-application contract
  that does not exist today. This is the only gap that currently
  *blocks a shipped-but-disabled* feature (R2-11 greyed out the
  purchase button because the renderer has no hook).

---

## 1. Concepts needing implementation

Each entry lists **what** is missing, **known/inferred**, **blocks
release?**, **source**, and (for inferred) the smallest next step that
clarifies desired state.

### 1.1 Plan 31 — Play Console & store publication

*Known, blocks release.* Source: `docs/plans/plan-31-play-console.md`;
`docs/agent/STATE.md` "Next actions"; Phase 9 §19, §25.

Plan 31 is the **only** outstanding master-plan entry. Its six tasks
are external to the code plus two code swaps:

1. Play Console app listing + content rating + data-safety + privacy
   policy URL linking. *External.*
2. Store listing upload (screenshots, feature graphic, short/full
   descriptions). *External, assets not yet produced.*
3. AAB upload + internal → closed → open → production tracks.
   *External.*
4. **Real Google Play Billing Library** replacing `StubBillingManager`
   (swap point: `di/BillingModule.kt` `@Binds`). *Code change.*
   Source: Phase 5 `missing_concepts_list.md` §1 first entry;
   Phase 9 §19 risk 1.
5. **Real AdMob (or mediation) SDK** replacing `StubRewardAdManager`
   (swap point: `di/AdModule.kt` `@Binds`). *Code change.*
   Source: Phase 5 §1 second entry; Phase 9 §19.
6. Firebase Test Lab pre-launch report. *External.*

The stub-then-swap pattern (Phase 8 §5, Phase 9 §19 divergence
rationale) was explicitly designed to keep the swap at Plan 31
cheap. The risk is not architectural — it is that neither real SDK
has ever exercised the purchase-cancel / ad-failed-to-load paths, and
`FakeBillingManager`/`FakeRewardAdManager` always succeed in tests
(Phase 9 §19 risk 5, §24 risk 5).

### 1.2 App icon, audio assets, store visual assets

*Known, blocks release.* Source: `docs/agent/STATE.md` known issues;
Phase 5 `missing_concepts_list.md` §1 (App icon, Real audio assets,
Store listing visual assets, Privacy-policy URL).

- No custom launcher icon (`res/mipmap-*/` absent, no
  `android:icon` at application level). Plan 31 Task 2.
- `res/raw/sfx_*.ogg` are placeholder sine-wave tones.
  `SoundManager`/`SoundPool` plumbing is complete (Phase 9 §6 risk
  4). Sourcing royalty-free SFX is a release-prep manual step.
- No screenshots, feature graphic (1024×500), or promo video in the
  repo. `docs/release/play-store-listing.md` enumerates what's
  required.
- `docs/release/privacy-policy.md` text is complete but **not
  hosted**. Google Play requires an https-reachable URL.

These are asset / external-hosting tasks; the code does not change.

### 1.3 Cosmetic visual application in renderer

*Known, blocks a specific UI affordance (not the release itself).*
Source: Phase 5 `missing_concepts_list.md` §1; Phase 9 §19 gap; R2-11
disabled the purchase button with "Coming Soon".

- `CosmeticEntity` + `CosmeticDao` + `StoreScreen` support
  ownership/equip/unequip for 7 seeded placeholders (Phase 9 §19).
- `GameEngine` / `ZigguratEntity` / `ProjectileEntity` / `EnemyEntity`
  do not read the equipped cosmetic (Phase 9 §6 gap c).
- Three walking-milestone cosmetic IDs (`MilestoneReward.Cosmetic`)
  are declared in `Milestone.kt` but **do not match** any
  `SEED_COSMETICS` entry — `ClaimMilestone.kt:25` silently drops the
  reward type (Phase 8 §3 finding, Phase 9 §17 risk 2).

**Smallest next step** (desired state unclear — which visuals count
as "the feature"?): ship **one** cosmetic end-to-end (e.g. a ziggurat
recolour from gold → jade) that touches every layer required:
seed row, equip/unequip path, `GameEngine.cosmeticOverrides` pass-
through, `ZigguratEntity.layerColors` applied from override. Once
one cosmetic is visually demonstrated, the pipeline is defined and
the remaining six + three milestone cosmetics can follow as content.

### 1.4 `SupplyDropTrigger.STEP_BURST` — orphan enum entry

*Inferred, does not block release.* Source: Phase 8
`architecture_analysis.md` §3 finding ("`STEP_BURST` declared at
`SupplyDropTrigger.kt:5` but never produced"); Phase 9 §16 risk 1.

The enum value exists with notification copy ("Your pace is
impressive!"). No code path emits it: `GenerateSupplyDrop.kt` only
checks MILESTONE, THRESHOLD, RANDOM. Two resolutions are equally
valid:

- **Delete the enum entry** (inline with global rule #2: document
  first — which is what this section does) if burst-detection is no
  longer desired.
- **Wire the trigger** to `StepVelocityAnalyzer` (burst = "7+ minutes
  above 100 steps/min") inside `DailyStepManager.runFollowOnPipeline`
  before `GenerateSupplyDrop`.

**Smallest next step:** pick one. The docs do not require either;
Phase 5 §2 flags this as an "unintended gap" but there is no plan
file.

### 1.5 Boss / high-threat targeting priority

*Inferred, does not block release.* Source: Phase 5
`missing_concepts_list.md` §2; Phase 9 §8 gap 2.

`ZigguratEntity.findNearestEnemies(n)` ignores enemy type. A ranged
enemy behind a wall of basics deals disproportionate damage before
being shot. Common tower-defence ergonomic gap; balance tests pass
because they aggregate. Not a documented requirement in GDD §10 or
`docs/battle-formulas.md`, so desired behaviour is unclear.

**Smallest next step:** no action until either players report the
issue or a designer decides "bosses first". If added, changes
localise to `GameEngine.findNearestEnemies` + a new `targetingMode`
flag on `ResolvedStats`.

### 1.6 Accessibility (Plan 24)

*Known, deliberately deferred.* Source: `docs/plans/master-plan.md`
("Plan 24: Accessibility *(deferred)*"); `docs/agent/STATE.md`
priority #5.

`CONSTRAINTS.md` calls accessibility a post-v1.0 priority. Activity
Minute Parity (Plan 05) already serves non-ambulatory users.
TalkBack, colour-blind modes, adjustable text size, alternative
input, audio cues are all out of scope for v1.0. **Do not schedule
before Plan 31.**

### 1.7 Onboarding / tutorial

*Inferred, out of v1.0 scope.* Source: Phase 5
`missing_concepts_list.md` §1.

No first-run tutorial. The home screen starts cold with a BATTLE
button. Not required by any plan file or the GDD. Worth surfacing
post-launch based on Play Console funnel data.

### 1.8 Localisation / i18n

*Known, out of v1.0 scope.* Source: Phase 5 §1; `docs/agent/CONSTRAINTS.md`
absent; GDD does not list additional locales.

All UI strings are English-only, hardcoded in composables.
`res/values/strings.xml` holds only `app_name`. Retrofitting string
resources is mechanical but noisy; defer until market demand is
known.

### 1.9 Closed-app Labs completion notification

*Inferred.* Source: Phase 9 §11 gap.

When research completes while the app is closed, the player sees
the checkmark next app-open but no notification fires.
`CheckResearchCompletion` runs in `HomeViewModel.init`; the
completion path is batched. A per-research
`WorkManager.enqueueUniqueWork(research.id, …)` would fire a notif
at expiry, but the cost is N workers for N concurrent research.

**Smallest next step:** do nothing for v1.0; the notification
channel (`reminders`) already exists in `SmartReminderManager` and
could be repurposed later if retention data says it matters.

### 1.10 Step-validation visibility (anti-cheat trust gap)

*Inferred, improves quality.* Source: Phase 4 §5; Phase 9 §2 risk 2.

A player whose credit is silently capped (`StepVelocityAnalyzer`
0.5× or 0.0×, or `StepCrossValidator` Level 0–3) sees slower Step
accumulation with **no explanation**. The counters exist in
`AntiCheatPreferences` and `DailyStepRecordEntity.escrowSteps`;
nothing reads them into UI. This is the Phase 4 item 5 "surface
anti-cheat activity on Stats screen" proposal — already fully
costed (~80 lines, no new tables).

### 1.11 In-app privacy-policy navigation

*Inferred.* Source: Phase 5 `missing_concepts_list.md` §3.

The only in-app path to a policy summary is the Health Connect
rationale activity. Settings / Store / Home have no "Privacy Policy"
link. Post-release Play Console requires the external URL anyway,
but a Settings link to the same URL is a small UX win.

### 1.12 Celestial Gate (Tier 11+) visual differentiation

*Inferred, cosmetic.* Source: Phase 9 §9 risk 1.

`Biome.forTier(n)` returns Celestial Gate for all n ≥ 11; there is no
distinct ambient-particle set or ziggurat palette beyond the generic
palette. Minor late-game content gap. Not required by GDD §6.3 —
which defines five biomes and stops at Tier 11+ as a bucket.

---

## 2. Architecture changes required

"Required" here means **the foundations or constraints demand it**,
not "would be nice". Most of the architecture is stable; three items
are genuinely required.

### 2.1 Wrap currency-mutating multi-writes in `@Transaction` (REQUIRED)

*Known, improves correctness.* Source: Phase 4 §2; Phase 9 §5 risk 1,
Appendix A item 1; `docs/agent/CONSTRAINTS.md` "Room writes must not
produce negative currencies"; Phase 7 doc-inferred R-STEP-06/R-STEP-07
(idempotent crediting).

**The claim that balances never go negative is only true because the
clamp is in SQL**. The architectural invariant "game-state writes
must be atomic at the DAO level"
(`devdocs/archaeology/foundations/known_requirements.md` §2 "Game-
state writes must be atomic") is **violated** at five sites:

1. `AwardBattleSteps` — `addSteps(credited)` + `incrementBattleSteps(today, credited)`
   (`domain/usecase/AwardBattleSteps.kt:31-37`).
2. `PurchaseUpgrade` — `spendSteps(cost)` + `setUpgradeLevel(type, newLevel)`
   (`domain/usecase/PurchaseUpgrade.kt:20-22`).
3. `StepCrossValidator.validate` — `spendSteps(excess)` + `updateEscrow(date, excess, newSync)`
   (`data/healthconnect/StepCrossValidator.kt:60-65` and parallel branches).
4. `BattleViewModel.endRound` — `updateBestWave` → `awardWaveMilestone`
   → `updateHighestUnlockedTier` (`presentation/battle/BattleViewModel.kt:144-184`).
5. `ClaimMilestone` — profile-credit + `milestoneDao.markClaimed`
   (`domain/usecase/ClaimMilestone.kt`).

Grep-verified: **zero `@Transaction` or `withTransaction` uses in
`app/src/main`** (Phase 4 §2, re-verified in Phase 8). The
architectural change is small: per-write-pair, extract a composite
DAO method or use `AppDatabase.withTransaction { … }`. Phase 4 §2
proposes the `PurchaseUpgrade` site as the first PR (most user-
visible), with the others as follow-ups.

**Risk of not doing this:** a crash between writes produces silent
state divergence. Balance clamp hides the symptom until the player
notices "why did I get charged but not levelled up?"

**Rollback plan:** per Phase 4 §2 — keep the read-then-write path
behind the new `@Transaction` path during migration.

### 2.2 `TimeProvider` abstraction (REQUIRED for deterministic testing)

*Known, improves testability + correctness at midnight boundaries.*
Source: Phase 4 §1; Phase 9 §23 (55% coverage — the lowest of the
25); Phase 2 `intro2codebase.md` §5 ("There is no injected `Clock`
today").

**53 direct `System.currentTimeMillis()` / `LocalDate.now()` calls
across 33 files** (Phase 4 §1 grep count). The existing
default-parameter pattern (`today: String = LocalDate.now().toString()`)
works for use cases but **cannot be threaded into ticker loops** in
`LabsViewModel` or `MissionsViewModel`, and cannot enforce
consistency across sibling calls within a single round.

Phase 4 §1 proposes the narrow three-migration-sites first PR:

- `domain/time/TimeProvider.kt` (interface, pure Kotlin).
- `data/time/SystemTimeProvider.kt` (default impl) +
  `di/TimeModule.kt` `@Binds`.
- Migrate three call sites (`AwardBattleSteps`, `BattleViewModel.endRound`
  date lookup, `MissionsViewModel` ticker).

This is an **additive** architecture change — no existing use case
changes signature (default params stay), no existing test breaks.
Post-PR, new features would adopt `TimeProvider` by convention; the
53 legacy sites can be migrated opportunistically or never.

**Open desired-state question:** does `TimeProvider` also include
`nanoTime()` for `GameLoopThread`? The game loop explicitly opted
out of injected time
(`devdocs/archaeology/intro2codebase.md` §5 accepts
`System.nanoTime()` direct). Keep it out of the interface unless
needed.

### 2.3 Deep-link coverage for all 12 routes (REQUIRED to fulfil notification contract)

*Known, improves reliability.* Source: Phase 8 §3; Phase 9 §21 gap;
`devdocs/archaeology/traces/trace_10_supply_drop_to_deep_link.md` §8.

`MainActivity.pendingNavigation` collector handles only **5 of 12
routes** (Home, Workshop, Battle, Missions, Supplies).
Store/Stats/Weapons/Cards/Economy/Settings cannot be reached from a
notification — a future `navigate_to=store` intent silently lands on
Home. No test asserts coverage.

The 12 notification sources (step channel, supply-drop,
milestone, reminder) all legitimately want access to any screen.
Smallest change: a `Screen.fromRoute(name: String)?` factory + a
single `when` branch in the collector that navigates to *any* matched
route, falling through to Home if unknown.

**Related architectural item:** type-safe routes via Navigation
Compose's typed-route API would catch this at compile time, but
retrofitting is a meaningful refactor (Phase 9 §21 divergence). Do
the minimal `when` extension for v1.0; schedule typed routes only if
routes grow past 12.

### 2.4 `MissionProgressTracker` extraction (not strictly required, but architecturally warranted)

*Inferred, improves maintainability.* Source: Phase 8 §3 ("mission
progress duplicated across 5 sites"); Phase 9 §17 risk 1.

Five sites call `DailyMissionDao` or the progress-update flow
directly:

- `presentation/battle/BattleViewModel.kt` (battle missions)
- `presentation/labs/LabsViewModel.kt` (research missions)
- `presentation/workshop/WorkshopViewModel.kt` (upgrade missions)
- `presentation/missions/MissionsViewModel.kt` (midnight regen +
  walking step re-check)
- `data/sensor/DailyStepManager.kt` (walking missions)

Two of these (Workshop, Labs) also exemplify the Phase 8 §8
"forbidden-direction imports": presentation layer reaching into
`data.local.*Dao`.

**Smallest next step (PR-sized):** extract
`domain/usecase/UpdateMissionProgress.kt` that takes a
`MissionCategory` and a delta, owns the re-read + clamp + write in a
`@Transaction` (composes cleanly with §2.1). Migrate the 5 call sites
one per PR. Each migration also removes one forbidden-direction
import.

### 2.5 `FollowOnPipeline` extraction from `DailyStepManager`

*Inferred, improves maintainability.* Source: Phase 4 §4; Phase 8 §3
"fat modules"; Phase 9 §1 risk 1.

`DailyStepManager` has 12 constructor dependencies (Phase 4 counted
11 before the 12th audit — Phase 8 updated to 12) and mixes two
responsibilities: (a) the pure anti-cheat-gated crediting pipeline
(rate → velocity → ceiling → persist), (b) the 5-stage fan-out
(widget, supply drop, daily login, weekly challenge, walking
missions).

Phase 4 §4 proposes a mechanical `FollowOnPipeline` extraction with
zero behaviour change. The refactor is purely structural and unlocks:

- Independent unit testing of the follow-on stages.
- Removing the Phase 9 §18 "Season Pass bonus leak" (see §3 below)
  by giving `TrackDailyLogin` a single call site that passes the
  flags.
- Shrinking `DailyStepManager`'s surface area so future additions
  (e.g. a new walking-reward type) don't widen the 12-dep
  constructor further.

### 2.6 Non-changes: architecture that is correct as-is

The following are **not** changes required:

- **Multi-module split** (`:domain`, `:data`, `:presentation`).
  Phase 8 §7 confirms zero package-level cycles; the 12
  forbidden-direction imports (Phase 8 §8) are all in presentation
  reaching into `data.local.*Dao`, which a rename + type-safe
  factory pattern resolves. A Gradle module split would enforce
  boundaries at compile time but is disproportionate for a solo
  developer (rationale in Phase 9 §22 divergence).
- **Event bus / channels engine↔VM**. Phase 9 §6 risk 1 names the
  polling (200 ms) as a tradeoff but confirms it is invisible to
  users at the current entity count. Keep polling.
- **Reactive-only `CurrencyDashboardViewModel`**. R2-10 chose a
  hybrid live-profile + snapshot for weekly aggregate on purpose;
  Phase 9 §18 confirms the snapshot refresh on lifecycle entry is
  correct.
- **Clean Architecture itself**. Domain has zero Android imports
  (grep-verified in Phase 8); layer separation is intact. The 12
  forbidden-direction imports are tactical, not structural.

---

## 3. Technical debt blocking progress

"Blocking" here means debt that would make the next meaningful
change harder, error-prone, or unverifiable. Items are ordered by
leverage.

### 3.1 Stub SDK swap has never exercised failure paths

*Known, blocks Plan 31 verification.* Source: Phase 9 §19 risk 5,
§24 risk 5.

`FakeBillingManager` and `FakeRewardAdManager` always return
success. No test exercises:

- Purchase cancelled by user
- Purchase pending
- Billing service disconnected
- Ad failed to load (no fill)
- Ad skipped / rewarded callback not fired
- Receipt verification rejected

When Plan 31 Task 4 lands the real SDKs, there is no regression net
for the above paths. **Before** SDK swap, extend the fakes with
configurable failure modes and add tests for each PurchaseResult /
AdResult sealed variant.

### 3.2 Zero `@Transaction` (cross-cutting)

*Known, blocks correctness hardening.* Already covered in §2.1.
Called out here because it compounds with §3.3 and §3.5 — every
downstream feature that does "credit + increment counter" would
inherit the same risk. The debt is paid down once, lifted for
everyone.

### 3.3 53 direct wall-clock calls (cross-cutting)

*Known, blocks deterministic testing of midnight-edge behaviours.*
Already covered in §2.2.

Notable midnight-boundary hazards that this debt hides (Phase 4 §1
detail):

- `DailyStepManager.todayDate()` reads at dispatch time, not at
  event time — late-day sensor deltas credit to the next day.
- `StepCrossValidator` keys escrow by `"yyyy-MM-dd"`; DST shifts
  the daily boundary.
- `AwardBattleSteps` uses `LocalDate.now()` as default `today`;
  mid-round DST change resets the 2 000/day counter.
- `BattleViewModel.endRound:168` reads `LocalDate.now().toString()`
  for mission lookup; crossing midnight misses the played-day's
  mission.

None of these has been reported by a user; they are latent.

### 3.4 Mid-battle navigation loses round state

*Known, blocks reliability.* Source: Phase 4 §3; Phase 9 §14 risk
(overdrive doesn't expire); Phase 3 trace 10 §9.

`BattleViewModel.onCleared` nulls the `onStepReward` callback but
does not run `endRound()`. If a deep-link fires mid-battle (or the
OS kills the VM), `updateBestWave` + `awardWaveMilestone` +
`updateHighestUnlockedTier` are skipped; daily-mission battle
progress is lost; the round's result is **silently discarded**.

Phase 4 §3 has a PR-sized fix that composes with §2.1 (atomic
writes) and §2.2 (TimeProvider): extract a persistence function
from `endRound`, call it from `onCleared` via a scope that survives
VM cancellation (`ProcessLifecycleOwner.lifecycleScope`), wrap each
write in try/catch.

### 3.5 Three overlapping reward vocabularies

*Known, blocks any future reward-type addition.* Source: Phase 8 §4;
Phase 9 Appendix A item 8.

- `domain/model/Currency.kt` — enum (STEPS, CASH, GEMS, POWER_STONES).
  Omits Card Dust.
- `domain/model/SupplyDropReward.kt` — sealed family (Steps, Gems, PS,
  Card Dust).
- `domain/model/MilestoneReward.kt` — sealed family (Gems, PowerStones,
  Cosmetic).

Any future reward-type (e.g. "Season Pass XP", "Event Token") must
update two to three of these to avoid a coverage gap. A single
`Reward` sealed hierarchy would simplify, **but the ripple is wide**
(every VM that type-matches the existing sealed would need to change).
Not recommended until there is actual pressure — e.g. a planned
fourth reward type or a cross-cutting feature like "convert X to Y".

**Debt acknowledgement only**; do not pay down for its own sake.

### 3.6 Loadout caps are VM-only invariants

*Known, blocks confidence in direct DAO paths.* Source: Phase 8 §5
implied invariants; Phase 9 §12 risk 1, §13 risk 2.

`CardLoadout` and `UltimateWeaponLoadout` enforce max-3 in the use
case and VM (`CardsViewModel.kt:114`, `UltimateWeaponViewModel.kt:82`).
No DAO-level guard. A future debug screen or migration that writes
loadouts directly could violate max-3.

No direct DAO path exists today; this is a fragile invariant that
should be tightened if/when a second write path appears. Either a
SQL CHECK constraint (supported in Room via `@Entity(indices = …,
primaryKeys = …)` + manual migration) or a composite DAO method
that validates.

### 3.7 Decrypt-failure recovery leaves zombie DB file

*Known, blocks device-restore resilience.* Source: Phase 5
`missing_concepts_list.md` §2; Phase 9 §5 risk 2, §25 risk 2; Phase
3 trace 12 §9.

`DatabaseKeyManager` wipes the encrypted-passphrase blob and
generates a fresh key on decrypt failure, but **leaves the
SQLCipher-encrypted DB file on disk**. The new passphrase cannot
open the old DB → crash-on-launch loop until the user clears app
data manually.

One-line fix: delete `context.getDatabasePath("steps_of_babylon.db")`
next to the passphrase-blob wipe in `DatabaseKeyManager`. Zero
dependencies; small test added to `RoomSchemaTest`-adjacent
Robolectric suite. This is the highest user-visible risk that is
*not* tracked in any plan file.

### 3.8 Season Pass bonus leaks on sensor path

*Known, correctness bug.* Source: Phase 8 §3; Phase 9 §18 risk 1.

`DailyStepManager.runFollowOnPipeline` invokes `TrackDailyLogin(...)`
**without** Season Pass flags; `HomeViewModel.init` does pass them.
Players who cross the 1 000-step threshold while the app is closed
get the base PS + streak Gems but **miss the +10 Gems Season Pass
bonus**. Players who open the app after crediting complete get the
full reward. Behaviour depends on when the app is open, which is
user-invisible.

Fix is mechanical: `DailyStepManager` reads the profile flags before
calling the use case, or (cleaner) the §2.5 `FollowOnPipeline`
extraction centralises the single call site.

### 3.9 No instrumented / Room-migration / Compose-UI tests

*Known, deliberate trade-off, blocks confidence in framework-level
behaviour.* Source: Phase 9 §24 gap; `README.md` ("Instrumented
tests … are planned but not yet implemented"); Plan 29 notes.

The 412 JVM tests (Robolectric for Android types) cover domain,
use cases, VMs, balance, and round-trip schema. They **do not**
cover:

- Room migrations on a real SQLite instance (only `MIGRATION_7_8`
  exists today — no seeded v7 → v8 rehearsal).
- Compose UI behaviour under config change.
- Foreground service / widget / boot-receiver on a real device.
- SQLCipher-specific edge cases.

This is an accepted v1.0 trade-off (README + STATE.md), but any
future migration ships without test coverage. Add
`RoomMigrationRehearsalTest` that seeds a v7 DB, migrates, and
asserts v8 schema the **next** time a migration is added —
establishes a pattern before it is needed.

### 3.10 Forbidden-direction imports (12 sites)

*Known, blocks Clean Architecture claim.* Source: Phase 8 §8.

Six `domain/` files import `data.local.*Dao`; six `presentation/`
files import `data.local.*Dao`. All 12 are pre-existing and
documented in Phase 4 / foundations docs as tolerated gaps. None is
newly introduced. The §2.4 `MissionProgressTracker` extraction
removes two; §2.5 `FollowOnPipeline` can remove two more
(DAO accessors that move into the pipeline class are no longer
imported from presentation). The remaining eight are single-method
convenience imports that can be replaced by repository methods
one-at-a-time.

**Paid down as a side-effect of other refactors**; not worth a
dedicated PR.

### 3.11 Dead code

*Known, trivial.* Source: Phase 1 / trace 05; Phase 8 §3.

- `MainActivity.PlaceholderScreen` — unused `@Composable` still in
  the file. Delete in passing; not archaeology-worthy on its own.
- `Screen.items by lazy` — documented workaround for a sealed-class
  init-order NPE (commit `1872af9`). **Keep** — the comment is the
  memo; removing the `by lazy` reintroduces the NPE.

### 3.12 Documentation drift

*Known, cosmetic.* Source: Phase 2 `intro2codebase.md` §9; Phase 9
§5 risk 5.

- `docs/database-schema.md` says schema v7; code is v8.
- `AGENTS.md` (this file's input) says "33 file source-index" in
  one paragraph and "412 JVM tests" in another; the latter is
  correct.
- `README.md` says "Instrumented tests ... are planned" — accurate.

One-line fixes; not blocking anything.

---

## 4. What can be incrementally improved

Items in this section are **not required** to ship, would each fit
in a single PR, and compose with each other. Ordered by payback per
unit effort. All five primary items are already fully costed in
`devdocs/archaeology/5_things_or_not.md` — reproduced here as a
cross-reference table rather than duplicated (global rule #3).

### 4.1 Phase 4 cross-reference (5 PR-sized improvement bets)

| # | Improvement | Quality attribute | First PR | Blocks release? | Source |
|---|---|---|---|---|---|
| 1 | `TimeProvider` abstraction | Testability, reproducibility | 1 file + 3 call sites | No | 5_things_or_not §1; §2.2 above |
| 2 | `@Transaction` for multi-writes | Correctness | `purchaseUpgradeAtomic` DAO | No | 5_things_or_not §2; §2.1 above |
| 3 | Resilient `BattleViewModel.endRound` | Reliability, UX | `onCleared` guard + extract | No | 5_things_or_not §3; §3.4 above |
| 4 | Extract `FollowOnPipeline` | Maintainability | Mechanical extraction, 0 behaviour change | No | 5_things_or_not §4; §2.5 above |
| 5 | Surface anti-cheat on Stats | Trust, operability | New `Card { }` reading existing prefs | No | 5_things_or_not §5; §1.10 above |

Each has its own citation, code pointers, risk assessment, rollback
plan, and verification steps in `devdocs/archaeology/5_things_or_not.md`
(Phase 4). They satisfy global rule #5 (exact file paths, risk,
rollback, verification).

### 4.2 Additional incremental improvements (inferred, no Phase 4 entry)

Ordered by payback per effort.

#### 4.2.1 Wipe DB file on decrypt failure

*One-line fix, high-severity recovery bug.* Source: §3.7 above.

- Files: `data/local/DatabaseKeyManager.kt`.
- Change: after wiping the passphrase blob, delete
  `context.getDatabasePath("steps_of_babylon.db")`; let `AppDatabase`
  rebuild from scratch next access.
- Risk: recovery wipes user progress, but progress is already
  unrecoverable in this state; the alternative is crash-on-launch.
- Rollback: revert the three-line change.
- Verification: Robolectric test that stores a DB with one key,
  rotates the key, confirms next open rebuilds rather than crashes.

#### 4.2.2 Deep-link collector covers all 12 routes

*Small reliability improvement.* Source: §2.3 above; Phase 9 §21.

- Files: `presentation/MainActivity.kt`,
  `presentation/navigation/Screen.kt` (add `fromRoute` factory).
- Change: replace the 5-route `when` with a `Screen.fromRoute(name)`
  + `?.let { navController.navigate(it.route) }`. Fall-through to
  Home preserved for unknown strings.
- Risk: a `navigate_to` string that matches a never-intended route
  (e.g. Battle without a tier context) could crash; mitigate with
  route-specific validity checks.
- Rollback: revert the single function replacement.
- Verification: extend `DeepLinkRoutingTest` with 7 cases for the
  currently-unhandled routes.

#### 4.2.3 Configurable failure modes in `FakeBillingManager` / `FakeRewardAdManager`

*Pre-SDK-swap regression net.* Source: §3.1 above.

- Files: `app/src/test/java/.../fakes/FakeBillingManager.kt`,
  `FakeRewardAdManager.kt`.
- Change: add `nextResult: PurchaseResult = PurchaseResult.Success(...)`
  and `nextAdResult: AdResult = AdResult.Rewarded` configuration; let
  tests override.
- Risk: none (fakes are test-only).
- Rollback: revert.
- Verification: extend `StoreViewModelTest` to cover
  `PurchaseResult.Cancelled`, `PurchaseResult.Failed`,
  `PurchaseResult.Pending`. Likewise for `CardsViewModelTest` free-
  pack-via-ad flow with `AdResult.Failed`.

#### 4.2.4 `ClaimMilestone.Cosmetic` branch currently drops reward

*Silent correctness bug.* Source: Phase 8 §3 finding; §1.3 above.

`ClaimMilestone.kt:25` credits Gems and PowerStones but **silently
drops** `MilestoneReward.Cosmetic`. Three declared milestone
cosmetic IDs do not match any `SEED_COSMETICS` entry. Smallest fix:
either

- Make the three IDs match seeded cosmetics (content change, no code
  change), **or**
- Surface a non-fatal error in `ClaimMilestone` when the ID is
  unknown (log + `Result.UnknownCosmetic`).

Resolution depends on §1.3 (which visuals are "the feature") and
should land in the same PR.

#### 4.2.5 Sound settings on a dedicated Settings surface

*Minor UX polish.* Source: Phase 5 `missing_concepts_list.md` §2
("Sound settings location").

Sound mute / volume lives inside `NotificationSettingsScreen`
because it is the only "settings" surface. A future new setting (e.g.
"Battle screen shake toggle") would also have to land there.
Smallest change: rename `NotificationSettingsScreen` →
`SettingsScreen` + split into "Notifications" and "Audio" sections;
update `Screen.kt` route name. No new screen, no new navigation
entry.

#### 4.2.6 In-app privacy-policy link

*One-screen change.* Source: §1.11 above; Phase 5 §3.

Add a "Privacy Policy" row on the renamed Settings screen
(see 4.2.5) that launches the external URL via
`CustomTabsIntent` — once the URL exists (Plan 31 dependency).

#### 4.2.7 Suppress Battle Step Rewards FloatingText when cap hit

*Very minor UX polish.* Source: Phase 9 §3 edge cases.

When the 2 000/day cap is exhausted, `AwardBattleSteps` returns 0
but `FloatingText` still shows `stepReward(type)` (the base amount).
Suppress FloatingText if the use case returned 0 credit. One
`if (credited > 0)` branch in `BattleViewModel.onStepRewardHook`.

### 4.3 Not-on-this-list (per Phase 4 §6)

Phase 4 §6 explicitly excludes the following from "5 Things" because
they are tracked elsewhere, trivial, or less impactful. They remain
out of scope for §4:

- App icon + store assets — Plan 31.
- Real Billing / Ads SDKs — Plan 31.
- Documentation drift (schema version) — one-line fix.
- Deep-link central registry (Phase 4 judged §3 higher payback;
  §2.3 above picks up the minimum viable version).
- Game-loop max accumulator clamp.

---

## 5. What requires a rewrite

**Nothing in the application requires a rewrite.** The architecture
absorbed 30+ plans plus two external remediation rounds (24 sub-
plans total) without structural drift; Phase 8 confirms zero
package-level cycles and a tree that reflects Clean Architecture
intent. The 12 forbidden-direction imports are tactical (§3.10) and
the 5 architectural items in §2 are all additive.

The two candidates for "rewrite" that archaeology flagged were
evaluated and rejected:

### 5.1 Monolithic single Gradle module

*Not a rewrite candidate.* Source: Phase 8 §7; Phase 9 §22 divergence.

A multi-module split (`:domain`, `:data`, `:presentation`) would
compile-time-enforce layer boundaries and eliminate the 12
forbidden-direction imports. The cost-benefit ratio for a solo
developer is poor: incremental build times are already fast, the
existing single module works, and Phase 4 §4 + §2.4 above will
remove 4 of the 12 imports as a side-effect of already-scheduled
work. Revisit **only** if a second developer joins or cross-module
reuse becomes a requirement.

### 5.2 Cosmetic rendering pipeline (the one structural change worth doing)

*Not a rewrite — a new narrow contract.* Source: §1.3 above; Phase 9
§6 risk 5, §19 risk 2, §17 risk 2.

The cosmetic system today has three disconnected parts:

- **Data** — `CosmeticEntity` + `CosmeticDao` +
  `CosmeticRepositoryImpl` + seed rows.
- **UI** — `StoreScreen` + `StoreViewModel` (buy/equip/unequip
  works; buy disabled by R2-11).
- **Renderer** — `GameEngine` / `ZigguratEntity` /
  `ProjectileEntity` / `EnemyEntity` — **no awareness of equipped
  cosmetics at all**.

No entity reads `profile.equippedCosmeticIds`. No `BiomeTheme.kt`
override pathway exists. This is a **missing contract**, not a
broken one — the visual pipeline simply wasn't built because Plan
26 shipped the monetization stub and R2-11 disabled purchases until
the pipeline exists.

**Justification this is "required change short of rewrite":** the
monetization feature is **shipped and disabled**. Any work to
re-enable it requires this new contract. It is the single concept
that blocks a shipped feature (not just a roadmap item). It is
additive — no existing code moves; only new plumbing is added.

**Smallest next step** (per constraint: when desired state is
unclear, propose the smallest clarifying step):

1. Ship **one** cosmetic end-to-end. E.g. the jade ziggurat:
   - Seed a `CosmeticItem(id="ZIG_JADE", category=ZIGGURAT, overrideColors=[…])`.
   - Add `GameEngine.cosmeticOverrides: Map<CosmeticCategory, CosmeticItem>`
     populated from `PlayerRepository.observeProfile().equippedCosmeticIds`.
   - Pass the overrides to `ZigguratEntity.layerColors` in the
     constructor (already data-driven).
2. Update `StoreScreen` to enable the single cosmetic's purchase
   button.
3. Verify: a Robolectric VM test covering "equip updates overrides
   on next battle start"; a manual smoke test on debug APK.

Once the pipeline is proven on one cosmetic, the remaining six
seeded + three milestone cosmetics are content work. Defer the
animated biome transition (Phase 9 §9 — "cinematic" deferred by Plan
27) independently; the cosmetic-on-ziggurat pipeline does not need
it.

### 5.3 Mission progress and reward-vocabulary refactors

*Not rewrites.* Each was evaluated above:

- `MissionProgressTracker` extraction (§2.4) — bounded refactor,
  no data model changes.
- `Reward` sealed-hierarchy unification (§3.5) — acknowledged as
  debt, **explicitly deferred** until pressure appears. Not a
  rewrite candidate.

---

## 6. Risks and unknowns

### 6.1 Known risks (cited above, summarised)

1. **Atomic-write gap produces silent currency divergence** (§2.1,
   §3.2). Low probability per event, cumulative over lifetime.
2. **Midnight-boundary races lose rewards** (§2.2, §3.3). Latent;
   no user report yet.
3. **Mid-battle navigation silently discards round** (§3.4).
   Happens only if player taps a notification mid-round.
4. **Device restore crashes app** (§3.7). Requires the specific
   "restored from Google One to new phone" flow.
5. **Season Pass bonus depends on user's app-opening pattern**
   (§3.8). Invisible to the user; a non-premium user sees no
   symptom, a premium user sees an inconsistent bonus.
6. **Real SDK swap has no regression net for failure modes** (§3.1).
   Discovered only once real SDKs land.
7. **Cosmetic purchases greyed out** (§1.3, §5.2). Player-visible
   "coming soon" label; could read as broken feature.

### 6.2 Unknowns — desired state is ambiguous

Items where the docs do not clearly specify what "correct" looks like.
In each case, the smallest clarifying next step is proposed rather
than inventing a requirement.

1. **Should `SupplyDropTrigger.STEP_BURST` exist?** (§1.4)
   Proposed step: delete or wire. No doc demands either.
2. **Which cosmetics count as "the feature"?** (§1.3, §5.2)
   Proposed step: ship one, measure friction, proceed.
3. **Is boss-priority targeting desired?** (§1.5) Proposed step:
   none — wait for balance or UX signal.
4. **Is a closed-app Labs completion notification desired?** (§1.9)
   Proposed step: none — the `reminders` channel can be repurposed
   later if retention data says yes.
5. **Should Celestial Gate (Tier 11+) have a distinct visual?**
   (§1.12) Proposed step: none — GDD §6.3 defines five biomes and
   stops at "11+" as a bucket.
6. **Is cross-device determinism required for supply drops?**
   (Phase 9 §23 risk 5) — daily missions are date-seeded across
   devices; supply drops are not. No doc says either is required.
   Proposed step: add a comment in `GenerateSupplyDrop.kt` stating
   the observed non-determinism, defer until a leaderboard is
   proposed.
7. **Does the cosmetic pipeline need animation support?** (§5.2) —
   the three milestone cosmetics are "recolour" or "palette swap",
   which static overrides handle. Animation (e.g. breathing glow,
   particle cloud) would require a second phase. Defer.
8. **Is `TimeProvider.nanoTime()` part of the abstraction?** (§2.2)
   — `GameLoopThread` reads `System.nanoTime()` direct and that was
   an accepted trade-off. Proposed step: leave it out of the
   interface; revisit only if the game loop needs test seams.

### 6.3 Explicit non-unknowns

These could look like unknowns but are actually decided:

- **Cloud save / cross-device sync** — explicitly not wanted
  (R05 + Phase 5 §3 compliance). Deletion is "uninstall". No
  further action needed.
- **Analytics / crash reporting** — explicitly not wanted (Phase 6
  `philosophy.md` "privacy > observability"; Phase 5 §3). Play
  Console's crash dashboard is the only post-launch signal.
- **Server-side anti-cheat** — `CONSTRAINTS.md` forbids a backend
  for v1.0. Client-only ladder is the accepted trade-off.
- **CI/CD** — explicitly deferred for v1.0 (Phase 5 §1). Manual
  release gate.

---

## 7. Aggregated posture

Lifting §25 Appendix B from Phase 9 and colouring with this gap
analysis:

| Area | Phase 9 coverage | Release gate | Top gap |
|---|---|---|---|
| Core loop (1–18) | ~90%+ | Pass | `@Transaction` + `TimeProvider` are the cross-cutting fixes; nothing blocks the gameplay itself. |
| Monetization | Skeleton (45%) | **Blocks Plan 31** | Real SDK swap, cosmetic rendering contract (§5.2). |
| Release & security | Partial (75%) | **Blocks Plan 31** | App icon, store assets, privacy-policy URL. |
| Testing | Partial (70%) | Pass | No instrumented / migration tests (accepted v1.0 trade-off). |
| Reproducibility | Partial (55%) | Pass | 53 wall-clock calls — `TimeProvider` (§2.2) unblocks. |

Critical-path order from current HEAD to v1.0:

1. **Ship one cosmetic end-to-end** (§5.2 smallest step). Unblocks
   re-enabling the `StoreScreen` purchase button.
2. **Plan 31** (§1.1). Real billing SDK → real ad SDK → app icon →
   store assets → privacy URL → Play Console tracks.
3. **Post-release** (optional): Phase 4 five-item list (§4.1). Each
   is independently shippable; items 1 (`TimeProvider`) and 2
   (`@Transaction`) compose cleanly into a single PR.

No item in §2 or §3 is a Plan 31 blocker. Every §3 debt item has a
named smallest-PR fix.

---

## Appendix: relationship to prior phases

- **Phase 4 "5 Things"** is the explicit proposal layer. §2 and §3
  of this document promote those proposals to "required" or "tech
  debt" based on comparison against the foundations, rather than
  inventing new items.
- **Phase 5 missing_concepts** is the catalogue; §1 of this
  document promotes catalogue entries to "needs implementation"
  based on doc + roadmap support.
- **Phase 8 architecture_analysis / module_discovery** is the
  structural critique; §2 of this document adopts its findings
  without re-stating them (citations by section).
- **Phase 9 concept_mappings** is the coverage map; §7 of this
  document inherits Appendix B's roll-up and adds release-gate
  colour.

Where this phase adds value beyond Phases 4/5/8/9:

- Separates **known** (in roadmap/STATE/ADR/remediation tier) from
  **inferred** (archaeology only).
- Aligns each gap to **release gate** vs **quality improvement** vs
  **deliberately out of v1.0**.
- Calls out the **one structural refactor short of rewrite** (§5.2,
  cosmetic pipeline) that is not in Plan 31 and is not in Phase 4.
- Surfaces **6 unknowns** where desired state is ambiguous and
  proposes the smallest clarifying step for each.

---

*End of Phase 10 gap analysis. Source of truth verified against
HEAD `a9d0386` on 2026-05-05; grep counts (53 wall-clock calls, 0
`@Transaction`, 12 forbidden-direction imports, 5 mission-progress
sites, 12 `DailyStepManager` constructor deps) all re-checked in
Phase 8 and not re-counted here.*
