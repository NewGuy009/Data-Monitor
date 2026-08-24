package com.example.mysecondapp.data.market

import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketDataContract
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.Quote

interface MarketDataSource {
    val source: MarketSource
    val dataContract: MarketDataContract
        get() = MarketDataContracts.A_SHARE

    suspend fun fetchQuotes(stockCodes: List<String>): MarketDataResult<List<Quote>>
}
