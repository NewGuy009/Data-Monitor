package com.example.mysecondapp.domain.model

/** 买卖盘口的方向，避免 UI 根据正负数猜测方向。 */
enum class OrderBookSide {
    BID,
    ASK,
}

/** 五档盘口中的一个价位。level 从 1 开始，表示买一/卖一。 */
data class OrderBookLevel(
    val side: OrderBookSide,
    val level: Int,
    val price: Double,
    val quantity: Long,
)

/** 盘口数据；数据源没有稳定盘口字段时可以用空列表表达“暂无数据”。 */
data class OrderBook(
    val identity: StockIdentity,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val updatedAtMillis: Long,
)
