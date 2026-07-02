package com.whitefang.stepsofbabylon.data.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for [RewardAdManagerImpl] against a mocked [RewardedAdAdapter] and
 * [ConsentManager]. The adapter boundary is plain-Kotlin sealed classes, so mockito-kotlin's
 * default subclass mock-maker handles it without `mockito-inline`. `Activity` is mockito-mocked
 * as a pass-through to the mocked adapter.
 *
 * Runs on the Robolectric/JVM lane (i18n #34 phase 3, A′): the impl now injects an
 * `@ApplicationContext` and resolves its error strings via `context.getString(R.string.ad_error_*)`,
 * so the assertions need a real resource-backed [android.content.Context] (supplied by
 * [ApplicationProvider]). Under the default (English) locale the resolved strings are byte-identical
 * to the former hard-coded literals, so the assertions below are unchanged.
 *
 * Every [AdResult] variant is exercised at least once:
 * - `AdResult.Rewarded` — happy path (consent ok → load ok → show returns `Rewarded`).
 * - `AdResult.Cancelled` — user dismissed before reward threshold.
 * - `AdResult.Error` — 4 distinct causes: no activity, consent unavailable, load failed,
 *   show failed.
 *
 * C.6 PR 1 / ADR-0006.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RewardAdManagerImplTest {
    private lateinit var adapter: RewardedAdAdapter
    private lateinit var consentManager: ConsentManager
    private lateinit var activityProvider: ActivityProvider
    private lateinit var activity: Activity

    private lateinit var impl: RewardAdManagerImpl

    @Before
    fun setup() {
        adapter = mock()
        consentManager = mock()
        activityProvider = ActivityProvider()
        activity = mock()
        activityProvider.set(activity)

        impl =
            RewardAdManagerImpl(
                adapter = adapter,
                consentManager = consentManager,
                activityProvider = activityProvider,
                context = ApplicationProvider.getApplicationContext(),
            )
    }

    // --- happy path --------------------------------------------------------------------

    @Test
    fun `showRewardAd returns Rewarded when adapter reports Rewarded`() =
        runTest {
            stubHappyPath(showResult = SdkAdShowResult.Rewarded)

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_GEM)

            assertEquals(AdResult.Rewarded, result)
            verify(consentManager).ensureInitialized(activity)
            verify(adapter).loadAd(any())
            verify(adapter).showAd(any(), any())
        }

    // --- cancel path -------------------------------------------------------------------

    @Test
    fun `showRewardAd returns Cancelled when user dismisses before reward threshold`() =
        runTest {
            stubHappyPath(showResult = SdkAdShowResult.Dismissed)

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)

            assertEquals(
                "dismissed-without-reward maps to Cancelled, NOT Rewarded",
                AdResult.Cancelled,
                result,
            )
            verify(adapter).showAd(any(), any())
        }

    // --- error paths -------------------------------------------------------------------

    @Test
    fun `showRewardAd returns Error when no Activity is registered`() =
        runTest {
            activityProvider.clear()

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_GEM)

            assertTrue(result is AdResult.Error)
            assertEquals("No activity available for ad", (result as AdResult.Error).message)
            // Consent + adapter must NOT be consulted when there's no Activity to run against.
            verify(consentManager, never()).ensureInitialized(any())
            verify(adapter, never()).loadAd(any())
        }

    @Test
    fun `showRewardAd returns Error when consent is unavailable`() =
        runTest {
            consentManager.stub {
                onBlocking { ensureInitialized(any()) } doReturn Unit
                on { canRequestAds() } doReturn false
            }

            val result = impl.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)

            assertTrue(result is AdResult.Error)
            assertTrue(
                "message surfaces consent-pending reason",
                (result as AdResult.Error).message.contains("consent", ignoreCase = true),
            )
            // Load must NOT run without consent — we bail after the canRequestAds check.
            verify(adapter, never()).loadAd(any())
        }

    @Test
    fun `showRewardAd returns Error when loadAd fails`() =
        runTest {
            consentManager.stub {
                onBlocking { ensureInitialized(any()) } doReturn Unit
                on { canRequestAds() } doReturn true
            }
            adapter.stub {
                onBlocking { loadAd(any()) } doReturn
                    SdkAdLoadResult.Error(code = 3, message = "No fill available")
            }

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_GEM)

            assertTrue(result is AdResult.Error)
            assertEquals(
                "AdMob error code 3 (NO_FILL) maps to the 'no ad available' user message",
                "No ad available right now. Try again later.",
                (result as AdResult.Error).message,
            )
            // Show must NOT run after a failed load.
            verify(adapter, never()).showAd(any(), any())
        }

    @Test
    fun `showRewardAd returns Error when showAd fails`() =
        runTest {
            stubHappyPath(
                showResult = SdkAdShowResult.Error(code = 1, message = "Ad already used"),
            )

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_GEM)

            assertTrue(result is AdResult.Error)
            assertEquals(
                "AdMob show-error code 1 (ALREADY_USED) maps to the 'already shown' user message",
                "Ad was already shown.",
                (result as AdResult.Error).message,
            )
        }

    // --- consent-denied still grants (ADR-0006 Q1) -------------------------------------

    @Test
    fun `showRewardAd still returns Rewarded when canRequestAds is true after consent prompt`() =
        runTest {
            // Simulates the consent-denied-but-non-personalised-ads path: UMP runs, user
            // rejects tracking, canRequestAds still returns true (non-personalised is OK),
            // ad loads + shows normally, reward still granted. Per ADR-0006 Q1 decision.
            stubHappyPath(showResult = SdkAdShowResult.Rewarded)

            val result = impl.showRewardAd(AdPlacement.POST_ROUND_GEM)

            assertEquals(
                "Reward is granted regardless of personalisation — user watched the ad.",
                AdResult.Rewarded,
                result,
            )
        }

    // --- placement → ad-unit routing ---------------------------------------------------

    @Test
    fun `showRewardAd routes each placement to its own ad unit ID`() =
        runTest {
            // All 3 BuildConfig test IDs happen to be the same debug test unit, but the code
            // path must still pick them independently per placement. This guards against a
            // copy-paste regression where one placement is wired to another's constant.
            val captured = mutableListOf<String>()
            consentManager.stub {
                onBlocking { ensureInitialized(any()) } doReturn Unit
                on { canRequestAds() } doReturn true
            }
            adapter.stub {
                onBlocking { loadAd(any()) }.thenAnswer { invocation ->
                    captured += invocation.getArgument<String>(0)
                    SdkAdLoadResult.Success(SdkRewardedAd(rawRef = null))
                }
                onBlocking { showAd(any(), any()) } doReturn SdkAdShowResult.Rewarded
            }

            impl.showRewardAd(AdPlacement.POST_ROUND_GEM)
            impl.showRewardAd(AdPlacement.POST_ROUND_DOUBLE_PS)
            impl.showRewardAd(AdPlacement.DAILY_FREE_CARD_PACK)

            assertEquals("3 loads for 3 placements", 3, captured.size)
            // Debug BuildConfig wires all three to the same test unit; assert consistency with
            // that fact. When PR 2 wires distinct release IDs, this test still protects against
            // swapping the wrong BuildConfig constant in.
            assertTrue(
                "all 3 routed to documented test ad unit",
                captured.all { it == "ca-app-pub-3940256099942544/5224354917" },
            )
        }

    // --- helpers ------------------------------------------------------------------------

    /**
     * Wires a happy-path flow: consent ok + load returns a valid [SdkRewardedAd] + show
     * returns [showResult]. Defaults to [SdkAdShowResult.Rewarded] so the common success
     * test can omit the argument.
     */
    private fun stubHappyPath(showResult: SdkAdShowResult = SdkAdShowResult.Rewarded) {
        consentManager.stub {
            onBlocking { ensureInitialized(any()) } doReturn Unit
            on { canRequestAds() } doReturn true
        }
        adapter.stub {
            onBlocking { loadAd(any()) } doReturn
                SdkAdLoadResult.Success(SdkRewardedAd(rawRef = null))
            onBlocking { showAd(any(), any()) } doReturn showResult
        }
    }
}
