# Project Description (Doc-Inferred)

> Scope of this document: synthesis of the project description **as described in
> the repository's non-code documentation only**. Source files read: `README.md`,
> `AGENTS.md`, `CHANGELOG.md`, `docs/StepsOfBabylon_GDD.md`, `docs/architecture.md`,
> `docs/database-schema.md`, `docs/battle-formulas.md`, `docs/step-tracking.md`,
> `docs/monetization.md`, `docs/plans/master-plan.md`, `docs/plans/plan-R-remediation.md`,
> `docs/plans/plan-R2-remediation.md`, spot-checked individual plan files
> (01, 24, 31), `docs/release/*`, `docs/balance/balance-report.md`,
> `docs/agent/START_HERE.md`, `docs/agent/STATE.md`, `docs/agent/CONSTRAINTS.md`,
> `docs/agent/DECISIONS/ADR-0001-template.md`, `ADR-0002-health-connect.md`,
> `ADR-0003-battle-step-rewards.md`, and `.kiro/steering/*.md`. Source code
> (Kotlin, Gradle build scripts, tests) was deliberately **not consulted** for
> this phase.

---

## 1. What this product is

Steps of Babylon is a **single-player Android mobile game** that fuses an idle
tower-defense upgrade loop with a real-world pedometer. Every unit of permanent
progression — the "Steps" currency — is earned by the player physically
walking (or, via Activity Minute Parity, performing other tracked exercise).
Players spend those Steps to upgrade an ancient Babylonian ziggurat that
automatically defends itself against waves of mythic enemies in real-time
battles.

Taglines stated in docs: "Every Step Builds the Tower." (`README.md`, GDD cover)
and "An idle tower defense game where your real-world steps raise an ancient
ziggurat." (GDD §1).

Identified in docs:

- **Platform:** Android (GDD §1, `README.md`).
- **Minimum SDK:** 34 (Android 14); compile/target SDK 36 (`README.md`,
  `AGENTS.md`, `.kiro/steering/tech.md`).
- **Version at initial release:** 1.0.0 / versionCode 1 (`CHANGELOG.md`,
  `AGENTS.md`, `docs/release/release-checklist.md`).
- **Experience:** Solo, real-time battles, no multiplayer, no server backend
  for v1.0 (GDD §1 header, `docs/agent/START_HERE.md`, `.kiro/steering/product.md`).
- **Primary goal:** "v1.0 release on Google Play Store" (`START_HERE.md`).
- **Target user:** "Mobile gamers who want fitness motivation through
  gameplay" (`START_HERE.md`).

## 2. Core loop (as documented)

Documented in GDD §2 as two interlocking loops:

1. **Walk Loop (real world):** Device step counter + Health Connect accumulate
   Steps in the background → persistent notification / home-screen widget
   surface the balance → player opens the app and spends Steps on permanent
   Workshop upgrades.
2. **Battle Loop (in-game):** Player launches a Round → waves of enemies
   (26-second spawn phase + 9-second cooldown) → ziggurat auto-attacks → player
   spends round-local Cash on in-round upgrades, optionally activates Ultimate
   Weapons and Step Overdrive → round ends on ziggurat death → rewards apply →
   player returns to the Workshop.

README summarises this as "Walk → Earn Steps → Spend Steps on Workshop upgrades
→ Fight battles → Reach higher tiers → Repeat" (`.kiro/steering/product.md`).

## 3. Major systems (documented surface)

The documentation consistently describes the following systems. Counts below
are those stated in the docs; see the Docs vs Code delta for drift warnings.

