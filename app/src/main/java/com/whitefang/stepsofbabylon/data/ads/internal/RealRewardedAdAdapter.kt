package com.whitefang.stepsofbabylon.data.ads.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [RewardedAdAdapter] backed by Google Mobile Ads SDK v25. This is the ONLY file
 * in the app that imports `com.google.android.gms.ads.*` types; everything else talks to
 * the adapter interface via the SDK-neutral sealed classes in [RewardedAdAdapter.kt].
 *
 * **Testability.** This class is device-only testable — the Google Mobile Ads SDK requires
 * a live Google Play Services connection and its classes are final + have package-private
 * constructors that mockito cannot subclass. Unit-level coverage for the ad pipeline runs
 * against a mocked [RewardedAdAdapter] in `RewardAdManagerImplTest`. Manual verification
 * on the internal Play Store test track is the release-gate for changes to this file.
 *
 * **SDK init (ADR-0006 decision #3: preload-on-trigger).** We lazily call
 * [MobileAds.initialize] on first [loadAd] invocation rather than at app startup — the
 * app's 3 reward placements are all opt-in (post-round + daily free pack), so paying the
 * init cost only when the user actually triggers an ad keeps startup latency cleanest.
 * [MobileAds.initialize] is idempotent; concurrent first-callers are safe.
 *
 * **Reward attribution (ADR-0006 decision #5).** The `rewarded` flag is flipped to `true`
 * ONLY inside [OnUserEarnedRewardListener.onUserEarnedReward], which AdMob guarantees fires
 * before [FullScreenContentCallback.onAdDismissedFullScreenContent]. By the time we
 * resume the suspend-point in `onAdDismissed`, the flag already reflects whether the user
 * watched to the reward threshold. Rewarding on `onAdDismissed` without the flag would
 * let a user skip the ad and still receive the reward — the classic AdMob footgun.
 *
 * **Timeout policy (ADR-0006 decision #Q2).** We defer to AdMob's ~60-second internal
 * timeout on [RewardedAd.load]. Wrapping in a shorter coroutine timeout was considered and
 * rejected — AdMob surfaces failures as `LoadAdError` with specific codes (no-fill,
 * network-error, timeout) that let the UI show a precise message. A wrapping timeout would
 * collapse all failures into an undifferentiated "timed out" string.
 *
 * Introduced by C.6 PR 1 / ADR-0006.
 */
@Singleton
internal class RealRewardedAdAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : RewardedAdAdapter {

    /**
     * Set the first time [loadAd] is invoked and kept true thereafter. [MobileAds.initialize]
     * itself is idempotent, but avoiding the redundant Play Services round-trip per ad load
     * saves a few tens of ms on second-and-subsequent loads.
     */
    private val initialized = AtomicBoolean(false)

    override suspend fun loadAd(adUnitId: String): SdkAdLoadResult {
        ensureSdkInitialized()

        val result = CompletableDeferred<SdkAdLoadResult>()
        // AdMob's RewardedAd.load REQUIRES the Main thread despite being asynchronous under
        // the hood. Calling from Dispatchers.IO throws IllegalStateException on device.
        withContext(Dispatchers.Main) {
            RewardedAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        result.complete(SdkAdLoadResult.Success(SdkRewardedAd(rawRef = ad)))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // error.code is the AdMob ERROR_CODE_* constant (NO_FILL=3, etc);
                        // error.message is the human-readable diagnostic. We pass both
                        // through unchanged — the impl translates to a user-friendly
                        // message.
                        result.complete(
                            SdkAdLoadResult.Error(code = error.code, message = error.message),
                        )
                    }
                },
            )
        }
        return result.await()
    }

    override suspend fun showAd(activity: Activity, loadedAd: SdkRewardedAd): SdkAdShowResult {
        val rewardedAd = loadedAd.rawRef as? RewardedAd
            ?: return SdkAdShowResult.Error(-1, "Invalid SDK ref — expected RewardedAd")

        val result = CompletableDeferred<SdkAdShowResult>()
        val rewarded = AtomicBoolean(false)

        withContext(Dispatchers.Main) {
            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // Fires AFTER onUserEarnedReward (if rewarded) and AFTER any manual
                    // user-skip. By the time we reach here the `rewarded` flag is final.
                    result.complete(
                        if (rewarded.get()) SdkAdShowResult.Rewarded else SdkAdShowResult.Dismissed,
                    )
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "onAdFailedToShowFullScreenContent: code=${error.code} ${error.message}")
                    result.complete(
                        SdkAdShowResult.Error(code = error.code, message = error.message),
                    )
                }
            }

            rewardedAd.show(
                activity,
                OnUserEarnedRewardListener { _ ->
                    // Fires exactly when the user crosses AdMob's reward threshold. Setting
                    // the flag is cheap; the actual resume happens in onAdDismissed so the
                    // full-screen sequence completes before control returns.
                    rewarded.set(true)
                },
            )
        }
        return result.await()
    }

    private suspend fun ensureSdkInitialized() {
        if (!initialized.compareAndSet(false, true)) return
        withContext(Dispatchers.Main) {
            // MobileAds.initialize is a one-shot; the completion-listener variant lets us
            // block until Play Services returns the adapter-status map. We ignore the map
            // (no mediation in v1.0 per ADR-0006 non-goals).
            val deferred = CompletableDeferred<Unit>()
            MobileAds.initialize(context) { _ -> deferred.complete(Unit) }
            deferred.await()
        }
    }

    companion object {
        private const val TAG = "RealRewardedAdAdapter"
    }
}
