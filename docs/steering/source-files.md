# Source File Index

All paths relative to `app/src/main/java/com/whitefang/stepsofbabylon/`.

## Application & DI

```
StepsOfBabylonApp.kt              # @HiltAndroidApp, Configuration.Provider (HiltWorkerFactory)
di/DatabaseModule.kt               # Hilt: Room DB (SQLCipher) + 12 DAO providers
di/RepositoryModule.kt             # Hilt: 8 repository interface → impl bindings (@Singleton)
di/StepModule.kt                   # Hilt: SensorManager provider
di/HealthConnectModule.kt          # Hilt: Health Connect organizational module
di/BillingModule.kt                # Hilt: @Binds BillingManager → BillingManagerImpl (C.5 PR 3: stub deleted, single binding for debug + release). Sibling BillingInternalModule @Binds BillingClientAdapter → RealBillingClientAdapter. Both modules `internal`.
di/AdModule.kt                     # Hilt: @Binds RewardAdManager → RewardAdManagerImpl (C.6 PR 3: stub deleted, single binding for debug + release). Sibling AdInternalModule @Binds RewardedAdAdapter → RealRewardedAdAdapter and ConsentManager → RealConsentManager. Both modules `internal`.
di/TimeModule.kt                   # Hilt: TimeProvider → SystemTimeProvider (B.1)
di/CoroutineScopeModule.kt         # Hilt: @ApplicationScope qualifier + app-lifetime CoroutineScope(SupervisorJob + Dispatchers.Default) for work that outlives VM cancellation (B.3 PR 2)
```

## Data Layer — Room

```
data/local/AppDatabase.kt         # @Database: 13 entities, 13 DAOs, version 11, exportSchema=true
data/local/Migrations.kt          # Registered Migration objects (v7→8 for battleStepsEarned / ADR-0003, v8→9 for billing_receipt / ADR-0005, v9→10 for ultimate_weapon_state recreate-table + bossPsEarnedToday / ADR-0008 + ADR-0009, v10→11 for card_inventory copyCount aggregation / ADR-0010)
data/local/Converters.kt          # @TypeConverters: Map<Int,Int> and Map<String,Int> via JSON
data/local/DatabaseKeyManager.kt  # SQLCipher passphrase via Android Keystore
data/local/PlayerProfileEntity.kt # Player profile entity (single row, id=1)
data/local/PlayerProfileDao.kt    # Player DAO: get() as Flow, atomic currency adjustments (incl. adjustStepBalanceIfSufficient SQL-guarded deduct for B.2 PR 1, spendGemsAtomic + spendPowerStonesAtomic SQL-guarded deducts for V1X-10 — both deduct + increment-spent in single statement, return rowCount for caller success check)
data/local/WorkshopUpgradeEntity.kt # Workshop upgrade entity
data/local/WorkshopDao.kt         # Workshop DAO + @Transaction purchaseUpgradeAtomic default method (B.2 PR 1)
data/local/LabResearchEntity.kt   # Lab research entity
data/local/LabDao.kt              # Lab research DAO
data/local/CardInventoryEntity.kt # Card inventory entity
data/local/CardDao.kt             # Card inventory DAO
data/local/UltimateWeaponStateEntity.kt # UW state entity (R4-06: weaponType PK + damageLevel + secondaryLevel + cooldownLevel + isUnlocked + isEquipped)
data/local/UltimateWeaponDao.kt   # UW state DAO (+markUnlocked +updateDamageLevel/updateSecondaryLevel/updateCooldownLevel for R4-06 per-path upgrades)
data/local/DailyStepRecordEntity.kt # Daily step record entity (with escrow fields)
data/local/DailyStepDao.kt        # Daily step record DAO (with escrow queries) + @Transaction creditBattleStepsAtomic default method (B.2 PR 2); incrementBattleSteps UPSERT SQL supplies all 9 NOT NULL columns explicitly on the INSERT half so the fresh-install first-kill path doesn't crash on NOT NULL before ON CONFLICT can resolve UNIQUE (2026-05-12 hotfix); +creditBossPowerStonesAtomic (R4-07) mirrors the battle-step shape for boss-drop PS with 100/day cap
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
data/repository/UltimateWeaponRepositoryImpl.kt  # Ultimate weapon state (R4-06: toDomain rewrite for 4-column entity → 6-field OwnedWeapon; upgradePathLevel dispatches to per-path DAO methods)
data/repository/StepRepositoryImpl.kt            # Daily step records + escrow + getDailyRecord()
data/repository/WalkingEncounterRepositoryImpl.kt # Walking encounters
data/repository/CosmeticRepositoryImpl.kt        # Cosmetic store items + private ZIGGURAT_COLOR_LOOKUP table (5 palettes: zig_jade @ C.2 PR 2, lapis_lazuli_skin @ PR 3 / IRON_SOLES reward, garden_ziggurat_skin @ PR 3b / MARATHON_WALKER reward, sandals_of_gilgamesh @ PR 3c / GLOBE_TROTTER reward, zig_obsidian @ V1X-14 / store-purchasable dark skin); toDomain populates CosmeticItem.overrideColors from the lookup; SEED_COSMETICS: 11 rows (7 ZIGGURAT_SKIN including the 3 milestone-reward cosmetics + 2 PROJECTILE_EFFECT + 2 ENEMY_SKIN); ensureSeedData uses per-cosmeticId filter so content PRs land on already-seeded installs without a data clear and player `isOwned`/`isEquipped` state survives upgrades
```

## Data Layer — Sensor

```
data/sensor/StepSensorDataSource.kt  # TYPE_STEP_COUNTER wrapper, emits deltas via callbackFlow
data/sensor/StepRateLimiter.kt       # Rolling 1-min window rate limiter (200/min, 250 burst)
data/sensor/StepVelocityAnalyzer.kt  # Unnatural step pattern detection (shaker/spoof), penalty multiplier
data/sensor/StepIngestionPreferences.kt # Service heartbeat + day-start counter for worker/service coordination
data/sensor/DailyStepManager.kt      # Orchestrates: rate limit → velocity analysis → STEP_MULTIPLIER (Workshop) + STEP_EFFICIENCY (Lab) bonus combined under shared +100 % cap (sensor walking only, RO-08 + RO-11 #A.3) → 50k ceiling → Room persist + activity minutes; constructor takes WorkshopRepository + LabRepository
```

## Data Layer — Billing & Ads

```
data/billing/BillingManagerImpl.kt             # Real Play Billing v8 impl (C.5 PR 1 / ADR-0005): adapter + receipt DAO + wallet credits + SHA-256 obfuscatedAccountId anti-fraud; `internal`; wallet side-effects run inside `BillingReceiptDao.grantOnceAtomic`; consume/ack runs after tx commits; `reconcilePendingPurchases` overrides the BillingManager default no-op to sweep PENDING→PURCHASED transitions + retry unresolved consume/ack. Sole `BillingManager` binding post-C.5 PR 3 (`StubBillingManager` deleted after Phase G internal-track on-device verification PASSED 2026-05-18).
data/billing/internal/BillingClientAdapter.kt  # SDK-neutral seam for `BillingManagerImpl` (C.5 PR 1). Interface + sealed result types (SdkBillingResult / SdkPurchase / SdkProductDetails / QueryProductDetailsResult / StartPurchaseResult / QueryPurchasesResult); `internal`; tests mock this directly so no `com.android.billingclient.*` imports leak into unit tests.
data/billing/internal/RealBillingClientAdapter.kt # Concrete BillingClientAdapter backed by Google Play Billing Library v8 (C.5 PR 1). Uses `enableAutoServiceReconnection()`, `PendingPurchasesParams.enableOneTimeProducts`, Mutex-guarded `launchPurchase` with CompletableDeferred bridging PurchasesUpdatedListener. Device-only testable — the only file in the app that imports `com.android.billingclient.*`.
data/billing/internal/ActivityProvider.kt      # WeakReference-backed Singleton Activity holder. MainActivity.onResume sets + onPause clears (C.5 PR 2).
data/ads/RewardAdManagerImpl.kt                # Real AdMob impl (C.6 PR 1 / ADR-0006): adapter + consent manager + per-placement BuildConfig ad-unit routing; `internal`; session-Mutex-guarded; consent → load → show → map to AdResult. Shares the existing `data/billing/internal/ActivityProvider` for the Activity reference needed by `RewardedAd.show()`. Sole `RewardAdManager` binding post-C.6 PR 3 (`StubRewardAdManager` deleted).
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
data/DataDeletionManager.kt                       # Full local data wipe: WorkManager cancel + service stop + DB delete + 10 SharedPreferences clear + Keystore alias delete + Activity recreate (V1X-01)
data/MilestoneNotificationPreferences.kt           # SharedPreferences wrapper for milestone notification dedup
data/NotificationPreferences.kt                   # SharedPreferences wrapper for 4 notification toggles
data/SoundPreferences.kt                          # SharedPreferences wrapper for sound mute/volume
data/anticheat/AntiCheatPreferences.kt            # SharedPreferences wrapper for anti-cheat counters + CV offense tracking
```

## Data Layer — Time

```
data/time/SystemTimeProvider.kt       # Production TimeProvider backed by Instant.now() / LocalDate.now(). @Singleton @Inject.
```

## Data Layer — i18n

```
data/AndroidStrings.kt                # Production domain.Strings impl (V1X-13, ADR-0014): Context.getString over the fx_* string resources. Constructed directly by GameSurfaceView (like SoundManager) — no Hilt binding.
```

## Domain Layer — Time

```
domain/time/TimeProvider.kt           # Wall-clock seam: now() / today(). No Android imports. Introduced by B.1.
domain/Strings.kt                     # i18n seam for engine-internal battle floating-text (V1X-13, ADR-0014). Pure-Kotlin interface, 5 methods (healHp/rapidFireBurst/cashReward/stepReward/powerStoneReward), no Android imports. Impl = data/AndroidStrings; consumed by GameEngine + BattleViewModel via nullable var with literal fallback.
```

## Domain Layer — Battle Engine (V1X-09)

