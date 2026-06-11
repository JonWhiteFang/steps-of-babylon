# Design Concepts

*Archaeology Phase 5 — design-level concepts (UX, API shape, system-design
patterns, data-flow contracts). Each entry: ≤3 sentences, implementation
status, file pointers. Since Steps of Babylon is an end-user product, this
list favours user-facing design and the internal contracts that shape it.*

---

## 1. Central product design

### Walk-to-earn, idle-tower-defence core loop
The five-step loop is: walk → Steps credited → spend Steps on Workshop
upgrades / Labs / Overdrive → launch Battle → unlock higher tier / biome →
repeat. Every progression lever traces back to real-world activity, not
in-game busywork.
**Implementation status:** Fully.
**Files:** `docs/StepsOfBabylonApp_GDD.md` (§1–3), `presentation/home/HomeScreen.kt`,
`data/sensor/DailyStepManager.kt`.

### Offline-first, solo-experience product
No network calls, no server backend, no multiplayer. All state lives locally
in the encrypted Room DB; network security config blocks cleartext entirely.
**Implementation status:** Fully.
**Files:** `res/xml/network_security_config.xml`, absence of any HTTP client
in `build.gradle.kts`.

### Single-activity Compose app with one custom SurfaceView
All 12 screens live inside one `NavHost` in `MainActivity`. The battle screen
is the sole exception — an `AndroidView(GameSurfaceView)` sandwiched between
Compose overlays — to get a dedicated render thread for the 60 UPS game
loop.
**Implementation status:** Fully.
**Files:** `presentation/MainActivity.kt`, `presentation/battle/BattleScreen.kt`.

### 12-route bottom-nav product shell
5 routes are top-level in the `BottomNavBar` (Home / Workshop / Battle /
Labs / Stats); the other 7 (Weapons, Cards, Supplies, Economy, Missions,
Settings, Store) are reached from Home buttons and badges. Bottom nav is
hidden during Battle.
**Implementation status:** Fully.
**Files:** `presentation/navigation/Screen.kt`, `presentation/navigation/BottomNavBar.kt`.

---

## 2. Domain data model shape

### Four permanent + one in-round currency
`Currency` enum has 4 entries: `STEPS`, `CASH`, `GEMS`, `POWER_STONES`.
`Cash` is per-round only; `Card Dust` exists as a first-class balance on
`PlayerProfile` but is not in the enum (it's considered an upgrade material,
not a currency for UI purposes).
**Implementation status:** Fully.
**Files:** `domain/model/Currency.kt`, `domain/model/PlayerWallet.kt`,
`domain/model/PlayerProfile.kt`.

### Three-state `PlayerProfile` aggregate
`PlayerProfile` carries currency balances + tier state + lifetime counters +
streak state + monetization flags (27 fields). All ViewModels derive their
UI from this single aggregate plus one or two supporting flows.
**Implementation status:** Fully.
**Files:** `domain/model/PlayerProfile.kt`, `data/local/PlayerProfileEntity.kt`.

### Enum-driven content design
Every static game concept is modelled as an enum whose values carry their
numeric balance (base cost, scaling factor, multiplier, duration, reward).
No JSON/YAML balance tables — the enum is the balance sheet, and
`balance/*Test.kt` validates it.
**Implementation status:** Fully.
**Files:** `domain/model/UpgradeType.kt` (23 × `UpgradeConfig`),
`domain/model/ResearchType.kt` (10), `domain/model/CardType.kt` (9 with
`valueLv1/valueLv5`), `domain/model/UltimateWeaponType.kt` (6),
`domain/model/OverdriveType.kt` (4), `domain/model/EnemyType.kt` (6),
`domain/model/DailyMissionType.kt` (6), `domain/model/Milestone.kt` (6),
`domain/model/BillingProduct.kt` (5), `domain/model/AdPlacement.kt` (3).

