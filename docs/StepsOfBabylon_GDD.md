# Steps of Babylon — Game Design Document

> **Every Step Builds the Tower.**

An idle tower defense game where your real-world steps raise an ancient ziggurat.

**Platform:** Android | **Experience:** Solo | **Battles:** Real-Time
**Game Version:** 1.0.12 (versionCode 28) | **GDD last reconciled with code:** 2026-06-16

---

## 1. Executive Summary

Steps of Babylon is an Android mobile game that fuses the addictive incremental upgrade loop of idle tower defense (inspired by *The Tower* by Tech Tree Games) with a real-world step counter. Players earn **Steps** as their primary currency by physically walking, then spend those steps to raise an ancient ziggurat — upgrading its attack, defense, and utility capabilities. The ziggurat engages in real-time, wave-based battles against increasingly powerful mythic enemies, and progression is gated entirely by physical activity.

**The core fantasy:** every step you take in the real world builds your ziggurat higher. The more you walk, the further you survive. The further you survive, the more motivated you are to walk tomorrow. You are Babylon's architect, and the world is your building site.

Beyond the core loop, Steps of Babylon deepens the connection between walking and gameplay through four signature systems:

- **Narrative Biome Progression** — transforms the battlefield as players advance through themed worlds
- **Rapid Fire** — a Workshop upgrade that fires periodic attack-speed bursts mid-wave (R4-03; replaced the removed Step Overdrive mechanic)
- **Activity Minute Parity** — credits indoor workouts like cycling and rowing alongside traditional steps
- **Walking Encounters** — delivers real-time Supply Drop rewards during physical activity via push notifications

**Design Philosophy:** Steps are never generated *passively* in-game. Nearly every upgrade, advancement, and unlock traces back to real physical movement. The game respects the player's effort by making every step count — literally. The sole in-game Step source is a small, daily-capped **battle-step reward** for actively playing rounds (see §3.2), a deliberate supplement that never displaces walking as the primary path.

### 1.1 Design Pillars

| Pillar | Description |
|---|---|
| Walk to Power | Steps are the sole permanent currency. No pay-to-win, no *idle*/passive step generation (the only in-game source is a daily-capped battle-step reward for active play — §3.2). Your ziggurat reflects your physical effort. |
| Satisfying Depth | The Tower's layered upgrade systems (Workshop, Labs, Ultimate Weapons, Cards) adapted for step-based progression. |
| One More Walk | Escalating costs and tantalizing unlocks create motivation to walk further, mirroring The Tower's "one more run" compulsion. |
| Journey of Discovery | Tier progression is mapped to narrative biomes, giving players a visual sense of traveling through a world powered by their real-world footsteps. |
| Real-Time Spectacle | Watching your ziggurat obliterate waves in real-time provides the dopamine reward for your physical investment. |

---

## 2. Core Game Loop

Steps of Babylon's gameplay operates on two interlocking loops: the **Walk Loop** (real world) and the **Battle Loop** (in-game).

### 2.1 The Walk Loop (Real World)

The Walk Loop runs continuously in the background via Android's step counter sensors and Health Connect integration.

1. Player walks throughout their day with Steps of Babylon installed.
2. Steps are counted in real-time via Android's `TYPE_STEP_COUNTER` sensor (with Health Connect as cross-validation).
3. Steps accumulate as spendable currency, visible on a persistent notification and home screen widget.
4. Player opens the app and spends accumulated steps on permanent Workshop upgrades.
5. Upgraded ziggurat performs better in battles, motivating the player to earn more steps for the next upgrade.

**Anti-cheat:** Step data is cross-validated against Health Connect records with graduated response (4 offense levels). Rate limiting caps at 200 steps/minute (250 burst for running). Step velocity analysis detects phone shakers and spoofers via statistical pattern detection. Activity minute validation prevents gaming of exercise sessions. Per-minute overlap deduction prevents double-counting.

### 2.2 The Battle Loop (In-Game)

Battles are real-time, wave-based tower defense sequences. The player's ziggurat sits center-screen and automatically engages approaching enemies.

1. Player initiates a **Round** (free, no step cost to play).
2. Enemies approach in **Waves**. Each wave has a 26-second spawn phase and 9-second cooldown.
3. The ziggurat auto-attacks based on its stats. Player can activate Ultimate Weapons manually.
4. Between waves, the player spends **Cash** (earned from kills/waves, temporary) on in-round upgrades.
5. Enemies scale in difficulty. The round ends when the ziggurat's health reaches zero.
6. Post-round: the player sees their wave record, earns milestone rewards, and returns to the Workshop to invest steps.

