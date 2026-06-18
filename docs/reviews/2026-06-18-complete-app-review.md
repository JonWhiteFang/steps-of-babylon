# Complete App Review — Steps of Babylon

**Review date:** 2026-06-18
**Reviewed version:** v1.0.8 (versionCode 24), HEAD on `main`
**Reviewer:** Synthesis lead, audit-grade multi-agent review (severity-scaled adversarial refutation)
**Scope:** Full end-to-end production-readiness audit — discovery only, no code changes
**Prior audit:** `docs/reviews/2026-06-17-complete-app-review.md` (same 20-section structure)

---

## How to read this report

Every live finding below survived a severity-scaled adversarial refutation pass: 3 independent
refuters for critical/high findings, 2 for medium, 1 for low. Each refuter re-checked the finding
against the actual code and tried to *kill* it (default-to-refuted discipline); only `confirmed` or
`partial` verdicts survive as live findings. The **finalSeverity** shown is the post-refutation
severity (often lower than the finder's original claim — refuters downgraded aggressively when a
consequence was overstated or already mitigated).

For each finding we cite:
- a **stable finding ID** (e.g. REL-2, CONC-1) used in the Technical Debt Register (Section 19) so
  issues can backlink;
- the **refutation trail** — refuter count and confirmed/partial/refuted vote tally — as the trust
  signal;
- the **code evidence** (file:line) and the surviving consequence after refutation.

Section 2 lists what was **refuted or downgraded** — what got killed and why is part of the value.

---

## 1. Executive Summary

Steps of Babylon is a mature, well-architected, offline-first Android game already shipping to the
Play **internal** track (v1.0.8 / versionCode 24). The codebase is disciplined: a documented Clean
Architecture, a machine-enforced domain-purity guard, an atomic guarded-deduct economy pattern, a
1069-test JVM suite, a real CI/CD pipeline, and an extensive committed memory spine. The prior audit's
"continue building" verdict still holds. This is **not** a project in trouble.

The audit surfaced **~95 findings**; after adversarial refutation, **most high-severity claims were
downgraded** — a healthy signal that the finders were aggressive and the refuters honest. The findings
that survived at material severity cluster into a small number of real themes:

