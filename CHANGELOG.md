# Changelog

All notable changes to Steps of Babylon are documented here.

## [Unreleased]

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

### Current state
- **455 JVM tests** green (412 baseline → 453 after Phase A → 455 after B.1). Zero behaviour change across Phase B.1.
- Plan 31 (Play Console & Store Publication) remains the only release-blocker; unblocked since the end of Plan R2.
- Phase B.2 onwards pending. Real Billing/Ad SDK swaps (Phase C.5/C.6) still gated on ADR-0005/ADR-0006.

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
