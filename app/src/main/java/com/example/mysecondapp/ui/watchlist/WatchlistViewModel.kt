package com.example.mysecondapp.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.data.network.TencentStockApi
import com.example.mysecondapp.data.watchlist.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val stockApi: TencentStockApi,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    private val _statusText = MutableStateFlow("Loading remote quote...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    val watchlistItems = watchlistRepository.observeWatchlist().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        seedWatchlist()
        fetchTestQuote()
    }

    private fun seedWatchlist() {
        viewModelScope.launch {
            watchlistRepository.seedSampleWatchlistIfEmpty()
        }
    }

    private fun fetchTestQuote() {
        viewModelScope.launch {
            _statusText.value = runCatching {
                val raw = stockApi.getQuotes("sh600000")
                if (raw.length > 120) raw.take(120) + "..." else raw
            }.getOrElse { error ->
                "Quote request failed: ${error.message}"
            }
        }
    }
}
