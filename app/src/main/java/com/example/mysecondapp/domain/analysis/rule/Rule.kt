package com.example.mysecondapp.domain.analysis.rule

import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.indicator.IndicatorSeries
import com.example.mysecondapp.domain.analysis.indicator.IndicatorValueState
import com.example.mysecondapp.domain.analysis.indicator.HistoricalAnalysisInput
import com.example.mysecondapp.domain.analysis.signal.TechnicalReasonCode
import com.example.mysecondapp.domain.analysis.signal.TechnicalResult
import com.example.mysecondapp.domain.analysis.signal.TechnicalResultStatus
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.StockIdentity
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
enum class RuleDirection { BULLISH, BEARISH }

@Serializable
enum class ThresholdOperator {
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    EQUALS,
}

/** Typed rule tree; rules evaluate domain values and never parse UI labels. */
@Serializable
sealed interface RuleCondition {
    @Serializable
    data class TechnicalEvent(
        val resultKind: String,
        val reasonCode: TechnicalReasonCode,
    ) : RuleCondition

    @Serializable
    data class IndicatorThreshold(
        val resultKind: String,
        val valueKey: String,
        val operator: ThresholdOperator,
        val threshold: Double,
    ) : RuleCondition {
        init {
            require(threshold.isFinite())
        }
    }

    @Serializable
    data class All(val children: List<RuleCondition>) : RuleCondition {
        init { require(children.isNotEmpty()) }
    }

    @Serializable
    data class Any(val children: List<RuleCondition>) : RuleCondition {
        init { require(children.isNotEmpty()) }
    }

    @Serializable
    data class Not(val child: RuleCondition) : RuleCondition
}

@Serializable
data class AnalysisRule(
    val id: String,
    val version: Int,
    val name: String,
    val period: CandlePeriod,
    val direction: RuleDirection,
    val condition: RuleCondition,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(name.isNotBlank())
    }
}

/** All values needed to evaluate one rule at one historical cutoff. */
data class RuleAnalysisContext(
    val input: HistoricalAnalysisInput,
    val technicalResults: List<TechnicalResult>,
    val indicatorSeries: List<IndicatorSeries> = emptyList(),
) {
    val cutoffMillis: Long
        get() = input.analysisCutoffMillis
}

enum class RuleEvaluationStatus {
    MATCHED,
    NOT_MATCHED,
    UNAVAILABLE,
    DATA_QUALITY_BLOCKED,
}

@Serializable
data class RuleEvidence(
    val conditionType: String,
    val resultKind: String,
    val reasonCode: String,
    val sourceBarTimestamps: List<Long>,
    val values: Map<String, Double> = emptyMap(),
    val parameters: Map<String, Double> = emptyMap(),
) {
    init {
        require(sourceBarTimestamps.isNotEmpty())
        require(values.values.all(Double::isFinite))
        require(parameters.values.all(Double::isFinite))
    }
}

data class RuleEvaluation(
    val rule: AnalysisRule,
    val status: RuleEvaluationStatus,
    val cutoffMillis: Long,
    val identity: StockIdentity,
    val adjustment: CandleAdjustment,
    val providerId: DataProviderId,
    val signalBarTimestampMillis: Long,
    val evidence: List<RuleEvidence>,
    val reason: String,
)

/**
 * Stable identity for a historical signal. Rule version is included so changing a rule
 * cannot reinterpret an old record or collide with a previous version's signal.
 */
data class SignalKey(
    val ruleId: String,
    val ruleVersion: Int,
    val identity: StockIdentity,
    val period: CandlePeriod,
    val adjustment: CandleAdjustment,
    val providerId: DataProviderId,
    val signalBarTimestampMillis: Long,
    val direction: RuleDirection,
)

data class SignalRecord(
    val key: SignalKey,
    val cutoffMillis: Long,
    val evidence: List<RuleEvidence>,
    val createdAtMillis: Long,
)

/** In-memory policy used by M3.7; M3.8 will replace the set with a Room unique key. */
class SignalDeduplicator {
    private val acceptedKeys = mutableSetOf<SignalKey>()

    @Synchronized
    fun accept(evaluation: RuleEvaluation, createdAtMillis: Long): SignalRecord? {
        if (evaluation.status != RuleEvaluationStatus.MATCHED) return null
        val key = evaluation.toSignalKey()
        if (!acceptedKeys.add(key)) return null
        return SignalRecord(key, evaluation.cutoffMillis, evaluation.evidence, createdAtMillis)
    }
}

fun RuleEvaluation.toSignalKey(): SignalKey {
    return SignalKey(
        ruleId = rule.id,
        ruleVersion = rule.version,
        identity = identity,
        period = rule.period,
        adjustment = adjustment,
        providerId = providerId,
        signalBarTimestampMillis = signalBarTimestampMillis,
        direction = rule.direction,
    )
}