```
domain/battle/engine/SimulationMath.kt # Pure-math helpers extracted from GameEngine. 7 functions: recoveryPulseAmount (V1X-09, ADR-0012), chronoMultiplier (V1X-09), thornReflectionDamage (V1X-09), lifestealHealAmount (V1X-09), tickLifestealAccumulator (V1X-09), clampHp (V1X-09), stepMultiplierBonus (V1X-18, ADR-0015 — asymptotic curve `1 - (1 - 0.05)^level`). Plus 7 constants (RECOVERY_*, CHRONO_SLOW_FACTOR_DEFAULT, LIFESTEAL_CAP, STEP_MULTIPLIER_DECAY_FACTOR, STEP_MULTIPLIER_BONUS_CAP).
domain/battle/engine/Simulation.kt # V1X-09 Phase 3 (ADR-0012, slices 1–6). Pure-domain owner of in-round simulation state. Slice 1: cash economy — @Volatile cash / totalCashEarned (private set) + reset / creditCash (credits both) / applyInterest (cash only, min(level×0.5%,10%), excluded from lifetime) / spend (insufficient-balance guard). Slice 2: round-progress counters — @Volatile totalEnemiesKilled / totalStepsEarned / elapsedSeconds (private set) + tickElapsed / recordEnemyKilled / creditSteps (ignores non-positive) / hasWaveProgress. Slice 3: tickEntities(entities, deltaTime, chronoSlowFactor) scales dt for chrono-slowable (enemy) entities via the EntityProtocol seam, full dt otherwise (lifted from the engine's inline `is EnemyEntity` tick loop). Slice 4: collision sweep — generic detectProjectileEnemyHits<P,E>(projectiles, enemies, onHit) (first-overlap-per-projectile + break) + detectZigguratHits<P>(enemyProjectiles, zigX, zigY, zigWidth, onHit); both fire onHit interleaved INSIDE the loop (exact original overlap expressions) so a hit's knockback/death side-effects are observed by later projectiles the same frame — behaviour-identical to the old CollisionSystem loops. GameEngine holds a Simulation and delegates its cash / totalCashEarned / spendCash + the three counters + hasWaveProgress() + both collision sweeps here; roundOver (control flag) + per-kill / wave-complete reward *formulas* stay in GameEngine. Slice 5: UW lifecycle timers — advanceUWTimers(cooldownRemaining, effectTimeRemaining, deltaTime): UWTimerAdvance (cooldown floored at 0 + effect-duration countdown clamped to 0 on the crossing frame + justExpired/effectWasActive transition flags so the engine runs CHRONO/GOLDEN expiry once and BLACK_HOLE/POISON ongoing effects every active frame incl. the expiry frame) + isUWReadyToFire(...) auto-trigger predicate; GameEngine.updateUWs applies the result + keeps the presentation-coupled side-effects + activateUW (UWState + uwStates + public API unchanged). Slice 6: SimulationEvent flow — events: SharedFlow<SimulationEvent> (replay=0, extraBufferCapacity=64, DROP_OLDEST) + emit(event) (non-suspending tryEmit, game-loop-thread safe); GameEngine emits StepReward/BossKilled from handleEnemyDeath (replacing its two @Volatile callbacks) and BattleViewModel collects the stream. **Completes Phase 3.** No Android imports.
domain/battle/engine/SimulationEvent.kt # V1X-09 Phase 3 final slice (ADR-0012). Pure-domain sealed interface for the game-loop→ViewModel one-shot side-effect hand-off: StepReward(amount,x,y) + BossKilled(tier,x,y). Replaces GameEngine's @Volatile onStepReward/onBossKilled callbacks; emitted via Simulation.emit, collected by BattleViewModel.handleSimulationEvent. No Android imports.
```

## Domain Layer — Models

