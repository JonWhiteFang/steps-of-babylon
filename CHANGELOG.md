# Changelog

All notable changes to Steps of Babylon are documented here.

## [Unreleased]

### Security — scope dependency submission to runtime classpaths (clears build-only Dependabot noise) (2026-06-10)

- The `dependency-submission.yml` workflow (Plan 32 / ADR-0018) submitted the **entire** Gradle dependency graph, including the buildscript/plugin classpath. That dragged the Google-API-client stack behind the Play Publisher plugin + AGP/bundletool (netty ×19, protobuf-java, bouncycastle, jose4j, jdom2, httpclient, commons-lang3) into GitHub's security graph as **30 Dependabot alerts (14 high / 3 low / 13 medium) attributed to `settings.gradle.kts`** — none of which ship in the AAB (build-machine-only). Verified 2026-06-10 against `:app` `releaseRuntimeClasspath`: of all flagged packages, **only guava ships** (transitive, `31.1-android`, 2 medium alerts).
- Fix: set `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS: ".*RuntimeClasspath"` on the `gradle/actions/dependency-submission` step, restricting the submitted graph to the app's release/debug/test runtime classpaths and excluding the buildscript `classpath`. The security graph now reflects real production exposure. On the next `push:main` submission after merge, the 30 build-only dependencies leave the graph and their alerts **auto-dismiss**; the 2 guava alerts correctly remain (tracked as a separate shipped-dependency bump).
- Workflow-only change (no app code / test / schema). `dependency-submission.yml` runs on `push:main`, so it does not execute on the PR — the alert reduction is observable post-merge.

### V1X-14 — zig_obsidian becomes purchasable in the Store; V1X-14 COMPLETE (2026-06-10)

- Completes V1X-14. The `zig_obsidian` renderer palette + its `CosmeticRepositoryImplTest` exact-value test already shipped on `main` in commit `5033b77` (2026-05-28), but that commit never flipped the Store allow-list — so despite the commit message ("first purchasable dark skin"), `zig_obsidian` still rendered a disabled "Coming Soon" button. This PR closes that gap: `StoreScreen.kt`'s single `ENABLED_COSMETIC_ID = "zig_jade"` constant becomes a `Set` `ENABLED_COSMETIC_IDS = setOf("zig_jade", "zig_obsidian")`, and the purchasability `when` branch changes from `== ENABLED_COSMETIC_ID` to `in ENABLED_COSMETIC_IDS`. `zig_obsidian` (100 💎) now shows a live "💎 100" Buy button; equipping it swaps the ziggurat layer colors to the obsidian palette via the existing C.2 override pipeline. The Cosmetics header copy now names both Jade and Obsidian; the milestone cosmetics (lapis_lazuli_skin, garden_ziggurat_skin, sandals_of_gilgamesh) stay deliberately store-disabled (milestone acquisition only). Stale `ENABLED_COSMETIC_ID` comment references updated in both `StoreScreen.kt` and `CosmeticRepositoryImpl.kt`.
- `testDebugUnitTest` + `lintDebug` + `assembleDebug` BUILD SUCCESSFUL, 867 unchanged (the palette test was already on `main`; this is a Compose-surface allow-list flip with no JVM-testable change).

### V1X-13 i18n phase 1 (final) — HardcodedText lint-as-error guard; PHASE 1 COMPLETE (2026-06-04)

- Final slice of V1X-13 (ADR-0014). Adds a `lint { error += "HardcodedText" }` block to `app/build.gradle.kts`, promoting Android Lint's `HardcodedText` check from warning to **error** so a hardcoded `android:text` in an XML resource fails the build. Fixed the only 3 existing violations in `res/layout/widget_step_counter.xml`: the static title now uses `@string/app_name`; the two runtime-populated `TextView`s (`widget_daily_steps`, `widget_balance`, set via `RemoteViews` by `WidgetUpdateHelper`) switched from `android:text` placeholders to design-time `tools:text`.
- **Empirical finding (resolves the plan's "investigate" note):** `HardcodedText` is XML-only — it does NOT flag Compose `Text("literal")`. With the guard enabled, `lintDebug` stays green despite ~110 hardcoded Compose strings still present on the phase-2 screens; if the rule inspected Compose those would all fail. So the guard concretely protects XML resources only (today: the widget layout). Compose string discipline is held by the phase-1 migration + review; a dedicated Compose rule (custom lint / Detekt-compose / a dependency-free JVM guard test) is a deferred option. Documented in `docs/architecture.md` (new "Internationalization (i18n)" section). Also corrected the plan's snippet — `warningsAsErrors` is a Boolean (promotes ALL warnings), so a single-check guard uses the `error` `MutableSet`.
- `testDebugUnitTest` + `lintDebug` + `assembleDebug` BUILD SUCCESSFUL, 867 unchanged. **Completes V1X-13 i18n phase 1** (notifications + engine-internal floating-text via `domain/Strings` + battle/workshop Compose surfaces + this lint guard). Phases 2/3 (remaining screens, then the first non-English locale) deferred to v1.3 / v1.4.

### V1X-13 i18n phase 1 (cont.) — battle + workshop Compose surfaces (2026-06-04)

- Third slice of V1X-13 (ADR-0014). Extracts every user-facing string literal from the battle + workshop Jetpack Compose surfaces into `res/values/strings.xml` (~45 new resources) read via `stringResource(...)` / `context.getString(...)`. Migrated 7 files: `WorkshopScreen.kt` (weapons/cards buttons, balance header), `UpgradeCard.kt` (MAX / level / cost), `BattleScreen.kt` (wave header, cash, Battle-Steps banner + its content description, and the quit / speed / pause / upgrades content descriptions + speed labels), `PostRoundOverlay.kt` (all reward banners, stat rows, ad buttons, and the share text + chooser via `getString` inside the share `onClick`), `PauseOverlay.kt`, `InRoundUpgradeMenu.kt` (cash, FREE!, level·description, Now→Next readout, MAX, cost), and `BiomeTransitionOverlay.kt` (welcome / steps-walked / continue). Positional format args throughout (`%1$d` / `%1$s`) so translations can reorder; resources are shared where text is byte-identical (`cash_amount` `$%1$d` for HUD cash + in-round cost + cash-earned stat; `steps_earned_banner` for the HUD + post-round step banners; `action_resume` for the pause button + the battle pause-toggle content description; `upgrade_max` for both MAX labels). Pure glyph `Text` (▶ / ⏸ / ✕) and dynamic enum/number text (tab names, `type.name`, UW cooldown seconds) are left as-is — not translatable content. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 867 unchanged (pure refactor, no behaviour change). **Remaining phase-1:** the `HardcodedText` lint-as-error guard (final PR). Engine-internal Canvas announcement text (`WaveAnnouncement` / `GameEngine` per-enemy HP %) and the remaining screens (Store, Settings, Cards, Home, Economy, Labs, Missions, Stats, Supplies, UltimateWeapon, Help) are phase 2 (v1.3).

### V1X-13 i18n phase 1 (cont.) — domain/Strings seam for engine-internal floating-text (2026-06-04)

- Second slice of V1X-13 (ADR-0014). Routes the 6 engine-internal battle floating-text strings through a new pure-Kotlin `domain/Strings` seam so they are localizable without giving `GameEngine` a `Context` (which would force Robolectric onto the pure-JVM `GameEngineTest`). New `domain/Strings` interface (5 methods — `healHp` / `rapidFireBurst` / `cashReward` / `stepReward` / `powerStoneReward`; no Android imports, passes `DomainPurityTest`) + `data/AndroidStrings` impl (`Context.getString`; +5 `fx_*` resources in `strings.xml`). `GameEngine` gains a nullable `var strings: Strings?` (mirrors the `soundManager` collaborator pattern) and routes its 4 `FloatingText` literals (recovery + lifesteal heal, RAPID FIRE!, per-kill cash) through it with the original literal as fallback; `BattleViewModel` routes its 2 (step reward, boss Power Stones) through `engine.strings`. `GameSurfaceView` constructs `AndroidStrings(context)` directly (like `SoundManager`) and sets `engine.strings` — no Hilt binding. The byte-identical fallback literals keep `GameEngineTest` + `BattleViewModelTest` green with **zero churn** (no fake needed, no Robolectric). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 867 unchanged (pure refactor, no behaviour change). The `HardcodedText` lint-as-error guard remains deferred to the final phase-1 PR.

### V1X-13 i18n phase 1 (start) — notification string extraction (2026-06-04)

- Begin V1X-13 (i18n string extraction, phase 1). First slice lifts every user-facing notification string out of Kotlin literals into `res/values/strings.xml` (16 new resources). The 3 notification managers (`StepNotificationManager`, `SupplyDropNotificationManager`, `MilestoneNotificationManager`) now read all channel names/descriptions, titles, content, and action labels via `context.getString(...)`. Positional format args (`%1$d` / `%2$s`) so future translations can reorder; the step notification title reuses `R.string.app_name`; `SupplyDrop.trigger.message` is left as-is (domain-sourced). No new abstraction needed — the managers already hold a `Context`, so this slice stays clear of the battle engine. Deferred to subsequent phase-1 PRs: the `domain/Strings` Hilt seam (for engine-internal floating-text strings), the battle + workshop screen surfaces, and the `HardcodedText` lint-as-error guard (added last, once all phase-1 surfaces are migrated). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 867 unchanged (pure refactor, no behaviour change).

### Release lane — automated Play "What's new" notes (2026-06-04)

- `release.yml` now publishes Play Store release notes automatically. A new `Prepare Play release notes` step writes the annotated tag's message into `distribution/whatsnew/whatsnew-en-US` (capped at Play's 500-char limit; falls back to "Bug fixes and improvements." for lightweight tags / manual dispatch), and the `r0adkll/upload-google-play` step now passes `whatsNewDirectory: distribution/whatsnew`. `actions/checkout` gained `fetch-depth: 0` so the annotated tag object is available to read. Closes the gap where the first CI-driven release (`v1.0.1`) shipped to the internal track with no "What's new" text. Release notes are now sourced from the `git tag -a -m "…"` message — no separate manual paste into Play Console.

### Plan 32 — CI/CD pipeline (GitHub Actions) (2026-06-03)

