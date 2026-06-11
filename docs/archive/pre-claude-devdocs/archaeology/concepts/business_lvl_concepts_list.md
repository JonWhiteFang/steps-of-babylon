# Business-Level Concepts

*Archaeology Phase 5 — product-level concepts: value propositions, hard
commercial/design invariants, monetization, progression systems, and the
currencies/economies that tie them together. Each entry: ≤3 sentences,
implementation status, file pointers. Central → branching.*

---

## 1. Product positioning

### Walk-to-play fitness tower defence
Real-world walking is the sole source of the primary currency (Steps).
Every progression mechanic — upgrades, research, unlocks, new biomes — is
gated on physical activity. Competitive differentiator: fitness is
mechanically required, not cosmetic.
**Implementation status:** Fully.
**Files:** `docs/StepsOfBabylonApp_GDD.md`, `data/sensor/DailyStepManager.kt`,
`docs/release/play-store-listing.md`.

### Single-player, offline, no-backend product
Targets solo mobile gamers who value privacy and play opportunistically
through the day. No multiplayer, no server backend, no account creation —
so no backend ops cost for v1.0.
**Implementation status:** Fully.
**Files:** `README.md`, `docs/release/privacy-policy.md`, absence of any
network client.

### Android 14+ production release (Google Play, v1.0.0)
Min SDK 34, target SDK 36, single AAB shipping through Google Play. Play
Console setup + real SDK integration + store listing assets are the
gating Plan 31 work.
**Implementation status:** Partial — release build is clean and signed-opt-in;
store publication pending.
**Files:** `app/build.gradle.kts`, `docs/release/release-checklist.md`,
`docs/plans/plan-31-play-console.md`.

### "Play a little, often" session shape
Intended session is ~5 minutes of check-in during the day (spend Steps,
claim supplies, try a round) rather than long sittings. Battle rounds are
~20 minutes at 1× speed, reduced to ~5 at 4× speed.
**Implementation status:** Fully.
**Files:** `presentation/battle/GameLoopThread.kt` (speed control),
`docs/StepsOfBabylonApp_GDD.md` §3.

---

## 2. Hard design invariants (non-negotiable)

### Steps are never generated passively in-game
This is the product's foundational rule: all Steps come from the Android
step counter or Health Connect exercise-minute conversion. Stated in
`START_HERE.md` and `CONSTRAINTS.md`; enforced by absence of any code path
that adds Steps without passing through `DailyStepManager`.
**Implementation status:** Fully.
**Files:** `docs/agent/START_HERE.md`, `docs/agent/CONSTRAINTS.md`,
`data/sensor/DailyStepManager.kt`.

### Steps are never purchasable with real money
No IAP SKU produces Steps. Gems (paid currency) cannot be converted to
Steps either. Constraint listed in both `START_HERE.md` and `CONSTRAINTS.md`.
**Implementation status:** Fully.
**Files:** `domain/model/BillingProduct.kt` (note absence of any Step SKU),
`data/billing/StubBillingManager.kt`.

### 50 000 Steps/day walking ceiling
`DailyStepManager.DAILY_CEILING = 50_000L`. Combined with rate limit (200/min)
and velocity analysis, defines the anti-cheat upper bound for walking
credits per calendar day.
**Implementation status:** Fully.
**Files:** `data/sensor/DailyStepManager.kt`, `docs/agent/CONSTRAINTS.md`.

### 2 000 battle-Steps/day cap (ADR-0003)
Steps earned from enemy kills are separate from the walking ceiling and
capped at 2 000/day via `AwardBattleSteps.DAILY_BATTLE_STEP_CAP`. Flat
per-enemy-type reward — explicitly **not** multiplied by Fortune / Cash
Bonus / Golden Ziggurat.
**Implementation status:** Fully.
**Files:** `domain/usecase/AwardBattleSteps.kt`,
`docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`.

### Monetization is convenience / cosmetic only
Five SKUs: 3 Gem packs (50/300/700 Gems at $0.99/$4.99/$9.99), Ad Removal
($3.99 one-time), Season Pass ($4.99/month for +10 Gems daily and one free
Lab rush/day). No pay-to-win lever.
**Implementation status:** Fully (stub SDK path); real billing integration
deferred to Plan 31.
**Files:** `domain/model/BillingProduct.kt`, `presentation/store/StoreScreen.kt`,
`data/billing/StubBillingManager.kt`.

---

## 3. Currency economy

### Steps (primary permanent currency)
Earned only from walking/exercise. Spent on Workshop upgrades, Lab research,
Step Overdrive activations. Balance persists forever.
**Implementation status:** Fully.
**Files:** `domain/model/Currency.kt`, `domain/model/PlayerWallet.kt`,
`data/local/PlayerProfileEntity.kt` (`currentStepBalance`).