| System | Docs summary | Primary source |
|---|---|---|
| Step tracking | `TYPE_STEP_COUNTER` primary, Health Connect cross-validation/gap-fill, `TYPE_STEP_DETECTOR` tertiary (deferred). Foreground service + WorkManager 15-min sync. Boot receiver. Heartbeat-based service/worker coordination. | `docs/step-tracking.md`, GDD §11 |
| Workshop upgrades | 23 permanent upgrade types across Attack/Defense/Utility branches. Cost `baseCost × scaling^level` rounded up. "Quick Invest" convenience path. | GDD §4, `docs/battle-formulas.md`, Plan 01, Plan 07 |
| Battle renderer | Custom `SurfaceView` with dedicated thread, fixed timestep game loop, 1×/2×/4× speed controls. Entity system for ziggurat, enemies, projectiles, orbs, effects. | `docs/architecture.md`, GDD §15, Plan 08 |
| Enemies | 6 types (Basic, Fast, Tank, Ranged, Boss, Scatter) with speed/health/damage multipliers. Bosses every 10 waves (every 7 at Tier 9+ with the MORE_BOSSES condition). | GDD §10, `battle-formulas.md` |
| Tier system | 10 tiers with wave-based unlocks, escalating cash multipliers (1.0× → 10.0×), and battle conditions from Tier 6+ (orb/knockback/thorn resistance, armored enemies, more bosses, enemy speed, enemy attack speed). | GDD §6, `battle-formulas.md` |
| Biomes | 5 narrative biomes mapped to tier ranges: Hanging Gardens (1–3), Burning Sands (4–6), Frozen Ziggurats (7–8), Underworld of Kur (9–10), Celestial Gate (11+). Each has its own palette, enemy theme, ziggurat appearance and cinematic on transition. | GDD §6.3, Plan 18 |
| Labs | 10 research types with Step cost + real-time duration, 1–4 slots (extra slots unlocked with Gems). Gem rush for instant completion. Background-timer based. | GDD §8, Plan 16 |
| Cards | 9 card types across 3 rarities (Common/Rare/Epic). Acquired from Gem-priced Card Packs. Duplicates convert to Card Dust. 5 upgrade levels. Loadout of 3. | GDD §9, Plan 17 |
| Ultimate Weapons | 6 types (Death Wave, Chain Lightning, Black Hole, Chrono Field, Poison Swamp, Golden Ziggurat) unlocked/upgraded with Power Stones. Loadout of 3. Cooldown-based activation. Max level 10. | GDD §7, `battle-formulas.md`, Plan 15 |
| Step Overdrive | 4 types (Assault 500 / Fortress 500 / Fortune 300 / Surge 750 Steps) granting 60-second buffs; once per round. | GDD §5.1, Plan 14 |
| Walking Encounters & Supply Drops | Seeded-random drops during walks, delivered via push notification. Inbox caps at 10 stored. 4 documented reward types: Steps, Gems, Power Stones, Card Dust; GDD also references an "energy surge" Overdrive Charge drop. | GDD §2.3, Plan 19 |
| Economy (premium currencies) | Weekly step challenges award Power Stones at 50k/75k/100k tiers. Daily login streaks award Gems + PS. Wave personal-best milestones award PS (1/2/5). Walking milestones (6 tiers from First Steps → Globe Trotter). | GDD §7.1, §16, Plan 20, Plan 21 |
| Daily Missions | 3 random missions per day across Walking/Battle/Upgrade categories, refreshed at midnight. | GDD §16.2, Plan 21 |
| Stats & History | Walking-history charts (daily/weekly/monthly), battle statistics, all-time aggregates. | GDD §12.1, Plan 22 |
| Notifications & Widget | Persistent step count notification (foreground-service mandated), 2×2 home widget, smart "you're N steps away from…" reminders, milestone alerts, biome unlock cinematics, supply-drop rich notifications. | GDD §12.3, Plan 23 |
| Monetization (stub) | 3 Gem packs ($0.99 / $4.99 / $9.99), Ad Removal ($3.99 one-time), Season Pass ($4.99/month subscription), cosmetic IAPs ($0.99–$2.99), optional reward ads. Steps can **never** be purchased. Stubs in place; real SDKs deferred. | `docs/monetization.md`, Plan 26 |
| Anti-cheat | 200 steps/min rate limit (250 burst), step-velocity analysis (shakers/spoofers), 50,000 steps/day ceiling, graduated Health Connect cross-validation (4 offense levels), activity-minute validation, per-minute overlap deduction. | `docs/step-tracking.md`, GDD §11.3, `CONSTRAINTS.md` |
| Battle Step Rewards | ADR-0003 adds a small per-kill Step reward (BASIC/FAST/SCATTER = 1, RANGED = 2, TANK = 3, BOSS = 10) capped at 2,000/day via a new `battleStepsEarned` column (schema v8). Flat, not multiplied by overdrive/cards/UWs. | `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`, `STATE.md`, `CONSTRAINTS.md` |

