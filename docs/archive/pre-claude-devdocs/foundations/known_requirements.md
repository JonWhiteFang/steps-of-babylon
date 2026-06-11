# Known Requirements (Doc-Inferred)

> Scope: requirements that are **explicitly stated** in the repository's
> non-code documentation. When a requirement is implied or left vague by the
> docs it is recorded that way rather than strengthened with code inference.
> Citations use `file §section` style.

---

## 1. Functional requirements

### 1.1 Step ingestion

- R-STEP-01 The app **shall** count steps using the Android
  `TYPE_STEP_COUNTER` hardware sensor as the primary source.
  *(GDD §11.1, `docs/step-tracking.md`)*
- R-STEP-02 The app **shall** cross-validate and gap-fill step data against
  Health Connect. *(GDD §11.1, `docs/step-tracking.md`, ADR-0002)*
- R-STEP-03 Step counting **shall** continue when the app is backgrounded
  or killed, via a foreground service of type "health".
  *(GDD §11.2, `docs/step-tracking.md`, `CONSTRAINTS.md`)*
- R-STEP-04 The foreground service **shall** be restarted on device boot via
  a `BOOT_COMPLETED` receiver. *(GDD §11.2, `docs/step-tracking.md`)*
- R-STEP-05 A WorkManager periodic worker **shall** run every 15 minutes
  to reconcile local counts with Health Connect and recover gaps when the
  foreground service is killed. *(`docs/step-tracking.md`)*
- R-STEP-06 The foreground service and the periodic worker **shall** share
  an authoritative baseline and a heartbeat so the same sensor delta is
  never credited twice. *(`docs/plans/plan-R-remediation.md` R01,
  `docs/step-tracking.md` "Service ↔ Worker Coordination")*
- R-STEP-07 Activity-minute crediting **shall** be delta-based and
  idempotent across repeated worker runs.
  *(`docs/plans/plan-R2-remediation.md` R2-01)*
- R-STEP-08 Activity-minute crediting **shall** fan out to the same
  follow-on pipeline as sensor-step crediting (widget update, supply-drop
  generation, daily login/weekly challenge tracking, walking mission
  progress). *(`docs/plans/plan-R2-remediation.md` R2-02)*
- R-STEP-09 The app **shall** request battery-optimization whitelisting on
  first launch. *(GDD §11.2, `docs/step-tracking.md`)*

### 1.2 Anti-cheat

- R-AC-01 Credited steps **shall** be capped at 200 per minute, with a
  short-term burst up to 250 for running. *(GDD §11.3,
  `docs/step-tracking.md`, `CONSTRAINTS.md`)*
- R-AC-02 Daily credited steps **shall not** exceed 50,000 per day.
  *(GDD §11.3, `docs/step-tracking.md`, `CONSTRAINTS.md`)*
- R-AC-03 A step-velocity analyser **shall** detect phone shakers (constant
  rate, CV < 5%) and spoofers (instant jump from <20 to >150 steps/min)
  and apply a 0.5× or 0.0× penalty multiplier. *(GDD §11.3,
  `docs/step-tracking.md`)*
- R-AC-04 The app **shall** implement a 4-level graduated response when
  Health Connect cross-validation flags a discrepancy >20%: Level 0
  (escrow with 3-sync release), Level 1 (escrow with 2-sync discard),
  Level 2 (cap at HC), Level 3 (cap at HC −10%).
  *(`docs/step-tracking.md`, `CONSTRAINTS.md`)*
- R-AC-05 Escrow **shall** deduct steps from the player balance
  immediately on flag and release them only on reconciliation, so that
  discarded escrows remain deducted. *(`docs/plans/plan-R-remediation.md` R02)*
- R-AC-06 Activity-minute sessions <2 minutes **shall** be discarded, >4
  hours **shall** be truncated, and more than 5 distinct activity types
  per day **shall** be rejected beyond the 5th. *(GDD §11.3,
  `docs/step-tracking.md`)*
- R-AC-07 Per-minute overlap deduction **shall** prevent double-counting
  between sensor steps and activity minutes (credit sensor when the
  sensor records ≥50 steps/min during that minute).
  *(GDD §11.3, `docs/step-tracking.md`)*
