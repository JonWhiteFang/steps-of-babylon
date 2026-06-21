package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.presentation.ui.EmptyState
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.presentation.ui.labelRes

@Composable
fun WorkshopScreen(onNavigateToWeapons: () -> Unit = {}, onNavigateToCards: () -> Unit = {}, viewModel: WorkshopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.error != null) { ErrorState(state.error!!, onRetry = viewModel::retry); return }
    if (state.isLoading) { LoadingBox(); return }
    val categories = UpgradeCategory.entries
    val selectedIndex = categories.indexOf(state.selectedCategory)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Column(Modifier.fillMaxSize()) {
            // Weapons + Cards buttons
            OutlinedButton(onClick = onNavigateToWeapons, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.workshop_weapons))
            }
            OutlinedButton(onClick = onNavigateToCards, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.workshop_cards))
            }

            // Balance header
            Text(
                text = stringResource(R.string.workshop_balance, state.stepBalance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )

            // Category tabs
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectCategory(category) },
                        text = { Text(stringResource(category.labelRes())) },
                    )
                }
            }

            // Upgrade list. Defensive empty-state guards the pre-seed transient (observeAllUpgrades
            // emitting before ensureUpgradesExist lands); every seeded category otherwise has ≥4
            // Workshop-visible upgrades.
            if (state.upgrades.isEmpty()) {
                EmptyState(message = "No upgrades in this category yet.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.upgrades, key = { it.type.name }) { info ->
                        UpgradeCard(info = info, onClick = { viewModel.purchase(info.type) })
                    }
                }
            }
        }
    }
    }
}
