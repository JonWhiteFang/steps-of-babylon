# Look & Feel Bundle A — Correctness & Accessibility Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the safe, presentation-only look-and-feel cleanup from the 2026-06-12 UX review — de-emoji all UI-control/currency/status glyphs via a shared currency component, add loading spinners + a Workshop empty-state, fix the onboarding-dots a11y gap, and re-title/rename the Settings screen.

**Architecture:** A shared component layer (`CurrencyDisplay`, `LoadingBox`, `EmptyState`) in `presentation/ui/`, then a screen-by-screen sweep that routes every currency/status glyph through it. Every change is in `presentation/` except one pure deletion of a dead `domain/` enum. No engine/economy/concurrency logic changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), `material-icons-extended` (already a dependency — `app/build.gradle.kts:212`), JUnit Jupiter for the one JVM unit test. Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-12-look-and-feel-bundle-a-design.md` · **Issue:** #160

---

## Conventions used in this plan

- **Loading-gate pattern (uniform):** immediately after the screen collects its state, insert
  `if (state.isLoading) { LoadingBox(); return }`. Each target screen's ViewModel already flips
  `isLoading = false` on its first combined emission (verified), and the flows use
  `SharingStarted.WhileSubscribed(5000)` whose replay cache never expires — so the spinner shows
  only on the genuine first load, not on screen revisits. Early-return is safe in a `@Composable`
  (normal conditional composition); the brief loading window has no snackbar message to drop.
- **De-emoji of a currency** → `CurrencyValue(...)` (standalone display) or `CurrencyCost(...)`
  (compact, for button labels), from Task 1.
- **De-emoji of a status glyph** → a Material `Icon(...)`, with `contentDescription` per the spec's
  per-site rule (sole status carrier → real description; sits next to text that conveys the state →
  `null`).
- **Commit per task.** Run the build only on tasks that change Kotlin (noted per task).

---

## Task 1: `CurrencyDisplay.kt` — shared currency component + JVM test

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplay.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplayTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplayTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrencyDisplayTest {

    @Test
    fun `label returns the plural-noun form for each currency`() {
        assertEquals("Steps", CurrencyType.STEPS.label())
        assertEquals("Cash", CurrencyType.CASH.label())
        assertEquals("Gems", CurrencyType.GEMS.label())
        assertEquals("Power Stones", CurrencyType.POWER_STONES.label())
    }

    @Test
    fun `formatCurrency inserts grouping separators`() {
        assertEquals("0", formatCurrency(0))
        assertEquals("999", formatCurrency(999))
        assertEquals("1,000", formatCurrency(1_000))
        assertEquals("1,234,567", formatCurrency(1_234_567))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.CurrencyDisplayTest"`
Expected: FAIL — compilation error (`CurrencyType`, `label`, `formatCurrency` unresolved).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplay.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.presentation.ui.theme.GemColor
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.PowerStoneColor
import com.whitefang.stepsofbabylon.presentation.ui.theme.StepColor
import java.text.NumberFormat
import java.util.Locale

/**
 * The four player currencies, as a *presentation* concept. Members mirror the (now-deleted) domain
 * `Currency` enum. This is the single source of truth for currency presentation: swap [icon] here
 * later to adopt themed-glyph art in one place. Lives in presentation (not domain) because it carries
 * Compose-bound [icon]/[tint], which the Android-free domain layer cannot hold.
 */
enum class CurrencyType { STEPS, CASH, GEMS, POWER_STONES }

/** Approximate Material vector per currency until themed glyph art ships (#160 follow-up). */
fun CurrencyType.icon(): ImageVector = when (this) {
    CurrencyType.STEPS -> Icons.Filled.DirectionsWalk
    CurrencyType.CASH -> Icons.Filled.Paid
    CurrencyType.GEMS -> Icons.Filled.Diamond
    CurrencyType.POWER_STONES -> Icons.Filled.OfflineBolt
}

/** Palette-aligned tint per currency (tokens from Color.kt). CASH has no token → reuse [Gold]. */
@Composable
fun CurrencyType.tint(): Color = when (this) {
    CurrencyType.STEPS -> StepColor
    CurrencyType.CASH -> Gold
    CurrencyType.GEMS -> GemColor
    CurrencyType.POWER_STONES -> PowerStoneColor
}

/** Plural-noun form for standalone a11y `contentDescription`. No quantity inflection. */
fun CurrencyType.label(): String = when (this) {
    CurrencyType.STEPS -> "Steps"
    CurrencyType.CASH -> "Cash"
    CurrencyType.GEMS -> "Gems"
    CurrencyType.POWER_STONES -> "Power Stones"
}

