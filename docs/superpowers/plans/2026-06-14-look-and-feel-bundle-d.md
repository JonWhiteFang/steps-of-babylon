# Look & Feel Bundle D — Collectibles Rarity Visual System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Ultimate Weapons and Cards a cohesive, prominent rarity identity (shared 3-tier palette, filled badge, accent border), an unmistakable EQUIPPED chip, and a loadout-cap hint — 100% presentation-only, zero domain/economy/loadout change.

**Architecture:** One new shared helper `presentation/ui/Rarity.kt` (a `RarityTier` enum + pure mapping/label/colour functions + Compose primitives `RarityBadge`/`EquippedChip`/`Modifier.rarityBorder`), one new theme token `RaritySand`, and edits to the two collectible screens that compose these primitives into their existing `Card`s. Rarity is derived in the composable from data already on the display-info classes (`CardType.rarity`, `UltimateWeaponType.unlockCost`) — the ViewModels are untouched.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt (no new wiring), JUnit Jupiter for the pure-JVM test. Build/test via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-d-design.md` (commits `6ad0ce5` + `1b3994e`, adversarial-review-hardened — see spec §7).

---

## File Structure

| File | Create/Modify | Responsibility |
|---|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Color.kt` | Modify (append) | Add `RaritySand` tier-0 token. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Rarity.kt` | Create | Single source of truth: `RarityTier`, `color()`, `cardRarityTier`, `uwRarityTier`, label fns, `RarityBadge`, `EquippedChip`, `Modifier.rarityBorder`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/RarityTest.kt` | Create | Pure-JVM unit tests for the mapping/label/colour functions. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt` | Modify | Route rarity through the shared helper; prominent treatment; EquippedChip; cap hint. Delete inline `rarityColor()` + dead import. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt` | Modify | Rarity border + badge from `unlockCost`; EquippedChip (replace `CheckCircle`); dimmed-but-coloured locked UWs; cap hint. |
| `CLAUDE.md`, `CHANGELOG.md`, `docs/steering/source-files.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md` | Modify | Doc sweep per the PR Task-List Convention. |

**Build order rationale:** the shared helper + its test (Tasks 1–2) land first so both screens (Tasks 3–4) compile against a tested API. Docs (Task 5) then STATE/RUN_LOG (Task 6) close out per CLAUDE.md's PR Task-List Convention.

> **Conventions to honour (verified against HEAD):**
> - Pure helpers in `presentation/ui/` are JVM-unit-tested; `@Composable` pieces are not (`EnumDisplayName` + `EnumDisplayNameTest`, `CurrencyDisplay` + `CurrencyDisplayTest` which deliberately skips the `@Composable tint()`).
> - `color()` is a **plain `fun`**, NOT `@Composable` (spec §4.1, review finding `rarity-color-needless-composable`) — it reads only top-level `val Color` tokens, so it is JVM-testable and callable from a `Modifier` extension.
> - Tests iterate `.entries` (never hard-code counts) — see `EnumDisplayNameTest` and the balance tests.
> - `Modifier` extension idiom: see `PurchasePulse.kt:54` (`fun Modifier.pulseScale(...): Modifier = this.graphicsLayer(...)`).

---

## Task 1: Add the `RaritySand` theme token

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Color.kt` (append at end)

No test (a single colour constant; exercised by Task 2's distinctness test).

- [ ] **Step 1: Append the token**

Add to the very end of `Color.kt` (after the `StepColor` line):

```kotlin

// --- Rarity ramp --------------------------------------------------------------------------------
// Shared 3-tier collectibles rarity palette (Bundle D, #163). Tier-1 reuses LapisLight, tier-2 reuses
// Gold; only tier-0 needs a new token. Named for the rarity ramp (not reusing SandStone) so the
// ladder can be re-tuned independently of the brand fill colour (spec D10).
/** Rarity tier-0 (lowest): COMMON cards / RARE UWs. Warm sandstone, light-on-bronze legible. */
val RaritySand = Color(0xFFC2B280)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Color.kt
git commit -m "feat(#163): add RaritySand rarity tier-0 token"
```

---

## Task 2: Create `Rarity.kt` + its tests (TDD)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Rarity.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/RarityTest.kt`

