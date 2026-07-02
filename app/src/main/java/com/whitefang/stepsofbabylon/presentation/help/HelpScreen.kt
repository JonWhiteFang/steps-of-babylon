package com.whitefang.stepsofbabylon.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R

@Composable
fun HelpScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HelpSection(
            stringResource(R.string.help_currencies_title),
            stringResource(R.string.help_currencies_body),
        )

        HelpSection(
            stringResource(R.string.help_workshop_title),
            stringResource(R.string.help_workshop_body),
        )

        HelpSection(
            stringResource(R.string.help_battle_title),
            stringResource(R.string.help_battle_body),
        )

        HelpSection(
            stringResource(R.string.help_tiers_title),
            stringResource(R.string.help_tiers_body),
        )

        HelpSection(
            stringResource(R.string.help_labs_title),
            stringResource(R.string.help_labs_body),
        )

        HelpSection(
            stringResource(R.string.help_cards_title),
            stringResource(R.string.help_cards_body),
        )

        HelpSection(
            stringResource(R.string.help_uw_title),
            stringResource(R.string.help_uw_body),
        )

        HelpSection(
            stringResource(R.string.help_encounters_title),
            stringResource(R.string.help_encounters_body),
        )

        HelpSection(
            stringResource(R.string.help_fairplay_title),
            stringResource(R.string.help_fairplay_body),
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HelpSection(
    title: String,
    body: String,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}
