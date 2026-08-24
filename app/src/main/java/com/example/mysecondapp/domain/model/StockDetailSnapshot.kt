package com.example.mysecondapp.domain.model

/**
 * 详情页可展示的一次聚合快照。
 *
 * 各子数据使用可空字段，是为了允许分时、K 线、盘口分别降级：例如盘口接口没有数据时，
 * 详情页仍然可以正常展示最新报价和 K 线，而不必让整个页面失败。
 */
data class StockDetailSnapshot(
    val identity: StockIdentity,
    val quote: Quote?,
    val intraday: IntradaySeries?,
    val candlePeriod: CandlePeriod,
    val candles: List<Candle>,
    val orderBook: OrderBook?,
    val tradeTicks: List<TradeTick>,
    val fetchedAtMillis: Long,
    /** Different detail regions may legitimately come from different fallback providers. */
    val providerIds: Set<DataProviderId> = emptySet(),
)
