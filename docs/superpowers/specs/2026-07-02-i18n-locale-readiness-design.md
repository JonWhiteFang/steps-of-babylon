# i18n Locale-Readiness (phase 3, final) — Design Spec

**Date:** 2026-07-02
**Issue:** #34 (i18n) — phase 3, the final externalization pass.
**Predecessors:** phase 1 (V1X-13 — battle-engine `Strings` seam + `strings.xml` bootstrap) and phase 2
(#354/#355 — Compose-screen `stringResource` extraction). Both shipped. ADR-0014 is the governing decision.
**Status:** design — pending Adversarial Review Gate before a plan is written.

---

## 1. Goal

Extract **every remaining user-facing English string literal** in the app so that adding a
`res/values-<locale>/strings.xml` yields a fully-translated UI with **no English leaking through**.
This is the explicit prerequisite for "the first real non-English locale" (the payoff of the whole
i18n effort). After this phase, the only English literals remaining in the codebase should be
log messages, code comments, technical identifiers (route names, SharedPreferences keys, SKU ids,
intent extras), and data-only interpolations (`"$count"`, `"${x}%"`, glyphs like `"—"`/`"✕"`).

**Non-goal:** authoring an actual translation. This phase is English-only extraction; the second
`values-xx` file is a separate follow-up.

**Non-goal:** any behavior change. The rendered English text stays byte-identical (modulo where a
plural/units form is deliberately introduced — see §6.D). Test count stays **1282 JVM** (assertions
updated in place; a small `UiMessageTest` may add a handful — the plan will pin the exact number).

---

## 2. Scope — the seven categories

Phase-2's plan deferred "Canvas/Activity" as PR3. Investigation for this spec found the remaining
surface is broader than that shorthand and clusters into seven categories. Each has a defined
mechanism (below). Categories were confirmed by code-grounded search on `main` @ `406df54`.

| # | Category | Files (representative) | Count (approx) | Mechanism |
|---|---|---|---|---|
| **A** | ViewModel `_userMessage` strings | Store/Cards/Workshop/Labs VMs | ~20 | Sealed `UiMessage` + `@StringRes` |
| **A′** | Billing error messages (data layer) | `data/billing/BillingManagerImpl.kt` | 8 + `toUserMessage()` | `context.getString` (impl already has `@ApplicationContext`) |
| **B** | Bottom-nav labels | `presentation/navigation/Screen.kt` | 14 | `label: String` → `@StringRes labelRes: Int` |
| **C** | `SCREEN_LOAD_ERROR` const | `presentation/ui/ErrorState.kt` (+ 11 VM `.catch` sites) | 1 | `String` const → `@StringRes Int` |
| **D** | Duration / units / plurals | LabsScreen, UnclaimedSuppliesScreen, economy/UW/stats | ~7 | `plurals` + units; extend `domain/Strings` where non-Composable |
| **E** | Battle Canvas text | `battle/effects/WaveAnnouncement.kt`, `battle/engine/BattleRenderer.kt` | ~4 | Extend `domain/Strings` seam |
| **F** | Activity literals | `HealthConnectPermissionActivity.kt` | 2 (incl. long policy body) | `getString` (Activity has `Context`) |

Categories A′ and F' long body are the two items STATE.md's PR3 shorthand under-counted; both are real
leaking English and are in scope for a "100% locale-ready" target (confirmed with the developer).

**Explicitly OUT of scope (leave alone — data / technical / already-done):**
- Data-only interpolations: `Text("$count")`, `"${tier.cashMultiplier}x"`, `"$pct%"`, `"$badgeCount"`.
- Glyphs already handled or non-translatable: `"—"`, `"✕"` (extracted in phase 2), `"+"`.
- Notification strings — already externalized in phase 1 (`StepNotificationManager`, `Milestone…`,
  `SupplyDrop…`, `SmartReminder…` all use `context.getString(R.string.…)`; verified).
- `SupplyDropTrigger.message` (domain model field) — the message text is authored data on the
  domain model, delivered by push; localizing walking-encounter copy is a content decision, not a
  string-extraction mechanic. Out of scope; note it in the plan as a known residual.
- Log messages, route strings, SKU ids, SharedPreferences keys, intent extras.

---

## 3. Existing patterns this phase reuses (do NOT reinvent)

Two seams already exist from prior phases and are the load-bearing precedent:

1. **`domain/Strings` (interface) + `data/AndroidStrings` (impl).** Pure-Kotlin interface in the
   domain layer (no Android imports) exposing display-string methods (`healHp`, `cashReward`,
   `waveComposition`, `bossCountdown` — the last already uses `getQuantityString` for plural
   correctness). The production impl is resource-backed and constructed from a `Context` by
   `GameSurfaceView` (no Hilt binding). This keeps `GameEngineTest`/`SimulationTest` pure-JVM. **This
   is the mechanism for categories D (non-Composable helpers) and E (Canvas).**

2. **`@StringRes Int` return + call-site `stringResource`.** Established by phase-2 Task 13/14
   (`Screen.secondaryTitle`, `CurrencyType.label()`, `pathLabel()`) for functions/values that must
   stay non-`@Composable` but map to a resource. **This is the mechanism for categories B and C, and
   the shape of the `UiMessage` type in A.**

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
    // … one case per distinct message (list finalized in the plan by grepping all _userMessage sites)

    /**
     * Escape hatch for a message whose text originates below the presentation layer already
     * localized (e.g. a billing PurchaseResult.Error whose message came from AndroidStrings /
     * context.getString in the data layer — category A′). Carries the resolved String verbatim.
     */
    data class Raw(val text: String) : UiMessage {
        override val resId: Int get() = 0 // unused; see resolve()
    }
}
```

`UiState.userMessage: String?` becomes `userMessage: UiMessage?` on each affected UiState
(Store/Cards/Workshop/Labs). The screen's `LaunchedEffect` resolves:

```kotlin
LaunchedEffect(state.userMessage) {
    state.userMessage?.let { msg ->
        val text = when (msg) {
            is UiMessage.Raw -> msg.text
            else -> context.getString(msg.resId, *msg.args.toTypedArray())
        }
        // existing snackbar / host call, unchanged
    }
}
```

(A tiny `UiMessage.resolve(context)` extension in `UiMessage.kt` can encapsulate the `when` so the
four screens don't duplicate it. `context` is obtained via `LocalContext.current` at the composable,
which the screens that show messages already have or trivially add.)

### Why `Raw` exists
Category A′: `BillingManagerImpl` produces already-localized error strings (it has a `Context`) and
returns them in `PurchaseResult.Error(message)`. The Store/Cards VMs forward that via
`UiMessage.Raw(result.message)` — no re-localization, no double-lookup. `Raw` is the *only* sanctioned
place a runtime `String` enters `UiMessage`; new static messages MUST be a typed case, not `Raw`.

### Testing
- VM tests (fakes-based, pure JVM) assert the emitted **type** — e.g.
  `assertEquals(UiMessage.NotEnoughGems, state.userMessage)` — never English text. This is *more*
  robust than the current string-equality assertions.
- New `UiMessageTest` (Robolectric, JVM lane): iterate every non-`Raw` case, assert
  `context.getString(case.resId, …)` is non-blank and (for arg-bearing cases) that the format
  succeeds. Guards against a case pointing at a missing/wrong-arity resource.

---

## 5. Category A′ — billing error messages (data layer)

`data/billing/BillingManagerImpl.kt` constructs `PurchaseResult.Error("…English…")` at 8 sites plus a
`SdkBillingResult.toUserMessage()` mapper. The impl already injects `@ApplicationContext context`
(the data layer is permitted Android deps). Replace each English literal and each `toUserMessage`
branch with `context.getString(R.string.billing_error_…)`. No signature change to `PurchaseResult`
(it stays `Error(message: String)` — the string is now localized at its source). The VM forwards it
via `UiMessage.Raw`. `BillingManagerImplTest` assertions that check `message` text switch to checking
the resource-resolved value (Robolectric) or the `PurchaseResult.Error` *shape*, per the plan.

---

## 6. Categories B–F — mechanical, following established patterns

**B. Bottom-nav labels (`Screen.kt`).** Change the sealed-class constructor param
`label: String` → `@StringRes labelRes: Int`; each `data object` passes an `R.string.nav_*` id.
Consumers are **only** `BottomNavBar.kt` (`Text(screen.label)` + `contentDescription = screen.label`),
which resolve via `stringResource(screen.labelRes)`.
**Fragile-zone discipline (#161):** touch ONLY the label field. Do NOT alter `route`, `icon`, or the
`by lazy` lists (`items`, `allScreens`, `argumentFreeRoutes`), `fromRoute`, `startDestination`, or
`secondaryTitle`. `DeepLinkRoutingTest` + `ScreenSecondaryTitleTest` must stay green untouched. All 14
objects get a label (even non-tab screens carry one today); keep parity to avoid a partial migration.

**C. `SCREEN_LOAD_ERROR`.** Today a `String` const in `ErrorState.kt`, assigned into
`UiState.error: String?` by 11 VMs' `.catch { emit(state(error = SCREEN_LOAD_ERROR)) }`. Convert the
error channel to a `@StringRes Int`: `error: Int?` on each UiState, VMs emit
`R.string.screen_load_error`, and `ErrorState`/the screen early-return resolves with `stringResource`.
**Fragile-zone discipline (#194, ADR-0028):** the `.catch` MUST stay INSIDE `flatMapLatest` (a
downstream catch makes `retry()` a no-op). This change only swaps the *value* emitted; do NOT move the
`.catch`. `StatsViewModelTest`'s throw→error / retry→recover assertions switch to asserting `error ==
R.string.screen_load_error` (Int) instead of the English string.

**D. Duration / units / plurals.**
- `LabsScreen.formatTime` (`"${h}h ${m}m ${s}s"` / `"${m}m ${s}s"` / `"${s}s"`) and its `"Done!"`
  early-return (already converted to a param in phase-2 Task 14) — the unit-suffixed forms are
  composed in a non-`@Composable` helper. Move formatting behind a `domain/Strings.labDuration(...)`
  method (resource + `getQuantityString` where a language needs plural units) OR, since the call site
  is a Composable, resolve unit strings via `pluralStringResource`/`stringResource` and pass in. The
  plan picks one per call site; prefer the Composable-local resolution when the caller is already
  `@Composable`, and the `Strings` seam only where it is not.
- `UnclaimedSuppliesScreen.formatTimeAgo` (`"${m}m ago"` / `"${h}h ago"` / `"${d}d ago"` + `"Just
  now"` param from Task 14) — same treatment; the "ago" forms want `plurals`.
