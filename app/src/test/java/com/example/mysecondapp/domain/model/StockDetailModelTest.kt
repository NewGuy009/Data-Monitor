package com.example.mysecondapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StockDetailModelTest {

    @Test
    fun `detail snapshot can represent complete chart and order book data`() {
        val identity = StockIdentity(market = "SH", code = "600000")
        val quote = Quote(
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
            updatedAtMillis = 1_700_000_000_000,
            source = MarketSource.TENCENT,
        )
        val snapshot = StockDetailSnapshot(
            identity = identity,
            quote = quote,
            intraday = IntradaySeries(
                identity = identity,
                tradingDate = "2026-08-19",
                points = listOf(
                    IntradayPoint(
                        timestampMillis = 1_700_000_000_000,
                        price = 10.20,
                        cumulativeVolume = 1_000,
                        cumulativeTurnover = 1_020_000.0,
                    ),
                ),
                fetchedAtMillis = 1_700_000_000_100,
            ),
            candlePeriod = CandlePeriod.DAY,
            candles = listOf(
                Candle(
                    timestampMillis = 1_699_900_000_000,
                    open = 10.00,
                    high = 10.40,
                    low = 9.90,
                    close = 10.25,
                    volume = 100_000,
                    turnover = 1_025_000.0,
                ),
            ),
            orderBook = OrderBook(
                identity = identity,
                bids = listOf(OrderBookLevel(OrderBookSide.BID, 1, 10.24, 1_200)),
                asks = listOf(OrderBookLevel(OrderBookSide.ASK, 1, 10.25, 800)),
                updatedAtMillis = 1_700_000_000_200,
            ),
            tradeTicks = listOf(
                TradeTick(
                    timestampMillis = 1_700_000_000_300,
                    price = 10.25,
                    quantity = 100,
                    direction = TradeDirection.BUY,
                ),
            ),
            fetchedAtMillis = 1_700_000_000_400,
        )

        assertEquals("SH-600000", snapshot.identity.cacheKey())
        assertEquals("sh600000", snapshot.identity.providerCode())
        assertEquals("浦发银行", snapshot.quote?.name)
        assertEquals(1, snapshot.intraday?.points?.size)
        assertEquals(1, snapshot.candles.size)
        assertNotNull(snapshot.orderBook)
        assertEquals(TradeDirection.BUY, snapshot.tradeTicks.single().direction)
    }
}
