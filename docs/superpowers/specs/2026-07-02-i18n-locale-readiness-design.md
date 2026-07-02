# i18n Locale-Readiness (phase 3, final) — Design Spec

**Date:** 2026-07-02
**Issue:** #34 (i18n) — phase 3, the final externalization pass.
**Predecessors:** phase 1 (V1X-13 — battle-engine `Strings` seam + `strings.xml` bootstrap) and phase 2
(#354/#355 — Compose-screen `stringResource` extraction). Both shipped. ADR-0014 is the governing decision.
**Status:** design — AMENDED after the Adversarial Review Gate (2026-07-02, ultracode multi-agent:
37 findings, 26 surviving, 11 refuted). The review's central correction: the initial draft **badly
under-scoped** the surface — it omitted the largest body (domain-model display copy), two whole screens
(Help, onboarding content), two more `userMessage` VMs (Battle, Missions), the ad-error data layer, and
mischaracterized the battle-Canvas wiring. This amended spec incorporates every surviving finding.

---

## 1. Goal

Extract **every remaining user-facing English string literal** in the app so that adding a
`res/values-<locale>/strings.xml` yields a fully-translated UI with **no English leaking through**.
This is the explicit prerequisite for "the first real non-English locale" (the payoff of the whole
i18n effort). After this phase, the only English literals remaining in the codebase should be
log messages, code comments, technical identifiers (route names, SharedPreferences keys, SKU ids,
intent extras), and data-only interpolations (`"$count"`, `"${x}%"`, glyphs like `"—"`/`"✕"`).

**Scope reality (post-review):** the remaining surface is **substantially larger** than the initial
draft assumed — it is dominated by (a) **domain-model display copy** hardcoded on enums/models and
rendered verbatim (Workshop/Labs/UW/Card/Mission/Milestone/Cosmetic descriptions — category G) and
(b) **two whole un-externalized screens** (Help — category H; onboarding carousel content — category
I). Because these are load-bearing for the "100% / no English leaking" goal (chosen by the developer),
they are IN scope. This makes phase 3 a **multi-PR sub-effort**, not a two-PR follow-up (see §8).

**Non-goal:** authoring an actual translation. This phase is English-only extraction; the second
`values-xx` file is a separate follow-up.

**Non-goal:** any behavior change. The rendered English text stays byte-identical (modulo where a
plural/units form is deliberately introduced — see §6.D). Test count grows only by the new
`UiMessageTest` (+ any enum-resolver test); assertions that pin migrated English switch to type/id
assertions in place. The plan sets the exact JVM-test delta.

---

## 2. Scope — the categories

The remaining surface clusters into the categories below. Each has a defined mechanism (§4–6).
Every count was code-grounded against `main` @ `406df54` and re-verified by the review; where the
review corrected a count, the corrected figure is shown.

| # | Category | Files (representative) | Count | Mechanism |
|---|---|---|---|---|
| **A** | ViewModel `_userMessage`/`userMessage` strings | **6 VMs**: Store/Cards/Workshop/Labs **+ Missions + Battle** | ~28 | Sealed `UiMessage` + `@StringRes` |
| **A′-billing** | Billing error messages (data layer) | `data/billing/BillingManagerImpl.kt` | ~7 inline + 10 `toUserMessage()` branches (1 arg-bearing) | `context.getString` (impl already has `@ApplicationContext`) |
| **A′-ads** | Ad error messages (data layer) | `data/ads/RewardAdManagerImpl.kt` (+ `internal/RealRewardedAdAdapter.kt`) | 2 inline + 2 `toUserMessage()` mappers | `context.getString` — **must FIRST add `@ApplicationContext`** (not currently injected) |
| **B** | Bottom-nav labels | `presentation/navigation/Screen.kt` | 14 | `label: String` → `@StringRes labelRes: Int` |
| **C** | `SCREEN_LOAD_ERROR` const | `presentation/ui/ErrorState.kt` (+ **10** VM `.catch` sites) | 1 | `String` const → `@StringRes Int` |
| **D** | Duration / units / plurals | LabsScreen `formatTime`, UnclaimedSuppliesScreen `formatTimeAgo`, `CurrencyDashboardViewModel` weekly-reset, **`UltimateWeaponScreen.pathValueAtNext` (7+ unit labels, non-@Composable)**, StatsScreen `"$minutes min"` | ~15 | `plurals` + units; `domain/Strings` where non-Composable |
| **E** | Battle Canvas text | `battle/effects/WaveAnnouncement.kt`, `battle/effects/WaveCooldownText.kt` | ~3 | Engine pre-resolves labels + passes into effect ctors (see §6.E — NOT a drop-in) |
| **F** | Activity literals | `HealthConnectPermissionActivity.kt` (title + long policy body), **`MainActivity.kt:288-289` (snackbar msg + "Settings" action)** | 4 | `getString`/`stringResource` (Activities have `Context`) |
| **G** | **Domain-model display strings** (NEW) | `domain/model/` UpgradeType/ResearchType/DailyMissionType/UltimateWeaponType/CardType descriptions, `Milestone.displayName`; `data/repository/CosmeticRepositoryImpl` name/description | large (~60+) | enum→`@StringRes` resolver at the **presentation boundary** (domain stays Android-free) |
| **H** | **HelpScreen** (NEW) | `presentation/help/HelpScreen.kt` (9 sections, multi-paragraph) | ~18 | Composable `stringResource` (multi-line resources) |
| **I** | **Onboarding carousel content** (NEW) | `presentation/onboarding/OnboardingSlide.kt` (`OnboardingContent.slides` titles+bodies) | ~8 | enum/index → `@StringRes` resolved in `OnboardingScreen` (model stays JVM-testable) |
| **Z** | **Compose stragglers** (sweep) | `presentation/workshop/WorkshopScreen.kt:104` `EmptyState("No upgrades…")` + any sibling `EmptyState`/`placeholder=` literals phase 2 missed | few | Composable `stringResource`; widen the final grep to `EmptyState(message=…)` |

**Explicitly OUT of scope (leave alone — data / technical / already-done):**
- Data-only interpolations: `Text("$count")`, `"${tier.cashMultiplier}x"`, `"$pct%"`, `"$badgeCount"`.
- Glyphs already handled or non-translatable: `"—"`, `"✕"` (extracted in phase 2), `"+"`.
- Notification strings — already externalized in phase 1 (`StepNotificationManager`, `Milestone…`,
  `SupplyDrop…`, `SmartReminder…` all use `context.getString(R.string.…)`; verified).
- `SupplyDropTrigger.message` (domain model field) — authored walking-encounter content delivered by
  push; localizing that copy is a content decision, not a string-extraction mechanic. Known residual.
- **`BillingProduct.priceDisplay`** (`domain/model/BillingProduct.kt:9-13` — the hardcoded USD strings
  `"$0.99"`/`"$4.99/mo"`, rendered at `StoreScreen.kt:107/169/238` as `statePrices[product] ?:
  product.priceDisplay`). This is a **static USD fallback** shown only when Play Billing's live
  `formattedPrice` is unavailable (network failure / no `ProductDetails`); pricing is a
  monetization/content decision, and the live path already localizes. Known residual — mirrors the
  `SupplyDropTrigger.message` treatment; called out here so a reviewer can tell miss from deliberate.
- Log messages, route strings, SKU ids, SharedPreferences keys, intent extras.

---

## 3. Existing patterns this phase reuses (do NOT reinvent)

Two seams already exist from prior phases and are the load-bearing precedent:

1. **`domain/Strings` (interface) + `data/AndroidStrings` (impl).** Pure-Kotlin interface in the
   domain layer (no Android imports) exposing display-string methods (`healHp`, `cashReward`,
   `waveComposition`, `bossCountdown` — the last already uses `getQuantityString` for plural
   correctness, and `enemyTypeName(EnemyType)` — the exact enum→resource precedent for category G).
   The production impl is resource-backed and constructed from a `Context` by `GameSurfaceView` (no
   Hilt binding). This keeps `GameEngineTest`/`SimulationTest` pure-JVM. **This is the mechanism for
   category E (Canvas, via engine pre-resolution) and the model for category G's enum resolver.**

2. **`@StringRes Int` return + call-site `stringResource`.** Established by phase-2 Task 13/14
   (`Screen.secondaryTitle`, `CurrencyType.label()`, `pathLabel()`) for functions/values that must
   stay non-`@Composable` but map to a resource. **This is the mechanism for categories B and C, the
   shape of the `UiMessage` type in A, and the enum→`@StringRes` resolver in G/I.**

The design deliberately does **not** inject a `Context` or a `Strings` provider into the standard
screen ViewModels (A) — that would put an Android dependency where `PresentationPurityTest` and the
fakes-based VM tests want none. Instead A uses a typed, resource-id-carrying value object.

---

## 4. Category A — sealed `UiMessage` (the one real design choice)

### Problem
Screen ViewModels emit transient user messages by setting `_userMessage.value = "Not enough Gems"`
(a raw English `String` in `UiState.userMessage: String?`). The screen shows it via
`LaunchedEffect(state.userMessage) { … snackbar(it) }`. The VM can't call `stringResource` (not
`@Composable`) and must not hold a `Context` (breaks pure-JVM fakes-based tests + clean architecture).

