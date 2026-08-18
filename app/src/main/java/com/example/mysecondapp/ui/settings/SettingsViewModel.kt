package com.example.mysecondapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SETTINGS_STEP_SECONDS = 5

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val refreshIntervalSeconds = settingsRepository.refreshIntervalSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 15,
    )

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
