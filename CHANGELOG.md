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

### Current state
- **486 JVM tests** green (412 baseline → 453 after Phase A → 455 after B.1 → 458 after B.2 PR 1 → 461 after B.3 PR 1 → 463 after B.2 PR 2 → 465 after B.2 PR 3 → 468 after B.2 PR 4 → 470 after B.2 PR 5 → 473 after B.3 PR 2 → 475 after C.2 PR 1 → 480 after C.2 PR 2 → 484 after C.4 → 486 after ensureSeedData fix). Zero balance-math changes across all of Phase B and Phase C so far.
- Plan 31 (Play Console & Store Publication) remains the only release-blocker; unblocked since the end of Plan R2.
- **RO-02 complete: 5/5 atomic sites landed** (`PurchaseUpgrade`, `AwardBattleSteps`, `StepCrossValidator`, `ClaimMilestone`, `runEndRoundPersistence`).
- **RO-03 complete: 2/2 resilience sites landed** (extraction + `onCleared` guard).
- **RO-07 in flight: C.2 PRs 1+2 + C.4 + ensureSeedData fix landed.** C.2 PR 3+ now unblocked: each new seed row + palette lands cleanly on any install, and the C.4 UnknownCosmetic detections flip to successful claims as each milestone cosmetic id gets seeded.
- Real Billing/Ad SDK swaps (Phase C.5/C.6) still gated on ADR-0005/ADR-0006.
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
