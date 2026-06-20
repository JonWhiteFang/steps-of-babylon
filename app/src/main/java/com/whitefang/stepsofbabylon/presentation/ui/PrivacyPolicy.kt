package com.whitefang.stepsofbabylon.presentation.ui

/**
 * Single source of truth for the hosted privacy-policy URL (#240).
 *
 * This is the same URL declared in the Play Console Data-Safety form
 * (`docs/release/data-safety-form.md`) and served from `site/index.md` via GitHub Pages. Play requires
 * the in-app privacy-policy link to match the Data-Safety declaration, so all references go through
 * this constant; [com.whitefang.stepsofbabylon.presentation.ui.PrivacyPolicyUrlTest] pins the value.
 */
const val PRIVACY_POLICY_URL = "https://jonwhitefang.github.io/steps-of-babylon/"
