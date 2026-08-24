package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.data.settings.SettingsRepository
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.ProviderCapability
import com.example.mysecondapp.domain.model.StockIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface MarketDataProviderSelector {
    suspend fun quoteProviders(markets: Set<String>): List<MarketDataProvider>

    suspend fun searchProviders(markets: Set<String>): List<MarketDataProvider>

    suspend fun detailProviders(
        identity: StockIdentity,
        capability: ProviderCapability,
    ): List<MarketDataProvider>
}

@Singleton
class DefaultMarketDataProviderSelector @Inject constructor(
    private val registry: MarketDataProviderRegistry,
    private val settingsRepository: SettingsRepository,
) : MarketDataProviderSelector {

    override suspend fun quoteProviders(markets: Set<String>): List<MarketDataProvider> =
        orderedProviders().filter { provider ->
            provider.marketDataSource != null && markets.any { market ->
                provider.capabilities.supports(market, ProviderCapability.QUOTE)
            }
        }

    override suspend fun searchProviders(markets: Set<String>): List<MarketDataProvider> =
        orderedProviders().filter { provider ->
            provider.searchDataSource != null && markets.any { market ->
                provider.capabilities.supports(market, ProviderCapability.SEARCH)
            }
        }

    override suspend fun detailProviders(
        identity: StockIdentity,
        capability: ProviderCapability,
    ): List<MarketDataProvider> = orderedProviders().filter { provider ->
        provider.detailDataSource != null &&
            provider.capabilities.supports(identity.market, capability)
    }

    private suspend fun orderedProviders(): List<MarketDataProvider> {
        val preference = settingsRepository.dataSourcePreference.first()
        return preference.orderedProviderIds().mapNotNull(registry::find)
    }
}

/** Compatibility selector used by existing tests while repositories migrate to provider selection. */
class FixedMarketDataProviderSelector(
    quoteSources: List<MarketDataSource>,
    detailSource: StockDetailDataSource? = null,
) : MarketDataProviderSelector {
    private val providers = quoteSources.mapIndexed { index, source ->
        FixedMarketDataProvider(
            id = DataProviderId(source.source.name.lowercase()),
            marketDataSource = source,
            detailDataSource = detailSource.takeIf { index == 0 },
        )
    }

    override suspend fun quoteProviders(markets: Set<String>): List<MarketDataProvider> =
        providers.filter { provider -> provider.marketDataSource != null }

    override suspend fun searchProviders(markets: Set<String>): List<MarketDataProvider> =
        emptyList()

    override suspend fun detailProviders(
        identity: StockIdentity,
        capability: ProviderCapability,
    ): List<MarketDataProvider> = providers.filter { provider -> provider.detailDataSource != null }
}

private class FixedMarketDataProvider(
    override val id: DataProviderId,
    override val marketDataSource: MarketDataSource?,
    override val detailDataSource: StockDetailDataSource?,
) : MarketDataProvider {
    override val symbolMapper: ProviderSymbolMapper = when (id.value) {
        DataProviderId("sina").value -> SinaSymbolMapper
        DataProviderId("tencent").value -> TencentSymbolMapper
        else -> LegacyMarketSymbolMapper
    }
    override val capabilities = com.example.mysecondapp.domain.model.ProviderCapabilities(
        byMarket = mapOf("SH" to ProviderCapability.entries.toSet(), "SZ" to ProviderCapability.entries.toSet()),
    )
    override val searchDataSource = null
}

/** Compatibility selector for search-only unit tests while production uses the registry selector. */
class FixedSearchMarketDataProviderSelector(
    source: StockSearchDataSource,
) : MarketDataProviderSelector {
    private val provider = SearchOnlyMarketDataProvider(source)

    override suspend fun quoteProviders(markets: Set<String>): List<MarketDataProvider> = emptyList()

    override suspend fun searchProviders(markets: Set<String>): List<MarketDataProvider> = listOf(provider)

    override suspend fun detailProviders(
        identity: StockIdentity,
        capability: ProviderCapability,
    ): List<MarketDataProvider> = emptyList()
}

private class SearchOnlyMarketDataProvider(
    override val searchDataSource: StockSearchDataSource,
) : MarketDataProvider {
    override val id = DataProviderId("tencent")
    override val symbolMapper = TencentSymbolMapper
    override val capabilities = com.example.mysecondapp.domain.model.ProviderCapabilities(
        byMarket = mapOf(
            "SH" to setOf(ProviderCapability.SEARCH),
            "SZ" to setOf(ProviderCapability.SEARCH),
            "BJ" to setOf(ProviderCapability.SEARCH),
        ),
    )
    override val marketDataSource: MarketDataSource? = null
    override val detailDataSource: StockDetailDataSource? = null
}