### Cash (per-round volatile currency)
Earned from enemy kills and wave completion inside a battle. Used for
in-round upgrades and UW activations that cost cash. Resets on round
start/end.
**Implementation status:** Fully.
**Files:** `presentation/battle/engine/GameEngine.kt` (`cash` @Volatile),
`presentation/battle/ui/InRoundUpgradeMenu.kt`.

### Gems (soft premium currency)
Earned from milestones, daily login, weekly challenges, supply drops, and
optionally purchased. Spent on Card Packs, Lab rushes, Lab slot unlocks,
cosmetics, ad removal.
**Implementation status:** Fully.
**Files:** `domain/model/PlayerWallet.kt`, `domain/usecase/TrackDailyLogin.kt`,
`presentation/store/StoreScreen.kt`.

### Power Stones (hard premium currency)
Earned from weekly challenges, wave milestones, and supply drops. Spent
exclusively on Ultimate Weapon unlock/upgrade. Not purchasable with real
money.
**Implementation status:** Fully.
**Files:** `domain/usecase/AwardWaveMilestone.kt`,
`domain/usecase/TrackWeeklyChallenge.kt`,
`domain/usecase/UnlockUltimateWeapon.kt`.

### Card Dust (crafting material)
Earned from opening duplicates (5/15/50 dust per Common/Rare/Epic duplicate)
and supply drops. Spent on upgrading owned cards by rarity × level.
**Implementation status:** Fully.
**Files:** `domain/model/CardRarity.kt`, `domain/usecase/OpenCardPack.kt`,
`domain/usecase/UpgradeCard.kt`.

---

## 4. Progression systems (what Steps buy)

### Workshop — 23 permanent upgrades paid in Steps
Three categories (Attack 8, Defense 9, Utility 6). Geometric cost curve
(1.11–1.35 scaling). Every upgrade has a human-readable `effectPerLevel`
description. STEP_MULTIPLIER and RECOVERY_PACKAGES are currently hidden
from the Workshop UI (R04).
**Implementation status:** Fully, with 2 hidden entries.
**Files:** `domain/model/UpgradeType.kt`, `domain/model/UpgradeConfig.kt`,
`presentation/workshop/WorkshopScreen.kt`.

### Labs — 10 research projects, time-gated
Each project has per-type `baseCostSteps`, `baseTimeHours`, `maxLevel`
(3–20), and an `effectPerLevel`. Multiple projects run concurrently in 1–4
slots; 200 Gems to unlock each extra slot; Gem rush costs 50–200 Gems
(linear).
**Implementation status:** Fully.
**Files:** `domain/model/ResearchType.kt`, `domain/usecase/StartResearch.kt`,
`domain/usecase/RushResearch.kt`, `domain/usecase/UnlockLabSlot.kt`.

### Cards — 9 types × 3 rarities, Gem-bought packs
Pack tiers: Common (50 Gems, 80/18/2), Rare (150 Gems, 50/40/10), Epic
(500 Gems, 20/40/40). Equip max 3; level 1–5; linear stat interpolation.
Duplicates recycle to Card Dust.
**Implementation status:** Fully.
**Files:** `domain/model/CardType.kt`, `domain/model/CardRarity.kt`,
`domain/usecase/OpenCardPack.kt`, `domain/usecase/UpgradeCard.kt`.

### Ultimate Weapons — 6 types, Power Stone unlock + upgrade
`DEATH_WAVE` (50 PS), `CHRONO_FIELD`/`CHAIN_LIGHTNING` (75 PS),
`GOLDEN_ZIGGURAT` (80 PS), `POISON_SWAMP` (60 PS), `BLACK_HOLE` (100 PS).
Upgrade cost = unlockCost × 2 × level, max level 10, cooldown −5%/level.
Equip max 3.
**Implementation status:** Fully.
**Files:** `domain/model/UltimateWeaponType.kt`,
`domain/model/UltimateWeaponLoadout.kt`,
`domain/usecase/UnlockUltimateWeapon.kt`, `domain/usecase/UpgradeUltimateWeapon.kt`.

### Step Overdrive — 4 mid-battle buffs, once per round
`ASSAULT` (500 Steps, 2× atk speed + 1.5× damage), `FORTRESS` (500, 2× regen
+ 50% damage reduction), `FORTUNE` (300, 3× cash), `SURGE` (750, reset UW
cooldowns). 60-second duration.
**Implementation status:** Fully.
**Files:** `domain/model/OverdriveType.kt`, `domain/usecase/ActivateOverdrive.kt`.

