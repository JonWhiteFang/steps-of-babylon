# Steps of Babylon — Product Overview

Steps of Babylon is an Android idle tower defense game where real-world walking drives all progression. Players earn **Steps** by physically walking, then spend them to upgrade an ancient ziggurat that fights wave-based battles against mythic enemies.

## Core Loop

Walk → Earn Steps → Spend Steps on Workshop upgrades → Fight battles → Reach higher tiers → Repeat

## Currencies

| Currency | Source | Use |
|---|---|---|
| Steps | Real-world walking only | Workshop upgrades, Labs, Overdrive |
| Cash | Killing enemies in-round | In-round upgrades (resets each round) |
| Gems | Milestones, daily login | Card Packs, Lab rush |
| Power Stones | Weekly challenges, wave milestones | Ultimate Weapon unlock/upgrade |
| Card Dust | Duplicate card recycling | Card upgrades |

## Key Systems

- **Workshop** — 23 permanent upgrade types (Attack/Defense/Utility), purchased with Steps
- **Labs** — 10 research projects, Step cost + real-time duration (background timer)
- **Cards** — 9 types, 3 rarities, loadout of 3 max, acquired via Gem packs
- **Ultimate Weapons** — 6 types, loadout of 3 max, Power Stone gated
- **Step Overdrive** — 4 types, burns Steps for a 60s combat buff, once per round
- **Tiers** — 10+ difficulty levels with escalating battle conditions (Tier 6+)
- **Biomes** — 5 narrative environments tied to tier ranges

## Hard Design Rules

- Steps can **never** be generated passively in-game or purchased with real money
- Anti-cheat: 200 steps/min rate limit, 50,000 steps/day ceiling, Health Connect cross-validation
- Solo experience — no multiplayer, no server backend required for v1.0
- Monetization is cosmetic/convenience only (ads, IAP for Gems/cosmetics, Season Pass)
- Room database is the single source of truth for all game state
