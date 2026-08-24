package com.example.mysecondapp.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.IntradayPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.candlestickSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHART_HEIGHT_DP = 220

/** 分时价格折线。Vico 模型构建细节集中在此处，不泄漏到详情页面。 */
@Composable
fun IntradayPriceChart(
    points: List<IntradayPoint>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val xLabels = remember(points) { points.map { point -> point.timestampMillis.formatTime() } }
    val xFormatter = remember(xLabels) { indexedLabelFormatter(xLabels) }

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = points.indices.toList(),
                    y = points.map { point -> point.price },
                )
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .testTag(StockDetailTestTags.IntradayChart),
    )
}

/** 日、周、月 K 线。X 值使用连续索引，时间戳仅用于横轴显示，避免不规则交易日造成过大空白。 */
@Composable
fun CandleStickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val xLabels = remember(candles) { candles.map { candle -> candle.timestampMillis.formatDate() } }
    val xFormatter = remember(xLabels) { indexedLabelFormatter(xLabels) }

    LaunchedEffect(candles) {
        modelProducer.runTransaction {
            candlestickSeries(
                x = candles.indices.toList(),
                opening = candles.map { candle -> candle.open },
                closing = candles.map { candle -> candle.close },
                low = candles.map { candle -> candle.low },
                high = candles.map { candle -> candle.high },
            )
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberCandlestickCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .testTag(StockDetailTestTags.CandleChart),
    )
}

/** 将 Vico 的索引 X 轴恢复成领域时间标签，越界取最近合法标签以适配刻度插值。 */
private fun indexedLabelFormatter(labels: List<String>): CartesianValueFormatter = CartesianValueFormatter { _, value, _ ->
    labels.getOrNull(value.toInt()) ?: labels.lastOrNull().orEmpty()
}

private fun Long.formatTime(): String = DetailTimeFormatter.format(Date(this))

private fun Long.formatDate(): String = DetailDateFormatter.format(Date(this))

private val DetailTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val DetailDateFormatter = SimpleDateFormat("MM-dd", Locale.getDefault())
