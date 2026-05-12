# Source File Index

All paths relative to `app/src/main/java/com/whitefang/stepsofbabylon/`.

## Application & DI

```
StepsOfBabylonApp.kt              # @HiltAndroidApp, Configuration.Provider (HiltWorkerFactory)
di/DatabaseModule.kt               # Hilt: Room DB (SQLCipher) + 12 DAO providers
di/RepositoryModule.kt             # Hilt: 8 repository interface → impl bindings (@Singleton)
di/StepModule.kt                   # Hilt: SensorManager provider
di/HealthConnectModule.kt          # Hilt: Health Connect organizational module
di/BillingModule.kt                # Hilt: flag-gated BillingManager binding — StubBillingManager when BuildConfig.USE_REAL_BILLING=false, BillingManagerImpl when true (C.5 PR 2); sibling BillingInternalModule @Binds BillingClientAdapter → RealBillingClientAdapter
di/AdModule.kt                     # Hilt: flag-gated RewardAdManager binding — StubRewardAdManager when BuildConfig.USE_REAL_ADS=false, RewardAdManagerImpl when true (C.6 PR 2); sibling AdInternalModule @Binds RewardedAdAdapter → RealRewardedAdAdapter and ConsentManager → RealConsentManager
di/TimeModule.kt                   # Hilt: TimeProvider → SystemTimeProvider (B.1)
di/CoroutineScopeModule.kt         # Hilt: @ApplicationScope qualifier + app-lifetime CoroutineScope(SupervisorJob + Dispatchers.Default) for work that outlives VM cancellation (B.3 PR 2)
```

## Data Layer — Room

```
data/local/AppDatabase.kt         # @Database: 13 entities, 13 DAOs, version 9, exportSchema=true
data/local/Migrations.kt          # Registered Migration objects (v7→8 for battleStepsEarned / ADR-0003, v8→9 for billing_receipt / ADR-0005)
data/local/Converters.kt          # @TypeConverters: Map<Int,Int> and Map<String,Int> via JSON
data/local/DatabaseKeyManager.kt  # SQLCipher passphrase via Android Keystore
data/local/PlayerProfileEntity.kt # Player profile entity (single row, id=1)
data/local/PlayerProfileDao.kt    # Player DAO: get() as Flow, atomic currency adjustments (incl. adjustStepBalanceIfSufficient SQL-guarded deduct for B.2 PR 1)
data/local/WorkshopUpgradeEntity.kt # Workshop upgrade entity
data/local/WorkshopDao.kt         # Workshop DAO + @Transaction purchaseUpgradeAtomic default method (B.2 PR 1)
data/local/LabResearchEntity.kt   # Lab research entity
data/local/LabDao.kt              # Lab research DAO
data/local/CardInventoryEntity.kt # Card inventory entity
data/local/CardDao.kt             # Card inventory DAO
data/local/UltimateWeaponStateEntity.kt # UW state entity
data/local/UltimateWeaponDao.kt   # UW state DAO
data/local/DailyStepRecordEntity.kt # Daily step record entity (with escrow fields)
data/local/DailyStepDao.kt        # Daily step record DAO (with escrow queries) + @Transaction creditBattleStepsAtomic default method (B.2 PR 2)
data/local/WalkingEncounterEntity.kt # Walking encounter entity
data/local/WalkingEncounterDao.kt # Walking encounter DAO
data/local/WeeklyChallengeEntity.kt # Weekly step challenge entity
data/local/WeeklyChallengeDao.kt   # Weekly challenge DAO
data/local/DailyLoginEntity.kt     # Daily login tracking entity
data/local/DailyLoginDao.kt        # Daily login DAO
data/local/MilestoneEntity.kt      # Milestone claim state entity
data/local/MilestoneDao.kt         # Milestone DAO + @Transaction claimMilestoneAtomic default method (B.2 PR 4)
data/local/DailyMissionEntity.kt   # Daily mission entity
data/local/DailyMissionDao.kt      # Daily mission DAO
data/local/CosmeticEntity.kt       # Cosmetic store entity
data/local/CosmeticDao.kt          # Cosmetic store DAO
data/local/BillingReceiptEntity.kt # Play Billing receipt entity — idempotency store keyed by purchaseToken (C.5 PR 1 / ADR-0005)
data/local/BillingReceiptDao.kt    # Billing receipt DAO + @Transaction grantOnceAtomic default method (C.5 PR 1)
```

## Data Layer — Repositories

```
data/repository/PlayerRepositoryImpl.kt         # Player profile + wallet (entity→domain mapping)
data/repository/WorkshopRepositoryImpl.kt        # Workshop upgrades; takes WorkshopDao + PlayerProfileDao for purchaseUpgradeAtomic (B.2 PR 1)
data/repository/LabRepositoryImpl.kt             # Lab research
data/repository/CardRepositoryImpl.kt            # Card inventory
data/repository/UltimateWeaponRepositoryImpl.kt  # Ultimate weapon state
data/repository/StepRepositoryImpl.kt            # Daily step records + escrow + getDailyRecord()
data/repository/WalkingEncounterRepositoryImpl.kt # Walking encounters
data/repository/CosmeticRepositoryImpl.kt        # Cosmetic store items + private ZIGGURAT_COLOR_LOOKUP table (4 palettes: zig_jade @ C.2 PR 2, lapis_lazuli_skin @ PR 3 / IRON_SOLES reward, garden_ziggurat_skin @ PR 3b / MARATHON_WALKER reward, sandals_of_gilgamesh @ PR 3c / GLOBE_TROTTER reward); toDomain populates CosmeticItem.overrideColors from the lookup; SEED_COSMETICS: 11 rows (7 ZIGGURAT_SKIN including the 3 milestone-reward cosmetics + 2 PROJECTILE_EFFECT + 2 ENEMY_SKIN); ensureSeedData uses per-cosmeticId filter so content PRs land on already-seeded installs without a data clear and player `isOwned`/`isEquipped` state survives upgrades
```

