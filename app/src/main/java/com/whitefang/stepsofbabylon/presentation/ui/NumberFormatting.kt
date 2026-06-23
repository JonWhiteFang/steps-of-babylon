package com.whitefang.stepsofbabylon.presentation.ui

import java.text.NumberFormat
import java.util.Locale

/**
 * #262 L87: the single number-grouping helper for player-facing integer counts. Pinned to
 * [Locale.US] so grouping separators are deterministic (`1,234,567`) regardless of the device's
 * default locale. Previously three mechanisms coexisted (`NumberFormat.getNumberInstance()` with the
 * JVM default locale, `"%,d".format(...)`, and a `Locale.US`-pinned `formatCurrency`); this is the
 * canonical one they all route through.
 *
 * Deliberately Compose-free (no `androidx.compose.*` import) so the home-screen widget
 * (`service/StepWidgetProvider`, which renders `RemoteViews`, not Compose) can share it.
 */
fun formatCount(value: Long): String = NumberFormat.getNumberInstance(Locale.US).format(value)