/** Thousands-grouped amount (US grouping for determinism). Centralizes the review's separator fix. */
fun formatCurrency(amount: Long): String = NumberFormat.getNumberInstance(Locale.US).format(amount)

/**
 * Icon + thousands-formatted value, e.g. a balance readout. The icon's text label is adjacent, so
 * it uses `contentDescription = null` (the value carries meaning); [CurrencyType.label] is for the
 * rare standalone case.
 */
@Composable
fun CurrencyValue(
    type: CurrencyType,
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(type.icon(), contentDescription = null, tint = type.tint(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatCurrency(amount), style = style, color = type.tint())
    }
}

/** Compact inline form for button labels: icon + raw value (no color, inherits the button's). */
@Composable
fun CurrencyCost(
    type: CurrencyType,
    amount: Long,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(type.icon(), contentDescription = type.label(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatCurrency(amount))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.CurrencyDisplayTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplay.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplayTest.kt
git commit -m "feat(ui): add shared CurrencyDisplay component (#160)"
```

---

## Task 2: `LoadingBox.kt` — shared loading indicator

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/LoadingBox.kt`

No test (trivial wrapper; verified by build + on-device).

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/LoadingBox.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Full-screen centered progress indicator, shown while a screen's first data load is in flight. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

- [ ] **Step 2: Commit** (build deferred to Task 14, the first screen that uses it)

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/LoadingBox.kt
git commit -m "feat(ui): add shared LoadingBox component (#160)"
```

---

## Task 3: `EmptyState.kt` — shared empty-state

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EmptyState.kt`

No test (trivial; verified by build + on-device).

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EmptyState.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Centered empty-state: an optional title above a supporting message. Extracted from the Cards
 * screen's inline empty-state (PR #159) so Cards/Workshop share one shape. When [title] is null the
 * message renders alone (covers the single-line case).
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 2: Commit** (build deferred to Task 6, the first screen that uses it)

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EmptyState.kt
git commit -m "feat(ui): add shared EmptyState component (#160)"
```

---

## Task 4: Delete the dead `domain/model/Currency.kt`

**Files:**
- Delete: `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/Currency.kt`

This enum (`enum class Currency { STEPS, CASH, GEMS, POWER_STONES }`) has zero references in main or
test (confirmed via `git grep`). `CurrencyType` (Task 1) supersedes it in presentation.

- [ ] **Step 1: Confirm it is unreferenced**

Run: `git grep -wn "Currency" -- 'app/src/*.kt' | grep -v "CurrencyType\|CurrencyDisplay\|CurrencyDashboard\|Currency colours\|// "`
Expected: only `domain/model/Currency.kt:3:enum class Currency { ... }` (its own declaration). If
any other reference appears, STOP and do not delete — re-evaluate.

- [ ] **Step 2: Delete the file**

```bash
git rm app/src/main/java/com/whitefang/stepsofbabylon/domain/model/Currency.kt
```

- [ ] **Step 3: Build to confirm nothing broke**

Run: `./run-gradle.sh testDebugUnitTest > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL (DomainPurityTest still passes — deletion only).

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(domain): delete dead unreferenced Currency enum (#160)"
```

---

## Task 5: De-emoji Labs (Steps/Gems currency, Free ⭐, Rush 💎, Start 🦶, ⏱)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt`

- [ ] **Step 1: Add imports**

After the existing import block (the `import androidx.compose.material3.Text` group), add. (LabsScreen
already imports `Row`/`Spacer`/`Column`/`Alignment` but NOT `Icon`/`Icons`/`size`/`width` — all new
ones below are needed and non-duplicate; verified against the current file.)

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyCost
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
```

- [ ] **Step 2: Replace the header balances (lines 48–49)**

Old:
```kotlin
            Text("🦶 ${state.stepBalance}", style = MaterialTheme.typography.titleMedium)
            Text("💎 ${state.gems}", style = MaterialTheme.typography.titleMedium)
```
New:
```kotlin
            CurrencyValue(CurrencyType.STEPS, state.stepBalance)
            CurrencyValue(CurrencyType.GEMS, state.gems)
```

- [ ] **Step 3: Replace the "Unlock Slot" button label (line 56)**

Old:
```kotlin
                    Text("Unlock Slot (${state.slotUnlockCostGems} 💎)")
```
New:
```kotlin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unlock Slot ")
                        CurrencyCost(CurrencyType.GEMS, state.slotUnlockCostGems)
                    }
```

- [ ] **Step 4: Replace the "Free ⭐" rush button (line 136)**

Old:
```kotlin
                                OutlinedButton(onClick = onFreeRush) { Text("Free ⭐") }
```
New:
```kotlin
                                OutlinedButton(onClick = onFreeRush) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Free")
                                }
```

- [ ] **Step 5: Replace the "Rush" button label (line 139)**

Old:
```kotlin
                            Button(onClick = onRush, enabled = info.canAffordRush) {
                                Text("Rush (${info.rushCostGems} 💎)")
                            }
```
New:
```kotlin
                            Button(onClick = onRush, enabled = info.canAffordRush) {
                                Text("Rush ")
                                CurrencyCost(CurrencyType.GEMS, info.rushCostGems)
                            }
```

- [ ] **Step 6: Replace the "⏱ time" + "Start 🦶" row (lines 149–152)**

Old:
```kotlin
                        Text("⏱ ${String.format("%.1fh", info.timeToCompleteHours)}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onStart, enabled = info.canAffordStart) {
                            Text("Start (${info.costToStart} 🦶)")
                        }
```
New:
```kotlin
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(String.format("%.1fh", info.timeToCompleteHours), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onStart, enabled = info.canAffordStart) {
                            Text("Start ")
                            CurrencyCost(CurrencyType.STEPS, info.costToStart)
                        }
```

> Note: `Spacer`, `Row`, `Alignment`, `Modifier.width`/`size`, `dp` are already imported in this
> file (verify; add any missing `androidx.compose.foundation.layout.width` / `...size` import).

- [ ] **Step 7: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt
git commit -m "feat(ui): de-emoji Labs — currency icons, star, clock (#160)"
```

---

## Task 6: De-emoji Cards + route empty-state through `EmptyState`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt`

- [ ] **Step 1: Add imports**

Add to the import block. (CardsScreen already imports `Row`/`Spacer`/`Column`/`Alignment` but NOT
`Icon`/`Icons`/`size`/`width` — all new ones below are needed and non-duplicate; verified.)

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyCost
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
import com.whitefang.stepsofbabylon.presentation.ui.EmptyState
```

- [ ] **Step 2: Replace the gem header (line 54)**

Old:
```kotlin
            Text("💎 ${state.gems} Gems", style = MaterialTheme.typography.titleMedium)
```
New:
```kotlin
            CurrencyValue(CurrencyType.GEMS, state.gems)
```

> Note: Cards `state.gems` is `Long`, `pack.tier.gemCost` is `Long` — no `.toLong()` needed.

- [ ] **Step 3: Replace the "Free Pack (Ad)" button (line 63)**

Old:
```kotlin
                Text("🎬 Free Pack (Ad)")
```
New:
```kotlin
                Icon(Icons.Filled.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Free Pack (Ad)")
```

(`size`/`width` imports were added in Step 1.)

- [ ] **Step 4: Replace the pack-cost button label (line 78)**

Old:
```kotlin
                ) { Text("${pack.tier.name}\n${pack.tier.gemCost}💎") }
```
New:
```kotlin
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pack.tier.name)
                        CurrencyCost(CurrencyType.GEMS, pack.tier.gemCost)
                    }
                }
