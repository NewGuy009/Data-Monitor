package com.example.mysecondapp.domain.model

/**
 * 一根标准 OHLC K 线。
 *
 * 不在模型中保留腾讯或其他源的原始数组字段，后续图表和指标计算只依赖这组统一字段。
 */
data class Candle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long? = null,
    val turnover: Double? = null,
    val adjustment: CandleAdjustment = CandleAdjustment.QFQ,
    val currency: CurrencyCode = CurrencyCode.CNY,
    val volumeUnit: QuantityUnit = QuantityUnit.SHARES,
)
