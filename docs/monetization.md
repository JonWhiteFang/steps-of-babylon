# Monetization

## Hard Rule

Steps can **never** be purchased with real money. This is non-negotiable. All monetization is cosmetic or convenience.

## Revenue Streams

### In-App Purchases

| Product | Price | Type | What Player Gets |
|---|---|---|---|
| Gem Pack (Small) | $0.99 | Consumable | 50 Gems |
| Gem Pack (Medium) | $4.99 | Consumable | 300 Gems |
| Gem Pack (Large) | $9.99 | Consumable | 700 Gems |
| Ad Removal | $3.99 | One-time | No more optional ads |
| Season Pass | $4.99/month | Subscription | Daily bonus Gems, exclusive cosmetics, 1 free Lab rush/day |

### Cosmetics

| Category | Price Range | Examples |
|---|---|---|
| Ziggurat Skins | $0.99–$2.99 | Obsidian, Crystal, Golden, seasonal themes |
| Projectile Effects | $0.99–$1.99 | Fire trails, lightning arcs, star particles |
| Enemy Skins | $0.99 | Reskinned enemy types (cosmetic only) |

### Optional Reward Ads

| Trigger | Reward | Frequency |
|---|---|---|
| Post-round | 1 bonus Gem | After each round |
| Post-round | Double Power Stone rewards | After each round |
| Daily | Free Card Pack | Once per day |

Ads are always optional. They never interrupt gameplay or walking. No forced ads ever.

## Gem Economy

Gems are the bridge currency between free and paid:

- Free sources: daily login streaks, walking milestones, daily missions
- Paid source: Gem Packs (IAP)
- Spent on: Card Packs, Lab slot unlocks (up to 4), Lab rush timers, cosmetics

### Gem Earning Rate (Free)

| Source | Amount | Frequency |
|---|---|---|
| Daily login | 1–5 Gems | Daily (streak scaling) |
| Daily missions (3/day) | 2–10 Gems each | Daily |
| Walking milestones | 10–500 Gems | One-time |
| Supply Drops | 1–3 Gems | Per drop |
| Post-round ad | 1 Gem | Per round |

Estimated free Gem income: ~15–30/day for an active player.

### Gem Spending

| Item | Cost | Notes |
|---|---|---|
| Card Pack (Common) | 50 Gems | 3 cards, mostly Common |
| Card Pack (Rare) | 150 Gems | 3 cards, guaranteed 1+ Rare |
| Card Pack (Epic) | 500 Gems | 3 cards, guaranteed 1 Epic |
| Lab Slot Unlock | 200 Gems | Slots 2–4 |
| Lab Rush (instant complete) | 50–200 Gems | Scales with remaining time |

## Season Pass

Monthly subscription ($4.99):
- 10 bonus Gems/day
- Exclusive cosmetic each month
- 1 free Lab rush per day
- Season Pass badge on profile

No gameplay advantages beyond convenience. A non-subscriber can earn everything through play.

## Design Principles

- No pay-to-win. Steps are effort-gated, not money-gated.
- No loot boxes with real money — Card Packs use Gems (earnable for free).
- No energy systems or play-gating.
- No FOMO mechanics — missing a day has zero penalty.
- Cosmetics are the primary revenue driver.
- Ads are opt-in rewards, never interruptions.

## Implementation Status

### Architecture

Billing and ad functionality use interface-based abstractions in the domain layer (pure Kotlin), with concrete real-SDK implementations in the data layer behind SDK-neutral adapter seams so unit tests never import `com.android.billingclient.*` or `com.google.android.gms.ads.*`.

| Interface | Implementation | Location |
|---|---|---|
| `BillingManager` | `BillingManagerImpl` (Google Play Billing v8) | `data/billing/` |
| `RewardAdManager` | `RewardAdManagerImpl` (Google Mobile Ads v25 + UMP v4) | `data/ads/` |

DI bindings in `di/BillingModule.kt` and `di/AdModule.kt` are plain `@Binds` (post-C.5 PR 3 / C.6 PR 3 — the `StubBillingManager` and `StubRewardAdManager` were deleted after on-device verification on the internal track).

Adapter seams (`data/billing/internal/BillingClientAdapter` + `data/ads/internal/RewardedAdAdapter` + `data/ads/internal/ConsentManager`) keep SDK imports out of unit tests; tests mock the adapter interfaces directly.

### What's Implemented (Real SDK)

- Gem Pack purchases (3 tiers) — real Play Billing v8 consumable purchases; wallet credit + receipt grant happen atomically inside `BillingReceiptDao.grantOnceAtomic`; `consumeAsync` runs after the transaction commits with retry on the next reconciliation sweep
- Ad Removal — real Play Billing v8 non-consumable; `acknowledgePurchaseAsync` finalizes the purchase
- Season Pass — real Play Billing v8 monthly subscription; `purchaseTime + 30 days` expiry rule; reconciliation sweep refreshes expiry on Play Store re-delivery; awards 10 bonus Gems/day via TrackDailyLogin, 1 free Lab rush/day
- Idempotency — `BillingReceiptDao` table keyed by `purchaseToken`; `granted = true` row guarantees the wallet is credited at most once even across crashes / `PENDING → PURCHASED` transitions / repeat reconciliation sweeps
- Post-round reward ads — real AdMob rewarded ads with UMP consent gating; placement-aware ad-unit routing (`POST_ROUND_GEM` / `POST_ROUND_DOUBLE_PS` / `DAILY_FREE_CARD_PACK`)
- UMP consent — prefetched on `MainActivity.onResume` in release builds (gated by `BuildConfig.USE_REAL_ADS`) so the first reward-ad tap doesn't pay the ~200–500 ms UMP init latency
- Cosmetic store — 4 cosmetics with full visual application end-to-end (`zig_jade`, `lapis_lazuli_skin`, `garden_ziggurat_skin`, `sandals_of_gilgamesh`); 7 additional seeds with placeholder visuals pending content

### What's Out-of-Scope for v1

- Server-side receipt verification (forbidden by `CONSTRAINTS.md` for v1.0 — no backend).
- Real-time subscription renewal notifications (would require Real-time Developer Notifications + a backend; Season Pass relies on the reconciliation sweep refreshing expiry on Play Store re-delivery instead).
- Ad mediation for fill rate optimization (deferred until live AdMob fill data justifies the integration cost).
- Live formatted-price display from `ProductDetails.priceDisplay` (currently the Store screen uses static `BillingProduct.<X>.priceDisplay` constants that have to be kept in sync with Play Console pricing manually — a v1.x candidate refactor).
