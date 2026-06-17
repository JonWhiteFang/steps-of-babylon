# Steps of Babylon — Complete App Review

**Date:** 2026-06-17 · **Reviewed at:** HEAD `c1686c8` (main) · **Build under review:** v1.0.8 (versionCode 24), live on the Play Console **internal** track.

**Method.** Code-grounded, adversarially-verified audit. The orchestrator first mapped the repository and independently verified the security-, persistence-, and release-critical surfaces. A 17-dimension review workflow then fanned out — one specialist reviewer per dimension, each required to cite `file:line` from the *actual code* (not docs) — and every material (Critical/High/Medium) finding was handed to an independent skeptic instructed to **refute it by default**; only `confirmed`/`partial` findings survive here. A completeness critic then swept the whole review for cross-cutting gaps, two of which the orchestrator re-verified by hand and promoted to findings (TIME-1, NOTIF-1). Total: 51 agents, ~3.1M tokens, 826 tool-uses, plus direct orchestrator verification.

> **Scope note / what this review is NOT.** This is a static + structural audit. It did **not** run the app on a device, did not execute the test suite (the headline 1054 figure is a `@Test` count cross-checked against `grep`, not a fresh run), and could not inspect the live Play Console Data Safety form or the AdMob child-treatment configuration (flagged where relevant as "needs external verification"). No code was changed; this document is the only artifact produced.

---

## 1. Executive Summary

Steps of Babylon is a genuinely impressive solo project. The core conceit — an idle tower-defense game whose entire progression is gated by real-world walking — is fully realized end-to-end, the engineering discipline is far above typical hobby Android work, and the documentation/memory spine is exceptional. The codebase is mature, heavily tested at the domain layer (1054 JVM tests), security-conscious (SQLCipher + Android Keystore, atomic guarded economy, client-side purchase verification), and shipped through a sophisticated CI/release pipeline. It already runs live on an internal track.

The honest gaps cluster in five places, none of which block *internal* testing but several of which should be closed before a **public** release:

1. **No crash visibility + an unguarded game-loop thread.** There is no global uncaught-exception handler and no crash reporting of any kind, and the `SurfaceView` game loop runs `update()`/`render()` with no `try/catch`. A single runtime exception in the 1209-line `GameEngine` silently kills the process — invisibly, on a judgment-gated track. This compounds two confirmed reachable game-loop races (`EffectEngine`, `uwStates`).
2. **The entire Compose UI / navigation / service layer is structurally untested.** Zero Compose UI tests, zero tests for the foreground `StepCounterService` and `BootReceiver` — the exact components a step-gated game depends on. The recently-shipped #187 "Settings won't scroll" bug is precisely the class this gap lets through.
3. **Time-gated mechanics trust the unguarded device clock.** Labs research completion and season-pass expiry resolve against `System.currentTimeMillis()` with no monotonic cross-check or rollback detector — the "real-time gated progression" pillar is trivially defeatable by changing the device clock. *No review dimension owned this; the orchestrator verified it.*
4. **Privacy-policy / Data-Safety accuracy.** The in-app and hosted privacy policies say data is "never uploaded" / frame AdMob as a "future" integration, but the live release ships AdMob + UMP and collects the advertising ID. This is a Play-compliance accuracy defect, not a code vulnerability — but it must be fixed before any public-track promotion.
5. **An unfinished architecture seam.** A whole feature family (missions / daily-login / milestones / weekly-challenge / boss-PS) bypasses the repository pattern that the other 8 features use, inverting the documented `presentation → domain ← data` dependency rule.

| Score | Rating | Notes |
|---|---|---|
| **Overall health** | **7.5 / 10** | Strong core, mature engineering, a handful of pre-public gaps. |
| **Product readiness** | **7 / 10** | Core loop fully playable; missing error states + first-run continuity. |
| **Technical architecture** | **7 / 10** | Battle-sim extraction is exemplary; one feature family bypasses the layering. |
| **UX** | **7 / 10** | Onboarding is strong; no error states, weak post-onboarding orientation. |
| **Security / privacy** | **7.5 / 10** | Security 8.5 (excellent); privacy 7 (policy-accuracy + Data-Safety gaps). |
| **Testing** | **7 / 10** | Domain coverage outstanding; UI/service/integration unguarded. |

**Readiness rating: Public beta (for the *internal/closed* track: ready now; for a *public production* launch: not yet).** The game is well past prototype and is correctly positioned on the internal track. To promote to closed/public it needs the privacy-policy fix (compliance), the crash handler + game-loop guard (so the soak phase produces signal), and the two verified game-loop race fixes.

**Biggest strength:** the pure-domain battle-simulation extraction (ADR-0012) + the defect-driven concurrency/economy regression suite — this is the architectural backbone and it is genuinely well done.

**Biggest weakness:** observability — no crash reporting + an unguarded render loop means real-world stability is currently unmeasurable.

**Biggest hidden risk:** clock-tampering defeats the time-gated progression (Labs) and login-streak economy, and *no one was looking at the time axis* of anti-cheat (it sits between the Security and anti-cheat concerns and fell through).

**Highest-leverage next task:** add a `Thread.setDefaultUncaughtExceptionHandler` (with an on-disk crash breadcrumb) **and** wrap the per-tick `update()/render()` in `try/catch` — together ~Small effort, and they convert "silent invisible process death" into "logged, recoverable glitch with a signal to gate promotion on."

**Continue building, or stabilise first?** **Continue — the foundation is sound and worth building on.** Do *not* embark on a large refactor. Spend one focused stabilisation pass on the "Must fix before public release" list in §18 (crash handler, game-loop guard, two race fixes, privacy-policy accuracy, a thin Compose/service smoke-test layer), then resume feature work. The architecture seam (§5) can be closed opportunistically; it is debt, not a blocker.

---

## 2. Repository Map

**Stack.** Kotlin 2.3.0 (JVM 17) · AGP 9.0.1 · Gradle 9.5.1 (Kotlin DSL, version catalog) · Jetpack Compose (BOM 2026.02.00) for menus + a custom `SurfaceView`/`GameLoopThread` battle renderer · Hilt 2.59.2 DI (KSP, not kapt) · Room 2.8.4 + SQLCipher 4.16.0 (encrypted, offline-first) · WorkManager 2.11.0 + a foreground service · Android Sensor `TYPE_STEP_COUNTER` + Health Connect 1.1.0 · Play Billing 8.3.0 · Google Mobile Ads 25.3.0 + UMP 4.0.0. minSdk 34 / target+compile 36. Package `com.whitefang.stepsofbabylon`.

**Size.** 279 main Kotlin files (~20.7k LOC); 155 JVM test files (~18.5k LOC) holding **1054** `@Test` methods; 9 instrumented tests. 13 ViewModels, 39 use-case files, 13 DAOs, 13 `@Entity`, Room schema v12 (6 migrations, schemas exported `1.json`–`12.json`).

**Modules (`settings.gradle.kts`).** `:app` (shipped) + `:baselineprofile` + `:macrobenchmark` (both `com.android.test` dev-tooling, never shipped).

**Architecture (Clean / MVVM).**
```
app/src/main/java/com/whitefang/stepsofbabylon/
├── data/         # Android-dependent: local (Room+SQLCipher), repository impls, sensor, healthconnect,
│                 #   anticheat, onboarding, billing (Play v8), ads (AdMob+UMP), time
├── domain/       # Pure Kotlin (zero Android imports, machine-enforced): model, repository (interfaces),
│                 #   usecase (39), battle/engine (Simulation core), battle/entity, time
├── presentation/ # ViewModels + Compose screens + SurfaceView battle renderer (engine/effects/biome/ui)
├── di/           # Hilt modules: Database, Repository, Step, HealthConnect, Ad, Billing, Time, CoroutineScope
└── service/      # Foreground step service, WorkManager workers, BootReceiver, notifications, widget
```

