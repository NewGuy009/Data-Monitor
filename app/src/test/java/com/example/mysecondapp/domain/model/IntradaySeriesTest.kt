package com.example.mysecondapp.domain.model

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class IntradaySeriesTest {

    private val identity = StockIdentity("SH", "600000")

    @Test
    fun `coverage reports partial data during the trading day`() {
        val series = seriesAt("2026-08-20 09:30", "2026-08-20 09:59")

        assertEquals(
            IntradayCoverage.PARTIAL_TRADING_DAY,
            series.coverageAt(timestamp("2026-08-20 10:00")),
        )
    }

    @Test
    fun `coverage reports complete data after the close`() {
        val series = seriesAt("2026-08-20 09:30", "2026-08-20 15:00")

        assertEquals(
            IntradayCoverage.COMPLETE_TRADING_DAY,
            series.coverageAt(timestamp("2026-08-20 15:01")),
        )
    }

    @Test
    fun `coverage reports latest available data when date is not today`() {
        val series = seriesAt("2026-08-20 09:30", "2026-08-20 15:00")

        assertEquals(
            IntradayCoverage.LATEST_AVAILABLE_TRADING_DAY,
            series.coverageAt(timestamp("2026-08-21 10:00")),
        )
    }

    @Test
    fun `coverage uses the market session timezone and close for US equities`() {
        val series = IntradaySeries(
            identity = StockIdentity("US-NASDAQ", "AAPL"),
            tradingDate = "2026-08-20",
            points = listOf(
                IntradayPoint(newYorkTimestamp("2026-08-20 09:30"), 220.0),
                IntradayPoint(newYorkTimestamp("2026-08-20 16:00"), 221.0),
            ),
            fetchedAtMillis = newYorkTimestamp("2026-08-20 16:00"),
            currency = CurrencyCode.USD,
            tradingSession = TradingSession.US_EQUITY,
            marketTimeZone = "America/New_York",
        )

        assertEquals(
            IntradayCoverage.COMPLETE_TRADING_DAY,
            series.coverageAt(newYorkTimestamp("2026-08-20 16:01")),
        )
        assertEquals(CurrencyCode.USD, series.currency)
    }

    private fun seriesAt(first: String, last: String): IntradaySeries = IntradaySeries(
        identity = identity,
        tradingDate = "2026-08-20",
        points = listOf(
            IntradayPoint(timestamp(first), 10.0),
            IntradayPoint(timestamp(last), 10.1),
        ),
        fetchedAtMillis = timestamp(last),
    )

    private fun timestamp(value: String): Long = ShanghaiDateTime.parse(value)!!.time

    private companion object {
        val ShanghaiDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            isLenient = false
        }
        val NewYorkDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
            isLenient = false
        }
    }

    private fun newYorkTimestamp(value: String): Long = NewYorkDateTime.parse(value)!!.time
}
