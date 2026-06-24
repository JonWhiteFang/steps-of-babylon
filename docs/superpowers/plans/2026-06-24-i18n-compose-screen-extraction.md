# i18n Compose-Screen String Extraction (phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the hardcoded English UI strings from the standard Compose screens (+ their dialogs, shared `presentation/ui/` components, two battle/ui Compose components, and the navigation `secondaryTitle` titles) into `res/values/strings.xml`, referenced via `stringResource`.

**Architecture:** Pure phase-2 of ADR-0014. Every screen already has the reference pattern from phase 1 (workshop, battle). Most of the work is mechanical: move a literal into `strings.xml` under a `screen_element` snake_case key, replace the literal with `stringResource(R.string.key)` (or `stringResource(R.string.key, arg)` for templates with embedded data). No behavior change. Two structural changes: (1) `Screen.secondaryTitle()` returns `String?` today and is called from a `@Composable` slot → changes to `@StringRes Int?`, resolved at the `MainActivity` call site (Task 13); (2) the non-`@Composable` string-helper functions (`CurrencyType.label()`, `pathLabel()`, the `"Done!"`/`"Just now"` formatter early-returns) likewise change to `@StringRes Int` / call-site resolution (Task 14).

**Task count:** 15 tasks across 2 PRs (Tasks 1–5 = PR1; Tasks 6–15 = PR2, where Task 14 is the helper conversions and Task 15 is verification + PR open). PR3 (deferred Canvas/Activity) is a separate later effort.

**Review:** this plan passed the Adversarial Review Gate (ultracode, multi-agent) on 2026-06-24 — 18 findings, 17 surviving, applied here. The review's central correction: the original inventory grepped only `Text("…")` and undercounted (it missed helper-composable String args, dialog bodies, conditional text, and the non-composable string helpers). The Methodology section below is the result; the per-task tables are now scaffolds, not the completeness gate. Also fixed: a CRITICAL `economy_steps_to_ps` format-arg (`%2$d` not `%2$s`) and the UltimateWeaponBar per-slot `val` hoist.

