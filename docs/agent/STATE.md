# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** v1.0.2 (versionCode 18) · **908 JVM + 9 instrumented tests** green · `main` clean ·
schema v11 · the launch is externally gated (closed-track soak).

## Current objective

- **Launch is the critical path and it is externally gated.** The ≥14-day closed-track soak window
  has **elapsed** (resumed 2026-05-26 from v14; 14 days passed 2026-06-09). The only remaining launch
  gate is **≥12 opted-in testers** — verify in Play Console → Closed testing → Testers, then apply for
  production access. All in-repo release plumbing is done (CI release lane live; v1.0.1 fired green).
- **In-repo work during the soak = remaining audit Lows.** #121/#125/#126 are fixed + merged. Still
  open: #124 (billing signature verify — local-only, bypassable mitigation), #127 (duplicate daily
  missions — needs a schema v11→v12 bump, defer to a schema-touching wave), #128 (30-Low tracker).

## Recently shipped (newest first — see RUN_LOG for detail)

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
- **Battle engine:** simulation extracted to pure-domain `domain/battle/` (V1X-09 Phases 1–3 complete,
  ADR-0012) — `GameEngine` is a thin render shell delegating to `Simulation`.
- **Persistence:** Room schema v11 (13 entities, SQLCipher-encrypted), migrations v7→11, decrypt-fail wipe recovery.
- **Monetization:** real Play Billing v8 + AdMob v25 + UMP v4, device-verified; live Store prices.
- **Release infra:** Play Console listing live, internal track active; CI pipeline (Plan 32) — PR gate +
  instrumented emulator lane (both required on `main`) + `v*`-tag release lane to Play internal (ADR-0018);
  v1.0.1 fired green 2026-06-04. Dependabot dashboard clean.
- **Guardrails:** `DomainPurityTest` (machine-enforced domain purity, #27) + the concurrency/economy regression
  guards listed under fragile zones below.

## Known issues / debt

- **Launch gate (Google policy):** ≥12 testers opted-in + ≥14 days closed testing before production access.
  Time gate met; tester count is the remaining unknown.
- **Open audit Lows:** #124 (no purchase signature verification — local-only, mitigation bypassable on a
  repackaged APK), #127 (duplicate daily missions — needs a unique index + schema bump, deferred), #128 (30-Low tracker).
- **RO-09 deferred (v1.x backlog):** #3 STEP_MULTIPLIER × cross-validator unit mismatch (needs schema migration);
  #4 currency lifetime-counter desync (display-only); #5 TOCTOU on gem/PS spend (lifetime drift, wallet correct);
  #6 per-kill credit on `viewModelScope` (≤1 step lost on mid-round nav-away).
- **Content/polish debt:** sound assets are placeholder sine tones; cosmetics — 5 ziggurat palettes plumbed
  (zig_jade + zig_obsidian store-purchasable; lapis/garden/sandals milestone-only), rest "Coming Soon" pending art.
- **Phase B debt:** B.4 FollowOnPipeline + B.5 UpdateMissionProgress extraction (ADR-0004, ~1 week, zero user benefit — deferred).
- `BuildConfig.USE_REAL_ADS` consent-prefetch branch is JVM-untested (device-verified). Play "no debug symbols"
  warning persists on every upload (pre-stripped .so files — informational).

## Top priorities / next actions

1. **(External)** Recruit ≥12 testers; distribute the Play Console closed-track opt-in URL.
2. **(External)** Apply for production access once ≥12 testers is met (time gate already elapsed). Google review 1–3 days.
3. **(External)** Promote closed → production with staged rollout; tag `v1.0.0` after 100 %.
4. **(In-repo, during soak)** Remaining audit Lows: #124, then #127 (bundle into a schema-touching wave), #128.
5. **(Post-launch)** Plan V1X waves — see `docs/plans/plan-V1X-roadmap.md`: v1.0.1 polish (V1X-01/02/03),
   v1.0.2 audio (V1X-04/05/06), v1.1 testing-infra + simulation + atomic-spend (V1X-07–11), v1.2 cloud save + i18n (V1X-12/13).

## Do-not-touch / fragile zones

- `domain/model/` — stable; balance constants validated by regression tests. `BillingProduct.skuId()` is a stable public API.
- `domain/usecase/` — 32 use cases stable.
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
- **Game-loop frame clamp (#126)** — `SimulationMath.clampAccumulator` (`MAX_CATCHUP_TICKS = 8`); don't lower below ~8 (a 30fps@4× render legitimately needs ~7.9 ticks/frame). Guarded by `SimulationMathTest`.
- **`daily_step_record` writers must stay column-targeted (#121)** — disjoint-column `ON CONFLICT(date) DO UPDATE SET` upserts, NOT a whole-row read-copy-`@Upsert`. Guarded by `DailyStepDaoTest` + `StepRepositoryImplTest`.

## References

- **Memory loop:** `CLAUDE.md` (canonical guide) · `docs/agent/START_HERE.md` (contract) · `docs/agent/CONSTRAINTS.md` · SessionStart hook + `/checkpoint` skill.
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit:** `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128 + regression specs).
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `docs/release/privacy-policy.md` → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity). Full set in `docs/agent/DECISIONS/`.