**Key distinction from The Tower:** The permanent currency (Steps) comes almost entirely from walking — the one in-game source is the small, daily-capped battle-step reward (§3.2). Cash (temporary, in-round currency) still comes from gameplay, preserving the in-round upgrade decisions.

### 2.3 Walking Encounters (Supply Drops)

Walking Encounters transform the real-world activity into a loot-driven experience by delivering rare rewards via push notifications as the player walks.

| Trigger | Notification Example | Reward |
|---|---|---|
| Every 2,000 steps (5% chance per 100 steps after threshold) | "A supply crate was spotted on your path! Tap to claim." | 50–200 bonus Steps or 1–3 Gems |
| Step burst (500+ steps in 5 min) | "Your pace is impressive! An energy surge flows into your ziggurat." | Bonus Steps |
| 10,000 step daily milestone | "10K steps! A rare supply drop has materialized." | Rare Card Pack or 5 Gems + 3 Power Stones |
| Random (1% per 500 steps) | "Something shimmers ahead on the trail..." | Random: Steps, Gems, Power Stones, or a Card Copy |

- Supply Drops are generated locally using a seeded random system based on step count and time of day.
- **GPS / location tracking removed in v1.0.** Original GDD draft proposed an "Exploration Mode" with GPS-based 1.5km distance triggers; this was dropped per ADR-0016 (battery, privacy, and Play Console review-cost trade-off). Reserved as a v2.x meta-progression concept that may ship alongside V1X-25 long-term progression.
- Players who disable notifications still accumulate drops in an "Unclaimed Supplies" inbox (max 10 stored).
- Drop rates average 2–4 per 10,000 steps.

---

## 3. Currency System

| Currency | Source | Persistence | Used For |
|---|---|---|---|
| **Steps** | Real-world walking (pedometer); + a small daily-capped battle-step reward for active play (§3.2) | Permanent | Workshop upgrades, Lab research, UW unlocks, Card purchases |
| **Cash** | Killing enemies + wave completion bonuses | Temporary (resets each round) | In-round upgrades |
| **Gems** | Daily login streaks, milestones, long-distance walking bonuses | Permanent | Card packs, Lab slot unlocks, Lab rush timers, cosmetics |
| **Power Stones** | Weekly walking challenges, wave milestones | Permanent | Unlocking and upgrading Ultimate Weapons |

### 3.1 Step Economy Tuning

| Player Profile | Daily Steps | Weekly Workshop Upgrades |
|---|---|---|
| Sedentary | 2,000–3,000 | 5–8 low-tier upgrades |
| Casual Walker | 5,000–7,000 | 15–25 upgrades across categories |
| Active Walker | 10,000–12,000 | 40–60 upgrades, reaching mid-tier costs |
| Power Walker | 15,000–20,000 | 80–120 upgrades, pushing into high-tier |
| Marathon Runner | 25,000+ | Deep investment into expensive upgrades |

**Tuning principle:** A casual walker (5k steps/day) should make meaningful progress every day. An active walker should feel proportionally rewarded through access to higher-tier upgrades.

### 3.2 Battle-Step Reward — the one in-game Step source

The **hard invariant is that Steps are never generated *passively*** (idle, timers, or purchase). The single deliberate exception is a small reward for *actively* playing battle rounds: each enemy kill grants a flat, per-type Step trickle (Basic/Fast/Scatter = 1, Ranged = 2, Tank = 3, Boss = 10), credited immediately on kill.

- **Daily cap: 2,000 Steps/day** (`AwardBattleSteps.DAILY_BATTLE_STEP_CAP`) — ~4% of the 50,000-step anti-cheat ceiling, so it can never rival walking as the primary source. It is a **separate** counter, never additive to (and never able to raise) the 50k walking ceiling.
- **Flat, never multiplied** — battle Steps ignore all in-round modifiers (Cash Bonus, Golden Ziggurat, cards, Lab multipliers) so the yield stays predictable for the cap logic and the anti-cheat audit trail.
- **Rationale:** rewards showing up and playing without letting battle farming displace or mask real walking. Full mechanics and enforcement (atomic per-day crediting, rollover, race safety) are in **ADR-0003** and `docs/steering/security-model.md` §3.

---

