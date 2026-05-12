package com.whitefang.stepsofbabylon.data.ads.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [ConsentManager] backed by Google's User Messaging Platform (UMP) SDK v4. This
 * is the ONLY file in the app that imports `com.google.android.ump.*` types.
 *
 * **Flow (first call per session):**
 *
 * 1. [UserMessagingPlatform.getConsentInformation] returns a `ConsentInformation` singleton
 *    backed by UMP's on-device cache.
 * 2. `requestConsentInfoUpdate` asks AdMob whether the user needs a consent prompt
 *    (based on geography, previous answers, and the message type configured in the
 *    AdMob console).
 * 3. If a form is required, [UserMessagingPlatform.loadAndShowConsentFormIfRequired]
 *    displays it; otherwise this is a no-op.
 * 4. Both branches complete the suspend-point so the caller can proceed to [canRequestAds].
 *
 * **Subsequent calls** short-circuit via `initialized` once UMP reports a final status
 * (either form dismissed or no form required). The UMP SDK itself caches more aggressively
 * across app launches.
 *
 * **Error policy.** A failure in `requestConsentInfoUpdate` (network error, AdMob
 * configuration drift) does NOT throw — it is logged, `initialized` is still flipped so
 * we do not re-attempt on every ad request, and [canRequestAds] is allowed to return
 * whatever UMP's cached state is. The caller checks [canRequestAds] to decide whether
 * to proceed with a load.
 *
 * **Not wired into MainActivity in PR 1.** The first invocation happens the first time a
 * user triggers a reward ad in a release build (which, for PR 1, never happens — `@Binds`
 * still points at `StubRewardAdManager`). PR 2 changes that.
 *
 * Device-only testable — the UMP SDK requires a live Play Services connection and its
 * classes cannot be mocked. Unit coverage for the consent flow runs against a mocked
 * [ConsentManager] interface in `RewardAdManagerImplTest`.
 *
 * Introduced by C.6 PR 1 / ADR-0006.
 */
@Singleton
internal class RealConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConsentManager {

    /**
     * UMP's consent-information singleton. Lazy-init because its construction triggers a
     * Play Services lookup that we want to defer to first use (mirrors the
     * preload-on-trigger shape from the ads pipeline).
     */
    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    /**
     * Guards the one-shot [ensureInitialized] flow so concurrent first-callers (e.g. two
     * viewmodels launching ads in parallel) do not both run the UMP request.
     */
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    override suspend fun ensureInitialized(activity: Activity): Unit = initMutex.withLock {
        if (initialized) return@withLock

        // Use unspecified debug geography in release; AdMob decides by IP.
        val params = ConsentRequestParameters.Builder().build()
        val deferred = CompletableDeferred<Unit>()

        // All UMP callbacks fire on the main thread; run the request from Main too so the
        // form-display call that follows has a valid Activity context.
        withContext(Dispatchers.Main) {
            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    // onConsentInfoUpdateSuccess
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            Log.w(TAG, "Consent form error: code=${formError.errorCode} ${formError.message}")
                        }
                        initialized = true
                        if (!deferred.isCompleted) deferred.complete(Unit)
                    }
                },
                { updateError ->
                    // onConsentInfoUpdateFailure — log + mark initialized so we don't
                    // loop. canRequestAds() may still return true if UMP has a cached
                    // prior decision.
                    Log.w(TAG, "Consent update failed: code=${updateError.errorCode} ${updateError.message}")
                    initialized = true
                    if (!deferred.isCompleted) deferred.complete(Unit)
                },
            )
        }
        deferred.await()
    }

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    companion object {
        private const val TAG = "RealConsentManager"
    }
}