- `CurrencyDashboardViewModel` `"${days}d ${hours}h"` (weekly reset) — VM is non-Composable; either a
  `@StringRes`-carrying UiState field with args, or a `domain/Strings` method. Plan decides; prefer
  keeping the raw numbers in state and formatting in the Composable to avoid a `Strings` inject into
  this VM.
- `UltimateWeaponScreen` `"${v.toInt()}s"` and `StatsScreen` `"$minutes min"` — Composable-local
  `stringResource`/`pluralStringResource`.
Where a plural is introduced, English `<plurals>` supplies `one`/`other`; the *rendered English* must
match today's text (e.g. today's `"1m ago"` stays `"1m ago"` — a plural entry can encode that
identically). No visible English change.

**E. Battle Canvas text.** `WaveAnnouncement.kt` (`"⚠ BOSS INCOMING"`, `"Wave $wave"`, `"Next Wave:
${t}s"`) and `BattleRenderer.kt` (`bossCountdownLabel` is already provided; `"$pct%"` is data-only,
leave it). Extend the `domain/Strings` interface with `bossIncoming()`, `waveHeader(wave: Int)`,
`nextWaveIn(seconds: Int)`; implement in `AndroidStrings` via `getString`. These renderers already
receive text through the engine's `strings` provider (the `?: "fallback"` pattern at
`WaveAnnouncement`/`BattleRenderer`), so wiring is the same as phase-1's `waveComposition`. The
`⚠` glyph stays in the resource value byte-identical. Pure-JVM engine tests unaffected (fake `Strings`).

