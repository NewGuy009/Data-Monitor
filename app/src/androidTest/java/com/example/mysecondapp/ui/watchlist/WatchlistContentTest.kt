package com.example.mysecondapp.ui.watchlist

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.QuoteSnapshot
import com.example.mysecondapp.domain.model.StockSearchItem
import com.example.mysecondapp.domain.model.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WatchlistContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun search_result_row_can_add_item() {
        var clickedItem: StockSearchItem? = null

        composeRule.setContent {
            MaterialTheme {
                WatchlistContent(
                    statusText = "Ready",
                    snapshotCount = 0,
                    refreshIntervalSeconds = 15,
                    isRefreshing = false,
                    searchQuery = "600519",
                    searchResults = listOf(
                        StockSearchResultUiState(
                            item = StockSearchItem(
                                market = "SH",
                                code = "600519",
                                name = "贵州茅台",
                            ),
                            isAdded = false,
                        ),
                    ),
                    snapshots = emptyList(),
                    onQueryChange = {},
                    onAddClick = { clickedItem = it },
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(WatchlistTestTags.SearchField).assertIsDisplayed()
        composeRule.onNodeWithText("贵州茅台").assertIsDisplayed()
        composeRule.onNodeWithTag(WatchlistTestTags.searchAddButton("SH", "600519"))
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clickedItem != null)
            assertEquals("600519", clickedItem?.code)
        }
    }

    @Test
    fun snapshot_card_shows_quote_details() {
        composeRule.setContent {
            MaterialTheme {
                WatchlistContent(
                    statusText = "Ready",
                    snapshotCount = 1,
                    refreshIntervalSeconds = 15,
                    isRefreshing = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    snapshots = listOf(
                        QuoteSnapshot(
                            watchlistItem = WatchlistItem(
                                market = "SH",
                                code = "600000",
                                name = "浦发银行",
                                order = 0,
                            ),
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
                                volume = 1_000L,
                                turnover = 10_000.0,
                                updatedAtMillis = 1_700_000_000_000L,
                                source = MarketSource.TENCENT,
                            ),
                            refreshedAtMillis = 1_700_000_000_000L,
                            source = MarketSource.TENCENT,
                        ),
                    ),
                    onQueryChange = {},
                    onAddClick = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("浦发银行").assertIsDisplayed()
        composeRule.onNodeWithText("10.25").assertIsDisplayed()
        composeRule.onNodeWithText("Open").assertIsDisplayed()
    }
}
