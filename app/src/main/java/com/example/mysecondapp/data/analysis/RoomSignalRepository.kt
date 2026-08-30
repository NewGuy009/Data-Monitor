package com.example.mysecondapp.data.analysis

import com.example.mysecondapp.data.local.SignalDao
import com.example.mysecondapp.domain.analysis.rule.SignalRecord
import com.example.mysecondapp.domain.repository.SignalRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSignalRepository @Inject constructor(
    private val dao: SignalDao,
) : SignalRepository {
    override suspend fun insertIfAbsent(signal: SignalRecord): Boolean = dao.insertIgnore(signal.toEntity()) != -1L

    override fun observeRecent(limit: Int): Flow<List<SignalRecord>> = dao.observeRecent(limit).map { signals -> signals.map { it.toDomain() } }

    override suspend fun getForStock(market: String, code: String): List<SignalRecord> =
        dao.getForStock(market, code).map { it.toDomain() }
}
