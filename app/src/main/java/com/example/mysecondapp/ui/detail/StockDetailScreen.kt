package com.example.mysecondapp.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.IntradayCoverage
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.OrderBookLevel
import com.example.mysecondapp.domain.model.OrderBookSide
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.TradeDirection
import com.example.mysecondapp.domain.model.TradeTick
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val DetailPositiveColor = Color(0xFFC62828)
private val DetailNegativeColor = Color(0xFF2E7D32)
private val DetailFlatColor = Color(0xFF546E7A)
private val DetailPriceFormatter = DecimalFormat("0.00")

@Composable
fun StockDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StockDetailContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPeriodSelected = viewModel::selectPeriod,
        onRefresh = viewModel::refresh,
        modifier = modifier.testTag(StockDetailTestTags.Screen),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailContent(
    uiState: StockDetailUiState,
    onBackClick: () -> Unit,
    onPeriodSelected: (CandlePeriod) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = uiState.snapshot?.quote?.name ?: uiState.identity.code)
                        Text(
                            text = "${uiState.identity.market} ${uiState.identity.code}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { DetailQuoteHeader(snapshot = uiState.snapshot) }
                item {
                    DetailChartSection(
                        selectedPeriod = uiState.selectedPeriod,
                        snapshot = uiState.snapshot,
                        onPeriodSelected = onPeriodSelected,
                        nowMillis = nowMillis,
                    )
                }
                uiState.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item { DetailOrderBookSection(snapshot = uiState.snapshot) }
                item { DetailTradeTicksSection(snapshot = uiState.snapshot) }
            }
        }
    }
}

@Composable
private fun DetailQuoteHeader(snapshot: StockDetailSnapshot?) {
    val quote = snapshot?.quote
    val tone = when {
        quote == null -> DetailFlatColor
        quote.changeAmount > 0 -> DetailPositiveColor
        quote.changeAmount < 0 -> DetailNegativeColor
        else -> DetailFlatColor
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = quote?.latestPrice?.formatDetailPrice() ?: "--",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = tone,
        )
        Text(
            text = quote?.let { value ->
                val amount = value.changeAmount.formatSignedPrice()
                val percent = value.changePercent.formatSignedPrice()
                "$amount  $percent%"
            } ?: "Loading detail data...",
            style = MaterialTheme.typography.titleMedium,
            color = tone,
        )
        quote?.let { value ->
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailMetric("Open", value.openPrice.formatDetailPrice())
                DetailMetric("High", value.highPrice.formatDetailPrice())
                DetailMetric("Low", value.lowPrice.formatDetailPrice())
                DetailMetric("Prev", value.previousClosePrice.formatDetailPrice())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailChartSection(
    selectedPeriod: CandlePeriod,
    snapshot: StockDetailSnapshot?,
    onPeriodSelected: (CandlePeriod) -> Unit,
    nowMillis: () -> Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DetailChartPeriod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.matches(selectedPeriod),
                    onClick = { onPeriodSelected(option.period) },
                    shape = SegmentedButtonDefaults.itemShape(index, DetailChartPeriod.entries.size),
                    label = { Text(option.label) },
                )
            }
        }
        when {
            snapshot == null -> DetailChartPlaceholder("Loading chart data...")
            selectedPeriod == CandlePeriod.MINUTE && snapshot.intraday?.points?.isNotEmpty() == true -> {
                val intraday = snapshot.intraday
                IntradayCoverageSummary(intraday = intraday, nowMillis = nowMillis())
                IntradayPriceChart(points = intraday.points)
            }
            snapshot.candlePeriod == selectedPeriod && snapshot.candles.isNotEmpty() -> {
                CandleStickChart(candles = snapshot.candles)
            }
            else -> DetailChartPlaceholder("No chart data available")
        }
    }
}

