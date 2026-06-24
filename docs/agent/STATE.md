# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** **v1.0.12 (versionCode 28) SHIPPED → Play internal** (tag `v1.0.12` release run `28051957931`
`success` end-to-end — every step green incl. `bundleRelease` [R8+sign], `jarsigner -verify`, Play-internal
upload, GitHub Release `v1.0.12` w/ `app-release.aab` 15.29 MB; release PR #342 squash `8aa7c3e`). v1.0.12
promotes the body accumulated since v1.0.11 — the audit-triage **batches A–D** (docs-drift, dead-code, i18n
locale-safety incl. a real Turkish-locale billing fix, CI/release hardening, Kover+OSV tooling) + two
focused audit fixes (**#216** notification quiet-hours/cap, **#221** dead-cosmetic removal) + **#164** Bundle
E closed. **No new mechanics, no schema change** (`app/schemas` byte-identical to v1.0.11). **Predecessor:
v1.0.11 (vc 27) SHIPPED → Play internal** (tag `v1.0.11` release run `28018033566` `success`; PR #330
`3915fd1`), which promoted the large post-v1.0.10 polish/hardening body —
player-facing: accessibility (#213/#214), i18n correctness (#225/#259/#260), Home zero-state (#224),
perf-smoothness (#242/#243), offline step/purchase reliability (#251/#249), process-death survival (#234).
Internal: detekt+ktlint gate + 6-stage format (#312/#311), Compose UI + DAO-contention tests (#253/#252),
dependency-verification (#256), clock-tamper (#211), GameEngine decomposition (#230/#231), architecture
dependency-rule restoration (#220/#227/#228/#219/#229), compileSdk-37 + Dependabot wave, CI/supply-chain
(#257/#254/#212/#255), privacy/monetization (#240/#241/#239). Collateral grounded by a verification fan-out
(CHANGELOG↔commit reconcile / pointer sweep / What's-new — all adversarially confirmed; lint entry PR# fixed
#311→#312; #310/#311/#287 given a CI/tooling note). **v1.0.12 supersedes v1.0.11 (vc 27)** · **1282 JVM + 9 instrumented tests**
green (the post-v1.0.11 body lands at 1277: audit-triage A–D → 1256, then #216 +19 → 1275, #221 +2 → 1277;
then #217 service/boot-receiver tests +5 → 1282; per-wave detail in `CHANGELOG.md` + `RUN_LOG.md`) · schema v12 · all closed-test Gate A–G in-repo items MERGED · **all 3 Gate H `severity:blocker`s MERGED:** #190 + #191
(crash visibility + the two reachable battle CMEs — PR #204, `d673386`) and #192 (privacy/Data-Safety
text — PR #205, `0019217`). **Remaining to promote internal → closed:** (a) **DONE — the manual Play
Console Data-Safety form for #192 was submitted 2026-06-24** (the four AdMob-SDK data types Collected+Shared
+ "Contains ads"=Yes + deletion URL, per `docs/release/data-safety-form.md`); the last repo-external
pre-promotion prerequisite is now cleared; (b) the `severity:major` soak-hardening items are ALL addressed
— **#195 + #193 MERGED via #270; #194 (error states, UX-1) MERGED (PR #272, `1811617`)** — #194 had been
prematurely closed 2026-06-17 with no implementing commit, verified unfixed at HEAD + re-opened 2026-06-19;
(c) v1.0.12 (the latest internal release) ships the accumulated body. **The internal→closed promotion is
now purely the developer's judgment call** (Closed-Test Readiness Gate, `plan-FORWARD.md`) — no remaining
repo-external blocker.
Latest audit
(`docs/reviews/2026-06-18-complete-app-review.md`, supersedes 2026-06-17) verdict: **7/10 — continue
building** (keep shipping internal, NOT public-ready); it filed **38 net-new Med+ issues #224–#261 + Low
tracker #262** — none are internal-track blockers; its 4 net-new HIGHs (#233/#236/#250/#261) were the
highest-leverage before-public work and are **now ALL fixed** (#236 PR #270; #250 PR #272; #261 + #233
PR #274, `8b50b13`). The larger **#233 clean Simulation-hoist** (ADR-0012) +
the med/low backlog (#262) remain.

## Current objective

- **CURRENT (nothing in flight — `main` clean).** Next candidate work selected: **#34 i18n string
  externalization** (move hardcoded UI strings into `strings.xml`; medium, low-risk, no fragile-zone
  touch). See "Top priorities" below for the rest of the audit-backlog non-batchable items + the
  internal→closed promotion judgment call.
- **Previous objective (DONE — #217 service/boot-receiver test coverage MERGED to `main`; PR #348,
  commit `0d09ef2`; test-only, no app change).** Closed the 2026-06-17 audit's TEST-2 gap
  (`StepCounterService`/`BootReceiver` had zero test references). The skeptic scope-correction held: the
  catch-up logic + crash-safety/notification seams were already covered, so the only uncovered surface
  was thin Android glue. Added **`BootReceiverTest`** (3 Robolectric/JUnit-4 tests — action gate,
  ACTIVITY_RECOGNITION permission gate, `StepCounterService` dispatch; `BootReceiver` is not
  `@AndroidEntryPoint` so no Hilt) + **`StepCounterServiceTest`** (2 Robolectric tests — the
  injection-independent lifecycle: `onStartCommand`→`START_STICKY`, `onBind`→`null`, on a
  directly-constructed instance; the Hilt-injected `onCreate` stays on its pure seams + the instrumented
  suite). Authored via the `android-test-writer` subagent; full suite green at **1277 → 1282 JVM (+5)**,
  0 failures. **No production-code/schema change.**
- **Previous objective (DONE — Claude Code tooling + CI fast-path MERGED to `main`; nothing in flight, no app change).**
  Acted on the `claude-automation-recommender` review of this repo's Claude Code setup. **PR #345**
  (`ca239c2`) added two subagents (`concurrency-reviewer`, `android-test-writer`), two skills
  (`adversarial-review`, `new-migration`), a Tier-3 `Migrations.kt` advisory in `guard-sensitive-edits.sh`,
  and a shared `.mcp.json` (context7; key via `CONTEXT7_API_KEY`, never committed). **PR #346** (`4b28b38`)
  widened the CI `changes` classifier (both `ci.yml` + `instrumented.yml`) so `.claude/**` / `.mcp.json`-only
  diffs skip the build gate **and** the emulator suite (anchored patterns; everything app-affecting still
  gates; verified with a path-classification harness). Also flipped the **repo merge policy** to
  merge-commits only (squash+rebase disabled) + enabled auto-merge. Each authored artifact was code-grounded
  and adversarially verified via an 8-agent research→draft→refute Workflow before writing (140 claims
  checked; 1 refuted, 3 partial, all corrected). **No app/Kotlin/schema/test change — 1277 JVM unchanged.**
  **Next (no work in flight):** unchanged from below — the audit backlog's non-batchable items + the
  internal→closed promotion judgment call.
- **Previous objective (DONE — v1.0.12 / versionCode 28 SHIPPED → Play internal; nothing in flight).** The
  collateral-only release PR #342 (`8aa7c3e`) merged, the annotated `v1.0.12` tag was pushed, and
  `release.yml` (run `28051957931`) fired green end-to-end → signed AAB uploaded to the Play **internal**
  track + GitHub Release `v1.0.12` (`app-release.aab` 15.29 MB). Promoted the post-v1.0.11 body already on
  `main` (audit-triage A–D + #216 + #221 + #164 closed; **no new mechanics, no schema change**; 1277 JVM).
  The developer-approved "What's new" (quiet hours + supply-drop cap; locale purchase fix; cleanup) is now
  the Play release note. **Next (no work in flight):** the audit backlog's **non-batchable items** — #217
  service tests (test-only), A24 anti-cheat rate-limit clock-tamper (large/fragile), battle game-loop perf
  L46-L51 (fragile), L12 BattleViewModel decomposition (large, #306/ADR-0012), billing-anti-fraud
  L35/A25/A26/L41 (by-design, no fix); the #34 i18n-externalization push; remaining audit med/low.
  **Promotion status:** the **Play Console Data-Safety form (#192) was submitted 2026-06-24** — the last
  repo-external pre-promotion prerequisite is cleared. The internal→closed promotion is now **purely the
  developer's judgment call** (Closed-Test Readiness Gate, `plan-FORWARD.md`); once promoted, Google's
  ≥12-tester / ≥14-day closed soak begins (Phase 2).
- **Previous objective (DONE — audit single-issue fixes #216 + #221 MERGED to `main`).** Two
  focused audit issues shipped this session, each through the full spec→adversarial-review→plan→
  adversarial-review→TDD→PR→merge loop. **#221 FEAT-1** (PR #340, `705865f`) — removed the 4 seeded
  projectile/enemy-skin cosmetics + the 2 unused `CosmeticCategory` values (`PROJECTILE_EFFECT`/`ENEMY_SKIN`)
  that had no render path, closing the audit "live trap"; only `ZIGGURAT_SKIN` remains. Existing-device
  safety (belt-and-suspenders): `CosmeticDao.deleteByIds` purge in `ensureSeedData` + resilient
  `CosmeticRepositoryImpl.toDomainOrNull` filtering rows whose stored String category no longer parses (so
  `CosmeticCategory.valueOf` can't crash; also covers the `StoreViewModel.init` purge-vs-`observeAll` race);
  **no schema migration** (data-only delete); +2 JVM. **#216 NOTIF-1** (PR #339, `2f1c090`) — quiet-hours
  (22:00–08:00 local) + supply-drop daily-cap (3/day) on the reminder & supply-drop notification paths via a
  new pure-domain `domain/notification/NotificationPolicy`; `SupplyDropNotificationManager` injects
  `TimeProvider` + field-caches its prefs (runs under the #120 credit mutex); `DataDeletionManager` also
  wipes the new `supply_drop_notifications` prefs (#247); +19 JVM. Also closed **#164 Bundle E** (verify-and-
  close — shipped v1.0.8, never closed). **No schema/economy change across the arc; 1256 → 1277 JVM.**
  `[Unreleased]`. **Next (no work in flight):** the audit backlog's **non-batchable items** — #217 service
  tests (test-only), A24 anti-cheat rate-limit clock-tamper (large/fragile), battle game-loop perf L46-L51
  (fragile), L12 BattleViewModel decomposition (large, #306/ADR-0012), billing-anti-fraud L35/A25/A26/L41
  (by-design, no fix); the #34 i18n-externalization push; remaining audit med/low. Also still open: the
  v1.0.11 **internal→closed** promotion judgment call (gated on the **manual Play Console Data-Safety action
  #192** — `docs/release/data-safety-form.md`, a human step).
- **Previous objective (DONE — audit-triage batches A–D all SHIPPED to `main`).** A verification
  `Workflow` code-grounded ~125 unverified #262/#128 tracker findings vs HEAD `617babd` → **83 LIVE / 23
  FIXED / 6 STALE / 4 DUP / 1 POSITIVE**; LIVE survivors clustered into batches A–G. **A–D merged** (each
  spec→plan→adversarial-review→implement→PR→merge): **A** docs/content-drift (#333), **B** dead-code removal
  (#334), **C** i18n locale-safety incl. a REAL Turkish-locale billing bug L88 (#335), **D1** release/CI
  config hardening + ktlint-job split (#336), **D2** Kover coverage + OSV supply-chain scan (#337). 1256 JVM
  at arc end; **no schema change**. #218 closed; #262 annotated per batch. (Per-PR detail in RUN_LOG/CHANGELOG.)
- *Prior objectives (all DONE, `[Unreleased]` unless noted) — the **v1.0.11 release** (vc 27 → Play internal,
  tag `v1.0.11`, PR #330) and everything before it — are recorded per-PR in `docs/agent/RUN_LOG.md` +
  `CHANGELOG.md` and summarized under "Recently shipped" below; not duplicated here.*

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
- **Monetization:** real Play Billing v9 + AdMob v25 + UMP v4, device-verified; live Store prices.
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
- **Skills (`.claude/skills/`):** `checkpoint` (end-of-session memory write) · `complete-app-review` (ultracode `Workflow` for the full 20-section audit — every finding refuted by separate subagents 3/3/2/1; writes `docs/reviews/<date>-complete-app-review.md` + emits a propose-then-confirm GitHub-issue plan) · `release` (cut a `v*` release) · `adversarial-review` (the Adversarial Review Gate as a one-command Workflow for a single spec/plan) · `new-migration` (User-only guided Room schema-change choreography).
- **Subagents (`.claude/agents/`):** `concurrency-reviewer` (read-only thread-safety & atomic-economy reviewer — encodes the lock model + guarded-deduct economy) · `android-test-writer` (JVM-lane-default test writer). Plus the shared `.mcp.json` (context7 MCP for live API docs; key via `CONTEXT7_API_KEY`).
- **Look-&-feel bundle docs (all shipped):** Bundle E (#164, v1.0.8) spec `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` + plan `docs/superpowers/plans/2026-06-15-look-and-feel-bundle-e.md` (both review-passed) · #171 spec/plan `docs/superpowers/{specs,plans}/2026-06-15-battle-bottom-chrome-overlap*.md` · Bundle D (#163, v1.0.7) spec `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-d-design.md` + plan `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-d.md` · Bundle C (#162) shipped in v1.0.6.
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit (run via the `complete-app-review` skill; dated reports are point-in-time artifacts):**
  `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128) · `docs/reviews/2026-06-17-complete-app-review.md` (raised Gate H blockers #190–#192 + soak-hardening #193–#195) · **latest `docs/reviews/2026-06-18-complete-app-review.md` (7/10; 7 high · 43 med · 95 low; filed Med+ #224–#261 + Low tracker #262; 4 net-new HIGHs #233/#236/#250/#261).**
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `site/index.md` (canonical; published to GitHub Pages by `.github/workflows/pages.yml` — `site/` ONLY, not `docs/`) → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity) · 0021 (onboarding explain-only) · 0022 (design tokens + de-emoji) · 0023 (bottom-nav back-stack) · 0024 (Bundle E: custom font + onboarding biome theming + persist-first completion beat) · **0025 (#26 perf/battery Gate-G: multi-module benchmark tooling on AGP-9 [1.5.0-alpha, dev-only] + #124 guard narrowing + A28/A31/A29 GC-churn fixes)**. Full set in `docs/agent/DECISIONS/`.
