package com.example.mysecondapp.domain.analysis.history

import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity

/**
 * 一段可供历史分析使用的 K 线序列及其来源元数据。
 *
 * 元数据属于整段序列而不是单根 Candle；分析只能使用截止 Bar 及其之前的数据。
 */
data class HistoricalBarSeries(
    val identity: StockIdentity,
    val bars: List<Candle>,
    val period: CandlePeriod,
    val adjustment: CandleAdjustment,
    val currency: CurrencyCode,
    val volumeUnit: QuantityUnit,
    val providerId: DataProviderId,
    val marketTimeZone: String,
    val fetchedAtMillis: Long,
    val analysisCutoffMillis: Long,
    val cutoffBarCompletion: HistoricalBarCompletion,
) {
    /** 显式截取分析前缀，防止调用方误把未来 Bar 传入指标计算。 */
    fun barsAtOrBeforeCutoff(): List<Candle> = bars.filter { it.timestampMillis <= analysisCutoffMillis }
}

/** 截止 Bar 是否已经完成，未知状态不能触发历史事件规则。 */
enum class HistoricalBarCompletion {
    CONFIRMED,
    UNCONFIRMED,
    UNKNOWN,
}

enum class HistoricalBarQuality {
    COMPLETE,
    PARTIAL,
    GAPPED,
    INVALID,
    INSUFFICIENT_HISTORY,
}

/** 机器可读的数据问题码，UI 可以按码翻译，不依赖解析器文本。 */
enum class HistoricalBarIssueCode {
    EMPTY_SERIES,
    INSUFFICIENT_HISTORY,
    ANALYSIS_CUTOFF_NOT_FOUND,
    TIMESTAMP_NOT_ASCENDING,
    DUPLICATE_TIMESTAMP,
    INVALID_OHLC,
    NON_FINITE_PRICE,
    NEGATIVE_VOLUME,
    NON_FINITE_TURNOVER,
    NEGATIVE_TURNOVER,
    MIXED_ADJUSTMENT,
    MIXED_CURRENCY,
    MIXED_VOLUME_UNIT,
    UNEXPECTED_GAP,
    UNCONFIRMED_CUTOFF_BAR,
}

data class HistoricalBarIssue(
    val code: HistoricalBarIssueCode,
    val barTimestampMillis: Long? = null,
)

data class HistoricalBarValidationOptions(
    val minimumBarCount: Int = 2,
    /** 只有调用方明确提供交易日历时才检查缺失交易 Bar。 */
    val expectedBarTimestamps: Set<Long>? = null,
) {
    init {
        require(minimumBarCount > 0) { "minimumBarCount must be positive" }
    }
}

data class HistoricalBarValidationResult(
    val series: HistoricalBarSeries,
    val analysisBars: List<Candle>,
    val quality: HistoricalBarQuality,
    val issues: List<HistoricalBarIssue>,
) {
    /** 只有完全可靠的序列允许产生最终事件信号。 */
    val isEligibleForFinalEventRule: Boolean
        get() = quality == HistoricalBarQuality.COMPLETE
}