/** Typed evaluator with deterministic group semantics and evidence propagation. */
class RuleEvaluator @Inject constructor() {
    fun evaluate(rule: AnalysisRule, context: RuleAnalysisContext): RuleEvaluation {
        val evaluation = if (!rule.enabled) {
            baseEvaluation(rule, context, RuleEvaluationStatus.NOT_MATCHED, emptyList(), "Rule is disabled.")
        } else if (rule.period != context.input.series.period) {
            baseEvaluation(rule, context, RuleEvaluationStatus.UNAVAILABLE, emptyList(), "Rule period is not available.")
        } else if (context.input.quality != HistoricalBarQuality.COMPLETE) {
            baseEvaluation(
                rule,
                context,
                RuleEvaluationStatus.DATA_QUALITY_BLOCKED,
                emptyList(),
                "Historical data quality is ${context.input.quality}.",
            )
        } else {
            val result = evaluateCondition(rule.condition, context)
            baseEvaluation(rule, context, result.status.toRuleStatus(), result.evidence, result.reason)
        }
        return evaluation
    }

    private fun baseEvaluation(
        rule: AnalysisRule,
        context: RuleAnalysisContext,
        status: RuleEvaluationStatus,
        evidence: List<RuleEvidence>,
        reason: String,
    ): RuleEvaluation = RuleEvaluation(
        rule = rule,
        status = status,
        cutoffMillis = context.cutoffMillis,
        identity = context.input.series.identity,
        adjustment = context.input.series.adjustment,
        providerId = context.input.series.providerId,
        signalBarTimestampMillis = context.input.bars.lastOrNull()?.timestampMillis ?: context.cutoffMillis,
        evidence = evidence,
        reason = reason,
    )

    private fun evaluateCondition(condition: RuleCondition, context: RuleAnalysisContext): ConditionEvaluation = when (condition) {
        is RuleCondition.TechnicalEvent -> {
            val result = context.technicalResults.firstOrNull { it.kind == condition.resultKind }
            when {
                result == null -> unavailable(condition.resultKind, "Technical result is missing.")
                result.status == TechnicalResultStatus.UNAVAILABLE -> unavailable(condition.resultKind, "Technical result is unavailable.")
                result.reasonCode != condition.reasonCode -> notMatched(condition.resultKind, result)
                else -> matched("technical_event", result)
            }
        }

        is RuleCondition.IndicatorThreshold -> {
            val result = context.technicalResults.firstOrNull { it.kind == condition.resultKind }
            val indicator = context.indicatorSeries.firstOrNull { it.definition.id == condition.resultKind }
            val indicatorValue = indicator?.valueAt(context.cutoffMillis)
            val value = result?.values?.get(condition.valueKey) ?: indicatorValue?.value(condition.valueKey)
            when {
                result == null && indicator == null -> unavailable(condition.resultKind, "Indicator result is unavailable.")
                result?.status == TechnicalResultStatus.UNAVAILABLE || indicatorValue?.state != null && indicatorValue.state != IndicatorValueState.VALUE -> unavailable(condition.resultKind, "Indicator result is unavailable.")
                value == null -> unavailable(condition.resultKind, "Indicator value is missing.")
                condition.operator.matches(value, condition.threshold) -> {
                    if (result != null) matched("indicator_threshold", result)
                    else matched("indicator_threshold", indicatorValue!!.toTechnicalResult(condition.resultKind, indicator!!))
                }
                else -> {
                    if (result != null) notMatched(condition.resultKind, result)
                    else notMatched(condition.resultKind, indicatorValue!!.toTechnicalResult(condition.resultKind, indicator!!))
                }
            }
        }

        is RuleCondition.All -> combineAll(condition.children.map { evaluateCondition(it, context) })
        is RuleCondition.Any -> combineAny(condition.children.map { evaluateCondition(it, context) })
        is RuleCondition.Not -> {
            val child = evaluateCondition(condition.child, context)
            when (child.status) {
                ConditionStatus.MATCHED -> ConditionEvaluation(ConditionStatus.NOT_MATCHED, child.evidence, "NOT condition matched.")
                ConditionStatus.NOT_MATCHED -> ConditionEvaluation(ConditionStatus.MATCHED, child.evidence, "NOT condition did not match.")
                ConditionStatus.UNAVAILABLE -> child
            }
        }
    }

    private fun combineAll(children: List<ConditionEvaluation>): ConditionEvaluation = when {
        children.any { it.status == ConditionStatus.NOT_MATCHED } -> ConditionEvaluation(
            ConditionStatus.NOT_MATCHED,
            children.flatMap { it.evidence },
            "At least one ALL condition did not match.",
        )
        children.any { it.status == ConditionStatus.UNAVAILABLE } -> ConditionEvaluation(
            ConditionStatus.UNAVAILABLE,
            children.flatMap { it.evidence },
            "At least one ALL condition is unavailable.",
        )
        else -> ConditionEvaluation(ConditionStatus.MATCHED, children.flatMap { it.evidence }, "All conditions matched.")
    }

