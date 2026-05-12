package com.whitefang.stepsofbabylon.data.ads.internal

import android.app.Activity

/**
 * SDK-neutral seam between [com.whitefang.stepsofbabylon.data.ads.RewardAdManagerImpl] and
 * Google's User Messaging Platform (UMP) SDK. One concrete implementation — [RealConsentManager] —
 * is the only file that references `com.google.android.ump.*` types.
 *
 * **Contract.** [ensureInitialized] runs the UMP consent flow: request consent info update,
 * show the privacy form if required, and cache the result. [canRequestAds] is a cheap
 * post-init check used by [com.whitefang.stepsofbabylon.data.ads.RewardAdManagerImpl] to
 * short-circuit the ad load when UMP has not yet determined consent status.
 *
 * **Consent-denied reward policy (ADR-0006 Q1 = yes).** If the user rejects personalised
 * tracking, UMP still allows non-personalised ads via [canRequestAds] returning `true`.
 * Per the ADR decision, we still grant the reward when such an ad is watched — the user
 * watched the ad, the reward contract is fulfilled. This manager does not branch on
 * personalised vs. non-personalised; that is AdMob's concern.
 *
 * **Scope.** PR 1 ships this abstraction but does NOT wire it into MainActivity yet. The
 * real in-app consent form will surface the first time a user triggers a reward ad in
 * release builds (C.6 PR 2 flags that on). Debug builds bind `StubRewardAdManager` and
 * never construct this class.
 *
 * Introduced by C.6 PR 1 / ADR-0006.
 */
internal interface ConsentManager {

    /**
     * Runs the UMP consent flow idempotently:
     *
     * 1. Request consent-info update from AdMob's consent servers.
     * 2. If a privacy form is required AND not yet shown, display it on [activity] and
     *    suspend until the user dismisses it.
     * 3. Cache the result in UMP's on-device store; subsequent calls short-circuit.
     *
     * **Must be called from a coroutine backed by a main-thread dispatcher** — UMP's
     * callbacks fire on the main thread and its form-display needs an Activity. Errors
     * during the update/display are logged but do NOT throw; the caller should check
     * [canRequestAds] to decide whether to proceed.
     */
    suspend fun ensureInitialized(activity: Activity)

    /**
     * Returns `true` when UMP considers the user's consent state sufficient for an ad
     * request (either personalised or non-personalised). Returns `false` only when the
     * SDK has not yet been initialized OR the user is in a geography where consent is
     * mandatory and has explicitly declined.
     *
     * **Cheap — no suspension, no network.** Safe to call from any dispatcher.
     */
    fun canRequestAds(): Boolean
}
