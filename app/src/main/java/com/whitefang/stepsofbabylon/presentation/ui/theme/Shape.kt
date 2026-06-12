package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Steps of Babylon shape scale.
 *
 * Previously no [Shapes] token set existed, so corner radii were hardcoded ad-hoc across the app
 * (8.dp slot buttons, 16.dp modals, CircleShape badges) with no shared hierarchy. Centralising the
 * three Material3 shape buckets gives cards/sheets/buttons a consistent, slightly chunky "carved
 * tablet" rounding. Components that opt into `MaterialTheme.shapes` pick these up automatically;
 * existing explicit `RoundedCornerShape(...)` call-sites are left untouched (no behaviour change),
 * and can migrate to these tokens incrementally.
 */
val SobShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)