### Which ViewModels (SIX, corrected by review)
The review confirmed the identical `userMessage: String?` → `LaunchedEffect` snackbar pattern in
**six** VMs, not the four the draft named:
- `store/StoreViewModel.kt:153` (1 static literal; `:135` forwards the A′-billing `result.message`)
- `cards/CardsViewModel.kt:114,132,136,177` + `:181` (`.ifBlank { "Ad failed to load. Try again later." }` fallback)
- `workshop/WorkshopViewModel.kt:137,159,175`
- `labs/LabsViewModel.kt:157,168-172,193,208,212,217,236-237`
- **`missions/MissionsViewModel.kt:216,220,225`** (`"You haven't walked enough steps yet."` /
  `"Milestone already claimed."` / the interpolated `UnknownCosmetic` message with a
  `${result.cosmeticId}` arg — needs a format-arg case) — `MissionsUiState.kt:36`,
  `MissionsScreen.kt:69-70`
- **`battle/BattleViewModel.kt:768,772,793,797`** (`"Ad cancelled. Try again."` /
  `result.message.ifBlank { "Ad failed to load. Try again later." }`) — `BattleUiState.kt:55`
  `userMessage: String?`, rendered at `BattleScreen.kt:113-114` `LaunchedEffect(state.userMessage) {
  showSnackbar(it) }`. Battle is a SurfaceView-hosted screen but its `userMessage` is still a Compose
  snackbar, so the `UiMessage.resolve(context)` path applies unchanged.