1. **Background step-counting reliability is under-built relative to the GDD's own risk register.**
   The single most important promise of the app — reliable step counting while backgrounded/killed —
   has two confirmed gaps: the GDD-promised battery-optimization whitelist prompt is **entirely
   absent** (FEAT-1), and a device with no hardware step counter fails silently with only a `Log.w`
   (REL-3 / open issue #193). The offline gap-fill path also clamps recovered steps to ~200/min
   (NET-3), losing most steps recovered after the service was killed.

2. **Monetization error-handling and reconciliation are reactive.** Offline IAP failures are silently
   swallowed (NET-1), and pending/offline-completed purchases reconcile **only** when the user
   re-opens the Store (NET-2) — risking a Play auto-refund of a kept entitlement. The premium-currency
   spend→grant pair is split across two non-atomic DB writes (DATA-1), the one economy flow not given
   the project's own atomic-transaction treatment.

3. **State-management gaps on the core gameplay screen.** An in-flight battle round is destroyed on any
   configuration change (rotation/dark-mode/font-scale) with no orientation lock and no saved state
   (STATE-1). The Missions screen never re-subscribes its query at midnight, showing yesterday's
   missions (STATE-2 / open issue #195).

4. **Supply-chain hardening gaps inconsistent with the project's own SHA-pinning discipline.** The
   committed `gradle-wrapper.jar` is never validated in CI (CICD-1), there is no Gradle dependency
   verification metadata (DEP-1), and no wrapper distribution checksum (CICD-7).

5. **Architecture-vs-documentation drift.** The Clean Architecture dependency rule is violated: 9
   domain use cases import the data layer, 6 ViewModels inject raw Room DAOs, and the `DomainPurityTest`
   that the docs cite as the guarantee only checks Android-framework prefixes — so the violation sails
   through green (ARCH-1/2/3). This is design debt, not a runtime defect.

6. **i18n is a structural blocker for any non-English market** — ~108 hardcoded Compose strings, no
   plurals, no string externalization on the first-run flow (I18N-1). Correctly an accepted v1.0
   deferral (ADR-0014), but a hard gate for localization.

**Nothing surviving is a crash-on-launch-for-everyone, a data-corruption exploit, or a security hole
that leaks PII off-device.** The shipped app works. The surviving high/medium findings are
reliability, monetization-robustness, and release-hygiene gaps that matter **before broadening past
the internal track** — especially before a public launch on aggressive-OEM devices.

---

## 2. Refuted / Downgraded Findings (what was killed and why)

The refutation pass was the most valuable filter in this audit. The following were **fully refuted**
(dropped from the live register):

- **"Battery-killed steps inflate lifetime totalStepsEarned" (refuted 1/1).** The finder claimed
  `creditBattleStepsAtomic` folds battle-kill steps into the lifetime "walked" stat. Refuted as a
  duplicate/mischaracterization — battle steps are segregated into their own capped column; the
  consequence did not survive grounding.
- **"Pre-v7 install crash-loops on upgrade" framed as a SECURITY/HIGH defect (refuted 3/3 in the
  security framing; survives only as a documentation/testing-coverage note).** Three refuters traced
  the git history: the *first* build ever distributed to any Play track already shipped at schema v9
  (later v11/v12). Schema versions 1–6 existed only on the solo dev's machine during pre-release
  development and used destructive fallback at the time. **No real install can be below v9**, so the
  "deterministic crash-loop for real users" consequence is unreachable. The valid residual — no
  *general* upgrade-path destructive fallback as defense-in-depth, and no contiguity guard — survives
  as DATA-3 / TEST-2 / CICD-2 at low/medium, not as a security high.
- **"guava constraint comment attributes the transitive to kotlinx-coroutines-guava" (refuted 1/1).**
  Misattribution noted but treated as subsumed by the broader stale-comment finding (DEP-5).

The following were **materially downgraded** by refutation (kept as live findings at the lower
severity, but their alarming framing was rejected):

- **Onboarding "enable in Settings" dead-end** — finder said high (progression-blocking dead end);
  refuted to **low** (3/3 partial). The MainActivity cold-launch snackbar auto-re-fires the permission
  request every launch, and "Replay tutorial" routes to the onboarding denial slide with a working
  "Open Settings" deep-link. Real defect: the in-app Settings tab has no permission control and the
  recovery path is undiscoverable — a copy/discoverability gap, not a dead end.
- **Domain→data dependency violations** (ARCH-1/2/3) — finders said high; refuted to **medium** (3/3
  confirmed each). Real and pervasive, but maintainability/design debt with zero runtime, security, or
  economy impact — the app works, and "domain has zero *Android* imports" remains literally true.
- **GameEngine god-class / BattleViewModel god-object** — kept at medium (GameEngine) and downgraded to
  **low** (BattleViewModel). The BattleViewModel finder's "untestable / defeats Hilt DI" consequences
  were refuted by a 40-test, 1033-line BattleViewModelTest and project-wide use-case-construction
  conventions; its size/dep counts were also slightly wrong (15 ctor params, 9 inline use cases, not 14/11).
- **player_profile dual-write path** — finder said medium (correctness-risk multiplier); refuted to
  **low** (1 refuted, 1 partial). All raw-DAO callers only *credit* (capped, transactional); no
  reachable *spend* path bypasses the guarded-deduct discipline, so the over-spend hazard does not exist.
- **Music-decode on main thread / StepCounterService startForeground crash / StepSyncWorker no retry /
  HC readers conflate errors / DailyStepManager lock-hold / DatabaseKeyManager wipe** — all confirmed
  but with downgraded or split severity (see Sections 11–12). E.g. `startForeground` (REL-1) is high in
  one verdict, medium in two: the dominant `ForegroundServiceStartNotAllowedException` is actually
  *exempted* on the BOOT_COMPLETED path, narrowing the trigger to a permission-revoke race.
- **CI tag-vs-version guard / NDK debug symbols / log stripping / AdMob COPPA** — kept but several
  downgraded (release lane self-fails on reused versionCode via Play rejection; NDK FULL bundles zero
  symbols because the only native `.so` are vendor-stripped prebuilts).

**Why this matters:** the audit's aggressive finders produced ~10 "high" claims; refutation confirmed
only a handful at high. The surviving highs are concentrated, specific, and code-grounded.

---

## 3. Architecture & Design

**Verdict: strong intent, with a real and documented-as-clean-but-actually-leaky dependency boundary.**

The project follows MVVM + Clean Architecture (`presentation → domain ← data`) with a pure-domain
battle simulation extracted per ADR-0012. The structure is genuinely good: 37 domain models, 39 use
cases, 10 repository interfaces, an extracted `Simulation` core, and a documented acyclic lock order
for the game-loop threading. The composition design (presentation `EnemyEntity` wraps domain
`EnemyState`) is sound.

The surviving architecture findings are all **dependency-direction / design-debt** issues, confirmed
but downgraded to **medium** (none is a runtime defect):

- **ARCH-1 (medium, 3/3 confirmed):** 9 domain use cases import the data layer
  (`AwardBattleSteps.kt:3-5` imports `data.local.DailyStepDao`, `data.local.PlayerProfileDao`,
  `data.time.SystemTimeProvider`; same in `AwardBossPowerStones`, `CheckMilestones`, `ClaimMilestone`,
  `ClaimMission`, `GenerateDailyMissions`, `TrackDailyLogin`, `TrackWeeklyChallenge`,
  `UpdateCompleteResearchMissionProgress`). `GenerateDailyMissions.kt:20` constructs a Room
  `@Entity` inside the domain. ~25% of use cases violate the documented core invariant. The DAOs are
  interfaces and fakes exist, so they *are* unit-testable — the "cannot be tested without the data
  layer" sub-claim was refuted; the boundary violation itself stands.
- **ARCH-2 (medium, 3/3 confirmed):** `DomainPurityTest` gives **false confidence**. Its
  `forbiddenPrefixes` (`DomainPurityTest.kt:23`) only flags `android.`/`androidx.`/`com.android.`/
  `com.google.android.` — it does **not** forbid `com.whitefang.stepsofbabylon.data.*`. The 9
  data-importing domain files pass green. CLAUDE.md/START_HERE/CONSTRAINTS all cite this test as the
  machine-enforced purity guarantee. Trivial fix: add the `data.` prefix to the list (it will fail
  until ARCH-1 is fixed — that's the point).
- **ARCH-3 (medium, 3/3 confirmed):** 6 ViewModels inject raw Room DAOs; `BattleViewModel` injects
  the concrete `AppDatabase` and calls `appDatabase.withTransaction{}` directly
  (`BattleViewModel.kt:73-76,97`). Transaction orchestration leaks into presentation; `player_profile`
  is written from both a repository and a raw-DAO path within one VM. Data integrity is intact (the
  writes go through atomic guarded methods inside a real transaction) — this is coupling/testability
  debt.
- **ARCH-4 (medium, 2/2 partial):** Inconsistent persistence abstraction — 13 DAOs but only 8 data
  repositories; missions/milestones/daily-login/weekly-challenge have no repository port. The
  "prevents fakes" consequence was refuted (DAO-level fakes exist); the structural inconsistency and
  un-guarded domain→data crossing survive.
- **ARCH-7 (medium, 2/2 confirmed):** `GameEngine.kt` is a 1223-line presentation-layer hotspot; the
  ADR-0012 extraction is partial (`Simulation` is 254 lines). Reward formulas, damage application, UW
  effects, and entity orchestration still live in the Canvas-coupled class.

**Recommendation:** Introduce the missing repository ports
(`MissionRepository`/`MilestoneRepository`/`DailyLoginRepository` + a crediting method on
`PlayerRepository`), move the raw DAO/`AppDatabase` injections behind interfaces, and **harden
`DomainPurityTest`** so the boundary is actually enforced. None of this blocks shipping; it pays down
the largest gap between the documented architecture and the code.

---

## 4. Code Quality & Maintainability

**Verdict: high overall quality with localized hotspots and a handful of dead-code/naming artifacts.**

The dominant code-quality concern is concentration of complexity in two files: `GameEngine.kt` (1223
lines, the largest source file; **CQ-1**, medium, 2/2 confirmed — mixes simulation orchestration,
rendering, UW lifecycle, cash economy, targeting, and Paint allocation) and `BattleViewModel.kt` (720
lines, 15 ctor deps, 9 inline use cases; **ARCH-6**, downgraded to low — real god-object smell, but
the untestability consequence was refuted by a healthy 40-test suite).

Confirmed dead/misleading code (all **low**):
- **CQ-2 (low, 1/1):** `GameEngine.resetUWCooldowns()` is dead — zero callers, and it mutates
  `uwStates` without taking `entitiesLock`, a revive-time footgun for the #191 CONC-2 race.
- **CQ-3 (low, 1/1):** `GameEngine.cooldownText` is write-only dead state (assigned at lines 809/817,
  never read; the banner is owned by `EffectEngine`).
- **CQ-7 (low, 1/1):** `GameLoopThread.fps` is computed but never consumed (misleads readers into
  thinking an FPS HUD exists). The finder overstated the perf cost — the assignment is once/second.
- **CQ-4 / ARCH-5 (low):** Dead Card Dust API persists across `PlayerRepository`/`PlayerProfileDao`/
  `PlayerProfile` after R4-08/ADR-0010 removed the mechanic. The DB column is correctly retained for
  migration safety; the repo/DAO mutators (`updateCardDust`/`adjustCardDust`) have zero callers.
- **CQ-8 (low, 1/1):** `GameEngine.fortuneMultiplier` is a misleading name — it now holds only the
  GOLDEN_ZIGGURAT cash buff after the FORTUNE/Step-Overdrive source was deleted (R4-01). Rename to
  `goldenCashMultiplier`.

Duplication (all **low**, confirmed): `AdResult` Cancelled/Error handling duplicated across
BattleViewModel + CardsViewModel (CQ-5); three near-identical `StoreViewModel.purchase*` methods
(CQ-6); insufficient-funds strings duplicated across 5 ViewModels (CQ-9); inconsistent `TimeProvider`
usage — CardsViewModel/StoreViewModel call `LocalDate.now()`/`System.currentTimeMillis()` directly
(TEST-7, low, the testability consequence refuted because both flows are already deterministically
tested).

**ARCH-8 (low, 1/1):** empty `HealthConnectModule` placeholder + 7 loose preference/helper files at
the `data/` package root break the otherwise-tidy subpackaging convention.

---

## 5. Testing & Coverage

**Verdict: strong JVM unit coverage (1069 tests), with three categorical gaps that matter for the
exact reliability/migration risks this audit found.**

The headline 1069 JVM + 9 instrumented tests is confirmed. ViewModels and domain logic are
well-covered with fakes. The gaps are pointed and consequential:

- **TEST-1 (high, 3/3 confirmed):** Open bug **#195** (Missions day-rollover stale query) has **zero
  regression coverage**. The only VM-constructing tests assert celebration counts; none advances the
  clock past midnight. `MissionsViewModel.kt:83-84` binds `getByDate(today)` once at construction. No
  test would catch a regression or validate a fix. Severity tracks the open `severity:major` bug it
  fails to guard (one refuter held high, two adjusted to medium).
- **TEST-2 (medium, 3/3 partial):** No test guards the Room migration chain for contiguity/completeness;
  `AppMigrations.ALL` covers only v7→v12 with no general upgrade fallback. The "pre-v7 crash for real
  users" framing was refuted (no shipped build below v9), but the forward-looking risk is real: a
  future version bump that forgets to register a migration would compile, pass all 1069 tests, and
  crash-loop on upgrade. `RoomSchemaTest` only builds a fresh v12 DB; `Migration11To12Test` drives one
  step in isolation; the CI guard only diffs schema JSON.
- **TEST-3 (medium, 2/2 confirmed):** The atomic guarded-deduct/one-shot-claim DAOs are tested **only
  serially**. `WorkshopDao.purchaseUpgradeAtomic` (the canonical template) has no real-DB test at all —
  only fakes. The economy invariant the whole pattern exists to protect (two concurrent spends can't
  both succeed) is never exercised under real concurrent contention at the SQL layer.
- **TEST-4 (medium, 2/2 confirmed):** **Zero Compose UI tests** — no `ui-test-junit4` dependency, no
  `createComposeRule`. ~94 presentation files (13+ screens, onboarding, battle overlays) have no
  rendered-behavior, interaction, or accessibility-semantics coverage. The instrumented suite is 3
  functional tests + the Hilt runner.
- **TEST-5 (low, 2/2 partial):** Service/worker/BootReceiver/HC-reader paths are thin glue with no
  tests; the riskiest arithmetic (`computeCatchUp`, `resolveDisplayBalance`, `StepCrossValidator`) is
  already covered, so impact is bounded; `StepGapFiller.fillGaps` is a genuine untested gap.
- **TEST-6 (low, 1/1):** `DatabaseKeyManager` keystore-recovery is half-tested — the file-wipe
  mechanics are covered but the decrypt-failure→wipe *decision* branch is not (Robolectric can't shadow
  AndroidKeyStore).
- **TEST-8 (low, 1/1):** Flow tests use manual `toList`+`runCurrent` + a fragile `cancelForTest()`-or-
  hang ritual instead of Turbine; **TEST-9 (low, 1/1):** the sole `Thread.sleep(300)` in
  `DailyStepManagerConcurrencyTest` is a latent false-green under loaded CI; **TEST-10 (low, 1/1):**
  `EscrowLifecycleTest` stubs the Room transaction seam, so multi-write atomicity is asserted only
  against a pass-through (real-transaction rollback is never exercised — though the production
  `withTransaction` wrap structurally guarantees atomicity, so the alarming "wallet goes inconsistent"
  claim was refuted).

**Recommendation:** Add (1) a `MissionsViewModelTest` day-rollover case (TEST-1, guards an open bug),
(2) a `MigrationTestHelper` chain test + contiguity assertion (TEST-2), (3) a real-DB concurrent
guarded-deduct test (TEST-3), and (4) the `ui-test-junit4` dependency + a small set of high-value
Compose tests starting with onboarding and a claim flow (TEST-4).

---

## 6. Security

**Verdict: solid on-device security posture; the surviving findings are supply-chain hardening and
information-disclosure hygiene, not exploitable PII/data leaks.**

The core posture is good: SQLCipher DB encryption with an Android-Keystore-wrapped passphrase, cleartext
blocked globally via network security config, R8 obfuscation, fail-closed release license-key guard,
allowlist-validated deep links with `FLAG_IMMUTABLE` PendingIntents (verified as a **positive control**,
SEC-POS), no tracked secrets, and a fully offline single-player model (no server attack surface).

Surviving findings:
- **CICD-1 (high, 3/3 — two refuters raised low→high):** CI/release never validates the committed
  `gradle-wrapper.jar`. It is git-tracked and executes with full build privileges on every checkout,
  **including the release lane that decodes the signing keystore and ships the production AAB**
  (`release.yml:30,88`). No `gradle/actions/wrapper-validation` anywhere — conspicuous on a project that
  SHA-pins every other action. A tampered jar runs arbitrary code before any guard fires.
- **DEP-1 (medium, 3/3 — finder said high, refuted to medium):** No Gradle dependency verification
  (`verification-metadata.xml` absent). A signed, monetized AAB is built with zero supply-chain
  integrity checks on the resolution graph; amplified by deliberately tracking alpha/beta/just-published
  artifacts. Downgraded because resolution is HTTPS from Google + Maven Central, actions are SHA-pinned,
  Dependabot alerts on the release graph, and absence of verification metadata is the industry norm — so
  this is defense-in-depth, not an active exploit.
- **CICD-7 (medium, 2/2 — one refuter low, one medium):** Gradle wrapper has no
  `distributionSha256Sum`; `validateDistributionUrl=true` only checks URL shape, not ZIP integrity.
  Inconsistent with the repo's SHA-pinning discipline; mitigated by HTTPS to the official host.
- **PRIV-3 / SEC-2 (low, 1/1):** Purchase tokens are logged in plaintext at WARN/INFO
  (`BillingManagerImpl.kt:208,231,277,290,332`); release builds do **not** strip `android.util.Log`
  (no `-assumenosideeffects` rule). Low because minSdk-34 logcat needs signature/privileged `READ_LOGS`
  — realistic exposure is adb/bug-report dumps, and a token is a transaction id, not a credential. (Note:
  orderId is **not** logged — only `purchaseToken`.)
- **SEC-3 (low, 1/1):** No release-time log stripping in general — anti-cheat thresholds (rate-limit
  hits, CV-offense counts) and billing flow log to logcat in release. Same minSdk-34 mitigation.
- **SEC-5 (low, 1/1):** `StepWidgetProvider` is exported with no permission guard on its
  `APPWIDGET_UPDATE` filter — structurally required for app widgets; handler is side-effect-free (reads
  non-sensitive prefs only). Recorded for completeness; keep `onUpdate` side-effect-free w.r.t. wallet.
- **CICD-5 (low, 1/1 partial):** Release lane materializes keystore + license key as plaintext files on
  the ephemeral runner with no explicit cleanup. Both stated exploit vectors (workspace archival, `set
  -x` log echo) were refuted — no `upload-artifact`/`set -x` exists, and `.gitignore` covers the files.
  Defense-in-depth note only.
- **SEC-1 (low, 1/1 partial):** Purchase verification uses `SHA1withRSA` — correct and **Google-
  mandated** (the finder confirms this), so not fixable. The real residual is the documented,
  accepted no-server-side-verification tradeoff. No code change; document in ADR-0005.
- **SEC-4 (low, 1/1 partial):** `obfuscatedAccountId` is a SHA-256 of a wiped-on-Clear-Data UUID —
  the privacy-correct choice; the durability/anti-fraud tradeoff is already documented. Informational.

**Recommendation:** Add `gradle/actions/wrapper-validation` to CI (CICD-1) — cheap, closes the lone
unvalidated executable in an otherwise pinned pipeline. Then add `verification-metadata.xml` (DEP-1)
and `distributionSha256Sum` (CICD-7). Redact/truncate purchase tokens in logs and add an
`-assumenosideeffects` Log-strip rule for release (PRIV-3/SEC-2/SEC-3).

---

## 7. Performance

**Verdict: pooled, bounded, and largely sound; one user-visible audio hitch and several GC-churn smells
on the battle hot path.**

- **PERF-1 (medium, 3/3 — finder said high, refuted to medium 2/3):** Background music decodes a 1.3 MB
  OGG **synchronously on the main thread** on every Battle↔menu transition (`MusicManager.createPlayer`
  → `MediaPlayer.create`, no caching, fired from `LaunchedEffect(currentRoute)`). Real main-thread I/O
  on a frequent navigation; the "ANR-risk / decodes 1.3 MB" framing was overstated (local-resource
  `prepare()` is tens of ms, not a full decode, and the early-return guards prevent re-decode on
  menu↔menu nav). Fix: `prepareAsync` or cache the two prepared players.
- **PERF-2 (medium, 2/2 confirmed):** `ProjectileTrailEffect` calls `pool.acquire()` for every alive
  projectile every tick, thrashing the 200-slot `ParticlePool` under multishot+bounce×4-catch-up and
  stealing slots from death/UW effects. Bounded (pooled, clamped catch-up), no correctness impact.
- **PERF-3 (low, 2/2 confirmed — finder said medium):** `getAliveEnemies()` allocates a fresh
  `ArrayList` several times per tick. Downgraded because the live-re-derive is a deliberate #125 corpse-
  double-credit safeguard and the dominant per-frame allocations were already eliminated by A28; residual
  GC churn only.
- **PERF-4 (low, 1/1):** Per-projectile bounce search + per-shot `findNearestEnemies` use allocating
  `Sequence`/sort chains with `hypot` in comparators. Bounded by the 40-enemy/wave cap.
- **PERF-5 (low, 1/1):** `BackgroundRenderer` redraws 30–50 ambient particles as individual
  `drawCircle` calls every frame atop the pool — a design cost worth measuring with the existing
  macrobenchmark on a low-end device before any perf claim.
- **PERF-6 (low, 1/1):** `render()` allocates a full entities snapshot list every frame — a deliberate
  #118 thread-safety design (snapshot under lock, draw outside); reusable buffer would help.
- **PERF-7 / NET-equivalent (low, 1/1):** `OrbEntity` keeps a per-enemy hit-cooldown HashMap and calls
  `getEnemies()` each tick — O(orbs×enemies) with map churn, bounded by 40 enemies.
- **CICD-9 (low, 1/1):** `gradle.properties` omits parallel/build-cache/configuration-cache and pins
  only 2 GB heap — build/CI throughput only, no runtime impact.

**Recommendation:** Fix PERF-1 (async/cached MediaPlayer) and PERF-2 (throttle trail spawning) — the two
that actually touch frame rate / user-perceived smoothness. The rest are low-priority allocation hygiene.

---

## 8. Production Readiness Assessment

**Readiness rating: READY FOR CONTINUED INTERNAL-TRACK SHIPPING; NOT YET READY FOR BROAD/PUBLIC
LAUNCH.**

The app is correctly on the Play internal track and functions end-to-end. For a **public** launch on
the open Android device population, the following must be addressed first:

**Blockers for public launch (must-fix):**
1. **FEAT-1** — GDD-promised battery-optimization whitelist prompt is entirely absent. On aggressive-OEM
   devices (Xiaomi/Huawei/Samsung/OnePlus) the foreground step service is routinely killed, silently
   stopping the core currency. This is the highest-likelihood/highest-impact risk in the GDD's own
   register, and its named primary mitigation is unimplemented.
2. **REL-3 / #193** — no-sensor UX. A device without a hardware step counter completes onboarding,
   walks, and earns nothing with zero explanation. Add a `hasSystemFeature` check and steer to the
   Health Connect / Activity-Minute path.
3. **NET-1 / NET-2** — offline IAP failures swallowed; pending purchases reconcile only on Store
   re-open (risking Play auto-refund of a kept entitlement). Monetization-facing.
4. **STATE-1** — in-flight battle round destroyed on rotation/config change (no orientation lock, no
   saved state). Add `screenOrientation` lock or hoist the simulation into the VM.
5. **STATE-2 / #195** — Missions day-rollover stale query (open `severity:major`).
6. **CICD-1 / DEP-1** — supply-chain: validate the wrapper jar; add dependency verification.
7. **PRIV-1** — privacy policy vs Data-Safety inconsistency (AdMob location/diagnostics/app-interactions).
8. **The manual Play Console Data-Safety declaration** (#192 — repo cannot do this) and a `v*` release
   tag to ship `[Unreleased]` changes.

**Strongly recommended before public launch:** DATA-1 (atomic premium-currency spend+grant), NET-3
(offline gap-fill clamp loses recovered steps), DATA-4 (DataDeletionManager misses 2 prefs files),
PRIV-2 (no in-app privacy-policy link), PRIV-4 (no COPPA/families config), TEST-1/2/3/4.

**Acceptable as documented deferrals:** I18N-1 and all i18n/l10n findings (English-only v1.0, ADR-0014);
all accessibility findings (post-v1.0 per CLAUDE.md/master-plan Plan 24); ARCH-1/2/3 (design debt).

---

## 9. Product & UX

**Verdict: the core teaching/recovery flows have real, code-grounded gaps; most are low after
refutation because mitigations exist one screen away.**

- **UX-1 (low, 3/3 partial — finder said high):** Onboarding's denial copy says "enable in Settings" but
  the in-app Settings tab has no step-counting/permission control. Refuted from a dead-end to a
  discoverability gap: the cold-launch snackbar auto-re-prompts every launch, and "Replay tutorial"
  reaches the onboarding "Open Settings" deep-link — but neither is discoverable as the recovery path.
  Add a "Permissions & Tracking" section to Settings.
- **REL-3 / #193 (medium, 3/3 confirmed):** No-sensor silent failure (see Section 8). The most
  product-critical UX gap because it makes the game silently unplayable on affected devices with no
  signal.
- **UX-2 (medium, 2/2 partial):** Home has no zero-state / first-walk guidance — post-onboarding the
  player lands on a screen of zeros with a prominent BATTLE button and no "go walk" nudge. Softened by
  the onboarding carousel's final slide, but the persistent Home state still has no guidance for a
  returning new player.
- **UX-3 (medium, 2/2 — 1 confirmed, 1 partial):** Raw `SCREAMING_CASE` enum names leak into the UI in
  6 places (`TierSelector`, `BattleViewModel`, `InRoundUpgradeMenu`, `UpgradeCard`,
  `UltimateWeaponScreen`) via `.name.replace('_',' ')` instead of the existing `toDisplayName()` helper
  — so the app shows both "Chain Lightning" and "CHAIN LIGHTNING" depending on screen. Cosmetic but
  visible on primary surfaces.
- **UX-4 (low, 2/2 partial):** Inconsistent copy externalization — ~108 hardcoded Compose literals vs
  strings.xml. Downgraded to low because it's an ADR-0014-documented phased deferral (see I18N-1 for the
  full treatment).
- **UX-5 (low, 1/1):** Locked tiers give no "how to unlock" affordance; the `bestWavePerTier` data
  needed to render a precise hint is passed into `TierSelector` and dropped on the floor.
- **UX-8 (low, 1/1):** Help is a static, unsearchable text wall with emoji headers that re-introduce the
  exact placeholder-looking glyphs `HomeScreen` deliberately migrated off.

---

## 10. Scorecard (/10)

Scores reflect the post-refutation evidence. They are relative to a production-readiness bar for an
app already on the internal track aiming at a public launch.

| Dimension | Score | Rationale |
|---|---:|---|
| Architecture & Design | 7/10 | Strong intent and structure; real but non-runtime dependency-direction debt (ARCH-1/2/3) and a false-confidence purity guard. |
| Code Quality & Maintainability | 7/10 | High overall; two god-class hotspots and assorted dead-code/naming artifacts. |
| Testing & Coverage | 6/10 | Excellent JVM breadth (1069); zero Compose UI tests, untested migration chain, serial-only economy tests, and an open bug (#195) with no regression guard. |
| Security | 7/10 | Solid on-device posture; supply-chain hardening gaps (unvalidated wrapper, no dep verification) inconsistent with the project's own pinning discipline. |
| Performance | 7/10 | Pooled/bounded; one main-thread audio hitch and battle-tick GC churn. |
| Product & UX | 6/10 | Core loop works; reliability/onboarding-recovery and no-sensor gaps undercut the walking-gated value prop on real devices. |
| Reliability | 6/10 | Good defensive patterns (loop guard, breadcrumbs); battery-whitelist absence + no-sensor silence + config-change round loss are real reliability gaps. |
| Data & Persistence | 7/10 | Atomic guarded-deduct pattern is excellent; premium-currency spend+grant is the one non-atomic flow; migration floor unguarded; DataDeletionManager incomplete. |
| Build / CI / Release | 6/10 | Real pipeline with good guards; wrapper not validated, no version-vs-tag guard, schema-drift gate misses new untracked files. |
| Documentation | 8/10 | Exceptional memory spine and ADRs; localized drift (stale .docx, wrong test command, migration-floor inaccuracy, oversized STATE.md). |
| **Overall** | **7/10** | A disciplined, shipping-quality codebase with a concentrated set of reliability/monetization/release-hygiene gaps to close before broadening beyond the internal track. |

---

## 11. Reliability & Resilience

**Verdict: good defensive scaffolding (game-loop try/catch, crash breadcrumbs, START_STICKY) with
several confirmed crash/data-loss paths, all bounded after refutation.**

- **REL-1 (medium, 3/3 — 1 high, 2 medium):** `StepCounterService.onCreate` calls `startForeground()`
  with no try/catch (`StepCounterService.kt:54-65`). A throw propagates → process death; the breadcrumb
  handler records but doesn't prevent it; START_STICKY retries into the same crash. Refuted from high
  because the headline `ForegroundServiceStartNotAllowedException` is **exempted** on the BOOT_COMPLETED
  path and the MainActivity starts are foreground-exempt — the realistic trigger is a narrow
  permission-revoke race producing a `SecurityException`. Wrap it and `stopSelf()` gracefully.
- **REL-2 (medium, 2/2 confirmed):** `GameSurfaceView` releases its `SoundManager` on `surfaceDestroyed`
  but never recreates it. After one background→resume cycle, every `engine.soundManager?.play()` hits a
  released `SoundPool` (no-op) — combat SFX silently die for the rest of the session. Cosmetic (no
  crash/data), but reproducible and untested. Recreate in `surfaceCreated` or move ownership to the VM.
- **REL-4 (low→medium, 2/2 confirmed):** `MusicManager.createPlayer` assumes `MediaPlayer.create()`
  never returns null; the non-null return + immediate `.apply{}` NPEs on the main thread on codec/OOM
  failure. Rare (valid bundled assets) but uncaught and untested. One refuter held low (rare path), one
  medium.
- **DATA-3 / REL (low, 2/2 partial):** Migration floor — no general upgrade destructive fallback (the
  "crash for real users" framing refuted; latent future-regression risk survives). See TEST-2 / DATA-3.
- **DATA-4 (medium, 2/2 confirmed):** `DataDeletionManager` omits `onboarding_prefs` and `haptics_prefs`
  from the "full wipe." After "Delete All Data," the onboarding flag survives, so the recreated app
  lands on Home (not the tutorial) with an empty DB — a confusing post-wipe state and a contract
  violation. The surviving keys are non-PII, so the privacy framing was trimmed; reliability/UX holds.
- **DATA-5 (medium, 1 confirmed/1 partial):** `DataDeletionManager` closes the `@Singleton` AppDatabase
  while async work (cancelled WorkManager, stopped service) may still touch it; `activity.recreate()`
  doesn't rebuild singletons. A narrow race window; worst case is a swallowed background-thread
  exception, not process death, on a deliberate destructive action. Await cancellation / restart the
  process instead of `recreate()`.
- **REL-5 (low, 1/1):** Orphaned `GameLoopThread` after `join(1000)` timeout can keep ticking against a
  torn-down surface; bounded by the locks + guarded `unlockCanvasAndPost` (no crash, no corruption).
- **REL-6 (low, 1/1 partial):** `StepSyncWorker.doWork` always returns `Result.success()` and never
  retries; the "permanently lost" framing refuted (gap-based catch-up self-heals next cycle), residual
  is up-to-15-min latency on a transient fault.
- **REL-7 (low, 1/1 partial):** `applyDamageToZiggurat` HP-ratio division is unguarded against
  `maxHp==0` (NaN-poisoned comparison) — unreachable today (no path drives maxHealth to 0), latent
  consistency nit vs the guarded line 423.
- **REL-8 (low, 1/1 partial):** BootReceiver doesn't handle `MY_PACKAGE_REPLACED`/`LOCKED_BOOT_COMPLETED`
  — after a Play update the foreground service/notification doesn't restart until app launch; WorkManager
  catch-up recovers step *totals*, so it's a notification/real-time-credit latency gap, not data loss.
- **REL-9 (low, 1/1):** `runFollowOnPipeline` runs DB writes + a notification post + a `Flow.first()`
  while holding the credit Mutex (see PERF/NET context); each stage is failure-isolated, bounded impact.
- **REL-10 / DIAG (medium, 2/2 confirmed):** **Four silent `catch(_: Exception){}` blocks** in
  `DailyStepManager.runFollowOnPipeline` (lines 242/258/274/277) swallow all follow-on failures with no
  log/breadcrumb. With no analytics SDK, a recurring failure in supply-drop generation / economy rewards
  / mission progress is completely invisible — the single weakest diagnostic spot in the data layer.
  Transient failures self-heal; systematic ones are silent. Route through `CrashBreadcrumbStore`/`Log.w`.
- **REL-11 (low, 1/1 confirmed):** No retry/visibility when the foreground-notification balance read
  fails repeatedly — the #43 fold correctly avoids "Balance: 0," but a persistent read failure pins the
  value silently. Add a throttled breadcrumb.
- **TEST-5 (medium):** Service/BootReceiver/audio reliability paths — the exact paths above — have no
  test coverage, so regressions here are invisible until a production crash.

---

## 12. Data & Persistence

**Verdict: the atomic guarded-deduct pattern is a genuine strength; the surviving gaps are one
non-atomic premium-currency flow, an unguarded migration floor, and a defense-in-depth credit-validation
gap.**

- **DATA-1 (high, 3/3 — 2 high, 1 medium):** Premium-currency **spend and grant are split across two
  non-atomic DB writes**. `OpenCardPack` does `spendGems()` (its own transaction) then a separate
  `repeat(3){ incrementCopyCount/addCard }`; `UnlockUltimateWeapon`/`UpgradeUltimateWeapon` do
  `spendPowerStones()` then a separate repository write. A crash/kill/cancellation between the committed
  spend and the grant permanently debits real-money-purchasable Gems / Power Stones with no card/weapon
  delivered and **no reconciliation record** (unlike billing's `grantOnceAtomic`). This is exactly the
  partial-failure gap the project closed for Steps (`WorkshopDao.purchaseUpgradeAtomic`), milestones,
  battle steps, and billing — but these three flows were never given the same `@Transaction` treatment.
  Fix: a single cross-DAO `@Transaction` per flow. (One refuter held medium: narrow crash window,
  bounded per-incident loss, offline single-player.)
- **DATA-2 (medium, 2/2 confirmed):** Migration test coverage is one step of five; the two riskiest
  recreate-table migrations (9→10, 10→11, with data transforms — UW level→3 path columns via integer
  division, card dust→copy aggregation) are untested. The SQL currently matches the schema exports, so
  this is a latent-risk/coverage finding, not a present corruption.
- **DATA-3 (low, 2/2 partial):** No general destructive-migration fallback for upgrades + no migration
  below v7. Real-user crash refuted (floor is v9+ in the field); residual is a future-regression hazard
  + an undocumented floor.
- **CICD-2 / TEST-2 (medium, 2/2 confirmed):** No automated guard that exported schema version equals
  `AppDatabase.version` or that migrations are contiguous. A future version bump that commits the new
  schema JSON (passing the drift gate) but forgets to register the Migration object ships a guaranteed
  launch crash for every upgrading user, uncaught by CI or the suite.
- **DATA-6 (low, 1/1 partial):** `spendSteps` uses the `MAX(0,…)` clamp with no rows-affected gate
  (intentional for the anti-cheat escrow clawback per CLAUDE.md). All current callers are clawbacks; the
  footgun is a future caller confusing it with `spendStepsIfSufficient` — already mitigated by KDoc and
  a fixed historical bug (#122). Consider renaming to `clawbackSteps`.
- **DATA-7 (low, 1/1 confirmed):** Card copy increment is a read-then-insert that can lose a copy under
  concurrency (same shape #127 fixed for missions). The OpenCardPack path is serialized; the **unguarded
  `ClaimSupplyDrop` path** (no `_processing` guard) is the reachable race. The unique index prevents
  corruption; worst case is one shorted cosmetic copy. Replace with an atomic upsert.
- **DATA-8 / data-validation (low, 1/1 partial):** Currency credit methods (`addSteps`/`addGems`/
  `addPowerStones`) accept unvalidated signed Long — a negative would silently debit and corrupt the
  lifetime-earned stat. All 14 production call sites are guarded against negatives, so it's a
  defense-in-depth gap, not a reachable defect. Add `require(amount >= 0)`.

---

## 13. Offline & Network Behavior

**Verdict: the offline-first model is sound, but the monetization and step-recovery paths degrade poorly
offline — the highest-value offline findings in the audit.**

- **NET-1 (medium, 3/3 — 1 high, 2 medium):** Offline IAP failures are **silently swallowed**. The three
  `StoreViewModel.purchase*` methods discard the `PurchaseResult`; the carefully-authored offline error
  strings ("Network error…", "Purchase pending — complete payment…") are dead. On poor network the
  spinner clears and nothing happens — no error, no retry hint. PENDING is especially bad. The ad path
  *does* surface `AdResult.Error`, so this is an inconsistency, not a platform limit. Refuted from high
  because the success path credits atomically and PENDING is eventually reconciled — it's an error-
  feedback defect, not data loss.
- **NET-2 (high, 3/3 confirmed):** Pending/offline-completed purchases reconcile **only** when the user
  re-opens the Store (`reconcilePendingPurchases()` is called solely from `StoreViewModel.init`). Play
  auto-refunds purchases not acknowledged within 3 days. A player who buys Ad Removal / Season Pass on a
  flaky connection, gets the local grant, but whose `acknowledge()` RPC fails, will have the purchase
  **refunded by Google** if they don't re-open the Store within 3 days — losing a kept entitlement. No
  resume hook, no periodic worker, no connectivity-regained trigger. The async PENDING→PURCHASED listener
  explicitly drops orphaned purchases for the next sweep. Trigger reconcile from `onResume` and/or the
  existing 15-min `StepSyncWorker`.
- **NET-3 (medium, 2/2 confirmed):** Offline-recovery gap-fill is silently clamped to ~200 steps/min by
  the rate limiter. `StepGapFiller.fillGaps` credits a multi-hour recovered gap (e.g. ~10,000 steps after
  the service was killed during a walk) through `recordSteps` → `StepRateLimiter.credit`, capping a
  single window at 250 and **permanently dropping the remainder** (the raw delta advances `sensorTotal`,
  so the next gap is 0 — the dropped steps can never be re-presented). The anti-cheat intent is valid but
  conflates "live sensor delta" rate limiting with "batch recovery of an elapsed period." Credit recovered
  gaps against minutes-elapsed × cap, or via HC per-bucket totals.
- **REL-6 / NET (low, 1/1 partial):** `StepSyncWorker` has no network constraint and never retries — HC
  sync failures dropped per cycle. Refuted from medium: the credit pipeline is idempotent/self-healing
  and HC is local IPC (a network constraint would be *wrong*); residual is no fast retry/backoff.
- **NET-5 (low, 1/1 confirmed):** HC readers conflate transient/network errors, "not installed," and "no
  permission" into the same null result. A previously-escrowed (penalized) user won't get steps released
  on a cycle where the HC read transiently fails (validate() bails before the escrow-release branch). A
  delay, not permanent loss; flagged as a fairness defect on flaky-HC devices.
- **NET-6 (low, 1/1 confirmed):** No connectivity awareness anywhere — offline ad/IAP CTAs aren't pre-
  disabled, only fail after a tap (the free-card-pack path can hold the processing flag ~60s on AdMob's
  internal timeout). Degraded-UX/polish.

---

## 14. Accessibility

**Verdict: real and pervasive gaps, but **explicitly scoped post-v1.0** (CLAUDE.md, master-plan Plan
24), so all findings are correctly low/medium and non-blocking for v1.0.**

- **A11Y-BATTLE (medium, 3/3 partial — finder said high):** The battle SurfaceView is invisible to
  TalkBack — no AccessibilityDelegate, no live region, no combat narration. The Compose HUD is a static
  snapshot with no `liveRegion`; the wave-progress bar conveys phase by color only. Refuted from high
  because (a) the GDD §17 audio-cue promise *is* met (7 SFX fire during battle), (b) the GDD scopes
  TalkBack to "All menus," not the battle screen, (c) a round is completable via auto-firing UWs, and
  (d) accessibility is an explicit post-v1.0 priority.
- **A11Y-CONTRAST-1 (medium, 2/2 confirmed):** Primary Gold buttons fail WCAG AA — DeepBronze on Gold =
  4.19:1 (< 4.5:1 for normal text), affecting every primary CTA app-wide (onboarding Next/Start, gold
  purchase buttons). The Ivory alternative is worse (2.09:1). One-token fix toward a deeper bronze.
- **A11Y-CONTRAST-2 (low, 2/2 partial):** UW cooldown number is gray-on-dark at 11sp — actual contrast
  3.95:1 (finder's 3.55 was based on a wrong `Color.Gray` value of #808080; it's #888888), still < 4.5:1.
  TalkBack is covered by an existing contentDescription; only sighted low-vision users hit it.
- **A11Y-CVD (medium, 1 confirmed/1 partial):** GDD-promised color-blind modes (three palettes) are
  entirely absent; status is color + small icons. The wave-phase bar is the one genuine sole-color
  channel. Documented post-v1.0 deferral, but the GDD presents it as a flat promise — store-listing
  honesty risk if marketed as accessible.
- **A11Y-HEADINGS (low, 1/1):** No semantic `heading()` declared anywhere — TalkBack users can't
  navigate long screens by heading (WCAG 1.3.1). The team uses `contentDescription` in 14 files, so they
  know the API.
- **A11Y-LOADING (low, 1/1):** Full-screen `LoadingBox` (10 reused screens) has no contentDescription/
  liveRegion and no "Loading…" text.
- **A11Y-TOUCH (low, 1/1 partial):** No shipped interactive control under 48dp could be confirmed
  (Material defaults enforce it) — a forward-looking hygiene note.
- **A11Y-DISABLED (low, 1/1):** Disabled in-round upgrade buttons use DarkGray with no labelled reason
  ("not enough cash" vs "max level") and 10–12sp text.
- **I18N (a11y angle, low, 1/1):** Hardcoded contentDescription/page-dot strings are un-localizable, so
  even TalkBack announcements can't be localized.

---

## 15. Internationalization & Localization

**Verdict: a hard structural blocker for any non-English market, correctly deferred for an English-only
v1.0 (ADR-0014), but the first-run flow is 100% hardcoded.**

- **I18N-1 (high, 3/3 confirmed):** The bulk of user-facing screens (16 surfaces incl. onboarding, help,
  store, settings, stats, missions, labs, cards, weapons, home, economy, supplies) hold hardcoded English
  `Text("…")` literals — 108 confirmed, with 16 screens at zero `stringResource`. The first-run/onboarding
  flow (highest-visibility surface) is entirely hardcoded. The `HardcodedText` lint-as-error guard is
  XML-only and does **not** catch Compose literals (documented at `build.gradle.kts:205-208`), so there
  is no machine guard against further drift. Kept at high as an i18n-readiness blocker, though it is a
  documented, accepted phased deferral (ADR-0014 phase 1 = notifications + battle/workshop only).
- **I18N-2 (medium, 1 confirmed/1 partial):** No plurals support anywhere — quantity nouns baked as fixed
  singular/plural ("+%d Step" always singular; "%d steps" always plural; "%d enemies" wrong at n=1). The
  English n=1 grammar is wrong today; multi-form languages can't be localized. Affects already-shipped
  notification + HUD strings.
- **I18N-3 (low, 2/2 confirmed — finder said medium):** Three coexisting number-format mechanisms, two of
  which use the JVM default locale. The same balance renders with US commas on the currency widget but
  device-locale separators on Home/Stats/Missions/Economy. `CurrencyDashboardScreen.kt:82` mixes a
  default-locale operand with a hardcoded US-comma literal in one line. Cosmetic; English-only today.
- **I18N-4 (low, 1 refuted/1 partial — finder said medium):** `BillingProduct.skuId()` uses
  `name.lowercase()` with no Locale. One refuter showed Kotlin's `lowercase()` is **locale-invariant**
  (uses `Locale.ROOT` internally, unlike the deprecated `toLowerCase()`), refuting the Turkish-i SKU-break
  premise. The other kept it as a latent style nit. Net: effectively a non-issue; an explicit `Locale.ROOT`
  would document intent.
- **I18N-5 (low, 1/1 confirmed):** Display-side `uppercase()`/`lowercase()` in `EnumDisplayName` and
  battle/stats use the default locale — cosmetic Turkish-i risk on labels (e.g. "Enemy İntel" on a tr/az
  device). Pass `Locale.ROOT`.
- **I18N-6 (medium, 2/2 — 1 confirmed/1 low):** Translation-hostile string concatenation and **raw enum-
  name surfacing** — `GameEngine.kt:832` emits `"Next: " + "${count} ${enemy.name}"`, so the ENEMY_INTEL
  overlay literally shows "Next: 12 BASIC, 4 RANGED, 1 BOSS" to the player. Both untranslatable and
  unpolished. Use a localized `@StringRes` per `EnemyType` and full templated strings.
- **I18N-7 (low, 1/1):** RTL declared (`supportsRtl=true`) but unverified — no RTL tests, no pseudo-
  locale, leading-glyph/emoji strings and a hardcoded `$` cash prefix. Layouts use RTL-safe start/end
  paddings (no `absolute*`), so likely fine, but never exercised.
- **I18N-8 (low, 1/1):** Glossary inconsistency — "steps" vs "Steps" vs "Step" mixed within one string
  (`notif_step_content` uses both casings). Undermines translation consistency.

---

## 16. Feature Completeness (vs GDD)

**Verdict: one high-impact unimplemented mitigation and several cosmetic/monetization GDD-vs-reality
drifts.**

- **FEAT-1 (high, 3/3 — 2 high, 1 medium):** GDD §11.2's battery-optimization whitelist prompt — named in
  the §19 risk register as the primary mitigation for the **highest** likelihood/impact risk (OEM battery
  optimization killing the step service) — is **entirely absent**. Zero references to
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`/`isIgnoringBatteryOptimizations`/`PowerManager` anywhere. The two
  other listed mitigations (WorkManager catch-up, HC gap-filling) only partially cover this and depend on
  HC being installed/granted. The user just sees a game that "stopped counting my steps."
- **FEAT-2 (medium, 2/2 — 1 confirmed/1 low):** Cosmetic catalog is largely non-functional — 4 of 11
  seeded cosmetics (all PROJECTILE_EFFECT + ENEMY_SKIN items) have **no render path**; the renderer reads
  cosmetics only for ZIGGURAT_SKIN. Dead data the GDD §13 sells as a monetization category. The four are
  also excluded from the purchasable allowlist, so no current user can buy them (one refuter dropped to
  low on that basis).
- **FEAT-3 (low, 1/1 partial):** Store cosmetics are mostly "Coming Soon" — only 2 of 7 ziggurat skins are
  purchasable; `zig_crystal`/`zig_golden` have gem prices in seed data but no render palette. A deliberate,
  documented allowlist gate (the disabled card shows no price, so "reads as broken" was softened).
- **FEAT-4 (low, 1/1):** GDD claims biome transitions "unlock a cosmetic ziggurat skin" — no biome→cosmetic
  unlock linkage exists; cosmetics come only from Store/milestones. GDD-vs-reality drift the 2026-06-16
  reconciliation missed.
- **FEAT-5 (low, 1/1):** GDD presents the biome transition as a "cinematic showing the ziggurat ascending";
  the implementation is a static text overlay (the total-steps half is present). Reward-moment polish gap.
- **FEAT-6 (low, 1/1):** GDD §11.1 still lists `TYPE_STEP_DETECTOR` as a Tertiary realtime-feedback sensor;
  it's unimplemented and only the steering doc carries the deferral note — the GDD and steering doc disagree.

---

## 17. State Management

**Verdict: the durable game data is safe in Room, but transient/in-flight state has confirmed loss paths
on the core gameplay screen and the missions flow.**

- **STATE-1 (high, 3/3 — 2 high, 1 medium):** An in-flight battle round is destroyed on any configuration
  change. `BattleScreen.kt:67` holds the engine via `remember { GameSurfaceView(context) }` (not
  `rememberSaveable`, not hoisted), and MainActivity has no `configChanges`/`screenOrientation` — so
  rotation/dark-mode/font-scale/multi-window fully recreates the Activity, discards the engine, and builds
  a fresh one at wave 1 / 0 cash while the surviving VM thinks a round is in progress. Refuted from the
  "mis-credit / skip persistence" framing: the realistic outcome is **silent loss of the un-finalized
  round** (live-earned Steps are credited per-event, so permanent currency is safe). Lock orientation or
  hoist `Simulation` into the VM.
- **STATE-2 / #195 (medium, 3/3 — 1 high, 2 medium):** `MissionsViewModel` binds `getByDate(today)` with a
  construction-time snapshot of `today` (`MissionsViewModel.kt:83-84`); the midnight ticker reassigns the
  var but the `combine` stays bound to the old-date Flow. After local midnight the UI shows yesterday's
  (now uncompletable) missions while the new day's are invisible until VM recreation. `HomeViewModel`/
  `StatsViewModel` correctly use `flatMapLatest` over a date StateFlow + ON_RESUME refresh — Missions is the
  lone broken one. Self-heals on any tab switch (no data loss, claims still atomic), hence medium not high.
- **STATE-3 (medium, 1 confirmed/1 partial):** No `SavedStateHandle`/`rememberSaveable` anywhere —
  transient UI and in-flight state lost on process death. The sharpest case is the just-opened card-pack
  reveal (cards persisted, reveal animation lost). Durable data is safe in Room; permission flags mostly
  re-derive (the "false-positive defaults" sub-claim was overstated — `onboardingComplete` and
  `stepCountingGranted` re-derive from durable sources; only `permissionAsked` resets to a conservative
  false).
- **STATE-4 (medium, 1 confirmed/1 partial — finder said medium):** 4 screens use plain `collectAsState()`
  instead of `collectAsStateWithLifecycle()` (Store/Labs/Cards/Battle), keeping StateFlows hot while
  backgrounded (LabsViewModel's 1s ticker recomputes the whole combine every second behind a dialog). One
  refuter dropped to low (battery micro-inefficiency, not correctness); the BattleViewModel sub-case was
  refuted (it uses `asStateFlow`, no `WhileSubscribed` upstream).
- **STATE-5 (low, 1/1 partial):** `SettingsViewModel` mirrors SharedPreferences into a one-shot StateFlow
  seeded at init — fragile single-writer assumption; refuted as a present defect (Settings is the sole
  writer), survives as latent-design debt.
- **STATE-6 (low, 1/1 confirmed):** `userMessage` in the persisted StateFlow re-shows the snackbar on
  rotation because `clearMessage` only fires after `showSnackbar` completes (copy-pasted across 6 screens).
  Cosmetic; MainActivity's own hint snackbar documents the same accepted tradeoff. Use a Channel/event.
- **STATE-7 (low, 1/1 partial):** WorkshopViewModel/CardsViewModel mirror Room data into a plain `var`
  read by command handlers — a redundant second source of truth. The wrong-outcome consequence was refuted
  (the screen uses lifecycle-aware collection, so the snapshot is fresh when clicks fire); survives as an
  anti-pattern/maintainability nit.
- **STATE-8 (low, 1/1 confirmed):** `CurrencyDashboardViewModel` snapshot refreshes only via
  `LaunchedEffect(Unit)` on first composition — no resume/midnight refresh, so weekly/daily-login state
  goes stale until VM recreation (unlike Stats/Home which wire ON_RESUME). Profile half stays reactive.

---

## 18. Privacy & Compliance

**Verdict: the on-device privacy model is sound, but there are real policy-consistency and disclosure gaps
to close before promoting past the internal track.**

- **PRIV-1 (medium, 3/3 — 1 high, 2 medium):** The public privacy policy (`site/index.md`) omits the AdMob
  collection categories the Data-Safety form declares — Approximate Location (IP-derived), Diagnostics, and
  App interactions. The policy discloses only the advertising ID; "location"/"diagnostics"/"app
  interactions" appear nowhere. Play requires policy↔Data-Safety consistency. Refuted from high to medium:
  the policy carries an incorporation-by-reference catch-all ("device and usage information as described in
  Google's policies") that plausibly covers diagnostics/app-interactions, and the form is an unsubmitted
  planning doc — but **location is a sensitive category named nowhere**, a genuine under-disclosure.
- **PRIV-2 (medium, 2/2 confirmed):** No in-app privacy-policy link anywhere in the main UI (Settings/Help)
  — the only in-app link is inside the Health Connect permission-rationale screen, reachable only via the
  HC framework flow (never from in-app navigation). A user who never triggers the HC UI has no in-app path
  to the policy. Refuted from any "hard rejection" claim: the store-listing URL + hosted policy satisfy
  Play's baseline, so this is a discoverability/best-practice gap. Add a "Privacy Policy" row to Settings.
- **PRIV-4 (medium, 2/2 confirmed):** No COPPA / families-policy configuration — AdMob initialized with no
  `RequestConfiguration`, no `tagForChildDirectedTreatment`/`tagForUnderAgeOfConsent`/`maxAdContentRating`,
  no age gate — despite the policy asserting the app is "not directed to children." A walking game has
  plausible minor appeal; this is a COPPA/GDPR-K/Families-policy exposure the code doesn't enforce. The
  UMP consent path also doesn't set under-age treatment. (Note: the binding legal control is the Play
  Console target-audience declaration, which lives outside the repo.)
- **PRIV-3 / SEC-2 (low, 2/2 — see Section 6):** Purchase tokens logged in plaintext; release doesn't strip
  Log. Downgraded to low (logcat read needs privileged READ_LOGS on minSdk 34).
- **DATA-4 (privacy angle, low):** "Delete All Data" leaves `haptics_prefs` + `onboarding_prefs` behind —
  the surviving keys are non-PII (a boolean + a completion flag), so the privacy framing was trimmed; the
  reliability/UX angle (DATA-4) holds.
- **PRIV-7 (low, 1/1 confirmed):** Onboarding permission primer requests ACTIVITY_RECOGNITION with no
  data-use disclosure or policy link at the point of the sensitive ask — best-practice gap (the data is
  genuinely on-device-only and disclosed elsewhere; the system dialog mediates the actual grant).
- **SEC-4 (low, 1/1 partial — see Section 6):** `obfuscatedAccountId` durability tradeoff is the privacy-
  correct choice and already documented. Informational.
- **SEC-POS (positive control):** Deep-link `navigate_to` extra is allowlist-validated + `FLAG_IMMUTABLE`
  — verified safe, recorded so future maintainers don't relax it.

---

## 19. Technical Debt Register (stable finding IDs)

Severities are post-refutation (finalSeverity). RT = refutation trail (refuters × verdict tally).

| ID | Sev | Area | Summary | RT |
|---|---|---|---|---|
| **FEAT-1** | High | Feature | GDD battery-optimization whitelist prompt entirely absent (top GDD risk's primary mitigation) | 3× (2C/1P) |
| **NET-2** | High | Offline | Pending/offline purchases reconcile only on Store re-open → Play auto-refund of kept entitlement | 3× (3C) |
| **STATE-1** | High | State-mgmt | In-flight battle round destroyed on config change (no orientation lock, no saved state) | 3× (2C/1P) |
| **I18N-1** | High | i18n | 16 screens / ~108 hardcoded Compose literals; first-run flow 100% hardcoded (lint can't catch) | 3× (3C) |
| **TEST-1** | High | Testing | Open bug #195 (missions day-rollover) has zero regression coverage | 3× (3C) |
| **CICD-1** | High | Security | CI/release never validates committed `gradle-wrapper.jar` (runs in signing lane) | 3× (3C) |
| **DATA-1** | High | Data | Premium-currency spend+grant split across two non-atomic writes (crash loses Gems/PS) | 3× (3C) |
| **REL-3 / #193** | Medium | Product/Rel | No-sensor UX: sensor absence fails silently with only a `Log.w` | 3× (3C) |
| **NET-1** | Medium | Offline | Offline IAP failures silently swallowed (result discarded, no error shown) | 3× (3C) |
| **NET-3** | Medium | Offline | Offline gap-fill clamped to ~200/min → most recovered steps permanently lost | 2× (2C) |
| **STATE-2 / #195** | Medium | State-mgmt | Missions day-rollover stale query (combine bound to construction-date Flow) | 3× (3C) |
| **STATE-3** | Medium | State-mgmt | No SavedStateHandle/rememberSaveable; transient state lost on process death | 2× (1C/1P) |
| **STATE-4** | Medium | State-mgmt | 4 screens use plain `collectAsState()` (StateFlows hot while backgrounded) | 2× (1C/1P) |
| **DATA-2** | Medium | Data | Riskiest recreate-table migrations (9→10, 10→11) untested | 2× (2C) |
| **DATA-4** | Medium | Reliability | "Delete All Data" omits `onboarding_prefs` + `haptics_prefs` (confusing post-wipe state) | 2× (2C) |
| **DATA-5** | Medium | Reliability | DataDeletionManager closes singleton AppDatabase amid async work; recreate() doesn't rebuild it | 2× (1C/1P) |
| **CICD-2 / TEST-2** | Medium | Build/Data | No guard that schema version == AppDatabase.version or migrations contiguous | 2× / 3× |
| **REL-1** | Medium | Reliability | `StepCounterService.startForeground` unguarded → process death (permission-revoke race) | 3× (1C/2P) |
| **REL-2** | Medium | Reliability | SoundManager released on surfaceDestroyed, never recreated → SFX die after resume | 2× (2C) |
| **REL-4** | Medium | Reliability | `MusicManager.createPlayer` NPEs on null `MediaPlayer.create()` | 2× (2C) |
| **REL-10 / DIAG** | Medium | Reliability | 4 silent `catch(_:Exception){}` blocks swallow follow-on-pipeline failures (no log/breadcrumb) | 2× (2C) |
| **PERF-1** | Medium | Performance | 1.3 MB OGG decoded synchronously on main thread per Battle↔menu transition | 3× (1C/2P) |
| **PERF-2** | Medium | Performance | Projectile-trail spawns per-projectile per-tick, thrashing the 200-slot pool | 2× (2C) |
| **ARCH-1** | Medium | Architecture | 9 domain use cases import the data layer (DAOs, Room @Entity, SystemTimeProvider) | 3× (3C) |
| **ARCH-2** | Medium | Architecture | DomainPurityTest only scans Android prefixes → misses domain→data violation | 3× (3C) |
| **ARCH-3** | Medium | Architecture | 6 ViewModels inject raw DAOs; BattleViewModel injects AppDatabase + withTransaction | 3× (3C) |
| **ARCH-4** | Medium | Architecture | 13 DAOs but only 8 repositories (missions/milestones/login/challenge unported) | 2× (2P) |
| **ARCH-7 / CQ-1** | Medium | Arch/Quality | GameEngine 1223-line hotspot; ADR-0012 extraction partial | 2×+2× (4C) |
| **A11Y-CONTRAST-1** | Medium | A11y | Gold primary buttons fail WCAG AA (DeepBronze on Gold = 4.19:1) | 2× (2C) |
| **A11Y-BATTLE** | Medium | A11y | Battle SurfaceView invisible to TalkBack; HUD no live region (post-v1.0 scoped) | 3× (3P) |
| **A11Y-CVD** | Medium | A11y | GDD-promised color-blind palettes entirely absent (post-v1.0 scoped) | 2× (1C/1P) |
| **I18N-2** | Medium | i18n | No plurals anywhere (wrong at n=1; un-localizable for multi-form languages) | 2× (1C/1P) |
| **I18N-6** | Medium | i18n | Translation-hostile concatenation + raw enum-name surfacing ("Next: 12 BASIC") | 2× (1C/1low) |
| **FEAT-2** | Medium | Feature | 4 of 11 cosmetics have no render path (dead data sold as a category) | 2× (1C/1low) |
| **PRIV-1** | Medium | Privacy | Policy omits AdMob location/diagnostics/app-interaction the Data-Safety form declares | 3× (1C/2P) |
| **PRIV-2** | Medium | Privacy | No in-app privacy-policy link (only via HC rationale screen) | 2× (2C) |
| **PRIV-4** | Medium | Privacy | No COPPA/families config; AdMob init with no RequestConfiguration/age gate | 2× (2C) |
| **CICD-3** | Medium | Build | Schema-drift CI gate misses NEW untracked schema files (only catches modifications) | 2× (2C) |
| **CICD-7 / DEP-wrapper** | Medium | Security | Gradle wrapper has no `distributionSha256Sum` | 2× (1low/1C) |
| **DEP-1** | Medium | Security | No Gradle dependency verification metadata | 3× (3C) |
| **CICD-6 / DEP-2** | Medium | Build | kotlinx-coroutines runtime undeclared/unpinned (61 files, transitive only) | 2× (1C/1P) |
| **DEP-dependabot** | Medium | Build | Dependabot opens ungrouped PRs despite a documented combined-verification regression | 2× (1C/1low) |
| **TEST-3** | Medium | Testing | Guarded-deduct DAOs tested only serially, never under concurrent contention | 2× (2C) |
| **TEST-4** | Medium | Testing | Zero Compose UI tests (entire presentation rendered behavior unverified) | 2× (2C) |
| **TEST-5** | Medium | Testing | Service/BootReceiver/audio reliability paths have zero test coverage | 2× (2P) |
| **DOC-migration** | Medium | Docs | database-schema.md claims fallbackToDestructiveMigration in use (code: OnDowngrade only) | 2× (2C) |
| **UX-2** | Medium | Product | Home has no zero-state / first-walk guidance | 2× (2P) |
| **UX-3** | Medium | Product | Raw SCREAMING_CASE enum names leak into UI in 6 places (vs toDisplayName helper) | 2× (1C/1P) |
| *(Low findings below — abbreviated)* | | | | |
| **UX-1** | Low | Product | Onboarding "enable in Settings" but Settings has no permission control (downgraded from high) | 3× (3P) |
| **UX-5/UX-8** | Low | Product | Locked tiers no unlock hint; Help static text wall w/ emoji headers | 1×+1× |
| **A11Y-*** | Low | A11y | Contrast-2, headings, loading box, touch targets, disabled-button reason | 1× each |
| **CQ-2..9** | Low | Quality | Dead `resetUWCooldowns`/`cooldownText`/`fps`; Card Dust API; `fortuneMultiplier` rename; AdResult/purchase/string dup | 1× each |
| **ARCH-5/8, ARCH-6** | Low | Arch | Card Dust dead surface; empty HealthConnectModule + loose data-root files; BattleViewModel god-object (downgraded) | 1×–2× |
| **DATA-3/6/7/8** | Low | Data | Migration floor; spendSteps clamp; card-copy race (ClaimSupplyDrop); unvalidated signed credit | 1×–2× |
| **REL-5..11, NET-5/6** | Low | Reliability/Offline | Orphaned loop thread; worker no-retry; NaN HP guard; MY_PACKAGE_REPLACED; HC error conflation; no connectivity awareness; balance-read visibility | 1× each |
| **PERF-3..7, CICD-9** | Low | Performance | getAliveEnemies/bounce/background/render alloc; orb hashmap; gradle.properties tuning | 1×–2× |
| **STATE-5..8** | Low | State-mgmt | SettingsVM one-shot; snackbar re-show; VM `var` mirror; CurrencyDashboard no resume refresh | 1× each |
| **I18N-3/4/5/7/8** | Low | i18n | Number-format mix; skuId (effectively non-issue); display-side case; RTL unverified; glossary | 1×–2× |
| **FEAT-3/4/5/6** | Low | Feature | Store "Coming Soon" cosmetics; biome→skin unlock missing; biome "cinematic" is static; TYPE_STEP_DETECTOR doc | 1× each |
| **SEC-1/3/4/5, PRIV-3/7, CICD-5** | Low | Security/Privacy | SHA1withRSA (Google-mandated); no log strip; obfuscatedAccountId; widget export; token logging; primer disclosure; CI plaintext secrets | 1× each |
| **CICD-4/8, DEP-3/4/5, DOC-*, TEST-6..10** | Low | Build/Docs/Testing | lintDebug-only; gradlew vs run-gradle.sh; jarsigner-verify; release concurrency; dep-submission scope; Play Billing v8→v9; bleeding-edge toolchain; stale .docx; wrong test command; CLAUDE.md tree omission; oversized STATE.md; README test count; broken xref; source-files gap; keystore-recovery half-tested; Turbine; Thread.sleep; EscrowLifecycleTest stub | 1× each |

---

## 20. Recommendations & Next Steps

### Top 10 highest-priority fixes (before broadening past internal track)

1. **FEAT-1 — Implement the battery-optimization whitelist primer.** Add a contextual, dismissible prompt
   after the activity-recognition grant, gated on `PowerManager.isIgnoringBatteryOptimizations()`, firing
   `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. This is the GDD's named mitigation for its top risk and
   the single biggest reliability gap for the walking-gated core loop.
2. **REL-3 / #193 — Surface no-sensor state.** Expose `hasSystemFeature(FEATURE_SENSOR_STEP_COUNTER)` to
   the UI; on onboarding + Home, show a clear message and steer to the Health Connect / Activity-Minute
   path when the hardware counter is absent.
3. **NET-2 — Drive purchase reconciliation off a non-Store trigger.** Call `reconcilePendingPurchases()`
   from `MainActivity.onResume` and/or piggy-back the 15-min `StepSyncWorker`, so an unacknowledged
   entitlement isn't auto-refunded by Google before the user happens to re-open the Store.
4. **DATA-1 — Make premium-currency spend+grant atomic.** Wrap `OpenCardPack` / `UnlockUltimateWeapon` /
   `UpgradeUltimateWeapon` spend+grant in a single cross-DAO `@Transaction` (mirror
   `purchaseUpgradeAtomic` / `claimMilestoneAtomic`).
5. **STATE-1 — Stop losing the in-flight battle round on rotation.** Lock the battle screen orientation
   (or add `configChanges`) and/or hoist the pure-domain `Simulation` into `BattleViewModel` as the
   durable owner; add a rotation regression test to `BattleSurfaceLifecycleTest`.
6. **NET-1 — Surface offline IAP errors.** Capture `PurchaseResult` in the three `StoreViewModel.purchase*`
   methods and route Error/PENDING into `_userMessage` (mirror `CardsViewModel.watchFreePackAd`).
7. **CICD-1 + DEP-1 — Close the supply-chain gaps.** Add `gradle/actions/wrapper-validation` as the first
   CI/release build step; generate and commit `gradle/verification-metadata.xml`; add
   `distributionSha256Sum` to the wrapper.
8. **STATE-2 / #195 + TEST-1 — Fix and test missions day-rollover.** Drive the date through a
   `MutableStateFlow<String>` + `flatMapLatest` (mirror Home/Stats) and add a `MissionsViewModelTest` case
   that advances the clock past midnight.
9. **PRIV-1 + PRIV-2 + PRIV-4 — Privacy compliance.** Add the four AdMob categories (incl. approximate
   location) to the public policy; add a "Privacy Policy" row to Settings; set a conservative
   `RequestConfiguration` (`maxAdContentRating`, child-directed/under-age treatment) before the first ad
   request. (Plus complete the manual Play Console Data-Safety declaration, #192.)
10. **NET-3 + DATA-4 + REL-10 — Step-recovery + diagnostics hygiene.** Credit recovered HC gaps against
    minutes-elapsed × cap (not a single rate-limit window); add `onboarding_prefs`/`haptics_prefs` to
    `DataDeletionManager.PREFS_NAMES`; replace the 4 silent `catch(_:Exception){}` blocks in
    `DailyStepManager` with `CrashBreadcrumbStore`/`Log.w` so recurring failures are observable.

### Sequencing

- **Before public launch:** items 1–10 above, plus `CICD-3` (schema-drift gate misses new files),
  `TEST-2/3/4` (migration chain, concurrent economy, Compose UI), and `ARCH-2` (harden `DomainPurityTest`
  — a one-line change that surfaces the real boundary violations for later paydown).
- **Architecture paydown (no launch gate):** introduce the missing repository ports and remove raw-DAO/
  `AppDatabase` injections (ARCH-1/3/4), continue the ADR-0012 GameEngine extraction (ARCH-7/CQ-1).
- **Documentation reconciliation (cheap, do alongside):** fix the migration-floor claim in
  `database-schema.md`, the wrong instrumented-test command in `tech.md`/README, the `CLAUDE.md`
  `data/diagnostics/` omission, the README test count (1010→1069), and trim `STATE.md` back toward one
  page. Reconcile the #194 GitHub status (CLOSED with no referencing commit).
- **Documented deferrals (track, don't gate v1.0):** all i18n/l10n (ADR-0014), all accessibility (Plan
  24, post-v1.0), Play Billing v8→v9 migration planning.

### What's working well (do not regress)

The atomic guarded-deduct economy pattern, the pure-domain `Simulation` extraction, the documented
acyclic lock order on the game loop, the `#190` crash-breadcrumb + loop guard, the fail-closed release
license-key guard, the allowlist-validated deep links, the SHA-pinned CI actions, and the extensive
committed memory spine are genuine strengths. Most of this audit's "high" claims were *refuted* precisely
because these patterns already hold — that is the project's quality showing through.

---

*No production code, tests, or configuration were modified during this review. This report is the sole
artifact.*
