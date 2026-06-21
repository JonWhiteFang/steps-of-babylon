# Spec — i18n Correctness Wave (#259 plurals · #260 concatenation + raw enum names)

- **Date:** 2026-06-21
- **Issues:** #259 (no plurals — quantity nouns grammatically wrong at n=1 and for inflected languages), #260 (translation-hostile string concatenation + raw `enum.name` surfaced to users)
- **Builds on:** ADR-0014 / V1X-13 (i18n phase 1, *shipped*) — the `domain/Strings` seam, `data/AndroidStrings` impl, the populated `strings.xml` (80 entries), and the `HardcodedText` lint-as-error guard.
- **Status:** Draft → pending Adversarial Review Gate.

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
- The Help screen's 8 `+`-concatenated bullet blocks (`HelpScreen.kt:28–96`). That is English *prose*,
  not a grammatical or enum-name bug — leave to #34.
- Abbreviation/relative-time sites ("5m ago", "3h", "min", "PS", "k", "Nd Hh"). The unit is masked so
  there is no grammatical bug; they are presentation-format concerns for #34.

**No schema / economy / engine-formula change.** Behaviour-preserving for English output except where the
output was *grammatically wrong* (the bug being fixed).

## 2. Ground truth (verified during brainstorming)

- `app/src/main/res/values/strings.xml` already holds **80 `<string>` entries** (battle HUD, all
  overlays, notifications, #214 a11y). #34's "only one entry" premise is **stale**. There are **zero
  `<plurals>`** and **no `values-*/` locale folders**.
- The engine string seam exists: `domain/Strings` (interface, Android-free) + `data/AndroidStrings`
  (`Context.getString`-backed), wired via `GameEngine.strings: Strings?` with a literal fallback so
  `GameEngineTest` stays pure-JVM (ADR-0014 §3).
- `HardcodedText` lint is **error-level** (`app/build.gradle.kts` ~L202–210) but **XML-only** — it does
  not flag Compose `Text("literal")` (documented in-code; ~110 phase-2 Compose literals still pass).
- `unitTests.isReturnDefaultValues = true` and `unitTests.isIncludeAndroidResources = true`; Robolectric
  is on `testImplementation`. So resource-backed strings/plurals are testable on the JVM lane via
  Robolectric (no emulator).
- Enum display today: raw `enum.name` reflected through `presentation/ui/EnumDisplayName.kt`
  `String.toDisplayName()` (UPPER_SNAKE → Title Case). **No `@StringRes` label map, no domain label
  field** (sole exception: `Milestone.displayName`, a hardcoded English `String`).
- The `#225` guard `NoRawEnumNameInUiTest` fails the build on any `.name.replace(` in `presentation/`.
  It does **not** ban bare `.name` surfacing — that is exactly several of the #260 sites.

## 3. Scope inventory (the exact sites this wave fixes)

### 3a. Plural sites → `res/values/plurals.xml` (#259)

Count-driven full nouns where singular/plural is visibly wrong. Each `<plurals>` ships `one` + `other`
(English); the structure enables `few`/`many`/`zero` per future locale.

| plural key | nouns / example today (wrong) | consumers |
|---|---|---|
| `fx_step_reward` | "+1 Step" / "+3 Step" | engine float (via `Strings.stepReward`) |
| `steps_earned_banner` | "👟 +1 Steps" | `BattleScreen` battle HUD banner |
| `wave_enemies` | "…· 1 enemies" | `battle_wave_header` (split — see 3d) |
| `boss_in_waves` | "Boss in 1 waves" | engine `bossCountdownLabel` (via seam) |
| `reward_gems` | "+1 Gems" | mission/milestone/supply reward (Component 4) |
| `reward_power_stones` | "+1 Power Stones" | mission/milestone/supply reward |
| `reward_steps` | "+1 Steps claimed!" | supply reward |
| `card_copies` | "+1 Copy" | `CardsScreen` |
| `days_remaining` | "1 days remaining" | `StoreScreen` season pass |
| `page_x_of_n` | "Page 1 of 4" | onboarding page-dots a11y |
| `widget_steps` | "N steps" | `StepWidgetProvider` |
| `reminder_steps_away` | "N steps from upgrading…" | `SmartReminderManager` |
| `notif_today_steps` | "Today: N steps" (split from `notif_step_content`) | `StepNotificationManager` |

Existing flat strings that bake a noun are **migrated to plurals** or split: `fx_step_reward`,
`steps_earned_banner`, `postround_power_stones`, `battle_wave_header`, `notif_step_content`.

`notif_step_content` ("Today: %1$d steps | Balance: %2$d Steps") packs **two** quantity nouns + a
"Balance" label in one format string → split into `notif_today_steps` (plural) + a balance piece,
composed by a parent format string `notif_step_content` with `%1$s | %2$s` so the two halves are each
plural-aware and word-order is in the resource, not code.

### 3b. Plural consumption paths (both already in-repo)

- **Compose (main thread):** `pluralStringResource(R.plurals.x, count, count)`.
- **Off-Compose** (engine via the `Strings` seam; notifications, widget, reminder — `Context`/`Resources`
  in scope): `resources.getQuantityString(R.plurals.x, count, count)`.

### 3c. Engine seam extension (#260 off-thread strings)

`GameEngine` runs on the game-loop thread; `stringResource` is not callable. Extend the established
seam — **add to `domain/Strings`** (interface, stays Android-free) and implement in `data/AndroidStrings`:

```kotlin
// domain/Strings.kt (additions)
fun enemyTypeName(type: EnemyType): String                 // localized enemy noun (replaces raw .name)
fun waveComposition(counts: Map<EnemyType, Int>): String   // whole "Next: 12 Basic, 4 Ranged, 1 Boss"
fun bossCountdown(waves: Int): String                      // plural-correct "Boss in N waves" / "Boss next wave"
```

- `waveComposition` builds the line from a **templated resource with a list placeholder**
  (`R.string.wave_composition` = "Next: %1$s"), where the list is `getQuantityString(R.plurals.…)` per
  entry joined by a `wave_composition_separator` resource — **no `+` concat in code**. Per-entry text uses
  `enemyTypeName(type)` + the per-type count (a `wave_comp_entry` resource `"%1$d %2$s"` so number/noun
  order is localizable).
- `EnemyType` is a **domain enum** referenced by the seam method's *signature* — `domain/Strings`
  already depends on `domain/model`, so this stays within the domain layer (no Android import). The
  `@StringRes` mapping lives in `data/AndroidStrings` (a `when (type)` → `R.string.enemy_basic` etc.),
  not in the domain enum.
- `GameEngine.nextWaveCompositionLabel()` / `bossCountdownLabel()` call
  `strings?.waveComposition(...) ?: <literal fallback>` and `strings?.bossCountdown(w) ?: <literal>` —
  exactly the ADR-0014 pattern; the literal fallback keeps `GameEngineTest` pure-JVM and byte-identical
  to today's English output.
- **`WaveAnnouncement` (Canvas-drawn, render thread):** route the noun/sentence banners through the seam
  too — "BOSS INCOMING" (literal seam string) and the "Next Wave: Ns" countdown unit. The plain **"Wave
  N"** banner and the **`N / M` HP bar** stay numeric literals (no plural noun, no grammar) — *not* routed.

### 3d. Raw-`.name` enum labels → real localized display-names (#260)

Only enums that currently surface raw `CONSTANT_CASE` to users. Domain can't reference `R`, so resolution
is by call-site:

| enum | site (raw today) | fix |
|---|---|---|
| `EnemyType` | `GameEngine.kt:840` wave composition | `Strings.enemyTypeName()` seam (3c) |
| `WavePhase` | `BattleScreen.kt:213` `lowercase().replaceFirstChar` | presentation `@StringRes` map, `stringResource` |
| `PackTier` | `CardsScreen.kt:117` `Text(pack.tier.name)` | presentation `@StringRes` map |
| `UpgradeCategory` | `InRoundUpgradeMenu.kt:110` tab label `cat.name` | presentation `@StringRes` map |
| `CosmeticCategory` | `StoreViewModel.kt:80` → `StoreScreen.kt:200` | presentation `@StringRes` map (resolve in screen, not VM) |
| `CardRarity` | `Rarity.kt:65` badge label (raw `.name`) | presentation `@StringRes` map |

**Mechanism:** a presentation-layer extension per enum, e.g. `@StringRes fun WavePhase.labelRes(): Int`,
resolved with `stringResource(enum.labelRes())` at the Compose call site. Keeps the domain enum
Android-free; the `@StringRes` table lives in `presentation/ui/` (the design the surface-map endorsed).
New strings: `enemy_basic/fast/tank/ranged/boss/scatter`, `wave_phase_spawning/cooldown`,
`pack_tier_common/rare/epic`, `upgrade_cat_attack/defense/utility`,
`cosmetic_cat_ziggurat_skin/projectile_effect/enemy_skin`, `rarity_common/rare/epic`.

**Guards preserved:** the `BattleScreen` `state.wavePhase == "SPAWNING"` **color** comparison keeps reading
the raw enum string from `uiState` — only the *displayed text* is localized, so the color branch is
untouched. `toDisplayName()` stays for the out-of-scope enums.

**`StatsViewModel.kt:100` `DayOfWeek.name.take(3)`** chart axis: use `java.time` locale-aware short names
(`DayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())`) — no new resource, already localized by
the JDK. (A chart axis, not prose; acceptable.)

### 3e. Concatenation seams (#260)

| site | today | fix |
|---|---|---|
| `GameEngine.kt:840` wave composition | `"Next: " + joinToString(", "){ "${v} ${k.name}" }` | `Strings.waveComposition()` (3c) |
| `GameEngine.kt:852` boss countdown | `if (w==1) "Boss next wave" else "Boss in $w waves"` | `Strings.bossCountdown()` (3c) |
| `MissionsViewModel.kt:145/160` + `missionRewardLabel` | `parts.joinToString(" ") + " claimed!"` | structured payload (Component 4) |
| `UnclaimedSuppliesViewModel` `supplyLabel` | `"+${n} Gems claimed!"` etc. | structured payload (Component 4) |
| `Milestone.rewardsSummary()` (domain) | `joinToString(", "){ "${amount} Gems" }` | structured payload (Component 4) |

### 3f. Celebration payload contract (Component 4 — the one real blast-radius change)

`ClaimCelebrationEvent(val label: String)` becomes a **structured** payload formatted at the Compose
boundary (so it is plural-correct + localizable):

```kotlin
sealed interface ClaimReward {
    data class Currencies(val gems: Int = 0, val powerStones: Int = 0, val steps: Long = 0) : ClaimReward
    data object Card : ClaimReward
    data object Generic : ClaimReward
}
data class ClaimCelebrationEvent(val reward: ClaimReward)
```

- `MissionsViewModel` / `UnclaimedSuppliesViewModel` emit the structured value — **no English assembly in
  VM/domain**. The `Channel.CONFLATED` flow, `receiveAsFlow()`, and the `@VisibleForTesting cancelForTest()`
  ticker harness are **unchanged** — only the payload *type* changes.
- The `ClaimCelebration` composable formats `ClaimReward` → text via `pluralStringResource` +
  `R.string.reward_claimed` ("%1$s claimed!" — full template, word order in the resource). Multiple
  currencies join via a `reward_join` resource, not `+`.
- `Milestone.rewardsSummary()` (domain `String`) is **replaced** by a structured accessor (e.g.
  `rewards: List<MilestoneReward>` already exists) consumed at the Compose boundary; the
  `MissionsScreen` milestone row formats it the same way.

## 4. Architecture & data flow

```
                         ┌─────────────────────────────────────────────┐
  res/values/            │ strings.xml  (+ enum labels, split notif,     │
  ├ strings.xml          │              full-sentence templates)         │
  └ plurals.xml  ◄───────┤ plurals.xml  (count-driven nouns: one/other)  │
                         └─────────────────────────────────────────────┘
        ▲ getQuantityString                    ▲ pluralStringResource / stringResource
        │ (Resources, off-Compose)             │ (Compose, main thread)
        │                                       │
  ┌─────┴───────────────┐          ┌────────────┴───────────────────────────────┐
  │ data/AndroidStrings │          │ presentation (Compose call sites)            │
  │  enemyTypeName       │          │  WavePhase/PackTier/UpgradeCategory/          │
  │  waveComposition     │          │  CosmeticCategory/CardRarity .labelRes()      │
  │  bossCountdown       │          │  CardsScreen / StoreScreen / InRoundMenu /    │
  │  (+ existing fx_*)   │          │  BattleScreen                                 │
  └─────┬───────────────┘          │  ClaimCelebration(ClaimReward) ── formats ────┤
        │ implements                │  VMs emit ClaimReward (structured, no English)│
  ┌─────┴───────────────┐          └──────────────────────────────────────────────┘
  │ domain/Strings (port)│
  │  (Android-free)      │ ◄── GameEngine.strings?.…() ?: <literal fallback>  (game-loop thread)
  └──────────────────────┘
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │ service: StepNotificationManager / SmartReminderManager / StepWidgetProvider   │
  │   getQuantityString(R.plurals.…) — Context/Resources in scope, no Compose      │
  └──────────────────────────────────────────────────────────────────────────────┘
```

## 5. Testing strategy (JVM/Robolectric lane — no emulator)

- **`PluralsResourceTest`** (Robolectric): for each new plural, assert `n=1` → `one` form and `n=2` →
  `other` form render correctly. Pins the exact regression ("+1 Step" not "+1 Steps"; "1 day" not
  "1 days"). Mutation: swapping a quantity arg or deleting a `one` form breaks it.
- **`AndroidStringsTest`** (Robolectric, extend): `enemyTypeName` (all 6 `EnemyType`), `waveComposition`
  (multi-entry, order + separators), `bossCountdown` (n=1 vs n=2). Asserts localized + plural-correct
  output; no raw `.name` substring appears.
- **`GameEngineTest`** (pure-JVM, existing): literal fallbacks unchanged → existing assertions stay green
  with zero churn; add one test that when `strings` is set, the seam value is used (FakeStrings).
- **Celebration:** VM test asserts the **structured** `ClaimReward` emission (count + type), not a string.
  A Robolectric test on `ClaimCelebration`/the formatter asserts `ClaimReward → localized text`.
  **Retire** `SupplyRewardFormatTest` + the `missionRewardLabel` literal assertions; replace with the
  structured-payload tests.
- **`EnumLabelResTest`** (Robolectric or pure where possible): iterate each in-scope enum's `entries`,
  assert every constant maps to a non-blank string (catches a missing mapping when a constant is added).
- **`NoRawEnumNameInUiTest`** (#225): stays green (we use `stringResource`/seam, never `.name.replace(`).
  *Optional, decided in plan:* strengthen it to also flag raw `.name`/`.name.take(`/`.name.lowercase(`
  surfacing at the now-fixed sites so the lower-quality pattern can't drift back.

## 6. Fragile zones honored (from STATE.md "do-not-touch")

- **ADR-0014 engine `Strings` seam + literal fallback** — *extended*, not broken; fallbacks byte-identical
  to current English; `GameEngineTest` stays Robolectric-free.
- **`GameEngine.entities`/`uwStates` `entitiesLock` + game-loop crash guard (#118/#190/#191)** — only
  string *emission* is touched; no shared-collection structural mutation, no lock-order change.
- **`MissionsViewModel` `_today` ticker (#195) + `cancelForTest()` (#162) + `Channel.CONFLATED`** —
  payload *type* changes only; flow mechanics + ticker + test harness untouched.
- **`formatCurrency` `Locale.US` grouping (#160)** — untouched (numeric grouping, not a noun).
- **Onboarding model purity (ADR-0021/0024)** — the only onboarding touch is the `page_x_of_n` plural at
  the **screen** level (`pluralStringResource`); the pure `OnboardingSlide` model is **not** touched (slide
  body copy is #34 prose, out of scope).
- **`HardcodedText` lint** stays error-level; new XML resources comply.
- **#225 `NoRawEnumNameInUiTest`** stays green.

## 7. Acceptance criteria

1. `res/values/plurals.xml` exists; every site in 3a renders the correct form at `n=1` and `n≥2`.
2. No reachable "1 days"/"+1 Steps"/"1 enemies" grammatical error remains at the in-scope sites.
3. The #260 evidence sites surface **localized display names**, never raw `CONSTANT_CASE`
   (`GameEngine:840`, `BattleScreen:213`, `CardsScreen:117`, `InRoundUpgradeMenu:110`, `StoreScreen:200`,
   `Rarity:65`).
4. Wave composition, boss countdown, and the claim-celebration / milestone-reward labels are built from
   **full templated resources**, with zero user-facing `+`/`joinToString` sentence assembly at those sites.
5. `domain/` remains Android-free (`DomainPurityTest` green); `NoRawEnumNameInUiTest` green;
   `HardcodedText` lint green.
6. `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL. English output is
   unchanged except where it was grammatically wrong.
7. No schema / economy / engine-formula change. #34 and the Help-screen prose remain tracked-open.

## 8. Risks & open questions for the plan stage

- **R1 — celebration contract churn.** Changing `ClaimCelebrationEvent` touches both VMs + the composable +
  their tests. Mitigated: flow mechanics unchanged; the change is type-only and the new tests are
  stronger (structured, not literal). Plan must enumerate every `ClaimCelebrationEvent(` construction site.
- **R2 — `waveComposition` ordering.** `Map<EnemyType, Int>` iteration order must match today's output
  (insertion order from `WaveSpawner.getWaveComposition`). Plan: pass a `LinkedHashMap`/ordered structure
  or sort by `EnemyType.ordinal` deterministically; pin in `AndroidStringsTest`.
- **R3 — `Milestone.rewardsSummary()` consumers.** It is surfaced in `MissionsScreen` *and* the milestone
  celebration. Plan must find all consumers before replacing the domain `String` accessor.
- **R4 — plural in a non-Compose, non-Resources path.** Verify every off-Compose consumer
  (`SmartReminderManager`, `StepWidgetProvider`, notif managers) has a `Resources`/`Context` handle for
  `getQuantityString` (they do, per the surface map — confirm in plan).
- **R5 — `notif_step_content` split.** Splitting a shipped notification string must preserve the exact
  `%1$d/%2$d` data and the #43 last-known-good balance fold; plan verifies the parent template composes
  identically in English.
```