### Design
Introduce a sealed value type in `presentation/ui/UiMessage.kt`:

```kotlin
import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R

/**
 * A transient, localizable user message emitted by a ViewModel and resolved to a String at the
 * Compose call site (see any screen's `LaunchedEffect(state.userMessage)`). Carries a @StringRes id
 * + optional positional format args, so ViewModels stay Context-free and pure-JVM testable — tests
 * assert the UiMessage *type/args*, not English text. Mirrors the @StringRes pattern used by
 * Screen.secondaryTitle / CurrencyType.label (ADR-0014 phase 2).
 */
sealed interface UiMessage {
    @get:StringRes val resId: Int
    val args: List<Any> get() = emptyList()

    // Argument-free cases as data objects:
    data object NotEnoughGems : UiMessage { override val resId = R.string.msg_not_enough_gems }
    data object NotEnoughSteps : UiMessage { override val resId = R.string.msg_not_enough_steps }
    data object AlreadyMaxLevel : UiMessage { override val resId = R.string.msg_already_max_level }
    // … one case per distinct message across the SIX VMs (list finalized in the plan by grepping
    //   every _userMessage/userMessage assignment). Includes the ad-retry fallbacks now inlined as
    //   .ifBlank { "Ad failed to load…" } / "Ad cancelled. Try again." — these become TYPED cases,
    //   NOT Raw (Raw is only for already-localized lower-layer strings — see §5).

    /** Format-arg case, e.g. the Missions UnknownCosmetic message carrying ${cosmeticId}. */
    data class WithArgs(@StringRes override val resId: Int, override val args: List<Any>) : UiMessage

    /**
     * Escape hatch for a message whose text originates below the presentation layer ALREADY
     * localized (a billing/ad Error whose message was produced by context.getString in the data
     * layer — categories A′-billing / A′-ads AFTER they are localized at source). Carries the
     * resolved String verbatim. NEVER wrap an un-localized lower-layer string in Raw.
     */
    data class Raw(val text: String) : UiMessage {
        override val resId: Int get() = 0 // unused; resolve() handles Raw before reading resId
    }
}
```

`UiState.userMessage: String?` becomes `userMessage: UiMessage?` on each of the six affected UiStates.
The screen's `LaunchedEffect` resolves via a shared extension in `UiMessage.kt`:

```kotlin
fun UiMessage.resolve(context: android.content.Context): String = when (this) {
    is UiMessage.Raw -> text
    else -> context.getString(resId, *args.toTypedArray())
}
```

Screens obtain `context` via `LocalContext.current` and call `state.userMessage?.resolve(context)`.
The `Raw` branch is matched **before** `resId` is read, so `Raw.resId = 0` is never dereferenced.

### Testing
- VM tests (fakes-based, pure JVM) assert the emitted **type** — e.g.
  `assertEquals(UiMessage.NotEnoughGems, state.userMessage)` (data objects have structural equality) —
  never English text. This is *more* robust than the current string-equality assertions.
- **Existing tests that assert the old English verbatim MUST switch to type/arg assertions:** at
  minimum `BattleViewModelTest.kt:290,337,358` (asserts `"Ad cancelled. Try again."` etc.) and the
  Store/Cards/Workshop/Labs/Missions VM tests. The plan enumerates each.