## Data Layer — Sensor

```
data/sensor/StepSensorDataSource.kt  # TYPE_STEP_COUNTER wrapper, emits deltas via callbackFlow
data/sensor/StepRateLimiter.kt       # Rolling 1-min window rate limiter (200/min, 250 burst)
data/sensor/StepVelocityAnalyzer.kt  # Unnatural step pattern detection (shaker/spoof), penalty multiplier
data/sensor/StepIngestionPreferences.kt # Service heartbeat + day-start counter for worker/service coordination
data/sensor/DailyStepManager.kt      # Orchestrates: rate limit → velocity analysis → 50k ceiling → Room persist + activity minutes
```

## Data Layer — Billing & Ads

```
data/billing/StubBillingManager.kt             # Stub billing: simulates purchases with 500ms delay, credits Gems/flags (to be deleted in C.5 PR 3)
data/billing/BillingManagerImpl.kt             # Real Play Billing v8 impl (C.5 PR 1 / ADR-0005): adapter + receipt DAO + wallet credits + SHA-256 obfuscatedAccountId anti-fraud; `internal`; wallet side-effects run inside `BillingReceiptDao.grantOnceAtomic`; consume/ack runs after tx commits; `reconcilePendingPurchases` overrides the BillingManager default no-op to sweep PENDING→PURCHASED transitions + retry unresolved consume/ack. @Binds still points at Stub — flag swap lands in C.5 PR 2.
data/billing/internal/BillingClientAdapter.kt  # SDK-neutral seam for `BillingManagerImpl` (C.5 PR 1). Interface + sealed result types (SdkBillingResult / SdkPurchase / SdkProductDetails / QueryProductDetailsResult / StartPurchaseResult / QueryPurchasesResult); `internal`; tests mock this directly so no `com.android.billingclient.*` imports leak into unit tests.
data/billing/internal/RealBillingClientAdapter.kt # Concrete BillingClientAdapter backed by Google Play Billing Library v8 (C.5 PR 1). Uses `enableAutoServiceReconnection()`, `PendingPurchasesParams.enableOneTimeProducts`, Mutex-guarded `launchPurchase` with CompletableDeferred bridging PurchasesUpdatedListener. Device-only testable — the only file in the app that imports `com.android.billingclient.*`.
data/billing/internal/ActivityProvider.kt      # WeakReference-backed Singleton Activity holder. MainActivity.onResume sets + onPause clears (C.5 PR 2).
data/ads/StubRewardAdManager.kt                # Stub ads: simulates ad view with 1s delay, always rewards (to be deleted in C.6 PR 3)
data/ads/RewardAdManagerImpl.kt                # Real AdMob impl (C.6 PR 1 / ADR-0006): adapter + consent manager + per-placement BuildConfig ad-unit routing; `internal`; session-Mutex-guarded; consent → load → show → map to AdResult. Shares the existing `data/billing/internal/ActivityProvider` for the Activity reference needed by `RewardedAd.show()`. @Binds still points at Stub — flag swap lands in C.6 PR 2.
data/ads/internal/RewardedAdAdapter.kt         # SDK-neutral seam for `RewardAdManagerImpl` (C.6 PR 1). Interface + sealed result types (SdkAdLoadResult / SdkAdShowResult / SdkRewardedAd); `internal`; tests mock this directly so no `com.google.android.gms.ads.*` imports leak into unit tests.
data/ads/internal/RealRewardedAdAdapter.kt     # Concrete RewardedAdAdapter backed by Google Mobile Ads SDK v25 (C.6 PR 1). Lazy `MobileAds.initialize` on first loadAd, CompletableDeferred bridging `RewardedAdLoadCallback` + `FullScreenContentCallback` + `OnUserEarnedRewardListener`, AtomicBoolean `rewarded` flag flipped only inside `onUserEarnedReward`. Device-only testable — the only file in the app that imports `com.google.android.gms.ads.*`.
data/ads/internal/ConsentManager.kt            # SDK-neutral seam for UMP consent (C.6 PR 1). Interface; `internal`; tests mock directly so no `com.google.android.ump.*` imports leak into unit tests.
data/ads/internal/RealConsentManager.kt        # Concrete ConsentManager backed by UMP SDK v4 (C.6 PR 1). Mutex-guarded once-per-session `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`; errors logged but non-throwing. Device-only testable — the only file in the app that imports `com.google.android.ump.*`.
```

## Data Layer — Health Connect

