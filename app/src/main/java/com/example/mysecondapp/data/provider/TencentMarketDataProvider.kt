package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.detail.TencentDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.market.TencentMarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.data.search.TencentStockSearchDataSource
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.ProviderCapabilities
import com.example.mysecondapp.domain.model.ProviderCapability
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TencentMarketDataProvider @Inject constructor(
    private val quotes: TencentMarketDataSource,
    private val details: TencentDetailDataSource,
    private val search: TencentStockSearchDataSource,
) : MarketDataProvider {
    override val id: DataProviderId = DataProviders.TENCENT
    override val symbolMapper: ProviderSymbolMapper = TencentSymbolMapper
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        byMarket = mapOf(
            "SH" to setOf(
                ProviderCapability.QUOTE,
                ProviderCapability.SEARCH,
                ProviderCapability.INTRADAY,
                ProviderCapability.CANDLES,
                ProviderCapability.ORDER_BOOK,
            ),
            "SZ" to setOf(
                ProviderCapability.QUOTE,
                ProviderCapability.SEARCH,
                ProviderCapability.INTRADAY,
                ProviderCapability.CANDLES,
                ProviderCapability.ORDER_BOOK,
            ),
            "BJ" to setOf(ProviderCapability.QUOTE),
        ),
    )
    override val marketDataSource: MarketDataSource = quotes
    override val detailDataSource: StockDetailDataSource = details
    override val searchDataSource: StockSearchDataSource = search
}
