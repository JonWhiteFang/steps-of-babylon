# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** **v1.0.3 (versionCode 19) releasing to Play internal track** (tag `v1.0.3`; supersedes
v1.0.2/code18) · **973 JVM + 9 instrumented tests** green · schema v12 · **Gate C (onboarding) merged
to `main`** (PR #157, squash `48e8e63`) · launch is judgment-gated on the Closed-Test Readiness Gate
(`plan-FORWARD.md`).

## Current objective

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
  onboarding (**Gate C ticked** — branch `feat/onboarding-gate-c`, no schema change) (see Recently
  shipped). Still open in **Gate D**: **#128** (remaining ~21 Lows: perf/anti-cheat/security groups,
  deferred to v1.1 per the audit grouping). Remaining bigger gate items (now the live work): **#29**
  decision-support (Gate F), **#26** perf/battery (Gate G, device-measured).

## Recently shipped (newest first — see RUN_LOG for detail)

- **2026-06-12 — #24 first-launch onboarding (Gate C)** (branch `feat/onboarding-gate-c`, not yet
  merged). Gate-C slice of V1X-22: one-time 4-slide tutorial carousel (walk→spend→battle) + permission
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
- **First-launch onboarding (#24, Gate C — on `feat/onboarding-gate-c`):** 4-slide tutorial carousel +
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

## Top priorities / next actions

Phase 1 (work down the Readiness Gate so the developer can decide to promote — the real current work):
1. **Bigger gate items (now the live work):** #29 decision-support (Gate F), #26 device perf/battery (Gate G, device-measured). *(#24 onboarding / Gate C shipped — merged via PR #157 and released as v1.0.3 to internal. #29 is the likely next pickup.)*
2. **Manual play-feel gates (developer):** A audio feel, E balance — can't be closed from code.
3. **Deferred:** #128 remaining ~21 audit Lows (perf/anti-cheat/security groups → v1.1).

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
- **Onboarding gating + flag location (#24, ADR-0021)** — the first-launch flag is device-local SharedPreferences (`OnboardingPreferences`), intentionally NOT Room (must not sync; reinstall re-shows). In `MainActivity`, `startDestination` reads it **synchronously** via pure `Screen.startDestination()`; **only** the cold-permission request branch is gated behind `onboardingComplete` (service-start/HC-chaining stay ungated — don't widen the gate or step counting breaks for granted users); the deep-link collector gates on live nav state (current route == Onboarding). `Screen.Onboarding` is deliberately **out of** `allScreens`/`argumentFreeRoutes`/`items` (not a public deep-link target) — keep it out (`DeepLinkRoutingTest` pins the exact-13 set). Onboarding is **explain-only — never grant Steps** (preserves the hard invariant). Guarded by `OnboardingRoutingTest` + `OnboardingPreferencesTest` + `OnboardingContentTest` + `OnboardingViewModelTest` + `DeepLinkRoutingTest` navigate_to guards.

## References

- **Memory loop:** `CLAUDE.md` (canonical guide) · `docs/agent/START_HERE.md` (contract) · `docs/agent/CONSTRAINTS.md` · SessionStart hook + `/checkpoint` skill.
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit:** `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128 + regression specs).
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `docs/release/privacy-policy.md` → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity). Full set in `docs/agent/DECISIONS/`.