```

- [ ] **Step 5: Replace the inline empty-state (lines 84–97) with `EmptyState`**

Old:
```kotlin
        if (state.ownedCards.isEmpty() && !state.isLoading) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No cards yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Open a pack above to start your collection. Equip up to 3 cards for per-round bonuses in battle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
```
New:
```kotlin
        if (state.ownedCards.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "No cards yet",
                message = "Open a pack above to start your collection. Equip up to 3 cards for per-round bonuses in battle.",
            )
        } else {
```

- [ ] **Step 6: Replace the pack-result glyphs (lines 118–120)**

Old:
```kotlin
                    results.forEach { r ->
                        val label = if (r.isNew) "🆕 ${formatName(r.type.name)}" else "♻ ${formatName(r.type.name)} +1 Copy"
                        Text(label, color = rarityColor(r.type.rarity))
                    }
```
New:
```kotlin
                    results.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.isNew) {
                                Icon(Icons.Filled.FiberNew, contentDescription = "New", tint = rarityColor(r.type.rarity), modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.Autorenew, contentDescription = "Duplicate", tint = rarityColor(r.type.rarity), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (r.isNew) formatName(r.type.name) else "${formatName(r.type.name)} +1 Copy",
                                color = rarityColor(r.type.rarity),
                            )
                        }
                    }
```

- [ ] **Step 7: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt
git commit -m "feat(ui): de-emoji Cards + shared empty-state (#160)"
```

---

## Task 7: De-emoji Store (gem header/amount/cost, ✅ Purchased/Active, ⭐ Season Pass)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyCost
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
```

- [ ] **Step 2: Replace the gem balance header (line 51)**

Old:
```kotlin
            Text("💎 %,d %s".format(state.gems, if (state.gems == 1L) "Gem" else "Gems"), style = MaterialTheme.typography.titleMedium, color = GemColor)
