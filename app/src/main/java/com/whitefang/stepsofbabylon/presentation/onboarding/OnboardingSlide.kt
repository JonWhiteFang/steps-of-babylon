package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.Biome

/** Decorative art slot for a slide (Bundle E, #164). Pure marker — OnboardingScreen maps it to an
 *  @DrawableRes. Kept out of the model (not an @DrawableRes Int / ImageVector) so the slide list
 *  stays Android-free and JVM-testable. */
enum class OnboardingArt { ZIGGURAT, }

/**
 * One tutorial slide. Pure Kotlin (emoji icon, not an ImageVector) so the content list is
 * JVM-testable without Android. [isPermissionPrimer] marks the final slide that owns the
 * activity-recognition permission ask. [biome] supplies the per-slide background gradient (Bundle E,
 * #164) via BiomeTheme; [art] overrides the emoji [icon] with a vector asset (slide 1's ziggurat).
 * Both new fields are pure (a domain enum / a marker enum), preserving JVM-testability.
 */
data class OnboardingSlide(
    val icon: String,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val isPermissionPrimer: Boolean = false,
    val biome: Biome? = null,
    val art: OnboardingArt? = null,
)

/** Canonical first-launch tutorial content. Final slide is the permission primer. */
object OnboardingContent {
    val slides: List<OnboardingSlide> =
        listOf(
            OnboardingSlide(
                icon = "🏛️",
                titleRes = R.string.onboarding_slide1_title,
                bodyRes = R.string.onboarding_slide1_body,
                biome = Biome.HANGING_GARDENS,
                art = OnboardingArt.ZIGGURAT,
            ),
            OnboardingSlide(
                icon = "🔨",
                titleRes = R.string.onboarding_slide2_title,
                bodyRes = R.string.onboarding_slide2_body,
                biome = Biome.BURNING_SANDS,
            ),
            OnboardingSlide(
                icon = "⚔️",
                titleRes = R.string.onboarding_slide3_title,
                bodyRes = R.string.onboarding_slide3_body,
                biome = Biome.FROZEN_ZIGGURATS,
            ),
            OnboardingSlide(
                icon = "👣",
                titleRes = R.string.onboarding_slide4_title,
                bodyRes = R.string.onboarding_slide4_body,
                isPermissionPrimer = true,
                biome = Biome.CELESTIAL_GATE,
            ),
        )
}
