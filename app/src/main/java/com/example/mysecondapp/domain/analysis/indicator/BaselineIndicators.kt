package com.example.mysecondapp.domain.analysis.indicator

import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandlePeriod
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object BaselineIndicatorIds {
    const val SMA = "sma"
    const val EMA = "ema"
    const val VOLUME_SMA = "volume_sma"
    const val MACD = "macd"
    const val RSI = "rsi"
    const val BOLL = "boll"
    const val ATR = "atr"
    const val OBV = "obv"
}

private val HISTORICAL_PERIODS = setOf(CandlePeriod.DAY, CandlePeriod.WEEK, CandlePeriod.MONTH)

class SmaIndicator(private val period: Int = 20) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.SMA,
        requiredFields = setOf(IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period,
        outputKeys = listOf("value"),
        parameters = mapOf("period" to period.toDouble()),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries = rollingSingle(
        input = input,
        definition = definition,
    ) { bars, index -> bars.windowValues(index, period) { it.close }?.average() }
}

class EmaIndicator(private val period: Int = 12) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.EMA,
        requiredFields = setOf(IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period,
        outputKeys = listOf("value"),
        parameters = mapOf("period" to period.toDouble()),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries {
        val closes = input.bars.map { it.close }
        val ema = exponentialValues(closes, period)
        return singleSeries(input, definition) { index -> ema.getOrNull(index) }
    }
}

class VolumeSmaIndicator(private val period: Int = 20) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.VOLUME_SMA,
        requiredFields = setOf(IndicatorField.VOLUME),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period,
        outputKeys = listOf("value"),
        parameters = mapOf("period" to period.toDouble()),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries = rollingSingle(
        input = input,
        definition = definition,
    ) { bars, index -> bars.windowValues(index, period) { it.volume?.toDouble() }?.average() }
}

class MacdIndicator(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
    private val signalPeriod: Int = 9,
) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.MACD,
        requiredFields = setOf(IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = slowPeriod + signalPeriod - 1,
        outputKeys = listOf("macd", "signal", "histogram"),
        parameters = mapOf(
            "fastPeriod" to fastPeriod.toDouble(),
            "slowPeriod" to slowPeriod.toDouble(),
            "signalPeriod" to signalPeriod.toDouble(),
        ),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries {
        val closes = input.bars.map { it.close }
        val fast = exponentialValues(closes, fastPeriod)
        val slow = exponentialValues(closes, slowPeriod)
        val macd = closes.indices.map { index ->
            fast[index]?.let { fastValue -> slow[index]?.let { slowValue -> fastValue - slowValue } }
        }
        val signal = exponentialValuesNullable(macd, signalPeriod)
        return multiSeries(input, definition) { index ->
            val macdValue = macd[index]
            val signalValue = signal[index]
            if (macdValue == null || signalValue == null) null
            else mapOf(
                "macd" to macdValue,
                "signal" to signalValue,
                "histogram" to macdValue - signalValue,
            )
        }
    }
}

class RsiIndicator(private val period: Int = 14) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.RSI,
        requiredFields = setOf(IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period + 1,
        outputKeys = listOf("value"),
        parameters = mapOf("period" to period.toDouble()),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries {
        val closes = input.bars.map { it.close }
        val rsi = MutableList<Double?>(closes.size) { null }
        if (closes.size > period) {
            var gainSum = 0.0
            var lossSum = 0.0
            for (index in 1..period) {
                val change = closes[index] - closes[index - 1]
                if (change >= 0.0) gainSum += change else lossSum -= change
            }
            var averageGain = gainSum / period
            var averageLoss = lossSum / period
            rsi[period] = rsiValue(averageGain, averageLoss)
            for (index in period + 1 until closes.size) {
                val change = closes[index] - closes[index - 1]
                val gain = max(change, 0.0)
                val loss = max(-change, 0.0)
                averageGain = ((averageGain * (period - 1)) + gain) / period
                averageLoss = ((averageLoss * (period - 1)) + loss) / period
                rsi[index] = rsiValue(averageGain, averageLoss)
            }
        }
        return singleSeries(input, definition) { index -> rsi[index] }
    }

    private fun rsiValue(averageGain: Double, averageLoss: Double): Double = when {
        averageLoss == 0.0 && averageGain == 0.0 -> 50.0
        averageLoss == 0.0 -> 100.0
        else -> 100.0 - (100.0 / (1.0 + averageGain / averageLoss))
    }
}

class BollingerBandsIndicator(
    private val period: Int = 20,
    private val deviations: Double = 2.0,
) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.BOLL,
        requiredFields = setOf(IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period,
        outputKeys = listOf("middle", "upper", "lower"),
        parameters = mapOf("period" to period.toDouble(), "deviations" to deviations),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries = multiSeries(input, definition) { index ->
        val values = input.bars.windowValues(index, period) { it.close } ?: return@multiSeries null
        val middle = values.average()
        val standardDeviation = sqrt(values.sumOf { value -> (value - middle) * (value - middle) } / period)
        mapOf(
            "middle" to middle,
            "upper" to middle + deviations * standardDeviation,
            "lower" to middle - deviations * standardDeviation,
        )
    }
}

class AtrIndicator(private val period: Int = 14) : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.ATR,
        requiredFields = setOf(IndicatorField.HIGH, IndicatorField.LOW, IndicatorField.CLOSE),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = period,
        outputKeys = listOf("value"),
        parameters = mapOf("period" to period.toDouble()),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries {
        val trueRanges = input.bars.mapIndexed { index, bar ->
            val previousClose = input.bars.getOrNull(index - 1)?.close
            if (previousClose == null) bar.high - bar.low
            else max(
                bar.high - bar.low,
                max(abs(bar.high - previousClose), abs(bar.low - previousClose)),
            )
        }
        return rollingSingle(input, definition) { bars, index ->
            if (bars[index].high.isFinite() && bars[index].low.isFinite() && bars[index].close.isFinite()) {
                trueRanges.subList(index - period + 1, index + 1).average()
            } else null
        }
    }
}