```
domain/model/Currency.kt              # Enum: STEPS, CASH, GEMS, POWER_STONES
domain/model/PlayerWallet.kt          # Currency balances data class
domain/model/PlayerProfile.kt         # Full profile (maps from PlayerProfileEntity)
domain/model/ActiveResearch.kt        # In-progress lab research
domain/model/OwnedCard.kt             # Player-owned card instance
domain/model/OwnedWeapon.kt           # Player-owned ultimate weapon (R4-06: 6 fields — type, damageLevel, secondaryLevel, cooldownLevel, isUnlocked, isEquipped + levelOf(path))
domain/model/UWPath.kt                # 3-value enum: DAMAGE, SECONDARY, COOLDOWN (R4-06 — used by UpgradeUltimateWeapon + UltimateWeaponRepository)
domain/model/DailyStepSummary.kt      # Daily step record domain model (with escrow fields)
domain/model/SupplyDrop.kt              # Walking encounter supply drop
domain/model/SupplyDropTrigger.kt        # 4 trigger types with notification messages
domain/model/SupplyDropReward.kt         # 4 reward types (Steps, Gems, Power Stones, Card Dust)
domain/model/DropGeneratorState.kt       # Generator state tracking (lastCheckSteps, milestoneTriggered)
domain/model/Milestone.kt               # 6 walking milestones with step thresholds and rewards
domain/model/MilestoneReward.kt          # Sealed class: Gems, PowerStones, Cosmetic
domain/model/DailyMissionType.kt         # 6 daily mission types (walking/battle/upgrade)
domain/model/MissionCategory.kt          # Mission categories: WALKING, BATTLE, UPGRADE (in DailyMissionType.kt)
domain/model/BillingProduct.kt           # 5 billing products + PurchaseResult sealed class + public `skuId()` returning `name.lowercase()` (Plan 31 Phase F unblocker, refines ADR-0005 decision #6 to the lowercase wire format Play Console requires) + opt-in Companion for data-layer `BillingProduct.fromSkuIdOrNull(skuId)` reverse lookup (C.5 PR 1)
domain/model/AdPlacement.kt              # 3 ad placements + AdResult sealed class
domain/model/CosmeticCategory.kt         # 3 cosmetic categories (ziggurat, projectile, enemy)
domain/model/CosmeticItem.kt             # Cosmetic item domain model (+ optional overrideColors: List<Int>? for renderer override, C.2 PR 1)
domain/model/UpgradeType.kt           # 24 Workshop upgrade types with configs (R4-03 added RAPID_FIRE in ATTACK)
domain/model/RapidFireSchedule.kt     # R4-03 helper: per-level (interval, duration, multiplier) interpolation table for the RAPID_FIRE upgrade. L1 60s/5s/2.0× → L10 30s/30s/3.0× (duration matches interval = permanent buff). Centralises math so GameEngine.tickRapidFire and DescribeUpgradeEffect.formatRapidFire read identical numbers.
domain/model/UpgradeCategory.kt       # Attack, Defense, Utility categories
domain/model/UpgradeConfig.kt         # Upgrade configuration (baseCost, scaling, maxLevel)
domain/model/Tier.kt                  # Tier data class
domain/model/TierConfig.kt            # Full tier table (1–10)
domain/model/BattleCondition.kt       # 7 battle condition types
domain/model/Biome.kt                 # 5 biomes with forTier() mapping
domain/model/BattleConditionEffects.kt # Pre-computed battle condition modifiers from tier
domain/model/EnemyType.kt             # 6 enemy types with multipliers
domain/model/UltimateWeaponType.kt    # 6 UW types with per-path L1/L10 spec + valueAtLevel/cooldownAtLevel/damageAtLevel/secondaryAtLevel/costForPath (R4-06)
domain/model/UltimateWeaponLoadout.kt # UW loadout (max 3)
domain/model/ResearchType.kt          # 12 lab research types; +`val isComingSoon: Boolean = false` constructor field (RO-11 #B.2); only AUTO_UPGRADE_AI flagged true with description "Reserved for v1.x — research progress preserved" (ENEMY_INTEL wired in V1X-15b: 8000×1.5 cost / 4h×1.10 / maxLevel 10 / +2%/lvl damage outer multiplier + L1/L5/L10 UI overlays per ADR-0017); the other 11 wired end-to-end (DAMAGE / HEALTH / CRITICAL / REGEN / CASH / STEP_EFFICIENCY / UW_COOLDOWN / ENEMY_INTEL as outer multipliers via ResolveStats + GameEngine + DailyStepManager; WAVE_SKIP via WaveSpawner.startWave; MULTISHOT_RESEARCH / BOUNCE_RESEARCH via ResolveStats caps)
domain/model/CardRarity.kt            # Common, Rare, Epic
domain/model/CardType.kt              # 9 card types with effects + effectAtLevel/secondaryAtLevel/effectDescriptionAtLevel(level) — the description method (#53 fix) is the single source of truth for the live, level-aware UI string and stays in lockstep with the gameplay math by deriving its numbers from the same effectAtLevel formula
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
domain/repository/UltimateWeaponRepository.kt   # Ultimate weapon interface (R4-06: upgradePathLevel replaces upgradeWeapon)
domain/repository/StepRepository.kt             # Daily step records + escrow + Health Connect methods
domain/repository/WalkingEncounterRepository.kt # Walking encounter interface
domain/repository/BillingManager.kt             # Billing interface (purchase, query, reconcilePendingPurchases with default no-op so fakes inherit do-nothing contract; C.5 PR 1). Plan 31 PR B added `getPriceDisplay(product): String?` (default null) so the Store screen can read live formatted prices from Play Billing's ProductDetails.priceDisplay instead of the static BillingProduct.priceDisplay constants.
domain/repository/RewardAdManager.kt            # Reward ad interface (show ad, availability)
domain/repository/CosmeticRepository.kt         # Cosmetic store interface + `idExists(cosmeticId): Boolean` (C.4 — used by ClaimMilestone to pre-flight MilestoneReward.Cosmetic ids and surface UnknownCosmetic result variant for the 3 currently-mismatched milestone cosmetic ids)
domain/usecase/CalculateUpgradeCost.kt          # Cost formula: baseCost * scaling^level
domain/usecase/CanAffordUpgrade.kt              # Affordability check against wallet
domain/usecase/PurchaseUpgrade.kt               # Delegates to WorkshopRepository.purchaseUpgradeAtomic — atomic deduct + level-set (B.2 PR 1)
domain/usecase/QuickInvest.kt                   # Recommends cheapest affordable upgrade
domain/usecase/ResolveStats.kt                  # Workshop + in-round + lab research levels → ResolvedStats. RO-08: in-round multiplier `ir(...)` extended to all 14 stat-bearing upgrade types (was previously only DAMAGE/ATTACK_SPEED/HEALTH); multiplicative stats follow `(1+ws*x)*(1+ir*x)`, additive stats sum levels before per-level effect and any cap. Range now multiplicative + clamped to BASE × 3. RO-11 #A.1: optional `labLevels: Map<ResearchType, Int> = emptyMap()` parameter adds 3rd multiplicative tier — DAMAGE_RESEARCH (+5 %/lvl), HEALTH_RESEARCH (+5 %/lvl), CRITICAL_RESEARCH (+3 %/lvl), REGEN_RESEARCH (+4 %/lvl). Default empty preserves existing call sites.
domain/usecase/CalculateDamage.kt               # Raw damage + crit roll + damage/meter bonus → DamageResult
domain/usecase/CalculateDefense.kt              # Damage reduction (cap 75%) + flat block
domain/usecase/UpdateBestWave.kt                # Compares wave to stored best, persists if new record
domain/usecase/CheckTierUnlock.kt               # Checks wave milestones for tier unlock eligibility
domain/usecase/UnlockUltimateWeapon.kt           # Checks Power Stone balance, deducts, sets isUnlocked flag (R4-06)
domain/usecase/UpgradeUltimateWeapon.kt          # Per-path UW upgrade: takes UWPath param, cost scaling per level, max level 10 (R4-06)
domain/usecase/CalculateResearchCost.kt          # Research cost: baseCostSteps × costScaling^level
domain/usecase/CalculateResearchTime.kt          # Research time: baseTimeHours × timeScaling^level
domain/usecase/StartResearch.kt                  # Validates slots/affordability/max level, deducts Steps, starts timer
domain/usecase/CompleteResearch.kt               # Completes research when timer elapsed, increments level
domain/usecase/RushResearch.kt                   # Instant complete via Gems (50–200 linear cost)
domain/usecase/UnlockLabSlot.kt                  # Unlock lab slot (200 Gems, max 4)
domain/usecase/CheckResearchCompletion.kt        # Auto-completes all expired research on app launch
domain/usecase/UpdateCompleteResearchMissionProgress.kt # R3-03 (#1): COMPLETE_RESEARCH daily-mission tick. Encapsulates the DAO lookup + progress update + count gating in one place; takes `DailyMissionDao`, exposes `invoke(completedCount: Int, today: String = LocalDate.now().toString())`. Early returns on `completedCount <= 0` (closes the false-trigger). Additive-with-cap progress increment so a multi-completion auto-batch on app launch correctly reflects all completions while clamping at `m.target`. Idempotent: missing row / already-claimed / already-completed / DAO exceptions are silent no-ops (matches the prior `LabsViewModel.updateResearchMission` fail-open contract).
domain/usecase/OpenCardPack.kt                   # Opens card pack: 3 tiers, rarity rolling, duplicate→dust
domain/usecase/UpgradeCard.kt                    # Card upgrade: Card Dust cost by rarity × level
domain/usecase/ApplyCardEffects.kt               # Post-process ResolvedStats with equipped card effects
domain/usecase/ManageCardLoadout.kt              # Equip/unequip cards (max 3 loadout)
domain/usecase/GenerateSupplyDrop.kt             # Seeded random supply drop generation (4 triggers)
domain/usecase/ClaimSupplyDrop.kt                # Credits reward to player, marks drop claimed
domain/usecase/TrackWeeklyChallenge.kt           # Weekly step challenge PS awards (50k/75k/100k)
domain/usecase/TrackDailyLogin.kt                # Daily login PS + Gem streak
domain/usecase/AwardWaveMilestone.kt             # PS on new personal-best waves (1/2/5)
domain/usecase/AwardBossPowerStones.kt           # PS on boss kills: tier×PS (T1=1, T10=10), 100/day cap, atomic DAO path (R4-07 / ADR-0009)
domain/usecase/CheckMilestones.kt                # Detect newly achievable walking milestones
domain/usecase/ClaimMilestone.kt                 # Credit milestone rewards via MilestoneDao.claimMilestoneAtomic (gems + power stones) with pre-flight CosmeticRepository.idExists check; returns `ClaimMilestoneResult` sealed type (Success | InsufficientSteps | AlreadyClaimed | UnknownCosmetic(cosmeticId)) so the 3 mismatched milestone cosmetic ids surface explicitly instead of silently dropping (C.4)
domain/usecase/GenerateDailyMissions.kt          # Generate 3 daily missions (date-seeded random)
domain/usecase/PurchaseGemPack.kt                # Purchase Gem pack via BillingManager
domain/usecase/DescribeUpgradeEffect.kt          # RO-11 #C / RO-10: per-row "Now → Next" readout for in-round upgrade menu. UpgradeEffectReadout(current, next?) data class; next is null at maxLevel. Stat-bearing upgrades call ResolveStats twice (current + post-purchase) and format the relevant ResolvedStats field with unit suffix (dmg/HP/px//s/%); cash utilities + hidden-from-in-round upgrades (STEP_MULTIPLIER, RECOVERY_PACKAGES, included for Workshop reuse) compute from UpgradeConfig.effectPerLevel directly with cap clamps. Format strings pinned to Locale.ROOT for deterministic output across locales (English-only v1.0 strings; localization deferred to v2.0). RO-12: optional `equippedCards: List<OwnedCard> = emptyList()` invoke param + `applyCardEffects: ApplyCardEffects = ApplyCardEffects()` constructor param threads card effects post-resolveStats so the readout matches the live engine pipeline (`resolveStats → applyCardEffects → engine.setStats`). HEALTH_REGEN format bumped from %.1f/s to %.2f/s so +2 %/level on a base ~1.3/s produces a visibly different Lv 0 → Lv 1 readout (was "1.3/s → 1.3/s" pre-RO-12).
domain/battle/entity/EntityProtocol.kt # V1X-09 Phase 3 (ADR-0012, slices 3–4). Pure-domain entity seam (no Android imports): `val isAlive` + `val x` / `val y` / `val width` (positional reads for the collision sweep, slice 4) + `val isChronoSlowable` (default false) + `fun update(deltaTime)`. Lets `Simulation.tickEntities` + the `Simulation` collision sweeps iterate battle entities without touching Canvas. Implemented by presentation `Entity`; `EnemyEntity` overrides `isChronoSlowable = true`.
domain/battle/entity/ProjectileState.kt # V1X-09 Phase 2 (ADR-0012, first per-entity extraction). Pure projectile-motion state (no Android imports): owns x/y + alive flag + homing-toward-target step. Presentation ProjectileEntity delegates update() here. Establishes the domain/battle/entity/<Name>State pattern for the remaining entities.
domain/battle/entity/OrbState.kt # V1X-09 Phase 2 (ADR-0012). Pure orbit position + radial-oscillation state (no Android imports): owns angle + radialPhase + derived (x,y) + currentOrbitRadius; companion consts ORBIT_RADIUS_MIN/MAX (25/70 px) + ORBIT_PERIOD_SEC (2.5 s) + MID/AMPLITUDE/RADIAL_ANGULAR_SPEED (the #54 oscillation math). Presentation OrbEntity delegates position here; enemy-proximity / hit-cooldown stays in OrbEntity.
domain/battle/entity/EnemyState.kt # V1X-09 Phase 2 (ADR-0012). Pure movement + attack-cooldown state (no Android imports): owns (x,y) + attackCooldown + initialDist; spawn(x,y) captures initialDist, update(dt):Boolean does the homing step while beyond stopDistance (RANGED uses initialDist*RANGED_STOP_FACTOR 0.4, melee uses MELEE_RANGE 40) and returns true on the frame an attack should fire, applyKnockback shifts position. Presentation EnemyEntity delegates motion/attack here + syncs x/y back to the Entity base each tick; HP/armor/death/attack-callbacks/render stay in EnemyEntity.
domain/battle/entity/ZigguratState.kt # V1X-09 Phase 2 (ADR-0012, final entity). Pure ziggurat simulation state (no Android imports): owns stats (private set) + currentHp/maxHp + @Volatile rapidFireMultiplier + private attackCooldown; derived attackRange (=stats.range) + attackInterval (=1/(attackSpeed*rapidFireMultiplier)); regenHp(dt) clamps at maxHp; tickAttackReady(dt):Boolean decrements cooldown returns ≤0; onFired() resets to attackInterval; holdReady() clamps to 0; updateStats(newStats) redirects derived reads. Presentation ZigguratEntity delegates its entire public surface here so GameEngine/BattleViewModel are untouched.
```

## Presentation Layer

