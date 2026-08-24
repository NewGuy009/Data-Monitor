package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.QuoteSnapshot
import kotlinx.coroutines.flow.Flow

interface MarketRepository {
    fun observeSnapshots(): Flow<List<QuoteSnapshot>>

    suspend fun refreshWatchlistQuotes(): MarketDataResult<List<Quote>>
}
