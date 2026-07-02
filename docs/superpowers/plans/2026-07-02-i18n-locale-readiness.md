# i18n Locale-Readiness (phase 3, final) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract every remaining user-facing English string literal (categories A–I + Z from the spec) so a `res/values-<locale>/strings.xml` fully translates the app — the prerequisite for the first real non-English locale.

**Architecture:** Reuse the two seams prior phases established — (1) the `domain/Strings` interface + `data/AndroidStrings` impl for non-Composable/engine strings, and (2) `@StringRes Int` return + call-site `stringResource` for values/functions that can't be `@Composable`. The one new type is a sealed `presentation/ui/UiMessage` for ViewModel transient messages. Domain stays Android-free (`DomainPurityTest`); enum→resource resolvers live in `presentation/`.

**Tech Stack:** Kotlin, Jetpack Compose (`stringResource`/`pluralStringResource`), Android string + plurals resources, Hilt (`@ApplicationContext`), Robolectric JVM-lane Compose tests, JUnit Jupiter (JVM) + JUnit4 (Robolectric/instrumented).

**Spec:** `docs/superpowers/specs/2026-07-02-i18n-locale-readiness-design.md` (passed the Adversarial Review Gate 2026-07-02: 37 findings, 26 surviving, all applied).

---

## ⚠️ Execution contract (read FIRST)

**Six PRs, strictly sequential. Each PR MUST be fully merged to `main` and the merge verified before the next PR's branch is cut.** This is a developer requirement AND an ordering dependency: PR3b's error-channel change edits the SAME UiState files PR3a touches, so branching PR3b off a pre-PR3a `main` would conflict. The per-PR **"Merge gate"** task at the end of each PR is mandatory — do not start the next PR until it passes.

Repo merge policy is **merge-commits only** (squash/rebase disabled) with auto-merge enabled (STATE.md). CI (`ci.yml` + `instrumented.yml`) gates `main`: PR lane (lint + unit + assembleDebug + schema-drift) + instrumented emulator lane. `.claude/**`/docs-only diffs skip the build gate; **all these PRs touch app code**, so both lanes run.

**Methodology grep gate (per file, every PR).** After editing a file, run this and confirm only data-only (`Text("$…")`), glyphs (`—`/`✕`/`+`), and technical survivors remain:
```bash
grep -nE '"[A-Z][a-z]|"[a-z]+ [a-z]' <file> | grep -vE '^\s*//|^\s*\*|import |\.typography|MaterialTheme|Color\.|fontWeight|tag = |R\.string|stringResource|Log\.|navigate_to'
```

## Conventions for EVERY task (read once)

- **Key naming:** `screen_element` snake_case. Namespaces this phase introduces: `msg_*` (VM user messages), `billing_error_*`, `ad_error_*`, `nav_*` (bottom-nav labels), `help_*`, `onboarding_slide_*`, and the category-G families `upgrade_desc_*`/`research_desc_*`/`mission_desc_*`/`uw_desc_*`/`card_effect_*`/`milestone_name_*`/`cosmetic_*`. Reuse an existing key if one already fits (grep `strings.xml` first) rather than minting a duplicate.
- **Static literal** → `stringResource(R.string.key)` (Compose) / `context.getString(R.string.key)` (non-Compose with a Context) / a `@StringRes Int` resolved at the call site (non-Compose, no Context). **Literal with embedded data** → format-arg resource (`%1$d`/`%1$s`) + the arg at resolution.
- **Escaping:** apostrophes `\'`; meaningful leading/trailing space → wrap the whole value in `"…"`. Keep emoji/glyphs (`⚠`, `👟`, `•`, `×`) byte-identical in the resource value.
- **Build commands** (Gradle is verbose — redirect + tail): 
  - Full JVM suite: `./run-gradle.sh :app:testDebugUnitTest > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log`
  - Compile + resource link: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log`
  - A single test class: append `--tests "*ClassName"`.
  - Static analysis: `./run-gradle.sh :app:detekt > /tmp/d.log 2>&1 && tail -n 10 /tmp/d.log` + `./lint-kotlin.sh` (formatting; `--format` to auto-fix).
- **`strings.xml`/`plurals.xml` ordering:** append each task's block at the end (before `</resources>`), under a `<!-- comment header -->`. Don't reorder existing entries. `strings.xml` append point is after `<string name="time_just_now">Just now</string>`; `plurals.xml` after the last `</plurals>`.
- **Robolectric caveat:** `stringResource` works in tests only inside a `setContent {}`; in plain JVM assertions use `context.getString(R.string.x, args)`.
- **No behavior change.** Rendered English stays byte-identical (a `<plurals>` entry encodes today's exact text). Tests that pinned migrated English switch to type/id assertions **in place**.

---

# PR3a — VM user-messages + data-layer errors (categories A + A′)

**Branch:** `i18n/34-pr3a-vm-messages` (cut from `main`).
**Scope:** sealed `UiMessage` type; migrate `userMessage` in **6** VMs (Store/Cards/Workshop/Labs/Missions/Battle); localize `BillingManagerImpl` (has `@ApplicationContext`); **add `@ApplicationContext` to `RewardAdManagerImpl`** then localize it; VM-side static ad fallbacks become typed `UiMessage` cases; new `UiMessageTest`.

---

### Task A1: Create the `UiMessage` sealed type + resolver

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/UiMessage.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R

/**
 * A transient, localizable user message emitted by a ViewModel and resolved to a String at the
 * Compose call site (see any screen's `LaunchedEffect(state.userMessage)`). Carries a @StringRes id
 * (+ optional positional format args) so ViewModels stay Context-free and pure-JVM testable — tests
 * assert the UiMessage type/args, not English text. Mirrors the @StringRes pattern used by
 * Screen.secondaryTitle / CurrencyType.label (ADR-0014 phase 2/3). i18n #34 phase 3.
 */
sealed interface UiMessage {
    @get:StringRes
    val resId: Int
    val args: List<Any> get() = emptyList()

    // --- Currency / affordability ---
    data object NotEnoughGems : UiMessage {
        override val resId = R.string.msg_not_enough_gems
    }
    data object NotEnoughSteps : UiMessage {
        override val resId = R.string.msg_not_enough_steps
    }

    // --- Workshop / Labs / Cards level + slot states ---
    data object AlreadyMaxLevel : UiMessage {
        override val resId = R.string.msg_already_max_level
    }
    data object NoAffordableUpgrades : UiMessage {
        override val resId = R.string.msg_no_affordable_upgrades
    }
    data object CardAtMaxLevel : UiMessage {
        override val resId = R.string.msg_card_at_max_level
    }
    data object NotEnoughCopies : UiMessage {
        override val resId = R.string.msg_not_enough_copies
    }
    data object ResearchComingSoon : UiMessage {
        override val resId = R.string.msg_research_coming_soon
    }
    data object NoResearchSlot : UiMessage {
        override val resId = R.string.msg_no_research_slot
    }
    data object AlreadyResearching : UiMessage {
        override val resId = R.string.msg_already_researching
    }
    data object SeasonPassRequired : UiMessage {
        override val resId = R.string.msg_season_pass_required
    }
    data object FreeRushUsed : UiMessage {
        override val resId = R.string.msg_free_rush_used
    }
    data object NoActiveResearch : UiMessage {
        override val resId = R.string.msg_no_active_research
    }
    data object NotEnoughGemsOrMaxSlots : UiMessage {
        override val resId = R.string.msg_not_enough_gems_or_max_slots
    }

    // --- Missions ---
    data object NotEnoughStepsMission : UiMessage {
        override val resId = R.string.msg_mission_not_enough_steps
    }
    data object MilestoneAlreadyClaimed : UiMessage {
        override val resId = R.string.msg_milestone_already_claimed
    }

    /**
     * Missions UnknownCosmetic — %1$s = the cosmetic id being finalised.
     * (This is the concrete realization of the spec §4 generic `WithArgs(resId, args)` arg-case;
     * a named case is preferred over a generic (resId,args) pair for type-safety — intentional
     * divergence from the spec's illustrative `WithArgs`, per review finding F6.)
     */
    data class RewardUnavailable(val cosmeticId: String) : UiMessage {
        override val resId = R.string.msg_reward_unavailable
        override val args = listOf<Any>(cosmeticId)
    }

    // --- Ad flow (VM-side static fallbacks; the data-layer message itself arrives via Raw) ---
    data object AdCancelled : UiMessage {
        override val resId = R.string.msg_ad_cancelled
    }
    data object AdFailed : UiMessage {
        override val resId = R.string.msg_ad_failed
    }

    /**
     * Escape hatch for a message produced BELOW the presentation layer that is ALREADY localized
     * (a billing/ad Error whose text came from context.getString in the data layer — categories
     * A′-billing / A′-ads). Carries the resolved String verbatim. NEVER wrap an un-localized
     * lower-layer string in Raw.
     */
    data class Raw(val text: String) : UiMessage {
        override val resId: Int get() = 0 // unused; resolve() matches Raw before reading resId
    }

    companion object
}

/** Resolve to a display String. `Raw` is matched before `resId` is read, so `Raw.resId = 0` is never used. */
fun UiMessage.resolve(context: Context): String =
    when (this) {
        is UiMessage.Raw -> text
        else -> context.getString(resId, *args.toTypedArray())
    }
```

- [ ] **Step 2: Add the string resources**

Append to `strings.xml` under `<!-- i18n #34 phase 3: ViewModel user messages (category A) -->`:

```xml
    <!-- i18n #34 phase 3: ViewModel user messages (category A) -->
    <string name="msg_not_enough_gems">Not enough Gems</string>
    <string name="msg_not_enough_steps">Not enough Steps</string>
    <string name="msg_already_max_level">Already at max level</string>
    <string name="msg_no_affordable_upgrades">No affordable upgrades</string>
    <string name="msg_card_at_max_level">Card already at max level</string>
    <string name="msg_not_enough_copies">Not enough copies</string>
    <string name="msg_research_coming_soon">Coming soon — reserved for v1.x</string>
    <string name="msg_no_research_slot">No research slot available</string>
    <string name="msg_already_researching">Already researching</string>
    <string name="msg_season_pass_required">Season Pass required</string>
    <string name="msg_free_rush_used">Free rush already used today</string>
    <string name="msg_no_active_research">No active research to rush</string>
    <string name="msg_not_enough_gems_or_max_slots">Not enough Gems or max slots reached</string>
    <string name="msg_mission_not_enough_steps">You haven\'t walked enough steps yet.</string>
    <string name="msg_milestone_already_claimed">Milestone already claimed.</string>
    <string name="msg_reward_unavailable">Reward temporarily unavailable (cosmetic “%1$s” is being finalised). Try again after the next update.</string>
    <string name="msg_ad_cancelled">Ad cancelled. Try again.</string>
    <string name="msg_ad_failed">Ad failed to load. Try again later.</string>
```

