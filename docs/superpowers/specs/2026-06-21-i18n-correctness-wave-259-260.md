# Spec — i18n Correctness Wave (#259 plurals · #260 concatenation + raw enum names)

- **Date:** 2026-06-21
- **Issues:** #259 (no plurals — quantity nouns grammatically wrong at n=1 and for inflected languages), #260 (translation-hostile string concatenation + raw `enum.name` surfaced to users)
- **Builds on:** ADR-0014 / V1X-13 (i18n phase 1, *shipped*) — the `domain/Strings` seam, `data/AndroidStrings` impl, the populated `strings.xml` (80 entries), and the `HardcodedText` lint-as-error guard.
- **Status:** Reviewed (Adversarial Review Gate, ultracode 39-agent run: 33 findings → 31 surviving / 2 refuted; all surviving applied below) → pending plan.

## 0. Issue-line drift map (review SC-3)

The issues were filed 2026-06-18; lines have drifted. Current live lines (verified 2026-06-21):

| Issue cite | Live line | Note |
|---|---|---|
| #260 `GameEngine.kt:832` | `GameEngine.kt:840` | :832 is now a doc comment; real concat at :840 |
| #260 `MissionsViewModel.kt:173` | `MissionsViewModel.kt:145` (mission) + `:160` (milestone) | :173 is now `cancelForTest`'s KDoc |
| #260 `OnboardingSlide.kt:32-54` | same | **deferred** to #34 (prose, see §1 / §9) |
| #260 `HelpScreen.kt:28-53` | `:28-96` | **deferred** to #34 (prose, see §1 / §9) |
| #259 `strings.xml:7,29,45,68` | `:7,29,45` are plural sites; `:68` (`inround_readout_max "Now: %1$s (MAX)"`) is **not** a quantity noun | dropped from plural scope (no count noun) |

## 1. Goal & non-goals

**Goal.** Fix the two genuine *correctness* bugs the 2026-06-18 audit found:

1. **#259** — quantity nouns are hardcoded to a single fixed form, so English reads wrong at `n=1`
   ("+1 Step", "1 days remaining", "Wave 1 · 1 enemies") and no inflected locale can ever be correct.
2. **#260** — user-facing sentences are assembled by `+`/`joinToString` (baking English word order),
   and several enums surface raw `CONSTANT_CASE` (`BASIC`, `BOSS`, `EPIC`, pack-tier/category names)
   directly to the user.

This wave makes the **plural-sensitive** and **concat/enum-broken** surfaces grammatically correct and
localization-ready, on the *existing* ADR-0014 architecture.

**Non-goals (explicitly out of scope — tracked, not done here):**

- The bulk extraction of the ~412 already-fine English Compose literals on the never-migrated menu
  screens. That is ADR-0014's deferred **phase 2** (issue #34) and is English-only-payoff work until a
  real locale ships. #34 stays open.
- Shipping a non-English locale / pseudo-locale CI (ADR-0014 defers to v1.3/v1.4).
- Replacing `toDisplayName()` for the *already title-cased* enums (`UpgradeType`, `ResearchType`,
  `UltimateWeaponType`, `Biome`, `BattleCondition`, `CardType`). They read fine in English; their full
  localization is #34 phase-2 work. **Only enums that surface raw `CONSTANT_CASE` are fixed here.**
- The Help screen's 8 `+`-concatenated bullet blocks (`HelpScreen.kt:28–96`) and the **OnboardingSlide
  body copy** (`OnboardingSlide.kt:32–54`). Those are English *prose*, not a grammatical or enum-name
  bug — leave to #34. **Consequence (review SC-4): #260 cites those two sites, so #260 is only
  *partially* closed by this PR** (see §9).
- Abbreviation/relative-time sites ("5m ago", "3h", "min", "PS", "k", "Nd Hh"). The unit is masked so
  there is no grammatical bug; they are presentation-format concerns for #34.
- Cosmetic *display names* themselves (e.g. "Lapis Lazuli Ziggurat Skin") — these are free-text English
  domain content (`MilestoneReward.Cosmetic.name`), localizable as content under #34. This wave carries
  them through verbatim (see §3f); it does not move them to resources.

