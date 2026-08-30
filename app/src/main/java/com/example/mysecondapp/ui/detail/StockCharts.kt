package com.example.mysecondapp.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.IntradayPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.candlestickSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private const val CHART_HEIGHT_DP = 220
private val IntradayPointSpacing = 1.dp
private val ExtremumHighColor = Color(0xFFC62828)
private val ExtremumLowColor = Color(0xFF2E7D32)

/**
 * Keeps the price axis focused on the current data range.
 *
 * Vico's default auto range includes zero for positive-only data. That is
 * useful for columns, but it makes small-priced stocks look almost flat.
 */
private val PriceRangeProvider = object : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        adaptivePriceRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        adaptivePriceRange(minY, maxY).second
}

/** 分时价格折线。Vico 模型构建细节集中在此处，不泄漏到详情页面。 */
@Composable
fun IntradayPriceChart(
    points: List<IntradayPoint>,
    marketTimeZone: String,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val extrema = remember(points) { points.toIntradayPriceExtrema() }
    val persistentMarkers = extrema?.let { value ->
        rememberPersistentExtremaMarkers(
            highIndex = value.highIndex,
            lowIndex = value.lowIndex,
        )
    }
    val xLabels = remember(points, marketTimeZone) {
        points.map { point -> point.timestampMillis.formatChartTime(marketTimeZone) }
    }
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
            rememberLineCartesianLayer(
                pointSpacing = IntradayPointSpacing,
                rangeProvider = PriceRangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
            persistentMarkers = persistentMarkers,
        ),
        modelProducer = modelProducer,
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
    marketTimeZone: String,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val extrema = remember(candles) { candles.toCandlePriceExtrema() }
    val persistentMarkers = extrema?.let { value ->
        rememberPersistentExtremaMarkers(
            highIndex = value.highIndex,
            lowIndex = value.lowIndex,
        )
    }
    val xLabels = remember(candles, marketTimeZone) {
        candles.map { candle -> candle.timestampMillis.formatChartDate(marketTimeZone) }
    }
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
            rememberCandlestickCartesianLayer(rangeProvider = PriceRangeProvider),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
            persistentMarkers = persistentMarkers,
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
    labels.getOrNull(axisLabelIndex(value, labels.size)) ?: labels.firstOrNull().orEmpty()
}

internal fun axisLabelIndex(value: Double, labelCount: Int): Int =
    value.roundToInt().coerceIn(0, (labelCount - 1).coerceAtLeast(0))

/** Calculate a small, data-relative margin for the price axis. */
internal fun adaptivePriceRange(minY: Double, maxY: Double): Pair<Double, Double> {
    val lower = minY.coerceAtMost(maxY)
    val upper = maxY.coerceAtLeast(minY)
    val spread = upper - lower
    val reference = maxOf(kotlin.math.abs(lower), kotlin.math.abs(upper))
    val padding = if (spread > 0.0) {
        maxOf(spread * 0.08, reference * 0.001)
    } else {
        maxOf(reference * 0.02, 0.01)
    }
    val paddedLower = lower - padding
    val paddedUpper = upper + padding

    // Market prices are non-negative, so do not create a negative axis for
    // a positive-only series when the lower padding reaches below zero.
    return if (lower >= 0.0) {
        maxOf(0.0, paddedLower) to paddedUpper
    } else {
        paddedLower to paddedUpper
    }
}

/** The selected period's extrema are pinned to the source points, not screen coordinates. */
private data class PriceExtrema(
    val highIndex: Int,
    val lowIndex: Int,
)

private fun List<IntradayPoint>.toIntradayPriceExtrema(): PriceExtrema? {
    if (isEmpty()) return null
    return PriceExtrema(
        highIndex = indices.maxByOrNull { index -> this[index].price } ?: return null,
        lowIndex = indices.minByOrNull { index -> this[index].price } ?: return null,
    )
}

private fun List<Candle>.toCandlePriceExtrema(): PriceExtrema? {
    if (isEmpty()) return null
    return PriceExtrema(
        highIndex = indices.maxByOrNull { index -> this[index].high } ?: return null,
        lowIndex = indices.minByOrNull { index -> this[index].low } ?: return null,
    )
}

