package com.example.mysecondapp.domain.analysis.signal

import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import com.example.mysecondapp.domain.analysis.indicator.HistoricalAnalysisInput
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

class TechnicalAnalysisTest {

    @Test
    fun `ema cross fires once and an unchanged relation is not a repeated event`() {
        val cross = EmaCrossDetector(fastPeriod = 2, slowPeriod = 3).detect(inputFromCloses(10.0, 10.0, 10.0, 9.0, 12.0))
        val unchanged = EmaCrossDetector(fastPeriod = 2, slowPeriod = 3).detect(
            inputFromCloses(10.0, 10.0, 10.0, 9.0, 12.0, 13.0),
        )

        assertEquals(TechnicalResultStatus.MATCHED, cross.status)
        assertEquals(TechnicalReasonCode.EMA_GOLDEN_CROSS, cross.reasonCode)
        assertEquals(listOf(4_000L, 5_000L), cross.sourceBarTimestamps)
        assertTrue(cross.values.containsKey("previousFastEma"))
        assertEquals(2.0, cross.parameters["fastPeriod"]!!, 0.0)
        assertEquals(TechnicalResultStatus.NOT_MATCHED, unchanged.status)
        assertEquals(TechnicalReasonCode.EMA_BULLISH_RELATION, unchanged.reasonCode)
    }

    @Test
    fun `engulfing detects positive near miss and insufficient history`() {
        val detector = EngulfingPatternDetector()
        val matched = detector.detect(inputFromBars(listOf(
            bar(1_000L, open = 10.0, close = 9.0),
            bar(2_000L, open = 8.8, close = 10.2),
        )))
        val nearMiss = detector.detect(inputFromBars(listOf(
            bar(1_000L, open = 10.0, close = 9.0),
            bar(2_000L, open = 9.1, close = 10.2),
        )))
        val insufficient = detector.detect(inputFromBars(listOf(bar(1_000L, 10.0, 10.2))))

        assertEquals(TechnicalReasonCode.BULLISH_ENGULFING, matched.reasonCode)
        assertEquals(TechnicalResultStatus.MATCHED, matched.status)
        assertEquals(TechnicalResultStatus.NOT_MATCHED, nearMiss.status)
        assertEquals(TechnicalReasonCode.PATTERN_NOT_MATCHED, nearMiss.reasonCode)
        assertEquals(TechnicalResultStatus.UNAVAILABLE, insufficient.status)
        assertEquals(TechnicalReasonCode.INSUFFICIENT_HISTORY, insufficient.reasonCode)
    }

    @Test
    fun `range breakout requires price and volume confirmation`() {
        val detector = RangeBreakoutDetector(lookback = 2, volumeMultiplier = 1.5)
        val matched = detector.detect(inputFromBars(listOf(
            bar(1_000L, 9.5, 10.0, high = 10.0, volume = 100L),
            bar(2_000L, 10.0, 10.5, high = 11.0, volume = 100L),
            bar(3_000L, 11.0, 12.0, high = 12.0, volume = 200L),
        )))
        val nearMiss = detector.detect(inputFromBars(listOf(
            bar(1_000L, 9.5, 10.0, high = 10.0, volume = 100L),
            bar(2_000L, 10.0, 10.5, high = 11.0, volume = 100L),
            bar(3_000L, 11.0, 12.0, high = 12.0, volume = 120L),
        )))
        val insufficient = detector.detect(inputFromBars(listOf(bar(1_000L, 9.0, 10.0))))

        assertEquals(TechnicalResultStatus.MATCHED, matched.status)
        assertEquals(TechnicalReasonCode.RANGE_BREAKOUT, matched.reasonCode)
        assertEquals(11.0, matched.values["rangeHigh"]!!, 0.0001)
        assertEquals(TechnicalResultStatus.NOT_MATCHED, nearMiss.status)
        assertEquals(TechnicalResultStatus.UNAVAILABLE, insufficient.status)
    }

    @Test
    fun `trend rsi and volume states retain source values and reasons`() {
        val rising = inputFromCloses(*(1..30).map(Int::toDouble).toDoubleArray())
        val trend = TrendStateDetector(fastPeriod = 2, slowPeriod = 3).detect(rising)
        val rsi = RsiStateDetector(period = 3).detect(inputFromCloses(10.0, 9.0, 8.0, 7.0))
        val volume = VolumeStateDetector(period = 2).detect(inputFromBars(listOf(
            bar(1_000L, 10.0, 10.0, volume = 100L),
            bar(2_000L, 10.0, 10.0, volume = 200L),
        )))

        assertEquals(TechnicalReasonCode.EMA_BULLISH_RELATION, trend.reasonCode)
        assertEquals(TechnicalReasonCode.RSI_OVERSOLD, rsi.reasonCode)
        assertEquals(TechnicalReasonCode.VOLUME_ABOVE_AVERAGE, volume.reasonCode)
        assertTrue(volume.sourceBarTimestamps.isNotEmpty())
        assertTrue(volume.values["averageVolume"]!! > 0.0)
    }

    @Test
    fun `candle shape is geometry only`() {
        val shape = bar(1_000L, open = 10.0, close = 12.0, high = 13.0, low = 9.0).toShape()

        assertTrue(shape.isBullish)
        assertFalse(shape.isBearish)
        assertEquals(2.0, shape.body, 0.0001)
        assertEquals(1.0, shape.upperShadow, 0.0001)
        assertEquals(1.0, shape.lowerShadow, 0.0001)
    }

    @Test
    fun `bollinger and obv states expose structured direction`() {
        val bollinger = BollingerPositionDetector(period = 3, deviations = 1.0).detect(
            inputFromCloses(10.0, 10.0, 10.0),
        )
        val obv = ObvStateDetector().detect(inputFromBars(listOf(
            bar(1_000L, 10.0, 10.0, volume = 100L),
            bar(2_000L, 10.0, 11.0, volume = 200L),
        )))

        assertEquals(TechnicalReasonCode.PRICE_WITHIN_BOLLINGER_BANDS, bollinger.reasonCode)
        assertEquals(TechnicalReasonCode.OBV_RISING, obv.reasonCode)
        assertEquals(300.0, obv.values["obv"]!!, 0.0001)
    }

    private fun inputFromCloses(vararg closes: Double): HistoricalAnalysisInput = inputFromBars(
        closes.mapIndexed { index, close -> bar((index + 1) * 1_000L, close, close) },
    )

    private fun inputFromBars(bars: List<Candle>): HistoricalAnalysisInput {
        val series = HistoricalBarSeries(
            identity = StockIdentity("SH", "600000"),
            bars = bars,
            period = CandlePeriod.DAY,
            adjustment = CandleAdjustment.QFQ,
            currency = CurrencyCode.CNY,
            volumeUnit = QuantityUnit.SHARES,
            providerId = DataProviders.TENCENT,
            marketTimeZone = "Asia/Shanghai",
            fetchedAtMillis = 100_000L,
            analysisCutoffMillis = bars.lastOrNull()?.timestampMillis ?: 0L,
            cutoffBarCompletion = HistoricalBarCompletion.CONFIRMED,
        )
        return HistoricalAnalysisInput(series, bars, HistoricalBarQuality.COMPLETE)
    }

    private fun bar(
        timestamp: Long,
        open: Double,
        close: Double,
        high: Double = maxOf(open, close) + 0.5,
        low: Double = minOf(open, close) - 0.5,
        volume: Long = 100L,
    ): Candle = Candle(
        timestampMillis = timestamp,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
    )
}
