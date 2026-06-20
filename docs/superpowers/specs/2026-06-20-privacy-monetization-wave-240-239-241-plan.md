# Implementation Plan — Privacy / Monetization wave (#240 · #239 · #241)

**Date:** 2026-06-20
**Spec:** `2026-06-20-privacy-monetization-wave-240-239-241.md` (REVIEWED — F1–F6 applied)
**Status:** REVIEWED (single-agent plan-stage Adversarial Review Gate, ultracode off; 1 MAJOR finding
[Task-3 JVM-safety rationale] + minor URL-anchor note applied 2026-06-20; 0 critical). Ready to implement.
**Build/test commands:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` (non-TTY).

Ordering note: TDD per task with a testable seam (RED → GREEN). #239 is doc-only (no test seam).
Branch off `main`: `fix/privacy-monetization-240-239-241`.

---

## Task 1 — Shared `PRIVACY_POLICY_URL` constant + drift guard (#240)

**Goal:** one source of truth for the hosted-policy URL, pinned by a JVM test.

1. **RED.** New test `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/PrivacyPolicyUrlTest.kt`:
   assert `PRIVACY_POLICY_URL == "https://jonwhitefang.github.io/steps-of-babylon/"` — the canonical
   declared URL at `docs/release/data-safety-form.md:66` (also in `site/index.md`'s header comment,
   line 7). This is the Play "in-app link == form URL == hosted page" guard.
2. **GREEN.** Add the constant. Placement: a new top-level file
   `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/PrivacyPolicy.kt` with
   `const val PRIVACY_POLICY_URL = "https://jonwhitefang.github.io/steps-of-babylon/"`. (Presentation
   layer — NOT `domain/`; keeps `DomainPurityTest` green. Mirrors the shared-`presentation/ui/` pattern
   used by `EnumDisplayName`/`ColorLerp`/`Rarity`.)
3. Verify the test goes RED→GREEN.

**Files:** new `presentation/ui/PrivacyPolicy.kt`, new `…/presentation/ui/PrivacyPolicyUrlTest.kt`.

---

## Task 2 — Settings "Privacy Policy" row + MainActivity wiring (#240)

**Goal:** a tappable Privacy Policy row in Settings → opens the hosted policy via a guarded ACTION_VIEW
intent.

No JVM/Compose test (SettingsScreen is a `@Composable`; Compose UI tests are out of scope — tracked as
#253; the URL behaviour is already guarded by Task 1). Build + on-device/emulator-verified.

1. **`SettingsScreen.kt`:** add `onOpenPrivacyPolicy: () -> Unit = {}` to the signature (alongside
   `onReplayTutorial`, `onOptimizeBattery`). In the **Data** section (after the Delete All Data card,
   or directly under the "Data" header before Delete — final placement: put it ABOVE Delete so the
   destructive action stays last), add an `OutlinedCard(onClick = onOpenPrivacyPolicy, …)` matching the
   "Background activity" row idiom (title "Privacy Policy", subtitle "How your data is handled"). No
   leading icon required (keep parity with the Background-activity row, which has none); optional
   `Icons.AutoMirrored`/info icon is a nicety, skip to avoid an import churn unless trivial.
2. **`MainActivity.kt`:**
   - Add new top-level `private fun openPrivacyPolicy(context: android.content.Context)` AFTER
     `requestBatteryExemption` (~line 480), using the guarded idiom:
     ```kotlin
     val view = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
     if (view.resolveActivity(context.packageManager) != null) context.startActivity(view)
     ```
     (no-browser device → safe no-op). Import `PRIVACY_POLICY_URL`.
   - In `composable(Screen.Settings.route)` (`MainActivity.kt:386-391`) pass
     `onOpenPrivacyPolicy = { openPrivacyPolicy(context) }`.
3. Build (`assembleDebug`) to confirm it compiles.

**Files:** `presentation/settings/SettingsScreen.kt`, `presentation/MainActivity.kt`.

---

## Task 3 — AdMob `RequestConfiguration` (maxAdContentRating = PG) before first ad request (#241)

**Goal:** cap ad content rating at PG, 13+ stance, before the first `AdRequest` — no age gate, no
child-directed flag.

1. **RED.** New test
   `app/src/test/java/com/whitefang/stepsofbabylon/data/ads/AdRequestConfigurationTest.kt`:
   - `buildAdRequestConfiguration().maxAdContentRating == RequestConfiguration.MAX_AD_CONTENT_RATING_PG`
   - `…tagForChildDirectedTreatment == RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED`
   - `…tagForUnderAgeOfConsent == RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED`
   (verifies the 13+/no-flag decision and that we set ONLY the rating). Plain JUnit Jupiter — no
   Robolectric, no SDK init.
   **JVM-safety basis (plan-review MAJOR, corrected):** the builder ctor / `build()` / getters touch no
   `android.*`, BUT `setMaxAdContentRating("PG")` internally calls `android.util.Log.w` (verified in
   25.4.0 bytecode: `zzo.zzi`). The test runs cleanly anyway because the module sets
   `unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts:198`), which returns a default for
   the stubbed `Log.w` instead of throwing "not mocked". The class-load chain is otherwise
   Android-free. So the test depends on that Gradle flag — do NOT remove it. Asserting the two int
   tags (`== -1`, i.e. `*_UNSPECIFIED`) exercises no Android at all and is the safest part of the test.
2. **GREEN.** In `RealRewardedAdAdapter.kt`:
   - Add a pure helper (top-level `internal fun` or companion `@VisibleForTesting`):
     ```kotlin
     internal fun buildAdRequestConfiguration(): RequestConfiguration =
         RequestConfiguration.Builder()
             .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
             .build()
     ```
   - In `ensureSdkInitialized()`, as the **first statement inside the `withContext(Dispatchers.Main)`
     block, before `MobileAds.initialize` (line 142)**:
     ```kotlin
     MobileAds.setRequestConfiguration(buildAdRequestConfiguration())
     ```
   - Import `com.google.android.gms.ads.RequestConfiguration`.
3. Verify RED→GREEN. (The `setRequestConfiguration` static call is device/build-verified; the value is
   unit-tested.)

**Files:** `data/ads/internal/RealRewardedAdAdapter.kt`, new
`…/data/ads/AdRequestConfigurationTest.kt`.

**Fragile-zone note:** the ad adapter is unrelated to `entitiesLock`/`effectsLock`/economy — no
fragile-zone overlap. `@Singleton` + idempotent global setter → safe (spec F1).

---

## Task 4 — Reconcile hosted policy text with the 4 AdMob categories (#239) — doc only

**Goal:** `site/index.md` enumerates all four AdMob-collected categories (incl. approximate location),
consistent with the Data-Safety form.

1. **`site/index.md`:**
   - Under "Advertising Identifier (Google AdMob)" (lines 33-36) and/or "Data Sharing" (lines 53-55),
     add an explicit enumeration of the four categories Google's AdMob SDK collects/shares: (1)
     advertising ID + device/app-set IDs, (2) **approximate location derived from IP address**, (3)
     app-interaction events (ad launches, taps, video views), (4) diagnostics (launch time, hang rate,
     energy usage). Phrasing aligned to `data-safety-form.md:45-48`. Keep the "collected by Google, not
     by us / governed by Google's policy" framing.
   - Bump **Effective Date** (line 14) from "June 18, 2026" to "June 20, 2026".
   - Audit the "Children's Privacy" paragraph (lines 81-83): confirm it isn't under-disclosing relative
     to the new location text; expected = no rewrite (it already references AdMob's advertising-ID
     collection and the 13+ stance is unchanged).
2. **`docs/release/data-safety-form.md`:** update line 81's "the **June 18, 2026** effective date"
   reference to "June 20, 2026" in lockstep (F5).
3. No app code, no test. Note in the PR body: GitHub Pages re-publish happens via `pages.yml` on merge;
   a human must confirm the live page refreshed (form acceptance checklist) — NOT claimable from the repo.

**Files:** `site/index.md`, `docs/release/data-safety-form.md`.

---

## Task 5 — ADRs

1. **Amend `docs/agent/DECISIONS/ADR-0006-ad-sdk.md`** Q5: add a dated status-amendment note that the
   adult/no-child-directed stance is **retained**, now **refined** with an explicit
   `maxAdContentRating = PG` `RequestConfiguration` set before the first request (closes #241). Do not
   rewrite the original decision — append the amendment (per the "amend status only if warranted" rule).
2. **New `docs/agent/DECISIONS/ADR-0032-privacy-monetization-wave.md`:** records the wave —
   in-app policy link (#240), policy/form consistency (#239), the maxAdContentRating decision and its
   13+ rationale (#241), and the rejected alternatives (age gate / child-directed). Reference the spec
   + this plan + the review.

**Files:** `ADR-0006-ad-sdk.md` (amend), new `ADR-0032-privacy-monetization-wave.md`.

---

## Task 6 — Sync current-state docs (PR Task-List Convention, BEFORE the STATE/RUN_LOG update)

Audit and touch ONLY what this PR actually invalidates:
- `CHANGELOG.md` — add an `[Unreleased]` entry for the wave (#240/#239/#241); update test count.
- `CLAUDE.md` — headline **test count** (+net-new tests from Tasks 1 & 3); no architecture/convention
  change otherwise.
- `docs/steering/source-files.md` — add entries for new files (`presentation/ui/PrivacyPolicy.kt`,
  the two new tests, ADR-0032); note the SettingsScreen new param.
- `docs/steering/security-model.md` — note the in-app privacy-policy link + the maxAdContentRating cap
  if the doc enumerates the ads/consent posture (check; touch only if it does).
- NOT touched: `docs/database-schema.md` (no schema), `docs/steering/tech.md`/`structure.md` (no new
  module/dep), `README.md` (no build/run change).

---

## Task 7 — STATE + RUN_LOG (end-of-run, after Task 6)

- Update `docs/agent/STATE.md` (current objective → this wave DONE/merged; recently-shipped entry;
  adjust the audit-backlog counts: #240/#239/#241 closed).
- Append `docs/agent/RUN_LOG.md` with what was done + what remains.
- (These are handled by the `/checkpoint` skill at session end.)

---

## Task 8 — Commit

After GREEN build + doc sync: commit on the branch with a message summarizing the three fixes + the
review (findings total/surviving/refuted) and open a PR. Do NOT push/commit until the developer
confirms (per harness rules — outward-facing action).

---

## Verification checklist (acceptance, from the spec)

- [ ] `PRIVACY_POLICY_URL` constant == form URL; `PrivacyPolicyUrlTest` GREEN.
- [ ] Settings shows a tappable "Privacy Policy" row; no-browser device = no-op (build-verified; ideally
      emulator-verified).
- [ ] `site/index.md` enumerates all 4 AdMob categories incl. approximate location; effective date
      June 20, 2026; `data-safety-form.md:81` synced.
- [ ] `buildAdRequestConfiguration()` sets maxAdContentRating=PG, no child/under-age tags;
      `AdRequestConfigurationTest` GREEN; `setRequestConfiguration` called before init in
      `ensureSdkInitialized`.
- [ ] ADR-0006 Q5 amended; ADR-0032 added.
- [ ] `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; JVM count = 1126 +
      net-new (Tasks 1 & 3 add tests); no regressions; `DomainPurityTest` green.
- [ ] Current-state docs synced; STATE/RUN_LOG updated.
