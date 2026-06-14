# Look & Feel — Bundle D: Collectibles Rarity Visual System (UW + Card)

**Date:** 2026-06-14
**Issue:** #163
**Source review:** `docs/external-reviews/2026-06-12-look-and-feel-ux-review.md` (§4 HIGH Collectibles, §5 Weapons/Cards, §8 T10, §10 Remaining Rec 4)
**Predecessors:** #159 (design tokens / ActionBar removal, ADR-0022), #160 / PR #165 (Bundle A — de-emoji, loading/empty, a11y), #161 / PRs #166+#167 (Bundle B — back affordances, ADR-0023), #162 / PR #172 (Bundle C — haptics + celebrations + shared purchase pulse)
**Status:** Design approved; ready for implementation plan.

---

## 1. Goal & Scope

Bundle D is the **collectibles identity** wave of the 2026-06-12 look-and-feel review. The review's
finding (§4 HIGH): *"UWs have no rarity/visual identity; equipped-vs-owned + 3-cap is buried; card
rarity is thin generic blue/purple/gray."* This bundle gives both collectible screens — Ultimate
Weapons and Cards — a cohesive, **prominent** rarity identity, makes the EQUIPPED state unmistakable,
and surfaces the loadout cap.

It is **100% presentation-only and additive**. It introduces a small **rarity helper layer** in
`presentation/ui/` (the same shared-layer discipline as Bundles A/B/C — `CurrencyDisplay`,
`EmptyState`, `Haptics`, `PurchasePulse`) so the rarity treatment is defined once and composed into
each screen's existing card body, which differ substantially.

**In scope (this PR):**
1. **Rarity helper (greenfield).** New `presentation/ui/Rarity.kt` — a presentation-only `RarityTier`
   (3 tiers, one shared palette), pure tier-mapping + label functions for both Cards and UWs, and the
   shared Compose primitives (`RarityBadge`, `EquippedChip`, `Modifier.rarityBorder`).
2. **One theme token.** Add `RaritySand = Color(0xFFC2B280)` to `theme/Color.kt`. The other two tier
   colors already exist as tokens (`LapisLight`, `Gold`).
3. **Cards screen.** Route rarity through the shared helper (deleting the inline `rarityColor()`), add
   the prominent treatment (3dp border + left accent bar + filled pill badge), replace the implicit
   equipped tint with an explicit `EquippedChip`, and add the header cap hint.
4. **Weapons screen.** Add the same treatment + a `RarityBadge` derived from `unlockCost`, replace the
   tiny green `CheckCircle` with `EquippedChip`, dim locked UWs while still showing their rarity, and
   add the header cap hint. The per-path upgrade rows are untouched.

**Explicitly out of scope (tracked elsewhere — do NOT do here):**
- **Pack-open dialog redesign.** The Cards pack result is still a plain `AlertDialog` (review §5). It
  *will* automatically adopt the new shared rarity color (it calls the same helper), but its **layout
  is not redesigned** here — not one of #163's three bullets.
- **Any rarity *gameplay* meaning** — drop-rate display, Card Dust (removed R4-08), etc. Rarity here is
  purely a visual classification.
- **Custom font + onboarding per-slide theming + real ziggurat asset** → #164 (Bundle E).
- **Reward SFX / audio sting** — blocked on the placeholder-audio debt track.
- **Any change to the battle renderer/engine/effects, economy, concurrency, loadout logic, domain
  models, or `Screen.kt` routes.** The 3-cap is already enforced in `domain/`; we only *surface* it.

**Risk:** Low. Confined to `presentation/` (+ one `theme/Color.kt` token). Zero domain / economy /
loadout-logic / concurrency / renderer files. The two ViewModels are **not** touched. Respects every
STATE.md fragile zone.

---

## 2. Ground Truth (verified against current `HEAD`, post-v1.0.6)

The design rests on these verified facts, not the review's prose:

| Fact | Evidence |
|---|---|
| **Cards have a real rarity model.** `CardRarity` = `COMMON / RARE / EPIC` (a 3-value enum; `copiesPerLevel` 3/4/5; the `dustValue`/`upgradeDustPerLevel` fields are `@Deprecated` R4-08 carry-overs). | `domain/model/CardRarity.kt:3-13`. |
| **Card rarity is rendered today** via a private `rarityColor(CardRarity)` in `CardsScreen` (COMMON `#B8B0A0`, RARE `#6FA8DC`, EPIC `#B57EDC`), used for a **2dp** `BorderStroke`, a rarity-name text label, and the pack-dialog icon/label tints. Equipped state = a `primaryContainer` **background tint** (no chip). | `CardsScreen.kt:167` (border), `:177` (label), `:139-147` (dialog), `:207-211` (colors), `:168-169` (equipped tint). |
| **EPIC's color `#B57EDC` is identical to the Power-Stone currency color `PowerStoneColor`.** A latent palette collision — Epic cards and the Power-Stone glyph read as the same "thing." Bundle D's Epic→Gold (tier2) **removes** this collision. | `CardsScreen.kt:210` vs `theme/Color.kt:49`. |
| **UWs have NO rarity dimension.** `UltimateWeaponType` is 6 unique types carrying `unlockCost`, `description`, and per-path balance endpoints — no rarity enum, no accent color. Equipped = a tiny green `Icons.Filled.CheckCircle` (`#4CAF50`). | `domain/model/UltimateWeaponType.kt:24-108`; `UltimateWeaponScreen.kt:103-109`. |
| **The 6 UW `unlockCost` values** are: DEATH_WAVE 50, POISON_SWAMP 60, CHAIN_LIGHTNING 75, CHRONO_FIELD 75, GOLDEN_ZIGGURAT 80, BLACK_HOLE 100. They cluster into three bands: **50–60 / 75–80 / 100**. | `UltimateWeaponType.kt:42,53,64,75,87,98`. |
| **Both screens already carry the data needed.** `CardDisplayInfo.type` (→ `.rarity`) and `UWDisplayInfo.type` (→ `.unlockCost`) are present on the existing display-info classes. Rarity is **derivable in the composable** — no ViewModel/state change. | `cards/CardsUiState.kt:7-17`; `weapons/UltimateWeaponViewModel.kt:35-41`. |
| **Loadout cap is domain-enforced and already shown numerically.** Both screens print `"Equipped: N/3"`; the equip control is `enabled = equippedCount < 3` (Cards) / `isEquipped || canEquipMore` (UWs). No logic change is needed to add a cap hint — only the header string + color. | `UltimateWeaponScreen.kt:50-55,143`; `CardsScreen.kt:73`, `CardItem` `:186`. |
| **`presentation/ui/` shared-layer convention.** Granular drop-in helpers, each one concern; pure helpers are JVM-unit-tested (`EnumDisplayNameTest`, `CurrencyDisplayTest`), `@Composable` pieces are not. `String.toDisplayName()` already formats `UPPER_SNAKE` → "Title Case". | `presentation/ui/{EnumDisplayName,CurrencyDisplay,EmptyState,Haptics,PurchasePulse}.kt`; `presentation/ui/EnumDisplayNameTest.kt`. |
| **Theme tokens** already include `Gold = #D4A843` and `LapisLight = #A7C7E7` (the latter tuned to ~5.3:1 on the `DeepBronze` surface). Only sand `#C2B280` (= existing `SandStone`, but see D-note) needs a rarity-named token. | `theme/Color.kt:7,24,9`. |

