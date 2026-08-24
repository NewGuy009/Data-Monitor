package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.market.SinaMarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.ProviderCapabilities
import com.example.mysecondapp.domain.model.ProviderCapability
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SinaMarketDataProvider @Inject constructor(
    private val quotes: SinaMarketDataSource,
) : MarketDataProvider {
    override val id: DataProviderId = DataProviders.SINA
    override val symbolMapper: ProviderSymbolMapper = SinaSymbolMapper
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        byMarket = mapOf(
            "SH" to setOf(ProviderCapability.QUOTE),
            "SZ" to setOf(ProviderCapability.QUOTE),
        ),
    )
    override val marketDataSource: MarketDataSource = quotes
    override val detailDataSource: StockDetailDataSource? = null
    override val searchDataSource: StockSearchDataSource? = null
}
