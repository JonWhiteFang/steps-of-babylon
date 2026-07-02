package com.whitefang.stepsofbabylon.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R

/**
 * Displays the privacy policy for Health Connect permissions.
 * Shown when users tap the privacy policy link in the Health Connect permissions screen.
 */
class HealthConnectPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.hc_privacy_policy_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(R.string.hc_privacy_policy_body),
                )
            }
        }
    }
}
