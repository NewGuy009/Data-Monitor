package com.example.mysecondapp.domain.analysis.signal

import com.example.mysecondapp.domain.analysis.indicator.EmaIndicator
import com.example.mysecondapp.domain.analysis.indicator.BollingerBandsIndicator
import com.example.mysecondapp.domain.analysis.indicator.HistoricalAnalysisInput
import com.example.mysecondapp.domain.analysis.indicator.IndicatorSeries
import com.example.mysecondapp.domain.analysis.indicator.IndicatorValueState
import com.example.mysecondapp.domain.analysis.indicator.RsiIndicator
import com.example.mysecondapp.domain.analysis.indicator.VolumeSmaIndicator
import com.example.mysecondapp.domain.analysis.indicator.ObvIndicator
import com.example.mysecondapp.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
enum class TechnicalResultStatus {
    MATCHED,
    NOT_MATCHED,
    UNAVAILABLE,
}

/** Stable codes consumed by future rules; display text remains a UI responsibility. */
@Serializable
enum class TechnicalReasonCode {
    EMA_BULLISH_RELATION,
    EMA_BEARISH_RELATION,
    EMA_NEUTRAL_RELATION,
    EMA_GOLDEN_CROSS,
    EMA_DEATH_CROSS,
    RSI_OVERSOLD,
    RSI_OVERBOUGHT,
    RSI_NEUTRAL,
    RSI_RECOVERY,
    PRICE_ABOVE_BOLLINGER_UPPER,
    PRICE_BELOW_BOLLINGER_LOWER,
    PRICE_WITHIN_BOLLINGER_BANDS,
    VOLUME_ABOVE_AVERAGE,
    VOLUME_BELOW_AVERAGE,
    OBV_RISING,
    OBV_FALLING,
    OBV_FLAT,
    BULLISH_ENGULFING,
    BEARISH_ENGULFING,
    RANGE_BREAKOUT,
    INSUFFICIENT_HISTORY,
    INDICATOR_UNAVAILABLE,
    MISSING_VOLUME,
    PATTERN_NOT_MATCHED,
}

data class TechnicalResult(
    val kind: String,
    val status: TechnicalResultStatus,
    val reasonCode: TechnicalReasonCode,
    val sourceBarTimestamps: List<Long>,
    val values: Map<String, Double> = emptyMap(),
    val parameters: Map<String, Double> = emptyMap(),
) {
    init {
        require(sourceBarTimestamps.isNotEmpty())
        require(values.values.all(Double::isFinite))
        require(parameters.values.all(Double::isFinite))
    }
}

/** Candle geometry is domain data for patterns; it does not contain any trading recommendation. */
data class CandleShape(
    val timestampMillis: Long,
    val body: Double,
    val range: Double,
    val upperShadow: Double,
    val lowerShadow: Double,
    val isBullish: Boolean,
    val isBearish: Boolean,
)

fun Candle.toShape(): CandleShape = CandleShape(
    timestampMillis = timestampMillis,
    body = abs(close - open),
    range = high - low,
    upperShadow = high - max(open, close),
    lowerShadow = minOf(open, close) - low,
    isBullish = close > open,
    isBearish = close < open,
)

/** Persistent state based on the final available indicator values. */
class TrendStateDetector(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val lastTimestamp = input.bars.lastOrNull()?.timestampMillis ?: input.analysisCutoffMillis
        if (input.bars.size < slowPeriod) return unavailable(lastTimestamp, TechnicalReasonCode.INSUFFICIENT_HISTORY)
        val fast = EmaIndicator(fastPeriod).calculate(input).latestValue()
        val slow = EmaIndicator(slowPeriod).calculate(input).latestValue()
        val fastValue = fast?.value("value")
        val slowValue = slow?.value("value")
        if (fastValue == null || slowValue == null) return unavailable(lastTimestamp, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val reason = when {
            fastValue > slowValue -> TechnicalReasonCode.EMA_BULLISH_RELATION
            fastValue < slowValue -> TechnicalReasonCode.EMA_BEARISH_RELATION
            else -> TechnicalReasonCode.EMA_NEUTRAL_RELATION
        }
        return TechnicalResult(
            kind = "ema_trend",
            status = TechnicalResultStatus.MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = listOf(lastTimestamp),
            values = mapOf("fastEma" to fastValue, "slowEma" to slowValue),
            parameters = mapOf("fastPeriod" to fastPeriod.toDouble(), "slowPeriod" to slowPeriod.toDouble()),
        )
    }
}

