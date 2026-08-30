package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import kotlinx.coroutines.flow.Flow

interface AnalysisRuleRepository {
    fun observeEnabled(): Flow<List<AnalysisRule>>

    suspend fun getAll(): List<AnalysisRule>

    suspend fun save(rule: AnalysisRule)

    suspend fun delete(ruleId: String)
}