## 4. Workshop Upgrades (Permanent, Step-Funded)

The Workshop is the primary progression system. Upgrades are purchased with Steps and persist permanently across all rounds. Divided into three branches: Attack, Defense, and Utility.

### 4.1 Attack Upgrades

| Upgrade | Effect | Base Cost | Scaling | Max Level |
|---|---|---|---|---|
| Damage | +2% base damage per level | 50 Steps | ×1.12/level | Unlimited |
| Attack Speed | +1.5% attacks per second | 75 Steps | ×1.15/level | Unlimited |
| Critical Chance | +0.5% crit chance per level | 100 Steps | ×1.18/level | Cap 80% |
| Critical Factor | +0.1× crit multiplier per level | 120 Steps | ×1.18/level | Unlimited |
| Range | +2% attack radius | 80 Steps | ×1.14/level | Cap 300% |
| Multishot | +1 additional target per level | 5,000 Steps | ×1.5/level | Cap 11 targets (incl. baseline) |
| Bounce Shot | +1 bounce per level | 8,000 Steps | ×1.5/level | Cap 10 bounces |
| Damage/Meter | +1% bonus damage based on enemy distance | 200 Steps | ×1.16/level | Unlimited |
| Rapid Fire | Periodic attack-speed burst (60s/5s/2.0× → permanent/3.0× at max) | 2,000 Steps | ×1.18/level | 10 |

### 4.2 Defense Upgrades

| Upgrade | Effect | Base Cost | Scaling | Max Level |
|---|---|---|---|---|
| Health | +3% max ziggurat health | 50 Steps | ×1.12/level | Unlimited |
| Health Regen | +2% health regen per second | 60 Steps | ×1.13/level | Unlimited |
| Defense % | +0.3% damage reduction (diminishing) | 100 Steps | ×1.16/level | Cap 75% |
| Defense Absolute | +flat damage blocked per hit | 80 Steps | ×1.14/level | Unlimited |
| Knockback | +2% knockback force | 90 Steps | ×1.15/level | Unlimited |
| Thorn Damage | +1% damage reflected to attackers | 150 Steps | ×1.18/level | Unlimited |
| Orbs | Orbiting projectiles that damage nearby enemies | 800 Steps | ×1.22/level | Cap 6 orbs |
| Lifesteal | +0.2% of damage dealt returned as health | 200 Steps | ×1.20/level | Cap 15% |
| Death Defy | +1% chance to survive killing blow at 1 HP | 500 Steps | ×1.28/level | Cap 50% |

### 4.3 Utility Upgrades

| Upgrade | Effect | Base Cost | Scaling | Max Level |
|---|---|---|---|---|
| Cash Bonus | +3% cash earned during rounds | 40 Steps | ×1.11/level | Unlimited |
| Cash/Wave | +flat cash bonus at end of each wave | 60 Steps | ×1.13/level | Unlimited |
| Interest | +0.5% interest on held cash between waves | 150 Steps | ×1.18/level | Cap 10% |
| Free Upgrades | +1% chance for in-round upgrades to cost 0 cash | 200 Steps | ×1.20/level | Cap 25% |
| Recovery Packages | Periodic health restore drops during waves | 300 Steps | ×1.22/level | Unlimited |
| Step Multiplier | +1% bonus steps earned from walking | 1,000 Steps | ×1.35/level | Cap 100% |

**Step Multiplier** is unique to Steps of Babylon — investing steps to earn MORE steps. Early investment pays dividends over weeks and months.

---

## 5. In-Round Upgrades (Temporary, Cash-Funded)

During active rounds, players spend Cash on temporary upgrades that reset when the round ends. All Workshop stats have corresponding in-round upgrades that stack multiplicatively.

**Strategic tension:** Invest cash in Damage to kill faster and earn more cash, or shore up Health/Defense to survive longer? Rush Cash Bonus early for compound returns, or invest in combat stats?

### 5.1 Rapid Fire (Workshop, R4-03)

> **Step Overdrive was removed in R4-01** (the old once-per-round Assault/Fortress/Fortune/Surge
> step-burn buffs). Its tactical-burst role is now served by the **Rapid Fire** permanent
> Workshop upgrade — no mid-round Step burn exists in v1.0.

Rapid Fire (`UpgradeType.RAPID_FIRE`) fires a periodic attack-speed burst during a wave's
spawn phase. Every `interval` seconds the ziggurat's attack speed multiplies by
`multiplier` for `duration` seconds, then resets until the next pulse. Per-level values
interpolate L1 → L10 (`RapidFireSchedule`):