```
New:
```kotlin
            CurrencyValue(CurrencyType.GEMS, state.gems)
```

> This drops the singular "Gem" form; the icon + count is now the canonical presentation
> (consistent with every other currency site). The `GemColor` import may become unused — remove it
> if the build warns.

- [ ] **Step 3: Replace the gem-pack amount (line 62)**

Old:
```kotlin
                        Text("%,d 💎 Gems".format(product.gemAmount), fontWeight = FontWeight.Bold)
```
New:
```kotlin
                        CurrencyValue(CurrencyType.GEMS, product.gemAmount, style = MaterialTheme.typography.titleMedium)
```

- [ ] **Step 4: Replace the Ad Removal "✅ Purchased" (lines 82–86)**

Old:
```kotlin
                        Text(
                            if (state.adRemoved) "✅ Purchased"
                            else state.priceDisplays[BillingProduct.AD_REMOVAL] ?: BillingProduct.AD_REMOVAL.priceDisplay,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
```
New:
```kotlin
                        if (state.adRemoved) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Purchased", tint = com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Purchased", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(
                                state.priceDisplays[BillingProduct.AD_REMOVAL] ?: BillingProduct.AD_REMOVAL.priceDisplay,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
```

- [ ] **Step 5: Replace the "⭐ Season Pass" title (line 101)**

Old:
```kotlin
                            Text("⭐ Season Pass", fontWeight = FontWeight.Bold)
```
New:
```kotlin
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Season Pass", fontWeight = FontWeight.Bold)
                            }
```

- [ ] **Step 6: Replace the Season Pass "✅ Active" line (lines 102–108)**

Old:
```kotlin
                            Text(
                                when {
                                    state.seasonPassActive -> "✅ Active — ${state.seasonPassDaysRemaining ?: 0} days remaining"
                                    else -> state.priceDisplays[BillingProduct.SEASON_PASS] ?: BillingProduct.SEASON_PASS.priceDisplay
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
```
New:
```kotlin
                            if (state.seasonPassActive) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Active — ${state.seasonPassDaysRemaining ?: 0} days remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text(
                                    state.priceDisplays[BillingProduct.SEASON_PASS] ?: BillingProduct.SEASON_PASS.priceDisplay,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
```

- [ ] **Step 7: Replace the cosmetic price button (line 156)**

Old:
```kotlin
                        ) { Text("💎 ${cosmetic.priceGems}") }
```
New:
```kotlin
                        ) { CurrencyCost(CurrencyType.GEMS, cosmetic.priceGems) }
```

- [ ] **Step 8: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt
git commit -m "feat(ui): de-emoji Store — currencies, purchased/active status, season pass (#160)"
```

---

## Task 8: De-emoji Missions (✓ claimed checks + reward Row rewrite)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
```

(`Icon` and `Row`/`Spacer` come from the existing `material3.*` and `layout.*` wildcard imports.)

- [ ] **Step 2: Replace the mission claimed check (lines 75–77)**

Old:
```kotlin
                if (mission.claimed) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
```
New:
```kotlin
                if (mission.claimed) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Claimed", tint = MaterialTheme.colorScheme.primary)
                }
```

- [ ] **Step 3: Replace the reward `buildString` (lines 88–95) with a Row of `CurrencyValue`s**

Old:
```kotlin
                val reward = buildString {
                    if (mission.rewardGems > 0) append("${mission.rewardGems} 💎")
                    if (mission.rewardPowerStones > 0) {
                        if (isNotEmpty()) append(" + ")
                        append("${mission.rewardPowerStones} ⚡")
                    }
                }
                Text(reward, style = MaterialTheme.typography.bodySmall)
```
New:
```kotlin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mission.rewardGems > 0) {
                        CurrencyValue(CurrencyType.GEMS, mission.rewardGems.toLong(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (mission.rewardPowerStones > 0) {
                        if (mission.rewardGems > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text("+", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(6.dp))
                        }
                        CurrencyValue(CurrencyType.POWER_STONES, mission.rewardPowerStones.toLong(), style = MaterialTheme.typography.bodySmall)
                    }
                }
```

> Preserves today's behavior: nothing renders when both rewards are zero; the "+" separator shows
> only when both are present. `Modifier.width` comes from the `layout.*` wildcard import.

- [ ] **Step 4: Replace the milestone claimed check (line 121)**

Old:
```kotlin
                if (ms.isClaimed) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
```
New:
```kotlin
                if (ms.isClaimed) Icon(Icons.Filled.CheckCircle, contentDescription = "Claimed", tint = MaterialTheme.colorScheme.primary)
```

- [ ] **Step 5: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt
git commit -m "feat(ui): de-emoji Missions — claim checks + reward currency icons (#160)"
```

---

## Task 9: De-emoji Economy (✓ Claimed/Earned, ✓/✗ week, ⏱ weekly timer)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
```

(`Icons`, `Icon`, `androidx.compose.foundation.layout.size`, and `androidx.compose.ui.Alignment` are
already imported in CurrencyDashboardScreen.kt; `Row`/`Spacer` too. Only the three icon vectors above
are new. Note: the de-emoji here uses `Check` for the claimed/earned lines, so `CheckCircle` is NOT
needed — do not add it.)

> `Modifier.width` is used in the new code below; CurrencyDashboardScreen does NOT currently import
> `androidx.compose.foundation.layout.width`. Add it:
> ```kotlin
> import androidx.compose.foundation.layout.width
> ```

- [ ] **Step 2: Replace the weekly "⏱ time remaining" (line 69)**

Old:
```kotlin
                        Text("⏱ ${state.weeklyTimeRemaining}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```
New:
```kotlin
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(state.weeklyTimeRemaining, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
```

- [ ] **Step 3: Replace the daily-gems claimed line (lines 115–119)**

Old:
```kotlin
                Text(
                    if (state.todayGemsClaimed) "✓ Today's Gems claimed" else "Open the app daily for Gems!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```
New:
```kotlin
                if (state.todayGemsClaimed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Today's Gems claimed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Open the app daily for Gems!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
```

- [ ] **Step 4: Replace the daily-PS earned line (lines 128–132)**

Old:
```kotlin
                Text(
                    if (state.todayPsClaimed) "✓ Earned today (walked 1,000+ steps)"
                    else "Walk 1,000 steps today for 1 Power Stone",
                    style = MaterialTheme.typography.bodyMedium,
                )
```
New:
```kotlin
                if (state.todayPsClaimed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Earned today (walked 1,000+ steps)", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("Walk 1,000 steps today for 1 Power Stone", style = MaterialTheme.typography.bodyMedium)
                }
```

- [ ] **Step 5: Replace the ThresholdRow "✓ Claimed" (lines 152–161)**

Old:
```kotlin
        Text(
            when {
                claimed -> "✓ Claimed"
                reached -> "Ready!"
                else -> "—"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (reached && !claimed) FontWeight.Bold else FontWeight.Normal,
            color = if (claimed) StatusSuccess else if (reached) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
```
New:
```kotlin
        if (claimed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Claimed", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
            }
        } else {
            Text(
                if (reached) "Ready!" else "—",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                color = if (reached) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

- [ ] **Step 6: Replace the HistoryRow "✓/✗" (lines 171–176)**

Old:
```kotlin
            Text(
                if (met) "✓" else "✗",
                style = MaterialTheme.typography.bodySmall,
                color = if (met) StatusSuccess else StatusDanger,
                fontWeight = FontWeight.Bold,
            )
```
New:
```kotlin
            Icon(
                if (met) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (met) "Goal met" else "Goal missed",
                tint = if (met) StatusSuccess else StatusDanger,
                modifier = Modifier.size(16.dp),
            )
```

- [ ] **Step 7: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt
git commit -m "feat(ui): de-emoji Economy — claim/week status icons + timer (#160)"
```

---

## Task 10: De-emoji Weapons (✓ equipped) + add `isLoading` + loading gate (NO empty-state)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponViewModel.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt`

> Weapons gets **no** empty-state: its list is a fixed 6-entry `UltimateWeaponType.entries` catalog,
> never data-empty. It gets the loading gate (covers the pre-emission frame) + the ✓ de-emoji.

- [ ] **Step 1: Add `isLoading` to the UiState (ViewModel, line 43–47)**

Old:
```kotlin
data class UltimateWeaponUiState(
    val weapons: List<UWDisplayInfo> = emptyList(),
    val powerStones: Long = 0,
    val equippedCount: Int = 0,
)
```
New:
```kotlin
data class UltimateWeaponUiState(
    val weapons: List<UWDisplayInfo> = emptyList(),
    val powerStones: Long = 0,
    val equippedCount: Int = 0,
    val isLoading: Boolean = true,
)
```

- [ ] **Step 2: Set `isLoading = false` in the combine mapper (ViewModel, the `UltimateWeaponUiState(...)` built inside `combine`, ~line 92–94)**

Old:
```kotlin
            powerStones = wallet.powerStones,
            equippedCount = owned.count { it.isEquipped && it.isUnlocked },
        )
```
New:
```kotlin
            powerStones = wallet.powerStones,
            equippedCount = owned.count { it.isEquipped && it.isUnlocked },
            isLoading = false,
        )
```

- [ ] **Step 3: Add imports to the screen**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
```

- [ ] **Step 4: Add the loading gate (screen, right after the state collection, line 33)**

Old:
```kotlin
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
```
New:
```kotlin
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) { LoadingBox(); return }

    Column(Modifier.fillMaxSize()) {
```

- [ ] **Step 5: Replace the equipped "✓" (screen, lines 94–101)**

Old:
```kotlin
                if (info.isEquipped) {
                    Text(
                        "✓",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
```
New:
```kotlin
                if (info.isEquipped) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Equipped",
                        tint = Color(0xFF4CAF50),
                    )
                }
```

- [ ] **Step 6: Build**

Run: `./run-gradle.sh testDebugUnitTest > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL (UltimateWeaponViewModelTest still passes — the new field has a default
and the mapper sets it; no test asserts full-state equality on the loading default).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/
git commit -m "feat(ui): de-emoji Weapons equipped check + loading gate (#160)"
```

---

## Task 11: Onboarding — ✓ status line + pagination-dots a11y

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

- [ ] **Step 2: Add a11y to the page-dots row (lines 110–130)**

Old:
```kotlin
            // Page dots.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { i ->
                    val active = i == pagerState.currentPage
                    // Decorative dot — a single Box with a background, no inner Surface, no
                    // semantics (it carries no text, so nothing to hide from TalkBack).
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                    )
                }
            }
