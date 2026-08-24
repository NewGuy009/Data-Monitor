package com.example.mysecondapp.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.IntradayPoint
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.OrderBookLevel
import com.example.mysecondapp.domain.model.OrderBookSide
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StockDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_displays_quote_and_switches_chart_period() {
        var selectedPeriod: CandlePeriod? = null
        var backClicked = false
        val identity = StockIdentity("SH", "600000")

        composeRule.setContent {
            MaterialTheme {
                StockDetailContent(
                    uiState = StockDetailUiState(
                        identity = identity,
                        selectedPeriod = CandlePeriod.DAY,
                        snapshot = StockDetailSnapshot(
                            identity = identity,
                            quote = Quote(
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
                                volume = 100_000,
                                turnover = 1_025_000.0,
                                updatedAtMillis = 1_000L,
                                source = MarketSource.TENCENT,
                            ),
                            intraday = null,
                            candlePeriod = CandlePeriod.DAY,
                            candles = listOf(
                                Candle(1_000L, 10.0, 10.3, 9.9, 10.25, 100_000, 1_025_000.0),
                            ),
                            orderBook = null,
                            tradeTicks = emptyList(),
                            fetchedAtMillis = 1_000L,
                        ),
                    ),
                    onBackClick = { backClicked = true },
                    onPeriodSelected = { selectedPeriod = it },
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(StockDetailTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithText("浦发银行").assertIsDisplayed()
        composeRule.onNodeWithText("10.25").assertIsDisplayed()
        composeRule.onNodeWithTag(StockDetailTestTags.CandleChart).assertIsDisplayed()
        composeRule.onNodeWithText("Week").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle {
            assertEquals(CandlePeriod.WEEK, selectedPeriod)
            assertTrue(backClicked)
        }
    }

    @Test
    fun content_shows_intraday_chart_when_time_period_has_points() {
        val identity = StockIdentity("SH", "600000")

        composeRule.setContent {
            MaterialTheme {
                StockDetailContent(
                    uiState = StockDetailUiState(
                        identity = identity,
                        selectedPeriod = CandlePeriod.MINUTE,
                        snapshot = StockDetailSnapshot(
                            identity = identity,
                            quote = null,
                            intraday = IntradaySeries(
                                identity = identity,
                                tradingDate = "2026-08-20",
                                points = listOf(
                                    IntradayPoint(1_000L, 10.10),
                                    IntradayPoint(2_000L, 10.20),
                                ),
                                fetchedAtMillis = 2_000L,
                            ),
                            candlePeriod = CandlePeriod.MINUTE,
                            candles = emptyList(),
                            orderBook = null,
                            tradeTicks = emptyList(),
                            fetchedAtMillis = 2_000L,
                        ),
                    ),
                    onBackClick = {},
                    onPeriodSelected = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(StockDetailTestTags.IntradayChart).assertIsDisplayed()
        composeRule.onNodeWithText("Trading date: 2026-08-20").assertIsDisplayed()
        composeRule.onNodeWithText("2 points", substring = true).assertIsDisplayed()
    }

    @Test
    fun content_shows_loaded_order_book_levels() {
        val identity = StockIdentity("SH", "600000")

        composeRule.setContent {
            MaterialTheme {
                StockDetailContent(
                    uiState = StockDetailUiState(
                        identity = identity,
                        selectedPeriod = CandlePeriod.DAY,
                        snapshot = StockDetailSnapshot(
                            identity = identity,
                            quote = null,
                            intraday = null,
                            candlePeriod = CandlePeriod.DAY,
                            candles = emptyList(),
                            orderBook = OrderBook(
                                identity = identity,
                                bids = listOf(OrderBookLevel(OrderBookSide.BID, 1, 9.11, 145_300L)),
                                asks = listOf(OrderBookLevel(OrderBookSide.ASK, 1, 9.12, 23_500L)),
                                updatedAtMillis = 1_000L,
                            ),
                            tradeTicks = emptyList(),
                            fetchedAtMillis = 1_000L,
                        ),
                    ),
                    onBackClick = {},
                    onPeriodSelected = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Order Book").assertIsDisplayed()
        composeRule.onNodeWithText("Bid").assertIsDisplayed()
        composeRule.onNodeWithText("Ask").assertIsDisplayed()
        composeRule.onNodeWithText("9.11").assertIsDisplayed()
        composeRule.onNodeWithText("145,300").assertIsDisplayed()
    }

    @Test
    fun content_shows_placeholder_and_error_when_chart_data_is_unavailable() {
        val identity = StockIdentity("SH", "600000")

        composeRule.setContent {
            MaterialTheme {
                StockDetailContent(
                    uiState = StockDetailUiState(
                        identity = identity,
                        selectedPeriod = CandlePeriod.DAY,
                        snapshot = StockDetailSnapshot(
                            identity = identity,
                            quote = null,
                            intraday = null,
                            candlePeriod = CandlePeriod.DAY,
                            candles = emptyList(),
                            orderBook = null,
                            tradeTicks = emptyList(),
                            fetchedAtMillis = 1_000L,
                        ),
                        errorMessage = "Network unavailable",
                    ),
                    onBackClick = {},
                    onPeriodSelected = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("No chart data available").assertIsDisplayed()
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("No order book data available.").assertIsDisplayed()
    }
}