| Level | Interval | Burst Duration | Attack-Speed Multiplier |
|---|---|---|---|
| L1 | 60s | 5s | 2.0× |
| L10 | 30s | 30s | 3.0× (permanent — duration meets interval) |

At max level the burst re-triggers before it expires, becoming a continuous +3.0×
attack-speed buff. See `docs/battle-formulas.md` § Rapid Fire for the full interpolation.

---

## 6. Tier System (Difficulty Progression)

### 6.1 Tier Progression

| Tier | Unlock Requirement | Cash Multiplier | Battle Conditions |
|---|---|---|---|
| 1 | Starting tier | 1.0× | None |
| 2 | Wave 50 on Tier 1 | 1.8× | None |
| 3 | Wave 50 on Tier 2 | 2.6× | None |
| 4 | Wave 50 on Tier 3 | 3.4× | None |
| 5 | Wave 75 on Tier 4 | 4.2× | None |
| 6 | Wave 75 on Tier 5 | 5.0× | Enemies +10% speed |
| 7 | Wave 100 on Tier 6 | 6.0× | Orb Resistance Lv20, +15% enemy speed |
| 8 | Wave 100 on Tier 7 | 7.2× | Knockback Resistance Lv30, Armored Enemies Lv5 |
| 9 | Wave 100 on Tier 8 | 8.5× | Thorn Resistance Lv30, More Bosses (every 7 waves) |
| 10 | Wave 150 on Tier 9 | 10.0× | Full battle conditions |

### 6.2 Battle Conditions (Tier 6+)

- **Orb Resistance:** Orbs deal reduced % of enemy health
- **Knockback Resistance:** Knockback force reduced by X%
- **Armored Enemies:** Enemies block the first X hits
- **Thorn Resistance:** Reflected damage reduced by X%
- **More Bosses:** Boss enemies spawn every X waves instead of every 10
- **Enemy Speed:** All enemies move X% faster
- **Enemy Attack Speed:** Enemies attack X% faster once in range

### 6.3 Narrative Biome Progression

| Biome | Tiers | Environment | Enemy Theme | Tower Appearance |
|---|---|---|---|---|
| The Hanging Gardens | 1–3 | Lush terraced gardens, flowing water, date palms. Green/gold. | Garden guardians: clay golems, vine serpents, stone lions of Ishtar | Mud-brick ziggurat with verdant terraces |
| The Burning Sands | 4–6 | Vast desert, heat shimmer, sandstorms, distant ruins. Orange/amber. | Desert spirits: sand djinn, scorpion colossi, fire wraiths of Nergal | Fired-brick ziggurat with forge-lit braziers |
| The Frozen Ziggurats | 7–8 | Mountain plateaus, howling winds, snow-dusted ruins, aurora. Blue/white. | Frost ancients: ice-bound sentinels, blizzard spirits, crystal lamassu | Ice-encrusted ziggurat with crystalline blue-glazed tiers |
| The Underworld of Kur | 9–10 | Subterranean realm, bioluminescent cuneiform, River of the Dead. Purple/teal. | Underworld horrors: shadow demons of Ereshkigal, bone revenants, void spawn | Obsidian ziggurat with glowing protective wards |
| The Celestial Gate | 11+ | Floating temple platforms, reality tears, starfield. Multi-chromatic. | Celestial beings: star devourers, divine sentinels of Anu, chaos fragments of Tiamat | Celestial ziggurat crackling with divine energy |

Biome transitions trigger a cinematic showing the ziggurat "ascending" with total steps walked displayed. Each biome has a unique evolving soundtrack, cohesive enemy visual language, and unlocks a cosmetic ziggurat skin. *(Deferred — not in v1.0: the transition ships as a static text/gradient overlay (`BiomeTransitionOverlay`), not an animated cinematic, and there is no biome→cosmetic unlock — ziggurat skins are Store-purchase only. Animated cinematic + biome-linked unlocks are post-v1.0.)*

---

## 7. Ultimate Weapons

Powerful activatable abilities unlocked and upgraded with Power Stones. Players select up to 3 UWs per round.

