package com.example.mysecondapp.domain.model

/** 成交方向；UNKNOWN 用于数据源没有提供可靠方向的情况。 */
enum class TradeDirection {
    BUY,
    SELL,
    NEUTRAL,
    UNKNOWN,
}

/** 成交明细中的一笔成交。 */
data class TradeTick(
    val timestampMillis: Long,
    val price: Double,
    val quantity: Long,
    val direction: TradeDirection = TradeDirection.UNKNOWN,
)