(`msg_research_coming_soon` and `msg_reward_unavailable` reproduce the exact current Unicode — `—` em-dash, `“`/`”` curly quotes. `WorkshopViewModel`'s "Already at max level" reuses `msg_already_max_level` shared with Labs.)

- [ ] **Step 3: Build (compile + resource link)**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/UiMessage.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): add UiMessage sealed type + resolver + msg_* resources (phase 3 A)"
```

---

### Task A2: Migrate the four "screen" VMs (Store, Cards, Workshop, Labs) + their UiStates + screens

**Files:**
- Modify UiState field type in: `store/StoreUiState.kt:16`, `cards/CardsUiState.kt:30`, `workshop/WorkshopUiState.kt:30`, `labs/LabsUiState.kt:30` — change `val userMessage: String? = null` → `val userMessage: UiMessage? = null` (add `import com.whitefang.stepsofbabylon.presentation.ui.UiMessage`).
- Modify VM assignments:
  - `store/StoreViewModel.kt:153` `"Not enough Gems"` → `UiMessage.NotEnoughGems`; **line 135** `_userMessage.value = result.message` → `_userMessage.value = UiMessage.Raw(result.message)`; change the `_userMessage` backing field + `userMessage` combine source type to `UiMessage?`.
  - `cards/CardsViewModel.kt:114` → `NotEnoughGems`; `:132` `"Card already at max level"` → `CardAtMaxLevel`; `:136` `"Not enough copies"` → `NotEnoughCopies`; `:177` `"Ad cancelled. Try again."` → `AdCancelled`; `:181` `result.message.ifBlank { "Ad failed to load. Try again later." }` → **see Step 2 below** (typed fallback + Raw).
  - `workshop/WorkshopViewModel.kt:137` `"Already at max level"` → `AlreadyMaxLevel`; `:159` `"Not enough Steps"` → `NotEnoughSteps`; `:175` `"No affordable upgrades"` → `NoAffordableUpgrades`.
  - `labs/LabsViewModel.kt` `:157`→`ResearchComingSoon`; `:168`→`NotEnoughSteps`; `:169`→`NoResearchSlot`; `:170`→`AlreadyMaxLevel`; `:171`→`AlreadyResearching`; `:193`→`NotEnoughGems`; `:208`→`SeasonPassRequired`; `:212`→`FreeRushUsed`; `:217`→`NoActiveResearch`; `:237`→`NotEnoughGemsOrMaxSlots`.
- Modify screens' `LaunchedEffect`: `store/StoreScreen.kt:66-71`, `cards/CardsScreen.kt:77-82`, `workshop/WorkshopScreen.kt:57-62`, `labs/LabsScreen.kt:65-70`.

- [ ] **Step 1: Change each `_userMessage`/backing type + UiState field**

Each of the four VMs holds a `private val _userMessage = MutableStateFlow<String?>(null)` (or exposes `userMessage` via combine). Change the generic to `<UiMessage?>`. Change each UiState `userMessage` field to `UiMessage?`. Add the import. Then replace each literal assignment with the typed case per the mapping above (all are `data object`s — no args). Example (WorkshopViewModel.kt:159):

```kotlin
// before: _userMessage.value = "Not enough Steps"
_userMessage.value = UiMessage.NotEnoughSteps
```

- [ ] **Step 2: Cards ad-fallback (line 181) — split literal vs forwarded**

`CardsViewModel.kt:181` today: `_userMessage.value = result.message.ifBlank { "Ad failed to load. Try again later." }`. The blank branch is a VM-side static literal (→ typed case); the non-blank branch is an already-localized data-layer string (→ `Raw`, once Task A4 localizes the ad manager):

```kotlin
_userMessage.value =
    if (result.message.isBlank()) UiMessage.AdFailed else UiMessage.Raw(result.message)
```

- [ ] **Step 3: Update each screen's `LaunchedEffect` to resolve**

Each screen has:
```kotlin
LaunchedEffect(state.userMessage) {
    state.userMessage?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.clearMessage()
    }
}
```
Add `val context = LocalContext.current` (with `import androidx.compose.ui.platform.LocalContext` if absent) above the effect, add `import com.whitefang.stepsofbabylon.presentation.ui.resolve`, and change the body:
```kotlin
LaunchedEffect(state.userMessage) {
    state.userMessage?.let {
        snackbarHostState.showSnackbar(it.resolve(context))
        viewModel.clearMessage()
    }
}
```
(`LocalContext.current` is a `@Composable` read — it must be OUTSIDE the `LaunchedEffect` lambda, then captured. StoreScreen/Cards/Workshop/Labs do not currently read `LocalContext`; add it.)

- [ ] **Step 4: Build**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/{store,cards,workshop,labs}
git commit -m "i18n(#34): migrate Store/Cards/Workshop/Labs userMessage to UiMessage (phase 3 A)"
```

---

### Task A3: Migrate Missions + Battle VMs

**Files:**
- `missions/MissionsUiState.kt:36` + `missions/MissionsViewModel.kt:216,220,224` + `missions/MissionsScreen.kt:69-74`.
- `battle/BattleUiState.kt:55` + `battle/BattleViewModel.kt:768,772,793,797` + `battle/BattleScreen.kt:113-118` (BattleScreen already has `val context = LocalContext.current` at :80).

- [ ] **Step 1: Missions**

`MissionsUiState.kt:36` `val userMessage: String? = null` → `UiMessage?` (+ import). In `MissionsViewModel.kt`: `:216` → `UiMessage.NotEnoughStepsMission`; `:220` → `UiMessage.MilestoneAlreadyClaimed`; `:224` (the interpolated `UnknownCosmetic` case — currently `"Reward temporarily unavailable (cosmetic “${result.cosmeticId}” is being finalised)…"`) →
```kotlin
userMessage.value = UiMessage.RewardUnavailable(result.cosmeticId)
```
Note `MissionsViewModel` uses a `userMessage` MutableStateFlow directly (not `_userMessage`); change its generic to `UiMessage?`. Update `MissionsScreen.kt:69-74` `LaunchedEffect` to resolve (add `LocalContext.current` + `.resolve(context)`).

- [ ] **Step 2: Battle**

`BattleUiState.kt:55` `val userMessage: String? = null` → `UiMessage?` (+ import). In `BattleViewModel.kt`: `:768` and `:793` `it.copy(userMessage = "Ad cancelled. Try again.")` → `it.copy(userMessage = UiMessage.AdCancelled)`; `:772` and `:797` `val msg = result.message.ifBlank { "Ad failed to load. Try again later." }` → 
```kotlin
val msg: UiMessage =
    if (result.message.isBlank()) UiMessage.AdFailed else UiMessage.Raw(result.message)
```
then `it.copy(userMessage = msg)`. Update `BattleScreen.kt:113-118` `LaunchedEffect` body to `showSnackbar(it.resolve(context))` (context already present at :80; add the `resolve` import).

- [ ] **Step 3: Build**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/{missions,battle}
git commit -m "i18n(#34): migrate Missions + Battle userMessage to UiMessage (phase 3 A)"
```

---

### Task A4: Localize `BillingManagerImpl` (A′-billing)

**Files:**
- `data/billing/BillingManagerImpl.kt` (has `@ApplicationContext private val context: Context` at :80).
- `strings.xml`.

- [ ] **Step 1: Add billing_error_* resources**

```xml
    <!-- i18n #34 phase 3: billing error messages (category A′) -->
    <string name="billing_error_no_activity">No activity available for purchase</string>
    <string name="billing_error_product_unavailable">Product %1$s not available. (Play Console SKU may be missing or not yet released.)</string>
    <string name="billing_error_cancelled">Purchase cancelled</string>
    <string name="billing_error_pending">Purchase pending — complete payment to receive your items</string>
    <string name="billing_error_unverified">Purchase could not be verified</string>
    <string name="billing_error_service_unavailable">Billing service unavailable. Try again shortly.</string>
    <string name="billing_error_billing_unavailable">Google Play billing is not available on this device.</string>
    <string name="billing_error_item_unavailable">This product is not available right now.</string>
    <string name="billing_error_item_owned">You already own this item.</string>
    <string name="billing_error_developer">Purchase failed (configuration error).</string>
    <string name="billing_error_network">Network error. Check your connection and try again.</string>
    <string name="billing_error_other">Purchase failed (code %1$d).</string>
    <string name="billing_error_ok">OK</string>
```

- [ ] **Step 2: Replace the inline literals**

- `:97` `PurchaseResult.Error("No activity available for purchase")` → `PurchaseResult.Error(context.getString(R.string.billing_error_no_activity))`
- `:107-109` `"Product ${product.skuId()} not available. " + "(...)"` → `PurchaseResult.Error(context.getString(R.string.billing_error_product_unavailable, product.skuId()))`
- `:132` `"Purchase cancelled"` → `context.getString(R.string.billing_error_cancelled)`
- `:212` `"Purchase pending — complete payment to receive your items"` → `context.getString(R.string.billing_error_pending)`
- `:228` `"Purchase could not be verified"` → `context.getString(R.string.billing_error_unverified)`

Add `import com.whitefang.stepsofbabylon.R` if absent.

- [ ] **Step 3: Replace `toUserMessage()` (all 10 branches, :440-452)**

```kotlin
private fun SdkBillingResult.toUserMessage(): String =
    when (this) {
        is SdkBillingResult.Ok -> context.getString(R.string.billing_error_ok)
        is SdkBillingResult.UserCanceled -> context.getString(R.string.billing_error_cancelled)
        is SdkBillingResult.ServiceDisconnected -> context.getString(R.string.billing_error_service_unavailable)
        is SdkBillingResult.ServiceUnavailable -> context.getString(R.string.billing_error_service_unavailable)
        is SdkBillingResult.BillingUnavailable -> context.getString(R.string.billing_error_billing_unavailable)
        is SdkBillingResult.ItemUnavailable -> context.getString(R.string.billing_error_item_unavailable)
        is SdkBillingResult.ItemAlreadyOwned -> context.getString(R.string.billing_error_item_owned)
        is SdkBillingResult.DeveloperError -> context.getString(R.string.billing_error_developer)
        is SdkBillingResult.NetworkError -> context.getString(R.string.billing_error_network)
        is SdkBillingResult.Other -> context.getString(R.string.billing_error_other, responseCode)
    }
```
(`Other` is the arg-bearing branch → `%1$d` with `responseCode`. `toUserMessage` is a member extension where `context` is in scope via the enclosing class.)

- [ ] **Step 4: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/billing/BillingManagerImpl.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): localize BillingManagerImpl error messages (phase 3 A')"
```

---

### Task A5: Add `@ApplicationContext` to `RewardAdManagerImpl` + localize (A′-ads)

**Files:**
- `data/ads/RewardAdManagerImpl.kt` (ctor `:74-80` does NOT inject a Context — mirror BillingManagerImpl).
- `strings.xml`.

- [ ] **Step 1: Add ad_error_* resources**

```xml
    <!-- i18n #34 phase 3: ad error messages (category A′) -->
    <string name="ad_error_no_activity">No activity available for ad</string>
    <string name="ad_error_consent_pending">Can\'t request ads yet — consent pending</string>
    <string name="ad_error_load_internal">Ad request was invalid. (Internal error.)</string>
    <string name="ad_error_load_invalid">Ad request was invalid. (Invalid request.)</string>
    <string name="ad_error_load_network">Network error. Check your connection and try again.</string>
    <string name="ad_error_load_no_fill">No ad available right now. Try again later.</string>
    <string name="ad_error_load_other">Couldn\'t load ad. (code %1$d)</string>
    <string name="ad_error_show_internal">Ad couldn\'t play. (Internal error.)</string>
    <string name="ad_error_show_already">Ad was already shown.</string>
    <string name="ad_error_show_not_ready">Ad isn\'t ready to show.</string>
    <string name="ad_error_show_foreground">Ad couldn\'t play. (App not in foreground.)</string>
    <string name="ad_error_show_other">Couldn\'t show ad. (code %1$d)</string>
```

- [ ] **Step 2: Add the Context injection**

