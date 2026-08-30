package com.example.mysecondapp.domain.analysis.indicator

import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
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

class BaselineIndicatorsTest {

    @Test
    fun `registry exposes all baseline indicators and rejects duplicate ids`() {
        val registry = BaselineIndicators.registry()

        assertEquals(8, registry.definitions.size)
        assertTrue(registry.find(BaselineIndicatorIds.RSI) != null)
        assertEquals(8, registry.definitions.map { it.id }.distinct().size)

        val indicator = SmaIndicator(3)
        var rejected = false
        try {
            IndicatorRegistry(listOf(indicator, SmaIndicator(4)))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `sma and ema keep warmup explicit and use only historical values`() {
        val short = input(listOf(1.0, 2.0, 3.0, 4.0), cutoff = 4_000L)
        val extended = input(listOf(1.0, 2.0, 3.0, 4.0, 100.0), cutoff = 4_000L)
        val sma = SmaIndicator(3).calculate(short)
        val ema = EmaIndicator(3).calculate(short)
        val extendedEma = EmaIndicator(3).calculate(extended)

        assertEquals(IndicatorValueState.WARMUP, sma.values[0].state)
        assertEquals(2.0, sma.values[2].value("value")!!, 0.0001)
        assertEquals(2.0, ema.values[2].value("value")!!, 0.0001)
        assertEquals(3.0, ema.values[3].value("value")!!, 0.0001)
        assertEquals(3.0, extendedEma.values[3].value("value")!!, 0.0001)
    }

    @Test
    fun `rsi flat prices are neutral and bollinger has hand calculated bands`() {
        val input = input(List(5) { 10.0 }, cutoff = 5_000L)
        val rsi = RsiIndicator(3).calculate(input)
        val boll = BollingerBandsIndicator(3, 2.0).calculate(
            input(listOf(1.0, 2.0, 3.0), cutoff = 3_000L),
        )

        assertEquals(IndicatorValueState.VALUE, rsi.values[3].state)
        assertEquals(50.0, rsi.values[4].value("value")!!, 0.0001)
        assertEquals(2.0, boll.values[2].value("middle")!!, 0.0001)
        assertEquals(2.0 + 2.0 * 0.81649658, boll.values[2].value("upper")!!, 0.0001)
        assertEquals(2.0 - 2.0 * 0.81649658, boll.values[2].value("lower")!!, 0.0001)
    }

    @Test
    fun `macd exposes signal and histogram only after warmup`() {
        val result = MacdIndicator(fastPeriod = 2, slowPeriod = 3, signalPeriod = 2).calculate(
            input(listOf(1.0, 2.0, 3.0, 4.0, 5.0), cutoff = 5_000L),
        )

        assertEquals(IndicatorValueState.WARMUP, result.values[2].state)
        assertEquals(IndicatorValueState.VALUE, result.values[3].state)
        assertEquals(
            result.values[4].value("macd")!! - result.values[4].value("signal")!!,
            result.values[4].value("histogram")!!,
            0.0001,
        )
    }

    @Test
    fun `volume indicators mark missing input unavailable and obv tracks direction`() {
        val bars = listOf(
            bar(1_000L, 10.0, 100L),
            bar(2_000L, 11.0, 150L),
            bar(3_000L, 10.0, 120L),
            bar(4_000L, 12.0, null),
        )
        val analysisInput = inputFromBars(bars)
        val volume = VolumeSmaIndicator(2).calculate(analysisInput)
        val obv = ObvIndicator().calculate(analysisInput)

        assertEquals(IndicatorValueState.VALUE, volume.values[1].state)
        assertEquals(125.0, volume.values[1].value("value")!!, 0.0001)
        assertEquals(IndicatorValueState.UNAVAILABLE, volume.values[3].state)
        assertEquals(IndicatorUnavailableReason.MISSING_INPUT, volume.values[3].unavailableReason)
        assertEquals(100.0, obv.values[0].value("value")!!, 0.0001)
        assertEquals(250.0, obv.values[1].value("value")!!, 0.0001)
        assertEquals(130.0, obv.values[2].value("value")!!, 0.0001)
        assertFalse(obv.values[3].state == IndicatorValueState.VALUE)
    }

    @Test
    fun `atr calculates true range average and registry supports calculation`() {
        val analysisInput = inputFromBars(
            listOf(
                bar(1_000L, 10.0, 100L, high = 12.0, low = 9.0),
                bar(2_000L, 11.0, 100L, high = 14.0, low = 10.0),
                bar(3_000L, 12.0, 100L, high = 13.0, low = 11.0),
            ),
        )
        val atr = AtrIndicator(2).calculate(analysisInput)
        val registry = IndicatorRegistry(listOf(EmaIndicator(3)))

        assertEquals(IndicatorValueState.VALUE, atr.values[1].state)
        assertEquals((3.0 + 4.0) / 2.0, atr.values[1].value("value")!!, 0.0001)
        assertEquals(IndicatorValueState.VALUE, registry.calculate(BaselineIndicatorIds.EMA, analysisInput).values[2].state)
    }

    @Test
    fun `non finite input becomes unavailable instead of leaking nan`() {
        val result = EmaIndicator(2).calculate(
            input(listOf(1.0, Double.NaN, 3.0), cutoff = 3_000L),
        )

        assertEquals(IndicatorValueState.UNAVAILABLE, result.values[1].state)
        assertEquals(IndicatorUnavailableReason.INVALID_VALUE, result.values[1].unavailableReason)
        assertTrue(result.values.all { value -> value.values.values.all(Double::isFinite) })
    }

    @Test
    fun `every baseline indicator is invariant at a cutoff when future bars are appended`() {
        val allBars = (1..40).map { index ->
            bar(index * 1_000L, 10.0 + index * 0.2, (100 + index).toLong())
        }
        val cutoff = 36_000L
        val prefixInput = inputFromBars(allBars.take(36), cutoff)
        val fullInput = inputFromBars(allBars, 40_000L)
        val registry = BaselineIndicators.registry()

        registry.definitions.forEach { definition ->
            val prefixValue = registry.calculate(definition.id, prefixInput).valueAt(cutoff)
            val fullValue = registry.calculate(definition.id, fullInput).valueAt(cutoff)
            assertEquals(definition.id, prefixValue?.state, fullValue?.state)
            assertEquals(definition.id, prefixValue?.values, fullValue?.values)
        }
    }

    private fun input(closes: List<Double>, cutoff: Long): HistoricalAnalysisInput = inputFromBars(
        closes.mapIndexed { index, close -> bar((index + 1) * 1_000L, close, 100L) },
        cutoff,
    )

    private fun inputFromBars(
        bars: List<Candle>,
        cutoff: Long = bars.last().timestampMillis,
    ): HistoricalAnalysisInput {
        val series = HistoricalBarSeries(
            identity = StockIdentity("SH", "600000"),
            bars = bars,
            period = CandlePeriod.DAY,
            adjustment = CandleAdjustment.QFQ,
            currency = CurrencyCode.CNY,
            volumeUnit = QuantityUnit.SHARES,
            providerId = DataProviders.TENCENT,
            marketTimeZone = "Asia/Shanghai",
            fetchedAtMillis = 10_000L,
            analysisCutoffMillis = cutoff,
            cutoffBarCompletion = HistoricalBarCompletion.CONFIRMED,
        )
        return HistoricalAnalysisInput(series, series.barsAtOrBeforeCutoff(), HistoricalBarQuality.COMPLETE)
    }

    private fun bar(
        timestamp: Long,
        close: Double,
        volume: Long?,
        high: Double = close + 1.0,
        low: Double = close - 1.0,
    ): Candle = Candle(
        timestampMillis = timestamp,
        open = close,
        high = high,
        low = low,
        close = close,
        volume = volume,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
    )
}