@Composable
private fun rememberPersistentExtremaMarkers(
    highIndex: Int,
    lowIndex: Int,
): (com.patrykandpatrick.vico.core.cartesian.CartesianChart.PersistentMarkerScope.(ExtraStore) -> Unit) {
    val highLabel = rememberTextComponent(
        color = Color.White,
        textSize = 11.sp,
        padding = insets(horizontal = 4.dp, vertical = 2.dp),
        background = rememberShapeComponent(fill = fill(ExtremumHighColor)),
    )
    val lowLabel = rememberTextComponent(
        color = Color.White,
        textSize = 11.sp,
        padding = insets(horizontal = 4.dp, vertical = 2.dp),
        background = rememberShapeComponent(fill = fill(ExtremumLowColor)),
    )
    val highMarker = remember(highLabel) { PriceExtremaMarker(highLabel = highLabel) }
    val lowMarker = remember(lowLabel) { PriceExtremaMarker(lowLabel = lowLabel) }
    val combinedMarker = remember(highLabel, lowLabel) {
        PriceExtremaMarker(highLabel = highLabel, lowLabel = lowLabel)
    }

    return remember(highIndex, lowIndex, highMarker, lowMarker, combinedMarker) {
        {
            if (highIndex == lowIndex) {
                combinedMarker at highIndex
            } else {
                highMarker at highIndex
                lowMarker at lowIndex
            }
        }
    }
}

private class PriceExtremaMarker(
    private val highLabel: TextComponent? = null,
    private val lowLabel: TextComponent? = null,
) : CartesianMarker {
    override fun drawOverLayers(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ) {
        targets.forEach { target ->
            when (target) {
                is CandlestickCartesianLayerMarkerTarget -> {
                    highLabel?.drawHigh(context, target.canvasX, target.highCanvasY, target.entry.high)
                    lowLabel?.drawLow(context, target.canvasX, target.lowCanvasY, target.entry.low)
                }

                is LineCartesianLayerMarkerTarget -> target.points.forEach { point ->
                    highLabel?.drawHigh(context, target.canvasX, point.canvasY, point.entry.y)
                    lowLabel?.drawLow(context, target.canvasX, point.canvasY, point.entry.y)
                }
            }
        }
    }
}

private fun TextComponent.drawHigh(
    context: CartesianDrawingContext,
    x: Float,
    pointY: Float,
    price: Double,
) {
    val y = (pointY - EXTREMUM_LABEL_GAP_DP * context.density)
        .coerceAtLeast(context.layerBounds.top + getHeight(context))
    draw(
        context = context,
        text = "H ${formatExtremumPrice(price)}",
        x = x,
        y = y,
        verticalPosition = Position.Vertical.Bottom,
    )
}

private fun TextComponent.drawLow(
    context: CartesianDrawingContext,
    x: Float,
    pointY: Float,
    price: Double,
) {
    val y = (pointY + EXTREMUM_LABEL_GAP_DP * context.density)
        .coerceAtMost(context.layerBounds.bottom - getHeight(context))
    draw(
        context = context,
        text = "L ${formatExtremumPrice(price)}",
        x = x,
        y = y,
        verticalPosition = Position.Vertical.Top,
    )
}

private const val EXTREMUM_LABEL_GAP_DP = 4f

private fun formatExtremumPrice(price: Double): String = "%.2f".format(Locale.ROOT, price)

internal fun Long.formatChartTime(marketTimeZone: String): String =
    formatChartTimestamp(pattern = "HH:mm", marketTimeZone = marketTimeZone)

internal fun Long.formatChartDate(marketTimeZone: String): String =
    formatChartTimestamp(pattern = "MM-dd", marketTimeZone = marketTimeZone)

private fun Long.formatChartTimestamp(pattern: String, marketTimeZone: String): String =
    SimpleDateFormat(pattern, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone(marketTimeZone)
    }.format(Date(this))