```
presentation/MainActivity.kt                      # Single Activity, Scaffold + NavHost + BottomNavBar, permissions; onResume/onPause drive ActivityProvider.set/clear for Play Billing purchase flow (C.5 PR 2); onResume also fires a flag-gated one-shot UMP consent prefetch via injected ConsentManager so the first reward-ad tap doesn't pay the init latency (C.6 PR 2)
presentation/HealthConnectPermissionActivity.kt    # Scrollable privacy policy for Health Connect permissions
presentation/navigation/Screen.kt                 # Sealed class: 12 routes (Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store)
presentation/navigation/BottomNavBar.kt            # Bottom navigation bar with 5 items
presentation/home/HomeViewModel.kt                 # @HiltViewModel: combines profile + step flows → HomeUiState. #55 fix: `init` captures the `List<ResearchType>` returned by `CheckResearchCompletion(labRepository)()` and passes `completed.size` to a new `private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)` field, mirroring the LabsViewModel R3-03 pattern. Pre-fix the return value was discarded so background-completed research never advanced the COMPLETE_RESEARCH daily mission. `dailyMissionDao` was already injected for the existing `countClaimable` consumer; no Hilt graph change.
presentation/home/HomeUiState.kt                   # UI state: steps, balance, tier, biome, bestWave
presentation/home/HomeScreen.kt                    # Dashboard: step card, currencies, tier selector, battle button
presentation/home/TierSelector.kt                  # Horizontal tier chip row with lock/unlock states
presentation/workshop/WorkshopViewModel.kt         # @HiltViewModel: upgrades + wallet → WorkshopUiState
presentation/workshop/WorkshopUiState.kt           # UI state: upgrade list, balance, selected category
presentation/workshop/WorkshopScreen.kt            # 3-tab layout, upgrade list. V1X-13: weapons/cards button labels + balance header via stringResource (workshop_*).
presentation/workshop/UpgradeCard.kt               # Reusable upgrade card (affordable/expensive/maxed states). V1X-13: MAX/level/cost via stringResource (upgrade_max/upgrade_level/upgrade_cost_steps).
presentation/battle/BattleScreen.kt                # Compose wrapper: AndroidView + overlays (HUD, pause, post-round), auto-pause. R3-04 (#3): bottom control-row modifier chain rewrapped to align → windowInsetsPadding(navigationBars) → padding(bottom=24dp) → horizontalScroll(rememberScrollState()) → background → padding(h=12dp,v=8dp) so the buttons clear the system gesture handle and gracefully scroll horizontally on phones too narrow to fit them all (e.g. Pixel 6, 411dp). R4-01: Overdrive button + status text + menu invocation removed; row is now 5 buttons (3× speed + Pause + Upgrade). The horizontal-scroll safety net stays in place because Wave 2 R4-03 (Rapid Fire) may add visual indicators back to the row. V1X-13: HUD (wave header / cash / Battle-Steps banner) + bottom-control speed labels and the quit/speed/pause/upgrades content descriptions via stringResource (battle_*, cash_amount, steps_earned_banner); ▶/⏸ glyphs left literal.
presentation/battle/BattleViewModel.kt             # @HiltViewModel: 16-param constructor (RO-11 added LabRepository); round lifecycle in `runEndRoundPersistence` wrapped in `AppDatabase.withTransaction` (B.2 PR 5) with per-write runCatching error isolation (B.3 PR 1); `onCleared` guard launches persistence on `@ApplicationScope CoroutineScope` when round is mid-progress (B.3 PR 2); hydrates `engine.cosmeticOverrides` from CosmeticRepository (C.2 PR 1); RO-11 #A.2 + #B.1: `var labLevels: Map<ResearchType, Int>` and `var startWave: Int` snapshots populated in init/playAgain; private helpers `cashResearchMultiplier()` / `uwCooldownMultiplier()` / `waveSkipStartWave()`; pushes engine multipliers via `cashResearchMultiplier` + `uwCooldownMultiplier` and threads `startWave` through `surfaceView.configure(...)`; RO-11 #C: public `describeEffect(type)` exposes `DescribeUpgradeEffect` use case sharing the same `resolveStats` instance for drift-free Now → Next preview. RO-12: private `resolveCurrentStats(inRound)` helper runs the live-engine pipeline (`resolveStats(workshop, inRound, lab) → applyCardEffects(stats, equippedCards).stats`); used by `purchaseInRoundUpgrade` so lab + card multipliers survive every in-round purchase (pre-RO-12 the call site dropped both `labLevels` and `applyCardEffects`, silently stripping research + card bonuses for the rest of the round); `describeEffect` threads `equippedCards` through to DescribeUpgradeEffect to keep the readout in lockstep. V1X-15b: `@VisibleForTesting internal applyResearchParams(engine)` dedups the cash/UW-cooldown/ENEMY_INTEL engine pushes across `startPollingEngine` + `playAgain`; private `enemyIntelLevel()` reads `labLevels[ENEMY_INTEL]` and is pushed to `engine.enemyIntelLevel`.
presentation/battle/BattleUiState.kt               # UI state: wave, HP, cash, speed, pause, RoundEndState
presentation/battle/GameSurfaceView.kt             # SurfaceView + SurfaceHolder.Callback, manages game loop thread; tracks `currentStartWave` and threads it through all 3 `engine.init` call sites (configure / surfaceCreated / surfaceChanged) so `playAgain` mid-session sees the latest WAVE_SKIP level (RO-11 #B.1); R3-01: surfaceCreated + surfaceChanged route through `@VisibleForTesting internal fun initEngineIfNeeded()` which gates `engine.init` on `!engine.hasWaveProgress()` so a background-and-resume cycle preserves the in-flight round; `@Volatile internal var pendingSpeed` / `pendingPaused` captured by `setSpeedMultiplier` / `setPaused` survive the `gameThread = null` lifecycle gap and seed the new thread when surfaceCreated fires
presentation/battle/GameLoopThread.kt              # Dedicated thread: fixed timestep (60 UPS), accumulator, speed multiplier
presentation/battle/engine/GameEngine.kt           # Central coordinator: entity list, update/render dispatch, wave/collision integration; +hasWaveProgress() (B.3 PR 2); +@Volatile cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> consulted in init() to select ziggurat layer colors (C.2 PR 1); +applyStats() single-mutation point that propagates ResolvedStats updates to engine + ziggurat in lock-step (RO-08); +updateEffectiveLevels() public setter for in-round cash-utility levels (RO-08); +tickRecoveryPackages() periodic-heal pulse (RO-08, RECOVERY_PACKAGES — 30s interval, 1% per level capped at 50%, SPAWNING-phase only); RO-09 #1: entities.forEach scales `deltaTime` for `EnemyEntity` only when `chronoActive` true (`CHRONO_SLOW_FACTOR=0.10f` companion constant) so CHRONO_FIELD UW actually slows enemies; projectiles/orbs/ziggurat keep unscaled `deltaTime`. R4-01: Step Overdrive deleted entirely — `activeOverdrive` / `overdriveTimeRemaining` / `preOverdriveStats` / `overdriveAuraEffect` fields removed; `activateOverdrive` / `expireOverdrive` methods deleted; the `update()` overdrive-timer block deleted. GOLDEN_ZIGGURAT becomes the sole writer of `fortuneMultiplier`: activate hard-sets to 5.0×, expiry resets to 1.0× (collapses the 3-site coerceAtLeast/cross-overdrive RO-09 #2 lifecycle to 2 unconditional writes). R3-02: 2 onMeleeHit lambdas (initial WaveSpawner wiring + SCATTER child enemy spawn) flipped from `{ _, dmg -> applyDamageToZiggurat(dmg, null) }` to `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }` so `applyThorn` actually fires; +`lifestealAccumulator: Double = 0.0` field reset in init() + new `applyLifesteal(healAmount)` helper that mirrors applyThorn's shape and emits `FloatingText("+X HP", STEP_COLOR)` each time the accumulator crosses an integer HP threshold so low-level LIFESTEAL produces visible burst feedback (math identical to pre-fix; only the visual is new). R4-06: UWState 4-field struct (type + damageLevel + secondaryLevel + cooldownLevel); auto-trigger on cooldown replaces manual activation; per-path `activateUW`; `chronoSlowFactor` instance field (companion `CHRONO_SLOW_FACTOR` constant removed). V1X-15b: +@Volatile `enemyIntelLevel` (set by BattleViewModel, not reset in init) gating 3 overlays — `nextWaveCompositionLabel()` (L1+, fed into `WaveCooldownText`) / `bossCountdownLabel()` (L10) / L5+ per-enemy HP-% text drawn in `render()`. #118: +private `entitiesLock` monitor — `update()` (whole tick), `init()` (whole rebuild), `applyStats` orb-reconcile branch, and `render()` (snapshot-under-lock then draw outside) all `synchronized(entitiesLock)` so the loop thread can't collide with a main-thread ORBS reconcile / playAgain re-init; `stats` marked `@Volatile`. Guarded by `GameEngineConcurrencyTest`.
presentation/battle/engine/Entity.kt               # Abstract base: x, y, width, height, isAlive, update(), render(). V1X-09 Phase 3: implements pure-domain EntityProtocol — isAlive + isChronoSlowable (default false) + update + the x/y/width positional reads (now `override var`; the collision sweep reads them) — so Simulation.tickEntities + the collision sweeps can iterate without Canvas; height stays a plain var (not in the protocol); render() stays abstract.
presentation/battle/engine/WaveSpawner.kt          # Wave lifecycle: 26s spawn + 9s cooldown, enemy composition by wave; RO-11 #B.1 added optional `startWave: Int = 1` constructor param backing `var currentWave: Int = startWave` so BattleViewModel can map a WAVE_SKIP lab-research level to a higher initial wave (L0 → wave 1, L10 → wave 11). Enemy scaling at the higher wave is automatic via EnemyScaler. R3-02 changed `onMeleeHit: (Double) -> Unit` to `(EnemyEntity, Double) -> Unit` so consumers can react to the attacker reference (THORN_DAMAGE reflection); the field is forwarded as-is to each spawned `EnemyEntity.onMeleeHit`. V1X-15b added 2 pure helpers for the ENEMY_INTEL overlays: `getWaveComposition(wave): Map<EnemyType,Int>` (deterministic expected per-type counts mirroring the `pickType` probability bands, boss split off index 0 on boss waves) + `wavesUntilNextBoss(): Int`; backed by private `typeProbabilities(wave)`.
presentation/battle/engine/EnemyScaler.kt          # Wave-based stat scaling (1.05^wave × tierMultiplier), cash rewards per type
presentation/battle/engine/CollisionSystem.kt      # Projectile↔enemy and enemy projectile↔ziggurat collision. V1X-09 Phase 3 (ADR-0012, slice 4): reduced to a presentation adapter — keeps only the concrete-type concerns (filterIsInstance partitioning + alive snapshot) and delegates both sweeps to the pure-domain Simulation (detectProjectileEnemyHits / detectZigguratHits). Takes a Simulation param (GameEngine.update passes its `simulation`); the iteration + overlap geometry now live in the domain.
presentation/battle/entities/ZigguratEntity.kt     # 5-layer ziggurat, nearest-enemy targeting, HP tracking. V1X-09 Phase 2 (ADR-0012): HP/regen + attack-cooldown + rapidFireMultiplier + derived attackInterval/attackRange delegated to pure-domain ZigguratState; the entity's full public surface (currentHp/maxHp/stats/rapidFireMultiplier/attackRange/updateStats) delegates to the state so GameEngine/BattleViewModel are untouched. Keeps layer geometry + originX/Y/centerY + targeting/fire callback + render(). Constructor unchanged. RO-08 / R4-01: stats updates propagate live via updateStats (called by GameEngine.applyStats); attackInterval / attackRange read the live stats so in-round upgrades + GOLDEN_ZIGGURAT damage buff propagate mid-round. R4-01 dropped overdriveColor / overdriveProgress fields + the timer-bar render block + the timerBgPaint / timerFillPaint paints (used only by the timer bar).
presentation/battle/entities/ProjectileEntity.kt   # Moves toward target, self-destructs on arrival. V1X-09 Phase 2: motion delegated to pure-domain ProjectileState; this class keeps render() + the collision/bounce fields (damage/bouncesRemaining/hitEnemies). Constructor signature unchanged.
presentation/battle/entities/EnemyEntity.kt        # 6 enemy types, melee/ranged attack, mini HP bar. V1X-09 Phase 3: overrides EntityProtocol.isChronoSlowable = true (sole chrono-slowed entity; read by Simulation.tickEntities). V1X-09 Phase 2 (ADR-0012): movement + attack-cooldown delegated to pure-domain EnemyState; this class syncs x/y back to the Entity base each tick + keeps HP/armor/death, the melee/ranged attack callbacks, and render(). Constructor + spawner wiring unchanged. R3-02 changed `onMeleeHit: ((Double) -> Unit)?` to `((EnemyEntity, Double) -> Unit)?`; `update()` invokes `onMeleeHit?.invoke(this, damage)` so consumers see the attacker reference (THORN_DAMAGE reflection). Pre-R3-02 the chain dropped the attacker so `GameEngine.applyThorn` always early-returned despite being plumbed. (V1X-15b ENEMY_INTEL L5+ HP-% labels are drawn by `GameEngine.render` looping live `EnemyEntity`s, NOT in this class — keeps the level gate out of the entity constructor.)
presentation/battle/entities/EnemyProjectileEntity.kt # Ranged enemy projectiles targeting ziggurat; shooter field for thorn damage reflection. V1X-09 Phase 2: homing motion reuses the same pure-domain ProjectileState as ProjectileEntity (identical math); keeps render() + damage/shooter fields. Constructor unchanged.
presentation/battle/entities/OrbEntity.kt          # Orbiting projectiles circling ziggurat, per-enemy hit cooldown. #54 fix: orbit radius oscillates between ORBIT_RADIUS_MIN (25 px) and ORBIT_RADIUS_MAX (70 px) over ORBIT_PERIOD_SEC (2.5 s) so the inner sweep overlaps the enemy melee zone and the outer sweep places the orb cleanly outside HIT_RANGE. V1X-09 Phase 2: orbit position + radial-oscillation math delegated to pure-domain OrbState; OrbEntity keeps hit-cooldown/proximity (needs EnemyEntity refs + onHitEnemy callback) + render(), and re-exposes ORBIT_RADIUS_MIN/MAX + ORBIT_PERIOD_SEC + currentOrbitRadius via @VisibleForTesting from OrbState so the Robolectric OrbEntityTest is untouched. Constructor unchanged (initialRadialPhase still controls phase for tests).
presentation/battle/effects/ParticlePool.kt        # Pre-allocated particle pool (200 capacity), acquire/release/recycle
presentation/battle/effects/EffectEngine.kt        # Manages active effects, owns ParticlePool + ScreenShake
presentation/battle/effects/ScreenShake.kt         # Canvas translate oscillation with decaying amplitude
presentation/battle/effects/ReducedMotionCheck.kt  # System ANIMATOR_DURATION_SCALE reader
presentation/battle/effects/ProjectileTrailEffect.kt # Fading biome-colored trail particles behind projectiles
presentation/battle/effects/DeathEffect.kt         # Per-enemy-type death burst (6 types, 6-20 particles)
presentation/battle/effects/FloatingText.kt        # "+X Cash" rising text that fades
presentation/battle/effects/UWVisualEffect.kt      # 6 particle-based UW activation spectacles
presentation/battle/effects/WaveAnnouncement.kt    # Wave number slide-in + boss warning + cooldown countdown. V1X-15b: `WaveCooldownText` gained an optional `nextWaveComposition: String?` 2nd param (ENEMY_INTEL L1+ next-wave composition line, drawn below the timer).
presentation/audio/SoundManager.kt                 # SoundPool wrapper, 7 effects, volume/mute. V1X-05: `play(effect, expectedIntervalMs = 100L)` — frequency-aware SHOOT throttle `(interval/3).coerceIn(30,100)ms` so RAPID_FIRE + high attack-speed feel responsive without exhausting SoundPool channels.
presentation/audio/MusicManager.kt                 # V1X-06: MediaPlayer wrapper for 2 looping tracks (bgm_walking / bgm_battle). Audio focus listener pauses on phone call / Spotify; volume + mute control. MainActivity wires lifecycle (resume/pause/release) + nav-state observer that switches tracks when entering/leaving Battle.
presentation/audio/MusicPreferences.kt             # V1X-06: SharedPreferences wrapper for music mute toggle + volume slider state. Stored under "music_prefs"; cleared by DataDeletionManager.
presentation/battle/ui/HealthBarRenderer.kt        # HP bar: green→yellow→red gradient, numeric text
presentation/battle/ui/InRoundUpgradeMenu.kt      # In-round upgrade menu: 3 tabs, purchase with Cash; RO-11 #C / RO-10 added optional `describeEffect: (UpgradeType) -> UpgradeEffectReadout` parameter (default no-op fallback) that drives a per-row Gold-#D4A843 "Now → Next" readout below the description. Suppresses the readout line when describeEffect returns empty current; renders "Now: X (MAX)" without arrow when next is null. V1X-13: cash/FREE/level-desc/readout/MAX/cost via stringResource (inround_*, cash_amount, upgrade_max); ✕ glyph + dynamic tab/type names left literal.
presentation/battle/ui/PostRoundOverlay.kt         # Post-round summary: wave, kills, cash, time, new record banner. V1X-13: all banners/stat rows/ad buttons via stringResource + share text & chooser via getString in onClick (postround_*, cash_amount, steps_earned_banner).
presentation/battle/ui/PauseOverlay.kt             # Pause overlay: Resume + Quit Round buttons. V1X-13: title + buttons via stringResource (pause_title/action_resume/pause_quit_round).
presentation/battle/ui/BiomeTransitionOverlay.kt   # Full-screen biome reveal overlay with step count. V1X-13: welcome/steps-walked/continue via stringResource (biome_*, action_continue); biome name dynamic.
presentation/battle/ui/UltimateWeaponBar.kt        # UW passive cooldown display (R4-06: no clickable activation buttons; auto-trigger replaces manual)
presentation/battle/biome/BiomeTheme.kt            # 5 biome color palettes (sky, ground, ziggurat, enemy, particles)
presentation/battle/biome/BackgroundRenderer.kt    # Gradient sky + ambient particle system per biome
presentation/ui/theme/Color.kt                     # Compose color definitions
presentation/ui/theme/Theme.kt                     # Compose theme setup (Material3)
presentation/weapons/UltimateWeaponViewModel.kt    # @HiltViewModel: UW unlock/upgrade/equip state (R4-06: UWPathDisplay + UWDisplayInfo.paths for per-path UI)
presentation/weapons/UltimateWeaponScreen.kt       # UW management: 6 cards with lock/unlock/equip + 3 per-path Upgrade buttons (R4-06)
presentation/labs/LabsViewModel.kt                  # @HiltViewModel: research state + wallet + countdown ticker. R3-03: replaced the private `updateResearchMission()` helper with `private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)`; all 3 call sites (init / rushResearch / freeRush) pass an explicit count (init: `completed.size` from CheckResearchCompletion; rush: 1 on Result.Rushed; freeRush: 1 after manual completeResearch). The DailyMissionType import was removed.
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
presentation/store/StoreUiState.kt                     # UI state: gems, adRemoved, seasonPass, cosmetics, priceDisplays (Map<BillingProduct, String> populated from BillingManager.getPriceDisplay; missing keys signal UI fallback to BillingProduct.priceDisplay constant; Plan 31 PR B)
presentation/store/StoreScreen.kt                      # Store screen: Gem packs, Ad Removal, Season Pass, Cosmetics
```