- New `UiMessageTest` (Robolectric, JVM lane — same lane as `CardsScreenTest`): iterate every non-`Raw`
  case, assert `context.getString(case.resId, …)` is non-blank and (for arg cases) that the format
  succeeds. Guards against a case pointing at a missing/wrong-arity resource.

---

## 5. Categories A′-billing and A′-ads — data-layer error messages

Both are data-layer sources of user-facing English forwarded to a snackbar via `result.message`. Both
must be localized **at source** (data layer is permitted Android deps) so the VM can forward via
`UiMessage.Raw`. The two differ in one critical way the draft missed.

### A′-billing (`BillingManagerImpl`)
The impl **already injects** `@ApplicationContext context` (`BillingManagerImpl.kt:80`). Replace each
inline English literal and each `toUserMessage()` branch with `context.getString(R.string.billing_error_…)`.
Corrections from review:
- The literal count is **~7 inline** `PurchaseResult.Error("…")` sites (incl. `UserCanceled` "Purchase
  cancelled" which appears twice, the pending path `:212`, the verify-failed path `:228`, no-activity
  `:97`, and the two-line "Product … not available" `:108-110`), **plus 10 `toUserMessage()` branches**
  (`:442-451`) — not "8". The plan should grep-derive the exact set rather than trust a figure.
- **Arg-bearing branch:** `toUserMessage()`'s `Other -> "Purchase failed (code $responseCode)."`
  (`:451`) interpolates `$responseCode`. Its resource needs a `%1$d` format arg and a
  `getString(R.string.billing_error_other, responseCode)` call — **not** a zero-arg lookup. Any other
  code-embedding branch is treated the same.

`PurchaseResult` stays `Error(message: String)` (no sealed-type change); the string is now localized at
its source. `BillingManagerImplTest` assertions on `message` text switch to resource-resolved
(Robolectric) or `PurchaseResult.Error` shape, per the plan.

### A′-ads (`RewardAdManagerImpl`) — needs a Context first
`data/ads/RewardAdManagerImpl.kt` produces the exact same class of user-facing English:
- `:93` `AdResult.Error("No activity available for ad")`, `:107` `AdResult.Error("Can't request ads
  yet — consent pending")`, and two mappers `SdkAdLoadResult.Error.toUserMessage()` (`:160`) /
  `SdkAdShowResult.Error.toUserMessage()` (`:172`) with English branches incl.
  `"Couldn't load ad. (code $code)"` (another arg-bearing case → `%1$d`).
- `AdResult.Error(message: String)` is `domain/model/AdPlacement.kt:10`; forwarded verbatim by
  `CardsViewModel.kt:181` and `BattleViewModel.kt:772,797` via `result.message`.

**Critical difference:** unlike `BillingManagerImpl`, `RewardAdManagerImpl` does **NOT** inject a
`Context` (its ctor `:74-80` takes adapter/consentManager/activityProvider only). So A′-ads must
**first add an `@ApplicationContext context` injection**, then `context.getString(R.string.ad_error_…)`.
Budget this Context-injection step explicitly — it is not the trivial "impl already has a Context" that
A′-billing enjoys.

**Only after** the ad manager localizes at source is `UiMessage.Raw(result.message)` legitimate for the
ad path. The VM-side `.ifBlank { "Ad failed to load. Try again later." }` and `"Ad cancelled. Try
again."` fallbacks are static English produced IN the VM — those become **typed `UiMessage` cases**
(category A), not `Raw`.

---

## 6. Categories B–I — mechanism detail

