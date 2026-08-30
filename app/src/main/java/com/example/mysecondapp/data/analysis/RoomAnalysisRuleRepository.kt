package com.example.mysecondapp.data.analysis

import com.example.mysecondapp.data.local.AnalysisRuleDao
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.repository.AnalysisRuleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAnalysisRuleRepository @Inject constructor(
    private val dao: AnalysisRuleDao,
) : AnalysisRuleRepository {
    override fun observeEnabled(): Flow<List<AnalysisRule>> = dao.observeEnabled().map { rules -> rules.map { it.toDomain() } }

    override suspend fun getAll(): List<AnalysisRule> = dao.getAll().map { it.toDomain() }

    override suspend fun save(rule: AnalysisRule) = dao.upsert(rule.toEntity())

    override suspend fun delete(ruleId: String) = dao.delete(ruleId)
}