The pure functions (`color`, `cardRarityTier`, `uwRarityTier`, `cardRarityLabel`, `uwRarityLabel`) are TDD'd here. The `@Composable` pieces (`RarityBadge`, `EquippedChip`, `Modifier.rarityBorder`) are added in Step 5 (visual; not unit-tested — verified on-device, per spec §5).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/RarityTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guards for the Bundle D (#163) rarity helper. Covers the mapping/label/colour functions
 * (the @Composable RarityBadge/EquippedChip/rarityBorder are visual, verified on-device per spec §5).
 */
class RarityTest {

    @Test
    fun `uw unlock cost maps to the expected tier for every weapon`() {
        // Iterate entries (not a hard-coded 6) so a re-priced or 7th UW forces a review here.
        val expected = mapOf(
            UltimateWeaponType.DEATH_WAVE to RarityTier.TIER_0,      // 50
            UltimateWeaponType.POISON_SWAMP to RarityTier.TIER_0,    // 60
            UltimateWeaponType.CHAIN_LIGHTNING to RarityTier.TIER_1, // 75
            UltimateWeaponType.CHRONO_FIELD to RarityTier.TIER_1,    // 75
            UltimateWeaponType.GOLDEN_ZIGGURAT to RarityTier.TIER_1, // 80
            UltimateWeaponType.BLACK_HOLE to RarityTier.TIER_2,      // 100
        )
        for (type in UltimateWeaponType.entries) {
            assertEquals(
                expected[type],
                uwRarityTier(type.unlockCost),
                "tier drift for $type (unlockCost=${type.unlockCost})",
            )
        }
    }

    @Test
    fun `uw tier boundaries are range-based (catch-all for re-prices)`() {
        assertEquals(RarityTier.TIER_0, uwRarityTier(60))
        assertEquals(RarityTier.TIER_1, uwRarityTier(61))
        assertEquals(RarityTier.TIER_1, uwRarityTier(89))
        assertEquals(RarityTier.TIER_2, uwRarityTier(90))
        assertEquals(RarityTier.TIER_0, uwRarityTier(0))     // below all → lowest
        assertEquals(RarityTier.TIER_2, uwRarityTier(9999))  // above all → highest
    }

    @Test
    fun `card rarity maps to the expected tier for every rarity`() {
        val expected = mapOf(
            CardRarity.COMMON to RarityTier.TIER_0,
            CardRarity.RARE to RarityTier.TIER_1,
            CardRarity.EPIC to RarityTier.TIER_2,
        )
        for (rarity in CardRarity.entries) {
            assertEquals(expected[rarity], cardRarityTier(rarity), "tier drift for $rarity")
        }
    }

    @Test
    fun `uw labels never say COMMON`() {
        assertEquals("RARE", uwRarityLabel(RarityTier.TIER_0))
        assertEquals("EPIC", uwRarityLabel(RarityTier.TIER_1))
        assertEquals("LEGENDARY", uwRarityLabel(RarityTier.TIER_2))
    }

    @Test
    fun `card labels are the rarity name`() {
        assertEquals("COMMON", cardRarityLabel(CardRarity.COMMON))
        assertEquals("RARE", cardRarityLabel(CardRarity.RARE))
        assertEquals("EPIC", cardRarityLabel(CardRarity.EPIC))
    }