- R-AC-08 Battle Step rewards **shall** be capped at 2,000 per day and
  **shall not** be multiplied by Fortune Overdrive, Cash Bonus, or Golden
  Ziggurat UW. *(ADR-0003, `CONSTRAINTS.md`)*

### 1.3 Economy

- R-ECO-01 Four permanent currencies exist (Steps, Gems, Power Stones,
  Card Dust) and one round-local currency (Cash). *(GDD §3,
  `.kiro/steering/product.md`)*
- R-ECO-02 Steps **shall** be acquired only from walking, from Activity
  Minute Parity credits, and (per ADR-0003) from a small per-kill
  in-battle reward capped at 2,000/day. *(GDD §3, ADR-0003)*
- R-ECO-03 Steps **shall not** be acquirable through real-money purchase.
  *(GDD §13, `docs/monetization.md`, `CONSTRAINTS.md`)*
- R-ECO-04 Cash **shall** reset at the end of every round. *(GDD §5,
  `.kiro/steering/product.md`)*
- R-ECO-05 Weekly step challenges **shall** award Power Stones at 50k
  (10 PS), 75k (20 PS) and 100k (35 PS) weekly thresholds. *(GDD §7.1)*
- R-ECO-06 Daily login + 1,000-step activity **shall** award 1 Power Stone
  per day. *(GDD §7.1)*
- R-ECO-07 Wave personal-best milestones **shall** award 2–5 Power Stones.
  *(GDD §7.1)*
- R-ECO-08 Walking milestones (6 tiers: First Steps 1k, Morning Jogger 10k,
  Trail Blazer 100k, Marathon Walker 500k, Iron Soles 1M, Globe Trotter 5M)
  **shall** award the Gem / Power Stone / cosmetic rewards listed in
  GDD §16.1.
- R-ECO-09 Three daily missions **shall** be generated deterministically
  each day (Walking, Battle, Upgrade categories) and refresh at midnight.
  *(GDD §16.2)*

### 1.4 Workshop upgrades

- R-WS-01 The Workshop **shall** contain 23 upgrade types across Attack
  (8), Defense (9), and Utility (6) branches. *(GDD §4, Plan 01)*
- R-WS-02 Upgrade cost **shall** follow `cost = ceil(baseCost × scaling ^
  level)`. *(GDD §4, `docs/battle-formulas.md`, `CONSTRAINTS.md`)*
- R-WS-03 Upgrades with a documented `maxLevel` (e.g. CRITICAL_CHANCE cap
  80%, RANGE cap 300%, MULTISHOT cap 5 targets, BOUNCE_SHOT cap 4,
  DEFENSE_PERCENT cap 75%, LIFESTEAL cap 15%, ORBS cap 6, DEATH_DEFY cap
  50%, INTEREST cap 10%, FREE_UPGRADES cap 25%, STEP_MULTIPLIER cap 100%)
  **shall** stop accepting purchases once the cap is reached. *(GDD §4)*
- R-WS-04 `STEP_MULTIPLIER` and `RECOVERY_PACKAGES` **shall** remain hidden
  from the Workshop UI until gameplay support exists.
  *(`docs/plans/plan-R-remediation.md` R04, `docs/battle-formulas.md` note)*
- R-WS-05 A "Quick Invest" button **shall** recommend the cheapest
  currently-affordable upgrade. *(GDD §12.1, Plan 07)*

### 1.5 Battle system

- R-BAT-01 Battles **shall** use a custom `SurfaceView` renderer running
  on a dedicated thread with a fixed-timestep game loop (60 UPS).
  *(`docs/architecture.md`, GDD §15, `AGENTS.md`)*
- R-BAT-02 Wave timing **shall** be 26 s spawn phase + 9 s cooldown at 1×
  speed. *(GDD §2.2, `docs/battle-formulas.md`)*
- R-BAT-03 The player **shall** be able to toggle speed 1× / 2× / 4×.
  *(GDD §12.1, `docs/battle-formulas.md`)*
- R-BAT-04 Six enemy types **shall** exist (Basic, Fast, Tank, Ranged,
  Boss, Scatter) with speed/health/damage multipliers listed in GDD §10
  and `docs/battle-formulas.md`.
- R-BAT-05 Bosses **shall** spawn every 10 waves, or every 7 waves when
  the `MORE_BOSSES` battle condition is active (Tier 9+).
