# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** **v1.0.10 (versionCode 26) SHIPPED → Play internal** (tag `v1.0.10` fired green end-to-end —
release run `27821174663` `success`: signed AAB, `jarsigner -verify`, Play-internal upload `status:completed`,
GitHub Release `v1.0.10` w/ `app-release.aab` 15.9 MB). v1.0.10 promotes the **4 fix waves since v1.0.9**
(reliability #236/#195/#193 · graceful-degradation #194/#250 · background-reliability #261/#233 ·
data-integrity #237/#238/#248) via release PR #278 (squash `ffa9973`). **ALL 4 net-new audit HIGHs
(#233/#236/#250/#261) + the last Gate-H `severity:major` soak items (#193/#194/#195) are now SHIPPED.**
Latest content wave MERGED: data-integrity (PR #276, `0f32ac6`; #237/#238/#248 auto-closed; ADR-0030,
single-agent review caught a critical pre-code defect). Earlier waves MERGED: #261/#233 (PR #274, `8b50b13`);
#194/#250 (PR #272, `1811617`); #236/#195/#193 (PR #270, `ebf588a`).
Supersedes **v1.0.9 (vc 25)** · **1254 JVM + 9 instrumented tests**
green (1110 shipped in v1.0.10; +8 reliability wave #251/#249 → 1118; +8 correctness/UX wave
#225/#235/#224/#222 → 1126; +4 privacy/monetization #240/#239/#241 → 1130; +9 perf wave #242/#243 → 1139;
+13 accessibility wave #213/#214/#226 → 1152; +15 test-integrity wave #252/#253 → 1167; +1 architecture-invariant wave #227/#228 → 1168; +1 presentation→data cleanup #219/#229 → 1169; +26 i18n correctness wave #259/#260 → 1195; +1 #220 domain-purity guard hardening → 1196; +9 GameEngine decomposition #230/#231 → 1205; +8 process-death state survival #234 → 1213; +17 time-axis anti-cheat #211/#258 → 1230; +4 HomeScreen Compose UI #253 → 1234; +20 critical-screen Compose UI #253 → 1254; all `[Unreleased]`) · schema v12 · all closed-test Gate A–G in-repo items MERGED · **all 3 Gate H `severity:blocker`s MERGED:** #190 + #191
(crash visibility + the two reachable battle CMEs — PR #204, `d673386`) and #192 (privacy/Data-Safety
text — PR #205, `0019217`). **Remaining to promote internal → closed:** (a) the **manual Play Console
Data-Safety action** for #192 (documented in `docs/release/data-safety-form.md` — cannot be done from the
repo); (b) the `severity:major` soak-hardening items are now ALL addressed — **#195 + #193 MERGED via
#270; #194 (error states, UX-1) MERGED (PR #272, `1811617`)** — #194 had been
prematurely closed 2026-06-17 with no implementing commit, verified unfixed at HEAD + re-opened 2026-06-19;
(c) a `v*` release tag to ship the `[Unreleased]` work (#190/#191/#192 + #236/#195/#193 + #194/#250) to internal.
Latest audit
(`docs/reviews/2026-06-18-complete-app-review.md`, supersedes 2026-06-17) verdict: **7/10 — continue
building** (keep shipping internal, NOT public-ready); it filed **38 net-new Med+ issues #224–#261 + Low
tracker #262** — none are internal-track blockers; its 4 net-new HIGHs (#233/#236/#250/#261) were the
highest-leverage before-public work and are **now ALL fixed** (#236 PR #270; #250 PR #272; #261 + #233
PR #274, `8b50b13`). The larger **#233 clean Simulation-hoist** (ADR-0012) +
the med/low backlog (#262) remain.

## Current objective

- **CURRENT (IN FLIGHT — PR open, awaiting controller merge; `[Unreleased]`).**
  **Staged repo-wide ktlint auto-format — stage 1 of 6 (`domain/`).** Mechanical `ktlint -F` over
  `domain/` only (72 files), pure-formatting / zero behaviour change; all hunks on the Bucket-A
  allowlist (trailing-comma, signature reflow, expression-body, if/when bracing). **Both baselines
  regenerated:** ktlint `config/ktlint/baseline.xml` **9256 → 8534** (full-`app/src` scope, shrinking
  per stage); detekt `config/detekt/baseline.xml` **496 → 489** (the reflow drifted line-keyed
  signatures of pre-existing baselined smells — no genuinely-new smell). **1254 JVM tests green, 0
  failures**; `lint-kotlin.sh` check + `:app:detekt` both exit 0. **Next:** controller merges; then
  stages 2–6 (`data/` → `service/`+`di/`+top-level → `presentation/` excl battle → `presentation/battle/`
  [FRAGILE] → test sources), each repeating scoped `-F` + dual-baseline regen + full-suite gate. Plan:
  `docs/superpowers/plans/2026-06-23-ktlint-repo-wide-format-staged.md`.
- **Previous objective (DONE — MERGED PR #318, squash `a218c09`; `[Unreleased]`).**
  **Compose UI tests: critical screens (#253).** 20 Robolectric-backed tests for 7 critical screens
  (Workshop, Store, Labs, Missions, UltimateWeapons, Supplies, BattleControlRail). Covers
  purchase/claim/equip affordance gating, balance rendering, empty states, loadout caps. **1234 → 1254
  JVM**; no production code change. **Next:** close #253; remaining audit work — i18n #34;
  med/low #262/#128; the larger #233 Simulation-hoist (ADR-0012).
- **Previous objective (DONE — MERGED PR #316, squash `1eb01ed`; `[Unreleased]`).**
  **HomeScreen Compose UI tests (#253).** 4 Robolectric-backed tests for the Home screen (loaded state,
  BATTLE button, first-walk prompt show/hide). Same pattern as Cards/Onboarding. **1230 → 1234 JVM**;
  no production code change. Also closed #260 (its code defects were fixed in PR #302; only #34 prose
  tail remained).
- **Previous objective (DONE — MERGED PR #314, squash `966d049`; #256 auto-closed; `[Unreleased]`).**
  **Gradle dependency verification (#256).** See RUN_LOG for detail.
- **Previous objective (DONE — MERGED PR #312, squash `6236a42`; `[Unreleased]`).**
  **Kotlin lint enforcement: detekt + ktlint CI gate (#311; ADR-0037).** Build-infra + config + CI only —
  no production Kotlin, no schema/economy/engine change, no test-count change (1230 JVM + 9 instrumented
  unchanged). **ADR-0037.** See RUN_LOG for detail.
- **Previous objective (DONE — MERGED PR #309, squash `0baf9bc`; #211/#258 auto-closed; `[Unreleased]`).**
  **Time-axis anti-cheat: clock-tamper resistance (#211) + schema-doc gap-fill (#258).** ADR-0036.
  Pure-domain `TimeIntegrity` (4-slot baseline, `Trusted`/`Rollback`), reboot-durable max-wall-clock floor +
  capped-accrual `trustedWallClock` anchor. Two exploits closed (backward rollback + in-session forward
  jump). 1213 → 1230 JVM (+17). See CHANGELOG + RUN_LOG for detail.
- **Previous objective (DONE — MERGED PR #307, squash `051c1cf`; #234 auto-closed; `[Unreleased]`).** Both CI lanes
  green (build-and-test 7m41s, connected/instrumented 6m35s); squash-merged 2026-06-22; branch deleted.
  **Process-death state survival (#234, `severity:major`).** Transient UI state Android drops on a
  process kill now survives via `SavedStateHandle` (ViewModels) / `rememberSaveable` (Compose): Workshop
  selected tab, Stats selected period, the Cards **pack-reveal payload** (the reveal-once card-flip
  confirmation — #234's sharpest case), and the onboarding `permissionAsked` flag. The pack-reveal rides
  a new presentation-layer `@Parcelize` **`PackRevealState`** DTO (`presentation/cards/PackRevealState.kt`)
  mapped to/from `List<CardResult>` at the combine boundary — domain `CardResult`/`CardType` stay pure
  (**`DomainPurityTest` green**; `@Parcelize` would import Android). Selections are drop-in
  `getStateFlow(key, default)` combine sources (no behavior change; defaults unchanged); screen +
  `CardsUiState` types untouched. Added the `kotlin-parcelize` plugin (applied via `kotlin("plugin.parcelize")`
  + root `apply false` — AGP-9 bundles it). **No schema/economy/engine change; 1205 → 1213 JVM** (+8);
  `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan each through the **Adversarial
  Review Gate** (spec 25→15 surviving; **plan 21→14, caught 2 critical pre-code defects** — bare
  `runTest{}` vs the VM's `setMain(dispatcher)` scheduler, and two un-migrated VM ctor call sites
  [`UserFeedbackTest`, `CardsScreenTest`]). Subagent-driven execution (9 tasks, spec+quality review on the
  Cards-wiring cutover). **OUT of scope (noted at issue-close):** battle live-round + `RoundEndState`
  overlay (engine holds no serializable snapshot — not survivable without major work); `onboardingComplete`
  already durable via `OnboardingPreferences`; the "onboarding-finish bug" #234 alleged was investigated
  and found NOT real (`OnboardingScreen` persists before `onFinished`). permissionAsked restore is an
  on-device dev step. No ADR (established platform APIs). **Next:** remaining audit majors — #211
  (clock-tamper), #258 (schema docs), #253 (Compose UI tests); i18n #34; med/low #262/#128.
- **Previous objective (DONE — MERGED PR #304, squash `3d33240`; #231 auto-closed; #230 left OPEN by design; `[Unreleased]`).**
  Both CI lanes green (build-and-test 5m17s, connected/instrumented 4m42s); squash-merged 2026-06-22;
  branch deleted. **#230 deliberately NOT auto-closed** — the PR omitted a `Closes #230` directive because
  full closure is a judgment call on the partial-domain-hoist basis (UW *effect-resolution* domain hoist
  deferred to a future ADR-0012 slice needing `EntityProtocol` surgery); close #230 manually if that basis
  is accepted, else leave it open as the tracked follow-up.
  **GameEngine god-class decomposition (#230 · #231; ADR-0012 Phase 4).** The 1233-line
  `presentation/battle/engine/GameEngine` — the app's highest-churn/highest-risk file — split into an
  orchestrator + sole-`entitiesLock`-owner + façade composing four collaborators via narrow host
  interfaces (`BattleHosts.kt` = `UWHost`/`BuffHost`/`CombatHost`): **BattleRenderer** (Canvas + all
  Paint), **UWController** (UW lifecycle + CHRONO/GOLDEN/fortune + #119 re-layer), **BuffTickers**
  (recovery/rapid-fire/lifesteal), **CombatResolver** (damage/death + rewards). Pure cash formulas
  hoisted to `SimulationMath` (#230's domain half, bit-identical). **Strictly behavior-preserving** (move,
  don't rewrite); public API unchanged; thread-safety identical — collaborators hold no monitor, run
  inside the engine's held lock; `GameEngineConcurrencyTest`/`EffectEngineConcurrencyTest` pass
  **UNCHANGED**. **GameEngine 1233 → 618 LOC; every new collaborator < 400; 1196 → 1205 JVM** (+2
  cash-formula + 7 collaborator tests — the latter exercising logic previously trapped behind
  reflection); `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan each through the
  **Adversarial Review Gate** (ultracode ON: spec 37→24 surviving; **plan 34→18, catching 3 critical
  pre-code compile-breakers** — the `tier`→`playerTier` rename/param collision, a missed direct
  `eng.uwStates[0]` test access, a dangling `CashEconomyTest.BASE_CASH_PER_WAVE` ref). Subagent-driven
  execution (12 tasks, two-stage review on the Task-7 cutover; code-quality nits — a dropped `activateUW`
  KDoc + stale `updateUWs→` comment refs — fixed). **ADR-0012 Phase 4.** **#231 closeable; #230 closeable
  on the partial-domain-hoist + explicit-tracking basis** (UW *effect-resolution* domain hoist deferred —
  needs `EntityProtocol` surgery, tracked in ADR-0012; confirm with issue owner if #230 demands the full
  domain migration). **Next:** the #230 close/keep-open call; then remaining audit majors — #234
  (process-death/SavedStateHandle), #211 (clock-tamper), #258 (schema docs), #253 (Compose UI tests);
  i18n #34; med/low #262/#128.
- **Previous objective (DONE — branch `chore/220-harden-domain-purity-guard`, ready to PR; `[Unreleased]`).**
  **Close the data↔domain cycle (#220, ARCH-3).** Investigation found the cycle was **already resolved**
  by the #227/#228/#229 cluster — its back-edge (`domain.usecase → data.local`) is gone, `domain/` has
  zero `data.*` imports (only KDoc doc-links remain), independently re-verified. So #220 is a
  verify-and-close, not a refactor. To lock it ahead of a future `domain` Gradle-module extraction (#27),
  `DomainPurityTest` gains a third check: fails on an **inline fully-qualified** `…data…` reference in
  domain *code* (the import scan would miss it), comment-stripped so KDoc doc-links don't false-positive;
  mutation-verified. **Test-only; 1195 → 1196 JVM**; `testDebugUnitTest lintDebug` BUILD SUCCESSFUL.
  **Closes #220.** No ADR (extends the #228 guard). **Next:** PR; then remaining audit majors — #230/#231
  (GameEngine god-class / ADR-0012 hoist — the big one), #234 (process-death/SavedStateHandle), #211
  (clock-tamper), #258 (schema docs), #253 (Compose UI tests); i18n #34; med/low #262/#128.
- **Previous objective (DONE — MERGED PR #302, squash `7cc02d1`; #259 auto-closed; #260 left open for the
  #34 prose tail; `[Unreleased]`).**
  **i18n correctness wave (#259 plurals · #260 concatenation + raw enum-name surfacing)** off the
  2026-06-18 complete-app-review backlog. **No schema/economy/engine change; 1169 → 1195 JVM** (+26);
  `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan **and** a final whole-branch
  review all through the **Adversarial Review Gate** (ultracode ON: spec 33→31 surviving/2 refuted; plan
  32→28/4 — caught a `coerceIn(0,…)` Long compile bug, the never-wired `fx_step_reward` plural, and 5
  un-updated breaking tests pre-implementation; final 6→6 surviving / 0 critical-major). Subagent-driven
  execution (14 tasks); the structured-payload unit (Tasks 8–10) committed as one compile-coupled commit.
  **#259:** new `res/values/plurals.xml` (13 count-driven `<plurals>`) — Compose via `pluralStringResource`,
  off-Compose (engine seam / notifications / widget / reminder) via `getQuantityString`; flat noun-baking
  strings migrated/split; `PluralsResourceTest` pins one-vs-other. **#260:** extended the `domain/Strings`
  seam (`enemyTypeName`/`waveComposition`/`bossCountdown` + `FakeStrings`) for off-thread engine text;
  new `presentation/ui/EnumLabels.kt` `@StringRes` labels for the raw-`CONSTANT_CASE` enums
  (`UpgradeCategory`×2/`PackTier`/`CardRarity`/UW-rarity/`CosmeticCategory`); `WavePhase` via a
  String→`@StringRes` lookup (uiState stays String so the `== "SPAWNING"` color branch is intact);
  `CosmeticDisplayInfo.category` String→enum; `StatsViewModel` locale-aware `DayOfWeek` short name; and a
  **structured `ClaimReward`** payload (`Bundle`/`Message`/`Generic`, formatted at the Compose boundary)
  replacing the celebration/milestone/supply reward concatenation (`rewardsSummary`/`missionRewardLabel`/
  `supplyLabel` removed; `ClaimCelebrationEvent` type name kept → screens + `Channel.CONFLATED`/ticker
  intact). `NoRawEnumNameInUiTest` widened (`.name.take(`/`.name.lowercase(`). **#20 CARD_COPY** supply-row
  behavior preserved; `SupplyRewardFormatTest` migrated Jupiter→Robolectric. New tests: `PluralsResourceTest`,
  `AndroidStringsTest`, `EnumLabelResTest`, `ClaimRewardFormatTest`, `FakeStrings`, a GameEngine seam test.
  **No ADR** (extends ADR-0014; no new architecture). **Scope: #259 closeable; #260 stays OPEN** — its
  OnboardingSlide/Help *prose* evidence is English-prose extraction deferred to **#34** (not a
  grammatical/enum bug; flag at PR/issue time). **Next:** open the PR; then remaining audit backlog —
  architecture #220 (cyclic data↔domain — smaller now the ports exist), #230/#231 (GameEngine god-class /
  ADR-0012 hoist), #234 (process-death/SavedStateHandle); data-integrity #211; remaining i18n #34/#259-tail;
  med/low #262/#128.
- **Previous objective (DONE — MERGED PR #300, squash `870c938`; #219/#229 auto-closed; `[Unreleased]`).**
  **Presentation→data cleanup (#219 · #229)** — finishes the
  dependency-rule work at the presentation boundary (builds on #227/#228). **Behavior-preserving structural
  refactor; no schema/economy/engine change; 1168 → 1169 JVM** (+1: `PresentationPurityTest`).
  `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan both through the **Adversarial
  Review Gate** (single-agent, ultracode OFF); plan review caught the under-counted test-rewiring blast
  radius (Battle/Workshop/UserFeedback ctor sites). **#219:** 5 ViewModels off the raw DAOs; the
  `CurrencyDashboardViewModel` raw-`WeeklyChallengeEntity` leak removed. 4 new port methods
  (`Mission.observeMissionsForDate`/`observeClaimableCount`, `Milestone.observeClaimedMilestoneIds`,
  `WeeklyChallenge.getLastNWeeks`) — reactive reads stay `Flow`. Central fix: `MissionsViewModel` mapping
  moved off the raw `missionType` String onto `DailyMission.type`. New `PresentationPurityTest` guards the
  boundary (mutation-verified); **documented exception:** `BattleViewModel` keeps `AppDatabase` for the
  end-of-round `withTransaction` seam (the lone allowlisted import). **#229:** persistence-abstraction rule
  recorded — every table has a port; DAO-direct confined to data layer; `BillingReceiptDao` the deliberate
  data-internal no-port exception. **ADR-0035.** Accepted edge-case shift: unknown-`missionType` rows now
  drop (mapNotNull) instead of rendering raw ids; no test covered the old fallback. Both CI checks green.
  Remaining audit backlog: architecture #220 (cyclic data↔domain coupling — likely much smaller now the
  ports exist), #230/#231 (GameEngine god-class / ADR-0012 simulation-hoist), #234 (process-death /
  SavedStateHandle); data-integrity #211; i18n #259/#260; med/low #262/#128.
- **Previous objective (DONE — MERGED PR #299, squash `cfe46f1`; #227/#228 auto-closed; `[Unreleased]`).**
  **Architecture-invariant wave (#227 · #228)** off the
  complete-app-review backlog: restores the Clean-Architecture dependency rule at the
  dependency-DIRECTION level and machine-enforces it. **Behavior-preserving structural refactor; no
  schema/economy/engine change; 1167 → 1168 JVM** (+1: the new DI-agnostic guard; the 9 use-case tests
  were rewired, not added). `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan both
  through the **Adversarial Review Gate** (single-agent, ultracode OFF). **#227:** 9 use cases off the data
  layer → 3 new domain models (`DailyMission`/`DailyLogin`/`WeeklyChallenge`) + 4 ports
  (`Mission`/`Milestone`/`DailyLogin`/`WeeklyChallenge`Repository) + `StepRepository` extension; Room access
  moved to `data/repository/*Impl`. **Atomic-passthrough preserved** (impls inject the real
  `PlayerProfileDao` into the DAO `@Transaction` — guarded-deduct intact, ADR-0027); the spec review
  confirmed this is correct (single `@Singleton AppDatabase`). **#228:** `DomainPurityTest` now forbids
  `…data` + `dagger.`/`javax.inject.` imports; mutation-verified (re-added data import → build fails naming
  the file). 4 new fake repos + `FakeStepRepository` carries the atomic-credit emulation. **ADR-0034.**
- **Previous objective (DONE — MERGED PR #298, squash `7aac895`; #252 auto-closed, #253 left open for
  follow-up screens; `[Unreleased]`).** **Test-integrity wave (#252 · #253)** off the complete-app-review
  backlog: two adversarially-confirmed `severity:major` testing gaps, one **test-only** PR (no
  production-code change; build-file change adds only test-scope Compose deps). **1152 → 1167 JVM** (+15);
  `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan both through the **Adversarial
  Review Gate** (single-agent, ultracode OFF) — **two CRITICALs caught pre-code:** spec — an `:memory:`
  Room DB is a single connection (no WAL pool) so it can't reproduce a concurrent-writer race → switched to
  a **file-backed** temp DB; plan — the `@Transaction` DAO targets have no injectable parking seam, so the
  mutation check must **remove `@Transaction`** (autocommit interleave), not rewrite the body. **#252:**
  `AtomicDaoConcurrencyTest` (7) — 12 concurrent threads + `CountDownLatch` start-gate at a one-success
  balance/claim, invariant-based asserts (exactly-one-winner, never over-spent/double-credited) across the
  guarded deducts, one-shot claims, and the two `@Transaction` composites; deterministic over 3 re-runs;
  **mutation-verified** (removing `@Transaction` from `claimMilestoneAtomic` → 11/12 double-credit). **#253
  (beachhead):** the project's first Compose UI tests, on the **Robolectric/JVM lane** (PR gate, no
  emulator) — `createComposeRule()` + `@GraphicsMode(NATIVE)`, `ui-test-manifest` on `debugImplementation`
  supplies the host `ComponentActivity`, fakes back the real VMs. `CardsScreenTest` (4) + `OnboardingScreenTest`
  (4); both mutation-verified. #253 stays **open** for follow-up screens (Home, Battle controls, Store). No
  ADR (test additions on established patterns). MERGED PR #298 (`7aac895`); both CI checks green.
- **Previous objective (DONE — MERGED PR #296, squash `0ff9662`; #213/#214/#226 auto-closed; `[Unreleased]`).**
  **Accessibility wave (#213 · #214 · #226)** off the complete-app-
  review backlog: three confirmed `severity:major` a11y findings, one combined PR. **No
  schema/economy/engine change; 1139 → 1152 JVM** (+13); `testDebugUnitTest lintDebug assembleDebug`
  BUILD SUCCESSFUL. Spec + plan both through the **Adversarial Review Gate** (single-agent, ultracode OFF).
  Plan review caught a **CRITICAL pre-code bug** (a stateful announcer mutated inside
  `derivedStateOf`/`remember` = side-effect-in-composition → reworked to a pure `(prev,next)` diff
  advanced in a `LaunchedEffect`) + a major (`size(0.dp)` live-region nodes are pruned from the a11y tree
  → `size(1.dp).alpha(0f)`). Both new suites mutation-verified. **#213:** new `OnGold=#4A2618` text-role
  token (~5.99:1; `onPrimary` was `DeepBronze` ~4.19:1, AA-normal fail) + plain-Int `GoldArgb`/`OnGoldArgb`
  consts so the pure `ContrastTest` pins the REAL tokens ≥4.5:1 (regression fails the build);
  `StatusDanger`/`RaritySand` surveyed = icon/fill only, unchanged. **#214:** pure
  `battleAnnouncement(prev,next)` + sealed `BattleAnnouncement` (`BattleAnnouncerTest`, 11) feeding a
  polite Compose live region in `BattleScreen` (invisible `size(1.dp).alpha(0f)` node) — announces
  wave/phase/25%-health/round-over/error from uiState; health bucketed (no 200ms-poll spam); on-device
  TalkBack is a developer step (no Compose UI tests, #253). **#226:** developer chose DEFER — GDD §17
  reworded to a tracked post-v1.0 deferral; survey confirmed no color-ONLY status (wave-phase bar has its
  label; currencies pair tint+icon+value; dashboard goal uses Check/Close shape) → no code change; store
  listing has no a11y claim (unchanged). No ADR. On-device TalkBack confirmation remains a developer step
  (no Compose UI tests, #253). Next: more audit backlog
  (architecture #219–#231; data-integrity #211/#234; i18n #259/#260; med/low #262/#128).
- **Previous objective (DONE — MERGED PR #295, squash `2363359`; both CI checks green; #242/#243
  auto-closed; `[Unreleased]`).** **Performance wave (#242 · #243)** off the 2026-06-18
  complete-app-review backlog: two confirmed `severity:major` perf defects, one combined PR. **No
  schema/economy/engine-formula change; 1130 → 1139 JVM** (+9); `testDebugUnitTest lintDebug
  assembleDebug` BUILD SUCCESSFUL. Spec + plan both through the **Adversarial Review Gate** (single-agent,
  ultracode OFF). Spec: 8 findings (3 major — #242 concurrency model + #243 1× density). Plan: 8 findings
  incl. **F-C, a real bug caught pre-code** (build-once guard must be built-OR-in-flight, not
  `desiredTrack` alone, else an A→B→A-faster-than-a-decode interleave double-decodes). Both new test
  suites **mutation-verified**. **#242 (ADR-0033):** `MusicManager` no longer decodes the 1.3 MB OGG on
  the main thread per Battle↔menu nav — each track's player built at most once OFF the main thread
  (injectable decode executor), cached, switched via pause/seekTo(0)/start; executor runs ONLY the
  decode, ALL state + control on the main thread (posted back via a Handler); desiredTrack/activeTrack
  split, per-track pending-flag dedup, release-vs-in-flight + muted-deferred + #246 null-degrade handled.
  **#243:** per-projectile `trailTimer` (loop-thread-only, under entitiesLock) throttles trail emission
  to one per `TRAIL_INTERVAL=0.03s` of sim-time via pure `advanceTrail` (`ProjectileTrailThrottleTest`),
  capping ~10 simultaneous particles/projectile at any speed (was unbounded at 4×, starving the 200-slot
  pool). Fragile zones intact (`GameEngineConcurrencyTest`/`EffectEngineConcurrencyTest` green). Accepted
  ~1× trail density trade (on-device feel sign-off is a developer step).
- **Previous objective (DONE — MERGED PR #294, squash `78846fe`; both CI checks green; #240/#239/#241
  auto-closed; `[Unreleased]`).** **Privacy / monetization wave (#240 · #239 · #241)** off the 2026-06-18
  complete-app-review backlog: three confirmed before-public privacy/ads-policy findings, one combined PR.
  **Presentation + a single SDK-config call + policy text; no schema/economy/engine change; 1126 → 1130 JVM**
  (+4); `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. Spec + plan both through the
  **Adversarial Review Gate** (single-agent, ultracode OFF — developer chose the lighter review; spec F1–F6
  [0 critical/major]; plan 1 MAJOR — #241 test passes only because `unitTests.isReturnDefaultValues=true`
  absorbs an internal `android.util.Log.w` in `setMaxAdContentRating`, now documented — all applied
  pre-implementation). **#240** in-app Privacy Policy link: new `presentation/ui/PrivacyPolicy.kt`
  `PRIVACY_POLICY_URL` (drift-guarded by `PrivacyPolicyUrlTest`) + a Settings "Privacy Policy" row →
  `MainActivity.openPrivacyPolicy` guarded `ACTION_VIEW` (no-browser = safe no-op); onboarding link declined.
  **#241** AdMob content-rating cap (developer decision: **13+ adult, cap rating only** — no age gate, no
  child-directed flag; refines ADR-0006 Q5): `buildAdRequestConfiguration()` maxAdContentRating=PG via
  `MobileAds.setRequestConfiguration` before the first ad request; `AdRequestConfigurationTest` (3,
  mutation-verified). **#239** `site/index.md` reconciled with the Data-Safety form — all four AdMob
  categories incl. approximate location + PG-cap note, effective date June 18 → **June 20, 2026**,
  `data-safety-form.md:81` synced (live Pages page refresh CONFIRMED by the developer 2026-06-20). **ADR-0032;
  ADR-0006 Q5 amended.** (#239 manual follow-up done; #192 manual Play Console Data-Safety submission remains, separate.)
- **Previous objective (DONE — MERGED PR #292, squash `bc8de3b`; both CI checks green on Gradle 9.6.0; #290 closed as superseded; `[Unreleased]`).**
  **Dependabot all-gradle wave (#290): took 11 of 12 bumps, HELD Kotlin 2.4.0.** Branched from #290's head
  (inherits its Gradle-9.6.0 wrapper regen), reverted the kotlin line to 2.3.0, rebased onto current `main`.
  Per the `dependabot-wave-handling` rule: combine safe bumps into ONE build-verified PR, drop the
  project-blocked one. **Taken:** Gradle wrapper 9.6.0, Compose BOM 2026.06.00, WorkManager 2.11.2, **Play
  Billing 9.1.0** (additive; `BillingClientAdapter` seam absorbs it), play-services-ads 25.4.0, coroutines
  1.11.0, **mockito-kotlin 6.3.0** (mockito-core → 5.23.0 transitively; JDK 17 + Kotlin 2 OK), test-runner
  1.7.0, uiautomator 2.4.0-rc01. **Held — Kotlin 2.4.0** (blocked by TWO unreleased upstream items, researched
  via 2 parallel agents: Hilt's bundled `kotlin-metadata-jvm` caps at metadata 2.3.0 — fix merged dagger#5179
  but unreleased past 2.59.2; AND KSP #2964 — Kotlin 2.4's module-name change breaks code-gen, no fix released;
  fragile workarounds rejected for a load-bearing Hilt/KSP build). `kotlin-compose` plugin tracks the `kotlin`
  ref so it stays 2.3.0 too. **No app source change; 1126 JVM unchanged;** full `testDebugUnitTest lintDebug
  assembleDebug` + benchmark assemble BUILD SUCCESSFUL on Gradle 9.6.0. No ADR (dependency hygiene on the
  established catalog; rationale in the catalog comment + CHANGELOG). **MERGED PR #292 (`bc8de3b`); both CI
  checks green on Gradle 9.6.0; #290 closed as superseded.** Revisit Kotlin 2.4.0 when Dagger > 2.59.2 ships
  AND KSP #2964 is fixed+released (Dependabot will re-propose Kotlin on its own).
- **Previous objective (DONE — MERGED PR #289, squash `1b6465a`; both CI checks green; #199 auto-closed; `[Unreleased]`).**
  **compileSdk 36 → 37 migration + dependency unblock** — reversed the deliberate compileSdk-36 pin that
  recurrently blocked Dependabot. Raised `compileSdk` 37 in all 3 modules (targetSdk stays 36 — compile-only,
  not behavioral; minSdk 34); unblocked **core-ktx 1.19.0 (closes #199), lifecycle 2.11.0, sqlite-ktx 2.6.2**.
  **HC stays 1.1.0** (1.2.x still alpha-only; gate re-based onto "beta/stable"). **No app source/schema/test
  change; 1126 JVM unchanged.** Built spec→plan, **both through the full Adversarial Review Gate** (ultracode:
  spec 27→20 surviving/7 refuted; plan 23→10/13 — all surviving were doc-sync line-precision + verify-rigor,
  applied pre-implementation). **PR-gate CI ran green on a clean runner → CONFIRMED CI auto-provisions
  platform 37** (the central risk). Locally also verified a full `:app:assembleRelease` (R8 at compileSdk 37).
  **ADR-0031.** Local platform-37 install (reproducibility): `sdkmanager "platforms;android-37.0"
  "build-tools;37.0.0"` (stable channel, latest cmdline-tools).
  **Dependabot fallout (resolved):** old grouped #288 (16 updates) auto-closed on rebase (its core-ktx/
  lifecycle/sqlite bumps now on `main`); #287 (all-actions: checkout v7 + gh-release) rebased green + MERGED
  (`fa7e957`; no `pull_request_target` usage so checkout-v7's fork-PR break is N/A). Dependabot reopened the
  remainder as **#290 (12 updates) — STILL FAILING**, now on a NEW conflict (NOT compileSdk): kotlin-compose
  plugin **2.4.0** emits Kotlin-metadata 2.4.0 but **Hilt 2.59.2's bundled kotlin-metadata-jvm caps at 2.3.0**
  → `hiltJavaCompileDebug` fails. **#290 is its own task** (hold the kotlin-2.4.0 bump, or bump Hilt to a
  Kotlin-2.4-compatible release) — left OPEN for a deliberate decision, not merged.
- **Previous objective (DONE — MERGED PR #285, squash `67cf74c`; both CI checks green; #257/#254/#212/#255 auto-closed; `[Unreleased]`).**
  **CI / supply-chain hardening wave** off the complete-app-review backlog: four confirmed findings, one
  combined PR. **Build-infra + config only — no app source / schema / test-count change** (`testDebugUnitTest
  lintDebug assembleDebug` BUILD SUCCESSFUL; 1126 JVM unchanged). **#257** coroutines runtime was floating
  transitively at 1.9.0 while tests ran 1.10.1 → pinned `kotlinx-coroutines-android` via a shared `coroutines`
  catalog ref (release classpath now resolves 1.10.1 everywhere). **#254** schema-drift gate missed
  new-untracked schemas → `git add -N` + `git diff` + `git status --porcelain` belt (locally simulated to
  catch a new file). **#212** wrapper integrity → `distributionSha256Sum` in `gradle-wrapper.properties` +
  `gradle/actions/wrapper-validation` first step in BOTH `ci.yml` and `release.yml`. **#255** Dependabot →
  grouped (`all-gradle` + separate `gradle-wrapper` + `all-actions`). **#256 (dependency-verification
  metadata) DEFERRED** — strict verification would break every weekly Dependabot bump on a bleeding-edge dep
  set; developer chose to defer. No ADR (config hardening on the established Plan-32 CI). This PR's own CI
  run was the first to exercise the new wrapper-validation step + strengthened drift gate (both green).
  Next: back to the audit backlog (perf/policy/architecture/test-integrity clusters; #256 deferred).
- **Previous objective (DONE — MERGED PR #283, squash `0d58aa1`; both CI checks green; #225/#235/#224/#222 auto-closed; `[Unreleased]`).**
  Quick **correctness/UX wave** off the 2026-06-18 complete-app-review backlog: four self-contained,
  low-risk defects, one combined PR. **Presentation + test-only — no schema/economy/engine change;
  1118 → 1126 JVM** (+8); `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. TDD per fix
  (RED guard/test → fix → GREEN). Ultracode OFF → TDD + single-agent self-review (flagged, not silently
  skipped). **#225** six `enum.name.replace('_',' ')` UI sites → shared `toDisplayName()` + new
  `NoRawEnumNameInUiTest` source-scan guard. **#235** Labs/Cards/Store → `collectAsStateWithLifecycle()`
  (Battle stays plain, documented + allowlisted in new `FlowCollectionLifecycleTest`). **#224** pure
  `HomeUiState.showFirstWalkPrompt` + Home `EmptyState` first-walk prompt (`HomeFirstWalkPromptTest`).
  **#222** `DataTransformMigrationsTest` characterizes the 9→10 UW-split + 10→11 card-dedup migrations.
  No ADR. Next: back to the audit backlog (#262/#128 + perf/policy/CI/architecture clusters).
- **Previous objective (DONE — MERGED PR #280, squash `1cc3afe`; both CI checks green; #251/#249 auto-closed; `[Unreleased]`).**
  Before-public **reliability wave**: two confirmed 2026-06-18 complete-app-review `severity:major` defects,
  one combined branch. **No schema change; no economy/engine-formula change** beyond the offline gap-fill
  *crediting path*; **1110 → 1118 JVM** (+8); full `testDebugUnitTest lintDebug assembleDebug` BUILD
  SUCCESSFUL. TDD (RED→GREEN per fix); spec + plan each through a **single-agent adversarial review**
  (ultracode off, developer chose "b") — spec review added a test-mechanics amendment (hoist `antiCheatPrefs`
  mock), plan review caught a missing `BillingProduct` import; both applied pre-implementation. Subagent-
  driven execution: 2 implementers + spec & quality review each + a final whole-branch review (**READY TO
  MERGE**, 0 critical/major). **#251**: new `DailyStepManager.recordTrustedSteps` — HC-verified offline-
  recovery gaps bypass the live-walking rate limiter (skip rate-limit + velocity; keep 50k ceiling +
  STEP_MULTIPLIER; under the non-reentrant #120 mutex via `ensureInitializedLocked`; idempotent via
  `dailySensorTotal`); `StepGapFiller` switched to it; `StepSyncWorker.sensorCatchUp` deliberately stays
  rate-limited (raw-hardware delta, not HC-verified). **#249**: the 3 `StoreViewModel` billing purchase fns
  surface `PurchaseResult.Error.message` via `_userMessage` (Store Snackbar), de-triplicated into a private
  `runPurchase` helper; mirrors `CardsViewModel`. No ADR (bug-fixes on established patterns). Ships on the
  next `v*` tag (currently `[Unreleased]`). Whole-branch review flagged ONE
  accepted minor: user-cancel now shows a "Purchase cancelled" Snackbar (spec-approved parity with
  CardsViewModel; reversible later if undesirable). Remaining audit backlog after this: med/low (#262) +
  the rest of #224–#260; the larger #233 Simulation-hoist (deferred).
- **Previous objective (DONE — SHIPPED v1.0.10 / versionCode 26 → Play internal track).** First release since v1.0.9;
  promotes the 4 fix waves accumulated on `main` (no new features, no schema change). Release PR #278
  (squash `ffa9973`): versionCode 25→26, versionName 1.0.9→1.0.10; CHANGELOG `[Unreleased]`→`[1.0.10]`; new
  `docs/release/release-notes-v1.0.10.md` (Play "What's new" 293 chars, developer-approved); version-pointer
  sync (README/GDD/master-plan). Both CI checks green on #278; on merge, annotated tag `v1.0.10` (message =
  the "What's new" block) fired `release.yml` → run `27821174663` `success` (signed AAB, `jarsigner -verify`,
  Play-internal upload `status:completed`, GitHub Release `v1.0.10` + `app-release.aab` 15.9 MB). Player-facing
  in this release: battery-optimization primer (#261), load-error retry states (#194), offline-purchase
  reconcile (#250), battle portrait-lock (#233). **Manual Play Console Data-Safety action (#192) is still NOT
  done by this tag** — separate human step (`docs/release/data-safety-form.md`). Next: med/low backlog
  (#262 + #251/#249), the larger #233 Simulation-hoist (deferred), and the promote-to-closed judgment call.
- **Previous objective (DONE — MERGED PR #276, squash `0f32ac6`; both CI checks green; #237/#238/#248 auto-closed;
  shipped in v1.0.10).**
  Data-integrity wave: three confirmed 2026-06-18 complete-app-review defects, one combined PR. **No schema
  change; no economy/engine-logic change; 1100 → 1110 JVM** (+10); `testDebugUnitTest lintDebug assembleDebug`
  BUILD SUCCESSFUL. TDD where there's a seam; spec
  (`docs/superpowers/specs/2026-06-19-data-integrity-wave-237-238-248.md`) put through a single-agent
  adversarial review (ultracode off) that **caught a critical pre-code defect**: the #238 catch-branch can't
  be tested through `getPassphrase` under Robolectric (`KeyStore.getInstance` throws before any decrypt), so
  the wipe-vs-rethrow *decision* was extracted as a pure seam. **#237**: pure
  `AppMigrations.validateChain(migrations, liveVersion, floor)` + `MIGRATION_FLOOR=7` → `MigrationChainTest`
  fails the build if a future version bump forgets to register a `Migration` (reads the live version from a
  built DB — `@Database` is `@Retention(CLASS)`, reflection returns null). **#238**: `DatabaseKeyManager`
  wipes the DB **only** when the Keystore alias is provably absent (device restore); transient
  alias-present decrypt failures rethrow (no wipe) — pure `decideOnDecryptFailure(aliasExists)` + injectable
  `keystoreAliasExists` (defaults to "present"/no-wipe). **#248**: `DataDeletionManager` awaits
  `cancelAllWork().result` (bounded `.get(2s)`, main-thread-safe) **before** `database.close()` — closes the
  WorkManager half of the write-after-close race; service-collector half narrowed not eliminated (kept
  `recreate()` + lazy-reopen self-heal). **ADR-0030.** Remaining audit work after this: the larger #233
  clean Simulation-hoist (ADR-0012, deferred) + the med/low backlog (#262) + #251/#249 (step-counting /
  IAP) + the rest of #224–#260.
- **Previous objective (DONE — MERGED PR #274, squash `8b50b13`; both CI checks green; #261/#233 auto-closed;
  `[Unreleased]`).** Background-reliability wave: #261 battery-optimization whitelist primer + #233
  battle portrait-lock. The last 2 net-new HIGHs; **no schema change; 1098 → 1100 JVM** (+2);
  `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. TDD where there's a seam; spec+plan
  (`docs/superpowers/specs/2026-06-19-background-reliability-261-233.md`) put through a lighter
  single-agent adversarial review that **caught a real re-show bug pre-code** (the construction-time
  `shouldOfferBatteryExemption` is stale after the grant, so `batteryPrimerHandled` must be set on BOTH
  primer buttons, not just "Maybe later"). **#261** (GDD's top risk): new injectable
  `BatteryOptimizationStatus.isIgnoring()` → `OnboardingViewModel.shouldOfferBatteryExemption` → a
  contextual/dismissible onboarding granted-branch primer + durable Settings "Background activity"
  re-offer; `MainActivity.requestBatteryExemption` fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
  new manifest `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play-eligible via the FGS-health step service).
  **#233**: per-screen portrait lock in `BattleScreen` (`DisposableEffect` + `LocalActivity`,
  PORTRAIT-on-enter/UNSPECIFIED-on-dispose) makes the config-change engine/VM desync unreachable; minimal
  (battle is portrait-designed, no landscape resources). **ADR-0029.** Tests: `OnboardingViewModelTest`
  (offer iff not exempt); intent firing + portrait lock are build/on-device-verified (documented boundaries).
  Remaining audit work after this: the larger **#233 clean Simulation-hoist** (ADR-0012, deferred) + the
  med/low backlog (#262) + the manual Play Console Data-Safety action (#192) + a `v*` release tag.
- **Previous objective (DONE — MERGED PR #272, squash `1811617`; both CI checks green; #194/#250 auto-closed;
  `[Unreleased]`).** Graceful-degradation wave: #194 shared error state + #250 offline-purchase reconcile;
  **no schema change; 1093 → 1098 JVM** (+5); `testDebugUnitTest lintDebug assembleDebug`
  BUILD SUCCESSFUL. TDD'd; spec+plan
  (`docs/superpowers/specs/2026-06-19-graceful-degradation-194-250.md`) put through a lighter
  single-agent adversarial review that **caught 2 real defects pre-code**: (1) `.catch` must live INSIDE
  `flatMapLatest` or `retry()` is a no-op (stuck-error, inverse bug); (2) `reconcilePendingPurchases()`
  → `connect()` has no internal timeout → must wrap in `withTimeoutOrNull`. **#194** (UX-1, re-opened
  2026-06-19 — was closed 2026-06-17 with no implementing commit; verified unfixed at HEAD): shared
  `presentation/ui/ErrorState.kt` + `error: String?` on 10 UiStates (Battle excluded) + per-VM
  `_retry`/`flatMapLatest`/`.catch`/`retry()`; screens early-return `ErrorState` before the loading
  check. **#250**: shared time-bounded `reconcileBillingSafely()` helper called from
  `MainActivity.onResume` (foreground) + `StepSyncWorker.doWork` (background safety net). **ADR-0028.**
  Tests: `StatsViewModelTest` (throw→error, retry→recover) + `ReconcileBillingSafelyTest` (once/swallow/timeout).
  Remaining HIGHs after this: #233 (config-change battle-state loss [large]); plus #261 (battery
  whitelist) + the med/low backlog (#262). **#194 was the last Gate-H `severity:major` soak item.**
- **Previous objective (DONE — MERGED PR #270, squash `ebf588a`; both CI checks green; #236/#195/#193 auto-closed;
  `[Unreleased]`).** Reliability wave: three confirmed 2026-06-18 complete-app-review defects, one combined PR; **no schema change;
  1081 → 1093 JVM** (+12); `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. TDD'd (RED→GREEN
  per fix); spec+plan (`docs/superpowers/specs/2026-06-18-reliability-wave-236-195-193.md`) put through a
  lighter single-agent adversarial review (ultracode off, per the developer's choice) — its one
  scope-changing finding (drop an unnecessary interface for #193: mockito-core 5.x mocks final classes
  directly) was applied. **#236** (HIGH, data-integrity) premium spend+grant made atomic — new
  `CardDao.openCardPackAtomic` / `UltimateWeaponDao.unlockWeaponAtomic` `@Transaction` methods +
  repository ports; extends ADR-0020 → **ADR-0027**; `OpenCardPack`/`UnlockUltimateWeapon` dropped their
  now-unused `PlayerRepository`. **#195** (STATE-1) MissionsViewModel `var today` → `_today`
  MutableStateFlow + `flatMapLatest` + `refreshDate()` + screen `ON_RESUME` (mirrors Home/Stats).
  **#193** (REL-3) `StepSensorDataSource.isSensorAvailable()` → `OnboardingViewModel.stepSensorAvailable`
  → new highest-priority onboarding final-slide branch steering to Health Connect. No ADR for #195/#193
  (established patterns). Remaining HIGHs after this: #233 (config-change battle-state loss [large]),
  #250 (IAP reconcile), #261 (battery whitelist) + the med/low backlog (#262).
- **Previous objective (DONE) — cutting release v1.0.9 (versionCode 25) to the Play internal track.** First release since
  v1.0.8; promotes 23 commits. Collateral-only PR (no production-code change in the PR itself): bump
  `versionCode` 24→25 / `versionName` 1.0.8→1.0.9 in `app/build.gradle.kts`; promote CHANGELOG
  `[Unreleased]`→`[1.0.9]`; add `docs/release/release-notes-v1.0.9.md` (Play "What's new" 297 chars +
  developer detail); version-pointer doc sync (README/GDD/master-plan). `testDebugUnitTest` green (1081).
  **On merge, tag `v1.0.9` (annotated; tag message = the "What's new" block) → `release.yml` builds the
  signed AAB → Play internal.** Player-facing: #29 Workshop decision-support, #187 Settings-scroll,
  #190/#191 crash/CME fixes, #245 SFX-on-resume, #26 startup perf. **Manual Play Console Data-Safety
  action (#192) is still NOT done by this tag** — separate human step (`docs/release/data-safety-form.md`).
- **Previous objective (DONE) — reliability-hardening wave: 5 confirmed 2026-06-18 audit defects fixed (#244/#246/#245/#232/#247)
  — MERGED (PR #267, squash `8864f5b`; both CI checks green; all 5 issues closed).** Defensive
  bug-fixes, TDD'd (RED→GREEN per fix), no schema/economy/engine-logic change. **#244** FGS
  `startForeground()` crash path → `startForegroundSafely` seam (Log.w + stopSelf; BootReceiver guarded);
  **#246** `MusicManager.createPlayer` NPE → nullable + injectable `playerFactory`, degrades to silent;
  **#245** battle SFX die after background→resume → `ensureSoundManager`/`releaseSoundManager` seams
  (rebuild + re-point engine; null in the destroyed window); **#232** silent `catch{}` in
  `DailyStepManager` → `onPipelineError` seam (Log.w, runs under #120 mutex); **#247**
  `DataDeletionManager` incomplete wipe → added `onboarding_prefs`+`haptics_prefs` + a source-scan guard
  test that fails on drift/unresolved sites. Put through the **Adversarial Review Gate** (4-dimension
  fan-out → adversarial refute, 23 agents; 19 findings → **0 critical, 0 confirmed major** — 3 "major"s
  downgraded to `partial`; **6 worthwhile findings applied** as amendments: #244 onFailure switched
  CrashBreadcrumbStore→Log.w to match #232's single-slot rationale, #245 release nulls the engine ref +
  idempotent guard, #247 test now fails on unresolved args, #232 mutex KDoc caveat, #246/#245 tests
  strengthened to observable-state). **1069→1081 JVM** (+12). `testDebugUnitTest lintDebug assembleDebug`
  BUILD SUCCESSFUL, 0 failures. No ADR (bug-fixes on established patterns).
  Remaining open audit issues: the other 2 net-new HIGHs (#233 config-change battle-state loss [large],
  #236 atomic premium spend [medium, fits ADR-0020], #250 IAP reconcile, #261 battery whitelist) + 43
  med · 95 low (#262 tracker) — none internal-track blockers.
- *(Older objectives trimmed — see RUN_LOG for full history.)*

## Recently shipped (newest first — see RUN_LOG for detail)

- **2026-06-20 — test-integrity + architecture cluster MERGED (all `[Unreleased]`, off the 2026-06-18
  audit backlog; spec+plan each through the Adversarial Review Gate, single-agent).** **Test-integrity**
  (#252 concurrent-contention DAO test / #253 Compose-UI-test beachhead) — PR #298, `7aac895`, +15 JVM →
  **1167** (#253 left open for follow-up screens). **Architecture-invariant** (#227 domain→data
  dependency-rule fix / #228 `DomainPurityTest` strengthened) — PR #299, `cfe46f1`, +1 JVM, ADR-0034.
  **Presentation→data cleanup** (#219 ViewModel DAO/entity-leak / #229 persistence-abstraction rule) —
  PR #300, `870c938`, +1 JVM, ADR-0035 (new `PresentationPurityTest`). The architecture cluster
  #227/#228/#219/#229 is now fully closed; #220/#230/#231/#234 remain.
- **2026-06-20 — three complete-app-review fix waves MERGED (all `[Unreleased]`, off the 2026-06-18 audit
  backlog; spec+plan each through the Adversarial Review Gate, ultracode OFF → single-agent).**
  **Accessibility** (#213 button contrast / #214 battle TalkBack live region / #226 color-blind deferral) —
  PR #296, `0ff9662`, +13 JVM → **1152**. **Performance** (#242 background-music caching / #243
  projectile-trail throttle) — PR #295, `2363359`, +9 JVM, ADR-0033. **Privacy/monetization** (#240 in-app
  policy link / #239 policy-form consistency / #241 AdMob PG content-rating cap) — PR #294, `78846fe`, +4
  JVM, ADR-0032 (+ ADR-0006 Q5 amended). No schema/economy/engine-formula change in any wave.
- **2026-06-19 — full ultracode doc-drift sweep (docs-only, `[Unreleased]`).** Multi-agent `Workflow`
  (59 agents: 9 live-doc-cluster finders, every claim code-grounded → per-finding adversarial refute →
  cross-doc-coherence + link-integrity lanes → refute). 48 candidates → **48 surviving, 0 refuted**;
  deduped to **34 unique fixes across 15 live docs**. Headline theme: the long-standing **test-count
  inflation reconciled** — actual `@Test` count is **1118** (gradle: 1118, 0 failures), but CLAUDE.md
  said 1141, STATE.md 1141 (with a self-flagged but never-reconciled "+23 pre-branch drift"), README
  both 1110 and 1010, CHANGELOG `[Unreleased]` "1133→1141". All reconciled to **1110→1118 (+8)** on the
  real shipped base. Also: `domain/usecase/` **36→39**; SFX **9→7** (.ogg ground truth); 7 per-file
  test counts in source-files.md (CardType 31→32, BillingManagerImpl 14→20, RealPurchaseVerifier 4→9,
  PlayerRepositoryImpl 13→18, DailyStepDao 14→13, CardRepositoryImpl 16→15, SimulationMath 34→42);
  BattleViewModel **16→15-param**; CardsUiState "dust balance"→copy counts; **security-model.md** key-recovery
  row rewritten to the #238 scoped-wipe behavior; **database-schema.md** `fallbackToDestructiveMigration()`→
  `fallbackToDestructiveMigrationOnDowngrade` (+ migration-floor=v7 framing); **lib-room.md** `adjustStepBalance`
  example regained its `MAX(0,…)` clamp; **plan-FORWARD.md** §H/§D blockers #190/#191/#192 + soak #193/#194/#195
  ticked MERGED; **plan-V1X-roadmap** V1X-07/10/11 moved to shipped + 2 `AGENTS.md`→`CLAUDE.md` path refs;
  **plan-31** build pointer v1.0.5→v1.0.10; **plan-32-ci** post-authoring reconciliation note (5 workflows +
  benchmark type-check); **play-store-listing** desc char-count 2,389→2,927 + 3 mis-categorized upgrade bullets
  fixed against `UpgradeType`; **product.md** Workshop 24→"22 Steps-purchasable (MULTISHOT/BOUNCE_SHOT hidden)".
  No app code/schema/test change; **1118 JVM** unchanged.
- *(Older entries trimmed — see RUN_LOG for full history.)*

## What works (current capabilities)

- **Gameplay:** Plans 01–30 + 10b + R + R2 + R3 + R4 complete. Full battle loop, Workshop/Labs/Cards/UWs,
  tier progression, biomes, walking encounters, anti-cheat, milestones/missions, stats/history.
- **First-launch onboarding (#24, Gate C — shipped in v1.0.3):** 4-slide tutorial carousel +
  contextual permission primer + Settings replay; explain-only (no Steps grant).
- **Battle engine:** simulation extracted to pure-domain `domain/battle/` (V1X-09 Phases 1–3 complete,
  ADR-0012) — `GameEngine` is a thin render shell delegating to `Simulation`.
- **Persistence:** Room schema v12 (13 entities, SQLCipher-encrypted), migrations v7→12, decrypt-fail wipe recovery.
- **Monetization:** real Play Billing v8 + AdMob v25 + UMP v4, device-verified; live Store prices.
- **Release infra:** Play Console listing live, internal track active; CI pipeline (Plan 32) — PR gate +
  instrumented emulator lane (both required on `main`) + `v*`-tag release lane to Play internal (ADR-0018);
  v1.0.1 fired green 2026-06-04. Dependabot dashboard clean.
- **Guardrails:** `DomainPurityTest` (machine-enforced domain purity, #27) + the concurrency/economy regression
  guards listed under fragile zones below.

## Known issues / debt

- **CLOSED-TRACK PROMOTION BLOCKERS (2026-06-17 complete-app review, Gate H) — all 3 MERGED:** **#190**
  (crash visibility + game-loop guard — REL-1/REL-2) + **#191** (two reachable battle CMEs — CONC-1/CONC-2)
  via PR #204 (`d673386`, ADR-0026); **#192** (privacy/Data-Safety text — PRIV-1/SEC-1) via PR #205
  (`0019217`). **#192 STILL requires a manual Play Console Data-Safety action** (declare the four AdMob-SDK
  data types per `docs/release/data-safety-form.md`) before promotion — a developer step, not code. Of the
  3 `severity:major` soak-hardening items, **#195** (Missions day-rollover — STATE-1) + **#193** (no-sensor
  silent dead-end — REL-3) are **MERGED (PR #270, `ebf588a`)** and **#194** (no error states — UX-1) is
  **MERGED (PR #272, `1811617`, ADR-0028)** — it had been prematurely CLOSED
  2026-06-17 with no commit; re-opened 2026-06-19 after verifying it was unfixed. All 3 now addressed.
  The merged blockers/fixes ship only on the next `v*` tag (currently `[Unreleased]`). Full report: `docs/reviews/2026-06-17-complete-app-review.md`.
- **Promotion gate:** the Closed-Test Readiness Gate (`plan-FORWARD.md` A–H) is the call to promote
  internal → closed; Gate H (above) must clear first. Google's ≥12-tester + ≥14-day-soak policy is a
  downstream Phase-2 step that only begins after that promotion.
- **Open audit Lows:** #128 (remaining ~21 Lows — perf/anti-cheat/security groups, deferred to v1.1).
  Plus the 2026-06-17 review's before-public/post-launch findings (architecture seam, A11Y contrast,
  no-Compose-UI-tests, Gradle-wrapper validation, clock-tamper TIME-1, i18n) — review §18 Tiers 2–5.
  (#124 purchase signature verification, #146 enemy-counter drift, #127 duplicate daily missions fixed 2026-06-11.)
- **RO-09 deferred (v1.x backlog):** #3 STEP_MULTIPLIER × cross-validator unit mismatch (needs schema migration);
  #4 currency lifetime-counter desync (display-only); #5 TOCTOU on gem/PS spend (lifetime drift, wallet correct);
  #6 per-kill credit on `viewModelScope` (≤1 step lost on mid-round nav-away — confirmed by the 2026-06-17 review:
  BossKilled PS on `viewModelScope` is lost on nav-away; StepReward correctly uses `applicationScope`).
- **Content/polish debt:** audio shipped (V1X-04/05/06 — 7 synthesized `.ogg` SFX + 2 BGM tracks via
  SoundPool/MediaPlayer; only the subjective "feel" assessment remains, Gate A); cosmetics — 5 ziggurat
  palettes plumbed (zig_jade + zig_obsidian store-purchasable; lapis/garden/sandals milestone-only), rest
  "Coming Soon" pending art. The 4 seeded projectile/enemy-skin cosmetics have no render path (#192-adjacent
  FEAT-1, before-public).
- **Phase B debt:** B.4 FollowOnPipeline + B.5 UpdateMissionProgress extraction (ADR-0004, ~1 week, zero user benefit — deferred).
- `BuildConfig.USE_REAL_ADS` consent-prefetch branch is JVM-untested (device-verified). Play "no debug symbols"
  warning persists on every upload (pre-stripped .so files — informational).
- _(Resolved 2026-06-14, post-v1.0.4 — shipping in v1.0.5/code21.)_
  **`release.yml` `track`→`tracks`** deprecation rename (verified non-breaking at the pinned action SHA);
  **Battle HUD vertical offset** — the stale `top = 80.dp` HUD pad double-counted the removed status-bar +
  ActionBar chrome (`MainActivity` is edge-to-edge + Scaffold supplies the inset); fixed to `40.dp` /
  quit-button `32.dp`, reproduced + re-verified on the emulator.

## Top priorities / next actions

Phase 1 (work down the Readiness Gate so the developer can decide to promote — the real current work):
1. **Manual Play Console Data-Safety action for #192** (cannot be done from the repo): in Play Console →
   App content → Data safety, declare the four AdMob-SDK data types + "Contains ads" = Yes + deletion URL
   per `docs/release/data-safety-form.md`. Required before promotion. **(Developer action.)**
2. **Soak-hardening (Gate H `severity:major`) — ALL ADDRESSED:** **#195** + **#193** **DONE (PR #270,
   `ebf588a`)**; **#194** error states (UX-1) **MERGED (PR #272, `1811617`, ADR-0028)**
   — was prematurely CLOSED 2026-06-17 with no commit; re-opened + actually fixed 2026-06-19.
3. **Cut a `v*` release** to ship the `[Unreleased]` work (#190/#191/#192 + #236/#195/#193 + #194/#250) to the internal
   track (version bump + release notes + tag; the release lane handles signing + upload).
4. **(DONE — all 3 Gate H blockers MERGED:** #190/#191 PR #204 `d673386`; #192 PR #205 `0019217`.)
3. **Then the remaining developer-judgment / manual gate items:** **Gate A** in-play audio feel, **Gate E**
   early-tier balance feel. None is code-addressable; the promote decision is yours once Gate H clears,
   then a manual Play Console action on the uploaded AAB.
4. **All other in-repo gate items DONE:** look-&-feel bundles A–E (→ v1.0.4–v1.0.8); **#29** Gate F (MERGED `70ebf53`); **#26** Gate G in-repo slice (MERGED PR #184, device half `[deferred]`); **#44** Gate B.1 (MERGED PR #186); **#187** Settings-scroll (MERGED PR #188) from the Gate-D fresh-install pass.
5. **Deferred — not a blocker:** #128 remaining ~21 audit Lows → v1.1; the review's before-public/post-launch findings (review §18 Tiers 2–5); Gate B.2 cosmetic "Coming Soon" framing.

Phase 2 (only AFTER the developer promotes internal → closed):
6. **(External)** Recruit ≥12 testers; ≥14-day closed soak; apply for production access; staged rollout; tag `v1.0.0`.

Backlog (post-launch): V1X waves — see `docs/plans/plan-V1X-roadmap.md` (cloud save #36, i18n #34, telemetry #23, etc.).

## Do-not-touch / fragile zones

- `domain/model/` — stable; balance constants validated by regression tests. `BillingProduct.skuId()` is a stable public API.
- `domain/usecase/` — 39 use cases stable.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions. `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config + AdMob production-ID wiring (don't break the test-ID fallback) + `ndk { debugSymbolLevel = "FULL" }`.
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` — guard against sealed-class init-order NPE (commit 1872af9).
- `release/` — gitignored; `release/upload-keystore.jks` is irreplaceable (now Play-App-Signing-enrolled, mostly historical).
- **Live-price wiring (PR B)** — "fetch once on Store entry" is intentional for v1; don't add resume/locale refresh without re-deriving cache invalidation.
- **GOLDEN × overdrive `fortuneMultiplier` (RO-09 #2)** — 3-site "higher buff wins" invariant, guarded by 4 GameEngineTest entries.
- **`GameEngine.entities` thread-safety (#118)** — every structural mutation/iteration behind the private `entitiesLock`; guarded by `GameEngineConcurrencyTest`.
- **`GameEngine.uwStates` is on `entitiesLock` too (#191 CONC-2, ADR-0026)** — `updateUWs` iterates it under the tick lock; `initUWs` (main-thread replay) is now wrapped in `synchronized(entitiesLock)`; the 200ms VM poll reads `uwSnapshot()` (a list-structure copy under the lock), NOT `uwStates` directly. Don't add an unlocked `uwStates` structural mutation or re-point the poll at the raw list. Guarded by `GameEngineConcurrencyTest`'s replay-race test.
- **`EffectEngine` has its OWN `effectsLock` (#191 CONC-1, ADR-0026)** — `effects`/`pendingEffects` add/drain/render/clear are guarded; per-effect `update`/`render` + the Canvas draw run OUTSIDE it (snapshot idiom); `removeAll` is a deferred 2nd lock acquisition so `update→removeAll` order is preserved (no 1-frame effect-lifetime change). `pool`/`screenShake` stay loop-confined (unguarded). **Lock order is acyclic: `entitiesLock` (outer) → `effectsLock` (inner) — never the reverse** (EffectEngine holds no GameEngine ref). Guarded by `EffectEngineConcurrencyTest`.
- **Game-loop crash guard (#190 REL-2, ADR-0026)** — `GameLoopThread.run()` wraps per-tick `update()`/`render()` in `try/catch` → record breadcrumb → stop loop → `onLoopError`. The inner `lockCanvas/unlockCanvasAndPost` try/finally MUST stay nested inside the outer catch (render crash unlocks first). `BattleViewModel.onBattleLoopError` sets `battleError` + `roundEnded` (`@Volatile`) and must NOT set `eng.roundOver` (would persist the corrupt round). Don't remove the guard or run the loop unguarded. Guarded by `GameLoopThreadGuardTest` + 2 `BattleViewModelTest` entries.
- **GOLDEN damage layer (#119)** — GOLDEN is a re-derived `goldenDamageMult`, not a stat snapshot. Don't restore snapshot-and-overwrite.
- **Economy spend/claim contract (#122, ADR-0020)** — `spendGems`/`spendPowerStones`/`spendStepsIfSufficient` return Boolean; gate the grant on the result. One-shot claims use guarded `… AND claimed=0` + mark-first.
- **Premium spend+grant is atomic (#236, ADR-0027)** — card-pack and UW-unlock deduct+grant commit/roll back together via `CardDao.openCardPackAtomic` / `UltimateWeaponDao.unlockWeaponAtomic` (`@Transaction` default methods that call `PlayerProfileDao` as a param — same cross-DAO mechanism as `claimMilestoneAtomic`). The guarded deduct runs FIRST inside the tx; `openCardPackAtomic` returns `null` and `unlockWeaponAtomic` returns `false` on insufficient (no grant written). `unlockWeaponAtomic` re-checks already-unlocked INSIDE the tx before deducting (double-tap can't pay twice). Exposed via repository ports so the use cases stay domain-pure — `OpenCardPack`/`UnlockUltimateWeapon` no longer take `PlayerRepository`. Rarity rolling stays pure/seeded in `OpenCardPack` (the DAO only does the writes). The use cases' pre-checks (`gems < cost` etc.) are cheap fast-paths, NOT the guard. Don't reintroduce a separate spend-then-grant or move the deduct out of the tx. Guarded by `PremiumSpendDaoTest` + atomic-path assertions in `OpenCardPackTest`/`UnlockUltimateWeaponTest`; fakes use a `linkedPlayer` wallet seam.
- **Screen error-state pattern (#194, ADR-0028)** — the 10 data-backed screens surface a load error via a shared `presentation/ui/ErrorState.kt` (+ `SCREEN_LOAD_ERROR`); each UiState carries `error: String?` and each VM wraps its data flow in `_retry.flatMapLatest { <combine/map>.catch { emit(errorState) } }` + `fun retry() { _retry.value++ }`. **The `.catch` MUST stay INSIDE `flatMapLatest`** — a downstream catch completes the stream so `retry()` becomes a no-op (stuck-error, the inverse bug). Screens early-return `ErrorState(state.error!!, onRetry = viewModel::retry)` before the loading check (`state.error` is a delegated property → `!!`, not smart-cast). Date VMs (Home/Missions/Stats) fold `_retry` via `combine(_date,_retry){d,_->d}`. **Battle is excluded** (owns `battleError`/overlay, #190). Guarded by `StatsViewModelTest` (throw→error, retry→recover); VM-level only (no Compose-UI harness in repo).
- **Background billing reconcile is time-bounded + best-effort (#250, ADR-0028)** — `MainActivity.onResume` (foreground) and `StepSyncWorker.doWork` (background, 15-min) both call the shared top-level `service.reconcileBillingSafely(billingManager)` = `withTimeoutOrNull(20s)` + catch-all. The timeout is load-bearing: `BillingManagerImpl.connect()` has NO internal timeout (its disconnect callback never resumes), so an offline/stalled device would otherwise hang the worker / leak a coroutine on resume. `reconcilePendingPurchases()` is idempotent + mutex-serialised + connect-guarded + Activity-independent (BillingClient from `@ApplicationContext`). Don't inline the call without the timeout, and don't drop either trigger (Store-open alone misses the 3-day Play auto-refund window). Guarded by `ReconcileBillingSafelyTest`.
- **Battery-exemption primer gating (#261, ADR-0029)** — `OnboardingViewModel.shouldOfferBatteryExemption` = `!BatteryOptimizationStatus.isIgnoring()` is read ONCE at construction → it's STALE after the user grants the exemption. The onboarding granted-branch primer therefore gates re-display on a session-local `batteryPrimerHandled` flag that **BOTH** buttons ("Allow background activity" AND "Maybe later") set — setting it only on "Maybe later" re-shows the primer after the user just allowed. Never block the flow (both paths reach `finish()`). The durable re-offer is the Settings "Background activity" row (onboarding is one-shot). `MainActivity.requestBatteryExemption` fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (falls back to the settings list); manifest must keep `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play-eligible only via the FGS-health step service). Guarded by `OnboardingViewModelTest`.
- **Battle is portrait-locked (#233, ADR-0029)** — `BattleScreen` sets `activity.requestedOrientation = PORTRAIT` in a `DisposableEffect` (via `LocalActivity`), restored to `UNSPECIFIED` on dispose. This makes the config-change engine/VM desync (fresh `remember`ed `GameSurfaceView`/engine vs. surviving `BattleViewModel`) UNREACHABLE by preventing mid-round rotation. The lock is per-screen (battle is a Compose destination in the single MainActivity — a manifest `screenOrientation` would lock the whole app). An entry-time recreate (device was landscape) is harmless: the round starts only after `configure`/`startPollingEngine`, strictly after `isLoading` flips false. Don't remove the lock without restoring orientation on dispose, and don't move it to the manifest (would lock the whole app). The clean fix (hoist `Simulation` to the VM, ADR-0012) is the deferred larger effort.
- **`DailyStepManager` Mutex (#120)** — credit read-check-write under a non-reentrant `Mutex`; don't add an un-locked counter mutation.
- **Trusted gap-fill credit path (#251)** — `DailyStepManager.recordTrustedSteps` is the ONLY credit path that
  bypasses the rate limiter + velocity analyzer; it exists for HC-verified offline-recovery gaps
  (`StepGapFiller`), whose total is independently bounded by Health Connect's own daily aggregate. It MUST run
  under the same non-reentrant #120 `mutex` via `ensureInitializedLocked()` — never call `recordSteps` from it
  (self-deadlock). It KEEPS the 50k `DAILY_CEILING` + STEP_MULTIPLIER and persists the raw gap into
  `dailySensorTotal` (so the next `fillGaps` sees `gap ≈ 0` — idempotent), and intentionally does NOT touch
  `stepsPerMinute` (a multi-minute elapsed window has no single true epoch minute). **Do NOT route live-sensor
  deltas through it** — `StepSyncWorker.sensorCatchUp` credits a RAW-hardware-counter gap (not HC-verified) and
  MUST stay on the rate-limited `recordSteps`. Guarded by 5 `R251` `DailyStepManagerTest` cases.
- **Store purchase-error surfacing (#249)** — the 3 billing purchase fns route through a private
  `StoreViewModel.runPurchase(product)` that sets `_userMessage` on `PurchaseResult.Error` (PENDING vs hard
  error is carried in the message string). `purchaseCosmetic` is a Gem spend — NOT routed through `runPurchase`,
  keeps its own `#122` `spendGems`-gated failure path. The `#194` `flatMapLatest`/`.catch` error-state flow is
  untouched. Don't re-discard `purchase()`'s result. Guarded by `StoreViewModelTest` (error/pending/success).
- **`GameEngine.getAliveEnemies()` must NOT be cached across a frame (#125)** — `takeDamage` re-fires `onDeath` on a dead enemy; a shared snapshot double-credits kills. Guarded by `R125` GameEngineTest.
- **HUD enemy count is derived, not tallied (#146)** — `GameEngine.aliveEnemyCount()` counts live `EnemyEntity` under `entitiesLock`; the desync-prone `WaveSpawner.enemiesAlive` tally was removed (SCATTER children bypassed its only `++`; `onDeath` re-fires double-counted). Don't reintroduce a hand-kept counter. `EnemyEntity.takeDamage` is guarded `if (!isAlive) return 0.0` (no corpse re-hit → no double-credit). Guarded by 3 `R146` GameEngineTest entries.
- **Game-loop frame clamp (#126)** — `SimulationMath.clampAccumulator` (`MAX_CATCHUP_TICKS = 8`); don't lower below ~8 (a 30fps@4× render legitimately needs ~7.9 ticks/frame). Guarded by `SimulationMathTest`.
- **`daily_step_record` writers must stay column-targeted (#121)** — disjoint-column `ON CONFLICT(date) DO UPDATE SET` upserts, NOT a whole-row read-copy-`@Upsert`. Guarded by `DailyStepDaoTest` + `StepRepositoryImplTest`.
- **Migration chain is guarded (#237, ADR-0030)** — `AppMigrations.validateChain(migrations, liveVersion,
  floor=MIGRATION_FLOOR=7)` (pure) + `MigrationChainTest` fail the build if `ALL` isn't a contiguous +1-step
  chain topping out at `AppDatabase.version`. When you bump the schema version you MUST add+register the new
  `Migration` in `ALL` or the test goes red. The live version is read from a built DB
  (`db.openHelper.readableDatabase.version`), NOT annotation reflection (`@Database` is `@Retention(CLASS)`).
  If the floor ever moves off 7, bump `MIGRATION_FLOOR` deliberately (ties to #258's pre-v7 concern).
- **Decrypt-fail recovery is scoped to cause (#238, ADR-0030)** — `DatabaseKeyManager` wipes the DB **only**
  when the Keystore alias is provably absent (device restore); an alias-present decrypt failure is a
  *transient* fault and is **rethrown** (no wipe — preserves non-regenerable progress; retries next launch).
  The decision is the pure `decideOnDecryptFailure(aliasExists)` seam; `keystoreAliasExists` is injectable
  and defaults to "present" (no wipe) if the keystore can't be opened. Don't widen the catch back to
  "wipe on any exception". Tests that override `keystoreAliasExists` MUST reset it in `@After` (JVM-global
  `var` on the `object`). Guarded by `DatabaseKeyManagerTest`.
- **`deleteAllData` awaits work-cancel before close (#248, ADR-0030)** — `DataDeletionManager` blocks on
  `cancelAllWork().result.get(2s)` BEFORE `database.close()` so an in-flight `StepSyncWorker` can't write to
  a closed DB. Keep the bound small (main-thread call, 5s ANR window); don't move `close()` ahead of the
  cancel barrier. The service-collector half of the race is narrowed, not eliminated (documented). Guarded
  by `DataDeletionManagerTest` (SynchronousExecutor; cancel-before-close test).
- **`daily_mission` uniqueness is DB-level (#127)** — `(date, missionType)` unique index + `@Insert(onConflict = IGNORE)` is the authoritative guard against duplicate daily missions; the generator's read-then-insert check is racy on a WAL pool. Don't weaken the index or relax `IGNORE` back to plain `@Insert`. Schema v12; `MIGRATION_11_12` dedups via `GROUP BY` + `MAX()` (keeps `MAX(claimed)`). Guarded by `DailyMissionDaoTest` + `Migration11To12Test`.
- **Billing signature verification (#124, ADR-0005 amendment)** — every wallet grant goes through `PurchaseVerifier.isValidPurchase(originalJson, signature, expectedProductId, expectedPurchaseToken)` BEFORE `grantOnceAtomic`, on BOTH paths. The product+token binding is load-bearing (blocks replaying a signed cheap receipt for an expensive product) — don't credit off the caller's `product` without verifying first. `PLAY_LICENSE_KEY` blank → fail-open is debug/CI only; a **release** build with a blank key is hard-failed by the `app/build.gradle.kts` `taskGraph` guard + the `release.yml` `PLAY_LICENSE_KEY` secret step — don't weaken either or fail-open could ship. Guarded by `RealPurchaseVerifierTest` + `BillingManagerImplTest`.
- **Currency presentation is centralized (#160)** — all currency glyphs render via
  `presentation/ui/CurrencyDisplay.kt` (`CurrencyType.icon()/tint()` + `CurrencyValue`/`CurrencyCost`).
  Adopt themed-glyph art by swapping `icon()` in ONE place; don't reintroduce inline emoji/`%,d` currency
  text on screens. `formatCurrency` uses `Locale.US` grouping for deterministic output (pinned by
  `CurrencyDisplayTest`). The domain `Currency` enum was deleted as dead — `CurrencyType` is the
  presentation-layer home (carries Compose `icon()/tint()`, can't live in the Android-free domain).
- **Onboarding gating + flag location (#24, ADR-0021)** — the first-launch flag is device-local SharedPreferences (`OnboardingPreferences`), intentionally NOT Room (must not sync; reinstall re-shows). In `MainActivity`, `startDestination` reads it **synchronously** via pure `Screen.startDestination()`; **only** the cold-permission request branch is gated behind `onboardingComplete` (service-start/HC-chaining stay ungated — don't widen the gate or step counting breaks for granted users); the deep-link collector gates on live nav state (current route == Onboarding). `Screen.Onboarding` is deliberately **out of** `allScreens`/`argumentFreeRoutes`/`items` (not a public deep-link target) — keep it out (`DeepLinkRoutingTest` pins the exact-13 set). Onboarding is **explain-only — never grant Steps** (preserves the hard invariant). Guarded by `OnboardingRoutingTest` + `OnboardingPreferencesTest` + `OnboardingContentTest` + `OnboardingViewModelTest` + `DeepLinkRoutingTest` navigate_to guards.
- **Top-bar back affordance is centralized (#161, PR-B1)** — the back/up bar renders via ONE
  `presentation/ui/SobTopAppBar.kt` in MainActivity's **outer** Scaffold `topBar`, gated by the pure
  `Screen.secondaryTitle(route)` helper (returns the title for the 8 push-nav secondary screens, null
  for tabs/Battle/Onboarding/unknown). Don't reintroduce per-screen bars or thread an `onNavigateBack`
  param into screens; add/remove a screen's bar by editing `secondaryTitle` in ONE place (pinned by
  `ScreenSecondaryTitleTest`). The bar uses the **default** `TopAppBarDefaults.windowInsets` — the
  topBar self-pads the status bar; do **NOT** set `windowInsets = WindowInsets(0)` (that draws the
  title under the status bar — caught in plan review). Back = `navigateUp()`. `secondaryTitle` must
  NOT touch `Screen`'s `by lazy` route lists (no route change → `DeepLinkRoutingTest` unaffected).
- **Bottom-nav back-stack contract (#161, PR-B2, ADR-0023)** — a bottom-nav tab tap goes to the tab
  ROOT, via the shared `NavOptionsBuilder.bottomNavOptions()` (`BottomNavBar.kt`): `popUpTo(Home.route)`
  + `launchSingleTop`, **NO** `saveState`/`restoreState`. The save/restore idiom resurrected push-children
  (Cards/Weapons are flat-graph children of Workshop, not a nested sub-graph) on tab re-entry. Don't
  re-add save/restore unless you first restructure the graph so each tab owns a nested `navigation{}`
  sub-graph. `popUpTo(graph.startDestination)` is a no-op here (Home IS the flat-graph start — don't
  mistake it for a fix). The regression guard `BottomNavRestoreTest` reuses `bottomNavOptions()` to drive
  the exact NavOptions; keep the builder shared so the test can't drift from the bar. (Note: Compose-UI
  test rules don't work under Robolectric here — `ActivityScenario` can't resolve a host activity, PR-4736;
  use `TestNavHostController` for nav tests.)
- **Haptics + feel are centralized (#162, Bundle C)** — `Haptics`/`PurchasePulse`/`ClaimCelebration` live
  in `presentation/ui/`. Haptics fire via `View.performHapticFeedback` gated by `HapticsPreferences`
  (Settings toggle, default ON, read at *call* time so a toggle takes effect on the next tap — don't
  capture `isEnabled()` at `remember`). The shared 1.12× `PurchasePulse` is `graphicsLayer` scale (no
  reflow) — add it via `Modifier.pulseScale(rememberPulse())`; don't reintroduce a per-screen inline pulse.
  **Claim celebrations fire from a conflated `Channel` VM event** (`MissionsViewModel`/
  `UnclaimedSuppliesViewModel`.`celebration`), gated on `Result.Success` (never on failure/`UnknownCosmetic`;
  `claimAll` only on ≥1 Success via a left-of-`||` fold) — keep it a conflated `Channel`, NOT a
  `SharedFlow`/`StateFlow` (a replay=0 SharedFlow drops pre-subscriber emits + breaks the test harness).
  The Missions VM's `while(true)` init ticker means JVM tests that construct it MUST call the
  `@VisibleForTesting cancelForTest()` or `runTest` cleanup hangs; label *content* is covered by the pure
  top-level `missionRewardLabel`/`supplyLabel` builders (VM tests assert emission *count*). Post-Round
  entrance is hosted in `BattleScreen` keyed on the round-end **nullability** transition, NOT
  `RoundEndState` identity — watch-ad copies must not re-trigger it.
- **Collectibles rarity is centralized (#163, Bundle D)** — all rarity visuals render via
  `presentation/ui/Rarity.kt`. `RarityTier.color()` is a **plain fun** (NOT `@Composable`) so it's
  JVM-testable and callable from `Modifier.rarityBorder`; don't re-add `@Composable` (a parallel
  composable must delegate to it). UW rarity is **derived in the UI from `unlockCost`** (`uwRarityTier`:
  ≤60/61-89/≥90 → RARE/EPIC/LEGENDARY) — there is no domain rarity field; don't add one for this.
  `uwRarityTier`'s ranges (not exact-value matches) + `RarityTest`'s iterate-over-`entries` guard pin
  today's six costs while tolerating a re-price/7th UW. `rarityBorder` **clips to the card shape first**,
  then border + accent bar via `drawWithContent` (a `drawBehind` bar is occluded by the Material3
  container fill; an unclipped bar bleeds past the rounded corners). The Cards `EquippedChip` replaced
  the `primaryContainer` tint (D4 — chip is the sole equipped signal); don't reintroduce the tint. Locked
  UWs show **dimmed** rarity (alpha 0.5f on border + badge), not hidden (D6). Guarded by `RarityTest`
  (pure fns; the `@Composable` pieces are visual-only, verified on-device).
- **Battle bottom chrome is ONE coordinated layout (#171)** — speed/pause/upgrade live on the left rail
  (`BattleControlRail` at `Alignment.CenterStart`, fixed `BattleControlRailDefaults.WIDTH`, `railStartInset`
  = `WindowInsets.systemBars.union(displayCutout).only(Start)`); the UW cooldown bar owns bottom-center
  (`navigationBars` inset + 24dp); the in-round upgrade menu spans the **full screen width** at
  `BottomCenter` and clears the rail **vertically** — `InRoundUpgradeMenu`'s fixed `IN_ROUND_MENU_HEIGHT`
  (240dp) is short enough that the bottom-anchored sheet's top sits below the rail's bottom, so it never
  covers the rail's lower buttons (rail stays tappable while shopping; the list scrolls). **Don't
  reintroduce independent bottom-anchored `padding(bottom = …)` offsets** — that three-way contention is
  exactly the overlap #171 fixed. **Don't shrink the rail's vertical extent or grow `IN_ROUND_MENU_HEIGHT`
  past the rail's bottom edge** without re-checking on-device — that's the only thing keeping the full-width
  menu from covering the rail (the clearance is a Compose layout fact, not a single constant). Rail buttons
  are extracted **verbatim** (only pause has `haptics.tap()`; don't add haptics to the speed/upgrade
  buttons). Modifier order on the rail is width→verticalScroll→background→padding (background after scroll →
  pill wraps the viewport). Landscape HUD↔rail overlap is a known, accepted, de-scoped limitation (battle is
  portrait-designed; manifest doesn't lock orientation). No Compose-rule layout test (PR-4736) — on-device
  is the acceptance gate (verified at 1080×2400). (History: the menu first left-padded to dodge the rail
  horizontally via a `menuStartPadding`/`GAP` coupling + `BattleControlRailTest`; the dev pivoted it to
  full-width-clears-vertically after seeing it on-device, retiring that coupling + test.)
- **Onboarding biome theming + completion beat (#164, Bundle E, ADR-0024)** — the per-slide gradient is a
  SINGLE shared layer behind the `HorizontalPager` (a per-page `Box` can't cross-fade), computed from
  `pagerState.currentPageOffsetFraction` via the pure `crossfadeNeighborIndex` + `lerpArgb` in
  `presentation/ui/ColorLerp.kt` (clamps the neighbour to `[0,lastIndex]` so overscroll can't index OOB);
  reduced-motion → static per-page gradient (no offset blend). The root `Surface` is `Color.Transparent`
  so the gradient shows through — the title stays Ivory because the Scaffold above provides
  `onBackground`=Ivory as `LocalContentColor` (don't set an explicit wrong `contentColor`). The
  **completion beat MUST stay persist-first**: `finish()` calls `completeOnboarding()` FIRST +
  unconditionally, has an `if (finishing) return` latch, then triggers the pulse; `onFinished()` fires
  exactly once from `LaunchedEffect(finishing)` (immediate under reduced-motion). The pulse reuses
  `PurchasePulse` (`rememberPulse()`/`pulseScale()`) on the icon + the two `finish()` CTAs — NOT the
  "Enable step counting" button (it calls `onEnableStepCounting`). Don't reorder the final-slide
  `when` (granted→!asked→denied) or gate navigation on the animation. This sits ON TOP of the existing
  onboarding gating contract (ADR-0021) — that contract is unchanged. The slide model's new `biome`/`art`
  fields are pure (domain enum + marker enum) — keep `OnboardingSlide` Android-free (the JVM
  `OnboardingContentTest` depends on it). Cinzel re-themes Display+Headline app-wide incl. numeric
  Headline content (CurrencyDashboard balance, Home steps hero) — digit legibility is an on-device
  sign-off item, not assumed. **Font-license caveat:** the OFL `.txt` lives at `licenses/OFL-Cinzel.txt`,
  NOT `res/font/` (the AAPT resource merger rejects non-`.ttf/.otf/.xml` files under `res/font/`).
- **Combat-power index (#29)** — a display/ranking **PROXY** only — `CombatPower` returns a bare `Double`,
  must NEVER feed the engine (type-incompatible with the `ResolvedStats` stat-sinks). The value bar +
  Best-Buy badge apply only when **Δpower > 0** (Damage / Attack-Speed / Crit-Chance + Crit-Factor once
  crit chance > 0). Workshop previews increment the **WORKSHOP** dimension via `WorkshopLevels` (NOT
  in-round; spec §5.1). `EvaluateUpgradeValue` + `DescribeUpgradeEffect.workshopPreview` **share**
  `WorkshopLevels` so the Now→Next string + the value delta can't diverge.
- **Benchmark modules + AGP-9 plugin wiring (#26)** — `:baselineprofile` + `:macrobenchmark` are
  `com.android.test` **dev-tooling, never shipped**. benchmark/baselineprofile is pinned **1.5.0-alpha06**
  because stable 1.4.1 throws on AGP 9.0.1 at plugin-apply (`Module :app is not a supported android
  module`) — don't "downgrade to stable" without re-checking AGP-9 support. The two new plugins
  (`android.test`, `androidx.baselineprofile`) are declared `apply false` in the ROOT `build.gradle.kts`
  (pin the version once — a per-module version clashes with AGP on the classpath). **Do NOT add
  `kotlin.android`** to a `com.android.test` module — AGP 9 has built-in Kotlin and applying it errors.
  `profileinstaller` is the ONLY shipping addition (stable 1.4.1). The committed profile lives at
  `app/src/release/generated/baselineProfiles/baseline-prof.txt` (plugin-managed src; R8 + profileinstaller
  consume it). No `connectedBenchmarkReleaseAndroidTest` exists yet — capturing startup *numbers* needs a
  non-debuggable `benchmark` build type on `:app` (a fragile-zone change). ADR-0025.
- **#124 guard is benchmark-variant-aware (#26)** — the fail-closed license-key guard in
  `app/build.gradle.kts` matches the broad `^(bundle|assemble|package).*Release$` regex but excludes
  per-task `!contains("Benchmark") && !contains("NonMinified")` (the baselineprofile plugin auto-generates
  `benchmarkRelease`/`nonMinifiedRelease` which would otherwise false-trip it). Exclusion is **per-task**,
  NOT whole-graph: a combined `bundleRelease`+benchmark graph still hard-fails on a blank key. Don't relax
  to a literal allowlist (drops future flavored release tasks) or to a whole-graph check (fail-open risk).
- **Collision scratch buffers (#26 A28)** — `GameEngine` owns 3 reusable `ArrayList` scratch buffers
  (`projScratch`/`enemyScratch`/`enemyProjScratch`); the per-tick partition over `entities` fills them in
  ONE pass **inside `synchronized(entitiesLock)`** (#118) and hands them to `CollisionSystem.checkCollisions`
  (now takes pre-filtered typed lists, no `filterIsInstance`). The single `enemyScratch` fill serves the
  whole sweep — matching the old single snapshot, so the #146 `takeDamage` corpse-guard still prevents
  double-credit. These are per-tick (cleared every sweep), NOT the #125 cross-frame cache — and `getAliveEnemies()`
  is untouched. Don't move the partition out of the lock or re-filter mid-sweep. Guarded by
  `CollisionSystemScratchTest` + the `A28` GameEngineTest entries.
- **Cached CHRONO_FIELD Paint (#26 A31)** — the full-screen chrono overlay uses a cached
  `chronoOverlayPaint` field (colour `0x222196F3`, FILL), NOT a per-frame `Paint()` alloc. `setChronoActiveForTest`
  is a `@VisibleForTesting` seam. Guarded by `ChronoOverlayPaintTest` (instance-identity across two renders).
- **Profile-Flow dedup (#26 A29)** — `PlayerRepositoryImpl.observeProfile/observeWallet/observeTier` end with
  `.distinctUntilChanged()` AFTER the `.map{}` (dedupe the projected value). Works because `PlayerProfile`/
  `PlayerWallet` are data classes (structural equality). Suppresses no-op re-emissions to every screen
  ViewModel; safe because no consumer uses these as a bare trigger (all `.first()` or `combine`/`stateIn`
  value chains). Don't move the distinct before the map.
- **Labs surfaced-research filter is centralized (#44)** — which `ResearchType`s appear in Labs is decided
  by ONE pure helper, `ResearchType.surfacedInLabs()` (`entries.filterNot { it.isComingSoon }`,
  order-preserving). `LabsViewModel` builds `researchList` from it — don't re-inline
  `entries.filterNot { … }` at the call-site (re-introduces the drift this consolidated). `LabsScreen` no
  longer reads `isComingSoon` (the dead `COMING SOON` UI branches were removed) — don't re-add a per-card
  coming-soon branch; a hidden type never reaches a card. The `LabsViewModel.startResearch`
  `if (type.isComingSoon)` guard is the **reachable** second layer (blocks a Step spend if a future
  deep-link/quick-research caller bypasses the list) — keep it. Guarded by `ResearchTypeTest` (pins the
  helper BODY: excludes coming-soon + equals the 11 wired types); the call-site→helper wiring is held by
  code review + the guard, NOT a test (the VM's `while(true)` ticker blocks cheap VM-construction tests —
  an accepted coverage boundary, per the spec). `AUTO_UPGRADE_AI` stays the sole `isComingSoon=true`
  research type (deferred to v1.x; its real design = "auto-spends Cash on optimal upgrades during rounds").

## References

- **Memory loop:** `CLAUDE.md` (canonical guide; now incl. the **Adversarial Review Gate** for specs/plans) · `docs/agent/START_HERE.md` (contract) · `docs/agent/CONSTRAINTS.md` · SessionStart hook + `/checkpoint` skill.
- **Skills (`.claude/skills/`):** `checkpoint` (end-of-session memory write) · `complete-app-review` (ultracode `Workflow` for the full 20-section audit — every finding refuted by separate subagents 3/3/2/1; writes `docs/reviews/<date>-complete-app-review.md` + emits a propose-then-confirm GitHub-issue plan).
- **Look-&-feel bundle docs (all shipped):** Bundle E (#164, v1.0.8) spec `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` + plan `docs/superpowers/plans/2026-06-15-look-and-feel-bundle-e.md` (both review-passed) · #171 spec/plan `docs/superpowers/{specs,plans}/2026-06-15-battle-bottom-chrome-overlap*.md` · Bundle D (#163, v1.0.7) spec `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-d-design.md` + plan `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-d.md` · Bundle C (#162) shipped in v1.0.6.
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit (run via the `complete-app-review` skill; dated reports are point-in-time artifacts):**
  `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128) · `docs/reviews/2026-06-17-complete-app-review.md` (raised Gate H blockers #190–#192 + soak-hardening #193–#195) · **latest `docs/reviews/2026-06-18-complete-app-review.md` (7/10; 7 high · 43 med · 95 low; filed Med+ #224–#261 + Low tracker #262; 4 net-new HIGHs #233/#236/#250/#261).**
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `site/index.md` (canonical; published to GitHub Pages by `.github/workflows/pages.yml` — `site/` ONLY, not `docs/`) → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity) · 0021 (onboarding explain-only) · 0022 (design tokens + de-emoji) · 0023 (bottom-nav back-stack) · 0024 (Bundle E: custom font + onboarding biome theming + persist-first completion beat) · **0025 (#26 perf/battery Gate-G: multi-module benchmark tooling on AGP-9 [1.5.0-alpha, dev-only] + #124 guard narrowing + A28/A31/A29 GC-churn fixes)**. Full set in `docs/agent/DECISIONS/`.