Mirror BillingManagerImpl exactly. Add to the ctor and add the imports (`android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `com.whitefang.stepsofbabylon.R`):
```kotlin
@Inject
constructor(
    private val adapter: RewardedAdAdapter,
    private val consentManager: ConsentManager,
    private val activityProvider: ActivityProvider,
    @ApplicationContext private val context: Context,
) : RewardAdManager {
```
Hilt provides `@ApplicationContext` with no module change (application-scoped binding). This is a `@Singleton`/`@Inject` class — verify no manual construction site breaks (grep `RewardAdManagerImpl(` — should be Hilt-only).

- [ ] **Step 3: Replace the literals + both mappers**

- `:93` `AdResult.Error("No activity available for ad")` → `AdResult.Error(context.getString(R.string.ad_error_no_activity))`
- `:107` `"Can't request ads yet — consent pending"` → `context.getString(R.string.ad_error_consent_pending)`
- `SdkAdLoadResult.Error.toUserMessage()` (:160-167):
```kotlin
private fun SdkAdLoadResult.Error.toUserMessage(): String =
    when (code) {
        0 -> context.getString(R.string.ad_error_load_internal)
        1 -> context.getString(R.string.ad_error_load_invalid)
        2 -> context.getString(R.string.ad_error_load_network)
        3 -> context.getString(R.string.ad_error_load_no_fill)
        else -> context.getString(R.string.ad_error_load_other, code)
    }
```
- `SdkAdShowResult.Error.toUserMessage()` (:172-179):
```kotlin
private fun SdkAdShowResult.Error.toUserMessage(): String =
    when (code) {
        0 -> context.getString(R.string.ad_error_show_internal)
        1 -> context.getString(R.string.ad_error_show_already)
        2 -> context.getString(R.string.ad_error_show_not_ready)
        3 -> context.getString(R.string.ad_error_show_foreground)
        else -> context.getString(R.string.ad_error_show_other, code)
    }
```

- [ ] **Step 4: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/ads/RewardAdManagerImpl.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): inject @ApplicationContext + localize RewardAdManagerImpl (phase 3 A')"
```

---

### Task A6: Update tests + add `UiMessageTest`

**Files:**
- `store/StoreViewModelTest.kt:253,270` · `cards/CardsViewModelTest.kt:210,236,257` · `battle/BattleViewModelTest.kt:290,311,337,358`.
- **`data/billing/BillingManagerImplTest.kt:242,264-265,281,315`** (review F3 — asserts exact English `.message`; A4 localizes those).
- **`data/ads/RewardAdManagerImplTest.kt:56,105,147`** (review F2/TS-01 — manually constructs the impl with 3 args on the plain-JVM lane + asserts exact English; A5 adds a Context param + `context.getString`).
- Create `presentation/ui/UiMessageTest.kt`.
- (Workshop/Labs/Missions VM tests have NO userMessage assertions — verified; no change.)

> ⚠️ **These two data-layer test reconciliations (F3, F2/TS-01) are load-bearing:** without them PR3a's
> A7 merge gate (`:app:testDebugUnitTest`) fails to COMPILE (RewardAdManagerImplTest's 3-arg ctor call)
> or fails assertions (`context.getString` returns empty on a non-Robolectric Context), blocking the
> entire sequential 6-PR chain at the first gate. Do them as Steps 4a/4b below.

- [ ] **Step 1: Update StoreViewModelTest**

`:253` `assertEquals("Network error", vm.uiState.value.userMessage)` — this is now a forwarded billing `Raw`. Under Robolectric the resolved text is the localized string; but StoreViewModelTest is a pure JVM VM test (no Robolectric). Assert the **type**:
```kotlin
assertEquals(UiMessage.Raw("Network error"), vm.uiState.value.userMessage)
```
Wait — the billing fake returns `PurchaseResult.Error("Network error")` verbatim, so `Raw("Network error")` is exact. `:270` `"Purchase pending — …"` likewise → `UiMessage.Raw("Purchase pending — complete payment to receive your items")` (the fake supplies that string). `:285` `assertNull(...)` unchanged. Add `import com.whitefang.stepsofbabylon.presentation.ui.UiMessage`.

(If the fake billing manager in `test/fakes/` returns those strings, keep them as `Raw`. If instead a test wants to assert a *typed* message, that only applies to the VM-side static cases, not forwarded billing text.)

- [ ] **Step 2: Update CardsViewModelTest**

- `:210` `assertEquals("Ad cancelled. Try again.", …)` → `assertEquals(UiMessage.AdCancelled, vm.uiState.value.userMessage)`.
- `:236` `assertEquals("load failed", …)` (non-blank ad error, forwarded) → `assertEquals(UiMessage.Raw("load failed"), vm.uiState.value.userMessage)`.
- `:257` `assertEquals("Ad failed to load. Try again later.", …)` (blank branch fallback) → `assertEquals(UiMessage.AdFailed, vm.uiState.value.userMessage)`.

- [ ] **Step 3: Update BattleViewModelTest** (mapping corrected per review TS-03 — `:311` is a FORWARDED non-blank ad error, NOT AdCancelled; the 2nd AdCancelled is `:337`)

- `:290` `assertEquals("Ad cancelled. Try again.", …)` (watchGemAd Cancelled) → `assertEquals(UiMessage.AdCancelled, …)`.
- `:311` `assertEquals("No ad available", …)` (watchGemAd Error — forwarded non-blank `result.message`) → `assertEquals(UiMessage.Raw("No ad available"), …)`. **Verify the exact forwarded string the test's fake ad manager returns and use it in `Raw(...)`.**
- `:337` `assertEquals("Ad cancelled. Try again.", …)` (watchPsAd Cancelled) → `assertEquals(UiMessage.AdCancelled, …)`.
- `:358` `assertEquals("Ad failed to load. Try again later.", …)` (watchPsAd blank fallback) → `assertEquals(UiMessage.AdFailed, …)`.

- [ ] **Step 4: Write `UiMessageTest` (Robolectric JVM lane)**

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Config MUST match the repo's Robolectric JVM lane — mirror CardsScreenTest exactly (review TS-04):
// @RunWith(RobolectricTestRunner::class) + @Config(sdk = [34], application = android.app.Application::class).
// Do NOT use @Config(manifest = Config.NONE) (non-standard here). Import BOTH assertEquals + assertTrue.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UiMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val staticCases =
        listOf(
            UiMessage.NotEnoughGems, UiMessage.NotEnoughSteps, UiMessage.AlreadyMaxLevel,
            UiMessage.NoAffordableUpgrades, UiMessage.CardAtMaxLevel, UiMessage.NotEnoughCopies,
            UiMessage.ResearchComingSoon, UiMessage.NoResearchSlot, UiMessage.AlreadyResearching,
            UiMessage.SeasonPassRequired, UiMessage.FreeRushUsed, UiMessage.NoActiveResearch,
            UiMessage.NotEnoughGemsOrMaxSlots, UiMessage.NotEnoughStepsMission,
            UiMessage.MilestoneAlreadyClaimed, UiMessage.AdCancelled, UiMessage.AdFailed,
        )

    @Test
    fun `every static UiMessage resolves to a non-blank string`() {
        staticCases.forEach { msg ->
            assertTrue("${msg::class.simpleName} must resolve non-blank", msg.resolve(context).isNotBlank())
        }
    }

    @Test
    fun `RewardUnavailable formats the cosmetic id argument`() {
        val text = UiMessage.RewardUnavailable("zig_jade").resolve(context)
        assertTrue("must interpolate the cosmetic id", text.contains("zig_jade"))
    }

    @Test
    fun `Raw returns its verbatim text`() {
        assertEquals("verbatim", UiMessage.Raw("verbatim").resolve(context))
    }
}
```
(Match the repo's existing Robolectric config — check `CardsScreenTest` for the exact `@RunWith`/`@Config`/`@GraphicsMode` annotations and mirror them; adjust imports if the repo uses JUnit Jupiter vs JUnit4 on the Robolectric lane — `CardsScreenTest` is the reference.)

- [ ] **Step 4a: Reconcile `BillingManagerImplTest` (review F3 — REQUIRED, breaks the gate otherwise)**

`BillingManagerImplTest` runs on the plain JVM lane (`org.junit.Assert`, no Robolectric) and asserts the exact English `.message` that Task A4 now resolves via `context.getString`. Open it and reconcile the ~4 assertions:
- `:242` `assertEquals("Purchase cancelled", (result as PurchaseResult.Error).message)`
- `:264-265` `.message.contains("gem_pack_small")` (the product-unavailable arg case)
- `:281` `assertEquals("No activity available for purchase", …)`
- `:315` `assertTrue(...message.contains("pending"))`

Because A4 makes `.message` come from `context.getString(R.string.billing_error_*)`, on a plain JVM Context those return empty/throw. **Move the class to the Robolectric JVM lane** (`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], application = android.app.Application::class)`, mirroring `CardsScreenTest`) and keep the English assertions — under the default locale `context.getString` resolves to the same English, so the assertions pass byte-identically. (Alternative: assert the `PurchaseResult.Error` *shape* + the arg, if converting the lane is too invasive — but the resource-resolved-English route keeps the strongest coverage.) Confirm the test builds the `BillingManagerImpl` with a resource-backed Context. Verify the exact assertion lines by symbol (line numbers may drift).

- [ ] **Step 4b: Reconcile `RewardAdManagerImplTest` (review F2/TS-01 — REQUIRED, breaks compile otherwise)**

`RewardAdManagerImplTest` (a) manually constructs `RewardAdManagerImpl(adapter, consentManager, activityProvider)` at `:56` — Task A5's 4th ctor param (`context`) breaks this compile; and (b) runs on the plain-JVM lane (class doc says "No Robolectric required") asserting exact English at `:105`/`:147` that A5 now resolves via `context.getString`. Fix both:
- Convert the class to the Robolectric lane (`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], application = android.app.Application::class)`).
- Pass a Context to the ctor: `RewardAdManagerImpl(adapter, consentManager, activityProvider, ApplicationProvider.getApplicationContext())`.
- Keep the English assertions (`context.getString(R.string.ad_error_*)` resolves to the same English under the default locale) — they pass byte-identically. Verify lines by symbol.

- [ ] **Step 5: Run the affected tests**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "*UiMessageTest" --tests "*StoreViewModelTest" --tests "*CardsViewModelTest" --tests "*BattleViewModelTest" --tests "*BillingManagerImplTest" --tests "*RewardAdManagerImplTest" > /tmp/t.log 2>&1 && tail -n 25 /tmp/t.log` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/UiMessageTest.kt \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/{store,cards,battle} \
  app/src/test/java/com/whitefang/stepsofbabylon/data/{billing,ads}
git commit -m "i18n(#34): pin VM + data-layer tests to UiMessage/resource + add UiMessageTest (phase 3 A)"
```

---

### Task A7 (MERGE GATE): verify PR3a + full suite + open PR + merge + confirm merged

- [ ] **Step 1: Full JVM suite + lint**

```bash
./run-gradle.sh :app:testDebugUnitTest > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
./run-gradle.sh :app:detekt > /tmp/d.log 2>&1 && tail -n 10 /tmp/d.log
./lint-kotlin.sh
```
Expected: all `BUILD SUCCESSFUL`; JVM count = 1282 + UiMessageTest's 3 = **1285**, 0 failures. (Confirm the exact delta — if the Robolectric class counts differently, record the real number.)

- [ ] **Step 2: Methodology grep** on each touched VM/screen/data file — confirm no static English `userMessage`/`Error("…")` literal remains (only `Raw(...)` forwards + resource lookups).

- [ ] **Step 3: Push + open PR**

```bash
git push -u origin i18n/34-pr3a-vm-messages
gh pr create --title "i18n(#34): VM user-messages + data-layer errors — PR3a (A + A′)" \
  --body "Phase 3 of ADR-0014. Sealed UiMessage type; 6 VMs (Store/Cards/Workshop/Labs/Missions/Battle) migrate userMessage: String? -> UiMessage?; BillingManagerImpl + RewardAdManagerImpl (now @ApplicationContext-injected) localize error text at source; VM ad fallbacks are typed cases. New UiMessageTest. No behavior change. Refs #34."
