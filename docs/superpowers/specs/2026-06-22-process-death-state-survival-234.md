# Design Spec — Process-death state survival (#234)

**Date:** 2026-06-22
**Issue:** #234 (`severity:major`, adversarial status `partial` — "No SavedStateHandle/rememberSaveable
anywhere — transient UI and in-flight state is lost on process death")
**Status:** Draft — pending Adversarial Review Gate (spec stage)
**Scope decision (developer):** Low-risk surfaces + pack-reveal via a presentation-layer `@Parcelize`
DTO; battle live-round/overlay OUT of scope; the "onboarding-finish bug" investigated and found NOT
real (excluded).

---

## 1. Problem

Android can kill a backgrounded process at any time; on relaunch the system recreates the task and
**ViewModels are NOT restored** (a ViewModel survives configuration change, not process death). Durable
game data is safe in Room, but several user-facing transient selections/overlays are silently dropped.
The codebase has **zero `SavedStateHandle` and zero `rememberSaveable` in main source** (verified) — so
*every* in-memory `MutableStateFlow` selection and every `remember{}` Compose flag resets to its default
on relaunch.

Grounded surface audit (file:line verified against HEAD):

| # | Surface | Location | Lost on death? | Reconstructible? |
|---|---|---|---|---|
| 1 | Workshop selected tab | `presentation/workshop/WorkshopViewModel.kt:48` (`_selectedCategory`, default `ATTACK`) | Yes | No (UI selection) |
| 2 | Stats selected period | `presentation/stats/StatsViewModel.kt:36` (`_selectedPeriod`, default `WEEK`) | Yes | No (UI selection) |
| 3 | Cards pack-reveal payload | `presentation/cards/CardsViewModel.kt:42` (`_lastPackResult: List<CardResult>?`) | Yes (overlay) | **No** (cards+gems ARE in Room, but the reveal set + per-card `isNew` badge are not derivable post-write) |
| 4 | `permissionAsked` flag | `presentation/MainActivity.kt:118` (bare `remember{}`) | Yes (resets to `false`) | No (it's a "have we shown the dialog this session" UX flag) |

> The issue cites `WorkshopViewModel.kt:43`; the actual declaration is **:48** (line 43 is an unrelated
> `QuickInvest` field). Stale-by-5; not material.

## 2. Goal & non-goals

**Goal:** make surfaces 1–4 survive process death using `SavedStateHandle` (ViewModels) +
`rememberSaveable` (Compose), **without violating the domain-purity invariant** (`domain/` has zero
Android imports — machine-enforced by `architecture/DomainPurityTest`).

**Non-goals (explicit, recorded at issue-close):**
- **Battle live-round + `RoundEndState` post-round overlay.** The live engine (`GameEngine`, `inRoundLevels`,
  HP, entity list) holds no serializable snapshot — not survivable without major work. The overlay
  payload (`BattleUiState.roundEndState`) is partially Room-backed (best-wave/PS/tier/stats are committed
  in `runEndRoundPersistence`'s transaction) but the per-round summary + ad-claim flags are transient;
  persisting just the overlay is Medium-High risk on the just-refactored `BattleViewModel` (#230/#231).
  Deferred to a follow-up.
- **"Onboarding-finish persistence bug" — NOT a real bug.** `OnboardingScreen.kt:107` calls
  `viewModel.completeOnboarding()` (which calls `OnboardingPreferences.setCompleted()`,
  `OnboardingViewModel.kt:38-39`) **before** `onFinished()` (`OnboardingScreen.kt:114`). Onboarding
  completion already survives process death via the `onboarding_prefs` SharedPreferences store
  (`MainActivity.kt:116` re-reads `hasCompletedOnboarding()` on relaunch). The `onboardingComplete = true`
  at `MainActivity.kt:338` is a redundant in-session nav flag. **No change.**
- No new persistence surface (Room/Preferences) for transient UI — `SavedStateHandle` is the
  process-death-survival mechanism; it does not (and should not) become durable cross-launch storage.

## 3. Hard invariants

1. **Domain purity.** `domain/` keeps zero Android imports. `CardResult` (`domain/usecase/OpenCardPack.kt`)
   and `CardType` (`domain/model/CardType.kt`) are **not** `@Parcelize`d (that would import
   `kotlinx.parcelize`/`android.os.Parcelable` → `DomainPurityTest` fails the build, forbidden prefixes
   `android.`/`androidx.`). The only Android-coupling added lives in `presentation/`.
2. **Behavior preservation for the selections.** The two `_selected*` flows are consumed as `combine`
   *sources* (NOT `flatMapLatest` keys): `WorkshopViewModel.kt:57-63` combine inside
   `_retry.flatMapLatest{}` (:56); `StatsViewModel.kt:51` combine inside
   `combine(_today,_retry).flatMapLatest{}` (:42-43). Replacing the `MutableStateFlow` with
   `SavedStateHandle.getStateFlow(key, default)` is a drop-in — same `StateFlow<T>`, same default, same
   downstream combine. No emission-timing change.
3. **Pack-reveal render contract unchanged.** The DTO↔UI mapping must preserve every field the
   `CardsScreen` reveal UI reads (`CardType`, `isNew`, `copiesAwarded`) 1:1. The plan pins
   `CardsUiState.lastPackResult`'s exact type + the screen's read sites before implementation.
4. **Hilt DI unchanged in shape.** The **three** affected ViewModels (Workshop/Stats/Cards) are
   `@HiltViewModel` + `@Inject constructor`, obtained via `hiltViewModel()` — Hilt auto-provides
   `SavedStateHandle` to the constructor, so adding the param requires no factory/module change.
   Surface 4 (`permissionAsked`) is a Compose `remember{}` flag in `MainActivity` (an
   `@AndroidEntryPoint` Activity, not a ViewModel), fixed via `rememberSaveable` (§4) — it does NOT get
   a Hilt-injected `SavedStateHandle`.

## 4. Per-surface design

### Surfaces 1 & 2 — selected tab / period (drop-in)
Add `savedStateHandle: SavedStateHandle` to the constructor. Replace:
```kotlin
private val _selectedCategory = MutableStateFlow(UpgradeCategory.ATTACK)
```
with:
```kotlin
private val selectedCategory: StateFlow<UpgradeCategory> =
    savedStateHandle.getStateFlow(KEY_SELECTED_CATEGORY, UpgradeCategory.ATTACK)
```
- Setter `selectCategory(category)` becomes `savedStateHandle[KEY_SELECTED_CATEGORY] = category`.
- The `combine(...)` block reads `selectedCategory` (the new flow) instead of `_selectedCategory`.
- Enums round-trip through `SavedStateHandle` (Serializable). Key is a `private const val`.
- Identical for `StatsViewModel`: key `selectedPeriod`, default `StatsPeriod.WEEK`, setter `selectPeriod`.

### Surface 3 — pack-reveal (presentation-layer Parcelable DTO)
New file `presentation/cards/PackRevealState.kt`:
```kotlin
@Parcelize
data class PackRevealState(val cards: List<RevealedCard>) : Parcelable

@Parcelize
data class RevealedCard(
    val cardTypeName: String,   // CardType.name — enum round-trips by name
    val isNew: Boolean,
    val copiesAwarded: Int,
) : Parcelable
```
`CardsViewModel`:
- Add `savedStateHandle: SavedStateHandle` to the constructor.
- On pack open (`openPack` :98 / `watchFreePackAd` :147): after computing the domain `List<CardResult>`,
  map → `PackRevealState` and write `savedStateHandle[KEY_PACK_REVEAL] = packRevealState`.
- `dismissPackResult()` (:136): `savedStateHandle[KEY_PACK_REVEAL] = null`.
- The combine source (the `_lastPackResult` argument in the `combine(...)` block, ~:53) switches to
  `savedStateHandle.getStateFlow<PackRevealState?>(KEY_PACK_REVEAL, null)`, mapped inside the combine to
  **`List<CardResult>?`** — `CardsUiState.lastPackResult` is `List<CardResult>?` and `CardsScreen`
  (~:150-170) reads `r.type`/`r.isNew`/`r.copiesAwarded` directly, so the screen + uiState type stay
  **unchanged**; the DTO is purely the SavedStateHandle serialization layer (DTO → `List<CardResult>` on
  read, `List<CardResult>` → DTO on write).
- Mapping functions are pure + unit-testable: `List<CardResult>.toPackRevealState()` and
  `PackRevealState.toUiModel()` (or to `List<CardResult>` if the UI model IS `CardResult` — pinned in
  plan). `CardType` encodes as `.name`; decodes via `CardType.valueOf(name)`.

> The reveal payload is genuinely transient and **not** faithfully reconstructible from Room (once the 3
> cards are written, you can't tell which-3-were-just-revealed nor their per-card `isNew`). The economic
> outcome (cards + gems) is already durable in Room (`OpenCardPack` writes via `openCardPackAtomic`
> BEFORE setting `_lastPackResult`, #236). This surface persists the **visual confirmation**, which #234
> explicitly prioritises ("the pack reveal is the sharpest case").

### Surface 4 — permissionAsked (rememberSaveable)
`MainActivity.kt:118`: `var permissionAsked by remember { mutableStateOf(false) }` →
`var permissionAsked by rememberSaveable { mutableStateOf(false) }`. Boolean is natively saveable; no
other change. (`onboardingComplete` at :115-117 is left as-is — it's seeded from the durable
`OnboardingPreferences`, see §2.)

**Behavioral interaction (the one place this matters).** `permissionAsked` feeds the onboarding
final-slide `when`-block (`OnboardingScreen.kt:232-330`), which branches: `!stepSensorAvailable` →
`stepCountingGranted` → `!permissionAsked` ("Enable step counting", :305) → else ("Open Settings" +
"Continue without step counting", :310). The only case the change affects is **asked + still-denied**:
post-process-death-restore the slide now shows the **:310 denied-recovery branch** instead of
re-prompting "Enable step counting". **This is the intended/correct outcome** — the system dialog was
already shown; re-firing it would be worse UX and contradicts the codebase's "never strand the player"
intent. `stepCountingGranted` is re-derived fresh from `ContextCompat.checkSelfPermission` on every
`onCreate` (not from saved state), so a since-granted permission still shows the satisfied branch
regardless. `permissionAsked` is the only flag this PR persists. (The framing here supersedes
`OnboardingScreen.kt:63`'s "shown this session" KDoc — it now survives process-death-with-restore, not
a true cold start with no saved-state bundle; a one-line KDoc tweak from "this session" to "this process
instance" is in scope.)

### Build change
Add the `kotlin-parcelize` plugin to `:app` via the version catalog
(`org.jetbrains.kotlin.plugin.parcelize`, tracking the existing `kotlin` version ref — never hardcode).
Confirm it composes with KSP/Hilt (additive Kotlin compiler plugin; the build gate catches any clash).

## 5. Testing strategy

0. **getStateFlow smoke-test FIRST (plan step 1).** Although decompiling lifecycle 2.11.0 confirms
   `SavedStateHandle(Map)` → `getStateFlow` is a pure in-memory-map + `MutableStateFlow` path (no
   `android.os.Bundle`/`SavedStateRegistry`, so `isReturnDefaultValues=true` cannot stub it), this is a
   new-to-repo API. The plan's first implementation step is a one-line throwaway smoke assertion on the
   plain JVM lane: `SavedStateHandle(mapOf("k" to UpgradeCategory.DEFENSE)).getStateFlow("k",
   UpgradeCategory.ATTACK).value == DEFENSE`, then `handle["k"] = ATTACK; assert .value == ATTACK`. If it
   ever regressed, the #253 Robolectric runner is the named contingency lane.
1. **Selection survival (Workshop/Stats)** — the process-death simulation: extend the existing
   `createVm()` test helper to take `handle: SavedStateHandle = SavedStateHandle()`; construct the VM
   with a handle pre-seeded with a non-default selection
   (`SavedStateHandle(mapOf("selectedCategory" to UpgradeCategory.DEFENSE))`), drive the established
   `backgroundScope.launch { vm.uiState.collect {} }; advanceUntilIdle()` pattern, and assert
   `vm.uiState.value.selectedCategory` reflects the seeded value (= restore-on-relaunch; read AFTER
   `advanceUntilIdle()` since `WhileSubscribed` emits nothing until subscription — the `getStateFlow`
   seed resolves synchronously so the value is correct). Plus: `selectCategory(X)` then assert
   `handle["selectedCategory"] == X` (= save). Same for Stats/period.
2. **Pack-reveal round-trip** — (a) pure mapping tests: `List<CardResult>`→`PackRevealState`→`List<CardResult>`
   preserves `type`/`isNew`/`copiesAwarded` (incl. a multi-card list + the `isNew=true` "NEW!" badge); (b)
   `CardsViewModel` test: open a pack (fake repo) → assert reveal in handle; construct a FRESH VM from the
   **same handle instance** (SavedStateHandle does NOT auto-persist across instances — passing the
   populated handle simulates the system bundle) → assert the reveal re-appears in uiState (the "killed
   mid-reveal" scenario). **Deterministic `isNew`:** `FakeCardRepository.openCardPackAtomic` derives
   `isNew` from `cards.value` novelty, so either seed the fake (pre-add a rolled type → at least one
   `isNew=true` and one `isNew=false`) OR assert against the actual returned `CardResult` list rather
   than a hard-coded badge vector the random roll can't guarantee.
3. **`permissionAsked`** — the `remember`→`rememberSaveable` swap on a natively-saveable Boolean is
   verified **on-device** (or by a deferred instrumented `BattleSurfaceLifecycleTest`-style recreation
   test). Do NOT attempt a JVM/Robolectric restore test: no `StateRestorationTester`/
   `createAndroidComposeRule` pattern exists in the repo, and screen-level `createComposeRule` cannot host
   `MainActivity`'s `setContent` graph — such a test would assert nothing about real restore (test
   theater). The branch-interaction (§4 surface 4) MAY be covered cheaply by constructing
   `OnboardingScreen(permissionAsked = true, …)` on the existing #253 Robolectric lane and asserting the
   :310 recovery branch renders (this tests the slide's branching, not the restore mechanism).
4. **Guards stay green** — `DomainPurityTest` MUST pass (the enforcing assertion that `@Parcelize` stayed
   in presentation and domain `CardResult`/`CardType` are untouched). Full
   `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; headline test count rises.

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `@Parcelize` leaks Android into `domain/` | DTO lives in `presentation/cards/`; domain types untouched; `DomainPurityTest` enforces. |
| DTO↔UI mapping drifts from the screen's render contract | Plan pins `CardsUiState.lastPackResult` type + every screen read site; mapping is 1:1 + unit-tested. |
| `getStateFlow` changes combine emission timing | Still a `StateFlow` with the same default; seeded-handle test proves first-emission correctness. |
| `kotlin-parcelize` clashes with KSP/Hilt | Additive Kotlin plugin on `:app`; `assembleDebug` gate catches classpath issues. |
| `CardType.valueOf(name)` throws on an unknown/renamed enum | Names are written + read in the same app version within one process-death window; defensive `runCatching`/`enumValueOrNull` fallback to "no reveal" if a value can't resolve (drops gracefully — never crashes). Plan decides whether to add the guard. |

## 7. Acceptance criteria

- Workshop tab + Stats period survive process death (seeded-`SavedStateHandle` test proves restore;
  setter test proves save).
- Pack-reveal payload survives process death via the presentation `@Parcelize` DTO; faithful
  type/isNew/copies; `dismissPackResult` clears it.
- `permissionAsked` uses `rememberSaveable`; restore verified **on-device** (no JVM/Robolectric restore
  test — see §5.3). The asked+denied post-restore onboarding branch (:310 recovery) is the intended
  outcome (§4 surface 4).
- `domain/CardResult` + `domain/CardType` unchanged; **`DomainPurityTest` green**.
- Full `testDebugUnitTest lintDebug assembleDebug` green; headline count updated.
- #234 closeable on this scope. **Two #234-named surfaces deliberately left unchanged (noted at close):**
  (a) battle live-round/`RoundEndState` overlay — deferred (not survivable / Medium-High risk, §2);
  (b) `MainActivity.onboardingComplete` — already durable via `OnboardingPreferences` (§2), so no change
  needed.
