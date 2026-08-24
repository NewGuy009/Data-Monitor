package com.example.mysecondapp.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.QuoteSnapshot
import com.example.mysecondapp.domain.model.StockSearchItem
import com.example.mysecondapp.domain.model.WatchlistItem
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PositivePriceColor = Color(0xFFC62828)
private val NegativePriceColor = Color(0xFF2E7D32)
private val FlatPriceColor = Color(0xFF546E7A)
private val PriceFormatter = DecimalFormat("0.00")
private val PercentFormatter = DecimalFormat("0.00")
private val TimeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun WatchlistScreen(
    modifier: Modifier = Modifier,
    onSnapshotClick: (WatchlistItem) -> Unit = {},
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshIntervalSeconds by viewModel.refreshIntervalSeconds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    WatchlistContent(
        modifier = modifier
            .fillMaxSize(),
        statusText = statusText,
        snapshotCount = snapshots.size,
        refreshIntervalSeconds = refreshIntervalSeconds,
        isRefreshing = isRefreshing,
        searchQuery = searchQuery,
        searchResults = searchResults,
        snapshots = snapshots,
        onQueryChange = viewModel::updateSearchQuery,
        onAddClick = viewModel::addSearchResult,
        onRefresh = viewModel::refreshWatchlistQuotes,
        onSnapshotClick = onSnapshotClick,
    )
}

@Composable
fun WatchlistContent(
    modifier: Modifier = Modifier,
    statusText: String,
    snapshotCount: Int,
    refreshIntervalSeconds: Int,
    isRefreshing: Boolean,
    searchQuery: String,
    searchResults: List<StockSearchResultUiState>,
    snapshots: List<QuoteSnapshot>,
    onQueryChange: (String) -> Unit,
    onAddClick: (StockSearchItem) -> Unit,
    onRefresh: () -> Unit,
    onSnapshotClick: (WatchlistItem) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WatchlistHeader(
            statusText = statusText,
            refreshIntervalSeconds = refreshIntervalSeconds,
            snapshotCount = snapshotCount,
        )

        StockSearchSection(
            query = searchQuery,
            results = searchResults,
            onQueryChange = onQueryChange,
            onAddClick = onAddClick,
        )

        Box(
            // The quote list should only occupy the remaining space under header/search.
            // Using weight keeps the list scrollable after users add more watchlist items.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (snapshots.isEmpty() && !isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No local watchlist data yet.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = snapshots,
                            key = { "${it.watchlistItem.market}-${it.watchlistItem.code}" },
                        ) { snapshot ->
                            QuoteSnapshotCard(
                                snapshot = snapshot,
                                onClick = { onSnapshotClick(snapshot.watchlistItem) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistHeader(
    statusText: String,
    refreshIntervalSeconds: Int,
    snapshotCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Watchlist Quotes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(text = "Items $snapshotCount") },
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(text = "Auto ${refreshIntervalSeconds}s") },
            )
        }
    }
}