### Multiplicative stat stacking with hard caps
`ResolveStats` composes Workshop × In-Round levels multiplicatively, then
`ApplyCardEffects` post-processes the result by copying
`ResolvedStats` with patched fields. Each stat enforces an in-function cap
(e.g. crit chance `min(…, 0.80)`, defence `min(…, 0.75)`).
**Implementation status:** Fully.
**Files:** `domain/usecase/ResolveStats.kt`, `domain/usecase/ApplyCardEffects.kt`,
`domain/model/ResolvedStats.kt`.

### Geometric cost formula as universal invariant
Every upgrade/research/UW cost follows `baseCost × scaling^level`; scaling
factors range 1.11–1.35 for workshop, 1.15 for labs. Enforced in
`CONSTRAINTS.md` as a hard rule.
**Implementation status:** Fully.
**Files:** `domain/usecase/CalculateUpgradeCost.kt`,
`domain/usecase/CalculateResearchCost.kt`,
`domain/model/UltimateWeaponType.kt` (`upgradeCost`).

### Tier-driven battle conditions
`TierConfig.forTier(n).battleConditions: Map<BattleCondition, Int>` holds
percentage-based modifiers; `BattleConditionEffects.fromTier(n)` translates
that map into a pre-computed multiplier bundle the engine uses on every
tick. First conditions appear at tier 6; tier 10 applies all seven.
**Implementation status:** Fully.
**Files:** `domain/model/TierConfig.kt`, `domain/model/BattleCondition.kt`,
`domain/model/BattleConditionEffects.kt`.

### Biome as a visual layer over tier ranges
`Biome.forTier(n)` maps tiers 1–3 → Hanging Gardens, 4–6 → Burning Sands,
7–8 → Frozen Ziggurats, 9–10 → Underworld of Kur, 11+ → Celestial Gate.
`BiomeTheme` supplies palette (sky, ground, ziggurat layer colours, enemy
tint, particle colour) derived from the biome.
**Implementation status:** Fully.
**Files:** `domain/model/Biome.kt`, `presentation/battle/biome/BiomeTheme.kt`,
`presentation/battle/biome/BackgroundRenderer.kt`.

### Loadout caps as hard invariants
Max 3 equipped Ultimate Weapons and max 3 equipped Cards, enforced both in
domain models (`UltimateWeaponLoadout`, `CardLoadout`) and in
`ManageCardLoadout`. `CONSTRAINTS.md` lists this as a game-design invariant.
**Implementation status:** Fully.
**Files:** `domain/model/UltimateWeaponLoadout.kt`, `domain/model/CardLoadout.kt`,
`domain/usecase/ManageCardLoadout.kt`.

---

## 3. Contract / interface shape

### Use case as plain Kotlin class with `operator fun invoke`
All 32 use cases are plain Kotlin with an `operator fun invoke(...)` entry
point — no Hilt annotations. ViewModels construct them inline so Hilt only
has to bind repositories and DAOs.
**Implementation status:** Fully (convention reaffirmed by ADR-0003).
**Files:** `domain/usecase/*.kt`.

### Repository interfaces in domain, implementations in data
8 repository interfaces (`PlayerRepository`, `WorkshopRepository`, `LabRepository`,
`CardRepository`, `UltimateWeaponRepository`, `StepRepository`,
`WalkingEncounterRepository`, `CosmeticRepository`) + `BillingManager` +
`RewardAdManager`. Every interface is reactive (`Flow<T>` for reads,
`suspend` for writes).
**Implementation status:** Fully.
**Files:** `domain/repository/*.kt`, `data/repository/*Impl.kt`,
`data/billing/StubBillingManager.kt`, `data/ads/StubRewardAdManager.kt`.

### ViewModel → UiState → Composable unidirectional flow
Each ViewModel exposes exactly one `uiState: StateFlow<*UiState>` built via
`combine(*).stateIn(viewModelScope, WhileSubscribed(5000), initial)`. Action
methods on the ViewModel are suspend or void-launching; screens call them
and re-render on the next flow emission.
**Implementation status:** Fully.
**Files:** 13 `*ViewModel.kt` + `*UiState.kt` + `*Screen.kt` triples under
`presentation/`.