| Ultimate Weapon | Effect | Unlock Cost | Visual |
|---|---|---|---|
| Death Wave | Massive damage pulse radiating outward, kills/damages all enemies on screen | 50 Power Stones | Green shockwave from ziggurat |
| Chain Lightning | Arcing electrical damage chaining between enemies | 75 Power Stones | Blue-white lightning arcs |
| Black Hole | Gravity well pulling enemies to a point with sustained damage | 100 Power Stones | Purple-black swirling vortex |
| Chrono Field | Slows all enemies to 10% speed for duration | 75 Power Stones | Blue time-distortion overlay |
| Poison Swamp | Toxic area dealing % max health damage/sec to grounded enemies | 60 Power Stones | Green bubbling ground effect |
| Golden Ziggurat | 5× cash earned + 50% damage boost for duration | 80 Power Stones | Ziggurat glows gold, coins rain |

### 7.1 Power Stone Acquisition

- **Weekly Step Challenge:** 50k steps/week = 10 PS. 75k = 20. 100k+ = 35.
- **Wave Milestones:** New personal-best wave records award 2–5 PS.
- **Daily Bonus:** Login + walk 1,000 steps = 1 PS/day.

---

## 8. Labs (Time-Gated Research)

Long-term research system using Steps to initiate and real time to complete. Players start with 1 Lab Slot (up to 4 via Gems).

| Research | Effect Per Level | Base Cost | Base Time | Max Level |
|---|---|---|---|---|
| Damage Research | +5% base damage multiplier | 2,000 Steps | 4 hours | 20 |
| Health Research | +5% max health multiplier | 2,000 Steps | 4 hours | 20 |
| Cash Research | +5% cash earned multiplier | 1,500 Steps | 3 hours | 20 |
| Step Efficiency | +2% bonus steps from walking | 5,000 Steps | 8 hours | 10 |
| Wave Skip | Start rounds at Wave X instead of Wave 1 | 10,000 Steps | 24 hours | 10 |
| Auto-Upgrade AI | *Coming soon (v1.x) — not yet implemented; hidden from the Labs UI (filtered out via `ResearchType.surfacedInLabs()`, `isComingSoon=true`).* Planned: auto-spends Cash on optimal upgrades during rounds | 8,000 Steps | 12 hours | 5 |
| UW Cooldown | -3% Ultimate Weapon cooldown | 4,000 Steps | 6 hours | 15 |
| Critical Research | +3% critical damage multiplier | 3,000 Steps | 5 hours | 15 |
| Regen Research | +4% health regen multiplier | 2,500 Steps | 4.5 hours | 15 |
| Enemy Intel | +2% damage per level; reveals next wave at L1, enemy HP at L5, boss timing at L10 (wired V1X-15b) | 8,000 Steps | 4 hours | 10 |
| Multishot Research | +1 multishot target per level | 5,000 Steps | 6 hours | 10 |
| Bounce Research | +1 projectile bounce per level | 8,000 Steps | 6 hours | 10 |

---

## 9. Simplified Cards

Temporary per-round bonuses activated at round start. Equip up to 3 Cards. Acquired from Card Packs (Gems). 3 rarities: Common, Rare, Epic. **Copy-based progression** (Card Dust was removed in R4-08, ADR-0010): duplicate pulls accumulate as additional copies, and copy count gates progression through **7 levels** (copies/level scale by rarity: 3 COMMON, 4 RARE, 5 EPIC).

| Card Name | Rarity | Effect (Lv1) | Effect (Lv7, max) |
|---|---|---|---|
| Iron Skin | Common | +10 Defense Absolute (flat) | +42 Defense Absolute (flat) |
| Sharp Shooter | Common | +15% Critical Chance | +45% Critical Chance |
| Cash Grab | Common | +20% Cash from kills | +65% Cash from kills |
| Vampiric Touch | Rare | +5% Lifesteal | +20% Lifesteal |
| Chain Reaction | Rare | +2 Bounce Shot targets | +5 Bounce Shot targets |
| Second Wind | Rare | Revive once at 50% HP | Revive once at 100% HP |
| Walking Fortress | Epic | +50% Health, -20% Attack Speed | +125% Health, -5% Attack Speed |
| Glass Cannon | Epic | +80% Damage, -40% Health | +140% Damage, -10% Health |
| Step Surge | Epic | Earn 2× Gems this round | Earn 5× Gems this round |

---

## 10. Enemy Types

