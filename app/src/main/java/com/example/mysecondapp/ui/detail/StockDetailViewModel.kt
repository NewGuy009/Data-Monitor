package com.example.mysecondapp.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.indicator.toAnalysisInput
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.analysis.rule.M3RuleTemplates
import com.example.mysecondapp.domain.analysis.signal.BollingerPositionDetector
import com.example.mysecondapp.domain.analysis.signal.ObvStateDetector
import com.example.mysecondapp.domain.analysis.signal.RsiStateDetector
import com.example.mysecondapp.domain.analysis.signal.TechnicalResult
import com.example.mysecondapp.domain.analysis.signal.TrendStateDetector
import com.example.mysecondapp.domain.analysis.signal.VolumeStateDetector
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.repository.StockDetailRepository
import com.example.mysecondapp.domain.repository.AnalysisRuleRepository
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
    val selectedPeriod: CandlePeriod = CandlePeriod.MINUTE,
    val snapshot: StockDetailSnapshot? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val analysis: StockAnalysisUiState? = null,
    val enabledRuleIds: Set<String> = emptySet(),
)

/** 详情页分析摘要只暴露已计算的事实，不在 Compose 中重新计算指标。 */
data class StockAnalysisUiState(
    val quality: HistoricalBarQuality,
    val issueCodes: List<String>,
    val cutoffMillis: Long,
    val providerId: String,
    val adjustment: String,
    val trend: TechnicalResult? = null,
    val rsi: TechnicalResult? = null,
    val volume: TechnicalResult? = null,
    val bollinger: TechnicalResult? = null,
    val obv: TechnicalResult? = null,
)

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockDetailRepository: StockDetailRepository,
    private val analysisRuleRepository: AnalysisRuleRepository,
) : ViewModel() {

    private val identity = StockIdentity(
        market = requireNotNull(savedStateHandle[DetailDestination.MARKET_ARGUMENT]),
        code = requireNotNull(savedStateHandle[DetailDestination.CODE_ARGUMENT]),
    )
    private val refreshMutex = Mutex()
    private val selectedPeriod = MutableStateFlow(CandlePeriod.MINUTE)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val analysis = MutableStateFlow<StockAnalysisUiState?>(null)
    private val enabledRuleIds = MutableStateFlow<Set<String>>(emptySet())

    private val baseUiState = combine(
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
    }
    val uiState: StateFlow<StockDetailUiState> = baseUiState
        .combine(analysis) { state, analysisState -> state.copy(analysis = analysisState) }
        .combine(enabledRuleIds) { state, ruleIds -> state.copy(enabledRuleIds = ruleIds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StockDetailUiState(identity = identity),
        )

    init {
        viewModelScope.launch {
            analysisRuleRepository.observeEnabled().collect { rules ->
                enabledRuleIds.value = rules.map(AnalysisRule::id).toSet()
            }
        }
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
                refreshAnalysis()
                isRefreshing.value = false
            }
        }
    }

    fun enableTemplate(rule: AnalysisRule) {
        viewModelScope.launch {
            analysisRuleRepository.save(rule)
        }
    }

    private suspend fun refreshAnalysis() {
        when (val result = stockDetailRepository.fetchHistoricalBarSeries(identity, CandlePeriod.DAY)) {
            is MarketDataResult.Failure -> analysis.value = null
            is MarketDataResult.Success -> {
                val validation = result.value
                val summary = if (validation.quality == HistoricalBarQuality.COMPLETE) {
                    val input = validation.toAnalysisInput()
                    StockAnalysisUiState(
                        quality = validation.quality,
                        issueCodes = validation.issues.map { it.code.name },
                        cutoffMillis = validation.series.analysisCutoffMillis,
                        providerId = validation.series.providerId.value,
                        adjustment = validation.series.adjustment.name,
                        trend = TrendStateDetector().detect(input),
                        rsi = RsiStateDetector().detect(input),
                        volume = VolumeStateDetector().detect(input),
                        bollinger = BollingerPositionDetector().detect(input),
                        obv = ObvStateDetector().detect(input),
                    )
                } else {
                    StockAnalysisUiState(
                        quality = validation.quality,
                        issueCodes = validation.issues.map { it.code.name },
                        cutoffMillis = validation.series.analysisCutoffMillis,
                        providerId = validation.series.providerId.value,
                        adjustment = validation.series.adjustment.name,
                    )
                }
                analysis.value = summary
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
