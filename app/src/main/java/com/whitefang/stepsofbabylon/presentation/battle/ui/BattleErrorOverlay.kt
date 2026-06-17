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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R

/**
 * #190 REL-2: non-dismissable overlay shown when the game loop crashed and stopped. The only
 * action is "Return to menu" (the loop is dead + engine state is suspect, so no retry). The
 * scrim itself doesn't catch touches — round chrome is suppressed by the caller via the
 * `battleError` gate, so there is nothing interactive behind it.
 */
@Composable
fun BattleErrorOverlay(onReturnToMenu: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F14)),
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.battle_error_title), style = MaterialTheme.typography.headlineLarge, color = Color(0xFFD4A843), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.battle_error_body), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onReturnToMenu, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battle_error_return)) }
            }
        }
    }
}