| Enemy | Behavior | Counter Strategy |
|---|---|---|
| Basic | Normal speed, attacks on contact | General DPS |
| Fast | 2× speed, lower health, overwhelms in groups | Knockback, Multishot, area abilities |
| Tank | Slow, massive health, high damage | Sustained DPS, Damage/Meter |
| Ranged | Stops at distance, fires projectiles | Long Range upgrades, Chain Lightning UW |
| Boss | Every 10 waves, huge health, special abilities | Ultimate Weapons, high crit builds |
| Scatter | Splits into smaller enemies when killed | Area damage, Orbs, Bounce Shot |

---

## 11. Step Counter Technical Implementation

### 11.1 Sensor Stack

- **Primary:** Android `TYPE_STEP_COUNTER` hardware sensor (always-on, battery efficient)
- **Secondary:** Health Connect SDK for cross-validation and gap-filling
- **Tertiary:** `TYPE_STEP_DETECTOR` for realtime feedback (notifications, widget) *(Deferred — not implemented in v1.0; only `TYPE_STEP_COUNTER` (+ Health Connect) is used. See `docs/steering/` for the deferral note.)*

### 11.2 Background Service Architecture

- Foreground Service with persistent notification showing daily step count and balance
- WorkManager periodic sync (every 15 min) to reconcile with Health Connect
- Boot receiver to restart service after reboot
- Battery optimization whitelist request on first launch

### 11.3 Anti-Cheat Measures

| Measure | Implementation | Severity |
|---|---|---|
| Rate Limiting | Max 200 steps/min credited (bursts up to 250 for running) | Soft — excess discarded silently |
| Step Velocity Analysis | Detects constant rate (CV < 5%) and instant jumps (phone shakers/spoofers) | Medium — penalty multiplier (0.5× or 0.0×) |
| Health Connect Cross-Validation | Discrepancies >20% flagged, graduated response (4 offense levels) | Medium–Hard — escrow → cap → penalty |
| Activity Minute Validation | Filters extreme sessions (>4hr), micro-sessions (<2min), type flooding (>5/day) | Medium — sessions truncated/rejected |
| Overlap Deduction | Per-minute sensor step tracking prevents double-counting with activity minutes | Soft — only credits non-overlapping minutes |
| Daily Ceiling | Max 50,000 steps/day | Hard — prevents extreme exploits |

### 11.4 Activity Minute Parity (Indoor Workout Support)

**Conversion:** 1 Health Connect Active Minute = 100 Step-equivalents (~brisk walk pace).

| Activity | Source | Conversion | Daily Cap |
|---|---|---|---|
| Outdoor walking/running | TYPE_STEP_COUNTER | 1:1 (native steps) | 50,000 steps |
| Treadmill walking | TYPE_STEP_COUNTER or Health Connect | 1:1 | 50,000 steps |
| Stationary cycling | Health Connect Active Minutes | 1 min = 100 Step-eq | 10,000 Step-eq |
| Rowing machine | Health Connect Active Minutes | 1 min = 100 Step-eq | 10,000 Step-eq |
| Swimming | Health Connect Active Minutes | 1 min = 120 Step-eq | 12,000 Step-eq |
| Wheelchair propulsion | Health Connect Active Minutes | 1 min = 110 Step-eq | 11,000 Step-eq |
| Yoga / Stretching | Health Connect Active Minutes | 1 min = 50 Step-eq | 5,000 Step-eq |

Double-counting prevention: Step-equivalents only credited when step sensor records <50 steps/min.

---

## 12. UI/UX Design

### 12.1 Main Screens

- **Home / Dashboard:** Today's step count, total step balance, current tier/biome, best wave record. Quick-launch battle button. Biome-themed background. Unclaimed Supplies badge. Growing ziggurat illustration.
- **Battle Screen:** Full-screen real-time tower defense. Biome-themed battlefield. Health bar, wave counter, Cash upgrade tabs (Attack/Defense/Utility), UW buttons, speed controls (1×/2×/4×). (No Step Overdrive button — that mechanic was removed in R4-01.)
- **Workshop:** Three-tab layout (Attack/Defense/Utility). Shows level, effect, and step cost. Affordable upgrades highlighted green. "Quick Invest" button for recommended path.
- **Labs & Cards:** Split-view. Top: active Lab research with timers. Bottom: Card collection grid with rarity-colored borders and loadout selection.
- **Stats / History:** Walking history charts (daily/weekly/monthly), battle statistics, all-time stats.

### 12.2 Visual Style

