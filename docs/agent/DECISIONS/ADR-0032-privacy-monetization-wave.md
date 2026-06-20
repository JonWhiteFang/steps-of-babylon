# ADR-0032: Privacy / monetization wave — in-app policy link, policy/form consistency, AdMob content-rating cap

Status: Accepted (2026-06-20)

## Context

The 2026-06-18 complete-app review (`docs/reviews/2026-06-18-complete-app-review.md`) filed a cluster of
before-public privacy/ads-policy findings. Three were taken as one combined wave (none is an
internal-track blocker; all are self-contained, low-risk, and share the same artifacts):

- **#240** (medium) — the only in-app path to the privacy policy was the Health Connect permission
  rationale (`HealthConnectPermissionActivity.kt:47`), which many users never trigger. Play's User Data
  policy expects an easily accessible in-app link.
- **#239** (medium, partial) — the hosted policy (`site/index.md`) disclosed only the advertising ID,
  while the Play Console Data-Safety form (`docs/release/data-safety-form.md`) declares **four** AdMob
  SDK categories (Device/other IDs, Approximate location, App interactions, Diagnostics). Play requires
  the policy to be consistent with the Data-Safety declaration; approximate location is a sensitive
  category that was entirely absent from the policy.
- **#241** (medium) — no `RequestConfiguration` existed anywhere in source. The `AdRequest` was built
  plain (`RealRewardedAdAdapter.kt:76`) and `MobileAds.initialize` ran with no global config; the policy
  asserted "not directed to children under 13" but the code did nothing to bound served-ad content.

ADR-0006 Q5 had previously decided **not** to mark the app child-directed (adult-targeted; child-directed
would suppress personalised ads for all users, reducing eCPM).

## Decision

### #240 — in-app privacy-policy link
- New single-source-of-truth constant `presentation/ui/PrivacyPolicy.kt` →
  `PRIVACY_POLICY_URL = "https://jonwhitefang.github.io/steps-of-babylon/"` (the same URL declared in the
  Data-Safety form and served from `site/index.md`). A JVM drift-guard test (`PrivacyPolicyUrlTest`)
  pins it so the in-app link, the form URL, and the hosted page can't silently diverge.
- A "Privacy Policy" row in `SettingsScreen.kt`'s **Data** section (above "Delete All Data" so the
  destructive action stays last) → new `onOpenPrivacyPolicy` callback → `MainActivity.openPrivacyPolicy`,
  a guarded `Intent(ACTION_VIEW, …)` (resolve-before-launch, no-browser device = safe no-op) reusing the
  `requestBatteryExemption` idiom.
- **Onboarding link declined.** #240's "optional" primer-slide link is not added — onboarding is
  one-shot (existing players never re-see it); Settings is the durable, always-reachable entry point
  (same reasoning as the #261 battery-row). External browser, not an in-app webview (no new dependency).

### #239 — policy/form consistency
- `site/index.md` now enumerates all four AdMob categories (incl. **approximate location derived from
  IP**) under "Advertising Identifier" + "Data Sharing", with present-tense "collected by Google, not by
  us" framing. Effective date bumped **June 18 → June 20, 2026**; the form's acceptance-checklist
  effective-date reference (`data-safety-form.md:81`) synced in lockstep. Children's-Privacy section left
  unchanged (already consistent with the 13+ stance). Doc-only — no app code, no manual Console action
  (the form was already authored under #192; this only reconciles the public policy *text*).

### #241 — AdMob content-rating cap (developer decision: 13+ adult, cap rating only)
- ADR-0006 Q5 stance **retained and refined**: still **no** age gate and **no**
  `tagForChildDirectedTreatment` / `tagForUnderAgeOfConsent` — only a conservative `maxAdContentRating`.
- Pure `buildAdRequestConfiguration()` returns
  `RequestConfiguration.Builder().setMaxAdContentRating(MAX_AD_CONTENT_RATING_PG).build()`;
  `ensureSdkInitialized()` calls `MobileAds.setRequestConfiguration(...)` as the first statement inside
  its `withContext(Dispatchers.Main)` block, before `MobileAds.initialize` — so the cap is in effect for
  every `AdRequest`. The value is JVM-unit-tested (`AdRequestConfigurationTest`: PG + both tags
  `UNSPECIFIED`); the static SDK call stays device/build-verified.

## Rationale

- **13+ cap-only over an age gate / child-directed flag.** The game targets adults and the policy says
  "not directed to children under 13." A `maxAdContentRating = PG` bound makes the *served-ad content*
  match that text with zero UX change and no eCPM loss, while an age gate would add onboarding friction
  and `tagForChildDirectedTreatment` would suppress personalised ads for every user. PG (not G) is the
  conservative-but-reasonable choice for a 13+ casual game.
- **Placement inside the adapter (not the Application).** Keeps all GMA-SDK touch confined to
  `RealRewardedAdAdapter` (the only `com.google.android.gms.ads.*`-importing file) and matches the
  preload-on-trigger architecture (ADR-0006 #3) — cost proportional to use; the config doesn't run on
  cold starts that never show an ad. Safety rests on `@Singleton` adapter + an idempotent, global,
  order-independent static setter, not on the per-instance `AtomicBoolean`.
- **Single URL constant.** Play requires the in-app link == Data-Safety-form URL == hosted page; one
  constant + a drift test enforces that invariant in the build.

## Consequences

- No schema / economy / engine change. New tests: `PrivacyPolicyUrlTest`, `AdRequestConfigurationTest`.
- `AdRequestConfigurationTest` depends on the module's `unitTests.isReturnDefaultValues = true`
  (`app/build.gradle.kts:198`) to absorb the internal `android.util.Log.w` call inside
  `setMaxAdContentRating` — do not remove that flag.
- A human must still confirm the live GitHub Pages policy refreshed (Pages caches) — noted in the PR; not
  claimable from the repo. The separate manual Play Console Data-Safety submission (#192) is unaffected.

## References

- Spec: `docs/superpowers/specs/2026-06-20-privacy-monetization-wave-240-239-241.md` (reviewed).
- Plan: `docs/superpowers/specs/2026-06-20-privacy-monetization-wave-240-239-241-plan.md` (reviewed).
- ADR-0006 (ad SDK) — Q5 amended in lockstep.
- Issues #240, #239, #241; review `docs/reviews/2026-06-18-complete-app-review.md`.