```

- [ ] **Step 4: Merge + verify merged (GATE — do not start PR3b until this passes)**

Wait for CI green (PR lane + instrumented lane), then merge (auto-merge or `gh pr merge --merge`). Then:
```bash
git checkout main && git pull
git log --oneline -3   # confirm the PR3a merge commit is on main
```
Confirm the merge commit is present on `main` before cutting PR3b. If CI is red, fix on the branch and re-push; do not proceed.

---

# PR3b — error channel + easy Compose (categories C + F + H + Z)

**Branch:** `i18n/34-pr3b-error-compose` (cut from `main` **after PR3a merges**).
**Scope:** `SCREEN_LOAD_ERROR` → `@StringRes` across 10 VMs + `ErrorState`; the two Activities; HelpScreen (9 sections); WorkshopScreen EmptyState + straggler sweep.

---

### Task B1: `SCREEN_LOAD_ERROR` → `@StringRes Int` (category C)

**Files:**
- `presentation/ui/ErrorState.kt:56` (const) + `:29-33` (composable sig).
- 10 UiStates: `stats/StatsUiState.kt:40`, `store/StoreUiState.kt:15`, `supplies/SuppliesUiState.kt:8`, **`weapons/UltimateWeaponViewModel.kt:53`** (the `UltimateWeaponUiState` is defined INLINE in the VM file — there is NO separate `UltimateWeaponUiState.kt`; review CG-05), `cards/CardsUiState.kt:28`, `economy/EconomyUiState.kt:12`, `home/HomeUiState.kt:19`, `workshop/WorkshopUiState.kt:28`, `labs/LabsUiState.kt:28`, `missions/MissionsUiState.kt:28`.
- 10 VMs `.catch` emit sites (same files, the `error = SCREEN_LOAD_ERROR` lines).
- Each screen's early-return that reads `state.error` (e.g. `stats/StatsScreen.kt:27`).

- [ ] **Step 1: Add the resource + change the const to a `@StringRes` Int**

Add to `strings.xml`:
```xml
    <!-- i18n #34 phase 3: shared screen load-error (category C) -->
    <string name="screen_load_error">Couldn\'t load this screen. Check your connection and try again.</string>
```
In `ErrorState.kt`, replace the `const val` (`:56`) with a `@StringRes` Int constant (a plain top-level `val`, since `const` can't hold a resource id reference in all cases — use a top-level `val` or an object):
```kotlin
import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R

