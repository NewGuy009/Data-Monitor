package com.example.mysecondapp.data.analysis

import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.analysis.rule.RuleCondition
import com.example.mysecondapp.domain.analysis.rule.RuleDirection
import com.example.mysecondapp.domain.analysis.rule.RuleEvidence
import com.example.mysecondapp.domain.analysis.rule.SignalKey
import com.example.mysecondapp.domain.analysis.rule.SignalRecord
import com.example.mysecondapp.domain.analysis.signal.TechnicalReasonCode
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisPersistenceMapperTest {

    @Test
    fun `nested rule condition survives entity round trip`() {
        val rule = AnalysisRule(
            id = "composite",
            version = 3,
            name = "Composite daily rule",
            period = CandlePeriod.DAY,
            direction = RuleDirection.BULLISH,
            condition = RuleCondition.All(
                listOf(
                    RuleCondition.TechnicalEvent("ema_cross", TechnicalReasonCode.EMA_GOLDEN_CROSS),
                    RuleCondition.Any(
                        listOf(
                            RuleCondition.IndicatorThreshold(
                                resultKind = "rsi_state",
                                valueKey = "rsi",
                                operator = com.example.mysecondapp.domain.analysis.rule.ThresholdOperator.LESS_THAN,
                                threshold = 70.0,
                            ),
                            RuleCondition.Not(
                                RuleCondition.TechnicalEvent("engulfing", TechnicalReasonCode.BEARISH_ENGULFING),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(rule, rule.toEntity().toDomain())
    }

    @Test
    fun `signal identity and evidence survive entity round trip`() {
        val signal = SignalRecord(
            key = SignalKey(
                ruleId = "ema_golden_cross",
                ruleVersion = 1,
                identity = StockIdentity("SH", "600000"),
                period = CandlePeriod.DAY,
                adjustment = CandleAdjustment.QFQ,
                providerId = DataProviders.TENCENT,
                signalBarTimestampMillis = 2_000L,
                direction = RuleDirection.BULLISH,
            ),
            cutoffMillis = 2_000L,
            evidence = listOf(
                RuleEvidence(
                    conditionType = "technical_event",
                    resultKind = "ema_cross",
                    reasonCode = TechnicalReasonCode.EMA_GOLDEN_CROSS.name,
                    sourceBarTimestamps = listOf(1_000L, 2_000L),
                    values = mapOf("fastEma" to 10.5, "slowEma" to 10.2),
                    parameters = mapOf("fastPeriod" to 12.0),
                ),
            ),
            createdAtMillis = 3_000L,
        )

        assertEquals(signal, signal.toEntity().toDomain())
    }
}
