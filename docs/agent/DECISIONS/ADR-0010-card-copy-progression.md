# ADR-0010: Card Copy-Based Progression

**Status:** Accepted  
**Date:** 2026-05-24  
**Context:** R4-08 (Plan R4 Wave 3)  
**Supersedes:** The pre-R4-08 Card Dust upgrade model (Plan 17)

## Context

Internal-soak feedback (2026-05-22): *"Cards - scrap the dust mechanic, they should be
upgraded by getting more copies of the card, so 5(?) cards will upgrade to a higher level,
there should be maybe 7 levels in total."*

The dust system was unintuitive — players didn't understand why duplicates turned into an
abstract currency rather than directly strengthening the card.

## Decision

### 1. Copy-based upgrades replace Card Dust

Cards upgrade by collecting copies. Copies needed per level scales with rarity:
- COMMON: 3 copies per level
- RARE: 4 copies per level
- EPIC: 5 copies per level

### 2. Seven levels (was five)

`maxLevel` raised from 5 to 7. L7 values are linear extrapolation from the original
L1→L5 range extended two more steps (~30% stronger at max). SECOND_WIND is capped at
100% in `effectAtLevel` to prevent >100% HP recovery.

### 3. Duplicate cards → +1 copy

Opening a pack with a card you already own increments that card's `copyCount` instead of
awarding Card Dust. The `dustAwarded` field on `CardResult` is replaced by `copiesAwarded`.

### 4. First card guaranteed at pack-tier rarity

Opening a RARE pack guarantees the first of 3 cards is RARE rarity. The other 2 still
use standard weighted rolls. This makes pack purchases feel more predictable.

### 5. Supply drops: CARD_DUST → CARD_COPY

`SupplyDropReward.CARD_DUST` replaced by `CARD_COPY`. Claiming a CARD_COPY drop awards
1 copy of a random card type (seeded by `rewardAmount` as card index).

### 6. Schema migration v10→v11

- Add `copyCount INTEGER NOT NULL DEFAULT 1` column
- Aggregate duplicate rows by `cardType` (temp table dance: GROUP BY with COUNT(*) as copyCount)
- Add unique index on `cardType`
- Zero out `cardDust` balance (only developer's install affected)

### 7. Card Dust deprecation

`CardRarity.dustValue` and `upgradeDustPerLevel` kept as `@Deprecated` fields for one
release to avoid breaking any serialization. Deletion deferred to v2.x migration.

## Consequences

- Simpler mental model: "get more copies → card gets stronger"
- Higher max level (7 vs 5) gives more progression depth
- Card Dust UI removed from CardsScreen
- ~25 tests rewritten across use cases and ViewModels
- `UpgradeCard` no longer needs `PlayerRepository` (no currency to deduct)
- `ClaimSupplyDrop` now needs `CardRepository` (to credit copies)