class ObvIndicator : Indicator {
    override val definition = IndicatorDefinition(
        id = BaselineIndicatorIds.OBV,
        requiredFields = setOf(IndicatorField.CLOSE, IndicatorField.VOLUME),
        supportedPeriods = HISTORICAL_PERIODS,
        minimumWarmupBars = 1,
        outputKeys = listOf("value"),
    )

    override fun calculate(input: HistoricalAnalysisInput): IndicatorSeries {
        var obv: Double? = null
        val values = input.bars.mapIndexed { index, bar ->
            val volume = bar.volume?.toDouble()
            obv = if (volume == null || !volume.isFinite()) {
                null
            } else if (index == 0 || obv == null) {
                volume
            } else {
                val previousClose = input.bars[index - 1].close
                when {
                    bar.close > previousClose -> obv!! + volume
                    bar.close < previousClose -> obv!! - volume
                    else -> obv
                }
            }
            obv
        }
        return singleSeries(input, definition) { index -> values[index] }
    }
}

object BaselineIndicators {
    fun registry(): IndicatorRegistry = IndicatorRegistry(
        listOf(
            SmaIndicator(),
            EmaIndicator(),
            VolumeSmaIndicator(),
            MacdIndicator(),
            RsiIndicator(),
            BollingerBandsIndicator(),
            AtrIndicator(),
            ObvIndicator(),
        ),
    )
}

private fun rollingSingle(
    input: HistoricalAnalysisInput,
    definition: IndicatorDefinition,
    calculate: (List<Candle>, Int) -> Double?,
): IndicatorSeries = singleSeries(input, definition) { index ->
    if (index < definition.minimumWarmupBars - 1) null else calculate(input.bars, index)
}

private fun singleSeries(
    input: HistoricalAnalysisInput,
    definition: IndicatorDefinition,
    valueAt: (Int) -> Double?,
): IndicatorSeries = multiSeries(input, definition) { index ->
    valueAt(index)?.takeIf { it.isFinite() }?.let { mapOf(definition.outputKeys.single() to it) }
}

private fun multiSeries(
    input: HistoricalAnalysisInput,
    definition: IndicatorDefinition,
    valuesAt: (Int) -> Map<String, Double>?,
): IndicatorSeries {
    val unsupported = input.series.period !in definition.supportedPeriods
    val values = input.bars.mapIndexed { index, bar ->
        when {
            unsupported -> IndicatorValue(
                timestampMillis = bar.timestampMillis,
                state = IndicatorValueState.UNAVAILABLE,
                unavailableReason = IndicatorUnavailableReason.UNSUPPORTED_PERIOD,
            )

            index < definition.minimumWarmupBars - 1 -> IndicatorValue(
                timestampMillis = bar.timestampMillis,
                state = IndicatorValueState.WARMUP,
            )

            else -> valuesAt(index)
                ?.takeIf { result -> result.keys.containsAll(definition.outputKeys) && result.values.all(Double::isFinite) }
                ?.let { result ->
                IndicatorValue(
                    timestampMillis = bar.timestampMillis,
                    state = IndicatorValueState.VALUE,
                    values = result,
                )
            } ?: IndicatorValue(
                timestampMillis = bar.timestampMillis,
                state = IndicatorValueState.UNAVAILABLE,
                unavailableReason = if (input.hasMissingRequiredInput(index, definition)) {
                    IndicatorUnavailableReason.MISSING_INPUT
                } else {
                    IndicatorUnavailableReason.INVALID_VALUE
                },
            )
        }
    }
    return IndicatorSeries(definition, values)
}

private fun List<Candle>.windowValues(
    index: Int,
    period: Int,
    selector: (Candle) -> Double?,
): List<Double>? {
    if (index < period - 1) return null
    val values = subList(index - period + 1, index + 1).map(selector)
    return values.takeIf { list -> list.all { it != null && it.isFinite() } }?.map { it!! }
}

private fun HistoricalAnalysisInput.hasMissingRequiredInput(
    index: Int,
    definition: IndicatorDefinition,
): Boolean {
    val start = (index - definition.minimumWarmupBars + 1).coerceAtLeast(0)
    return bars.subList(start, index + 1).any { bar ->
        definition.requiredFields.any { field ->
            when (field) {
                // OHLC is non-null by contract; NaN/Infinity is an invalid value, not a missing field.
                IndicatorField.CLOSE,
                IndicatorField.HIGH,
                IndicatorField.LOW,
                -> false
                IndicatorField.VOLUME -> bar.volume == null
            }
        }
    }
}

private fun exponentialValues(values: List<Double>, period: Int): List<Double?> =
    exponentialValuesNullable(values.map { it }, period)

private fun exponentialValuesNullable(values: List<Double?>, period: Int): List<Double?> {
    val result = MutableList<Double?>(values.size) { null }
    var current: Double? = null
    var validCount = 0
    val multiplier = 2.0 / (period + 1)
    values.forEachIndexed { index, value ->
        if (value == null || !value.isFinite()) {
            // A missing input breaks the recursive chain; do not bridge it with a fabricated value.
            current = null
            validCount = 0
            return@forEachIndexed
        }
        if (current == null) {
            validCount += 1
            if (validCount == period) {
                current = values.subList(index - period + 1, index + 1).map { it!! }.average()
                result[index] = current
            }
        } else {
            current = ((value - current!!) * multiplier) + current!!
            result[index] = current
        }
    }
    return result
}
