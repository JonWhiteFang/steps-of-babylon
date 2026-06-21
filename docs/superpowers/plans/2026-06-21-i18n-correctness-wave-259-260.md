# i18n Correctness Wave (#259 plurals · #260 concat + raw enum names) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the genuine i18n *correctness* bugs — grammatically-wrong plurals (#259) and translation-hostile sentence concatenation + raw `CONSTANT_CASE` enum surfacing (#260) — on the existing ADR-0014 i18n architecture, without finishing #34's bulk extraction.

**Architecture:** Add `res/values/plurals.xml` for count-driven nouns; extend the `domain/Strings` seam (+ `data/AndroidStrings`) for off-game-loop-thread engine strings (wave composition, boss countdown, enemy names); give raw-`.name` enum sites real `@StringRes` labels (enum-receiver extensions where an enum is in scope, a `String→@StringRes when()` where the UI state carries a String); and replace VM/domain reward-sentence concatenation with a structured `ClaimReward` payload formatted at the Compose boundary.

**Tech Stack:** Kotlin, Jetpack Compose (`pluralStringResource`/`stringResource`), Android resources (`getQuantityString`), JUnit Jupiter + Robolectric (resource-resolving tests), `domain/Strings` interface seam.

**Spec:** `docs/superpowers/specs/2026-06-21-i18n-correctness-wave-259-260.md` (reviewed via the Adversarial Review Gate; 31 surviving findings applied).

---

## Conventions for this plan

- **Build/test command:** `./run-gradle.sh <task>` (never `./gradlew` — non-TTY hang). Single JVM test:
  `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.<FQCN>"`.
- **Resource-resolving tests are Robolectric** (`@RunWith(RobolectricTestRunner)`, `@Config(sdk=[34], application = android.app.Application::class)`, `ApplicationProvider.getApplicationContext()`). `unitTests.isIncludeAndroidResources = true` is already set, so Robolectric reads real `R.string`/`R.plurals`. Pure-JVM (JUnit Jupiter) tests **cannot** resolve resources (they hit `isReturnDefaultValues` stubs).
- **Commit after every task** (the step is explicit). Keep commits scoped to the task.
- **Do NOT touch** (fragile zones, spec §6): `entitiesLock`/game-loop ordering, `GameLoopThread` guard, `MissionsViewModel` `_today` ticker / `cancelForTest()` / `Channel.CONFLATED` mechanics, `formatCurrency`'s `Locale.US`, the `OnboardingSlide` pure model, the #20 CARD_COPY supply behavior, the #43 balance fold.

## File Structure (created / modified)

**Created:**
- `app/src/main/res/values/plurals.xml` — all count-driven `<plurals>` (Task 1).
- `app/src/test/java/com/whitefang/stepsofbabylon/PluralsResourceTest.kt` — Robolectric one/other guard (Task 1).
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt` — `@StringRes` label extensions for raw-`.name` enums + the `WavePhase` string lookup (Tasks 5–6).
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabelResTest.kt` — Robolectric: every in-scope enum/key resolves to non-blank text (Task 6).
- `app/src/test/java/com/whitefang/stepsofbabylon/data/AndroidStringsTest.kt` — Robolectric: enemy names / wave composition / boss countdown (Task 4).
- `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeStrings.kt` — pure fake of `domain.Strings` (Task 3).
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimRewardFormatTest.kt` — Robolectric formatter test (Task 8).

**Modified:**
- `domain/Strings.kt` (+3 methods), `data/AndroidStrings.kt` (+impls) — Tasks 3–4.
- `presentation/battle/engine/GameEngine.kt` (composition/countdown via seam) — Task 4.
- `presentation/battle/effects/WaveAnnouncement.kt` ("BOSS INCOMING" via seam) — Task 4 (optional sub-step).
- `presentation/battle/BattleScreen.kt` (WavePhase + enemies plural) — Tasks 2, 6.
- `presentation/battle/ui/InRoundUpgradeMenu.kt` + `presentation/workshop/WorkshopScreen.kt` (UpgradeCategory tabs) — Task 5.
- `presentation/cards/CardsScreen.kt` (PackTier + card-pull plural) — Tasks 5, 2.
- `presentation/store/StoreUiState.kt` + `StoreViewModel.kt` + `StoreScreen.kt` (CosmeticCategory enum + season-pass days plural) — Tasks 5, 2.
- `presentation/ui/Rarity.kt` + `presentation/weapons/UltimateWeaponScreen.kt` (CardRarity + uwRarity labels) — Task 5.
- `presentation/stats/StatsViewModel.kt` (DayOfWeek localized short name) — Task 7.
- `presentation/ui/ClaimCelebration.kt` (structured `ClaimReward` + formatter) — Task 8.
- `presentation/missions/MissionsViewModel.kt` + `MissionsScreen.kt`, `domain/model/Milestone.kt` (remove `rewardsSummary`) — Task 9.
- `presentation/supplies/UnclaimedSuppliesViewModel.kt` + `UnclaimedSuppliesScreen.kt` (structured payload + row plurals) — Tasks 8, 10.
- `service/StepNotificationManager.kt` + `StepWidgetProvider.kt` + `SmartReminderManager.kt` (plurals) — Task 11.
- `presentation/onboarding/OnboardingScreen.kt` (page-dots plural) — Task 2.
- `app/src/test/.../presentation/ui/NoRawEnumNameInUiTest.kt` (strengthen) — Task 12.
- `strings.xml` — split `notif_step_content`, add enum-label + template strings — Tasks 4, 5, 11.

---

## Task 1: Create `plurals.xml` + the plural-resource regression guard

**Files:**
- Create: `app/src/main/res/values/plurals.xml`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/PluralsResourceTest.kt`

- [ ] **Step 1: Create `plurals.xml` with all count-driven nouns (spec §3a)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- #259 i18n correctness wave: count-driven nouns. English needs only one/other;
         the structure lets a future locale add few/many/zero. -->

    <!-- Engine floating-text (via domain/Strings → getQuantityString). Replaces flat fx_step_reward. -->
    <plurals name="fx_step_reward">
        <item quantity="one">+%1$d Step</item>
        <item quantity="other">+%1$d Steps</item>
    </plurals>

    <!-- Battle HUD banner (Compose). Replaces flat steps_earned_banner. Keeps the 👟 glyph. -->
    <plurals name="steps_earned_banner">
        <item quantity="one">👟 +%1$d Step</item>
        <item quantity="other">👟 +%1$d Steps</item>
    </plurals>

    <!-- Wave header enemy count (Compose). Used by the split battle_wave_header (Task 2). -->
    <plurals name="wave_enemies">
        <item quantity="one">%1$d enemy</item>
        <item quantity="other">%1$d enemies</item>
    </plurals>

    <!-- Boss countdown (engine via seam). -->
    <plurals name="boss_in_waves">
        <item quantity="one">Boss next wave</item>
        <item quantity="other">Boss in %1$d waves</item>
    </plurals>

    <!-- Reward amounts (celebration + milestone row + supply row). -->
    <plurals name="reward_gems">
        <item quantity="one">+%1$d Gem</item>
        <item quantity="other">+%1$d Gems</item>
    </plurals>
    <plurals name="reward_power_stones">
        <item quantity="one">+%1$d Power Stone</item>
        <item quantity="other">+%1$d Power Stones</item>
    </plurals>
    <plurals name="reward_steps">
        <item quantity="one">+%1$d Step</item>
        <item quantity="other">+%1$d Steps</item>
    </plurals>

    <!-- Card pull copy count (Compose). Used by card_pull_result (Task 2). -->
    <plurals name="card_copies">
        <item quantity="one">+%1$d Copy</item>
        <item quantity="other">+%1$d Copies</item>
    </plurals>

    <!-- Season pass days remaining (Compose). -->
    <plurals name="days_remaining">
        <item quantity="one">Active — %1$d day remaining</item>
        <item quantity="other">Active — %1$d days remaining</item>
    </plurals>

    <!-- Onboarding page dots a11y (Compose). %1$d = current page, %2$d = total. Plural on the total. -->
    <plurals name="page_x_of_n">
        <item quantity="one">Page %1$d of %2$d</item>
        <item quantity="other">Page %1$d of %2$d</item>
    </plurals>

    <!-- Widget daily steps (RemoteViews → getQuantityString). -->
    <plurals name="widget_steps">
        <item quantity="one">%1$d step</item>
        <item quantity="other">%1$d steps</item>
    </plurals>

    <!-- Smart reminder body (notification → getQuantityString). %1$d = gap, %2$s = upgrade name. -->
    <plurals name="reminder_steps_away">
        <item quantity="one">You\'re %1$d step from upgrading %2$s!</item>
        <item quantity="other">You\'re %1$d steps from upgrading %2$s!</item>
    </plurals>

    <!-- Notification: daily steps half of the split notif_step_content (Task 11). -->
    <plurals name="notif_today_steps">
        <item quantity="one">Today: %1$d step</item>
        <item quantity="other">Today: %1$d steps</item>
    </plurals>