**B. Bottom-nav labels (`Screen.kt`).** Change the sealed-class constructor param
`label: String` → `@StringRes labelRes: Int`; each `data object` passes an `R.string.nav_*` id.
The **only** consumer is `BottomNavBar.kt:47-48` (`Text(screen.label)` + `contentDescription =
screen.label`), which resolve via `stringResource(screen.labelRes)`. (A repo sweep confirmed no other
`Screen.label` reader and no test that reads it as a String — the `.label` hits in
`WalkingHistoryChart`/`CurrencyDisplay` are unrelated `period.label`/`CurrencyType.label`.)
**Fragile-zone discipline (#161):** touch ONLY the label field. Do NOT alter `route`, `icon`, or the
`by lazy` lists (`items`, `allScreens`, `argumentFreeRoutes`), `fromRoute`, `startDestination`, or
`secondaryTitle`. `DeepLinkRoutingTest` + `ScreenSecondaryTitleTest` must stay green untouched. All 14
objects get a label (even non-tab screens carry one today); keep parity to avoid a partial migration.

**C. `SCREEN_LOAD_ERROR`.** Today a `String` const in `ErrorState.kt:56`, assigned into
`UiState.error: String?` by **10 distinct ViewModels** (corrected from 11 — the 11th `rg` hit is the
declaration file itself; "Weapons" and "UltimateWeapon" are the same file). The 10 emit sites:
`home/HomeViewModel.kt:151`, `workshop/WorkshopViewModel.kt:117`, `cards/CardsViewModel.kt:97`,
`store/StoreViewModel.kt:109`, `labs/LabsViewModel.kt:140`, `missions/MissionsViewModel.kt:140`,
`stats/StatsViewModel.kt:90`, `economy/CurrencyDashboardViewModel.kt:77`,
`supplies/UnclaimedSuppliesViewModel.kt:50`, `weapons/UltimateWeaponViewModel.kt:118`.
Convert the error channel to a `@StringRes Int`: `error: Int?` on each UiState, VMs emit
`R.string.screen_load_error`, and `ErrorState`/the screen early-return resolves with `stringResource`.
**Fragile-zone discipline (#194, ADR-0028):** the `.catch` MUST stay INSIDE `flatMapLatest` (a
downstream catch makes `retry()` a no-op). This change only swaps the *value* emitted; do NOT move the
`.catch`. **`error` is a delegated property used with `!!` in the screen early-return** — confirm
`Int?` works with the `!!` pattern (it does; `!!` is type-agnostic). The `StatsViewModelTest` #194
tests assert **nullability** (`assertNotNull(error)` / `assertNull(error)` at `:224,235,243`), NOT the
English string — they survive the `String?`→`Int?` change **unchanged**. (The draft overstated this as
"switch to asserting == R.string.x"; that assertion does not exist today. If the plan wants to pin the
specific id, ADD a new `assertEquals(R.string.screen_load_error, error)` rather than "switch" a
non-existent string assertion.)

**D. Duration / units / plurals.**
- `LabsScreen.formatTime` (`"${h}h ${m}m ${s}s"` etc.) + its `"Done!"` early-return (converted to a
  param in phase-2 Task 14) — non-`@Composable` helper. Resolve unit strings via
  `pluralStringResource`/`stringResource` at the (Composable) call site and pass in, OR route through
  a `domain/Strings.labDuration(...)` method where the caller is not Composable.
- `UnclaimedSuppliesScreen.formatTimeAgo` (`"${m}m ago"`/`"${h}h ago"`/`"${d}d ago"` + `"Just now"`
  param) — same treatment; the "ago" forms want `plurals`.
- **`UltimateWeaponScreen.pathValueAtNext` (`:290`) is a `private fun … : String` — NOT `@Composable`
  — and holds 7+ user-facing unit labels**, not just the `"${v.toInt()}s"` the draft cited:
  `"${v.toInt()} DPS"` (`:307`), `"${v.toInt()} dmg"` (`:308`), `"${v.toInt()} enemies"` (`:314`),
  `"${v.toInt()} px/s"` (`:316`), and `String.format` English-word forms `"%.0f%% screen"` (`:315`),
  `"%.0f%% area"` (`:318`), `"%.1f× dmg"` (`:319`). Because the helper is non-Composable, the spec's
  "Composable-local `stringResource`" cannot apply directly: either **lift `pathValueAtNext` into a
  `@Composable`** (resolve each unit at the call site) or route it through the `domain/Strings` seam.
  The plan picks one and enumerates all labels.
- `CurrencyDashboardViewModel` weekly-reset `"${days}d ${hours}h"` (`:128`) flows through a real
  pipeline: `SnapshotData.weeklyTimeRemaining: String` (`:42`) → `MutableStateFlow<SnapshotData>` →
  `EconomyUiState.weeklyTimeRemaining: String` (`:71`), computed in a `suspend refresh()`. "Keep raw
  numbers in state" therefore means changing **both** the private `SnapshotData` field and the public
  `EconomyUiState` field from `String` to raw ints (or a small holder), formatting in the Composable.
  Call out any `EconomyUiState`-shape test that asserts the formatted string.
- `StatsScreen` `"$minutes min"` — Composable-local `stringResource`/`pluralStringResource`.
Where a plural is introduced, English `<plurals>` supplies `one`/`other`; the *rendered English* must
match today's text (e.g. today's `"1m ago"` stays `"1m ago"`). No visible English change.

**E. Battle Canvas text — NOT a drop-in (rewritten per review).** The draft's claim that these
renderers "already receive text through the engine's `strings` provider (the `?: fallback` pattern)"
is **false against the code**. Ground truth:
- `battle/effects/WaveAnnouncement.kt` ctor (`:6-12`) takes only primitives
  (`wave/isBossWave/screenWidth/screenHeight/reducedMotion`) — **no `strings` field** — and hardcodes
  `"⚠ BOSS INCOMING"` (`:53`) and `"Wave $wave"` (`:55`) inside `render()`. Constructed at
  `GameEngine.kt:552` with no strings/label param.
