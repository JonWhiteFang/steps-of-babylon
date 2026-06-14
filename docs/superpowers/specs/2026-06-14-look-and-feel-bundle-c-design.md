# Look & Feel — Bundle C: Feedback / Feel (haptics + Post-Round / claim celebration + purchase pulse)

**Date:** 2026-06-14
**Issue:** #162
**Source review:** `docs/external-reviews/2026-06-12-look-and-feel-ux-review.md` (§4 HIGH States/feedback, §6 Animation & feel / Audio, §8 T9/T11, §10 Haptics, Remaining Recs 2–3)
**Predecessors:** #159 (design tokens / ActionBar removal, ADR-0022), #160 / PR #165 (Bundle A — de-emoji, loading/empty, a11y), #161 / PRs #166+#167 (Bundle B — back affordances + bottom-nav back-stack fix, ADR-0023)
**Status:** Design approved; ready for implementation plan.

---

## 1. Goal & Scope

Bundle C is the **feedback / feel** wave of the 2026-06-12 look-and-feel review. The review's blunt
finding: reward moments outside battle (claims, purchases, milestones) are flat, the Post-Round
overlay is static text, and there are **zero haptics anywhere in the codebase**. This bundle makes the
game feel *tactile* — every spend, equip, claim, and round-end gets a one-shot animation and/or a
haptic tick, controlled by a new Settings toggle.

It is presentation-only and additive. It introduces a small **haptics + animation helper layer** in
`presentation/ui/` so the feel is applied once, not re-implemented per call-site (the same shared-layer
discipline as Bundles A/B).

**In scope (this PR):**
1. **Haptics infrastructure (greenfield).** New `data/HapticsPreferences.kt` (SharedPreferences, default
   ON) + `presentation/ui/Haptics.kt` (a Compose helper over `View.performHapticFeedback`) + a Settings
   "Haptics" toggle. Wired to: purchase / equip / claim / **BATTLE**-start / pause taps.
2. **Shared, bigger purchase pulse.** Extract `UpgradeCard`'s inline 1.05× pulse into a reusable
   `presentation/ui/PurchasePulse.kt` (`rememberPulse()` + `Modifier.pulseScale(...)`), enlarge to
   **1.12×**, and apply it (with a haptic) across **all** spend buttons (Workshop, in-round, Store, UW,
   Cards, Labs) — today only Workshop pulses.
3. **Post-Round celebration.** Add an entrance animation to the "Round Over" overlay + a **staggered
   reward-line sting** (the colour-coded record / tier / power-stones / steps lines reveal in sequence
   with a success haptic). Compose-HUD only — does **not** touch the renderer/engine/effects.
4. **Claim celebration.** Missions / Milestones / Supplies claims currently remove silently; add a
   one-shot reward flourish + success haptic, fired from a new one-shot success event on each claim VM.