## 4. Currencies (documented)

From GDD §3 and `.kiro/steering/product.md`:

| Currency | Source (as documented) | Persistence | Primary uses |
|---|---|---|---|
| **Steps** | Real-world walking + Activity Minute Parity + (per ADR-0003) small per-kill battle rewards capped at 2,000/day | Permanent | Workshop upgrades, Labs research, UW unlocks (via Power Stones indirectly — see §7), Overdrive activations |
| **Cash** | Killing enemies + wave completion bonuses | Transient (round-local; resets each round) | In-round upgrades |
| **Gems** | Daily login streaks, milestones, daily missions, Supply Drops, optional post-round ad; purchaseable | Permanent | Card Packs, Lab slot unlocks, Lab rush, cosmetics |
| **Power Stones** | Weekly step challenges, wave milestones, daily bonus | Permanent | Unlocking and upgrading Ultimate Weapons |
| **Card Dust** | Duplicate cards | Permanent | Card level upgrades |

## 5. Architecture at a glance (doc view)

Docs consistently describe a three-layer Clean Architecture:

```
presentation/  (ViewModels, Compose screens, SurfaceView battle renderer)
     │
     ▼
   domain/    (Use cases, repository interfaces, pure-Kotlin models — zero Android imports)
     ▲
     │
   data/      (Room entities/DAOs, repository impls, sensor + Health Connect + billing + ads data sources)
```

Supporting slices stated in docs:

- **Dependency Injection:** Hilt with KSP (not kapt). Modules live in `di/`.
- **Persistence:** Room over SQLCipher-encrypted SQLite. Android Keystore manages
  the passphrase. `allowBackup="false"`. Schema version 7 (per
  `docs/database-schema.md`) or 8 (per ADR-0003 / `STATE.md`). See delta.
- **Async:** Kotlin coroutines + Flow; ViewModels expose `StateFlow` to Compose.
- **Background work:** Foreground service (health type, `START_STICKY`) plus
  WorkManager periodic worker, coordinated via a heartbeat in
  `StepIngestionPreferences`.
- **Build:** Gradle 9.3.1 (Kotlin DSL) with a single version catalog at
  `gradle/libs.versions.toml`. JDK 17.

## 6. What is explicitly out of scope for v1.0 (stated)

- Multiplayer, leaderboards, tournaments, guilds — all listed under "Future
  Expansion Possibilities" (GDD §18).
- Any server backend — "Solo experience — no multiplayer, no server backend
  required for v1.0" (`AGENTS.md`, `.kiro/steering/product.md`).
- Real Play Billing Library integration (stub shipped instead —
  `docs/monetization.md`).
- Real AdMob integration (stub shipped instead).
- Real cosmetic visual application (`docs/monetization.md` explicitly calls
  this out as deferred; Plan R2-11 gates purchases until visuals exist).
- Accessibility Plan 24 — "Deferred (post-v1.0)" per
  `docs/plans/plan-24-accessibility.md` and the master plan status.
- `TYPE_STEP_DETECTOR` tertiary sensor — marked "*Deferred — not implemented
  in v1.0*" in `docs/step-tracking.md`.
