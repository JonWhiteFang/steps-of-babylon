package com.whitefang.stepsofbabylon.presentation.onboarding

/**
 * One tutorial slide. Pure Kotlin (emoji icon, not an ImageVector) so the content list
 * is JVM-testable without Android. [isPermissionPrimer] marks the final slide that owns
 * the activity-recognition permission ask.
 */
data class OnboardingSlide(
    val icon: String,
    val title: String,
    val body: String,
    val isPermissionPrimer: Boolean = false,
)

/** Canonical first-launch tutorial content. Final slide is the permission primer. */
object OnboardingContent {
    val slides: List<OnboardingSlide> = listOf(
        OnboardingSlide(
            icon = "🏛️",
            title = "Walk to power your ziggurat",
            body = "Every real step you take earns Steps — the permanent currency that " +
                "fuels everything. Steps are earned only by walking.",
        ),
        OnboardingSlide(
            icon = "🔨",
            title = "Spend Steps in the Workshop",
            body = "Permanent upgrades make your tower stronger across three categories: " +
                "Attack, Defense, and Utility.",
        ),
        OnboardingSlide(
            icon = "⚔️",
            title = "Send it into battle",
            body = "Your ziggurat auto-battles waves of enemies. Survive, climb tiers, and " +
                "unlock new biomes.",
        ),
        OnboardingSlide(
            icon = "👣",
            title = "Enable step counting",
            body = "To turn your real-world steps into Steps, we need activity-recognition " +
                "permission. Notifications are optional. Then go for a walk to earn your first Steps!",
            isPermissionPrimer = true,
        ),
    )
}
