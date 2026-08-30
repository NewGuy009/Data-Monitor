package com.example.mysecondapp.domain.model

import kotlinx.serialization.Serializable

/** K 线周期；枚举值与外部数据源的字符串协议隔离。 */
@Serializable
enum class CandlePeriod {
    MINUTE,
    DAY,
    WEEK,
    MONTH,
}