```
data/healthconnect/HealthConnectClientWrapper.kt  # HealthConnectClient wrapper, availability, permissions
data/healthconnect/HealthConnectStepReader.kt      # Reads aggregated daily steps via aggregate()
data/healthconnect/StepCrossValidator.kt           # Cross-validation, graduated response (4 offense levels); 5 multi-write branches wrapped in AppDatabase.withTransaction via @VisibleForTesting runInTransaction seam (B.2 PR 3)
data/healthconnect/StepGapFiller.kt                # Recovers missed steps from HC when service killed
data/healthconnect/ExerciseSessionReader.kt        # Reads exercise sessions for Activity Minute Parity
data/healthconnect/ActivityMinuteConverter.kt      # Converts exercise minutes to step-equivalents with caps
data/healthconnect/ActivityMinuteValidator.kt      # Filters suspicious exercise sessions (duration/type/micro caps)
data/BiomePreferences.kt                          # SharedPreferences wrapper for first-seen biome tracking
data/MilestoneNotificationPreferences.kt           # SharedPreferences wrapper for milestone notification dedup
data/NotificationPreferences.kt                   # SharedPreferences wrapper for 4 notification toggles
data/SoundPreferences.kt                          # SharedPreferences wrapper for sound mute/volume
data/anticheat/AntiCheatPreferences.kt            # SharedPreferences wrapper for anti-cheat counters + CV offense tracking
```

## Data Layer — Time

```
data/time/SystemTimeProvider.kt       # Production TimeProvider backed by Instant.now() / LocalDate.now(). @Singleton @Inject.
```

## Domain Layer — Time

```
domain/time/TimeProvider.kt           # Wall-clock seam: now() / today(). No Android imports. Introduced by B.1.
```

## Domain Layer — Models

```
domain/model/Currency.kt              # Enum: STEPS, CASH, GEMS, POWER_STONES
domain/model/PlayerWallet.kt          # Currency balances data class
domain/model/PlayerProfile.kt         # Full profile (maps from PlayerProfileEntity)
domain/model/ActiveResearch.kt        # In-progress lab research
domain/model/OwnedCard.kt             # Player-owned card instance
domain/model/OwnedWeapon.kt           # Player-owned ultimate weapon
domain/model/DailyStepSummary.kt      # Daily step record domain model (with escrow fields)
domain/model/SupplyDrop.kt              # Walking encounter supply drop
domain/model/SupplyDropTrigger.kt        # 4 trigger types with notification messages
domain/model/SupplyDropReward.kt         # 4 reward types (Steps, Gems, Power Stones, Card Dust)
domain/model/DropGeneratorState.kt       # Generator state tracking (lastCheckSteps, milestoneTriggered)
domain/model/Milestone.kt               # 6 walking milestones with step thresholds and rewards
domain/model/MilestoneReward.kt          # Sealed class: Gems, PowerStones, Cosmetic
domain/model/DailyMissionType.kt         # 6 daily mission types (walking/battle/upgrade)
domain/model/MissionCategory.kt          # Mission categories: WALKING, BATTLE, UPGRADE (in DailyMissionType.kt)
domain/model/BillingProduct.kt           # 5 billing products + PurchaseResult sealed class + opt-in Companion for data-layer `BillingProduct.fromSkuIdOrNull(skuId)` reverse lookup (C.5 PR 1)
domain/model/AdPlacement.kt              # 3 ad placements + AdResult sealed class
domain/model/CosmeticCategory.kt         # 3 cosmetic categories (ziggurat, projectile, enemy)
domain/model/CosmeticItem.kt             # Cosmetic item domain model (+ optional overrideColors: List<Int>? for renderer override, C.2 PR 1)
domain/model/UpgradeType.kt           # 23 Workshop upgrade types with configs
domain/model/UpgradeCategory.kt       # Attack, Defense, Utility categories
domain/model/UpgradeConfig.kt         # Upgrade configuration (baseCost, scaling, maxLevel)
domain/model/Tier.kt                  # Tier data class
domain/model/TierConfig.kt            # Full tier table (1–10)
domain/model/BattleCondition.kt       # 7 battle condition types
domain/model/Biome.kt                 # 5 biomes with forTier() mapping
domain/model/BattleConditionEffects.kt # Pre-computed battle condition modifiers from tier
domain/model/EnemyType.kt             # 6 enemy types with multipliers
domain/model/UltimateWeaponType.kt    # 6 UW types with unlock costs
domain/model/UltimateWeaponLoadout.kt # UW loadout (max 3)
domain/model/OverdriveType.kt         # 4 overdrive types with costs
domain/model/ResearchType.kt          # 10 lab research types
domain/model/CardRarity.kt            # Common, Rare, Epic
domain/model/CardType.kt              # 9 card types with effects
domain/model/CardLoadout.kt           # Card loadout (max 3)
domain/model/RoundState.kt            # Transient battle state
domain/model/ZigguratBaseStats.kt     # Base stat constants (HP, damage, attack speed, range, regen, projectile speed)
domain/model/ResolvedStats.kt         # Computed combat stats from workshop + in-round upgrades
```

## Domain Layer — Interfaces & Use Cases