- First CI for the repo (ADR-0018). Five files under `.github/`, all third-party actions SHA-pinned (Dependabot-maintained): `ci.yml` (PR + push:main gate — `./gradlew testDebugUnitTest lintDebug assembleDebug` + a Room schema-drift guard `git diff --exit-code app/schemas`; secret-free), `instrumented.yml` (`connectedDebugAndroidTest` on an API-34 KVM emulator, AVD-cached; blocking on PRs to `main` + nightly + dispatch), `release.yml` (`v*` tag → keystore-from-secrets → `bundleRelease` → `jarsigner -verify` → `r0adkll/upload-google-play` internal track + GitHub Release; `environment: release`), `dependency-submission.yml` (Gradle dependency graph on main), and `dependabot.yml` (gradle + github-actions weekly). Least-privilege `permissions`, `concurrency` cancellation. CI builds the committed `versionCode` (no auto-bump — respects the v13 reused-code rejection).
- **Lint fix surfaced by the new gate:** enforcing `lintDebug` revealed a latent error on `main` — `NotificationSettingsScreen.kt` cast `LocalContext.current` to `Activity` (`ContextCastToActivity`). Fixed by switching to `androidx.activity.compose.LocalActivity.current` (available since activity-compose 1.9.0; project on 1.12.3) and dropping the now-unused `android.app.Activity` / `LocalContext` imports. Behaviour unchanged. Local gate green: 867 tests + `lintDebug` + `assembleDebug` all pass.
- **Merged + post-merge setup (PR #100, 2026-06-03/04):** merged to `main` (merge commit `2e76f91`); both CI checks green on GitHub runners (`build-and-test` 7m30s, `connected` 8m34s). Manual setup completed 2026-06-04 — `release` environment + 9 secrets, branch protection on `main` requiring `build-and-test` + `connected`, and a `play-ci-upload` Play service account granted release-to-testing-tracks. `versionCode` bumped 16 → 17 for the first CI-driven release. Pipeline fully live.

### #27 — machine-enforced domain-purity guard (2026-06-03)

- New dependency-free JVM test `app/src/test/java/com/whitefang/stepsofbabylon/architecture/DomainPurityTest.kt` (1 test, new `architecture/` test package) machine-enforces the core architecture invariant (`CONSTRAINTS.md` / `START_HERE.md`): the `domain/` layer has **zero Android imports**. It walks the domain source tree and fails the build if any `.kt` file imports `android.` / `androidx.` / `com.android.` / `com.google.android.`. Self-validating — it first asserts the domain source root resolves to a directory, so a wrong test working-dir fails loud instead of silently passing on an empty walk. Chosen over Konsist/Detekt deliberately: no new Gradle dependency, runs inside the existing `testDebugUnitTest` suite, and it precisely covers the one invariant the 2026-06-02 issue-triage recon flagged as the cheap high-leverage win for GitHub #27. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 866 → 867 (the run also confirms `domain/` is currently pure — 0 offending imports). Addresses the machine-enforced-domain-purity half of #27 (broader single-module modularisation remains).

### V1X-09 Phase 3 (final) — SimulationEvent flow replaces the @Volatile callbacks; Phase 3 COMPLETE (2026-06-03)

- Final slice of V1X-09 Phase 3 (ADR-0012). Replaces `GameEngine`'s two `@Volatile` callback fields (`onStepReward`, `onBossKilled`) with a pure-domain event stream, completing the engine↔ViewModel decoupling. New `domain/battle/engine/SimulationEvent` sealed interface — `StepReward(amount, x, y)` + `BossKilled(tier, x, y)` (no Android imports). `Simulation` gains `events: SharedFlow<SimulationEvent>` (`replay = 0`, `extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`) fed by `emit(event)` (non-suspending `tryEmit`, safe to call from the game-loop thread). `replay = 0` is deliberate: a collector that re-subscribes for the next round (`playAgain`) must never re-receive the previous round's events, which would double-credit. `GameEngine` drops the two callback fields, exposes `val events get() = simulation.events`, and `handleEnemyDeath` now `simulation.emit(…)`s the two events instead of invoking lambdas. `BattleViewModel` replaces `wireStepRewardCallback` / `wireBossKilledCallback` with a single `@VisibleForTesting handleSimulationEvent(engine, event)` that preserves the original per-event coroutine scope (Battle-Step credit on `applicationScope` so a mid-round nav-away still commits the reward; boss Power Stones on `viewModelScope`); the stream is collected by a child coroutine of the polling loop, cancelled when the loop breaks at round end and re-created fresh by `playAgain` (one collector per round — no leak, no double-credit). `onCleared` no longer null-unwires callbacks (the collector dies with `viewModelScope`). +2 pure-JVM `SimulationTest` cases (emit delivers both event types to a collector in order; the `replay = 0` stream does NOT deliver pre-subscription events to a late subscriber). The 6 `BattleViewModelTest` step-reward tests + 3 `GameEngineTest` R407 boss tests were migrated in place — they now drive `handleSimulationEvent` / collect `eng.events` (via `runTest(UnconfinedTestDispatcher())`) instead of invoking the callbacks. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 864 → 866. No behaviour change. **Completes Phase 3 — `GameEngine` is now a thin presentation/render shell over the pure-domain `Simulation`, and the game-loop → ViewModel side-effect hand-off is a single Flow.**

### V1X-09 Phase 3 (cont.) — UW lifecycle timers extracted to domain Simulation (2026-06-02)

- Fifth slice of V1X-09 Phase 3 (ADR-0012). Moves the pure UW cooldown + effect-duration timer state machine out of `GameEngine.updateUWs` and into the pure-domain `Simulation`. New `Simulation.advanceUWTimers(cooldownRemaining, effectTimeRemaining, deltaTime): UWTimerAdvance` decrements both timers (cooldown floored at 0; the effect duration counts down only while active and clamps to exactly 0 on the frame it crosses) and returns two transition flags: `effectWasActive` (the effect was running at frame start, so its per-frame ongoing effect — BLACK_HOLE pull/DoT, POISON_SWAMP DoT — should fire; true even on the frame the effect expires, exactly matching the pre-extraction inline block where the ongoing `when` sat inside the `effectTimeRemaining > 0f` guard) and `justExpired` (the effect crossed to ≤0 this frame, so its one-shot expiry side-effects — CHRONO_FIELD slow reset, GOLDEN_ZIGGURAT stat/fortune restore — should run). New `Simulation.isUWReadyToFire(cooldownRemaining, effectTimeRemaining)` is the auto-trigger readiness predicate. `GameEngine.updateUWs` now applies the returned timer values back onto each `UWState` and keeps every presentation-coupled side-effect (enemy damage, stat mutation, the chrono/golden visual flags) + the `activateUW` activation path; the engine's public API, the `UWState` data class, and every caller (`BattleViewModel` polling, `GameEngineTest`) are unchanged. +7 pure-JVM `SimulationTest` cases (4 `advanceUWTimers` covering cooldown clamp / inactive no-op / active countdown / expiry-frame flags, 3 `isUWReadyToFire`). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 857 → 864. No behaviour change — the `GameEngineTest` UW + chrono + GOLDEN regression guards still pass. Remaining Phase 3 slice: the `SimulationEvent` flow replacing the `@Volatile` callbacks.

### V1X-09 Phase 3 (cont.) — collision sweep extracted to domain Simulation (2026-06-02)

- Fourth slice of V1X-09 Phase 3 (ADR-0012). Moves the per-frame collision iteration + overlap geometry out of the presentation `CollisionSystem` and into the pure-domain `Simulation`. `domain/battle/entity/EntityProtocol` gains `x` / `y` / `width` reads (the positional surface the sweep needs; `presentation/battle/engine/Entity`'s existing `var x/y/width` become `override var`, `height` stays a plain `var`). `Simulation` gains two generic, behaviour-preserving sweeps: `detectProjectileEnemyHits<P, E : EntityProtocol>(projectiles, enemies, onHit)` (first-overlap-per-projectile + `break`) and `detectZigguratHits<P : EntityProtocol>(enemyProjectiles, zigX, zigY, zigWidth, onHit)`. Both keep the EXACT original overlap expressions and fire `onHit` **synchronously inside the loop** — so a hit that knocks back or kills an enemy (`GameEngine.onProjectileHitEnemy` applies `applyKnockback` + sets `proj.isAlive = false`) is still observed by later projectiles' overlap tests the same frame; this is why the loop could not be refactored to detect-all-then-dispatch. The generics let callers pass concretely-typed lists + callbacks while the sweep stays Canvas-free. `CollisionSystem` shrinks to a presentation adapter — it keeps only `filterIsInstance` type-partitioning + the alive snapshot (both inherently concrete-type concerns) and delegates both sweeps to the engine's `Simulation`; `GameEngine.update()` passes `simulation` into `CollisionSystem.checkCollisions`. +6 pure-JVM `SimulationTest` cases (overlap fires the pair, first-hit `break`, no-overlap no-fire, an interleaved-firing guard where `onHit` moves the enemy mid-sweep so a later projectile misses, plus ziggurat hit/miss). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 851 → 857. No behaviour change. Remaining Phase 3 slices: UW lifecycle, `SimulationEvent` flow.

### V1X-09 Phase 3 (cont.) — EntityProtocol seam + entity-tick loop extracted to domain Simulation (2026-06-01)

- Third slice of V1X-09 Phase 3 (ADR-0012). Introduces a pure-domain `domain/battle/entity/EntityProtocol` interface (`isAlive`, `isChronoSlowable` defaulting `false`, `update(deltaTime)` — no Android imports) so the domain `Simulation` can iterate battle entities without referencing the Canvas-coupled presentation types. `presentation/battle/engine/Entity` now implements `EntityProtocol` (keeps its abstract `render(canvas)`); `EnemyEntity` overrides `isChronoSlowable = true` (the only entity CHRONO_FIELD slows). New `Simulation.tickEntities(entities, deltaTime, chronoSlowFactor)` runs the per-frame tick, scaling `deltaTime` for chrono-slowable entities and ticking everything else at full speed; `GameEngine.update()` replaces its inline `entities.forEach { … if (chronoActive && e is EnemyEntity) … }` loop with `simulation.tickEntities(entities, deltaTime, if (chronoActive) chronoSlowFactor else 1f)`, removing the last `is EnemyEntity` type check from the tick. Engine public API + every caller unchanged; the `GameEngineTest` chrono regression guards (`setChronoActive` + `chronoSlowFactor` reflection) still pass because behaviour is identical. +4 pure-JVM `SimulationTest` cases (slowable scaled, non-slowable full dt, factor-1 no-op, mixed-list all-ticked) backed by a private `FakeEntity(isChronoSlowable)`. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 847 → 851. No behaviour change. Remaining Phase 3 slices: collision migration, UW lifecycle, `SimulationEvent` flow.

### V1X-09 Phase 3 (cont.) — round-progress counters extracted to domain Simulation (2026-06-01)

- Second slice of V1X-09 Phase 3 (ADR-0012). `domain/battle/engine/Simulation` gains the round-progress counters — `totalEnemiesKilled` / `totalStepsEarned` / `elapsedSeconds` (all `@Volatile`, `private set`) — plus `tickElapsed(dt)` / `recordEnemyKilled()` / `creditSteps(amount)` (ignores non-positive) / `hasWaveProgress()`. `GameEngine` now delegates those three counters + `hasWaveProgress()` to the `Simulation` and routes the increment sites (`update()` clock, `handleEnemyDeath` kill + Battle-Step credit) through it; `roundOver` stays in the engine as a control flag tied to the update loop + external quit signal. Public API + every caller (`BattleViewModel`, `GameSurfaceView`, the Robolectric `GameSurfaceViewTest` + instrumented `BattleSurfaceLifecycleTest`) unchanged. +7 pure-JVM `SimulationTest` cases; `BattleViewModelTest.installEngineWithProgress` reworked to seed the counters through the engine's `simulation` field. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 840 → 847. No behaviour change.

### V1X-09 Phase 3 (start) — cash economy extracted to domain Simulation (2026-06-01)

- Begin V1X-09 Phase 3 (lift the `GameEngine.update()` simulation into `domain/battle/engine/Simulation`; ADR-0012). First slice extracts the in-round **cash economy** into a new pure-domain `Simulation` class (no Android imports): `@Volatile cash` / `totalCashEarned` (private set) + `reset()` / `creditCash(amount)` (credits both balances, ignores non-positive) / `applyInterest(level)` (compounds `min(level × 0.5 %, 10 %)` onto `cash` only — interest is excluded from lifetime earned) / `spend(amount)` (insufficient-balance guard). `presentation/battle/engine/GameEngine` now holds a `Simulation` and delegates its `cash` / `totalCashEarned` / `spendCash` public surface to it; the per-kill and wave-complete reward *formulas* (which depend on tier config, enemy type, and engine multiplier fields) stay in `GameEngine` and call `creditCash` / `applyInterest` with the computed amounts. Engine public API + every caller (`BattleViewModel` polling, `BattleScreen`, `GameEngineTest`) unchanged. +9 pure-JVM `SimulationTest` cases; `BattleViewModelTest.installEngineForPurchase` reflective helper reworked to seed cash through the engine's private `simulation` field (the old `cash` backing field no longer exists). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 831 → 840. No behaviour change. Establishes the `Simulation` class + delegation pattern for subsequent Phase 3 slices (entity tick, collision, UW lifecycle, `SimulationEvent` flow).

### V1X-09 Phase 2 (final) — ZigguratEntity simulation extraction; Phase 2 COMPLETE (2026-06-01)

- Final per-entity sub-PR of V1X-09 Phase 2 (ADR-0012). New pure-domain `domain/battle/entity/ZigguratState` owns the live combat stats, HP, RAPID_FIRE multiplier, and the attack cooldown, plus the derived `attackInterval` / `attackRange` reads and the HP-regen (`regenHp`, clamped at `maxHp`) + attack-readiness (`tickAttackReady(dt): Boolean` / `onFired()` / `holdReady()`) logic (no Android imports). `presentation/battle/entities/ZigguratEntity` delegates its **entire public surface** to the state — `currentHp` / `maxHp` (get+set), `stats` / `attackRange` (read-only get), `rapidFireMultiplier` (get+set), `updateStats` — so `GameEngine` (~30 `zig.*` touch points: HP damage/heal/death-defy/second-wind writes, `applyStats`, `tickRapidFire`) and `BattleViewModel` are **untouched**. The entity keeps the 5-layer geometry, `originX`/`originY`/`centerY`, the nearest-enemy targeting + fire callback (which need presentation `EnemyEntity` refs), and the Canvas `render()`. Constructor unchanged. +6 pure-JVM `ZigguratStateTest` (regen below cap, regen clamp at maxHp, attack-cooldown cadence across the interval, holdReady keeps ready next tick, attackInterval reflects attackSpeed × rapidFireMultiplier, updateStats redirects attackRange + attackInterval). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 825 → 831. No behaviour change. **This completes Phase 2 — all 5 battle entities (Projectile, EnemyProjectile, Orb, Enemy, Ziggurat) now delegate their simulation state to `domain/battle/entity`.** Phase 3 (lift the `GameEngine.update()` loop into `domain/battle/engine/Simulation`) remains.

### V1X-09 Phase 2 (cont.) — EnemyEntity motion extraction (2026-06-01)

- Third per-entity sub-PR of V1X-09 Phase 2 (ADR-0012). New pure-domain `domain/battle/entity/EnemyState` owns the enemy's position + attack cooldown + captured initial distance and the homing-movement + RANGED stop-distance + attack-cadence state machine (no Android imports): `spawn(x, y)` captures the initial distance, `update(dt): Boolean` returns `true` on the frame an attack should fire, `applyKnockback` shifts position. `presentation/battle/entities/EnemyEntity` delegates `update()` / `initDistance()` / `applyKnockback()` to it and syncs `x`/`y` back onto the `Entity` base each tick (so `CollisionSystem` / `GameEngine` / `OrbEntity` position reads stay correct), keeping HP/armor/death, the melee/ranged attack callbacks (which need the entity reference), and the Canvas `render()`. Constructor + `WaveSpawner` `.apply { x=…; y=…; initDistance() }` wiring unchanged. +5 pure-JVM `EnemyStateTest` (melee proportional move, fire-on-arrival, cooldown gating across the interval, ranged stops farther out than melee range, knockback shift) — no Robolectric. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 820 → 825. No behaviour change. Only `ZigguratEntity` remains for Phase 2.

### V1X-09 Phase 2 (cont.) — Orb + EnemyProjectile motion extraction (2026-05-29)

- Second per-entity sub-PR of V1X-09 Phase 2 (ADR-0012). `EnemyProjectileEntity` (identical homing motion to `ProjectileEntity`) now reuses the same pure-domain `ProjectileState` — no new domain class, motion already covered by `ProjectileStateTest`. New pure-domain `domain/battle/entity/OrbState` owns the orbit angle + radial-oscillation phase + derived (x, y) position (the #54 radius-oscillation math); `presentation/battle/entities/OrbEntity` delegates its position to it and keeps the enemy-proximity / per-enemy hit-cooldown logic (needs `EnemyEntity` refs + the `onHitEnemy` callback) plus the Canvas `render()`. `OrbEntity` re-exposes `ORBIT_RADIUS_MIN/MAX`, `ORBIT_PERIOD_SEC`, and `currentOrbitRadius` from `OrbState` so the existing Robolectric `OrbEntityTest` (6 #54 regression tests) is untouched. Both entity constructors unchanged. +4 pure-JVM `OrbStateTest` (mid-radius + on-orbit position at phase 0, MAX after quarter-cycle, MIN after three-quarter-cycle, angular advance moves position). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 816 → 820. No behaviour change.

### V1X-09 Phase 2 (start) — ProjectileEntity motion extraction (2026-05-29)

- Begin V1X-09 Phase 2 (entity update-logic extraction; ADR-0012). New pure-domain `domain/battle/entity/ProjectileState` owns the projectile's position + alive flag + homing-toward-target step (no Android imports); `presentation/battle/entities/ProjectileEntity` now delegates its `update()` to it and keeps only the Canvas `render()` plus the collision/bounce fields (`damage`, `bouncesRemaining`, `hitEnemies`) that `CollisionSystem` / `GameEngine` read. Constructor signature unchanged — no call-site churn. Establishes the `domain/battle/entity/<Name>State` package + delegation pattern that the remaining entities (Enemy, Orb, EnemyProjectile, Ziggurat) will follow in subsequent per-entity sub-PRs. +4 pure-JVM `ProjectileStateTest` cases (proportional single-axis + two-axis movement, arrival-within-one-step death, multi-step convergence) — no Robolectric. `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 812 → 816. No behaviour change.

### V1X-15b ENEMY_INTEL UI overlays (2026-05-29)

- Ship the information half of `ResearchType.ENEMY_INTEL` (the combat half — +2 %/level damage — landed in PR #84). Three SurfaceView overlays gated on the new `GameEngine.enemyIntelLevel` (plumbed from `BattleViewModel.labLevels`): **L1+** the wave-cooldown banner shows the next wave's composition (e.g. `Next: 12 BASIC, 4 RANGED, 1 BOSS`); **L5+** each living enemy shows an HP-% label above its HP bar; **L10** the HUD shows a boss-arrival countdown (`Boss in N waves` / `Boss next wave`). New pure helpers `WaveSpawner.getWaveComposition(wave)` (deterministic expected per-type counts mirroring the `pickType` probability bands) + `WaveSpawner.wavesUntilNextBoss()`. `WaveCooldownText` gains an optional `nextWaveComposition` line. `GameEngine` exposes `nextWaveCompositionLabel()` / `bossCountdownLabel()` (null below their level gates) and draws the L5 HP-% labels in `render()` (kept out of `EnemyEntity` so the level gate doesn't touch the entity constructor). `BattleViewModel` extracts an `applyResearchParams(engine)` helper (deduped across `startPollingEngine` + `playAgain`) that pushes the cash / UW-cooldown multipliers + ENEMY_INTEL level. +6 JVM tests (3 `WaveSpawnerTest`, 2 `GameEngineTest`, 1 `BattleViewModelTest`). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 806 → 812. ADR-0017 status updated (UI overlays shipped; the +2 %/lvl coefficient remains an open balance item pending on-device feedback). **Needs human on-device verification — the HP-% labels must stay legible at 30+ enemies on high-tier Tank waves.**

### V1X-15b ENEMY_INTEL combat foundation (2026-05-29)

- Ship the combat half of `ResearchType.ENEMY_INTEL` (GitHub issue #44; design ADR-0017). The enum flips from its `isComingSoon` placeholder (3000/6h/maxLevel 3) to the locked balance: 8,000 Steps base × 1.5 scaling, 4h × 1.10, maxLevel 10, +2 %/level damage. `ResolveStats.damage` gains a `× (1 + lab(ENEMY_INTEL) × 0.02)` outer multiplier that stacks multiplicatively with DAMAGE_RESEARCH. ENEMY_INTEL now surfaces in the Labs screen (only `AUTO_UPGRADE_AI` remains Coming Soon); its damage flows through the existing DAMAGE upgrade readout via `labLevels`, matching how DAMAGE_RESEARCH already works (research types have no standalone `DescribeUpgradeEffect` readout — that use case is keyed on `UpgradeType`). +6 tests (5 `ResolveStatsTest` ENEMY_INTEL multiplier cases + 1 `ResearchTypeTest` balance-values guard; the set-equality contract tightens to `{AUTO_UPGRADE_AI}`). `testDebugUnitTest` BUILD SUCCESSFUL, 800 → 806. **The L1/L5/L10 UI overlays (next-wave composition, per-enemy HP %, boss-arrival countdown) are a tracked follow-up PR — SurfaceView rendering changes that need human on-device verification.** See ADR-0017.

### V1X-08 StoreIapFlowTest formally deferred (2026-05-29)

- Docs-only. After `BattleSurfaceLifecycleTest` (PR #81) and `DeepLinkIntentTest` (PR #82) shipped — each closing a genuine Robolectric blind spot — the third planned V1X-08 suite, `StoreIapFlowTest`, is formally deferred. Rationale (recorded in the V1X-08 status note in `docs/plans/plan-V1X-roadmap.md`): it has no real-framework-only gap to exercise. A meaningful instrumented IAP flow can't drive real Play Billing on a bare emulator (needs a signed Play-Console build + a licensed test account), so it would run against a fake `BillingClientAdapter` — exactly what the 14-test JVM `BillingManagerImplTest` already does, against a real in-memory Room DB (so `grantOnceAtomic`'s `@Transaction` semantics are exercised end-to-end). The real SDK path was separately verified on-device in the C.5 internal-track pass. V1X-08's first-pass instrumented-coverage goal is considered met with 2 of 3 suites. No code or test changes; instrumented count stays at 9.

### V1X-08 DeepLinkIntentTest — second real instrumented suite (2026-05-29)

- Add `app/src/androidTest/.../DeepLinkIntentTest.kt` (4 tests), the second real instrumented suite for V1X-08. Verifies the `navigate_to` deep-link contract (`MainActivity` reads `intent.getStringExtra("navigate_to")` and routes via `Screen.fromRoute` + `argumentFreeRoutes`) against the **real** Android framework. The headline test round-trips the extra through a real `android.os.Parcel` — the instrumented-only value over the Robolectric `DeepLinkRoutingTest`, because notification deep-links travel inside a `PendingIntent` that marshals the `Intent` through Binder/Parcel, which Robolectric only stubs. Remaining tests: missing-extra-is-null after round-trip; every `argumentFreeRoute` resolves + passes the routing gate; unknown route fails closed. No Hilt rule (touches only `Intent` / `Parcel` / the pure-Kotlin `Screen`); no new Gradle deps. `connectedDebugAndroidTest` BUILD SUCCESSFUL in 14s on Pixel 6 emulator (9 instrumented tests: 4 new + 4 BattleSurfaceLifecycle + smoke). `StoreIapFlowTest` remains for a follow-up PR (needs a Hilt billing-adapter test double or a signed Play-Console build — design decision deferred). Test count: 800 JVM (unchanged) + 9 instrumented (was 5).

### V1X-08 BattleSurfaceLifecycleTest — first real instrumented suite (2026-05-29)

- Add `app/src/androidTest/.../BattleSurfaceLifecycleTest.kt` (4 tests), the first real instrumented suite layered on the V1X-08 Phase 1A harness. Re-runs the four R3-01 backgrounding-state-loss invariants (GitHub issue #2) against the **real** Android framework on a connected emulator, hardening the existing `GameSurfaceViewTest` which exercises the same `GameSurfaceView` seams (`initEngineIfNeeded`, `pendingSpeed`, `pendingPaused`) under Robolectric's shadow framework. Robolectric stubs `SurfaceView` and never produces real surface callbacks, so its coverage is a workaround; this suite constructs the view on the real main thread via `InstrumentationRegistry` + `runOnMainSync`. No Hilt rule (`GameSurfaceView` takes only `Context`); no new Gradle deps (`androidx.test:runner` + `androidx.test.ext:junit` already present from Phase 1A). `connectedDebugAndroidTest` BUILD SUCCESSFUL in 19s on Pixel 6 emulator (5 instrumented tests: 4 new + the smoke test). `StoreIapFlowTest` + `DeepLinkIntentTest` remain for follow-up PRs. Test count: 800 JVM (unchanged) + 5 instrumented (was 1).

### V1X-08 Phase 1A instrumented-test infrastructure (merged 2026-05-29)

- **PR #80:** Stand up the `androidTest/` source set end-to-end. New `HiltTestRunner` (subclass of `AndroidJUnitRunner` that swaps `StepsOfBabylonApp` → `HiltTestApplication` via `newApplication` override) wired via `testInstrumentationRunner` in `app/build.gradle.kts`. New `androidTestImplementation` deps: `androidx.test:runner` 1.6.2, `androidx.test.ext:junit` 1.2.1, `dagger.hilt:hilt-android-testing` 2.59.2 (re-uses existing `hilt` version pin). New `kspAndroidTest(libs.hilt.compiler)` so Hilt DI components get generated for `@HiltAndroidTest` classes; the regular `ksp` config does not cover the androidTest source set. Single-test smoke suite `InfrastructureSmokeTest.harnessBoots` proves the pipeline (Gradle config → KSP → Hilt component → AndroidJUnit4 → emulator) is wired correctly. `connectedDebugAndroidTest` BUILD SUCCESSFUL in 1m 28s on Pixel 6 emulator (API 36); JVM unit tests still 800/800 green. Three real suites (`BattleSurfaceLifecycleTest` for R3-01-class regression guards, `StoreIapFlowTest` for closed-track IAP coverage, `DeepLinkIntentTest` for the 13 nav routes) deferred to follow-up PRs. Per the V1X-08 plan, no ADR required at this scope. Test count: 800 JVM (unchanged) + 1 instrumented (new).

### V1X-18 STEP_MULTIPLIER asymptotic curve (2026-05-28)

- **PR #79:** Replace the linear-with-hard-cap STEP_MULTIPLIER curve with an asymptotic curve `bonus = 1 - (1 - 0.05)^level`. Early-mid game buffed (L10 was +10%, now +40.1%); late game tiny nerf at L100 (-0.6pp); dead-content L99=L100 fixed. Math centralised in `SimulationMath.stepMultiplierBonus`. DescribeUpgradeEffect format bumped to 2-decimal precision. ADR-0015 records the rationale and V1X-18b deferred (cross-validator interaction). +11 tests. Closes #49. Test count 789 → 800.

### V1X-19 GDD GPS Exploration Mode reconciliation (2026-05-28)

- **PR #78:** Reconcile GDD §2.3 with shipping product per ADR-0016. Drop the GPS-based "Exploration Mode" feature from v1.x scope (battery + privacy + Play review trade-off doesn't justify unproven gameplay payoff). Reserved as v2.x meta-progression concept. New "Privacy & Permissions" section in architecture.md explicitly declares no location services. Docs-only. Closes #40.

### V1X-16 Weekly Challenges expanded view (2026-05-28)

- **PR #77:** Expand Weekly Step Challenge card in CurrencyDashboardScreen — time remaining ('Nd Hh' until next Monday reset) shown in header; last 4 past weeks history with met/missed indicator + PS earned per week. New `WeeklyChallengeDao.getLastNWeeks(limit)` query. New `WeeklyResult` data class. +2 tests. Closes #41. Test count 787 → 789.

### V1X-09 Simulation extraction Phase 1 (2026-05-28)

- **PR #76:** Lift pure-math helpers from `GameEngine.kt` (1004 LOC) to a new `domain/battle/engine/SimulationMath` object. Zero Android imports. 27 new pure-JVM tests covering Recovery Packages heal formula, Chrono multiplier, Thorn damage reflection, Lifesteal heal + accumulator threshold-crossing, HP clamping. ADR-0012 documents the Phase 1 decision; Phase 2 (entity extraction) and Phase 3 (full Simulation+Flow) deferred. Closes #37 (partial). Test count 760 → 787.

### V1X Wave 3 partial — Repo tests + content/social (2026-05-28)

Five sub-plans landing the testing infrastructure foundation and content/social polish. Test count 711 → 760.

- **V1X-07 Repository unit tests (PR #73):** 4 new test files, 49 tests for PlayerRepositoryImpl (13), UltimateWeaponRepositoryImpl (12), CardRepositoryImpl (16), StepRepositoryImpl (8). Pattern uses mockito-kotlin to verify entity→domain mapping + DAO call shapes. Closes #42 (partial — 4/6 repos).
- **V1X-15 Coming Soon flags (PR #74):** Biome.isComingSoon flag (CELESTIAL_GATE flagged true). LabsViewModel filters research types where isComingSoon=true. +1 test. Closes #43, #44 (AUTO_UPGRADE_AI half).
- **V1X-17 Text-share for personal bests (PR #75):** PostRoundOverlay shows 'Share Record' button on new wave record. ACTION_SEND with pre-populated text. New BuildConfig.PLAY_STORE_URL. Closes #50.

V1X-08 (instrumented tests) and V1X-09 (simulation extraction) remain — both multi-day refactors deferred.

### V1X Wave 2 — Audio overhaul + quick wins (2026-05-28)

Seven sub-plans implementing audio overhaul and quick-win fixes. Test count 704 → 711.

- **V1X-05 Frequency-aware SFX throttle (PR #67):** SHOOT throttle scales with attack speed — `(interval/3).coerceIn(30,100)ms`. +5 tests. Closes #46.
- **V1X-04 Real SFX assets (PR #68):** Replaced 7 placeholder sine-wave sounds with synthesized game audio (frequency sweeps, noise bursts, ADSR envelopes). Generator script at `tools/generate_sfx.py`. Closes #38.
- **V1X-06 Background music system (PR #69):** MusicManager with walking/battle tracks, audio focus handling, volume slider + mute toggle in Settings. +1 test. Closes #39.
- **V1X-11 Per-kill scope fix (PR #70):** Single-line `viewModelScope` → `applicationScope` so per-kill step credit survives mid-round nav-away. Closes #51.
- **V1X-10 Atomic Gem/PS spend (PR #71):** SQL-guarded `spendGemsAtomic`/`spendPowerStonesAtomic` eliminates TOCTOU double-deduct race. Closes #35.
- **V1X-14 zig_obsidian palette (PR #72):** Obsidian-black ziggurat skin now purchasable in Store. +1 test. Closes #45.

### V1X Wave 1 — Post-launch polish (2026-05-28)

Three sub-plans implementing the first v1.x patch wave. Test count 698 → 704.

- **V1X-03 Per-screen navigation icons (PR #64):** Replaced 9× `Icons.Default.Star` placeholders with category-appropriate Material icons (BarChart, AutoAwesome, Style, Inbox, AttachMoney, Flag, Settings, ShoppingCart, HelpOutline). Closes #33.
- **V1X-01 In-app data deletion UI (PR #65):** New `DataDeletionManager` performs full local wipe (WorkManager cancel → service stop → DB delete → 10 SharedPreferences clear → Keystore alias delete → Activity recreate). Settings screen gains danger-styled "Delete All Data" card with 2-step confirmation dialog. New dep: `work-testing`. +4 tests. Closes #48.
- **V1X-02 Season Pass UI revamp (PR #66):** Season Pass card now shows "Active — N days remaining", free-vs-paid feature comparison, and "Manage subscription" deep-link to Play subscription management. +2 tests. Closes #47.

### v15 soak hotfix bundle (2026-05-28)

Fixes 5 issues filed during v15 closed-track soak testing. Commit `0ecfe1d`. Test count 695 → 698.

- **#60 Higher tiers don't get harder:** Enemy HP/damage now scaled by `TierConfig.cashMultiplier` per tier (T1=1×, T10=10×). Added `tierMultiplier` param to `EnemyScaler.scaleHealth/scaleDamage`, threaded through `WaveSpawner` and `GameEngine`.
- **#62 Free Upgrades no visual:** Wired `lastPurchaseFree` from `BattleUiState` to `InRoundUpgradeMenu`. Green "FREE!" text appears next to cash display when a free upgrade triggers.
- **#59 Death Wave invisible:** Overhauled DEATH_WAVE visual effect — red expanding ring (was green), 1.2s duration (was 0.5s), 8 particles/tick (was 3), bigger size (7f vs 4f), wider radius (0.8× screen vs 0.6×).
- **#63 Wave progress bar:** Added thin `LinearProgressIndicator` to Compose HUD showing spawn/cooldown phase progress. Green during spawning, amber during cooldown.
- **#61 Ranged thorn damage:** Reversed R3-02 design decision. Ranged enemies now take thorn damage when their projectiles hit the ziggurat. Widened `onFireProjectile` callback to pass shooter reference through `EnemyProjectileEntity` to `applyDamageToZiggurat`.

### AAB v15 uploaded to Play Console closed track (2026-05-27 ~05:39 BST)

User uploaded `app/build/outputs/bundle/release/app-release.aab` (versionCode 15) to the Play Console closed-testing track. AAB bundles 3 v14-soak-board fixes: #53 (card upgrade descriptions, PR #56), #55 (lab mission credit, PR #57), #54 (orbs damage via radial oscillation, PR #58).

**On-device smoke test pending.** Test the 3-item checklist on a real device installed from the closed track:

1. Card upgrade visibly changes its description text (e.g. IRON_SKIN Lv 1 "+10% Defense Absolute" → upgrade once → text shows ≥ +15%).
2. Background-completed lab research credits the COMPLETE_RESEARCH daily mission on next app launch.
3. Orbs visually pulse inward and damage enemies at melee range (HP bars on parked enemies should drop visibly when an orb's inner sweep aligns with the enemy's angle).

**No source / build / test impact — external upload milestone.** Closed-track ≥14-day window unchanged; earliest production-access application stays 2026-06-09.

### versionCode 14 → 15 + AAB v15 build (2026-05-27)

Bundle of the 3 v14-soak-board fixes — #53 (PR #56), #55 (PR #57), #54 (PR #58) — into a single AAB for closed-track upload. Bumped `versionCode` 14 → 15 in `app/build.gradle.kts`. `versionName` stays `1.0.0` because this is a hotfix, not a feature release.

`./run-gradle.sh clean bundleRelease` BUILD SUCCESSFUL in 2m 32s. Output AAB at `app/build/outputs/bundle/release/app-release.aab` (~19 MB), signed with the upload keystore from `keystore.properties` and verified by `jarsigner -verify` (PKIX warnings expected for self-signed upload keys; Play App Signing re-signs after upload). Merged manifest at `app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml` confirms `android:versionCode="15"`.

**What's bundled in v15 vs v14:**

- **#53** — Card upgrade descriptions reflect level (PR #56). New `CardType.effectDescriptionAtLevel(level)` method.
- **#55** — Background lab research completion credits the COMPLETE_RESEARCH daily mission (PR #57). HomeViewModel.init R3-03 regression.
- **#54** — Orbs damage enemies via radial oscillation (PR #58). Geometry mismatch since Plan 10b.

**Upload note.** Closed-track ≥14-day window resumes from v14 effective 2026-05-26. Uploading v15 mid-window does NOT reset the 14-day clock as long as v15 doesn't surface fresh blockers in its own soak. Earliest production-access application stays **2026-06-09**.

**On-device verification needed post-upload:** install v15 from the closed track on a real device → confirm a card upgrade visibly changes its description text (e.g. IRON_SKIN Lv 1 "+10% Defense Absolute" → upgrade once → text shows ≥ +15%) → confirm a background-completed lab research project credits the COMPLETE_RESEARCH daily mission on the next app launch → confirm orbs visually pulse inward and damage enemies at melee range (HP bars on parked enemies should drop visibly when an orb's inner sweep aligns with the enemy's angle).

### Fix #54: Orbs do no damage — radial oscillation through enemy kill zone (2026-05-27)

Fixes [GitHub issue #54](https://github.com/JonWhiteFang/steps-of-babylon/issues/54). The ORBS Workshop upgrade has been dead content since Plan 10b shipped — orbs orbited at a fixed radius `stats.range * 0.4f` ≈ 120 px while enemies stopped at meleeRange = 40 px from the same origin, so the minimum orb-to-enemy distance (80 px) was 3.2× larger than HIT_RANGE (25 px). Orbs literally couldn't reach stopped enemies; they only occasionally clipped enemies during the brief approach window. Surfaced during v14 closed-track soak.

**Bug shape (geometry mismatch since 2024).** Both circles share the same centre (`zig.originX, zig.originY`) — orbs are constructed with `zigX = zig.originX, zigY = zig.originY` and enemies target `targetX = zig.originX, targetY = zig.originY` per `WaveSpawner` lines 11–12. So an enemy parked at melee range sits at radius 40 px; an orb sat at radius 120 px on the same circle. When orb angle == enemy angle, distance = `120 - 40 = 80 px` — 3.2× larger than `HIT_RANGE = 25`. The damage wiring (`OrbEntity.update → onOrbHitEnemy → enemy.takeDamage`) was correct; the geometry was wrong. **Zero unit tests covered the orb damage path before this fix.**

**Fix — radial oscillation.** Replace the fixed orbit radius with a sinusoidal oscillation between `ORBIT_RADIUS_MIN = 25` (orb just inside meleeRange, 15 px from enemy → hit) and `ORBIT_RADIUS_MAX = 70` (orb cleanly outside, 30 px from enemy → no hit). One full cycle takes `ORBIT_PERIOD_SEC = 2.5 s`. All 6 max-level orbs share the same radial phase by default so the swarm pulses inward together — a visible "breathing" defensive rhythm rather than a constantly-grinding kill ring. Per-enemy `HIT_COOLDOWN = 0.5 s` still gates double-hits within a single inward sweep.

The math:

| Phase | sin(phase) | Radius | Distance to enemy at melee | Hit? |
|---|---|---|---|---|
| `-π/2` (inner) | -1 | 25 | 15 | ✓ (well within HIT_RANGE) |
| `0` (mid) | 0 | 47.5 | 7.5 | ✓ |
| `+π/2` (outer) | +1 | 70 | 30 | ✗ (outside HIT_RANGE) |

Result: orbs deal damage during the inward half of the cycle (~1.25 s), rest during the outward half. With 6 orbs synced and each enemy hit at most once per HIT_COOLDOWN per orb, expected DPS is ~12 dmg/s per enemy at base damage 10 — about half the throughput of a fixed-radius orbit but with much more visual character.

**Files changed:**

- `presentation/battle/entities/OrbEntity.kt` — fully rewritten. Dropped the `orbitRadius` constructor param. Added 3 companion constants (`ORBIT_RADIUS_MIN`, `ORBIT_RADIUS_MAX`, `ORBIT_PERIOD_SEC`), a derived `RADIAL_ANGULAR_SPEED` (`2π / period`), and `MID` / `AMPLITUDE` constants. New `initialRadialPhase: Float = 0f` constructor param. New `@VisibleForTesting internal var radialPhase` (private set) advances by `RADIAL_ANGULAR_SPEED * deltaTime` each `update()` call. New `@VisibleForTesting internal val currentOrbitRadius` getter computes `MID + AMPLITUDE * sin(radialPhase)`. The proximity check inside `update()` uses `currentOrbitRadius` instead of the dropped `orbitRadius` constructor field.
- `presentation/battle/engine/GameEngine.kt` — `spawnOrbs()` no longer computes `radius = stats.range * 0.4f`. The single call site to `OrbEntity(...)` drops the `orbitRadius = radius` arg. 1-line deletion + 1 inline comment explaining the #54 fix.

6 new tests in `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/entities/OrbEntityTest.kt` (Robolectric + JUnit 4, mirroring DailyStepDaoTest / GameSurfaceViewTest):

- `R54 orb at inner sweep hits enemy at melee range from origin` — positive path. Phase = -π/2, R=25, enemy at (40, 0), distance 15 → hit expected.
- `R54 orb at outer sweep does NOT hit enemy at melee range` — negative path. Phase = +π/2, R=70, enemy at (40, 0), distance 30 → no hit.
- `R54 per-enemy HIT_COOLDOWN prevents double-hits within one cooldown window` — gating behaviour. First update lands the hit, second update 0.1s later (well below 0.5s cooldown) does NOT.
- `R54 orb does not hit dead enemy at melee range` — defensive guard for the `!enemy.isAlive` skip in the proximity loop.
- `R54 damage value is forwarded verbatim from constructor to onHitEnemy callback` — tests damage = 42.5 (arbitrary non-default) reaches the callback unchanged.
- `R54 radial oscillation traverses full MIN to MAX cycle over ORBIT_PERIOD_SEC` — samples the radius at 4 phase angles (0, π/2, π, 3π/2) and asserts the values match MID, MAX, MID, MIN. Catches any future regression where the oscillation gets collapsed to a constant radius.

Test count: 689 → 695 (+6). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL. Pure geometry fix — no schema change, no DI graph change, no public API change.

**This closes the v14 closed-track soak board.** All 3 real-bug issues filed during v14 soak are now fixed: #53 (card upgrade descriptions, merged via PR #56), #55 (lab mission credit, merged via PR #57), #54 (this PR). The 30 already-triaged backlog issues (#21–#51) remain folded into Plan V1X.

### Fix #55: Lab research completion when app closed does not credit COMPLETE_RESEARCH daily mission (2026-05-27)

Fixes [GitHub issue #55](https://github.com/JonWhiteFang/steps-of-babylon/issues/55). When research finished while the app was closed (timer elapsed in the background), the auto-completion path on next launch correctly incremented the research level but never advanced the daily COMPLETE_RESEARCH mission counter. Player observed: research level went up, daily Gem reward stayed locked.

**Bug shape (regression introduced by R3-03).** R3-03 (PR #7, commit `e628818`) extracted the daily-mission tick into `domain/usecase/UpdateCompleteResearchMissionProgress.kt` and gated the DAO write on `completedCount >= 1`. R3-03 correctly wired the new use case into all 3 `LabsViewModel` call sites (`init` / `rushResearch` / `freeRush`) — but missed the parallel call site in `HomeViewModel.init`, which had been running `CheckResearchCompletion` on every app launch since Plan 06 / Plan 16.

The bad sequence:
1. Research timer elapses while app is backgrounded.
2. Player opens app → `HomeViewModel.init` runs → calls `CheckResearchCompletion(labRepository)()` and **discards** the returned `List<ResearchType>` of completed research. Research level increments correctly in the DB. ✓ Mission tick never happens. ✗
3. Player navigates to Labs → `LabsViewModel.init` runs → `checkCompletion()` finds nothing to complete (already done in step 2) → returns `[]` → `updateMissionProgress(completedCount = 0)` early-returns at the R3-03 count gate. ✗

Mission credit permanently lost for that completion.

**Fix.** Mirror the LabsViewModel R3-03 pattern in `HomeViewModel`. Capture the `List<ResearchType>` returned by `CheckResearchCompletion` and pass `completed.size` to a new `private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)` field. `dailyMissionDao` was already injected for the existing `countClaimable` flow consumer, so no Hilt graph change.

```kotlin
// Pre-fix (HomeViewModel.kt:55)
CheckResearchCompletion(labRepository)()    // return value discarded

// Post-fix
val completed = CheckResearchCompletion(labRepository)()
updateMissionProgress(completedCount = completed.size)
```

2 new tests in `app/src/test/java/com/whitefang/stepsofbabylon/presentation/home/HomeViewModelTest.kt`:

- `R55 background research completion credits the COMPLETE_RESEARCH daily mission` — positive path. Sets up an expired research project + a fresh mission row, constructs the VM, asserts both that the mission is now ticked (progress=1, completed=true) AND that the research level itself incremented (verifies the `CheckResearchCompletion` half ran end-to-end, not just the mission tick).
- `R55 no in-flight research means COMPLETE_RESEARCH mission stays at progress 0 (R3-03 regression guard)` — defensive. Sets up a mission row with no active research at all, asserts the mission stays at progress=0. Would fail if any future change to the HomeViewModel call site regressed to passing a hardcoded count instead of the actual `completed.size`.

Test count: 687 → 689 (+2). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL. Pure call-site fix — no schema change, no DI graph change, no public API change beyond the additive use-case construction.

### Fix #53: Card upgrades not showing increased power (2026-05-27)

Fixes [GitHub issue #53](https://github.com/JonWhiteFang/steps-of-babylon/issues/53). Cards Screen rendered the same `+10% Defense Absolute` text for IRON_SKIN regardless of the player's actual card level, so a player upgrading from Lv1 to Lv2 saw no visual change even though the underlying gameplay effect (`ApplyCardEffects` → `CardType.effectAtLevel(level)`) correctly scaled. Bug existed since R4-08 (Cards copy-based 7-level progression, 2026-05-24).

**Bug shape (display-only).** `CardsScreen.kt:99` rendered `card.type.effectLv1` — the hardcoded Lv1 description string baked into the `CardType` enum. `CardsUiState.effectDescription` propagated the same constant via `CardsViewModel.kt:55`. Both UiState and Screen bypassed any level-awareness. Actual gameplay path via `ApplyCardEffects.kt:21` correctly used `card.type.effectAtLevel(card.level)` which interpolates between `valueLv1` and `valueLv7` over levels 1–7, so the stat *was* scaling as designed — players could not see it.

**Fix.** Added `CardType.effectDescriptionAtLevel(level): String` as the single source of truth for the level-aware user-facing description. The method derives its numbers from the same `effectAtLevel(level)` / `secondaryAtLevel(level)` formulas used by `ApplyCardEffects`, so display and gameplay can never drift. Format conventions:

- Primary value: integer truncation via `.toInt()` to match the existing Lv1 / Lv7 strings (`+10%`, `+42%`, `+2`, …). Actual gameplay uses the un-truncated `Double`; display intentionally drops fractional parts so 87.5 % reads as "+87 %".
- Secondary value (WALKING_FORTRESS / GLASS_CANNON debuffs): same `.toInt()` shape; the negative sign is part of the template since `secondaryAtLevel` always returns a positive magnitude.
- STEP_SURGE multiplier: one decimal place via a new `formatMultiplier` helper, with a trailing `.0` stripped so Lv1 reads "Earn 2x Gems this round" (matches `effectLv1` verbatim) while Lv4 reads "Earn 3.5x Gems this round". Pinned to `Locale.ROOT` so the decimal separator stays as `.` regardless of device locale (matches the `DescribeUpgradeEffect` convention from RO-11 #C).

`CardsViewModel.kt:55` `effectDescription = card.type.effectLv1` → `effectDescription = card.type.effectDescriptionAtLevel(card.level)`. `CardsScreen.kt:99` `Text(card.type.effectLv1, …)` → `Text(card.effectDescription, …)` (the screen now reads the UiState field instead of bypassing it).

31 new tests in `app/src/test/java/com/whitefang/stepsofbabylon/domain/model/CardTypeTest.kt`:

- 3 named tests per card × 9 cards = 27. For each card: Lv1 description matches `effectLv1` verbatim (no-regression contract — players who haven't upgraded see unchanged text); Lv4 shows the interpolated mid-curve value (catches stuck-on-Lv1 / jumps-to-Lv7 regressions); Lv7 description matches `effectLv7` verbatim (curve must hit the documented cap exactly).
- 4 cross-card invariants: `9 entries exist`; `every card produces a non-empty description at every level 1 to maxLevel` (catches future `when`-branch omissions); `Lv1 and Lv7 descriptions are different for every card` (direct regression guard for #53 — would have failed on `main` pre-fix); `Lv4 description is different from both Lv1 and Lv7 for cards with continuous progression` (excludes CHAIN_REACTION whose integer truncation is intentional).

Test count: 656 → 687 (+31). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL. Pure display fix — no schema change, no DI change, no public API change beyond the additive method on `CardType`.

### Doc sweep — sync current-state docs to schema v11 + 656 tests + Plan R4 complete + AAB v14 PASSED (2026-05-26 ~08:20 BST)

Full current-state doc sweep per the agent protocol's PR Task-List Convention. Drift accumulated since the v14 upload + smoke test pass + Plan V1X authoring: schema version (v9 / v10 stale references in 6 places), test count (646 / 649 stale references in 2 places), Plan R4 status (still marked in progress in AGENTS.md status checklist), Plan 31 narrative (still referenced "ship Plan R4 next" instead of v14 PASSED + closed-track soak), Plan V1X (not yet listed in AGENTS.md status checklist), STATE.md `Next actions` block (still listed R4-06/R4-07/R4-08 implementation steps).

**Files synced (10 docs):**

- `AGENTS.md` — Plan R4 status flipped `[ ]` → `[x]` with closure narrative; Plan 31 narrative refreshed to reflect AAB v14 PASSED smoke test + closed-track ≥14-day window resume; new `[ ] Plan V1X` status entry added.
- `README.md` — `# Unit tests (646 JVM tests)` → `656`; `(13 entities, schema v10)` → `(13 entities, schema v11)`.
- `.kiro/steering/source-files.md` — AppDatabase line `version 10` → `version 11`; Migrations line extended with `v9→10 ... + bossPsEarnedToday / ADR-0008 + ADR-0009, v10→11 for card_inventory copyCount aggregation / ADR-0010`.
- `.kiro/steering/structure.md` — AppDatabase row `version 9` → `version 11` with full description of v9→10 (R4-06 + R4-07) + v10→11 (R4-08) schema changes.
- `.kiro/steering/lib-room.md` — `Current schema version: 9` → `11`; `(13 entities, 13 DAOs, schema v9)` → `schema v11`.
- `docs/architecture.md` — `DatabaseModule ... schema v9` → `schema v11`.
- `docs/database-schema.md` — `Current schema version: 10` → `11`; new `MIGRATION_10_11` entry; v9→v10 description amended to mention `bossPsEarnedToday` (R4-07 / ADR-0009 folded into the same migration); CardInventory entity gained `copyCount` column row + level cap 5→7 + unique index note; DailyStepRecord entity gained `bossPsEarnedToday` row.
- `docs/agent/STATE.md` — DB version line refreshed to v11 with all 4 migrations registered; test count line `649 JVM tests` → `656 JVM tests`; `Next actions (explicit order)` block collapsed from 10 entries (5 stale R4-implementation + 4 external + 1 backlog) to 9 entries that match the current external-gating + V1X waves shape.
- `CHANGELOG.md` — this entry.
- `docs/agent/RUN_LOG.md` — new "Doc sweep" entry appended.

**`tech.md` audited but not modified** — all 23 dependency versions matched `gradle/libs.versions.toml` exactly. **`structure.md` lower sections audited but not modified** — module list still accurate.

**Historical artifacts deliberately not modified per agent protocol:**
- `RUN_LOG.md` prior entries (only appending new)
- `docs/plans/plan-R*.md`, `plan-R2*.md`, `plan-R3*.md`, `plan-R4*.md`, `plan-V1X*.md` (all historical at authoring date)
- `docs/external-reviews/*`, `devdocs/archaeology/*`, `smoke_tests/*`
- `docs/agent/DECISIONS/ADR-*.md` files
- `docs/StepsOfBabylon_GDD.md` (still describes Step Overdrive in §5.1 — R4-01 removal supersedes the GDD section but GDD edits are managed via dedicated sub-plans + ADRs, not routine sweeps; V1X-19 will reconcile)
- `docs/monetization.md`, `docs/battle-formulas.md`, `docs/step-tracking.md` (current-state design docs not in the standard sweep list; may have drift from R4-01 overdrive removal + R4-08 dust removal but absent major reader confusion the corrections wait for V1X-19 GDD reconciliation)

**No source / build / test impact — pure docs sync.** Test count stays at 656.

### AAB v14 on-device smoke test PASSED (2026-05-26 ~05:35 BST)

AAB v14 verified on an upgrade-from-v8 device. All three checklist items confirmed working:

1. **#19 fix — UW auto-trigger race resolved:** Equipped Golden Tower fires automatically when its cooldown reaches 0.
2. **#20 fix (Workshop half) — additive seeding works:** `RAPID_FIRE` visible and purchasable in Workshop → Attack tab on the upgrade-from-v8 install.
3. **#20 fix (Lab half) — additive seeding works:** `MULTISHOT_RESEARCH` and `BOUNCE_RESEARCH` visible in Labs on the upgrade-from-v8 install.

The #19 + #20 fix bundle (PR #52, commit `230309c`) is now confirmed working on the upgrade path that originally triggered the bugs in v12. **Closed-track ≥14-day window resumes from v14 effective 2026-05-26.** Earliest production-access application: 2026-06-09. Active gating activity shifts from on-device verification to tester recruitment + soak.

**No source / build / test impact — this is an external verification milestone.**

### Plan V1X (v1.x Roadmap) authored (2026-05-26 early hours)

New planning document `docs/plans/plan-V1X-roadmap.md` derived from the [2026-05-25 GitHub issue triage](docs/external-reviews/2026-05-25-issue-triage.md) of all 33 open issues. Captures the post-launch patch sequence as 29 sub-plans across 6 versioned releases (v1.0.1 polish → v1.0.2 audio → v1.1 testing-infra + simulation extraction → v1.2 cloud save + i18n) plus content/balance/docs ships and 9 strategic v2.x proposals. **Pure planning — no source / build / test impact.**

**4 user decisions locked 2026-05-26:**

1. CELESTIAL_GATE biome (#43) stays deferred (V1X-15) — added to `Biome.isComingSoon` flag.
2. ENEMY_INTEL research (#44) gets full v1.x design content (V1X-15b) — new 10-level Lab project: +2 %/level damage outer multiplier + UI overlays at L1 (next-wave composition) / L5 (per-enemy HP %) / L10 (boss-arrival countdown). Cost curve matches CASH_RESEARCH (8000 Steps × 1.5×, 4 hours × 1.10×).
3. GPS / Exploration Mode (#40) dropped from GDD (V1X-19) — ADR-0016 to be written.
4. STEP_MULTIPLIER asymptotic curve (#49) confirmed to land in same PR as RO-09 deferred #3 cross-validator unit-mismatch fix (V1X-18) — ADR-0015 to be written.

**Already-tracked debt items got full sub-plan specs per user request** (#35 atomic Gem/PS spend → V1X-10 + ADR-0011, #44 Coming Soon flags → V1X-15 + V1X-15b + ADR-0017, #45 ziggurat skin content → V1X-14, #51 per-kill scope migration → V1X-11). All four now have proper Files/Tests/Acceptance blocks instead of cross-references.

**ADRs to write when sub-plans land:** ADR-0011 through ADR-0017 (7 new ADRs).

**Schema migrations expected:** v11 → v12 (cloud save tracking columns in V1X-12).

**Test count target:** 656 → ~910 (≃40 instrumented + ~210 JVM net).

**Files modified:**

- `docs/plans/plan-V1X-roadmap.md` — NEW (~1300 lines)
- `docs/plans/master-plan.md` — V1X row added to plan index, dependency graph, status checklist; R4 status flipped to complete
- `AGENTS.md` — V1X registered in Plan Index + Dependency Graph + Critical Path
- `CHANGELOG.md` — this entry
- `docs/agent/STATE.md` — current objective rotated; previous-run history retained
- `docs/agent/RUN_LOG.md` — new entry appended

**No application code changes; no test count change.**

### versionCode 13 → 14 + AAB v14 build (2026-05-26 early hours)

Play Console rejected AAB v13 with "versionCode already used" — likely from an aborted/cached earlier upload registration; v13 was never visible on any release track. Bumped `versionCode` 13 → 14 in `app/build.gradle.kts` (commit `8b51cc5`). `./run-gradle.sh clean bundleRelease` BUILD SUCCESSFUL in 1m 26s. Output `app/build/outputs/bundle/release/app-release.aab` (~19 MB) signed with the upload keystore and verified by `jarsigner -verify`. Same content as v13 (the #19 + #20 fix bundle); only the `versionCode` differs.

### versionCode 12 → 13 + AAB v13 build (2026-05-25 evening)

Bumped `versionCode` 12 → 13 in `app/build.gradle.kts` (commit `9807f34`) following the merge of PR #52 (#19 + #20 fix bundle, commit `230309c`). `./run-gradle.sh clean bundleRelease` BUILD SUCCESSFUL in 1m 9s. Output `app/build/outputs/bundle/release/app-release.aab` (~19 MB) signed with the upload keystore and verified by `jarsigner -verify` (PKIX warning expected for self-signed upload keys; Play App Signing re-signs with Google's key after upload). Ready for upload to Play Console closed track.

**On-device verification needed post-upload:** equip Golden Tower → start a battle → confirm UW auto-triggers when its cooldown reaches 0; open Workshop → Attack tab → confirm `RAPID_FIRE` is listed; open Labs → confirm `MULTISHOT_RESEARCH` and `BOUNCE_RESEARCH` are listed.

### Fix #19 + #20: UW auto-trigger race + Workshop/Lab additive seeding (2026-05-25)

Two on-device-only bugs surfaced by the AAB v12 closed-track smoke test. Both ship in the same fix bundle on branch `fix/19-20-uw-autotrigger-and-seeding`.

**#19 — UWs not auto-triggering in battle (Golden Tower confirmed).** Root cause: race condition in `BattleScreen` composition. `LaunchedEffect(surfaceView) { viewModel.startPollingEngine(...) }` fired synchronously on first composition, calling `engine.initUWs(equippedWeapons)` while `equippedWeapons` was still `emptyList()` (the `BattleViewModel.init { viewModelScope.launch }` data load had not yet completed). The subsequent `LaunchedEffect(state.isLoading) { surfaceView.configure(...) }` ran `engine.init(...)` which clears `uwStates` again — and `startPollingEngine` was never re-invoked, leaving `uwStates` empty for the entire round and silently disabling the R4-06 auto-trigger gate. Fix: removed the early `LaunchedEffect(surfaceView)` and moved `startPollingEngine` into the existing `LaunchedEffect(state.isLoading)` block, ordered AFTER `surfaceView.configure(...)` so the configure-fired `engine.init` clears `uwStates` first and `startPollingEngine` then re-populates them via `engine.initUWs(equippedWeapons)` with the now-loaded list.

**#20 — RAPID_FIRE not visible in Workshop (and sibling: MULTISHOT_RESEARCH / BOUNCE_RESEARCH not visible in Labs).** Root cause: `WorkshopRepositoryImpl.ensureUpgradesExist()` and `LabRepositoryImpl.ensureResearchExists()` both used a "seed if completely empty" gate (`if (dao.getAll().first().isEmpty())`). Players upgrading from an older AAB whose tables already had rows for the then-existing enum entries hit the early-return branch, so new enums added in subsequent releases (R4-03 RAPID_FIRE; R4-02b MULTISHOT_RESEARCH / BOUNCE_RESEARCH) never got rows inserted. Fix: change both to the additive per-enum-name filter pattern already used by `CosmeticRepositoryImpl.ensureSeedData` — select only the missing entries and insert default-level rows while preserving existing rows' levels and active-research state.

**Files modified (3):** `BattleScreen.kt`, `WorkshopRepositoryImpl.kt`, `LabRepositoryImpl.kt`.

**Files created (5):** `docs/external-reviews/2026-05-25-issue-triage.md` (184-line triage of all 33 open issues), new fakes `FakeWorkshopDao` (49 lines) + `FakeLabDao` (39 lines), new repo-impl tests `WorkshopRepositoryImplTest` (4 tests, 149 lines) + `LabRepositoryImplTest` (3 tests, 118 lines).

**Tests:** 649 → 656 (+7 — 4 Workshop + 3 Lab). Both `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` BUILD SUCCESSFUL.

### Fix #18: Ad-rewarded card pack persistence (2026-05-25)

Fixed a bug where watching a reward ad for a free card pack would show the cards in the UI but never persist them to the database. Root cause: `OpenCardPack` relied on a stale `ownedCards` snapshot; when the INSERT hit the UNIQUE constraint on `cardType`, `OnConflictStrategy.IGNORE` silently dropped the write. Fix: query the DB directly via `cardRepository.hasCard(type)` instead of trusting the caller's snapshot.

### R4-08: Cards copy-based 7-level progression (2026-05-24)

Wave 3 of [Plan R4](docs/plans/plan-R4-feedback-bundle.md). Replaces Card Dust with copy-based upgrades. Cards now upgrade by collecting copies (3 COMMON / 4 RARE / 5 EPIC per level). Max level raised from 5 to 7 (~30% stronger at cap). ADR-0010 records the decision.

**Schema:** Room v10 → v11. `card_inventory` table recreated with `copyCount` column, duplicate rows aggregated, unique index on `cardType`.

**Key changes:**

- `CardType` — maxLevel 5→7, L7 values via linear extrapolation, SECOND_WIND capped at 100%.
- `CardRarity` — `copiesPerLevel` (3/4/5) added; `dustValue`/`upgradeDustPerLevel` deprecated.
- `OpenCardPack` — duplicates increment copy count; first card guaranteed at pack-tier rarity.
- `UpgradeCard` — copy-based (no longer needs PlayerRepository/dust).
- `SupplyDropReward` — `CARD_DUST` → `CARD_COPY`.
- `ClaimSupplyDrop` — now takes `CardRepository`; CARD_COPY awards 1 copy of random card.
- `CardsViewModel/UiState/Screen` — dust UI removed, shows copies/copiesNeeded.

**Tests:** 645 → 646 (+1 net). UpgradeCardTest, ClaimSupplyDropTest, GenerateSupplyDropTest, CardsViewModelTest, ApplyCardEffectsTest, CardBalanceTest all updated.

### R4-07: Boss-drop Power Stones (2026-05-24)

Third and final Wave 2 sub-plan of [Plan R4](docs/plans/plan-R4-feedback-bundle.md). Boss kills now award tier-scaled Power Stones (T1=1 PS, T2=2 PS, … T10=10 PS) with a 100 PS/day cap. Addresses the PS scarcity created by R4-06's expanded 3-path UW upgrade system. ADR-0009 records the decision.

**Schema:** `bossPsEarnedToday` column added to `daily_step_record` table (folded into existing v9→v10 migration).

**New files:**

- `domain/usecase/AwardBossPowerStones.kt` — use case mirroring `AwardBattleSteps` pattern.
- `docs/agent/DECISIONS/ADR-0009-boss-drop-power-stones.md`

**Modified files:**

- `data/local/DailyStepRecordEntity.kt` — +`bossPsEarnedToday: Long` column.
- `data/local/DailyStepDao.kt` — +`getBossPsEarnedToday`, +`incrementBossPs`, +`creditBossPowerStonesAtomic`.
- `data/local/Migrations.kt` — `MIGRATION_9_10` extended with `ALTER TABLE ADD COLUMN bossPsEarnedToday`.
- `presentation/battle/engine/GameEngine.kt` — +`onBossKilled` callback, fires in `handleEnemyDeath` for BOSS type.
- `presentation/battle/BattleViewModel.kt` — +`wireBossKilledCallback`, calls use case + emits FloatingText.
- `presentation/battle/effects/FloatingText.kt` — +`PS_COLOR` constant (purple).

**Tests:** +12 (633 → 645). 9 in `AwardBossPowerStonesTest` + 3 in `GameEngineTest`.

### R4-06: UW auto-trigger + per-path upgrades (2026-05-24)

Second Wave 2 sub-plan of [Plan R4 (Internal Soak Feedback Bundle)](docs/plans/plan-R4-feedback-bundle.md). Redesigns Ultimate Weapons from a single-level upgrade with manual activation to a 3-path upgrade system (DAMAGE, SECONDARY, COOLDOWN) with automatic cooldown-based triggering. Source feedback: *"UWs should auto-trigger on cooldown and have per-path upgrades instead of a single level."* ADR-0008 records the decision.

**Schema:** Room v9 → v10. `ultimate_weapon_state` table recreated via `MIGRATION_9_10` (recreate-table dance) — single `level` column replaced by `damageLevel`, `secondaryLevel`, `cooldownLevel`, `isUnlocked` columns.

**New domain model:**

- `domain/model/UWPath.kt` — 3-value enum: DAMAGE, SECONDARY, COOLDOWN.

**Rewritten files (source, 14):**

- `domain/model/UltimateWeaponType.kt` — per-path L1/L10 spec + `valueAtLevel`/`cooldownAtLevel`/`damageAtLevel`/`secondaryAtLevel`/`costForPath` methods.
- `domain/model/OwnedWeapon.kt` — 6 fields: type, damageLevel, secondaryLevel, cooldownLevel, isUnlocked, isEquipped + `levelOf(path)` accessor.
- `domain/usecase/UpgradeUltimateWeapon.kt` — takes `path: UWPath` parameter for per-path upgrade.
- `domain/usecase/UnlockUltimateWeapon.kt` — sets `isUnlocked` flag (no longer increments level).
- `domain/repository/UltimateWeaponRepository.kt` — `upgradePathLevel` replaces `upgradeWeapon`.
- `data/local/UltimateWeaponStateEntity.kt` — 4 new columns (damageLevel, secondaryLevel, cooldownLevel, isUnlocked) replacing single `level`.
- `data/local/UltimateWeaponDao.kt` — +`markUnlocked` +`updateDamageLevel`/`updateSecondaryLevel`/`updateCooldownLevel`.
- `data/local/Migrations.kt` — +`MIGRATION_9_10` recreate-table dance for `ultimate_weapon_state`.
- `data/local/AppDatabase.kt` — version 9 → 10.
- `data/repository/UltimateWeaponRepositoryImpl.kt` — `toDomain` rewrite for 4-column entity → 6-field domain model.
- `presentation/battle/engine/GameEngine.kt` — UWState 4-field struct, auto-trigger on cooldown, per-path `activateUW`, `chronoSlowFactor` instance field (companion `CHRONO_SLOW_FACTOR` constant removed).
- `presentation/battle/ui/UltimateWeaponBar.kt` — passive display (no clickable activation buttons).
- `presentation/weapons/UltimateWeaponScreen.kt` — 3 per-path Upgrade buttons per UW card.
- `presentation/weapons/UltimateWeaponViewModel.kt` — `UWPathDisplay` + `UWDisplayInfo.paths` for per-path UI state.

**BattleViewModel:** `activateUW` method removed (auto-trigger replaces manual activation).

**Files changed (test, 1):**

- `fakes/FakeUltimateWeaponRepository.kt` — rewritten for new `upgradePathLevel` interface + per-path level tracking.

**Test count:** 626 → 633 (+7). `./run-gradle.sh testDebugUnitTest` BUILD SUCCESSFUL.

**ADR:** ADR-0008 — UW per-path upgrade system with auto-trigger.

### R4-03: Rapid Fire upgrade (2026-05-23)

First Wave 2 sub-plan of [Plan R4 (Internal Soak Feedback Bundle)](docs/plans/plan-R4-feedback-bundle.md). Adds a new `RAPID_FIRE` Workshop upgrade in the ATTACK category that fires a periodic attack-speed burst during a wave's SPAWNING phase. Source feedback: *"Add 'Rapid Fire', upgradable duration and speed, auto triggers at time interval (max upgrade should be constant)."* Locked decision (2026-05-22T20:27 BST): periodic-pulse engine pattern mirroring `RECOVERY_PACKAGES`.

**Spec:**

| Parameter | L1 | L10 |
|---|---|---|
| Cost (Steps) | 2,000 (baseCost) | scaling 1.18× per level |
| maxLevel | 10 | — |
| Interval (s between bursts) | 60 | 30 (matches duration → permanent) |
| Burst duration (s) | 5 | 30 (matches interval → permanent) |
| Attack-speed multiplier during burst | 2.0× | 3.0× |

Linear interpolation between L1 and L10 for all three burst parameters. At L10 duration matches interval (both 30 s), so the next burst fires before the previous one expires — the player gets an effectively permanent +3.0× attack-speed buff.

**Files added:**

- `domain/model/RapidFireSchedule.kt` — NEW interpolation helper. Centralises the (interval, duration, multiplier) per-level math so `GameEngine.tickRapidFire` and `DescribeUpgradeEffect.formatRapidFire` always agree on the same numbers. Pure Kotlin, JVM-test-friendly.

**Files changed (source, 4):**

- `domain/model/UpgradeType.kt` — added `RAPID_FIRE(UpgradeCategory.ATTACK)` enum entry + `UpgradeConfig(2_000, 1.18, 10, 1.0, "Periodic attack-speed burst (60s/5s/2.0× → permanent/3.0× at max)")` row in the configs map. ATTACK group is now 9 entries (was 8); total enum is 24 entries (was 23). MULTISHOT / BOUNCE_SHOT remain `isWorkshopVisible = false` from R4-02b; RAPID_FIRE keeps the default `true`.
- `presentation/battle/entities/ZigguratEntity.kt` — new `@Volatile var rapidFireMultiplier: Float = 1f` field; `attackInterval` becomes `(1.0 / (stats.attackSpeed * rapidFireMultiplier)).toFloat()`. The multiplier is set by `GameEngine.tickRapidFire` while a burst is active and reset to `1f` when the burst expires or the wave enters cooldown phase.
- `presentation/battle/engine/GameEngine.kt` — added `RapidFireSchedule` import, two new private fields (`rapidFireTimer`, `rapidFireActiveRemaining`), reset of both fields in `init()`, new `tickRapidFire(deltaTime)` private method called from `update()` immediately after `tickRecoveryPackages`. Mirrors `tickRecoveryPackages` shape: SPAWNING-phase only, level-0 no-op, COOLDOWN phase resets all state. Order inside the method matters — active-decrement runs *before* the timer-fire check so at L10 (interval == duration) the multiplier transitions from one burst to the next within a single tick with no observable 1-frame gap. On burst-fire emits a yellow-gold `"RAPID FIRE!"` `FloatingText` above the ziggurat for visual feedback.
- `domain/usecase/DescribeUpgradeEffect.kt` — new `RAPID_FIRE` branch in the `format` `when` reads the workshop level only (RAPID_FIRE is not an in-round purchase). New `formatRapidFire(level)` private helper renders the readout shape: `"inactive"` at L0; `"{i}s/{d}s/{m}×"` between L1 and L9 (e.g. `"60s/5s/2.0×"` at L1, `"33s/27s/2.9×"` at L9); `"permanent/{m}×"` at L10 once `RapidFireSchedule.isPermanent` returns true.
- `presentation/battle/ui/InRoundUpgradeMenu.kt` — added `UpgradeType.RAPID_FIRE` to the `hiddenInRound` set alongside `STEP_MULTIPLIER` and `RECOVERY_PACKAGES`. RAPID_FIRE is a passive periodic effect like RECOVERY_PACKAGES; an in-round Cash entry would imply per-round purchases of the burst trigger which doesn't match the "persistent attack-speed burst" feature design.

**Files changed (test, 4):**

- `presentation/battle/engine/GameEngineTest.kt` — 6 new R4-03 tests + 2 new reflective helpers (`invokeTickRapidFire(eng, deltaTime)` mirrors `invokeTickRecovery`; `setWavePhase(eng, phase)` flips the engine's private `WaveSpawner.phase` for the cooldown-reset test). Tests cover: level-0 no-op, L1 fires after 60 s, L1 burst expires after 5 s, L5 multiplier interpolation produces 2.444× (×0.001 tolerance), L10 produces permanent buff across burst boundaries, COOLDOWN phase resets the timer.
- `domain/usecase/DescribeUpgradeEffectTest.kt` — 4 new R4-03 readout tests covering L0 "inactive" with L1 next-preview, L1 "60s/5s/2.0×" with L2 next, L9 finite triple readout with L10 next as permanent, L10 collapses to `"permanent/3.0×"` with `next == null`. The existing smoke test ("every visible upgrade type produces a non-empty current readout at L0") implicitly covers RAPID_FIRE since the new `formatRapidFire(0)` returns `"inactive"`.
- `domain/model/UpgradeTypeTest.kt` — entry-count assertion 23 → 24; ATTACK count assertion 8 → 9.
- `presentation/workshop/WorkshopViewModelTest.kt` — R4-02b filter test bumped 6 → 7 ATTACK upgrades (sanity assertion).

**Test count:** 616 → 626 (+10). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL on first run.

**Acceptance:**

- Purchasing RAPID_FIRE L1 produces a visible 5-second yellow-gold `"RAPID FIRE!"` floating text above the ziggurat every 60 s of SPAWNING-phase, with a clearly-visible ≈2× projectile rate during the burst. → Engine math regression-guarded; on-device check pending Wave 2 AAB.
- L10 produces continuous `"RAPID FIRE!"` re-emission every 30 s and a sustained ≈3× projectile rate. → Regression-guarded by the L10-permanent-buff GameEngineTest.
- `"Now → Next"` readout in DescribeUpgradeEffect shows clear progression from L0 inactive → L1 "60s/5s/2.0×" → L9 "33s/27s/2.9×" → L10 "permanent/3.0×". → 4 readout tests guard the format.
- ATTACK is now 9-entry total in the enum, but the in-round upgrade menu still only shows the 6 stat-bearing ATTACK upgrades (DAMAGE / ATTACK_SPEED / CRITICAL_CHANCE / CRITICAL_FACTOR / RANGE / DAMAGE_PER_METER) plus MULTISHOT and BOUNCE_SHOT (still in-round-Cash purchasable from R4-02b). RAPID_FIRE is hidden from in-round.

**ADR:** not warranted — Rapid Fire is an additive Workshop upgrade with a centralised interpolation helper. The pattern (`tickRapidFire` mirrors `tickRecoveryPackages`) is established; no new architectural decision.

### Plan R4 Wave 1 — AAB v9 verified on internal track (2026-05-23)

Milestone: end-of-Wave-1 build/upload/verify cycle complete. AAB v9 (versionCode 9, commit `cbbb525`) bundles all four Wave 1 sub-plans:

- **R4-01** Remove Step Overdrive (PR #9, commit `e375d14`, 627 → 615 tests)
- **R4-02** Multishot/Bounce 4-level scaling (PR #10, commit `b2f7cd5`, 615 tests unchanged — 6 rewritten in place)
- **R4-02b** Multishot/Bounce Labs research path amendment (PR #12, 615 → 616 tests)
- **R4-04** `Icons.Filled.Upgrade` button icon swap (PR #11, 616 tests unchanged)

Uploaded to Play Console internal track 2026-05-23; on-device smoke test PASSED 2026-05-23. AAB v8 (versionCode 8, built earlier 2026-05-23 ahead of the R4-02b amendment) was discarded without upload. Wave 2 (R4-03 Rapid Fire + R4-06 UW auto-trigger + per-path upgrades + R4-07 boss-drop Power Stones; combined Room migration v9→v10; ADR-0008 + ADR-0009) starts next.

### R4-02b: Multishot/Bounce Labs research path (2026-05-23)

Amendment to R4-02 in response to user request: *"multishot and bounce shot should be upgraded via labs (same cost but steps instead of cash)"*. Adds a Labs research path for both upgrades and bumps the in-round Cash purchase max level from 4 to 10. Removes both upgrades from the Workshop screen (they're now Labs-research / in-round-Cash only). Final stat caps bumped to keep both 10-level paths economically meaningful.

**Spec change:**

| Field | Pre-R4-02b | Post-R4-02b |
|---|---|---|
| `UpgradeType.MULTISHOT.maxLevel` | 4 (in-round Cash) | **10 (in-round Cash)** |
| `UpgradeType.BOUNCE_SHOT.maxLevel` | 4 (in-round Cash) | **10 (in-round Cash)** |
| Workshop visibility | yes (Workshop ATTACK list) | **no (`isWorkshopVisible = false`)** |
| `multishotTargets` cap | 5 (1 base + 4 max) | **11 (1 base + 10 max)** |
| `bounceCount` cap | 4 | **10** |
| `ResearchType.MULTISHOT_RESEARCH` | did not exist | **NEW: 5,000 Steps base × 1.5×, 6 h base × 1.10×, 10 levels, +1 target/lvl** |
| `ResearchType.BOUNCE_RESEARCH` | did not exist | **NEW: 8,000 Steps base × 1.5×, 6 h base × 1.10×, 10 levels, +1 bounce/lvl** |

**Effective progression paths post-R4-02b:**
- *Quick (Cash, mid-round)*: in-round upgrade menu, +1 target/bounce per level, up to 10 levels per round, resets at round end.
- *Permanent (Steps + real-time, Labs)*: Labs research, +1 target/bounce per level, up to 10 levels, persists across rounds.
- Both stack additively. Final cap of 11 multishot / 10 bounces means once the Labs path is fully maxed, the in-round path becomes overflow ("insurance" if Labs hasn't reached 10 yet) but still useful for mid-progression players.

**Files changed (source, 4):**
- `domain/model/UpgradeType.kt` — added `isWorkshopVisible: Boolean = true` constructor flag (defaults true for the 21 standard upgrades); set `false` for `MULTISHOT` and `BOUNCE_SHOT`. Bumped both `UpgradeConfig.maxLevel` from 4 to 10. Updated descriptions to mention the dual-path nature.
- `domain/model/ResearchType.kt` — added 2 new entries: `MULTISHOT_RESEARCH` (5,000 Steps base, 6 h base, 10 levels, `costScaling = 1.5`, `timeScaling` default 1.10), `BOUNCE_RESEARCH` (8,000 Steps base, otherwise identical). Class KDoc updated to reflect 12 total enums (was 10).
- `domain/usecase/ResolveStats.kt` — cap formulas updated:
  ```kotlin
  multishotTargets = min(1 + total(MULTISHOT) + (labLevels[MULTISHOT_RESEARCH] ?: 0), 11)
  bounceCount = min(total(BOUNCE_SHOT) + (labLevels[BOUNCE_RESEARCH] ?: 0), 10)
  ```
- `presentation/workshop/WorkshopViewModel.kt` — added `&& type.isWorkshopVisible` predicate to the existing category filter. MULTISHOT/BOUNCE_SHOT disappear from the Workshop screen; the existing `hiddenUpgrades` set (STEP_MULTIPLIER, RECOVERY_PACKAGES) stays untouched. The DAO continues to seed Workshop rows for all `UpgradeType.entries` so `ResolveStats.total(MULTISHOT)` keeps reading 0 from the unpurchasable Workshop side.

**Files changed (test, 4):**
- `domain/usecase/ResolveStatsTest.kt` — 4 tests rewritten in place. Two pairs: cap-coverage (`R402b multishot per-level scaling stacks in-round + Labs with cap 11` / `R402b bounce ... cap 10`) covering Labs-alone, in-round-alone, and both-stacked paths plus the legacy-level defensive clamp; in-round-vs-workshop sum (`R402b in-round multishot sums workshop and in-round levels` / `R402b in-round bounce ...`) updated for the new caps.
- `domain/usecase/DescribeUpgradeEffectTest.kt` — unchanged (existing tests check L0/L1/L2, all well below the new 11/10 cap).
- `domain/model/ResearchTypeTest.kt` — set-equality contract still holds (only AUTO_UPGRADE_AI + ENEMY_INTEL flagged `isComingSoon`); comment updated to note 10 wired enums (was 8) including the new MULTISHOT_RESEARCH + BOUNCE_RESEARCH.
- `balance/CostCurveTest.kt` — dropped MULTISHOT and BOUNCE_SHOT from `premiumUpgrades` set (they're no longer Workshop upgrades); `standard upgrades` test now skips `!isWorkshopVisible`; `cheapest upgrades` filter pre-applied. The two upgrades' Cash and Steps cost curves are exercised by the in-round/Labs balance contracts respectively.
- `presentation/workshop/WorkshopViewModelTest.kt` — new test `R402b MULTISHOT and BOUNCE_SHOT are filtered out of Workshop UI` verifies neither appears in the ATTACK list and that the remaining 6 ATTACK upgrades still surface.

**Test count:** 615 → 616 (+1; the new WorkshopViewModelTest filter test). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL.

**AAB strategy:** v8 (built earlier today, not yet uploaded) is discarded. After R4-02b merges, versionCode bumps 8 → 9 and a fresh end-of-Wave-1 AAB v9 will bundle R4-01 + R4-02 + R4-02b + R4-04 for the internal-track upload + on-device smoke test.

### R4-04: in-round upgrade button icon (2026-05-23)

Third and final Wave 1 sub-plan of [Plan R4 (Internal Soak Feedback Bundle)](docs/plans/plan-R4-feedback-bundle.md). Replaces the Unicode glyph `⬆` on the in-round upgrade button with the Material `Icons.Filled.Upgrade` vector icon for clearer affordance. Source feedback: "Make round upgrade button have a more obvious icon."

**Files changed:**
- `gradle/libs.versions.toml` — added `compose-material-icons-extended` library entry. The existing `compose-material-icons` covers `material-icons-core` (small built-in set, used for `ArrowBack`); the extended package supplies the full Material catalogue. R8 minification effectively tree-shakes unused icons in release builds, so the runtime cost in the shipped AAB is bounded.
- `app/build.gradle.kts` — added `implementation(libs.compose.material.icons.extended)` next to the existing core dep.
- `presentation/battle/BattleScreen.kt` — added `import androidx.compose.material.icons.filled.Upgrade`; swapped `Text("⬆", color = Color.White)` for `Icon(Icons.Filled.Upgrade, contentDescription = null, tint = Color.White)`. The button's outer `Modifier.semantics { contentDescription = "Upgrades" }` already declares the accessible label, so the inner `Icon` uses `contentDescription = null` to avoid TalkBack reading the label twice.

**Tests affected:** none. Pure Compose UI surface, no JVM-testable change. Verification is build clean (`testDebugUnitTest` + `assembleDebug` + `assembleRelease` all BUILD SUCCESSFUL) plus on-device check on the next AAB.

**Test count:** 615 → 615 (unchanged).

**Acceptance:** in-round upgrade button shows the Material upgrade icon. On-device check pending Wave 1 AAB.

**Wave 1 complete.** All three Wave 1 sub-plans (R4-01 / R4-02 / R4-04) have landed on `main`. End-of-Wave-1 AAB build (versionCode 7 → 8) is the next step before Wave 2 (R4-03 + R4-06 + R4-07) begins.

### R4-02: Multishot/Bounce 4-level scaling (2026-05-23)

Second Wave 1 sub-plan of [Plan R4 (Internal Soak Feedback Bundle)](docs/plans/plan-R4-feedback-bundle.md). Replaces the per-20-levels / per-15-levels grind formulas for `MULTISHOT` / `BOUNCE_SHOT` with flat 4-level upgrades that grant +1 target / +1 bounce per level. Source feedback: "Multishot and bounce shot shouldn't be multiple levels for no benefit, make 1 level give 1 bonus but make it an expensive upgrade."

**Spec change:**

| Upgrade | Pre-R4-02 | Post-R4-02 |
|---|---|---|
| `MULTISHOT` | maxLevel=100, baseCost=500, scaling=1.25, +1 target per 20 levels (cap 5) | **maxLevel=4, baseCost=5,000, scaling=1.5, +1 target per level (cap 5)** |
| `BOUNCE_SHOT` | maxLevel=60, baseCost=1,000, scaling=1.30, +1 bounce per 15 levels (cap 4) | **maxLevel=4, baseCost=8,000, scaling=1.5, +1 bounce per level (cap 4)** |

Resulting cost curve to max each upgrade is exactly 4 purchases:

| Upgrade | L1 (cost 0) | L2 (cost 1) | L3 (cost 2) | L4 (cost 3) | Total |
|---|---|---|---|---|---|
| `MULTISHOT` | 5,000 | 7,500 | 11,250 | 16,875 | **40,625 Steps** |
| `BOUNCE_SHOT` | 8,000 | 12,000 | 18,000 | 27,000 | **65,000 Steps** |

**Files changed:**

- `domain/model/UpgradeType.kt` — `MULTISHOT` / `BOUNCE_SHOT` `UpgradeConfig` rows replaced with the new 4-level shape.
- `domain/usecase/ResolveStats.kt` — `multishotTargets` formula simplified from `min(1 + floor(total(MULTISHOT)/20.0).toInt(), 5)` to `min(1 + total(MULTISHOT), 5)`; `bounceCount` from `min(floor(total(BOUNCE_SHOT)/15.0).toInt(), 4)` to `min(total(BOUNCE_SHOT), 4)`. The `min(…)` caps stay in place defensively in case a legacy install has a pre-R4-02 level value above the new `maxLevel`. Unused `kotlin.math.floor` import dropped.
- `domain/usecase/DescribeUpgradeEffect.kt` — unchanged. The existing `formatTargets` / `formatBounces` helpers handle any integer; the call sites read `stats.multishotTargets` / `stats.bounceCount` which are now driven by the simpler formula automatically.
- `domain/usecase/ApplyCardEffects.kt` — unchanged. `CHAIN_REACTION` card's `bounceCount += value` stacks additively *after* the `min(…, 4)` cap in `ResolveStats`, so a max-level `CHAIN_REACTION` (+4 bounces) on top of max-level `BOUNCE_SHOT` (4) still produces the documented 8 total bounces.

**Files edited (test):**
- `domain/usecase/ResolveStatsTest.kt` — 4 tests rewritten: `multishot thresholds` → `R402 multishot per-level scaling` (asserts 1→5 targets at levels 0–4, plus the legacy-level-100 defensive cap); `bounce thresholds` → `R402 bounce per-level scaling` (asserts 0→4 bounces at levels 0–4, plus the legacy-level-60 defensive cap); `RO08 in-round multishot sums levels for the per-20 threshold` → `R402 in-round multishot sums workshop and in-round levels` (additive ws + ir, cap at 5); `RO08 in-round bounce sums levels for the per-15 threshold` → `R402 in-round bounce sums workshop and in-round levels` (additive ws + ir, cap at 4).
- `domain/usecase/DescribeUpgradeEffectTest.kt` — 2 tests rewritten: `MULTISHOT shows targets with threshold pluralisation` and `BOUNCE_SHOT shows bounces with threshold pluralisation` updated to the per-level scaling (level 1 → level 2 transitions, not level 20 → level 21).
- `balance/CostCurveTest.kt` — unchanged. Its `premium upgrades are expensive but not unreachable at level 10` test uses `if (maxLevel != null && maxLevel < 10) maxLevel - 1 else 10` so the test level for both `MULTISHOT` and `BOUNCE_SHOT` is now 3 (=maxLevel-1). At level 3, MULTISHOT costs 16,875 and BOUNCE_SHOT costs 27,000 — both well under the 100,000 ceiling.

**Test count:** 615 → 615 (unchanged — 6 tests rewritten in place, no net additions). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL.

**Acceptance criteria (from `plan-R4-feedback-bundle.md` § R4-02):**
- [x] L1 of `MULTISHOT` visibly fires at 2 enemies (test asserts `multishotTargets == 2` at MULTISHOT=1).
- [x] L1 of `BOUNCE_SHOT` visibly bounces once (test asserts `bounceCount == 1` at BOUNCE_SHOT=1).
- [x] Costs reach 5,000 / 8,000 Steps at L1 (formula: `ceil(baseCost × scaling^0) = baseCost`).
- [x] Costs reach ~16,875 / ~27,000 at L4 (formula: `ceil(baseCost × 1.5^3) = ceil(baseCost × 3.375)`).
- [x] `CHAIN_REACTION` card stacks additively on top of upgrade levels (unchanged behaviour, `ApplyCardEffects` runs after `ResolveStats`).

### R4-01: remove Step Overdrive (2026-05-23)

First Wave 1 sub-plan of [Plan R4 (Internal Soak Feedback Bundle)](docs/plans/plan-R4-feedback-bundle.md). Deletes the entire Step Overdrive feature — 4 enums, 4 standalone files, ~600 LOC, ~50 references across the battle stack — in response to user-soak feedback that the mechanic was "not fun and wastes steps for not a lot of benefit". R4-06 (Wave 2) will redesign the Ultimate Weapon system with auto-trigger + per-path upgrades; the visual feedback hooks Overdrive used (`overdriveColor` / `overdriveProgress`) are removed here and will be replaced as part of that redesign.

**Files deleted:**
- `domain/model/OverdriveType.kt` (4-entry enum: ASSAULT/FORTRESS/FORTUNE/SURGE)
- `domain/usecase/ActivateOverdrive.kt` (validation use case + sealed `Result` type)
- `presentation/battle/effects/OverdriveAuraEffect.kt` (4-color particle aura)
- `presentation/battle/ui/OverdriveMenu.kt` (Compose 4-card selection menu)
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/ActivateOverdriveTest.kt` (-4 tests)

**Files edited (production):**
- `domain/model/RoundState.kt` — dropped `overdriveUsed` and `overdriveType` fields (transient, no DB schema impact)
- `presentation/battle/engine/GameEngine.kt` — removed `OverdriveType` + `OverdriveAuraEffect` imports, `activeOverdrive` / `overdriveTimeRemaining` / `preOverdriveStats` / `overdriveAuraEffect` fields, the `activateOverdrive(type, baseStats)` method (4-branch when), the `expireOverdrive()` method, the `update()` block that ticked the overdrive timer, and the `init()` reset block's overdrive lines. **GOLDEN_ZIGGURAT simplified:** activation now writes `fortuneMultiplier = 5.0` unconditionally (was `coerceAtLeast(5.0)` to coexist with FORTUNE); expiry writes `fortuneMultiplier = 1.0` unconditionally (was the 4-state `if (activeOverdrive == FORTUNE) 3.0 else 1.0` cross-overdrive guard from RO-09 #2). Also dropped the `zig.overdriveColor / overdriveProgress` side effects from GOLDEN activation/expiry.
- `presentation/battle/BattleViewModel.kt` — removed `OverdriveType` + `ActivateOverdrive` imports, `activateOverdriveUseCase` field, `activateOverdrive(type)` method, `toggleOverdriveMenu()` method, `activeOverdriveType` / `overdriveTimeRemaining` reads in the polling loop, `showOverdriveMenu = false` from `runEndRoundPersistence` UI state push, and the `showOverdriveMenu = false` cross-clear in `toggleUpgradeMenu()`.
- `presentation/battle/BattleScreen.kt` — removed `OverdriveMenu` import, the `state.activeOverdriveType?.let { ... }` status line in the top-left HUD, the Overdrive button in the bottom control row (now 5 buttons: 3× speed + Pause + Upgrade, was 6 with Overdrive), and the `OverdriveMenu` invocation block. Updated the R3-04 horizontal-scroll comment to reflect the new 5-button reality while keeping the scroll safety net for future R4 additions (e.g. R4-03 Rapid Fire).
- `presentation/battle/BattleUiState.kt` — dropped `overdriveUsed`, `activeOverdriveType`, `overdriveTimeRemaining`, `showOverdriveMenu` fields and the `OverdriveType` import.
- `presentation/battle/entities/ZigguratEntity.kt` — dropped `overdriveColor` / `overdriveProgress` fields, the timer-bar render block, and the `timerBgPaint` / `timerFillPaint` paints (used only by the timer bar). Updated 2 KDoc comments to reflect post-R4-01 reality.
- `presentation/battle/GameSurfaceView.kt` + `presentation/battle/engine/EnemyScaler.kt` — comment-only updates to drop "overdrive" / "Fortune overdrive" references.

**Files edited (test):**
- `presentation/battle/engine/GameEngineTest.kt` — removed `OverdriveType` import; deleted 3 RO-08 Overdrive-propagation tests (`ASSAULT propagates 2x attackSpeed`, `FORTRESS propagates healthRegen`, `expireOverdrive restores baseline stats`); collapsed 4 RO-09 #2 cross-overdrive guards into 2 simpler GOLDEN-only tests (`R401 GOLDEN_ZIGGURAT activation sets fortuneMultiplier to 5x`, `R401 GOLDEN_ZIGGURAT expiry resets fortuneMultiplier to 1x`); dropped `invokeExpireOverdrive` / `readAttackInterval` / `zigStatsForTest` helpers (only used by the removed tests). Net: −5 tests in this file.
- `balance/UWOverdriveBalanceTest.kt` → renamed `UWBalanceTest.kt` via `git mv`; rewrote with KDoc explaining the rename; dropped `overdrive costs represent 3 to 10 minutes of walking` and `surge overdrive value scales with equipped UW count` (−2 tests). 3 surviving tests cover UW cooldown spacing, Death Wave Lv 5 vs wave 50 boss, and GOLDEN 5× cash bounding.
- `balance/CashEconomyTest.kt` — dropped `fortune overdrive 3x cash for one wave is strong but not game-breaking` (−1 test).
- `presentation/battle/GameSurfaceViewTest.kt` — comment-only update to drop "UW / overdrive state" reference.

**ADR-0003 amendment.** Appended a new `## Amendments` section noting that the Rationale's "no Fortune-overdrive multiplier" constraint became vacuously true post-R4-01. The flat-rate invariant for battle Steps is unchanged — GOLDEN_ZIGGURAT's `fortuneMultiplier` still doesn't multiply battle Steps, even though R4-06 will redesign GOLDEN's per-path upgrades.

**Test count:** 627 → 615 (−12 net: −4 ActivateOverdriveTest, −5 GameEngineTest (−7 then +2 collapsed GOLDEN-only), −2 UWBalanceTest, −1 CashEconomyTest). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh assembleDebug` both BUILD SUCCESSFUL.

**Zero behaviour change for any non-Overdrive feature.** The bottom control row is now 5 buttons; the in-flight ziggurat no longer shows an overdrive timer bar. The 5 surviving R4 sub-plans (R4-02 Multishot/Bounce 4-level, R4-03 Rapid Fire, R4-04 Upgrade icon, R4-05 Help screen, R4-06 UW per-path, R4-07 boss-drop PS, R4-08 Card copy progression) will land in subsequent waves; this PR is the first and largest deletion of the bundle.

### R3-04: battle bottom control-bar overflow fix (2026-05-19)

Fixes [GitHub issue #3](https://github.com/JonWhiteFang/steps-of-babylon/issues/3). On a Pixel 6 (411dp wide) the 6th button (Overdrive) of the bottom control row in `BattleScreen` was cut off by the right edge of the screen.

**Bug shape.** The bottom control row was a plain `Row` with no horizontal-scroll fallback, so on devices narrower than the row's intrinsic width (~380dp + 24dp horizontal padding + button spacing) the right-most button overflowed and clipped against the screen edge. The row also only had `padding(bottom = 24.dp)` so on devices with a tall gesture handle inset it sat partially under the swipe-up area.

**Fix.** Two `Modifier` additions on the existing `Row` in `presentation/battle/BattleScreen.kt`:

- `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` (placed before the bottom 24dp padding) lifts the entire row clear of the system gesture inset before any other padding is applied.
- `Modifier.horizontalScroll(rememberScrollState())` (placed before the `background`) lets the row scroll horizontally on screens too narrow to show all 6 buttons. Order matters here: the rounded-corner background pill is now drawn on the inner scrolling content so it follows the visible buttons rather than filling the full viewport.

No behaviour change to any individual button. 5 imports added (`horizontalScroll`, `rememberScrollState`, `WindowInsets`, `navigationBars`, `windowInsetsPadding`).

**No JVM regression test.** This is a pure Compose UI change. The project test suite is JVM-only — there are no instrumented Compose UI tests — so verification is build clean (`testDebugUnitTest` + `bundleRelease` both BUILD SUCCESSFUL) plus on-device check on the next AAB. Test count stays at **627**.

### R3-03: COMPLETE_RESEARCH mission false-trigger fix (2026-05-19)

Fixes [GitHub issue #1](https://github.com/JonWhiteFang/steps-of-babylon/issues/1). Opening the Labs screen with no in-flight research previously advanced the daily COMPLETE_RESEARCH mission to progress=1, completed=true regardless of whether anything actually completed.

**Bug shape.** `LabsViewModel.init` ran a coroutine that called `labRepository.ensureResearchExists()`, then `checkCompletion()`, then `updateResearchMission()` unconditionally. The private `updateResearchMission()` helper looked up today's COMPLETE_RESEARCH mission row and called `dailyMissionDao.updateProgress(m.id, 1, true)` whenever a row was found and not already completed — ignoring the actual return value of `CheckResearchCompletion`. Players reasonably interpreted this as the daily mission silently auto-completing the moment they opened Labs.

**Fix.** Extracted the mission-tick logic into a new `domain/usecase/UpdateCompleteResearchMissionProgress.kt` use case (matches the existing project convention for cross-layer use cases like `TrackDailyLogin`, `GenerateDailyMissions`). The new use case:

- Takes `DailyMissionDao` and exposes `invoke(completedCount: Int, today: String = LocalDate.now().toString())`.
- Gates the DAO write on `completedCount >= 1` (early return otherwise) — the entire R3-03 fix.
- Replaces the historical "always set to target, completed=true" write with an additive-with-cap increment: `newProgress = (m.progress + completedCount).coerceAtMost(m.target)`. For COMPLETE_RESEARCH the target is 1, so the clamp degenerates to the historical single-completion behaviour while gracefully handling multi-completion auto-batches (e.g. 3 expired research projects all auto-completing on app launch correctly count as one completed mission, not three increments overshooting the target).
- Preserves the prior fail-open contract: missing today's row, already-claimed, already-completed, and DAO exceptions are all silent no-ops.

All 3 LabsViewModel call sites updated to use the new use case with explicit counts: `init` passes `completed.size` from `CheckResearchCompletion`, `rushResearch` passes 1 on `Result.Rushed`, `freeRush` passes 1 after `labRepository.completeResearch(type)`. The now-redundant private `updateResearchMission()` helper and the `DailyMissionType` import were removed.

5 new tests in `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/UpdateCompleteResearchMissionProgressTest.kt`:
- `R303 does NOT tick when completedCount is 0` (R3-03 acceptance — the negative case from issue #1)
- `R303 does NOT tick when completedCount is negative` (defensive guard against caller bugs)
- `R303 ticks to 1 when completedCount is 1` (positive: the auto-completion path on Labs entry)
- `R303 caps progress at target when multiple research complete in one batch` (regression guard against the additive math overshooting)
- `R303 is a no-op when no mission row exists for the given date` (defensive: idempotency)

Why a separate use case rather than inlining `if (completed.isNotEmpty())` in `LabsViewModel.init`? Three reasons. First, it keeps the gating in one testable place — anyone calling the use case (current 3 call sites + any future entry point) gets the gating for free, so the bug shape can't reappear at a different call site. Second, it avoids a hard test problem: `LabsViewModel.init` runs a `while(true) { delay(1000) }` ticker on `viewModelScope` (a separate `SupervisorJob` not reachable from the test scope), which makes any in-VM regression test that doesn't carefully cancel the scope hang `runTest`'s scheduler-drain forever. Third, it matches the project convention (`TrackDailyLogin(dailyLoginDao, playerRepository)`, `GenerateDailyMissions(dailyMissionDao)` etc. live as use cases that take DAOs).

Test count: 622 → 627 (+5). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` both BUILD SUCCESSFUL.

### R3-02: THORN_DAMAGE wiring + LIFESTEAL visibility (2026-05-19)

Fixes [GitHub issue #4](https://github.com/JonWhiteFang/steps-of-babylon/issues/4). Two related defects in the THORN_DAMAGE / LIFESTEAL Defense upgrades surfaced during the v5 internal-track on-device smoke test.

**THORN_DAMAGE never reflected damage.** `ResolvedStats.thornPercent` was correctly plumbed by `ResolveStats` (1 % per level) and consumed by `GameEngine.applyThorn` via `attacker.takeDamage(rawDamage * stats.thornPercent * conditions.thornMultiplier)`. But every call site of `applyDamageToZiggurat` passed `attacker = null`. The `EnemyEntity.onMeleeHit -> WaveSpawner.onMeleeHit -> GameEngine` callback chain was typed `(Double) -> Unit` and dropped the enemy reference, so `applyThorn` early-returned for every melee hit. Players saw zero reflected damage regardless of THORN_DAMAGE upgrade level.

**LIFESTEAL heal was imperceptible at low levels.** The math was correct (`zig.currentHp += damage * stats.lifestealPercent`, `Double`-typed so sub-1-HP heals are conserved), but at low levels the heal was sub-pixel: Lv 1 (0.2 % lifesteal) on base damage 10 = 0.02 HP per hit — an invisible HP-bar nudge users could not perceive.

**Fix — THORN:**
- Changed `EnemyEntity.onMeleeHit: ((Double) -> Unit)?` to `((EnemyEntity, Double) -> Unit)?`; `update()` invokes `onMeleeHit?.invoke(this, damage)` so consumers see the attacker.
- Changed `WaveSpawner.onMeleeHit: (Double) -> Unit` to `(EnemyEntity, Double) -> Unit`.
- Flipped 2 `GameEngine` call sites (initial `WaveSpawner` wiring + SCATTER child enemy spawn site) from `{ _, dmg -> applyDamageToZiggurat(dmg, null) }` to `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }` so `applyThorn` actually fires.
- Ranged-projectile path stays `null` per GDD wording 'damage reflected to attackers' — the firing enemy isn't the attacker, the projectile is, and projectiles aren't living entities.

**Fix — LIFESTEAL** (per `plan-R3-remediation-3.md` § R3-02 option (b)):
- Added `private var lifestealAccumulator: Double = 0.0` field on `GameEngine`, reset in `init()`.
- Extracted the existing in-place heal at `onProjectileHitEnemy` and `onOrbHitEnemy` into a new private `applyLifesteal(healAmount)` helper (mirrors `applyThorn`'s shape) that does math + accumulator + visible-text emit in one place.
- Each time the accumulator crosses an integer HP threshold a `FloatingText("+$visibleHp HP", STEP_COLOR)` indicator is queued above the ziggurat, mirroring the RECOVERY_PACKAGES feedback pattern.
- Math is identical to pre-R3-02; only the visible feedback is new. At cap (15 % × base damage 10 = 1.5 HP / hit) every shot emits `+1 HP`; at Lv 1 it takes ~50 hits to accumulate one visible burst, matching the expectation that low-level lifesteal is weak but observable.

3 new tests in `app/src/test/.../GameEngineTest.kt` under a new R3-02 section:
- `R302 THORN_DAMAGE reflects damage on melee hit via plumbed attacker reference`
- `R302 THORN_DAMAGE scales linearly with thornPercent` (direct value assertions catch the pre-fix degenerate case where both reflects are zero — 0 × 5 == 0 passes a ratio check trivially)
- `R302 LIFESTEAL emits visible floating text when accumulated heal crosses 1 HP`

Plus 5 reflective helpers: `freshEngineWithStats`, `invokeOnMeleeHit` (reads `WaveSpawner.onMeleeHit` field), `createDummyAttacker`, `invokeOnProjectileHitEnemy`, `readPendingFloatingTextSnippets`. `WaveSpawnerTest` updated to compile against the new 2-arg `onMeleeHit` signature.

Test count: 619 → 622 (+3). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` both BUILD SUCCESSFUL.

### R3-01: battle backgrounding state preservation (2026-05-19)

Fixes [GitHub issue #2](https://github.com/JonWhiteFang/steps-of-babylon/issues/2). Backgrounding the app mid-round (Recents, screen lock, incoming notification) previously wiped wave / cash / kills / `elapsedTimeSeconds` because `GameSurfaceView.surfaceCreated` and `surfaceChanged` unconditionally called `engine.init` on every Android lifecycle event, and the new `GameLoopThread` started with default `speedMultiplier = 1f` / `isPaused = false` because `setSpeedMultiplier` / `setPaused` wrote only to `gameThread` which was null between `surfaceDestroyed` and the next `surfaceCreated`.

Fix:
- Extracted `@VisibleForTesting internal fun GameSurfaceView.initEngineIfNeeded()` that gates `engine.init` on `!engine.hasWaveProgress()`. The `hasWaveProgress()` helper already existed (added by RO-03 / B.3 PR 2 for `BattleViewModel.onCleared` mid-nav persistence) — exactly the right signal at the wrong call site, now wired in.
- Added `@Volatile internal var pendingSpeed: Float = 1f` and `pendingPaused: Boolean = false` written by `setSpeedMultiplier` / `setPaused` alongside the live thread; `surfaceCreated` reads them when constructing the new thread so a recreated game loop inherits the player's UI selections.

4 new tests in `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/GameSurfaceViewTest.kt` (Robolectric):
- `R3-01 surface recreation preserves engine progress mid-round`
- `R3-01 setSpeedMultiplier persists pendingSpeed for next thread`
- `R3-01 setPaused persists pendingPaused for next thread`
- `R3-01 initEngineIfNeeded does run engine init when no progress yet` (inverse / no-regression guard)

Deliberately deferred: `BattleScreen` `ON_RESUME` re-apply handler (defence in depth). With the `GameSurfaceView` fix the new thread always inherits correct state; the secondary handler would only matter if Compose dropped state across recomposition, which is hard to unit-test without a Compose harness.

Test count: 615 → 619 (+4). `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` both BUILD SUCCESSFUL.

### Plan R3 scaffolding (2026-05-19)

The v5 internal-track on-device smoke test (2026-05-19 morning) surfaced 4 gameplay bugs filed as GitHub issues #1–#4 on `JonWhiteFang/steps-of-babylon`. This PR formalises the response as Plan R3 (Remediation 3) — the GitHub-issue-driven analog of the external-review-driven Plan R and Plan R2.

GitHub-side scaffolding (already live on the remote at the time of this commit):
- 11 new labels: `severity:blocker`, `severity:major`, `severity:minor`, `area:battle`, `area:missions`, `area:economy`, `area:billing`, `area:ui`, `needs-more-info`, `in-progress`, `regression-guard-needed`.
- New milestone `v1.0.0 closed-test gate` (#1) with all 4 open issues attached.
- `needs-more-info` clarification comment posted on issue #3 (7-day window).

Repo-side:
- New plan file `docs/plans/plan-R3-remediation-3.md` (182 lines, 4 sub-plans mirroring the R2 shape: Sub-Plan Index → Dependency Graph → per-sub-plan detail block → Execution Notes → Priority Tiers → Open Questions).
- `docs/plans/master-plan.md` and `AGENTS.md` updated to register R3 in the Plan Index, dependency graph, critical path, and Status checklist (master plan entry count 34 → 35).
- `docs/agent/STATE.md` and `docs/agent/RUN_LOG.md` updated.

No source / test / schema impact. Test count remains 615 at this commit.

### README audit + LICENSE creation (2026-05-19)

Follow-up to the docs-sweep PR earlier the same day. The user requested an audit of `README.md` for fitness-for-function. Audit found 10 gaps across P0–P3 severities; this PR lands all of them in a single pass plus a new `LICENSE` file. Pure-docs PR — no source / test / schema impact.

#### New file

- **`LICENSE`** (17 lines) — proprietary, all rights reserved. Copyright © 2026 Jon White Fang. Source code is proprietary; the compiled app is distributed via Google Play under the standard user license. Third-party libraries retain their own licenses. Contact: jonwhitefang@gmail.com. Closes the P0 "no license declaration" finding which would have flagged on any external read of the repo (closed-track recruitment, GitHub-via-Play-Store-listing visibility, etc.).

#### `README.md` rewrite (75 → 127 lines)

| Pri | Fix |
|---|---|
| P0 | New `## Status` section: "Version 1.0.0 (versionCode 7) — pre-launch. Internal-track build verified on a real device 2026-05-18… 627 JVM unit tests green." Links `STATE.md` + `CHANGELOG.md`. Closes the "silent on current phase" finding. |
| P0 | New `## Privacy` section with 3 links: hosted privacy policy URL, hosted data-deletion URL, canonical `docs/release/privacy-policy.md`. Names SQLCipher encryption-at-rest and the Play Billing / AdMob exception explicitly. Closes the privacy-link omission. |
| P0 | New `## License` section pointing at the new `LICENSE` file. |
| P1 | `## Tech Stack` one-liner expanded from 6 items to 11: added SQLCipher (database encryption), Health Connect (paired with the existing Android Sensor API mention), Google Play Billing v8, Google Mobile Ads SDK v25, UMP v4 (consent). Now mentions the canonical `tech.md` version table alongside `AGENTS.md`. |
| P1 | `## Key Documentation` table gained 3 rows: `AGENTS.md`, `CHANGELOG.md`, and `Privacy Policy`. Reordered to put `AGENTS.md` and `CHANGELOG.md` near the top since they're the most-referenced live docs. |
| P1 | `## Project Structure` block went one level deeper under `data/` (previously a single `# Room entities, DAOs, repositories impl` line, now broken out into `local/`, `repository/`, `sensor/`, `healthconnect/`, `billing/`, `ads/` with brief descriptions including the C.5 PR 3 / C.6 PR 3 "sole binding" status). `di/` row now lists all 8 modules; `service/` row now mentions the widget. |
| P2 | New `## Where to start` 1-line pointer: read `START_HERE.md` → `STATE.md` → the relevant plan in `docs/plans/`. |
| P2 | The "Note: Instrumented tests…" comment-on-a-comment moved out of the code block into a proper sentence below it. |
| P2 | `## Setup` gained a keystore prerequisite callout: `assembleRelease` / `bundleRelease` need `release/upload-keystore.jks` + `keystore.properties` (both gitignored). Points at `plan-31-walkthrough.md`. The bare `./gradlew assembleRelease` line was dropped from `## Build & Run`; replaced with `./gradlew bundleRelease` (the actual production task) marked with the keystore caveat. |
| P3 | Feature graphic embedded at the top of the README via `![Steps of Babylon](docs/release/store-assets/play-store-feature-graphic-1024x500.png)`. Lifts perceived quality on GitHub. The image already shipped in commit history from 2026-05-13. |

#### Verification

- All 10 cited document links resolve (`StepsOfBabylon_GDD.md`, `architecture.md`, `AGENTS.md`, `CHANGELOG.md`, `master-plan.md`, `battle-formulas.md`, `database-schema.md`, `step-tracking.md`, `monetization.md`, `privacy-policy.md`).
- All build commands match `tech.md` and the on-disk `gradle/libs.versions.toml`.
- `run-gradle.sh` recreation snippet now includes the comment line that ships in the actual on-disk file (was a small drift previously).
- No source / test / schema impact. Test count stays at 615.

### Docs sweep — 12 stale live-docs fixed post-RO-12 + versionCode bump (2026-05-19)

Pure-docs PR. Sweep across every live current-state doc (per agent protocol PR Task-List Convention) found 12 drift items left over from the RO-08 / RO-09 / RO-11 / RO-12 / C.5 PR 3 / C.6 PR 3 / Plan 31 PR B sequence and the `1796b4c` versionCode bump. No source / test / schema impact. Historical artifacts (`devdocs/**`, `smoke_tests/**`, `docs/external-reviews/**`, `plan-R*.md`, prior `RUN_LOG` / `CHANGELOG` entries) deliberately left frozen per `.kiro/steering/11-agent-protocol.md`.

| # | File | Drift fixed |
|---|---|---|
| 1 | `README.md` | "33-entry development roadmap" → "34-entry" (Plans 01–31 + 10b + R + R2 = 34). |
| 2 | `.kiro/steering/lib-room.md` | "AppDatabase.kt in `data/local/` (12 entities, 12 DAOs)" → "(13 entities, 13 DAOs, schema v9)". |
| 3 | `.kiro/steering/structure.md` | AppDatabase row "(12 entities, 12 DAOs, version 8)" → "(13 entities, 13 DAOs, version 9; `billing_receipt` added in C.5 PR 1)". |
| 4 | `.kiro/steering/tech.md` | Play Billing row updated post-C.5 PR 3: was "flag-gated `@Binds` via `BuildConfig.USE_REAL_BILLING`; debug=stub, release=real", now "sole `BillingManager` binding for debug + release as of C.5 PR 3 (`StubBillingManager` deleted; `BuildConfig.USE_REAL_BILLING` removed)". |
| 5 | `docs/architecture.md` | Hilt module list refreshed: `BillingModule` / `AdModule` no longer reference stubs; sibling `BillingInternalModule` / `AdInternalModule` adapter bindings documented; new `CoroutineScopeModule` (B.3 PR 2 / RO-03) row added; `DatabaseModule` row gained "(13 entities, 13 DAOs, schema v9)". |
| 6 | `docs/monetization.md` | What's-Out-of-Scope lost the stale "live formatted-price display from `ProductDetails.priceDisplay`" bullet (shipped in Plan 31 PR B 2026-05-18); What's-Implemented gained a live-prices line; Out-of-Scope replaced with the two intentional v1.x deferrals from PR B (no refresh on app resume / locale change; no retry on transient network failure). |
| 7 | `docs/plans/master-plan.md` | Status checklist Plan 26 row "(stub implementation — real SDK integration deferred)" → "(real Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end via C.5 PR 1–3 + C.6 PR 1–3; on-device verification PASSED 2026-05-18)". |
| 8 | `docs/plans/plan-31-play-console.md` | Status changed from "Not Started" to a comprehensive "In Progress (Phases A–G landed: …)" line; Dependencies updated to include Plan R (Tier 1) + Plan R2 (Tier 1). Task 4 "Integrate real Google Play Billing Library" + "Integrate real AdMob SDK" struck through with C.5 PR 1–3 / C.6 PR 1–3 done-references. |
| 9 | `docs/battle-formulas.md` | Heaviest edit. Stats Resolution gained the 3rd multiplicative Lab tier (RO-11 #A.1). Damage Calculation gained `DAMAGE_RESEARCH` + `CRITICAL_RESEARCH` outer multipliers. Health & Regen gained `HEALTH_RESEARCH` + `REGEN_RESEARCH` multipliers. Cash Economy gained `cashResearchMultiplier` on both `cashFromKill` and `cashPerWave`. UW Cooldown Scaling gained `uwCooldownMultiplier`. Step Multiplier section rewritten to reflect the live combined `STEP_MULTIPLIER` (Workshop) + `STEP_EFFICIENCY` (Lab) formula under shared +100 % cap, dropping the obsolete "hidden from the Workshop UI (see Remediation R04)" footnote. Two new sections appended: "Lab Research — Wave Skip" (RO-11 #B.1) and "Lab Research — Coming Soon" (RO-11 #B.2). |
| 10 | `docs/step-tracking.md` | Data-flow box now includes the `STEP_MULTIPLIER` (Workshop) + `STEP_EFFICIENCY` (Lab) bonus stage between `StepVelocityAnalyzer` and the 50 k daily ceiling; added an explanatory note clarifying the bonus is sensor-path only and references battle-formulas.md § Step Multiplier. |
| 11 | `AGENTS.md` | Version line bumped "versionCode 5" → "versionCode 6" (commit `1796b4c`); status-checklist Plan 26 row + parallelizable-branches Monetization row both updated to "real Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end". |
| 12 | `docs/agent/STATE.md` | Current objective + Top priorities item #1 + Next actions step #1 all dropped the now-completed "Bump versionCode 5 → 6" sub-step; immediate action is now `bundleRelease` + sign + upload. |

#### Verification

- Re-grep against the original 12 stale strings now matches only frozen historical artifacts (`devdocs/archaeology/**`, `devdocs/foundations/**`, `devdocs/evolution/**`, `smoke_tests/**`, `docs/plans/plan-26-monetization.md`, `docs/external-reviews/**`, prior `RUN_LOG.md` + `CHANGELOG.md` entries) — zero remaining matches in current-state docs.
- `git diff --stat`: 12 files changed, 90 insertions, 42 deletions.
- No source / test / schema impact. Test count stays at 615.

### RO-12 — In-round stat drift bugfix bundle (2026-05-19)

Discovered during v5 internal-track on-device smoke test (Wave 4 screenshot, 06:21 BST 2026-05-19): the `RO-11 #C` "Now → Next" readout rendered correctly on the DEFENSE tab but **the HEALTH "Now" value disagreed with the live ziggurat HP bar by ~5 %** — a drift consistent with `HEALTH_RESEARCH` Lv 1 being included in the readout but stripped from the engine after the player's first in-round purchase.

Root-cause investigation surfaced **3 real stat-resolution bugs plus 1 display-precision bug**, all in the in-round upgrade pipeline:

| # | Bug | RO-11-introduced? |
|---|---|---|
| 1 | `BattleViewModel.purchaseInRoundUpgrade` called `resolveStats(workshopLevels, inRoundLevels)` without `labLevels` — stripped lab research bonuses on every in-round purchase. | **Yes** — `labLevels` arg added to `ResolveStats` in RO-11 #A.1; one call site missed during the wiring pass. |
| 2 | Same site did not re-apply `ApplyCardEffects` after recomputing stats — stripped card effects (WALKING_FORTRESS, GLASS_CANNON, IRON_SKIN, SHARP_SHOOTER, VAMPIRIC_TOUCH, CHAIN_REACTION) on every in-round purchase. | **No** — pre-existing since cards landed (Plan 17), but unmasked by RO-11 making the drift visible by stacking lab on top. |
| 3 | `DescribeUpgradeEffect` did not apply card effects either, so the readout drifted from the live engine when any stat-modifying card was equipped. | **Yes** — introduced by RO-11 #C alongside the use case. |
| 4 | `HEALTH_REGEN` readout used `%.1f/s` format. At base ~1.3/s with +2 %/level, per-level delta is ~0.026/s and rounds away — readout showed "Now: 1.3/s → 1.3/s" for a real upgrade, making it look like a no-op. | **Yes** — display-precision oversight in RO-11 #C format string. |

All 4 are closed-test blockers in different ways: Bugs 1 + 2 directly defeat RO-11 acceptance check #1 ("DAMAGE_RESEARCH L5 visibly hits harder") any time a player buys an in-round upgrade; Bug 3 shows the same kind of drift to the player visually; Bug 4 makes a real upgrade look broken. Discovered + fixed in a single morning before promoting v5 → closed.

**Fix (single PR, no schema / DI / public-API changes).**

- **`BattleViewModel.kt`:** new private helper `resolveCurrentStats(inRound: Map<UpgradeType, Int>): ResolvedStats` that runs the full live-engine pipeline `resolveStats(workshop, inRound, lab) → applyCardEffects(stats, equippedCards).stats`. `purchaseInRoundUpgrade` now routes through this helper, so lab + card multipliers survive every in-round purchase. The card-effect *side outputs* (`cashBonusPercent`, `secondWindHpPercent`, `gemMultiplier`) are static for the round and remain computed once in `init` / `playAgain`. `describeEffect` now threads `equippedCards` through to `DescribeUpgradeEffect` so the readout stays in lockstep with the engine.
- **`DescribeUpgradeEffect.kt`:** constructor gains optional `applyCardEffects: ApplyCardEffects = ApplyCardEffects()`; `invoke` gains optional `equippedCards: List<OwnedCard> = emptyList()`. `format` now post-applies `applyCardEffects(raw, equippedCards).stats` before formatting so the readout mirrors the engine pipeline. Default `emptyList()` preserves pre-RO-12 behaviour for the 25 existing test call sites and any future Workshop-screen call site that doesn't have card context.
- **`HEALTH_REGEN` format:** `%.1f/s` → `%.2f/s`. One character. Other format strings unchanged because they have larger per-level magnitudes (e.g., `KNOCKBACK` per-level delta is +0.1 px which is visible at 1 decimal).

**Test coverage: 609 → 615 (+6 net).**

- `BattleViewModelTest` +3 with new `installEngineForPurchase` reflective helper:
  - `RO12 in-round HEALTH purchase preserves HEALTH_RESEARCH lab bonus` — Bug 1 direct guard. Post-purchase `maxHealth = BASE × 1.20 (lab L4) × 1.03 (in-round L1)` instead of pre-fix `BASE × 1.03`.
  - `RO12 in-round HEALTH purchase preserves WALKING_FORTRESS card bonus` — Bug 2 direct guard. Post-purchase `maxHealth = BASE × 1.50 (WF L1) × 1.03` instead of pre-fix `BASE × 1.03`.
  - `RO12 in-round HEALTH purchase preserves both lab AND card bonuses stacked` — combined regression matching the screenshot scenario (`BASE × 1.20 × 1.03 × 1.50 = 1854 HP` post-fix vs `1030 HP` pre-fix — 824 HP drift in a single screen).
- `DescribeUpgradeEffectTest` +3:
  - Existing `HEALTH_REGEN` test renamed to `"2-decimal format"` with expected value `"1.20/s"`.
  - `RO12 HEALTH_REGEN Lv 0 to Lv 1 produces a visibly different readout` — Bug 4 direct guard. Asserts `"1.00/s" → "1.02/s"` with `assertNotEquals` for clarity.
  - `RO12 HEALTH readout reflects equipped WALKING_FORTRESS card multiplier` — Bug 3 direct guard. ws=5 HEALTH + WALKING_FORTRESS Lv 1 → `"1725 HP"` (pre-fix would have been `"1150 HP"`).
  - `RO12 HEALTH readout with no cards equipped is unchanged from pre-RO12 baseline` — default-arg behaviour preserved for the 25 existing tests.

**Verification.**

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, **615 tests pass (609 → 615, +6)**, zero failures.
- versionCode bump (5 → 6) deferred to the upload PR so this PR is reviewable in isolation.

**On-device verification (post-merge).** Once v6 hits the internal track, repeat the v5 smoke test plus 5 RO-12-specific checks per `docs/plans/plan-RO-12-in-round-stat-drift.md` § 8: HP bar matches HEALTH "Now" with HEALTH_RESEARCH owned, HP bar still matches after any in-round purchase, HP bar matches with WALKING_FORTRESS equipped, HEALTH_REGEN row shows 2-decimal readout that visibly changes Lv 0 → Lv 1, and RO-11 acceptance check #1 still passes after multiple in-round purchases.

### RO-11 — Labs research wiring + in-round upgrade-effect readout (2026-05-19)

Pre-RO-11 all 10 `ResearchType` enums were dead — declared with effect descriptions, costing Steps + real-time + Gems (rush) to complete, displayed correctly on the Labs screen, but never consumed by any combat-path / step-credit / cash-economy / UW-cooldown class. Same shape as RO-08 #1 (`STEP_MULTIPLIER` + `RECOVERY_PACKAGES`) and RO-09 #1 (`CHRONO_FIELD`), wider blast radius (entire Labs system instead of single enum). Closes the dead-enum gap that would have surfaced as "research does nothing" closed-test feedback within the first round of any tester at level 3+ `DAMAGE_RESEARCH`. Discovery context: user reported on top of the Labs gap that the in-round upgrade menu didn't show numerical effect of each purchase (originally tracked as RO-10, absorbed as Phase C of RO-11).

**Phase A — 7 simple multipliers wired (commits `d3dc4d6` / `a4eca72` / `14b0665`, +9 tests).**

- `DAMAGE_RESEARCH` / `HEALTH_RESEARCH` / `CRITICAL_RESEARCH` / `REGEN_RESEARCH` attach as a third multiplicative tier inside `ResolveStats.invoke` (signature gains optional `labLevels: Map<ResearchType, Int> = emptyMap()`). Formula: `base × (1 + ws × per) × (1 + ir × per) × (1 + lab × labPer)`.
- `CASH_RESEARCH` (per-kill cash + wave-end cash) and `UW_COOLDOWN` (cooldown set in `activateUW`) wire onto `GameEngine` via two new `@Volatile` fields (`cashResearchMultiplier: Double = 1.0`, `uwCooldownMultiplier: Float = 1f`). `BattleViewModel` reads `LabRepository.observeAllResearch()` once per round in `init` / `playAgain` and pushes the multipliers via private helpers; `UWSlotInfo.cooldownTotal` mirrors `eng.uwCooldownMultiplier` so the cooldown ring-fill UI tracks the actual cooldown.
- `STEP_EFFICIENCY` (per-walking-credit) wires onto `DailyStepManager` (constructor gains `LabRepository` as 16th param). `applyStepMultiplier` now reads BOTH the workshop `STEP_MULTIPLIER` level AND the lab `STEP_EFFICIENCY` level on every credit; combines additively under the existing `STEP_MULTIPLIER_CAP = 1.0` (per plan-RO-11 § 9 open question #3 — shared cap, not separate). New `STEP_EFFICIENCY_PER_LEVEL = 0.02` constant. Activity minutes intentionally still excluded for the same GDD wording rationale that excluded them from `STEP_MULTIPLIER` in RO-08.

**Phase B — `WAVE_SKIP` wired + AUTO_UPGRADE_AI / ENEMY_INTEL gated (commits `28337e5` / `6b754c9`, +3 tests).**

- `WaveSpawner` gains `private val startWave: Int = 1` constructor param; `var currentWave: Int = startWave` replaces the hardcoded `= 1`. `GameEngine.init()` accepts optional `startWave: Int = 1`, coerces to ≥1, threads into both the WaveSpawner constructor AND `triggerWaveAnnouncement(...)` so the HUD slide-in shows the correct opening wave. `GameSurfaceView.configure(...)` extends with `startWave: Int = 1` and threads through all 3 internal `engine.init` call sites (`configure` / `surfaceCreated` / `surfaceChanged`). `BattleViewModel` exposes `var startWave: Int = 1; private set` (mirrors `var labLevels` shape from Phase A) computed via `(1 + WAVE_SKIP_level).coerceAtLeast(1)` — L0 = wave 1 (current behaviour), L10 = wave 11. `BattleScreen` reads `viewModel.startWave` and pushes through `surfaceView.configure(...)` when isLoading flips to false.
- `ResearchType` gains optional `isComingSoon: Boolean = false` constructor parameter; `AUTO_UPGRADE_AI` and `ENEMY_INTEL` set `isComingSoon = true` and have descriptions updated to *"Reserved for v1.x — research progress preserved"*. `LabsScreen` renders a "COMING SOON" badge in the row header (tertiary color, takes priority over MAX / level chip) and suppresses the entire Start / Rush / Progress UI block for those rows. `LabsViewModel.startResearch` adds a defensive belt-and-braces guard: early-return + snackbar message if a Coming Soon type ever reaches the VM via a future entry point. Research progress preserved on the deferred two so the v1.x real implementations pick up where players left off.

**Phase C — `DescribeUpgradeEffect` use case + in-round readout (commit `93f6ae8`, +25 tests).**

User report (originally RO-10): *"It is hard to see the upgrades and how they affect the tower. Can we have this visible, such as damage in round shown by the upgrade, and the same with all of them?"* — Pre-fix the in-round upgrade menu showed only the description text and cost button. Now every visible row shows a third Text below the description in Gold (#D4A843):

```
DAMAGE                                                   [$50]
Lv 3 · +2% base damage per level
Now: 12.5 dmg → Next: 13.0 dmg                          ← new
```

- New use case `domain/usecase/DescribeUpgradeEffect.kt`. `UpgradeEffectReadout(current: String, next: String?)` — `next` is `null` when at maxLevel (UI renders "Now: X (MAX)" instead of arrow). Operator-fun signature mirrors `ResolveStats` and shares its instance for drift-free preview. Stat-bearing upgrades call `resolveStats` twice; cash utilities + hidden upgrades compute from `UpgradeConfig.effectPerLevel` directly with cap clamps. Format strings pinned to `Locale.ROOT` so de-DE / fr-FR users don't see "12,5 dmg" diverging from the rest of the English-only v1.0 strings (plan § 9 open question #5 — localization is v2.0 effort).
- Lab-research outer multipliers (Phase A) flow through the same `format` path so a tester with `DAMAGE_RESEARCH L5` sees the bonus reflected in the readout — closing the visibility loop the user originally asked for.
- `BattleViewModel.describeEffect(type)` is the public seam; `InRoundUpgradeMenu` accepts an optional `describeEffect: (UpgradeType) -> UpgradeEffectReadout` parameter with no-op fallback. `BattleScreen` passes `viewModel::describeEffect` through.

**Test coverage: 572 → 609 (+37 new tests, vs ~36 plan target).**

- `ResolveStatsTest` +4 (per stat-bearing research type outer multiplier).
- `GameEngineTest` +2 (`cashResearchMultiplier` 2.0× kill cash + `uwCooldownMultiplier` 0.55× CHAIN_LIGHTNING cooldown).
- `DailyStepManagerTest` +3 (STEP_EFFICIENCY alone, STEP_MULTIPLIER + STEP_EFFICIENCY combined, shared +100 % cap).
- `WaveSpawnerTest` +1 new file (currentWave reads from startWave constructor; L11 path).
- `BattleViewModelTest` +1 (WAVE_SKIP L5 → startWave = 6 propagation).
- `ResearchTypeTest` +1 new file (set-equality contract: only AUTO_UPGRADE_AI + ENEMY_INTEL are `isComingSoon`).
- `DescribeUpgradeEffectTest` +25 new file (per-upgrade-type Now → Next readout coverage including caps + lab-research stacking + smoke test).

**Open questions resolved against plan-RO-11 § 9.**

| # | Question | Resolution |
|---|---|---|
| 1 | `WAVE_SKIP` semantics | "start at wave 1 + level" — L0 = wave 1 (current), L10 = wave 11 |
| 2 | Lab × workshop stacking | Multiplicative — matches RO-08 ws × ir × lab pattern |
| 3 | `STEP_EFFICIENCY` + `STEP_MULTIPLIER` cap | Shared +100 % cap, not separate |
| 4 | Steps already spent on deferred enums | No refund; research progress preserved for v1.x |
| 5 | Readout localization | English-only via `Locale.ROOT`; localization is v2.0 effort |

**Verification.**

- `./run-gradle.sh test --rerun-tasks` — BUILD SUCCESSFUL, 609 tests pass (was 572, +37). Zero failures across all suites.
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL, clean R8 minify + lint vital + signing.
- **Internal track release (2026-05-19 morning):** versionCode bumped 4 → 5 (commit `734beaa`); signed AAB ~18 MB uploaded to Play Console internal track; v5 release notes for Play Console + closed-track tester recruitment landed in `docs/release/` (commit `d9f48e3`). Awaiting on-device smoke test of the 8 RO-11 acceptance checks before closed-track promotion.

**Out of scope / deferred to v1.x backlog.**

- `AUTO_UPGRADE_AI` real implementation (~2 days; auto-purchase coroutine + optimal-upgrade definition + UI toggle).
- `ENEMY_INTEL` real implementation (HP-bar gating + wave preview UI + boss telegraph banner).
- Cross-validator unit fix for combined `STEP_MULTIPLIER` + `STEP_EFFICIENCY` against `hcSteps` (same as RO-09 deferred #3 — schema migration v9 → v10).
- Workshop-screen surface of the same readout (the use case already supports it via the hidden-but-tested-for-reuse paths).
- `BattleViewModel` constructor refactor — now at 16 params; ADR + extraction candidate for v1.x.

### RO-09 — Pre-closed-test fixes (2026-05-18)

A self-audit run after RO-08 surfaced 7 latent issues; this PR lands the 3 that were flagged for **fix-before-closed-test**. The other 4 are bounded-impact lifetime-stat / atomicity / unit-mismatch issues with effectively zero exposure during the 14-day closed-test window — they're documented in `docs/plans/plan-RO-09-pre-closed-test-fixes.md` § "Deferred findings (v1.x)" for follow-up. Audit motivation: maximise the quality of the v1.0 closed-test build before the 14-day clock starts.

**Fix #1 — CHRONO_FIELD UW now actually slows enemies (commit `fcb282e`).**

Pre-fix: the description claimed *"Slows all enemies to 10 % speed for duration"* (8 s, 75 Power-Stone unlock cost). The activate path set `chronoActive = true` and the expire path reset it, but the **only** consumer of `chronoActive` anywhere in the codebase was the rendering overlay (purple full-screen tint). `EnemyEntity.update` read raw `speed`; `GameEngine.update` passed raw `deltaTime` to every entity. Net: a player who unlocked CHRONO_FIELD spent 75 Power Stones and saw an 8-second purple-tinted screen with zero gameplay effect. Same shape as the RO-08 dead-enum findings — feature wired into UI / cooldown / cost path but disconnected from gameplay.

- New companion constant `CHRONO_SLOW_FACTOR = 0.10f`.
- `GameEngine.update` entity loop now scales `deltaTime` per-entity: `if (chronoActive && e is EnemyEntity) deltaTime * CHRONO_SLOW_FACTOR else deltaTime`. The gate on `EnemyEntity` keeps projectiles, orbs, and the ziggurat at full speed, so player-side timing (shoot cooldowns, projectile travel, orb orbit) is unaffected.

**Fix #2 — GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` stacking (commit `f4d5997`).**

`fortuneMultiplier` is a single shared field used by **both** Step Overdrive FORTUNE (3.0×) and Ultimate Weapon GOLDEN_ZIGGURAT (5.0×). The lifecycle paths leaked the buff in three ways:

| Pre-fix scenario | Pre-fix value | Correct value |
|---|---|---|
| GOLDEN expires, FORTUNE active | 5.0× (preserved) | **3.0×** (FORTUNE owns it) |
| GOLDEN expires, ASSAULT active | 5.0× (leaked) | **1.0×** |
| GOLDEN expires, FORTRESS active | 5.0× (leaked) | **1.0×** |
| GOLDEN expires, SURGE active | 5.0× (leaked) | **1.0×** |
| FORTUNE activates while GOLDEN active | 3.0× (downgrade) | **5.0×** (GOLDEN's higher value wins) |
| ASSAULT/FORTRESS/SURGE expires, GOLDEN still active | 1.0× (collapsed) | **5.0×** (GOLDEN still owns it) |

The most exploitable case: activate ASSAULT (1.0× cash), then GOLDEN_ZIGGURAT (5.0× cash). When GOLDEN expired while ASSAULT was still active, the 5.0× multiplier persisted for the remainder of ASSAULT's window — up to ~50 s of unintended 5× cash uptime per round.

Three production edits in `GameEngine.kt`, each preserving the invariant *"the higher of the two buffs always wins; the lower restores cleanly when one ends"*:

- `activateOverdrive(FORTUNE)`: `fortuneMultiplier = fortuneMultiplier.coerceAtLeast(3.0)` — symmetrical to the existing GOLDEN activate which uses `coerceAtLeast(5.0)`.
- `expireOverdrive`: `fortuneMultiplier = if (goldenZigActive) 5.0 else 1.0`.
- GOLDEN expire branch in `updateUWs`: `fortuneMultiplier = if (activeOverdrive == OverdriveType.FORTUNE) 3.0 else 1.0`.

**Fix #7 — Dead `total` expression in `LabsScreen.kt:106` (commit `fdc34d3`).**

`val total = info.remainingMs + (System.currentTimeMillis() - (System.currentTimeMillis() - info.remainingMs))` — algebraically `2 × info.remainingMs`, never read. Refactor leftover; the next line recomputes `totalMs` correctly via `info.timeToCompleteHours * 3_600_000`. One-line delete, zero behaviour change.

**Test coverage: 565 → 572 (+7 new tests).**

- `GameEngineTest` +3 RO-09 #1: chrono active slows enemies to 10 % of baseline (ratio in 0.08..0.12 tolerance band); chrono inactive is deterministic across fresh engines; chrono active does **not** slow `ProjectileEntity` (regression guard for the `EnemyEntity`-only gate).
- `GameEngineTest` +4 RO-09 #2: GOLDEN expiry preserves FORTUNE's 3.0× when FORTUNE active; GOLDEN expiry resets to 1.0 when ASSAULT active (regression guard for the leak); FORTUNE activation does not downgrade GOLDEN's 5.0×; `expireOverdrive` preserves GOLDEN's 5.0× when GOLDEN still active.

New test helpers: `setChronoActive(eng, Boolean)` (reflection on private `chronoActive` field), `simulateEnemyMovement(activateChrono)` (fresh-engine baseline-vs-chrono comparator), `readFortuneMultiplier(eng)` (reflection on private `fortuneMultiplier`), `activateGoldenZigForTest(eng, level)` (uses public `initUWs` + `activateUW` path), `invokeUpdateUWs(eng, deltaTime)` (reflection on private `updateUWs(Float)` to deterministically expire UWs without ticking the overdrive timer / wave spawner / collisions).

**Out of scope / deferred to v1.x:**

- **Finding #3 — STEP_MULTIPLIER × cross-validator unit mismatch.** Post-RO-08, `record.creditedSteps` includes the multiplier bonus but the cross-validator compares it raw against `hcSteps`. Edge case affecting players with prior CV offenses **and** STEP_MULTIPLIER ≥ 1. Fix requires a `multiplierBonusApplied` column on `daily_step_record` + migration v9 → v10. Closed-test cohort almost certainly has clean step history and zero exposure during the 14-day window.
- **Finding #4 — Currency lifetime counter desync.** `addGems` / `spendGems` / `addPowerStones` / `spendPowerStones` fire two `@Query` UPDATEs back-to-back, not in `@Transaction`. Lifetime counters can drift on crash. Display-only impact (Stats screen).
- **Finding #5 — TOCTOU race on gem / PS spend.** `MAX(0, balance + delta)` clamps the wallet but `incrementSpent` records the full requested amount. Wallet stays correct; lifetime drifts.
- **Finding #6 — Per-kill battle-step credit on `viewModelScope`.** End-of-round persistence migrated to `applicationScope` in RO-03 B.3 PR 2, but per-kill credits still launch on `viewModelScope`. Mid-round nav-away cancels in-flight credits; loss bounded to ≤ 1 step per pending callback.

**Verification:**

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 572 tests pass (was 565, +7).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL, clean R8 minify + lint vital + signing.
- After this PR lands: bump `versionCode 3 → 4` and re-upload to the internal track for one final smoke check, then promote internal v4 → closed track.

### RO-08 — Bundle of 4 upgrade-wiring fixes (2026-05-18)

External code review highlighted that several Workshop / Card upgrades were declared but not actually applied to the tower during play. This PR closes all four gaps in a single change. No SKU / billing / ad-SDK touchpoints — purely engine, ResolveStats, and DailyStepManager.

**Fix #1 — STEP_MULTIPLIER + RECOVERY_PACKAGES wired in (previously dead enums).**

- `STEP_MULTIPLIER`: `DailyStepManager` gained a `WorkshopRepository` constructor parameter and an `applyStepMultiplier(baseCredit)` helper. The bonus applies on the sensor (walking) credit path only — not on activity minutes (cycling / swimming / treadmill from Health Connect) — matching the GDD §4.3 wording "+1 % bonus steps earned from walking" and avoiding a churn to the existing `dailyActivityMinuteTotal += credited` source-tracking semantics. The bonus applies *after* anti-cheat (rate limit + velocity analysis) and *before* the 50 k absolute daily ceiling, so the cap stays an absolute ceiling. Cap on the multiplier itself is +100 % per GDD. Fresh DB read on each credit so level-ups take effect immediately.
- `RECOVERY_PACKAGES`: `GameEngine` now ticks a private `tickRecoveryPackages(deltaTime)` helper inside the main `update()` loop. Pulses fire only during `WavePhase.SPAWNING` (not during between-wave cooldowns — the upgrade's framing is "during waves"); the timer resets between waves so the first pulse of a new wave waits a full 30 s. Constants `RECOVERY_INTERVAL_SECONDS=30f`, `RECOVERY_PERCENT_PER_LEVEL=0.01`, `RECOVERY_PERCENT_PER_PULSE_CAP=0.50`. At full HP the pulse is suppressed but the timer still resets so a freshly-damaged tower waits one full interval. Heal floors at 1 HP for visible feedback. Spawns a green floating-text indicator above the tower; no sound (automatic effect, audio could be noisy).

**Fix #2 — `ZigguratEntity` stale-stats propagation.**

The entity captured `attackInterval` (`val`) and `attackRange` (`val`) at construction and held a `val stats: ResolvedStats` reference. After construction, any `engine.stats = stats.copy(...)` (Overdrive ASSAULT/FORTRESS, in-round upgrades, GOLDEN_ZIGGURAT UW) created a new `ResolvedStats` instance the engine saw but the entity didn't. Concrete bug: ASSAULT's "2× attack speed" and FORTRESS's "2× health regen" silently no-op'd; in-round ATTACK_SPEED purchases never made the tower fire faster.

- `ZigguratEntity.stats` is now a `var` with a `private set` and a `fun updateStats(newStats)` setter.
- `attackInterval` is a private computed property `get() = (1.0 / stats.attackSpeed).toFloat()` recomputed each tick from the live `stats`.
- `attackRange` becomes `val attackRange: Float get() = stats.range` — same idea.
- `GameEngine.applyStats(newStats)` is the single mutation point: replaces `engine.stats`, calls `zig.updateStats(newStats)`, rebalances `currentHp` proportionally if `maxHealth` changed, and re-spawns orbs if `orbCount` changed. All five stat-mutation sites (`setStats`, `activateOverdrive` ASSAULT/FORTRESS arms, `expireOverdrive`, GOLDEN_ZIGGURAT UW activate + expire, `updateZigguratStats`) route through `applyStats`.

**Fix #3 — In-round upgrade coverage matches GDD §5.**

Battle-formulas.md §Stats Resolution and GDD §5 both state "All Workshop stats have corresponding in-round upgrades that stack multiplicatively." Pre-RO-08 only DAMAGE / ATTACK_SPEED / HEALTH had an `ir(...)` term in `ResolveStats`; the other 14 stat-bearing upgrades silently produced no in-round effect even though the in-round upgrade menu deducted cash for them.

- `ResolveStats` now applies `ir(...)` to all 14 stat-bearing types. Multiplicative stats (`damage`, `attackSpeed`, `maxHealth`, `healthRegen`, `range`, `knockbackForce`) follow the documented `(1 + ws*x) × (1 + ir*x)` formula. Additive stats (`critChance`, `critMultiplier`, `defensePercent`, `defenseAbsolute`, `thornPercent`, `lifestealPercent`, `damagePerMeterBonus`, `deathDefyChance`, `multishotTargets`, `bounceCount`, `orbCount`) sum the two level sources before applying the per-level effect and any cap. Range now uses the multiplicative form coerced to `≤ BASE × 3` (matches the prior cap; previously the cap clamped only the workshop term).
- `InRoundUpgradeMenu` mirrors the WorkshopViewModel hidden-set: `STEP_MULTIPLIER` and `RECOVERY_PACKAGES` are filtered out of the in-round menu (passive walking-bonus and periodic-heal effects don't fit the cash-fed in-round mechanic).
- `GameEngine` renamed `workshopLevels` → `effectiveLevels` (private) and added `fun updateEffectiveLevels(combined)`. `BattleViewModel.purchaseInRoundUpgrade` builds a `combinedLevelsForCash()` map (additive merge of `workshopLevels + inRoundLevels`) and pushes it to the engine on every purchase. The `FREE_UPGRADES` free-roll chance is now read from the same combined map, so a mid-round FREE_UPGRADES level contributes to its own subsequent free-roll chances. Result: in-round purchases of `CASH_BONUS` / `CASH_PER_WAVE` / `INTEREST` / `FREE_UPGRADES` now actually take effect for subsequent kill rewards / wave-end bonuses / interest payouts / free-upgrade rolls.

**Fix #4 — STEP_SURGE card multiplies the watch-ad gem reward.**

`ApplyCardEffects` already computed `gemMultiplier` from STEP_SURGE; `BattleViewModel` was discarding the field. Now stored as `cardGemMultiplier`, refreshed in `init` and `playAgain`, applied in `watchGemAd` via `(1.0 * cardGemMultiplier).toLong().coerceAtLeast(1L)`. Default `1.0` keeps the existing 1-gem reward unchanged when no STEP_SURGE is equipped; Lv1 → 2 gems, Lv5 → 4 gems. The post-round reward-ad path is currently the only in-game gem source in the round, so this lights up the card's full design.

**Test coverage: 535 → 565 (+30 new tests).**

- `ResolveStatsTest` +20: in-round multiplier coverage for every stat-bearing type (multiplicative attackSpeed / healthRegen / range / knockback; additive crit chance/factor, defense %/abs, thorn, lifesteal, damage/meter, death-defy, multishot, bounce, orbs); each cap (range × 3, crit 80 %, defense 75 %, lifesteal 15 %, death-defy 50 %, multishot 5 targets, orbs 6) verified under combined ws+ir.
- `BattleViewModelTest` +2: STEP_SURGE Lv1 doubles ad reward (1 → 2 gems); Lv5 quadruples (1 → 4).
- `GameEngineTest` (new file) +5: ASSAULT propagates 2× attackSpeed to ziggurat; FORTRESS propagates 2× healthRegen; expireOverdrive restores baseline interval; `updateEffectiveLevels` updates the engine's level map; RECOVERY_PACKAGES heal pulse fires at 30 s in SPAWNING phase; level 0 produces no heal; full HP suppresses pulse; pulse caps at 50 % maxHp.
- `DailyStepManagerTest` +5: STEP_MULTIPLIER level 0 is a no-op; level 50 grants +50 % bonus; cap at +100 % regardless of level; 50 k daily ceiling clamps multiplied credit; multiplier does NOT apply to activity minutes.

GameEngineTest uses reflection to invoke the private `tickRecoveryPackages(Float)` and `expireOverdrive()` helpers — bypasses the full game-loop side effects (enemy spawning, melee hits, projectile collisions) so the heal-only and stats-only assertions stay deterministic.

**Out of scope / future work:**

- The `STEP_MULTIPLIER` lookup runs a Room flow `.first()` on every credit — fine for the typical sensor cadence (1 credit per ~30 sensor ticks max via the rate limiter) but could be cached if profiling reveals contention.
- Activity-minute deltas don't get the multiplier (intentional — see the "Fix #1" notes above on the `dailyActivityMinuteTotal += credited` source-tracking semantics). A future v1.x could extend the multiplier to activity minutes by changing that to `+= delta` and tracking HC raw progress separately.
- RECOVERY_PACKAGES uses no sound effect to avoid noise during automatic firing. A subtle blip could be added in v1.x.

### PR C — Plan 31 walkthrough doc revision: 4 lessons-learned + minor footguns (2026-05-18)

Pure-docs PR. The walkthrough at `docs/release/plan-31-walkthrough.md` was written before the first end-to-end Plan 31 run; the live walk-through with the user surfaced four things the doc didn't anticipate, plus three smaller footguns. All fixed inline + summarised in a new "Updated 2026-05-18" preamble at the top.

Lessons fixed:

1. **Android Developer Verification (mandatory since late 2025).** New "E1 detour" subsection walks through the debug-keystore registration path: confirm the offered SHA-256 matches `~/.android/debug.keystore`, drop a one-line `assets/adi-registration.properties` snippet, build a debug APK, verify, upload, delete the snippet. Cites ADR-0007.
2. **Lowercase SKU IDs.** Phase F's product-ID table now uses `gem_pack_small` / `ad_removal` / `season_pass` etc. (matches `BillingProduct.skuId()`). Phase F also documents the `Purchase option ID` field's `[a-z0-9-]` format requirement (hyphens, not underscores) with recommended values mirroring the product IDs.
3. **Closed testing is mandatory — ≥12 testers, ≥14 days.** Phase I1 retitled from "(optional)" to "(mandatory)" with explicit recruitment + opt-in URL guidance + the 14-day calendar clock that gates the Production-access form.
4. **The native debug symbols warning is unfixable for v1.** New callout in Phase G2 explains SQLCipher + androidx.graphics.path ship pre-stripped; `ndk { debugSymbolLevel = "FULL" }` is correct config but bundles zero symbols. The warning is informational, not a blocker.

Minor footguns also documented:

- `versionCode` is forward-only — a `bundleRelease` smoke test consumes the counter permanently in Play Console even without rollout.
- Phase E6 (pricing & distribution) is a no-op in modern Play Console; country/region selection moved into the Phase G release flow.
- Phase F now mentions Plan 31 PR B's live-price wiring, so a future doc reader knows in-app prices auto-track Play Console.
- Contact email updated from the `support@whitefanggames.com` placeholder to the actual `jonwhitefang@gmail.com`.
- `versionCode` table cell now points at `app/build.gradle.kts` as the live source instead of saying "`1`".

No source / test impact.

### PR B — Live formatted price from Play Billing's `ProductDetails.priceDisplay` (2026-05-18)

Pre-closed-testing UX/footgun fix. The Store screen previously read
`BillingProduct.priceDisplay` static constants directly (`"$0.99"`, `"$3.99"`, etc.).
If a Play Console price ever drifted from the constant — e.g. as briefly happened
earlier this Plan 31 cycle when `ad_removal` was almost set to $9.99 with the in-app
label still saying $3.99 — testers would see one price in-app and be charged a
different price by the Play Billing dialog. Bait-and-switch territory and a Play Store
policy concern even on test cards.

Now Play Console is the source of truth for the user-visible price, with the static
constants kept as a build-time fallback for offline / pre-query.

- **`BillingManager.getPriceDisplay(product): String?`** — new interface method with a
  default no-op (`null`) so test fakes inherit a do-nothing contract. KDoc cites locale
  examples (`"£0.79"`, `"€0,99"`) and explicitly tells callers to handle the `null`
  case by falling back to `BillingProduct.priceDisplay`.
- **`BillingManagerImpl` override** — `sessionMutex.withLock { ensureConnected() →
  queryProductDetails(listOf(skuId), productType) → firstOrNull()?.priceDisplay }`.
  Failure paths (`connect` error, `QueryProductDetailsResult.Error`, empty result list)
  all return `null` with a `Log.w` for diagnosability. Mutex shared with `purchase()` and
  `reconcilePendingPurchases()` so the live-price refresh on Store entry can't race a
  purchase-in-progress.
- **`StoreViewModel.refreshPriceDisplays()`** — launched once on init alongside the
  existing `reconcilePendingPurchases` hook. Iterates `BillingProduct.entries`,
  populating `_priceDisplays: MutableStateFlow<Map<BillingProduct, String>>` progressively
  as each query completes. Failures (null) are skipped — the missing key signals the UI
  to fall back. Marked `@VisibleForTesting internal` so unit tests can drive a
  deterministic refresh.
- **`StoreUiState.priceDisplays: Map<BillingProduct, String>`** — new field combining
  the 5th flow argument into the existing `combine` chain. Default empty map; missing
  keys are the explicit fallback signal at the UI layer.
- **`StoreScreen`** — every price label changed from
  `BillingProduct.X.priceDisplay` to `state.priceDisplays[X] ?: BillingProduct.X.priceDisplay`.
  3 sites: Gem packs, Ad Removal, Season Pass. Inline comment cites Plan 31 PR B.
- **`FakeBillingManager.priceDisplayOverrides: MutableMap<BillingProduct, String?>`** —
  test knob. Empty map (default) → `getPriceDisplay` returns `null` for every product,
  matching the production behaviour when Play Billing is unavailable. Tests opt in by
  populating the map.

#### Tests (+5, 530 → 535)

- **`BillingManagerImplTest`** (+3): `getPriceDisplay returns adapter priceDisplay on success`
  (asserts `"£0.79"` surfaces verbatim, locale-formatted by Play Billing); `... returns
  null when product details query fails` (`QueryProductDetailsResult.Error` → null);
  `... returns null when adapter cannot connect` (`SdkBillingResult.BillingUnavailable`
  → null).
- **`StoreViewModelTest`** (+2): `priceDisplays starts empty so the UI falls back to
  static priceDisplay` (no overrides set → `state.priceDisplays.isEmpty()`);
  `priceDisplays populates from getPriceDisplay results on init` (3 of 5 products
  override-mapped to non-null values, 2 deliberately omitted to assert that missing
  keys remain absent rather than landing as empty strings).

#### Out-of-scope (intentional v1.x deferrals)

- **No price refresh on app resume / locale change.** Prices are queried once on Store
  entry. If the device locale changes mid-session, the user has to re-enter the Store
  to see localized prices. Acceptable for v1.
- **No retry on transient network failure.** A single null sticks for the whole Store
  session. Acceptable because the static fallback is a known-good price baked into the
  AAB at build time — the user always sees something sensible. Revisit if support
  surfaces "my prices look weird" tickets post-launch.

No DB schema change. No new dependencies.

### PR A — Surface ad-error feedback as snackbar in Battle + Cards screens (2026-05-18)

Pre-closed-testing UX polish. Three call sites (`CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`) previously swallowed `AdResult.Cancelled` and `AdResult.Error` silently, so a tester tapping "Watch ad for Gems" on a device that returned `NO_FILL` got no feedback at all. Now they get a clear snackbar message — mirrors the `userMessage: StateFlow<String?>` pattern already used by `MissionsViewModel` / `WorkshopViewModel` / `LabsViewModel` / `CardsViewModel.upgradeCard`.

- **`CardsViewModel.watchFreePackAd`** — `if (result is AdResult.Rewarded)` opened up to a `when` over all three variants. `Cancelled` → "Ad cancelled. Try again."; `Error` → the adapter's `result.message` verbatim (or a generic fallback when blank, since `RewardAdManagerImpl` can return blank for some Mobile Ads SDK error codes).
- **`BattleViewModel.watchGemAd` + `watchPsAd`** — same pattern. Added `userMessage: String?` field to `BattleUiState`, `clearMessage()` method to the VM. The `watchPsAd` Rewarded branch correctly keeps its `state.powerStonesAwarded > 0` guard inside the `Rewarded` arm so the user does not get the "reward credited" feedback for a 0-stones round.
- **`BattleScreen`** wraps a `SnackbarHost` aligned to bottom-center, drawn last in the outer `Box` so it stacks on top of every other overlay (including `PostRoundOverlay`, where the watchGemAd / watchPsAd buttons live). `LaunchedEffect(state.userMessage)` calls `showSnackbar` then `viewModel.clearMessage()` so each event surfaces exactly once.
- **6 new tests, +6 total — 524 → 530.**
  - `CardsViewModelTest`: extended 2 existing `Cancelled` / `Error` cases to assert `userMessage` is set; added 1 new test for the blank-message fallback (`AdResult.Error("")` → "Ad failed to load. Try again later.").
  - `BattleViewModelTest`: 5 new tests — `watchGemAd Cancelled` + `watchGemAd Error`, `watchPsAd Cancelled` (uses `installEngineForEndRound + quitRound` to set up a `roundEndState`) + `watchPsAd Error` (blank-message fallback), and `clearMessage nulls userMessage`.

No behaviour change on the happy `Rewarded` paths. No new dependencies. No DB schema change.

### C.5 PR 3 — Delete `StubBillingManager`, collapse `BillingModule` to `@Binds BillingManagerImpl` (2026-05-18)

Mechanical follow-up to the Phase G internal-track on-device smoke-test PASS earlier the same day. With real Play Billing v8 verified end-to-end on a real device, the second `BillingManager` implementation has no remaining purpose.

- **`StubBillingManager` deleted** (`data/billing/StubBillingManager.kt`, 36 lines). The class simulated purchases with a 500 ms delay and credited gems / set flags directly on `PlayerRepository` — useful while real Play Billing was unwired, redundant now.
- **`BillingModule` collapsed** from a flag-gated `@Provides` Provider-switch to two plain `@Binds` abstract classes. `BillingModule` now binds `BillingManager → BillingManagerImpl`; sibling `BillingInternalModule` keeps the existing `BillingClientAdapter → RealBillingClientAdapter` binding. KDoc rewritten to capture the C.5 PR 1–3 history. Mirrors the C.6 PR 3 collapse of `AdModule`.
- **`BuildConfig.USE_REAL_BILLING` removed.** No code reads it anymore. Removed from `app/build.gradle.kts` defaultConfig + debug + release blocks; the `buildFeatures.buildConfig` opt-in comment now references `USE_REAL_ADS` (the surviving flag) only. `AdModule.kt` KDoc lost its "symmetric with USE_REAL_BILLING" line. The `app/build.gradle.kts` Play Billing dependency comment was also refreshed to note `BillingManagerImpl` is the sole binding.
- **KDoc cleanup across 5 production files.** `BillingManagerImpl.kt` lost its "PR 1 wiring status" block; `BillingManager.kt` interface lost its `@link` to `StubBillingManager`; `ActivityProvider.kt` lost its "binding still points at Stub" line; `StoreViewModel.kt` lost its mention of Stub + `USE_REAL_BILLING`; `AdModule.kt` lost the "symmetric flag" reference. All replaced with present-tense descriptions.
- **`BillingManagerParityTest` deleted** (3 tests). It existed to assert that Stub and Real produce equivalent wallet/flag effects on the golden path during the C.5 PR 2 transition. With Stub gone, the only remaining side is Real, and that's already exhaustively covered by `BillingManagerImplTest` (14 tests — 3 happy paths + 5 failure paths + idempotency + 2 reconciliation cases + delegation).
- **`docs/monetization.md` Implementation Status block fully refreshed.** The doc had been stale since pre-C.5/C.6: still described stubs, said real SDK integration was "deferred," listed Play Billing v7 as the target. Now it lists Real-SDK reality (Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end), the atomic idempotency guarantees, and an honest "What's Out-of-Scope for v1" section (no server-side verification, no real-time subscription notifications, no ad mediation, no live formatted-price display from `ProductDetails.priceDisplay`).

#### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, **524 JVM tests** pass (down from 527 — the 3 BillingManagerParityTest tests were removed, no others changed).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 minify + signing all clean. Signed AAB at `app/build/outputs/bundle/release/app-release.aab` (~18 MB) at versionCode 4. Not uploaded — v3 is the live internal-track AAB and there's no functional reason to bump it. v4 stays reserved for the next legitimate upload (e.g. closed-track promotion or post-closed-test bug fix).

#### Phase G internal-track smoke test PASS (2026-05-18)

Required context for this PR landing. User installed the v3 internal-track AAB on a real device via the opt-in URL; full smoke checklist passed:

- Launcher icon, walking-tracked step accumulation, battle round flow.
- All 3 Gem packs purchased on real Play Billing with the test card and credited the wallet correctly: `gem_pack_small` → +50 Gems; `gem_pack_medium` → +300 Gems; `gem_pack_large` → +700 Gems.
- `ad_removal` purchased on real Play Billing; `adRemoved` flag set; reward-ad UI hidden across the app.
- `season_pass` subscription purchased; `seasonPassActive = true` with `purchaseTime + 30-day` expiry; +10 Gems/day daily-login bonus active.
- AdMob test ad served on the post-round reward path.

This closes the device-verification gate for C.5 PR 2 (real Play Billing + receipt-table idempotency works end-to-end on a real device with the rolled-out internal-track AAB) and unblocks this PR.

### v3 rolled out to internal track + versionCode 3 → 4 (2026-05-15)

User uploaded the v3 AAB (with the new `ndk { debugSymbolLevel = "FULL" }` config landed in the previous commit) and rolled it out to the internal-testing track instead of rolling out the earlier v2 draft. v3 is functionally equivalent to v2 — the symbol-warning is structurally unfixable for any AAB containing SQLCipher's pre-stripped .so prebuilts — but v3 is the cleaner build to ship.

Local forward-only counter bump versionCode 3 → 4 in `app/build.gradle.kts`. v4 is reserved for the next upload (most likely a post-smoke-test bug fix or C.5 PR 3 deletion).

No code change. No test impact. No new AAB built locally.

### Native debug symbols + versionCode 2 → 3 (2026-05-15)

Play Console flagged the v2 internal-track AAB upload with the standard "This App Bundle contains native code, and you've not uploaded debug symbols" warning. Investigated whether AGP's `ndk { debugSymbolLevel = "FULL" }` could fix it.

- **Added `ndk { debugSymbolLevel = "FULL" }` to the release build type.** AGP runs an `extractReleaseNativeDebugMetadata` task that pulls native debug info out of any `.so` files going into the AAB and packages it into `BUNDLE-METADATA/com.android.tools.build.debugsymbols/`. Inline comment block documents intent + the SDK_TABLE-vs-FULL trade-off.
- **Findings.** AGP task ran cleanly but produced **zero bundled symbols**. The two native libraries in the AAB are SQLCipher (`libsqlcipher.so`, ~6 MB per ABI) and `androidx.graphics.path` — both ship as pre-stripped prebuilts. No debug info exists in those `.so` files for AGP to extract. Play Console warning will persist on every upload until either (a) we build SQLCipher from source ourselves, or (b) upstream SQLCipher AAR starts shipping with `.dbg` files. Both are out-of-scope for v1.
- **Why keep the config anyway.** Documents intent for any future maintainer; correct config; cost is one extra Gradle task per release build (~seconds); auto-correct if dependencies start shipping symbols later.
- **versionCode 2 → 3.** Forward-only counter bump for the next upload. Internal-track v2 from earlier today is staying as the rollout candidate — the symbol-warning is informational and not a release blocker, so we don't need to re-upload just to dismiss it.

No test impact. Signed AAB rebuilt at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB, merged manifest confirms `versionCode="3"`. Not uploaded — v2 stays the internal-track AAB pending smoke test.

### versionCode bump 1 → 2 (2026-05-14)

Play Console retains every uploaded AAB's versionCode forever (even from withdrawn drafts), so an earlier `bundleRelease` smoke-test upload during the Plan 31 walk-through session permanently consumed `versionCode = 1`. Internal-track upload of the lowercase-SKU AAB rejected with "Version code 1 has already been used. Try another version code." One-line bump in `app/build.gradle.kts` (`versionCode = 1` → `versionCode = 2`); `versionName` stays `1.0.0` because nothing user-visible changed. Re-built signed AAB at `app/build/outputs/bundle/release/app-release.aab` (~18 MB), merged manifest confirms `versionCode="2"`. No test impact.

### Phase F unblocker — lowercase SKU wire format (2026-05-14)

Unblocks Play Console SKU creation. Play Console rejects product IDs that don't match `[a-z0-9._]`; our wire format previously sent `BillingProduct.name` (UPPER_SNAKE_CASE) byte-for-byte (per ADR-0005 decision #6), which Play Console refused.

- **`BillingProduct.skuId(): String`** — promoted from a private extension in `BillingManagerImpl` to a **public** method on the `BillingProduct` enum, returning `name.lowercase()`. KDoc cites Play Console's `[a-z0-9._]` rule. Any code that needs to compute the wire id should use `product.skuId()`; tests can do the same.
- **`BillingManagerImpl`** — KDoc invariant #4 updated from "uppercase enum name" to "lowercase enum name, e.g. `gem_pack_small`, `ad_removal`, `season_pass`". Private `BillingProduct.skuId()` extension deleted; the existing call sites at `purchase()` (queryProductDetails + error message) and `reconcileType()` (PENDING + PURCHASED branches) automatically pick up the new public method via name resolution. `fromSkuIdOrNull` companion extension now compares `it.skuId() == skuId` so reverse lookup matches the lowercase wire format.
- **`BillingReceiptEntity.productId` KDoc** — now refers to `BillingProduct.skuId()` and the lowercase wire format directly (refines ADR-0005 decision #6 post-Plan 31 Phase F).
- **Tests updated** — 4 test files. `BillingManagerImplTest` (5 hardcoded uppercase strings + the `stubHappyPath` helper switched from `product.name` to `product.skuId()`); `BillingManagerParityTest` (helper switched to `product.skuId()`); `BillingReceiptDaoTest` (10 hardcoded strings, all now lowercase — productId is opaque to the DAO, but staying consistent with production wire format keeps the fixtures realistic); `RoomSchemaTest` (2 strings in the billing-receipt round-trip).
- **No DB schema or migration change.** `productId TEXT NOT NULL` accepts any case; existing devices with uppercase rows from prior debug builds are not in the wild, so a one-time data migration is unnecessary. Plan 31 has not entered closed testing yet.

#### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 527 tests pass (no count change, parity with last session).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 minify + signing all clean. Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB. Rebuild required because Plan 31 Phase G AAB upload should carry the lowercase wire format from day one (Play Console SKUs will be created lowercase, and an old uppercase AAB in the same internal-testing track would silently route product-details queries to empty results).
- One pre-existing Kotlin warning carries over (`@ApplicationContext` parameter-target annotation; unrelated to this PR; tracked to land alongside KT-73255 follow-up).

#### Next session

Plan 31 Phase G: upload the new AAB to Internal testing → create 5 lowercase SKUs (`gem_pack_small`, `gem_pack_medium`, `gem_pack_large`, `ad_removal`, `season_pass`) → license testers → on-device verification of a real Play Billing test purchase. The on-device PASS unblocks C.5 PR 3 (delete `StubBillingManager`) and the closed-testing recruitment workstream.

### Plan 31 walk-through session — Phases A-F mostly landed, ADV registered, AAB built (2026-05-13)

Multi-hour live walk-through of the `docs/release/plan-31-walkthrough.md` doc. Most of Plan 31's external-account + listing work landed, plus three small code-side build-config changes batched into a single `feat(release): Plan 31 prep` commit. Stops at the SKU-creation step where Play Console requires lowercase product IDs (resumes next session).

#### External work completed

- **Phase A1.** Google Play Console developer account created + identity-verified (jonwhitefang@gmail.com, Personal account, $25 fee paid).
- **Phase A2.** AdMob account created, linked to the same Google account.
- **Phase B.** Privacy policy hosted on GitHub Pages at <https://jonwhitefang.github.io/steps-of-bablylon/>. Verified reachable in incognito. Mid-session the Play Console data-safety form required a `delete-data` URL; added a `Data Deletion` section + `<a name="delete-data"></a>` anchor to both `docs/release/privacy-policy.md` and `docs/index.md` (separate commit on `main` mid-flow). Final URL: `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`.
- **Phase C.** Production upload keystore generated locally at `release/upload-keystore.jks` (RSA 2048, 10000-day validity, alias `upload`, CN / O / etc. set). `keystore.properties` populated at project root. SHA-256 fingerprint `C4:00:72:90:D8:40:32:92:86:06:C0:E1:E4:CB:8E:86:95:80:6A:FE:54:81:A1:15:9A:74:93:62:F2:BE:BA:E8`. Both files gitignored. Smoke-tested via `./run-gradle.sh bundleRelease` after a one-line build script fix (see code section below).
- **Phase D1.** AdMob registered the Android app + created three rewarded ad units (one per `AdPlacement` enum value: `POST_ROUND_GEM`, `POST_ROUND_DOUBLE_PS`, `DAILY_FREE_CARD_PACK`). App ID + 3 ad unit IDs landed in `local.properties` (gitignored).
- **Phase E1.** App created in Play Console ("Steps of Babylon", Game, Free, package `com.whitefang.stepsofbabylon`).
- **Phase E1 detour — Android Developer Verification (new Google policy).** Modern Play Console flow demanded ADV before letting the package name register. Initially confused because Play Console only offered an "eligible" key fingerprint `47:E8:9F:0A:3D:C1:8C:EA:B4:F5:A5:80:4D:74:B0:9E:C6:67:92:3B:C6:49:5E:C6:05:2A:26:AD:48:9D:75:5D` to select — not our newly-generated upload keystore. Forensic check revealed it was the **local Android debug keystore** at `~/.android/debug.keystore`; every prior debug install on a Google-account-signed-in device had registered the package + debug fingerprint with Google's known-package-names registry, routing us into Step 2B (existing package) instead of Step 2A (new package). Decided to register with the debug keystore (path of least resistance) rather than the release upload keystore (rationale + Google review path). See ADR-0007.
- **ADV proof-of-ownership executed.** Play Console issued snippet `CHE2JNVSSL3U4AAAAAAAAAAAAA`. Created `app/src/main/assets/adi-registration.properties` with the snippet on a single line (matches Google's sample format from `android/security-samples`). Built debug APK via `./run-gradle.sh assembleDebug`, verified `apksigner verify --print-certs` shows the SHA-256 matches `47:E8:9F:0A:...` and the APK contains `assets/adi-registration.properties` at the expected path. Uploaded the 70 MB debug APK; Play Console verified ownership and registered the package name to the developer account. Snippet file deleted post-verification (one-time use); gitignored anyway so future verifications don't accidentally commit account-specific tokens.
- **Phase E2.** Main store listing populated: app name + short description (57/80 chars) + full description (2,389 chars from `docs/release/play-store-listing.md`) + 512×512 hi-res icon + 1024×500 feature graphic + 5 phone screenshots. Phone screenshots captured on-device from emulator-5554 (1080×2400 raw → centre-cropped to 1080×1920 9:16 + flattened to 24-bit RGB to satisfy Play Store requirements): `screenshot-1-home.png`, `screenshot-2-workshop.png`, `screenshot-3-battle.png`, `screenshot-4-labs.png`, `screenshot-5-stats.png`. All in gitignored `release/screenshots/`. Battle screenshot is the hero — 5-tier ziggurat in Hanging Gardens biome with full HUD.
- **Phase E2c.** Store settings: category Games → Strategy, tags `Casual` / `Strategy` / `Tower defense`, contact email `jonwhitefang@gmail.com`.
- **Phase E2d.** Privacy policy URL pasted into Play Console.
- **Phase E3.** Content rating questionnaire completed per the matrix in `docs/release/play-store-listing.md` (mostly No, Yes only on IAP + reward ads). Ratings issued.
- **Phase E4.** Data safety form completed: collects step/health (functionality, encrypted at rest) + purchase history (third-party Play Billing); shares with Google Play Billing + AdMob; users can delete via app Settings → Storage → Clear data; delete-data URL = `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`.
- **Phase E5.** Target audience set to `18+` per ADR-0006 Q5 to keep us out of COPPA / Families program complications.
- **Phase E6.** Effectively a no-op in the modern Play Console layout — country / region selection now happens inside the release flow in Phase G, not as a standalone form. Free pricing was locked in at app creation and is not configurable post-create without a paid-app license.

#### Modern-Play-Console deviations from the walk-through doc

- **ADV (Android Developer Verification) flow.** Walk-through pre-dated this Google policy; addressed mid-session via the debug-keystore path.
- **Pricing & distribution form.** Removed in modern Play Console; integrated into the release flow.
- **Closed testing prerequisite for production.** Dashboard explicitly said "Have at least 12 testers opted-in" + "Run your closed test with at least 12 testers, for at least 14 days". This adds ~14 days to the launch timeline. Internal track is still our immediate target (verifies the AAB on a real device + exercises Play Billing); closed track recruitment becomes a separate workstream.

#### Code-side changes (committed as `feat(release): Plan 31 prep`, sha bb6b253)

- **`app/build.gradle.kts`** — fixed the long-latent keystore path bug (`file(...)` resolved relative to the `app/` module, but the signing guide and Plan 31 walk-through both consistently document `storeFile=release/upload-keystore.jks` as a project-root path). Switched to `rootProject.file(...)`. The bug surfaced the moment someone first followed the documented signing flow; build was failing with `Keystore file '/Users/jpawhite/Documents/Kiro Projects/steps-of-bablylon/app/release/upload-keystore.jks' not found for signing config 'release'`.
- **`app/build.gradle.kts`** — Wired AdMob production IDs from gitignored `local.properties` into the `release { }` block: 3 `buildConfigField` overrides for `AD_UNIT_POST_ROUND_GEM` / `_DOUBLE_PS` / `_DAILY_FREE_CARD_PACK` + 1 `manifestPlaceholders["admobAppId"]` override. Falls back to Google's documented test IDs (`ca-app-pub-3940256099942544/5224354917` for the unit; `~3347511713` for the app id) when local.properties is absent or missing keys, so a CI build or a fresh clone never mints revenue from accidental impressions. Debug build keeps the test IDs from defaultConfig untouched. Two new `val` constants `ADMOB_TEST_APP_ID` + `ADMOB_TEST_REWARDED_AD_UNIT` declared at the top of the file alongside the existing keystore-properties loader for grep-friendly symmetry. Verified by inspecting `app/build/generated/source/buildConfig/release/.../BuildConfig.java` (production IDs) + the merged release manifest (production app ID), and confirming debug BuildConfig still reads the test IDs.
- **`app/src/main/AndroidManifest.xml`** — Added `<uses-permission android:name="com.android.vending.BILLING" />` explicitly. Play Console's in-app-product creation page hard-gates SKU creation on the uploaded AAB declaring this permission, and Play Billing Library v8 no longer auto-merges it (older versions did). Inline comment documents the rationale so a future cleanup doesn't strip it. Verified the permission appears in the merged release manifest at `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
- **`.gitignore`** — added `release/` directory ignore (covers `upload-keystore.jks`, `upload-cert.pem`, `screenshots/*.png` — all release-prep artifacts that don't belong in source control), and `app/src/main/assets/adi-registration.properties` (account-specific ADV one-time-use snippet). `*.jks` and `keystore.properties` ignores were already present from Plan 30.

#### Verification

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 527 tests pass (no test changes).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, 19,396,531 bytes (≈19.4 MB). `jarsigner -verify` reports `jar verified` (PKIX warning is normal for self-signed upload keystores; Play App Signing handles the upstream chain). Merged manifest contains `com.android.vending.BILLING`. AdMob production app ID + 3 ad unit IDs flow into release BuildConfig.
- `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Debug BuildConfig still uses Google's test ad units. Debug APK signed with `~/.android/debug.keystore` per the registered ADV fingerprint.

#### Where we stopped + immediate next steps

User hit a Play Console block at Phase F (in-app product creation): "Product ID must start with a number or lowercase letter. Can contain numbers, lowercase letters, underscores, and periods." Our `BillingProduct` enum constants are UPPER_SNAKE_CASE (`GEM_PACK_SMALL`, `AD_REMOVAL`, `SEASON_PASS`, etc.) and `BillingManagerImpl.skuId()` maps `BillingProduct.name` directly to the Play Billing `productId`. Need to map UPPER_SNAKE_CASE ↔ lowercase for the wire format. Decision deferred to next session; recommendation is to update the `skuId()` private extension + the public `fromSkuIdOrNull` companion extension to lowercase the enum name (e.g. `GEM_PACK_SMALL` → `gem_pack_small`), keeping the Kotlin enum constants idiomatic and only the Play-side string changing.

**Next session's task list (in order):**

1. Update `BillingManagerImpl.skuId()` + `BillingProduct.fromSkuIdOrNull(skuId)` to use lowercase wire format. Audit existing tests (`BillingManagerImplTest`, `BillingManagerParityTest`, `BillingReceiptDaoTest`) for any hardcoded `GEM_PACK_SMALL` strings and update.
2. Rebuild signed AAB, upload to Play Console **Internal testing** track (already-built AAB at `app/build/outputs/bundle/release/app-release.aab` is fine for the AAB-upload step — it has BILLING; but a fresh build with the lowercase mapping should land before SKUs are wired up).
3. Create the 5 SKUs in Play Console: `gem_pack_small`, `gem_pack_medium`, `gem_pack_large`, `ad_removal`, `season_pass`. First three are managed consumables, `ad_removal` is managed non-consumable, `season_pass` is a monthly subscription with 3-day grace + 30-day account hold + no free trial.
4. Add license testers (Gmail addresses), roll out to internal testing track.
5. Internal-track on-device verification: real Play Billing test purchase credits the wallet end-to-end. Unblocks C.5 PR 3 (delete `StubBillingManager` + collapse `BillingModule` Provider-switch to `@Binds`).
6. Recruit ≥12 closed testers, run closed-track release for ≥14 days (new Google production-access prerequisite).
7. Apply for production access, promote to production after Google review (1-7 days).

#### Local artifacts created this session (gitignored)

- `release/upload-keystore.jks` — RSA 2048 production upload key. Backed up to user's password manager.
- `release/upload-cert.pem` — public cert exported for ADV (would have gone via Path B if ADV had not auto-routed via debug keystore). Kept on disk for the future "add additional ADV key" flow that lets the release upload key act as a verified key alongside the debug keystore.
- `release/screenshots/screenshot-{1..5}-{home,workshop,battle,labs,stats}.png` — 5× 1080×1920 24-bit RGB phone screenshots used in the Play Store listing.
- `keystore.properties` — Gradle signing credentials at project root.
- `local.properties` (existing file) — 4 new `admob.*` keys appended.
- `~/Desktop/Screenshot 2026-05-13 at 13.43.19.png` + `~/Desktop/Screenshot2.png` (user-supplied) — Play Console UI screenshots used to diagnose the ADV + pricing-form flows.

### Play Store feature graphic — 1024×500 PNG (2026-05-13)

- **`docs/release/store-assets/play-store-feature-graphic-1024x500.png`** — 1024×500, 621.5 KB, 8-bit RGB. 40% under Play Store's 1024 KB cap.
- **Source.** User supplied `docs/release/store-assets/StepsOfBabylonArt.png` (1376×768, 1.2 MB pixel-art Tower of Babel scene — ziggurat-style tower under a swirling-cloud sky, walking figure on the lower-left third, path leading to the tower base, ruined Mesopotamian buildings framing left and right). Aspect mismatch: source 1.792 vs target 2.048 means 96 px of total height needs cropping.
- **Crop choice.** Center vertical crop `y=48..720` (lose 48 px top + 48 px bottom) for a 1376×672 intermediate, then LANCZOS-downsampled to 1024×500. Considered top-aligned (would clip the character's feet — rejected) and bottom-aligned (would lose dramatic sky-swirl detail — rejected). Center balances composition: full tower retained, character whole, ~85% of swirl pattern preserved, path + framing ruins intact. The minor AI-tool sparkle artifact at the source's bottom-right is sub-perceptual at storefront sizes.
- **PNG over JPEG** because pixel-art crispness matters; JPEG would smear the chunky pixel grid. PNG palette compression on this many distinct colors produced 621.5 KB — well within budget.
- **Source preserved in-repo** for future re-crops. If the Play Store presentation ever wants a different framing (e.g., tighter on the tower, more sky bias), regenerate from the original PNG.
- **Plan 31 raster blocker count: 3 → 1.** Only screenshots remain on the raster-asset list.

### Play Store hi-res icon — 512×512 PNG rendered from vector source (2026-05-13)

- **`docs/release/store-assets/play-store-icon-512.png`** — 512×512, 3.8 KB, 8-bit RGB. Generated artifact, tracked in git as a Play Store release asset.
- **`tools/render_play_store_icon.py`** — reproducible Pillow-only renderer. Reads the same 108-viewport polygon coords (20 vertices) + the same 3-stop vertical gradient stops as the in-app vector XML drawables, supersamples 4× to 2048×2048 for crisp polygon edges, then LANCZOS-downsamples to the final 512×512. No external SVG renderer needed (no rsvg-convert, ImageMagick, or Inkscape install) — the path data + gradient model are reimplemented directly in Pillow drawing primitives.
- **Source-of-truth coordinates duplicated, not symlinked.** The Android adaptive icon uses Android-specific XML schema (`<aapt:attr>` gradient, `android:pathData` SVG-subset) that no off-the-shelf vector renderer accepts directly. The script duplicates the polygon vertex list + gradient stops verbatim and includes a header docstring telling future-you to edit BOTH files when changing the design. Cleaner than maintaining an SVG-XML translation pipeline for a single icon.
- **Pixel sanity check** validates the output: corner `#0E2247` exact match; ziggurat top `#D3A846` (vs Gold `#D4A843`, within 1 channel of perfect — gradient interpolation + LANCZOS blend); middle exact `#C2B280`; bottom `#8D5E3D` (vs lightened DeepBronze `#8B5A3A`, within 4 channels at the polygon edge). All deviations are sub-perceptual at icon-render sizes.
- **Plan 31 status update.** App icon (512×512 PNG) blocker for Play Console upload — resolved. Remaining raster assets: 1024×500 feature graphic + screenshots. The feature graphic is a different composition problem (banner + tagline, not a logo); a future render script could plausibly do it but a designer or image-gen prompt is more cost-effective. Screenshots need device capture from the running app.

### App launcher icon — vector adaptive icon (2026-05-12)

- **Closes the "No app icon resources" debt item** tracked in STATE.md since Plan 30. Four new vector XML resources + `AndroidManifest.xml` wiring; `./run-gradle.sh assembleDebug` BUILD SUCCESSFUL.
- **`res/drawable/ic_launcher_background.xml`** — solid `#0E2247` deep-lapis vector fill. Echoes the `LapisLazuli` brand color from `presentation/ui/theme/Color.kt` (darkened from `#26619C` for contrast against the warm ziggurat foreground) and the Celestial Gate biome night-sky palette from `presentation/battle/biome/BiomeTheme.kt`. Solid over gradient deliberately — gradients in adaptive-icon backgrounds can mis-render on OEM launchers that composite their own mask shape.
- **`res/drawable/ic_launcher_foreground.xml`** — 5-tier stepped-ziggurat silhouette as a single compound closed path, filled with a vertical 3-stop linear gradient: Gold `#D4A843` (top) → SandStone `#C2B280` (mid) → lightened DeepBronze `#8B5A3A` (bottom). The lightened base swaps out brand `DeepBronze #6B3A2A` because pure `DeepBronze` against the `#0E2247` background is tonally too close and loses the silhouette at 24dp launcher renderings. All five tiers echo the 5-layer ziggurat entity in `presentation/battle/entities/ZigguratEntity.kt` and the 5-entry `zigguratColors` list per biome.
- **Geometry invariants.** Canvas 108×108 (adaptive-icon standard); all tower content inside the 72dp safe zone (`x=22..86, y=29..79`); tower visual center at `(54, 54)` matches canvas center so every launcher mask (round / squircle / teardrop / square) crops evenly. Each tier is 10dp tall and steps in 6dp on each side. Dimensions documented inline in the foreground XML header so future edits know the constraint budget.
- **`res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`** — `<adaptive-icon>` wrappers pointing at the two drawables. Round variant has identical contents to the primary — Android handles round masking from the adaptive source, so a separate round asset is unnecessary. `minSdk=34` means every target device supports adaptive icons; no raster density fallbacks needed in `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`.
- **`AndroidManifest.xml`** — added `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"` on the `<application>` tag. Previously these attributes were absent entirely and Android was defaulting to the generic system placeholder.
- **Plan 31 status.** In-app launcher icon: ✅ shippable. Play Store 512×512 hi-res PNG + 1024×500 feature graphic + screenshots: still pending, require raster tooling (Android Studio asset studio / Figma / image-gen service) outside the agent's capability. The vector source is ready to export from.

### Phase C.6 PR 3 — Delete `StubRewardAdManager`, collapse `AdModule` to `@Binds RewardAdManagerImpl` (2026-05-12)

- **`StubRewardAdManager` deleted** (`data/ads/StubRewardAdManager.kt`, 16 lines). The C.6 PR 2 internal-track verification PASS earlier this session (two on-device sessions / two placements exercised the real AdMob pipeline) removed the last reason to keep a second `RewardAdManager` implementation around. Developer debug affordance previously supplied by the stub's always-`Rewarded` path is replaced by `FakeRewardAdManager` in unit tests + Google's documented rewarded-ad test unit id (`ca-app-pub-3940256099942544/5224354917`, already wired as the default `BuildConfig.AD_UNIT_*`) for any device-level exercise.
- **`di/AdModule.kt` rewritten to collapse the C.6 PR 2 Provider switch.** With only one implementation left, the runtime `BuildConfig.USE_REAL_ADS` branch is dead code: `internal object AdModule` with `@Provides + Provider<Stub> + Provider<Real>` → `internal abstract class AdModule` with a single `@Binds RewardAdManager → RewardAdManagerImpl`. Sibling `AdInternalModule` kept as-is (still supplies `RewardedAdAdapter` + `ConsentManager` to both `RewardAdManagerImpl` and `MainActivity`'s direct `ConsentManager` injection). Module stays `internal` because `RewardAdManagerImpl` is `internal`. KDoc now reads as a three-PR history (PR 1 landed real impl → PR 2 flipped binding behind flag → PR 3 deleted stub + collapsed shape).
- **`RewardAdManagerParityTest` deleted** (4 tests, 140 lines). Without a stub to compare against, parity has nothing left to assert. `RewardAdManagerImplTest` (8 tests covering every `AdResult` variant + per-placement ad-unit routing + consent-denied-still-grants) remains the full coverage surface.
- **`BuildConfig.USE_REAL_ADS` retained.** The flag no longer gates the Hilt binding but still gates the `MainActivity.onResume` UMP consent prefetch so debug emulators without Play Services don't pay the UMP init cost on every app start. Symmetric with `USE_REAL_BILLING` and cheap to keep. Debug builds now bind `RewardAdManagerImpl` too — a bare emulator will return `AdResult.Error` on any ad tap (no Play Services → adapter load fails), which matches the release behaviour when `NO_FILL` happens. That's intentional: debug no longer has the "always rewards" surface that was silently masking client code that assumed `Rewarded` as the only outcome.
- **KDoc swept for stale stub references.** Updated `RewardAdManagerImpl.kt` ("PR 1 wiring status" block → three-PR history + minor follow-on rewording), `ConsentManager.kt` ("Scope" paragraph), `RealConsentManager.kt` ("Not wired into MainActivity" block), `MainActivity.kt` (consent prefetch comment), and three `app/build.gradle.kts` comment blocks on `USE_REAL_ADS`. Zero remaining code-level references to `StubRewardAdManager` in `app/src/main` or `app/src/test`; four surviving mentions in `di/AdModule.kt` + `RewardAdManagerImpl.kt` KDoc are historical-only backticked references explaining what PR 3 did.
- **Tests: 531 → 527 (-4).** Exactly the parity test's 4 cases. `./run-gradle.sh testDebugUnitTest` BUILD SUCCESSFUL, 0 failures. `./run-gradle.sh assembleDebug` BUILD SUCCESSFUL — Hilt graph resolves with the collapsed `@Binds` + sibling internal module. Pre-existing Kotlin KT-73255 `@ApplicationContext` param-vs-field warnings on 4 files unchanged (unrelated batch cleanup).
- **Release loop shortens.** Plan 31 Play Console setup is now the only upstream for C.5 PR 3 (symmetric `StubBillingManager` deletion). After Plan 31 unblocks device-track purchase verification, C.5 PR 3 is a similar single-file deletion PR. The pre-existing `AdResult.Error` silent-swallow UX gap in 3 call sites (`CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`) is now more user-visible by default in debug (no stub masking it) but is still not a release blocker.

### Hotfix — Battle-step-credit NOT NULL crash on fresh install + C.6 PR 2 device-track verification PASS (2026-05-12)

- **Crash fixed: `SQLiteConstraintException: NOT NULL constraint failed: daily_step_record.sensorSteps`** on first enemy kill of a fresh install. `DailyStepDao.incrementBattleSteps`'s UPSERT SQL supplied only `date + battleStepsEarned` on the INSERT half; every other column in `daily_step_record` is `NOT NULL` with no SQL `DEFAULT`. SQLite evaluates NOT NULL BEFORE the `ON CONFLICT(date)` clause — `ON CONFLICT(date)` only catches UNIQUE violations, not NOT NULL — so the INSERT aborted on the first NOT NULL check before the conflict handler could route to UPDATE. Reproduced on the C.6 PR 2 device-track emulator: fresh install, start battle, first kill, process dies. Bug was latent since B.2 PR 2 (battle-step-credit atomicity); unit tests never hit it because `FakeDailyStepDao` is a plain in-memory Map with no `NOT NULL` enforcement.
- **Fix**: expanded the INSERT half of `incrementBattleSteps`' UPSERT to supply every NOT NULL column explicitly (`0` for every numeric column, `'{}'` for the JSON-encoded `activityMinutes` map which matches the `Converters.fromStringIntMap(emptyMap())` round-trip). The UPDATE branch on conflict still touches only `battleStepsEarned`, preserving any existing sensor / HC / escrow data populated earlier in the day by the step sensor path. Kotlin-level entity defaults (`val sensorSteps: Long = 0`, `val activityMinutes: Map<String, Int> = emptyMap()`) unchanged — they govern Kotlin construction, not Room-generated SQL, which was the root of the bug.
- **First-attempt miss documented**: initially added an `insertIfAbsent` pre-seed via `@Insert(onConflict = OnConflictStrategy.IGNORE)` inside the atomic transaction, hoping it would make the subsequent UPSERT hit the UPDATE branch. Reverted after tests still failed with the same constraint exception — because SQLite evaluates NOT NULL on the new INSERT attempt regardless of whether an existing row would satisfy the ON CONFLICT target. Fixing the SQL directly is the only path.
- **Single source of truth preserved**: no schema migration, no DB version bump (still v9), no `@ColumnInfo(defaultValue = "0")` sprinkled across the entity (would have required a migration to update the CREATE TABLE statement). The hardcoded zeros in the SQL duplicate the entity's Kotlin defaults, which is a minor lint but the most surgical fix for an already-released schema.
- **Device-track verified on-device**: same emulator that crashed earlier in the session — rebuilt, reinstalled, started a battle, killed enemies, **no crash**. Full battle→round-end→post-round overlay flow works.
- **Tests: 526 → 531 (+5).** New `DailyStepDaoTest` (Robolectric + real in-memory Room, mirrors `BillingReceiptDaoTest` pattern): direct `incrementBattleSteps succeeds on empty table` regression guard, `creditBattleStepsAtomic credits successfully on empty table` (wallet credit + step balance + all Kotlin-default columns populated), `creditBattleStepsAtomic preserves existing sensor data` (ON CONFLICT UPDATE branch doesn't clobber populated rows), `creditBattleStepsAtomic returns partial credit near the cap`, `creditBattleStepsAtomic returns zero when cap already exhausted`. `./run-gradle.sh test` = BUILD SUCCESSFUL, 531 tests, 0 failures.
- **C.6 PR 2 device-track verification marked PASS.** Two on-device sessions with two different `AdPlacement` values (`DAILY_FREE_CARD_PACK` → AdMob `NO_FILL` code 3; `POST_ROUND_GEM` → DNS resolution failure code 0) both exercised the complete real-SDK pipeline end-to-end: real `RewardAdManagerImpl` selected by the flag-gated `AdModule` Provider switch, UMP consent dialog fired and completed from the `MainActivity.onResume` prefetch, `ActivityProvider` supplied the Activity, sibling `AdInternalModule` bindings resolved `RewardedAdAdapter` + `ConsentManager`, AdMob SDK v25 made the outbound request, error codes mapped and surfaced by the impl. The only un-exercised-live branch is `AdResult.Rewarded`, which is mechanistically symmetric to `Error` in our code — no conditional logic gates the happy-vs-error flow beyond the obvious wallet credit.
- **UX gap surfaced (pre-existing, not C.6 PR 2)**: `CardsViewModel.watchFreePackAd` + `BattleViewModel.watchGemAd` + `BattleViewModel.watchPsAd` all silently swallow `AdResult.Error` and `AdResult.Cancelled` — only `AdResult.Rewarded` has observable effect. User sees "nothing happens" on an ad tap whenever Google returns NO_FILL or the network stutters. Logged as a follow-up; not a release-blocker but worth a small snackbar plumbing pass before public launch. Affects 3 call sites.
- **Unblocks C.6 PR 3** (stub deletion). Paired with outstanding C.5 PR 2 device verification, which remains blocked on Plan 31 Play Console setup.

### Phase C.6 PR 2 — Flag-gated ad manager binding + MainActivity consent prefetch (2026-05-12)

- **`di/AdModule.kt` rewritten.** `abstract class` with a single `@Binds StubRewardAdManager` → `internal object` with `@Provides provideRewardAdManager` that accepts `Provider<StubRewardAdManager>` + `Provider<RewardAdManagerImpl>` and picks via `BuildConfig.USE_REAL_ADS`. Lazy `Provider` injection means the unselected impl is never constructed: the stub's `delay(1000)` never fires in release, and the real impl's AdMob + UMP clients never start in debug. Sibling `internal abstract class AdInternalModule` `@Binds` `RewardedAdAdapter` → `RealRewardedAdAdapter` and `ConsentManager` → `RealConsentManager` so Dagger can construct `RewardAdManagerImpl` when asked. Both modules are `internal` because they reference `internal` types. Mirrors the `BillingModule` / `BillingInternalModule` shape from C.5 PR 2.
- **MainActivity consent prefetch.** `@Inject internal lateinit var consentManager: ConsentManager` added alongside the existing `activityProvider`. `onResume()` fires a flag-gated one-shot `consentManager.ensureInitialized(this@MainActivity)` launch on the existing `activityScope` (Main.immediate, cancelled on destroy) guarded by an `AtomicBoolean consentPrefetchAttempted`. Flag-gate on `BuildConfig.USE_REAL_ADS` means debug builds (stub binding) never hit UMP / Play Services — emulator-friendly. Release builds amortise the ~200-500ms UMP init before the user's first reward-ad tap. UMP's own idempotency makes a missed guard harmless; the guard exists to skip launching coroutines that would immediately no-op.
- **Dagger graph resolution.** No missing-binding error on first build — the `AdInternalModule` bindings for `RewardedAdAdapter` + `ConsentManager` satisfy both `RewardAdManagerImpl`'s constructor deps and `MainActivity`'s direct injection of `ConsentManager`. Unlike C.5 PR 2 (which hit a missing-binding on first run and had to add the sibling module in a second pass), C.6 PR 2 shipped both modules in the initial cut.
- **No new dependencies.** `play-services-ads:25.0.0` + `user-messaging-platform:4.0.0` already on the classpath from C.6 PR 1. `BuildConfig.USE_REAL_ADS` already present from C.6 PR 1 (debug=false, release=true, `buildConfig = true` opted in via `buildFeatures`). The flag was dormant until this PR read it.
- **Tests: 522 → 526 (+4).** New `RewardAdManagerParityTest` (4 tests, plain-Kotlin mockito-kotlin — no Robolectric): 3 per-placement happy-path parity (`POST_ROUND_GEM` / `POST_ROUND_DOUBLE_PS` / `DAILY_FREE_CARD_PACK` all produce `AdResult.Rewarded` from both `StubRewardAdManager` and `RewardAdManagerImpl` when consent + adapter mocks are wired to happy responses); 1 `isAdAvailable` parity test (both return `true` for all 3 placements per ADR-0006 decision 4 where the real availability check moves into `showRewardAd`). Mirrors `BillingManagerParityTest`'s per-shape test structure from C.5 PR 2. Completes in 20ms total.
- **Not in this PR:** device-only internal test track verification of a real AdMob reward-ad render. That's the C.6 PR 2 → PR 3 gate, and it happens outside the unit-test loop. C.6 PR 3 (delete `StubRewardAdManager`) lands after ~1 week of closed-track confirmation.
- **Kotlin KT-73255 forward-compat warnings on `RealRewardedAdAdapter:56` and `RealConsentManager:55`** (plus the two pre-existing billing warnings) are all the same `@ApplicationContext` param-vs-field annotation-target issue; not addressed in this PR. Would land as a batch cleanup when `-Xannotation-default-target=param-property` flips.

### Phase C.6 PR 1 — Real AdMob `RewardAdManagerImpl` + UMP consent (2026-05-11)

- **Deps:** `com.google.android.gms:play-services-ads:25.0.0` + `com.google.android.ump:user-messaging-platform:4.0.0` pinned in `libs.versions.toml`. v25 is the current stable Google Mobile Ads SDK line as of 2026-05. ADR-0006 promoted Proposed → Accepted with 9 concrete commitments + answers to Q1–Q6.
- **New files:** `data/ads/RewardAdManagerImpl.kt` (orchestrates consent → load → show → `AdResult` mapping with per-placement `BuildConfig` ad-unit routing + sessionMutex + AdMob error-code-to-user-message translation); `data/ads/internal/RewardedAdAdapter.kt` (SDK-neutral seam with `SdkAdLoadResult` + `SdkAdShowResult` + `SdkRewardedAd` sealed types); `data/ads/internal/RealRewardedAdAdapter.kt` (the only file importing `com.google.android.gms.ads.*` — lazy `MobileAds.initialize` on first load, `CompletableDeferred` bridging all three AdMob callbacks, `AtomicBoolean` `rewarded` flag set ONLY in `onUserEarnedReward`); `data/ads/internal/ConsentManager.kt` (UMP-neutral interface); `data/ads/internal/RealConsentManager.kt` (the only file importing `com.google.android.ump.*` — Mutex-guarded once-per-session `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`).
- **Shared infrastructure.** `RewardAdManagerImpl` consumes the existing `data/billing/internal/ActivityProvider` (introduced in C.5 PR 1 and wired via MainActivity in C.5 PR 2). Both `RewardedAd.show()` and `BillingClient.launchBillingFlow()` need an Activity; sharing the WeakReference holder avoids a second lifecycle observer and duplicate onResume/onPause code.
- **Build config.** `BuildConfig.USE_REAL_ADS` introduced (debug=false, release=true) via `buildConfigField`. Three additional `buildConfigField` strings for per-placement ad-unit IDs — all three default to Google's documented rewarded-ad test unit (`ca-app-pub-3940256099942544/5224354917`) in debug so any dev can exercise the real SDK path without a production AdMob account; release overrides sourced from `local.properties` will be wired in C.6 PR 2. AdMob `APPLICATION_ID` supplied to `AndroidManifest.xml` via new `admobAppId` manifestPlaceholder (debug: `ca-app-pub-3940256099942544~3347511713` test app ID; release override in PR 2).
- **Manifest.** `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>` added inside `<application>` per AdMob's SDK-init contract — missing entry causes a `RuntimeException` on first request.
- **ProGuard.** Added `-keep class com.google.android.gms.ads.** { *; }` + `-keep class com.google.android.ump.** { *; }` + matching `-dontwarn` rules. Both SDKs ship internal keep manifests in their AARs; explicit rules here guard against R8 regressions across SDK version bumps.
- **Binding unchanged.** `di/AdModule.kt` still `@Binds`s `StubRewardAdManager`. C.6 PR 2 is where the `BuildConfig.USE_REAL_ADS` flag is read and the binding swap + MainActivity consent-flow wiring lands (mirroring C.5 PR 2's Provider-based switch in `BillingModule`).
- **ADR-0006 Proposed → Accepted.** 6 open questions resolved with concrete decisions: Q1 (consent-denied reward) YES grant the reward; Q2 (ad-load timeout) defer to AdMob's ~60s default to preserve distinct error codes; Q3 (per-session cap) NO — opt-in ads already capped per-placement; Q4 (mediation scaffolding) NO upfront abstraction; Q5 (child-directed flag) NO — game targets adults; Q6 (test ads in release debug) NO — internal-track tests real ads. See `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` for the full decision table.
- **Tests: 514 → 522 (+8).** New `RewardAdManagerImplTest` (8 tests, plain-Kotlin sealed adapter + mockito-kotlin; no Robolectric): happy `Rewarded` path; `Cancelled` on user-dismiss; 4 `Error` paths (no activity, consent unavailable, load failed with AdMob code 3 → "No ad available" message, show failed with code 1 → "already shown" message); consent-denied-still-grants per Q1; placement → ad-unit routing for all 3 `AdPlacement` values. `./run-gradle.sh test` = BUILD SUCCESSFUL.
- **Kotlin KT-73255 forward-compat warning on `RealRewardedAdAdapter:56`** is the same `@ApplicationContext` param-vs-field annotation target issue that exists on `BillingManagerImpl:79` and `RealBillingClientAdapter:54` — not addressed in this PR; lands as a batch cleanup when `-Xannotation-default-target=param-property` flips.

### Phase C.5 PR 2 — Flag-gated binding swap + MainActivity lifecycle wiring + reconcile hook (2026-05-11)

- **New build flag.** `BuildConfig.USE_REAL_BILLING` — `false` in debug (binds `StubBillingManager`), `true` in release (binds `BillingManagerImpl`). `buildFeatures.buildConfig = true` opted in because AGP 9 disables it by default. `defaultConfig` sets a safe `false` baseline for any future flavour that forgets to override; `debug { }` / `release { }` override explicitly for grep-friendly symmetry.
- **`di/BillingModule.kt` rewritten.** `abstract class` with `@Binds` → `internal object` with `@Provides` that accepts `Provider<StubBillingManager>` + `Provider<BillingManagerImpl>` and picks via `BuildConfig.USE_REAL_BILLING`. Lazy `Provider` injection means the unselected impl is never constructed: the stub's `PlayerRepository` observer never attaches in release, and the real impl's Play Billing client never starts in debug. Sibling `internal abstract class BillingInternalModule` `@Binds` `BillingClientAdapter → RealBillingClientAdapter` so Dagger can construct `BillingManagerImpl` when asked (required even if the debug Provider is never invoked — Dagger resolves the whole graph at compile time). Both modules are `internal` because they reference `internal` types (`BillingManagerImpl`, `RealBillingClientAdapter`).
- **`MainActivity` lifecycle wiring.** `@Inject internal lateinit var activityProvider: ActivityProvider` added. `onResume()` calls `activityProvider.set(this)` BEFORE the existing `updateLastActiveAt` launch. New `onPause()` override calls `activityProvider.clear()` BEFORE `super.onPause()` so nothing observes a stale Activity reference mid-teardown. The `WeakReference` in `ActivityProvider` is the belt — the explicit `clear()` is the suspenders, because a paused-but-not-yet-GC'd Activity could otherwise race with a purchase attempt.
- **`StoreViewModel.init` reconcile hook.** Added `viewModelScope.launch { billingManager.reconcilePendingPurchases() }` as a second `init` block launch (runs concurrently with the existing `cosmeticRepository.ensureSeedData()`). `BillingManager.reconcilePendingPurchases()` inherits a default no-op, so `StubBillingManager` + `FakeBillingManager` stay silent outside release. In release builds this sweeps `PENDING → PURCHASED` transitions on Store entry and retries any consume/ack that failed after a prior grant landed in Room — without re-crediting the wallet (the `granted = true` guard short-circuits `grantOnceAtomic`).
- **`FakeBillingManager` gained `reconcileCallCount: Int` with `private set`** so the StoreViewModel reconcile-hook invariant can be test-asserted. Default no-op `reconcilePendingPurchases()` overridden to increment the counter; call count stays zero for any test that doesn't construct a `StoreViewModel`.
- **Tests: 510 → 514 (+4).** New `BillingManagerParityTest` (3 tests, Robolectric + 2 independent in-memory Room DBs + real `PlayerRepositoryImpl` on both sides + mocked `BillingClientAdapter` on real side): `GEM_PACK_SMALL` parity (both credit 50 gems + `totalGemsEarned`), `AD_REMOVAL` parity (both flip `adRemoved` + leave gem wallet alone), `SEASON_PASS` parity (both activate + expiry within 60s tolerance of `now + 30 days` — stub uses call-time, real uses mocked `purchaseTime`, 60s is exhaustive for "30-day window within a test run"). Plus 1 new `StoreViewModelTest` case asserting `billingManager.reconcileCallCount == 1` after VM init. `./run-gradle.sh test` = BUILD SUCCESSFUL, 0 failures, 0 errors.
- **Not in this PR:** device-only internal test track verification of a real Play Billing purchase. That's the C.5 PR 2 → PR 3 gate, and it happens outside the unit-test loop. C.5 PR 3 (delete `StubBillingManager`) lands after ~1 week of closed-track confirmation.
- **Kotlin KT-73255 forward-compat warnings on `BillingManagerImpl:79` and `RealBillingClientAdapter:54`** are pre-existing from C.5 PR 1 (Hilt `@ApplicationContext` param-vs-field targeting); not addressed in this PR.

### Phase C.5 PR 1 — Real Play Billing v8 `BillingManagerImpl` (2026-05-11)

- **Dep:** `com.android.billingclient:billing-ktx:8.3.0` pinned in `libs.versions.toml`. v7 sunsets 2026-08-31 per Google's two-year deprecation window, so v8 is the current line. ADR-0005 amended from v7 → v8.
- **New files:** `data/billing/BillingManagerImpl.kt` (orchestrates purchases + receipts + wallet credits + anti-fraud `obfuscatedAccountId`); `data/billing/internal/BillingClientAdapter.kt` (SDK-neutral seam with sealed result types); `data/billing/internal/RealBillingClientAdapter.kt` (the one file that imports `com.android.billingclient.*`); `data/billing/internal/ActivityProvider.kt` (weak-ref holder, wired by MainActivity in PR 2); `data/local/BillingReceiptEntity.kt` + `data/local/BillingReceiptDao.kt` (idempotency store with `grantOnceAtomic` @Transaction).
- **DB schema bump v8 → v9.** `AppDatabase` grew to 13 entities + 13 DAOs. New `MIGRATION_8_9` creates the `billing_receipt` table with DDL byte-matching the Room-generated `app/schemas/…/9.json` export (verified via `RoomSchemaTest`). `DatabaseModule` gained `provideBillingReceiptDao`.
- **Interface extended.** `BillingManager` gained `suspend fun reconcilePendingPurchases()` with a default no-op body. `StubBillingManager` and `FakeBillingManager` inherit the no-op automatically — zero changes to existing callers.
- **Domain model shift.** `BillingProduct` gained an empty `companion object` so the data layer can attach reverse-lookup extensions (`fromSkuIdOrNull`). No Android import introduced — domain layer stays pure.
- **ProGuard.** Added `-keep class com.android.billingclient.** { *; }` + `-keep interface` + `-dontwarn` per Play Billing release-note guidance.
- **Binding unchanged.** `di/BillingModule.kt` still `@Binds`s `StubBillingManager`. C.5 PR 2 is where the `BuildConfig.USE_REAL_BILLING` flag and binding swap land.
- **Atomicity model.** Wallet credits run INSIDE `BillingReceiptDao.grantOnceAtomic` — receipt flip + wallet write commit atomically. Play Services RPCs (`consumeAsync`, `acknowledgePurchaseAsync`) run AFTER the transaction, so the SQLite lock is never held across a Google round-trip. Failed consume/ack is retried by `retryUnresolvedConsumeOrAck()` on the next reconciliation sweep — wallet is NOT re-credited (the `granted = true` guard short-circuits). Pending purchases persist with `granted = false` and are promoted to PURCHASED on the next sweep.
- **5 ADR open questions resolved** (promoting ADR-0005 Proposed → Accepted): Q1 delegates to v8's `enableAutoServiceReconnection()`; Q2 orders consume/ack after grant-commit with retry-without-re-credit; Q3 uses real Play Console SKUs + license test accounts (no static test SKUs); Q4 marks subscription proration out of scope for v1.0; Q5 sets `obfuscatedAccountId` to SHA-256 of a device-local UUID stored in `SharedPreferences("billing_anti_fraud")`.
- **Tests: 488 → 510.** `BillingReceiptDaoTest` (7 tests, Robolectric + real in-memory Room): upsert/get round-trip, `getByToken` not-found, `grantOnceAtomic` flip + wallet-credit lambda ran exactly once, idempotency (second call returns false, wallet lambda NOT run, `grantedAt` from first call preserved), `markConsumed`/`markAcknowledged` target-only, `getGrantedButUnresolved` filter, `getAll` orders by `purchaseTime` DESC. `RoomSchemaTest` extended with billing_receipt round-trip touching every column (incl. 4 nullables). `BillingManagerImplTest` (14 tests, Robolectric + real in-memory Room + mockito-kotlin on adapter): 3 happy paths (GEM_PACK_SMALL + consume / AD_REMOVAL + ack / SEASON_PASS + 30-day expiry), 5 failure paths (user cancel, product unavailable, no activity, connect fails, pending purchase persists receipt without credit), idempotency (same `purchaseToken` → Success + no double-credit), 2 reconciliation cases (PENDING→PURCHASED transition grants exactly once across repeated sweeps; `retryUnresolvedConsumeOrAck` retries consume without re-crediting wallet), `isAdRemoved` / `isSeasonPassActive` delegation to `PlayerRepository`. `./run-gradle.sh test` = BUILD SUCCESSFUL.

### Phase A — Foundation (2026-05-07, all 9 PRs merged)
- **A.2** Added `junit-vintage-engine` to test classpath — recovered 9 previously-hidden Robolectric tests (`RoomSchemaTest`, `DeepLinkRoutingTest`, `StepWidgetProviderTest`). Each needed `@Config(sdk = [34], application = android.app.Application::class)` as a Robolectric 4.14.1 / compileSdk 36 workaround.
- **A.3** `DatabaseKeyManager` now deletes the on-disk DB file (plus -shm/-wal) when the passphrase blob fails to decrypt, preventing crash-on-launch loops after device restore.
- **A.6** `DailyStepManager.runFollowOnPipeline` now forwards `seasonPassActive` / `seasonPassExpiry` to `TrackDailyLogin`, so the +10 Gems/day Season Pass bonus is credited even when step ingestion runs from the worker or background service.
- **A.5** `Screen.fromRoute` + `argumentFreeRoutes` whitelist — deep-links now reach all 12 argument-free routes (previously: 4). Unknown routes fall through silently.
- **A.4** `FakeBillingManager` / `FakeRewardAdManager` gained `resultQueue` scripting, configurable `isAdRemoved`/`isSeasonPassActive`/`isAdAvailable`, and call-log history. Store / Cards ViewModel tests now exercise every `PurchaseResult` / `AdResult` variant.
- **A.7** Capped Battle Step kills no longer spawn a misleading "+N Step" FloatingText. Callback signature changed to `(amount, x, y)` and spawn responsibility moved from GameEngine into BattleViewModel's callback.
- **A.8** Removed dead `PlaceholderScreen` and 4 orphaned imports from `MainActivity.kt`.
- **A.9** Deleted unused `SupplyDropTrigger.STEP_BURST` enum entry (no producer, no Room rows, no tests). Commit body preserves the original notification copy.
- **A.1** Current-state docs synced to DB schema v8 and 453-test baseline.

### Phase B.1 — Core Refactoring (2026-05-07, TimeProvider narrow migration)
- **B.1 PR 1** Added `TimeProvider` interface in `domain/time/` with `now(): Instant` and `today(): LocalDate`. Production `SystemTimeProvider` in `data/time/`, Hilt wiring in `di/TimeModule.kt`.
- **B.1 PR 2** Migrated 3 date-reading sites to a `timeProvider` default-arg parameter: `AwardBattleSteps`, `BattleViewModel`, `MissionsViewModel`. ~50 other wall-clock sites left on the real clock by design — narrow migration, not a sweep.
- **B.1 PR 3** Added `FakeTimeProvider` and 2 midnight-boundary tests that were previously impossible to write against the real clock. BattleViewModel now propagates its `timeProvider` into the inline `AwardBattleSteps` construction so the abstraction is end-to-end.
- ADR-0004 FollowOnPipeline stub recorded (status: Proposed, upgrade to Accepted when B.4 PR 1 lands).

### Phase B.2 — RO-02 atomic multi-writes (2026-05-07, PRs 1–3 of 5)
- **B.2 PR 1** `PurchaseUpgrade` now commits through `WorkshopDao.purchaseUpgradeAtomic` — a suspend `@Transaction` default interface method that takes `PlayerProfileDao` as a param. SQL-guarded deduct `UPDATE … WHERE currentStepBalance >= :cost` plus the workshop-level upsert runs in a single SQLite transaction. Closes the partial-failure gap between `spendSteps` and `setUpgradeLevel` and the double-tap race where two concurrent purchases could both pass an in-memory affordability check and double-spend. `PurchaseUpgrade` dropped its `PlayerRepository` dep; body shrank to a single delegation. First `@Transaction` marker in `app/src/main`.
- **B.2 PR 2** `AwardBattleSteps` now commits through `DailyStepDao.creditBattleStepsAtomic` — same pattern as PR 1, cross-DAO `@Transaction` default method taking `PlayerProfileDao`. Cap check + `incrementBattleSteps` + `adjustStepBalance` wrapped atomically. Closes the partial-failure gap (wallet credited without cap counter advancing) and the concurrent-kill race (two kills with 1 headroom could overflow by 1). `AwardBattleSteps` dropped its `PlayerRepository` dep; `BattleViewModel` gained a Hilt-injected `PlayerProfileDao`.
- **B.2 PR 3** `StepCrossValidator` wraps its 5 multi-write branches (Level 3 / Level 2 cap-excess, Level 1 / Level 0 first-escrow, reconciliation release) in `AppDatabase.withTransaction { }`. Different idiom from PRs 1–2 (repo-level not DAO-level) because the validator lives in `data/healthconnect/` and needs parallel transaction scopes; RO-02 explicitly licenses the cross-layer `AppDatabase` import here. Introduced a test-friendly `@VisibleForTesting internal var runInTransaction` seam so existing Mockito-based tests keep working without a real Room DB. `SharedPreferences` anti-cheat writes (`recordCvOffense`, `decayCvOffenses`) deliberately stay outside the transaction (not SQLite-backed).
- **B.2 PRs 4–5 still pending:** `ClaimMilestone` atomic (same pattern as PRs 1–2) and the `runEndRoundPersistence` `@Transaction` wrap (single-call-site change thanks to B.3 PR 1).

### Phase B.3 PR 1 — Resilient `runEndRoundPersistence` (2026-05-07)
- Extracted `BattleViewModel.endRound` body into a private suspend `runEndRoundPersistence(eng, wave)`. Each of 5 writes + 1 notification is wrapped in its own `runCatching { }.onFailure { Log.w(TAG, "endRound: <writeName> failed", it) }` so a single Room / notification-manager exception can no longer leave the player on a frozen battle screen with no post-round overlay.
- Writes whose results feed `RoundEndState` (`updateBestWave` → isNewBestWave/previousBest, `awardWaveMilestone` → psAwarded, `checkTierUnlock` + `updateHighestUnlockedTier` → tierUnlocked) use `.getOrNull()` / `.getOrDefault(0)` fallbacks, so the `_uiState.update` push **always** runs — even when every write throws.
- Writes 4–5 (`incrementBattleStats`, `dailyMissionDao.updateProgress`) moved from ad-hoc `try / catch (_: Exception) { /* best-effort */ }` swallows to `runCatching + Log.w` for observability parity with the R2-07 `StepSyncWorker` precedent.
- `endRound()` shrank from ~35 lines to 6 (guard + null-check + `viewModelScope.launch { runEndRoundPersistence(eng, wave) }`). `quitRound()` and the polling-loop call site unchanged; `roundEnded` guard still dedupes.
- `FakePlayerRepository` opened up (`class` → `open class`; 4 write methods marked `open override`) so tests can inject per-method throwing overrides to exercise the failure-isolation paths.
- `onCleared` mid-navigation round-loss fix is deliberately deferred to B.3 PR 2 per the RO-03 spec.

### Phase B.2 PR 4 — Atomic @Transaction for ClaimMilestone (2026-05-08)
- **MilestoneDao** gained `claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao)` — a suspend `@Transaction` default method. Read-modify-write pattern matches `DailyStepDao.creditBattleStepsAtomic`: check existing → bail if already claimed → `upsert(MilestoneEntity(claimed=true, claimedAt))` → credit gems + power stones via `playerDao.adjustGems` + `incrementGemsEarned` (and Power Stones equivalents) inside the tx. Closes the partial-failure gap between reward credits and the mark-claimed write, and the double-claim race where two concurrent clicks could both see `claimed=false` and both credit.
- **ClaimMilestone** use case: dep shape `(milestoneDao, playerRepository)` → `(milestoneDao, playerRepository, playerProfileDao)`. Still reads `totalStepsEarned` through `PlayerRepository` (monotonic read, safe outside the tx). Body shrank from reward-iteration loop + upsert to a single atomic delegation. `MilestoneReward.Cosmetic` still a no-op pending C.4 detection fix.
- **MissionsViewModel** gained a Hilt-injected `PlayerProfileDao`. **FakeMilestoneDao** gained optional `linkedPlayer: FakePlayerRepository? = null` + Mutex-guarded `claimMilestoneAtomic` override + `claimMilestoneAtomicCallCount` counter. Existing no-arg construction sites (CheckMilestonesTest, HomeViewModelTest) stay source-compatible.
- **ClaimMilestoneTest**: 5 → 8 cases (+3 RO-02 atomicity cases).

### Phase B.2 PR 5 — Room @Transaction around runEndRoundPersistence (2026-05-08, FINAL RO-02 site)
- **BattleViewModel.runEndRoundPersistence** now commits its 5 SQLite writes (`updateBestWave`, `awardWaveMilestone`, `updateHighestUnlockedTier`, `incrementBattleStats`, `dailyMissionDao.updateProgress`) inside a single `AppDatabase.withTransaction { }` block. External readers (Flow-based reactive reads) now see either the pre-PR state or the post-PR state, never a partial fan-out.
- Constructor grew to 12 params (+`AppDatabase`). Introduced `@VisibleForTesting internal var runInTransaction` seam matching `StepCrossValidator`'s B.2 PR 3 idiom — Mockito mocks of `AppDatabase` can't run Room's `withTransaction` extension, so tests override with a direct-invocation pass-through.
- Non-SQLite side effects (milestone notification, `_uiState.update`) moved to *after* the tx — no DB lock held across Android system calls or UI pushes.
- Outer `runCatching { runInTransaction { ... } }` preserves RO-03 resilience: Room infrastructure failures (disk full, SQLCipher decrypt failure) still let the post-round overlay appear with safe defaults (`isNewBestWave = false`, etc).
- **RO-02 family complete: 5/5 sites landed** (3 DAO-level `@Transaction` + 2 repo-level `withTransaction`).
- **BattleViewModelTest**: 19 → 21 cases (+2 atomicity cases: tx opened exactly once per round; UI push runs AFTER tx commits).

### Phase B.3 PR 2 — onCleared guard preserves mid-nav round progress (2026-05-08, FINAL RO-03 site)
- **New `di/CoroutineScopeModule.kt`** with `@ApplicationScope` qualifier + `@Singleton @Provides fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Outlives VM cancellation — fire-and-forget work launched here completes even if the originating ViewModel is cleared mid-operation.
- **Deviation from RO-03 spec** (documented in the module's KDoc): spec suggested `ProcessLifecycleOwner.lifecycleScope`, but (a) `androidx.lifecycle:lifecycle-process` isn't on the classpath, (b) its default dispatcher is `Dispatchers.Main` — wrong for DB writes, (c) Hilt-injected scope is more testable (`TestScope`-backed), (d) matches the project's Hilt-first conventions (BillingModule / AdModule / TimeModule precedent).
- **BattleViewModel**: +`@param:ApplicationScope applicationScope: CoroutineScope` (12 → 13 params). Extracted `markEndedAndLaunchPersistence(scope, eng)` helper that sets `roundEnded + roundOver` and launches `runEndRoundPersistence` on the provided scope; `endRound()` delegates via `viewModelScope`, new `onCleared()` override delegates via `applicationScope` when `engine != null && !roundEnded && engine.hasWaveProgress()`. Three-way guard prevents bounce-through phantom persistence and double-persistence on the normal `quitRound → onCleared` sequence.
- **GameEngine.hasWaveProgress()**: `elapsedTimeSeconds > 0f || totalEnemiesKilled > 0`. Thread-safe (reads `@Volatile` fields only).
- **RO-03 family complete: 2/2 sites landed** (B.3 PR 1 resilient extraction + B.3 PR 2 mid-nav scope guard).
- **BattleViewModelTest**: 21 → 24 cases (+3 B.3 PR 2 tests: mid-round onCleared persists, no-progress bounce-through is no-op, post-quitRound onCleared is no-op).
- Fixed the `@ApplicationScope` → `@param:ApplicationScope` forward-compat warning (Kotlin KT-73255) on the same PR for clean build.

### Phase C.2 PR 1 — Cosmetic renderer override pipeline (2026-05-08, RO-07 plumbing)
- **CosmeticItem** domain model: +`overrideColors: List<Int>? = null` nullable field. Pure Kotlin (no Android imports in domain). All existing construction sites stay source-compatible.
- **CosmeticRepositoryImpl**: +private `ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>>` empty map + KDoc (first entry ships in C.2 PR 2 with ZIG_JADE). `toDomain` populates `overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId]`. No DB schema change — colors are content, live in code.
- **GameEngine**: +`@Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()` public property. In `init()`, selects `cosmeticOverrides[ZIGGURAT_SKIN]?.overrideColors ?: biomeTheme.zigguratColors` when constructing ZigguratEntity — null-coalesce guarantees no regression when no cosmetic equipped.
- **BattleViewModel** constructor grew to 14 params (+`CosmeticRepository`). Loads equipped cosmetics in the init launch (`cosmeticRepository.observeEquipped().first().associateBy { it.category }`) and pushes to `engine?.cosmeticOverrides` in TWO places: init-launch completion and `startPollingEngine` — idempotent double-push handles the load-vs-attach race; whichever fires last wins.
- Pure additive plumbing. No user-visible change until C.2 PR 2 seeds ZIG_JADE + removes the R2-11 "Coming Soon" guard for that single ID in StoreScreen.
- **BattleViewModelTest**: 24 → 26 cases (+2 C.2 PR 1 tests: empty equipped set stays empty on engine; equipped ZIGGURAT_SKIN cosmetic propagates to `engine.cosmeticOverrides`).

### Phase C.2 PR 2 — Seed zig_jade as first end-to-end cosmetic (2026-05-08, RO-07 content)
- **CosmeticRepositoryImpl**: `ZIGGURAT_COLOR_LOOKUP` populated with its first entry — `"zig_jade"` mapped to the 5-color jade palette `[0xFF104E3C, 0xFF1A6B52, 0xFF2A8F6E, 0xFF3CAB82, 0xFF54C79A]` (bottom layer → top highlight). Matches the fixture used by the PR 1 synthetic VM→engine test.
- **SEED_COSMETICS**: +1 row — `CosmeticEntity(cosmeticId = "zig_jade", category = "ZIGGURAT_SKIN", name = "Jade Ziggurat", description = "Deep jade stone with pale highlights", priceGems = 150)`. Placed first in the list so it surfaces at the top of the Store cosmetics section. Total seed count: 7 → 8 (4 ZIGGURAT_SKIN, 2 PROJECTILE_EFFECT, 2 ENEMY_SKIN).
- **StoreScreen**: R2-11 "Coming Soon" guard lifted for `zig_jade` only via a new file-level `ENABLED_COSMETIC_ID` allow-list const. Unowned jade shows `💎 {priceGems}` on an enabled Button wired to `viewModel.purchaseCosmetic`, disabled while `state.isPurchasing` (double-tap guard). All other unowned cosmetics stay behind the existing "Coming Soon" disabled button until their palette ships in C.2 PR 3+. Disclaimer line updated: "Most cosmetic visuals are still being finalized. Jade Ziggurat is available now."
- **FakeCosmeticDao**: new in-memory fake (`test/fakes/`, 75 LOC) simulating Room's `@PrimaryKey(autoGenerate = true)` via a monotonic counter, plus per-cosmeticId upsert / equip / unequip / unequipCategory semantics matching the real DAO contract.
- **CosmeticRepositoryImplTest**: new (`test/data/repository/`, 134 LOC, 5 cases) proves the `seed → ZIGGURAT_COLOR_LOOKUP → CosmeticItem.overrideColors` chain on the real impl — the last mile of the C.2 pipeline that PR 1's VM test could not cover with a fake repo. Cases: (1) `ensureSeedData` inserts `zig_jade` with correct metadata; (2) `zig_jade.overrideColors` matches the 5-color jade palette exactly (content-as-code contract); (3) other 7 seeds have `null` overrideColors (regression guard: lookup is selective, not blanket); (4) equipped `zig_jade` surfaces via `observeEquipped` with palette intact — repo-layer mirror of the PR 1 VM→engine test; (5) `ensureSeedData` idempotent on repeat call (documents the current all-or-nothing `dao.count() > 0` gate).
- **480 tests** (475 → 480 via the 5 new repo-layer cases). Zero balance-math changes.
- **Known debt flagged for release:** `ensureSeedData` currently short-circuits when `dao.count() > 0`, so future content PRs (C.2 PR 3+) that add new seed rows won't land on already-seeded installs without a data clear. Acceptable for pre-release; must be replaced with per-cosmeticId upsert logic (or a DB migration) before v1.0.

### Phase C.4 — ClaimMilestone UnknownCosmetic detection (2026-05-08, RO-07 follow-up)
- **ClaimMilestone**: return type `Boolean` → `ClaimMilestoneResult` sealed class with four variants — `Success` (atomic credit ran), `InsufficientSteps` (step threshold unmet), `AlreadyClaimed` (atomic DAO returned false), `UnknownCosmetic(cosmeticId)` (one of the milestone's `MilestoneReward.Cosmetic` ids has no matching row in `SEED_COSMETICS`). Pre-flight check runs BEFORE the atomic DAO call so no partial credit when a cosmetic id is unknown. Constructor grew to 4 params (+`CosmeticRepository`).
- **CosmeticRepository**: +`suspend fun idExists(cosmeticId: String): Boolean` on the domain interface. Real impl lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`; `FakeCosmeticRepository` checks its `items` StateFlow directly. KDoc on the interface documents the C.4 detection-only rationale and flags resolution as C.2 PR 3+ content work.
- **MissionsViewModel**: gained a Hilt-injected `CosmeticRepository` (7 constructor params). `claimMilestone(milestone)` now pattern-matches `ClaimMilestoneResult` and surfaces non-Success outcomes as user-visible snackbar messages via a new `userMessage: StateFlow<String?>` + `clearMessage()` method. The `combine()` grew from 4 to 5 flows (+userMessage). `MissionsUiState` gained `userMessage: String?` field with KDoc.
- **MissionsScreen**: wrapped in `Scaffold(snackbarHost = { SnackbarHost(…) })` with a `LaunchedEffect(state.userMessage)` that shows the snackbar and clears. First time Missions gets user feedback on failed claims (previously silent).
- **User-visible impact today:** the 3 currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin` on MARATHON_WALKER, `lapis_lazuli_skin` on IRON_SOLES, `sandals_of_gilgamesh` on GLOBE_TROTTER) now surface as a snackbar ("Reward temporarily unavailable …") instead of silently dropping. Those 3 milestones cannot be claimed until C.2 PR 3+ adds matching seed rows; until then the claim rejects cleanly with zero partial credit.
- **ClaimMilestoneTest**: 8 → 12 cases (-1 merged + 5 new). Removed the old `credits Gems and Power Stones for IRON_SOLES` success-path case; coverage preserved by (a) new `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (default-state UnknownCosmetic rejection) + (b) new `milestone with matching cosmetic id credits rewards via atomic path` (seeds a `lapis_lazuli_skin` cosmetic fixture and shows the atomic credit runs cleanly, emulating post-C.2-PR-3 state). Renamed 3 cases for Result-type clarity. Switched the concurrent-claims race target from IRON_SOLES (unknown cosmetic) to MORNING_JOGGER (Gems-only) so the atomicity invariant being tested is independent of the cosmetic-id pre-flight check. 4 new C.4 cases: `UnknownCosmetic` x 3 (one per mismatched milestone, asserting the exact offending id), `UnknownCosmetic rejects claim before the atomic DAO call with no credit` (regression guard on the pre-flight ordering).
- **MissionsViewModelTest**: direct `ClaimMilestone` construction updated to 4-arg (+`FakeCosmeticRepository()`); asserts `ClaimMilestoneResult.Success` on the FIRST_STEPS claim path.
- **484 tests** (480 → 484 via +4 net). Zero balance-math changes.

### Fix — `ensureSeedData` per-cosmeticId filter (2026-05-08)
- **CosmeticRepositoryImpl.ensureSeedData**: replaced the all-or-nothing `if (dao.count() > 0) return` short-circuit with a per-`cosmeticId` filter. Reads existing ids once via `observeAll().first().mapTo(HashSet())`, computes `missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`, and `upsertAll(missing)` only when non-empty. Three behaviours:
  - **Fresh install:** `existingIds` empty → every SEED_COSMETICS row inserted. Identical to pre-fix behaviour.
  - **Partial-catalogue upgrade:** device already has the pre-`zig_jade` 7-row catalogue → only `zig_jade` inserted; 7 legacy rows untouched. Before the fix, this case was broken (count > 0 short-circuit skipped everything), so `zig_jade` never landed on already-installed devs without a data clear.
  - **Steady state:** all ids present → `missing` empty → no DAO write. Same as before, different mechanism.
- **Why the filter instead of a universal upsert:** `CosmeticEntity`'s primary key is `id` (auto-gen), not `cosmeticId`. Re-upserting a seed row with `id = 0` would insert a new auto-gen row alongside the existing one, not replace it. The explicit filter sidesteps that entirely by never handing already-present rows to the DAO.
- **Unblocks C.2 PR 3+** (the 3 milestone cosmetic seed rows that resolve the C.4 UnknownCosmetic detections): content PRs can now land on any install regardless of its catalogue history. Also removes the "data clear required" friction for any dev who installed a pre-C.2-PR-2 debug build.
- **Tests:** CosmeticRepositoryImplTest gained 2 regression-guard cases:
  - `ensureSeedData inserts newly-added rows on partial catalogue upgrade` — pre-seeds 7 legacy rows manually (no `zig_jade`), asserts `ensureSeedData` inserts `zig_jade` with its `ZIGGURAT_COLOR_LOOKUP` palette and leaves the 7 legacy rows intact.
  - `ensureSeedData preserves player state on existing rows (isOwned, isEquipped)` — pre-seeds `zig_jade` with `isOwned=true, isEquipped=true`, runs `ensureSeedData`, asserts the player state survives (never overwritten because the filter skips the row entirely).
  Existing idempotency test renamed (removed "count gate holds" phrase); end-state assertion unchanged because the filter produces the same steady-state behaviour via a different mechanism.
- **486 tests** (484 → 486). Zero balance-math changes.

### Phase C.2 PR 3 — Seed `lapis_lazuli_skin` (IRON_SOLES milestone reward) (2026-05-08)
- **CosmeticRepositoryImpl.SEED_COSMETICS**: +1 row — `CosmeticEntity(cosmeticId = "lapis_lazuli_skin", category = "ZIGGURAT_SKIN", name = "Lapis Lazuli Ziggurat Skin", description = "Deep lapis lazuli stone with pyrite-gold flecks", priceGems = 500)`. Placed second in the list (directly after `zig_jade`) so the two palette-shipping cosmetics appear grouped at the top of the Store section. Intentionally NOT added to `StoreScreen.ENABLED_COSMETIC_ID` — still shows "Coming Soon" in the Store; primary acquisition path is the IRON_SOLES milestone claim. Store pricing is a future UX decision. Total seed count: 8 → 9.
- **ZIGGURAT_COLOR_LOOKUP**: +1 entry — `"lapis_lazuli_skin"` maps to `[0xFF1A1F5C, 0xFF2A3880, 0xFF3B4FAB, 0xFF4F68C8, 0xFFD4A84A]` (bottom → top: deep lapis base → bright lapis → pyrite-gold crown). The gold crown is the traditional pyrite-fleck reference that distinguishes lapis lazuli from plain blue stone. Same 5-int / layer-ordered contract as the `zig_jade` palette (C.2 PR 2).
- **Resolves C.4 UnknownCosmetic for IRON_SOLES**: `CosmeticRepository.idExists("lapis_lazuli_skin")` now returns `true`, so `ClaimMilestone(Milestone.IRON_SOLES)` passes the pre-flight cosmetic-id check and runs the atomic credit (200 Gems + 50 Power Stones). Before this PR, IRON_SOLES claims returned `UnknownCosmetic("lapis_lazuli_skin")` and rejected cleanly; post-PR, they return `Success`.
- **ClaimMilestoneTest**: rewired 12 → 11 cases. Removed `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (prod semantics flipped — lapis_lazuli_skin is now seeded, IRON_SOLES no longer returns UnknownCosmetic). Switched `UnknownCosmetic rejects claim before the atomic DAO call with no credit` to target MARATHON_WALKER (garden_ziggurat_skin still unknown) so the test reflects prod behaviour. Rewrote the former `milestone with matching cosmetic id credits rewards via atomic path` as `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl` — uses `CosmeticRepositoryImpl(FakeCosmeticDao())` instead of a `FakeCosmeticRepository` fixture, proving the full `SEED_COSMETICS → ensureSeedData → idExists → ClaimMilestone atomic credit → wallet` chain on the real implementation.
- **CosmeticRepositoryImplTest**: updated 7 → 8 cases. +1 new `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` with exact-value palette assertion matching the `zig_jade` pattern (content-as-code contract). Updated `ensureSeedData is idempotent` count assertion 8 → 9. Updated `ensureSeedData inserts newly-added rows on partial catalogue upgrade` count 8 → 9 with a new lapis palette check alongside the existing jade one (proves both palettes land on the same upgrade path). Updated `ensureSeedData preserves player state on existing rows` count 8 → 9. Updated the `other seeded ziggurat cosmetics have null overrideColors` comment to reflect that both `zig_jade` (PR 2) and `lapis_lazuli_skin` (PR 3) now ship palettes.
- **486 tests, unchanged count** (-1 ClaimMilestoneTest removed case + 1 CosmeticRepositoryImplTest new lapis palette case = 0 net). Zero balance-math changes.
- **Next up (C.2 PR 3b / 3c):** `garden_ziggurat_skin` (MARATHON_WALKER, 600 Gems) and `sandals_of_gilgamesh` (GLOBE_TROTTER, 500 Gems). Each PR will flip one more `UnknownCosmetic` detection to `Success`. After all 3 land, all 6 Milestone entries are fully claimable end-to-end — closes the "shipped but disabled" monetization gap that has been tracked since Plan R2-11.

### Phase C.2 PR 3b + 3c — Seed remaining milestone cosmetics (MARATHON_WALKER + GLOBE_TROTTER) (2026-05-08)
- **CosmeticRepositoryImpl.SEED_COSMETICS**: +2 rows — `garden_ziggurat_skin` (ZIGGURAT_SKIN, 600 Gems, MARATHON_WALKER reward) and `sandals_of_gilgamesh` (ZIGGURAT_SKIN, 500 Gems, GLOBE_TROTTER reward). Total seed count: 9 → 11. Both placed directly after `lapis_lazuli_skin` so the 4 palette-shipping cosmetics appear as a block at the top of the catalogue. Neither is in `StoreScreen.ENABLED_COSMETIC_ID` — both are milestone-acquisition-only, still show "Coming Soon" in the Store.
- **ZIGGURAT_COLOR_LOOKUP**: +2 palettes.
  - `garden_ziggurat_skin`: `[0xFF8B4726, 0xFFAD7B4C, 0xFF5E7F47, 0xFF7BA85A, 0xFFE0C890]` — Hanging Gardens biome theme. Terracotta ziggurat base → sun-bleached sandstone → mossy vines → lush foliage → pale bloom canopy. Evokes the stone structure overtaken by cascading gardens.
  - `sandals_of_gilgamesh`: `[0xFF3B2A1A, 0xFF6B4A2A, 0xFF8B6B42, 0xFFB89152, 0xFFE8C068]` — weathered bronze → polished bronze → gold crown. Heroic motif; the gold crown echoes `lapis_lazuli_skin` as a shared "legendary" visual cue.
- **Category decision for `sandals_of_gilgamesh`:** the id carries footwear semantics ("walking the edges of the world") but the cosmetic is implemented as a `ZIGGURAT_SKIN` — a bronze Gilgamesh-themed ziggurat variant. Kept the existing category + pipeline intact (no new `CosmeticCategory` enum value, no schema change, no new rendering path). Description text ("Bronze ziggurat in honour of Gilgamesh, whose sandals walked the edges of the world") bridges the name-vs-implementation gap. Revisit a `PLAYER_AVATAR` category only if future milestones introduce multiple player-avatar cosmetics.
- **Resolves the remaining 2 C.4 UnknownCosmetic detections**: `ClaimMilestone(MARATHON_WALKER)` now returns `Success` (600 Gems credited); `ClaimMilestone(GLOBE_TROTTER)` now returns `Success` (500 Gems credited). Combined with C.2 PR 3 (IRON_SOLES), all 3 previously-mismatched milestone cosmetic ids are fixed. **All 6 Milestone entries now claim cleanly end-to-end.**
- **ClaimMilestoneTest**: 11 → 11 cases (net 0: -2 + 2). Removed both `UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER` and `... for GLOBE_TROTTER` (prod semantics flipped to Success for both). Kept the `UnknownCosmetic rejects claim before the atomic DAO call with no credit` synthetic regression guard (still uses MARATHON_WALKER against the empty fake — no prod Milestone currently reaches this rejection path, but the guard protects against future content work introducing a new Milestone with an unseeded Cosmetic reward). Setup comment rewritten to reflect: no more prod mismatches. Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl` — same shape as the existing IRON_SOLES test. Together with the IRON_SOLES test, every Milestone with a Cosmetic reward has a dedicated end-to-end success test.
- **CosmeticRepositoryImplTest**: 8 → 10 cases (+2). Added `C2PR3b - garden_ziggurat_skin propagates hanging-gardens palette` + `C2PR3c - sandals_of_gilgamesh propagates bronze-ziggurat palette`, each with exact-value palette assertion matching the `zig_jade` / `lapis_lazuli_skin` pattern (content-as-code contract). Updated all 3 count assertions (9 → 11): idempotency, partial-catalogue upgrade (now verifies all 4 palette-shipping cosmetics land correctly on the same upgrade path), existing-row preservation. Updated the `other seeded ziggurat cosmetics have null overrideColors` comment to list all 4 palette cosmetics.
- **488 tests** (486 → 488 via +2 net). Zero balance-math changes.
- **Monetization gap closed.** The RO-07 "shipped but disabled" cosmetic gap that has been tracked since Plan R2-11 is fully resolved: renderer pipeline live (C.2 PR 1), first store cosmetic live (C.2 PR 2 `zig_jade`), all 3 milestone cosmetics live (C.2 PR 3 / 3b / 3c), UnknownCosmetic detection still guards against regressions (C.4), `ensureSeedData` lands new rows on any install (fix). Players who hit IRON_SOLES / MARATHON_WALKER / GLOBE_TROTTER now get their full rewards atomically.

### Current state
- **531 JVM tests** green (412 baseline → 488 after C.2 PR 3b+3c → 510 after C.5 PR 1 → 514 after C.5 PR 2 → 522 after C.6 PR 1 → 526 after C.6 PR 2 → 531 after the battle-step-credit hotfix). Zero balance-math changes across all of Phase B, Phase C, and the hotfix.
- Plan 31 (Play Console & Store Publication) remains the only release-blocker; unblocked since the end of Plan R2.
- **RO-02 complete: 5/5 atomic sites landed** (`PurchaseUpgrade`, `AwardBattleSteps`, `StepCrossValidator`, `ClaimMilestone`, `runEndRoundPersistence`).
- **RO-03 complete: 2/2 resilience sites landed** (extraction + `onCleared` guard).
- **RO-07 complete for the milestone-cosmetic gap: C.2 PRs 1+2+3+3b+3c + C.4 + ensureSeedData fix landed.** All 6 Milestone entries claim cleanly end-to-end. Only 3 of 7 seeded ziggurat skins ship palettes (the 3 milestone rewards + `zig_jade`); the 3 original placeholder skins (`zig_obsidian`, `zig_crystal`, `zig_golden`) + 4 non-ziggurat seeds (`proj_*`, `enemy_*`) remain "Coming Soon" in the Store until their visual content is designed.
- Real Billing/Ad SDK swaps (Phase C.5/C.6) still gated on ADR-0005/ADR-0006 — now the top release-critical item.
- B.4 (FollowOnPipeline extraction) + B.5 (UpdateMissionProgress use case) remain as pure debt, not release blockers.

## [1.0.0] — 2026-03-10

### Core Gameplay
- Step-powered progression: earn Steps currency by real-world walking via device step counter
- Workshop with 23 permanent upgrade types across Attack, Defense, and Utility categories
- Tower defense battle system with custom SurfaceView renderer and fixed-timestep game loop
- 6 enemy types (Basic, Fast, Tank, Ranged, Boss, Scatter) with wave-based spawning
- Stats resolution engine combining Workshop (permanent) × In-Round (temporary) upgrades multiplicatively
- In-round upgrades purchased with Cash earned from kills, with interest mechanic
- Crit system, knockback, lifesteal, thorn damage, death defy, damage/meter bonus
- Advanced combat: orbiting projectiles, multishot, bounce shot

### Progression
- 10 tier system with wave-based unlock requirements and escalating battle conditions (Tier 6+)
- 5 narrative biomes: Hanging Gardens, Burning Sands, Frozen Ziggurats, Underworld of Kur, Celestial Gate
- Labs research system with 10 research types, real-time background timers, up to 4 slots, Gem rush
- Cards system with 9 card types, 3 rarities, Card Dust upgrades, loadout of 3
- 6 Ultimate Weapons unlocked with Power Stones, loadout of 3, cooldown-based activation
- 4 Step Overdrive types for mid-battle 60-second combat buffs

### Economy & Rewards
- Walking Encounters with seeded random Supply Drops delivered via push notification
- Weekly step challenges with Power Stone rewards (50k/75k/100k thresholds)
- Daily login streaks with Gem and Power Stone rewards
- 6 walking milestones from First Steps to Globe Trotter
- 3 random daily missions refreshed at midnight (walking/battle/upgrade categories)
- Wave milestone Power Stone awards on personal-best waves

### Battle Polish
- Particle effects: projectile trails, enemy death bursts (6 types), UW activation spectacles, overdrive auras
- Screen shake with decaying amplitude
- Wave announcements with boss warnings and cooldown countdowns
- Floating text for cash pickups
- Biome-themed color palettes and ambient background particles
- Sound effects (7 types) with volume control and shoot throttling
- Speed controls: 1x / 2x / 4x

### Infrastructure
- Foreground step-counting service (health type, START_STICKY) with boot receiver
- WorkManager 15-minute periodic sync with Health Connect cross-validation and gap-filling
- Activity Minute Parity: indoor workout minutes converted to step-equivalents
- Anti-cheat: 200 steps/min rate limit, step velocity analysis, 50k daily ceiling, graduated Health Connect cross-validation (4 offense levels)
- SQLCipher encrypted Room database with Android Keystore key management
- Home screen widget (2×2) with step count display
- Smart upgrade proximity reminders
- Milestone and wave record notifications

### Monetization (Stub)
- Store screen with Gem packs, ad removal, Season Pass, and cosmetic items
- Stub billing and reward ad implementations (real SDK integration in future update)

### Stats & UI
- Stats screen with walking history bar charts (daily/weekly/monthly), battle stats, all-time aggregates
- Currency dashboard with weekly challenge progress and login streak tracking
- Missions screen with daily missions and walking milestones
- Settings screen with 4 notification toggles
- 12-screen Compose navigation with bottom nav bar

### Testing
- 397 JVM unit tests covering all use cases, domain models, balance validation, ViewModels, anti-cheat, effects, step ingestion coordination, widget balance, walking mission progress, activity-minute idempotency, currency guards, UX feedback, and integration tests

### Remediation (R01–R05)
- Fixed step double-crediting between StepCounterService and StepSyncWorker via heartbeat + Room baseline coordination
- Fixed Health Connect escrow to actually deduct suspicious steps from player balance
- Fixed battle engine receiving empty workshop utility levels (CASH_BONUS/CASH_PER_WAVE/INTEREST)
- Hidden unimplemented STEP_MULTIPLIER and RECOVERY_PACKAGES from Workshop UI
- Disabled backup, added SQLCipher key recovery on keystore mismatch

### Remediation (R06–R09)
- Fixed widget showing 0 balance — now displays real step balance after crediting
- Fixed widget click target not responding (missing android:id on root layout)
- Walking missions now update live on step credit, not only when screen opens
- Fixed notification settings label to accurately describe toggle behavior
- lastActiveAt now updated on app resume for smart reminder accuracy
- Fixed deep-link navigation when app is already open (warm-start intent handling)
- Fixed Season Pass expiry check in Store screen (was ignoring expiry timestamp)
- Fixed adRemoved state lost on Play Again in battle

### Remediation (R10–R11)
- Added user feedback messages (snackbar) for failed purchases across Workshop, Cards, Labs, Store
- Added double-tap guards on all purchase/ad actions — prevents overlapping coroutines
- Added DAO-level non-negative guards on gems, power stones, and card dust (MAX(0, ...))
- Fixed midnight date staleness in Missions, Home, and Stats screens
- Added content descriptions to all symbol-only battle controls for TalkBack accessibility
- Added semantics to Ultimate Weapon bar slots
- Replaced placeholder contact emails with real address in privacy policy, store listing, and Health Connect activity
- Fixed README instrumented test reference (deferred, not available)

### Remediation (R12)
- Added Robolectric integration tests for widget SharedPreferences round-trip
- Added deep-link intent routing tests
- Added Room v7 schema round-trip tests (PlayerProfile, DailyStepRecord, WorkshopUpgrade)
- Added end-to-end escrow lifecycle integration tests (escrow→release and escrow→discard)

### Release Prep
- R8/ProGuard rules hardened for Room, Hilt, SQLCipher, Health Connect, sensors, WorkManager
- Release signing configuration with gitignored keystore.properties
- Privacy policy and Play Store listing text

### Scaffold & Foundation
- Gradle 9.3.1 project with Kotlin DSL and version catalog
- Hilt DI setup with `@HiltAndroidApp`
- Room database skeleton, Compose theme, single Activity
- Written detailed plan files for Plans 02–30 in `docs/plans/`
- All core domain models (Plan 01): Currency, PlayerWallet, UpgradeType (23), TierConfig (1–10), BattleCondition (7), Biome (5), EnemyType (6), UltimateWeaponType (6), OverdriveType (4), ResearchType (10), CardType (9), CardRarity (3)
- CalculateUpgradeCost and CanAffordUpgrade use cases