**Audit corrections vs. the review** (recorded so a future reviewer doesn't re-raise):

| Review claim | Reality (verified) |
|---|---|
| "Per-rarity visual identity **for Ultimate Weapons**" | UWs have **no rarity dimension** in the domain. Bundle D *derives* a presentation-only tier from `unlockCost` (D1); it does **not** add a rarity field to the domain model. |
| Card rarity is "thin generic blue/purple/gray" | Accurate — 2dp border, COMMON is grey `#B8B0A0`. Bundle D thickens to 3dp + accent bar + filled badge and re-tunes the palette. |
| (implicit) all three screens share one rarity scale named identically | The shared *palette* is identical; the **labels shift per screen** (Cards: COMMON/RARE/EPIC; UWs: RARE/EPIC/LEGENDARY) because no UW is "common" (D2). |

---

## 3. Decisions (locked during brainstorming)

| # | Decision | Choice | Driver |
|---|---|---|---|
| D1 | UW rarity model | **Derive tier from `unlockCost`** (presentation-only `when`), no domain change. | User pick. UWs have no rarity field; the issue forbids economy change. Cost is the only intrinsic "value" axis already present. |
| D2 | Shared scale + naming | **One 3-color palette; names shift per screen.** Cards: COMMON/RARE/EPIC. UWs: RARE/EPIC/LEGENDARY (same three colors). | User pick. Every UW is a premium Power-Stone unlock — none is "Common" — but Cards genuinely range from Common up. Shared palette keeps the two screens visually unified. |
| D3 | Palette | **Full brand-aligned ramp: sand `#C2B280` → sky-lapis `#A7C7E7` → gold `#D4A843`.** | User pick (option C). A warm→cool→warm climb reading as a clear rarity ladder; two of three are existing brand tokens (`LapisLight`, `Gold`). Also resolves the Epic/Power-Stone amethyst collision (§2). |
| D4 | Treatment intensity | **Prominent (option B):** 3dp rarity border + left accent bar + filled rarity pill badge + filled EQUIPPED chip. **No** background tint / outer glow. | User pick. Unmistakable upgrade over the tiny ✓ + thin border, without the visual noise a tinted/glowing card causes across a long scrolling collection. |
| D5 | Cap messaging | **Header hint only.** When `equipped == 3`, the `"Equipped: 3/3"` header turns a warning color and appends `" — unequip one to swap"`. Equip buttons stay disabled as today. | User pick. Matches the issue wording exactly; one clear message per screen, no per-card clutter. |
| D6 | Locked UWs | **Show rarity even when locked** (border + badge, dimmed). | User pick. Rarity is intrinsic to the weapon, not earned by unlocking; the dimmed-but-coloured locked card is an aspirational pull to spend Power Stones. |
| D7 | Sharing mechanism | **Shared primitives** (`RarityBadge`, `EquippedChip`, `Modifier.rarityBorder`), each screen composes them into its own `Card`. NOT a single `CollectibleCard` scaffold. | User pick. Matches the granular `presentation/ui/` style; the two card bodies (UW per-path rows vs Card level/copies) differ too much for one scaffold without parameter bloat. |
| D8 | UW tier boundaries | **Range-based, not exact-value:** `unlockCost ≤ 60 → TIER_0`, `61..89 → TIER_1`, `≥ 90 → TIER_2`. | A re-priced or 7th UW landing off today's exact values (50/60/75/80/100) still gets a sane tier; an exhaustiveness test pins today's six. |
| D9 | Implementation structure | **Shared helper first** (`Rarity.kt` + token), **then** wire both screens. One PR. | Matches Bundles A/B/C. |
| D10 | `RaritySand` token vs reuse `SandStone` | **New `RaritySand` token** (even though its value equals `SandStone #C2B280`). | A semantically-named token lets the rarity ramp be re-tuned independently of the brand `SandStone` without a surprise ripple. Mirrors how currency colors got their own tokens (§Color.kt:43-51). |

---

## 4. Architecture — the new shared rarity layer

One new file, one token, two screens edited. **No ViewModel / domain / state change.**

### 4.1 `presentation/ui/Rarity.kt` — single source of truth

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

/** Presentation-only rarity tier. The colour carrier shared by Cards and Ultimate Weapons.
 *  NOT a domain concept — UWs have no rarity field; this is derived in the UI (see [uwRarityTier]). */
enum class RarityTier { TIER_0, TIER_1, TIER_2 }

/** Tier → theme colour token. Sand / sky-lapis / gold ramp (D3). */
@Composable
fun RarityTier.color(): Color = when (this) {
    RarityTier.TIER_0 -> RaritySand     // #C2B280
    RarityTier.TIER_1 -> LapisLight      // #A7C7E7 (existing token)
    RarityTier.TIER_2 -> Gold            // #D4A843 (existing token)
}

/** Card rarity → tier. Exhaustive over CardRarity. */
fun cardRarityTier(rarity: CardRarity): RarityTier = when (rarity) {
    CardRarity.COMMON -> RarityTier.TIER_0
    CardRarity.RARE   -> RarityTier.TIER_1
    CardRarity.EPIC   -> RarityTier.TIER_2
}

/** UW unlock cost → tier. Range-based catch-all (D8) so a re-price/7th UW still tiers sanely. */
fun uwRarityTier(unlockCost: Int): RarityTier = when {
    unlockCost <= 60 -> RarityTier.TIER_0
    unlockCost <= 89 -> RarityTier.TIER_1
    else             -> RarityTier.TIER_2
}

/** Per-screen labels (D2). */
fun cardRarityLabel(rarity: CardRarity): String = rarity.name          // COMMON / RARE / EPIC
fun uwRarityLabel(tier: RarityTier): String = when (tier) {
    RarityTier.TIER_0 -> "RARE"
    RarityTier.TIER_1 -> "EPIC"
    RarityTier.TIER_2 -> "LEGENDARY"
}

/** Filled pill badge in the tier colour. */
@Composable fun RarityBadge(tier: RarityTier, label: String) { /* … */ }

/** Filled "✓ EQUIPPED" chip (StatusSuccess token). */
@Composable fun EquippedChip() { /* … */ }

/** 3dp border + left accent bar in the tier colour. */
fun Modifier.rarityBorder(tier: RarityTier): Modifier  // (reads tier colour via composition or a passed Color)
```

- **What/why/depends:** the pure functions (`cardRarityTier`, `uwRarityTier`, the two label fns) carry
  all the logic and are JVM-unit-tested. The `@Composable` pieces are dumb renderers depending only on
  Compose + theme tokens. No new dependency (Material3 + theme already present).
- **Exhaustiveness:** `cardRarityTier` / `cardRarityLabel` use exhaustive `when` over `CardRarity`
  (compiler-enforced). `uwRarityTier` uses ranges so it total-functions over any `Int`.

> **Note for the planner — `rarityBorder` colour access.** `Modifier.color()` is `@Composable`; a
> `Modifier` extension is not. Resolve the tier colour at the call-site (`val c = tier.color()`) and
> pass it into `rarityBorder(color = c)` (or implement the border via `Modifier.drawBehind`/`border`
> inside a composable that has the colour). Pin the exact signature in the plan; do not leave it as a
> bare `Modifier.rarityBorder(tier)` if that forces a `@Composable` modifier.

### 4.2 `theme/Color.kt` — one new token

```kotlin
/** Rarity tier-0 (lowest). Warm sandstone; value matches SandStone but named for the rarity ramp
 *  so the ladder can be re-tuned independently of the brand fill colour (D10). */
val RaritySand = Color(0xFFC2B280)
```
(`LapisLight` and `Gold` already serve tier-1 / tier-2.)

### 4.3 `CardsScreen.kt` edits
- **Delete** the private `rarityColor()` (lines 207–211). Route through `cardRarityTier(...).color()`.
- `CardItem`: border `2.dp → rarityBorder(tier)` (3dp + accent bar); add `RarityBadge(tier,
  cardRarityLabel(...))` in the header; replace the implicit `primaryContainer` equipped tint with an
  explicit `EquippedChip` in the header when `card.isEquipped` (keep or drop the subtle tint per the
  plan — chip is the authoritative signal now).
- Header: when `equippedCount == 3`, render the cap hint (warning color + " — unequip one to swap").
- The pack-result dialog keeps its layout but its `rarityColor(r.type.rarity)` calls become
  `cardRarityTier(r.type.rarity).color()` (same call-through; Epic now reads gold).

### 4.4 `UltimateWeaponScreen.kt` edits
- `UWCard`: derive `val tier = uwRarityTier(info.type.unlockCost)`; apply `rarityBorder(tier)`; add
  `RarityBadge(tier, uwRarityLabel(tier))` in the header row; **replace** the `CheckCircle` (lines
  103–109) with `EquippedChip` when `info.isEquipped`.
- **Locked UWs (D6):** keep the existing dimmed container but still draw the rarity border + badge
  (dimmed alpha). The Unlock button row is unchanged.
- Header: when `equippedCount == 3`, render the cap hint (same treatment as Cards).
- Per-path upgrade rows (`UWPathRow`) and all unlock/upgrade/equip logic: **untouched.**

### 4.5 Unchanged
`CardsViewModel`, `UltimateWeaponViewModel`, `CardsUiState`, `UWDisplayInfo`, every use case, every
DAO, every domain model. Rarity is derived in the composable from `type.rarity` / `type.unlockCost`
already present on the display info.

---

## 5. Testing

New `presentation/ui/RarityTest.kt` — pure JVM (JUnit Jupiter), mirroring `EnumDisplayNameTest`:

1. **`uwRarityTier` over all 6 `UltimateWeaponType.entries`** by their real `unlockCost` → expected
   tier (DEATH_WAVE/POISON_SWAMP = TIER_0; CHAIN_LIGHTNING/CHRONO_FIELD/GOLDEN_ZIGGURAT = TIER_1;
   BLACK_HOLE = TIER_2). This is the drift guard: a re-priced/added UW that lands in an unexpected band
   fails here. Iterate `UltimateWeaponType.entries` (don't hard-code 6) so a 7th type forces a review.
2. **`cardRarityTier` exhaustive over all 3 `CardRarity.entries`** → expected tier.
3. **Both label functions** over every tier / rarity → expected string (incl. UW TIER_0 → "RARE",
   never "COMMON").
4. **Distinctness guard:** the three tiers map to three *distinct* colours is asserted indirectly via
   the tier→token mapping being injective (kept as a pure `tierColorName(tier)` test helper or asserted
   on the `Color` values if the test has Compose access; if `@Composable color()` isn't JVM-reachable,
   assert distinctness on a parallel pure `tierToken(tier)` mapping the composable delegates to).

The `@Composable` pieces (`RarityBadge`, `EquippedChip`, `rarityBorder`) are visual — verified by the
on-device feel sign-off, consistent with how Bundle C's pulse/celebration visuals were handled (not
unit-tested). Existing `CardsViewModelTest` / `UltimateWeaponViewModelTest` stay green untouched.

**Headline count:** ~990 JVM → ~990 + N (the `RarityTest` cases). Update `CLAUDE.md`'s headline line +
`CHANGELOG.md` when the exact count lands.

---

## 6. Delivery

One PR, on a `feat/163-look-and-feel-bundle-d` branch. Build order: (1) `RaritySand` token + `Rarity.kt`
+ `RarityTest.kt`; (2) wire `CardsScreen`; (3) wire `UltimateWeaponScreen`; (4) doc sweep
(`CLAUDE.md` headline count, `CHANGELOG.md`, `docs/steering/source-files.md` for the new
`presentation/ui/Rarity.kt`, STATE/RUN_LOG per the PR Task-List Convention). Gate: `./run-gradle.sh
testDebugUnitTest lintDebug assembleDebug` green, then on-device feel sign-off (rarity reads at a
glance; EQUIPPED chip unmistakable; cap hint appears at 3/3; locked UWs show dimmed rarity).
```

