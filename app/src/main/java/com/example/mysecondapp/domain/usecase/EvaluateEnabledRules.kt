package com.example.mysecondapp.domain.usecase

import com.example.mysecondapp.domain.analysis.rule.RuleAnalysisContext
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluation
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluationStatus
import com.example.mysecondapp.domain.analysis.rule.RuleEvaluator
import com.example.mysecondapp.domain.analysis.rule.SignalRecord
import com.example.mysecondapp.domain.analysis.rule.toSignalKey
import com.example.mysecondapp.domain.repository.AnalysisRuleRepository
import com.example.mysecondapp.domain.repository.SignalRepository
import javax.inject.Inject

/**
 * Evaluates persisted rules and records matched historical facts.
 *
 * The evaluator remains pure and the signal repository owns durable idempotency, so this use case
 * can later be called by replay, detail refresh, or background monitoring without UI coupling.
 */
class EvaluateEnabledRules @Inject constructor(
    private val ruleRepository: AnalysisRuleRepository,
    private val signalRepository: SignalRepository,
    private val ruleEvaluator: RuleEvaluator = RuleEvaluator(),
) {
    suspend operator fun invoke(
        context: RuleAnalysisContext,
        createdAtMillis: Long,
    ): List<RuleEvaluation> = ruleRepository.getAll()
        .asSequence()
        .filter { it.enabled }
        .map { rule -> ruleEvaluator.evaluate(rule, context) }
        .toList()
        .also { evaluations ->
            evaluations
                .filter { it.status == RuleEvaluationStatus.MATCHED }
                .forEach { evaluation -> signalRepository.insertIfAbsent(evaluation.toSignalRecord(createdAtMillis)) }
        }
}

private fun RuleEvaluation.toSignalRecord(createdAtMillis: Long): SignalRecord = SignalRecord(
    key = toSignalKey(),
    cutoffMillis = cutoffMillis,
    evidence = evidence,
    createdAtMillis = createdAtMillis,
)