### Sealed class for every multi-outcome operation
`StartResearch.Result`, `OpenCardPack.Result`, `ActivateOverdrive.Result`,
`PurchaseResult`, `AdResult` — every potentially-failing operation uses a
sealed result so `when` branches stay exhaustive without losing context.
**Implementation status:** Fully.
**Files:** `domain/usecase/StartResearch.kt`, `domain/usecase/OpenCardPack.kt`,
`domain/usecase/ActivateOverdrive.kt`, `domain/model/BillingProduct.kt`,
`domain/model/AdPlacement.kt`.

### Entity↔Domain mapper owned by repository impl
Each `RepositoryImpl` owns a `.toDomain()` (and sometimes `.toEntity()`)
extension so the mapper is collocated with the persistence code. The
domain layer is unaware the mapper exists.
**Implementation status:** Fully.
**Files:** `data/repository/PlayerRepositoryImpl.kt` (and seven siblings).

### Stub-SDK pattern for external services
`BillingManager` and `RewardAdManager` are domain interfaces; their impls
(`StubBillingManager`, `StubRewardAdManager`) simulate latency and always
succeed. Real Play Billing Library / AdMob SDKs swap in by changing only
the `@Binds` in `BillingModule` / `AdModule`.
**Implementation status:** Partial — stubs work end-to-end; real SDK binding
is Plan 31 work.
**Files:** `domain/repository/BillingManager.kt`, `data/billing/StubBillingManager.kt`,
`domain/repository/RewardAdManager.kt`, `data/ads/StubRewardAdManager.kt`,
`di/BillingModule.kt`, `di/AdModule.kt`.

### Hybrid reactive + snapshot ViewModel
`CurrencyDashboardViewModel` merges a live `observeProfile()` flow (for
currency balances that must stay fresh) with a `MutableStateFlow<SnapshotData>`
for weekly/login data that is refreshed on screen entry via `LaunchedEffect`.
Pattern introduced in R2-10.
**Implementation status:** Fully for the Economy screen; the other three
affected screens use pure flows.
**Files:** `presentation/economy/CurrencyDashboardViewModel.kt`,
`presentation/economy/CurrencyDashboardScreen.kt`.

---

## 4. Data-flow and subsystem design

### Follow-on pipeline fan-out after step credit
Every credit (sensor *and* activity-minute) calls
`DailyStepManager.runFollowOnPipeline(timestampMs)`, which runs 5 stages in
fixed order (widget update → supply drop → daily login → weekly challenge →
walking missions), each inside its own `try/catch`. Deliberate choice:
one stage failing never starves the others (see trace 04).
**Implementation status:** Fully (R2-02 unified the two input paths).
**Files:** `data/sensor/DailyStepManager.kt`.

### Idempotent delta-based crediting
`recordActivityMinutes` compares incoming cumulative `stepEquivalents`
against `dailyActivityMinuteTotal` and only credits the positive delta,
preventing process-restart double-counting. Tracked separately from
`dailySensorCredited` so the Room `creditedSteps` column never mixes the
two.
**Implementation status:** Fully (R2-01).
**Files:** `data/sensor/DailyStepManager.kt`.

### Supply-drop priority ladder
`GenerateSupplyDrop` checks in strict priority order: daily milestone (10 k
crossing) → step threshold (2 k boundary with 5% chance per 100 steps) →
random (1% per 500 steps). Only the first matching trigger fires per call —
inbox cap is 10.
**Implementation status:** Fully — `STEP_BURST` enum entry exists but is not
wired to a detector.
**Files:** `domain/usecase/GenerateSupplyDrop.kt`, `domain/model/SupplyDropTrigger.kt`.

### 4-level graduated anti-cheat response
`StepCrossValidator` escalates through Level 0 (escrow, 3 syncs) → Level 1
(escrow, 2 syncs) → Level 2 (cap at HC value) → Level 3 (cap at HC − 10%)
based on a persistent 7-day decaying offence counter. Every level deducts
from the wallet immediately on first offence (R02) rather than lazily
discarding later.
**Implementation status:** Fully.
**Files:** `data/healthconnect/StepCrossValidator.kt`,
`data/anticheat/AntiCheatPreferences.kt`.

