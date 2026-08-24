package com.example.mysecondapp.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.data.settings.SettingsRepository
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.StockSearchItem
import com.example.mysecondapp.domain.model.WatchlistItem
import com.example.mysecondapp.domain.repository.MarketRepository
import com.example.mysecondapp.domain.repository.StockSearchRepository
import com.example.mysecondapp.domain.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val stockSearchRepository: StockSearchRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshMutex = Mutex()
    private var searchJob: Job? = null

    private val _statusText = MutableStateFlow("Preparing watchlist refresh...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _rawSearchResults = MutableStateFlow<List<StockSearchItem>>(emptyList())

    private val watchlistItems = watchlistRepository.observeWatchlistItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val refreshIntervalSeconds = settingsRepository.refreshIntervalSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 15,
    )

    val snapshots = marketRepository.observeSnapshots().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val searchResults = combine(
        _rawSearchResults,
        watchlistItems,
    ) { results, items ->
        val watchlistKeys = items.map { item -> item.uniqueKey() }.toSet()
        results.map { result ->
            StockSearchResultUiState(
                item = result,
                isAdded = result.uniqueKey() in watchlistKeys,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        startAutoRefreshLoop()
        seedWatchlistAndRefresh()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Remote suggestions are requested only after a short pause, and a stale response
            // is never allowed to replace the results for the user's newer query.
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (_searchQuery.value == query) {
                performStockSearch(query)
            }
        }
    }

    fun addSearchResult(item: StockSearchItem) {
        viewModelScope.launch {
            if (watchlistRepository.containsWatchlistItem(item.market, item.code)) {
                _statusText.value = "${item.name} is already in watchlist."
                return@launch
            }

            // New symbols append to the tail so existing ordering remains stable.
            val nextOrder = watchlistRepository.nextDisplayOrder()
            watchlistRepository.addWatchlistItem(
                WatchlistItem(
                    market = item.market,
                    code = item.code,
                    name = item.name,
                    groupName = null,
                    order = nextOrder,
                ),
            )

            _searchQuery.value = ""
            _rawSearchResults.value = emptyList()
            _statusText.value = "Added ${item.name} to watchlist."
            refreshWatchlistQuotes(trigger = RefreshTrigger.AFTER_ADD)
        }
    }

    fun refreshWatchlistQuotes() {
        viewModelScope.launch {
            refreshWatchlistQuotes(trigger = RefreshTrigger.MANUAL)
        }
    }

    private fun seedWatchlistAndRefresh() {
        viewModelScope.launch {
            watchlistRepository.seedSampleWatchlistIfEmpty()
            refreshWatchlistQuotes(trigger = RefreshTrigger.INITIAL)
        }
    }

    private fun startAutoRefreshLoop() {
        viewModelScope.launch {
            // Restart the loop whenever the user changes refresh interval.
            settingsRepository.refreshIntervalSeconds.collectLatest { intervalSeconds ->
                while (isActive) {
                    delay(intervalSeconds * 1_000L)
                    refreshWatchlistQuotes(trigger = RefreshTrigger.AUTO)
                }
            }
        }
    }

    private suspend fun performStockSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _rawSearchResults.value = emptyList()
            return
        }

        _rawSearchResults.value = stockSearchRepository.search(normalizedQuery)
    }

    private suspend fun refreshWatchlistQuotes(trigger: RefreshTrigger) {
        // Use a mutex so manual refresh, add-flow refresh and auto refresh do not overlap.
        refreshMutex.withLock {
            _isRefreshing.value = true
            _statusText.value = when (trigger) {
                RefreshTrigger.INITIAL -> "Loading watchlist quotes..."
                RefreshTrigger.MANUAL -> "Refreshing watchlist quotes..."
                RefreshTrigger.AUTO -> "Auto refreshing watchlist quotes..."
                RefreshTrigger.AFTER_ADD -> "Syncing newly added symbol..."
            }

            try {
                _statusText.value = when (val result = marketRepository.refreshWatchlistQuotes()) {
                    is MarketDataResult.Success -> {
                        val actionText = when (trigger) {
                            RefreshTrigger.AUTO -> "Auto updated"
                            RefreshTrigger.AFTER_ADD -> "Synced"
                            else -> "Loaded"
                        }
                        "$actionText ${result.value.size} quotes from ${result.source.name}"
                    }

                    is MarketDataResult.Failure -> {
                        "Quote refresh failed: ${result.error.toUserMessage()}"
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

data class StockSearchResultUiState(
    val item: StockSearchItem,
    val isAdded: Boolean,
)

private enum class RefreshTrigger {
    INITIAL,
    MANUAL,
    AUTO,
    AFTER_ADD,
}

private const val SEARCH_DEBOUNCE_MILLIS = 250L

private fun MarketError.toUserMessage(): String = when (this) {
    is MarketError.Network -> message ?: "Network unavailable"
    is MarketError.EmptyResponse -> "No quote returned from ${source.name}"
    is MarketError.ParseFailure -> "Quote parsing failed from ${source.name}"
    is MarketError.UnsupportedSymbol -> reason
    is MarketError.Unknown -> message ?: "Unknown market error"
}

private fun WatchlistItem.uniqueKey(): String = "$market-$code"

private fun StockSearchItem.uniqueKey(): String = "$market-$code"
