# ADR-0006: Real reward-ad SDK integration

**Date:** 2026-05-08 (Proposed), 2026-05-11 (Accepted)
**Status:** Accepted

## Context

All reward-ad functionality is currently stubbed via `StubRewardAdManager` (`app/src/main/java/.../data/ads/`). The stub satisfies the `RewardAdManager` interface (`domain/repository/`), `delay(1000)`s to simulate an ad view, and always returns `AdResult.Rewarded` from `showRewardAd(placement)`. Ad availability (`isAdAvailable(placement)`) always returns `true`. It was landed in Plan 26 under the same stub-then-swap pattern as `BillingManager`.

Three callers touch `RewardAdManager`:

- **`BattleViewModel`** — post-round `watchGemAd()` (+1 Gem) and `watchPsAd()` (double Power Stone rewards). Button surfaces on `PostRoundOverlay` when `adRemoved = false`.
- **`CardsViewModel`** — `watchFreePackAd()` (once-per-day free Common card pack). Button on `CardsScreen`.
- **`StoreScreen`** indirectly via `adRemoved` flag — if Ad Removal has been purchased (real or stub), the entire ad UI surface is hidden app-wide.

3 ad placements defined in `domain/model/AdPlacement.kt`: `POST_ROUND_GEM`, `POST_ROUND_DOUBLE_PS`, `DAILY_FREE_CARD_PACK`.

`AdResult` sealed class has three variants: `Rewarded`, `Cancelled`, `Error(message)`. All three must have realistic failure-path coverage before C.6 PR 1 lands; A.4 already exercised them on the stub fakes via `FakeRewardAdManager.resultQueue`.

Shipping v1.0 requires replacing the stub with a real integration. Unlike billing, **ads are opt-in** — the entire ad subsystem can fail gracefully (no ad → no bonus) without blocking core gameplay. This changes the risk profile: medium-high (vs. high for billing).

**External prerequisites** out of scope for the code PRs:

- AdMob app ID + 3 reward-ad unit IDs (one per `AdPlacement`) provisioned via AdMob console.
- Consent management platform (CMP) evaluation for GDPR / DSA compliance — needed before production release in EU.
- Play Store app signing certificate SHA-1 registered in AdMob for ad-serving.

## Decision

Implemented in C.6 PR 1 on 2026-05-11. Commitments:

1. **Library: Google Mobile Ads SDK v25.0.0** pinned exact
   (`com.google.android.gms:play-services-ads:25.0.0`) plus UMP
   (`com.google.android.ump:user-messaging-platform:4.0.0`). Version
   pinned, never ranged. v25 is the current stable line as of
   2026-05-11 per Google's Android SDK release notes.
2. **Impl location:** `data/ads/RewardAdManagerImpl.kt` alongside
   `StubRewardAdManager`. SDK-neutral adapter seam at
   `data/ads/internal/RewardedAdAdapter.kt` with the only concrete
   (and `com.google.android.gms.ads.*`-importing) implementation in
   `data/ads/internal/RealRewardedAdAdapter.kt`. Consent via
   `data/ads/internal/ConsentManager.kt` → `RealConsentManager` (the
   only file that imports `com.google.android.ump.*`). Both coexist
   under a future `BuildConfig.USE_REAL_ADS` flag introduced in
   PR 1 but only read from Hilt in PR 2.
3. **Preload-on-trigger** kept as the default; no startup preload.
   Rationale: 1-hour ad expiry + opt-in placement shape means any
   preload is usually wasted.
4. **`isAdAvailable()` returns `true`.** Real check happens inside
   `showRewardAd()` via `adapter.loadAd(adUnitId)`; a load failure
   surfaces as `AdResult.Error`.
5. **`OnUserEarnedRewardListener` is the single reward source.**
   `RealRewardedAdAdapter` flips an `AtomicBoolean` in
   `OnUserEarnedRewardListener.onUserEarnedReward`; the outer
   suspend-point resumes in `onAdDismissedFullScreenContent`, at
   which point the flag is final. The adapter maps to
   `SdkAdShowResult.Rewarded` only when the flag is true.
6. **Test-ad IDs in debug.** `BuildConfig.AD_UNIT_POST_ROUND_GEM`,
   `AD_UNIT_POST_ROUND_DOUBLE_PS`, and `AD_UNIT_DAILY_FREE_CARD_PACK`
   default to Google's documented rewarded-ad test unit
   (`ca-app-pub-3940256099942544/5224354917`) in debug. The AdMob
   APPLICATION_ID in `AndroidManifest.xml` is substituted via the
   `admobAppId` manifestPlaceholder; debug uses
   `ca-app-pub-3940256099942544~3347511713` (test app ID). Release
   builds override both from `local.properties` once C.6 PR 2
   wires that path; the defaults remain the safe test IDs so a
   misconfigured release build cannot mint revenue.
