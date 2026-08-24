package com.example.mysecondapp.domain.model

/** 分时图中的一个成交价格点。时间统一为 Unix epoch 毫秒。 */
data class IntradayPoint(
    val timestampMillis: Long,
    val price: Double,
    // 腾讯分钟线返回的是截至该分钟的累计量，不是单分钟增量。
    val cumulativeVolume: Long? = null,
    val cumulativeTurnover: Double? = null,
    val averagePrice: Double? = null,
)
