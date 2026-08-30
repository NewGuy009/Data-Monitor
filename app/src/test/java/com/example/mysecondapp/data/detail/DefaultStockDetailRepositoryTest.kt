package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.IntradayPoint
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.TradeTick
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStockDetailRepositoryTest {

    private val identity = StockIdentity(market = "SH", code = "600000")

    @Test
    fun `fresh detail requests reuse independent memory caches`() = runBlocking {
        val marketSource = FakeMarketDataSource()
        val detailSource = FakeStockDetailDataSource()
        var now = 1_000_000L
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = marketSource,
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            nowMillis = { now },
        )

        val first = repository.refreshDetail(identity, CandlePeriod.DAY, candleLimit = 10)
        now += 1_000L
        val second = repository.refreshDetail(identity, CandlePeriod.DAY, candleLimit = 10)

        assertTrue(first is MarketDataResult.Success)
        assertTrue(second is MarketDataResult.Success)
        assertEquals(1, marketSource.callCount)
        assertEquals(1, detailSource.intradayCallCount)
        assertEquals(1, detailSource.candleCallCount)
        assertEquals(MarketSource.CACHE, (second as MarketDataResult.Success).source)
    }

    @Test
    fun `expired cache is returned when network refresh fails`() = runBlocking {
        val marketSource = FakeMarketDataSource()
        val detailSource = FakeStockDetailDataSource()
        val klineCache = FakeKlineCache()
        var now = 2_000_000L
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = marketSource,
            detailDataSource = detailSource,
            klineCache = klineCache,
            nowMillis = { now },
        )

        val first = repository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)
        detailSource.candleResult = MarketDataResult.Failure(
            MarketError.Network("detail source unavailable"),
        )
        now += 16 * 60_000L
        val second = repository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)

        assertTrue(first is MarketDataResult.Success)
        assertTrue(second is MarketDataResult.Success)
        val cached = second as MarketDataResult.Success
        assertEquals(MarketSource.CACHE, cached.source)
        assertEquals(1, cached.value.size)
        assertEquals(10.10, cached.value.single().close, 0.0001)
        assertEquals(2, detailSource.candleCallCount)
    }

    @Test
    fun `expired memory cache refreshes from network before reading Room`() = runBlocking {
        val detailSource = FakeStockDetailDataSource()
        var now = 4_000_000L
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            nowMillis = { now },
        )

        repository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)
        detailSource.candleResult = MarketDataResult.Success(
            value = listOf(
                Candle(1_000L, 11.0, 11.2, 10.9, 11.1, 110_000L, 1_111_000.0),
            ),
            source = MarketSource.TENCENT,
            fetchedAtMillis = 4_100_000L,
        )
        now += 16 * 60_000L

        val refreshed = repository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)

        assertTrue(refreshed is MarketDataResult.Success)
        assertEquals(11.1, (refreshed as MarketDataResult.Success).value.single().close, 0.0001)
        assertEquals(2, detailSource.candleCallCount)
    }

    @Test
    fun `Room data is used only after network refresh fails`() = runBlocking {
        val klineCache = FakeKlineCache()
        val firstSource = FakeStockDetailDataSource()
        val firstRepository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = firstSource,
            klineCache = klineCache,
            nowMillis = { 5_000_000L },
        )
        firstRepository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)

        val failedSource = FakeStockDetailDataSource().apply {
            candleResult = MarketDataResult.Failure(MarketError.Network("offline"))
        }
        val fallbackRepository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = failedSource,
            klineCache = klineCache,
            nowMillis = { 5_000_000L },
        )

        val fallback = fallbackRepository.fetchCandles(identity, CandlePeriod.DAY, limit = 10)

        assertTrue(fallback is MarketDataResult.Success)
        assertEquals(MarketSource.CACHE, (fallback as MarketDataResult.Success).source)
        assertEquals(10.10, fallback.value.single().close, 0.0001)
        assertEquals(1, failedSource.candleCallCount)
    }

    @Test
    fun `refreshDetail keeps successful quote and intraday when candles fail`() = runBlocking {
        val marketSource = FakeMarketDataSource()
        val detailSource = FakeStockDetailDataSource().apply {
            candleResult = MarketDataResult.Failure(MarketError.ParseFailure(
                source = MarketSource.TENCENT,
                rawPayloadPreview = "bad kline",
            ))
        }
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = marketSource,
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            nowMillis = { 3_000_000L },
        )

        val result = repository.refreshDetail(identity, CandlePeriod.DAY, candleLimit = 10)

        assertTrue(result is MarketDataResult.Success)
        val snapshot = (result as MarketDataResult.Success).value
        assertEquals("浦发银行", snapshot.quote?.name)
        assertEquals(1, snapshot.intraday?.points?.size)
        assertTrue(snapshot.candles.isEmpty())
    }

    @Test
    fun `historical series adds provider metadata and cutoff quality`() = runBlocking {
        val detailSource = FakeStockDetailDataSource().apply {
            candleResult = MarketDataResult.Success(
                value = listOf(
                    Candle(1_000L, 10.0, 10.5, 9.5, 10.2, 100L, 1_000.0),
                    Candle(2_000L, 10.2, 10.8, 10.0, 10.6, 120L, 1_200.0),
                ),
                source = MarketSource.TENCENT,
                fetchedAtMillis = 3_000L,
            )
        }
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            // Move the fake clock beyond the market session close for the 1970 test date.
            nowMillis = { 100_000_000L },
        )

        val result = repository.fetchHistoricalBarSeries(identity, CandlePeriod.DAY, limit = 10)

        assertTrue(result is MarketDataResult.Success)
        val validation = (result as MarketDataResult.Success).value
        assertEquals(HistoricalBarQuality.COMPLETE, validation.quality)
        assertEquals("tencent", validation.series.providerId.value)
        assertEquals(CandlePeriod.DAY, validation.series.period)
        assertEquals(2_000L, validation.series.analysisCutoffMillis)
        assertEquals(HistoricalBarCompletion.CONFIRMED, validation.series.cutoffBarCompletion)
    }

    @Test
    fun `historical series keeps invalid data visible to analysis boundary`() = runBlocking {
        val detailSource = FakeStockDetailDataSource().apply {
            candleResult = MarketDataResult.Success(
                value = listOf(
                    Candle(1_000L, 10.0, 9.0, 9.5, 10.2, 100L, 1_000.0),
                    Candle(2_000L, 10.2, 10.8, 10.0, 10.6, 120L, 1_200.0),
                ),
                source = MarketSource.TENCENT,
                fetchedAtMillis = 3_000L,
            )
        }
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            nowMillis = { 4_000_000L },
        )

        val result = repository.fetchHistoricalBarSeries(identity, CandlePeriod.DAY, limit = 10)

        assertTrue(result is MarketDataResult.Success)
        val validation = (result as MarketDataResult.Success).value
        assertEquals(HistoricalBarQuality.INVALID, validation.quality)
        assertEquals(2, validation.analysisBars.size)
        assertTrue(validation.issues.any { issue -> issue.code.name == "INVALID_OHLC" })
    }

    @Test
    fun `historical series rejects minute input without requesting source`() = runBlocking {
        val detailSource = FakeStockDetailDataSource()
        val repository = DefaultStockDetailRepository.createForTest(
            marketDataSource = FakeMarketDataSource(),
            detailDataSource = detailSource,
            klineCache = FakeKlineCache(),
            nowMillis = { 4_000_000L },
        )

        val result = repository.fetchHistoricalBarSeries(identity, CandlePeriod.MINUTE, limit = 10)

        assertTrue(result is MarketDataResult.Failure)
        assertEquals(0, detailSource.candleCallCount)
    }
}