- GPS "Exploration Mode" for distance-based supply drops — mentioned as
  optional in GDD §2.3 but not confirmed in implementation docs.
- App icon, feature graphic, screenshots — `docs/release/play-store-listing.md`
  says "Deferred to Plan 31".

## 7. Delivery status (doc-stated)

`AGENTS.md`, `CHANGELOG.md`, and `docs/agent/STATE.md` agree that all of Plans
01–30 (plus 10b, R, R2) are complete, with Plan 24 (Accessibility) explicitly
deferred. Plan 31 (Play Console & Store Publication) is the next item of work
and is the only thing between the current state and public release. The
`Unreleased` section of `CHANGELOG.md` lists Plan R2 and Plan 31 as pending
for a future version.

---

## Docs vs Code delta

This section is the allowed place to reconcile documentation with other
evidence. Findings are based on documentation artefacts (ADRs, remediation
plans, STATE.md, CHANGELOG.md) that describe the code's behaviour, not on
reading code directly.

**Where docs and code agree (as admitted by the docs themselves):**

- Clean Architecture layering, Hilt+KSP, Room+SQLCipher, coroutines/Flow,
  Compose menus + SurfaceView battle renderer — described identically across
  `README.md`, `AGENTS.md`, `.kiro/steering/{product,structure,tech}.md`,
  `docs/architecture.md`, and confirmed by the source-file index in
  `.kiro/steering/source-files.md`.
- Anti-cheat ceilings (200/min, 50k/day, graduated Health Connect response)
  match between GDD §11.3, `docs/step-tracking.md`, and `CONSTRAINTS.md`.
- Monetization product catalogue matches between GDD §13,
  `docs/monetization.md`, and `docs/release/play-store-listing.md`.

**Where docs appear outdated:**

- `README.md` calls the master plan "a 33-entry development roadmap", but
  `AGENTS.md` and `docs/plans/master-plan.md` list **34 entries** (Plans 01–31
  plus 10b, R, R2).
- `docs/database-schema.md` states "Current schema version: 7" and lists
  migrations v1→v7, but `ADR-0003-battle-step-rewards.md` and
  `docs/agent/STATE.md` state that **schema v8 is now current** and that a new
  `battleStepsEarned` column exists on `DailyStepRecordEntity`. The schema doc
  has not been updated to reflect ADR-0003.
- `docs/release/release-checklist.md` says "All 397 unit tests pass";
  `AGENTS.md` says 401 and `STATE.md` says 412. All three figures are cited in
  the repo — the checklist is the stalest.
- `CHANGELOG.md` `[1.0.0] — 2026-03-10` does not list the Battle Step Rewards
  feature introduced by ADR-0003 on 2026-05-03; that feature lives only in
  `STATE.md`, `CONSTRAINTS.md`, and the ADR.
- `docs/plans/master-plan.md` still marks Plan 24 as "Deferred" / unchecked,
  yet `CHANGELOG.md`'s R11 entry says "Added content descriptions to all
  symbol-only battle controls for TalkBack accessibility". Partial
  accessibility has shipped under Plan R11 without updating Plan 24's status.
- `docs/release/release-checklist.md` has `fallbackToDestructiveMigration`
  marked as a pending removal item. Plan R2-06 records that the removal is
  complete (replaced with `fallbackToDestructiveMigrationOnDowngrade()`), but
  the checklist itself still shows the unchecked box.
- `docs/monetization.md` "What's Deferred" and "What's Implemented (Stub)"
  sections remain accurate in spirit, but R2-11 further narrowed the cosmetic
  purchase flow (buy button disabled pre-release) — monetization.md still
  reads as though cosmetic purchases work end-to-end.

**Where docs describe intended future state rather than current behaviour:**

