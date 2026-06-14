# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** **v1.0.5 (versionCode 21) live on Play internal track** (tag `v1.0.5` on `92a66f8`;
release lane green 2026-06-14, signed AAB uploaded; supersedes v1.0.4/code20) · **981 JVM + 9
instrumented tests** green · schema v12 · **shipped the two post-v1.0.4 fixes** (Battle HUD vertical
offset + `release.yml` `track`→`tracks` — the deprecation annotation is now gone; PR #169 squash
`85ce889`) · v1.0.4 shipped the four look-&-feel waves (#159/#160/#161, #161 fully closed) · **docs
spine reconciled post-v1.0.5** (31-fix multi-agent drift sweep across 19 live docs, 2026-06-14;
in `[Unreleased]`) · launch is judgment-gated on the Closed-Test Readiness Gate (`plan-FORWARD.md`).

## Current objective

- **Look-&-feel Bundle C (#162 feedback/feel) — spec + plan complete and adversarially reviewed; ready to
  implement.** Spec `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-c-design.md` and plan
  `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-c.md` both passed the **Adversarial Review Gate**
  (spec: 29 findings/25 surviving → applied; plan: 36 findings/29 surviving, incl. 1 CRITICAL test-hang →
  applied). Next action: execute the 12-task plan via subagent-driven-development. Scope: haptics
  (`HapticsPreferences` + `Haptics` + Settings toggle), shared 1.12× `PurchasePulse`, Post-Round entrance +
  staggered reward sting, one-shot claim celebrations. Presentation-only; no engine/economy/domain change.
- **NEW standing protocol — Adversarial Review Gate** (CLAUDE.md → Agent protocol): every design spec and
  implementation plan passes a code-grounded multi-dimension review → adversarial refute → confirmed-only
  synthesis Workflow **before** the next stage. Default-on under ultracode; if ultracode is off, flag the
  artifact as unreviewed and ask (don't silently skip).
- **Launch is judgment-gated, not clock-gated** (reframed in PR #145; `plan-FORWARD.md`). v1.0 is live
  on the Play Console **internal** track; we are deliberately staying in internal testing until the
  developer judges the game good enough to promote to **closed** test, made concrete by the Closed-Test
  Readiness Gate (A–G). The old ≥14-day-soak / ≥12-tester clock is a **Phase 2** concern that begins
  *after* promotion to closed — it does not gate the work now. All in-repo release plumbing is done
  (CI release lane live; v1.0.1 fired green).
- **In-repo work = the Closed-Test Readiness Gate** (`plan-FORWARD.md`, A–G). Shipped so far: a
  quick-clear of 8 audit Lows + the latent #35 crash (Gate B + D); **#124** billing signature verify;
  **#146** enemy-counter-drifts-negative; **#127** duplicate daily missions (schema v11→v12) — all
  Gate D; **#154** disable "buy more" at max consistently (Gate F UX polish); **#24** first-launch
  onboarding (**Gate C ticked** — shipped in v1.0.3, no schema change) (see Recently
  shipped). Still open in **Gate D**: **#128** (remaining ~21 Lows: perf/anti-cheat/security groups,
  deferred to v1.1 per the audit grouping). Remaining bigger gate items (now the live work): **#29**
  decision-support (Gate F), **#26** perf/battery (Gate G, device-measured).

## Recently shipped (newest first — see RUN_LOG for detail)

- **2026-06-14 — Bundle C (#162) spec + plan + the Adversarial Review Gate protocol (docs-only, uncommitted to a PR yet).**
  Brainstormed Bundle C (feedback/feel) → spec (`95234a9`) → **spec adversarial review** (29 findings,
  25 surviving, 4 refuted; fixes `67cdbe1`) → 12-task TDD plan (`2fb26c8`) → **plan adversarial review**
  (36 findings, 29 surviving incl. 1 CRITICAL — bare `runTest{}` would hang on the Missions VM `while(true)`
  ticker; fixes `d068ce1`). Plan-review fixes: extracted pure `missionRewardLabel`/`supplyLabel` fns
  (testable without the VM), `@VisibleForTesting cancelForTest()` to stop the ticker, Success-arm-only
  milestone edit (preserve smart-quote escapes), explicit `remember`-import delete-list. **Codified the
  review process** into CLAUDE.md + START_HERE as the standing **Adversarial Review Gate** (`ce70351`),
  with the ultracode-off fallback (flag + ask). No production code yet — implementation is the next session.
- **2026-06-14 — full doc-drift sweep (post-v1.0.5; docs-only, in `[Unreleased]`).** Multi-agent audit
  (67 agents: ground-truth extraction → 11-lane fan-out + cross-doc-coherence + link-integrity lanes →
  per-finding adversarial verification → synthesis). 50 candidates → 47 verified → **31 confirmed fixes
  across 19 live docs**; 3 false positives cleared (dangling paths inside frozen release-notes — left
  untouched). Themes: version/test-count lag (v1.0.2/vc18/960 → v1.0.5/vc21/981 in README/GDD/master-plan);
  shipped-but-pending statuses (#161→PR #167/v1.0.4, #24→v1.0.3, Gate-D #124/#127/#146→v1.0.2 ticked in
  plan-FORWARD); `ResearchType` is **12 not 10** + only AUTO_UPGRADE_AI coming-soon (ENEMY_INTEL wired
  V1X-15b); deleted `Currency.kt` purged from structure/source-files; cloud-save migration v11→v12 → **v12→v13**
  (v11→v12 consumed by #127); lib-room destructive-fallback claim, lib-hilt example DAO names, instrumented
  file count 2→4, DAO providers 12→13, `track`→`tracks`, CardDust→CardCopy, CHANGELOG broken links (8).
  ADR-0005 got a permitted status-only amendment (StubBillingManager deleted). **981 JVM unchanged.**
- **2026-06-14 — v1.0.5 (versionCode 21) released to Play internal track** (tag `v1.0.5` on `92a66f8`,
  via release PR #170). Shipped the two post-v1.0.4 fixes from PR #169: the **Battle HUD vertical-offset**
  fix (player-visible) + the **`release.yml` `track`→`tracks`** deprecation rename. Release collateral
  only (versionCode 20→21, versionName 1.0.4→1.0.5, CHANGELOG `[Unreleased]`→`[1.0.5]`, new
  `release-notes-v1.0.5.md`). CI PR gate + instrumented lane green on #170; release lane green in 7m27s
  (signed AAB, `jarsigner -verify` + #124 license-key step passed; GitHub Release with AAB asset).
  **First release to exercise the new `tracks:` input — and the `track`-deprecation annotation that
  appeared on the v1.0.4 run is now GONE**, confirming the fix end-to-end. Approved player-facing
  "What's new" (188 bytes).
- **2026-06-14 — Post-v1.0.4 follow-ups + spot-check (PR #169 squash `85ce889`; docs `07429fc`).** Cleared
  every open item from the v1.0.4 release audit. **(1) Battle HUD vertical offset** — the in-round HUD
  `Column` carried a stale `top = 80.dp` (quit button `72.dp`) that double-counted a removed status-bar +
  ActionBar offset (`MainActivity` is edge-to-edge + Scaffold supplies the inset; #159 removed the
  ActionBar), leaving the wave header ~53dp below the engine health bar; fixed to `40.dp` / `32.dp`,
  reproduced + re-verified on the emulator. **(2) `release.yml` `track`→`tracks`** deprecation rename
  (verified non-breaking at the pinned action SHA). Both ride the **next** `v*` tag (they sit in
  CHANGELOG `[Unreleased]`, not v1.0.4 which already shipped). **(3) #160 navigate-away loading
  no-reflash** — **verified PASS** on-device via 30fps screen-recording: the `LoadingBox` spinner shows
  only on first cold tab entry, never on re-entry; no code change. Presentation + CI config only; **981
  JVM** unchanged; CI PR gate + instrumented lane green on #169.
- **2026-06-14 — v1.0.4 (versionCode 20) released to Play internal track** (tag `v1.0.4` on `1972f1a`,
  via release PR #168). Shipped the four presentation-only look-&-feel waves merged since v1.0.3/code19
  (#159 design tokens / ActionBar removal; #160 Bundle A; #161 Bundle B PR-B1 #166; #161 Bundle B PR-B2
  #167). **Release collateral only** — versionCode 19→20, versionName 1.0.3→1.0.4, CHANGELOG
  `[Unreleased]`→`[1.0.4]`, new `docs/release/release-notes-v1.0.4.md`. Pre-release adversarial audit
  (4 lenses + 2 refutations) confirmed: presentation-only claim holds against the real diff (no
  DAO/repo/entity/migration/di/service/engine touched), changelog accurate, versionCode 20 unused,
  #124 guard + signing + NDK symbols + release.yml intact. CI PR gate + instrumented `connected` lane
  both green on #168; release lane green (signed AAB uploaded, `jarsigner -verify` + #124 license-key
  step passed; GitHub Release with AAB asset). Approved warm player-facing "What's new" (426 chars).
  Two follow-ups + one spot-check it surfaced were all closed the same day (see the #169 entry above).
- **2026-06-13 — Look-&-feel Bundle B PR-B2: bottom-nav restore-wrong-screen bug fix (#161)** (PR #167,
  merged 2026-06-13 — merge commit `b4f2a2b`; shipped in v1.0.4). Second of the two Bundle-B PRs (PR-B1
  merged via #166). Done under `systematic-debugging` — **reproduced on-device before any fix**. The
  device repro *corrected the reported symptom*: the original "Cards → tap Home → Cards" path did NOT
  reproduce; the bug actually surfaces on returning to the **owning** tab
  (`Home→Workshop→Cards→Stats→Workshop` → lands on Cards). **Root cause:** the canonical multi-back-stack
  idiom (`popUpTo(Home){saveState}`+`restoreState`) saves/restores each tab's whole nested sub-stack, but
  in this flat NavHost Cards/Weapons are push-children of Workshop → folded into Workshop's saved branch
  → resurrected on tab re-entry. **Fix:** tab tap → tab root (`popUpTo(Home)`+`launchSingleTop`, no
  save/restore), extracted to a shared `bottomNavOptions()` builder. The obvious
  `popUpTo(graph.startDestination)` "fix" was confirmed a no-op (Home *is* the flat-graph start).
  System-Back + Home-tile pushes unaffected (don't route through BottomNavBar); cross-tab scroll no
  longer preserved (accepted). Guard: **`BottomNavRestoreTest`** (JVM `TestNavHostController` — drives the
  real shared NavOptions, no Compose rule), **red-before-green verified**; +`navigation-testing` test dep.
  (The Robolectric+Compose-UI-rule harness was abandoned after 6 infra failures — `ActivityScenario`
  can't resolve a host activity under Robolectric, PR-4736; `TestNavHostController` sidesteps it.)
  **979→981 JVM**; lint + assemble green; fix re-verified on-device. **ADR-0023.**
- **2026-06-13 — Look-&-feel Bundle B PR-B1: navigation back affordances (#161)** (PR #166, merged to
  `main`). First of **two sequential** Bundle-B PRs (PR-B2 above is the second).
  Spec → adversarially-reviewed plan (the plan review caught a wrong `WindowInsets(0)` inset approach and
  a per-class vs per-method test-count error — both fixed pre-build) → subagent-driven execution with
  per-task spec+quality review. **New shared `presentation/ui/SobTopAppBar.kt`** (`CenterAlignedTopAppBar`:
  centered title + ArrowBack) rendered ONCE in MainActivity's outer Scaffold `topBar`, gated by the new
  pure `Screen.secondaryTitle(route)` helper → appears on exactly the 8 push-nav secondary screens
  (Weapons/Cards/Supplies/Economy/Missions/Settings/Store/Help), null elsewhere (tabs/Battle/Onboarding).
  Back = `navigateUp()`. Default `TopAppBarDefaults.windowInsets` (bar self-pads the status bar — verified
  on-device the title/arrow sit below the status bar on all 8). Inline titles deleted from
  Settings/Help/Store/Economy/Supplies (now carried by the bar); Missions keeps its two section headers;
  Economy/Supplies actions right-aligned (+ Supplies action row hidden on empty state). **975→979 JVM**
  (+4 `ScreenSecondaryTitleTest`, Robolectric); `testDebugUnitTest lintDebug assembleDebug` green;
  on-device verified all 8 screens + negative cases (no bar on tabs/Battle/Onboarding). No
  engine/economy/concurrency touched; no `Screen.kt` route-list change. No ADR (the back-stack-contract
  ADR lands with PR-B2).
- **2026-06-13 — Look-&-feel Bundle A: correctness & a11y cleanup (#160)** (PR #165, squash `491815b`;
  merged to `main`; issue #160 closed; CI PR gate + `connected` instrumented lane both green). Second
  safe presentation-only wave off the
  2026-06-12 UX review (spec → adversarially-reviewed plan → subagent-driven execution with per-task
  spec+quality review + a final whole-branch Opus review). **New shared `presentation/ui/` layer:**
  `CurrencyDisplay.kt` (`CurrencyType` + `icon()/tint()/label()` + `CurrencyValue`/`CurrencyCost` +
  `formatCurrency` — single source of truth for currency presentation, themed-glyph art later = one-file
  swap), `LoadingBox.kt`, `EmptyState.kt`. **Finished the de-emoji sweep** — every UI-control/currency/
  status glyph across Labs/Cards/Store/Missions/Economy/Weapons/Onboarding/Battle-HUD → Material icons
  (decorative Help headings + onboarding slide icons incl. 🏛️ intentionally left → Bundle E). **a11y:**
  onboarding page-dots row-level `contentDescription` ("Page N of M"); correct per-site descriptions on
  new status icons. **Loading spinners** on 10 menu screens (added `isLoading` to Store + Weapons UiState;
  Battle excluded). **Workshop** defensive empty-state; **Settings** renamed `NotificationSettings*`→
  `Settings*` + retitled (route string unchanged). **Deleted dead `domain/model/Currency.kt`.** Other 4
  bundles tracked: #161 nav (+restore bug), #162 haptics/feel, #163 rarity visuals, #164 font/onboarding-art.
  **973→975 JVM** (+2 `CurrencyDisplayTest`); `testDebugUnitTest lintDebug` + `assembleDebug` green; the
  Battle file touched is the HUD pause glyph only (no engine/renderer). Instrumented + on-device
  navigate-away loading check pending (CI `connected` lane + manual).
- **2026-06-12 — Look-&-feel polish pass (presentation-only; Gate C/F UX)** (PR #159, squash `2dc9a08`;
  merged). Off a full UX/art-direction review (15-agent fan-out + adversarial verify + live on-device
  emulator walkthrough). Headline fixes: **removed the redundant platform ActionBar app-wide** (new
  `res/values/themes.xml` `NoActionBar` + `windowBackground`; `AndroidManifest` `android:theme`;
  pixel-verified the old #1A1B20 bar is now DeepBronze); **hid the bottom nav during onboarding**;
  added **design tokens** (`ui/theme/Type.kt` `SobTypography` + `Shape.kt` `SobShapes` + role/currency/
  status tokens in `Color.kt`, wired in `Theme.kt`); **fixed the LapisLazuli-as-text WCAG fail (1.45:1)**
  via a `LapisLight` token on the Home headline; **de-emoji'd** Home/Economy controls → Material icons;
  **palette-aligned** currency colours; fixed verified bugs (Cards double-Gems header, Stats legend label
  + `toArgb`, biome-title capitalization, Store "1 Gems" plural, thousands separators). 12 files changed
  + 4 new — zero engine/economy/concurrency touched. ADR-0022. (Bundle A above continues this work.)
- **2026-06-12 — #24 first-launch onboarding (Gate C)** (PR #157, merged 2026-06-12; released in
  v1.0.3). Gate-C slice of V1X-22: one-time 4-slide tutorial carousel (walk→spend→battle) + permission
  primer + Settings "Replay tutorial". New `data/onboarding/OnboardingPreferences` (device-local
  SharedPreferences flag — **no Room schema change**), `presentation/onboarding/*`. `MainActivity` chooses
  start destination from a synchronous flag read via pure `Screen.startDestination()`; only the
  cold-permission request branch is gated behind completion; permanent-denial recovery via
  Snackbar→app-settings. **Explain-only — no Steps grant** (rejected welcome-bonus; ADR-0021). Built
  spec-first + adversarially reviewed (spec & plan) before coding; subagent-driven with per-task
  spec+quality review. **960→973 JVM.** #24 stays OPEN for deferred retention scope (D2/D7, wave-5
  celebration, projected-reward estimates — pair with telemetry #23).
- **2026-06-11 — #154 disable "buy more" at max** (PR #156, squash `592097b`; issue closed; Gate F UX).
  At a purchasable's cap the buy control must be un-clickable + visually disabled, consistently. 3/4
  surfaces already correct; fixed the Workshop `UpgradeCard` outlier (`enabled = canAfford && !isMaxed`
  on the `Card`, + pinned `disabledContainerColor` so it keeps the Gold "MAX" tint). 5 regression tests
  pin the "canAfford==false at cap even with MAX_VALUE balance" state contract + no-op-spend guards.
  No schema/economy change (spend logic already refused at cap). **955→960 JVM.**
- **2026-06-11 — full doc-drift sweep** (PR #155, squash `8f1b5bc`; docs-only). 20-doc-cluster
  workflow audit (each finding adversarially re-verified against code) + a deeper manual residual pass.
  Fixed ~60 confirmed drift items across 25 docs: schema v11→v12 sweep, test-count → genericized/955,
  use-cases 32→36, UpgradeType 23→24, SupplyDropTrigger 4→3, nav routes 12→13, **Step Overdrive ghosts**
  (removed R4-01 → Rapid Fire) purged from GDD/battle-formulas/CONSTRAINTS/product, **Card Dust** refs
  (removed R4-08 → copy-based) corrected (kept the legacy `cardDust` DB column note), battle-formula
  errors (crit-research on multiplier not chance; multishot/bounce additive caps 11/10; cash
  fortune+card multipliers; UW per-path R4-06 table), step-tracking overlap-rule inversion + exercise
  types, jarsigner `-strict` contradiction, V1X dead-link + shipped-wave status. Restructures: CHANGELOG
  split into `[1.0.2]`/`[1.0.1]` (git-verified boundary) + fresh `[Unreleased]`; balance-report
  historical banner; release-checklist promoted version-agnostic; `docs/index.md` self-documenting
  comment. New docs: `docs/release/release-notes-v1.0.2.md`, `docs/steering/security-model.md`.
  **Also fixed a real infra bug:** `.gitignore` `release/` was unanchored (matched `docs/release/` too,
  silently swallowing new release docs) → anchored to `/release/`.
- **2026-06-11 — v1.0.2 released to Play internal track** (tag `v1.0.2` on `5298fae`). Shipped the
  whole batch since v1.0.1/code17 (#118–123, #125/126, #121, audit-Lows, #124, #146, #127). No bump
  needed — code 18 / 1.0.2 was already committed (PR #108) but never tagged. Release lane green:
  signed AAB uploaded with 5-bullet player-facing "What's new"; GitHub Release + AAB asset created.
  #124 license-key guard passed (signature verification active in the shipped build).
- **2026-06-11 — #127 duplicate daily missions** (PR #152, squash `605f0a9`; issue closed).
  Check-then-insert generator with no DB uniqueness → two concurrent VM inits each inserted a full
  batch → 6 claimable missions/day. Fix: `(date, missionType)` unique index + `@Insert(IGNORE)` +
  `@Transaction generateForDate`; **schema v11→v12** migration (`MIGRATION_11_12`) dedups via
  `GROUP BY` + `MAX()` (incl. `MAX(claimed)` so a claimed duplicate isn't resurrected). First
  migration with a dedicated test. TDD'd (real-Room); 5-lens adversarial review (11 findings, 4
  minor confirmed + all fixed). 948→955 JVM.
- **2026-06-11 — #146 enemy counter drifts negative** (PR #151, squash `6b5779a`; issue closed).
  Two confirmed causes: SCATTER children bypassed the only `enemiesAlive++`; `EnemyEntity.takeDamage`
  re-fired `onDeath` on a corpse (projectile path #125 didn't cover) → counter + cash/Step
  double-credit. Fix: new authoritative `GameEngine.aliveEnemyCount()` (derived from live entities
  under `entitiesLock`) replaces the removed hand-kept tally; `takeDamage` guarded `if (!isAlive)
  return 0.0`. TDD'd; 4-lens adversarial review (8 findings, 0 real, cause-#2 guard mutation-verified).
  945→948 JVM. No schema change.
- **2026-06-11 — #124 billing signature verification** (PR #148, squash `c610f46`; issue closed).
  New `PurchaseVerifier` seam: client-side `SHA1withRSA` Play-signature check + signed-product/token
  binding, gating both grant paths before `grantOnceAtomic`. Release-build fail-closed Gradle guard +
  CI `PLAY_LICENSE_KEY` secret so fail-open can't ship. TDD'd; two rounds of adversarial review (2
  confirmed findings — release fail-open + product binding — fixed and confirmed closed). 933→945 JVM.
  No schema change. **Deploy prerequisite satisfied:** `PLAY_LICENSE_KEY` secret set in the GitHub
  `release` environment — the next `v*` tag ships with verification active.
- **2026-06-11 — quick-clear audit-Low wave** (branch `fix/quick-clear-gate-b-d`). 8 trivial Lows
  (#16/#17/#20/#21/#22/#30/#33/#43) + the latent #35 card-pack crash, TDD'd + adversarially reviewed.
  HC moved off alpha → 1.1.0 stable (#33). No schema change. 908→933 JVM.
- **2026-06-11 — #121 `daily_step_record` lost-update** (PR #144). Column-targeted DAO upserts replace
  the read-copy-`@Upsert`; concurrent sensor/worker/HC writers no longer clobber. 899→908 JVM. No schema change.
- **2026-06-10 — #125 + #126 battle-perf** (PR #143). Game-loop catch-up clamp (#126) + single-pass
  `getAliveEnemies` (#125). 890→908 JVM across the two waves.
- **2026-06-10 — Dependabot wave** (PR #142). 6 bumps incl. Gradle 9.5.1 + JUnit Jupiter 6.1.0. Dashboard clean.
- **2026-06-10 — 5 Medium+ audit fixes** (#118/#119/#120/#122/#123, PRs #129–#133/#140/#141). 867→890 JVM.
- **2026-06-10 — Codebase audit + CLAUDE.md rewrite** (committed to `main`). 45 findings filed as #118–#128.
- **2026-06-10 — Kiro→Claude Code conversion** (PR #117, ADR-0019). Committed memory spine + SessionStart hook + `/checkpoint`.

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

- **Promotion gate (developer judgment):** the Closed-Test Readiness Gate (`plan-FORWARD.md` A–G) is
  the call to promote internal → closed. Google's ≥12-tester + ≥14-day-soak policy is a downstream
  Phase-2 step that only begins after that promotion (not the current gate).
- **Open audit Lows:** #128 (remaining ~21 Lows — perf/anti-cheat/security groups, deferred to v1.1).
  (#124 purchase signature verification, #146 enemy-counter drift, and #127 duplicate daily missions
  all fixed 2026-06-11.)
- **RO-09 deferred (v1.x backlog):** #3 STEP_MULTIPLIER × cross-validator unit mismatch (needs schema migration);
  #4 currency lifetime-counter desync (display-only); #5 TOCTOU on gem/PS spend (lifetime drift, wallet correct);
  #6 per-kill credit on `viewModelScope` (≤1 step lost on mid-round nav-away).
- **Content/polish debt:** sound assets are placeholder sine tones; cosmetics — 5 ziggurat palettes plumbed
  (zig_jade + zig_obsidian store-purchasable; lapis/garden/sandals milestone-only), rest "Coming Soon" pending art.
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
1. **Look-&-feel follow-ups (Gate C/F UX):** **Bundles #159/#160/#161 all merged AND released in v1.0.4** (PR #168, tag `v1.0.4`); **#161 fully closed** (both Bundle-B PRs shipped). **#162 (Bundle C, feedback/feel) is spec'd + planned + adversarially-reviewed — IMPLEMENTATION IS THE NEXT ACTION** (execute the 12-task plan via subagent-driven-development; see Current objective). Remaining bundles after C, each needing its own spec → plan → PR: **#163** UW/Card rarity visuals, **#164** custom font + onboarding per-slide theming + real ziggurat asset. (Battle HUD offset fixed 2026-06-14 — see `[Unreleased]`.) **Both #160-era spot-checks now cleared on-device** (navigate-away loading no-reflash **verified PASS** 2026-06-14 via 30fps screen-recording — spinner shows only on first cold tab entry, never on re-entry).
2. **Bigger gate items:** #29 decision-support (Gate F), #26 device perf/battery (Gate G, device-measured).
3. **Manual play-feel gates (developer):** A audio feel, E balance — can't be closed from code.
4. **Deferred:** #128 remaining ~21 audit Lows (perf/anti-cheat/security groups → v1.1).

Phase 2 (only AFTER the developer promotes internal → closed):
6. **(External)** Recruit ≥12 testers; ≥14-day closed soak; apply for production access; staged rollout; tag `v1.0.0`.

Backlog (post-launch): V1X waves — see `docs/plans/plan-V1X-roadmap.md` (cloud save #36, i18n #34, telemetry #23, etc.).

## Do-not-touch / fragile zones

- `domain/model/` — stable; balance constants validated by regression tests. `BillingProduct.skuId()` is a stable public API.
- `domain/usecase/` — 36 use cases stable.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions. `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config + AdMob production-ID wiring (don't break the test-ID fallback) + `ndk { debugSymbolLevel = "FULL" }`.
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` — guard against sealed-class init-order NPE (commit 1872af9).
- `release/` — gitignored; `release/upload-keystore.jks` is irreplaceable (now Play-App-Signing-enrolled, mostly historical).
- **Live-price wiring (PR B)** — "fetch once on Store entry" is intentional for v1; don't add resume/locale refresh without re-deriving cache invalidation.
- **GOLDEN × overdrive `fortuneMultiplier` (RO-09 #2)** — 3-site "higher buff wins" invariant, guarded by 4 GameEngineTest entries.
- **`GameEngine.entities` thread-safety (#118)** — every structural mutation/iteration behind the private `entitiesLock`; guarded by `GameEngineConcurrencyTest`.
- **GOLDEN damage layer (#119)** — GOLDEN is a re-derived `goldenDamageMult`, not a stat snapshot. Don't restore snapshot-and-overwrite.
- **Economy spend/claim contract (#122, ADR-0020)** — `spendGems`/`spendPowerStones`/`spendStepsIfSufficient` return Boolean; gate the grant on the result. One-shot claims use guarded `… AND claimed=0` + mark-first.
- **`DailyStepManager` Mutex (#120)** — credit read-check-write under a non-reentrant `Mutex`; don't add an un-locked counter mutation.
- **`GameEngine.getAliveEnemies()` must NOT be cached across a frame (#125)** — `takeDamage` re-fires `onDeath` on a dead enemy; a shared snapshot double-credits kills. Guarded by `R125` GameEngineTest.
- **HUD enemy count is derived, not tallied (#146)** — `GameEngine.aliveEnemyCount()` counts live `EnemyEntity` under `entitiesLock`; the desync-prone `WaveSpawner.enemiesAlive` tally was removed (SCATTER children bypassed its only `++`; `onDeath` re-fires double-counted). Don't reintroduce a hand-kept counter. `EnemyEntity.takeDamage` is guarded `if (!isAlive) return 0.0` (no corpse re-hit → no double-credit). Guarded by 3 `R146` GameEngineTest entries.
- **Game-loop frame clamp (#126)** — `SimulationMath.clampAccumulator` (`MAX_CATCHUP_TICKS = 8`); don't lower below ~8 (a 30fps@4× render legitimately needs ~7.9 ticks/frame). Guarded by `SimulationMathTest`.
- **`daily_step_record` writers must stay column-targeted (#121)** — disjoint-column `ON CONFLICT(date) DO UPDATE SET` upserts, NOT a whole-row read-copy-`@Upsert`. Guarded by `DailyStepDaoTest` + `StepRepositoryImplTest`.
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

## References

- **Memory loop:** `CLAUDE.md` (canonical guide; now incl. the **Adversarial Review Gate** for specs/plans) · `docs/agent/START_HERE.md` (contract) · `docs/agent/CONSTRAINTS.md` · SessionStart hook + `/checkpoint` skill.
- **Bundle C (#162) in-flight:** spec `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-c-design.md` · plan `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-c.md` (both review-passed; implementation pending).
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit:** `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128 + regression specs).
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `docs/release/privacy-policy.md` → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity). Full set in `docs/agent/DECISIONS/`.
