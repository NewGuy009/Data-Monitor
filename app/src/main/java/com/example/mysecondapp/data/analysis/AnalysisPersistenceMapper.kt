package com.example.mysecondapp.data.analysis

import com.example.mysecondapp.data.local.AnalysisRuleEntity
import com.example.mysecondapp.data.local.SignalEntity
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.analysis.rule.RuleDirection
import com.example.mysecondapp.domain.analysis.rule.RuleEvidence
import com.example.mysecondapp.domain.analysis.rule.SignalRecord
import com.example.mysecondapp.domain.analysis.rule.SignalKey
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.StockIdentity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val persistenceJson = Json {
    classDiscriminator = "conditionType"
    ignoreUnknownKeys = true
}

/** Room 只保存稳定协议字段；条件与证据由版本化 JSON 承载，避免 UI 文本成为数据格式。 */
fun AnalysisRule.toEntity(): AnalysisRuleEntity = AnalysisRuleEntity(
    ruleId = id,
    version = version,
    name = name,
    period = period.name,
    direction = direction.name,
    conditionJson = persistenceJson.encodeToString(condition),
    enabled = enabled,
)

fun AnalysisRuleEntity.toDomain(): AnalysisRule = AnalysisRule(
    id = ruleId,
    version = version,
    name = name,
    period = CandlePeriod.valueOf(period),
    direction = RuleDirection.valueOf(direction),
    condition = persistenceJson.decodeFromString(conditionJson),
    enabled = enabled,
)

fun SignalRecord.toEntity(): SignalEntity = SignalEntity(
    ruleId = key.ruleId,
    ruleVersion = key.ruleVersion,
    market = key.identity.market,
    code = key.identity.code,
    period = key.period.name,
    adjustment = key.adjustment.name,
    providerId = key.providerId.value,
    signalBarTimestampMillis = key.signalBarTimestampMillis,
    direction = key.direction.name,
    cutoffMillis = cutoffMillis,
    evidenceJson = persistenceJson.encodeToString(ListSerializer(RuleEvidence.serializer()), evidence),
    createdAtMillis = createdAtMillis,
)

fun SignalEntity.toDomain(): SignalRecord = SignalRecord(
    key = SignalKey(
        ruleId = ruleId,
        ruleVersion = ruleVersion,
        identity = StockIdentity(market, code),
        period = CandlePeriod.valueOf(period),
        adjustment = CandleAdjustment.valueOf(adjustment),
        providerId = DataProviderId(providerId),
        signalBarTimestampMillis = signalBarTimestampMillis,
        direction = RuleDirection.valueOf(direction),
    ),
    cutoffMillis = cutoffMillis,
    evidence = persistenceJson.decodeFromString(ListSerializer(RuleEvidence.serializer()), evidenceJson),
    createdAtMillis = createdAtMillis,
)
