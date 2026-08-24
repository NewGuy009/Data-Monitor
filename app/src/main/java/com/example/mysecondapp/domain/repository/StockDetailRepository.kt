package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.TradeTick
import kotlinx.coroutines.flow.Flow

/**
 * 个股详情数据的领域入口。
 *
 * 该接口与 M1 的 [MarketRepository] 分离，详情页可以独立控制 K 线周期、缓存和刷新频率。
 * 具体数据源、Room 缓存和降级顺序都由 data 层实现，UI 不接触原始接口响应。
 */
interface StockDetailRepository {

    /** 观察当前身份对应的详情聚合状态，便于后续接入 Room/内存缓存的 Flow。 */
    fun observeDetail(identity: StockIdentity): Flow<StockDetailSnapshot?>

    /** 拉取详情页当前周期所需的聚合数据。 */
    suspend fun refreshDetail(
        identity: StockIdentity,
        candlePeriod: CandlePeriod = CandlePeriod.DAY,
        candleLimit: Int = DEFAULT_CANDLE_LIMIT,
    ): MarketDataResult<StockDetailSnapshot>

    /** 只刷新分时数据，避免切换详情局部区域时重复请求 K 线。 */
    suspend fun fetchIntraday(identity: StockIdentity): MarketDataResult<IntradaySeries>

    /** 拉取指定周期的 K 线，limit 由仓库负责裁剪到合理范围。 */
    suspend fun fetchCandles(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int = DEFAULT_CANDLE_LIMIT,
    ): MarketDataResult<List<Candle>>

    /** 拉取盘口；无稳定数据时允许成功返回空盘口。 */
    suspend fun fetchOrderBook(identity: StockIdentity): MarketDataResult<OrderBook>

    /** 拉取最近成交明细；无稳定数据时允许成功返回空列表。 */
    suspend fun fetchTradeTicks(
        identity: StockIdentity,
        limit: Int = DEFAULT_TRADE_TICK_LIMIT,
    ): MarketDataResult<List<TradeTick>>

    companion object {
        // 首期满足详情图表常用浏览范围，后续可由设置或数据源能力调整。
        const val DEFAULT_CANDLE_LIMIT: Int = 320
        const val DEFAULT_TRADE_TICK_LIMIT: Int = 100
    }
}
