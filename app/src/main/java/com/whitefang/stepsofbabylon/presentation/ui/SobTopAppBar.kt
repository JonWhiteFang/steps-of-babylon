package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * The single shared back-affordance app bar for the 8 secondary (push-navigated) screens
 * (Bundle B / #161). Rendered once in MainActivity's outer Scaffold `topBar`, gated by
 * [com.whitefang.stepsofbabylon.presentation.navigation.Screen.secondaryTitle].
 *
 * Inset handling: the bar deliberately uses the DEFAULT `TopAppBarDefaults.windowInsets`
 * (status-bar Top + Horizontal) — i.e. `windowInsets` is NOT overridden. In a Material3
 * `Scaffold`, the `topBar` slot owns its own top inset: the bar self-pads the status bar, and the
 * Scaffold then sets `innerPadding.top = topBarHeight` (height INCLUDING that inset), which the
 * NavHost consumes via `Modifier.padding(innerPadding)`. So there is one coherent inset path — the
 * bar pushes its arrow/title below the status bar and the content below the bar. Do NOT zero the
 * insets: that would draw the arrow/title under the status bar (clipped) and strip the status-bar
 * offset from content. Adopting themed-bar art later is a one-file change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
    )
}
