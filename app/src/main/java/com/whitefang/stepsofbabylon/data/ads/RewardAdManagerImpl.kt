package com.whitefang.stepsofbabylon.data.ads

import android.util.Log
import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.data.ads.internal.ConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RewardedAdAdapter
import com.whitefang.stepsofbabylon.data.ads.internal.SdkAdLoadResult
import com.whitefang.stepsofbabylon.data.ads.internal.SdkAdShowResult
import com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider
import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [RewardAdManager] implementation wiring Google Mobile Ads SDK v25 + UMP consent.
 * Introduced by C.6 PR 1 / ADR-0006.
 *
 * **Wiring history.**
 *
 * - **C.6 PR 1** — landed this class + adapter + UMP seam; Hilt `@Binds` still pointed
 *   at `StubRewardAdManager`.
 * - **C.6 PR 2** — flipped the binding behind `BuildConfig.USE_REAL_ADS` (debug→stub,
 *   release→real) via a Provider-based switch; MainActivity began prefetching UMP consent
 *   on first resume in release builds.
 * - **C.6 PR 3** — deleted `StubRewardAdManager` after internal-track verification. This
 *   class is now the only `RewardAdManager` binding for both debug and release. See
 *   [com.whitefang.stepsofbabylon.di.AdModule].
 *
 * **ActivityProvider is shared with billing.** The existing
 * [com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider] (C.5 PR 2)
 * already holds the live [android.app.Activity] reference via MainActivity lifecycle,
 * which is exactly what `RewardedAd.show()` needs. Sharing it avoids a second
 * WeakReference holder; the class was general-purpose from the start. A future refactor
 * may relocate it from `data/billing/internal/` to a shared `data/platform/` location,
 * but that is cosmetic churn and stays out of this PR.
 *
 * **Design invariants (from ADR-0006 decisions):**
 *
 * 1. **Preload-on-trigger, not upfront (#3).** Each [showRewardAd] call runs a fresh
 *    [RewardedAdAdapter.loadAd]; we do not cache preloaded ads across calls. Ads expire
 *    after 1 hour — caching would risk serving stale instances or paying for unused loads.
 * 2. **Reward attribution via `OnUserEarnedRewardListener` only (#5).** The adapter
 *    surfaces this as [SdkAdShowResult.Rewarded], which we map to [AdResult.Rewarded].
 *    [SdkAdShowResult.Dismissed] (user-skip before reward threshold) maps to
 *    [AdResult.Cancelled] — the reward MUST NOT be credited. This is the single
 *    correctness invariant for the reward-ad path.
 * 3. **Consent via UMP, consent-denied still grants reward (Q1).** If the user is in a
 *    GDPR/DSA geography and declines personalised tracking, UMP still permits
 *    non-personalised ads. We do NOT gate the reward on personalisation — the user
 *    watched the ad, the reward contract is fulfilled.
 * 4. **No custom timeout (Q2).** AdMob's internal ~60-second timeout handles
 *    `RewardedAd.load`. Wrapping in a shorter coroutine-level timeout would collapse
 *    distinct AdMob error codes (no-fill, network-error, timeout) into an undifferentiated
 *    "timed out" message. Callers see precise SDK messages via [AdResult.Error].
 * 5. **No per-session frequency cap (Q3).** Per-placement caps already exist in game
 *    state (`RoundEndState.gemAdWatched`, `freeCardPackAdUsedToday`). A combined
 *    session cap was considered and rejected — ads are opt-in and each placement is
 *    capped once-per-meaningful-event.
 * 6. **No mediation scaffolding (Q4).** YAGNI; revisit post-v1.0 if eCPM demands it.
 *    The adapter seam keeps a future mediation layer plug-in-able without touching this
 *    class.
 *
 * **Frequency-cap ownership stays at the ViewModel.** This class does NOT consult
 * `RoundEndState.gemAdWatched` or `PlayerProfile.freeCardPackAdUsedToday` — those remain
 * the caller's responsibility. An ad shown via direct `billingManager.showRewardAd()`
 * call (e.g. during testing) will therefore succeed even in states where the UI would
 * hide the button. This is intentional and matches the prior stub's behaviour.
 */
@Singleton
internal class RewardAdManagerImpl
    @Inject
    constructor(
        private val adapter: RewardedAdAdapter,
        private val consentManager: ConsentManager,
        private val activityProvider: ActivityProvider,
    ) : RewardAdManager {
        /**
         * Serialises [showRewardAd] so two concurrent callers cannot race into overlapping
         * `RewardedAd.show()` invocations. AdMob serialises show internally, but letting two
         * parallel `load` + `show` pairs queue up is wasteful and confuses the user with two
         * ad surfaces in rapid succession.
         */
        private val sessionMutex = Mutex()

        override suspend fun showRewardAd(placement: AdPlacement): AdResult =
            sessionMutex.withLock {
                val activity =
                    activityProvider.current()
                        ?: return@withLock AdResult.Error("No activity available for ad")

                // 1. Make sure UMP has run its consent flow at least once. No-op after the first
                //    successful run per session (UMP itself caches across launches).
                runCatching { consentManager.ensureInitialized(activity) }
                    .onFailure {
                        Log.w(
                            TAG,
                            "Consent init failed; proceeding with canRequestAds()=${consentManager.canRequestAds()}",
                            it,
                        )
                    }

                if (!consentManager.canRequestAds()) {
                    return@withLock AdResult.Error("Can't request ads yet — consent pending")
                }

                // 2. Load a fresh RewardedAd for this placement.
                val adUnitId = adUnitIdForPlacement(placement)
                val loaded =
                    when (val load = adapter.loadAd(adUnitId)) {
                        is SdkAdLoadResult.Success -> {
                            load.ad
                        }

                        is SdkAdLoadResult.Error -> {
                            Log.w(TAG, "loadAd($placement) failed: code=${load.code} ${load.message}")
                            return@withLock AdResult.Error(load.toUserMessage())
                        }
                    }

                // 3. Show it. The adapter suspends until the AdMob full-screen sequence completes
                //    (reward fired OR user dismissed OR show-time error).
                return@withLock when (val show = adapter.showAd(activity, loaded)) {
                    is SdkAdShowResult.Rewarded -> {
                        AdResult.Rewarded
                    }

                    is SdkAdShowResult.Dismissed -> {
                        AdResult.Cancelled
                    }

                    is SdkAdShowResult.Error -> {
                        Log.w(TAG, "showAd($placement) failed: code=${show.code} ${show.message}")
                        AdResult.Error(show.toUserMessage())
                    }
                }
            }

        /**
         * Always returns `true` — the real availability check happens inside [showRewardAd]
         * where a load failure surfaces as [AdResult.Error]. A cached availability query was
         * considered (ADR-0006 decision #4) and rejected as post-v1.0 work.
         */
        override fun isAdAvailable(placement: AdPlacement): Boolean = true

        private fun adUnitIdForPlacement(placement: AdPlacement): String =
            when (placement) {
                AdPlacement.POST_ROUND_GEM -> BuildConfig.AD_UNIT_POST_ROUND_GEM
                AdPlacement.POST_ROUND_DOUBLE_PS -> BuildConfig.AD_UNIT_POST_ROUND_DOUBLE_PS
                AdPlacement.DAILY_FREE_CARD_PACK -> BuildConfig.AD_UNIT_DAILY_FREE_CARD_PACK
            }

        /**
         * Translates an AdMob load-error code into a user-visible message. AdMob's documented
         * codes are stable across SDK versions.
         */
        private fun SdkAdLoadResult.Error.toUserMessage(): String =
            when (code) {
                0 -> "Ad request was invalid. (Internal error.)"
                1 -> "Ad request was invalid. (Invalid request.)"
                2 -> "Network error. Check your connection and try again."
                3 -> "No ad available right now. Try again later."
                else -> "Couldn't load ad. (code $code)"
            }

        /**
         * Translates an AdMob show-error code into a user-visible message.
         */
        private fun SdkAdShowResult.Error.toUserMessage(): String =
            when (code) {
                0 -> "Ad couldn't play. (Internal error.)"
                1 -> "Ad was already shown."
                2 -> "Ad isn't ready to show."
                3 -> "Ad couldn't play. (App not in foreground.)"
                else -> "Couldn't show ad. (code $code)"
            }

        companion object {
            private const val TAG = "RewardAdManagerImpl"
        }
    }
