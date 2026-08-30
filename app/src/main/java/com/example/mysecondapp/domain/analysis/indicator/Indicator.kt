package com.example.mysecondapp.domain.analysis.indicator

import com.example.mysecondapp.domain.model.CandlePeriod

enum class IndicatorField {
    CLOSE,
    HIGH,
    LOW,
    VOLUME,
}

data class IndicatorDefinition(
    val id: String,
    val requiredFields: Set<IndicatorField>,
    val supportedPeriods: Set<CandlePeriod>,
    val minimumWarmupBars: Int,
    val outputKeys: List<String>,
    val parameters: Map<String, Double> = emptyMap(),
) {
    init {
        require(id.isNotBlank())
        require(minimumWarmupBars > 0)
        require(outputKeys.isNotEmpty())
    }
}

enum class IndicatorValueState {
    WARMUP,
    VALUE,
    UNAVAILABLE,
}

enum class IndicatorUnavailableReason {
    UNSUPPORTED_PERIOD,
    INSUFFICIENT_HISTORY,
    MISSING_INPUT,
    INVALID_VALUE,
}

data class IndicatorValue(
    val timestampMillis: Long,
    val state: IndicatorValueState,
    val values: Map<String, Double> = emptyMap(),
    val unavailableReason: IndicatorUnavailableReason? = null,
) {
    init {
        require(values.values.all(Double::isFinite))
        if (state == IndicatorValueState.VALUE) require(values.isNotEmpty())
        if (state != IndicatorValueState.UNAVAILABLE) require(unavailableReason == null)
    }

    fun value(key: String): Double? = values[key]
}

data class IndicatorSeries(
    val definition: IndicatorDefinition,
    val values: List<IndicatorValue>,
) {
    init {
        require(values.zipWithNext().all { (current, next) -> current.timestampMillis < next.timestampMillis })
    }

    fun valueAt(timestampMillis: Long): IndicatorValue? =
        values.firstOrNull { it.timestampMillis == timestampMillis }
}

interface Indicator {
    val definition: IndicatorDefinition

    fun calculate(input: HistoricalAnalysisInput): IndicatorSeries
}

/** 指标注册表拒绝重复 ID，所有调用方通过同一组定义获取指标。 */
class IndicatorRegistry(indicators: List<Indicator>) {
    private val indicatorsById: Map<String, Indicator> = indicators
        .also { list -> require(list.map { it.definition.id }.distinct().size == list.size) }
        .associateBy { it.definition.id }

    val definitions: List<IndicatorDefinition>
        get() = indicatorsById.values.map { it.definition }

    fun find(id: String): Indicator? = indicatorsById[id]

    fun require(id: String): Indicator = requireNotNull(find(id)) {
        "Unknown indicator: $id"
    }

    fun calculate(id: String, input: HistoricalAnalysisInput): IndicatorSeries =
        require(id).calculate(input)
}
