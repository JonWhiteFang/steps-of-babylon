# Implementation Plan — Accessibility wave (#213 · #214 · #226)

**Date:** 2026-06-20
**Spec:** `2026-06-20-accessibility-wave-213-214-226.md` (REVIEWED — F1–F5 applied)
**Status:** REVIEWED (single-agent plan-stage Adversarial Review Gate, ultracode off; 5 findings —
**CRITICAL** caught pre-code (stateful announcer mutated in `derivedStateOf`/`remember` = side-effect-in-
composition → use a pure (prev,next) fn diffed in a `LaunchedEffect`), **MAJOR** localization shape
decided (sealed result + composable resolves `stringResource`), **MAJOR** `size(0.dp)` → `size(1.dp)
.alpha(0f)` (zero-bounds nodes pruned from a11y tree), + 2 minor — all applied 2026-06-20; contrast math
re-verified). Ready to implement.
**Build/test:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` (non-TTY).

Branch (already created): `a11y/contrast-talkback-cvd-213-214-226`. TDD where there's a pure seam.

---

## Task 1 — #213 contrast: `OnGold` token + WCAG guard test

1. **RED — pure WCAG contrast guard.** New
   `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/theme/ContrastTest.kt` (JUnit Jupiter,
   pure Int ARGB math — mirrors `ColorLerpTest`). Implement the WCAG ratio in the TEST (or via a shared
   pure helper — see step 2) and assert:
   - `contrastRatio(OnGold, Gold) >= 4.5` (the fix).
   - (regression doc) `contrastRatio(DeepBronze, Gold)` < 4.5 (the old value, ~4.19 — documents WHY the
     token exists; optional but cheap).
   - Use plain `Int` ARGB literals (`0xFF4A2618.toInt()`, `0xFFD4A843.toInt()`), NOT Compose `Color`.
2. **GREEN — token.** In `Color.kt`, add `val OnGold = Color(0xFF4A2618)` with a KDoc explaining it's the
   text-role colour for `Gold` surfaces (~5.99:1, passes AA-normal; the brand `DeepBronze` is only 4.19:1
   — fails). In `Theme.kt:16` change `onPrimary = DeepBronze` → `onPrimary = OnGold`.
   - **WCAG helper placement:** put the pure ratio fn where the test can reach it without Compose. Option
     A: a private fn in the test file (simplest — the value is the artifact, not a shipped API). Option B:
     a shared `fun contrastRatio(argbA: Int, argbB: Int): Double` in `presentation/ui` if reused. Default
     A (test-local) unless the plan-review prefers a shipped helper. Either way the ratio is computed from
     Int channels (sRGB linearise → relative luminance → (Llight+0.05)/(Ldark+0.05)).
3. **No other token change (F2):** `StatusDanger`/`RaritySand` are icon-tint/fill only — leave them.
4. Verify RED→GREEN; **mutation-check**: revert `onPrimary` to `DeepBronze` and confirm `ContrastTest`
   fails.

**Files:** `Color.kt`, `Theme.kt`, new `ContrastTest.kt`.

---

## Task 2 — #214 battle TalkBack live region

1. **RED — pure announcer test.** New
   `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/BattleAnnouncerTest.kt` (JUnit
   Jupiter, pure — asserts the **sealed `BattleAnnouncement`** value, no Compose). Drive (prev, next)
   pairs through `battleAnnouncement` and assert:
   - blank `wavePhase` OR `maxHp <= 0.0` in `next` → `null` (pre-round; no spurious announce; also covers
     divide-by-zero guard).
   - wave change → `Wave(n)`; phase change → `Phase("SPAWNING")` / `Phase("COOLDOWN")` (the raw enum;
     the composable maps to the localized string — the helper does NOT humanise).
   - health emits `Health(bucket)` only on **25%-bracket crossings** (`floor(hp/max*4)`): many small HP
     decrements within a bracket → `null`; crossing a bracket → one `Health`.
   - `roundEndState != null` (newly) → `RoundOver(wave)`; `battleError` (newly true) → `Error`.
   - priority: when multiple fields change, battleError > roundOver > wave > phase > health.
   - unchanged `next` vs `prev` → `null` (no spam).
2. **GREEN — pure helper returning a sealed "what changed" (PLAN-REVIEW MAJOR — decided: option b).**
   New `presentation/battle/BattleAnnouncer.kt`. Two parts, both **pure (no Android/Compose import)**:
   - A sealed `BattleAnnouncement` result: `Wave(n: Int)` / `Phase(raw: String)` / `Health(bucket: Int)`
     (bucket 0–4 = quarters) / `RoundOver(wave: Int)` / `Error` (+ the helper returns `null` = nothing).
   - The diff is a **pure function over (prev, next)** — NOT a stateful class mutated in composition:
     `fun battleAnnouncement(prev: BattleSnapshot?, next: BattleSnapshot): BattleAnnouncement?` where
     `BattleSnapshot` is the 6 primitives (`currentWave: Int, wavePhase: String, currentHp: Double,
     maxHp: Double, roundEndState: RoundEndState?, battleError: Boolean`). `RoundEndState` is a plain data
     class (no Android), so the helper imports it and stays JVM-pure. Guards (F1): return `null` when
     `next.wavePhase` is blank OR `next.maxHp <= 0.0`; bucket health `floor(hp/max*4)` with the divide
     guarded. Priority: battleError > roundEnd > wave > phase > health (first changed-field wins). The
     **composable** maps the sealed result → `stringResource` (so localization + the raw-enum→string map
     `SPAWNING`→`R.string.battle_a11y_phase_spawning` live in Compose, not the helper).
3. **`strings.xml`:** add localizable announcement strings (`battle_a11y_wave`, `battle_a11y_phase_spawning`/
   `_cooldown`, `battle_a11y_health`, `battle_a11y_round_over`, `battle_a11y_error`). Match the existing
   `battle_*` convention.
4. **`BattleScreen.kt` (PLAN-REVIEW CRITICAL — no side-effect-in-composition; PLAN-REVIEW MAJOR — no
   zero-size node):** add a **sized-invisible sibling** node inside the root `Box` (after the
   `AndroidView` at line 144):
   `Box(Modifier.size(1.dp).alpha(0f).semantics { liveRegion = LiveRegionMode.Polite; contentDescription
   = announcement })`. The announcement String is held in `var announcement by remember {
   mutableStateOf("") }` and updated **inside a `LaunchedEffect(state.currentWave, state.wavePhase,
   state.currentHp, state.maxHp, state.roundEndState, state.battleError)`** (a legitimate side-effect
   scope — NOT `derivedStateOf`/`remember` calc, which Compose may run/discard and corrupt the diff): the
   effect keeps the previous `BattleSnapshot` in another `remember { mutableStateOf<BattleSnapshot?>(null)
   }`, calls `battleAnnouncement(prev, next)`, and if non-null resolves it via `stringResource` (resolve
   the templates ABOVE the effect — `stringResource` is `@Composable`; pass the resolved templates/args
   into the effect or resolve in the effect using values captured from a `@Composable` boundary; simplest:
   resolve all 5 templates with `stringResource` into locals, then the effect formats with the sealed
   result's args) and writes `announcement`. **Use `size(1.dp).alpha(0f)`, NOT `size(0.dp)`** (zero-bounds
   nodes are pruned from the a11y tree → TalkBack never fires). New imports:
   `androidx.compose.ui.semantics.liveRegion`, `androidx.compose.ui.semantics.LiveRegionMode`,
   `androidx.compose.foundation.layout.size`, `androidx.compose.ui.draw.alpha`,
   `androidx.compose.runtime.mutableStateOf`/`setValue`. Do NOT touch the steps-earned node (the
   `if (state.stepsEarnedThisRound > 0)` block, lines 163–171).
5. Verify RED→GREEN. **On-device TalkBack confirmation that the live region actually announces is a HARD
   acceptance criterion** (no automated Compose coverage exists — #253; the zero-size pruning risk makes
   this non-optional).

**Files:** new `BattleAnnouncer.kt`, new `BattleAnnouncerTest.kt`, `BattleScreen.kt`, `strings.xml`.

---

## Task 3 — #226 GDD deferral + non-color-cue confirmation

1. **`docs/StepsOfBabylon_GDD.md:459`:** reword the color-blind line to a tracked post-v1.0 deferral
   (e.g. "**Color-blind modes (post-v1.0, planned — not in v1.0):** three CVD-safe palettes for common
   color vision deficiencies."). Leave line 460 (TalkBack/audio cues) — #214 now strengthens that claim.
2. **No store edit** (no a11y claim). **No `master-plan.md` edit** (already deferred at line 147). **No
   archived-doc edit** (CLAUDE.md historical rule).
3. **Non-color-cue confirmation (F-survey done in spec):** the review confirmed no genuinely color-ONLY
   status site (wave-phase bar has its text label at `BattleScreen.kt:155`; currencies pair icon+value;
   `CurrencyDashboardScreen` goal uses Check/Close shape + contentDescription). **No code change required**
   — record this in the PR/RUN_LOG as a completed survey, not a silent skip. (If the plan-review surfaces
   a real color-only site, add a text/shape cue there; otherwise none.)

**Files:** `docs/StepsOfBabylon_GDD.md`.

---

## Task 4 — Build + verify + mutation-check

`./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`. Confirm BUILD SUCCESSFUL; new tests ran;
JVM = 1139 + net-new; `DomainPurityTest` green; no regressions. Mutation-check Task 1 (onPrimary revert)
+ Task 2 (e.g. break the bracketing → health-spam test fails).

## Task 5 — Sync current-state docs (PR Task-List Convention, BEFORE STATE/RUN_LOG)

- `CHANGELOG.md` — `[Unreleased]` section; test count.
- `CLAUDE.md` — headline test count (+net-new).
- `docs/steering/source-files.md` — new `BattleAnnouncer.kt` + 2 tests; `Color.kt` (OnGold), `Theme.kt`,
  `BattleScreen.kt` (live region) entries.
- NOT touched: schema/tech/structure/README (no schema/dep/module/build change). GDD is synced in Task 3.
- **ADR:** likely NO ADR (a11y polish on established token/UI patterns — no new architecture/concurrency).
  Decide at synthesis; default no ADR, rationale in CHANGELOG + spec.

## Task 6 — STATE + RUN_LOG (end-of-run; `/checkpoint`)

Rotate STATE objective; append RUN_LOG (goal/changes/verification/doc-sync/next).

## Task 7 — Commit + PR + monitor + merge

Commit on branch, push, PR (closes #213/#214/#226). **The PR MUST explicitly justify the no-palette
close of #226** (the issue asked to EITHER ship a palette OR document it as a tracked deferral — we chose
the latter; plus the survey found no color-ONLY status: the wave-phase bar carries its text label at
`BattleScreen.kt:155`, currencies pair icon+numeric value, the dashboard goal uses Check/Close shape +
contentDescription). Watch CI, merge on green.

---

## Verification checklist (acceptance, from the spec)

- [ ] `OnGold` on `Gold` ≥ 4.5:1, pinned by `ContrastTest`; `onPrimary = OnGold`; no other token changed.
- [ ] Battle polite live region announces wave/phase/health-bracket/outcome/error from uiState; pure
      `BattleAnnouncer` test proves transitions + health bucketing + pre-round null; TalkBack on-device
      flagged as a developer step.
- [ ] GDD §17 color-blind line is a tracked post-v1.0 deferral; no color-ONLY status remains (survey
      recorded); no store/master-plan/archived edits.
- [ ] `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; JVM 1139 + net-new; no regressions;
      `DomainPurityTest` green.
- [ ] Docs synced; STATE/RUN_LOG updated.