- R-BAT-06 Stats **shall** resolve as `base × (1 + workshopBonus) × (1 +
  inRoundBonus)`. *(`docs/battle-formulas.md`)*
- R-BAT-07 Damage calculation **shall** include a crit roll, crit multiplier
  (`2.0 + critFactorLevel × 0.1`), and a damage/meter distance bonus.
- R-BAT-08 Defence **shall** be capped at 75% reduction plus a flat block
  component. *(`docs/battle-formulas.md`)*
- R-BAT-09 Lifesteal **shall** be capped at 15% of damage dealt; thorn
  damage **shall** be reduced by the THORN_RESISTANCE battle condition;
  knockback **shall** be reduced by the KNOCKBACK_RESISTANCE condition;
  orbs **shall** be reduced by the ORB_RESISTANCE condition;
  ARMORED_ENEMIES **shall** absorb the first N hits of damage fully.
- R-BAT-10 Advanced mechanics: Orbs (cap 6), Multishot (cap 5 targets),
  Bounce Shot (cap 4 bounces). *(`docs/plans/plan-10b-advanced-combat.md`)*
- R-BAT-11 Battle Workshop wiring: the real workshop level map **shall**
  be passed into the game engine (not `emptyMap()`).
  *(`docs/plans/plan-R-remediation.md` R03)*

### 1.6 In-round economy

- R-IR-01 Cash per kill **shall** be `baseKillCash × tierCashMultiplier ×
  (1 + cashBonusLevel × 0.03)`. *(`docs/battle-formulas.md`)*
- R-IR-02 Interest on held cash **shall** be capped at 10% per wave.
- R-IR-03 Free-upgrade chance **shall** be capped at 25%.

### 1.7 Tier system

- R-TIER-01 Ten tiers **shall** exist with the unlock requirements, cash
  multipliers and battle conditions in GDD §6.1.
- R-TIER-02 Tier unlock **shall** be wave-milestone gated: Wave 50 on the
  previous tier for Tiers 2–4, Wave 75 for Tiers 5–6, Wave 100 for Tiers
  7–9, Wave 150 for Tier 10. *(GDD §6.1)*
- R-TIER-03 Seven battle condition types (ORB_RESISTANCE,
  KNOCKBACK_RESISTANCE, ARMORED_ENEMIES, THORN_RESISTANCE, MORE_BOSSES,
  ENEMY_SPEED, ENEMY_ATTACK_SPEED) **shall** activate from Tier 6
  onward with the multipliers in GDD §6.1–6.2 and
  `docs/battle-formulas.md`.

### 1.8 Biomes

- R-BIO-01 Five biomes **shall** exist mapped to tier ranges:
  Hanging Gardens (1–3), Burning Sands (4–6), Frozen Ziggurats (7–8),
  Underworld of Kur (9–10), Celestial Gate (11+). *(GDD §6.3)*
- R-BIO-02 Each biome transition **shall** trigger a cinematic showing
  the ziggurat "ascending" with total steps walked displayed.
  *(GDD §6.3, Plan 18)*
- R-BIO-03 Each biome **shall** have its own colour palette, enemy
  visual language, ziggurat appearance, and cosmetic skin unlock.
  *(GDD §6.3)*

### 1.9 Ultimate Weapons

- R-UW-01 Six UW types **shall** exist (Death Wave, Chain Lightning,
  Black Hole, Chrono Field, Poison Swamp, Golden Ziggurat) with the
  unlock costs, cooldowns, durations, and damage formulas in GDD §7 and
  `docs/battle-formulas.md`.
- R-UW-02 Players **shall** select up to 3 UWs per round.
- R-UW-03 UW upgrade cost **shall** be `unlockCost × 2 × currentLevel`
  in Power Stones, to a max level of 10.
  *(`docs/battle-formulas.md`)*
- R-UW-04 Cooldown **shall** scale as `baseCooldownSeconds × (1 − 0.05 ×
  (level − 1))`.

### 1.10 Step Overdrive

- R-OD-01 Four overdrive types **shall** exist: Assault (500 Steps, 2×
  attack speed + 1.5× damage), Fortress (500 Steps, 2× regen + 50%
  reduction), Fortune (300 Steps, 3× cash), Surge (750 Steps, resets UW
  cooldowns). *(GDD §5.1, `docs/battle-formulas.md`)*
