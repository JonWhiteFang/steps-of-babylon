package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Drift guard (#240): the in-app Privacy Policy link, the Play Console Data-Safety form's declared
 * URL (`docs/release/data-safety-form.md:66`), and the hosted page (`site/index.md`) MUST all be the
 * same URL — Play requires the in-app link to match the Data-Safety declaration. If anyone changes one
 * without the others, this fails the build.
 */
class PrivacyPolicyUrlTest {

    @Test
    fun `privacy policy URL matches the Data-Safety form declared URL`() {
        assertEquals("https://jonwhitefang.github.io/steps-of-babylon/", PRIVACY_POLICY_URL)
    }
}