- GDD §17 (Accessibility) describes TalkBack, audio cues in battle, three
  colour-blind palettes, adjustable text size, and rest-day encouragement.
  `docs/plans/plan-24-accessibility.md` marks all of this "Deferred (post-v1.0)".
  The production build carries only the small subset delivered under R11.
- GDD §18 "Future Expansion Possibilities" (Guilds, Leaderboards, Tournaments,
  Modules, Bots, Seasonal Events, Biome Side Quests, Map Hazards) is explicitly
  forward-looking.
- `docs/monetization.md` §"What's Deferred" calls out real Google Play Billing
  v7 and AdMob SDK integration, purchase verification, subscription renewals,
  and real cosmetic visual application as future work for Plan 31.
- GDD §11.1 Tertiary sensor (`TYPE_STEP_DETECTOR`) is documented as deferred
  in `docs/step-tracking.md`.
- GDD §2.3 "GPS distance tracking is optional ('Exploration Mode')" is stated
  as a design intent but no implementation doc confirms it.

**Where code contains behaviour not documented in narrative docs:**

- **Battle Step Rewards (ADR-0003)** are entirely absent from the GDD, the
  battle-formulas document, the release checklist, the Play-Store listing,
  and the `[1.0.0]` section of the changelog. The feature is only documented
  in `ADR-0003-battle-step-rewards.md`, `STATE.md`, and `CONSTRAINTS.md`.
- The `SharedPreferences`-backed state stores referenced by name in
  `.kiro/steering/source-files.md` (`BiomePreferences`,
  `MilestoneNotificationPreferences`, `NotificationPreferences`,
  `SoundPreferences`, `AntiCheatPreferences`, `StepIngestionPreferences`) are
  not catalogued in `docs/database-schema.md`, despite the schema doc's claim
  that "Room is the single source of truth for all game state". These stores
  are functionally second-tier state (dedup counters, mute flags, heartbeats)
  rather than canonical game state, but the narrative docs do not acknowledge
  them.
- `docs/step-tracking.md` lists only walking-loop follow-ons after a step
  credit. `docs/plans/plan-R2-remediation.md` (R2-02) reveals that the
  activity-minute path now also fires the same widget, supply-drop, economy
  and walking-mission pipeline. That parity is only visible in the
  remediation plan, not in `step-tracking.md`.
- Plan R1 and R2 introduced numerous UX details (user-message snackbars,
  double-tap guards, midnight date refresh, deep-link `onNewIntent`
  handling, Season Pass expiry consolidation) that are described in
  remediation plan text but never merged back into the feature-area docs
  they modified.

**Where documentation is too vague to verify:**

- Supply Drop reward catalogue: GDD §2.3 lists 5 reward shapes (Steps, Gems,
  Power Stones, Overdrive Charge, "Something shimmers… Random"), while the
  source-file index notes only 4 `SupplyDropReward` types (Steps, Gems,
  Power Stones, Card Dust). Whether the "energy surge / Overdrive Charge"
  reward actually exists as a reward type is not confirmed in any doc.
- GDD §2.3 states that Supply Drops are "generated locally using a seeded
  random system based on step count and time of day", but no doc specifies
  the seeding algorithm or the exact probability tables beyond broad
  "1 per 500 steps" / "2–4 per 10,000 steps" ranges.
- GDD §11.4 / `docs/step-tracking.md` assert that "1 Active Minute = 100
  Step-equivalents (~brisk walk pace)" without explaining how the 100
  figure was derived or validated.
- Daily Mission reward ranges differ between docs: GDD §16.2 quotes fixed
  per-mission values (e.g. "Walk 5,000 steps → 5 Gems"), while
  `docs/monetization.md` summarises the range as "2–10 Gems each". Neither
  doc states which is canonical.
- `docs/plans/plan-31-play-console.md` states that Plan 31 depends on
  Tier-1 of both Plan R and Plan R2, but the dependency list in the master
  plan shows both are complete — so whether there are any remaining blockers
  beyond Play Console assets is not explicitly stated.