- R-OD-02 Overdrive **shall** last 60 seconds and **shall** be usable at
  most once per round. *(GDD §5.1, `CONSTRAINTS.md`)*
- R-OD-03 Overdrive **shall** stack multiplicatively with existing stats.
  *(`docs/battle-formulas.md`)*

### 1.11 Labs

- R-LAB-01 Ten research types **shall** exist, each with the Step costs,
  real-time durations, effects, and max levels listed in GDD §8 and
  `docs/battle-formulas.md`.
- R-LAB-02 The player **shall** start with 1 Lab slot and **shall** be
  able to unlock slots 2–4 for 200 Gems each (max 4). *(GDD §8,
  `.kiro/steering/source-files.md`)*
- R-LAB-03 Research cost **shall** scale as `baseCost × scaling ^ level`,
  time **shall** scale similarly. *(`docs/battle-formulas.md`)*
- R-LAB-04 Research **shall** be rushable to instant completion via Gems
  on a linear 50–200 Gem cost curve. *(`.kiro/steering/source-files.md` —
  RushResearch, Plan 16)*
- R-LAB-05 Completed-in-background research **shall** be auto-credited on
  app launch. *(`.kiro/steering/source-files.md` — CheckResearchCompletion)*

### 1.12 Cards

- R-CARD-01 Nine card types **shall** exist across three rarities
  (Common / Rare / Epic). *(GDD §9)*
- R-CARD-02 Cards **shall** be acquired from Card Packs purchased with
  Gems (Common 50 / Rare 150 / Epic 500). *(`docs/monetization.md`)*
- R-CARD-03 Duplicate cards **shall** convert to Card Dust.
- R-CARD-04 Each card **shall** have 5 upgrade levels. *(GDD §9)*
- R-CARD-05 The equipped loadout **shall** be capped at 3 cards.
  *(`CONSTRAINTS.md`)*

### 1.13 Walking Encounters & Supply Drops

- R-SUP-01 Supply Drops **shall** be generated via a seeded random
  system keyed on step count and time of day. *(GDD §2.3)*
- R-SUP-02 Drops **shall** be delivered via push notification with
  one-tap claim; disabled-notification users **shall** accumulate drops
  in an "Unclaimed Supplies" inbox capped at 10 stored. *(GDD §2.3)*
- R-SUP-03 Drop rates **shall** average 2–4 per 10,000 steps. *(GDD §2.3)*
- R-SUP-04 Triggers: every 2,000 steps (5%/100 steps), every 1.5 km GPS
  distance, step bursts (500+ steps in 5 min), 10,000-step daily
  milestone, and a random 1%/500 steps. *(GDD §2.3)*
- R-SUP-05 Deep-link notifications **shall** open the Unclaimed Supplies
  inbox, including when the app is already in the foreground (warm
  start). *(`docs/plans/plan-R-remediation.md` R09)*

### 1.14 Notifications & Widget

- R-NOTIF-01 A persistent foreground-service notification **shall** show
  live step count and spendable balance. *(GDD §12.3,
  `docs/step-tracking.md`)*
- R-NOTIF-02 A 2×2 home-screen widget **shall** show the step count with
  a mini-ziggurat; tapping it **shall** open the app.
  *(GDD §12.3, `docs/plans/plan-R-remediation.md` R06)*