/** Shared "couldn't load this screen" resource id, emitted into UiState.error (i18n #34 phase 3). */
@StringRes
val SCREEN_LOAD_ERROR: Int = R.string.screen_load_error
```
Keep the name `SCREEN_LOAD_ERROR` so the 10 emit sites need no rename — only the *type* changes (`String` → `@StringRes Int`).

- [ ] **Step 2: Change each UiState `error` field type**

In all 10 UiStates: `val error: String? = null` → `val error: Int? = null`. (Add `import androidx.annotation.StringRes` and annotate `@StringRes val error: Int? = null` for clarity.) The 10 `.catch { emit(state(error = SCREEN_LOAD_ERROR)) }` lines compile unchanged (now emitting an Int). **Do NOT move the `.catch` — it MUST stay inside `flatMapLatest` (#194).**

- [ ] **Step 3: Update each screen's early-return to resolve**

Each screen has (e.g. StatsScreen.kt:27-30):
```kotlin
if (state.error != null) {
    ErrorState(state.error!!, onRetry = viewModel::retry)
    return
}
```
`ErrorState`'s first param is `message: String`. Resolve the id: change the call to `ErrorState(stringResource(state.error!!), onRetry = viewModel::retry)` (add `import androidx.compose.ui.res.stringResource` if absent). `state.error!!` is now an `Int` — `stringResource(Int)` is valid. The `!!` on the delegated property still works (type-agnostic).

Apply to all 10 screens: Stats, Store, UnclaimedSupplies, UltimateWeapon(weapons), Cards, CurrencyDashboard(economy), Home, Workshop, Labs, Missions. (Grep each screen for `state.error` / `ErrorState(` to find the exact call.)

- [ ] **Step 4: Update `StatsViewModelTest` #194 assertions**

`:224` `assertNotNull(state.error, …)` and `:235`/`:243` `assertNull(...)` are nullability checks that survive `String?`→`Int?` **unchanged** — no edit needed. Optionally ADD one id assertion to pin the value:
```kotlin
assertEquals(com.whitefang.stepsofbabylon.R.string.screen_load_error, state.error)
```
(Only if the plan wants to pin it — the existing tests already pass. Keep this optional add minimal.)

- [ ] **Step 5: Build + test + commit**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*StatsViewModelTest" > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
```
Both `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation \
  app/src/main/res/values/strings.xml app/src/test/java/com/whitefang/stepsofbabylon/presentation/stats
git commit -m "i18n(#34): SCREEN_LOAD_ERROR -> @StringRes across 10 VMs (phase 3 C)"
```

---

### Task B2: Activity literals (category F)

**Files:**
- `presentation/HealthConnectPermissionActivity.kt:30` (title) + `:31-52` (policy body).
- `presentation/MainActivity.kt:288-289` (snackbar msg + action label).
- `strings.xml`.

- [ ] **Step 1: Add resources**

```xml
    <!-- i18n #34 phase 3: Activity literals (category F) -->
    <string name="hc_privacy_policy_title">Privacy Policy</string>
    <string name="hc_privacy_policy_body">Steps of Babylon uses your device\'s step counter sensor to track daily walking activity. This data powers all in-game progression.\n\nHealth Connect Integration\nWith your permission, the app reads:\n• Step count records — to cross-validate sensor readings and recover missed steps\n• Exercise session records — to convert indoor workout minutes into step-equivalent credits (Activity Minute Parity)\n\nYou can revoke Health Connect permissions at any time through your device settings.\n\nData Storage\nYour step, Health Connect, and game data is stored locally on your device in an encrypted database. Steps of Babylon has no server backend, and we do not upload your step, health, or game data to any server, sell it, or share it with third parties.\n\nAdvertising\nThe app shows optional, opt-in reward ads via Google AdMob. To serve these, Google\'s ads SDK collects your device\'s advertising ID; this is collected by Google, not by us. A consent prompt governs ad personalisation. You can reset or limit your advertising ID in Settings → Google → Ads. Full privacy policy: https://jonwhitefang.github.io/steps-of-babylon/\n\nContact: jonwhitefang@gmail.com</string>
    <string name="step_permission_hint">Step counting is off — enable it in Settings</string>
    <string name="step_permission_action">Settings</string>
```
(The body is ONE resource with `\n` line breaks and `•`/`—`/`→` for the glyphs — copy the current triple-quoted text byte-for-byte, escaping apostrophes. Verify the rendered output matches by diffing on-device or in a Robolectric render.)

- [ ] **Step 2: Replace in HealthConnectPermissionActivity**

`:30` `Text("Privacy Policy", …)` → `Text(stringResource(R.string.hc_privacy_policy_title), …)`. Replace the triple-quoted `text = """…"""` with `text = stringResource(R.string.hc_privacy_policy_body)`. Add `import androidx.compose.ui.res.stringResource` + `import com.whitefang.stepsofbabylon.R`. (The Activity's `setContent {}` is `@Composable` — `stringResource` is valid.)

- [ ] **Step 3: Replace in MainActivity**

`:288-289` are inside a `LaunchedEffect` (a coroutine, NOT a `@Composable` scope), so mirror the sibling `crashNotice` pattern (`:276` resolves `stringResource` OUTSIDE the effect then captures it). Above the `LaunchedEffect(showStepPermissionSettingsHint)` block, add:
```kotlin
val stepHint = stringResource(R.string.step_permission_hint)
val stepAction = stringResource(R.string.step_permission_action)
```
then inside: `message = stepHint,` and `actionLabel = stepAction,`.

- [ ] **Step 4: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/HealthConnectPermissionActivity.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): extract Activity literals — HC policy body + MainActivity snackbar (phase 3 F)"
```

---

### Task B3: HelpScreen (category H)

**Files:**
- `presentation/help/HelpScreen.kt` (`HelpSection(title: String, content: () -> String)`; 9 sections, `:28-98`).
- `strings.xml`.

- [ ] **Step 1: Add resources — 9 titles + 9 bodies**

Add under `<!-- i18n #34 phase 3: Help screen (category H) -->`. Titles keep emoji; bodies are multi-line with `\n` and `•` bullets, byte-identical to the current text. (Reproduce each of the 9 sections verbatim — the ground-truth report lists them; escape apostrophes, keep `×`/`•`/`→` glyphs.) Key names: `help_currencies_title`/`help_currencies_body`, `help_workshop_*`, `help_battle_*`, `help_tiers_*`, `help_labs_*`, `help_cards_*`, `help_uw_*`, `help_encounters_*`, `help_fairplay_*`. Example (first section):
```xml
    <string name="help_currencies_title">💰 Currencies</string>
    <string name="help_currencies_body">• Steps — earned only by walking. Spent on Workshop upgrades and Labs research.\n• Cash — earned by killing enemies in battle. Resets each round. Spent on in-round upgrades.\n• Gems — earned from milestones, daily login streaks, and card packs. Spent on card packs and Lab rush.\n• Power Stones — earned from weekly challenges, wave milestones, and boss kills. Spent on Ultimate Weapon upgrades.\n• Card Copies — earned from packs and supply drops. Collect enough copies to level up a card.</string>
```
(Repeat for all 9 — copy each body from the current source verbatim.)

- [ ] **Step 2: Replace each `HelpSection("title") { "body" }` call**

Since `HelpSection(title: String, content: () -> String)`, resolve at the call site (HelpScreen is `@Composable`):
```kotlin
HelpSection(stringResource(R.string.help_currencies_title)) { stringResource(R.string.help_currencies_body) }
```
Wait — `content: () -> String` is a plain lambda, not `@Composable`, so `stringResource` cannot be called inside it. Two options: (a) change `content` to a pre-resolved `String` param and resolve at the call site, or (b) make `content` `@Composable`. **Choose (a)** (simpler, no recomposition concern): change the signature to `HelpSection(title: String, body: String)` and its internals `Text(content(), …)` → `Text(body, …)`; then each call becomes:
```kotlin
HelpSection(
    stringResource(R.string.help_currencies_title),
    stringResource(R.string.help_currencies_body),
)
```
Add `import androidx.compose.ui.res.stringResource` + `R`. Apply to all 9 sections.

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`. Methodology-grep HelpScreen.kt → zero English literals.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/help/HelpScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): externalize HelpScreen 9 sections (phase 3 H)"
```

---

### Task B4: WorkshopScreen EmptyState + straggler sweep (category Z)

**Files:**
- `presentation/workshop/WorkshopScreen.kt:104`.
- `strings.xml`.

- [ ] **Step 1: Add resource + replace**

```xml
    <string name="workshop_empty">No upgrades in this category yet.</string>
```
`WorkshopScreen.kt:104` `EmptyState(message = "No upgrades in this category yet.")` → `EmptyState(message = stringResource(R.string.workshop_empty))`.

- [ ] **Step 2: Straggler sweep**

Run the widened sweep and extract any survivor found (there should be none beyond the above + already-scoped items):
```bash
sg -l kotlin -p 'EmptyState($$$)' app/src/main/java/com/whitefang/stepsofbabylon/presentation
rg -n 'Text\("[A-Z]' app/src/main/java/com/whitefang/stepsofbabylon/presentation --glob '*.kt' | rg -v 'stringResource|Text\("\$'
rg -n 'placeholder =|message =' app/src/main/java/com/whitefang/stepsofbabylon/presentation --glob '*.kt' | rg '"[A-Z]'
```
Extract any user-facing English survivor (add a key + `stringResource`); exclude data-only `Text("$…")`, glyphs, and battle-Canvas (that's PR3d).

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): WorkshopScreen EmptyState + straggler sweep (phase 3 Z)"
```

---

### Task B5 (MERGE GATE): verify PR3b + open PR + merge + confirm merged

- [ ] **Step 1:** Full suite + detekt + ktlint (all `BUILD SUCCESSFUL`; JVM count unchanged from PR3a's baseline; 0 failures).
- [ ] **Step 2:** Methodology grep across all touched files → only data-only/glyph survivors.
- [ ] **Step 3:** Push + `gh pr create` (title `i18n(#34): error channel + Activities + Help + stragglers — PR3b (C+F+H+Z)`; body notes no behavior change).
- [ ] **Step 4 (GATE):** CI green → merge → `git checkout main && git pull && git log --oneline -3` to confirm the merge commit is on `main` before cutting PR3c.

---

# PR3c — nav labels + duration/plurals (categories B + D)

**Branch:** `i18n/34-pr3c-nav-duration` (cut from `main` **after PR3b merges**).
**Scope:** `Screen.kt` label → `@StringRes` (#161 fragile); duration/units/plurals incl. the non-Composable `pathValueAtNext` restructuring and the economy-pipeline change.

---

### Task C1: `Screen.label` → `@StringRes labelRes` (category B, FRAGILE #161)

**Files:**
- `presentation/navigation/Screen.kt` (ctor `:21-25`; 14 data objects `:26-56`).
- `presentation/navigation/BottomNavBar.kt:47-48` (only consumer).
- `strings.xml`.

- [ ] **Step 1: Add nav_* resources (14)**

```xml
    <!-- i18n #34 phase 3: bottom-nav labels (category B) -->
    <string name="nav_home">Home</string>
    <string name="nav_workshop">Workshop</string>
    <string name="nav_battle">Battle</string>
    <string name="nav_labs">Labs</string>
    <string name="nav_stats">Stats</string>
    <string name="nav_weapons">Weapons</string>
    <string name="nav_cards">Cards</string>
    <string name="nav_supplies">Supplies</string>
    <string name="nav_economy">Economy</string>
    <string name="nav_missions">Missions</string>
    <string name="nav_settings">Settings</string>
    <string name="nav_store">Store</string>
    <string name="nav_help">Help</string>
    <string name="nav_onboarding">Onboarding</string>
```

- [ ] **Step 2: Change the ctor param + 14 data objects**

`import androidx.annotation.StringRes` is already present (`:3`). Change the ctor:
```kotlin
sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
```
Change each of the 14 data objects, e.g. `data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)`. **Do NOT touch** the `by lazy` lists (`items`/`allScreens`/`argumentFreeRoutes`), `fromRoute`, `startDestination`, or `secondaryTitle` — only the `label`→`labelRes` field. (These reference the data objects, not `.label`, so they compile unchanged.)

- [ ] **Step 3: Update BottomNavBar (only consumer)**

`BottomNavBar.kt:47-48`:
```kotlin
icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
label = { Text(stringResource(screen.labelRes)) },
```
Add `import androidx.compose.ui.res.stringResource` + `R` if absent. (BottomNavBar is `@Composable`.)

- [ ] **Step 4: Build + run the nav guards**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*DeepLinkRoutingTest" --tests "*ScreenSecondaryTitleTest" > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
```
Both `BUILD SUCCESSFUL` (these tests don't read `.label`, so they must stay green — proving the `by lazy` lists are untouched).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): Screen.label -> @StringRes labelRes (phase 3 B, #161-safe)"
```

---

### Task C2: `pathValueAtNext` unit labels (category D — the tricky one)

**Files:**
- `presentation/weapons/UltimateWeaponScreen.kt` (`pathValueAtNext` `:290-323`, non-`@Composable`; caller `:220` inside a `@Composable`).
- `strings.xml`.

- [ ] **Step 1: Add unit resources**

The unit words are the translatable part (`s`, `DPS`, `dmg`, `enemies`, `px/s`, `screen`, `area`, `× dmg`). The numeric formatting (`%.0f`/`%.1f`) stays. **Spacing is byte-identical to the current source** (verified from ground truth): `"${v.toInt()}s"` is spaceless → `%1$ds`; `"${v.toInt()} DPS"` has a space → `%1$d DPS`; the CHRONO_FIELD SECONDARY branch is the rounded FLOAT `"%.0fs"` (spaceless, HALF_UP-rounded — NOT truncated), so it needs its own `%1$ss`-with-`fmt0` resource (review F4). Add:
```xml
    <!-- i18n #34 phase 3: UW path-value units (category D) — spacing byte-identical to source -->
    <string name="uw_value_seconds">%1$ds</string>          <!-- COOLDOWN: "${v.toInt()}s" (int, spaceless) -->
    <string name="uw_value_seconds_f">%1$ss</string>        <!-- CHRONO_FIELD SECONDARY: "%.0fs" (rounded float, spaceless) — review F4 -->
    <string name="uw_value_dps">%1$d DPS</string>
    <string name="uw_value_dmg">%1$d dmg</string>
    <string name="uw_value_enemies">%1$d enemies</string>
    <string name="uw_value_pxs">%1$d px/s</string>
    <string name="uw_value_percent">%1$s%%</string>
    <string name="uw_value_multiplier">%1$s×</string>
    <string name="uw_value_percent_screen">%1$s%% screen</string>
    <string name="uw_value_percent_area">%1$s%% area</string>
    <string name="uw_value_multiplier_dmg">%1$s× dmg</string>
```

- [ ] **Step 2: Lift `pathValueAtNext` into a `@Composable`**

Since the caller (`:220`, inside `@Composable UWPathRow`) is Composable, make `pathValueAtNext` `@Composable` so it can call `stringResource`:
```kotlin
@Composable
private fun pathValueAtNext(
    type: UltimateWeaponType,
    path: UWPath,
    currentLevel: Int,
): String {
    val next = (currentLevel + 1).coerceAtMost(UltimateWeaponType.MAX_PATH_LEVEL)
    val v = type.valueAtLevel(path, next)
    return when (path) {
        UWPath.COOLDOWN -> stringResource(R.string.uw_value_seconds, v.toInt())
        UWPath.DAMAGE ->
            when (type) {
                UltimateWeaponType.CHRONO_FIELD ->
                    stringResource(R.string.uw_value_percent, fmt0(v * 100))
                UltimateWeaponType.GOLDEN_ZIGGURAT ->
                    stringResource(R.string.uw_value_multiplier, fmt1(v))
                UltimateWeaponType.POISON_SWAMP ->
                    stringResource(R.string.uw_value_percent, fmt1(v * 100))
                UltimateWeaponType.BLACK_HOLE -> stringResource(R.string.uw_value_dps, v.toInt())
                else -> stringResource(R.string.uw_value_dmg, v.toInt())
            }
        UWPath.SECONDARY ->
            when (type) {
                UltimateWeaponType.CHAIN_LIGHTNING -> stringResource(R.string.uw_value_enemies, v.toInt())
                UltimateWeaponType.DEATH_WAVE -> stringResource(R.string.uw_value_percent_screen, fmt0(v * 100))
                UltimateWeaponType.BLACK_HOLE -> stringResource(R.string.uw_value_pxs, v.toInt())
                UltimateWeaponType.CHRONO_FIELD -> stringResource(R.string.uw_value_seconds_f, fmt0(v))  // rounded float, review F4
                UltimateWeaponType.POISON_SWAMP -> stringResource(R.string.uw_value_percent_area, fmt0(v * 100))
                UltimateWeaponType.GOLDEN_ZIGGURAT -> stringResource(R.string.uw_value_multiplier_dmg, fmt1(v))
            }
    }
}

// helpers keep the ROOT-locale numeric formatting (percent/multiplier/seconds_f take a pre-formatted %1$s):
private fun fmt0(x: Float) = String.format(java.util.Locale.ROOT, "%.0f", x)
private fun fmt1(x: Float) = String.format(java.util.Locale.ROOT, "%.1f", x)
```
The CHRONO_FIELD SECONDARY branch keeps the rounded-float path (`uw_value_seconds_f` + `fmt0(v)`) so `v=2.6` still renders `3s` (HALF_UP), NOT `2s` — byte-identical to the current `String.format("%.0fs", v)` (review F4). The COOLDOWN branch stays the truncating int path (`uw_value_seconds` + `v.toInt()`), matching the current `"${v.toInt()}s"`. The caller `:220` is unchanged (it already calls `pathValueAtNext(...)` in a Composable). Add `import androidx.compose.ui.res.stringResource`.

- [ ] **Step 3: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`. Visually diff a few UW path values on-device or against the current output to confirm byte-identical spacing.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): UW pathValueAtNext units via @StringRes (phase 3 D)"
```

---

### Task C3: Duration formatters + StatsScreen minutes + economy pipeline (category D)

**Files:**
- `labs/LabsScreen.kt` `formatTime` (`:263-277`, already takes `doneLabel` param).
- `supplies/UnclaimedSuppliesScreen.kt` `formatTimeAgo` (`:166-178`, already takes `justNowLabel`).
- `stats/StatsScreen.kt:92` (`"$minutes min"`).
- `economy/CurrencyDashboardViewModel.kt:128` + `economy/EconomyUiState.kt:14` + the private `SnapshotData` (`:42`).
- `plurals.xml` + `strings.xml`.

- [ ] **Step 1: `formatTime` unit strings → resources**

`formatTime` is non-`@Composable`, called at `LabsScreen.kt:191` (Composable). The cleanest byte-identical route: pass the three composed forms in, OR resolve the unit template at the call site. Given the compound form `"${h}h ${m}m ${s}s"`, add a single format-arg resource per branch:
```xml
    <string name="duration_hms">%1$dh %2$dm %3$ds</string>
    <string name="duration_ms">%1$dm %2$ds</string>
    <string name="duration_s">%1$ds</string>
```
Add two more `String` params to `formatTime` for the resolved templates is clumsy; instead make `formatTime` `@Composable` (LabsScreen call site is Composable):
```kotlin
@Composable
private fun formatTime(ms: Long, doneLabel: String): String {
    if (ms <= 0) return doneLabel
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hms, hours, minutes, seconds)
        minutes > 0 -> stringResource(R.string.duration_ms, minutes, seconds)
        else -> stringResource(R.string.duration_s, seconds)
    }
}
```
(`hours`/`minutes`/`seconds` are `Long` — cast to `Int` for `%d`, or use `%1$d` which accepts the value; verify the arg type. Use `.toInt()` to be safe with `stringResource`'s vararg `Any`.) The `:191` call is unchanged.

- [ ] **Step 2: `formatTimeAgo` → plurals**

The "ago" forms want plurals. Add:
```xml
    <plurals name="time_ago_minutes"><item quantity="one">%1$dm ago</item><item quantity="other">%1$dm ago</item></plurals>
    <plurals name="time_ago_hours"><item quantity="one">%1$dh ago</item><item quantity="other">%1$dh ago</item></plurals>
    <plurals name="time_ago_days"><item quantity="one">%1$dd ago</item><item quantity="other">%1$dd ago</item></plurals>
```
(English one/other identical to preserve output — the structure lets a locale differ.) Make `formatTimeAgo` `@Composable` (call site `:121` is Composable) and use `pluralStringResource`:
```kotlin
@Composable
private fun formatTimeAgo(timestampMs: Long, justNowLabel: String): String {
    val diff = System.currentTimeMillis() - timestampMs
    val minutes = (diff / 60_000).toInt()
    return when {
        minutes < 1 -> justNowLabel
        minutes < 60 -> pluralStringResource(R.plurals.time_ago_minutes, minutes, minutes)
        minutes < 1440 -> (minutes / 60).let { pluralStringResource(R.plurals.time_ago_hours, it, it) }
        else -> (minutes / 1440).let { pluralStringResource(R.plurals.time_ago_days, it, it) }
    }
}
```
Add `import androidx.compose.ui.res.pluralStringResource`.

- [ ] **Step 3: StatsScreen `"$minutes min"`**

Add `<string name="stats_activity_minutes">%1$d min</string>`. `StatsScreen.kt:92` is inside `@Composable` — `"$minutes min"` → `stringResource(R.string.stats_activity_minutes, minutes)`.

- [ ] **Step 4: Economy weekly-reset pipeline (raw ints in state)**

Add `<string name="economy_time_remaining">%1$dd %2$dh</string>`. Change the pipeline to carry raw ints:
- `SnapshotData` (`:42`): `val weeklyTimeRemaining: String = ""` → `val weeklyResetDays: Int = 0, val weeklyResetHours: Int = 0`.
- `EconomyUiState.kt:14`: `val weeklyTimeRemaining: String = ""` → `val weeklyResetDays: Int = 0, val weeklyResetHours: Int = 0`.
- `CurrencyDashboardViewModel.kt:128`: replace `val timeRemaining = "${days}d ${hours}h"` — instead assign `days`/`hours` (both `Long` → `.toInt()`) into the snapshot fields; the combine at `:71` maps them into `EconomyUiState` (`weeklyResetDays`/`weeklyResetHours`).
- In `CurrencyDashboardScreen.kt`, wherever `weeklyTimeRemaining` was shown, resolve: `stringResource(R.string.economy_time_remaining, state.weeklyResetDays, state.weeklyResetHours)`. (Grep for `weeklyTimeRemaining` render site.)
- **REQUIRED (not conditional — review CG-04/TS-06):** `CurrencyDashboardViewModelTest.kt` has a test `V1X16 - weeklyTimeRemaining is populated and formatted` that reads `vm.uiState.value.weeklyTimeRemaining` (~:141) and asserts `.isNotBlank()` (~:142). Renaming the field DELETES what it reads → compile failure at the C4 gate. Update that test to assert the two int fields instead (e.g. `assertTrue(state.weeklyResetDays >= 0)` + `assertTrue(state.weeklyResetHours in 0..23)`), and add `.../economy` to the C3 `git add` list (already tentatively included). Verify the test name/lines by symbol.

- [ ] **Step 5: Build + test + commit**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*CurrencyDashboardViewModelTest" > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
```
Both `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/{labs,supplies,stats,economy} \
  app/src/main/res/values/strings.xml app/src/main/res/values/plurals.xml \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/economy 2>/dev/null
git commit -m "i18n(#34): duration/plurals + economy reset pipeline to raw ints (phase 3 D)"
```

---

### Task C4 (MERGE GATE): verify PR3c + open PR + merge + confirm merged

- [ ] **Step 1:** Full suite + detekt + ktlint (all green; count unchanged; 0 failures).
- [ ] **Step 2:** Methodology grep on touched files. On-device spot-check of the bottom nav + a UW path row + Labs timer + supplies "ago" (visual byte-identity).
- [ ] **Step 3:** Push + `gh pr create` (title `i18n(#34): nav labels + duration/plurals — PR3c (B+D)`; body flags #161 fragile zone + DeepLinkRoutingTest green).
- [ ] **Step 4 (GATE):** CI green → merge → confirm merge commit on `main` before cutting PR3d.

---

# PR3d — battle Canvas text (category E)

**Branch:** `i18n/34-pr3d-battle-canvas` (cut from `main` **after PR3c merges**).
**Scope:** extend `domain/Strings` + `AndroidStrings` + `FakeStrings`; `GameEngine` pre-resolves labels and passes them into `WaveAnnouncement`/`WaveCooldownText` constructors; keep the null-`Strings` fallback so `GameEngineTest`/`SimulationTest` stay pure-JVM. FRAGILE battle zone.

---

### Task D1: Extend the `Strings` seam

**Files:**
- `domain/Strings.kt` (interface) · `data/AndroidStrings.kt` (impl) · `test/fakes/FakeStrings.kt` · `strings.xml`.

- [ ] **Step 1: Add resources**

```xml
    <!-- i18n #34 phase 3: battle Canvas text (category E) -->
    <string name="fx_boss_incoming">⚠ BOSS INCOMING</string>
    <string name="fx_wave_header">Wave %1$d</string>
    <string name="fx_next_wave">Next Wave: %1$ds</string>
```
(`⚠` = ⚠. `fx_wave_header` uses `%1$d` for the wave number. `fx_next_wave` = `Next Wave: %1$ds` — no space before `s`, matching `"Next Wave: ${t.toInt()}s"`.)

- [ ] **Step 2: Add 3 methods to `domain/Strings.kt`**

```kotlin
    /** Boss-wave banner, e.g. "⚠ BOSS INCOMING". */
    fun bossIncoming(): String

    /** Wave-announcement header, e.g. "Wave 7". */
    fun waveHeader(wave: Int): String

    /** Cooldown countdown line, e.g. "Next Wave: 5s". */
    fun nextWaveIn(seconds: Int): String
```

- [ ] **Step 3: Implement in `AndroidStrings.kt`**

```kotlin
    override fun bossIncoming(): String = context.getString(R.string.fx_boss_incoming)

    override fun waveHeader(wave: Int): String = context.getString(R.string.fx_wave_header, wave)

    override fun nextWaveIn(seconds: Int): String = context.getString(R.string.fx_next_wave, seconds)
```

- [ ] **Step 4: Implement in `test/fakes/FakeStrings.kt`**

```kotlin
    override fun bossIncoming() = "FAKE_BOSS_INCOMING"
    override fun waveHeader(wave: Int) = "FAKE_WAVE_$wave"
    override fun nextWaveIn(seconds: Int) = "FAKE_NEXT_$seconds"
```

- [ ] **Step 5: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/Strings.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/data/AndroidStrings.kt \
  app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeStrings.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): extend Strings seam with bossIncoming/waveHeader/nextWaveIn (phase 3 E)"
```

---

### Task D2: Thread resolved labels into the effect constructors

**Files:**
- `presentation/battle/effects/WaveAnnouncement.kt` (holds BOTH `WaveAnnouncement` `:6-56` and `WaveCooldownText` `:59-89`).
- `presentation/battle/engine/GameEngine.kt` (`:99` `strings`; construction `:552` + `:561`; `nextWaveCompositionLabel`/`bossCountdownLabel` precedent `:577-597`).

- [ ] **Step 1: `WaveAnnouncement` — accept pre-resolved labels**

Add two nullable label params (nullable so the engine's null-Strings fallback can pass the literal, and to keep a default for any test constructing it directly). Change ctor:
```kotlin
class WaveAnnouncement(
    private val wave: Int,
    private val isBossWave: Boolean,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val reducedMotion: Boolean = false,
    private val bossLabel: String = "⚠ BOSS INCOMING",
    private val waveLabel: String = "Wave $wave",
) : Effect {
```
In `render()`, replace the two hardcoded `drawText`:
```kotlin
if (isBossWave) {
    bossTextPaint.alpha = alpha
    canvas.drawText(bossLabel, screenWidth / 2f, baseY - 50f, bossTextPaint)
}
canvas.drawText(waveLabel, screenWidth / 2f, baseY, textPaint)
```
(Defaults keep the exact current literals for any direct constructor / test that omits them.)

- [ ] **Step 2: `WaveCooldownText` — per-frame countdown needs a formatter**

The countdown `t` is computed per-frame in `render()`, so it can't be pre-resolved once. Pass a `(Int) -> String` formatter (nullable; null → the literal fallback). Change ctor:
```kotlin
class WaveCooldownText(
    private val screenWidth: Float,
    private val nextWaveComposition: String? = null,
    private val nextWaveLabeler: ((Int) -> String)? = null,
    private val getTimeRemaining: () -> Float,
) : Effect {
```
In `render()`:
```kotlin
val t = getTimeRemaining()
if (t > 0f) {
    val label = nextWaveLabeler?.invoke(t.toInt()) ?: "Next Wave: ${t.toInt()}s"
    canvas.drawText(label, screenWidth / 2f, 60f, paint)
    nextWaveComposition?.let { canvas.drawText(it, screenWidth / 2f, 90f, compositionPaint) }
}
```
(Kotlin trailing-lambda note: `getTimeRemaining` stays the LAST param so the existing trailing-lambda construction `WaveCooldownText(screenWidth, label) { … }` still binds it. `nextWaveLabeler` goes before it.)

- [ ] **Step 3: `GameEngine` — resolve + pass**

At the `WaveAnnouncement` construction (`:552`):
```kotlin
val bossLabel = strings?.bossIncoming() ?: "⚠ BOSS INCOMING"
val waveLabel = strings?.waveHeader(wave) ?: "Wave $wave"
fx.addEffect(
    WaveAnnouncement(wave, isBoss, screenWidth, screenHeight, reducedMotion, bossLabel, waveLabel),
)
```
At the `WaveCooldownText` construction (`:561`), add the labeler (capturing `strings`):
```kotlin
val labeler: (Int) -> String = { secs -> strings?.nextWaveIn(secs) ?: "Next Wave: ${secs}s" }
val ct =
    WaveCooldownText(screenWidth, nextWaveCompositionLabel(), labeler) {
        if (spawner.phase == WavePhase.COOLDOWN) {
            WaveSpawner.COOLDOWN_DURATION - (spawner.phaseTimer)
        } else {
            0f
        }
    }
```
(The `⚠` glyph in Kotlin source is fine as the literal `⚠`; keep it byte-identical to the current source — copy the existing `"⚠ BOSS INCOMING"` verbatim.)

- [ ] **Step 4: Build + run engine tests (purity gate)**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*GameEngineTest" --tests "*SimulationTest" > /tmp/t.log 2>&1 && tail -n 15 /tmp/t.log
```
Both `BUILD SUCCESSFUL` — the null-`Strings` fallback path must keep these pure-JVM tests green (they construct GameEngine with `strings == null`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle
git commit -m "i18n(#34): thread resolved wave labels into effect ctors via engine (phase 3 E)"
```

---

### Task D3 (MERGE GATE): verify PR3d + open PR + merge + confirm merged

- [ ] **Step 1:** Full suite + detekt + ktlint (green; count unchanged; 0 failures).
- [ ] **Step 2:** Methodology grep on the battle effect + engine files (only data-only `"$pct%"` glyph survivors). On-device spot-check: enter a battle, trigger a boss wave + wave cooldown, confirm "⚠ BOSS INCOMING" / "Wave N" / "Next Wave: Ns" render identically.
- [ ] **Step 3:** Push + `gh pr create` (title `i18n(#34): battle Canvas text — PR3d (E)`; body notes fragile battle zone + engine-test purity preserved via null-Strings fallback).
- [ ] **Step 4 (GATE):** CI green (incl. instrumented lane — battle surface) → merge → confirm merge commit on `main` before cutting PR3e.

---

# PR3e — domain-model display strings (category G)

**Branch:** `i18n/34-pr3e-domain-strings` (cut from `main` **after PR3d merges**).
**Scope:** enum→`@StringRes` resolvers at the **presentation boundary** for UpgradeType/ResearchType/DailyMissionType/UltimateWeaponType descriptions, Milestone.displayName; a data-layer resource lookup for Cosmetic name/description; and the CardType effect-description numeric-split (the hard sub-item). Domain stays Android-free (`DomainPurityTest`). This PR may split into several commits; keep it one branch/PR unless it grows unwieldy.

**Precedent:** `presentation/ui/EnumLabels.kt` already houses `@StringRes` extension fns on enums (`UpgradeCategory.labelRes()` etc., #260) — put the new resolvers there. Pattern mirror: `CurrencyType.label()` (`@StringRes fun` resolved via `stringResource` at the call site).

---

### Task E1: Upgrade / Research / Mission / UW description resolvers

**Files:**
- `presentation/ui/EnumLabels.kt` (add resolver fns) · `strings.xml`.
- Render sites: `workshop/UpgradeCard.kt` + `battle/ui/InRoundUpgradeMenu.kt:143`; `labs/LabsScreen.kt:168`; `missions/MissionsScreen.kt:125`; `weapons/UltimateWeaponScreen.kt:134`.

- [ ] **Step 1: Add resources (one per enum value)**

Add four families, each value copied verbatim from the enum (see ground-truth report for exact text). Keys: `upgrade_desc_<name>` (24), `research_desc_<name>` (12), `mission_desc_<name>` (6), `uw_desc_<name>` (6). Example (fragments — reproduce ALL values):
```xml
    <!-- i18n #34 phase 3: domain-model descriptions (category G) -->
    <string name="upgrade_desc_damage">+2% base damage per level</string>
    <string name="upgrade_desc_rapid_fire">Periodic attack-speed burst (60s/5s/2.0× → permanent/3.0× at max)</string>
    <!-- … all 24 upgrade_desc_* … -->
    <string name="research_desc_enemy_intel">Tactical awareness. +2% damage per level. Reveals next wave at L1, enemy HP at L5, boss timing at L10.</string>
    <!-- … all 12 research_desc_* … -->
    <string name="mission_desc_walk_5000">Walk 5,000 steps</string>
    <!-- … all 6 mission_desc_* … -->
    <string name="uw_desc_death_wave">Massive damage pulse radiating outward, damages enemies in radius</string>
    <!-- … all 6 uw_desc_* … -->
```

- [ ] **Step 2: Add `@StringRes` resolver fns in `EnumLabels.kt`**

```kotlin
@StringRes
fun UpgradeType.descriptionRes(): Int = when (this) {
    UpgradeType.DAMAGE -> R.string.upgrade_desc_damage
    // … all 24 …
}

@StringRes
fun ResearchType.descriptionRes(): Int = when (this) { /* all 12 */ }

@StringRes
fun DailyMissionType.descriptionRes(): Int = when (this) { /* all 6 */ }

@StringRes
fun UltimateWeaponType.descriptionRes(): Int = when (this) { /* all 6 */ }
```
(Exhaustive `when` over enum entries — the compiler enforces completeness, guarding against a missed value.)

- [ ] **Step 3: Update the render sites**

- `workshop/UpgradeCard.kt` + `InRoundUpgradeMenu.kt:143`: today `stringResource(R.string.inround_level_desc, level, type.config.description)`. The template embeds the description as `%2$s`. Change to resolve the description first: `stringResource(R.string.inround_level_desc, level, stringResource(type.descriptionRes()))`. (Nested `stringResource` is fine in Compose.)
- `labs/LabsScreen.kt:168` `Text(info.type.description, …)` → `Text(stringResource(info.type.descriptionRes()), …)`.
- **`missions/MissionsScreen.kt:125` — NEEDS DTO SURGERY (review CG-02/F1/SCOPE-2):** the render receiver `mission` is a **`MissionDisplayInfo`** UiState DTO (`MissionsUiState.kt:5-14`) that stores a pre-computed **`description: String`** — it does NOT carry the `DailyMissionType`, and `MissionsViewModel.kt:115` pre-resolves `m.type.description` into that String OFF the Composable path. So `mission.descriptionRes()` cannot compile. Do the surgery: (1) add `val type: DailyMissionType` to `MissionDisplayInfo` and DROP the `description: String` field; (2) in `MissionsViewModel.kt:113-122` pass `m.type` (the `DailyMission` domain model exposes `.type: DailyMissionType`) instead of `m.type.description`; (3) render `Text(stringResource(mission.type.descriptionRes()), …)` at `:125`; (4) reconcile any `MissionDisplayInfo`-shape test. (`MilestoneDisplayInfo` already carries `milestone: Milestone`, so E2 needs no such surgery.)
- `weapons/UltimateWeaponScreen.kt:134` `Text(info.type.description, …)` → `Text(stringResource(info.type.descriptionRes()), …)`. (`info.type` IS a `UltimateWeaponType` enum — confirmed; no surgery.)
- `labs/LabsScreen.kt:168` `Text(info.type.description, …)` → `Text(stringResource(info.type.descriptionRes()), …)`. (`info.type` IS a `ResearchType` enum — confirmed; no surgery.)

**Do NOT delete the `description` fields from the domain enums** — leaving them avoids churning domain + its tests, and `DomainPurityTest` stays green (no Android in domain). The UI simply stops reading them. (Optionally the plan could remove the now-unused fields in a later cleanup, but that risks touching balance/other consumers — leave them.)

- [ ] **Step 4: Build + commit**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log` → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/{workshop,labs,missions,weapons} \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/InRoundUpgradeMenu.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): Upgrade/Research/Mission/UW description resolvers (phase 3 G)"
```

---

### Task E2: Milestone.displayName resolver

**Files:**
- `presentation/ui/EnumLabels.kt` · `missions/MissionsScreen.kt:210` · `strings.xml`.

- [ ] **Step 1: Resources + resolver**

```xml
    <string name="milestone_name_first_steps">First Steps</string>
    <string name="milestone_name_morning_jogger">Morning Jogger</string>
    <string name="milestone_name_trail_blazer">Trail Blazer</string>
    <string name="milestone_name_marathon_walker">Marathon Walker</string>
    <string name="milestone_name_iron_soles">Iron Soles</string>
    <string name="milestone_name_globe_trotter">Globe Trotter</string>
```
```kotlin
@StringRes
fun Milestone.displayNameRes(): Int = when (this) {
    Milestone.FIRST_STEPS -> R.string.milestone_name_first_steps
    Milestone.MORNING_JOGGER -> R.string.milestone_name_morning_jogger
    Milestone.TRAIL_BLAZER -> R.string.milestone_name_trail_blazer
    Milestone.MARATHON_WALKER -> R.string.milestone_name_marathon_walker
    Milestone.IRON_SOLES -> R.string.milestone_name_iron_soles
    Milestone.GLOBE_TROTTER -> R.string.milestone_name_globe_trotter
}
```
`MissionsScreen.kt:210` `Text(milestone.displayName, …)` → `Text(stringResource(milestone.displayNameRes()), …)`. (Confirm the receiver is `Milestone` — if the screen holds a domain wrapper, resolve off the enum inside it.)

- [ ] **Step 2: Build + commit**

Run assembleDebug → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): Milestone.displayName resolver (phase 3 G)"
```

---

### Task E3: Cosmetic name/description (data-layer seed)

**Files:**
- `data/repository/CosmeticRepositoryImpl.kt` (`SEED_COSMETICS` `:165-232`, 7 rows with `name`/`description`) · render `store/StoreScreen.kt:313,315` · `strings.xml`.

- [ ] **Step 1: Decide the mechanism (resolver in presentation)**

The seed is data-layer (`CosmeticEntity` rows persisted to Room). The stored `name`/`description` are English strings in the DB. Localizing the *stored* values would need a migration and would still be frozen at seed time — WRONG. Instead, treat the cosmetic id as the stable key and resolve name/description at the **presentation** render site by id:
```xml
    <string name="cosmetic_name_zig_jade">Jade Ziggurat</string>
    <string name="cosmetic_desc_zig_jade">Deep jade stone with pale highlights</string>
    <!-- … all 7 cosmetics: zig_jade, lapis_lazuli_skin, garden_ziggurat_skin,
         sandals_of_gilgamesh, zig_obsidian, zig_crystal, zig_golden … -->
```
Add a resolver in `presentation/ui/EnumLabels.kt` (or a new `CosmeticLabels.kt`) keyed by the cosmetic id String:
```kotlin
@StringRes
fun cosmeticNameRes(id: String): Int = when (id) {
    "zig_jade" -> R.string.cosmetic_name_zig_jade
    // … all 7 …
    else -> 0
}
@StringRes
fun cosmeticDescRes(id: String): Int = when (id) { /* … */ else -> 0 }
```
At `StoreScreen.kt:313/315`, resolve (the DTO field is **`cosmeticId`**, NOT `id` — review CG-06/SCOPE-4; `CosmeticDisplayInfo.cosmeticId` at `StoreUiState.kt:28`): `Text(if (cosmeticNameRes(cosmetic.cosmeticId) != 0) stringResource(cosmeticNameRes(cosmetic.cosmeticId)) else cosmetic.name, …)` (falls back to the stored name for an unknown/future id — resilient, mirrors the `CosmeticRepositoryImpl.toDomainOrNull` defensive pattern #221). Same for description with `cosmeticDescRes(cosmetic.cosmeticId)` / `cosmetic.description`. The `when(id)` keys are the 7 seed ids: `zig_jade`, `lapis_lazuli_skin`, `garden_ziggurat_skin`, `sandals_of_gilgamesh`, `zig_obsidian`, `zig_crystal`, `zig_golden` (verify against `CosmeticRepositoryImpl` SEED_COSMETICS).

**Leave the seed `name`/`description` in `CosmeticRepositoryImpl` as-is** (the DB fallback + no migration).

- [ ] **Step 2: Build + commit**

Run assembleDebug → `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): cosmetic name/desc resolved by id at Store render (phase 3 G)"
```

---

### Task E4: CardType effect descriptions (the numeric-split — hardest sub-item)

**Files:**
- `domain/model/CardType.kt` — **already exposes public `effectAtLevel(level)` (`:36`) + `secondaryAtLevel(level)` (`:42`)** (the numeric lerp); the string builder is `effectDescriptionAtLevel(level)` (`:68-91`). (Review SCOPE-3: do NOT add new `effectValueAtLevel`/`secondaryValueAtLevel` — reuse the existing accessors.)
- `presentation/ui/EnumLabels.kt` (new `@Composable` resolver) · `cards/CardsUiState.kt:16` (`CardDisplayInfo.effectDescription: String`) · `cards/CardsViewModel.kt:80` (pre-computes it) · `cards/CardsScreen.kt:271` (renders it) · `strings.xml`.
- `domain/model/CardTypeTest.kt` (**~27–31 string-pinning `assertEquals` on `effectDescriptionAtLevel`** — review CG-07 corrected the "~54" figure; count by symbol).

**Problem (two parts):** (1) `effectDescriptionAtLevel(level)` interpolates a numeric value (from `effectAtLevel`/`secondaryAtLevel`) into a unit-bearing English string — the *template* (label + unit) must localize while the number stays domain-computed. (2) **DTO surgery (review CG-01/SCOPE-3):** the string is pre-computed in the ViewModel (`CardsViewModel.kt:80` `effectDescription = card.type.effectDescriptionAtLevel(card.level)`) and stored as a plain `String` on `CardDisplayInfo` (`CardsUiState.kt:16`); `CardsScreen.kt:271` only reads `card.effectDescription`. A `@Composable`/`stringResource` resolver CANNOT be called from the non-Composable VM — so the DTO must carry the raw inputs and resolution must move to the Composable.

- [ ] **Step 1: DTO surgery — carry `type` + `level`, drop the pre-computed String**

- `CardsUiState.kt:16`: drop `val effectDescription: String`; add `val type: CardType` and `val level: Int` to `CardDisplayInfo` (if not already present — grep; `card.type`/`card.level` are used at `:80`, so the VM has them).
- `CardsViewModel.kt:80`: stop computing `effectDescription = card.type.effectDescriptionAtLevel(card.level)`; instead pass `type = card.type, level = card.level` into `CardDisplayInfo`.

- [ ] **Step 2: Add per-card format-arg resources (byte-identical to today's output)**

One template per card. Verify each against the CURRENT `effectDescriptionAtLevel` output (ground truth lists `effectLv1`/`effectLv7`; the live string interpolates the lerped value). Examples:
```xml
    <string name="card_effect_iron_skin">+%1$d Defense Absolute</string>
    <string name="card_effect_sharp_shooter">+%1$d%% Critical Chance</string>
    <string name="card_effect_walking_fortress">+%1$d%% Health, -%2$d%% Attack Speed</string>
    <string name="card_effect_step_surge">Earn %1$sx Gems this round</string>
    <!-- … all 9 … -->
```
(Match exact unit formatting: IRON_SKIN flat int, SHARP_SHOOTER percent, WALKING_FORTRESS/GLASS_CANNON two args, STEP_SURGE uses `formatMultiplier` pre-formatted to `%1$s`. Reproduce the exact rounding each card uses today — `.toInt()` vs a formatter — per `effectDescriptionAtLevel`'s current code.)

- [ ] **Step 3: `@Composable` resolver reusing the existing accessors**

```kotlin
@Composable
fun CardType.effectDescription(level: Int): String {
    val v = effectAtLevel(level)          // EXISTING public accessor (CardType.kt:36)
    val s = secondaryAtLevel(level)       // EXISTING (CardType.kt:42), null for non-debuff cards
    return when (this) {
        CardType.IRON_SKIN -> stringResource(R.string.card_effect_iron_skin, v.toInt())
        CardType.SHARP_SHOOTER -> stringResource(R.string.card_effect_sharp_shooter, v.toInt())
        CardType.WALKING_FORTRESS -> stringResource(R.string.card_effect_walking_fortress, v.toInt(), (s ?: 0.0).toInt())
        CardType.STEP_SURGE -> stringResource(R.string.card_effect_step_surge, formatMultiplier(v))
        // … all 9 …
    }
}
```
(Confirm the exact signatures/return types of `effectAtLevel`/`secondaryAtLevel` by opening `CardType.kt:36,42` — match arg types to `stringResource`.) Render at `CardsScreen.kt:271`: `Text(card.type.effectDescription(card.level), …)` (both fields now on the DTO).

- [ ] **Step 4: Reconcile `CardTypeTest` (~27–31 assertions — count corrected per CG-07)**

`CardTypeTest` asserts `effectDescriptionAtLevel(n)` returns exact English (Lv1/Lv4/Lv7 triads across 9 cards + a couple of `effectLv1`/`effectLv7` self-checks). Keep a single source of truth: **delete `effectDescriptionAtLevel` from the domain** (its only caller was the VM, now removed) and move its string-pinning assertions to a new **Robolectric-lane** `CardEffectDescriptionTest` that asserts `type.effectDescription(level)` resolves to the SAME English (default locale). The numeric lerp stays covered by whatever pins `effectAtLevel`/`secondaryAtLevel` today (verify a value test exists at L1/L4/L7; add one if `effectDescriptionAtLevel`'s deletion removes the only value coverage). If deleting `effectDescriptionAtLevel` risks other callers, grep first (`sg -l kotlin -p '$X.effectDescriptionAtLevel($$$)'`) — the review found only the VM.

- [ ] **Step 5: Build + test + commit**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*CardType*" --tests "*CardEffect*" --tests "*CardsViewModelTest" > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log
```
Both `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CardType.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/presentation \
  app/src/test/java/com/whitefang/stepsofbabylon \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): CardType effect descriptions via DTO type+level + @StringRes resolver (phase 3 G)"
```

---

### Task E5 (MERGE GATE): verify PR3e + open PR + merge + confirm merged

- [ ] **Step 1:** Full suite + detekt + ktlint + **`DomainPurityTest`** (must stay green — no Android/data import entered domain). Confirm count (only shifted CardType assertions; net delta ~0 or small).
- [ ] **Step 2:** Methodology grep across all category-G render sites. On-device spot-check: Workshop upgrade card, a Lab card, a mission, a UW card, a milestone name, a Store cosmetic, a card effect line.
- [ ] **Step 3:** Push + `gh pr create` (title `i18n(#34): domain-model display strings — PR3e (G)`; body notes domain stays pure, CardType numeric-split, ~54 assertions moved to a presentation test).
- [ ] **Step 4 (GATE):** CI green → merge → confirm merge commit on `main` before cutting PR3f.

---

# PR3f — onboarding carousel content (category I)

**Branch:** `i18n/34-pr3f-onboarding` (cut from `main` **after PR3e merges**).
**Scope:** the 4 slide titles + bodies in `OnboardingContent.slides`, keeping `OnboardingSlide` Android-free (JVM-testable), resolved at `OnboardingScreen`. Reconcile `OnboardingContentTest` + `OnboardingScreenTest`.

---

### Task F1: Move slide content to `@StringRes`, resolve at the screen

**Files:**
- `presentation/onboarding/OnboardingSlide.kt` (`OnboardingSlide.title/body: String`; `OnboardingContent.slides` 4 slides).
- `presentation/onboarding/OnboardingScreen.kt:184,190` (render).
- `presentation/onboarding/OnboardingContentTest.kt:28-29` · `OnboardingScreenTest.kt:71`.
- `strings.xml`.

- [ ] **Step 1: Add resources (4 titles + 4 bodies)**

```xml
    <!-- i18n #34 phase 3: onboarding carousel content (category I) -->
    <string name="onboarding_slide1_title">Walk to power your ziggurat</string>
    <string name="onboarding_slide1_body">Every real step you take earns Steps — the permanent currency that fuels everything. Steps are earned only by walking.</string>
    <string name="onboarding_slide2_title">Spend Steps in the Workshop</string>
    <string name="onboarding_slide2_body">Permanent upgrades make your tower stronger across three categories: Attack, Defense, and Utility.</string>
    <string name="onboarding_slide3_title">Send it into battle</string>
    <string name="onboarding_slide3_body">Your ziggurat auto-battles waves of enemies. Survive, climb tiers, and unlock new biomes.</string>
    <string name="onboarding_slide4_title">Enable step counting</string>
    <string name="onboarding_slide4_body">To turn your real-world steps into Steps, we need activity-recognition permission. Notifications are optional. Then go for a walk to earn your first Steps!</string>
```
(Each body reproduces the current concatenated text verbatim — mind the space at the concatenation seams, `—` for the em-dash in slide 1.)

- [ ] **Step 2: Change the slide model to carry `@StringRes` ids**

`OnboardingSlide` must stay Android-free (JVM-testable — `OnboardingContentTest` runs on the JVM). `@StringRes Int` is a plain `Int` — no Android runtime import (the annotation is compile-only). Change:
```kotlin
data class OnboardingSlide(
    val icon: String,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val isPermissionPrimer: Boolean = false,
    val biome: Biome? = null,
    val art: OnboardingArt? = null,
)
```
Add `import androidx.annotation.StringRes` (compile-only, allowed — it does not violate JVM-testability; confirm `OnboardingContentTest` still compiles/runs on the JVM lane. If the pure-JVM lane can't see `androidx.annotation`, fall back to keeping the model String-free by using plain `Int` without the annotation.) Update the 4 `OnboardingSlide(...)` entries to `titleRes = R.string.onboarding_slide1_title, bodyRes = R.string.onboarding_slide1_body`, etc. Add `import com.whitefang.stepsofbabylon.R`.

- [ ] **Step 3: Resolve at `OnboardingScreen`**

`:184` `Text(slide.title, …)` → `Text(stringResource(slide.titleRes), …)`; `:190` `Text(slide.body, …)` → `Text(stringResource(slide.bodyRes), …)`. Add `import androidx.compose.ui.res.stringResource`.

- [ ] **Step 4: Reconcile the tests**

- `OnboardingContentTest.kt:28-29` assert `slide.title.isNotBlank()` / `slide.body.isNotBlank()` — now `titleRes`/`bodyRes` are `Int`s. Change to assert the ids are non-zero:
```kotlin
assertTrue("slide title res must be set", slide.titleRes != 0)
assertTrue("slide body res must be set", slide.bodyRes != 0)
```
The biome/art/permission-primer assertions are unchanged.
- `OnboardingScreenTest.kt:71` `onNodeWithText(slides.first().title).assertIsDisplayed()` — resolve the id through context:
```kotlin
val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
composeRule.onNodeWithText(ctx.getString(slides.first().titleRes)).assertIsDisplayed()
```
(Confirm `OnboardingViewModelTest` / any other reader of `.title`/`.body` — grep — and reconcile.)

- [ ] **Step 5: Build + test + commit**

```bash
./run-gradle.sh :app:assembleDebug > /tmp/b.log 2>&1 && tail -n 20 /tmp/b.log
./run-gradle.sh :app:testDebugUnitTest --tests "*OnboardingContentTest" --tests "*OnboardingScreenTest" --tests "*OnboardingViewModelTest" > /tmp/t.log 2>&1 && tail -n 20 /tmp/t.log
```
Both `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding \
  app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding \
  app/src/main/res/values/strings.xml
git commit -m "i18n(#34): onboarding carousel content via @StringRes (phase 3 I)"
```

---

### Task F2 (MERGE GATE): verify PR3f + full-phase completeness sweep + open PR + merge

- [ ] **Step 1:** Full suite + detekt + ktlint (green; 0 failures; record final count).
- [ ] **Step 2: Full-phase completeness sweep** (the gate for the WHOLE effort). Run the widened repo-wide grep across `presentation/`, `service/`, `navigation/`, `battle/`, the two Activities, and `domain/model/`:
```bash
rg -nE '"[A-Z][a-z]|"[a-z]+ [a-z]' app/src/main/java/com/whitefang/stepsofbabylon \
  --glob '*.kt' | rg -vE '//|^\s*\*|import |Log\.|R\.string|stringResource|pluralStringResource|CHANNEL|_ID|navigate_to|route =|"[A-Z]"$'
```
Expected survivors: only data-only interpolations, glyphs, technical identifiers, the documented residuals (`SupplyDropTrigger.message`, `BillingProduct.priceDisplay`), and the domain enum `description`/`displayName` fields left in place (now unread by the UI). Extract any genuine user-facing English survivor before proceeding.
- [ ] **Step 3:** On-device spot-check: first-launch onboarding carousel renders identically.
- [ ] **Step 4:** Push + `gh pr create` (title `i18n(#34): onboarding carousel content — PR3f (I) + phase-3 completeness`; body notes the full-phase sweep result + documented residuals).
- [ ] **Step 5 (GATE):** CI green → merge → confirm merge commit on `main`.

---

## Final task: Doc sync + checkpoint (after ALL 6 PRs merge — PR Task-List Convention)

- [ ] **Step 1: Sync current-state docs**
  - `docs/agent/DECISIONS/ADR-0014…` — append: phase 3 (locale-readiness) shipped across PR3a–f; note the `UiMessage` type, the `RewardAdManagerImpl` Context injection, the category-G enum-resolver pattern (`EnumLabels.kt`), and the documented residuals (`SupplyDropTrigger.message`, `BillingProduct.priceDisplay`). Record that the app is now locale-ready (a `values-xx` file fully translates the UI).
  - `CHANGELOG.md` — `[Unreleased]`: "i18n phase 3 — full locale-readiness string extraction (#34, PR3a–f): VM UiMessage type, billing/ad error localization, nav labels, duration plurals, battle Canvas, domain-model display copy, onboarding content. No behavior change; JVM count → <final>."
  - `CLAUDE.md` — update the headline test count if it changed; add a one-line note on the `UiMessage` pattern + `EnumLabels` resolver under Conventions if warranted.
  - `docs/steering/source-files.md` — add `presentation/ui/UiMessage.kt`; note `EnumLabels.kt` gained the category-G resolvers; note `Screen.label`→`labelRes`; note `SCREEN_LOAD_ERROR` type change.
  - `docs/agent/STATE.md` fragile-zone list — note the `UiMessage` contract (Raw only for already-localized lower-layer text) + the `error: Int?` channel.
- [ ] **Step 2: Run `/checkpoint`** to update `docs/agent/STATE.md` (rotate current objective: #34 phase 3 DONE, app locale-ready; next = ship a real `values-xx` locale) + append `docs/agent/RUN_LOG.md` (per-PR summary).

---

## Notes for the implementer

- **The 6-PR order is a hard dependency, not just preference:** PR3b edits the same UiState files as PR3a (C's `error` channel vs A's `userMessage`). Never branch a later PR off a `main` that lacks the earlier merge.
- **Fragile zones, per PR:** PR3c #161 (`Screen.kt` — touch only `label`; `DeepLinkRoutingTest`/`ScreenSecondaryTitleTest` must stay green); PR3b #194 (`.catch` stays inside `flatMapLatest`); PR3d battle-engine purity (null-`Strings` fallback keeps `GameEngineTest`/`SimulationTest` pure-JVM); PR3e `DomainPurityTest` (resolvers live in `presentation/`, never on the domain enum).
- **Byte-identical English is the acceptance bar.** A `<plurals>` one/other must reproduce today's exact text. Diff on-device where visual (battle Canvas, nav, onboarding, UW path values).
- **`UiMessage.Raw` discipline:** only for lower-layer strings ALREADY localized (billing/ad after A4/A5). New static messages are typed cases.
- **CardType (E4) is the hardest task** — it needs a numeric-value/template split and moves ~54 test assertions. If it balloons, it can be its own commit within PR3e (it already is) but stays in PR3e's branch.
- **Deferred (documented residuals, NOT this plan):** `SupplyDropTrigger.message` (authored push content), `BillingProduct.priceDisplay` (static USD fallback, overridden by live Play price). Leave both; they're recorded in the spec's OUT-of-scope list.