### Wave lifecycle state machine
`WaveSpawner` alternates between `SPAWNING` (26 s) and `COOLDOWN` (9 s).
Each wave has a deterministic enemy composition; boss waves every 10 waves
(or every 7 at tier 9+ via `MORE_BOSSES`).
**Implementation status:** Fully.
**Files:** `presentation/battle/engine/WaveSpawner.kt`,
`domain/model/BattleConditionEffects.kt`.

### Cash-economy layering per kill
Per-kill cash = `base × tierMultiplier × (1 + cashBonusLevel × 0.03) ×
fortuneMultiplier × (1 + cardCashBonusPercent / 100)`. Interest on held
cash applies at wave completion (`INTEREST` upgrade, cap 10%).
**Implementation status:** Fully.
**Files:** `presentation/battle/engine/GameEngine.kt` (`handleEnemyDeath`,
`handleWaveComplete`), `presentation/battle/engine/EnemyScaler.kt`.

### Battle-Step reward channel, separate from cash
Enemy kills also award flat per-type Steps (BASIC/FAST/SCATTER=1, RANGED=2,
TANK=3, BOSS=10) that are **not** multiplied by Fortune/Cash Bonus/Golden
Ziggurat. Accumulated in `GameEngine.totalStepsEarned` and credited via
`onStepReward` callback → VM → `AwardBattleSteps` with 2 k/day cap.
**Implementation status:** Fully (ADR-0003).
**Files:** `presentation/battle/engine/GameEngine.kt`,
`presentation/battle/engine/EnemyScaler.kt`,
`domain/usecase/AwardBattleSteps.kt`.

### Round-end cascade
On `roundOver = true` the VM runs (in order) `UpdateBestWave` →
`AwardWaveMilestone` → `CheckTierUnlock` → `incrementBattleStats` → mission
progress updates → `MilestoneNotificationManager.notifyNewBestWave`.
Describes trace 08.
**Implementation status:** Fully.
**Files:** `presentation/battle/BattleViewModel.kt` (`endRound`).

### Heartbeat handoff between foreground service and periodic worker
Service writes `heartbeat` every credit; `StepSyncWorker` checks
`StepIngestionPreferences.isServiceAlive(now)` (2-minute threshold) and
skips sensor catch-up when the service is running, avoiding
double-count. Worker uses Room's `sensorSteps` as authoritative baseline
(R01).
**Implementation status:** Fully.
**Files:** `data/sensor/StepIngestionPreferences.kt`,
`service/StepCounterService.kt`, `service/StepSyncWorker.kt`.

---

## 5. UX & feedback design

### Immediate-feedback snackbar pattern
Workshop / Cards / Labs / Store screens wrap content in `Scaffold {
SnackbarHost }` and surface every failed purchase / invalid action via
`userMessage`, then `clearMessage()` after display. Silent failures are a
historical anti-pattern fixed in R10.
**Implementation status:** Fully for those four screens; Battle and Home
use inline visual feedback instead.
**Files:** `presentation/{workshop,cards,labs,store}/*ViewModel.kt`, same
`*Screen.kt`.

### Double-tap guard (`isProcessing`)
Action handlers early-return if `_processing.value`. Prevents duplicate
purchases when a user double-taps during latency. Same shape across 4
action-heavy ViewModels.
**Implementation status:** Fully for those four.
**Files:** `presentation/{workshop,cards,labs,store}/*ViewModel.kt`.

### Quick Invest button
`QuickInvest` finds the cheapest upgrade the player can afford and returns
it; the Workshop FAB fires this on tap. Surface for non-optimising players
who just want "what should I buy next".
**Implementation status:** Fully.
**Files:** `domain/usecase/QuickInvest.kt`,
`presentation/workshop/WorkshopViewModel.kt`,
`presentation/workshop/WorkshopScreen.kt`.

