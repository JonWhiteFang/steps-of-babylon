package com.whitefang.stepsofbabylon.presentation.onboarding

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
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * One-time first-launch tutorial. Teaches walk -> spend -> battle, then asks for
 * step-counting permission with context on the final slide. Driven by callbacks from
 * MainActivity; the ViewModel supplies the slide list and persists completion.
 *
 * @param stepCountingGranted whether ACTIVITY_RECOGNITION is currently held.
 * @param permissionAsked whether the permission dialog has been shown this session.
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
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = viewModel.slides
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val lastIndex = slides.lastIndex

    fun goTo(page: Int) {
        scope.launch {
            if (reducedMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
        }
    }

    fun finish() {
        viewModel.completeOnboarding()
        onFinished()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {

            // Top bar: Skip (non-final slides only) jumps to the permission primer.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < lastIndex) {
                    TextButton(onClick = { goTo(lastIndex) }) { Text("Skip") }
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
                    Text(
                        slide.icon,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(slide.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        slide.body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Page dots.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { i ->
                    val active = i == pagerState.currentPage
                    // Decorative dot — a single Box with a background, no inner Surface, no
                    // semantics (it carries no text, so nothing to hide from TalkBack).
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                    )
                }
            }

            // Bottom controls.
            if (pagerState.currentPage < lastIndex) {
                Button(
                    onClick = { goTo(pagerState.currentPage + 1) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next") }
            } else {
                // Final (permission primer) slide.
                // ORDER MATTERS: stepCountingGranted is checked FIRST so a replay where the
                // permission is already held shows the satisfied state and does NOT re-ask
                // (spec §5). Only if not granted do we branch on whether we've asked yet.
                when {
                    stepCountingGranted -> {
                        Text(
                            "Step counting enabled ✓",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start playing")
                        }
                    }
                    !permissionAsked -> {
                        Button(onClick = onEnableStepCounting, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable step counting")
                        }
                    }
                    else -> {
                        // Asked but denied — give an explicit recovery path, never strand the player.
                        Text(
                            "Step counting is off. You can enable it any time in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Settings")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue without step counting")
                        }
                    }
                }
            }
        }
    }
}
