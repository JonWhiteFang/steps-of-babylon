# CLAUDE.md — Steps of Babylon

This is the single canonical **operating guide** for working in this repository. It is auto-loaded
every session. Authority lives here; *progress and decisions* live in the committed memory spine
under `docs/agent/` (which points back here and never restates these rules).

`docs/agent/START_HERE.md` is the quick **agent contract** (constraints + build commands + where
memory lives). This file is the fuller manual: the protocol, architecture, conventions, and domain
model. Read START_HERE first if you only read one thing.

## Keeping this file honest (read before editing CLAUDE.md)

This file is auto-loaded every session, so bloat and drift cost every future session. Guard it:

- **Belongs here (stable):** the agent protocol, architecture shape, conventions, domain concepts,
  fragile-zone pointers. Things that change rarely and are true regardless of where the project is.
- **Does NOT belong here (volatile → lives elsewhere):** anything with a **date, PR number, version
  code, or test-count history**. Current status → `docs/agent/STATE.md`. Per-PR narrative →
  `docs/agent/RUN_LOG.md` / `CHANGELOG.md`. Plan/roadmap detail → `docs/plans/master-plan.md`.
  Migration/version history → `CHANGELOG.md`.
- **Rule of thumb:** if you're tempted to append a status update or a "as of <date>" clause to
  CLAUDE.md, it belongs in STATE/RUN_LOG/CHANGELOG instead — link to it, don't inline it.
- **The one live number kept here:** the headline test count (in Testing), because it's a fast
  drift signal. Update it when it changes; keep the *detail* in CHANGELOG.

## Project Memory (read first)

Treat these as the project source of truth — never rely on chat history.

| File | Purpose |
|---|---|
| `docs/agent/START_HERE.md` | Agent contract — what this is, how to work here |
| `docs/agent/STATE.md` | One-page live snapshot (current objective, priorities, next actions, fragile zones) |
| `docs/agent/CONSTRAINTS.md` | Architecture invariants, security rules, "never do" list |
| `docs/agent/RUN_LOG.md` | Append-only log of every work session |
| `docs/agent/DECISIONS/` | Architecture Decision Records (ADRs) |
| `docs/plans/master-plan.md` | Full plan index, dependency graph, critical path, status tracker |
| `docs/steering/` | Reference docs (tech stack, structure, source-file index, library guides) — read on demand |

## Always-on memory rules

