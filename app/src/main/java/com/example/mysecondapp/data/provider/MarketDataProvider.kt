package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.ProviderCapabilities

/** Aggregates all optional capabilities belonging to one external data provider. */
interface MarketDataProvider {
    val id: DataProviderId
    val capabilities: ProviderCapabilities
    val symbolMapper: ProviderSymbolMapper
        get() = LegacyMarketSymbolMapper
    val marketDataSource: MarketDataSource?
    val detailDataSource: StockDetailDataSource?
    val searchDataSource: StockSearchDataSource?
}
