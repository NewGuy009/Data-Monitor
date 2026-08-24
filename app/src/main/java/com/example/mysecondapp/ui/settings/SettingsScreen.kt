package com.example.mysecondapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mysecondapp.domain.model.DataProviderId

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val refreshIntervalSeconds by viewModel.refreshIntervalSeconds.collectAsStateWithLifecycle()
    val dataSourcePreference by viewModel.dataSourcePreference.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Refresh Interval",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "$refreshIntervalSeconds seconds",
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::decreaseRefreshInterval) {
                    Text(text = "- 5s")
                }
                Button(onClick = viewModel::increaseRefreshInterval) {
                    Text(text = "+ 5s")
                }
            }
            Text(
                text = "Stored in DataStore and should survive app restarts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Quote Data Source",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.quoteProviders.forEach { providerId ->
                    ProviderChip(
                        providerId = providerId,
                        selected = providerId == dataSourcePreference.primaryProviderId,
                        onClick = { viewModel.selectPrimaryProvider(providerId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    providerId: DataProviderId,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(providerId.value.replaceFirstChar { character -> character.uppercase() }) },
    )
}