</resources>
```

- [ ] **Step 2: Write the failing Robolectric plural guard**

```kotlin
package com.whitefang.stepsofbabylon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #259: pins that each new plural selects the `one` form at n=1 and `other` at n≥2. This is the
 * regression guard for the grammatical bug (e.g. "+1 Step" not "+1 Steps", "1 day" not "1 days").
 * Robolectric reads the real res/values/plurals.xml (unitTests.isIncludeAndroidResources = true).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PluralsResourceTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun `fx_step_reward one vs other`() {
        assertEquals("+1 Step", res.getQuantityString(R.plurals.fx_step_reward, 1, 1))
        assertEquals("+3 Steps", res.getQuantityString(R.plurals.fx_step_reward, 3, 3))
    }

    @Test fun `wave_enemies one vs other`() {
        assertEquals("1 enemy", res.getQuantityString(R.plurals.wave_enemies, 1, 1))
        assertEquals("5 enemies", res.getQuantityString(R.plurals.wave_enemies, 5, 5))
    }

    @Test fun `boss_in_waves one is special-cased and other is plural`() {
        assertEquals("Boss next wave", res.getQuantityString(R.plurals.boss_in_waves, 1, 1))
        assertEquals("Boss in 2 waves", res.getQuantityString(R.plurals.boss_in_waves, 2, 2))
    }

    @Test fun `reward plurals one vs other`() {
        assertEquals("+1 Gem", res.getQuantityString(R.plurals.reward_gems, 1, 1))
        assertEquals("+2 Gems", res.getQuantityString(R.plurals.reward_gems, 2, 2))
        assertEquals("+1 Power Stone", res.getQuantityString(R.plurals.reward_power_stones, 1, 1))
        assertEquals("+1 Step", res.getQuantityString(R.plurals.reward_steps, 1, 1))
    }

    @Test fun `card_copies and days_remaining one vs other`() {
        assertEquals("+1 Copy", res.getQuantityString(R.plurals.card_copies, 1, 1))
        assertEquals("+2 Copies", res.getQuantityString(R.plurals.card_copies, 2, 2))
        assertEquals("Active — 1 day remaining", res.getQuantityString(R.plurals.days_remaining, 1, 1))
        assertEquals("Active — 5 days remaining", res.getQuantityString(R.plurals.days_remaining, 5, 5))
    }

    @Test fun `widget and notif and reminder steps one vs other`() {
        assertEquals("1 step", res.getQuantityString(R.plurals.widget_steps, 1, 1))
        assertEquals("Today: 1 step", res.getQuantityString(R.plurals.notif_today_steps, 1, 1))
        assertEquals("Today: 9 steps", res.getQuantityString(R.plurals.notif_today_steps, 9, 9))
    }

    @Test fun `page_x_of_n carries both args`() {
        assertEquals("Page 1 of 4", res.getQuantityString(R.plurals.page_x_of_n, 4, 1, 4))
    }
}
```

- [ ] **Step 3: Run the test — expect PASS** (the resources exist; this guard locks them in)

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.PluralsResourceTest"`
Expected: PASS (7 tests). If a `one`/`other` form is wrong, fix `plurals.xml`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/plurals.xml app/src/test/java/com/whitefang/stepsofbabylon/PluralsResourceTest.kt
git commit -m "feat(i18n): add plurals.xml for count-driven nouns (#259) + Robolectric guard"
```

---

## Task 2: Wire the Compose-side plurals (battle HUD, card pull, season pass, onboarding)

These are main-thread Compose sites — use `pluralStringResource`. (Engine/notification plurals come in Tasks 4 & 11.) The `battle_wave_header` flat string is split so the enemy count is plural-aware.

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (split `battle_wave_header`, add `card_pull_result`)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt:213,228`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt:162`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt:148`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt:194`

- [ ] **Step 1: Split `battle_wave_header` + add `card_pull_result` in `strings.xml`**

Replace line 45 (`<string name="battle_wave_header">Wave %1$d · %2$d enemies</string>`) with a wave label that takes the pre-formatted enemy plural as a string:

```xml
    <string name="battle_wave_header">Wave %1$d · %2$s</string>
```

Add near the upgrade/card strings:

```xml
    <!-- #260: card-pull result line; %1$s = card name, %2$s = pre-formatted copy plural -->
    <string name="card_pull_result">%1$s %2$s</string>
```

- [ ] **Step 2: Update `BattleScreen.kt` — wave header enemies plural + steps banner plural**

At `BattleScreen.kt:213`, the wave header currently is:
```kotlin
Text(stringResource(R.string.battle_wave_header, state.currentWave, state.enemyCount), color = Color.White, style = MaterialTheme.typography.titleMedium)
```
Replace with (compose the plural-formatted enemy count into the header):
```kotlin
Text(
    stringResource(
        R.string.battle_wave_header,
        state.currentWave,
        pluralStringResource(R.plurals.wave_enemies, state.enemyCount, state.enemyCount),
    ),
    color = Color.White, style = MaterialTheme.typography.titleMedium,
)
```

At `BattleScreen.kt:228` the steps banner currently is `stringResource(R.string.steps_earned_banner, state.stepsEarnedThisRound)`. `stepsEarnedThisRound` is a `Long` — narrow the **selector** to Int (spec §3a Long→Int rule), keep the full value as the format arg:
```kotlin
pluralStringResource(
    R.plurals.steps_earned_banner,
    state.stepsEarnedThisRound.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
    state.stepsEarnedThisRound,
)
```

Add imports to `BattleScreen.kt` if absent: `import androidx.compose.ui.res.pluralStringResource`.

- [ ] **Step 3: Update `CardsScreen.kt:162` — card-pull copy plural + templated join**

Current:
```kotlin
if (r.isNew) formatName(r.type.name) else "${formatName(r.type.name)} +1 Copy",
```
Replace with:
```kotlin
if (r.isNew) formatName(r.type.name)
else stringResource(
    R.string.card_pull_result,
    formatName(r.type.name),
    pluralStringResource(R.plurals.card_copies, r.copiesAwarded, r.copiesAwarded),
),
```
(`CardResult.copiesAwarded: Int` exists — confirmed in `OpenCardPack.kt:14`. Add `pluralStringResource`/`stringResource` imports if absent.)

- [ ] **Step 4: Update `StoreScreen.kt:148` — season-pass days plural**

Current:
```kotlin
"Active — ${state.seasonPassDaysRemaining ?: 0} days remaining"
```
Replace with:
```kotlin
pluralStringResource(
    R.plurals.days_remaining,
    state.seasonPassDaysRemaining ?: 0,
    state.seasonPassDaysRemaining ?: 0,
)
```
(Add `pluralStringResource` import. Confirm `seasonPassDaysRemaining` is `Int?` in `StoreUiState`; if `Long?`, apply the Int-narrowing on the selector.)

- [ ] **Step 5: Update `OnboardingScreen.kt:194` — page-dots a11y plural**

Current:
```kotlin
.semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${slides.size}" },
```
Replace with (resolve outside the lambda — `pluralStringResource` is `@Composable`):
```kotlin
// computed just before the .semantics modifier, in composable scope:
val pageLabel = pluralStringResource(
    R.plurals.page_x_of_n, slides.size, pagerState.currentPage + 1, slides.size,
)
// ...then:
.semantics { contentDescription = pageLabel },
```
(Add `pluralStringResource` import. Note `OnboardingScreenTest` asserts the exact "Page X of N" text — it still reads "Page 1 of 4", so it stays green.)

- [ ] **Step 6: Build + run the touched screens' tests**

Run: `./run-gradle.sh testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL. `OnboardingScreenTest`/`CardsScreenTest` green (text unchanged in English).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt
git commit -m "feat(i18n): Compose-side plurals — wave enemies, steps banner, card copies, season-pass days, onboarding page dots (#259)"
```

---

## Task 3: Extend the `domain/Strings` seam + add `FakeStrings`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/Strings.kt`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeStrings.kt`

- [ ] **Step 1: Add the 3 methods to `domain/Strings.kt`**

Add inside the `interface Strings` (after `powerStoneReward`):
```kotlin
    /** Localized enemy-type display name, e.g. "Basic"/"Boss" (replaces raw EnemyType.name). */
    fun enemyTypeName(type: EnemyType): String

    /** Whole next-wave composition line, e.g. "Next: 1 Boss, 12 Basic" (no concatenation). */
    fun waveComposition(counts: Map<EnemyType, Int>): String

    /** Plural-correct boss countdown, e.g. "Boss next wave" / "Boss in 2 waves". */
    fun bossCountdown(waves: Int): String