**Tech Stack:** Kotlin, Jetpack Compose (`androidx.compose.ui.res.stringResource`), Android string resources, Robolectric Compose UI tests (JVM lane), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-24-i18n-compose-screen-extraction-design.md` (passed the Adversarial Review Gate 2026-06-24).

---

## ⚠️ Methodology (read FIRST — the per-task line tables are NON-EXHAUSTIVE starting points)

The adversarial review found the line-by-line tables in this plan **undercount** each screen — the
original inventory grepped only `Text("…")` and missed several literal categories. **Do NOT treat a
task's table as the complete list.** For every screen, the authoritative step is:

1. **Grep the file comprehensively** for English literals, not just `Text("…")`. Run, per file:
   ```bash
   grep -nE '"[A-Z][a-z]|"[a-z]+ [a-z]' <file> | grep -vE '^\s*//|^\s*\*|import |\.typography|MaterialTheme|Color\.|fontWeight|tag = '
   ```
2. **Migrate EVERY English literal found**, including these categories the original tables missed:
   - **Strings passed as arguments to helper composables** — e.g. `ToggleRow("Supply Drops", "Notifications for walking rewards", …)`, `CurrencyItem("Steps", …)`, `BalanceCard("Gems", …)`, `StatRow("Rounds Played", …)`, `MenuButton(label = "Settings", …)`, `EmptyState(title = "No cards yet", message = "…")`. These render as user-visible text and MUST be extracted (pass `stringResource(R.string.x)` as the argument).
   - **Conditional / branch text** — e.g. `if (state.equippedCount >= 3) Text("Equipped: 3/3 — unequip one to swap")`, `if (reached) "Ready!" else "—"`.
   - **`AlertDialog` body `text = { Text("…") }`** (not just the title) — e.g. the two delete-confirmation dialog bodies in SettingsScreen.
   - **Empty-state / zero-state messages.**
3. **Leave data-only literals alone** — `Text("${x}")`, `"$pct%"`, `if (reached) … else "—"` (the `"—"` em-dash is a glyph, not translatable — but `"Ready!"` IS). Use judgment per the format-arg rule below.
4. **Non-`@Composable` helper functions that RETURN English strings** (e.g. `CurrencyType.label()`, `formatTime()`'s `"Done!"`, `formatTimeAgo()`'s `"Just now"`, `pathLabel()`'s `"Damage"`/`"Cooldown"`) cannot call `stringResource`. These are handled by **Task 14** (convert to `@StringRes Int` + resolve at the Compose call site). Do NOT hardcode-skip them.

The per-task tables below capture the *known* literals (incl. the ones the review surfaced). Use them as a scaffold, but the grep in step 1 is the completeness gate — if it finds a literal not in the table, extract it too (add a sensibly-named key).

## Conventions for EVERY task (read once)

- **Key naming:** `screen_element` snake_case (e.g. `settings_sound_header`, `store_section_gem_packs`). Reuse existing shared keys where one already fits (`action_resume`, `action_continue`, `upgrade_max` etc.) rather than minting a duplicate — grep `strings.xml` first.
- **Static literal** → `stringResource(R.string.key)`. **Literal with embedded data** (`"Lab Slots: ${a}/${b}"`) → format-arg resource `"Lab Slots: %1$d/%2$d"` + `stringResource(R.string.key, a, b)`.
- **Do NOT extract data-only `Text("${…}")`** (no English words, e.g. `Text("${slot.cooldownRemaining.toInt()}")`, `Text("$t")`, `Text("${tier.cashMultiplier}x")`, `Text("$badgeCount")`) — these carry no translatable text. They are explicitly LEFT ALONE.
- **Escaping:** apostrophes `\'`; meaningful leading/trailing space → wrap the whole value in `"…"` (e.g. `<string name="x">"Unlock Slot "</string>`).
- **`stringResource` import:** add `import androidx.compose.ui.res.stringResource` to any screen that doesn't already have it. For `pluralStringResource`, import `androidx.compose.ui.res.pluralStringResource`.
- **`contentDescription`** literals are extracted too.
- **Where a string contains an emoji or special glyph** (`"✕"`, `"• Daily Gems: …"`), keep the value byte-identical including the glyph.
- **Build command:** `./run-gradle.sh :app:testDebugUnitTest` (full JVM suite) and `./run-gradle.sh :app:assembleDebug` (compile + resource link). Save output to a temp file and tail it (Gradle is verbose).
- **Commit cadence:** one commit per screen task (or per small cluster), message `i18n(#34): extract <screen> strings (phase 2)`.
- **`detekt`/`ktlint`:** run `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` before each commit (CI-gated; baseline only fails on NEW violations — `stringResource` calls won't trip it, but a stray long line might).

---

## File structure

**Modified across the plan:**
- `app/src/main/res/values/strings.xml` — add keys (grows across every task).
- Each in-scope screen `.kt` — replace literals with `stringResource`.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt` — `secondaryTitle` return type.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt` — `secondaryTitle` call site.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt` — updated for the `@StringRes Int?` return.
- `app/src/test/.../cards/CardsScreenTest.kt`, `.../onboarding/OnboardingScreenTest.kt` — update text assertions to read resources (only these two screens have Compose UI tests).

---

# PR1 — Heavy screens (store, settings, cards, onboarding)

Branch: `i18n/34-pr1-heavy-screens` (cut from `main`).

---

### Task 1: StoreScreen

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt` (lines 84, 114, 122, 142, 152, 174, 203, 243, 253–255, 268, 277, 306, 313, 335)
- Test: none exists (heightened-rigor visual review).

- [ ] **Step 1: Add string resources**

Add to `strings.xml` (before `</resources>`), in a `<!-- Store screen -->` block:

```xml
    <!-- Store screen -->
    <string name="store_section_gem_packs">Gem Packs</string>
    <string name="store_buy">Buy</string>
    <string name="store_section_premium">Premium</string>
    <string name="store_ad_removal">Ad Removal</string>
    <string name="store_purchased">Purchased</string>
    <string name="store_season_pass">Season Pass</string>
    <string name="store_subscribe">Subscribe</string>
    <string name="store_pass_perk_gems">• Daily Gems: 0 → 10/day</string>
    <string name="store_pass_perk_lab_rush">• Lab Rush: 50–200 Gems → 1 free/day</string>
    <string name="store_pass_perk_cosmetics">• Exclusive cosmetics unlocked</string>
    <string name="store_manage_subscription">Manage subscription</string>
    <string name="store_pass_comparison">Free vs Season Pass:</string>
    <string name="store_cosmetics_note">More cosmetic visuals are still being finalized. Jade and Obsidian Ziggurat skins are available now.</string>
    <string name="store_section_cosmetics">Cosmetics</string>
    <string name="store_unequip">Unequip</string>
    <string name="store_equip">Equip</string>
    <string name="store_coming_soon">Coming Soon</string>
```

- [ ] **Step 2: Replace literals in StoreScreen.kt**

Add `import androidx.compose.ui.res.stringResource` if absent. Then replace each literal:

| Line | From | To |
|---|---|---|
| 84 | `Text("Gem Packs", …)` | `Text(stringResource(R.string.store_section_gem_packs), …)` |
| 114 | `Text("Buy")` | `Text(stringResource(R.string.store_buy))` |
| 122 | `Text("Premium", …)` | `Text(stringResource(R.string.store_section_premium), …)` |
| 142 | `Text("Ad Removal", …)` | `Text(stringResource(R.string.store_ad_removal), …)` |
| 152 | `Text("Purchased", …)` | `Text(stringResource(R.string.store_purchased), …)` |
| 174 | `Text("Buy")` | `Text(stringResource(R.string.store_buy))` |
| 203 | `Text("Season Pass", …)` | `Text(stringResource(R.string.store_season_pass), …)` |
| 243 | `Text("Subscribe")` | `Text(stringResource(R.string.store_subscribe))` |
| 248 | `Text("Free vs Season Pass:", …)` | `Text(stringResource(R.string.store_pass_comparison), …)` |
| 279 | `Text("More cosmetic visuals are still being finalized. Jade and Obsidian Ziggurat skins are available now.", …)` | `Text(stringResource(R.string.store_cosmetics_note), …)` |
| 253 | `Text("• Daily Gems: 0 → 10/day", …)` | `Text(stringResource(R.string.store_pass_perk_gems), …)` |
| 254 | `Text("• Lab Rush: 50–200 Gems → 1 free/day", …)` | `Text(stringResource(R.string.store_pass_perk_lab_rush), …)` |
| 255 | `Text("• Exclusive cosmetics unlocked", …)` | `Text(stringResource(R.string.store_pass_perk_cosmetics), …)` |
| 268 | `Text("Manage subscription")` | `Text(stringResource(R.string.store_manage_subscription))` |
| 277 | `Text("Cosmetics", …)` | `Text(stringResource(R.string.store_section_cosmetics), …)` |
| 306 | `Text("Unequip")` | `Text(stringResource(R.string.store_unequip))` |
| 313 | `Text("Equip")` | `Text(stringResource(R.string.store_equip))` |
| 335 | `Text("Coming Soon")` | `Text(stringResource(R.string.store_coming_soon))` |

Ensure `R` is imported (`com.whitefang.stepsofbabylon.R`) — most screens already import it; add if the compile complains.

- [ ] **Step 3: Build (compile + resource link)**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log`
Expected: `BUILD SUCCESSFUL`. A `Resources$NotFoundException`-style failure here means a missing/typo'd key.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt
git commit -m "i18n(#34): extract StoreScreen strings (phase 2)"
```

---

### Task 2: SettingsScreen (incl. AlertDialogs)

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt` (lines 53, 59, 74, 79, 109, 117, 158, 165, 167, 174, 184, 186)
- Test: none exists (heightened-rigor visual review — flag in PR description).

- [ ] **Step 1: Add string resources**

SettingsScreen passes most strings as `ToggleRow(title, subtitle, …)` and `SettingsCard`/row
arguments — these are NOT `Text("…")` calls, so the original grep missed them. The full set:

```xml
    <!-- Settings screen -->
    <string name="settings_sound_header">Sound</string>
    <string name="settings_music_volume">Music Volume</string>
    <string name="settings_mute_sound_header">Mute Sound Effects</string>
    <string name="settings_mute_sound_subtitle">Silence all in-game sounds</string>
    <string name="settings_mute_music_header">Mute Music</string>
    <string name="settings_mute_music_subtitle">Silence background music</string>
    <string name="settings_haptic_header">Haptic Feedback</string>
    <string name="settings_haptic_subtitle">Vibrate on taps, claims, and rewards</string>
    <string name="settings_live_steps_header">Live Step Updates</string>
    <string name="settings_live_steps_subtitle">Update notification with live step count and balance. A minimal tracking notification is always shown while step counting is active.</string>
    <string name="settings_supply_drops_header">Supply Drops</string>
    <string name="settings_supply_drops_subtitle">Notifications for walking rewards</string>
    <string name="settings_smart_reminders_header">Smart Reminders</string>
    <string name="settings_smart_reminders_subtitle">Upgrade proximity reminders</string>
    <string name="settings_milestone_alerts_header">Milestone Alerts</string>
    <string name="settings_milestone_alerts_subtitle">Wave records and step milestones</string>
    <string name="settings_help_header">Help</string>
    <string name="settings_replay_tutorial">Replay tutorial</string>
    <string name="settings_replay_tutorial_subtitle">See the first-launch walkthrough again</string>
    <string name="settings_background_activity_header">Background activity</string>
    <string name="settings_background_activity_subtitle">Allow step counting to keep running in the background</string>
    <string name="settings_data_header">Data</string>
    <string name="settings_privacy_policy">Privacy Policy</string>
    <string name="settings_privacy_policy_subtitle">How your data is handled</string>
    <string name="settings_delete_card_header">Delete All Data</string>
    <string name="settings_delete_card_subtitle">Permanently erase all progress</string>
    <string name="settings_delete_confirm_title">Delete All Data?</string>
    <string name="settings_delete_confirm_body">This will permanently delete all your progress, steps history, upgrades, and currency. This cannot be undone.</string>
    <string name="settings_delete_continue">Continue</string>
    <string name="settings_delete_final_title">Are you absolutely sure?</string>
    <string name="settings_delete_final_body">All steps, upgrades, cards, weapons, and purchases will be lost forever. The app will restart.</string>
    <string name="settings_delete_confirm_final">Delete Everything</string>
    <string name="action_cancel">Cancel</string>
```

(Note: `action_cancel` is a candidate shared key — grep `strings.xml`; if one already exists, reuse it and drop this line. Line numbers below are from the pre-edit file and WILL drift as you insert `stringResource` imports — match the literal text, and run the methodology grep to confirm nothing is left.)

- [ ] **Step 2: Replace literals**

Add `stringResource` import if absent. The `ToggleRow(...)`/card-row literals are positional/named
String arguments — wrap each in `stringResource(...)`. Replace ALL of (line numbers approximate):

| ~Line | From | To |
|---|---|---|
| 53 | `Text("Sound", …)` | `Text(stringResource(R.string.settings_sound_header), …)` |
| 59 | `Text("Music Volume", …)` | `Text(stringResource(R.string.settings_music_volume), …)` |
| 39–40 | `ToggleRow("Live Step Updates", "Update notification…", …)` | `ToggleRow(stringResource(R.string.settings_live_steps_header), stringResource(R.string.settings_live_steps_subtitle), …)` |
| 44 | `ToggleRow("Supply Drops", "Notifications for walking rewards", …)` | `ToggleRow(stringResource(R.string.settings_supply_drops_header), stringResource(R.string.settings_supply_drops_subtitle), …)` |
| 45 | `ToggleRow("Smart Reminders", "Upgrade proximity reminders", …)` | `ToggleRow(stringResource(R.string.settings_smart_reminders_header), stringResource(R.string.settings_smart_reminders_subtitle), …)` |
| 47–48 | `ToggleRow("Milestone Alerts", "Wave records and step milestones", …)` | `ToggleRow(stringResource(R.string.settings_milestone_alerts_header), stringResource(R.string.settings_milestone_alerts_subtitle), …)` |
| 55 | `ToggleRow("Mute Sound Effects", "Silence all in-game sounds", …)` | `ToggleRow(stringResource(R.string.settings_mute_sound_header), stringResource(R.string.settings_mute_sound_subtitle), …)` |
| 56 | `ToggleRow("Mute Music", "Silence background music", …)` | `ToggleRow(stringResource(R.string.settings_mute_music_header), stringResource(R.string.settings_mute_music_subtitle), …)` |
| 68–69 | `ToggleRow("Haptic Feedback", "Vibrate on taps, claims, and rewards", …)` | `ToggleRow(stringResource(R.string.settings_haptic_header), stringResource(R.string.settings_haptic_subtitle), …)` |
| 74 | `Text("Help", …)` | `Text(stringResource(R.string.settings_help_header), …)` |
| 79 | `Text("Replay tutorial", …)` | `Text(stringResource(R.string.settings_replay_tutorial), …)` |
| 81 | `"See the first-launch walkthrough again"` (subtitle arg) | `stringResource(R.string.settings_replay_tutorial_subtitle)` |
| 96 | `"Background activity"` (row title arg) | `stringResource(R.string.settings_background_activity_header)` |
| 101 | `"Allow step counting to keep running in the background"` (subtitle arg) | `stringResource(R.string.settings_background_activity_subtitle)` |
| 109 | `Text("Data", …)` | `Text(stringResource(R.string.settings_data_header), …)` |
| 117 | `Text("Privacy Policy", …)` | `Text(stringResource(R.string.settings_privacy_policy), …)` |
| 119 | `"How your data is handled"` (subtitle arg) | `stringResource(R.string.settings_privacy_policy_subtitle)` |
| 140 | `"Delete All Data"` (card title arg) | `stringResource(R.string.settings_delete_card_header)` |
| 146 | `"Permanently erase all progress"` (card subtitle arg) | `stringResource(R.string.settings_delete_card_subtitle)` |
| 158 | `title = { Text("Delete All Data?") }` | `title = { Text(stringResource(R.string.settings_delete_confirm_title)) }` |
| 161 | `text = { Text("This will permanently delete…") }` body | `Text(stringResource(R.string.settings_delete_confirm_body))` |
| 165 | `Text("Continue", color = …)` | `Text(stringResource(R.string.settings_delete_continue), color = …)` |
| 167 | `Text("Cancel")` | `Text(stringResource(R.string.action_cancel))` |
| 174 | `title = { Text("Are you absolutely sure?") }` | `title = { Text(stringResource(R.string.settings_delete_final_title)) }` |
| 177 | `text = { Text("All steps, upgrades, cards…") }` body | `Text(stringResource(R.string.settings_delete_final_body))` |
| 184 | `Text("Delete Everything", color = …)` | `Text(stringResource(R.string.settings_delete_confirm_final), color = …)` |
| 186 | `Text("Cancel")` | `Text(stringResource(R.string.action_cancel))` |

After editing, run the methodology grep on `SettingsScreen.kt` and confirm zero English literals remain
(other than data-only / glyphs).

- [ ] **Step 3: Build**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt
git commit -m "i18n(#34): extract SettingsScreen strings incl. delete dialogs (phase 2)"
```

---

### Task 3: CardsScreen (incl. pack dialog + contentDescription) + update CardsScreenTest

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt` (lines 89, 101, 110, 114, 168, 177, 184, 210, 246, 248, 266, 271, 283)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreenTest.kt` (assertions on "2 cards" etc.)

- [ ] **Step 1: Add string resources**

```xml
    <!-- Cards screen -->
    <string name="cards_owned_count">%1$d cards</string>
    <string name="cards_equipped_count">Equipped: %1$d/3</string>
    <string name="cards_equipped_full">Equipped: 3/3 — unequip one to swap</string>
    <string name="cards_empty_title">No cards yet</string>
    <string name="cards_empty_message">Open a pack above to start your collection. Equip up to 3 cards for per-round bonuses in battle.</string>
    <string name="cards_free_pack_ad">Free Pack (Ad)</string>
    <string name="cards_free_pack_used">Free pack used today</string>
    <string name="cards_pack_opened_title">Pack Opened!</string>
    <string name="cards_pack_new">New</string>
    <string name="cards_pack_duplicate">Duplicate</string>
    <string name="action_ok">OK</string>
    <string name="cards_level_progress">Lv %1$d/%2$d</string>
    <string name="cards_unequip">Unequip</string>
    <string name="cards_equip">Equip</string>
    <string name="cards_upgrade_progress">Upgrade (%1$d/%2$d)</string>
```

(Note: `upgrade_max` ("MAX") already exists in `strings.xml` from phase 1 — reuse it at line 246, do NOT add a new key.)

- [ ] **Step 2: Replace literals**

Add `stringResource` import if absent.

| Line | From | To |
|---|---|---|
| 89 | `Text("${state.ownedCards.size} cards", …)` | `Text(stringResource(R.string.cards_owned_count, state.ownedCards.size), …)` |
| 95 | `Text("Equipped: 3/3 — unequip one to swap", …)` (in the `equippedCount >= 3` branch) | `Text(stringResource(R.string.cards_equipped_full), …)` |
| 101 | `Text("Equipped: ${state.equippedCount}/3", …)` | `Text(stringResource(R.string.cards_equipped_count, state.equippedCount), …)` |
| 146–147 | `EmptyState(title = "No cards yet", message = "Open a pack above…")` | `EmptyState(title = stringResource(R.string.cards_empty_title), message = stringResource(R.string.cards_empty_message))` |
| 110 | `Text("Free Pack (Ad)")` | `Text(stringResource(R.string.cards_free_pack_ad))` |
| 114 | `Text("Free pack used today", …)` | `Text(stringResource(R.string.cards_free_pack_used), …)` |
| 168 | `title = { Text("Pack Opened!") }` | `title = { Text(stringResource(R.string.cards_pack_opened_title)) }` |
| 177 | `contentDescription = "New",` | `contentDescription = stringResource(R.string.cards_pack_new),` |
| 184 | `contentDescription = "Duplicate",` | `contentDescription = stringResource(R.string.cards_pack_duplicate),` |
| 210 | `Text("OK")` | `Text(stringResource(R.string.action_ok))` |
| 246 | `Text("MAX", …)` | `Text(stringResource(R.string.upgrade_max), …)` |
| 248 | `Text("Lv ${card.level}/${card.type.maxLevel}", …)` | `Text(stringResource(R.string.cards_level_progress, card.level, card.type.maxLevel), …)` |
| 266 | `Text("Unequip")` | `Text(stringResource(R.string.cards_unequip))` |
| 271 | `Text("Equip")` | `Text(stringResource(R.string.cards_equip))` |
| 283 | `Text("Upgrade (${card.copyCount}/${card.copiesNeeded})")` | `Text(stringResource(R.string.cards_upgrade_progress, card.copyCount, card.copiesNeeded))` |

- [ ] **Step 3: Update CardsScreenTest assertions to read the resource**

In `CardsScreenTest.kt`, the existing test asserts `onNodeWithText("2 cards")`. After migration the screen renders the same text, but pin the assertion to the resource. Add at the top of the test class a context handle and resolve the string:

In the test method `renders gem balance and owned-card count`, replace:

```kotlin
        composeRule.onNodeWithText("2 cards").assertExists()
```

with:

```kotlin
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(ctx.getString(R.string.cards_owned_count, 2)).assertExists()
```

Add imports as needed: `androidx.test.core.app.ApplicationProvider`, `com.whitefang.stepsofbabylon.R`. Leave the `"250"` (gem balance) assertion unchanged — it is a pure number, not a migrated string. Apply the same `ctx.getString` pattern to any OTHER assertion in this file that targets a now-migrated literal (search the file for `onNodeWithText("` and reconcile each against the migrated keys; numbers/data stay as-is).

- [ ] **Step 4: Run the Cards UI test**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*CardsScreenTest" > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log`
Expected: `BUILD SUCCESSFUL`, CardsScreenTest green.

- [ ] **Step 5: Build full + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreenTest.kt
git commit -m "i18n(#34): extract CardsScreen strings + dialog + cd; pin CardsScreenTest to resources (phase 2)"
```

---

### Task 4: OnboardingScreen + update OnboardingScreenTest

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt` (lines 139, 240, 267, 311, 318, 325, 332, 346, 353)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreenTest.kt`

- [ ] **Step 1: Add string resources**

```xml
    <!-- Onboarding screen (buttons + final-slide body copy; slide-carousel content lives in OnboardingSlide via existing keys) -->
    <string name="onboarding_skip">Skip</string>
    <string name="onboarding_next">Next</string>
    <string name="onboarding_no_sensor_body">This device has no step-counter sensor. You can still earn Steps by connecting Health Connect in Settings — otherwise the game won\'t track your walking.</string>
    <string name="onboarding_continue_anyway">Continue anyway</string>
    <string name="onboarding_enabled_body">Step counting enabled</string>
    <string name="onboarding_battery_primer_body">Some phones pause background apps to save battery, which stops step counting. Allow Steps of Babylon to run in the background so your walking always counts.</string>
    <string name="onboarding_allow_background">Allow background activity</string>
    <string name="onboarding_maybe_later">Maybe later</string>
    <string name="onboarding_start_playing">Start playing</string>
    <string name="onboarding_enable_step_counting">Enable step counting</string>
    <string name="onboarding_denied_body">Step counting is off. You can enable it any time in Settings.</string>
    <string name="onboarding_open_settings">Open Settings</string>
    <string name="onboarding_continue_without">Continue without step counting</string>
```

**Caution:** the three body strings (lines ~255, ~296, ~339) are built with Kotlin string
concatenation (`"…" + "…"`) across multiple source lines — collapse each into ONE resource value
exactly reproducing the concatenated text (verify by reading the source; mind the spaces at the
concatenation seams). The `won't` apostrophe must be escaped `won\'t` in XML.

- [ ] **Step 2: Replace literals**

Add `stringResource` import if absent.

| Line | From | To |
|---|---|---|
| 139 | `Text("Skip")` | `Text(stringResource(R.string.onboarding_skip))` |
| 240 | `Text("Next")` | `Text(stringResource(R.string.onboarding_next))` |
| 255–257 | `Text("This device has no step-counter sensor…" + …)` (concatenated body) | `Text(stringResource(R.string.onboarding_no_sensor_body))` |
| 267 | `Text("Continue anyway")` | `Text(stringResource(R.string.onboarding_continue_anyway))` |
| 285 | `Text("Step counting enabled", …)` | `Text(stringResource(R.string.onboarding_enabled_body), …)` |
| 296–298 | `Text("Some phones pause background apps…" + …)` (concatenated body) | `Text(stringResource(R.string.onboarding_battery_primer_body))` |
| 311 | `Text("Allow background activity")` | `Text(stringResource(R.string.onboarding_allow_background))` |
| 318 | `Text("Maybe later")` | `Text(stringResource(R.string.onboarding_maybe_later))` |
| 325 | `Text("Start playing")` | `Text(stringResource(R.string.onboarding_start_playing))` |
| 332 | `Text("Enable step counting")` | `Text(stringResource(R.string.onboarding_enable_step_counting))` |
| 339 | `Text("Step counting is off. You can enable it any time in Settings.", …)` | `Text(stringResource(R.string.onboarding_denied_body), …)` |
| 346 | `Text("Open Settings")` | `Text(stringResource(R.string.onboarding_open_settings))` |
| 353 | `Text("Continue without step counting")` | `Text(stringResource(R.string.onboarding_continue_without))` |

Line 153 is a code comment mentioning `contentDescription` — LEAVE IT ALONE (not a literal).

- [ ] **Step 3: Update OnboardingScreenTest**

Search `OnboardingScreenTest.kt` for `onNodeWithText("` assertions. For any that target a now-migrated button literal (e.g. `"Next"`, `"Skip"`, `"Enable step counting"`), replace the literal with `ctx.getString(R.string.onboarding_…)` using the same `ApplicationProvider` handle as Task 3 Step 3. Assertions targeting slide *content* (titles/body from `OnboardingSlide`, already-migrated phase-1 keys) or test-only strings stay as they are.

- [ ] **Step 4: Run the Onboarding UI test**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*OnboardingScreenTest" > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log`
Expected: `BUILD SUCCESSFUL`, green.

- [ ] **Step 5: Build full + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreenTest.kt
git commit -m "i18n(#34): extract OnboardingScreen button strings; pin OnboardingScreenTest (phase 2)"
```

---

### Task 5: PR1 full verification + open PR

- [ ] **Step 1: Run full JVM suite + lint**

Run:
```bash
./run-gradle.sh :app:testDebugUnitTest > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
./run-gradle.sh :app:detekt > /tmp/d.log 2>&1 && tail -n 10 /tmp/d.log
./lint-kotlin.sh
```
Expected: all `BUILD SUCCESSFUL`, JVM test count = 1282 (unchanged; no tests added/removed, assertions updated in place), 0 failures.

- [ ] **Step 2: Open PR1**

```bash
git push -u origin i18n/34-pr1-heavy-screens
gh pr create --title "i18n(#34): extract Compose strings — PR1 heavy screens (store/settings/cards/onboarding)" \
  --body "Phase 2 of ADR-0014. Mechanical string extraction for the four heaviest screens incl. Settings delete dialogs and the Cards pack dialog. CardsScreenTest/OnboardingScreenTest assertions pinned to resources. No behavior change; 1282 JVM unchanged. SettingsScreen has no Compose UI test — visual-diff reviewed (heightened rigor). Refs #34."
```

---

# PR2 — Remaining screens + shared/nav surfaces

Branch: `i18n/34-pr2-remaining` (cut from `main` AFTER PR1 merges, so `strings.xml` additions don't conflict).

---

### Task 6: HomeScreen + TierSelector

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeScreen.kt` (lines 121, 170, 195, 213)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/TierSelector.kt` — **no extraction** (lines 61–62 are data-only: `Text("$t")`, `Text("${tier.cashMultiplier}x")`). Verify and leave alone.

LEAVE ALONE in HomeScreen: line 166 `Text("${state.unclaimedDropCount}")` and line 234 `Text("$badgeCount")` (data-only badges). `"Today"` (line 121) IS English text — extract.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Home screen -->
    <string name="home_today">Today</string>
    <string name="home_currency_steps">Steps</string>
    <string name="home_currency_gems">Gems</string>
    <string name="home_currency_power_stones">Power Stones</string>
    <string name="home_empty_title">Earn your first Steps</string>
    <string name="home_empty_message">Go for a walk — every step powers up your ziggurat. Your Steps will appear here.</string>
    <string name="home_best_wave">Best Wave: %1$d</string>
    <string name="home_unclaimed_supplies_cd">Unclaimed supplies, %1$d available</string>
    <string name="home_unclaimed_supplies">Unclaimed Supplies</string>
    <string name="home_season_pass_active">Season Pass Active</string>
    <string name="home_battle">BATTLE</string>
    <string name="home_menu_missions">Missions</string>
    <string name="home_menu_settings">Settings</string>
    <string name="home_menu_help">Help</string>
    <string name="home_menu_store">Store</string>
```

- [ ] **Step 2: Replace literals in HomeScreen.kt**

Add `stringResource` import if absent. `CurrencyItem(label, …)` / `MenuButton(label = …, …)` /
`EmptyState(title = …, message = …)` pass strings as arguments — wrap each in `stringResource`.

| ~Line | From | To |
|---|---|---|
| 121 | `Text("Today", …)` | `Text(stringResource(R.string.home_today), …)` |
| 136 | `CurrencyItem("Steps", state.stepBalance)` | `CurrencyItem(stringResource(R.string.home_currency_steps), state.stepBalance)` |
| 137 | `CurrencyItem("Gems", state.gems)` | `CurrencyItem(stringResource(R.string.home_currency_gems), state.gems)` |
| 138 | `CurrencyItem("Power Stones", state.powerStones)` | `CurrencyItem(stringResource(R.string.home_currency_power_stones), state.powerStones)` |
| 146–147 | `EmptyState(title = "Earn your first Steps", message = "Go for a walk…")` | `EmptyState(title = stringResource(R.string.home_empty_title), message = stringResource(R.string.home_empty_message))` |
| 152 | `Text("Best Wave: ${state.bestWave}", …)` | `Text(stringResource(R.string.home_best_wave, state.bestWave), …)` |
| 163 | `contentDescription = "Unclaimed supplies, ${state.unclaimedDropCount} available"` | `contentDescription = stringResource(R.string.home_unclaimed_supplies_cd, state.unclaimedDropCount)` |
| 170 | `Text("Unclaimed Supplies", …)` | `Text(stringResource(R.string.home_unclaimed_supplies), …)` |
| 176 | `MenuButton(… label = "Missions", …)` | `MenuButton(… label = stringResource(R.string.home_menu_missions), …)` |
| 180 | `MenuButton(… label = "Settings", …)` | `MenuButton(… label = stringResource(R.string.home_menu_settings), …)` |
| 181 | `MenuButton(… label = "Help", …)` | `MenuButton(… label = stringResource(R.string.home_menu_help), …)` |
| 182 | `MenuButton(… label = "Store", …)` | `MenuButton(… label = stringResource(R.string.home_menu_store), …)` |
| 195 | `Text("Season Pass Active", …)` | `Text(stringResource(R.string.home_season_pass_active), …)` |
| 213 | `Text("BATTLE", …)` | `Text(stringResource(R.string.home_battle), …)` |

(`home_currency_*` are distinct from `stats_*`/`economy_balance_*` keys deliberately — same English
word, different screen context, so a translation can diverge.)

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeScreen.kt
git commit -m "i18n(#34): extract HomeScreen strings (phase 2)"
```

---

### Task 7: CurrencyDashboardScreen (economy)

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt` (lines 71, 115, 148, 203, 214, 217, 251, 261)
- Test: none (heightened-rigor visual review).

LEAVE ALONE: none data-only here, but line 115 (`"${formatCount(...)} / 100,000 steps"`) and 251 (`"$steps steps → $ps PS"`) are templates → format-arg.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Premium Currencies (economy) screen -->
    <string name="economy_store">Store</string>
    <string name="economy_balance_gems">Gems</string>
    <string name="economy_balance_power_stones">Power Stones</string>
    <string name="economy_weekly_challenge">Weekly Step Challenge</string>
    <string name="economy_weekly_progress">%1$s / 100,000 steps</string>
    <string name="economy_past_weeks">Past Weeks</string>
    <string name="economy_login_streak">Login Streak</string>
    <string name="economy_gems_claimed_today">Today\'s Gems claimed</string>
    <string name="economy_open_daily">Open the app daily for Gems!</string>
    <string name="economy_daily_power_stone">Daily Power Stone</string>
    <string name="economy_earned_today">Earned today (walked 1,000+ steps)</string>
    <string name="economy_walk_for_ps">Walk 1,000 steps today for 1 Power Stone</string>
    <string name="economy_steps_to_ps">%1$s steps → %2$d PS</string>
    <string name="economy_ready">Ready!</string>
    <string name="economy_claimed">Claimed</string>
```

**CRITICAL — format-arg type (from review):** in `ThresholdRow(steps: String, ps: Int, …)` the
second arg `ps` is an **Int**, so the resource MUST be `%2$d` (NOT `%2$s` — a String specifier with an
Int arg). The call site `stringResource(R.string.economy_steps_to_ps, steps, ps)` is correct as-is.
The em-dash fallback `if (reached) "Ready!" else "—"` — extract `"Ready!"` only; `"—"` is a glyph,
leave it (or inline as a literal — it carries no translatable text).

- [ ] **Step 2: Replace literals**

Add `stringResource` import if absent. Note `BalanceCard("Gems", …)` / `BalanceCard("Power Stones", …)`
pass the label as a String argument — wrap in `stringResource`.

| ~Line | From | To |
|---|---|---|
| 71 | `Text("Store")` | `Text(stringResource(R.string.economy_store))` |
| 77 | `BalanceCard("Gems", …)` | `BalanceCard(stringResource(R.string.economy_balance_gems), …)` |
| 78 | `BalanceCard("Power Stones", …)` | `BalanceCard(stringResource(R.string.economy_balance_power_stones), …)` |
| 93 | `Text("Weekly Step Challenge", …)` | `Text(stringResource(R.string.economy_weekly_challenge), …)` |
| 115 | `Text("${formatCount(state.weeklySteps)} / 100,000 steps", …)` | `Text(stringResource(R.string.economy_weekly_progress, formatCount(state.weeklySteps)), …)` |
| 129 | `Text("Past Weeks", …)` | `Text(stringResource(R.string.economy_past_weeks), …)` |
| 148 | `Text("Login Streak", …)` | `Text(stringResource(R.string.economy_login_streak), …)` |
| 182 | `Text("Today's Gems claimed", …)` | `Text(stringResource(R.string.economy_gems_claimed_today), …)` |
| 189 | `Text("Open the app daily for Gems!", …)` | `Text(stringResource(R.string.economy_open_daily), …)` |
| 203 | `Text("Daily Power Stone", …)` | `Text(stringResource(R.string.economy_daily_power_stone), …)` |
| 214 | `Text("Earned today (walked 1,000+ steps)", …)` | `Text(stringResource(R.string.economy_earned_today), …)` |
| 217 | `Text("Walk 1,000 steps today for 1 Power Stone", …)` | `Text(stringResource(R.string.economy_walk_for_ps), …)` |
| 251 | `Text("$steps steps → $ps PS", …)` | `Text(stringResource(R.string.economy_steps_to_ps, steps, ps), …)` |
| 265 | `if (reached) "Ready!" else "—"` | `if (reached) stringResource(R.string.economy_ready) else "—"` |
| 261 | `Text("Claimed", …)` | `Text(stringResource(R.string.economy_claimed), …)` |

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt
git commit -m "i18n(#34): extract CurrencyDashboardScreen strings (phase 2)"
```

---

### Task 8: StatsScreen

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsScreen.kt` (lines 52, 67, 90, 106, 113, 118)
- Test: none (heightened-rigor visual review).

- [ ] **Step 1: Add string resources**

```xml
    <!-- Stats screen -->
    <string name="stats_walking_history">Walking History</string>
    <string name="stats_today">Today</string>
    <string name="stats_battle_stats">Battle Stats</string>
    <string name="stats_all_time">All-Time Stats</string>
    <string name="stats_gems">Gems</string>
    <string name="stats_power_stones">Power Stones</string>
    <!-- StatRow labels (passed as String args to StatRow(label, value)) -->
    <string name="stats_row_steps">Steps</string>
    <string name="stats_row_activity_equiv">Activity Step-Equivalents</string>
    <string name="stats_row_tier_best_wave">Tier %1$d Best Wave</string>
    <string name="stats_row_rounds_played">Rounds Played</string>
    <string name="stats_row_enemies_killed">Enemies Killed</string>
    <string name="stats_row_total_cash">Total Cash Earned</string>
    <string name="stats_row_lifetime_steps">Lifetime Steps</string>
    <string name="stats_row_days_active">Days Active</string>
    <string name="stats_row_avg_daily_steps">Avg Daily Steps</string>
    <string name="stats_row_workshop_levels">Workshop Levels</string>
    <string name="stats_row_current">Current</string>
    <string name="stats_row_earned">Earned</string>
    <string name="stats_row_spent">Spent</string>
```

(Note: `"Today"` here is a separate context from `home_today`; keep a distinct `stats_today` key. The
`StatRow(label, value)` calls pass the label as a String arg — wrap in `stringResource`. `"Current"`/
`"Earned"`/`"Spent"` appear in BOTH the Gems and Power Stones blocks — reuse the one key each. The
SECOND arg to `StatRow` is a value (`formatCount(...)` or `"$x"`) — LEAVE those alone, they're data.)

- [ ] **Step 2: Replace literals**

| ~Line | From | To |
|---|---|---|
| 52 | `Text("Walking History", …)` | `Text(stringResource(R.string.stats_walking_history), …)` |
| 67 | `Text("Today", …)` | `Text(stringResource(R.string.stats_today), …)` |
| 69 | `StatRow("Steps", …)` | `StatRow(stringResource(R.string.stats_row_steps), …)` |
| 71 | `StatRow("Activity Step-Equivalents", …)` | `StatRow(stringResource(R.string.stats_row_activity_equiv), …)` |
| 90 | `Text("Battle Stats", …)` | `Text(stringResource(R.string.stats_battle_stats), …)` |
| 93 | `StatRow("Tier $tier Best Wave", "$wave")` | `StatRow(stringResource(R.string.stats_row_tier_best_wave, tier), "$wave")` |
| 95 | `StatRow("Rounds Played", …)` | `StatRow(stringResource(R.string.stats_row_rounds_played), …)` |
| 96 | `StatRow("Enemies Killed", …)` | `StatRow(stringResource(R.string.stats_row_enemies_killed), …)` |
| 97 | `StatRow("Total Cash Earned", …)` | `StatRow(stringResource(R.string.stats_row_total_cash), …)` |
| 106 | `Text("All-Time Stats", …)` | `Text(stringResource(R.string.stats_all_time), …)` |
| 108 | `StatRow("Lifetime Steps", …)` | `StatRow(stringResource(R.string.stats_row_lifetime_steps), …)` |
| 109 | `StatRow("Days Active", …)` | `StatRow(stringResource(R.string.stats_row_days_active), …)` |
| 110 | `StatRow("Avg Daily Steps", …)` | `StatRow(stringResource(R.string.stats_row_avg_daily_steps), …)` |
| 111 | `StatRow("Workshop Levels", …)` | `StatRow(stringResource(R.string.stats_row_workshop_levels), …)` |
| 113 | `Text("Gems", …)` | `Text(stringResource(R.string.stats_gems), …)` |
| 114 | `StatRow("Current", …)` | `StatRow(stringResource(R.string.stats_row_current), …)` |
| 115 | `StatRow("Earned", …)` | `StatRow(stringResource(R.string.stats_row_earned), …)` |
| 116 | `StatRow("Spent", …)` | `StatRow(stringResource(R.string.stats_row_spent), …)` |
| 118 | `Text("Power Stones", …)` | `Text(stringResource(R.string.stats_power_stones), …)` |
| 119 | `StatRow("Current", …)` | `StatRow(stringResource(R.string.stats_row_current), …)` |
| 120 | `StatRow("Earned", …)` | `StatRow(stringResource(R.string.stats_row_earned), …)` |
| 121 | `StatRow("Spent", …)` | `StatRow(stringResource(R.string.stats_row_spent), …)` |

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsScreen.kt
git commit -m "i18n(#34): extract StatsScreen strings (phase 2)"
```

---

### Task 9: LabsScreen

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt` (lines 83, 97, 188, 200, 238)
- Test: none (VM `while(true)` ticker — visual review).

LEAVE ALONE: line 155 `Text("Lv ${info.level}/${info.type.maxLevel}", …)` — wait, this IS English ("Lv"). Extract it too (added below). Lines 97/200/238 have trailing spaces — preserve via quoted values.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Labs screen -->
    <string name="labs_slots">Lab Slots: %1$d/%2$d</string>
    <string name="labs_unlock_slot">"Unlock Slot "</string>
    <string name="labs_level_progress">Lv %1$d/%2$d</string>
    <string name="labs_free">Free</string>
    <string name="labs_rush">"Rush "</string>
    <string name="labs_start">"Start "</string>
    <string name="labs_no_slot">No Slot Available</string>
```

(`formatTime()`'s `"Done!"` at LabsScreen.kt:251 is a non-composable helper return → handled in Task 14.)

- [ ] **Step 2: Replace literals**

| Line | From | To |
|---|---|---|
| 83 | `Text("Lab Slots: ${state.activeSlots}/${state.totalSlots}", …)` | `Text(stringResource(R.string.labs_slots, state.activeSlots, state.totalSlots), …)` |
| 97 | `Text("Unlock Slot ")` | `Text(stringResource(R.string.labs_unlock_slot))` |
| 155 | `Text("Lv ${info.level}/${info.type.maxLevel}", …)` | `Text(stringResource(R.string.labs_level_progress, info.level, info.type.maxLevel), …)` |
| 188 | `Text("Free")` | `Text(stringResource(R.string.labs_free))` |
| 200 | `Text("Rush ")` | `Text(stringResource(R.string.labs_rush))` |
| 209 | `"No Slot Available"` (passed as a String arg) | `stringResource(R.string.labs_no_slot)` |
| 238 | `Text("Start ")` | `Text(stringResource(R.string.labs_start))` |

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt
git commit -m "i18n(#34): extract LabsScreen strings (phase 2)"
```

---

### Task 10: MissionsScreen

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt` (lines 86, 102, 127, 162, 176, 209, 253)
- Test: none (VM ticker — visual review).

LEAVE ALONE: line 162 `Text("+", …)` — this is a glyph prefix with no translatable text; but it IS a literal. Extract as `missions_plus_prefix` to be safe and consistent (cheap). Both line-127 and line-209 `contentDescription = "Claimed"` reuse one key.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Missions screen -->
    <string name="missions_daily">Daily Missions</string>
    <string name="missions_resets_in">Resets in %1$dh %2$dm</string>
    <string name="missions_milestones">Walking Milestones</string>
    <string name="missions_cd_claimed">Claimed</string>
    <string name="missions_reward_plus">+</string>
    <string name="missions_claim">Claim</string>
```

- [ ] **Step 2: Replace literals**

| Line | From | To |
|---|---|---|
| 86 | `Text("Daily Missions", …)` | `Text(stringResource(R.string.missions_daily), …)` |
| 88 | `Text("Resets in ${hours}h ${minutes}m", …)` | `Text(stringResource(R.string.missions_resets_in, hours, minutes), …)` |
| 102 | `Text("Walking Milestones", …)` | `Text(stringResource(R.string.missions_milestones), …)` |
| 127 | `contentDescription = "Claimed",` | `contentDescription = stringResource(R.string.missions_cd_claimed),` |
| 162 | `Text("+", …)` | `Text(stringResource(R.string.missions_reward_plus), …)` |
| 176 | `Text("Claim")` | `Text(stringResource(R.string.missions_claim))` |
| 209 | `contentDescription = "Claimed",` | `contentDescription = stringResource(R.string.missions_cd_claimed),` |
| 253 | `Text("Claim")` | `Text(stringResource(R.string.missions_claim))` |

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt
git commit -m "i18n(#34): extract MissionsScreen strings (phase 2)"
```

---

### Task 11: UltimateWeaponScreen (weapons) + UnclaimedSuppliesScreen

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt` (lines 157, 233)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt` (lines 70, 126)
- Test: none.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Ultimate Weapons screen -->
    <string name="uw_power_stones_balance">Power Stones: %1$d</string>
    <string name="uw_equipped_full">Equipped: 3/3 — unequip one to swap</string>
    <string name="uw_equipped_count">Equipped: %1$d/3</string>
    <string name="uw_unlock_cost">Unlock (%1$d PS)</string>
    <string name="uw_unequip">Unequip</string>
    <string name="uw_equip">Equip</string>
    <string name="uw_path_level_cost">L%1$d (%2$d PS)</string>

    <!-- Unclaimed Supplies screen -->
    <string name="supplies_claim_all">Claim All</string>
    <string name="supplies_empty">No supply drops yet — keep walking!</string>
    <string name="supplies_claim">Claim</string>
```

(`pathLabel()` at UltimateWeaponScreen.kt:243 is a non-composable helper returning English path names
→ handled in Task 14. `state.powerStones`/`unlockCost`/`level`/`cost` are Ints → `%1$d`.)

- [ ] **Step 2: Replace literals**

UltimateWeaponScreen.kt:

| ~Line | From | To |
|---|---|---|
| 58 | `Text("Power Stones: ${state.powerStones}", …)` | `Text(stringResource(R.string.uw_power_stones_balance, state.powerStones), …)` |
| 65 | `Text("Equipped: 3/3 — unequip one to swap", …)` | `Text(stringResource(R.string.uw_equipped_full), …)` |
| 73 | `Text("Equipped: ${state.equippedCount}/3", …)` | `Text(stringResource(R.string.uw_equipped_count, state.equippedCount), …)` |
| 157 | `Text("Unlock (${info.type.unlockCost} PS)")` | `Text(stringResource(R.string.uw_unlock_cost, info.type.unlockCost))` |
| 182 | `Text(if (info.isEquipped) "Unequip" else "Equip")` | `Text(if (info.isEquipped) stringResource(R.string.uw_unequip) else stringResource(R.string.uw_equip))` |
| 233 | `Text("L${pathInfo.level + 1} (${pathInfo.cost} PS)")` | `Text(stringResource(R.string.uw_path_level_cost, pathInfo.level + 1, pathInfo.cost))` |

UnclaimedSuppliesScreen.kt:

| ~Line | From | To |
|---|---|---|
| 70 | `Text("Claim All")` | `Text(stringResource(R.string.supplies_claim_all))` |
| 79 | `"No supply drops yet — keep walking!"` (passed as a String arg) | `stringResource(R.string.supplies_empty)` |
| 126 | `Text("Claim")` | `Text(stringResource(R.string.supplies_claim))` |

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt
git commit -m "i18n(#34): extract UltimateWeaponScreen + UnclaimedSuppliesScreen strings (phase 2)"
```

---

### Task 12: Shared presentation/ui + battle/ui Compose components

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ErrorState.kt` (line 49)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/SobTopAppBar.kt` (line 38)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/UltimateWeaponBar.kt` (lines 50–55 — contentDescription branch)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/InRoundUpgradeMenu.kt` (line 109)

LEAVE ALONE: UltimateWeaponBar lines 60 (`Text(slot.typeName.take(2), …)`) and 62 (`Text("${slot.cooldownRemaining.toInt()}", …)`) — data-only.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Shared UI components -->
    <string name="action_retry">Retry</string>
    <string name="cd_back">Back</string>
    <string name="equipped_chip">✓ EQUIPPED</string>
    <string name="battle_uw_ready">%1$s ready</string>
    <string name="battle_uw_cooldown">%1$s on cooldown, %2$d seconds remaining</string>
    <string name="battle_close">✕</string>
```

Add to this task: `presentation/ui/Rarity.kt` line 114 `Text(text = "✓ EQUIPPED", …)` →
`Text(text = stringResource(R.string.equipped_chip), …)` (add the `stringResource` import; confirm the
enclosing `EquippedChip` is `@Composable` — it is). `CurrencyDisplay.kt`'s `CurrencyType.label()`
("Steps"/"Cash"/"Gems"/"Power Stones") is a non-composable helper return → handled in **Task 14**.

- [ ] **Step 2: Replace literals**

ErrorState.kt: add `stringResource` import; line 49 `Text("Retry")` → `Text(stringResource(R.string.action_retry))`.

SobTopAppBar.kt: add `stringResource` import; line 38 `contentDescription = "Back",` → `contentDescription = stringResource(R.string.cd_back),`.

UltimateWeaponBar.kt: add `stringResource` import. Replace the contentDescription branch (lines 50–55):

```kotlin
                        .semantics {
                            contentDescription =
                                if (slot.isReady) {
                                    "${slot.typeName} ready"
                                } else {
                                    "${slot.typeName} on cooldown, ${slot.cooldownRemaining.toInt()} seconds remaining"
                                }
                        },
```

`stringResource` is a `@Composable` and CANNOT be called inside the non-composable `semantics {}` lambda. Resolve the two strings ABOVE the `Box`, in the composable body, then reference them:

```kotlin
        val readyDesc = stringResource(R.string.battle_uw_ready, slot.typeName)
        val cooldownDesc = stringResource(
            R.string.battle_uw_cooldown, slot.typeName, slot.cooldownRemaining.toInt(),
        )
        // … then inside .semantics { }:
                        .semantics {
                            contentDescription = if (slot.isReady) readyDesc else cooldownDesc
                        },
```

**Placement (review correction):** the `Box` lives INSIDE a `slots.forEach { slot -> … }` loop, and
`readyDesc`/`cooldownDesc` reference the per-iteration `slot`. Place the two `val`s **inside the
`forEach` lambda, immediately before the `Box`** (NOT at the top of the outer `@Composable` function —
`slot` isn't in scope there, and hoisting outside the loop would capture only the first slot). Both
`stringResource` calls are then unconditional *per iteration* (Compose's rule forbids a conditional
`stringResource` *call* — evaluating both vals then choosing between the resolved strings inside
`semantics {}` satisfies it). Precedent: `BattleControlRail.kt` resolves per-item strings the same way.

InRoundUpgradeMenu.kt: add `stringResource` import; line 109 `Text("✕", …)` → `Text(stringResource(R.string.battle_close), …)`.

- [ ] **Step 3: Build**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log`
Expected: `BUILD SUCCESSFUL`. A "@Composable invocations can only happen from the context of a @Composable function" error means a `stringResource` is still inside `semantics {}` — move it out per Step 2.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ErrorState.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/SobTopAppBar.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/UltimateWeaponBar.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/InRoundUpgradeMenu.kt
git commit -m "i18n(#34): extract shared ui + battle/ui Compose component strings (phase 2)"
```

---

### Task 13: Screen.secondaryTitle → @StringRes + MainActivity call site + test

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt` (`secondaryTitle` helper, ~lines 117–129)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt` (lines 243–248 topBar slot)
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt`
- Modify: `strings.xml`

**Why a return-type change:** `secondaryTitle` returns `String?` and is called from the `@Composable` topBar slot. To localize without making `Screen` depend on a `Context` (it must stay Android-light, and its `by lazy` route lists are init-order-sensitive — #161 fragile zone), change the helper to return `@StringRes Int?` (a resource id is a plain `Int`, no Android runtime dep) and resolve it with `stringResource` at the `MainActivity` call site.

- [ ] **Step 1: Add string resources**

```xml
    <!-- Secondary-screen TopAppBar titles (Screen.secondaryTitle) -->
    <string name="screen_title_weapons">Ultimate Weapons</string>
    <string name="screen_title_cards">Cards</string>
    <string name="screen_title_supplies">Unclaimed Supplies</string>
    <string name="screen_title_economy">Premium Currencies</string>
    <string name="screen_title_missions">Missions</string>
    <string name="screen_title_settings">Settings</string>
    <string name="screen_title_store">Store</string>
    <string name="screen_title_help">Help</string>
```

- [ ] **Step 2: Change secondaryTitle to return @StringRes Int?**

In `Screen.kt`, add import `androidx.annotation.StringRes` and `com.whitefang.stepsofbabylon.R`. Replace the helper:

```kotlin
        @StringRes
        fun secondaryTitle(route: String?): Int? =
            when (route) {
                Weapons.route -> R.string.screen_title_weapons
                Cards.route -> R.string.screen_title_cards
                Supplies.route -> R.string.screen_title_supplies
                Economy.route -> R.string.screen_title_economy
                Missions.route -> R.string.screen_title_missions
                Settings.route -> R.string.screen_title_settings
                Store.route -> R.string.screen_title_store
                Help.route -> R.string.screen_title_help
                else -> null
            }
```

The `when (route)` body still touches only the route Strings (`Weapons.route` etc.) — it does NOT iterate the `by lazy` lists, so the #161 init-order guard and `DeepLinkRoutingTest` are unaffected.

- [ ] **Step 3: Update the MainActivity call site**

In `MainActivity.kt` lines 243–248, change:

```kotlin
                        Screen.secondaryTitle(topBarRoute)?.let { title ->
                            SobTopAppBar(
                                title = title,
                                onNavigateBack = { navController.navigateUp() },
                            )
                        }
```

to:

```kotlin
                        Screen.secondaryTitle(topBarRoute)?.let { titleRes ->
                            SobTopAppBar(
                                title = stringResource(titleRes),
                                onNavigateBack = { navController.navigateUp() },
                            )
                        }
```

Confirm `import androidx.compose.ui.res.stringResource` is present in MainActivity (add if not). `SobTopAppBar`'s `title: String` param is unchanged — we resolve to a String here.

- [ ] **Step 4: Update ScreenSecondaryTitleTest**

The first test currently asserts `assertEquals("Ultimate Weapons", Screen.secondaryTitle(Screen.Weapons.route))`. The return is now a resource id. Resolve it through a context. Replace the first test body:

```kotlin
    @Test
    fun `secondaryTitle returns the explicit title res for each of the 8 push-children`() {
        val ctx = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        fun title(route: String?) = Screen.secondaryTitle(route)?.let { ctx.getString(it) }

        assertEquals("Ultimate Weapons", title(Screen.Weapons.route))
        assertEquals("Cards", title(Screen.Cards.route))
        assertEquals("Unclaimed Supplies", title(Screen.Supplies.route))
        assertEquals("Premium Currencies", title(Screen.Economy.route))
        assertEquals("Missions", title(Screen.Missions.route))
        assertEquals("Settings", title(Screen.Settings.route))
        assertEquals("Store", title(Screen.Store.route))
        assertEquals("Help", title(Screen.Help.route))
    }
```

The three `assertNull` tests still work unchanged (`secondaryTitle(...)` returns `null` for tabs/Onboarding/unknown — null is null regardless of the value type). Add import `androidx.test.core.app.ApplicationProvider` if not present (the test already runs under Robolectric `@Config`).

- [ ] **Step 5: Run the nav test**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*ScreenSecondaryTitleTest" > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log`
Expected: `BUILD SUCCESSFUL`, green.

- [ ] **Step 6: Build full + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt
git commit -m "i18n(#34): localize Screen.secondaryTitle TopAppBar titles via @StringRes (phase 2)"
```

---

### Task 14: Non-`@Composable` string-helper conversions (`@StringRes` + call-site resolve)

These functions RETURN English strings but are NOT `@Composable`, so they can't call `stringResource`.
Per the approved design decision, convert each to return `@StringRes Int` (+ format args where
needed) and resolve at the Compose call site — the same pattern as `Screen.secondaryTitle` (Task 13).

**Files:**
- Modify: `strings.xml`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplay.kt` (`CurrencyType.label()` ~line 55, caller line 93)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt` (`pathLabel()` ~line 243, its callers)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt` (`formatTime()` `"Done!"` ~line 251, callers) — **only the `"Done!"` literal; the duration formatting stays**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt` (`formatTimeAgo()` `"Just now"` ~line 167, callers)

- [ ] **Step 1: Add string resources**

```xml
    <!-- Non-composable helper returns (resolved at call site) -->
    <string name="currency_label_steps">Steps</string>
    <string name="currency_label_cash">Cash</string>
    <string name="currency_label_gems">Gems</string>
    <string name="currency_label_power_stones">Power Stones</string>
    <string name="uw_path_damage">Damage</string>
    <string name="uw_path_slow_factor">Slow factor</string>
    <string name="uw_path_cash_multiplier">Cash multiplier</string>
    <string name="uw_path_dot">DoT % MaxHP/sec</string>
    <string name="uw_path_damage_dps">Damage DPS</string>
    <string name="uw_path_chain_length">Chain length</string>
    <string name="uw_path_radius">Radius</string>
    <string name="uw_path_pull_strength">Pull strength</string>
    <string name="uw_path_duration">Duration</string>
    <string name="uw_path_area">Area</string>
    <string name="uw_path_damage_multiplier">Damage multiplier</string>
    <string name="uw_path_cooldown">Cooldown</string>
    <string name="lab_time_done">Done!</string>
    <string name="time_just_now">Just now</string>
```

- [ ] **Step 2: Convert `CurrencyType.label()` to `@StringRes`**

In `CurrencyDisplay.kt`, add imports `androidx.annotation.StringRes` and `com.whitefang.stepsofbabylon.R`. Change:

```kotlin
@StringRes
fun CurrencyType.label(): Int =
    when (this) {
        CurrencyType.STEPS -> R.string.currency_label_steps
        CurrencyType.CASH -> R.string.currency_label_cash
        CurrencyType.GEMS -> R.string.currency_label_gems
        CurrencyType.POWER_STONES -> R.string.currency_label_power_stones
    }
```

At the caller (line 93, inside a `@Composable`): `contentDescription = type.label()` →
`contentDescription = stringResource(type.label())`. Search the repo for any OTHER `.label()` caller
(`sg -l kotlin -p '$X.label()'`) — line 93 is the only known one; resolve each with `stringResource`
(in a `@Composable`) or `context.getString` (outside one).

- [ ] **Step 3: Convert `pathLabel()` to `@StringRes`**

In `UltimateWeaponScreen.kt`, change `pathLabel(type, path): String` to return `@StringRes Int`,
mapping each branch to the matching `R.string.uw_path_*` key (DAMAGE/CHRONO_FIELD→`uw_path_slow_factor`,
GOLDEN_ZIGGURAT→`uw_path_cash_multiplier`, POISON_SWAMP→`uw_path_dot`, BLACK_HOLE→`uw_path_damage_dps`,
else→`uw_path_damage`; SECONDARY/CHAIN_LIGHTNING→`uw_path_chain_length`, DEATH_WAVE→`uw_path_radius`,
BLACK_HOLE→`uw_path_pull_strength`, CHRONO_FIELD→`uw_path_duration`, POISON_SWAMP→`uw_path_area`,
GOLDEN_ZIGGURAT→`uw_path_damage_multiplier`; COOLDOWN→`uw_path_cooldown`). Add `@StringRes` import.
At each `pathLabel(...)` call site (a `@Composable` — confirm), wrap: `stringResource(pathLabel(type, path))`.

- [ ] **Step 4: Convert the `formatTime` / `formatTimeAgo` early-return literals**

These functions interpolate durations, so they must stay `String`-returning (can't be pure `@StringRes`).
For the pure-literal early returns only, hoist the literal to the call site. Simplest pattern: have the
function return a sentinel the caller maps, OR (preferred, minimal) keep the function `String` but pass
the resolved "Done!"/"Just now" strings IN as parameters resolved at the call site. Concretely, in
`LabsScreen.kt` change `formatTime(ms)`'s `if (ms <= 0) return "Done!"` to
`if (ms <= 0) return doneLabel` and add a `doneLabel: String` param; at the (composable) call site pass
`doneLabel = stringResource(R.string.lab_time_done)`. Apply the same shape to `formatTimeAgo`'s
`"Just now"` → `justNowLabel: String` param + `stringResource(R.string.time_just_now)` at the call site.
(If a call site is NOT composable, resolve with `context.getString` instead.)

- [ ] **Step 5: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/CurrencyDisplay.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt
git commit -m "i18n(#34): localize non-composable string helpers via @StringRes (phase 2)"
```

---

### Task 15: PR2 full verification + open PR

- [ ] **Step 1: Sweep for any remaining hardcoded literals in PR2 scope**

Run (the comprehensive methodology grep, not just `Text("…")`):
```bash
for d in home economy stats labs missions weapons supplies ui battle/ui navigation; do
  echo "== $d =="
  grep -rnE '"[A-Z][a-z]|"[a-z]+ [a-z]' \
    "app/src/main/java/com/whitefang/stepsofbabylon/presentation/$d" --include='*.kt' 2>/dev/null \
    | grep -vE '^\s*[^:]+:[0-9]+:\s*//|^\s*[^:]+:[0-9]+:\s*\*|import |\.typography|MaterialTheme|Color\.|fontWeight|R\.string|stringResource'
done
```
Expected: only data-only `Text("$…")` survivors, glyphs (`"—"`), and the deferred files. Any English
literal left = migrate it (add a key, repeat the pattern) before proceeding.

- [ ] **Step 2: Run full JVM suite + lint**

Run:
```bash
./run-gradle.sh :app:testDebugUnitTest > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
./run-gradle.sh :app:detekt > /tmp/d.log 2>&1 && tail -n 10 /tmp/d.log
./lint-kotlin.sh
```
Expected: all `BUILD SUCCESSFUL`, 1282 JVM tests, 0 failures.

- [ ] **Step 3: Open PR2**

```bash
git push -u origin i18n/34-pr2-remaining
gh pr create --title "i18n(#34): extract Compose strings — PR2 remaining screens + shared/nav surfaces" \
  --body "Phase 2 of ADR-0014. Home/economy/stats/labs/missions/weapons/supplies + shared presentation/ui + battle/ui Compose components (UltimateWeaponBar cd, InRoundUpgradeMenu ✕) + Screen.secondaryTitle TopAppBar titles (now @StringRes, resolved at the MainActivity call site; ScreenSecondaryTitleTest updated). No behavior change; 1282 JVM unchanged. Several screens lack Compose UI tests (Stats/Economy/Labs/Missions) — visual-diff reviewed. Refs #34 (closes after PR3 deferred Canvas/Activity follow-up)."
```

---

## Doc sync + checkpoint (after BOTH PRs merge — PR Task-List Convention)

These run as the FINAL task, before declaring the effort done. Per CLAUDE.md, the doc sync precedes the STATE/RUN_LOG update.

- [ ] **Step 1: Sync current-state docs**
  - `CHANGELOG.md` — add an `[Unreleased]` entry: "i18n phase 2 — Compose-screen string extraction (#34), no behavior change, 1282 JVM unchanged."
  - `docs/agent/DECISIONS/ADR-0014…` — append a note that phase 2 (Compose screens) shipped; PR3 (Canvas/Activity) remains; correct the historical "flip HardcodedText in the final PR" line to reflect that the guard was already enabled in phase 1 and is XML-only.
  - `CLAUDE.md` — test count unchanged (1282); no architecture change → likely no edit. Confirm.
  - `docs/steering/source-files.md` — `Screen.kt` `secondaryTitle` now returns `@StringRes Int?`; note if the file index tracks that signature.

- [ ] **Step 2: Run `/checkpoint`** to update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md` (rotate current objective; note #34 phase 2 done, PR3 deferred).

---

## Notes for the implementer

- **`strings.xml` ordering:** append each task's block at the end (before `</resources>`), grouped by the comment header shown. Don't reorder existing entries.
- **If a build fails on a missing `R.string.x`:** you either forgot Step 1 (add the resource) or typo'd the key. The resource name and the `R.string.` reference must match exactly.
- **Robolectric NATIVE-lane caveat:** `stringResource` works in tests only inside a `setContent {}` block; in plain JUnit assertions use `context.getString(R.string.x, args)` (Tasks 3, 4, 13 use this).
- **Deferred (NOT this plan — PR3 follow-up):** `BattleRenderer.kt` + `WaveAnnouncement.kt` `Canvas.drawText` literals (need `domain/Strings` methods), and `HealthConnectPermissionActivity.kt`. Leave them.
- **Fragile-zone reminder:** do not touch `Screen`'s `by lazy` route lists, `Screen.items`/`argumentFreeRoutes`, or the deep-link set — only `secondaryTitle`'s return type/body changes (Task 13).
