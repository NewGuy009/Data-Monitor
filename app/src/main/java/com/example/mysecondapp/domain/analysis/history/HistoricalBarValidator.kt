package com.example.mysecondapp.domain.analysis.history

import com.example.mysecondapp.domain.model.Candle
import javax.inject.Inject

/**
 * 历史分析的第一道质量边界。
 *
 * 该类保持纯 Kotlin：它只验证输入并返回结果，不访问网络、Room 或 Compose，也不修改输入列表。
 */
class HistoricalBarValidator @Inject constructor() {

    fun validate(
        series: HistoricalBarSeries,
        options: HistoricalBarValidationOptions = HistoricalBarValidationOptions(),
    ): HistoricalBarValidationResult {
        val analysisBars = series.barsAtOrBeforeCutoff()
        val issues = mutableListOf<HistoricalBarIssue>()

        if (series.bars.isEmpty() || analysisBars.isEmpty()) {
            issues += HistoricalBarIssue(HistoricalBarIssueCode.EMPTY_SERIES)
        } else {
            if (series.bars.none { it.timestampMillis == series.analysisCutoffMillis }) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.ANALYSIS_CUTOFF_NOT_FOUND)
            }
            if (analysisBars.size < options.minimumBarCount) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.INSUFFICIENT_HISTORY)
            }
            validateOrdering(analysisBars, issues)
            validateValues(analysisBars, issues)
            validateMetadata(series, analysisBars, issues)
            validateExpectedGaps(analysisBars, options.expectedBarTimestamps, issues)
            if (
                analysisBars.any { it.timestampMillis == series.analysisCutoffMillis } &&
                series.cutoffBarCompletion != HistoricalBarCompletion.CONFIRMED
            ) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.UNCONFIRMED_CUTOFF_BAR)
            }
        }

        val quality = when {
            issues.any { it.code.isInvalidStructure() } -> HistoricalBarQuality.INVALID
            analysisBars.size < options.minimumBarCount -> HistoricalBarQuality.INSUFFICIENT_HISTORY
            issues.any { it.code == HistoricalBarIssueCode.UNEXPECTED_GAP } -> HistoricalBarQuality.GAPPED
            issues.any { it.code == HistoricalBarIssueCode.UNCONFIRMED_CUTOFF_BAR } -> HistoricalBarQuality.PARTIAL
            else -> HistoricalBarQuality.COMPLETE
        }

        return HistoricalBarValidationResult(
            series = series,
            analysisBars = analysisBars,
            quality = quality,
            issues = issues,
        )
    }

    private fun validateOrdering(
        bars: List<Candle>,
        issues: MutableList<HistoricalBarIssue>,
    ) {
        bars.zipWithNext().forEach { (current, next) ->
            when {
                current.timestampMillis == next.timestampMillis -> issues += HistoricalBarIssue(
                    HistoricalBarIssueCode.DUPLICATE_TIMESTAMP,
                    next.timestampMillis,
                )

                current.timestampMillis > next.timestampMillis -> issues += HistoricalBarIssue(
                    HistoricalBarIssueCode.TIMESTAMP_NOT_ASCENDING,
                    next.timestampMillis,
                )
            }
        }
    }

    private fun validateValues(
        bars: List<Candle>,
        issues: MutableList<HistoricalBarIssue>,
    ) {
        bars.forEach { bar ->
            val prices = listOf(bar.open, bar.high, bar.low, bar.close)
            if (prices.any { !it.isFinite() }) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.NON_FINITE_PRICE, bar.timestampMillis)
            } else if (
                prices.any { it <= 0.0 } ||
                bar.high < maxOf(bar.open, bar.close) ||
                bar.low > minOf(bar.open, bar.close) ||
                bar.high < bar.low
            ) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.INVALID_OHLC, bar.timestampMillis)
            }
            if (bar.volume != null && bar.volume < 0L) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.NEGATIVE_VOLUME, bar.timestampMillis)
            }
            if (bar.turnover != null && !bar.turnover.isFinite()) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.NON_FINITE_TURNOVER, bar.timestampMillis)
            } else if (bar.turnover != null && bar.turnover < 0.0) {
                issues += HistoricalBarIssue(HistoricalBarIssueCode.NEGATIVE_TURNOVER, bar.timestampMillis)
            }
        }
    }

    private fun validateMetadata(
        series: HistoricalBarSeries,
        bars: List<Candle>,
        issues: MutableList<HistoricalBarIssue>,
    ) {
        if (bars.any { it.adjustment != series.adjustment }) {
            issues += HistoricalBarIssue(HistoricalBarIssueCode.MIXED_ADJUSTMENT)
        }
        if (bars.any { it.currency != series.currency }) {
            issues += HistoricalBarIssue(HistoricalBarIssueCode.MIXED_CURRENCY)
        }
        if (bars.any { it.volumeUnit != series.volumeUnit }) {
            issues += HistoricalBarIssue(HistoricalBarIssueCode.MIXED_VOLUME_UNIT)
        }
    }

    private fun validateExpectedGaps(
        bars: List<Candle>,
        expectedTimestamps: Set<Long>?,
        issues: MutableList<HistoricalBarIssue>,
    ) {
        if (expectedTimestamps == null || bars.isEmpty()) return
        val first = bars.first().timestampMillis
        val last = bars.last().timestampMillis
        val actual = bars.map { it.timestampMillis }.toSet()
        if (expectedTimestamps.any { it in first..last && it !in actual }) {
            issues += HistoricalBarIssue(HistoricalBarIssueCode.UNEXPECTED_GAP)
        }
    }
}

private fun HistoricalBarIssueCode.isInvalidStructure(): Boolean = when (this) {
    HistoricalBarIssueCode.ANALYSIS_CUTOFF_NOT_FOUND,
    HistoricalBarIssueCode.TIMESTAMP_NOT_ASCENDING,
    HistoricalBarIssueCode.DUPLICATE_TIMESTAMP,
    HistoricalBarIssueCode.INVALID_OHLC,
    HistoricalBarIssueCode.NON_FINITE_PRICE,
    HistoricalBarIssueCode.NEGATIVE_VOLUME,
    HistoricalBarIssueCode.NON_FINITE_TURNOVER,
    HistoricalBarIssueCode.NEGATIVE_TURNOVER,
    HistoricalBarIssueCode.MIXED_ADJUSTMENT,
    HistoricalBarIssueCode.MIXED_CURRENCY,
    HistoricalBarIssueCode.MIXED_VOLUME_UNIT,
    -> true

    else -> false
}