**Explicitly out of scope (tracked elsewhere — do NOT do here):**
- **Reward SFX / audio sting** — blocked on the placeholder sine-tone audio debt (`res/raw/*.ogg`).
  Ship haptics + animation now; the SFX layer is deferred to the audio-debt track (review §6 Audio;
  issue #162 "Blocked sub-item"). The animation hooks are designed so a later SFX call slots in beside
  the haptic with no rework.
- **UW + Card rarity visual system** → #163 (Bundle D).
- **Custom font + onboarding per-slide theming + real ziggurat asset** → #164 (Bundle E).
- **Any change to the battle renderer/engine/effects, economy, concurrency, or `Screen.kt` routes.**

**Risk:** Low. Confined to `presentation/` (+ one `data/` SharedPreferences wrapper mirroring
`SoundPreferences`). One Battle file is touched — the Compose HUD `PostRoundOverlay.kt` + its host line
in `BattleScreen.kt` — the fragile renderer/engine/effects are **not** touched. Zero economy /
concurrency / domain-logic files. Respects every STATE.md fragile zone.

---

## 2. Ground Truth (verified against current `HEAD`, post-v1.0.5)

A 5-lane parallel code map (haptics readiness, reduced-motion mechanism, Post-Round overlay, claim
flows, purchase pulse + all buy/equip call-sites) established the following — the design rests on these,
not on the review's prose:

| Fact | Evidence |
|---|---|
| **Zero haptics today.** No `performHapticFeedback`, `HapticFeedback`, `Vibrator`, or `VIBRATE` permission anywhere in `app/src`. Entirely greenfield. | repo-wide grep `haptic\|vibrat` → no matches; manifest has no `VIBRATE`. |
| **Reduced-motion** is a single hand-rolled util: `ReducedMotionCheck.isReducedMotionEnabled(context)` reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. Queried from composables via `remember { ReducedMotionCheck.isReducedMotionEnabled(context) }`. No Compose CompositionLocal exists. | `presentation/battle/effects/ReducedMotionCheck.kt:6-9`; `UpgradeCard.kt:47-48`. |
| **Settings prefs pattern:** each group is a `@Singleton @Inject` SharedPreferences wrapper (`SoundPreferences`, `MusicPreferences`, `NotificationPreferences`) with synchronous `getBoolean`/`putBoolean` pairs; surfaced via a field in `SettingsState` + a `setXxx` method in `SettingsViewModel` + a `ToggleRow` in `SettingsScreen`. | `data/SoundPreferences.kt:8-17`; `SettingsViewModel.kt:21,39,49`; `SettingsScreen.kt:38` (`ToggleRow`) / `:111-119`. |
| **Purchase pulse** exists only on Workshop `UpgradeCard`: `animateFloatAsState` 1f→**1.05f**, `tween(100)` (`snap()` under reduced-motion), held 100ms via `LaunchedEffect`, applied with `graphicsLayer(scaleX/scaleY)`, triggered in the Card `onClick` guarded `canAfford && !isMaxed`. Every other buy button is a plain Material3 `Button` with no pulse. | `UpgradeCard.kt:49-58,61-68,74`. |
| **Post-Round overlay** (`PostRoundOverlay.kt`) has **no animation** (no `androidx.compose.animation` import). Hosted only at `BattleScreen.kt:226` via `state.roundEndState?.let { PostRoundOverlay(...) }`. `RoundEndState` carries `totalCashEarned`, `stepsEarned`, `powerStonesAwarded`, `isNewBestWave`/`previousBest`, `tierUnlocked` — the 4 colour-coded highlight lines. The overlay is mutated **in place** after appearing (`gemAdWatched`/`psAdWatched` copies). | `PostRoundOverlay.kt:36-87`; `BattleScreen.kt:226`; `BattleUiState.kt:6-19`; `BattleViewModel.kt:386-396,658,679`. |
| **Claim flows:** Missions + Milestones share `presentation/missions/{MissionsScreen,MissionsViewModel}.kt`; Supplies is `presentation/supplies/{UnclaimedSuppliesScreen,UnclaimedSuppliesViewModel}.kt`. **All three remove the claimed item via a Room-Flow re-emission** (the list recomposes; no animation). The use cases return sealed `Result`s; `claimMission`/`claimDrop`/`claimAll` **discard** them, and `claimMilestone`'s `Success` branch is a no-op (`-> Unit // claim state updates via flow`). The `userMessage: MutableStateFlow<String?>` on `MissionsViewModel` is the existing one-shot-message pattern (surfaced via `LaunchedEffect` snackbar). | `MissionsViewModel.kt:53,98-122`; `UnclaimedSuppliesViewModel.kt:31-39`; `MissionsScreen.kt:35-40`. |
| **BATTLE-start tap** = the Home gold "BATTLE" button (`onBattleClick` → `navController.navigate(Screen.Battle.route)`). **Pause tap** = the HUD `FilledTonalButton` → `viewModel.togglePause()` (+ `PauseOverlay` Resume). | `HomeScreen.kt:155`; `MainActivity.kt:284`; `BattleScreen.kt:197`; `PauseOverlay.kt:46`. |
| Material3 + `material-icons-extended` already declared (Compose BOM `2026.02.00`); min SDK 34 → `HapticFeedbackConstants.CONFIRM` / `VIRTUAL_KEY` / `REJECT` all available. No new dependency. | `gradle/libs.versions.toml:7,40,46`. |

**Audit corrections vs. the review** (recorded so a future reviewer doesn't re-raise):

| Review claim | Reality (verified) |
|---|---|
| Milestones live in `presentation/economy` | **Corrected.** Milestones render in `MissionsScreen` (`MilestoneCard`); `economy/CurrencyDashboardScreen` is the weekly-challenge/login-streak dashboard with **no per-item Claim button**. |
| Post-Round shows an earned **gems** reward line | **Corrected.** `RoundEndState` has no gem-amount field; gems come only from the optional watch-gem-ad button (`gemAdWatched` flag). The staggered sting animates the 4 lines that *do* exist (record / tier / power-stones / steps), not a gem line. |
| Purchase pulse just needs to be "bigger" | True, **but** it exists on Workshop only — "bigger everywhere" requires extracting it into a shared modifier first (the other buy buttons have no pulse at all). §4.2. |

---

## 3. Decisions (locked during brainstorming)

| # | Decision | Choice | Driver |
|---|---|---|---|
| D1 | Haptics control | **Dedicated Settings "Haptics" toggle** (new `HapticsPreferences`, default ON), **independent** of Reduced-Motion. | User pick. A user may want motion reduced but tactile feedback on (or vice-versa); conflating them is wrong. Matches the existing Sound/Music toggle pattern exactly. |
| D2 | Haptic API | **`View.performHapticFeedback(HapticFeedbackConstants.*)`** via `LocalView.current`, NOT `Vibrator`/`VibrationEffect` and NOT Compose `LocalHapticFeedback`. | No `VIBRATE` permission needed; respects the device's system haptic setting automatically; gives explicit control over the constant set (`CONFIRM` vs `VIRTUAL_KEY`) that `LocalHapticFeedback`'s 2-value enum doesn't. |
| D3 | Post-Round richness | **Entrance animation + staggered per-reward-line sting.** | User pick; this is exactly what the review asked for ("entrance animation + reward sting"). |
| D4 | Purchase-pulse reach | **Extract a shared pulse+haptic modifier, enlarge to 1.12×, apply to ALL spend buttons.** | User pick; consistent tactile feel everywhere, and the shared modifier is the only way to give the non-Workshop buttons a pulse at all. |
| D5 | Claim richness | **Haptic + one-shot reward animation** (not haptic-only). | User pick; turns the silent removal into a payoff moment. |
| D6 | Claim event channel | **New one-shot success event** (`Channel(Channel.CONFLATED).receiveAsFlow()`) on `MissionsViewModel` + `UnclaimedSuppliesViewModel`; the **reward label is built in the VM** from data it already holds. | **Zero use-case / domain-logic change** — the VMs simply stop discarding the `Result` and emit on confirmed Success. Mirrors the existing `userMessage` one-shot pattern. |
| D7 | `claimAll` feel | **Fires the celebration + haptic ONCE** ("All supplies claimed!"), not per-drop. | Avoids a haptic/animation storm on a batch claim. Single claims fire per claim. |
| D8 | Implementation structure | **Shared helpers first** (`HapticsPreferences`, `Haptics`, `PurchasePulse`, `ClaimCelebration`), **then** wire call-sites. One PR. | Matches Bundle A/B. |
| D9 | Pulse target value | **1.12×** (from 1.05×). Still `graphicsLayer` scale (no layout reflow), `tween(100)` hold 100ms, `snap()` under reduced-motion. | User-confirmed; visible without being cartoonish, zero reflow risk. |

---

## 4. Architecture — New Shared Layer

Four new units in `presentation/ui/` (alongside the #160 `CurrencyDisplay`/`LoadingBox`/`EmptyState`
layer and the #161 `SobTopAppBar`), plus one `data/` SharedPreferences wrapper. Each has one clear
purpose and a well-defined interface.

### 4.1 `data/HapticsPreferences.kt` — the on/off flag

```kotlin
@Singleton
class HapticsPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("haptics_prefs", Context.MODE_PRIVATE)
    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)   // default ON
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean("enabled", enabled).apply()
}
```
- **What/why/depends:** a verbatim mirror of `SoundPreferences` — `@Singleton`, constructor-injected,
  its own `"haptics_prefs"` bucket. No Hilt module needed (constructor injection auto-provides it, like
  `SoundPreferences`). The `SettingsViewModel` writes it; the `Haptics` composable reads it.

### 4.2 `presentation/ui/Haptics.kt` — the call helper

```kotlin
class Haptics(private val view: View, private val prefs: HapticsPreferences) {
    fun tap()     { if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun success() { if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { Haptics(view, HapticsPreferences(context)) }
}
```
- **`tap()`** (`VIRTUAL_KEY`) — light tick for purchase / equip / BATTLE-start / pause taps.
- **`success()`** (`CONFIRM`) — heavier confirm for claims + the Post-Round reward sting.
- **Why read the pref at *call* time** (not captured at `remember`): toggling "Haptics off" in Settings
  takes effect immediately on the next tap without recomposition — and the composable's directly-
  constructed `HapticsPreferences(context)` shares the same `"haptics_prefs"` file as the Hilt singleton
  the VM writes, so they are always consistent. This is the same "read device/pref state directly in the
  composable" shape `ReducedMotionCheck` already uses.
- **What/why/depends:** maps an intent (`tap`/`success`) → an inset-correct, opt-out-respecting haptic
  pulse. Depends only on the Android `View` haptic API + `HapticsPreferences`. Thin View-layer helper →
  no JVM test (house norm; the load-bearing on/off logic is in `HapticsPreferences`, which *is* tested).

### 4.3 `presentation/ui/PurchasePulse.kt` — the shared spend pulse

Extracts the inline `UpgradeCard` pulse into a reusable pair:

```kotlin
class PulseState internal constructor(...) { fun trigger() { ... } ; internal val scale: Float }

@Composable
fun rememberPulse(reducedMotion: Boolean = /* default from ReducedMotionCheck */): PulseState

fun Modifier.pulseScale(pulse: PulseState): Modifier   // = graphicsLayer(scaleX/scaleY = pulse.scale)
```
- Internally identical mechanism to today's `UpgradeCard` pulse — `animateFloatAsState` 1f→**1.12f**
  (D9), `tween(100)` / `snap()` when `reducedMotion`, auto-reset after a 100ms `LaunchedEffect` hold —
  but reusable and applied via `Modifier.pulseScale`. Because it's a `graphicsLayer` scale it causes
  **no layout reflow**, so enlarging to 1.12× is safe on every surface.
- `UpgradeCard` is refactored to consume this (its inline copy is deleted → behavior identical except
  the larger scale). All other spend buttons call `pulse.trigger()` in their existing `onClick`.
- **What/why/depends:** one tactile-scale primitive for "a spend just happened". Depends on Compose
  animation + `ReducedMotionCheck`. Thin Compose helper → no new JVM test (the pulse is a visual that
  was never JVM-tested; verified on-device).

### 4.4 `presentation/ui/ClaimCelebration.kt` — one-shot reward flourish

```kotlin
@Composable
fun ClaimCelebration(event: ClaimCelebrationEvent?, onConsumed: () -> Unit)
// renders a brief scale-in + fade reward chip ("+50 💎 Claimed!" / label from the VM event),
// fires haptics.success() once on appearance, auto-dismisses, calls onConsumed.
```
- Reduced-motion → render instantly (no scale/fade), but the `success()` haptic **still fires** (D1:
  haptics independent of motion). One-shot — honours the review's "keep new animation one-shot; the
  foreground step service runs always — avoid always-on menu animation" battery note (§10).
- Consumes the VM one-shot event (§5); `onConsumed` clears it. Hosted once per claim screen (overlay in
  the screen's root `Box`, like the existing snackbar host).
- **What/why/depends:** the celebration overlay for "a claim succeeded". Depends on Compose animation +
  `rememberHaptics` + the VM event type. Thin Compose helper → no new JVM test; the **event-emission
  logic in the VMs is the tested part** (§7).

---

## 5. The one-shot claim event (D5/D6)

The card-removal is driven by a Room-Flow re-emission, so the celebration **cannot** fire from the
disappearing list item — it fires from the claim callback path, via a new one-shot event the screen
collects. This is the first event-bus in the app; keep it minimal and mirror the `userMessage` shape.

**`MissionsViewModel`** + **`UnclaimedSuppliesViewModel`** each gain:
```kotlin
private val _celebration = Channel<ClaimCelebrationEvent>(Channel.CONFLATED)
val celebration = _celebration.receiveAsFlow()
```
- **Missions:** `claimMission` currently discards `ClaimMissionResult`. Change to inspect it and
  `_celebration.trySend(...)` **only** on `ClaimMissionResult.Success` (build the label from the
  mission's reward fields the VM already maps). No use-case change.
- **Milestones:** the `ClaimMilestoneResult.Success -> Unit` branch (`MissionsViewModel.kt:113`) is the
  ready-made hook — emit there. The failure branches keep their existing `userMessage` snackbar (so
  `InsufficientSteps`/`AlreadyClaimed`/`UnknownCosmetic` do **not** celebrate). This is exactly why the
  celebration must gate on `Success`: the 3 `UnknownCosmetic` milestones must never falsely celebrate.
- **Supplies:** `claimDrop` emits on `ClaimSupplyDrop.Result.Success` (build label from the drop's
  reward). **`claimAll` emits ONE aggregate event** after the loop (D7), not one per drop.

Screens collect the event in a `LaunchedEffect` (same shape as the existing `userMessage` snackbar
collector at `MissionsScreen.kt:35-40`) and pass it to `ClaimCelebration`.

> **Why `Channel.CONFLATED` not `StateFlow`:** a celebration is a one-shot *event*, not state — a
> `StateFlow<String?>` re-emits the same value on every unrelated `combine` upstream tick (the Missions
> `uiState` has a 1s `tick`), which would re-trigger the animation. A conflated `Channel` delivers each
> claim once and drops nothing the UI needs. (The existing `userMessage` is a `StateFlow` because it's
> paired with `clearMessage()`; the new event is cleaner as a Channel.)

---

## 6. Call-site wiring map (exact sites)

### Haptic `tap()` — spend + equip + nav
| Surface | Site | Action |
|---|---|---|
| Workshop purchase | `UpgradeCard.kt` Card `onClick` (`:61-68`) | `pulse.trigger()` (already) + `haptics.tap()` |
| In-round purchase | `InRoundUpgradeMenu.kt:138` buy `onClick` | `pulse.trigger()` + `haptics.tap()` |
| Store gem pack / ad-removal / season pass / cosmetic | `StoreScreen.kt:77,106,137,180` | `pulse.trigger()` + `haptics.tap()` |
| UW unlock / per-path upgrade | `UltimateWeaponScreen.kt:113,179` | `pulse.trigger()` + `haptics.tap()` |
| Cards pack-open / card upgrade | `CardsScreen.kt:89,179` | `pulse.trigger()` + `haptics.tap()` |
| Labs start / slot-unlock / rush | `LabsScreen.kt:66,156,173` | `pulse.trigger()` + `haptics.tap()` |
| **Equip (no pulse — tap only)** UW toggle / Card equip+unequip / cosmetic equip+unequip | `UltimateWeaponScreen.kt:136`; `CardsScreen.kt:174,176`; `StoreScreen.kt:174,175` | `haptics.tap()` |
| BATTLE-start | `HomeScreen.kt:155` `onBattleClick` | `haptics.tap()` |
| Pause toggle | `BattleScreen.kt:197` (+ `PauseOverlay.kt:46` Resume) | `haptics.tap()` |

> The pulse only attaches to controls that already have an enablement guard (`canAfford`/`!isMaxed`);
> the haptic fires inside the same already-guarded `onClick`, so a disabled button neither pulses nor
> buzzes (the guard already prevents the underlying spend).

### Haptic `success()` — claims + post-round
| Surface | Site |
|---|---|
| Missions / Milestones / Supplies claim | via `ClaimCelebration` on the VM Success event (§5) |
| Post-Round reward sting | per revealed reward line (§3 D3 / below) |

### Post-Round celebration (`PostRoundOverlay.kt` + `BattleScreen.kt:226`)
- **Entrance:** wrap the host `state.roundEndState?.let { PostRoundOverlay(...) }` in
  `AnimatedVisibility` (fade + scale-in via `MutableTransitionState` seeded `false`) keyed on
  `roundEndState != null`. **Keyed on the transition, not on `RoundEndState` identity** — so the
  in-place `gemAdWatched`/`psAdWatched` copies (`BattleViewModel.kt:658,679`) do **not** re-run the
  entrance (the open-question the ground-truth map flagged).
- **Staggered sting:** inside `PostRoundOverlay`, a `visibleCount` state increments on a one-shot
  `LaunchedEffect(Unit)` timer; each of the 4 highlight blocks (`:61` record, `:69` tier, `:75`
  power-stones, `:80` steps) renders gated on its index `< visibleCount`, each firing `haptics.success()`
  as it appears. The stat block + buttons (`:91+`) render after the sting completes (or immediately).
- **Reduced-motion:** `visibleCount` jumps to "all" instantly (no stagger, no entrance scale); a single
  `haptics.success()` still fires once. One-shot regardless.

---

## 7. Settings toggle (D1)

Mirror `setSoundMuted` exactly:
- `SettingsState` gains `val hapticsEnabled: Boolean = true`.
- `SettingsViewModel` constructor gains `private val hapticsPrefs: HapticsPreferences`; init reads
  `hapticsEnabled = hapticsPrefs.isEnabled()`; new `fun setHapticsEnabled(enabled: Boolean) {
  hapticsPrefs.setEnabled(enabled); _state.update { it.copy(hapticsEnabled = enabled) } }`.
- `SettingsScreen` gains a `ToggleRow("Haptic Feedback", "Vibrate on taps, claims, and rewards",
  state.hapticsEnabled, viewModel::setHapticsEnabled)` next to the Sound/Music toggles.

---

## 8. Testing & Validation

**New / changed tests (JVM):**
- **`HapticsPreferencesTest`** (new) — mirrors `MusicPreferencesTest`: default `isEnabled() == true`;
  `setEnabled(false)` persists; round-trips. (Robolectric `SharedPreferences`, like the existing prefs
  tests.)
- **`MissionsViewModelTest`** (extend) — claiming a completed+unclaimed mission emits **one** celebration
  event; claiming a milestone on `Success` emits; the `InsufficientSteps`/`AlreadyClaimed`/
  `UnknownCosmetic` branches emit **no** celebration (only the existing `userMessage`). Use the existing
  fakes (`FakeDailyMissionDao`, `FakeMilestoneDao`, `FakePlayerRepository`, …).
- **`UnclaimedSuppliesViewModelTest`** (extend) — `claimDrop` on Success emits one event; **`claimAll`
  emits exactly one aggregate event** for N drops (D7), not N.
- **`SettingsViewModelTest`** — if one exists, add `setHapticsEnabled` coverage; otherwise the
  `HapticsPreferences` test + the mechanical mirror is sufficient (the VM method is a one-line
  pref-write + state-copy identical to the 6 existing setters). *(Confirm during planning.)*
- **No new Compose/View-helper tests** — `Haptics`, `PurchasePulse`, `ClaimCelebration`,
  `PostRoundOverlay` are thin presentation helpers (house norm: presentation-only, visually verified
  on device). The tested logic is the prefs flag + the VM event emission.
- **Regression guards confirmed green:** full existing suite compiles after the `UpgradeCard` pulse
  extraction (behavior identical bar the scale); `BattleViewModel`/engine untouched.

**Validation gates (project standard):**
- `./run-gradle.sh assembleDebug` → BUILD SUCCESSFUL
- `./run-gradle.sh testDebugUnitTest lintDebug` → green; headline test count rises (pinned to the actual
  count after the green build — **don't guess the number in the spec**; update `CLAUDE.md` + `STATE.md`
  to the real total).
- **On-device (emulator API 36):**
  - Haptic ticks on a purchase (Workshop + one other surface), an equip, BATTLE-start, pause, and a
    claim; toggling Settings "Haptics" off silences them immediately (no app restart).
  - The enlarged 1.12× pulse is visibly bigger than before on Workshop **and** now present on Store/UW/
    Cards/Labs/in-round.
  - Post-Round overlay animates in and the reward lines stagger with a tick; the watch-ad buttons do
    **not** re-trigger the entrance.
  - A mission / milestone / supply claim shows the celebration flourish; `claimAll` celebrates once.
  - **Reduced-motion device setting ON:** animations collapse to instant, but haptics still fire
    (validates D1 independence — the one thing a static diff can't show).

**Docs (mandatory PR Task-List Convention — sync BEFORE the STATE/RUN_LOG update):**
- `docs/steering/source-files.md` — add `HapticsPreferences.kt`, `Haptics.kt`, `PurchasePulse.kt`,
  `ClaimCelebration.kt`; note the `PostRoundOverlay` animation + the two claim VMs' new event.
- `docs/steering/structure.md` — note the `presentation/ui/` feel-helper additions if the structural
  shape shifts (likely a one-line mention).
- `CLAUDE.md` — headline test-count bump only (no architecture/convention change).
- `CHANGELOG.md` — `[Unreleased]` entry.
- Then `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md` via `/checkpoint`.
- **New fragile-zone note** in `STATE.md`: "Haptics + feel are centralized — `Haptics`/`PurchasePulse`/
  `ClaimCelebration` in `presentation/ui/`; claim celebrations fire from a conflated VM event gated on
  `Result.Success` (never on failure/`UnknownCosmetic`); Post-Round entrance keyed on the round-end
  transition, not `RoundEndState` identity (watch-ad copies must not re-trigger it)."
- **ADR:** **likely none** — this implements the ADR-0022 design-tokens/feel direction and introduces no
  new architectural *decision* beyond "haptics via `View.performHapticFeedback` + a conflated claim
  event." If the planning step judges the claim-event-bus pattern worth recording (first event-bus in
  the app), a short ADR may be added; decide in the plan. *(Default: no ADR; cross-reference §3 D2/D6.)*
- On merge: comment on / **close #162**; note the deferred SFX sub-item belongs to the audio-debt track.

---

## 9. File-Touch Summary

**New:**
- `data/HapticsPreferences.kt`
- `presentation/ui/Haptics.kt`
- `presentation/ui/PurchasePulse.kt`
- `presentation/ui/ClaimCelebration.kt`
- `test/.../data/HapticsPreferencesTest.kt`

**Modified:**
- `presentation/workshop/UpgradeCard.kt` — replace inline pulse with shared `PurchasePulse`; add
  `haptics.tap()`.
- `presentation/workshop/WorkshopScreen.kt`, `presentation/store/StoreScreen.kt`,
  `presentation/weapons/UltimateWeaponScreen.kt`, `presentation/cards/CardsScreen.kt`,
  `presentation/labs/LabsScreen.kt`, `presentation/battle/ui/InRoundUpgradeMenu.kt` — pulse + haptic on
  spend buttons; haptic on equip buttons (per §6 map).
- `presentation/home/HomeScreen.kt` — `haptics.tap()` on BATTLE-start.
- `presentation/battle/BattleScreen.kt` — `haptics.tap()` on pause; wrap the `PostRoundOverlay` host
  (`:226`) in `AnimatedVisibility`. **HUD/Compose only — no renderer/engine/effects change.**
- `presentation/battle/ui/PostRoundOverlay.kt` — entrance + staggered reward-line sting + `success()`.
- `presentation/battle/ui/PauseOverlay.kt` — `haptics.tap()` on Resume.
- `presentation/missions/MissionsViewModel.kt` + `MissionsScreen.kt` — one-shot celebration event
  (missions + milestones) + `ClaimCelebration` host.
- `presentation/supplies/UnclaimedSuppliesViewModel.kt` + `UnclaimedSuppliesScreen.kt` — one-shot
  celebration event (single + `claimAll`-once) + `ClaimCelebration` host.
- `presentation/settings/SettingsViewModel.kt` + `SettingsScreen.kt` — Haptics toggle.
- `test/.../presentation/missions/MissionsViewModelTest.kt`,
  `test/.../presentation/supplies/UnclaimedSuppliesViewModelTest.kt` — event-emission coverage.
- Doc files per §8.

**Untouched (fragile zones honored):** all of `data/` **except** the new `HapticsPreferences.kt`;
`service/`; `domain/` (zero use-case / model / repository-interface change — the VMs stop discarding
`Result`s but the use cases are unchanged); `presentation/battle/{engine,entities,effects}` (the
renderer + game loop — only the Compose HUD `PostRoundOverlay.kt` / `PauseOverlay.kt` / the `:226` host
line in `BattleScreen.kt` change); `Screen.kt` route definitions; `BattleViewModel` /
`RoundEndState` / `BattleUiState` (the entrance animation is hosted in `BattleScreen`, keyed on the
existing `roundEndState` nullability — no engine state added).