### Tier selector with lock/unlock chips
`TierSelector` is a horizontal `LazyRow` of chip-shaped items with
locked/unlocked/selected visual states; tapping an unlocked tier switches
`PlayerProfile.currentTier` without entering a battle.
**Implementation status:** Fully.
**Files:** `presentation/home/TierSelector.kt`,
`presentation/home/HomeScreen.kt`.

### Biome transition overlay on first entry
`BattleViewModel` consults `BiomePreferences` to see if the current tier's
biome has been seen; if not, it pushes a transition overlay into the UI
state. `BiomeTransitionOverlay` displays the biome title + total step count
before the battle begins.
**Implementation status:** Fully.
**Files:** `data/BiomePreferences.kt`,
`presentation/battle/ui/BiomeTransitionOverlay.kt`,
`presentation/battle/BattleViewModel.kt`.

### Floating "+N" text as HUD micro-feedback
Every enemy kill spawns a yellow `+X` cash `FloatingText` and (since
ADR-0003) a green `+N Step` one offset 24 px below. Both drift up and
fade out; rendered on Canvas on the game thread.
**Implementation status:** Fully.
**Files:** `presentation/battle/effects/FloatingText.kt`,
`presentation/battle/engine/GameEngine.kt` (`handleEnemyDeath`).

### Badge-and-route pattern for surfaced content
Home screen shows badged icon buttons for Supplies (`unclaimedDropCount`)
and Missions (`claimableMissionCount`). Badges only show when count > 0
and tapping navigates to the respective inbox.
**Implementation status:** Fully.
**Files:** `presentation/home/HomeUiState.kt`,
`presentation/home/HomeViewModel.kt`,
`presentation/home/HomeScreen.kt`.

### Speed-control multiplier (1× / 2× / 4×)
`GameLoopThread` exposes a `speed` float; `BattleUiState.speed` drives three
buttons in the HUD. Since the multiplier scales the accumulator, not `dt`,
physics stay deterministic at all three speeds.
**Implementation status:** Fully.
**Files:** `presentation/battle/GameLoopThread.kt`,
`presentation/battle/BattleScreen.kt`.

### Auto-pause on lifecycle dip
`BattleScreen` observes `LifecycleEventObserver(ON_PAUSE)` and calls
`viewModel.pause()` so leaving the screen (notification pull-down, home,
deep link) doesn't silently drain HP while away.
**Implementation status:** Fully.
**Files:** `presentation/battle/BattleScreen.kt`,
`presentation/battle/BattleViewModel.kt`.

### 4-toggle notification settings + "always-on minimal" fallback
`NotificationSettingsScreen` has four switches (Live Step Updates, Supply
Drops, Smart Reminders, Milestones). When "Live Step Updates" is off, the
foreground service still shows a minimal "Step tracking active" notification
because Android requires one (R2-05).
**Implementation status:** Fully.
**Files:** `presentation/settings/NotificationSettingsScreen.kt`,
`service/StepNotificationManager.kt`, `data/NotificationPreferences.kt`.

---

## 6. Monetization design

### Convenience-and-cosmetic-only IAP philosophy
Gems (Gem Packs), Ad Removal, Season Pass, Cosmetics. Steps are explicitly
*not* purchasable with real money, and the GDD treats this as non-negotiable.
**Implementation status:** Fully (stub billing); real SDK binding deferred.
**Files:** `domain/model/BillingProduct.kt`, `domain/repository/BillingManager.kt`,
`data/billing/StubBillingManager.kt`, `presentation/store/StoreScreen.kt`.

### Reward-ad → currency for engaged-but-not-paying players
Three `AdPlacement`s: `POST_ROUND_GEM` and `POST_ROUND_DOUBLE_PS` on
`PostRoundOverlay`, plus `DAILY_FREE_CARD_PACK` on Cards. Every placement is
gated on `profile.adRemoved`.
**Implementation status:** Fully (stubs always reward); real AdMob binding
deferred.
**Files:** `domain/model/AdPlacement.kt`, `data/ads/StubRewardAdManager.kt`,
`presentation/battle/ui/PostRoundOverlay.kt`, `presentation/cards/CardsScreen.kt`.

