package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.network.TencentDetailApi
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.OrderBookSide
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentDetailDataSourceTest {

    private val identity = StockIdentity(market = "SH", code = "600000")

    @Test
    fun `fetchIntraday parses current tencent minute payload`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """
                    {"code":0,"data":{"sh600000":{"data":{"date":"20260819","data":[
                    "0930 9.01 4337 3907637.00", "0931 9.04 26089 23530204.00"
                    ]}}}}
                """.trimIndent(),
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchIntraday(identity)

        assertTrue(result is MarketDataResult.Success)
        val success = result as MarketDataResult.Success
        assertEquals("2026-08-19", success.value.tradingDate)
        assertEquals(2, success.value.points.size)
        assertEquals(9.04, success.value.points.last().price, 0.0001)
        assertEquals(2_608_900L, success.value.points.last().cumulativeVolume)
        assertEquals(23_530_204.0, success.value.points.last().cumulativeTurnover ?: 0.0, 0.0001)
        assertEquals(9.02, success.value.points.last().averagePrice ?: 0.0, 0.01)
        assertEquals(CurrencyCode.CNY, success.value.currency)
        assertEquals(QuantityUnit.SHARES, success.value.volumeUnit)
    }

    @Test
    fun `fetchIntraday returns empty response for empty minute array`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """{"code":0,"data":{"sh600000":{"data":{"date":"20260819","data":[]}}}}""",
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchIntraday(identity)

        assertTrue(result is MarketDataResult.Failure)
    }

    @Test
    fun `fetchIntraday rejects out of order points`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """
                    {"code":0,"data":{"sh600000":{"data":{"date":"20260819","data":[
                    "0931 9.04 26089 23530204.00", "0930 9.01 4337 3907637.00"
                    ]}}}}
                """.trimIndent(),
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        assertTrue(dataSource.fetchIntraday(identity) is MarketDataResult.Failure)
    }

    @Test
    fun `fetchIntraday rejects duplicate timestamps`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """
                    {"code":0,"data":{"sh600000":{"data":{"date":"20260819","data":[
                    "0930 9.01 4337 3907637.00", "0930 9.04 26089 23530204.00"
                    ]}}}}
                """.trimIndent(),
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        assertTrue(dataSource.fetchIntraday(identity) is MarketDataResult.Failure)
    }

    @Test
    fun `fetchIntraday rejects malformed point instead of silently dropping it`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """
                    {"code":0,"data":{"sh600000":{"data":{"date":"20260819","data":[
                    "0930 9.01 4337 3907637.00", "bad"
                    ]}}}}
                """.trimIndent(),
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        assertTrue(dataSource.fetchIntraday(identity) is MarketDataResult.Failure)
    }

    @Test
    fun `fetchIntraday rejects invalid trading date`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = """
                    {"code":0,"data":{"sh600000":{"data":{"date":"20261340","data":[
                    "0930 9.01 4337 3907637.00"
                    ]}}}}
                """.trimIndent(),
                candlePayload = "{}",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        assertTrue(dataSource.fetchIntraday(identity) is MarketDataResult.Failure)
    }

    @Test
    fun `fetchCandles parses day week and month response keys`() = runBlocking {
        CandlePeriod.entries
            .filter { it != CandlePeriod.MINUTE }
            .forEach { period ->
                val api = FakeTencentDetailApi(
                    intradayPayload = "{}",
                    candlePayload = candlePayload(period),
                )
                val dataSource = TencentDetailDataSource(
                    tencentDetailApi = api,
                    json = Json { ignoreUnknownKeys = true },
                )

                val result = dataSource.fetchCandles(identity, period, limit = 5)

                assertTrue(result is MarketDataResult.Success)
                val success = result as MarketDataResult.Success
                assertEquals(2, success.value.size)
                assertEquals(9.20, success.value.first().open, 0.0001)
                assertEquals(9.10, success.value.first().close, 0.0001)
                assertEquals(9.38, success.value.first().high, 0.0001)
                assertEquals(9.06, success.value.first().low, 0.0001)
                assertEquals(200_000L, success.value.last().volume)
                assertEquals(
                    "sh600000,${period.name.lowercase()},,,5,qfq",
                    api.lastCandleParameter,
                )
            }
    }

    @Test
    fun `fetchCandles returns parse failure when every kline row is malformed`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = "{}",
                candlePayload = """{"code":0,"data":{"sh600000":{"qfqday":[["bad"]]}}}""",
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchCandles(identity, CandlePeriod.DAY, limit = 5)

        assertTrue(result is MarketDataResult.Failure)
    }

    @Test
    fun `fetchCandles falls back to raw day key without multiplying share volume`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = "{}",
                candlePayload = """
                    {"code":0,"data":{"sh600000":{"day":[
                    ["2026-08-18","9.20","9.10","9.38","9.06","1000"],
                    ["2026-08-19","9.09","9.08","9.10","8.96","2000"]
                    ]}}}
                """.trimIndent(),
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchCandles(identity, CandlePeriod.DAY, limit = 5)

        assertTrue(result is MarketDataResult.Success)
        val candles = (result as MarketDataResult.Success).value
        assertEquals(CandleAdjustment.RAW, candles.first().adjustment)
        assertEquals(1_000L, candles.first().volume)
    }

    @Test
    fun `fetchCandles rejects invalid ohlc rows`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = "{}",
                candlePayload = """
                    {"code":0,"data":{"sh600000":{"qfqday":[
                    ["2026-08-18","9.20","9.10","9.00","9.06","1000"]
                    ]}}}
                """.trimIndent(),
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchCandles(identity, CandlePeriod.DAY, limit = 5)

        assertTrue(result is MarketDataResult.Failure)
    }

    @Test
    fun `fetchOrderBook parses five bid and ask levels from qt snapshot`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(
                intradayPayload = "{}",
                candlePayload = orderBookPayload(),
            ),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchOrderBook(identity)

        assertTrue(result is MarketDataResult.Success)
        val orderBook = (result as MarketDataResult.Success).value
        assertEquals(5, orderBook.bids.size)
        assertEquals(5, orderBook.asks.size)
        assertEquals(OrderBookSide.BID, orderBook.bids.first().side)
        assertEquals(9.11, orderBook.bids.first().price, 0.0001)
        assertEquals(145_300L, orderBook.bids.first().quantity)
        assertEquals(9.12, orderBook.asks.first().price, 0.0001)
        assertEquals(23_500L, orderBook.asks.first().quantity)
    }

    @Test
    fun `fetchIntraday returns explicit unsupported error for current bj detail capability`() = runBlocking {
        val dataSource = TencentDetailDataSource(
            tencentDetailApi = FakeTencentDetailApi(intradayPayload = "{}", candlePayload = "{}"),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = dataSource.fetchIntraday(StockIdentity(market = "BJ", code = "430047"))

        assertTrue(result is MarketDataResult.Failure)
        assertTrue((result as MarketDataResult.Failure).error is MarketError.UnsupportedSymbol)
    }
}

private class FakeTencentDetailApi(
    private val intradayPayload: String,
    private val candlePayload: String,
) : TencentDetailApi {
    var lastCandleParameter: String = ""
        private set

    override suspend fun getIntraday(code: String): String = intradayPayload

    override suspend fun getForwardAdjustedKlines(parameter: String): String {
        lastCandleParameter = parameter
        return candlePayload
    }
}

private fun candlePayload(period: CandlePeriod): String {
    val responseKey = "qfq${period.name.lowercase()}"
    return """
        {"code":0,"data":{"sh600000":{"$responseKey":[
        ["2026-08-18","9.20","9.10","9.38","9.06","1000.000"],
        ["2026-08-19","9.09","9.08","9.10","8.96","2000"]
        ]}}}
    """.trimIndent()
}

private fun orderBookPayload(): String = """
    {"code":0,"data":{"sh600000":{"qfqday":[],"qt":{"sh600000":[
    "1","浦发银行","600000","9.11","9.08","9.03","673018","375922","297096",
    "9.11","1453","9.10","1611","9.09","877","9.08","2603","9.07","668",
    "9.12","235","9.13","1917","9.14","17309","9.15","8442","9.16","4701"
    ]}}}}
""".trimIndent()
