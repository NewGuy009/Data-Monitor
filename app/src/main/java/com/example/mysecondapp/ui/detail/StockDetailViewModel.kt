package com.example.mysecondapp.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.repository.StockDetailRepository
import com.example.mysecondapp.ui.navigation.DetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 详情页状态只持有领域模型，网络、缓存和 Room 回退均由 Repository 处理。 */
data class StockDetailUiState(
    val identity: StockIdentity,
    val selectedPeriod: CandlePeriod = CandlePeriod.DAY,
    val snapshot: StockDetailSnapshot? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockDetailRepository: StockDetailRepository,
) : ViewModel() {

    private val identity = StockIdentity(
        market = requireNotNull(savedStateHandle[DetailDestination.MARKET_ARGUMENT]),
        code = requireNotNull(savedStateHandle[DetailDestination.CODE_ARGUMENT]),
    )
    private val refreshMutex = Mutex()
    private val selectedPeriod = MutableStateFlow(CandlePeriod.DAY)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StockDetailUiState> = combine(
        selectedPeriod,
        stockDetailRepository.observeDetail(identity),
        isRefreshing,
        errorMessage,
    ) { period, snapshot, refreshing, error ->
        StockDetailUiState(
            identity = identity,
            selectedPeriod = period,
            snapshot = snapshot,
            isRefreshing = refreshing,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockDetailUiState(identity = identity),
    )

    init {
        refresh()
    }

    fun selectPeriod(period: CandlePeriod) {
        if (selectedPeriod.value == period) return
        selectedPeriod.value = period
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // 周期切换和下拉刷新共用锁，避免旧请求最后返回时覆盖最新周期状态。
            refreshMutex.withLock {
                isRefreshing.value = true
                errorMessage.value = null
                when (
                    val result = stockDetailRepository.refreshDetail(
                        identity = identity,
                        candlePeriod = selectedPeriod.value,
                    )
                ) {
                    is MarketDataResult.Success -> Unit
                    is MarketDataResult.Failure -> errorMessage.value = result.error.toUserMessage()
                }
                isRefreshing.value = false
            }
        }
    }
}

private fun MarketError.toUserMessage(): String = when (this) {
    is MarketError.Network -> message ?: "Network unavailable"
    is MarketError.EmptyResponse -> "No detail data returned from ${source.name}"
    is MarketError.ParseFailure -> "Detail data parsing failed from ${source.name}"
    is MarketError.UnsupportedSymbol -> reason
    is MarketError.Unknown -> message ?: "Unable to load stock detail"
}