    private fun combineAny(children: List<ConditionEvaluation>): ConditionEvaluation = when {
        children.any { it.status == ConditionStatus.MATCHED } -> ConditionEvaluation(
            ConditionStatus.MATCHED,
            children.flatMap { it.evidence },
            "At least one ANY condition matched.",
        )
        children.any { it.status == ConditionStatus.UNAVAILABLE } -> ConditionEvaluation(
            ConditionStatus.UNAVAILABLE,
            children.flatMap { it.evidence },
            "No ANY condition matched and at least one is unavailable.",
        )
        else -> ConditionEvaluation(ConditionStatus.NOT_MATCHED, children.flatMap { it.evidence }, "No ANY condition matched.")
    }
}

private enum class ConditionStatus { MATCHED, NOT_MATCHED, UNAVAILABLE }

private data class ConditionEvaluation(
    val status: ConditionStatus,
    val evidence: List<RuleEvidence>,
    val reason: String,
)

private fun ConditionStatus.toRuleStatus(): RuleEvaluationStatus = when (this) {
    ConditionStatus.MATCHED -> RuleEvaluationStatus.MATCHED
    ConditionStatus.NOT_MATCHED -> RuleEvaluationStatus.NOT_MATCHED
    ConditionStatus.UNAVAILABLE -> RuleEvaluationStatus.UNAVAILABLE
}

private fun matched(type: String, result: TechnicalResult): ConditionEvaluation = ConditionEvaluation(
    ConditionStatus.MATCHED,
    listOf(result.toEvidence(type)),
    "Condition matched.",
)

private fun notMatched(kind: String, result: TechnicalResult): ConditionEvaluation = ConditionEvaluation(
    ConditionStatus.NOT_MATCHED,
    listOf(result.toEvidence("condition")),
    "Condition did not match.",
)

private fun unavailable(kind: String, reason: String): ConditionEvaluation = ConditionEvaluation(
    ConditionStatus.UNAVAILABLE,
    emptyList(),
    "$kind: $reason",
)

private fun TechnicalResult.toEvidence(type: String): RuleEvidence = RuleEvidence(
    conditionType = type,
    resultKind = kind,
    reasonCode = reasonCode.name,
    sourceBarTimestamps = sourceBarTimestamps,
    values = values,
    parameters = parameters,
)

private fun com.example.mysecondapp.domain.analysis.indicator.IndicatorValue.toTechnicalResult(
    resultKind: String,
    series: IndicatorSeries,
): TechnicalResult = TechnicalResult(
    kind = resultKind,
    status = TechnicalResultStatus.MATCHED,
    reasonCode = TechnicalReasonCode.INDICATOR_UNAVAILABLE,
    sourceBarTimestamps = listOf(timestampMillis),
    values = values,
    parameters = series.definition.parameters,
)

private fun ThresholdOperator.matches(value: Double, threshold: Double): Boolean = when (this) {
    ThresholdOperator.GREATER_THAN -> value > threshold
    ThresholdOperator.GREATER_OR_EQUAL -> value >= threshold
    ThresholdOperator.LESS_THAN -> value < threshold
    ThresholdOperator.LESS_OR_EQUAL -> value <= threshold
    ThresholdOperator.EQUALS -> value == threshold
}

object M3RuleTemplates {
    fun emaGoldenCross() = AnalysisRule(
        id = "ema_golden_cross",
        version = 1,
        name = "EMA golden cross",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BULLISH,
        condition = RuleCondition.TechnicalEvent("ema_cross", TechnicalReasonCode.EMA_GOLDEN_CROSS),
    )

    fun emaDeathCross() = AnalysisRule(
        id = "ema_death_cross",
        version = 1,
        name = "EMA death cross",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BEARISH,
        condition = RuleCondition.TechnicalEvent("ema_cross", TechnicalReasonCode.EMA_DEATH_CROSS),
    )

    fun rsiOversoldRecovery() = AnalysisRule(
        id = "rsi_oversold_recovery",
        version = 1,
        name = "RSI oversold recovery",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BULLISH,
        condition = RuleCondition.TechnicalEvent("rsi_recovery", TechnicalReasonCode.RSI_RECOVERY),
    )

    fun volumeBreakout() = AnalysisRule(
        id = "volume_breakout",
        version = 1,
        name = "Volume breakout",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BULLISH,
        condition = RuleCondition.TechnicalEvent("range_breakout", TechnicalReasonCode.RANGE_BREAKOUT),
    )

    fun bullishEngulfing() = AnalysisRule(
        id = "bullish_engulfing",
        version = 1,
        name = "Bullish engulfing",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BULLISH,
        condition = RuleCondition.TechnicalEvent("engulfing", TechnicalReasonCode.BULLISH_ENGULFING),
    )

    fun bearishEngulfing() = AnalysisRule(
        id = "bearish_engulfing",
        version = 1,
        name = "Bearish engulfing",
        period = CandlePeriod.DAY,
        direction = RuleDirection.BEARISH,
        condition = RuleCondition.TechnicalEvent("engulfing", TechnicalReasonCode.BEARISH_ENGULFING),
    )
}