```
domain/repository/PlayerRepository.kt          # Profile/wallet: observe + spend/add currencies
domain/repository/WorkshopRepository.kt         # Workshop upgrades interface (incl. purchaseUpgradeAtomic for B.2 PR 1)
domain/repository/LabRepository.kt              # Lab research interface
domain/repository/CardRepository.kt             # Card inventory interface
domain/repository/UltimateWeaponRepository.kt   # Ultimate weapon interface
domain/repository/StepRepository.kt             # Daily step records + escrow + Health Connect methods
domain/repository/WalkingEncounterRepository.kt # Walking encounter interface
domain/repository/BillingManager.kt             # Billing interface (purchase, query, reconcilePendingPurchases with default no-op so Stub + fakes inherit do-nothing contract; C.5 PR 1)
domain/repository/RewardAdManager.kt            # Reward ad interface (show ad, availability)
domain/repository/CosmeticRepository.kt         # Cosmetic store interface + `idExists(cosmeticId): Boolean` (C.4 — used by ClaimMilestone to pre-flight MilestoneReward.Cosmetic ids and surface UnknownCosmetic result variant for the 3 currently-mismatched milestone cosmetic ids)
domain/usecase/CalculateUpgradeCost.kt          # Cost formula: baseCost * scaling^level
domain/usecase/CanAffordUpgrade.kt              # Affordability check against wallet
domain/usecase/PurchaseUpgrade.kt               # Delegates to WorkshopRepository.purchaseUpgradeAtomic — atomic deduct + level-set (B.2 PR 1)
domain/usecase/QuickInvest.kt                   # Recommends cheapest affordable upgrade
domain/usecase/ResolveStats.kt                  # Workshop + in-round levels → ResolvedStats
domain/usecase/CalculateDamage.kt               # Raw damage + crit roll + damage/meter bonus → DamageResult
domain/usecase/CalculateDefense.kt              # Damage reduction (cap 75%) + flat block
domain/usecase/UpdateBestWave.kt                # Compares wave to stored best, persists if new record
domain/usecase/CheckTierUnlock.kt               # Checks wave milestones for tier unlock eligibility
domain/usecase/ActivateOverdrive.kt              # Validates overdrive activation (balance + once-per-round)
domain/usecase/UnlockUltimateWeapon.kt           # Checks Power Stone balance, deducts, unlocks UW
domain/usecase/UpgradeUltimateWeapon.kt          # Cost scaling per level, max level 10
domain/usecase/CalculateResearchCost.kt          # Research cost: baseCostSteps × costScaling^level
domain/usecase/CalculateResearchTime.kt          # Research time: baseTimeHours × timeScaling^level
domain/usecase/StartResearch.kt                  # Validates slots/affordability/max level, deducts Steps, starts timer
domain/usecase/CompleteResearch.kt               # Completes research when timer elapsed, increments level
domain/usecase/RushResearch.kt                   # Instant complete via Gems (50–200 linear cost)
domain/usecase/UnlockLabSlot.kt                  # Unlock lab slot (200 Gems, max 4)
domain/usecase/CheckResearchCompletion.kt        # Auto-completes all expired research on app launch
domain/usecase/OpenCardPack.kt                   # Opens card pack: 3 tiers, rarity rolling, duplicate→dust
domain/usecase/UpgradeCard.kt                    # Card upgrade: Card Dust cost by rarity × level
domain/usecase/ApplyCardEffects.kt               # Post-process ResolvedStats with equipped card effects
domain/usecase/ManageCardLoadout.kt              # Equip/unequip cards (max 3 loadout)
domain/usecase/GenerateSupplyDrop.kt             # Seeded random supply drop generation (4 triggers)
domain/usecase/ClaimSupplyDrop.kt                # Credits reward to player, marks drop claimed
domain/usecase/TrackWeeklyChallenge.kt           # Weekly step challenge PS awards (50k/75k/100k)
domain/usecase/TrackDailyLogin.kt                # Daily login PS + Gem streak
domain/usecase/AwardWaveMilestone.kt             # PS on new personal-best waves (1/2/5)
domain/usecase/CheckMilestones.kt                # Detect newly achievable walking milestones
domain/usecase/ClaimMilestone.kt                 # Credit milestone rewards via MilestoneDao.claimMilestoneAtomic (gems + power stones) with pre-flight CosmeticRepository.idExists check; returns `ClaimMilestoneResult` sealed type (Success | InsufficientSteps | AlreadyClaimed | UnknownCosmetic(cosmeticId)) so the 3 mismatched milestone cosmetic ids surface explicitly instead of silently dropping (C.4)
domain/usecase/GenerateDailyMissions.kt          # Generate 3 daily missions (date-seeded random)
domain/usecase/PurchaseGemPack.kt                # Purchase Gem pack via BillingManager
```

## Presentation Layer