**No schema / economy / engine-formula change.** Behaviour-preserving for English output except where the
output was *grammatically wrong* (the bug being fixed).

## 2. Ground truth (verified during brainstorming + review)

- `app/src/main/res/values/strings.xml` already holds **80 `<string>` entries** (battle HUD, all
  overlays, notifications, #214 a11y). #34's "only one entry" premise is **stale**. There are **zero
  `<plurals>`** and **no `values-*/` locale folders**.
- The engine string seam exists: `domain/Strings` (interface, Android-free) + `data/AndroidStrings`
  (`Context.getString`-backed), wired via `GameEngine.strings: Strings?` with a literal fallback so
  `GameEngineTest` stays pure-JVM (ADR-0014 §3). **`AndroidStrings` has zero test coverage today**
  (review TS-1) and **there is no `FakeStrings`** (review TS-2) — both are authored new here.
- `HardcodedText` lint is **error-level** (`app/build.gradle.kts` ~L202–210) but **XML-only** — it does
  not flag Compose `Text("literal")` (documented in-code; ~110 phase-2 Compose literals still pass).
- `unitTests.isReturnDefaultValues = true` and `unitTests.isIncludeAndroidResources = true`; Robolectric
  is on `testImplementation`. So resource-backed strings/plurals are testable on the JVM lane via
  Robolectric (no emulator). **A test that resolves `@StringRes`/`getQuantityString` to *text* needs
  Robolectric** (a plain JVM test sees `isReturnDefaultValues` stubs) (review TS-5).
- Enum display today: raw `enum.name` reflected through `presentation/ui/EnumDisplayName.kt`
  `String.toDisplayName()`. **No `@StringRes` label map, no domain label field** (sole exception:
  `Milestone.displayName`, a hardcoded English `String`).
- The `#225` guard `NoRawEnumNameInUiTest` fails the build on `.name.replace(` in `presentation/`. It
  does **not** catch the patterns this wave fixes (`.lowercase().replaceFirstChar`, `.replace("_"," ")`
  on a String, bare `Text(enum.name)`, `.name.take(`) — so "stays green" is trivially true and gives no
  regression protection unless strengthened (review IC-9; §5).
- `minSdk 34 / compileSdk 37`. `java.time` (incl. `DayOfWeek.getDisplayName`) is native at API 34;
  `<plurals>` with only `one`/`other` is valid English; `sealed interface` + `Channel.CONFLATED` +
  `@StringRes Int` are all sound (review AF-5, refuted-as-fine).

## 3. Scope inventory (the exact sites this wave fixes)

### 3a. Plural sites → `res/values/plurals.xml` (#259)

Count-driven full nouns where singular/plural is visibly wrong. Each `<plurals>` ships `one` + `other`
(English); the structure enables `few`/`many`/`zero` per future locale.

| plural key | example today (wrong) | consumer (live line) | path |
|---|---|---|---|
| `fx_step_reward` | "+1 Step" / "+3 Step" | engine float via `Strings.stepReward` | getQuantityString (impl) |
| `steps_earned_banner` | "👟 +1 Steps" | `BattleScreen.kt:228` HUD banner | pluralStringResource |
| `wave_enemies` | "…· 1 enemies" | `battle_wave_header` split (3d) → `BattleScreen.kt:213` | pluralStringResource |
| `boss_in_waves` | "Boss in 1 waves" | engine `bossCountdownLabel` via seam | getQuantityString (impl) |
| `reward_gems` | "+1 Gems" | celebration + milestone + supply row (3f, 3g) | pluralStringResource |
| `reward_power_stones` | "+1 Power Stones" | celebration + milestone + supply row | pluralStringResource |
| `reward_steps` | "+1 Steps" (supply row) | `formatSupplyReward` (3g) + supply celebration | pluralStringResource |
| `card_copies` | "+1 Copy" | `CardsScreen.kt:162` (3h) | pluralStringResource |
| `days_remaining` | "1 days remaining" | `StoreScreen.kt:148` season pass | pluralStringResource |
| `page_x_of_n` | "Page 1 of 4" | `OnboardingScreen.kt:194` page-dots a11y | pluralStringResource |
| `widget_steps` | "1 steps" | `StepWidgetProvider.kt:37` | getQuantityString |
| `reminder_steps_away` | "1 steps from upgrading…" | `SmartReminderManager.kt:80` | getQuantityString |
| `notif_today_steps` | "Today: 1 steps" | `StepNotificationManager` (split, 3i) | getQuantityString |

**Long→Int rule (review AF-1/IC-8).** `pluralStringResource(id, count, …)` / `getQuantityString(id, quantity, …)`
take an **`Int`** quantity selector. Sites whose count is a `Long` (`state.stepsEarnedThisRound: Long`,
balance) pass `count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()` as the **selector**, while the
displayed number uses the full value via the `%1$d` format arg. The `ClaimReward` currency fields (3f)
are declared **`Int`** (all reward sources — `SupplyDrop.rewardAmount: Int`, mission/milestone gem/PS
amounts — fit Int; no celebration reward is a large Long), so no narrowing is needed there.

### 3b. Plural consumption paths (both already in-repo)

- **Compose (main thread):** `pluralStringResource(R.plurals.x, count, count)`.
- **Off-Compose** (engine via the `Strings` seam; notifications, widget, reminder — all have a
  `Context`/`Resources` handle, confirmed in the surface map): `resources.getQuantityString(R.plurals.x, count, count)`.

### 3c. Engine seam extension (#260 off-thread strings)

`GameEngine` (in `presentation/battle/engine/`, but runs on the **game-loop thread**) cannot call
`stringResource`. Extend the established seam — **add to `domain/Strings`** (interface, stays
Android-free; `EnemyType` is a domain enum so the signature imports no Android) and implement in
`data/AndroidStrings`:

```kotlin
// domain/Strings.kt (additions)
fun enemyTypeName(type: EnemyType): String                 // localized enemy noun (replaces raw .name)
fun waveComposition(counts: Map<EnemyType, Int>): String   // whole "Next: 12 Basic, 4 Ranged, 1 Boss"
fun bossCountdown(waves: Int): String                      // plural-correct via getQuantityString
```

- `waveComposition` builds the line from a **templated resource with a list placeholder**
  (`R.string.wave_composition` = "Next: %1$s"); the list is `getQuantityString` per entry (`wave_comp_entry`
  = "%1$d %2$s" with `enemyTypeName(type)`) joined by a `wave_composition_separator` resource — **no `+`
  concat in code**. The `@StringRes` map for `EnemyType` (`enemy_basic`…`enemy_scatter`) lives in
  `data/AndroidStrings` (a `when`), **not** in the domain enum.
- **Ordering (review FZ-2):** `WaveSpawner.getWaveComposition` returns insertion order; on boss waves
  BOSS is inserted **first** ("Next: 1 Boss, …"), so the spec's "12 Basic, 4 Ranged, 1 Boss" example is
  a *non-boss* illustration only. The seam **must pass the map through unchanged and must NOT re-sort**
  (sorting by `EnemyType.ordinal` would reorder boss waves). `AndroidStringsTest` pins a **boss-wave
  (BOSS-first) case**, not just a generic one.
- `GameEngine.nextWaveCompositionLabel()` / `bossCountdownLabel()` call
  `strings?.waveComposition(...) ?: <literal fallback>` and `strings?.bossCountdown(w) ?: <literal>`.
- **FZ-1 — fallback is a degraded last-resort, not the contract.** The literal fallback keeps
  `GameEngineTest` pure-JVM, and it intentionally keeps the **current raw English** (incl. raw `.name`
  for enemy types) — it is only reachable when `strings == null` (a misconfiguration; production always
  wires `AndroidStrings`). Acceptance criterion 3 ("never raw CONSTANT_CASE") is therefore guaranteed on
  the **production `strings != null` path**; the fallback is explicitly documented as degraded. *(There
  is no current `GameEngineTest` assertion on `nextWaveCompositionLabel`/`bossCountdownLabel` output —
  verified — so "zero churn to existing engine tests" still holds.)*
- **`WaveAnnouncement` (Canvas-drawn, render thread):** route the noun/sentence banners through the seam
  ("BOSS INCOMING"). The plain **"Wave N"** banner and the **`N / M` HP bar** stay numeric literals (no
  plural noun, no grammar) — *not* routed.

### 3d. Raw-`.name` enum labels → real localized display-names (#260)

Only enums that currently surface raw `CONSTANT_CASE` (or `.replace("_"," ")`-cased) to users. Domain
can't reference `R`, so resolution is by call-site, and **the receiver type at the call site dictates the
mechanism** (review IC-1/CG-2/AF-2/FZ-3):

| enum | site (live line) | receiver at site | fix |
|---|---|---|---|
| `EnemyType` | `GameEngine.kt:840` wave composition | enum (off-thread) | `Strings.enemyTypeName()` seam (3c) |
| `WavePhase` | `BattleScreen.kt:213` `lowercase().replaceFirstChar` | **String** (`BattleUiState.wavePhase: String`, from `spawner?.phase?.name ?: ""`) | **String→@StringRes `when` lookup** at the call site (`"SPAWNING"`/`"COOLDOWN"`/`""`→render nothing); uiState **stays a String** so the `state.wavePhase == "SPAWNING"` color branch (`:217`) and the #214 announcer are untouched |
| `UpgradeCategory` | `InRoundUpgradeMenu.kt:110` `cat.name` **and** `WorkshopScreen.kt:75` `category.name` (review SC-1) | enum | `@StringRes fun UpgradeCategory.labelRes()` extension + `stringResource`, both sites |
| `PackTier` | `CardsScreen.kt:117` `Text(pack.tier.name)` | enum | `@StringRes fun PackTier.labelRes()` |
| `CosmeticCategory` | `StoreScreen.kt:202` `cosmetic.category.replace("_"," ")` (review CG-1: was mis-cited :200 / "raw") | **String** today (`CosmeticDisplayInfo.category: String` = `it.category.name`, `StoreViewModel.kt:80`) | **Change `CosmeticDisplayInfo.category` to carry the `CosmeticCategory` enum**, then `@StringRes fun CosmeticCategory.labelRes()` + `stringResource` in the screen |
| `CardRarity` | `Rarity.kt:65` `cardRarityLabel = rarity.name` | enum | `@StringRes fun CardRarity.labelRes()` |
| UW rarity | `Rarity.kt:68-72` `uwRarityLabel` returns literal "RARE"/"EPIC"/"LEGENDARY", same `RarityBadge` (review SC-2) | tier | `uw_rarity_rare/epic/legendary` strings resolved at the call site (`UltimateWeaponScreen.kt:113`) |

New strings: `enemy_basic/fast/tank/ranged/boss/scatter`, `wave_phase_spawning/cooldown`,
`upgrade_cat_attack/defense/utility`, `pack_tier_common/rare/epic`,
`cosmetic_cat_ziggurat_skin/projectile_effect/enemy_skin`, `rarity_common/rare/epic`,
`uw_rarity_rare/epic/legendary`.

**`StatsViewModel.kt:100` `DayOfWeek.name.take(3)`** chart axis: use
`DayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())` (native at API 34). **Add imports**
`java.time.format.TextStyle` + `java.util.Locale` (review AF-3). No new resource.

### 3e. Concatenation seams (#260)

| site (live line) | today | fix |
|---|---|---|
| `GameEngine.kt:840` wave composition | `"Next: " + joinToString(", "){ "${v} ${k.name}" }` | `Strings.waveComposition()` (3c) |
| `GameEngine.kt:852` boss countdown | `if (w==1) "Boss next wave" else "Boss in $w waves"` | `Strings.bossCountdown()` (3c) |
| `MissionsViewModel.kt:145` mission claim + `missionRewardLabel:196` | `parts.joinToString(" ") + " claimed!"` | structured payload (3f) |
| `MissionsViewModel.kt:160` milestone claim | `"${milestone.rewardsSummary()} claimed!"` | structured payload (3f) |
| `Milestone.rewardsSummary()` (domain, `:34`) — also surfaced at `MissionsScreen.kt:165` | `joinToString(", "){ "${amount} Gems" / cosmetic.name }` | Compose-boundary formatter (3f) |
| `UnclaimedSuppliesViewModel.kt:72` `supplyLabel` | `"+${n} Gems claimed!"` etc. | structured payload (3f) |
| `UnclaimedSuppliesScreen.kt:124` `formatSupplyReward` (the **row**, review CG-3/IC-5) | `"+${n} Steps"` etc. + CARD_COPY name | plurals + keep CARD_COPY name path (3g) |
| `CardsScreen.kt:162` pull result | `"${formatName(r.type.name)} +1 Copy"` | full template (3h) |

### 3f. Celebration payload contract (#260 — the one real blast-radius change)

`ClaimCelebrationEvent(val label: String)` becomes a **structured** payload formatted at the Compose
boundary. It **must represent mixed currency + cosmetic + card rewards** (review IC-2: milestone
`IRON_SOLES`/`GLOBE_TROTTER`/`MARATHON_WALKER` carry `MilestoneReward.Cosmetic`):

```kotlin
sealed interface ClaimReward {
    /** Any mix of currencies, cosmetic display-names (domain free-text, carried verbatim), and card grants. */
    data class Bundle(
        val gems: Int = 0,
        val powerStones: Int = 0,
        val steps: Int = 0,
        val cosmeticNames: List<String> = emptyList(), // e.g. "Lapis Lazuli Ziggurat Skin" — content, #34
        val cards: Int = 0,                            // count of card grants (supply CARD_COPY = 1)
    ) : ClaimReward
    data object Generic : ClaimReward                  // "Reward claimed!" fallback (empty/unknown)
}
data class ClaimCelebrationEvent(val reward: ClaimReward)  // NAME unchanged → screens' remember<…?> untouched
```

- The **type name `ClaimCelebrationEvent` is kept** (only its field changes), so both screens'
  `remember { mutableStateOf<ClaimCelebrationEvent?>(null) }`, the `celebration = it` collectors, and the
  `ClaimCelebration(celebration)` call sites need **no edits** (review FZ-4, refuted-as-overstated). The
  real edits are: the data-class field + the composable body (`ClaimCelebration.kt:24/56`) + the 4
  `trySend` sites (`MissionsViewModel.kt:145,160`; `UnclaimedSuppliesViewModel.kt:56,66`).
- VMs emit the structured value — **no English assembly in VM/domain.** The `Channel.CONFLATED`,
  `receiveAsFlow()`, and `@VisibleForTesting cancelForTest()` ticker harness are **unchanged**.
- **`ClaimCelebration` formatter** (in the composable) builds a list of localized parts:
  per currency `> 0` → `pluralStringResource(R.plurals.reward_gems, n, n)` etc.; per cosmetic name → the
  name verbatim; `cards > 0` → `pluralStringResource(R.plurals.card_copies, cards, cards)` (or a `Card`
  string). Parts are joined by a **`reward_join` separator resource** then wrapped in
  `R.string.reward_claimed` = "%1$s claimed!". Empty parts / `Generic` → `R.string.reward_generic` =
  "Reward claimed!".
  - **Honest framing (review IC-3):** the multi-part case is still a resource-driven **list-join** — the
    `reward_join` separator + leading `+` placement are defined in the plan (separator value, whether `+`
    is inside `reward_gems` or the join). Fully-positional small-N templates (`reward_pair`/`reward_triple`)
    are a deferred refinement, **not** required here; the list-join with a separator resource is the
    accepted #260-compliant approach (matches the §3c wave-composition pattern). This is documented, not
    hidden.
  - **Null/exit safety (review AF-4):** the composable currently renders `event?.label ?: ""` and keeps
    the last value through the `AnimatedVisibility` fade-out. The new formatter resolves text only when
    `event?.reward != null`, returning "" during exit; **all `pluralStringResource` calls live in the
    composable body**, never in a nullable Elvis expression.
- **`Milestone.rewardsSummary()`** (domain `String`, surfaced at `MissionsScreen.kt:165` **and** fed to
  the milestone celebration) is **removed**; both consumers move to the same Compose-boundary formatter
  fed by the existing `Milestone.rewards: List<MilestoneReward>`. The milestone row at `MissionsScreen.kt:165`
  renders the localized parts (currencies via plurals, cosmetic names verbatim).

### 3g. Supplies **row** `formatSupplyReward` + the #20 CARD_COPY regression (review CG-3/IC-5/TS-3/CG-4)

The always-visible supplies-list **row** (`UnclaimedSuppliesScreen.kt:97` → `formatSupplyReward:124`) is a
**separate** function from the celebration `supplyLabel`, with the same `+N Steps/Gems/Power Stones`
plural bug **plus** the load-bearing **#20 CARD_COPY** behavior (for `CARD_COPY`, `rewardAmount` is a
card-*type index*, not a quantity → must render the resolved card name + "x1", never "+N"):

