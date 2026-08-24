package com.example.mysecondapp.data.provider

import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.ProviderCapabilities
import com.example.mysecondapp.domain.model.ProviderCapability
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.DataSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataProviderRegistryTest {

    @Test
    fun `registry looks up providers by stable id and returns deterministic order`() {
        val registry = MarketDataProviderRegistry(
            setOf(
                FakeProvider("sina"),
                FakeProvider("tencent"),
            ),
        )

        assertEquals(listOf("sina", "tencent"), registry.all.map { provider -> provider.id.value })
        assertEquals("tencent", registry.find(DataProviderId("tencent"))?.id?.value)
        assertNull(registry.find(DataProviderId("unknown")))
    }

    @Test
    fun `provider capabilities are scoped by market`() {
        val capabilities = ProviderCapabilities(
            byMarket = mapOf(
                "SH" to setOf(ProviderCapability.QUOTE, ProviderCapability.CANDLES),
                "US-NASDAQ" to setOf(ProviderCapability.QUOTE),
            ),
        )

        assertTrue(capabilities.supports("sh", ProviderCapability.CANDLES))
        assertTrue(capabilities.supports("US-NASDAQ", ProviderCapability.QUOTE))
        assertEquals(setOf("SH"), capabilities.supportedMarkets(ProviderCapability.CANDLES))
    }

    @Test
    fun `registry rejects duplicate provider ids`() {
        var rejected = false
        try {
            MarketDataProviderRegistry(setOf(FakeProvider("same"), FakeProvider("same")))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }

    @Test
    fun `preference orders primary first and removes duplicate fallback providers`() {
        val preference = DataSourcePreference(
            primaryProviderId = DataProviders.SINA,
            fallbackProviderIds = listOf(DataProviders.SINA, DataProviders.TENCENT, DataProviders.TENCENT),
        )

        assertEquals(
            listOf(DataProviders.SINA, DataProviders.TENCENT),
            preference.orderedProviderIds(),
        )
    }

    @Test
    fun `reserved global providers expose contracts without executable network sources`() {
        val usProvider = UsMarketDataProvider()
        val koreanProvider = KoreanMarketDataProvider()

        assertTrue(usProvider.capabilities.supports("US-NASDAQ", ProviderCapability.QUOTE))
        assertTrue(koreanProvider.capabilities.supports("KR-KOSPI", ProviderCapability.SEARCH))
        assertNull(usProvider.marketDataSource)
        assertNull(usProvider.detailDataSource)
        assertNull(koreanProvider.marketDataSource)
        assertNull(koreanProvider.searchDataSource)
    }
}

private class FakeProvider(id: String) : MarketDataProvider {
    override val id = DataProviderId(id)
    override val capabilities = ProviderCapabilities(emptyMap())
    override val marketDataSource: MarketDataSource? = null
    override val detailDataSource: StockDetailDataSource? = null
    override val searchDataSource: StockSearchDataSource? = null
}
