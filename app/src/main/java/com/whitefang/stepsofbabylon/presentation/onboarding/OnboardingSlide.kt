package com.whitefang.stepsofbabylon.presentation.onboarding

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
    val title: String,
    val body: String,
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
                title = "Walk to power your ziggurat",
                body =
                    "Every real step you take earns Steps — the permanent currency that " +
                        "fuels everything. Steps are earned only by walking.",
                biome = Biome.HANGING_GARDENS,
                art = OnboardingArt.ZIGGURAT,
            ),
            OnboardingSlide(
                icon = "🔨",
                title = "Spend Steps in the Workshop",
                body =
                    "Permanent upgrades make your tower stronger across three categories: " +
                        "Attack, Defense, and Utility.",
                biome = Biome.BURNING_SANDS,
            ),
            OnboardingSlide(
                icon = "⚔️",
                title = "Send it into battle",
                body =
                    "Your ziggurat auto-battles waves of enemies. Survive, climb tiers, and " +
                        "unlock new biomes.",
                biome = Biome.FROZEN_ZIGGURATS,
            ),
            OnboardingSlide(
                icon = "👣",
                title = "Enable step counting",
                body =
                    "To turn your real-world steps into Steps, we need activity-recognition " +
                        "permission. Notifications are optional. Then go for a walk to earn your first Steps!",
                isPermissionPrimer = true,
                biome = Biome.CELESTIAL_GATE,
            ),
        )
}
