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
4. **Hilt DI unchanged in shape.** All four affected VMs are `@HiltViewModel` + `@Inject constructor`,
   obtained via `hiltViewModel()` — Hilt auto-provides `SavedStateHandle` to the constructor, so adding
   the param requires no factory/module change.

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
- The combine source (:52) switches from `_lastPackResult` to
  `savedStateHandle.getStateFlow<PackRevealState?>(KEY_PACK_REVEAL, null)`, mapped (DTO → whatever
  `CardsUiState.lastPackResult` expects) inside the combine.
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

### Build change
Add the `kotlin-parcelize` plugin to `:app` via the version catalog
(`org.jetbrains.kotlin.plugin.parcelize`, tracking the existing `kotlin` version ref — never hardcode).
Confirm it composes with KSP/Hilt (additive Kotlin compiler plugin; the build gate catches any clash).

## 5. Testing strategy

1. **Selection survival (Workshop/Stats)** — the process-death simulation: construct the VM with a
   `SavedStateHandle` pre-seeded with a non-default selection
   (`SavedStateHandle(mapOf("selectedCategory" to UpgradeCategory.DEFENSE))`) and assert the first uiState
   emission reflects it (= restore-on-relaunch). Plus: `selectCategory(X)` then assert
   `savedStateHandle["selectedCategory"] == X` (= save). Extends the existing fake-backed VM tests on the
   plain JVM lane. Same for Stats/period.
2. **Pack-reveal round-trip** — (a) pure mapping tests: `CardResult`→`PackRevealState`→UI model preserves
   `type`/`isNew`/`copiesAwarded` (incl. a multi-card list + the `isNew=true` "NEW!" badge); (b)
   `CardsViewModel` test: open a pack (fake repo) → assert reveal in handle; construct a FRESH VM from the
   same handle → assert the reveal re-appears in uiState (the "killed mid-reveal" scenario).
3. **`permissionAsked`** — covered by the Robolectric Compose UI lane if a cheap assertion exists;
   otherwise documented as on-device-verified (one-line `remember`→`rememberSaveable` swap).
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
- `permissionAsked` uses `rememberSaveable`.
- `domain/CardResult` + `domain/CardType` unchanged; **`DomainPurityTest` green**.
- Full `testDebugUnitTest lintDebug assembleDebug` green; headline count updated.
- #234 closeable on this scope; battle live-round/overlay explicitly deferred (noted at close).