**Entry points.** `StepsOfBabylonApp` (Application; loads SQLCipher, schedules WorkManager) → `MainActivity` (single-Activity, Compose NavHost; chooses start destination from a synchronous onboarding flag). Largest files: `GameEngine.kt` (1209), `BattleViewModel.kt` (701), `MainActivity.kt` (432), `BillingManagerImpl.kt` (428), `DailyStepManager.kt` (329).

**Persistence.** Room is the single source of truth; SQLCipher passphrase generated via `SecureRandom`, wrapped by an Android-Keystore AES-256/GCM key, stored in `SharedPreferences`; decrypt-fail → DB wipe recovery (`DatabaseKeyManager.kt`).

**Build/test.** `./gradlew testDebugUnitTest` (JVM) · `./gradlew :app:connectedDebugAndroidTest` (instrumented, API 34+) · `./gradlew assembleDebug`/`bundleRelease`. Non-TTY wrapper `run-gradle.sh` (gitignored). Dependencies in `gradle/libs.versions.toml`.

**External integrations.** Play Billing (IAP), AdMob + UMP (rewarded ads + consent), Health Connect (step cross-validation). **No server backend** — fully offline single-player.

**Docs/tests/CI present.** Heavy doc spine (`CLAUDE.md`, `docs/agent/{START_HERE,STATE,CONSTRAINTS,RUN_LOG}.md`, 24 ADRs, steering docs, plans, two prior external reviews). CI: `ci.yml` (lint+unit+assembleDebug+benchmark-type-check+schema-drift), `instrumented.yml` (emulator lane, blocking on PR + nightly), `release.yml` (`v*` tag → signed AAB → Play internal), `dependency-submission.yml`. SHA-pinned actions + Dependabot.

---

## 3. Current Product Assessment

**What it is.** An Android idle/tower-defense game where physical walking is the only source of the primary currency (**Steps**). Players walk → earn Steps → spend them in the **Workshop** (permanent upgrades) and **Labs** (time-gated research) → fight wave-based **Battles** against mythic enemies across 10 difficulty tiers and 5 biomes, earning temporary in-round **Cash**, plus permanent **Gems** and **Power Stones** for Ultimate Weapons and Cards. The pitch — "Every Step Builds the Tower" — fuses fitness motivation with idle-game progression.

**Problem it solves.** Fitness motivation through game progression: it gives walking an extrinsic, compounding reward loop, for players who want a reason to move.

**What exists (and is usable today).** The full core loop is real and playable end-to-end: step tracking (sensor + Health Connect cross-validation + anti-cheat), Workshop/Labs/Cards/Ultimate-Weapons, tier progression, biome transitions, walking encounters (supply drops), milestones, daily missions, weekly challenges, login streaks, stats/history. Monetization is *real, not stubbed* — Play Billing v8 (gem packs, ad removal, season pass) and AdMob rewarded ads with UMP consent are wired and device-verified. Audio ships (9 real synthesized `.ogg` SFX + two BGM tracks via `SoundManager`/`MusicManager`) — contradicting a stale STATE.md note. First-launch onboarding (4-slide carousel + permission primer + replay) is in place.

