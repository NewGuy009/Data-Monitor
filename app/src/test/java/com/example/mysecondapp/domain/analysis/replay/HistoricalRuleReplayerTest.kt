package com.example.mysecondapp.domain.analysis.replay

import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidator
import com.example.mysecondapp.domain.analysis.rule.M3RuleTemplates
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluator
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalRuleReplayerTest {

    private val replayer = HistoricalRuleReplayer(HistoricalBarValidator(), RuleEvaluator())

    @Test
    fun `replay marks warmup points and matches only confirmed volume breakout`() {
        val bars = (1..20).map { index -> bar(index * 1_000L, close = 10.0, high = 10.0, volume = 100L) } +
            bar(21_000L, close = 12.0, high = 12.0, volume = 200L)

        val result = replayer.replay(M3RuleTemplates.volumeBreakout(), series(bars))

        assertEquals(21, result.points.size)
        assertEquals(ReplayPointStatus.INSUFFICIENT_HISTORY, result.points.first().status)
        assertEquals(ReplayPointStatus.EVALUATED, result.points.last().status)
        assertEquals(1, result.matchedPoints.size)
        assertEquals(21_000L, result.matchedPoints.single().barTimestampMillis)
        assertEquals(HistoricalBarQuality.COMPLETE, HistoricalBarValidator().validate(series(bars)).quality)
    }

    @Test
    fun `replaying same input is deterministic and future bars do not alter earlier points`() {
        val bars = (1..21).map { index ->
            if (index == 21) bar(index * 1_000L, 12.0, 12.0, 200L)
            else bar(index * 1_000L, 10.0, 10.0, 100L)
        }
        val original = replayer.replay(M3RuleTemplates.volumeBreakout(), series(bars))
        val repeated = replayer.replay(M3RuleTemplates.volumeBreakout(), series(bars))
        val withFuture = replayer.replay(
            M3RuleTemplates.volumeBreakout(),
            series(bars + bar(22_000L, 9.0, 9.0, 50L)),
        )

        assertEquals(original, repeated)
        assertEquals(
            original.points.map { point -> point.evaluation?.status },
            withFuture.points.take(original.points.size).map { point -> point.evaluation?.status },
        )
        assertEquals(
            original.points.map { point -> point.evaluation?.evidence },
            withFuture.points.take(original.points.size).map { point -> point.evaluation?.evidence },
        )
        assertTrue(original.matchedPoints.isNotEmpty())
    }

    @Test
    fun `unconfirmed final bar is retained as partial instead of evaluated`() {
        val bars = listOf(
            bar(1_000L, 10.0, 10.0, 100L),
            bar(2_000L, 11.0, 11.0, 100L),
        )
        val result = replayer.replay(
            M3RuleTemplates.bullishEngulfing(),
            series(bars, completion = HistoricalBarCompletion.UNCONFIRMED),
        )

        assertEquals(ReplayPointStatus.PARTIAL, result.points.last().status)
        assertTrue(result.matchedPoints.isEmpty())
    }

    private fun series(
        bars: List<Candle>,
        completion: HistoricalBarCompletion = HistoricalBarCompletion.CONFIRMED,
    ) = HistoricalBarSeries(
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
        cutoffBarCompletion = completion,
    )

    private fun bar(timestamp: Long, close: Double, high: Double, volume: Long) = Candle(
        timestampMillis = timestamp,
        open = close,
        high = high,
        low = close,
        close = close,
        volume = volume,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
    )
}