### Season Pass as daily-login bonus multiplier
When `seasonPassActive && seasonPassExpiry > now`, `TrackDailyLogin` adds
+10 Gems to the daily reward, and `LabsViewModel` exposes `freeRush()` once
per day. 30-day subscription; stub billing sets expiry at purchase time.
**Implementation status:** Fully (stub path).
**Files:** `domain/usecase/TrackDailyLogin.kt`,
`presentation/labs/LabsViewModel.kt`, `data/billing/StubBillingManager.kt`.

### Seven placeholder cosmetics with buy + equip/unequip
`CosmeticEntity` stores 7 seeded items across 3 categories (ziggurat /
projectile / enemy). The store lets players purchase with Gems and flip
`equipped` on the row, but visual application is not hooked into the
renderer (R2-11 disabled buy button until visual work lands).
**Implementation status:** Partial — ownership/equipping works, visual
effect is missing.
**Files:** `data/local/CosmeticEntity.kt`, `data/repository/CosmeticRepositoryImpl.kt`,
`domain/model/CosmeticItem.kt`, `presentation/store/StoreScreen.kt`.

---

## 7. Operational & integration contracts

### Three input channels converging on one credit pipeline
Sensor (via foreground service), `StepSyncWorker` catch-up (sensor +
HC gap-fill), and HC exercise-minute conversion all funnel into
`DailyStepManager.recordSteps` / `recordActivityMinutes`. Rate limit,
velocity check, ceiling, and persistence apply uniformly.
**Implementation status:** Fully.
**Files:** `service/StepCounterService.kt`, `service/StepSyncWorker.kt`,
`data/healthconnect/ActivityMinuteConverter.kt`, `data/sensor/DailyStepManager.kt`.

### Android 14+ Health Connect permission rationale activity
Manifest declares `HealthConnectPermissionActivity` with filter
`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` plus a permission alias
for Android 14's `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS` category.
Activity shows a scrollable privacy policy.
**Implementation status:** Fully.
**Files:** `presentation/HealthConnectPermissionActivity.kt`, `AndroidManifest.xml`.

### Notification → Intent extra → NavHost deep-link contract
Every notification's PendingIntent launches `MainActivity` with
`navigate_to="<route>"` extra. `MainActivity.pendingNavigation` extracts the
extra on cold start and on `onNewIntent`, the flow drives a `LaunchedEffect`
that calls `navController.navigate(route)`.
**Implementation status:** Partial — wiring covers Home, Workshop, Battle,
Missions, Supplies; Store/Stats/Weapons/Cards/Economy/Settings not handled.
**Files:** `presentation/MainActivity.kt`, four notification-manager classes
in `service/`.

### Pre-launch balance validation via test suite
`balance/*Test.kt` (39 tests) codifies GDD timelines and asserts numeric
constants land in tolerance — step economy, cost curves, enemy scaling,
tier progression, cash economy, card balance, UW/overdrive, supply-drop
economy. Treats balance drift as a test failure, not a design review.
**Implementation status:** Fully.
**Files:** `app/src/test/java/com/whitefang/stepsofbabylon/balance/*.kt`,
`docs/balance/balance-report.md`.

### Strict privacy-by-default: data stays on device
No analytics, no crash reporting, no Firebase. All telemetry is local
(Logcat + SharedPreferences counters). Privacy policy documents this as
the product contract.
**Implementation status:** Fully.
**Files:** `docs/release/privacy-policy.md`, absence of Firebase/Sentry
dependencies in `gradle/libs.versions.toml`.

### Accessibility surface (current minimum)
Battle controls, UW slots, and Home badges carry
`contentDescription` via `Modifier.semantics`. System reduced-motion scales
animations. Full accessibility pass (TalkBack polish, colour-blind modes,
adjustable text) is deferred (Plan 24).
**Implementation status:** Partial — baseline in place, full pass pending.
**Files:** `presentation/battle/BattleScreen.kt`,
`presentation/battle/ui/UltimateWeaponBar.kt`,
`presentation/home/HomeScreen.kt`,
`presentation/battle/effects/ReducedMotionCheck.kt`.