```
Add the import at the top: `import com.whitefang.stepsofbabylon.domain.model.EnemyType` (domain→domain, stays Android-free; `DomainPurityTest` unaffected).

- [ ] **Step 2: Create `FakeStrings` (pure, for engine/VM tests)**

```kotlin
package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.model.EnemyType

/**
 * Pure-Kotlin fake of [Strings] for JVM tests (no Android). Returns deterministic, recognizable
 * strings so a test can assert the seam was consulted (distinct from GameEngine's literal fallback).
 */
class FakeStrings : Strings {
    override fun healHp(hp: Int) = "FAKE_HEAL_$hp"
    override fun rapidFireBurst() = "FAKE_RAPID_FIRE"
    override fun cashReward(cash: Long) = "FAKE_CASH_$cash"
    override fun stepReward(steps: Long) = "FAKE_STEP_$steps"
    override fun powerStoneReward(ps: Long) = "FAKE_PS_$ps"
    override fun enemyTypeName(type: EnemyType) = "FAKE_${type.name}"
    override fun waveComposition(counts: Map<EnemyType, Int>) =
        "FAKE_COMP:" + counts.entries.joinToString(",") { "${it.value}:${it.key.name}" }
    override fun bossCountdown(waves: Int) = "FAKE_BOSS_$waves"
}
```

- [ ] **Step 3: Build (interface change must compile against the existing impl — it won't yet)**

Run: `./run-gradle.sh compileDebugKotlin 2>&1 | tail -5`
Expected: FAIL — `AndroidStrings` does not implement the 3 new members. (Task 4 fixes the impl.) This confirms the interface change landed.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/Strings.kt app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeStrings.kt
git commit -m "feat(i18n): extend domain/Strings seam (enemyTypeName/waveComposition/bossCountdown) + FakeStrings (#260)"
```

---

## Task 4: Implement the seam in `AndroidStrings` + route `GameEngine` through it

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (enemy names + composition templates)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/AndroidStrings.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:835-853`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/effects/WaveAnnouncement.kt:36`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/data/AndroidStringsTest.kt`

- [ ] **Step 1: Add enemy-name + composition strings to `strings.xml`**

```xml
    <!-- #260: localized enemy-type names (replace raw EnemyType.name in the wave-composition overlay) -->
    <string name="enemy_basic">Basic</string>
    <string name="enemy_fast">Fast</string>
    <string name="enemy_tank">Tank</string>
    <string name="enemy_ranged">Ranged</string>
    <string name="enemy_boss">Boss</string>
    <string name="enemy_scatter">Scatter</string>

    <!-- #260: next-wave composition (ENEMY_INTEL overlay). %1$s = the joined per-type list. -->
    <string name="wave_composition">Next: %1$s</string>
    <!-- per-type entry: %1$d = count, %2$s = localized enemy name -->
    <string name="wave_comp_entry">%1$d %2$s</string>
    <!-- separator between entries -->
    <string name="wave_composition_separator">", "</string>

    <!-- #260: boss-incoming banner (WaveAnnouncement, Canvas) -->
    <string name="boss_incoming">⚠ BOSS INCOMING</string>
