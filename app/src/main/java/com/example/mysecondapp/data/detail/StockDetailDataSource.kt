package com.example.mysecondapp.data.detail

import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.MarketDataContract
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.TradeTick

/** 单个外部详情源的能力边界，后续可新增备源而不影响详情仓库。 */
interface StockDetailDataSource {
    val source: MarketSource

    fun dataContract(identity: StockIdentity): MarketDataContract =
        MarketDataContracts.forMarket(identity.market)

    suspend fun fetchIntraday(identity: StockIdentity): MarketDataResult<IntradaySeries>

    suspend fun fetchCandles(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult<List<Candle>>

    suspend fun fetchOrderBook(identity: StockIdentity): MarketDataResult<OrderBook>

    suspend fun fetchTradeTicks(
        identity: StockIdentity,
        limit: Int,
    ): MarketDataResult<List<TradeTick>>
}