## Resources

All paths relative to `app/src/main/res/`.

```
drawable/ic_launcher_background.xml     # Solid #0E2247 deep-lapis vector background for the adaptive launcher icon. Solid (not gradient) for consistent rendering across launcher mask shapes.
drawable/ic_launcher_foreground.xml     # 5-tier stepped-ziggurat silhouette vector, single compound path, vertical 3-stop linear gradient Gold #D4A843 (top) → SandStone #C2B280 (mid) → lightened-DeepBronze #8B5A3A (bottom). All content inside the 72dp safe zone; tower center (54,54) matches canvas center.
mipmap-anydpi-v26/ic_launcher.xml       # <adaptive-icon> pointing at the drawable/ background + foreground pair.
mipmap-anydpi-v26/ic_launcher_round.xml # Same contents as ic_launcher.xml — Android handles round masking from the adaptive source; no separate round asset needed.
values/strings.xml                      # String resources. app_name + 16 notification strings + 5 fx_* battle floating-text strings (engine-internal, via domain/Strings) + ~45 battle/workshop Compose-surface strings (V1X-13 i18n phase 1: workshop_*, upgrade_*, battle_*, cash_amount, steps_earned_banner, inround_*, pause_*, action_resume/continue, biome_*, postround_*).
xml/network_security_config.xml         # Network security config (cleartext blocked).
xml/step_widget_info.xml                # AppWidget provider info for the 2×2 step widget.
raw/                                    # Placeholder sine-wave sound assets.
layout/                                 # widget_step_counter.xml — 2×2 step widget layout (battle renderer is SurfaceView, not Compose/XML). V1X-13: title uses @string/app_name; the 2 runtime-set TextViews use design-time tools:text (no android:text) so the HardcodedText lint-as-error guard (app/build.gradle.kts `lint { error += "HardcodedText" }`) stays clean. HardcodedText is XML-only — does NOT catch Compose Text() (see docs/architecture.md i18n section).
```

## Tools & Release Assets