/** Cross is an event: an unchanged bullish/bearish relation deliberately returns NOT_MATCHED. */
class EmaCrossDetector(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val timestamps = input.bars.takeLast(2).map { it.timestampMillis }
        if (input.bars.size < slowPeriod + 1) return unavailable(
            timestamps.ifEmpty { listOf(input.analysisCutoffMillis) },
            TechnicalReasonCode.INSUFFICIENT_HISTORY,
        )
        val fast = EmaIndicator(fastPeriod).calculate(input).values
        val slow = EmaIndicator(slowPeriod).calculate(input).values
        val previousFast = fast[fast.lastIndex - 1].value("value")
        val currentFast = fast.last().value("value")
        val previousSlow = slow[slow.lastIndex - 1].value("value")
        val currentSlow = slow.last().value("value")
        val previousFastValue = previousFast ?: return unavailable(timestamps, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val currentFastValue = currentFast ?: return unavailable(timestamps, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val previousSlowValue = previousSlow ?: return unavailable(timestamps, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val currentSlowValue = currentSlow ?: return unavailable(timestamps, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val golden = previousFastValue <= previousSlowValue && currentFastValue > currentSlowValue
        val death = previousFastValue >= previousSlowValue && currentFastValue < currentSlowValue
        val reason = when {
            golden -> TechnicalReasonCode.EMA_GOLDEN_CROSS
            death -> TechnicalReasonCode.EMA_DEATH_CROSS
            currentFastValue > currentSlowValue -> TechnicalReasonCode.EMA_BULLISH_RELATION
            currentFastValue < currentSlowValue -> TechnicalReasonCode.EMA_BEARISH_RELATION
            else -> TechnicalReasonCode.EMA_NEUTRAL_RELATION
        }
        return TechnicalResult(
            kind = "ema_cross",
            status = if (golden || death) TechnicalResultStatus.MATCHED else TechnicalResultStatus.NOT_MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = timestamps,
            values = mapOf(
                "previousFastEma" to previousFastValue,
                "previousSlowEma" to previousSlowValue,
                "fastEma" to currentFastValue,
                "slowEma" to currentSlowValue,
            ),
            parameters = mapOf("fastPeriod" to fastPeriod.toDouble(), "slowPeriod" to slowPeriod.toDouble()),
        )
    }
}

class RsiStateDetector(
    private val period: Int = 14,
    private val oversold: Double = 30.0,
    private val overbought: Double = 70.0,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val timestamp = input.bars.lastOrNull()?.timestampMillis ?: input.analysisCutoffMillis
        val value = RsiIndicator(period).calculate(input).latestValue()?.value("value")
            ?: return unavailable(timestamp, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val reason = when {
            value <= oversold -> TechnicalReasonCode.RSI_OVERSOLD
            value >= overbought -> TechnicalReasonCode.RSI_OVERBOUGHT
            else -> TechnicalReasonCode.RSI_NEUTRAL
        }
        return TechnicalResult(
            kind = "rsi_state",
            status = TechnicalResultStatus.MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = listOf(timestamp),
            values = mapOf("rsi" to value),
            parameters = mapOf("period" to period.toDouble(), "oversold" to oversold, "overbought" to overbought),
        )
    }
}

/** RSI recovery is a two-Bar event, distinct from a persistent oversold state. */
class RsiRecoveryDetector(
    private val period: Int = 14,
    private val oversold: Double = 30.0,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bars = input.bars.takeLast(2)
        if (bars.size < 2) return unavailable(
            bars.map { it.timestampMillis }.ifEmpty { listOf(input.analysisCutoffMillis) },
            TechnicalReasonCode.INSUFFICIENT_HISTORY,
        )
        val values = RsiIndicator(period).calculate(input).values.takeLast(2)
        val previous = values.firstOrNull()?.value("value")
        val current = values.lastOrNull()?.value("value")
        if (previous == null || current == null) return unavailable(bars.map { it.timestampMillis }, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val matched = previous <= oversold && current > oversold
        return TechnicalResult(
            kind = "rsi_recovery",
            status = if (matched) TechnicalResultStatus.MATCHED else TechnicalResultStatus.NOT_MATCHED,
            reasonCode = if (matched) TechnicalReasonCode.RSI_RECOVERY else TechnicalReasonCode.PATTERN_NOT_MATCHED,
            sourceBarTimestamps = bars.map { it.timestampMillis },
            values = mapOf("previousRsi" to previous, "rsi" to current),
            parameters = mapOf("period" to period.toDouble(), "oversold" to oversold),
        )
    }
}

class VolumeStateDetector(private val period: Int = 20) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bar = input.bars.lastOrNull() ?: return unavailable(input.analysisCutoffMillis, TechnicalReasonCode.INSUFFICIENT_HISTORY)
        val volume = bar.volume?.toDouble() ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.MISSING_VOLUME)
        val average = VolumeSmaIndicator(period).calculate(input).latestValue()?.value("value")
            ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        return TechnicalResult(
            kind = "volume_state",
            status = TechnicalResultStatus.MATCHED,
            reasonCode = if (volume >= average) TechnicalReasonCode.VOLUME_ABOVE_AVERAGE else TechnicalReasonCode.VOLUME_BELOW_AVERAGE,
            sourceBarTimestamps = listOf(bar.timestampMillis),
            values = mapOf("volume" to volume, "averageVolume" to average),
            parameters = mapOf("period" to period.toDouble()),
        )
    }
}