**Partially implemented.** Cosmetics: of 11 seeded items only 2 ziggurat skins are purchasable; the rest are "Coming Soon," and 4 projectile/enemy skins have *no render path at all* (dead data). Onboarding has shipped its tutorial slice but its retention scope (D2/D7 nudges, projected-reward estimates) is still open (#24).

**Planned but absent (by deliberate, documented deferral).** Cloud save / backup (#36), telemetry/analytics (#23), i18n beyond English (#34), color-blind modes + rest-day encouragement (GDD §17), deterministic replay testing (#25). The Celestial Gate biome (tiers 11+) is `isComingSoon` and unreachable (tiers cap at 10).

**Assumptions made.** That the build's behavioural claims hold without a device run (the audit is static); that the internal-track promotion gate (`plan-FORWARD.md` A–G) reflects the real launch process; that the Play Console Data Safety form currently mirrors the (inaccurate) privacy policy text — which would make it a live compliance risk, but this could not be verified from the repo.

---

## 4. Feature Completeness Review

The implemented game is a remarkably faithful realization of the GDD. The design's core nouns match the domain enums almost exactly: all 24 upgrade types (`UpgradeType.kt:18-45`, with GDD-matching cost/scaling/caps at `:51-78`), 6 Ultimate Weapons, 9 Cards, 6 enemy types (`EnemyType.kt:18-23`), 12 Lab research types (`ResearchType.kt:35-72`), 10 tiers with exact cash multipliers (`TierConfig.kt`), 6 milestones, 6 daily-mission types. The Power-Stone economy is fully wired (weekly thresholds 50k=10/75k=20/100k=35 PS at `TrackWeeklyChallenge.kt:18`; daily 1-PS login at `TrackDailyLogin.kt:26-28`).

**The core loop is fully playable end-to-end** and gameplay-critical upgrades that could easily be declared-but-dead are actually wired into the simulation (RECOVERY_PACKAGES, FREE_UPGRADES flow into `GameEngine`/`SimulationMath`). **Monetization is real**: GEM packs, AD_REMOVAL (gates the reward-ad UI at `PostRoundOverlay.kt:126`, `CardsScreen.kt:98`), and SEASON_PASS (`BillingManagerImpl.kt:253-280`, granting daily bonus gems + a daily free Lab rush). **Audio is delivered** (the "placeholder sine tones" doc claim is stale).

**Confirmed gaps a player can hit today:**

- **Cosmetics are mostly non-functional (FEAT-1, Medium, confirmed).** Of 11 seed cosmetics (`CosmeticRepositoryImpl.kt:142-170`), only `zig_jade`/`zig_obsidian` are purchasable (`StoreScreen.kt:238`); the rest are "Coming Soon." Critically, only `ZIGGURAT_SKIN` has a render path (`GameEngine.kt:338`) — the 4 seeded projectile/enemy skins (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) have *no rendering wiring whatsoever* and `overrideColors` is only ever populated for ziggurat ids, so they could never apply even if owned. They're un-buyable today but are a live trap if the enable-list is ever widened without adding render wiring. GDD §13's "projectile effects, enemy skins" category is data-present but unusable.
- **Two GDD §17 accessibility promises are absent (FEAT-2, Low after verify).** Color-blind modes (three palettes) and rest-day encouragement return zero code matches. *Verified as a deliberate, tracked post-v1.0 deferral* (master-plan Plan 24), so the only true defect is a GDD-honesty nit (it states them as flat promises). Treat color-blind as pre-public-release; rest-day as nice-to-have.
- **Supply-drop fidelity (FEAT-3, Low).** 3 of 4 GDD triggers (`SupplyDropTrigger.kt` lacks the "step burst" trigger); the daily-milestone reward is simplified to 5 Gems vs the GDD's richer drop (`GenerateSupplyDrop.kt:29`).
- **Store full of "Coming Soon" (FEAT-5, Low).** Several catalogue items are permanently locked pending art — acceptable on internal, undercuts the monetization pillar for a public store (tracked as cosmetic debt).

AUTO_UPGRADE_AI is correctly *hidden*, not half-built — honest content. Cloud save (#36) and telemetry (#23) are absent by design. **None of these block the internal track**; the cosmetic dead-data and color-blind absence are the most defensible-to-fix before public release.

---

## 5. Architecture Review

**Score 6.5/10.** The headline "Clean Architecture (`presentation → domain ← data`)" story is **partly real, partly aspirational**.

**What holds up — and it's the backbone of the project.** The domain layer is genuinely Android-free (machine-enforced by `DomainPurityTest.kt:34-49`, cross-checked clean). The simulation/render split (ADR-0012) is the strongest piece of the codebase: `domain/battle/engine/Simulation.kt:151-253` owns cash economy, entity-tick, collision geometry, and UW timers as pure testable Kotlin; `EntityProtocol.kt:9-25` is a clean Canvas-free seam; `CollisionSystem` is a thin adapter. DI is conventional and correct (`RepositoryModule.kt` `@Binds` 8 impls; `TimeModule`, a well-documented `CoroutineScopeModule`). No `data → presentation` imports exist.

**The real problem: one feature family bypasses the architecture entirely.** The missions/daily-login/milestone/weekly-challenge/boss-PS family was wired with no repository seam, producing three confirmed invariant violations against `CONSTRAINTS.md:4-7`:

- **ARCH-1 (domain → data inversion; Low after verify).** 9 of 39 use cases import concrete data types — Room DAOs and even a Room `@Entity` constructed directly (`GenerateDailyMissions.kt:20` builds `DailyMissionEntity`). The skeptic confirmed the structural violation but deflated severity: the DAOs are Kotlin `interface`s and all 9 use cases *do* have JVM unit tests with fakes, and `DomainPurityTest` only forbids `android.*` imports (still passes correctly), so the harm is invariant-drift, not the claimed testability/purity loss.
- **ARCH-2 (presentation → data; Medium, confirmed).** 6 ViewModels inject DAOs/`AppDatabase` directly (`BattleViewModel`, `MissionsViewModel`, `HomeViewModel`, `CurrencyDashboardViewModel`, `LabsViewModel`, `WorkshopViewModel`), and `CurrencyDashboardViewModel.kt:72` reads a raw `WeeklyChallengeEntity` (a persistence type leaking unmediated into the UI). A schema/column change ripples straight into presentation.
- **ARCH-3 (cyclic data↔domain coupling; Medium, confirmed).** `DailyStepManager.kt:19-21` imports `domain.usecase.*` while those use cases import `data.local.*` back — a package-level cycle hidden only by the single Gradle module. It blocks ever extracting `domain` into its own module (which would make `DomainPurityTest` build-enforced).

The fix for all three is the same mechanical move: introduce repository interfaces for the engagement DAOs, map entities at the data boundary. The drift is undocumented, so anyone trusting `docs/architecture.md` assumes a purity that isn't there for this family.

**God-object watch (not blocking).** `GameEngine.kt` (1209 LOC, ~40% KDoc, the loop core already extracted to `Simulation`) and `BattleViewModel.kt` (701 LOC, 16 deps) are the risk concentrations — acknowledged debt (ADR-0012 chose partial extraction), the obvious refactor targets if the engagement seam is ever closed.

---

## 6. Code Quality Review

**Score 8/10.** Far better than typical hobby Android: small single-purpose functions, consistent intention-revealing naming, and disciplined async hygiene — **0 `TODO`/`FIXME`, 0 `GlobalScope`, 0 `runBlocking` in main**.

**The "big files" are mostly justified.** `GameEngine.kt` is large because the domain is, and the loop core is already extracted; its 32 functions are each one responsibility. The one function worth decomposing is `BattleViewModel.runEndRoundPersistence` (`:300`, ~85 lines nesting `runCatching{ runInTransaction{ five per-write runCatching } }`) — correct and well-commented but hard to follow at a glance (CQ-2).

**Null safety is genuinely good.** All 8 `!!` sites are defensively guarded (`peekFirst()!!` behind `isNotEmpty()`; `minWithOrNull(...)!!` behind an `isEmpty()` early return; a redundant `getString(k,"")!!` inside try/catch). The mildest smell is `LabRepositoryImpl.kt:27-28` relying on a DAO query's non-null guarantee (CQ-4, Low).

**Real weaknesses — duplication and missing tooling.** The `AdResult` cancelled/error copy is duplicated 4× (`BattleViewModel.kt:660-687`, `CardsViewModel.kt:135-137`, CQ-1). `MainActivity.kt:268` reimplements reduced-motion detection inline while calling the canonical `ReducedMotionCheck.isReducedMotionEnabled` helper 28 lines later (CQ-3). There is **no detekt/ktlint** (CQ-5); the only static lever is Android lint's `HardcodedText` (XML-only, misses the ~108 Compose literals). So complexity/duplication drift is caught only by human review. Magic numbers are mostly fine (gameplay constants live in `SimulationMath`/`TierConfig`); the exceptions are one-off render-tuning literals in `GameEngine`, acceptable. Doc drift is a code-quality signal too: README says "1010 JVM tests" vs actual 1054 (CQ-6).

---

## 7. UX and UI Review

**Score 7/10.** The menu UX has clearly matured past the "prototype" verdict of the prior look-and-feel review (`docs/external-reviews/2026-06-12-…`): bottom nav hidden during onboarding/battle (`MainActivity.kt:239-243`), a shared back affordance for the 8 push screens (`SobTopAppBar.kt`), haptics wired through, claim celebrations on mission/milestone claims. **Onboarding is genuinely strong**: a 4-slide carousel teaches walk→spend→battle, the final slide owns the permission ask with three explicit states (granted / not-yet-asked / permanently-denied recovery via "Open Settings"), and completion is persisted *before* navigation with a double-tap latch.

**The most material gap — no error state anywhere (UX-1, Medium, confirmed).** All 11 data screens use `if (state.isLoading) { LoadingBox(); return }`, every `UiState` carries `isLoading` but **no error field**, and `isLoading` flips false *only inside the `combine` success branch* (`HomeViewModel.kt:115`). If any source Room/SQLCipher flow throws, `combine` cancels and the screen spins **forever** with no message or retry — `stateIn` retains the initial `isLoading=true` value. Low-probability for an offline DB, but zero graceful degradation. A single shared `ScreenStateHost(isLoading, error, retry)` would cover all 11 screens.

**Weak first-run-to-shell continuity (UX-2, Medium, confirmed).** After onboarding, a fresh player lands on Home showing "0 steps," zeroed currencies, and a prominent gold un-gated BATTLE button (`HomeScreen.kt:108-159`) with no nudge to walk first — the walk-first lesson lives only in the now-dismissed carousel. A one-time dismissible "Go for a walk to earn your first Steps" empty-state banner (when `stepBalance==0 && todaySteps==0`) would close it (Small). This is the open #24 retention scope.

**Inconsistencies / polish residue.** `EmptyState` exists but is used in only 2 screens (Cards, Workshop) — Weapons/Stats/Supplies-history have none. Workshop tabs render raw enum names "ATTACK"/"DEFENSE"/"UTILITY" instead of title-case despite a `toDisplayName()` helper existing (`WorkshopScreen.kt:73`). Help is an unscannable wall of text with no ToC. No manifest portrait-lock (rotation behaviour unverified). The core loop, 5-tab nav model, motivation hooks (streak, weekly challenge, daily missions with midnight countdown), and reward feedback are solid and shippable for internal/closed.

---

## 8. Accessibility Review

**Score 7/10.** Markedly more a11y-aware than typical for a v1.0 that only *aspires* to post-launch accessibility. The work is real: a dedicated contrast-token layer (`Color.kt:18-33`) with a documented WCAG fix for lapis-as-text (`LapisLazuli` 1.45:1 → `LapisLight` 5.29:1), reduced-motion support wired through both Compose nav and the canvas renderer, decorative icons correctly using `contentDescription = null`, the onboarding emoji using `clearAndSetSemantics{}`, page-dots carrying a single "Page X of N" label, and icon-only battle controls getting parent `semantics`. All 22 audited `contentDescription = null` sites are legitimately decorative.

**Confirmed gaps:**

- **Primary-button contrast fails AA-normal (A11Y-1, Medium, confirmed).** `onPrimary` = DeepBronze on Gold computes to **4.19:1** (`Theme.kt:16`); Material3 `Button` labels render at `labelLarge` 14sp SemiBold — not "large text," so the 4.5:1 threshold applies and fails. This affects **all ~30 primary CTAs**, including the buttons that override `containerColor` but inherit the default content color. One-line fix (darken `onPrimary` toward `#4A2618` → 5.99:1). `StatusDanger` (#FF7043, 3.38:1) and `RaritySand` (4.41:1) also fail but mostly appear on large/bold text.
- **Battle SurfaceView is invisible to TalkBack (A11Y-2, Medium, confirmed).** `GameSurfaceView.kt:14` has no `AccessibilityDelegate`, `importantForAccessibility`, or live region; enemies/ziggurat-health/wave-outcome are canvas-only pixels. Mitigated (engine auto-fires UWs, controls + post-round overlay are labelled, so a round is completable) but a blind player gets zero combat feedback. For a closed-beta a11y claim, add a polite Compose live-region node fed from `uiState` (not full canvas a11y).
- **Touch targets + uncontrolled HUD text (Low).** TierSelector chips are 44dp tall (`TierSelector.kt:61`, below 48dp); battle HUD text is `Color.White` over the biome sky with no scrim (the onboarding title correctly uses a 45%-black scrim — that pattern isn't applied to the HUD).

Positives: dynamic font scaling intact; in-round menu scrolls so large fonts won't clip. No color-blind mode (acceptable for v1.0 since status is shape-reinforced, but track it). The `onPrimary` borderline and the SurfaceView gap are the items to resolve before any public a11y claim.

---

## 9. Security Review

**Score 8.5/10 — the strongest dimension.** Controls are done correctly, not performatively. All claims verified against code, not docs.

- **Signing secrets — CONFIRMED clean (Low, local hygiene only).** `keystore.properties` exists in the working tree with **real** `storePassword`/`keyPassword`. It is gitignored (`.gitignore:23,25`) and **verified never committed**: `git ls-files keystore.properties` is empty, and a history scan for the literal password value (`git log -S`) returns nothing — every `storePassword=` hit in history is the env-var reference `$STORE_PASSWORD` inside `release.yml:51`. So this is **not a repo leak**; it is a plaintext-secret-on-disk concern (fine for a solo dev, worth noting if the workstation is shared/backed-up). `local.properties` similarly holds AdMob IDs locally, also gitignored.
- **DB encryption — well-built.** `DatabaseKeyManager` generates a 256-bit passphrase via `SecureRandom`, wraps it with an AES-256/GCM Android-Keystore key, stores only ciphertext+IV in private prefs, and wipes stale key+DB on decrypt failure to avoid a crash loop. Two minor defense-in-depth gaps (no `setUnlockedDeviceRequired`/StrongBox; passphrase `ByteArray` never zeroed) — acceptable, not exploitable without root.
- **Billing #124 — solid client-side defense.** `RealPurchaseVerifier.kt:46-71` does Google's prescribed `SHA1withRSA` receipt verification (the *required* format — not a chosen-weak algorithm) **plus** product+token binding (`:67-68`) so a signed cheap receipt can't be replayed for an expensive SKU. Blank key fail-opens for debug/CI; unparseable key fails closed; a release build with a blank key is hard-failed by a task-graph Gradle guard (`app/build.gradle.kts:154-181`). Both grant paths gate on verification before any wallet credit. Honestly scoped as bypassable on a fully-repackaged APK — correct for a local-only economy.
- **Exported components — bounded.** Four `exported="true"`: MainActivity (LAUNCHER), the static-text HealthConnect rationale activity, a permission-guarded `ViewPermissionUsageActivity` alias, and `StepWidgetProvider` (only `onUpdate`, reads own `MODE_PRIVATE` prefs, `FLAG_IMMUTABLE` PendingIntent). The `navigate_to` deep-link extra (`MainActivity.kt:189,423`) is reachable by any app but is **allowlisted** to argument-free in-app routes (`Screen.fromRoute(route)?.takeIf { it.route in argumentFreeRoutes }`, Onboarding deliberately excluded) and triggers only benign navigation.
- **Other controls.** Cleartext blocked (`network_security_config.xml`), `allowBackup=false`, no WebView, no `MODE_WORLD_*`, no secrets logged, R8 + resource shrink with reasonable keeps, and a **verified-working** guava CVE constraint (resolves `→ 33.6.0-android`, see §15).

**The one accuracy bug** (also Privacy): the in-app policy asserts data is "never uploaded" while the app ships AdMob/UMP — a compliance inconsistency, not a code vulnerability (SEC-1 / PRIV-1, see §10).

> **Cross-cutting finding the dimension fan-out missed — orchestrator-verified.** **TIME-1 (clock-tamper / time-integrity; High).** Every time-gated mechanic resolves against the *unguarded device wall-clock*: Labs research completion (`CheckResearchCompletion.kt:10`, `CompleteResearch.kt:19` default `now = System.currentTimeMillis()`), season-pass expiry (`TrackDailyLogin.kt:46`), and the daily-login streak (keyed on the device `LocalDate` string). Only 2 of the engagement use cases inject the `TimeProvider` seam; 6 call `System.currentTimeMillis()` raw, and there is **no** `elapsedRealtime()` monotonic cross-check or persisted last-seen-time rollback detector anywhere in `app/src/main`. A player can set the clock forward to instantly finish Labs research (the *real-time-gated progression* pillar) or backward to farm login rewards/streaks. The Steps anti-cheat (rate limit, velocity, HC cross-validation) is strong on the *step* axis but **nothing guards the time axis** — it fell between the Security and anti-cheat concerns. Reachable, and undermines a core design pillar. Fix (Medium): persist a monotonic + wall-clock pair, detect backward jumps, and gate research completion on `max(elapsedRealtime delta, wall-clock delta)`-style reasoning, or accept it explicitly as out-of-scope for an offline single-player game and document the decision.

Distinguishing confidence: SEC-1/PRIV-1 and TIME-1 are **confirmed**. The Data-Safety form mismatch (§10) is **needs-verification** (external artifact).

---

## 10. Privacy and Data Handling Review

**Score 7/10.** Architecturally privacy-favourable — offline-first, **no server backend**, SQLCipher-encrypted, **no analytics/telemetry SDK** (#23 confirmed absent), strong data minimization (grep for location/contacts/camera/ANDROID_ID returns nothing), `allowBackup="false"` correctly set, and UMP consent before any ad request. But for a release *already live and collecting data*, there are real gaps.

- **PRIV-1 / SEC-1 — policy understates live data collection (High, confirmed).** The hosted policy (`docs/release/privacy-policy.md:30-35`) frames AdMob + Billing as *"Future versions … may integrate,"* but both are live in v1.0.8 (release build sets `USE_REAL_ADS=true`, real ad-unit IDs, UMP prefetch; `BillingManagerImpl` is the sole binding). The release merged manifest auto-merges `com.google.android.gms.permission.AD_ID` + three `ACCESS_ADSERVICES_*` Privacy-Sandbox permissions from the AdMob SDK (none declared in the source manifest), and the policy never mentions the **advertising ID** — a GDPR personal identifier. The in-app HealthConnect rationale goes further: *"Your data is never uploaded … not shared with third parties"* (`HealthConnectPermissionActivity.kt:44-46`) — factually false for release. This is a transparency-principle failure and a Play **Data Safety** mismatch risk on an app already distributed. Fix (Small text edit): rewrite present-tense to disclose AdMob (with advertising ID) + Billing, add an explicit Advertising-ID entry, and align the Data Safety form. *Severity reconciled: Privacy's High is the defensible rating for a live data-collecting app facing Play enforcement.*
- **PRIV-2 — "Delete All Data" leaves two prefs files (Low after verify).** `DataDeletionManager.PREFS_NAMES` enumerates 11 prefs files but omits `haptics_prefs` and `onboarding_prefs` (both confirmed live). The skeptic deflated severity: the policy's "we retain no copies" line attaches to the *OS-level Clear Data* path (which wipes everything), and the in-app dialog only promises to wipe *game state* — the residue is two low-sensitivity booleans. Still a real completeness bug (the manual list will drift); fix by enumerating the `shared_prefs` dir + a test asserting it matches all `getSharedPreferences` call sites.
- **PRIV-3 — COPPA/children (refuted → Low, needs external verification).** The listing targets "Everyone/PEGI 3" while serving ad-ID-keyed AdMob; nothing in-repo evidences child-directed treatment flags (`RealRewardedAdAdapter` builds a plain `AdRequest`). The skeptic refuted it as a code finding because the actual control lives in the **Play Console target-audience + AdMob families config**, not the repo — so this is a "verify the Console config" item, not a confirmed code defect.
- **Minor.** The HC rationale cites `support@whitefanggames.com` while the canonical policy/listing use `jonwhitefang@gmail.com` — a data-controller-contact inconsistency. No PII is logged (the ~53 Log sites use product IDs, tokens, step counts).

---

## 11. Performance Review

**Score 7.5/10.** Genuinely good for a solo v1.0; the perf work that landed is real. The fixed-timestep loop (`GameLoopThread.kt:34-81`) cleanly separates sim/render, drains an accumulator one tick at a time, and bounds catch-up via `SimulationMath.clampAccumulator` (`MAX_CATCHUP_TICKS=8`, unit-tested, #126). The render path is largely allocation-free: entities cache `Paint` at construction, `EnemyEntity` reuses one `Path`, the A31 render Paints are now cached fields, the `ParticlePool` is a fixed 200-slot ring with zero growth, and the A28 collision scratch buffers replaced three per-frame `filterIsInstance` allocations. Startup has a committed Baseline Profile + `profileinstaller`; R8 + resource shrinking are on; assets are tiny.

**Remaining hot-path concern — per-tick GC churn (PERF, Medium, not adversarially escalated).** `getAliveEnemies()` (`GameEngine.kt:869-875`) allocates a fresh `ArrayList` every call — once per orb update (up to 6), up to 4× per active UW, plus the auto-trigger gate ≈ **7–12 O(entities) allocations per tick × 60fps × up to 8 catch-up ticks**. The KDoc correctly notes the live re-derive is required for #125 correctness, but a reusable engine-owned scratch buffer (the A28 pattern) shared by the read-only consumers would eliminate most of it. Confidence medium — no device profile confirms the magnitude.

**A clearer fixable issue — PERF-1 (Low after verify).** `StepCounterService.kt:85` reads the full encrypted profile (`observeProfile().first()` → SQLCipher decrypt + full entity map) on **every** sensor delta, just to read `stepBalance`, even though the notification post is throttled to 30s. The skeptic confirmed the wasted-decrypt-per-batch but corrected the fix: the suggested `getStepBalance()` uses the *same* `SELECT *` decrypt (not a single-column query), so only the throttle-gating half helps; severity → Low (single-row table, infrequent batches).

**Battery.** Zero wake locks; sensor hardware-batched. One real gap: the periodic `StepSyncWorker` is scheduled with **no constraints** (`StepSyncScheduler.kt:14`) — adding `setRequiresBatteryNotLow` lets Doze defer HC sync at low battery for near-zero correctness cost. **Honest caveat:** the entire device-measured half of #26 (startup timings, jank, idle drain, OEM-kill behaviour) is deferred and unverified — the in-repo claims are structural, not measured.

---

## 12. Reliability and Error Handling Review

**Score 6.5/10.** The error-handling *fabric* is good; the missing *safety nets* are the real exposure.

- **REL-1 — no global crash handler / crash reporting (Medium after verify, confirmed).** `grep` for `setDefaultUncaughtExceptionHandler`/`Crashlytics`/`Sentry`/`ACRA` across `app/src/main` returns zero; `StepsOfBabylonApp.onCreate` only loads SQLCipher + schedules WorkManager. Production crashes are **invisible** to a developer running a judgment-gated promotion — no signal to gate on, no way to know if a release regressed stability. (Severity adjusted High→Medium: it's an observability gap, not itself a crash cause — but it makes everything below worse.)
- **REL-2 — unguarded game-loop thread (High, confirmed).** `GameLoopThread.run()` (`:47-60`) calls `engine.update()`/`engine.render()` with no `try/catch` (only `unlockCanvasAndPost` is guarded). Any `RuntimeException` in the 1209-line `GameEngine` propagates to the dedicated thread's default handler and — with no app-level handler (REL-1) — **silently kills the process** during the core-loop battle. Fix (Small): wrap the per-tick update/render, log + a crash breadcrumb, then skip-frame-and-continue or surface a "battle error" state.
- **REL-3 — no signal when the step sensor is absent (Medium, confirmed).** `StepSensorDataSource.kt:28-33` logs + `close()`s the flow when `TYPE_STEP_COUNTER` is null; `StepCounterService` keeps a foreground notification alive doing nothing, and `StepSyncWorker` no-ops. For a step-gated game, a no-sensor device (emulators, rare low-end hardware) is a silent dead-end with the notification implying it works. There is no `hasSystemFeature` check or `uses-feature` anywhere. Detect at first-launch and steer to Health Connect.
- **REL-5 — unguarded `BootReceiver.startForegroundService` (Low after verify).** No `try/catch` (`BootReceiver.kt:18`); the `health` FGS type from BOOT_COMPLETED is normally exempt from the Android 12+ restriction, so it likely never fires — but cheap defensive hardening given REL-1.
- **Confirmed RO-09 #6 debt.** BossKilled Power Stones are awarded on `viewModelScope` (`BattleViewModel.kt:440`, cancelled at `onCleared`) so a mid-round nav-away loses them, whereas StepReward correctly uses `applicationScope` (`:421`).

**What's solid:** HC readers return null/empty on failure (clean offline/permission-revoked degradation); `StepSyncWorker` wraps phases in `catch` + always returns `success()`; billing is well-built (atomic `grantOnceAtomic`, signature verify, reconciliation retry without re-credit); the decrypt-fail DB wipe prevents a crash-on-launch loop; only `fallbackToDestructiveMigrationOnDowngrade` (no silent destructive upgrade fallback). The two safety nets (REL-1 + REL-2) are the highest-leverage reliability fix in the whole report.

---

## 13. Testing Review

**Score 7/10.** Domain coverage is outstanding; the UI/service/integration layers are unguarded.

**Strengths.** 1054 JVM tests across 155 files (verified), ~1.78 assertions/test, **zero assertion-free tests**, excellent async hygiene (56 files use `runTest`, only 2 `runBlocking`). Breadth across all 39 use-cases, the pure `Simulation` core, and economy/balance is genuine. The fakes are high-quality — `FakePlayerRepository` deliberately mirrors the real guarded-deduct contract so it can't mask the #122 free-grant bug class. The concurrency tests are a standout: `DailyStepManagerConcurrencyTest` is *deterministic* (parks a thread inside the Mutex via a production seam to prove the 50k ceiling, #120), and `Migration11To12Test` tests the subtle no-resurrection case. This is mature, defect-driven testing.

**Confirmed gaps (in priority order):**

- **TEST-1 — no Compose UI tests at all (High, confirmed).** `grep` for `createComposeRule`/`onNodeWith*` returns nothing; no `ui-test` dependency in `androidTestImplementation`. The entire Compose layer + navigation + permission flows are tested only at ViewModel/Robolectric level or manually. The #187 "Settings won't scroll" bug (just shipped) is exactly this class — and its own fix commit admits "Compose-UI test rules don't run under Robolectric here." `SettingsViewModel` is also the one ViewModel of 13 with no test (Low — thin state-holder).
- **TEST-2 — service/receiver untested (Medium after verify).** `StepCounterService` (the foreground service step-counting depends on) and `BootReceiver` (reboot restart) have **zero** test references. The skeptic corrected two framing errors (the worker's catch-up *logic* IS covered via the real `StepSyncWorker.computeCatchUp`, and a `StepBalanceDisplayTest` exists), so the genuinely-uncovered surface is the thin Android glue (foreground promotion, START_STICKY, sensor-latch baseline, boot relaunch) — real but lower-risk than "total blackout."
- **TEST-3 — no coverage/mutation tooling (Medium, confirmed).** No JaCoCo/Kover/PITest anywhere; 1054 is a count, not a measured-coverage signal. Add Kover as an informational CI artifact (don't gate on a threshold for solo v1.0).
- **TEST-4 — migration coverage partial (Medium, confirmed).** Only `MIGRATION_11_12` of six (7→12) has a test; the two data-transforming recreate-table dances (9→10 UW-level split, 10→11 card dedup) are unverified. Note: the first public release shipped at v11, so only 11→12 runs for live users — but a `MigrationTestHelper.runMigrationsAndValidate` pass against the exported schemas is cheap insurance.
- **TEST-5 — golden-replay structurally blocked (Medium, confirmed).** The `Simulation` core is RNG-free (good), but `WaveSpawner.kt:108,178` and `GameEngine.kt:1069,1189` call global `Random` directly, so deterministic full-battle replay (#25) needs an injectable seeded RNG first.

**Recommended test strategy (risk-prioritised).** (1) A thin `createAndroidComposeRule` smoke suite over the 4–6 stateful screens (Home, Workshop, Settings-scroll, Store buy enable/disable, onboarding permission primer) — closes the #187 class. (2) Robolectric `ServiceController`/`BroadcastReceiver` tests for `StepCounterService`/`BootReceiver`. (3) Kover in CI (informational). (4) Full migration-chain validation. (5) Inject a seeded RNG into the spawner to unlock golden-replay. **Refactor-safety verdict:** domain/economy/battle-sim — yes, confidently; Compose UI / nav / service / worker — no, currently unguarded.

---

## 14. Build, CI/CD, and Release Review

**Score 8/10 — materially better than typical solo Android.** Disciplined Gradle (root `apply false` pins versions once, `FAIL_ON_PROJECT_REPOS`, version catalog, benchmark modules cleanly isolated and never shipped). The **release-safety machinery is the best part**: the #124 fail-closed guard refuses to *produce* a release artifact with a blank license key (thoughtful regex excludes only benchmark variants), AdMob/license fall back to safe Google test IDs, `release.yml` fail-closes again on an empty secret, writes secrets line-by-line, `jarsigner -verify`s, uploads the R8 `mapping.txt`, and derives Play "what's new" from the annotated tag. CI runs unit+lint+assembleDebug+benchmark-type-check+a real schema-drift guard (`git diff --exit-code app/schemas`, all 12 schemas tracked). Actions SHA-pinned; Dependabot maintains both ecosystems; dependency-submission scoped to `releaseRuntimeClasspath` (a sophisticated touch). Branch protection on `main` requires both `build-and-test` and `connected` (instrumented) with `enforce_admins: true` (ADR-0018, 2026-06-16).

**Confirmed gaps:**

- **CICD-1 — Gradle wrapper integrity unverified (High, confirmed).** `gradle-wrapper.properties` has no `distributionSha256Sum` and no workflow uses `gradle/wrapper-validation`. The wrapper jar is checked in, so a swap needs a reviewable commit (some defense), but a lane that signs and publishes a production AAB through an otherwise SHA-pinned pipeline should validate it. Fix (Small): add `distributionSha256Sum` + the SHA-pinned wrapper-validation action as the first CI/release step.
- **CICD-2 — release lane skips lint + instrumented (Low after verify).** `release.yml:33-34` runs only `testDebugUnitTest` before `bundleRelease`. Branch protection on `main` mitigates the accidental-regression path (every main commit passed both gates), so the residual is the *deliberate-misuse* path: `release.yml` triggers on **any** `v*` tag and never verifies the tagged SHA is a green `main` commit. Add `lintRelease` to the guard, or gate on the SHA being green.
- **CICD-3 — no tag↔version consistency guard (Low after verify).** Nothing asserts `v1.0.8` == `versionName`, and `versionCode` is hand-edited with no reuse check (the very "v13 rejection" the comment cites). The skeptic corrected the impact: it can't ship the *wrong build* (the AAB always carries the committed values) — only waste a signed CI run on a Play rejection or attach slightly-wrong notes. Cheap `grep` guard recommended anyway.

**DX friction.** `run-gradle.sh` is gitignored yet load-bearing (recreated from a README snippet, not versioned). `gradle.properties` sets no caching/parallel/configuration-cache flags. The benchmark plugin is an alpha (acceptable — never ships). **Release readiness verdict: ready for the internal/closed track now**; close CICD-1 + the privacy-policy fix before public.

---

## 15. Dependency Review

**Score 8.5/10 — one of the strongest dimensions.** Versions centralized in `gradle/libs.versions.toml` (only inline version is a test-only `androidx-test-core`), refreshed by a verified Dependabot wave, weekly cadence on both Gradle + actions, all 5 Action usages SHA-pinned.

- **Guava CVE constraint — verified working.** A `dependencyInsight` resolve confirmed `31.x → 33.6.0-android` with the printed reason "Force transitive guava … clear CVE-2023-2976/CVE-2020-8908" — a real mitigation, not a hopeful comment.
- **Alpha/beta deps correctly quarantined.** `benchmark 1.5.0-alpha06` + `uiautomator 2.4.0-beta02` are consumed **only** by the two `com.android.test` modules and never enter the AAB (the shipped `profileinstaller` stays stable 1.4.1). Well-justified in-comment (stable 1.4.1 throws on AGP 9.0.1). Residual risk: an alpha breaking change could red the PR gate's benchmark type-check — bounded, nothing ships.
- **Bleeding-edge toolchain (the main latent risk).** AGP 9.0.1, Kotlin 2.3.0, KSP 2.3.9, Compose BOM 2026.02.00 are very new — it forced the alpha pin and several AGP-9 workarounds. Not a defect (CI green, tests pass) but the project lives on a frontier where toolchain regressions surface first.
- **Shipped SDKs current + pinned-exact:** Health Connect 1.1.0 (correctly off the alpha line, #33), Billing 8.3.0 (v7 sunsets 2026-08-31 — good), Ads 25.3.0, UMP 4.0.0, SQLCipher 4.16.0. No floating/dynamic versions anywhere.

Minor: `junit-platform-launcher` declared without a version (transitive alignment, test-only). No confirmed vulnerable shipped dependency was found.

---

## 16. Documentation Review

**Score 8/10.** An unusually disciplined doc/memory spine for a solo project, and most of it verifies cleanly against code: 13 ViewModels/DAOs/Entities, schema v12 (1–12 exported), 8 repository bindings, 12 `ResearchType` (one coming-soon), the deleted `Currency.kt` fully purged, the multi-module change documented — all match. `CLAUDE.md` and `STATE.md` carry the correct **1054** headline. The `/checkpoint` per-PR drift sweep is institutionalised, and navigability (START_HERE → STATE → plans) is clear. Exceptional for one person.

**Confirmed drift (audit-grade):**

- **DOC-1 — README stale by 44 tests / 3 merges.** `README.md:13,48` says "1010 JVM" vs actual 1054. Git history shows it was correct at PR #181, then #29/#26/#44 updated CLAUDE.md/STATE/CHANGELOG but never README — because the `/checkpoint` drift table flags README only for "build/run instruction" changes, while it *embeds* the test count. Fix: one table-row edit + the two numbers (DOC-3 is the root-cause process fix).
- **DOC-2 — STATE.md use-case count.** `STATE.md:450` says "36 use cases stable" but `domain/usecase/` has **39** (the #29 trio landed after the last sweep). Low impact but exactly the cached-prose count CLAUDE.md's own rules warn against.
- **Audio drift (FEAT-4).** `STATE.md:424` says "placeholder sine tones" but real `.ogg` assets ship — independently confirmed.

**Volume/maintainability (DOC-4, Medium-effort).** `docs/` is 6.1MB / ~61k lines (170 files); `RUN_LOG.md` 988KB, `CHANGELOG.md` 316KB, `STATE.md` 64KB (~160 fragile-zone lines despite its own "one page" instruction). The README lag is direct evidence the volume already outpaces the sweep, and STATE.md's fragile-zone narrative duplicates content in CLAUDE.md + ADRs. **Don't restructure wholesale** (over-engineering) — but tighten STATE.md toward one page (push fragile-zone detail into the ADRs it already references) and prefer "read the enum/code" over cached counts. The cost/benefit has tipped slightly into bloat for a solo v1.0.

---

## 17. Technical Debt Register

Surviving findings after adversarial verification. Severity shown as `original → corrected` where the skeptic adjusted it. "Conf" = reviewer confidence. **Effort:** S/M/L.

| ID | Title | Sev | Effort | Conf | Verdict |
|---|---|---|---|---|---|
| **REL-2** | Game-loop thread has no try/catch — sim crash kills the process | **High** | S | high | confirmed |
| **CONC-1** | `EffectEngine` lists mutated cross-thread, unsynchronized (boss/step-reward) | **High** | S | high | confirmed |
| **TIME-1** | Time-gated mechanics trust unguarded device clock (Labs, season pass, streak) | **High** | M | high | confirmed (orchestrator) |
| **PRIV-1/SEC-1** | Privacy policy says "never uploaded" but ships AdMob/UMP + ad ID | **High** | S | high | confirmed |
| **TEST-1** | No Compose UI test coverage (UI/nav/permission unguarded) | **High** | M | high | confirmed |
| **CICD-1** | Gradle wrapper integrity unvalidated in a publishing lane | **High** | S | high | confirmed |
| **REL-1** | No global uncaught-exception handler / crash reporting | High→**Med** | S | high | confirmed |
| **ARCH-2** | Presentation injects DAOs/`AppDatabase`; Room entity leaks to UI | **Med** | M | high | confirmed |
| **ARCH-3** | Cyclic data↔domain package coupling | **Med** | S | high | confirmed |
| **CONC-2** | `uwStates` structurally mutated off-loop-thread on replay | **Med** | S | high | confirmed |
| **STATE-1** | `MissionsViewModel` never re-subscribes the query on day-rollover | **Med** | S | high | confirmed |
| **DATA-1** | 4 of 5 migrations (incl. 2 data-transforms) have no upgrade-path test | **Med** | M | high | partial |
| **A11Y-1** | Primary button labels fail WCAG AA-normal (4.19:1, ~30 CTAs) | **Med** | S | high | confirmed |
| **A11Y-2** | Battle SurfaceView invisible to TalkBack | **Med** | M | high | confirmed |
| **REL-3** | No user signal when step-counter hardware absent | **Med** | M | high | confirmed |
| **NOTIF-1** | No quiet-hours/daily-cap on reminder & supply-drop notifications | **Med** | M | med | confirmed (orchestrator) |
| **TEST-2** | Foreground service + BootReceiver untested | High→**Med** | M | high | partial |
| **TEST-3** | No coverage/mutation tooling | **Med** | S | high | confirmed |
| **TEST-4** | Only 1 of 6 migrations tested; no full-chain validation | **Med** | M | high | confirmed |
| **TEST-5** | Golden-replay blocked by global RNG in render layer | **Med** | M | high | confirmed |
| **UX-1** | No error state anywhere — failed load spins forever | **Med** | M | high | confirmed |
| **UX-2** | No first-run guidance after onboarding (empty un-oriented Home) | **Med** | S | high | confirmed |
| **I18N-1** | 108 hardcoded Compose `Text` literals; no locale dirs | **Med** | L | high | confirmed |
| **FEAT-1** | Projectile/enemy-skin cosmetics seeded with no render path | **Med** | M | high | confirmed |
| **ARCH-1** | Domain → data dependency inversion (9 use cases) | Med→**Low** | M | high | partial |
| **DATA-2** | `claimMilestoneAtomic` uses read-then-write not guarded mark-first | Med→**Low** | S | med | partial |
| **PRIV-2** | "Delete All Data" leaves 2 prefs files | Med→**Low** | S | high | partial |
| **PERF-1** | Full encrypted-profile decrypt per sensor delta | Med→**Low** | S | high | partial |
| **REL-5** | `BootReceiver.startForegroundService` unguarded | Med→**Low** | S | med | partial |
| **CICD-2** | Release lane skips lint + instrumented | Med→**Low** | S | high | partial |
| **CICD-3** | No tag↔versionCode guard | Med→**Low** | S | high | partial |
| **I18N-2** | Destructive-dialog copy hardcoded English | Med→**Low** | S | high | partial |
| **FEAT-2** | GDD color-blind modes + rest-day absent (tracked deferral) | Med→**Low** | M | high | partial |
| **PRIV-3** | COPPA/ad-ID for an "Everyone" game | Med→**Low** | — | low | refuted (verify Console) |

Plus **63 Low-severity findings** (not adversarially verified) across the dimensions — perf micro-allocations, code-duplication, doc nits, polish residue. The most actionable Low items: CQ-1 (duplicated ad-result copy), CQ-3 (inline reduced-motion divergence), DOC-1/DOC-2/DOC-3 (doc drift + the process fix), the unconstrained `StepSyncWorker`, and the Workshop tab title-case.

---

## 18. Prioritised Remediation Plan

### 1 — Must fix before any **public** release (blocking)

| Item | Effort | Risk reduced | Files | Approach | Validation |
|---|---|---|---|---|---|
| **REL-2 + REL-1**: game-loop `try/catch` + global crash handler/breadcrumb | S | Eliminates silent battle-crash process death; creates stability signal | `GameLoopThread.kt`, `StepsOfBabylonApp.kt` | Wrap per-tick `update()/render()`, log + persist a breadcrumb, skip-frame or surface an error state; install `Thread.setDefaultUncaughtExceptionHandler` writing a next-launch-uploadable breadcrumb | A Robolectric/JVM test that throws inside a stubbed engine tick and asserts the loop survives + breadcrumb written |
| **CONC-1**: synchronize `EffectEngine` effect lists | S | Removes a reachable CME on every boss kill / step reward | `EffectEngine.kt`, `BattleViewModel.kt` | Guard `pendingEffects` add/drain/iterate with a private monitor (mirror `entitiesLock`), or a thread-safe queue drained on the loop thread | A concurrency stress test (mirror `GameEngineConcurrencyTest`) racing `addEffect` vs `update/render` over N iters |
| **PRIV-1/SEC-1**: privacy-policy + Data-Safety accuracy | S | Play-compliance rejection/takedown risk | `docs/release/privacy-policy.md`, `HealthConnectPermissionActivity.kt`, Play Console | Present-tense rewrite disclosing AdMob (advertising ID) + Billing; add an Advertising-ID entry; align the Data Safety form; unify the contact email | Re-read both policy surfaces; manual Console Data-Safety cross-check |
| **TEST-1 (thin slice)**: minimal Compose UI smoke suite | M | Closes the #187 class on the highest-traffic screens | `app/src/androidTest/…`, `app/build.gradle.kts` (add `ui-test-junit4`) | `createAndroidComposeRule` over Home/Workshop/Settings-scroll/Store-buy/onboarding-primer — not exhaustive | Suite runs green in the instrumented lane |
| **CICD-1**: validate Gradle wrapper | S | Build-host supply-chain integrity for the signing lane | `gradle/wrapper/gradle-wrapper.properties`, `ci.yml`, `release.yml` | Add `distributionSha256Sum` + SHA-pinned `gradle/wrapper-validation` as first step | CI passes; tamper a byte locally → fails |

### 2 — Should fix before beta (closed test)

- **TIME-1** (M): add monotonic+wall-clock rollback detection for Labs/season-pass/streak, **or** explicitly document the offline-single-player decision to accept it. Validation: a `FakeTimeProvider` test asserting a backward jump can't re-award.
- **CONC-2** (S): wrap `initUWs` clear+add in `synchronized(entitiesLock)`; snapshot `uwStates` for the polling read. Validation: replay-path stress test.
- **STATE-1** (S): `MissionsViewModel` → `MutableStateFlow(today)` + `flatMapLatest` (mirror Home/Stats). Validation: a day-rollover VM test.
- **REL-3** (M): detect `TYPE_STEP_COUNTER` absence at first-launch → message + Health Connect steer. Validation: instrumented test on a no-sensor emulator.
- **UX-1** (M): shared `ScreenStateHost(isLoading, error, retry)`; wrap each `combine` in `.catch { emit(error) }`. Validation: inject a throwing fake repo → assert the error+retry renders.
- **A11Y-1** (S): darken `onPrimary` to clear 4.5:1. Validation: recompute contrast; spot-check on device.
- **TEST-2/TEST-4** (M): Robolectric `ServiceController`/`BroadcastReceiver` tests + `MigrationTestHelper` full-chain pass.

### 3 — Important but not blocking

- **ARCH-2/ARCH-3** (M): introduce repository interfaces for the engagement family; map entities at the boundary (also fixes ARCH-1). Unblocks a future `domain` Gradle-module extraction.
- **UX-2** (S): one-time "go for a walk" empty-state on Home. **NOTIF-1** (M): quiet-hours + daily cap on reminder/supply-drop notifications (Play "disruptive notifications" + retention). **TEST-3** (S): Kover in CI (informational). **A11Y-2** (M): Compose live-region for battle transitions. **FEAT-1** (M): remove the dead projectile/enemy cosmetic rows (or wire rendering). **DATA-2** (S): switch `claimMilestoneAtomic` to guarded mark-first. **PERF** GC (M): scratch-buffer `getAliveEnemies` consumers.

### 4 — Nice-to-have

- Detekt/ktlint (CQ-5); CQ-1/CQ-3 duplication; `StepSyncWorker` `setRequiresBatteryNotLow`; Workshop tab title-case; `EmptyState` on Weapons/Stats; Help ToC; PERF-1 throttle-gate; CICD-2/CICD-3 guards; passphrase zeroing; doc-drift fixes (DOC-1/2/3) + STATE.md slimming (DOC-4).

### 5 — Longer-term architectural

- Extract `domain` into its own Gradle module (build-enforced purity) once ARCH-2/3 land. Inject a seeded RNG to unlock deterministic golden-replay (#25, TEST-5). Telemetry/analytics abstraction (#23). Cloud save / backup (#36) — the biggest retention/trust gap (an offline, encrypted, `allowBackup=false`, no-cloud game means a lost device = total permanent progress loss). Full i18n (#34, I18N-1) once a second locale is committed. Decompose `GameEngine`/`BattleViewModel`.

---

## 19. Suggested Next Milestones

- **M1 — Stabilise for public (the §18 Tier-1 list).** One focused pass: crash handler + game-loop guard, `EffectEngine`/`uwStates` race fixes, privacy-policy/Data-Safety accuracy, a thin Compose+service smoke-test layer, wrapper validation. Exit: crashes are visible, the two reachable battle races are closed, the policy is truthful, the #187 class is guarded.
- **M2 — Integrity & resilience.** TIME-1 clock-tamper decision, REL-3 no-sensor messaging, UX-1 error states, day-rollover fix, migration full-chain tests, `onPrimary` contrast. Exit: the core mechanic can't be trivially cheated and failure modes degrade gracefully.
- **M3 — Architecture seam + polish.** Repository interfaces for the engagement family (ARCH-1/2/3), notification quiet-hours, first-run nudge, cosmetic dead-data cleanup, Kover, doc slimming. Exit: layering is honest and build-enforceable; store/notifications are public-ready.
- **M4 — Retention foundations (post-launch).** Telemetry (#23), cloud save (#36), color-blind modes, i18n phase 2, golden-replay. Exit: the data + durability + inclusivity foundations for sustained growth.

---

## 20. Final Verdict

Steps of Babylon is a **well-built, genuinely shippable solo project** that is correctly positioned on its internal track. The engineering quality — the pure-domain simulation extraction, the atomic guarded economy, the defect-driven concurrency regression suite, the encrypted persistence, the disciplined release pipeline, and the exceptional documentation spine — is well above what the "hobby game" label would suggest. The core walk→spend→battle loop is real, complete, and playable, monetization is wired for real, and the game is honest about most of what it defers.

What stands between it and a confident *public* launch is a short, concentrated list, not a rewrite: it currently flies blind on stability (no crash reporting + an unguarded render loop, compounded by two reachable game-loop races), its entire UI/service layer is structurally untested, its time-gated progression trusts the device clock, and its shipping privacy policy contradicts the SDKs it actually ships. Every one of those is fixable in a single focused stabilisation pass, and most are Small.

**Recommendation: continue building on this codebase.** The foundation is sound and worth the investment. Do the §18 Tier-1 stabilisation pass first — it is high-leverage and low-effort — then resume feature work. Address the architecture seam opportunistically rather than as a stop-the-world refactor. The biggest strategic gap to keep on the roadmap is **cloud save**: for a game whose entire pitch is months of accumulated physical effort, a lost device today means total, unrecoverable progress loss — that, more than any code defect, is the long-term retention risk.

---

*Scores reflect the adversarially-verified finding set: 5 confirmed High (one corrected to Medium), 16 confirmed Medium, plus partial/refuted adjustments and 63 unverified Low items. Every material finding cites code the reviewer and an independent skeptic both opened. Two cross-cutting findings (TIME-1, NOTIF-1) were surfaced by the completeness critic and re-verified by the orchestrator. This document changed no code.*
