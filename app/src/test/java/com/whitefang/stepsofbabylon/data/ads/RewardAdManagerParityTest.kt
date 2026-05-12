package com.whitefang.stepsofbabylon.data.ads

import android.app.Activity
import com.whitefang.stepsofbabylon.data.ads.internal.ConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RewardedAdAdapter
import com.whitefang.stepsofbabylon.data.ads.internal.SdkAdLoadResult
import com.whitefang.stepsofbabylon.data.ads.internal.SdkAdShowResult
import com.whitefang.stepsofbabylon.data.ads.internal.SdkRewardedAd
import com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider
import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Shape-level parity check between [StubRewardAdManager] and [RewardAdManagerImpl] on the
 * golden path for each of the 3 [AdPlacement] values. The C.6 PR 2 flag swap changes which
 * implementation Hilt binds at runtime based on `BuildConfig.USE_REAL_ADS`, so we need
 * evidence that the user-visible reward outcome is equivalent for both sides when every
 * upstream dependency (consent, load, show) is happy.
 *
 * **What "parity" means here.**
 *
 * - Both implementations return [AdResult.Rewarded] on a successful flow.
 * - [StubRewardAdManager.showRewardAd] returns it directly after a `delay(1000)`.
 * - [RewardAdManagerImpl.showRewardAd] returns it after a consent check + adapter load +
 *   adapter show — all mocked to happy responses here.
 *
 * **What "parity" does NOT mean.**
 *
 * - We do NOT attempt to run `RealRewardedAdAdapter` or `RealConsentManager` against
 *   Google Play Services or the Mobile Ads SDK — those are device-only. The real side
 *   mocks both interfaces and asserts that a happy-path mock response produces the same
 *   [AdResult] as the stub.
 * - We do NOT compare timings (the stub sleeps 1s; the real path is mock-driven).
 * - Failure parity is out of scope because the stub has no failure modes at all — all
 *   `AdResult.Error` + `AdResult.Cancelled` coverage lives in [RewardAdManagerImplTest].
 *
 * Mirrors the [com.whitefang.stepsofbabylon.data.billing.BillingManagerParityTest] shape
 * from C.5 PR 2. C.6 PR 2 / ADR-0006.
 */
class RewardAdManagerParityTest {

    private lateinit var adapter: RewardedAdAdapter
    private lateinit var consentManager: ConsentManager
    private lateinit var activityProvider: ActivityProvider
    private lateinit var activity: Activity

    private lateinit var stub: StubRewardAdManager
    private lateinit var real: RewardAdManagerImpl

    @Before
    fun setup() {
        adapter = mock()
        consentManager = mock()
        activityProvider = ActivityProvider()
        activity = mock()
        activityProvider.set(activity)

        stub = StubRewardAdManager()
        real = RewardAdManagerImpl(
            adapter = adapter,
            consentManager = consentManager,
            activityProvider = activityProvider,
        )

        // Both impls will go through the same happy-path flow; wire the adapter + consent
        // once so each @Test can focus on the per-placement assertions.
        stubHappyPath()
    }

    @Test
    fun `POST_ROUND_GEM parity - both impls return Rewarded on happy path`() = runTest {
        val stubResult = stub.showRewardAd(AdPlacement.POST_ROUND_GEM)
        val realResult = real.showRewardAd(AdPlacement.POST_ROUND_GEM)

        assertEquals(AdResult.Rewarded, stubResult)
        assertEquals(AdResult.Rewarded, realResult)
        assertEquals("stub + real return identical AdResult", stubResult, realResult)
    }

    @Test
    fun `POST_ROUND_DOUBLE_PS parity - both impls return Rewarded on happy path`() = runTest {
        val stubResult = stub.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)
        val realResult = real.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)

        assertEquals(AdResult.Rewarded, stubResult)
        assertEquals(AdResult.Rewarded, realResult)
        assertEquals("stub + real return identical AdResult", stubResult, realResult)
    }

    @Test
    fun `DAILY_FREE_CARD_PACK parity - both impls return Rewarded on happy path`() = runTest {
        val stubResult = stub.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)
        val realResult = real.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)

        assertEquals(AdResult.Rewarded, stubResult)
        assertEquals(AdResult.Rewarded, realResult)
        assertEquals("stub + real return identical AdResult", stubResult, realResult)
    }

    @Test
    fun `isAdAvailable parity - both impls return true for every placement`() {
        for (placement in AdPlacement.values()) {
            assertEquals(
                "$placement: stub.isAdAvailable",
                true,
                stub.isAdAvailable(placement),
            )
            assertEquals(
                "$placement: real.isAdAvailable (per ADR-0006 decision 4 — real check is in showRewardAd)",
                true,
                real.isAdAvailable(placement),
            )
        }
    }

    // --- helpers ------------------------------------------------------------------------

    /**
     * Wires the real impl's upstream dependencies to a happy-path flow: UMP consent is
     * initialized and permits ad requests, the adapter loads a fresh ad, and the show
     * returns [SdkAdShowResult.Rewarded] — equivalent to the user watching through to the
     * AdMob reward threshold.
     */
    private fun stubHappyPath() {
        consentManager.stub {
            onBlocking { ensureInitialized(any()) } doReturn Unit
            on { canRequestAds() } doReturn true
        }
        adapter.stub {
            onBlocking { loadAd(any()) } doReturn
                SdkAdLoadResult.Success(SdkRewardedAd(rawRef = null))
            onBlocking { showAd(any(), any()) } doReturn SdkAdShowResult.Rewarded
        }
    }
}