### Tiers — 10 difficulty levels with cash multipliers
Tier 1 = 1.0× cash, Tier 10 = 10.0× cash. Unlock requirements start at
wave 50 (tier 2) and rise to wave 150 (tier 10). Each player has a
`currentTier` (which to play) and `highestUnlockedTier` (gate).
**Implementation status:** Fully.
**Files:** `domain/model/TierConfig.kt`, `domain/model/Tier.kt`,
`domain/usecase/CheckTierUnlock.kt`.

### Battle Conditions at Tier 6+
Seven conditions (`ENEMY_SPEED`, `ENEMY_ATTACK_SPEED`, `ORB_RESISTANCE`,
`KNOCKBACK_RESISTANCE`, `ARMORED_ENEMIES`, `THORN_RESISTANCE`, `MORE_BOSSES`)
layer on from Tier 6; Tier 10 applies all seven simultaneously.
**Implementation status:** Fully.
**Files:** `domain/model/BattleCondition.kt`, `domain/model/TierConfig.kt`,
`domain/model/BattleConditionEffects.kt`.

### Biomes — 5 narrative environments
Hanging Gardens (T1–3) → Burning Sands (T4–6) → Frozen Ziggurats (T7–8)
→ Underworld of Kur (T9–10) → Celestial Gate (T11+). Each supplies a
palette + enemy tint + particle colour; first-time entry shows a cinematic
overlay.
**Implementation status:** Fully.
**Files:** `domain/model/Biome.kt`, `presentation/battle/biome/BiomeTheme.kt`,
`presentation/battle/ui/BiomeTransitionOverlay.kt`.

### Enemy roster — 6 archetypes
`BASIC` (1× speed/HP/dmg), `FAST` (2× speed, 0.5× HP), `TANK` (0.5× speed,
5× HP, 2× damage), `RANGED` (fires projectiles), `BOSS` (20× HP, 3× dmg,
every 10 waves), `SCATTER` (splits into 2–3 BASIC on death).
**Implementation status:** Fully.
**Files:** `domain/model/EnemyType.kt`,
`presentation/battle/entities/EnemyEntity.kt`.

---

## 5. Engagement & retention economy

### Daily missions (3 per day, midnight refresh)
One per category from `WALKING` / `BATTLE` / `UPGRADE`. Deterministically
selected by `Random(todayDate.hashCode())` so every device sees the same
trio on the same day. Progress auto-updates; reward on claim.
**Implementation status:** Fully.
**Files:** `domain/model/DailyMissionType.kt`,
`domain/usecase/GenerateDailyMissions.kt`,
`presentation/missions/MissionsScreen.kt`.

### Walking milestones — 6 lifetime tiers
1k → 10k → 100k → 500k → 1M → 5M total Steps. Rewards are Gems (60/25/200/
600/200/500) plus Power Stones (50 at the 1M mark) and 3 cosmetics gated
behind specific milestones.
**Implementation status:** Fully; cosmetic rewards store ownership but have
no visual effect yet.
**Files:** `domain/model/Milestone.kt`, `domain/model/MilestoneReward.kt`,
`domain/usecase/CheckMilestones.kt`, `domain/usecase/ClaimMilestone.kt`.

### Weekly step challenge
Rolling 7-day step sum awards Power Stones at 50k (10 PS), 75k (+10 = 20
total), 100k (+15 = 35 total). Only delta PS awarded per tier.
**Implementation status:** Fully.
**Files:** `domain/usecase/TrackWeeklyChallenge.kt`,
`data/local/WeeklyChallengeEntity.kt`.

### Daily login streak (7-day Gem cycle)
Logging in after 1 000+ steps awards 1 PS and `min(streak, 5)` Gems for the
day; missed day resets streak to 1. Streak cycles after day 7. Season Pass
adds +10 Gems.
**Implementation status:** Fully.
**Files:** `domain/usecase/TrackDailyLogin.kt`, `data/local/DailyLoginEntity.kt`.

### Wave milestones (battle-side PS rewards)
Reaching a new personal-best wave grants 1 PS; waves %10 grant 2 PS; waves
%25 grant 5 PS. Single payout per new record.
**Implementation status:** Fully.
**Files:** `domain/usecase/AwardWaveMilestone.kt`,
`presentation/battle/BattleViewModel.kt` (`endRound`).

### Walking Encounters / Supply Drops
Seeded random drops generated during step crediting, delivered as push
notifications and queued in an Unclaimed Supplies inbox (cap 10, oldest
discarded on overflow). Four triggers × four reward types.
**Implementation status:** Fully — `STEP_BURST` trigger enum exists but
burst detection is not wired.
**Files:** `domain/model/SupplyDropTrigger.kt`, `domain/model/SupplyDropReward.kt`,
`domain/usecase/GenerateSupplyDrop.kt`, `domain/usecase/ClaimSupplyDrop.kt`,
`presentation/supplies/UnclaimedSuppliesScreen.kt`.

