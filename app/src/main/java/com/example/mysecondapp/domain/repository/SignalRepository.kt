package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.analysis.rule.SignalRecord
import kotlinx.coroutines.flow.Flow

interface SignalRepository {
    /** 返回 true only when SQLite inserted a new signal identity. */
    suspend fun insertIfAbsent(signal: SignalRecord): Boolean

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<SignalRecord>>

    suspend fun getForStock(market: String, code: String): List<SignalRecord>

    companion object { const val DEFAULT_LIMIT = 100 }
}
