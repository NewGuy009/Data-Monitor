package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.model.StockSearchItem

interface StockSearchRepository {
    suspend fun search(
        query: String,
        limit: Int = 8,
    ): List<StockSearchItem>
}