```
tools/render_play_store_icon.py                       # Pillow-only renderer that reproduces the in-app vector adaptive icon as a 512×512 PNG for Play Store hi-res upload. Re-renders pixel-for-pixel from the same 108-viewport path coords + 3-stop gradient stops as the XML drawables. Supersamples to 2048×2048 before LANCZOS downsample for crisp anti-aliased edges. Run via `python3 tools/render_play_store_icon.py`; if the icon design ever changes, edit BOTH this script AND the matching XML.
docs/release/store-assets/StepsOfBabylonArt.png                              # User-supplied source for the Play Store feature graphic. 1376×768 pixel-art Tower of Babel scene (ziggurat tower, swirling sky, walking figure, framing ruins). Preserved in-repo for re-crops.
docs/release/store-assets/play-store-icon-512.png                            # Play Store hi-res app icon, 512×512, ~3.8 KB. Generated artifact — regenerate via the script above when source changes. Tracked in git as a release asset (Play Store assets repository pattern).
docs/release/store-assets/play-store-feature-graphic-1024x500.png            # Play Store feature graphic, 1024×500, ~621 KB. Center-vertical-cropped (y=48..720) from StepsOfBabylonArt.png + LANCZOS-downscaled. PNG (not JPEG) preserves pixel-art crispness.
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

All paths relative to `app/src/test/java/com/whitefang/stepsofbabylon/` unless noted otherwise. The `androidTest` source set lives at `app/src/androidTest/java/com/whitefang/stepsofbabylon/` and is documented at the bottom of this section (V1X-08 Phase 1A).

```
architecture/DomainPurityTest.kt                 # Machine-enforced domain-purity guard (#27): dependency-free JVM test that fails if any domain/ file imports android./androidx./com.android./com.google.android.; self-validating (asserts domain root dir exists first). 1 test.
fakes/FakePlayerRepository.kt                    # In-memory StateFlow-backed fake for PlayerRepository
fakes/FakeWorkshopRepository.kt                  # In-memory StateFlow-backed fake for WorkshopRepository
fakes/FakeUltimateWeaponRepository.kt            # In-memory StateFlow-backed fake for UltimateWeaponRepository (R4-06: rewritten for upgradePathLevel + per-path level tracking)
fakes/FakeLabRepository.kt                       # In-memory StateFlow-backed fake for LabRepository
fakes/FakeCardRepository.kt                      # In-memory StateFlow-backed fake for CardRepository
fakes/FakeWalkingEncounterRepository.kt          # In-memory StateFlow-backed fake for WalkingEncounterRepository
fakes/FakeStepRepository.kt                      # In-memory StateFlow-backed fake for StepRepository
fakes/FakeCosmeticRepository.kt                  # In-memory fake for CosmeticRepository
fakes/FakeBillingManager.kt                      # Configurable fake for BillingManager (+reconcileCallCount for C.5 PR 2 hook assertion + priceDisplayOverrides Map for Plan 31 PR B getPriceDisplay; private set on counter)
fakes/FakeRewardAdManager.kt                     # Configurable fake for RewardAdManager
fakes/FakeMilestoneDao.kt                        # In-memory fake for MilestoneDao
fakes/FakeDailyMissionDao.kt                     # In-memory fake for DailyMissionDao
fakes/FakeDailyLoginDao.kt                       # In-memory fake for DailyLoginDao
fakes/FakeWeeklyChallengeDao.kt                  # In-memory fake for WeeklyChallengeDao
fakes/FakeDailyStepDao.kt                        # In-memory fake for DailyStepDao with Flow support
fakes/FakeCosmeticDao.kt                         # In-memory fake for CosmeticDao with auto-increment id simulation (C.2 PR 2)
fakes/FakeWorkshopDao.kt                         # In-memory fake for WorkshopDao (issue #19/#20 fix bundle) — supports repo-impl seeding tests; default `purchaseUpgradeAtomic` interface body inherited unchanged so future atomic-purchase tests can opt in by supplying a real PlayerProfileDao
fakes/FakeLabDao.kt                              # In-memory fake for LabDao (issue #19/#20 fix bundle) — supports repo-impl seeding tests on the per-`researchType` primary key
fakes/FakeTimeProvider.kt                        # Mutable TimeProvider with var fixedDate / fixedInstant (B.1)
domain/usecase/CalculateUpgradeCostTest.kt        # Cost formula: baseCost × scaling^level, all 23 types
domain/usecase/CanAffordUpgradeTest.kt            # Affordability checks against wallet
domain/usecase/QuickInvestTest.kt                 # Cheapest affordable upgrade recommendation
domain/usecase/PurchaseUpgradeTest.kt             # Purchase flow with fake repos
domain/usecase/UpdateBestWaveTest.kt              # Best wave tracking, new record detection
domain/usecase/CheckTierUnlockTest.kt             # Tier unlock logic against wave milestones
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
domain/usecase/UpdateCompleteResearchMissionProgressTest.kt # 5 R3-03 tests covering count gating + multi-completion cap-at-target + no-row defensive path. Negative cases (count=0, count=-1) prove the gating; positive cases (count=1, count=5 capped at target=1) prove the additive-with-cap math; the missing-row case asserts idempotency.
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
domain/usecase/DescribeUpgradeEffectTest.kt       # RO-11 #C / RO-10 readout coverage + RO-12 card / precision regressions: 28 tests total — 1 multiplicative test per stat-bearing upgrade (DAMAGE / ATTACK_SPEED / RANGE / HEALTH / HEALTH_REGEN \"2-decimal format\" / KNOCKBACK), 1 additive test per percentage-cap upgrade (CRITICAL_CHANCE / CRITICAL_FACTOR / DEFENSE_PERCENT / DEFENSE_ABSOLUTE / THORN_DAMAGE / LIFESTEAL clamp at 15 % / DAMAGE_PER_METER / DEATH_DEFY clamp at 50 %), 1 discrete test per threshold-crossing upgrade (MULTISHOT / BOUNCE_SHOT / ORBS), 1 utility test per cash-utility upgrade (CASH_BONUS / CASH_PER_WAVE / INTEREST cap 10 % / FREE_UPGRADES cap 25 % \u2014 last two also assert next == null at max level), 2 hidden-but-tested-for-Workshop-reuse (STEP_MULTIPLIER cap 100 % / RECOVERY_PACKAGES cap 50 %), 1 lab-research-stacks (DAMAGE_RESEARCH outer multiplier \u00d7 DAMAGE), 1 smoke test that every UpgradeType produces a non-empty current readout (catches future enum additions falling through to a missing case branch). RO-12 adds 3 net new tests: `RO12 HEALTH_REGEN Lv 0 to Lv 1 produces a visibly different readout` (precision regression direct guard, asserts "1.00/s" → "1.02/s"), `RO12 HEALTH readout reflects equipped WALKING_FORTRESS card multiplier` (Bug 3 regression: ws=5 HEALTH + WALKING_FORTRESS Lv 1 → "1725 HP", verifies card effects are post-applied to mirror the live engine pipeline), `RO12 HEALTH readout with no cards equipped is unchanged from pre-RO12 baseline` (default-arg behaviour preserved for the 25 existing tests).
domain/model/MilestoneTest.kt                     # 6 milestones: thresholds, rewards, sorting
domain/model/DailyMissionTypeTest.kt              # 6 mission types: targets, rewards, categories
domain/model/TierConfigTest.kt                    # All 10 tiers, battle conditions, invalid tier
domain/model/BiomeTest.kt                         # All tier→biome mappings
domain/model/CardLoadoutTest.kt                   # Max 3, no duplicates, add/remove
domain/model/CardTypeTest.kt                      # 31 #53-regression tests covering effectDescriptionAtLevel: 3 named tests per card (Lv1 matches effectLv1 / Lv4 visibly different / Lv7 matches effectLv7) for all 9 cards + 4 cross-card invariants (entry count, no-blank smoke, Lv1≠Lv7 regression guard, Lv4 differs from both ends for the 8 continuous-progression cards). Lv1≠Lv7 invariant directly fails on `main` pre-fix where every level rendered effectLv1 verbatim.
domain/model/UltimateWeaponLoadoutTest.kt         # Max 3, no duplicates, add/remove
domain/model/UpgradeTypeTest.kt                   # 23 entries, category counts, valid configs
domain/model/EnemyTypeTest.kt                     # 6 entries, multiplier correctness
domain/model/BattleConditionEffectsTest.kt        # All tier condition modifiers verified
domain/model/ResearchTypeTest.kt                  # RO-11 #B.2 set-equality contract: only AUTO_UPGRADE_AI + ENEMY_INTEL are flagged `isComingSoon`. Catches both directions of regression — a deferred enum silently flipping back to wired (re-introduces the dead-enum gap), or a wired enum silently getting marked Coming Soon (regresses the UI to suppressed state).
presentation/battle/engine/EnemyScalerTest.kt     # Wave scaling, speed, cash rewards
presentation/battle/engine/WaveSpawnerTest.kt     # RO-11 #B.1 regression guard: `currentWave` reads from the constructor's `startWave` argument so BattleViewModel can map a WAVE_SKIP lab-research level to a higher initial wave (L10 → wave 11). The default `startWave = 1` path is implicitly covered by every existing GameEngineTest — a regression there would surface in unrelated tests. R3-02 updated `onMeleeHit` lambda type to the 2-arg `(EnemyEntity, Double) -> Unit` shape. v15-soak updated `onEnemyFireProjectile` to the 6-arg `(EnemyEntity, Float, Float, Float, Float, Double) -> Unit` shape for ranged thorn damage (#61). V1X-15b +3: `getWaveComposition` early non-boss deterministic, boss wave includes exactly one BOSS, `wavesUntilNextBoss` counts forward from currentWave.
presentation/battle/engine/GameEngineTest.kt      # RO-08 + RO-09 + R3-02 regression guards. RO-08: Overdrive ASSAULT propagates 2× attackSpeed to ziggurat (post-fix the entity holds a `var stats` reference instead of a captured one); FORTRESS propagates 2× healthRegen; expireOverdrive restores baseline interval; updateEffectiveLevels seeds the engine's effective-level lookup; RECOVERY_PACKAGES heal pulse fires at 30s in SPAWNING phase, level 0 / full HP / cap-50% all guarded. RO-09 #1: chrono active slows enemies to ~10% of baseline (per-entity dt scaling, regression-guarded with 0.08..0.12 tolerance band); chrono inactive deterministic across fresh engines; projectiles unaffected (200 px/sec movement preserved). RO-09 #2: 4 fortuneMultiplier-stacking guards covering all 3 production sites — GOLDEN expiry preserves FORTUNE 3.0×, GOLDEN expiry resets to 1.0× when ASSAULT active (regression for the 5.0×-leak-across-non-FORTUNE-overdrive bug), FORTUNE activation does not downgrade GOLDEN's 5.0× via `coerceAtLeast(3.0)`, expireOverdrive preserves GOLDEN's 5.0× via `if (goldenZigActive) 5.0 else 1.0`. R3-02: 3 entries asserting THORN_DAMAGE reflects damage on melee via plumbed attacker reference (50×·20 raw → 10 HP enemy delta), THORN scales linearly with thornPercent (direct value assertions — 0×5==0 ratio check would pass trivially pre-fix), and LIFESTEAL emits visible `+X HP` FloatingText when accumulated heal crosses 1 HP threshold. Helpers: `setChronoActive` / `readFortuneMultiplier` reflection on private fields; `activateGoldenZigForTest` uses public `initUWs`+`activateUW` path; `invokeUpdateUWs` reflectively expires UWs without ticking the overdrive timer or wave spawner; `simulateEnemyMovement` fresh-engine baseline-vs-chrono comparator. R3-02 helpers: `freshEngineWithStats(ResolvedStats)` variant of `freshEngine`; `invokeOnMeleeHit` reads `WaveSpawner.onMeleeHit` field reflectively and calls it directly with a known attacker; `createDummyAttacker` constructs a stationary BASIC enemy at the ziggurat origin; `invokeOnProjectileHitEnemy` reflectively invokes the private `onProjectileHitEnemy(ProjectileEntity, EnemyEntity)`; `readPendingFloatingTextSnippets` pulls `EffectEngine.pendingEffects` reflectively and returns the text content of any `FloatingText` entries. Reflection used to invoke the private `tickRecoveryPackages(Float)`, `expireOverdrive()`, and `updateUWs(Float)` helpers — bypasses full game-loop side effects (enemy spawn, melee hits) for deterministic assertions.
presentation/battle/biome/BiomeThemeTest.kt       # All 5 biome palettes, ziggurat colors, particles
data/sensor/StepRateLimiterTest.kt                # Normal/burst caps, window expiry, edge cases
data/sensor/StepVelocityAnalyzerTest.kt           # Natural/constant/jump patterns, window eviction
data/sensor/StepIngestionPreferencesTest.kt        # Heartbeat write/read, day-start counter, day rollover
data/sensor/StepIngestionTest.kt                   # Worker/service coordination: no double-credit, gap recovery, heartbeat gating
data/sensor/DailyStepManagerTest.kt                 # Widget balance, walking mission progress, activity-minute idempotency
data/healthconnect/StepCrossValidatorTest.kt      # Graduated response levels, offense tracking, escrow
data/healthconnect/ActivityMinuteValidatorTest.kt # Duration/type/micro-session filtering
presentation/battle/effects/ParticlePoolTest.kt   # Acquire/release/recycle/expire/clear/reset
presentation/battle/entities/OrbEntityTest.kt     # 6 #54-regression tests covering radial-oscillation behaviour: inner-sweep hits melee enemy, outer-sweep does not hit, HIT_COOLDOWN gates double-hits within one cooldown window, dead enemies skipped, damage forwarded verbatim from constructor to onHitEnemy callback, full MIN/MAX cycle traversed over ORBIT_PERIOD_SEC. Robolectric + JUnit 4 because OrbEntity initialises an [android.graphics.Paint] field at construction; pattern matches DailyStepDaoTest / GameSurfaceViewTest. Closes the gap that pre-fix had zero tests on the orb damage path despite the path being broken since Plan 10b.
presentation/battle/effects/ScreenShakeTest.kt    # Trigger/decay/override/reset/offset
presentation/battle/effects/DeathEffectTest.kt    # Particle counts per enemy type (6 types)
balance/StepEconomyTest.kt                        # Step economy vs 5 GDD player profiles (multi-week)
balance/CostCurveTest.kt                          # Workshop cost curve validation (standard/premium/ROI)
balance/EnemyScalingTest.kt                       # Enemy HP/damage scaling difficulty ramp
balance/TierProgressionTest.kt                    # Tier unlock timeline vs GDD §14
balance/CashEconomyTest.kt                        # In-round cash flow and interest balance
balance/CardBalanceTest.kt                        # Card tradeoffs and power levels
balance/UWBalanceTest.kt                          # UW cooldowns + damage proportionality (R4-01: renamed from UWOverdriveBalanceTest.kt; 2 overdrive-specific tests dropped, 3 surviving tests cover UW cooldown spacing / Death Wave Lv5 vs wave-50 boss / GOLDEN 5× cash bounding. Future R4-06 will add per-path balance coverage.)
balance/SupplyDropEconomyTest.kt                  # Drop rates and premium currency income
domain/usecase/TrackDailyLoginTest.kt              # Daily login PS/Gem streak awards
domain/usecase/TrackWeeklyChallengeTest.kt         # Weekly step challenge PS awards
presentation/stats/StatsViewModelTest.kt           # Stats VM: profile mapping, bars, period switching
presentation/weapons/UltimateWeaponViewModelTest.kt # UW VM: display, owned/locked, balance
presentation/supplies/UnclaimedSuppliesViewModelTest.kt # Supplies VM: drops, claim, claimAll
presentation/workshop/WorkshopViewModelTest.kt     # Workshop VM: categories, purchase, quick invest
presentation/cards/CardsViewModelTest.kt           # Cards VM: display, equip, upgrade
presentation/labs/LabsViewModelTest.kt             # Labs: start research, unlock slot (use-case level)
presentation/home/HomeViewModelTest.kt             # Home VM: profile mapping, tier, drops, missions. +2 #55 regression tests: `R55 background research completion credits the COMPLETE_RESEARCH daily mission` (positive — expired research + fresh mission row → progress=1, completed=true, level incremented) and `R55 no in-flight research means COMPLETE_RESEARCH mission stays at progress 0 (R3-03 regression guard)` (defensive — confirms #55 fix doesn't re-introduce the R3-03 false trigger).
presentation/battle/BattleViewModelTest.kt         # Battle VM: init stats, biome, overdrive, ads, toggles. RO-12 adds 3 regression tests + `installEngineForPurchase` reflective helper (engine + cash field): `RO12 in-round HEALTH purchase preserves HEALTH_RESEARCH lab bonus` (Bug 1 direct guard, post-purchase maxHealth = BASE × lab-multiplier × in-round-multiplier instead of pre-fix BASE × in-round-only), `RO12 in-round HEALTH purchase preserves WALKING_FORTRESS card bonus` (Bug 2 direct guard, same shape with card multiplier instead of lab), `RO12 in-round HEALTH purchase preserves both lab AND card bonuses stacked` (combined regression matching the screenshot scenario where pre-fix dropped a 1.20 × 1.50 = 1.80× multiplier on every in-round purchase).
presentation/battle/GameSurfaceViewTest.kt         # Robolectric regression suite for [GameSurfaceView] surface lifecycle (R3-01 / GitHub issue #2). 4 tests: surface recreation preserves engine progress mid-round; setSpeedMultiplier persists pendingSpeed for next thread; setPaused persists pendingPaused for next thread; initEngineIfNeeded does run engine init when no progress yet (inverse no-regression guard). JUnit 4 + Robolectric matching DailyStepDaoTest pattern — [GameSurfaceView] extends [android.view.SurfaceView] which can't be exercised by pure-JVM tests.
presentation/missions/MissionsViewModelTest.kt     # Missions: generation, claim, milestones (use-case level)
presentation/economy/CurrencyDashboardViewModelTest.kt # Economy VM: weekly, streak, balances
presentation/store/StoreViewModelTest.kt           # Store VM: gems, cosmetics, purchase
presentation/ux/CurrencyGuardTest.kt               # Currency spend clamps to 0 (gems, PS, dust, steps)
presentation/ux/UserFeedbackTest.kt                # Workshop purchase failure sets userMessage
presentation/DeepLinkRoutingTest.kt                # Deep-link intent extra extraction
data/local/RoomSchemaTest.kt                       # Room v9 schema round-trip (profile, steps, workshop, billing_receipt) — billing_receipt round-trip added in C.5 PR 1 exercises every column
data/local/BillingReceiptDaoTest.kt                # 7 tests for BillingReceiptDao: upsert/get round-trip, grantOnceAtomic flip + runs walletCredit, idempotency (second call returns false + walletCredit skipped), markConsumed/markAcknowledged target-only, getGrantedButUnresolved filter, getAll DESC order (C.5 PR 1)
data/local/DailyStepDaoTest.kt                     # 5 tests for DailyStepDao: regression guard for `incrementBattleSteps succeeds on empty table` (NOT NULL-before-ON-CONFLICT semantic), creditBattleStepsAtomic on empty table credits wallet + populates all Kotlin-default columns, creditBattleStepsAtomic preserves existing sensor data (ON CONFLICT UPDATE branch), partial credit near cap, zero credit when cap exhausted. Robolectric + real in-memory Room DB, mirrors BillingReceiptDaoTest pattern (2026-05-12 hotfix)
data/billing/BillingManagerImplTest.kt             # 14 tests for BillingManagerImpl (C.5 PR 1): 3 happy paths (GEM_PACK_SMALL consume + AD_REMOVAL ack + SEASON_PASS sub with 30-day expiry), 5 failure paths (user-cancel, product-unavailable, no-activity, connect-fails, pending purchase), idempotency (same purchaseToken does not double-credit), 2 reconciliation cases (PENDING→PURCHASED transition + retryUnresolvedConsumeOrAck without re-credit), isAdRemoved / isSeasonPassActive delegation. Robolectric + real in-memory Room DB for @Transaction semantics + mockito-kotlin for adapter.
data/ads/RewardAdManagerImplTest.kt                # 8 tests for RewardAdManagerImpl (C.6 PR 1 / ADR-0006): happy Rewarded; Cancelled on user-dismiss; 4 Error paths (no activity, consent unavailable, load failed with AdMob NO_FILL code 3 → 'No ad available' user msg, show failed with code 1 → 'already shown' user msg); consent-denied-still-grants per Q1; placement → ad-unit routing for all 3 AdPlacements. No Robolectric — plain-Kotlin sealed adapter types + mockito-kotlin.
data/integration/EscrowLifecycleTest.kt            # End-to-end escrow lifecycle (release + discard)
data/repository/CosmeticRepositoryImplTest.kt      # Seed → ZIGGURAT_COLOR_LOOKUP → overrideColors mapping for all 4 palette-shipping cosmetics (zig_jade, lapis_lazuli_skin, garden_ziggurat_skin, sandals_of_gilgamesh) with exact-value assertions as content-as-code contracts; non-palette seeds have null overrideColors; equipped zig_jade surfaces via observeEquipped with palette intact; ensureSeedData idempotent; partial-catalogue upgrade inserts 4 missing milestone cosmetics without touching legacy 7; existing-row isOwned/isEquipped preserved across ensureSeedData (C.2 PR 2 + PR 3 + PR 3b + PR 3c + ensureSeedData fix)
data/repository/WorkshopRepositoryImplTest.kt      # 4 tests for WorkshopRepositoryImpl.ensureUpgradesExist (issue #20 fix): empty-table seeds every UpgradeType; upgrade-from-v8 install adds RAPID_FIRE while preserving historical levels; idempotent on second call; orphan/unknown rows tolerated and not deleted. Mockito-kotlin `mock<PlayerProfileDao>()` to satisfy constructor signature; ensureUpgradesExist never invokes player DAO methods.
data/repository/LabRepositoryImplTest.kt           # 3 tests for LabRepositoryImpl.ensureResearchExists (issue #20 sibling fix): empty-table seeds every ResearchType; upgrade-from-v8 install adds MULTISHOT_RESEARCH + BOUNCE_RESEARCH while preserving historical levels AND active-research timestamps (startedAt / completesAt); idempotent on second call.
data/repository/PlayerRepositoryImplTest.kt        # 13 tests for PlayerRepositoryImpl (V1X-07): wallet/profile/tier observe mappings, addGems/addPowerStones multi-call delegation, V1X-10 atomic spend regression guards (spendGems → spendGemsAtomic only, never adjustGems + incrementGemsSpent fallback), updateBestWave read-modify-write merging, ensureProfileExists conditional upsert, getStepBalance null-safe.
data/repository/UltimateWeaponRepositoryImplTest.kt # 12 tests for UltimateWeaponRepositoryImpl (V1X-07): observeUnlockedWeapons isUnlocked filter (R4-06 contract), observeEquippedWeapons defensive isUnlocked + isEquipped intersection, unlockWeapon insert-vs-markUnlocked branching, R4-06 per-path upgrade dispatch (DAMAGE → updateDamageLevel; SECONDARY → updateSecondaryLevel; COOLDOWN → updateCooldownLevel), defensive insert when row missing, equip/unequip isEquipped flag flips with no-op when entity missing.
data/repository/CardRepositoryImplTest.kt          # 16 tests for CardRepositoryImpl (V1X-07): observeAllCards/observeEquippedCards mappings, hasCard true/false, addCard insert returns id, R4-08 addCardOrIncrementCopy increment-vs-insert branching, decrementCopiesAndLevelUp returns Boolean from DAO row count, upgradeCard / equipCard / unequipCard / deleteCard with no-op-when-missing guards.
data/repository/StepRepositoryImplTest.kt          # 8 tests for StepRepositoryImpl (V1X-07): observeTodayRecord mapping + null emission, getDailyRecord round-trip, updateDailySteps create-vs-update preserving unrelated fields (healthConnectSteps, activityMinutes), updateEscrow merging escrow fields onto existing record, releaseEscrow / discardEscrow delegation to DAO clearEscrow.
data/DataDeletionManagerTest.kt                    # 4 Robolectric tests for DataDeletionManager (V1X-01): clears all 11 SharedPreferences (biome / milestone-notification / notification / sound / music / anti-cheat / step-ingestion / db-key / widget-data / smart-reminders / billing-anti-fraud); closes the in-memory Room DB; calls activity.recreate() exactly once; idempotent across repeated invocations. Uses `WorkManagerTestInitHelper.initializeTestWorkManager` so `WorkManager.cancelAllWork()` doesn't throw on the first call. New test dep: `androidx.work:work-testing`.
domain/battle/engine/SimulationMathTest.kt         # 27 pure-JVM tests for SimulationMath (V1X-09 Phase 1, ADR-0012; V1X-18 ADR-0015 added 8 stepMultiplierBonus tests). Recovery pulse formula at L0/L1/L25/L60/min-1; chrono active/inactive/custom-factor; thorn reflection edge cases (zero rawDamage / zero thornPercent / condition multiplier); lifesteal heal cap (15 %) + sub-1 fraction conservation; lifesteal accumulator threshold-crossing (no-burst < 1 / 1-HP burst / multi-HP burst / 50-hit accumulation); HP clamp (over-heal / negative / passthrough / zero maxHp / negative maxHp). V1X-18 stepMultiplierBonus L0/L1/L10/L20/L50/L100/L200 + L99-vs-L100 dead-content regression guard.
domain/battle/engine/SimulationTest.kt             # 35 pure-JVM tests for Simulation (V1X-09 Phase 3, ADR-0012, slices 1–6). Slice 1 cash economy (9): creditCash accumulates both balances + sums + ignores non-positive; applyInterest adds to cash only (not lifetime) + caps at 10 % + level-0 no-op; spend deducts when affordable + rejects/leaves balance untouched when insufficient; reset zeroes both. Slice 2 round-progress (7): tickElapsed accumulates the clock; recordEnemyKilled increments; creditSteps accumulates + ignores non-positive; hasWaveProgress false-on-fresh / true-after-tick / true-after-kill; reset zeroes the counters. Slice 3 tickEntities (4): scales dt for a chrono-slowable fake, full dt for a non-slowable fake, factor-1 no-op, mixed-list all-ticked-once (private FakeEntity impl of EntityProtocol). Slice 4 collision sweep (6): detectProjectileEnemyHits fires the pair on overlap / stops at first overlapping enemy (break) / no-fire when nothing overlaps / interleaved-firing guard (onHit moves the enemy mid-sweep so a later projectile misses); detectZigguratHits fires on overlap / no-fire outside the radius. Slice 5 UW lifecycle (7): advanceUWTimers cooldown decrement+clamp-at-0 / zero-cooldown+inactive-effect untouched / active-effect countdown without expiry / justExpired+clamp on the crossing frame with effectWasActive still true; isUWReadyToFire true off-cooldown-and-idle / false on cooldown / false mid-effect. Slice 6 events stream (2): emit delivers both event types (StepReward + BossKilled) to a collector in order; replay=0 no-replay contract (a late subscriber sees nothing — guards the playAgain collector against double-crediting the previous round). No Robolectric.
domain/battle/entity/ProjectileStateTest.kt        # 4 pure-JVM tests for ProjectileState (V1X-09 Phase 2, ADR-0012): single-axis proportional move, two-axis proportional split, arrival-within-one-step death, multi-step convergence. No Robolectric — the motion math is now pure-domain.
domain/battle/entity/OrbStateTest.kt               # 4 pure-JVM tests for OrbState (V1X-09 Phase 2, ADR-0012): mid-radius + on-orbit position at phase 0, MAX after quarter-cycle, MIN after three-quarter-cycle, angularSpeed advances the orbit angle. No Robolectric (the OrbEntity Robolectric suite still covers the enemy-proximity/hit-cooldown path).
domain/battle/entity/EnemyStateTest.kt             # 5 pure-JVM tests for EnemyState (V1X-09 Phase 2, ADR-0012): melee proportional move (no attack), fire-on-arrival when in range, attack-cooldown gating across the interval (true/false/true), ranged stops + attacks farther out than melee range (initialDist*0.4 = 80 > 40), knockback shift. No Robolectric.
domain/battle/entity/ZigguratStateTest.kt          # 6 pure-JVM tests for ZigguratState (V1X-09 Phase 2, ADR-0012, final entity): regen below cap, regen clamp at maxHp, attack-cooldown gating across the interval (true/onFired/false/true), holdReady keeps ready next tick, attackInterval reflects attackSpeed × rapidFireMultiplier, updateStats redirects attackRange + attackInterval. No Robolectric.
presentation/audio/SoundManagerThrottleTest.kt     # 5 pure-logic tests for the V1X-05 frequency-aware SHOOT throttle formula `(expectedIntervalMs / 3).coerceIn(30, 100)`. Default 100ms input → 33ms throttle; baseline 1000ms (1 atk/sec) caps at 100ms; fast 200ms (5 atk/sec) → 66ms; very-fast 60ms floors at 30ms; zero floors at 30ms. Pure JVM, no Robolectric needed.
presentation/audio/MusicPreferencesTest.kt         # 1 Robolectric test for MusicPreferences round-trip (V1X-06): default isMuted=false + getVolume=0.5; setMuted(true) + setVolume(0.8) round-trip persists across SharedPreferences read.
service/StepWidgetProviderTest.kt                  # Widget SharedPreferences round-trip
```

### Instrumented (androidTest) source set

All paths relative to `app/src/androidTest/java/com/whitefang/stepsofbabylon/`. Stood up by V1X-08 Phase 1A on 2026-05-28.

```
HiltTestRunner.kt                  # AndroidJUnitRunner subclass; overrides newApplication() to install HiltTestApplication. Wired via testInstrumentationRunner in app/build.gradle.kts. The class FQN com.whitefang.stepsofbabylon.HiltTestRunner is the contract between Gradle config and runtime instantiation — don't rename without updating both call sites.
InfrastructureSmokeTest.kt         # Single @HiltAndroidTest harnessBoots smoke test asserting the instrumented pipeline (Gradle config → KSP → Hilt → AndroidJUnit4 → emulator) runs end-to-end. Deliberately does not touch DB / services / sensors / permissions — those belong to the three follow-up suites (BattleSurfaceLifecycleTest, StoreIapFlowTest, DeepLinkIntentTest).
BattleSurfaceLifecycleTest.kt      # First real instrumented suite (V1X-08, layered 2026-05-29). 4 @RunWith(AndroidJUnit4) tests mirroring the R3-01 backgrounding-state-loss invariants from the Robolectric GameSurfaceViewTest, but against the REAL Android framework: surface-recreation preserves engine progress (initEngineIfNeeded gated on hasWaveProgress), setSpeedMultiplier persists pendingSpeed, setPaused persists pendingPaused, fresh-engine init still runs (no-regression). Constructs GameSurfaceView on the main thread via InstrumentationRegistry + runOnMainSync. No HiltAndroidRule (GameSurfaceView takes only Context). No new gradle deps — androidx.test.runner + androidx.test.ext.junit already present from Phase 1A. Hardens the Robolectric guard, which can't exercise true SurfaceHolder.Callback lifecycle timing.
DeepLinkIntentTest.kt              # Second real instrumented suite (V1X-08, layered 2026-05-29). 4 @RunWith(AndroidJUnit4) tests verifying the `navigate_to` deep-link contract MainActivity reads (intent.getStringExtra("navigate_to") → Screen.fromRoute → argumentFreeRoutes gate) against the REAL framework: (1) the extra survives a real android.os.Parcel round-trip — the instrumented-only value over the Robolectric DeepLinkRoutingTest, since notification deep-links travel inside a PendingIntent that marshals through Binder/Parcel; (2) missing extra is null after round-trip; (3) every argumentFreeRoute resolves + passes the routing gate; (4) unknown route fails closed. No Hilt rule (touches only Intent / Parcel / pure-Kotlin Screen). No new gradle deps.
```