### Activity Minute Parity (inclusive fitness conversion)
Health Connect exercise sessions (cycling, swimming, yoga, etc.) convert to
Step-equivalents via a per-activity table with micro-session filtering (<2
min) and hard caps. Intended audience: non-ambulatory users, indoor
workouts.
**Implementation status:** Fully.
**Files:** `data/healthconnect/ActivityMinuteConverter.kt`,
`data/healthconnect/ActivityMinuteValidator.kt`,
`data/healthconnect/ExerciseSessionReader.kt`.

---

## 6. Player-trust contracts

### Four-level graduated anti-cheat response
Cross-validation between sensor and Health Connect with escalating
consequences: escrow → faster discard → cap at HC → cap minus 10%.
Communicated to players implicitly via slower credit, never via punitive
messaging. Offenses decay over 7 quiet days.
**Implementation status:** Fully.
**Files:** `data/healthconnect/StepCrossValidator.kt`,
`data/anticheat/AntiCheatPreferences.kt`.

### Privacy-by-default
No analytics, no crash reporting, no network calls. Step data leaves the
device only via user-initiated Health Connect operations. Privacy policy
documents this explicitly.
**Implementation status:** Fully.
**Files:** `docs/release/privacy-policy.md`,
`presentation/HealthConnectPermissionActivity.kt`.

### Encrypted local data at rest
SQLCipher encrypts the database; passphrase lives in Android Keystore. No
data export/import flow means nothing the app stores is readable outside
it.
**Implementation status:** Fully.
**Files:** `data/local/DatabaseKeyManager.kt`, `di/DatabaseModule.kt`.

### UX-feedback contract
Every failed action surfaces a snackbar (`userMessage`), and double-tap
races are prevented (`isProcessing`). Silent failure is considered a bug
(R10 findings).
**Implementation status:** Fully for Workshop/Cards/Labs/Store; Battle has
its own in-HUD feedback.
**Files:** `presentation/{workshop,cards,labs,store}/*ViewModel.kt` +
`*UiState.kt`.

### Accessibility minimum (baseline)
Core controls carry `contentDescription` for TalkBack; system
`ANIMATOR_DURATION_SCALE=0` disables shakes/trails; Activity Minute Parity
serves users who can't walk. Full accessibility pass (Plan 24) is
post-v1.0.
**Implementation status:** Partial (baseline only).
**Files:** `presentation/battle/BattleScreen.kt`,
`presentation/battle/effects/ReducedMotionCheck.kt`,
`data/healthconnect/ActivityMinuteConverter.kt`.

---

## 7. Release & distribution

### Version 1.0.0, versionCode 1 — first Play Store cut
One signed AAB through Google Play, UK publisher Whitefang Games. Release
checklist is written; actual keystore generation + Play Console setup are
manual Plan 31 steps.
**Implementation status:** Partial — build pipeline ready; publication
pending.
**Files:** `app/build.gradle.kts`, `docs/release/release-checklist.md`,
`docs/release/play-store-listing.md`.

### Signing via opt-in `keystore.properties`
Debug builds work without a keystore; release signing only activates when
the gitignored `keystore.properties` exists. `docs/release/signing-guide.md`
covers manual generation and Play App Signing enrolment.
**Implementation status:** Fully (opt-in).
**Files:** `app/build.gradle.kts`, `.gitignore`, `docs/release/signing-guide.md`.

### Store listing copy + privacy policy (drafted)
Short + full descriptions, content rating notes, screenshot/feature-graphic
requirements listed. Privacy policy includes real contact email. URL
hosting for the live policy is a Plan 31 manual task.
**Implementation status:** Partial — text drafted, hosting pending.
**Files:** `docs/release/play-store-listing.md`,
`docs/release/privacy-policy.md`.

### Release gate: 412 passing tests + clean R8 build
CI is absent; the manual gate is `./run-gradle.sh test` (all 412 green) plus
`assembleRelease` succeeding (26 MB APK, R8 clean). Balance regression
(`balance/*Test.kt`) is part of this gate.
**Implementation status:** Fully (manual).
**Files:** `app/src/test/java/...`, `docs/release/release-checklist.md`.

### Explicitly out of scope for v1.0
No multiplayer, no cloud save, no leaderboards, no Firebase, no analytics.
Cosmetic visual application, real billing SDK, real ad SDK, and
accessibility (Plan 24) are tracked for post-v1.0.
**Implementation status:** Explicitly out of scope.
**Files:** `docs/StepsOfBabylonApp_GDD.md` §1 (scope statement),
`docs/agent/STATE.md`.