@Composable
private fun StockSearchSection(
    query: String,
    results: List<StockSearchResultUiState>,
    onQueryChange: (String) -> Unit,
    onAddClick: (StockSearchItem) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Add Symbols",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Search symbols by code, name, or pinyin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WatchlistTestTags.SearchField),
                singleLine = true,
                label = { Text("Search") },
                placeholder = { Text("600519 / 贵州茅台 / gzmt") },
            )

            when {
                query.isBlank() -> {
                    Text(
                        text = "Type to search and add a symbol into watchlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                results.isEmpty() -> {
                    Text(
                    text = "No symbol match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        results.forEach { result ->
                            StockSearchResultRow(
                                result = result,
                                onAddClick = onAddClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockSearchResultRow(
    result: StockSearchResultUiState,
    onAddClick: (StockSearchItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = result.item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${result.item.market} ${result.item.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (result.isAdded) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Added") },
            )
        } else {
            FilledTonalButton(
                modifier = Modifier.testTag(WatchlistTestTags.searchAddButton(result.item.market, result.item.code)),
                onClick = { onAddClick(result.item) },
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun QuoteSnapshotCard(
    snapshot: QuoteSnapshot,
    onClick: () -> Unit,
) {
    val quote = snapshot.quote
    // A refreshed quote is the authoritative display name and also repairs old malformed local names.
    val displayName = quote?.name?.takeIf { it.isNotBlank() } ?: snapshot.watchlistItem.name
    val priceTone = quote.toPriceToneColor()

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(WatchlistTestTags.quoteCard(snapshot.watchlistItem.market, snapshot.watchlistItem.code)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${snapshot.watchlistItem.market} ${snapshot.watchlistItem.code}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val groupName = snapshot.watchlistItem.groupName
                    if (!groupName.isNullOrBlank()) {
                        Text(
                            text = "Group: $groupName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = quote?.latestPrice?.formatPrice() ?: "--",
                        style = MaterialTheme.typography.headlineSmall,
                        color = priceTone,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = quote?.formatChangeLine() ?: "Waiting for quote",
                        style = MaterialTheme.typography.bodyMedium,
                        color = priceTone,
                        textAlign = TextAlign.End,
                    )
                }
            }

            if (quote != null) {
                // Show the most decision-useful market fields in one compact block.
                QuoteDetailsSection(quote = quote)
            } else {
                Text(
                    text = snapshot.error.toDisplayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun QuoteDetailsSection(quote: Quote) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QuoteInfoRow(
            leftLabel = "Open",
            leftValue = quote.openPrice.formatPrice(),
            rightLabel = "Prev Close",
            rightValue = quote.previousClosePrice.formatPrice(),
        )
        QuoteInfoRow(
            leftLabel = "High",
            leftValue = quote.highPrice.formatPrice(),
            rightLabel = "Low",
            rightValue = quote.lowPrice.formatPrice(),
        )
        QuoteInfoRow(
            leftLabel = "Volume",
            leftValue = quote.volume.formatVolume(),
            rightLabel = "Turnover",
            rightValue = quote.turnover.formatTurnover(),
        )
        QuoteInfoRow(
            leftLabel = "Updated",
            leftValue = quote.updatedAtMillis.formatClockTime(),
            rightLabel = "Source",
            rightValue = quote.source.name,
        )
    }
}

@Composable
private fun QuoteInfoRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        QuoteInfoCell(
            modifier = Modifier.weight(1f),
            label = leftLabel,
            value = leftValue,
        )
        QuoteInfoCell(
            modifier = Modifier.weight(1f),
            label = rightLabel,
            value = rightValue,
            alignEnd = true,
        )
    }
}

@Composable
private fun QuoteInfoCell(
    modifier: Modifier,
    label: String,
    value: String,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}

private fun Quote?.toPriceToneColor(): Color {
    if (this == null) return FlatPriceColor
    return when {
        changeAmount > 0 -> PositivePriceColor
        changeAmount < 0 -> NegativePriceColor
        else -> FlatPriceColor
    }
}

private fun Quote.formatChangeLine(): String {
    val signedAmount = if (changeAmount > 0) "+${changeAmount.formatPrice()}" else changeAmount.formatPrice()
    val signedPercent = if (changePercent > 0) "+${changePercent.formatPercent()}%" else "${changePercent.formatPercent()}%"
    return "$signedAmount  $signedPercent"
}

private fun Double.formatPrice(): String = PriceFormatter.format(this)

private fun Double.formatPercent(): String = PercentFormatter.format(this)

private fun Long?.formatVolume(): String {
    if (this == null) return "--"
    if (this >= 100_000_000L) return "${PriceFormatter.format(this / 100_000_000.0)}B"
    if (this >= 10_000L) return "${PriceFormatter.format(this / 10_000.0)}M"
    return toString()
}

private fun Double?.formatTurnover(): String {
    if (this == null) return "--"
    if (this >= 100_000_000.0) return "${PriceFormatter.format(this / 100_000_000.0)}B"
    if (this >= 10_000.0) return "${PriceFormatter.format(this / 10_000.0)}M"
    return PriceFormatter.format(this)
}

private fun Long.formatClockTime(): String = TimeFormatter.format(Date(this))

private fun com.example.mysecondapp.domain.model.MarketError?.toDisplayText(): String = when (this) {
    is com.example.mysecondapp.domain.model.MarketError.Network -> message ?: "Network unavailable"
    is com.example.mysecondapp.domain.model.MarketError.EmptyResponse -> "No quote returned from ${source.name}"
    is com.example.mysecondapp.domain.model.MarketError.ParseFailure -> "Quote parsing failed from ${source.name}"
    is com.example.mysecondapp.domain.model.MarketError.UnsupportedSymbol -> reason
    is com.example.mysecondapp.domain.model.MarketError.Unknown -> message ?: "Unknown market error"
    null -> "Quote not loaded yet."
}

object WatchlistTestTags {
    const val SearchField = "watchlist_search_field"

    fun searchAddButton(market: String, code: String): String = "watchlist_add_$market-$code"

    fun quoteCard(market: String, code: String): String = "watchlist_quote_$market-$code"
}