- The memory spine (above) is truth. Do NOT rely on chat history as the project source of truth.
- **Before** planning or changing code: read the spine + check git state. (The SessionStart
  preflight hook injects git state + STATE.md's live sections automatically.)
- **After** finishing work: update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.
- If you made/changed a meaningful decision: create/update an ADR in `docs/agent/DECISIONS/`.
- Keep `STATE.md` to one page. Push detail into RUN_LOG / ADRs.
- The end-of-session write is automated by the `/checkpoint` skill — run it to finish a session.

## Agent protocol

### Context Preflight (at session start)
1. Read `docs/agent/START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`.
2. Review the latest `RUN_LOG.md` entry and any ADRs referenced in `STATE.md`.
3. Check repo state: `git status`, `git log -n 10 --oneline`.
4. Output a brief "Session Brief" (~10 bullets): what the project is, current state,
   constraints/invariants, today's objective, risks/unknowns.

### Adversarial Review Gate (mandatory before acting on a spec or a plan)
Every **design spec** and every **implementation plan** MUST pass a full adversarial review before the
next stage begins — review the spec before writing the plan; review the plan before any implementation.
This is the standing default; the developer should not have to ask for it each time.

The review is a multi-agent `Workflow` (see the Workflow tool's quality patterns), shaped as:
1. **Code-grounded multi-dimension fan-out** — one reviewer per dimension (e.g. code-grounding of every
   `file:line` claim, API/framework correctness, fragile-zone & invariant safety, scope completeness vs
   the issue/review, test-strategy feasibility, internal consistency/ambiguity). Each finding must cite
   the **actual code** it checked, not just the spec's prose.
2. **Adversarial verify** — a skeptic re-checks each finding against spec + code and tries to **refute**
   it; only `confirmed`/`partial` findings survive (default-to-refuted discipline).
3. **Synthesis** — apply every surviving finding to the artifact, then commit the amendments with a
   message summarising findings (total / surviving / refuted) and the substantive fixes.

Severity-scale the response: a quick spec gets a leaner fan-out; "be thorough"/audit-grade work gets the
full pattern with 3–5-vote verification. Do **not** advance to the next stage with unaddressed
`critical`/`major` findings.

**If ultracode is OFF** (a session reminder will say so), this gate's multi-agent form is disabled by the
opt-in rules — do **not** silently skip it. **Flag to the developer that the artifact is unreviewed and
ask** whether to (a) turn ultracode on for the review, (b) run a lighter single-agent review inline, or
(c) proceed without one. Never advance spec→plan→implementation on an unreviewed artifact without that
explicit choice.

### PR Task-List Convention (mandatory for every code-changing PR)
Every task list for a PR that changes production code, tests, or configuration MUST include
these two steps, in this order, immediately before the commit step:
1. **Sync current-state docs** affected by the change (runs BEFORE the STATE/RUN_LOG update).
2. **Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.**

Current-state docs to audit for every PR (touch only if the PR actually invalidates them):
- `CLAUDE.md` — architecture, conventions, domain concepts, headline test count. Update the test
  count when it changes; otherwise touch only if the PR changed something stable documented here.
- `CHANGELOG.md` — add a section for the PR; update the current-state block if phase status / test
  count / roadmap shifted.
- `docs/steering/source-files.md` — add entries for new files; update existing entries when a file's
  responsibility shape changed.
- `docs/steering/structure.md` — update when new modules/directories/architectural elements land.
- `docs/database-schema.md` — only if the Room schema or a migration changed.
- `docs/steering/tech.md`, `docs/steering/lib-*.md` — only if dependency versions/conventions/patterns changed.
- `docs/plans/master-plan.md` — update the status tracker when a plan's state changes.
- `README.md` — only if user-facing build/run instructions changed.

Historical artifacts — **NEVER modify** in a current-PR doc sweep:
- `docs/agent/RUN_LOG.md` prior entries (appending the current PR's entry is fine; editing old ones is not).
- `docs/archive/completed-plans-v1.0/*` — the shipped v1.0 plan files (Plans 01–30, 10b, R, R2, R3, R4, RO-*); historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `docs/archive/pre-claude-devdocs/*`, `docs/archive/smoke_tests/*` — pre-Claude analysis corpus; historical per HEAD pin.
- Individual `docs/agent/DECISIONS/ADR-*.md` files — amend status only if explicitly warranted.

### End-of-Run memory writes (run `/checkpoint`)
1. Current-state docs synced per the PR Task-List Convention above.
2. Update `docs/agent/STATE.md` (what changed + what's next).
3. Append `docs/agent/RUN_LOG.md` with what you did and what remains.
4. Add/update an ADR if you made a non-trivial decision.

---

## Project Overview

Steps of Babylon is an Android mobile game that combines idle tower defense gameplay with a
real-world step counter. Players earn **Steps** by physically walking, then spend them to upgrade an
ancient ziggurat that fights wave-based battles against mythic enemies. Progression is gated entirely
by physical activity.

See `docs/StepsOfBabylon_GDD.md` for the full game design document.

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **Package:** `com.whitefang.stepsofbabylon`
- **Min SDK:** 34 (Android 14) · **Target/Compile SDK:** 36
- **Version:** see `app/build.gradle.kts` (`versionCode`/`versionName`). Release/version history lives in `CHANGELOG.md` — do not inline it here.
- **Architecture:** MVVM + Clean Architecture (`presentation → domain ← data`)
- **UI:** Jetpack Compose (menus/screens) + custom `SurfaceView` (battle renderer)
- **DI:** Hilt (with KSP — not kapt)
- **Database:** Room (SQLite) with SQLCipher encryption — offline-first, all game state stored locally
- **Background:** WorkManager + Foreground Service (step counting)
- **Step Tracking:** Android Sensor API (`TYPE_STEP_COUNTER`) + Health Connect SDK (cross-validation, Activity Minute Parity)
- **Build:** Gradle 9.5.1 (Kotlin DSL), version catalog at `gradle/libs.versions.toml` (never hardcode versions). Multi-module since #26: `:app` (the shipped application) + `:baselineprofile` & `:macrobenchmark` (`com.android.test` dev-tooling modules — Baseline Profile generation + Macrobenchmark; never shipped, never CI-gated on timings).
- **CI/CD note (#26):** the PR gate also type-checks the two benchmark modules; perf benchmarks run locally on a device, not in CI (emulator timings are unreliable).
- **Security:** SQLCipher (DB encryption), Android Keystore (key management), R8 (obfuscation), network security config (cleartext blocked)
- **CI/CD:** GitHub Actions — PR gate (lint + unit + assembleDebug + schema-drift), instrumented emulator suite, a release lane that ships a signed AAB to the Play internal track on a `v*` tag, and a Pages lane (`pages.yml`) that publishes the hosted privacy policy from the top-level `site/` folder only (never the internal `docs/` tree). See `docs/plans/plan-32-ci.md` + ADR-0018.

## Architecture

```
app/src/main/java/com/whitefang/stepsofbabylon/
├── data/               # Android-dependent layer
│   ├── local/          # Room database, entities, DAOs, Migrations, DatabaseKeyManager
│   ├── repository/     # Repository implementations (Room-backed)
│   ├── sensor/         # Step sensor data source, rate limiter, velocity analyzer, daily step manager
│   ├── healthconnect/  # Health Connect client, step reader, cross-validator, gap filler, activity-minute converter
│   ├── anticheat/      # Anti-cheat preferences / offense tracking
│   ├── onboarding/     # OnboardingPreferences (device-local first-launch completion flag, SharedPreferences)
│   ├── billing/        # Play Billing v8 (BillingManagerImpl + internal SDK adapters)
│   ├── ads/            # AdMob v25 + UMP consent (RewardAdManagerImpl + internal adapters)
│   └── time/           # TimeProvider abstraction (testable clock)
├── domain/             # Pure Kotlin — ZERO Android imports (enforced by DomainPurityTest)
│   ├── model/          # Currency, PlayerWallet, PlayerProfile, all enums + game domain models
│   ├── repository/     # Repository interfaces
│   ├── usecase/        # All game-logic use cases (cost calc, stats resolution, purchases, claims, …)
│   ├── battle/         # Pure-domain battle simulation (extracted from the renderer — V1X-09, ADR-0012)
│   │   ├── engine/     #   Simulation, SimulationMath, SimulationEvent — the testable game-loop core
│   │   └── entity/     #   EntityProtocol + *State (Projectile/Orb/Enemy/Ziggurat simulation state)
│   └── time/           # Time domain abstractions
├── presentation/       # ViewModels, Compose screens, SurfaceView battle renderer
│   ├── navigation/     # Screen routes, BottomNavBar
│   ├── onboarding/     # First-launch tutorial carousel + permission primer (OnboardingScreen/ViewModel/Slide)
│   ├── home/ workshop/ weapons/ labs/ cards/ supplies/ economy/ missions/ settings/ stats/ store/ help/
│   ├── battle/         # BattleScreen, BattleViewModel, GameSurfaceView, GameLoopThread
│   │   ├── engine/     #   GameEngine (render/presentation shell), WaveSpawner, EnemyScaler, CollisionSystem, Entity
│   │   ├── entities/   #   ZigguratEntity, EnemyEntity, ProjectileEntity, EnemyProjectileEntity, OrbEntity
│   │   ├── effects/    #   ParticlePool, EffectEngine, ScreenShake, DeathEffect, UWVisualEffect, FloatingText, …
│   │   ├── biome/      #   BiomeTheme, BackgroundRenderer
│   │   └── ui/         #   BattleControlRail, InRoundUpgradeMenu, PostRoundOverlay, PauseOverlay, HealthBarRenderer, UltimateWeaponBar, …
│   ├── audio/          # Sound/music playback
│   └── ui/theme/       # Design tokens: Color (brand + role tokens), Type (SobTypography), Shape (SobShapes), Theme
├── di/                 # Hilt modules: Database, Repository, Step, HealthConnect, Ad, Billing, CoroutineScope, Time
└── service/            # Foreground step service, WorkManager workers, boot receiver, notifications, widget
```

Follow Clean Architecture layers: `presentation → domain ← data`. **The domain layer has zero
Android dependencies** — machine-enforced by `architecture/DomainPurityTest`.

See `docs/steering/source-files.md` for the full file-by-file index, and `docs/steering/structure.md`
for the structural reference.

## Plans & Roadmap

Development follows a master plan with **38 entries** (Plans 01–32, plus 10b, R, R2, R3, R4, V1X).
**`docs/plans/master-plan.md` is the single source** for the full index, dependency graph, critical
path, and live status tracker — it is not duplicated here (that copy always drifts). For the current
objective and what's done vs. in-flight, read `docs/agent/STATE.md`.

Key reference documents:

| Document | Path |
|---|---|
| Game Design Document | `docs/StepsOfBabylon_GDD.md` |
| Master Plan (index, deps, status) | `docs/plans/master-plan.md` |
| Battle Formulas | `docs/battle-formulas.md` |
| Database Schema | `docs/database-schema.md` |
| Step Tracking | `docs/step-tracking.md` |
| Monetization | `docs/monetization.md` |
| Architecture | `docs/architecture.md` |
| Security Model (consolidated) | `docs/steering/security-model.md` |

## Key Domain Concepts

- **Steps** — primary permanent currency, earned only from real-world walking/activity. Never generated in-game.
- **Cash** — temporary in-round currency from killing enemies. Resets each round.
- **Gems** — permanent premium currency from milestones and daily logins.
- **Power Stones** — permanent currency for Ultimate Weapons, from weekly challenges and boss drops.
- **Workshop** — permanent upgrades (Attack/Defense/Utility) purchased with Steps. See `UpgradeType`.
- **Labs** — time-gated research projects initiated with Steps, completed over real time. See `ResearchType`.
- **Cards** — per-round bonus items (3 equipped max), acquired via Gem-purchased packs. Copy-based
  progression (Card Dust was removed in R4-08, ADR-0010). 3 rarities. See `CardType`.
- **Ultimate Weapons (UWs)** — auto-triggering abilities (3 equipped max), unlocked with Power Stones,
  upgraded along 3 paths (R4-06, ADR-0008). See `UltimateWeaponType` (6 types).
- **Tiers** — difficulty levels (1–10) with escalating battle conditions and cash multipliers. See `TierConfig`.
- **Biomes** — 5 narrative environments tied to tier ranges (Hanging Gardens → Burning Sands →
  Frozen Ziggurats → Underworld of Kur → Celestial Gate).
- **Rapid Fire** — in-round Step-spend combat boost (R4-03; replaced the removed Step Overdrive mechanic).
- **Walking Encounters** — Supply Drop rewards delivered via push notifications during walks.
- **Activity Minute Parity** — Health Connect Active Minutes converted to Step-equivalents for indoor workouts.
- **Enemies** — 6 types (Basic, Fast, Tank, Ranged, Boss, Scatter) with distinct speed/health/damage multipliers.

> Exact counts of upgrade/research/card types live in the enums (`domain/model/`) and are validated by
> balance tests — read the enum, don't trust a number cached in prose.

## Conventions

- Use Kotlin coroutines and Flow for all async operations.
- ViewModels expose `StateFlow` to Compose UI.
- Room is the single source of truth for game state.
- All upgrade cost formulas follow: `baseCost * (scaling ^ level)`.
- Step counting must work reliably when the app is backgrounded or killed.
- Steps can **never** be generated passively in-game — this is a hard design rule.
- Anti-cheat: rate-limit at 200 steps/min, step velocity analysis (shaker/spoof detection), daily
  ceiling of 50,000 steps, graduated Health Connect cross-validation (4 offense levels), activity
  minute validation, per-minute overlap deduction.
- Domain models are pure Kotlin — no Android imports in `domain/`.
- Loadouts enforce max capacity: 3 UWs, 3 Cards.
- Currency spends should go through the **atomic guarded-deduct pattern** (guarded DAO `UPDATE …
  WHERE balance >= cost` returning rows-affected; grant only on success, inside a `@Transaction`).
  See `WorkshopDao.purchaseUpgradeAtomic` / `MilestoneDao.claimMilestoneAtomic` for the template.
  `PlayerRepository.spendGems` / `spendPowerStones` / `spendStepsIfSufficient` return `Boolean`
  (rows-affected > 0) — **gate the grant on that result**, never on a stale wallet snapshot (#122,
  ADR-0020). `spendSteps` is the exception: it keeps the `MAX(0,…)` clamp for the anti-cheat escrow
  clawback. One-shot claims (supply drops, daily missions) use a guarded `UPDATE … WHERE id AND
  claimed = 0` returning rows-affected and **mark-first** (credit only when it returns 1).
- **Generators that "create once per key" need a DB-level unique index, not a read-then-insert check**
  (#127). `GenerateDailyMissions` guarded on `getByDateOnce(date).isEmpty()` with no uniqueness, so two
  concurrent VM inits both passed the check and inserted → duplicate, independently-claimable rows. The
  fix is a `(date, missionType)` unique index + `@Insert(onConflict = IGNORE)` (the index is the
  authoritative guard; the read-check is racy on a WAL connection pool). Apply the same pattern to any
  new per-key generator.

## Battle Renderer

The battle screen uses a custom `SurfaceView` with a game loop (not Compose). Keep rendering separate
from game logic — the simulation has been extracted to a pure-domain core:

- **`domain/battle/engine/Simulation`** — pure-Kotlin game-loop core (cash economy, round-progress
  counters, entity tick, collision sweep, UW lifecycle timers, `SimulationEvent` flow). Fully
  JVM-testable, no Android. This is where game-logic changes should land. (V1X-09, ADR-0012.)
- **`presentation/battle/engine/GameEngine`** — the presentation/render shell that delegates simulation
  to `Simulation` and keeps render + UW activation side-effects.
- **`GameLoopThread`** runs `update()`/`render()` on a dedicated thread with a fixed timestep. Its
  per-tick `update()`/`render()` is wrapped in a `try/catch` (#190): on a throw it records a crash
  breadcrumb, stops the loop, and fires `onLoopError` → a "Battle error" UI state — **never silent
  process death**. Don't remove the guard or let the loop run `update()`/`render()` unguarded.
- **Stats resolution** combines Workshop (permanent) × In-Round (temporary) upgrades multiplicatively.
- **Wave timing:** 26s spawn phase + 9s cooldown between waves. **Speed controls:** 1x / 2x / 4x.

> ⚠️ **Thread-safety:** the game loop runs on its own thread while the UI/main thread can mutate engine
> state. `GameEngine.entities` is now guarded by a private `entitiesLock` monitor — every region that
> structurally mutates or iterates it (`update`, `init`, `applyStats` orb-reconcile, `render` via an
> under-lock snapshot, `aliveEnemyCount`) holds the lock (#118 fix). `GameEngine.uwStates` is on the
> SAME monitor: `updateUWs` iterates it under the tick lock, and `initUWs` (main-thread replay) +
> `uwSnapshot()` (the 200ms poll read) now take `entitiesLock` too (#191 CONC-2). `EffectEngine`'s
> `effects`/`pendingEffects` are guarded by their OWN private `effectsLock` (#191 CONC-1) — `addEffect`
> (off-thread), the `update` drain/deferred-sweep, and the `render` snapshot all hold it; per-effect
> `update`/`render` and the Canvas draw run OUTSIDE it. **Lock order is acyclic: `entitiesLock` (outer,
> held across the whole tick) → `effectsLock` (inner); never the reverse.** Any NEW structural mutation
> of a shared engine collection must take the same lock or be confined to the loop thread — see
> `docs/external-reviews/` and the remaining `severity:major` battle issues.
>
> ⚠️ **HUD enemy count is derived, not tallied (#146):** the wave-header "M enemies" reads
> `GameEngine.aliveEnemyCount()` (counts live `EnemyEntity` in the entity list under `entitiesLock`).
> Do NOT reintroduce a hand-kept counter — the removed `WaveSpawner.enemiesAlive` tally drifted
> negative because SCATTER children bypassed its only increment and `onDeath` re-fires double-counted.
> Relatedly, `EnemyEntity.takeDamage` is guarded by `if (!isAlive) return 0.0` so a second projectile
> on a corpse can't re-fire `onDeath` (double-credit) — defense-in-depth complementing #125.

## Known fragile zones & active risk

`docs/agent/STATE.md` holds the live "do-not-touch / fragile zones" list. For known open defects,
check the GitHub issues labelled `severity:major` / `severity:minor` and the dated reports under
`docs/external-reviews/`. Do not assume the codebase is defect-free because tests pass — several
known concurrency/economy issues are reachability-confirmed but not yet fixed.

## Testing

- Unit test domain use cases and game logic (cost calculations, damage formulas, tier progression).
- Use fakes for repositories in ViewModel tests (`test/fakes/`).
- **Frameworks:** JUnit Jupiter + kotlinx-coroutines-test for JVM unit tests (pure JVM, no emulator);
  JUnit 4 + AndroidJUnit4 + Hilt-android-testing for instrumented tests (needs a connected emulator, API 34+).
- **Run:** `./run-gradle.sh testDebugUnitTest` (JVM) · `./run-gradle.sh :app:connectedDebugAndroidTest` (instrumented — scope to `:app`; the benchmark modules' connected tests refuse a debuggable build).
- **Source:** `app/src/test/java/com/whitefang/stepsofbabylon/` (JVM) and
  `app/src/androidTest/java/com/whitefang/stepsofbabylon/` (instrumented).
- **Headline count: 1093 JVM tests + 9 instrumented tests.** Update this line when it changes; the
  per-PR breakdown and what's-covered detail lives in `CHANGELOG.md` / `RUN_LOG.md`, not here.
- **Notable guards:** `architecture/DomainPurityTest` (fails if `domain/` imports any Android package);
  `SimulationTest` (the extracted pure-domain game-loop core); `BattleSurfaceLifecycleTest` +
  `DeepLinkIntentTest` (instrumented, real-framework regression guards).

## Important Notes

- This is a solo-experience game — no multiplayer, no server backend required for v1.0.
- All monetization is cosmetic or convenience. Steps are never purchasable with real money.
- Accessibility is a priority for post-v1.0: TalkBack support, color-blind modes, Activity Minute
  Parity for non-ambulatory users (already implemented in Plan 05).
- **Gradle in non-TTY environments:** Gradle buffers output when stdout isn't a terminal (e.g., CI).
  Use `./run-gradle.sh <task>` instead of `./gradlew <task>` to avoid hanging. The script is gitignored
  — see `README.md` for how to recreate it.
