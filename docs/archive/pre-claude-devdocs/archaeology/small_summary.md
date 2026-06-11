# Steps of Babylon — Non-Technical Summary

*Archaeology Phase 1 — written from source code, not existing documentation.
Where code and docs disagree, this summary follows the code and flags the
discrepancy.*

## What this project is, in one paragraph

Steps of Babylon is a single Android phone game (one app, one module). You earn
an in-game currency called **Steps** by physically walking around in the real
world — the phone's step-counter sensor drives everything. You then spend those
Steps to permanently upgrade an ancient "ziggurat" tower. Periodically you
launch into a **Battle**, where the tower automatically fights waves of
mythological enemies and you make short-term decisions (buy in-round upgrades
with battle-earned Cash, fire Ultimate Weapons, pop Step Overdrive). Winning
further waves unlocks higher difficulty tiers and reveals new visual environments.
The game is meant to be played a little bit throughout the day: walk while living
your life, check in to spend your Steps, play a short round, repeat.

## Primary deliverable

There is exactly one deliverable:

- `:app` — an Android application, package `com.whitefang.stepsofbabylon`,
  version `1.0.0` / versionCode `1`, minSdk 34 (Android 14), targetSdk 36.

`settings.gradle.kts` only includes `:app`. There are no libraries, secondary
apps, or backend services in this repository. The game is fully offline; all
state lives in an encrypted local SQLite database on the device.

## What the user experiences

From the moment the app is opened:

1. **First-run permission prompts** (handled in `presentation/MainActivity.kt`):
   - Physical Activity / step counter permission.
   - Notifications permission.
   - Health Connect permission (for cross-checking the phone's step count
     against the system's health record and for converting exercise minutes
     into Step credit for people who work out without walking).

2. **A persistent foreground notification** appears as soon as permission is
   granted. It shows today's step count and Step balance and is managed by a
   background service (`service/StepCounterService.kt`) running as a
   "health" foreground service. This is what makes step counting keep working
   while the app is closed, the screen is off, or after the phone reboots (a
   boot receiver restarts the service).

3. **An optional home-screen widget** (2x2) shows the same day-total and
   balance, updated through a throttled helper.

4. **The main dashboard** (`presentation/home/HomeScreen.kt`) shows:
   - A horizontal tier selector (difficulty 1..n, locked until beaten).
   - A big "Today" card with today's step count.
   - Three currency counters: Steps, Gems, Power Stones.
   - The player's best-ever wave.
   - Entry points to Supplies (inbox of walking rewards), Missions, Settings,
     Store, and a prominent **BATTLE** button.
   - Background colour shifts to match whichever biome the current tier belongs
     to (five hand-themed biomes: Hanging Gardens → Burning Sands → Frozen
     Ziggurats → Underworld of Kur → Celestial Gate).

5. **Twelve navigation screens** reachable from bottom nav or from the home
   buttons (`presentation/navigation/Screen.kt`, wired in `MainActivity.kt`):
   Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy,
   Missions, Settings, Store.

6. **Workshop** (`presentation/workshop/`): permanent upgrades to the tower,
   paid for in Steps. 23 distinct upgrade types, split into Attack / Defense /
   Utility tabs. Cost doubles on a geometric curve as you level each one up
   (`domain/usecase/CalculateUpgradeCost.kt`: `baseCost × scaling^level`).

7. **Battle** (`presentation/battle/`): a full-screen action screen that is
   **not** built with the standard Android UI toolkit — it's a custom
   `SurfaceView` with its own rendering loop on a dedicated thread (60
   updates/sec, fixed timestep). The player sees:
   - A five-layer ziggurat defending the centre-bottom of the screen.
   - Waves of six enemy archetypes (Basic, Fast, Tank, Ranged, Boss, Scatter),
     each with different speed/HP/damage multipliers.
   - Each wave lasts 26 seconds of spawning plus a 9-second cooldown
     (`presentation/battle/engine/WaveSpawner.kt`). Enemies get harder wave
     over wave on a 1.05× curve.
   - A HUD with wave number, enemy count, Cash, battle-Step counter,
     and an Overdrive timer if active.
   - Bottom speed controls (1×/2×/4×), an Ultimate Weapon bar for up to 3
     equipped weapons, an in-round upgrade menu (spend Cash for round-only
     boosts), an Overdrive menu (burn Steps for a 60-second combat buff),
     a pause overlay, and a post-round summary screen.
   - Particle effects, screen shake, wave announcements, biome-coloured
     projectile trails, floating damage/reward numbers, unique death bursts
     per enemy type.

