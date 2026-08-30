package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.market.MarketDataSource
import com.example.mysecondapp.data.provider.FixedMarketDataProviderSelector
import com.example.mysecondapp.data.provider.MarketDataProvider
import com.example.mysecondapp.data.provider.MarketDataProviderSelector
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.ProviderCapability
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.StockDetailSnapshot
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.TradeTick
import com.example.mysecondapp.domain.model.toLegacyProviderId
import com.example.mysecondapp.domain.analysis.history.HistoricalBarCompletion
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidationResult
import com.example.mysecondapp.domain.analysis.history.HistoricalBarValidator
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.repository.StockDetailRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.max

/**
 * M2 个股详情仓库。
 *
 * 行情、分时和 K 线分别缓存，避免切换图表周期时重复请求无关数据。缓存超时后若网络失败，
 * 会继续返回当前进程内的旧值；M2.4 会在这个基础上增加 Room 持久化缓存。
 */
@Singleton
class DefaultStockDetailRepository private constructor(
    private val providerSelector: MarketDataProviderSelector,
    private val klineCache: KlineCache,
    private val nowMillis: () -> Long,
    private val historicalBarValidator: HistoricalBarValidator,
) : StockDetailRepository {

    @Inject
    constructor(
        providerSelector: MarketDataProviderSelector,
        roomKlineCache: RoomKlineCache,
        historicalBarValidator: HistoricalBarValidator,
    ) : this(
        providerSelector = providerSelector,
        klineCache = roomKlineCache,
        nowMillis = System::currentTimeMillis,
        historicalBarValidator = historicalBarValidator,
    )

    companion object {
        // 测试通过工厂注入伪数据源和可控时钟，不把测试依赖暴露给 Hilt。
        internal fun createForTest(
            marketDataSource: MarketDataSource,
            detailDataSource: StockDetailDataSource,
            klineCache: KlineCache,
            nowMillis: () -> Long,
            historicalBarValidator: HistoricalBarValidator = HistoricalBarValidator(),
        ): DefaultStockDetailRepository = DefaultStockDetailRepository(
            providerSelector = FixedMarketDataProviderSelector(
                quoteSources = listOf(marketDataSource),
                detailSource = detailDataSource,
            ),
            klineCache = klineCache,
            nowMillis = nowMillis,
            historicalBarValidator = historicalBarValidator,
        )

        private const val QUOTE_CACHE_TTL_MILLIS = 15_000L
        private const val INTRADAY_CACHE_TTL_MILLIS = 15_000L
        private const val ORDER_BOOK_CACHE_TTL_MILLIS = 15_000L
        private const val TRADE_TICKS_CACHE_TTL_MILLIS = 15_000L
        private const val MIN_CANDLE_LIMIT = 1
        private const val MAX_CANDLE_LIMIT = 1_000
        private const val MIN_TRADE_TICK_LIMIT = 1
    }

    private val detailSnapshots = MutableStateFlow<Map<String, StockDetailSnapshot>>(emptyMap())
    private val quoteCache = mutableMapOf<ProviderCacheKey, CachedValue<Quote>>()
    private val intradayCache = mutableMapOf<ProviderCacheKey, CachedValue<IntradaySeries>>()
    private val candleCache = mutableMapOf<CandleCacheKey, CachedValue<List<Candle>>>()
    private val orderBookCache = mutableMapOf<ProviderCacheKey, CachedValue<OrderBook>>()
    private val tradeTicksCache = mutableMapOf<ProviderTradeTickCacheKey, CachedValue<List<TradeTick>>>()

    override fun observeDetail(identity: StockIdentity): Flow<StockDetailSnapshot?> = detailSnapshots
        .map { snapshots -> snapshots[identity.cacheKey()] }
        .distinctUntilChanged()

    override suspend fun refreshDetail(
        identity: StockIdentity,
        candlePeriod: CandlePeriod,
        candleLimit: Int,
    ): MarketDataResult<StockDetailSnapshot> = coroutineScope {
        // 三类详情数据互不依赖，并行请求能缩短首次进入详情页的等待时间。
        val quoteDeferred = async { fetchQuote(identity) }
        val intradayDeferred = async { fetchIntraday(identity) }
        val candlesDeferred = if (candlePeriod != CandlePeriod.MINUTE) {
            async { fetchCandles(identity, candlePeriod, candleLimit) }
        } else {
            null
        }
        val orderBookDeferred = async { fetchOrderBook(identity) }
        val tradeTicksDeferred = async {
            fetchTradeTicks(identity, StockDetailRepository.DEFAULT_TRADE_TICK_LIMIT)
        }

        val quoteResult = quoteDeferred.await()
        val intradayResult = intradayDeferred.await()
        // 分时模式直接复用同一次请求的分时结果，避免触发第二次分钟线网络调用。
        val candlesResult = if (candlePeriod == CandlePeriod.MINUTE) {
            intradayResult.mapValue { series ->
                series.toMinuteCandles().takeLast(candleLimit.coerceIn(MIN_CANDLE_LIMIT, MAX_CANDLE_LIMIT))
            }
        } else {
            requireNotNull(candlesDeferred).await()
        }
        val orderBookResult = orderBookDeferred.await()
        val tradeTicksResult = tradeTicksDeferred.await()
        val previous = detailSnapshots.value[identity.cacheKey()]

        val quote = quoteResult.valueOrNull() ?: previous?.quote
        val intraday = intradayResult.valueOrNull() ?: previous?.intraday
        // 上一周期的 K 线不能拿来代替用户刚切换的周期，因此只保留相同周期的旧值。
        val candles = candlesResult.valueOrNull()
            ?: previous?.takeIf { it.candlePeriod == candlePeriod }?.candles
            ?: emptyList()
        val orderBook = orderBookResult.valueOrNull() ?: previous?.orderBook
        val tradeTicks = tradeTicksResult.valueOrNull() ?: previous?.tradeTicks.orEmpty()

        if (quote == null && intraday == null && candles.isEmpty() && orderBook == null && tradeTicks.isEmpty()) {
            return@coroutineScope MarketDataResult.Failure(
                quoteResult.errorOrNull()
                    ?: intradayResult.errorOrNull()
                    ?: candlesResult.errorOrNull()
                    ?: orderBookResult.errorOrNull()
                    ?: tradeTicksResult.errorOrNull()
                    ?: MarketError.Unknown("No detail data returned."),
            )
        }

        val fetchedAtMillis = max(
            quoteResult.fetchedAtMillisOr(previous?.fetchedAtMillis ?: nowMillis()),
            max(
                intradayResult.fetchedAtMillisOr(previous?.fetchedAtMillis ?: nowMillis()),
                candlesResult.fetchedAtMillisOr(previous?.fetchedAtMillis ?: nowMillis()),
            ),
        )
        val snapshot = StockDetailSnapshot(
            identity = identity,
            quote = quote,
            intraday = intraday,
            candlePeriod = candlePeriod,
            candles = candles,
            // 盘口和成交明细独立降级，不影响报价与图表的展示。
            orderBook = orderBook,
            tradeTicks = tradeTicks,
            fetchedAtMillis = fetchedAtMillis,
            providerIds = listOf(
                quoteResult,
                intradayResult,
                candlesResult,
                orderBookResult,
                tradeTicksResult,
            ).mapNotNull { result -> result.providerIdOrNull() }.toSet(),
        )
        detailSnapshots.value = detailSnapshots.value + (identity.cacheKey() to snapshot)

        MarketDataResult.Success(
            value = snapshot,
            source = listOf(
                quoteResult,
                intradayResult,
                candlesResult,
                orderBookResult,
                tradeTicksResult,
            ).effectiveSource(),
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    override suspend fun fetchIntraday(identity: StockIdentity): MarketDataResult<IntradaySeries> {
        val key = identity.cacheKey()
        val providers = providerSelector.detailProviders(identity, ProviderCapability.INTRADAY)
        val cached = providers.asSequence().mapNotNull { provider ->
            intradayCache[ProviderCacheKey(key, provider.id)]
        }.firstOrNull()
        if (cached?.isFresh(INTRADAY_CACHE_TTL_MILLIS, nowMillis()) == true) {
            return cached.asCacheResult()
        }

        val result = fetchFromDetailProviders(identity, ProviderCapability.INTRADAY, providers) { source ->
            source.fetchIntraday(identity)
        }
        return when (result) {
            is MarketDataResult.Success -> {
                intradayCache[ProviderCacheKey(key, result.providerIdOrLegacy())] = CachedValue(
                    result.value,
                    result.fetchedAtMillis,
                    nowMillis(),
                    result.providerIdOrLegacy(),
                )
                result
            }

            is MarketDataResult.Failure -> cached?.asCacheResult() ?: result
        }
    }

    override suspend fun fetchCandles(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult<List<Candle>> {
        val safeLimit = limit.coerceIn(MIN_CANDLE_LIMIT, MAX_CANDLE_LIMIT)
        val capability = if (period == CandlePeriod.MINUTE) {
            ProviderCapability.INTRADAY
        } else {
            ProviderCapability.CANDLES
        }
        val providers = providerSelector.detailProviders(identity, capability)
        val cached = providers.asSequence().mapNotNull { provider ->
            candleCache[CandleCacheKey(identity.cacheKey(), period, CandleAdjustment.QFQ, safeLimit, provider.id)]
                ?: candleCache[CandleCacheKey(identity.cacheKey(), period, CandleAdjustment.RAW, safeLimit, provider.id)]
        }.firstOrNull()
        if (cached?.isFresh(period.cacheTtlMillis(), nowMillis()) == true) {
            return cached.asCacheResult()
        }

        val result = if (period == CandlePeriod.MINUTE) {
            fetchIntraday(identity).mapValue { series -> series.toMinuteCandles().takeLast(safeLimit) }
        } else {
            fetchFromDetailProviders(identity, ProviderCapability.CANDLES, providers) { source ->
                source.fetchCandles(identity, period, safeLimit)
            }
        }

        return when (result) {
            is MarketDataResult.Success -> {
                val actualAdjustment = result.value.firstOrNull()?.adjustment ?: CandleAdjustment.QFQ
                candleCache[
                    CandleCacheKey(
                        identity.cacheKey(),
                        period,
                        actualAdjustment,
                        safeLimit,
                        result.providerIdOrLegacy(),
                    ),
                ] = CachedValue(
                    result.value,
                    result.fetchedAtMillis,
                    nowMillis(),
                    result.providerIdOrLegacy(),
                )
                if (period != CandlePeriod.MINUTE) {
                    klineCache.save(
                        identity = identity,
                        period = period,
                        candles = result.value,
                        fetchedAtMillis = result.fetchedAtMillis,
                        keepCount = safeLimit,
                        providerId = result.providerIdOrLegacy(),
                    )
                }
                result
            }

            is MarketDataResult.Failure -> cached?.asCacheResult()
                ?: readPersistedCandles(identity, providers.map { it.id }, period, safeLimit)
                ?: result
        }
    }

    override suspend fun fetchHistoricalBarSeries(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult<HistoricalBarValidationResult> {
        if (period == CandlePeriod.MINUTE) {
            return MarketDataResult.Failure(
                MarketError.Unknown("Historical analysis requires day, week, or month Bars."),
            )
        }

        return when (val result = fetchCandles(identity, period, limit)) {
            is MarketDataResult.Failure -> result
            is MarketDataResult.Success -> {
                val candles = result.value
                val contract = MarketDataContracts.forMarket(identity.market)
                val cutoff = candles.maxOfOrNull { it.timestampMillis }
                    ?: return MarketDataResult.Success(
                        value = historicalBarValidator.validate(
                            emptyHistoricalSeries(
                                identity = identity,
                                period = period,
                                result = result,
                                marketTimeZone = contract.marketTimeZone,
                            ),
                        ),
                        source = result.source,
                        fetchedAtMillis = result.fetchedAtMillis,
                        providerId = result.providerId,
                    )
                val series = HistoricalBarSeries(
                    identity = identity,
                    bars = candles,
                    period = period,
                    adjustment = candles.first().adjustment,
                    currency = candles.first().currency,
                    volumeUnit = candles.first().volumeUnit,
                    providerId = result.providerId ?: result.source.toLegacyProviderId() ?: DataProviders.TENCENT,
                    marketTimeZone = contract.marketTimeZone,
                    fetchedAtMillis = result.fetchedAtMillis,
                    analysisCutoffMillis = cutoff,
                    cutoffBarCompletion = completionFor(
                        timestampMillis = cutoff,
                        tradingSession = contract.tradingSession,
                        nowMillis = nowMillis(),
                    ),
                )
                MarketDataResult.Success(
                    value = historicalBarValidator.validate(series),
                    source = result.source,
                    fetchedAtMillis = result.fetchedAtMillis,
                    providerId = result.providerId,
                )
            }
        }
    }

    private fun emptyHistoricalSeries(
        identity: StockIdentity,
        period: CandlePeriod,
        result: MarketDataResult.Success<List<Candle>>,
        marketTimeZone: String,
    ): HistoricalBarSeries = HistoricalBarSeries(
        identity = identity,
        bars = emptyList(),
        period = period,
        adjustment = CandleAdjustment.QFQ,
        currency = MarketDataContracts.forMarket(identity.market).currency,
        volumeUnit = MarketDataContracts.forMarket(identity.market).volumeUnit,
        providerId = result.providerId ?: result.source.toLegacyProviderId() ?: DataProviders.TENCENT,
        marketTimeZone = marketTimeZone,
        fetchedAtMillis = result.fetchedAtMillis,
        analysisCutoffMillis = nowMillis(),
        cutoffBarCompletion = HistoricalBarCompletion.UNKNOWN,
    )

    private fun completionFor(
        timestampMillis: Long,
        tradingSession: com.example.mysecondapp.domain.model.TradingSession,
        nowMillis: Long,
    ): HistoricalBarCompletion {
        val barDate = tradingSession.localDateAt(timestampMillis)
        val currentDate = tradingSession.localDateAt(nowMillis)
        return when {
            barDate < currentDate -> HistoricalBarCompletion.CONFIRMED
            barDate > currentDate -> HistoricalBarCompletion.UNKNOWN
            else -> {
                val completion = tradingSession.completionTimestampMillis(barDate)
                when {
                    completion == null -> HistoricalBarCompletion.UNKNOWN
                    nowMillis >= completion -> HistoricalBarCompletion.CONFIRMED
                    else -> HistoricalBarCompletion.UNCONFIRMED
                }
            }
        }
    }

    private suspend fun readPersistedCandles(
        identity: StockIdentity,
        providerIds: List<DataProviderId>,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult.Success<List<Candle>>? {
        if (period == CandlePeriod.MINUTE) return null

        // Room 只作为网络失败后的持久化降级，不能在内存 TTL 到期时阻断网络刷新。
        var persistedEntry: Pair<DataProviderId, List<Candle>>? = null
        providerIds.forEach { providerId ->
            if (persistedEntry != null) return@forEach
            val candles = runCatching {
                klineCache.getRecent(
                    identity = identity,
                    period = period,
                    adjustment = CandleAdjustment.QFQ,
                    limit = limit,
                    providerId = providerId,
                )
                    .ifEmpty {
                        klineCache.getRecent(
                            identity = identity,
                            period = period,
                            adjustment = CandleAdjustment.RAW,
                            limit = limit,
                            providerId = providerId,
                        )
                    }
            }.getOrNull().orEmpty()
            if (candles.isNotEmpty()) persistedEntry = providerId to candles
        }
        val availablePersistedEntry = persistedEntry ?: return null
        val persistedProviderId = availablePersistedEntry.first
        val persistedCandles = availablePersistedEntry.second

        val cachedAtMillis = nowMillis()
        val cachedPersisted = CachedValue(
            value = persistedCandles,
            fetchedAtMillis = cachedAtMillis,
            cachedAtMillis = cachedAtMillis,
            providerId = persistedProviderId,
        )
        candleCache[
            CandleCacheKey(
                identity.cacheKey(),
                period,
                persistedCandles.first().adjustment,
                limit,
                persistedProviderId,
            ),
        ] = cachedPersisted
        return cachedPersisted.asCacheResult()
    }

    override suspend fun fetchOrderBook(identity: StockIdentity): MarketDataResult<OrderBook> {
        val key = identity.cacheKey()
        val providers = providerSelector.detailProviders(identity, ProviderCapability.ORDER_BOOK)
        val cached = providers.asSequence().mapNotNull { provider ->
            orderBookCache[ProviderCacheKey(key, provider.id)]
        }.firstOrNull()
        if (cached?.isFresh(ORDER_BOOK_CACHE_TTL_MILLIS, nowMillis()) == true) {
            return cached.asCacheResult()
        }

        val result = fetchFromDetailProviders(identity, ProviderCapability.ORDER_BOOK, providers) { source ->
            source.fetchOrderBook(identity)
        }
        return when (result) {
            is MarketDataResult.Success -> {
                orderBookCache[ProviderCacheKey(key, result.providerIdOrLegacy())] = CachedValue(
                    result.value, result.fetchedAtMillis, nowMillis(), result.providerIdOrLegacy(),
                )
                result
            }

            is MarketDataResult.Failure -> cached?.asCacheResult() ?: result
        }
    }

    override suspend fun fetchTradeTicks(
        identity: StockIdentity,
        limit: Int,
    ): MarketDataResult<List<TradeTick>> {
        val safeLimit = limit.coerceIn(MIN_TRADE_TICK_LIMIT, StockDetailRepository.DEFAULT_TRADE_TICK_LIMIT)
        val key = TradeTickCacheKey(identity.cacheKey(), safeLimit)
        val providers = providerSelector.detailProviders(identity, ProviderCapability.TRADE_TICKS)
        val cached = providers.asSequence().mapNotNull { provider ->
            tradeTicksCache[ProviderTradeTickCacheKey(key, provider.id)]
        }.firstOrNull()
        if (cached?.isFresh(TRADE_TICKS_CACHE_TTL_MILLIS, nowMillis()) == true) {
            return cached.asCacheResult()
        }

        val result = fetchFromDetailProviders(identity, ProviderCapability.TRADE_TICKS, providers) { source ->
            source.fetchTradeTicks(identity, safeLimit)
        }
        return when (result) {
            is MarketDataResult.Success -> {
                tradeTicksCache[ProviderTradeTickCacheKey(key, result.providerIdOrLegacy())] = CachedValue(
                    result.value, result.fetchedAtMillis, nowMillis(), result.providerIdOrLegacy(),
                )
                result
            }

            is MarketDataResult.Failure -> cached?.asCacheResult() ?: result
        }
    }

    private suspend fun fetchQuote(identity: StockIdentity): MarketDataResult<Quote> {
        val key = identity.cacheKey()
        val quoteProviders = providerSelector.quoteProviders(setOf(identity.market))
        val cached = quoteProviders.asSequence().mapNotNull { provider ->
            quoteCache[ProviderCacheKey(key, provider.id)]
        }.firstOrNull()
        if (cached?.isFresh(QUOTE_CACHE_TTL_MILLIS, nowMillis()) == true) {
            return cached.asCacheResult()
        }

        var quoteResult: MarketDataResult<List<Quote>>? = null
        var providerSymbol: String? = null
        var selectedProviderId: DataProviderId? = null
        quoteProviders.forEach { provider ->
            if (quoteResult is MarketDataResult.Success) return@forEach
            val source = provider.marketDataSource ?: return@forEach
            val symbol = provider.symbolMapper.toProviderSymbol(identity) ?: return@forEach
            providerSymbol = symbol
            selectedProviderId = provider.id
            quoteResult = source.fetchQuotes(listOf(symbol))
        }
        val selectedQuoteResult = quoteResult ?: MarketDataResult.Failure(
            MarketError.UnsupportedSymbol(
                market = identity.market,
                code = identity.code,
                reason = "No registered provider supports quote data for this market/code.",
            ),
        )
        val result = when (selectedQuoteResult) {
            is MarketDataResult.Success -> {
                val quote = selectedQuoteResult.value.firstOrNull { candidate ->
                    candidate.market.equals(identity.market, ignoreCase = true) && candidate.code == identity.code
                }
                if (quote == null) {
                    MarketDataResult.Failure(
                        MarketError.EmptyResponse(
                            source = selectedQuoteResult.source,
                            stockCodes = listOf(providerSymbol ?: identity.cacheKey()),
                        ),
                    )
                } else {
                    MarketDataResult.Success(
                        value = quote.copy(providerId = selectedProviderId),
                        source = selectedQuoteResult.source,
                        fetchedAtMillis = selectedQuoteResult.fetchedAtMillis,
                        providerId = selectedProviderId ?: selectedQuoteResult.providerIdOrLegacy(),
                    )
                }
            }

            is MarketDataResult.Failure -> selectedQuoteResult
        }

        return when (result) {
            is MarketDataResult.Success -> {
                quoteCache[ProviderCacheKey(key, result.providerIdOrLegacy())] = CachedValue(
                    result.value,
                    result.fetchedAtMillis,
                    nowMillis(),
                    result.providerIdOrLegacy(),
                )
                result
            }

            is MarketDataResult.Failure -> cached?.asCacheResult() ?: result
        }
    }

    private suspend fun <T> fetchFromDetailProviders(
        identity: StockIdentity,
        capability: ProviderCapability,
        providers: List<MarketDataProvider>,
        request: suspend (StockDetailDataSource) -> MarketDataResult<T>,
    ): MarketDataResult<T> {
        var lastFailure: MarketDataResult.Failure? = null
        providers.forEach { provider ->
            val source = provider.detailDataSource ?: return@forEach
            when (val result = request(source)) {
                is MarketDataResult.Success -> return result.copy(providerId = provider.id)
                is MarketDataResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: MarketDataResult.Failure(
            MarketError.UnsupportedSymbol(
                market = identity.market,
                code = identity.code,
                reason = "$capability is not supported by any selected provider.",
            ),
        )
    }

    private data class CachedValue<T>(
        val value: T,
        val fetchedAtMillis: Long,
        val cachedAtMillis: Long,
        val providerId: DataProviderId,
    ) {
        fun isFresh(ttlMillis: Long, nowMillis: Long): Boolean = nowMillis - cachedAtMillis <= ttlMillis

        fun asCacheResult(): MarketDataResult.Success<T> = MarketDataResult.Success(
            value = value,
            source = MarketSource.CACHE,
            fetchedAtMillis = fetchedAtMillis,
            providerId = providerId,
        )
    }

    private data class CandleCacheKey(
        val identityKey: String,
        val period: CandlePeriod,
        val adjustment: CandleAdjustment,
        val limit: Int,
        val providerId: DataProviderId,
    )

    private data class TradeTickCacheKey(
        val identityKey: String,
        val limit: Int,
    )

    private data class ProviderCacheKey(
        val identityKey: String,
        val providerId: DataProviderId,
    )

    private data class ProviderTradeTickCacheKey(
        val key: TradeTickCacheKey,
        val providerId: DataProviderId,
    )

}

private fun CandlePeriod.cacheTtlMillis(): Long = when (this) {
    CandlePeriod.MINUTE -> 15_000L
    CandlePeriod.DAY -> 15 * 60_000L
    CandlePeriod.WEEK -> 6 * 60 * 60_000L
    CandlePeriod.MONTH -> 24 * 60 * 60_000L
}

/** 分时点转换为一分钟 OHLC，成交量和成交额由累计值相减得到增量。 */
private fun IntradaySeries.toMinuteCandles(): List<Candle> = points.mapIndexed { index, point ->
    val previous = points.getOrNull(index - 1)
        Candle(
        timestampMillis = point.timestampMillis,
        open = point.price,
        high = point.price,
        low = point.price,
        close = point.price,
        volume = point.cumulativeVolume?.let { current ->
            // 腾讯分钟线累计量单位为手，领域层以股保存。
            (current - (previous?.cumulativeVolume ?: 0L)).coerceAtLeast(0L)
        },
            turnover = point.cumulativeTurnover?.let { current ->
                (current - (previous?.cumulativeTurnover ?: 0.0)).coerceAtLeast(0.0)
            },
            adjustment = CandleAdjustment.NONE,
            currency = this@toMinuteCandles.currency,
            volumeUnit = this@toMinuteCandles.volumeUnit,
        )
}

private fun <T, R> MarketDataResult<T>.mapValue(transform: (T) -> R): MarketDataResult<R> = when (this) {
    is MarketDataResult.Success -> MarketDataResult.Success(
        value = transform(value),
        source = source,
        fetchedAtMillis = fetchedAtMillis,
        providerId = providerId,
    )

    is MarketDataResult.Failure -> this
}

private fun <T> MarketDataResult<T>.valueOrNull(): T? = when (this) {
    is MarketDataResult.Success -> value
    is MarketDataResult.Failure -> null
}

private fun <T> MarketDataResult<T>.providerIdOrNull(): DataProviderId? = when (this) {
    is MarketDataResult.Success -> providerId
    is MarketDataResult.Failure -> null
}

private fun <T> MarketDataResult<T>.providerIdOrLegacy(): DataProviderId = when (this) {
    is MarketDataResult.Success -> providerId ?: source.toLegacyProviderId() ?: DataProviders.TENCENT
    is MarketDataResult.Failure -> DataProviders.TENCENT
}

private fun <T> MarketDataResult<T>.errorOrNull(): MarketError? = when (this) {
    is MarketDataResult.Success -> null
    is MarketDataResult.Failure -> error
}

private fun <T> MarketDataResult<T>.fetchedAtMillisOr(defaultValue: Long): Long = when (this) {
    is MarketDataResult.Success -> fetchedAtMillis
    is MarketDataResult.Failure -> defaultValue
}

private fun List<MarketDataResult<*>>.effectiveSource(): MarketSource = when {
    any { result -> result is MarketDataResult.Success && result.source != MarketSource.CACHE } -> MarketSource.TENCENT
    any { result -> result is MarketDataResult.Success } -> MarketSource.CACHE
    else -> MarketSource.CACHE
}
