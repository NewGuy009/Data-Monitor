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

/** Registration-only Korean provider; it reserves the contract without enabling a network call. */
@Singleton
class KoreanMarketDataProvider @Inject constructor() : MarketDataProvider {
    override val id: DataProviderId = DataProviders.KOREA
    override val symbolMapper: ProviderSymbolMapper = KoreanSymbolMapper
    override val capabilities = ProviderCapabilities(
        byMarket = mapOf(
            "KR-KOSPI" to setOf(ProviderCapability.QUOTE, ProviderCapability.SEARCH),
            "KR-KOSDAQ" to setOf(ProviderCapability.QUOTE, ProviderCapability.SEARCH),
        ),
    )
    override val marketDataSource: MarketDataSource? = null
    override val detailDataSource: StockDetailDataSource? = null
    override val searchDataSource: StockSearchDataSource? = null
}