8. **Other screens**:
   - **Labs** — time-gated research (pay Steps, wait real hours, optionally
     spend Gems to finish early). 1–4 slots that can be purchased with Gems.
   - **Cards** — buy card packs with Gems, manage a 3-card loadout, recycle
     duplicates into Card Dust to upgrade kept cards.
   - **Weapons** — unlock and upgrade up to 6 Ultimate Weapons with Power
     Stones, equip up to 3 in a loadout.
   - **Supplies** — inbox of walking-reward drops that arrive via notifications.
     Tapping the notification deep-links straight to this screen.
   - **Economy** — weekly walking challenge (with Power Stone tiers) and daily
     login streak (Gems + Power Stones).
   - **Missions** — three daily missions (walking / battle / upgrade) that
     refresh at midnight, plus long-running walking milestones with Gem /
     Power Stone / cosmetic rewards.
   - **Stats** — walking-history bar chart (today / week / month), battle
     totals, all-time aggregates.
   - **Settings** — toggles for the four notification types.
   - **Store** — Gem packs, Ad Removal, Season Pass, and cosmetic items.

9. **Notifications beyond the persistent step counter**:
   - Supply Drop notifications fired by `DailyStepManager` when the
     randomised trigger conditions hit during walking.
   - Milestone/best-wave notifications.
   - Smart reminders that nudge when an upgrade is almost affordable
     (piggy-backs on the 15-minute step-sync worker).

