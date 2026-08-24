package com.example.mysecondapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.data.settings.SettingsRepository
import com.example.mysecondapp.data.provider.MarketDataProviderRegistry
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataSourcePreference
import com.example.mysecondapp.domain.model.ProviderCapability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SETTINGS_STEP_SECONDS = 5

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    providerRegistry: MarketDataProviderRegistry,
) : ViewModel() {

    val quoteProviders = providerRegistry.all
        .filter { provider ->
            provider.marketDataSource != null &&
                provider.capabilities.supportedMarkets(ProviderCapability.QUOTE).isNotEmpty()
        }
        .map { provider -> provider.id }

    val refreshIntervalSeconds = settingsRepository.refreshIntervalSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 15,
    )

    val dataSourcePreference = settingsRepository.dataSourcePreference.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DataSourcePreference(),
    )

    fun selectPrimaryProvider(providerId: DataProviderId) {
        viewModelScope.launch {
            val current = dataSourcePreference.value
            settingsRepository.updateDataSourcePreference(
                current.copy(
                    primaryProviderId = providerId,
                    fallbackProviderIds = current.orderedProviderIds()
                        .filter { candidate -> candidate != providerId },
                ),
            )
        }
    }

    fun increaseRefreshInterval() {
        updateRefreshInterval(refreshIntervalSeconds.value + SETTINGS_STEP_SECONDS)
    }

    fun decreaseRefreshInterval() {
        updateRefreshInterval(refreshIntervalSeconds.value - SETTINGS_STEP_SECONDS)
    }

    private fun updateRefreshInterval(newValue: Int) {
        viewModelScope.launch {
            settingsRepository.updateRefreshIntervalSeconds(newValue)
        }
    }
}