class BollingerPositionDetector(
    private val period: Int = 20,
    private val deviations: Double = 2.0,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bar = input.bars.lastOrNull() ?: return unavailable(input.analysisCutoffMillis, TechnicalReasonCode.INSUFFICIENT_HISTORY)
        val bands = BollingerBandsIndicator(period, deviations).calculate(input).latestValue()
            ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val upper = bands.value("upper") ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val lower = bands.value("lower") ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val middle = bands.value("middle") ?: return unavailable(bar.timestampMillis, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val reason = when {
            bar.close > upper -> TechnicalReasonCode.PRICE_ABOVE_BOLLINGER_UPPER
            bar.close < lower -> TechnicalReasonCode.PRICE_BELOW_BOLLINGER_LOWER
            else -> TechnicalReasonCode.PRICE_WITHIN_BOLLINGER_BANDS
        }
        return TechnicalResult(
            kind = "bollinger_position",
            status = TechnicalResultStatus.MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = listOf(bar.timestampMillis),
            values = mapOf("close" to bar.close, "upper" to upper, "middle" to middle, "lower" to lower),
            parameters = mapOf("period" to period.toDouble(), "deviations" to deviations),
        )
    }
}

class ObvStateDetector {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bars = input.bars.takeLast(2)
        if (bars.size < 2) return unavailable(
            bars.map { it.timestampMillis }.ifEmpty { listOf(input.analysisCutoffMillis) },
            TechnicalReasonCode.INSUFFICIENT_HISTORY,
        )
        val values = ObvIndicator().calculate(input).values.takeLast(2)
        val previous = values.firstOrNull()?.value("value")
        val current = values.lastOrNull()?.value("value")
        if (previous == null || current == null) return unavailable(bars.map { it.timestampMillis }, TechnicalReasonCode.INDICATOR_UNAVAILABLE)
        val reason = when {
            current > previous -> TechnicalReasonCode.OBV_RISING
            current < previous -> TechnicalReasonCode.OBV_FALLING
            else -> TechnicalReasonCode.OBV_FLAT
        }
        return TechnicalResult(
            kind = "obv_state",
            status = TechnicalResultStatus.MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = bars.map { it.timestampMillis },
            values = mapOf("previousObv" to previous, "obv" to current),
        )
    }
}