    @Test
    fun `the three tiers map to three distinct colours`() {
        // Exercises the REAL color() (plain fun, JVM-reachable) — not a parallel shadow mapping.
        assertEquals(3, RarityTier.entries.map { it.color() }.toSet().size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.RarityTest"`
Expected: FAIL — compilation error, `RarityTier` / `uwRarityTier` / etc. unresolved (`Rarity.kt` not created yet).

- [ ] **Step 3: Create the pure core of `Rarity.kt`**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Rarity.kt` with the enum + pure functions only (Compose pieces added in Step 5):

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.ui.graphics.Color
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.LapisLight
import com.whitefang.stepsofbabylon.presentation.ui.theme.RaritySand

/**
 * Bundle D (#163): presentation-only collectibles rarity identity, shared by Cards + Ultimate Weapons.
 *
 * [RarityTier] is the shared 3-colour palette; the per-screen LABEL shifts (Cards: COMMON/RARE/EPIC;
 * UWs: RARE/EPIC/LEGENDARY — no UW is "common"). NOT a domain concept: UWs have no rarity field, so
 * their tier is derived from [com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType.unlockCost]
 * here in the UI ([uwRarityTier]).
 */
enum class RarityTier { TIER_0, TIER_1, TIER_2 }

/**
 * Tier → theme colour token (sand → sky-lapis → gold ramp). PLAIN fun (not @Composable): reads only
 * top-level `val Color` tokens, so it is JVM-unit-testable and callable from [rarityBorder].
 * `Color(0xFF…)` is a value class (ULong bit-math) — no Android runtime needed.
 */
fun RarityTier.color(): Color = when (this) {
    RarityTier.TIER_0 -> RaritySand   // #C2B280
    RarityTier.TIER_1 -> LapisLight   // #A7C7E7
    RarityTier.TIER_2 -> Gold         // #D4A843
}

/** Card rarity → tier. Exhaustive over [CardRarity] (compiler-enforced). */
fun cardRarityTier(rarity: CardRarity): RarityTier = when (rarity) {
    CardRarity.COMMON -> RarityTier.TIER_0
    CardRarity.RARE -> RarityTier.TIER_1
    CardRarity.EPIC -> RarityTier.TIER_2
}

/**
 * UW unlock cost → tier. Range-based (not exact-value) so a re-priced or future UW landing off
 * today's costs (50/60/75/80/100) still tiers sanely (spec D8). [RarityTest] pins today's six.
 */
fun uwRarityTier(unlockCost: Int): RarityTier = when {
    unlockCost <= 60 -> RarityTier.TIER_0
    unlockCost <= 89 -> RarityTier.TIER_1
    else -> RarityTier.TIER_2
}

/** Card label = the rarity name (COMMON / RARE / EPIC). */
fun cardRarityLabel(rarity: CardRarity): String = rarity.name

/** UW label shifts up so no UW reads as "common" (RARE / EPIC / LEGENDARY). */
fun uwRarityLabel(tier: RarityTier): String = when (tier) {
    RarityTier.TIER_0 -> "RARE"
    RarityTier.TIER_1 -> "EPIC"
    RarityTier.TIER_2 -> "LEGENDARY"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.RarityTest"`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Add the Compose primitives to `Rarity.kt`**

Append the `@Composable` pieces + the `Modifier` extension to `Rarity.kt`. Add these imports to the top of the file (alongside the existing ones):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess
```

Append these declarations at the end of the file:

```kotlin

/**
 * Filled pill badge in the tier colour, dark text for contrast on the light tier colours.
 * [label] is supplied by the caller ([cardRarityLabel] / [uwRarityLabel]) so the same badge serves
 * both screens' naming.
 */
@Composable
fun RarityBadge(tier: RarityTier, label: String) {
    Text(
        text = label,
        color = Color(0xFF1A1A2E),
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tier.color())
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Filled "✓ EQUIPPED" chip — the prominent equipped signal (replaces the tiny ✓ / background tint). */
@Composable
fun EquippedChip() {
    Text(
        text = "✓ EQUIPPED",
        color = Color(0xFF10300A),
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StatusSuccess)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * 3dp rarity border + a left accent bar in the tier colour. Plain Modifier extension — resolves the
 * colour itself via [color], so call-sites stay the bare `Modifier.rarityBorder(tier)`.
 *
 * The accent bar is drawn ON TOP of content via [drawWithContent] (the spec pins this: a `drawBehind`
 * bar would be occluded by the Material3 card container fill). 5dp wide, full height, left edge.
 */
fun Modifier.rarityBorder(tier: RarityTier): Modifier {
    val c = tier.color()
    return this
        .border(width = 3.dp, color = c, shape = RoundedCornerShape(12.dp))
        .drawWithContent {
            drawContent()
            drawRect(color = c, size = Size(width = 5.dp.toPx(), height = size.height))
        }
}
```

- [ ] **Step 6: Run the full unit-test build to confirm nothing regressed**

Run: `./run-gradle.sh testDebugUnitTest > /tmp/bundle-d-t2.log 2>&1; tail -n 20 /tmp/bundle-d-t2.log`
Expected: BUILD SUCCESSFUL; `RarityTest` green; existing tests still green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Rarity.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/RarityTest.kt
git commit -m "feat(#163): add shared Rarity helper (tier mapping, labels, badge, chip, border)"
```

---

## Task 3: Wire the Cards screen

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt`

No new unit test (the screen is `@Composable`; `CardsViewModelTest` is unaffected). Verified on-device.

- [ ] **Step 1: Delete the inline `rarityColor()` and its dead import**

Delete the private function + its comment block at the end of the file (current lines ~204–211):

```kotlin
// Rarity colours tuned for legibility on the dark surface: COMMON was Color.Gray (low contrast on
// bronze); a lighter stone-grey reads as "common" while staying visible. RARE/EPIC keep their
// blue/purple identities but lifted slightly for contrast.
private fun rarityColor(rarity: CardRarity): Color = when (rarity) {
    CardRarity.COMMON -> Color(0xFFB8B0A0)
    CardRarity.RARE -> Color(0xFF6FA8DC)
    CardRarity.EPIC -> Color(0xFFB57EDC)
}
```

Delete the now-unused import (current line 37):

```kotlin
import com.whitefang.stepsofbabylon.domain.model.CardRarity
```

Add the shared-helper imports alongside the other `presentation.ui` imports near the top:

```kotlin
import com.whitefang.stepsofbabylon.presentation.ui.EquippedChip
import com.whitefang.stepsofbabylon.presentation.ui.RarityBadge
import com.whitefang.stepsofbabylon.presentation.ui.cardRarityLabel
import com.whitefang.stepsofbabylon.presentation.ui.cardRarityTier
import com.whitefang.stepsofbabylon.presentation.ui.rarityBorder
```

- [ ] **Step 2: Re-point the pack-result dialog colour calls**

In the pack-result `AlertDialog` (current lines ~136–148), replace the three `rarityColor(r.type.rarity)` calls with `cardRarityTier(r.type.rarity).color()`. Add the import:

```kotlin
import com.whitefang.stepsofbabylon.presentation.ui.color
```

The dialog rows become (icon tints + text colour):

```kotlin
results.forEach { r ->
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (r.isNew) {
            Icon(Icons.Filled.FiberNew, contentDescription = "New", tint = cardRarityTier(r.type.rarity).color(), modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.Autorenew, contentDescription = "Duplicate", tint = cardRarityTier(r.type.rarity).color(), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (r.isNew) formatName(r.type.name) else "${formatName(r.type.name)} +1 Copy",
            color = cardRarityTier(r.type.rarity).color(),
        )
    }
}
```

- [ ] **Step 3: Add the loadout-cap header hint**

Replace the equipped header (current line 73):

```kotlin
Text("Equipped: ${state.equippedCount}/3", style = MaterialTheme.typography.titleSmall)
```

with a cap-aware version (turns warning-coloured + appends the unequip hint at the 3-cap):

```kotlin
if (state.equippedCount >= 3) {
    Text(
        "Equipped: 3/3 — unequip one to swap",
        style = MaterialTheme.typography.titleSmall,
        color = com.whitefang.stepsofbabylon.presentation.ui.theme.StatusWarning,
        fontWeight = FontWeight.Bold,
    )
} else {
    Text("Equipped: ${state.equippedCount}/3", style = MaterialTheme.typography.titleSmall)
}
```

Add the import for `FontWeight` if not already present:

```kotlin
import androidx.compose.ui.text.font.FontWeight
```

- [ ] **Step 4: Apply the prominent treatment to `CardItem`**

Rewrite the `CardItem` composable (current lines ~158–200). Derive the tier, replace the `border=`
param with `Modifier.rarityBorder(tier)`, drop the equipped `primaryContainer` tint, add the badge +
`EquippedChip` in the header, and remove the inline `" • RARE"` text label:

```kotlin
@Composable
private fun CardItem(
    card: CardDisplayInfo, equippedCount: Int,
    onEquip: () -> Unit, onUnequip: () -> Unit, onUpgrade: () -> Unit,
) {
    val haptics = rememberHaptics()
    val upgradePulse = rememberPulse()
    val tier = cardRarityTier(card.type.rarity)
    Card(
        Modifier.fillMaxWidth().rarityBorder(tier),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RarityBadge(tier, cardRarityLabel(card.type.rarity))
                    Text(formatName(card.type.name), style = MaterialTheme.typography.titleSmall)
                }
                if (card.isEquipped) {
                    EquippedChip()
                } else if (card.isMaxLevel) {
                    Text("MAX", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Lv ${card.level}/${card.type.maxLevel}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(card.effectDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (card.isEquipped) {
                    OutlinedButton(onClick = { haptics.tap(); onUnequip() }, modifier = Modifier.weight(1f)) { Text("Unequip") }
                } else {
                    Button(onClick = { haptics.tap(); onEquip() }, enabled = equippedCount < 3, modifier = Modifier.weight(1f)) { Text("Equip") }
                }
                if (!card.isMaxLevel) {
                    Button(
                        onClick = { upgradePulse.trigger(); haptics.tap(); onUpgrade() },
                        enabled = card.canAffordUpgrade,
                        modifier = Modifier.weight(1f).pulseScale(upgradePulse),
                    ) {
                        Text("Upgrade (${card.copyCount}/${card.copiesNeeded})")
                    }
                }
            }
        }
    }
}
```

> **Note:** the old header showed `Lv N/M` (or `MAX`) on the right and the rarity text inline on the left. The rewrite moves `Lv/MAX` to the right slot (replaced by the chip when equipped) and the rarity name is now the badge — so no level info is lost. The `effectDescription` line keeps the level context via the card body. `BorderStroke` import becomes unused — remove `import androidx.compose.foundation.BorderStroke` (line 3) if the compiler/lint flags it.

- [ ] **Step 5: Build + lint to confirm it compiles cleanly**

Run: `./run-gradle.sh testDebugUnitTest lintDebug > /tmp/bundle-d-t3.log 2>&1; tail -n 25 /tmp/bundle-d-t3.log`
Expected: BUILD SUCCESSFUL; no unused-import / unresolved-reference errors; lint clean (no new `HardcodedText`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt
git commit -m "feat(#163): Cards screen — rarity border/badge, EQUIPPED chip, cap hint"
```

---

## Task 4: Wire the Ultimate Weapons screen

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt`

No new unit test (`@Composable`; `UltimateWeaponViewModelTest` unaffected). Verified on-device.

- [ ] **Step 1: Add the shared-helper imports**

Add alongside the existing `presentation.ui` imports near the top:

```kotlin
import com.whitefang.stepsofbabylon.presentation.ui.EquippedChip
import com.whitefang.stepsofbabylon.presentation.ui.RarityBadge
import com.whitefang.stepsofbabylon.presentation.ui.rarityBorder
import com.whitefang.stepsofbabylon.presentation.ui.uwRarityLabel
import com.whitefang.stepsofbabylon.presentation.ui.uwRarityTier
```

- [ ] **Step 2: Add the loadout-cap header hint**

Replace the equipped header (current lines 50–55):

```kotlin
Text(
    "Equipped: ${state.equippedCount}/3",
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(horizontal = 16.dp),
    color = Color.Gray,
)
```

with the cap-aware version:

```kotlin
if (state.equippedCount >= 3) {
    Text(
        "Equipped: 3/3 — unequip one to swap",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
        color = com.whitefang.stepsofbabylon.presentation.ui.theme.StatusWarning,
        fontWeight = FontWeight.Bold,
    )
} else {
    Text(
        "Equipped: ${state.equippedCount}/3",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
        color = Color.Gray,
    )
}
```

(`FontWeight` is already imported in this file — line 24.)

- [ ] **Step 3: Apply rarity border + badge + EquippedChip to `UWCard`**

In `UWCard` (current lines ~74–151): derive the tier, apply `rarityBorder(tier)` to the `Card`, add a
`RarityBadge` in the header, and replace the `CheckCircle` with `EquippedChip`. Locked UWs keep their
dimmed container colour but still show the (dimmed) rarity border + badge per spec D6.

Replace the `Card(...)` opening + header `Row` (current lines ~83–110):

```kotlin
    val haptics = rememberHaptics()
    val tier = uwRarityTier(info.type.unlockCost)
    Card(
        modifier = Modifier.fillMaxWidth().rarityBorder(tier),
        colors = CardDefaults.cardColors(
            containerColor = if (info.isUnlocked) Color(0xFF2A2A3E) else Color(0xFF1A1A2E),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RarityBadge(tier, uwRarityLabel(tier))
                        Text(
                            info.type.name.replace('_', ' '),
                            fontWeight = FontWeight.Bold,
                            color = if (info.isUnlocked) Color.White else Color.Gray,
                        )
                    }
                    Text(
                        info.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.isUnlocked) Color.White.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.5f),
                    )
                }
                if (info.isEquipped) {
                    EquippedChip()
                }
            }
```

(The rest of `UWCard` — the unlock button branch, per-path rows, and equip/unequip `OutlinedButton` — is unchanged. The `Icons.Filled.CheckCircle` import + the old `Icon(...)` equipped block are removed; if `Icon`/`CheckCircle` become unused, delete those imports at lines 28–30.)

- [ ] **Step 4: Build + lint**

Run: `./run-gradle.sh testDebugUnitTest lintDebug > /tmp/bundle-d-t4.log 2>&1; tail -n 25 /tmp/bundle-d-t4.log`
Expected: BUILD SUCCESSFUL; no unresolved/unused-import errors; lint clean.

- [ ] **Step 5: Full assemble to confirm a shippable debug build**

Run: `./run-gradle.sh assembleDebug > /tmp/bundle-d-assemble.log 2>&1; tail -n 15 /tmp/bundle-d-assemble.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt
git commit -m "feat(#163): UW screen — rarity border/badge, EQUIPPED chip, cap hint, dimmed locked rarity"
```

---

## Task 5: Sync current-state docs (PR Task-List Convention step 1)

**Files:**
- Modify: `docs/steering/source-files.md`, `CLAUDE.md`, `CHANGELOG.md`

- [ ] **Step 1: Add `Rarity.kt` to the source-file index**

In `docs/steering/source-files.md`, add a line in the `presentation/ui/` block (after the `ClaimCelebration.kt` line ~294):

```
presentation/ui/Rarity.kt                          # Shared collectibles rarity identity (#163, Bundle D): RarityTier (3-tier palette) + plain color() (sand/LapisLight/Gold) + cardRarityTier(CardRarity)/uwRarityTier(unlockCost: ≤60/61-89/≥90) + cardRarityLabel/uwRarityLabel (Cards COMMON/RARE/EPIC; UWs RARE/EPIC/LEGENDARY) + @Composable RarityBadge/EquippedChip + Modifier.rarityBorder (3dp border + left accent bar). Pure fns JVM-tested (RarityTest); composables visual-only. Used by CardsScreen + UltimateWeaponScreen.
```

- [ ] **Step 2: Update the headline test count in CLAUDE.md**

`RarityTest` adds 6 tests. In `CLAUDE.md`'s Testing section, update the headline count line (990 → 996):

```
- **Headline count: 996 JVM tests + 9 instrumented tests.** Update this line when it changes; the
```

(Confirm the delta against the actual test run from Task 2 Step 6 — if a test was split/merged during implementation, use the real number.)

- [ ] **Step 3: Add a CHANGELOG entry**

In `CHANGELOG.md`, add a section for Bundle D under the current-development block (match the format of the existing Bundle C entry):

```markdown
### Look & Feel Bundle D (#163) — Collectibles rarity visual system

Presentation-only. Shared 3-tier rarity palette (sand → sky-lapis → gold) for Ultimate Weapons + Cards
via new `presentation/ui/Rarity.kt` (+ `RaritySand` token). Cards keep COMMON/RARE/EPIC; UWs derive
RARE/EPIC/LEGENDARY from `unlockCost`. Prominent treatment (3dp rarity border + left accent bar +
filled pill badge), explicit EQUIPPED chip replacing the tiny ✓ / background tint, header loadout-cap
hint ("3/3 — unequip one to swap"), locked UWs show dimmed rarity. Fixes the latent Epic/Power-Stone
amethyst colour collision. +6 JVM tests (RarityTest). Zero engine/economy/domain/loadout change.
```

- [ ] **Step 4: Commit the doc sweep**

```bash
git add docs/steering/source-files.md CLAUDE.md CHANGELOG.md
git commit -m "docs(#163): sync source-files index, headline test count, CHANGELOG for Bundle D"
```

---

## Task 6: Update STATE + RUN_LOG (PR Task-List Convention step 2)

**Files:**
- Modify: `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Run `/checkpoint` (preferred) or update both files manually**

Preferred: invoke the `checkpoint` skill, which performs the doc-drift sweep, updates `STATE.md`'s
current-objective + headline, and appends a `RUN_LOG.md` entry.

If updating manually:
- `STATE.md`: set the headline + current-objective to "Bundle D (#163) implemented on
  `feat/163-look-and-feel-bundle-d`; pending feel sign-off + PR" and bump the test count to 996.
- `RUN_LOG.md`: append a dated entry summarising the work (shared `Rarity.kt` helper + 2 screens, 6
  new tests, presentation-only, spec+plan both passed the Adversarial Review Gate).

- [ ] **Step 2: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#163): checkpoint — Bundle D implemented, pending feel sign-off"
```

---

## Final Verification

- [ ] **Run the full gate**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/bundle-d-final.log 2>&1; tail -n 20 /tmp/bundle-d-final.log`
Expected: BUILD SUCCESSFUL; all unit tests green (incl. `RarityTest`'s 6); lint clean; debug APK assembles.

- [ ] **On-device feel sign-off (developer, manual)** — install the debug APK and confirm on the Cards and Ultimate Weapons screens:
  - Rarity reads at a glance (sand / sky-lapis / gold border + accent bar + pill badge; Cards COMMON/RARE/EPIC, UWs RARE/EPIC/LEGENDARY).
  - The EQUIPPED chip is unmistakable (replaces the old tiny ✓ / tint).
  - The cap hint ("3/3 — unequip one to swap") appears only at 3 equipped, in the warning colour.
  - Locked UWs show their rarity border + badge, dimmed.
  - The accent bar is visible (not occluded) and the left edge isn't clipped.

---

## Notes for the implementer

- **Do NOT touch** any `domain/`, `data/`, ViewModel, use-case, DAO, or battle file. This is presentation-only; the 3-cap is already domain-enforced — you only surface it.
- If `./run-gradle.sh` is missing, recreate it per `README.md` (it's gitignored).
- Save every `brazil`/gradle build to a temp log and `tail`/`grep` it (long, verbose output) — the run commands above already redirect.
- The `RarityBadge`/`EquippedChip` use literal display strings ("✓ EQUIPPED", rarity labels). Lint's `HardcodedText` check applies to XML, not Compose `Text` — these will not trip it (consistent with the existing Compose screens). i18n of all strings is tracked separately in #34.
