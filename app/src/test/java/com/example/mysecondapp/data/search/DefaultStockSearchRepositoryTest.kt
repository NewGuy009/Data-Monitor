package com.example.mysecondapp.data.search

import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.StockSearchItem
import com.example.mysecondapp.data.provider.MarketDataProvider
import com.example.mysecondapp.data.provider.MarketDataProviderSelector
import com.example.mysecondapp.data.provider.TencentSymbolMapper
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.ProviderCapabilities
import com.example.mysecondapp.domain.model.ProviderCapability
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.data.detail.StockDetailDataSource
import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.search.StockSearchDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStockSearchRepositoryTest {

    private val remoteDataSource = FakeStockSearchDataSource()
    private val repository = DefaultStockSearchRepository(remoteDataSource)

    @Test
    fun `search returns exact code match first`() = runBlocking {
        val results = repository.search(query = "600519", limit = 5)

        assertTrue(results.isNotEmpty())
        assertEquals("600519", results.first().code)
    }

    @Test
    fun `search supports pinyin shorthand`() = runBlocking {
        val results = repository.search(query = "gzmt", limit = 5)

        assertTrue(results.any { item -> item.code == "600519" })
    }

    @Test
    fun `search returns empty list for blank query`() = runBlocking {
        val results = repository.search(query = "   ", limit = 5)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search includes remote result outside local dictionary`() = runBlocking {
        remoteDataSource.result = MarketDataResult.Success(
            value = listOf(StockSearchItem("SH", "603999", "Remote Symbol")),
            source = MarketSource.TENCENT,
            fetchedAtMillis = 1_000L,
        )

        val results = repository.search(query = "603999", limit = 5)

        assertEquals("603999", results.single().code)
        assertEquals("Remote Symbol", results.single().name)
    }

    @Test
    fun `search keeps local fallback when remote source fails`() = runBlocking {
        remoteDataSource.result = MarketDataResult.Failure(MarketError.Network("offline"))

        val results = repository.search(query = "600519", limit = 5)

        assertTrue(results.any { item -> item.code == "600519" })
    }

    @Test
    fun `search filters remote results outside selected provider capabilities`() = runBlocking {
        val source = FakeStockSearchDataSource().apply {
            result = MarketDataResult.Success(
                value = listOf(
                    StockSearchItem("SH", "603999", "Supported"),
                    StockSearchItem("US-NASDAQ", "AAPL", "Not Supported Yet"),
                ),
                source = MarketSource.TENCENT,
                fetchedAtMillis = 1_000L,
            )
        }
        val provider = object : MarketDataProvider {
            override val id = DataProviderId("tencent")
            override val capabilities = ProviderCapabilities(
                mapOf("SH" to setOf(ProviderCapability.SEARCH)),
            )
            override val symbolMapper = TencentSymbolMapper
            override val marketDataSource: MarketDataSource? = null
            override val detailDataSource: StockDetailDataSource? = null
            override val searchDataSource: StockSearchDataSource = source
        }
        val repository = DefaultStockSearchRepository(
            object : MarketDataProviderSelector {
                override suspend fun quoteProviders(markets: Set<String>) = emptyList<MarketDataProvider>()
                override suspend fun searchProviders(markets: Set<String>) = listOf(provider)
                override suspend fun detailProviders(
                    identity: StockIdentity,
                    capability: ProviderCapability,
                ) = emptyList<MarketDataProvider>()
            },
        )

        val results = repository.search("603999", limit = 8)

        assertEquals(listOf("603999"), results.map { item -> item.code })
    }
}

private class FakeStockSearchDataSource : StockSearchDataSource {
    var result: MarketDataResult<List<StockSearchItem>> = MarketDataResult.Success(
        value = emptyList(),
        source = MarketSource.TENCENT,
        fetchedAtMillis = 1_000L,
    )

    override suspend fun search(query: String): MarketDataResult<List<StockSearchItem>> = result
}