@Composable
private fun IntradayCoverageSummary(
    intraday: IntradaySeries,
    nowMillis: Long,
) {
    val firstTime = intraday.firstTimestampMillis?.formatIntradayTime() ?: "--"
    val lastTime = intraday.lastTimestampMillis?.formatIntradayTime() ?: "--"
    Text(
        text = "Trading date: ${intraday.tradingDate ?: "--"}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "$firstTime - $lastTime  |  ${intraday.pointCount} points  |  ${intraday.coverageAt(nowMillis).label()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailChartPlaceholder(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailOrderBookSection(snapshot: StockDetailSnapshot?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(text = "Order Book", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val orderBook = snapshot?.orderBook
        if (orderBook?.bids?.isNotEmpty() == true || orderBook?.asks?.isNotEmpty() == true) {
            OrderBookHeaderRow()
            orderBook.asks.asReversed().forEach { level -> OrderBookRow(level) }
            HorizontalDivider()
            orderBook.bids.forEach { level -> OrderBookRow(level) }
        } else {
            Text(
                text = "No order book data available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OrderBookHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Side", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
        Text("Level", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall)
        Text("Price", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text("Quantity", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OrderBookRow(level: OrderBookLevel) {
    val color = if (level.side == OrderBookSide.BID) DetailPositiveColor else DetailNegativeColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (level.side == OrderBookSide.BID) "Bid" else "Ask",
            modifier = Modifier.weight(0.8f),
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(text = level.level.toString(), modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall)
        Text(text = level.price.formatDetailPrice(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(text = level.quantity.formatQuantity(), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DetailTradeTicksSection(snapshot: StockDetailSnapshot?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(text = "Trades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val ticks = snapshot?.tradeTicks.orEmpty()
        if (ticks.isEmpty()) {
            Text(
                text = "No trade detail data available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ticks.take(StockDetailRepositoryDisplayLimits.TRADE_TICK_PREVIEW_LIMIT).forEach { tick ->
                TradeTickRow(tick)
            }
        }
    }
}

@Composable
private fun TradeTickRow(tick: TradeTick) {
    val directionLabel = when (tick.direction) {
        TradeDirection.BUY -> "Buy"
        TradeDirection.SELL -> "Sell"
        TradeDirection.NEUTRAL -> "Flat"
        TradeDirection.UNKNOWN -> "--"
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(directionLabel, style = MaterialTheme.typography.bodySmall)
        Text(tick.price.formatDetailPrice(), style = MaterialTheme.typography.bodySmall)
        Text(tick.quantity.formatQuantity(), style = MaterialTheme.typography.bodySmall)
    }
}

private object StockDetailRepositoryDisplayLimits {
    const val TRADE_TICK_PREVIEW_LIMIT = 20
}

@Composable
private fun RowScope.DetailMetric(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private enum class DetailChartPeriod(
    val label: String,
    val period: CandlePeriod,
) {
    Intraday("Time", CandlePeriod.MINUTE),
    Day("Day", CandlePeriod.DAY),
    Week("Week", CandlePeriod.WEEK),
    Month("Month", CandlePeriod.MONTH);

    fun matches(selectedPeriod: CandlePeriod): Boolean = period == selectedPeriod
}

private fun CandlePeriod.label(): String = when (this) {
    CandlePeriod.MINUTE -> "Intraday"
    CandlePeriod.DAY -> "Day"
    CandlePeriod.WEEK -> "Week"
    CandlePeriod.MONTH -> "Month"
}

private fun Double.formatDetailPrice(): String = DetailPriceFormatter.format(this)

private fun Double.formatSignedPrice(): String = when {
    this > 0 -> "+${formatDetailPrice()}"
    else -> formatDetailPrice()
}

private fun Long.formatQuantity(): String = when {
    this >= 100_000_000L -> "${DetailPriceFormatter.format(this / 100_000_000.0)}B"
    this >= 10_000L -> "${DetailPriceFormatter.format(this / 10_000.0)}M"
    else -> toString()
}

private fun IntradayCoverage.label(): String = when (this) {
    IntradayCoverage.PARTIAL_TRADING_DAY -> "Partial trading day"
    IntradayCoverage.COMPLETE_TRADING_DAY -> "Complete trading day"
    IntradayCoverage.LATEST_AVAILABLE_TRADING_DAY -> "Latest available trading day"
    IntradayCoverage.UNKNOWN -> "Coverage unknown"
}

private fun Long.formatIntradayTime(): String = IntradayTimeFormatter.format(Date(this))

private val IntradayTimeFormatter = SimpleDateFormat("HH:mm", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}

object StockDetailTestTags {
    const val Screen = "stock_detail_screen"
    const val IntradayChart = "stock_detail_intraday_chart"
    const val CandleChart = "stock_detail_candle_chart"
}