```
presentation/MainActivity.kt                      # Single Activity, Scaffold + NavHost + BottomNavBar, permissions; onResume/onPause drive ActivityProvider.set/clear for Play Billing purchase flow (C.5 PR 2); onResume also fires a flag-gated one-shot UMP consent prefetch via injected ConsentManager so the first reward-ad tap doesn't pay the init latency (C.6 PR 2)
presentation/HealthConnectPermissionActivity.kt    # Scrollable privacy policy for Health Connect permissions
presentation/navigation/Screen.kt                 # Sealed class: 12 routes (Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store)
presentation/navigation/BottomNavBar.kt            # Bottom navigation bar with 5 items
presentation/home/HomeViewModel.kt                 # @HiltViewModel: combines profile + step flows → HomeUiState
presentation/home/HomeUiState.kt                   # UI state: steps, balance, tier, biome, bestWave
presentation/home/HomeScreen.kt                    # Dashboard: step card, currencies, tier selector, battle button
presentation/home/TierSelector.kt                  # Horizontal tier chip row with lock/unlock states
presentation/workshop/WorkshopViewModel.kt         # @HiltViewModel: upgrades + wallet → WorkshopUiState
presentation/workshop/WorkshopUiState.kt           # UI state: upgrade list, balance, selected category
presentation/workshop/WorkshopScreen.kt            # 3-tab layout, upgrade list, Quick Invest FAB
presentation/workshop/UpgradeCard.kt               # Reusable upgrade card (affordable/expensive/maxed states)
presentation/battle/BattleScreen.kt                # Compose wrapper: AndroidView + overlays (HUD, pause, post-round), auto-pause
presentation/battle/BattleViewModel.kt             # @HiltViewModel: 14-param constructor; round lifecycle in `runEndRoundPersistence` wrapped in `AppDatabase.withTransaction` (B.2 PR 5) with per-write runCatching error isolation (B.3 PR 1); `onCleared` guard launches persistence on `@ApplicationScope CoroutineScope` when round is mid-progress (B.3 PR 2); hydrates `engine.cosmeticOverrides` from CosmeticRepository (C.2 PR 1)
presentation/battle/BattleUiState.kt               # UI state: wave, HP, cash, speed, pause, RoundEndState
presentation/battle/GameSurfaceView.kt             # SurfaceView + SurfaceHolder.Callback, manages game loop thread
presentation/battle/GameLoopThread.kt              # Dedicated thread: fixed timestep (60 UPS), accumulator, speed multiplier
presentation/battle/engine/GameEngine.kt           # Central coordinator: entity list, update/render dispatch, wave/collision integration; +hasWaveProgress() (B.3 PR 2); +@Volatile cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> consulted in init() to select ziggurat layer colors (C.2 PR 1)
presentation/battle/engine/Entity.kt               # Abstract base: x, y, width, height, isAlive, update(), render()
presentation/battle/engine/WaveSpawner.kt          # Wave lifecycle: 26s spawn + 9s cooldown, enemy composition by wave
presentation/battle/engine/EnemyScaler.kt          # Wave-based stat scaling (1.05^wave), cash rewards per type
presentation/battle/engine/CollisionSystem.kt      # Projectile↔enemy and enemy projectile↔ziggurat collision
presentation/battle/entities/ZigguratEntity.kt     # 5-layer ziggurat, nearest-enemy targeting, HP tracking
presentation/battle/entities/ProjectileEntity.kt   # Moves toward target, self-destructs on arrival
presentation/battle/entities/EnemyEntity.kt        # 6 enemy types, movement, melee/ranged attack, mini HP bar
presentation/battle/entities/EnemyProjectileEntity.kt # Ranged enemy projectiles targeting ziggurat
presentation/battle/entities/OrbEntity.kt          # Orbiting projectiles circling ziggurat, per-enemy hit cooldown
presentation/battle/effects/ParticlePool.kt        # Pre-allocated particle pool (200 capacity), acquire/release/recycle
presentation/battle/effects/EffectEngine.kt        # Manages active effects, owns ParticlePool + ScreenShake
presentation/battle/effects/ScreenShake.kt         # Canvas translate oscillation with decaying amplitude
presentation/battle/effects/ReducedMotionCheck.kt  # System ANIMATOR_DURATION_SCALE reader
presentation/battle/effects/ProjectileTrailEffect.kt # Fading biome-colored trail particles behind projectiles
presentation/battle/effects/DeathEffect.kt         # Per-enemy-type death burst (6 types, 6-20 particles)
presentation/battle/effects/FloatingText.kt        # "+X Cash" rising text that fades
presentation/battle/effects/UWVisualEffect.kt      # 6 particle-based UW activation spectacles
presentation/battle/effects/OverdriveAuraEffect.kt # 4 overdrive aura particle emitters
presentation/battle/effects/WaveAnnouncement.kt    # Wave number slide-in + boss warning + cooldown countdown
presentation/audio/SoundManager.kt                 # SoundPool wrapper, 7 effects, volume/mute, shoot throttling
presentation/battle/ui/HealthBarRenderer.kt        # HP bar: green→yellow→red gradient, numeric text
presentation/battle/ui/InRoundUpgradeMenu.kt      # In-round upgrade menu: 3 tabs, purchase with Cash
presentation/battle/ui/PostRoundOverlay.kt         # Post-round summary: wave, kills, cash, time, new record banner
presentation/battle/ui/PauseOverlay.kt             # Pause overlay: Resume + Quit Round buttons
presentation/battle/ui/BiomeTransitionOverlay.kt   # Full-screen biome reveal overlay with step count
presentation/battle/ui/OverdriveMenu.kt            # Overdrive type selection (4 options, cost, affordability)
presentation/battle/ui/UltimateWeaponBar.kt        # UW activation buttons (up to 3, cooldown overlay)
presentation/battle/biome/BiomeTheme.kt            # 5 biome color palettes (sky, ground, ziggurat, enemy, particles)
presentation/battle/biome/BackgroundRenderer.kt    # Gradient sky + ambient particle system per biome
presentation/ui/theme/Color.kt                     # Compose color definitions
presentation/ui/theme/Theme.kt                     # Compose theme setup (Material3)
presentation/weapons/UltimateWeaponViewModel.kt    # @HiltViewModel: UW unlock/upgrade/equip state
presentation/weapons/UltimateWeaponScreen.kt       # UW management: 6 cards with lock/unlock/equip/upgrade
presentation/labs/LabsViewModel.kt                  # @HiltViewModel: research state + wallet + countdown ticker
presentation/labs/LabsUiState.kt                    # UI state: research list, slots, balances
presentation/labs/LabsScreen.kt                     # Labs screen: research cards, start/rush/unlock slot
presentation/cards/CardsViewModel.kt                # @HiltViewModel: card collection + wallet + pack/upgrade/loadout
presentation/cards/CardsUiState.kt                  # UI state: owned cards, pack options, dust balance
presentation/cards/CardsScreen.kt                   # Cards screen: pack opening, collection, equip/upgrade
presentation/supplies/UnclaimedSuppliesViewModel.kt  # @HiltViewModel: claim/claimAll supply drops
presentation/supplies/SuppliesUiState.kt             # UI state: unclaimed drops list
presentation/supplies/UnclaimedSuppliesScreen.kt     # Inbox: drop cards, claim buttons, empty state
presentation/economy/CurrencyDashboardViewModel.kt   # @HiltViewModel: weekly challenge, login streak, balances
presentation/economy/EconomyUiState.kt               # UI state: weekly progress, streak, balances
presentation/economy/CurrencyDashboardScreen.kt      # Dashboard: weekly progress bar, streak dots, PS/Gem balances
presentation/missions/MissionsViewModel.kt           # @HiltViewModel: milestones + daily missions + claim flow
presentation/missions/MissionsUiState.kt             # UI state: missions list, milestones list, midnight countdown
presentation/missions/MissionsScreen.kt              # Missions screen: daily missions + walking milestones + claim
presentation/stats/StatsViewModel.kt                 # @HiltViewModel: walking history, battle stats, all-time aggregates
presentation/stats/StatsUiState.kt                   # UI state: bars, periods, battle/all-time stats
presentation/stats/StatsScreen.kt                    # Stats screen: chart, today, battle, all-time sections
presentation/stats/WalkingHistoryChart.kt            # Canvas-drawn bar chart with period toggle
presentation/settings/NotificationSettingsViewModel.kt # @HiltViewModel: notification preference toggles
presentation/settings/NotificationSettingsScreen.kt    # Settings screen: 4 notification toggles
presentation/store/StoreViewModel.kt                   # @HiltViewModel: billing + cosmetic purchase actions; init calls billingManager.reconcilePendingPurchases() on Store entry (C.5 PR 2)
presentation/store/StoreUiState.kt                     # UI state: gems, adRemoved, seasonPass, cosmetics
presentation/store/StoreScreen.kt                      # Store screen: Gem packs, Ad Removal, Season Pass, Cosmetics
```

