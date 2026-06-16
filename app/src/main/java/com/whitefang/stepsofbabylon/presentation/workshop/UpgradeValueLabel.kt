package com.whitefang.stepsofbabylon.presentation.workshop

import java.util.Locale

/**
 * Formats a combat-power value-per-step as the Workshop value-bar label, e.g.
 * "+1.6% power / 1,000 steps" (#29, spec §3.2 / §5.3).
 *
 * Callers render a bar only when the upgrade's Δpower > 0, so [percentPerKSteps] is always positive
 * and the leading "+" is always valid. A small-but-positive value that would round to "+0.0%" (e.g.
 * Critical Factor at very low crit chance) is floored to "+0.1%" so a card that legitimately carries a
 * bar never shows a contradictory "+0.0%". `Locale.ROOT` keeps the decimal separator device-independent
 * (matching `DescribeUpgradeEffect.fmt`) — do NOT copy `WorkshopViewModel.statValueFor`'s default-locale
 * `.format()` (spec INV-5).
 */
fun formatPowerPerKStepsLabel(percentPerKSteps: Double): String {
    val shown = if (percentPerKSteps > 0.0 && percentPerKSteps < 0.05) 0.1 else percentPerKSteps
    return String.format(Locale.ROOT, "+%.1f%% power / 1,000 steps", shown)
}