```
New:
```kotlin
            // Page dots. The dots convey page position via colour+size only — invisible to TalkBack
            // (HorizontalPager does not auto-announce "page X of N"), so the ROW carries a single
            // semantic label and the individual dots stay decorative.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${slides.size}" },
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                    )
                }
            }
```

- [ ] **Step 3: Replace the "Step counting enabled ✓" text (lines 145–151)**

Old:
```kotlin
                        Text(
                            "Step counting enabled ✓",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
```
New:
```kotlin
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Step counting enabled",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
```

- [ ] **Step 4: Build + run the onboarding guards**

Run: `./run-gradle.sh testDebugUnitTest --tests "*Onboarding*" > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL; `OnboardingContentTest`, `OnboardingRoutingTest`, `OnboardingViewModelTest` pass (content/routing unchanged — only presentation).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt
git commit -m "feat(ui): onboarding — de-emoji status line + page-dots a11y label (#160)"
```

---

## Task 12: De-emoji Battle HUD pause toggle (▶/⏸)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`

> HUD Compose only — does NOT touch the renderer/engine/effects. The button already carries a
> `contentDescription` (`pauseDesc`), so the icon is decorative.

- [ ] **Step 1: Add imports**

> ⚠️ `BattleScreen.kt` **already imports** `androidx.compose.material3.Icon` (line 21) and
> `androidx.compose.material.icons.Icons` (line 28). Re-adding either is a **duplicate-import compile
> error.** Add ONLY the two new icon vectors:

```kotlin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
```

- [ ] **Step 2: Replace the ▶/⏸ glyph (line 193)**

Old:
```kotlin
                ) { Text(if (state.isPaused) "▶" else "⏸", color = Color.White) }
```
New:
```kotlin
                ) { Icon(if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = Color.White) }
```

- [ ] **Step 3: Build + run the battle guards**

Run: `./run-gradle.sh testDebugUnitTest --tests "*Battle*" --tests "*GameEngine*" > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL; battle/engine tests pass (no logic touched).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt
git commit -m "feat(ui): de-emoji Battle HUD pause toggle (#160)"
```

---

## Task 13: Workshop empty-state (defensive) + loading gate

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.whitefang.stepsofbabylon.presentation.ui.EmptyState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
```

- [ ] **Step 2: Add the loading gate (right after the state collection, line 36)**

Old:
```kotlin
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = UpgradeCategory.entries
```
New:
```kotlin
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) { LoadingBox(); return }
    val categories = UpgradeCategory.entries
