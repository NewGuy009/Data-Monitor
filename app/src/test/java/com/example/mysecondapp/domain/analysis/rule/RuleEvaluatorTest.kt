package com.example.mysecondapp.domain.analysis.rule

import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import com.example.mysecondapp.domain.analysis.indicator.HistoricalAnalysisInput
import com.example.mysecondapp.domain.analysis.signal.TechnicalReasonCode
import com.example.mysecondapp.domain.analysis.signal.TechnicalResult
import com.example.mysecondapp.domain.analysis.signal.TechnicalResultStatus
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {

    private val evaluator = RuleEvaluator()

    @Test
    fun `template evaluation contains evidence and signal deduplicates`() {
        val rule = M3RuleTemplates.emaGoldenCross()
        val evaluation = evaluator.evaluate(rule, context(TechnicalResultStatus.MATCHED, TechnicalReasonCode.EMA_GOLDEN_CROSS, "ema_cross"))
        val key = evaluation.toSignalKey()
        val deduplicator = SignalDeduplicator()

        assertEquals(RuleEvaluationStatus.MATCHED, evaluation.status)
        assertEquals(2_000L, evaluation.cutoffMillis)
        assertEquals(StockIdentity("SH", "600000"), evaluation.identity)
        assertEquals(DataProviders.TENCENT, evaluation.providerId)
        assertEquals(2_000L, key.signalBarTimestampMillis)
        assertNotNull(evaluation.evidence.single())
        assertNotNull(deduplicator.accept(evaluation, 3_000L))
        assertNull(deduplicator.accept(evaluation, 4_000L))
    }

    @Test
    fun `all any and not preserve deterministic truth semantics`() {
        val matched = RuleCondition.TechnicalEvent("one", TechnicalReasonCode.EMA_GOLDEN_CROSS)
        val notMatched = RuleCondition.TechnicalEvent("two", TechnicalReasonCode.EMA_DEATH_CROSS)
        val base = context(
            TechnicalResultStatus.MATCHED,
            TechnicalReasonCode.EMA_GOLDEN_CROSS,
            "one",
        ).copy(
            technicalResults = listOf(
                result("one", TechnicalReasonCode.EMA_GOLDEN_CROSS),
                result("two", TechnicalReasonCode.EMA_GOLDEN_CROSS),
            ),
        )

        val all = evaluator.evaluate(rule(RuleCondition.All(listOf(matched, notMatched))), base)
        val any = evaluator.evaluate(rule(RuleCondition.Any(listOf(notMatched, matched))), base)
        val not = evaluator.evaluate(rule(RuleCondition.Not(matched)), base)

        assertEquals(RuleEvaluationStatus.NOT_MATCHED, all.status)
        assertEquals(RuleEvaluationStatus.MATCHED, any.status)
        assertEquals(RuleEvaluationStatus.NOT_MATCHED, not.status)
        assertTrue(all.evidence.size >= 2)
    }

    @Test
    fun `threshold condition can evaluate an indicator series`() {
        val indicator = com.example.mysecondapp.domain.analysis.indicator.RsiIndicator(1).calculate(contextInput())
        val rule = rule(
            RuleCondition.IndicatorThreshold(
                resultKind = "rsi",
                valueKey = "value",
                operator = ThresholdOperator.LESS_THAN,
                threshold = 50.0,
            ),
        )
        val context = contextInput().let { input -> RuleAnalysisContext(input, emptyList(), listOf(indicator)) }

        val evaluation = evaluator.evaluate(rule, context)

        assertEquals(RuleEvaluationStatus.MATCHED, evaluation.status)
        assertEquals("rsi", evaluation.evidence.single().resultKind)
    }

    @Test
    fun `non complete history blocks a matching rule`() {
        val input = contextInput(quality = HistoricalBarQuality.PARTIAL)
        val context = RuleAnalysisContext(
            input,
            listOf(result("ema_cross", TechnicalReasonCode.EMA_GOLDEN_CROSS)),
        )

        val evaluation = evaluator.evaluate(M3RuleTemplates.emaGoldenCross(), context)

        assertEquals(RuleEvaluationStatus.DATA_QUALITY_BLOCKED, evaluation.status)
        assertTrue(evaluation.evidence.isEmpty())
    }

    @Test
    fun `rule version direction and provider create distinct signal keys`() {
        val first = evaluator.evaluate(M3RuleTemplates.emaGoldenCross(), context(
            TechnicalResultStatus.MATCHED,
            TechnicalReasonCode.EMA_GOLDEN_CROSS,
            "ema_cross",
        )).toSignalKey()
        val secondRule = M3RuleTemplates.emaGoldenCross().copy(version = 2)
        val second = evaluator.evaluate(secondRule, context(
            TechnicalResultStatus.MATCHED,
            TechnicalReasonCode.EMA_GOLDEN_CROSS,
            "ema_cross",
        )).toSignalKey()

        assertTrue(first != second)
        assertTrue(first.ruleVersion != second.ruleVersion)
    }

    private fun rule(condition: RuleCondition) = AnalysisRule(
        id = "test_rule",
        version = 1,
        name = "Test rule",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BULLISH,
        condition = condition,
    )

    private fun context(
        status: TechnicalResultStatus,
        reason: TechnicalReasonCode,
        kind: String,
    ): RuleAnalysisContext = RuleAnalysisContext(
        input = contextInput(),
        technicalResults = listOf(result(kind, reason, status)),
    )

    private fun result(
        kind: String,
        reason: TechnicalReasonCode,
        status: TechnicalResultStatus = TechnicalResultStatus.MATCHED,
    ) = TechnicalResult(
        kind = kind,
        status = status,
        reasonCode = reason,
        sourceBarTimestamps = listOf(2_000L),
        values = mapOf("value" to 40.0),
    )

    private fun contextInput(quality: HistoricalBarQuality = HistoricalBarQuality.COMPLETE): HistoricalAnalysisInput {
        val bars = listOf(bar(1_000L, 10.0), bar(2_000L, 9.0))
        val series = HistoricalBarSeries(
            identity = StockIdentity("SH", "600000"),
            bars = bars,
            period = CandlePeriod.DAY,
            adjustment = CandleAdjustment.QFQ,
            currency = CurrencyCode.CNY,
            volumeUnit = QuantityUnit.SHARES,
            providerId = DataProviders.TENCENT,
            marketTimeZone = "Asia/Shanghai",
            fetchedAtMillis = 3_000L,
            analysisCutoffMillis = 2_000L,
            cutoffBarCompletion = HistoricalBarCompletion.CONFIRMED,
        )
        return HistoricalAnalysisInput(series, bars, quality)
    }

    private fun bar(timestamp: Long, close: Double) = Candle(
        timestampMillis = timestamp,
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = 100L,
        adjustment = CandleAdjustment.QFQ,
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
    )
}