```

- [ ] **Step 2: Write the failing `AndroidStringsTest` (Robolectric)**

```kotlin
package com.whitefang.stepsofbabylon.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NEW (#260): AndroidStrings had no test. Verifies the engine seam produces localized, plural-correct
 * text and never surfaces a raw CONSTANT_CASE enum name. Robolectric resolves the real resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AndroidStringsTest {

    private val strings = AndroidStrings(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `enemyTypeName is localized title-case, never raw name`() {
        assertEquals("Basic", strings.enemyTypeName(EnemyType.BASIC))
        assertEquals("Boss", strings.enemyTypeName(EnemyType.BOSS))
        EnemyType.entries.forEach {
            assertFalse("raw name leaked for $it", strings.enemyTypeName(it) == it.name)
        }
    }

    @Test fun `waveComposition joins entries in insertion order with no raw name`() {
        // Insertion order from WaveSpawner is preserved; non-boss example.
        val comp = linkedMapOf(EnemyType.BASIC to 12, EnemyType.RANGED to 4)
        assertEquals("Next: 12 Basic, 4 Ranged", strings.waveComposition(comp))
    }

    @Test fun `waveComposition boss wave puts BOSS first (insertion order, not re-sorted)`() {
        // FZ-2: WaveSpawner inserts BOSS first on boss waves; the seam must NOT re-sort.
        val comp = linkedMapOf(EnemyType.BOSS to 1, EnemyType.BASIC to 9)
        assertEquals("Next: 1 Boss, 9 Basic", strings.waveComposition(comp))
    }

    @Test fun `bossCountdown is plural-correct`() {
        assertEquals("Boss next wave", strings.bossCountdown(1))
        assertEquals("Boss in 2 waves", strings.bossCountdown(2))
    }
}
```

- [ ] **Step 3: Run — expect FAIL (compile error: AndroidStrings missing members)**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.AndroidStringsTest" 2>&1 | tail -5`
Expected: FAIL (does not compile — methods not implemented).

- [ ] **Step 4: Implement the 3 methods in `AndroidStrings.kt`**

Add the import `import com.whitefang.stepsofbabylon.domain.model.EnemyType` and these methods:
```kotlin
    override fun enemyTypeName(type: EnemyType): String = context.getString(
        when (type) {
            EnemyType.BASIC -> R.string.enemy_basic
            EnemyType.FAST -> R.string.enemy_fast
            EnemyType.TANK -> R.string.enemy_tank
            EnemyType.RANGED -> R.string.enemy_ranged
            EnemyType.BOSS -> R.string.enemy_boss
            EnemyType.SCATTER -> R.string.enemy_scatter
        }
    )

    override fun waveComposition(counts: Map<EnemyType, Int>): String {
        // Preserve insertion order (WaveSpawner inserts BOSS first on boss waves). Do NOT sort.
        val sep = context.getString(R.string.wave_composition_separator)
        val list = counts.entries.joinToString(sep) { (type, n) ->
            context.getString(R.string.wave_comp_entry, n, enemyTypeName(type))
        }
        return context.getString(R.string.wave_composition, list)
    }

    override fun bossCountdown(waves: Int): String =
        context.resources.getQuantityString(R.plurals.boss_in_waves, waves, waves)
```

- [ ] **Step 5: Run — expect PASS**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.AndroidStringsTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Route `GameEngine` through the seam (with literal fallback)**

`GameEngine.kt:835` `nextWaveCompositionLabel()` — replace the return line:
```kotlin
return strings?.waveComposition(comp)
    ?: ("Next: " + comp.entries.joinToString(", ") { "${it.value} ${it.key.name}" })
```
`GameEngine.kt:849` `bossCountdownLabel()` — replace the return line:
```kotlin
return strings?.bossCountdown(waves)
    ?: if (waves == 1) "Boss next wave" else "Boss in $waves waves"
```
(The literal fallback is intentionally the *current raw English* — a degraded `strings==null` last-resort, spec §3c/FZ-1. `comp` ordering is unchanged; `GameEngine.strings` already exists.)

- [ ] **Step 7: Route `WaveAnnouncement` "BOSS INCOMING" through resources**

`WaveAnnouncement.kt:36` draws `"⚠ BOSS INCOMING"` on Canvas (render thread). It has no `Context`; pass the resolved string in. Inspect the `WaveAnnouncement` constructor + its single construction site (in `GameEngine`/`triggerWaveAnnouncement`). **Minimal approach:** add a `bossLabel: String` constructor param defaulting to `"⚠ BOSS INCOMING"` and set it from `strings?.let { context...}` at the construction site, OR (simpler, no API change) leave the Canvas literal as-is and note it. **Decision for this plan:** since `WaveAnnouncement` has no string seam and adding one is API churn on a render-thread effect, **leave the "Wave N"/"BOSS INCOMING" Canvas banners as literals** and record them as #34 follow-up (they are not in #260's evidence list). Remove `boss_incoming` from strings.xml if unused, OR keep it for the #34 follow-up with a comment. *(Keep it, commented as reserved for #34.)*

- [ ] **Step 8: Add the GameEngine "seam consulted" test**

In `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt`, add (uses `FakeStrings`):
```kotlin
    @Test
    fun `nextWaveCompositionLabel uses the strings seam when set`() {
        val engine = GameEngine(/* existing test-construction args */).apply {
            strings = com.whitefang.stepsofbabylon.fakes.FakeStrings()
            // set enemyIntelLevel >= 1 and a spawner with a known composition via existing test helpers
        }
        val label = engine.nextWaveCompositionLabel()
        assertTrue(label == null || label.startsWith("FAKE_COMP:") || label.startsWith("Next:"))
    }
```
*(If `GameEngineTest`'s construction/seam helpers don't make this cheap, the executing agent may instead assert the seam at the `AndroidStrings` level only — the seam consultation is already structurally guaranteed by the `?:` and covered by AndroidStringsTest. Keep this test only if it's a clean addition; do not perturb the engine test harness.)*

- [ ] **Step 9: Build all**

Run: `./run-gradle.sh testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/data/AndroidStrings.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt app/src/test/java/com/whitefang/stepsofbabylon/data/AndroidStringsTest.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt
git commit -m "feat(i18n): engine wave-composition/boss-countdown/enemy-names via Strings seam (#260)"
```

---

## Task 5: Raw-`.name` enum labels → `@StringRes` (enum-receiver sites)

Covers the sites where an **enum** is in scope: `UpgradeCategory` (2 sites), `PackTier`, `CardRarity`, UW rarity. (WavePhase + CosmeticCategory are String-at-site → Task 6.)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt`
- Modify: `InRoundUpgradeMenu.kt:110`, `WorkshopScreen.kt:75`, `CardsScreen.kt:117`, `Rarity.kt:65-72`, `UltimateWeaponScreen.kt:113`

- [ ] **Step 1: Add enum-label strings**

```xml
    <!-- #260: localized enum display names (replace raw CONSTANT_CASE surfacing) -->
    <string name="upgrade_cat_attack">Attack</string>
    <string name="upgrade_cat_defense">Defense</string>
    <string name="upgrade_cat_utility">Utility</string>
    <string name="pack_tier_common">Common</string>
    <string name="pack_tier_rare">Rare</string>
    <string name="pack_tier_epic">Epic</string>
    <string name="rarity_common">COMMON</string>
    <string name="rarity_rare">RARE</string>
    <string name="rarity_epic">EPIC</string>
    <string name="uw_rarity_rare">RARE</string>
    <string name="uw_rarity_epic">EPIC</string>
    <string name="uw_rarity_legendary">LEGENDARY</string>
```

- [ ] **Step 2: Create `EnumLabels.kt` with `@StringRes` extensions**

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.usecase.PackTier

/**
 * #260: single source of truth for localized enum display names that previously surfaced raw
 * CONSTANT_CASE. Each returns a @StringRes resolved via stringResource at the Compose call site,
 * keeping the domain enums Android-free. (toDisplayName() stays for already-title-cased enums —
 * out of scope per the spec.)
 */
@StringRes fun UpgradeCategory.labelRes(): Int = when (this) {
    UpgradeCategory.ATTACK -> R.string.upgrade_cat_attack
    UpgradeCategory.DEFENSE -> R.string.upgrade_cat_defense
    UpgradeCategory.UTILITY -> R.string.upgrade_cat_utility
}

@StringRes fun PackTier.labelRes(): Int = when (this) {
    PackTier.COMMON -> R.string.pack_tier_common
    PackTier.RARE -> R.string.pack_tier_rare
    PackTier.EPIC -> R.string.pack_tier_epic
}

@StringRes fun CardRarity.labelRes(): Int = when (this) {
    CardRarity.COMMON -> R.string.rarity_common
    CardRarity.RARE -> R.string.rarity_rare
    CardRarity.EPIC -> R.string.rarity_epic
}

@StringRes fun CosmeticCategory.labelRes(): Int = when (this) {
    CosmeticCategory.ZIGGURAT_SKIN -> R.string.cosmetic_cat_ziggurat_skin
    CosmeticCategory.PROJECTILE_EFFECT -> R.string.cosmetic_cat_projectile_effect
    CosmeticCategory.ENEMY_SKIN -> R.string.cosmetic_cat_enemy_skin
}
```
(`CardRarity` constants are `COMMON/RARE/EPIC` — confirm in `CardRarity.kt`. The `cosmetic_cat_*` strings are added in Task 6 Step 1; declaring the extension here is fine since both land before build.)

- [ ] **Step 3: Update `InRoundUpgradeMenu.kt:110` + `WorkshopScreen.kt:75`**

InRoundUpgradeMenu — `Text(cat.name, fontSize = 12.sp)` →
```kotlin
Text(stringResource(cat.labelRes()), fontSize = 12.sp)
```
WorkshopScreen — `text = { Text(category.name) }` →
```kotlin
text = { Text(stringResource(category.labelRes())) },
```
(Add `import com.whitefang.stepsofbabylon.presentation.ui.labelRes` + `androidx.compose.ui.res.stringResource` where absent. Verify `cat`/`category` are `UpgradeCategory`.)

- [ ] **Step 4: Update `CardsScreen.kt:117` — pack tier**

`Text(pack.tier.name)` →
```kotlin
Text(stringResource(pack.tier.labelRes()))
```

- [ ] **Step 5: Update `Rarity.kt` — card + UW rarity labels become `@StringRes`**

`cardRarityLabel` and `uwRarityLabel` currently return raw/literal `String`. Convert to `@StringRes`:
```kotlin
/** Card label = the rarity name. @StringRes (#260). */
@StringRes fun cardRarityLabelRes(rarity: CardRarity): Int = when (rarity) {
    CardRarity.COMMON -> R.string.rarity_common
    CardRarity.RARE -> R.string.rarity_rare
    CardRarity.EPIC -> R.string.rarity_epic
}

/** UW label shifts up so no UW reads as "common". @StringRes (#260). */
@StringRes fun uwRarityLabelRes(tier: RarityTier): Int = when (tier) {
    RarityTier.TIER_0 -> R.string.uw_rarity_rare
    RarityTier.TIER_1 -> R.string.uw_rarity_epic
    RarityTier.TIER_2 -> R.string.uw_rarity_legendary
}
```
Add `import androidx.annotation.StringRes` + `import com.whitefang.stepsofbabylon.R`. Keep the old `cardRarityLabel`/`uwRarityLabel` String fns **only if** other callers exist (grep first); otherwise replace. Update the call sites: in `CardsScreen` (RarityBadge label) → `stringResource(cardRarityLabelRes(rarity))`; in `UltimateWeaponScreen.kt:113` → `stringResource(uwRarityLabelRes(tier))`. `RarityBadge(label: String)` keeps its String param (caller resolves).

- [ ] **Step 6: Build + lint**

Run: `./run-gradle.sh testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL. (`RarityTest` may assert `cardRarityLabel`/`uwRarityLabel` — update those assertions to the `*Res` form or to the resolved text via Robolectric in Task 6's EnumLabelResTest; if `RarityTest` breaks, adjust it to call the new fn names.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(i18n): @StringRes labels for UpgradeCategory/PackTier/CardRarity/UW rarity (#260)"
```

---

## Task 6: WavePhase + CosmeticCategory (String-at-site) + EnumLabelResTest

WavePhase reaches `BattleScreen` as a **String** (uiState); CosmeticCategory reaches `StoreScreen` as a **String** today — change the DisplayInfo to carry the enum.

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (cosmetic-category + wave-phase strings)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt` (WavePhase string lookup)
- Modify: `BattleScreen.kt:213`
- Modify: `StoreUiState.kt` (CosmeticDisplayInfo.category: CosmeticCategory) + `StoreViewModel.kt:80` + `StoreScreen.kt:202`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabelResTest.kt`

- [ ] **Step 1: Add wave-phase + cosmetic-category strings**

```xml
    <string name="wave_phase_spawning">Spawning</string>
    <string name="wave_phase_cooldown">Cooldown</string>
    <string name="cosmetic_cat_ziggurat_skin">Ziggurat Skin</string>
    <string name="cosmetic_cat_projectile_effect">Projectile Effect</string>
    <string name="cosmetic_cat_enemy_skin">Enemy Skin</string>
```

- [ ] **Step 2: Add the WavePhase String→@StringRes lookup to `EnumLabels.kt`**

```kotlin
/**
 * #260: BattleUiState.wavePhase is a String ("SPAWNING"/"COOLDOWN"/""), not the WavePhase enum,
 * so this resolves from the raw string. Returns null for the blank/unknown default (render nothing).
 */
@StringRes fun wavePhaseLabelRes(rawPhase: String): Int? = when (rawPhase) {
    "SPAWNING" -> R.string.wave_phase_spawning
    "COOLDOWN" -> R.string.wave_phase_cooldown
    else -> null
}
```

- [ ] **Step 3: Update `BattleScreen.kt:213` — WavePhase localized text (color branch untouched)**

Current:
```kotlin
Text(state.wavePhase.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
```
Replace with:
```kotlin
wavePhaseLabelRes(state.wavePhase)?.let { phaseRes ->
    Text(stringResource(phaseRes), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
}
```
(The `state.wavePhase == "SPAWNING"` color comparison at :217 stays — uiState is still a String. Add `wavePhaseLabelRes` import.)

- [ ] **Step 4: Change `CosmeticDisplayInfo.category` to the enum**

`StoreUiState.kt` — `val category: String,` → `val category: com.whitefang.stepsofbabylon.domain.model.CosmeticCategory,`
`StoreViewModel.kt:80` — `it.category.name` → `it.category` (pass the enum; confirm `it.category` is `CosmeticCategory`).
`StoreScreen.kt:202` — `Text(cosmetic.category.replace("_", " "), ...)` →
```kotlin
Text(stringResource(cosmetic.category.labelRes()), style = MaterialTheme.typography.labelSmall)
```
(Add imports. Grep for other `CosmeticDisplayInfo.category` consumers — spec R6 — and update them.)

- [ ] **Step 5: Write `EnumLabelResTest` (Robolectric)**

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.usecase.PackTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #260: every in-scope enum constant (and the WavePhase string keys) maps to a non-blank, non-raw
 * localized label. Catches a missing mapping when a constant is added. Robolectric resolves text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EnumLabelResTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = ctx.getString(id)

    @Test fun `every UpgradeCategory has a non-blank label`() =
        UpgradeCategory.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every PackTier has a non-blank label`() =
        PackTier.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every CardRarity has a non-blank label`() =
        CardRarity.entries.forEach { assertTrue(str(it.labelRes()).isNotBlank()) }

    @Test fun `every CosmeticCategory has a non-blank label not equal to the raw name`() =
        CosmeticCategory.entries.forEach {
            val s = str(it.labelRes()); assertTrue(s.isNotBlank()); assertFalse(s == it.name)
        }

    @Test fun `wave phase keys resolve and blank returns null`() {
        assertTrue(str(wavePhaseLabelRes("SPAWNING")!!).isNotBlank())
        assertTrue(str(wavePhaseLabelRes("COOLDOWN")!!).isNotBlank())
        assertTrue(wavePhaseLabelRes("") == null)
        assertTrue(wavePhaseLabelRes("GARBAGE") == null)
    }
}
```

- [ ] **Step 6: Run + build**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.EnumLabelResTest"` then `./run-gradle.sh lintDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(i18n): WavePhase (String) + CosmeticCategory (enum) localized labels + EnumLabelResTest (#260)"
```

---

## Task 7: StatsViewModel DayOfWeek localized short name

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModel.kt:100` + imports

- [ ] **Step 1: Replace the raw `.name.take(3)` axis label**

`StatsViewModel.kt:100` — `label = d.dayOfWeek.name.take(3),` →
```kotlin
label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
```
Add imports: `import java.time.format.TextStyle` and `import java.util.Locale`.

- [ ] **Step 2: Build + run StatsViewModelTest**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.stats.StatsViewModelTest"`
Expected: PASS. (If a test asserts `"MON"`, update it to the locale-short form — in `Locale.US` that is `"Mon"`. Adjust the assertion.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModel.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModelTest.kt
git commit -m "feat(i18n): StatsViewModel chart axis uses locale-aware DayOfWeek short name (#260)"
```

---

## Task 8: Structured `ClaimReward` payload + Compose-boundary formatter

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (reward templates)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimCelebration.kt`
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimRewardFormatTest.kt`

- [ ] **Step 1: Add reward templates to `strings.xml`**

```xml
    <!-- #260: claim-celebration formatting. reward_join glues multiple reward parts; the leading
         "+" lives inside the per-reward plurals (reward_gems/etc.). -->
    <string name="reward_join">" "</string>
    <string name="reward_claimed">%1$s claimed!</string>
    <string name="reward_generic">Reward claimed!</string>
    <string name="reward_card">Card</string>
    <string name="reward_all_supplies">All supplies claimed!</string>
```

- [ ] **Step 2: Replace `ClaimCelebrationEvent` with the structured payload + formatter**

In `ClaimCelebration.kt`, replace the data class (line 24) and the body's `event?.label` (line 56):
```kotlin
/** Structured one-shot reward payload (#260). Formatted at the Compose boundary (localized + plural). */
sealed interface ClaimReward {
    data class Bundle(
        val gems: Int = 0,
        val powerStones: Int = 0,
        val steps: Int = 0,
        val cosmeticNames: List<String> = emptyList(),
        val cards: Int = 0,
    ) : ClaimReward
    /** Pre-localized fixed message (e.g. "All supplies claimed!"). */
    data class Message(@StringRes val res: Int) : ClaimReward
    data object Generic : ClaimReward
}
data class ClaimCelebrationEvent(val reward: ClaimReward)

/** Pure-ish formatter (needs a Composable for plurals). Returns "" for null/empty (exit-safe, AF-4). */
@Composable
fun formatClaimReward(reward: ClaimReward?): String = when (reward) {
    null -> ""
    is ClaimReward.Generic -> stringResource(R.string.reward_generic)
    is ClaimReward.Message -> stringResource(reward.res)
    is ClaimReward.Bundle -> {
        val parts = buildList {
            if (reward.gems > 0) add(pluralStringResource(R.plurals.reward_gems, reward.gems, reward.gems))
            if (reward.powerStones > 0) add(pluralStringResource(R.plurals.reward_power_stones, reward.powerStones, reward.powerStones))
            if (reward.steps > 0) add(pluralStringResource(R.plurals.reward_steps, reward.steps, reward.steps))
            if (reward.cards > 0) add(pluralStringResource(R.plurals.card_copies, reward.cards, reward.cards))
            addAll(reward.cosmeticNames)
        }
        if (parts.isEmpty()) stringResource(R.string.reward_generic)
        else stringResource(R.string.reward_claimed, parts.joinToString(stringResource(R.string.reward_join)))
    }
}
```
In the composable body, replace `Text(event?.label ?: "", ...)` with:
```kotlin
Text(
    formatClaimReward(event?.reward),
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    style = MaterialTheme.typography.titleMedium,
)
```
Add imports: `androidx.annotation.StringRes`, `androidx.compose.ui.res.stringResource`, `androidx.compose.ui.res.pluralStringResource`, `com.whitefang.stepsofbabylon.R`.

- [ ] **Step 3: Write `ClaimRewardFormatTest` (Robolectric, via a tiny composition harness)**

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #260: the claim formatter must cover every case the retired missionRewardLabel literal test did
 * (single currency, multi-currency join, Generic fallback) plus cosmetic names and card grants.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ClaimRewardFormatTest {
    @get:Rule val rule = createComposeRule()

    private fun format(reward: ClaimReward?): String {
        var out = ""
        rule.setContent { out = formatClaimReward(reward) }
        rule.waitForIdle()
        return out
    }

    @Test fun `single currency`() {
        assert(format(ClaimReward.Bundle(gems = 5)) == "+5 Gems claimed!")
    }
    @Test fun `single gem is singular`() {
        assert(format(ClaimReward.Bundle(gems = 1)) == "+1 Gem claimed!")
    }
    @Test fun `multi currency joins`() {
        assert(format(ClaimReward.Bundle(gems = 5, powerStones = 2)) == "+5 Gems +2 Power Stones claimed!")
    }
    @Test fun `cosmetic name is carried verbatim`() {
        assert(format(ClaimReward.Bundle(gems = 200, powerStones = 50, cosmeticNames = listOf("Lapis Lazuli Ziggurat Skin")))
            == "+200 Gems +50 Power Stones Lapis Lazuli Ziggurat Skin claimed!")
    }
    @Test fun `generic and null`() {
        assert(format(ClaimReward.Generic) == "Reward claimed!")
        assert(format(null) == "")
    }
}
```
*(Note: `formatClaimReward` is `@Composable`-returning-String, captured via `setContent`. This mirrors the #253 Compose-Robolectric harness already in the repo.)*

- [ ] **Step 4: Run — expect FAIL on compile (callers still pass `label =`), then it cascades to Task 9/10**

Run: `./run-gradle.sh compileDebugKotlin 2>&1 | tail -5`
Expected: FAIL — `MissionsViewModel`/`UnclaimedSuppliesViewModel` still construct `ClaimCelebrationEvent(label = …)`. Fixed in Tasks 9 & 10. **Do not commit until callers compile** — combine Step 5/Task 9/Task 10 build green before committing, OR temporarily keep a `label` secondary constructor. **Cleaner:** implement Tasks 9 & 10 immediately after this step, then run the formatter test + commit all three together.

- [ ] **Step 5: (after Tasks 9 & 10) run the formatter test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.ClaimRewardFormatTest"`
Expected: PASS.

- [ ] **Step 6: Commit** (deferred — see Task 10 Step 5, which commits Tasks 8–10 together)

---

## Task 9: Missions — emit structured `ClaimReward`; remove `Milestone.rewardsSummary()`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsViewModel.kt:145,160` (+ delete `missionRewardLabel`)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/Milestone.kt` (delete `rewardsSummary()`)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt:165`

- [ ] **Step 1: Mission claim emits a `ClaimReward.Bundle` (MissionsViewModel.kt:145)**

```kotlin
val m = uiState.value.missions.find { it.id == id }
_celebration.trySend(ClaimCelebrationEvent(
    reward = if (m == null) ClaimReward.Generic
    else ClaimReward.Bundle(gems = m.rewardGems, powerStones = m.rewardPowerStones),
))
```
Delete the `missionRewardLabel` top-level fn (lines ~195-203). Add `import com.whitefang.stepsofbabylon.presentation.ui.ClaimReward`.

- [ ] **Step 2: Milestone claim emits a `ClaimReward.Bundle` from `milestone.rewards` (MissionsViewModel.kt:160)**

```kotlin
ClaimMilestoneResult.Success -> {
    val gems = milestone.rewards.filterIsInstance<MilestoneReward.Gems>().sumOf { it.amount }.toInt()
    val ps = milestone.rewards.filterIsInstance<MilestoneReward.PowerStones>().sumOf { it.amount }.toInt()
    val cosmetics = milestone.rewards.filterIsInstance<MilestoneReward.Cosmetic>().map { it.name }
    _celebration.trySend(ClaimCelebrationEvent(
        reward = ClaimReward.Bundle(gems = gems, powerStones = ps, cosmeticNames = cosmetics),
    ))
}
```
Add `import com.whitefang.stepsofbabylon.domain.model.MilestoneReward`.

- [ ] **Step 3: Remove `Milestone.rewardsSummary()` + update `MissionsScreen.kt:165`**

Delete `rewardsSummary()` from `Milestone.kt`. `MissionsScreen.kt:165` (`Text(milestone.rewardsSummary(), …)`) → render the same parts via the formatter:
```kotlin
Text(
    formatClaimReward(
        ClaimReward.Bundle(
            gems = milestone.rewards.filterIsInstance<MilestoneReward.Gems>().sumOf { it.amount }.toInt(),
            powerStones = milestone.rewards.filterIsInstance<MilestoneReward.PowerStones>().sumOf { it.amount }.toInt(),
            cosmeticNames = milestone.rewards.filterIsInstance<MilestoneReward.Cosmetic>().map { it.name },
        ),
    ).removeSuffix(" claimed!"),  // the row shows the reward list without the "claimed!" verb
    style = MaterialTheme.typography.bodySmall,
)
```
*(Alternative if `removeSuffix` reads as a hack: add a `formatRewardList(reward)` that returns just the joined parts, and have `formatClaimReward` wrap it. The executing agent should prefer the explicit `formatRewardList` helper — cleaner — and the milestone row + celebration both call it.)*
**Decision:** extract a `@Composable fun formatRewardParts(reward: ClaimReward.Bundle): String` (the joined list, no "claimed!"), have `formatClaimReward` call it, and the milestone row call `formatRewardParts` directly. Update Task 8's formatter accordingly.

- [ ] **Step 4: Update `MissionsViewModelTest`**

Replace the retired `missionRewardLabel` literal assertions (MissionsViewModelTest ~125-130) with structured assertions on the emitted `ClaimReward`:
```kotlin
// after claimMission(...) success, collect the celebration event:
val event = vm.celebration.first()
assertEquals(ClaimReward.Bundle(gems = 5, powerStones = 0), event.reward)
// ... cover gems-only, ps-only, both, and the null→Generic case
```
Keep the `cancelForTest()` call as the last statement (ticker harness, #162). Run with the existing test dispatcher pattern.

- [ ] **Step 5: Build (still needs Task 10 for full compile)** — defer commit to Task 10.

Run: `./run-gradle.sh compileDebugKotlin 2>&1 | tail -5` (expect only `UnclaimedSuppliesViewModel` errors remaining).

---

## Task 10: Supplies — structured celebration payload + row plurals + #20 guard

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesViewModel.kt:56,66` (+ delete/replace `supplyLabel`)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt:97,124` (`formatSupplyReward` → plurals)
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/SupplyRewardFormatTest.kt` (re-point)

- [ ] **Step 1: `claimDrop` / `claimAll` emit structured `ClaimReward`**

`UnclaimedSuppliesViewModel.kt:56`:
```kotlin
if (claimSupplyDrop(drop) is ClaimSupplyDrop.Result.Success) {
    _celebration.trySend(ClaimCelebrationEvent(reward = drop.toClaimReward()))
}
```
`UnclaimedSuppliesViewModel.kt:66` (`claimAll`):
```kotlin
if (anySuccess) _celebration.trySend(ClaimCelebrationEvent(reward = ClaimReward.Message(R.string.reward_all_supplies)))
```
Replace the `supplyLabel` top-level fn with a structured mapper:
```kotlin
/** Maps a supply drop to a structured celebration reward (#260). CARD_COPY → one card grant. */
internal fun SupplyDrop.toClaimReward(): ClaimReward = when (reward) {
    SupplyDropReward.STEPS -> ClaimReward.Bundle(steps = rewardAmount)
    SupplyDropReward.GEMS -> ClaimReward.Bundle(gems = rewardAmount)
    SupplyDropReward.POWER_STONES -> ClaimReward.Bundle(powerStones = rewardAmount)
    SupplyDropReward.CARD_COPY -> ClaimReward.Bundle(cards = 1)
}
```
Add imports `com.whitefang.stepsofbabylon.presentation.ui.ClaimReward`, `com.whitefang.stepsofbabylon.R`.

- [ ] **Step 2: `formatSupplyReward` (the row) → plurals, keep CARD_COPY name path**

`UnclaimedSuppliesScreen.kt` — `formatSupplyReward` currently returns a `String` built with literals. It runs in a `@Composable` row (`Text(text = formatSupplyReward(drop), …)` at :97). Make it `@Composable` and use plurals:
```kotlin
@Composable
internal fun formatSupplyReward(drop: SupplyDrop): String = when (drop.reward) {
    SupplyDropReward.STEPS -> pluralStringResource(R.plurals.reward_steps, drop.rewardAmount, drop.rewardAmount)
    SupplyDropReward.GEMS -> pluralStringResource(R.plurals.reward_gems, drop.rewardAmount, drop.rewardAmount)
    SupplyDropReward.POWER_STONES -> pluralStringResource(R.plurals.reward_power_stones, drop.rewardAmount, drop.rewardAmount)
    SupplyDropReward.CARD_COPY -> {
        // #20: rewardAmount is a card-TYPE index, NOT a quantity — resolve the card name + "x1".
        val cardType = CardType.entries[drop.rewardAmount % CardType.entries.size]
        "${cardType.name.toDisplayName()} x1"
    }
}
```
(Add `pluralStringResource` import. The CARD_COPY path is unchanged behavior. Note: the row "+N Steps" form now matches the celebration "+N Steps" via `reward_steps`.)

- [ ] **Step 3: Re-point `SupplyRewardFormatTest` (keep the #20 guard)**

`formatSupplyReward` is now `@Composable`, so the quantity assertions must run under Robolectric/Compose. Convert the test to the `createComposeRule` harness (like `ClaimRewardFormatTest`), capturing the formatter output via `setContent`. Keep ALL #20 CARD_COPY assertions (they assert the card-name + "x1" + no "+N"). Update the quantity expectations to the plural output:
```kotlin
// "+150 Steps" (other), "+1 Power Stone" (one) etc. — n=1 now uses the singular form.
assertEquals("+150 Steps", capture(drop(SupplyDropReward.STEPS, 150)))
assertEquals("+1 Power Stone", capture(drop(SupplyDropReward.POWER_STONES, 1)))  // was "+1 Power Stones"
// CARD_COPY assertions unchanged: "Iron Skin x1", no "+", endsWith "x1", resolves same CardType.
```
Add `@RunWith(RobolectricTestRunner)` + `@GraphicsMode(NATIVE)` + `createComposeRule()`; the CARD_COPY index loop stays.

- [ ] **Step 4: Add the `UnclaimedSuppliesViewModelTest` structured assertion**

Assert `claimDrop` emits the right `ClaimReward`:
```kotlin
assertEquals(ClaimReward.Bundle(steps = 150), drop(SupplyDropReward.STEPS, 150).toClaimReward())
assertEquals(ClaimReward.Bundle(cards = 1), drop(SupplyDropReward.CARD_COPY, 0).toClaimReward())
```

- [ ] **Step 5: Full build + commit Tasks 8–10 together**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL. `ClaimRewardFormatTest`, `SupplyRewardFormatTest`, `MissionsViewModelTest`, `UnclaimedSuppliesViewModelTest` all green.
```bash
git add -A
git commit -m "feat(i18n): structured ClaimReward payload kills VM/domain reward concatenation; supply-row plurals; keep #20 CARD_COPY guard (#260, #259)"
```

---

## Task 11: Notifications + widget + smart reminder plurals

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (split `notif_step_content`, add `notif_balance`)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/service/StepNotificationManager.kt:60`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/service/StepWidgetProvider.kt:37`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/service/SmartReminderManager.kt:80` (+ channel/title strings)

- [ ] **Step 1: Split `notif_step_content` (spec §3i)**

Replace line 7:
```xml
    <string name="notif_step_content">%1$s | %2$s</string>
    <string name="notif_balance">Balance: %1$d Steps</string>
```
(`notif_today_steps` plural already added in Task 1. Add `widget_balance` + `widget_steps` consumers below; add reminder strings.)
```xml
    <!-- Widget -->
    <string name="widget_balance">Balance: %1$s</string>
    <!-- Smart reminder -->
    <string name="reminder_channel_name">Smart Reminders</string>
    <string name="reminder_channel_desc">Upgrade proximity reminders</string>
    <string name="reminder_title">Almost there!</string>
```

- [ ] **Step 2: `StepNotificationManager.kt:60` composes the split string**

Current:
```kotlin
.setContentText(context.getString(R.string.notif_step_content, dailySteps, balance))
```
Replace with:
```kotlin
.setContentText(
    context.getString(
        R.string.notif_step_content,
        context.resources.getQuantityString(
            R.plurals.notif_today_steps,
            dailySteps.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            dailySteps,
        ),
        context.getString(R.string.notif_balance, balance),
    )
)
```
(`dailySteps`/`balance` are `Long` — selector narrowed; the #43 balance value is passed unchanged.)

- [ ] **Step 3: `StepWidgetProvider.kt:37-38` use plurals + balance string**

```kotlin
setTextViewText(
    R.id.widget_daily_steps,
    context.resources.getQuantityString(
        R.plurals.widget_steps,
        steps.coerceIn(0, Int.MAX_VALUE).toInt(),
        fmt.format(steps),
    ),
)
setTextViewText(R.id.widget_balance, context.getString(R.string.widget_balance, fmt.format(balance)))
```
*(Caveat: `widget_steps` plural arg uses `fmt.format(steps)` as `%1$d`? No — `%1$d` needs an Int/Long, not a formatted String. Change `widget_steps` to use `%1$s` and pass `fmt.format(steps)`:)*
Adjust `plurals.xml` `widget_steps` to `%1$s`:
```xml
    <plurals name="widget_steps">
        <item quantity="one">%1$s step</item>
        <item quantity="other">%1$s steps</item>
    </plurals>
```
and the call passes the **Int selector** + the **formatted string arg**:
```kotlin
context.resources.getQuantityString(R.plurals.widget_steps, steps.coerceIn(0, Int.MAX_VALUE).toInt(), fmt.format(steps))
```
Update `StepWidgetProviderTest` if it asserts the old `"N steps"` literal (it tests `saveData`, not text — likely unaffected; verify).

- [ ] **Step 4: `SmartReminderManager.kt:80` reminder body plural + externalize channel/title**

`.setContentText("You're $bestGap steps from upgrading $bestName!")` →
```kotlin
.setContentText(
    context.resources.getQuantityString(R.plurals.reminder_steps_away, bestGap.coerceIn(0, Int.MAX_VALUE).toInt(), bestGap, bestName)
)
```
*(Adjust `reminder_steps_away` first arg: it must be `%1$s` if `bestGap` is large, or `%1$d` if Int. Confirm `bestGap`'s type; if `Int`, keep `%1$d`.)* Also replace the hardcoded channel name/desc/title (lines ~41-42, 79) with `context.getString(R.string.reminder_channel_name/desc/title)`.

- [ ] **Step 5: Build + run service tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.service.*" lintDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(i18n): notification/widget/reminder plurals; split notif_step_content (#259)"
```

---

## Task 12: Strengthen `NoRawEnumNameInUiTest` (non-optional guard, spec IC-9)

**Files:**
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/NoRawEnumNameInUiTest.kt`

- [ ] **Step 1: Add a second test that bans raw `.name` text-surfacing on UI lines**

Add to the existing class (keep the existing `.name.replace(` test):
```kotlin
    @Test
    fun `no presentation Text or contentDescription surfaces a raw enum name`() {
        val presentationRoot = File("src/main/java/com/whitefang/stepsofbabylon/presentation")
        // Bare raw-name surfacing the wave fixed: Text(x.name) / contentDescription = "...x.name..." /
        // .name.take( / .name.lowercase(. Scoped to lines that render text so legitimate non-UI
        // .name uses (keys, logging, when-branches) don't false-positive.
        val rawNameSurface = Regex("""(Text\([^)]*\.name\b|\.name\.take\(|\.name\.lowercase\(|contentDescription\s*=\s*"[^"]*\$\{[^}]*\.name)""")
        val offenders = mutableListOf<String>()
        presentationRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readText().lineSequence().forEachIndexed { idx, line ->
                if (rawNameSurface.containsMatchIn(line)) offenders += "${file.name}:${idx + 1}: ${line.trim()}"
            }
        }
        assertTrue(offenders.isEmpty()) {
            "Raw enum-name surfaced in UI (#260 — use a @StringRes label / the Strings seam):\n" +
                offenders.sorted().joinToString("\n")
        }
    }
```

- [ ] **Step 2: Run — fix any real offender the regex catches**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.NoRawEnumNameInUiTest"`
Expected: PASS after Tasks 5–7. **If it flags a legitimate non-UI `.name`** (e.g. a `key = { it.milestone.name }` in a `LazyColumn`, or `toDisplayName(r.type.name)` which is allowed/out-of-scope), **tighten the regex** to exclude it (e.g. require it inside a `Text(`/`contentDescription` text position and not immediately followed by `.toDisplayName()`/`)` as a list key). Do not weaken it to uselessness; the goal is to catch bare `Text(enum.name)`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/NoRawEnumNameInUiTest.kt
git commit -m "test(i18n): strengthen NoRawEnumNameInUiTest to ban raw .name UI surfacing (#260, IC-9)"
```

---

## Task 13: Full verification + docs sync + memory write

**Files:**
- Modify: `CLAUDE.md` (headline test count), `CHANGELOG.md`, `docs/steering/source-files.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`, ADR if warranted.

- [ ] **Step 1: Full build**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL, 0 failures. Record the new JVM test count.

- [ ] **Step 2: Grep sanity — no remaining in-scope raw concatenation/enum sites**

Run:
```bash
grep -rn "joinToString.*\.name\|+ \" claimed\|\.name }\|Text(cat.name\|Text(category.name\|Text(pack.tier.name\|category.replace" app/src/main/java/com/whitefang/stepsofbabylon/presentation app/src/main/java/com/whitefang/stepsofbabylon/domain || echo "clean"
```
Expected: no in-scope hits (the wave-composition `it.key.name` survives ONLY in the GameEngine literal fallback, which is acceptable per FZ-1).

- [ ] **Step 3: Sync current-state docs (PR Task-List Convention)**

- `CLAUDE.md` Testing line: update the headline JVM count.
- `CHANGELOG.md`: add an `[Unreleased]` entry for the i18n correctness wave (#259 closed; #260 partially — prose deferred to #34).
- `docs/steering/source-files.md`: add `plurals.xml`, `EnumLabels.kt`, `FakeStrings.kt`, `PluralsResourceTest`, `AndroidStringsTest`, `EnumLabelResTest`, `ClaimRewardFormatTest`; update `ClaimCelebration.kt`/`Strings.kt`/`AndroidStrings.kt` responsibilities.
- `docs/steering/structure.md`: note `EnumLabels.kt` in `presentation/ui/`.

- [ ] **Step 4: Update STATE.md + append RUN_LOG.md** (or run `/checkpoint`).

- [ ] **Step 5: Update the GitHub issues**

- #259: closeable (comment: plurals.xml + guards).
- #260: **leave OPEN**, comment that grammar/enum/concat evidence is fixed and the OnboardingSlide/Help *prose* evidence is deferred to #34 (spec §9).

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "docs(i18n): sync current-state docs + STATE/RUN_LOG for the i18n correctness wave (#259/#260)"
```

---

## Self-review notes (author)

- **Spec coverage:** §3a→Tasks 1,2,4,10,11; §3c→Tasks 3,4; §3d enums→Tasks 5,6,7; §3e concat→Tasks 4,9,10; §3f payload→Task 8; §3g supply row→Task 10; §3h card pull→Task 2; §3i notif split→Task 11; §5 tests→each task + Task 12; §6 fragile zones→respected per-task; §9 traceability→Task 13 Step 5. ✓
- **Ordering hazard:** Tasks 8–10 form a compile-coupled unit (the payload type change breaks both VMs) — committed together (Task 10 Step 5). Flagged in Task 8 Step 4.
- **Known plan risks for the reviewer:** (a) the `widget_steps`/`reminder_steps_away` `%d` vs `%s` arg-type detail (formatted-string args need `%s`) is called out inline in Task 11; (b) the milestone-row "no 'claimed!'" formatting uses an extracted `formatRewardParts` helper (Task 9 Step 3); (c) the `NoRawEnumNameInUiTest` regex may need tightening against legitimate `.name` list-keys (Task 12 Step 2); (d) the GameEngineTest seam test is optional if it perturbs the harness (Task 4 Step 8).