```

- [ ] **Step 3: Add the empty-state branch around the upgrade list (lines 76–84)**

Old:
```kotlin
            // Upgrade list
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.upgrades, key = { it.type.name }) { info ->
                    UpgradeCard(info = info, onClick = { viewModel.purchase(info.type) })
                }
            }
```
New:
```kotlin
            // Upgrade list. Defensive empty-state guards the pre-seed transient (observeAllUpgrades
            // emitting before ensureUpgradesExist lands); every seeded category otherwise has ≥4
            // Workshop-visible upgrades.
            if (state.upgrades.isEmpty()) {
                EmptyState(message = "No upgrades in this category yet.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.upgrades, key = { it.type.name }) { info ->
                        UpgradeCard(info = info, onClick = { viewModel.purchase(info.type) })
                    }
                }
            }
```

- [ ] **Step 4: Build**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreen.kt
git commit -m "feat(ui): Workshop loading gate + defensive empty-state (#160)"
```

---

## Task 14: Loading gates on the remaining screens (Home, Stats, Cards, Labs, Missions, Economy, Supplies, Store)

**Files:**
- Modify each screen + add `isLoading` to `StoreUiState` and flip it in `StoreViewModel`.

> Each screen below already has `isLoading` in its UiState defaulting to `true` and flips to `false`
> on first emission — EXCEPT Store, which needs the field added (Steps 1–2). All gates use the same
> early-return pattern. Add `import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox` to each
> screen file.