class EngulfingPatternDetector {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bars = input.bars.takeLast(2)
        if (bars.size < 2) return unavailable(
            listOf(input.analysisCutoffMillis),
            TechnicalReasonCode.INSUFFICIENT_HISTORY,
        )
        val previous = bars.first()
        val current = bars.last()
        val bullish = previous.close < previous.open && current.close > current.open &&
            current.open <= previous.close && current.close >= previous.open
        val bearish = previous.close > previous.open && current.close < current.open &&
            current.open >= previous.close && current.close <= previous.open
        val reason = when {
            bullish -> TechnicalReasonCode.BULLISH_ENGULFING
            bearish -> TechnicalReasonCode.BEARISH_ENGULFING
            else -> TechnicalReasonCode.PATTERN_NOT_MATCHED
        }
        return TechnicalResult(
            kind = "engulfing",
            status = if (bullish || bearish) TechnicalResultStatus.MATCHED else TechnicalResultStatus.NOT_MATCHED,
            reasonCode = reason,
            sourceBarTimestamps = bars.map { it.timestampMillis },
            values = mapOf(
                "previousOpen" to previous.open,
                "previousClose" to previous.close,
                "open" to current.open,
                "close" to current.close,
            ),
        )
    }
}

class RangeBreakoutDetector(
    private val lookback: Int = 20,
    private val volumeMultiplier: Double = 1.5,
) {
    fun detect(input: HistoricalAnalysisInput): TechnicalResult {
        val bars = input.bars
        val current = bars.lastOrNull() ?: return unavailable(input.analysisCutoffMillis, TechnicalReasonCode.INSUFFICIENT_HISTORY)
        if (bars.size < lookback + 1) return unavailable(current.timestampMillis, TechnicalReasonCode.INSUFFICIENT_HISTORY)
        val history = bars.dropLast(1).takeLast(lookback)
        val currentVolume = current.volume?.toDouble() ?: return unavailable(current.timestampMillis, TechnicalReasonCode.MISSING_VOLUME)
        val averageVolume = history.map { it.volume?.toDouble() }
            .takeIf { values -> values.all { it != null } }
            ?.map { it!! }
            ?.average()
            ?: return unavailable(current.timestampMillis, TechnicalReasonCode.MISSING_VOLUME)
        val rangeHigh = history.maxOf { it.high }
        val matched = current.close > rangeHigh && currentVolume >= averageVolume * volumeMultiplier
        return TechnicalResult(
            kind = "range_breakout",
            status = if (matched) TechnicalResultStatus.MATCHED else TechnicalResultStatus.NOT_MATCHED,
            reasonCode = if (matched) TechnicalReasonCode.RANGE_BREAKOUT else TechnicalReasonCode.PATTERN_NOT_MATCHED,
            sourceBarTimestamps = history.map { it.timestampMillis } + current.timestampMillis,
            values = mapOf("close" to current.close, "rangeHigh" to rangeHigh, "volume" to currentVolume, "averageVolume" to averageVolume),
            parameters = mapOf("lookback" to lookback.toDouble(), "volumeMultiplier" to volumeMultiplier),
        )
    }
}

/** Placeholder contract for later divergence work; M3 does not infer swings from an unreviewed algorithm. */
interface SwingPointDetector {
    fun detect(input: HistoricalAnalysisInput): List<SwingPoint>
}

data class SwingPoint(
    val timestampMillis: Long,
    val price: Double,
    val type: SwingPointType,
)

enum class SwingPointType { HIGH, LOW }

private fun IndicatorSeries.latestValue() = values.lastOrNull { it.state == IndicatorValueState.VALUE }

private fun unavailable(timestamp: Long, reason: TechnicalReasonCode): TechnicalResult =
    unavailable(listOf(timestamp), reason)

private fun unavailable(timestamps: List<Long>, reason: TechnicalReasonCode): TechnicalResult = TechnicalResult(
    kind = "unavailable",
    status = TechnicalResultStatus.UNAVAILABLE,
    reasonCode = reason,
    sourceBarTimestamps = timestamps,
)
