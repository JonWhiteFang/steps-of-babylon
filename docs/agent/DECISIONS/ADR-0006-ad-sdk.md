# ADR-0006: Real reward-ad SDK integration

**Date:** 2026-05-08
**Status:** Proposed (stub — concrete decision deferred to C.6 PR 1 scoping)

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

## Decision (stub)

**TBD.** The concrete design — exact AdMob library version pin, test-ad strategy, CMP choice, frequency-capping policy — will be finalised when C.6 PR 1 is scoped. The decision recorded here is the commitment to:

1. **Library: Google AdMob SDK** (`com.google.android.gms:play-services-ads:<pinned>`). Most-recent stable at C.6 PR 1 landing date. No third-party ad mediation in v1.0.
2. **Impl location:** `data/ads/RewardAdManagerImpl.kt` alongside `StubRewardAdManager`. Both coexist under a `BuildConfig.USE_REAL_ADS` flag mirroring the C.5 / ADR-0005 billing swap.
3. **Preload-on-trigger, not upfront.** Load the `RewardedAd` when the `ViewModel.watchX()` method fires, not at app startup. Reasons:
   - Ads expire after 1 hour; preloading at app open wastes the load if the player doesn't trigger within the window.
   - Reduces startup latency + initial data usage.
   - Simpler failure semantics — no need to reconcile "stale preload".
4. **`isAdAvailable()` returns true** by default. The real check happens inside `showRewardAd()` via `RewardedAd.load()`; if load fails the method returns `AdResult.Error`. Pre-load availability is an optimisation left for post-v1.0.
5. **User-earned-reward callback is the single source of truth.** Only increment the reward on `OnUserEarnedRewardListener.onUserEarnedReward()` — not on dismiss, not on impression. Matches AdMob's documented reward-ad contract.
6. **Test-ad IDs in debug builds.** Debug builds use Google's documented reward-ad test unit (`ca-app-pub-3940256099942544/5224354917`) so a dev can exercise the real SDK path without a production AdMob account. Release builds use real IDs provisioned via `local.properties` → `BuildConfig.<PLACEMENT>_AD_UNIT_ID`. Production IDs never in git.
7. **Privacy: opt-in CMP, not a hardcoded geo check.** Use AdMob's User Messaging Platform (UMP) for GDPR/DSA consent. Consent status cached on-device; refresh daily. Fall back to non-personalised ads if consent is denied.
8. **Frequency capping: enforce in code, not in AdMob.**
   - `POST_ROUND_GEM` + `POST_ROUND_DOUBLE_PS`: once per round (already enforced by `RoundEndState.gemAdWatched` / `psAdWatched` flags).
   - `DAILY_FREE_CARD_PACK`: once per day, tracked on `PlayerProfileEntity.freeCardPackAdUsedToday` (already shipped). Resets at midnight via existing date-rollover logic.
   - No additional AdMob-side frequency caps.
9. **3-PR rollout** mirroring C.5:
   - **PR 1:** New `RewardAdManagerImpl` + UMP consent glue + unit tests with mocked `RewardedAd`. `@Binds` still points at `StubRewardAdManager`. Zero runtime behaviour change.
   - **PR 2:** Flip `@Binds` to `RewardAdManagerImpl` behind `BuildConfig.USE_REAL_ADS`. Flag defaults to false in debug, true in release.
   - **PR 3:** Delete `StubRewardAdManager` after internal + closed-track confirmation.

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

## Open questions (to be resolved in C.6 PR 1 scoping)

- **Consent-denied reward policy.** If UMP returns "consent denied" (user rejects tracking), AdMob still serves non-personalised ads. Do we still grant the reward when a non-personalised ad is watched? (Default: yes — the ad was watched, the reward contract is fulfilled. Documented here for explicit review.)
- **Ad load timeout.** AdMob's default is ~60 s for the `RewardedAd.load()` call. Do we surface a progress indicator, or `AdResult.Error` after a shorter timeout (say 10 s)? UX question; defaults to AdMob.
- **Ad impression-capping per placement.** The game has 3 placements with different expected frequencies. Do we want a combined per-session cap to prevent an over-enthusiastic player from watching 20 ads back-to-back? Default: no, but flag for review.
- **Mediation readiness.** Should the PR 1 `RewardAdManagerImpl` be written to allow a drop-in mediation layer later? Trade-off: more abstraction now vs. easier later swap.
- **COPPA / child-directed flag.** The game targets adults but steps + walking are a common kid-friendly category. Do we mark the AdMob app as child-directed to keep COPPA-safe? (Default: no, minSdk + Play Store age rating handle this.)
- **Test-ad render on release debug builds?** During the internal-track rollout window, is it safe to serve real ads to debug-build testers, or should internal-track also use test IDs? Trade-off between testing the real ad surface and not minting real revenue during QA.

All six will be decided in the PR description for C.6 PR 1 (which will upgrade this ADR from "Proposed (stub)" to "Accepted" with concrete answers).

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
