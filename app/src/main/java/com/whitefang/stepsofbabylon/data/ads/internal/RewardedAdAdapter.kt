package com.whitefang.stepsofbabylon.data.ads.internal

import android.app.Activity

/**
 * SDK-neutral seam between [com.whitefang.stepsofbabylon.data.ads.RewardAdManagerImpl] and
 * the Google Mobile Ads SDK. The impl talks to this interface only; the one concrete
 * implementation — [RealRewardedAdAdapter] — is the single place in the codebase that
 * references `com.google.android.gms.ads.*` types.
 *
 * Purpose (mirrors the Play Billing `BillingClientAdapter` precedent from C.5 PR 1):
 *
 * - **Testability.** `RewardedAd`, `LoadAdError`, `FullScreenContentCallback`, `AdRequest`,
 *   and friends are final classes (or `abstract` with package-private constructors)
 *   that cannot be mocked with the default mockito-subclass mock-maker. By contrast, this
 *   interface is plain Kotlin and mocks with zero configuration, which is what
 *   `RewardAdManagerImplTest` relies on.
 * - **Anti-corruption layer.** SDK types leak listener-callback shape, nullable
 *   `RewardItem`, and response-code magic numbers into anything that touches them.
 *   Collapsing all of that into a suspend-and-sealed-class surface keeps the impl's logic
 *   SDK-version-agnostic.
 * - **Version upgrade insulation.** When Google Mobile Ads SDK ships breaking changes
 *   (v25 already renamed `RewardedVideoAd` → `RewardedAd`; future majors are expected),
 *   only [RealRewardedAdAdapter] needs changes — the contract with the impl stays stable.
 *
 * **Per-call lifecycle.** Every [loadAd] call produces a fresh [SdkRewardedAd]; that
 * instance is consumed once by [showAd] and then discarded. AdMob's `RewardedAd` is
 * single-use by design — after a successful show, the SDK invalidates the instance. The
 * impl preloads-on-trigger (ADR-0006 decision #3) so there is no cached instance to
 * invalidate separately.
 *
 * Introduced by C.6 PR 1 / ADR-0006.
 */
internal interface RewardedAdAdapter {

    /**
     * Loads a rewarded ad for [adUnitId] from AdMob. Returns [SdkAdLoadResult.Success] with
     * a fresh [SdkRewardedAd] on success, or [SdkAdLoadResult.Error] with the SDK's
     * response code + message on any failure (no-fill, network error, invalid ad unit,
     * etc.). The SDK's ~60-second timeout applies; per ADR-0006 Q2 we surface that as a
     * plain Error with no custom timeout wrapper.
     */
    suspend fun loadAd(adUnitId: String): SdkAdLoadResult

    /**
     * Shows [loadedAd] to the user from [activity] and suspends until the user either
     * earns the reward (via AdMob's `OnUserEarnedRewardListener`), dismisses the ad
     * without earning, or the SDK reports a show-time failure (e.g. ad already shown, no
     * activity).
     *
     * Per AdMob's reward-ad contract (ADR-0006 decision #5), the reward is only credited
     * when [SdkAdShowResult.Rewarded] is returned — never on dismiss.
     *
     * **The `loadedAd` is single-use.** Callers must NOT reuse it across multiple
     * [showAd] calls — AdMob invalidates the instance after show. Load a new one.
     */
    suspend fun showAd(activity: Activity, loadedAd: SdkRewardedAd): SdkAdShowResult
}

/**
 * Response shape from [RewardedAdAdapter.loadAd]. A successful load produces a fresh
 * [SdkRewardedAd] whose `rawRef` holds the underlying SDK object for [RewardedAdAdapter.showAd]
 * to consume; test code constructs instances with `rawRef = null`.
 */
internal sealed class SdkAdLoadResult {
    data class Success(val ad: SdkRewardedAd) : SdkAdLoadResult()
    data class Error(val code: Int, val message: String) : SdkAdLoadResult()
}

/**
 * Response shape from [RewardedAdAdapter.showAd]. [Rewarded] is the only outcome that
 * should trigger a reward credit in the caller — [Dismissed] covers user-skip, and
 * [Error] covers every other show-time failure.
 */
internal sealed class SdkAdShowResult {
    /** User watched the ad to the reward threshold. Credit the reward. */
    data object Rewarded : SdkAdShowResult()

    /** User dismissed the ad before earning the reward. Do NOT credit. */
    data object Dismissed : SdkAdShowResult()

    /** SDK-level error (ad already shown, no activity, show-time failure). */
    data class Error(val code: Int, val message: String) : SdkAdShowResult()
}

/**
 * Platform-neutral projection of AdMob's `RewardedAd`. Carries only an opaque [rawRef]
 * back to the SDK instance so the real adapter can pass it to `RewardedAd.show()`. Tests
 * construct instances with `rawRef = null`.
 */
internal data class SdkRewardedAd(val rawRef: Any?)