- [ ] **Step 1: Add `isLoading` to `StoreUiState` (`StoreUiState.kt`, after line 13)**

Old:
```kotlin
    val isPurchasing: Boolean = false,
    val userMessage: String? = null,
```
New:
```kotlin
    val isPurchasing: Boolean = false,
    val isLoading: Boolean = true,
    val userMessage: String? = null,
```

- [ ] **Step 2: Set `isLoading = false` in `StoreViewModel` combine mapper (`StoreViewModel.kt`, in the `StoreUiState(...)` block, after `priceDisplays = priceDisplays,` ~line 74)**

Old:
```kotlin
            isPurchasing = purchasing,
            userMessage = message,
            priceDisplays = priceDisplays,
        )
```
New:
```kotlin
            isPurchasing = purchasing,
            isLoading = false,
            userMessage = message,
            priceDisplays = priceDisplays,
        )
```

- [ ] **Step 3: Add the gate to each screen.** Insert `if (state.isLoading) { LoadingBox(); return }` immediately after the `val state by ...` line, and add the `LoadingBox` import. Apply to:

  - `store/StoreScreen.kt` — after `val state by viewModel.uiState.collectAsState()` (line 37).
  - `home/HomeScreen.kt` — after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line 67).
  - `stats/StatsScreen.kt` — after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line 21).
  - `labs/LabsScreen.kt` — after `val state by viewModel.uiState.collectAsState()` (line 37).
  - `missions/MissionsScreen.kt` — after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line 23).
  - `economy/CurrencyDashboardScreen.kt` — after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line 43).
  - `supplies/UnclaimedSuppliesScreen.kt` — after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line 40).
  - `cards/CardsScreen.kt` — after `val state by viewModel.uiState.collectAsState()` (line 41).

  Example (Home):
  Old:
  ```kotlin
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val theme = BiomeTheme.forBiome(state.currentBiome)
  ```
  New:
  ```kotlin
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      if (state.isLoading) { LoadingBox(); return }
      val theme = BiomeTheme.forBiome(state.currentBiome)
  ```

> For screens whose first line after state collection is a `remember`/`LaunchedEffect`/`DisposableEffect`,
> put the gate **before** it — the effect simply registers on the first non-loading composition. The
> brief load window has no snackbar message to drop.

- [ ] **Step 4: Build**

Run: `./run-gradle.sh testDebugUnitTest > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL; StoreViewModelTest passes (new field defaulted + set in mapper).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/
git commit -m "feat(ui): screen-level loading spinners across menus (#160)"
```

---

## Task 15: Rename Settings (title + class/file rename)

**Files:**
- Rename: `settings/NotificationSettingsScreen.kt` → `settings/SettingsScreen.kt`
- Rename: `settings/NotificationSettingsViewModel.kt` → `settings/SettingsViewModel.kt`
- Modify: `presentation/MainActivity.kt` (import + call site)

- [ ] **Step 1: Rename the files with git**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git mv app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/NotificationSettingsScreen.kt \
       app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt
git mv app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/NotificationSettingsViewModel.kt \
       app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsViewModel.kt
