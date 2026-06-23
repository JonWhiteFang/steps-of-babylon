package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.presentation.ui.theme.GemColor
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.PowerStoneColor
import com.whitefang.stepsofbabylon.presentation.ui.theme.StepColor
import java.text.NumberFormat
import java.util.Locale

/**
 * The four player currencies, as a *presentation* concept. Members mirror the (now-deleted) domain
 * `Currency` enum. This is the single source of truth for currency presentation: swap [icon] here
 * later to adopt themed-glyph art in one place. Lives in presentation (not domain) because it carries
 * Compose-bound [icon]/[tint], which the Android-free domain layer cannot hold.
 */
enum class CurrencyType { STEPS, CASH, GEMS, POWER_STONES }

/** Approximate Material vector per currency until themed glyph art ships (#160 follow-up). */
fun CurrencyType.icon(): ImageVector =
    when (this) {
        CurrencyType.STEPS -> Icons.Filled.DirectionsWalk
        CurrencyType.CASH -> Icons.Filled.Paid
        CurrencyType.GEMS -> Icons.Filled.Diamond
        CurrencyType.POWER_STONES -> Icons.Filled.OfflineBolt
    }

/** Palette-aligned tint per currency (tokens from Color.kt). CASH has no token → reuse [Gold]. */
@Composable
fun CurrencyType.tint(): Color =
    when (this) {
        CurrencyType.STEPS -> StepColor
        CurrencyType.CASH -> Gold
        CurrencyType.GEMS -> GemColor
        CurrencyType.POWER_STONES -> PowerStoneColor
    }

/** Plural-noun form for standalone a11y `contentDescription`. No quantity inflection. */
fun CurrencyType.label(): String =
    when (this) {
        CurrencyType.STEPS -> "Steps"
        CurrencyType.CASH -> "Cash"
        CurrencyType.GEMS -> "Gems"
        CurrencyType.POWER_STONES -> "Power Stones"
    }

/** Thousands-grouped amount (US grouping for determinism). Centralizes the review's separator fix. */
fun formatCurrency(amount: Long): String = NumberFormat.getNumberInstance(Locale.US).format(amount)

/**
 * Icon + thousands-formatted value, e.g. a balance readout. The icon's text label is adjacent, so
 * it uses `contentDescription = null` (the value carries meaning); [CurrencyType.label] is for the
 * rare standalone case.
 */
@Composable
fun CurrencyValue(
    type: CurrencyType,
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(type.icon(), contentDescription = null, tint = type.tint(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatCurrency(amount), style = style, color = type.tint())
    }
}

/** Compact inline form for button labels: icon + raw value (no color, inherits the button's). */
@Composable
fun CurrencyCost(
    type: CurrencyType,
    amount: Long,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(type.icon(), contentDescription = type.label(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatCurrency(amount))
    }
}
