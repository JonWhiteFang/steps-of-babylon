package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.presentation.battle.biome.BiomeTheme
import com.whitefang.stepsofbabylon.presentation.ui.crossfadeNeighborIndex
import com.whitefang.stepsofbabylon.presentation.ui.lerpArgb
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * One-time first-launch tutorial. Teaches walk -> spend -> battle, then asks for
 * step-counting permission with context on the final slide. Driven by callbacks from
 * MainActivity; the ViewModel supplies the slide list and persists completion.
 *
 * @param stepCountingGranted whether ACTIVITY_RECOGNITION is currently held.
 * @param permissionAsked whether the permission dialog has been shown this process instance (survives process-death restore via rememberSaveable; #234).
 * @param reducedMotion honor the system reduce-animations setting.
 * @param onEnableStepCounting fire the system permission request (owned by MainActivity).
 * @param onOpenAppSettings open the app's system settings page (denial recovery).
 * @param onFinished persist+navigate away (MainActivity decides Home vs. back-to-Settings).
 */
@Composable
fun OnboardingScreen(
    stepCountingGranted: Boolean,
    permissionAsked: Boolean,
    reducedMotion: Boolean,
    onEnableStepCounting: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = viewModel.slides
    val stepSensorAvailable = viewModel.stepSensorAvailable
    // #261: offer the battery-exemption prompt after the step-permission grant. `batteryPrimerHandled`
    // closes the primer once the user responds — it (not the construction-time `shouldOfferBatteryExemption`,
    // which is stale after the grant) gates re-display, so it MUST be set on BOTH buttons.
    val showBatteryPrimer = viewModel.shouldOfferBatteryExemption
    var batteryPrimerHandled by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val lastIndex = slides.lastIndex

    fun goTo(page: Int) {
        scope.launch {
            if (reducedMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
        }
    }

    // Completion beat (spec E10/E10-seq): persist the gating flag FIRST and unconditionally, fire a
    // one-shot pulse (reused PurchasePulse), then navigate via the LaunchedEffect below. Navigation is
    // never gated on the animation, and the flag is already persisted, so backgrounding mid-pulse
    // cannot re-onboard. The `if (finishing) return` latch makes completion exactly-once even if the
    // CTA is double-tapped during the ~450ms beat (the original synchronous finish() navigated within
    // the frame, so this restores that once-only guarantee for the now-longer interactive window).
    val finishPulse = rememberPulse()
    var finishing by remember { mutableStateOf(false) }

    fun finish() {
        if (finishing) return // latch: ignore re-taps during the beat
        viewModel.completeOnboarding() // (1) persist — first, unconditional
        finishPulse.trigger() // (2) fire the one-shot pulse (no-op visual under reduced-motion)
        finishing = true // (3) arm navigation
    }
    LaunchedEffect(finishing) {
        if (finishing) {
            if (!reducedMotion) kotlinx.coroutines.delay(FINISH_PULSE_MS)
            onFinished() // (4) guaranteed exactly once; immediate under reduced-motion
        }
    }

    val sky =
        if (reducedMotion) {
            slideSky(slides[pagerState.currentPage].biome)?.let { Color(it.first) to Color(it.second) }
        } else {
            crossfadedSky(slides, pagerState.currentPage, pagerState.currentPageOffsetFraction)
        }
    Box(
        Modifier.fillMaxSize().then(
            if (sky != null) {
                Modifier.background(Brush.verticalGradient(listOf(sky.first, sky.second)))
            } else {
                Modifier
            },
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                // Top bar: Skip (non-final slides only) jumps to the permission primer.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (pagerState.currentPage < lastIndex) {
                        TextButton(onClick = { goTo(lastIndex) }) { Text(stringResource(R.string.onboarding_skip)) }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val slide = slides[page]
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Emoji icon is decorative — the title/body carry the meaning for TalkBack.
                        // clearAndSetSemantics{} (NOT semantics{contentDescription=""}) actually
                        // removes the auto-generated text node from the a11y tree.
                        if (slide.art != null) {
                            Image(
                                painter = painterResource(artDrawable(slide.art)),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(96.dp)
                                        .pulseScale(finishPulse),
                            )
                        } else {
                            Text(
                                slide.icon,
                                style = MaterialTheme.typography.displayMedium,
                                modifier =
                                    Modifier
                                        .pulseScale(finishPulse)
                                        .clearAndSetSemantics {},
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(slide.titleRes),
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(slide.bodyRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Page dots. The dots convey page position via colour+size only — invisible to TalkBack
                // (HorizontalPager does not auto-announce "page X of N"), so the ROW carries a single
                // semantic label and the individual dots stay decorative. The label is resolved here in
                // @Composable scope (the semantics{} lambda is not composable) via pluralStringResource.
                val pageLabel =
                    pluralStringResource(
                        R.plurals.page_x_of_n,
                        slides.size,
                        pagerState.currentPage + 1,
                        slides.size,
                    )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .semantics { contentDescription = pageLabel },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(slides.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                ),
                        )
                    }
                }

                // Bottom controls.
                if (pagerState.currentPage < lastIndex) {
                    Button(
                        onClick = { goTo(pagerState.currentPage + 1) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    // Final (permission primer) slide.
                    // ORDER MATTERS: the no-sensor case is checked FIRST (#193) — on a device with no
                    // hardware step-counter, granting ACTIVITY_RECOGNITION is meaningless (no Steps
                    // ever accrue), so we tell the player and steer them to Health Connect instead of
                    // showing a "Step counting enabled" success they'd never benefit from. Then
                    // stepCountingGranted so a replay where the permission is already held shows the
                    // satisfied state and does NOT re-ask (spec §5); only if not granted do we branch
                    // on whether we've asked yet.
                    when {
                        !stepSensorAvailable -> {
                            // #193: hardware step-counter absent — never strand the player at a
                            // silent dead-end. Explain it and let them continue (HC can backfill).
                            Text(
                                stringResource(R.string.onboarding_no_sensor_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth().pulseScale(finishPulse),
                            ) {
                                Text(stringResource(R.string.onboarding_continue_anyway))
                            }
                        }

                        stepCountingGranted -> {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.onboarding_enabled_body),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (showBatteryPrimer && !batteryPrimerHandled) {
                                // #261: offer the battery-optimization exemption after the grant. Never
                                // blocks — both buttons set batteryPrimerHandled so the primer closes and
                                // "Start playing" shows next (the construction-time showBatteryPrimer is
                                // stale after the grant, so the handled flag is what gates re-display).
                                Text(
                                    stringResource(R.string.onboarding_battery_primer_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = {
                                        onRequestBatteryExemption()
                                        batteryPrimerHandled = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.onboarding_allow_background))
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { batteryPrimerHandled = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.onboarding_maybe_later))
                                }
                            } else {
                                Button(
                                    onClick = { finish() },
                                    modifier = Modifier.fillMaxWidth().pulseScale(finishPulse),
                                ) {
                                    Text(stringResource(R.string.onboarding_start_playing))
                                }
                            }
                        }

                        !permissionAsked -> {
                            Button(onClick = onEnableStepCounting, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.onboarding_enable_step_counting))
                            }
                        }

                        else -> {
                            // Asked but denied — give an explicit recovery path, never strand the player.
                            Text(
                                stringResource(R.string.onboarding_denied_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.onboarding_open_settings))
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth().pulseScale(finishPulse),
                            ) {
                                Text(stringResource(R.string.onboarding_continue_without))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val FINISH_PULSE_MS = 450L

/** A slide's biome sky colours as packed-ARGB Ints, or null when the slide has no biome. */
private fun slideSky(biome: Biome?): Pair<Int, Int>? =
    biome?.let { BiomeTheme.forBiome(it).let { t -> t.skyColorTop to t.skyColorBottom } }

/**
 * The cross-faded (top, bottom) gradient colours for the current pager position. Blends the settled
 * slide's biome sky toward the neighbour the pager is dragging toward, using the signed offset
 * (spec E8): neighbour = currentPage + sign(offset), clamped to [0, lastIndex]; t = abs(offset).
 * Falls back to the settled slide's colours at the ends (no neighbour) or when a slide has no biome.
 */
private fun crossfadedSky(
    slides: List<OnboardingSlide>,
    page: Int,
    offset: Float,
): Pair<Color, Color>? {
    val current = slideSky(slides[page].biome) ?: return null
    val neighbourIndex = crossfadeNeighborIndex(page, offset, slides.lastIndex)
    val neighbour = slideSky(slides[neighbourIndex].biome) ?: current
    val t = abs(offset).coerceIn(0f, 1f)
    val top = lerpArgb(current.first, neighbour.first, t)
    val bottom = lerpArgb(current.second, neighbour.second, t)
    return Color(top) to Color(bottom)
}

/** Maps an [OnboardingArt] marker to its drawable resource (kept screen-local so the model stays
 *  Android-free). */
@androidx.annotation.DrawableRes
private fun artDrawable(art: OnboardingArt): Int =
    when (art) {
        OnboardingArt.ZIGGURAT -> R.drawable.ic_ziggurat_emblem
    }
