package com.example.mysecondapp.domain.analysis.replay

import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidationOptions
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidator
import com.example.mysecondapp.domain.analysis.indicator.toAnalysisInput
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.analysis.rule.RuleAnalysisContext
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluation
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluationStatus
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluator
import com.example.mysecondapp.domain.analysis.signal.BollingerPositionDetector
import com.example.mysecondapp.domain.analysis.signal.EmaCrossDetector
import com.example.mysecondapp.domain.analysis.signal.EngulfingPatternDetector
import com.example.mysecondapp.domain.analysis.signal.ObvStateDetector
import com.example.mysecondapp.domain.analysis.signal.RangeBreakoutDetector
import com.example.mysecondapp.domain.analysis.signal.RsiRecoveryDetector
import com.example.mysecondapp.domain.analysis.signal.RsiStateDetector
import com.example.mysecondapp.domain.analysis.signal.TechnicalResult
import com.example.mysecondapp.domain.analysis.signal.TrendStateDetector
import com.example.mysecondapp.domain.analysis.signal.VolumeStateDetector
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import javax.inject.Inject

enum class ReplayPointStatus {
    EVALUATED,
    INSUFFICIENT_HISTORY,
    DATA_QUALITY_BLOCKED,
    PARTIAL,
}

data class ReplayPoint(
    val barTimestampMillis: Long,
    val status: ReplayPointStatus,
    val evaluation: RuleEvaluation? = null,
    val issueCodes: List<String> = emptyList(),
)

data class HistoricalReplayResult(
    val rule: AnalysisRule,
    val points: List<ReplayPoint>,
) {
    val matchedPoints: List<ReplayPoint>
        get() = points.filter { it.evaluation?.status == RuleEvaluationStatus.MATCHED }
}

/**
 * Replays one rule Bar-by-Bar. Every prefix is rebuilt from the source series so a later Bar
 * cannot affect an earlier decision, which is the primary historical backtest safety invariant.
 */
class HistoricalRuleReplayer @Inject constructor(
    private val validator: HistoricalBarValidator,
    private val evaluator: RuleEvaluator,
) {
    fun replay(rule: AnalysisRule, series: HistoricalBarSeries): HistoricalReplayResult {
        val sourceBars = series.barsAtOrBeforeCutoff()
        val points = sourceBars.mapIndexed { index, bar ->
            val prefix = series.copy(
                bars = sourceBars.take(index + 1),
                analysisCutoffMillis = bar.timestampMillis,
                // Older Bars are historical facts; preserve the caller's uncertainty only for the final source Bar.
                cutoffBarCompletion = if (index == sourceBars.lastIndex) {
                    series.cutoffBarCompletion
                } else {
                    HistoricalBarCompletion.CONFIRMED
                },
            )
            val validation = validator.validate(
                prefix,
                HistoricalBarValidationOptions(minimumBarCount = 2),
            )
            when {
                validation.quality == HistoricalBarQuality.PARTIAL -> ReplayPoint(
                    barTimestampMillis = bar.timestampMillis,
                    status = ReplayPointStatus.PARTIAL,
                    issueCodes = validation.issues.map { it.code.name },
                )

                validation.quality != HistoricalBarQuality.COMPLETE -> ReplayPoint(
                    barTimestampMillis = bar.timestampMillis,
                    status = if (validation.quality == HistoricalBarQuality.INSUFFICIENT_HISTORY) {
                        ReplayPointStatus.INSUFFICIENT_HISTORY
                    } else {
                        ReplayPointStatus.DATA_QUALITY_BLOCKED
                    },
                    issueCodes = validation.issues.map { it.code.name },
                )

                else -> {
                    val input = validation.toAnalysisInput()
                    val technicalResults = buildTechnicalResults(input)
                    val evaluation = evaluator.evaluate(rule, RuleAnalysisContext(input, technicalResults))
                    ReplayPoint(
                        barTimestampMillis = bar.timestampMillis,
                        status = ReplayPointStatus.EVALUATED,
                        evaluation = evaluation,
                    )
                }
            }
        }
        return HistoricalReplayResult(rule, points)
    }

    private fun buildTechnicalResults(input: com.example.mysecondapp.domain.analysis.indicator.HistoricalAnalysisInput): List<TechnicalResult> = listOf(
        TrendStateDetector().detect(input),
        EmaCrossDetector().detect(input),
        RsiStateDetector().detect(input),
        RsiRecoveryDetector().detect(input),
        VolumeStateDetector().detect(input),
        BollingerPositionDetector().detect(input),
        ObvStateDetector().detect(input),
        EngulfingPatternDetector().detect(input),
        RangeBreakoutDetector().detect(input),
    )
}
