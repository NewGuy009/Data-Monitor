package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.ProviderCapabilities
import com.example.mysecondapp.domain.model.ProviderCapability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registration-only US provider. Its symbol and capability contract is ready, but no external API
 * is bound yet, so the selector excludes it from executable requests.
 */
@Singleton
class UsMarketDataProvider @Inject constructor() : MarketDataProvider {
    override val id: DataProviderId = DataProviders.US
    override val symbolMapper: ProviderSymbolMapper = UsSymbolMapper
    override val capabilities = ProviderCapabilities(
        byMarket = mapOf(
            "US-NASDAQ" to setOf(ProviderCapability.QUOTE, ProviderCapability.SEARCH),
            "US-NYSE" to setOf(ProviderCapability.QUOTE, ProviderCapability.SEARCH),
        ),
    )
    override val marketDataSource: MarketDataSource? = null
    override val detailDataSource: StockDetailDataSource? = null
    override val searchDataSource: StockSearchDataSource? = null
}