The developer experience (what's in the repo): Kotlin + Jetpack Compose for
all menu screens, a custom SurfaceView renderer for the Battle screen, Hilt
for dependency injection, Room on SQLCipher for local persistence, WorkManager
for periodic background catch-up, a standard Gradle + Version Catalog build
(`gradle/libs.versions.toml`), and an extensive unit test suite in
`app/src/test/`. Annotation processing is KSP throughout.

## The core loop (confirmed from code)

The single most important flow in the app is step ingestion → wallet →
upgrades → battle → unlocks → more upgrades:

1. **Walk.** The `TYPE_STEP_COUNTER` Android sensor emits deltas through
   `data/sensor/StepSensorDataSource.kt`, which are collected by
   `StepCounterService` running as a `health` foreground service
   (`AndroidManifest.xml` + `service/StepCounterService.kt`).
2. **Validate.** `data/sensor/DailyStepManager.kt` runs each delta through:
   - A 200-steps-per-minute rate limiter (with a 250 burst).
   - A "velocity analyser" that lowers credit if the step pattern looks like
     a shaker/spoof.
   - A hard 50,000-steps-per-day ceiling.
   - Periodic Health Connect cross-validation with a four-level graduated
     response if sensor counts wildly overshoot the system record.
3. **Persist.** Only the remaining "credited" steps are written to the Room
   database (`data/local/AppDatabase.kt`, version 8, 12 entities,
   SQLCipher-encrypted). Room is the single source of truth.
4. **Side-effects fan out** (still inside `DailyStepManager.runFollowOnPipeline`):
   - Widget update.
   - A seeded random supply-drop roll, and a push notification if one triggers.
   - Daily-login and weekly-challenge checks.
   - Walking-mission progress.
5. **Spend Steps** on permanent upgrades (Workshop), timed Labs research,
   or a one-shot battle buff (Step Overdrive). All spending goes through
   use cases in `domain/usecase/` and atomic Room updates.
6. **Play Battle.** The `GameEngine` on a dedicated thread runs the round;
   kills grant Cash (for in-round upgrades) and a small flat Steps reward per
   kill, capped at 2,000 per calendar day by `domain/usecase/AwardBattleSteps.kt`
   (see ADR-0003). Surviving deeper waves records a new best, which unlocks
   new difficulty tiers, which unlocks new biomes, which unlocks harder
   battles. And so on.
7. **Gems** (from milestones, daily logins, store) pay for card packs and
   lab-research "rush". **Power Stones** (from weekly step challenges and
   wave milestones) pay for Ultimate Weapon unlocks and upgrades.

A hard rule enforced in multiple places: Steps can never be created passively
or bought with real money. Only walking (and the capped battle rewards added
in ADR-0003) can mint Steps.

## What is complete vs. actively evolving

The vast majority of the game is implemented and working in code. The evidence
below comes from reading the source, not from the status page in the
planning docs.

**Complete (wired up end-to-end in source):**

- The entire data stack: 12 Room entities + DAOs, SQLCipher passphrase via
  Android Keystore, 8 repository implementations, at least one real Room
  migration (v7 → v8).
- The full step-ingestion pipeline with all four anti-cheat layers and the
  Health Connect cross-validator and gap-filler.
- The 32 domain use cases (all pure Kotlin, no Android imports).
- The battle renderer: dedicated game-loop thread, entity system
  (ziggurat, projectiles, enemies, enemy projectiles, orbs), wave spawner,
  enemy scaler, collision system, in-round upgrade menu, Overdrive menu,
  Ultimate Weapon bar, particle pool, screen shake, biome backgrounds,
  death/UW/overdrive VFX, wave announcements, pause/post-round overlays.
- All 12 UI screens are implemented (not placeholders) with dedicated
  ViewModels, StateFlow, and Hilt wiring.
- Notifications (persistent step counter, supply drop, milestones, smart
  reminder), 2×2 home-screen widget, boot receiver, 15-minute periodic
  WorkManager sync.
- Monetization surface: Gem packs, Ad Removal, Season Pass, cosmetics, reward
  ads. Store screen is fully wired — but see the stubs note below.
- Release tooling: R8/ProGuard rules, release signing config that reads from a
  gitignored `keystore.properties`, Room schema export directory.
- A substantial unit-test suite covering use cases, domain invariants,
  ViewModels, anti-cheat, balance curves, integration scenarios, and the
  escrow lifecycle.

**Actively evolving / known gaps (visible in code today):**

- **Billing is a stub.** `data/billing/StubBillingManager.kt` simulates a
  500 ms network delay and then credits Gems / sets the "ad-removed" flag /
  sets a 30-day Season Pass expiry directly in the wallet. The Google Play
  Billing Library is not integrated. `di/BillingModule.kt` binds the stub
  directly to the `BillingManager` interface.
- **Reward ads are a stub.** `data/ads/StubRewardAdManager.kt` waits 1 second
  and then always returns `AdResult.Rewarded`. `isAdAvailable` always returns
  `true`. No AdMob (or other) SDK is integrated. `di/AdModule.kt` binds the
  stub.
- **Cosmetics are visibly gated.** `presentation/store/StoreScreen.kt` shows
  the literal text "Cosmetic visuals are being finalized. Purchases are
  disabled until ready." and renders every unowned cosmetic with a disabled
  **"Coming Soon"** button. Owned cosmetics can still be equipped/unequipped.
  No code path actually applies cosmetic visuals to the battle renderer.
- **No launcher icon resources.** `app/src/main/res/` contains only `raw/`,
  `xml/`, `layout/`, and `values/` — no `mipmap-*` directories, no adaptive
  icon XML. `AndroidManifest.xml` has no `android:icon` attribute.
  `strings.xml` contains only `app_name`. The installed app will currently
  launch with the system's default icon.
- **`di/HealthConnectModule.kt` is, per its own doc comment, an "organizational
  placeholder"** — an empty `@Module` reserved for future Health Connect Hilt
  bindings.
- **`presentation/MainActivity.kt:237`** defines a private
  `PlaceholderScreen(name)` composable that no navigation route ever invokes.
  Dead code, left behind from an earlier scaffolding phase.
- **Steering doc drift.** `.kiro/steering/source-files.md` still claims
  `AppDatabase.kt` is "version 7"; the code is version 8, and a v7→v8
  `Migration` object is registered. The rules for this exercise treat code as
  authoritative, so this is flagged rather than "fixed" here. Anyone trusting
  the steering file will miss the new `battleStepsEarned` column.

**Recent activity (from git, last 10 commits):** almost everything touched
recently is battle UX polish plus the Battle Step Rewards feature from
ADR-0003. The headline remaining work, per `docs/plans/plan-31-play-console.md`,
is Play Console publication: real Billing + AdMob integration, app icon and
store listing assets, and the final test-track / production rollout.

## Major uncertainties (things source code does not make clear)

1. **Are the shipped sound effects the "real" final audio?** The seven
   `sfx_*.ogg` files in `res/raw/` range from ~2 KB to ~17 KB. The code
   (`presentation/audio/SoundManager.kt`) simply loads them into a
   `SoundPool` and plays them. There is nothing in source to indicate whether
   these are final mastered samples or auto-generated tones. Project memory
   elsewhere in the repo (which this deliverable deliberately treats as
   secondary to code) describes them as placeholders; the code alone cannot
   confirm or refute that.
2. **How will Billing/Ads be swapped in?** The DI modules currently bind the
   stub directly. It isn't obvious from code whether the plan is to (a)
   replace the stub class in-place, (b) introduce a build flavour that picks
   a real implementation vs. the stub, or (c) add a separate module with a
   different binding. Either the stub's `@Inject`-constructor approach will
   be changed or a new implementation will arrive alongside it.
3. **Intent of `PlaceholderScreen` in `MainActivity.kt`.** Unreachable code
   with no callers. Could be an abandoned scaffold or a reserved hook for a
   future route; the code gives no hint.
4. **Cosmetic application path.** The data model and Store UI for cosmetics
   exist, but grep for cosmetic usage in `presentation/battle/` shows no
   integration — the battle renderer currently draws from fixed `BiomeTheme`
   colours, not from any equipped cosmetic. The path by which an owned,
   equipped cosmetic would change what the player sees is not present in
   source.
5. **Final Play-Console-facing name/branding.** `strings.xml` only has
   `<string name="app_name">Steps of Babylon</string>`. No localised strings,
   no store-listing copy embedded in resources. All of that currently lives
   (if at all) in `docs/release/`, which this summary treats as secondary.

---

*Source excerpts verified for this summary (non-exhaustive):
`AndroidManifest.xml`, `StepsOfBabylonApp.kt`, `presentation/MainActivity.kt`,
`service/StepCounterService.kt`, `data/sensor/DailyStepManager.kt`,
`data/local/AppDatabase.kt`, `domain/model/UpgradeType.kt`,
`domain/model/Currency.kt`, `domain/model/EnemyType.kt`,
`domain/model/Biome.kt`, `domain/usecase/PurchaseUpgrade.kt`,
`domain/usecase/AwardBattleSteps.kt`, `domain/usecase/ResolveStats.kt`,
`presentation/home/HomeScreen.kt`, `presentation/battle/BattleScreen.kt`,
`presentation/battle/BattleViewModel.kt`, `presentation/battle/engine/GameEngine.kt`,
`presentation/battle/engine/WaveSpawner.kt`, `presentation/store/StoreScreen.kt`,
`presentation/audio/SoundManager.kt`, `data/billing/StubBillingManager.kt`,
`data/ads/StubRewardAdManager.kt`, `app/build.gradle.kts`,
`settings.gradle.kts`, `gradle/libs.versions.toml`,
`app/src/main/res/values/strings.xml`, `app/src/main/res/` tree listing.*
