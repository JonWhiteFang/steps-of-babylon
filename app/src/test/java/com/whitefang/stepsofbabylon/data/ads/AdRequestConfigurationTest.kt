package com.whitefang.stepsofbabylon.data.ads

import com.google.android.gms.ads.RequestConfiguration
import com.whitefang.stepsofbabylon.data.ads.internal.buildAdRequestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * #241: pins the AdMob [RequestConfiguration] applied before the first ad request to the developer
 * decision (2026-06-20, ADR-0032): cap ad content at PG for a 13+ audience, and DO NOT mark the app
 * child-directed or under-age — the adult stance of ADR-0006 Q5 is retained, only the rating is
 * bounded. Any future regression (raising the rating, or adding a child/under-age tag) fails here.
 *
 * Runs in plain JVM: the builder/getters touch no `android.*`; the one Android touch
 * (`setMaxAdContentRating` logs via `android.util.Log.w` internally) is neutralised by the module's
 * `unitTests.isReturnDefaultValues = true` (app/build.gradle.kts) — no Robolectric, no SDK init.
 */
class AdRequestConfigurationTest {
    @Test
    fun `max ad content rating is capped at PG`() {
        assertEquals(
            RequestConfiguration.MAX_AD_CONTENT_RATING_PG,
            buildAdRequestConfiguration().maxAdContentRating,
        )
    }

    @Test
    fun `child-directed treatment is left unspecified (no child-directed flag)`() {
        assertEquals(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            buildAdRequestConfiguration().tagForChildDirectedTreatment,
        )
    }

    @Test
    fun `under-age-of-consent tag is left unspecified (no age gate)`() {
        assertEquals(
            RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED,
            buildAdRequestConfiguration().tagForUnderAgeOfConsent,
        )
    }
}