```

- [ ] **Step 2: Rename symbols inside the two files**

In `SettingsScreen.kt`: rename the composable `NotificationSettingsScreen` → `SettingsScreen`, the
parameter type `NotificationSettingsViewModel` → `SettingsViewModel`, and change the title string
(line 31) from `"Notification Settings"` to `"Settings"`.

In `SettingsViewModel.kt`: rename `NotificationSettingsState` → `SettingsState` (both the `data class`
and its `MutableStateFlow`/`StateFlow` type references) and `NotificationSettingsViewModel` →
`SettingsViewModel`.

Apply with sed (verify after):
```bash
sed -i '' 's/NotificationSettingsViewModel/SettingsViewModel/g; s/NotificationSettingsState/SettingsState/g' \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsViewModel.kt
sed -i '' 's/NotificationSettingsScreen/SettingsScreen/g; s/NotificationSettingsViewModel/SettingsViewModel/g; s/"Notification Settings"/"Settings"/g' \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt
```

- [ ] **Step 3: Update MainActivity references**

In `presentation/MainActivity.kt`:
- Line 55 import: `import com.whitefang.stepsofbabylon.presentation.settings.NotificationSettingsScreen`
  → `import com.whitefang.stepsofbabylon.presentation.settings.SettingsScreen`
- Line 356 call site: `NotificationSettingsScreen(` → `SettingsScreen(`

```bash
sed -i '' 's/NotificationSettingsScreen/SettingsScreen/g' \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt
```

- [ ] **Step 4: Confirm no stale references remain**

Run: `git grep -n "NotificationSettings"`
Expected: no matches (empty output).

- [ ] **Step 5: Build + run the routing guards**

Run: `./run-gradle.sh testDebugUnitTest --tests "*DeepLinkRouting*" --tests "*OnboardingRouting*" > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL; both pass (route string `"settings"` unchanged).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/ \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt
git commit -m "refactor(ui): rename NotificationSettings* -> Settings* + retitle screen (#160)"
```

---

## Task 16: Full build, lint, and on-device validation

**Files:** none (verification only).

- [ ] **Step 1: Full unit suite + lint**

Run: `./run-gradle.sh testDebugUnitTest lintDebug > build.log 2>&1; tail -n 30 build.log`
Expected: BUILD SUCCESSFUL; 0 test failures; lint clean. Test count should be **974** (973 baseline
+ `CurrencyDisplayTest`).

- [ ] **Step 2: Assemble the debug APK**

Run: `./run-gradle.sh assembleDebug > build.log 2>&1; tail -n 10 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device visual check (emulator API 36)**

Install and walk: Labs (Start button + balances show walk/diamond icons, ⏱ → clock), Cards (Free Pack
slideshow icon, pack-open shows new/duplicate icons, gem header icon), Store (gem packs, ✅→CheckCircle
on Ad Removal/Season Pass if owned, ⭐→Star), Missions (claim checks, reward icons), Economy (claim/week
icons), Weapons (equipped CheckCircle), Battle (pause toggle is a Material play/pause icon), Settings
(titled "Settings").

- [ ] **Step 4: Navigate-away-and-return loading check (required)**

On Cards (a `WhileSubscribed`-gated screen): open it, navigate to another tab, wait >6 seconds, return.
Expected: the `LoadingBox` spinner does **not** re-flash on return (confirms the no-re-flash claim).

---

## Task 17: Documentation sync

**Files:**
- Modify: `CHANGELOG.md`, `docs/steering/source-files.md`, `CLAUDE.md`

> Per the PR Task-List Convention, this runs BEFORE the STATE/RUN_LOG update (which is done via
> `/checkpoint`, not in this plan).

- [ ] **Step 1: `CHANGELOG.md` — add an entry under `[Unreleased]`**

Add a bullet describing Bundle A: shared `CurrencyDisplay`/`LoadingBox`/`EmptyState` components;
de-emoji of currency/status/control glyphs across Labs/Cards/Store/Missions/Economy/Weapons/Onboarding/
Battle-HUD; onboarding page-dots a11y; Workshop empty-state + screen-level loading spinners; Settings
rename + retitle; deleted dead `domain/model/Currency.kt`. Note "973 → 974 JVM tests".

- [ ] **Step 2: `docs/steering/source-files.md` — add the new files**

Add entries for `presentation/ui/CurrencyDisplay.kt`, `presentation/ui/LoadingBox.kt`,
`presentation/ui/EmptyState.kt`; note the `NotificationSettings*` → `Settings*` rename and the
`Currency.kt` deletion.

- [ ] **Step 3: `CLAUDE.md` — bump the headline test count**

Change the Testing line `973 JVM tests` → `974 JVM tests`.

- [ ] **Step 4: Commit**

```bash
git add CHANGELOG.md docs/steering/source-files.md CLAUDE.md
git commit -m "docs: sync current-state docs for look-and-feel Bundle A (#160)"
```

- [ ] **Step 5: Finish with `/checkpoint`**

Run the `/checkpoint` skill to update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`. No ADR
needed (this implements the existing ADR-0022 design-tokens direction).

---

## Post-plan: open the PR

After all tasks: push the branch and open a PR to `main` titled
`feat(ui): look-and-feel Bundle A — de-emoji, loading/empty states, a11y, Settings rename (#160)`,
linking issue #160. Let CI (PR gate + instrumented) go green, then merge (squash) and close #160 with
a note that themed-currency-glyph art is a one-file `CurrencyDisplay.icon()` swap.
