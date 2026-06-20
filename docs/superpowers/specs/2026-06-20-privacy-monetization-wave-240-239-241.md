# Spec — Privacy / Monetization wave (#240 · #239 · #241)

**Date:** 2026-06-20
**Status:** REVIEWED (single-agent Adversarial Review Gate, ultracode off — developer chose lighter
review; 6 surviving findings F1–F6 [0 critical/major], all applied 2026-06-20). Ready for plan stage.
**Scope:** three confirmed before-public findings from the 2026-06-18 complete-app review, one
combined PR. Presentation + a single SDK-config call + policy text. **No schema / economy / engine
change.**

## Why these three together

All three are Play-policy "before public" findings in the privacy/ads surface, none is an
internal-track blocker, all are self-contained and low-risk, and they share the same review artifacts
(`site/index.md`, `data-safety-form.md`, the ads layer). Combining them mirrors how this project ships
hardening waves (one build-verified PR).

## #240 — No in-app privacy-policy link (medium · small)

**Confirmed.** The only in-app path to the policy is the Health Connect rationale text
(`HealthConnectPermissionActivity.kt:47`), which many users never trigger. `SettingsScreen.kt` has no
link. Play's User Data policy expects an in-app, easily accessible link.

**Fix.** Add a "Privacy Policy" row to `SettingsScreen.kt`'s **Data** section (an `OutlinedCard`
matching the existing "Background activity" / "Delete All Data" row idiom) that opens the hosted policy
via an `ACTION_VIEW` intent. Wire it through a new `onOpenPrivacyPolicy` callback on `SettingsScreen`,
fired from `MainActivity`'s `composable(Screen.Settings.route)` block — following the existing
`onOptimizeBattery = { requestBatteryExemption(context) }` callback-wiring idiom
(`MainActivity.kt:386-391`).

**Design details:**
- New top-level `private fun openPrivacyPolicy(context)` in `MainActivity.kt`, reusing the **guard
  idiom** of `requestBatteryExemption` (`MainActivity.kt:468-479`): build
  `Intent(Intent.ACTION_VIEW, Uri.parse(URL))`, guard with `resolveActivity(context.packageManager)
  != null` before `startActivity` so a device with no browser is a no-op (never crashes). Note there
  is **no existing `ACTION_VIEW` external-launch precedent** in `MainActivity` — the closest existing
  launches are the unguarded `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` deep-links
  (`MainActivity.kt:270-275`, `327-334`); we deliberately use the *guarded* form
  (`requestBatteryExemption`-style) here, which is the safer pattern. No `FLAG_ACTIVITY_NEW_TASK`
  needed — launched from the Activity context. (F4)
- **Single source of truth for the URL.** Introduce one shared constant rather than a second string
  literal. The URL currently lives as plain prose in `HealthConnectPermissionActivity.kt:47`. Add
  `const val PRIVACY_POLICY_URL = "https://jonwhitefang.github.io/steps-of-babylon/"` — placement
  decided in the plan stage (candidate: a small `presentation/ui` or top-level constant; do **not**
  add it to `domain/` — it is presentation/config, and `domain/` must stay pure). The Health Connect
  string is user-facing prose with the URL embedded mid-sentence; leaving that prose as-is is
  acceptable (it is a localizable rationale string, not a clickable link), but the new clickable link
  MUST use the constant.
- Row copy: title "Privacy Policy", subtitle "How your data is handled" (or similar; final wording in
  plan). Decorative leading icon optional (the existing Delete row uses `Icons.Filled.Delete`).