- `battle/effects/WaveCooldownText.kt` ctor (`:59-63`) takes `screenWidth`, a **pre-resolved**
  `nextWaveComposition: String?`, and a `getTimeRemaining` accessor; hardcodes `"Next Wave:
  ${t.toInt()}s"` at `:84`. Constructed at `GameEngine.kt:561` as
  `WaveCooldownText(screenWidth, nextWaveCompositionLabel())`.
- `battle/engine/BattleRenderer.kt` takes a **pre-resolved** `bossCountdownLabel: String?` (`:98`) and
  needs **no change**; `"$pct%"` (`:89`) is data-only.
- The `strings: Strings?` provider lives on `GameEngine.kt:99`; the `?: fallback` idiom lives in
  `GameEngine` (`nextWaveCompositionLabel()` `:582`, `bossCountdownLabel()`) and in
  `CombatResolver`/`BuffTickers` via `host.strings` — **not** in the effect classes.

**Required work:** add `bossIncoming()`, `waveHeader(wave: Int)`, `nextWaveIn(seconds: Int)` to
`domain/Strings` + `AndroidStrings`. Then, mirroring the existing `nextWaveCompositionLabel()`
precedent, **`GameEngine` resolves them from its own `strings` (with a `?: "literal"` fallback so
pure-JVM engine tests keep their literals) and passes the resolved text into the effect constructors**
(new ctor params on `WaveAnnouncement` for the boss/wave labels). **Do NOT inject `Strings` into the
presentation effect classes.** One nuance: `nextWaveIn(seconds)` is interpolated at **render time** from
a per-frame countdown (`WaveCooldownText:82-84`) — the engine cannot pre-resolve it once. Pass either
the nullable `strings` provider (with the `?: "Next Wave: ${s}s"` fallback resolved inside `render()`)
or a `(Int) -> String` formatter, so the live countdown still updates. Confirm the null-`Strings`
fallback keeps `GameEngineTest`/`SimulationTest` pure-JVM. This is PR-later (fragile) work; budget the
constructor-signature churn explicitly.

**F. Activity literals.**
- `HealthConnectPermissionActivity` has two: the `"Privacy Policy"` heading and the long multi-paragraph
  policy body (triple-quoted). Extract to `R.string.hc_privacy_policy_title` and
  `R.string.hc_privacy_policy_body` (the body is a single multi-line resource; preserve every line
  break, `•` glyphs, URL, and email byte-identical; escape apostrophes).
