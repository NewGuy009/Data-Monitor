package com.example.mysecondapp.domain.analysis.indicator

import com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidationResult
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries

/** 指标计算的只读输入，只允许使用已经通过截止点裁剪的历史前缀。 */
data class HistoricalAnalysisInput(
    val series: HistoricalBarSeries,
    val bars: List<Candle>,
    val quality: HistoricalBarQuality,
) {
    val analysisCutoffMillis: Long
        get() = series.analysisCutoffMillis

    init {
        require(bars.all { it.timestampMillis <= series.analysisCutoffMillis }) {
            "Analysis input contains a Bar after the declared cutoff."
        }
    }
}

/** 校验结果到指标输入的唯一转换点，避免调用方重新拼接或截取未来数据。 */
fun HistoricalBarValidationResult.toAnalysisInput(): HistoricalAnalysisInput = HistoricalAnalysisInput(
    series = series,
    bars = analysisBars,
    quality = quality,
)