private class FakeMarketDataSource : MarketDataSource {
    override val source: MarketSource = MarketSource.TENCENT
    var callCount: Int = 0

    override suspend fun fetchQuotes(stockCodes: List<String>): MarketDataResult<List<Quote>> {
        callCount += 1
        return MarketDataResult.Success(
            value = listOf(
                Quote(
                    market = "SH",
                    code = "600000",
                    name = "浦发银行",
                    latestPrice = 10.25,
                    previousClosePrice = 10.00,
                    openPrice = 10.10,
                    highPrice = 10.30,
                    lowPrice = 9.98,
                    changeAmount = 0.25,
                    changePercent = 2.5,
                    volume = 123_000,
                    turnover = 4_567_800.0,
                    updatedAtMillis = 1_000L,
                    source = MarketSource.TENCENT,
                ),
            ),
            source = source,
            fetchedAtMillis = 1_000L,
        )
    }
}

private class FakeStockDetailDataSource : StockDetailDataSource {
    override val source: MarketSource = MarketSource.TENCENT
    var intradayCallCount: Int = 0
    var candleCallCount: Int = 0
    var candleResult: MarketDataResult<List<Candle>> = MarketDataResult.Success(
        value = listOf(
            Candle(
                timestampMillis = 1_000L,
                open = 10.00,
                high = 10.20,
                low = 9.90,
                close = 10.10,
                volume = 100_000,
                turnover = 1_010_000.0,
            ),
        ),
        source = MarketSource.TENCENT,
        fetchedAtMillis = 1_000L,
    )

    override suspend fun fetchIntraday(identity: StockIdentity): MarketDataResult<IntradaySeries> {
        intradayCallCount += 1
        return MarketDataResult.Success(
            value = IntradaySeries(
                identity = identity,
                tradingDate = "2026-08-20",
                points = listOf(IntradayPoint(1_000L, 10.20)),
                fetchedAtMillis = 1_000L,
            ),
            source = source,
            fetchedAtMillis = 1_000L,
        )
    }

    override suspend fun fetchCandles(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult<List<Candle>> {
        candleCallCount += 1
        return candleResult
    }

    override suspend fun fetchOrderBook(identity: StockIdentity): MarketDataResult<OrderBook> =
        MarketDataResult.Success(
            value = OrderBook(
                identity = identity,
                bids = emptyList(),
                asks = emptyList(),
                updatedAtMillis = 1_000L,
            ),
            source = source,
            fetchedAtMillis = 1_000L,
        )

    override suspend fun fetchTradeTicks(
        identity: StockIdentity,
        limit: Int,
    ): MarketDataResult<List<TradeTick>> = MarketDataResult.Success(
        value = emptyList(),
        source = source,
        fetchedAtMillis = 1_000L,
    )
}

private class FakeKlineCache : KlineCache {
    private val values = mutableMapOf<String, List<Candle>>()

    override suspend fun getRecent(
        identity: StockIdentity,
        period: CandlePeriod,
        adjustment: CandleAdjustment,
        limit: Int,
        providerId: DataProviderId,
    ): List<Candle> = values["${providerId.value}-${identity.cacheKey()}-${period.name}"]
        .orEmpty()
        .takeLast(limit)

    override suspend fun save(
        identity: StockIdentity,
        period: CandlePeriod,
        candles: List<Candle>,
        fetchedAtMillis: Long,
        keepCount: Int,
        providerId: DataProviderId,
    ) {
        values["${providerId.value}-${identity.cacheKey()}-${period.name}"] = candles.takeLast(keepCount)
    }
}
