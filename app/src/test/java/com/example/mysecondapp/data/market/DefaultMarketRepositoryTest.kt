package com.example.mysecondapp.data.market

import com.example.mysecondapp.data.network.SinaStockApi
import com.example.mysecondapp.data.network.TencentStockApi
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.WatchlistItem
import com.example.mysecondapp.domain.repository.WatchlistRepository
import java.nio.charset.Charset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMarketRepositoryTest {

    @Test
    fun `refreshWatchlistQuotes merges primary and fallback quotes`() = runBlocking {
        val watchlist = listOf(
            WatchlistItem(market = "SH", code = "600000", name = "浦发银行", order = 0),
            WatchlistItem(market = "SZ", code = "000001", name = "平安银行", order = 1),
        )
        val tencentApi = CountingTencentStockApi(
            payload = buildRepositoryTencentLine(
                code = "600000",
                name = "浦发银行",
                latestPrice = "10.25",
                previousClosePrice = "10.00",
                openPrice = "10.10",
                highPrice = "10.30",
                lowPrice = "9.98",
                volumeHands = "123",
                turnoverWan = "456.78",
            ),
        )
        val sinaApi = CountingSinaStockApi(
            payload = buildRepositorySinaLine(
                market = "sz",
                code = "000001",
                name = "平安银行",
                openPrice = "11.00",
                previousClosePrice = "10.90",
                latestPrice = "11.20",
                highPrice = "11.35",
                lowPrice = "10.88",
                volume = "7654321",
                turnover = "99887766.55",
            ),
        )
        val repository = DefaultMarketRepository(
            watchlistRepository = FakeWatchlistRepository(watchlist),
            tencentMarketDataSource = TencentMarketDataSource(tencentApi),
            sinaMarketDataSource = SinaMarketDataSource(sinaApi),
        )

        val result = repository.refreshWatchlistQuotes()

        assertTrue(result is MarketDataResult.Success)
        val success = result as MarketDataResult.Success
        assertEquals(MarketSource.MIXED, success.source)
        assertEquals(2, success.value.size)
        assertEquals(1, tencentApi.callCount)
        assertEquals(1, sinaApi.callCount)

        val snapshots = repository.observeSnapshots().firstValue()
        assertEquals(2, snapshots.size)
        assertEquals("600000", snapshots[0].quote?.code)
        assertEquals("000001", snapshots[1].quote?.code)
        assertEquals(DataProviders.TENCENT, snapshots[0].providerId)
        assertEquals(DataProviders.SINA, snapshots[1].providerId)
    }

    @Test
    fun `refreshWatchlistQuotes serves memory cache within ttl`() = runBlocking {
        val watchlist = listOf(
            WatchlistItem(market = "SH", code = "600000", name = "浦发银行", order = 0),
        )
        val tencentApi = CountingTencentStockApi(
            payload = buildRepositoryTencentLine(
                code = "600000",
                name = "浦发银行",
                latestPrice = "10.25",
                previousClosePrice = "10.00",
                openPrice = "10.10",
                highPrice = "10.30",
                lowPrice = "9.98",
                volumeHands = "123",
                turnoverWan = "456.78",
            ),
        )
        val repository = DefaultMarketRepository(
            watchlistRepository = FakeWatchlistRepository(watchlist),
            tencentMarketDataSource = TencentMarketDataSource(tencentApi),
            sinaMarketDataSource = SinaMarketDataSource(CountingSinaStockApi(payload = "")),
        )

        val first = repository.refreshWatchlistQuotes()
        val second = repository.refreshWatchlistQuotes()

        assertTrue(first is MarketDataResult.Success)
        assertTrue(second is MarketDataResult.Success)
        val secondSuccess = second as MarketDataResult.Success
        assertEquals(MarketSource.CACHE, secondSuccess.source)
        assertEquals(1, tencentApi.callCount)
    }
}

private class FakeWatchlistRepository(
    private val items: List<WatchlistItem>,
) : WatchlistRepository {
    override fun observeWatchlistItems(): Flow<List<WatchlistItem>> = flowOf(items)

    override fun observeWatchlistGroup(groupName: String): Flow<List<WatchlistItem>> = flowOf(emptyList())

    override fun observeUngroupedWatchlistItems(): Flow<List<WatchlistItem>> = flowOf(emptyList())

    override fun observeGroupNames(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun addWatchlistItem(item: WatchlistItem) = Unit

    override suspend fun addWatchlistItems(items: List<WatchlistItem>) = Unit

    override suspend fun removeWatchlistItem(
        market: String,
        code: String,
    ) = Unit

    override suspend fun updateWatchlistItemGroup(
        market: String,
        code: String,
        groupName: String?,
    ) = Unit

    override suspend fun updateWatchlistOrder(items: List<WatchlistItem>) = Unit

    override suspend fun containsWatchlistItem(
        market: String,
        code: String,
    ): Boolean = false

    override suspend fun nextDisplayOrder(): Int = items.size

    override suspend fun seedSampleWatchlistIfEmpty() = Unit
}

private class CountingTencentStockApi(
    private val payload: String,
) : TencentStockApi {
    var callCount: Int = 0
        private set

    override suspend fun getQuotes(codes: String): String {
        callCount += 1
        return payload
    }
}

private class CountingSinaStockApi(
    private val payload: String,
) : SinaStockApi {
    var callCount: Int = 0
        private set

    override suspend fun getQuotes(codes: String): ResponseBody {
        callCount += 1
        return payload
            .toByteArray(Charset.forName("GBK"))
            .toResponseBody("text/plain".toMediaType())
    }
}

private suspend fun <T> Flow<T>.firstValue(): T = first()

private fun buildRepositoryTencentLine(
    code: String,
    name: String,
    latestPrice: String,
    previousClosePrice: String,
    openPrice: String,
    highPrice: String,
    lowPrice: String,
    volumeHands: String,
    turnoverWan: String,
): String {
    val fields = MutableList(38) { "" }
    fields[1] = name
    fields[2] = code
    fields[3] = latestPrice
    fields[4] = previousClosePrice
    fields[5] = openPrice
    fields[33] = highPrice
    fields[34] = lowPrice
    fields[36] = volumeHands
    fields[37] = turnoverWan
    return "v_sh$code=\"${fields.joinToString("~")}\";"
}

private fun buildRepositorySinaLine(
    market: String,
    code: String,
    name: String,
    openPrice: String,
    previousClosePrice: String,
    latestPrice: String,
    highPrice: String,
    lowPrice: String,
    volume: String,
    turnover: String,
): String {
    val fields = MutableList(32) { "0" }
    fields[0] = name
    fields[1] = openPrice
    fields[2] = previousClosePrice
    fields[3] = latestPrice
    fields[4] = highPrice
    fields[5] = lowPrice
    fields[8] = volume
    fields[9] = turnover
    return "var hq_str_${market}${code}=\"${fields.joinToString(",")}\";"
}
