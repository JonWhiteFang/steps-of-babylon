package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onQuitRound: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F14)),
        ) {
            val haptics = rememberHaptics()
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.pause_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFD4A843),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    haptics.tap()
                    onResume()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_resume)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onQuitRound, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_quit_round))
                }
            }
        }
    }
}