- The row opens the **external** hosted policy (consistent with the Data-Safety form's declared URL),
  not an in-app webview — no new dependency, matches the existing Health Connect link behaviour.
- **Onboarding link — DECLINED (F3).** Issue #240 calls a primer-slide link "optional". We decline it:
  onboarding is a one-shot flow (existing players never re-see it), so Settings is the durable,
  always-reachable entry point — the same reasoning the code already documents for the #261 battery
  row (`SettingsScreen.kt:73-75`). One canonical in-app link satisfies Play's "easily accessible"
  expectation.

**Test.** `SettingsScreen` has no existing test and is a `@Composable` requiring a Compose/Robolectric
harness — out of step with this repo's JVM-only norm (Compose UI tests are tracked separately as #253).
The **testable seam** is the intent-builder: extract `privacyPolicyIntent(context): Intent` (or assert
the `PRIVACY_POLICY_URL` constant value + that the click path calls the shared helper). Plan stage
decides the exact seam; at minimum a JVM test pins `PRIVACY_POLICY_URL` to the exact Data-Safety-form
URL (drift guard — if someone changes one, the test fails). Do **not** stand up a Robolectric Compose
harness for this (the repo abandoned that approach — see ADR-0023 / `BottomNavRestoreTest` history).

## #239 — Policy text omits 3 of the 4 AdMob data categories (medium · small · *partial*)

**Confirmed-partial** (3 refuters: 1✓/2~). The Data-Safety form (`data-safety-form.md:43-48`) declares
**four** AdMob-collected categories — Device/other IDs, App interactions, Diagnostics, **Approximate
location** — but `site/index.md` only discusses the advertising ID / device IDs. Play requires the
policy to be consistent with the Data-Safety declaration. Location is a sensitive category and is
entirely absent from the policy.

**Fix.** Add an explicit enumeration to `site/index.md` (under "Advertising Identifier (Google AdMob)"
and/or "Data Sharing") naming all four categories Google's AdMob SDK collects/shares, matching the
form. Present-tense, scoped "collected by Google, not by us" framing consistent with the existing text.
Bump the **Effective Date** (currently "June 18, 2026") to the change date and keep it in sync with any
"effective date" reference in the Data-Safety form.

**Design details:**
- Enumerate: (1) advertising ID + device/app-set IDs, (2) **approximate location derived from IP
  address**, (3) app-interaction events (ad launches, taps, video views), (4) diagnostics (e.g. launch
  time, hang rate, energy usage) — phrasing aligned to `data-safety-form.md:43-48` so the public policy
  and the form read consistently.
- Keep the "we do not ourselves collect/transmit this; Google's SDK does, governed by Google's policy"
  framing — accurate and already the document's voice.
- `site/index.md` is the **single canonical** policy (header comment lines 6-11) and the **only** page
  published to GitHub Pages. Edit only `site/index.md`. Do NOT reintroduce a `docs/`-tree copy.
- **Cross-doc coherence:** after editing, the four categories in `site/index.md` MUST match
  `data-safety-form.md`'s four rows (`data-safety-form.md:45-48`). If wording is refined, update both.
- **Effective-date sync (F5).** Bumping `site/index.md`'s effective date staleness `data-safety-form.md:81`,
  which hard-codes "the **June 18, 2026** effective date" in its acceptance checklist. Update that
  line in lockstep — so `data-safety-form.md` **is** in the change set for #239 (it is a `release/`
  doc, editable under the doc-sweep rules, not a frozen historical artifact).
- **Children's Privacy section audit (F6).** `site/index.md:81-83` ("not directed to children under
  13", + an AdMob advertising-ID mention) is already consistent with the #241 13+/no-age-gate
  decision — leave its stance unchanged. Once approximate-location disclosure is added above, confirm
  this paragraph isn't left under-disclosing relative to the new text (it need not repeat the full
  four-category list; a one-line check, no rewrite expected).
- **No app code change** for #239 — pure hosted-policy text. (Publishing is the existing `pages.yml`
  lane on merge; no manual Play Console re-entry needed since the URL is unchanged — but a human must
  still confirm the live page refreshed, per the form's checklist. Note this in the PR, do not claim
  it as done from the repo.)

## #241 — No AdMob RequestConfiguration / content-rating cap (medium · medium · confirmed)

**Confirmed.** No `RequestConfiguration` / `setRequestConfiguration` exists anywhere in source. The
`AdRequest` is built plain (`RealRewardedAdAdapter.kt:76`) and `MobileAds.initialize` runs with no
global config (`RealRewardedAdAdapter.kt:142`).

**Decision (developer, 2026-06-20): 13+ adult, cap content rating only.** Keep ADR-0006 Q5's
adult-targeted stance — **no** age gate, **no** `tagForChildDirectedTreatment`, **no**
`tagForUnderAgeOfConsent`. Add **only** a conservative `maxAdContentRating` bound. This refines (does
not reverse) ADR-0006 Q5, preserves personalized-ad eCPM, adds zero UX, and is consistent with the
policy's "not directed to children under 13" text.

**Fix.** Set a `RequestConfiguration` with
`maxAdContentRating = RequestConfiguration.MAX_AD_CONTENT_RATING_PG` (or `_G` — final value in plan;
PG is the conservative-but-reasonable choice for a 13+ casual game) via
`MobileAds.setRequestConfiguration(...)` **before the first ad request**.

**Design details — placement:**
- `setRequestConfiguration` is global, static, process-level SDK state independent of init order, but
  MUST be set before the first `AdRequest.Builder().build()` (`RealRewardedAdAdapter.kt:76`).
