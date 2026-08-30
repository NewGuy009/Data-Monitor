package com.example.mysecondapp.domain.analysis.history

import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalBarValidatorTest {

    private val validator = HistoricalBarValidator()

    @Test
    fun `valid prefix is complete and future bar is excluded from analysis`() {
        val series = series(bars = listOf(bar(1_000L), bar(2_000L), bar(3_000L)), cutoff = 2_000L)

        val result = validator.validate(series)

        assertEquals(HistoricalBarQuality.COMPLETE, result.quality)
        assertTrue(result.isEligibleForFinalEventRule)
        assertEquals(listOf(1_000L, 2_000L), result.analysisBars.map { it.timestampMillis })
    }

    @Test
    fun `empty and one bar series are insufficient`() {
        val empty = validator.validate(series(bars = emptyList(), cutoff = 1_000L))
        val one = validator.validate(series(bars = listOf(bar(1_000L)), cutoff = 1_000L))

        assertEquals(HistoricalBarQuality.INSUFFICIENT_HISTORY, empty.quality)
        assertTrue(empty.issues.any { it.code == HistoricalBarIssueCode.EMPTY_SERIES })
        assertEquals(HistoricalBarQuality.INSUFFICIENT_HISTORY, one.quality)
        assertTrue(one.issues.any { it.code == HistoricalBarIssueCode.INSUFFICIENT_HISTORY })
    }

    @Test
    fun `invalid values are reported as invalid`() {
        val invalid = bar(2_000L).copy(high = 9.0, low = -1.0, volume = -1L, turnover = -2.0)
        val nonFinite = bar(1_000L).copy(close = Double.NaN)

        val result = validator.validate(series(bars = listOf(nonFinite, invalid), cutoff = 2_000L))

        assertEquals(HistoricalBarQuality.INVALID, result.quality)
        assertTrue(result.issues.any { it.code == HistoricalBarIssueCode.NON_FINITE_PRICE })
        assertTrue(result.issues.any { it.code == HistoricalBarIssueCode.INVALID_OHLC })
        assertTrue(result.issues.any { it.code == HistoricalBarIssueCode.NEGATIVE_VOLUME })
        assertTrue(result.issues.any { it.code == HistoricalBarIssueCode.NEGATIVE_TURNOVER })
    }

    @Test
    fun `duplicate and descending timestamps are invalid`() {
        val duplicate = validator.validate(series(listOf(bar(1_000L), bar(1_000L)), 1_000L))
        val descending = validator.validate(series(listOf(bar(2_000L), bar(1_000L)), 2_000L))

        assertEquals(HistoricalBarQuality.INVALID, duplicate.quality)
        assertTrue(duplicate.issues.any { it.code == HistoricalBarIssueCode.DUPLICATE_TIMESTAMP })
        assertEquals(HistoricalBarQuality.INVALID, descending.quality)
        assertTrue(descending.issues.any { it.code == HistoricalBarIssueCode.TIMESTAMP_NOT_ASCENDING })
    }

    @Test
    fun `mixed metadata and unknown cutoff are invalid`() {
        val mixed = series(
            bars = listOf(bar(1_000L), bar(2_000L).copy(currency = CurrencyCode.USD)),
            cutoff = 2_000L,
        )
        val unknownCutoff = series(listOf(bar(1_000L), bar(2_000L)), 1_500L)

        val mixedResult = validator.validate(mixed)
        val cutoffResult = validator.validate(unknownCutoff)

        assertEquals(HistoricalBarQuality.INVALID, mixedResult.quality)
        assertTrue(mixedResult.issues.any { it.code == HistoricalBarIssueCode.MIXED_CURRENCY })
        assertEquals(HistoricalBarQuality.INVALID, cutoffResult.quality)
        assertTrue(cutoffResult.issues.any { it.code == HistoricalBarIssueCode.ANALYSIS_CUTOFF_NOT_FOUND })
    }

    @Test
    fun `calendar gap is conservative unless expected timestamps are supplied`() {
        val series = series(listOf(bar(1_000L), bar(3_000L)), 3_000L)

        val withoutCalendar = validator.validate(series)
        val withCalendar = validator.validate(
            series,
            HistoricalBarValidationOptions(expectedBarTimestamps = setOf(1_000L, 2_000L, 3_000L)),
        )

        assertEquals(HistoricalBarQuality.COMPLETE, withoutCalendar.quality)
        assertEquals(HistoricalBarQuality.GAPPED, withCalendar.quality)
        assertFalse(withoutCalendar.issues.any { it.code == HistoricalBarIssueCode.UNEXPECTED_GAP })
    }

    @Test
    fun `unconfirmed cutoff is partial and cannot trigger event`() {
        val result = validator.validate(
            series(
                bars = listOf(bar(1_000L), bar(2_000L)),
                cutoff = 2_000L,
                completion = HistoricalBarCompletion.UNKNOWN,
            ),
        )

        assertEquals(HistoricalBarQuality.PARTIAL, result.quality)
        assertFalse(result.isEligibleForFinalEventRule)
        assertTrue(result.issues.any { it.code == HistoricalBarIssueCode.UNCONFIRMED_CUTOFF_BAR })
    }

    private fun series(
        bars: List<Candle>,
        cutoff: Long,
        completion: HistoricalBarCompletion = HistoricalBarCompletion.CONFIRMED,
    ): HistoricalBarSeries = HistoricalBarSeries(
        identity = StockIdentity("SH", "600000"),
        bars = bars,
        period = CandlePeriod.DAY,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
        providerId = DataProviders.TENCENT,
        marketTimeZone = "Asia/Shanghai",
        fetchedAtMillis = 4_000L,
        analysisCutoffMillis = cutoff,
        cutoffBarCompletion = completion,
    )

    private fun bar(timestamp: Long): Candle = Candle(
        timestampMillis = timestamp,
        open = 10.0,
        high = 11.0,
        low = 9.0,
        close = 10.5,
        volume = 100L,
        turnover = 1_000.0,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
    )
}