Blends clean minimalist game aesthetic with ancient Mesopotamian art direction. Each biome drives the art with distinct color palettes, particle systems, and environmental design. Cuneiform-inspired UI elements, lapis lazuli accents, and Babylonian geometric motifs.

### 12.3 Notifications & Widgets

- **Persistent notification:** Step count and balance. Tap to open app.
- **Home screen widget (2×2):** Live step counter with mini ziggurat. Tap to battle.
- **Walking Encounter Supply Drops:** Rich notifications with thematic messages and one-tap claim.
- **Smart reminders:** "You're 2,000 steps away from upgrading Chain Lightning!"
- **Milestone alerts:** "New personal best! Wave 87 in The Burning Sands!"
- **Biome unlock cinematics:** "You have walked 168,000 steps to reach The Frozen Ziggurats." *(Deferred — v1.0 shows a static biome-transition overlay, not a cinematic; see §10/`BiomeTransitionOverlay`.)*

---

## 13. Monetization Strategy

Steps (core currency) can **never** be purchased with real money. This is a hard design rule.

| Category | Examples | Price Range | Gameplay Impact |
|---|---|---|---|
| Cosmetic Themes | Ziggurat skins, projectile effects, enemy skins | $0.99–$2.99 | None — purely visual |
| Gem Packs | Gems for Card Packs, Lab rushes, Lab slot unlocks | $0.99–$9.99 | Convenience only |
| Season Pass | 30-day pass: daily bonus Gems, exclusive cosmetics, 1 free Lab rush/day | $4.99/month | Mild convenience + cosmetic |
| Ad Removal | One-time purchase | $3.99 | Quality of life |

### 13.1 Optional Reward Ads

- Watch ad after round → 1 bonus Gem
- Watch ad → double post-round Power Stone rewards
- Watch ad → free Card Pack (once/day)

Ads are always optional and never interrupt gameplay or walking.

---

## 14. Expected Progression Timeline

Assumes ~8,000 steps/day.

| Timeframe | Steps Earned | Expected Progress |
|---|---|---|
| Day 1 | 8,000 | Tutorial complete. Workshop Lv3–5. Wave 15–20. Tier 1 (Hanging Gardens). |
| Week 1 | 56,000 | Workshop Lv15–25. 2 UWs unlocked. First Lab. Wave 50+. Tier 2. First Supply Drops. |
| Week 2–3 | 112k–168k | Workshop Lv30–50. 3 Lab slots. 3 Cards equipped. Tiers 4–5 (Burning Sands). Wave 80+. Rapid Fire leveled. |
| Month 1 | 240,000 | Deep Workshop investment. Step Multiplier active. Tier 5–6. Wave 100+. |
| Month 2–3 | 480k–720k | High-tier Workshop. Multiple UWs leveled. Full Card loadout. Tiers 7–8 (Frozen Ziggurats). |
| Month 6+ | 1,440,000+ | Endgame Workshop. Tiers 9+ (Underworld of Kur). Optimizing for battle conditions. |

---

## 15. Technical Architecture

### 15.1 Technology Stack

| Component | Technology | Rationale |
|---|---|---|
| Language | Kotlin | Modern Android standard, coroutines, strong type safety |
| Architecture | MVVM + Clean Architecture | Separation of concerns, testability |
| Game Engine | Custom Canvas/SurfaceView renderer | Lightweight, no heavy engine needed for 2D gameplay |
| Step Counting | Android Sensor API + Health Connect SDK | Reliable hardware-backed counting with cross-validation |
| Local Storage | Room Database (SQLite) | All game state stored locally. Offline-first. |
| Background Tasks | WorkManager + Foreground Service | Reliable step counting when backgrounded |
| UI Framework | Jetpack Compose (menus) + SurfaceView (battle) | Modern declarative UI + performant canvas rendering |
| Dependency Injection | Hilt | Standard Android DI |

### 15.2 Data Model (Core Entities)

- **PlayerProfile:** totalStepsEarned, currentStepBalance, currentTier, currentBiome, bestWavePerTier[], dailyStepHistory[], activityMinuteHistory[]
- **WorkshopState:** Map<UpgradeType, level> for all permanent upgrades
- **LabState:** activeResearch[], completedResearch Map<ResearchType, level>, labSlotCount
- **CardCollection:** ownedCards[] (each with copyCount), equippedCards[3]
- **UltimateWeaponState:** unlockedUWs[], per-path levels Map<UWType, {damage, secondary, cooldown}>, equippedUWs[3], powerStones (R4-06 per-path upgrades)
- **RoundState (transient):** currentWave, cash, tempUpgrades, towerHP, enemies[] *(Step Overdrive fields removed in R4-01)*
- **WalkingEncounterState:** unclaimedDrops[], dropHistory[]
- **ActivityTracker:** dailySteps, dailyActivityMinutes Map<ActivityType, minutes>, dailyStepEquivalents, lastSyncTimestamp