**F. Activity literals.** `HealthConnectPermissionActivity` has two: the `"Privacy Policy"` heading
and the long multi-paragraph policy body (a triple-quoted string). Both are user-facing. Extract to
`R.string.hc_privacy_policy_title` and `R.string.hc_privacy_policy_body` (the body is a single
multi-line resource; preserve every line break and the `•` glyphs and URL/email byte-identical).
Resolve with `getString` (Activity has a `Context`) or `stringResource` inside its `setContent`.
`MainActivity` had no non-nav user-facing literals (verified). If the plan's methodology grep surfaces
any, extract them the same way.

---

## 7. Architecture & invariants

- **Clean architecture preserved.** `UiMessage` lives in `presentation/ui/` (it imports `R` +
  `@StringRes` — presentation-only). `Strings` additions stay in `domain/` (pure) with the impl in
  `data/`. No new Android import lands in `domain/`; `DomainPurityTest` stays green.
- **`PresentationPurityTest` unaffected** — no new `data.local` DAO/entity import in presentation.
- **No schema change, no economy/engine-formula change.** Purely a string-plumbing refactor.
- **Fragile zones touched, with named guards that must stay green:** `Screen.kt` `by lazy` lists
  (#161 — `DeepLinkRoutingTest`, `ScreenSecondaryTitleTest`); `#194`/ADR-0028 error-state
  `flatMapLatest`/`.catch` ordering (`StatsViewModelTest`); the battle `Strings` seam (#V1X-13 —
  `GameEngineTest` purity). Each is called out per-task in the plan.

---

## 8. Rollout — two PRs by risk

- **PR3a — low-risk plumbing:** A (UiMessage + the four VMs/screens) + A′ (billing) + C
  (SCREEN_LOAD_ERROR) + F (Activity). No fragile-zone edits beyond the ADR-0028 error-channel value
  swap (mechanical). New `UiMessageTest`.
- **PR3b — fragile / formatting:** B (Screen.kt nav labels — #161 zone) + D (duration/plurals) + E
  (battle Canvas — engine `Strings` seam). Heightened review; on-device visual spot-check for battle
  and nav.

Each screen/file follows phase-2's **methodology grep gate**: after editing, grep the file for
English literals and confirm only data-only/glyph/technical survivors remain. A final repo-wide sweep
(the phase-2 Task-15 grep, widened to `service`, `navigation`, `battle`, and the two Activities)
is the completeness gate for the whole phase.

---

## 9. Testing strategy

- Full JVM suite green at **1282** (+ any `UiMessageTest` cases — exact delta set in the plan).
- VM tests: assert `UiMessage` **type** and UiState `error: Int?` **id**, not English.
- `BillingManagerImplTest`: resource-resolved message or `Error` shape.
- `detekt` + `ktlint` green (baseline; watch `MaxLineLength` on new resource-arg call sites).
- Compose UI tests (`CardsScreenTest`, `OnboardingScreenTest`) — reconcile any assertion that targets
  a now-typed message; most target already-migrated phase-2 text and are unaffected.
- Battle + bottom-nav: on-device visual spot-check (no Compose-rule layout test for these — PR-4736).

---

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `Screen.kt` label change perturbs `by lazy` init order → NPE (#161) | Change only the label field; run `DeepLinkRoutingTest`/`ScreenSecondaryTitleTest`; do not touch route lists. |
| Moving the `.catch` value swap accidentally hoists `.catch` outside `flatMapLatest` (#194) | Swap value only; keep `.catch` inside; `StatsViewModelTest` retry-recover guard. |
| A plural/units change alters rendered English | English `<plurals>` encode today's exact text; visual diff. |
| `UiMessage.Raw` becomes a dumping ground | Doc + review rule: new static messages MUST be a typed case; `Raw` only for already-localized lower-layer strings. |
| Long HC policy body corrupted on extraction (line breaks/glyphs/URL) | Copy verbatim into one multi-line resource; diff the rendered output; escape apostrophes. |
| Missed literals (grep under-count, as in phase 2) | Methodology grep per file + final widened repo-wide sweep as the completeness gate. |

---

## 11. Open questions for review

- **D formatting home:** per-call-site choice (Composable-local `pluralStringResource` vs a
  `domain/Strings` method) — is the "Composable-local where possible, `Strings` only where the caller
  is non-Composable" rule the right default, or should everything route through `Strings` for
  consistency with the battle seam? (Recommendation: Composable-local default; it avoids injecting a
  provider into VMs that don't need one.)
- **`UiMessage.resolve(context)` location:** extension in `UiMessage.kt` vs inline `when` per screen.
  (Recommendation: the shared extension — DRY, one place to handle `Raw`.)
