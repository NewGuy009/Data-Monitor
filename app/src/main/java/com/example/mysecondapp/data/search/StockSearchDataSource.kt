package com.example.mysecondapp.data.search

import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.StockSearchItem

/** External symbol-search capability, isolated from the local fallback policy. */
interface StockSearchDataSource {
    suspend fun search(query: String): MarketDataResult<List<StockSearchItem>>
}