- **`MainActivity.kt:288-289`** (corrected — the draft's "(verified) no literals" claim was FALSE): a
  step-permission snackbar `message = "Step counting is off — enable it in Settings"` +
  `actionLabel = "Settings"` inside a `LaunchedEffect`. The surrounding block is Composable and already
  uses `stringResource` for a sibling `crashNotice` (`:276`), so extract these two the same way. If the
  final grep surfaces any more, extract them too.

**G. Domain-model display strings (NEW — the largest surface).** A large body of user-facing display
copy is hardcoded English on domain enums/models and rendered verbatim:
- `domain/model/UpgradeType.kt` `config.description` (`:80,98,105…`) → `WorkshopScreen`/`InRoundUpgradeMenu`.
- `domain/model/ResearchType.kt` descriptions (`:39,45,56…`) → `LabsScreen:168`.
- `domain/model/DailyMissionType.kt` `description` (`:12-17`) → `MissionsScreen:125`.
- `domain/model/UltimateWeaponType.kt` descriptions (`:43,57,71,85,100,114`) → `UltimateWeaponScreen:134`.
- `domain/model/CardType.kt` effect descriptions (`:22,33,83,89`).
- `domain/model/Milestone.kt` `displayName` (`:9,16,23`) → `MissionsScreen:210`.
- `data/repository/CosmeticRepositoryImpl.kt` name/description (`:170-229`) → `StoreScreen:315`.
**Mechanism:** `domain/` must stay Android-free (`DomainPurityTest`), so map each enum/model value to a
`@StringRes Int` at the **presentation boundary** — an `EnumDisplayName`-style resolver (the exact
precedent is `AndroidStrings.enemyTypeName(EnemyType)` / `CurrencyType.label()`), OR expose via the
`domain/Strings` seam. Cosmetic name/description live in the data layer already, so a resource lookup
there (or a presentation resolver keyed by cosmetic id) fits. This is the bulk of the phase's work and
gets its own PR(s). If any subset is deferred, the spec MUST downgrade the "100%" claim for that subset
and list it as a named residual — silently dropping it makes the goal unachievable.

**H. HelpScreen (NEW).** `presentation/help/HelpScreen.kt:28-98` — 9 `HelpSection(title){ body }`
blocks (💰 Currencies … 🛡️ Fair Play), each a title + multi-paragraph body, all raw English. It is a
first-class bottom-nav destination (`Screen.Help`). Each title/body → `stringResource(R.string.help_*)`
with multi-line resources preserving the `\n` bullets and emoji glyphs byte-identical (same treatment
as category F's long body). Low-risk (pure Compose).

**I. Onboarding carousel content (NEW).** `presentation/onboarding/OnboardingSlide.kt:30-63` —
`OnboardingContent.slides` with hardcoded `title`/`body` (e.g. "Walk to power your ziggurat" / "Every
real step you take earns Steps…"), rendered at `OnboardingScreen.kt:184`. **Constraint:**
`OnboardingSlide`/`OnboardingContent` is intentionally pure-Kotlin + JVM-testable (`OnboardingContentTest`
depends on it; the `:5-16` comment documents this) — it MUST stay Android-free. So either keep the slide
model `String`-based and resolve titles/bodies to `R.string.onboarding_*` at `OnboardingScreen` (map by
index/enum), OR make the model carry `@StringRes` ids resolved in the Composable (`OnboardingScreen` is
`@Composable`). Note this JVM-testability constraint in the plan; reconcile `OnboardingContentTest`.
(This is distinct from the phase-2 onboarding *button* strings already extracted.)

**Z. Compose stragglers (sweep).** Phase 2 missed at least `WorkshopScreen.kt:104`
`EmptyState(message = "No upgrades in this category yet.")`. Extract to `R.string.workshop_empty` and
**widen the final completeness grep to catch `EmptyState(message=…)` / `placeholder=…` Compose
literals**, not just the named categories — this is the realized form of the §10 "grep under-count"
risk.

---

## 7. Architecture & invariants

- **Clean architecture preserved.** `UiMessage` lives in `presentation/ui/` (imports `R` +
  `@StringRes` — presentation-only). `Strings` additions stay in `domain/` (pure) with the impl in
  `data/`. Category G maps enums→`@StringRes` at the presentation boundary (or via `Strings`), so **no
  new Android import lands in `domain/`**; `DomainPurityTest` stays green. `OnboardingContent` (I) and
  the domain models (G) stay Android-free.
- **`PresentationPurityTest` unaffected** — no new `data.local` DAO/entity import in presentation.
- **No schema change, no economy/engine-formula change.** Purely a string-plumbing refactor.
- **Fragile zones touched, with named guards that must stay green:** `Screen.kt` `by lazy` lists
  (#161 — `DeepLinkRoutingTest`, `ScreenSecondaryTitleTest`); `#194`/ADR-0028 error-state
  `flatMapLatest`/`.catch` ordering (`StatsViewModelTest`); the battle `Strings` seam + effect
  constructor churn (V1X-13 — `GameEngineTest`/`SimulationTest` purity via the null-`Strings`
  fallback); `OnboardingContent` JVM-testability (`OnboardingContentTest`). Each is called out
  per-task in the plan.

---

## 8. Rollout — a multi-PR sub-effort (re-shaped post-review)

The expanded surface makes this **more than two PRs**. Grouped by risk and coupling:

- **PR3a — VM messages + data-layer errors (category A + A′-billing + A′-ads).** `UiMessage` type +
  the six VMs/UiStates/screens; localize `BillingManagerImpl` (has Context) and add a Context to
  `RewardAdManagerImpl` then localize it; VM-side ad fallbacks become typed cases. New `UiMessageTest`;
  update `BillingManagerImplTest` + the six VM tests. Self-contained.
- **PR3b — error channel + easy Compose (category C + F + H + Z).** `SCREEN_LOAD_ERROR`→`@StringRes`
  across the 10 VMs (mechanical value swap inside `flatMapLatest`); the two Activities; HelpScreen;
  the WorkshopScreen EmptyState + straggler sweep. Note: C touches the same VMs as A — sequence PR3b
  **after** PR3a (or coordinate) to avoid churning the same UiState files twice; the plan states the
  merge order.
- **PR3c — nav labels + duration/plurals (category B + D).** `Screen.kt` label field (#161 fragile);
  the duration/units/plurals incl. `pathValueAtNext` restructuring and the economy-pipeline change.
  Heightened review; on-device nav spot-check.
- **PR3d — battle Canvas (category E).** Engine `Strings` extension + effect constructor plumbing;
  fragile battle zone; on-device visual spot-check.
- **PR3e — domain-model display strings (category G).** The largest, most mechanical body; enum→resource
  resolver + its test. May itself split by model family if large. On-device spot-check of a few screens.
- **PR3f — onboarding content (category I).** The JVM-testability-constrained slide model change +
  `OnboardingContentTest` reconciliation.

The plan finalizes the exact PR boundaries and merge order; the above is the risk-grouping intent, not
a hard contract. Each screen/file follows phase-2's **methodology grep gate**: after editing, grep the
file for English literals and confirm only data-only/glyph/technical survivors remain. A final repo-wide
sweep (the phase-2 Task-15 grep, **widened** across `presentation/`, `service/`, `navigation/`,
`battle/`, the two Activities, `domain/model/`, and `EmptyState(message=…)` Compose literals) is the
completeness gate for the whole phase.

---

## 9. Testing strategy

- Full JVM suite green; the count grows by `UiMessageTest` (+ any enum-resolver / onboarding-resolver
  test). Assertions that pin migrated English switch to type/id assertions in place. The plan sets the
  exact delta.
- VM tests: assert `UiMessage` **type/args** and UiState `error: Int?` **id**, not English. This
  includes `BattleViewModelTest.kt:290,337,358` (currently exact-string) and the Missions/Store/Cards/
  Workshop/Labs VM tests.
- `BillingManagerImplTest`: resource-resolved message or `Error` shape.
- `StatsViewModelTest` #194 tests are nullability checks that survive `String?`→`Int?` unchanged; add a
  new id assertion only if the plan wants to pin `screen_load_error`.
- `OnboardingContentTest` reconciled for the slide-model change (category I).
- `detekt` + `ktlint` green (baseline; watch `MaxLineLength` on new resource-arg call sites).
- Compose UI tests (`CardsScreenTest`, `OnboardingScreenTest`) — reconcile any assertion that targets a
  now-typed message or migrated content.
- Battle + bottom-nav + Help + onboarding: on-device visual spot-check (no Compose-rule layout test for
  the SurfaceView/nav — PR-4736).

---

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `Screen.kt` label change perturbs `by lazy` init order → NPE (#161) | Change only the label field; run `DeepLinkRoutingTest`/`ScreenSecondaryTitleTest`; do not touch route lists. |
| Moving the `.catch` value swap accidentally hoists `.catch` outside `flatMapLatest` (#194) | Swap value only; keep `.catch` inside; `StatsViewModelTest` retry-recover guard. |
| A plural/units change alters rendered English | English `<plurals>` encode today's exact text; visual diff. |
| `UiMessage.Raw` becomes a dumping ground / carries un-localized English | Doc + review rule: `Raw` ONLY for lower-layer strings that are ALREADY localized (billing/ads *after* source localization); new static messages MUST be typed cases; VM `.ifBlank` fallbacks are typed cases. |
| `RewardAdManagerImpl` localization forgotten (no Context) | A′-ads task adds `@ApplicationContext` FIRST; without it `Raw(result.message)` leaks English. |
| Category G under-estimated / partially done → goal unmet | G gets its own PR(s); any deferred subset is a NAMED residual + the "100%" claim is downgraded for it. |
| Battle E wired wrong (frozen countdown / Strings pushed into effects) | Engine pre-resolves static labels; per-frame `nextWaveIn` gets the provider/formatter; null-`Strings` fallback keeps engine tests pure-JVM. |
| Long HC policy body / Help bodies / onboarding bodies corrupted on extraction | Copy verbatim into multi-line resources; diff rendered output; preserve line breaks/glyphs/URL/email; escape apostrophes. |
| Missed literals (grep under-count, as in phase 2 — already realized: WorkshopScreen EmptyState) | Methodology grep per file + final **widened** repo-wide sweep (incl. `EmptyState`, `domain/model/`) as the completeness gate. |

---

## 11. Open questions for review

- **D formatting home:** per-call-site choice (Composable-local `pluralStringResource` vs a
  `domain/Strings` method). Recommendation: Composable-local where the caller is Composable (avoids
  injecting a provider into VMs that don't need one); `Strings`/lift-to-Composable for the
  non-Composable `pathValueAtNext`.
- **Category G resolver home:** a presentation-layer `@StringRes` resolver (keyed by enum) vs extending
  `domain/Strings`. Recommendation: presentation-layer resolver for the enum descriptions (mirrors
  `CurrencyType.label()`); `domain/Strings` only where the value is consumed by non-presentation code.
- **`UiMessage.resolve(context)` location:** extension in `UiMessage.kt` (recommended — DRY, one place
  to handle `Raw`) vs inline `when` per screen.
- **Phase decomposition:** given the surface is ~2-3× the initial estimate (dominated by G), should
  phase 3 ship as the 6-PR sub-effort above, or should category G (domain-model display copy) be split
  into its own tracked follow-up so the smaller categories land sooner? (Developer call — see §8.)