7. **UMP for consent.** `RealConsentManager` runs
   `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`
   on first ad attempt; result cached via UMP's own on-device
   store. Non-EU users see no form. Debug geography parameter left
   at UMP default (decide-by-IP); we do not force a debug override.
8. **Code-side frequency capping.** Caps remain in `BattleViewModel`
   (`RoundEndState.gemAdWatched`, `psAdWatched`) and
   `PlayerProfileEntity.freeCardPackAdUsedToday`. `RewardAdManagerImpl`
   does NOT consult these — the cap is a caller concern.
9. **3-PR rollout** as planned:
   - **PR 1 (this one, landed):** real impl + adapter seam + UMP
     glue + 6 unit tests covering every `AdResult` variant.
     `@Binds` still points at `StubRewardAdManager`.
   - **PR 2 (future):** flip `@Binds` behind `BuildConfig.USE_REAL_ADS`
     (mirroring C.5 PR 2's Provider-based switch in `BillingModule`).
     Add sibling `AdInternalModule` with `@Binds` for the adapter +
     consent manager. MainActivity consent-flow wiring.
   - **PR 3 (future):** delete `StubRewardAdManager` after internal +
     closed-track confirmation.

## Resolved open questions

| # | Question | Decision (2026-05-11, C.6 PR 1) |
|---|----------|----------------------------------|
| Q1 | **Consent-denied reward policy.** When UMP returns consent denied and AdMob serves non-personalised ads, do we still grant the reward? | **Yes.** The user watched the ad — the reward contract is fulfilled. `RewardAdManagerImpl` does not branch on personalisation; it credits on `SdkAdShowResult.Rewarded` regardless. |
| Q2 | **Ad load timeout.** Custom coroutine-level timeout wrapping `RewardedAd.load` (10 s) or defer to AdMob's internal ~60 s? | **Defer to AdMob.** A shorter wrapper would collapse distinct AdMob error codes (no-fill, network-error, timeout) into an undifferentiated "timed out" message, harming UX. The impl's `toUserMessage` helper translates AdMob codes to precise user-visible strings. |
| Q3 | **Per-session impression cap.** Combined cap across all 3 placements to prevent back-to-back ad watching? | **No.** Ads are opt-in and each placement is capped once-per-meaningful-event (once per round / once per day). A session cap is solving a hypothetical. |
| Q4 | **Mediation readiness.** Should PR 1's `RewardAdManagerImpl` be written for drop-in mediation swap later? | **No upfront abstraction.** YAGNI — the existing adapter seam (`RewardedAdAdapter`) already lets a future mediation layer plug in via a new concrete impl without touching `RewardAdManagerImpl`. Adding mediation structure now would invent abstractions for a future we may never build. |
| Q5 | **COPPA / child-directed flag.** Mark the AdMob app as child-directed to stay COPPA-safe? | **No.** The game targets adults. Play Store age rating + `minSdk 34` (Android 14) handle the kid-safety surface. Marking child-directed would suppress personalised ads for all users, reducing eCPM for no compliance benefit. |
| Q6 | **Test-ad render on release debug builds.** Internal-track testers: real ads or test ads? | **Test IDs persist across debug builds; release uses real IDs once PR 2 wires them.** Internal-track testers installing the release build will see real ads — that is the point of internal-track (verify the real ad surface). Minting a small amount of real-test revenue during QA is acceptable. |

## Rationale

- **No mediation in v1.0.** Ad mediation (AdMob + AppLovin MAX / Unity LevelPlay) boosts fill rate but adds a whole compliance surface (each SDK has its own consent + data-handling requirements). Ads are already opt-in reward ads in a niche fitness game — fill rate is not the release-critical metric. Post-v1.0 if eCPM is unacceptable, revisit.
- **AdMob over alternatives.** Google's SDK has the broadest Play Store acceptance, best documented test infrastructure, and is the only SDK with a UMP that speaks GDPR + DSA out of the box. Meta Audience Network was deprecated. Unity Ads is better for games but a second SDK setup + test matrix; defer to post-v1.0 mediation.
- **Preload-on-trigger over startup-preload.** The app's ad surface is small (3 placements, opt-in, usually invoked < 3 times/session). A startup preload pays the cost for every session, and the 1-hour ad expiry means the preload is frequently wasted. Trigger-time load keeps cost proportional to use.
- **`onUserEarnedReward()` is non-negotiable.** Every AdMob integration guide warns about rewarding on `onAdDismissed` (users skip the ad, app still rewards). The user-earned-reward callback fires only when the ad is watched past the AdMob-enforced threshold. This is the single correctness invariant for the whole reward-ad path.
- **UMP over a hardcoded IP-to-EU mapping.** Google handles the legal nuance (which geographies are GDPR, which are DSA-only, which are US state laws). UMP is free and ships with the AdMob SDK. A custom solution would be worse on every axis.
- **Test-ad IDs in debug builds.** The alternative — every dev needing an AdMob account — is hostile to contributors and CI. Google's documented test IDs render house ads, fire all callbacks, never mint real revenue. This is the standard AdMob flow for non-production builds.
- **Code-side frequency capping.** Two reasons: (a) the caps are game-design decisions (once per round, once per day), not ad-network decisions — owning them in code keeps them discoverable + testable; (b) AdMob's frequency cap is an eCPM optimisation, not a behaviour contract. Mixing the two creates a test matrix.

## Consequences

- New `play-services-ads:<pinned>` dependency in `gradle/libs.versions.toml`. Pinned version, never ranged. Also pulls in `user-messaging-platform` for consent.
- New `app/proguard-rules.pro` keep rules per AdMob release notes (ad view classes, listener interfaces, reflection-based ad-format parsing).
- `BuildConfig.USE_REAL_ADS` + `BuildConfig.<PLACEMENT>_AD_UNIT_ID` (three fields, one per placement) added via `app/build.gradle.kts`. Production values sourced from `local.properties` (gitignored) + an AdMob app ID in `AndroidManifest.xml` meta-data.
- New `AndroidManifest.xml` entry: `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="@string/admob_app_id"/>`. Debug vs release values diverge via resource qualifiers or build types.
- `StubRewardAdManager` eventually deleted — one call site (`di/AdModule.kt` `@Binds`) and the `FakeRewardAdManager` continues to cover VM tests; it does not grow real-ad awareness.
- `BattleViewModel` + `CardsViewModel` contracts **unchanged** — the swap is pure impl-side. Existing guard logic (`watchGemAd()` short-circuit on `_purchasing`, `_freePackAdUsedToday` flag) stays as-is.
- UMP consent form may add a first-run UX step for EU users. Non-EU users see no consent dialog (UMP decides based on IP). The form is AdMob-provided, not a custom in-app screen.
- First-run data usage increases slightly (consent check + initial ad load on first `watchX()`). Bounded; no persistent background ad activity.
- Real ads incur AdMob-policy compliance: privacy policy must disclose AdMob data collection (already done in `docs/release/privacy-policy.md`), the app must not place ads over real content, etc. All documented in AdMob policy.

## Non-goals / future work

- **No banner ads, no interstitials in v1.0.** Only reward ads (already limited by `AdPlacement` enum). Banners would clutter the battle UI; interstitials would violate the "no forced ads" design principle in `docs/monetization.md`.
- **No ad mediation.** Single source (AdMob direct). Revisit post-v1.0 if eCPM is unacceptable.
- **No AdMob-side frequency capping.** Code-side only.
- **No house-ads or cross-promo.** AdMob's house-ads are disabled in favour of paid fills.
- **No rewarded interstitials.** Only rewarded-video ads (`RewardedAd`, not `RewardedInterstitialAd`). Simpler UX, simpler reward contract.
- **No A/B tests on ad placement or reward amount.** Hardcoded in `AdPlacement` + the caller VMs.
- **Do not refactor `RewardAdManager` interface.** Stays stable through the swap. A future `reloadAll()` method for startup preload would be a separate PR.

## Open questions

All six open questions answered on 2026-05-11 in the "Resolved open questions" table above.
See PR description of commit for C.6 PR 1 for the reasoning trail on each.

## References

- `devdocs/evolution/implementation_roadmap.md` §C.6 — full 3-PR breakdown with files, success criteria, risk mitigations, verification, rollback.
- `devdocs/evolution/refactoring_opportunities.md` — no direct RO entry (ads are gap-fill, not refactor).
- `docs/plans/plan-26-monetization.md` — original stub architecture.
- `docs/plans/plan-31-play-console.md` — AdMob console setup as an external-step dependency.
- `docs/monetization.md` — placement table + opt-in principle.
- `docs/release/privacy-policy.md` — pre-existing AdMob data-handling disclosure.
- `CONSTRAINTS.md` — "no forced ads" invariant.
- `app/src/main/java/.../domain/model/AdPlacement.kt` — placement enum + AdResult sealed class.
- `app/src/main/java/.../domain/repository/RewardAdManager.kt` — interface contract.
- `app/src/main/java/.../data/ads/StubRewardAdManager.kt` — current behaviour reference, to be deleted in C.6 PR 3.
- `app/src/test/java/.../fakes/FakeRewardAdManager.kt` — test-path coverage, stays through the swap.
- ADR-0005 — parallel billing-swap decision with matching 3-PR rollout shape.