---

## 16. Milestones & Daily Missions

### 16.1 Walking Milestones

| Milestone | Requirement | Reward |
|---|---|---|
| First Steps | 1,000 total steps | 10 Gems + Tutorial Card Pack |
| Morning Jogger | 10,000 total steps | 25 Gems |
| Trail Blazer | 100,000 total steps | 50 Gems + Rare Card Pack |
| Marathon Walker | 500,000 total steps | 100 Gems + Epic Card Pack + Garden Ziggurat Skin |
| Iron Soles | 1,000,000 total steps | 200 Gems + 50 Power Stones + Lapis Lazuli Ziggurat Skin |
| Globe Trotter | 5,000,000 total steps | 500 Gems + Unique "Sandals of Gilgamesh" cosmetic |

### 16.2 Daily Missions

Three random daily missions refresh at midnight:

- **Walking:** "Walk 5,000 steps" (5 Gems), "Walk 12,000 steps" (10 Gems + 2 PS)
- **Battle:** "Reach Wave 30" (3 Gems), "Kill 500 enemies" (5 Gems)
- **Upgrade:** "Spend 5,000 Steps on Workshop" (2 Gems), "Complete a Lab research" (5 Gems)

---

## 17. Accessibility & Health Considerations

- **Wheelchair/mobility support:** Activity Minute Parity credits all forms of physical activity at fair rates.
- **Indoor workout parity:** Cycling, rowing, swimming earn Step-equivalents via Health Connect Active Minutes.
- **Rest day encouragement:** After 3+ consecutive 10k+ step days, suggests rest with a bonus Gem reward.
- **No punishment for inactivity:** Missing days never results in penalties or FOMO mechanics.
- **Color-blind modes (post-v1.0, planned — not in v1.0):** three CVD-safe palettes for common color vision deficiencies are planned as a tracked post-v1.0 deferral (issue #226). v1.0 does not ship a palette toggle; instead, no status is conveyed by color alone — every color-coded status also carries a shape, icon, or text label (e.g. the battle wave-phase bar is labelled with the phase name; currencies pair a tint with an icon + value).
- **Screen reader support:** All menus compatible with TalkBack. Battle screen provides audio cues.
- **Adjustable text size:** Respects Android system font size settings.

---

## 18. Future Expansion Possibilities

- **Guilds/Clans:** Walking groups with shared weekly goals
- **Leaderboards:** Weekly step count and wave record rankings (opt-in)
- **Tournaments:** Timed competitive events with standardized stats
- **Modules:** Equipment system with set bonuses
- **Bots:** Companion units (Lamassu Guardian, Storm Djinn, etc.)
- **Seasonal Events:** Holiday-themed waves with exclusive cosmetics
- **Biome Side Quests:** Challenge missions with unique modifiers
- **Map Hazards:** Environmental effects within biomes

---

## 19. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Step spoofing/cheating | High | Erodes integrity | Multi-layer anti-cheat. Solo-only limits impact. |
| Battery drain | Medium | User churn | Hardware step counter, minimize polling, low-priority notification |
| Player hits wall | Medium | Frustration | Step Multiplier, daily missions, lateral tier progression |
| OEM battery optimization kills service | High | Steps not counted | Whitelist prompt, WorkManager catch-up, Health Connect gap-filling |
| Step cost balancing | Medium | Too easy/grindy | Playtesting with varied profiles. Server-side tuning capability. |
| Activity Minute gaming | Medium | Inflated step-eq | Health Connect validation. Separate daily caps. Overlap deduction. |
| Supply Drop notification fatigue | Low | Players disable notifications | Conservative rates. Unclaimed inbox fallback. Customizable frequency. |
| Rapid Fire trivializing difficulty | Medium | Brute-force tiers | Per-level interpolation caps the burst; competes with other Attack upgrades for Steps. (Replaced the removed Step Overdrive risk.) |

---

*End of Design Document — GDD revision v1.1 (game build 1.0.9; see the header for the authoritative game version)*