- Init is deliberately **lazy / preload-on-trigger** (ADR-0006 #3) inside
  `RealRewardedAdAdapter.ensureSdkInitialized()` (`RealRewardedAdAdapter.kt:135-145`) — NOT at app
  startup. Two candidate insertion points:
  - **(A) Inside `ensureSdkInitialized()`**, as the **first statement inside the
    `withContext(Dispatchers.Main)` block and before `MobileAds.initialize` (line 142)** — NOT before
    the `withContext` (it must stay on the Main path with init). Keeps all GMA-SDK touch confined to
    the one file that already imports `com.google.android.gms.ads.*` (the adapter is the only such
    file — architectural cleanliness). **Preferred** — see rationale.
  - (B) `StepsOfBabylonApp.onCreate()`. Guaranteed-earliest, but pulls a GMA import into the
    Application class (currently ads-free) and runs the config on **every** cold start even when no ad
    is ever shown (the app is opt-in ads; many sessions show none). Rejected unless the plan review
    surfaces a correctness reason A fails.
- **Rationale / safety for (A) (F1):** matches the preload-on-trigger architecture (cost proportional
  to use), confines the GMA dependency to `RealRewardedAdAdapter`, and is provably before the first
  request (the setter precedes init, which precedes the `AdRequest.build()` in the same `loadAd`
  call). The safety argument rests on **`@Singleton` adapter + a global, idempotent, order-independent
  static setter** — NOT on the per-instance `AtomicBoolean` (`initialized`, line 64), which only
  ensures once-per-adapter-instance execution. Even a stray re-set would be a harmless idempotent
  no-op; the AtomicBoolean simply avoids the redundant call.

**Test (F2 — resolved, commit to the real seam).** `RealRewardedAdAdapter`'s `initialize`/`load`/`show`
SDK calls are device-only / un-mockable (final SDK classes) — no existing test (Explore report §6;
`RealRewardedAdAdapter.kt:27-31`). **But `RequestConfiguration` itself IS JVM-constructible without an
initialized SDK** — verified against play-services-ads 25.4.0 bytecode by the spec reviewer:
`RequestConfiguration.Builder().<init>()` touches no Android and no `MobileAds` static state,
`setMaxAdContentRating(String)`/`build()` are pure, and `MAX_AD_CONTENT_RATING_PG`/`_G` are
ConstantValue-inlined Strings. So:
- Extract a pure `buildAdRequestConfiguration(): RequestConfiguration` (returns the configured value;
  no SDK init, no Android).
- `ensureSdkInitialized` calls `MobileAds.setRequestConfiguration(buildAdRequestConfiguration())` (the
  static call stays device/build-verified, but the *value* is now testable).
- JVM test asserts `buildAdRequestConfiguration().maxAdContentRating == RequestConfiguration.MAX_AD_CONTENT_RATING_PG`
  **and** that the child-directed + under-age tags remain `TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED`
  / `TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED` (locks the 13+/no-flag decision against regression).
This exercises the actual behaviour, not just a constant pin — no fabricated test, no Robolectric.

## Out of scope (explicit non-goals)

- **No age gate / no child-directed flag** (per the audience decision). #241's "if mixed-audience"
  branch is deliberately not taken.
- **No UMP `setTagForUnderAgeOfConsent`** on `RealConsentManager`'s `ConsentRequestParameters`
  (`RealConsentManager.kt:83`) — that is the mixed-audience path, not taken.
- **No in-app webview** for the policy (external intent only).
- **No manual Play Console action** — #239's form is already authored (`data-safety-form.md`); this
  wave only reconciles the *policy text*. The manual Data-Safety submission (#192) remains a separate
  developer step.
- **No schema / migration / economy / engine change.** None of the three touches Room, currency, or the
  game loop.

## Files expected to change

| File | Change | Issue |
|---|---|---|
| `presentation/settings/SettingsScreen.kt` | add Privacy Policy row + `onOpenPrivacyPolicy` param | #240 |
| `presentation/MainActivity.kt` | wire `onOpenPrivacyPolicy` + new `openPrivacyPolicy(context)` helper | #240 |
| (new) shared constant holder for `PRIVACY_POLICY_URL` | single source of truth | #240 |
| `presentation/HealthConnectPermissionActivity.kt` | (optional) reference the shared constant | #240 |
| `data/ads/internal/RealRewardedAdAdapter.kt` | `setRequestConfiguration` before init | #241 |
| `site/index.md` | enumerate the 4 AdMob categories + bump effective date | #239 |
| `docs/release/data-safety-form.md` | sync the effective-date reference (line 81) in lockstep (F5) | #239 |
| `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` | status-amend Q5 with the maxAdContentRating refinement | #241 |
| (new) `docs/agent/DECISIONS/ADR-0032-*.md` | the wave decision record | all |
| tests | URL-drift guard (#240) + config-value seam (#241) | #240/#241 |

## Acceptance criteria

1. Settings has a visible, tappable "Privacy Policy" row that opens the hosted policy; no-browser
   device is a safe no-op.
2. `PRIVACY_POLICY_URL` is a single constant equal to the Data-Safety-form URL; a JVM test pins it.
3. `site/index.md` enumerates all four AdMob data categories (incl. approximate location), consistent
   with `data-safety-form.md`; effective date bumped.
4. `MobileAds.setRequestConfiguration` sets `maxAdContentRating` (G/PG) before the first ad request; no
   child-directed / under-age tag set.
5. `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; JVM count moves only by
   the net-new tests (no regressions).
6. ADR-0006 Q5 amended; ADR-0032 records the wave.