## Service Layer

```
service/StepCounterService.kt        # Foreground service (health type), START_STICKY, collects sensor flow
service/StepNotificationManager.kt   # Notification channel + builder, 30s throttled updates
service/SupplyDropNotificationManager.kt # Supply drop notification channel + deep-link to inbox
service/BootReceiver.kt              # BOOT_COMPLETED → restart StepCounterService
service/StepSyncWorker.kt            # @HiltWorker CoroutineWorker, 15-min periodic: sensor catch-up + HC sync
service/StepSyncScheduler.kt         # Enqueues periodic WorkManager request
service/SmartReminderManager.kt      # Upgrade proximity reminders (piggybacked on StepSyncWorker)
service/MilestoneNotificationManager.kt # Wave record and step milestone notifications
service/StepWidgetProvider.kt        # 2×2 AppWidgetProvider for home screen widget
service/WidgetUpdateHelper.kt        # Throttled widget update helper
```

## Test Layer

All paths relative to `app/src/test/java/com/whitefang/stepsofbabylon/`.

```
fakes/FakePlayerRepository.kt                    # In-memory StateFlow-backed fake for PlayerRepository
fakes/FakeWorkshopRepository.kt                  # In-memory StateFlow-backed fake for WorkshopRepository
fakes/FakeUltimateWeaponRepository.kt            # In-memory StateFlow-backed fake for UltimateWeaponRepository
fakes/FakeLabRepository.kt                       # In-memory StateFlow-backed fake for LabRepository
fakes/FakeCardRepository.kt                      # In-memory StateFlow-backed fake for CardRepository
fakes/FakeWalkingEncounterRepository.kt          # In-memory StateFlow-backed fake for WalkingEncounterRepository
fakes/FakeStepRepository.kt                      # In-memory StateFlow-backed fake for StepRepository
fakes/FakeCosmeticRepository.kt                  # In-memory fake for CosmeticRepository
fakes/FakeBillingManager.kt                      # Configurable fake for BillingManager (+reconcileCallCount for C.5 PR 2 hook assertion; private set)
fakes/FakeRewardAdManager.kt                     # Configurable fake for RewardAdManager
fakes/FakeMilestoneDao.kt                        # In-memory fake for MilestoneDao
fakes/FakeDailyMissionDao.kt                     # In-memory fake for DailyMissionDao
fakes/FakeDailyLoginDao.kt                       # In-memory fake for DailyLoginDao
fakes/FakeWeeklyChallengeDao.kt                  # In-memory fake for WeeklyChallengeDao
fakes/FakeDailyStepDao.kt                        # In-memory fake for DailyStepDao with Flow support
fakes/FakeCosmeticDao.kt                         # In-memory fake for CosmeticDao with auto-increment id simulation (C.2 PR 2)
fakes/FakeTimeProvider.kt                        # Mutable TimeProvider with var fixedDate / fixedInstant (B.1)
domain/usecase/CalculateUpgradeCostTest.kt        # Cost formula: baseCost × scaling^level, all 23 types
domain/usecase/CanAffordUpgradeTest.kt            # Affordability checks against wallet
domain/usecase/QuickInvestTest.kt                 # Cheapest affordable upgrade recommendation
domain/usecase/PurchaseUpgradeTest.kt             # Purchase flow with fake repos
domain/usecase/UpdateBestWaveTest.kt              # Best wave tracking, new record detection
domain/usecase/CheckTierUnlockTest.kt             # Tier unlock logic against wave milestones
domain/usecase/ActivateOverdriveTest.kt           # Overdrive activation validation
domain/usecase/UnlockUltimateWeaponTest.kt        # UW unlock with Power Stones
domain/usecase/UpgradeUltimateWeaponTest.kt       # UW upgrade cost scaling, max level
domain/usecase/ResolveStatsTest.kt                # Multiplicative stacking, all stat caps
domain/usecase/CalculateDamageTest.kt             # Crit/no-crit with injectable Random, damage/meter bonus
domain/usecase/CalculateDefenseTest.kt            # Percent reduction, flat block, floor at 0
domain/usecase/CalculateResearchCostTest.kt       # Research cost scaling: baseCost × 1.15^level
domain/usecase/CalculateResearchTimeTest.kt       # Research time scaling: baseTime × 1.10^level
domain/usecase/StartResearchTest.kt               # Start research: slots, affordability, max level, deducts Steps
domain/usecase/CompleteResearchTest.kt            # Complete research: timer gating, level increment
domain/usecase/RushResearchTest.kt                # Rush research: linear Gem cost (50–200), deducts Gems
domain/usecase/UnlockLabSlotTest.kt               # Unlock slot: 200 Gems, max 4
domain/usecase/CheckResearchCompletionTest.kt     # Auto-complete: expired research, skip not-ready
domain/usecase/OpenCardPackTest.kt                # Pack opening: gem deduction, rarity rolling, duplicates→dust
domain/usecase/UpgradeCardTest.kt                 # Card upgrade: dust cost by rarity, max level
domain/usecase/ApplyCardEffectsTest.kt            # All 9 card effects, level scaling, buff+debuff combos
domain/usecase/ManageCardLoadoutTest.kt           # Equip/unequip, loadout full
domain/usecase/GenerateSupplyDropTest.kt          # Drop generation: inbox cap, milestone, threshold, random triggers
domain/usecase/ClaimSupplyDropTest.kt             # Claim flow: reward crediting, already-claimed guard
domain/usecase/AwardWaveMilestoneTest.kt          # Wave milestone PS: 1/2/5 at wave boundaries
domain/usecase/CheckMilestonesTest.kt             # Milestone detection: threshold, claimed exclusion
domain/usecase/ClaimMilestoneTest.kt              # Claim flow: reward crediting, idempotent guard, concurrent-claim atomicity (B.2 PR 4); post-C.2 PR 3b/3c all 3 milestone cosmetic claims (IRON_SOLES, MARATHON_WALKER, GLOBE_TROTTER) have end-to-end success tests via the real CosmeticRepositoryImpl + FakeCosmeticDao; synthetic rejection-before-atomic regression guard remains for mechanism coverage (C.4 + C.2 PR 3 + PR 3b + PR 3c)
domain/usecase/GenerateDailyMissionsTest.kt       # Mission generation: deterministic, one per category
domain/usecase/PurchaseGemPackTest.kt             # Gem pack purchase: delegates to billing, error forwarding
domain/model/MilestoneTest.kt                     # 6 milestones: thresholds, rewards, sorting
domain/model/DailyMissionTypeTest.kt              # 6 mission types: targets, rewards, categories
domain/model/TierConfigTest.kt                    # All 10 tiers, battle conditions, invalid tier
domain/model/BiomeTest.kt                         # All tier→biome mappings
domain/model/CardLoadoutTest.kt                   # Max 3, no duplicates, add/remove
domain/model/UltimateWeaponLoadoutTest.kt         # Max 3, no duplicates, add/remove
domain/model/UpgradeTypeTest.kt                   # 23 entries, category counts, valid configs
domain/model/EnemyTypeTest.kt                     # 6 entries, multiplier correctness
domain/model/BattleConditionEffectsTest.kt        # All tier condition modifiers verified
presentation/battle/engine/EnemyScalerTest.kt     # Wave scaling, speed, cash rewards
presentation/battle/biome/BiomeThemeTest.kt       # All 5 biome palettes, ziggurat colors, particles
data/sensor/StepRateLimiterTest.kt                # Normal/burst caps, window expiry, edge cases
data/sensor/StepVelocityAnalyzerTest.kt           # Natural/constant/jump patterns, window eviction
data/sensor/StepIngestionPreferencesTest.kt        # Heartbeat write/read, day-start counter, day rollover
data/sensor/StepIngestionTest.kt                   # Worker/service coordination: no double-credit, gap recovery, heartbeat gating
data/sensor/DailyStepManagerTest.kt                 # Widget balance, walking mission progress, activity-minute idempotency
data/healthconnect/StepCrossValidatorTest.kt      # Graduated response levels, offense tracking, escrow
data/healthconnect/ActivityMinuteValidatorTest.kt # Duration/type/micro-session filtering
presentation/battle/effects/ParticlePoolTest.kt   # Acquire/release/recycle/expire/clear/reset
presentation/battle/effects/ScreenShakeTest.kt    # Trigger/decay/override/reset/offset
presentation/battle/effects/DeathEffectTest.kt    # Particle counts per enemy type (6 types)
balance/StepEconomyTest.kt                        # Step economy vs 5 GDD player profiles (multi-week)
balance/CostCurveTest.kt                          # Workshop cost curve validation (standard/premium/ROI)
balance/EnemyScalingTest.kt                       # Enemy HP/damage scaling difficulty ramp
balance/TierProgressionTest.kt                    # Tier unlock timeline vs GDD §14
balance/CashEconomyTest.kt                        # In-round cash flow and interest balance
balance/CardBalanceTest.kt                        # Card tradeoffs and power levels
balance/UWOverdriveBalanceTest.kt                 # UW cooldowns, damage, overdrive costs
balance/SupplyDropEconomyTest.kt                  # Drop rates and premium currency income
domain/usecase/TrackDailyLoginTest.kt              # Daily login PS/Gem streak awards
domain/usecase/TrackWeeklyChallengeTest.kt         # Weekly step challenge PS awards
presentation/stats/StatsViewModelTest.kt           # Stats VM: profile mapping, bars, period switching
presentation/weapons/UltimateWeaponViewModelTest.kt # UW VM: display, owned/locked, balance
presentation/supplies/UnclaimedSuppliesViewModelTest.kt # Supplies VM: drops, claim, claimAll
presentation/workshop/WorkshopViewModelTest.kt     # Workshop VM: categories, purchase, quick invest
presentation/cards/CardsViewModelTest.kt           # Cards VM: display, equip, upgrade
presentation/labs/LabsViewModelTest.kt             # Labs: start research, unlock slot (use-case level)
presentation/home/HomeViewModelTest.kt             # Home VM: profile mapping, tier, drops, missions
presentation/battle/BattleViewModelTest.kt         # Battle VM: init stats, biome, overdrive, ads, toggles
presentation/missions/MissionsViewModelTest.kt     # Missions: generation, claim, milestones (use-case level)
presentation/economy/CurrencyDashboardViewModelTest.kt # Economy VM: weekly, streak, balances
presentation/store/StoreViewModelTest.kt           # Store VM: gems, cosmetics, purchase
presentation/ux/CurrencyGuardTest.kt               # Currency spend clamps to 0 (gems, PS, dust, steps)
presentation/ux/UserFeedbackTest.kt                # Workshop purchase failure sets userMessage
presentation/DeepLinkRoutingTest.kt                # Deep-link intent extra extraction
data/local/RoomSchemaTest.kt                       # Room v9 schema round-trip (profile, steps, workshop, billing_receipt) — billing_receipt round-trip added in C.5 PR 1 exercises every column
data/local/BillingReceiptDaoTest.kt                # 7 tests for BillingReceiptDao: upsert/get round-trip, grantOnceAtomic flip + runs walletCredit, idempotency (second call returns false + walletCredit skipped), markConsumed/markAcknowledged target-only, getGrantedButUnresolved filter, getAll DESC order (C.5 PR 1)
data/billing/BillingManagerImplTest.kt             # 14 tests for BillingManagerImpl (C.5 PR 1): 3 happy paths (GEM_PACK_SMALL consume + AD_REMOVAL ack + SEASON_PASS sub with 30-day expiry), 5 failure paths (user-cancel, product-unavailable, no-activity, connect-fails, pending purchase), idempotency (same purchaseToken does not double-credit), 2 reconciliation cases (PENDING→PURCHASED transition + retryUnresolvedConsumeOrAck without re-credit), isAdRemoved / isSeasonPassActive delegation. Robolectric + real in-memory Room DB for @Transaction semantics + mockito-kotlin for adapter.
data/billing/BillingManagerParityTest.kt           # 3 tests for C.5 PR 2 — asserts StubBillingManager and BillingManagerImpl produce equivalent wallet/flag state on the golden path for each of the 3 product shapes (GEM_PACK_SMALL consumable, AD_REMOVAL non-consumable, SEASON_PASS subscription). Two independent in-memory Room DBs + real PlayerRepositoryImpl on both sides + mocked BillingClientAdapter on real side. Subscription expiry compared within 60s tolerance (stub uses now-at-call-time, real uses purchaseTime).
data/ads/RewardAdManagerImplTest.kt                # 8 tests for RewardAdManagerImpl (C.6 PR 1 / ADR-0006): happy Rewarded; Cancelled on user-dismiss; 4 Error paths (no activity, consent unavailable, load failed with AdMob NO_FILL code 3 → 'No ad available' user msg, show failed with code 1 → 'already shown' user msg); consent-denied-still-grants per Q1; placement → ad-unit routing for all 3 AdPlacements. No Robolectric — plain-Kotlin sealed adapter types + mockito-kotlin.
data/ads/RewardAdManagerParityTest.kt              # 4 tests for C.6 PR 2 — asserts StubRewardAdManager and RewardAdManagerImpl produce equivalent AdResult.Rewarded on the happy path for each of the 3 AdPlacement values (POST_ROUND_GEM / POST_ROUND_DOUBLE_PS / DAILY_FREE_CARD_PACK), plus isAdAvailable parity (both return true for all 3 placements per ADR-0006 decision 4 where the real availability check moves into showRewardAd). Adapter + ConsentManager + Activity mocked via mockito-kotlin — no Robolectric.
data/integration/EscrowLifecycleTest.kt            # End-to-end escrow lifecycle (release + discard)
data/repository/CosmeticRepositoryImplTest.kt      # Seed → ZIGGURAT_COLOR_LOOKUP → overrideColors mapping for all 4 palette-shipping cosmetics (zig_jade, lapis_lazuli_skin, garden_ziggurat_skin, sandals_of_gilgamesh) with exact-value assertions as content-as-code contracts; non-palette seeds have null overrideColors; equipped zig_jade surfaces via observeEquipped with palette intact; ensureSeedData idempotent; partial-catalogue upgrade inserts 4 missing milestone cosmetics without touching legacy 7; existing-row isOwned/isEquipped preserved across ensureSeedData (C.2 PR 2 + PR 3 + PR 3b + PR 3c + ensureSeedData fix)
service/StepWidgetProviderTest.kt                  # Widget SharedPreferences round-trip
```