- Steps/Gems/Power Stones → `pluralStringResource(R.plurals.reward_steps/gems/power_stones, n, n)`.
- **CARD_COPY path is preserved unchanged** (resolve `CardType.entries[rewardAmount % size]` → name +
  "x1"). (The card *name* uses `toDisplayName` for now — out of scope per §1.)
- **`SupplyRewardFormatTest` is NOT retired** (it is the #20 regression guard). It is **re-pointed**: the
  three quantity assertions assert the new plural output; the CARD_COPY assertions (the #20 core) stay.
  Because plural resolution needs resources, the quantity assertions move to a **Robolectric** harness
  (the CARD_COPY assertions can stay pure if the card-name path is unchanged, or move with them).

### 3h. Card pull result (`CardsScreen.kt:162`, review IC-6)

`if (r.isNew) formatName(r.type.name) else "${formatName(r.type.name)} +1 Copy"` → a full template
`R.string.card_pull_result` = "%1$s %2$s" where `%1$s` = card name (`toDisplayName`, out-of-scope per §1)
and `%2$s` = `pluralStringResource(R.plurals.card_copies, n, n)`; the `isNew` branch renders just the
name. Word order for the name+count join lives in the resource, not code.

### 3i. `notif_step_content` split (review IC-4)

Current: `notif_step_content "Today: %1$d steps | Balance: %2$d Steps"` packs the **daily walking count**
(a true quantity noun) and the **currency balance** ("Steps" = the game's currency proper-noun). Split:

- `notif_today_steps` = **plural** ("Today: %1$d step" / "Today: %1$d steps").
- `notif_balance` = **flat** "Balance: %1$d Steps" ("Steps" is the currency name, not plural-varying).
- `notif_step_content` = parent **"%1$s | %2$s"** (two pre-formatted `%s`).
- `StepNotificationManager.kt:60` composes
  `getString(notif_step_content, getQuantityString(notif_today_steps, daily.toInt(), daily), getString(notif_balance, balance))`.
  The **data values and the #43 last-known-good balance fold are preserved** (balance still passed as
  before; only the formatting is split). R5's "preserve the exact data" = the numeric values, satisfied.

## 4. Architecture & data flow

```
                         ┌─────────────────────────────────────────────┐
  res/values/            │ strings.xml  (+ enum labels, split notif,     │
  ├ strings.xml          │              full-sentence templates)         │
  └ plurals.xml  ◄───────┤ plurals.xml  (count-driven nouns: one/other)  │
                         └─────────────────────────────────────────────┘
        ▲ getQuantityString                    ▲ pluralStringResource / stringResource
        │ (Resources, off-Compose)             │ (Compose, main thread)
  ┌─────┴───────────────┐          ┌────────────┴───────────────────────────────────┐
  │ data/AndroidStrings │          │ presentation (Compose call sites)                │
  │  enemyTypeName       │          │  UpgradeCategory/PackTier/CosmeticCategory/      │
  │  waveComposition     │          │  CardRarity .labelRes() (enum receiver);          │
  │  bossCountdown       │          │  WavePhase: String→@StringRes when() (String);   │
  │  (+ existing fx_*)   │          │  Cards/Store/InRoundMenu/Workshop/Supplies row   │
  └─────┬───────────────┘          │  ClaimCelebration(ClaimReward) ── formats ───────┤
        │ implements                │  VMs emit ClaimReward.Bundle (structured)         │
  ┌─────┴───────────────┐          └──────────────────────────────────────────────────┘
  │ domain/Strings (port)│ ◄── GameEngine.strings?.…() ?: <literal fallback>  (game-loop thread; fallback = degraded)
  └──────────────────────┘
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │ service: StepNotificationManager / SmartReminderManager / StepWidgetProvider   │
  │   getQuantityString(R.plurals.…) — Context/Resources in scope, no Compose      │
  └──────────────────────────────────────────────────────────────────────────────┘
```

## 5. Testing strategy (JVM/Robolectric lane — no emulator)

- **`PluralsResourceTest`** (Robolectric): for each new plural, assert `n=1` → `one` form and `n=2` →
  `other` form. Pins the regression ("+1 Step" not "+1 Steps"; "1 day" not "1 days"). Mutation: deleting
  a `one` form breaks it.
- **`AndroidStringsTest`** — **NEW** (review TS-1; `AndroidStrings` has no test today). Robolectric
  (`@RunWith(RobolectricTestRunner)`, `ApplicationProvider.getApplicationContext()`): `enemyTypeName`
  (all 6), `waveComposition` (multi-entry order + separators + a **BOSS-first boss-wave case**, FZ-2),
  `bossCountdown` (n=1 vs n=2). Asserts localized + plural output; no raw `.name` substring.
- **`FakeStrings`** — **NEW** (review TS-2; none exists). Pure-Kotlin fake under `test/.../fakes/`
  implementing **all** `domain.Strings` methods (existing 5 + the 3 added in 3c).
- **`GameEngineTest`** (pure-JVM, existing): existing assertions unchanged (no current assertion on the
  composition/countdown labels → zero churn, FZ-1). **Add** a test that with `strings` set (`FakeStrings`),
  the seam value is used for `nextWaveCompositionLabel`/`bossCountdownLabel`.
- **`ClaimCelebration` formatter test** — **NEW** (Robolectric). MUST cover all cases the retired
  `missionRewardLabel` test covered (review TS-4): single currency, multi-currency join (`reward_join`),
  and `Generic` fallback — plus a cosmetic-name `Bundle` and a `cards > 0` `Bundle`.
- **VM tests:** `MissionsViewModelTest` / `UnclaimedSuppliesViewModelTest` assert the **structured**
  `ClaimReward.Bundle` emission (amounts + cosmeticNames + cards), not a string. The retired
  `missionRewardLabel` literal assertions are replaced by these + the formatter test (TS-4).
- **`SupplyRewardFormatTest`** — **kept, re-pointed** (review TS-3/CG-4): quantity assertions → new plural
  output (Robolectric); the #20 CARD_COPY assertions stay.
- **`EnumLabelResTest`** — **Robolectric** (review TS-5; resolving `@StringRes`→text needs a Context):
  iterate each in-scope enum's `entries` (and the WavePhase/CosmeticCategory string keys), assert each
  resolves to a non-blank string (catches a missing mapping when a constant is added).
- **`NoRawEnumNameInUiTest`** (#225): **strengthen (non-optional, review IC-9)** to also fail on bare
  `Text(<enum>.name)`, `.name.take(`, `.name.lowercase(`, and `.replace("_"` text-surfacing in
  `presentation/`, so the lower-quality pattern can't drift back into the fixed sites. (Tune the regex to
  avoid false positives on legitimate non-UI `.name` uses; scope to `Text(`/`contentDescription` lines.)

## 6. Fragile zones honored (STATE.md "do-not-touch")

- **ADR-0014 engine `Strings` seam + literal fallback** — *extended*, not broken; fallback documented as
  degraded last-resort (3c/FZ-1); `GameEngineTest` stays Robolectric-free.
- **`GameEngine.entities`/`uwStates` `entitiesLock` + game-loop crash guard (#118/#190/#191)** — only
  string *emission* is touched; no shared-collection structural mutation, no lock-order change.
- **`BattleUiState.wavePhase` stays a `String`** — the `== "SPAWNING"` color branch (`:217`) and the #214
  TalkBack announcer are untouched; only the *displayed text* is localized (3d).
- **`MissionsViewModel` `_today` ticker (#195) + `cancelForTest()` (#162) + `Channel.CONFLATED`** —
  payload *type* changes only; flow mechanics + ticker + test harness untouched (FZ-4).
- **#20 CARD_COPY supply-row behavior + `SupplyRewardFormatTest`** — preserved + re-pointed, not retired (3g).
- **`formatCurrency` `Locale.US` grouping (#160)** — untouched (numeric grouping, not a noun).
- **Onboarding model purity (ADR-0021/0024)** — the only onboarding touch is the `page_x_of_n` plural at
  the **screen** (`OnboardingScreen.kt:194`, `pluralStringResource`); the pure `OnboardingSlide` model and
  its body copy are **not** touched (#34).
- **`HardcodedText` lint** stays error-level; new XML resources comply.

## 7. Acceptance criteria

1. `res/values/plurals.xml` exists; every site in 3a renders the correct form at `n=1` and `n≥2`.
2. No reachable "1 days"/"+1 Steps"/"1 enemies" grammatical error remains at the in-scope sites
   (including the supplies **row** `formatSupplyReward`, review IC-5).
3. On the **production path** (`AndroidStrings` wired), the #260 evidence sites surface **localized display
   names**, never raw `CONSTANT_CASE`: `GameEngine:840` (enemy types via seam), `BattleScreen:213`
   (WavePhase), `CardsScreen:117` (PackTier), `InRoundUpgradeMenu:110` **and** `WorkshopScreen:75`
   (UpgradeCategory), `StoreScreen:202` (CosmeticCategory), `Rarity:65` + `UltimateWeaponScreen:113`
   (rarity). The engine literal **fallback** may retain raw English (degraded, `strings==null` only; 3c).
4. Wave composition, boss countdown, claim-celebration, milestone-reward, and supply-row labels are built
   from **templated/plural resources**; zero user-facing `+`/`joinToString` *sentence* assembly at those
   sites (the multi-reward list-join via the `reward_join` separator resource is the accepted, documented
   form, IC-3).
5. `domain/` remains Android-free (`DomainPurityTest` green); strengthened `NoRawEnumNameInUiTest` green;
   `HardcodedText` lint green.
6. `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. English output unchanged
   except where grammatically wrong.
7. No schema / economy / engine-formula change.

## 8. Risks for the plan stage

- **R1 — celebration contract churn.** Enumerate all 4 `ClaimCelebrationEvent(` construction sites + the
  composable body. Type name unchanged → screens untouched (FZ-4). New tests are stronger (structured).
- **R2 — wave-composition ordering.** Pass the map through unchanged; **do not sort**; pin a BOSS-first
  boss-wave case in `AndroidStringsTest` (FZ-2).
- **R3 — `Milestone.rewardsSummary()` removal.** Two consumers (`MissionsScreen:165` row + milestone
  celebration). Plan moves both to the shared Compose formatter; confirm no other consumer.
- **R4 — `reward_join` contract.** Plan pins the separator value, `+`-placement, and 0/1/2/3-item behavior
  (IC-3).
- **R5 — `notif_step_content` split.** Verify the parent template composes byte-identically in English and
  the #43 balance fold is preserved (3i).
- **R6 — `CosmeticDisplayInfo.category` type change** (String→enum). Confirm no other consumer of that
  field beyond `StoreScreen:202`.

## 9. Issue-closure traceability (review SC-4)

- **#259** — fully addressed by this PR (all count-driven plural sites in 3a). Closable.
- **#260** — **partially** addressed: the grammar/enum-name + concatenation evidence (`GameEngine:840`,
  `MissionsViewModel:145/160`, raw-enum sites) is fixed; its **prose** evidence (`OnboardingSlide:32-54`,
  `HelpScreen:28-96`) is deferred to **#34** per §1. **#260 stays OPEN** after this PR with a note that
  its remaining scope is #34 prose extraction (or is split into a closed "grammar/enum" part + an open
  "prose" part). Do **not** auto-close #260 on this PR.
- **#34** — remains open (bulk phase-2 extraction, incl. Help + onboarding prose + `toDisplayName` enums).