- R-NOTIF-03 Smart reminders **shall** appear when the player is within
  a configurable step distance of a purchase (e.g. "2,000 steps away
  from upgrading Chain Lightning"). *(GDD §12.3, Plan 23)*
- R-NOTIF-04 Milestone achievement notifications **shall** fire at most
  once per milestone. *(`docs/plans/plan-R2-remediation.md` R2-08)*
- R-NOTIF-05 Biome-unlock cinematic notifications **shall** surface total
  steps walked. *(GDD §12.3, Plan 23)*
- R-NOTIF-06 Notification-setting labels **shall** accurately describe
  runtime behaviour. Toggling "off" must not imply the foreground
  notification disappears. *(`docs/plans/plan-R2-remediation.md` R2-05)*

### 1.15 Stats & History

- R-STAT-01 The Stats screen **shall** render walking history as bar
  charts over daily / weekly / monthly periods. *(GDD §12.1, Plan 22)*
- R-STAT-02 All-time aggregate stats **shall** include total Steps,
  Gems, Power Stones earned/spent; rounds played; enemies killed; Cash
  earned. *(`docs/database-schema.md` PlayerProfile columns, Plan 22)*

### 1.16 Monetization

- R-MON-01 The store **shall** offer 3 Gem packs ($0.99 / $4.99 / $9.99),
  Ad Removal ($3.99 one-time), and Season Pass ($4.99/month).
  *(GDD §13, `docs/monetization.md`)*
- R-MON-02 Ad Removal **shall** remove all optional ads across the app,
  and the flag **shall** persist across battle replays.
  *(`docs/plans/plan-R-remediation.md` R09)*
- R-MON-03 Season Pass benefits **shall** include 10 bonus Gems/day, 1
  free Lab rush/day, a monthly exclusive cosmetic, and a profile badge.
  *(`docs/monetization.md`)*
- R-MON-04 Season Pass expiry **shall** be checked consistently across
  every screen that references it. *(`docs/plans/plan-R-remediation.md` R09)*
- R-MON-05 Cosmetics purchases **shall** be disabled in the UI until
  visual application is implemented.
  *(`docs/plans/plan-R2-remediation.md` R2-11)*
- R-MON-06 Reward ads **shall** be strictly opt-in (post-round Gem,
  post-round double-PS, daily free Card Pack). No forced ads.
  *(GDD §13.1, `docs/monetization.md`)*
- R-MON-07 The app **shall** use a `BillingManager` interface + stub
  implementation and an `AdManager` interface + stub implementation
  until real SDKs are integrated under Plan 31.
  *(`docs/monetization.md` "Architecture")*

## 2. Non-functional requirements

### 2.1 Architecture

- R-NFR-ARCH-01 Clean Architecture layering: `presentation → domain ←
  data`; no shortcuts. *(`docs/architecture.md`, `CONSTRAINTS.md`)*
- R-NFR-ARCH-02 `domain/` **shall** have zero Android imports. Pure
  Kotlin. *(repeated in every architecture-relevant doc)*
- R-NFR-ARCH-03 Use cases **shall** be plain Kotlin classes with
  constructor injection and no Hilt annotations.
  *(`.kiro/steering/structure.md`, `.kiro/steering/lib-hilt.md`)*
- R-NFR-ARCH-04 ViewModels **shall** expose `StateFlow` (never `LiveData`).
  *(`.kiro/steering/lib-jetpack-compose.md`)*
- R-NFR-ARCH-05 Room is the single source of truth for all game state.
  *(`.kiro/steering/product.md`, `CONSTRAINTS.md`,
  `docs/database-schema.md`)*

### 2.2 Persistence & security

- R-NFR-DB-01 The Room database **shall** be encrypted at rest with
  SQLCipher (AES-256 via `net.zetetic:sqlcipher-android`).
  *(`docs/architecture.md`, `docs/database-schema.md`)*
- R-NFR-DB-02 The database passphrase **shall** be protected by an
  Android Keystore AES-256-GCM key and auto-recovered on keystore
  mismatch. *(`docs/architecture.md`, `docs/plans/plan-R-remediation.md` R05)*
- R-NFR-DB-03 Android backup **shall** be disabled (`allowBackup="false"`
  plus data-extraction rules excluding the DB and passphrase blob).
  *(`docs/architecture.md`, R05)*
- R-NFR-DB-04 Room schema exports **shall** be committed under
  `app/schemas/`. *(`.kiro/steering/structure.md`)*
- R-NFR-DB-05 Post-release schema version bumps **shall** be accompanied
  by explicit `Migration` objects;
  `fallbackToDestructiveMigration()` is forbidden in production.
  *(`docs/plans/plan-R2-remediation.md` R2-06)*
- R-NFR-DB-06 All currency decrements **shall** be guarded at the DAO
  level so balances cannot go negative (`SET col = MAX(0, col − :amount)`
  or transactional check-then-deduct).
  *(`docs/plans/plan-R-remediation.md` R10)*

### 2.3 Network / privacy

- R-NFR-NET-01 Cleartext traffic **shall** be blocked via
  `network_security_config.xml`. *(`docs/architecture.md`)*
- R-NFR-NET-02 v1.0 **shall** ship with no server backend; no game data
  may be uploaded off-device. *(`docs/release/privacy-policy.md`,
  `.kiro/steering/product.md`)*
- R-NFR-NET-03 Health Connect permissions **shall** be limited to
  `READ_STEPS` and `READ_EXERCISE`.
  *(`docs/step-tracking.md`, `docs/release/privacy-policy.md`)*

### 2.4 Build & tooling

- R-NFR-BUILD-01 Language target: Kotlin, JVM 17.
  *(`.kiro/steering/tech.md`, `AGENTS.md`)*
- R-NFR-BUILD-02 Annotation processing: KSP, never kapt.
  *(`CONSTRAINTS.md`, `.kiro/steering/tech.md`)*
- R-NFR-BUILD-03 Gradle 9.3.1 with Kotlin DSL; all versions in
  `gradle/libs.versions.toml`. Hardcoded versions in build files are
  forbidden. *(`.kiro/steering/tech.md`, `CONSTRAINTS.md`)*
- R-NFR-BUILD-04 Release builds **shall** enable `isMinifyEnabled=true`
  and `isShrinkResources=true` with ProGuard/R8 rules hardened for
  Room, Hilt, SQLCipher, Health Connect, sensors, and WorkManager.
  *(`docs/release/release-checklist.md`)*
- R-NFR-BUILD-05 Non-TTY environments (CI, Kiro CLI) **shall** use
  `./run-gradle.sh` to avoid Gradle output buffering hangs.
  *(`README.md`, `AGENTS.md`)*

### 2.5 Testing

- R-NFR-TEST-01 Domain-layer logic **shall** be tested as pure-JVM JUnit
  5 tests with `kotlinx-coroutines-test`. *(`AGENTS.md`, Plan 29)*
- R-NFR-TEST-02 ViewModel tests **shall** use in-memory fake
  repositories. Fakes exist for every real repository.
  *(`AGENTS.md` "Fakes" list)*
- R-NFR-TEST-03 Balance constants **shall** be covered by regression
  tests so any constant change is caught. 39 balance tests are the
  documented baseline. *(`docs/balance/balance-report.md`)*
- R-NFR-TEST-04 The documented minimum test count on release is 397
  per `docs/release/release-checklist.md`; `AGENTS.md` records the
  current count as 401 and `STATE.md` as 412. See the Docs vs Code
  delta.
- R-NFR-TEST-05 Integration tests (widget SharedPreferences round-trip,
  deep-link intent routing, Room schema round-trip, escrow lifecycle
  end-to-end) **shall** exist. *(`docs/plans/plan-R-remediation.md` R12)*
- R-NFR-TEST-06 Instrumented tests (`connectedAndroidTest`) are
  acknowledged as "planned but not yet implemented" / deferred.
  *(`README.md`, `AGENTS.md` post-R11 note)*

### 2.6 Platform

- R-NFR-PLAT-01 Min SDK 34 (Android 14), compile/target SDK 36.
  *(`README.md`, `AGENTS.md`, `.kiro/steering/tech.md`)*
- R-NFR-PLAT-02 Required permissions: `ACTIVITY_RECOGNITION`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`,
  `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`,
  `android.permission.health.READ_STEPS`,
  `android.permission.health.READ_EXERCISE`.
  *(`docs/step-tracking.md` "Permissions")*
- R-NFR-PLAT-03 Device must have a step counter sensor (documented in
  the Play Store listing).

### 2.7 Performance / resource use

- R-NFR-PERF-01 Battle game loop **shall** run at a fixed 60 UPS
  timestep on its own thread. *(`docs/architecture.md` "Game Loop",
  `AGENTS.md`)*
- R-NFR-PERF-02 Step counting **shall** be battery-efficient. The
  documented acceptance bar is "< 5% per day for step counting"
  (`docs/release/release-checklist.md`).
- R-NFR-PERF-03 Sensor sampling **shall** use
  `SensorManager.SENSOR_DELAY_NORMAL` and minimise wake-locks.
  *(`docs/step-tracking.md`)*

### 2.8 Accessibility

- R-NFR-A11Y-01 *(aspirational, Plan 24 deferred)* TalkBack support for
  all menu screens, battle-screen audio cues, three colour-blind
  palettes, adjustable text size. *(GDD §17,
  `docs/plans/plan-24-accessibility.md`)*
- R-NFR-A11Y-02 All icon- or symbol-only interactive controls **shall**
  carry content descriptions for TalkBack.
  *(`docs/plans/plan-R-remediation.md` R11)* — this one *has* shipped.
- R-NFR-A11Y-03 Activity Minute Parity **shall** convert wheelchair,
  cycling, rowing, swimming, yoga and treadmill activity into
  Step-equivalents at the rates in GDD §11.4 and
  `docs/step-tracking.md` with the stated per-activity daily caps.

## 3. Release / publication requirements

- R-REL-01 The app **shall** ship as a signed AAB, with a separately
  managed upload keystore and `keystore.properties` excluded from git.
  *(`docs/release/signing-guide.md`, `docs/release/release-checklist.md`)*
- R-REL-02 Google Play App Signing enrollment is recommended during
  first Play Console upload. *(`docs/release/signing-guide.md`)*
- R-REL-03 A privacy policy **shall** be hosted at a public URL and
  linked from the Play Console listing.
  *(`docs/release/play-store-listing.md`,
  `docs/release/release-checklist.md`)*
- R-REL-04 Store listing assets: 512×512 app icon, 1024×500 feature
  graphic, ≥2 phone screenshots (recommended 8). Category: Games →
  Strategy. Contact email required.
  *(`docs/release/play-store-listing.md`)*
- R-REL-05 Content rating: Everyone / PEGI 3.
  *(`docs/release/play-store-listing.md`)*
- R-REL-06 Plan 31 Play Console tasks: test-track setup, IAP product
  configuration, real Google Play Billing + AdMob SDK integration,
  Firebase Test Lab pre-launch report, internal → closed → open →
  production track promotion. *(`docs/plans/plan-31-play-console.md`)*

## 4. Process requirements

- R-PROC-01 Each session **shall** begin with a Context Preflight:
  read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, the latest
  `RUN_LOG` entry, and referenced ADRs; check `git status` and
  `git log -n 10 --oneline`; output a ~10-bullet Session Brief.
  *(`.kiro/steering/11-agent-protocol.md`)*
- R-PROC-02 Each session **shall** end with memory writes: update
  `STATE.md`, append `RUN_LOG.md`, create/update an ADR if a
  non-trivial decision was made.
  *(`.kiro/steering/11-agent-protocol.md`)*
- R-PROC-03 `STATE.md` **shall** stay one page; detail belongs in
  `RUN_LOG.md` and ADRs. *(`.kiro/steering/10-project-memory.md`)*
- R-PROC-04 Plan files **shall** be read before implementing any
  feature they cover. *(`AGENTS.md`, `README.md`, `START_HERE.md`)*
- R-PROC-05 Chat history **shall not** be treated as project memory —
  the doc spine is canonical. *(`.kiro/steering/10-project-memory.md`)*

---

## Docs vs Code delta

**Where docs and code agree:**

- The anti-cheat ceilings (200/min, 50k/day), the loadout caps
  (3 UW / 3 Cards), and the upgrade cost formula are stated
  consistently across GDD, `docs/battle-formulas.md`, `product.md`,
  `CONSTRAINTS.md`, and the Remediation plans. Nothing in the doc set
  contradicts them.
- The platform targets (SDK 34–36, JDK 17, Gradle 9.3.1) match between
  `README.md`, `AGENTS.md`, and `.kiro/steering/tech.md`.
- Health Connect permissions match between `docs/step-tracking.md`
  and `docs/release/privacy-policy.md`.

**Where docs appear outdated:**

- R-NFR-DB-05 ("no `fallbackToDestructiveMigration`") is marked
  complete by Plan R2-06 and by `STATE.md`, but
  `docs/release/release-checklist.md` still carries an unchecked box
  for that very item.
- R-NFR-TEST-04 has three different test counts across three
  simultaneously-current docs (397 / 401 / 412). At least two of the
  three are stale.
- `docs/database-schema.md` still reads as schema v7 with the last
  migration v6→v7; the ADR-0003 v7→v8 migration (`battleStepsEarned`
  column on `DailyStepRecordEntity`) is not reflected in that doc.
- R-ECO-02 is documented inconsistently: the GDD, `product.md`, and
  `START_HERE.md` say Steps come from walking only. Only ADR-0003 +
  `CONSTRAINTS.md` + `STATE.md` acknowledge the new per-kill Battle
  Step reward path. If `product.md` and the GDD are the public
  description of the rule, they contradict the implemented design.
- R-MON-05 ("cosmetic purchases disabled pre-release") is recorded in
  R2-11 but `docs/monetization.md` still reads as though cosmetic
  purchases work end-to-end in the current build.
- The README's "33-entry development roadmap" phrasing pre-dates
  Plan R2 and now under-counts by one.

**Where docs describe intended future state rather than current
behaviour:**

- R-NFR-A11Y-01 (full accessibility) is Plan 24 and deferred.
  Only R-NFR-A11Y-02 and R-NFR-A11Y-03 appear to be shipped.
- R-MON-07 (real SDK integration) is explicitly Plan 31 / deferred.
- R-REL-04 (visual store assets) is "Deferred to Plan 31" in
  `docs/release/play-store-listing.md`.
- R-STEP-09 (battery-optimization whitelisting on first launch) is
  referenced in GDD and step-tracking docs but no plan/ADR/changelog
  entry confirms it ships in v1.0.
- GDD §2.3's GPS "Exploration Mode" reward trigger has no plan or
  implementation evidence.
- GDD §18 "Future Expansion Possibilities" is explicitly labelled as
  future.

**Where code contains behaviour not documented in narrative docs:**

- R-ECO-02 (Battle Step rewards) exists only in `ADR-0003`,
  `STATE.md`, and `CONSTRAINTS.md`. Every user-facing description of
  how Steps are earned (GDD, README, Play-Store listing, CHANGELOG
  1.0.0) currently says "walking only" and will need updating.
- `SharedPreferences` stores (`BiomePreferences`,
  `MilestoneNotificationPreferences`, `NotificationPreferences`,
  `SoundPreferences`, `AntiCheatPreferences`, `StepIngestionPreferences`)
  are state surfaces that technically live outside Room, despite
  R-NFR-ARCH-05. Narrative docs don't call these out as exceptions.
- Plan R / Plan R2 introduce a number of user-facing improvements
  (double-tap purchase guards, user-message snackbars on failed
  actions, midnight-staleness refresh, warm-start deep-link handling,
  milestone notification dedup, worker logging, reactive economy
  dashboard) that are only described in the remediation plan files
  and never back-ported into the feature-area docs they changed.
- The R-NFR-TEST-05 integration-test list (widget SharedPreferences
  round-trip, deep-link routing, Room v7 schema round-trip, escrow
  lifecycle) is documented in R12 only — GDD/test-strategy sections
  don't reference it.

**Where documentation is too vague to verify:**

- R-SUP-01 / R-SUP-03 / R-SUP-04: the seeded-random algorithm and the
  exact probability curves for each trigger are not specified.
- R-ECO-09: daily-mission reward amounts differ between GDD §16.2
  (fixed quotes) and `docs/monetization.md` (range "2–10 Gems each").
- R-BAT-05: "Bosses every 10 waves (every 7 with MORE_BOSSES)" is
  consistent across docs, but the exact wave list at which Scatter /
  Ranged / Fast enemies begin appearing per tier is not spelled out
  in any narrative doc.
- R-NFR-PERF-02: "< 5% battery per day" is a target in the release
  checklist with no measurement methodology documented.
- R-UW-01: Golden Ziggurat's cash multiplier is listed as "5× cash"
  in GDD §7 but `docs/battle-formulas.md` records the active effect
  as "5× cash + 1.5× damage". Whether the damage boost is always on
  or is the upgraded version is not explicitly reconciled.
- R-NFR-A11Y-03: Activity Minute conversion rates are given as crisp
  integers but the rationale (why 100, not 90 or 120) is never
  documented.
- R-SUP-05: the documented "inbox capped at 10 stored" is stated in
  GDD §2.3 but the eviction policy (oldest first? unclaimed only?) is
  not specified in any doc.
