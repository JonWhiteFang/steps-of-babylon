# Steps of Babylon — Product Overview

Steps of Babylon is an Android idle tower defense game where real-world walking drives all progression. Players earn **Steps** by physically walking, then spend them to upgrade an ancient ziggurat that fights wave-based battles against mythic enemies.

## Core Loop

Walk → Earn Steps → Spend Steps on Workshop upgrades → Fight battles → Reach higher tiers → Repeat

## Currencies

| Currency | Source | Use |
|---|---|---|
| Steps | Real-world walking only | Workshop upgrades, Labs, Card packs, UW unlocks |
| Cash | Killing enemies in-round | In-round upgrades (resets each round) |
| Gems | Milestones, daily login | Card Packs, Lab rush |
| Power Stones | Weekly challenges, wave milestones | Ultimate Weapon unlock/upgrade |

> Duplicate cards accumulate as **copies** (copy count gates 7-level upgrades) — the old "Card Dust"
> currency was removed in R4-08 (ADR-0010). A `cardDust` DB column persists at 0 for back-compat only.

## Key Systems

- **Workshop** — 24 permanent upgrade types (Attack/Defense/Utility), purchased with Steps
- **Labs** — research projects (12 `ResearchType` enum rows; 11 surfaced in the Labs UI, AUTO_UPGRADE_AI deferred to v1.x), Step cost + real-time duration (background timer)
- **Cards** — 9 types, 3 rarities, loadout of 3 max, acquired via Gem packs; copy-based 7-level upgrades
- **Ultimate Weapons** — 6 types, loadout of 3 max, Power Stone gated, per-path upgrades (R4-06)
- **Rapid Fire** — Workshop upgrade firing periodic mid-wave attack-speed bursts (R4-03; replaced the removed Step Overdrive)
- **Tiers** — 10+ difficulty levels with escalating battle conditions (Tier 6+)
- **Biomes** — 5 narrative environments tied to tier ranges

## Hard Design Rules

- Steps can **never** be generated passively in-game or purchased with real money
- Anti-cheat: 200 steps/min rate limit, 50,000 steps/day ceiling, Health Connect cross-validation
- Solo experience — no multiplayer, no server backend required for v1.0
- Monetization is cosmetic/convenience only (ads, IAP for Gems/cosmetics, Season Pass)
- Room database is the single source of truth for all game state
